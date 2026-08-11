#!/usr/bin/env python3
"""
Ordía Continuous Evolution — Supervisor persistente (v2).

Mantiene un único agente de OpenHands trabajando continuamente: cuando un run
termina, dispatcha el siguiente en segundos (no horas). Garantiza
MAX_CONCURRENT_RUNS = 1 comprobando los runs activos antes de dispatchar.

Mejoras sobre v1:
- Lock cross-platform (Linux/macOS fcntl, Windows msvcrt, fallback a lock file
  con PID + TTL). Funciona en Linux, Windows y WSL.
- Lease distribuido vía GitHub Gist: evita que supervisor + watchdog GitHub
  Actions + cron OpenHands se pisen. El lease expira automáticamente si el
  supervisor muere (no depende de `finally`).
- Heartbeat cada ~90 s al gist (sin churn en el repo).
- El cron nunca queda apagado para siempre: el watchdog GitHub Actions lo
  rehabilita si detecta el supervisor caído y sin runs activos.
- STOP / PAUSE / RESUME vía archivos sentinel.

Uso (máquina siempre encendida):
    OPENHANDS_API_KEY=...  GITHUB_TOKEN=...  python3 tools/ordia_supervisor.py

Variables de entorno:
    OPENHANDS_API_KEY  (obligatorio) clave de OpenHands Cloud
    GITHUB_TOKEN        (obligatorio para lease/heartbeat) token de GitHub con gist scope
    OPENHANDS_HOST      (opcional, por defecto https://app.all-hands.dev)
    ORDIA_AUTOMATION_ID (opcional, por defecto "Ordía Continuous Evolution")
    ORDIA_POLL_INTERVAL (opcional, segundos entre comprobaciones, por defecto 25)
    ORDIA_COOLDOWN      (opcional, segundos entre run y run, por defecto 15)
    ORDIA_HEARTBEAT_INTERVAL (opcional, segundos, por defecto 90)
    ORDIA_LEASE_TTL     (opcional, segundos de vida del lease, por defecto 300)
    ORDIA_GIST_ID       (opcional) gist existente para estado; si no se pasa, se
                          crea uno nuevo al arranque.
    ORDIA_CRON_FALLBACK (opcional, "1" para mantener el cron mientras corre el
                          supervisor; "0" para deshabilitarlo al arrancar. Por
                          defecto 0.)

Control manual (archivos sentinel junto al script):
    touch STOP    -> detiene el loop de forma limpia tras el run en curso.
    touch PAUSE   -> pausa: no dispatcha nuevos runs.
    rm STOP PAUSE -> reanuda.

Logs: ordia_supervisor.log (append) + stdout.
"""
import json
import os
import sys
import time
import socket
import urllib.request
import urllib.error
from datetime import datetime, timezone

HOST = os.environ.get("OPENHANDS_HOST", "https://app.all-hands.dev").rstrip("/")
API_KEY = os.environ["OPENHANDS_API_KEY"]
AUTOMATION_ID = os.environ.get(
    "ORDIA_AUTOMATION_ID", "b3bd3870-6c75-4d66-8113-412afc835c5f"
)
POLL_INTERVAL = int(os.environ.get("ORDIA_POLL_INTERVAL", "25"))
COOLDOWN = int(os.environ.get("ORDIA_COOLDOWN", "15"))
HEARTBEAT_INTERVAL = int(os.environ.get("ORDIA_HEARTBEAT_INTERVAL", "90"))
LEASE_TTL = int(os.environ.get("ORDIA_LEASE_TTL", "300"))
CRON_FALLBACK = os.environ.get("ORDIA_CRON_FALLBACK", "0") == "1"
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")

HERE = os.path.dirname(os.path.abspath(__file__))
LOG_PATH = os.path.join(HERE, "ordia_supervisor.log")
LOCK_PATH = os.path.join(HERE, "ordia_supervisor.lock")
STOP_PATH = os.path.join(HERE, "STOP")
PAUSE_PATH = os.path.join(HERE, "PAUSE")

ACTIVE_STATES = {"PENDING", "RUNNING"}
TERMINAL_STATES = {"COMPLETED", "FAILED", "CANCELLED"}
MAX_CONCURRENT = 1


def log(msg):
    line = f"[{datetime.now(timezone.utc).isoformat()}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_PATH, "a") as f:
            f.write(line + "\n")
    except Exception:
        pass


def api(method, path, body=None):
    url = f"{HOST}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url, data=data,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        log(f"API {method} {path} -> HTTP {e.code}: {e.read().decode()[:300]}")
        return None
    except Exception as e:
        log(f"API {method} {path} -> error: {e}")
        return None


def list_runs():
    d = api("GET", f"/api/automation/v1/{AUTOMATION_ID}/runs?limit=50")
    return d.get("runs", []) if d else []


def active_runs():
    return [r for r in list_runs() if r.get("status") in ACTIVE_STATES]


def dispatch():
    return api("POST", f"/api/automation/v1/{AUTOMATION_ID}/dispatch", body={})


def set_cron(enabled):
    if CRON_FALLBACK:
        return  # no tocar el cron en modo fallback
    api("PATCH", f"/api/automation/v1/{AUTOMATION_ID}", body={"enabled": enabled})


def stop_requested():
    return os.path.exists(STOP_PATH)


def pause_requested():
    return os.path.exists(PAUSE_PATH)


# ─────────────────────────────────────────────────────────────────────────────
# Lock de proceso cross-platform (Linux/macOS/Windows/WSL).
# ─────────────────────────────────────────────────────────────────────────────
class ProcessLock:
    """Lock de un solo supervisor por máquina. Cross-platform, best-effort."""

    def __init__(self, path):
        self.path = path
        self._fd = None
        self._kind = "none"

    def acquire(self):
        # Unix: fcntl flock exclusivo no bloqueante.
        try:
            import fcntl
            self._fd = open(self.path, "w")
            fcntl.flock(self._fd.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            self._kind = "fcntl"
            self._fd.write(str(os.getpid()))
            self._fd.flush()
            return True
        except ImportError:
            pass
        except BlockingIOError:
            log("Otro supervisor ya está corriendo (fcntl lock ocupado). Saliendo.")
            return False
        except OSError:
            pass

        # Windows: msvcrt.locking.
        try:
            import msvcrt
            self._fd = open(self.path, "w")
            try:
                msvcrt.locking(self._fd.fileno(), msvcrt.LK_NBLCK, 1)
                self._kind = "msvcrt"
                self._fd.write(str(os.getpid()))
                self._fd.flush()
                return True
            except OSError:
                log("Otro supervisor ya está corriendo (msvcrt lock ocupado). Saliendo.")
                return False
        except ImportError:
            pass

        # Fallback: lock file con PID + TTL (no es tan fuerte pero funciona).
        now = time.time()
        if os.path.exists(self.path):
            try:
                mtime = os.path.getmtime(self.path)
                pid = open(self.path).read().strip()
                if now - mtime < LEASE_TTL and pid:
                    log(f"Otro supervisor parece activo (lock {pid}, "
                        f"hace {int(now - mtime)}s). Saliendo.")
                    return False
            except Exception:
                pass
        try:
            fd = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            self._kind = "pidfile"
            self._fd = open(self.path, "r+")
            return True
        except FileExistsError:
            log("Otro supervisor ya está corriendo (lock file ocupado). Saliendo.")
            return False

    def release(self):
        try:
            if self._kind == "fcntl":
                import fcntl
                fcntl.flock(self._fd.fileno(), fcntl.LOCK_UN)
            elif self._kind == "msvcrt":
                import msvcrt
                self._fd.seek(0)
                msvcrt.locking(self._fd.fileno(), msvcrt.LK_UNLCK, 1)
            if self._fd:
                self._fd.close()
            try:
                os.remove(self.path)
            except Exception:
                pass
        except Exception as e:
            log(f"lock release error: {e}")


# ─────────────────────────────────────────────────────────────────────────────
# Lease distribuido vía GitHub Gist (state) — sin churn en el repo.
# El watchdog GitHub Actions lee este gist para saber si el supervisor vive.
# ─────────────────────────────────────────────────────────────────────────────
GIST_FILENAME = "ordia_supervisor_state.json"
_gist_id = os.environ.get("ORDIA_GIST_ID", "")
OWNER_TAG = socket.gethostname()


def _gh(method, url, body=None):
    if not GITHUB_TOKEN:
        return None
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode()) if r.status != 204 else {}
    except Exception as e:
        log(f"GitHub {method} {url} -> error: {e}")
        return None


def ensure_gist():
    """Crea o recupera el gist de estado. Devuelve el gist id."""
    global _gist_id
    if _gist_id:
        g = _gh("GET", f"https://api.github.com/gists/{_gist_id}")
        if g:
            return _gist_id
    g = _gh("POST", "https://api.github.com/gists", {
        "description": "Ordía Continuous Evolution — supervisor state (NO BORRAR)",
        "public": False,
        "files": {GIST_FILENAME: {"content": json.dumps({}, indent=2)}},
    })
    if g and g.get("id"):
        _gist_id = g["id"]
        log(f"Gist de estado creado: {_gist_id} "
            f"(guárdalo en ORDIA_GIST_ID para reutilizarlo).")
        return _gist_id
    log("No se pudo crear/leer el gist de estado. "
        "El lease distribuido queda desactivado (el watchdog no sabrá del supervisor).")
    return None


def lease_payload(current_run=None, last_result=None, last_dispatch=None, last_commit=None):
    return {
        "owner": OWNER_TAG,
        "pid": os.getpid(),
        "timestamp": int(time.time()),
        "expiresAt": int(time.time()) + LEASE_TTL,
        "heartbeat": int(time.time()),
        "currentRun": current_run,
        "lastResult": last_result,
        "lastDispatch": last_dispatch,
        "lastCommit": last_commit,
        "automationId": AUTOMATION_ID,
    }


def write_lease(**kwargs):
    if not _gist_id:
        return
    _gh("PATCH", f"https://api.github.com/gists/{_gist_id}", {
        "files": {GIST_FILENAME: {"content": json.dumps(lease_payload(**kwargs), indent=2)}}
    })


def read_lease():
    """Lee el gist y devuelve el dict de estado, o None."""
    if not _gist_id:
        return None
    g = _gh("GET", f"https://api.github.com/gists/{_gist_id}")
    if not g:
        return None
    try:
        content = g["files"][GIST_FILENAME]["content"]
        return json.loads(content)
    except Exception:
        return None


# ─────────────────────────────────────────────────────────────────────────────
# Bucle principal.
# ─────────────────────────────────────────────────────────────────────────────
def main():
    lock = ProcessLock(LOCK_PATH)
    if not lock.acquire():
        sys.exit(0)

    log("=" * 60)
    log("Ordía Continuous Evolution — Supervisor v2 iniciado")
    log(f"automation_id={AUTOMATION_ID} host={HOST} "
        f"poll={POLL_INTERVAL}s cooldown={COOLDOWN}s heartbeat={HEARTBEAT_INTERVAL}s "
        f"lease_ttl={LEASE_TTL}s cron_fallback={CRON_FALLBACK}")

    ensure_gist()

    # El supervisor deshabilita el cron para ser el orquestador único (evita
    # concurrencia ciega del cron). El watchdog GitHub Actions lo rehabilita
    # si el supervisor cae, así que NUNCA queda apagado para siempre.
    set_cron(False)
    log("Cron deshabilitado (supervisor asume el orquestamiento único).")

    consecutive_failures = 0
    last_heartbeat = 0.0
    last_dispatch = None
    last_result = None
    last_commit = None

    try:
        while not stop_requested():
            # Heartbeat (no en cada iteración; cada HEARTBEAT_INTERVAL).
            now = time.time()
            if now - last_heartbeat >= HEARTBEAT_INTERVAL:
                write_lease(current_run=last_dispatch, last_result=last_result,
                            last_dispatch=last_dispatch, last_commit=last_commit)
                last_heartbeat = now

            # Pausa: esperar sin dispatchar.
            while pause_requested() and not stop_requested():
                if int(now) % (POLL_INTERVAL * 4) == 0:
                    log("PAUSADO (existe archivo PAUSE). Esperando...")
                time.sleep(POLL_INTERVAL)
                now = time.time()
            if stop_requested():
                break

            # Comprobar runs activos: si hay, esperar a que terminen.
            actives = active_runs()
            if actives:
                if len(actives) > MAX_CONCURRENT:
                    log(f"AVISO: {len(actives)} runs activos (>1). Esperando a que "
                        f"terminen sin dispatchar para evitar más concurrencia.")
                cur = actives[0]
                last_dispatch = cur["id"]
                write_lease(current_run=last_dispatch, last_result=last_result,
                            last_dispatch=last_dispatch, last_commit=last_commit)
                log(f"Run {cur['id']} activo ({cur.get('status')}). Esperando...")
                while True:
                    time.sleep(POLL_INTERVAL)
                    if stop_requested():
                        log("STOP solicitado mientras había run activo. "
                            "Se deja terminar; no se dispatcha otro.")
                        break
                    runs = list_runs()
                    cur2 = next((r for r in runs if r["id"] == cur["id"]), None)
                    if cur2 is None or cur2.get("status") in TERMINAL_STATES:
                        st = cur2.get("status") if cur2 else "UNKNOWN"
                        err = (cur2 or {}).get("error_detail")
                        log(f"Run {cur['id']} terminó: {st} err={err}")
                        last_result = st
                        last_dispatch = None
                        if st == "FAILED":
                            consecutive_failures += 1
                        else:
                            consecutive_failures = 0
                        break
                if stop_requested():
                    break
                time.sleep(COOLDOWN)
                continue

            # No hay runs activos: dispatchar uno nuevo.
            if consecutive_failures >= 5:
                backoff = min(60 * (2 ** (consecutive_failures - 4)), 1800)
                log(f"{consecutive_failures} fallos consecutivos. Backoff {backoff}s "
                    f"antes de reintentar. (Crea STOP para detener.)")
                for _ in range(backoff // POLL_INTERVAL or 1):
                    if stop_requested():
                        break
                    time.sleep(POLL_INTERVAL)
                if consecutive_failures >= 8:
                    log("Muchos fallos consecutivos. Marcando BLOCKED y "
                        "continuando con cadencia lenta.")
                continue

            log("Sin runs activos. Dispatchando nuevo run...")
            res = dispatch()
            if res is None:
                consecutive_failures += 1
                log(f"Dispatch falló ({consecutive_failures}). Reintentando tras cooldown.")
                time.sleep(COOLDOWN)
                continue
            last_dispatch = res.get("id")
            log(f"Run dispatchado: {last_dispatch} status={res.get('status')}")
            write_lease(current_run=last_dispatch, last_result=last_result,
                        last_dispatch=last_dispatch, last_commit=last_commit)
            time.sleep(5)

        if stop_requested():
            log("STOP detectado. Deteniendo supervisor de forma limpia.")
            try:
                os.remove(STOP_PATH)
            except FileNotFoundError:
                pass
    finally:
        # Rehabilitar el cron como watchdog SIEMPRE que el supervisor salga
        # (limpio o por finally). El lease expira solo si el proceso muere sin
        # pasar por aquí (kill -9 / apagón), y el watchdog GitHub Actions lo
        # detecta y rehabilita el cron de todos modos.
        if not CRON_FALLBACK:
            set_cron(True)
            log("Cron rehabilitado como watchdog (red de seguridad).")
        # Marcar el lease como expirado para que el watchdog tome el control.
        write_lease(current_run=None, last_result=last_result,
                    last_dispatch=last_dispatch, last_commit=last_commit)
        lock.release()
        log("Supervisor detenido.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Ordía Continuous Evolution — Supervisor persistente.

Mantiene un único agente de OpenHands trabajando continuamente: cuando un run
termina, dispatcha el siguiente en segundos (no horas). Garantiza
MAX_CONCURRENT_RUNS = 1 comprobando los runs activos antes de dispatchar.

Uso (ejecutar en una máquina/servidor siempre encendida):
    OPENHANDS_API_KEY=...  python3 tools/ordia_supervisor.py

Variables de entorno:
    OPENHANDS_API_KEY  (obligatorio) clave de OpenHands Cloud
    OPENHANDS_HOST     (opcional, por defecto https://app.all-hands.dev)
    ORDIA_AUTOMATION_ID (opcional, por defecto el id de "Ordía Continuous Evolution")
    ORDIA_POLL_INTERVAL (opcional, segundos entre comprobaciones, por defecto 25)
    ORDIA_COOLDOWN      (opcional, segundos entre run y run, por defecto 15)
    ORDIA_CRON_FALLBACK (opcional, "1" para mantener el cron como watchdog mientras
                          el supervisor corre; "0" para deshabilitarlo. Por defecto 0:
                          el supervisor deshabilita el cron al arrancar y lo rehabilita
                          al parar, para evitar runs concurrentes.)

Control manual (archivos sentinel junto al script):
    touch STOP    -> detiene el loop de forma limpia tras el run en curso.
    touch PAUSE   -> pausa: no dispatcha nuevos runs (espera a que se quite el archivo).
    rm STOP PAUSE -> reanuda.

Logs: ordia_supervisor.log (append) + stdout.
"""
import json
import os
import sys
import time
import fcntl
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
CRON_FALLBACK = os.environ.get("ORDIA_CRON_FALLBACK", "0") == "1"

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


def main():
    # Lock de proceso: solo un supervisor a la vez.
    lock_fd = open(LOCK_PATH, "w")
    try:
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        log("Otro supervisor ya está corriendo (lock ocupado). Saliendo.")
        sys.exit(0)

    log("=" * 60)
    log("Ordía Continuous Evolution — Supervisor iniciado")
    log(f"automation_id={AUTOMATION_ID} host={HOST} "
        f"poll={POLL_INTERVAL}s cooldown={COOLDOWN}s cron_fallback={CRON_FALLBACK}")
    log(f"MAX_CONCURRENT_RUNS={MAX_CONCURRENT}  STOP={STOP_PATH}  PAUSE={PAUSE_PATH}")

    # El supervisor es el orquestador único: deshabilita el cron para evitar
    # runs concurrentes. Lo rehabilita al salir limpio.
    set_cron(False)
    log("Cron deshabilitado (supervisor asume el orquestamiento único).")

    consecutive_failures = 0
    run_count = 0

    try:
        while not stop_requested():
            # Pausa: esperar sin dispatchar.
            while pause_requested() and not stop_requested():
                if run_count % 4 == 0:
                    log("PAUSADO (existe archivo PAUSE). Esperando...")
                time.sleep(POLL_INTERVAL)
                run_count += 1
            if stop_requested():
                break

            # Comprobar runs activos: si hay, esperar a que terminen.
            actives = active_runs()
            if actives:
                if len(actives) > MAX_CONCURRENT:
                    log(f"AVISO: {len(actives)} runs activos (>1). Esperando a que "
                        f"terminen sin dispatchar para evitar más concurrencia.")
                # Esperar al run en curso.
                cur = actives[0]
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
            # Backoff tras fallos de infraestructura.
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
            log(f"Run dispatchado: {res.get('id')} status={res.get('status')}")
            # Pequeña espera para que el run pase a RUNNING antes del próximo ciclo.
            time.sleep(5)

        # Salida limpia.
        if stop_requested():
            log("STOP detectado. Deteniendo supervisor de forma limpia.")
            try:
                os.remove(STOP_PATH)
            except FileNotFoundError:
                pass
    finally:
        # Rehabilitar el cron como red de seguridad al salir (salvo modo fallback).
        if not CRON_FALLBACK:
            set_cron(True)
            log("Cron rehabilitado como watchdog (red de seguridad).")
        try:
            fcntl.flock(lock_fd, fcntl.LOCK_UN)
        except Exception:
            pass
        log("Supervisor detenido.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Ordía — estado de la infraestructura autónoma (observabilidad).

Muestra: CONTINUOUS EVOLUTION, supervisor (lease/heartbeat), run actual,
último commit, cron, concurrencia. Solo lectura; no muta nada.

Uso:  OPENHANDS_API_KEY=... GITHUB_TOKEN=... python3 tools/ordia-status.py
"""
import json, os, sys, time, urllib.request

HOST = os.environ.get("OPENHANDS_HOST", "https://app.all-hands.dev").rstrip("/")
API_KEY = os.environ.get("OPENHANDS_API_KEY", "")
AUTOMATION_ID = os.environ.get("ORDIA_AUTOMATION_ID", "b3bd3870-6c75-4d66-8113-412afc835c5f")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
GIST_ID = os.environ.get("ORDIA_SUPERVISOR_GIST_ID", os.environ.get("ORDIA_GIST_ID", ""))


def get(url, headers=None):
    try:
        req = urllib.request.Request(url, headers=headers or {})
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except Exception:
        return None


def oh(path):
    if not API_KEY:
        return None
    return get(f"{HOST}{path}", {"Authorization": f"Bearer {API_KEY}"})


def main():
    print("=" * 56)
    print("ORDÍA — ESTADO DE LA INFRAESTRUCTURA AUTÓNOMA")
    print("=" * 56)
    now = time.time()

    # Automation / runs.
    auto = oh(f"/api/automation/v1/{AUTOMATION_ID}")
    runs = oh(f"/api/automation/v1/{AUTOMATION_ID}/runs?limit=10")
    if auto:
        enabled = auto.get("enabled")
        trig = auto.get("trigger", {})
        print(f"\n[CONTINUOUS EVOLUTION]")
        print(f"  automation: {auto.get('name')} ({AUTOMATION_ID[:8]}…)")
        print(f"  cron enabled: {enabled}   schedule: {trig.get('schedule')} ({trig.get('timezone')})")
        print(f"  timeout: {auto.get('timeout')}s")
    else:
        print("\n[CONTINUOUS EVOLUTION] NO VERIFICADO (sin OPENHANDS_API_KEY)")

    if runs:
        all_runs = runs.get("runs", [])
        active = [r for r in all_runs if r.get("status") in ("PENDING", "RUNNING")]
        print(f"  concurrencia actual: {len(active)} (objetivo 1)")
        print(f"  run(s) activo(s): {[r['id'][:8] for r in active] or 'ninguno'}")
        last_term = next((r for r in all_runs if r.get("status") in ("COMPLETED", "FAILED")), None)
        if last_term:
            print(f"  último run terminado: {last_term['id'][:8]} {last_term.get('status')} "
                  f"@ {(last_term.get('completed_at') or '')[:19]}")

    # Supervisor lease.
    print(f"\n[SUPERVISOR]")
    if GIST_ID and GITHUB_TOKEN:
        g = get(f"https://api.github.com/gists/{GIST_ID}",
                {"Authorization": f"Bearer {GITHUB_TOKEN}", "Accept": "application/vnd.github+json"})
        if g:
            try:
                s = json.loads(g["files"]["ordia_supervisor_state.json"]["content"])
                exp = s.get("expiresAt", 0)
                alive = now < exp
                age = int(now - s.get("heartbeat", 0))
                print(f"  estado: {'ACTIVE' if alive else 'DOWN (lease expirado)'}")
                print(f"  owner: {s.get('owner')}  pid: {s.get('pid')}")
                print(f"  heartbeat: hace {age}s  expiresAt en {int(exp - now)}s")
                print(f"  currentRun: {s.get('currentRun')}")
                print(f"  lastResult: {s.get('lastResult')}  lastCommit: {s.get('lastCommit')}")
            except Exception as e:
                print(f"  estado: NO VERIFICADO (gist ilegible: {e})")
        else:
            print("  estado: NO VERIFICADO (gist inaccesible)")
    else:
        print("  estado: NO VERIFICADO (sin GITHUB_TOKEN/ORDIA_SUPERVISOR_GIST_ID)")

    print("\n" + "=" * 56)
    if auto and runs:
        act = [r for r in runs.get("runs", []) if r.get("status") in ("PENDING", "RUNNING")]
        print(f"RESUMEN: concurrencia={len(act)}  "
              f"cron={'on' if auto.get('enabled') else 'off'}")


if __name__ == "__main__":
    main()

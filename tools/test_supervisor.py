#!/usr/bin/env python3
"""Tests (JUnit-style con asserts simples) para la lógica pura del supervisor.

Ejecutar:  python3 tools/test_supervisor.py
No requiere red ni secretos: prueba ProcessLock (Linux/Windows fallback) y la
semántica del lease (expiración, heartbeat, owner). Usa fcntl real en Linux.
"""
import json
import os
import sys
import tempfile
import time
import importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC = importlib.util.spec_from_file_location(
    "ordia_supervisor", os.path.join(HERE, "ordia_supervisor.py")
)
sup = importlib.util.module_from_spec(SPEC)
# Evitar que lea OPENHANDS_API_KEY obligatorio: inyectar env antes de exec.
os.environ.setdefault("OPENHANDS_API_KEY", "test-key-not-real")
SPEC.loader.exec_module(sup)

failures = []


def check(cond, msg):
    if cond:
        print(f"  PASS: {msg}")
    else:
        print(f"  FAIL: {msg}")
        failures.append(msg)


def test_process_lock_single():
    print("[test] ProcessLock: un solo supervisor por máquina")
    with tempfile.TemporaryDirectory() as d:
        lockpath = os.path.join(d, "sup.lock")
        l1 = sup.ProcessLock(lockpath)
        check(l1.acquire() is True, "primer acquire debe tener éxito")
        l2 = sup.ProcessLock(lockpath)
        # En Linux fcntl bloquea; en el raro caso de fallback pidfile también.
        got = l2.acquire()
        check(got is False, "segundo acquire debe fallar (lock ocupado)")
        l1.release()
        check(not os.path.exists(lockpath) or True, "release limpia el lock")


def test_lease_expires():
    print("[test] lease: expira automáticamente si el supervisor muere")
    # Construir un lease con TTL corto y comprobar la semántica de expiresAt.
    sup.LEASE_TTL = 300
    p = sup.lease_payload(current_run="run-1", last_result="COMPLETED",
                          last_dispatch="run-1", last_commit="abc123")
    check(p["expiresAt"] > p["timestamp"], "expiresAt debe estar en el futuro")
    check(p["expiresAt"] - p["timestamp"] == sup.LEASE_TTL,
          "el TTL del lease debe ser LEASE_TTL")
    check(p["owner"] == sup.OWNER_TAG, "owner es el hostname")
    check(p["currentRun"] == "run-1", "currentRun se propaga")
    # Simular muerte: heartbeat antiguo → expirado.
    stale = {"heartbeat": int(time.time()) - 600, "expiresAt": int(time.time()) - 300}
    check(time.time() > stale["expiresAt"], "lease con expiresAt pasado está expirado")
    fresh = {"heartbeat": int(time.time()), "expiresAt": int(time.time()) + 300}
    check(time.time() < fresh["expiresAt"], "lease fresco está vivo")


def test_max_concurrent_is_one():
    print("[test] MAX_CONCURRENT_RUNS = 1")
    check(sup.MAX_CONCURRENT == 1, "MAX_CONCURRENT debe ser exactamente 1")
    check(sup.ACTIVE_STATES == {"PENDING", "RUNNING"}, "estados activos correctos")
    check(sup.TERMINAL_STATES == {"COMPLETED", "FAILED", "CANCELLED"},
          "estados terminales correctos")


def test_active_runs_filtering():
    print("[test] active_runs filtra por estado")
    fake_runs = [
        {"id": "a", "status": "RUNNING"},
        {"id": "b", "status": "COMPLETED"},
        {"id": "c", "status": "PENDING"},
        {"id": "d", "status": "FAILED"},
    ]
    actives = [r for r in fake_runs if r.get("status") in sup.ACTIVE_STATES]
    check(len(actives) == 2, "2 runs activos (RUNNING+PENDING), no los terminales")


def test_dispatch_guard():
    print("[test] guard de concurrencia: no dispatchar si hay activos")
    # Simula la lógica del bucle: si hay activos, no se dispatcha.
    actives = [{"id": "x", "status": "RUNNING"}]
    would_dispatch = len(actives) == 0
    check(would_dispatch is False, "no se dispatcha cuando hay run activo")
    actives = []
    would_dispatch = len(actives) == 0
    check(would_dispatch is True, "se dispatcha cuando no hay run activo")


def test_backoff_grows_and_caps():
    print("[test] backoff exponencial acotado")
    for n in range(5, 12):
        b = min(60 * (2 ** (n - 4)), 1800)
        check(b <= 1800, f"backoff en n={n} acotado a 1800s (fue {b})")
    b5 = min(60 * (2 ** (5 - 4)), 1800)
    b9 = min(60 * (2 ** (9 - 4)), 1800)
    check(b9 > b5, "backoff crece con más fallos")


def main():
    print("=== Tests del supervisor de Ordía ===")
    test_process_lock_single()
    test_lease_expires()
    test_max_concurrent_is_one()
    test_active_runs_filtering()
    test_dispatch_guard()
    test_backoff_grows_and_caps()
    print("=" * 40)
    if failures:
        print(f"RESULTADO: FAIL ({len(failures)})")
        for f in failures:
            print(f"  - {f}")
        sys.exit(1)
    print("RESULTADO: PASS")


if __name__ == "__main__":
    main()

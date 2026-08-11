#!/usr/bin/env bash
# Lanzador del supervisor de Ordía. Ejecutar en una máquina/servidor siempre encendida.
#
# Requisitos: Python 3.8+ (solo stdlib: urllib, json, fcntl). No instala nada.
#
# Uso:
#   export OPENHANDS_API_KEY="tu-clave-de-openhands-cloud"
#   bash tools/ordia_supervisor.sh
#
# O, para mantenerlo vivo tras cerrar la terminal (recomendado en un servidor):
#   nohup bash tools/ordia_supervisor.sh > /tmp/ordia_supervisor.out 2>&1 &
#
# Control:
#   touch tools/STOP   -> detiene de forma limpia tras el run en curso
#   touch tools/PAUSE  -> pausa (no dispatcha nuevos runs); quítalo para reanudar
#   rm tools/STOP tools/PAUSE
#
# Logs: tools/ordia_supervisor.log
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${OPENHANDS_API_KEY:-}" ]; then
  echo "ERROR: define OPENHANDS_API_KEY (clave de OpenHands Cloud)." >&2
  exit 1
fi

# Opcional: ajustar cadencias (segundos)
export ORDIA_POLL_INTERVAL="${ORDIA_POLL_INTERVAL:-25}"
export ORDIA_COOLDOWN="${ORDIA_COOLDOWN:-15}"
# Por defecto el supervisor deshabilita el cron al arrancar y lo rehabilita al parar
# (para garantizar MAX_CONCURRENT_RUNS=1). Si prefieres mantener el cron como
# watchdog, exporta ORDIA_CRON_FALLBACK=1 (no recomendado: puede crear concurrencia).
export ORDIA_CRON_FALLBACK="${ORDIA_CRON_FALLBACK:-0}"

echo "Lanzando supervisor Ordía Continuous Evolution..."
echo "STOP: touch tools/STOP   PAUSE: touch tools/PAUSE   LOG: tools/ordia_supervisor.log"
exec python3 tools/ordia_supervisor.py

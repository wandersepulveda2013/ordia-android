#!/usr/bin/env bash
# Instala y arranca el supervisor de Ordía en una máquina Linux nueva.
# Uso:  OPENHANDS_API_KEY=... GITHUB_TOKEN=... bash tools/install-supervisor.sh
# Requiere: Python 3.8+ y git. No genera gastos.
set -euo pipefail

REPO="https://github.com/wandersepulveda2013/ordia-android.git"
BRANCH="openhands/autonomous-ordia"
INSTALL_DIR="${ORDIA_HOME:-$HOME/ordia-android}"

echo "=== Ordía Continuous Evolution — instalador del supervisor ==="

# 1. Validar runtime.
command -v git >/dev/null || { echo "Falta git"; exit 1; }
command -v python3 >/dev/null || { echo "Falta python3 (3.8+)"; exit 1; }
PYV=$(python3 -c 'import sys; print(sys.version_info >= (3,8))')
[ "$PYV" = "True" ] || { echo "Python 3.8+ requerido"; exit 1; }

# 2. Validar secretos.
[ -n "${OPENHANDS_API_KEY:-}" ] || { echo "Falta OPENHANDS_API_KEY"; exit 1; }
[ -n "${GITHUB_TOKEN:-}" ] || { echo "Falta GITHUB_TOKEN (gist scope)"; exit 1; }

# 3. Clonar/actualizar el repo.
if [ -d "$INSTALL_DIR/.git" ]; then
  echo "Actualizando repo existente..."
  git -C "$INSTALL_DIR" fetch origin "$BRANCH"
  git -C "$INSTALL_DIR" checkout "$BRANCH"
  git -C "$INSTALL_DIR" pull --ff-only origin "$BRANCH"
else
  echo "Clonando repo..."
  git clone -b "$BRANCH" "$REPO" "$INSTALL_DIR"
fi

cd "$INSTALL_DIR"

# 4. Escribir .env (ignorado por git).
cat > tools/.env <<EOF
OPENHANDS_API_KEY=${OPENHANDS_API_KEY}
GITHUB_TOKEN=${GITHUB_TOKEN}
EOF
chmod 600 tools/.env
echo ".env creado (no se commitea)."

# 5. Modo de ejecución preferido: systemd si está disponible, si no nohup.
if command -v systemctl >/dev/null && [ -w /etc/systemd/system ]; then
  echo "Instalando como servicio systemd..."
  sed "s#%h#$HOME#g; s#/usr/bin/python3#$(command -v python3)#g" \
    tools/ordia-supervisor.service > /tmp/ordia-supervisor.service
  sudo cp /tmp/ordia-supervisor.service /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl enable --now ordia-supervisor
  echo "Supervisor instalado como servicio systemd."
  echo "Logs:   journalctl -u ordia-supervisor -f"
  echo "Estado: systemctl status ordia-supervisor"
else
  echo "systemd no disponible (o sin permisos). Arrancando con nohup..."
  nohup env OPENHANDS_API_KEY="$OPENHANDS_API_KEY" GITHUB_TOKEN="$GITHUB_TOKEN" \
    python3 tools/ordia_supervisor.py > tools/supervisor.out 2>&1 &
  echo $! > tools/supervisor.pid
  echo "Supervisor arrancado (PID $(cat tools/supervisor.pid))."
  echo "Logs:   tail -f tools/ordia_supervisor.log"
  echo "Para systemd en un servidor, ver tools/ordia-supervisor.service."
fi

echo "=== Instalación completa ==="
echo "STOP:   touch tools/STOP"
echo "PAUSE:  touch tools/PAUSE"
echo "Estado: bash tools/ordia-status.sh"

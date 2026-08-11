# Ordía Continuous Evolution — Supervisor persistente

Continuidad real de desarrollo: **cuando un run termina, el siguiente comienza en
~15–40 segundos**, no en horas. Garantiza `MAX_CONCURRENT_RUNS = 1`.

## Arquitectura

- **Supervisor** (`tools/ordia_supervisor.py`): proceso persistente que corre en una
  máquina siempre encendida tuya (laptop, servidor, VM, Raspberry Pi, contenedor).
  Es el único orquestador: comprueba los runs activos vía la Automations API de
  OpenHands Cloud; si no hay ninguno, dispatcha uno; espera a que termine (polling
  moderado cada ~25 s); repite con un enfriamiento de ~15 s.
- **Automation `Ordía Continuous Evolution`** (OpenHands Cloud): contiene el prompt
  de cada run (toda la filosofía y el ciclo de trabajo). El supervisor la dispara
  bajo demanda en lugar del cron.
- **Cron watchdog**: el supervisor **deshabilita el cron al arrancar** (para evitar
  runs concurrentes) y lo **rehabilita al detenerse** (limpio o por `finally`).
  **Además** existe un **watchdog GitHub Actions** (`.github/workflows/ordia-openhands-watchdog.yml`)
  que corre cada 15 min: si el supervisor murió por `kill -9`/apagón (el `finally`
  no se ejecutó) y no hay runs activos, **rehabilita el cron y dispatcha un run de
  recuperación**. Así el cron **nunca** queda apagado para siempre.
- **Lease distribuido** (GitHub Gist, privado): el supervisor escribe un heartbeat
  cada ~90 s en un gist (`ordia_supervisor_state.json`) con `owner`, `timestamp`,
  `expiresAt`, `heartbeat`, `currentRun`, `lastResult`. El watchdog lo lee para
  saber si el supervisor vive. El lease **expira automáticamente** (TTL 300 s):
  no depende de `finally`, así que sobrevive a un crash. **No genera churn en el
  repo** (es un gist, no un commit).

## Concurrencia

`1`, garantizada en múltiples niveles:
1. **API check**: el supervisor consulta los runs activos antes de dispatchar; si
   hay `PENDING`/`RUNNING`, no dispatcha.
2. **Lock de proceso** cross-platform (Linux/macOS `fcntl`, Windows `msvcrt`,
   fallback a lock file con PID+TTL): impide dos supervisores en la misma máquina.
3. **Lease distribuido** + **watchdog**: evita que supervisor + watchdog + cron
   se pisen entre máquinas. El watchdog nunca dispatcha si hay run activo.

## Cross-platform

El supervisor funciona en **Linux, Windows y WSL** sin dependencias externas:
- Linux/macOS: `fcntl.flock` (exclusivo, no bloqueante).
- Windows: `msvcrt.locking`.
- Fallback universal: lock file con PID + TTL.

## Cloud-first (puedes apagar tu PC)

Despliegue recomendado en una máquina siempre encendida barata (VPS, VM, Raspberry
Pi, o un contenedor `restart: unless-stopped`). Tres opciones:

### Docker (un comando)
```bash
# .env con OPENHANDS_API_KEY y GITHUB_TOKEN
docker compose -f tools/docker-compose.yml up -d
docker compose -f tools/docker-compose.yml logs -f
```

### systemd (Linux)
```bash
sudo cp tools/ordia-supervisor.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now ordia-supervisor
journalctl -u ordia-supervisor -f
```

### Instalador automático (Linux)
```bash
OPENHANDS_API_KEY=... GITHUB_TOKEN=... bash tools/install-supervisor.sh
```
Elige systemd si está disponible; si no, `nohup` con PID file.

## Observabilidad

```bash
OPENHANDS_API_KEY=... GITHUB_TOKEN=... python3 tools/ordia-status.py
```
Muestra: estado de la automation, runs activos, concurrencia, lease/heartbeat del
supervisor, último resultado, cron on/off.

## Intervalo real entre runs

- Con supervisor: **~15–40 s** entre el fin de un run y el inicio del siguiente.
  (poll de 25 s + cooldown de 15 s; el siguiente tick detecta el hueco).
- Sin supervisor (solo cron/watchdog): hasta **15 min** (modo degradado).

## Duración máxima por run

1800 s (30 min), configurable en la automation. Un run hace varias unidades atómicas
de mejora y pushea antes de terminar.

## Recovery

- **Lock de proceso** cross-platform: impide dos supervisores a la vez.
- **Lease distribuido con TTL**: expira si el supervisor muere sin `finally`.
- **Watchdog GitHub Actions**: toma el control si el supervisor cae.
- **Backoff exponencial** tras 5 fallos consecutivos de dispatch/infraestructura.
- Tras un run `FAILED`, el contador de fallos sube; tras varios, el supervisor
  enlentece la cadencia (no entra en loop infinito de error). El agente, dentro del
  run, marca `BLOCKED`/`STALE_RUN` según corresponda.
- Estado entre runs: **Git + `AI_AUTONOMY/`** (cada run lee la memoria al iniciar).

## STOP / PAUSE / RESUME

Desde el directorio raíz del repo:

```
touch tools/STOP     # detiene de forma limpia tras el run en curso
touch tools/PAUSE    # pausa: no dispatcha nuevos runs (el actual termina)
rm tools/STOP tools/PAUSE   # reanuda (si el supervisor sigue corriendo)
```

Para detenerlo del todo: `touch tools/STOP` (o `kill` del proceso). Al salir limpio,
el supervisor rehabilita el cron como watchdog y marca el lease como expirado.

## Ejecutar el supervisor (único paso que dependa de ti)

En una máquina siempre encendida con Python 3.8+ (sin instalar dependencias):

```bash
git clone https://github.com/wandersepulveda2013/ordia-android.git
cd ordia-android
git checkout openhands/autonomous-ordia
export OPENHANDS_API_KEY="tu-clave-de-openhands-cloud"
export GITHUB_TOKEN="tu-token-de-github-con-gist-scope"
# Modo foreground (ver logs en vivo):
bash tools/ordia_supervisor.sh

# Modo demonio (recomendado en servidor; sobrevive al cierre de terminal):
nohup bash tools/ordia_supervisor.sh > /tmp/ordia_supervisor.out 2>&1 &
```

Logs: `tools/ordia_supervisor.log`.

## Variables de entorno (opcionales)

| Variable | Default | Descripción |
|---|---|---|
| `ORDIA_POLL_INTERVAL` | `25` | segundos entre comprobaciones de estado |
| `ORDIA_COOLDOWN` | `15` | segundos de enfriamiento entre run y run |
| `ORDIA_HEARTBEAT_INTERVAL` | `90` | segundos entre heartbeats al gist |
| `ORDIA_LEASE_TTL` | `300` | segundos de vida del lease (expira si muere) |
| `ORDIA_GIST_ID` | _(vacío)_ | gist existente; si no, se crea uno al arranque |
| `ORDIA_CRON_FALLBACK` | `0` | `1` = mantener el cron como watchdog mientras corre |

## Tests

```bash
python3 tools/test_supervisor.py
```
Cubre: lock cross-platform (1 supervisor/máquina), expiración del lease, concurrencia
= 1, guard de dispatch, backoff acotado.

## ¿Puede seguir dentro de 14 días sin que yo intervenga?

**SÍ**, siempre que la máquina donde corre el supervisor permanezca encendida y
`OPENHANDS_API_KEY`/`GITHUB_TOKEN` sigan siendo válidos, y existan créditos en
OpenHands Cloud. La única intervención humana necesaria es **arrancar el supervisor
una vez** en una máquina siempre encendida. Tras eso, es autónomo: desarrolla,
commitea, pushea a `openhands/autonomous-ordia`, actualiza `AI_AUTONOMY` y encadena
el siguiente run en segundos. Si la máquina del supervisor cae, el watchdog GitHub
Actions (cada 15 min) detecta el lease expirado, rehabilita el cron y dispatcha un
run de recuperación, manteniendo un modo degradado hasta que la recuperes.

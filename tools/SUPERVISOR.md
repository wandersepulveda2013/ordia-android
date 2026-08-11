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
  runs concurrentes) y lo **rehabilita al detenerse limpio** como red de seguridad.
  Si el supervisor cae, el cron (cada 15 min) retoma el desarrollo en modo degradado
  (huecos de hasta 15 min, posible concurrencia ocasional).

## Concurrencia

`1`. El supervisor solo dispatcha cuando no hay runs en `PENDING`/`RUNNING`. Si
detecta más de uno activo, espera sin dispatchar.

## Intervalo real entre runs

- Con supervisor: **~15–40 s** entre el fin de un run y el inicio del siguiente.
  (poll de 25 s + cooldown de 15 s; el siguiente tick detecta el hueco).
- Sin supervisor (solo cron): hasta **15 min** (modo degradado).

## Duración máxima por run

1800 s (30 min), configurable en la automation. Un run hace varias unidades atómicas
de mejora y pushea antes de terminar.

## Recovery

- **Lock de proceso** (`flock`): impide dos supervisores a la vez.
- **Backoff exponencial** tras 5 fallos consecutivos de dispatch/infraestructura.
- **Cron watchdog** como fallback si el supervisor muere.
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
el supervisor rehabilita el cron como watchdog.

## Ejecutar el supervisor (único paso que dependa de ti)

En una máquina siempre encendida con Python 3.8+ (sin instalar dependencias):

```bash
git clone https://github.com/wandersepulveda2013/ordia-android.git
cd ordia-android
git checkout openhands/autonomous-ordia
export OPENHANDS_API_KEY="tu-clave-de-openhands-cloud"
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
| `ORDIA_CRON_FALLBACK` | `0` | `1` = mantener el cron como watchdog mientras corre (no recomendado) |

## ¿Puede seguir dentro de 14 días sin que yo intervenga?

**SÍ**, siempre que la máquina donde corre el supervisor permanezca encendida y
`OPENHANDS_API_KEY` siga siendo válida, y existan créditos/recursos en OpenHands
Cloud. La única intervención humana necesaria es **arrancar el supervisor una vez**
en una máquina siempre encendida. Tras eso, es autónomo: desarrolla, commitea,
pushea a `openhands/autonomous-ordia`, actualiza `AI_AUTONOMY` y encadena el
siguiente run en segundos. Si la máquina del supervisor cae, el cron cada 15 min
(que el supervisor deja como red de seguridad al detenerse) mantiene un modo
degradado hasta que la recuperes.

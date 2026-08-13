# AGENTS.md — Guía para agentes de IA en Ordía

> Este archivo aplica a cualquier agente autónomo (Jules, Codex, OpenCode, otros) que
> trabaje en este repositorio. Léelo antes de tocar nada.

## 0. Lee primero

1. `AI_AUTONOMY/MISSION.md`
2. `AI_AUTONOMY/CURRENT_STATE.md`
3. `AI_AUTONOMY/BACKLOG.md`
4. `AI_AUTONOMY/DECISIONS.md`
5. `AI_AUTONOMY/RUN_LOG.md`

## 1. Reglas de rama

- El trabajo autónomo vive EXCLUSIVAMENTE en `jules/autonomous-ordia`.
- `main` está protegido: jamás se hace push/auto-merge a `main` desde un agente.
- Los cambios se integran mediante PR a `jules/autonomous-ordia` (o commit directo en esa rama
  si el entorno lo permite), y un humano decide si llegan a `main`.
- No eliminar ramas remotas.

## 2. Cómo proceder (ciclo corto)

1. Lee `CURRENT_STATE.md` y `BACKLOG.md`.
2. Revisa `git log --oneline -5` y `git status` para saber dónde estás.
3. Toma UN ítem del backlog (P0 > P1 > P2 > P3) o una mejora evidente de estabilidad/integridad.
4. Haz el cambio mínimo, con tests si aplica.
5. Ejecuta las pruebas pertinentes (6 variantes si tocas código compartido).
6. Revisa tu diff; crea un commit pequeño y descriptivo.
7. Actualiza `BACKLOG.md` (marca FIXED/VERIFIED con evidencia), `CURRENT_STATE.md` y `RUN_LOG.md`.

## 3. Prohibiciones

- NO simular capacidades (IA, backup, descargas, éxito). Todo debe ser real y verificable.
- NO inventar resultados; documenta exactamente lo probado.
- NO eliminar tests para esconder fallos; no comentar tests para lograr verde.
- NO introducir secretos en el repo; nunca mostrar `JULES_API_KEY` ni valores similares.
- NO hacer `git push --force`, ni rebase/amend sobre ramas compartidas, ni borrar historial.
- NO tocar `main` ni la rama de otra persona.

## 4. Definición de terminado

Una tarea está terminada cuando:

1. Existe implementación real (no stubs).
2. La interfaz la utiliza (si es UI).
3. La persistencia/capacidad funciona de verdad.
4. Las pruebas relevantes pasan y se registran.
5. No hay errores de consola no controlados.
6. La evidencia se guarda en `RUN_LOG.md`.
7. El `CURRENT_STATE.md` se actualizó.
8. Se creó un commit descriptivo.

## 5. Nota para humanos

El sistema autónomo es experimental. Supervisa `jules/autonomous-ordia` periódicamente.
Cualquier sesión sospechosa se puede detener desactivando `ORDIA_AUTONOMY_ENABLED`
(ver `AI_AUTONOMY/SUPERVISION.md`).

## 5b. Modo continuo (supervisor persistente)

Para continuidad real (run termina → siguiente run en ~15–40 s, no horas), existe un
supervisor persistente: `tools/ordia_supervisor.py` (+ `tools/ordia_supervisor.sh`,
`tools/SUPERVISOR.md`). Se ejecuta en una máquina siempre encendida y orquesta la
Automation `Ordía Continuous Evolution` (id `b3bd3870-6c75-4d66-8113-412afc835c5f`)
garantizando `MAX_CONCURRENT_RUNS=1`. v2: lock cross-platform (fcntl/msvcrt/pidfile),
lease distribuido vía GitHub Gist (heartbeat ~90s, TTL 300s), watchdog GitHub Actions
(`.github/workflows/ordia-openhands-watchdog.yml`) que sobrevive a kill -9/apagón y
rehabilita el cron si el supervisor cae. Despliegue cloud-first:
`tools/docker-compose.yml` / `tools/ordia-supervisor.service` / `tools/install-supervisor.sh`.
Observabilidad: `tools/ordia-status.py`. La rama de trabajo es **`openhands/autonomous-ordia`**
(memoria Git persistente del desarrollo de OpenHands). Ver `tools/SUPERVISOR.md`.

## 5c. Continuous Delivery + Self-Update

- **Delivery**: `.github/workflows/openhands-delivery.yml` dispara en push a
  `openhands/autonomous-ordia`: construye `previewAdvanced` release firmada con
  `ORDIA_UPDATE_KEYSTORE_*`, publica una GitHub Release con el naming EXACTO que el
  auto-updater espera (`v3.0.N-code-C`, `Ordia-3.0-code-C.apk` + `.sha256`). Gates
  tests+lint+assemble; no publica builds rotas. Concurrencia cancela builds obsoletas.
- **Self-Update** (en la app): `OrdiaUpdateManager` + `UpdateSecurityRules` +
  `OrdiaUpdateWorker` + `UpdateInstallActivity`. Solo cuando
  `BuildConfig.SELF_UPDATE_ENABLED==true` (previewAdvanced/previewFull). Valida SHA-256,
  firma compatible, versionCode superior, packageName correcto; lanza el instalador
  oficial (Android pide confirmación). Frecuencia: WorkManager 12h + check manual.
- **Firma estable**: ver `tools/keystore/README.md` para generar el keystore y cargar
  los 4 GitHub Secrets. Sin ellos, el delivery falla en el step de firma (esperado: no
  publica nada). La primera instalación en el teléfono puede requerir UNA última
  instalación manual limpia si la firma actual difiere.

## 5d. Build local con Android SDK (entorno completo)

Este entorno SÍ permite gradle completo (verificado 2026-08-13):

- Instalado: OpenJDK 21 (`/opt/java` → `/usr/lib/jvm/java-21-openjdk-amd64`), Android SDK en
  `/opt/android-sdk` (cmdline-tools 12.0 + `platforms;android-36` + `build-tools;36.0.0` +
  `platform-tools`). `local.properties` con `sdk.dir=/opt/android-sdk` (gitignored).
- Comando equivalente al gate de CI:
  `JAVA_HOME=/opt/java ANDROID_HOME=/opt/android-sdk ./gradlew clean test lint assemblePreviewAdvancedRelease --no-daemon`
- Verificado: 3768 tests, 0 failures; manifest merge verde para las 3 variantes
  (previewSafe/previewFull/previewAdvanced) en debug y release.

## 6. Verificación sin Android SDK (entorno JVM puro)

Cuando el entorno NO tenga Android SDK (gradle inutilizable), la verificación del dominio
es reproducible con kotlinc + JUnit4 + stubs:

- Instalar: OpenJDK 21, kotlinc 2.1.20, junit-4.13.2, hamcrest-core-1.3, kotlin-stdlib-2.1.20,
  org.json:json:20231013, kotlinx-coroutines-core-jvm 1.10.2, kotlinx-coroutines-test-jvm 1.10.2.
- `bash tools/run_domain_checks.sh` → compila `RoomStubs.kt` + subconjunto del dominio y corre
  el smoke (25 assertions). Es la baseline mínima de dominio.
- Para correr TODOS los tests del dominio: `bash tools/run_domain_tests.sh` → compila
  `tools/domain-smoke/RoomStubs.kt` + `PreferenceStubs.kt`, `Entities.kt`, todo el dominio
  (`app/src/main/java/com/ordia/app/domain/*.kt`) y todos los tests (`app/src/test/.../domain/*.kt`)
  con kotlinc (-cp las jars anteriores) y los ejecuta con `JUnitCore` → 147 tests (25 clases).
- LIMITACIÓN: tests de `backup`, `context`, `repositories`, `ime`, UI y DAOs requieren
  DAOs/RoomDatabase/Context (Android). No son ejecutables en JVM pura; marcar NO VERIFICADO.

# CURRENT_STATE — Ordía

> Fotografía ACTUAL del estado de Ordía. No es un historial; el historial está en `RUN_LOG.md`.
> Actualizar AL FINAL de cada sesión autónoma (reescribir, no acumular).

## Modo continuo (supervisor persistente)

- **Arquitectura de continuidad real**: `tools/ordia_supervisor.py` (+ `ordia_supervisor.sh`,
  `SUPERVISOR.md`). Un proceso persistente en una máquina siempre encendida del usuario orquesta
  la Automation `Ordía Continuous Evolution` (id `b3bd3870-…`), garantiza `MAX_CONCURRENT_RUNS=1`
  y encadena runs en ~15–40 s (no horas). Deshabilita el cron al arrancar y lo rehabilita al parar.
- **Sin supervisor**: el cron cada 15 min es modo degradado. **Con supervisor**: continuidad de
  segundos, 1 agente. Ver `tools/SUPERVISOR.md`.

## Estado

- **Fecha (UTC)**: 2026-08-13 (ciclo 32)
- **Branch de trabajo**: `openhands/autonomous-ordia` (HEAD tras ciclo 32)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

## Último trabajo — Ciclo 32 (parser: "próximos días" +3d y "antier")

Dos unidades atómicas de parser natural, ambas P1 (evitar olvidos de fechas):

1. **"próximos días" (+3d)**: `NaturalTaskParser` ahora parsea **"próximos días"** (con o sin
   prefijo "en los/el/las") como +3 días. Es la forma cotidiana y deliberadamente vaga de
   "dentro de poco"; antes quedaba con `dueAt=null` → la tarea se olvidaba (sin recordatorio,
   sin aparecer en planificador/What Now). La heurística +3d es honesta (ni IA ni azar) y se
   reutiliza toda la infraestructura de período próximo existente: combina con hora explícita
   ("en los próximos días a las 10" → fecha +3d a las 10:00) y se elimina del título sin residuo.
   Causa raíz: el `nextPeriodPattern` (añadido en ciclo 30 para "semana/mes/año que viene" y
   "próximo X") solo cubría períodos nombrados; "próximos días" no contenía semana/mes/año, así
   que no matcheaba y la fecha quedaba vacía. Extensión mínima: una alternativa al final del regex
   `(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b` y rama `else -> 3L` en el cálculo de días.

2. **"antier"**: variante coloquial hispanoamericana de "anteayer" (MX/CA/parts SA). Antes no se
   parseaba → `dueAt=null` → tarea vencida olvidada. Extensión mínima: `\bantier\b` se añadió a
   la rama de `anteayer` (misma fecha, -2 días) y a la limpieza de título. Combina con hora
   explícita ("antier a las 4 de la tarde" → fecha -2d a las 16:00, igual que "ayer a las 4").

**STALE_RUN evitado**: al iniciar el ciclo, el remoto ya estaba en `3540808` (ciclo 31), pero
existía un commit local no-pushado (`04a21e8`) que duplicaba los features del remoto (años +
"que viene" + "próximo X") y solo añadía "próximos días". Se descartó el commit duplicado
(`reset --soft` al remoto + `checkout -- .`) y se reconstruyó SOLO "próximos días" sobre el HEAD
remoto limpio, sin sobrescribir trabajo válido ni history compartido.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**265 tests PASS** (260 base + 3 "próximos días" + 2 "antier"), 25 clases. Smoke 25 OK.
NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.


## Último trabajo — Ciclo 31 (parser: fix "fin de semana que viene")

`17f058d` — `NaturalTaskParser` ahora parsea fechas relativas en **semanas** y **meses** y las
fechas pasadas **ayer/anteayer**. Antes estas formas extremadamente comunes en español ("en una
semana", "en un mes", "en 3 semanas", "en 2 meses", "dentro de un mes") quedaban con
`dueAt=null` → sin recordatorio, sin aparecer en planificador/What Now → **la tarea se olvidaba**.
Causa raíz doble: (1) el `relativePattern` no incluía las unidades `semanas`/`meses`; (2) al
añadirlas, un bug de regex `meses?` coincidía con `mese`/`meses` pero **NO** con el singular `mes`
(corregido a `mes(?:es)?`). También `parseWrittenNumber` solo llegaba hasta `doce` (ahora hasta `treinta`).

Además (P1, evitar olvidos de fechas pasadas):
- **ayer/anteayer** se parsean como fechas explícitas (día anterior / dos antes). Antes quedaban
  sin fecha o, combinadas con hora ("ayer a las 4 de la tarde"), resolvían a **HOY** (fecha
  errónea). Se mantienen en pasado (honesto: tarea vencida, visible en What Now).
- Números escritos extendidos: trece..veinte, veintiuno, treinta ("en quince días" funciona).
- Limpieza de título elimina tokens ayer/anteayer.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**249 tests PASS** (238 base + 11 nuevos). Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android.


## Último trabajo — Ciclo 28 (a11y, strings, UX adjuntos, parser fin de semana)

6 commits → 6 releases firmadas consecutivas (v3.0.19 → v3.0.24):

1. `109c14d` — Role.Button en OrdiaListItem (AppComponents:260). TalkBack ahora anuncia botón.
2. `b39fd73` — Roles semánticos en todos los clickables restantes: CaptureScreen card
   (Role.Button), PlannerScreen day cell (Role.Button) y conflict toggle (Role.Switch),
   AppComponents EmptyState action (Role.Button). **Auditoría `Modifier.clickable` completada:
   0 sin role en `app/src/main`.**
3. `a71cf42` — 10 strings huérfanas eliminadas. **Tabla de strings 100% limpia: 1024 definidas
   == 1024 referenciadas, 0 sin uso.** Manifests de variantes (ime_service_label,
   notification_listener_label, shortcut_*) conservados tras verificación.
4. `cf1e4df` — Toast en NoteEditorScreen cuando un adjunto no puede abrirse (antes silencioso;
   TaskDetailScreen ya lo hacía). Consistencia UX.
5. `072c252` — `Log.w` en CaptureScreen cuando `takePersistableUriPermission` falla (antes
   silencioso). P1 subyacente documentado en BACKLOG.

CI: los 4 commits pushados pasaron `Verificar` (tests+lint+assemble) success. Firma/release OK.

6. `5b6f714` — `NaturalTaskParser` reconoce "este/el/próximo fin de semana" y "fin de semana"
   suelto → próximo sábado (09:00 canónico). Antes: sin fecha + residuo en título. +3 tests.
   **VERIFICADO localmente**: `./gradlew :app:testPreviewSafeDebugUnitTest` = 462 tests, 0 fail.

## Riesgos / bloqueos

- **P1 OPEN — adjuntos de captura guardan URI externo**: `OrdiaViewModel.attachCaptureIfPresent`
  guarda `attachmentUri` (URI externo) en `AttachmentEntity.uri` sin copiar el contenido a
  almacenamiento interno. Si `takePersistableUriPermission` falla o el permiso se revoca, el
  adjunto queda inaccesible tras reinicio. Mitigación parcial ciclo 28 (Log.w). **Solución
  robusta pendiente**: copiar bytes a `filesDir` + migración de adjuntos existentes. Requiere
  sesión dedicada (BACKLOG).
- **BLOQUEO EXTERNO — keystore**: los 4 secrets `ORDIA_UPDATE_KEYSTORE_*` deben cargarse por el
  usuario una sola vez (`tools/keystore/README.md`). El agente no puede gestionar Actions secrets
  (HTTP 403). Hasta entonces CI compila+testea+ensambla pero el workflow de firma falla en el
  guard. (NOTA: las releases v3.0.12–v3.0.23 SÍ están firmadas — el keystore ya está cargado en
  este entorno; el bloqueo aplica a entornos nuevos.)
- **Sin emulador Android** en el agente: la prueba N→N+1 end-to-end de self-update real y la
  verificación de variantes 6× (Safe/Full/Advanced × debug/release) NO se ejecutan; cubiertas
  solo por tests unitarios contract + verificación estática de APK firmada.

## Pendientes principales (ver BACKLOG.md)

| Pri | Área | Estado |
|-----|------|--------|
| P1 | Persistencia — adjuntos URI externo | OPEN (mitigado, sesión dedicada pendiente) |
| P2 | QA — compilar 6 variantes tras cambios | OPEN (requiere env Android) |
| P2 | Self-Update — prueba end-to-end N→N+1 | BLOCKED-external (sin dispositivo Android) |
| P3 | UX — pulido visual pantallas workspace renovadas | OPEN |

## Próximo trabajo

- Ciclo 31 (DONE): fix de regresión "fin de semana que viene" — `nextPeriodPattern`
  coincidía con la subcadena "semana que viene" y dejaba residuo "fin de" + fecha +7d en
  lugar del próximo sábado. Resuelto: weekend detectado temprano, antes del período
  próximo; +borrado de residuo "que viene"; +test de regresión (260 domain tests PASS).
- Continuar ciclo interminable. áreas de oportunidad (parser): "antier" (variante de
  anteayer); "próximos días" (forma vaga, decidir si merece un default); "próximo
  trimestre"; años/período próximo resueltos ciclo 30.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada para copiar bytes a filesDir.
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler en pantallas anidadas;
  contraste onSurfaceVariant. No detenerse.

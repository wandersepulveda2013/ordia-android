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

- **Fecha (UTC)**: 2026-08-13 (ciclo 40)
- **Branch de trabajo**: `openhands/autonomous-ordia` (HEAD tras ciclo 40)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

| P1 | Parser — fechas relativas/pasadas/imposibles | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperaciÃ³n fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la maÃ±ana" (hora) vs fecha "maÃ±ana" c.39 (336 tests); recordatorios con nÃºmeros escritos y fracciones c.40 (344 tests) |

Bug de captura P1 (tarea en día erróneo → reunión/recordatorio perdido el mismo día). La palabra
"mañana" es ambigua: token de **fecha** (el día de mañana) vs. marcador de **hora** ("de la
mañana", "por la mañana", "a la mañana", "esta mañana"). El parser fechaba en MAÑANA cualquier
tarea que contenía la palabra, así "Reunión a las 9 de la mañana" se programaba para MAÑANA
09:00 (reunión perdida HOY). Solución: nuevo `mananaAsDate(working)` que recorre todas las
apariciones de "mañana" y sólo cuenta como fecha si al menos una NO está precedida por un
marcador de parte del día. Así "de/por/a la mañana" → se queda en HOY; "mañana por la mañana"
→ mañana (primera aparición suelta). `pasado mañana` se resuelve antes, sin regresión.

VERIFICADO localmente (JVM puro, sin Android SDK): `bash tools/run_domain_tests.sh` =
**336 tests PASS** (319 base remota + 3 nuevos de regresión), 25 clases. Smoke 25 OK. NO
VERIFICADO: gradle/lint/assemble/Android/UI/Room (sin Android SDK).

## Último trabajo — Ciclo 38: fechas pasadas + recuperación de fechas imposibles

Dos unidades atómicas del ciclo de parser natural (P1 — evitar olvidos + datos erróneos).

**1. Fechas pasadas "hace N"/"la semana/el mes pasado"** (commit `ff3a1f4`).
El usuario registra una tarea ya vencida ("pagué hace 2 días", "revisé el informe la
semana pasada", "reunión el mes pasado"). Antes estas formas quedaban **SIN fecha**
(`dueAt=null` → sin recordatorio, invisible en What Now/planificador) **Y** con la frase
temporal intacta como basura en el título. Causa raíz: no existían `agoPattern` ("hace N")
ni `lastPeriodPattern` ("la semana/el mes/el año pasado"); además `previousWeekdayPattern`
capturaba "el mes pasado" (grupo1="mes", no es día → sin fecha) y **borraba** la frase.
Solución: nuevos `agoPattern` (resta N días/semanas/meses/años; "hace poco"/"hace un
rato" = -3h, heurística honesta de "recién") y `lastPeriodPattern` (resta 7d/30d/365d),
detectados **antes** de `previousWeekdayPattern` e integrados al **inicio** de la cadena
`effectiveRelativeDueAt` (las fechas pasadas son explícitas y tienen prioridad sobre fechas
futuras ambiguas). La hora explícita se aplica sobre la fecha pasada (tarea vencida con hora).

**2. Recuperación de fechas imposibles** (commit `265fc93`).
`parseMonthNameDate` usaba `LocalDate.of(year, month, day)` que lanza `DateTimeException`
para fechas imposibles ("el 29 de febrero" en año no bisiesto, "el 31 de abril"). El
`runCatching` devolvía `null` → caía al fallback que **deja la frase temporal en el título**
y `dueAt=null` (tarea sin fecha y con basura). El usuario que escribe "el 29 de febrero"
claramente quiere una fecha real, no perderla. Solución: en vez de descartar, **recuperar**
con `java.time.Year`/`YearMonth`: Feb 29 no bisiesto → siguiente año bisiesto (2028);
día > máx del mes (31 abr) → clamp al último día válido del **siguiente año** (30 abr
2027); Feb 30 → Feb 28. Así la frase se reconoce, se borra del título y la tarea obtiene
una fecha útil (no se pierde).

**Colisión de remoto resuelta (no destructiva)**: durante el run el remoto avanzó dos veces
(runs paralelos: "mediados de semana"/"un par de" y luego "a las N horas" ciclo 37).
Rebase de mis 2 commits sobre el remoto; conflicto en `NaturalTaskParser.kt` (remote añadió
`startOfWeekDueAt`/`midOfWeekDueAt`; local añadió `agoDueAt`/`lastPeriodDueAt`) resuelto
combinando ambos conjuntos en la cadena `effectiveRelativeDueAt`. Conflicto en el test file
(ambos añadieron tests al final) resuelto conservando ambos conjuntos. Sin STALE_RUN, sin
force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**329 tests PASS** (25 clases). Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

### Ciclos parser recientes (resumen)
- Ciclo 37: "a las N horas" como hora, no duración falsa (3 tests, 315 → ahora incluidos en 329).
- Ciclo 36: "mediados de semana" = miércoles (4 tests).
- Ciclo 35: "un par de" coloquial = 2 (4 tests).
- Ciclo 34: "esta semana" (próximo domingo) + "principios de semana" (lunes).
- Ciclo 33: "principios de mes" (día 1), "fines de semana" recurrencia WEEKLY sáb+dom, días pasados ("el jueves pasado").
- Ciclo 32 (cont.4): adjuntos copiados a almacenamiento interno (`AttachmentStorage` + FileProvider) — P1 persistencia.
- Ciclo 31: fechas relativas semanas/meses + ayer/anteayer.

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
| P1 | Persistencia — adjuntos URI externo | FIXED (NO VERIFICADO Android) ciclo 32 cont.4 |
| P1 | Parser — fechas relativas/pasadas/imposibles | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperaciÃ³n fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la maÃ±ana" (hora) vs fecha "maÃ±ana" c.39 (336 tests); recordatorios con nÃºmeros escritos y fracciones c.40 (344 tests) |
| P2 | QA — compilar 6 variantes tras cambios | OPEN (requiere env Android) |
| P2 | Self-Update — prueba end-to-end N→N+1 | BLOCKED-external (sin dispositivo Android) |
| P3 | UX — pulido visual pantallas workspace renovadas | OPEN |

## Próximo trabajo

- Ciclo 32 (cont.4) (DONE): adjuntos copiados a almacenamiento interno vía `AttachmentStorage`
  + FileProvider. P1 persistencia resuelto. `addAttachment`/`attachCaptureIfPresent`/`resolveAttachmentUri`
  + `deleteAttachment` en OrdiaViewModel; `NoteEditorScreen`/`TaskDetailScreen` migrados (sin
  `takePersistableUriPermission`). 275 domain tests PASS. NO VERIFICADO Android/UI.
- Ciclos previos del 32: “próximos días” (+3d), “antier” (-2d), “próximo trimestre” (+90d),
  “fin de mes”/“mediados de mes”, verificados.
- Continuar ciclo interminable. Candidatos parser: ~~"esta semana" (vs "la semana que viene")~~
  HECHO ciclo 34; "próximo bimestre/semestre" (evaluar frecuencia), "próxima quincena" (+15d),
  ~~"principios de semana" (lunes)~~ HECHO ciclo 34 cont. (294 tests). "principios de mes" (día 1) ya hecho ciclo 33.
  ~~"mediados de semana" (miércoles)~~ HECHO ciclo 36 (312 tests). ~~"a finales de semana"~~ HECHO
  ciclo 37 (316 tests): resuelve a sábado (igual que "fin de semana"), forma plural análoga a
  "finales de mes"; ambigüedad viernes/sáb/dom resuelta por consistencia con "fin de semana" ya existente.
- P1 adjuntos: NEXT paso sería **migración de adjuntos legacy** (URIs externos antiguos ya
  guardados) — copiar contenido al abrir por primera vez si todavía accesible. Evaluar antes
  de implementar (riesgo: URIs ya inválidos). De momento `resolveAttachmentUri` no rompe legacy.
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler en pantallas anidadas;
  contraste onSurfaceVariant. No detenerse.

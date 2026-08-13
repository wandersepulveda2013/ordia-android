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

- **Fecha (UTC)**: 2026-08-13 (ciclo 43)
- **Branch de trabajo**: `openhands/autonomous-ordia` (HEAD tras ciclo 43)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

| P1/P2 | Parser — fechas relativas/pasadas/imposibles + rango horario + recurrencias laborables/quincenal | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 base (353 tests) + ampliación followers c.42 cont. (358 tests); recurrencia quincenal "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura → hoy c.42 cont.2 (362 tests); "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY [1-5] c.43 (372 tests) |

## Último trabajo — Ciclo 43: parser "entre semana"/"días laborables"/"de lunes a viernes" = recurrencia semanal Lun–Vie

Unidad atómica del ciclo de parser natural (P1 — evitar olvidos + fricción de captura). Frases
cotidianas para hábitos laborables ("Gimnasio entre semana", "Trabajo de lunes a viernes",
"Estudiar días laborables", "Reunión los días hábiles") **no generaban recurrencia**: el parser
las trataba como texto suelto → tarea única (freq=NONE) que aparece una sola vez y se olvida
el resto de la semana. Peor, "de lunes a viernes" dejaba "lunes" como residuo en el título
(`dayListPattern` capturaba solo "lunes", days=[1]) y el viernes se perdía. Brecha simétrica
frente a `weekendRecurrencePattern` (fines de semana → WEEKLY sáb+dom, c.33).

**Solución (mínima, en `NaturalTaskParser.kt`)**:
- `weekdayRangePattern`: nuevo patrón `(los |de )?(lunes|martes|miércoles|jueves|viernes)\s+a\s+(martes|…|domingo)` (rango Lun–Vie, admite prefijo `los `/`de `). Si el rango termina en viernes (o incluye viernes), → `RecurrenceFrequency.WEEKLY`, `days=[1,2,3,4,5]` (hábito laboral). Resuelve a la **próxima ocurrencia** del primer día (jue 30-07 dado now=mié 29-07 12:00, slot ya pasado hoy).
- `weekdaySetPattern`: variantes léxicas equivalentes → mismo WEEKLY [1-5]: `entre semana`, `días laborables`, `días hábiles`, `días de semana`, `de semana` (con prefijo opcional `los `/`de `). Consumen la frase completa (título limpio).
- **Orden de patrones crítico**: ambos se evalúan **ANTES** que `dayListPattern` para que "los lunes a viernes" sea rango (days=[1..5]) y no la lista ["lunes"] (days=[1]). El singular "fin de semana" sigue siendo fecha única (próximo sábado), sin colisión.

**Colisión de remoto resuelta (no destructiva)**: durante el run el remoto avanzó varios commits
(ciclos 38–42: "de/por/a la mañana", recordatorios con números escritos/fracciones, listas de
días sin coma + plurales sábados/domingos, rango horario sin "horas", recurrencia quincenal).
Procedimiento no destructivo: `git rebase` de mi commit sobre el HEAD remoto; auto-merge limpio en
`NaturalTaskParser.kt` + tests (cambios ortogonales); conflictos solo en docs
(`CURRENT_STATE.md`, `RUN_LOG.md`) resueltos conservando el trabajo del otro run y
renumerando el mío a **ciclo 43** (la otra ejecución ya había reclamado los números 41 y 42). Sin
force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**372 tests PASS** (365 base remota + 7 nuevos de este ciclo, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).


Bug de captura P2 (ciclo 42): el rango horario **sin la palabra "horas"** y con ambas horas < 13
("clase de 9 a 11") no se reconocía: `durationMinutes=null` y "de 9 a 11" quedaba como residuo.
Un run paralelo (`0a77387`) envió el fix base (aceptar el rango si no le sigue sustantivo de
cantidad, con set de followers básicos). **Este run** detectó que ese set dejaba residuo cuando
el rango iba seguido de un día de la semana ("el viernes"), un día relativo ("mañana") o un
marcador de parte del día ("a la tarde", "por la noche"), y lo amplió. Sigue rechazando
"comprar de 2 a 5 entradas". Heurística honesta, conservadora.

VERIFICADO localmente (JVM puro, sin Android SDK): `bash tools/run_domain_tests.sh` =
**358 tests PASS** (353 base c.42 + 5 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42 (cont. 2): día de semana suelto hoy con hora futura vence hoy

Fix P1 de captura de citas de hoy (parser natural).

**Problema**: **"el viernes a las 18"** escrito el propio viernes **antes** de las 18:00 se
programaba para el **viernes de la semana siguiente**: la cita de hoy se perdía una semana
entera (reunión/cita olvidada hoy, recordatorio 7 días tarde). Causa raíz: la rama de fecha
suelta usaba `nextWeekday`, que **siempre** salta +7 cuando el día objetivo es hoy (correcto para
recurrencias —necesitan "próximo" estricto— pero incorrecto para una fecha suelta puntual). No
existía path para "hoy si aún no llegó la hora".

**Solución (mínima, `NaturalTaskParser.kt`)**: nueva `nextWeekdayOrSame(from, target)` — devuelve
hoy si el día objetivo coincide con el de `from`, si no delega a `nextWeekday`. La rama de fecha
suelta (weekday blando) usa `nextWeekdayOrSame`; el descarte de "hoy si la hora ya pasó" se difiere
al combinar fecha+hora: si fecha+hora resulta pasada respecto a `now`, se rueda +7 días (sin agenda
en pasado, sin regresión). Las recurrencias siguen usando `nextWeekday` (próximo estricto, sin
cambio). Heurística honesta (no IA).

**Colisión con run paralelo (no destructiva)**: el push inicial se rechazó por divergencia (remoto
avanzó de `0a77387` a `727e7b8` por un run paralelo). `git fetch` + `git rebase
origin/openhands/autonomous-ordia` (no destructivo, sin force) integró limpio sobre el nuevo HEAD.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**362 tests PASS** (358 base remota + 4 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42 (cont.): rango horario sin "horas" + ampliación de followers seguros

Mejora aditiva sobre el fix base del rango horario (P2 — captura de bloque horario cotidiano).

**Colisión con run paralelo (no destructiva)**: mi base local (`91c8b9f`) estaba por detrás del
remoto: `0a77387` (run paralelo) ya había resuelto el mismo backlog item con un enfoque
equivalente (rango sin unidad y horas < 13 aceptado si no le sigue sustantivo de cantidad).
Descarté mi implementación competidora vía `git stash` + `git pull --ff-only` (sin force push,
sin reset --hard) y reconstruí sobre `0a77387`. Sin STALE_RUN destructivo. Aporté una mejora
aditiva no duplicativa sobre el fix base.

**Mejora aditiva**: el set de followers seguros del fix base dejaba residuo en tres clases de
frases cotidianas — rango + día de la semana ("clase de 9 a 11 el viernes"), rango + día
relativo ("taller de 10 a 12 mañana") y rango + parte del día con conector no listado ("curso
de 4 a 6 a la tarde", "turno de 9 a 11 por la noche"). Causa raíz: el regex `followedByCount`
sólo incluía conectores básicos (con/y/o/para/hasta/luego/después/pero/porque + puntuación).
Solución: ampliar el regex con artículos (el/la/los/las/un/una), a/al, por, sin, sobre, desde,
del, días de la semana y días relativos (mañana/hoy/ayer). El rechazo de "comprar de 2 a 5
entradas"/"de 2 a 5 personas" se preserva (sustantivo contable sigue fuera del set).

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**358 tests PASS** (353 base remota + 5 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 38: fechas pasadas + recuperación de fechas imposibles

Dos unidades atómicas del ciclo de parser natural (P1 — evitar olvidos + datos erróneos).


## Último trabajo — Ciclo 42: parser rango horario sin "horas" (ambas < 13)

Unidad atómica del parser natural (P2 — forma cotidiana no reconocida). **"clase de 9 a 11"**,
**"taller de 10 a 12"** (formato 12h sin la palabra "horas") caían a `dueAt=null`,
`durationMinutes=null` con el rango crudo ("9 a 11") como residuo en el título. El `timeRangePattern`
casa, pero el guard lo rechazaba: exigía unidad final ("horas"/"hs"/"h") o alguna hora ≥ 13
(24h inequívoco) para evitar falsos positivos como **"comprar de 2 a 5 entradas"** (cantidad, no horario).

**Solución (mínima, `NaturalTaskParser.kt`)**: heurística honesta (no IA): un rango sin unidad y
ambas horas < 13 se acepta como ventana horaria **solo si NO va seguido de un sustantivo de
cantidad**. Si tras el rango hay fin de cadena o un conector/preposición/puntuación
("con Juan", "y luego", ", después") se entiende como horario; si hay un sustantivo después
("entradas", "personas") se respeta como cantidad. Así "clase de 9 a 11" → dur 120,
título "Clase"; "comprar de 2 a 5 entradas" → sin duración, título intacto. Restricción
`end - start in 1..11` evita rangos absurdos.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**353 tests PASS** (350 base c.41 + 3 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room (sin Android SDK).

## Último trabajo — Ciclo 41: parser listas de días sin coma + plurales sábados/domingos

Unidad atómica del ciclo de parser natural (P1 — pérdida de datos silenciosa en rutinas). **"los lunes miércoles y viernes"** (forma informal en español, sin coma entre los dos primeros días) era tan común como la forma con coma, pero el parser exigía conector ","/"y" entre cada par: capturaba solo "lunes" y dejaba "miércoles y viernes" como residuo en el título → la rutina se repetía **un solo día** en silencio y los recordatorios no disparaban en los días perdidos. Adicionalmente, los plurales **"sábados"/"domingos"** no casaban (patrón singular con `\b`) y se perdían también. Complementario al ciclo 20 (que añadió el conector ","/"y").

**Solución (mínima, `NaturalTaskParser.kt`)**: separador **opcional** en `dayListPattern` (`(?:,|y)?`): como los nombres de día son palabras cerradas y específicas, admitir separador vacío solo casa cuando la palabra siguiente es otro día, sin riesgo de robar texto ajeno ("los lunes con el equipo" para en "lunes" porque "con" no es un día). Plural `s[aá]bados?|domingos?`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` = **350 tests PASS** (incluye 3 nuevos de este ciclo + 3 del run concurrente `60007d1` sobre la misma feature, casos distintos; coexisten con tests de los ciclos 36-40), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room (sin Android SDK).

**Nota de integración**: rebase no destructivo sobre `origin/openhands/autonomous-ordia` (otras runs avanzaron a ciclos 36–40: "a las N horas", fechas pasadas, recuperación de fechas imposibles, "a finales de semana", "de/por/a la mañana" vs fecha "mañana", recordatorios con números escritos y fracciones); este trabajo se renumera a ciclo 41 para evitar colisión. Conflictos de docs resueltos tomando base remota y reinsertando esta sección. Auto-merge limpio en `NaturalTaskParser.kt` + test (cambios ortogonales).

## Último trabajo — Ciclo 37: parser "a las N horas" (hora, no duración falsa)

Unidad atómica del ciclo de parser natural (P1 — corrección de bug que generaba datos
erróneos). **"a las N horas"** es la forma más natural de dar una hora en reloj de 24h
con sufijo "horas" ("reunión a las 9 horas", "clase a las 10 horas"). El parser **NO la
reconocía como hora**: el `timePattern` no consumía el sufijo "horas", así que "9 horas"
era **robado por `durationMatch`** como una duración falsa de **540 minutos** (9x60), y "a las"
quedaba como residuo en el título. Consecuencia: la tarea recibía una duración absurda y
**ninguna hora real** → recordatorio y planificación incorrectos. Bug doble porque, al añadir
la guardia para descartar "N horas" como duración, el filtro se aplicaba al ganador global tras
`minByOrNull`, descartando **TODOS** los matches de duración (incluido "durante 1h" válido)
cuando había algún "N horas" inválido presente. Además los conectores "durante"/"por" no
se limpiaban del título tras extraer la duración.

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
- Ciclo 43: "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY Lun–Vie (7 tests, 372 total)).
- Ciclo 41: listas de días sin coma + plurales sábados/domingos (3 tests, 350 total).
- Ciclo 40: recordatorios con números escritos y fracciones (8 tests, 344 total).
- Ciclo 39: "de/por/a la mañana" (hora) vs fecha "mañana" (336 tests).
- Ciclo 38: fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (329 tests).
- Ciclo 37: "a las N horas" como hora, no duración falsa (3 tests, incluidos en 336).
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
  verificación de variantes 6x (Safe/Full/Advanced x debug/release) NO se ejecutan; cubiertas
  solo por tests unitarios contract + verificación estática de APK firmada.

## Pendientes principales (ver BACKLOG.md)

| Pri | Área | Estado |
|-----|------|--------|
| P1 | Persistencia — adjuntos URI externo | FIXED (NO VERIFICADO Android) ciclo 32 cont.4 |
| P1 | Parser — fechas relativas/pasadas/imposibles | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 base (353) + ampliación followers (358) + recurrencia quincenal con palabra "cada quincena"/"quincenalmente" c.42 (365 tests) |
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
  HECHO ciclo 34; "próximo bimestre/semestre" (evaluar frecuencia), ~~"próxima quincena" (+15d)~~
  HECHO ciclo 42 (370 tests), `quincenaPattern`: "primera/segunda/1ra/2da quincena" → día 15/fin
  de mes (con rollover a mes próximo si ya pasó); "la quincena" sin cualificar → próximo hito;
  hora explícita respetada. Simétrico a `finDeMes`/`mediadosDeMes`. "próxima quincena" sigue
  como +15d (procesado después de `nextPeriodMatch`), "en N quincenas" como relativo.
  ~~"principios de semana" (lunes)~~ HECHO ciclo 34 cont. (294 tests). "principios de mes" (día 1) ya hecho ciclo 33.
  ~~"mediados de semana" (miércoles)~~ HECHO ciclo 36 (312 tests). ~~"a finales de semana"~~ HECHO
  ciclo 37 (316 tests): resuelve a sábado (igual que "fin de semana"), forma plural análoga a
  "finales de mes"; ambigüedad viernes/sáb/dom resuelta por consistencia con "fin de semana" ya existente.
- P1 adjuntos: NEXT paso sería **migración de adjuntos legacy** (URIs externos antiguos ya
  guardados) — copiar contenido al abrir por primera vez si todavía accesible. Evaluar antes
  de implementar (riesgo: URIs ya inválidos). De momento `resolveAttachmentUri` no rompe legacy.
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler en pantallas anidadas;
  contraste onSurfaceVariant. No detenerse.

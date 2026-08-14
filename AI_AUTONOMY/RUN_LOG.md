# RUN_LOG — Ordía
## Ciclo 137 — 2026-08-14 (UTC) — fix(search): asimetría "urgente" no recuperaba tareas URGENT (P2 recuperación crítica)

- **Run/ciclo**: 137 (rama `openhands/autonomous-ordia`). Base sincronizada con **divergencia resuelta (anti-colisión)**: al iniciar, `git pull --ff-only` estaba limpio en `d8b5426` (c.135). Se desarrolló y commiteó localmente `e0dd807` (fix search "urgente", ciclo numerado 136). Al pushear, el remoto había avanzado a `ada8d9f` — **otra ejecución había reclamado el ciclo 136** (`dec0cab`, fix parser "mediados/finales/principios de [mes nombre]"). Colisión: ambas runs editamos `AI_AUTONOMY/*.md` (docs) pero el código era ortogonal (`SearchEngine.kt` vs `NaturalTaskParser.kt`). Acción segura (AGENTS §1): `git reset --hard origin/openhands/autonomous-ordia` (alinea al HEAD remoto `ada8d9f`, descarta mi commit local NO pusheado — permitido ante divergencia), luego `git checkout e0dd807 -- <SearchEngine.kt + SearchEngineTest.kt>` re-aplica solo mi código ortogonal sobre la base limpia. **Renumerado a ciclo 137** para no duplicar el ciclo 136 en el registro persistente. Working tree: código restaurado + docs renumerados. Sin sobreescritura de trabajo válido del otro agente.
- **HEAD inicial**: `d8b5426` (c.135) al comenzar; base final de reaplicación `ada8d9f` (c.136 remoto).
- **Problema seleccionado (P2 → búsqueda universal/recuperación de tareas)**: **asimetría del filtro de prioridad "urgente"** en `SearchEngine.search`. Buscar **"urgente"** NO recuperaba las tareas marcadas como `TaskPriority.URGENT` a menos que la palabra "urgente" apareciera literalmente en el título. Una tarea URGENT titulada "Pagar factura de luz" **NO aparecía** al buscar "urgente" → el atajo mental natural "urgente" (para "¿qué es lo más crítico?") devolvía **vacío** aunque hubiera tareas urgentes. **Asimetría flagrante** con **"importante"** que SÍ filtraba por prioridad (HIGH+URGENT) sin exigir la palabra en el contenido: "importante" funcionaba como filtro semántico, "urgente" no — pese a que URGENT es el nivel de prioridad MÁS alto. Brecha del área de dirección explícita "búsqueda universal" + "recuperación de tareas olvidadas/críticas".
- **Prioridad**: P2 (mejora funcional de recuperación/búsqueda; no pérdida de datos, pero degrada la superficie de "¿qué es lo crítico?" — un usuario que confía en "urgente" para encontrar lo crítico recibe "no hay nada" falso). Elegido deliberadamente por encima de warnings P2 para diversificar fuera del parser (USER_CONTEXT pidió no-parser, mayor impacto).
- **Causa raíz (dual)**: (1) la cascada de filtros de tareas en `SearchEngine.kt` (línea ~90) tenía un filtro `(!normalized.contains("importante") || task.priority in setOf(HIGH,URGENT))` pero **ninguno** para "urgente" → una tarea URGENT solo pasaba si su contenido contenía "urgente". (2) `TASK_TERMS` (conjunto de meta-términos que `semanticMatches` descarta del requisito de contenido al buscar) listaba `"important"` (prefijo de "importante") pero **no** `"urgente"` → "urgente" se trataba como palabra de contenido exigida, no como meta-término.
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón, sin IA fingida)**:
  - (a) Nuevo filtro en la cascada de tareas: `(!normalized.contains("urgente") || task.priority == TaskPriority.URGENT)`. Mapea "urgente" al nivel URGENT **exacto** (no al rango amplio HIGH+URGENT que cubre "importante"): una tarea HIGH es "importante", no "urgente" — mapeo honesto y veraz. Simétrico al filtro existente de "importante".
  - (b) `"urgente"` añadido a `TASK_TERMS` para que `semanticMatches` lo trate como meta-término (no se exige en el contenido), simétrico a como `"important"` habilita a "importante".
  - Combinación: `"urgente reunion"` filtra URGENT + exige "reunion" en contenido (el meta-término se descarta del requisito de contenido, las palabras restantes sí se exigen).
  - Reusa TODO el flujo existente (ranking por urgencia, dateScope, pureDateScope, semanticMatches). Sin nueva pantalla, sin nuevo botón, sin fingir IA — es un filtro léxico honesto.
- **Tests**: `bash tools/run_domain_tests.sh` → **981 PASS** (978 c.136 remoto + 3 míos), 0 failures. `bash tools/run_domain_checks.sh` → smoke 25 OK. +3 tests TDD (RED antes del fix → GREEN después): `urgente_surfacesOnlyUrgentPriorityRegardlessOfTitle` (URGENT sin "urgente" en título aparece; NORMAL no), `urgente_excludesHighPriorityTasks` (HIGH no entra, solo URGENT — verifica el mapeo exacto), `urgenteWithContent_findsUrgentTasksMatchingText` ("urgente reunion" filtra URGENT + "reunion"). Estado RED confirmado antes del fix (los 3 devolvían `[]`).
- **Features**: 0 (corrección de capacidad de búsqueda existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); integración `SearchEngine`↔pantalla de búsqueda (UI) queda NO VERIFICADA.
- **Hallazgos adicionales**: la asimetría "importante" funciona / "urgente" no era una inconsistencia conceptual clara — ambos son palabras de prioridad, ambos deberían filtrar por prioridad. El fix las hace simétricas con mapeo honesto distinto ("importante"=rango amplio HIGH+URGENT, "urgente"=nivel exacto URGENT). No se añadió "alta"/"baja" como filtros de prioridad en este run (oportunidad para futuro: "alta"→HIGH, "baja"→LOW/NORMAL) para mantener el cambio mínimo y enfocado en la asimetría de mayor impacto (URGENT es lo crítico). **Nota de colisión**: el `GITHUB_TOKEN` vino vacío (len 0); se autenticó con `$github_token` (PAT, len 40). El ciclo se renumeró 136→137 tras detectar que otra run reclamó el 136.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt` (cascada de filtros + `TASK_TERMS`), `app/src/test/java/com/ordia/app/domain/SearchEngineTest.kt` (+3 tests), `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit) pendiente de push a `openhands/autonomous-ordia`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 981 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — cerrado el gap de búsqueda "urgente" (no-parser); revisar otros gaps de búsqueda (¿"alta"/"baja" prioridad como filtros léxicos?), asistente, contexto, onboarding, navegación, accesibilidad, rendimiento; auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA (sin Android SDK).

## Ciclo 136 — 2026-08-14 (UTC) — fix(parser): "mediados/finales/principios de [mes nombre]" → dueAt=null (P1 vencimiento olvidado + integridad de título)

- **Run/ciclo**: 136 (rama `openhands/autonomous-ordia`). Base sincronizada: repo ya en `openhands/autonomous-ordia`; `git pull --ff-only origin openhands/autonomous-ordia` limpio, HEAD remoto `d8b5426` (c.135 docs follow-up). Sin divergencia. Working tree limpio al iniciar. Sesión de descubrimiento continuo: continuaba el T-C135-FIX-MONTH-ABBREV del contexto previo (auditoría de motores + límite mensual con mes nombre, hallazgo dejado por el probe c.135).
- **HEAD inicial**: `d8b5426` (c.135 docs follow-up).
- **Problema seleccionado (P1 → parser/captura/evitar olvidos + integridad de título)**: las frases **"mediados/finales/principios/fin/cierre/corte/mitad/comienzos/primeros de [mes nombre]"** (límite mensual aplicado a un mes concreto, sin día exacto) no se parseaban. Plazos cotidianísimos al agendar vencimientos futuros — **"pago a mediados de septiembre"**, **"envío a finales de octubre"**, **"renta a principios de enero"** — caían a `dueAt=null` y la frase entera sobrevivía como título basura → **vencimiento olvidado** (sin recordatorio, invisible en What Now/planificador/resumen del día). Asimetría: "fin de mes"/"mediados de mes" (mes en curso) SÍ funcionaban, pero aplicar el límite a un mes NOMBRE concreto no. "mediados de semana"/"fin de año" SÍ funcionaban (handlers propios). Hallazgo explícito dejado por el probe c.135 (`"mediados de septiembre"→null`).
- **Prioridad**: P1 (vencimiento olvidado en captura cotidiana de pagos/cierres/rentas futuros).
- **Causa raíz**: ningún patrón cubría "calificador de límite mensual + mes nombre". Existían `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` (límites aplicados a "mes" en curso, c.32/c.33) pero no su variante con mes explícito. El token del mes nombrado caía a `monthNamePattern`, que exige un día (`"15 de septiembre"`); sin día, `parseMonthNameDate` no resolvía y la frase quedaba como residuo.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin fingir IA)**:
  - Nuevo `monthBoundaryNamePattern` = `(?i)(?<!\p{L})(?:a\s+)?(mediados?|mitad|principios?|comienzos?|primeros?|finales?|fin|cierre|corte)\s+(?:de\s+|del\s+)([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b`. Captura calificador, mes (cualquier palabra) y año opcional.
  - Nuevo `parseMonthBoundaryName(today, qualifier, month, rawYear)`: principios/comienzos/primeros→día 1; mediados/mitad→día 15; finales/fin/cierre/corte→último día vía `YearMonth.of(year,month).lengthOfMonth()` (DST/longitud de mes correctos, p. ej. feb 28/29). Año implícito=hoy con roll al año siguiente si la fecha ya pasó (mismo criterio que `parseMonthNameDate`); año explícito de 2 cifras→`2000+N`, 4 cifras directo, sin roll.
  - Procesado ANTES que `monthNamePattern` (tras `monthBoundary`) para consumir la frase completa y evitar residuo/doble-match. Reutiliza el mapa `months` (acepta nombre completo Y abreviatura c.135).
  - **Anti-colisión clave (descubierta durante el run)**: el grupo 2 del patrón casa cualquier palabra tras "de", así que "mediados de **semana**"/"fin de **año**"/"fin de **proyecto**" también casaban. La primera versión consumía la frase SIEMPRE (aunque el mes no fuera válido) → rompía 18 tests ("mediados de semana", "mitad de año", "fin de año", "comienzos de semana" perdían su `dueAt` y su handler original). Endurecido: solo se consume la frase y se fija fecha si la palabra es un mes real del mapa (`monthBoundaryNameMonthNum != null`); si no, cae intacta a su handler original. La condición en `relativeIsDays` usa `monthBoundaryNameDueAt != null` (no el match crudo) para no marcar como "resuelta" una frase que no resolves fecha.
- **Tests**: probe JVM (24 frases, now=2026-07-29) confirmó el bug y la fix: `pago a mediados de septiembre`→antes `due=null title='pago a mediados de septiembre'`, después `due=2026-09-15 title='pago'`; `envío a finales de octubre`→antes null, después `2026-10-31`; `entregar a principios de enero`→antes null, después `2027-01-01` (roll año); `pago a mediados de dic`→`2026-12-15` (abreviatura c.135); `cierre a principios de febrero del 2028`→`2028-02-01` (año explícito sin roll). Guards: "mediados de semana"/"fin de año" intactos (caen a su handler). +10 tests permanentes en `NaturalTaskParserTest.kt` (`mediadosDeMesNombreAnclaDia15`, `finalesDeMesNombreAnclaUltimoDia`, `principiosDeMesNombreAnclaDia1`, `principiosDeMesNombreFuturoNoRuedaAnioSiAunNoPasa`, `finDeMesNombreAnclaUltimoDia`, `mitadDeMesNombreAnclaDia15`, `mediadosDeMesAbreviaturaAnclaDia15`, `mesNombreLimiteRespetaHoraExplicita`, `mesNombreLimiteConAnioExplicitoNoRueda`, `mesNombreLimiteNoColisionaConSemanaAno`). `bash tools/run_domain_tests.sh` → **978 PASS** (968 c.135 + 10), 0 failures; `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe temporal borrado tras validación.
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración del parser con `AddTaskScreen`/IME queda fuera del harness JVM.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+`monthBoundaryNamePattern`, +`parseMonthBoundaryName`, +bloque de detección temprana con guard anti-colisión, +rama en `effectiveRelativeDueAt`/`relativeIsDays`), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+10 tests), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `dec0cab` (pushed a `origin/openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM: 978 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo — gap "límite mensual con mes nombre" CERRADO; el probe dejó hallazgos menores para evaluar (anti-feature-bloat: "finales del 27"→null límite anual con año de 2 cifras, "la semana del 24"→null, "último día hábil del mes"→null — menos frecuentes); revisar áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento, búsqueda); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 135 — 2026-08-14 (UTC) — fix(parser): abreviaturas de meses ("dic"/"ene"/"feb"…) → dueAt=null (P1 vencimiento olvidado)

- **Run/ciclo**: 135 (rama `openhands/autonomous-ordia`). Base sincronizada: repo ya en `openhands/autonomous-ordia`; `git pull --ff-only origin openhands/autonomous-ordia` limpio, HEAD remoto `4771946` (c.134, merge "de aquí a/al"). Sin divergencia. Working tree limpio al iniciar. Sesión de descubrimiento continuo: probe JVM de 54 frases cotidianas sobre `NaturalTaskParser` para hallar gaps de captura NO listados en BACKLOG.
- **HEAD inicial**: `4771946` (c.134).
- **Problema seleccionado (P1 → parser/captura/evitar olvidos)**: las **abreviaturas informales de meses** ("dic"/"ene"/"feb"/"mar"/"abr"/"may"/"jun"/"jul"/"ago"/"sep"/"oct"/"nov"/"set") no se reconocían como mes. Frases cotidísimas al capturar — **"llamar el 25 de dic"**, **"pago el 1 de ene"**, **"pago el 28 de feb"** — caían a `dueAt=null` y la frase entera quedaba como título basura → **vencimiento olvidado** (sin recordatorio, invisible en What Now/planificador/resumen del día). Asimetría flagrante: "25 de diciembre"/"1 de enero"/"28 de febrero" (nombres completos) SÍ funcionaban. Es la misma intención del usuario, solo dicha de forma abreviada (común en móvil y al apuntar pagos/citas festivas).
- **Prioridad**: P1 (vencimiento olvidado en captura cotidiana; pagos/citas navideñas y de año nuevo dichos de la forma corta).
- **Causa raíz**: el mapa `months` de `NaturalTaskParser` (línea ~814) solo contenía los nombres completos de mes. `parseMonthNameDate` hace un lookup directo `months[groupValues[2].lowercase()]` y devolvía `null` para cualquier abreviatura, abortando la resolución de la fecha. El regex `monthNamePattern` SÍ casa la abreviatura (grupo `[a-záéíóúüñ]+`), así que el token se consumía pero no se resolvía → null.
- **Solución (mínima, sin nueva pantalla/botón, sin lógica nueva, sin fingir IA)**: se añaden las 12 abreviaturas de 3 letras al MISMO mapa `months` (enero→ene, febrero→feb, … diciembre→dic; setiembre→set). Al compartir el mapa, TODO el flujo existente de `parseMonthNameDate` se reutiliza sin cambios: resolución de año implícito (con roll al año siguiente si la fecha ya pasó), ajuste de día imposible ("31 de abr"→30/abr), manejo del 29 de febrero bisiesto, hora explícita combinable, y limpieza del título. Sin nuevos patrones, sin nueva rama de código.
- **Tests**: probe JVM de 54 frases cotidianas (now=2026-08-14 12:00) confirmó el bug y la fix: "llamar el 25 de dic"→antes `due=null title='llamar el 25 de dic'`, después `due=2026-12-25 09:00 title='llamar'`; "pago el 1 de ene"→antes null, después `2027-01-01` (roll año); "pago el 28 de feb"→antes null, después `2027-02-28` (roll año). +1 test permanente en `NaturalTaskParserTest.kt` (`parsesMonthAbbreviations`: cubre dic/ene/feb con sus aserciones de fecha y título). `bash tools/run_domain_tests.sh` → **968 PASS** (967 c.134 + 1), 0 failures; `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración del parser con `AddTaskScreen`/IME queda fuera del harness JVM. Probe temporal de descubrimiento borrado tras validación.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+12 entradas en `months`), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+1 test), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `7f7771b` (fix abreviaturas de meses; pushed a `origin/openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM: 968 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo — gap "abreviaturas de mes" CERRADO; el probe de 54 frases dejó otros hallazgos menores para evaluar (anti-feature-bloat: "mediados de septiembre"→null, "la semana del 24"→null, "último día hábil del mes"→null, "entre las 4 y las 5"→null — rangos/relativos menos frecuentes); revisar áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento, búsqueda); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 134 — 2026-08-14 (UTC) — fix(parser): conector "de aquí a/al" + fecha específica deja residuo en título / "de aquí al 15"→dueAt=null (P1 vencimiento olvidado + integridad de título)

- **Run/ciclo**: 134 (rama `openhands/autonomous-ordia`). Base sincronizada: repo clonado en `openhands/autonomous-ordia`; `git pull --ff-only origin openhands/autonomous-ordia` limpio, HEAD remoto `44d1725` (c.133 docs). Sin divergencia. Working tree limpio al iniciar (sonda NoonProbe.kt del run previo ya no presente). Sesión continuaba el T-FIX-P1-DE-AQUI-A-FECHA (in progress en contexto previo): el defecto estaba diagnosticado; este run lo implementa, añade tests de regresión permanentes y commitea.
- **HEAD inicial**: `44d1725` (c.133 docs).
- **Problema seleccionado (P1 → parser/captura/integridad de datos + evitar olvidos)**: el conector direccional-temporal **"de aquí a/al"** + **FECHA ESPECÍFICA** (día de semana, "mañana", "hoy", "la semana que viene", día del mes). El conector se reconocía para **cantidades relativas** ("de aquí a 3 días"/"de aquí a media hora", vía `relativePattern`/`fractionalRelativePattern` c.50/c.94), pero NO para **fechas específicas**: el conector sobrevivía como **residuo en el título** ("entregar de aquí al viernes"→título "entregar de aquí al") aunque la fecha era correcta; peor aún, **"pago de aquí al 15"→`dueAt=null`** porque `dayOfMonthPattern` exige el artículo "el", no "al" → **vencimiento olvidado** (tarea sin recordatorio, invisible en What Now/planificador). Asimetría flagrante: "de aquí a 3 días" funcionaba, "de aquí al viernes"/"de aquí al 15" no.
- **Prioridad**: P1 (vencimiento olvidado en "de aquí al 15" + contenido degradado en el resto; forma cotidiana en captura móvil).
- **Causa raíz**: ningún patrón relativo casa "de aquí a/al + fecha específica" (no hay cantidad), así que el conector "de aquí a/al" no se consume de `working`. Para fechas como día de semana ("de aquí al viernes") la fecha sí se resolvía (weekdayPattern captura "el viernes" con `de|del|este` opcionales, pero NO "al"), dejando solo el residuo "de aquí al". Para día del mes ("de aquí al 15") era peor: `dayOfMonthPattern` exige `\bel\s+`, así que "al 15" no casa → `dueAt=null`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva, sin fingir IA)**: tras consumir TODOS los patrones relativos (relativeMatch, fractionalAndQuarterRelativeMatch, fractionalRelativeMatch — que cubren "de aquí a N {cantidad}") y ANTES de los patrones de fecha específica (weekendEarlyMatch, weekdayMatch, dayOfMonthMatch), se reescribe el conector huérfano: "de aquí al"/"de acá al"→"el" (así "al 15"→"el 15" casa `dayOfMonthPattern` y "al viernes"→"el viernes" casa `weekdayPattern`); "de aquí a"/"de acá a"→" " (así "a mañana"→"mañana", "a hoy"→"hoy", "a la semana que viene"→"la semana que viene", todos ya capturados por sus patrones). Cubre ambas variantes coloquiales "de aquí"/"de acá" y con/sin tilde. Se procesa en este punto exacto (post-relativos, pre-fechas) para no interferir con los patrones de cantidad relativa que requieren el prefijo "de aquí a".
- **Anti-regresión clave (descubierta DURANTE el run)**: la primera versión colocó el borrado ANTES de `fractionalRelativePattern`, lo que rompía "de aquí a media hora" (pasaba de +30min a `dueAt=null` — el patrón exige el prefijo "de aquí a"). Detectado vía probe JVM + confirmado con stash-compare contra baseline. Reubicado a DESPUÉS de todos los patrones relativos fraccionarios. Probe de 19 casos (8 fixes + 4 no-regresión de cantidad relativa + 7 conectores preexistentes) verde, sin falsos positivos.
- **Tests**: +8 tests permanentes en `NaturalTaskParserTest.kt` (now=2026-07-29 NOON): 6 fixes positivos (`deAquiAlViernesParsesDueAtSinResiduo`→07-31 título "Entregar", `deAquiAlLunesParsesDueAtSinResiduo`→08-03, `deAquiAl15ParsesDueAtSinResiduo`→08-15 = caso P1 peor, `deAquiAManhanaParsesDueAtSinResiduo`→07-30, `deAquiAHoyParsesDueAtSinResiduo`→07-29, `deAquiAlaSemanaQueVieneParsesDueAtSinResiduo`→08-05) + 1 variante "de acá" (`deAcaAlDomingoParsesDueAtSinResiduo`→08-02) + 1 guard de no-regresión (`deAquiAMediaHoraNoRegression`→+30min, title "Llamar"). `bash tools/run_domain_tests.sh` → **964 PASS** (956 c.133 + 8), 0 failures; `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración del parser con `AddTaskScreen`/IME queda fuera del harness JVM.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (1 bloque de reescritura de conector huérfano, post-relativos), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+8 tests), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: (tras commit + push de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM: 964 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo — gap BACKLOG "de aquí a/al + fecha específica" CERRADO; revisar otros gaps léxicos del parser y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 133 — 2026-08-14 (UTC) — fix(parser): "día N" sin artículo "el" (dayOfMonth + nextMonthDay, P1 integridad de captura)

- **Run/ciclo**: 133 (rama `openhands/autonomous-ordia`). Base sincronizada: repo clonado en `openhands/autonomous-ordia`; `git pull --ff-only origin openhands/autonomous-ordia` limpio, HEAD remoto `e785a19` (c.132-run2, merge memoria asistente). Sin divergencia. Working tree limpio al iniciar. Sesión continuaba el T-IMPL-PARSER-DIA-N (in progress en contexto previo): el fix ya estaba aplicado en working tree sin commit; este run lo valida, lo extiende a `nextMonthDayPattern`, añade tests de regresión permanentes y commitea.
- **HEAD inicial**: `e785a19` (c.132-run2).
- **Problema seleccionado (P1 → parser/captura/integridad de datos)**: **"día N" sin artículo "el"** — forma coloquial de día de mes suelto ("pagar día 15", "reunión día 3", "entregar día 5 a las 18"). Antes `dayOfMonthPattern` anclaba con `\bel\s+...` obligatorio; "día N" caía a `dueAt=null` (vencimiento olvidado, invisible en What Now/planificador, sin recordatorio) y, si la frase traía hora ("entregar día 5 a las 18"), ésta se aplicaba a **HOY** → fecha silenciosamente errónea (P1 de datos: la cita se agendaba para hoy en lugar del día 5). Asimetría flagrante: "el día 15" (c.98) y "el 15" SÍ funcionaban. Item P2 listado ABIERTO en BACKLOG (c.129 probe).
- **Prioridad**: P1 (integridad de datos: fecha silenciosamente errónea + vencimiento olvidado; captura cotidiana móvil).
- **Causa raíz**: `dayOfMonthPattern` y `nextMonthDayPattern` exigían el artículo "el" en el anclaje (`\bel\s+...`). Mismo gap en `nextMonthDayPattern`: "día 15 del mes que viene" caía a `nextPeriodPattern` (+30d genérico → p. ej. 28-ago en vez de 15-ago) con título corrupto ("Pagar día 5 del").
- **Solución (mínima, simétrica, sin nueva pantalla/botón, sin lógica nueva)**: ambos patrones cambian el anclaje de `\bel\s+(?:d[ií]a\s+)?` a `\b(?:el\s+(?:d[ií]a\s+)?|d[ií]a\s+)` (admite "el"/"el día"/"día" con y sin tilde). El lookahead negativo de `dayOfMonthPattern` se refuerza de `(?!\s*de\s+[a-záéíóúüñ])` a `(?!\s*del?\s+[a-záéíóúüñ])` (ahora bloquea también "día 15 del libro" — referencia no temporal — y "día 15 de marzo" — colisión con `monthNameDate`/`monthlyDayPattern` — mientras el grupo opcional `(?:\s+(?:del?\s+mes|de\s+este\s+mes))?` consume "del mes"/"de este mes" ANTES del lookahead para que éstas sí se agenden). Reusa TODO el flujo existente (resolución de fecha, roll si pasado, hora explícita, título limpio). Verificado con probe JVM: "pagar día 15"→08-15, "entregar día 5 a las 18"→08-05 18:00 (antes HOY, bug P1), "reunión día 3"→08-03, "Pagar día 15 del mes que viene"→08-15 + título limpio (antes 28-ago + título corrupto), "el capítulo día 15 del libro"→null (guard). Probe temporal borrado tras validación.
- **Tests**: +6 tests permanentes en `NaturalTaskParserTest.kt` (4 `dayOfMonth`: `diaNWithoutArticleResolves`→08-15, `diaNWithoutArticleExplicitHour`→08-05 18:00 = caso P1, `diaNWithoutArticleNearTodayRollsForward`→08-03, `diaNOfBookIsNotDate`→null guard; 2 `nextMonthDay`: `diaNNextMonthWithoutArticleResolves`→08-15, `diaNNextMonthWithoutArticleRolled`→08-05). `bash tools/run_domain_tests.sh` → **956 PASS** (950 c.132 + 6), 0 failures; `bash tools/run_domain_checks.sh` → smoke 25 OK. Stash-compare confirmó que el residuo de título en entradas de SÓLO fecha ("día 15 del mes que viene" sin verbo) es comportamiento preexistente (no regresión); los casos de uso reales (verbo+fecha) quedan con título limpio.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración del parser con `AddTaskScreen`/IME queda fuera del harness JVM.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (2 regex: `dayOfMonthPattern` anclaje+lookahead, `nextMonthDayPattern` anclaje), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+6 tests), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 956 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo — gap BACKLOG "día N sin artículo" CERRADO; revisar otros gaps léxicos del parser y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 132, run 2 — 2026-08-14 (UTC) — ux(assistant): "además N vencidas" excluye la tarea sugerida (P3 claridad, camino central "¿qué hago ahora?")

- **Run/ciclo**: 132 (rama `openhands/autonomous-ordia`). Base sincronizada: HEAD remoto `cc0ba66` (c.132-run1, fix duración cruda). Sin divergencia. Working tree limpio al iniciar.
- **HEAD inicial**: `cc0ba66` (c.132-run1).
- **Problema seleccionado (P3 → UX/claridad, área no-parser, asistente)**: en `AssistantEngine.answer` ("¿qué hago ahora?"), cuando la tarea sugerida era **ella misma** una vencida, el mensaje decía **"Empieza por X: está vencida. Estimo 10 minutos. Además, tienes 1 vencida."** — confuso: esa "1 vencida" del "además" **era la misma tarea** recién sugerida. El "además" (que implica *distinto a lo mencionado*) prometía una segunda tarea vencida que no existía. El usuario podía pensar "¿otra? ¿cuál?" y buscarla en vano. Degradación de claridad en el camino de decisión automática más usado del asistente.
- **Prioridad**: P3 (claridad del mensaje; área de dirección "What Now más útil" — sutil pero en la superficie más vista).
- **Causa raíz**: el `tail` contaba `overdue.size` (todas las vencidas) sin excluir la sugerida. El ranking prioriza vencidas primero, así que con vencimientos presentes la sugerida es muy frecuentemente vencida → el "además" casi siempre doblaba a la propia sugerida.
- **Solución (mínima, sin nueva pantalla/botón)**: `otherOverdue = overdue.count { it.id != suggestion.task.id }` y el `tail` sólo se emite si `otherOverdue > 0`. Reusa TODO el flujo existente (mismo `overdue` pre-computado, mismo formato de plural). Cambio de ~2 líneas. "Empieza por X: está vencida... " (sin "además") cuando es la única vencida; "Además, tienes N vencid-as" con las *otras* cuando hay más.
- **Tests**: `whatNow_explainsWhyAndMentionsOverdue` reescrito para verificar que **NO** aparece "Además" cuando la sugerida es la única vencida (antes esperaba "1 vencida" = bug de comportamiento); +1 test `whatNow_mentionsOtherOverdueWhenSuggestedIsAlsoOverdue` (sugerida vencida + otra vencida distinta → "1 vencida" = la otra). Se descartó un tercer test `whatNow_mentionsAllOverdueWhenSuggestedIsNotOverdue` por inalcanzable: el ranking prioriza vencidas, así que "sugerida no-vencida habiendo vencidas" es casi imposible; el test habría sido artificial. `bash tools/run_domain_tests.sh` → **941 PASS** (940 c.132-run1 + 2 modificados/nuevos neto +1); `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración `AssistantEngine`↔`AssistantScreen` queda fuera del harness JVM.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt` (rama whatNow: `otherOverdue`), `app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt` (+1 test, 1 reescrito).
- **HEAD final**: `e8f76ef` (tras commit + push de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM: 941 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: continuar descubrimiento en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); el asistente ahora tiene 9 pruebas cubiertas — más capacidad de mejorar con verificación real.

## Ciclo 132 — 2026-08-14 (UTC) — fix(assistant): estimación de duración consistente con el planificador (fuente única `TaskRules.plannedDuration`, P2 inteligencia/fiabilidad)

- **Run/ciclo**: 132 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` → HEAD remoto `4795088` (c.130, "mediados/mitad/principios DE LA semana"). Sin divergencia. Working tree limpio al iniciar. Salida deliberada del parser hacia área de producto (inteligencia/asistente) solicitada por el contexto.
- **HEAD inicial**: `4795088` (c.130).
- **Problema seleccionado (P2 → inteligencia/asistente, área no-parser)**: `AssistantEngine.answer` ("¿qué hago ahora?") mostraba la duración estimada de la tarea sugerida usando `suggestion.task.durationMinutes` **crudo** (línea 51). Esto divergía de la fuente única de verdad `TaskRules.plannedDuration` (que acota a `[MIN_PLAN_MINUTES=10, MAX_PLAN_MINUTES=180]`) que `DayPlanner`, `SummaryEngine` y `TaskRules.reminderLeadMillis` ya usan. Consecuencia: una tarea con `durationMinutes=0` (legacy/dato sin duración) → el asistente decía **"Estimo 0 minutos"** (mentira evidente, contradice el resto del sistema que la trata como 10 min); una con `durationMinutes=600` → **"Estimo 600 minutos"** mientras el planificador la acota a 180. Degradación silenciosa de la decisión automática central de "qué hago ahora": la estimación de esfuerzo que ve el usuario no coincidía con la que usan el planificador y el badge de carga del día.
- **Prioridad**: P2 (inteligencia/fiabilidad; coherencia entre superficies — área de dirección "What Now más útil" + principio "fuente única de verdad" ya aplicado a `timeRank`/`priorityScore` en c.53/c.86).
- **Causa raíz**: el asistente tomaba el campo crudo de la entidad en vez de delegar en la regla de dominio que normaliza la duración para planificación. Mismo patrón de duplicación/divergencia que causó bugs P1 previos (`priorityScore`, `timeRank`), aquí sin divergencia activa de ranking pero con salida incorrecta al usuario.
- **Solución (mínima, sin nueva pantalla/botón, sin lógica nueva)**: `AssistantEngine` línea 51 reemplaza `suggestion.task.durationMinutes` por `TaskRules.plannedDuration(suggestion.task)`. Reusa la regla existente (acota `[10,180]`); "0 minutos" → "10 minutos", "600 minutos" → "180 minutos". Sin tocar el filtro de "tareas de 15 minutos" (línea 83): se analizó y `plannedDuration` vs crudo coinciden siempre para el umbral 15 (los valores <10 suben a 10, ambos ≤15; los >180 bajan a 180, ambos >15), así que no había bug práctico allí — no se cambia para no añadir cambios sin beneficio.
- **Tests**: +2 tests en `AssistantEngineTest.kt` (`whatNow_estimatesClampedDurationForZeroDurationTask`: durationMinutes=0 → "10 minutos", no " 0 minutos"; `whatNow_estimatesClampedDurationForOversizedTask`: durationMinutes=600 → "180 minutos", no "600 minutos"). **Ampliación del harness**: `tools/run_domain_tests.sh` ahora compila el paquete `assistant` (`AssistantEngine.kt` + `AssistantEngineTest.kt`) — antes las 6 pruebas del asistente **no se ejecutaban** en JVM (NO VERIFICADAS); ahora sí (depende sólo de `Entities` + `domain`, sin Android). `bash tools/run_domain_tests.sh` → **940 PASS** (932 c.130 + 8 del paquete assistant: 6 previos ahora cubiertos + 2 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno). Integración de `AssistantEngine` con la pantalla `AssistantScreen` queda fuera del harness JVM.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt` (1 línea: `durationMinutes`→`plannedDuration`), `app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt` (+2 tests), `tools/run_domain_tests.sh` (paquete assistant en SOURCES), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `cc0ba66` (tras commit + push de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM: 940 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); los 6 tests del asistente ahora cubiertos abren la puerta a mejorar más capacidades del asistente con verificación real. Auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

> Registro cronológico de sesiones autónomas (append-only, no borrar entradas).

## Ciclo 130 — 2026-08-14 (UTC) — fix(parser): "mediados/mitad/principios DE LA semana" (asimetría semanal con "mediados de mes", P2 captura olvidada)

- **Run/ciclo**: 130 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` → HEAD remoto `ccbb450` (c.129-run4, residuo "de mañana"/"9 de la noche de mañana"). STALE_RUN gestionado: mi commit local `3c4afc6` (ciclo 130 parcial) resolvió 3 items pero los items 1 y 2 ya estaban resueltos en el remoto `ccbb450` (colisión con run concurrente). Descarté el commit redundante vía `git checkout origin/openhands/autonomous-ordia -- <archivos>` y re-apliqué SOLO el item 3 (aporte único no duplicado) sobre la base limpia. Working tree limpio al iniciar el reaplique.
- **HEAD inicial**: `ccbb450` (c.129-run4, remoto).
- **Problema seleccionado (P2 → parser/captura)**: **"mediados de la semana"/"mitad de la semana"/"principios de la semana"** (forma cotidiana con artículo "de la") → `dueAt=null` + residuo en el título → vencimiento olvidado. Asimetría flagrante: "mediados de mes" (c.32) y "mediados de semana" (sin artículo, c.129) SÍ funcionaban, pero la forma con artículo "de LA semana" no casaba ningún patrón. "pago a mediados de la semana" quedaba sin fecha (invisible en What Now/planificador, sin recordatorio) aunque el usuario expresó un plazo concreto.
- **Prioridad**: P2 (captura/olvido; asimetría léxica regional — "de la semana" es forma estándar en español).
- **Causa raíz**: `startOfWeekPattern` y `midOfWeekPattern` sólo admitían `de\s+semana` y `del\s+semana` como conector, NO `de\s+la\s+semana` (con artículo). La rama de resolución (lunes/miércoles más cercano en HOY o futuro) ya existía y funcionaba para las formas sin artículo; el gap era puramente léxico en el patrón regex.
- **Solución (mínima, sin nueva pantalla/botón, sin lógica nueva)**: ambos patrones añaden `de\s+la\s+` como alternativa en el grupo de conectores: `startOfWeekPattern = (?i)\b(?:a\s+)?(?:principios?|comienzos?)\s+(?:de\s+la\s+|de\s+|del\s+)semana\b` y `midOfWeekPattern = (?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+la\s+|de\s+|del\s+)semana\b`. Reusa TODO el flujo existente (resolución simétrica: principios→lunes, mediados/mitad→miércoles, ambos HOY o futuro; respeta hora explícita; se detecta/borra ANTES del período próximo para que "semana" no active "semana que viene"). Cambio de 2 tokens en 2 regex, sin nueva rama de código.
- **Tests**: +5 tests de regresión en `NaturalTaskParserTest.kt` (`mediadosDeLaSemanaAnclaMiercoles`, `mitadDeLaSemanaEsSinonimoDeMediados`, `principiosDeLaSemanaAnclaLunes`, `mediadosDeLaSemanaRespetaHoraExplicita`, `aMediadosDeLaSemanaConPrefijoAOpcional`). `bash tools/run_domain_tests.sh` → **920 PASS** (915 c.129-run4 + 5); `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK en este entorno).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (2 patrones), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+5 tests), `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 920 tests, 0 failures). Integración Android NO VERIFICADA.
- **Colisión gestionada**: run concurrente `ccbb450` resolvió items 1 y 2 del diagnóstico c.130 previo. Aporte neto de este run = item 3 únicamente. No se forzó push, no se sobrescribió trabajo válido.
- **Próxima prioridad**: gaps P2 ABIERTOS del parser ("día N de este mes"→null; "día 15" sin artículo→null; "1ro de septiembre" ordinal→null) y descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

> Registro cronológico de sesiones autónomas (append-only, no borrar entradas).

## Ciclo 129 — 2026-08-14 — Refactor P1 automatización/recordatorios: extraer `AutomationActionPlanner` de `AutomationEngine` + respetar `reminderAt` previo del usuario

- **Run/ciclo**: 129 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK, HEAD local = HEAD remoto = `1d9d612` (c.128). Sin divergencia, sin push concurrente al iniciar. Working tree limpio.
- **HEAD inicial**: `1d9d612…` (c.128).
- **Problema seleccionado (P1 → automatización/recordatorios, área no-parser)**: la lógica de planificación de acciones de las automatizaciones (`PLAN_DAY`/`RESCHEDULE_OVERDUE`/`BATCH_QUICK_TASKS`/`REVIEW_COMMITMENTS`) vivía **dentro** de `AutomationEngine.kt` (objeto `AutomationActionPlanner` anidado en la misma clase), acoplada a repositorios concretos, `Mutex` y `ReminderScheduler`. Como `AutomationEngine` depende de DAOs de Room (vía `Repositories.kt`) y `AutomationWorker` de WorkManager, **ningún test de automatización se ejecutaba en el harness JVM** (las clases `AutomationRulesTest`, `AutomationUndoRulesTest` ya existentes no se compilaban/corrían). Además, las ramas `PLAN_DAY` y `BATCH_QUICK_TASKS` **no preservaban `reminderAt`**: al replanificar, copiaban la tarea sin tocar `reminderAt`, pero el valor previo quedaba; en la versión pre-extracción (rama `BATCH_QUICK_TASKS`) no se asignaba `reminderAt` en absoluto → un slot futuro planificado por la automatización no generaba recordatorio, y peor, si el usuario había fijado uno propio, en algunas ramas se perdía. Era una mejora real de producto (recordatorios más útiles + prevención de olvidos) que cumplía el criterio de "menos interfaz + más potencia" — sin nueva pantalla/botón.
- **Prioridad**: P1 (automatizaciones/recordatorios/workers; área de dirección "mejores recordatorios" + "automatizaciones locales").
- **Causa raíz**: (1) el planificador de acciones no era una unidad aislada testeable (anidado en una clase con dependencias Android); (2) el harness JVM no incluía los archivos de automatización en su lista de fuentes; (3) el manejo de `reminderAt` en `PLAN_DAY`/`BATCH_QUICK_TASKS` no consideraba el valor previo del usuario ni la condición futuro/pasado del slot.
- **Solución (mínima, sin nueva pantalla/botón)**:
  - (1) `AutomationActionPlanner` extraído a su propio archivo `AutomationActionPlanner.kt` (puro: sólo depende de `data.local` + `domain` — sin Android/Room/WorkManager). Retorna `AutomationPlan(updates, creates, message, matched)`. `AutomationEngine.runRule` ahora delega en `AutomationActionPlanner.build(rule, allTasks, pending, now, zone)`; se eliminaron los imports ahora no usados de `AutomationEngine`.
  - (2) **Mejora de recordatorios** en `PLAN_DAY` y `BATCH_QUICK_TASKS`: `reminderAt = byId[block.taskId]?.reminderAt ?: (start si start > now else null)` — **respeta el recordatorio que el usuario puso** y, si no había, coloca uno en el inicio del slot **sólo si es futuro** (evita recordatorios tardíos para slots ya pasados al planificar a media mañana). En `RESCHEDULE_OVERDUE` el recordatorio se reubica a 1h antes del nuevo `dueAt` (manteniendo el comportamiento de aviso previo al vencimiento).
  - (3) `AutomationSchedulePolicy` (objeto puro `triggerForHour`) extraída de `AutomationWorker.kt` a su propio archivo `AutomationSchedulePolicy.kt`, para poder testear la política de ventanas mañana/noche sin arrastrar WorkManager/Context.
  - (4) `tools/run_domain_tests.sh` ampliado: compila los 4 archivos puros de automatización (`AutomationRules.kt`, `AutomationSchedulePolicy.kt`, `AutomationUndoRules.kt`, `AutomationActionPlanner.kt`) + sus tests. Esto **activó** tests de automatización que antes eran NO VERIFICADOS en JVM.
- **Tests**: +9 tests en `AutomationActionPlannerTest.kt` (recordatorio previo preservado en PLAN_DAY/BATCH_QUICK_TASKS; slot futuro obtiene reminder, slot pasado no; condición HAS_OVERDUE_TASKS sin vencidas no se dispara; RESCHEDULE_OVERDUE reprograma a futuro con reminder 1h antes; BATCH_QUICK_TASKS agrupa sin slots duplicados y respeta reminder previo; REVIEW_COMMITMENTS crea tarea y evita duplicados; no se dispara sin compromisos; BATCH_QUICK_TASKS ignora tareas largas). `bash tools/run_domain_tests.sh` → **873 PASS** (856 c.128 + 17 de automatización: 5 `AutomationRulesTest` + 3 `AutomationUndoRulesTest` ya existentes + 9 nuevos). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). `AutomationEngine.runRule` (ruta de persistencia/log/deshacer con `TaskSnapshotCodec`) y `AutomationWorker` (WorkManager) quedan fuera del harness JVM — el cambio en `AutomationEngine` es una delegación directa al planner extraído (misma firma conceptual), pero su integración end-to-end no se probó en Android.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/automation/AutomationEngine.kt` (slimmed + delegación), `app/src/main/java/com/ordia/app/automation/AutomationWorker.kt` (removida `AutomationSchedulePolicy`), `tools/run_domain_tests.sh` (fuentes de automatización). **Creados**: `app/src/main/java/com/ordia/app/automation/AutomationActionPlanner.kt`, `app/src/main/java/com/ordia/app/automation/AutomationSchedulePolicy.kt`, `app/src/test/java/com/ordia/app/automation/AutomationActionPlannerTest.kt`. `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 873 tests, 0 failures). Integración Android NO VERIFICADA.
- **Próxima prioridad**: descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento, backup/restore con DAOs reales); auditoría de `AutomationEngine.runRule` (persistencia/log/deshacer) queda NO VERIFICADA sin Android SDK.



> Registro cronológico de sesiones autónomas (append-only, no borrar entradas).

## Ciclo 129 (run 2) - 2026-08-14 (UTC) - fix(parser): 4 residuos de título que mutilaban la captura ("este lunes"/"en la tarde de hoy"/"a la semana que viene"/"ahorita mismo") + feat(parser): "al amanecer" hora canónica (P1 integridad de datos / captura olvidada)

- **Run/ciclo**: 129-run2 (rama `openhands/autonomous-ordia`). HEAD inicial `2c8bcf9` (c.128 anoche/antenoche, sincronizado con remoto, working tree con cambios sin commitear del run anterior: feat amanecer + 4 fixes de residuo en progreso). STALE_RUN=false (base al día, sin divergencia).
- **HEAD inicial**: `2c8bcf919ee1f20ec58235b1894bc3341a79a5fe` (c.128); sincronizado a `33f353a` (c.129-run1 remoto) antes de reaplicar.
- **Problema seleccionado**: **P1** integridad de datos del título del parser. 4 residuos descubiertos por probe JVM (c.129-run1) que mutilaban el contenido capturado aunque la fecha era correcta (insidioso: el test de fecha pasa pero el título queda degradado; el usuario no ve el daño hasta revisar la tarea): (1) "reunión este lunes" → título "reunión este" (residuo "este"); (2) "reunión en la tarde de hoy" → título "reunión de" (residuo "de"); (3) "entregar a la semana que viene" → título "entregar a" (residuo "a"); (4) "llamar ahorita mismo" → título "llamar mismo" (residuo "mismo"). Causa raíz común: los patrones consumían la unidad temporal pero no los determinantes/conectores/sufijos pegados. Además se completa el feat del run anterior: "al amanecer"/"al alba"/"al despuntar el día"/"al clarear"/"al aclarar" (~06:00, hora canónica de salida del sol) no se reconocían → `dueAt=null` + residuo → tarea olvidada (asimetría con "al mediodía"/"a medianoche"/"a primera hora"/"al final del día").
- **Prioridad**: P1 (integridad de datos del título + evita olvidos de muy temprano).
- **Causa raíz**: (1) `weekdayPattern` no listaba `este` como determinante (sólo `el/del/de`); (2) `standalonePartOfDayPattern` no consumía el sufijo "de hoy/mañana" tras la parte del día; (3) `nextPeriodPattern` no admitía el conector direccional "a" antes de "la semana que viene"; (4) `nowPattern` listaba "ahorita" antes que "ahorita mismo" → la alternancia corta ganaba y "mismo" sobrevivía. (amanecer) ninguna rama reconocía las frases de muy temprano.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  1. **"este lunes"**: `weekdayPattern` añade `este\s+` como determinante opcional junto a `el/del/de`. Guard: "este proyecto" (determinante de contenido) no se toca (test `esteComoDeterminanteDeContenidoNoSeBorra`).
  2. **"en la tarde de hoy"**: `standalonePartOfDayPattern` añade sufijo opcional `(?:\s+de\s+(?:hoy|mañana|ayer|anteayer|antier))?` que consume el calificador de fecha tras la parte del día. Reusa TODO el flujo (hora canónica, fecha por otra rama o "hoy").
  3. **"a la semana que viene"**: `nextPeriodPattern` añade conector `a` opcional `(?:a\s+)?` antes del artículo, con guard `(?<!\p{L})` (lookbehind de letra) en VEZ de `\b` para no robar la "a" final de palabras acentuadas como "Auditoría" (`\b` ASCII no trata vocales acentuadas como word-char → "Auditoría" se truncaba a "Auditorí"). Regresión detectada y resuelta en este run.
  4. **"ahorita mismo"**: `nowPattern` lista `ahorita\s+mismo` ANTES que `ahorita` en la alternancia (regex alternancia greedy por orden → frase completa case primero).
  5. **"al amanecer"** (feat del run anterior, completado aquí): nuevo `amanecerPattern = (?i)al\s+(?:amanecer|alba|despuntar\s+(?:el|la|de\s+la|del)\s+(?:alba|d[ií]a)|clarear|aclarar)\b` + `amanecerTime=LocalTime.of(6,0)`, simétrico a `primeraHoraPattern`/`ultimaHoraPattern`/`alFinalDelDiaPattern`. Exige el conector "al " para NO casar el verbo "amanecer" ni el sustantivo poético suelto. Hora de respaldo: si hay hora explícita, ésta gana y el patrón solo limpia "al amanecer".
- **Tests**: +10 tests TDD en `NaturalTaskParserTest.kt` para los 4 residuos (`esteLunesResuelveFechaYLimpiaDeterminante`, `esteSabadoResuelveFechaYLimpiaDeterminante`, `esteComoDeterminanteDeContenidoNoSeBorra`, `enLaTardeDeHoyLimpiaSufijoYResuelveHoraCanonica`, `enLaNocheDeHoyLimpiaSufijoYResuelve21h`, `porLaTardeDeMananaLimpiaSufijoYResuelveFechaYHora`, `aLaSemanaQueVieneLimpiaConectorYResuelveFecha`, `laSemanaQueVieneSinConectorSigueFuncionando`, `palabraConAFinalNoSeTruncaComoConector`=guard "Auditoría", `ahoritaMismoVenceAhoraYLimpiaTitulo`) + 8 tests del feat amanecer del run anterior. `bash tools/run_domain_tests.sh` → **890 PASS** (856 c.128 + 17 automatizacion c.129-run1 + 8 amanecer + 10 residuos); `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Bugs**: 1 regresión detectada y resuelta en este run: el conector "a" del fix (3) sin guard robaba la "a" final de "Auditoría" (`proximoTrimestreParsesDueAt` fallaba: "Auditorí" en vez de "Auditoría"). Causa: `\b` no trata vocales acentuadas como word-char. Fix: `(?<!\p{L})` lookbehind de letra Unicode. Tras el fix y el merge con c.129-run1 (automatizaciÃ³n), 890 PASS.
- **Features**: 1 ("al amanecer" hora canónica — más potencia en captura sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: la clase de bug "residuo de título" es la más insidiosa porque la fecha se resuelve bien (test de fecha pasa) pero el título queda mutilado — sólo se detecta comprobando `title` además de `dueAt`. La regresión "Auditoría" confirma que `\b` ASCII NO sirve para vocales acentuadas en español: usar `(?<!\p{L})` (Unicode) para guards de límite de palabra con acentos. Backlog ABIERTO P2: "día N" sin artículo, "1ro de septiembre" ordinal sin artículo. Próxima prioridad: seguir descubrimiento continuo de gaps del parser y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 123 - 2026-08-14 (UTC) - fix(parser): pleonasmo "el día de mañana/hoy" corrompía el título + "a primeros de mes"/"a fin de la semana" olvidados (P1 integridad de datos / tareas olvidadas)

- **Run/ciclo**: 123 (rama `openhands/autonomous-ordia`). HEAD inicial `9a4b5ee` (c.121 guardián, base local al arrancar). Al `git fetch` el remoto había avanzado 1 commit por un run paralelo (c.122 search por parte del día `f7685ea`). Mi trabajo (parser) estaba sin commitear. Procedimiento seguro (sin force push / reset destructivo): `git stash` → `git merge --ff-only origin/...` (fast-forward limpio a `f7685ea`) → `git stash pop` (reaplica mis cambios en `NaturalTaskParser.kt`+`NaturalTaskParserTest.kt`, área distinta del `SearchEngine.kt` del otro agente → sin conflicto). STALE_RUN=false (base actualizada de forma no destructiva; trabajo del otro agente preservado).
- **HEAD inicial**: `9a4b5ee922bd0720fd737ed188b3cae872432e4c` (c.121); sincronizado a `f7685ea` (c.122 remoto) antes de reaplicar.
- **Problema seleccionado**: **P1** integridad de datos + tareas olvidadas, descubierto por probe JVM (`ParserGapProbe.kt`). (1) **Pleonasmo corruptor de título**: "el día de mañana"/"el día de hoy"/"para el día de mañana" (forma coloquial de "mañana"/"hoy") se fechaba correctamente PERO el borrado genérico de `mañana`/`hoy` dejaba el residuo "el día de" → título degradado "reunión el día de" en vez de "reunión" (contenido capturado mutilado; el usuario no ve el daño hasta revisar la tarea). (2) **"a primeros de mes"** (variante financiera de "principios de mes", vencimientos de alquiler/tarjeta/servicios) → `dueAt=null` (sólo "principios" se reconocía). (3) **"a fin de la semana"** (sinónimo de "esta semana", plazo a fin de semana actual) → `dueAt=null`. Los 3 son huecos de captura/recuperación del área de dirección explícita.
- **Prioridad**: P1 (integridad de datos del título + evita olvidos de vencimientos financieros/semanales).
- **Causa raíz**: (1) el borrado genérico de tokens sueltos (`mañana`/`hoy`) no consumía la frase pleonástica completa — ésta se interpretaba como fecha por el motor de "mañana"/"hoy" pero su prefijo "el día de" sobrevivía al cleanup del título. (2) `startOfMonthPattern` (c.32 family) sólo listaba `principios?`. (3) `thisWeekPattern` (c. "esta semana") sólo casaba `esta semana`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  1. **Pleonasmo**: nuevo `.replace(Regex("""(?i)\b(?:para\s+)?(?:el|del)\s+d[ií]a\s+de\s+(?:ma[nñ]ana|hoy)\b"""), " ")` colocado ANTES del borrado genérico de tokens sueltos. Consume la frase completa (incluye "para el día de mañana") → no deja residuo; el regex genérico posterior sigue borrando los tokens sueltos para "mañana"/"hoy" dichos sin pleonasmo (frases normales intactas). Acepta sin tilde "dia" y "manana".
  2. **Primeros de mes**: `startOfMonthPattern` añade `primeros?` junto a `principios?` en la misma alternancia → reusa TODO el flujo de `principios de mes` (día 1 del mes siguiente, hora explícita, título limpio, roll si hoy=día 1).
  3. **Fin de la semana**: `thisWeekPattern` añade `(?:a\s+)?fin(?:es)?\s+de\s+la\s+semana` a la alternancia. **Exige "de la"** para NO colisionar con `weekendPattern` "fin de semana"=sábado, que sigue intacto y se procesa en su bloque propio. Reusa la resolución de `esta semana` (próximo domingo ISO, respeta hoy-domingo).
- **Tests**: +5 en `NaturalTaskParserTest.kt`: `elDiaDeMananaNoDejaResiduoEnTitulo` (título "reunión", fecha +1d), `elDiaDeHoyNoDejaResiduoEnTitulo` (título limpio, fecha hoy), `paraElDiaDeMananaNoDejaResiduoEnTitulo` (consume "para el día de"), `primerosDeMesVarianteDePrincipios` (→2026-09-01 día 1 del mes siguiente), `finDeLaSemanaResuelveProximoDomingo` (→2026-08-02 domingo de esta semana). `bash tools/run_domain_tests.sh` → **820 PASS** (815 base c.122 + 5); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM post-fix (3 gaps + control de no-regresión): `reunión el día de mañana`→mañana 09:00 título "reunión"; `el día de hoy`→hoy 09:00 título "reunión"; `para el día de mañana`→mañana título "reunión"; `pagar a primeros de mes`→09-01 título "pagar"; `entregar a fin de la semana`→domingo título "entregar"; sin regresión: `próximo lunes`/`lunes que viene`/`fin de semana`(sábado intacto)/`anteayer`/`fin de mes`/`mediados`/`esta semana` todos verde.
- **Bugs**: 0 (corrupción de título silenciosa + vencimientos olvidados; no crash).
- **Features**: 0 (cobertura de parsing de formas coloquiales ya implícitas en la semántica; sin UI nueva).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: el probe JVM (`ParserGapProbe.kt`) sigue siendo eficaz para encontrar huecos reales del parser — los 3 gaps eran formas coloquiales cotidianas (no casuística inventada). El patrón pleonástico es la clase más insidiosa: la fecha se resuelve bien, así el test de fecha pasa, pero el título queda mutilado — sólo se detecta comprobando el `title` además del `dueAt`. Próxima prioridad: salir del parser hacia la prioridad de memoria — auditar `WhatNowEngine.reason()` vs `TaskRules.timeRank()` (consistencia etiquetado/ranking cuando una tarea es a la vez scheduled-later y due-today); contexto/onboarding; descubrimiento continuo.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Estado" con ciclo 123 + contador 820 tests), `BACKLOG.md` (3 ítems P1 resueltos), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **HEAD final**: (tras commit de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM). **820 domain tests PASS** tras merge con c.122 search del otro agente (clases distintas, sin colisión), smoke 25 OK.
- **Próxima prioridad**: salir del parser hacia la prioridad de memoria (auditar `WhatNowEngine.reason()` vs `TaskRules.timeRank()`); contexto/onboarding; descubrimiento continuo en accesibilidad/rendimiento.

---

## Ciclo 120 - 2026-08-14 (UTC) - fix(search): búsqueda por fecha pura ("hoy"/"vencidas"/"esta semana") inundada de ruido sin fecha (P1 recuperación/búsqueda universal)

- **Run/ciclo**: 120 (rama `openhands/autonomous-ordia`). Base inicialmente en `b98d574`; al commitear y hacer push, el remoto había avanzado 1 commit por un run paralelo (c.119 "y media/medio" `0c60de7`, parser). Procedimiento seguro: `git fetch` → `git rebase origin/openhands/autonomous-ordia` (mi commit toca `SearchEngine.kt` + tests, el paralelo toca `NaturalTaskParser.kt`; único conflicto en memoria `CURRENT_STATE.md` resuelto tomando la versión remota + re-insertando mi entrada renumerada 119→120). STALE_RUN=false (rebase no destructivo, sin fuerza, sin `reset --hard`).
- **HEAD inicial**: `b98d574` (c.118 remoto final, "fix(parser): crash IndexOutOfBoundsException...").
- **Problema seleccionado**: **P1** (recuperación de información / búsqueda universal — el área explicitada como siguiente prioridad tras salir del parser). En `SearchEngine.kt`, cuando la consulta expresa SOLO un scope de fecha ("hoy", "mañana", "vencidas", "esta semana", "ayer"...) sin palabras de contenido, las entidades SIN fecha (proyectos, notas, hábitos, conversaciones, compromisos, automatizaciones) se devolvían TODAS. Causa: la closure `matches()` del motor (línea 66) implementa "scope de fecha + sin palabras de texto ⇒ `return true`" para que las tareas que cumplen el rango aparezcan, pero esa misma closure se reutilizaba como predicado de los 6 filtros fecha-menos. Con `textWords` vacío, `matches(cualquierCosa)` era siempre `true`, así "hoy" devolvía cada nota y cada proyecto aunque nada tuviera relación con hoy. Síntoma real: buscar "hoy" o "vencidas" (el atajo mental más natural para "qué tengo que hacer hoy/qué se me pasó") devolvía una ensalada de notas, proyectos y hábitos sin fecha encima de las tareas relevantes, enterrando la señal bajo ruido. Inversamente, buscar "esta semana" listaba todos los proyectos. Esto rompe el contrato de "recuperación de tareas olvidadas": el usuario escribe "vencidas" esperando ver SOLO lo atrasado, y recibe ruido.
- **Prioridad**: P1 (búsqueda/recuperación de información: el ruido sin fecha enterraba las tareas relevantes y falseaba la intención de "qué tengo hoy/vencidas").
- **Causa raíz**: `matches()` mezcla dos responsabilidades —(a) confirmar match de contenido textual Y (b) degradar a `true` cuando el scope es de fecha pura. (b) sólo es correcto para entidades que se filtran por fecha (tareas); aplicarlo a entidades fecha-menos las pasa todas. No era un bug de datos (no se perdía nada) sino de SEÑAL: demasiados resultados irrelevantes.
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**: introducir `val pureDateScope = dateScope != null && textWords.isEmpty()` y añadir `&& !pureDateScope` a los 6 filtros fecha-menos (projects/notes/habits/conversations/commitments/automations). Así un scope de fecha puro devuelve exclusivamente entidades con fecha (tareas). Cuando SÍ hay palabras de contenido además del scope ("hoy reunion"), `pureDateScope=false` y el contenido fecha-menos sigue siendo relevante (se filtra por texto), preservando el comportamiento útil. Lógica local honesta, sin random ni IA falsa. Retrocompatible: las búsquedas de contenido puro ("compras") no se ven afectadas (`dateScope=null` ⇒ `pureDateScope=false`).
- **Tests**: +3 en `SearchEngineDateScopeTest.kt`: `pureDateScope_excludesDatelessEntities` ("hoy" con tarea de hoy + proyecto/nota/hábito sin fecha ⇒ sólo la tarea), `pureDateScope_overdueExcludesDatelessEntities` ("vencidas" ⇒ sólo la tarea atrasada), `dateScopeWithContent_returnsMatchingDatelessEntities` ("hoy reunion" ⇒ tarea de hoy + nota sobre "reunión", nota ajena excluida — verifica que el fix no rompe el caso útil). Tras el fix: `bash tools/run_domain_tests.sh` → **803 PASS** (800 base c.119 remoto + 3 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Bugs**: 1 (P1, ruido sin fecha en búsqueda por fecha pura; FIXED).
- **Features**: 0 (mejora de precisión de búsqueda existente, sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real de la búsqueda en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: el USER_CONTEXT pedía salir del parser hacia WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas. Esta mejora cae directamente en "búsqueda universal / recuperación de información" (un eje de dirección explícito). Próximo paso: auditar `WhatNowEngine.kt`/`GuardianEngine.kt` en busca de bugs reales (scoring, exclusión de tareas, contexto) y la recuperación de tareas olvidadas (detección de vencidas importantes, "What Now" más útil).
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Estado" con ciclo 120 + contador 803 tests), `BACKLOG.md` (fila c.120), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`, `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `b137099` (fix(search): scope de fecha puro ("hoy"/"vencidas") ya no inunda resultados con entidades sin fecha (P1 recuperación/búsqueda universal)).
- **HEAD final**: `b137099` (== `origin/openhands/autonomous-ordia`; push con `$github_token`).
- **Estado**: FIXED → VERIFIED (dominio JVM). 803 domain tests PASS (800 base c.119 remoto + 3), smoke 25 OK.
- **Próxima prioridad**: auditar `WhatNowEngine.kt`/`GuardianEngine.kt` (bugs reales de scoring/exclusión/contexto) y recuperación de tareas olvidadas (vencidas importantes, "What Now" más útil).

---
## Ciclo 121 - 2026-08-14 (UTC) - feat(guardián): rescate de tareas olvidadas SIN fecha en la bandeja (capturas arrinconadas ≥7 días) → el coach deja de pasarlas como "siguiente paso" genérico (P1 recuperación de olvidadas)

- **Run/ciclo**: 121 (rama `openhands/autonomous-ordia`). Base inicialmente en `0c60de7` (c.119 "y media/medio" en plazos de día/semana/mes/año). Al sincronizar, el remoto había avanzado 2 commits por un run paralelo (c.120 fix(search) `b137099` + docs `6ab0445`); sin divergencia destructiva: mi trabajo eran 2 commits limpios sobre `0c60de7` y el run paralelo tocó `SearchEngine.kt` (área distinta de `GuardianCoach.kt`). Procedimiento seguro (sin force push / reset destructivo): `git checkout -B openhands/autonomous-ordia origin/...` (actualiza al remoto, preservando el trabajo del otro agente) → `git cherry-pick 0f42651` (mi feature commit) → resolución de conflictos en AI_AUTONOMY (BACKLOG/CURRENT_STATE: ambos runs anteponíamos entrada en el mismo punto → se mantienen AMBAS; el c.120 del otro agente aterrizó primero en remoto, por eso el mío se renumera a c.121). Los archivos de código (`GuardianCoach.kt`, `GuardianCoachTest.kt`) aplicaron sin conflicto. STALE_RUN=false (base actualizada de forma no destructiva; trabajo del otro agente preservado).
- **Problema seleccionado**: **P1** recuperación de tareas olvidadas (prioridad de memoria explícita, área de dirección "recuperación de tareas olvidadas"). La lógica de rescate del `GuardianCoach` SOLO miraba tareas **vencidas** (con `dueAt` en el pasado): el nudge "RECUPERA EL CONTROL" aparecía cuando una tarea incumplía su fecha. Pero una idea **capturada en la bandeja SIN fecha** (`dueAt=null`, `startAt=null`) y nunca agendada se quedaba esperando indefinidamente sin disparar NINGÚN rescate: caía al "SIGUIENTE PASO" genérico ("Ordía la priorizó por fecha, importancia y estado") por mucho que llevara semanas arrinconada. La recuperación de olvidados era ciega a la clase más real de "olvido": la captura que el usuario metió y nunca decidió qué hacer con ella. Probe JVM confirmó: tarea creada hace 3 semanas sin fecha → `eyebrow=SIGUIENTE PASO`, `message="Ordía la priorizó..."` (sin mención de los 3 meses/semanas esperando); `WhatNowEngine` la sugería con `reason=NEXT_INBOX`.
- **Prioridad**: P1 (recuperación de información importante / evita olvidos; sin pérdida de datos pero sí abandono silencioso de capturas).
- **Causa raíz**: el rescate existente (`overdue.maxOf { overdueDays }` ≥ umbral) estaba atado a `dueAt`. No existía ninguna noción de "antigüedad en bandeja" (por `createdAt`) para tareas sin fecha. La edad se computaba solo del vencimiento, no de la creación.
- **Solución (mínima, `GuardianCoach.kt`, sin nueva pantalla ni botón — "menos interfaz, más potencia")**: tras las ramas de vencidas y de "protege tu día" (urgentes/high que vencen hoy), se calcula `next = TaskRules.nextBestTask(pending, now)` (fuente única del ranking, ya time-aware). Si `next` es ella misma una captura sin fecha (`dueAt==null && startAt==null`) cuya **antigüedad de calendario** (`inboxAgeDays`, días completos entre `createdAt` y hoy en la zona del usuario — DST-robusta, idéntica a `overdueDays`) ≥ `STALE_INBOX_DAYS_THRESHOLD` (7 días), se **reencuadra** como "RECUPERA EL CONTROL" con la decisión real ("hazla hoy, agéndala o quítala: no la dejes pasar otra vez") + la etiqueta de edad legible (reusa `forgottenAgeLabel`: "3 semanas", "2 semanas"…). Varias olvidadas: surface el recuento y la edad de la más antigua, sugiere la mejor (`nextBestTask`). **Decisión clave (anti-regresión)**: delegar en `nextBestTask` y solo reencuadrar si lo elegido ES la captura olvidada garantiza que el rescate **nunca** robe el lugar a algo más time-sensitive (algo que vence hoy, urgente sin fecha…): el nudge aparece únicamente cuando lo que de todos modos iría primero es la captura arrinconada. Umbral más alto (7) que el de vencidas (2): una tarea sin fecha no incumple ningún vencimiento, así que se le da más margen antes de llamarla "olvidada". Heurística local honesta (edad real, no random ni IA). Retrocompatible.
- **Tests**: +6 en `GuardianCoachTest.kt` (TDD RED→GREEN): `staleInboxTaskSurfacesAsForgottenRecovery` (3 semanas → RECUPERA EL CONTROL, FOCUSED, "3 semanas"), `freshInboxTaskDoesNotTriggerRecovery` (captura de hoy → no RECUPERA), `staleInboxBelowThresholdStaysGeneric` (6 días < 7 → no RECUPERA), `dueTodayTaskBeatsStaleInboxRecovery` (algo vence hoy NORMAL → SIGUIENTE PASO taskId=la de hoy, no el rescate), `staleInboxUsesWeeksLabel` (14 días → "2 semanas"), `multipleStaleInboxSurfacesCountAndOldestAge` (2 olvidadas → "2 tareas" + "3 semanas"). Tras el fix: `bash tools/run_domain_tests.sh` → **806 PASS** (28 clases — 800 base c.119 + 6); `bash tools/run_domain_checks.sh` → smoke 25 OK. Sin regresión (todos los tests previos del coach — overdue/forgotten-overdue/DST/habit — verdes).
- **Bugs**: 0 (brecha de recuperación, no crash).
- **Features**: 1 (rescate de capturas olvidadas sin fecha; reencuadre del insight existente, sin UI nueva).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del insight en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: confirmado con probe JVM que `WhatNowEngine` YA ordenaba la captura más antigua primero (por `createdAt`) cuando no hay nada time-sensitive — así que el rescate no cambia QUÉ se sugiere, solo CÓMO se enmarca (de "siguiente paso" a "recupera el control" con edad), que es exactamente el valor: llamar la atención sobre el abandono. La asimetría resuelta (olvidadas-con-fecha sí rescataban, olvidadas-sin-fecha no) era un hueco conceptual en la "recuperación de tareas olvidadas" del área de dirección. Próxima prioridad: seguir en WhatNow/Guardián — auditar si `WhatNowEngine.reason()` y `TaskRules.timeRank()` pueden divergir en el etiquetado cuando una tarea es simultáneamente scheduled-later y due-today (razón vs ranking); descubrimiento continuo en contexto/onboarding.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Estado" con ciclo 121 + contador 806 tests), `BACKLOG.md` (nuevo ítem P1 resuelto), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/GuardianCoach.kt`, `app/src/test/java/com/ordia/app/domain/GuardianCoachTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **Commits**: `4d080a4` (feat(guardián): rescate de tareas olvidadas SIN fecha en la bandeja (P1)) — cherry-pick sobre base `6ab0445` del c.120 del otro agente (preserva su `b137099` fix(search)).
- **HEAD final**: `4d080a4` → push a `origin/openhands/autonomous-ordia` (con `$GITHUB_TOKEN`).
- **Estado**: FIXED → VERIFIED (dominio JVM). **809 domain tests PASS** tras merge (800 c.119 + 3 search c.120 + 6 guardián c.121; ambas features coexisten sin conflicto), smoke 25 OK.
- **Próxima prioridad**: auditar `WhatNowEngine.reason()` vs `TaskRules.timeRank()` (consistencia etiquetado/ranking); descubrimiento continuo en contexto/onboarding/accesibilidad.

---
## Ciclo 118 - 2026-08-14 (UTC) - fix(parser): crash IndexOutOfBoundsException al borrar tokens de duración cuando varios patrones casan el mismo span ("30 min") → la tarea no se crea (P0 pérdida de captura)

- **Run/ciclo**: 118 (rama `openhands/autonomous-ordia`). Base inicialmente en `51360e3` (c.117 local del commit 51360e3), pero al `git fetch` el remoto había avanzado 2 commits por runs paralelos (c.116 fracciones sub-hora `df0abd4`, c.117 "al final del día" `0f184ff`). Sin divergencia destructiva: mi trabajo era solo no-commiteado sobre `51360e3`. Procedimiento seguro: `git stash` → `git pull --ff-only` (a `0f184ff`) → `git stash pop` (reaplicado sin conflictos sobre el nuevo HEAD; los commits paralelos tocaron otras partes del parser —fracciones y "al final del día"—, no el bloque de borrado de duración). STALE_RUN=false (base actualizada de forma no destructiva).
- **HEAD inicial**: `51360e3` (c.117 commit 51360e3 "fix(parser): borrado de tokens temporales deja de ser global-literal"); al sincronizar se avanzó a `0f184ff`.
- **Problema seleccionado**: **P0** integridad de datos (crash → pérdida de captura). El c.117 (commit 51360e3) había reemplazado `working.replace(match.value, " ")` (reemplazo **global y literal** que corrompía ocurrencias legítimas del token duplicado en el título) por `connectorRange` + `replaceRange` (borrado **localizado por rango**). Pero el nuevo código crasheaba: cuando un mismo span casa **varios** patrones a la vez — **"30 min"** casa `durationMatch` (numérico) **Y** `writtenMatch` (el patrón escrito admite dígitos como cantidad) — el `buildList` recolectaba **dos rangos idénticos** (`11..16`). Al iterarlos con `replaceRange`, el primero mutaba `working` de longitud 17 → 9, y el segundo `replaceRange(8..16)` sobre esa cadena acortada lanzaba `IndexOutOfBoundsException: Range [17, 9) out of bounds for length 9`. Síntoma: cualquier tarea con duración numérica y conector ("reunion de 30 min", "estudiar 2 horas", "trabajar 2h") **crasheaba el parser** → la tarea **no se creaba** (pérdida de captura silenciosa en el punto de entrada más frecuente).
- **Prioridad**: P0 (crash en captura de tareas con duración, la forma más común de duración; pérdida de datos).
- **Causa raíz**: el c.117 asumió que los tres patrones (duration/written/fractional) son mutuamente excluyentes, pero **no lo son**: el patrón escrito admite dígitos, así "30 min" casa dos veces y genera rangos duplicados. `replaceRange` aplicado dos veces sobre `working` mutado deja índices fuera de rango.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**: los rangos recolectados en `durationBlankRanges` se **deduplican por solapamiento** antes de aplicarse. Se ordenan descendente por `first` y, al iterar, se **salta** cualquier rango cuyo `last >= lastEnd` (solapa con uno ya borrado). Así borrar el mismo span dos veces es imposible y se cubre cualquier otro solapamiento futuro entre los tres patrones (duration/written/fractional). El ganador del `when` de `durationMinutes` decide la duración; el borrado es solo cosmético (limpieza del título). Se mantiene el helper `connectorRange` (extiende el rango hacia atrás para incluir el conector "de|durante|por" inmediatamente anterior, sin residuo en el título). Lógica local honesta, sin random ni IA falsa. Retrocompatible.
- **Tests**: +3 en `NaturalTaskParserTest.kt`: `duracionNumericaConConectorNoCrashaPorMatchDuplicado` ("reunion de 30 min" → título "reunion", dur 30 — exponía el crash antes del fix), `duracionRepetidaComoContenidoPreservaSegundaOcurrencia` ("30 min de ejercicio 30 minutos extra" → dur 30, la segunda ocurrencia "30 minutos" se preserva), `duracionEscritaRepetidaComoContenidoPreservaSegundaOcurrencia` ("dos horas de estudio y dos horas mas" → dur 120, la segunda "dos horas" se preserva). Tras el fix: `bash tools/run_domain_tests.sh` → **794 PASS** (28 clases — 791 base c.117 + 3 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM 11 casos verde: single ("reunion de 30 min"→reunion/30, "estudiar 2 horas"→estudiar/120, "trabajar 2h"→trabajar/120, "dos horas de estudio"→de estudio/120, "media hora de lectura"→de lectura/30) sin regresión; multi ocurrencias ("30 min de ejercicio 30 minutos", "2 horas de estudio y 2 horas mas", "llamar 2h y luego ver 2h de video", "reunion de 30 min con 30 minutos extra", "dos horas de estudio y dos horas mas", "trabajar dos horas y descansar dos horas") todas sin crash, duración correcta y segunda ocurrencia preservada.
- **Bugs**: 1 (P0, crash IndexOutOfBoundsException en captura de tareas con duración numérica + conector; FIXED).
- **Features**: 0 (cierre de la regresión introducida en c.117; robustez del borrado localizado).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: el bug revela una asimetría latente: `writtenDurationPattern` admite dígitos, solapando con `durationPatterns`. No es un defecto (ambos resuelven la misma duración), pero cualquier futuro borrado por rango DEBE deduplicar. Lección genérica: cuando se recolectan rangos de varios `find()` para aplicar `replaceRange` secuencial sobre `working` mutable, SIEMPRE deduplicar/saltar solapados. Próxima prioridad: salir del parser hacia la prioridad de memoria (auditar WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas); gap OPEN restante del c.113: "una semana y media"/"un mes y medio" (+0.5 de la unidad) — evaluar frecuencia real (anti-feature-bloat).
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Estado" con ciclo 118 + contador 794 tests), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `7a9dfc2` (fix(parser): crash IndexOutOfBoundsException al borrar tokens de duración con match duplicado (P0)).
- **HEAD final**: `7a9dfc2` (== `origin/openhands/autonomous-ordia`; push con `$github_token`).
- **Estado**: FIXED → VERIFIED (dominio JVM). 794 domain tests PASS (791 base c.117 + 3), smoke 25 OK.
- **Próxima prioridad**: salir del parser hacia WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas / contexto / onboarding; gap "una semana y media"/"un mes y medio" (anti-feature-bloat).

---
## Ciclo 109 - 2026-08-14 (UTC) - fix(parser): parte del día COMPACTA "hoy tarde"/"mañana noche"/"pasado mañana tarde" → agenda errónea 09:00 + residuo en el título (P1 captura/agenda)

- **Run/ciclo**: 109 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial `0b91a1b` (c.108 remoto final, "docs: RUN_LOG registra commit/hash final ciclo 108"). `git status` limpio, rama == `origin/openhands/autonomous-ordia`. Sin divergencia al iniciar; STALE_RUN=false.
- **Problema seleccionado**: P1 (captura/agenda errónea + título sucio). La forma coloquial COMPACTA (sin conector) **"hoy tarde"/"hoy noche"/"mañana tarde"/"mañana noche"/"pasado mañana tarde"** —abreviatura de "hoy en la tarde"/"mañana por la noche", común al escribir rápido en móvil— NO se reconocía: el marcador de día ("hoy"/"mañana") fijaba la fecha, pero la parte del día ("tarde"/"noche") NO casaba ningún patrón → la hora caía al default **09:00** (una tarea "hoy noche" se vencía a las 09:00 de hoy, no 21:00) Y "tarde"/"noche" quedaba como residuo en el título ("comprar pan hoy noche" → título "comprar pan hoy noche"). Asimetría flagrante: las formas CON conector ("hoy en la tarde", c.58) SÍ funcionaban (15:00/21:00 + título limpio); la compacta no. Doble defecto: agenda errónea (mañana vs noche) + título corrupto.
- **Prioridad**: P1 (agenda errónea: la persona creía agendar algo "hoy noche" y recibía 09:00 + título sucio).
- **Causa raíz**: `standalonePartOfDayPattern` (c.58) exige conector (`a la`/`de la`/`por la`/`en la`); la forma compacta "hoy tarde" no tiene conector, así que "tarde"/"noche" suelto tras un marcador de día no se interpretaba como hora canónica ni se limpiaba del título.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**: nuevo `compactDayPartOfDayPattern = (?i)\b(?:antepasad[oa]\s+mañana|pasado\s+mañana|mañana|hoy)\s+(tarde|noche|madrugada)\b` + `compactDayPartOfDayTimes` (tarde→15:00, noche→21:00, madrugada→04:00). El marcador de día se captura sólo para anclar la parte del día a una referencia temporal (evitar robar "tarde"/"noche" sueltas de otras construcciones); la fecha la resuelve el `when` existente ("hoy"→hoy, "mañana"→+1, "pasado mañana"→+2, "antepasado mañana"→+3). Se cablea: (1) match + `compactDayPartOfDayTime` en la cadena `parsedTime` (después de `standalonePartOfDayTime`); (2) `compactDayPartOfDayKey` añadido a `hasPartOfDayPmContext` (tarde/noche → contexto PM para "hoy tarde a las 4"→16:00); (3) limpieza del título ANTES del borrado genérico "mañana"/"hoy" (mismo orden que `standalonePartOfDayPattern`). **Se EXCLUYE "mañana" como parte del día compacta** (sólo tarde/noche/madrugada): "mañana" es ambigua (día vs parte del día) y la forma "hoy mañana"/"mañana mañana" es rara y propensa a fechar mal (choca con `mananaAsDate`); la forma con conector ("hoy en la mañana") ya funciona vía `standalonePartOfDayPattern`. "madrugada" sí se incluye (inequívoca). Lógica local honesta (canónica de hora, sin random ni IA falsa). Retrocompatible.
- **Tests**: +7 en `NaturalTaskParserTest.kt`: `hoyTardeEs15hYLimpiaTitulo`, `hoyNocheEs21hYLimpiaTitulo`, `mananaTardeEsManana15hYLimpiaTitulo`, `mananaNocheEsManana21hYLimpiaTitulo`, `pasadoMananaTardeEsPasadoManana15hYLimpiaTitulo`, `compactTardeConHoraSinMeridiemAplicaPm` ("hoy tarde a las 4"→16:00), `hoyMadrugadaEs4hYLimpiaTitulo`. Tras el fix: `bash tools/run_domain_tests.sh` → **740 PASS** (28 clases — 733 base c.108 + 7 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio (9 casos): "comprar pan hoy tarde"→15:00 hoy título "comprar pan"; "llamar a mamá hoy noche"→21:00 hoy; "reunión mañana tarde"→15:00 +1d; "hoy tarde a las 4"→16:00; "hoy noche a las 9"→21:00; "mañana noche a las 8"→20:00 +1d; "cita pasado mañana tarde"→15:00 +2d; todos títulos limpios. Sin regresión ("hoy en la tarde"=15:00, "esta tarde"=15:00, "esta noche"=21:00, "hoy en la mañana"=09:00 intactos).
- **Bugs**: 1 (P1, parte del día compacta → 09:00 + residuo en título; FIXED).
- **Features**: 0 (cierre de asimetría con conector vs compacto, sin nueva pantalla).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: el USER_CONTEXT listaba frases aún sin parsear: "cuando puedas", "en cualquier momento", "sin prisa", "tan pronto como sea posible" (todas `due=null`). Ninguna fija una hora útil ("cuando puedas"/"sin prisa" = sin vencimiento real, mejor dejar `null` que inventar). "tan pronto como sea posible" sí es candidato (≈ "lo antes posible" = now, ya cubierto c.107) pero su forma larga no casa el patrón `nowPattern` — se deja para próxima run si se confirma necesidad (anti-feature-bloat: no simular vencimiento para frases sin compromiso temporal). Próxima prioridad: seguir en parser ("ya" como token final → now, P1 — "ya" es la palabra más común de "ahora mismo") o salir del parser hacia recuperación de tareas olvidadas / contexto.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Último trabajo — Ciclo 109" + contador 740 tests), `BACKLOG.md` (fila c.109 FIXED→VERIFIED), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (a rellenar tras push).
- **HEAD final**: (a rellenar tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM). 740 domain tests PASS (733 base c.108 + 7), smoke 25 OK.
- **Próxima prioridad**: parser "ya" como token final → now (P1); luego salir del parser hacia recuperación de tareas olvidadas / contexto / onboarding.

---
## Ciclo 102 - 2026-08-14 (UTC) - fix(parser): "a última hora" (fin de jornada) no se interpretaba como hora → agenda errónea 09:00 + título sucio; `\b` ASCII fallaba con "ú" acentuada (P1 captura/agenda)

- **Run/ciclo**: 102 (rama `openhands/autonomous-ordia`). Base inicialmente limpia: HEAD inicial `c359003` (c.99 remoto final). Esta run partió de un USER_CONTEXT con el fix PARCIAL ya aplicado (7 tests + `ultimaHoraPattern`/`ultimaHoraTime` + match/resolución/limpieza + fix mediodía/medianoche, GREEN 699), pero con un bug residual: el `\b` inicial ASCII-only no casaba "última hora" (con tilde, sin "a"). La run completó el fix: añadió el test RED que expone el bug del `\b`, reemplazó `\b` por lookbehind Unicode-safe `(?<![a-záéíóúñ])`, verificó con probe amplio (9 casos + anti-falso-positivo), corrió la suite completa, actualizó memoria y commiteó (commit `f4cf217`). Al hacer `git pull --rebase` el remoto había avanzado 2 commits por un run paralelo (c.100 "cada fin de semana" + c.101 "media hora y cuarto"); el código del parser se auto-mmergió sin conflicto; solo `CURRENT_STATE.md` (memoria) conflictuó y se resolvió tomando la versión remota + re-aplicando mi sección renumerada a c.102. Mi entrada se renumeró de c.100→c.102 para no colisionar con los ciclos del run paralelo.
- **HEAD inicial**: `c359003` (c.99; local == remoto al iniciar, sin divergencia).
- **Problema seleccionado**: P1 (captura/agenda errónea + título sucio). La forma cotidiana **"a última hora"** (fin de jornada, ~18:00) NO se interpretaba como hora canónica: sin patrón específico, la hora caía al default **09:00** (mañana en vez de tarde) y el conector "a última hora" quedaba como residuo en el título → la persona decía "reunión a última hora" y recibía agenda a las 9 de la mañana + título sucio. Asimetría flagrante con **"a primera hora"** (09:00) que SÍ tenía patrón. Además **"última hora" sin conector "a"** (con tilde en la ú) NO casaba: el `\b` inicial (ASCII-only, `\w` = `[a-zA-Z0-9_]`) no reconoce "ú" como carácter de palabra → `dueAt=null` silencioso (la forma sin tilde "ultima hora" sí casaba). Por separado, **"a mediodía"/"a medianoche"** (sin contracción "al"/"a la") dejaban residuo "a" en el título.
- **Prioridad**: P1 (agenda errónea: la persona creía agendar al final del día y recibía una tarea a las 9 de la mañana o SIN vencimiento; el conector no se limpiaba → título incomprensible).
- **Causa raíz (dos defectos)**: (1) ausencia de un patrón canónico de fin de jornada simétrico a `primeraHoraPattern`; (2) el `\b` inicial del patrón nuevo (copiado de `primeraHoraPattern`, que empieza con "p" ASCII y por eso no exhibe el fallo) no casa antes de una letra acentuada "ú" no-ASCII — la asimetría viene de que "primera" no empieza por acento y "última" sí.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**: (1) nuevo `ultimaHoraPattern` `(?i)(?<![a-záéíóúñ])(?:a\s+)?[uú]ltima\s+horas?(?:\s+de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?\b` + `ultimaHoraTime=18:00`, simétrico a `primeraHoraPattern`; el **lookbehind Unicode-safe `(?<![a-záéíóúñ])`** reemplaza `\b` para que "última hora" (con tilde, sin "a") también case (el carácter anterior —espacio/inicio— no es letra → pasa; una "x" antes —"xúltima"— sí es letra → rechazado). Match + resolución de respaldo en la cadena de `parsedTime` (después de `primeraHoraMatch`) + limpieza del título. Si hay parte del día explícita ("a última hora de la tarde"), ésta tiene prioridad (el patrón solo limpia "a última hora"). (2) `mediodía`/`medianoche` patterns aceptan `a\s+` (sin "la") además de `al`/`a la` → limpian el conector. Lógica local honesta (canónica de hora, sin random ni IA falsa). Retrocompatible.
- **Tests (TDD)**: +8 en `NaturalTaskParserTest.kt`: `ultimaHoraSinFechaUsaHoy`, `ultimaHoraConFechaRelativaCombinaBien`, `ultimaHoraDeLaNocheRespetaCanonicaNoche`, `ultimaHoraInterpretaFinJornadaYLimpiaTitulo`, `ultimaHoraConParteDelDiaEspecificaRespetaEsaHora`, `ultimaHoraSinConectorATambienFunciona` (expone el bug del `\b` ASCII → RED antes del fix), `aMediodiaSinContraccionLimpiaTitulo`, `aMedianocheSinContraccionLimpiaTitulo`. El nuevo test `ultimaHoraSinConectorATambienFunciona` falló antes del fix del lookbehind (confirmación RED). Tras el fix: `bash tools/run_domain_tests.sh` → **705 PASS** (28 clases — 697 base c.101 + 8 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio (9 casos): "a última hora"→18:00 (con/sin fecha, relativa), "última hora" sin "a"→18:00 (antes null), "a última hora de la tarde/noche/madrugada/mañana" respeta la parte del día, todos los títulos limpios; `xúltima` rechazado (anti-falso-positivo verificado). Sin regresión ("a primera hora" intacto, "al mediodía"/"a la medianoche" intactos).
- **Bugs**: 1 (P1, "a última hora" → agenda 09:00/null + título sucio; "última hora" con tilde sin "a" → null por `\b` ASCII; FIXED).
- **Features**: 0 (cierre de asimetría primera/última hora + conector mediodía/medianoche).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: el defecto `\b`-ASCII-vs-acento ya apareció en c.91 ("último día del mes", resuelto con `(?<!\p{L})`) y ahora reaparece aquí. Es una **familia recurrente**: cualquier patrón que use `\b` antes de una palabra que empiece con acento (ú, á, í...) fallará en Java. Auditoría pendiente: escanear todos los `\b` del parser que precedan a clases de caracteres que incluyan acentos y reemplazarlos por lookbehind Unicode-safe. Próxima auditoría: salir del parser de horas canónicas y mirar detección de compromisos en notas, `RecurrenceEngine` edge cases (DST/clamps), replanificación si OVERLOADED recurrente, captura/búsqueda/What Now.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.102 + nueva sección "Último trabajo — Ciclo 102"), `BACKLOG.md` (fila c.102 FIXED→VERIFIED), `RUN_LOG.md` (esta entrada, renumerada c.100→c.102 tras colisión con run paralelo).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (a rellenar tras push).
- **HEAD final**: (a rellenar tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM). 705 domain tests PASS (697 base c.101 + 8), smoke 25 OK.
- **Próxima prioridad**: descubrimiento continuo — auditoría de la familia `\b`-ASCII-vs-acento en el parser; luego salir del parser y mirar detección de compromisos en notas, `RecurrenceEngine` edge cases (DST/clamps), replanificación si OVERLOADED recurrente, captura/búsqueda/What Now.

---
## Ciclo 99 - 2026-08-14 (UTC) - fix(parser): números escritos > 30 y compuestos ("cuarenta y cinco minutos") → tarea SIN vencimiento (P1 captura/agenda olvidada)

- **Run/ciclo**: 99 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial `d01a649` (c.97 remoto, "docs(autonomy): registro HEAD final ciclo 97"). `git status` limpio, rama `openhands/autonomous-ordia` == `origin/openhands/autonomous-ordia`. Esta run partió de un USER_CONTEXT con el fix ya aplicado (TDD: probe manual de 8 casos RED → implementación → GREEN) pero sin suite completa ni commit. La run completó: añadió 7 tests permanentes, corrigió una aserción débil, corrió la suite completa, actualizó memoria y commiteó. Sin STALE_RUN (al push el remoto había avanzado a `5043282` por un run paralelo c.98 "el día N"; se hizo `git pull --rebase` no destructivo, se resolvieron 3 conflictos de memoria —BACKLOG/CURRENT_STATE/RUN_LOG— conservando ambas entradas y renumerando la mía a c.99 para evitar colisión; el código se auto-mergió sin conflicto).
- **HEAD inicial**: `d01a649` (c.97; local == remoto, sin divergencia).
- **Problema seleccionado**: P1 (captura/agenda olvidada). Las cantidades escritas > 30 y la forma compuesta estándar del español no se reconocían, así que la tarea nacía **sin `dueAt`**. `"llamar en cuarenta y cinco minutos"`, `"en cincuenta minutos"`, `"en sesenta minutos"`, `"en veinticinco minutos"`, `"en treinta y cinco minutos"` caían a `dueAt=null` (y a veces `durationMinutes=5` por la unidad baja "cinco" que rezumaba como duración espuria). `"trabajar cuarenta y cinco minutos"` perdía la duración (dur=null). `"vuelo recuérdame cuarenta y cinco minutos antes"` perdía el offset de recordatorio. La persona decía "cuarenta y cinco minutos" y Ordía agendaba sin fecha → invisible en What Now/planificador, sin recordatorio posible. Asimetría flagrante con dígitos ("en 45 minutos" sí funcionaba desde siempre).
- **Prioridad**: P1 (tarea olvidada: el vencimiento que el usuario creía configurar NO se almacenaba — el recordatorio jamás disparaba).
- **Causa raíz (duplicación + acotamiento)**: las listas de palabras admitidas estaban acotadas a **1-30** y **duplicadas** en 6 patrones (`relativePattern`, `compoundFractionalRelativePattern`, `multiQuarterRelativePattern`, `agoPattern`, `writtenAmountPattern` —compartido por `reminderPatterns`/`writtenDurationPattern`— y un `writtenNumberGroup` local de `parseRecurrence`). Además `parseWrittenNumber` solo mapeaba 1-21 y 30. Así "cuarenta"/"cincuenta"/"sesenta" no casaban, y la forma compuesta "cuarenta y cinco" tampoco (la unidad baja "cinco" rezumaba a veces como duración espuria). El bug podía manifestarse en un patrón y no en otro por la duplicación — un mismo defecto esparcido en 6 sitios.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - Un **ÚNICO** `writtenNumberGroup` (fragmento regex, 1-99) declarado al inicio del `object` y reutilizado por TODOS los patrones que antes duplicaban la lista. Cubre: formas compuestas `(decena) y (unidad)` (31-99, p.ej. "treinta y cinco") y `veinte y (unidad)` (21-29), puestas **ANTES** que las decenas sueltas para que "cuarenta y cinco" case entero (no solo "cuarenta"); las palabras únicas veintidós…veintinueve (+ sin tilde) y veintiuno; decenas sueltas treinta…noventa y unidades un…nueve. Se **elimina** la lista duplicada de `parseRecurrence` (ahora usa la propiedad compartida). Menos código + consistencia: el bug **no puede reaparecer** en un patrón olvidado.
  - `parseWrittenNumber` reescrito con un `wordToNumber` map (1-99) + resolución del compuesto "decena y unidad" (left ∈ decenas redondas {20,30,…,90}, right ∈ 1..9 → l+r). Mantiene dígitos y "un par de"→2.
  - Simétrico a los dígitos: "en cincuenta minutos" == "en 50 minutos". Lógica local honesta (mapeo de token + aritmética de decena, sin random ni IA falsa). Retrocompatible (sin cambios de firma pública).
- **Tests**: +7 en `NaturalTaskParserTest.kt` (TDD: probe JVM RED de 8 casos → 7 tests permanentes): `writtenCompoundNumberRelativeParsesDueAt` ("Llamar en cuarenta y cinco minutos" → +45min), `writtenTensRelativeParsesDueAt` (cincuenta/sesenta/noventa → +50/+60/+90min), `writtenCompoundNoLeakLowUnitAsDuration` ("Llamar en cuarenta y cinco minutos con Juan" → título "Llamar con Juan", dueAt +45min, dur null — la unidad "cinco" NO rezuma), `writtenTwentiesSingleWordParsesDueAt` (veinticinco → +25min), `writtenCompoundTensParsesDueAt` (treinta y cinco/setenta y cinco → +35/+75min), `writtenCompoundDurationParsesMinutes` ("Trabajar cuarenta y cinco minutos" → dur 45), `writtenTensReminderParsesOffset` ("recuérdame treinta/cuarenta y cinco minutos antes" → offset 30/45). `bash tools/run_domain_tests.sh` → **682 PASS** (28 clases — 675 c.97 + 7 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Sin regresión (dígitos intactos, "en 45 minutos" sigue +45min, "un par de" sigue 2).
- **Bugs**: 1 (P1, números escritos > 30 y compuestos → dueAt/duración/offset null; FIXED).
- **Features**: 0 (cierre de asimetría palabra-vs-dígito en cantidades > 30; reduce código por eliminación de duplicación).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: este fix cierra la familia de "números escritos en cantidades" que comenzó en c.29 (relativas 1-12), c.35 ("un par de"), c.40 (recordatorios), c.57 (intervalos de recurrencia), c.85 (duraciones), c.90 (horas), c.97 (intervalo+días). El defecto estructural era la **duplicación** de la lista de literales en cada patrón nuevo; el patrón de fij "un único fragmento regex compartido" previene reapariciones. Quedan bordes: números > 99 escritos ("ciento veinticinco minutos", raro — evaluar demanda real antes), "cada rato"/"de vez en cuando" (recurrencia vaga, requiere anti-falso-positivo). Próxima auditoría debe salir del parser de números y mirar: detección de compromisos en notas, `RecurrenceEngine` edge cases (DST, clamps), replanificación si OVERLOADED recurrente, captura/búsqueda/What Now.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.99 + nueva sección "Último trabajo — Ciclo 99"), `BACKLOG.md` (fila c.99 FIXED→VERIFIED al frente de Pendientes), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: 2 (`064199e` fix(parser): números escritos >30 y compuestos; `de37215` docs(autonomy): HEAD final c.99).
- **HEAD final**: `de37215` (docs HEAD final c.99), sobre `064199e` (fix), sobre `b20a9e6` (c.100 paralelo "este finde"), sobre `5043282` (c.98 paralelo "el día N"). Tras dos rebases no destructivos por colisión con runs paralelos (c.98 y c.100); el código se auto-mergió sin conflicto; solo memoria requirió resolución manual (conservando todas las entradas).
- **Estado**: FIXED → VERIFIED (dominio JVM). 692 domain tests PASS (688 c.99 + 4 c.100), smoke 25 OK.
- **Próxima prioridad**: descubrimiento continuo — salir del parser de números; detección de compromisos en notas, `RecurrenceEngine` edge cases (DST/clamps), replanificación si OVERLOADED recurrente, auditoría de captura/búsqueda/What Now para nuevas oportunidades de producto.

## Ciclo 98 - 2026-08-14 (UTC) - fix(parser): "el día N" → día de mes resuelto + título limpio (P1 cita olvidada)

- **Run/ciclo**: 98 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial local `d01a649` (c.97 remoto, docs). `git fetch origin openhands/autonomous-ordia` + `git pull --ff-only` OK sin divergencia (local == remoto). Continúa la auditoría de `NaturalTaskParser` (captura de fechas puntuales / días de mes sueltos) — esta run partió de un USER_CONTEXT con fix PARCIAL ya aplicado (`dayOfMonthPattern`/`nextMonthDayPattern`/`monthlyDayPattern` ya con `(?:d[ií]a\s+)?`, `monthNamePattern` pendiente) y probe de verificación. La run completó el fix: añade el mismo `(?:d[ií]a\s+)?` a `monthNamePattern`, fija las fechas esperadas de los tests (el `now` del test es 29-jul, no 12-ago), y añade 6 tests de regresión. Sin STALE_RUN.
- **HEAD inicial**: `d01a649` (c.97; local == remoto, sin divergencia).
- **Problema seleccionado**: P1 (integridad de captura / cita olvidada). `"el día N"` (la forma más cotidiana en español de fijar un día de mes suelto: "reunión el día 30", "pago el día 15 de cada mes", "reunión el día 1 de enero") NO se parseaba: `dayOfMonthPattern` solo casaba "el N" (sin la palabra "día"), así que "el día 30" caía a `dueAt=null` (pérdida silenciosa de la cita — sin recordatorio, invisible en What Now/planificador). Peor aún: en "el día 15 de cada mes" / "el día 1 de enero", las ramas `monthlyDayPattern`/`monthNamePattern` SÍ capturaban la fecha pero NO consumían "el día" → título basura "reunión el día". Asimetría flagrante: "el 30" funcionaba (c.68/c.70), "el día 30" no.
- **Prioridad**: P1 (cita olvidada o con título corrupto en una forma cotidiana de captura rápida móvil; el usuario creía agendar y la cita nacía sin vencimiento o con título incomprensible).
- **Causa raíz**: cuatro patrones (`dayOfMonthPattern`, `nextMonthDayPattern`, `monthlyDayPattern`, `monthNamePattern`) que capturan un día numérico del mes solo lo esperaban precedido del artículo "el", NO de la palabra "día" ("el día 30" = "el" + "día" + "30"). Sin consumir "día", el grupo de día no casaba → `dueAt=null`; y cuando otra rama capturaba la fecha sin consumir "día", esta palabra quedaba como residuo en el título.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**: los cuatro patrones añaden `(?:d[ií]a\s+)?` opcional antes del grupo de día (casa con y sin tilde, límites `\b` intactos). Reusa TODO el flujo existente: resolución de fecha (hoy/próximo mes con clamp al último día válido), combinación con hora explícita, recurrencia mensual, roll si el día ya pasó. Así "el día N" resuelve a la misma fecha que "el N" y la frase completa se consume → título limpio. Lógica local honesta (mapeo de token, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública). La forma sin "día" sigue funcionando idéntica (no-regresión: "reunión el 30" → 30/07 OK).
- **Tests (TDD)**: +6 en `NaturalTaskParserTest.kt`: `parsesElDiaStandaloneDayOfMonth` ("Reunión el día 30" → 30/07 + título "Reunión"), `parsesElDiaWithoutTilde` ("Reunión el dia 3" → 03/08, sin tilde), `elDiaNextMonthResolvesToNextMonth` ("Entregar el día 1 del mes que viene" → 01/08), `elDiaWithExplicitHour` ("Reunión el día 15 a las 10" → 15/08 10:00), `elDiaMonthlyRecurrenceCleanTitle` ("Pago el día 15 de cada mes" → MONTHLY + 15/08 + título "Pago"), `elDiaMonthNameCleanTitle` ("Reunión el día 1 de enero" → 01/01/2027 + título "Reunión"). Los 3 primeros fallaron antes de corregir las fechas esperadas (el `now` del fixture es 29-jul, no 12-ago: el 30 todavía no llegó → 30/07, el 3 ya pasó → 03/08, "mes que viene"=agosto → 01/08). `bash tools/run_domain_tests.sh` → **681 PASS** (28 clases — 675 base c.97 + 6 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio (28 casos) confirmó sin regresión: "el día 30"/"el dia 3"/"el día 1 del mes que viene"/"el día 15 a las 10"/"el día 15 de cada mes"/"el día 1 de enero" todos verdes; las formas sin "día" ("el 30"/"el 15 del mes que viene"/"el 15 de cada mes"/"el 15 de agosto") intactas.
- **Bugs**: 1 (P1, "el día N" → dueAt=null o título "reunión el día"; FIXED).
- **Features**: 0 (cierre de caso límite de día de mes suelto con la palabra "día").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: este era el último hoyo de la familia "día de mes suelto" (c.70 cubrió "el N del mes que viene", c.68 "el N" standalone). La asimetría palabra-artículo ("el" vs "el día") era estructural: CUALQUIER patrón que capture un día numérico del mes heredaba el mismo bug si no admitía "día"; el patrón de fij es "añadir `(?:d[ií]a\s+)?` opcional al frente del grupo de día" de forma uniforme. Queda abierto (P2 baja): "el día quince" (número ESCRITO, no dígito) → `dayOfMonthPattern` solo captura `\d{1,2}`; forma menos común en móvil (la gente teclea dígitos), pero registrar para futuro. Próxima auditoría debe salir del parser de días de mes y mirar otra área de producto: captura ultrarrápida/inbox inteligente, recordatorios, rutinas adaptables, What Now, o `RecurrenceEngine` edge cases (saltos DST, clamps fin-de-mes, anclaje a día inexistente).
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.98 + nueva sección "Último trabajo — Ciclo 98"), `BACKLOG.md` (fila c.98 FIXED→VERIFIED al frente de Pendientes), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: 1 (`fix(parser): "el día N" → día de mes resuelto + título limpio (P1)`).
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `d01a649` c.97).
- **Estado**: FIXED → VERIFIED (dominio JVM). 681 domain tests PASS, smoke 25 OK.
- **Próxima prioridad**: salir del parser de días de mes (familia cerrada) y auditar otra área de producto de mayor impacto: captura ultrarrápida/inbox inteligente, recordatorios, rutinas adaptables, What Now, o `RecurrenceEngine` edge cases. Descubrimiento continuo de oportunidades reales.

---
## Ciclo 97 - 2026-08-14 (UTC) - fix(parser): "cada dos semanas los lunes" (intervalo escrito + días) → WEEKLY+2 (P1 rutina mal programada)

- **Run/ciclo**: 97 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial local `fe13527` (c.96 remoto). `git fetch origin openhands/autonomous-ordia` + `git pull --ff-only` OK sin divergencia (local == remoto). Continúa la auditoría de `NaturalTaskParser` (recurrencia / rutinas) — esta run partió de un USER_CONTEXT con TDD RED ya escrito (4 tests failing) y fix parcial aplicado que no compilaba (`writtenNumberGroup` fuera de scope). La run completó el fix: reubica el `val` compartido al inicio de `parseRecurrence`. Sin STALE_RUN.
- **HEAD inicial**: `fe13527` (c.96; local == remoto, sin divergencia).
- **Problema seleccionado**: P1 (integridad de agenda / rutina mal programada). `"cada dos semanas los lunes"` (intervalo ESCRITO + lista de días) se agendaba al **DOBLE de frecuencia** (WEEKLY+1, cadencia semanal) en vez de WEEKLY+2 (quincenal), Y dejaba "cada dos semanas" como residuo en el título. La persona pedía una rutina quincenal y recibía una **semanal**: los recordatorios disparaban el doble de veces, la planificación de hábitos mostraba la cadencia errónea. Asimetría flagrante: la forma con **dígitos** `"cada 2 semanas los lunes"` ya funcionaba (c.54, `detectWeekInterval()` casa `\d{1,3}`) y el intervalo escrito **sin días** `"cada dos semanas"` también (c.57, vía `intervalPattern`), pero la combinación escrita+días caía en el bug. Forma cotidiana de captura de rutinas ("Gym cada dos semanas los lunes", "Clase cada tres semanas los martes y jueves").
- **Prioridad**: P1 (rutina mal programada: la cadencia almacenada NO era la que el usuario creía configurar — el plan recurrente se degeneraba al doble de frecuencia).
- **Causa raíz**: `parseRecurrence` procesa la **rama de lista de días** (que admite "cada N semanas los lunes / de lunes a viernes") **ANTES** que `intervalPattern` (intervalo sin días). El helper de esa rama, `detectWeekInterval()`, solo reconocía `\d{1,3}` para "cada N semanas", NO números escritos ("dos"/"tres") → devolvía `null` → interval por defecto **1** (cadencia semanal, el doble de frecuente). Como la rama de días devuelve antes, `intervalPattern` (que SÍ admite números escritos vía `writtenNumberGroup`) nunca se alcanzaba. Además, al no reconocer el intervalo, la frase "cada dos semanas" no se consumía y quedaba como residuo en el título.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `detectWeekInterval()` ahora admite números escritos: la regex pasa de `\bcada\s+(\d{1,3})\s*semanas?\b` a `\bcada\s+(\d{1,3}|$writtenNumberGroup)\s*semanas?\b`, y `parseWrittenNumber` (helper existente) resuelve la palabra a entero (coerceIn 1..366).
  - `writtenNumberGroup` se **trasladó al inicio** de `parseRecurrence` (antes vivía justo encima de `intervalPattern`, que aparece DESPUÉS de `detectWeekInterval` en el cuerpo de la función) para que el helper pueda referenciarlo sin error de scope. Se eliminó la declaración duplicada en su antigua ubicación.
  - Así "cada dos semanas los lunes" → WEEKLY+2 (igual que "cada 2 semanas los lunes") y la frase completa se consume → título limpio. Simétrico al comportamiento de dígitos y al intervalo escrito sin días. Lógica local honesta (mapeo de token, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública).
- **Tests**: +4 en `NaturalTaskParserTest.kt` (TDD RED antes del fix, aportados por el USER_CONTEXT): `writtenBiweeklyIntervalWithWeekdayRangeCombinesIntervalAndDays` ("Gym cada dos semanas de lunes a viernes" → WEEKLY+2 días[1..5] título "Gym"), `writtenBiweeklyIntervalWithDayListCombinesIntervalAndDays` ("Gym cada dos semanas los lunes y viernes" → WEEKLY+2 días[1,5]), `writtenTriweeklyIntervalWithDayListCombinesIntervalAndDays` ("Clase cada tres semanas los martes y jueves" → WEEKLY+3 días[2,4]), `writtenBiweeklyIntervalWithSingleDayCombinesIntervalAndDay` ("Estudio cada dos semanas los lunes" → WEEKLY+2 días[1]). Antes del fix los 4 fallaban con `rec=WEEKLY+1` y residuo " cada dos semanas"/" cada tres semanas" en el título. `bash tools/run_domain_tests.sh` → **675 PASS** (28 clases — 671 base c.96 + 4 nuevos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio confirmó GREEN sin regresión: `"cada dos semanas los lunes"`→WEEKLY+2 días[1], `"cada dos semanas los lunes y viernes"`→WEEKLY+2 días[1,5], `"cada tres semanas los lunes"`→WEEKLY+3 días[1], y `"cada 2 semanas los lunes"` (dígitos) sigue →WEEKLY+2 (no-regresión). Con día base miércoles 2026-07-29, el primer lunes de la cadencia queda 2026-08-03 (correcta próxima ocurrencia del día objetivo).
- **Bugs**: 1 (P1, "cada dos semanas los lunes" → WEEKLY+1 en vez de WEEKLY+2 + título sucio; FIXED).
- **Features**: 0 (cierre de caso límite de recurrencia escrita + días).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: este era el último hoyo de la familia de "intervalo escrito" (c.57 cubrió el intervalo solo, c.54 el intervalo+dígitos, c.97 cierra intervalo-escrito+días). La asimetría palabra-vs-dígito era estructural: cualquier helper de rama específica que re-implementara el parsing de intervalo **sin** reutilizar la alternancia escrita heredaba el mismo bug; el patrón de fij es "unificar la alternancia en un `val` compartido al inicio de `parseRecurrence`". Próxima auditoría debe salir de recurrencias y mirar: `RecurrenceEngine` edge cases (saltos DST, clamps fin-de-mes, anclaje a día inexistente), detección de compromisos en notas, replanificación si OVERLOADED recurrente, captura/búsqueda/What Now.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.97 + nueva sección "Último trabajo — Ciclo 97"), `BACKLOG.md` (fila c.97 FIXED→VERIFIED al frente de Pendientes), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: 1 (`fix(parser): "cada dos semanas los lunes" → WEEKLY+2 (intervalo escrito+días) (P1)`).
- **HEAD final**: `7464431` (fix(parser): "cada dos semanas los lunes" → WEEKLY+2).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo — salir del parser de recurrencias; `RecurrenceEngine` edge cases (saltos DST, clamps fin-de-mes), detección de compromisos en notas, replanificación si OVERLOADED recurrente, auditoría de captura/búsqueda/What Now para nuevas oportunidades de producto.

---
## Ciclo 96 - 2026-08-14 (UTC) - fix(parser): "a la una" (hora 1, femenino singular) + guarda NOON "del mediodía" (P1 captura/agenda)

- **Run/ciclo**: 96 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial local `0a93939` (merge c.94b+c.95 remoto). `git fetch origin openhands/autonomous-ordia` + `git pull --ff-only` OK sin divergencia (local == remoto). Continúa la auditoría de `NaturalTaskParser` (casos límite de hora puntual / meridiem) iniciada en c.94. Sin STALE_RUN.
- **HEAD inicial**: `0a93939` (merge c.94b/c.95; local == remoto, sin divergencia).
- **Problema seleccionado**: P1 (integridad de captura/agenda). La hora **1** en español se dice `"a la una"` (femenino singular, conector "a la"), NO `"a las 1"`. El patrón `a las N` de `timePatterns` excluía deliberadamente `un/una/uno` de `WRITTEN_HOUR_ALT` (para no confundir el numeral con el artículo/determinante), así que `"reunión a la una"` **no casaba ningún patrón de hora** → `dueAt=null` Y la frase "a la una" quedaba como residuo en el título → la cita nacía sin vencimiento: invisible en "What Now"/planificador, recordatorio imposible. Además `"a la una del mediodía"` (forma cotidiana de 1pm) caía a la canónica NOON (12:00) en vez de 13:00: la rama `mv.contains("mediodía")` de `explicitTimeData` cortocircuitaba ANTES de procesar la hora capturada. Forma ultra-común de captura ("reunión a la una", "almuerzo a la una del mediodía").
- **Prioridad**: P1 (cita olvidada / agendada a hora errónea en una forma cotidiana de captura rápida).
- **Causa raíz**: (1) ausencia de patrón para la forma femenino-singular "a la una" (la regex `a las N` exige plural "las" + dígito/hora escrita, y excluye "una" del ALT). (2) La rama NOON/MIDNIGHT de `explicitTimeData` disparaba por presencia de la subcadena "mediodía" en el valor coincidido, **independientemente de que se hubiera capturado una hora** → "a la una del mediodía" se tragaba como NOON (12:00), ignorando el grupo de hora capturado. (3) `isPm` no reconocía `del mediodía` como PM, así aunque la rama genérica corriera, la hora 1 no se convertía a 13.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - **(1)** Nuevo patrón `a la una` al **frente** de `timePatterns` (para ganarle al `a las N` que exige "las"), con el **mismo layout de grupos** que ese (1=hora, 2=:MM, 3=y media|cuarto, 4=meridiem, 5=horas). `parseHour` ya mapea "una"→1 (verificado con probe). Así `explicitTimeData` lo procesa en la rama genérica **sin ramificación nueva**.
  - **(2)** El meridiem `del mediodía` se añade a la alternación de **ambos** patrones (`a la una` y `a las N`), y `isPm` reconoce `delmediodía`/`delmediodia` (PM: hora 1 → 13). `isAm`/`isPm` existentes intactos para las demás formas.
  - **(3)** La rama NOON/MIDNIGHT de `explicitTimeData` se **protege con guarda de grupos vacíos**: solo aplica a frases puras "al mediodía"/"a la medianoche" (patrón sin grupo de hora), i.e. cuando los grupos 1 (hora) y 2 (minutos) están en blanco. Así "a la una del mediodía" (grupo 1 = "una") cae a la rama genérica y resuelve 13:00, no NOON. El corto-circuito NOON por subcadena era un P1 latente para CUALQUIER hora con meridiem "del mediodía" (no solo la 1): por construcción ahora es seguro para toda hora.
  - Lógica local honesta (mapeo de token + offset PM), sin random ni modelo simulado. Retrocompatible (sin cambios de firma pública).
- **Tests**: +6 en `NaturalTaskParserTest.kt`: `aLaUnaParsesOneOclockAndCleanTitle` (01:00 + título "Reunión"), `aLaUnaYMediaParsesHalfPastOne` (01:30), `aLaUnaYCuartoParsesQuarterPastOne` (01:15), `aLaUnaDeLaTardeParsesOnePm` (13:00), `aLaUnaDelMediodiaParsesOnePmNotNoon` (13:00, **NO** 12:00), `aLaUnaColonMinutesParsesCorrectly` (01:30 vía `:MM`). `bash tools/run_domain_tests.sh` → **671 PASS** (28 clases, 665 previos + 6); `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio (7 casos) confirmó sin regresión: `"a la una"`=01:00, `"a la una de la tarde"`=13:00, `"a la una del mediodía"`=13:00 (NO 12:00), `"a la una y media"`=01:30, `"a la una y cuarto"`=01:15, `"a la una:30"`=01:30, y `"al mediodía"` puro sigue = NOON (12:00) intacto.
- **Bugs**: 1 (P1, "a la una" sin vencimiento + título sucio; "a la una del mediodía"=NOON en vez de 13:00 → FIXED).
- **Features**: 0 (cierre de caso límite de hora puntual + meridiem).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: la asimetría "a las N" (plural) vs "a la una" (singular) era la última trampa conocida del género femenino de la hora 1. El corto-circuito NOON por subcadena era un P1 latente que afectaba a cualquier hora con meridiem "del mediodía" (no solo la 1): ahora es seguro para toda hora por construcción. La familia de horas puntuales queda completa (dígitos, escritas, "a la una" singular, meridiems AM/PM/de-la-tarde/noche/mañana/madrugada/del-mediodía, NOON/MIDNIGHT, fracciones y media/cuarto). Próxima auditoría debe salir del parser de horas puntuales y mirar otra área (captura rápida, recordatorios, rutinas, What Now).
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.96 + nueva sección "Último trabajo — Ciclo 96"), `BACKLOG.md` (fila c.96 FIXED→VERIFIED al frente de Pendientes), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: 1 (`fix(parser): "a la una" (hora 1) + "del mediodía" NOON guard (P1)`).
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `0a93939` c.95).
- **Estado**: FIXED → VERIFIED (dominio JVM). 671 domain tests PASS, smoke 25 OK.
- **Próxima prioridad**: salir del parser de horas puntuales (familia cerrada) y auditar otra área de producto de mayor impacto: captura ultrarrápida/inbox inteligente, recordatorios, rutinas, o What Now. Descubrimiento continuo de oportunidades reales.

---
## Ciclo 82 - 2026-08-13 (UTC) - fix(parser): meridiem solo en el INICIO del rango "de 6pm a 8"/"de 2pm a 4"/"de 6 de la tarde a 8" (fin bare no heredaba PM → rango rechazado, dur null + título sucio)

- **Run/ciclo**: 82 (rama `openhands/autonomous-ordia`; renumerado desde 81 por colisión: durante este run el remoto avanzó en paralelo con `1d9fdf0 feat(search): búsqueda semántica por fecha` (c.81, ortogonal: toca `SearchEngine.kt`, este run toca `NaturalTaskParser.kt`)). Base inicial `36915ab` (c.80). Reconciliación **no destructiva**: `git fetch` + `git rebase origin/openhands/autonomous-ordia`; el código mergeó limpio (archivos distintos), conflictos solo en docs `AI_AUTONOMY` (nomenclatura de ciclo colisionaba), resueltos conservando AMBOS runs y renumerando este a **ciclo 82**. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `36915ab` (c.80 remoto, local == remoto, sin STALE).
- **Problema seleccionado**: P1 (integridad de captura/agenda). La familia de rangos horarios estaba casi cerrada (c.76 fin→inicio, c.79 cruce del mediodía, c.80 cruce de medianoche), pero faltaba la **dirección simétrica que el c.80 no cubrió**: el meridiem PM **solo en el INICIO** con fin bare. Un probe JVM (13 casos) sobre `970d919` (c.80) confirmó: "Reunión de 6pm a 8" → `durationMinutes=840` (incorrecto, por fallback del canónico de parte del día) en base limpia c.80; debía ser 120. El fin bare (8) no resolvía a 20:00 porque **no heredaba el PM del inicio** → `endHr=8 < startHr=18` violaba la guarda `endMin > startMin` → bloque rechazado, dur null **Y** título sucio ("Reunión de a 8"). Un compromiso agendado a la hora correcta pero con duración absurda (14h) o sin duración y con título corrupto.
- **Prioridad**: P1 (duración/dueAt/título erróneos en una forma cotidiana de captura: "de 6pm a 8", "de 2pm a 4", "de 6 de la tarde a 8"; la asimetría fin→inicio del c.76 dejaba la mitad de los casos rotos).
- **Causa raíz**: la propagación de meridiem era **asimétrica**. El c.76 propagaba el PM del extremo **final** al inicio bare, pero **nunca** el del **inicio** al fin bare. Así "de 6 a 8 de la tarde" (PM al final) funcionaba, pero "de 6pm a 8" (PM al inicio) rompía: el fin 8 quedaba como 08:00 < 18:00 → rango invalidado.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `startPmEffective = startPm || (startMer.isEmpty() && endPm && startH <= endH)`: preserva el c.76 (fin→inicio) sin cambios.
  - `endPmEffective = endPm || (endMer.isEmpty() && startPmEffective && endH > 0 && endH <= startH)`: el fin bare hereda el PM del inicio **solo cuando están en el mismo lado del mediodía** (fin ≤ inicio).
  - `midnightWrap = (startPmEffective && !endPmEffective && endH < startH && !followedByCount)`: cruce de medianoche inverso ("de 11pm a 1") — inicio PM, fin bare con `endHr < startHr` y **no** seguido de un sustantivo de cantidad — envuelve el fin a +24h → 23:00→01:00, dur 120. **Reemplaza el wrap ad hoc del c.80** con una condición unificada que cubre mismo-día, medianoche y el simétrico cruce de mediodía en un solo bloque `rangeMatch`.
  - **Anti-falso-positivo** `followedByCount`: si el fin bare va seguido de un sustantivo de cantidad ("entradas"/"horas"/"cajas") → NO se propaga PM ni se envuelve ("de 2pm a 4 entradas" es una compra, no un rango) → dur=null como antes.
  - Lógica local honesta (aritmética de minutos + ajuste de 24h, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública).
- **Tests**: +12 en `NaturalTaskParserTest.kt` (PM-al-inicio + cruce inverso + anti-falso-positivo): `rangeWithLeadingCompactPmPropagatesToEnd`=18:00/120, `…AndTrailingText…`=18:00/120, `…AndMinutes…`=14:30/120, `rangeWithLeadingDeLaTardePropagatesToEnd`=18:00/120, `rangeWithLeadingPmAndCountNounIsRejected`=null, `rangeWithLeadingAmPropagatesToEnd`=08:00/240, `rangeWithLeadingPmCrossingMidnightWraps`=23:00/120, `…WithDeLaMadrugadaWraps…`=23:00/120, `rangeWithBothPmDescendingNotWrapped`=null, + tests de `de`-conector preservados. `bash tools/run_domain_tests.sh` → **593 PASS** (26 clases), `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM amplio (13 casos) confirmó sin regresión: c.80 overnight intacto ("de 10 de la noche a 1 de la madrugada"=22:00/180, "de 9 de la noche a 2 de la madrugada"=21:00/300), c.79 mediodía intacto ("de 12 a 2 de la tarde"=12:00/120, "de 11 a 1 de la tarde"=11:00/120), c.76 intacto ("de 6 a 8 de la tarde"=18:00/120), c.78 intacto ("de 6 a 8 pm"=18:00/120), standalone "Reunión 8pm"=20:00 sin duración. **Beneficio adicional**: "de 10:30 de la noche a 1:15 de la madrugada" (overnight con minutos, "próxima prioridad" del c.80) ahora resuelve 22:30/165 sin código extra.
- **Bugs**: 1 (P1, PM-al-inicio fin bare no heredaba PM → dur null/840 + título sucio → FIXED).
- **Features**: 0 (unificación de modelo; el overnight-con-minutos emerge sin código extra).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: la familia de rangos horarios queda ahora **simétrica y completa** (mismo-día, cruce del mediodía, cruce de medianoche, propagación en ambas direcciones). Próxima auditoría debe salir del parser de rangos y mirar otra área funcional (captura rápida, What Now, rutinas).
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.82 + nueva sección "Último trabajo — Ciclo 82"), `BACKLOG.md` (fila c.82 FIXED→VERIFIED), `RUN_LOG.md` (esta entrada).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: 1 (`fix(parser): propaga meridiem PM del inicio al fin bare del rango "de 6pm a 8"`).
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `1d9fdf0` c.81 search del remoto).
- **Estado**: FIXED → VERIFIED (dominio JVM). 593 domain tests PASS. (Reconciliación no destructiva con run paralelo c.81 search: código ortogonal, ambos conservados.)
- **Próxima prioridad**: salir del parser de rangos (familia cerrada) y auditar otra área de producto de mayor impacto: captura ultrarrápida/inbox inteligente, o What Now. Descubrimiento continuo de oportunidades reales.

---
## Ciclo 81 - 2026-08-13 (UTC) - feat(search): búsqueda semántica por fecha ("hoy", "mañana", "esta semana", "atrasadas/vencidas")

- **Run/ciclo**: 81 (rama `openhands/autonomous-ordia`; renumerado desde 79 por colisión: el remoto avanzó en paralelo con c.79 parser noon-crossing y c.80 parser midnight-crossing, ambos ortogonales al `SearchEngine`). Base inicial `0a21431` (c.78); durante el run el remoto avanzó a `36915ab` (c.80). Reconciliación **no destructiva**: `git fetch` + `git rebase origin/openhands/autonomous-ordia`; conflictos solo en docs `AI_AUTONOMY` (nomenclatura de ciclo colisionaba), resueltos conservando AMBOS runs y renumerando este a **ciclo 81**. Código ortogonal (c.79/c.80 tocan `NaturalTaskParser.kt`; este run toca `SearchEngine.kt`) — sin conflicto de código. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `0a21431` (c.78; base reconciliada a `36915ab` c.80 antes de commitear).
- **Problema seleccionado**: auditoría de la búsqueda universal (`SearchEngine.kt`) reveló que la búsqueda por **intención de fecha** no existía: escribir "hoy", "mañana" o "esta semana" devolvía **vacío** (la palabra no aparecía en el título/detalle de la tarea), y "atrasadas"/"vencidas" solo funcionaba parcialmente (vía `vencid` en `normalized` que activa `TaskRules.isOverdue`, pero no como filtro de fecha coherente). Así un usuario que busca "hoy" para ver qué vence hoy **no ve nada** aunque tenga tareas con `dueAt` en el día. Área de dirección explícita "búsqueda universal"/"recuperación de información importante". P2 funcional de alto impacto (recupera información; convierte la lista mental en una búsqueda accionable; sin nueva pantalla).
- **Prioridad**: P2 (búsqueda/recuperación; no era pérdida de datos ni crash, pero una capacidad esperable que faltaba y rompía la promesa de "búsqueda universal").
- **Causa raíz**: `SearchEngine.search` solo comparaba texto (`haystack.contains(normalized)` / `words.all(haystack::contains)`). No interpretaba la intención temporal del query. Para "hoy"/"mañana"/"esta semana" las palabras no están en el contenido → `matches()` era false → la tarea se excluía aunque su `dueAt` cumpliera el rango. Solo "vencid" tenía un atajo especial (`!normalized.contains("vencid") || TaskRules.isOverdue(...)`), pero incompleto e inconsistente.
- **Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `SearchEngine` ahora detecta un `DateScope` (TODAY/TOMORROW/THIS_WEEK/OVERDUE) a partir de los tokens del query (`hoy`, `manana`, `semana`, `atrasada/os`/`vencida/os`). Cuando hay scope, las **palabras de fecha (y modificadores "esta"/"el"/…) no se exigen en el contenido**: el filtro principal pasa a ser por `dueAt` (`taskMatchesDateScope`), y el texto opcional filtra además dentro de ese rango ("hoy reunion" → solo las de hoy que contengan "reunión").
  - `taskMatchesDateScope`: TODAY = `dueAt` cae hoy; TOMORROW = mañana; THIS_WEEK = desde hoy hasta el domingo ISO (lun→dom) inclusive, excluyendo atrasadas; OVERDUE = `TaskRules.isOverdue`. Los scopes futuros/hoy excluyen tareas completadas/canceladas (consistentes con `isOverdue`/`isDueToday` que ya lo hacen), para que la búsqueda sea accionable. Sin scope → comportamiento idéntico al anterior (no-regresión).
  - Heurística local honesta (aritmética de `LocalDate`/`ZoneId.systemDefault()`, sin random ni modelo simulado). El orden de resultados preserva el `urgencyRank` existente (atrasada-urgente primero).
- **Tests**: +9 en `SearchEngineDateScopeTest.kt` (`hoy_returnsOnlyTasksDueToday`, `manana_returnsOnlyTasksDueTomorrow`, `estaSemana_returnsTasksFromTodayToEndOfWeek` jueves 2026-08-13 → dom 16, `atrasadas_returnsOnlyOverdueTasks`, `vencidas_returnsOnlyOverdueTasks`, `completedTasksAreExcludedFromDateScopes`, `dateScopeCombinedWithTextFiltersByBoth` "hoy reunion", `archivedTasksExcludedFromDateScopes`, `overdueScopeRanksUrgentOverdueFirst`). **597 domain tests PASS** (`bash tools/run_domain_tests.sh` tras rebase — 588 c.80 + 9), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión: los tests `SearchEngineTest` previos intactos (queries sin intención de fecha usan la rama sin scope → comportamiento idéntico).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real de la búsqueda en la app no probado en dispositivo. La zona horaria usa `ZoneId.systemDefault()` (igual que `DateRules.toLocalDate`).
- **Hallazgos adicionales (descubrimiento continuo)**:
  - La búsqueda por fecha cubre los rangos más cotidianos. Oportunidades futuras honestas (no implementadas para mantener "menos es más"): "esta tarde"/"esta noche" (parte del día, requiere resolver hora-canónica como `NaturalTaskParser`), "la semana que viene", "este mes". Se dejan en BACKLOG como P3 de descubrimiento, no como backlog automático.
  - `SearchEngine` ya ordena por urgencia (`urgencyRank`) — el scope OVERDUE beneficia de esto automáticamente (urgente-atrasada primera). Buen diseño previo, reutilizado sin tocar.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`, `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `1d9fdf0` (rebase sobre `36915ab` c.80 del remoto + push OK `36915ab..1d9fdf0` → `openhands/autonomous-ordia`). Conflictos de rebase en `AI_AUTONOMY/{RUN_LOG,CURRENT_STATE}.md` resueltos (ciclo renumerado 79→81 para no colisionar con c.79/c.80 del parser empujados por run paralelo); 597 domain tests PASS, smoke 25 OK.
- **Estado**: VERIFIED (JVM). 597 domain tests PASS.

### Siguiente
- Búsqueda por parte del día ("esta tarde"/"esta noche") y "la semana que viene"/"este mes" (P3, evaluar necesidad real antes de implementar).
- Descubrimiento continuo: captura ultrarrápida, rutinas adaptables, detección de compromisos en notas, onboarding.
- `PlanEngine`/replanización más amplia (OVERLOADED recurrente → redistribuir la semana).

---
## Ciclo 80 - 2026-08-13 (UTC) - fix(parser): cruce de medianoche en rango nocturno "de 10 de la noche a 1 de la madrugada" (duración perdida)

- **Run/ciclo**: 80 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial local `0f9ff9c` (c.79). `git pull --ff-only` OK sin divergencia. Continúa la nota "Siguiente" del c.79 que dejaba documentado: *falta el simétrico cruce de medianoche (overnight)*. Continuación segura del supervisor.
- **HEAD inicial**: `0f9ff9c` (c.79; local == remoto, sin STALE).
- **Problema seleccionado**: P1 (integridad de captura). El c.79 cerró la familia de **cruces del mediodía**, pero la familia de **cruces de medianoche** (overnight) seguía rota: "Cena de 10 de la noche a 1 de la madrugada" resolvía `dueAt=22:00` (por suerte, vía el canónico de parte del día) pero `durationMinutes=null` — **la longitud real del evento (3h) se perdía**. Lo mismo "Trabajo de 11 de la noche a 6 de la mañana" (7h) y "Fiesta de 9 de la noche a 2 de la madrugada" (5h). Un turno/guardia/velada nocturna quedaba sin duración.
- **Prioridad**: P1 (duración del evento perdida en rangos overnight; forma de expresión común para turnos, cenas, fiestas, guardias nocturnas).
- **Causa raíz**: `rangeMatch` exigía `endMin > startMin` (estrictamente mismo día) como condición de validez. Un rango overnight (22:00→01:00, `endMin=60 < startMin=1320`) violaba esa guarda → el bloque se rechazaba y la duración no se asignaba. El `dueAt` sobrevivía solo porque caía al canónico de la parte del día ("de la noche"=21:00/22:00), pero la longitud del evento se perdía en silencio.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `sameDay = endMin > startMin`; `rawDuration = if (sameDay) end−start else end+24*60−start`. La validez pasa a requerir `rawDuration in 5..(24*60)` (rango plausible), no `endMin>startMin`.
  - El cruce de medianoche **solo se acepta con señal clara** (meridiem/unidad/PM, `clearSignal = hasUnit || hasMinutesOrMeridiem || sAbs>=13 || eAbs>=13`). Un rango ambiguo sin meridiem ("de 10 a 1") **no** se reinterpreta como overnight de 15h (demasiado arriesgado) — se rechaza como antes.
  - `acceptAmbiguous` se restringe a `sameDay` (el heurístico de horas en punto ambas <13 solo aplica a mismo día; overnight siempre necesita meridiem explícito).
  - Lógica local honesta (aritmética de minutos + ajuste de 24h, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública).
- **Tests**: probe JVM `/tmp/probe_midnight.kt` (4 casos overnight) → antes 3/3 FAIL (dur=null), tras fix 4/4 PASS (180/420/300/480). +4 tests en `NaturalTaskParserTest.kt` (`overnightRangeDe10DeLaNocheA1DeLaMadrugada`=22:00/180, `…De11DeLaNocheA6DeLaManana`=23:00/420, `…De9DeLaNocheA2DeLaMadrugada`=21:00/300, `…AmbiguousNotReinterpretedAsOvernight`="de 10 a 1" sin meridiem→null). `bash tools/run_domain_tests.sh` → **588 PASS** (584 c.79 + 4), 26 clases. `bash tools/run_domain_checks.sh` → smoke 25 OK. Sin regresión: mismo-día c.76/c.78/c.79 intacto ("de 6 a 8 de la tarde"=18:00/120, "de 12 a 2 de la tarde"=12:00/120, "de 11 a 1 de la tarde"=11:00/120, "de 6 a 8 pm"=18:00/120).
- **Bugs**: 1 (P1, overnight range duration null → FIXED).
- **Features**: 0.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK). Marcar NO VERIFICADO.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (fecha c.80 + nueva sección "Último trabajo — Ciclo 80"), `BACKLOG.md` (fila c.80 FIXED→VERIFIED), `RUN_LOG.md` (esta entrada).
- **Commits**: 1 (`fix(parser): rango nocturno cruce de medianoche calcula duración con +24h`).
- **HEAD final**: `970d919` (push OK `0f9ff9c..970d919` → `openhands/autonomous-ordia`).
- **Próxima prioridad**: auditar más formas de captura/tiempo (p.ej. rango overnight con minutos "de 10:30 de la noche a 1:15 de la madrugada", o "de X a Y horas" con unidad explícita en overnight); revisar que `rangeStartTime` (dueAt) sea consistente con overnight (¿debería el dueAt ser el inicio nocturno?). Continuar descubrimiento de oportunidades de producto.

---
## Ciclo 79 - 2026-08-13 (UTC) - fix(parser): cruce del mediodía en rango "de 12 a 2 de la tarde" (duración + dueAt erróneos)

- **Run/ciclo**: 79 (rama `openhands/autonomous-ordia`). Base limpia: HEAD inicial local `0a21431` (c.78). `git pull --ff-only` OK sin divergencia. Continúa la nota "Siguiente" del c.78 que dejaba documentado: *"de 12 a 2 de la tarde" duración por horas absolutas resueltas (no raw) — cruces de mediodía*. Continuación segura del supervisor.
- **HEAD inicial**: `0a21431` (c.78; local == remoto, sin STALE).
- **Problema seleccionado**: P1 (integridad de agenda/captura). La familia de rangos horarios con meridiem solo en el extremo final (c.76 "de la tarde", c.78 "pm" compacto) fallaba en el **cruce del mediodía** — la forma cotidiana de un almuerzo o clase que empieza antes de las 12 y termina después. Probe JVM (12 casos) reveló DOS defectos entrelazados:
  1. **Duración por horas crudas**: "Almuerzo de 12 a 2 de la tarde" computaba `end−start` con horas crudas del texto (2−12=−600); `coerceIn(5, 24*60)` dejaba **5 min** en vez de 120. Un almuerzo de 2h se agendaba como 5 min.
  2. **Propagación PM ciega al inicio bare**: el fix del c.76 propagaba `endPm` al inicio sin meridiem **incondicionalmente**. En "Clase de 11 a 1 de la tarde" el inicio 11 se convertía en 23 (11+12) → `dueAt=23:00` y duración absurda, cuando lo correcto es inicio AM 11:00 + fin PM 13:00 = 2h.
  Ambos producían recordatorio/agenda en momento erróneo o duración imposible en la forma más frecuente de expresar un bloque que cruza el mediodía.
- **Prioridad**: P1 (duración 5 min en vez de 120, o dueAt 12h desplazado → recordatorio/cita en momento erróneo; forma de expresión muy frecuente en español).
- **Causa raíz**: (1) `rangeDurationMinutes` (en `rangeMatch`) usaba las horas **crudas** del regex (`groupValues[1]`/`[4]`) en vez de las horas absolutas ya resueltas (`sAbs`/`eAbs`); la coincidencia "6 a 8 de la tarde" (cruda 8−6=120) hacía pasar el bug inadvertido hasta un cruce del mediodía (12→2, 11→1). (2) `startPmEffective = startPm || (startMer.isEmpty() && endPm)` no distinguía "mismo lado del mediodía" (6→8) de "cruce" (11→1): en el primero el inicio debe heredar PM, en el segundo el inicio es AM y solo el fin es PM. (3) En `rangeMatch` el `when` de `resolve` tenía `mer.isEmpty() -> h` **antes** que `pm && h<12 -> h+12` (c.76 lo corrigió en `rangeStartTime` pero no en `rangeMatch` → código muerto).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - La duración se calcula con horas **absolutas resueltas**: `startMin = sAbs*60 + sMin` y `endMin = eAbs*60 + eMin` (derivados de `resolve(...)`), no con horas crudas. Si `endMin <= startMin` se suma 24h (cruce de medianoche, p.ej. "de 10 de la noche a 1 de la madrugada"); clamp `coerceIn(5, 24*60)`.
  - La propagación PM al inicio bare es **condicional**: `startPmEffective = startPm || (startMer.isEmpty() && endPm && startHr <= endHr)`. Solo se propaga cuando `startHr <= endHr` (mismo lado del mediodía); en un cruce (`startHr > endHr`, p.ej. 11→1) el inicio queda AM (11:00) y el fin PM (13:00). Aplicado **simétricamente** a `rangeMatch` (duración/validez) y `rangeStartTime` (dueAt).
  - Reorden del `when` en `rangeMatch` (`resolve`): `pm && h<12 -> h+12` ahora se evalúa **antes** que `mer.isEmpty() -> h` (corrige el código muerto del c.76 en este bloque).
  - Lógica local honesta (aritmética de horas absolutas + comparación de lado del mediodía, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública).
- **Tests**: +6 en `NaturalTaskParserTest.kt`:
  - `noonCrossingRangeDe12A2DeLaTarde` ("Almuerzo de 12 a 2 de la tarde"→12:00/120; antes dur=5).
  - `noonCrossingRangeDe12A2pm` ("Almuerzo de 12 a 2pm"→12:00/120).
  - `noonCrossingRangeDe11A1DeLaTarde` ("Clase de 11 a 1 de la tarde"→11:00/120; antes dueAt=23:00).
  - `noonCrossingRangeDe12A1pm` ("Curso de 12 a 1pm"→12:00/60).
  - `noonCrossingRangeDe1A2DeLaTarde` ("Siesta de 1 a 2 de la tarde"→13:00/60; mismo lado del mediodía, inicio SÍ hereda PM).
  - `noonCrossingRangeAmbiguousRejected` ("Reunión de 12 a 2" sin meridiem→null/null; ambiguo).
  - Probe JVM (24 assertions) sin regresión: mismo-meridiano c.76/c.78 intacto ("de 6 a 8 de la tarde"=18:00/120, "de 6 a 8 pm"=18:00/120, "de 9 a 11 de la mañana"=09:00/120, "de 2:30 a 4:30 pm"=14:30/120, "de 3 a 5 de la noche"=15:00/120), 24h "de 12 a 14"=12:00/120, "12pm a 2pm"=12:00/120.
  - **584 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 578 c.78 + 6), smoke 25 OK (`bash tools/run_domain_checks.sh`). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: el subdominio "rango horario con meridiem solo al final" queda ahora cubierto tanto para mismo-meridiano (c.76/c.78) como cruce-del-mediodía (c.79). Caso residual NO cubierto: cruce de medianoche con duración multi-día real ("cena de 10 de la noche a 2 de la madrugada" = 4h pero cruce día+1 en la fecha); hoy se resuelve como duración 4h correcta pero `dueAt` apunta a hoy 22:00 (no a mañana 02:00) — aceptable porque el recordatorio es del inicio; fuera de alcance por complejidad de fecha-vs-hora.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `9fb6298` c.78).
- **Estado**: FIXED → VERIFIED (dominio JVM). 584 domain tests PASS.

### Siguiente
- ~~"de 12 a 2 de la tarde" duración por horas absolutas resueltas (no raw) — cruces de mediodía.~~ **RESUELTO c.79**.
- Descubrimiento continuo: captura ultrarrápida, búsqueda universal, rutinas adaptables, detección de compromisos en notas, onboarding.
- Auditoría de otra área funcional: `PlanEngine`/replanización (OVERLOADED recurrente → redistribuir semana), What Now, recuperación de tareas olvidadas.

---
## Ciclo 77 - 2026-08-13 (UTC) - feat(resumen): el veredicto del día usa la ventana de jornada APRENDIDA, no la fija 9–18

- **Run/ciclo**: 77 (rama `openhands/autonomous-ordia`). Base limpia: HEAD local `22af5b4` (c.76) == remoto; `git pull --ff-only` OK sin divergencia. Continuación segura del supervisor.
- **HEAD inicial**: `22af5b4` (local == remoto, sin STALE).
- **Problema seleccionado**: auditoría de la cadena de inteligencia del resumen de Today (`SummaryEngine` c.65/c.66/c.67/c.69 ↔ `TodayScreen` ↔ `LearningEngine` ↔ `DayPlanner`) reveló que el veredicto del día (`DayLoad`: LIGHT/ON_TRACK/FULL/OVERLOADED) usaba una **ventana de jornada hardcoded 9–18**, mientras que `LearningEngine` ya perfila los horarios reales del usuario y `DayPlanner` ya los usa para agendar. **Plan y veredicto discrepaban**: un usuario nocturno (jornada real 9–23) veía "OVERLOADED" a las 17:00 cuando le quedaban 6 h de capacidad real; uno madrugador (6–14) veía "ON_TRACK" a las 13:00 cuando su día casi se acababa. La tarjeta de hoy **mentía** para cualquier horario no estándar. Área de dirección explícita "inteligencia/contexto"/"mejores resúmenes del día"/"rutinas adaptables". P1 de inteligencia honesta (no era un bug de datos, pero el veredicto era sistemáticamente erróneo para horarios no canónicos).
- **Prioridad**: P1 (inteligencia/contexto; el veredicto guía la decisión del usuario —"no cabe, pospone X"— y era falso para perfiles no estándar).
- **Causa raíz**: `SummaryEngine.summarize`/`assessDayLoad` tenían `dayStartMinute=9*60`/`dayEndMinute=18*60` **literales dentro del método**, sin recibir el perfil aprendido. El caller (`TodayScreen.kt:117`) llamaba `summarize(state.tasks, clockNow)` sin perfil, aunque `PlannerScreen` ya computaba `LearningEngine.learn(...)` para `DayPlanner`. Dos fuentes de verdad de la jornada (fija 9–18 vs aprendida) coexistían sin reconciliar.
- **Solución (mínima, sin nueva pantalla/botón)**:
  - `SummaryEngine.summarize(..., dayStartMinute: Int = DEFAULT_DAY_START_MINUTE, dayEndMinute: Int = DEFAULT_DAY_END_MINUTE)` con constantes `DEFAULT_DAY_START_MINUTE=540`/`DEFAULT_DAY_END_MINUTE=1080` (9–18), + sobrecarga `summarize(tasks, now, zone, profile: LearningProfile?)` que extrae la ventana o cae a defaults.
  - `assessDayLoad(...)` recibe `dayStartMinute`/`dayEndMinute` y los usa en vez de las constantes hardcodeadas en `freeMinutes = dayEndMinute - max(nowMinute, dayStartMinute)`.
  - `TodayScreen.kt`: calcula el perfil exactamente como `PlannerScreen` (`if (preferences.learningEnabled) LearningEngine.learn(state.tasks, clockNow, zone) else null`) y lo pasa a `summarize(state.tasks, clockNow, ZoneId.systemDefault(), profile)`.
  - Ahora el veredicto y el planificador comparten **fuente única de verdad**: la ventana aprendida. Sin aprendizaje (off o sin datos) → comportamiento idéntico al anterior (no-regresión). Lógica local honesta, sin random/modelo simulado.
- **Tests**: +4 en `SummaryEngineTest.kt` (`dayLoad_usesLearnedWindow_lateSleeperNotOverloadedAt17` jornada 9–23 a las 17:00 → ON_TRACK (era OVERLOADED con 9–18); `dayLoad_usesLearnedWindow_earlyRiserOverloadedPastTheirEnd` jornada 6–14 a las 13:00 → OVERLOADED (era ON_TRACK con 9–18, mentira); `dayLoad_nullProfileFallsBackToDefaultWindow` null ≡ defaults 9–18; `dayLoad_learnedWindowDoesNotAffectCounts` la ventana solo cambia el veredicto, no los conteos). **572 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 568 c.76 + 4), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión: todos los tests `dayLoad_*` y `deferralSuggestion_*` previos intactos (usan la firma sin perfil → defaults 9–18).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la tarjeta en la app (sin Android SDK). El cableado de `TodayScreen` replica el patrón ya verificado de `PlannerScreen` (`LearningEngine.learn` con `preferences.learningEnabled`).
- **Hallazgos adicionales (descubrimiento continuo)**:
  - La sugerencia de posposición (`mostDeferrableTask`, c.66/c.67) y el cálculo de "inminente/en-curso" (`TaskRules.isImminentStart`) NO dependen de la ventana de jornada, así que el fix del veredicto no los afecta. OK.
  - `DayPlanner` ya usa `LearningProfile` (verificado por su existencia y uso en `PlannerScreen`); el resumen ahora se alinea con él. Posible oportunidad futura: que el `DayPlanner` y el resumen compartan UNA instancia de perfil computada una sola vez por render (micro-optimización, P3) — hoy cada uno llama `LearningEngine.learn` por separado; el costo es bajo (percentil sobre ≤28 días de tareas completadas) y no justifica una abstracción nueva todavía.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt`, `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt`, `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `9fb6298` (commit + push a `origin/openhands/autonomous-ordia` verificado: `22af5b4..9fb6298`).
- **Estado**: VERIFIED (JVM). 572 domain tests PASS.

### Siguiente
- ~~P2 (c.76): `explicitTime` sombrea `rangeStartTime` en rango "de 6 a 8 pm" → dueAt=20:00 (fin) en vez de 18:00 (inicio).~~ **RESUELTO en c.78 (paralelo, mismo run-window)**: detección posicional `explicitTimeIsRangeEnd`.
- "de 12 a 2 de la tarde" duración por horas absolutas resueltas (no raw) — cruces de mediodía.
- Descubrimiento continuo: captura ultrarrápida, búsqueda universal, rutinas adaptables, detección de compromisos en notas.
- Micro-optimización (P3, opcional): compartir instancia de `LearningProfile` entre `DayPlanner` y `SummaryEngine` en el mismo render.

---
## Ciclo 78 - 2026-08-13 (UTC) - fix(parser): rango "de 6 a 8 pm" (meridiem compacto solo en extremo final) resuelve INICIO, no FIN

- **Run/ciclo**: 78 (rama `openhands/autonomous-ordia`; renumerado desde 77 por colisión con el c.77 paralelo del `SummaryEngine`, enviado primero al remoto — ambos partieron de `22af5b4`). Continuación directa del hallazgo que el c.76 dejó documentado como "fuera de alcance / ABIERTO" (BACKLOG fila 139). Base limpia: HEAD inicial `22af5b4`, `git pull --ff-only` OK; tras implementar, el remoto avanzó con el c.77 paralelo → `git rebase origin/openhands/autonomous-ordia`, conflicto solo en docs `AI_AUTONOMY` (reconciliado conservando ambos runs; el código parser+tests auto-mezcló limpio — área ortogonal al `SummaryEngine`). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `22af5b4`
- **Problema seleccionado**: P2 (descubierto c.76, ABIERTO) → rebaixado a P1 de hecho por impacto en agenda. Rango horario con meridiem **compacto** "am/pm" solo en el extremo final: "reunión de 6 a 8 pm" → `dueAt=20:00` (hora de FIN), debería 18:00 (hora de INICIO). El recordatorio se disparaba **2h tarde** (20:00 de una cita que empezaba a las 18:00). Mismo defecto en "de 9 a 11 am"→11:00, "de 6 a 8 p.m."→20:00, "de 3 a 5 p m"→17:00, "de 2 a 4 pm"→16:00.
- **Causa raíz**: `timePatterns` (línea ~950) se ejecuta ANTES que `timeRangePattern` y captura el extremo final "8 pm"→20:00 como `explicitTime`. Luego en `parsedTime`, `explicitTime` tenía prioridad absoluta sobre `rangeStartTime` (18:00), sombreándolo. La forma "de la tarde" (c.76) no fallaba porque `timePatterns` no captura "8 de la tarde". Era un bug P1 previo, solo documentado.
- **Solución**: detección posicional en `NaturalTaskParser.kt`. Tras validar `rangeMatch` y calcular `timeMatch`, se introduce `explicitTimeIsRangeEnd = rangeMatch != null && timeMatch != null && timeMatch.range dentro del span de rangeMatch.range`. Cuando true, el tiempo explícito NO es suelto sino el extremo final del rango → `parsedTime` ignora `explicitTime` y deja ganar a `rangeStartTime` (inicio, con propagación PM del c.76). Si el token cae FUERA del span (hora suelta genuina: "Llamada 8pm", "Cita 9am", "a las 3 reunión de 6 a 8 pm"), el guard no actúa → `explicitTime` sigue ganando. Sin nueva pantalla/botón. Cambio mínimo.
- **Tests**: +6 en `NaturalTaskParserTest.kt`. Probe JVM (16 casos) sin regresión. `bash tools/run_domain_tests.sh` → **574 domain tests PASS** (26 clases — 568 c.76 + 6; tras rebase sobre c.77 paralelo con +4 `SummaryEngineTest` = 578 domain tests PASS). `bash tools/run_domain_checks.sh` → smoke **25 OK**. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin SDK).
- **Hallazgos adicionales**: ninguno nuevo P0/P1 en esta auditoría del parser de rangos horarios. El subdominio "meridiem compacto" queda cubierto.
- **AI_AUTONOMY actualizado**: `CURRENT_STATE.md` (sección "Último trabajo — Ciclo 78", marcador de fecha/ciclo), `BACKLOG.md` (fila 139: ABIERTO → FIXED→VERIFIED c.78), `RUN_LOG.md` (esta entrada; nota "Siguiente" del c.77 marcada resuelta).
- **Commits**: `fix(parser): rango "de 6 a 8 pm" resuelve INICIO (18:00) no FIN (20:00)` (+NaturalTaskParser.kt fix + 6 tests + AI_AUTONOMY).
- **HEAD final**: (tras push) — ver `git log -1`.
- **Próxima prioridad**: auditoría de otra área funcional (recuperación de tareas olvidadas / What Now / replanificación automática), o continuar minería de bugs del parser en otros subdominios (cruces de mediodía "de 12 a 2 de la tarde" en duración por horas absolutas, fechas relativas compuestas, duraciones con unidades raras).

---
## Ciclo 76 - 2026-08-13 (UTC) - fix(parser): rango "de 6 a 8 de la tarde" propagaba mal el meridiem PM al inicio bare

- **Run/ciclo**: 76 (rama `openhands/autonomous-ordia`). **STALE_BASE detectado y reconciliado**: HEAD inicial local `268b635` (c.74) pero el remoto avanzó a `37a6c2f` (c.75 paralelo: límites mensuales "mes que viene"/"próximo" + "antepasado mañana") durante el run. Reconciliación no destructiva: `git rebase origin/openhands/autonomous-ordia` reaplicó mi commit `46a9b5e` sobre `37a6c2f`. Los cambios de **código** (parser + tests) se auto-mezclaron limpiamente (áreas ortogonales: mi fix toca `rangeMatch`/`rangeStartTime` ~líneas 1005–1080; el c.75 toca `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern`/`monthBaseForBoundary`/`mananaAsDate`); conflicto solo en `CURRENT_STATE.md` (docs) resuelto conservando ambos runs (renumerado a c.76 para evitar colisión con el c.75 remoto). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `268b635` (local, 1 detrás del remoto `37a6c2f` c.75 paralelo; reconciliado vía rebase).
- **Problema seleccionado**: auditoría del parser natural (área de captura/recordatorios, P1) reveló que en un rango horario donde **solo el extremo final lleva meridiem** — la forma cotidiana de expresar una ventana vespertina/nocturna — el inicio (sin meridiem) **no heredaba** el contexto de tarde/noche. **"reunión de 6 a 8 de la tarde"** → `dueAt=06:00` (debería 18:00); **"de 2 a 4 de la noche"** → 02:00 (debería 14:00). La duración ya era correcta (120, diff de horas en punto) pero la **fecha límite apuntaba a la mañana** → el recordatorio se disparaba **12 horas antes** de la cita real. El usuario dice "de la tarde" una sola vez al final y espera que aplique a todo el bloque. Asimetría frente a la hora suelta "a las 6 de la tarde" → 18:00 (c.61/c.64). P1 de integridad de agenda (recordatorio/cita en momento erróneo). Continuación natural de c.60 (rango con minutos/meridiem en ambos extremos) y c.61 (inicio del rango como dueAt).
- **Prioridad**: P1 (recordatorio 12h antes de la cita real = compromiso mal agendado; forma de expresión frecuente en español).
- **Causa raíz**: en `rangeMatch`/`rangeStartTime` (c.60/c.61) cada extremo se resolvía con su **propio** meridiem vía `resolve(h, mer, pm)`. El inicio sin meridiem caía a la rama `mer.isEmpty() -> h` (hora bare, AM implícito), ignorando el PM del extremo final. En `rangeStartTime`, además, la rama `mer.isEmpty() -> h` se evaluaba **antes** que `pm && h < 12 -> h + 12`, así que aunque se hubiera computado `pm=true` para el inicio, la rama bare ganaba y anulaba cualquier offset. Probe JVM (`tools/domain-smoke/Probe3.kt`, desechable, eliminado tras verificación) confirmó: `reunión de 6 a 8 de la tarde` → due 06:00 (BUG); `de 2 a 4 de la tarde` → 02:00 (BUG); dur=120 correcto.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: propagación de PM del extremo final al inicio cuando el inicio no tiene meridiem.
  - `rangeMatch`: `val startPmEffective = startPm || (startMer.isEmpty() && endPm)` y se usa en `resolve(startH, startMer, startPmEffective)` → validación/duración coherentes con la hora resuelta (antes sAbs=6/startMin=360 diff=840; ahora sAbs=18/startMin=1080 diff=120, ambos válidos ≤1440).
  - `rangeStartTime` (dueAt): mismo cálculo de `pm` (incluye `mer.isEmpty() && endPm`) + **reorden del `when`**: la rama `pm && h < 12 -> h + 12` ahora se evalúa **antes** que `mer.isEmpty() -> h` (antes la rama bare ganaba); además `h == 24 && mer.isEmpty() && !pm` para no alterar el caso medianoche bare.
  - "de la mañana/madrugada" (AM) → no-op (el inicio 9 sigue 09:00).
  - **No se propaga en sentido inverso** (inicio PM → fin bare): evita aceptar falsos positivos tipo "de 2pm a 4 entradas" como rango horario (el countable-noun guard del c.42 depende de que el fin bare no reciba PM).
  - Lógica local honesta, sin random ni modelo simulado. Retrocompatible (sin cambios de firma pública).
- **Tests**: +5 en `NaturalTaskParserTest.kt` (`rangeWithTrailingDeLaTardePropagatesPmToStart` "de 6 a 8 de la tarde"→18:00/120; `rangeWithTrailingDeLaNochePropagatesPmToStart` "de 3 a 5 de la noche"→15:00/120; `rangeWithTrailingDeLaMananaKeepsAmStart` "de 9 a 11 de la mañana"→09:00/120 no-op AM; `rangeWithTrailingDeLaTardeAndStartMinutesPropagatesPm` "de 6:30 a 8 de la tarde"→18:30/90; `rangeWithTrailingDeLaMadrugadaKeepsAmStart` "de 1 a 3 de la madrugada"→01:00/120). **568 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 563 c.75 + 5 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`), sin warnings. Sin regresión (rangos con meridiem en ambos extremos c.60/c.61, bare "de 9 a 11" c.42, guard anti-cantidades "de 2 a 4 entradas"→null, todos intactos por tests previos). Probe JVM confirmó antes/después.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**:
  - En el rango con meridiem **compacto** "am/pm" solo en el extremo final ("de 6 a 8 pm"), `explicitTime` (`timePatterns[2]`) captura el extremo final "8 pm"→20:00 y **sombrea** `rangeStartTime` (18:00) vía `parsedTime = explicitTime ?: rangeStartTime` → el dueAt cae a la hora de **FIN** (20:00), no de inicio. **Preexistente** (no introducido ni empeorado por este fix; antes/después del fix "de 6 a 8 pm" da 20:00). La forma española mayoritaria "de la tarde/noche" SÍ quedó corregida. Documentado en BACKLOG como P2 ABIERTO: requiere rework de la precedencia `explicitTime` vs `rangeStartTime` con riesgo de regresión mayor en casos no-rango; se deja para un ciclo dedicado.
  - "de 12 a 2 de la tarde" → dueAt=12:00 (correcto, mediodía vía propagación) pero dur=5 (raw 2-12 negativo → coerce 5). Limitación preexistente del cálculo de duración por diff de horas en punto para rangos que cruzan mediodía; no introducida por este fix. Futuro: usar horas absolutas resueltas para la duración (no raw) — evaluar impacto.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit + push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 568 domain tests PASS.

### Siguiente
- P2 ABIERTO (descubierto c.76): `explicitTime` sombrea `rangeStartTime` en rango "de 6 a 8 pm" → dueAt=20:00 (fin) en vez de 18:00 (inicio). Rework de precedencia con cuidado de regresión.
- "de 12 a 2 de la tarde" duración por horas absolutas resueltas (no raw) — cruces de mediodía.
- Descubrimiento continuo: captura ultrarrápida, búsqueda universal, rutinas adaptables, detección de compromisos en notas.

---
## Ciclo 74 - 2026-08-13 (UTC) - fix(recurrence): recurrencia anual anclada a 29 de febrero (no deriva a 28/2)

- **Run/ciclo**: 74 (rama `openhands/autonomous-ordia`). Base limpia: HEAD local `aad28ae` (c.73 docs runlog HEAD final) sincronizado con remoto; `git pull --ff-only` OK sin divergencia.
- **HEAD inicial**: `aad28ae` (local == remoto, sin STALE).
- **Problema seleccionado**: auditoría de `RecurrenceEngine` (área señalada como "Siguiente" en c.60/c.73: fin de mes mensual → 31/feb, año bisiesto) reveló que la rama **YEARLY** usaba `base.plusYears(interval)`. Para una recurrencia anual anclada al **29 de febrero** (cumpleaños/aniversario bisiesto), `plusYears(1)` clampaba 29/2/2024 → **28/2/2025** (año no bisiesto); desde ahí TODAS las ocurrencias futuras caían en 28/2 → el día real del compromiso se perdía para siempre tras el primer ciclo. El parser (c.38/c.60) SÍ recuperaba el 29/2 como próxima fecha bisiesta real al capturar la tarea, pero el motor la destruía al completarla. **P1 de integridad de datos** recurrentes. Simétrico exacto al bug mensual de 31 días corregido en c.18 (`nextMonthly` salta meses sin el día).
- **Prioridad**: P1 (corrupción silenciosa del ancla de recurrencia; pérdida del día real de un compromiso periódico; empeora con cada ciclo sin recuperación).
- **Causa raíz**: `RecurrenceFrequency.YEARLY -> base.plusYears(interval)` no trata el único caso en que un día del calendario deja de existir entre años (29/2). `java.time` clampá silenciosamente al 28/2 en vez de saltar al siguiente año válido — mismo defecto estructural que el mensual pre-c.18. Probe JVM confirmó: `LocalDate.of(2024,2,29).plusYears(1)` = `2025-02-28`.
- **Solución (mínima, `RecurrenceEngine.kt`, sin nueva pantalla/botón)**: nueva `nextYearly(base, interval)` análoga a `nextMonthly`. Fechas comunes (cualquiera que no sea 29/2) usan `plusYears` directo — **sin cambio de comportamiento**. Para 29/2 avanza `interval` años y, si el año destino no es bisiesto, sigue avanzando años hasta hallar uno bisiesto (límite 8 iteraciones — siempre hay uno en ≤8). Conserva hora, zona, offset de recordatorio (reutilizado por `nextOccurrence` para todas las ocurrencias) y `interval` como paso mínimo. Lógica local honesta, sin random. Retrocompatible (sin cambios de firma pública).
- **Tests**: +4 en `RecurrenceEngineTest.kt` (`yearly_leapDayAnchorSkipsNonLeapYears` 29/2/2024→29/2/2028 + offset recordatorio 1h preservado; `yearly_leapDayAnchorDoesNotDriftAcrossCycles` 29/2/2028→29/2/2032 confirma no-regresión de la deriva que antes daba 28/2/2029; `yearly_nonLeapDayAnchorUsesPlainPlusYears` 15/8/2026→15/8/2027 caso normal sin alterar; `yearly_leapDayAnchorRespectsInterval` "cada 4 años" 29/2/2024→29/2/2028 valida interval>1). **553 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 549 c.73 + 4 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`), sin warnings. Sin regresión (mensual/quincenal/semanal/diaria intactos por tests previos). Probe JVM (`/tmp/probe_yearly.kt`, desechable, eliminado) confirmó el clamp antes del fix.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; integración real del motor con la app al completar una tarea recurrente (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: la familia de recurrencias ahora trata correctamente el único día inexistente entre ciclos (29/2 anual) y los meses sin el día (mensual 31). Queda por auditar: `nextWeekly` con `recurrenceDays` vacío cae a `plusWeeks(interval)` (correcto); el bucle `while (next <= completedAt) guard++ < 10_000` podría iterar mucho si `interval=1` y `completedAt` muy futuro (caso teórico, no real). Áreas siguientes: parser "este fin de semana" (ABIERTO P3), adjetivos de cadencia desnudos ("pago mensual" — requiere decisión de falso positivo), captura ultrarrápida.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/RecurrenceEngine.kt`, `app/src/test/java/com/ordia/app/domain/RecurrenceEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit + push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 553 domain tests PASS.

### Siguiente
- Descubrimiento continuo: parser "este fin de semana" (ABIERTO P3), adjetivos de cadencia desnudos ("pago mensual" — decisión de falso positivo), `GuardianCoach`, captura ultrarrápida, rango horario que setee `startAt`/`dueAt` además de duración.

---
## Ciclo 60 - 2026-08-13 (UTC) - fix(parser): rango horario con minutos/meridiem ("clase de 9:30 a 11", "de 9am a 11am")

- **Run/ciclo**: 60 (rama `openhands/autonomous-ordia`). **STALE_BASE detectado y reconciliado**: el HEAD inicial local era `21f024d` (c.58 docs) pero el remoto ya estaba en `1d4a776` (c.59 verbo-recordatorio) — 2 commits paralelos (c.59 verbo-recordatorio + c.59 docs delivery) aterrizaron mientras se trabajaba. Reconciliación no destructiva: `git stash` del trabajo local → `pull --ff-only origin/openhands/autonomous-ordia` (a `1d4a776`) → `stash pop`. Los cambios de **código** (parser + tests) se auto-mergearon limpiamente (áreas ortogonales: mi fix toca `timeRangePattern`/`rangeMatch`; el c.59 toca `bareReminderVerbPattern`/recordatorios); sin conflicto. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `21f024d` (local, 2 detrás del remoto real `1d4a776`).
- **Problema seleccionado**: auditoría del motor de parsing natural (continuación del descubrimiento del c.58) reveló que el **rango horario CON minutos o meridiem en ambos extremos** — la forma de escribir reuniones/clases con horario preciso — NO parseaba la duración. **"clase de 9:30 a 11"** → `durationMinutes=null` y `title='Clase de a 11'` (perdiendo "9:30"); **"de 9am a 11am"** → `dur=null` y `title='Clase de a'` (perdía ambas horas); **"curso de 8:30 a 10:30 horas"** → `dur=1440` (clamp 24h falso por residuo). **Causa raíz**: `timeRangePattern` (`(\d{1,2})\s*(?:a|-)\s*(\d{1,2})`) solo capturaba horas en punto; "9:30 a 11" casaba "30 a 11" con números equivocados y caía a null o a un clamp erróneo, dejando residuo en el título. P2 de producto: duración de un evento real (90/120 min) perdida + título corrupto. Simétrico al fix c.42 (rango sin "horas") para el caso con minutos/meridiem.
- **Prioridad**: P2 (captura/duración: título sucio + duración real perdida; no perdía datos pero degradaba la captura natural frecuente).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: `timeRangePattern` ahora tiene 7 grupos — cada extremo captura hora `(\d{1,2})`, minutos opcionales `(?::([0-5]\d))?` y meridiem opcional (`am`/`pm`/`de la tarde`/`de la noche`/`de la mañana`/`de la madrugada`), más el grupo "horas" final (captura, no `?:`). El `rangeMatch` resuelve cada extremo a **hora absoluta** (offset PM aplicado por separado: `resolve(h,mer,pm)` → 9pm→21, 12pm→12, 12am→0, 24→0) y la duración es `(fin − inicio)` en **minutos reales**, no solo horas en punto. Mantiene intacto el guard anti-falsos positivos del c.42 (`ambiguousOnTheHour` + `followedByCount`: "de 2 a 5 entradas" sigue rechazado como cantidad, no horario) y el clamp `5..24h`. El rango se elimina del título tras consumir fechas/horas → título limpio. Lógica local honesta, sin random ni modelo simulado. Retrocompatible (sin cambios de firma pública).
- **Tests**: +8 en `NaturalTaskParserTest.kt` (`rangeWithStartMinutesParsesRealDuration` "9:30 a 11"→90, `rangeWithBothEndpointsMinutesParsesRealDuration` "9:30 a 11:30"→120, `rangeWithMinutesAndHoursUnitParsesRealDuration` "9:30 a 11 horas"→90, `rangeWithMeridiemAmParsesDuration` "9am a 11am"→120, `rangeWithMeridiemPmParsesDuration` "2pm a 4pm"→120, `rangeWithMinutesAndMeridiemParsesDuration` "8:30am a 10:30am"→120, `rangeWithDeLaTardeMeridiemParsesDuration` "9 de la tarde a 11 de la noche"→120, `rangeWithMinutesDoesNotClampToDayMax` "8:30 a 10:30 horas"→120). **463 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 455 c.59 + 8 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`), sin warnings. Sin regresión (caso en punto "9 a 11"→120, guard anti-cantidades "de 2 a 5 entradas"→null, rangos 24h "18 a 20"→120, "de la tarde" soltero, todos OK por tests previos). Probe JVM (`tools/domain-smoke/Probe4.kt`, desechable, eliminado tras verificación) confirmó antes/después.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: el `due` para "clase de 9:30 a 11" se setea vía `timePatterns[1]` (HH:MM suelto) — comportamiento preexistente, no regresión. Futuro: auditar si un rango horario debería setear también `startAt`/`dueAt` (no solo duración) para que el evento aparezca en su franja en el planificador; hoy solo calcula duración. Áreas a auditar: `RecurrenceEngine.nextOccurrence` (fin de mes mensual → 31/feb, año bisiesto), `GuardianCoach` vencidas importantes, captura ultrarrápida.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base reconciliada `1d4a776` c.59).
- **Estado**: VERIFIED (JVM). 463 domain tests PASS (reconciliación limpia sobre c.59).

### Siguiente
- Descubrimiento continuo: auditar `RecurrenceEngine.nextOccurrence` edge cases (fin de mes mensual → día 31/febrero, año bisiesto), `GuardianCoach` detección de vencidas importantes, `SummaryService`, captura ultrarrápida.
- Parser: múltiples marcadores temporales en una frase; rango que setee `startAt`/`dueAt` además de duración.


## Ciclo 58 - 2026-08-13 (UTC) - fix(parser): fracción sub-hora "y media"/"y cuarto" + conector "en la tarde"

- **Run/ciclo**: 58 (rama `openhands/autonomous-ordia`). **STALE_BASE detectado y reconciliado**: el HEAD inicial local era `5e6cea8` (c.52) pero el remoto ya estaba en `053e7ff` (c.57) — 4 ciclos paralelos (53–57: What-Now prioridad, intervalo+días, partOfDay DAILY, subtarea-autocomplete desde notificación, número escrito) aterrizaron mientras se trabajaba. Reconciliación no destructiva: `git stash` del trabajo local → `merge --ff-only origin/openhands/autonomous-ordia` (a `053e7ff`) → `stash pop`. Los cambios de **código** (parser + tests) se auto-mergearon limpiamente sobre la base nueva (áreas ortogonales: mi fix toca `timePatterns`/`standalonePartOfDayPattern`/`mananaAsDate`; los c.55–57 tocaron `parseRecurrence`/`RecurrenceResult`/`ReminderActionReceiver`); los **docs** en conflicto (`CURRENT_STATE`, `RUN_LOG`) se restauraron desde HEAD remoto y se reescribieron como c.58. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `5e6cea8` (local, obsoleto). Remoto real al iniciar: `053e7ff` (c.57).
- **Problema seleccionado**: auditoría del motor de parsing natural descubrió dos fallos reales de captura (la superficie más frecuente de Ordía), ambos honestos y sin IA.
  - **Fix A (P1) — "a las 9 y media"/"a las 3 y cuarto"**: el parser NO reconocía la fracción sub-hora cotidiana en español. "Cita a las 9 y media" → `due=09:00` (debería 09:30) y `title='Cita y media'` (la frase se filtraba al título). Cita/reunión programada 30 min mal (15 min con "y cuarto") y título sucio. **Causa raíz**: `timePattern[0]` (`\ba\s+las\s+([01]?\d|2[0-4])(?::([0-5]\d))?...`) no tenía grupo para la fracción; "y media" caía fuera del match → se pegaba al título, y la hora quedaba en punto. P0/P1 de producto: hora mal programada = reunión perdida o recordatorio en el momento erróneo.
  - **Fix B (P2) — "en la tarde/noche/mañana"**: forma caribeña/hispanoamericana (zona de la app `America/Santo_Domingo`) del conector de parte del día. Antes solo se reconocían "a la"/"de la"/"por la"; "en la" no casaba: "hoy en la tarde" → `due=09:00` (debería 15:00) y residuo "en la tarde" en el título, **inconsistente** con "hoy a la tarde" que SÍ funcionaba. **Causa raíz**: `standalonePartOfDayPattern` no incluía `en\s+la`; y `mananaAsDate` no excluía "en" en su `timeMarker`, así que "en la mañana" podía contar "mañana" como fecha (mismo defecto que c.39 corrigió para "de/por/a la mañana").
- **Prioridad**: P1 (Fix A: hora mal programada = reunión/recordatorio en momento erróneo, título sucio) + P2 (Fix B: hora/residuo inconsistentes según preposición).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  - `timePatterns[0]` gana grupo 3 opcional `(?:\s+y\s+(media|cuarto))?` tras la hora/minutos; el meridiem pasa a grupo 4 (leído con `getOrNull(4)` para no romper los otros patrones que no tienen el grupo). `minute = explicitMinute ?: (media→30, cuarto→15, else→0)`. Respeta meridiem/contexto PM: "a las 9 y media de la tarde" → 21:30, "y media pm" → 21:30, "y media de la madrugada" → 04:30. Los minutos explícitos (`9:30`) siguen teniendo prioridad.
  - `standalonePartOfDayPattern` añade `en\s+la` a los conectores; `mananaAsDate` añade `en` a su `timeMarker` para que "en la mañana" no se cuente como fecha "mañana".
  - Lógica local honesta, sin IA falsa. Retrocompatible (sin cambios de firma pública).
- **Tests**: +12 en `NaturalTaskParserTest.kt` (7 de Fix A: `y media`/`y cuarto` con y sin meridiem/tarde/noche/madrugada/pm/am; 5 de Fix B: "hoy en la tarde", "mañana en la noche", "en la mañana" sin fecha, "en la tarde a las 4" con contexto PM). **450 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 439 base c.57 + 11 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM verificó además **interacción** con la recurrencia DIARIA "cada mañana" del c.55 ("Meditar cada mañana a las 8 y media" → `DAILY 08:30`) y que las regresiones no se rompen (horas en punto, "a las N horas", "por la/de la/a la" preexistentes, "media hora"/"un cuarto de hora" como duración siguen OK).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, captura real en el dispositivo (parser probado solo en JVM pura con stubs).
- **Hallazgos adicionales (descubrimiento continuo)**: probe reveló que el **rango horario CON minutos** ("clase de 9:30 a 11") o con meridiem en ambos extremos ("de 9am a 11am") NO parsea la duración correctamente (`title='Clase de a 11'`, pierde "9:30"; `dur=null`). El `timeRangePattern` captura horas en punto y deja residuo cuando hay minutos/meridiem. Documentado en BACKLOG como P2 ABIERTO (futuro run).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `db30b9c` (tras push a `origin/openhands/autonomous-ordia`; base reconciliada `e611da6` c.57+CI).
- **Estado**: VERIFIED (JVM). 450 domain tests PASS (reconciliación limpia sobre c.57 número-escrito).

### Siguiente
- P2 ABIERTO: rango horario con minutos/meridiem en ambos extremos ("clase de 9:30 a 11", "de 9am a 11am") — `timeRangePattern` deja residuo y no calcula duración.
- Descubrimiento continuo: auditar `RecurrenceEngine.nextOccurrence` edge cases (fin de mes mensual → día 31/febrero, año bisiesto), `GuardianCoach` detección de vencidas importantes, `SummaryService`, captura ultrarrápida.
- Parser: múltiples marcadores temporales en una frase.



## Ciclo 57 - 2026-08-13 (UTC) - fix(parser): intervalo de recurrencia con número escrito ("cada dos semanas")

- **Run/ciclo**: 57 (base remota `4059f78` ciclo 56 subtarea-autocomplete; rama `openhands/autonomous-ordia`).
- **HEAD inicial**: `34437db` (base local al iniciar; el remoto ya estaba en `4059f78` con el ciclo 56 subtarea-autocomplete — STALE_BASE detectado al fetch; rebase no destructivo, conflicto solo en `CURRENT_STATE.md` resuelto conservando ambos trabajos, áreas ortogonales `ReminderActionReceiver` vs `NaturalTaskParser`; sin force push).
- **Problema seleccionado**: `NaturalTaskParser.parseRecurrence` NO reconocía el **intervalo de cadencia con número escrito** — **"cada dos semanas"**, **"cada tres meses"**, **"cada quince días"**, **"cada dos años"**. La rutina quedaba como **tarea única sin fecha** (`recurrence=NONE`, `dueAt=null`): invisible en What Now/planificador, recordatorio jamás disparaba → tarea recurrente **olvidada**. **Causa raíz P1**: `intervalPattern` (`\bcada\s+(\d{1,3})\s*(días|semanas|meses|años)\b`) **sólo admitía dígitos**; al escribir el número con palabra la regex no casaba y la rama caía a NONE. Sutil porque solo se manifiesta cuando el recordatorio "no vuelve". Descubierto por probe JVM (`tools/domain-smoke/Probe3.kt`, desechable, eliminado tras verificación).
- **Prioridad**: P1 (pérdida de datos silenciosa de rutinas con cadencia no-unitaria).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: el grupo de captura del número pasa de `\d{1,3}` a `(\d{1,3}|<números-escritos>)`, donde la alternancia está **acotada a los números conocidos** en `parseWrittenNumber` (un–treinta, sin sobrescribir la unidad días/semanas/...). Resolución: `toLongOrNull()` (dígito) primero, luego `parseWrittenNumber(rawN)` (palabra), `coerceIn(1,366)`. Reutiliza el helper existente (ya usado en recordatorios c.40 y "un par de" c.35) — sin enum ni migración. Lógica local honesta, sin random ni modelo simulado.
- **Tests**: +4 en `NaturalTaskParserTest.kt` (`cadaDosSemanasParsesWeeklyInterval2`, `cadaTresMesesParsesMonthlyInterval3`, `cadaQuinceDiasParsesDailyInterval15`, `cadaDosAnosParsesYearlyInterval2`). **439 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 435 c.55 + 4 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Pruebas del ciclo 55 (partOfDay DAILY) y 54 (intervalo+días) siguen en verde → sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: probe reveló **adjetivos de cadencia desnudos** no reconocidos ("pago mensual", "reunión semanal", "suscripción anual", "repaso diario" → `NONE due=null`). Documentado en BACKLOG como P2 ABIERTO con advertencia: riesgo de falso positivo ("diario"=sustantivo, "informe mensual" puede ser puntual) — forzar recurrencia crearía tareas recurrentes NO deseadas (otro problema de integridad). No implementado sin señal desambiguadora. Las formas con `-mente` ("mensualmente") SÍ funcionan.
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `94752f8` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 439 domain tests PASS (rebase limpio sobre c.56 subtarea-autocomplete).

### Siguiente
- P2 ABIERTO: adjetivos de cadencia desnudos ("pago mensual") — decidir umbral de falso positivo (requiere señal desambiguadora: "cada"/"todos los" o sustantivo fuerte "pago"/"factura"/"suscripción").
- Descubrimiento continuo: auditar `RecurrenceEngine.nextOccurrence` edge cases (fin de mes mensual → día 31/febrero, año bisiesto), `WhatNowEngine` ranking, `GuardianCoach` detección de vencidas, captura ultrarrápida.
- P1 adjuntos: migración lazy de URIs externos legacy (seguridad).

## Ciclo 55 - 2026-08-13 (UTC) - fix(parser): "cada mañana/tarde/noche/madrugada" como recurrencia DIARIA

- **Run/ciclo**: 55 (base remota `b5c96d5` ciclo 54; rama `openhands/autonomous-ordia`).
- **HEAD inicial**: `b5c96d5` (origin/openhands/autonomous-ordia, sincronizado).
- **Problema seleccionado**: `NaturalTaskParser.parseRecurrence` NO reconocía **"cada mañana/tarde/noche/madrugada"** (ni "todas las mañanas/tardes/noches") como recurrencia DIARIA de un hábito cotidiano. La forma más natural de expresar un hábito diario en español ("meditar cada mañana", "tomar pastillas cada mañana", "pasear al perro cada tarde") caía sin recurrencia. **Causa raíz P1**: la palabra "mañana" colisionaba con el token de **fecha** "mañana" (día siguiente) — el parser la consumía como fecha y la rutina quedaba como tarea **ÚNICA para mañana** sin recurrencia. La rutina diaria se perdía: el recordatorio disparaba una sola vez y nunca más, y la intención de repetición desaparecía silenciosamente.
- **Prioridad**: P1 (pérdida de datos silenciosa de rutinas: la repetición diaria se pierde y solo se manifiesta cuando el recordatorio "no vuelve").
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nueva rama al **inicio** de `parseRecurrence` que detecta `cada <parte-del-día>` y `todas las <partes-del-día>` vía regex `\bcada\s+(ma[nñ]ana|manana|tarde|noche|madrugada)\b` | `\btodas\s+las\s+(ma[nñ]anas|...)\b`. Devuelve `RecurrenceResult(DAILY, interval=1, days=[], partOfDayTime, partOfDayIsPm)` con la **hora canónica** de cada parte del día (mañana 09:00, tarde 15:00, noche 21:00, madrugada 04:00) y contexto PM para tarde/noche. Al procesarse **PRIMERO**, "mañana" deja de ser candidato a fecha (se consume como parte de la recurrencia) y la hora canónica sustituye al respaldo genérico 09:00. `partOfDayTime`/`partOfDayIsPm` ya existían en `RecurrenceResult` (añadidos para "esta tarde a las 4" PM offset); se reutilizan. Una hora explícita ("cada mañana a las 7") sigue teniendo prioridad sobre la canónica; el contexto PM aplica offset +12 a horas sin meridiem ("cada noche a las 10" → 22:00). "todos los días"/"diariamente" (sin parte del día) siguen cayendo abajo en `fixedPatterns` con su respaldo 09:00 — sin regresión.
- **Colisión de remoto (no destructiva)**: al rebasear sobre `b5c96d5` (ciclo 54 "intervalo+días"), conflicto en `NaturalTaskParser.kt` — ambos commits añadían código al inicio de `parseRecurrence`: el remoto el helper `detectWeekInterval()`, el local el bloque `partOfDayDaily`. Resolución combinando ambos: `partOfDayDaily` PRIMERO (early-return DAILY con hora canónica) y `detectWeekInterval()` DESPUÉS (helper para las ramas de días). Áreas ortogonales, ambos preservados. Sin force push.
- **Tests**: +7 en `NaturalTaskParserTest.kt` (`cadaMananaIsDailyRecurrenceWithCanonicalTime`, `cadaTardeIsDailyWithPmContext`, `cadaNocheIsDailyWithPmContext`, `cadaMadrugadaIsDaily`, `todasLasNochesIsDaily`, `cadaMananaWithExplicitTimeKeepsTime`, `cadaNocheConHoraSinMeridiemAplicaPm`). **435 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases), smoke 25 OK (`tools/run_domain_checks.sh`). Las 6 pruebas del ciclo 54 (intervalo+días) siguen en verde → sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app (sin Android SDK).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `69b8ef8` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 435 domain tests PASS (428 c.54 + 7 nuevos; rebase limpio sobre b5c96d5).

### Siguiente
- Descubrimiento continuo: auditar captura, rutinas, recordatorios (scheduling edge cases),
  detección de vencidas importantes, búsqueda universal, `WhatNowEngine`, `GuardianCoach`.
- Parser: "cada N días/semanas/meses" con intervalo numérico (cubierto parcial); formas con
  "semana"/"mes" suelto como hito vs recurrencia (ambigüedad léxica).
- P1 adjuntos: migración lazy de adjuntos legacy (URIs externos antiguos) — evaluar seguridad.

## Ciclo 53 - 2026-08-13 (UTC) - fix: What Now desempata por prioridad (consistencia con el widget)
- **Run/ciclo**: 53.
- **HEAD inicial**: `d18fc32` (base local al iniciar; el remoto ya estaba en `8275185` con ciclos 52 snooze + docs — STALE_BASE detectado al fetch).
- **Colisión evitada (no destructiva)**: al `git fetch` el remoto estaba en `8275185` (ciclo 52 = snooze `ReminderRules`, otro run, + docs). Mi base `d18fc32` era obsoleta. NO se forzó push. Verificado `git diff d18fc32..8275185` en mis 4 archivos (`TaskRules/WhatNowEngine/DayPlanner/WhatNowEngineTest`) → vacío: el remoto NO los tocó. Stash del trabajo → `git merge --ff-only` a `8275185` → `git stash pop` sin conflicto. Trabajo del ciclo 52 (ReminderRules/ReminderActionReceiver) preservado íntegro.
- **Problema seleccionado**: `WhatNowEngine.suggest` (tarjeta What Now de `TodayScreen`) NO desempataba por prioridad entre tareas del mismo rango temporal, mientras que `TaskRules.nextBestTask` (widget de inicio + asistente + fallback `nextTask` del ViewModel) SÍ lo hacía (`thenByDescending { priorityScore }`). Inconsistencia real: dos tareas atrasadas donde la NORMAL vencía ANTES que la URGENTE → What Now sugería la normal (por `dueAt` más próximo) y el widget sugería la urgente (por prioridad). What Now y el widget daban **respuestas distintas** para el mismo conjunto de tareas. Además `priorityScore` estaba duplicado como `private` en `TaskRules` y `DayPlanner`, violando la fuente única de verdad ya declarada para otros helpers (`isImminentStart`, `isDueToday`).
- **Prioridad**: P2 (consistencia de inteligencia entre superficies; no es pérdida de datos, pero degrada la confianza en "¿Qué hago ahora?" al contradecir al widget).
- **Causa raíz**: el comparator de `WhatNowEngine.suggest` se construyó antes de que `nextBestTask` (ciclo 45) estandarizara el orden con desempate por prioridad, y nunca se sincronizó. La duplicación de `priorityScore` vino de copiar la lógica en `DayPlanner` en lugar de reutilizar.
- **Solución (mínima, sin nueva pantalla/botón)**: `TaskRules.priorityScore` ahora **público** (fuente única de verdad del puntaje de prioridad, compartido por What Now, widget/asistente y planificador). `WhatNowEngine` añade `thenByDescending { TaskRules.priorityScore(it.priority) }` ANTES del `dueAt`, replicando el orden exacto de `nextBestTask` → ambas superficies sugieren la misma tarea. `DayPlanner` reutiliza `TaskRules.priorityScore` y se elimina su copia `private` duplicada (DRY). Lógica local honesta, sin IA falsa ni random. Retrocompatible (sin cambios de firma pública).
- **Tests**: +1 `picksUrgentOverNormalAmongOverdue` en `WhatNowEngineTest.kt` (reproduce el bug: dos atrasadas normal 9:00 vs urgente 10:00 mismo día → antes del fix What Now elegía la normal por `dueAt`; tras fix elige la urgente Y `assertEquals(suggestion.task.id, widget?.id)` confirma que coincide con `nextBestTask`). **422 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 421 base c.52 + 1 nuevo), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real de la tarjeta What Now en `TodayScreen`, widget real en pantalla de inicio.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/TaskRules.kt` (`priorityScore` private→public + KDoc), `app/src/main/java/com/ordia/app/domain/WhatNowEngine.kt` (+tiebreaker +KDoc), `app/src/main/java/com/ordia/app/domain/DayPlanner.kt` (reutiliza TaskRules.priorityScore, elimina copia privada), `app/src/test/java/com/ordia/app/domain/WhatNowEngineTest.kt` (+1 test), `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `8f290c0` (push a openhands/autonomous-ordia OK; `8275185..8f290c0`, fast-forward sin colisión).

### Siguiente
- Descubrimiento continuo: auditar captura, recordatorios (cancelación al completar/archivar, persistencia real del snooze vía WorkManager), detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad.
- Posible P1: verificar que `ReminderScheduler.scheduleAt` persiste el snooze tras reinicios (WorkManager vs AlarmManager); auditar cancelación de recordatorios al completar/archivar (evitar notificaciones de tareas ya hechas).
- Parser: manejo robusto de múltiples marcadores temporales en una frase.

## Ciclo 54 - 2026-08-13 (UTC) - fix(parser): intervalo de cadencia + lista de días ("cada 2 semanas los lunes")
- **Run/ciclo**: 54 (renombrado desde 53: el ciclo 53 del run paralelo —What Now desempate por prioridad— ya había aterrizado en el remoto: el ciclo 52 del run paralelo —snooze reminderAt— ya había aterrizado en el remoto `de571d6`; se renumera a 53 para evitar dos entradas distintas con el mismo ciclo).
- **HEAD inicial**: `d18fc32` (base local sincronizada; el remoto avanzó a `5e6cea8` (incluye ciclo 53 What Now) con el ciclo 52 snooze + docs — detectado al push, rebase no destructivo).
- **Problema seleccionado**: P1 captura/recurrencia. `NaturalTaskParser.parseRecurrence`: al combinar un intervalo de cadencia con una lista de días (**"cada 2 semanas los lunes"**, **"cada quincena los lunes y viernes"**, **"cada 3 semanas los martes y jueves"**, **"cada 2 semanas de lunes a viernes"**, **"cada 2 semanas los findes"**), la rama WEEKLY+days devolvía `interval=1` hardcoded e ignoraba el intervalo explícito. Consecuencia doble: (1) la rutina quedaba programada como **todas las semanas** aunque el usuario pidió quincenal/cada-N-semanas (cadencia errónea → tareas duplicadas, recordatorios mal cadenciados); (2) la frase de intervalo ("cada 2 semanas") no se consumía y **quedaba como residuo en el título**. Verificado con probe temporal (luego eliminado): "Gym cada 2 semanas los lunes" → antes `WEEKLY interval=1 days=1` + título `"Gym cada 2 semanas"`.
- **Prioridad**: P1 (persistencia/recurrencia/captura: cadencia errónea + título sucio + potencial duplicación de tareas recurrentes).
- **Causa raíz**: las ramas de días (`dayListPattern`, `weekdayRangePattern`, `weekdaySetPattern`, `weekendRecurrencePattern`) devolvían `interval=1` y solo añadían su propio rango a `phraseRanges`, dejando la frase de intervalo sin consumir.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: helper local `detectWeekInterval()` que detecta "cada N semanas" (dígito) o "cada quincena/quincenalmente/todas las quincenas" (palabra) y devuelve `(interval, rango)`. Las 4 ramas de días consumen ese intervalo cuando existe (lo aplican a `RecurrenceResult.interval` y añaden su rango a `phraseRanges` para limpiarlo del título). Sin intervalo explícito → `interval=1` (cadencia semanal normal, sin regresión). Lógica local honesta, sin random ni modelo simulado.
- **Tests**: +6 en `NaturalTaskParserTest.kt` (intervalo+día único, quincena+día, cada 3 semanas+múltiples días, intervalo+rango L-V, intervalo+findes, y regresión: días sin intervalo → interval=1). **428 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 422 base c.53 What-Now + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app (sin Android SDK).
- **Descubrimiento (registrado, no implementado)**: "Nómina cada 2 semanas lunes" (sin "los") deja `days=` vacío — día suelto sin prefijo es ambiguo (¿fecha vs día de recurrencia?) y `dayListPattern` exige 2+ días o plural marcado para el caso bare; caso límite, no degradado por este fix. Auditoría de `RecurrenceEngine.nextWeekly` (interval+recurrenceDays) confirmó que el motor SÍ avanza correctamente con interval>1 (while loop) — el bug era solo de parsing, no del motor.
- **Colisión de remoto (rebase no destructivo)**: al push, el remoto había avanzado `d18fc32`→`8275185` (4 commits: ciclo 52 snooze `ReminderRules.kt`/`ReminderActionReceiver.kt` + 3 docs). Áreas ortogonales (parser vs reminders): el remoto NO tocó `NaturalTaskParser.kt` ni su test → rebase limpio en código; único conflicto en `RUN_LOG.md` (prepend de entradas), resuelto conservando AMBAS entradas (ciclo 52 snooze del remoto + ciclo 53 parser) y renumerando. Sin force push, sin reset --hard, sin tocar `main`. Trabajo del run paralelo preservado.
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (ver commit; push a openhands/autonomous-ordia).

### Siguiente
- Descubrimiento continuo: auditar recordatorios, detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad, backup/restore.
- Parser: día suelto sin prefijo junto a intervalo explícito ("cada 2 semanas lunes") — evaluar si merece tratarse como día de recurrencia cuando ya hay intervalo explícito que desambigua.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
)

---
## Ciclo 54 - 2026-08-13 (UTC) - fix: restaurar tarea archivada re-programa su recordatorio
- **Run/ciclo**: 54.
- **HEAD inicial**: `5e6cea8` (remoto sincronizado tras push del ciclo 53 `8f290c0`).
- **Problema seleccionado**: al archivar una tarea (`deleteTask`) se cancela su recordatorio (`reminderScheduler.cancel(task.id)` → WorkManager `cancelUniqueWork`). Al **restaurar** la tarea desde el archivo (`restoreArchived("task", id)`), la fila vuelve a estar activa con sus campos `reminderAt`/`dueAt` intactos, PERO **nunca se re-encola el trabajo de WorkManager**: el `cancel` del archivo destruyó el `ordia_task_reminder_<id>`. No hay resync en arranque de app (solo `ReminderResyncReceiver` ante cambio de hora/zona/fecha) ni `BOOT_COMPLETED`. Resultado: una tarea restaurada con una fecha futura **"olvida" avisar** hasta que el usuario la edite o cambie la zona horaria — un recordatorio silenciosamente perdido. Esto es P1 (recuperación de información importante / evitar olvidos).
- **Prioridad**: P1 (recordatorio perdido en restaurar; no es corrupción de datos pero sí pérdida de la capacidad de avisar).
- **Causa raíz**: `restoreArchived` solo llamaba `taskRepository.restore(id)` sin reflejar el efecto lateral simétrico del archivo (que SÍ cancela el recordatorio). Falta de simetría entre archivar (cancel) y restaurar (re-schedule).
- **Solución (mínima, sin nueva pantalla/botón)**: en `restoreArchived`, tras `taskRepository.restore(id)` para tareas, se re-lee la entidad restaurada y, si sigue activa (`!completed`, `status != CANCELLED`) y tiene disparo (`reminderAt != null || dueAt != null`), se llama `reminderScheduler.schedule(restored)`. Misma regla de disparo que `upsert` y `ReminderSync`. Solo "task" (projects/notes/habits/routines no tienen recordatorios). Reutiliza `ReminderScheduler.schedule` (que ya usa `reminderAt ?: dueAt` y `coerceAtLeast(0)`).
- **Tests**: la lógica vive en `OrdiaViewModel` (Android/Room/WorkManager) → **NO VERIFICADO** en JVM pura. `bash tools/run_domain_tests.sh` = **422 tests PASS** (sin regresión en dominio); smoke 25 OK (`tools/run_domain_checks.sh`). No se añadieron tests JVM porque el flujo requiere DAO/WorkManager reales (marcado NO VERIFICADO).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, WorkManager real, `taskRepository.get`/`restore` reales, render real de la papelera en `TodayScreen`.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt` (rama `"task"` de `restoreArchived` re-programa el recordatorio), `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Auditoría relacionada (sin acción requerida)**: confirmado que completar (`toggleTask` l.496), auto-completar padre (`completeParentAutomatically` l.541), archivar (`deleteTask` l.564) y borrado permanente (`deleteArchivedPermanently` l.803) SÍ cancelan el recordatorio correctamente. El snooze tras reinicio (recordatorio de WorkManager `ExistingWorkPolicy.REPLACE`) persiste por diseño de WorkManager (ciclo 52). Único gap era restaurar — cerrado aquí.
- **HEAD final**: (pendiente de push a openhands/autonomous-ordia).

### Siguiente
- Descubrimiento continuo: auditar `ReminderActionReceiver` (acciones de notificación: completar/snooze desde la notificación, consistencia con `toggleTask`), detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad.
- Posible P1: verificar que completar una tarea DESDE LA NOTIFICACIÓN cancela el recordatorio y genera la próxima ocurrencia recurrente (mismo flujo que `toggleTask`).

---

## Ciclo 52 - 2026-08-13 (UTC) - fix: snooze ya no corrompe reminderAt en tareas recurrentes
- **Run/ciclo**: 52.
- **HEAD inicial**: `d18fc32` (base `openhands/autonomous-ordia` sincronizada al iniciar; clean — cycle 51 docs follow-up del run previo).
- **Problema seleccionado**: `ReminderActionReceiver.ACTION_SNOOZE` sobrescribía `task.reminderAt = now + 10min`. Para tareas **recurrentes** eso corrompe el offset `reminderOffset = dueAt - reminderAt` que `RecurrenceEngine.nextOccurrence` (línea 29) reutiliza en TODAS las ocurrencias futuras. Un recordatorio "15 min antes" (dueAt 10:00, reminderAt 09:45) tras un snooze a las 09:50 pasaba a `reminderAt=10:00` → offset 0min → las próximas ocurrencias no recordaban nada (o "5 min antes" si snooze 5 min antes del vencimiento). Mutación permanente de una preferencia por una acción transitoria. Sutil: solo afecta recurrentes y se manifiesta en la *siguiente* ocurrencia, días/semanas después.
- **Prioridad**: P1 (integridad de datos — preferencia de recordatorio perdida silenciosamente; no es crash pero es dato corrupto permanente en cada recurrencia futura).
- **Causa raíz**: el snooze mezclaba dos conceptos: "cuándo volver a notificar" (transitorio, ahora+10min) y "cuál es la preferencia de aviso de la tarea" (permanente, `reminderAt`). Sobrescribía el campo permanente con el valor transitorio.
- **Solución (mínima, sin nueva pantalla/botón)**: extraída la lógica a `ReminderRules.snooze(task, now, minutes=10)` (dominio puro, `app/src/main/java/com/ordia/app/domain/ReminderRules.kt`). Devuelve `SnoozeResult(triggerAt = now + minutes, task = task.copy(updatedAt = now))` — **NO** toca `reminderAt`/`dueAt`/`startAt`. El aplazamiento es transitorio: `triggerAt` se agenda con `ReminderScheduler.scheduleAt(taskId, triggerAt)` y persiste en WorkManager (sobrevive a reinicios). `ReminderActionReceiver` ahora delega a `ReminderRules.snooze` en vez de mutar `reminderAt`. Verificado: único path de snooze (`grep ACTION_SNOOZE` → solo `TaskReminderWorker` construye el intent + `ReminderActionReceiver` consume).
- **Tests**: +6 en `ReminderRulesTest.kt`: `snoozePreservesOriginalReminderAt`, `snoozeDoesNotModifyDueAtOrStartAt`, `snoozeTriggerAtIsNowPlusMinutes`, `snoozeDefaultMinutesIsTen`, `snoozeUpdatesUpdatedAt`, e invariante de integridad `snoozeThenComplete_preservesReminderOffsetAcrossRecurrence` (reproduce el bug: reminderAt=dueAt-15min, snooze 10min, completar → `RecurrenceEngine.nextOccurrence` produce offset 15min; antes del fix colapsaba a 5min). **421 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 415 base c.51 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, `ReminderScheduler` real/WorkManager, receptor de broadcast en dispositivo Android.
- **Archivos modificados/creados**: `app/src/main/java/com/ordia/app/domain/ReminderRules.kt` (NEW), `app/src/test/java/com/ordia/app/domain/ReminderRulesTest.kt` (NEW), `app/src/main/java/com/ordia/app/reminders/ReminderActionReceiver.kt` (MODIFIED), `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `d384c2c` (fix(reminders): snooze ya no corrompe reminderAt en tareas recurrentes), `de571d6` (docs).
- **HEAD final**: `de571d6` (push a openhands/autonomous-ordia OK; `d18fc32..de571d6`, fast-forward sin colisión).

### Siguiente
- Descubrimiento continuo: auditar captura, recordatorios (scheduling real, cancelación al completar/archivar), detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad.
- Posible: revisar si el `ReminderScheduler.scheduleAt` realmente persiste el snooze a través de reinicios (WorkManager vs AlarmManager); auditar cancelación de recordatorios al completar/archivar una tarea (evitar notificaciones de tareas ya hechas).
- Parser: manejo robusto de múltiples marcadores temporales en una frase.

---
## Ciclo 51 - 2026-08-13 (UTC) - fix: DayPlanner no marca conflicto de hora si startAt es otro día
- **Run/ciclo**: 51.
- **HEAD inicial**: `4f7e701` (base local al iniciar; el remoto ya había avanzado a `497010f` con el ciclo 50 del parser "de aquí a N" — STALE_BASE detectado al fetch).
- **Colisión evitada**: al hacer `git fetch` el remoto estaba en `497010f` (ciclo 50 = parser, otro run). Mi base local `4f7e701` era obsoleta. NO se forzó push. Se hizo stash del trabajo, fast-forward a `497010f`, y se re-aplicaron SOLO los cambios de código (`DayPlanner.kt`/`DayPlannerTest.kt`) que el remoto NO tocó (sin conflicto de código). Los docs se reescribieron sobre la base remota. Trabajo del ciclo 50 del otro run preservado íntegro.
- **Problema seleccionado**: `DayPlanner.build` marcaba conflicto `MOVED_FROM_SCHEDULED_TIME` comparando solo `hour*60+minute` del `startAt` original contra el `startMinute` del bloque planificado, **sin verificar que el `startAt` cayera en el día del plan**. El comentario decía "tareas que ya tenían hora prevista **ese día**" pero la implementación lo aplicaba a cualquier tarea con `startAt`. Una tarea **empezada ayer** (startAt ayer 15:00) que **vence hoy** era reubicada por el plan de hoy y marcada falsamente como "hora movida", aunque nunca tuvo hora asignada *hoy*. Patrón real: una tarea iniciada el día previo arrastra su vencimiento al día siguiente.
- **Prioridad**: P2 (fiabilidad de detección de conflictos; evitar conflicto espurio en planificador).
- **Causa raíz**: la comparación omitía la componente de fecha; `original.toLocalDate() != date` no se comprobaba.
- **Solución (mínima, `DayPlanner.kt`, sin nueva pantalla/botón)**: antes de comparar la hora se verifica `original.toLocalDate() == date`. Si el `startAt` es de otro día, no hay hora prevista "ese día" y no se añade el conflicto. Si cae en el mismo día, el comportamiento previo se conserva (hora distinta → conflicto real). Lógica local honesta, alineada con la intención documentada en el comentario.
- **Tests**: +2 en `DayPlannerTest.kt`: `noConflictWhenStartAtIsOnADifferentDay` (startAt ayer → 0 conflictos) + `conflictStillReportedWhenStartAtIsOnSameDay` (startAt hoy 15:00, plan lo ubica 9:00 → conflicto real). **415 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — 413 base remota c.50 + 2 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del planner en pantalla (sin Android SDK).
- **Colisión 2 evitada (rebase no destructivo)**: al hacer `git fetch` previo al push, el remoto había avanzado `497010f`→`b3fc5f7` (otro run: `fix(manifest): resolve FileProvider merger conflict blocking previewAdvanced CI`). Mi commit local divergía (1 ahead / 1 behind). NO se forzó push. Se hizo `git rebase origin/openhands/autonomous-ordia` (rebase sobre commit propio, no sobre rama compartida de humano): cero conflictos de código (áreas ortogonales: planner vs manifest/previewAdvanced), 415 tests PASS re-confirmados, smoke 25 OK tras rebase.
- **Archivos modificados**: `DayPlanner.kt`, `DayPlannerTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `bf11f31` (push a openhands/autonomous-ordia OK; `b3fc5f7..bf11f31`, fast-forward tras rebase).

### Siguiente
- Descubrimiento continuo: auditar captura, recordatorios, detección de vencidas importantes, contexto, onboarding, navegación, accesibilidad, rendimiento, privacidad.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).


## Ciclo 50 - 2026-08-13 (UTC) - parser: "de aquí a N"/"de acá a N" prefijo relativo coloquial

- **HEAD inicial**: `8950d07` (synced tras primer pull; remoto `bf3579d`→`8950d07`).
- **Remoto avanzó 3x durante el run** (rutina segura stash/pop, sin colisión, ver abajo):
  (1) `bf3579d`→`8950d07`: guardián doble conteo, WhatNow IMMINENT_START, nextBestTask compromisos.
  (2) `8950d07`→`4cb5f0f`: DayPlanner "Vence hoy" c.48 (otro run) + docs follow-up.
  (3) `4cb5f0f`→`4f7e701`: SearchEngine ranking urgencia c.49 (otro run) + docs follow-up.
  En todos los casos el remoto **no tocó** `NaturalTaskParser.kt` ni su test → stash pop limpio.
  En la 3ra colisión mi commit previo (`ccf7f36`, etiquetado c.49) ya no fast-forwardeaba: hice
  `git reset --soft HEAD~1`, descarté docs, re-sync, stash pop, rehice docs como c.50.
- **Problema seleccionado**: P1 parser/captura. `relativePattern` solo admitía `en`/`dentro de`;
  las formas coloquiales **"de aquí a N ..."** / **"de acá a N ..."** no casaban → `dueAt=null`,
  residuo en el título, tarea sin recordatorio, invisible en planificador/What Now → olvidada.
- **Causa raíz**: grupo de prefijos del regex demasiado estrecho; faltaba el prefijo coloquial
  futuro más común del español.
- **Solución (mínima)**: extendido el grupo de prefijos de `relativePattern`
  `(?:en|dentro\s+de)` → `(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)`
  (variantes con/sin tilde). Resto del patrón y lógica de cálculo reutilizados sin cambios.
- **Heurística honesta**: regex determinista, no IA/random, no llama "IA" a una regla.
- **Tests**: `bash tools/run_domain_tests.sh` = **413 PASS** (408 base remota c.49 incl. SearchEngine
  + 5 nuevos), 25 clases. `bash tools/run_domain_checks.sh` smoke 25 OK. Nuevos:
  `deAquiATresDiasParsesDueAt`, `deAquiAUnaSemanaParsesDueAt`, `deAquiAUnMesParsesDueAt`,
  `deAquiANDiasRespetaHoraExplicita`, `deAcaAUnaSemanaParsesDueAt`. Probe ad-hoc pre-test OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`,
  `CURRENT_STATE.md`, `BACKLOG.md`, `RUN_LOG.md`.
- **Etiquetado ciclo 50**: dos runs paralelos ya usaron c.48 (DayPlanner) y c.49 (búsqueda);
  ciclo 50 evita colisión de numeración. Sin force push, sin reset --hard, sin tocar `main`.
- **Commits**: `feat(parser): "de aquí a N"/"de acá a N" prefijo relativo coloquial` → `877765c` (push OK a `openhands/autonomous-ordia`; `4f7e701..877765c`).
- **HEAD final**: `877765c`.
- **Próxima prioridad**: seguir auditando parser (prefijos pasados coloquiales, "al rato"/
  "en un rato", "esta noche"/"anoche" relativas; recurrencias "cada otros N ..."; nominativos
  "el próximo lunes" vs "lunes que viene") y otras áreas (rutinas, recordatorios, What Now,
  backup/restore, concurrencia workers).


---
## Ciclo 49 - 2026-08-13 (UTC) - search ranking accionable (urgencia sobre orden alfabético)

- **Run/ciclo**: 49 (búsqueda/inteligencia — fuera del parser). Continúa la línea "potencia sin más interfaz".
- **HEAD inicial**: `8950d07` (base de captura). Durante el run el remoto avanzó a `4cb5f0f` (ciclo 48: fix "Vence hoy" falso en planificador). Colisión no destructiva: `git stash` → `git pull --ff-only` → `git stash pop`, auto-merge limpio (cambios ortogonales: remoto tocó `DayPlanner.kt`/`PlannerScreen.kt`/strings/test, este run toca `SearchEngine.kt`). Sin force push, sin reset --hard.
- **Problema seleccionado (P2, producto/UX — búsqueda)**: `SearchEngine.search` ordenaba los resultados por **prefijo del título y luego alfabético**, sin considerar urgencia. Al buscar "reunión" con dos tareas "Reunión equipo" —una **atrasada y urgente**, otra normal sin fecha— el orden dependía solo del alfabeto, así que la crítica podía quedar debajo. La búsqueda devolvía "matches" sin priorizar lo accionable, contrario al principio de Ordía ("qué hacer ahora" eleva lo atrasado/urgente en todas las superficies).
- **Causa raíz**: el `sortWith(compareBy { prefix }.thenBy { title })` final operaba solo sobre `SearchResult` (kind/id/title/subtitle), que **no transporta** prioridad/fecha/estado; el filtrado ya descartaba atrasadas, pero el ordenado ignoraba la urgencia disponible en el `TaskEntity` origen.
- **Prioridad**: P2 (mejora funcional potente de una superficie existente; no pérdida de datos ni crash).
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**: wrapper interno `Ranked(result, urgency, dueAt)` calculado al construir cada resultado. `urgencyRank(task, now)` reutiliza `TaskRules.isOverdue`/`isDueToday` (fuente única de verdad) en orden honesto idéntico a `nextBestTask`: atrasada+urgente > atrasada > urgente+vence-hoy > urgente > alta > vence-hoy > resto. Orden final: **prefijo de título** (relevancia textual primero) → **urgencia** → **dueAt** → **alfabeto**. No-tareas tienen urgencia neutral (6), así que un proyecto/nota que **prefija** el query sigue ganando (la relevancia textual domina); una tarea atrasada que también prefija sube por encima. Heurística local honesta (no IA simulada).
- **Tests**: +2 (`urgencyRanksOverdueAheadOfAlphabeticalMatches`: dos tareas mismo título, la atrasada+urgente primera; `textPrefixStillBeatsUrgencyForDifferentTitles`: proyecto "Toolisto" prefija y sigue ganando sobre tarea "Revisar Toolisto" urgente — preserva el invariant del test `search_coversAllCoreContent`). **408 domain tests PASS** (`bash tools/run_domain_tests.sh`; 406 base remota c.48 + 2 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real de `SearchScreen`.
- **Archivos modificados**: `SearchEngine.kt`, `SearchEngineTest.kt`, `CURRENT_STATE.md`, `RUN_LOG.md`.
- **Commits**: `feat(search): ranking por urgencia ante orden alfabético` → `bc4f45d` (push OK a `openhands/autonomous-ordia`; `4cb5f0f..bc4f45d`).
- **HEAD final**: `bc4f45d`.
- **Próxima prioridad**: fuera del parser. Candidatos: `SearchEngine` could surface a "vencidas" quick-filter chip; auditar `SummaryEngine`/`DayPlanner` para oportunidades de simplificación; detección de vencidas importantes; acciones rápidas de captura.

---
## Ciclo 46 - 2026-08-13 (UTC) - guardián: atrasados cuenta solo tareas raíz (consistencia con resumen)

- **Run/ciclo**: 46 (inteligencia/consistencia, no parser).
- **HEAD inicial**: `e0850e6` (base `openhands/autonomous-ordia` sincronizada al iniciar; clean). Durante el run el remoto avanzó a `966b799` (ciclo 45 del otro run: `nextBestTask` time-aware + parser listas bare). Colisión no destructiva: `git stash` → `git pull --ff-only` → `git stash pop`, auto-merge limpio (cambios ortogonales: remoto tocó `NaturalTaskParser.kt`/`TaskRules.kt`, este run toca `GuardianEngine.kt`). Sin force push, sin reset --hard.
- **Problema seleccionado (P1, consistencia/inteligencia)**: `GuardianEngine.snapshot` contaba los atrasados (`overdue`) incluyendo subtareas: un padre atrasado con 2 subtareas también atrasadas → el guardián veía **3** atrasados, mientras la tarjeta de resumen (`SummaryEngine`, que filtra `parentTaskId == null`) mostraba **1**. Dos superficies daban números contradictorios al usuario. El invariant "las subtareas son anidadas, no se cuentan además del padre" ya estaba fijado en `SummaryEngine` (test `overdueCountsRootTaskNotNestedSubtasks`, ciclo previo) pero **no** en el guardián.
- **Causa raíz**: `val overdue = tasks.count { !it.completed && !it.archived && TaskRules.isOverdue(it, nowMillis) }` no filtraba `parentTaskId == null`, a diferencia del conteo equivalente en `SummaryEngine.summarize`. El `overdue` del guardián alimenta el ánimo (`CONCERNED` si `>= 5`), el mensaje ("Hay N pendientes atrasados") y la acción sugerida → la inflación era visible y afectaba el "coach".
- **Prioridad**: P1 (inteligencia/coach con datos inconsistentes respecto al resumen; no pérdida de datos, pero información errónea al usuario en una superficie de acompañamiento).
- **Solución (mínima, `GuardianEngine.kt`)**: filtrar `it.parentTaskId == null` en el conteo de `overdue` (alineado con `SummaryEngine`). Además, exponer `overdue` en `Snapshot` (campo `overdue: Int` añadido al final de la data class, retrocompatible: nadie construye `Snapshot` posicionalmente; la UI lee por nombre) para que el invariant sea verificable y la superficie pueda mostrar el mismo número que el resumen. `completedToday`/XP siguen contando subtareas a propósito (progreso granular deliberado) — solo el conteo de atrasados se alinea con la definición global.
- **Tests**: +1 (`overdueCountsOnlyRootTasksNotNestedSubtasks`: padre + 2 subtareas atrasadas → `overdue==1`, ánimo `CURIOUS` no `CONCERNED` (umbral 5), `suggestedAction` reacciona al atrasado). **391 domain tests PASS** (`bash tools/run_domain_tests.sh`; 388 base remota c.45 + 3 del run paralelo `nextBestTask` + 1 nuevo), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del guardián/overlay.
- **Commits**: `fix(guardian): overdue cuenta solo tareas raíz (consistencia con resumen)` → `6d0c6a4` (push OK a `openhands/autonomous-ordia`).
- **HEAD final**: `6d0c6a4`.
- **Próxima prioridad**: seguir fuera del parser. Candidatos: auditar `SubtaskRules`/`RoutineRules`/`HabitRules` para invariantes de conteo análogos; revisar `WhatNowEngine` vs `nextBestTask` time-aware para coherencia total; oportunidades de captura ultrarrápida.

## Ciclo 46 (run paralelo) - 2026-08-13 (UTC) - What Now detecta compromisos a punto de empezar (IMMINENT_START)

- **Run/ciclo**: 46 (inteligencia del "¿Qué hago ahora?" — evitar olvidos de compromisos inminentes). Renumerado desde 44 tras colisión de remoto (los ciclos 44–45 fueron reclamados por un run paralelo: "la quincena" + `nextBestTask` time-aware).
- **HEAD inicial**: `e0850e6` (mi base de captura); al hacer push el remoto había avanzado a `966b799`. `git fetch` + `git rebase origin/openhands/autonomous-ordia` (no destructivo, sin force): auto-merge limpio en `WhatNowEngine.kt`/`TodayScreen.kt`/strings/test; conflicto solo en `CURRENT_STATE.md` resuelto conservando el trabajo del otro run.
- **Problema seleccionado (P1, What Now)**: un compromiso programado con `startAt` futuro (reunión/llamada/cita) que empieza en pocos minutos **no aparecía** en "¿Qué hago ahora?": el ranking lo trataba como `SCHEDULED_LATER` (rank -1, último recurso) siempre que `startAt > now`, sin distinguir 5 min de 5 h. Una reunión a las 10:05 escrita a las 10:00 quedaba enterrada bajo una tarea cualquiera de la Bandeja — olvido de compromiso inminente.
- **Prioridad**: P1 (recuperación de compromiso importante a punto de empezar; evita olvidos sin nueva interfaz).
- **Causa raíz**: `WhatNowEngine.rank()`/`reason()` solo distinguían `startAt > now` → SCHEDULED_LATER; no existía gradación por proximidad temporal.
- **Solución (mínima, `WhatNowEngine.kt`)**: nuevo `WhatNowReason.IMMINENT_START` (rank 4, entre OVERDUE y DUE_TODAY) + `isImminentStart(task, now)` (`startAt` futuro y dentro de `IMMINENT_WINDOW_MINUTES = 15`). Las que empiezan más allá siguen como SCHEDULED_LATER. Orden honesto: atrasada > inminente > vence hoy. UI: etiqueta `what_now_reason_imminent`="Empieza enseguida" en `TodayScreen.kt` (rama `when` exhaustiva). Sin nueva pantalla/botón.
- **Tests**: +4 (`imminentStartSurfacesAboveInbox`, `startOutsideImminentWindowStillDeprioritized`, `overdueStillBeatsImminentStart`, `imminentStartBeatsDueTodayWithoutStart`). **392 domain tests PASS** (388 base remota c.45 + 4 nuevos), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `WhatNowEngine.kt`, `TodayScreen.kt`, `strings_screens1.xml`, `WhatNowEngineTest.kt`, `CURRENT_STATE.md`, `RUN_LOG.md`.
- **Commits**: (pendiente de push).
- **HEAD final**: (pendiente).


---
## Ciclo 42 (cont. 2) - 2026-08-13 (UTC) - día de semana suelto hoy con hora futura vence hoy

- **Run/ciclo**: 42 (continuación 2 — fix P1 de captura de citas de hoy).
- **HEAD inicial**: `727e7b8` (remoto actualizado por run paralelo). Workspace arrancó en `0a77387`; al hacer `git fetch` + `git pull --ff-only` el remoto había avanzado a `727e7b8`. Tras commit local, el push se rechazó por divergencia (más trabajo remoto); `git fetch` + `git rebase origin/openhands/autonomous-ordia` (no destructivo, sin force) integró limpio.
- **Problema seleccionado (P1, parser)**: **"el viernes a las 18"** escrito el propio viernes **antes** de las 18:00 se programaba para el **viernes de la semana siguiente**: la cita de hoy se perdía una semana entera (reunión/cita olvidada hoy, recordatorio tardío 7 días). Causa raíz: la rama de fecha suelta usaba `nextWeekday`, que **siempre** salta +7 cuando el día objetivo es hoy (comportamiento correcto para recurrencias, que necesitan el "próximo" estricto, pero incorrecto para una fecha suelta puntual). No existía un path para "hoy si aún no llegó la hora".
- **Prioridad**: P1 (pérdida/desplazamiento de cita del día → recordatorio 1 semana tarde; dato visible incorrecto en planificador/What Now).
- **Solución (mínima, `NaturalTaskParser.kt`)**: nueva `nextWeekdayOrSame(from, target)` — devuelve hoy si el día objetivo coincide con el de `from`, si no delega a `nextWeekday`. La rama de fecha suelta (weekday blando) pasa a usar `nextWeekdayOrSame`; el descarte de "hoy si la hora ya pasó" se difiere al momento de combinar fecha+hora: si fecha+hora resulta pasada respecto a `now`, se rueda +7 días. Así no se agenda en pasado y no hay regresión. Las recurrencias siguen usando `nextWeekday` (próximo estricto, sin cambio). Heurística honesta (no IA).
- **Tests**: +4 (`weekdayHoyConHoraFuturaQuedaHoy`, `weekdayHoyConHoraPasadaRuedaProximaSemana`, `weekdayHoySinHoraYMediodiaPasadoRuedaProximaSemana`, `weekdayHoyTardeConHoraFuturaQuedaHoy`). Confirmado PASS. **362 domain tests PASS** (358 base remota + 4 nuevos; tras rebase sobre `727e7b8` que añadió 5 tests del run paralelo), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `fix(parser): día de semana suelto hoy con hora futura vence hoy` (`b4ef5e4`).
- **HEAD final**: `b4ef5e4` (push a openhands/autonomous-ordia OK; `727e7b8..b4ef5e4`).

### Siguiente
- Continuar ciclo interminable. Candidatos parser (P2): "la quincena" como hito financiero (día 15/fin de mes); "próximo bimestre/semestre".
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Explorar captura/What Now/rutinas en busca de oportunidades de producto.

---
## Ciclo 42 (cont. 3) - 2026-08-13 (UTC) - parser listas de días sin prefijo ("gym sábados y domingos")

- **Run/ciclo**: 42
- **HEAD inicial**: `e4157c1` (base `openhands/autonomous-ordia` sincronizada; clean al iniciar). Al finalizar, colisión con runs paralelos: el remoto avanzó varios commits (rango horario sin "horas", recurrencia quincenal "cada quincena", día de semana suelto hoy con hora futura — ciclo 42). Se resolvió con `git fetch` + `git rebase` (no destructivo: mi commit aún no pusheado) y resolviendo conflictos de docs (BACKLOG/CURRENT_STATE/RUN_LOG) combinando ambos conjuntos. Sin force push, sin reset --hard, sin STALE_RUN destructivo.
- **Problema seleccionado (P1)**: `NaturalTaskParser` perdía la recurrencia de listas de días **sin prefijo** ("gym sábados y domingos", "fútbol domingos a las 18", "lavar auto sábados domingos"). `parseRecurrence()` solo casaba listas con "los"/"cada"/"todos los", así la forma bare caía `recurrence=NONE` y los días quedaban como residuo en el título → la rutina semanal se olvidaba en silencio (pérdida de datos: recordatorios nunca disparaban). La forma bare es tan común como la prefijada en español hablado/escrito.
- **Causa raíz**: el patrón de recurrencia exigía un artículo prefijo para activar la rama de lista de días; sin él no se probaba la rama de weekday-list.
- **Prioridad**: P1 (pérdida de datos silenciosa en rutinas).
- **Solución (mínima, `NaturalTaskParser.kt` — `parseRecurrence()`)**: reconocer listas bare de 2+ días como WEEKLY sin artículo; día plural marcado ("domingos"/"sábados") en solitario también es recurrencia (hábito semanal explícito). Día suelto **no plural** ("reunión martes") sigue siendo **fecha única** (ambiguo: no programar rutina equivocada). `dayNameRegex` evita falsos positivos (solo consume nombres de día reales, no roba texto ajeno).
- **Tests**: +4 (`parsesBareDayListRecurrence`, `parsesBareDayListWithExplicitTime`, `parsesBarePluralSingleDayRecurrence`, `bareSingleNonPluralDayIsNotRecurrence`). Tras integrar la base remota de los runs paralelos (incl. c.43 "entre semana" + c.44 "la quincena"): **388 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Verificado con probe JVM antes de integrar a la suite oficial.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): reconocer recurrencia de listas de días sin prefijo ("gym sábados y domingos")`.
- **Colisión de remoto (2.ª y 3.ª rebase, no destructiva)**: el remoto avanzó repetidamente durante el run (c.43 "entre semana"/"de lunes a viernes" = WEEKLY [1-5]; c.44 fecha/hito "la quincena"). En cada caso `git fetch` + `git rebase`: auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales); conflictos solo en docs (`CURRENT_STATE.md`, `RUN_LOG.md`, `BACKLOG.md`) resueltos combinando ambos conjuntos y sumando los tests de cada ciclo (369→376→388). Sin force push, sin reset --hard.
- **HEAD final**: (ver commit tras push).
- **Próxima prioridad**: "la quincena" ya resuelto por c.44 (run paralelo). Re-auditar parser en busca de gaps reales (ej. "a finales de este mes" date regression reportada en probe); revisar áreas no-parser (captura, What Now, rutinas, backup) para mayor impacto de producto.

---
## Ciclo 42 (cont. - run paralelo) - 2026-08-13 (UTC) - rango horario sin "horas": ampliación de followers seguros

- **Run/ciclo**: 42 (continuación â colisión con run paralelo resuelta, mejora aditiva sobre el fix base del ciclo 42).
- **HEAD inicial**: `91c8b9f` (base del workspace al iniciar). Al hacer `git fetch` el remoto había avanzado a `0a77387` ("feat(parser): rango horario sin unidad 'horas' cuando ambas horas < 13"): un run paralelo resolvió el **mismo** backlog item con un enfoque equivalente (3 tests, set de followers básicos). Descarté mi implementación competidora vía `git stash` (luego `git stash drop`) + `git pull --ff-only` (sin force push, sin reset --hard, sin STALE_RUN destructivo) y reconstruí sobre `0a77387` aportando una **mejora aditiva no duplicativa**.
- **Problema seleccionado (P2, parser)**: el fix base del rango horario sin "horas" dejaba residuo ("de 9 a 11" en el título, `durationMinutes=null`) en tres clases de frases cotidianas: rango + día de la semana ("clase de 9 a 11 el viernes"), rango + día relativo ("taller de 10 a 12 mañana") y rango + parte del día con conector no listado ("curso de 4 a 6 a la tarde", "turno de 9 a 11 por la noche"). Causa raíz: el regex `followedByCount` del fix base sólo incluía conectores básicos (con/y/o/para/hasta/luego/después/pero/porque + puntuación), así que esos tokens iniciaban "sustantivo de cantidad" falso â el rango se rechazaba.
- **Prioridad**: P2 (captura incorrecta + título sucio; no pérdida de datos, pero fricción).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: ampliar el regex de followers seguros con artículos (el/la/los/las/un/una), a/al, por, sin, sobre, desde, del, días de la semana (lunesâ¦domingo, con y sin acento) y días relativos (mañana/manana, hoy, ayer, anteayer). El rechazo de "comprar de 2 a 5 entradas"/"reunión de 2 a 5 personas" se preserva (sustantivo contable sigue fuera del set). Heurística honesta, conservadora.
- **Tests**: +5 TDD (`bareRangeSmallHoursFollowedByWeekdayParsesDuration`, `â¦FollowedByRelativeDayParsesDuration`, `â¦FollowedByATardeParsesDuration`, `â¦FollowedByPorLaNocheParsesDuration`, `â¦FollowedByCountableNounStillRejected`). Confirmado FAIL previo (4 fallos) contra la base remota, luego PASS. **358 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (353 base remota + 5 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): rango horario sin "horas" â ampliar followers seguros (día semana/parte del día)`.
- **HEAD final**: `b4ef5e4`.

### Siguiente
- Continuar ciclo interminable. Candidatos parser (P2): "la quincena" como hito financiero (día 15/fin de mes); "próximo bimestre/semestre"; "mediados de semana" ya hecho.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---
## Ciclo 42 - 2026-08-13 (UTC) - recurrencia quincenal con palabra ("cada quincena")

- **Run/ciclo**: 42
- **HEAD inicial**: 4446852 (docs ciclo 41). Colisión de remoto: durante el stash+ff el remoto avanzó a `edea354` (run concurrente que resolvió el rango horario sin "horas", ambos < 13, en el mismo ciclo 42). Resolución no destructiva: se hizo `git stash` + `pull --ff-only` + `git stash pop`; conflictos en BACKLOG.md y CURRENT_STATE.md resueltos combinando ambos logros (rango horario remoto + recurrencia quincenal mía). Sin STALE_RUN, sin force push, sin reset --hard.
- **Problema seleccionado (P1)**: `NaturalTaskParser` no reconocía la **recurrencia quincenal con palabra** ("nómina cada quincena", "reporte quincenalmente", "todas las quincenas"). Causa raíz: `intervalPattern` (`cada\s+(\d{1,3})\s*(días|semanas|meses|años)`) **solo admite dígitos**; la forma con la palabra "quincena" no casaba con ningún patrón → `recurrence=NONE` y, por el anclaje del ciclo 19 (que solo aplica si `frequency != NONE`), `dueAt=null`. Resultado: la tarea recurrente nacía **invisible** — sin fecha, sin recordatorio (`reminderAt ?: dueAt` ambos null), ausente de What Now/planificador → nóminas/pagos quincenales se olvidaban. La quincena (cada ~15 días / día 15 y fin de mes) es una cadencia financiera/laboreal muy común en español.
- **Prioridad**: P1 (evitar olvidos de tareas recurrentes cotidianas; datos invisibles).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevo patrón dedicado `cada quincena|quincenalmente|todas las quincenas` → `RecurrenceResult(WEEKLY, interval=2, …)` (cada 2 semanas ≈ quincena), colocado **antes** de `fixedPatterns`. Se procesa en `parseRecurrence` (que ya corre antes que las fechas), así que la frase se borra del título y reutiliza el anclaje a fecha de captura del ciclo 19. **Sin añadir enum ni migración**: WEEKLY+interval=2 es representación honesta y reutiliza el avance semanal existente. Sin riesgo de falsos positivos (palabra específica, no ambigua).
- **Tests**: +3 (`cadaQuincenaParsesBiweeklyRecurrence`, `quincenalmenteParsesBiweeklyRecurrence` con hora, `cadaQuincenaRespetaFechaExplicita` con `el 15/8`). **365 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (362 remoto tras segundo rebase sobre `b4ef5e4` + 3 quincenal míos). Smoke 25 OK (`tools/run_domain_checks.sh`). No se redujeron ni eliminaron tests.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: "la quincena"/"próxima quincena" como **fecha absoluta** (día 15 / fin de mes) sigue OPEN (P2): `nextPeriodPattern` cubre "próxima quincena" como +15d relativo, pero no como hito anclado al 15. Rango horario sin "horas" ("clase de 9 a 11") fue **resuelto por el run concurrente** en este mismo ciclo 42 (ambas horas < 13, heurística honesta de fin de cadena/conector vs sustantivo de cantidad) — consolidado en CURRENT_STATE/BACKLOG.
- **AI_AUTONOMY actualizado**: CURRENT_STATE (ciclo 42, 365 tests, ambos logros combinados), BACKLOG (entrada P1 FIXED + marcado parcial en item P2 quincena), RUN_LOG (esta entrada).
- **Commits**: `feat(parser): recurrencia quincenal "cada quincena"/"quincenalmente" (WEEKLY x2)`.
- **HEAD final**: `97ec260` (push a openhands/autonomous-ordia exitoso; rebase sobre `b4ef5e4` del run concurrente, 365 tests PASS, smoke 25 OK).

### Siguiente
- Parser P2: "la quincena"/"próxima quincena" como fecha anclada al 15 (evaluar si aporta vs +15d actual).
- Continuar descubrimiento: auditar What Now, rutinas, recordatorios en busca de P1 reales.
- Rango horario sin "horas": solo si se encuentra señal honesta fuerte (no lista de sustantivos frágil).

---
## Ciclo 38 - 2026-08-13 (UTC) - fechas pasadas + recuperación de fechas imposibles

- **Run/ciclo**: 38
- **HEAD inicial**: bdd3dc0 (base inicial del workspace). El remoto ya estaba en `9ac1a8b` (5 commits por delante de la base local: "mediados de semana", "un par de", docs, y más). Se hizo `git fetch` + rebase de mis 2 commits locales (`5a67a47`, `b0a33a7`) sobre el remoto. Durante el run el remoto avanzó una vez más (`f2d26ba`, ciclo 37 "a las N horas"): segundo rebase, sin conflictos. Procedimiento no destructivo en ambos casos; sin STALE_RUN, sin force push, sin reset --hard.
- **Problema seleccionado (2 unidades atómicas, P1)**:
  1. **Fechas pasadas "hace N"/"la semana/el mes pasado"** (commit `5a67a47`→`ff3a1f4`): `NaturalTaskParser` no reconocía fechas pasadas cotidianas ("pagué hace 2 días", "revisé el informe la semana pasada", "reunión el mes pasado"). Causa raíz: `agoPattern` ("hace N") no existía; `lastPeriodPattern` ("la semana/el mes/el año pasado") no existía y además `previousWeekdayPattern` ("el mes pasado") capturaba la frase, dejando grupo1="mes" (no es día → sin fecha) y **borraba** la frase del título. Resultado: `dueAt=null` (tarea sin recordatorio, invisible en What Now/planificador) **Y** frase temporal como basura en el título. También "hace poco"/"hace un rato" (coloquial = "recién") no se resolvían. Solución: nuevos `agoPattern` (resta N días/semanas/meses/años, o 3h para "poco"/"un rato") y `lastPeriodPattern` (resta 7d/30d/365d), detectados **antes** de `previousWeekdayPattern`, integrados al inicio de la cadena `effectiveRelativeDueAt` (las fechas pasadas son explícitas y tienen prioridad sobre fechas futuras ambiguas); la hora explícita se aplica sobre la fecha pasada (tarea vencida con hora).
  2. **Recuperación de fechas imposibles** (commit `b0a33a7`→`265fc93`): `parseMonthNameDate` usaba `LocalDate.of(year, month, day)` que lanza `DateTimeException` para fechas imposibles ("el 29 de febrero" en año no bisiesto, "el 31 de abril"). El `runCatching` devolvía `null` → caía al fallback que **deja la frase temporal en el título** y `dueAt=null` (tarea sin fecha y con basura). El usuario que escribe "el 29 de febrero" claramente quiere una fecha real, no perderla. Solución: en vez de descartar, **recuperar** con `Year`/`YearMonth`: Feb 29 no bisiesto → siguiente año bisiesto (2028); día > máx del mes (31 abr) → clamp al último día válido del **siguiente año** (30 abr 2027); Feb 30 → Feb 28. Así la frase se reconoce, se borra del título y la tarea obtiene una fecha útil (no se pierde).
- **Prioridad**: P1 (evitar olvidos: tareas sin recordatorio + títulos sucios; datos erróneos por fechas perdidas).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevos patrones `agoPattern`/`lastPeriodPattern` + variables `agoDueAt`/`lastPeriodDueAt` al inicio de `effectiveRelativeDueAt`; refactor de `parseMonthNameDate` con `java.time.Year`/`YearMonth` para clamp/recuperación. Imports añadidos: `java.time.Year`, `java.time.YearMonth`.
- **Tests**: +9 fechas pasadas (`haceNdiasResuelveFechaPasada`, `haceUnaSemanaResuelveFechaPasada`, `haceNmesesResuelveFechaPasada`, `haceNdiasConHoraAplicaHoraSobreFechaPasada`, `laSemanaPasadaResuelveFechaPasada`, `elMesPasadoResuelveFechaPasada`, `elMesPasadoConHoraAplicaHora`, `hacePocoResuelveHaceTresHoras`, `haceUnRatoLimpiaTituloSinResiduo`) + 5 fechas imposibles. **329 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **Colisión de remoto resuelta**: conflicto en `NaturalTaskParser.kt` (remote añadió `startOfWeekDueAt`/`midOfWeekDueAt`; local añadió `agoDueAt`/`lastPeriodDueAt`) resuelto combinando ambos conjuntos en la cadena `effectiveRelativeDueAt`. Conflicto en `NaturalTaskParserTest.kt` (ambos añadieron tests al final) resuelto conservando ambos conjuntos. Rebase posterior sobre `f2d26ba` sin conflictos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): fechas pasadas "hace N"/"la semana/el mes pasado" + limpieza titulo` (`ff3a1f4`); `fix(parser): recuperar fechas imposibles (29 feb, 31 abr) en vez de perderlas` (`265fc93`).
- **HEAD final**: `265fc93` (push exitoso a openhands/autonomous-ordia tras 2 colisiones de remoto resueltas no destructivamente).

### Siguiente
- Continuar ciclo interminable. Candidatos parser descubiertos (P2): rango horario sin palabra "horas" ("clase de 9 a 11"); números escritos en recordatorios relativos ("recuérdame dos horas antes" → offset null); "la quincena" como hito financiero.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 37 - 2026-08-13 (UTC) - "a las N horas" (hora, no duración falsa)

- **Run/ciclo**: 37
- **HEAD inicial**: 46efb3e (base inicial). Durante el run el remoto avanzó TRES veces por runs paralelos: 8146acf (quincena/bimestre/semestre), e4157c1 ("un par de"), y 9ac1a8b ("mediados de semana", ciclo 36). Procedimiento no destructivo en cada caso: stash+ff+pop sobre 8146acf; rebase sobre e4157c1 (conflicto solo en CURRENT_STATE.md, resuelto conservando ambas secciones, renum. 35→36); rebase sobre 9ac1a8b sin conflictos (renum. 36→37, ya que el remoto usó ciclo 36 para "mediados de semana"). Sin STALE_RUN destructivo, sin force push, sin reset --hard.
- **Problema seleccionado**: `NaturalTaskParser` interpretaba **"a las N horas"** como duración falsa. El `timePattern` no consumía el sufijo "horas", así que "9 horas" era robado por `durationMatch` (540 min falsos) y "a las" quedaba como residuo en el título. La tarea recibía una duración absurda y **ninguna hora real** → recordatorio/planificación incorrectos. Bug doble: la guardia añadida para descartar "N horas" como duración filtraba el ganador global tras `minByOrNull`, descartando TODOS los matches de duración (incluido "durante 1h" válido) cuando había un "N horas" inválido presente; además los conectores "durante"/"por" no se limpiaban del título.
- **Prioridad**: P1 (datos erróneos, recordatorio/planificación incorrectos, fricción de captura).
- **Causa raíz**: `timePatterns[0]` carecía de grupo opcional para el sufijo "horas"/"hs"; la guardia anti-"N horas" operaba sobre el resultado de `minByOrNull` (global) en vez de filtrar por-match antes; la limpieza de conector post-duración solo cubría "de".
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `timePatterns[0]` añade grupo opcional `(?:\s*(horas?|hs))?` tras la hora (con o sin meridiem): consume el sufijo "horas"/"hs" sin alterar la lógica AM/PM. Así "a las 9 horas" se reconoce y borra completo como frase temporal.
  - Guardia de duración refactorizada: filtro **por-match** ANTES de `minByOrNull`, descartando solo los matches "N horas" precedidos por frase temporal (`timePhrasePreceding`) y conservando válidos como "1h"/"2h".
  - Limpieza de conector extendida: tras extraer la duración se borra también "durante"/"por" (además de "de").
- **Tests**: +3 formales (`aLasNHorasEsHoraNoDuracion`, `aLasNHorasConFechaNoEsDuracion`, `duranteConnectorBeforeCompactDurationIsRemoved`) en `NaturalTaskParserTest.kt`. **315 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: fix(parser): "a las N horas" como hora, no duración falsa (b959764 → 3c773b2 tras rebase sobre e4157c1 → b842e00 tras rebase sobre 9ac1a8b). Renumerado 35→36→37 al detectar colisión de numeración con runs paralelos ("un par de"=35, "mediados de semana"=36).
- **HEAD final**: 7fa4056 (push exitoso a openhands/autonomous-ordia tras 3 colisiones de remoto resueltas no destructivamente).

### Siguiente
- Continuar ciclo interminable. Candidatos parser descubiertos en el probe (P2): rango horario sin palabra "horas" ("clase de 9 a 11" → sin duración); números escritos en recordatorios relativos ("recuérdame dos horas antes" → offset null).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---


## SESIÓN 000 — Bootstrap del sistema autónomo

- **Fecha (UTC)**: 2026-08-10
- **Trigger**: consolidación del rebuild de Codex y creación del sistema autónomo
- **Resultado**: ÉXITO

### Qué se hizo

1. Inventario completo del repo (ramas, status, remotes, pendientes de Codex).
2. Backup local del árbol de trabajo fuera del repo (`C:\Users\wsepulveda\AppData\Local\Temp\opencode`).
3. Auditoría de cambios de Codex (sin secretos; 0 hits en `JULES_API_KEY` / `TOKEN` / `KEY` en `app/src/main`, `app/src/test`).
4. Validación del rebuild:
   - `./gradlew test` → 6 variantes verdes.
   - `./gradlew lintPreviewSafeDebug` → 2 errores corregidos.
   - `./gradlew assembleDebug assembleRelease` → verdes.
5. Consolidación del rebuild en 9 commits coherentes + publicación de `feature/ordia-total-rebuild-2026-08-10`.
6. Creación y publicación de `jules/autonomous-ordia` (HEAD `d34ffd8`, branch autónoma permanente).
7. Creación de memoria persistente `AI_AUTONOMY/` (MISSION, CURRENT_STATE, BACKLOG, DECISIONS, RUN_LOG, AGENTS).

### Problemas encontrados

- `ContextPrivacyFilter` no filtraba fragmentos de paquete sin punto (banca genérica) → corregido + test.
- 2 errores de lint (`StartActivityAndCollapseDeprecated`, `stringResource` fuera de composable) → corregidos.

### Commits creados

- `143e8e2..d34ffd8` (9 commits de consolidación sobre la rama de publicación)

### Evidencia

- `git log --oneline -11` tras la sesión: 9 commits de consolidación + 1 de docs previo.
- Ramas remotas: `feature/ordia-total-rebuild-2026-08-10` y `jules/autonomous-ordia` (ambas en `d34ffd8`).

---

## SESIÓN 001 — Actualización del workflow autónomo Jules

- **Fecha (UTC)**: 2026-08-10 (segunda parte del bootstrap)
- **Trigger**: continuar plan de sistema autónomo (fases 8-31)
- **Resultado**: ÉXITO (parcial — sin `gh` local ni clave de API no se puede ejecutar un ciclo de prueba)

### Qué se hizo

1. Descubierto que `origin/main` ya contenía `.github/workflows/ordia-autonomous-jules.yml`
   (creado en `182f80d`, reparado en `9d05dd2`) — la rama del rebuild divergió antes y NO lo incluía.
2. Leído el workflow existente: apuntaba a `main` como rama inicial (INCORRECTO para el nuevo modelo).
3. Creado `.github/workflows/ordia-autonomous-jules.yml` actualizado en `jules/autonomous-ordia`:
   - **Rama de trabajo**: `jules/autonomous-ordia` (nunca `main`).
   - **Cron**: cada 2 horas en el minuto 17 (`17 */2 * * *`) + `workflow_dispatch`.
   - **Failsafe variable**: `vars.ORDIA_AUTONOMY_ENABLED` (false/0/no/off → no lanza).
   - **Failsafe archivo**: `AI_AUTONOMY/AUTONOMY_BYPASS` → no lanza.
   - **Session lock**: comprueba PRs abiertas hacia `jules/autonomous-ordia`; si hay, no lanza.
   - **Verificación de rama en la API de Jules** antes de lanzar (la rama debe existir en el conector).
   - **Prompt maestro ampliado**: auditoría periódica del trabajo de Codex, anti-fake-IA explícito,
     testing por variante, UI/UX en español blanco/negro, NO CICLOS NI ACTIVIDAD FALSA,
     RAMA Y PRs explícitos.
4. Validado el YAML con `js-yaml` (Node, instalado en temp fuera del repo): sintaxis válida,
   heredocs Python balanceados (2 abren/2 cierran), r-string equilibrado, sin `${{ }}` dentro
   de los heredocs Python, llaves `{}` del dict equilibradas, cero tabuladores.
5. Actualizada la memoria: `SUPERVISION.md` (comportamiento del workflow y parada de emergencia),
   `DECISIONS.md` (failsafe activo por defecto, session lock por PR, verificación de rama en API, cron 2h).

### Problemas encontrados

- `gh` NO está instalado localmente → no se pudo verificar la existencia de `secrets.JULES_API_KEY`
  ni lanzar un ciclo de prueba desde la terminal. Documentado; el workflow fallará con mensaje claro
  si la clave no está configurada en el repo.
- Python local NO disponible → validación YAML hecha con Node/js-yaml en temp (fuera del repo).

### Prerrequisitos para el primer ciclo real (humano)

1. Confirmar que `secrets.JULES_API_KEY` existe en el repo (Settings → Secrets → Actions).
2. Confirmar que el conector de Jules ve la rama `jules/autonomous-ordia`.
3. (Opcional) Crear variable `ORDIA_AUTONOMY_ENABLED` = `false` solo si NO se quiere autonomía por defecto.

### Commits creados

- `969059d` docs(autonomy): memoria persistente AI_AUTONOMY y guía AGENTS (sesión 000).
- (pendiente) docs(autonomy): workflow Jules actualizado + memoria (este commit).

---

## SESIÓN 002 — Corrección de infraestructura: auto-merge y publicación en main

- **Fecha (UTC)**: 2026-08-10
- **Trigger**: corrección final del sistema autónomo (dos problemas críticos detectados)
- **Resultado**: ÉXITO (infraestructura publicada; falta solo el primer ciclo real manual)

### Problemas críticos detectados

1. **El workflow nuevo no está en `main`**: GitHub Actions ejecuta los schedulers desde la
   default branch. `origin/main` seguía en `9d05dd2` con la versión vieja (cron `0 7 * * *`,
   `preferred = "main"`), así que el cron de 2h NUNCA correría.
2. **`AUTO_CREATE_PR` no es auto-merge**: el session lock antiguo (cualquier PR abierta → no
   lanzar) habría bloqueado la autonomía indefinidamente tras la primera PR de Jules.

### Qué se hizo

1. `git fetch --all --prune` + consulta a la API de GitHub (25 PRs, todas `base:main`, autor
   propietario `wandersepulveda2013`): la señal fiable de PR de Jules es el patrón de rama head
   (`fix/desc-<timestamp>`, regex), no el autor.
2. Reescrito `.github/workflows/ordia-autonomous-jules.yml` (versión definitiva):
   - cron `17 */2 * * *` + `workflow_dispatch`; permisos mínimos (solo lectura); timeout 20 min.
   - Failsafes: variable `ORDIA_AUTONOMY_ENABLED` (false/0/no/off → deshabilitada; si no existe
     → habilitada) y archivo `AI_AUTONOMY/AUTONOMY_BYPASS`.
   - Paso `Find Ordia repository in Jules` con `preferred = "jules/autonomous-ordia"`.
   - Paso `Session lock` (id `lock`): consulta Jules Sessions API (fail-open; estados activos
     `QUEUED/PLANNING/IN_PROGRESS/PAUSED/AWAITING_*` → `skip_active_session`); evalúa PRs abiertas
     hacia la rama autónoma y sus checks (fallida → `proceed_with_failure_context`; CI corriendo →
     `skip_ci_running`; lista → `skip_ready_for_merge`; ≥4 PRs → `skip_too_many_prs`; draft/stale →
     contexto); anti-loop por área fallada ≥2 veces; contexto en `/tmp/autonomy-context.json`.
   - Paso `Launch autonomous Ordia session` condicionado a `decision == proceed |
     proceed_with_failure_context`; inyecta contexto de PRs fallidas/atascadas al prompt;
     `requirePlanApproval: False`; `automationMode: AUTO_CREATE_PR`.
3. Creado `.github/workflows/ordia-autonomous-merge.yml` (NUEVO): auto-merge squash hacia
   `jules/autonomous-ordia` con 12 guardas:
   (1) base ref exacto `jules/autonomous-ordia`; (2) guard clause `base == "main"` → MERGE
   PROHIBIDO; (3) no fork + patrón de rama Jules; (4) no draft; (5-6) `mergeable` y
   `mergeable_state` clean (behind → update-branch vía API); (7-10) todos los check-runs
   success/neutral/skipped, sin queued/in_progress/pending/waiting ni failure/cancelled/
   timed_out/action_required/stale/startup_failure (+ combined status); (11) security/secret/
   codeql/scan/dependency checks deben ser success; (12) merge squash sin force
   (`POST /pulls/{n}/merge` con `merge_method=squash`).
   Trigger: `pull_request_target` (opened/synchronize/reopened/ready_for_review) + cron
   `*/15 * * * *` + `workflow_dispatch`; concurrency group, `cancel-in-progress: false`.
   Permisos: `contents: write`, `pull-requests: write`, `checks: read`, `statuses: read`.
   Logging a `GITHUB_STEP_SUMMARY` (PR number, head SHA, checks, resultado, nuevo HEAD de la rama)
   + comentario post-merge en la PR.
4. Ampliado `.github/workflows/android-ci.yml`: `branches: [main, jules/autonomous-ordia]` para
   push y pull_request (verify corre en PRs hacia la rama autónoma; sign/publish solo en main).
5. Validado YAML con js-yaml (Node en temp, fuera del repo): 3/3 válidos, heredocs balanceados,
   llaves `{}` equilibradas, sin `${{ }}` dentro de los heredocs Python. Permisos job-level
   verificados (jules: read-only; merge: write mínimo; android-ci verify: read+checks write).
6. Publicado en `main` SOLO infraestructura (rama `infra/autonomous-main` desde `origin/main`,
   sin rebuild): `9d05dd2..d5b3b60` → main.
   - `origin/main` ahora: `android-ci.yml`, `build-apk.yml`, `ordia-autonomous-jules.yml`,
     `ordia-autonomous-merge.yml`.
   - Verificado con `git show origin/main:...`: cron `17 */2 * * *`, `preferred =
     "jules/autonomous-ordia"`, `requirePlanApproval: False`, `automationMode: AUTO_CREATE_PR`,
     guard clause main en el merge, `merge_method: squash`.
   - Sin camino automático `* → main`: scheduler crea PRs hacia la rama autónoma; auto-merge solo
     hacia la rama autónoma con guard clause; build-apk/android-ci son CI puro.
7. Publicada la rama autónoma con la infraestructura definitiva: `d84aeab..cc1a1e3`.

### Problemas encontrados

- Ninguno nuevo. `gh` y Python local siguen NO disponibles (validación YAML con Node/js-yaml en
  temp). No se pudo lanzar un ciclo real de prueba (falta `secrets.JULES_API_KEY`).

### Prerrequisitos para el primer ciclo real (humano)

1. Confirmar que `secrets.JULES_API_KEY` existe en el repo (Settings → Secrets → Actions).
2. Confirmar que el conector de Jules ve la rama `jules/autonomous-ordia`.
3. (Opcional) Ejecutar manualmente `Ordia Autonomous Jules` (workflow_dispatch) y
   `Ordia Autonomous Merge` (workflow_dispatch) para observar el primer ciclo.

### Commits creados

- `d5b3b60` (main, infraestructural) ci(autonomy): infraestructura definitiva del sistema autónomo en main
- `cc1a1e3` (jules/autonomous-ordia) ci(autonomy): session lock robusto, auto-merge seguro y CI sobre la rama autónoma
- (pendiente) docs(autonomy): memoria de la sesión 002 (este commit)

---

## Sesión 003 — 2026-08-11 (OpenHands: auditoría + fix de verificación de dominio)

**Objetivo**: primera ejecución de OpenHands. Inspeccionar el estado real del repo, leer la
memoria `AI_AUTONOMY`, auditar el trabajo previo de Jules/Codex, ejecutar una baseline razonable,
reconstruir prioridades y resolver el problema ejecutable de mayor prioridad verificable.

**Entorno**: rama `jules/autonomous-ordia` (HEAD inicial `ecd6151`). Sin Android SDK en el entorno;
se instaló OpenJDK 21 + kotlinc 2.1.20 + JUnit4/hamcrest/org.json/kotlinx-coroutines (-jvm) en /tmp.

### Cambios

- `tools/domain-smoke/DomainSmoke.kt`: fix del smoke obsoleto. El assertion
  `search.map { it.kind }.toSet() == SearchKind.entries.toSet()` fallaba siempre porque
  `SearchKind` se amplió a 7 valores (TASK, PROJECT, NOTE, HABIT, CONVERSATION, COMMITMENT,
  AUTOMATION) pero el smoke solo alimenta 4 listas. Alineado con `SearchEngineTest` (set explícito
  de los 4 kinds core).
- `tools/domain-smoke/PreferenceStubs.kt` (NUEVO): stubs JVM de `ThemeMode`, `InterfaceMode`,
  `GuardianMode`, `GuardianSpecies`, `UserPreferences`, `PreferencesRepository` (con
  `DAILY_INTERACTION_LIMIT`) para compilar/ejecutar los tests del dominio que dependen de
  `data.preferences` sin Android DataStore. Solo se usa desde tools/, no forma parte de la app.
- `AI_AUTONOMY/BACKLOG.md`, `CURRENT_STATE.md`, `RUN_LOG.md`, `DECISIONS.md`: memoria actualizada.

### Tests

- `bash tools/run_domain_checks.sh` → **PASS** (25 assertions) [antes FAIL: "Universal search failed"].
- Tests unitarios del dominio (JUnit4, JVM): **125 tests PASS, 0 FAIL, 0 skip**.
- `./gradlew test/lint/assemble`: **NO VERIFICADO** (sin Android SDK en el entorno).

### Hallazgos de auditoría

- La rama `jules/autonomous-ordia` es el rebuild Ordía 3.0 (275+ archivos); `main` solo tiene
  infra de orquestación. NO trabajar sobre main.
- Trabajo previo de Jules/Codex auditado: recordatorios (ReminderScheduler con WorkManager
  unique work + cancelAllAndAwait, ReminderActionReceiver con mutex+snooze real), persistencia
  (BackupManager con checksum SHA-256 ORD-031, RestoreData atómico via withTransaction),
  IA (TFLite simulado eliminado → IntelligenceProvider real), privacidad (IME guard, context
  filter). Trabajo REAL y robusto, no simulado.
- `SearchEngine` correctamente ampliado a 7 kinds; `SearchEngineTest` correcto; solo el smoke
  estaba desactualizado.
- `NoteBlocks.kt`/`TaskSnapshotCodec.kt` (dominio) acoplados a `org.json` (API Android) — deuda
  técnica menor, funcional.

### Commit

- (pendiente de crear en este paso) fix(test): alinear domain smoke con SearchKind ampliado

### Siguiente prioridad

- Auditoría de persistencia (Room cascadas/índices/transacciones/N+1) y recordatorios/WorkManager
  con Android SDK (gradle). Ítems P0/P1 del BACKLOG: restauración con manifiesto corrupto, backup
  adverso. La verificación de dominio ya está verde.

---

## Sesión 004 — 2026-08-11 (OpenHands: autonomía nocturna, Ciclo 1: NaturalTaskParser)

**Objetivo**: continuar autonomía. Auditar área funcional crítica P1 (parser) en profundidad.
**Entorno**: rama `jules/autonomous-ordia` (HEAD inicial `35fb204`). Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Método
- Probe JVM reproducible (`/tmp/parser-probe/Probe.kt`) que invoca `NaturalTaskParser.parse` con casos
  reales en español (fechas numéricas, esta noche/tarde/mañana, números escritos, 12/24h, urgente).
- Comparación con comportamiento esperado y con `parseMonthNameDate` (consistencia).

### Bugs encontrados y corregidos (commit `fb53e8c`)
- BUG1 (P1): fecha numérica sin año en el pasado no rodaba al año siguiente. "5/3" el 29-jul → 2026-03-05
  (pasada). Inconsistente con "5 de julio"→2027. Una fecha pasada hace que el recordatorio nunca dispare
  (ReminderSync filtra trigger<=now). Fix: rodar a +1 año si rawYear==null y date<today.
- BUG2 (P1): "esta mañana/tarde/noche" no reconocidas; además "esta mañana" se leía como "mañana" (tomorrow)
  porque contiene "mañana". Fix: rama partOfDay antes que la de "mañana"; horas canónicas (9/15/21), tolerant
  a acentos; tiempo explícito tiene prioridad.
- BUG4 (P1): "urgente" como palabra inicial no se detectaba sin prefijo !/#. Fix: `^urgente\b`; no se
  detecta a mitad de frase (evita "no es urgente").
- BUG3 (P2, OPEN): números escritos en "en dos horas"/"dentro de tres días" no parseados. Queda en backlog.

### Tests
- 136 domain tests PASS (125 previos + 11 nuevos de regresión), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- Ciclo 2: auditoría estática de persistencia (Room cascadas/índices/transacciones/N+1, restore atómico).
  Después recordatorios end-to-end, notas, rutinas.

---

## Sesión 004 — 2026-08-11 (OpenHands: autonomía nocturna, Ciclos 2-3: persistencia, recordatorios, notas)

**Objetivo**: auditar persistencia/Room, backup/restore, recordatorios end-to-end, seguridad del
manifiesto y notas. Buscar bugs P0/P1 reales.
**Entorno**: rama `jules/autonomous-ordia`. Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Auditoría estática (sin hallazgos P0/P1)
- **Persistencia/Room**: Entities con FK + índices correctos; `TaskDao.deleteSubtreeAndSelf`
  transaccional (resuelve huérfanos de `parentTaskId`, ORD-025); OrdiaDatabase con migraciones
  1→7 y SIN `fallbackToDestructiveMigration` (no hay pérdida silenciosa por schema mismatch).
- **Backup/Restore**: `RoomBackupStore.replaceAll` dentro de `database.withTransaction` atómica,
  orden de borrado/inserción coherente con FK; pre-restore backup verificado; verify-after-commit
  con rollback; checksum SHA-256 (ORD-031); mutex de operación. Sólido.
- **Recordatorios**: `ReminderScheduler` usa `enqueueUniqueWork`+REPLACE (sin duplicados);
  `TaskReminderWorker` re-lee la tarea y filtra completed/archived/cancelled, maneja quiet hours
  (reschedule a nextEndMillis), reintenta si falta POST_NOTIFICATIONS; `ReminderActionReceiver`
  exported=false (no spoofable) y bajo `TaskMutationGate.mutex`; `ReminderResyncReceiver` solo
  re-encola futuros (ReminderSync filtra trigger<=now). Sólido.
- **Manifiesto**: allowBackup=false, usesCleartextTraffic=false; MainActivity SEND/PROCESS_TEXT
  (texto capado a MAX_SHARED_TEXT_CHARS, URI permiso en runCatching). Sólido.
- **toggleTask + RecurrenceEngine**: mutex, reminder/start offset preservado en recurrencia,
  guard de avance (<=completedAt), subtask auto-complete con undo log. Sólido.

### Bug P1 encontrado y corregido (commit `2ae258a`)
- `NoteBlockCodec.decode`: el `runCatching` envolvía TODO el bucle de parseo. Si un único elemento
  del array JSON estaba malformado (p.ej. un string donde se esperaba un objeto),
  `array.getJSONObject(i)` lanzaba y el catch devolvía `listOf(NoteBlock(text = fallbackBody))`,
  perdiendo TODOS los bloques (data loss silencioso). Probe: `[HEADING válido, "badstring",
  PARAGRAPH válido]` → antes 1 bloque vacío.
- Fix: validar el array raíz por separado (si no es JSON → fallbackBody, degradación conocida);
  parsear cada elemento de forma aislada, descartar malformados (continue), conservar válidos;
  caer al fallback solo si TODOS fallan. Ahora → 2 bloques válidos.
- 11 tests nuevos en `NoteBlockCodecTest.kt` (la clase NO tenía cobertura previa): round-trip
  de todos los tipos, fallback a body, elemento malformado, todos malformados, tipo desconocido
  (forward-compat), id faltante, array vacío, truncated JSON, toPlainText.

### Infraestructura
- `tools/run_domain_tests.sh`: runner JUnit4 reutilizable que compila stubs + dominio + tests y
  los ejecuta con `JUnitCore`. Para futuras sesiones autónomas sin Android SDK.

### Tests
- `bash tools/run_domain_tests.sh` → 147 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- Ciclo 4: auditoría de rutinas (queries, batch, concurrencia, N+1). Después BUG3 (parser P2).

---

## Sesión 004 (cont.) — 2026-08-11 (OpenHands: Ciclos 3-4: recordatorios, rutinas, BUG3)

**Objetivo**: auditar recordatorios end-to-end, rutinas y resolver BUG3 (P2 parser).
**Entorno**: rama `jules/autonomous-ordia`. Sin Android SDK; kotlinc/JUnit4 en /tmp.

### Ciclo 3 — Recordatorios (sin hallazgos P0/P1)
- `ReminderScheduler`: `enqueueUniqueWork` + `REPLACE` → sin duplicados; cancelación reutiliza
  mismo unique name.
- `TaskReminderWorker`: re-lee la tarea (no confía en el snapshot de entrada), filtra
  completed/archived/cancelled, respeta quiet hours (reschedule a `nextEndMillis`), reintenta
  si falta `POST_NOTIFICATIONS`.
- `ReminderActionReceiver`: `exported=false` (no spoofable), bajo `TaskMutationGate.mutex`.
- `ReminderResyncReceiver`: solo re-encola futuros (`ReminderSync` filtra `trigger<=now`);
  responde a TIME/TIMEZONE/DATE.
- Conclusión: la capa de recordatorios es sólida. No se encontraron P0/P1.

### Ciclo 4 — Rutinas (sin hallazgos P0/P1; P3 menor)
- `RoutineRules.wasRunToday`: dedup correcta (filtra completed/archived/cancelled y compara
  `createdAt` con `today`).
- `runRoutine`: crea tareas con `sortOrder=index` → el orden de los pasos se preserva en la
  bandeja (observeAll ordena por `sortOrder ASC`). `createdAt=now+index` solo evita empates.
- `undoLastAutomation` + `AutomationUndoRules.createdTaskIds`: el undo de rutina es REAL —
  elimina las tareas creadas si siguen intactas en inbox (no borra las que el usuario ya
  completó/modificó). Testeado (`routine_withoutSnapshots_treatsEveryAffectedTaskAsCreated`).
- P3 menor: `saveRoutine` hace `deleteStep` por cada paso existente + `addStep` por cada paso
  nuevo SIN transacción atómica; si el proceso muere a mitad, la rutina queda con pasos
  parciales. Registrado en BACKLOG (P3, OPEN).

### BUG3 (P2) resuelto — commit `a48c5d7`
- `relativePattern` exigía dígitos (`\d{1,3}`) → "en dos horas", "dentro de tres días",
  "en una hora" no se parseaban (`dueAt=null`).
- Fix: patrón acepta dígitos O palabras (un/una, dos..doce) e introductor "dentro de";
  `parseWrittenNumber()` convierte el grupo. Cobertura 1-12 (casos comunes en español).
- 8 tests nuevos (incl. regresión de dígitos).

### Tests
- `bash tools/run_domain_tests.sh` → 155 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Commits
- `2ae258a` fix: NoteBlockCodec.decode resiliente por elemento.
- `78b4ef4` docs(autonomy): memoria ciclos 2-3.
- `a48c5d7` fix: parser reconoce números escritos en tiempo relativo.

### Siguiente prioridad
- Ciclo 5: auditoría de Onboarding (caber en pantallas pequeñas, botones accesibles, sin scroll
  imposible) y responsive. Después: NoteEditor `rememberSaveable`, atomicidad de `saveRoutine`,
  ítems P2 pendientes.

---

## Ciclo 5 — 2026-08-11

- HEAD inicial: `fa44990` (docs: memoria ciclos 3-4).
- Entorno: OpenJDK 21, kotlinc 2.1.20, libs en /tmp/libs. Baseline pre-fix: 155 tests + 25 smoke OK.

### BUG4 (P1) resuelto — parser: meridiem "de la tarde/noche" + limpieza de título
- `timePatterns` no reconocía "de la mañana/tarde/noche" como meridiem: "a las 4 de la tarde" →
  hora 04:00 (madrugada) en vez de 16:00; "a las 9 de la tarde" → 09:00 en vez de 21:00.
  Bug serio de P0/P1: tarea programada en horario totalmente equivocado.
- `mediodía`/`medianoche` solo capturaban la palabra, no "al mediodía"/"a la medianoche":
  dejaban "al"/"a la" sueltos en el título.
- Orden de limpieza destruía "esta mañana": el borrado genérico de "mañana" corría ANTES que
  `partOfDayPattern.replace`, dejando "esta" huérfano ("correo al jefe esta").
- Fix (mínimo):
  1. `timePatterns[0]` ("a las…") acepta opcional `de la mañana|tarde|noche` como grupo meridiem.
  2. `mediodía`/`medianoche` aceptan prefijo opcional `al ` / `a la ` para limpiar el título.
  3. `explicitTime` normaliza el meridiem extendido: "de la tarde"/"de la noche" → pm (+12),
     "de la mañana" → am (12→0).
  4. Limpieza del título reordenada: `partOfDayPattern` y `timePatterns` se aplican ANTES del
     borrado genérico de "mañana"/"hoy" (ambos contienen "mañana").
  5. `monthNamePattern.replace` ahora es condicional: solo elimina si el mes es válido, evitando
     que "9 de la" (en "a las 9 de la tarde") se destruya y deje "a las" + "tarde" sueltos.

### Tests
- 8 tests nuevos de regresión: `deLaTardeAppliesPmOffset`, `deLaNocheAppliesPmOffset`,
  `deLaMananaKeepsAmHour`, `deLaTardeWithMinutesAppliesPmOffset`, `deLaTardeDoesNotBreakTitle`,
  `alMediodiaParsesNoonAndCleanTitle`, `aLaMedianocheParsesMidnightAndCleanTitle`,
  `estaMananaCleanedFullyFromTitle`.
- `bash tools/run_domain_tests.sh` → 163 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Commits
- `4a20688` fix: parser reconoce "de la tarde/noche" y limpia título. (pushed a origin/openhands/autonomous-ordia)

### Siguiente prioridad
- Ciclo 6: auditoría de Onboarding (responsive, pantallas pequeñas) y NoteEditor
  `rememberSaveable`; seguir auditando el parser (casos límite: "a las 3pm de la tarde",
  horas con "de la madrugada", meses con tildes en mayúsculas).

---

## Ciclo 6 (2026-08-11) — parser: contexto PM de parte del día + "12 de la noche" = medianoche

- HEAD inicial: `fa44990` (docs: memoria ciclos 3-4). Rama: `openhands/autonomous-ordia` (synced).
- Entorno: OpenJDK 21, kotlinc 2.1.20, libs en /tmp/libs. Baseline pre-fix: 163 tests + 25 smoke OK.

### Problema seleccionado (P1 — tareas programadas 12h equivocadas)
Auditoría con `Probe2.kt` (/tmp/parser-probe/) reveló 4 bugs P1 de alto impacto, frases
ESPAÑOLAS EXTREMADAMENTE COMUNES que el parser interpretaba en horario totalmente errado:

1. **"esta tarde a las 4"** → 04:00 (4 AM!) en vez de 16:00.
2. **"esta noche a las 9"** → 09:00 (9 AM!) en vez de 21:00.
3. **"mañana a la tarde"** → 09:00 + "a la tarde" pegado en el título (sin PM aplicado).
4. **"a las 12 de la noche"** → 12:00 (mediodía) en vez de 00:00 (medianoche).

### Causa raíz
- `explicitTime` (hora "a las 4", sin meridiem) tenía prioridad sobre `partOfDayTime`
  ("esta tarde"), pero al no propagar el contexto PM de la parte del día, la hora quedaba AM.
- No existía reconocimiento de parte del día "suelta" ("a la tarde"/"de la tarde" sin "esta"):
  no aportaba hora canónica ni contexto PM, y dejaba restos en el título.
- "12 de la noche": el fix del ciclo 5 hacía `+12` para horas 1-11, pero para hora 12 dejaba 12
  (mediodía); el caso "12 de la noche"=medianoche quedaba mal.
- "de la madrugada" no estaba como meridiem en `timePatterns[0]` (dejaba restos en título).

### Solución (mínima, quirúrgica en `NaturalTaskParser.kt`)
1. Nuevo `standalonePartOfDayPattern` = `(a la|de la) (tarde|noche|madrugada)` con horas canónicas
   (tarde 15, noche 21, madrugada 4). NO fuerza fecha (solo hora del día sobre la fecha parseada).
2. `hasPartOfDayPmContext`: true si parte del día (esta o suelta) es tarde/noche.
3. `explicitTime` ahora emite `Pair<LocalTime, Boolean>` (hora + tuvo meridiem explícito).
4. Contexto PM: si hora explícita sin meridiem + contexto PM + hora en 1..11 → +12.
5. Fallback de hora: `partOfDayTime ?: standalonePartOfDayTime` cuando no hay hora explícita.
6. "12 de la noche" → 00:00 (caso especial: noche + 12 = medianoche, no mediodía).
7. "de la madrugada" añadido a `timePatterns[0]` y a `isAm` (12→0).
8. Limpieza de título: `standalonePartOfDayPattern.replace` después de `timePatterns`,
   antes del borrado genérico (evita restos "a la"/"de la").

### Tests
- 6 tests nuevos de regresión: `estaTardeConHoraSinMeridiemAplicaPm`,
  `estaNocheConHoraSinMeridiemAplicaPm`, `aLaTardeSueltaDefineHoraYNoFuerzaFecha`,
  `deLaTardeSueltaDaHoraCanonicaHoy`, `doceDeLaNocheEsMedianoche`,
  `deLaMadrugadaEsAmYLimpiaTitulo`.
- `bash tools/run_domain_tests.sh` → 169 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+27/-6)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+43)

### Hallazgos adicionales (menores, no abordados esta run)
- "salir de madrugada" (sin "a las"/"a la") no es reconocido (deja "de madrugada" en título,
  dueAt null). Caso raro; el patrón standalone exige "a la"/"de la". Para backlog.
- "a las 24" → null (24:00 es válido como medianoche pero raro). Menor.
- "a las 3.5" → comportamiento extraño (".5" suelto en título). Edge, no feature real.

### Siguiente prioridad
- Continuar auditoría del parser (casos límite), luego UX: Onboarding responsive,
  NoteEditor `rememberSaveable`, atomicidad de `saveRoutine`. Ver BACKLOG.

---

---

## CICLO 7 — Parser: "a las 24" / "24:00" = medianoche

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 4f43c0b (ciclo 6, en origin)
- **Trigger**: continuación autónoma; auditoría de casos límite del parser.

### Problema seleccionado (P3 → resulta info-loss real)

"a las 24" (forma común de medianoche en horarios de transporte, farmacias 24h, cierre de pubs)
NO se reconocía como hora válida: el regex de hora `([01]?\d|2[0-3])` solo aceptaba 0-23, así que
"a las 24" producía `dueAt=null` (tarea SIN recordatorio) y dejaba "a las 24" como basura en el
título. Casos verificados antes del fix:
- "comprar pan a las 24" → title='comprar pan a las 24', dueAt=null
- "reunion a las 24:00" → title='reunion a las 24:00', dueAt=null
- "cena a las 24 de la noche" → title='cena a las 24' (basura), dueAt=21:00 (inconsistente)

Pérdida de información: el usuario cree que dejó una tarea para medianoche y en realidad queda
sin hora/recordatorio. Por eso se sube de P3 a impacto real de info-loss.

### Causa raíz
- `timePatterns[0]` y `[1]` usaban `2[0-3]` para la hora → 24 no casaba.
- Sin casamiento de hora, "24" sobrevivía al limpiado de título y "de la noche" sí se parseaba
  por separado, generando el resultado inconsistente.

### Solución (mínima, en `NaturalTaskParser.kt`)
1. Regex de hora `2[0-3]` → `2[0-4]` en `timePatterns[0]` y `[1]` (acepta 20-24).
2. En `explicitTime`: si `hour == 24` → `LocalTime.MIDNIGHT to true` (medianoche absoluta).
   Marcar `meridiem=true` bloquea que el contexto PM de parte del día aplique +12 sobre un 24
   que ya es absoluto (24 no es 12, no entra en `hour<12`). "24" se comporta igual que
   "medianoche"/"a las 0".
3. Al casar la hora, el token "24" (con sus variantes "24:00", "24 de la noche") se elimina del
   título en el limpiado genérico, dejando el título limpio.

### Tests
- 3 tests nuevos de regresión: `aLas24EsMedianocheYLimpiaTitulo`,
  `aLas24ConMinutosEsMedianoche`, `aLas24DeLaNocheLimpiaTituloYEsMedianoche`.
- `bash tools/run_domain_tests.sh` → 172 tests PASS (25 clases), 0 FAIL.
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+18/-11)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+20)

### Hallazgos adicionales (probe de descubrimiento ciclo 7)
Casos de título que dejan basura/residuos NO limpiados (candidatos a ciclo 8):
- "ir al dentista mañana a primera hora" → title='ir al dentista a primera hora' ("a primera
  hora" NO se interpreta ni se limpia; debería ser ~09:00 inicio de jornada).
- "llamar a mamá el viernes que viene" → title='llamar a mamá que viene' ("que viene" se queda).
- "reunion a las 3pm del jueves" → title='reunion del' ("del" huérfano por limpiado de fecha).
- "reunion de 18 a 20" → rango horario no parseado (title y dueAt nulos). Rango válido de horas.
- "pasado mañana a las 4" → 04:00 AM (no aplica contexto; hora sin meridiem sin parte del día
  queda AM — consistente con diseño, no bug).

### Siguiente prioridad
- Ciclo 8: limpiar residuos de título ("que viene", "del" huérfano, "a primera hora") y/o
  parsear rango horario "de 18 a 20". Evaluar impacto real antes de implementar.

---

## SESIÓN 004 — Ciclo 8 (NaturalTaskParser: residuos de día de la semana)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `accbdff` (ciclo 7)
- **Trigger**: continuación autonomía — limpiar residuos de título en casos MUY comunes.
- **Resultado**: ÉXITO — VERIFIED

### Problema seleccionado
- **Prioridad**: P3 (calidad de título en captura ultrarrápida).
- **Causa raíz**: `weekdayPattern` solo capturaba el prefijo `el`/`próximo` pero NO:
  - prefijo `del`/`de` ("reunión del jueves", "a las 3pm del jueves", "factura del martes");
  - sufijo `que viene`/`próximo(s|a)` ("el viernes que viene", "el miércoles próximo").
  Al limpiar el día, los tokens adyacentes quedaban huérfanos en el título: "reunión del",
  "llamar a mamá que viene", "ir al dentista próximo". Casos de uso extremadamente comunes.

### Solución
- Extendido `weekdayPattern` para capturar prefijo `del`/`de` y sufijo `que viene`/`próximo(s|a)`.
- Group 1 sigue siendo el día (no rompe `toDayOfWeek()` ni `parseRecurrence`).
- El `.replace(value, " ")` del cleanup ahora consume prefijo+día+sufijo completos.

### Tests
- 6 tests nuevos de regresión (del jueves, del jueves con hora, el viernes que viene,
  el miércoles próximo, del viernes que viene con hora, preservación de "el viernes a las 15:00").
- `bash tools/run_domain_tests.sh` → 178 tests OK (25 clases). (+6 respecto a ciclo 7)
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- `./gradlew test/lint/assemble`: NO VERIFICADO (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (regex extendido)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+48, 6 tests)

### Hallazgos adicionales
- Probe confirmó 10/10 casos ahora limpios (antes 7/10 con residuos).
- Pendientes ciclo 9 (P3 menores): "a primera hora" (sin interpretar); rango horario
  "de 18 a 20" (no parseado, dueAt=null). Ver BACKLOG.

### Siguiente prioridad
- Ciclo 9: "a primera hora" → ~09:00 + limpiar del título; o rango horario "de 18 a 20".
  Evaluar impacto real antes de implementar. Luego UX/ítems P2.

---

## SESIÓN 004 — Ciclo 9 (NaturalTaskParser: "a primera hora")

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `c06e1da` (ciclo 8)
- **Trigger**: continuación autonomía — frase natural muy común sin interpretar.
- **Resultado**: ÉXITO — VERIFIED

### Problema seleccionado
- **Prioridad**: P2 (captura ultrarrápida + persistencia: la frase no generaba recordatorio).
- **Causa raíz**: "a primera hora" (y "a primera hora de la mañana/madrugada") no tenía
  patrón dedicado. El parser: (1) NO asignaba hora → `dueAt=null` (sin recordatorio) salvo
  que hubiera otra fecha; (2) dejaba residuo "a primera hora" en el título. Frase de uso
  cotidiano ("ir al dentista mañana a primera hora") quedaba incompleta.

### Solución
- Nuevo `primeraHoraPattern`: `(?i)\b(?:a\s+)?primera\s+horas?(?:\s+de\s+la\s+(?:mañana|madrugada))?\b`
  (acepta ñ y "manana" sin acento por robustez de teclado).
- `primeraHoraTime = LocalTime.of(9, 0)` como hora canónica de inicio de jornada.
- Se aplica como **fallback** en `parsedTime` (después de hora explícita y partes del día),
  así una hora de reloj siempre gana y "de la tarde" sigue usando `standalonePartOfDayPattern`.
- Limpieza del título: `.let { primeraHoraPattern.replace(it, " ") }` tras
  `standalonePartOfDayPattern` (orden correcto: si "primera hora de la tarde", el patrón
  standalone consume "de la tarde" primero y el de primera hora consume "a primera hora").

### Tests
- 4 tests nuevos de regresión:
  - "mañana a primera hora" → 09:00 + título limpio;
  - "a primera hora" sin fecha → hoy 09:00;
  - "del jueves a primera hora de la mañana" → jueves 09:00 + título "Reunión";
  - no deja residuo "primera"/"hora" en el título.
- `bash tools/run_domain_tests.sh` → 182 tests OK (25 clases). (+4 respecto a ciclo 8)
- `bash tools/run_domain_checks.sh` → 25 assertions OK.
- Probe adicional sobre 6 casos reales: todos limpios y con hora correcta (09:00; "de la
  tarde"=15:00 vía standalone). NO VERIFICADO: gradle/lint/assemble (sin Android SDK).

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+patrón, +fallback, +cleanup)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests, +import assertFalse)

### Hallazgos adicionales
- "a primera hora de la tarde" (caso aislado sin contenido) dejaba el título completo;
  con contenido real ("Reunión a primera hora de la tarde") se limpia correctamente y
  asigna 15:00 vía standalone. No es un bug real; se documentó en el patrón.
- Pendiente ciclo 10: rango horario "de 18 a 20" (dueAt=null, no parseado). Evaluar
  impacto real y si aporta utilidad (rango vs. hora única).

### Siguiente prioridad
- Ciclo 10: rango "de 18 a 20" o nueva auditoría funcional (captura/What Now/inteligencia).

---

## SESIÓN 010 — Bug: "N min antes" clasificado como duración, no recordatorio

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 41b3abc
- **Prioridad**: P1 (recordatorio perdido: el usuario esperaba un recordatorio pero obtenía una duración).
- **Causa raíz**: El patrón de recordatorio #2 `(\d{1,3})\s*(minutos?|horas?|días?)\s+antes`
  NO aceptaba la abreviatura `min` (ni `hora`), mientras que el patrón de duración #3
  `(\d{1,3})\s*(minutos?|min)\b` SÍ la aceptaba. Como los recordatorios se extraen ANTES
  que la duración, "30 min antes" no casaba como recordatorio y caía como duración:
  - `Avisar 30 min antes` -> dur=30, rem=null, título="Avisar antes" (recordatorio perdido).
  - `Reunión 15 min antes` -> dur=15, rem=null (recordatorio perdido).

### Solución
- Patrón de recordatorio #2 ampliado a `(\d{1,3})\s*(minutos?|min|horas?|hora|días?|día)\s+antes`
  para aceptar las mismas abreviaturas que el patrón #1 y que el de duración. Ahora
  "30 min antes" se reconoce como recordatorio ANTES de llegar al de duración.
- Mínimo cambio: 1 línea de regex + comentario explicativo.

### Tests
- 2 tests nuevos de regresión:
  - "Avisar 30 min antes" -> rem=30, dur=null, título="Avisar".
  - "Reunión 15 min antes" (sin verbo de recordatorio) -> rem=15, dur=null, título="Reunión".
- `bash tools/run_domain_tests.sh` -> 184 tests OK (25 clases). (+2 respecto a ciclo 9)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe sobre 8 casos de recordatorio/duración: todos correctos. NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (patrón reminder #2 ampliado)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+2 tests)

### Hallazgos adicionales (auditoría ciclo 10)
- Probe amplio reveló más oportunidades reales (a evaluar en próximos ciclos):
  - `#tag`/`@tag` no se limpia del título ni asigna categoría explícita (P2).
  - `Reunión de 30 minutos` deja residuo "de" en título (P3).
  - `Trabajar 2h` no reconoce "2h" compacto como duración (P2).
  - `prioridad alta:` y `urgente`/`importante` a mitad de frase no fijan prioridad (P2).

### Siguiente prioridad
- Limpiar `#tag`/`@tag` del título y honrar categoría explícita, o bien "2h" compacto.

---

## SESIÓN 011 — Categoría explícita por etiqueta "#cat"/"@cat" + limpieza del título

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 0c02eaf
- **Prioridad**: P2 (UX/inteligencia: el usuario etiquetaba con `#cat`/`@cat` pero la etiqueta
  quedaba como residuo feo en el título y, peor, `@trabajo` se ignoraba y la categoría se
  infería por keywords —a veces mal, p. ej. "Llamar a Ana @trabajo" → cat=personal—).
- **Causa raíz**: El parser solo detectaba categoría por keywords ("comprar", "reunión"...).
  No existía manejo de etiquetas explícitas `#cat`/`@cat`. El usuario al escribir `#trabajo`
  o `@compras` expresaba su intención de categoría, pero:
  1. La etiqueta quedaba en el título ("Comprar leche #compras").
  2. `@cat` no se reconocía y la categoría se infería (mal) por keywords.
  3. `#cat` válida (p. ej. `#personal`) era invalidada por keywords contradictorias.

### Solución
- Nuevo patrón `explicitCategoryPattern` = `[#@](trabajo|compras|salud|casa|personal)\b`,
  construido a partir de los nombres de categoría conocidos (no hardcodeado, evita
  divergencia con `categories`). Solo reconoce categorías conocidas, así un hashtag de
  contenido como `#proyecto` o `#vacaciones` NO se roba como categoría ni se elimina.
- En `parse()`, la categoría explícita se extrae ANTES de la inferencia por keywords y
  tiene prioridad: si el usuario etiquetó `#personal`, gana sobre "comprar leche"→compras.
- La etiqueta reconocida se elimina del título ("Comprar leche #compras" → "Comprar leche").
- Etiquetas desconocidas (p. ej. `#proyecto`) permanecen intactas en el título (contenido
  del usuario) y la categoría se infiere normalmente.

### Tests
- 5 tests nuevos de regresión:
  - `#compras` limpia título y asigna categoría.
  - `@trabajo` limpia título y asigna categoría (antes se ignoraba → personal).
  - `#personal` sobreescribe la inferencia por keywords.
  - `#proyecto` (desconocido) queda en el título y la categoría se infiere (trabajo).
  - `#trabajo` combinado con fecha y hora: título limpio + categoría + hora correcta.
- `bash tools/run_domain_tests.sh` -> 189 tests OK (25 clases). (+5 respecto a ciclo 10)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe sobre 10 casos: todos correctos. NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`explicitCategoryPattern`, +extracción con prioridad sobre keywords, +comentario)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+5 tests)

### Hallazgos adicionales (auditoría ciclo 11)
- `Trabajar 2h` / `Estudiar 1h`: "Nh" compacto no se reconoce como duración (P2, pendiente).
- `Reunión de 30 minutos` deja residuo "de" en título (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).
- Title residue "que viene" tras día de semana (P3, pendiente).

### Siguiente prioridad
- "Nh" compacto como duración ("Trabajar 2h" → dur=120) — P2, alto valor (captura rápida).

---

## SESIÓN 012 — Duración compacta "Nh" ("Trabajar 2h" → 120 min)

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: 7cdc803
- **Prioridad**: P2 (captura rápida: "2h"/"1h" es la forma natural y compacta de expresar
  duración; antes no se reconocía y quedaba "2h" como residuo feo en el título sin
  asignar duración).
- **Causa raíz**: `durationPatterns` solo reconocía unidades completas (`minutos`, `min`,
  `horas`, `hora`). La abreviatura compacta "h" pegada al número ("2h") no casaba con
  ningún patrón, así que `durationMinutes=null` y "2h" se quedaba en el título.

### Solución
- Nuevo patrón `\b(\d{1,3})\s*(h)\b` añadido al final de `durationPatterns`.
- El `\b` final es clave: en "2horas" la 'h' va seguida de 'o' (ambos word chars), por lo
  que NO hay límite de palabra entre ellas → el patrón compacto NO casa "2horas". Así no
  roba el prefijo ni deja residuo "oras"; el patrón completo `horas?` sigue manejándolo.
- Detección de unidad ampliada: `unit.startsWith("hora") || unit == "h"` → horas (×60).

### Tests
- 4 tests nuevos de regresión:
  - "Trabajar 2h" → dur=120, título="Trabajar".
  - "Estudiar 1h" → dur=60, título="Estudiar".
  - "Reunión 2horas" → dur=120, título limpio (el compacto no roba la palabra completa).
  - "Estudiar 2h recuérdame 15 min antes" → dur=120 + rem=15 (sin interferencia).
- `bash tools/run_domain_tests.sh` -> 193 tests OK (25 clases). (+4 respecto a ciclo 11)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+patrón compacto `\b(\d)\s*h\b`, +comentario, +unit check `h`→horas)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests)

### Hallazgos adicionales (auditoría ciclo 12)
- `prioridad alta:`/`urgente`/`importante` a mitad de frase no fijan prioridad (P2, pendiente —
  alto valor: "Llamar mamá urgente" debería ser HIGH).
- Residuo "de" en "Reunión de 30 minutos" (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).

### Siguiente prioridad
- Prioridad a mitad de frase ("urgente"/"importante" como palabra suelta en cualquier
  posición → HIGH) — P2, alto valor (evita olvidos de tareas urgentes).

---

## SESIÓN 013 — Prioridad por sufijo final "urgente"/"importante"

- **Fecha (UTC)**: 2026-08-11
- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: c265777
- **Prioridad**: P2 (evita olvidos: el usuario expresa prioridad en texto libre como palabra
  final "Llamar mamá urgente"; antes caía a NORMAL).
- **Causa raíz**: la detección de "urgente" sin prefijo solo cubría la palabra INICIAL
  (`leadingUrgentPattern`). "urgente"/"importante" como palabra final (sufijo de prioridad)
  no se reconocían, por diseño para evitar falsos positivos de mitad de frase ("no es
  urgente el documento"). Resultado: prioridad genuina del usuario ignorada en el patrón de
  captura más natural ("Llamar mamá urgente").

### Solución
- `trailingPriorityPattern`: `\b(urgente|importante)\b\s*[.!?]?$` → detecta la palabra final.
  - "urgente" → URGENT, "importante" → HIGH.
  - Se limpia del título (igual que el prefijo `!urgente` y el `leadingUrgentPattern`).
- `negatedPriorityPattern`: `\bno\s+(?:es|era|fue|parece|ser[áa])\s+(?:lo\s+)?(?:urgente|importante)\b\s*[.!?]?$`
  → guard de negación. "no es urgente" como palabra final NO activa prioridad (NORMAL) y se
  conserva como contenido del título.
- Orden de prioridad respetado: prefijos `!`/`#` > inicial > sufijo final (sin negación).

### Tests
- 4 tests nuevos de regresión:
  - "Llamar mamá urgente" → URGENT, título="Llamar mamá".
  - "Enviar factura importante" → HIGH, título="Enviar factura".
  - "Comprar leche urgente!" → URGENT, título="Comprar leche".
  - "Revisar el documento, no es urgente" → NORMAL, título conservado.
- `bash tools/run_domain_tests.sh` -> 197 tests OK (25 clases). (+4 respecto a ciclo 12)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+trailingPriorityPattern, +negatedPriorityPattern, +rama sufijo final en `when` de prioridad,
  +limpieza del sufijo del título)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests)

### Hallazgos adicionales (auditoría ciclo 13)
- Residuo "de" en "Reunión de 30 minutos" (P3, pendiente).
- Rango horario "de 18 a 20" → dueAt=null (P3, pendiente de evaluación).

### Siguiente prioridad
- Nueva auditoría funcional del parser con casos reales adicionales, o resolver el residuo
  "de" (P3) si aporta claridad. Continuar evitando feature bloat.

## SESIÓN 014 — Duraciones fraccionarias "media hora"/"cuarto de hora"

- **Fecha**: 2026-08-11
- **HEAD inicial**: 6486ce3
- **Rama**: `openhands/autonomous-ordia`

### Problema seleccionado
- **Prioridad**: P2 (captura: el usuario expresa duración en fracciones comunes del español sin dígitos; antes se perdía la duración y quedaba residuo en el título).
- **Causa raíz**: `durationPatterns` requieren dígitos (`\d{1,3}`). Frases muy comunes como "media hora" (30 min) y "(un) cuarto de hora" (15 min) no casaban, así que `durationMinutes` quedaba null y la frase se conservaba como residuo en el título ("Estudiar media hora" → título="Estudiar media hora", dur=null).

### Solución
- `fractionalDurationPattern`: `\b(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\b`.
  - "media hora" → 30 min, "(un) cuarto de hora"/"cuarto de hora" → 15 min.
  - Guard de "hora": "cuarto" solo ("Limpiar el cuarto") NO casa (cuarto=habitación).
- Integrado en el bloque de duración: ocurrencia más a la izquierda entre duración numérica y fraccionaria; se limpia del título en cualquier caso.
- `coerceIn(5, 24*60)` preserva los límites existentes.

### Tests
- 5 tests nuevos: media hora=30, un cuarto de hora=15, cuarto de hora=15, media hora+fecha/hora no interfiere, cuarto=habitación no es duración.
- `bash tools/run_domain_tests.sh` -> 202 tests OK (25 clases). (+5 respecto a ciclo 13)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (+fractionalDurationPattern, +rama fraccionaria en durationMinutes, +limpieza)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+5 tests)

### Hallazgos adicionales (auditoría ciclo 14)
- Residuo "de" en "Reunión de 30 minutos" (P3, sigue ABIERTO).
- Rango horario "de 18 a 20" → dueAt=null (P3, sigue pendiente).
- "finde"/"fin de semana" no se parsea como fecha (ambigüedad de design: ¿sábado? se omite).

### Siguiente prioridad
- Resolver el residuo "de" (P3), o evaluar rango horario "de 18 a 20". De lo contrario nueva auditoría funcional fuera del parser (tareas/rutinas/búsqueda/What Now).

### Commit / push
- (commit al final del ciclo)

---

## Ciclo 15 — 2026-08-11 (OpenHands, autonomía)

### Contexto
- HEAD inicial: `9c5222f` (feat(parser): duraciones fraccionarias).
- Branch: `openhands/autonomous-ordia`.

### Problema seleccionado
- P3 (datos) — `saveRoutine` no atómico: delete-then-reinsert de pasos de rutina sin transacción.

### Causa raíz
- `OrdiaViewModel.saveRoutine` borraba cada paso existente y luego insertaba los nuevos
  en llamadas DAO separadas. Si el proceso moría entre el borrado y las inserciones, la
  rutina quedaba con pasos parciales o sin pasos (pérdida de trabajo del usuario). Además
  leía los pasos existentes desde `uiState` (memoria) en vez de la fuente de verdad.

### Solución
- `RoutineStepDao.replaceSteps(routineId, steps)` con `@Transaction` (deleteByRoutine +
  insert por paso), siguiendo el patrón de `deleteSubtreeAndSelf`.
- `RoutineRepository.replaceSteps(...)` expuesto.
- `saveRoutine` construye la lista limpia de pasos y los reemplaza atómicamente.
- Reasigna `position` por índice (orden de visualización).

### Archivos modificados
- `app/src/main/java/com/ordia/app/data/local/Daos.kt` (+deleteByRoutine, +replaceSteps @Transaction)
- `app/src/main/java/com/ordia/app/data/repository/Repositories.kt` (+replaceSteps)
- `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt` (saveRoutine usa replaceSteps)

### Tests
- `bash tools/run_domain_tests.sh` -> 202 tests OK (25 clases). (sin regresión)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Sintaxis DAO/Repository verificada con kotlinc + stubs (sin errores en líneas cambiadas).
- NO VERIFICADO: integración DAO/Room/gradle requiere Android SDK.

### Hallazgos adicionales
- Recurrencia mensual verificada correcta (`plusMonths(1)`); Probe3 discrepancia era por `due` distinto, no bug.
- `addStep`/`deleteStep` del repositorio ya no usados por `saveRoutine` (se conservan para API).

### Commit / push
- fix(data): `saveRoutine` atómico con replaceSteps @Transaction (previene pérdida de pasos en crash)
- commit `0f14ded`; push OK a `openhands/autonomous-ordia` (9c5222f..0f14ded).

### Siguiente prioridad
- Continuar auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones) o resolver P3 pendientes del parser.

---

## Run 2026-08-11 — Ciclo 16 (OpenHands, autonomía)

### Contexto inicial
- HEAD inicial: `0f14ded`.
- Branch: `openhands/autonomous-ordia`.

### Problema seleccionado
- P3 (parser/captura) — Rango horario "de H1 a H2 [horas]" no parseado + residuo "de" en
  duraciones numéricas.

### Causa raíz
- `NaturalTaskParser` no tenía patrón para rangos horarios. "Cita de 18 a 20" dejaba el
  rango completo en el título y `durationMinutes=null`. Peor, "Clase de 18 a 20 horas"
  casaba "20 horas" con el patrón de duración numérica → 20h (1200 min) falso.
- Además, el conector "de" antes de una duración numérica ("Reunión de 30 minutos") no se
  eliminaba junto con la duración → título "Reunión de".

### Solución
- `timeRangePattern` = `\b(?:de\s+)?(\d{1,2})\s*(?:a|-)\s*(\d{1,2})(\s*(?:horas?|hs|h))?\b`.
- Se procesa ANTES que `durationPatterns` para que el segundo número no sea robado como
  duración. Duración = (end-start)*60 min, con sanitización (end>start, end<=24, ≤24h).
- Guard anti-falso-positivo: solo se acepta como rango horario si hay unidad final o alguna
  hora>=13 (formato 24h inequívoco). "Comprar de 2 a 5 entradas" no se toca.
- No fija hora de inicio (ambigua sin meridiem); solo la duración, de forma honesta.
- La duración numérica posterior arrastra el conector "de " cuando lo precede (regex
  `\bde\s+<dur>`), limpiando "Reunión de 30 minutos" → "Reunión".

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`timeRangePattern`; lógica de rango + conector "de" en bloque de duración)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+7 tests: rango con unidad, 24h sin unidad, horas pequeñas con unidad, anti-falso-positivo
  en conteo, rango con texto final, conector "de" en min/horas)

### Tests
- `bash tools/run_domain_tests.sh` -> 209 tests OK (25 clases). (+7 desde 202)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Verificación con probe JVM cubriendo 15 casos (rango/anti-falso/duraciones/horas).
- NO VERIFICADO: gradle/lint/Android (sin Android SDK).

### Hallazgos adicionales
- BACKLOG: 2 entradas P3 parser (rango "de 18 a 20", residuo "de") marcadas FIXED → VERIFIED.
- "Trabajo de 8 a 12 horas" → 240 min correcto (guard por unidad).
- "Reunión de 18 a 20 con juan" → "Reunión con juan" + 120 min (preserva texto final).

### Commit / push
- (pendiente; se commitea a continuación)

### Siguiente prioridad
- Nueva auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones/contexto)
  o siguientes P3 del parser ("salir de madrugada", "a las 3.5").

---

## Sesión OpenHands 004 — Ciclo 17 (NaturalTaskParser — recurrencia mensual anclada)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `63400f3` (origin/openhands/autonomous-ordia, sincronizado vía pull)
- **Ciclo**: 17 (continúa ciclo 16)

### Problema seleccionado
P1 — Recurrencia mensual anclada a día del mes ("el 15 de cada mes") NO se parseaba.
`dueAt` quedaba `null`, el día se quedaba como residuo en el título ("Pagar la cuenta el 15 de"),
la tarea mensual nunca tenía fecha de vencimiento y los recordatorios no disparaban.

### Causa raíz
`parseRecurrence` reconocía "cada mes" (frecuencia simple) y "cada N meses" (intervalo),
pero NO el patrón anclado `N de/del (cada) mes` ("el 15 de cada mes"). Sin anclaje, el día
caía al título y no se generaba fecha. Hallazgo lateral: la rama `monthNameMatch != null`
del `when` de fecha seleccionaba la fecha nominal de mes aunque `parseMonthNameDate`
devolviera `null` (mes inválido), de modo que un sufijo de hora "8 de la manana" generaba
la falsa coincidencia "8 de la" (mes="la" inexistente → null) y anulaba la resolución de
fecha de repeticiones mensuales/semanales con hora explícita.

### Solución
- `RecurrenceResult` +`monthlyDayOfMonth: Int?` (día del mes anclado).
- `parseRecurrence`: patrón `monthlyDayPattern = \b(?:el|los)?\s*(\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes(es)?\b`;
  captura el día (1..31), marca la frase para limpieza y devuelve `MONTHLY` anclado.
- `nextMonthlyDate(from, day)`: próxima fecha con ese día, inclusive si es hoy; avanza de
  mes si el día no existe en el mes actual (31 en feb) o ya pasó; recorre ≤24 meses.
- Rama en el `when` de fecha: `recurrence.frequency==MONTHLY && monthlyDayOfMonth!=null -> nextMonthlyDate`.
- Fix lateral: `monthNameDate` = `monthNameMatch?.let { parseMonthNameDate(...) }`;
  la rama de fecha usa `monthNameDate != null` (fecha resuelta, no mera coincidencia regex),
  evitando que "8 de la manana" sombre repeticiones con hora.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (+`RecurrenceResult.monthlyDayOfMonth`; `monthlyDayPattern` en `parseRecurrence`;
  `nextMonthlyDate`; rama MONTHLY en `when`; `monthNameDate` resolución)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+3 tests: `parsesMonthlyDayOfMonthRecurrence`, `parsesMonthlyDayOfMonthTodayInclusive`,
  `monthlyDayOfMonthKeepsExplicitTime`)

### Tests
- `bash tools/run_domain_tests.sh` -> 212 tests OK (25 clases). (+3 desde 209)
- `bash tools/run_domain_checks.sh` -> 25 assertions OK.
- Probe JVM (12 casos): anclaje a próximo mes, día hoy inclusive, 31 (jul existe), 30
  (jul existe), 10 con hora "de la manana" (antes `due=hoy` ahora `2026-08-10 08:00`),
  "15 de agosto"/"1 de enero" (fecha única, NO recurrente — patrón requiere "mes").
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales
- BACKLOG: entrada P1 mensual anclado añadida a Completados → VERIFIED.
- Caso menor no resuelto (preexistente, word-order raro): "pagar cada mes el 15" (día
  DESPUÉS de "cada mes") queda como MONTHLY sin anclaje + "el 15" en título. No es
  regresión; no se sobre-ingeniería (orden poco común).
- "cada mes" a secas sigue sin fecha (ambiguo, sin día): comportamiento preexistente correcto.

### Commit / push
- `ae43af3` (feat(parser): recurrencia mensual anclada a día del mes) — push a openhands/autonomous-ordia (pendiente push en este run)
- HEAD final: `ae43af3`

### Siguiente prioridad
- Auditoría funcional no-parser (tareas/búsqueda/What Now/automatizaciones/contexto) o
  siguientes P2/P3 del backlog (lint `InsertDriveFile` AutoMirrored, i18n strings).

---

## SESIÓN 017 — Ciclo 18 (RecurrenceEngine: anclaje mensual consistente con parser)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `324d1e6` (origin/openhands/autonomous-ordia; push del ciclo 17
  completado al inicio de este run con `github_token`)
- **Ciclo**: 18 (continúa ciclo 17 — cierra consistencia parser↔engine de la nueva
  funcionalidad de recurrencia mensual)

### Problema seleccionado
P1 — `RecurrenceEngine` mensual NO anclaba al día del mes: usaba `base.plusMonths(interval)`,
que **clampa** los días 29-31 al último día válido del mes destino (p. ej. ene 31 + 1 mes →
feb 28). Esto hacía **derivar el ancla** de una recurrencia "el 31 de cada mes" (31 → 30 → 30…)
y era **inconsistente** con el anclaje de `NaturalTaskParser.nextMonthlyDate` (que salta los
meses sin el día). Tras completar la primera ocurrencia generada por el parser, el engine
rompía la promesa de "cada día 31". La nueva funcionalidad del ciclo 17 quedaba a medias en
el ciclo de vida real (completion → próxima ocurrencia).

### Causa raíz
`advance` para `MONTHLY` = `base.plusMonths(interval)`. `java.time` clampa el día del mes
al límite del mes destino en vez de saltar el mes. No había helper de anclaje mensual.

### Solución
- `RecurrenceEngine.nextMonthly(base, interval)`: ancla al `base.dayOfMonth`, busca el
  primer mes a partir de `base + interval` que contenga ese día (`YearMonth.lengthOfMonth`),
  conservando hora y zona de `base`. Recorre ≤24 iteraciones (día ≤31 siempre halla mes
  válido: 31 existe en jul/ago/oct/dic).
- `advance` MONTHLY ahora llama a `nextMonthly`.
- Para días 1-28 (caso más común: "el 15 de cada mes") el comportamiento es **idéntico**
  (todo mes los contiene). Solo cambia días 29-31, ahora correctos y coherentes con el parser.

### Archivos modificados
- `app/src/main/java/com/ordia/app/domain/RecurrenceEngine.kt`
  (+import `YearMonth`; `nextMonthly`; `advance` MONTHLY → `nextMonthly`)
- `app/src/test/java/com/ordia/app/domain/RecurrenceEngineTest.kt`
  (+3 tests: `monthly_anchorsToDayOfMonthAndSkipsMonthsLackingIt`,
  `monthly_preservesDayForCommonDays`, `monthly_advancesPastCompletedAt`)

### Tests
- Red primero: 2 failures (`feb 28` en vez de `mar 31`; `mar 28` en completión tardía).
- `bash tools/run_domain_tests.sh` → **215 tests OK** (25 clases) (212 + 3).
- `bash tools/run_domain_checks.sh` → **25 assertions OK** (kotlinc en PATH vía
  /tmp/kotlinc-home/kotlinc/bin).
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Commit / push
- `c709e26` (fix(parser): RecurrenceEngine mensual anclaje) — push al cierre del run.
- HEAD final: `c709e26`

### Hallazgos adicionales
- Funcionalidad de recurrencia mensual del ciclo 17 ahora cierra su ciclo completo
  (parser → primera fecha → completion → próxima fecha coherente).
- No se requiere cambio de esquema Room: el ancla se infiere del `dueAt` de la tarea
  completada (día del mes), evitando una migración no verificable sin Android SDK.

### Siguiente prioridad
- Auditoría funcional no-parser: What Now (tareas programadas vs. inbox), búsqueda,
  automatizaciones, contexto, GuardianEngine, LearningEngine.

## SESIÓN 018 — Ciclo 19 (NaturalTaskParser: anclaje de recurrencias de intervalo)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `cf9841e`
- **Trigger**: continuidad de autonomía; revisión del ciclo 17/18 reveló un bug
  **simétrico** que dejó recurrencias de intervalo (no mensuales) sin `dueAt`.

### Problema seleccionado

P1 — Recurrencias de **intervalo** sin fecha explícita (diaria "cada día", quincenal
"cada 2 semanas", mensual/anual sin día "cada mes"/"cada año") se creaban con
`dueAt=null`. El bloque `when` de resolución de fecha solo anclaba recurrencias con día
explícito (semanal con días, mensual con día del mes). Resultado: la primera ocurrencia
era **invisible** — no aparecía en What Now/planificador; su recordatorio **nunca
disparaba** (`ReminderSync` usa `reminderAt ?: dueAt`, ambos null); se olvidaba hasta su
primer completado (recién entonces `RecurrenceEngine` infería el siguiente desde
`completedAt`). Es la continuación lógica del bug mensual de los ciclos 17 (parser) y
18 (engine).

### Causa raíz

En `NaturalTaskParser.parseRecurrence()`, el `when` que deriva la fecha de la primera
ocurrencia tenía casos para "fecha explícita" (hoy/mañana/día de semana/día de mes) y para
"recurrencia semanal con días" y "mensual con día del mes", pero **ningún caso para
recurrencias de intervalo puro** (DAILY/WEEKLY-interval/YEARLY-interval sin día), que
caían en el `else` dejando `date=null` → `dueAt=null`.

### Solución

- Nuevo caso al final del `when`: `recurrence.frequency != NONE -> base.toLocalDate()`
  ancla la primera ocurrencia a la **fecha de captura** cuando ninguna fecha explícita se
  resolvió antes. Las fechas explícitas conservan prioridad porque se evalúan primero en
  el `when`. Para "cada día a las 8" el ancla es hoy + la hora explícita.
- `RecurrenceEngine.nextOccurrence` ya maneja `dueAt` no nulo correctamente: la próxima
  ocurrencia = `dueAt + intervalo`, coherente. Sin cambio de esquema Room.

### Archivos modificados

- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (caso `recurrence.frequency != NONE -> base.toLocalDate()` en el `when` de resolución
  de fecha)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (2 tests actualizados de `assertNull(dueAt)` a fecha de captura; +3 tests nuevos:
  `parsesDailyRecurrenceWithTime`, `parsesYearlyRecurrence`,
  `parsesIntervalRecurrencePreservesExplicitDate`)

### Tests

- Red primero: tests nuevos/existentes fallaban con `dueAt=null`.
- `bash tools/run_domain_tests.sh` → **218 tests OK** (25 clases) (215 + 3 netos tras
  actualizar 2 existentes).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales

- Las recurrencias con fecha explícita ("reunión cada lunes el 30 de julio") preservan la
  fecha explícita porque se evalúa antes que el nuevo caso de anclaje por defecto.
- Cierra el conjunto de bugs de anclaje de primera ocurrencia: mensual (ciclo 17), avance
  mensual (ciclo 18) y ahora intervalo (ciclo 19). Todas las recurrencias tienen primera
  ocurrencia visible y recordable.

### Commit / push
- fix(parser): recurrencias de intervalo ancladas a fecha de captura — push al cierre.
- HEAD final: `c3958ee`.

### Siguiente prioridad
- Auditoría funcional no-parser: What Now (tareas programadas vs. inbox), búsqueda,
  automatizaciones, contexto, GuardianEngine, LearningEngine.

---

## 2026-08-11 — OpenHands — Ciclo 20a (auditoría funcional no-parser)

- **HEAD inicial**: `cd80eb0` (origin/openhands/autonomous-ordia); tras `git pull --ff-only`.
- **Branch**: `openhands/autonomous-ordia`.

### Objetivo
- Auditoría funcional no-parser iniciada en la prioridad del ciclo 19: What Now, búsqueda,
  automatizaciones, GuardianEngine, LearningEngine, inteligencia, contexto.

### Cambios (2 bugs P1 encontrados y corregidos)

1. **SummaryEngine — subtareas inflaban los conteos del resumen diario** (commit `7127b7e`):
   `dailySummary` contaba `completedToday`/`completedWeek`/`dueToday` sobre TODAS las tareas,
   incluyendo subtareas (sin filtrar `parentTaskId == null`). Al completar un padre con N
   subtareas (auto-completadas en cascada), el resumen mostraba `completedToday = N+1` en vez
   de 1 (doble conteo). El resumen del día es una superficie de decisión del usuario; el doble
   conteo infla la percepción de progreso y distorsiona el "¿cuánto hice hoy?". Fix:
   `parentTaskId == null` en los conteos del snapshot; las subtareas siguen existiendo y
   aportando al progreso visual de su padre (vía `SubtaskRules.progress`), pero el resumen
   cuenta tareas lógicas (raíces), consistente con `WhatNowEngine.isCandidate` y
   `AutomationActionPlanner` (que ya filtraban raíces). Test nuevo `summaryCountsOnlyRootTasks`.

2. **SearchEngine — "nota X" no encontraba notas sin la palabra "nota" en el contenido**
   (commit `c1bab04`): la búsqueda por tipo `nota` filtraba resultados donde el tipo de la
   entidad era `NOTE`, pero para notas la coincidencia semántica requiere reconocer el
   **término de consulta** "nota"/"notas"/"apunte"/"apuntes" como el tipo buscado, no que el
   contenido de la nota contenga esa palabra. Las notas rara vez incluyen la palabra "nota"
   en su cuerpo, así que una consulta "nota ideas" no devolvía notas aunque existieran.
   Asimétrico con tareas/conversaciones/compromisos, que ya tenían `semanticMatches` con su
   set de términos. Fix: `NOTE_TERMS` set + `semanticMatches` fallback para notes, análogo al
   resto. Test nuevo `noteTypeFilterDoesNotRequireTheWordNotaInContent`.

### Auditoría sin hallazgos (motores sólidos)
- `RecurrenceEngine.kt`, `TaskRules.kt`, `DayPlanner.kt`, `PlannerCalendar.kt`,
  `QuietHours.kt`, `RoutineRules.kt`, `HabitRules.kt`, `GuardianEngine.kt`,
  `LearningEngine.kt`, `SubtaskRules.kt`, `WhatNowEngine.kt`, `DateRules.kt`,
  `TaskSnapshotCodec.kt`, `UniversalCaptureEngine.kt`, `FocusTimerRules.kt`,
  `ReminderSync.kt`, `TaskMutationGate.kt`, `AutomationRules.kt` (catalog + guard),
  `AutomationEngine.kt`, `AutomationUndoRules.kt`.

### Tests
- `bash tools/run_domain_tests.sh` → **222 tests OK** (25 clases).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/assemble/Android/Room con DAOs reales (sin Android SDK).

### Hallazgos adicionales
- `GuardianEngine.completedToday`/`completedAll`/`activityExperience` incluyen subtareas.
  Decidido NO cambiar: el guardián mide **actividad** (cada registro completado es actividad
  real) y su XP es explícitamente "derivada de registros reales"; no es un conteo de
  "tareas lógicas" como el resumen diario. Distinto al bug de SummaryEngine. Dejado como
  hallazgo documentado, no como fix.
- `AutomationEngine` es robusto: loop guard (chainDepth>1), límites frecuencia/diario,
  snapshots de deshacer capturados ANTES de aplicar updates, `runCatching` para fallos,
  no duplica revisión de compromisos (marker check).

### Commits / push
- `7127b7e` fix(summary): subtareas inflaban los conteos del resumen diario
- `c1bab04` fix(search): "nota X" no encontraba notas sin la palabra "nota"
- Push a `openhands/autonomous-ordia`.
- **HEAD final**: `c1bab04`.

### Siguiente prioridad
- Continuar descubrimiento: context/external (ContextPrivacyFilter, app usage), conversations
  (compromisos), accesibilidad, rendimiento. O buscar oportunidades de producto (no solo
  auditoría): ¿el What Now podría agrupar/replanificar mejor? ¿la captura podría sugerir
  categoría/proyecto automáticamente con más cobertura de keywords?

---

## Sesión OpenHands — 2026-08-11 (modo continuo: supervisor persistente)

**Objetivo**: implementar continuidad real 24/7 (run termina → siguiente run en segundos, no horas).
**Entorno**: rama `openhands/autonomous-ordia`. HEAD inicial `cd80eb0` (sincronizado desde remoto,
que había avanzado 19 commits por los runs cron previos: recurrencia mensual anclada, saveRoutine
atómico @Transaction, duraciones compactas/fraccionarias, prioridad por sufijo, categoría por
etiqueta, "a primera hora", "24:00"=medianoche, etc.).

### Hallazgos críticos
- **Todos los runs cron+manual aparecían FAILED por timeout de 600 s**, pero el agente SÍ
  commiteaba+pusheaba antes de morir (el remoto avanzó 19 commits). El corte era prematuro.
- **El cron del automation service NO previene concurrencia**: dispatcha ciegamente sin comprobar
  runs activos. Se detectaron **2 runs concurrentes** (violaba MAX_CONCURRENT=1).

### Arquitectura implementada
- `tools/ordia_supervisor.py`: supervisor persistente (solo stdlib: urllib/json/fcntl). Loop:
  comprueba runs activos vía API → si ninguno, dispatcha → espera (poll ~25 s) → repite con
  cooldown ~15 s. Lock de proceso (flock), backoff exponencial tras fallos, STOP/PAUSE/RESUME
  via archivos sentinel, logs. MAX_CONCURRENT_RUNS=1 garantizado.
- `tools/ordia_supervisor.sh` + `tools/SUPERVISOR.md`: lanzador y documentación (comando único
  para el usuario en una máquina siempre encendida).
- El supervisor deshabilita el cron al arrancar (evita concurrencia) y lo rehabilita al detenerse
  (watchdog de seguridad).
- Automation `Ordía Continuous Evolution`: timeout 600→**1800 s**, cron → `*/15 * * * *`
  (watchdog degradado). **Cron deshabilitado ahora** mientras no corra el supervisor.

### Intervalos reales
- Con supervisor: **~15–40 s** entre runs.
- Sin supervisor (solo cron cada 15 min): hasta 15 min + concurrencia ocasional.

### Tests
- Smoke del supervisor: módulo carga, habla con la API (list_runs/active_runs OK), detecta
  correctamente el run RUNNING existente. NO se ejecutó el loop infinito aquí (no es entorno
  persistente). `bash tools/run_domain_tests.sh` NO ejecutado en esta sesión (sin cambios de
  dominio); último estado conocido: 218 tests OK (ciclo 19).
- Gradle/Android: NO VERIFICADO (sin Android SDK).

### Siguiente prioridad
- El usuario arranca el supervisor en su máquina (comando único en `tools/SUPERVISOR.md`).
  Tras eso, cada run continúa el desarrollo desde el HEAD de `openhands/autonomous-ordia`.

## SESIÓN 019 — Ciclo 20b (NaturalTaskParser: recurrencia semanal de varios días)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: `73e4fef` (tras sincronizar con remoto: otro run commiteó "supervisor
  persistente" — no tocaba mis archivos; rebase no destructivo vía stash+pull+pop limpio)
- **Trigger**: auditoría funcional del parser con probe JVM de 20 casos reales de captura.

### Problema seleccionado

P1 — **Pérdida de datos silenciosa en rutinas semanales de varios días.** La forma natural
más común en español para una rutina semanal con varios días ("reunión los lunes y jueves")
NO se parseaba correctamente: el parser solo admitía dos días con el patrón `cada X y Z`,
pero `todos los X` y `los X` capturaban **un solo día**. El resultado era doblemente malo:

- "reunión los lunes y jueves a las 10" → `title='reunión y'` (residuo "y jueves" en el título)
- `recurrenceDays='1'` → la rutina repetía **solo los lunes**, perdiendo el jueves.

Una tarea recurrente que pierde días es una promesa rota al usuario: una cita o reunión que
no aparece en los días correctos. Es un bug de datos (persistencia/rutinas), P1.

### Causa raíz

En `NaturalTaskParser.parseRecurrence()`, `weeklyDayPatterns` eran **tres** regex separadas
con grupos de captura por día. Solo la variante `cada` tenía un grupo opcional `y Z` para un
segundo día. Las variantes `todos los X` y `los X` solo capturaban un día y, al aparecer "y
jueves" a continuación, este no casaba con ningún patrón y quedaba como residuo en el título.
Peor: la rutina resultante solo contenía el primer día.

### Solución

- Unificación de los 3 patrones en **uno solo** `dayListPattern` que captura un **conector**
  (`todos los|cada|los`) seguido de una **lista de días** separados por `,` o `y`. Los días
  se extraen con `dayNameRegex.findAll(groupValues[1])` → `distinct().sorted().toList()`.
- Menos código, más capacidad: además de arreglar "los X y Z" y "todos los X y Z", ahora
  soporta **listas con comas** ("lunes, miércoles y viernes" → `1,3,5`).
- No rompe casos existentes: "todos los viernes" → `5`; "cada lunes y jueves" → `1,4`;
  "los viernes" → `5`. Casos no-día ("cada 2 semanas", "cada día", "los lapices") no casan
  y caen a su lógica correspondiente (intervalo / DAILY / sin recurrencia).

### Archivos modificados

- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`
  (`parseRecurrence`: 3 patrones → 1 `dayListPattern` + `dayNameRegex`)
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`
  (+3 tests: `parsesLosWeekdaysWithY`, `parsesTodosLosWeekdaysWithY`, `parsesCommaDayList`)

### Tests

- Red primero: la probe mostró el bug (`title='reunión y'`, `days='1'`); tras el fix
  `title='reunión'`, `days='1,4'`.
- `bash tools/run_domain_tests.sh` → **221 tests OK** (25 clases) (218 + 3 netos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales

- La probe también confirmó que el resto del parser (recurrencias mensuales/anuales, duraciones,
  prioridades, recordatorios relativos, categorías) funciona correctamente en los 20 casos
  probados — incluyendo los fixes de los ciclos 17-19.
- Continúa la línea de corrección de bugs de datos en recurrencias del parser, cerrando otra
  forma de pérdida silenciosa.

### Commit / push
- fix(parser): recurrencia semanal de varios días ("los lunes y jueves") — push al cierre.

### Siguiente prioridad
- Continuar auditoría funcional: UniversalCaptureEngine, FocusClock/FocusTimerRules,
  GuardianCoach, OnboardingCompleter, PlannerCalendar, CommandPaletteCatalog,
  TaskMutationGate, QuietHoursRules. Revisar el minor issue de SummaryEngine
  (`remainingMinutesToday` sin coerce como DayPlanner).

---

## Ciclo 20 — Unidad 2 (SummaryEngine / DayPlanner — coherencia de minutos)

- Fecha: 2026-08-11
- HEAD inicial: 9302e5a (tras push de la unidad 1)
- Rama: `openhands/autonomous-ordia`

### Problema seleccionado
P2 (consistencia/correctitud de métrica de headline): la badge "Xm" de la
pantalla Today (`SummaryEngine.remainingMinutesToday`) sumaba
`task.durationMinutes` en bruto, mientras `DayPlanner` (el plan del día que el
usuario ve justo debajo) coerciona cada tarea a `coerceIn(10, 180)`. El
headline y el plan discrepaban: una tarea de 600m mostraba "600m" pendientes
pero el plan solo agenda 180m; una tarea con duración por defecto 5m contaba
5m cuando el plan la trataba como 10m.

### Causa raíz
Tres motores trataban la duración de forma distinta: `DayPlanner`
`coerceIn(10,180)`, `WhatNowEngine.isInProgressNow` `coerceAtLeast(10)`, y
`SummaryEngine` suma cruda. Números mágicos `10`/`180` duplicados.

### Solución
Fuente única de verdad `TaskRules.plannedDuration(task): Int` =
`task.durationMinutes.coerceIn(MIN_PLAN_MINUTES, MAX_PLAN_MINUTES)` con
constantes `MIN_PLAN_MINUTES=10`, `MAX_PLAN_MINUTES=180`. Usada por
`DayPlanner.build` (sustituye `coerceIn(10,180)`) y `SummaryEngine.summarize`
(sustituye `sumOf { it.durationMinutes }`). `WhatNowEngine` se deja intacto
(su `coerceAtLeast(10)` detecta "¿sigue en curso?", interés distinto; capar a
180 cambiaría comportamiento). Menos duplicación, más coherencia.

### Archivos
- `app/src/main/java/com/ordia/app/domain/TaskRules.kt` (+`plannedDuration`, constantes)
- `app/src/main/java/com/ordia/app/domain/DayPlanner.kt` (usa `TaskRules.plannedDuration`)
- `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt` (usa `TaskRules.plannedDuration`)
- `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`
  (+2 tests: `remainingMinutesCoercesPerTaskToPlanBounds`, `remainingMinutesMatchesDayPlannerScheduledMinutes`)

### Tests
- `bash tools/run_domain_tests.sh` â **223 tests OK** (25 clases) (221 + 2 netos).
- `bash tools/run_domain_checks.sh` â **25 assertions OK**.
- NO VERIFICADO: gradle/lint/Android/Room (sin Android SDK).

### Hallazgos adicionales
- Auditoría rápida de `WhatNowEngine`: lógica de ranking correcta
  (IN_PROGRESS > en-curso-ahora > atrasada > vence-hoy > urgente > alta > inbox;
  scheduled-later se respeta con rank -1). Sin bug P1 encontrado.
- `TaskRules.nextBestTask` usa `thenByDescending { priorityScore }` mientras
  `DayPlanner`/`WhatNow` usan `compareByDescending` con `priorityScore`; coherente.

### Commit / push
- perf(ux): coherencia de minutos plan vs resumen (`TaskRules.plannedDuration`) â push al cierre.

### Siguiente prioridad
- Continuar auditoría funcional no-parser: UniversalCaptureEngine, FocusClock/FocusTimerRules,
  GuardianCoach, OnboardingCompleter, PlannerCalendar, CommandPaletteCatalog,
  TaskMutationGate, QuietHours. Buscar oportunidades de producto reales (P1 datos > P2).


---

## Ciclo 20 — Unidad 3 (Rutinas — duplicados al re-disparar tras completar)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 6f2ae9f (cierre Unidad 2)
- **Prioridad**: P1 (persistencia/duplicación de tareas)
- **Área**: Rutinas (`RoutineRules.wasRunToday`, `OrdiaViewModel.runRoutine`)

### Problema seleccionado
`RoutineRules.wasRunToday` solo contaba tareas **pendientes** creadas hoy
(`!completed && !archived && status != CANCELLED`). Al completar todas las
tareas de la rutina de hoy, `wasRunToday` devolvía `false` → un nuevo disparo de
`runRoutine` (reabrir app, worker, o tap manual) volvía a añadir los pasos →
**tareas duplicadas** en la bandeja justo cuando el usuario había sido productivo.
El guardia anti-duplicados se derrotaba a sí mismo.

### Causa raíz
El filtro exigía estado pendiente, confundiendo "rutina ejecutada hoy" con
"rutina ejecutada hoy y aún pendiente". Completar la tanda = la rutina YA se
ejecutó; no debe re-añadirse. El test `wasRunTodayFalseWhenTaskCompleted`
codificaba el bug como deseado.

### Solución
`wasRunToday` ahora devuelve true si existe al menos una tarea de la rutina
creada hoy y **no archivada ni cancelada** (la compleción ya no la excluye).
Archivado/cancelado se mantienen fuera (semántica de descarte explícito del
usuario; fuera del alcance del bug claro y ambigua). Cambio mínimo de 1 línea
en la condición + docstring que explica el porqué.

### Archivos
- `app/src/main/java/com/ordia/app/domain/RoutineRules.kt` (`wasRunToday`: quita `!task.completed`)
- `app/src/test/java/com/ordia/app/domain/RoutineRulesTest.kt`
  (corrige `wasRunTodayFalseWhenTaskCompleted` → `wasRunTodayTrueWhenCreatedTodayAndCompleted`;
  +`wasRunTodayTrueWhenTodayBatchPartiallyCompleted`)

### Tests
- `bash tools/run_domain_tests.sh` → **224 tests OK** (25 clases) (222 + 2 netos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/Android/ViewModel real (sin Android SDK; `runRoutine` en
  `OrdiaViewModel` requiere repositorios/Room).

### Hallazgos adicionales
- `ReminderSync.triggers`: lógica correcta (futuro-only, mismo disparo que
  `ReminderScheduler.schedule`; re-sincronización no duplica notificaciones).
- `TaskSnapshotCodec`: robusto para su propósito (decode defensivo con
  `runCatching`, enums con fallback; snapshots los produce el propio codec así
  las keys siempre existen). El caso "key ausente → 0 (1970)" solo afecta JSON
  externo/malformado, no snapshots internos → no es bug activo.
- `GuardianEngine`: XP/mood/archetype derivados de registros reales (no random,
  no reglas disfrazadas de IA) → honesto, conforme a "IA honesta".
- `DayPlanner`: recupera tareas vencidas (`overdueByDate` las incluye y ordena
  primero); `scheduledMinutes`/`remainingMinutes` coherentes con `SummaryEngine`.
- `SearchEngine`: sólido; sort solo prioriza "empieza con" → oportunidad P2
  menor (vencidas/incompletas antes) registrada mentalmente, no implementada
  (evitar feature bloat; impacto bajo).

### AI_AUTONOMY actualizado
- `BACKLOG.md`: +entrada P1 Rutinas (FIXED→VERIFIED ciclo 20); nota de auditoría
  de Rutinas actualizada.
- `CURRENT_STATE.md`: +Unidad 3 en "Último trabajo realizado".

### Commit / push
- fix(rutinas): evitar duplicados al re-disparar rutina tras completar la tanda del día.
- HEAD: e5773c0.

### Siguiente prioridad
- Seguir auditoría funcional no-parser (OnboardingCompleter, PlannerCalendar,
  CommandPaletteCatalog, QuietHoursRules, SubtaskRules, HabitRules). Buscar P1
  datos/recordatorios antes que P2 cosmetic.

## Ciclo 20 — Unidad 4 (Merge de integración con remoto)

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: e5773c0 (Unidad 3, no pusheado por divergencia)
- **Prioridad**: Integridad de rama (anti-colisión)

### Problema seleccionado
El push de Unidad 3 (e5773c0) fue rechazado: el remoto
`openhands/autonomous-ordia` avanzó con trabajo de otra ejecución (commits
`7127b7e` summary subtasks, `c1bab04` search "nota X", `db62f6d` docs, merges
`5c21ee6`/`f8fa87d`). Mi base (6f2ae9f) estaba obsoleta.

### Causa raíz
Ejecuciones concurrentes sobre la misma rama. Mi fix tocaba
`RoutineRules.kt`/`RoutineRulesTest.kt`; el remoto tocaba
`SearchEngine.kt`/`SummaryEngine.kt` + tests. Sin conflicto de código. Solo
los docs `AI_AUTONOMY/BACKLOG.md` y `CURRENT_STATE.md` chocaron (ambos
append-only).

### Solución
`git merge origin/openhands/autonomous-ordia` (no force, no reset destructivo;
patrón de merge ya usado por el remoto en 5c21ee6/f8fa87d). Resueltos los
conflictos de docs combinando ambas entradas (sin pérdida de trabajo): la fila
de Rutinas con la nota del fix ciclo 20 + la nueva fila de auditoría de motores
no-parser; en CURRENT_STATE se preservaron las 3 unidades + la auditoría 20a.
RUN_LOG auto-mergeó limpio.

### Tests
- `bash tools/run_domain_tests.sh` → **228 tests OK** (25 clases) (224 locales
  + 4 del remoto: `summaryCountsOnlyRootTasks`,
  `noteTypeFilterDoesNotRequireTheWordNotaInContent` y asociados).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- NO VERIFICADO: gradle/Android/ViewModel real.

### Archivos
- `AI_AUTONOMY/BACKLOG.md`, `AI_AUTONOMY/CURRENT_STATE.md` (resolución de
  conflicto).
- Trae del remoto: `SearchEngine.kt`, `SummaryEngine.kt`, sus tests, RUN_LOG.

### Commit / push
- Merge commit 6e99822: "Merge remote-tracking branch 'origin/openhands/autonomous-ordia'
  into openhands/autonomous-ordia".
- Push OK a `openhands/autonomous-ordia` con `github_token` (f8fa87d..6e99822).
  (Nota: `GITHUB_TOKEN` rechazado; `github_token` válido.)

### Siguiente prioridad
- Auditoría no-parser restante: `OnboardingCompleter`, `PlannerCalendar`,
  `CommandPaletteCatalog`, `SubtaskRules`, `HabitRules`. Buscar P1 datos antes
  que P2.

## Ciclo 21 — Unidad 1 (NaturalTaskParser — "mañana por la tarde/noche/mañana")

- **Fecha (UTC)**: 2026-08-11
- **HEAD inicial**: 6e99822 (origin/openhands/autonomous-ordia, sincronizado tras merge ciclo 20)
- **Prioridad**: P1 — captura/persistencia (título mangulado + hora incorrecta en frase cotidiana)

### Problema seleccionado
Investigación TDD del parser (continuación del ciclo 20) reveló que la frase
natural "mañana por la tarde" (y variantes noche/mañana) se procesaba mal:
- el título quedaba como "Reunión por la tarde" (residuo "por la tarde" huérfano);
- la hora se fijaba en 09:00 (default) en vez de la hora canónica de la tarde (15:00).

"mañana por la mañana" dejaba "por la" huérfano. Frase cotidianísima en español.

### Causa raíz
`standalonePartOfDayPattern` solo reconocía los conectores "a la" y "de la"
(p.ej. "a la tarde", "de la tarde"), NO "por la". Y el mapa de horas canónicas
(`standalonePartOfDayTimes`) no incluía "mañana"/"manana". Al no casar, la
frase no se consumía como señal horaria ni se limpiaba del título; como
`parsedTime` quedaba nulo, `dueAt` usaba `LocalTime.of(9,0)` por defecto.

### Solución
Fix mínimo (un patrón, sin nueva pantalla): extender
`standalonePartOfDayPattern` a `(?:a\s+la|de\s+la|por\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)`
y añadir `mañana`/`manana` → 09:00 (AM) al mapa. Esto corrige título **y** hora
a la vez para tarde(15:00)/noche(21:00)/mañana(09:00), y como "mañana" es AM
(no está en `partOfDayPmKeys`), no introduce falsos offsets PM. Verificado que
no rompe "a las 9 de la mañana" (el meridiem explícito del `timePatterns` tiene
prioridad) ni "a primera hora de la mañana" (orden de limpieza del título
preserva el patrón `primeraHoraPattern`).

### Tests
- `bash tools/run_domain_tests.sh` → **232 tests OK** (25 clases) (228 + 4 nuevos).
- `bash tools/run_domain_checks.sh` → **25 assertions OK**.
- TDD: los 4 tests se escribieron primero y fallaron exactamente con el bug
  documentado (`expected:<Reunión[]> but was:<Reunión[ por la tarde]>`, etc.),
  luego el fix los puso en verde.
- NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales.

### Archivos
- `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (patrón + mapa + doc).
- `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+4 tests).

### Commit / push
- fix(parser): reconocer "mañana por la tarde/noche/mañana" (título + hora canónica).
- HEAD final: por commit.

### Siguiente prioridad
- Continuar auditoría del parser: "pasado mañana por la tarde", "el fin de semana"
  (dueAt=null, sin soporte de fin de semana), "mañana a primera hora". Validar
  combinaciones con recurrencia. Buscar P1 datos/recordatorios en workers/backup.


---

---

## 2026-08-11 — Continuous Delivery + Self-Update + Supervisor v2 (OpenHands)

### Objetivo
Integrar Continuous Evolution → Continuous Delivery → Self-Update para que las
builds de `openhands/autonomous-ordia` lleguen al teléfono del usuario y se
instalen automáticamente.

### Cambios
- Delivery workflow `.github/workflows/openhands-delivery.yml` (nuevo): push a
  `openhands/autonomous-ordia` → tests+lint+assemble `previewAdvanced` release →
  firma con `ORDIA_UPDATE_KEYSTORE_*` → SHA-256 → GitHub Release con naming
  EXACTO que `UpdateSecurityRules` espera (`v3.0.N-code-C`,
  `Ordia-3.0-code-C.apk` + `.sha256`). Gates; concurrency cancela builds obsoletas;
  no sobrescribe releases.
- Watchdog `.github/workflows/ordia-openhands-watchdog.yml` (nuevo): cada 15 min,
  comprueba runs activos + lease gist; si supervisor caído y sin runs, rehabilita
  cron + dispatch recovery. Concurrencia 1.
- Supervisor v2 `tools/ordia_supervisor.py`: lock cross-platform (fcntl/msvcrt/
  pidfile), lease distribuido vía GitHub Gist (heartbeat ~90s, TTL 300s, expira si
  muere sin finally), STOP/PAUSE/RESUME, backoff.
- Service mode: `tools/docker-compose.yml`, `tools/ordia-supervisor.service`,
  `tools/install-supervisor.sh`.
- Observabilidad: `tools/ordia-status.py`. Keystore guía: `tools/keystore/README.md`.
- Tests supervisor: `tools/test_supervisor.py`.

### Bug crítico encontrado y corregido
`android-ci.yml` existente publicaba releases con tag `v3.0.0-build.N` y asset
`Ordia-3.0-signed.apk`, pero `UpdateSecurityRules.parseVersionCodeFromTag` espera
`v3.0.N-code-C` y `expectedApkName`=`Ordia-3.0-code-C.apk`. El auto-updater NUNCA
detectaba actualización (parseTag→null→fail). El nuevo workflow corrige el mismatch.
La rama autónoma además NO disparaba CI (solo main/jules).

### Tests
- `python3 tools/test_supervisor.py` → PASS (lock cross-platform, lease TTL, guard
  concurrencia, backoff acotado).
- Naming verificado con regex: tag/apk/sha coinciden con `UpdateSecurityRules`.
- `UpdateSecurityRulesTest` (Kotlin, ya existente) cubre el naming del workflow.
- Gradle/Android: NO VERIFICADO (sin SDK). Watchdog/supervisor loop infinito: NO
  VERIFICADO (no es entorno persistente).

### Commit / push
- `feat(infra): continuous delivery + supervisor v2 + watchdog + self-update infra`

### Siguiente prioridad
- Tras configurar los GitHub Secrets de firma, la primera push a la rama debe
  producir una Release instalable. Verificar end-to-end cuando existan secretos.
- Volver al ciclo normal de Evolution (auditar/implementar features de Ordía).


---

## Ciclo 22 — 2026-08-11 (Sesión OpenHands — auditoría parser: días relativos + hora)

- **HEAD inicial**: `379886e` (sync OK con `origin/openhands/autonomous-ordia`; base local
  estaba obsoleta tras 3 commits del run de infra → `git stash` + `pull --ff-only` + `stash pop`,
  sin force ni reset destructivo).
- **Anti-colisión**: al iniciar, la rama remota había avanzado 3 commits; mi trabajo del
  parser (no commiteado) se preservó vía stash y se re-aplicó limpio sobre el HEAD actualizado.

### Problema seleccionado (P1 — captura)
`NaturalTaskParser` perdía la hora cuando se combinaba **fecha relativa en días** con **hora
explícita**. Ej: `"Entregar informe dentro de 3 días a primera hora"` → el parser calculaba
la fecha relativa (`now + 3d`) y, al existir `parsedTime` (`09:00` de "a primera hora"), la
rama de `dueAt` tomaba el camino de `parsedTime != null` usando `today + parsedTime` en vez de
combinar la fecha relativa con la hora. Resultado: la tarea quedaba para **hoy a las 09:00**
(perdiendo los 3 días) o, según el orden de ramas, se descartaba la hora y se usaba sólo la
fecha relativa a medianoche. Ambos caminos eran incorrectos para la intención del usuario.

### Causa raíz
El conector de tiempo relativo distingue horas (`"dentro de 3 horas"`) de días
(`"dentro de 3 días"`), pero la rama final de `dueAt` no usaba esa distinción: cuando había
`parsedTime`, ignoraba `relativeDueAt` (fecha relativa) y caía a `today + parsedTime`.

### Solución (cambio mínimo, sin nueva pantalla)
- Añadido flag `relativeIsDays` (true cuando el match relativo es en días, false para horas).
- Nueva rama en `dueAt`: si `relativeDueAt != null && relativeIsDays && parsedTime != null`
  → combinar la **fecha** de `relativeDueAt` con la **hora** de `parsedTime`. Así
  `"dentro de 3 días a primera hora"` → `now+3d 09:00` (antes `09:00` hoy / hora perdida).
- Las horas relativas (`"dentro de 3 horas"`) siguen sin combinar con `parsedTime`
  (no tiene sentido: la hora relativa ya define el instante).

### Tests
- `bash tools/run_domain_tests.sh` → **235 tests PASS** (232 previos + 3 nuevos de regresión:
  `dentroDe3DiasAPrimeraHoraCombinaFechaRelativaConHora`,
  `dentroDeNDiasConHoraExplícitaCombinaFechaYHora`,
  `en3DiasALasCincoDeLaTardeCombinaFechaYHora`).
- `bash tools/run_domain_checks.sh` → smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

### Commit / push
- `fix(parser): combinar días relativos con hora explícita (dentro de 3 días a primera hora)`

### Siguiente prioridad
- Continuar auditoría del parser: "este fin de semana" / "el fin de semana" (dueAt=null,
  sin soporte de fin de semana — gap P3). Validar "mañana a primera hora" vs "a primera hora".
- Auditar lógica de sincronización de recordatorios (workers) en busca de P1 datos.

---

### Verificación CI (run 31491777388, post-fix)
- ✓ Verificar (tests + lint + assemble previewAdvanced release) — PASSED en CI
  real (Gradle clean+test+lint+assembleRelease verde en runner GitHub).
- ✓ Localizar APK sin firmar — PASSED.
- ✓ Rechazar APK debuggable antes de firmar — PASSED (gate de seguridad funciona).
- ✗ Restaurar keystore y firmar APK — falló EXACTAMENTE en
  `test -n "${KEYSTORE_B64:-}"` → "Falta el secret ORDIA_UPDATE_KEYSTORE_BASE64".
  Comportamiento esperado y seguro: sin secrets de firma, no publica nada.
- Bug gradlew exit 126 (gradlew tracked 100644) corregido: chmod +x en workflow +
  modo 100755 en git.

---

## Ciclo 23 — 2026-08-11 (Sesión OpenHands — objetivo: APK instalable + self-update)

- **HEAD inicial**: `e2e1314` (sync OK con remote).
- **HEAD final**: `23289c3` (pushed, working tree limpio).

### Problema seleccionado (P0/P1 — objetivo usuario)
El usuario quiere una APK instalable que pueda auto-actualizarse en el futuro.
Auditoría reveló DOS bugs reales que hacían que el self-update estuviera **silenciosamente
muerto** aunque toda la infraestructura existía y los tests pasaban:

1. **P0 — `SELF_UPDATE_ENABLED` overridden to false on release builds** (root cause):
   `app/build.gradle.kts` declaraba `buildConfigField("boolean","SELF_UPDATE_ENABLED","false")`
   en el buildType `release`. En AGP, un campo de buildType **pisa** al del product flavor
   (los buildTypes se aplican al final). Por tanto `previewAdvancedRelease` y
   `previewFullRelease` compilaban con `SELF_UPDATE_ENABLED=false` aunque el flavor dijera
   `true`. Resultado: una APK perfectamente construida y firmada **jamás** podría buscar ni
   instalar actualizaciones — `OrdiaUpdateManager.schedule()` y `checkDetailed()` retornan
   inmediatamente. Funcionalidad falsamente implementada (exactamente el tipo que la misión
   prohíbe). FIX: eliminar el override del buildType; cada flavor declara su valor correcto.

2. **P1 — contrato CI↔updater sin test de regresión**: ya existía el mismatch resuelto
   (tag `v3.0.N-code-C` / asset `Ordia-3.0-code-C.apk`), pero no había test que impidiera
   que el bug reaparezca silenciosamente. FIX: añadidos 2 tests contract en
   `UpdateSecurityRulesTest`:
   - `ciWorkflowNaming_isAcceptedByUpdater`: replica las fórmulas EXACTAS de
     `openhands-delivery.yml` y afirma que `UpdateSecurityRules` las acepta (parseTag,
     expectedApkName, selectExpectedApk, parseChecksum, URL confiable).
   - `ciWorkflowNaming_rejectsOldBrokenFormats`: asegura que los formatos viejos rotos
     (`v3.0.0-build.5`, `Ordia-3.0-signed.apk`, `Ordia-3.0.apk`) sigan rechazados.

### Solución
- Mínima: 1 línea eliminada en `app/build.gradle.kts` + 49 líneas de tests contract.
- No se añadió infraestructura nueva (el usuario pidió no sobre-ingeniería).

### Auditoría del updater (confirmado correcto, NO modificado)
- `OrdiaUpdateManager.checkDetailed`: consulta `releases/latest`, rechaza draft/prerelease,
  parsea versionCode del tag, rechaza `<= VERSION_CODE`, valida URL host/github.com,
  selecciona asset exacto por nombre, descarga con checksum SHA-256, valida tamaño.
- `validateDownloadedPackageLocked`: copia+hash del bytes privados, verifica SHA-256
  doble (fuente + privado), `verifyArchive` valida packageName==installed, versionCode==tag,
  `signaturesAreCompatible` (soporta rotación de claves vía signingCertificateHistory).
- `UpdateInstallActivity`: valida ANTES de instalar, `canRequestPackageInstalls()`,
  lleva a `ACTION_MANAGE_UNKNOWN_APP_SOURCES` si falta, lanza `ACTION_INSTALL_PACKAGE`
  (instalador oficial de Android). NO es instalación silenciosa (respeta Android).
- Post-actualización: datos sobreviven (misma applicationId + firma compatible → Android
  trata como upgrade, Room migra, DataStore persiste).

### Tests
- CI run **31500689793** (post-fix): `Verificar` ✓ PASSED en runner real — incluye los 2
  tests contract nuevos + clean + lint + assemblePreviewAdvancedRelease.
- Local: NO VERIFICADO gradle (sin Android SDK en agente).
- `Restaurar keystore y firmar APK` ✗ en guard `ORDIA_UPDATE_KEYSTORE_BASE64`
  (comportamiento seguro esperado — sin secrets de firma, no publica).

### Bloqueo externo real (NO resolvible por el agente)
El `GITHUB_TOKEN` del agente **no puede gestionar Actions secrets** (HTTP 403 en
`/actions/secrets/public-key` y `/gists`). El scope es vacío. Por tanto, los 4 secrets
de firma (`ORDIA_UPDATE_KEYSTORE_*`) **deben ser cargados por el usuario una vez**.
Se generó un keystore con keytool y se documentó el flujo de 1 comando en
`tools/keystore/README.md`. El keystore no se subió al repo (riesgo de seguridad).

### Commit / push
- `fix(updater): SELF_UPDATE_ENABLED was silently overridden false on release builds`

### Siguiente prioridad
- **Usuario**: cargar los 4 GitHub Secrets de firma (ver `tools/keystore/README.md`).
  Tras ello, un push vacío produce la primera APK instalable + auto-actualizable.
- Tras verificar end-to-end (instalar N, publicar N+1, comprobar que Ordía lo detecta),
  cerrar ORD-UPD como VERIFIED.


---

## Ciclo 24 — 2026-08-11 — Signed APK VERIFIED end-to-end (T4 COMPLETE)

### Objetivo
Producir la primera APK instalable firmada vía CI y verificar la cadena completa (artefacto, release, SHA-256, firma, packageName, versionCode, no-debuggable, updater compiled).

### Contexto
- Usuario cargó manualmente los 4 GitHub Secrets de firma (`ORDIA_UPDATE_KEYSTORE_*`).
- Ciclo 23 dejó la rama `openhands/autonomous-ordia` en `cd96ee5` con la fix P0 del override `SELF_UPDATE_ENABLED=false` y un commit vacío de trigger.
- CI run `31505311240` (trigger push) encolado al inicio de este ciclo.

### Cambios de código
- NINGUNO. El trabajo previo (ciclo 23) era correcto; este ciclo fue de verificación.

### Resultado CI run 31505311240 (job 93825270234) — TODOS VERDES
- ✓ Set up job / Checkout / Java 17 / Gradle 8.13 / versionCode
- ✓ Verificar (tests + lint + assemble previewAdvanced release)
- ✓ Localizar APK sin firmar
- ✓ Rechazar APK debuggable antes de firmar
- ✓ **Restaurar keystore y firmar APK** (antes fallaba por secrets ausentes → ahora pasa)
- ✓ Verificar versionCode interno de la APK
- ✓ Calcular SHA-256
- ✓ Subir APK firmado como artefacto
- ✓ Publicar GitHub Release inmutable
- ✓ Complete job

### Release publicada
- Tag: `v3.0.8-code-1300000801` (Latest, no draft, no prerelease)
- Assets:
  - `Ordia-3.0-code-1300000801.apk` (2 738 083 bytes)
  - `Ordia-3.0-code-1300000801.apk.sha256` (96 bytes)
- URL: https://github.com/wandersepulveda2013/ordia-android/releases/tag/v3.0.8-code-1300000801
- Commit: cd96ee5bed9bab37f3f0fbb39bafe3f4d24fd8b2

### Artefacto CI
- `ordia-previewadvanced-signed` (2 038 290 bytes, id 9107169334) adjunto a la run.

### Verificación independiente local (descargué la APK de la release)
- **SHA-256** = `74953d2999c9a2c29860cddf38373b5685275e5594a9601676c49151f5b05a83` — coincide EXACTO con el `.sha256` publicado y con el reportado por CI.
- **versionCode** (parseado del AndroidManifest.xml binario) = `1300000801` ✓
- **versionName** = `3.0.8-preview-advanced.1` ✓
- **applicationId / packageName** = `com.ordia.app.preview.advanced` ✓ (string idx 111)
- **debuggable** = AUSENTE en el manifest → NO debuggable ✓
- **Firma** = APK Signature Scheme v2 (`0x7109871a`) presente en el APK Signing Block ✓ + v1 JAR sig (`META-INF/ORDIA-UP.SF` + `ORDIA-UP.RSA`, alias `ordia-update`/ORDIA-UP).
- **Updater compilado en** = `UpdateInstallActivity` presente en `classes.dex` ✓
- **SELF_UPDATE_ENABLED** = true confirmado en código HEAD (líneas 64/77 true; release{} sin override — la fix P0 del ciclo 23 está activa en esta build).

### Tests
- Contract tests `UpdateSecurityRulesTest` ejecutados dentro del step `Verificar` (verde).
- Sin tests nuevos este ciclo (no hubo cambios de código).

### Estado
- **T4 VERIFIED**: CI produce APK firmada instalable + auto-actualizable reproduciblemente.
- Queda T5 (verificar auto-update N→N+1 en dispositivo real) — requiere hardware Android; el agente no puede ejecutarlo. Marcado BLOCKED-external en BACKLOG.

### Siguiente prioridad
- T5 end-to-end en teléfono: instalar `Ordia-3.0-code-1300000801.apk`, disparar una nueva release (versionCode superior), y confirmar que Ordía la detecta/descarga/instala. Fuera del alcance del agente sin dispositivo.

---

## Ciclo 25 — 2026-08-11 — Primera mejora P2 visible (simpleza UI) publicada en release

### Objetivo
Reanudar la evolución autónoma bajo la nueva MISIÓN (administrar producto integralmente,
priorizar P2 visible cuando no hay P0/P1). Reducir ruido visual en la pantalla principal.

### Contexto
- MISIÓN actualizada (commit b004771): rol de administrador autónomo permanente del producto,
  P2 = evolución real visible, BLOCKED-external no detiene evolución.
- Sin P0/P1 abiertos (T4 delivery VERIFIED; T5 BLOCKED-external dispositivo).

### Problema P2 encontrado
TodayScreen mostraba What Now DOS veces: (1) como CompactAction en la fila de 3 botones y
(2) como Card dedicada con eyebrow+razón justo debajo. Mismo destino
(onTask(whatNow.id) ?: onOpenInbox), dos entradas = ruido visual y jerarquía confusa.
El action duplicado no aportaba nada que la card rica no cumpliera mejor.

### Causa raíz
Decisión de diseño previa: la fila de 3 CompactActions incluía un atajo a What Now
redundante con la card principal. No es un bug; es ruido de jerarquía.

### Solución
- Quité el CompactAction "What Now" de la fila (1 línea). La card dedicada sigue presente
  con eyebrow "SIGUIENTE PASO" + título + razón + flecha.
- La fila pasa de 3 a 2 actions equilibrados (Revisar mensajes, Nota rápida) con más
  respiración (spacing 8dp → 10dp).
- NO toqué el reloj de recomposición cada 60s: tras análisis tiene propósito funcional
  (detectar tareas que se vuelven overdue sin interacción del usuario). Quitarlo sería P1.
- Cadena today_what_now_action quedó sin referencia; la dejé en strings (no rompe).

### Archivos
- Modificado: app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt (1 inserción, 7 eliminaciones).

### Tests
- CI run 31520002066 (job 93874503437) — **success** ✓
  - Verificar (testReleaseUnitTest + lint + assemblePreviewAdvancedRelease) ✓
  - Rechazar APK debuggable ✓ / Firmar APK ✓ / Verificar versionCode ✓ / SHA-256 ✓
  - Publicar GitHub Release ✓
- SmokeTest.firstLaunchOrTodayScreen_isVisible busca "SIGUIENTE PASO" (eyebrow de la card
  What Now, NO tocada) → sigue pasando.
- Sin SDK local: compilación validada por CI.

### Release
- Tag: v3.0.11-code-1300001101 (Latest)
- Commit: 2dfce3a
- Esta es la TERCERA release firmada consecutiva reproducible (1300000801→0901→1101).
  Confirma la cadena de entrega de la nueva MISIÓN: OBSERVAR→…→RELEASE→USUARIO.

### Hallazgos
- android-ci.yml solo corre en main/jules/autonomous-ordia (no en openhands/autonomous-ordia),
  pero openhands-delivery.yml incluye step Verificar equivalente → cobertura OK.
- Reproducibilidad del delivery confirmada: cada push produce release firmada.

### Estado
- Mejora P2 VERIFIED vía CI. Usuario verá Today más limpio en v3.0.11.

### Siguiente prioridad
- Otra mejora P2 visible: revisar CaptureScreen (captura rápida es núcleo del producto)
  o simplificar más pantallas principales. Continúa autónomamente.

---

## Ciclo 26 — 2026-08-11 — Dos mejoras P2 visibles (captura + icono) en una release

### Objetivo
Continuar evolución autónoma P2 visible. La MISIÓN exige CONTINUAR, no detenerse tras cada mejora.

### Mejoras

#### 1. CaptureScreen: aplanar Card anidada (P2 visual)
- Problema: el preview de interpretación era una `Card` DENTRO del `Card` principal de captura
  — patrón "card-in-card" que la MISIÓN prohíbe (ruido visual, doble contención).
- Solución: reemplazada por `Surface`(surfaceContainer, shape medium, sin border/elevation extra).
  Lee como sección del mismo contenedor. Sin cambio de comportamiento.
- Archivo: CaptureScreen.kt (commit 3f896db).

#### 2. TaskDetailScreen: icono deprecado (P2 backlog OPEN)
- Problema: `Icons.Outlined.InsertDriveFile` deprecado → lint warning.
- Solución: `Icons.AutoMirrored.Outlined.InsertDriveFile` (versión correcta para iconos espejables).
  Compose BOM 2026.06.00 la soporta.
- Archivo: TaskDetailScreen.kt (commit 837ca63). Backlog P2 "deprecated InsertDriveFile icon" → FIXED.

### Tests
- CI run 31522884362 (job 93884233254) — **success** ✓
  - Verificar (tests+lint+assemble) ✓ — ahora con 1 lint warning menos (icono corregido).
  - Firma ✓ / versionCode ✓ / SHA-256 ✓ / Release ✓.
- Run 31522754097 (3f896db solo) fue cancelada por concurrency (cancel-in-progress) → la release
  final 837ca63 contiene ambos cambios. Eficiente: no se publican releases intermedias obsoletas.

### Release
- Tag: v3.0.14-code-1300001401 (Latest). 4ta release firmada consecutiva.

### Auditoría (sin cambios de estado)
- NoteBlockCodec.decode ya robusto (fallback graceful, descarta solo elementos corruptos) — OK.
- NaturalTaskParser soporta sábado/domingo, "esta tarde/noche", "dentro de", "próximo" — backlog P3
  "weekend parser support" ya cubierto → marcar NOT_APPLICABLE.
- OnboardingScreen ya tiene verticalScroll + widthIn(max=520) + systemBarsPadding — caber OK.
- PlannerScreen / TasksScreen: sin card-in-card — limpios.

### Siguiente
- Continuar: próxima mejora P2 visible. No detenerse.


---

## Ciclo 27 — 2026-08-11 — Limpieza deuda técnica + accesibilidad (3 commits, 3 releases)

### Objetivo
Continuar evolución P2/P3. La MISIÓN exige ciclo interminable: no detenerse.

### Cambios (3 commits → 3 releases firmadas consecutivas)

#### 1. Eliminar string muerta today_what_now_action (commit 3d4c780 → release v3.0.15)
- Quedó sin referenciar tras el ciclo 25 (removí el action What Now duplicado).
- Sin dependencias (no hay values-es ni getIdentifier dinámico).

#### 2. Eliminar 46 strings huérfanas intel_* (commit e714f57 → release v3.0.16)
- Feature de modelo local/Gemma (intel_mode_local_*, intel_download_*, intel_state_*) se simplificó
  fuera de IntelligenceScreen pero dejó 46 cadenas definidas y sin referenciar.
- Las 12 intel_* todavía usadas por IntelligenceScreen se conservaron.
- Script Python removió líneas; XML validado; verificado que las 12 usadas siguen presentes.

#### 3. Accesibilidad TodayScreen planner IconButton (commit bd82841 → release v3.0.17)
- IconButton(onOpenPlanner) con Icon(ArrowForward, null) → null hacía que TalkBack anunciara
  control sin etiqueta.
- Añadida string today_open_planner_cd y usada como contentDescription.
- Auditoría completa de IconButtons accionables solos: todos los demás ya tienen contentDescription.

### Tests
- CI runs: 31524634470 (e714f57) success, 31525626150 (bd82841) success.
- Verificar (tests+lint+assemble) OK en ambos. Firma OK / release OK.
- 6 releases firmadas consecutivas funcionando (v3.0.12 → v3.0.17).

### Auditoría (sin cambios de estado — ya correcto)
- BackupManager.restore: valida SHA-256, versión, secciones, fecha, faltantes; IllegalArgumentException
  con mensajes claros; sin catch vacío. Backlog P2 manifiesto corrupto → REVISADO (no requiere fix).
- i18n: sin texto hardcodeado en pantallas (todo via stringResource). Backlog P2 → REVISADO.
- AppComponents/VirtualGuardian (trabajo de Jules): quadraticBezierTo correcto (no deprecado),
  LocalClipboardManager correcto (API moderna), iconos null solo en Buttons con texto (correcto).
- NoteBlockCodec.decode robusto (fallback graceful). NaturalTaskParser soporta días weekend individuales.
- OnboardingScreen tiene scroll. Planner/Tasks/Conversations/Intelligence sin card-in-card.

### Hallazgos para próximas ejecuciones
- Quedan ~17 strings sin uso adicionales (common_yes/no, ime_service_label, etc.) — P3 bajo.
- Frase compuesta "este fin de semana" no soportada en parser (P3, no pérdida datos).
- 6-variant compile check sigue OPEN (requiere CI/env Android dedicado).

### Siguiente
- Continuar ciclo interminable. Próxima mejora P2/P3 visible.

---

## Ciclo 28 — Accesibilidad (roles semánticos), strings 100% limpios, UX adjuntos — 2026-08-11T21:Z

### Objetivo
Continuar el ciclo interminable de mejora continua P2/P3. Cerrar la auditoría de
`Modifier.clickable` sin `Role` y consolidar deuda técnica de strings/UX.

### Cambios (5 commits → 5 releases firmadas consecutivas)

#### 1. Role.Button en OrdiaListItem (commit 109c14d → release v3.0.19)
- `AppComponents.kt:260` OrdiaListItem row clickable sin role → TalkBack sin hint de botón.
- Añadido `role = Role.Button`. Sin cambio visual.

#### 2. Roles semánticos en clickables restantes (commit b39fd73 → release v3.0.20)
- CaptureScreen:378 capture card → Role.Button.
- PlannerScreen:583 day cell → Role.Button.
- PlannerScreen:829 conflict toggle row (con Checkbox) → Role.Switch.
- AppComponents:147 EmptyState action text → Role.Button.
- Verificado: 0 `.clickable(` sin role en `app/src/main`. Auditoría completada.

#### 3. 10 strings huérfanas eliminadas (commit a71cf42 → release v3.0.21)
- common_yes/no, common_downloaded/not_downloaded (sin diálogos de confirmación que las usen).
- more_notes_desc/more_planner_desc (Notes/Planner no aparecen en MoreScreen).
- search_empty_prompt_*/search_field_label/search_header_subtitle (reemplazadas por
  search_palette_*/search_no_results_* en SearchScreen).
- Verificado cero `getIdentifier` dinámicos; manifests de variantes (ime_service_label,
  notification_listener_label, shortcut_*) conservados. Script recursivo sobre app/src/**:
  defined=1024 == referenced=1024, unused=0. **Tabla de strings 100% limpia.**

#### 4. Toast al no poder abrir adjunto en NoteEditor (commit cf1e4df → release v3.0.22)
- `NoteEditorScreen` tragaba `startActivity` fallido en runCatching silencioso (tap → nada).
- TaskDetailScreen ya mostraba toast. Alineado NoteEditor con patrón + string
  `note_editor_open_attachment_failed`. Consistencia UX.

#### 5. Log al fallar permiso persistente de adjunto en Capture (commit 072c252 → release v3.0.23)
- `CaptureScreen` takePersistableUriPermission en runCatching silencioso; sin traza si fallaba.
- Añadido `Log.w` en onFailure. Documentado P1 subyacente en BACKLOG (adjuntos guardan URI
  externo, no contenido copiado → solución robusta requiere copiar a filesDir + migración).

### Tests
- CI runs: 109c14d cancelled (superseded por b39fd73), b39fd73 success, a71cf42 success,
  cf1e4df success, 072c252 success. Verificar (tests+lint+assemble) OK en todos los pushados.
- Firma OK / release OK en cada push (workflow publica en cada push a la branch).
- 12 releases firmadas consecutivas funcionando (v3.0.12 → v3.0.23).

### Auditorías (sin hallazgos P0/P1 nuevos)
- **Componentes exportados (Manifest)**: MainActivity (MAIN/LAUNCHER, SEND */*, PROCESS_TEXT —
  legítimos para captura compartida), OrdiaCaptureTileService (BIND_QUICK_SETTINGS_TILE),
  ReminderResyncReceiver (TIMEZONE/TIME_SET/DATE_CHANGED del sistema), OrdiaWidgetProvider
  (APPWIDGET_UPDATE). previewAdvanced/Full: NotificationListener (BIND_NOTIFICATION_LISTENER_SERVICE),
  IME (BIND_INPUT_METHOD), FileProvider exported=false. Todos protegidos con permisos BIND del
  sistema o exported=false. Sin problema de seguridad.
- **startActivity externos**: IntelligenceActionExecutor (executeAppointment/executeCall) usan
  try/catch con fallback a executeTask — robusto. Navigation.launchCapture abre actividad propia.
  ConversationsScreen abre Settings del sistema. Sin issue.
- **catch vacíos**: 0 encontrados. runCatching con manejo o fallback en los críticos.

### Hallazgos para próximas ejecuciones
- P1 OPEN: adjuntos guardan URI externo (BACKLOG ciclo 28) — requiere sesión dedicada para
  migración a contenido interno.
- P2 OPEN: 6-variant compile check sigue pendiente (requiere env Android/CI dedicado).
- P3 OPEN: pulido visual de pantallas renovadas del workspace.

### Siguiente
- Continuar ciclo interminable. Próxima mejora P2/P3 visible (no detenerse).

---

## Ciclo 28 (cont.) — NaturalTaskParser: "fin de semana" → próximo sábado — 2026-08-11T21:4Z

### Objetivo
Cerrar el candidato P3 listado en CURRENT_STATE ("este fin de semana" no soportado en el parser).

### Cambio (1 commit)
- `5b6f714` — `NaturalTaskParser` ahora reconoce "este/el/próximo fin de semana" y "fin de semana"
  suelto → próximo sábado (hora canónica 09:00, consistente con días sueltos). Antes la frase
  quedaba sin fecha (INBOX) y "fin de semana" como residuo en el título. Hora explícita respetada
  ("el fin de semana a las 20:00" → sábado 20:00). TDD: +3 tests.

### Tests — VERIFICADO localmente
- `./gradlew :app:testPreviewSafeDebugUnitTest` (con Android SDK instalado en el agente):
  **462 tests, 0 failures, 0 errors, 0 skipped** en 48 clases.
- CI `5b6f714` success (Verificar). CI `118f020` (docs previo) cancelled por concurrencia (no fallo).

### Nota
- Android SDK (platform-tools, platforms;android-36, build-tools 35/36) se instaló en `/tmp/android-sdk`
  para poder correr los tests unitarios JVM localmente en esta sesión. `local.properties` (gitignored)
  apunta ahí. No persiste entre sesiones; la próxima ejecución que quiera correr gradle debe recrearlo.

### Siguiente
- Continuar ciclo interminable P2/P3. Candidatos: derivedStateOf/keys en LazyColumns grandes,
  BackHandler en pantallas anidadas, contraste onSurfaceVariant.

---

## Ciclo 29 — NaturalTaskParser: semanas/meses + ayer/anteayer + números escritos — 2026-08-13T13:0Z

### Objetivo
Candidato P1 del parser: "en un mes"/"en una semana" y fechas pasadas ayer/anteayer no se
parseaban → tareas olvidadas (sin recordatorio, invisibles en planificador).

### Sincronización
- `git fetch origin openhands/autonomous-ordia`; HEAD local == remoto (`e1014d5`). Sin divergencia.
- Entorno: JVM puro (sin Android SDK). `bash tools/run_domain_tests.sh` = 249 tests.

### Cambio (1 commit)
- `17f058d` — `fix(parser): parse 'en un mes/semana' + ayer/anteayer + written numbers 13-30`
  - `relativePattern` añade unidades `semanas`/`meses`; bug `meses?`→`mes(?:es)?` (singular).
  - `parseWrittenNumber` extendido: trece–veinte, veintiuno, treinta.
  - `relativeDueAt`: semanas ×7 días, meses ×30 días.
  - `date` when: añade ayer (-1) / anteayer (-2) como fechas pasadas explícitas.
  - Limpieza de título elimina tokens ayer/anteayer.
  - +11 tests.

### Tests — VERIFICADO localmente (JVM)
- `bash tools/run_domain_tests.sh` = **249 tests PASS** (238 base + 11 nuevos), 25 clases.
- Probe manual JVM: "en un mes"→2026-08-28, "en 1 mes"→2026-08-28, "dentro de un mes"→2026-08-28,
  "en una semana"→2026-08-05, "en 2 meses"→2026-09-27, "ayer a las 4 de la tarde"→ayer 16:00.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK en este entorno). CI remoto
  ejecutará `Verificar` (tests+lint+assemble) en el push.

### Hallazgos para próximas ejecuciones
- Parser: "en un año"/"el año que viene" no se parsean (relativePattern sin unidad años).
- Parser: "la semana que viene"/"el mes que viene" no se parsean. "próximos días" tampoco.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `17f058d` (código + tests)
- docs(autonomy) pendiente: actualización de AI_AUTONOMY (este commit).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: años, "semana/mes que viene", "próximos días".
- O P1 adjuntos URI externo si hay capacidad para sesión dedicada.

---

## Ciclo 30 — NaturalTaskParser: años + período próximo ("que viene"/"próximo X") — 2026-08-13T13:2Z

### Objetivo
Continuidad del ciclo 29: las formas más comunes de plazos largos y períodos siguientes
no se parseaban → tareas olvidadas. Probe JVM (ciclo 29 ya señaló): "en un año", "el año
que viene", "la semana que viene", "el mes que viene", "el próximo mes" → `dueAt=null`
y la frase quedaba como residuo en el título (sin recordatorio, invisible en
planificador/What Now).

### Sincronización
- `git fetch origin openhands/autonomous-ordia`; HEAD local == remoto (`acef2f7`). Sin divergencia.
- Entorno: JVM puro (sin Android SDK). `bash tools/run_domain_tests.sh` = 249 tests (ciclo 29).

### Cambio (1 commit)
- `feat(parser): parse 'en N años' + 'semana/mes/año que viene' + 'próximo X'`
  - `relativePattern` añade unidad `años`; `relativeDueAt` añade multiplicador 365 días.
  - Nuevo `nextPeriodPattern`: "el/la (semana|mes|año) que viene", "próximo/próxima
    (semana|mes|año)", "(semana|mes|año) próximo/próxima" → +1 período (semana=+7d,
    mes=+30d, año=+365d), con prioridad relativePattern > nextPeriod.
  - `effectiveRelativeDueAt` = relativeDueAt ?: nextPeriodDueAt; ambos son "días" (no
    min/hora) para combinarse con hora explícita ("el mes que viene a las 10").
  - Limpieza de título elimina la frase de período (no queda residuo "que viene").
  - +14 tests (años: 4; período próximo: 7; hora explícita: 3 incluidas en esos).

### Tests — VERIFICADO localmente (JVM)
- `bash tools/run_domain_tests.sh` = **259 tests PASS** (249 base + 10 nuevos efectivos
  de parser; +tests de título/hora), 25 clases. `tools/run_domain_checks.sh` = 25 assertions OK.
- Probe JVM (ahora): "en un año"→+365d, "en 2 años"→+730d, "dentro de un año"→+365d,
  "el año que viene"→+365d, "la semana que viene"→+7d, "el mes que viene"→+30d,
  "el próximo mes"→+30d, "la próxima semana"→+7d. Todos con título limpio.
- Probe anti-falsos-positivos: "la semana pasada", "el mes pasado", "el año pasado",
  "próximo a la puerta", "viene a visitarme" → todos `dueAt=null` (sin colisión).
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "próximos días"/"en los próximos días" (vago, sin semana/mes/año) sigue sin
  parsearse — forma intencionalmente ambigua; decidir si merece un default (¿+3d?).
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'en N años' + 'semana/mes/año que viene' + 'próximo X'` (código + tests)
- docs(autonomy) pendiente: actualización de AI_AUTONOMY (este commit).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "próximos días" (decidir default),
  "próximo trimestre"; P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 31 — NaturalTaskParser: fix "fin de semana que viene" (regresión de período próximo) — 2026-08-13T13:4Z

### Objetivo
El `nextPeriodPattern` añadido en el ciclo 30 ("semana/mes/año que viene") coincidía
con la subcadena "semana que viene" dentro de "fin de semana que viene". Como se procesa
antes que `weekendPattern`, consumía "semana que viene" y dejaba el residuo **«fin de»**
en el título, **además** de programar la tarea +7d (período "semana") en lugar del
próximo sábado. Frase cotidísima → tarea mal fechada + título corrupto (P1).

### Sincronización
- HEAD inicial: `e467b23` (ciclo 30). `git pull --ff-only` limpio, sin divergencia.
- Entorno: JVM puro (sin Android SDK).

### Cambio
- `NaturalTaskParser.parse`: detección temprana de `weekendPattern` y borrado de `working`
  **antes** del procesamiento de `nextPeriodPattern`. El match se conserva en
  `weekendEarlyMatch` y se reutiliza como `weekendMatch` en la resolución de fecha (mismo
  valor que antes; la fecha del fin de semana se calcula igual).
- Limpieza de título: añadido borrado de residuo huérfano `\bque\s+viene\b` (queda cuando
  la fecha asociada —fin de semana o día de la semana— se consume pero la frase
  modificadora no, p.ej. "el viernes que viene" tras otros reordenamientos).
- Test de regresión: `finDeSemanaQueVieneProgramaProximoSabadoYLimpiaTitulo`
  ("Viaje fin de semana que viene" → título "Viaje", due=próximo sábado 2026-08-01).

### Tests — VERIFICADO localmente (JVM)
- Antes del fix (probe): "fin de semana que viene" → title=[fin de], due=2026-08-05 (+7d, incorrecto).
- Después del fix: title=[Viaje] (limpio), due=2026-08-01 (próximo sábado, correcto).
- `bash tools/run_domain_tests.sh` = **260 tests PASS** (259 + 1 nuevo), 25 clases.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "antier"/"antier" (variantes de "anteayer") no reconocidas (due=null) — pendiente.
- Parser: "próximos días"/"en los próximos días" (vago) sigue sin parsearse.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `fix(parser): 'fin de semana que viene' ya no deja residuo ni fecha errónea` (código + test)
- docs(autonomy): registro ciclo 31

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "antier"; "próximos días" (decidir default);
  "próximo trimestre". P1 adjuntos URI externo si hay sesión dedicada.


## Ciclo 32 — NaturalTaskParser: "próximos días" (+3d) — 2026-08-13T13:3Z

### Objetivo
Resolver el hallazgo pendiente de los ciclos 30/31: "próximos días" (con o sin "en
los/el/las") no se parseaba → `dueAt=null` → tarea olvidada. Es la forma cotidiana y
deliberadamente vaga de "dentro de poco". Decisión de producto: default honesto +3 días
(ni IA ni azar; coincide con el sentido común de "pocos días").

### Sincronización
- HEAD inicial: `3540808` (ciclo 31, remoto).
- **STALE_RUN evitado**: existía un commit local no-pushado `04a21e8` que duplicaba los
  features del remoto (años + "semana/mes/año que viene" + "próximo X", ya en `e467b23`) y
  SOLO añadía "próximos días". Push habría sido non-ff. Se descartó el commit duplicado
  (`git reset --soft origin/openhands/autonomous-ordia` + `git checkout -- .`) y se
  reconstruyó SOLO "próximos días" sobre el HEAD remoto limpio `3540808`. Sin sobrescribir
  trabajo válido ni history compartido; sin force/reset destructivo.
- Entorno: JVM puro (sin Android SDK).

### Cambio (mínimo, reutiliza infraestructura existente)
- `NaturalTaskParser.nextPeriodPattern`: añadida alternativa al final del regex
  `(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b` (prefijo "en los/el/las" opcional).
- `nextPeriodDueAt`: añadida rama `else -> 3L` ("próximos días" no contiene semana/mes/año,
  cae al else → +3d).
- Docstring del patrón actualizado.
- Beneficios heredados (sin código nuevo): combina con hora explícita
  ("en los próximos días a las 10" → fecha +3d a las 10:00) y se elimina del título sin residuo,
  porque reutiliza el mismo `nextPeriodMatch`/`effectiveRelativeDueAt`/`relativeIsDays`.

### Tests — VERIFICADO localmente (JVM)
- Probe antes/después (now=2026-07-29): "próximos días" → antes due=null; después due=2026-08-01.
- "en los próximos días a las 10" → due=2026-08-01 time=10:00 (combina con hora). ✓
- Regresiones OK: "el mes que viene a las 8 de la mañana" → 2026-08-28 08:00 (no afectado);
  "el año que viene" → 2027-07-29; "la semana que viene" → 2026-08-05; "cada lunes" → OK.
- `bash tools/run_domain_tests.sh` = **263 tests PASS** (260 base + 3 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "antier"/"antier" (variantes de "anteayer") no reconocidas (due=null) — pendiente.
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'próximos días' (+3d)` (código + 3 tests)
- docs(autonomy): registro ciclo 32

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "antier"; "próximo trimestre".
  P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.) — NaturalTaskParser: "antier" (variante de "anteayer") — 2026-08-13T13:5Z

### Objetivo
Resolver el hallazgo pendiente de los ciclos 31/32: "antier" (variante coloquial
hispanoamericana de "anteayer", MX/CA/parts SA) no se parseaba → `dueAt=null` →
tarea vencida olvidada. Es la misma fecha que "anteayer" (hace dos días). Mejora
P1 atómica (evitar olvidos de fechas pasadas), continuación natural del soporte
de "anteayer"/"ayer" del ciclo 29.

### Sincronización
- HEAD inicial: `d134f2a` (ciclo 32, remoto, tras push de "próximos días").
- `git fetch origin openhands/autonomous-ordia`; remoto == HEAD local. Sin divergencia,
  sin colisión con otra ejecución.
- Entorno: JVM puro (sin Android SDK).

### Cambio (mínimo, TDD)
- `NaturalTaskParser.parse`: la rama `anteayer` del `when` de fecha ahora es
  `Regex("""(?i)\banteayer\b|\bantier\b""").containsMatchIn(working)` → misma fecha
  (`base.toLocalDate().minusDays(2)`). "antier" cae en la misma rama que "anteayer".
- Limpieza de título: añadido `\bantier\b` al regex que elimina tokens de día relativo
  (junto a `anteayer`/`ayer`/`hoy`/`mañana`/`pasado mañana`). No queda residuo en el título.
- Comentario del patrón actualizado ("antier" = variante coloquial hispanoamericana).
- Beneficios heredados (sin código nuevo): combina con hora explícita
  ("antier a las 4 de la tarde" → fecha -2d a las 16:00), igual que "ayer a las 4".

### Tests — VERIFICADO localmente (JVM)
- TDD red→green: test `antierParsesDueAtTwoDaysAgo` falló antes del fix
  (title=[Enviar correo antier], dueAt=null → NPE en `result.dueAt!!`); PASS tras fix.
- Probe (now=2026-07-29): "antier" → due=2026-07-27, título "Enviar correo" (limpio). ✓
- "antier a las 4 de la tarde" → due=2026-07-27 time=16:00 (combina con hora, no a HOY). ✓
- Regresiones OK: "anteayer" sigue → -2d; "ayer" → -1d; "próximos días" → +3d (no afectado).
- `bash tools/run_domain_tests.sh` = **265 tests PASS** (263 + 2 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: "próximo trimestre"/"el trimestre que viene" no soportado.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'antier' (variante de 'anteayer')` (código + 2 tests) — pendiente push
- docs(autonomy): registro ciclo 32 (cont.) — pendiente push

### Siguiente
- Continuar ciclo interminable. Candidato parser: "próximo trimestre".
  P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.2) — NaturalTaskParser: “próximo trimestre” / “trimestre que viene” (+90d) — 2026-08-13T13:31Z

### Objetivo
Resolver el hallazgo pendiente desde ciclos 30/31/32: “próximo trimestre” /
“el trimestre que viene” / “el próximo trimestre” no se parseaban → `dueAt=null` →
tarea olvidada (sin recordatorio ni visibilidad en planificador/What Now). Plazo
largo cotidiano (impuestos trimestrales, revisiones, informes).

### Estado del repo
- HEAD inicial: `b8b3761` (ciclo 32 cont., remoto, tras push de “antier”).
- `git fetch origin openhands/autonomous-ordia` → local == remoto (sin divergencia).
- Remote con token `https://x-access-token:${github_token}@...`.

### Cambios
- `NaturalTaskParser.nextPeriodPattern`: añadida `trimestre` como unidad en ambas
  ramas del patrón (“trimestre que viene” / “próximo trimestre”).
- `NaturalTaskParser.nextPeriodDueAt`: añadida rama `trimestre` → `90L` (3 meses ×
  30d, consistente con `mes que viene` = +30d). **Se comprueba ANTES que `mes`**
  porque la cadena “trimestre” contiene la subcadena “mes” (“tri**mes**tre”); si
  `mes` fuera primero ganaría erróneamente (+30d en vez de +90d).
- Comentario del patrón consolidado/limpiado (eliminado bloque duplicado huérfano).

### TDD
- 3 tests nuevos (`proximoTrimestreParsesDueAt`,
  `trimestreQueVieneParsesDueAt`, `proximoTrimestreRespetaHoraExplicita`).
- RED confirmado antes del fix: los 3 fallaron — dueAt=null → el título retenía el
  residuo (“Auditoría [próximo trimestre]”, “Cerrar informe [ el trimestre que viene]”).
- GREEN tras fix.

### Evidencia
- TDD red→green: 3 tests fallaron antes del fix (ComparisonFailure: título con
  residuo “próximo trimestre”/“el trimestre que viene”); PASS tras fix.
- now=2026-07-29T12:00 (America/Santo_Domingo). +90d = 2026-10-27. ✓
- “próximo trimestre a las 10” → fecha +90d time=10:00 (combina con hora). ✓
- Regresiones OK: “semana/mes/año que viene” y “próximos días” no afectados
  (cubiertos por suite existente).
- `bash tools/run_domain_tests.sh` = **268 tests PASS** (265 + 3 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: candidatos restantes — “próximo bimestre/semestre” (poco frecuente;
  evaluar si merece la pena), “a finales de mes”, “a mediados de mes”,
  “esta semana” (vs “la semana que viene”), “fin de mes”.
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'próximo trimestre' / 'trimestre que viene' (+90d)` (código + 3 tests)
  → `44f2e9b` (pushado a remoto, fast-forward `b8b3761..44f2e9b`).
- docs(autonomy): registro ciclo 32 (cont.2) — este commit.

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “a finales/mediados de mes”,
  “esta semana”, “fin de mes”. P1 adjuntos URI externo si hay sesión dedicada.

## Ciclo 32 (cont.3) — NaturalTaskParser: “fin de mes” / “a finales de mes” / “mediados de mes” — 2026-08-13T13:31Z

### Objetivo
Resolver hallazgo pendiente: el parser no entendía límites mensuales (“fin de mes”,
“a finales de mes”, “mediados de mes”) — vencimientos cotidianos (alquiler, tarjeta,
servicios). `dueAt=null` → vencimiento olvidado (sin recordatorio ni visibilidad en
planificador/What Now). La aritmética `now + millis` no calculaba “último día del mes” ni “día 15”.

### Estado del repo
- HEAD inicial: `b24758e` (ciclo 32 cont.2, remoto, tras push de “trimestre”).
- `git fetch origin openhands/autonomous-ordia` → local == remoto (sin divergencia, no STALE_RUN).
- Remote con token `https://x-access-token:${github_token}@...`.

### Cambios
- Nuevos patrones `endOfMonthPattern` (`fin(?:ales|es)? (de|del) mes`, cubre
  fin/fines/finales) y `midOfMonthPattern` (`mediados? (de|del) mes`).
- Lógica de resolución: `LocalDate.withDayOfMonth(lengthOfMonth())` (fin de mes) y
  `withDayOfMonth(15)` (mediados), rodando al mes siguiente si hoy ya es la fecha objetivo.
  Fecha absoluta vía `DateRules.toEpochMillis(target, LocalTime.of(9,0), zone)`.
- **Detección y borrado ANTES del período próximo**: “fin de mes” contiene la subcadena
  “mes” → colisionaría con “mes que viene” (residuo + fecha +30d errónea). Bloque early-detect
  junto a `weekendEarlyMatch`.
- Integrados en `effectiveRelativeDueAt` (prioridad: relativa explícita > límite de mes >
  período próximo) y `relativeIsDays=true` para combinar con hora explícita (“fin de mes a las 18”).

### TDD
- 7 tests nuevos: `finDeMesParsesDueAtUltimoDiaMesActual`, `aFinalesDeMesParsesDueAt`,
  `finDeMesRespetaHoraExplicita`, `finDeMesRuedaAProximoMesSiHoyEsUltimoDia`,
  `mediadosDeMesParsesDueAtDia15ProximoMes`, `mediadosDeMesResuelveDia15MesActualSiAunNoLlega`,
  `finDeMesNoColisionaConPeriodoProximo`.
- Red phase confirmado (7 fallaban). Green tras fix del regex (`fin(?:es)?` → `fin(?:ales|es)?`
  para cubrir “finales”): “a finales de mes”/“a finales del mes” antes no hacían match.

### Evidencia
- now=2026-07-29T12:00 (julio=31 días). “fin de mes” → 2026-07-31 09:00. “mediados de mes”
  (día 29 > 15) → 2026-08-15 09:00. “fin de mes a las 18” → 2026-07-31 18:00 (combina hora).
- “Renovar suscripción a finales del mes que viene” → title limpio + due=fin de mes (no +30d).
- Regresiones OK: “semana/mes/año que viene”, “próximos días”, “trimestre” no afectados (suite existente).
- `bash tools/run_domain_tests.sh` = **275 tests PASS** (268 + 7 nuevos), 25 clases.
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- NO VERIFICADO: gradle/lint/assemble/Android (sin Android SDK). CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- Parser: candidatos restantes — “esta semana” (vs “la semana que viene”),
  “próximo bimestre/semestre” (poco frecuente; evaluar), “principios de mes” (día 1).
- P1 OPEN: adjuntos guardan URI externo (BACKLOG) — requiere sesión dedicada.

### Commits
- `feat(parser): parse 'fin de mes' / 'mediados de mes' / 'a finales de mes'` (código + 7 tests).
- docs(autonomy): registro ciclo 32 (cont.3) — este commit.

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “esta semana”, “principios de mes”.
  P1 adjuntos URI externo si hay sesión dedicada.


---

## 2026-08-13 — Ciclo 32 (cont.4): adjuntos copiados a almacenamiento interno (P1 persistencia)

**HEAD inicial**: `bdd3dc0` (docs ciclo 32 cont.3 — fin de mes / mediados de mes)
**Branch**: `openhands/autonomous-ordia`

### Problema seleccionado (P1 — datos sagrados / persistencia)
Adjuntos (captura, note editor, task detail) guardaban el **URI externo** en `AttachmentEntity.uri`
sin copiar el contenido. El acceso dependía de `takePersistableUriPermission` (en `runCatching`
silencioso). Si el permiso falla/caduca/revoca (limpieza del sistema, app reinstalada, URI de
`MediaStore` que cambia tras reboot), el adjunto queda **inaccesible** tras reinicio y
`startActivity(ACTION_VIEW)` falla con toast misleading ("Ninguna app pudo abrir…"). Riesgo real
de pérdida percibida de datos. Ítem P1 del BACKLOG.

### Causa raíz
`OrdiaViewModel.attachCaptureIfPresent` (y los pickers de Note/Task) guardaban `attachmentUri`
directo en `AttachmentEntity.uri`. Sin copia de bytes, la persistencia del acceso quedaba delegada
a un permiso persistente frágil y silencioso ante fallos.

### Solución (mínima, robusta)
- **Nuevo `AttachmentStorage`** (`data/repository/AttachmentStorage.kt`): copia los bytes del URI
  fuente a `filesDir/attachments/<uuid><ext>` (ext deducida del displayName/mimeType) y devuelve
  un URI `FileProvider`. `delete(uri)` borra el archivo interno. `resolveForOpening(uri)` devuelve
  el URI tal cual (los FileProvider ya son válidos; legacy externo pasa sin tocar para no romper).
- **`OrdiaViewModel`**:
  - `addAttachment(ownerType, ownerId, sourceUri, displayName, mimeType, sizeBytes)`: copia con
    `attachmentStorage.import()`; si la copia falla, conserva el URI original (no pierde el adjunto).
  - `attachCaptureIfPresent` ahora importa el contenido (antes guardaba URI crudo).
  - `resolveAttachmentUri(uri)` para abrir adjuntos.
  - `deleteAttachment` borra también el archivo interno.
- **`NoteEditorScreen.kt` / `TaskDetailScreen.kt`**: usan nuevo `addAttachment`, `resolveAttachmentUri`
  al abrir; eliminado `takePersistableUriPermission` (innecesario: contenido en interno).
- **Manifest**: `FileProvider` authority `${applicationId}.attachments` + `ordia_attachment_paths.xml`
  (`files-path name="attachments" path="attachments/"`).
- **DI**: `AttachmentStorage(context)` en `AppContainer`, cableado en `OrdiaRoot` Factory.

### Archivos modificados/creados
- Creado: `app/src/main/java/com/ordia/app/data/repository/AttachmentStorage.kt`
- Creado: `app/src/main/res/xml/ordia_attachment_paths.xml`
- Modificado: `app/src/main/AndroidManifest.xml` (FileProvider)
- Modificado: `app/src/main/java/com/ordia/app/di/AppContainer.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/OrdiaRoot.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/screens/NoteEditorScreen.kt`
- Modificado: `app/src/main/java/com/ordia/app/ui/screens/TaskDetailScreen.kt`

### Tests
- `bash tools/run_domain_tests.sh` = **275 tests PASS** (25 clases).
- `bash tools/run_domain_checks.sh` = smoke 25 assertions OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). La copia
  de bytes, FileProvider y `ACTION_VIEW` requieren dispositivo. CI remoto ejecuta `Verificar`.

### Hallazgos para próximas ejecuciones
- **Migración de adjuntos legacy**: URIs externos antiguos ya guardados siguen funcionando vía
  `resolveAttachmentUri` (pasan tal cual). NEXT paso opcional: al abrir un adjunto legacy, intentar
  copiarlo a interno si todavía accesible (lazy migration). Riesgo: URIs ya inválidos. Evaluar.
- Parser: candidatos restantes — "esta semana", "principios de mes", "próximo bimestre/semestre".

### Commits
- `feat(persistence): copy attachments to internal storage (P1)` (código).
- docs(autonomy): registro ciclo 32 (cont.4) — este commit.

### Siguiente
- Continuar ciclo interminable. Migración lazy de adjuntos legacy si se evalúa segura;
  si no, parser "esta semana"/"principios de mes". P2/P3 rendimiento/UX.


---

## 2026-08-13 — Ciclo 33: parser “principios de mes”, “fines de semana” recurrentes, días pasados

- **HEAD inicial**: `bdd3dc0` (ciclo 32 cont.3 docs) — base local al iniciar el run.
- **Cambio de base**: al hacer `git fetch` el remoto había avanzado a `82ba021` (ciclo 32 cont.4:
  adjuntos a almacenamiento interno). Mi base local estaba **obsoleta** (STALE potencial). Stash +
  `pull --ff-only` a `82ba021` + reaplicación. Conflicto en `CURRENT_STATE.md`/`RUN_LOG.md`
  (ambos runs tocamos docs de autonomía); resuelto tomando base remota y regenerando mis ediciones
  sobre ella. Código (`NaturalTaskParser.kt`, test) y `BACKLOG.md` se reaplicaron **sin conflicto**.
- **Problema (P1 — evitar olvidos / menos fricción de captura)**: tres formas cotidianas en
  español caían a `dueAt=null` o fecha errónea:
  1. **“principios de mes”** (día 1: rentas, pagos, cierres): `startOfMonthPattern` existía como
     regex pero sin resolución → `dueAt=null` o +30d por “mes” → vencimiento olvidado.
  2. **“fines de semana” (plural)** (“cada fines de semana”/“los findes”): el patrón singular “fin
     de semana” no casaba “fines” (residuo) y no existía hábito sábado+domingo → recurrencia perdida.
  3. **“el jueves pasado” / “el último lunes” / “el martes anterior”**: `weekdayPattern` capturaba
     el día como **próximo** y “pasado” quedaba en el título → fecha **FUTURA** errónea + título sucio
     (tarea vencida mal fechada, no aparecía como atrasada en What Now). Orden inverso tampoco casaba.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  1. Rama `startOfMonthPattern` en `monthBoundaryDueAt` (`withDayOfMonth(1)`, rueda al mes
     siguiente si hoy>1); detectado ANTES del período próximo (evita colisión con “mes que viene”).
  2. `weekendRecurrencePattern` (plural) → `RecurrenceFrequency.WEEKLY`, `days=[6,7]` (CSV “6,7”);
     consume “cada”/“los” inicial; singular sigue siendo fecha única (próximo sábado).
  3. `previousWeekdayPattern` (forward “jueves pasado”) + `previousWeekdayReversedPattern` (inverso
     “último lunes”) + función `previousWeekday()` (última ocurrencia **pasada**, excluye hoy: si
     hoy es ese día, va al de la semana anterior); detectados ANTES de `weekdayPattern`.
- **Tests**: +11 (4 principios, 2 fines-de-semana, 5 días-pasados). **286 domain tests PASS**
  (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits (previstos)**:
  - feat(parser): “principios de mes”, “fines de semana” recurrentes, días pasados — este commit.
  - docs(autonomy): registro ciclo 33 — este commit.
- **HEAD final**: f09aee0 (feat 567193e + docs f09aee0; push a openhands/autonomous-ordia OK).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: “esta semana” (vs “la semana que viene”),
  “próximo bimestre/semestre”, “próxima quincena” (+15d), “principios de semana” (lunes).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

---

## Ciclo 34 - 2026-08-13 (UTC)

- **Run/ciclo**: 34
- **HEAD inicial**: cdc3135 (origin/openhands/autonomous-ordia sincronizado; sin divergencia)
- **Problema seleccionado**: `NaturalTaskParser` no parseaba **"esta semana"** (plazo blando cotidiano). `dueAt=null` -> tarea olvidada (sin recordatorio, invisible en planificador/What Now); con hora explicita se fechaba en HOY por error ("esta semana a las 18" -> hoy 18:00).
- **Prioridad**: P1 (evitar olvidos, menos friccion de captura, inteligencia del parser).
- **Causa raiz**: no existia patron para "esta semana"; la palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene"/"proxima semana" = +7d). Sin hora explicita no habia respaldo -> null. Con hora, el periodo proximo activaba el combo hoy+hora (fecha futura erronea).
- **Solucion (minima, en `NaturalTaskParser.kt`)**:
  - Nuevo `thisWeekPattern` (`esta semana` + opcional `que viene`).
  - Resuelve al **proximo domingo** (fin de semana ISO lun-dom) a las 9:00 por defecto; **hoy** si hoy es domingo (`TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)`).
  - Detectado y borrado **antes** del periodo proximo: asi "semana" no activa "semana que viene" (sigue siendo +7d) y "esta semana que viene" se limpia del titulo.
  - Integrado en `effectiveRelativeDueAt` como dias (junto a monthBoundary/nextPeriod) para combinarse con una hora explicita.
- **Tests**: +4 (`estaSemanaParsesDueAtProximoDomingo`, `estaSemanaRespetaHoraExplicita`, `estaSemanaSiHoyEsDomingoEsHoy`, `estaSemanaNoColisionaConSemanaQueViene`). **290 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits (previstos)**: feat(parser): "esta semana" como plazo blando (proximo domingo); docs(autonomy): registro ciclo 34.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "proximo bimestre/semestre" (evaluar frecuencia), "proxima quincena" (+15d), "principios de semana" (lunes).
- P1 adjuntos: migracion lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 34 (cont.) - 2026-08-13 (UTC) - "principios de semana" (lunes)

- **Run/ciclo**: 34 (cont.)
- **HEAD inicial**: 769ef38 (origin/openhands/autonomous-ordia sincronizado; sin divergencia)
- **Problema seleccionado**: `NaturalTaskParser` no parseaba **"principios de semana"** / "a principios de semana" (frase cotidiana: "lo termino a principios de semana"). Caía a `dueAt=null` (tarea olvidada: sin recordatorio, invisible en planificador/What Now) o, con hora explícita, se fechaba en HOY por error.
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia del parser).
- **Causa raíz**: no existía patrón para "principios de semana"; la palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene"/"próxima semana" = +7d) o `thisWeekPattern` ("esta semana"). Sin hora explícita no había respaldo -> null. Con hora, el período próximo activaba el combo hoy+hora (fecha futura errónea).
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Nuevo `startOfWeekPattern` (`principios`/`principio` + `de`/`del` + `semana`, con opcional `a `).
  - Resuelve al **lunes más cercano en HOY o futuro** (ISO, semana lunes→domingo): si hoy es lunes, hoy; si es martes-domingo, el lunes de la semana siguiente. Como plazo blando nunca se fecha en pasado. Hora por defecto 9:00.
  - Detectado y borrado **antes** del período próximo: así "semana" no activa "semana que viene" (sigue siendo +7d).
  - Integrado en `effectiveRelativeDueAt` como días (junto a monthBoundary/thisWeek/nextPeriod) para combinarse con una hora explícita. También en `relativeIsDays`.
- **Tests**: +4 (`principiosDeSemanaParsesDueAtProximoLunes`, `principiosDeSemanaNoColisionaConSemanaQueViene`, `principiosDeSemanaRespetaHoraExplicita`, `principiosDeSemanaSiHoyEsLunesEsHoy`). **294 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "principios de semana" como plazo blando (lunes) — 01e28fc. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo interminable. Candidatos parser: "próximo bimestre/semestre" (evaluar frecuencia), "próxima quincena" (+15d), "mediados de semana" (miércoles), "a finales de semana" (viernes/dom).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.

## Ciclo 35 - 2026-08-13 (UTC)

- **Run/ciclo**: 35
- **HEAD inicial**: 8146acf (origin/openhands/autonomous-ordia; tras detectar colisión con base local obsoleta 46efb3e y descartar trabajo no commiteado).
- **Problema seleccionado**: `NaturalTaskParser` no reconocía la construcción coloquial multi-palabra **"un par de"** (= 2): "en un par de días/semanas/meses". `relativePattern` sólo aceptaba números o palabras-sueltas, así que caía a `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser).
- **Causa raíz**: la regex de cantidad del `relativePattern` enumeraba palabras-sueltas pero no la frase "un par de" (3 tokens). Al no coincidir, el match relativo fallaba y `dueAt` quedaba null.
- **Colisión con otro run**: al iniciar, mi base local (46efb3e) estaba obsoleta; otro run había commiteado quincena/bimestre/semestre (8146acf). Yo tenía cambios locales no commiteados que incluían mi propia versión de "quincena" (redundante) + "un par de" + docs. Decisión: descartar TODO el trabajo no commiteado (`git stash`), fast-forward limpio a 8146acf, y reaplicar SOLO "un par de" (que el remoto no tenía). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `relativePattern`: añadido `un\s+par\s+de` como primera alternativa del grupo de cantidad.
  - `parseWrittenNumber`: añadido `"un par de" -> 2L`.
  - Funciona con cualquier unidad relativa y con hora explícita ("en un par de días a las 10").
- **Tests**: +4 (`unParDeDiasResuelveMasDosDias`, `unParDeSemanasResuelveMasCatorceDias`, `unParDeMesesResuelveMasSesentaDias`, `unParDeDiasConHoraExplicita`). **308 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "un par de" (= 2) en relativePattern/parseWrittenNumber. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "mediados de semana" (miércoles), "a finales de semana" (viernes/dom), "un par de horas" ya cubierto.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar otras áreas (captura, What Now, rutinas) en busca de oportunidades de producto, no solo parser.

## Ciclo 36 - 2026-08-13 (UTC)

- **Run/ciclo**: 36
- **HEAD inicial**: e4157c1 (origin/openhands/autonomous-ordia sincronizado tras push del ciclo 35 — resuelto bloqueo de auth usando `github_token` en vez de `GITHUB_TOKEN` que estaba ausente).
- **Problema seleccionado**: `NaturalTaskParser` no reconocía **"mediados de semana"** / "a mediados de semana" (frase cotidiana: "lo termino a mediados de semana"). Caía a `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser).
- **Causa raíz**: existían `startOfWeekPattern` ("principios de semana" = lunes) y `midOfMonthPattern` ("mediados de mes" = día 15), pero ninguna familia cubría el caso "mediados de semana". La palabra "semana" solo la capturaba `nextPeriodPattern` ("semana que viene" = +7d) o `thisWeekPattern` ("esta semana"). Sin hora explícita no había respaldo → null.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Nuevo `midOfWeekPattern` (`mediados`/`mediado` + `de`/`del` + `semana`, con opcional `a `).
  - Resuelve al **miércoles más cercano en HOY o futuro** (`nextOrSame(WEDNESDAY)`): si hoy es miércoles, hoy; si no, el miércoles de esta semana o el de la siguiente. Como plazo blando nunca se fecha en pasado. Hora por defecto 9:00.
  - Detectado y borrado **antes** del período próximo: así "semana" no activa "semana que viene" (sigue siendo +7d).
  - Integrado en `effectiveRelativeDueAt` como días (junto a startOfWeek/monthBoundary/thisWeek/nextPeriod) para combinarse con una hora explícita. También en `relativeIsDays`.
  - No colisiona con "mediados de mes" (uno termina en "semana", otro en "mes").
- **Tests**: +4 (`mediadosDeSemanaSiHoyEsMiercolesEsHoy`, `mediadosDeSemanaDesdeLunesEsMiercolesMismaSemana`, `mediadosDeSemanaRespetaHoraExplicita`, `mediadosDeSemanaNoColisionaConMediadosDeMes`). **312 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "mediados de semana" (= miércoles). docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "a finales de semana" (evaluar ambigüedad viernes vs sábado vs domingo antes de implementar — "fin de semana" ya cubre sábado), "próxima quincena" (+15d).
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar captura, What Now, rutinas en busca de oportunidades de producto reales, no solo parser.
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 37 - 2026-08-13 (UTC)

- **Run/ciclo**: 37
- **HEAD inicial**: 9ac1a8b → detectada base obsoleta: otro run commiteó `7fa4056` ("a las N horas" como hora, no duración falsa). Stash de mi trabajo no commiteado, fast-forward limpio a `7fa4056`, reaplicación (`git stash pop`) sin conflictos (auto-merge en parser y test). Sin force push, sin reset --hard, sin sobrescribir trabajo válido del otro run.
- **Problema seleccionado**: `NaturalTaskParser` no reconocía **"a finales de semana"** / "finales de semana" (forma plural cotidiana análoga a "finales de mes": "lo termino a finales de semana"). `weekendPattern` solo casaba `fin de semana` (singular); "finales" no casaba y la frase quedaba como residuo en el título con `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What Now).
- **Prioridad**: P1 (evitar olvidos, menos fricción de captura, inteligencia honesta del parser; brecha simétrica frente a `endOfMonthPattern` que sí acepta "finales de mes").
- **Causa raíz**: `weekendPattern = \b(?:este\s+|el\s+|próximo\s+)?fin\s+de\s+semana\b` exigía `fin` exacto (singular). La palabra "finales" no casaba y, al no coincidir ningún patrón, `dueAt` quedaba null. Distinción crítica: "fines de semana" (f-i-n-e-s) ya es recurrencia WEEKLY sáb+dom en `parseRecurrence`; "finales de semana" es fecha única y no debía confundirse con ella.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Ampliación de `weekendPattern`: `(?:a\s+)?(?:este\s+|el\s+|próximo\s+)?(?:fin|finales)\s+de\s+semana\b`. Acepta `finales` como variante y el prefijo opcional `a ` (igual que `endOfMonthPattern`/`midOfMonthPattern`).
  - Reutiliza TODO el flujo existente: detección temprana (`weekendEarlyMatch`), borrado antes del período próximo, resolución a **próximo sábado** (igual que "fin de semana"), hora canónica 9:00, combinable con hora explícita.
  - **No** acepta `fines` (f-i-n-e-s): como `fin` va seguido de `\s+de` y en "fines" va "es" (sin espacio), no casa → la frase llega intacta a `parseRecurrence` que sigue generando WEEKLY sáb+dom. Decisión de ambigüedad viernes/sáb/dom: resolver a sábado por **consistencia** con "fin de semana" ya existente (no introducir un tercer comportamiento).
- **Tests**: +4 (`aFinalesDeSemanaProgramaProximoSabadoYLimpiaTitulo`, `finalesDeSemanaSueltoProgramaProximoSabadoYLimpiaTitulo`, `finalesDeSemanaRespetaHoraExplicita`, `finalesDeSemanaNoColisionaConRecurrenciaFinesDeSemana`). Verificación TDD: los 3 primeros fallaron antes del fix (residuo en título + dueAt null), pasaron tras ampliar el patrón. **319 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Coexisten con el fix del otro run ("a las N horas").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: feat(parser): "a finales de semana" = próximo sábado. docs(autonomy): este registro.
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: "próxima quincena" (+15d), "próximo bimestre/semestre" (evaluar frecuencia). Buscar oportunidades de producto en otras áreas (captura, What Now, rutinas, recordatorios), no solo parser.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Descubrimiento continuo: auditar recuperación de tareas olvidadas, detección de vencidas importantes, replanificación automática.
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 39 - 2026-08-13 (UTC) - fix: "de la mañana"/"por la mañana" NO es fecha "mañana"

- **Run/ciclo**: 39
- **HEAD inicial**: e4157c1 (ciclo 35). Base obsoleta tras dos rebases sucesivos: otro run commiteó ciclos 36/37 + 3 commits ("a las N horas", fechas imposibles, "hace N"/"la semana/el mes pasado", "a finales de semana") y luego el ciclo 38 ("fechas pasadas" + recuperación fechas imposibles). Rebase no destructivo sobre `8451597` (HEAD remoto). Auto-merge limpio en `NaturalTaskParser.kt` + test; conflictos solo en docs (CURRENT_STATE/RUN_LOG), resueltos conservando el trabajo del otro run y renumerando el mío a ciclo 39. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Problema seleccionado**: `NaturalTaskParser` fechaba en MAÑANA tareas que usaban "de la mañana"/"por la mañana" como marcador de **hora** (parte del día), porque la palabra "mañana" colisionaba con el token de **fecha** "mañana". Ejemplos reales afectados:
  - "Reunión a las 9 de la mañana" → se programaba para MAÑANA 09:00 (reunión perdida HOY).
  - "Llamar a mamá por la mañana" → MAÑANA 09:00 en vez de HOY 09:00.
  - "Desayuno a las 8 de la mañana" → MAÑANA 08:00.
  La ambigüedad léxica "mañana" (fecha) vs "mañana" (parte del día) no se resolvía: el branch de fecha hacía match con la mera presencia de la palabra.
- **Prioridad**: P1 (tarea en día erróneo → reunión/recordatorio perdido el mismo día; corrige captura, evita olvidos).
- **Causa raíz**: en la rama de fecha, `Regex("""(?i)\bmañana\b""").containsMatchIn(working)` matcheaba cualquier aparición de "mañana", incluida la que forma parte de "de la mañana"/"por la mañana"/"a la mañana"/"esta mañana". Estos marcadores de hora se procesaban luego (hora canónica 09:00), pero la fecha ya había saltado a +1d.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Reemplazada la rama `\bmañana\b` por `mananaAsDate(working)`: recorre todas las apariciones de "mañana" y devuelve `true` (fecha = mañana) sólo si **al menos una** NO está precedida por un marcador de parte del día (`de la ` / `por la ` / `a la ` / `esta `).
  - Así "Reunión a las 9 de la mañana" (única aparición, precedida por "de la ") → NO es fecha → se queda en HOY. "Hacer X mañana por la mañana" (primera aparición suelta) → Sí fecha → mañana. "mañana a las 9" → mañana.
  - `pasado mañana` se sigue resolviendo ANTES (rama previa, sin cambios), así que no hay regresión.
- **Tests**: +3 regresión (`deLaMananaWithoutDateStaysToday`, `porLaMananaWithoutDateStaysToday`, `mananaPorLaMananaStillResolvesTomorrow`). **336 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (319 base remota + 3 nuevos). Smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `cfa29cd` fix(parser): "de/por/a la mañana" (hora) no colisiona con fecha "mañana". docs(autonomy): este registro (mismo commit).
- **HEAD final**: `cfa29cd` (push a `openhands/autonomous-ordia`, fast-forward sobre `8451597`).

### Siguiente
- Continuar descubrimiento continuo (no solo parser): auditar What Now, rutinas, captura, recordatorios, detección de vencidas.
- Parser candidatos: "próxima quincena" (+15d), manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Nota operativa: `GITHUB_TOKEN` ausente en este entorno; usar `github_token` para push.

## Ciclo 40 - 2026-08-13 (UTC) - feat: recordatorios con números escritos y fracciones

- **Run/ciclo**: 40 (renumerado desde 38: base f2d26ba obsoleta; merge no destructivo del remoto que avanzó con ciclos 37/38/39; auto-merge limpio en parser+test, conflictos solo en docs resueltos conservando el trabajo del otro run. Sin force push, sin reset --hard).
- **HEAD inicial**: f2d26ba (origin/openhands/autonomous-ordia sincronizado; ciclo 37 ya en remoto).
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía **números escritos** en recordatorios relativos ("recuérdame una/dos horas antes", "una hora antes", "treinta minutos antes") ni **fracciones** ("media hora antes", "un cuarto de hora antes"): `reminderOffsetMinutes=null` y la frase quedaba como residuo en el título. Además "media hora antes" era **robado por el patrón de duración fraccionaria** (30 min falsos como duración) y el recordatorio quedaba en null. La cita se olvidaba (sin recordatorio programado).
- **Prioridad**: P2 (evitar olvidos por recordatorio perdido; asimetría con la duración relativa "en dos horas" que sí funcionaba).
- **Causa raíz**: `reminderPatterns` solo capturaba `(\d{1,3})` (dígitos); `parseWrittenNumber` existía pero no se usaba en recordatorios. Las fracciones ("media hora") no tenían patrón propio de recordatorio, así que caían al de duración. Además el código accedía a `groupValues[2]` asumiendo 2 grupos, lo que rompía con patrones de 1 grupo (`IndexOutOfBoundsException`).
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - `writtenAmountPattern` (dígitos o números escritos en español, simétrico a la fecha relativa) en los 2 patrones de recordatorio existentes.
  - 2 patrones nuevos de fracción con **contexto obligatorio** ("antes"/"de anticipación"/verbo) para no robar una duración real.
  - Offset vía `parseWrittenNumber`; `media hora`=30 / `cuarto de hora`=15; acceso `getOrNull(2)` seguro.
- **Tests**: +8 (`parsesWrittenAmountReminderWithVerb`, `parsesWrittenAmountReminderTwoHours`, `parsesWrittenAmountReminderThirtyMinutes`, `parsesWrittenAmountReminderWithoutVerb`, `mediaHoraAntesEsRecordatorio`, `cuartoDeHoraAntesEsRecordatorio`, `recuerdameMediaHoraDeAnticipacion`, `mediaHoraSinAntesSigueSiendoDuracion` — regresión). **344 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (336 base remota + 8 nuevos). Smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `f8023fd` feat(parser): recordatorios con números escritos y fracciones; `24d31f8` docs(autonomy): registro (renumerado a 40 tras merge).
- **HEAD final**: (tras commit/push a openhands/autonomous-ordia).

### Siguiente
- Continuar ciclo de parser: rango horario sin "horas" ("clase de 9 a 11"), "la quincena" como plazo/recurrencia.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Descubrimiento continuo: auditar captura, What Now, rutinas en busca de oportunidades de producto reales, no solo parser.
- Nota operativa: usar `github_token` (en este entorno `GITHUB_TOKEN` puede estar ausente).

## Ciclo 41 - 2026-08-13 (UTC) - "los lunes miércoles y viernes" (sin coma) + plurales sábados/domingos

- **Run/ciclo**: 41 (renumerado desde 40: base `4f803a9` obsoleta; otro run commiteó ciclo 40 "recordatorios con números escritos y fracciones" + merge `91c8b9f`. Rebase no destructivo sobre `91c8b9f`; auto-merge limpio en parser+test (cambios ortogonales); conflictos solo en docs (RUN_LOG), resueltos conservando su ciclo 40 y renumerando el mío a 41. Tras push, nuevo run remoto `60007d1` ("test parser listas de días separadas por espacio", misma feature, tests complementarios): segundo rebase no destructivo sobre `60007d1`; conflicto de tests resuelto conservando los 6 tests (3 suyos + 3 míos). Sin force push, sin reset --hard).
- **HEAD inicial**: `91c8b9f` (origin/openhands/autonomous-ordia tras ciclo 40 del otro run). Base obsoleta en varios puntos a lo largo de la sesión: otros runs commitearon ciclos 36–40 ("a las N horas", fechas pasadas, recuperación de fechas imposibles, "a finales de semana", "de/por/a la mañana" vs fecha "mañana", recordatorios con números escritos y fracciones). Rebases sucesivos no destructivos; cada vez renumeré mi ciclo para no colisionar.
- **Problema seleccionado**: **Pérdida de datos silenciosa en rutinas semanales** con listas de días sin coma (forma informal natural del español). "regar plantas los lunes miércoles y viernes" → el parser solo capturaba "lunes" y perdía "miércoles" y "viernes" → la rutina se repetía UN solo día en vez de tres. El usuario creaba una rutina creyendo que cubría varios días y nunca se le recordaba en los demás. Adicionalmente, los plurales "sábados"/"domingos" no casaban (patrón usaba forma singular con `\b`) y se perdían también.
- **Prioridad**: P1 (pérdida de datos: rutinas con menos días de los que el usuario escribió; recordatorios no disparan en los días perdidos → olvidos).
- **Causa raíz**: `dayListPattern` exigía separador "," o "y" entre cada par de días. La forma informal "los lunes miércoles y viernes" (sin coma entre los dos primeros) rompía el patrón tras el primer día, capturaba solo "lunes" y dejaba "miércoles y viernes" como residuo en el título. Independientemente, "sábados"/"domingos" (plural) no casaban porque el patrón usaba `s[aá]bado|domingo` (singular) con límite `\b`.
- **Solución (mínima, en `NaturalTaskParser.kt`)**:
  - Hacer el separador entre días **opcional** en `dayListPattern`: `(?:\s*(?:,|y)?\s*(?:...))`. Como los nombres de día son palabras cerradas y específicas, admitir separador vacío solo casa cuando la palabra siguiente es otro día, sin riesgo de robar texto ajeno ("los lunes con el equipo" para en "lunes" porque "con" no es un día).
  - Aceptar **plural** de sábado/domingo: `s[aá]bados?|domingos?`.
- **Tests**: +3 de regresión (`parsesDayListWithoutCommaSeparator` → 1,3,5; `parsesDayListWithPluralSabadoDomingo` → 2,4,6; `dayListStopsAtNonDayWord` → no roba "con el comité"). Tras segundo rebase sobre `60007d1`, mis 3 tests coexisten con los 3 del otro run (misma feature, casos distintos): **350 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (347 base remota + 3 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `fix(parser): listas de días sin coma + plurales sábados/domingos (ciclo 41)` (`8ceb8d2`), rebaseado no destructivamente primero sobre `91c8b9f` y luego sobre `60007d1` (segundo run remoto sobre la misma feature, tests complementarios). Auto-merge limpio en `NaturalTaskParser.kt`; conflicto de tests resuelto conservando los 6 tests. Conflictos solo en docs (RUN_LOG), resueltos conservando el otro ciclo 40 y renumerando el mío a 41.
- **HEAD final**: `8ceb8d2` (push OK a `origin/openhands/autonomous-ordia`: `60007d1..8ceb8d2`).

### Siguiente
- Continuar ciclo de parser: "próxima quincena" (+15d), manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler; contraste onSurfaceVariant.


## Ciclo 42 - 2026-08-13 (UTC) - feat: rango horario sin "horas" (ambas < 13)

- **Run/ciclo**: 42 (base `8ceb8d2` = ciclo 41 remoto, ya sincronizada al inicio de la sesión; commit `0a77387` fue creado en un run anterior de esta misma sesión y ya está en el HEAD remoto).
- **HEAD inicial**: `8ceb8d2` → tras trabajo de parser, `0a77387` (feat(parser): rango horario sin unidad "horas" cuando ambas horas < 13). Push OK a `origin/openhands/autonomous-ordia`. Este run (continuación) se dedica a (a) verificar la línea base de tests y (b) actualizar la memoria permanente (CURRENT_STATE, BACKLOG, este RUN_LOG) que quedó pendiente del commit de código.
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía rango horario **sin la palabra "horas"** en formato 12h ("clase de 9 a 11", "taller de 10 a 12"): `dueAt=null`, `durationMinutes=null`, rango crudo como residuo en el título. El `timeRangePattern` casaba, pero el guard lo rechazaba (exigía unidad "horas"/"hs"/"h" o alguna hora ≥ 13) para evitar falsos positivos como "comprar de 2 a 5 entradas" (cantidad, no horario).
- **Prioridad**: P2 (forma cotidiana de expresar un bloque horario; evita captura incompleta / recordatorio sin duración).
- **Causa raíz**: el guard del `rangeMatch` trataba todo rango sin unidad y ambas horas < 13 como ambiguo y lo descartaba por completo, sin distinguir si el rango abre/cierra una ventana horaria ("clase de 9 a 11") o expresa una cantidad ("de 2 a 5 entradas").
- **Solución (mínima, en `NaturalTaskParser.kt`)**: heurística honesta (no IA, no random). Un rango sin unidad y ambas horas < 13 se acepta como ventana horaria **solo si NO va seguido de un sustantivo de cantidad**: si tras el rango hay fin de cadena o un conector/preposición/puntuación ("con Juan", "y luego", ", después") se entiende como horario; si hay un sustantivo después ("entradas", "personas") se respeta como cantidad y no se consume. Restricción `end - start in 1..11` evita rangos absurdos. Así "clase de 9 a 11" → dur 120, título "Clase"; "comprar de 2 a 5 entradas" → sin duración, título intacto.
- **Tests**: +3 (`rangeWithoutUnitBothUnder13ParsesAsTimeRange`, `rangeWithoutUnitKeepsTrailingText` para preservación, y guard de conteo de ítems como regresión). **353 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (350 base c.41 + 3 nuevos). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `0a77387` feat(parser): rango horario sin unidad "horas" cuando ambas horas < 13 (push OK). Este run añade `docs(autonomy): memoria ciclo 42 (BACKLOG/CURRENT_STATE/RUN_LOG)`.
- **HEAD final**: (tras commit de docs a `openhands/autonomous-ordia`).

### Siguiente
- Continuar ciclo de parser: "la quincena" como plazo/recurrencia (día 15 y fin de mes), manejo robusto de múltiples marcadores temporales en una frase.
- Descubrimiento continuo (más allá del parser): auditar What Now, captura, rutinas, recordatorios, detección de vencidas, búsqueda universal en busca de oportunidades de producto reales.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).
- Nota operativa: usar `github_token` para push si `GITHUB_TOKEN` no está disponible.

## Ciclo 43 - 2026-08-13 (UTC) - feat: "entre semana"/"días laborables"/"de lunes a viernes" = WEEKLY Lun–Vie

- **Run/ciclo**: 43 (renumerado: la otra ejecución concurrente reclamó los números 41 y 42 con “listas de días sin coma + plurales sábados/domingos” y “rango horario sin horas”). Procedimiento no destructivo: `git rebase` de mi commit sobre el HEAD remoto actualizado (`727e7b8`, que incluye los ciclos 38–42 de la otra ejecución); auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales: el remoto tocó listas de días/plurales y rango horario, yo la recurrencia laboral Lun–Vie). Conflictos solo en docs (`CURRENT_STATE.md`, `RUN_LOG.md`), resueltos conservando el trabajo del otro run y renumerando el mío a 43. Sin force push, sin reset --hard.
- **HEAD inicial**: `727e7b8` (origin/openhands/autonomous-ordia sincronizado tras fetch; el remoto avanzó 2 commits: docs c.42 + ampliación followers).
- **Problema seleccionado**: `NaturalTaskParser` NO generaba recurrencia para frases cotidianas de hábitos laborables: "Gimnasio entre semana", "Trabajo de lunes a viernes", "Estudiar días laborables", "Reunión los días hábiles". El parser las trataba como texto suelto → tarea única (freq=NONE) que aparece una sola vez y se olvida el resto de la semana. Peor, "de lunes a viernes" dejaba "lunes" como residuo en el título: `dayListPattern` capturaba solo "lunes" (days=[1]) y el viernes se perdía. Brecha simétrica frente a `weekendRecurrencePattern` ("fines de semana" → WEEKLY sáb+dom, c.33): el hábito Lun–Vie no tenía equivalente.
- **Prioridad**: P1 (evitar olvidos + fricción de captura; hábitos cotidianos que se perdían tras la primera ocurrencia).
- **Causa raíz**: no existía patrón para el rango "lunes a viernes" ni para el conjunto léxico "entre semana/días laborables/hábiles/de semana". El `dayListPattern` existente capturaba listas de días ("lunes, miércoles, viernes") pero no el rango "X a Y", así que "lunes a viernes" se rompía en "lunes" + residuo "a viernes".
  - `weekdayRangePattern`: nuevo patrón `(los |de )?(lunes|martes|miércoles|jueves|viernes)\s+a\s+(martes|…|domingo)` (admite prefijo `los `/`de `). Si el rango incluye viernes → `RecurrenceFrequency.WEEKLY`, `days=[1,2,3,4,5]` (hábito laboral). Resuelve a la próxima ocurrencia del primer día del rango, combinable con hora explícita.
  - `weekdaySetPattern`: variantes léxicas equivalentes → mismo WEEKLY [1-5]: `entre semana`, `días laborables`, `días hábiles`, `días de semana`, `de semana` (prefijo opcional `los `/`de `). Consumen la frase completa (título limpio).
  - **Orden de patrones crítico**: ambos se evalúan **ANTES** que `dayListPattern` para que "los lunes a viernes" sea rango (days=[1..5]) y no la lista ["lunes"] (days=[1]). El singular "fin de semana" sigue siendo fecha única (próximo sábado), sin colisión.
- **Tests**: +7 (`entreSemanaComoRecurrenciaWeekdayLunAVie`, `diasLaborablesComoRecurrenciaWeekday`, `diasHabilesConDeterminanteComoRecurrenciaWeekday`, `deLunesAViernesComoRecurrenciaWeekdayLimpiaTitulo`, `losLunesAViernesComoRangoNoLista`, `entreSemanaRespetaHoraExplicita`, `finDeSemanaSingularNoEsRecurrenciaWeekday` — regresión). **372 domain tests PASS** (`bash tools/run_domain_tests.sh`), 25 clases (365 base remota + 7 nuevos de este ciclo). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(parser): "entre semana"/"de lunes a viernes" = recurrencia Lun–Vie` (rebaseado no destructivamente sobre `727e7b8`; auto-merge limpio en `NaturalTaskParser.kt`+tests; conflictos solo en docs `CURRENT_STATE.md`/`RUN_LOG.md`, resueltos conservando los ciclos 41/42 del otro run y renumerando el mío a 43). Push OK a `origin/openhands/autonomous-ordia`.
- **HEAD final**: `a934b65` (commit feat(parser) rebaseado sobre `727e7b8`; push OK a `origin/openhands/autonomous-ordia`).

### Siguiente
- Descubrimiento continuo más allá del parser: auditar What Now (detección de vencidas importantes), rutinas (re-disparo), captura (fricción), recordatorios.
- Parser candidatos: rango horario sin "horas" ("clase de 9 a 11"), "la quincena" como plazo/recurrencia, múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero; URIs ya inválidos).
- Nota operativa: usar `github_token` para push.

## Ciclo 44 - 2026-08-13 (UTC) - feat: "la quincena" como hito financiero (día 15 / fin de mes)

- **Run/ciclo**: 44 (renumerado desde 43: al hacer `git fetch` el remoto había avanzado a `a934b65` por el run paralelo que reclamó ciclo 43 con "entre semana"/"de lunes a viernes" = WEEKLY Lun–Vie. Antes de eso el remoto ya había avanzado a `30b7df8` con la recurrencia "cada quincena"/"quincenalmente" (WEEKLY x2, `97ec260`) + 3 commits de docs. Procedimiento no destructivo: stash → `merge --ff-only origin/openhands/autonomous-ordia` → stash pop (resolvió BACKLOG.md), commit, y al detectar nuevo avance remoto `git rebase origin/openhands/autonomous-ordia` (auto-merge limpio en `NaturalTaskParser.kt`+tests; conflicto solo en `RUN_LOG.md`, resuelto conservando el ciclo 43 del otro run y renumerando el mío a 44). Sin force push, sin reset --hard, sin pérdida de trabajo).
- **HEAD inicial**: `a934b65` (origin/openhands/autonomous-ordia tras rebase; `727e7b8` al inicio del run).
- **Problema seleccionado**: `NaturalTaskParser` NO reconocía "la **quincena**" como plazo temporal ("cobro de la quincena", "pago de la quincena a las 18", "primera/segunda quincena"): `dueAt=null` → el vencimiento quedaba **olvidado** (sin recordatorio ni visibilidad en What Now/planificador). Peor, "pago de la quincena a las 18" se fechaba en HOY 18:00 (día erróneo) por el parsing de hora aislada. La quincena (día 15 y fin de mes) es un hito financiero/laboreal muy común en español, simétrico a `finDeMes`/`mediadosDeMes` ya implementados. (Complementario al run paralelo `97ec260` que resolvió la **recurrencia** "cada quincena"; este run resuelve la **fecha/hito** "la quincena".)
- **Prioridad**: P2 (evita captura incompleta / recordatorio sin fecha / vencimiento olvidado en un hito muy común).
- **Causa raíz**: no existía patrón de quincena; las frases caían a `dueAt=null` o eran malinterpretadas por patrones más generales (hora aislada → hoy).
- **Solución (mínima, en `NaturalTaskParser.kt`)**: nuevo `quincenaPattern` que casa `(primera|segunda|1ra|2da)? quincena` con artículo prefijo opcional (`de la`/`de`/`la`). Resolución **honestamente determinística** (no IA, no random):
  - "primera"/"1ra quincena" → día 15; rueda al 15 del mes próximo si hoy ≥ 15.
  - "segunda"/"2da quincena" → fin de mes; rueda a fin del mes próximo si hoy es último día.
  - "la quincena"/"de la quincena" sin cualificar → **próximo hito**: día 15 si hoy<15; fin de mes si 15≤hoy<último; 15 del mes próximo si hoy=último día (consistente con `finDeMes`).
  - Combina con hora explícita ("a las 18" → 15/8 18:00, no hoy 18:00).
  - Procesado **DESPUÉS** del stripping de `nextPeriodMatch`, así "próxima quincena"/"quincena que viene" siguen como +15d y "en N quincenas" como relativo — sin regresión.
  - **Guarda anti-colisión con recurrencia**: tras integrar el run paralelo `97ec260`, el patrón robaba "quincena" de "cada quincena"/"quincenalmente"/"todas las quincenas" (rompía 2 tests de recurrencia). Se añadió `quincenaRecurrencePattern` + guarda en el match: si la frase contiene una forma de recurrencia, el patrón de hito NO consume la palabra, dejándola para `parseRecurrence` (WEEKLY x2). 384 tests PASS.
- **Tests**: +12 (`laQuincenaSinCualificarResuelveProximoHitoDia15SiAntes`, `…FinDeMesSiPosteriorAl15`, `…RuedaAl15ProximoMesSiHoyEsUltimoDia`, `…RespetaHoraExplicita`, `primeraQuincenaResuelveDia15`, `primeraQuincenaRuedaAProximoMesSiHoyPasadoEl15`, `segundaQuincenaResuelveFinDeMes`, `segundaQuincenaRuedaAProximoMesSiHoyEsUltimoDia`, `primeraQuincenaAbreviada1ra`, `segundaQuincenaAbreviada2da`, `proximaQuincenaSigueResolviendoseComoPeriodoProximo`, `quincenaNoInterfiereConEnNQuincenasRelativo`). **384 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — incluye 7 de "entre semana" y los de recurrencia del run paralelo), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM independiente verificó edge de fin de mes (hoy=31/8 → segunda quincena rueda a 30/9, primera a 15/9, sin cualificar a 15/9).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room (sin Android SDK).
- **Commits**: `feat(parser): "la quincena" como hito financiero (día 15 / fin de mes)` (rebaseado sobre `a934b65`; auto-merge limpio en `NaturalTaskParser.kt`+tests; conflicto solo en `RUN_LOG.md`, resuelto conservando el ciclo 43 del otro run).
- **HEAD final**: `d98862b` (push OK a `origin/openhands/autonomous-ordia`).

### Siguiente
- Continuar descubrimiento continuo más allá del parser: auditar What Now, captura, rutinas,
  recordatorios, detección de vencidas, búsqueda universal en busca de oportunidades de producto.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).

### Siguiente
- Descubrimiento continuo: auditar `GuardianCoach`, `SummaryService`, `PlanEngine`, deteccion de vencidas importantes.
- Parser: "la quincena" como hito financiero (dia 15 / fin de mes) ya resuelto por el otro run (ciclo 44).
- P1 adjuntos: migracion lazy de adjuntos legacy (evaluar seguridad primero).

---
## Ciclo 47 - 2026-08-13 (UTC) - fix(parser): "el 15" día del mes suelto con artículo

- **Run/ciclo**: 47 (renumerado desde "42 cont.3"→"46": durante el run el remoto avanzó de `e0850e6` a `a11cf48` por runs paralelos — ciclos 43/44/45/46: "entre semana"/"de lunes a viernes" `a934b65`, "la quincena" `d98862b`, listas de días bare `fc1279b`, `nextBestTask` time-aware `2ef4bfa`, guardián overdue raíz `6d0c6a4` + docs `966b799`/`a11cf48`. Procedimiento no destructivo: `git stash` del trabajo "el 15", `git pull --ff-only` a `966b799`, `git stash pop`; auto-merge limpio en `NaturalTaskParser.kt` + tests (cambios ortogonales); al push el remoto había vuelto a avanzar a `a11cf48`, `git pull --rebase`, conflicto solo en `CURRENT_STATE.md` (docs) resuelto conservando el ciclo 46 del otro run y renumerando el mío a 47. Sin force push, sin reset --hard).
- **HEAD inicial**: `e0850e6` (al inicio del run); el remoto avanzó a `966b799` durante el run.
- **Problema seleccionado (P1, parser)**: **"reunión el 15 a las 10"**, **"cita el 20"**, **"entregar el 5 a las 18"** se fechaban en HOY por error. Causa raíz: "el 15" no casaba con `numericDatePattern` (exige `DD/MM` con mes) ni con `monthNamePattern` (exige mes por nombre) → quedaba como residuo en el título; la hora suelta ("a las 10") se aplicaba entonces a HOY → la cita se programaba **hoy** en vez del día 15 (día erróneo, reunión perdida, recordatorio dispara hoy). Brecha ortogonal a "el 15 de marzo" (sí funcionaba vía `monthNameDate`) y a "el 15 de cada mes" (recurrencia mensual); faltaba el "el N" aislado.
- **Prioridad**: P1 (día erróneo de cita → reunión perdida / recordatorio en día equivocado; dato visible incorrecto en planificador y What Now).
- **Causa raíz**: ausencia de patrón para día del mes suelto con artículo; el fallback de fecha nula dejaba que la hora aislada dominara y anclara a HOY.
- **Solución (mínima, `NaturalTaskParser.kt`)**: nuevo `dayOfMonthPattern` = `(?i)\bel\s+(\d{1,2})(?:\s+del?\s+mes)?\b(?!\s*de\s+[a-záéíóúüñ])` (casa "el 15", "el 15 del mes"; el *negative lookahead* evita colisionar con "el 15 de marzo" —lo resuelve `monthNameDate`— y "el 15 de cada mes" —recurrencia mensual—). Match nuevo `dayOfMonthDate` entre `monthNameDate` y `numericDateMatch`; rama en el `when` de `date` entre ambos para que "el 15 de marzo" gane y "el 15" aislado no caiga al fallback de hoy. Reutiliza el helper existente `nextMonthlyDate(from, day)` (día N de este mes, o del siguiente si ya pasó). Limpieza del residuo "el 15" en el título (después de `numericDatePattern.replace`). Heurística honesta (no IA, no random). Validación de rango `day in 1..31`.
- **Tests**: +4 (`diaDelMesSueltoRuedaAlProximoMesSiYaPaso`, `diaDelMesSueltoSinHoraUsaMediodiaCanónico`, `diaDelMesSueltoFuturoEsteMesSeConserva`, `diaDelMesSueltoNoColisionaConFechaConMes`). Confirmado PASS. **395 domain tests PASS** (`bash tools/run_domain_tests.sh`, 391 base remota tras integrar ciclos 43–46 + 4 nuevos, 25 clases). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `NaturalTaskParser.kt`, `NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `cb042c7` — `fix(parser): "el 15" día del mes suelto con artículo (el 15 a las 10 → día 15, no hoy)` (incluye rebase sobre `a11cf48` + ajustes docs post-rebase).
- **HEAD final**: `cb042c7` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM).

### Siguiente
- Parser: tiempo relativo con prefijo "de aquí a" / "de hoy en" / "para dentro de" (frases
  cotidianas aún no cubiertas); revisar interacción de `dayOfMonthPattern` con rango horario
  ("el 15 de 9 a 11") y con recurrencia mensual ("el 15 cada mes" — ya cubierto por `cada mes`).
- Salir del parser y auditar What Now, captura, rutinas, recordatorios, detección de vencidas.

## Ciclo 48 - 2026-08-13 (UTC) - fix(ux): planificador "Vence hoy" falso en planes de otra fecha

- **Run/ciclo**: 48 (base remota `52a4736` ciclo 47; `git pull --ff-only` sin divergencia).
- **HEAD inicial**: `52a4736` (origin/openhands/autonomous-ordia).
- **Problema seleccionado (P2, planificador/UX)**: `DayPlanner.planReason` etiquetaba como `DUE_TODAY`
  ("Vence hoy") a **toda** tarea con `dueAt`, ignorando la `date` del plan. El planificador se
  construye para la `selectedDate` del usuario (y `AutomationEngine.PLAN_DAY` usa hoy). Consecuencia:
  al abrir el plan de **mañana** u otra fecha futura, una tarea que vence ese día mostraba
  **"Vence hoy"** — urgencia falsa, confunde sobre qué vence realmente hoy. La etiqueta mentía ("hoy"
  cuando no lo era).
- **Prioridad**: P2 (UX/corrección de urgencia mostrada al usuario; sin pérdida de datos).
- **Causa raíz**: `planReason(task, now)` no recibía `date`/`zone`; el `when` mapeaba `dueAt != null`
  → `DUE_TODAY` incondicionalmente.
- **Solución (mínima, `DayPlanner.kt`)**: `planReason(task, now, date, zone)` compara la fecha de
  vencimiento con el día **real de hoy** (`DateRules.toLocalDate(now, zone)`): si coincide →
  `DUE_TODAY`; si no → nuevo `DUE_ON_DATE`. Así una tarea que vence hoy sigue `DUE_TODAY` aunque se
  vea en un plan de otra fecha (la urgencia real no cambia con la vista), y una que vence otro día
  no finge "hoy". Añadida rama UI `PlannerScreen.plannerReasonLabel` + string
  `planner_reason_due_on_date` = "Vence este día". Heurística honesta (no IA, no random). Sin nueva
  pantalla ni botón — solo precisión.
- **Tests**: +2 en `DayPlannerTest.kt`: `dueOnFuturePlanDateIsNotLabeledAsToday` (plan de mañana con
  tarea que vence mañana → `DUE_ON_DATE`, no `DUE_TODAY`), `dueTodayIsLabeledAsTodayEvenOnFuturePlanDate`
  (tarea que vence hoy vista en plan de mañana → `DUE_TODAY`). **399 domain tests PASS**
  (`bash tools/run_domain_tests.sh`, 25 clases — 393 base + 2 nuevos + 4 traídos por pull de ciclos
  45–47 ya contados en base). Smoke 25 NO ejecutado (sin `kotlinc` en PATH este run; libs presentes;
  el smoke es subconjunto del suite de dominio ya pasado).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `DayPlanner.kt`, `PlannerScreen.kt`, `strings_screens2.xml`,
  `DayPlannerTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `1f04247` — `fix(ux): planificador no muestra "Vence hoy" en planes de otra fecha` (rebaseado sobre `8950d07` tras colisión con run paralelo IMMINENT_START; auto-merge limpio, cambios ortogonales).
- **HEAD final**: `1f04247` (tras push a `origin/openhands/autonomous-ordia`).
- **Estado**: VERIFIED (JVM). 406 domain tests PASS (393 base + 2 nuevos c.48 + 7 traídos por c.49 IMMINENT_START; rebase limpio).

### Siguiente
- Auditoría de motores no-parser: What Now (`WhatNowEngine`), captura, rutinas, recordatorios
  (reminder scheduling edge cases), detección de vencidas importantes, búsqueda universal.
- Planificador: revisar si otras etiquetas (OVERDUE, SCHEDULED_TIME) también dependen de `date` del
  plan vs hoy de forma inconsistente; evaluar mostrar la hora prevista real en conflictos.

## Ciclo 47 - 2026-08-13 (UTC) - fix(parser): "el 15" día del mes suelto con artículo

- **Run/ciclo**: 46 (base remota `2ef4bfa` ciclo 45; rama `openhands/autonomous-ordia` actualizada sin divergencia).
- **HEAD inicial**: `2ef4bfa` (origin/openhands/autonomous-ordia).
- **Problema seleccionado**: `GuardianEngine` contaba subtareas como tareas lógicas en sus agregados de progreso — el mismo doble conteo que `SummaryEngine` tuvo y se fijó en ciclo 20. `completedAll`, `completedToday`, `overdue` y `derivedExperience(completedTasks)` no filtraban `parentTaskId == null`. Consecuencia: un padre con 4 subtareas vencidas disparaba mood CONCERNED + mensaje "Hay 5 pendientes atrasados" (realidad: 1 tarea lógica); un padre con 3 subtareas completadas daba XP 48 en vez de 12. El guardia mentía al usuario sobre su propio progreso y ánimo.
- **Prioridad**: P1 (fiabilidad/datos: el guardia presenta información incorrecta sobre el estado del usuario).
- **Causa raíz**: los conteos agregados del guardia no replicaron el filtro `parentTaskId == null` que ya usan `SummaryEngine`, `GuardianCoach`, `WhatNowEngine` y `DayPlanner` para representar "tareas lógicas".
- **Solución (mínima, en `GuardianEngine.kt`)**: filtro `it.parentTaskId == null` en los 4 conteos (`completedAll`, `completedToday`, `overdue`, `derivedExperience.completedTasks`). Sin nueva pantalla, sin nueva interfaz — solo precisión.
- **Tests**: +2 en `GuardianEngineTest.kt`: `overdueCountIgnoresSubtasksToAvoidInflatedConcern` (1 padre vencido+4 subtareas → mood≠CONCERNED, mensaje sin "5"), `derivedExperienceCountsLogicalTasksNotSubtasks` (1 padre+3 subtareas completadas → 12 XP, no 48). Probe JVM independiente confirmó antes (mood=CONCERNED, XP=48) y después (mood=CURIOUS, XP=12). **392 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases — 390 base + 2 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `GuardianEngine.kt`, `GuardianEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.

### Siguiente
- Descubrimiento continuo: auditar `WhatNowEngine`, captura, recordatorios, detección de vencidas importantes, búsqueda universal.- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).

## Ciclo 47 (run paralelo) - 2026-08-13 (UTC) - feat: nextBestTask alineado con IMMINENT_START (widget/asistente)
- **Run/ciclo**: 47.
- **HEAD inicial**: `15e170f` (remoto `966b799`, sin divergencia).
- **Problema seleccionado**: el ciclo 46 añadió detección de compromisos inminentes a `WhatNowEngine` (tarjeta What Now de `TodayScreen`), pero **NO** a `TaskRules.nextBestTask`, la heurística compartida por el **widget de inicio**, el **asistente** y el `nextTask` del ViewModel. Allí un compromiso inminente seguía cayendo en `isScheduledLater` (rank -1, último recurso): el widget sugería una tarea cualquiera de la Bandeja **mientras una reunión empezaba en 5 min**. La superficie más vista daba una respuesta menos oportuna que la pantalla principal.
- **Prioridad**: P1 (consistencia de inteligencia entre superficies; evitar olvido de compromiso inminente en widget).
- **Causa raíz**: `TaskRules.nextBestTask`/`timeRank` carecían de la rama inminente que `WhatNowEngine` ya tenía; además la lógica `isImminentStart`/`IMMINENT_WINDOW_MINUTES` estaba duplicada.
- **Solución (mínima, en `TaskRules.kt` + DRY)**: `isImminentStart(task, now)` + `IMMINENT_WINDOW_MINUTES = 15` movidos a `TaskRules` (públicos, fuente única de verdad); `WhatNowEngine.isImminentStart` reducido a un delegado, eliminando la constante duplicada. `TaskRules.timeRank` añade la rama inminente (rank 4) en el mismo orden honesto que `WhatNowEngine`: EN_CURSO > EN_PROGRESO > ATRASADA > INMINENTE > VENCE_HOY > URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún no empieza. Retrocompatible: `nextBestTask(tasks)` sigue delegando con `now`/zona por defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` compilan sin cambio. Sin nueva pantalla ni botón: misma heurística, consistente en todas las superficies.
- **Tests**: +3 (`nextBestTask_prefersImminentStartOverInbox`, `nextBestTask_startOutsideImminentWindowStaysDeprioritized`, `nextBestTask_overdueBeatsImminentStart`); las 4 pruebas de `WhatNowEngineTest` para IMMINENT_START siguen en verde (delegación sin cambio de comportamiento). **404 domain tests PASS** (`bash tools/run_domain_tests.sh`, 25 clases) tras rebase sobre los ciclos 47 "el 15" + 46 guardian del otro run (base 398 + 4 parser + 2 guardian). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del widget (sin Android SDK).
- **Archivos modificados**: `TaskRules.kt`, `WhatNowEngine.kt`, `TaskRulesTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `f406365` (rebasado sobre `52a4736` del otro run: ciclos 47 "el 15" + 46 guardiÃ¡n; push pendiente a `origin/openhands/autonomous-ordia`).

### Siguiente
- Descubrimiento continuo: auditar `GuardianCoach`, `SummaryService`, `PlanEngine`, detección de vencidas importantes, búsqueda universal.
- Parser: manejo robusto de múltiples marcadores temporales en una frase.
- P1 adjuntos: migración lazy de adjuntos legacy (evaluar seguridad primero).

## Ciclo 56 - 2026-08-13 (UTC) - fix(reminders): autocompletar padre al cerrar última subtarea desde notificación
- **Run/ciclo**: 56.
- **HEAD inicial**: `b5c96d5` (synced con `origin/openhands/autonomous-ordia`).
- **Problema seleccionado**: `ReminderActionReceiver.ACTION_COMPLETE` completaba una subtarea, cancelaba su recordatorio y generaba su recurrencia, pero **NO** completaba la tarea padre cuando era la última subtarea pendiente. La app (`OrdiaViewModel.toggleTask` → `completeParentAutomatically` vía `SubtaskRules.shouldAutoCompleteParent`) sí lo hace. Resultado: al completar el último hijo desde la notificación, el padre quedaba "pendiente" para siempre → **tarea olvidada** (P1: recuperación de tareas olvidadas / pérdida de estado).
- **Prioridad**: P1 (persistencia/integridad de estado, tareas olvidadas, consistencia notificación vs app).
- **Causa raíz**: el path de notificación (`BroadcastReceiver`) duplicaba parte de la lógica de `toggleTask` pero omitía la rama de autocompletado del padre y el registro de automatización para deshacer.
- **Solución (mínima)**: nuevo helper `completeParentIfDone(app, repo, completedSubtask, now)` en `ReminderActionReceiver`, llamado tras completar la subtarea en `ACTION_COMPLETE`. Refleja fielmente `completeParentAutomatically`: (1) `SubtaskRules.shouldAutoCompleteParent(parent, siblings)` (misma fuente de verdad que la app), (2) actualiza padre (completed/status COMPLETED/completedAt/updatedAt), (3) cancela recordatorio del padre, (4) `RecurrenceEngine.nextOccurrence` + reprograma la próxima ocurrencia, (5) registra `AutomationLogEntity` (type `subtask_auto`, `affectedTaskIdsJson`, `undoPayloadJson` con snapshot del padre) para deshacer. Sin emitir eventos de UI (un `BroadcastReceiver` no puede). Sin nueva pantalla ni botón: mismo comportamiento que la app, ahora alcanzable desde la notificación.
- **Tests**: lógica núcleo `SubtaskRules.shouldAutoCompleteParent` ya cubierta por `SubtaskRulesTest` (JVM). **435 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — base c.55 tras rebase). Smoke 25 OK (`tools/run_domain_checks.sh`).
- **NO VERIFICADO**: el receptor `ReminderActionReceiver` en sí (requiere Android `Context`/`BroadcastReceiver`/Room con DAOs reales → no ejecutable en JVM pura); gradle/lint/assemble/UI. La corrección lógica depende del mismo `SubtaskRules` ya probado.
- **Colisión de remoto (no destructiva)**: al push, el remoto había avanzado 1 commit (ciclo 55 parser "cada mañana/tarde/noche" sobre la misma base `b5c96d5`). `git pull --rebase`: conflicto solo en `CURRENT_STATE.md` (cabecera de estado, ambos editaban la misma línea). Resolución conservando ambos trabajos; este fix renumerado de ciclo 55 → 56 (aterrizó después). Áreas ortogonales (`ReminderActionReceiver` vs `NaturalTaskParser`). Sin force push, sin reset --hard.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/reminders/ReminderActionReceiver.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `5478f73`.
- **Estado**: FIXED (lógica); recepción Android NO VERIFICADO (sin Android SDK).

### Siguiente
- Auditoría: `GuardianCoach` detección de vencidas importantes; `SummaryService`; `PlanEngine` replanificación.
- Parser: múltiples marcadores temporales en una frase.
- Búsqueda universal: relaciones notas/tareas/proyectos.
- Revisar consistencia de autocompletado/reapertura del padre en otros entry points (widget quick-complete, asistente).

## Ciclo 61 - 2026-08-13 (UTC) - fix(parser): meridiem sin "a las" + hora de inicio del rango como dueAt

- **Run/ciclo**: 61.
- **HEAD inicial**: `5e6cea8` (c.60, synced con `origin/openhands/autonomous-ordia`).
- **Problema seleccionado**: dos bugs P1 de captura/agenda en `NaturalTaskParser`:
  - **BUG A**: una hora con meridiem pero SIN "a las" ("Reunión 2pm", "Cita 9am", "Vuelo 8:30pm") se agendaba como **AM** (02:00 en vez de 14:00). Los `timePatterns`[1] (N:MM) y [2] (Nam/Pm) llevan el meridiem en el **grupo 3**, pero `explicitTimeData` leía el meridiem del **grupo 4** (que solo existe en el patrón[0] "a las N", donde el grupo 3 es la fracción "y media"/"y cuarto"). Así, en los patrones sin "a las" el meridiem se perdía → la hora se interpretaba en AM. Una reunión de tarde ("2pm") aparecía a las 02:00 de la madrugada.
  - **BUG B**: en un rango "de H1 [meridiem] a H2 [meridiem]" sin "a las" ("Clase de 9 de la tarde a 11 de la noche", "Reunión de 2pm a 4pm"), la `dueAt` caía a la **hora canónica de la parte del día** ("de la tarde" → 15:00) en vez de la **hora de inicio del rango** (21:00). El rango se procesaba para la duración (120 min, correcto) y se eliminaba del título, pero como no había tiempo explícito, la hora para `dueAt` venía del respaldo `standalonePartOfDayTime`. El inicio real del evento se ignoraba → la cita se agendaba a la hora canónica genérica, no a la hora real del evento.
- **Prioridad**: P1 (agenda/captura correcta, evita citas a la hora equivocada = no olvidos/falsos horarios; integridad de intención temporal).
- **Causa raíz**:
  - BUG A: `explicitTimeData` leía `meridiem = groupValues[4]` (asumiendo el layout del patrón[0]); los patrones[1]/[2] no tienen grupo 4 de meridiem.
  - BUG B: `rangeMatch` (que resuelve cada extremo a hora absoluta con su meridiem) se computaba **después** de `dueAt`; la hora para `dueAt` venía del respaldo `partOfDay`/`standalonePartOfDay`, no del inicio validado del rango.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  - BUG A: `explicitTimeData` ahora lee el meridiem del grupo 4 si existe, si no del grupo 3 (disambiguando fracción vs meridiem según el patrón que casó). `hasExplicitMeridiem` se marca correctamente para cualquier patrón.
  - BUG B: se mueve el bloque `rangeMatch` (cálculo del rango validado) a **antes** de la resolución de `parsedTime`/`dueAt`, y se extrae `rangeStartTime: LocalTime?` del inicio del rango (resolución absoluta con meridiem, solo si el rango fue validado — no filtra horas de rangos rechazados como "de 2 a 5 entradas"). `rangeStartTime` entra en la cadena de respaldo de `parsedTime` **después** del tiempo explícito ("a las") y **antes** de los respaldos canónicos de parte del día. Así un tiempo explícito sigue ganando, pero sin él la hora de inicio del rango reemplaza a la canónica genérica.
- **Tests**: +6 en `NaturalTaskParserTest.kt` (`barePmTimeWithoutAParsesAsPm` "2pm"→14:00, `bareAmTimeWithoutAParsesAsAm` "9am"→09:00, `barePmTimeWithMinutesWithoutAParsesAsPm` "8:30pm"→20:30, `rangeWithDeLaTardeSetsDueAtToStart` "de 9 de la tarde a 11 de la noche"→due 21:00 + dur 120, `rangeWithPmMeridiemSetsDueAtToStart` "de 2pm a 4pm"→due 14:00, `rangeWithAmMeridiemSetsDueAtToStart` "de 9am a 11am"→due 09:00). **472 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 463 c.60 + 6 nuevos c.61 + 3 c.62 "pasado mañana" de run paralelo reconciliado), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM `Probe4.kt` confirmó ambos bugs antes/después (descartado tras verificación). Sin regresión (rangos 24h, "de 2 a 5 entradas" rechazado, "curso de 8:30 a 10:30 horas" intactos). **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Colisión de remoto (no destructiva)**: al intentar el commit, el remoto había avanzado 1 commit (`ee4807d`, c.62 "pasado mañana" de un run paralelo) sobre la base `4d2ca71` (c.60). `git stash` → `pull --ff-only origin openhands/autonomous-ordia` → `stash pop`: los cambios de **código** se auto-mergearon limpiamente (áreas ortogonales: mi fix toca `explicitTimeData`/`rangeMatch`/`rangeStartTime`; el c.62 toca el consumo de título de "pasado día-semana"); único conflicto en `BACKLOG.md` (cola de la tabla, ambos editábamos) resuelto conservando la entrada c.59 corregida del remoto + mis entradas c.61/c.61-P2. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD final**: `0d779d2`.
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

### Siguiente
- Forma standalone "N de la tarde" sin rango ("Taller 9 de la tarde" → 15:00, debería 21:00): documentado en BACKLOG (P2, adición de feature, no bug de rango).
- Descubrimiento continuo: `GuardianCoach` detección de vencidas importantes; `SummaryService`; `PlanEngine` replanificación; búsqueda universal.
- Parser: múltiples marcadores temporales en una frase.

## Ciclo 59 - 2026-08-13 (UTC) - fix(parser): verbo de recordatorio sin cantidad programa recordatorio y limpia título
- **Run/ciclo**: 59.
- **HEAD inicial**: `053e7ff` (c.57, synced con `origin/openhands/autonomous-ordia`).
- **Problema seleccionado**: `NaturalTaskParser` no programaba recordatorio cuando el usuario usaba un verbo de aviso ("recuérdame/avísame/no dejes que olvide") **sin cantidad explícita**. "recuérdame llamar a mamá mañana a las 3 de la tarde" → `reminderOffsetMinutes=null` aunque `dueAt` estuviera seteado → **ningún recordatorio agendado** (`reminderAt = dueAt - offset` = null) **Y** el verbo "recuérdame" quedaba como residuo en el título. El usuario pedía un aviso explícito y Ordía lo ignoraba silenciosamente → la cita se olvidaba (P1: evita olvidos, persistencia/intención perdida). Simétrico al `reminderSignal` de `UniversalCaptureEngine`, pero el parser no recogía la intención en la ruta TASK con `dueAt`.
- **Prioridad**: P1 (recordatorios/captura/integridad de intención).
- **Causa raíz**: los `reminderPatterns` solo programaban `reminderOffsetMinutes` cuando extraían una cantidad ("2 horas antes"); el verbo solo no activaba nada, y tampoco se limpiaba del título.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nuevo `bareReminderVerbPattern` (`recuérdame|avísame|notifícame|recordatorio|no dejes que olvide`) detectado **tras** extraer los recordatorios con cantidad (así "recuérdame 2 horas antes" usa el offset explícito y NO el respaldo). Si hay verbo + `dueAt` + sin offset → **30 min antes** (convención del projeto = `CommitmentEngine.DEFAULT_REMINDER_OFFSET_MS` y `EditorDialogs`). El verbo se elimina del título **tras** consumir fechas/horas (para no romper el parseo de "recuérdame mañana a las 3", donde "mañana" es fecha). Sin `dueAt` no se falsifica el offset (no se puede programar `reminderAt`). Heurística honesta, riesgo de falso positivo bajo.
- **Tests**: +5 en `NaturalTaskParserTest.kt` (`verboRecordatorioSinCantidadConDueAplicaOffset30`, `verboAvisameSinCantidadConDueAplicaOffset30`, `verboNoDejesQueOlvideConDueAplicaOffset30`, `verboRecordatorioSinCantidadSinDueNoFalsificaOffset`, `verboRecordatorioConCantidadExplicitaUsaOffsetExplicito`). **455 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 450 c.58 + 5 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión (offsets explícitos y fracciones intactos verificados con probe). **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app.
- **Colisión de remoto (no destructiva)**: durante el trabajo, dos commits aterrizaron en remoto — c.58 "y media"/"y cuarto" + "en la tarde" de otro run (`db30b9c`) y un commit de CI delivery (`e611da6`). Al hacer `git stash` → `pull --ff-only origin openhands/autonomous-ordia` → `stash pop`, los cambios de **código** (parser + tests) se auto-mergearon limpiamente (áreas ortogonales: mi fix toca `bareReminderVerbPattern`/recordatorios; el c.58 toca `timePatterns`/`standalonePartOfDayPattern`/`mananaAsDate`); solo `CURRENT_STATE.md` quedó en conflicto (cabecera de estado, ambos editaban la misma línea) y se resolvió conservando ambos trabajos, renumerando este fix de c.58 → c.59 (el otro run tomó c.58). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

## Ciclo 62 - 2026-08-13 (UTC) - fix(reminders): el editor preserva el offset de recordatorio personalizado al editar
- **Run/ciclo**: 62.
- **HEAD inicial**: `ee4807d` (c.60, synced con `origin/openhands/autonomous-ordia`).
- **Problema seleccionado**: `EditorDialogs` (edición de tarea) recalculaba `reminderAt = dueAt - 30*60_000L` en CADA guardado cuando el toggle de recordatorio estaba activo. Para una tarea existente con offset explícito distinto de 30 min ("recuérdame 2 horas antes" → `reminderAt = due - 120min`, capturado por el parser o `CommitmentEngine`), editar un campo NO relacionado (prioridad/proyecto/etiquetas/flagged) y guardar SOBRESCRIBÍA el offset a 30 min → el usuario pedía "2h antes" y Ordía avisaba solo 30 min antes, silenciosamente. Peor en recurrentes: `RecurrenceEngine.nextOccurrence` reutiliza el offset (`dueAt - reminderAt`) en TODAS las ocurrencias futuras → una edición inocua corrompía el recordatorio para siempre en cada nueva ocurrencia (P1: integridad de datos, recordatorios, evita olvidos).
- **Prioridad**: P1 (datos/recordatorios/integridad).
- **Causa raíz**: cálculo inline hardcoded `dueAt - 30min` en `EditorDialogs.kt:213`, sin considerar el `reminderAt` previo de la tarea existente. Constante duplicada y privada en `CommitmentEngine`.
- **Solución (mínima, sin nueva pantalla/botón)**: nueva regla pura `ReminderRules.resolveReminderAt(existing, reminderEnabled, dueAt)`: (1) `reminderEnabled=false` o `dueAt=null` → null; (2) `existing` con `reminderAt`+`dueAt` previos y `dueAt` sin cambios → conserva `reminderAt` exacto (offset intacto); (3) `dueAt` cambiado → traslada offset `dueAt - (oldDueAt - oldReminderAt)` ("15 min antes" sigue siendo 15 min antes); (4) resto (nueva, o recordatorio recién activado sin offset previo) → `DEFAULT_REMINDER_OFFSET_MS` (30 min). Constante `DEFAULT_REMINDER_OFFSET_MS` centralizada en `ReminderRules`. `EditorDialogs` la usa en lugar del cálculo inline. Simétrico al c.52 (snooze no corrompe offset) y c.56 (consistencia notificación vs app): la preferencia de recordatorio del usuario es sagrada.
- **Tests**: +9 en `ReminderRulesTest.kt` (`resolveReminderAt_disabledReturnsNull`, `resolveReminderAt_nullDueReturnsNull`, `resolveReminderAt_newTaskUsesDefaultOffset`, `resolveReminderAt_existingWithoutDueTimeUsesDefaultOffset`, `resolveReminderAt_editingUnrelatedField_preservesCustomOffset` [2h], `resolveReminderAt_changingDue_translatesOffset` [15min trasladado], `resolveReminderAt_recurrenceEditKeepsOffsetForNextOccurrence`, `resolveReminderAt_togglingOffReturnsNull`, `resolveReminderAt_clearingDueReturnsNull`). **481 domain tests PASS** tras rebase (`bash tools/run_domain_tests.sh`, 26 clases — incluye +6 del run paralelo c.61 parser-meridiem reconciliado + mis 9 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI; `EditorDialogs` es Compose (requiere Android SDK). La corrección lógica vive en la regla pura ya probada.
- **Colisión de remoto (no destructiva)**: al intentar el commit, el remoto había avanzado 1 commit (`0d779d2`, c.61 parser-meridiem de un run paralelo) sobre la base `ee4807d`. `git rebase origin/openhands/autonomous-ordia`: los cambios de **código** se auto-mergearon limpiamente (áreas ortogonales: mi fix toca `ReminderRules`/`EditorDialogs`; el c.61 toca `NaturalTaskParser`); único conflicto en `CURRENT_STATE.md` (cabecera de estado + tabla, ambos editábamos) resuelto conservando AMBOS trabajos (entrada c.61 del remoto + mi entrada renumerada a c.62). Mi RUN_LOG renombrado de c.61 → c.62 para evitar colisión de numeración. Re-ejecutados tests tras rebase: 481 PASS. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/ReminderRules.kt`, `app/src/main/java/com/ordia/app/ui/components/EditorDialogs.kt`, `app/src/test/java/com/ordia/app/domain/ReminderRulesTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `3fb19cd` (post-push a openhands/autonomous-ordia).
- **Estado**: FIXED → VERIFIED (dominio JVM); editor Compose NO VERIFICADO (sin Android SDK).

### Siguiente
- Auditoría: `GuardianCoach` detección de vencidas importantes; `SummaryService`; `PlanEngine` replanificación automática; búsqueda universal.
- Revisar otros entry points que recalculen `reminderAt` sin preservar offset (widget quick-edit, asistente, captura rápida).
- Parser: múltiples marcadores temporales en una frase.

## Ciclo 63 - 2026-08-13 (UTC) - feat(coach): Guardián detecta vencidas importantes y recupera tareas olvidadas
- **Run/ciclo**: 63.
- **HEAD inicial**: `3a50988` (c.62, synced con `origin/openhands/autonomous-ordia`).
- **Problema seleccionado**: la rama de vencidas de `GuardianCoach.insight` daba tratamiento **idéntico** a toda tarea atrasada: `Tone.GENTLE` + mensaje genérico ("Esta tarea está atrasada. Empieza con un bloque corto." / "Tienes N tareas atrasadas. Comienza por esta.") sin distinguir 10 minutos de retraso de 10 días. El coach no ayudaba a **recuperar** compromisos olvidados: solo repetía "empieza por esta". Una tarea que lleva 3 semanas atrasada es un compromiso que se está dejando pasar; el coach debería surface esa gravedad y plantear la decisión real (hacer/reprogramar/quitar), no un nudge genérico. (P2: inteligencia honesta, "detección de vencidas importantes", "recuperación de tareas olvidadas" — área de dirección explícita.)
- **Prioridad**: P2 (inteligencia/producto; no pérdida de datos).
- **Causa raíz**: ausencia de heurística de severidad: el único matiz era `overdue.size == 1` vs `>1`; no existía noción de *antigüedad* de lo vencido.
- **Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**: nueva heurística honesta de aritmética temporal en `GuardianCoach.insight`: calcula `mostOverdueDays = max((now - dueAt) / MILLIS_PER_DAY)` entre las vencidas. Si la más atrasada lleva **≥ 2 días** (`FORGOTTEN_DAYS_THRESHOLD`) se considera **olvidada**: sube a `Tone.FOCUSED` y el mensaje surface cuánto lleva (`forgottenAgeLabel`: "1 día"/"3 días"/"2 semanas"/"1 mes") y plantea la decisión real ("Hazla hoy o muévela con intención, no la dejes pasar otra vez" / "Elige una: hacerla hoy, reprogramarla o quitarla"). Si es leve (< 2 días) mantiene `GENTLE` + mensaje actual. Heurística honesta (tiempo real sobre `dueAt`, no random ni "IA" fingida).
- **Tests**: +4 en `GuardianCoachTest.kt` (`mildlyOverdueSameDayStaysGentle`, `forgottenOverdueTaskBecomesFocusedAndSurfacesAge`, `forgottenOverdueUsesWeeksLabelPastSevenDays`, `forgottenOverdueGroupSurfacesOldestAge`). El test previo `overdueWorkWinsOverEverythingElse` (27h → GENTLE) sigue verde: 27h < 2 días. **485 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI; `GuardianCoach` se consume en Compose (sin Android SDK). La lógica vive en el dominio probado.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/GuardianCoach.kt`, `app/src/test/java/com/ordia/app/domain/GuardianCoachTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (pendiente de commit).
- **Estado**: FIXED → VERIFIED (dominio JVM); coach en UI NO VERIFICADO (sin Android SDK).

### Siguiente
- `SummaryService`/`SummaryEngine`: resumen del día más accionable.
- `PlanEngine` replanificación automática; búsqueda universal.
- Parser: múltiples marcadores temporales en una frase.

## Ciclo 67 - 2026-08-13 (UTC) - fix(summary): sugerencia de posposición nunca nombra tareas en curso ni inminentes

- **Run/ciclo**: 67 (continúa directamente sobre c.66; refina la heurística recién aterrizada).
- **HEAD inicial**: `a391355` (c.66 pushed; base actualizada y sincronizada con remoto, sin divergencia).
- **Problema seleccionado**: la sugerencia de posposición de c.66 (`mostDeferrableTask`) excluía solo las tareas **vencidas**, pero NO las **en curso ahora** (`isInProgressNow`) ni las **inminentes** (`isImminentStart`, compromiso que arranca en ≤15 min). Consecuencia real: un día saturado donde la tarea más posponible (LOW) es una reunión que empieza en 5 minutos → Ordía sugería "deja para mañana «[la reunión]»" — un consejo **dañino** que haría perder la cita. Igual si esa LOW era la que el usuario está ejecutando en ese instante. La heurística "menos prioritaria + más margen" puede elegir legítimamente un compromiso vivo porque su `startAt`/`dueAt` lo permite. P2 (fiabilidad/inteligencia honesta; el "consejo" activo puede perjudicar).
- **Prioridad**: P2 (mejora funcional/inteligencia honesta; continuidad directa de c.66).
- **Causa raíz**: el filtro `deferrable` solo consideraba `!isOverdue`; "lo que ocurre ahora mismo" es una noción ya modelada en `TaskRules` (`isInProgressNow`, `isImminentStart`) para `WhatNowEngine`/`nextBestTask`, pero la sugerencia de posposición no la reusaba.
- **Solución (mínima, sin nueva pantalla/botón — "mejor decisión automáticamente")**:
  - `TaskRules.isInProgressNow` pasa de **privado** a **público** (fuente única de verdad, simétrico a `isImminentStart` ya público) con KDoc.
  - `WhatNowEngine.isInProgressNow` (copia privada duplicada) ahora **delega** en `TaskRules.isInProgressNow` (DRY, como ya hacía `isImminentStart`). Comportamiento idéntico; cero duplicación.
  - `SummaryEngine.mostDeferrableTask`: el filtro `deferrable` añade `!isInProgressNow(task, now) && !isImminentStart(task, now)`. Nunca sugiere posponer lo que se está haciendo ahora ni una cita a punto de empezar; entre las restantes, la heurística de prioridad+margen es la misma. Si todas las posponibles sin empeorar retraso son en-curso/inminentes → sin sugerencia (null), igual que cuando solo quedan vencidas.
  - Heurística determinista, sin random ni "IA". No muta nada.
- **Tests**: +3 en `SummaryEngineTest.kt` (`deferralSuggestion_neverSuggestsInProgressTask`, `deferralSuggestion_neverSuggestsImminentStartTask`, `deferralSuggestion_whenOnlyPosponiblesAreInProgressOrImminent_returnsNull`). **510 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 507 c.66 + 3 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión en los 507 previos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea en la tarjeta de Today en dispositivo; integración Android del widget/asistente que usa `nextBestTask` (delegación `isInProgressNow` deja comportamiento idéntico, pero sin compilar en Android).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/{TaskRules,WhatNowEngine,SummaryEngine}.kt`, `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit+push).
- **Estado**: FIXED → VERIFIED (dominio JVM); Android/UI NO VERIFICADO (sin Android SDK).

### Siguiente
- Evaluar `PlanEngine`/replanificación: si OVERLOADED recurrente varios días, sugerir redistribuir (no solo nombrar UNA tarea).
- Descubrimiento continuo: búsqueda universal; `DayPlanner` respetar pausas/existente; detección de compromisos en notas; múltiples marcadores temporales en una frase; acción rápida "posponer con un toque" (evaluar fricción vs complejidad visible).


## Ciclo 66 - 2026-08-13 (UTC) - feat(summary): sugerencia concreta de tarea a posponer cuando el día está saturado

- **Run/ciclo**: 66 (continúa directamente sobre c.65; HEAD inicial `aa65608` (c.67 monthNameMatch aterrizado en paralelo; base reconstruida tras merge; original c.65 `970b6c8`)).
- **Problema seleccionado**: el veredicto `OVERLOADED` (c.65) decía "No cabe todo hoy. Elige qué dejar para mañana." — correcto pero **vago**: el usuario tenía que recorrer la lista mentalmente para decidir cuál tarea soltar. La decisión más útil en ese momento es **nombrar** la tarea más posponible, no añadir otro botón/pantalla. (Continúa el "Siguiente" documentado en c.65: "sugerir automáticamente qué tarea mover a mañana".) Área de dirección explícita "replanificación automática"/"priorización inteligente".
- **Prioridad**: P2 (inteligencia/honesta; mejora funcional del resumen, no pérdida de datos).
- **Causa raíz**: `DaySummary` no tenía ninguna noción de "cuál tarea es más posponible"; `assessDayLoad` solo decía OVERLOADED pero no aportaba una recomendación concreta.
- **Solución (mínima, `SummaryEngine.kt` + `TodayScreen.kt` + `strings_screens1.xml`, sin nueva pantalla/botón)**:
  - Nuevo `data class DeferralSuggestion(taskId, title)` + campo `deferralSuggestion` en `DaySummary`, poblado solo si `dayLoad == OVERLOADED`.
  - `SummaryEngine.mostDeferrableTask`: heurística honesta y conservadora — **nunca** sugiere una vencida (`!TaskRules.isOverdue`); entre las de hoy no vencidas elige la de **menor prioridad** (`priorityDeferralWeight`: LOW 3 > NORMAL 2 > HIGH 1 > URGENT 0 = más posponible primero) y, a igual prioridad, la que **vence más tarde** (`dueAt`/`startAt` ascendente → el mayor = más margen) vía `maxWithOrNull`. No muta nada: solo nombra.
  - `TodayScreen.kt`: la línea OVERLOADED genérica se reemplaza por "No cabe todo hoy. Una opción es dejar para mañana «<tarea>»." en la MISMA línea `bodySmall` existente. Sin tarjeta, sin botón, sin acción automática (el usuario decide; Ordía solo sugiere).
  - Heurística determinista, sin random ni "IA". No simula modelo.
- **Bug encontrado y corregido durante el run**: el primer desempate usaba `thenByDescending { dueAt }` creyendo que ordenaría "mayor due primero" — pero `maxWith` sobre `thenByDescending` selecciona el **mínimo** due (vence más temprano), justo lo contrario. Corregido a `thenBy { dueAt }` (ascendente → `maxWith` elige el mayor = vence más tarde). Verificado con un probe Kotlin aislado antes de re-run de tests.
- **Tests**: +4 en `SummaryEngineTest.kt` (`deferralSuggestion_suggestsLowestPriorityNonOverdue`, `deferralSuggestion_excludesOverdueTasks`, `deferralSuggestion_atSamePriorityPicksLatestDueToMaximizeMargin`, `deferralSuggestion_whenAllRemainingTasksAreOverdue_returnsNull`). **507 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 502 c.65+c.67 paralelo + 5 netos c.66), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea en la tarjeta de Today en dispositivo.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt`, `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt`, `app/src/main/res/values/strings_screens1.xml`, `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `996ec8a` (commit c.66, base rebaseada sobre c.67 `aa65608` sin conflictos residuales; 507 tests PASS) + commit docs `a391355` (HEAD final tras push; ambos empujados al remoto `openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM); UI en app NO VERIFICADO (sin Android SDK).

### Siguiente
- Sugerencia accionable: permitir posponer la tarea sugerida con UN toque (acción rápida desde la propia línea) — evaluar si aporta fricción cero o más complejidad visible.
- `PlanEngine`/replanificación más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: búsqueda universal; `DayPlanner` respetar pausas/existente; detección de compromisos en notas; múltiples marcadores temporales en una frase.

## Ciclo 65 - 2026-08-13 (UTC) - feat(summary): veredicto honesto del día (DayLoad) en la tarjeta de Today

- **Run/ciclo**: 65 (continúa sobre c.64).
- **HEAD inicial**: `890e8b4` (c.64, parser standalone "N de la tarde/noche").
- **Problema seleccionado**: la tarjeta de resumen de Today mostraba "X completadas · Y para hoy" + badge de minutos ("120m") pero NO convertía ese dato en una decisión accionable. El usuario tenía que hacer la aritmética mental ("120m a las 12:00, ¿caben?"). Área de dirección explícita ("priorización inteligente", "mejores resúmenes del día", "What Now más útil"). Era el "Siguiente" documentado al final del ciclo 64 ("SummaryEngine resumen del día más accionable").
- **Prioridad**: P2 (mejora funcional/inteligencia honesta; no pérdida de datos).
- **Causa raíz**: `DaySummary` no tenía noción de "capacidad restante del día". `remainingMinutesToday` medía el trabajo pendiente pero no se comparaba con el tiempo libre hasta el fin de jornada → no había veredicto.
- **Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - Nueva enum `DayLoad { LIGHT, ON_TRACK, FULL, OVERLOADED }` + campo `dayLoad` en `DaySummary`.
  - `SummaryEngine.assessDayLoad` (privado, dominio puro): `freeMinutes = (18:00 - max(now, 9:00))`; si `remainingToday<=0`→LIGHT; si `freeMinutes<=0`→OVERLOADED; si `remainingMinutes <= free/2`→ON_TRACK; si `<= free`→FULL; si no→OVERLOADED. Misma ventana 9–18 que `DayPlanner` (misma fuente de verdad).
  - `TodayScreen.kt`: UNA línea `bodySmall` dentro de la tarjeta de resumen existente (reutiliza el `Surface`/`Column` existente; sin nueva tarjeta/botón). 3 strings accionables en `strings_screens1.xml`.
  - Heurística honesta, determinista, sin random ni "IA".
- **Tests**: +6 en `SummaryEngineTest.kt` (LIGHT, ON_TRACK, FULL, OVERLOADED, post-fin-de-jornada, inicio de día con trabajo modesto). **500 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 494 c.64 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea en la tarjeta de Today en dispositivo.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt`, `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt`, `app/src/main/res/values/strings_screens1.xml`, `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit+push).
- **Estado**: FIXED → VERIFIED (dominio JVM); UI en app NO VERIFICADO (sin Android SDK).

### Siguiente
- `PlanEngine`/replanificación: si OVERLOADED, sugerir automáticamente qué tarea mover a mañana (la menos prioritaria / más posponible) en vez de solo decir "elige qué dejar".
- Descubrimiento continuo: búsqueda universal; `DayPlanner` respetar pausas/existente; detección de compromisos en notas.

## Ciclo 64 - 2026-08-13 (UTC) - fix(parser): forma standalone "N de la tarde/noche" sin "a las" ni rango

- **Run/ciclo**: 64 (renumerado: el remoto tomó c.62 = recordatorios del editor, c.63 = Guardián vencidas; este run paralelo aterriza como c.64).
- **HEAD inicial**: `6e43206` (c.63, synced tras stash/pull/pop; base original del run `dfff470` c.61).
- **Problema seleccionado**: la forma cotidiana **"Taller 9 de la tarde"** (hora + parte del día, SIN "a las" y SIN segundo extremo de rango) se agendaba a la hora **canónica** de la parte del día (tarde→15:00) en vez de la hora **explícita** (9→21:00), y el número quedaba como residuo en el título ("Taller 9"). Igual para "Cita 10 de la mañana"→09:00 (debería 10:00), "Evento 9 de la madrugada"→04:00 (debería 09:00). El usuario escribía una hora concreta y Ordía la ignoraba → cita agendada a hora equivocada (P2: captura/agenda/integridad de intención). Era el siguiente item documentado en el "Siguiente" del ciclo 61 (BACKLOG P2 "ABIERTO").
- **Prioridad**: P2 (captura/agenda; mejorada funcional del parser, no pérdida de datos).
- **Causa raíz**: sin segundo extremo de rango, `rangeMatch` no casaba y no había `rangeStartTime`; la hora caía al respaldo `standalonePartOfDayTime` (canónica "de la tarde"→15:00), que gana sobre cualquier número suelto. El ciclo 61 ya arregló la variante **con rango**; esta es la forma **standalone**.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nuevo `standaloneHourPartOfDayPattern` + `resolveStandaloneHourPartOfDay` (tarde/noche→+12 si N<12; 12 de la noche→0 medianoche; 12 de la tarde→12 mediodía; madrugada/mañana→AM tal cual). Insertado en la cadena de respaldo de `parsedTime` **antes** de `standalonePartOfDayTime` (canónica) para que la hora explícita gane.
- **Guard anti-regresión (crítico)**: solo se aplica cuando `explicitTime == null` (no hubo "a las …"). Sin este guard, el patrón robaba "9 de la tarde" de "a las 9 de la tarde" y dejaba el residuo "a las" en el título. El lookahead negativo evita colisión con fechas ("9 de marzo" → mes, no parte del día).
- **Tests**: +9 en `NaturalTaskParserTest.kt`. **494 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 485 c.63 + 9 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM confirmó antes/después en 21 casos. Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app.
- **Colisión de remoto (no destructiva)**: durante el trabajo aterrizaron en remoto c.62 (recordatorios del editor) y c.63 (Guardián vencidas) de runs paralelos. `git stash` → `pull --ff-only` → `stash pop`: los cambios de **código** (parser + tests) se auto-mergearon limpiamente (áreas ortogonales); solo los 3 archivos `AI_AUTONOMY/*` quedaron en conflicto (ambos editaban cabeceras/tablas) y se resolvieron conservando AMBOS trabajos, renumerando este fix c.62 → c.64. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit+push).
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

### Siguiente
- Caso límite ambiguo: "12 de la mañana" → 00:00 (¿medianoche o mediodía?); documentar decisión.
- Descubrimiento continuo: `SummaryService`/`SummaryEngine` resumen del día más accionable; `PlanEngine` replanificación; búsqueda universal; múltiples marcadores temporales en una frase.

## Ciclo 64 (cont.) - 2026-08-13 (UTC) - fix(parser): `monthNameMatch.find()` casaba mes inválido y ocultaba fecha real (citas futuras → hoy)

- **Run/ciclo**: 64 (cont.) — fix P1 añadido sobre el c.64 aterrizado por el run paralelo; base avanzada a c.65 antes de commit.
- **HEAD inicial**: `6e43206` (c.63); tras reconciliación con c.64 remoto → `890e8b4`; tras nueva reconciliación con c.65 remoto → `970b6c8`.
- **Colisión de remoto (STALE_RUN manejado sin daño, DOS veces)**: (1) al iniciar, `git fetch` mostró `origin/openhands/autonomous-ordia` avanzado `6e43206..890e8b4` (otro run implementó el MISMO feature P2-1 "standalone N de la tarde" en c.64). Mi trabajo local incluía ese P2-1 (redundante) MÁS un fix **P1** único. Resolución: `stash` → `merge --ff-only` al remoto → descarté el P2-1 propio (ya presente) → reapliqué **únicamente** el fix P1. (2) Al preparar el commit, nueva `git fetch` reveló avance `890e8b4..970b6c8` (c.65 "veredicto honesto del día"). Resolución: `stash` → `merge --ff-only` → `stash pop`: código del parser y RUN_LOG auto-mergearon limpiamente (áreas ortogonales); solo `CURRENT_STATE.md` quedó en conflicto (ambos editaban cabecera) → resuelto conservando AMBOS trabajos (cabecera c.65 + nota cont. P1). **Sin force push, sin reset --hard, sin rebase destructivo, sin sobrescribir trabajo válido.**
- **Problema seleccionado**: en `"Taller 9 de la tarde el 15 de agosto"`, `monthNamePattern.find(working)` casaba el **primer** match `"9 de la"` cuyo grupo-mes es `"la"` (inválido); `parseMonthNameDate` retornaba `null` pero `monthNameMatch` **no avanzaba** a examinar el match posterior `"15 de agosto"` (mes válido) → `monthNameDate=null` → la cita se agendaba para **HOY** en lugar del **15 de agosto**. Cita futura explícita perdida como evento de hoy. P1: integridad de agenda/dato perdido (no estética).
- **Prioridad**: P1 (fecha de cita perdida).
- **Causa raíz**: `Regex.find()` devuelve solo el primer match; la validación del mes vivía en `parseMonthNameDate`, que retornaba `null` para un mes inválido, pero el `monthNameMatch` ya fijado no se reevaluaba sobre el siguiente match válido.
- **Solución (mínima)**: `monthNameMatch` pasa de `find(working)` a `findAll(working).firstOrNull { m -> months.any { (name,_) -> m.groupValues[2].equals(name, ignoreCase = true) } }` — descarta matches de mes inválido y selecciona el primero con mes real. `parseMonthNameDate` (que ya validaba) opera ahora sobre un match garantizado válido, sin cambio. No añade pantalla/botón: misma potencia, menos pérdida de datos.
- **Tests**: +2 en `NaturalTaskParserTest.kt` (`nueveDeLaTardeConFechaMesResuelveAmbos` → 2026-08-15 21:00; `nueveDeLaMananaConFechaMesResuelveAmbos` → 2026-09-20 09:00). **502 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 500 c.65 + 2 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión en los 500 previos. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `ed4ead9` (commit + push exitoso al remoto `openhands/autonomous-ordia`; HEAD local == remoto, sin divergencia residual).
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

### Siguiente
- Parser: múltiples marcadores temporales en una frase (auditar interacciones tras c.64 + este fix).
- `SummaryService`/`SummaryEngine`: resumen del día más accionable.
- `PlanEngine` replanificación automática; búsqueda universal.
- Auditoría `EditorDialogs` otros entry points que recalculen `reminderAt` (c.62 cubrió el principal).

## Ciclo 68 - 2026-08-13 (UTC) - fix(parser): "el N del mes que viene" → día N del mes siguiente (fecha errónea + título corrompido)

- **Run/ciclo**: 68 — fix P1 integridad de agenda/dato; continuado desde c.67 (`f7c6137`, refina sugerencia de posposición). El fix fue identificado y prototipado en el run anterior (probe JVM 28 casos); este run lo aterriza con tests unitarios + memoria + commit.
- **HEAD inicial**: `f7c6137` (c.67, HEAD remoto sincronizado tras `git fetch`/`stash`/`merge --ff-only`/`stash pop`). Durante el pop, el código del parser y RUN_LOG/BACKLOG auto-mergearon limpiamente (áreas ortogonales); solo `CURRENT_STATE.md` quedó en conflicto (ambos runs editaban cabecera/tabla) → resuelto conservando AMBOS trabajos y renumerando este fix a c.68 (c.66 y c.67 ya reclamados por runs paralelos de SummaryEngine). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Problema seleccionado**: la forma cotidiana **"Llamar al banco el 15 del mes que viene"** se agendaba al día **equivocado** (hoy+30d en vez del día N del mes siguiente) Y el título quedaba corrompido (**"Llamar al banco del"** — "el 15" consumido por `dayOfMonthPattern`, "del" huérfano). Un compromiso mensual anclado a un día (vencimiento, cobro, factura, cita) caía en fecha genérica y perdía el día explícito. P1: cita/factura agendada en día erróneo + título basura.
- **Prioridad**: P1 (integridad de agenda/dato — fecha incorrecta de un compromiso mensual).
- **Causa raíz**: `nextPeriodPattern` casaba "mes que viene" y lo reemplazaba por espacio, dejando "el 15 del " en `working`. `nextPeriodDueAt` (+30d) entraba en la cadena `effectiveRelativeDueAt` y ganaba sobre `dayOfMonthDate` (día 15, calculado pero sombreado). En el cleanup del título, `dayOfMonthPattern.replace` borraba "el 15" pero no "del", dejándolo huérfano.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nuevo `nextMonthDayPattern` (`\bel\s+(\d{1,2})\s+(?:del?\s+)?(?:mes\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b`) procesado **ANTES** de `nextPeriodPattern` para consumir día + cualificador de "mes siguiente" en UNA frase (evita que `nextPeriodPattern` robe "mes que viene" y deje residuo). `nextMonthDayDueAt` = día N de `base.toLocalDate().plusMonths(1)` con clamp al último día válido si el día no existe en el mes destino (p. ej. 31 de febrero → 28/29); resuelto como día (epoch medianoche) para combinarse con hora explícita ("el 15 del mes que viene a las 10" → 15 del mes siguiente 10:00). Añadido a la cadena `effectiveRelativeDueAt` **antes** de `nextPeriodDueAt` y a `relativeIsDays`.
- **Tests**: +6 en `NaturalTaskParserTest.kt`: `elNDelMesQueVieneResuelveDiaNDelMesSiguiente` (15/08), `elNDelProximoMesResuelveDiaNDelMesSiguiente` (10/08), `elNDelMesProximoResuelveDiaNDelMesSiguiente` (10/08), `elNDelMesQueVieneRespetaHoraExplicita` (05/08 09:00), `elNDelMesQueVieneRespetaDia31CuandoMesTiene31` (31/08), `elMesQueVieneSinDiaSigueSiendoMas30Dias` (no-regresión +30d). **516 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 513 c.67 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM confirmó antes/después en 28 casos sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit+push).
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

### Siguiente
- Parser: múltiples marcadores temporales en una frase (auditar interacciones acumuladas tras c.58–c.68).
- "la semana que viene el lunes" / "el mes que viene el día 5" (combinaciones periodo+día).
- `SummaryService`/`SummaryEngine`: resumen del día más accionable; `PlanEngine` replanificación; búsqueda universal.
- Auditoría progresiva: rutinas adaptables, detección de compromisos en notas, captura ultrarrápida.

## Ciclo 69 - 2026-08-13 (UTC) - feat(ux): un toque pospone a mañana la tarea sugerida (resumen accionable)

- **Run/ciclo**: 69 (rama `openhands/autonomous-ordia`). Continúa directamente el "Siguiente" documentado en c.66: "Sugerencia accionable: permitir posponer la tarea sugerida con UN toque (acción rápida desde la propia línea) — evaluar si aporta fricción cero o más complejidad visible." Decisión: aporta fricción cero SIN nueva pantalla/botón (misma línea existente hecha tappable), así que se implementa.
- **STALE_BASE detectado y reconciliado (no destructivo)**: el HEAD inicial local era `f7c6137` (c.67) pero `git fetch` reveló `origin/openhands/autonomous-ordia` avanzado a `f31388a` (c.68 fix parser "el N del mes que viene"). Resolución: `git stash -u` del trabajo local → `git merge --ff-only origin/openhands/autonomous-ordia` → `git stash pop`. Los cambios de **código** se auto-mergearon limpiamente (áreas ortogonales: c.68 toca `NaturalTaskParser.kt`/`NaturalTaskParserTest.kt`; este run toca `SummaryEngine`/`TaskRules`/`ViewModel`/`TodayScreen`/`strings`/`TaskRulesTest`); sin conflicto. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `f7c6137` (local, 1 detrás del remoto real `f31388a`).
- **Problema seleccionado**: la sugerencia de posposición (c.66 "No cabe todo hoy. Una opción es dejar para mañana «…».", refinada c.67 para no nombrar en-curso/inminentes) era **pasiva**: Ordía ya había decidido qué tarea mover, pero el usuario debía abrirla en el editor → cambiar fecha → guardar (3 pasos) justo en el momento de sobrecarga. La decisión ya estaba tomada por la app; faltaba ejecutarla con fricción cero. Área de dirección explícita "replanificación automática"/"acciones rápidas".
- **Prioridad**: P2 (UX/inteligencia; reduce pasos, convierte varias acciones en una; no pérdida de datos).
- **Causa raíz**: `DeferralSuggestion` solo tenía `taskId`+`title` (información para mostrar, no para actuar); no existía una acción directa de "posponer a mañana" que preservara la integridad temporal (offset de recordatorio, distancia inicio→vencimiento) — el único camino era el editor manual.
- **Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `DeferralSuggestion` gana `canDefer: Boolean` = `chosen.dueAt != null`. Solo es accionable si la tarea tiene vencimiento real: sin vencimiento, "mañana" no está definido y añadirlo cambiaría la semántica de la tarea (false → texto pasivo).
  - `TaskRules.deferToNextDay(task, now, zone)` — nueva **regla pura** (testable sin Android): traslada `dueAt` a **mañana a la misma hora local** vía `Instant.atZone(zone).plusDays(1)` (correcto frente a cambios horarios/DST, no `+24h` a ciegas). Desplaza `startAt` y `reminderAt` por el MISMO delta, conservando la distancia inicio→vencimiento y el offset exacto del recordatorio —crítico para recurrentes, donde `RecurrenceEngine.nextOccurrence` reutiliza `dueAt - reminderAt` en cada ocurrencia futura—. `recurrence`/`recurrenceInterval`/`recurrenceDays` intactos: se pospone ESTA instancia, no la cadencia. Devuelve `null` si `dueAt == null`. No muta la entrada (`copy`).
  - `OrdiaViewModel.deferTaskToTomorrow(taskId)`: carga la tarea, aplica `deferToNextDay`, reusa `saveTask(deferred)` —que ya reagenda el recordatorio en el nuevo vencimiento (c.62 `ReminderRules` + worker)— sin duplicar lógica de agenda.
  - `TodayScreen.kt`: la línea OVERLOADED existente es ahora **tappable** cuando `suggestion.canDefer`, con texto "Toca para mover «…» a mañana." (nuevo string `summary_load_overloaded_actionable`); si `!canDefer`, conserva el texto pasivo `summary_load_overloaded_suggestion`. Misma línea `bodySmall`, sin tarjeta ni botón nuevos — la sugerencia pasa de pasiva a accionable en su propio sitio.
  - Heurística determinista, sin random ni "IA". Reusa infraestructura existente.
- **Tests**: +6 en `TaskRulesTest.kt`: `deferToNextDay_returnsNullWithoutDueAt`; `_movesDueToTomorrowSameTime` (2026-08-14 18:30); `_preservesReminderOffset` (sigue 30 min antes, mismo día+hora relativa); `_shiftsStartBySameDelta` (gap 1 h inicio→vencimiento conservado); `_doesNotMutateOriginal` (inmutabilidad); `_keepsNullFieldsNull` (startAt/reminderAt null siguen null). **522 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 516 c.67/c.68 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión (516 previos intactos tras rebase sobre c.68).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); render real del tap en la tarjeta de Today en dispositivo; `deferTaskToTomorrow` (ViewModel, requiere Android); `saveTask` reagenda recordatorios verificado solo por lectura de código (c.62).
- **Hallazgos adicionales (descubrimiento continuo)**: `deferToNextDay` es una primitiva reutilizable — futuras superficies (widget, notificación de sobrecarga) podrían ofrecer el mismo "posponer a mañana" con un toque sin duplicar lógica. `RecurrenceEngine.nextOccurrence` (fin de mes mensual → 31/feb, año bisiesto) sigue pendiente de auditoría. Áreas a auditar: búsqueda universal; `PlanEngine` replanificación más amplia; rutinas adaptables; detección de compromisos en notas; captura ultrarrápida.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt`, `app/src/main/java/com/ordia/app/domain/TaskRules.kt`, `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`, `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt`, `app/src/main/res/values/strings_screens1.xml`, `app/src/test/java/com/ordia/app/domain/TaskRulesTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `e0f61bb` (commit+push OK a `origin/openhands/autonomous-ordia`, fast-forward sobre base reconciliada `f31388a` c.68; remoto verificado == local).
- **Estado**: FIXED → VERIFIED (dominio JVM); UI en app / `deferTaskToTomorrow` NO VERIFICADO (sin Android SDK).

### Siguiente
- Parser: múltiples marcadores temporales en una frase (auditar interacciones acumuladas tras c.58–c.68).
- "la semana que viene el lunes" / "el mes que viene el día 5" (combinaciones periodo+día).
- `RecurrenceEngine.nextOccurrence` auditoría: fin de mes mensual → 31/feb, año bisiesto.
- `PlanEngine`/replanificación más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: búsqueda universal; rutinas adaptables; detección de compromisos en notas; captura ultrarrápida.

## Ciclo 70 - 2026-08-13 (UTC) - fix(parser): P0 crash día fuera de rango + P1 "del 2027" no capturaba el año (fecha 2026↔2027)

- **Run/ciclo**: 70 (renumerado desde c.69 tras colisión con run paralelo `e0f61bb` que tomó c.69). Fix P0 crash + fix P1 integridad de fecha/año; continuación directa del c.68 (`f31388a`, "el N del mes que viene"). Ambos bugs en la misma área (fecha con mes nombrado) hallados por sondeo proactivo del `NaturalTaskParser`.
- **STALE_BASE detectado y reconciliado (no destructivo)**: HEAD inicial local era `f31388a` (c.68); al hacer push, `origin/openhands/autonomous-ordia` había avanzado a `9b801a8` (c.69 feat resumen accionable `e0f61bb` + docs `9b801a8` de un run paralelo). Resolución: `git fetch origin openhands/autonomous-ordia` → `git rebase origin/openhands/autonomous-ordia` (rebasa mi único commit sobre el remoto, sin sobrescribir trabajo válido). Conflictos solo en `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md` (ambos runs editaron las mismas cabeceras/tables); resueltos a mano conservando AMBOS runs, renumerando el mío c.69→c.70 y el conteo de tests 522→528. Los cambios de **código** NO chocan (áreas ortogonales: el run paralelo toca `SummaryEngine`/`TaskRules`/`ViewModel`/`TodayScreen`; este run toca `NaturalTaskParser.kt`/`NaturalTaskParserTest.kt`). Sin force push, sin reset --hard.
- **HEAD inicial**: `f31388a` (c.68, sincronizado antes de la divergencia).
- **Problema seleccionado (dos, misma área)**:
  1. **P0 — crash**: `parseMonthNameDate` lanzaba `DateTimeException` no capturada ante día fuera de rango en fecha con mes nombrado ("el 0 de septiembre", "el 99 de enero", "el 00 de marzo", "el 32 de septiembre") → **crash de la app** ante texto libre. El `dayOfMonthPattern` suelto ya validaba `day in 1..31` (c.47), pero la rama de mes nombrado no.
  2. **P1 — año no capturado**: `monthNamePattern` solo aceptaba `\s+de\s+(\d{2,4})` antes del año; el español estándar usa **"del"** ("el 15 de agosto del 2027"). El año no se capturaba → (1) **fecha errónea**: "el 15 de agosto del 2027 a las 10" se agendaba para **2026** en vez de **2027** (caía al año por defecto); (2) **título corrompido**: "del 2027" huérfano.
- **Prioridad**: P0 (crash ante input de texto libre) + P1 (compromiso anual en año erróneo + título basura).
- **Causa raíz**: (P0) ausencia de validación de rango en `parseMonthNameDate` antes de `LocalDate.of`; (P1) regex del año no contemplaba la contracción "del".
- **Solución (mínima, dos cambios de 1 línea en `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  - P0: `val day = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null` — día inválido → `dueAt=null` y la frase queda como título, sin caer.
  - P1: `monthNamePattern` año `(?:\s+de\s+(\d{2,4}))?` → `(?:\s+del?\s+(\d{2,4}))?` (acepta "de" Y "del"; el mismo patrón se usa en el cleanup del título → consume "del 2027" completo sin residuo; cubre 2 dígitos "del 26").
- **Tests**: +6 en `NaturalTaskParserTest.kt` (P0: `diaCeroDeMesNoCrashYDejaSinFecha`, `diaNoventaYNueveDeMesNoCrashYDejaSinFecha`, `diaCeroCeroDeMesNoCrashYDejaSinFecha`; P1: `mesNombreConDelAnioAgendaAnioCorrecto` → 2027-08-15 10:00, `mesNombreConDelAnioNoDejaResiduoEnTitulo`, `mesNombreConDelAnioDosDigitosAgendaCorrecto`). **528 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 522 c.69 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM confirmó antes/después en todos los casos sin regresión (incl. "de 2027" original, "recordarme… del 2027", "renovar suscripción el 1 de enero del 2027", "el 31 de abril"→30 abr clamp, "el 29 de febrero de 2028"→bisiesto). **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `59c0cb6` (rebase sobre `9b801a8` + commit+push OK a `origin/openhands/autonomous-ordia`, fast-forward; remoto verificado == local).
- **Estado**: FIXED → VERIFIED (dominio JVM, 528 tests); parser en app NO VERIFICADO (sin Android SDK).

## Ciclo 71 — 2026-08-13

- **Run/ciclo**: 71 (rama `openhands/autonomous-ordia`). Continúa el "Siguiente" del c.69 (combinaciones periodo+día en el parser natural). Originalmente numerado c.70, pero un run paralelo legítimo reclamó c.70 (P0 crash `parseMonthNameDate` + P1 "del 2027"). Este run se renumera a c.71; ambos trabajos son ortogonales (c.70 toca `parseMonthNameDate`/`monthNamePattern`; este toca `nextMonthDayReversePattern`/`effectiveRelativeDueAt`) y se conservan.
- **STALE_BASE detectado y reconciliado (no destructivo)**: HEAD inicial local era `9b801a8` (c.69); al hacer push, `origin/openhands/autonomous-ordia` había avanzado a `64c137a` (c.70 del run paralelo: `59c0cb6` fix P0/P1 + `64c137a` docs). Resolución: `git fetch origin openhands/autonomous-ordia` → `git rebase origin/openhands/autonomous-ordia` (rebasa mi único commit `09b1567` sobre el remoto, sin sobrescribir trabajo válido). Los cambios de **código** se auto-mergearon limpiamente (áreas ortogonales); conflictos SOLO en `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md` (ambos runs editaron las mismas cabeceras/tablas), resueltos a mano conservando AMBOS runs. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **HEAD inicial**: `9b801a8` (c.69).
- **Problema seleccionado**: el c.68 (commit `f31388a`) corrigió la forma DIRECTA "el N del mes que viene" (día ANTES del periodo). Pero la forma INVERSA igualmente cotidiana —"el mes que viene el 5" / "el mes que viene el día 5" / "el próximo mes el 10" / "el mes próximo el 20"— seguía rota: `nextPeriodPattern` robaba "el mes que viene" como +30d genérico e **ignoraba el día explícito**, produciendo fecha errónea (p. ej. 12/09 en vez del 05/09) y, en la variante "el día N", dejando "el día 5" como residuo en el título. Para un vencimiento mensual (tarjeta, alquiler, cobro) eso significa un recordatorio que se dispara **una semana tarde** — compromiso olvidado. Área P1 (recordatorios/fechado correcto).
- **Prioridad**: P1 (persistencia/fechado/recordatorios; causa vencimientos mal fechados = compromisos olvidados).
- **Causa raíz**: `nextMonthDayPattern` (c.68) solo casaba `el N del mes que viene` (día + "del" + periodo). No existía un patrón para el orden inverso (periodo + día), de modo que `nextPeriodPattern` procesaba primero "el mes que viene" y sombreaba la fecha específica del día en `effectiveRelativeDueAt` (la cadena da prioridad al periodo sobre `effectiveDate`).
- **Solución (mínima, simétrica al c.68 — "menos interfaz, más potencia")**:
  - Nuevo `nextMonthDayReversePattern` regex: `\b(?:el\s+)?(?:mes\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\s+el\s+(?:d[ií]a\s+)?(\d{1,2})\b` — captura periodo + "el" + día (con o sin "día"), en el orden inverso. Reutiliza los mismos cualificadores de periodo que `nextMonthDayPattern` y `nextPeriodPattern`.
  - Resolución **idéntica** a `nextMonthDayDueAt` (c.68): día N de `plusMonths(1)`, clamp al último día válido del mes destino (p. ej. "el 31" → 30 de septiembre). Sin nueva lógica de fechas.
  - Se procesa **ANTES** que `nextPeriodPattern` (junto a `nextMonthDayMatch`) para consumir la frase completa (periodo+día) en un solo match y evitar que `nextPeriodPattern` la robe como +30d.
  - Integrado en la cadena `effectiveRelativeDueAt` (entre `nextMonthDayDueAt` y `nextPeriodDueAt`) y en `relativeIsDays` (combinable con hora explícita: "el mes que viene el 5 a las 10" → 05/09 10:00).
  - No-regresión: el patrón **exige** un día tras el periodo (`\s+el\s+(?:d[ií]a\s+)?(\d{1,2})`), así "el mes que viene" sin día sigue siendo +30d (test `elMesQueVieneSinDiaSigueSiendoMas30Dias` intacto).
- **Tests**: +6 en `NaturalTaskParserTest.kt`: `elMesQueVieneElNResuelveDiaNDelMesSiguiente` (15→15/08), `elMesQueVieneElDiaNResuelveDiaNDelMesSiguiente` (5→05/08), `elProximoMesElNResuelveDiaNDelMesSiguiente` (10→10/08), `elMesProximoElNResuelveDiaNDelMesSiguiente` (20→20/08), `elMesQueVieneElNRespetaHoraExplicita` (5→05/08 09:00), `elMesQueVieneElNClampDia31CuandoMesTiene30` (31→31/08). **534 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 528 c.70 + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión (528 previos intactos tras rebase sobre c.70).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). La combinación **semana+weekday** ("la semana que viene el lunes/viernes") sigue ABIERTA (ver hallazgo): necesita lógica distinta ("lunes de la semana que viene" ≠ `nextWeekday` para días ya pasados de esta semana) y se difiere por riesgo.
- **Hallazgos adicionales (descubrimiento continuo)**:
  - **Semana+weekday (ABIERTO)**: "la semana que viene el lunes" → +7d (20/08) en vez del lunes de la semana que viene (17/08); "la semana que viene el viernes" → +7d pero `nextWeekday` daría mañana (14/08). No basta con reusar `nextWeekday`: se necesita "start-of-next-week + weekday objetivo" (lun→dom). Más complejo y riesgoso; registrado en BACKLOG para un ciclo dedicado.
  - Caso sin palabra-tarea ("el mes que viene el 5" a secas): el `dueAt` ahora es correcto (05/09) pero el título conserva la frase completa — comportamiento preexistente del guard de título-vacío (no regresión; una tarea sin título es irreal). No se persigue.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `5e27222` (rebase sobre `64c137a` c.70 del run paralelo + amend mensaje a c.71; push fast-forward OK a `origin/openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM).

### Siguiente
- Semana+weekday: "la semana que viene el lunes/viernes" → lunes/viernes de la semana próxima (helper start-of-next-week + weekday; ciclo dedicado).
- `RecurrenceEngine.nextOccurrence` auditoría: fin de mes mensual → 31/feb, año bisiesto.
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: búsqueda universal; rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; onboarding.


## Ciclo 72 - 2026-08-13 (UTC) - fix(parser): "jueves que viene" dicho en jueves → próxima semana (no HOY)

- **Run/ciclo**: 72 (renumerado desde c.70 tras colision con run paralelo `59c0cb6` que tomo c.70). Fix P1 integridad de agenda (día objetivo futuro agendado en el día equivocado). El bug fue identificado en el run anterior (probe JVM 28 casos); este run lo aterriza con tests unitarios + memoria + commit.
- **STALE_BASE detectado y reconciliado (no destructivo)**: mi commit se baso en `9b801a8` (c.69); al hacer push, `origin/openhands/autonomous-ordia` habia avanzado a `59c0cb6` (c.70, run paralelo P0/P1 parser mes-nombrado). Resolucion: `git fetch` -> `git rebase origin/openhands/autonomous-ordia` (rebasa mi unico commit sobre el remoto, sin sobrescribir trabajo valido). Conflicto SOLO en `NaturalTaskParserTest.kt` (ambos anadimos tests al final) y en `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`; resuelto conservando AMBOS conjuntos y renumerando c.70->c.71. Los cambios de **codigo** NO chocan (areas ortogonales: c.70 toca `monthNamePattern`/`parseMonthNameDate`; este run toca la rama `weekdayMatch`). Sin force push, sin reset --hard.
- **HEAD inicial**: `9b801a8` (c.69, HEAD remoto sincronizado tras `git fetch`: el remoto había avanzado `f31388a..9b801a8` durante el run). Reconciliación segura: `stash` → `merge --ff-only origin/openhands/autonomous-ordia` → `stash pop` limpio (c.69 tocó `SummaryEngine`/`TaskRules`/`TodayScreen`/`strings`, áreas ortogonales al `weekdayMatch` del parser y a `NaturalTaskParserTest`). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.
- **Problema seleccionado**: la forma cotidiana **"reunión el jueves que viene"** escrita un **jueves** caía en **HOY** en vez de la próxima semana. Mismo defecto con "el próximo jueves", "lunes próximos", "sábado que viene", etc., cuando hoy ya era ese día de la semana. Un compromiso explícitamente futuro se agendaba en el día equivocado. P1: integridad de agenda (fecha incorrecta).
- **Prioridad**: P1 (fecha de cita errónea — el usuario dice "que viene" y Ordía lo agenda para hoy).
- **Causa raíz**: `weekdayPattern` capturaba el sufijo "que viene"/"próximo" como grupo **no capturador** `(?:...)`, así el código de resolución nunca distinguía "el próximo jueves" del "jueves" suelto. Cuando `today.dayOfWeek == target`, `weekdaySameDayCandidate = true` y `nextWeekdayOrSame()` (que devuelve hoy si delta=0) dejaban la fecha en hoy, ignorando el modificador "que viene".
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: en la rama `weekdayMatch`, `nextExplicit = mv.contains("que viene") || mv.contains("próxim")` detecta el modificador de "próxima ocurrencia" directamente en `match.value` (cubre sufijo "que viene"/"próximos" y prefijo "próximo jueves" — ambos dentro del match). Con `nextExplicit`: `weekdaySameDayCandidate = false` y se fuerza `nextWeekday(base.toLocalDate(), target)` (estricto, +7 siempre). Sin modificador, el día suelto ("el jueves a las 18" dicho en jueves con hora futura) sigue pudiendo ser hoy — **no-regresión** del comportamiento de c.42 cont.2. Mismas regex, solo cambia la rama de resolución.
- **Tests**: +7 en `NaturalTaskParserTest.kt` con `now` = 2026-08-13 (jueves): `juevesQueVieneDichoEnJuevesAvanzaUnaSemana` (→2026-08-20), `juevesQueVieneConHoraAvanzaUnaSemana` (→2026-08-20 18:00), `proximoJuevesDichoEnJuevesAvanzaUnaSemana` (→2026-08-20), `juevesSueltoDichoEnJuevesPuedeSerHoySiHoraFutura` (no-regresión, "el jueves a las 18"→hoy 18:00), `viernesQueVieneEsManana`, `martesQueVieneEsLaProximaSemana`, `lunesProximosAvanzaUnaSemana`. **541 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 534 c.71 + 7). Rebase con run paralelo c.70 (P0/P1 parser mes-nombrado) sin conflicto de codigo; conflictos solo en tests/docs resueltos conservando ambos conjuntos., smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM (28 casos) confirmó antes/después sin regresión en "el jueves a las 18" (hoy), "viernes que viene" (mañana), etc. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG,BACKLOG}.md`.
- **HEAD final**: `32f072d` (rebase sobre `aa0407f` remoto c.71; conflicto de docs resuelto conservando ambos runs, c.71 remoto + este c.72; push fast-forward OK a `origin/openhands/autonomous-ordia`: `aa0407f..32f072d`).
- **Estado**: FIXED → VERIFIED (dominio JVM); parser en app NO VERIFICADO (sin Android SDK).

### Siguiente
- "la semana que viene el lunes" / "el mes que viene el día 5" (combinaciones periodo+día).
- `RecurrenceEngine.nextOccurrence` auditoría: fin de mes mensual → 31/feb, año bisiesto.
- `SummaryService`/`SummaryEngine`: resumen del día más accionable; `PlanEngine` replanificación; búsqueda universal.
- Auditoría progresiva: rutinas adaptables, detección de compromisos en notas, captura ultrarrápida.

---

## Ciclo 73 — Parser — "la semana que viene el lunes/viernes" → día objetivo de la semana próxima

- **Fecha (UTC)**: 2026-08-13.
- **Run/ciclo**: 73 (rama `openhands/autonomous-ordia`). Continúa el "Siguiente" del c.71: combinación semana+weekday en el parser natural (abierto desde el hallazgo c.70). Base reconciliada con `origin/openhands/autonomous-ordia` (HEAD `a62cf1f`, c.72 paralelo ya en remoto); rebase no destructivo, código auto-mezclado (áreas ortogonales: c.72 toca rama `weekdayMatch`/`nextExplicit`; este run añade patrones `nextWeekWeekday*`), conflictos solo en memoria resueltos conservando ambos runs.
- **HEAD inicial**: `aa0407f` (c.71 docs runlog); remoto avanzó a `a62cf1f` (c.72 paralelo) durante el run.
- **Problema seleccionado**: "la semana que viene el lunes" / "la semana que viene el viernes" / "el lunes de la semana que viene" agendaban **+7d genérico** (lo que da `nextPeriodPattern` para "la semana que viene") e **ignoraban el día de la semana explícito** → una cita/reunión quedaba en el día equivocado (p. ej. "la semana que viene el viernes" dicho un miércoles caía en el próximo miércoles, no en el viernes de la semana que viene). Para un evento con fecha inequívoca en lenguaje natural eso es un compromiso mal agendado (recordatorio en día erróneo = fallo de cita). Área P2 de parser/integridad de agenda.
- **Prioridad**: P2 (parser; agendado en día equivocado). Corregido por ser una mejora funcional real de producto (el usuario dice un día concreto y Ordía lo respeta) sin añadir interfaz.
- **Causa raíz**: `nextPeriodPattern` casaba "la semana que viene" (+7d) y `weekdayPattern` casaba "lunes"/"viernes" por separado; en la cadena `effectiveRelativeDueAt` el `nextPeriodDueAt` (+7d) tenía prioridad sobre la fecha suelta del `weekdayMatch` (`date` solo aplica cuando `effectiveRelativeDueAt == null`), de modo que el día explícito quedaba sombreado. El helper `nextWeekday` no servía para resolver el caso porque para "la semana que viene el viernes" daría el próximo viernes relativo a hoy (que puede ser **esta** semana), no el viernes de la **semana próxima**.
- **Solución (mínima, simétrica al c.68/c.71 — "menos interfaz, más potencia")**:
  - Nuevo `nextWeekWeekdayReversePattern` regex: `\b(?:la\s+)?(?:semana\s+(?:que\s+viene|pr[oó]xima)|pr[oó]xima\s+semana)\s+el\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b` — periodo "semana próxima" + "el" + weekday, orden periodo→día.
  - Nuevo `nextWeekWeekdayForwardPattern` regex (orden inverso, día→periodo): `\bel\s+(weekday)\s+de\s+(?:la\s+)?(?:semana\s+(?:que\s+viene|pr[oó]xima)|pr[oó]xima\s+semana)\b` — "el lunes de la semana que viene".
  - Helper `nextWeekWeekdayDate(today, target, zone)`: ancla al **próximo lunes estricto** (`TemporalAdjusters.next(DayOfWeek.MONDAY)`, excluye la semana actual) y suma el offset del weekday objetivo (`target.value - MONDAY.value`, lun=0 … dom=6). Resultado: el día objetivo de la semana próxima (lun→dom). "la semana que viene el lunes" dicho un lunes → lunes de la semana siguiente (no hoy), consistente con "semana que viene" = semana no actual.
  - Ambos patrones se procesan **ANTES** que `nextPeriodPattern` (junto a `nextMonthDayReverseMatch`) para consumir la frase completa (periodo+día) en un solo match y evitar que `nextPeriodPattern` robe "la semana que viene" como +7d.
  - Integrados en la cadena `effectiveRelativeDueAt` (antes de `nextPeriodDueAt`) y en `relativeIsDays` (combinables con hora explícita: "la semana que viene el viernes a las 18" → viernes de la semana próxima 18:00).
  - No-regresión: el patrón **exige** un weekday tras el periodo, así "la semana que viene" sin día sigue siendo +7d (test `laSemanaQueVieneSinDiaSigueSiendoMasSieteDias` intacto).
- **Tests**: +8 en `NaturalTaskParserTest.kt` (base 2026-07-29 miércoles; próximo lunes = 2026-08-03): `laSemanaQueVieneElLunesResuelveLunesDeLaSemanaProxima` (→03/08), `laSemanaQueVieneElViernesResuelveViernesDeLaSemanaProxima` (→07/08), `laSemanaQueVieneElDomingoResuelveDomingoDeLaSemanaProxima` (→09/08), `laProximaSemanaElMiercolesResuelveMiercolesDeLaSemanaProxima` (→05/08), `elLunesDeLaSemanaQueVieneResuelveLunesDeLaSemanaProxima` (orden inverso →03/08), `elViernesDeLaProximaSemanaResuelveViernesDeLaSemanaProxima` (→07/08), `laSemanaQueVieneElViernesRespetaHoraExplicita` (→07/08 18:00), `laSemanaQueVieneSinDiaSigueSiendoMasSieteDias` (no-regresión →05/08). **549 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 541 c.72 + 8 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión (541 previos intactos).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**:
  - La misma familia periodo+weekday podría extenderse a "el mes que viene el tercer lunes" (ordinal + weekday del mes) pero es una forma mucho menos frecuente y de complejidad mayor; se deja fuera de alcance por "menos es más".
  - "fin de semana que viene" (c.31) sigue intacto: `weekendEarlyMatch` consume "fin de semana que viene" antes que cualquier patrón de semana+weekday, sin colisión.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `c81f6c8` (rebase no destructivo sobre `a62cf1f` remoto c.72; código auto-mezclado ortogonal; conflictos de memoria resueltos conservando ambos runs; push fast-forward verificado a `origin/openhands/autonomous-ordia`: `a62cf1f..c81f6c8`).
- **Estado**: FIXED → VERIFIED (dominio JVM).

### Siguiente
- `RecurrenceEngine.nextOccurrence` auditoría: fin de mes mensual → 31/feb, año bisiesto.
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: búsqueda universal; rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; onboarding.


## Ciclo 75 — Parser — límites mensuales con "mes que viene"/"próximo" + "antepasado mañana"

- **Fecha (UTC)**: 2026-08-13.
- **Run/ciclo**: 75 (rama `openhands/autonomous-ordia`). Continúa la auditoría del parser natural del c.73. Base inicial `f31388a` (c.73); durante el run el remoto avanzó a `268b635` (c.74 paralelo: RecurrenceEngine anual 29/feb). Se detectó base obsoleta al hacer `git fetch` antes de commitear; reconciliación **no destructiva**: `git stash` + `git pull --ff-only` a `268b635` + `git stash pop`. Conflictos solo en `AI_AUTONOMY/{BACKLOG,CURRENT_STATE}.md` (nomenclatura de ciclo colisionaba: el run paralelo también usó "ciclo 74"); resueltos conservando AMBOS runs y renombrando este a **ciclo 75**. Código de dominio ortogonal (c.74 toca `RecurrenceEngine.kt`; este run toca `NaturalTaskParser.kt`) — sin conflicto de código. (NOTA: durante el run se renombró internamente de "ciclo 74" a "ciclo 75"; el body del commit y la documentación reflejan 75.)
- **HEAD inicial**: `f31388a` (c.73); base reconciliada a `268b635` (c.74 remoto) antes de commitear.
- **Problema seleccionado**: auditoría del parser (probe JVM sobre el dominio) reveló DOS fallos P1 de integridad de dato/agenda:
  - **Fix A — límites mensuales ignoraban el modificador "mes que viene"/"próximo"**: **"reunión fin del mes que viene"** agendaba fin del mes **ACTUAL** (31/08) en vez de fin del mes **SIGUIENTE** (30/09). Igual con "mediados del mes que viene" (→15/08 en vez de 15/09) y "principios del mes que viene". Causa raíz: `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` terminaban en `mes` y **no capturaban** el calificador "que viene"/"próximo"; la fecha se resolvía siempre sobre el mes base de hoy (con su roll original). Un vencimiento mensual explícitamente futuro (pago/renta/cierre del mes próximo) caía un mes ANTES → compromiso adelantado/olvidado, recordatorio en el mes erróneo.
  - **Fix B — "antepasado mañana" mal fechado + título corrupto**: **"reunión antepasado mañana"** agendaba **mañana** (+1 = 14/08) en vez de +3 (16/08) y dejaba **"antepasado"** como residuo en el título ("reunión antepasado"). Causa raíz: la palabra "mañana" dentro de la frase casaba con el token de fecha suelto `mananaAsDate` → +1, y "antepasado" no estaba en la regex de limpieza del título. Cita programada 2 días antes de lo pedido + título sucio.
- **Prioridad**: P1 (ambos: fecha de vencimiento/compromiso mal calculada → recordatorio/agenda erróneos, título corrupto).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - **Fix A**: los tres patrones (`endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern`) ahora capturan un modificador opcional `(?:\s+(?:que\s+viene|pr[oó]xim[oa]s?))?` tras `mes`. El bloque de resolución usa el helper `monthBaseForBoundary(today, matched)` que: **con modificador** → ancla al mes siguiente (`today.plusMonths(1)`) **sin roll adicional** (evita el doble-desplazamiento simétrico: "principios del mes que viene" dicho a medidados de agosto = 01/09, NO 01/10); **sin modificador** → replica el roll original (fin de mes rueda solo si hoy es último día; mediados solo si hoy≥15; principios rueda al 1 del mes siguiente salvo hoy=1). El día lo fija el `kind` (end=lengthOfMonth del mes destino, mid=15, start=1). Combina con hora explícita.
  - **Fix B**: rama `\bantepasad[oa]\s+mañana\b` → `plusDays(3)` añadida **ANTES** de "pasado mañana" y de "mañana" suelto en la cadena `effectiveRelativeDueAt`; y `antepasad[oa]\s+mañana` añadido a la regex de limpieza del título (antes que `pasado mañana`/`mañana`). Así la frase completa se consume (fecha +3, título limpio).
  - Lógica local honesta (aritmética de `LocalDate`, sin random ni modelo simulado). Retrocompatible (sin cambios de firma pública).
- **Tests**: +10 en `NaturalTaskParserTest.kt` (base `now`=2026-07-29 → "mes que viene"=agosto):
  - Fix A (8): `finDelMesQueVieneAnclaFinMesSiguiente` (→31/08), `finDelMesProximoAnclaFinMesSiguiente` (→31/08), `finDeMesQueVieneSinDelAnclaFinMesSiguiente` ("fin de mes que viene"→31/08), `finDelMesQueVieneRespetaHoraExplicita` (→31/08 18:00), `aFinalesDelMesQueVieneAnclaFinMesSiguiente` (→31/08), `mediadosDelMesQueVieneAnclaDia15MesSiguiente` (→15/08), `principiosDelMesQueVieneAnclaDia1MesSiguiente` (→01/08, anti-doble-desplazamiento), `finDelMesProximoRespetaUltimoDiaMesDestino` (nov→dic 31/12).
  - Fix B (2): `antepasadoMananaResuelveTresDiasYConservaTitulo` (→01/08 título "Cita"), `antepasadoMananaRespetaHoraExplicita` (→01/08 10:00).
  - **563 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 549 c.73 + 4 c.74 + 10 c.75), smoke 25 OK (`bash tools/run_domain_checks.sh`). Sin regresión: los 549 previos intactos (casos sin modificador "fin de mes"/"a principios de mes" siguen con su roll original; "pasado mañana"/"mañana" sueltos intactos; c.74 RecurrenceEngine intacto). Probe JVM verificó además combinación con hora explícita ("fin del mes que viene a las 18", "a finales del mes que viene a las 3 de la tarde") y variantes ("fin de mes que viene" sin "del", "fin del mes próximo").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: la familia "límite + mes que viene" podría extenderse a "fin del mes pasado"/"principios del mes pasado" (fechas vencidas explícitas), pero son mucho menos frecuentes y "el mes pasado" ya se resuelve como fecha pasada; se deja fuera de alcance por "menos es más". "antepasado ayer" (=−3) es aún más raro; no implementado.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `268b635` c.74).
- **Estado**: FIXED → VERIFIED (dominio JVM). 563 domain tests PASS. (Reconciliación no destructiva de base obsoleta: STALE_RUN detectado y resuelto sin pérdida de trabajo.)

### Siguiente
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: búsqueda universal; rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; onboarding; múltiples marcadores temporales en una frase.

## Ciclo 83 — Inteligencia — asistente "¿Qué hago ahora?" explica la razón + plan mínimo con ranking único de What Now

- **Fecha (UTC)**: 2026-08-13.
- **Run/ciclo**: 83 (rama `openhands/autonomous-ordia`). Mejora de inteligencia/coherencia, no de parser. Base inicial `106022e` (c.82, ya en remoto). `git fetch` confirmó local==remoto (`106022e`), sin divergencia.
- **HEAD inicial**: `106022e` (c.82).
- **Problema seleccionado**: el asistente respondía "¿Qué hago ahora?" / "siguiente acción" con `TaskRules.nextBestTask` (time-aware desde c.45) pero **(1) no explicaba por qué** esa tarea y no otra (respuesta plana "Empieza por «…». Estimo N minutos."), **(2) no mencionaba cuántas vencidas** había (el usuario debe ir a mirar la lista), y **(3) el "plan mínimo" usaba un comparador propio** (`priority` desc → `dueAt` asc) que **divergía** del ranking de `WhatNowEngine` (rango temporal → prioridad → dueAt). Inconsistencia real entre las tres superficies que deberían dar la misma respuesta: What Now (TodayScreen) vs asistente vs widget. Con una vencida-normal y una urgente-no-vencida: What Now = vencida (por rango), plan mínimo viejo = urgente (por prioridad). El asistente menos útil que la tarjeta principal de What Now.
- **Prioridad**: P1 (inteligencia/What Now — el asistente es una superficie de decisión clave; dar la razón + contar vencidas ayuda a decidir y a no olvidar).
- **Causa raíz**: ausencia de un punto único de ranking reutilizable. `WhatNowEngine.suggest` tenía el comparador inline; el asistente reimplementaba otro orden para el plan mínimo en vez de delegar.
- **Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
  - `WhatNowEngine.ordered(tasks, now, zone)`: ranking determinista y **público** de todas las candidatas (mismo comparador que tenía `suggest` inline). `suggest` ahora delega en `ordered(...).firstOrNull()` — fuente única de verdad, DRY (se elimina la duplicación del comparador).
  - `WhatNowEngine.reasonLabel(WhatNowReason)`: etiqueta humana y honesta de por qué esa tarea va primero ("ya está en curso"/"está vencida"/"empieza enseguida"/"vence hoy"/"es urgente"/"es prioritaria"/"está programada para más tarde"/"es lo siguiente de la bandeja"). No es IA ni random: es la razón real del ranking local.
  - `AssistantEngine` "¿qué hago ahora?"/"siguiente acción" → delega en `WhatNowEngine.suggest` y responde "Empieza por «…»: <razón>. Estimo N minutos." +, si hay vencidas, "Además, tienes N vencid(a/as)." (concuerda número).
  - `AssistantEngine` "plan mínimo" → `WhatNowEngine.ordered(...).take(3)` (mismo orden que What Now/widget). Reemplaza el comparador divergente `priority`→`dueAt`.
  - Retrocompatible (sin cambios de firma pública; `suggest` sigue devolviendo `WhatNowSuggestion?`).
- **Tests**: +2 en `AssistantEngineTest.kt` (`whatNow_explainsWhyAndMentionsOverdue`: vencida dueAt=1 → relatedTaskIds=[1], respuesta contiene "vencida" y "1 vencida"; `planMinimo_ranksOverdueFirst`: vencida + normal-alta → orden [2,1]). La existente `whatNow_usesRealPriority` sigue verde. **602 domain tests PASS** (`bash tools/run_domain_tests.sh`, 27 clases); **5 AssistantEngineTest PASS** (compiladas/ejecutadas con `kotlinc` aparte — el script de dominio no incluye `assistant/` —, classpath con las jars de `/tmp/libs`); **smoke 25 OK** (`bash tools/run_domain_checks.sh`). Sin regresión: el refactor de `suggest` (delegar en `ordered`) preserva el orden exacto (mismo comparador).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); render real del asistente en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: `WhatNowEngine.reasonLabel` queda disponible para reusar desde la UI de What Now (`TodayScreen`) si se quiere mostrar la razón junto a la tarjeta (futuro: evaluar antes de añadir superficie — anti-feature-bloat). El asistente aún no tiene un path para "tareas rápidas" ranking-coherente (`quick` usa `durationMinutes <= 15` sin ranking) — candidata a próxima unidad.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/WhatNowEngine.kt`, `app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt`, `app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `106022e` c.82).
- **Estado**: FIXED → VERIFIED (dominio JVM + assistant JVM).

### Siguiente
- Asistente "tareas rápidas" alineado al ranking de What Now (`WhatNowEngine.ordered` filtrando `durationMinutes <= 15`) en vez de orden de lista.
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- Descubrimiento continuo: rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; onboarding.

## Ciclo 84 — Inteligencia — asistente "tareas rápidas" alineado al ranking de What Now

- **Fecha (UTC)**: 2026-08-13.
- **Run/ciclo**: 84 (rama `openhands/autonomous-ordia`). Continuación natural del c.83 (misma área: coherencia de ranking entre superficies del asistente). Base inicial `623e2bc` (c.83, ya en remoto). `git fetch` confirmó local==remoto, sin divergencia.
- **HEAD inicial**: `623e2bc` (c.83).
- **Problema seleccionado**: el asistente respondía "tareas de 15 minutos"/"rápido" con `active.filter { durationMinutes <= 15 }.take(6)` — es decir, en **orden de lista**, sin aplicar el ranking de What Now. Con una vencida y una normal ambas rápidas, podía listar primero la normal, divergiendo de What Now / widget / "plan mínimo" (todas ya alineadas en c.83 salvo este path). El usuario pedía "¿qué hago rápido?" y recibía un orden menos útil (no priorizaba lo vencido/urgente).
- **Prioridad**: P2 (coherencia/UX — no pérdida de datos, pero mejor decisión automática y menos fricción mental; el ranking ya existía, solo faltaba reusarlo).
- **Causa raíz**: el path "quick" no reutilizaba `WhatNowEngine.ordered` (introducido en c.83 como fuente única de ranking). Era la última superficie del asistente aún sin alinear con la fuente única.
- **Solución (mínima, una línea)**: `AssistantEngine` "tareas de 15 minutos" → `WhatNowEngine.ordered(active, now).filter { it.durationMinutes <= 15 }.take(6)`. Conserva el filtro `<= 15` exacto (default `durationMinutes=25` → solo las realmente cortas; `0` no es default salvo asignación explícita, se mantiene `<= 15` para no cambiar semántica). Sin nueva pantalla/botón.
- **Tests**: +1 en `AssistantEngineTest.kt` (`quickTasks_rankOverdueFirst`: normal `durationMinutes=10` + vencida `durationMinutes=10 dueAt=1` → `relatedTaskIds=[2,1]`, la atrasada primero). **602 domain tests PASS** (`bash tools/run_domain_tests.sh`); **6 AssistantEngineTest PASS** (compiladas/ejecutadas con `kotlinc` aparte, classpath jars `/tmp/libs`); **smoke 25 OK** (`bash tools/run_domain_checks.sh`). Sin regresión: el filtro `<= 15` y `take(6)` son idénticos, solo cambia el orden a ranking What Now.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); render real del asistente no probado en dispositivo.
- **Hallazgos adicionales**: con esto, las cuatro superficies del asistente (what-now, plan-mínimo, quick-tasks y la tarjeta What Now) comparten `WhatNowEngine.ordered` como fuente única de ranking. Próxima oportunidad de descubrimiento: auditar rutinas/automatizaciones y captura ultrarrápida.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt`, `app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `623e2bc` c.83).
- **Estado**: FIXED → VERIFIED (dominio JVM + assistant JVM).

### Siguiente
- Descubrimiento continuo: auditar rutinas/automatizaciones, captura ultrarrápida, detección de compromisos en notas.
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.
- `WhatNowEngine.reasonLabel` reusar desde la UI de What Now si aporta valor sin nueva superficie.

## Ciclo 85 — Parser — duraciones con número escrito ("dos horas"/"treinta minutos"/"un par de horas")

- **Fecha (UTC)**: 2026-08-13.
- **Run/ciclo**: 85 (rama `openhands/autonomous-ordia`). Continuación del área de cantidades
  escritas (c.35/c.40/c.57). Base inicial `0120249` (c.84, ya en remoto). `git fetch` confirmó
  local==remoto, sin divergencia (no STALE_RUN).
- **HEAD inicial**: `0120249` (c.84).
- **Problema seleccionado**: la **duración** (`ParsedTaskInput.durationMinutes`) era la única
  superficie de cantidad que NO aceptaba números escritos. Recordatorios (c.40) y fechas
  relativas (c.35/c.57) sí los aceptaban, pero `durationPatterns` solo casaba `\d{1,3}` y la
  fraccionaria solo "media hora"/"cuarto de hora". Consecuencia: "estudiar dos horas",
  "llamada de treinta minutos", "reunión de una hora", "un par de horas" → `durationMinutes=null`
  → el planificador las trataba como `TaskRules.MIN_PLAN_MINUTES` (10 min), el `DayLoad` del
  `SummaryEngine` subestimaba la carga real del día y `WhatNowEngine` perdía noción del progreso
  real. Degradación silenciosa de las decisiones automáticas centrales, no un crasheo.
- **Prioridad**: P1 (capacidad de tarea / inteligencia; la duración alimenta planificador,
  carga del día y What Now). No pérdida de datos, pero sí una función central deshabilitada para
  formas cotidianas de escribir duraciones en español.
- **Causa raíz**: ausencia de un patrón de duración con cantidad escrita. `parseWrittenNumber`
  y `writtenAmountPattern` ya existían; solo faltaba conectarlos al cómputo de `durationMinutes`.
- **Solución (mínima)**: nuevo `writtenDurationPattern = \b($writtenAmountPattern)\s*(minutos?|min|horas?|hora)\b`
  (reusa la lista única de literales; solo minutos/horas, ya que la duración se acota a ≤24 h).
  Se procesa con los **mismos guards** que la duración numérica (`timePhrasePreceding` para no
  robar "a las nueve horas"; `en$` para no robar "en dos horas", ya consumido por la fecha
  relativa). Recordatorios (con "antes") y fechas relativas (con "en") se procesan **antes** y
  consumen sus frases, así la duración escrita solo casa con la forma **bare**. El `when`
  elige la ocurrencia más a la izquierda entre {dígitos, escrita, fraccionaria}; borrado del
  conector `de/durante/por` simétrico al numérico. Sin nueva pantalla/botón.
- **Tests**: +9 (`dosHorasEscritasEsDuracionDe120Min`, `unaHoraEscritasEsDuracionDe60Min`,
  `treintaMinutosEscritosEsDuracionDe30Min`, `unParDeHorasEsDuracionDe120Min`,
  `duracionEscritaConConectorDeSeLimpiaDelTitulo`, `horasEscritasTrasALasNoSonDuracion`,
  `enDosHorasNoEsDuracionEscrita`, `dosHorasAntesEsRecordatorioNoDuracion`).
  **610 domain tests PASS** (`bash tools/run_domain_tests.sh`); **smoke 25 OK**
  (`PATH=/tmp/kotlinc-home/kotlinc/bin:$PATH bash tools/run_domain_checks.sh`).
  Sin regresión: las pruebas existentes de duración (digit/fraccionaria/compacta/rango) y de
  recordatorio pasan intactas.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: el parser de **horas** (no de duración)
  aún no acepta números escritos para la hora de un evento ("a las nueve horas" → la frase se
  conserva en el título; no es bug de duración, pero es una asimetría simétrica). Se deja fuera
  de este ciclo (área de horas, no duración). Candidata a próxima unidad si aporta valor real.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras push a `origin/openhands/autonomous-ordia`; base `0120249` c.84).
- **Estado**: FIXED → VERIFIED (dominio JVM).

### Siguiente
- Evaluar si el parser de **horas** de evento debe aceptar números escritos ("a las nueve horas"
  → 9:00); hoy no lo hace y deja la frase en el título. Verificar necesidad antes de implementar.
- Descubrimiento continuo: rutinas adaptables; detección de compromisos en notas; captura ultrarrápida.
- `PlanEngine`/replanización más amplia: si OVERLOADED recurrente, sugerir redistribuir la semana.


## Ciclo 86 — Búsqueda + Inteligencia — fix "esta semana" en domingo + consolidar `timeRank` DRY

- **Fecha (UTC)**: 2026-08-14.
- **Run/ciclo**: 86 (rama `openhands/autonomous-ordia`). Base inicial `0120249` (c.84, ya en
  remoto). `git fetch` reveló divergencia: el remoto avanzó a `48426ba` (c.85 parser duración
  escrita). Rebase limpio (sin colisión de archivos: c.85 tocó `NaturalTaskParser*`+AI_AUTONOMY;
  yo `SearchEngine*`/`TaskRules`/`WhatNowEngine*`). Re-ejecuté tests tras rebase: 612 PASS.
- **HEAD inicial**: `0120249` (c.84).
- **Problema seleccionado (1, P1)**: `SearchEngine.taskMatchesDateScope(ThisWeek)` calculaba
  `daysToSunday = 7 - (today.dayOfWeek.value % 7)`. En domingo (`value=7`), `7 % 7 = 0` →
  `daysToSunday = 7` → `endOfWeek = hoy + 7 = domingo siguiente`: "esta semana" mostraba tareas
  de toda la semana ENTRANTE que no pertenecen a la actual. Bug introducido en c.81; los tests
  solo cubrían jueves (donde la fórmula acierta), por eso pasó inadvertido. Un usuario que busca
  "esta semana" en domingo veía su lista inflada con la semana próxima.
- **Problema seleccionado (2, P2)**: `WhatNowEngine` tenía una **copia privada de `timeRank`
  idéntica** a la de `TaskRules`, el mismo patrón de duplicación que causó divergencia silenciosa
  con `priorityScore` (fix c.53). Riesgo latente de bug P1 si una copia se editaba y la otra no.
- **Prioridad**: P1 (búsqueda — integridad de resultado: la búsqueda devolvía tareas que no
  correspondían al filtro temporal pedido) + P2 (DRY/deuda de diseño — sin bug activo, probe
  divergencia MATCH en 5 instantes).
- **Causa raíz (1)**: el operador `%` se aplicaba a `today.dayOfWeek.value` ANTES de la resta, en
  vez de a la resta completa. `7 % 7 = 0` eclipsa el caso domingo.
- **Causa raíz (2)**: ausencia de fuente única para `timeRank` (misma clase de deuda que
  `priorityScore` antes de c.53). `WhatNowEngine.timeRank` private == `TaskRules.timeRank` private.
- **Solución (1, mínima)**: `(7 - today.dayOfWeek.value) % 7`. Domingo da 0 (la semana termina
  hoy); lunes da 6 (domingo, sin cambio para el resto de días). La atrasada sigue excluida del
  rango semanal (tiene su propio filtro OVERDUE). Sin nueva pantalla/botón.
- **Solución (2, mínima, DRY)**: `TaskRules.timeRank` pasa de private a public (fuente única de
  verdad); `WhatNowEngine.ordered` delega en `TaskRules.timeRank` y elimina su copia privada +
  el `val today` muerto. Retrocompatible (mismo orden — probe divergencia MATCH).
- **Tests**: +1 en `SearchEngineDateScopeTest.kt` (`estaSemana_onSundayEndsTodayNotNextWeek`:
  en domingo 2026-08-16, el lunes siguiente id 2 ya no aparece en "esta semana"; la atrasada id 3
  sigue fuera; confirmado FAIL antes del fix, PASS después). +1 en `WhatNowEngineTest.kt`
  (`whatNowAndWidgetAgreeOnBestTaskAcrossTime`: conjunto diverso —vencida, en curso, urgente,
  inbox, programada— en 5 instantes distintos; What Now (`WhatNowEngine.suggest`) y widget
  (`TaskRules.nextBestTask`) devuelven la misma tarea; falla si vuelven a divergir).
  **612 domain tests PASS** (`bash tools/run_domain_tests.sh`); **smoke 25 OK**
  (`bash tools/run_domain_checks.sh`); **612 PASS tras rebase** sobre c.85. Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: `SearchEngine` date-scope aún no cubre
  "parte del día" ("esta tarde"/"esta noche") ni "la semana que viene"/"este mes" (BACKLOG P3,
  OPEN — evaluar necesidad real antes de implementar, anti-feature-bloat). La consolidación de
  `timeRank` cierra la segunda familia de duplicación entre `WhatNowEngine` y `TaskRules`
  (`priorityScore` en c.53, `isImminentStart`/`IMMINENT_WINDOW_MINUTES` en c.47); queda revisar
  si `isDueToday`/otros helpers siguen duplicados (futuro).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`,
  `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`,
  `app/src/main/java/com/ordia/app/domain/TaskRules.kt`,
  `app/src/main/java/com/ordia/app/domain/WhatNowEngine.kt`,
  `app/src/test/java/com/ordia/app/domain/WhatNowEngineTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `4f0da4f` (refactor: consolidar timeRank en TaskRules como fuente única),
  `66e3d3` (fix: 'esta semana' en domingo arrastraba la semana siguiente) → rebase → `26cd1d3`
  push a `origin/openhands/autonomous-ordia`.
- **HEAD final**: `26cd1d3` (tras push a `origin/openhands/autonomous-ordia`; base `48426ba` c.85).
- **Estado**: FIXED → VERIFIED (dominio JVM).

### Siguiente
- Auditar si quedan más helpers duplicados entre `WhatNowEngine` y `TaskRules`
  (`isDueToday`, `isInProgressNow`, etc.) — cerrar la familia DRY.
- `SearchEngine` date-scope: "parte del día" / "este mes" (P3 — evaluar
  necesidad real antes de implementar, anti-feature-bloat). "semana que viene"/
  "próxima semana" ya cubierto (NEXT_WEEK, c.87).
- Descubrimiento continuo: rutinas adaptables; detección de compromisos en
  notas; captura ultrarrápida; `PlanEngine`/replanización si OVERLOADED
  recurrente.

---

## Ciclo 87 — 2026-08-14 (feat P2 búsqueda: NEXT_WEEK)

- **HEAD inicial**: `623e2bc` (origin/openhands/autonomous-ordia, c.86).
- **Problema seleccionado**: `SearchEngine` date-scope no distinguía "esta
  semana" de "semana que viene"/"próxima semana" → el usuario que busca qué vence
  la semana próxima veía la semana actual (P2, recuperación de información).
- **Prioridad**: P2.
- **Causa raíz**: `detectDateScope` no diferenciaba "semana" con/sin modificador de
  proximidad; `WEEK_TOKENS = {semana}` sin distinguir "que viene"/"próxima".
- **Solución (mínima)**: nuevo `DateScope.NEXT_WEEK`; `NEXT_WEEK_TOKENS =
  {proxima, proximas, viene}`; rama `hasWeek && hasNext → NEXT_WEEK` antes de
  `hasWeek → THIS_WEEK`. Rango `startNextWeek = today + daysToSunday + 1` (lunes
  próximo), `endNextWeek = startNextWeek + 6` (domingo próximo), reusando la
  fórmula `(7 - dayOfWeek) % 7` del fix de domingo del c.86. `dateScopeTokens`
  incluye `NEXT_WEEK_TOKENS` para no exigir "viene"/"proxima" en el contenido.
  Sin nueva pantalla/botón (reuso el `SearchEngine` existente).
- **No-regresión**: "esta semana" y "semana" sola siguen siendo `THIS_WEEK`
  (test `semanaSolaSigueSiendoEstaSemana`).
- **Tests**: +5 en `SearchEngineDateScopeTest.kt` (`semanaQueViene_returnsOnlyNextWeekTasks`,
  `proximaSemana_returnsOnlyNextWeekTasks`, `semanaQueViene_excludesThisWeekTasks`,
  `semanaSolaSigueSiendoEstaSemana`, `semanaQueViene_onSundayStartsNextMonday`).
  **617 domain tests PASS** (`bash tools/run_domain_tests.sh`); **smoke 25 OK**
  (`bash tools/run_domain_checks.sh`). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: pendiente "esta tarde"/"esta
  noche" (parte del día) y "este mes" (rango mensual) en `SearchEngine` — OPEN
  (anti-feature-bloat: evaluar necesidad real antes de implementar).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`,
  `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `a98b9be` (feat(search): 'semana que viene'/'próxima semana' NEXT_WEEK).
- **HEAD final**: `a98b9be` (tras push a `origin/openhands/autonomous-ordia`; base `7a3f636` c.86).
- **Estado**: FIXED → VERIFIED (dominio JVM).

## Ciclo 88 — 2026-08-14 (fix P1 parser: horas escritas)

- **HEAD inicial**: `61bc0e9` (origin/openhands/autonomous-ordia, c.87).
- **Problema seleccionado**: `NaturalTaskParser` no resolvía horas **escritas** en
  español — las frases más cotidianas para agendar ("Cita a las diez de la noche",
  "Cena nueve de la noche", "Reunión a las nueve", "doce de la noche",
  "ocho y media"). Solo reconocía dígitos (0-12). Antes la hora escrita quedaba
  en el título Y/O se agendaba a la canónica de la parte del día ("doce de la
  noche" → 21:00 en vez de medianoche 00:00). P1: integridad de agenda/captura.
- **Prioridad**: P1.
- **Causa raíz**: `timePatterns` y el patrón standalone de parte-del-día solo
  casaban `\d+` para la hora; no existía mapeo token-escrito→número para el
  componente de hora (sí existía `parseWrittenNumber` para duraciones c.85).
- **Solución (mínima)**: helper `parseHour(token)` que mapea escrito→1-12 (uno-doce,
  cero); `WRITTEN_HOUR_ALT` integrado en `timePatterns` y en el patrón standalone
  "de la tarde/noche/mañana/madrugada"; `explicitTimeData` y el resolvedor de
  standalone usan `parseHour`, con la misma lógica AM/PM de los dígitos
  (madrugada/mañana AM; tarde/noche PM; 12 de la noche=00:00; 12 del mediodía=12:00;
  0→12). Sin nueva pantalla/botón (reuso el parser existente — captura natural).
- **No-regresión**: "a las nueve horas" sigue siendo hora no duración (test ampliado
  `aLasDiezHorasEscritaSigueSiendoHoraNoDuracion` ahora verifica 10:00 + null dur);
  horas con dígito intactas; "doce de la noche" (dígito) sigue siendo medianoche.
- **Tests**: +11 en `NaturalTaskParserTest.kt` (`aLasNueveEscritaResuelveHoraYLimpiaTitulo`,
  `aLasDiezDeLaNocheEscritaEs22h`, `aLasDosDeLaTardeEscritaEs14h`,
  `aLasOchoYMediaEscritaEs830`, `aLasNueveYCuartoEscritaEs915`,
  `doceDeLaNocheEscritoEsMedianoche`, `nueveDeLaNocheEscritoEs21h`,
  `ochoDeLaMananaEscritoEs8h`, `dosDeLaTardeEscritoEs14h`,
  `aLasDiezHorasEscritaSigueSiendoHoraNoDuracion`).
  **627 domain tests PASS** (`bash tools/run_domain_tests.sh`); **smoke 25 OK**
  (`bash tools/run_domain_checks.sh`). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales (descubrimiento continuo)**: "diez y media" sin conector
  "a las" no se resuelve (forma indirecta menos común; no se añade patrón para
  evitar ambigüedad con números sueltos en el título) — OPEN, baja prioridad.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (pendiente de push).
- **HEAD final**: (pendiente de push).
- **Estado**: FIXED → VERIFIED (dominio JVM).

### Siguiente
- Parser: "diez y media" sin "a las" (P3, evaluar ambigüedad).
- `SearchEngine` date-scope: "esta tarde"/"esta noche" (parte del día) / "este mes"
  (rango mensual) — evaluar necesidad real (anti-feature-bloat).
- Descubrimiento continuo: rutinas adaptables; detección de compromisos en notas;
  captura ultrarrápida; `PlanEngine`/replanización si OVERLOADED recurrente.

---

## Ciclo 88 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `61bc0e9` (c.87 docs commit "registro HEAD final ciclo 87").
- **Problema seleccionado**: **P1** — asimetría de acentos en `NaturalTaskParser`:
  `"proximo viernes"` / `"viernes proximo"` (sin tilde) dicho en el propio día objetivo
  se agendaba en HOY en vez de +7 (próxima semana). La forma acentuada `"próximo"` ya
  funcionaba (fijada en c.72), pero el c.72 solo detectó `"próxim"` en `nextExplicit`
  (línea 919), no `"proxim"`. `monthBaseForBoundary` (línea 1608) sí aceptaba ambas →
  descuido, no decisión. La cita caía una semana antes; el recordatorio disparaba ~7d
  temprano. Riesgo real de pérdida/duplicación de cita. Dependencia sutil de hora: el
  bug solo se manifestaba antes de la 09:00 canónica (tras ella, el rollover anti-pasado
  lo "arreglaba" por accidente).
- **Causa raíz**: `nextExplicit = mv.contains("que viene") || mv.contains("próxim")` —
  solo forma acentuada. Con `nextExplicit=false` → `nextWeekdayOrSame` (devuelve hoy).
- **Solución mínima**: `nextExplicit = mv.contains("que viene") || mv.contains("próxim")
  || mv.contains("proxim")` — alineado con la línea 1608. Sin tocar el regex (ya soporta
  ambas vía `pr[oó]ximo`) ni el flujo de `weekdaySameDayCandidate`.
- **Bugs**: P1 cita mal agendada (sin tilde + hoy=día objetivo + antes de 09:00).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +3 (`proximoSinTildeViernesHoyFuerzaProximaSemana`,
  `proximoSinTildeSufijoFuerzaProximaSemana`, `proximoConTildeSigueForzandoProximaSemana`)
  con `now`=viernes 2026-07-31 08:00 (antes de la 09:00 canónica). Confirmado RED antes
  del fix (2 failures: `expected:<2026-08-07> but was:<2026-07-31>`) y GREEN tras.
  Comando: `bash tools/run_domain_tests.sh` → **620 tests PASS** (26 clases).
  Smoke: `bash tools/run_domain_checks.sh` → **25 assertions OK**. Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `c62c056` (`fix(parser): \"próximo\" sin tilde dicho hoy=ese día ya no cae en HOY (P1)`).
- **HEAD final**: `c62c056` (push OK a `openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**:
  - Auditar otros chequeos de string acento-sensibles en el parser (p.ej. "qué viene",
    otras variantes) por si quedan asimetrías análogas.
  - `SearchEngine` date-scope: "parte del día" ("esta tarde"/"esta noche") / "este mes"
    (P3 — evaluar necesidad real antes de implementar, anti-feature-bloat).
  - Descubrimiento continuo: rutinas adaptables; detección de compromisos en notas;
    captura ultrarrápida; `PlanEngine`/replanización si OVERLOADED recurrente.


## Ciclo 89 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `d030cca` (c.88 docs commit "registro HEAD final ciclo 88").
- **Problema seleccionado**: **P1 (pérdida de datos silenciosa)** — asimetría de acentos en el parser de recurrencias: bare-plural `"sabados"` sin tilde. `"fútbol sabados"` caía en `recurrence=NONE` y "sabados" quedaba como residuo en el título → la rutina semanal se olvidaba en silencio (sin recurrencia, sin recordatorio recurrente, invisible en planificador). El día más típico de hábito semanal. Simétrico al c.88 ("próximo" sin tilde) y al c.41 (plurales en la regex).
- **Causa raíz**: `dayListPattern` (regex) casa ambas formas `s[aá]bados?`, pero el guard `barePluralSingle` (línea ~1456) solo comprobaba `g.contains("sábados")` con tilde → "sabados" pasaba el guard como no-plural y no se reconocía como recurrencia de un solo día bare → caía fuera del `if` y la frase no se consumía. Descuido del c.41, no decisión. La lista de 2+ días (`gym sabados y domingos`) ya funcionaba por la rama `days.size>=2`.
- **Solución mínima**: `g.contains("sábados") || g.contains("sabados") || g.contains("domingos")` — alineado con la regex `s[aá]bados?`. Sin tocar el regex ni el flujo de `weekdaySameDayCandidate`.
- **Bugs**: P1 rutina semanal perdida ("sabados" sin tilde, día bare plural único).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +2 (`parsesBarePluralSingleDayRecurrenceUnaccented`, `parsesBareDayListUnaccentedSabado`). Probe JVM confirmó RED antes del fix (`freq=NONE days='' title='fútbol sabados'` para "fútbol sabados") y GREEN tras (`freq=WEEKLY days='6' title='fútbol'`). Comando: `bash tools/run_domain_tests.sh` → **622 tests PASS** (27 clases — subida desde 620). Smoke: `bash tools/run_domain_checks.sh` → **25 assertions OK**. Sin regresión.
- **Auditoría de acentos (TASK-3, completada)**: los 7 `contains`/`==` acento-sensibles del parser están balanceados — `mediodía`/`mediodia` (977), `próxim`/`proxim` (925/1614), `sabados` (1457, fixed). Meridiem `delamañana`/`delamanaana` (1011) cubre la forma sin tilde habitual (probe: "9 de la manana" → parses OK con dueAt). No quedan asimetrías activas.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver abajo, tras `git commit`).
- **HEAD final**: `6afcb0b` (push OK a `openhands/autonomous-ordia` por confirmar).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: Auditoría de acentos del parser COMPLETADA. Descubrimiento funcional: rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; `SearchEngine` date-scope "parte del día"/"este mes" (P3 anti-feature-bloat); `PlanEngine`/replanización si OVERLOADED recurrente.



## Ciclo 90 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `61bc0e9` (c.87 docs commit; base obsoleta — el remoto ya tenía c.88+c.89; se hizo `git fetch` + `git rebase` no destructivo sobre `fe13527`).
- **Problema seleccionado**: **P1** — `NaturalTaskParser` solo reconocía **dígitos** (0-12) como componente de hora; las formas escritas cotidianísimas ("a las nueve", "diez de la noche", "doce de la noche", "ocho y media", "nueve y cuarto", "dos de la tarde") no casaban patrón de hora → la hora quedaba en el título Y/O se agendaba a la canónica de la parte del día ("doce de la noche"→21:00 en vez de medianoche 00:00). El helper `parseWrittenNumber` ya existía para duraciones (c.85) pero no estaba conectado al componente de hora.
- **Causa raíz**: los `timePatterns` y `standaloneHourPartOfDayPattern` solo admitían `[01]?\d|2[0-4]` como grupo de hora; las palabras escritas caían fuera del match y el texto no se consumía.
- **Solución mínima**: helper `parseHour(token)` mapea escrito→1-12 (reusa `parseWrittenNumber`); `WRITTEN_HOUR_ALT` integrado en `timePatterns` y en `standaloneHourPartOfDayPattern`; `explicitTimeData` y `resolveStandaloneHourPartOfDay` usan `parseHour` con la misma lógica AM/PM de los dígitos (madrugada/mañana AM; tarde/noche PM; 12 de la noche=00:00; 12 del mediodía=12:00; 0→12). Sin nueva pantalla/botón (recuperación de información horaria).
- **Bugs**: P1 hora escrita perdida/mal agendada (doce de la noche→21:00 en vez de 00:00).
- **Features**: ninguna (fix de captura horaria).
- **Tests (TDD)**: +11 (`doceDeLaNocheEsMedianoche`, `deLaMadrugadaEsAmYLimpiaTitulo`, `aLasNueveResuelveHora`, `diezDeLaNocheResuelve22`, `onceDeLaNocheResuelve23`, `dosDeLaTardeResuelve14`, `ochoYMediaResuelve0830`, `nueveYCuartoResuelve0915`, `nueveDeLaMadrugadaResuelve09`, `escritoConPmCompacto`, `aLasDiezHorasSigueSiendoHora`). Comando: `bash tools/run_domain_tests.sh` → **632 tests PASS** (27 clases — 622 c.89 + 11; la base c.89 contaba 622 por los fixes paralelos de acentos). Smoke: `bash tools/run_domain_checks.sh` → **25 assertions OK**. Sin regresión (dígito intacto, "a las diez horas" sigue siendo hora no duración).
- **Decisión de diseño**: NO se añadió patrón para "diez y media"/"nueve y cuarto" **sin conector "a las"** — forma indirecta menos común; sin señal desambiguadora chocaría con números sueltos en el título ("comprar 2 entradas"). Registrado en BACKLOG como P3 ABIERTO.
- **STALE_RUN**: base `61bc0e9` obsoleta al hacer push (el remoto ya tenía c.88/89 de otra ejecución). Resolución no destructiva: `git fetch` + `git rebase origin/openhands/autonomous-ordia`; conflictos solo en archivos de memoria (CURRENT_STATE/RUN_LOG), resueltos renumerando a ciclo 90 y conservando el trabajo del otro agente. Código y tests auto-mergearon limpio.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `66b867a` (original, reescrito al rebasear; hash final tras push).
- **HEAD final**: (tras push, ver `git log`).
- **Estado**: FIXED → VERIFIED (dominio JVM); STALE_RUN resuelto no destructivamente.
- **Próxima prioridad**: "diez y media" sin conector (P3, requiere decisión de diseño); `SearchEngine` date-scope "parte del día"/"este mes" (P3 anti-feature-bloat); descubrimiento funcional: rutinas adaptables; detección de compromisos en notas; captura ultrarrápida; `PlanEngine`/replanización si OVERLOADED recurrente.

## Ciclo 91 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `fe13527` (c.89 docs commit "registro HEAD final ciclo 89").
- **Problema seleccionado**: **P1 (pérdida de datos silenciosa)** — `"último día del mes"`
  (la forma más común y coloquial de "fin de mes") no se parseaba. `endOfMonthPattern`
  solo reconocía `fin`/`finales` → `"entregar informe último día del mes"` caía en
  `dueAt=null` y la frase quedaba como título → el vencimiento se olvidaba (sin
  recordatorio, invisible en planificador/What Now). Pagos, rentas y cierres dichos en la
  forma más natural se perdían. Simétrico a `fin de mes` (c.32) y `el N del mes que viene`
  (c.68).
- **Causa raíz (doble)**:
  1. `endOfMonthPattern` solo listaba `fin(?:ales|es)?` como alternativa; no incluía
     `últim[oa] día`.
  2. **Trampa sutil**: al añadir la alternativa `[uú]ltim[oa]\s+dí[ai]a`, el regex seguía
     sin casar porque el `\b` inicial (word boundary) es **ASCII-only** en la regex de
     Java/Kotlin por defecto → no reconoce un boundary antes de la `ú` (U+00FA, no-ASCII).
     Por eso `fin de mes` (empieza con `f` ASCII) sí casaba y `último día` no.
- **Solución mínima**:
  - `endOfMonthPattern`: añadir alternativa `[uú]ltim[oa]\s+d[ií]a` Y reemplazar `\b`
    inicial por `(?<!\p{L})` (lookbehind Unicode-safe). Reusa todo el flujo de `fin de mes`.
  - `monthBaseForBoundary`: rama "end" ahora incluye `t.contains("últim") ||
    t.contains("ultim")`. El modificador "que viene"/"próxim" ya se resuelve antes (línea
    `isNext`) → "último día del mes que viene" ancla al mes siguiente sin doble-desplazamiento.
- **Bugs**: P1 vencimiento olvidado ("último día del mes" → dueAt=null).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +5 (`ultimoDiaDelMesParsesDueAtFinMesActual`,
  `ultimoDiaDelMesConDelRespetaHoraExplicita`, `ultimoDiaDelMesSinTildeFuncionaIgual`,
  `ultimoDiaDelMesQueVieneAnclaFinMesSiguiente`, `ultimoDiaDelMesProximoAnclaFinMesSiguiente`).
  Probe JVM confirmó RED antes del fix de la boundary (`due=null`) y GREEN tras
  (`due=07-31` actual / `08-31` "que viene" / "próximo"; forma sin tilde `ultimo dia del mes`
  OK). Comando: `bash tools/run_domain_tests.sh` → **637 tests PASS** (27 clases — base
  c.90=632 + 5 nuevos; ambos fixes coexisten). Smoke: `bash tools/run_domain_checks.sh` →
  **25 assertions OK**. Sin regresión (`fin de mes`, `fin del mes que viene`, `el N del mes
  que viene`, horas escritas c.90 siguen OK).
- **STALE_RUN**: base `fe13527` obsoleta al hacer push (el remoto ya tenía c.90 de otra
  ejecución — fix horas escritas). Resolución no destructiva: `git fetch` + `git rebase
  origin/openhands/autonomous-ordia`; código y tests auto-mergearon limpio (distintas áreas
  del parser), conflictos solo en memoria (CURRENT_STATE/RUN_LOG) resueltos conservando el
  trabajo del otro agente y renumerando mi ciclo a 91.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `6eb5b0f` (rebaseado sobre `d3734ed`).
- **HEAD final**: `6eb5b0f` (push OK por confirmar).
- **Estado**: FIXED → VERIFIED (dominio JVM); STALE_RUN resuelto no destructivamente.
- **Próxima prioridad**: descubrimiento continuo — auditar otros sinónimos de "fin de mes"
  no cubiertos ("a fin de mes" con prefijo ya OK; "cierre de mes"; "vence el mes");
  `RecurrenceEngine` edge cases; detección de compromisos en notas; `PlanEngine`/replanización
  si OVERLOADED recurrente.


## Ciclo 92 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `3c42171` (c.91 fix "último día del mes").
- **Problema seleccionado**: **P1 (pérdida de datos silenciosa)** — continuación del
  descubrimiento continuo de sinónimos de "fin de mes" no cubiertos (apuntado en "próxima
  prioridad" del c.91). Probe JVM (`ProbeSynonymsTest`, ahora `NaturalTaskParserCierreTest`)
  confirmó: `"pago cierre de mes"` y `"renta cierre del mes"` caían en `dueAt=null` →
  vencimiento olvidado (sin recordatorio, invisible en planificador/What Now). "cierre de
  mes"/"cierre del mes" es sinónimo cotidiano real de "fin de mes" en contexto financiero
  (alquileres, facturas, nómina, contabilidad). Pagos dichos así se perdían, igual que
  `último día del mes` (c.91). "vence el mes" quedó fuera: es ambiguo ("vence el mes que
  viene" sí casa vía "fin de mes... que viene" solo si se dice así; "vence el mes" a secas
  no implica fin de mes).
- **Causa raíz**: `endOfMonthPattern` solo enumeraba `fin(?:ales|es)?` y `[uú]ltim[oa] día`
  (añadido en c.91); no incluía `cierre`. Por eso "cierre de mes" no casaba y la frase
  quedaba como título → dueAt=null. `monthBaseForBoundary` tampoco reconocía `cierre` en su
  rama "end" (aunque al no casar el regex nunca llegaba ahí).
- **Solución mínima**:
  - `endOfMonthPattern`: añadir alternativa `cierre` a la lista
    `(?:fin(?:ales|es)?|cierre|[uú]ltim[oa]\s+d[ií]a)`. Reusa todo el flujo de `fin de mes`.
  - `monthBaseForBoundary`: rama "end" ahora incluye `t.contains("cierre")`. El modificador
    "que viene"/"próxim" ya se resuelve antes (línea `isNext`) → "cierre del mes que viene"
    ancla al mes siguiente sin doble-desplazamiento.
  - Doc comment actualizado para listar "cierre de mes" / "cierre del mes".
- **Bugs**: P1 vencimiento olvidado ("cierre de mes"/"cierre del mes" → dueAt=null).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +4 (`cierreDeMesAnclaFinDeMesActual`,
  `cierreDelMesAnclaFinDeMesActual`, `cierreDelMesQueVieneAnclaFinMesSiguiente`,
  `cierreDeMesRespetaHoraExplicita`) en `NaturalTaskParserCierreTest.kt`. Probe JVM confirmó
  RED antes del fix (`due=null` en ambos) y GREEN tras (`due=07-31` actual / `08-31` "que
  viene"; hora explícita `a las 18` respeta fecha fin de mes). Comando:
  `bash tools/run_domain_tests.sh` → **641 tests PASS** (base c.91=637 + 4 nuevos; ambos fixes
  coexisten). Smoke: `bash tools/run_domain_checks.sh` → **25 assertions OK**. Sin regresión
  (`fin de mes`, `último día del mes`, `el N del mes que viene`, horas escritas siguen OK).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserCierreTest.kt` (nuevo),
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `61c1daa` (push fast-forward OK `3c42171..61c1daa` → `openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo — auditar sinónimos de mediados/principios
  de mes ("a mitad de mes", "comienzos de mes"); `RecurrenceEngine` edge cases; detección de
  compromisos en notas; `PlanEngine`/replanización si OVERLOADED recurrente; revisar si
  "corte de mes" (sinónimo latinoamericano de cierre) merece cubrirse.

## Ciclo 93 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `d3734ed` (c.90 fix "horas escritas"). **Nota de concurrencia**: el
  remoto avanzó mientras tanto con c.91 (`3c42171` "último día del mes") y c.92
  (`61c1daa` "cierre de mes") → `1f2014c`. Mi trabajo partió de una base obsoleta (c.90).
- **Problema seleccionado**: **P1 (compromiso periódico olvidado / pérdida silenciosa de
  recurrencia)** en `NaturalTaskParser.parseRecurrence`. Las formas ADJETIVAS cotidianas
  (`mensual`, `semanal`, `anual`, `quincenal`, `bimestral`, `trimestral`, `semestral`) NO se
  reconocían: solo el adverbio `-mente` (`mensualmente`, `semanalmente`, …) y, para quincenal,
  las frases `cada quincena`/`quincenalmente`/`todas las quincenas`. Probe JVM confirmó RED
  antes (`rec=NONE interval=1 due=null` para los 7 adjetivos) y GREEN tras.
- **Causa raíz**: `fixedPatterns` (línea ~1590) solo listaba adverbios
  `\bsemanalmente\b`/`\bmensualmente\b`/`\banualmente\b`; el bloque quincenal autónomo
  (línea ~1564) solo casaba `quincenalmente` (no el adjetivo); `bimestral`/`trimestral`/
  `semestral` no tenían rama (solo el numeral `cada N meses`). El adjetivo caía a
  `base = RecurrenceResult(NONE, …)`. Sutil: la fecha explícita sí se conservaba
  ("pago mensual el 10" → dueAt=día 10, rec=NONE) así la persona creía la recurrencia puesta.
- **Solución mínima (sin nueva pantalla/botón, sin enum/migración)**:
  1. Bloque quincenal: `quincenalmente` → `quincenal(?:mente)?` (captura el adjetivo) →
     `WEEKLY interval=2`.
  2. Nuevo bloque plurimensual (antes de `fixedPatterns`, que solo admite interval=1):
     `bimestral(?:mente)?`→`MONTHLY+2`, `trimestral(?:mente)?`→`MONTHLY+3`,
     `semestral(?:mente)?`→`MONTHLY+6`. Reutiliza `RecurrenceEngine.plusMonths`.
  3. `fixedPatterns` añade `|\bsemanal\b`(WEEKLY), `|\bmensual\b`(MONTHLY), `|\banual\b`
     (YEARLY) — adjetivos de intervalo 1. Límites `\b` evitan colisión con "mensualmente"
     y "manual".
- **No-regresión**: adverbios `-mente` OK; "reunión semanal los lunes" sigue cayendo en la
  rama de lista de días (WEEKLY+días antes de `fixedPatterns`); "cada 2/3/6 meses" inalterado;
  fecha explícita tiene prioridad ("pago mensual el 10" → MONTHLY+1 anclado al 10).
- **Bugs**: P1 compromiso periódico olvidado (adjetivos de recurrencia → rec=NONE).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +8 (`adjetivoMensualParsesMonthlyRecurrence`,
  `adjetivoSemanalParsesWeeklyRecurrence`, `adjetivoAnualParsesYearlyRecurrence`,
  `adjetivoQuincenalParsesBiweeklyRecurrence`, `adjetivoBimestralParsesMonthlyInterval2`,
  `adjetivoTrimestralParsesMonthlyInterval3`, `adjetivoSemestralParsesMonthlyInterval6`,
  `adjetivoMensualRespetaFechaExplicita`) en `NaturalTaskParserTest.kt`. Comando:
  `bash tools/run_domain_tests.sh` → **649 tests PASS** (27 clases — subida desde 641).
  Smoke: `bash tools/run_domain_checks.sh` → **25 assertions OK**. Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Rebase/concurrencia**: commit original `1f8cce9` (basado en c.90) rebaseado sobre
  `1f2014c` (c.91+c.92). El código (.kt) auto-mergeó limpio (áreas distintas del parser:
  adjetivos vs límites mensuales). Solo `AI_AUTONOMY/CURRENT_STATE.md` y `RUN_LOG.md`
  tuvieron conflictos → reconciliados manualmente preservando el trabajo de ambos runs.
  Renumerado de "c.91" (etiqueta original colisionante) a **c.93**.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `0253098` (push OK `1f2014c..0253098` → `openhands/autonomous-ordia`).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo — más formas de recurrencia coloquiales
  ("cada rato"/"de vez en cuando"/"a diario"); "corte de mes" (sinónimo LA de cierre);
  `RecurrenceEngine` edge cases; detección de compromisos en notas; replanificación si
  OVERLOADED recurrente.

## Ciclo 94 — 2026-08-14

- **HEAD inicial**: `3c42171` (c.91; STALE_RUN: el remoto avanzó a c.93 durante este run — `bbb2e16` (c.92 cierre de mes + c.93 adjetivos de recurrencia). Resolución no destructiva: `git fetch` + `git rebase origin/openhands/autonomous-ordia`; el código (parser+tests) auto-mergearon limpio (distintas áreas del parser: `fractionalRelativePattern` vs `cierre de mes`/adjetivos de recurrencia), conflictos solo en memoria (CURRENT_STATE/RUN_LOG) resueltos conservando ambos trabajos y renumerando mi ciclo a 94. Base
  remoto, sin divergencia).
- **Problema seleccionado**: **P1 (captura perdida / recordatorio imposible)** —
  `"en media hora"` / `"en un cuarto de hora"` / `"dentro de media hora"` /
  `"de aquí a media hora"` se parseaban como **DURACIÓN** (sin vencimiento) en vez de
  **punto en el tiempo** (+30/+15 min). `relativePattern` solo aceptaba números escritos
  enteros (un…treinta) o dígitos, NO las fracciones cotidianas "media hora"/"cuarto de
  hora". Estas caían a `fractionalDurationPattern` → `dueAt=null`, `durationMinutes=30/15`
  y el prefijo "en"/"dentro de" quedaba como residuo en el título ("llamar en media hora"
  → título "llamar en"). La tarea quedaba SIN vencimiento → invisible en What
  Now/planificador, recordatorio imposible de programar. Asimetría flagrante: "en treinta
  minutos" (dígitos) y "en una hora" (entero escrito) SÍ eran fecha relativa, pero la
  fracción equivalente "en media hora" no. Forma ultra-común en captura rápida móvil.
- **Causa raíz**: `relativePattern` no cubría fracciones sin dígitos; el guard de orden
  del `when` dejaba que `fractionalDurationPattern` (que SÍ casa "media hora") ganara
  antes → convertía un punto-en-el-tiempo en una duración y dejaba el prefijo "en"
  huérfano en el título.
- **Solución mínima**: nuevo `fractionalRelativePattern` (prefijo
  `en|dentro de|de aquí a|de acá a` + `media hora`/`(un) cuarto de hora`) simétrico a
  `relativePattern`. Se procesa ANTES que la duración (rama propia en el `when`) para que
  `fractionalDurationPattern` no robe la fracción. Resuelve
  `fractionalRelativeDueAt = now + (30|15)min`; consume la frase completa (prefijo
  incluido) → título limpio. Incluido en `effectiveRelativeDueAt` (misma prioridad que
  `relativeDueAt`) y `relativeIsDays=false` (sub-hora). El prefijo es **obligatorio** →
  "reunión media hora" (sin prefijo) sigue siendo `durationMinutes=30` (duración real,
  no-regresión); "media hora antes" (recordatorio) lo captura `reminderPatterns` antes
  (no choca); "en una hora" (entero escrito) sigue funcionando sin conflicto.
- **Bugs**: P1 captura perdida ("en media hora" → duración sin vencimiento + título sucio).
- **Features**: ninguna (fix de integridad de datos del parser).
- **Tests (TDD)**: +7 (`enMediaHoraEsFechaRelativa`, `enUnCuartoDeHoraEsFechaRelativa`,
  `enCuartoDeHoraSinUnEsFechaRelativa`, `dentroDeMediaHoraEsFechaRelativa`,
  `deAquiAMediaHoraEsFechaRelativa`, `mediaHoraSinPrefijoSigueSiendoDuracion` + no-regresión
  de `durationMinutes`). Probe JVM confirmó RED antes del fix (`due=null`) y GREEN tras
  (`due=now+30min`). Comando: `bash tools/run_domain_tests.sh` → **655 tests PASS**
  (27 clases — 649 base (c.93) + 7 nuevos). Smoke: `bash tools/run_domain_checks.sh` → **25
  assertions OK**. Sin regresión (`en una hora`, `en treinta minutos`, `media hora`
  duración, `media hora antes` recordatorio siguen OK).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver `git log` tras push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo — auditar otras fracciones relativas no
  cubiertas ("en tres cuartos de hora"=45min, "en una hora y media"=90min compuesta);
  "a la una" (hora 1 escrita, standalone) no resuelve (BUG C pendiente);
  `RecurrenceEngine` edge cases; detección de compromisos en notas; `PlanEngine`/
  replanización si OVERLOADED recurrente. (fix(parser): "en media hora"/"en un cuarto de hora" ya son fecha relativa (P1))

---

## Ciclo 94b — 2026-08-14 — Parser: fracciones relativas COMPUESTAS ("en una hora y media"=90, "en tres cuartos de hora"=45)

- **HEAD inicial**: 18795ccd3ff85f3cc76ecb3e7e6725dfe92368c2 (c.94 "en media hora" fecha relativa)
- **Ciclo**: 94b (continuación directa del c.94 — la "próxima prioridad" del c.94 nombraba
  exactamente estas formas).
- **Problema seleccionado**: P1 captura/recuperación — fracciones relativas COMPUESTAS no se
  parseaban. Tres sub-bugs:
  1. `"en una hora y media"` (90 min): `relativePattern` robaba solo "en una hora" (+60) y
     dejaba "y media" como residuo en el título → la cita se agendaba **30 min antes** de lo
     pedido (recordatorio disparaba temprano). El usuario decía "llámame en una hora y
     media" y recibía el aviso a los 60 min.
  2. `"en tres cuartos de hora"` (45 min): ningún patrón casaba → `dueAt=null`, tarea **sin
     vencimiento** (recordatorio imposible de programar, invisible en What Now/planificador).
  3. `"en una hora y cuarto"` (75 min) idem a (1): robaba "en una hora" (+60), "y cuarto"
     huérfano en el título, agendado 15 min antes.
  Asimetría con "en una hora" (+60, entero escrito) que sí funcionaba (c.57/c.94).
- **Prioridad**: P1 (integridad de datos del parser: vencimiento perdido o desplazado en
  captura rápida cotidiana).
- **Causa raíz**: `relativePattern` (y `fractionalRelativePattern` del c.94) solo cubren
  formas SIMPLES (entero + unidad, o fracción sola). Las compuestas "N horas y (media|
  cuarto)" y "N cuartos" no tienen rama propia → `relativePattern` gana parcial y deja
  residuo, o nada casa.
- **Solución mínima**: dos nuevos patrones procesados ANTES que `relativePattern`:
  - `compoundFractionalRelativePattern` = prefijo (`en|dentro de|de aquí a|de acá a`) +
    (número escrito un…doce | dígitos) + "horas" + "y" + (media | un cuarto | cuarto) →
    `now + amount×60 + (30|15) min`. Cubre "en una hora y media"(90), "en dos horas y
    cuarto"(135), "en 3 horas y media"(210).
  - `multiQuarterRelativePattern` = prefijo + (número escrito | dígitos) + "cuartos"
    + ("de hora")? → `now + amount×15 min`. Cubre "en tres cuartos de hora"(45),
    "en dos cuartos"(30), "en 2 cuartos de hora"(30).
  Ambos consumen la frase completa → título limpio. Incluidos en `effectiveRelativeDueAt`
  (misma prioridad, sub-hora → `relativeIsDays=false` para que la hora explícita no la
  sobreescriba). `parseWrittenNumber` reutilizado para el coeficiente (simetría con c.57).
  El prefijo es obligatorio → "reunión una hora y media" (sin prefijo) sigue siendo
  `dueAt=null` (igual que "reunión una hora" sin prefijo); no choca con recordatorios
  ("media hora antes" lo captura `reminderPatterns` antes).
- **Bugs**: P1 captura perdida/desplazada (3 formas compuestas/multi-cuarto).
- **Features**: ninguna (fix de integridad de datos).
- **Tests (TDD)**: +7 tests en `NaturalTaskParserTest.kt` para las formas compuestas
  (`enUnaHoraYMediaEsFechaRelativa90`, `enUnaHoraYCuartoEsFechaRelativa75`,
  `enDosHorasYMediaEsFechaRelativa150`, `enTresCuartosDeHoraEsFechaRelativa45`,
  `enTresCuartosSinDeHoraEsFechaRelativa45`, `dentroDeUnaHoraYMediaEsFechaRelativa90`,
  no-regresión `enUnaHoraSigueSiendoFechaRelativa60`). Probe JVM confirmó RED antes del fix
  (`due=+60 title='llamar y media'` / `due=null`) y GREEN tras (`due=+90` / `due=+45`,
  títulos limpios). Comando: `bash tools/run_domain_tests.sh` → **662 tests PASS**
  (28 clases — 655 base c.94 + 7 nuevos). Smoke: `bash tools/run_domain_checks.sh` → **25
  assertions OK**. Sin regresión (`en una hora` +60, `en media hora` +30, `media hora`
  duración 30, `media hora antes` recordatorio, `en treinta minutos` +30 siguen OK).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: probe descubrió `"cita en media hora y cuarto"` (45 min, dos
  fracciones sumadas sin entero) → `due=+30 title='cita y cuarto'` (BUG residual, P2
  baja — forma poco común; "tres cuartos de hora" ya cubierto por `multiQuarterRelative-
  Pattern`). Registrado en BACKLOG como PENDIENTE. `"en hora y media"` (sin "una") →
  `due=null` (raro, aceptable — la gente dice "una hora y media"). `"en cuarenta y cinco
  minutos"` → `due=null dur=5` (BUG preexistente del parser de duración escrita, no
  abordado — `parseWrittenNumber` no maneja números compuestos >30; registro para futuro).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver `git log` tras push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: "a la una" (hora 1 escrita standalone, BUG C pendiente del c.69);
  `"en cuarenta y cinco minutos"` (número escrito compuesto >30 en duración); fuera del
  parser — detección de compromisos en notas; `PlanEngine`/replanización si OVERLOADED
  recurrente; rutinas adaptables. (fix(parser): "en una hora y media"/"en tres cuartos de hora" ya son fecha relativa compuesta (P1))

---

## Ciclo 95 — 2026-08-14

- **Rama**: `openhands/autonomous-ordia`
- **HEAD inicial**: `bbb2e16` (c.93). Base local limpia al empezar. Al push, el remoto
  había avanzado a `18795cc` (c.94 concurrente: "en media hora"/"cuarto de hora" fecha
  relativa). Reconciliación no destructiva: `git pull` (merge) — el código (parser+tests)
  auto-mergearon limpio (áreas distintas: `fractionalRelativePattern` vs `fixedPatterns`/
  `endOfMonthPattern`); conflicto solo en `RUN_LOG.md` (colisión de nomenclatura de ciclo)
  resuelto conservando AMBOS trabajos y renumerando este ciclo a 95.
- **Problema seleccionado**: **P1 (compromiso diario olvidado + vencimiento mensual
  olvidado)** en `NaturalTaskParser`. DOS brechas simétricas a fixes previos:
  1. **"a diario"** (la frase adverbial cotidiana más común para un hábito diario en
     español: "llevar al niño al colegio a diario", "revisar correos a diario") caía a
     `rec=NONE`: el compromiso diario nacía como tarea ÚNICA sin recurrencia ni
     recordatorio periódico → rutina silenciosamente perdida. "todos los días"/"cada día"/
     "diariamente" SÍ funcionaban (c.33), pero "a diario" no estaba en el patrón.
  2. **"corte de mes"/"corte del mes"** (sinónimo latinoamericano de "fin de mes"/"cierre
     de mes": corte de caja, corte de nómina, pago de renta al corte del mes) caía a
     `dueAt=null`: el vencimiento se olvidaba (sin recordatorio ni visibilidad en
     planificador/What Now). "fin de mes"/"cierre de mes" SÍ funcionaban (c.92 añadió
     "cierre"); "corte" era la brecha residual documentada como próxima prioridad en c.93.
- **Causa raíz**:
  1. `fixedPatterns` DAILY (línea ~1591) listaba `\btodos los días\b`/`\bcada día\b`/
     `\bdiariamente\b` pero NO `\ba\s+diario\b`.
  2. `endOfMonthPattern` (línea ~179) y `monthBaseForBoundary` (línea ~1658) aceptaban
     `fin`/`finales`/`cierre`/`últim` pero NO `corte`.
- **Solución mínima (sin nueva pantalla/botón, sin enum/migración)**:
  1. Patrón DAILY: +`|\ba\s+diario\b` a la regex existente. Límites `\b` evitan colisión
     con "diario" sustantivo (cuaderno diario): solo la frase "a diario" (adverbio) activa
     DAILY.
  2. `endOfMonthPattern`: +`corte` a la alternación
     `(?:fin(?:ales|es)?|cierre|corte|últim…)`; `monthBaseForBoundary`: +`|| t.contains("corte")`
     en la rama "end". Reutiliza todo el flujo de fin de mes (detección temprana, borrado,
     resolución al último día del mes, combinable con hora explícita).
- **No-regresión**: "todos los días"/"cada día"/"diariamente" OK (testdedicated);
  "fin de mes"/"cierre de mes"/"finales de mes"/"último día del mes" OK; "mediados"/
  "principios" intactos; "corte" no colisiona con otras palabras (límites `\b`).
- **Bugs**: P1 compromiso diario olvidado ("a diario" → NONE); P1 vencimiento mensual
  olvidado ("corte de mes" → dueAt=null).
- **Features**: ninguna (fix de integridad de datos / recurrencia).
- **Tests (TDD)**: sonda JVM inicial (3 tests RED) confirmó ambas brechas; convertidos en 3
  tests permanentes en `NaturalTaskParserTest.kt`:
  `parsesADiarioRecurrence` (DAILY+1, dueAt anclado a hoy),
  `corteDeMesParsesDueAtFinDeMes` (dueAt=31/7), `corteDelMesParsesDueAtFinDeMes`
  (dueAt=31/7, forma "del"). Comando: `bash tools/run_domain_tests.sh` → **658 tests PASS**
  (28 clases — 649 base c.93 + 7 c.94 remoto + 3 propios; tras merge). Smoke: `bash tools/run_domain_checks.sh` → **25 OK**.
  Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `701f74b` (merge de integracion tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo — "cada rato"/"de vez en cuando" (recurrencia
  vaga, requiere decisión de falso positivo); detección de compromisos en notas;
  `RecurrenceEngine` edge cases; replanificación si OVERLOADED recurrente; auditoría de
  captura/búsqueda/What Now para nuevas oportunidades de producto.


---

## Ciclo 99 — 2026-08-14

- **HEAD inicial**: `5043282` (fix(parser): "el día N" → día de mes resuelto + título limpio, c.98).
- **Problema seleccionado**: P1 Parser — "este finde" (apócope coloquial singular) se capturaba
  como recurrencia semanal WEEKLY (sáb+dom para siempre) en vez de fecha única.
- **Causa raíz**: `weekendRecurrencePattern` (hábito) incluía la alternancia `este\s+` en su
  grupo opcional `(?:cada\s+)?(?:los\s+|este\s+)?findes?`. Así "este finde" casaba el patrón
  de hábito (WEEKLY), aunque el determinante singular "este" señala UN fin de semana concreto
  (fecha). Asimetría: "fin de semana" (fecha, vía `weekendPattern`) sí estaba bien, pero el
  apócope "finde" sin "de semana" solo existía en el patrón de hábito → todo "finde" era
  recurrencia, incluso el singular con "este/el/próximo".
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo):
  1. `weekendPattern` (fecha → próximo sábado) ahora acepta también el apócope "finde"
     singular con prefijo opcional este/el/próximo, con lookbehind negativo `(?<!cada\s)`
     y `(?<!los\s)` para no robar el hábito, y lookahead negativo que excluye el plural
     "findes" (hábito). Reutiliza todo el flujo existente (detección temprana `weekendEarlyMatch`,
     borrado de la frase, resolución `nextWeekday(base, SATURDAY)`, hora canónica 9:00,
     combinable con hora explícita).
  2. `weekendRecurrencePattern` (hábito) pierde la alternancia `este\s+`: ahora solo casa
     `cada`/`los`/`fines` (señal de hábito clara), nunca el singular con "este".
- **Heurística honesta**: el determinante singular "este/el/próximo" es señal desambiguadora
  real de fecha única (no IA, no random); el plural "findes" o el determinante de cadencia
  "cada"/"los" es señal de hábito.
- **Tests**: probe JVM inicial descubrió el falso positivo (`viaje este finde`→WEEKLY) y
  confirmó el fix sin regresión ("fin de semana"/"este fin de semana"/"fines de semana" intactos;
  "cada finde"/"los findes" siguen WEEKLY 6,7). +4 tests permanentes en `NaturalTaskParserTest.kt`:
  `esteFindeProgramaProximoSabadoSinRecurrencia`, `findeSueltoProgramaProximoSabadoSinRecurrencia`,
  `cadaFindeSigueSiendoHabitoSemanalFinDeSemana`, `losFindesSigueSiendoHabitoSemanalFinDeSemana`.
  Comando: `bash tools/run_domain_tests.sh` → **685 tests PASS** (681 + 4). Smoke:
  `bash tools/run_domain_checks.sh` → **25 OK**. Sin regresión.
- **Hallazgo adicional (RESUELTO c.100)**: "cada fin de semana" (forma larga + "cada") seguía
  dando `rec=NONE` (debería WEEKLY 6,7). Preexistente (verificado con git stash: idéntico en
  `5043282` sin mis cambios). "cada finde" (apócope) SÍ era hábito; la forma larga no casaba
  `weekendRecurrencePattern`. Resuelto en c.100 (FIXED → VERIFIED).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `b20a9e6` (fix(parser): "el día N" → día de mes resuelto + título limpio, c.99 → c.100).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: BACKLOG-NEW "cada fin de semana" → WEEKLY (P2, extensión de
  `weekendRecurrencePattern`); continuar descubrimiento de frases cotidianas del parser
  ("cita en media hora y cuarto" BACKLOG-16; adjetivos de cadencia desnudos P2);
  auditoría de captura/búsqueda/What Now para oportunidades de producto.

## Ciclo 100 — 2026-08-14

- **HEAD inicial**: `b20a9e6` (c.99 fix singular "este finde" → fecha; push remoto OK).
- **Problema seleccionado**: P2 Parser — "cada fin de semana" (forma larga + "cada") caía a
  `rec=NONE` (debería WEEKLY 6,7), descubierto en c.99 como hallazgo abierto. "cada finde"
  (apócope) SÍ era hábito; la forma larga "fin de semana" no. Un hábito de fin de semana
  expresado en lenguaje natural se perdía: la tarea aparecía UNA sola vez sin recurrencia y el
  recordatorio no repetía.
- **Causa raíz**: `weekendPattern` (detección temprana de fecha → próximo sábado) se ejecuta
  ANTES que `parseRecurrence` y consumía "cada fin de semana" por completo (sin distinción del
  determinante "cada"). Al borrarse la frase antes del analizador de recurrencia, esta nunca la
  veía → `weekendRecurrencePattern` solo casaba `fines de semana`/`findes?` (apócope), no la
  forma larga con "cada". Doble asimetría: "cada finde"=hábito (OK) vs "cada fin de semana"=fecha
  única (mal).
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo, reutiliza flujo existente):
  1. `weekendPattern` (fecha) gana lookbehind negativo `(?<!cada\s)` y `(?<!los\s)` en la
     rama `fin|finales de semana` (igual que ya tenía en la rama `finde`). Así "cada fin de
     semana" y "los fines de semana" NO son consumidos por la rama de fecha → llegan intactos a
     `parseRecurrence`.
  2. `weekendRecurrencePattern` (hábito) añade la alternancia `cada\s+fin\s+de\s+semana\b`.
     Reutiliza todo: `detectWeekInterval()` ("cada dos semanas los fines de semana"),
     `WEEKLY` + días 6,7, primera ocurrencia resuelta por la rama WEEKLY+days (próximo sábado).
- **Heurística honesta**: el determinante de cadencia "cada"/"los" o el plural "fines" es señal
  real de hábito; el singular sin "cada/los" ("este/el fin de semana", "finde" suelto con
  este/el) sigue siendo fecha única. Sin IA, sin random.
- **Tests**: +1 test permanente en `NaturalTaskParserTest.kt`
  (`cadaFinDeSemanaEsHabitoSemanalFinDeSemana`). Verificado sin regresión: "este finde"/"fin de
  semana"/"este fin de semana" siguen fecha única (NONE); "cada finde"/"los findes"/"fines de
  semana"/"cada fin de semana" son WEEKLY 6,7. Comando: `bash tools/run_domain_tests.sh` →
  **686 tests PASS** (685 + 1). Smoke: `bash tools/run_domain_checks.sh` → **25 OK**. Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `571d64f` (rebase sobre `c359003` del run paralelo c.99a "números
  escritos >30" — el push inicial sobre `b20a9e6` fue rechazado por divergencia; resolví con
  `git rebase origin/openhands/autonomous-ordia` (NO destructivo), conflicto solo en
  `CURRENT_STATE.md` resuelto combinando c.100 + c.99b (este finde, mío) + c.99a (números
  escritos, run paralelo); código `NaturalTaskParser.kt`/test auto-mergió limpio porque tocan
  patrones distintos. Re-probado: **693 tests PASS** (688 del remoto + mi 1 + ajuste), smoke 25
  OK. Push FF `c359003..571d64f`).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: BACKLOG-16 "cita en media hora y cuarto" (nota de precisión, baja prio);
  continuar descubrimiento de frases cotidianas del parser y auditoría de producto
  (captura/What Now/recuperación de tareas vencidas).

---

## Ciclo 101 — 2026-08-14

- **Run/ciclo**: 101 (continuación autónoma, rama `openhands/autonomous-ordia`).
- **HEAD inicial**: `571d64f` (push c.100; local == origin, sin divergencia).
- **Problema seleccionado**: BACKLOG-16 (P2 parser) — `"cita en media hora y cuarto"` dejaba
  "y cuarto" como residuo en el título y agendaba 30 min (debería 45 min). Bug confirmado por
  probe del c.100.
- **Prioridad**: P2 (precisión de captura + integridad de título). No había P0/P1 conocido.
- **Causa raíz**: `fractionalRelativePattern` (c.94) casa solo "en media hora" (+30) y no
  contempla el sufijo "+ cuarto" sobre una fracción sin número entero. El
  `compoundFractionalRelativePattern` (c.94b) exige número+"horas" antes del "y", así no casa
  con "media hora y cuarto". Resultado: frase rota, residuo "y cuarto" en el título,
  vencimiento 15 min antes de lo pedido.
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo, reutiliza el flujo existente): nuevo
  `fractionalAndQuarterRelativePattern = (en|dentro de|de aquí a|de acá a) +
  (media hora|(un )?cuarto (de )?hora) + "y cuarto"` → `now + base + 15` min (base=30 si
  "media", 15 si "cuarto"). Se procesa ANTES que `fractionalRelativePattern` (roba la frase
  completa, sin residuo) y antes de la duración. Incluido en `effectiveRelativeDueAt`
  (prioridad sobre `fractionalRelativeDueAt`) y en la condición de exclusión de
  `relativeIsDays` (sub-hora). Prefijo obligatorio: "reunión media hora" (duración real)
  sigue siendo `durationMinutes=30` (no-regresión c.94); "media hora antes" (recordatorio)
  lo captura `reminderPatterns` (no choca).
- **Tests**: +4 tests en `NaturalTaskParserTest.kt`
  (`enMediaHoraYCuartoEsFechaRelativaDe45Min`, `enUnCuartoDeHoraYCuartoEsFechaRelativaDe30Min`,
  `dentroDeMediaHoraYCuartoEsFechaRelativaDe45Min`, `deAquiAMediaHoraYCuartoEsFechaRelativaDe45Min`).
  Comando: `bash tools/run_domain_tests.sh` → **697 tests PASS** (693 + 4). Smoke
  (`bash tools/run_domain_checks.sh` con PATH de kotlinc) → **25 OK**. Sin regresión:
  "en media hora"=+30, "en un cuarto de hora"=+15, "en una hora y cuarto"=+75,
  "en una hora y media"=+90, "en tres cuartos de hora"=+45 todos intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **CURRENT_STATE**: reescrito (reescribir, no acumular) — se eliminaron las ~12 secciones
  "Último trabajo" duplicadas históricas (c.99-c.91...) que violaban la guía; el historial
  vive en RUN_LOG.md. Queda: Estado (resumen conciso c.101+c.100+c.99...) + una sección
  "Último trabajo — Ciclo 101".
- **BACKLOG**: BACKLOG-16 marcado FIXED → VERIFIED (ciclo 101).
- **Commits**: (pendiente de push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: continuar descubrimiento de frases cotidianas del parser y auditar
  producto (captura ultrarrápida, What Now, recuperación de vencidas, inbox inteligente).

## Ciclo 103 — 2026-08-14 — Parser: fracciones compuestas plurales "+ tres cuartos" y "+ y cuarto" sobre multi-cuarto (P1)

- **HEAD inicial**: `84f634c` (push c.101, local == origin al iniciar). **Base obsoleta detectada**: al hacer `git fetch` el remoto había avanzado a `0285a20` (c.102 de otra ejecución: fix parser "a última hora"). STALE_RUN reconstruido de forma segura: stash de mi trabajo → fast-forward a `0285a20` (hijo directo, sin divergencia destructiva) → stash pop → resolución del único conflicto (CURRENT_STATE.md, docs). Mi trabajo pasa a numerarse **c.103** para no colisionar con el c.102 remoto ya existente.
- **Problema seleccionado**: dos bugs P1 parser descubiertos por probe JVM del c.101:
  (A) `"en una hora y tres cuartos"` (60+45=105 min) dejaba "y tres cuartos" como residuo en
  el título y agendaba +60 (45 min antes). (B) `"en tres cuartos de hora y cuarto"`
  (3+1=4 cuartos=60 min) dejaba "y cuarto" como residuo y agendaba +45 (15 min antes).
- **Prioridad**: P1 (precisión de captura + integridad de vencimiento + título limpio).
  No había P0 conocido.
- **Causa raíz (A)**: `compoundFractionalRelativePattern` (c.94b) solo admitía
  `media|un cuarto|cuarto` como fracción final, NO los plurales "tres cuartos"/"dos
  cuartos", así caía a `relativePattern` que robaba solo "en una hora" (+60) dejando
  "y tres cuartos" como residuo. Asimetría: `multiQuarterRelativePattern` ("en tres
  cuartos de hora") sí funcionaba, pero la forma con N horas + plural no.
- **Causa raíz (B)**: `multiQuarterRelativePattern` (c.94b) robaba solo "en tres cuartos
  de hora" (+45) y no consumía el sufijo "+ y cuarto", que quedaba como residuo. Análogo
  al c.101 ("media hora y cuarto") pero sobre la rama multi-cuarto.
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo, reutiliza el flujo existente):
  (A) `compoundFractionalRelativePattern` añade `tres\s+cuartos|dos\s+cuartos` al grupo
  de fracción; el resolver suma (45 si "tres" | 30 si "dos" | 30 si "media" | 15 si
  "cuarto") min. (B) `multiQuarterRelativePattern` añade sufijo opcional
  `(?:\s+y\s+cuarto)?`; el resolver detecta el sufijo en `match.value` y suma +1 cuarto (15
  min) extra. Ambos reutilizan TODO el flujo existente (`parseWrittenNumber`,
  `effectiveRelativeDueAt`, `relativeIsDays=false`). Verificado que NO choca con el fix
  "a última hora" del c.102 remoto (distintas funciones/ramas del parser).
- **Tests**: +4 tests en `NaturalTaskParserTest.kt`
  (`enUnaHoraYTresCuartosEsFechaRelativaDe105Min`,
  `enDosHorasYDosCuartosEsFechaRelativaDe150Min`,
  `enTresCuartosDeHoraYCuartoEsFechaRelativaDe60Min`,
  `enDosCuartosDeHoraYCuartoEsFechaRelativaDe45Min`). Comando: `bash tools/run_domain_tests.sh`
  → **709 tests PASS** (705 base c.102 + 4 c.103). Smoke
  (`PATH=/tmp/kotlinc-home/kotlinc/bin:$PATH bash tools/run_domain_checks.sh`) → **25 OK**.
  Sin regresión: "en una hora y media"=+90, "en una hora y cuarto"=+75, "en tres cuartos
  de hora"=+45, "en dos cuartos de hora"=+30 todos intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **BACKLOG**: dos entradas nuevas (c.103) marcadas FIXED → VERIFIED.
- **Commits**: `eda7fdd` (push a `openhands/autonomous-ordia`).
- **HEAD final**: `eda7fdd`.
- **Estado**: FIXED → VERIFIED (dominio JVM); STALE_RUN reconstruido de forma segura.
- **Próxima prioridad**: continuar descubrimiento de frases cotidianas del parser y auditar
  producto (captura ultrarrápida, What Now, recuperación de vencidas, inbox inteligente).

## Ciclo 104 — 2026-08-14 — Parser: "en/dentro de/de aquí a un rato" ahora +1h (P1)

- **HEAD inicial**: `eda7fdd` (c.103, local == origin al iniciar).
- **Problema seleccionado**: bug P1 parser — las frases cotidianas vagas de futuro `"en un rato"`,
  `"dentro de un rato"`, `"de aquí a un rato"`, `"de acá a un rato"` producían `dueAt=null`
  (tarea olvidada, sin recordatorio). El usuario capturaba "llamar en un rato" y Ordía no agendaba
  nada. Asimetría: el pasado `"hace un rato"` (−3h) SÍ se parseaba (`agoPattern`), pero la
  contraparte futura no. "un rato" no es número entero ni fracción canónica → no casa en
  `relativePattern`/`fractionalRelativePattern` → cae sin fecha.
- **Causa raíz**: ausencia de un patrón para la expresión relativa vaga futura. "Un rato" es
  impreciso por naturaleza, pero ignorarlo = olvidar la tarea; la heurística honesta +1h
  (mismo orden que "hace un rato"→−3h en magnitud cotidiana, pero hacia el futuro) la recupera
  sin fingir precisión. Heurística descrita honestamente en comentario (no es IA).
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo, reutiliza el flujo existente):
  (1) nuevo `vagueRelativePattern = prefijo (en|dentro de|de aquí a|de acá a) + "un rato"`
  declarado ANTES que `relativePattern` (roba la frase completa, sin residuo en el título);
  (2) bloque de procesamiento: `vagueRelativeDueAt = now + 60*60_000L` (+1h) y se elimina el
  match del `working` (igual que los demás relativos); (3) incluido en `effectiveRelativeDueAt`
  (prioridad tras `relativeDueAt`, antes de `fractionalAndQuarterRelativeDueAt`); (4)
  deliberadamente EXCLUIDO de `relativeIsDays` (es horas, no días — preserva el comportamiento
  de hora explícita). Reusa TODO el flujo (explicitTime, reminder, recurrence, duration).
- **Bug encontrado durante verificación (probe)**: la constante inicial era `60L*60*60_000L`
  = 216,000,000 ms = **60 horas** (no 1 hora). El test pasaba por usar la misma constante
  errónea. Corregido a `60*60_000L` = 3,600,000 ms = 1 h. Re-ejecutado probe JVM end-to-end:
  "llamar en un rato"→+1h título "llamar", "pausa dentro de un rato"→+1h título "pausa",
  "cita de aquí a un rato"→+1h título "cita". Confirmado delta_h=1.0 (no 60.0).
- **Tests**: +3 tests en `NaturalTaskParserTest.kt`
  (`enUnRatoEsFechaRelativaDe1Hora`, `dentroDeUnRatoEsFechaRelativaDe1Hora`,
  `deAquiAUnRatoEsFechaRelativaDe1Hora`). Comando: `bash tools/run_domain_tests.sh`
  → **712 tests PASS** (709 base c.103 + 3 c.104). Smoke
  (`PATH=/tmp/kotlinc-home/kotlinc/bin:$PATH bash tools/run_domain_checks.sh`) → **25 OK**.
  Sin regresión: "en una hora"=+60min, "en media hora"=+30, "hace un rato"=−3h intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `31ccb79` (push a `openhands/autonomous-ordia`).
- **HEAD final**: `31ccb79`.
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: continuar descubrimiento de frases cotidianas del parser; auditar
  recuperación de tareas olvidadas y "un rato" en otros contextos (recordatorios).

---


## Ciclo 105 — 2026-08-14 — GuardianCoach: edad de "olvidada" por días de calendario (DST-safe) + auditoría `\b`-acento del parser completada (P1 recuperación)

- **Branch**: `openhands/autonomous-ordia`.
- **HEAD inicial**: `eda7fdd` (c.103, fracciones compuestas plurales).
- **Problema (P1, recuperación de tareas olvidadas)**: `GuardianCoach.insight` calculaba la
  antigüedad de la tarea más atrasada con `((now - dueAt) / MILLIS_PER_DAY).toInt()` —
  milisegundos crudos / 24 h. Esto NO cuenta días de calendario: una tarea vencida hace N días
  consultada ANTES de la hora de su vencimiento (p. ej. hace 2 días vista a las 7:00, cuando
  vencía a las 9:00) da `now - dueAt = 2·24h - 2h = 46h` → `/24 = 1` → `coerceAtLeast(1)=1` < 2
  → `Tone.GENTLE` en vez de `FOCUSED`. El nudge de recuperación ("RECUPERA EL CONTROL",
  decisión hacer/reprogramar/quitar) NO aparecía para una tarea realmente olvidada de 2 días.
  Además era frágil al horario de verano (DST): un "día natural" no siempre son 24 h, así que
  cruzando un cambio de horario la edad podía desajustarse ~1 día y el umbral FOCUSED (≥2)
  clasificar mal.
- **Causa raíz**: la heurística usaba aritmética de milisegundos en vez de días de calendario
  en la zona del usuario, mezclando "cuánto tiempo exacto" con "cuántos días lleva", que es como
  la persona y el `forgottenAgeLabel` ("días"/"semanas"/"meses") cuentan el atraso.
- **Solución (mínima, `GuardianCoach.kt`, sin nueva pantalla/botón)**: nueva
  `private fun overdueDays(dueAt, today, zone) = ChronoUnit.DAYS.between(localDate(dueAt), today)`
  (días de calendario entre la fecha local de vencimiento y hoy, en la zona del usuario).
  Sustituye el `((now - it) / MILLIS_PER_DAY).toInt()` del `mostOverdueDays`. Elimina
  `MILLIS_PER_DAY` (ya sin usos). Reutiliza TODO el flujo existente: `forgottenAgeLabel`,
  `FORGOTTEN_DAYS_THRESHOLD`, `Tone.FOCUSED` vs `GENTLE`. Es correcta aunque se consulte antes
  de la hora del vencimiento y es DST-robusta (`ChronoUnit.DAYS` opera sobre `LocalDate`, ignora
  los 23/25 h del DST). No-regresión: vencidas el mismo día o el anterior siguen contando 1;
  los tests de 4 y 14 días (Santo Domingo, al mediodía) siguen dando "4 días"/"2 semanas".
- **Auditoría `\b`-acento del parser (familia recurrente c.91/c.102)**: escaneados los 76
  patrones regex de `NaturalTaskParser.kt`. Resultado: NINGÚN `\b` queda inmediatamente antes de
  una letra acentuada ni tras un grupo cuya última rama termine en acento. Los casos conocidos ya
  estaban resueltos con lookbehind Unicode-safe: c.91 "último día del mes" → `(?<!\p{L})`;
  c.102 "última hora" → `(?<![a-záéíóúñ])`. La familia NO reaparece a nivel de patrón; se
  descarta seguir cazando `\b` fantasmas (sería actividad fabricada). Regla para futuros
  patrones: si un `\b` va delante de una palabra con acento inicial, usar `(?<![a-záéíóúñ])` o
  `(?<!\p{L})`.
- **Tests**: +2 en `GuardianCoachTest.kt`: `twoDaysOverdueBeforeDueTimeIsStillFocused` (vencida
  hace 2 días consultada a las 7 a.m. → FOCUSED + "2 días"; antes GENTLE por el bug),
  `overdueAgeIsCalendarDayCountAcrossDstBoundary` (zona `America/New_York`, tramo cruza el
  forward DST del 8-mar-2026, hace 3 días → "3 días"). Comando: `bash tools/run_domain_tests.sh`
  → **711 tests PASS** (709 base c.103 + 2). Smoke
  (`PATH=/tmp/kotlinc-home/kotlinc/bin:$PATH bash tools/run_domain_checks.sh`) → **25 OK**.
  Sin regresión: los 6 tests previos de `GuardianCoachTest` siguen verdes.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
  Render real del coach en la app no probado en dispositivo.
- **Hallazgos adicionales (descubrimiento continuo)**: la misma trampa `(now - dueAt)/24h` para
  "días" podría existir en otras heurísticas; `SearchEngine.taskMatchesDateScope` ya usa
  `LocalDate` correctamente (no afecta). Próxima revisión: `RecurrenceEngine` clamps/DST y
  detección de compromisos en notas; producto: captura ultrarrápida, inbox inteligente.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/GuardianCoach.kt`,
  `app/src/test/java/com/ordia/app/domain/GuardianCoachTest.kt`,
  `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **Commits**: (pendiente de push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: auditar `RecurrenceEngine` (clamps/DST) y detección de compromisos en
  notas; explorar producto (captura ultrarrápida, What Now, inbox inteligente).

---


## Ciclo 105 (run B) — 2026-08-14 — Parser: familia vaga "un momento"/"al rato"/"pasado un rato"

- **HEAD inicial**: `07a1f31` (tras docs c.104).
- **Problema (P1)**: extensión natural del c.104. Probe de descubrimiento continuo reveló que
  `"en un momento"`, `"dentro de un momento"`, `"al rato"`, `"pasado un rato"` producían
  `dueAt=null` (tarea olvidada, sin recordatorio). Misma asimetría que c.104: el pasado
  `"hace un rato"` (−3h) SÍ se parseaba, pero `"un momento"`/`"al rato"` (futuro) no casaban
  ningún patrón (`relativePattern` requiere número+unidad, `fractionalRelativePattern`
  requiere fracción canónica). "un momento"/"al rato" son imprecisos por naturaleza.
- **Causa raíz**: `vagueRelativePattern` (c.104) solo cubría la forma literal "un rato" con
  prefijo; dejaba fuera sinónimos cotidianos de la misma familia semántica.
- **Solución**: ampliar `vagueRelativePattern` a `(?:un rato|un momento)` tras el prefijo
  `en|dentro de|de aquí a|de acá a` + dos ramas sin prefijo: `al rato` y `pasado un rato`.
  Reutiliza TODO el flujo existente (`vagueRelativeDueAt = now + 60*60_000L`, +1h, heurística
  honesta descrita en el docstring, no IA; incluido en `effectiveRelativeDueAt`; excluido de
  `relativeIsDays`). Sin nueva pantalla/botón. TDD: probe RED → implementación → tests GREEN.
- **Tests**: `bash tools/run_domain_tests.sh` → **716 OK** (712 base c.104 + 4 nuevos:
  `enUnMomento`/`dentroDeUnMomento`/`alRato`/`pasadoUnRato` = +1h). Smoke
  (`PATH=/tmp/kotlinc-home/kotlinc/bin:$PATH bash tools/run_domain_checks.sh`) → **25 OK**.
  Sin regresión: "en una hora"=+1h, "en media hora"=+0.5h, "en 2 horas"=+2h, "en un mes"=+720h,
  "hace un rato"=−3h intactos (probe verde).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver hash tras push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo de frases cotidianas del parser
  ("enseguida"/"ahora mismo" semántica "ahora"; "más rato"/"más tarde" vagos) y otras áreas
  (recuperación de tareas olvidadas, What Now, contexto).


---

## Ciclo 106 — 2026-08-14 — Parser: "enseguida"/"en seguida" (adverbio de inmediatez) + resolución de colisión con ejecución paralela

- **HEAD inicial**: `f55c056` (obsoleto; remoto ya avanzó a `b5e195a` = ciclo 105 "un momento"/"al rato"/"pasado un rato" de otra ejecución).
- **STALE_RUN / colisión**: mi trabajo local inicial (patrón separado `imminentColloquialPattern` para "enseguida"/"al rato"/"en seguida") se solapaba con el remoto b5e195a, que resolvió el mismo problema de "al rato" expandiendo el `vagueRelativePattern` existente. Mi implementación duplicaba "al rato" en un patrón aparte (redundante, más código). Resolución segura: descarte del trabajo local obsoleto (`git reset HEAD` + `git checkout --` sin `reset --hard`) + fast-forward a `b5e195a` + re-implementación de SOLO "enseguida"/"en seguida" (el valor único no cubierto por el remoto), añadiéndolo al MISMO `vagueRelativePattern` para evitar duplicación.
- **Problema (P1)**: los adverbios cotidianos de inmediatez **"enseguida"** (una palabra) y **"en seguida"** (dos palabras) NO casaban ningún patrón → `dueAt=null` + residuo en el título → tarea olvidada (sin recordatorio, invisible en What Now/planificador). Asimetría: "al rato"/"un momento"/"pasado un rato" SÍ eran +1h desde c.105, pero "enseguida"/"en seguida" son adverbios puros sin sustantivo de cantidad → la rama del patrón no los cubría. El propio RUN_LOG del c.105 los listaba como próxima prioridad de descubrimiento.
- **Causa raíz**: `vagueRelativePattern` (c.104/105) agrupaba (a) prefijo `en|dentro de|de aquí a|de acá a` + `un rato|un momento` y (b) `al rato|pasado un rato`. "enseguida"/"en seguida" no encajan en ninguna rama (no llevan "un rato"/"un momento" ni son "al rato"/"pasado un rato").
- **Solución (mínima)**: extender el `vagueRelativePattern` existente con la alternancia `|en\s*seguida|enseguida`, reutilizando TODO el flujo de c.104/105 (match → `vagueRelativeDueAt = now + 1h`, frase consumida → título limpio). `en\s*seguida` cubre "en seguida" (con espacio); `enseguida` la forma compacta; el `\b` final ya existente asegura el límite. Sin nueva pantalla/botón, sin patrón separado (evita duplicación con el remoto).
- **Tests**: `bash tools/run_domain_tests.sh` → **720 PASS** (718 b5e195a + 2 nuevos: `enseguidaEsFechaRelativaDe1Hora`, `enSeguidaSeparadoEsFechaRelativaDe1Hora`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin regresión: "al rato"/"un momento"/"pasado un rato" (c.105) intactos, "en media hora"=+30, "en una hora"=+60 intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver hash tras push).
- **HEAD final**: (tras push).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo de frases cotidianas del parser ("ahora mismo"/"en cualquier momento" semántica "ahora"; "más rato"/"más tarde" vagos) y otras áreas (recuperación de tareas olvidadas, What Now, contexto, inbox inteligente).



---

## Ciclo 107 — 2026-08-14 — Parser: "ahora" inmediato ("ahora mismo"/"ahorita"/"lo antes posible"…) → dueAt=now + resolución de colisión con c.106 paralelo

- **HEAD inicial**: `b5e195a` (obsoleto; remoto ya avanzó a `30b62d5` = ciclo 106 "enseguida"/"en seguida" de otra ejecución paralela).
- **STALE_RUN / colisión**: mi trabajo local (un `nowPattern` que inicialmente incluía `enseguida|en seguida`) se solapaba con el remoto `30b62d5`, que resolvió "enseguida"/"en seguida" → +1h en el `vagueRelativePattern` existente (procesado ANTES que mi `nowPattern`). Resolución segura (sin `reset --hard`/force): `git stash push -u` + `git merge --ff-only origin/openhands/autonomous-ordia` (→ `30b62d5`) + `git stash pop` (sin conflictos) + reconciliación semántica: quité `enseguida|en seguida` de mi `nowPattern` y eliminé sus 2 tests (`enseguidaVenceAhoraYLimpiaTitulo`, `enSeguidaSeparadoVenceAhoraYLimpiaTitulo`) para NO sobrescribir el trabajo válido de la otra ejecución ni diverger en semántica (+1h vs now). Mi valor único intacto: `ahorita|ahora mismo|ahora|lo más pronto/temprano posible|lo antes posible|cuanto antes|a la brevedad`.
- **Problema (P1)**: las frases cotidianísimas que significan literalmente "ya" — **"ahora mismo"/"ahorita"/"ahora" (suelto)/"lo antes posible"/"cuanto antes"/"a la brevedad"/"lo más pronto/temprano posible"** — NO casaban ningún patrón → `dueAt=null` → tarea SIN vencimiento, invisible en "What Now"/planificador, sin recordatorio programable → olvidada (P1). Asimetría: la familia vaga ("enseguida"/"al rato"/"un momento", +1h) ya estaba cubierta (c.104/105/106), pero "ahora"/"ahorita"/"lo antes posible"/"cuanto antes"/"a la brevedad" no. Diferencia semántica clave: estas frases significan "ya/ahora mismo", no "en ~1h", así que resolverlas a +1h sería agendar una hora DESPUÉS de lo que el usuario pidió → se resuelven a `now` para sacar la tarea a la superficie de inmediato. El propio RUN_LOG del c.106 las listaba como próxima prioridad ("ahora mismo"/semántica "ahora").
- **Causa raíz**: ningún patrón existente casaba "ahora" puro o las fórmulas "lo antes posible"/"cuanto antes"/"a la brevedad". "ahora" sola y "ahorita" no son cantidades relativas (`relativePattern`) ni vagas (`vagueRelativePattern` exige "un rato"/"un momento"); las fórmulas idiomáticas no son fechas ni horas canónicas.
- **Solución (mínima)**: nuevo `nowPattern` (regex `\b(?:ahorita|ahora\s+mismo|ahora|lo\s+m[áa]s\s+(?:pronto|temprano)\s+posible|lo\s+antes\s+posible|cuanto\s+antes|a\s+la\s+brevedad)\b`) declarado junto a `vagueRelativePattern`; match → `nowDueAt = now`, frase consumida → título limpio; incluido en `effectiveRelativeDueAt` (después de `vagueRelativeDueAt`: las vagas +1h solo ganan si coinciden, el "ahora" puro cae aquí); excluido de `relativeIsDays` (sub-hora, no se combina con hora explícita). Heurística honesta descrita en comentario, no IA. Sin nueva pantalla/botón.
- **Tests**: `bash tools/run_domain_tests.sh` → **727 PASS** (720 base `30b62d5` + 7 nuevos: `ahoraMismoVenceAhoraYLimpiaTitulo`, `ahoritaVenceAhoraYLimpiaTitulo`, `ahoraSoloVenceAhoraYLimpiaTitulo`, `loAntesPosibleVenceAhoraYLimpiaTitulo`, `cuantoAntesVenceAhoraYLimpiaTitulo`, `aLaBrevedadVenceAhoraYLimpiaTitulo`, `loMasProntoPosibleVenceAhoraYLimpiaTitulo`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin regresión: "enseguida"=+1h (c.106 intacto), "en un rato"=+1h, "en media hora"=+30min, "mañana a las 9" intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `2f5b418` (feat(parser): "ahora mismo"/"ahorita"/"ahora"/"lo antes posible"/"cuanto antes"/"a la brevedad" ahora son dueAt=now (P1)). Push OK `30b62d5..2f5b418`.
- **HEAD final**: `2f5b418`.
- **Estado**: FIXED → VERIFIED (dominio JVM); STALE_RUN reconstruido de forma segura y no destructiva.
- **Próxima prioridad**: descubrimiento continuo — "más tarde"/"más rato"/"en cualquier momento" (vago futuro sin hora, ¿+3h?¿hoy tarde?), "tan pronto como sea posible"/"cuanto antes mejor"; otras áreas: recuperación de tareas olvidadas (What Now/Guardián), inbox inteligente, contexto, onboarding.


## Ciclo 108 — 2026-08-14 — Parser: "más tarde"/"más rato"/"después" (vago futuro) → dueAt=now+3h (P1 captura/agenda)

- **HEAD inicial**: `bad2dda` (rama `openhands/autonomous-ordia`, sincronizada con remoto; base NO obsoleta — pull --ff-only limpio, sin colisión con ejecuciones paralelas).
- **Problema (P1)**: los adverbios cotidianísimos de "luego, no ahora pero hoy mismo" — **"más tarde"/"más rato"/"después" (con o sin tilde, suelto)** — NO casaban ningún patrón → `dueAt=null` → tarea SIN vencimiento, invisible en "What Now"/planificador, sin recordatorio programable → olvidada (P1). Asimetría: "ahora"=now (c.107), "un rato"/"enseguida"=+1h (c.104/105/106), pero "más tarde"/"después" no casaban. El propio RUN_LOG del c.107 las listaba como próxima prioridad. Diferencia semántica: significa "luego, hoy mismo pero no ya" → intervalo MAYOR que el vago (+1h) y no "ya", se resuelve a +3h (≈ "esta tarde/más tarde hoy").
- **Causa raíz**: ningún patrón existente casaba "más tarde"/"más rato"/"después" puros. No son cantidades relativas (`relativePattern` exige "en N …") ni vagas inmediatas (`vagueRelativePattern` exige "un rato"/"un momento"/"enseguida"); tampoco son "ahora" puro (`nowPattern`). Caían al default sin fecha.
- **Solución (mínima)**: nuevo `laterRelativePattern` (regex `\b(?:(?:m[aá]s\s+(?:tarde|rato)|despu[eé]s)(?!\s+(?:de\b|del\b|de\s+la\b)))\b`) declarado junto a `nowPattern`; match → `laterRelativeDueAt = now + 3*60*60_000L` (+3h), frase consumida → título limpio; incluido en `effectiveRelativeDueAt` (después de `nowDueAt`, sub-hora); excluido de `relativeIsDays`. El **lookahead negativo** `(?!\s+(?:de|del|de la))` evita robar "después del/de la N" (dependencia/evento, p. ej. "llamar después del almuerzo" debe quedar como título sin fecha) y "después de N minutos/horas" (lo cubre `relativePattern`). Heurística honesta (+3h ≈ "esta tarde") descrita en comentario, no IA. Sin nueva pantalla/botón. + fix de un comentario obsoleto (decía "enseguida", corregido).
- **Tests**: `bash tools/run_domain_tests.sh` → **733 PASS** (727 c.107 + 6 nuevos: `masTardeVenceMasTardeYLimpiaTitulo`, `masRatoVenceMasTardeYLimpiaTitulo`, `despuesSueltoVenceMasTardeYLimpiaTitulo`, `despuesSinTildeVenceMasTardeYLimpiaTitulo`, `masTardeSinTildeVenceMasTardeYLimpiaTitulo`, `despuesDelAlmuerzoNoEsAdverbioSuelto`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe amplio (9 casos) verde: "más tarde"/"más rato"/"después"/"despues"/"mas tarde" → +3h (15:00) título limpio; "después del almuerzo" → null título intacto. Sin regresión: "ahora"=now, "enseguida"=+1h, "en media hora"=+30min, "mañana a las 9" intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `4ffe760` (feat(parser): "más tarde"/"más rato"/"después" ahora +3h (P1)). Push OK `bad2dda..4ffe760`.
- **HEAD final**: `4ffe760`.
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Commits**: `10bd857` (rebase sobre `a589560` remoto c.109 "hoy tarde"; commit original `6bd66a3` reescrito 2x por rebase: `9350fcb` sobre `0b91a1b`, luego `10bd857` sobre `a589560`)
- **HEAD final**: `10bd857` (previo al push; se actualizara si el rebase remoto lo reescribe).

## Ciclo 110 — 2026-08-14 — Parser: "manana" sin tilde NO reconocido como fecha relativa + fix backlog "de madrugada/de noche/de tarde"

- **HEAD inicial**: `30b62d5` (c.106 "enseguida"/"en seguida"). STALE_RUN reconstruido seguro: tras commit local `6bd66a3`, push rechazado -- remoto avanzo a `0b91a1b` (c.107 "ahora mismo"/"ahorita" de otra ejecucion + c.108 "mas tarde"/"despues"). Rebase no destructivo de `6bd66a3` sobre `0b91a1b`: codigo (NaturalTaskParser.kt + test) auto-merge limpio (mis cambios de acentos y los remotos de "ahora"/"mas tarde" son ortogonales); conflictos SOLO en docs AI_AUTONOMY (memoria acumulativa) resueltos conservando AMBOS lados y renumerando mi entrada c.107->c.110 (el remoto ya uso c.107/108/109). Sin force push, sin reset --hard.
- **Problema (P1, pérdida de datos móvil)**: **"manana" sin tilde** NO se reconocía como fecha relativa "mañana" en la mayoría de patrones del parser — las regex usaban `mañana` literal (con tilde), no `ma[nñ]ana`. En móvil la escritura sin tilde es la norma (teclado sin acentos rápidos). Consecuencias:
  - "llamar manana" → `dueAt=null` + residuo "manana" en el título → **cita olvidada** (sin recordatorio, invisible en What Now/planificador).
  - "pasado manana" → +1 en vez de +2 → **cita adelantada un día**.
  - "12 de la manana" → 12:00 (mediodía) en vez de 00:00 (medianoche) → **asimetría con "12 de la mañana" con tilde** (que sí daba 00:00).
  - El detector `hasStandaloneManana` (línea ~1958) usaba `mañana` literal → "en la manana a las 4" contaba "manana" como fecha → la cita caía a MAÑANA con hora 04:00.
  - Asimetría flagrante con "próximo"/"sábados" sin tilde (ya corregidos c.88/c.89): la familia de tolerancia a acentos estaba incompleta para la palabra más usada del parser ("mañana").
- **Causa raíz**: los patrones de fecha relativa, de limpieza del título, de "pasado/antepasado mañana", el detector `hasStandaloneManana` y "para mañana" todos escribían `mañana` con tilde. El meridiem resuelto en `explicitTimeData` comparaba cadenas crudas ("delamañana"/"delamanaana" — esta última con doble 'a', nunca casaba) sin normalizar ñ→n/í→i.
- **Solución (mínima, sin nueva pantalla/botón)**:
  1. Unificación `mañana`→`ma[nñ]ana` en TODOS los patrones relevantes: rama `when` de fecha relativa, regex de limpieza del título (línea ~1499), "pasado/antepasado mañana" (línea ~1095 + limpieza), `hasStandaloneManana` (línea ~1971), "para mañana" (línea ~1516).
  2. Normalización `ñ→n`/`í→i` del meridiem resuelto en `explicitTimeData`: `mer = meridiem.lowercase().replace("ñ","n").replace("í","i")` casa "de la manana" con "de la mañana" → `delamanana`/`delamediodia` unificados; elimina la asimetría "12 de la manana"=00:00 y simplifica las comparaciones isPm/isAm (una sola forma en vez de dos con/sin tilde).
  3. Fix P3 backlog (BACKLOG-P3-PARSER-1) en el mismo ciclo: `standalonePartOfDayPattern` ampliado con rama `de\s+(tarde|noche|madrugada)` (conector "de" suelto, adverbios "salir de madrugada"/"trabajar de noche"/"jugar tenis de tarde"). **NO** aplica a "de mañana" porque colisionaría con la fecha relativa "mañana" ("reunión de mañana" debe seguir siendo mañana, no 09:00 hoy). Dos grupos de captura (g1 para las formas con "la", g2 para "de" suelto); el resolvedor usa `g1.ifBlank { g2 }`.
- **Tests**: `bash tools/run_domain_tests.sh` → **743 PASS** (rebase: 733 c.108 remoto + 10 mios: 4 deMadrugada/Noche/Tarde + 6 reconciliados "ahora"/"mas tarde" auto-merged); pre-rebase 730 PASS (726 c.106 + 4) (726 c.106 + 4 nuevos: `deMadrugadaSetsCanonicalHour`=04:00, `deNocheSetsCanonicalHour`=21:00, `deTardeSetsCanonicalHour`=15:00, `deMadrugadaWithDateKeepsDate`=mañana 04:00). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin regresión: probe JVM verde ("reunión de mañana"=2026-08-15 09:00 fecha relativa preservada, "reunión de manana"=idem, "reunión de tarde"=15:00 hoy, "salir de madrugada"=04:00 título limpio "salir", "salir de noche"=21:00, "trabajar de noche"=21:00 título "trabajar", "pasado manana"=+2, "antepasado manana"=+3, "12 de la manana"=00:00 simétrico a "12 de la mañana").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `e0faad8` (rebase sobre `0b91a1b` remoto; commit original `6bd66a3` reescrito por rebase).
- **HEAD final**: (tras push, ver abajo).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: descubrimiento continuo de frases cotidianas del parser ("ahora mismo"/"en cualquier momento" semántica "ahora"; "más rato"/"más tarde" vagos; tolerancia a acentos en otras palabras comunes — auditar "última"→"ultima" en "última hora", "próxima"→"proxima" en "próxima semana" en SearchEngine) y otras áreas (recuperación de tareas olvidadas, What Now, contexto, inbox inteligente).

## Ciclo 111 — 2026-08-14 — Parser: REGRESIÓN de integración c.109+c.110 — `compactDayPartOfDayPattern` aún `mañana` literal → "manana noche" agenda 09:00 + título sucio

- **HEAD inicial**: `0d807c8` (c.110 "manana" sin tilde, ya pushed).
- **Problema (P1, regresión de integración, captura/agenda errónea + título sucio)**: el patrón `compactDayPartOfDayPattern` —introducido en c.109 (remoto, "hoy tarde" compacta)— seguía usando `mañana` **literal** (con tilde). La unificación de acentos de c.110 (`mañana`→`ma[nñ]ana`) recorrió la rama `when` de fecha relativa, limpieza del título, "pasado/antepasado mañana", `hasStandaloneManana` y "para mañana", pero **omitrió** `compactDayPartOfDayPattern`. Así **"comprar pan manana noche"** (sin tilde, norma en escritura móvil rápida) → `due=mañana 09:00` (default) + residuo **"noche" en el título**, en vez de `mañana 21:00` + título limpio. Agenda errónea (21:00→09:00, 12 h de diferencia) Y título sucio = P1 de datos/captura. Asimetría flagrante: **"comprar pan mañana noche"** (con tilde) SÍ funcionaba (21:00, título limpio); la sin tilde no.
- **Causa raíz**: c.109 y c.110 se hicieron en ejecuciones separadas; la auditoría de acentos de c.110 no cubrió el patrón recién añadido por c.109. El `compactDayPartOfDayPattern` contiene `mañana` en tres de sus cuatro ramas y todas quedaron con tilde.
- **Descubrimiento**: probe JVM ad-hoc (19 frases cotidianas) detectó el bug. La auditoría sistemática de `mañana` literal restante en el parser (`grep -n "mañana"`) reveló que los `mapOf` ya tenían ambas formas (con/sin tilde), pero el PATRÓN regex `compactDayPartOfDayPattern` no.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: las tres ramas `mañana` de `compactDayPartOfDayPattern` pasan a `ma[nñ]ana`, idéntico al resto de la unificación de c.110. Sin cableado nuevo (el group capturado es `tarde|noche|madrugada`, sin tilde, ya maneado por `compactDayPartOfDayTimes`/`hasPartOfDayPmContext`).
- **Tests**: `bash tools/run_domain_tests.sh` → **754 PASS** (750 c.110 + 4 nuevos de paridad sin tilde: `mananaSinTildeTardeEsManana15hYLimpiaTitulo`, `mananaSinTildeNocheEsManana21hYLimpiaTitulo`, `pasadoMananaSinTildeNocheEsPasadoManana21hYLimpiaTitulo`, `antepasadoMananaSinTildeMadrugadaEsDosDias4hYLimpiaTitulo`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM paridad total con/sin tilde ("mañana noche"/"manana noche"=21:00, "pasado manana noche"=+2 21:00, "antepasado manana madrugada"=+3 04:00, todos título limpio). Sin regresión: formas con tilde intactas.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver abajo tras push).
- **HEAD final**: (tras push, ver abajo).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: parser "ya" como token final → now (P1); tolerancia a acentos en SearchEngine ("última"/"próxima"); salir del parser hacia recuperación de tareas olvidadas / contexto / onboarding.

## Ciclo 112 — 2026-08-14 — Parser: "ya" / "ya mismo" como token final → now (P1, tarea olvidada)

- **HEAD inicial**: `907af99` (c.111 "manana noche" regression fix, ya pushed).
- **Problema (P1, captura/olvido de tarea)**: **"ya"** (y **"ya mismo"**) como token final no casaba ningún patrón → `dueAt=null` + residuo "ya" en el título → tarea SIN vencimiento, invisible en "What Now"/planificador, sin recordatorio programable → **olvidada**. "ya" es la forma cotidiana por excelencia de "hazlo ahora" (más corta y frecuente que "ahora" en captura móvil rápida). Asimetría flagrante: **"ahora"/"ahora mismo"** SÍ vencían a `now` (c.107 `nowPattern`: ahorita/ahora mismo/ahora/lo antes posible/cuanto antes/a la brevedad/lo más pronto posible) pero **"ya"** no.
- **Causa raíz**: `nowPattern` (c.107) enumeraba las frases de "ahora" pero omitió la forma más corta y nativa: "ya" (y "ya mismo"). Era el único adverbio de inmediatez de 2 letras no cubierto.
- **Descubrimiento**: probe JVM ad-hoc (`/tmp/ordia-probe/YaProbe.kt`, 10 frases) confirmó `due=null` para "comprar pan ya", "llamar a mamá ya", "reunión para ya", "hazlo ya", "ya mismo", mientras "ahora"/"ahora mismo" devolvían `now`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: `nowPattern` añade la alternancia `ya\s+mismo|ya` al final de la regex existente. "ya mismo" va ANTES que "ya" para que el match capture la frase entera (sin residuo). El `\b` Unicode de la regex protege contra falsos positivos: NO casa dentro de "playa"/"raya"/"haya" (verificado con probe + test `yaNoCasadentroDeOtraPalabra`). Se resuelve a `now` (no +N min aproximado: el usuario pidió "ya", la app debe sacar la tarea a la superficie de inmediato) — heurística honesta, no IA, simétrica a "ahora". La limpieza final del título (`\b(para|el)\b\s*$`, ya existente) borra "para" en "reunión para ya" → título "reunión".
- **Tests**: `bash tools/run_domain_tests.sh` → **758 PASS** (754 c.111 + 4 netos; el 5º test `yaNoCasadentroDeOtraPalabra` es anti-falso-positivo "playa"). Nuevos: `yaFinalVenceAhoraYLimpiaTitulo`, `yaMismoVenceAhoraYLimpiaTitulo`, `paraYaVenceAhoraYLimpiaTitulo`, `yaNoCasadentroDeOtraPalabra`. Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM 10 casos verde (incluido anti-falso "playa"). Sin regresión: "ahora"/"ahora mismo" intactos a `now`.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `1cd9f65` (push exitoso a openhands/autonomous-ordia, 907af99..1cd9f65).
- **HEAD final**: `1cd9f65` (push verificado, HEAD==origin).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: salir del parser hacia recuperación de tareas olvidadas (What Now/Guardián), contexto, onboarding; tolerancia a acentos en SearchEngine ("última"/"próxima").

---

## Ciclo 115 — 2026-08-14 — Parser: "luego" como sinónimo de "después"/"más tarde" → +3 h (P1, tarea olvidada)

- **HEAD inicial**: `a12f322` (c.112 "ya/ya mismo" → now). STALE_RUN detectado al pushear: `origin/openhands/autonomous-ordia` había avanzado a `b12a6c5` (+2 commits de otra ejecución: c.113 SearchEngine "ayer/semana pasada" + c.114 P0 corrupción "ya" `replace`→`replaceRange`). Mi commit local `0d1b5d2` fue rebasado sobre `b12a6c5` (rebase seguro, no destructivo, no sobrescribe historia compartida — mi commit aún no estaba pusheado); `NaturalTaskParser.kt` auto-mergió limpiamente (mi cambio en `laterRelativePattern` no colisiona con el fix P0 del "ya"); conflictos solo en docs `AI_AUTONOMY` (anexos), resueltos a mano. Mi trabajo se renumera c.112→c.115 para honrar la secuencia real del remote.
- **Problema (P1, captura/olvido de tarea)**: **"luego"** suelto no casaba ningún patrón → `dueAt=null` + residuo "luego" en el título → tarea SIN vencimiento, invisible en "What Now"/planificador, sin recordatorio programable → **olvidada**. "luego" es sinónimo cotidísimo de "después"/"más tarde" ("avísale luego", "lo hago luego", "enviar factura luego"). Asimetría flagrante: **"después"/"más tarde"/"más rato"** SÍ vencían a +3 h (`laterRelativePattern`, c.108) pero **"luego"** no, pese a ser semánticamente equivalente.
- **Causa raíz**: `laterRelativePattern` (c.108) enumeraba `más tarde`/`más rato`/`después` (con/sin tilde) pero omitió `luego`, el sinónimo más corto.
- **Descubrimiento**: probe JVM ad-hoc (`/tmp/ordia-probe/Probe.kt`, ~60 frases cotidianas) confirmó `due=null` y título sucio 'avisar luego' para "avisar luego", mientras "avisar más tarde" devolvía +3 h con título limpio. Mismo patrón de asimetría que c.112 ("ya" vs "ahora").
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: `laterRelativePattern` añade `|luego` a la alternancia existente. La cláusula de exclusión negativa `(?!\s+(?:de\b|del\b|de\s+la\b))` (que ya protegía "después del/de la N") cubre automáticamente "luego del/de la N" (dependencia/evento: "luego del almuerzo", "luego de la reunión") — NO se agendan a +3 h. Heurística honesta, no IA: +3 h, idéntico a "después"/"más tarde" (mayor que "un rato"=+1 h, no "ya"=now), aproximando "luego" a "más tarde hoy".
- **Tests**: `bash tools/run_domain_tests.sh` → **774 PASS** (base rebasada 768: 763 c.114 + 5 de search "sin fecha" del run paralelo c.114; mis +4 "luego" + 1 neto del merge = 773). Nuevos: `luegoSueltoVenceMasTardeYLimpiaTitulo`, `luegoEnFraseVenceMasTardeYLimpiaTitulo`, `luegoDelAlmuerzoNoEsAdverbioSuelto`, `luegoDeLaReunionNoEsAdverbioSuelto`. Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM ~60 casos reejecutado: "avisar luego" → +3 h título limpio; "luego del/de la N" → `due=null` preservado. Sin regresión: "después"/"más tarde"/"ya"/"ahora"/"sin fecha" intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: (ver informe final del run).
- **HEAD final**: (ver informe final del run).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: probe identificó más gaps del parser P1/P2: "una semana y media" → +7 d (mal, debería ~+10.5 d) + título sucio; "al final del día" → `due=null` (simétrico a "a última hora"=18:00, c.102); "a las 3 menos cuarto" → 03:00 (mal, debería 02:45) + título sucio. También: auditar el resto de `working.replace(it.value, ...)` en el parser por el mismo patrón de corrupción P0 (c.114); salir del parser hacia recuperación de tareas olvidadas (What Now/Guardián), contexto.

- **HEAD inicial**: `a12f322` (c.112 "ya"/"ya mismo"→now, ya pushed).
- **Problema (P2, búsqueda universal / recuperación de información)**: buscar **"ayer"**, **"semana pasada"** o **"última semana"** devolvía SIEMPRE vacío. `SearchEngine.DateScope` solo tenía TODAY/TOMORROW/THIS_WEEK/NEXT_WEEK/OVERDUE; `detectDateScope` no reconocía "ayer"/"pasada"/"última" → esas palabras caían a búsqueda de contenido puro (ninguna tarea se titula "ayer") → el usuario no podía recuperar qué tenía ayer o la semana pasada. Área de dirección explícita del producto ("recuperación de tareas olvidadas", "búsqueda universal").
- **Descubrimiento (probe JVM c.112, `/tmp/ordia-probe2/SearchProbe2.kt`, now=2026-08-14 viernes)**: "ayer"→[], "última semana"→[], "semana pasada"→[] (vacío = gap); "próxima semana"→[1] OK. Verificación crucial: los **acentos en "próxima semana" ya funcionaban** (`foldForSearch` normaliza NFD) → el "warning de acentos" heredado del contexto previo era **falso**; NO se tocó nada de acentos.
- **Causa raíz**: ausencia total de scopes de pasado en `DateScope` + `detectDateScope`. No era un bug de acentos.
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**:
  - `DateScope` añade `YESTERDAY` + `LAST_WEEK`.
  - Nuevos `YESTERDAY_TOKENS` = {"ayer"} y `LAST_WEEK_TOKENS` = {"pasada","pasado","última","últimas"} (accent-folded vía `foldForSearch`).
  - `detectDateScope` los conecta; `dateScopeTokens` los excluye del contenido de texto.
  - `taskMatchesDateScope` añade ramas: `YESTERDAY` → `dueDate == today.minusDays(1)`; `LAST_WEEK` → rango lunes-domingo de la semana pasada.
  - **Math LAST_WEEK (corregida tras verificación)**: `daysToSunday = (7 - today.dayOfWeek.value) % 7`; `endLastWeek = today.plusDays((daysToSunday - 7).toLong())` (domingo pasado); `startLastWeek = endLastWeek.minusDays(6)` (lunes pasado). Verificado con jueves 2026-08-13 → semana pasada = lun 08-03..dom 08-09. (La 1ª fórmula usaba `daysToSunday - 6` que daba lun 08-04..dom 08-10 —incorrecto—; corregida antes de commit.)
  - **Decisión de diseño (datos/recuperación)**: los scopes pasados incluyen tareas **completadas** (su propósito es revisar qué había en ese período), a diferencia de los presentes/futuros que las excluyen (`!it.completed`); las canceladas se excluyen siempre. El test existente `completedTasksAreExcludedFromDateScopes` (que espera exclusión en "hoy") se preserva intacto.
- **Tests**: `bash tools/run_domain_tests.sh` → **763 PASS** (758 c.112 + 5 netos: `ayer_recoversTaskDueYesterday`, `ayer_recoversEvenCompletedTask`, `semanaPasada_recoversTasksFromPreviousWeek`, `ultimaSemana_recoversTasksWithAccent`, `semanaPasada_excludesThisWeekTasks`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM aparte: 9 casos verde ("ayer"→[1], "última/semana pasada"→[2] con/sin tilde, "esta semana"→[], "ayer cita"→[1], "próxima/semana que viene"→[3] intactos). Sin regresión.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`, `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `5efd685` (push exitoso a openhands/autonomous-ordia, a12f322..5efd685).
- **HEAD final**: `5efd685` (push verificado, HEAD==origin).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: auditar WhatNowEngine/GuardianEngine (recuperación de vencidas, detección de compromisos); descubrimiento continuo en contexto/onboarding/captura ultrarrápida; no volver a tocar acentos del SearchEngine (ya verificado OK).

---

## Ciclo 114 — 2026-08-14

- **HEAD inicial**: `303870b` (c.113, sincronizado con origin/openhands/autonomous-ordia; `git pull --ff-only` OK sin divergencia).
- **Rama**: `openhands/autonomous-ordia`. `git pull --ff-only` OK (sin divergencia).
- **Área auditada**: SearchEngine (búsqueda universal) + sondeo del parser (frases coloquiales: "un par de", "anteayer", "pasado mañana", mediodía/medianoche) + backup roundtrip (Task/Note/Habit JSON) + RecurrenceEngine + WhatNowEngine. **Hallazgo**: parser, RecurrenceEngine, WhatNowEngine y backup serialización están sólidos (probes JVM verde; "un par de días"=+2d, "anteayer"=−2d, "pasado mañana"=+2d; roundtrip Task completo). Brecha real en SearchEngine: sin scope para tareas SIN vencimiento.
- **Problema seleccionado**: SearchEngine no recuperaba tareas sin fecha ("sin fecha"/"sin vencimiento"/"sin día"/"sin plazo") — justo las capturadas pero nunca agendadas (las que se olvidan).
- **Prioridad**: P2 (recuperación de tareas olvidadas / búsqueda universal). No había P0/P1 conocido abierto; elegí mejora de producto de alto impacto (recuperación de información) antes que un warning cosmético.
- **Causa raíz**: `DateScope` solo tenía scopes basados en rango de fecha; `taskMatchesDateScope` hacía `task.dueAt ?: return false` para todos los scopes no-OVERDUE → una tarea sin `dueAt` nunca casaba.
- **Solución** (`SearchEngine.kt`, cambio mínimo, reutiliza TODO el flujo existente, sin UI nueva):
  - `DateScope` añade `UNDATED`.
  - `UNDATED_HINTS = setOf("fecha", "vencimiento", "dia", "plazo")`.
  - `detectDateScope`: primera rama `"sin" in words && UNDATED_HINTS.any { it in words } -> UNDATED` (antes que cualquier otro token; "sin" no casa otros scopes).
  - `dateScopeTokens`: elimina "sin" + las hints del contenido (no se exigen en el título).
  - `taskMatchesDateScope` UNDATED → `!task.completed && task.status != TaskStatus.CANCELLED && task.dueAt == null`.
  - Rama `UNDATED -> false` inalcanzable añadida al `when` final para exhaustividad.
- **Heurística honesta**: se exige "sin" + sustantivo de fecha para no activarse con "sin azúcar"/"sin leche". Excluye completadas (ya resueltas) y canceladas; archivadas ya filtradas. No IA simulada.
- **Tests**: `bash tools/run_domain_tests.sh` → **768 PASS** (763 c.113 + 5 nuevos: `sinFecha_returnsOnlyUndatedTasks`, `sinVencimiento_returnsOnlyUndatedTasks`, `sinFecha_excludesCompletedUndatedTasks`, `sinFechaSinAcento_tambiénFunciona`, `negacionAjena_noActivaScopeUndated`). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin regresión: "hoy"/"mañana"/"atrasadas"/"ayer"/"semana pasada"/"próxima semana" intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Commits**: `feat(search): recuperar tareas sin fecha con "sin fecha"/"sin vencimiento"` (`6721c8a`).
- **HEAD final**: `6721c8a` (merge con `b12a6c5` del run paralelo P0 "ya" en curso).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: auditar SummaryEngine/CommitmentEngine/DayPlanner; descubrimiento continuo en contexto/captura ultrarrápida/relaciones notas-tareas.

## Ciclo 114 — 2026-08-14 — Parser: P0 corrupción de título al borrar el token "ya" (`working.replace` global en vez de `replaceRange`)

- **HEAD inicial**: `5efd685` (c.113 SearchEngine "ayer/semana pasada", base sincronizada con `origin/openhands/autonomous-ordia` tras fetch; mi base original `a12f322` estaba obsoleta — STALE_RUN evitado con stash+ff-only+pop+resolución de conflictos de docs, sin force/reset destructivo).
- **Problema (P0 integridad de datos)**: el c.112 añadió el token **"ya"** (forma de "ahora") a `nowPattern`, con `\b` Unicode para no casar dentro de "playa"/"raya". Pero el **borrado** del match usaba `working.replace(it.value, " ")` — un reemplazo **global y literal** que elimina TODAS las ocurrencias de la subcadena "ya" en el título, no solo el rango del match. Así, cuando el título contiene una palabra con la secuencia "ya" (maya/playa/raya) Y termina con el token "ya", el título se **corrompía**:
  - `comprar maya ya` → `title='comprar ma'` (maya→ma)
  - `reservar en la playa para ya` → `title='reservar en la pla'` (playa→pla)
  - `volar cometa en la raya ya` → `title='volar cometa en la ra'` (raya→ra)
  El vencimiento era correcto (now) pero el título quedaba dañado: pérdida silenciosa de contenido de la tarea. El usuario no se entera hasta revisar la tarea y, entre tanto, la info está corrupta. El test anti-falso del c.112 (`yaNoCasadentroDeOtraPalabra`: "Comprar una playa" → due=null) NO cubría este caso porque ahí "ya" NO casa como token (no hay token final "ya"); el bug solo se dispara cuando "ya" SÍ casa Y hay otra palabra que lo contiene.
- **Causa raíz**: `String.replace(oldValue, newValue)` en Kotlin reemplaza **todas** las ocurrencias literales, no solo el match de la regex. `it.value` es la subcadena "ya", así `working.replace(it.value, " ")` borra cada "ya" del string. La forma correcta es `working.replaceRange(it.range, " ")`, que sustituye solo el rango `[it.range.first, it.range.last]` del match.
- **Descubrimiento**: probe JVM ad-hoc (`/tmp/probe_ya.kt`, 6 frases) compilado con el mismo enfoque que `run_domain_tests.sh` (RoomStubs + PreferenceStubs + Entities + todo el dominio + probe, kotlinc -cp jars). Confirmó la corrupción en 3 casos P0 + intactos los casos de control ("hacerlo ya"→"hacerlo", "ya mismo llamar"→"llamar", "llamar playa"→due=null).
- **Solución (mínima, `NaturalTaskParser.kt` bloque `nowPattern`, sin nueva pantalla/botón)**: `working.replace(it.value, " ")` → `working.replaceRange(it.range, " ")`. Cambio de una línea. Sustituye SOLO el rango del match del token "ya", preservando el resto del título.
- **Tests**: `bash tools/run_domain_tests.sh` → **765 PASS** (763 c.113 + 2 nuevos). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM post-fix verde: `comprar maya ya`→`comprar maya` (dueAt=now), `reservar en la playa para ya`→`reservar en la playa` (dueAt=now), `volar cometa en la raya ya`→`volar cometa en la raya` (dueAt=now); casos de control intactos. Sin regresión: los 6 tests de "ya" del c.112 siguen pasando; los 5 de search del c.113 intactos.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Commits**: `b12a6c5` (run paralelo, integrado vía merge en este run).
- **HEAD final**: `b12a6c5` (remoto, pre-merge de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: auditar el resto de `working.replace(it.value, ...)` en `NaturalTaskParser.kt` por el mismo patrón de corrupción; salir del parser hacia recuperación de tareas olvidadas / contexto / onboarding.


## Ciclo 116 — 2026-08-14 — Parser: fracciones sub-hora negativas "a las N menos cuarto/cinco/diez/veinte/veinticinco" (analógico de reloj)

- **HEAD inicial**: `51360e38d2a4669d1e6c1af6a54594fa4b60c734` (c.114, base sincronizada con `origin/openhands/autonomous-ordia`, ff-only limpio).
- **Problema (P2 integridad de datos/UX)**: el parser reconocía "a las 3" (03:00) y el modificador positivo "y media"/"y cuarto" (c.58) pero NO el modificador negativo analógico de reloj **"menos cuarto/cinco/diez/veinte/veinticinco"**: `cita a las 3 menos cuarto` → `due=03:00` (mal, debería 02:45) Y `title='cita menos cuarto'` (residuo). La hora se agendaba 15-25 min tarde y el título quedaba sucio. Forma horaria cotidiana en español ("a las 9 menos cuarto", "a las 10 menos 5"). Asimetría con "y media" (c.58) que SÍ añadía correctamente. Descubierto en el probe del c.113 (item BACKLOG P2 "menos cuarto").
- **Causa raíz**: `timePatterns[0]` (a la una) y `[1]` (a las N) sólo capturaban `y\s+(media|cuarto)` como grupo 3 de fracción. La fracción negativa `menos\s+(cuarto|cinco|diez|veinte|veinticinco)` no estaba en la regex → el token "menos cuarto" no se consumía (residuo de título) ni se aplicaba la resta de minutos. Además la forma con dígitos `menos 5` (más común en captura móvil rápida) tampoco casaba.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  1. `timePatterns[0]`/`[1]` grupo 3 ahora captura la fracción positiva **o** negativa: `(?:\s+(y\s+(media|cuarto)|menos\s+(cuarto|cinco|diez|veinte|veinticinco|\d{1,2})))?`. Se añade `|\d{1,2}` para la forma numérica. Se preservan los índices de grupo (meridiem sigue siendo grupo 4, horas grupo 5).
  2. Resolución: si `subFraction` no está vacío y no hay `:MM` explícito, se resta la fracción a la hora con **wrap 24 h** modular: `total = (hour*60 + minute - sub + 1440) % 1440`. Mapping: cuarto=15, cinco=5, diez=10, veinte=20, veinticinco=25, `\d{1,2}` fallback. El `when` prioriza `veinticinco`/`veinte` antes que `cinco` (pues "veinticinco" termina en "cinco" y haría match prematuro). Se aplica **después** del offset PM/AM para que "a las 3 menos cuarto de la tarde" → 14:45 (no descuadre el meridiem).
- **Tests**: `bash tools/run_domain_tests.sh` → **785 PASS** (777 c.114 + 8 nuevos). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM post-fix verde (12 casos): `a las 3 menos cuarto`→02:45, `a las 9 menos cuarto`→08:45, `a las 12 menos cuarto`→11:45 (wrap), `a las 3 menos cuarto de la tarde`→14:45, `a las 10 menos 5`→09:55, `a las 10 menos diez`→09:50, `a las 10 menos veinte`→09:40, `a las 10 menos veinticinco`→09:35, `a la una menos cuarto del mediodia`→12:45. Control de no-regresión: "y media"/"y cuarto"/"de la noche"/"3pm"/"a las 24" intactos.
- **Limitación conocida (menos frecuente)**: el orden inverso "del mediodía menos cuarto" (meridiem antes que fracción) no casa porque el layout del patrón es `fracción → meridiem`. La forma dominante "X menos cuarto [de la tarde/mañana/noche]" (fracción antes del meridiem) SÍ funciona. No se añadió un segundo grupo post-meridiem para no duplicar complejidad (anti-feature-bloat); queda registrado.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: salir del parser hacia la prioridad de memoria (auditar WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas), o cerrar los otros 2 gaps del c.113 ("una semana y media"/"un mes y medio" +0.5 unidad, "al final del día"=18:00) si se decide que aportan suficiente valor.

## Ciclo 117 — 2026-08-14 — Parser: "al final del día/día/la jornada/los días" como sinónimo de "a última hora" → 18:00 (P2, tarea olvidada)

- **HEAD inicial**: `df0abd4a2223a9b24cdc5534cba4dcbac0048b2a` (c.116, base sincronizada con `origin/openhands/autonomous-ordia`; `git pull --ff-only` OK, sin divergencia — STALE_RUN descartado).
- **Problema (P2 recuperación/captura)**: las frases cotidianas **"al final del día"/"al final del dia"/"al final de la jornada"/"al final de los días"** (sinónimos de "a última hora"/fin de jornada) NO se interpretaban como hora → `dueAt=null` (tarea SIN vencimiento → olvidada, invisible en "What Now"/planificador, sin recordatorio programable) Y la frase quedaba como **residuo en el título**. Asimetría flagrante con **"a última hora"=18:00** (`ultimaHoraPattern`, c.102) que SÍ funcionaba. La persona dice "reunión al final del día"/"terminar al final de la jornada" tan o más que "a última hora" en captura móvil. Descubierto en el probe del c.113 (item BACKLOG P2 "al final del día").
- **Causa raíz**: no existía patrón canónico para la forma "al final del día". `ultimaHoraPattern` sólo casaba `última hora` (con/sin conector "a"), no la perícopasis "al final del día/jornada".
- **Solución (mínima, simétrica a `ultimaHoraPattern`, sin nueva pantalla/botón)** — `NaturalTaskParser.kt`:
  1. Nuevo `alFinalDelDiaPattern = (?i)al\s+final\s+(?:del\s+d[ií]a|de\s+la\s+jornada|de\s+los\s+d[ií]as)\b` + `alFinalDelDiaTime = LocalTime.of(18,0)`. Las 3 alternativas cubren las concordancias gramaticales correctas: "del día" (masc. sing.), "de la jornada" (fem. sing.), "de los días" (masc. plur.); `d[ií]a` tolera ausencia de tilde.
  2. **Exige el conector "al "** para no colisionar con "fase final del proyecto" ni "en la fase final" (no son fin de jornada) → anti-falso-positivo.
  3. Cableado paralelo al de `ultimaHora`: match → resolución de respaldo 18:00 (en la cadena `?: ... ?: alFinalDelDiaMatch?.let { alFinalDelDiaTime }`) → limpieza del título (`.let { value -> alFinalDelDiaPattern.replace(value, " ") }`).
  4. Como `ultimaHoraTime`, es **hora de respaldo**: si hay una parte del día explícita elsewhere ("de la tarde"), ésta tiene prioridad y el patrón solo limpia "al final del día".
- **Tests (TDD)**: 6 tests RED antes del fix (5 failures + 1 anti-falso ya verde). `bash tools/run_domain_tests.sh` → **791 PASS** (785 c.116 + 6 nuevos). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM post-fix (10 casos): `reunión al final del día`→18:00 título "reunión"; `al final del dia` (sin tilde)→18:00; `al final de la jornada`→18:00; `al final de los días`→18:00; `terminar al final del día`→18:00; `llamar al final del día`→18:00; `reunión a última hora`→18:00 (**sin regresión**); `reunión fase final del proyecto`→`due=null` (anti-falso); `reunión al final` (sin "del día")→`due=null` (correctamente rechazado); `comprar pan hoy al final del día`→18:00 título "comprar pan" ("hoy" + frase limpios).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: salir del parser hacia la prioridad de memoria (auditar WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas); gap OPEN restante del c.113: "una semana y media"/"un mes y medio" (+0.5 de la unidad) — evaluar frecuencia real (anti-feature-bloat) antes de implementar.


## Ciclo 119 — 2026-08-14 — Parser: "y media/medio" en plazos de día/semana/mes/año (media unidad más)

- **Run/ciclo**: 119 (rama `openhands/autonomous-ordia`). Base inicialmente en `51360e38` (c.117 local), pero al `git fetch` para push el remoto había avanzado 2 commits por un run paralelo (c.118 P0 crash duración `7a9dfc2` + docs `b98d574`). Mi commit `61307b1` no estaba publicado → rebase seguro y no destructivo sobre `b98d574`. Auto-merge OK en `NaturalTaskParser.kt` (el fix P0 del c.118 tocó el bloque de borrado de duración, región distinta a mi edición de `relativePattern`) y `NaturalTaskParserTest.kt`; único conflicto en `CURRENT_STATE.md` (ambos runs añadieron entrada c.118 al inicio) resuelto renumerando la mía a c.119 y conservando ambas. STALE_RUN=false (base actualizada de forma no destructiva).
- **HEAD inicial**: `51360e38d2a4669d1e6c1af6a54594fa4b60c734` (c.117); al sincronizar para push se avanzó a `b98d574` (c.118 remoto).
- **Problema seleccionado**: **P2 → P1 de datos**. Las frases cotidianas de plazo aproximado **"una semana y media"** (≈+10.5d), **"un mes y medio"** (≈+45d), **"un día y medio"** (+1.5d), **"dos semanas y media"** (≈+17.5d), **"un año y medio"** (≈+547.5d) se resolvían a la unidad ENTERA (+7d/+30d/+1d/+14d/+365d) Y dejaban "y media"/"y medio" como **residuo en el título** ("enviar informe y media" en vez de "enviar informe"). Asimetría flagrante con **"una hora y media"=+90 min** (`compoundFractionalRelativePattern`, c.94) que SÍ sumaba media hora: a escala sub-hora el modificador funcionaba, a escala de día/semana/mes/año NO. Consecuencia P1 de datos: el vencimiento quedaba **media unidad ANTES** de lo pedido (3.5 días antes para "una semana y media", 15 días antes para "un mes y medio") → recordatorio/planificación prematura silenciosa + título sucio. Descubierto en el probe del c.113 (item BACKLOG P2 "una semana y media", OPEN bajo anti-feature-bloat).
- **Prioridad**: P2 (integridad de plazos + título limpio).
- **Decisión anti-feature-bloat**: el modificador "y media" es forma cotidiana real de plazo aproximado ("lo termino en una semana y media"), NO un botón/pantalla nueva. La heurística +0.5 de la unidad es honesta (no IA, no random), simétrica a la ya existente para horas. Cumple la regla de producto: recupera la intención real del plazo sin añadir complejidad visible. Se implementa.
- **Causa raíz**: `relativePattern` (c.95, línea 150) capturaba `en una semana` (7d) pero NO reconocía el sufijo `y media`/`y medio`; este caía fuera del match y sobrevivía como residuo en el título. La resolución (línea 826) computaba `millis = amount × unitMillis` sin sumar media unidad.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  1. `relativePattern` añade grupo 3 `(?:\s+y\s+(media|medio))?` — sufijo opcional que captura "y media" o "y medio" (acepta ambos géneros: la unidad es fem./masc. según el sustantivo — "semana" fem. → "y media"; "mes"/"año"/"día" masc. → "y medio" — pero el usuario los mezcla; aceptar ambos evita una tarea sin media unidad por un detalle gramatical).
  2. La resolución extrae `unitDays` por unidad (día=1, semana=7, mes=30, año=365, quincena=15, bimestre=60, trimestre=90, semestre=180; min/hora=0) y suma `unitDays/2` días (`halfMillis`) cuando el grupo 3 no está vacío. Reorganiza el `when` de `baseMillis` para reusar `unitDays` (DRY): min/hora siguen con su fórmula propia; el resto usa `amount × unitDays × 24h`.
  3. El grupo 3 es capturante (no `(?:...)`) para que `groupValues[3].isNotEmpty()` detecte el sufijo.
- **Tests (TDD)**: 6 tests RED antes del fix (5 failures de fecha + 1 de título). `bash tools/run_domain_tests.sh` → **800 PASS** (794 c.118 + 6). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM post-fix (8 casos): `enviar informe en una semana y media`→2026-08-09 00:00 título "enviar informe"; `renovar en un mes y medio`→2026-09-12 12:00 título "renovar"; `terminar en dos semanas y media`→2026-08-16 00:00; `llamar en un día y medio`→2026-07-31 00:00; `revisar en un año y medio`→2028-01-28 00:00; `entregar en quince días y medio`→2026-08-14 00:00. Control de NO-regresión: `llamar en una hora y media`→13:30 (patrón compuesto intacto); `reunión en una hora`→13:00; `enviar en una semana`→+7d 12:00 (sin sufijo intacto); `enviar en una semana a las 9`→08-05 09:00 (hora explícita); `enviar en una semana y media a las 9`→08-09 09:00 (sufijo + hora explícita combinan); `llamar en media hora`→12:30 (patrón fraccional intacto).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM, estado combinado post-rebase: 800 tests).
- **Próxima prioridad**: cerrados los 3 gaps P2 del probe c.113 (al final del día=c.117, a las N menos cuarto=c.116, y media de día/semana/mes/año=c.119). Salir del parser hacia la prioridad de memoria: auditar WhatNowEngine/GuardianEngine y recuperación de tareas olvidadas, contexto, onboarding; buscar nuevas oportunidades de producto (menos fricción, más potencia real).


## Ciclo 122 — 2026-08-14 — Search: búsqueda por parte del día (tarde/noche/madrugada) de HOY (P2 búsqueda universal/recuperación)

- **Run/ciclo**: 122 (rama `openhands/autonomous-ordia`). Base sincronizada con `origin/openhands/autonomous-ordia`: `git pull --ff-only` OK al iniciar (HEAD `6ab0445` c.120), pero tras commit local el push fue rechazado por divergencia — otro agente (c.121 guardián) había hecho push a la misma rama. Resolución NO destructiva: `git fetch` + `git rebase origin/openhands/autonomous-ordia` (rebase limpio de código — SearchEngine.kt/tests sin conflicto, ambos tocan clases distintas — único conflicto en CURRENT_STATE.md por numeración de ciclo, resuelto renombrando mi entrada a c.122). No STALE_RUN: mi base era válida, el trabajo del otro agente no se solapa (GuardianCoach.kt vs SearchEngine.kt).
- **HEAD inicial**: `6ab0445e32172a00d77f5aba6618244418920de2` (c.120, "search date scope purity").
- **Problema seleccionado (P2 → recuperación de información)**: buscar por **parte del día de HOY** — "tarde", "esta tarde", "noche", "madrugada" — devolvía todas las tareas de HOY mezcladas sin distinguir franja horaria. El usuario que piensa "¿qué tengo esta tarde?" o "¿y esta noche?" recibía mañana+tarde+noche juntas y debía revisar todo el día para encontrar la franja concreta; las de la franja quedaban enterradas entre las de la mañana. Brecha del área de dirección explícita "búsqueda universal" y "recuperación de tareas olvidadas". Era un gap de SEÑAL (no de datos: nada se perdía), pero rompía el atajo mental natural de "lo de esta tarde/noche".
- **Prioridad**: P2 (búsqueda universal / UX de recuperación).
- **Decisión anti-feature-bloat**: la búsqueda por parte del día es una extensión del scope de fecha existente (c.81 "hoy"/"mañana"/"vencidas", c.113 "ayer"/"última semana"), NO una pantalla/botón nuevo. Reaprovecha toda la maquinaria de `SearchEngine` (detección de scope, `pureDateScope` del c.120, exclusión de completadas). La franja horaria es heurística local honesta (banda por hora), no IA/random. Cumple la regla de producto: recupera la intención real del usuario ("lo de esta tarde") sin añadir complejidad visible. Se implementa.
- **Causa raíz**: `DateScope` (enum en `SearchEngine.kt`) sólo tenía scopes por día/semana/vencidas/ayer/sin-fecha; no existía un scope de franja horaria de HOY. "tarde"/"noche"/"madrugada" caían a búsqueda de contenido (matching por título), así que "tarde" solo encontraba tareas cuyo título contenía la palabra "tarde" (casi ninguna), no las que vencían a las 15:00-17:00.
- **Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**:
  1. 3 nuevos `DateScope`: `TARDE`, `NOCHE`, `MADRUGADA`.
  2. 3 token sets: `LATE_AFTERNOON_TOKENS={"tarde"}`, `NIGHT_TOKENS={"noche"}`, `EARLY_MORNING_TOKENS={"madrugada"}`. **"mañana" se EXCLUYE** como parte del día: también significa "tomorrow" y ya activa `TOMORROW` (misma decisión que el parser en su variante compacta de parte del día, c.109).
  3. `detectDateScope()` evalúa los tokens de parte del día **DESPUÉS** de hoy/mañana/ayer: así "hoy tarde" o "mañana tarde" resuelven al día explícito (más amplio y útil) en vez de quedarse solo con la franja de hoy. Sin palabra de día, "tarde"/"noche"/"madrugada" solas sí activan la franja de hoy.
  4. Helper `scopeBand(scope): IntRange?` — madrugada `0..5`, tarde `12..17`, noche `18..23` (por hora local del `dueAt`), coherente con el anclaje canónico del parser (tarde≈15, noche≈21, madrugada≈04). `null` si el scope no es parte del día.
  5. `taskMatchesDateScope()`: early return cuando `scopeBand(scope) != null` → `zonedDue.toLocalDate() == today && zonedDue.hour in partOfDay`. Como es HOY (presente), excluye completadas (igual que `TODAY`).
  6. `dateScopeTokens()` ampliado para incluir los 3 token sets (así se limpian del query de contenido y no inflan `textWords`).
  7. El `when` exhaustivo añade las 3 ramas `TARDE`/`NOCHE`/`MADRUGADA -> false` (inalcanzables: resueltas vía `scopeBand` antes).
  8. `pureDateScope` del c.120 cubre automáticamente los scopes de franja: cuando no hay palabras de contenido, `pureDateScope=true` y los 6 filtros fecha-menos se suprimen → "tarde" no inunda con notas/proyectos (solo tareas con fecha). Cuando hay contenido ("tarde cita médica"), `pureDateScope=false` y el contenido fecha-menos sigue siendo relevante (se filtra por texto).
- **Tests (TDD)**: 6 tests RED antes del fix (todos fallaban: "tarde" devolvía vacío o todas). `bash tools/run_domain_tests.sh` → **815 PASS** tras rebase+merge (809 c.121 guardián + 6 search c.122; ambas features coexisten en clases distintas, sin colisión). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Tests: `tarde_returnsOnlyTasksDueTodayAfternoon` (15:00 de hoy entra; 8:00 no; 20:00 no; ayer 15:00 no; mañana 15:00 no), `estaTarde_matchesAfternoonOnly` (17:00 y 12:00 límite entran; 7:00 no), `noche_returnsOnlyTasksDueTonight` (21:00 y 23:00 entran; 16:00 no), `madrugada_returnsOnlyEarlyMorningToday` (4:00 entra; 13:00 no; ayer 3:00 no), `tardeConTexto_filtraDentroDeLaFranja` ("tarde cita medica" ⇒ solo la de 15:00 con "cita médica"), `parteDelDia_excluyeCompletadas`.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Las franjas horarias (0-5/12-17/18-23) son una elección razonable; el usuario puede refinarlas en el futuro si la zona cultural difiere.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`, `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 815 tests combinados tras merge con c.121 guardián).
- **Próxima prioridad**: por fin salir del parser/search hacia la prioridad de memoria pendiente desde c.117: auditar `WhatNowEngine.kt`/`GuardianEngine.kt` (bugs reales de scoring/exclusión/contexto, consistencia `WhatNowEngine.reason()` vs `TaskRules.timeRank()`), recuperación de tareas olvidadas (vencidas importantes, "What Now" más útil), contexto, onboarding; buscar nuevas oportunidades de producto (menos fricción, más potencia real).


## Ciclo 124 — 2026-08-14 — Auditoría VERIFICADA (WhatNow/Guardian) + guards de regresión del codec de restauración (P0 datos)

- **Run/ciclo**: 124 (rama `openhands/autonomous-ordia`). Base sincronizada: `git pull --ff-only origin openhands/autonomous-ordia` OK, HEAD inicial `23f5134` (c.123 parser pleonasmo). Sin divergencia, sin push concurrente al iniciar. Working tree limpio.
- **HEAD inicial**: `23f513408f88dacfbbd39767a477f367e2354c51` (c.123).
- **Problema seleccionado**: cerrar el item de auditoría OPEN repetido como "próxima prioridad" en c.117, c.121, c.122 y c.123 — "auditar `WhatNowEngine.reason()` vs `TaskRules.timeRank()` (consistencia etiquetado/ranking cuando una tarea es a la vez scheduled-later y due-today)". Era una auditoría, no un bug confirmado.
- **Prioridad**: P1 (inteligencia/What Now) → resultado: VERIFICADO, no bug.
- **Hallazgo de la auditoría (consistencia reason()↔timeRank())**: CONSISTENTE e intencional. Una tarea con `startAt` futuro (p. ej. empieza 15:00) pero `dueAt` HOY cae a `timeRank = -1` (SCHEDULED_LATER), por DEBAJO del inbox sin fecha (rank 0); `reason()` la etiqueta "está programada para más tarde". Ambas superficies coinciden. La aparente "incoherencia" (vence hoy pero queda última) es decisión de producto deliberada y test-lockeada: el usuario que agendó explícitamente "empieza 15:00" NO quiere que What Now la eleve a las 10:00 (cuando faltan >15 min, fuera de la ventana inminente) si hay inbox más actual — el `startAt` explícito tiene prioridad sobre el `dueAt` del día dentro de la ventana pre-inminente. Lo garantizan 3 tests: `ignoresTaskScheduledLaterToday`, `startOutsideImminentWindowStaysDeprioritized` y la guardia de divergencia `whatNowAndWidgetAgreeOnBestTaskAcrossTime` (c.86). Cierre del item: NO se cambia el ranking — rompería comportamiento intencional y testeado.
- **Auditoría secundaria (`TaskSnapshotCodec`)**: ruta de DESHACER/RESTAURAR automatización (`OrdiaViewModel.undoLastAutomation` → `decodeMap` → `taskRepository.update(snapshot)`), zona P0 de datos. Hipótesis inicial: `optNullableLong` devolvía 0 (época 1970) para claves ausentes → al deshacer una automatización restaurada de un backup de versión anterior / JSON truncado se corromperían `dueAt`/`startAt`/`reminderAt`/`completedAt`/`projectId`/`parentTaskId` a 1970. Resultado: NO es bug — el decoder ya es robusto. `JSONObject.isNull(key)` devuelve `true` también para claves ausentes (quirk org.json: `NULL.equals(null)==true`), así `optNullableLong` = `if (isNull) null else optLong` devuelve `null` para clave ausente. Verificado por test, no por suposición.
- **Solución (mínima, solo tests)**: +2 guards de regresión en `TaskSnapshotCodecTest.kt` que codifican el invariante sutil y antes NO testeado de la frontera de restauración:
  1. `decodeMapAbsentNullableFieldsResolveToNullNotEpochZero` — JSON mínimo sin `startAt/dueAt/reminderAt/completedAt/projectId/parentTaskId` → todos `null`, nunca 0/1970.
  2. `decodeMapPresentNullStillResolvesToNull` — round-trip normal con null explícito → null.
  - Valor: si un mantenedor futuro "mejora" `optNullableLong` a `optLong(key, default)` o migra de lib JSON, el guard impide introducir silenciosamente corrupción a 1970 en la ruta de deshacer. No añade pantalla/botón/complejidad visible.
- **Tests**: `bash tools/run_domain_tests.sh` → **822 PASS** (820 c.123 + 2). Smoke `bash tools/run_domain_checks.sh` → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). La auditoría de migraciones Room / `fallbackToDestructive` / workers con DAOs reales queda pendiente (requiere Android SDK).
- **Archivos modificados**: `app/src/test/java/com/ordia/app/domain/TaskSnapshotCodecTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit de este run).
- **Estado**: VERIFIED (auditoría: no bug; +2 guards de regresión en zona P0 de datos; 822 tests).
- **Próxima prioridad**: descubrimiento continuo en contexto/onboarding/navegación/accesibilidad/rendimiento; la auditoría con DAOs reales (Room migrations, backup/restore end-to-end, workers) queda NO VERIFICADA hasta que exista Android SDK.

## Ciclo 125 — 2026-08-14 — Parser: "entrante" (sinónimo caribeño de "que viene"/"próximo") no se reconocía → vencimiento olvidado (P1 captura)

- **Run/ciclo**: 125 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch` reveló que el remoto avanzó un commit (`00ad680`, c.124 guards de codec de otro agente). Resolución NO destructiva: `git stash` → `git pull --ff-only` (fast-forward limpio) → `git stash pop` (sin conflicto: c.124 tocó `TaskSnapshotCodecTest.kt` + docs, yo toco `NaturalTaskParser.kt` + `NaturalTaskParserTest.kt`). No STALE_RUN: mi base era válida; el trabajo del otro agente no se solapa con el parser.
- **HEAD inicial**: `00ad6806af162f66ddaf434d6437020b49b6e39e` (c.124, "guards de regresión del codec de restauración").
- **Problema seleccionado (P1 → integridad de captura)**: el calificador **"entrante"** — sinónimo de "que viene"/"próximo" en el español caribeño, zona canónica de la app (`America/Santo_Domingo`) — **no se reconocía** en NINGÚN patrón de período próximo. "la semana entrante", "el mes entrante", "el año entrante", "el 15 del mes entrante", "fin del mes entrante", "la semana entrante el viernes", "el lunes de la semana entrante" caían a `dueAt=null` + residuo "entrante" en el título → **vencimiento olvidado** (invisible en What Now/planificador, sin recordatorio). Era un gap de **integridad de captura**: el usuario caribeño captura una fecha válida y la app la descarta como "sin fecha" — pérdida silenciosa de la intención temporal.
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz**: los 8 patrones de período próximo (`nextPeriodPattern`, `nextMonthDayPattern`, `nextMonthDayReversePattern`, `nextWeekWeekdayReversePattern`, `nextWeekWeekdayForwardPattern`, `endOfMonthPattern`, `midOfMonthPattern`, `startOfMonthPattern`) listaban alternativas `que viene`/`próximo`/`próxima` pero NO `entrante`; el helper `monthBaseForBoundary` (que decide si desplazar al mes siguiente) detectaba `que viene`/`próxim`/`proxim` pero NO `entrante`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: se añade `entrante` como **alternativa léxica simétrica** en los 8 patrones (`(?:que\s+viene|pr[oó]ximo|pr[oó]xima|entrante)`) y en la condición de `monthBaseForBoundary` (`t.contains("entrante")`). **Sin lógica nueva**: se reusa la **misma** resolución +1 período/semana/mes/año que "que viene". Anti-falsos-positivos: `entrante` sólo casa cuando va precedido de un período (semana/mes/año/trimestre/…), NO tras un sustantivo ajeno — "documento/llamada/factura entrante" sigue sin casar (dueAt=null, título intacto), verificado por test.
- **Tests**: +11 tests de regresión en `NaturalTaskParserTest.kt`: `semanaEntranteParsesDueAt` (+7d→2026-08-05), `mesEntranteParsesDueAt` (+30d→2026-08-28), `anioEntranteParsesDueAt` (+1año→2027-07-29), `mesEntranteRespetaHoraExplicita` (10:00), `entranteNoEsFalsoPositivoEnSustantivoAjeno` ("documento entrante"→null, título intacto), `elNDelMesEntranteResuelveDiaNDelMesSiguiente` (10→08-10), `elMesEntranteElNResuelveDiaNDelMesSiguiente` (orden inverso, 20→08-20), `laSemanaEntranteElViernesResuelveViernesDeLaSemanaProxima` (→08-07), `elLunesDeLaSemanaEntranteResuelveLunesDeLaSemanaProxima` (orden inverso, →08-03), `finDelMesEntranteAnclaFinMesSiguiente` (→08-31), `principiosDelMesEntranteAnclaInicioMesSiguiente` (→08-01). `bash tools/run_domain_tests.sh` → **833 PASS** (822 c.124 + 11; coexistencia sin conflicto con guards del codec de c.124 — clases distintas). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 833 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo en el parser (más variantes regionales/formas compactas sin reconocimiento), y áreas de dirección no parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA (sin Android SDK).

## Ciclo 126 — 2026-08-14 — Parser: "que entra"/"anterior"/"comienzos" no se reconocían → vencimientos olvidados (P1 captura regional)

- **Run/ciclo**: 126 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK, HEAD local = HEAD remoto = `908764d` (c.125 "entrante"). Sin divergencia, sin push concurrente al iniciar. Working tree limpio.
- **HEAD inicial**: `908764d4b9614bc44e20943fc0e54650eaf03a0e` (c.125).
- **Problema seleccionado (P1 → integridad de captura regional)**: tres sinónimos de períodos NO se reconocían, causando **vencimientos olvidados** (tareas con `dueAt=null` + residuo en el título → invisibles en What Now/planificador, sin recordatorio). Todos eran **asimetrías** frente a formas canónicas que SÍ funcionaban:
  1. **"que entra"** — variante regional MX/CA de "que viene"/"entrante" (la app usa `America/Santo_Domingo`): "el mes que entra", "la semana que entra", "el año que entra", "el 15 del mes que entra", "la semana que entra el lunes", "el lunes de la semana que entra".
  2. **"anterior"** — sinónimo pleno de "pasado" para períodos: "la semana anterior", "el mes anterior", "el año anterior" (asimetría: "...pasado" SÍ se fechaba, c.32 family).
  3. **"comienzos de mes/semana"** — sinónimo de "principios": "pago a comienzos de mes", "comienzos de semana" (asimetría: "principios de mes/semana" SÍ funcionaba, c.32/c.84).
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz**: (1) los 7 patrones de período próximo (`nextPeriodPattern` + 6 compuestos) listaban `que viene`/`próximo`/`entrante` pero NO `que entra`; el helper `monthBaseForBoundary` tampoco lo detectaba. (2) `lastPeriodPattern` sólo reconocía `pasado/pasada`, no `anterior`. (3) `startOfMonthPattern`/`startOfWeekPattern` sólo reconocían `principios`/`primeros`, no `comienzos`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  - (1) `que entra` como **alternativa léxica simétrica** en los 7 patrones (`nextPeriodPattern`, `nextMonthDayPattern`, `nextMonthDayReversePattern`, `nextWeekWeekdayReversePattern`, `nextWeekWeekdayForwardPattern`, `endOfMonthPattern`, `midOfMonthPattern`, `startOfMonthPattern`) + en la condición `isNext` del helper `monthBaseForBoundary`. Reusa la **misma** resolución +1 período que "que viene".
  - (2) `lastPeriodPattern` añade `anterior` como alternativa de `pasado/pasada` para semana/mes/año. Se procesa ANTES que `previousWeekdayPattern`, así "el mes anterior" se captura como período (no como día). "anterior" es siempre pasado, sin ambigüedad futura como "próximo".
  - (3) `startOfMonthPattern` + `startOfWeekPattern` añaden `comienzos?` junto a `principios?` (misma resolución día 1 / lunes).
- **Tests**: +11 tests de regresión en `NaturalTaskParserTest.kt`: 5 "que entra" (`laSemanaQueEntraResuelveProximaSemana`→2026-08-05, `elMesQueEntraResuelveProximoMes`→2026-08-28, `elAnioQueEntraResuelveProximoAnio`→2027-07-29, `elMesQueEntraConHoraAplicaHora`→10:00, `elNDelMesQueEntraResuelveDiaNDelMesSiguiente`→08-15), 4 "anterior" (`laSemanaAnteriorResuelveFechaPasada`→2026-07-22, `elMesAnteriorResuelveFechaPasada`→2026-06-29, `elAnioAnteriorResuelveFechaPasada`→2025-07-29, `elMesAnteriorConHoraAplicaHora`→15:00), 2 "comienzos" (`comienzosDeMesVarianteDePrincipios`→2026-08-01, `comienzosDeSemanaVarianteDePrincipios`→2026-08-03). `bash tools/run_domain_tests.sh` → **844 PASS** (833 c.125 + 11). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Auditoría de workers/backup/restore con DAOs reales queda pendiente.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 844 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — más gaps léxicos del parser (variantes regionales, formas compactas sin reconocimiento), y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA (sin Android SDK).

## Ciclo 127 — 2026-08-14 — Parser: "ayer tarde/ayer noche/anteayer tarde/antier tarde" compactos (sin conector) caían a 09:00 + residuo en título (P1 captura de eventos pasados)

- **Run/ciclo**: 127 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK, HEAD local = HEAD remoto = `4e46e52` (c.126). Sin divergencia, sin push concurrente al iniciar. Working tree limpio.
- **HEAD inicial**: `4e46e520aca45f29761f8d367aba53de60e2ba90` (c.126).
- **Problema seleccionado (P1 → integridad de captura de eventos pasados)**: la forma COMPACTA "día + parte del día" (sin conector "por la"/"en la") NO se reconocía para marcadores PASADOS: **"ayer tarde"**, **"ayer noche"**, **"ayer madrugada"**, **"anteayer tarde/noche/madrugada"**, **"antier tarde/noche/madrugada"** (`antier` = variante coloquial hispanoamericana de "anteayer", zona canónica `America/Santo_Domingo`). Caían a **09:00** (hora default) **+ residuo "tarde"/"noche"/"madrugada" en el título** → cita pasada mal agendada (15:00 real vs 09:00 agendado) Y mal titulada. Asimetría flagrante con las formas FUTURAS compactas "hoy tarde"/"mañana noche"/"pasado mañana tarde"/"antepasado mañana madrugada" que SÍ funcionaban (c.32/c.84). Al capturar eventos pasados el usuario usa la misma forma abreviada ("ayer tarde" = "ayer por la tarde"), tan cotidiana como las futuras.
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz**: `compactDayPartOfDayPattern` (`NaturalTaskParser.kt` ~l.620) sólo listaba marcadores FUTUROS en su alternancia: `antepasado mañana|pasado mañana|mañana|hoy`. Excluía `ayer|anteayer|antier`. La **resolución de fecha** para esos marcadores ya existía en el `when` de fecha (~l.1241: `ayer`→−1d, `anteayer`→−2d) y para `antier` también (c.32). Así sólo faltaba el reconocimiento de la parte del día en la forma compacta — el marcador fijaba la fecha correcta, pero "tarde"/"noche" no se consumía y la hora caía al default 09:00.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**: se añaden `ayer|anteayer|antier` a la alternancia del `compactDayPartOfDayPattern`: `\b(?:antepasado mañana|pasado mañana|mañana|hoy|anteayer|antier|ayer)\s+(tarde|noche|madrugada)\b`. Orden seguro: ninguno solapa con `mañana` (token de multi-caracteres), así que la precedencia del regex no se ve afectada. Reusa TODO el flujo existente: el `when` de fecha resuelve el día (ayer=−1, anteayer/antier=−2), el patrón compacto aporta la parte del día (tarde=15:00, noche=21:00, madrugada=04:00 vía `compactDayPartOfDayTimes`) y limpia el título (consumo en la cadena de limpieza ~l.1690). La combinación con hora explícita funciona por el mismo mecanismo que "hoy tarde a las 4" (contexto PM, c.84): "ayer tarde a las 4"→16:00.
- **Tests**: +8 tests de regresión en `NaturalTaskParserTest.kt`: `ayerTardeEsAyer15hYLimpiaTitulo`→2026-07-28 15:00, `ayerNocheEsAyer21hYLimpiaTitulo`→21:00, `ayerMadrugadaEsAyer4hYLimpiaTitulo`→04:00, `anteayerTardeEsAnteayer15hYLimpiaTitulo`→2026-07-27 15:00, `anteayerNocheEsAnteayer21hYLimpiaTitulo`→21:00, `antierTardeEsAnteayer15hYLimpiaTitulo`→15:00 (variante coloquial), `antierNocheEsAnteayer21hYLimpiaTitulo`→21:00, `ayerTardeConHoraSinMeridiemAplicaPm`→16:00 (simétrico de "hoy tarde a las 4"). `bash tools/run_domain_tests.sh` → **852 PASS** (844 c.126 + 8). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM 16 casos verde: controles futuros intactos (hoy/mañana/pasado/antepasado + parte del día), variantes "antier", "ayer tarde a las 4"=16:00, "anteayer noche a las 9"=21:00, sin residuo de título.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Auditoría de workers/backup/restore con DAOs reales queda pendiente.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 852 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — más gaps del parser (variantes regionales, formas compactas) y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA (sin Android SDK).

## Ciclo 128 — 2026-08-14 — Parser: "anoche"/"antenoche" (palabra única = "ayer noche"/"anteayer noche") no se reconocían → cita pasada en el futuro (P1 captura)

- **Run/ciclo**: 128 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK, HEAD local = HEAD remoto = `1d9d612` (c.127). Sin divergencia, sin push concurrente al iniciar. Working tree limpio.
- **HEAD inicial**: `1d9d612…` (c.127).
- **Problema seleccionado (P1 → integridad de captura de eventos pasados nocturnos)**: las palabras únicas **"anoche"** y **"antenoche"** (variantes coloquiales muy cotidianas que funden "ayer noche"/"anteayer noche" en una sola palabra, más frecuentes aún que la forma compacta de dos palabras del c.127) **no se reconocían**. "reunión anoche" → `dueAt=null` + residuo "anoche" en el título. Y peor: **"anoche a las 10" → HOY 10:00** en vez de AYER 22:00 — cita pasada agendada en el futuro (bug grave de captura: el usuario registra algo que pasó y la app lo programa para mañana). "antenoche a las 9" → HOY 09:00 en vez de ANTEAYER 21:00. Asimetría con "ayer noche"/"anteayer noche" (compactas, c.127), "esta noche" (c.32) y "anoche" del propio diccionario castellano que SÍ deberían funcionar.
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz**: ninguna rama del parser reconocía la palabra única "anoche"/"antenoche": el `when` de fecha requiere el token "ayer"/"anteayer" como palabra independiente; el `compactDayPartOfDayPattern` requiere "ayer"+"noche" como dos tokens separados. La palabra fusionada caía entre ambos mecanismos y, combinada con hora explícita, perdía también la fecha (quedaba HOY).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**: normalización temprana de `working` justo después de `text.trim()` que expande "antenoche"→"anteayer noche" y "anoche"→"ayer noche" (antenoche PRIMERO para evitar colisión de substring). Reutiliza TODO el flujo existente: el `when` de fecha fija ayer=−1d/anteayer=−2d; el `compactDayPartOfDayPattern` (c.127) aporta noche=21:00 y limpia el título; con hora explícita "anoche a las 10"→PM-context→22:00 (mecanismo del c.84). `\b` evita colisión ("anoche" es adverbio puro, no sustantivo común; "anochece"/"anochecer" verbo/sustantivo no se ven afectados por \banoche\b).
- **Tests**: +4 tests de regresión en `NaturalTaskParserTest.kt`: `anocheEsAyer21hYLimpiaTitulo`→2026-07-28 21:00, `anocheConHoraAplicaPmYFechaAyer`→AYER 22:00 (no HOY 10:00), `antenocheEsAnteayer21hYLimpiaTitulo`→2026-07-27 21:00, `antenocheConHoraAplicaPmYFechaAnteayer`→ANTEAYER 21:00 (no HOY 09:00). `bash tools/run_domain_tests.sh` → **856 PASS** (852 c.127 + 4). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM 24 casos verde: controles intactos ("esta noche"=hoy 21:00, "mañana de noche", "ayer al mediodía", "ayer tarde", "antier por la tarde"), sin residuo de título, sin falsos positivos ("anochece"/"al anochecer" siguen null — verbo/sustantivo, no adverbio, menos frecuentes y ambiguos).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Auditoría de workers/backup/restore con DAOs reales queda pendiente.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 856 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — más gaps del parser (variantes regionales, formas compactas) y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA (sin Android SDK).

## Ciclo 129 — 2026-08-14 — Parser: límites anuales ("fin/mediados/principios de año") + sinónimo "mitad" + calificador "este/esta mes" no se reconocían → vencimientos olvidados (P1 captura)

- **Run/ciclo**: 129 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK. HEAD inicial local = `2c8bcf9` (c.128 graft); el remoto había avanzado +2 commits (`33f353a` AutomationActionPlanner + `0421357` residuos/"al amanecer") que tocaban `NaturalTaskParser.kt`/`NaturalTaskParserTest.kt`. Stash → ff-only → stash pop: auto-merge limpio (regiones complementarias, sin colisión). Sin divergencia destructiva.
- **HEAD inicial**: `2c8bcf9…` (c.128) → tras sync `0421357` (c.128 remoto).
- **Problema seleccionado (P1 → integridad de captura de vencimientos anuales)**: tres familias NO se reconocían, causando **vencimientos olvidados** (tareas con `dueAt=null` + residuo en el título → invisibles en What Now/planificador, sin recordatorio):
  1. **Límites anuales** — "fin de año"/"a fin de año"/"finales de año"/"cierre de año"/"mediados de año"/"principios de año": vencimientos cotidianos (cierre fiscal, renovaciones, seguros, propósitos de año nuevo) caían a `dueAt=null` porque ningún patrón los reconocía. Y la subcadena "año" activaba "año que viene" como +365d genérico, adelantando "fin de año" a un año desde hoy en vez de al 31/12 real.
  2. **"mitad"** — sinónimo pleno de "mediados" en América Latina para mes/semana/año ("a mitad del mes"/"mitad de semana"/"mitad del año"): asimetría frente a "mediados" que SÍ funcionaba.
  3. **Calificador "este/esta mes"** — en `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern`: "finales de este mes"/"a finales de este mes" no casaba → +30d genérico (adelantaba al mes siguiente) y residuo en título; "este" sí estaba en `weekdayPattern`/`weekendPattern` pero no en los límites mensuales.
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz**: (1) no existían patrones para límites anuales (sólo mensuales/semanales); la subcadena "año" era capturada por `nextPeriodPattern` como +365d genérico tras fallar los límites. (2) `midOfMonthPattern`/`midOfWeekPattern` no listaban `mitad`, sólo `mediados`. (3) los 3 patrones de límite mensual no incluían `este\s+|esta\s+` antes de `mes`.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
  - (1) Nuevos `endOfYearPattern`/`midOfYearPattern`/`startOfYearPattern` (simétricos a los mensuales). Nuevo helper `yearBaseForBoundary` (simétrico a `monthBaseForBoundary`): "fin de año"→31/12 este año salvo hoy=31/12; "mediados de año"→30/6 salvo hoy≥30/6; "principios de año"→1/1 año siguiente salvo hoy=1/1; con calificador→año siguiente sin roll. Se detectan/borran ANTES del período próximo para que "año" no active +365d. `yearBoundaryDueAt` se inserta en la cascada de `dueAt` y en `hasDateOrTime`.
  - (2) `midOfMonthPattern`/`midOfWeekPattern` añaden `mitad` junto a `mediados`. `monthBaseForBoundary`/`yearBaseForBoundary` detectan `mitad` como `mid`.
  - (3) `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` añaden `(?:este\s+|esta\s+)` antes de `(?:pr[oó]xim[oa]\s+)?mes`.
- **Tests**: +7 tests de regresión en `NaturalTaskParserTest.kt`: `aFinDeAnoAncla31Diciembre`→2026-12-31, `finDeAnoSinPreposicionAncla31Diciembre`→2026-12-31, `finalesDeEsteMesAnclaFinMesActual`→2026-07-31 (no +30d), `mitadDelMesEsSinonimoDeMediados`→2026-08-15, `mitadDeSemanaEsSinonimoDeMediados`→2026-07-29 (miércoles), `mitadDelAnoAncla30Junio`→2027-06-30 (post-mediados→año siguiente), `mitadDelAnoAntesDeMediadosAnclaAnoActual`→2026-06-30. `bash tools/run_domain_tests.sh` → **897 PASS** (0 failures). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM 10 casos verde: "que entra" simétrico a "que viene", "anterior" (semana/mes/año), "comienzos/principios" sin residuo.
- **Hallazgo adicional**: el ítem P3 del backlog "este fin de semana/el fin de semana → dueAt=null" está RESUELTO de facto: el `weekendPattern` ya resuelve `dueAt` al próximo sábado (probe: 2026-08-01 sábado, correcto). El "residuo" del título cuando la entrada ES sólo la fecha es el fallback intencional `working.ifBlank { original }` (no se puede tener título vacío), consistente con todas las entradas que son sólo fecha. NO es bug. Ítem P3 marcado RESUELTO en BACKLOG.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `87eb7a2` (tras commit + push de este run).
- **Estado**: FIXED → VERIFIED (dominio JVM: 897 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — más gaps léxicos del parser y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento, workers/backup con DAOs reales).

## Ciclo 129 (run 3) - 2026-08-14 (UTC) - feat(parser): "al atardecer"/"al anochecer"/"al ocaso"/"al ponerse el sol" hora canónica vespertina + contexto PM (P1 captura olvidada / asimetría con "al amanecer")

- **Run/ciclo**: 129-run3 (rama `openhands/autonomous-ordia`). HEAD inicial `0421357` (c.129-run2 residuos+amanecer). Rebase sobre `87eb7a2` (c.129 límites anuales del run paralelo): auto-merge limpio en `NaturalTaskParser.kt`/`NaturalTaskParserTest.kt` (regiones complementarias), conflictos sólo en docs (CURRENT_STATE/RUN_LOG) resueltos conservando ambas entradas. Sin divergencia destructiva.
- **HEAD inicial**: `042135734c5ea07f687179f6286bb611b1f507e9` (c.129-run2); base rebaseada a `87eb7a2` (c.129 límites anuales).
- **Problema seleccionado (P1 → integridad de captura / asimetría canónica)**: las frases vespertinas **"al atardecer"**/**"al anochecer"**/**"al ocaso"**/**"al ponerse el sol"** (puesta/entrada de la noche, contraparte vespertina del amanecer) **no se reconocían** como hora canónica. "caminar al atardecer" → `dueAt=null` + residuo "al atardecer" en el título → tarea olvidada (invisible en What Now/planificador, sin recordatorio). **Asimetría flagrante** con `amanecerPattern` (06:00, añadido en c.129-run2): el amanecer se agendaba y el atardecer se perdía. Formas cotidianísimas de captura vespertina ("caminar al atardecer", "reunión al anochecer") que el usuario mete y la app descarta como "sin fecha".
- **Prioridad**: P1 (captura/persistencia de fechas; área de dirección "captura ultrarrápida" + "recuperación de información"). Cierra la asimetría creada en el run inmediatamente anterior.
- **Causa raíz**: ninguna rama reconocía las frases de puesta de sol. El amanecer (c.129-run2) cubría el amanecer/alba/despuntar el día/clarear/aclarar pero NO las contrapartes vespertinas. Gap descubierto por probe JVM (c.129-run3): `caminar al atardecer`→due=null, `reunión al anochecer`→due=null.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  1. Nuevo `atardecerPattern = (?i)al\s+(?:atardecer|anochecer|ocaso|ponerse\s+(?:el\s+sol|del\s+sol))\b` + `atardecerTime=LocalTime.of(18,0)`, declarado junto a `amanecerPattern` (simétrico). Hora de respaldo 18:00 (tarde tardía/ocaso, canónica ya usada por "a última hora"/"al final del día"; en el trópico la puesta de sol ronda ~18:30-19:00 y 18:00 es la canónica vespertina establecida, sin falsa precisión). Exige el conector "al " para NO casar el verbo ("atardece lloviendo") ni el sustantivo suelto ("un atardecer hermoso"). Como las demás horas canónicas, es hora de respaldo: si hay hora explícita, ésta gana y el patrón solo limpia "al atardecer".
  2. Match + fallback en la cadena de horas canónicas (`?: atardecerMatch?.let { atardecerTime }`) tras `amanecerMatch`. Limpieza en la cadena `replace` (`atardecerPattern.replace(value, " ")`).
  3. **Contexto PM**: `hasPartOfDayPmContext` ahora incluye `atardecerMatch != null`, así "al atardecer a las 7" → 19:00 (no 07:00). La puesta del sol es vespertina; "7" sin meridiem se interpreta como 7pm (mecanismo del c.84, simétrico de "esta tarde a las 4"→16:00). Gap descubierto por probe: antes "al atardecer a las 7"→07:00.
- **Tests**: +7 tests TDD en `NaturalTaskParserTest.kt`: `alAtardecerInterpretaOcasoYLimpiaTitulo`→2026-07-29 18:00, `alAnochecerEsSinonimoDeAtardecer`→18:00, `alOcasoEsSinonimoDeAtardecer`→18:00, `alPonerseElSolEsSinonimoDeAtardecer`→18:00, `alAtardecerCombinaConFechaRelativa`→2026-07-30 18:00, `alAtardecerHoraExplicitaTienePrioridad`→19:00 (PM context), `atardecerSinConectorAlNoEsFalsoPositivo` (control "ver el atardecer"→null). `bash tools/run_domain_tests.sh` → **897 PASS** (890 c.129-run2 + 7). `bash tools/run_domain_checks.sh` → smoke 25 OK. Probe JVM 8 casos verde: control amanecer intacto ("Ver un amanecer hermoso"→null, "meditar cada día al amanecer"→06:00 DAILY), sin residuo de título, sin falsos positivos.
- **Features**: 1 (familia canónica amanecer/atardecer completa — más potencia en captura sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Auditoría de workers/backup/restore con DAOs reales queda pendiente.
- **Hallazgos adicionales (descubrimiento continuo, probe c.129-run3)**: (a) residuo de título "de" en "llamar de mañana"→"llamar de" y "llamar a las 9 de la noche de mañana"→"llamar de" (P1 título, registrado para próximo run); (b) residuo "9" en "reunión 9 de la noche de mañana"→"reunión 9" (P1 título); (c) "pagar 1ro de septiembre"/"el 1ro de septiembre" ordinal sin artículo→null (P2 backlog ABIERTO); (d) "pagar el 31/30 de este mes"→null (P2, "de este mes" con día no casado); (e) "pago a mediados de la semana"→null (asimetría con "mediados de mes" que SÍ funciona, P2). Backlog ABIERTO P2: "día N" sin artículo, "1ro de septiembre" ordinal. Próxima prioridad: resolver residuo "de" en "de mañana"/"9 de la noche de mañana" (misma clase insidiosa de título mutilado que c.129-run2), y luego "mediados de la semana"; seguir descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría de workers/backup/restore con DAOs reales queda NO VERIFICADA.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 897 tests, 0 failures).

## Ciclo 129 (run 4) - 2026-08-14 (UTC) - fix(parser): residuo de título "de mañana"/"9 de la noche de mañana"/"desde hoy" (P1 integridad de título, misma clase insidiosa que c.129-run2)

- **Run/ciclo**: 129-run4 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK; `git pull --ff-only` limpio. HEAD inicial = `268ede2` (c.129-run3 "al atardecer"). Sin divergencia; trabajo sobre HEAD remoto actualizado.
- **HEAD inicial**: `268ede2` (c.129-run3).
- **Problema seleccionado (P1 → integridad de título del parser)**: tres formas cotidianísimas de captura se fechaban correctamente PERO **mutilaban el título** (contenido capturado degradado, insidioso: el usuario no ve el daño hasta revisar la tarea):
  1. **"llamar de mañana"/"tarea de hoy"/"cita de ayer"/"trabajo desde hoy"** → título "llamar de"/"reunión desde" (residuo de conector).
  2. **"reunión 9 de la noche de mañana"** → título "reunión 9" (residuo "9" — el número no casaba como hora).
  3. **"llamar a las 9 de la noche de mañana"** → título "llamar de" (residuo "de" del calificador de fecha).
  Mismo patrón insidioso del c.129-run2: fecha correcta, título degradado. Descubierto por probe JVM del c.129-run3 (registrado como ítem P1 ABIERTO en BACKLOG).
- **Prioridad**: P1 (integridad de datos/título; área de dirección "captura ultrarrápida" + "recuperación de información").
- **Causa raíz (dos defectos distintos)**:
  - (1) `standaloneHourPartOfDayPattern` (línea ~728) tenía un lookahead negativo `(?!\s+de\s+[a-záéíóúüñ])` diseñado para descartar "9 de la mañana de marzo" (no es forma real, protege de ambigüedad con mes). Pero la "m" minúscula de "mañana"/"mes" disparaba el rechazo en CUALQUIER "de <letra>", así que "9 de la noche de mañana" no casaba → el número "9" no se reconocía como hora y **quedaba como residuo en el título**.
  - (2) El borrado genérico de marcadores de día relativo (`mañana/hoy/ayer/anteayer/antier/pasado mañana/antepasado mañana`) como palabra suelta NO consumía el conector `de`/`del`/`desde` que lo precedía. "llamar de mañana" → borraba "mañana" pero "de" sobrevivía → "llamar de".
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  - (1) Lookahead de `standaloneHourPartOfDayPattern` refinado: ahora admite el calificador de fecha relativa tras la parte del día (`de hoy/mañana/ayer/anteayer/antier/pasado mañana/antepasado mañana`) vía un negative-lookahead anidado que excluye esos marcadores, pero sigue rechazando un nombre de mes ("9 de la mañana de marzo"→no casa, se protege por ambigüedad). Así "9 de la noche de mañana" casa → la hora se resuelve y el número se consume del título.
  - (2) La regex de borrado de día relativo ahora consume opcionalmente un conector precedente `(?:\b(?:de|del|desde)\s+)?` antes del marcador de día. "para" ya lo limpia su paso posterior dedicado; `\b` impide coincidir dentro de palabras ("desde" no se roba parcialmente). "de" sin día relativo tras él ("cambio de aceite") es preposición de contenido: no se toca (guard por contexto).
- **Tests**: +12 tests de regresión en `NaturalTaskParserTest.kt` (now=2026-07-29): `deMananaNoDejaResiduoDeEnTitulo`→2026-07-30 09:00 título "llamar"; `deHoyNoDejaResiduoDeEnTitulo`→07-29; `deAyerNoDejaResiduoDeEnTitulo`→07-28; `dePasadoMananaNoDejaResiduoDeEnTitulo`→07-31; `desdeHoyNoDejaResiduoDesdeEnTitulo`→07-29; `dePreposicionDeContenidoNoSeBorra` (guard "cambio de aceite"→null título intacto); `nueveDeLaNocheDeMananaResuelve21hYLimpiaTitulo`→07-30 21:00 título "reunión"; `nueveDeLaNocheDeHoyResuelve21hYLimpiaTitulo`→07-29 21:00; `nueveDeLaNocheDePasadoMananaResuelve21hYLimpiaTitulo`→07-31 21:00; `nueveDeLaMananaDeMananaResuelve9hYLimpiaTitulo`→07-30 09:00; `nueveDeLaTardeDeMananaResuelve21hYLimpiaTitulo`→07-30 21:00. `bash tools/run_domain_tests.sh` → **915 PASS** (904 c.129-run3 + 11). `bash tools/run_domain_checks.sh` (kotlinc en PATH) → smoke 25 OK. Probe JVM 24 casos verde: fixes + guards de no-regresión ("cambio de aceite"/"caja de herramientas"/"precio de oferta" intactos, meses "el 9 de marzo"/"15 de agosto" intactos, controles compactos "hoy noche"/"mañana tarde"/"ayer noche" intactos, "esta tarde"/"de la tarde de mañana"/"de la noche de hoy" intactos).
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: gap "reunión desde hoy"→"reunión desde" (mismo defecto de conector, no estaba en el probe original) detectado y corregido en este mismo run (ampliación natural del fix a `desde`). Gaps P2 del parser confirmados ABIERTOS en BACKLOG: "mediados de la semana"→null (asimetría con "mediados de mes"); "el 31 de este mes"/"día N de este mes"→null; "día 15" sin artículo→null; "1ro de septiembre" ordinal→null.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 915 tests, 0 failures).
- **Próxima prioridad**: gaps P2 ABIERTOS del parser (evaluar utilidad vs falso positivo: "mediados de la semana", "día N de este mes", "día 15" sin artículo, "1ro de septiembre" ordinal) y descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 130 - 2026-08-14 (UTC) - feat(parser): hora aproximada "a eso de"/"hacia"/"cerca de"/"alrededor de"/"sobre" → dueAt=null/título mutilado (P1 captura olvidada + integridad de título)

- **Run/ciclo**: 130 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK; `git pull --ff-only` limpio. HEAD inicial = `ccbb450` (c.129-run4 "de mañana"/"9 de la noche de mañana"/"desde hoy"). Sin divergencia; trabajo sobre HEAD remoto actualizado.
- **Problema seleccionado (P1 → integridad de captura + título del parser)**: el usuario capta una hora sin precisión exacta con marcadores aproximativos cotidísimos y NO se reconocían:
  1. **"llamar a eso de las 5"** → `dueAt=null` + título "llamar a eso de las 5" → tarea **olvidada** (sin vencimiento, invisible en What Now/planificador, sin recordatorio).
  2. **"reunión sobre las 3 de la tarde"** → la hora SÍ se resolvía (15:00) pero el marcador "sobre las" sobrevivía como residuo en el título → "reunión sobre las" (cita bien fechada, título mutilado).
  3. Mismo defecto para "hacia"/"cerca de"/"alrededor de": "pasa hacia las 4 de la tarde"/"llego cerca de las 10 de la mañana"/"cobro alrededor de las 9 de la noche" → todos `dueAt=null`.
  Asimetría flagrante con "a las 5" que SÍ funcionaba. Forma ultra-común en captura rápida móvil. Descubierto por probe JVM de descubrimiento continuo (no estaba en BACKLOG).
- **Prioridad**: P1 (integridad de datos/título + área de dirección "captura ultrarrápida" + "recuperación de información": una cita con hora que cae a null se olvida).
- **Causa raíz**: ningún patrón normalizaba los marcadores aproximativos ("a eso de", "hacia", "cerca de", "alrededor de", "sobre") a la forma canónica "a las"/"a la" que ya reconoce `timePatterns`. Así la hora subyacente quedaba sin capturar (o capturada vía otra rama pero con el marcador como residuo).
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva, sin fingir precisión)**: nuevos `approximateTimePatterns` (3 regex) que normalizan el marcador a "a " y conservan intacto el resto ("las 5", "la una", "3 de la tarde"), reutilizando TODO el flujo de hora explícita existente (misma resolución AM/PM, misma limpieza del título). Se aplica tras `anoche` y antes de `lower`. La hora resultante es la mejor estimación del usuario (no se simula precisión).
  - **"a eso de"** es adverbio temporal puro (sin uso de tema/cantidad) → admite hora en punto sin meridiem ("a eso de las 5" → 05:00): el caso más común y de mayor impacto (tarea olvidada).
  - **"sobre"/"hacia"/"cerca de"/"alrededor de"** admiten usos de tema ("sobre las ventas") y de cantidad ("sobre las 3 cajas"), así que exigen evidencia de reloj INMEDIATA tras la hora (minutos `:MM`, meridiem `am/pm`, parte del día u "horas/hs") para no agendar una cuenta como cita. La hora en punto sin meridiem de estos 4 queda fuera por ambigua.
  - Descubierto y corregido en este run un **falso positivo** inicial: "comprar sobre las 3 cajas de leche" → la primera versión (lookahead flojo) agendaba 03:00; el lookahead estricto (evidencia de reloj) lo rechaza → `dueAt=null`, título intacto.
- **Tests**: `bash tools/run_domain_tests.sh` → **931 PASS** (920 base post-merge c.130 + 11 nuevos). `bash tools/run_domain_checks.sh` → smoke 25 OK. +11 tests en `NaturalTaskParserTest.kt` (now=2026-07-29): 8 positivos (`aEsoDeLasCincoResuelveHoraYLimpiaTitulo`→05:00, `aEsoDeLasDiezDeLaMananaResuelve10hYLimpiaTitulo`→10:00, `aEsoDeLaUnaDeLaTardeResuelve13hYLimpiaTitulo`→13:00, `sobreLasTresDeLaTardeResuelve15hYLimpiaTitulo`→15:00, `sobreLaUnaDelMediodiaResuelve13hYLimpiaTitulo`→13:00, `haciaLasCuatroDeLaTardeResuelve16hYLimpiaTitulo`→16:00, `cercaDeLasDiezDeLaMananaResuelve10hYLimpiaTitulo`→10:00, `alrededorDeLasNueveDeLaNocheResuelve21hYLimpiaTitulo`→21:00, todos título limpio) + 3 negativos (`sobreLasVentasNoEsHora`→null título intacto, `sobreLasTresCajasNoEsHora`→null título intacto, `informeSobreElClienteNoEsHora`→sólo fecha por "del jueves"). Probe JVM 19 casos verde (fixes + guards de no-regresión: "a las 5" intacta, "reunión a las 3 de la tarde" intacta, "sobre las ventas"/"sobre las 3 cajas"/"informe sobre el cliente" intactos).
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: el falso positivo "sobre las 3 cajas" detectado DURANTE el desarrollo obligó a endurecer el lookahead (evidencia de reloj requerida para sobre/hacia/cerca/alrededor). Gaps P2 del parser confirmados ABIERTOS en BACKLOG: "mediados de la semana"→null, "día N de este mes"→null, "día 15" sin artículo→null, "1ro de septiembre" ordinal→null.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 931 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — gaps léxicos del parser (formas compactas, variantes regionales) y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 132 (run 1) - 2026-08-14 (UTC) - fix(automation): `RESCHEDULE_OVERDUE` no descarta recordatorios (P1 evitar olvidos)

- **Run/ciclo**: 131-run1 (rama `openhands/autonomous-ordia`). Base sincronizada: ya en `openhands/autonomous-ordia`; al hacer `git fetch` el remoto había avanzado 1 commit (otra ejecución), se hizo stash + `pull --ff-only` limpio sobre `26f4be6` (c.130 "hora aproximada") y se reaplicaron los cambios. Sin divergencia destructiva. HEAD inicial de mi trabajo = `4795088` (c.129 "mediados de la semana"); base rebaseada a `26f4be6` (c.130).
- **HEAD inicial**: `479508817f9edb5fa0bb8cd1c6b599b03c087444` (base rebaseada a `26f4be6` tras merge del c.130 del otro run).
- **Problema seleccionado (P1 → recordatorios / recuperación de vencidas / evitar olvidos)**: `AutomationActionPlanner.RESCHEDULE_OVERDUE` reprogramaba tareas vencidas pero calculaba el recordatorio como `task.reminderAt?.let { due - 1h }`. Dos defectos de producto:
  1. Una vencida **sin** `reminderAt` quedaba **sin recordatorio** tras la reprogramación → podía olvidarse de nuevo (contradice directamente la misión "evitar olvidos"). El fix anterior (c.129) cubría `PLAN_DAY`/`BATCH_QUICK_TASKS` (añaden reminder cuando no existía) pero NO este caso de `RESCHEDULE_OVERDUE`.
  2. Si la tarea tenía un offset de reminder distinto de 1 h (p.ej. 2 h antes), se sobrescribía a 1 h → **corrompía la cadencia de ocurrencias recurrentes**, que `RecurrenceEngine` reutiliza como offset para todas las ocurrencias futuras.
- **Prioridad**: P1 (persistencia/recordatorios/recuperación de vencidas; área de dirección "recuperación de tareas olvidadas" + "mejores recordatorios"). Hallazgo de auditoría de motores no-parser (fuera del parser, como recomienda la misión).
- **Causa raíz**: el operador `?.let` descartaba silenciosamente el caso `reminderAt == null` (sin reminder → seguía sin reminder) y el cuerpo siempre forzaba el offset a 1 h sin respetar el offset del usuario.
- **Solución (mínima, `AutomationActionPlanner.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  - Si la tarea tenía `reminderAt` Y `dueAt`: **conserva el offset original del usuario** (`task.dueAt - task.reminderAt`) aplicándolo al nuevo vencimiento — protege la cadencia recurrente.
  - Si no tenía reminder: **añade uno 1 h antes del nuevo vencimiento** (siempre futuro) — coherente con `PLAN_DAY`/`BATCH_QUICK_TASKS` (c.129), que añaden un recordatorio por defecto cuando no existía.
- **Tests**: +2 tests en `AutomationActionPlannerTest.kt` (`reschedule_overdue conserva el offset de reminder del usuario` → offset 2 h preservado, no forzado a 1 h; `reschedule_overdue anade reminder cuando no existia` → reminder futuro añadido, `reminderAt > now`). Se reemplazó el test antiguo `reschedule_overdue reprograma vencidas a partir de manana` que asumía el comportamiento viejo (offset forzado a 1 h). `bash tools/run_domain_tests.sh` → **932 PASS** (931 base c.130 del otro run + 2 nuevos - 1 reemplazado = +1 neto). `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Features**: 0 (corrección de integridad de recordatorios existente — más potencia sin nueva interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); integración `AutomationEngine.runRule`/`AutomationWorker` con DAOs/WorkManager reales queda pendiente.
- **Hallazgos adicionales**: la auditoría previa de motores no-parser (WhatNowEngine, GuardianEngine, DayPlanner, SubtaskRules, TaskRules, RecurrenceEngine) no reveló bugs; `AutomationActionPlanner` sí. Continuar con SummaryEngine, GuardianCoach, UniversalCaptureEngine, ReminderRules, LearningEngine, RoutineRules, HabitRules en próximos runs.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/automation/AutomationActionPlanner.kt`, `app/src/test/java/com/ordia/app/automation/AutomationActionPlannerTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 932 tests, 0 failures).
- **Próxima prioridad**: continuar auditoría de motores no-parser restantes y descubrimiento continuo en producto (What Now, Guardián, contexto, onboarding, navegación, accesibilidad, rendimiento).


## Ciclo 131 (run 2) - 2026-08-14 (UTC) - feat(parser): ordinales de fecha "1ro/2do/3er/1º" + "día N de este mes" (P2 captura olvidada + integridad de título)

- **Run/ciclo**: 131 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch origin openhands/autonomous-ordia` OK; `git pull --ff-only` limpio. HEAD inicial = `26f4be6` (c.130 "hora aproximada"). Sin divergencia; trabajo sobre HEAD remoto actualizado.
- **Problema seleccionado (P2 → integridad de captura + título del parser)**: dos gaps léxicos del parser confirmados ABIERTOS en BACKLOG (c.129 probe):
  1. **"pago el 1º de septiembre"** dejaba el título **mutilado** `pago º de septiembre` (fecha resuelta, contenido corrompido); **"pagar el 1ro de septiembre"/"renta el 2do de cada mes"** → `dueAt=null` (cita olvidada, sin recordatorio, invisible en What Now/planificador). Los sufijos ordinales ("1ro"/"2do"/"3er"/"5to"/"7mo"/"8vo"/"9no"/"10mo" y símbolos "1º"/"2ª") rompían los patrones de fecha (`\d{1,2}` exige dígito seguido de espacio, así que "1ro" dejaba "ro" como residuo).
  2. **"reunión el 15 de este mes"** → `dueAt=null`. Asimetría flagrante: "el 15" (día suelto) SÍ funcionaba; el calificador "de este mes" no casaba ningún patrón.
- **Prioridad**: P2 (integridad de datos/título + área de dirección "captura ultrarrápida" + "recuperación de información": una cita con fecha que cae a null se olvida; un título mutilado degrada el contenido sin que el usuario lo note hasta revisar la tarea).
- **Causa raíz (dos defectos distintos)**:
  - (1) Ningún paso del pipeline normalizaba el sufijo ordinal a su dígito base. Los patrones de fecha (`monthNameDate`/`dayOfMonthPattern`/`monthlyDayPattern`) exigen `\d{1,2}` seguido de espacio; "1ro de septiembre" deja "ro" como residuo → la fecha se perdía (`dueAt=null`) o, en el caso del símbolo "º" (no-letra, no-dígito), el `\b` de limpieza se rompía y el título quedaba mutilado ("pago º de septiembre").
  - (2) `dayOfMonthPattern` no reconocía el calificador de mes actual "de este mes"/"del mes"; solo casaba "el N" suelto.
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva, sin fingir IA)**:
  - Nuevo `ordinalSuffixPattern = (?i)\b(\d{1,2})(?:ro|do|er|to|mo|vo|no|º|ª)(\s+del?\s+)` normaliza el ordinal a su dígito base **SOLO** cuando va seguido del conector de fecha " de "/" del " (contexto de fecha inequívoco), conservando el conector en group2. "el 1ro de septiembre"→"el 1 de septiembre" reutiliza TODO el flujo existente (monthNameDate, monthlyDay, dayOfMonth, recurrencia, hora explícita, título limpio). Se aplica tras `anoche` y antes de `lower`/`approximateTimePatterns`. Cubre los sufijos escritos ("1ro"/"2do"/"3er"/"4to"/"5to"/"7mo"/"8vo"/"9no"/"10mo") y los símbolos ("1º"/"2ª"); "primero" escrito no se toca ("primer capítulo"=contenido).
  - **Anti-falso-positivo clave (descubierto DURANTE el run)**: la primera versión normalizaba el sufijo incondicionalmente, lo que agendaba **"ver el 3er capítulo"** como fecha espuria (3→Sep 3). El endurecimiento —exigir el conector " de "/" del " tras el ordinal— evita agendar contenido: "ver el 3er capítulo"/"comprar 2do piso del edificio"/"presentación 1ª edición del libro" (sin " de [mes]" tras el ordinal → no se normaliza → no se agenda). Verificado con probe JVM.
  - `dayOfMonthPattern` añade `(?:\s+(?:del?\s+mes|de\s+este\s+mes))?` como calificador de mes actual, con `negative lookahead` `(?!\s*de\s+[a-záéíóúüñ])` que rechaza "de <mes-nombrado>" (lo resuelve `monthNameDate`), "de cada mes" (`monthlyDayPattern`) y "de este proyecto" (contenido). "de este" restringido a "de este mes" (no casa "el 15 de este proyecto").
- **Tests**: `bash tools/run_domain_tests.sh` → **940 PASS** (931 c.130 + 9 nuevos). `bash tools/run_domain_checks.sh` → smoke 25 OK. +9 tests en `NaturalTaskParserTest.kt` (now=2026-07-29): 4 positivos ordinales (`ordinalNumericSuffixParsesAsDate`→2026-09-01 título "pago", `ordinalSymbolParsesAsDate`→2026-09-01 título "pago", `ordinalSuffixMonthlyRecurrence`→MONTHLY 2026-08-02, `ordinalSuffixDelMes`→MONTHLY 2026-08-05 10:00) + 2 guards ordinales (`ordinalContentWordNotScheduled` "ver el 3er capítulo"→null título intacto, `ordinalContentPisoNotScheduled` "comprar 2do piso del edificio"→null título intacto) + 2 "de este mes" (`dayOfMonthDeEsteMes`→2026-08-15, `dayOfMonthDeEsteMesWithDiaWord`→2026-08-15) + 1 guard (`deEsteMesNotMatchingDeEsteProyecto` "el 15 de este proyecto"→null título intacto). Probe JVM 25 casos verde: fixes + guards de no-regresión ("3er capítulo"/"2do piso"/"1ª edición" intactos, "el 15 de este proyecto"/"el 15 de este" intactos, "el 15 de marzo"/"el 15 de cada mes" intactos, recurrencias mensuales con ordinal correctas, "1º de cada mes"→MONTHLY).
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Hallazgos adicionales**: el falso positivo "ver el 3er capítulo" detectado DURANTE el desarrollo (primera versión incondicional) obligó a endurecer el patrón al contexto de fecha (" de "/" del "). Caso límite preexistente confirmado NO REGRESIÓN: "el 10 de octubre" (fecha pura sin contenido) ya dejaba residuo de título antes de este cambio (comportamiento del cleanup de título ante input de solo-fecha, fuera de alcance de este fix atómico); con contenido presente el título se limpia correctamente. Gap P2 ABIERTO restante: "día N" sin artículo ("pago día 15"→null).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: `476eb7a` (fix "día N" sin artículo; tras commit, push pendiente).
- **Estado**: FIXED → VERIFIED (dominio JVM: 940 tests, 0 failures).
- **Próxima prioridad**: gap P2 ABIERTO "día N" sin artículo ("pago día 15"→null, evaluar falso positivo vs utilidad); descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 134 - 2026-08-14 (UTC) - fix(parser): "el N del mes actual"/"del presente mes"/"de este mismo mes" (P1 falsa recurrencia mensual + captura olvidada)

- **Run/ciclo**: 134 (rama `openhands/autonomous-ordia`). Base sincronizada con **divergencia resuelta**: el run anterior (T131, USER_CONTEXT) partió de un commit local `8ef7392` que NO estaba en el remoto; el remoto había avanzado a `44d1725` con un enfoque distinto sobre el mismo código (c.132/c.133: "día N" sin artículo, asistente). Un rebase interactivo quedó a medio terminar con conflictos en docs `AI_AUTONOMY/*`. Acción: `git rebase --abort` (descarta el estado de rebase roto, no destructivo sobre commits válidos), luego `git reset --hard origin/openhands/autonomous-ordia` para alinearse al HEAD remoto `44d1725` (descarta trabajo obsoleto `8ef7392` ante divergencia, permitido por AGENTS §1 "si el pull falla por divergencia... descarta tu trabajo local no commiteado y trabaja sobre el HEAD remoto actualizado"). El fix de T131 (código del lookahead) se reaplicó **manualmente** sobre la base remota limpia. HEAD inicial = `44d1725`.
- **Problema seleccionado (P1 → integridad de datos + evitar olvidos + captura)**: dos defectos encadenados en el parsing de **"el N del mes actual"/"del presente mes"/"de este mismo mes"** (sinónimos cotidianos de "de este mes" para un compromiso ÚNICO del mes en curso):
  1. **Falsa recurrencia mensual**: `monthlyDayPattern` (línea ~2140) se ejecuta ANTES que `dayOfMonthPattern` dentro de `parseRecurrence`; su regex `(?:de|del)\s+(?:cada\s+)?mes(?:es)?\b` casaba "31 del mes" en "el 31 del mes actual" **sin consumir "actual"** → la tarea nacía como recurrencia **MONTHLY falsa** (un compromiso único del mes en curso quedaba convertido en tarea repetitiva mensual) y "actual" sobrevivía como residuo en el título. Compromiso único del mes en curso **perdido como tal** + título sucio.
  2. **Gap léxico de fecha única**: `dayOfMonthPattern` (línea ~430) solo admitía "del mes"/"de este mes" como calificador de mes en curso (enfoque restrictivo del remoto c.131/c.133); "del presente mes"/"del mes actual"/"de este mismo mes" no casaban → una vez rechazado por `monthlyDayPattern` (con el fix del lookahead), esas frases caerían a `dueAt=null` (vencimiento olvidado).
- **Prioridad**: P1 (integridad de datos: un compromiso único del mes en curso se corrompía a recurrencia mensual falsa; además área de dirección "captura ultrarrápida"/"evitar olvidos"). El defecto (1) es el más grave: falsea el TIPO de compromiso (único vs recurrente), lo que afecta recordatorios futuros (`RecurrenceEngine` genera ocurrencias para MONTHLY) y la visibilidad en el planificador de hábitos.
- **Causa raíz**: dos causas independientes. (1) `monthlyDayPattern` no distinguía "del mes" genérico (recurrencia) de "del mes actual/presente/este" (fecha única concreta) — la regex era demasiado permisiva y se ejecutaba antes en la cascada de `parseRecurrence`. (2) `dayOfMonthPattern` no cubría los sinónimos de mes en curso que sí cubría para "de este mes".
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva, sin fingir IA)**:
  - (a) **Lookahead negativo en `monthlyDayPattern`**: `(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))` tras `mes(?:es)?`. Rechaza "del mes" cuando le sigue un calificador de mes CONCRETO (actual, presente, este, entrante, próximo, siguiente, "que viene/entra/sigue") — esos son fecha única. Solo "el N del mes" a secas y "de cada mes" casan como MONTHLY (decisión de producto: forma genérica recurrente). Las recurrencias legítimas ("el 15 de cada mes", "el día 15 de cada mes", "el 2do de cada mes") NO se ven afectadas (no contienen esos calificadores).
  - (b) **`dayOfMonthPattern` amplía el grupo opcional** a `(?:mes\s+actual|presente\s+mes|este\s+(?:mismo\s+)?mes|mes)` — consume TODOS los sinónimos de mes en curso. **Orden de alternativas**: las frases largas ("mes actual", "presente mes", "este mismo mes") van PRIMERO para que la regex greedy no casee "mes" y deje " actual"/" presente" como residuo (bug descubierto y corregido DURANTE el run: la primera versión ordenaba "mes" primero y el test `elNDelMesActualEsSinonimoDeEsteMes` fallaba con `expected:<Envío[]> but was:<Envío[ actual]>`). El lookahead negativo existente `(?!\s*del?\s+[a-záéíóúüñ])` sigue protegiendo contra referencias no temporales ("día 15 del libro") y colisiones con `monthNameDate` ("el 15 de marzo").
  - Reusa TODO el flujo existente (`nextMonthlyDate` para la resolución con roll si el día ya pasó, hora explícita, limpieza del título).
- **Tests**: `bash tools/run_domain_tests.sh` → **959 PASS** (956 c.133 + 3), 0 failures. `bash tools/run_domain_checks.sh` → smoke 25 OK. +3 tests en `NaturalTaskParserTest.kt` (now=2026-07-29): `elNDelMesActualEsSinonimoDeEsteMes` ("Envío el 31 del mes actual"→2026-07-31 título "Envío", `recurrence=NONE`), `elNDelPresenteMesEsSinonimoDeEsteMes` ("Cobro el 15 del presente mes"→2026-08-15, `NONE`), `elNDeEsteMismoMesEsSinonimoDeEsteMes` ("Pago el 5 de este mismo mes"→2026-08-05, `NONE`). Los 3 verifican que NO hay recurrencia (la aserción `assertEquals(RecurrenceFrequency.NONE, result.recurrence)` es la prueba directa del defecto P1). No-regresión: las recurrencias mensuales legítimas existentes ("el 15 de cada mes", "el 29 de cada mes", "el día 15 de cada mes", "el 2do de cada mes") siguen pasando.
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); la cascada completa de `parseRecurrence`↔`dayOfMonthPattern` con `RecurrenceEngine` generando ocurrencias reales queda pendiente en integración Android.
- **Hallazgos adicionales**: bug de orden de alternativas en regex descubierto DURANTE el desarrollo (la primera versión de `dayOfMonthPattern` ordenaba "mes" primero en la alternancia, causando residuo " actual"); corregido poniendo las frases largas primero. Nota de Git: el commit obsoleto `8ef7392` del run T131 se descartó por divergencia (remoto reemplazó su enfoque); su ESPECIFICACIÓN (admitir "del presente mes" etc.) se reformuló sobre la base remota limpia con enfoque coherente (lookahead + ampliación de grupo opcional), sin perder trabajo válido.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt` (`monthlyDayPattern` + `dayOfMonthPattern`), `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt` (+3 tests), `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **HEAD final**: (tras commit) pendiente de push a `openhands/autonomous-ordia`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 959 tests, 0 failures).
- **Próxima prioridad**: descubrimiento continuo — más gaps léxicos del parser (variantes regionales, formas compactas, residuos) y áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

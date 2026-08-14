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

- **Fecha (UTC)**: 2026-08-13 (ciclo 83; feat P1 inteligencia/asistente: "¿Qué hago ahora?" del asistente ahora delega en `WhatNowEngine.suggest` (motor honesto de What Now) y **explica por qué** esa tarea va primero ("está vencida"/"empieza enseguida"/"vence hoy"/"es urgente"… vía nuevo `WhatNowEngine.reasonLabel`) + avisa cuántas hay vencidas ("Además, tienes N vencidas"). El "plan mínimo" reemplaza su ordenador divergente (`priority`→`dueAt`) por `WhatNowEngine.ordered` —misma ordenación que What Now/widget—, así las tres superficies (What Now, asistente, widget) muestran el mismo orden. Antes el asistente usaba `TaskRules.nextBestTask` (ya time-aware desde c.45) pero NO explicaba la razón ni mencionaba vencidas, y el plan mínimo tenía un comparador aparte que divergía del de What Now (p.ej. vencida de prioridad normal vs urgente no vencida: What Now = vencida por rango, plan mínimo viejo = urgente por prioridad). Refactor DRY: `WhatNowEngine.suggest` ahora reusa el nuevo `ordered(...)` (fuente única del ranking, antes el comparador estaba duplicado). Sin nueva pantalla/botón. +2 tests (`whatNow_explainsWhyAndMentionsOverdue`, `planMinimo_ranksOverdueFirst`); 602 domain tests PASS (assistant compilado/ejecutado aparte: 5 AssistantEngineTest PASS); smoke 25 OK; NO VERIFICADO gradle/Android/UI/Room; + c.82 fix P1 parser rango horario con meridiem solo en el INICIO "de 6pm a 8"/"de 2pm a 4"/"de 6 de la tarde a 8": el fin bare NO heredaba el PM del inicio → `endHr=8 < startHr=18` violaba la guarda `endMin>startMin` → rango rechazado, `durationMinutes=null` Y título sucio ("Reunión de a 8"); ahora `startPmEffective` propaga el PM del inicio al fin bare (mismo lado del mediodía) → 18:00→20:00, dur 120, con anti-falso-positivo (`midnightWrap` solo si el fin NO va seguido de un sustantivo de cantidad "de 2pm a 4 entradas") y cruce inverso "de 11pm a 1" (inicio PM, fin bare con `endHr<startHr` → NO hereda PM, envuelve +24h → 23:00→01:00, dur 120); unifica en un único bloque `rangeMatch` que cubre mismo-día, medianoche y el simétrico cruce de mediodía, reemplazando el wrap ad hoc de c.80; +12 tests, 593 domain tests PASS; + c.81 feat P2 búsqueda semántica por fecha en `SearchEngine` ("hoy"/"mañana"/"esta semana"/"atrasadas/vencidas" filtran por `dueAt` no por texto; +9 tests, 597 domain tests PASS); + c.80 fix P1 parser cruce de medianoche "Cena de 10 de la noche a 1 de la madrugada": `rangeMatch` exigía `endMin > startMin` → overnight rechazado y `durationMinutes=null`; ahora `sameDay` vs overnight con `rawDuration = if (sameDay) end−start else end+24h−start`, clamp 5..24h, cruce solo con señal clara; +4 tests, 588 domain tests PASS; + c.79 fix P1 parser cruce del mediodía en rango horario con meridiem solo al final "de 12 a 2 de la tarde"/"de 11 a 1 de la tarde": (1) la duración se computaba con horas crudas del texto (2−12=−600) y coerceIn dejaba 5 min en vez de 120; (2) la propagación PM del c.76 era ciega → "de 11 a 1 de la tarde" convertía el inicio 11→23 (dueAt=23:00); ahora la duración usa horas absolutas resueltas (sAbs/eAbs) y la propagación PM es condicional `startHr <= endHr` (mismo lado del mediodía) aplicada simétricamente a rangeMatch/rangeStartTime; +6 tests, 584 domain tests PASS; + c.78 fix P1 parser rango horario con meridiem compacto solo en el extremo final "de 6 a 8 pm"/"de 9 a 11 am": el `explicitTime` capturaba el extremo final "8 pm"→20:00 y sombreaba `rangeStartTime` (18:00) → dueAt caía a la hora de FIN (20:00), no de inicio (18:00); recordatorio 2h tarde; ahora detección posicional `explicitTimeIsRangeEnd` deja ganar a `rangeStartTime` (inicio, con propagación PM del c.76) → 18:00, duración correcta 120; horas sueltas "Llamada 8pm"→20:00 sin regresión; + c.77 feat P1 resumen: el veredicto del día `DayLoad` ahora usa la ventana de jornada APRENDIDA (`LearningProfile`) en vez de la fija 9–18 → la tarjeta de hoy deja de mentir para horarios no estándar (jornada 9–23 a las 17:00 = ON_TRACK no OVERLOADED; jornada 6–14 a las 13:00 = OVERLOADED no ON_TRACK); `SummaryEngine.summarize`/`assessDayLoad` reciben `dayStartMinute`/`dayEndMinute` (defaults 9–18) + sobrecarga con `LearningProfile?`; `TodayScreen` computa el perfil igual que `PlannerScreen` y lo pasa; +4 tests; + c.76 fix P1 parser rango horario "de 6 a 8 de la tarde": el meridiem PM del extremo final no se propagaba al inicio sin meridiem → agendaba 06:00 en vez de 18:00 (recordatorio 12h antes de la cita real); ahora el inicio bare hereda el PM del extremo final "de la tarde/noche" → 18:00, duración correcta 120; + c.75 fix parser "fin/mediados/principios del mes que viene/próximo" ancla al mes siguiente (no al actual) + fix "antepasado mañana" → +3 días y título limpio; + c.74 fix P1 RecurrenceEngine anual anclado a 29 de febrero: `plusYears(1)` clampaba a 28/2 y derivaba el ancla para siempre (cumpleaños/aniversario bisiesto perdido) → ahora `nextYearly` salta años no bisiestos, simétrico al `nextMonthly` 31 de c.18; + c.73 fix parser "la semana que viene el lunes/viernes" → día objetivo de la semana próxima, no +7d genérico; + c.72 fix parser "jueves que viene" dicho en jueves -> proxima semana (no HOY); + c.71 fix parser orden inverso "el mes que viene el N" agenda día N del mes siguiente; + c.70 fix P0 crash parser día fuera de rango "el 0/99 de mes" + fix P1 "el 15 de agosto del 2027" no capturaba año "del" → fecha errónea 2026↔2027 y título "del 2027" huérfano; + c.69 feat resumen accionable: un toque pospone la tarea sugerida a mañana)
- **Branch de trabajo**: `openhands/autonomous-ordia` (sobre c.70 fix parser P0/P1 + c.69 feat resumen accionable un-toque + c.68 fix parser "el N del mes que viene" día N del mes siguiente + c.67 refina sugerencia de posposición: nunca nombra tareas en curso ni inminentes + c.66 sugerencia concreta de tarea a posponer cuando el día está saturado + c.65 veredicto honesto del día + c.64 fix parser standalone "N de la tarde/noche" + fix P1 `monthNameMatch.find()` casaba mes inválido + c.63 detección de vencidas importantes en el Guardián + c.62 integridad de recordatorios en el editor + c.61 meridiem sin "a las" + c.60 rango-minutos/meridiem + c.59 verbo-recordatorio + c.58 fracción sub-hora/"en la tarde" + c.57 número-escrito + c.56 subtarea-autocomplete + c.55 partOfDay DAILY + c.54 intervalo+días + c.53 What Now + c.52 snooze)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

| P1/P2 | Parser — fechas relativas/pasadas/imposibles + rango horario + recurrencias laborables/quincenal/bare + día de mes suelto | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 base (353 tests) + ampliación followers c.42 cont. (358 tests); recurrencia quincenal "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura → hoy c.42 cont.2 (362 tests); listas de días sin prefijo ("gym sábados y domingos") c.42 (369 tests); "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY [1-5] c.43 (376 tests); fecha/hito "la quincena" (1ra/2da/sin cualificar) c.44 (388 tests); `nextBestTask` time-aware (widget/asistente) c.45 (394 tests); **"el 15" día de mes suelto con artículo** c.47 (394+4 tests); **"de aquí a N"/"de acá a N" prefijo relativo coloquial** c.50 (413 tests); **DayPlanner conflicto startAt otro día** c.51 (415 tests); **intervalo+días "cada 2 semanas los lunes"/"cada quincena los lunes y viernes"/"cada 3 semanas de lunes a viernes"** c.54 (428 tests); **"cada mañana/tarde/noche/madrugada" + "todas las mañanas/tardes/noches" como recurrencia DIARIA** con hora canónica (c.55, 435 tests); **autocompletar padre al cerrar última subtarea desde notificación** (`ReminderActionReceiver.ACTION_COMPLETE` ↔ `SubtaskRules.shouldAutoCompleteParent`) (c.56); **intervalo con número escrito "cada dos semanas"/"cada tres meses"/"cada quince días"/"cada dos años"** (c.57, 439 tests); **fracción sub-hora "a las 9 y media"/"a las 3 y cuarto" (media→30, cuarto→15) + conector caribeño "en la tarde/noche/mañana"** (c.58, 450 tests); **verbo de recordatorio sin cantidad "recuérdame/avísame/no dejes que olvide" con fecha límite → recordatorio 30 min antes + verbo limpiado del título** (c.59, 455 tests); **rango horario con minutos/meridiem en ambos extremos "clase de 9:30 a 11"/"de 9am a 11am"/"de 2pm a 4pm" → duración real (fin−inicio) en minutos + título limpio** (c.60, 463 tests); **meridiem sin "a las" "Reunión 2pm"→14:00 (era 02:00) + hora de inicio del rango como dueAt "de 9 de la tarde a 11 de la noche"→21:00 (era 15:00)** (c.61, 469 tests); **editor de tareas preserva el offset de recordatorio personalizado al editar campos no relacionados** (`ReminderRules.resolveReminderAt` ↔ `EditorDialogs`): antes `reminderAt = due - 30min` siempre destruía offsets explícitos ("2h antes") al cambiar prioridad/proyecto/etiquetas, y para recurrentes corrompía el recordatorio de TODAS las ocurrencias futuras (c.62, 475 tests; 481 tras rebase con run paralelo c.61); **Guardián detecta vencidas importantes: la tarea más atrasada con ≥2 días (olvidada) sube a `Tone.FOCUSED` y surface cuánto tiempo lleva ("3 días"/"2 semanas") pidiendo la decisión real (hacer hoy/reprogramar/quitar) en vez de "empieza por esta"** (`GuardianCoach.forgottenAgeLabel` ↔ `FORGOTTEN_DAYS_THRESHOLD`): antes toda vencida recibía `GENTLE` + mensaje genérico idéntico sin distinguir 10 min de 10 días, sin ayudar a recuperar compromisos olvidados (c.63, 485 tests); **veredicto honesto del día en el resumen de Today: `SummaryEngine` calcula `DayLoad` (LIGHT/ON_TRACK/FULL/OVERLOADED) comparando los minutos restantes con el tiempo libre hasta el fin de jornada (9–18) y lo surface como UNA línea accionable en la tarjeta existente ("El día va a tiempo. Sigue con la siguiente tarea." / "El día está lleno pero cabe. Empieza ya la próxima tarea." / "No cabe todo hoy. Elige qué dejar para mañana.") en vez de obligar al usuario a hacer la aritmética mental (c.65, 500 tests); **sugerencia concreta de tarea a posponer cuando el día está saturado: cuando `DayLoad == OVERLOADED`, `SummaryEngine` propone UNA tarea real (la de menor prioridad y, a igual prioridad, la que vence más tarde = más margen), nunca una vencida, y la surface en la misma línea ("No cabe todo hoy. Una opción es dejar para mañana «…».") en vez del vago "elige qué dejar" (c.67, 516 tests)**; **sugerencia accionable con UN toque: la línea OVERLOADED es ahora tappable —"Toca para mover «…» a mañana"— y `TaskRules.deferToNextDay` (regla pura) traslada la tarea a mañana a la misma hora local (vía `ZonedDateTime`, correcto frente a DST) preservando el offset del recordatorio (crítico para recurrentes) y la distancia inicio→vencimiento; `OrdiaViewModel.deferTaskToTomorrow` reusa `saveTask` (reagenda el recordatorio). Sin nueva pantalla/botón: la sugerencia pasa de pasiva a accionable en la misma línea (c.69, 522 tests)**; "el N del mes que viene" → día N del mes siguiente (c.68, 516 tests); **"el 0/99 de <mes>" (día fuera de rango) ya no crashea: `parseMonthNameDate` valida `day in 1..31` antes de crear `LocalDate` (P0 crash → dueAt=null sin caer); "el 15 de agosto del 2027" (español usa "del" antes del año) ahora captura el año: `monthNamePattern` acepta `del?` antes del año → agenda 2027 (no 2026) y no deja "del 2027" huérfano en el título (c.70, 528 tests) **"jueves que viene"/"próximo día" dicho en el propio día objetivo ("jueves que viene" dicho un jueves) → +7 en vez de HOY: `weekdayPattern` capturaba el sufijo "que viene"/"próximo" como grupo no capturador, así el código nunca distinguía "el próximo jueves" de "el jueves" suelto; `weekdaySameDayCandidate` permitía hoy y `nextWeekdayOrSame` devolvía hoy → tarea agendada el día equivocado (P1 integridad de agenda). Ahora `nextExplicit = match.value` contiene "que viene"/"próxim" → fuerza `nextWeekday` estricto (+7) y desactiva `weekdaySameDayCandidate`; el día suelto "el jueves a las 18" dicho en jueves sigue pudiendo ser hoy si la hora no pasó (no-regresión). +7 tests; 541 domain tests PASS (c.72, 534→541)**; **"la semana que viene el lunes/viernes"/"el lunes de la semana que viene" → día objetivo de la semana próxima (no +7d genérico): nuevos `nextWeekWeekdayReversePattern`/`nextWeekWeekdayForwardPattern` + helper `nextWeekWeekdayDate` ancla al próximo lunes estricto + offset del weekday; procesados antes que `nextPeriodPattern` (c.73, 549 tests)**; **límites mensuales con modificador "mes que viene"/"próximo" anclan al mes SIGUIENTE (no al actual): `endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` ahora capturan el calificador y `monthBaseForBoundary` lo resuelve (anti-doble-desplazamiento para "principios") + **"antepasado mañana" → +3 días y título limpio** (antes "mañana" casaba como fecha suelta → +1 y "antepasado" quedaba en el título) (c.75, 563 tests)**; **rango horario con meridiem solo en el extremo final "de 6 a 8 de la tarde"/"de 2 a 4 de la noche": el inicio bare hereda el PM del extremo final → 18:00/14:00 (era 06:00/02:00, recordatorio 12h antes de la cita real); `rangeMatch`/`rangeStartTime` propagan `endPm` al inicio sin meridiem + reorden del `when`; no se propaga en sentido inverso (evita falso positivo "de 2pm a 4 entradas") (c.76, 568 tests)** |

## Último trabajo — Ciclo 84: Inteligencia — asistente "tareas rápidas" alineado al ranking de What Now

Feat P2 (continuación natural del c.83) de **coherencia entre superficies** (`AssistantEngine` ↔
`WhatNowEngine`). El asistente respondía "tareas de 15 minutos"/"rápido" con
`active.filter { it.durationMinutes <= 15 }.take(6)` — es decir, **en orden de lista**, sin
aplicar el ranking de What Now. Resultado: dos tareas rápidas (una vencida y una normal) podían
listar primero la normal, divergiendo de lo que muestra What Now / widget / "plan mínimo". El
usuario pedía "¿qué hago rápido?" y recibía un orden menos útil (no priorizaba lo vencido).

**Causa raíz**: el path "quick" no reutilizaba `WhatNowEngine.ordered` (introducido en c.83 como
fuente única de ranking). Era la última superficie del asistente aún sin alinear.

**Solución (mínima, una línea)**: `AssistantEngine` "tareas de 15 minutos" →
`WhatNowEngine.ordered(active, now).filter { it.durationMinutes <= 15 }.take(6)`. Conserva el
filtro `<= 15` exacto (default `durationMinutes=25` → solo las realmente cortas; `0` por defecto
no existe salvo asignación explícita). Sin nueva pantalla/botón.

**Tests**: +1 en `AssistantEngineTest.kt` (`quickTasks_rankOverdueFirst`: normal `durationMinutes=10`
+ vencida `durationMinutes=10 dueAt=1` → `relatedTaskIds=[2,1]`, la atrasada primero).
**602 domain tests PASS** (`tools/run_domain_tests.sh`); **6 AssistantEngineTest PASS** (kotlinc
aparte, fuera del script de dominio); **smoke 25 OK** (`tools/run_domain_checks.sh`).
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
**Archivos modificados**: `app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt`,
`app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt`,
`AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
**Estado**: FIXED → VERIFIED (dominio JVM + assistant JVM).

## Último trabajo — Ciclo 83: Inteligencia — asistente "¿Qué hago ahora?" explica la razón + plan mínimo con ranking único de What Now

Feat P1 de **inteligencia honesta y coherencia** (`AssistantEngine` ↔ `WhatNowEngine`). El
asistente respondía "¿Qué hago ahora?" con `TaskRules.nextBestTask` (time-aware desde c.45) pero
**(1) no explicaba por qué** esa tarea y no otra, y **(2) el "plan mínimo" usaba un comparador
propio** (`priority` desc → `dueAt` asc) que **divergía** del ranking de `WhatNowEngine` (rango
temporal → prioridad → dueAt). Así las tres superficies que deberían dar la misma respuesta —What
Now (TodayScreen), asistente y widget— mostraban órdenes distintos: con una vencida normal y una
urgente no vencida, What Now = vencida (por rango), plan mínimo viejo = urgente (por prioridad).

**Causa raíz**: ausencia de un punto único de ranking reutilizable. `WhatNowEngine.suggest`
tenía el comparador inline (duplicado conceptualmente con `TaskRules.nextBestTask`), y el
asistente reimplementaba otro orden para el plan mínimo en lugar de delegar.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- `WhatNowEngine.ordered(tasks, now, zone)`: ranking determinista y **público** de todas las
  candidatas (mismo comparador que tenía `suggest` inline). `suggest` ahora delega en
  `ordered(...).firstOrNull()` — fuente única de verdad, DRY (se elimina la duplicación).
- `WhatNowEngine.reasonLabel(WhatNowReason)`: etiqueta humana y honesta por qué esa tarea va
  primero ("ya está en curso"/"está vencida"/"empieza enseguida"/"vence hoy"/"es urgente"/"es
  prioritaria"/"está programada para más tarde"/"es lo siguiente de la bandeja"). No es IA, es
  la razón real del ranking.
- `AssistantEngine` "¿qué hago ahora?"/"siguiente acción" → delega en `WhatNowEngine.suggest` y
  responde "Empieza por «…»: <razón>. Estimo N minutos." +, si hay vencidas, "Además, tienes N
  vencid(a/as)." Antes solo decía "Empieza por «…». Estimo N minutos." sin contexto.
- `AssistantEngine` "plan mínimo" → `WhatNowEngine.ordered(...).take(3)` (mismo orden que What
  Now/widget). Reemplaza el comparador divergente `priority`→`dueAt`.

No se simula IA ni se usa random: el razonamiento es la explicación honesta del ranking local.
Retrocompatible (sin cambios de firma pública; `suggest` sigue devolviendo `WhatNowSuggestion?`).

**Tests**: +2 en `AssistantEngineTest.kt` (`whatNow_explainsWhyAndMentionsOverdue`: vencida →
relatedTaskIds=[1], respuesta contiene "vencida" y "1 vencida"; `planMinimo_ranksOverdueFirst`:
vencida + normal-alta → orden [2,1]). La existente `whatNow_usesRealPriority` sigue verde
(urgente vs normal → urgente). **602 domain tests PASS** (`tools/run_domain_tests.sh`, 27
clases); **5 AssistantEngineTest PASS** (compiladas/ejecutadas con kotlinc aparte, fuera del
script de dominio); **smoke 25 OK** (`tools/run_domain_checks.sh`).
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); render
real del asistente en la app no probado en dispositivo.
**Hallazgos adicionales**: `WhatNowEngine.reasonLabel` queda disponible para reusar desde la UI
de What Now (`TodayScreen`) si se quiere mostrar la razón junto a la tarjeta (futuro, evaluar
antes de añadir superficie).
**Archivos modificados**: `app/src/main/java/com/ordia/app/domain/WhatNowEngine.kt`,
`app/src/main/java/com/ordia/app/assistant/AssistantEngine.kt`,
`app/src/test/java/com/ordia/app/assistant/AssistantEngineTest.kt`,
`AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
**Estado**: FIXED → VERIFIED (dominio JVM + assistant JVM).

## Último trabajo — Ciclo 82: Parser — meridiem solo en el INICIO del rango ("de 6pm a 8") + unificación de la familia de rangos horarios

Fix P1 de integridad de captura/agenda (`NaturalTaskParser` ↔ `rangeMatch`). El ciclo 80 cerró
el **cruce de medianoche** con meridiem al inicio ("de 10 de la noche a 1 de la madrugada"), pero
dejaba rota la dirección simétrica que el c.80 NO tocó: el **meridiem (PM) solo en el INICIO** con
fin bare ("de 6pm a 8", "de 2pm a 4", "de 6 de la tarde a 8"). Un probe JVM (13 casos) sobre la
base `970d919` (c.80) confirmó el bug: el fin bare (8) no resolvía a 20:00 porque **no heredaba
el PM del inicio** → `endHr=8 < startHr=18` violaba la guarda `endMin > startMin` → el bloque se
rechazaba, `durationMinutes=null` **Y** el título quedaba sucio ("Reunión de a 8").

**Causa raíz**: la propagación de meridiem era **asimétrica**. El c.76 propagaba el PM del extremo
**final** al inicio bare (fin→inicio), pero **nunca** el del **inicio** al fin bare (inicio→fin). Así
"de 6 a 8 de la tarde" (PM al final) funcionaba (18:00→20:00), pero "de 6pm a 8" (PM al inicio)
rompía: el fin 8 quedaba como 08:00 < 18:00 → rango invalidado. Además, el c.80 introdujo una
regresión latente: su `sameDay` estricto rechazaba también los rangos PM-al-inicio donde
`endHr < startHr` aun siendo mismo día legítimo (un probe mostró dur=840 en base limpia c.80 para
"Reunión de 6pm a 8", por el camino de fallback del canónico de parte del día).

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- `endPmEffective = endPm || (endMer.isEmpty() && startPmEffective && endH > 0 && endH <= startH)`:
  el fin bare hereda el PM del inicio **solo cuando están en el mismo lado del mediodía** (fin ≤
  inicio, p.ej. 8 ≤ 18). Cuando `endHr < startHr` ya resuelto a PM, NO se fuerza PM al fin (sería
  un falso overnight) — ahí entra el cruce de medianoche inverso.
- `startPmEffective = startPm || (startMer.isEmpty() && endPm && startH <= endH)`: preserva el
  c.76 (fin→inicio) sin cambios, ahora en un único modelo simétrico.
- `midnightWrap = (startPmEffective && !endPmEffective && endH < startH && !followedByCount)`: el
  cruce de medianoche inverso ("de 11pm a 1") — inicio PM, fin bare con `endHr < startHr` y **no**
  seguido de un sustantivo de cantidad — envuelve el fin a +24h → 23:00→01:00, dur 120. Esto
  reemplaza el wrap ad hoc del c.80 con una condición unificada que cubre mismo-día, medianoche y
  el simétrico cruce de mediodía en un solo bloque `rangeMatch`.
- **Anti-falso-positivo**: `followedByCount` detecta si el fin bare va seguido de un sustantivo de
  cantidad ("entradas", "horas", "cajas") → NO se propaga PM ni se envuelve ("de 2pm a 4 entradas"
  es una compra, no un rango horario) → dur=null como antes.
- Lógica local honesta (aritmética de minutos + ajuste de 24h, sin random ni modelo simulado).
  Retrocompatible (sin cambios de firma pública).

**Tests**: +12 en `NaturalTaskParserTest.kt` (PM-al-inicio + cruce inverso + anti-falso-positivo):
`rangeWithLeadingCompactPmPropagatesToEnd`=18:00/120, `…AndTrailingText…`=18:00/120,
`…AndMinutes…`=14:30/120, `rangeWithLeadingDeLaTardePropagatesToEnd`=18:00/120,
`rangeWithLeadingPmAndCountNounIsRejected`=null, `rangeWithLeadingAmPropagatesToEnd`=08:00/240,
`rangeWithLeadingPmCrossingMidnightWraps`=23:00/120, `…WithDeLaMadrugadaWraps…`=23:00/120,
`rangeWithBothPmDescendingNotWrapped`=null, + los de `de`-conector preservados. **593 domain tests
PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 588 c.80 + 12 netas -4 absorbidas por
unificación), smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM amplio (13 casos overnight +
mismo-día) confirmó sin regresión: c.80 intacto ("de 10 de la noche a 1 de la madrugada"=22:00/180,
"de 9 de la noche a 2 de la madrugada"=21:00/300), c.79 intacto ("de 12 a 2 de la tarde"=12:00/120,
"de 11 a 1 de la tarde"=11:00/120), c.76 intacto ("de 6 a 8 de la tarde"=18:00/120), c.78 intacto
("de 6 a 8 pm"=18:00/120), standalone "Reunión 8pm"=20:00 sin duración. **Beneficio adicional**:
"de 10:30 de la noche a 1:15 de la madrugada" (overnight con minutos, "próxima prioridad" del
c.80) ahora resuelve 22:30/165 sin código extra.

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser
en la app (sin Android SDK).

## Último trabajo — Ciclo 81: Búsqueda — búsqueda semántica por fecha ("hoy", "mañana", "esta semana", "atrasadas/vencidas")

Mejora P2 de búsqueda/recuperación (`SearchEngine.kt`). La búsqueda universal solo comparaba texto:
escribir "hoy", "mañana" o "esta semana" devolvía **vacío** porque la palabra no estaba en el
título/detalle de la tarea, aunque su `dueAt` cumpliera el rango. Solo "vencidas" funcionaba
parcialmente (vía un atajo `vencid` que activaba `TaskRules.isOverdue`, pero no como filtro de
fecha coherente). Un usuario que busca "hoy" para ver qué vence hoy **no veía nada**.

**Solución (sin nueva pantalla/botón — "menos interfaz, más potencia")**: `SearchEngine` detecta un
`DateScope` (TODAY/TOMORROW/THIS_WEEK/OVERDUE) a partir de los tokens del query (`hoy`, `manana`,
`semana`, `atrasada/os`/`vencida/os`). Con scope, las palabras de fecha (y modificadores
"esta"/"el"/…) **no se exigen en el contenido**: el filtro principal pasa a ser por `dueAt`
(`taskMatchesDateScope`), y el texto opcional filtra además dentro del rango ("hoy reunion" → solo
las de hoy que contengan "reunión"). Los scopes futuros/hoy excluyen tareas completadas/canceladas
(consistentes con `isOverdue`/`isDueToday`), para que la búsqueda sea accionable. Sin scope →
comportamiento idéntico al anterior (no-regresión). Heurística local honesta (aritmética de
`LocalDate`/`ZoneId.systemDefault()`, sin random ni modelo simulado). El `urgencyRank` existente
sigue ordenando (atrasada-urgente primero). +9 tests (`SearchEngineDateScopeTest.kt`);
**597 domain tests PASS** (588→597), smoke 25 OK. NO VERIFICADO gradle/Android/UI/Room.

## Último trabajo — Ciclo 80: Parser — cruce de medianoche en rango nocturno ("de 10 de la noche a 1 de la madrugada")

Fix P1 de integridad de captura (`NaturalTaskParser` ↔ `rangeMatch`). El ciclo 79 cerró la
familia de **cruces del mediodía**, pero dejaba la nota de que faltaba el simétrico **cruce de
medianoche**. Un probe JVM (3 casos overnight) confirmó el bug: "Cena de 10 de la noche a 1 de
la madrugada" resolvía `dueAt=22:00` (por suerte, vía el canónico de parte del día) pero
`durationMinutes=null` — **la longitud real del evento (3h) se perdía**.

**Causa raíz**: `rangeMatch` exigía `endMin > startMin` (estrictamente mismo día) como condición
de validez. Un rango overnight (22:00→01:00, `endMin=60 < startMin=1320`) violaba esa guarda →
el bloque se rechazaba por completo y la duración no se asignaba. El `dueAt` sobrevivía solo
porque caía al canónico de la parte del día ("de la noche"=21:00/22:00), pero la longitud del
evento se perdía en silencio (un turno de 7h quedaba sin duración).

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
- `sameDay = endMin > startMin`; `rawDuration = if (sameDay) end−start else end+24*60−start`.
  La validez pasa a requerir `rawDuration in 5..(24*60)` (rango plausible), no `endMin>startMin`.
- El cruce de medianoche **solo se acepta con señal clara** (meridiem/unidad/PM,
  `clearSignal`). Un rango ambiguo sin meridiem ("de 10 a 1") **no** se reinterpreta como
  overnight de 15h (demasiado arriesgado) — se rechaza como antes.
- `acceptAmbiguous` se restringe a `sameDay` (el heurístico de horas en punto ambas <13 solo
  aplica a mismo día; overnight siempre necesita meridiem explícito).

**Tests**: +4 en `NaturalTaskParserTest.kt` (`overnightRangeDe10DeLaNocheA1DeLaMadrugada`=22:00/180,
`…De11DeLaNocheA6DeLaManana`=23:00/420, `…De9DeLaNocheA2DeLaMadrugada`=21:00/300,
`…AmbiguousNotReinterpretedAsOvernight`="de 10 a 1" sin meridiem→null). **588 domain tests PASS**
(`bash tools/run_domain_tests.sh`, 26 clases — 584 c.79 + 4), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión: mismo-día c.76/c.78/c.79 intacto
("de 6 a 8 de la tarde"=18:00/120, "de 12 a 2 de la tarde"=12:00/120, "de 11 a 1 de la tarde"=11:00/120).

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser
en la app (sin Android SDK).

## Último trabajo — Ciclo 79: Parser — cruce del mediodía en rangos con meridiem solo al final ("de 12 a 2 de la tarde")

Fix P1 de integridad de agenda (`NaturalTaskParser` ↔ `rangeMatch`/`rangeStartTime`). La nota
"Siguiente" del c.78 dejaba documentado el bug: "de 12 a 2 de la tarde" en duración por horas
absolutas. Un probe JVM (12 casos) reveló **DOS defectos entrelazados** en la familia de rangos
horarios con meridiem solo en el extremo final (c.76/c.78):

1. **Duración por horas crudas**: "Almuerzo de 12 a 2 de la tarde" computaba `end−start` con las
   horas **crudas** del texto (2−12=−600) y `coerceIn(5, 24*60)` dejaba **5 min** en vez de 120.
   Un almuerzo de 2h se agendaba como un evento de 5 min.
2. **Propagación PM ciega al inicio bare**: el fix del c.76 propagaba `endPm` al inicio sin
   meridiem **incondicionalmente**. En un cruce real "Clase de 11 a 1 de la tarde" el inicio 11
   se convertía en 23 (11+12) → `dueAt=23:00` y duración absurda (5 min tras clamp), cuando lo
   correcto es inicio AM 11:00 y fin PM 13:00 (2h).

**Causa raíz**: (1) `rangeDurationMinutes` usaba las horas crudas del regex (`groupValues`) en
vez de las horas absolutas ya resueltas (`sAbs`/`eAbs`); la coincidencia "6 a 8 de la tarde"
(cruda 8−6=120) hacía pasar el bug inadvertido hasta un cruce del mediodía. (2) La propagación
de PM no distinguía "mismo lado del mediodía" (6→8) de "cruce" (11→1): en el primero el inicio
debe heredar PM, en el segundo el inicio es AM y solo el fin es PM.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
- La duración se calcula con las horas **absolutas resueltas** (`startMin`/`endMin` derivadas de
  `sAbs`/`eAbs`), no con horas crudas.
- La propagación PM al inicio bare es ahora condicional:
  `startPmEffective = startPm || (startMer.isEmpty() && endPm && startHr <= endHr)`.
  Solo se propaga cuando `startHr <= endHr` (mismo lado del mediodía); en un cruce (`start>end`)
  el inicio queda AM y el fin PM. Aplicado **simétricamente** a `rangeMatch` (duración/validez)
  y `rangeStartTime` (dueAt).
- Reorden del `when` en `rangeMatch` (`resolve`): `pm && h<12 -> h+12` antes que `mer.isEmpty() -> h`
  (c.76 ya lo tenía en `rangeStartTime` pero no en `rangeMatch`; era código muerto).

**Tests**: +6 en `NaturalTaskParserTest.kt` (`noonCrossingRangeDe12A2DeLaTarde`=12:00/120,
`…De12A2pm`=12:00/120, `…De11A1DeLaTarde`=11:00/120, `…De12A1pm`=12:00/60,
`…De1A2DeLaTarde`=13:00/60, `…AmbiguousRejected`="de 12 a 2" sin meridiem→null). **584 domain
tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 578 c.78 + 6), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión: mismo-meridiano c.76/c.78 intacto
("de 6 a 8 de la tarde"=18:00/120, "de 6 a 8 pm"=18:00/120, "de 9 a 11 de la mañana"=09:00/120).

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser
en la app (sin Android SDK).

## Último trabajo — Ciclo 76: Parser — propagación de meridiem PM del extremo final al inicio bare del rango horario

Fix P1 de integridad de agenda (`NaturalTaskParser`). En un rango horario donde **solo el extremo
final lleva meridiem** ("reunión **de 6 a 8 de la tarde**", "de 2 a 4 de la noche"), el inicio (sin
meridiem) **no heredaba** el contexto de tarde/noche y se agendaba como **06:00 / 02:00** en vez de
**18:00 / 14:00**. La duración ya era correcta (120, diff de horas en punto), pero la fecha límite
apuntaba a la mañana → el recordatorio se disparaba **12 horas antes** de la cita real. Forma
cotidiana de expresar una ventana vespertina/nocturna; el usuario dice "de la tarde" una sola vez
al final y espera que aplique a todo el bloque.

**Causa raíz**: en `rangeMatch`/`rangeStartTime` (c.60/c.61), cada extremo se resolvía con su
**propio** meridiem. El inicio sin meridiem caía a `mer.isEmpty() -> h` (hora bare, AM implícito),
ignorando el PM del extremo final. Asimetría frente a la hora suelta "a las 6 de la tarde" → 18:00.

**Solución (mínima, `NaturalTaskParser.kt`)**: propagación de PM del extremo final al inicio cuando
el inicio no tiene meridiem. En `rangeMatch`: `val startPmEffective = startPm || (startMer.isEmpty()
&& endPm)` y se usa en `resolve(startH, startMer, startPmEffective)` (validación/duración coherentes
con la hora resuelta). En `rangeStartTime` (dueAt): mismo cálculo + reorden del `when` para que la
rama `pm && h < 12 -> h + 12` se evalúe antes que `mer.isEmpty() -> h` (antes la rama bare ganaba y
anulaba la propagación). "de la mañana/madrugada" (AM) → no-op (el inicio 9 sigue 09:00). **No se
propaga en sentido inverso** (inicio PM → fin bare) para no aceptar falsos positivos tipo "de 2pm a
4 entradas". Sin nueva pantalla/botón: el rango vespertino se agenda a la hora correcta.

**Tests**: +5 en `NaturalTaskParserTest.kt` (`rangeWithTrailingDeLaTardePropagatesPmToStart`
"de 6 a 8 de la tarde"→18:00/120; `rangeWithTrailingDeLaNochePropagatesPmToStart` "de 3 a 5 de la
noche"→15:00/120; `rangeWithTrailingDeLaMananaKeepsAmStart` "de 9 a 11 de la mañana"→09:00/120
no-op AM; `rangeWithTrailingDeLaTardeAndStartMinutesPropagatesPm` "de 6:30 a 8 de la tarde"→18:30/90;
`rangeWithTrailingDeLaMadrugadaKeepsAmStart` "de 1 a 3 de la madrugada"→01:00/120). **568 domain
tests PASS** (26 clases — 563 c.75 + 5), smoke 25 OK. **NO VERIFICADO**: gradle/lint/assemble/
Android/UI/Room con DAOs reales; render real del parser en la app (sin SDK).

---

## Último trabajo — Ciclo 78: Parser — rango con meridiem compacto (am/pm) solo en el extremo final: dueAt resuelve INICIO, no FIN

Fix P1 de integridad de agenda (`NaturalTaskParser`), continuación directa del hallazgo que el c.76
dejó documentado como "fuera de alcance". En un rango horario donde **solo el extremo final lleva
un meridiem compacto** ("reunión **de 6 a 8 pm**", "taller **de 9 a 11 am**", "clase de 6 a 8 p.m.",
"evento de 3 a 5 p m"), `timePatterns` (que se ejecuta ANTES que `timeRangePattern`) capturaba el
extremo final ("8 pm"→20:00) como `explicitTime`. Luego, en `parsedTime`, `explicitTime` **tenía
prioridad** sobre `rangeStartTime` (18:00, la hora de inicio), de modo que `dueAt` caía a la hora
de **FIN** (20:00), no de inicio (18:00). El recordatorio se disparaba **2 horas tarde** (a las
20:00 de una cita que empezaba a las 18:00). La forma "de la tarde" (c.76) no tenía este fallo
porque `timePatterns` no captura "8 de la tarde". Era un bug P1 previo, solo documentado.

**Causa raíz**: orden de procesamiento + prioridad sin contexto. `timeMatch`/`explicitTime` se
calcula sobre `working` antes de validar el rango, y un token que es de hecho el extremo final de
un rango ("8 pm") se trataba como hora suelta. Al dar prioridad absoluta a `explicitTime` en
`parsedTime`, el rango (y su hora de inicio correcta) quedaba sombreado.

**Solución (mínima, `NaturalTaskParser.kt`)**: detección posicional. Tras validar `rangeMatch` y
calcular `timeMatch`, se introduce `explicitTimeIsRangeEnd = rangeMatch != null && timeMatch != null
&& timeMatch.range dentro del span de rangeMatch.range`. Cuando es true, el tiempo explícito NO es
suelto sino el extremo final del rango, así que `parsedTime` ignora `explicitTime` y deja ganar a
`rangeStartTime` (hora de inicio, ya con propagación de PM del c.76 → 18:00). Si el tiempo explícito
cae FUERA del span del rango (hora suelta genuina antes/después, p. ej. "a las 3 reunión de 6 a 8
pm"), el guard no actúa y `explicitTime` sigue ganando: sin regresión para horas sueltas ("Llamada
8pm"→20:00, "Cita 9am"→09:00) ni para "de 2pm a 4pm"/"de 9am a 11am" (ambos extremos con meridiem:
el timeMatch captura el PRIMER extremo = inicio, que cae dentro del span → rangeStartTime gana con
el mismo valor → idéntico). Sin nueva pantalla/botón.

**Tests**: +6 en `NaturalTaskParserTest.kt`
(`rangeWithTrailingCompactPmResolvesStartNotEnd` "de 6 a 8 pm"→18:00/120;
`rangeWithTrailingCompactAmResolvesStartNotEnd` "de 9 a 11 am"→09:00/120;
`rangeWithPmDotsResolvesStartNotEnd` "de 6 a 8 p.m."→18:00/120;
`rangeWithTrailingPmSpacedResolvesStartNotEnd` "de 3 a 5 p m"→15:00/120;
`rangeWithTrailingCompactPmAndStartMinutesResolvesStart` "de 2:30 a 4:30 pm"→14:30/120;
`standaloneHourWithMeridiemNotAffectedByRangeGuard` "Llamada 8pm"→20:00 + "Cita 9am"→09:00 sin
rango). **578 domain tests PASS** (26 clases — 568 c.76 + 4 `SummaryEngineTest` del c.77 paralelo
+ 6 c.78), smoke 25 OK. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin SDK).

---

## Último trabajo — Ciclo 75: Parser — límites mensuales con "mes que viene"/"próximo" + "antepasado mañana"

Fix P1 de integridad de dato/agenda (`NaturalTaskParser`), continuación de la auditoría del parser
del c.73. Dos fallos encontrados por probe JVM sobre el dominio:

**Fix A — límites mensuales ignoraban el modificador "mes que viene"/"próximo"**. **"reunión fin
del mes que viene"** agendaba fin del mes **ACTUAL** (31/08) en vez de fin del mes **SIGUIENTE**
(30/09). Igual con "mediados/principios del mes que viene". Causa raíz:
`endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` terminaban en `mes` y **no
capturaban** el calificador; la fecha se resolvía siempre sobre el mes base de hoy. Un
vencimiento mensual explícitamente futuro caía un mes ANTES → compromiso adelantado/olvidado.

**Fix B — "antepasado mañana" mal fechado + título corrupto**. **"reunión antepasado mañana"**
agendaba **mañana** (+1) en vez de +3 y dejaba **"antepasado"** en el título. Causa raíz: la
palabra "mañana" casaba con `mananaAsDate` → +1, y "antepasado" no estaba en la regex de limpieza.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: los tres patrones de
límite mensual ahora capturan `(?:\s+(?:que\s+viene|pr[oó]xim[oa]s?))?`; el helper
`monthBaseForBoundary(today, matched)` ancla al mes siguiente **sin roll adicional** cuando hay
modificador (evita el doble-desplazamiento simétrico: "principios del mes que viene" a medidados
de agosto = 01/09, no 01/10), y replica el roll original sin modificador. Para "antepasado
mañana": rama `plusDays(3)` antes de "pasado mañana"/"mañana" + inclusión en la regex de limpieza
del título. Lógica local honesta, retrocompatible.

**Tests**: +10 (8 Fix A + 2 Fix B). **563 domain tests PASS** (26 clases — 549 c.73 + 4 c.74 + 10 c.75), smoke
25 OK. Sin regresión (549 previos intactos; sin modificador "fin de mes"/"a principios de mes"
siguen con su roll original; "pasado mañana"/"mañana" sueltos intactos). **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin SDK).

---
## Último trabajo — Ciclo 74: RecurrenceEngine — recurrencia anual anclada a 29 de febrero (no deriva a 28/2)

Fix P1 de integridad de datos (`RecurrenceEngine`). Una recurrencia **YEARLY** anclada al **29 de
febrero** (cumpleaños/aniversario bisiesto, "aniversario el 29 de febrero de cada año") **deriva su
ancla para siempre** tras el primer ciclo: `advance` usaba `base.plusYears(interval)`, y
`plusYears(1)` sobre 29/2/2024 clampaba a **28/2/2025** (no bisiesto); a partir de ahí todas las
ocurrencias futuras caían en 28/2, perdiendo para siempre el día real del compromiso. El parser
(c.38/c.60) SÍ recuperaba el 29/2 como próxima fecha bisiesta real al capturar la tarea, pero el
motor la destruía al completarla. Simétrico exacto al bug mensual de 31 días corregido en c.18
(`nextMonthly` salta meses sin el día).

**Causa raíz**: `RecurrenceFrequency.YEARLY -> base.plusYears(interval)` no trata el único caso en
que un día del calendario deja de existir entre años (29/2). `java.time` clampá silenciosamente al
28/2 en vez de saltar al siguiente año válido.

**Solución (mínima, `RecurrenceEngine.kt`)**: nueva `nextYearly(base, interval)` análoga a
`nextMonthly`. Para fechas comunes (cualquiera que no sea 29/2) usa `plusYears` directo (sin cambio
de comportamiento); para 29/2 avanza `interval` años y, si el año destino no es bisiesto, sigue
avanzando años hasta hallar uno bisiesto (límite 8 iteraciones — siempre hay uno). Conserva hora,
zona, offset de recordatorio y `interval` como paso mínimo. No nueva pantalla/botón: protege datos
recurrentes sin fricción.

**Tests**: +4 en `RecurrenceEngineTest.kt` (`yearly_leapDayAnchorSkipsNonLeapYears` 2024→2028 +
offset recordatorio; `yearly_leapDayAnchorDoesNotDriftAcrossCycles` 2028→2032 confirma no-regresión
de la deriva; `yearly_nonLeapDayAnchorUsesPlainPlusYears` 15/8→15/8 caso normal sin alterar;
`yearly_leapDayAnchorRespectsInterval` "cada 4 años" 2024→2028 valida interval>1). **553 domain
tests PASS** (26 clases — 549 c.73 + 4), smoke 25 OK. **NO VERIFICADO**: gradle/lint/assemble/
Android/UI/Room con DAOs reales; integración real del motor con la app (sin SDK).

---

## Último trabajo — Ciclo 75: Parser — límites mensuales con "mes que viene"/"próximo" + "antepasado mañana"

Fix P1 de integridad de dato/agenda (`NaturalTaskParser`), continuación de la auditoría del parser
del c.73. Dos fallos encontrados por probe JVM sobre el dominio:

**Fix A — límites mensuales ignoraban el modificador "mes que viene"/"próximo"**. **"reunión fin
del mes que viene"** agendaba fin del mes **ACTUAL** (31/08) en vez de fin del mes **SIGUIENTE**
(30/09). Igual con "mediados/principios del mes que viene". Causa raíz:
`endOfMonthPattern`/`midOfMonthPattern`/`startOfMonthPattern` terminaban en `mes` y **no
capturaban** el calificador; la fecha se resolvía siempre sobre el mes base de hoy. Un
vencimiento mensual explícitamente futuro caía un mes ANTES → compromiso adelantado/olvidado.

**Fix B — "antepasado mañana" mal fechado + título corrupto**. **"reunión antepasado mañana"**
agendaba **mañana** (+1) en vez de +3 y dejaba **"antepasado"** en el título. Causa raíz: la
palabra "mañana" casaba con `mananaAsDate` → +1, y "antepasado" no estaba en la regex de limpieza.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: los tres patrones de
límite mensual ahora capturan `(?:\s+(?:que\s+viene|pr[oó]xim[oa]s?))?`; el helper
`monthBaseForBoundary(today, matched)` ancla al mes siguiente **sin roll adicional** cuando hay
modificador (evita el doble-desplazamiento simétrico: "principios del mes que viene" a medidados
de agosto = 01/09, no 01/10), y replica el roll original sin modificador. Para "antepasado
mañana": rama `plusDays(3)` antes de "pasado mañana"/"mañana" + inclusión en la regex de limpieza
del título. Lógica local honesta, retrocompatible.

**Tests**: +10 (8 Fix A + 2 Fix B). **563 domain tests PASS** (26 clases — 549 c.73 + 4 c.74 + 10 c.75), smoke
25 OK. Sin regresión (549 previos intactos; sin modificador "fin de mes"/"a principios de mes"
siguen con su roll original; "pasado mañana"/"mañana" sueltos intactos). **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en la app (sin SDK).

---


---

## Último trabajo — Ciclo 73: Parser — "la semana que viene el lunes/viernes" → día objetivo de la semana próxima

(ver RUN_LOG c.73)

## Ciclo 71 (anterior) — Parser — orden inverso "el mes que viene el N" -> día N del mes siguiente "el mes que viene el N" → día N del mes siguiente

Fix P1 de integridad de agenda/dato (`NaturalTaskParser`), simétrico al c.68. La forma cotidiana
**"Pagar tarjeta el mes que viene el 5"** (periodo ANTES del día, opuesto al "el 5 del mes que viene"
del c.68) se agendaba al día **equivocado**: `nextPeriodPattern` robaba "el mes que viene" como
+30d genérico e ignoraba el día explícito (→ 12/09 en vez del 05/09). Para un vencimiento mensual
anclado a un día concreto (tarjeta, alquiler, cobro) el recordatorio se disparaba **una semana tarde**
—compromiso olvidado. P1: cita/factura agendada en día erróneo.

**Causa raíz**: `nextMonthDayPattern` (c.68) solo casaba `el N del mes que viene` (día + "del" +
periodo). El orden inverso (periodo + "el" + día) no casaba, así que `nextPeriodPattern` procesaba
primero "el mes que viene" y `nextPeriodDueAt` (+30d) ganaba en la cadena `effectiveRelativeDueAt`
sobre `dayOfMonthDate`, sombreando el día explícito. En la variante "el día N", "el día 5" quedaba
como residuo en el título.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- Nuevo `nextMonthDayReversePattern`: captura periodo + "el" + día (con o sin "día"), en orden
  inverso. Reutiliza los mismos cualificadores de periodo que `nextMonthDayPattern`/`nextPeriodPattern`.
- Resolución **idéntica** a `nextMonthDayDueAt` (c.68): día N de `plusMonths(1)`, clamp al último día
  válido del mes destino (p. ej. "el 31" → 30 de septiembre). Sin nueva lógica de fechas.
- Se procesa **ANTES** de `nextPeriodPattern` (junto a `nextMonthDayMatch`) para consumir la frase
  completa (periodo+día) en un solo match y evitar que `nextPeriodPattern` la robe como +30d.
- Integrado en `effectiveRelativeDueAt` (entre `nextMonthDayDueAt` y `nextPeriodDueAt`) y en
  `relativeIsDays` (combinable con hora explícita: "el mes que viene el 5 a las 10" → 05/09 10:00).
- No-regresión: el patrón **exige** un día tras el periodo, así "el mes que viene" sin día sigue
  siendo +30d.

**Tests**: +6 en `NaturalTaskParserTest.kt` ("el mes que viene el 15" → 15 del mes siguiente;
"el mes que viene el día 5" → 5; "el próximo mes el 10" → 10; "el mes próximo el 20" → 20; con hora
→ 05/09 09:00; "el mes que viene el 31" respeta 31 cuando el mes destino tiene 31). **528 domain
tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 522 c.69 + 6 nuevos), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/
Room con DAOs reales; render real del parser en la app (sin Android SDK). La combinación
**semana+weekday** ("la semana que viene el lunes/viernes") está FIXED (c.73): helper
`nextWeekWeekdayDate` ancla al próximo lunes estricto + offset del weekday objetivo.

## Último trabajo — Ciclo 69: Resumen accionable — un toque pospone la tarea sugerida a mañana

Mejora P2 de inteligencia/UX (`SummaryEngine` ↔ `TaskRules` ↔ `OrdiaViewModel` ↔ `TodayScreen`),
continuación directa de c.66/c.67. La sugerencia de posponer una tarea (c.66/c.67) era **pasiva**:
nombraba la tarea más posponible pero el usuario tenía que abrirla en el editor, cambiar la fecha a
mañana y guardar — 3 pasos para ejecutar la sugerencia que Ordía ya le dio. Fricción innecesaria
justo en el momento de sobrecarga.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- `DeferralSuggestion` gana `canDefer: Boolean` (`chosen.dueAt != null`): la sugerencia solo es
  accionable si la tarea tiene vencimiento real (sin vencimiento, "mañana" no está definido y
  añadirlo cambiaría la semántica de la tarea).
- `TaskRules.deferToNextDay(task, now, zone)` — nueva regla pura: traslada la tarea a **mañana a la
  misma hora local** vía `ZonedDateTime` (correcto frente a DST, no `+24h` a ciegas) y desplaza
  `startAt` y `reminderAt` por el mismo delta, conservando la distancia inicio→vencimiento y el
  offset exacto del recordatorio (crítico para recurrentes: `RecurrenceEngine` reutiliza
  `dueAt - reminderAt` en cada ocurrencia). `recurrence`/`interval`/`days` intactos: se pospone ESTA
  instancia, no la cadencia. Devuelve `null` si no hay `dueAt`. No muta la entrada.
- `OrdiaViewModel.deferTaskToTomorrow(taskId)`: carga la tarea, aplica `deferToNextDay` y reusa
  `saveTask` — que ya reagenda el recordatorio en el nuevo vencimiento (no duplica lógica).
- `TodayScreen.kt`: la línea OVERLOADED es ahora **tappable** cuando `canDefer`, con texto
  "Toca para mover «…» a mañana." (nuevo string `summary_load_overloaded_actionable`); si no es
  accionable, conserva el texto pasivo. Misma línea `bodySmall`, sin tarjeta/botón nuevos.
- Heurística determinista, sin random ni "IA". Reusa infraestructura existente (`saveTask`,
  reagenda recordatorios).

**Tests**: +6 en `TaskRulesTest.kt` (null sin dueAt; mueve a mañana misma hora; preserva offset
del recordatorio; traslada startAt por mismo delta; no muta original; deja null los campos null).
**522 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 516 c.67/c.68 + 6 nuevos),
smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del tap en la
tarjeta de Today en dispositivo; `deferTaskToTomorrow` (ViewModel, requiere Android).

## Último trabajo — Ciclo 67: Resumen — la sugerencia de posposición nunca nombra tareas en curso ni inminentes

Refinamiento P2 de fiabilidad/inteligencia honesta (`SummaryEngine` ↔ `TaskRules` ↔ `WhatNowEngine`),
continuación directa de c.66. La sugerencia de posponer una tarea (c.66) solo excluía las **vencidas**,
pero podía legítimamente nombrar **la reunión que empieza en 5 min** o **la tarea que el usuario está
ejecutando ahora** (porque su `startAt`/`dueAt` las hace "menos prioritarias con más margen") — un consejo
**dañino** que haría perder la cita.

**Solución (mínima, sin nueva pantalla/botón — "mejor decisión automáticamente")**:
- `TaskRules.isInProgressNow` pasa de **privado** a **público** (fuente única de verdad, simétrico a
  `isImminentStart` ya público) con KDoc.
- `WhatNowEngine.isInProgressNow` (copia privada duplicada) ahora **delega** en `TaskRules.isInProgressNow`
  (DRY, igual que `isImminentStart`). Comportamiento idéntico; cero duplicación.
- `SummaryEngine.mostDeferrableTask`: el filtro `deferrable` añade `!isInProgressNow && !isImminentStart`.
  Nunca sugiere posponer lo que se está haciendo ahora ni una cita a punto de empezar; entre las
  restantes, la heurística de prioridad+margen es la misma. Si todas las posponibles sin empeorar
  retraso son en-curso/inminentes → sin sugerencia (null), igual que cuando solo quedan vencidas.
- Heurística determinista, sin random ni "IA". No muta nada.

**Tests**: +3 en `SummaryEngineTest.kt` (nunca sugiere en-curso, nunca sugiere inminente, solo
posponibles en-curso/inminentes → null). **510 domain tests PASS** (`bash tools/run_domain_tests.sh`,
26 clases — 507 c.66 + 3 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea en
la tarjeta de Today en dispositivo; integración Android del widget/asistente (`nextBestTask`, delegación
`isInProgressNow` deja comportamiento idéntico pero sin compilar en Android).

## Último trabajo — Ciclo 66: Resumen — sugerencia concreta de tarea a posponer cuando el día está saturado

Mejora P2 de inteligencia/honesta (`SummaryEngine` ↔ `TodayScreen`), continuación directa de c.65.
El veredicto `OVERLOADED` decía "No cabe todo hoy. Elige qué dejar para mañana." — correcto pero
**vago**: dejaba al usuario recorrer la lista mentalmente para decidir cuál soltar. La decisión
más útil aquí es **nombrar** la tarea más posponible, no añadir otro botón ni pantalla.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- Nuevo `data class DeferralSuggestion(taskId, title)` + campo `deferralSuggestion` en `DaySummary`,
  poblado solo cuando `dayLoad == OVERLOADED`.
- `SummaryEngine.mostDeferrableTask`: heurística honesta y conservadora — **nunca** sugiere una
  vencida (posponer un retraso lo empeora); entre las de hoy no vencidas elige la de **menor
  prioridad** (LOW>NORMAL>HIGH>URGENT en "posponibilidad") y, a igual prioridad, la que **vence
  más tarde** (más margen → más segura de aplazar sin riesgo inminente). No muta nada: solo nombra.
  (c.67 refina: también excluye tareas **en curso** e **inminentes**.)
- En `TodayScreen.kt` se reemplaza la línea OVERLOADED genérica por "No cabe todo hoy. Una opción
  es dejar para mañana «<tarea>»." dentro de la MISMA línea `bodySmall` existente. Sin tarjeta,
  sin botón, sin acción automática (el usuario decide; Ordía solo sugiere).
- Heurística determinista, sin random ni "IA". No simula modelo.

**Tests**: +4 en `SummaryEngineTest.kt` (sugiere menor prioridad, excluye vencidas, a igual
prioridad elige la que vence más tarde, sin posponible → null). **507 domain tests PASS**
(`bash tools/run_domain_tests.sh`, 26 clases — 502 c.65+c.67 paralelo + 5 netos c.66),
smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea
en la tarjeta de Today en dispositivo.
| P1/P2 | Parser — fechas relativas/pasadas/imposibles + rango horario + recurrencias laborables/quincenal/bare + día de mes suelto | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 base (353 tests) + ampliación followers c.42 cont. (358 tests); recurrencia quincenal "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura → hoy c.42 cont.2 (362 tests); listas de días sin prefijo ("gym sábados y domingos") c.42 (369 tests); "entre semana"/"días laborables/hábiles"/"de lunes a viernes" = WEEKLY [1-5] c.43 (376 tests); fecha/hito "la quincena" (1ra/2da/sin cualificar) c.44 (388 tests); `nextBestTask` time-aware (widget/asistente) c.45 (394 tests); **"el 15" día de mes suelto con artículo** c.47 (394+4 tests); **"de aquí a N"/"de acá a N" prefijo relativo coloquial** c.50 (413 tests); **DayPlanner conflicto startAt otro día** c.51 (415 tests); **intervalo+días "cada 2 semanas los lunes"/"cada quincena los lunes y viernes"/"cada 3 semanas de lunes a viernes"** c.54 (428 tests); **"cada mañana/tarde/noche/madrugada" + "todas las mañanas/tardes/noches" como recurrencia DIARIA** con hora canónica (c.55, 435 tests); **autocompletar padre al cerrar última subtarea desde notificación** (`ReminderActionReceiver.ACTION_COMPLETE` ↔ `SubtaskRules.shouldAutoCompleteParent`) (c.56); **intervalo con número escrito "cada dos semanas"/"cada tres meses"/"cada quince días"/"cada dos años"** (c.57, 439 tests); **fracción sub-hora "a las 9 y media"/"a las 3 y cuarto" (media→30, cuarto→15) + conector caribeño "en la tarde/noche/mañana"** (c.58, 450 tests); **verbo de recordatorio sin cantidad "recuérdame/avísame/no dejes que olvide" con fecha límite → recordatorio 30 min antes + verbo limpiado del título** (c.59, 455 tests); **rango horario con minutos/meridiem en ambos extremos "clase de 9:30 a 11"/"de 9am a 11am"/"de 2pm a 4pm" → duración real (fin−inicio) en minutos + título limpio** (c.60, 463 tests); **meridiem sin "a las" "Reunión 2pm"→14:00 (era 02:00) + hora de inicio del rango como dueAt "de 9 de la tarde a 11 de la noche"→21:00 (era 15:00)** (c.61, 469 tests); **editor de tareas preserva el offset de recordatorio personalizado al editar campos no relacionados** (`ReminderRules.resolveReminderAt` ↔ `EditorDialogs`): antes `reminderAt = due - 30min` siempre destruía offsets explícitos ("2h antes") al cambiar prioridad/proyecto/etiquetas, y para recurrentes corrompía el recordatorio de TODAS las ocurrencias futuras (c.62, 475 tests; 481 tras rebase con run paralelo c.61); **Guardián detecta vencidas importantes: la tarea más atrasada con ≥2 días (olvidada) sube a `Tone.FOCUSED` y surface cuánto tiempo lleva ("3 días"/"2 semanas") pidiendo la decisión real (hacer hoy/reprogramar/quitar) en vez de "empieza por esta"** (`GuardianCoach.forgottenAgeLabel` ↔ `FORGOTTEN_DAYS_THRESHOLD`): antes toda vencida recibía `GENTLE` + mensaje genérico idéntico sin distinguir 10 min de 10 días, sin ayudar a recuperar compromisos olvidados (c.63, 485 tests); **veredicto honesto del día en el resumen de Today: `SummaryEngine` calcula `DayLoad` (LIGHT/ON_TRACK/FULL/OVERLOADED) comparando los minutos restantes con el tiempo libre hasta el fin de jornada (9–18) y lo surface como UNA línea accionable en la tarjeta existente ("El día va a tiempo. Sigue con la siguiente tarea." / "El día está lleno pero cabe. Empieza ya la próxima tarea." / "No cabe todo hoy. Elige qué dejar para mañana.") en vez de obligar al usuario a hacer la aritmética mental (c.65, 500 tests); **sugerencia concreta de tarea a posponer cuando el día está saturado: cuando `DayLoad == OVERLOADED`, `SummaryEngine` propone UNA tarea real (la de menor prioridad y, a igual prioridad, la que vence más tarde = más margen), nunca una vencida, y la surface en la misma línea ("No cabe todo hoy. Una opción es dejar para mañana «…».") en vez del vago "elige qué dejar" (c.67, 516 tests); "el N del mes que viene"/"del próximo mes"/"del mes próximo" → día N del mes siguiente** (`nextMonthDayPattern` ↔ `nextMonthDayDueAt`): antes `nextPeriodPattern` robaba "mes que viene" como +30d genérico ignorando el día explícito (→ fecha errónea) y dejaba el residuo "el N del" en el título; día imposible se ajusta al último día válido del mes destino; "el mes que viene" sin día sigue siendo +30d (c.68, 516 tests); "el 0/99 de <mes>" ya no crashea (valida `day in 1..31`); "el 15 de agosto del 2027" captura el año "del" → 2027 (no 2026) y título limpio ; **orden inverso "el mes que viene el N"/"el mes que viene el día N"/"el próximo mes el N"/"el mes próximo el N" → día N del mes siguiente** (`nextMonthDayReversePattern` ↔ `nextMonthDayReverseDueAt`, resolución idéntica a la forma directa c.68): antes `nextPeriodPattern` robaba "el mes que viene" como +30d ignorando el día explícito (→ fecha errónea, recordatorio de vencimiento mensual una semana tarde = compromiso olvidado) y dejaba "el día N" como residuo en el título; simétrico al c.68 (c.71, 534 tests); **"jueves que viene"/"próximo jueves"/"lunes próximos" dicho en el propio día objetivo → +7 (no HOY)** (`weekdayMatch` ↔ `nextExplicit`): antes el modificador "que viene"/"próximo" era grupo no capturador, la rama nunca distinguía "próximo jueves" del "jueves" suelto → `weekdaySameDayCandidate`+`nextWeekdayOrSame` dejaban la fecha en hoy (compromiso futuro agendado el día equivocado); con `nextExplicit` → `nextWeekday` estricto (+7); día suelto con hora futura sigue pudiendo ser hoy (c.72, 541 tests); **"la semana que viene el lunes/viernes"/"el lunes de la semana que viene" → día objetivo de la semana próxima** (`nextWeekWeekdayReversePattern` + `nextWeekWeekdayForwardPattern` ↔ `nextWeekWeekdayDate`): antes `nextPeriodPattern` robaba "la semana que viene" como +7d ignorando el día explícito (→ cita en día equivocado); ancla al próximo lunes estricto + offset del weekday (c.73, 549 tests)** |

## Último trabajo — Ciclo 70: Parser — P0 crash día fuera de rango + P1 "del 2027" no capturaba el año

Dos fixes de integridad/crash en `NaturalTaskParser` (misma área: fecha con mes
nombrado). Ambos son inputs de texto libre reales que un usuario escribe
cotidianamente. (Rebasado sobre c.69 feat resumen accionable de un run paralelo;
renumerado de c.69→c.70 para evitar colisión de numeración.)

**P0 — crash ante día fuera de rango** (`parseMonthNameDate`): frases como
**"el 0 de septiembre"**, **"el 99 de enero"**, **"el 00 de marzo"** lanzaban
`DateTimeException` no capturada al intentar `LocalDate.of(year, month, day)` con
`day=0/99/00` → **crash de la app** ante entrada de texto libre. El
`dayOfMonthPattern` suelto ya validaba `day in 1..31` (c.47), pero la rama de mes
nombrado no. Fix: `val day = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null`
— día inválido → `dueAt=null` y la frase queda como título, sin caer.

**P1 — año "del" no capturado** (`monthNamePattern`): el español estándar escribe
**"el 15 de agosto del 2027"** (contracción "de" + "el" = "del" antes del año), no
"de 2027". El regex `monthNamePattern` solo aceptaba `\s+de\s+(\d{2,4})` → el año
quedaba **sin capturar**, causando DOS fallos: (1) **fecha errónea** — "el 15 de
agosto del 2027 a las 10" se agendaba para **2026** en vez de **2027** (el año
nunca se aplicaba; caía al año por defecto); (2) **título corrompido** —
"del 2027" quedaba como residuo ("del 2027"). Fix: `(?:\s+de\s+(\d{2,4}))?` →
`(?:\s+del?\s+(\d{2,4}))?` (acepta "de" Y "del"). El `del?` también cubre la
variante de 2 dígitos ("del 26"). Como el mismo patrón se usa en el cleanup del
título, "del 2027" se consume completo (sin residuo). P1: compromiso anual
(vencimiento, renovación, impuesto) agendado en año erróneo + título basura.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
dos cambios de 1 línea en `NaturalTaskParser.kt` (validación de rango + `del?` en
el regex). No afecta recurrencias, horas, ni otras ramas de fecha.

**Tests**: +6 en `NaturalTaskParserTest.kt` (P0: "el 0/99/00 de <mes>" →
`dueAt=null` sin crash, 3 tests; P1: "el 15 de agosto del 2027 a las 10" →
2027-08-15 10:00 + título limpio + variante 2 dígitos, 3 tests). **528 domain
tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 522 c.69 + 6 nuevos),
smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM confirmó antes/después en
todos los casos sin regresión (incl. "de 2027" original, "recordarme… del 2027",
"renovar suscripción el 1 de enero del 2027"). **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales; render real del parser en
la app (sin Android SDK).

## Último trabajo — Ciclo 71: Parser — orden inverso "el mes que viene el N" → día N del mes siguiente

Fix P1 de integridad de agenda (`NaturalTaskParser`), continuación simétrica del c.68.
La forma cotidiana **"Pagar tarjeta el mes que viene el 5"** (periodo ANTES del día,
opuesto al "el 5 del mes que viene" del c.68) se agendaba mal: `nextPeriodPattern` robaba
"el mes que viene" como +30d genérico e **ignoraba el día explícito**, produciendo fecha
errónea (p. ej. 12/09 en vez del 05/09). Para un vencimiento mensual anclado a un día
(tarjeta, alquiler, cobro) el recordatorio se disparaba **una semana tarde** —
compromiso olvidado. En la variante "el día N" además dejaba "el día 5" como residuo en
el título.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
nuevo `nextMonthDayReversePattern` regex (periodo + "el" + día, con o sin "día", en orden
inverso) con resolución **idéntica** a `nextMonthDayDueAt` del c.68 (día N de
`plusMonths(1)`, clamp al último día válido del mes destino). Se procesa **ANTES** que
`nextPeriodPattern` para consumir la frase completa. Integrado en la cadena
`effectiveRelativeDueAt`/`relativeIsDays` (combina con hora explícita). Exige día →
"el mes que viene" sin día sigue +30d.

**Tests**: +6 en `NaturalTaskParserTest.kt` (`elMesQueVieneElNResuelveDiaNDelMesSiguiente`,
`elMesQueVieneElDiaNResuelveDiaNDelMesSiguiente`, `elProximoMesElNResuelveDiaNDelMesSiguiente`,
`elMesProximoElNResuelveDiaNDelMesSiguiente`, `elMesQueVieneElNRespetaHoraExplicita`,
`elMesQueVieneElNClampDia31CuandoMesTiene30`). **534 domain tests PASS**
(`bash tools/run_domain_tests.sh`, 26 clases — 528 c.70 + 6 nuevos), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/
Android/UI/Room con DAOs reales; render real del parser en la app (sin Android SDK).

**STALE_BASE**: originalmente c.70, renumerado a c.71 tras colisión con un run paralelo
que reclamó c.70 (P0 crash `parseMonthNameDate` + P1 "del 2027"). Reconciliado con
`git rebase` no destructivo; el código se auto-mezcló (áreas ortogonales); conflictos
solo en memoria, resueltos conservando ambos runs.

**Siguiente (descubierto)**: semana+weekday "la semana que viene el lunes/viernes"
ahora FIXED (c.73). Buscar nueva oportunidad de producto en captura/parser/inteligencia
(ver BACKLOG y áreas de dirección).

## Último trabajo — Ciclo 68: Parser — "el N del mes que viene" → día N del mes siguiente

Fix P1 de integridad de agenda/dato (`NaturalTaskParser`). La forma cotidiana
**"Llamar al banco el 15 del mes que viene"** se agendaba al día **equivocado**
(2026-09-12 = hoy+30d en vez del 15 de septiembre) Y el título quedaba corrompido
(**"Llamar al banco del"** — "el 15" consumido como día de mes suelto, "del"
huérfano). Un compromiso mensual anclado a un día concreto (vencimiento, cobro,
cita) caía en la fecha genérica "+30 días desde hoy" y perdía el día explícito.
P1: cita/factura agendada en día erróneo + título basura.

**Causa raíz**: `nextPeriodPattern` casaba "mes que viene" y lo reemplazaba por
espacio, dejando "el 15 del " en `working`. `nextPeriodDueAt` (= +30d) entraba en
la cadena `effectiveRelativeDueAt` y ganaba sobre `dayOfMonthDate` (el día 15,
calculado pero sombreado). En el cleanup del título, `dayOfMonthPattern.replace`
borraba "el 15" pero no "del", dejándolo huérfano.

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- Nuevo `nextMonthDayPattern`: `\bel\s+(\d{1,2})\s+(?:del?\s+)?(?:mes\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b` — captura día + cualificador de "mes siguiente" en UNA frase.
- Se procesa **ANTES** de `nextPeriodPattern` para consumir la frase completa (día + "mes que viene"), evitando que éste la robe como +30d y dejando residuo.
- `nextMonthDayDueAt` = día N de `base.toLocalDate().plusMonths(1)`, con clamp al último día válido si el día no existe en el mes destino (p. ej. 31 de febrero → 28/29). Resuelto como día (epoch medianoche) para combinarse con hora explícita ("el 15 del mes que viene a las 10" → 15 del mes siguiente a las 10:00).
- Se añade a la cadena `effectiveRelativeDueAt` **antes** de `nextPeriodDueAt` y a `relativeIsDays` para que respete hora explícita.

**Tests**: +6 en `NaturalTaskParserTest.kt` ("el 15 del mes que viene" → 15/08;
"el 10 del próximo mes" → 10/08; "el 10 del mes próximo" → 10/08; con hora →
05/08 09:00; "el 31 del mes que viene" respeta 31 cuando el mes destino tiene 31;
no-regresión "el mes que viene" sin día sigue siendo +30d). **513 domain tests
PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 513 c.67 + 6 nuevos),
smoke 25 OK (`tools/run_domain_checks.sh`). Probe JVM confirmó antes/después en
28 casos sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room
con DAOs reales; render real del parser en la app (sin Android SDK).

## Último trabajo — Ciclo 65: Resumen — veredicto honesto del día en la tarjeta de Today

Mejora P2 de inteligencia/honesta (`SummaryEngine` ↔ `TodayScreen`). La tarjeta de resumen de
Today mostraba "X completadas · Y para hoy" + un badge de minutos ("120m"), pero dejaba al usuario
hacer la aritmética mental: ¿120m a las 12:00 caben en el día? ¿O tengo que soltar algo? El número
de minutos no se convertía en una **decisión accionable**. (Área de dirección explícita:
"priorización inteligente", "mejores resúmenes del día", "What Now más útil".)

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**:
- Nueva enum `DayLoad { LIGHT, ON_TRACK, FULL, OVERLOADED }` + campo `dayLoad` en `DaySummary`.
- `SummaryEngine.assessDayLoad`: compara `remainingMinutesToday` con el tiempo libre hasta el fin
  de jornada (misma ventana que `DayPlanner`, 9:00–18:00):
  - nada pendiente → LIGHT (no se muestra, la tarjeta ya dice "0 para hoy");
  - ≤ mitad de la jornada libre → ON_TRACK;
  - ≤ jornada libre entera → FULL;
  - > → OVERLOADED.
- Pasado el fin de jornada (19:00+) cualquier trabajo restante → OVERLOADED (no hay capacidad).
- En `TodayScreen.kt` se añade UNA línea (`bodySmall`) dentro de la tarjeta de resumen existente:
  "El día va a tiempo. Sigue con la siguiente tarea." / "El día está lleno pero cabe. Empieza ya
  la próxima tarea." / "No cabe todo hoy. Elige qué dejar para mañana." Sin nueva tarjeta, sin
  botones; reutiliza el contenedor existente.
- Heurística honesta, determinista, sin random ni "IA". No simula modelo.

**Tests**: +6 en `SummaryEngineTest.kt` (LIGHT, ON_TRACK, FULL, OVERLOADED, post-fin-de-jornada,
inicio de día). **500 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 494 c.64
+ 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión.
**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales; render real de la línea
en la tarjeta de Today en dispositivo.

## Último trabajo — Ciclo 63: Guardián — detección de vencidas importantes (recuperación de tareas olvidadas)

Mejora P2 de inteligencia/honesta (`GuardianCoach`). Antes, la rama de vencidas del coach daba
tratamiento **idéntico** a toda tarea atrasada: `Tone.GENTLE` + "Esta tarea está atrasada. Empieza
con un bloque corto." / "Tienes N tareas atrasadas. Comienza por esta." — sin distinguir una tarea
con 10 minutos de retraso de otra con 10 días. El coach no ayudaba a **recuperar** compromisos
olvidados, solo repetía "empieza por esta". (Área de dirección explícita: "detección de vencidas
importantes", "recuperación de tareas olvidadas".)

**Solución (mínima, sin nueva pantalla/botón — "menos interfaz, más potencia")**: nueva heurística
honesta de aritmética temporal en `GuardianCoach.insight`:
- Calcula la antigüedad de la tarea **más** atrasada: `max((now - dueAt) / MILLIS_PER_DAY)`.
- Si la más atrasada lleva **≥ 2 días** (`FORGOTTEN_DAYS_THRESHOLD`), se considera **olvidada**:
  sube a `Tone.FOCUSED` y el mensaje **surface cuánto tiempo lleva** (`forgottenAgeLabel`:
  "1 día"/"3 días"/"2 semanas"/"1 mes") y **plantea la decisión real** — "Hazla hoy o muévela con
  intención, no la dejes pasar otra vez" (caso único) / "Elige una: hacerla hoy, reprogramarla o
  quitarla" (varias). El coach deja de repetir "empieza por esta" y ayuda a **decidir**, no solo a
  priorizar.
- Si la vencida es leve (< 2 días, p. ej. misma tarde o ayer): mantiene `GENTLE` + mensaje actual.
- Heurística honesta (tiempo real sobre `dueAt`, no random ni "IA" fingida). Sin falso positivo:
  el mensaje incluye el número exacto de días/semanas, verificable.

**Tests**: +4 en `GuardianCoachTest.kt` (`mildlyOverdueSameDayStaysGentle` [8h mismo día → GENTLE],
`forgottenOverdueTaskBecomesFocusedAndSurfacesAge` [4 días → FOCUSED + "4 días"],
`forgottenOverdueUsesWeeksLabelPastSevenDays` [14 días → "2 semanas"],
`forgottenOverdueGroupSurfacesOldestAge` [grupo: FOCUSED + surface edad más antigua 3 días]).
El test previo `overdueWorkWinsOverEverythingElse` (27h → GENTLE) sigue verde: 27h < 2 días.
**485 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI;
`GuardianCoach` se consume en Compose (sin Android SDK). La lógica vive en el dominio probado.

## Histórico reciente — Ciclo 62: Integridad de recordatorios — el editor preserva el offset personalizado al editar

Fix P1 de integridad de datos (`ReminderRules` / `EditorDialogs`). `EditorDialogs` recalculaba
`reminderAt = dueAt - 30*60_000L` en **cada** guardado cuando el toggle de recordatorio estaba
activo. Para una tarea **existente** cuyo recordatorio tenía un offset explícito distinto de 30 min
(p. ej. "recuérdame 2 horas antes" → `reminderAt = due - 120min`, capturado por el parser o por
`CommitmentEngine`), bastaba con abrir el editor y cambiar un campo **no relacionado** (prioridad,
proyecto, etiquetas, flagged) y guardar para que el offset se **sobrescribiera** a 30 min. El usuario
pedía "2 horas antes" y, tras editar la prioridad, Ordía avisaba solo 30 min antes. Silencioso.

Peor aún en recurrentes: `RecurrenceEngine.nextOccurrence` reutiliza el offset
(`dueAt - reminderAt`) en **TODAS** las ocurrencias futuras. Una sola edición inocua de un campo
ajeno corrompía el horario del recordatorio de la tarea para siempre en cada nueva ocurrencia.

**Solución (mínima, sin nueva pantalla/botón)**: nueva regla pura `ReminderRules.resolveReminderAt(existing, reminderEnabled, dueAt)`:
- `reminderEnabled=false` o `dueAt=null` → `null` (desactiva / sin fecha).
- `existing` con `reminderAt` y `dueAt` previos y `dueAt` **sin cambios** → conserva el `reminderAt`
  exacto (offset intacto: editar prioridad no toca el recordatorio).
- `existing` con offset previo y `dueAt` **cambiado** → **traslada** el offset:
  `dueAt - (oldDueAt - oldReminderAt)` ("15 min antes" sigue siendo 15 min antes en la nueva hora).
- Resto (tarea nueva, o recordatorio recién activado sin offset previo) → `DEFAULT_REMINDER_OFFSET_MS`
  (30 min antes, convención del projeto). Constante centralizada en `ReminderRules` (antes duplicada
  y privada en `CommitmentEngine`).

`EditorDialogs` ahora llama a esta regla en lugar del cálculo inline. Simétrico en espíritu al fix
c.52 (snooze no corrompe el offset) y c.56 (consistencia notificación vs app): la preferencia de
recordatorio del usuario es sagrada y no debe degradarse por ediciones ajenas.

**Tests**: +9 en `ReminderRulesTest.kt` (toggle off→null, due null→null, nueva→30min,
recién activado→30min, editar campo no relacionado preserva offset 2h, cambiar due traslada offset,
recurrente preserva offset en próxima ocurrencia, toggle off con existente→null, limpiar due→null).
**475 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 466 c.60 + 9 nuevos); smoke
25 OK (`tools/run_domain_checks.sh`). Tras rebase con run paralelo c.61 (parser-meridiem), el total
verificado es **481 domain tests PASS**. **NO VERIFICADO**: gradle/lint/assemble/Android/UI; el render
real del editor (`EditorDialogs` es Compose, requiere Android SDK). La corrección lógica vive en la
regla pura ya probada.

**Archivos modificados**: `app/src/main/java/com/ordia/app/domain/ReminderRules.kt`,
`app/src/main/java/com/ordia/app/ui/components/EditorDialogs.kt`,
`app/src/test/java/com/ordia/app/domain/ReminderRulesTest.kt`,
`AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.

## Último trabajo — Ciclo 64: Parser — forma standalone "N de la tarde/noche" sin "a las" ni rango

Fix P2 de captura/agenda (`NaturalTaskParser`). La forma cotidiana **"Taller 9 de la tarde"** (hora
+ parte del día, SIN "a las" y SIN segundo extremo de rango) se agendaba a la hora **canónica** de la
parte del día (tarde→15:00) en vez de la hora **explícita** (9→21:00), y el número quedaba como residuo
en el título ("Taller 9"). Igual para "Cita 10 de la mañana"→09:00 (debería 10:00), "Evento 9 de la
madrugada"→04:00 (debería 09:00). El usuario escribía una hora concreta y Ordía la ignoraba.

- **Causa raíz**: sin segundo extremo de rango, `rangeMatch` no casaba y no había `rangeStartTime`;
  la hora caía al respaldo `standalonePartOfDayTime` (canónica "de la tarde"→15:00), que gana sobre
  cualquier número suelto. El ciclo 61 ya arregló la variante con rango ("de 9 de la tarde a 11 de la
  noche"); esta es la forma **standalone** sin rango.
- **Fix mínimo**: nuevo `standaloneHourPartOfDayPattern`
  `(?i)(?<![:\d])(\d{1,2})(?::([0-5]\d))?\s+de\s+la\s+(tarde|noche|madrugada|mañana|manana)(?!\s+de\s+[a-záéíóúüñ])`
  + `resolveStandaloneHourPartOfDay` (tarde/noche→+12 si N<12; 12 de la noche→0 medianoche;
  12 de la tarde→12 mediodía; madrugada/mañana→AM tal cual). Insertado en la cadena de respaldo de
  `parsedTime` **antes** de `standalonePartOfDayTime` (canónica) para que la hora explícita gane.
- **Guard anti-regresión**: solo se aplica cuando `explicitTime == null` (no hubo "a las …"), porque
  "a las 9 de la tarde" ya lo resuelve `timePatterns` y aún NO se ha borrado de `working` en ese punto;
  sin el guard, el patrón robaba "9 de la tarde" y dejaba el residuo "a las" en el título. El lookahead
  negativo `(?!\s+de\s+[a-z…])` evita colisión con fechas ("9 de marzo" → mes, no parte del día).
- **Tests**: +9 (`standaloneNueveDeLaTardeResuelve21hYLimpiaTitulo`, `…OchoDeLaNoche…20h`,
  `…DiezDeLaManana…10h`, `…NueveDeLaMadrugada…9h`, `…DosDeLaTarde…14h`, `standaloneDoceDeLaNocheEsMedianoche`,
  `standaloneDoceDeLaTardeEsMediodia`, `conALasNueveDeLaTardeNoDejaResiduo`, `sinNumeroDeLaTardeMantieneCanonica15h`).
  Probe JVM confirmó antes/después en 21 casos (incl. "9 de marzo", "el 15 de agosto", rango, "mañana 9 de la tarde").
  **494 domain tests PASS** (`tools/run_domain_tests.sh`, 26 clases — 485 c.63 + 9 nuevos), smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

### Ciclo 64 (cont.) — Parser — fix P1 `monthNameMatch.find()` casaba mes inválido y ocultaba fecha real

Fix **P1** de integridad de agenda (`NaturalTaskParser`), descubierto al auditar el fix c.64 de
"standalone N de la tarde". En `"Taller 9 de la tarde el 15 de agosto"`, `monthNamePattern.find()`
casaba el **primer** match `"9 de la"` (grupo-mes `"la"`, inválido); `parseMonthNameDate` retornaba
`null` pero `monthNameMatch` ya no avanzaba a examinar el match posterior `"15 de agosto"` (mes
válido) → `monthNameDate` quedaba `null` → la cita se agendaba para **HOY** en lugar del **15 de
agosto**. Una cita futura explícita se perdía como evento de hoy (mismo patrón de dato-agenda
perdido que otros fixes P1 de fechas). No dependía del fix c.64: afectaba a cualquier frase donde
un "N de la parte del día" precediera a una fecha de mes válida.

- **Colisión con run paralelo**: al iniciar este run, `git fetch` reveló que `origin/openhands/autonomous-ordia`
  había avanzado `6e43206..890e8b4` (otro run implementó el MISMO feature P2-1 "standalone N de la
  tarde" en el ciclo 64). Resolución **no destructiva**: `stash` del trabajo local →
  `merge --ff-only` al remoto (avanza a `890e8b4`, sin force/reset/rebase) → se descartó el P2-1
  propio (redundante, ya presente en el remoto) → se reaplicó **únicamente** este fix P1 único sobre
  la nueva base. Sin sobrescribir trabajo válido del otro agente. (El P2-1 local redundante se
  descartó limpiamente; el bug P1 no estaba cubierto por el remoto.)
- **Fix mínimo**: `monthNameMatch` pasa de `monthNamePattern.find(working)` a
  `monthNamePattern.findAll(working).firstOrNull { m -> months.any { (name,_) -> m.groupValues[2].equals(name, ignoreCase = true) } }`,
  descartando matches de mes inválido y encontrando la fecha real posterior. El `parseMonthNameDate`
  posterior (que ya validaba el mes) opera ahora sobre un match garantizado válido, sin cambio.
- **Tests**: +2 (`nueveDeLaTardeConFechaMesResuelveAmbos` → 2026-08-15 21:00; `nueveDeLaMananaConFechaMesResuelveAmbos`
  → 2026-09-20 09:00). **496 domain tests PASS** (`tools/run_domain_tests.sh`, 26 clases — 494 c.64 + 2 nuevos),
  smoke 25 OK. Sin regresión en los 494 previos.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{CURRENT_STATE,RUN_LOG}.md`.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 61: Parser — meridiem sin "a las" + hora de inicio del rango como dueAt

Fix P1 de captura/agenda (`NaturalTaskParser`). Dos bugs de horario real que agendaban citas a la
hora equivocada:

- **BUG A — meridiem sin "a las"**: una hora con `am`/`pm` pero SIN el prefijo "a las" — **"Reunión 2pm"**,
  **"Cita 9am"**, **"Vuelo 8:30pm"** — se agendaba como **AM** ("2pm" → 02:00 en vez de 14:00). Los
  `timePatterns`[1] (N:MM) y [2] (Nam/Pm) llevan el meridiem en el **grupo 3**, pero `explicitTimeData`
  leía el meridiem del **grupo 4** (que solo existe en el patrón[0] "a las N", donde el grupo 3 es la
  fracción "y media"/"y cuarto"). En los patrones sin "a las" el meridiem se perdía y la hora caía a AM.
  Una reunión de tarde aparecía a las 02:00 de la madrugada.
- **BUG B — hora de inicio del rango ignorada**: en un rango "de H1 [meridiem] a H2 [meridiem]" sin
  "a las" — **"Clase de 9 de la tarde a 11 de la noche"**, **"Reunión de 2pm a 4pm"** — la `dueAt` caía a la
  **hora canónica de la parte del día** ("de la tarde" → 15:00) en vez de la **hora de inicio del rango**
  (21:00). El rango daba la duración (120 min, correcto) y se eliminaba del título, pero como no había
  tiempo explícito, la hora para `dueAt` venía del respaldo `standalonePartOfDayTime`. El inicio real del
  evento se ignoraba → la cita se agendaba a la hora canónica genérica, no a la hora real del evento.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**:
- BUG A: `explicitTimeData` ahora lee el meridiem del grupo 4 si existe, si no del grupo 3
  (disambiguando fracción vs meridiem según el patrón que casó). `hasExplicitMeridiem` correcto en todos.
- BUG B: el bloque `rangeMatch` (rango validado) se mueve a **antes** de la resolución de
  `parsedTime`/`dueAt`, y se extrae `rangeStartTime: LocalTime?` del inicio del rango (resolución
  absoluta con meridiem, **solo si el rango fue validado** — no filtra horas de rangos rechazados como
  "de 2 a 5 entradas"). `rangeStartTime` entra en la cadena de respaldo de `parsedTime` **después** del
  tiempo explícito ("a las") y **antes** de los respaldos canónicos de parte del día: un tiempo explícito
  sigue ganando, pero sin él la hora de inicio del rango reemplaza a la canónica genérica. Lógica local
  honesta, sin IA.

**Tests**: +6 en `NaturalTaskParserTest.kt` — `barePmTimeWithoutAParsesAsPm` ("2pm"→14:00),
`bareAmTimeWithoutAParsesAsAm` ("9am"→09:00), `barePmTimeWithMinutesWithoutAParsesAsPm`
("8:30pm"→20:30), `rangeWithDeLaTardeSetsDueAtToStart` ("9 de la tarde a 11 de la noche"→due 21:00
+ dur 120), `rangeWithPmMeridiemSetsDueAtToStart` ("2pm a 4pm"→due 14:00),
`rangeWithAmMeridiemSetsDueAtToStart` ("9am a 11am"→due 09:00). **472 domain tests PASS**
(`bash tools/run_domain_tests.sh`, 26 clases — 463 c.60 + 6 nuevos c.61 + 3 c.62 "pasado mañana"
de run paralelo reconciliado), smoke 25 OK (`tools/run_domain_checks.sh`). Sin regresión (rangos
24h, "de 2 a 5 entradas" rechazado, "curso de 8:30 a 10:30 horas" intactos). **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app (sin
Android SDK).

**Reconciliación con run paralelo**: el HEAD inicial local (`4d2ca71`, c.60) estaba 1 commit
detrás del remoto (`ee4807d`, c.62 "pasado mañana" de otro run). Al hacer stash → pull --ff-only
→ stash pop, los cambios se auto-mergearon: mi fix toca `explicitTimeData`/`rangeMatch`/`rangeStartTime`;
el c.62 toca el consumo de título de "pasado día-semana". Único conflicto fue en `BACKLOG.md`
(ambos editamos la cola de la tabla) — resuelto conservando la entrada c.59 corregida del remoto
+ mis entradas c.61/c.61-P2. Sin force push, sin reset --hard, sin sobrescribir trabajo válido.

## Último trabajo — Ciclo 59: Parser — verbo de recordatorio sin cantidad ("recuérdame ... mañana a las 3")

Fix P1 de captura/recordatorios (`NaturalTaskParser`). La forma cotidiana **"recuérdame llamar a
mamá mañana a las 3 de la tarde"** expresaba una intención de recordatorio explícita PERO sin cantidad
("2 horas antes"). El parser solo programaba `reminderOffsetMinutes` cuando un `reminderPattern`
extraía una cantidad; sin ella `reminderOffsetMinutes=null` → **ningún recordatorio se agendaba**
(`reminderAt = dueAt - offset` = null aunque hubiera `dueAt`) **Y** el verbo "recuérdame" quedaba como
residuo en el título. Resultado: la cita se olvidaba justo cuando el usuario había pedido expresamente
que se le avisara. El bug es de captura + recordatorios (P1: evita olvidos, persistencia/intención
perdida). Simétrico al `reminderSignal` de `UniversalCaptureEngine` (que enruta "recuérdame" como
target REMINDER), pero el parser no recogía la intención cuando la frase también contenía una fecha
(ruta TASK con `dueAt`).

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nuevo `bareReminderVerbPattern`
(`recuérdame|avísame|notifícame|recordatorio|no dejes que olvide`) detectado **tras** extraer los
recordatorios con cantidad (así "recuérdame 2 horas antes" usa el offset explícito y NO el respaldo).
Si el verbo aparece pero no se extrajo offset Y hay `dueAt`, se asume **30 min antes** (convención del
projeto, mismo valor que `CommitmentEngine.DEFAULT_REMINDER_OFFSET_MS` y `EditorDialogs`). El verbo se
elimina del título **tras** consumir fechas/horas (para no romper el parseo de "recuérdame mañana a
las 3", donde "mañana" es fecha). Sin `dueAt` no se falsifica el offset (no se puede programar
`reminderAt`). Heurística honesta, riesgo de falso positivo bajo (el verbo es señal inequívoca de
intención de aviso). Compone bien con el c.58 (fracción sub-hora): "recuérdame llamar mañana a las 3
y media de la tarde" → due=mañana 15:30, offset=30min, verbo limpiado.

**Tests**: +5 en `NaturalTaskParserTest.kt` (`verboRecordatorioSinCantidadConDueAplicaOffset30`,
`verboAvisameSinCantidadConDueAplicaOffset30`, `verboNoDejesQueOlvideConDueAplicaOffset30`,
`verboRecordatorioSinCantidadSinDueNoFalsificaOffset`, `verboRecordatorioConCantidadExplicitaUsaOffsetExplicito`).
**455 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 450 c.58 + 5 nuevos), smoke 25 OK
(`tools/run_domain_checks.sh`). Sin regresión (offsets explícitos y fracciones intactos). **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app.

**Reconciliación con run paralelo**: el HEAD inicial local (`053e7ff`, c.57) estaba actualizado, pero
durante el trabajo dos commits aterrizaron en remoto (c.58 fracción sub-hora de otro run + un commit
de CI). Al hacer stash → pull --ff-only → stash pop, los cambios de código (parser + tests) se
auto-mergearon limpiamente (áreas ortogonales: fracción sub-hora/`timePatterns` vs. verbo de
recordatorio/`bareReminderVerbPattern`); solo `CURRENT_STATE.md` quedó en conflicto (cabecera de
estado) y se resolvió conservando ambos trabajos, renumerando este fix de c.58 → c.59 (el otro run
tomó c.58). Sin force push, sin reset --hard, sin sobrescribir trabajo válido.

## Último trabajo — Ciclo 58: parser reconoce "y media"/"y cuarto" y "en la tarde" (captura natural en español)

Auditoría del motor de parsing natural descubrió dos fallos reales de captura (la superficie más
frecuente de Ordía), ambos honestos y sin IA. Aterriza tras reconciliación no destructiva con los
ciclos 55–57 (que tocaron `parseRecurrence` y el bloque de tiempo del mismo archivo): el fix se
integró limpiamente sobre esa base nueva y compone bien con "cada mañana a las 8 y media" (DAILY +
08:30).

**Fix A (P1) — "a las 9 y media" / "a las 3 y cuarto"**: fracción sub-hora cotidiana en español.
Antes el parser NO reconocía "y media"/"y cuarto" como modificador de la hora: "Cita a las 9 y
media" → `due=09:00` (debería 09:30) y `title='Cita y media'` (la frase se filtraba al título). Una
cita/reunión programada 30 min mal (15 min con "y cuarto") y con título sucio. P0 de producto: hora
mal programada = reunión perdida o recordatorio en el momento erróneo.

**Fix B (P2) — "en la tarde/noche/mañana"**: forma caribeña/hispanoamericana (zona de la app,
`America/Santo_Domingo`) del conector de parte del día. Antes solo se reconocían "a la"/"de la"/
"por la"; "en la" no casaba: "hoy en la tarde" → `due=09:00` (debería 15:00) y `title='hoy en la
tarde'` (residuo). Comparar con "hoy a la tarde" que SÍ funcionaba → inconsistencia según la
preposición que usara el usuario.

**Solución (mínima, sin nueva pantalla/botón)**:
- `timePatterns[0]` gana grupo 3 opcional `(\s+y\s+(media|cuarto))?` tras la hora/minutos, y el
  meridiem pasa a grupo 4 (leído con `getOrNull` para no romper los otros patrones que no tienen
  el grupo). `minute = explicitMinute ?: (media→30, cuarto→15, else→0)`. Respeta meridiem/contexto
  PM ("a las 9 y media de la tarde" → 21:30, "y media pm" → 21:30, "y media de la madrugada" →
  04:30). Los minutos explícitos (`9:30`) siguen teniendo prioridad.
- `standalonePartOfDayPattern` añade `en\s+la` a los conectores, y `mananaAsDate` añade `en` a su
  `timeMarker` para que "en la mañana" no se cuente como fecha "mañana" (misma técnica usada en
  c.39 para "de/por/a la mañana").

Lógica local honesta, sin IA falsa. Retrocompatible (sin cambios de firma pública).

**Tests**: +12 en `NaturalTaskParserTest.kt` (7 de Fix A: `y media`/`y cuarto` con y sin meridiem/
tarde/noche/madrugada/pm/am; 5 de Fix B: "hoy en la tarde", "mañana en la noche", "en la mañana"
sin fecha, "en la tarde a las 4" con contexto PM). Probe JVM verificó además interacción con la
recurrencia DIARIA "cada mañana" del c.55 ("Meditar cada mañana a las 8 y media" → DAILY 08:30) y
que las regresiones no se rompen (horas en punto, "a las N horas", "por la/de la/a la" preexistentes,
"media hora"/"un cuarto de hora" como duración siguen OK). **450 domain tests PASS**
(`tools/run_domain_tests.sh`, 26 clases — 439 base c.57 + 11 nuevos), smoke 25 OK
(`tools/run_domain_checks.sh`).

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales, captura real en el
dispositivo (parser probado solo en JVM pura con stubs).

## Último trabajo — Ciclo 57: Parser — intervalo de recurrencia con número escrito

Fix P1 de captura/recurrencia (`NaturalTaskParser.parseRecurrence`). Las formas más naturales de
expresar una cadencia no-unitaria con palabras — **"visitar a mi madre cada dos semanas"**,
**"dentista cada tres meses"**, **"reunión cada quince días"**, **"renovar pasaporte cada dos años"** —
NO se reconocían como recurrencia. La rutina quedaba como **tarea única sin fecha** (`recurrence=NONE`,
`dueAt=null`): invisible en What Now/planificador, y el recordatorio jamás disparaba. **Causa raíz
P1**: `intervalPattern` (`\bcada\s+(\d{1,3})\s*(días|semanas|meses|años)\b`) **sólo admitía dígitos**;
al escribir el número con palabra, la regex no casaba y la rama se caía a NONE. Sutil: el helper
`parseWrittenNumber` (que ya resuelve "dos"/"tres"/.../"quince") existía y se usaba para recordatorios
y "un par de", pero no se había conectado a `intervalPattern`.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: el grupo de captura del
número en `intervalPattern` pasa de `\d{1,3}` a `(\d{1,3}|<números-escritos>)`, donde la alternancia
está **acotada a los números conocidos** en `parseWrittenNumber` (un–treinta, sin sobrescribir la
unidad días/semanas/...). Al resolver, se prueba primero `toLongOrNull()` (dígito) y luego
`parseWrittenNumber(rawN)` (palabra), con `coerceIn(1,366)`. Reutiliza infraestructura existente; sin
enum ni migración. Lógica local honesta, sin random ni modelo simulado.

**Tests**: +4 en `NaturalTaskParserTest.kt` (`cadaDosSemanasParsesWeeklyInterval2`,
`cadaTresMesesParsesMonthlyInterval3`, `cadaQuinceDiasParsesDailyInterval15`,
`cadaDosAnosParsesYearlyInterval2`). **439 domain tests PASS** (`bash tools/run_domain_tests.sh`,
26 clases — 435 c.55 + 4 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Las pruebas del ciclo 55
(partOfDay DAILY) y 54 (intervalo+días) siguen en verde → sin regresión. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del parser en la app.

## Último trabajo — Ciclo 56: autocompletar padre al cerrar última subtarea desde notificación

Fix P1 de integridad de estado / tareas olvidadas. `ReminderActionReceiver.ACTION_COMPLETE`
(completar desde la notificación) completaba la subtarea, cancelaba su recordatorio y generaba su
recurrencia, pero **NO** completaba la tarea padre cuando era la última subtarea pendiente. La app
(`OrdiaViewModel.toggleTask` → `completeParentAutomatically` vía `SubtaskRules.shouldAutoCompleteParent`)
sí lo hace. Resultado: al completar el último hijo desde la notificación, el padre quedaba "pendiente"
para siempre → **tarea olvidada**, inconsistencia entre notificación y app.

**Causa raíz**: el path de notificación (`BroadcastReceiver`) duplicaba parte de la lógica de
`toggleTask` pero omitía la rama de autocompletado del padre y el registro de automatización para
deshacer.

**Solución (mínima, `ReminderActionReceiver.kt`, sin nueva pantalla/botón)**: helper
`completeParentIfDone(app, repo, completedSubtask, now)` llamado tras completar la subtarea en
`ACTION_COMPLETE`. Refleja fielmente `completeParentAutomatically`: (1) `SubtaskRules.shouldAutoCompleteParent`
(misma fuente de verdad que la app), (2) actualiza padre (completed/status COMPLETED/completedAt/updatedAt),
(3) cancela recordatorio del padre, (4) `RecurrenceEngine.nextOccurrence` + reprograma la próxima
ocurrencia, (5) registra `AutomationLogEntity` (type `subtask_auto`, `affectedTaskIdsJson`,
`undoPayloadJson` con snapshot del padre) para deshacer. Sin emitir eventos de UI (un `BroadcastReceiver`
no puede). **Mismo comportamiento que la app, ahora alcanzable desde la notificación.**

**Tests**: lógica núcleo `SubtaskRules.shouldAutoCompleteParent` ya cubierta por `SubtaskRulesTest`.
**435 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — base c.55 tras rebase). Smoke
25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: el receptor `ReminderActionReceiver` en sí
(requiere Android `Context`/`BroadcastReceiver`/Room con DAOs reales → no ejecutable en JVM pura);
gradle/lint/assemble/UI.

## Último trabajo — Ciclo 55: Parser "cada mañana/tarde/noche/madrugada" como recurrencia DIARIA

Fix P1 de captura/recurrencia (`NaturalTaskParser.parseRecurrence`). Las formas más naturales de
expresar un hábito cotidiano en español — **"meditar cada mañana"**, **"tomar pastillas cada mañana"**,
**"pasear al perro cada tarde"**, **"leer cada noche"**, **"regar las plantas cada madrugada"**, y sus
plurales **"todas las mañanas/tardes/noches"** — NO se reconocían como recurrencia DIARIA. La rutina
quedaba como **tarea única** (sin repetición). **Causa raíz P1**: la palabra "mañana" colisionaba con el
token de **fecha** "mañana" (día siguiente); el parser la consumía como fecha y la intención de
repetición desaparecía. La rutina diaria se perdía silenciosamente: el recordatorio disparaba una sola
vez y nunca más. Sutil porque solo se manifiesta cuando el recordatorio "no vuelve".

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: nueva rama al **inicio** de
`parseRecurrence` (se procesa PRIMERO) que detecta `cada <parte-del-día>` y `todas las <partes-del-día>`
y devuelve `RecurrenceResult(DAILY, interval=1, days=[], partOfDayTime, partOfDayIsPm)` con la **hora
canónica** de cada parte del día (mañana 09:00, tarde 15:00, noche 21:00, madrugada 04:00) y contexto
PM para tarde/noche. Al procesarse primero, "mañana" deja de ser candidato a fecha y la hora canónica
sustituye al respaldo genérico 09:00. `partOfDayTime`/`partOfDayIsPm` ya existían en `RecurrenceResult`
(añadidos para "esta tarde a las 4" PM offset); se reutilizan. Hora explícita ("cada mañana a las 7")
tiene prioridad sobre la canónica; contexto PM aplica offset +12 a horas sin meridiem ("cada noche a
las 10" → 22:00). "todos los días"/"diariamente" (sin parte del día) siguen en `fixedPatterns` con su
respaldo 09:00 — sin regresión. Lógica local honesta, sin random ni modelo simulado.

**Colisión de remoto (no destructiva)**: al rebasear sobre `b5c96d5` (ciclo 54 "intervalo+días"),
conflicto en `NaturalTaskParser.kt` — ambos añadían código al inicio de `parseRecurrence`: el remoto el
helper `detectWeekInterval()`, el local el bloque `partOfDayDaily`. Resolución combinando ambos:
`partOfDayDaily` PRIMERO (early-return DAILY con hora canónica) y `detectWeekInterval()` DESPUÉS
(helper para las ramas de días). Áreas ortogonales, ambos preservados. Sin force push.

**Tests**: +7 en `NaturalTaskParserTest.kt` (`cadaMananaIsDailyRecurrenceWithCanonicalTime`,
`cadaTardeIsDailyWithPmContext`, `cadaNocheIsDailyWithPmContext`, `cadaMadrugadaIsDaily`,
`todasLasNochesIsDaily`, `cadaMananaWithExplicitTimeKeepsTime`,
`cadaNocheConHoraSinMeridiemAplicaPm`). **435 domain tests PASS** (`bash tools/run_domain_tests.sh`,
26 clases — 428 c.54 + 7 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). Las 6 pruebas del ciclo
54 (intervalo+días) siguen en verde → sin regresión. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room
con DAOs reales, render real del parser en la app.

## Último trabajo — Ciclo 56: autocompletar padre al cerrar última subtarea desde notificación

Fix P1 de integridad de estado / tareas olvidadas. `ReminderActionReceiver.ACTION_COMPLETE`
(completar desde la notificación) completaba la subtarea, cancelaba su recordatorio y generaba su
recurrencia, pero **NO** completaba la tarea padre cuando era la última subtarea pendiente. La app
(`OrdiaViewModel.toggleTask` → `completeParentAutomatically` vía `SubtaskRules.shouldAutoCompleteParent`)
sí lo hace. Resultado: al completar el último hijo desde la notificación, el padre quedaba "pendiente"
para siempre → **tarea olvidada**, inconsistencia entre notificación y app.

**Causa raíz**: el path de notificación (`BroadcastReceiver`) duplicaba parte de la lógica de
`toggleTask` pero omitía la rama de autocompletado del padre y el registro de automatización para
deshacer.

**Solución (mínima, `ReminderActionReceiver.kt`, sin nueva pantalla/botón)**: helper
`completeParentIfDone(app, repo, completedSubtask, now)` llamado tras completar la subtarea en
`ACTION_COMPLETE`. Refleja fielmente `completeParentAutomatically`: (1) `SubtaskRules.shouldAutoCompleteParent`
(misma fuente de verdad que la app), (2) actualiza padre (completed/status COMPLETED/completedAt/updatedAt),
(3) cancela recordatorio del padre, (4) `RecurrenceEngine.nextOccurrence` + reprograma la próxima
ocurrencia, (5) registra `AutomationLogEntity` (type `subtask_auto`, `affectedTaskIdsJson`,
`undoPayloadJson` con snapshot del padre) para deshacer. Sin emitir eventos de UI (un `BroadcastReceiver`
no puede). **Mismo comportamiento que la app, ahora alcanzable desde la notificación.**

**Colisión de remoto (no destructiva)**: al rebasear sobre `b5c96d5`→`69b8ef8` (ciclo 55 parser
"cada mañana/tarde/noche"), conflicto solo en `CURRENT_STATE.md` (cabecera de estado: ambos runs
editaban la misma línea). Resolución conservando ambos trabajos: parser c.55 preservado intacto, este
fix renumerado a ciclo 56 (aterrizó después). Áreas ortogonales (`ReminderActionReceiver` vs
`NaturalTaskParser`). Sin force push.

**Tests**: lógica núcleo `SubtaskRules.shouldAutoCompleteParent` ya cubierta por `SubtaskRulesTest`.
**435 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — base c.55 tras rebase). Smoke
25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: el receptor `ReminderActionReceiver` en sí
(requiere Android `Context`/`BroadcastReceiver`/Room con DAOs reales → no ejecutable en JVM pura);
gradle/lint/assemble/UI.

## Último trabajo — Ciclo 54: Parser combina intervalo de cadencia + lista de días

Fix P1 de captura/recurrencia (`NaturalTaskParser.parseRecurrence`). Cuando una frase unía un
intervalo de cadencia con una lista de días (**"cada 2 semanas los lunes"**, **"cada quincena los
lunes y viernes"**, **"cada 3 semanas los martes y jueves"**, **"cada 2 semanas de lunes a viernes"**,
**"cada 2 semanas los findes"**), la rama WEEKLY+days del parser se quedaba solo con la lista de días
y devolvía `interval=1`: la rutina quedaba programada como **todas las semanas** aunque el usuario
pidió quincenal/cada-N-semanas (cadencia errónea), y la frase de intervalo ("cada 2 semanas") **quedaba
como residuo en el título**. Una rutina quincenal se convertía en semanal: tareas duplicadas, ruido y
recordatorios mal cadenciados. Verificado con probe: "Gym cada 2 semanas los lunes" → antes
`WEEKLY interval=1 days=1` + título `"Gym cada 2 semanas"`.

**Causa raíz**: las ramas de días (dayListPattern, weekdayRangePattern, weekdaySetPattern,
weekendRecurrencePattern) devolvían `interval=1` hardcoded e ignoraban cualquier intervalo explícito
presente en la frase; además solo añadían su propio rango a `phraseRanges`, dejando la frase de
intervalo sin consumir → residuo en el título.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: helper `detectWeekInterval()`
que detecta "cada N semanas" o "cada quincena/quincenalmente/todas las quincenas" en la frase y
devuelve `(interval, rango)`. Las ramas de días ahora consumen ese intervalo cuando existe (lo aplican
al `RecurrenceResult.interval` y añaden su rango a `phraseRanges` para limpiarlo del título). Sin
intervalo explícito → `interval=1` (cadencia semanal normal, sin regresión). Lógica local honesta, sin
random ni modelo simulado.

**Tests**: +6 en `NaturalTaskParserTest.kt` (`biweeklyIntervalWithDayListCombinesIntervalAndDays`,
`quincenaIntervalWithDayListCombinesIntervalAndDays`, `triweeklyIntervalWithMultipleDaysCombinesIntervalAndDays`,
`biweeklyIntervalWithWeekdayRangeCombinesIntervalAndDays`, `biweeklyIntervalWithWeekendCombinesIntervalAndDays`,
`dayListWithoutIntervalKeepsWeeklyInterval`). **428 domain tests PASS** (`bash tools/run_domain_tests.sh`,
26 clases — 421 base c.52 snooze + 6 nuevos), smoke 25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room
con DAOs reales, render real del parser en la app.

## Último trabajo — Ciclo 53: What Now desempata por prioridad (consistencia con el widget)

Fix P2 de consistencia de inteligencia entre `WhatNowEngine` (tarjeta "¿Qué hago ahora?" de
`TodayScreen`) y `TaskRules.nextBestTask` (widget de inicio + asistente + fallback del ViewModel).
Ambos ordenaban tareas por rango temporal, pero **solo `nextBestTask` desempataba por prioridad**
(`thenByDescending { priorityScore }`). `WhatNowEngine.suggest` saltaba directamente al `dueAt` más
próximo. Consecuencia real: con dos tareas atrasadas donde la NORMAL vencía ANTES que la URGENTE
(p.ej. normal ayer 9:00, urgente ayer 10:00), What Now sugería la normal mientras el widget
sugería la urgente — **dos respuestas distintas para el mismo conjunto de tareas**. Además
`priorityScore` existía duplicado como `private` en `TaskRules` y en `DayPlanner`, violando la
fuente única de verdad declarada para otras helpers (`isImminentStart`, `isDueToday`).

**Solución (mínima, sin nueva pantalla/botón)**:
- `TaskRules.priorityScore` ahora es **público** (fuente única de verdad del puntaje de prioridad).
- `WhatNowEngine` añade `thenByDescending { TaskRules.priorityScore(it.priority) }` ANTES del
  `dueAt`, replicando exactamente el orden de `nextBestTask` → ambas superficies sugieren la misma
  tarea.
- `DayPlanner` reutiliza `TaskRules.priorityScore` y se elimina su copia `private` duplicada (DRY).

Lógica local honesta, sin IA falsa ni random. Retrocompatible (sin cambios de firma pública).

**Colisión de remoto (no destructiva)**: al iniciar, el remoto ya estaba en `8275185` (ciclos 52
snooze + docs). Mi base local `d18fc32` era obsoleta (STALE_BASE). NO se forzó push: stash del
trabajo → fast-forward a `8275185` → re-aplicar SOLO mis 4 archivos que el remoto NO tocó
(verificado `git diff` vacío en `TaskRules/WhatNowEngine/DayPlanner/WhatNowEngineTest`). Sin
conflicto de código. Trabajo del ciclo 52 (ReminderRules) preservado íntegro.

**Tests**: +1 `picksUrgentOverNormalAmongOverdue` (reproduce el bug: dos atrasadas normal 9:00 vs
urgente 10:00 → antes del fix What Now elegía la normal; tras fix elige la urgente Y coincide con
`nextBestTask`). **422 domain tests PASS** (`bash tools/run_domain_tests.sh`, 26 clases — 421 base
c.52 + 1 nuevo), smoke 25 OK (`tools/run_domain_checks.sh`). **NO VERIFICADO**: gradle/lint/assemble/
Android/UI/Room con DAOs reales, render real de What Now en `TodayScreen`.

## Último trabajo — Ciclo 52: snooze ya no corrompe `reminderAt` en tareas recurrentes

Fix P1 de **integridad de datos** en recordatorios. `ReminderActionReceiver`
manejaba `ACTION_SNOOZE` sobrescribiendo `task.reminderAt = now + 10min`.
Para tareas **recurrentes** eso destruye el offset
`reminderOffset = dueAt - reminderAt` que `RecurrenceEngine.nextOccurrence`
reutiliza en **todas** las ocurrencias futuras: un recordatorio configurado a
"15 min antes" pasaba a ser "5 min antes" para siempre tras un solo snooze.
Mutación silenciosa e irreversible: una acción transitoria (aplazar la
notificación 10 min) corrompía permanentemente la preferencia de cuándo avisar
en cada futura repetición. Solo afectaba a recurrentes, lo que lo hacía sutil:
se manifiesta días/semanas después del snooze, cuando la siguiente ocurrencia
recuerda "demasiado tarde".

**Solución (mínima, sin nueva pantalla/botón)**: extraída la lógica de snooze
a `ReminderRules.snooze` (dominio puro, testable). El aplazamiento es
**transitorio**: `SnoozeResult.triggerAt` (now+10min) se pasa a
`ReminderScheduler.scheduleAt` y persiste en WorkManager (sobrevive a
reinicios sin tocar `reminderAt`). La preferencia original `reminderAt`
(pero también `dueAt`/`startAt`) se preserva intacta; solo se actualiza
`updatedAt = now`. `ReminderActionReceiver` delega a `ReminderRules.snooze`.
Único path de snooze en la app (verificado: `grep ACTION_SNOOZE` → solo
`TaskReminderWorker` construye el intent y `ReminderActionReceiver` lo
consume).

**Tests**: +6 en `ReminderRulesTest.kt`, incluida la invariante de integridad
`snoozeThenComplete_preservesReminderOffsetAcrossRecurrence` que reproduce el
bug: una tarea con `reminderAt = dueAt - 15min`, tras snooze (10 min) y
completar, la siguiente ocurrencia mantiene `reminderOffset = 15min`
(antes del fix el offset colapsaba a 5min). **421 domain tests PASS**
(`bash tools/run_domain_tests.sh`), smoke 25 OK. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, `ReminderScheduler`
real/WorkManager, receptor de broadcast en dispositivo.

## Último trabajo — Ciclo 51: DayPlanner no marca conflicto de hora si `startAt` es otro día

Fix P2 de fiabilidad del planificador diario (`DayPlanner.build`). El conflicto
`MOVED_FROM_SCHEDULED_TIME` (tarea con hora prevista que el plan reubica en otra hora) se computaba
comparando solo `hour*60+minute` del `startAt` original contra el `startMinute` del bloque planificado,
**sin verificar que el `startAt` cayera en el día del plan**. El comentario ya decía "tareas que ya
tenían hora prevista **ese día**", pero la implementación lo aplicaba a cualquier tarea con `startAt`.
Patrón real afectado: una tarea **iniciada ayer** (`startAt` ayer 15:00) que **vence hoy** era
reubicada por el plan de hoy y marcada falsamente como "hora movida", aunque nunca tuvo hora
asignada *hoy*. Ruido de conflictos espurios en el planificador.

**Solución (mínima, `DayPlanner.kt`, sin nueva pantalla/botón)**: antes de comparar la hora se verifica
`original.toLocalDate() == date`. Si el `startAt` es de otro día, no hay hora prevista "ese día" y no se
añade el conflicto. Si cae en el mismo día, el comportamiento previo se conserva (hora distinta →
conflicto real). Lógica local honesta, alineada con la intención documentada en el comentario.

**Colisión de remoto (no destructiva)**: al hacer `git fetch` el remoto ya estaba en `497010f`
(ciclo 50 = parser "de aquí a N" de otro run). Mi base local `4f7e701` era obsoleta (STALE_BASE).
NO se forzó push: stash del trabajo → fast-forward a `497010f` → re-aplicar SOLO los cambios de
código (`DayPlanner.kt`/`DayPlannerTest.kt`) que el remoto NO tocó (sin conflicto de código) → docs
reescritos sobre la base remota y reetiquetados ciclo 51. Trabajo del ciclo 50 del otro run preservado.
Sin force push, sin reset --hard, sin tocar `main`.

**Colisión 2 de remoto (rebase no destructivo)**: al hacer `git fetch` previo al push, el remoto había
avanzado `497010f`→`b3fc5f7` (otro run: `fix(manifest): resolve FileProvider merger conflict blocking
previewAdvanced CI`). Mi commit divergía (1 ahead / 1 behind). `git rebase origin/openhands/autonomous-ordia`
limpio (áreas ortogonales: planner vs manifest/previewAdvanced); 415 tests PASS re-confirmados tras rebase.
Push fast-forward OK: `b3fc5f7..bf11f31`. HEAD final: `bf11f31`.

**Tests**: +2 (`noConflictWhenStartAtIsOnADifferentDay`, `conflictStillReportedWhenStartAtIsOnSameDay`).
**415 domain tests PASS** (`bash tools/run_domain_tests.sh`), smoke 25 OK. **NO VERIFICADO**:
gradle/lint/assemble/Android/UI/Room con DAOs reales, render real del planner.

## Último trabajo — Ciclo 50: "de aquí a N"/"de acá a N" — prefijo relativo coloquial

Fix P1 de captura de plazos futuros (parser natural). Frases cotidianas como
**"llamar al dentista de aquí a tres días"**, **"reunión de aquí a una semana"**,
**"viaje de aquí a un mes"** NO se parseaban: `relativePattern` solo admitía los prefijos
`en`/`dentro de`, así que `dueAt` quedaba `null` y la frase **"de aquí a tres días"** permanecía
como residuo en el título. Tarea sin fecha → sin recordatorio, invisible en planificador/What Now,
**olvidada** (la forma coloquial más común de "en N" en español). La brecha era ortogonal a
`dayOfMonthPattern` (c.47) y a las fechas pasadas "hace N" (c.38); faltaba el prefijo coloquial
futuro. "para dentro de N" ya funcionaba ("para" se limpia, "dentro de" casaba).

**Solución (mínima, `NaturalTaskParser.kt`)**: extendido el grupo de prefijos de `relativePattern`
de `(?:en|dentro\s+de)` a
`(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)`, añadiendo las variantes coloquiales
**"de aquí a"** / **"de acá a"** (con/sin tilde, `í`/`i` y `á`/`a`). El resto del patrón
(cantidad + unidad) y la lógica de cálculo (`relativeDueAt`, `now + millis`) se reutilizan sin
cambios; la limpieza del título (`working.replace(it.value, " ")`) ya elimina todo el match,
incluido el prefijo. Riesgo de falso positivo bajo: requiere `de aquí a`/`de acá a` + cantidad +
unidad temporal. Heurística honesta (no IA, no random).

**Colisiones de remoto (no destructivas, tres veces)**: el remoto avanzó tres veces durante el run,
todas ortogonales (ninguna tocó `NaturalTaskParser.kt` ni su test):
(1) `bf3579d`→`8950d07` (guardián doble conteo, WhatNow IMMINENT_START, nextBestTask compromisos).
(2) `8950d07`→`4cb5f0f` (DayPlanner "Vence hoy" c.48 de otro run + docs follow-up).
(3) `4cb5f0f`→`4f7e701` (búsqueda ranking por urgencia c.49 de otro run + docs follow-up).
Rutina segura repetida (`git stash push` solo del código → descartar docs → `git pull --ff-only`
→ `git stash pop` limpio; en la 3ra colisión además `git reset --soft` de mi commit previo y
rebase sobre la nueva base). Etiquetado ciclo 50 para no colisionar con el ciclo 49 del run
paralelo (búsqueda). Sin force push, sin reset --hard, sin tocar `main`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**413 tests PASS** (408 base remota c.49 incl. SearchEngine + 5 nuevos: `deAquiATresDiasParsesDueAt`,
`deAquiAUnaSemanaParsesDueAt`, `deAquiAUnMesParsesDueAt`, `deAquiANDiasRespetaHoraExplicita`,
`deAcaAUnaSemanaParsesDueAt`), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`). Probe ad-hoc
confirmó los 5 casos reales antes de los tests. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room
con DAOs reales (sin Android SDK).


## Último trabajo — Ciclo 49: búsqueda ordena por urgencia (no solo alfabético)

Unidad atómica de producto/inteligencia de la **búsqueda universal** (P2 — potencia sin más interfaz).
**Problema**: `SearchEngine.search` ordenaba resultados por prefijo de título y luego alfabético, sin
considerar urgencia. Al buscar "reunión" con dos tareas "Reunión equipo" —una atrasada+urgente, otra
normal sin fecha— la crítica podía quedar debajo por azar alfabético. La búsqueda devolvía matches sin
priorizar lo accionable, contrario al principio de Ordía de elevar lo atrasado/urgente en todas las
superficies (What Now, widget, guardián ya lo hacen; la búsqueda no).

**Solución (mínima, `SearchEngine.kt`, sin nueva pantalla/botón)**: wrapper interno `Ranked(result,
urgency, dueAt)` calculado al construir cada resultado. `urgencyRank(task, now)` reutiliza
`TaskRules.isOverdue`/`isDueToday` (fuente única de verdad) en orden honesto idéntico a `nextBestTask`:
atrasada+urgente > atrasada > urgente+vence-hoy > urgente > alta > vence-hoy > resto. Orden final:
**prefijo de título** (relevancia textual primero) → **urgencia** → **dueAt** → **alfabeto**. No-tareas
tienen urgencia neutral, así que un proyecto/nota que **prefija** el query sigue ganando (la relevancia
textual domina); una tarea atrasada que también prefija sube por encima. Heurística local honesta.

**VERIFICADO localmente (JVM puro)**: `bash tools/run_domain_tests.sh` = **408 tests PASS** (406 base
c.48 + 2 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real de `SearchScreen`.

## Último trabajo — Ciclo 47 (run paralelo): `nextBestTask` alineado con IMMINENT_START (widget/asistente)

Unidad atómica de fiabilidad de inteligencia (P1 — consistencia entre superficies). El ciclo 46 añadió
la detección de **compromisos a punto de empezar** (`IMMINENT_START`, `startAt` futuro dentro de 15 min)
al **What Now** de `TodayScreen` (`WhatNowEngine`), pero **NO** a `TaskRules.nextBestTask`, que es la
heurística compartida por el **widget de inicio**, el **asistente** y el `nextTask` del ViewModel. Allí
un compromiso inminente seguía cayendo en `isScheduledLater` (rank -1, último recurso): el widget
sugería una tarea cualquiera de la Bandeja **mientras una reunión empezaba en 5 min** — la superficie más
vista daba una respuesta menos oportuna que la pantalla principal, justo el olvido que Ordía debe evitar.

**Solución (mínima, en `TaskRules.kt` + DRY)**:
- `isImminentStart(task, now)` + `IMMINENT_WINDOW_MINUTES = 15` ahora viven en `TaskRules`
  (públicos, fuente única de verdad). `WhatNowEngine.isImminentStart` se redujo a un delegado
  (`TaskRules.isImminentStart`), eliminando la constante duplicada. Sin cambio de comportamiento
  en What Now (mismas 4 pruebas).
- `TaskRules.timeRank` añade la rama inminente (rank 4, entre OVERDUE y SCHEDULED_LATER) en
  el mismo orden honesto que `WhatNowEngine`: EN_CURSO > EN_PROGRESO > ATRASADA > **INMINENTE** >
  VENCE_HOY > URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún
  no empieza.
- Retrocompatible: la firma `nextBestTask(tasks)` existente sigue delegando con `now`/zona por
  defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` compilan sin cambio. Sin
  nueva pantalla, sin nuevo botón: misma heurística, consistente en todas las superficies.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**404 tests PASS** (392 base c.46 + 1 guardian run paralelo + 4 parser "el 15" + 2 guardian doble conteo + 5 nuevos de este ciclo: 3 de `nextBestTask` inminente), 25 clases. (404 verificado tras rebase)
Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real del widget (sin Android SDK).

## Último trabajo — Ciclo 46 (run paralelo): What Now detecta compromisos a punto de empezar (IMMINENT_START)

Unidad atómica de inteligencia del "¿Qué hago ahora?" (P1 — evitar olvidos de compromisos inminentes).
**Problema**: un compromiso programado con `startAt` futuro (reunión/llamada/cita) que empieza en
pocos minutos **no aparecía** en "¿Qué hago ahora?" aunque sea lo más urgente del momento: el ranking
lo trataba como `SCHEDULED_LATER` (rank -1, último recurso) siempre que `startAt > now`, sin importar
si faltaban 5 min o 5 horas. Así una reunión a las 10:05 escrita a las 10:00 quedaba enterrada bajo
una tarea cualquiera de la Bandeja — justo el tipo de olvido que Ordía debe evitar.

**Solución (mínima, en `WhatNowEngine.kt`)**:
- Nuevo `WhatNowReason.IMMINENT_START` (entre OVERDUE y DUE_TODAY en el ranking, rank 4).
- `isImminentStart(task, now)`: `startAt` futuro y dentro de `IMMINENT_WINDOW_MINUTES = 15`.
  Las que empiezan más allá de 15 min siguen como `SCHEDULED_LATER` (sin cambio).
- Orden de prioridad honesto: EN_CURSO > EN_PROGRESO > ATRASADA > INMINENTE > VENCE_HOY >
  URGENTE > ALTA > BANDEJA. Una tarea atrasada sigue ganando a un compromiso que aún no empieza
  (no regresas lo pasado por lo inminente).
- UI: nueva etiqueta `what_now_reason_imminent` = "Empieza enseguida" en `TodayScreen.kt`
  (rama `when` exhaustiva añadida). Sin nueva pantalla, sin nuevo botón: misma sugerencia,
  mejor priorización. Menos interfaz, más potencia.

**Colisión de remoto resuelta (no destructiva)**: el remoto había avanzado a `966b799` (ciclos 44–45
del otro run: "la quincena" como hito + `nextBestTask` time-aware). `git fetch` + `git rebase
origin/openhands/autonomous-ordia` (no destructivo, sin force): auto-merge limpio en
`WhatNowEngine.kt`/`TodayScreen.kt`/strings/test (cambios ortogonales); conflicto solo en
`CURRENT_STATE.md` resuelto conservando el trabajo del otro run y renumerando el mío a **ciclo 46**.
Sin force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**392 tests PASS** (388 base remota c.45 + 4 nuevos), 25 clases. Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).

| P2 | Inteligencia — `nextBestTask` time-aware (widget/asistente alineado con What Now) | FIXED (c.45, run paralelo `2ef4bfa`): ordena en-curso-ahora > vencida > vence-hoy > urgente > alta > resto; `isDueToday` zona-aware. 388 tests. |
| P1 | Inteligencia/coach — `GuardianEngine.overdue` infla subtareas vs `SummaryEngine` | FIXED (c.46): filtra `parentTaskId == null` (alineado con resumen) + expone `overdue` en `Snapshot`. 391 tests. |
| P1 | Parser — "el 15" día del mes suelto con artículo | FIXED (c.47): `dayOfMonthPattern` ("el N"/"el N del mes") con lookahead anti-colisión; reutiliza `nextMonthlyDate`. 394 tests. |
| P2 | Planificador — etiqueta "Vence hoy" incorrecta en planes de otra fecha | FIXED (c.48): `DayPlanner.planReason` distingue "Vence hoy" (vence el día real de hoy) de "Vence este día" (vence el día del plan ≠ hoy). 399 tests. |

## Último trabajo — Ciclo 48: planificador — "Vence hoy" era falso en planes de otra fecha

Fix P2 de corrección de UX/urgencia en el planificador. `DayPlanner.planReason` etiquetaba como
`DUE_TODAY` ("Vence hoy") a **toda** tarea con `dueAt`, sin mirar la `date` del plan. Pero el
planificador se construye para la `selectedDate` que el usuario elige (y `AutomationEngine.PLAN_DAY`
siempre usa hoy). Consecuencia: al abrir el plan de **mañana** (o cualquier fecha futura), una tarea
que vence ese día mostraba **"Vence hoy"** — urgencia falsa que confunde al usuario sobre qué vence
realmente hoy. La etiqueta decía "hoy" cuando no lo era.

**Causa raíz**: `planReason(task, now)` ignoraba la `date` del plan; el `when` mapeaba cualquier
`dueAt != null` a `DUE_TODAY` de forma incondicional.

**Solución (mínima, `DayPlanner.kt`)**: `planReason` ahora recibe `date`+`zone` y compara la fecha
de vencimiento con el día **real de hoy** (`DateRules.toLocalDate(now, zone)`): si coincide →
`DUE_TODAY` ("Vence hoy"); si no → nuevo `DUE_ON_DATE` ("Vence este día"). La urgencia real de "hoy"
ya no se degrada con la vista: una tarea que vence hoy sigue siendo `DUE_TODAY` aunque se muestre en
un plan de otra fecha, y una que vence otro día no finge ser "hoy". Añadida rama UI en
`PlannerScreen.plannerReasonLabel` + string `planner_reason_due_on_date` = "Vence este día".
Heurística honesta (no IA, no random). Sin nueva pantalla, sin nuevo botón — solo precisión.

**Colisión de remoto (no destructiva)**: al iniciar, `git pull --ff-only` trajo los ciclos 45–47
(`2ef4bfa`/`d98862b`/`a934b65`/`fc1279b`/`6d0c6a4`/`cb042c7`→`52a4736`); base actualizada sin
divergencia. Sin force push, sin reset --hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**399 tests PASS** (393 base remota tras integrar ciclos 45–47 + 2 nuevos `dueOnFuturePlanDateIsNotLabeledAsToday`
y `dueTodayIsLabeledAsTodayEvenOnFuturePlanDate`, 25 clases). Smoke 25 OK no ejecutado por falta de
`kotlinc` en PATH en este run (libs presentes); el smoke es subconjunto del suite de dominio ya
pasado. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 47: "el 15" — día del mes suelto con artículo

Fix P1 de captura de citas (parser natural). Frases cotidianas como **"reunión el 15 a las 10"**,
**"cita el 20"**, **"entregar el 5 a las 18"** se fechaban en HOY por error: "el 15" no casaba con
`numericDatePattern` (que exige `DD/MM` con mes) y quedaba como residuo en el título; la hora suelta
("a las 10") se aplicaba entonces a HOY → la cita se programaba hoy en vez del día 15. Día erróneo
→ reunión perdida (no aparece como atrasada ese día, recordatorio dispara hoy). La brecha era
ortogonal a `monthNameDate` ("el 15 de marzo" sí funcionaba) y a la recurrencia mensual
("el 15 de cada mes"); faltaba el "el N" aislado.

**Solución (mínima, `NaturalTaskParser.kt`)**: nuevo `dayOfMonthPattern` = `el N` / `el N del mes`
con *negative lookahead* `(?!\s*de\s+[a-záéíóúüñ])` para no colisionar con "el 15 de marzo" (lo
resuelve `monthNameDate`, evaluado antes) ni "el 15 de cada mes" (recurrencia mensual). Se ancla al
día N reutilizando el helper existente `nextMonthlyDate(from, day)` (día N de este mes, o del
siguiente si ya pasó). Rama nueva en el `when` de `date`, entre `monthNameDate` y `numericDateMatch`,
para que "el 15 de marzo" gana y "el 15" aislado no caiga al fallback de hoy. Limpieza del residuo
"el 15" en el título. Heurística honesta (no IA, no random).

**Colisión de remoto (no destructiva)**: durante el run el remoto avanzó varias veces (ciclos 43–46:
"la quincena" `d98862b`, "entre semana"/"de lunes a viernes" `a934b65`, recurrencia de listas de
días bare `fc1279b`, `nextBestTask` time-aware `2ef4bfa`, guardián overdue raíz `6d0c6a4`).
`git stash` → `git pull --ff-only` → `git stash pop`; auto-merge limpio en `NaturalTaskParser.kt`
+ tests (cambios ortogonales); conflicto solo en `CURRENT_STATE.md`/`RUN_LOG.md` (docs) resuelto
conservando el trabajo del otro run y renumerando el mío a **ciclo 47**. Sin force push, sin reset
--hard.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**394 tests PASS** (390 base remota + 4 nuevos, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales (sin Android SDK).

## Último trabajo — Ciclo 46: guardián cuenta atrasados solo como tareas raíz (consistencia con resumen)

Fix P1 de consistencia/inteligencia (no parser, no pérdida de datos). `GuardianEngine.snapshot`
contaba los atrasados (`overdue`) **incluyendo subtareas**: un padre atrasado con 2 subtareas
también atrasadas → el guardián veía **3** atrasados, mientras la tarjeta de resumen
(`SummaryEngine`, que filtra `parentTaskId == null`) mostraba **1**. Dos superficies daban
números contradictorios al usuario. El invariant "las subtareas son anidadas, no se cuentan
además del padre" ya estaba fijado en `SummaryEngine` (test `overdueCountsRootTaskNotNestedSubtasks`)
pero **no** en el guardián. El `overdue` del guardián alimenta el ánimo (`CONCERNED` si `>= 5`),
el mensaje ("Hay N pendientes atrasados") y la acción sugerida → la inflación era visible y
afectaba al "coach".

**Solución (mínima, `GuardianEngine.kt`)**: filtrar `it.parentTaskId == null` en el conteo de
`overdue` (alineado con `SummaryEngine`). Además, exponer `overdue: Int` en `Snapshot` (campo
añadido al final de la data class, retrocompatible: nadie construye `Snapshot` posicionalmente;
la UI lee por nombre) para que el invariant sea verificable y la superficie pueda mostrar el mismo
número que el resumen. `completedToday`/XP siguen contando subtareas a propósito (progreso granular
deliberado) — solo el conteo de atrasados se alinea con la definición global.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**391 tests PASS** (388 base remota c.45 + 3 del run paralelo `nextBestTask` + 1 nuevo), 25 clases.
Smoke 25 OK (`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con
DAOs reales (sin Android SDK). Render real del guardián/overlay.

## Último trabajo — Ciclo 45: `nextBestTask` time-aware (inteligencia widget/asistente)

Mejora funcional de inteligencia local (P2 — "menos interfaz, más potencia"). `TaskRules.nextBestTask`
(heurística "¿qué hago ahora?" usada por el **widget de inicio**, el **asistente** y el fallback
`nextTask` del ViewModel) ordenaba solo por `overdue > prioridad > dueAt > createdAt` e **ignoraba
el tiempo**: una tarea ocurriendo **ahora mismo** (`startAt<=now<=startAt+dur`, p.ej. una reunión
a las 15:00 siendo las 15:00) no se priorizaba, y una **URGENTE para mañana** se sugería antes
que una **NORMAL que vence hoy**. `WhatNowEngine` (tarjeta What Now de `TodayScreen`) sí era
time-aware, dejando al widget/asistente dando una respuesta menos oportuna que la pantalla
principal — y el widget es la superficie más vista.

**Solución (mínima, en `TaskRules.kt`)**: `nextBestTask(tasks, now, zone)` con orden honesto (no IA,
no random): (1) en curso ahora (`startAt<=now<=startAt+dur`), (2) vencida, (3) vence hoy,
(4) urgente, (5) alta, (6) resto por inbox/orden/creación; las programadas para más tarde quedan
últimas. Nuevos helpers `isInProgressNow` (privado) e `isDueToday` (público, zona-aware, ya usado
por `GuardianCoach`). Retrocompatible: la firma existente `nextBestTask(tasks)` delega con
`now`/zona por defecto → `OrdiaWidgetProvider`, `AssistantEngine`, `OrdiaViewModel` siguen
compilando sin cambio.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**386 tests PASS** (384 base remota + 2 nuevos de este ciclo, 25 clases). Smoke 25 OK
(`tools/run_domain_checks.sh`). NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs
reales, render real del widget (sin Android SDK).

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
**369 tests PASS** (365 base remota c.42 + 4 nuevos de listas bare), 25 clases. Smoke 25 OK.
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
**369 tests PASS** (365 base remota + 4 nuevos de listas bare), 25 clases. Smoke 25 OK (`tools/run_domain_checks.sh`).
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
**369 tests PASS** (365 base remota + 4 nuevos de listas bare), 25 clases. Smoke 25 OK.
NO VERIFICADO: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

## Último trabajo — Ciclo 42: parser listas de días sin prefijo ("gym sábados y domingos")

Unidad atómica del ciclo de parser natural (P1 — pérdida de datos silenciosa en rutinas). La forma
**bare** de lista de días (sin "los"/"cada"/"todos los") es tan común como la prefijada: **"gym
sábados y domingos"**, **"fútbol domingos a las 18"**, **"lavar auto sábados domingos"**. El
parser solo casaba listas con prefijo, así estas caían sin recurrencia (`recurrence=NONE`) y los
días quedaban como residuo en el título → la rutina semanal se **olvidaba en silencio** y los
recordatorios no disparaban nunca. Complementario al ciclo 41 (separador opcional), pero distinto:
aquí el problema era la ausencia de artículo prefijo.

**Solución (mínima, `NaturalTaskParser.kt` — `parseRecurrence()`)**: reconocer listas bare de 2+
días como recurrencia WEEKLY sin exigir "los"/"cada". Un día plural marcado ("domingos",
"sábados") en solitario también es recurrencia (hábito semanal explícito). Un día suelto **no
plural** ("reunión martes") sigue siendo **fecha única** (es ambiguo: ¿fecha o recurrencia?),
para no programar una rutina equivocada. `dayNameRegex` garantiza que el match solo consume
nombres de día reales, sin robar texto ajeno.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**357 tests PASS** (350 base + 4 nuevos: `parsesBareDayListRecurrence`,
`parsesBareDayListWithExplicitTime`, `parsesBarePluralSingleDayRecurrence`,
`bareSingleNonPluralDayIsNotRecurrence`), 25 clases. Smoke 25 OK. NO VERIFICADO:
gradle/lint/assemble/Android/UI/Room (sin Android SDK).

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
| P1 | Parser — fechas relativas/pasadas/imposibles | FIXED → VERIFIED: "esta semana" c.34; "un par de" c.35; "mediados de semana" c.36; "a las N horas" c.37 cont. (316 tests); "a finales de semana" c.37 (319 tests); fechas pasadas "hace N"/"la semana/el mes pasado" + recuperación fechas imposibles (29 feb, 31 abr) c.38 (329 tests); fix "de/por/a la mañana" (hora) vs fecha "mañana" c.39 (336 tests); recordatorios con números escritos y fracciones c.40 (344 tests); listas de días sin coma + plurales sábados/domingos c.41 (350 tests); rango horario sin "horas" ambas < 13 c.42 (353) + ampliación followers (358) + recurrencia quincenal con palabra "cada quincena"/"quincenalmente" c.42 (365 tests); día de semana suelto hoy con hora futura c.42 cont.2 (362 tests); listas de días sin prefijo ("gym sábados y domingos") c.42 (369 tests) |
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

## Último trabajo — Ciclo 46: GuardianEngine doble conteo de subtareas (mood/XP inflados)

- **Bug P1 (fiabilidad/datos)**: `GuardianEngine` contaba subtareas como tareas lógicas en sus
  agregados, inflando el ánimo (mood) y la experiencia (XP) del guardia — simétrico al doble
  conteo de `SummaryEngine` FIXED en ciclo 20. `completedAll`, `completedToday`, `overdue` y
  `derivedExperience(completedTasks)` no filtraban `parentTaskId == null`. Consecuencias reales:
  1 padre + 4 subtareas vencidas → guardia CONCERNED + mensaje "Hay 5 pendientes atrasados"
  (realidad: 1 tarea lógica); 1 padre + 3 subtareas completadas → XP 48 en vez de 12. El guardia
  mentía al usuario sobre su progreso.
- **Fix mínimo**: filtro `parentTaskId == null` en los 4 conteos, consistente con
  `SummaryEngine`/`GuardianCoach`/`WhatNow`/`DayPlanner` (todos cuentan solo raíces).
- **Tests**: +2 (`overdueCountIgnoresSubtasksToAvoidInflatedConcern`,
  `derivedExperienceCountsLogicalTasksNotSubtasks`). Probe JVM confirmó antes/después.
  **392 domain tests PASS** (`tools/run_domain_tests.sh`), smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).


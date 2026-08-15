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
- **Fecha (UTC)**: 2026-08-15. Rama `openhands/autonomous-ordia`, HEAD `df55c52` (base c.265 remota; este run c.266 pendiente de commitear). Entorno JVM (sin Android SDK): kotlinc 2.1.20, jars en `/tmp/libs`, OpenJDK 21.
- **Tests**: `bash tools/run_domain_tests.sh` -> **1743 PASS** (sin regresión tras c.266; el helper `RecurrenceSpawn` es cableado Android, fuera del harness de dominio), 0 failures, 40 clases; `bash tools/run_domain_checks.sh` -> smoke 25 OK. **NO VERIFICADO** gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Recientes (rama)**:
  - c.266 (este run): **P1 fuente-única recurrencia+notificación perdía el desglose** - completar una recurrente CON subtareas desde el RECORDATORIO no clonaba el checklist (sólo la app lo hacía, c.223/c.236). RESUELTO: nuevo orquestador `RecurrenceSpawn.spawnNextOccurrence` (top-level en `com.ordia.app.reminders`) unifica los 4 caminos spawn (2 ViewModel + 2 ReminderActionReceiver) en una sola fuente: `nextOccurrence`→`add`→`schedule`→clonar subtareas→re-enlazar etiquetas. El path de la notificación GANA el clonado que antes perdía. Composición de reglas puras ya verificadas; orquestador Android = NO VERIFICADO JVM.
  - c.265 (`babdf9b`): **P1 datos-integridad límites mensuales con mes explícito** - "renta finales de mes de octubre" daba fecha WRONG (mes en curso, 31/8) en vez de 31/10; dejaba residuo. RESUELTO: los 3 patrones `*OfMonthPattern` capturan mes/año explícitos opcionales y `boundaryDueAt` resuelve con `parseMonthBoundaryName`. Adicional: `monthBoundaryNamePattern` ahora casa "final" singular y consume "al " (limpia "pago al final de agosto"->"pago"). Guard "cada fin de mes de \<mes>" no recurre (sinsentido). +11 tests.
  - c.261 (`090fe48`): agenda distingue proxima semana/semana que viene/semana pasada vs esta semana (`AssistantEngine.agendaAnswer`).
  - c.262 (`f257e08`): agenda reconoce "mes" y distingue proximo/que viene/pasado/este (bisiesto-safe via `YearMonth`).
  - c.263 (`24e1dd2`): "¿que tengo hoy?" con dia vacio pero atrasadas ya no miente "agenda vacia": nombra la atrasada mas urgente + recuento + `relatedTaskIds`.
  - c.264 (`b813e64`, run paralelo): **P1 datos-integridad des-hacer recurrente deja huerfano** - RESUELTO sin migracion Room: nueva regla `RecurrenceEngine.spawnedOccurrenceToRevert` re-deriva la ocurrencia esperada (no se almacena `originTaskId`) y la revierte solo si sigue pristina; cableado en `toggleTask`/`undoLastAutomation`. +9 tests.
  - Todos TDD (RED->GREEN), sin nueva pantalla/intento.
- **OPEN pendientes** (mayor impacto primero):
  - P2 "bisemanal" ambiguo (parser).
  - P3 residuos de título restantes ("el primer" suelto); P3 "a las 3.5" decimal-hour. (El residuo "de <mes>" de límites mensuales quedó RESUELTO c.265.)
  - P2 decision `TaskStatus.CANCELLED` inalcanzable desde UI (requiere Android/UI).
  - Verificacion Android pendiente del fix c.264 (toggle/un-complete + `deletePermanently` con Room real) cuando haya SDK.
  - Verificacion Android pendiente del fix c.266 (compilación del helper `RecurrenceSpawn` + los 4 caminos spawn con Room real) cuando haya SDK.
- **Proxima prioridad**: descubrimiento continuo - (i) seguir auditando motores no-parser (`WhatNowEngine`/`GuardianCoach`/`SummaryEngine`/`SearchEngine`) por rendijas simetricas; (ii) areas no-parser (onboarding, navegacion, accesibilidad, rendimiento, workers/backup con DAOs reales); (iii) auditar OTROS orquestadores Android con copias duplicadas que puedan divergir (patrón simétrico al de c.266). Re-fetch antes de implementar.

## Último trabajo — Ciclo 109: Parser — parte del día COMPACTA "hoy tarde"/"mañana noche"/"pasado mañana tarde" → agenda 09:00 + residuo en el título (P1 captura/agenda)

Bug **P1 (captura/agenda errónea + título sucio)**: la forma coloquial COMPACTA (sin conector)
**"hoy tarde"/"hoy noche"/"mañana tarde"/"mañana noche"/"pasado mañana tarde"** —abreviatura
de "hoy en la tarde"/"mañana por la noche", común al escribir rápido en móvil— NO se reconocía:
el marcador de día ("hoy"/"mañana") fijaba la fecha, pero la parte del día ("tarde"/"noche") NO
casaba ningún patrón → la hora caía al default **09:00** (una tarea "hoy noche" se vencía a las
09:00 de hoy, no 21:00) Y "tarde"/"noche" quedaba como residuo en el título ("comprar pan hoy
noche" → título "comprar pan hoy noche"). Asimetría flagrante: las formas CON conector
("hoy en la tarde", c.58) SÍ funcionaban (15:00/21:00 + título limpio); la compacta no.

Causa raíz: `standalonePartOfDayPattern` (c.58) exige conector (`a la`/`de la`/`por la`/`en la`);
la forma compacta "hoy tarde" no tiene conector, así que "tarde"/"noche" suelto tras un marcador
de día no se interpretaba como hora canónica ni se limpiaba del título.

Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia"):
nuevo `compactDayPartOfDayPattern = (?i)\b(?:antepasad[oa]\s+mañana|pasado\s+mañana|mañana|hoy)\s+(tarde|noche|madrugada)\b`
+ `compactDayPartOfDayTimes` (tarde→15:00, noche→21:00, madrugada→04:00). El marcador de día se
captura sólo para anclar la parte del día a una referencia temporal (evitar robar "tarde"/"noche"
sueltas de otras construcciones); la fecha la resuelve el `when` existente ("hoy"→hoy, "mañana"→+1,
"pasado mañana"→+2, "antepasado mañana"→+3). Cableado: (1) match + `compactDayPartOfDayTime` en la
cadena `parsedTime` (después de `standalonePartOfDayTime`); (2) `compactDayPartOfDayKey` añadido a
`hasPartOfDayPmContext` (tarde/noche → "hoy tarde a las 4"→16:00); (3) limpieza del título ANTES del
borrado genérico "mañana"/"hoy" (mismo orden que `standalonePartOfDayPattern`). **Se EXCLUYE "mañana"
como parte del día compacta** (sólo tarde/noche/madrugada): "mañana" es ambigua (día vs parte del
día) y la forma "hoy mañana"/"mañana mañana" es rara y propensa a fechar mal (choca con
`mananaAsDate`); la forma con conector ("hoy en la mañana") ya funciona. "madrugada" sí se incluye
(inequívoca). Lógica local honesta (canónica, sin random ni IA falsa). Retrocompatible.

Tests: +7 en `NaturalTaskParserTest.kt`: `hoyTardeEs15hYLimpiaTitulo`, `hoyNocheEs21hYLimpiaTitulo`,
`mananaTardeEsManana15hYLimpiaTitulo`, `mananaNocheEsManana21hYLimpiaTitulo`,
`pasadoMananaTardeEsPasadoManana15hYLimpiaTitulo`, `compactTardeConHoraSinMeridiemAplicaPm`
("hoy tarde a las 4"→16:00), `hoyMadrugadaEs4hYLimpiaTitulo`. Verificado sin regresión: "hoy en la
tarde"=15:00, "esta tarde"=15:00, "esta noche"=21:00, "hoy en la mañana"=09:00 intactos. Comando
`bash tools/run_domain_tests.sh` → **740 tests PASS** (733 c.108 + 7); smoke
(`bash tools/run_domain_checks.sh`) → **25 OK**. Probe JVM amplio (9 casos) verde, títulos limpios.

- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
  Render real del parser en la app no probado en dispositivo.
- **Próxima prioridad**: parser "ya" como token final → now (P1); luego salir del parser hacia
  recuperación de tareas olvidadas / contexto / onboarding.

## Último trabajo — Ciclo 111: Parser — REGRESIÓN de integración c.109+c.110: `compactDayPartOfDayPattern` aún `mañana` literal → "manana noche" agenda 09:00 + título sucio (P1 captura/datos)

Bug **P1 (regresión de integración, captura/agenda errónea + título sucio)**: el patrón
`compactDayPartOfDayPattern` —introducido en c.109 (remoto, "hoy tarde" compacta)— seguía usando
`mañana` **literal** (con tilde). La unificación de acentos de c.110 (`mañana`→`ma[nñ]ana`) recorrió
la rama `when` de fecha relativa, limpieza del título, "pasado/antepasado mañana", `hasStandaloneManana`
y "para mañana", pero **omitrió** `compactDayPartOfDayPattern`. Así **"comprar pan manana noche"**
(sin tilde, norma en escritura móvil rápida) → `due=mañana 09:00` (default) + residuo **"noche" en
el título**, en vez de `mañana 21:00` + título limpio. Agenda errónea (21:00→09:00, 12 h de
diferencia) Y título sucio = P1 de datos/captura. Asimetría flagrante: **"comprar pan mañana noche"**
(con tilde) SÍ funcionaba (21:00, título limpio); la sin tilde no.

**Causa raíz**: c.109 y c.110 se hicieron en ejecuciones separadas y la auditoría de c.110
no cubrió el patrón que c.109 acababa de añadir. El `compactDayPartOfDayPattern` contiene
`mañana` en tres de sus cuatro ramas (`antepasad[oa]\s+mañana`, `pasado\s+mañana`, `mañana` suelto)
y todas quedaron con tilde.

**Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón)**: las tres ramas pasan a
`ma[nñ]ana`, idéntico al resto de la unificación de c.110. Sin cableado nuevo (el group capturado
es `tarde|noche|madrugada`, sin tilde, ya maneado por `compactDayPartOfDayTimes`/`hasPartOfDayPmContext`).

**Tests**: +4 en `NaturalTaskParserTest.kt` (paridad sin tilde): `mananaSinTildeTardeEsManana15hYLimpiaTitulo`,
`mananaSinTildeNocheEsManana21hYLimpiaTitulo`, `pasadoMananaSinTildeNocheEsPasadoManana21hYLimpiaTitulo`,
`antepasadoMananaSinTildeMadrugadaEsDosDias4hYLimpiaTitulo`. Verificado con probe JVM paridad total
con/sin tilde ("mañana noche"/"manana noche"=21:00, "pasado manana noche"=+2 21:00,
"antepasado manana madrugada"=+3 04:00, todos título limpio). `bash tools/run_domain_tests.sh` →
**754 tests PASS** (750 c.110 + 4); smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin
regresión: formas con tilde intactas.

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

**Próxima prioridad**: parser "ya" como token final → now (P1); tolerancia a acentos en SearchEngine
("última"/"próxima"); salir del parser hacia recuperación de tareas olvidadas / contexto / onboarding.

## Último trabajo — Ciclo 110: Parser — "manana" (sin tilde) NO se reconocía como fecha relativa "mañana" → cita olvidada / agenda adelantada / asimetría medianoche (P1 datos móvil)

Bug **P1 (pérdida de datos móvil)**: **"manana" sin tilde** NO se reconocía como fecha relativa
"mañana" en la mayoría de patrones del parser — las regex usaban `mañana` literal (con tilde),
no `ma[nñ]ana`. En móvil la escritura sin tilde es la norma (teclado sin acentos rápidos).
Consecuencias: "llamar manana" → `dueAt=null` + residuo "manana" en el título → **cita olvidada**
(sin recordatorio, invisible en What Now/planificador); "pasado manana" → +1 en vez de +2 →
**cita adelantada un día**; "12 de la manana" → 12:00 (mediodía) en vez de 00:00 (medianoche),
asimetría con "12 de la mañana" (con tilde). El detector `hasStandaloneManana` usaba `mañana`
literal → "en la manana a las 4" contaba "manana" como fecha → la cita caía a MAÑANA con 04:00.
Asimetría flagrante con "próximo"/"sábados" sin tilde (c.88/c.89): la tolerancia a acentos estaba
incompleta para la palabra más usada del parser ("mañana").

**Causa raíz**: fecha relativa, limpieza del título, "pasado/antepasado mañana", `hasStandaloneManana`
y "para mañana" escribían `mañana` con tilde. El meridiem resuelto en `explicitTimeData` comparaba
cadenas crudas ("delamañana"/"delamanaana" — esta última con doble 'a', nunca casaba) sin normalizar ñ→n/í→i.

**Solución (mínima, sin nueva pantalla/botón)**: unificación `mañana`→`ma[nñ]ana` en TODOS los
patrones relevantes (rama `when` de fecha relativa, regex de limpieza del título, "pasado/antepasado
mañana" + limpieza, `hasStandaloneManana`, "para mañana"). Normalización `ñ→n`/`í→i` del meridiem
resuelto en `explicitTimeData` (`mer = meridiem.lowercase().replace("ñ","n").replace("í","i")`)
casa "de la manana" con "de la mañana" → `delamanana`/`delamediodia` unificados; elimina la asimetría
"12 de la manana"=00:00 y simplifica las comparaciones isPm/isAm (una sola forma). Fix P3 backlog
(BACKLOG-P3-PARSER-1) en el mismo ciclo: `standalonePartOfDayPattern` ampliado con rama
`de\s+(tarde|noche|madrugada)` (conector "de" suelto: "salir de madrugada"/"trabajar de noche"/
"jugar tenis de tarde"); NO aplica a "de mañana" (colisionaría con la fecha relativa "mañana").

**Tests**: `bash tools/run_domain_tests.sh` → **750 PASS** (740 c.109 remoto "hoy tarde" + 10 míos:
4 deMadrugada/deNoche/deTarde/deMadrugadaWithDate + 6 reconciliados "ahora"/"más tarde" auto-merged
del rebase). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**. Sin regresión: probe JVM verde
("reunión de mañana"=2026-08-15 09:00 fecha relativa preservada, "reunión de manana"=idem,
"reunión de tarde"=15:00 hoy, "salir de madrugada"=04:00 título limpio "salir", "salir de noche"=21:00,
"trabajar de noche"=21:00 título "trabajar", "pasado manana"=+2, "antepasado manana"=+3,
"12 de la manana"=00:00 simétrico a "12 de la mañana").

**NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).

**STALE_RUN**: rebase no destructivo sobre `a589560` remoto c.109 "hoy tarde" (otra ejecución paralela
modificó la rama durante mi trabajo). Código auto-merge limpio (mis acentos y su "hoy tarde" son
ortogonales); conflictos SOLO en docs resueltos conservando AMBOS lados y renumerando mi entrada
c.107→c.110 (el remoto ya usó c.107/108/109). Sin force push, sin reset --hard.

**Próxima prioridad**: descubrimiento continuo — tolerancia a acentos en otras palabras comunes
(auditar "última"→"ultima" en "última hora", "próxima"→"proxima" en "próxima semana" en SearchEngine);
"en cualquier momento"/"sin prisa"/"cuando puedas" (vago sin hora, ¿hoy?¿sin vencimiento con flag?);
recuperación de tareas olvidadas (What Now/Guardián), inbox inteligente, contexto, onboarding.

## Último trabajo — Ciclo 106: Parser — "enseguida"/"en seguida" (adverbio de inmediatez) NO casaba ningún patrón → dueAt=null + residuo en el título (P1 captura/agenda)

Bug **P1 (captura/agenda errónea + título sucio)**: los adverbios cotidianos de inmediatez
**"enseguida"** (una palabra) y **"en seguida"** (dos palabras) NO casaban ningún patrón del
parser → `dueAt=null` y la palabra quedaba como residuo en el título. La persona decía
"avisar enseguida" y Ordía creaba una tarea SIN vencimiento (sin recordatorio posible, invisible
en What Now/planificador) con título sucio "avisar enseguida". Asimetría: **"al rato"**,
**"un momento"** y **"pasado un rato"** SÍ eran +1h desde c.105 (otra ejecución expandió
`vagueRelativePattern`), pero **"enseguida"/"en seguida"** son adverbios puros de inmediatez
sin sustantivo de cantidad ("un rato"/"un momento") → la rama del patrón no los cubría.

Causa raíz: `vagueRelativePattern` (c.104/105) agrupaba (a) formas con prefijo
`en|dentro de|de aquí a|de acá a` + `un rato|un momento`, y (b) `al rato|pasado un rato`.
"enseguida"/"en seguida" no encajan en (a) (no llevan "un rato"/"un momento") ni en (b) (no son
"al rato"/"pasado un rato"). Sin patrón, caían al default sin fecha y el adverbio no se consumía.

Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón — "menos interfaz, más potencia"):
se extiende el `vagueRelativePattern` existente con la alternancia `|en\s*seguida|enseguida`,
reutilizando TODO el flujo ya implementado en c.104/105: match → `vagueRelativeDueAt = now + 1h`,
frase consumida (`.replace(it.value, " ")`) → título limpio. `en\s*seguida` cubre "en seguida"
(con espacio) y `enseguida` la forma compacta; el `\b` final ya existente asegura el límite.
No se añade un patrón separado (evita duplicación: el remoto b5e195a ya resolvió "al rato" en el
mismo patrón; esta ejecución añade SOLO enseguida/en seguida, el valor único no cubierto).

Tests: +2 en `NaturalTaskParserTest.kt`: `enseguidaEsFechaRelativaDe1Hora`,
`enSeguidaSeparadoEsFechaRelativaDe1Hora`. Verificado sin regresión: "al rato"/"un momento"/
"pasado un rato" (c.105) intactos, "en media hora"=+30, "en una hora"=+60 intactos. Comando
`bash tools/run_domain_tests.sh` → **720 tests PASS** (718 b5e195a + 2); smoke
(`bash tools/run_domain_checks.sh`) → **25 OK**.

- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
  Render real del parser en la app no probado en dispositivo.
- **STALE_RUN (colisión con ejecución paralela)**: la base local f55c056 estaba obsoleta — otra
  ejecución remota (b5e195a) resolvió el mismo problema de "al rato" expandiendo el MISMO
  `vagueRelativePattern`. Mi implementación local inicial había creado un patrón separado que
  duplicaba "al rato". Resolución segura: descarte del trabajo local obsoleto + fast-forward a
  b5e195a + re-implementación de SOLO "enseguida"/"en seguida" (el único valor no cubierto por el
  remoto), evitando duplicación de código. Sin force push, sin reset --hard.
- **Próxima prioridad**: continuar descubrimiento de frases cotidianas del parser y auditar
  producto (captura ultrarrápida, What Now, recuperación de vencidas, inbox inteligente).

## Último trabajo — Ciclo 114: SearchEngine — "sin fecha" no recuperaba tareas sin vencimiento (brecha de búsqueda universal / recuperación)

Bug **P2 (recuperación de tareas olvidadas / búsqueda universal)**: `"sin fecha"` /
`"sin vencimiento"` / `"sin día"` / `"sin plazo"` devolvían las tareas **sin vencimiento**
como resultado de una búsqueda de contenido puro — "sin" + "fecha" no aparecen en ningún
título útil — así que las tareas capturadas pero **nunca agendadas** (justo las que se
olvidan) no se podían recuperar con la búsqueda universal. `DateScope` solo tenía scopes
basados en rango de fecha, y `taskMatchesDateScope` hacía `task.dueAt ?: return false`, así
una tarea sin `dueAt` nunca casaba ningún scope.

| Entrada | Antes (bug) | Ahora |
|---|---|---|
| `sin fecha` (tareas: hoy, mañana, atrasada, undated id=5) | ruido/vacío | [id=5] ✅ |
| `sin vencimiento` | ruido/vacío | [id=5] ✅ |
| `sin dia` (sin tilde) | ruido/vacío | [id=5, id=21] ✅ |
| `sin azucar` (negación ajena) | contenido "sin azúcar" | [id=22] (búsqueda normal, NO scope) ✅ |
| tarea sin fecha pero completada | — | excluida de `sin fecha` ✅ |

- **Causa raíz**: ausencia de un scope para tareas sin `dueAt`; todas las intenciones de
  búsqueda por fecha requerían una fecha concreta que comparar.
- **Solución** (`SearchEngine.kt`, cambio mínimo, reutiliza TODO el flujo existente):
  nuevo `DateScope.UNDATED`; `UNDATED_HINTS = {fecha, vencimiento, dia, plazo}`;
  `detectDateScope` activa UNDATED cuando "sin" + una pista de fecha (primera rama, antes
  que cualquier otro token); `dateScopeTokens` elimina "sin" + la hint del contenido para
  no exigirlos en el título; `taskMatchesDateScope` UNDATED →
  `!completed && status != CANCELLED && dueAt == null`.
- **Heurística honesta**: se exige "sin" acompañado de un sustantivo de fecha para no
  activarse con "sin leche"/"sin azúcar". Se excluyen completadas (ya resueltas, no son
  "olvidadas") y canceladas; las archivadas ya se filtraron arriba. Sin nueva pantalla ni
  botón: aprovecha la búsqueda universal existente.
- **Tests**: +5 tests en `SearchEngineDateScopeTest.kt`
  (`sinFecha_returnsOnlyUndatedTasks`, `sinVencimiento_returnsOnlyUndatedTasks`,
  `sinFecha_excludesCompletedUndatedTasks`, `sinFechaSinAcento_tambiénFunciona`,
  `negacionAjena_noActivaScopeUndated`).
  Verificado sin regresión: "hoy"/"mañana"/"atrasadas"/"ayer"/"semana pasada"/"próxima
  semana" intactos. Comando: `bash tools/run_domain_tests.sh` → **768 tests PASS**
  (763 c.113 + 5). Smoke (`bash tools/run_domain_checks.sh`) → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`,
  `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: continuar descubrimiento de producto (captura ultrarrápida,
  What Now, detección de vencidas, inbox inteligente, relaciones notas/tareas) y auditar
  engines restantes (SummaryEngine, CommitmentEngine, DayPlanner).

Bug **P2 (precisión de captura/título sucio)**: `"en media hora y cuarto"` (30+15 = 45 min,
dos fracciones sumadas sin número entero) no se parseaba como punto relativo.
`fractionalRelativePattern` (c.94) casa solo "en media hora" (+30 min) y dejaba "y cuarto"
como residuo en el título; `compoundFractionalRelativePattern` (c.94b) exige número+"horas"
antes del "y", así que no casaba. Resultado:

| Entrada | Antes (bug) | Ahora |
|---|---|---|
| `cita en media hora y cuarto` | title="cita y cuarto", due=+30min | title="cita", due=+45min ✅ |
| `llamar en media hora y cuarto` | title="llamar y cuarto", due=+30min | title="llamar", due=+45min ✅ |
| `en un cuarto de hora y cuarto` | residuo, +15min | title limpio, +30min ✅ |
| `nos vemos de aquí a media hora y cuarto` | residuo, +30min | title limpio, +45min ✅ |

- **Causa raíz**: `fractionalRelativePattern` no contemplaba el sufijo "+ cuarto" sobre una
  fracción sin número entero; el compuesto sí lo hacía pero solo con coeficiente numérico.
- **Solución** (`NaturalTaskParser.kt`, cambio mínimo, reutiliza el flujo existente): nuevo
  `fractionalAndQuarterRelativePattern = prefijo (en|dentro de|de aquí a|de acá a) +
  (media hora|(un )?cuarto (de )?hora) + "y cuarto"` → `now + base + 15` min, donde base = 30
  si "media", 15 si "cuarto". Se procesa **ANTES** que `fractionalRelativePattern` para robar
  la frase completa (sin residuo en el título) y antes de la duración. Incluido en
  `effectiveRelativeDueAt` (prioridad sobre `fractionalRelativeDueAt`) y en la condición de
  exclusión de `relativeIsDays` (sub-hora: la hora explícita no la sobreescribe).
- **Heurística honesta**: el prefijo obligatorio preserva la duración real ("reunión media
  hora" sin prefijo sigue siendo `durationMinutes=30`, no-regresión c.94) y no choca con el
  recordatorio ("media hora antes" lo captura `reminderPatterns`). Forma poco común (la gente
  suele decir "tres cuartos de hora", ya cubierto), pero afecta precisión y limpieza de título.
- **Tests**: +4 tests en `NaturalTaskParserTest.kt`
  (`enMediaHoraYCuartoEsFechaRelativaDe45Min`, `enUnCuartoDeHoraYCuartoEsFechaRelativaDe30Min`,
  `dentroDeMediaHoraYCuartoEsFechaRelativaDe45Min`, `deAquiAMediaHoraYCuartoEsFechaRelativaDe45Min`).
  Verificado sin regresión: "en media hora"=+30, "en un cuarto de hora"=+15, "en una hora y
  cuarto"=+75, "en una hora y media"=+90, "en tres cuartos de hora"=+45 todos intactos.
  Comando: `bash tools/run_domain_tests.sh` → **697 tests PASS** (693 + 4). Smoke
  (`bash tools/run_domain_checks.sh`) → **25 OK**.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`,
  `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM).
- **Próxima prioridad**: continuar descubrimiento de frases cotidianas del parser y auditar
  producto (captura ultrarrápida, What Now, recuperación de vencidas, inbox inteligente).

---

## Ciclo 132 — 2026-08-14 (UTC) — fix(automation): `RESCHEDULE_OVERDUE` no descarta recordatorios (P1 evitar olvidos)

- **Problema**: `AutomationActionPlanner.RESCHEDULE_OVERDUE` reprogramaba tareas vencidas pero
  calculaba el recordatorio como `task.reminderAt?.let { due - 1h }`. Dos defectos:
  (1) una vencida **sin** `reminderAt` quedaba **sin recordatorio** tras la reprogramación →
  podía olvidarse de nuevo (contradice la misión "evitar olvidos"); (2) si tenía un offset
  distinto (p.ej. 2 h), se sobrescribía a 1 h, **corrompiendo la cadencia de ocurrencias
  recurrentes** que `RecurrenceEngine` reutiliza como offset para futuras ocurrencias.
- **Prioridad**: P1 (persistencia/recordatorios/recuperación de vencidas; misión "evitar olvidos").
- **Causa raíz**: el `?.let` descartaba el caso null (sin reminder) y siempre forzaba offset 1 h.
- **Solución (mínima, `AutomationActionPlanner.kt`, sin nueva pantalla/botón)**:
  - Si la tarea tenía reminder Y dueAt: **conserva el offset original del usuario**
    (`dueAt - reminderAt`) aplicado al nuevo vencimiento — protege la cadencia recurrente.
  - Si no tenía reminder: **añade uno 1 h antes del nuevo vencimiento** (siempre futuro),
    coherente con `PLAN_DAY`/`BATCH_QUICK_TASKS` (que añaden recordatorio por defecto cuando
    no existía).
- **Tests**: +2 tests en `AutomationActionPlannerTest.kt`
  (`reschedule_overdue conserva el offset de reminder del usuario`=offset 2 h preservado,
  `reschedule_overdue anade reminder cuando no existia`=reminder futuro añadido). Se reemplazó
  el test antiguo que asumía el offset forzado a 1 h.
  `bash tools/run_domain_tests.sh` → **932 PASS** (931 base c.130 del otro run + 2 nuevos - 1 reemplazado = +1 neto). `bash tools/run_domain_checks.sh` →
  smoke 25 OK.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK);
  integración `AutomationEngine.runRule`/`AutomationWorker` con DAOs/WorkManager reales.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/automation/AutomationActionPlanner.kt`,
  `app/src/test/java/com/ordia/app/automation/AutomationActionPlannerTest.kt`,
  `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 932 tests, 0 failures).
- **Próxima prioridad**: continuar auditoría de motores no-parser (SummaryEngine,
  GuardianCoach, UniversalCaptureEngine, ReminderRules, LearningEngine, RoutineRules,
  HabitRules) y descubrimiento continuo en producto (What Now, Guardián, contexto,
  onboarding, navegación, accesibilidad, rendimiento).


## Ciclo 131 (run 2) - 2026-08-14 (UTC) - feat(parser): ordinales de fecha "1ro/2do/3er/1º" + "día N de este mes" (P2 captura olvidada + integridad de título)

- **Run/ciclo**: 131 (rama `openhands/autonomous-ordia`). Base sincronizada: `git fetch` + `git pull --ff-only` limpio. HEAD inicial = `26f4be6` (c.130 "hora aproximada"). Sin divergencia.
- **Problema seleccionado (P2 → captura olvidada + integridad de título)**: dos gaps léxicos del parser confirmados ABIERTOS en BACKLOG (c.129 probe):
  1. **"pago el 1º de septiembre"** dejaba el título **mutilado** `pago º de septiembre` (fecha resuelta, contenido corrompido); **"pagar el 1ro de septiembre"/"renta el 2do de cada mes"** → `dueAt=null` (cita olvidada). Los sufijos ordinales ("1ro"/"2do"/"3er"/"5to"/"7mo"/"8vo"/"9no"/"10mo" y símbolos "1º"/"2ª") rompían los patrones de fecha (`\d{1,2}` exige dígito seguido de espacio).
  2. **"reunión el 15 de este mes"** → `dueAt=null`. Asimetría: "el 15" (día suelto) SÍ funcionaba.
- **Causa raíz**: (1) ningún paso normalizaba el sufijo ordinal a su dígito base → "1ro" dejaba "ro" como residuo; (2) `dayOfMonthPattern` no reconocía "de este mes"/"del mes".
- **Solución (mínima, `NaturalTaskParser.kt`, sin nueva pantalla/botón, sin lógica nueva)**:
  - `ordinalSuffixPattern = (?i)\b(\d{1,2})(?:ro|do|er|to|mo|vo|no|º|ª)(\s+del?\s+)` normaliza el ordinal a su dígito base **SOLO** cuando va seguido del conector de fecha " de "/" del " (contexto inequívoco), conservando el conector. "el 1ro de septiembre"→"el 1 de septiembre" reutiliza TODO el flujo existente. Se aplica tras `anoche` y antes de `lower`/`approximateTimePatterns`.
  - **Anti-falso-positivo clave**: el conector " de "/" del " requerido evita agendar contenido como "ver el 3er capítulo"/"comprar 2do piso"/"1ª edición". Descubierto DURANTE el run: la primera versión (normalización incondicional) agendaba "ver el 3er capítulo" como fecha espuria → endurecido al contexto de fecha.
  - `dayOfMonthPattern` añade `(?:\s+(?:del?\s+mes|de\s+este\s+mes))?` como calificador de mes actual, con `negative lookahead` que rechaza "de <mes-nombrado>"/"de cada mes"/"de este proyecto". "de este" restringido a "de este mes".
- **Tests**: `bash tools/run_domain_tests.sh` → **940 PASS** (931 c.130 + 9). `bash tools/run_domain_checks.sh` → smoke 25 OK. +9 tests (`ordinalNumericSuffixParsesAsDate`→09-01, `ordinalSymbolParsesAsDate`→09-01, `ordinalSuffixMonthlyRecurrence`→MONTHLY 08-02, `ordinalSuffixDelMes`→MONTHLY 08-05 10:00, `ordinalContentWordNotScheduled`/`ordinalContentPisoNotScheduled` guards, `dayOfMonthDeEsteMes`→08-15, `dayOfMonthDeEsteMesWithDiaWord`→08-15, `deEsteMesNotMatchingDeEsteProyecto` guard). Probe JVM 25 casos verde (fixes + guards: "3er capítulo"/"2do piso"/"1ª edición" intactos, "el 15 de este proyecto" intacto, recurrencias mensuales con ordinal correctas).
- **Features**: 0 (corrección de integridad de captura existente — más potencia sin nueva interfaz, sin fingir IA).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 940 tests, 0 failures).
- **Próxima prioridad**: gap P2 ABIERTO "día N" sin artículo ("pago día 15"→null, evaluar falso positivo); descubrimiento continuo en áreas no-parser (contexto, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales queda NO VERIFICADA.

## Ciclo 150 - 2026-08-14 (UTC) - fix(parser): "el N próximo" (orden inverso) → mes equivocado + residuo (P1)

- **Run/ciclo**: 150 (rama `openhands/autonomous-ordia`). HEAD inicial = `e4351ff`. Base sincronizada con remoto (pull --ff-only limpio, sin colisión).
- **Problema resuelto (P1)**: gap complementario al fix remoto `e4351ff` (forma directa "el próximo N"). La forma INVERSA "el N próximo" (calificador DESPUÉS del día) no estaba cubierta: "pago el 15 próximo" → antes `due=2026-08-15` (mes en curso, equivocado; debería 2026-09-15) + `title='pago próximo'` (residuo). Vencimiento agendado en mes equivocado + título degradado → recordatorio en fecha errónea, tarea invisible el día real.
- **Causa raíz**: `dayOfMonthPattern` (día suelto "el 15") capturaba "el 15" y lo anclaba al mes en curso antes de que ningún patrón viera "próximo" como sufijo. No existía patrón espejo de `nextMonthDayShortPattern` para el orden inverso.
- **Solución**: nuevo `nextMonthDayShortReversePattern` (regex espejo: `\bel\s+(\d{1,2})\s+pr[oó]ximo\b`) + resolución `nextMonthDayShortReverseDueAt` (día N del mes siguiente con clamp de día imposible, año nuevo si dic→ene) + wiring en `effectiveRelativeDueAt` y `relativeIsDays`. Paridad exacta con el fix remoto `e4351ff`, cambios ortogonales, sin nueva pantalla/botón, sin IA fingida.
- **Tests**: `bash tools/run_domain_tests.sh` → **1099 PASS** (1095 c.`e4351ff` + 4 nuevos), 0 failures. `bash tools/run_domain_checks.sh` → smoke 25 OK. +4 tests TDD: orden inverso (con/sin tilde), día bajo, no-regresión "el próximo lunes" (forma directa+weekday intacta). Probe JVM confirmó gap antes → verde después.
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del parser en la app no probado en dispositivo.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt`, `app/src/test/java/com/ordia/app/domain/NaturalTaskParserTest.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 1099 tests, 0 failures; smoke 25 OK).
- **Próxima prioridad**: gap "el día siguiente"/"el día de mañana" (= mañana, P2 ABIERTO, decisión de diseño pendiente vs "pasado mañana"); "de aquí al 15 del 9" (prefijo "de aquí al" no normalizado); áreas no-parser (contexto, What Now, onboarding, navegación, accesibilidad, rendimiento); auditoría workers/backup/restore con DAOs reales: paquete `backup/` ya verificado en JVM (c.147-B).

## Ciclo 158 - 2026-08-14 (UTC) - feat(intelligence): sugerencia de posposición libera la mayor capacidad (P2)

- **Run/ciclo**: 158 (rama `openhands/autonomous-ordia`). HEAD inicial = `f372c0e` (c.155 docs pusheado). Al pushear el control remoto había avanzado a `9e57297` (c.156 parser "y media" + c.157 parser "unos" de un run paralelo): rebase no destructivo sobre `9e57297`, conflictos sólo en docs de autonomía (código ortogonal: `SummaryEngine` vs `NaturalTaskParser`); entrada renumerada 156→158 para evitar colisión de numeración con el c.156 parser del run paralelo. Continúa la línea de mejora del `SummaryEngine`/What Now (c.154 veredicto con vencidas, c.155 mensaje honesto): ambos runs dejaron en verde el cálculo de saturación y el mensaje, pero la **decisión accionable** (qué tarea mover a mañana) seguía siendo mediocre.
- **Problema resuelto (P2 inteligencia/resumen, área "priorización inteligente"/"replanificación automática"/"What Now más útil")**: **la sugerencia de posposición (`mostDeferrableTask`) ignoraba cuánta capacidad libera cada tarea**. Entre dos tareas de la misma prioridad, el comparador elegía la de `dueAt`/`startAt` más tarde (más "margen de horario"), sin importar su duración. Así, un día saturado con una tarea grande de 120 min (LOW, vence 14:00) y una pequeña de 30 min (LOW, vence 17:00) sugería posponer la **pequeña** — la que libera MENOS capacidad — dejando al usuario con el bloque grande que sigue sin caber. La decisión útil al reprogramar es **liberar el máximo tiempo de la jornada**, no escoger "la que tiene más holgura de horario".
- **Causa raíz**: `mostDeferrableTask` usaba `compareBy { priorityDeferralWeight }.thenBy { dueAt }` — sólo prioridad y luego horario. La duración (`TaskRules.plannedDuration`, ya usada por `summarize` para `loadMinutes`/`remainingMinutesToday`) no entraba en el criterio de qué tarea mover.
- **Solución (mínima, sin nueva pantalla/botón, sin IA fingida, honesta)**: `mostDeferrableTask` añade `TaskRules.plannedDuration(it)` como **segundo criterio**: `compareBy { priorityDeferralWeight }.thenBy { plannedDuration }.thenBy { dueAt }`. Ascendente (`thenBy`, NO `thenByDescending`): con `maxWithOrNull`, ascendente hace que MAYOR duración sea "mayor" → se elige la de mayor duración (libera más capacidad). Solo aplica entre la MISMA prioridad (el peso de prioridad sigue siendo el primer criterio — no se pospone una URGENT antes que una LOW). Tercer criterio `dueAt` intacto (desempate por horario más tarde, preserva tests previos de "última" gana entre duraciones iguales). Heurística honesta: reutiliza `TaskRules.plannedDuration` (clamp 10–180 min), la misma fuente de verdad del veredicto de carga del c.154.
- **Bug de implementación detectado y corregido en este mismo run**: el primer intento usó `.thenByDescending { plannedDuration }`, que (por la semántica de `maxWithOrNull`) hace que MAYOR duración sea "menor" → elegía la duración MÁS PEQUEÑA — justo lo contrario. Confirmado empíricamente con un micro-test de `maxWithOrNull` aislado (idióneo porque `thenByDescending` invierte el orden del comparador). Corregido a `.thenBy { plannedDuration }`.
- **Tests**: +1 test TDD (RED→GREEN) en `SummaryEngineTest.kt`: `deferralSuggestion_atSamePriorityPicksTaskThatFreesMostCapacity` (dos tareas LOW: id 1 duración 120 min vence 14:00, id 2 duración 30 min vence 17:00, día OVERLOADED → antes sugería id 2 `expected:<1> but was:<2>`, ahora id 1). `bash tools/run_domain_tests.sh` → **1116 PASS** (1115 c.155 + 1), 0 failures. `bash tools/run_domain_checks.sh` → smoke 25 OK.
- **Features**: 0 (mejora de calidad de la decisión existente — más potencia sin más interfaz; alineada con "MENOS ES MÁS").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real de la sugerencia en la app no probado en dispositivo.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SummaryEngine.kt` (criterio de capacidad en `mostDeferrableTask`), `app/src/test/java/com/ordia/app/domain/SummaryEngineTest.kt` (+1 test), `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 1116 tests, 0 failures; smoke 25 OK).
- **Próxima prioridad**: descubrimiento continuo — oportunidades NO-parser (contexto, What Now, onboarding, navegación, accesibilidad, rendimiento, auditoría workers/backup con DAOs reales: `backup/` ya verificado en JVM c.147-B); gaps léxicos del parser ("pago mensual" requiere desambiguador, ABIERTO P2; "a las 3.5" ABIERTO P3). Re-fetch antes de implementar para evitar colisión con runs paralelos.

## Ciclo 155 - 2026-08-14 (UTC) - ux(What Now): mensaje honesto de saturación por vencidas (P2)

- **Run/ciclo**: 155 (rama `openhands/autonomous-ordia`). HEAD inicial = `2ab593f` (c.154 docs). Base sincronizada (pull --ff-only limpio, sin colisión).
- **Problema resuelto (P2 UX/inteligencia, What Now/replanificación honesta)**: incoherencia dominio→UI revelada por el c.154. Cuando el veredicto es OVERLOADED sin `deferralSuggestion` y hay vencidas, la UI mostraba "No cabe todo hoy. Elige qué dejar para mañana." — aconsejando posponer trabajo vencido, lo cual lo empeora. El motor `mostDeferrableTask` se niega a nombrar vencidas como posponibles, pero el string de respaldo las aconsejaba posponer. Contradictorio. Antes del c.154 este caso casi no aparecía (día con sólo vencidas era LIGHT "no hay trabajo"); el c.154 lo hizo visible (OVERLOADED por atrasos) y expuso el mensaje erróneo.
- **Causa raíz**: `TodayScreen` sólo anulaba el texto del veredicto cuando había `deferralSuggestion` concreta; el caso "OVERLOADED + suggestion null + overdue>0" caía al string genérico "dejar para mañana".
- **Solución (mínima, sin nueva pantalla/botón)**: nuevo string `summary_load_overloaded_overdue` ("No cabe todo hoy y hay %1$d vencida(s). Hazlas hoy o reprograma con intención, no las pospongas."). La rama de composición del veredicto en `TodayScreen` añade un caso: OVERLOADED + suggestion==null + overdue>0 usa el nuevo mensaje con conteo de vencidas, replanteando la decisión real (hacer hoy / reprogramar con intención) en vez de "dejar para mañana". El `clickable` (mover a mañana a un toque) sigue activo sólo con `suggestion` concreta y posponible, así el mensaje de atraso no invita a aplazamiento de un toque.
- **Tests**: sin cambios de dominio (sólo UI+strings). `bash tools/run_domain_tests.sh` → **1115 PASS**, 0 failures (no-regresión). `bash tools/run_domain_checks.sh` → smoke 25 OK. El comportamiento de dominio que respalda la rama (OVERLOADED + suggestion null + overdue>0) está cubierto por tests existentes: `deferralSuggestion_whenAllRemainingTasksAreOverdue_returnsNull` + tests de carga con vencidas (c.154).
- **Features**: 0 (mejora de honestidad del resumen existente — más claridad sin más interfaz).
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK). Render real del resumen en la app no probado en dispositivo.
- **Archivos modificados**: `app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt`, `app/src/main/res/values/strings_screens1.xml`, `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **Estado**: FIXED pendiente (dominio 1115 tests en verde, smoke 25 OK; UI NO VERIFICADA sin Android SDK).
- **Próxima prioridad**: descubrimiento continuo — oportunidades NO-parser (contexto, onboarding, navegación, accesibilidad, rendimiento, auditoría workers/backup con DAOs reales); gaps léxicos del parser ("pago mensual" requiere desambiguador, ABIERTO P2). Re-fetch antes de implementar.

## Ciclo 225 — 2026-08-15 (UTC) — fix(datos/recordatorios): archivar/restaurar un padre mueve su subárbol entero y cancela/rearma los recordatorios de las subtareas (P1)

- **Run/ciclo**: 225 (rama `openhands/autonomous-ordia`). HEAD inicial = `b790107` (c.224 duplicado de subtareas). Base sincronizada (`git fetch`, 0 ahead/0 behind, sin colisión). Entorno JVM: kotlinc 2.1.20, OpenJDK 21.
- **Problema resuelto (P1 datos/recordatorios/consistencia — familia c.223/c.224 sobre integridad del subárbol)**: `deleteTask` (archivar) y `restoreArchived` operaban sobre UNA sola fila, a diferencia de `deletePermanently` que SÍ mueve el subárbol entero (`dao.deleteSubtreeAndSelf`). Asimetría: borrar permanente = subárbol completo; archivar/restaurar = sólo el padre. **Síntoma real (recordatorios zombis)**: `deleteTask` cancelaba `reminderScheduler.cancel(task.id)` (sólo el padre); las subtareas conservaban su `reminderAt` y su alarma de WorkManager armada → el usuario "borraba" "Preparar charla" y **seguía recibiendo avisos** de "Slides"/"Ensayar" (subtareas invisibles: no son raíces —`rootTasks` filtra `parentTaskId==null`— y su padre archivado no se renderiza, pero su recordatorio sí dispara). + huérfanos ocultos (`archived=0` apuntando a padre `archived=1`) + restaurar incompleta (sólo rearma el padre).
- **Causa raíz**: `TaskRepository.archive(id)` → `dao.archive(id)` (1 fila), `restore(id)` → `dao.restore(id)` (1 fila), mientras `deletePermanently(id)` → `dao.deleteSubtreeAndSelf(id)` (subárbol vía `TaskTree.collectIds`). El "mover el subárbol junto al padre" (ORD-025) no se extendió a archivar/restaurar; y el ViewModel sólo tocaba el recordatorio del id único.
- **Solución (mínima, sin nueva pantalla/botón, sin IA fingida — reutiliza `TaskTree.collectIds`, ya testeado en JVM)**:
  1. **DAO** (`Daos.kt`): `archiveByIds(ids)`/`restoreByIds(ids)` (UPDATE masivo `id IN (:ids)`) + `@Transaction archiveSubtree(id)`/`restoreSubtree(id)` que reusan `TaskTree.collectIds(id) { getChildIds(it) }` — espejo de `deleteSubtreeAndSelf`. `getChildIds` no filtra `archived` → encuentra todas las descendientes.
  2. **Repositorio** (`Repositories.kt`): `archive`→`archiveSubtree`, `restore`→`restoreSubtree`; nuevo `subtreeIds(id)` (= `TaskTree.collectIds(id) { dao.getChildIds(it) }`). Para una HOJA, `collectIds`=`[id]` → sin cambio; sólo los padres en cascada.
  3. **ViewModel `deleteTask`**: `subtreeIds(task.id).forEach { reminderScheduler.cancel(it) }` antes de archivar → cancela alarmas de TODO el subárbol.
  4. **ViewModel `restoreArchived("task",id)`**: tras `restore(id)`, itera `subtreeIds(id)` y rearma el recordatorio de cada restaurada activa con `reminderAt`/`dueAt` (reusa el predicado existente; `schedule` se autoguarda si no hay fechas).
  - Semántica: archivar padre = archiva subárbol; restaurar = restaura subárbol. "El subárbol se mueve junto al padre" ahora vale para archivar/borrar-permanente/restaurar (las 3). Edge "subtarea archivada antes que el padre" → se mueve junto (idempotente al archivar; desarchiva al restaurar), coherente con `deletePermanently`.
- **Tests**: la capa PURA (`TaskTree.collectIds` — la decisión "qué ids son del subárbol") ya estaba cubierta por `TaskTreeTest` (ORD-025) y NO se modificó. La cascada nueva vive en DAO (Room `@Transaction`/`@Query`) + ViewModel (Android/WorkManager) → NO ejecutable en JVM. `bash tools/run_domain_tests.sh` → **1487 PASS**, 0 failures, 40 clases (sin regresión: `SubtaskRules`/`TaskTree` intactos); `bash tools/run_domain_checks.sh` → smoke 25 OK. Sin tests falsos ni assertions reducidas.
- **Features**: 0 (fix de integridad/consistencia — cierra la simetría archivar↔borrar-permanente↔restaurar sin interfaz nueva; "MENOS ES MÁS"/"datos (sagrados)").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); las nuevas consultas Room y el cableado `deleteTask`/`restoreArchived`↔`subtreeIds`↔`reminderScheduler` en runtime Android (la decisión pura `TaskTree.collectIds` SÍ verificada en JVM; el cableado espeja `deleteSubtreeAndSelf` existente).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/data/local/Daos.kt`, `app/src/main/java/com/ordia/app/data/repository/Repositories.kt`, `app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt`, `AI_AUTONOMY/{BACKLOG,CURRENT_STATE,RUN_LOG}.md`.
- **Estado**: FIXED pendiente (dominio JVM: 1487 tests, 0 failures; smoke 25 OK; cascada Room/Android → NO VERIFICADO en JVM, pendiente de Android SDK/gradle).
- **Próxima prioridad**: (i) verificar con Android SDK la cascada archive/restore + cancel/rearm de recordatorios en runtime; (ii) clonar etiquetas de subtareas en recurrencia+duplicado (c.223/c.224, P3); (iii) `TaskStatus.CANCELLED` inalcanzable desde UI (BACKLOG P2); (iv) seguir auditando áreas no-parser (workers/backup con DAOs reales, onboarding, navegación, accesibilidad, rendimiento). Re-fetch antes de implementar.

## Ciclo c.236 — 2026-08-15 (UTC) — feat(búsqueda): "lunes"/"viernes"/"sábado"… recupera las tareas de ese día de la semana (DateScope.WEEKDAY) — P2 "búsqueda universal"/"recuperación de tareas olvidadas"/"planificación"

- **Run/ciclo**: 236 (rama `openhands/autonomous-ordia`). HEAD inicial = `a05492d` (c.232 asistente "¿qué tengo mañana?"). Base sincronizada (`git pull --ff-only` limpio, sin colisión). Entorno JVM: kotlinc 2.1.20, OpenJDK 21.
- **Problema resuelto (P2 búsqueda universal/recuperación)**: la búsqueda universal NO recuperaba las tareas por **día de la semana** ("lunes", "viernes", "sábado"…). `SearchEngine` exponía scopes `hoy`/`mañana`/`ayer`/semanas/meses/partes-del-día/vencidas/sin-fecha/olvidadas, pero "lunes" caía a búsqueda de contenido (sólo aparecía una tarea si "lunes" estaba en su título). Asimetría: el usuario ya podía buscar "hoy"/"esta semana"/"ayer" y el parser de captura SÍ fechaba "el viernes"/"lunes que viene" (`nextWeekdayOrSame`/`nextWeekday`), pero la búsqueda no unía ambas. Un usuario que piensa "¿qué tengo el viernes?" debía abrir el planificador y contar días a mano. BACKLOG línea 159 ya marcaba scopes de fecha adicionales como "PARCIAL/OPEN". Área "búsqueda universal"/"recuperación de tareas olvidadas"/"planificación".
- **Causa raíz**: `DateScope` no tenía `WEEKDAY`; `detectDateScope` no reconocía tokens de día de la semana; `taskMatchesDateScope` no comparaba el día calendario de `dueAt` contra un weekday objetivo. Falta el equivalente a `nextWeekdayOrSame` (inclusivo) / `nextWeekday` (estricto) del parser.
- **Solución (mínima, sin nueva pantalla/botón, sin IA fingida — heurística determinista que reusa la semántica del parser de captura)**:
  1. `DateScope.WEEKDAY` en el enum.
  2. `WEEKDAY_TOKENS` (lunes..domingo, sin acento — `foldForSearch`: miércoles→miercoles, sábado→sabado) + `WEEKDAY_BY_TOKEN` (mapa token→`DayOfWeek`, ISO lun=1..dom=7).
  3. `WEEKDAY_NEXT_MODIFIERS` (próximo/proxima/viene/siguiente/posterior — coincide con `nextExplicit` del parser c.70) para el modo estricto.
  4. `detectDateScope`: rama `WEEKDAY_TOKENS.any → DateScope.WEEKDAY`, evaluada ANTES que las partes del día (así "viernes"/"sábado" se resuelven al día, no a la franja de hoy; "viernes tarde" → WEEKDAY viernes, consistente con "mañana tarde" → TOMORROW).
  5. `resolveWeekdayTarget(words, now, zone)`: extrae el primer token weekday, decide inclusivo (delta `(target-today+7)%7`, incluye hoy) vs estricto (delta==0→+7), usando `DateRules.toLocalDate`. **Semántica idéntica** a `NaturalTaskParser.nextWeekdayOrSame`/`nextWeekday` (c.42/c.70) → buscar y capturar significan lo mismo.
  6. `taskMatchesDateScope(WEEKDAY, weekdayTarget)`: compara el día calendario del `dueAt` —o del `completedAt` bajo anclaje "completadas lunes"— contra `weekdayTarget`. Excluye canceladas y, para lectura hacia adelante (día futuro/hoy), completadas (igual que TODAY/mañana). Devuelve false si `weekdayTarget==null` (defensa).
  7. `anchorMatchesScope(WEEKDAY)=false` (resuelto antes en `taskMatchesDateScope`); `scopeBand` ya cubre WEEKDAY vía `else → null`.
  8. `dateScopeTokens` añade `WEEKDAY_TOKENS` + `WEEKDAY_NEXT_MODIFIERS` para que no se exijan como contenido ("viernes reunion" filtra contenido dentro del viernes).
- **Tests**: `bash tools/run_domain_tests.sh` → **1541 PASS** (1532 base c.235 + 9 míos), 0 failures; `bash tools/run_domain_checks.sh` → smoke 25 OK. +9 tests TDD en `SearchEngineDateScopeTest.kt`: `lunes_onMonday_includesToday`, `viernes_midWeek_resolvesToNextFriday`, `proximoLunes_onMonday_jumpsToNextWeek`, `lunesQueViene_strictNextWeek` ("que" stop word), `viernesReunion_filtersByBothDateAndContent`, `lunes_pureDateScopeExcludesDatelessEntities`, `lunes_excludesCompletedTaskDueThatDay`, `completadasLunes_anchorsOnCompletedAt`, `sabado_resolvesCorrectlyWithAccent` (tilde).
- **Features**: 1 funcional (búsqueda por weekday — más recuperación sin más interfaz; alineado "MENOS ES MÁS"/"búsqueda universal").
- **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room con DAOs reales (sin Android SDK); integración `SearchScreen`↔`SearchEngine.search` en runtime Android (dominio puro verificado; el cableado UI ya invoca `SearchEngine.search` sin transformar el resultado — sin tocar UI).
- **Archivos modificados**: `app/src/main/java/com/ordia/app/domain/SearchEngine.kt`, `app/src/test/java/com/ordia/app/domain/SearchEngineDateScopeTest.kt`, `AI_AUTONOMY/{CURRENT_STATE,BACKLOG,RUN_LOG}.md`.
- **Estado**: FIXED → VERIFIED (dominio JVM: 1541 tests, 0 failures; smoke 25 OK).
- **Próxima prioridad**: descubrimiento continuo — seguir ampliando búsqueda/recuperación (p. ej. "este finde"/"fin de semana" como scope de búsqueda, simétrico al parser c.33 weekend); oportunidades no-parser (contexto, What Now, onboarding, navegación, accesibilidad, rendimiento, workers/backup con DAOs reales). Re-fetch antes de implementar.

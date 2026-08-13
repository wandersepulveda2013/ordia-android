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

- **Fecha (UTC)**: 2026-08-13 (ciclo 38)
- **Branch de trabajo**: `openhands/autonomous-ordia` (HEAD tras ciclo 38)
- **main**: contiene SOLO infraestructura de orquestación (workflows); no el rebuild de la app.
- **Workflows autónomos (en `main`)**: `ordia-autonomous-jules.yml` (cron `17 */2 * * *` + dispatch)
  y `ordia-autonomous-merge.yml` (pull_request_target + cron `*/15 * * * *` + dispatch).
- **Release workflow**: publica APK firmada en cada push a `openhands/autonomous-ordia` (incluso
  docs-only) → los commits de código generan releases automáticamente.

## Último trabajo — Ciclo 38: parser recordatorios con números escritos y fracciones

Unidad atómica del ciclo de parser natural (P2 — evitar olvidos por recordatorio perdido). **"recuérdame una/dos horas antes"**, **"treinta minutos antes"**, **"media hora antes"**, **"un cuarto de hora antes"**: formas cotidiansimas de pedir un recordatorio. El parser **NO las reconocía** como recordatorio: `reminderPatterns` solo capturaba `(\d{1,3})` (dígitos), así que `reminderOffsetMinutes=null` y la frase quedaba como **residuo en el título** (la cita se olvidaba). Asimetría total con la duración relativa: "en dos horas" sí funcionaba. Además **"media hora antes"** era **robado por el patrón de duración fraccionaria** (30 min falsos como duración) y el recordatorio quedaba en null: doble fallo (duración errada + recordatorio perdido).

**Solución (mínima, en `NaturalTaskParser.kt`)**:
- Nuevo `writtenAmountPattern` (dígitos o números escritos en español, simétrico al de fecha relativa) usado en los 2 patrones de recordatorio existentes, sustituyendo `(\d{1,3})`.
- 2 patrones nuevos de **fracción** ("media hora", "(un) cuarto de hora") con **contexto obligatorio** ("antes"/"de anticipación"/verbo) para que NO roben una duración real ("reunión media hora" sin "antes" sigue siendo duración de 30 min).
- Cálculo del offset vía `parseWrittenNumber` (ya existente); `media hora`=30 / `cuarto de hora`=15.
- `match.groupValues.getOrNull(2)` en vez de `[2]`: los patrones de fracción solo exponen grupo 1, los de cantidad+unidad exponen hasta el 2; acceso seguro evita `IndexOutOfBoundsException`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` = **323 tests PASS** (315 base + 8 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI/Room (sin Android SDK).

## Último trabajo — Ciclo 37: parser "a las N horas" (hora, no duración falsa)

Unidad atómica del ciclo de parser natural (P1 — corrección de bug que generaba datos
erróneos). **"a las N horas"** es la forma más natural de dar una hora en reloj de 24h
con sufijo "horas" ("reunión a las 9 horas", "clase a las 10 horas"). El parser **NO la
reconocía como hora**: el `timePattern` no consumía el sufijo "horas", así que "9 horas"
era **robado por `durationMatch`** como una duración falsa de **540 minutos** (9×60), y "a las"
quedaba como residuo en el título. Consecuencia: la tarea recibía una duración absurda y
**ninguna hora real** → recordatorio y planificación incorrectos. Bug doble porque, al añadir
la guardia para descartar "N horas" como duración, el filtro se aplicaba al ganador global tras
`minByOrNull`, descartando **TODOS** los matches de duración (incluido "durante 1h" válido)
cuando había algún "N horas" inválido presente. Además los conectores "durante"/"por" no
se limpiaban del título tras extraer la duración.

**Solución (mínima, en `NaturalTaskParser.kt`)**:
- `timePatterns[0]` añade grupo opcional `(?:\s*(horas?|hs))?` tras la hora (con o sin
  meridiem): consume el sufijo "horas"/"hs" sin alterar la lógica AM/PM (grupo propio, no
  meridiem). Así "a las 9 horas" se reconoce y borra completo como frase temporal.
- Guardia de duración refactorizada: en vez de filtrar el ganador global, se filtra
  **por-match** ANTES de `minByOrNull`, descartando solo los matches "N horas" precedidos por
  una frase temporal (`timePhrasePreceding`) y conservando válidos como "1h"/"2h".
- Limpieza de conector extendida: tras extraer la duración se borra también "durante"/"por"
  (además de "de") para no dejar residuo en el título.

Nota de colisión: al hacer push, el remoto había avanzado (otro run: ciclo 35 "un par de",
commit e4157c1). Mi commit local se rebaseó (no destructivo, rama propia) sobre e4157c1;
conflicto solo en CURRENT_STATE.md, resuelto conservando ambas secciones y renumerando mi
trabajo a ciclo 37 (el remoto ya usó ciclo 36 para "mediados de semana"). Mi entrada de RUN_LOG también se renumeró a ciclo 37.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**315 tests PASS** (304 base remota + 4 "un par de" + 4 "mediados de semana" + 3 nuevos míos = 315), 25 clases.
Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI (sin Android SDK).

## Último trabajo — Ciclo 35: parser "un par de" (coloquial = 2)

Unidad atómica del ciclo de parser natural (P1 — evitar olvidos, menos fricción de captura).
"un par de" es la forma coloquial más común de decir "2" de viva voz: "en un par de
días/semanas/meses". El `relativePattern` no reconocía esta construcción multi-palabra, así
que caía a `dueAt=null` → tarea **olvidada** (sin recordatorio, invisible en planificador/What
Now). Ahora `relativePattern` captura `un par de` como cantidad y `parseWrittenNumber` lo
resuelve a `2L`. Funciona con cualquier unidad y con hora explícita ("en un par de días a las 10").

VERIFICADO localmente (JVM puro, sin Android SDK): `bash tools/run_domain_tests.sh` =
**312 tests PASS** (308 + 4 nuevos), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/
assemble/Android/UI (sin Android SDK).

## Último trabajo — Ciclo 36: parser "mediados de semana" (= miércoles)

`NaturalTaskParser` no reconocía **"mediados de semana"** / "a mediados de semana"
(frase cotidiana: "lo termino a mediados de semana"). Caía a `dueAt=null` → tarea
olvidada (sin recordatorio, invisible en planificador/What Now). Añadido
`midOfWeekPattern` análogo a `startOfWeekPattern` (lunes) y `midOfMonthPattern`
(día 15): resuelve al **miércoles más cercano en HOY o futuro** (nextOrSame
WEDNESDAY), hora por defecto 9:00, combinable con hora explícita. Detectado y
borrado antes del período próximo para no colisionar con "semana que viene".
+4 tests. Sin colisión con "mediados de mes" (sigue siendo día 15).

## Último trabajo — Ciclo 35: parser "un par de" (= 2)

Unidad atómica del ciclo de parser natural (P1 â corrección de bug que generaba datos
erróneos). **"a las N horas"** es la forma más natural de dar una hora en reloj de 24h
con sufijo "horas" ("reunión a las 9 horas", "clase a las 10 horas"). El parser **NO la
reconocía como hora**: el `timePattern` no consumía el sufijo "horas", así que "9 horas"
era **robado por `durationMatch`** como una duración falsa de **540 minutos** (9hÃ60), y "a las"
quedaba como residuo en el título. Consecuencia: la tarea recibía una duración absurda y
**ninguna hora real** → recordatorio y planificación incorrectos. Bug doble porque, al añadir
la guardia para descartar "N horas" como duración, el filtro se aplicaba al ganador global tras
`minByOrNull`, descartando **TODOS** los matches de duración (incluido "durante 1h" válido)
cuando había algún "N horas" inválido presente. Además los conectores "durante"/"por" no
se limpiaban del título tras extraer la duración.

**Solución (mínima, en `NaturalTaskParser.kt`)**:
- `timePatterns[0]` añade grupo opcional `(?:\s*(horas?|hs))?` tras la hora (con o sin
  meridiem): consume el sufijo "horas"/"hs" sin alterar la lógica AM/PM (grupo propio, no
  meridiem). Así "a las 9 horas" se reconoce y borra completo como frase temporal.
- Guardia de duración refactorizada: en vez de filtrar el ganador global, se filtra
  **por-match** ANTES de `minByOrNull`, descartando solo los matches "N horas" precedidos por
  una frase temporal (`timePhrasePreceding`) y conservando válidos como "1h"/"2h".
- Limpieza de conector extendida: tras extraer la duración se borra también "durante"/"por"
  (además de "de") para no dejar residuo en el título.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**297 tests PASS** (304 base remota + 3 nuevos + 4 "un par de" = 311), 25 clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/
assemble/Android/UI (sin Android SDK).

## Último trabajo — Ciclo 33: parser "principios de mes", "fines de semana" recurrentes, días pasados

Unidad atómica del ciclo de parser natural (P1 — evitar olvidos de fechas, menos fricción de
captura). Tres capacidades nuevas del `NaturalTaskParser`, todas formas cotidianas en español que
antes caían a `dueAt=null` o fecha errónea:

1. **“principios de mes” (día 1)**: complemento de “fin/mediados de mes”. Pagos, rentas, cierres
   que vencen el 1. Patrón `startOfMonthPattern`; rueda al 1 del mes siguiente si hoy > 1 (si hoy
   es 1, vence hoy). Detectado y borrado ANTES del período próximo (evita colisión con “mes que
   viene”, como ya hacían fin/mediados).
2. **“fines de semana” (plural) como recurrencia WEEKLY sábado+domingo**: “cada fines de semana” /
   “los findes” expresa un hábito de fin de semana. Antes el plural no casaba con el patrón singular
   “fin de semana” (quedaba como residuo) ni generaba recurrencia. Ahora `RecurrenceFrequency.WEEKLY`
   con `days=[6,7]` (CSV “6,7”). El patrón consume un “cada”/“los” inicial opcional para limpiar el
   título. Singular “fin de semana” sigue siendo fecha única (próximo sábado): el plural = hábito.
3. **“el jueves pasado” / “el último lunes” / “el martes anterior” (fecha pasada)**: el usuario
   reconoce que la tarea quedó vencida. Antes “el jueves pasado” se leía como “jueves” (próximo)
   por `weekdayPattern` y “pasado” quedaba en el título → fecha FUTURA errónea + título sucio. Ahora
   se resuelve a la última ocurrencia PASADA (tarea vencida honesta, visible en What Now como
   atrasada). Función `previousWeekday()` (excluye hoy: si hoy es ese día, va al de la semana
   anterior). Dos patrones: orden natural (“jueves pasado”) e inverso (“último lunes”); detectados
   y borrados ANTES de `weekdayPattern`.

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**286 tests PASS** (275 base + 11 nuevos: 4 principios, 2 fines-de-semana, 5 días-pasados), 25
clases. Smoke 25 OK. NO VERIFICADO: gradle/lint/assemble/Android/UI (sin Android SDK).


## Último trabajo — Ciclo 32 (cont.4): adjuntos copiados a almacenamiento interno (P1 persistencia)

Cuarta unidad atómica del ciclo 32 (persistencia, P1 — datos sagrados / evitar pérdida de adjuntos):

**Causa raíz**: los adjuntos (captura, note editor, task detail) guardaban el **URI externo** en
`AttachmentEntity.uri` sin copiar el contenido. El acceso dependía de `takePersistableUriPermission`
(en `runCatching` silencioso). Si el permiso falla/caduca/revoca (limpieza del sistema, app reinstalada,
URI de `MediaStore` que cambia tras reboot), el adjunto queda **inaccesible** tras reinicio y
`startActivity(ACTION_VIEW)` falla con un toast misleading ("Ninguna app pudo abrir…"). Riesgo real
de pérdida percibida de datos.

**Solución (mínima, robusta)**: nuevo `AttachmentStorage` copia los bytes del URI fuente a
`filesDir/attachments/<uuid><ext>` y devuelve un URI `FileProvider` (`content://${applicationId}.attachments/...`).
- `addAttachment(ownerType, ownerId, sourceUri, displayName, mimeType, sizeBytes)` en `OrdiaViewModel`
  usa `attachmentStorage.import()` para copiar de inmediato; si la copia falla, conserva el URI original
  (no pierde el adjunto).
- `attachCaptureIfPresent` ahora importa el contenido en vez de guardar el URI crudo.
- `resolveAttachmentUri(uri)` resuelve el URI al abrir (los internos ya son válidos; los legacy externos
  pasan tal cual para no romper adjuntos antiguos).
- `deleteAttachment` borra también el archivo interno (sin dejar basura).
- `NoteEditorScreen`/`TaskDetailScreen` usan el nuevo `addAttachment` y `resolveAttachmentUri`; ya NO
  llaman `takePersistableUriPermission` (innecesario: el contenido vive en almacenamiento interno).
- Manifest: `FileProvider` con authority `${applicationId}.attachments` + `ordia_attachment_paths.xml`
  (`files-path name="attachments" path="attachments/"`).

**VERIFICADO localmente (JVM puro, sin Android SDK)**: `bash tools/run_domain_tests.sh` =
**275 tests PASS** (25 clases). Smoke 25 OK. **NO VERIFICADO**: gradle/lint/assemble/Android/UI/Room
con DAOs reales (sin Android SDK) — la copia de bytes, FileProvider y `ACTION_VIEW` requieren dispositivo.
CI remoto ejecuta `Verificar`.

Ciclos previos del 32: “próximos días” (+3d), “antier” (-2d), “próximo trimestre” (+90d),
“fin de mes”/“mediados de mes”, verificados.


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
| P1 | Persistencia — adjuntos URI externo | FIXED (NO VERIFICADO Android) ciclo 32 cont.4 |
| P1 | Parser — "esta semana" plazo blando | FIXED → VERIFIED ciclo 34 (290 tests); "principios de semana" VERIFIED ciclo 34 cont. (294 tests); quincena/bimestre/semestre VERIFIED (300 tests, 8146acf); "un par de" VERIFIED ciclo 35 (308 tests); "mediados de semana" VERIFIED ciclo 36 (312 tests); "a las N horas" VERIFIED ciclo 37 (315 tests); recordatorios escritos/fracciones VERIFIED ciclo 38 (323 tests) |
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
  ~~"mediados de semana" (miércoles)~~ HECHO ciclo 36 (312 tests). "a finales de semana" (viernes/dom) pendiente — evaluar ambigüedad viernes vs sábado vs domingo antes de implementar.
- P1 adjuntos: NEXT paso sería **migración de adjuntos legacy** (URIs externos antiguos ya
  guardados) — copiar contenido al abrir por primera vez si todavía accesible. Evaluar antes
  de implementar (riesgo: URIs ya inválidos). De momento `resolveAttachmentUri` no rompe legacy.
- P2/P3: derivedStateOf/keys en LazyColumns grandes; BackHandler en pantallas anidadas;
  contraste onSurfaceVariant. No detenerse.

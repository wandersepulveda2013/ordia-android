# BACKLOG — Ordía

> Inventario priorizado de mejoras y correcciones. Formato:
> `PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO`
> El agente mueve ítems a FIXED/VERIFIED con tests que lo demuestren.
> No añadir ideas vagas; solo problemas reales con evidencia.

## Pendientes (P0 / P1 / P2)

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P2 | UI | `Icons.Outlined.InsertDriveFile` deprecado; usar `Icons.AutoMirrored.Outlined.InsertDriveFile` | warning de compilación en `TaskDetailScreen` | OPEN |
| P2 | i18n | Revisar coherencia de cadenas nuevas (command_palette, feedback, floating_capture, android_access) | inspección manual pendiente | OPEN |
| P2 | QA | Verificar que las 6 variantes (Safe/Full/Advanced × debug/release) compilan tras cambios | `./gradlew test` | OPEN |
| P2 | Backup | Comprobar restauración con manifiesto corrupto (escenario adverso) | revisión de `RestoreData` | OPEN |
| P2 | Parser | `NaturalTaskParser` no reconoce números escritos en expresiones relativas: "en dos horas", "dentro de tres días" | probe JVM: `due=null` para "en dos horas" | OPEN |
| P3 | UX | Pulido visual de pantallas renovadas del workspace | capturas tras sesión | OPEN |

## Completados

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P1 | Privacy | Fragmentos de paquete sin punto (banca genérica) no se filtraban | `ContextPrivacyFilterTest` | FIXED |
| P1 | Capture | `StartActivityAndCollapseDeprecated` en tile de Quick Settings | lint | FIXED |
| P1 | UI | `stringResource` fuera del ámbito composable en `TaskDetailScreen` | lint | FIXED |
| P1 | Parser | Fecha numérica sin año en el pasado NO rodaba al año siguiente (inconsistente con fechas con nombre de mes): "Pagar factura 5/3" dicho el 29-jul devolvía 2026-03-05 (pasada) → recordatorio nunca dispara (ReminderSync filtra trigger<=now) | probe JVM reproducido; `numericDateWithoutYearInPastRollsToNextYear` | FIXED → VERIFIED (136 domain tests PASS en JVM/kotlinc) |
| P1 | Parser | "esta mañana/tarde/noche" no se reconocían; además "esta mañana" se interpretaba como "el día de mañana" (contiene "mañana") | probe JVM reproducido; `estaMananaIsNotMistakenForTomorrow` | FIXED → VERIFIED (136 domain tests PASS) |
| P1 | Parser | "urgente" como palabra inicial no se detectaba como prioridad sin prefijo !/# | probe JVM reproducido; `leadingUrgenteSetsUrgentPriority` | FIXED → VERIFIED (136 domain tests PASS) |
| P2 | Tests | `tools/domain-smoke/DomainSmoke.kt` obsoleto tras ampliar `SearchKind` a 7 valores: el smoke comparaba `SearchKind.entries.toSet()` (7 kinds) pero solo alimentaba 4 listas (tasks/proyectos/notas/hábitos) → `run_domain_checks.sh` fallaba con "Universal search failed" | `bash tools/run_domain_checks.sh` (reproducido); alineado con `SearchEngineTest` que usa el set correcto de 4 kinds core | FIXED → VERIFIED (smoke 25 assertions OK + 125 tests dominio OK con kotlinc/JUnit4 en JVM) |

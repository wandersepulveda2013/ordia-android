# BACKLOG — Ordía

> Inventario priorizado de mejoras y correcciones. Formato:
> `PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO`
> El agente mueve ítems a FIXED/VERIFIED con tests que lo demuestren.
> No añadir ideas vagas; solo problemas reales con evidencia.

## Pendientes (P0 / P1 / P2)

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P2 | UI | `Icons.Outlined.*` deprecados; usar versiones `AutoMirrored` | warnings de compilación | FIXED |
| P2 | i18n | Revisar coherencia de cadenas nuevas (command_palette, feedback, floating_capture, android_access) | inspección manual pendiente | OPEN |
| P2 | QA | Verificar que las 6 variantes (Safe/Full/Advanced × debug/release) compilan tras cambios | `./gradlew test` | OPEN |
| P2 | Backup | Comprobar restauración con manifiesto corrupto (escenario adverso) | revisión de `RestoreData` | OPEN |
| P3 | UX | Pulido visual de pantallas renovadas del workspace | capturas tras sesión | OPEN |

## Completados

| PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO |
|-----------|------|----------|----------|--------|
| P1 | Privacy | Fragmentos de paquete sin punto (banca genérica) no se filtraban | `ContextPrivacyFilterTest` | FIXED |
| P1 | Capture | `StartActivityAndCollapseDeprecated` en tile de Quick Settings | lint | FIXED |
| P1 | UI | `stringResource` fuera del ámbito composable en `TaskDetailScreen` | lint | FIXED |

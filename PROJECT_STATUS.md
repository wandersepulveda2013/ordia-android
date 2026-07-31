# Estado de Ordia

Fecha de corte: 31 de julio de 2026
Rama de trabajo: `feature/ordia-audit-critical-fixes` (base `8ecfa07`)
Referencia completa de hallazgos y correcciones: [`AUDITORIA_ORDIA_2026.md`](AUDITORIA_ORDIA_2026.md)

## Implementado en código fuente

| Área | Estado | Alcance |
|---|---|---|
| Inicio y adaptación | Implementado | Onboarding, modos Simple/Organizado/Avanzado, teléfono/tableta |
| Tareas | Implementado | CRUD, subtareas, prioridades, estados, etiquetas, repetición, archivo |
| Planificación | Implementado | Semana, mes, duración y plan automático local |
| Proyectos | Implementado | CRUD, progreso, tareas y notas relacionadas |
| Notas | Implementado | Bloques, plantillas, adjuntos, autosave |
| Hábitos y rutinas | Implementado | Registros, rachas, objetivos y pasos |
| Enfoque | Implementado | Temporizador, vínculo con tareas e historial |
| Búsqueda | Implementado | Tareas, proyectos, notas y hábitos; tolera tildes |
| Guardián | Implementado | Superposición, arrastre, modos y acciones rápidas |
| Recordatorios | Implementado | WorkManager, silencio, completar y posponer |
| Widget | Implementado | Próximo paso, pendientes y captura rápida |
| Copia y restauración | Implementado | JSON local versionado (v4 con checksum SHA-256 y respaldo preventivo) |
| Estadísticas y archivo | Implementado | Resumen de progreso, restauración y borrado definitivo |
| Privacidad | Implementado | Sin INTERNET, backup automático desactivado, permisos contextuales |
| Contexto | Implementado | IME y accesibilidad con guardas de privacidad (campos sensibles, apps bloqueadas), motor local con estado Unsupported honesto |

## Verificado en este entorno

- `./gradlew clean test lint assembleDebug assembleRelease`: **OK** en las 6 variantes (3 flavors × debug/release).
- **834 pruebas unitarias, 0 fallos** (139 × 6 variantes).
- Lint sin errores (solo warnings P3/P4 documentados en la auditoría).
- 6 APKs generados; release R8 de ~16 MB con `DEBUGGABLE=false`; job `sign` del CI valida con `aapt2 dump badging`.
- Wrapper de Gradle con `distributionSha256Sum` y validación en CI.
- Hallazgos críticos de la auditoría: **3/3 P0 corregidos, 7/8 P1 corregidos**; 2 bloqueos por CI documentados (ORD-015 migración Room, ORD-032 FTS) y 14 abiertos (P2/P3/P4) sin impacto en la línea base.

## Pendiente fuera de este entorno

- Pruebas instrumentadas en emulador (no hay ADB/dispositivo en este equipo).
- Pruebas de permisos, notificaciones y overlay en dispositivos físicos.
- Revisión de Google Play y firma final.
- ORD-015: test de migración Room 1→2 (requiere versionar `app/schemas/`).
- ORD-005: aplicar `ContextPrivacyFilter` a las fuentes accessibility/notificaciones.

El estado del proyecto es **beta interna** con la línea base de pruebas verde y los bloqueos documentados.

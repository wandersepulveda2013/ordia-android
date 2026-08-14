# Backlog de Evolución Autónoma
| ID | PRIORIDAD | ÁREA | PROBLEMA | EVIDENCIA | ESTADO | ACCIÓN | TEST |
|---|---|---|---|---|---|---|---|
| ORDIA-AGENT-0001 | P6 | Tech Debt | Deprecaciones de Compose | `Divider()` y `Icons.Outlined` direccionales obsoletos | FIXED | Reemplazado por `HorizontalDivider` y `Icons.AutoMirrored.Outlined` | N/A |
| ORDIA-AGENT-0002 | P3 | UX/Design | Base de colores no es blanco/negro/gris | `Theme.kt` usa tonos crema/papel | FIXED | Actualizado colores base manteniendo acentos | Compilar |
| ORDIA-AGENT-0003 | P3 | UI Components | Falta sistema de diseño centralizado (`OrdiaCard`, `OrdiaButton`, etc) | `AppComponents.kt` usa Material genérico | FIXED | Creados componentes base `Ordia*` | Tests/Preview |

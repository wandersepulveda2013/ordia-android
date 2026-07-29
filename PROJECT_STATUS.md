# Estado de Ordia 1.0

Fecha de corte: 29 de julio de 2026

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
| Copia y restauración | Implementado | JSON local versionado |
| Estadísticas y archivo | Implementado | Resumen de progreso, restauración y borrado definitivo |
| Privacidad | Implementado | Sin INTERNET, backup automático desactivado, permisos contextuales |

## Verificado en este entorno

- Estructura y codificación UTF-8.
- XML válido.
- Manifiesto y permisos esperados.
- Reglas puras de dominio mediante `kotlinc`.
- Ausencia de patrones conocidos de texto corrupto.
- Presencia de pruebas, migración, backup y CI.

## Pendiente fuera de este entorno

- Sincronización Gradle con Android SDK.
- Compilación real del APK/AAB.
- Lint Android real.
- Pruebas instrumentales en emulador.
- Pruebas de permisos, notificaciones y overlay en dispositivos físicos.
- Revisión de Google Play y firma final.

Por esa razón el estado correcto es **candidato de código fuente**, no binario de producción certificado.

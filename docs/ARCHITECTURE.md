# Arquitectura

## Capas

- `data/local`: entidades, DAOs, conversores y base Room.
- `data/repository`: acceso estable a datos y operaciones de persistencia.
- `data/preferences`: ajustes mediante DataStore.
- `domain`: reglas puras de tareas, recurrencia, hábitos, búsqueda, planificación y guardián.
- `ui`: estado agregado, ViewModel, navegación, pantallas y componentes Compose.
- `reminders`: WorkManager, notificaciones y acciones completar/posponer.
- `overlay`: servicio del guardián y captura rápida.
- `backup`: serialización y restauración JSON.
- `widget`: widget de pantalla de inicio.

## Flujo de estado

Los repositorios exponen `Flow`. `OrdiaViewModel` combina esos flujos en `OrdiaUiState`, ejecuta comandos y emite mensajes efímeros. Compose observa el estado con ciclo de vida y envía eventos al ViewModel.

## Persistencia

Room contiene tareas, proyectos, notas, hábitos, registros, sesiones, rutinas, etiquetas y adjuntos. La base está en versión 2 con migración 1→2. DataStore conserva apariencia, nivel de interfaz, guardián, horas de silencio y preferencias de planificación.

## Recordatorios

Cada tarea usa trabajo único por ID. Cambiarla reemplaza el trabajo anterior. El worker respeta horas de silencio, permisos y estado de la tarea. Las acciones de notificación pueden completar o posponer.

## Guardián

`GuardianOverlayService` solo se inicia tras autorización explícita de superposición. La posición se conserva localmente. El panel abre actividades propias; no usa servicio de accesibilidad ni captura la pantalla.

## Reglas de dominio

Las reglas importantes se mantienen fuera de Android cuando es posible para facilitar pruebas:

- `TaskRules`
- `NaturalTaskParser`
- `RecurrenceEngine`
- `HabitRules`
- `QuietHours`
- `SearchEngine`
- `DayPlanner`
- `GuardianCoach`

## Copias

`BackupManager` exporta un formato JSON versionado. La restauración valida el formato y repone todas las colecciones compatibles. La copia automática del sistema está desactivada para que la salida de datos sea explícita.

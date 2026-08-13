# Backlog de Evolución de Ordía

## P0 (Críticos)
- Actualmente no se encontraron errores críticos que corrompan datos o causen crashes directos, pero se debe monitorear el sistema de recordatorios y actualizaciones.

## P1 (Experiencia Degradada)
- [Mejorar] "What Now" actualmente solo sugiere la siguiente tarea basada en prioridad, fecha de vencimiento y estado, sin dar una razón explicativa o tener en cuenta la carga del día o el tiempo libre actual.
- [Mejorar] El Parser de Lenguaje Natural (`NaturalTaskParser`) está limitado a horas exactas y expresiones básicas de tiempo. Faltan expresiones coloquiales comunes ("esta noche", "al mediodía", "a primera hora", etc.).

## P2 (Mejoras Funcionales)
- [Mejorar] Rediseñar la pantalla principal (`TodayScreen`) para centrarla en "¿Qué debo hacer ahora?" dándole protagonismo al GuardianAvatar con un espaciado limpio y removiendo tarjetas/fondos pesados (Cards).
- [Mejorar] Rediseñar los hábitos y proyectos en `TodayScreen` para que sean más ligeros visualmente y consistentes con el diseño minimalista en blanco y negro (eliminar bordes/cards pesados en los `LazyRow`).

## P3 (Pulido y Deuda Técnica)
- [Deuda] Existen varios warnings de deprecación de Jetpack Compose reportados por Lint (e.g. `Icons.Outlined` a `Icons.AutoMirrored.Outlined`, `Divider` a `HorizontalDivider`).

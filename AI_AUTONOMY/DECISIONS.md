El usuario solicita una 'MEGA EVOLUCIÓN' de Ordía y da instrucciones específicas sobre diseño visual y funcionalidad.
El primer paso según las instrucciones (WAVE 1) es 'foundation + design system'.
La aplicación usa Jetpack Compose para las vistas. Hay un archivo Theme.kt, Type.kt y AppComponents.kt, que es donde el diseño base de Ordía (foundation / design system) debe establecerse.

El usuario indica explícitamente:
- A VECES MENOS ES MÁS.
- Base: blanco, negro, grises cuidadosamente elegidos.
- Añade paleta secundaria contenida para: prioridad, alerta, éxito, foco, calendario, automatizaciones, guardianes.
- Tipografía: menos texto innecesario.
- Espaciado: más aire.
- Formas: menos profundidad, evitar exceso de tarjetas dentro de tarjetas.
- Evitar: diseño de IA, degradados, componentes que parezcan plantillas.

Mejorar:
- OrdiaButton
- OrdiaInput
- OrdiaSheet
- OrdiaDialog
- OrdiaCard
- OrdiaTask
- OrdiaNote
- OrdiaAction
- OrdiaGuardian
- OrdiaTimeline
- OrdiaCommand
- OrdiaKeyboardBar
(Nombres orientativos, la idea es que la UI use componentes centralizados de diseño).

Voy a enfocarme en refactorizar 'Theme.kt' para que los colores base sean blancos, negros y grises con acentos específicos para semántica (alertas, prioridad). Y luego reescribir/crear componentes base en AppComponents.kt siguiendo este lenguaje 'less is more' minimalista y elegante.

También actualizar Type.kt para simplificar y limpiar.

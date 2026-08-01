# Megaactualización integral de Ordía

Fecha de inicio: 1 de agosto de 2026  
Rama: `feature/ordia-megaactualizacion-integral`

## Línea base

- Árbol de Git limpio antes de crear la rama.
- `clean test lint assembleDebug`: correcto.
- 1,698 pruebas JVM (283 por cada una de las seis variantes), 0 fallos.
- Lint base: 0 errores.
- Tres APKs debug generadas.

## Bloque 1 — marca y espacio de trabajo

- Nombre visible unificado como **Ordía**, con tilde, incluidas las variantes.
- Tema claro blanco/negro/grises y tema oscuro negro/grises; un solo acento discreto.
- Icono adaptativo original de captura/conexión, sin reutilizar el guardián anterior.
- Capas `foreground`, `background` y `monochrome` para iconos temáticos de Android 13+.
- Splash nativo claro y oscuro para Android 12+.
- Onboarding reconstruido con ilustraciones vectoriales nativas, una decisión por página y diseño adaptable.
- Inicio reducido a captura, organización del día, siguiente acción, recuperación de vencidos y tres próximos pasos.
- FAB funcional para tarea, nota, voz, organizar el día y revisar mensajes.
- Cierre del FAB por el mismo botón, toque exterior, Atrás, Escape y navegación.
- Pruebas nuevas para la máquina de estado del FAB y el nombre visible.

Validación del bloque:

- `testPreviewAdvancedDebugUnitTest`: 285 pruebas, 0 fallos.
- `lintPreviewAdvancedDebug`: 0 errores.
- `assemblePreviewAdvancedDebug`: correcto.

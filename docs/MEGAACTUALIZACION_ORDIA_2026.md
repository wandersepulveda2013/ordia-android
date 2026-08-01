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

## Bloque 2 — captura universal y escritura rápida

- Nueva pantalla **Capturar** con compositor amplio, envío por teclado, voz, adjuntos y pegado desde portapapeles.
- Destino automático o explícito: Bandeja, tarea, nota y recordatorio.
- Intérprete local y determinista; conserva el texto original antes de crear cualquier elemento.
- Historial persistente con origen, estado, fecha y acceso al resultado creado.
- Borrador principal con guardado automático y recuperación tras cerrar o reiniciar la aplicación.
- Deduplicación temporal por huella SHA-256 para evitar dobles pulsaciones.
- Captura conectada desde Inicio, el FAB/actividad rápida y `ACTION_SEND` de texto, imágenes o archivos.
- Las listas de varias líneas se convierten en notas con bloques de verificación.
- Las notas nuevas ahora se guardan automáticamente después de 800 ms de edición significativa.
- Barra inferior móvil de cinco destinos: Inicio, Tareas, Capturar, Notas y Más; Planificador permanece accesible desde Más.
- Room v4 con `captures` y `capture_drafts`, índices, conversores y migración no destructiva 3→4.
- Esquemas Room 2, 3 y 4 incorporados al control de versiones.
- Formato de respaldo v5: incluye capturas y borradores, conserva checksum y acepta copias v2–v4.
- El IME real existente continúa siendo opcional, local y excluye campos sensibles; no registra pulsaciones.
- Las imágenes se conservan como adjuntos. No se declara OCR porque el proyecto no tenía un motor OCR verificable.

Validación del bloque:

- `testPreviewAdvancedDebugUnitTest`: 292 pruebas, 0 fallos.
- `compilePreviewAdvancedDebugAndroidTestKotlin`: correcto; incluye contrato de migración 3→4.
- `lintPreviewAdvancedDebug`: 0 errores, 111 advertencias heredadas o informativas (una menos que el bloque 1).
- `assemblePreviewAdvancedDebug`: correcto.
- APK: `app/build/outputs/apk/previewAdvanced/debug/app-previewAdvanced-debug.apk`.

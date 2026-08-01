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

## Bloque 3 — conversaciones y compromisos

- Nuevo centro **Conversaciones**, accesible desde Inicio, Más, el rail de tabletas y el menú de captura rápida.
- Importación local y acotada de exportaciones TXT tipo WhatsApp y JSON de Telegram, además de texto pegado o compartido.
- Vista previa con participantes, mensajes legibles y contenido sensible omitido antes de guardar.
- Selección opcional de la identidad propia para distinguir compromisos personales y de otras personas.
- Motor determinista local para solicitudes, reuniones, compras, recordatorios y compromisos; extrae fecha, lugar, responsable, confianza y recordatorio sugerido.
- Códigos OTP, contraseñas, datos de autenticación y números de tarjeta se bloquean antes de llegar al extractor.
- Ninguna propuesta crea una tarea automáticamente: cada compromiso queda pendiente hasta que el usuario lo convierte o descarta.
- Retención mínima por defecto: se guardan resumen y compromisos; el chat completo solo se conserva mediante un interruptor explícito.
- Historial eliminable, enlaces a tareas creadas, borrado en cascada y deduplicación persistente por SHA-256.
- `ACTION_SEND` admite texto, imágenes y archivos; los textos con estructura de chat abren directamente la vista previa de Conversaciones.
- Room v5 con `conversations` y `commitments`, claves foráneas, índices y migración no destructiva 4→5.
- Esquema Room 5 incorporado al control de versiones y prueba instrumental específica de migración.
- Formato de respaldo v6 con conversaciones y compromisos, validación de consentimiento/relaciones y compatibilidad con copias v2–v5.

Validación del bloque:

- `test`: 1,824 pruebas JVM (304 por cada una de las seis variantes), 0 fallos, 0 errores y 0 omitidas.
- `compilePreviewAdvancedDebugAndroidTestKotlin`: correcto; incluye contratos de migración 3→4 y 4→5.
- `lintPreviewAdvancedDebug`: 0 errores, 111 advertencias heredadas o informativas; no aumentaron respecto al bloque 2.
- `assembleDebug`: correcto; se generaron las tres APK debug (`previewAdvanced`, `previewFull` y `previewSafe`).

## Bloque 4 — observación consentida y privacidad

- `NotificationListenerService` opcional conservado únicamente en las variantes Full y Advanced; Safe no declara el componente ni acceso a Internet.
- Observación desactivada por defecto, con activación explícita, estado visible, acceso directo al permiso de Android, pausa de una hora, reanudación y revocación interna.
- Lista cerrada y configurable de fuentes: WhatsApp, WhatsApp Business, Telegram, Signal y Google Messages. Cada aplicación requiere autorización individual.
- Modo fijo **solo detectar compromisos**: la notificación se procesa localmente y solo se persiste cuando el motor encuentra una propuesta revisable.
- Retención mínima: se guardan resumen, acción y metadatos necesarios; `rawContent` permanece vacío y el original no se conserva.
- Filtro previo para paquetes bancarios, billeteras, autenticadores, salud, contraseñas, OTP, 2FA, tarjetas, notificaciones continuas y resúmenes de grupo.
- Deduplicación persistente mediante SHA-256, límite diario, procesamiento acotado a 4.000 caracteres y ausencia de contenido en logs.
- Centro de Conversaciones ampliado con fuentes autorizadas, estado del permiso, controles de privacidad, borrado selectivo de datos observados e historial de consentimiento sin texto de mensajes.
- Eliminado por completo el servicio de accesibilidad anterior, su permiso, manifiesto, configuración XML y pantalla de activación. Ordía no usa `AccessibilityService` para leer chats ni interfaces.
- Room v6 con `observed_sources` y `consent_events`, índices, DAO transaccional y migración no destructiva 5→6.
- Esquema Room 6 incorporado al control de versiones y prueba instrumental que preserva conversaciones existentes.
- Formato de respaldo v7 con fuentes e historial de consentimiento; acepta copias v2–v6 y siempre restaura las fuentes desactivadas para exigir una nueva decisión local.

Validación del bloque:

- `test`: 1.878 pruebas JVM (313 por cada una de las seis variantes), 0 fallos, 0 errores y 0 omitidas.
- `compilePreviewAdvancedDebugAndroidTestKotlin`: correcto; incluye migración 5→6, consentimiento y borrado selectivo de conversaciones observadas.
- `lintPreviewAdvancedDebug`: 0 errores, 108 advertencias y 1 observación informativa; dos advertencias menos que en el bloque 3.
- `assembleDebug`: correcto; se generaron las tres APK debug.
- Manifiestos fusionados de Advanced: 0 referencias a `AccessibilityService` o `BIND_ACCESSIBILITY_SERVICE`.
- APK Advanced: 37.616.121 bytes; SHA-256 `d51318a2203e0543cce489ca35103d55402160d497c247c371f8dcaeac343c6b`.
- APK Full: 37.615.765 bytes; SHA-256 `ce61411070c1e940be30f6e54fc4b1452b52c35aa0d046f315fdbe303cc0ddb0`.
- APK Safe: 37.598.577 bytes; SHA-256 `ac9430863daee492d681253fb585b1a72840d3edb75c50dec41e30f23326b360`.

## Bloque 5 — automatizaciones explicables y reversibles

- Nuevo centro **Automatizaciones**, accesible desde Más y desde el rail de tabletas.
- Creación mediante lenguaje natural acotado y cuatro plantillas reales: preparar el día, reprogramar vencidas, agrupar tareas rápidas y revisar compromisos de mensajes.
- Cada regla persiste nombre, instrucción, activador, condición, acción, explicación, estado, frecuencia mínima, máximo diario, último resultado y error.
- Las reglas nacen desactivadas; el usuario puede probarlas sin cambios, activarlas, ejecutarlas ahora, pausarlas o eliminarlas.
- Motor local separado de la UI; usa datos Room reales, `DayPlanner`, tareas y compromisos pendientes.
- WorkManager ejecuta reglas matutinas/nocturnas con batería no baja; las reglas de apertura se evalúan al iniciar la aplicación.
- Protección contra bucles, dobles ejecuciones por frecuencia, máximo diario, duplicados de definición y creación repetida de la revisión de mensajes.
- Los cambios registran snapshots previos en el historial existente y ofrecen deshacer; las pruebas nunca modifican tareas.
- Errores acotados y visibles; el worker realiza como máximo dos reintentos transitorios y no crea bucles infinitos.
- Room v7 con `automation_rules`, índices y migración no destructiva 6→7; los logs anteriores se preservan.
- Respaldo v8 incluye reglas e historial, acepta copias v2–v7 y restaura todas las reglas desactivadas.

Validación del bloque:

- Pruebas dirigidas de parser, condiciones, planificación, límites, bucles, duplicados y horarios: correctas.
- Pruebas de respaldo v2–v8, checksum y restauración segura: correctas.
- `compilePreviewAdvancedDebugAndroidTestKotlin`: correcto; incluye migración 6→7 y smoke test Room.
- `compilePreviewAdvancedDebugKotlin`: correcto.

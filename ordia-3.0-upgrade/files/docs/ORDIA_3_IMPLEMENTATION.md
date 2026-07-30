# Ordia 3.0 — implementación

Esta entrega incorpora la base auditada de Ordia 2.0 y añade atención contextual privada.

## Implementado

- análisis local de español para tareas, compromisos y estudio;
- fechas relativas, días de la semana y horas;
- rechazo de contraseñas, PIN, CVV, OTP y claves;
- confirmación antes de guardar;
- recepción manual mediante Compartir y Procesar texto;
- listener opcional de notificaciones;
- bandeja que guarda solo datos estructurados;
- deduplicación, caducidad, límite diario y pausa;
- pantalla de control y permiso;
- guardianes y actualización segura heredados de 2.0.

## Deliberadamente excluido

No se incluye AccessibilityService para leer chats completos. Esa vía es invasiva, difícil de justificar en Google Play y no es necesaria para el valor principal.

## Presencia sin notificaciones

Cuando el guardián flotante está activo, su panel muestra el número de sugerencias contextuales pendientes. No se publica una notificación adicional por cada mensaje detectado.

## Consentimiento por aplicación

El listener permanece desactivado por defecto. Incluso después de conceder acceso a notificaciones, la lista permitida comienza vacía y el usuario activa cada aplicación por separado.

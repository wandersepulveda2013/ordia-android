# Privacidad y permisos

## Modelo de datos

Ordia funciona sin cuenta y almacena la información en Room y DataStore dentro del espacio privado de la app. No declara permiso de Internet.

## Copias

La copia automática de Android está desactivada. El usuario puede exportar un JSON local y elegir dónde guardarlo. Ese archivo contiene la información organizada en Ordia y debe tratarse como confidencial.

## Notificaciones

`POST_NOTIFICATIONS` se solicita desde Ajustes cuando el usuario decide activar recordatorios. Denegarlo no impide usar la organización básica.

## Superposición

`SYSTEM_ALERT_WINDOW` permite mostrar el guardián sobre otras aplicaciones. Es opcional y se gestiona desde la pantalla de permisos del sistema. El guardián no lee, captura ni modifica el contenido de otras apps.

## Servicio en primer plano

El guardián usa un servicio visible para permanecer disponible. Android muestra una notificación persistente y ofrece una acción para ocultarlo.

## Dictado

La captura rápida abre el reconocedor de voz instalado en el dispositivo mediante una intención del sistema. Ordia no graba audio directamente y no solicita `RECORD_AUDIO`. El comportamiento de red del reconocedor elegido depende del proveedor instalado en el dispositivo.

## Vibración

`VIBRATE` se reserva para retroalimentación y recordatorios compatibles.

## Datos externos

Los adjuntos se conservan mediante URI concedidas por Android. El usuario debe mantener acceso al archivo original o exportar una copia antes de retirarlo del proveedor de documentos.

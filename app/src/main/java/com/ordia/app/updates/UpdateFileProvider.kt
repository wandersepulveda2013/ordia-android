package com.ordia.app.updates

import androidx.core.content.FileProvider

/**
 * FileProvider dedicado a la infraestructura de auto-actualización de APKs.
 *
 * Se declara como una subclase con su propio `android:name` en los manifiestos
 * de los flavors previewAdvanced/previewFull para que el manifest merger no lo
 * fusiona con el FileProvider de adjuntos (`${applicationId}.attachments`),
 * que también usa `androidx.core.content.FileProvider`. Sin esta distinción de
 * nombre, dos `<provider>` con el mismo `android:name` pero distintas
 * `authorities` provocan un fallo de merge en CI.
 */
class UpdateFileProvider : FileProvider()

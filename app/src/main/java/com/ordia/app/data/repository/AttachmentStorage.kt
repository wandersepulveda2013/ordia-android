package com.ordia.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.ordia.app.data.local.AttachmentOwnerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persiste adjuntos copiando su contenido a almacenamiento interno de la app.
 *
 * Antes, `AttachmentEntity.uri` guardaba el URI externo tal cual (SAF/`content://`).
 * Eso hacía el adjunto frágil: si el permiso persistente fallaba o caducaba, el
 * contenido quedaba inaccesible tras reinicio y `ACTION_VIEW` fallaba con un mensaje
 * engañoso. Aquí copiamos los bytes a `filesDir/attachments/` y exponemos el archivo
 * interno vía un FileProvider propio, de modo que la app es dueña del contenido y
 * puede abrirlo de forma fiable.
 */
class AttachmentStorage(private val context: Context) {

    private val baseDir: File by lazy {
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }
    }

    private fun authority(): String = "${context.packageName}.attachments"

    /**
     * Copia el contenido apuntado por [sourceUri] a almacenamiento interno.
     *
     * @return la ruta absoluta del archivo interno, o `null` si la copia falló
     *         (el llamador debe entonces guardar el URI original como respaldo).
     */
    suspend fun import(
        sourceUri: String,
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        displayName: String
    ): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext null
        val target = targetFile(ownerType, ownerId, displayName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
        } catch (_: Exception) {
            return@withContext null
        }
        target.absolutePath
    }

    /**
     * Resuelve el URI a abrir con `ACTION_VIEW`. Si apunta a un archivo interno
     * de adjuntos, lo expone vía FileProvider; si el archivo ya no existe,
     * devuelve `null` (el llamador muestra el toast de fallo). Para URIs externos
     * (heredados de capturas anteriores a este cambio) se devuelve tal cual.
     */
    fun resolveForOpening(storedUri: String): Uri? {
        val file = storedFileOrNull(storedUri) ?: run {
            // No es un archivo interno gestionado: devolver el URI tal cual
            // (puede ser un content:// legacy o un file://).
            return runCatching { Uri.parse(storedUri) }.getOrNull()
        }
        if (!file.exists()) return null
        return runCatching { FileProvider.getUriForFile(context, authority(), file) }.getOrNull()
    }

    /** Elimina el archivo interno asociado a [storedUri], si lo hay. Silencioso. */
    fun deleteStored(storedUri: String) {
        val file = storedFileOrNull(storedUri) ?: return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun storedFileOrNull(storedUri: String): File? {
        if (storedUri.isBlank()) return null
        val file = runCatching { File(storedUri) }.getOrNull() ?: return null
        // Solo gestionamos archivos que viven dentro de nuestro directorio de adjuntos.
        val base = baseDir.canonicalPath
        return runCatching {
            if (file.canonicalPath.startsWith(base)) file else null
        }.getOrNull()
    }

    private fun targetFile(ownerType: AttachmentOwnerType, ownerId: Long, displayName: String): File {
        val stamp = System.currentTimeMillis()
        val safe = sanitizeFileName(displayName)
        val baseName = safe.ifBlank { DEFAULT_NAME }
        val nameWithExt = ensureExtension(baseName)
        val candidate = File(baseDir, "${ownerType.name.lowercase()}_${ownerId}_${stamp}_$nameWithExt")
        // Evita colisiones (extremadamente improbables por el stamp, pero defensivo).
        return if (candidate.exists()) {
            File(baseDir, "${ownerType.name.lowercase()}_${ownerId}_${stamp}_${(1..999).random()}_$nameWithExt")
        } else candidate
    }

    companion object {
        const val DIR = "attachments"
        private const val DEFAULT_NAME = "archivo"

        /** Sanea un nombre para usarlo como nombre de archivo (puro, sin dependencias Android). */
        fun sanitizeFileName(name: String): String {
            if (name.isBlank()) return ""
            val cleaned = name.trim()
                .replace('/', '-')
                .replace('\\', '-')
                .replace('\u0000'.toString(), "")
            // Recorta nombres excesivamente largos conservando la extensión.
            return if (cleaned.length <= MAX_NAME_LEN) cleaned else {
                val dot = cleaned.lastIndexOf('.')
                if (dot in 1..(cleaned.length - 2) && cleaned.length - dot <= MAX_EXT_LEN) {
                    cleaned.take(MAX_NAME_LEN - (cleaned.length - dot)) + cleaned.substring(dot)
                } else {
                    cleaned.take(MAX_NAME_LEN)
                }
            }
        }

        private const val MAX_NAME_LEN = 120
        private const val MAX_EXT_LEN = 8

        private fun ensureExtension(name: String): String {
            val dot = name.lastIndexOf('.')
            if (dot in 1..(name.length - 2)) return name
            return name
        }

        /** Extrae el display name desde un URI content:// vía OpenableColumns (mejor esfuerzo). */
        fun queryDisplayName(context: Context, uri: Uri): String? {
            return runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
                }
            }.getOrNull()
        }
    }
}

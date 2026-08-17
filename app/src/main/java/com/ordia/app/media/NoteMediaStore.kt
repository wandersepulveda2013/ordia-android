package com.ordia.app.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom

/**
 * Almacenamiento privado seguro para multimedia de notas.
 *
 * Todo el contenido vive bajo `filesDir/notes-media/`, espacio privado de la app
 * (no requiere permisos de almacenamiento). Los nombres son determinísticos y
 * únicos para evitar duplicados accidentales al re-insertar el mismo origen.
 *
 * Las imágenes se normalizan según la orientación EXIF para que nunca lleguen
 * rotadas al editor, conservando la original intacta cuando se hace OCR/escáner.
 */
object NoteMediaStore {
    private const val TAG = "NoteMediaStore"
    private const val DIR = "notes-media"

    private fun mediaDir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Nombre de archivo único: timestamp + random hex + ext inferida. */
    private fun uniqueName(base: String?, mimeType: String?): String {
        val ext = extensionFor(base, mimeType)
        val rnd = SecureRandom().nextInt().toLong() and 0xFFFFFFFFL
        return "${System.currentTimeMillis()}-${rnd.toString(16)}$ext"
    }

    private fun extensionFor(base: String?, mimeType: String?): String {
        val fromMime = mimeType?.let {
            when (it.lowercase()) {
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                "application/pdf" -> ".pdf"
                "audio/m4a", "audio/x-m4a", "audio/mp4" -> ".m4a"
                "audio/mpeg", "audio/mp3" -> ".mp3"
                else -> null
            }
        }
        if (fromMime != null) return fromMime
        val fromName = base?.substringAfterLast('.', missingDelimiterValue = "")
        return if (fromName.isNullOrEmpty() || fromName.length > 6) ".bin" else ".$fromName"
    }

    /** Resuelve un [uri] externo a un archivo privado normalizado. Devuelve ruta absoluta. */
    fun importImage(context: Context, uri: Uri): String? {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val displayName = queryDisplayName(context.contentResolver, uri)
        val name = uniqueName(displayName, mime)
        val target = File(mediaDir(context), name)
        return runCatching {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.setMutableRequired(true)
            }
            val oriented = applyExifOrientation(context, uri, decoded)
            FileOutputStream(target).use { out ->
                val format = if (mime.contains("png", ignoreCase = true)) Bitmap.CompressFormat.PNG
                else if (mime.contains("webp", ignoreCase = true)) Bitmap.CompressFormat.WEBP
                else Bitmap.CompressFormat.JPEG
                oriented.compress(format, 90, out)
            }
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            target.absolutePath
        }.onFailure { Log.w(TAG, "importImage failed", it) }.getOrNull()
    }

    /** Guarda un [bitmap] dado como JPEG/PNG privado. Devuelve ruta absoluta. */
    fun saveBitmap(context: Context, bitmap: Bitmap, mime: String = "image/jpeg"): String? {
        val name = uniqueName(null, mime)
        val target = File(mediaDir(context), name)
        return runCatching {
            FileOutputStream(target).use { out ->
                val format = if (mime.contains("png", true)) Bitmap.CompressFormat.PNG
                else if (mime.contains("webp", true)) Bitmap.CompressFormat.WEBP
                else Bitmap.CompressFormat.JPEG
                bitmap.compress(format, 92, out)
            }
            target.absolutePath
        }.onFailure { Log.w(TAG, "saveBitmap failed", it) }.getOrNull()
    }

    /** Copia un stream arbitrario a almacenamiento privado. Útil para PDF/audio/archivos. */
    fun importStream(context: Context, uri: Uri): String? {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(context.contentResolver, uri)
        val name = uniqueName(displayName, mime)
        val target = File(mediaDir(context), name)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Log.w(TAG, "importStream failed", it) }.getOrNull()
    }

    fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        return runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && c.getColumnIndex(OpenableColumns.DISPLAY_NAME) >= 0) {
                    c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else null
            }
        }.getOrNull()
    }

    /**
     * Rota/voltea el bitmap según la orientación EXIF del origen.
     * Devuelve el mismo bitmap si no hay rotación, o uno nuevo.
     */
    private fun applyExifOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        return runCatching {
            val exif = context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input)
            } ?: return bmp
            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) return bmp
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }.getOrDefault(bmp)
    }

    /** Recupera dimensiones sin decodificar el bitmap completo. */
    fun imageDimensions(path: String): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return if (opts.outWidth > 0) opts.outWidth to opts.outHeight else null
    }

    /** Borra un archivo por ruta absoluta de forma segura (idempotente). */
    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return runCatching { File(path).takeIf { it.exists() }?.delete() ?: false }
            .getOrDefault(false)
    }

    /** ¿Es una ruta privada gestionada por este store? (evita borrar archivos externos). */
    fun isManagedPath(context: Context, path: String): Boolean {
        val base = mediaDir(context).absolutePath
        return path.startsWith(base)
    }
}

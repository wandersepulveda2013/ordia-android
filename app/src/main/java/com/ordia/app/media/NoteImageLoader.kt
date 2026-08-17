package com.ordia.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Carga imágenes desde cualquier referencia soportada por ORDÍA:
 * - rutas absolutas privadas (las que produce [NoteMediaStore] / cámara);
 * - URIs `file://`;
 * - URIs `content://` (galería, share target, FileProvider).
 *
 * Decodifica con submuestreo para no agotar memoria en notas con muchas
 * imágenes, y siempre devuelve un bitmap inmutable y listo para mostrar.
 */
object NoteImageLoader {
    private const val TAG = "NoteImageLoader"
    private const val MAX_DIM = 2048

    fun load(context: Context, ref: String): Bitmap? {
        if (ref.isBlank()) return null
        // Ruta absoluta directa (sin esquema).
        if (ref.startsWith("/")) return decodePath(ref)
        val uri = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
        val scheme = uri.scheme
        return when (scheme) {
            "file" -> decodePath(uri.path ?: return null)
            "content" -> decodeContent(context, uri)
            else -> decodePath(ref)
        }
    }

    private fun decodePath(path: String): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = sampleSize(bounds.outWidth, bounds.outHeight)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        }.onFailure { Log.w(TAG, "decodePath failed: $path", it) }.getOrNull()
    }

    private fun decodeContent(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.setMutableRequired(false)
                val w = info.size.width
                val h = info.size.height
                val maxDim = max(w, h)
                if (maxDim > MAX_DIM) {
                    val ratio = MAX_DIM.toFloat() / maxDim
                    decoder.setTargetSampleSize(sampleFor(maxDim, min(w, h), ratio))
                }
            }
        }.onFailure { Log.w(TAG, "decodeContent failed: $uri", it) }.getOrNull()
    }

    private fun sampleSize(w: Int, h: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var sample = 1
        var maxDim = max(w, h)
        while (maxDim / 2 >= MAX_DIM) {
            sample *= 2
            maxDim /= 2
        }
        return sample
    }

    private fun sampleFor(maxDim: Int, minDim: Int, ratio: Float): Int {
        val target = (maxDim * ratio).toInt().coerceAtLeast(1)
        var sample = 1
        while (maxDim / (sample * 2) >= target) sample *= 2
        return sample.coerceAtLeast(1)
    }
}

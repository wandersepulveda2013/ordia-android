package com.ordia.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * Procesamiento de imágenes para el escáner de documentos, sin OpenCV.
 *
 * Incluye:
 *  - corrección de perspectiva por 4 puntos (transformación bilineal por warping
 *    de columnas/filas, suficientemente buena para documentos bien encuadrados);
 *  - rotación por múltiplos de 90°;
 *  - modos: auto (sólo mejora de contraste), gris, blanco y negro (umbral).
 *
 * Es intencionalmente simple y robusto: prioriza estabilidad sobre precisión
 * sub-píxel. No detecta bordes automáticamente; el usuario ajusta los 4 puntos.
 */
object DocumentScanner {

    enum class Mode { AUTO, GRAY, BW }

    data class Quad(
        val tl: PointF, val tr: PointF,
        val bl: PointF, val br: PointF
    ) {
        data class PointF(val x: Float, val y: Float)
    }

    /** Rota [bmp] por [degrees] (múltiplos de 90). Devuelve un bitmap nuevo. */
    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    /** Aplica el [mode] de escaneo al bitmap. Devuelve uno nuevo. */
    fun applyMode(bmp: Bitmap, mode: Mode): Bitmap {
        if (mode == Mode.AUTO) return enhanceContrast(bmp)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (mode) {
            Mode.GRAY -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            Mode.BW -> {
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                    // Umbral ~0.5: escala y post-offset para forzar B/N puro.
                    set(floatArrayOf(2f, 0f, 0f, 0f, -255f))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            Mode.AUTO -> {}
        }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    /** Realce de contraste suave (auto). No destruye detalles. */
    private fun enhanceContrast(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix().apply {
            setSaturation(0.85f)
            postConcat(ColorMatrix().apply {
                setScale(1.12f, 1.12f, 1.12f, 1f)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    /**
     * Corrección de perspectiva por 4 puntos.
     *
     * Mapea el cuadrilátero [quad] (en coordenadas del bitmap) a un rectángulo
     * de salida de tamaño [outW]×[outH], usando interpolación bilineal por
     * columnas. Lee por filas con getPixels para reducir overhead.
     */
    fun perspective(bmp: Bitmap, quad: Quad, outW: Int, outH: Int): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val src = bmp
        val tl = quad.tl; val tr = quad.tr; val bl = quad.bl; val br = quad.br
        val row = IntArray(outW)
        for (y in 0 until outH) {
            val v = y.toFloat() / outH
            val leftX = lerp(tl.x, bl.x, v)
            val leftY = lerp(tl.y, bl.y, v)
            val rightX = lerp(tr.x, br.x, v)
            val rightY = lerp(tr.y, br.y, v)
            for (x in 0 until outW) {
                val u = x.toFloat() / outW
                val sx = lerp(leftX, rightX, u)
                val sy = lerp(leftY, rightY, u)
                val ix = sx.toInt().coerceIn(0, src.width - 1)
                val iy = sy.toInt().coerceIn(0, src.height - 1)
                row[x] = src.getPixel(ix, iy)
            }
            out.setPixels(row, 0, outW, 0, y, outW, 1)
        }
        return out
    }

    /** Calcula un tamaño de salida razonable a partir del quad. */
    fun outputSize(bmp: Bitmap, quad: Quad): Pair<Int, Int> {
        val wTop = dist(quad.tl, quad.tr)
        val wBottom = dist(quad.bl, quad.br)
        val hLeft = dist(quad.tl, quad.bl)
        val hRight = dist(quad.tr, quad.br)
        val w = max(wTop, wBottom).toInt().coerceIn(1, bmp.width)
        val h = max(hLeft, hRight).toInt().coerceIn(1, bmp.height)
        return min(w, 2400) to min(h, 2400)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun dist(a: Quad.PointF, b: Quad.PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

package com.ordia.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ordia.app.media.NoteMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Button

/**
 * Diálogo de dibujo / escritura a mano.
 *
 * Canvas Compose con trazos vectoriales, herramientas (pluma/marcador/resaltador/
 * borrador), colores, grosores, undo/redo. Al confirmar, rasteriza a PNG en
 * almacenamiento privado e inserta como bloque DRAWING/HANDWRITING.
 *
 * No intenta ser Photoshop; está pensado para diagramas, croquis y anotaciones.
 */
@Composable
internal fun DrawingDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val redoStack = remember { mutableStateListOf<DrawStroke>() }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableStateOf(4f) }
    var current by remember { mutableStateOf<DrawStroke?>(null) }

    val canvasSize = remember { mutableStateOf(Offset.Zero) }

    fun addStroke(s: DrawStroke) { strokes.add(s); redoStack.clear() }
    fun undo() { strokes.removeLastOrNull()?.let { redoStack.add(it) } }
    fun redo() { redoStack.removeLastOrNull()?.let { strokes.add(it) } }

    val colors = listOf(
        Color.Black, Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
        Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF6D4C41)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { undo() }, enabled = strokes.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
                    IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
                    Spacer(Modifier.width(8.dp))
                    // Herramientas
                    Tool.entries.forEach { t ->
                        val selected = tool == t
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            onClick = { tool = t },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                t.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(tool, color, width) {
                            detectDragGestures(
                                onDragStart = { off ->
                                    current = DrawStroke(tool, color, width, mutableListOf(off))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current?.points?.add(change.position)
                                },
                                onDragEnd = {
                                    current?.let { addStroke(it) }
                                    current = null
                                },
                                onDragCancel = { current = null }
                            )
                        }
                ) {
                    canvasSize.value = Offset(size.width, size.height)
                    // Fondo blanco para el rasterizado.
                    drawRect(Color.White)
                    strokes.forEach { drawStroke(it) }
                    current?.let { drawStroke(it) }
                }
                Spacer(Modifier.height(8.dp))
                // Paleta
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { c ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(c, RoundedCornerShape(50))
                                .pointerInput(c) {
                                    detectTapGestures { color = c; if (tool == Tool.ERASER) tool = Tool.PEN }
                                }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Grosor", style = MaterialTheme.typography.labelSmall)
                    listOf(2f, 4f, 8f, 16f).forEach { w ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (width == w) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            onClick = { width = w }
                        ) {
                            Text("${w.toInt()}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val size = canvasSize.value
                        if (size.x <= 0f || size.y <= 0f) return@Button
                        scope.launch {
                            val path = withContext(Dispatchers.Default) {
                                runCatching {
                                    val bmp = Bitmap.createBitmap(size.x.toInt(), size.y.toInt(), Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bmp)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    strokes.forEach { s ->
                                        val paint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeJoin = android.graphics.Paint.Join.ROUND
                                            strokeCap = android.graphics.Paint.Cap.ROUND
                                            strokeWidth = s.width
                                            this.color = if (s.tool == Tool.ERASER) android.graphics.Color.WHITE else s.color.toArgb()
                                            if (s.tool == Tool.HIGHLIGHTER) alpha = 80
                                        }
                                        val p = android.graphics.Path()
                                        val pts = s.points
                                        if (pts.isNotEmpty()) {
                                            p.moveTo(pts[0].x, pts[0].y)
                                            for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
                                            canvas.drawPath(p, paint)
                                        }
                                    }
                                    NoteMediaStore.saveBitmap(context, bmp, "image/png")
                                }.getOrNull()
                            }
                            if (path != null) onInsert(path)
                        }
                    }) { Text("Insertar") }
                }
            }
        }
    }
}

private enum class Tool(val label: String) { PEN("Pluma"), MARKER("Marcador"), HIGHLIGHTER("Resaltador"), ERASER("Borrador") }

private data class DrawStroke(val tool: Tool, val color: Color, val width: Float, val points: MutableList<Offset>)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(s: DrawStroke) {
    if (s.points.isEmpty()) return
    val col = if (s.tool == Tool.ERASER) Color.White else s.color
    val alpha = if (s.tool == Tool.HIGHLIGHTER) 0.3f else 1f
    val path = Path().apply {
        moveTo(s.points[0].x, s.points[0].y)
        for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
    }
    drawPath(
        path = path,
        color = col.copy(alpha = alpha),
        style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

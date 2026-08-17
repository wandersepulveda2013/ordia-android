package com.ordia.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ordia.app.R
import com.ordia.app.media.DocumentScanner
import com.ordia.app.media.NoteMediaStore
import com.ordia.app.media.OcrRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Diálogo de escaneo de documentos.
 *
 * Flujo: cargar imagen → previsualizar con 4 puntos ajustables → rotar →
 * elegir modo (auto/gris/BN) → corregir perspectiva → insertar como imagen.
 */
@Composable
internal fun ScannerDialog(
    context: Context,
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sourceBmp by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(DocumentScanner.Mode.AUTO) }
    // Puntos normalizados [0..1] sobre la imagen mostrada.
    var pts by remember(sourceBmp) {
        mutableStateOf(
            DocumentScanner.Quad(
                DocumentScanner.Quad.PointF(0.05f, 0.05f),
                DocumentScanner.Quad.PointF(0.95f, 0.05f),
                DocumentScanner.Quad.PointF(0.05f, 0.95f),
                DocumentScanner.Quad.PointF(0.95f, 0.95f)
            )
        )
    }
    var dragging by remember { mutableStateOf(0) } // 0=tl 1=tr 2=bl 3=br
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            sourceBmp = runCatching {
                val src = ImageDecoder.createSource(context.contentResolver, sourceUri)
                ImageDecoder.decodeBitmap(src) { d, _, _ -> d.setMutableRequired(true) }
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringRes(R.string.notes_editor_scanner_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val bmp = sourceBmp
                if (bmp == null) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val rotated = if (rotation % 360 == 0) bmp else DocumentScanner.rotate(bmp, rotation)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(rotated.width.toFloat() / rotated.height.toFloat())
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Image(
                            bitmap = rotated.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(rotated.width.toFloat() / rotated.height.toFloat())
                                .pointerInput(rotated) {
                                    detectDragGestures(
                                        onDragStart = { off ->
                                            dragging = closestPoint(pts, off, size.width.toFloat(), size.height.toFloat())
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            val nx = (change.position.x / w).coerceIn(0f, 1f)
                                            val ny = (change.position.y / h).coerceIn(0f, 1f)
                                            pts = when (dragging) {
                                                0 -> pts.copy(tl = DocumentScanner.Quad.PointF(nx, ny))
                                                1 -> pts.copy(tr = DocumentScanner.Quad.PointF(nx, ny))
                                                2 -> pts.copy(bl = DocumentScanner.Quad.PointF(nx, ny))
                                                3 -> pts.copy(br = DocumentScanner.Quad.PointF(nx, ny))
                                                else -> pts
                                            }
                                        }
                                    )
                                }
                    ) {
                        val w = this.size.width
                        val h = this.size.height
                        val p = Path().apply {
                            moveTo(pts.tl.x * w, pts.tl.y * h)
                            lineTo(pts.tr.x * w, pts.tr.y * h)
                            lineTo(pts.br.x * w, pts.br.y * h)
                            lineTo(pts.bl.x * w, pts.bl.y * h)
                            close()
                        }
                        drawPath(p, color = androidx.compose.ui.graphics.Color(0x88FFC107), style = Stroke(width = 3.dp.toPx()))
                        for (point in listOf(pts.tl, pts.tr, pts.bl, pts.br)) {
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color(0xFFFFC107),
                                radius = 10.dp.toPx(),
                                center = Offset(point.x * w, point.y * h)
                            )
                        }
                    }
                }
                } // fin else
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { rotation = (rotation + 90) % 360 }) { Text(stringRes(R.string.notes_editor_scanner_rotate)) }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == DocumentScanner.Mode.AUTO, onClick = { mode = DocumentScanner.Mode.AUTO }, label = { Text(stringRes(R.string.notes_editor_scanner_mode_auto)) })
                    FilterChip(selected = mode == DocumentScanner.Mode.GRAY, onClick = { mode = DocumentScanner.Mode.GRAY }, label = { Text(stringRes(R.string.notes_editor_scanner_mode_gray)) })
                    FilterChip(selected = mode == DocumentScanner.Mode.BW, onClick = { mode = DocumentScanner.Mode.BW }, label = { Text(stringRes(R.string.notes_editor_scanner_mode_bw)) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val src = sourceBmp ?: return@Button
                        scope.launch {
                            val path = withContext(Dispatchers.Default) {
                                runCatching {
                                    var b = if (rotation % 360 == 0) src else DocumentScanner.rotate(src, rotation)
                                    val w = b.width.toFloat(); val h = b.height.toFloat()
                                    val quad = DocumentScanner.Quad(
                                        DocumentScanner.Quad.PointF(pts.tl.x * w, pts.tl.y * h),
                                        DocumentScanner.Quad.PointF(pts.tr.x * w, pts.tr.y * h),
                                        DocumentScanner.Quad.PointF(pts.bl.x * w, pts.bl.y * h),
                                        DocumentScanner.Quad.PointF(pts.br.x * w, pts.br.y * h)
                                    )
                                    val (ow, oh) = DocumentScanner.outputSize(b, quad)
                                    var warped = DocumentScanner.perspective(b, quad, ow, oh)
                                    warped = DocumentScanner.applyMode(warped, mode)
                                    NoteMediaStore.saveBitmap(context, warped, "image/jpeg")
                                }.getOrNull()
                            }
                            if (path != null) onInsert(path) else error = context.getString(R.string.notes_editor_scanner_empty)
                        }
                    }) { Text(stringRes(R.string.notes_editor_scanner_apply)) }
                }
            }
        }
    }
}

@Composable
internal fun OcrDialog(
    context: Context,
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onInsertText: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<OcrState>(OcrState.Loading) }
    var editable by remember { mutableStateOf("") }

    LaunchedEffect(sourceUri) {
        if (!OcrRunner.isAvailable) {
            state = OcrState.Unavailable
            return@LaunchedEffect
        }
        state = OcrState.Loading
        withContext(Dispatchers.IO) {
            val bmp = runCatching {
                val src = ImageDecoder.createSource(context.contentResolver, sourceUri)
                ImageDecoder.decodeBitmap(src) { d, _, _ -> d.setMutableRequired(false) }
            }.getOrNull()
            if (bmp == null) { state = OcrState.Failed; return@withContext }
            val result = OcrRunner.recognize(context, bmp)
            state = when (result) {
                is OcrRunner.OcrResult.Success -> { editable = result.text; OcrState.Done }
                OcrRunner.OcrResult.Empty -> OcrState.Empty
                OcrRunner.OcrResult.Unavailable -> OcrState.Unavailable
                is OcrRunner.OcrResult.Failed -> OcrState.Failed
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.notes_editor_ocr_title)) },
        text = {
            when (state) {
                OcrState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringRes(R.string.notes_editor_ocr_running))
                }
                OcrState.Empty -> Text(stringRes(R.string.notes_editor_ocr_empty))
                OcrState.Unavailable -> Text(stringRes(R.string.notes_editor_ocr_failed))
                OcrState.Failed -> Text(stringRes(R.string.notes_editor_ocr_failed))
                OcrState.Done -> OutlinedTextField(
                    value = editable,
                    onValueChange = { editable = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (state == OcrState.Done) {
                TextButton(onClick = { onInsertText(editable) }) { Text(stringRes(R.string.notes_editor_ocr_insert)) }
            }
        },
        dismissButton = {
            Row {
                if (state == OcrState.Done) {
                    TextButton(onClick = {
                        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Ordia OCR", editable))
                    }) { Text(stringRes(R.string.notes_editor_ocr_copy)) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

private enum class OcrState { Loading, Empty, Unavailable, Failed, Done }

private fun closestPoint(
    quad: DocumentScanner.Quad, off: Offset, w: Float, h: Float
): Int {
    val pts = listOf(
        quad.tl.x * w to quad.tl.y * h,
        quad.tr.x * w to quad.tr.y * h,
        quad.bl.x * w to quad.bl.y * h,
        quad.br.x * w to quad.br.y * h
    )
    var best = 0; var bestD = Float.MAX_VALUE
    for (i in pts.indices) {
        val d = (pts[i].first - off.x) * (pts[i].first - off.x) + (pts[i].second - off.y) * (pts[i].second - off.y)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

package com.ordia.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ordia.app.ui.theme.OrdiaAccent
import com.ordia.app.ui.theme.OrdiaInk
import kotlinx.coroutines.delay

/**
 * Splash de identidad de ORDÍA NOTES.
 *
 * Un trazo de tinta dibuja el símbolo "O" de Ordía en ~0.9 s y desvanecerá
 * para revelar las notas. Respeta "reducir movimiento": si el sistema lo pide,
 * aparece ya completo sin animar.
 *
 * No bloquea artificialmente: si [onFinished] se invoca cuando la animación
 * termina; la carga de datos continúa en background.
 */
@Composable
fun NotesSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val progress = remember { Animatable(0f) }
    var revealWord by remember { mutableStateOf(false) }
    val wordAlpha by animateFloatAsState(
        targetValue = if (revealWord) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "wordReveal"
    )

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            progress.snapTo(1f)
            revealWord = true
            delay(120)
            onFinished()
        } else {
            progress.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
            revealWord = true
            delay(360)
            onFinished()
        }
    }

    val canvas = MaterialTheme.colorScheme.background
    val ink = OrdiaInk
    val accent = OrdiaAccent
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize(0.42f)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) * 0.34f
            // El símbolo: un trazo circular abierto (casi completo) con un
            // pequeño remate, evocando tinta que construye la "O".
            val path = Path().apply {
                addArc(
                    androidx.compose.ui.geometry.Rect(
                        Offset(cx - radius, cy - radius),
                        Offset(cx + radius, cy + radius)
                    ),
                    startAngleDegrees = -110f,
                    sweepAngleDegrees = 300f
                )
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * progress.value, drawn, true)
            drawPath(drawn, color = ink, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            // Acento: un punto cuando el trazo termina.
            if (progress.value > 0.98f) {
                drawCircle(
                    color = accent,
                    radius = 6.dp.toPx(),
                    center = Offset(cx + radius * 0.92f, cy - radius * 0.18f)
                )
            }
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ORDÍA",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = wordAlpha),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = 6.sp
            )
        }
    }
}

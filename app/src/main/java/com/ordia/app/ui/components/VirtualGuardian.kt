package com.ordia.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.labelRes
import kotlin.math.sin

@Composable
fun VirtualGuardian(
    snapshot: GuardianEngine.Snapshot,
    size: Dp = 180.dp,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "guardian-life")
    val floatY by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
        label = "guardian-float"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(3_600), RepeatMode.Restart),
        label = "guardian-blink"
    )
    val palette = palette(snapshot.species)
    val description = stringResource(
        R.string.pet_guardian_description,
        snapshot.name,
        stringResource(snapshot.species.labelRes()),
        stringResource(snapshot.stage.labelRes()),
        stringResource(snapshot.mood.labelRes())
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.backdrop)
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bob = sin(floatY * Math.PI).toFloat() * this.size.height * 0.035f
            drawGuardian(
                species = snapshot.species,
                stage = snapshot.stage,
                mood = snapshot.mood,
                palette = palette,
                center = Offset(this.size.width / 2f, this.size.height / 2f + bob),
                scale = 0.78f + snapshot.stage.ordinal * 0.035f,
                blink = blink > 0.94f
            )
            drawAmbient(snapshot, palette, floatY)
        }
    }
}

private data class GuardianPalette(
    val body: Color,
    val bodyDark: Color,
    val accent: Color,
    val glow: Color,
    val eye: Color,
    val backdrop: Color
)

private fun palette(species: GuardianSpecies): GuardianPalette = when (species) {
    GuardianSpecies.LUMI -> GuardianPalette(Color(0xFFFFE3A6), Color(0xFFE1A94D), Color(0xFFFFF5D4), Color(0xFFFFC857), Color(0xFF33250C), Color(0xFFFFF7E8))
    GuardianSpecies.MOSS -> GuardianPalette(Color(0xFF9FC58E), Color(0xFF55734C), Color(0xFFD8EBCB), Color(0xFF87B975), Color(0xFF172313), Color(0xFFF1F7ED))
    GuardianSpecies.ORBIT -> GuardianPalette(Color(0xFFAFA8ED), Color(0xFF6156A6), Color(0xFFE8E4FF), Color(0xFF8E7CF0), Color(0xFF17132C), Color(0xFFF3F1FF))
    GuardianSpecies.EMBER -> GuardianPalette(Color(0xFFFFA36C), Color(0xFFC84F2A), Color(0xFFFFE1C7), Color(0xFFFF7145), Color(0xFF35140C), Color(0xFFFFF0E8))
    GuardianSpecies.TIDE -> GuardianPalette(Color(0xFF7DC9DD), Color(0xFF28778C), Color(0xFFD9F7FF), Color(0xFF56B8D4), Color(0xFF0B2931), Color(0xFFECFAFD))
    GuardianSpecies.NOVA -> GuardianPalette(Color(0xFFE19AD8), Color(0xFF934E8A), Color(0xFFFFE5FB), Color(0xFFD76CCA), Color(0xFF32102E), Color(0xFFFFF0FD))
}

private fun DrawScope.drawGuardian(
    species: GuardianSpecies,
    stage: GuardianEngine.Stage,
    mood: GuardianEngine.Mood,
    palette: GuardianPalette,
    center: Offset,
    scale: Float,
    blink: Boolean
) {
    val radius = size.minDimension * 0.27f * scale
    drawCircle(palette.glow.copy(alpha = 0.22f), radius * 1.28f, center)

    when (species) {
        GuardianSpecies.LUMI -> drawLumi(center, radius, palette)
        GuardianSpecies.MOSS -> drawMoss(center, radius, palette)
        GuardianSpecies.ORBIT -> drawOrbit(center, radius, palette)
        GuardianSpecies.EMBER -> drawEmber(center, radius, palette)
        GuardianSpecies.TIDE -> drawTide(center, radius, palette)
        GuardianSpecies.NOVA -> drawNova(center, radius, palette)
    }

    val eyeY = center.y - radius * 0.08f
    val eyeDistance = radius * 0.37f
    val eyeRadius = radius * 0.075f
    if (blink) {
        drawLine(palette.eye, Offset(center.x - eyeDistance - eyeRadius, eyeY), Offset(center.x - eyeDistance + eyeRadius, eyeY), strokeWidth = radius * 0.045f)
        drawLine(palette.eye, Offset(center.x + eyeDistance - eyeRadius, eyeY), Offset(center.x + eyeDistance + eyeRadius, eyeY), strokeWidth = radius * 0.045f)
    } else {
        drawCircle(palette.eye, eyeRadius, Offset(center.x - eyeDistance, eyeY))
        drawCircle(palette.eye, eyeRadius, Offset(center.x + eyeDistance, eyeY))
        drawCircle(Color.White.copy(alpha = 0.78f), eyeRadius * 0.34f, Offset(center.x - eyeDistance - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f))
        drawCircle(Color.White.copy(alpha = 0.78f), eyeRadius * 0.34f, Offset(center.x + eyeDistance - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f))
    }

    val mouthY = center.y + radius * 0.30f
    when (mood) {
        GuardianEngine.Mood.HAPPY, GuardianEngine.Mood.PROUD, GuardianEngine.Mood.PLAYFUL -> {
            drawArc(
                palette.eye,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.25f, mouthY - radius * 0.12f),
                size = Size(radius * 0.5f, radius * 0.28f),
                style = Stroke(radius * 0.055f)
            )
        }
        GuardianEngine.Mood.SLEEPY -> drawLine(palette.eye, Offset(center.x - radius * 0.13f, mouthY), Offset(center.x + radius * 0.13f, mouthY), strokeWidth = radius * 0.045f)
        GuardianEngine.Mood.CONCERNED -> drawArc(
            palette.eye,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.22f, mouthY),
            size = Size(radius * 0.44f, radius * 0.24f),
            style = Stroke(radius * 0.05f)
        )
        else -> drawArc(
            palette.eye,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.2f, mouthY - radius * 0.08f),
            size = Size(radius * 0.4f, radius * 0.2f),
            style = Stroke(radius * 0.045f)
        )
    }

    if (stage.ordinal >= GuardianEngine.Stage.YOUNG.ordinal) {
        drawCircle(palette.accent, radius * 0.09f, Offset(center.x, center.y - radius * 0.72f))
    }
    if (stage.ordinal >= GuardianEngine.Stage.COMPANION.ordinal) {
        drawArc(palette.accent, 200f, 140f, false, Offset(center.x - radius * 0.55f, center.y - radius * 0.62f), Size(radius * 1.1f, radius * 1.1f), style = Stroke(radius * 0.045f))
    }
}

private fun DrawScope.drawLumi(center: Offset, radius: Float, palette: GuardianPalette) {
    drawCircle(palette.body, radius, center)
    drawCircle(palette.bodyDark.copy(alpha = 0.32f), radius * 0.88f, Offset(center.x, center.y + radius * 0.12f))
    drawCircle(palette.body, radius * 0.8f, Offset(center.x, center.y - radius * 0.05f))
}

private fun DrawScope.drawMoss(center: Offset, radius: Float, palette: GuardianPalette) {
    drawCircle(palette.body, radius, center)
    val leaf = Path().apply {
        moveTo(center.x - radius * 0.38f, center.y - radius * 0.74f)
        quadraticTo(center.x - radius * 0.75f, center.y - radius * 1.08f, center.x - radius * 0.15f, center.y - radius * 1.02f)
        close()
    }
    drawPath(leaf, palette.accent)
    drawCircle(palette.bodyDark.copy(alpha = 0.25f), radius * 0.65f, Offset(center.x, center.y + radius * 0.2f))
}

private fun DrawScope.drawOrbit(center: Offset, radius: Float, palette: GuardianPalette) {
    drawCircle(palette.body, radius * 0.9f, center)
    drawOval(palette.accent.copy(alpha = 0.75f), Offset(center.x - radius * 1.28f, center.y - radius * 0.28f), Size(radius * 2.56f, radius * 0.56f), style = Stroke(radius * 0.1f))
    drawCircle(palette.glow, radius * 0.13f, Offset(center.x + radius * 0.92f, center.y - radius * 0.16f))
}

private fun DrawScope.drawEmber(center: Offset, radius: Float, palette: GuardianPalette) {
    val flame = Path().apply {
        moveTo(center.x, center.y - radius * 1.18f)
        cubicTo(center.x + radius * 0.9f, center.y - radius * 0.5f, center.x + radius, center.y + radius * 0.6f, center.x, center.y + radius)
        cubicTo(center.x - radius, center.y + radius * 0.6f, center.x - radius * 0.8f, center.y - radius * 0.45f, center.x, center.y - radius * 1.18f)
        close()
    }
    drawPath(flame, palette.body)
    drawCircle(palette.accent.copy(alpha = 0.55f), radius * 0.48f, Offset(center.x, center.y + radius * 0.27f))
}

private fun DrawScope.drawTide(center: Offset, radius: Float, palette: GuardianPalette) {
    val drop = Path().apply {
        moveTo(center.x, center.y - radius * 1.12f)
        cubicTo(center.x + radius * 0.8f, center.y - radius * 0.25f, center.x + radius, center.y + radius * 0.45f, center.x, center.y + radius)
        cubicTo(center.x - radius, center.y + radius * 0.45f, center.x - radius * 0.8f, center.y - radius * 0.25f, center.x, center.y - radius * 1.12f)
        close()
    }
    drawPath(drop, palette.body)
    drawCircle(palette.accent.copy(alpha = 0.32f), radius * 0.42f, Offset(center.x - radius * 0.26f, center.y - radius * 0.26f))
}

private fun DrawScope.drawNova(center: Offset, radius: Float, palette: GuardianPalette) {
    val points = 10
    val path = Path()
    repeat(points) { index ->
        val angle = Math.PI * 2 * index / points - Math.PI / 2
        val r = if (index % 2 == 0) radius * 1.05f else radius * 0.72f
        val point = Offset(center.x + (kotlin.math.cos(angle) * r).toFloat(), center.y + (kotlin.math.sin(angle) * r).toFloat())
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, palette.body)
    drawCircle(palette.accent.copy(alpha = 0.3f), radius * 0.55f, center)
}

private fun DrawScope.drawAmbient(snapshot: GuardianEngine.Snapshot, palette: GuardianPalette, phase: Float) {
    val count = 3 + snapshot.stage.ordinal
    repeat(count) { index ->
        val angle = (index.toFloat() / count) * Math.PI * 2 + phase * 0.5f
        val distance = size.minDimension * (0.34f + index * 0.015f)
        val point = Offset(
            size.width / 2f + (kotlin.math.cos(angle) * distance).toFloat(),
            size.height / 2f + (kotlin.math.sin(angle) * distance).toFloat()
        )
        drawCircle(palette.glow.copy(alpha = 0.45f), size.minDimension * (0.008f + (index % 2) * 0.004f), point)
    }
}

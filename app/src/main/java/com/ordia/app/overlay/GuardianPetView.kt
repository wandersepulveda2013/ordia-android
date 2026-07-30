package com.ordia.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.UserPreferences
import com.ordia.app.domain.GuardianEngine
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight animated overlay renderer. It intentionally avoids image assets and network access. */
class GuardianPetView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var preferences = UserPreferences()
    private var phase = 0f
    private var animationsAllowed = true

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2_200L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            if (animationsAllowed) invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        animator.start()
    }

    fun update(preferences: UserPreferences, animationsAllowed: Boolean = true) {
        this.preferences = preferences
        this.animationsAllowed = animationsAllowed && preferences.guardianAnimations && !preferences.reduceMotion
        val experience = GuardianEngine.effectiveExperience(
            derivedExperience = preferences.guardianExperience,
            persistedExperience = preferences.guardianExperience,
            bond = preferences.guardianBond
        )
        val stage = GuardianEngine.stageForExperience(experience)
        contentDescription = "${preferences.guardianName}, ${preferences.guardianSpecies.label}, etapa ${stage.label}"
        if (this.animationsAllowed) {
            when {
                animator.isPaused -> animator.resume()
                !animator.isStarted -> animator.start()
            }
        } else if (animator.isStarted && !animator.isPaused) {
            animator.pause()
        }
        invalidate()
    }

    fun celebrate() {
        if (!animationsAllowed) return
        animate().scaleX(1.16f).scaleY(1.16f).setDuration(160L).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(220L).start()
        }.start()
        invalidate()
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f + sin(phase * Math.PI).toFloat() * height * 0.035f
        val r = minOf(width, height) * 0.29f
        val palette = palette(preferences.guardianSpecies)

        paint.color = palette.glow
        paint.alpha = 58
        canvas.drawCircle(cx, cy, r * 1.35f, paint)
        paint.alpha = 255

        when (preferences.guardianSpecies) {
            GuardianSpecies.LUMI -> canvas.drawCircle(cx, cy, r, bodyPaint(palette.body))
            GuardianSpecies.MOSS -> drawMoss(canvas, cx, cy, r, palette)
            GuardianSpecies.ORBIT -> drawOrbit(canvas, cx, cy, r, palette)
            GuardianSpecies.EMBER -> drawEmber(canvas, cx, cy, r, palette)
            GuardianSpecies.TIDE -> drawTide(canvas, cx, cy, r, palette)
            GuardianSpecies.NOVA -> drawNova(canvas, cx, cy, r, palette)
        }

        val mood = moodFrom(preferences.guardianLastEvent)
        drawFace(canvas, cx, cy, r, palette.eye, mood)
        val stage = GuardianEngine.stageForExperience(
            GuardianEngine.effectiveExperience(
                derivedExperience = preferences.guardianExperience,
                persistedExperience = preferences.guardianExperience,
                bond = preferences.guardianBond
            )
        )
        if (stage.ordinal >= GuardianEngine.Stage.YOUNG.ordinal) {
            paint.color = palette.accent
            canvas.drawCircle(cx, cy - r * 0.78f, r * 0.09f, paint)
        }
        if (stage.ordinal >= GuardianEngine.Stage.COMPANION.ordinal) {
            stroke.color = palette.accent
            stroke.strokeWidth = r * 0.055f
            canvas.drawArc(RectF(cx - r * 0.62f, cy - r * 0.68f, cx + r * 0.62f, cy + r * 0.56f), 200f, 140f, false, stroke)
        }

        repeat(3 + stage.ordinal) { index ->
            val angle = Math.PI * 2 * index / (3 + stage.ordinal) + phase
            val d = r * 1.5f
            paint.color = palette.glow
            paint.alpha = 130
            canvas.drawCircle(cx + (cos(angle) * d).toFloat(), cy + (sin(angle) * d).toFloat(), r * 0.045f, paint)
        }
        paint.alpha = 255
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float, eyeColor: Int, mood: GuardianEngine.Mood) {
        paint.color = eyeColor
        val eyeY = cy - r * 0.08f
        val eyeDistance = r * 0.36f
        val eyeRadius = r * 0.075f
        if (mood == GuardianEngine.Mood.SLEEPY) {
            stroke.color = eyeColor
            stroke.strokeWidth = r * 0.055f
            canvas.drawLine(cx - eyeDistance - eyeRadius, eyeY, cx - eyeDistance + eyeRadius, eyeY, stroke)
            canvas.drawLine(cx + eyeDistance - eyeRadius, eyeY, cx + eyeDistance + eyeRadius, eyeY, stroke)
        } else {
            canvas.drawCircle(cx - eyeDistance, eyeY, eyeRadius, paint)
            canvas.drawCircle(cx + eyeDistance, eyeY, eyeRadius, paint)
        }

        stroke.color = eyeColor
        stroke.strokeWidth = r * 0.055f
        val mouth = RectF(cx - r * 0.25f, cy + r * 0.14f, cx + r * 0.25f, cy + r * 0.48f)
        when (mood) {
            GuardianEngine.Mood.CONCERNED -> canvas.drawArc(mouth, 190f, 160f, false, stroke)
            GuardianEngine.Mood.FOCUSED -> canvas.drawLine(cx - r * 0.15f, cy + r * 0.32f, cx + r * 0.15f, cy + r * 0.32f, stroke)
            else -> canvas.drawArc(mouth, 10f, 160f, false, stroke)
        }
    }

    private fun drawMoss(canvas: Canvas, cx: Float, cy: Float, r: Float, palette: Palette) {
        canvas.drawCircle(cx, cy, r, bodyPaint(palette.body))
        val leaf = Path().apply {
            moveTo(cx - r * 0.2f, cy - r * 0.78f)
            quadTo(cx - r * 0.78f, cy - r * 1.18f, cx + r * 0.05f, cy - r * 1.05f)
            close()
        }
        paint.color = palette.accent
        canvas.drawPath(leaf, paint)
    }

    private fun drawOrbit(canvas: Canvas, cx: Float, cy: Float, r: Float, palette: Palette) {
        canvas.drawCircle(cx, cy, r * 0.92f, bodyPaint(palette.body))
        stroke.color = palette.accent
        stroke.strokeWidth = r * 0.1f
        canvas.drawOval(RectF(cx - r * 1.3f, cy - r * 0.3f, cx + r * 1.3f, cy + r * 0.3f), stroke)
    }

    private fun drawEmber(canvas: Canvas, cx: Float, cy: Float, r: Float, palette: Palette) {
        val path = Path().apply {
            moveTo(cx, cy - r * 1.18f)
            cubicTo(cx + r * 0.95f, cy - r * 0.5f, cx + r, cy + r * 0.58f, cx, cy + r)
            cubicTo(cx - r, cy + r * 0.58f, cx - r * 0.9f, cy - r * 0.48f, cx, cy - r * 1.18f)
            close()
        }
        paint.color = palette.body
        canvas.drawPath(path, paint)
    }

    private fun drawTide(canvas: Canvas, cx: Float, cy: Float, r: Float, palette: Palette) {
        val path = Path().apply {
            moveTo(cx, cy - r * 1.15f)
            cubicTo(cx + r * 0.82f, cy - r * 0.24f, cx + r, cy + r * 0.45f, cx, cy + r)
            cubicTo(cx - r, cy + r * 0.45f, cx - r * 0.82f, cy - r * 0.24f, cx, cy - r * 1.15f)
            close()
        }
        paint.color = palette.body
        canvas.drawPath(path, paint)
    }

    private fun drawNova(canvas: Canvas, cx: Float, cy: Float, r: Float, palette: Palette) {
        val path = Path()
        repeat(10) { index ->
            val angle = Math.PI * 2 * index / 10 - Math.PI / 2
            val distance = if (index % 2 == 0) r * 1.06f else r * 0.73f
            val x = cx + (cos(angle) * distance).toFloat()
            val y = cy + (sin(angle) * distance).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.color = palette.body
        canvas.drawPath(path, paint)
    }

    private fun bodyPaint(color: Int): Paint = paint.apply { this.color = color; alpha = 255 }

    private fun moodFrom(event: String): GuardianEngine.Mood = when (event) {
        "task_complete", "habit_complete", "focus_complete", "progress", "evolve" -> GuardianEngine.Mood.PROUD
        "play" -> GuardianEngine.Mood.PLAYFUL
        "rest" -> GuardianEngine.Mood.SLEEPY
        "talk", "pet", "feed", "rename" -> GuardianEngine.Mood.HAPPY
        else -> GuardianEngine.Mood.CALM
    }

    private data class Palette(val body: Int, val accent: Int, val glow: Int, val eye: Int)
    private fun palette(species: GuardianSpecies): Palette = when (species) {
        GuardianSpecies.LUMI -> Palette(Color.rgb(255, 225, 155), Color.rgb(227, 167, 65), Color.rgb(255, 197, 87), Color.rgb(51, 37, 12))
        GuardianSpecies.MOSS -> Palette(Color.rgb(151, 193, 132), Color.rgb(78, 116, 68), Color.rgb(133, 185, 115), Color.rgb(23, 35, 19))
        GuardianSpecies.ORBIT -> Palette(Color.rgb(175, 168, 237), Color.rgb(99, 85, 179), Color.rgb(142, 124, 240), Color.rgb(23, 19, 44))
        GuardianSpecies.EMBER -> Palette(Color.rgb(255, 158, 101), Color.rgb(204, 76, 39), Color.rgb(255, 113, 69), Color.rgb(53, 20, 12))
        GuardianSpecies.TIDE -> Palette(Color.rgb(118, 199, 220), Color.rgb(40, 119, 140), Color.rgb(86, 184, 212), Color.rgb(11, 41, 49))
        GuardianSpecies.NOVA -> Palette(Color.rgb(225, 154, 216), Color.rgb(147, 78, 138), Color.rgb(215, 108, 202), Color.rgb(50, 16, 46))
    }
}

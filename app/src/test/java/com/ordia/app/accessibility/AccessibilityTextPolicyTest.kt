package com.ordia.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la política de privacidad de la captura avanzada de pantalla.
 *
 * Cubren ORD-035 (nunca procesar campos password/masked) y ORD-034
 * (recursión acotada al explorar árboles de nodos), además del umbral
 * mínimo de texto para evitar ruido.
 */
class AccessibilityTextPolicyTest {

    // --- ORD-035: campos sensibles ---

    @Test
    fun passwordSourceIsNeverProcessedEvenWithLongText() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText("A".repeat(100), isPasswordSource = true))
    }

    @Test
    fun emptyPasswordSourceIsNeverProcessed() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText("", isPasswordSource = true))
    }

    @Test
    fun nullPasswordSourceIsNeverProcessed() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText(null, isPasswordSource = true))
    }

    @Test
    fun passwordNodeIsMarkedSensitive() {
        assertTrue(AccessibilityTextPolicy.isSensitiveNode(isPassword = true))
        assertFalse(AccessibilityTextPolicy.isSensitiveNode(isPassword = false))
    }

    // --- Umbral mínimo de texto ---

    @Test
    fun textShorterThanMinimumIsDiscarded() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText("hola", isPasswordSource = false))
    }

    @Test
    fun blankTextIsDiscarded() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText("   ", isPasswordSource = false))
    }

    @Test
    fun nullTextIsDiscarded() {
        assertFalse(AccessibilityTextPolicy.shouldProcessText(null, isPasswordSource = false))
    }

    @Test
    fun textAtMinimumLengthIsProcessed() {
        assertTrue(AccessibilityTextPolicy.shouldProcessText("A".repeat(8), isPasswordSource = false))
    }

    @Test
    fun longTextIsProcessed() {
        assertTrue(
            AccessibilityTextPolicy.shouldProcessText(
                "Preparar la presentación para el comité del viernes",
                isPasswordSource = false
            )
        )
    }

    // --- ORD-034: profundidad acotada ---

    @Test
    fun canDescendAtRoot() {
        assertTrue(AccessibilityTextPolicy.canDescend(0))
    }

    @Test
    fun canDescendUntilOneBelowMaxDepth() {
        assertTrue(AccessibilityTextPolicy.canDescend(AccessibilityTextPolicy.MAX_NODE_DEPTH - 1))
    }

    @Test
    fun cannotDescendAtMaxDepth() {
        assertFalse(AccessibilityTextPolicy.canDescend(AccessibilityTextPolicy.MAX_NODE_DEPTH))
    }

    @Test
    fun cannotDescendBeyondMaxDepth() {
        assertFalse(AccessibilityTextPolicy.canDescend(AccessibilityTextPolicy.MAX_NODE_DEPTH + 1))
        assertFalse(AccessibilityTextPolicy.canDescend(1_000))
    }

    @Test
    fun maxDepthIsReasonable() {
        // 10 niveles es suficiente para UI normales y corta árboles patológicos.
        assertTrue(AccessibilityTextPolicy.MAX_NODE_DEPTH in 5..15)
    }

    // --- Límite de longitud ---

    @Test
    fun maxTextLengthIsBounded() {
        assertTrue(AccessibilityTextPolicy.MAX_TEXT_LENGTH in 1_000..10_000)
    }
}

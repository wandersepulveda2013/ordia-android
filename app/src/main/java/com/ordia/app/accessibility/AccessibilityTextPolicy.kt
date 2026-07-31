package com.ordia.app.accessibility

/**
 * Política de extracción de texto para la captura avanzada de pantalla.
 *
 * Lógica pura, sin dependencias de Android, para poder probarla en JVM.
 * El servicio de accesibilidad delega aquí las decisiones de privacidad:
 * - Nunca procesar campos password/masked (ORD-035).
 * - Descartar textos demasiado cortos para reducir ruido y batería.
 * - Recursión acotada al explorar árboles de nodos (ORD-034).
 */
object AccessibilityTextPolicy {

    /** Longitud máxima de texto que se envía al motor contextual. */
    const val MAX_TEXT_LENGTH = 4_000

    /** Longitud mínima para considerar que un texto merece análisis. */
    const val MIN_TEXT_LENGTH = 8

    /** Profundidad máxima al recorrer nodos de accesibilidad. */
    const val MAX_NODE_DEPTH = 10

    /**
     * Un nodo marcado como password (o cualquier campo enmascarado) nunca
     * debe procesarse, aunque el sistema no lo redacte.
     */
    fun isSensitiveNode(isPassword: Boolean): Boolean = isPassword

    /**
     * ¿El texto de un evento merece análisis?
     * Descarta campos sensibles y textos demasiado cortos o vacíos.
     */
    fun shouldProcessText(text: String?, isPasswordSource: Boolean): Boolean {
        if (isPasswordSource) return false
        val length = text?.trim()?.length ?: return false
        return length >= MIN_TEXT_LENGTH
    }

    /**
     * ¿Se puede descender un nivel más en el árbol de nodos?
     * La recursión se corta en MAX_NODE_DEPTH para evitar árboles patológicos.
     */
    fun canDescend(depth: Int): Boolean = depth < MAX_NODE_DEPTH
}

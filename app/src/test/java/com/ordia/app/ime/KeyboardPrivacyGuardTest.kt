package com.ordia.app.ime

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del guardián de privacidad del IME:
 * - Campos sensibles (password, numberPassword, date/time) siempre ignorados.
 * - Aplicaciones bloqueadas por privacidad (bancos, autenticadores) ignoradas.
 * - Patrones "No detectar" hasheados sin texto en claro y normalizados.
 */
class KeyboardPrivacyGuardTest {

    // ── Campos sensibles ────────────────────────────────────────────────────

    @Test
    fun textPassword_isSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun visiblePassword_isSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun webPassword_isSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun numberPassword_isSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun datetime_isSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_DATETIME
        assertTrue(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun normalText_isNotSensitive() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyboardPrivacyGuard.isSensitiveInputType(inputType))
    }

    @Test
    fun emptyInputType_isNotSensitive() {
        assertFalse(KeyboardPrivacyGuard.isSensitiveInputType(0))
    }

    // ── Debería ignorar (sensibilidad + paquete) ─────────────────────────────

    @Test
    fun sensitiveField_shouldIgnore_evenWithSafePackage() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(KeyboardPrivacyGuard.shouldIgnore(inputType, "com.example.app"))
    }

    @Test
    fun blockedBankPackage_shouldIgnore() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertTrue(KeyboardPrivacyGuard.shouldIgnore(inputType, "com.bbva"))
    }

    @Test
    fun blockedPackage_withPrefix_shouldIgnore() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT
        assertTrue(KeyboardPrivacyGuard.shouldIgnore(inputType, "com.banorte.mx"))
    }

    @Test
    fun unknownPackage_shouldNotIgnore() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyboardPrivacyGuard.shouldIgnore(inputType, "com.ordia.app"))
    }

    @Test
    fun nullPackage_shouldNotIgnoreSensibleText() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyboardPrivacyGuard.shouldIgnore(inputType, null))
    }

    @Test
    fun unknownPackage_isNotAllowedUntilExplicitOptIn() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyboardPrivacyGuard.isAnalysisAllowed(inputType, "com.example.app", emptySet()))
        assertTrue(KeyboardPrivacyGuard.isAnalysisAllowed(inputType, "com.example.app", setOf("com.example.app")))
    }

    @Test
    fun blockedPackage_cannotBeAllowed() {
        val inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyboardPrivacyGuard.isAnalysisAllowed(inputType, "com.bbva", setOf("com.bbva")))
    }

    @Test
    fun sensitiveHint_isIgnored() {
        val inputType = EditorInfo.TYPE_CLASS_NUMBER
        assertTrue(KeyboardPrivacyGuard.shouldIgnore(inputType, "com.example.app", fieldHint = "Código OTP"))
    }

    // ── Normalización de tokens ──────────────────────────────────────────────

    @Test
    fun normalizeTokens_lowercasesAndJoins() {
        assertEquals("pagar a juan 500 pesos",
            KeyboardPrivacyGuard.normalizeTokens("Pagar a Juan: 500 pesos!"))
    }

    @Test
    fun normalizeTokens_ignoresBlankTokens() {
        assertEquals("hola mundo", KeyboardPrivacyGuard.normalizeTokens("Hola,   mundo"))
    }

    @Test
    fun normalizeTokens_emptyInput() {
        assertEquals("", KeyboardPrivacyGuard.normalizeTokens(""))
    }

    // ── Hash sin texto en claro ──────────────────────────────────────────────

    @Test
    fun sha256Hex_is64HexChars() {
        val hash = KeyboardPrivacyGuard.sha256Hex("pagar")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun sha256Hex_isDeterministic() {
        assertEquals(
            KeyboardPrivacyGuard.sha256Hex("No detectes esta frase"),
            KeyboardPrivacyGuard.sha256Hex("No detectes esta frase")
        )
    }

    @Test
    fun sha256Hex_differsByContent() {
        assertFalse(
            KeyboardPrivacyGuard.sha256Hex("pagar") ==
                KeyboardPrivacyGuard.sha256Hex("recordar")
        )
    }

    @Test
    fun ignoredPatternHash_neverContainsPlainText() {
        val secret = "Mi contraseña super secreta 12345"
        val normalized = KeyboardPrivacyGuard.normalizeTokens(secret)
        val hash = KeyboardPrivacyGuard.sha256Hex(normalized)
        // Lo que se persiste es el hash; ni el texto original ni el normalizado
        // (legible) pueden reconstruirse desde él.
        assertFalse(hash.contains(secret, ignoreCase = true))
        assertFalse(hash == normalized)
        // El hash es hexadecimal puro, sin letras del texto original.
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun bufferCap_isBounded() {
        assertTrue(KeyboardPrivacyGuard.MAX_BUFFER_CHARS <= 4096)
    }
}

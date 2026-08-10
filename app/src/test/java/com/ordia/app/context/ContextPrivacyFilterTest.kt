package com.ordia.app.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del filtro de privacidad previo del pipeline contextual (ORD-005):
 * - Paquetes bloqueados (banca, autenticadores, gestores de contraseñas, apps médicas).
 * - Campos de entrada sensibles (password, OTP, etc.).
 * - Contenido sensible por patrones.
 *
 * El filtro es un objeto JVM puro (sin APIs de Android), por lo que estas
 * pruebas son unitarias y deterministas.
 */
class ContextPrivacyFilterTest {

    private fun event(
        text: String = "Recordar revisar el correo",
        sourcePackage: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) = ContextEvent(
        source = ContextCaptureSource.SCREEN_ADVANCED,
        rawText = text,
        timestampMs = 1_000L,
        sourcePackage = sourcePackage,
        metadata = metadata
    )

    // ── Paquetes bloqueados ────────────────────────────────────────────────

    @Test
    fun bankPackage_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.bbva")))
    }

    @Test
    fun bankPackage_withPrefix_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.banorte.mx")))
    }

    @Test
    fun authenticatorPackage_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.authy")))
    }

    @Test
    fun passwordManagerPackage_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.bitwarden")))
    }

    @Test
    fun medicalPackage_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.health.provider")))
    }

    @Test
    fun ownPackage_notBlocked() {
        assertFalse(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "com.ordia.app")))
    }

    @Test
    fun nullPackage_withSafeText_notBlocked() {
        assertFalse(ContextPrivacyFilter.shouldBlock(event(sourcePackage = null)))
    }

    // ── Campos de entrada sensibles ────────────────────────────────────────

    @Test
    fun passwordInputType_blocked() {
        val event = event(metadata = mapOf("inputType" to "textPassword"))
        assertTrue(ContextPrivacyFilter.shouldBlock(event))
    }

    @Test
    fun otpInputType_blocked() {
        val event = event(metadata = mapOf("inputType" to "numberPassword"))
        assertTrue(ContextPrivacyFilter.shouldBlock(event))
    }

    @Test
    fun normalInputType_notBlocked() {
        val event = event(metadata = mapOf("inputType" to "text"))
        assertFalse(ContextPrivacyFilter.shouldBlock(event))
    }

    // ── Contenido sensible ─────────────────────────────────────────────────

    @Test
    fun passwordContent_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "mi contraseña es secreta")))
    }

    @Test
    fun bankAccountContent_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "número de cuenta 1234 5678")))
    }

    @Test
    fun cardNumberContent_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "el número de tarjeta es 4111 1111")))
    }

    @Test
    fun validSpacedCardNumber_withoutLabel_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "4111 1111 1111 1111")))
    }

    @Test
    fun nakedOtp_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "482913")))
    }

    @Test
    fun pemPrivateKey_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "-----BEGIN PRIVATE KEY-----")))
    }

    @Test
    fun seedPhraseCue_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "Mi frase semilla es uno dos tres")))
    }

    @Test
    fun genericBankingPackageFragment_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(sourcePackage = "org.example.mobilebanking")))
    }

    @Test
    fun selfHarmContent_blocked() {
        assertTrue(ContextPrivacyFilter.shouldBlock(event(text = "me siento mal, ideas de hacerme daño")))
    }

    @Test
    fun normalTask_notBlocked() {
        assertFalse(ContextPrivacyFilter.shouldBlock(event(text = "Recordar comprar leche y pan")))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Test
    fun isPackageBlocked_prefixMatch() {
        assertTrue(ContextPrivacyFilter.isPackageBlocked("com.scotiabank.mx"))
    }

    @Test
    fun isPackageBlocked_noMatch() {
        assertFalse(ContextPrivacyFilter.isPackageBlocked("com.example.app"))
    }

    @Test
    fun isSensitiveInputType_detectsPassword() {
        assertTrue(ContextPrivacyFilter.isSensitiveInputType("textVisiblePassword"))
        assertFalse(ContextPrivacyFilter.isSensitiveInputType("textEmailAddress"))
    }
}

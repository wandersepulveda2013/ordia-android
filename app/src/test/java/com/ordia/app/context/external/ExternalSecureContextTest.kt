package com.ordia.app.context.external

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica pura de seguridad del contexto externo (ORD-018):
 * - Paquete de origen sensible bloquea la tarjeta externa (prefijo, no igualdad).
 * - Paquete null/desconocido no bloquea (el pipeline contextual ya filtró).
 * - Exclusiones del usuario se suman a la lista fija.
 * - Títulos interpretados con contenido sensible se detectan.
 */
class ExternalSecureContextTest {

    // ── Paquete de origen ───────────────────────────────────────────────────

    @Test
    fun bankPackage_withSuffix_isSecure() {
        assertTrue(ExternalSecureContext.isSecurePackage("com.bbva.mx"))
    }

    @Test
    fun authenticatorPackage_isSecure() {
        assertTrue(ExternalSecureContext.isSecurePackage("com.authy"))
    }

    @Test
    fun passwordManagerPackage_isSecure() {
        assertTrue(ExternalSecureContext.isSecurePackage("com.bitwarden"))
    }

    @Test
    fun medicalPackage_isSecure() {
        assertTrue(ExternalSecureContext.isSecurePackage("com.health.provider"))
    }

    @Test
    fun ownPackage_isNotSecure() {
        assertFalse(ExternalSecureContext.isSecurePackage("com.ordia.app"))
    }

    @Test
    fun unknownPackage_isNotSecure() {
        assertFalse(ExternalSecureContext.isSecurePackage("com.example.app"))
    }

    @Test
    fun nullPackage_isNotSecure() {
        assertFalse(ExternalSecureContext.isSecurePackage(null))
    }

    @Test
    fun excludedUserApp_isSecure() {
        val excluded = setOf("com.example.banking")
        assertTrue(ExternalSecureContext.isSecurePackage("com.example.banking", excludedApps = excluded))
    }

    @Test
    fun excludedUserApp_byPrefix_isSecure() {
        val excluded = setOf("com.example")
        assertTrue(ExternalSecureContext.isSecurePackage("com.example.superapp", excludedApps = excluded))
    }

    @Test
    fun excludedUserApp_doesNotAffectOthers() {
        val excluded = setOf("com.example.banking")
        assertFalse(ExternalSecureContext.isSecurePackage("com.ordia.app", excludedApps = excluded))
    }

    // ── Títulos sensibles ───────────────────────────────────────────────────

    @Test
    fun titleWithPassword_isSensitive() {
        assertTrue(ExternalSecureContext.isSensitiveTitle("Recordar mi contraseña del banco"))
    }

    @Test
    fun titleWithToken_isSensitive() {
        assertTrue(ExternalSecureContext.isSensitiveTitle("Copiar el token de verificación"))
    }

    @Test
    fun titleWithCardNumber_isSensitive() {
        assertTrue(ExternalSecureContext.isSensitiveTitle("Anotar numero de tarjeta nueva"))
    }

    @Test
    fun normalTaskTitle_isNotSensitive() {
        assertFalse(ExternalSecureContext.isSensitiveTitle("Comprar leche y pan"))
    }

    @Test
    fun emptyTitle_isNotSensitive() {
        assertFalse(ExternalSecureContext.isSensitiveTitle(""))
    }

    // ── Ser/deserialización de sourcePackage ────────────────────────────────

    @Test
    fun suggestion_carriesSourcePackage() {
        val s = ExternalSuggestion(
            id = "src-pkg",
            confirmationId = "conf",
            kind = com.ordia.app.context.ContextIntentKind.TASK,
            title = "Comprar leche",
            source = com.ordia.app.context.ContextCaptureSource.KEYBOARD,
            sourcePackage = "com.whatsapp"
        )
        assertTrue(s.sourcePackage == "com.whatsapp")
    }
}

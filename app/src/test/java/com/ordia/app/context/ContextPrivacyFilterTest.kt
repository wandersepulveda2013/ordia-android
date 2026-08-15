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

    // ── CLABE interbancaria (MX): no usa Luhn, debe bloquearse por su propio checksum ──

    @Test
    fun nakedValidClabe_blocked() {
        // CLABE real con checksum válido, sin palabra "clabe"/"cuenta": antes se fugaba.
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("032180000118359719"))
    }

    @Test
    fun spacedValidClabe_blocked() {
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("032 180 0001 1835 9719"))
    }

    @Test
    fun clabeInSentence_blocked() {
        assertTrue(
            ContextPrivacyFilter.containsSensitiveContent(
                "transfiere a esta cuenta 032180000118359719 antes del cierre"
            )
        )
    }

    @Test
    fun invalidClabeChecksum_notBlocked() {
        // 18 dígitos pero checksum inválido: no es CLABE, no se sobrelbloquea.
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("032180000118359710"))
    }

    @Test
    fun randomEighteenDigits_notBlocked() {
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("123456789012345678"))
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
    fun connectionStringWithCredentials_blocked() {
        // c.298: cadenas de conexion con user:pass@ embebidas (devops por SMS).
        // Antes del fix escapaban al gate de lectura y se persistian como contexto.
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("te paso la cadena: postgres://reportes:Verde2024@10.0.0.5/prod"))
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("conexion mongodb://admin:S3cr3tP4ss@db.host.com:27017/prod"))
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("usa mysql://root:toor@10.0.0.5:3306/db"))
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("redis://default:redispassword@cache.internal:6379"))
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("amqp://guest:guest@rabbitmq:5672/vhost"))
        assertTrue(ContextPrivacyFilter.containsSensitiveContent("https://admin:SuperSecret@api.service.io/data"))
    }

    @Test
    fun plainUrlWithoutCredentials_notBlocked() {
        // c.298: URLs normales (sin user:pass@) no deben bloquearse (falso positivo
        // romperia la captura de enlaces legitimos).
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("mira https://example.com/articulo interesante"))
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("el sitio es http://ordia.app no te lo pierdas"))
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("enviame el link de https://docs.ejemplo.com/guia"))
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("descarga de https://github.com/usuario/repo"))
        // URL con puerto, sin userinfo: no hay credencial -> no bloquear.
        assertFalse(ContextPrivacyFilter.containsSensitiveContent("servidor en https://api.ejemplo.com:8443/v1/status"))
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

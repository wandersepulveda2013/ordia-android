package com.ordia.app.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas de IntelligenceSafetyGate (c.361).
 *
 * Regresiones cubiertas:
 * - Falsos positivos: el gate anterior bloqueaba cualquier número de 13-19
 *   dígitos SIN validar Luhn, lo que descartaba tareas legítimas que
 *   mencionaban referencias/facturas/IMEI largos (pérdida de captura).
 *   c.303 eliminó ese patrón crudo de los gates canónicos; c.361 alinea
 *   este gate con la fuente única SensitiveSecretPatterns.
 * - Falsos negativos: el gate anterior no delegaba a SensitiveSecretPatterns,
 *   así que dejaba pasar claves PEM, tokens de servicio y CURP que el gate
 *   canónico sí detectaba (brecha de privacidad).
 */
class IntelligenceSafetyGateTest {

    private fun blocked(text: String) =
        IntelligenceSafetyGate.evaluate(text) == PrivacyResult.BLOCKED

    // --- Falsos positivos corregidos: números largos no válidos como tarjeta ---

    @Test
    fun referenciaDePagoLargaNoLuhnNoSeBloquea() {
        assertEquals(false, blocked("paga la referencia 1234567890123456"))
    }

    @Test
    fun facturaDe13DigitosNoLuhnNoSeBloquea() {
        assertEquals(false, blocked("el número de factura es 1234567890123"))
    }

    @Test
    fun imeiDe15DigitosNoLuhnNoSeBloquea() {
        assertEquals(false, blocked("mi IMEI es 123456789012345"))
    }

    @Test
    fun numeroDeSeguimientoMuyLargoNoSeBloquea() {
        assertEquals(false, blocked("guía 1234567890123456789012345"))
    }

    @Test
    fun conversacionSobreBancoSinSecretoNoSeBloquea() {
        assertEquals(false, blocked("recuérdame llamar al banco sobre mi cuenta"))
    }

    @Test
    fun recordatorioDeCambiarContrasenaSinValorNoSeBloquea() {
        // Sin valor adyacente: no es un secreto real; el gate canónico también lo deja pasar.
        assertEquals(false, blocked("recuérdame cambiar mi contraseña esta semana"))
    }

    // --- Verdaderos positivos: secretos reales sí se bloquean ---

    @Test
    fun panLuhnValidoSeBloquea() {
        assertEquals(true, blocked("mi tarjeta es 4111111111111111"))
    }

    @Test
    fun clavePrivadaPemSeBloquea() {
        assertEquals(true, blocked("guarda esto -----BEGIN RSA PRIVATE KEY-----"))
    }

    @Test
    fun curpSeBloquea() {
        assertEquals(true, blocked("mi CURP es GOME850101HDFLRN09"))
    }

    @Test
    fun pinConValorSeBloquea() {
        assertEquals(true, blocked("mi pin es 1234"))
    }

    @Test
    fun otpConValorSeBloquea() {
        assertEquals(true, blocked("el código de seguridad es 482917"))
    }

    @Test
    fun contrasenaConValorSeBloquea() {
        assertEquals(true, blocked("mi contraseña es secreta123"))
    }

    // --- Moderación temática (permanece) ---

    @Test
    fun contenidoViolentoSeBloquea() {
        assertEquals(true, blocked("recuérdame matar el proceso del servidor"))
    }

    @Test
    fun textoVacioSeBloquea() {
        assertEquals(true, blocked("   "))
    }
}

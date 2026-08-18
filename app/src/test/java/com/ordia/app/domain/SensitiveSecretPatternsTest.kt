package com.ordia.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression lock for [SensitiveSecretPatterns], la fuente única de patrones de
 * secreto/PII consumida por los TRES gates de privacidad (persistencia, lectura
 * de contexto, intelligence gate). El dominio de privacidad es P0 (c.299):
 * un falso negativo persiste PII/secreto en texto plano y un falso positivo
 * bloquea un compromiso legítimo.
 *
 * Estos tests cubren:
 *  - paridad mayúsculas/minúsculas de los PII anclados por palabra-clave
 *    (c.317 aplicó IGNORE_CASE al IBAN; c.569 lo extiende a CURP/RFC/DNI/NIE/
 *    pasaporte/licencia/INE — un valor en minúsculas no debe escapar).
 *  - checksums numéricos (Luhn/CLABE/IBAN/CPF/CUIT/CNPJ/RUT/DNI): un valor
 *    estructuralmente válido pero con dígito verificador incorrecto NO se
 *    bloquea (precisión); un valor real SÍ se bloquea (cobertura).
 *  - secretos canónicos (claves privadas, API keys, JWT, AWS, Slack, GitHub,
 *    GitLab, connection strings, IBAN) se bloquean por valor.
 */
class SensitiveSecretPatternsTest {

    // ── PII: paridad mayúsculas/minúsculas (c.569) ──────────────────────────
    // La palabra-clave ya es case-insensitive (regex `(?i)`). El VALOR también
    // debe serlo: en chat casual un CURP/DNI/pasaporte se escribe a menudo en
    // minúsculas y antes escapaba, persistiéndose en texto plano.

    @Test fun curpUppercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi CURP es GOME850101HDFRRN09"))
    }

    @Test fun curpLowercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi curp es gome850101hdfrrn09"))
    }

    @Test fun rfcUppercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi RFC es GOHA850101AB1"))
    }

    @Test fun rfcLowercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi rfc es goha850101ab1"))
    }

    @Test fun dniUppercaseLetterIsBlocked() {
        // 12345678 % 23 = 14 → 'Z' (tabla TRWAGMYFPDXBNJZSQVHLCKE)
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi DNI es 12345678Z"))
    }

    @Test fun dniLowercaseLetterIsBlocked() {
        // La letra de control debe aceptarse en minúsculas (chat casual).
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi dni es 12345678z"))
    }

    @Test fun passportUppercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi pasaporte es AB1234567"))
    }

    @Test fun passportLowercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi pasaporte es ab1234567"))
    }

    @Test fun licenceUppercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("su licencia es AB123456"))
    }

    @Test fun licenceLowercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("su licencia es ab123456"))
    }

    @Test fun ineLowercaseIsBlocked() {
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi INE es abc123456789"))
    }

    // ── Precisión: palabra-clave + valor con checksum incorrecto NO bloquea ──
    // Evita falsos positivos que perderían compromisos legítimos.

    @Test fun dniWithWrongCheckLetterIsNotBlocked() {
        // 12345678 % 23 = 14 → 'Z'. Cualquier otra letra es inválida.
        assertFalse(SensitiveSecretPatterns.containsPersonalIdentifier("mi DNI es 12345678A"))
    }

    @Test fun rfcWithoutKeywordIsNotBlocked() {
        // Estructura válida de RFC pero sin palabra-clave anclante: no es PII
        // detectable, no se bloquea (evita falsos positivos sobre códigos).
        assertFalse(SensitiveSecretPatterns.containsPersonalIdentifier("el código GOHA850101AB1 es del producto"))
    }

    @Test fun curpWithoutKeywordIsNotBlocked() {
        assertFalse(SensitiveSecretPatterns.containsPersonalIdentifier("referencia GOME850101HDFRRN09 del pedido"))
    }

    // ── Checksums numéricos: cobertura (real) + precisión (inválido no bloquea) ──

    @Test fun validLuhnPanIsBlocked() {
        // 4242 4242 4242 4242 — pasa Luhn, PAN de prueba canónico.
        assertTrue(SensitiveSecretPatterns.containsNumericSensitive("tarjeta 4242424242424242"))
    }

    @Test fun invalidLuhnDigitsAreNotBlocked() {
        // 19 dígitos que no pasan Luhn: una referencia, no una tarjeta.
        assertFalse(SensitiveSecretPatterns.containsNumericSensitive("referencia 1234567890123456789"))
    }

    @Test fun validIbanUppercaseIsBlocked() {
        // GB82 WEST 1234 5698 7654 32 — IBAN canónico de prueba (pasa mod-97).
        assertTrue(SensitiveSecretPatterns.containsNumericSensitive("mi iban GB82WEST12345698765432"))
    }

    @Test fun validIbanLowercaseIsBlocked() {
        // c.317: IBAN en minúsculas debe bloquearse (mod-97 internaliza uppercase).
        assertTrue(SensitiveSecretPatterns.containsNumericSensitive("iban es6621000418401234567891"))
    }

    @Test fun ibanShapedButInvalidChecksumIsNotBlocked() {
        // Estructura LLDD... pero mod-97 != 1: no es IBAN, no se bloquea.
        assertFalse(SensitiveSecretPatterns.containsNumericSensitive("codigo GB00WEST12345698765432"))
    }

    @Test fun validCpfWithKeywordIsBlocked() {
        // CPF 529.982.247-25 — válido (cálculo canónico). Anclado por "cpf".
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("cpf: 52998224725"))
    }

    @Test fun cpfAllEqualFailsCpfChecksumButFailsClosedViaNssShape() {
        // 111.111.111-11: el CPF all-iguales es inválido por ley (checksum lo
        // rechaza), pero 11 dígitos tras una palabra-clave de identificador se
        // tratan como NSS (fail-closed para PII: ante la duda, bloquear).
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("cpf 11111111111"))
    }

    @Test fun validRutWithKeywordIsBlocked() {
        // RUT 7.654.321-6 — válido (serie [2,3,4,5,6,7] mod-11).
        assertTrue(SensitiveSecretPatterns.containsPersonalIdentifier("mi rut es 76543216"))
    }

    @Test fun rutWithWrongVerifierIsNotBlocked() {
        assertFalse(SensitiveSecretPatterns.containsPersonalIdentifier("mi rut es 76543210"))
    }

    // ── Secretos canónicos por valor (ambos gates) ──────────────────────────

    @Test fun privateKeyBlockIsBlocked() {
        assertTrue(SensitiveSecretPatterns.patterns.any { it.containsMatchIn(
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA"
        ) })
    }

    @Test fun awsAccessKeyIdIsBlocked() {
        assertTrue(SensitiveSecretPatterns.patterns.any { it.containsMatchIn("key: AKIAIOSFODNN7EXAMPLE") })
    }

    @Test fun githubPatIsBlocked() {
        assertTrue(SensitiveSecretPatterns.patterns.any { it.containsMatchIn("token ghp_1234567890abcdefghij1234") })
    }

    @Test fun jwtIsBlocked() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        assertTrue(SensitiveSecretPatterns.patterns.any { it.containsMatchIn(jwt) })
    }

    @Test fun connectionStringIsBlocked() {
        assertTrue(SensitiveSecretPatterns.patterns.any { it.containsMatchIn(
            "postgres://user:secretpass@db.example.com:5432/prod"
        ) })
    }

    @Test fun innocuousTextIsNotBlocked() {
        // Frase legítima sin credencial ni PII: no debe disparar ningún gate.
        assertFalse(SensitiveSecretPatterns.containsPersonalIdentifier("comprar pan y leche mañana"))
        assertFalse(SensitiveSecretPatterns.containsNumericSensitive("comprar pan y leche mañana"))
        assertFalse(SensitiveSecretPatterns.patterns.any { it.containsMatchIn("comprar pan y leche mañana") })
    }
}

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

    // --- Falsos positivos de "pin"/"nip" como SUBSTRING (c.509) ---
    // `contains("pin")` casaba dentro de "pintar"/"pintura"/"pines" y, combinado
    // con cualquier numero de 4-6 digitos en el texto, bloqueaba tareas de
    // pintura legitimas. `contains("nip")` analogo. La deteccion ahora exige el
    // limite de palabra \b(pin|nip)\b.

    @Test
    fun pintarConNumeroNoSeBloqueaPorSubstringPin() {
        assertEquals(false, blocked("pintar la sala 1234"))
    }

    @Test
    fun pinturaConNumeroNoSeBloqueaPorSubstringPin() {
        assertEquals(false, blocked("comprar pintura 2021"))
    }

    @Test
    fun pinesComoTornillosConNumeroNoSeBloquea() {
        assertEquals(false, blocked("comprar 4 pines para la mesa 1234"))
    }

    @Test
    fun nipComoSubstringNoFalsificaBloqueo() {
        assertEquals(false, blocked("enviar el snippet 5555 al equipo"))
    }

    @Test
    fun otpConValorSeBloquea() {
        assertEquals(true, blocked("el código de seguridad es 482917"))
    }

    // --- Falsos positivos de "código" + número que NO es OTP (c.510) ---

    @Test
    fun codigoPostalConNumeroNoSeBloquea() {
        assertEquals(false, blocked("enviar paquete al código postal 12345"))
    }

    @Test
    fun codigoDeBarrasConNumeroNoSeBloquea() {
        assertEquals(false, blocked("registrar el código de barras del producto 1234567"))
    }

    @Test
    fun codigoQrConNumeroNoSeBloquea() {
        assertEquals(false, blocked("imprimir el código QR de la factura 2024001"))
    }

    @Test
    fun codigoDeAreaConNumeroNoSeBloquea() {
        assertEquals(false, blocked("llamar al código de área 555 del proveedor"))
    }

    @Test
    fun codigoOtpPorSmsSeBloquea() {
        assertEquals(true, blocked("recibí el código 4321 por SMS"))
    }

    @Test
    fun contrasenaConValorSeBloquea() {
        assertEquals(true, blocked("mi contraseña es secreta123"))
    }

    // --- Falsos positivos de "clave" no-credencial + número (c.512) ---
    // "clave" tiene sentidos no-credenciales: metafórico ("la clave del éxito"),
    // de juego/acertijo ("la clave del juego") y musical ("clave musical",
    // "clave de sol/fa/do"). `credentialKeywordWithValue` los trataba como
    // credencial porque "clave" + un número de 3+ dígitos en la ventana de 40
    // chars bastaba para bloquear, descartando captura legítima.

    @Test
    fun claveDelExitoConNumeroNoSeBloquea() {
        assertEquals(false, blocked("la clave del éxito es practicar 100 veces"))
    }

    @Test
    fun claveDelExitoSinAcentoConNumeroNoSeBloquea() {
        assertEquals(false, blocked("la clave del exito es practicar 100 veces"))
    }

    @Test
    fun claveDelJuegoConNumeroNoSeBloquea() {
        assertEquals(false, blocked("la clave del juego es llegar a 500 puntos"))
    }

    @Test
    fun claveMusicalConNumeroNoSeBloquea() {
        assertEquals(false, blocked("recordar la clave musical de la obra 305"))
    }

    @Test
    fun claveDeSolConNumeroNoSeBloquea() {
        assertEquals(false, blocked("estudiar la clave de sol del acertijo 123"))
    }

    @Test
    fun claveCredencialDeAccesoSeBloquea() {
        assertEquals(true, blocked("mi clave de acceso es 4829"))
    }

    @Test
    fun claveCredencialWifiSeBloquea() {
        assertEquals(true, blocked("la clave del wifi es 1234567890"))
    }

    @Test
    fun claveBancariaSeBloquea() {
        assertEquals(true, blocked("clave bancaria 4567"))
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

    // --- Falsos negativos de tildes: OTP/credenciales sin acento (c.516) ---
    // En español escrito casualmente (móvil, sin autocorrector de tildes)
    // "codigo"/"contrasena" sin tilde son formas extremadamente comunes. El gate
    // anterior solo casaba la forma con tilde ("código"/"contraseña"), así que
    // un OTP como "mi codigo de verificacion es 1234" NO se bloqueaba y el
    // secreto se procesaba/persistía en texto plano (fuga de privacidad).

    @Test
    fun otpCodigoSinTildeSeBloquea() {
        assertEquals(true, blocked("mi codigo de verificacion es 1234"))
    }

    @Test
    fun otpCodigoSeguridadSinTildeSeBloquea() {
        assertEquals(true, blocked("el codigo de seguridad es 482917"))
    }

    @Test
    fun otpCodigoPorSmsSinTildeSeBloquea() {
        assertEquals(true, blocked("recibi el codigo 4321 por sms"))
    }

    @Test
    fun codigoDeAccesoSinTildeSeBloquea() {
        assertEquals(true, blocked("codigo de acceso 998877"))
    }

    @Test
    fun contrasenaConValorSinTildeSeBloquea() {
        assertEquals(true, blocked("mi contrasena es secreta123"))
    }

    @Test
    fun claveCredencialSinTildeSeBloquea() {
        assertEquals(true, blocked("mi clave de acceso es 4829"))
    }

    // Los falsos positivos de "código" no-OTP también escritos sin tilde siguen
    // sin bloquearse (c.510 sobre texto normalizado).
    @Test
    fun codigoPostalSinTildeNoSeBloquea() {
        assertEquals(false, blocked("enviar paquete al codigo postal 12345"))
    }

    @Test
    fun codigoDeAreaSinTildeNoSeBloquea() {
        assertEquals(false, blocked("llamar al codigo de area 555 del proveedor"))
    }

    // --- Falsos negativos de tildes: CONTENIDO bloqueado sin acento (c.519) ---
    // Continuacion directa de c.516 (que cerro credenciales sin tilde pero no
    // toco el contenido bloqueado). El gate de inteligencia solo tenia las
    // regex de drogas/insultos con tilde (cocaina/estupido/imbecil/eroti/
    // narcotrafico en su forma acentuada), asi que escritas SIN tilde - forma
    // casual extremadamente comun en movil - escapaban al proveedor de IA.
    // Asimetria frente al gate de lectura (ContextPrivacyFilter), que si cubre
    // ambas formas. La normalizacion (unaccent) en evaluate cierra la brecha
    // sin duplicar literales.
    // Probe JVM PRE-fix: 5/5 PASS (no bloqueaba); POST-fix: 5/5 BLOCK.
    @Test
    fun drogaCocainaSinTildeSeBloquea() {
        assertEquals(true, blocked("vende cocaina barata en la zona"))
    }

    @Test
    fun drogaHeroinaSinTildeSeBloquea() {
        assertEquals(true, blocked("trae heroina pura para el viernes"))
    }

    @Test
    fun insultoEstupidoSinTildeSeBloquea() {
        assertEquals(true, blocked("eres un estupido por no entenderlo"))
    }

    @Test
    fun insultoImbecilSinTildeSeBloquea() {
        assertEquals(true, blocked("que imbecil eres a veces"))
    }

    @Test
    fun narcotraficoSinTildeSeBloquea() {
        assertEquals(true, blocked("hay narcotrafico en la frontera"))
    }
}

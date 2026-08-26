package com.ordia.app.context

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1198 (este lado; re-frase de la candidata (a) «hacer la transferencia» de
 * MI auditoría c.1197): RECHAZADA POR DISEÑO DE PRIVACIDAD — hermano de la
 * candidata «cambiar la contraseña» c.1029. La sonda POST de c.1198 midió la
 * causa raíz REAL con un DEBUG vunero-ordinario (no el gate de keyword c.751
 * atribuido inicialmente): `ContextPrivacyFilter.blockedContentPatterns`
 * descarta `\btransferencia\b` ANTES de clasificar (finanzas domésticas,
 * hermana de depósito/retiro/saldo/estado-de-cuenta). Doctrina c.1029
 * («cambiar la contraseña» RECHAZADA): un piso pre-filtro que distinga
 * acción de secreto arriesga fuga en el título capturado («hacer la
 * transferencia a abc123» → título persistiría el secreto). Anti-overreach:
 * preferimos NO capturar una tarea legítima ocasional a persistir
 * contenido financiero sensible.
 *
 * La fuga PLURAL quedó CERRADA en este mismo ciclo: el ancla `\b` permitía
 * "transferencias" (la coda 's' rompe la frontera) — rendija de protección,
 * no intención capturable. Pin: toda variante (singular/plural/determinante)
 * debe quedar NULL DELIBERADO. Guard c.616 TDD RED→GREEN: RED era la fase
 * plural HIT (gaps 7→1 tras la reversión del piso c.1198), GREEN cierra
 * el plural fijando el bloqueo.
 */
class ContextIntentEngineTransferenciaPrivacyPinTest {

    private fun analyze(text: String) =
        ContextIntentEngine.analyze(
            ContextEvent(
                source = ContextCaptureSource.NOTIFICATION,
                rawText = text,
                timestampMs = 1_700_000_000_000L
            )
        )

    // --- PINS NULL POR PRIVACIDAD (deliberado; doctrina c.1029) ---

    @Test
    fun `singular hacer la transferencia NULL por privacidad`() {
        assertNull(analyze("hacer la transferencia"))
    }

    @Test
    fun `singular con ancla temporal NULL por privacidad`() {
        assertNull(analyze("hacer la transferencia al casero el lunes"))
    }

    @Test
    fun `determinante indefinido NULL por privacidad`() {
        assertNull(analyze("hacer una transferencia al banco mañana"))
    }

    @Test
    fun `posesivo NULL por privacidad`() {
        assertNull(analyze("hacer mi transferencia del mes"))
    }

    @Test
    fun `demostrativo NULL por privacidad`() {
        assertNull(analyze("hacer esta transferencia antes del cierre"))
    }

    @Test
    fun `brevedad sin articulo NULL por privacidad`() {
        assertNull(analyze("hacer transferencia mañana"))
    }

    @Test
    fun `plural NULL por privacidad - rendija cerrada`() {
        assertNull(analyze("hacer las transferencias del mes el día 1"))
    }

    @Test
    fun `plural simple NULL por privacidad`() {
        assertNull(analyze("hacer transferencias"))
    }

    @Test
    fun `deposito plural NULL por privacidad - misma rendija`() {
        assertNull(analyze("depósitos recibidos"))
    }

    @Test
    fun `estados de cuenta plural NULL por privacidad`() {
        assertNull(analyze("estados de cuenta se descargan hoy"))
    }
}

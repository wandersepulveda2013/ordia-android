package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1107: candidata (h) complementaria de la fila DECIMOTERCERA del
 * BACKLOG (registrada c.1102-bis) — la AVERSIÓN con infinitivo
 * («odio/detesto/aborrezco + infinitivo») se persistía como
 * compromiso FIRME. Medida PRE (sonda efímera `/tmp/probe1107/Probe.kt`,
 * motor real vía `tools/run_probe.sh`, HEAD `85ddffa`): 12 capturas
 * como falso compromiso — «odio ir al dentista» APPOINTMENT 0.67,
 * «odio ir al médico» APPOINTMENT 0.67, «detesto llamar al banco»
 * CALL 0.57, «aborrezco hacer la compra» SHOPPING 0.45, «odio sacar
 * al perro» HOUSEHOLD 0.45, «detesto ir al gimnasio» EXERCISE 0.59,
 * «aborrezco limpiar la cocina» HOUSEHOLD 0.45, «odio llamar a
 * mamá» CALL 0.57, «detesto limpiar la cocina» HOUSEHOLD 0.45,
 * «odio ir al dentista mañana» APPOINTMENT 0.77 con dueAt (¡la
 * aversión arrastraba hasta fecha!), «aborrezco sacar al perro»
 * HOUSEHOLD 0.45, «ODIO IR AL DENTISTA» APPOINTMENT 0.67. La
 * captura pasiva persistía EXACTAMENTE lo contrario de la actitud
 * del usuario (contamina What Now; hermano del guard de plan
 * negado c.1009/c.1044/c.1091 — misma clase P1/P2 precisión).
 * Fix mínimo (hermano de [planWrapperIsNegated]): NUEVO guard
 * [aversionGoverns] que descarta TODA la clasificación cuando un
 * verbo de aversión de 1ª persona en presente de indicativo
 * («odio», «detesto», «aborrezco») gobierna un infinitivo.
 * Anti-overreach (alcance fijado por los pines de esta clase):
 * (1) «no odio …» NO casa (lookbehind `(?<!no )` — pineada
 * byte-idéntica: la negación de la aversión no es aversión, y su
 * estado previo — HIT APPOINTMENT — queda INTACTO como pin);
 * (2) sustantivo «odio los lunes» NULL estructural (el guard exige
 * infinitivo);
 * (3) condicional «odiaría …» FUERA (lateral registrada, pineada
 * byte-idéntica);
 * (4) 3ª persona «mi madre odia …» FUERA (lateral registrada,
 * pineada byte-idéntica);
 * (5) los compromisos reales hermanos (mismos verbos subordinados
 * sin aversión) capturan INTACTOS.
 */
class ContextIntentAversionGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Aversión + infinitivo: TODA la frase se descarta ----

    @Test
    fun `odio ir al dentista no captura`() {
        assertNull(analyze("odio ir al dentista"))
    }

    @Test
    fun `odio ir al medico no captura`() {
        assertNull(analyze("odio ir al médico"))
    }

    @Test
    fun `detesto llamar al banco no captura`() {
        assertNull(analyze("detesto llamar al banco"))
    }

    @Test
    fun `aborrezco hacer la compra no captura`() {
        assertNull(analyze("aborrezco hacer la compra"))
    }

    @Test
    fun `odio sacar al perro no captura`() {
        assertNull(analyze("odio sacar al perro"))
    }

    @Test
    fun `detesto ir al gimnasio no captura`() {
        assertNull(analyze("detesto ir al gimnasio"))
    }

    @Test
    fun `aborrezco limpiar la cocina no captura`() {
        assertNull(analyze("aborrezco limpiar la cocina"))
    }

    @Test
    fun `odio llamar a mama no captura`() {
        assertNull(analyze("odio llamar a mamá"))
    }

    @Test
    fun `detesto limpiar la cocina no captura`() {
        assertNull(analyze("detesto limpiar la cocina"))
    }

    @Test
    fun `odio ir al dentista con fecha no captura`() {
        assertNull(analyze("odio ir al dentista mañana"))
    }

    @Test
    fun `aborrezco sacar al perro no captura`() {
        assertNull(analyze("aborrezco sacar al perro"))
    }

    @Test
    fun `aversion en mayusculas no captura`() {
        assertNull(analyze("ODIO IR AL DENTISTA"))
    }

    // ---- Pines NULL preexistentes (coherencia estructural) ----

    @Test
    fun `odio pagar la luz sigue null`() {
        assertNull(analyze("odio pagar la luz"))
    }

    @Test
    fun `odio los lunes sigue null`() {
        assertNull(analyze("odio los lunes"))
    }

    @Test
    fun `odio como sustantivo sigue null`() {
        assertNull(analyze("qué odio tiene el dentista"))
    }

    @Test
    fun `odio cuando sin infinitivo sigue null`() {
        assertNull(analyze("odio cuando suena la alarma"))
    }

    // ---- Pines BYTE-IDÉNTICOS (laterales registradas, FUERA de alcance) ----

    @Test
    fun `no odio ir al dentista queda byte identico`() {
        // (?<!no ): la negación de la aversión no es aversión. Pin del
        // estado PREVIO (HIT) — lateral documentada, no empeora ni mejora.
        assertNotNull(analyze("no odio ir al dentista"))
    }

    @Test
    fun `odiaria ir al dentista queda byte identico`() {
        // Condicional FUERA de alcance (lateral registrada): pin del HIT previo.
        assertNotNull(analyze("odiaría ir al dentista"))
    }

    @Test
    fun `aversion de tercera persona queda byte identica`() {
        // 3ª persona FUERA de alcance (lateral registrada): pin del HIT previo.
        assertNotNull(analyze("mi madre odia ir al médico"))
    }

    // ---- Regresiones: compromisos reales hermanos INTACTOS ----

    @Test
    fun `ir al medico captura intacto`() {
        val i = analyze("ir al médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
    }

    @Test
    fun `llamar a mama captura intacto`() {
        val i = analyze("llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `hacer la compra captura intacto`() {
        val i = analyze("hacer la compra esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.SHOPPING, i!!.kind)
    }

    @Test
    fun `sacar al perro captura intacto`() {
        val i = analyze("sacar al perro esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `pagar la luz captura intacto`() {
        val i = analyze("pagar la luz el día 5")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `ir al gimnasio captura intacto`() {
        val i = analyze("ir al gimnasio mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i!!.kind)
    }

    @Test
    fun `limpiar la cocina captura intacto`() {
        val i = analyze("limpiar la cocina hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `pedir hora al dentista captura intacto`() {
        val i = analyze("pedir hora al dentista mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }
}

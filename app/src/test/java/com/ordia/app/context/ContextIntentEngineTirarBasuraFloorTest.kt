package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1179 (auditoría vigésima este lado, sonda persistida
 * `tools/probe/TwentiethClassHouseholdProbe.kt` — clase VIGÉSIMA QUEHACER
 * DOMÉSTICO): extensión aditiva del piso HOUSEHOLD_TRASH_FLOOR (c.717) con
 * el verbo hermano «tirar». Medido NULL antes del fix: «tirar la basura
 * esta noche» era el ÚNICO gap de 14 candidatas (13/14 ya capturaban por
 * cobertura heredada). «tirar» es bivalente (la puerta/piedras/penalti/
 * de la cuerda) igual que «sacar» (dinero/fotos/perro), así se acota al
 * MISMO objeto «basura» (lockstep piso↔plantilla↔guard-negación, lección
 * c.616/c.717; CERO keywords nuevas — gate c.751: el piso 0.45 da
 * MINIMUM_CONFIDENCE por sí solo). Acotado deliberado (una forma por
 * ciclo): los objetos no-basura quedan FUERA (candidatas propias). La
 * negación compuesta «no voy a tirar la basura» estaba NULL correcta
 * antes vía el guard global c.1009 y sigue NULL tras el fix.
 */
class ContextIntentEngineTirarBasuraFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas directas (piso) ---

    @Test
    fun tirarLaBasuraEstaNoche_capturesHouseholdWithDueAt() {
        val intent = analyze("tirar la basura esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tirar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tirarLaBasuraSinFecha_capturesHouseholdWithoutDueAt() {
        val intent = analyze("tirar la basura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tirar la basura", intent.title)
    }

    @Test
    fun tirarBasuraTrasAcuse_capturesHousehold() {
        val intent = analyze("vale, tirar la basura mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tirar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tirarBasuraConPrefijoTemporal_tituloSinResiduo() {
        val intent = analyze("esta noche tirar la basura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tirar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdameTirarLaBasura_governsTaskWrapper() {
        // Envolvente: TASK gobierna y conserva el verbo (lección c.652).
        val intent = analyze("recuérdame tirar la basura mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tirar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles NULL: anti-overreach (piso restringido al objeto) ---

    @Test
    fun tirarBasuraNegada_noCaptura() {
        assertNull(analyze("no tirar la basura esta noche"))
    }

    @Test
    fun tirarBasuraNegadaConPrefijoTemporal_noCaptura() {
        assertNull(analyze("esta noche no tirar la basura"))
    }

    @Test
    fun tirarBasuraNegadaCompuesta_noCaptura() {
        // Guard global c.1009 (planWrapperIsNegated): la compuesta ya era
        // NULL correcta antes del fix y debe seguir NULL (sonda G1 c.1179).
        assertNull(analyze("no voy a tirar la basura esta noche"))
    }

    @Test
    fun tirarBasuraConDuda_noCaptura() {
        assertNull(analyze("quizá tirar la basura mañana"))
    }

    @Test
    fun tirarPiedras_noCaptura() {
        // Objeto distinto: «tirar» no es piso de posición libre doméstica.
        assertNull(analyze("tirar piedras mañana"))
    }

    @Test
    fun tirarUnPenalti_noCaptura() {
        assertNull(analyze("tirar un penalti esta tarde"))
    }

    @Test
    fun tirarLaPuerta_noCaptura() {
        assertNull(analyze("tirar la puerta esta noche"))
    }

    // --- Regresiones de la familia (verbo hermano intacto) ---

    @Test
    fun sacarLaBasura_regresionIntacta() {
        val intent = analyze("sacar la basura esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun sacarLaBasuraNegada_regresionIntacta() {
        assertNull(analyze("no sacar la basura esta noche"))
    }

    @Test
    fun recuerdameSacarLaBasura_regresionIntacta() {
        val intent = analyze("recuérdame sacar la basura mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }
}

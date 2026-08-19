package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.717 (P1 olvido silencioso en captura pasiva — SEGUNDA clase de verbos
 * cotidianos de gestión, forma 7/14: "sacar la basura" descubierto por sonda
 * persistente `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; UNA forma
 * por ciclo, doctrina anti-overreach): "sacar la basura esta noche" se
 * DESCARTABA (analyze → NULL) — la faena doméstica cotidiana por excelencia
 * con fecha explícita, olvidada por Ordía (P1). Kind decidido en
 * deliberación: HOUSEHOLD (misma familia que "regar las plantas" c.645 y los
 * verbos domésticos `limpiar/lavar/...`), en deliberación contra TASK. El
 * piso nuevo se acota AL OBJETO "basura" (como `ERRAND_CARRY_FLOOR` acota a
 * vehículos/mantenimiento c.684): "sacar" suelto es demasiado genérico
 * (dinero/fotos/perro) para un piso de posición libre. Anti-overreach:
 * objeto restringido a "basura" (`\b` final: "basurilla" no casa), `(?<!no )`
 * bloquea la negada, duda (c.649) no captura; controles "sacar dinero"/
 * "sacar fotos" → NULL. Envolvente protegido: "recuérdame sacar la basura"
 * → TASK (guard en [WRAPPABLE_PATTERNS], lección c.652). Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineSacarBasuraFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "sacar la basura" es una faena doméstica inequívoca ---

    @Test
    fun sacarLaBasuraEstaNoche_capturesHouseholdWithDueAt() {
        val intent = analyze("sacar la basura esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun sacarLaBasuraSinFecha_capturesHouseholdWithoutDueAt() {
        val intent = analyze("sacar la basura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
    }

    @Test
    fun sacarBasuraTrasAcuse_capturesHousehold() {
        val intent = analyze("vale, sacar la basura mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun sacarBasuraConPrefijoTemporal_tituloSinResiduo() {
        val intent = analyze("esta noche sacar la basura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdameSacarLaBasura_governsTaskWrapper() {
        // Envolvente: TASK gobierna y conserva el verbo (lección c.652).
        val intent = analyze("recuérdame sacar la basura mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles NULL: anti-overreach (piso restringido al objeto) ---

    @Test
    fun sacarBasuraNegada_noCaptura() {
        assertNull(analyze("no sacar la basura esta noche"))
    }

    @Test
    fun sacarBasuraNegadaConPrefijoTemporal_noCaptura() {
        assertNull(analyze("esta noche no sacar la basura"))
    }

    @Test
    fun sacarBasuraConDuda_noCaptura() {
        assertNull(analyze("quizá sacar la basura mañana"))
    }

    @Test
    fun sacarDinero_noCaptura() {
        // Objeto distinto: "sacar" no es piso de posición libre doméstica.
        assertNull(analyze("sacar dinero mañana"))
    }

    @Test
    fun sacarFotos_noCaptura() {
        assertNull(analyze("sacar fotos mañana"))
    }
}
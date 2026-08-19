package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.718 (P1 olvido silencioso en captura pasiva — SEGUNDA clase de verbos
 * cotidianos de gestión, forma 8/14: "pasar por <destino>" descubierto por
 * sonda persistente `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711;
 * UNA forma por ciclo, doctrina anti-overreach): "pasar por el banco mañana"
 * se DESCARTABA (analyze → NULL) — el desplazamiento al lugar de trámite,
 * con fecha explícita, olvidado por Ordía (P1). Kind decidido en
 * deliberación: ERRAND (misma familia que el piso "ir a banco/correos/…"
 * c.647), en deliberación contra VISIT — donde vivía el keyword histórico
 * genérico "pasar por". El piso nuevo se acota a lugares de trámite (como
 * `ERRAND_CARRY_FLOOR` c.684 acota a vehículos/mantenimiento), así no
 * captura VISIT ("pasar por casa") ni destinos de ocio ("el parque").
 * Anti-overreach: negada, duda, pasado "pasé…", destino no-trámite → NULL;
 * envolvente "recuérdame" → TASK gobierna (doctrina c.613/lección c.652).
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEnginePasarPorFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "pasar por <lugar de trámite>" es diligencia inequívoca ---

    @Test
    fun pasarPorElBancoManana_capturesErrandWithDueAt() {
        val intent = analyze("pasar por el banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Pasar por el banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasarPorCorreos_varianteSinArticulo() {
        val intent = analyze("pasar por correos mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Pasar por correos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasarPorOficinaTrasAcuse_capturesErrand() {
        val intent = analyze("vale, pasar por la oficina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Pasar por la oficina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasarPorConPrefijoTemporal_tituloSinResiduo() {
        val intent = analyze("mañana pasar por el banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Pasar por el banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdamePasarPorElBanco_governsTaskWrapper() {
        // Envolvente: TASK gobierna y conserva el verbo (lección c.652).
        val intent = analyze("recuérdame pasar por el banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar por el banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles NULL: anti-overreach (piso restringido al destino) ---

    @Test
    fun pasarPorElBancoNegada_noCaptura() {
        assertNull(analyze("no pasar por el banco mañana"))
    }

    @Test
    fun pasarPorElBancoConDuda_noCaptura() {
        assertNull(analyze("quizá pasar por el banco mañana"))
    }

    @Test
    fun destinoNoTramite_noCaptura() {
        // "el parque" no es lugar de trámite: el piso acotado no captura.
        assertNull(analyze("pasar por el parque mañana"))
    }

    @Test
    fun pasadaPreterito_noCaptura() {
        assertNull(analyze("pasé por el banco ayer"))
    }
}

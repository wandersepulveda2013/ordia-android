package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1175 (P1 olvido silencioso en captura pasiva): lateral ABIERTA
 * (d-ter) de MI cierre c.1169 — futuro 1ª persona «haré la
 * mudanza», hermana del infinitivo «hacer la mudanza» (c.1169)
 * y del presente «hago la mudanza» (c.1171, ambas de la forma
 * C20 de la sonda persistida `tools/probe/FifteenthClassAdminProbe.kt`,
 * c.1132, clase DECIMOQUINTA burocracia/mudanza). PRE medido con
 * sonda efímera (motor real vía `tools/run_probe.sh`, HEAD base
 * `e3915fb2` post-marcador): 5/5 capturas NULL (pelada,
 * «el sábado», «del piso nuevo en octubre», prefijo
 * temporal «el lunes haré…», acuse «vale, haré…»),
 * guards 6/6 NULL (negación «no haré», pretérito «hice»,
 * subjuntivo-duda «quizá haga», 3ª persona «hará»,
 * condicional «haría», nominal), pines 6/6 (infinitivo c.1169,
 * presente c.1171, maleta c.715, curso c.1152, compra SHOPPING,
 * envolvente «recuérdame…»). El futuro de compromiso
 * («haré la mudanza en octubre») es la forma cotidiana de
 * anunciar un plan diferido propio; perderlo cuesta el dinero y las
 * semanas de siempre (camiones, ayudantes, entrega de llaves).
 *
 * Fix lockstep DOS puntos (lección c.616/c.751 — CERO keywords
 * nuevas): (1) extensión aditiva del piso «(hacer|hago|haré)
 * (det)? mudanzas?» en hasStrongTaskImperative (ancla
 * ^|acuse|temporal + lookbehind «no » heredados del piso
 * c.1169/c.1171); (2) misma extensión en la plantilla
 * matchHacerMudanza (verbo CAPTURADO: grafía preservada, doctrina
 * c.653, precedente c.903; residuo temporal lo depura sanitizeTitle).
 * Mecanismo verificado en código: el piso c.613 eleva el score con
 * maxOf(score, MINIMUM_CONFIDENCE) SIN exigir keyword — por eso
 * «haré» captura sin tocar ContextIntent (gate c.751 intacto).
 *
 * Kind TASK (gestión logística puntual con plazo, hermana del
 * infinitivo c.1169 y del presente c.1171). Determinista (regex),
 * sin random, sin IA fingida. Alcance: SOLO futuro 1ª persona
 * «haré»; subjuntivo «haga…» y 3ª persona «hará…»
 * quedan laterales documentadas (UNA forma por ciclo, doctrina
 * anti-overreach). Re-pin legítimo (precedente c.1035/c.1041/
 * c.1094/c.1171): este ciclo HABILITA deliberadamente la captura
 * pineada NULL en `ContextIntentEngineHacerMudanzaFloorTest`
 * (futuroHareMudanza_isNull, c.1169, pin correcto cuando se fijó)
 * y en `ContextIntentEngineHagoMudanzaFloorTest`
 * (futuroHareMudanza_isNull, c.1171, pin correcto cuando se fijó).
 */
class ContextIntentEngineHareMudanzaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «haré (la)? mudanza» es un plan diferido comprometido ---
    // Títulos/dueAt pineados tras medir POST con sonda efímera
    // (motor real, tools/run_probe.sh).

    @Test
    fun hareLaMudanzaSabado_capturesTaskWithDueAt() {
        val intent = analyze("haré la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Haré la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hareLaMudanzaDelPisoNuevoEnOctubre_capturesTask() {
        // Pin medido POST (sonda efímera, motor real): «en octubre»
        // no resuelve dueAt (hermano de «el fin de semana» c.1169/
        // c.1171) y sobrevive como residuo en el título.
        val intent = analyze("haré la mudanza del piso nuevo en octubre")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Haré la mudanza del piso nuevo en octubre", intent.title)
    }

    @Test
    fun elLunesHareLaMudanza_capturesTaskTemporalPrefix() {
        // Prefijo temporal: ruta de extractTitle hermana de
        // «el lunes hago la mudanza» (c.1171). Captura NUEVA
        // gracias a este piso (PRE: NULL).
        val intent = analyze("el lunes haré la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("El lunes haré la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hareLaMudanza_capturesTaskBare() {
        val intent = analyze("haré la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Haré la mudanza", intent.title)
    }

    @Test
    fun valeHareLaMudanza_capturesTaskAckPrefix() {
        val intent = analyze("vale, haré la mudanza del piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Haré la mudanza del piso", intent.title)
    }

    // --- Envolvente: candado c.613 (pin byte-idéntico, ya capturaba) ---

    @Test
    fun tengoQueHacerLaMudanza_remainsTaskWrapper() {
        val intent = analyze("tengo que hacer la mudanza del piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza del piso", intent.title)
    }

    // --- Guards (deben quedarse NULL) ---

    @Test
    fun negadaNoHareMudanza_isNull() {
        assertNull(analyze("no haré la mudanza esta semana"))
    }

    @Test
    fun pasadoHiceMudanza_isNull() {
        assertNull(analyze("hice la mudanza ayer"))
    }

    @Test
    fun dudaSubjuntivoHagaMudanza_isNull() {
        assertNull(analyze("quizá haga la mudanza en verano"))
    }

    @Test
    fun terceraPersonaHaraMudanza_isNull() {
        // Lateral documentada: UNA forma por ciclo.
        assertNull(analyze("él hará la mudanza mañana"))
    }

    @Test
    fun condicionalHariaMudanza_isNull() {
        assertNull(analyze("haría la mudanza si tuviera furgoneta"))
    }

    @Test
    fun nominalMudanzaDelPiso_isNull() {
        assertNull(analyze("la mudanza del piso nuevo será en octubre"))
    }

    // --- Regresiones (formas que YA capturan — pin byte-idéntico) ---

    @Test
    fun hacerLaMudanzaSabado_remainsTask() {
        // Piso c.1169 (infinitivo) intacto.
        val intent = analyze("hacer la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoLaMudanzaSabado_remainsTask() {
        // Piso c.1171 (presente 1ª persona) intacto.
        val intent = analyze("hago la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLaCompra_remainsShopping() {
        val intent = analyze("hacer la compra mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun hacerLaMaleta_remainsTask() {
        val intent = analyze("hacer la maleta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hacerDeberes_remainsStudy() {
        val intent = analyze("hacer deberes mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }
}

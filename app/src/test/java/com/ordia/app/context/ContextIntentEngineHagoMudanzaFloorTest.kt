package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1171 (P1 olvido silencioso en captura pasiva): lateral ABIERTA
 * de MI cierre c.1169 — presente 1ª persona «hago la mudanza»,
 * hermana del infinitivo «hacer la mudanza» (c.1169, forma C20 de la
 * sonda persistida `tools/probe/FifteenthClassAdminProbe.kt`, c.1132,
 * clase DECIMOQUINTA burocracia/mudanza). PRE medido con sonda
 * efímera (motor real vía `tools/run_probe.sh`, HEAD base
 * `ec8c5c21` post-marcador): 5/5 capturas NULL (pelada, «el
 * sábado», «del piso nuevo el fin de semana», prefijo
 * temporal «el lunes hago…», acuse «vale, hago…»),
 * guards 6/6 NULL (negación, pretérito «hice», subjuntivo-duda
 * «quizá haga», subjuntivo «haga», 3ª persona
 * «hace», nominal), pines 6/6 (infinitivo c.1169 con dueAt,
 * maleta c.715, curso c.1152, compra SHOPPING, envolvente
 * «tengo que…» c.613, deberes STUDY c.898). «el
 * sábado hago la mudanza» se DESCARTABA (analyze → NULL)
 * pese a llevar temporal explícito: «hacer» keyword TASK no
 * casa la 1ª persona «hago» y todo piso «hacer X» acotado
 * exige el infinitivo (curso c.1152, maleta c.827, deberes c.898,
 * check-in c.1140, mudanza c.1169). Olvido real: el presente de
 * compromiso («el sábado hago la mudanza») es la forma
 * cotidiana de fijar un plan con otra persona; la mudanza perdida
 * cuesta dinero y semanas (camiones, ayudantes, entrega de llaves).
 *
 * Fix lockstep DOS puntos (lección c.616/c.751 — CERO keywords
 * nuevas): (1) extensión aditiva del piso «(hacer|hago) (det)?
 * mudanzas?» en hasStrongTaskImperative (ancla ^|acuse|temporal +
 * lookbehind «no » heredados del piso c.1169); (2) misma extensión
 * en la plantilla matchHacerMudanza (grafía preservada, doctrina
 * c.653; residuo temporal lo depura sanitizeTitle). Mecanismo
 * verificado en código: el piso c.613 eleva el score con
 * maxOf(score, MINIMUM_CONFIDENCE) SIN exigir keyword — por eso
 * «hago» captura sin tocar ContextIntent (gate c.751 intacto).
 *
 * Kind TASK (gestión logística puntual con plazo, hermana del
 * infinitivo c.1169). Determinista (regex), sin random, sin IA
 * fingida. Alcance: SOLO presente 1ª persona «hago»; futuro
 * «haré…» y subjuntivo «haga…» quedan laterales
 * documentadas (UNA forma por ciclo, doctrina anti-overreach).
 */
class ContextIntentEngineHagoMudanzaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «hago (la)? mudanza» es un plan comprometido ---
    // Títulos/dueAt pineados tras medir POST con sonda efímera
    // (motor real, tools/run_probe.sh).

    @Test
    fun hagoLaMudanzaSabado_capturesTaskWithDueAt() {
        val intent = analyze("hago la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoLaMudanzaDelPisoNuevo_capturesTask() {
        // Pin medido POST (sonda efímera, motor real): «el fin de
        // semana» no resuelve dueAt (hermano de c.1169) y sobrevive
        // como residuo en el título.
        val intent = analyze("hago la mudanza del piso nuevo el fin de semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza del piso nuevo el fin de semana", intent.title)
    }

    @Test
    fun elLunesHagoLaMudanza_capturesTaskTemporalPrefix() {
        // Pin medido POST (sonda efímera, motor real): la ruta de
        // extractTitle conserva el prefijo temporal en el título
        // (dueAt sí resuelve), hermano de «mañana hacer la mudanza»
        // c.1169. Captura NUEVA gracias a este piso (PRE: NULL).
        val intent = analyze("el lunes hago la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("El lunes hago la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoLaMudanza_capturesTaskBare() {
        val intent = analyze("hago la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza", intent.title)
    }

    @Test
    fun valeHagoLaMudanza_capturesTaskAckPrefix() {
        val intent = analyze("vale, hago la mudanza del piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza del piso", intent.title)
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
    fun negadaNoHagoMudanza_isNull() {
        assertNull(analyze("no hago la mudanza"))
    }

    @Test
    fun pasadoHiceMudanza_isNull() {
        assertNull(analyze("hice la mudanza ayer"))
    }

    @Test
    fun dudaSubjuntivoHagaMudanza_isNull() {
        assertNull(analyze("quizá haga la mudanza el sábado"))
    }

    @Test
    fun subjuntivoHagaMudanza_isNull() {
        assertNull(analyze("haga la mudanza"))
    }

    @Test
    fun terceraPersonaHaceMudanza_isNull() {
        assertNull(analyze("él hace la mudanza el sábado"))
    }

    @Test
    fun nominalMudanzaDelPiso_isNull() {
        assertNull(analyze("la mudanza del piso nuevo"))
    }

    @Test
    fun futuroHareMudanza_isNull() {
        // Lateral documentada de c.1169: UNA forma por ciclo.
        assertNull(analyze("haré la mudanza en octubre"))
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

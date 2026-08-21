package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.827 (P1 olvido silencioso en captura pasiva — familia equipaje/viaje
 * doméstico). Renumerado de c.823 a c.827 por colisión con runs remotos.
 * doméstico nunca sondeada; una familia por ciclo, doctrina anti-overreach):
 * "hacer/preparar/meter la maleta" ("hacer la maleta esta noche") se
 * DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CaptureCoverageProbe.kt` c.822 + `/tmp/probe823/`, 16 casos):
 * las 7 capturas → NULL; los 7 guards → NULL; la envolvente "tengo que
 * hacer la maleta" ya capturaba (TASK 0.45, título limpio "Hacer la
 * maleta"). Fix: piso de TASK ACOTADO al objeto `maletas?` con los verbos
 * (hacer|preparar|meter) — misma doctrina objeto-anclada que "hacer copia
 * de seguridad" (c.774), porque "hacer"/"meter" son muy bivalentes (la
 * compra —SHOPPING c.758—, la cama —HOUSEHOLD c.728—, la pata/el gol) — +
 * plantilla de título "hacer la maleta"→"Hacer la maleta" que despoja el
 * acuse y el prefijo temporal (lección c.616, match arranca en el verbo;
 * el verbo se capitaliza desde el match — 3 alternativas, no fijable como
 * c.774 — y la grafía del objeto se preserva, doctrina c.653). Lockstep
 * keyword-OBJETO "maleta" en ContextIntentKind.TASK.keywords (lección
 * c.713/c.751/c.765; NO los verbos). Kind: TASK — hacer la maleta gobierna
 * el equipaje (preparación), no el desplazamiento (TRAVEL queda para
 * "viaje/vuelo/hotel"). Anti-overreach: `\s+...maletas?\b` exige el objeto,
 * `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el pasado
 * "hice/preparó/metí" no casa, la afirmación nominal "la maleta está
 * hecha" no casa (keyword sola = 0.12 < umbral), "meter la carta" (objeto
 * distinto) no casa. Plural idioma: "hacer las maletas". Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineMaletaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "hacer/preparar/meter la maleta" es una tarea clara ---

    @Test
    fun hacerLaMaletaEstaNoche_capturesTaskWithDueAt() {
        val intent = analyze("hacer la maleta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun prepararLaMaletaManana_capturesTaskWithDueAt() {
        val intent = analyze("preparar la maleta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Preparar la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun meterLaMaletaEnElCocheEstaNoche_capturesTaskWithDueAt() {
        val intent = analyze("meter la maleta en el coche esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Meter la maleta en el coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLasMaletasElViernes_pluralCapturesTaskWithDueAt() {
        val intent = analyze("hacer las maletas el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer las maletas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLaMaletaSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("hacer la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la maleta", intent.title)
    }

    @Test
    fun hacerLaMaletaTrasAcuse_capturesTask() {
        val intent = analyze("vale, hacer la maleta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLaMaletaTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy hacer la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- c.836: forma enclítica del piso («hacerme la maleta»), 1 de las 4
    // NULLs declarativas verificadas en `tools/probe/SixthClassEncliticProbe.kt`
    // (hermano c.834). Sufijo (me|te|se|nos) con ancla de objeto `maletas?`;
    // el título conserva el pronombre (precedente c.770 «Tomarme la pastilla»).

    @Test
    fun hacermeLaMaletaEstaNoche_encliticCapturesTaskWithDueAt() {
        val intent = analyze("hacerme la maleta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacerme la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun prepararteLaMaletaManana_encliticCapturesTaskWithDueAt() {
        val intent = analyze("prepararte la maleta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Prepararte la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerseLasMaletasElViernes_encliticPluralCapturesTask() {
        val intent = analyze("hacerse las maletas el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacerse las maletas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun prepararnosLaMaletaSinFecha_encliticCapturesTaskWithoutDueAt() {
        val intent = analyze("prepararnos la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Prepararnos la maleta", intent.title)
    }

    // --- Controles anti-overreach de la forma enclítica (NULL) ---

    @Test
    fun noHacermeLaMaleta_encliticNegatedStaysNull() {
        assertNull(analyze("no hacerme la maleta"))
    }

    @Test
    fun quizasHacermeLaMaleta_encliticConditionalStaysNull() {
        assertNull(analyze("quizá hacerme la maleta mañana"))
    }

    @Test
    fun meHiceLaMaletaAyer_encliticPastNarrativeStaysNull() {
        assertNull(analyze("me hice la maleta ayer"))
    }

    @Test
    fun hacermeUnFavor_encliticBivalentVerbDifferentObjectStaysNull() {
        assertNull(analyze("hacerme un favor mañana"))
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix: /tmp/probe823/MaletaRedProbe.kt) ---

    @Test
    fun noHacerLaMaleta_negatedStaysNull() {
        assertNull(analyze("no hacer la maleta"))
    }

    @Test
    fun quizasHacerLaMaleta_conditionalStaysNull() {
        assertNull(analyze("quizá hacer la maleta mañana"))
    }

    @Test
    fun hiceLaMaletaAyer_pastNarrativeStaysNull() {
        assertNull(analyze("hice la maleta ayer"))
    }

    @Test
    fun preparoLaMaletaAyer_pastNarrativeStaysNull() {
        assertNull(analyze("preparó la maleta ayer"))
    }

    @Test
    fun metiLaMaletaAyer_pastNarrativeStaysNull() {
        assertNull(analyze("metí la maleta en el coche ayer"))
    }

    @Test
    fun laMaletaEstaHecha_nounStatementStaysNull() {
        assertNull(analyze("la maleta está hecha"))
    }

    @Test
    fun meterLaCarta_differentObjectStaysNull() {
        assertNull(analyze("meter la carta en el buzón mañana"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando (PRE-fix:
    // TASK 0.45, título limpio) y "hacer la compra" sigue SHOPPING ---

    @Test
    fun tengoQueHacerLaMaleta_wrapperStillWins() {
        val intent = analyze("tengo que hacer la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la maleta", intent.title)
    }

    @Test
    fun hacerLaCompra_staysShopping() {
        val intent = analyze("hacer la compra mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }
}

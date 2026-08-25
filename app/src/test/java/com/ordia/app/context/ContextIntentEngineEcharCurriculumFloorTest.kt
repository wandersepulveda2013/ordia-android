package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1148 — candidata (a) FUERTE de la clase DECIMOSÉPTIMA (vida laboral,
 * sonda persistida `tools/probe/SeventeenthClassWorkProbe.kt` c.1147 del
 * hermano, C5): «echar el currículum en la oferta de infojobs». NULL PRE
 * medido con sonda efímera (motor real vía `tools/run_probe.sh`, base
 * `5a39f45`): 7/7 candidatas NULL (desnuda sin temporal, con «mañana»,
 * «esta semana», oferta con plazo, acuse «vale,», grafía sin tilde
 * «curriculo», sin artículo), 2/2 envolventes HIT por camino genérico
 * («tengo que…»/«recuérdame…» 0.45, título ya limpio), 8/8 guards NULL,
 * 4/4 regresiones HIT. Olvido silencioso P1: la oferta de empleo tiene
 * plazo — el olvido cuesta la oportunidad entera.
 *
 * Decisión de dominio: TASK (gestión administrativa SIN desplazamiento
 * explícito: echar el currículum se hace online; hermana de «enviar»
 * TASK c.692 y de «sellar el paro» TASK c.1143 — la doctrina ERRAND
 * c.842/c.862 gobierna solo el desplazamiento).
 *
 * Lockstep TRES puntos (lección c.616/c.751, hermano EXACTO de c.1143;
 * keyword-OBJETO como «gasolina» c.829 porque «echar» es bivalente —
 * echar agua/de menos/la culpa — y NO se añade):
 * (1) keyword-OBJETO «currículum»/«curriculo» en TASK; (2) piso NUEVO
 * «echar (el)? curr[ií]culum» en `hasStrongTaskImperative` junto al
 * piso «sellar el paro» c.1143 (ancla ^|acuse|temporal y guard
 * `(?<!no )` heredados de la familia); (3) plantilla hermana
 * matchEcharCurriculum en [extractTitle] (doctrina c.653: verbo
 * preservado, solo capitalización inicial).
 *
 * Acotado deliberado: «echar de menos…», «echar la carta», «echar
 * agua a las plantas» NULL — el objeto «currículum» es EXIGIDO por el
 * piso. Lateral ABIERTA (UNA por ciclo): «echar un currículum»
 * (indefinido), «mandar el currículum» (sinónimo).
 */
class ContextIntentEngineEcharCurriculumFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas «echar el currículum» ───────────────────────────

    @Test
    fun `echar el curriculum en oferta con plazo captura TASK`() {
        val intent = analyze("echar el currículum en la oferta de infojobs")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum en la oferta de infojobs", intent.title)
    }

    @Test
    fun `echar el curriculum manana captura TASK con titulo limpio`() {
        val intent = analyze("echar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `echar el curriculum esta semana captura TASK`() {
        val intent = analyze("echar el currículum esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `echar el curriculum sin fecha captura TASK sin dueAt`() {
        val intent = analyze("echar el currículum")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
        assertEquals(null, intent.dueAt)
    }

    @Test
    fun `acuse vale echar el curriculum captura TASK`() {
        val intent = analyze("vale, echar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
    }

    @Test
    fun `echar el curriculo sin tilde captura TASK`() {
        val intent = analyze("echar el curriculo en infojobs")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `echar curriculum sin articulo captura TASK`() {
        val intent = analyze("echar currículum en la oferta del mercadona")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // ─── Guards (NULL deliberado) ─────────────────────────────────

    @Test
    fun `negada no eches el curriculum no captura`() {
        assertNull(analyze("no eches el currículum todavía"))
    }

    @Test
    fun `duda no se si echar el curriculum no captura`() {
        assertNull(analyze("no sé si echar el currículum en esa oferta"))
    }

    @Test
    fun `duda quizas echar el curriculum no captura`() {
        assertNull(analyze("quizá echar el currículum mañana"))
    }

    @Test
    fun `pasado eche el curriculum no captura`() {
        assertNull(analyze("eché el currículum ayer"))
    }

    @Test
    fun `sustantivo el curriculum esta listo no captura`() {
        assertNull(analyze("el currículum está listo para enviar"))
    }

    @Test
    fun `bivalente echar de menos no captura`() {
        assertNull(analyze("echar de menos a los compañeros del trabajo"))
    }

    @Test
    fun `bivalente echar la carta no captura`() {
        assertNull(analyze("echar la carta al buzón"))
    }

    @Test
    fun `bivalente echar agua a las plantas no captura`() {
        assertNull(analyze("echar agua a las plantas"))
    }

    // ─── Regresiones (byte-idénticas) ─────────────────────────────

    @Test
    fun `echar gasolina sigue ERRAND c829`() {
        val intent = analyze("echar gasolina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
    }

    @Test
    fun `sellar el paro sigue TASK c1143`() {
        val intent = analyze("sellar el paro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
    }

    @Test
    fun `enviar el curriculum sigue TASK c692`() {
        val intent = analyze("enviar el currículum a la empresa mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el currículum a la empresa", intent.title)
    }

    @Test
    fun `pagar la luz sigue PAYMENT`() {
        val intent = analyze("pagar la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `envolvente tengo que echar el curriculum sigue TASK`() {
        val intent = analyze("tengo que echar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente recuerdame echar el curriculum sigue TASK`() {
        val intent = analyze("recuérdame echar el currículum el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
    }
}

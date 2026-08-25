package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1156: lateral (d) de la auditoría c.1132 (clase DECIMOQUINTA,
 * burocracia/administración; sonda persistida
 * `tools/probe/FifteenthClassAdminProbe.kt` C2/K3/K5/K6) —
 * «empadronarme en el nuevo piso este mes» medida NULL 4/4 en PRE con
 * sonda efímera sobre HEAD a1d8a643 (verbo reflexivo monosemántico sin
 * keyword: la notificación ni llegaba al análisis, gate c.751).
 * El padrón municipal tiene plazo: olvidarlo cuesta multas/recargos y
 * la pérdida de ayudas (vivienda, escuela). Olvido silencioso P1.
 *
 * Fix lockstep (hermano EXACTO de «cubrir el turno» c.1149 y
 * reflexivos c.1044/c.1115):
 *  1. keyword-VERB «empadronar» en ContextIntent (0.12 sola inerte <
 *     umbral; «empadronamiento» NO casa — «empadronar» no es subcadena
 *     de «empadronamiento»: rompe en -a- vs -ar).
 *  2. Piso reflexivo acotado «empadronar(me|te|se|nos|os)» en
 *     `hasStrongTaskImperative` (misma ancla ^|acuse|temporal y guard
 *     `(?<!no )`). Kind TASK: trámite SIN desplazamiento explícito
 *     (hermana de «sellar el paro» c.1143; la doctrina ERRAND
 *     c.842/c.862 solo gobierna el desplazamiento).
 *  3. Plantilla matchEmpadronar en `extractTitle` (lección c.616):
 *     el pronombre enclítico es parte del verbo (doctrina c.653).
 *
 * Alcance deliberado (UNA forma por ciclo, anti-overreach): SOLO el
 * reflexivo. «empadronar al niño» (terceros) y «hacer la mudanza»
 * (C20) quedan laterales NULL.
 *
 * Guards pineados NULL (medidos PRE): negada, pretérito «empadroné»,
 * subjuntivo-duda «quizá me empadronen», nominal «certificado de
 * empadronamiento», declarativa «el padrón abre», no-reflexiva
 * «empadronar al niño» y la lateral «hacer la mudanza».
 */
class ContextIntentEngineEmpadronarFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---------- captura (4/4 NULL en PRE) ----------

    @Test
    fun `empadronarme en el nuevo piso este mes captura TASK conservando la cola`() {
        val r = analyze("empadronarme en el nuevo piso este mes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.confidence >= 0.45f)
        // «este mes» no es residuo temporal depurable ni parsea a dueAt
        // (familia conocida de colas, medida en C9/R1/R2 de la sonda
        // c.1132); el título lo conserva.
        assertNull(r.dueAt)
        assertEquals("Empadronarme en el nuevo piso este mes", r.title)
    }

    @Test
    fun `empadronarme en el nuevo piso el lunes captura TASK con dueAt`() {
        val r = analyze("empadronarme en el nuevo piso el lunes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Empadronarme en el nuevo piso", r.title)
    }

    @Test
    fun `empadronarnos en el nuevo piso esta semana captura TASK en plural reflexivo`() {
        val r = analyze("empadronarnos en el nuevo piso esta semana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertNull(r.dueAt)
        assertEquals("Empadronarnos en el nuevo piso esta semana", r.title)
    }

    @Test
    fun `empadronarme desnudo captura TASK`() {
        val r = analyze("empadronarme")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
    }

    // ---------- guards NULL esperados (medidos en PRE, deben seguir NULL) ----------

    @Test
    fun `guard negada - no empadronarme no captura`() {
        assertNull(analyze("no empadronarme en el nuevo piso"))
    }

    @Test
    fun `guard preterito - ya me empadroné no captura`() {
        assertNull(analyze("ya me empadroné en el nuevo piso"))
    }

    @Test
    fun `guard subjuntivo duda - quizá me empadronen no captura`() {
        assertNull(analyze("quizá me empadronen en el nuevo piso"))
    }

    @Test
    fun `guard nominal - el certificado de empadronamiento no captura`() {
        assertNull(analyze("el certificado de empadronamiento"))
    }

    @Test
    fun `guard declarativa - el padrón municipal abre a las 9 no captura`() {
        assertNull(analyze("el padrón municipal abre a las 9"))
    }

    @Test
    fun `guard anti-overreach - empadronar al niño (no reflexiva) no captura`() {
        assertNull(analyze("empadronar al niño en el colegio"))
    }

    @Test
    fun `guard lateral - hacer la mudanza sigue NULL (ciclo aparte)`() {
        assertNull(analyze("hacer la mudanza del piso el fin de semana"))
    }

    // ---------- regresiones HIT esperadas (pines byte-idénticos) ----------

    @Test
    fun `regresion - sellar el paro sigue TASK (piso c1143 intacto)`() {
        val r = analyze("sellar el paro el día 4")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Sellar el paro el día 4", r.title)
    }

    @Test
    fun `regresion - dar de alta la luz sigue TASK (piso c1139 intacto)`() {
        val r = analyze("dar de alta la luz del piso nuevo mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Dar de alta la luz del piso nuevo", r.title)
    }

    @Test
    fun `regresion - renovar el pasaporte sigue TASK (c698 intacto)`() {
        val r = analyze("renovar el pasaporte este mes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Renovar el pasaporte este mes", r.title)
    }

    @Test
    fun `regresion - envolvente recordame empadronarme sigue TASK`() {
        val r = analyze("recuérdame empadronarme en el nuevo piso")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Empadronarme en el nuevo piso", r.title)
    }

    @Test
    fun `regresion - envolvente tengo que empadronarme sigue TASK`() {
        val r = analyze("tengo que empadronarme este mes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Empadronarme este mes", r.title)
    }
}

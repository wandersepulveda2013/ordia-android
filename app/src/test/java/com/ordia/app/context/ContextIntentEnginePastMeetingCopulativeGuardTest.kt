package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1142: lateral ABIERTA documentada del guard c.1138 — FALSO POSITIVO
 * P1 medido 8/8 (sonda efímera `/tmp/probe1142/Probe.kt`, HEAD base
 * `7f49cbb`): la copulativa en pretérito de una reunión YA celebrada
 * («la reunión de padres fue ayer», «ayer fue la reunión de padres»)
 * capturaba como MEETING 0.45 con dueAt — el hecho cumplido se persistía
 * como compromiso futuro con fecha (misma corrupción que c.1138 cerró
 * para el relato «fui a la reunión…»; las copulativas quedaron FUERA
 * como laterales documentadas en el KDoc de
 * [ContextIntentEngine.PAST_MEETING_NARRATIVE_PATTERN] — una forma por
 * ciclo, doctrina anti-overreach). Familia medida PRE: T1-T8 8/8 HIT
 * MEETING 0.45 (cópula pospuesta «…fue/era ayer|anteayer|anoche» y
 * antepuesta «ayer|anteayer|anoche fue/era la…»), pines P1-P8 HIT
 * correctos y guards G1-G5 en su estado heredado.
 * Fix: guard POSICIONAL hermano de [ContextIntentEngine.pastMeetingNarrativeGoverns]
 * ([ContextIntentEngine.pastMeetingCopulativeGoverns]) con DOS formas:
 *   (1) cópula POSPUESTA — «(fue|era) (ayer|anteayer|anoche)» DESPUÉS del
 *       match MEETING y en la MISMA cláusula (sin corte [.!?,;:] entre el
 *       fin del match y la cópula: el pin posicional c.1138 «…es mañana;
 *       fui a la del curso pasado» sigue capturando);
 *   (2) cópula ANTEPUESTA — «(ayer|anteayer|anoche) (fue|era)» con
 *       ADYACENCIA estricta de artículo/poseedor hasta el inicio del
 *       match (anti-overreach: «ayer fue un día largo, la reunión es
 *       mañana» NO gobierna — sigue HIT).
 * CERO keywords nuevas (doctrina c.862); CERO cambios en ContextIntent.kt.
 * Pines de alcance (byte-idénticos PRE):
 *   - nominal vencida «reunión de padres ayer» SIGUE HIT (doctrina
 *     c.5369 — el guard exige cópula pretérito, no «ayer» a secas);
 *   - presente «la reunión de padres es mañana» SIGUE HIT (cópula
 *     presente, no «fue/era»);
 *   - piso quedar «quedamos con ana ayer» SIGUE MEETING 0.54 (sin
 *     cópula en la frase);
 *   - envolvente «recuérdame que fui a la reunión de padres ayer»
 *     SIGUE TASK 0.45 (candado c.613 — el guard sólo descarta el
 *     candidato MEETING);
 *   - vecinos heredados NULL: «la reunión fue pospuesta», «tenía
 *     reunión con el equipo» (PAST_OBLIGATION c.1240), «fui a la
 *     reunión de padres ayer» (c.1138).
 * Laterales NO resueltas (una forma por ciclo; documentadas en el KDoc
 * del guard): «fue ayer la reunión de padres» (cópula+fecha antepuestas
 * sin marcador inicial), «la reunión de ayer fue productiva» (cópula
 * pospuesta sin marcador de pasado tras ella — sigue HIT, hermana de
 * la doctrina c.5369) y las compuestas («ha sido ayer»).
 */
class ContextIntentEnginePastMeetingCopulativeGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- FALSO POSITIVO c.1142: copulativa pretérito de reunión ya celebrada → NULL ----

    @Test
    fun `la reunion de padres fue ayer no captura`() {
        assertNull(analyze("la reunión de padres fue ayer"))
    }

    @Test
    fun `la reunion del colegio fue anteayer no captura`() {
        assertNull(analyze("la reunión del colegio fue anteayer"))
    }

    @Test
    fun `la reunion con el jefe fue anoche no captura`() {
        assertNull(analyze("la reunión con el jefe fue anoche"))
    }

    @Test
    fun `ayer fue la reunion de padres no captura`() {
        assertNull(analyze("ayer fue la reunión de padres"))
    }

    @Test
    fun `anteayer fue la reunion del colegio no captura`() {
        assertNull(analyze("anteayer fue la reunión del colegio"))
    }

    @Test
    fun `anoche fue la reunion con el equipo no captura`() {
        assertNull(analyze("anoche fue la reunión con el equipo"))
    }

    @Test
    fun `la reunion de padres era ayer no captura`() {
        assertNull(analyze("la reunión de padres era ayer"))
    }

    @Test
    fun `ayer era la reunion de padres no captura`() {
        assertNull(analyze("ayer era la reunión de padres"))
    }

    // ---- PINES byte-idénticos: capturas legítimas que NO toca el guard ----

    @Test
    fun `reunion de padres manana sigue capturando como MEETING`() {
        val i = analyze("reunión de padres mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `la reunion de padres es manana sigue capturando copula presente`() {
        val i = analyze("la reunión de padres es mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `reunion de padres ayer nominal vencida sigue capturando doctrina c5369`() {
        val i = analyze("reunión de padres ayer")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `quedamos con ana ayer piso quedar sigue capturando`() {
        val i = analyze("quedamos con ana ayer")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.54f, i.confidence)
    }

    @Test
    fun `reunion es manana y fui a la del curso pasado sigue capturando por posicionalidad`() {
        val i = analyze("la reunión de padres es mañana; fui a la del curso pasado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `tengo reunion con el equipo manana sigue capturando`() {
        val i = analyze("tengo reunión con el equipo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `recuerdame que fui a la reunion de padres ayer sigue TASK envolvente candado c613`() {
        val i = analyze("recuérdame que fui a la reunión de padres ayer")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `voy a la reunion de padres manana sigue capturando`() {
        val i = analyze("voy a la reunión de padres mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.5f, i.confidence)
    }

    @Test
    fun `ayer fue un dia largo y la reunion es manana sigue capturando anti-overreach adyacencia`() {
        val i = analyze("ayer fue un día largo, la reunión de padres es mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `la reunion de ayer fue productiva sigue capturando lateral no tocada`() {
        val i = analyze("la reunión de ayer fue productiva")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    // ---- GUARDS: vecinos NULL que el guard no debe tocar ----

    @Test
    fun `la reunion fue pospuesta sigue NULL heredado`() {
        assertNull(analyze("la reunión fue pospuesta"))
    }

    @Test
    fun `tenia reunion con el equipo sigue NULL por PAST_OBLIGATION c1240`() {
        assertNull(analyze("tenía reunión con el equipo"))
    }

    @Test
    fun `fui a la reunion de padres ayer sigue NULL por guard c1138`() {
        assertNull(analyze("fui a la reunión de padres ayer"))
    }
}

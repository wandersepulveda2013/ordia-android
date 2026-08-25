package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1134: candidata (c) complementaria de la clase DECIMODUARTA
 * (BACKLOG, añadida en c.1127-bis tras la colisión convergente con
 * la sonda del hermano `10c7789`) — FALSO POSITIVO P1 medido 5/5:
 * el relato en pretérito de una reunión YA celebrada («fui a la
 * reunión de padres ayer») capturaba como MEETING 0.45 con dueAt —
 * el hecho cumplido se persistía como compromiso futuro con fecha
 * (ayer parseado; la corrupción es del mismo tipo que c.1240
 * PAST_OBLIGATION para los pisos por VERBO, pero aquí los pisos
 * MEETING son por SUSTANTIVO c.647 y carecían de guard de pretérito).
 * Familia medida PRE con sonda efímera `/tmp/probe1129/Probe.kt`
 * (motor real vía `tools/run_probe.sh`, HEAD `17277ce`): T1-T9 9/9
 * HIT MEETING 0.45 (fui/fuimos/estuve/fue/asistí/«no fui» —el
 * FALSO MÁS GRAVE: una NO-asistencia ya pasada se persistía como
 * compromiso—; sin fecha explícita; coordinación «…y mañana tengo
 * otra»), pins P1-P10 HIT y G1 NULL (PAST_OBLIGATION c.1240).
 * Fix: guard POSICIONAL hermano de [ContextIntentEngine.pastObligationGoverns]
 * — un pretérito de ir/estar/asistir (fui/fuiste/fue/fuimos/fuisteis/
 * fueron/estuve/…/estuvieron/asistí/…/asistieron, con opcional «no »
 * prefijada) SEGUIDO de preposición (a|al|en) que ABARCA al match
 * MEETING (fin del pretérito+preposición ≤ inicio del match) descarta
 * el candidato MEETING de la selección; si el descarte deja la
 * lista vacía, la frase cae a NULL (el relato de un hecho cumplido
 * no es un compromiso, hermandad con c.1240). CERO keywords nuevas
 * (doctrina c.862); CERO cambios en ContextIntent.kt.
 * Pines de alcance (byte-idénticos PRE):
 *   - nominal vencida «reunión de padres ayer» SIGUE HIT (doctrina
 *     c.5369 de vencidas explícitas — fue el primer hijo de ese
 *     principio; el guard exige pretérito+preposición, no «ayer»);
 *   - futuro «voy a ir a la reunión de padres» SIGUE HIT (el patrón
 *     exige forma pretérita, no el infinitivo «ir»);
 *   - posicionalidad: «la reunión de padres es mañana; fui a la del
 *     curso pasado» SIGUE HIT (el marcador va DESPUÉS del match);
 *   - envolvente «recuérdame que fui a la reunión de padres ayer»
 *     SIGUE TASK 0.45 (el usuario pide recordarlo explícitamente;
 *     candado c.613 — el piso envolvente sobrevive al descarte del
 *     candidato MEETING);
 *   - piso quedar «quedamos con ana ayer» SIGUE MEETING 0.54 (el
 *     guard NO toca los pisos MEETING —laterales documentados en el
 *     KDoc de [ContextIntentEngine.pastMeetingNarrativeGoverns]—;
 *     esa forma ya era candidata del KDoc del piso l.142);
 *   - «tenía reunión con el equipo» SIGUE NULL (PAST_OBLIGATION
 *     c.1240, región intacta).
 * Laterales NO resueltas (una forma por ciclo, doctrina anti-overreach;
 * documentadas en el KDoc del guard): las copulativas «la reunión
 * de padres fue ayer» / «ayer fue la reunión de padres» (siguen HIT
 * — el pretérito va DESPUÉS o precede sin preposición) y la familia
 * «hemos ido / había ido» (compuestas — recorte conservador).
 */
class ContextIntentEnginePastMeetingNarrativeGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- FALSO POSITIVO c.1134: relato en pretérito de reunión ya celebrada → NULL ----

    @Test
    fun `fui a la reunion de padres ayer no captura`() {
        assertNull(analyze("fui a la reunión de padres ayer"))
    }

    @Test
    fun `fuimos a la reunion del colegio ayer no captura`() {
        assertNull(analyze("fuimos a la reunión del colegio ayer"))
    }

    @Test
    fun `estuve en la reunion de padres ayer no captura`() {
        assertNull(analyze("estuve en la reunión de padres ayer"))
    }

    @Test
    fun `fue a la reunion de padres no captura`() {
        assertNull(analyze("fue a la reunión de padres"))
    }

    @Test
    fun `asisti a la reunion con el jefe ayer no captura`() {
        assertNull(analyze("asistí a la reunión con el jefe ayer"))
    }

    @Test
    fun `no fui a la reunion de padres ayer no captura`() {
        assertNull(analyze("no fui a la reunión de padres ayer"))
    }

    @Test
    fun `fui a la reunion de padres sin fecha no captura`() {
        assertNull(analyze("fui a la reunión de padres"))
    }

    @Test
    fun `fueron a la reunion del colegio no captura`() {
        assertNull(analyze("fueron a la reunión del colegio"))
    }

    @Test
    fun `fui a la reunion y manana tengo otra no captura`() {
        assertNull(analyze("fui a la reunión de padres y mañana tengo otra"))
    }

    // ---- PINES byte-idénticos: capturas MEETING legítimas que NO toca el guard ----

    @Test
    fun `reunion de padres manana sigue capturando como MEETING`() {
        val i = analyze("reunión de padres mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `tengo reunion con el equipo manana sigue capturando`() {
        val i = analyze("tengo reunión con el equipo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `la reunion de padres es manana sigue capturando`() {
        val i = analyze("la reunión de padres es mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
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
    fun `ire a la reunion del colegio manana sigue capturando`() {
        val i = analyze("iré a la reunión del colegio mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.5f, i.confidence)
    }

    @Test
    fun `reunion es manana y fui a la del curso pasado sigue capturando por posicionalidad`() {
        val i = analyze("la reunión de padres es mañana; fui a la del curso pasado")
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
    fun `quedamos con ana ayer piso quedar sigue capturando lateral no tocado`() {
        val i = analyze("quedamos con ana ayer")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.54f, i.confidence)
    }

    @Test
    fun `recuerdame que fui a la reunion de padres ayer sigue TASK envolvente candado c613`() {
        val i = analyze("recuérdame que fui a la reunión de padres ayer")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    @Test
    fun `voy a ir a la reunion de padres futuro sigue capturando`() {
        val i = analyze("voy a ir a la reunión de padres")
        assertNotNull(i)
        assertEquals(ContextIntentKind.MEETING, i!!.kind)
        assertEquals(0.45f, i.confidence)
    }

    // ---- GUARDS: vecinos NULL que el guard no debe tocar ----

    @Test
    fun `tenia reunion con el equipo sigue NULL por PAST_OBLIGATION c1240`() {
        assertNull(analyze("tenía reunión con el equipo"))
    }

    @Test
    fun `fui a comprar pan sigue NULL`() {
        assertNull(analyze("fui a comprar pan"))
    }

    @Test
    fun `fui al medico ayer sigue NULL`() {
        assertNull(analyze("fui al médico ayer"))
    }
}

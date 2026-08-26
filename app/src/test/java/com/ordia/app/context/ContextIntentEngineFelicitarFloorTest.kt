package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1167: candidata (a) FUERTE de la auditoría c.1165 (clase
 * DECIMOCTAVA, vida social y eventos; sonda persistida
 * `tools/probe/EighteenthClassSocialProbe.kt` C3) —
 * «felicitar a Laura mañana» medida NULL 6/6 en PRE con sonda efímera
 * (motor real vía `tools/run_probe.sh`) sobre HEAD 488bfe6: «felicitar»
 * no era keyword ni tenía piso (gate c.751: ni llegaba al análisis).
 * La felicitación de cumpleaños olvidada es el coste social canónico:
 * olvido silencioso P1.
 *
 * Fix lockstep 3 puntos (hermano EXACTO de «empadronarme» c.1156):
 *  1. keyword-VERB «felicitar» en ContextIntent.TASK (0.12 sola inerte <
 *     umbral; «felicitación» NO casa por subcadena: rompe -r- vs -c-).
 *  2. Piso acotado «felicitar a(l| la| los| las)? <persona>» en
 *     `hasStrongTaskImperative` (misma ancla ^|acuse|temporal y guard
 *     `(?<!no )`). Kind TASK: gestión social SIN desplazamiento ni
 *     llamada explícita (hermana de «enviar la invitación» TASK,
 *     medida c.1165-C5; CALL gobierna solo llamar/hablar/telefonear).
 *  3. Plantilla matchFelicitar en `extractTitle` (lección c.616,
 *     grafía preservada c.653; el residuo temporal de cola lo depura
 *     [sanitizeTitle]).
 *
 * Alcance deliberado (UNA forma por ciclo, anti-overreach): SOLO
 * «felicitar a <persona>». «felicitar la Navidad» (sin «a»),
 * enclíticos («felicitarla») y «darle la enhorabuena» quedan
 * laterales NULL documentadas.
 *
 * Guards pineados NULL (medidos PRE): negación compuesta «no voy a
 * felicitar…», negación inmediata «no felicitar…», duda subjuntivo
 * «quizá felicite…», pretérito «felicité…», sustantivo aislado «la
 * felicitación de cumpleaños», verbo aislado «felicitar», sin-«a»
 * «felicitar la Navidad».
 */
class ContextIntentEngineFelicitarFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---------- captura (6/6 NULL en PRE) ----------

    @Test
    fun `felicitar a Laura manana captura TASK con dueAt y titulo depurado`() {
        val r = analyze("felicitar a Laura mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.confidence >= 0.45f)
        assertTrue(r.dueAt != null)
        assertEquals("Felicitar a Laura", r.title)
    }

    @Test
    fun `felicitar a mama por su cumpleanos captura TASK sin fecha`() {
        val r = analyze("felicitar a mamá por su cumpleaños")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.confidence >= 0.45f)
        assertNull(r.dueAt)
        assertEquals("Felicitar a mamá por su cumpleaños", r.title)
    }

    @Test
    fun `felicitar a los abuelos el domingo captura TASK con dueAt`() {
        val r = analyze("felicitar a los abuelos el domingo")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Felicitar a los abuelos", r.title)
    }

    @Test
    fun `manana felicitar a Laura captura TASK por ancla temporal prefija`() {
        val r = analyze("mañana felicitar a Laura")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Felicitar a Laura", r.title)
    }

    @Test
    fun `vale felicitar a Marta esta tarde captura TASK por prefijo de acuse`() {
        val r = analyze("vale, felicitar a Marta esta tarde")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Felicitar a Marta", r.title)
    }

    @Test
    fun `felicitar al jefe el lunes captura TASK con contraccion al`() {
        val r = analyze("felicitar al jefe el lunes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Felicitar al jefe", r.title)
    }

    // ---------- guards (8/8 NULL en PRE, deben seguir NULL) ----------

    @Test
    fun `negacion compuesta no voy a felicitar sigue NULL`() {
        assertNull(analyze("no voy a felicitar a Laura mañana"))
    }

    @Test
    fun `negacion inmediata no felicitar sigue NULL`() {
        assertNull(analyze("no felicitar a Laura esta vez"))
    }

    @Test
    fun `duda subjuntivo quiza felicite sigue NULL`() {
        assertNull(analyze("quizá felicite a Laura"))
    }

    @Test
    fun `preterito felicite ayer sigue NULL`() {
        assertNull(analyze("felicité a Laura ayer"))
    }

    @Test
    fun `preterito ya felicite sigue NULL`() {
        assertNull(analyze("ya felicité a los primos"))
    }

    @Test
    fun `sustantivo aislado la felicitacion de cumpleanos sigue NULL`() {
        assertNull(analyze("la felicitación de cumpleaños"))
    }

    @Test
    fun `verbo aislado felicitar sigue NULL`() {
        assertNull(analyze("felicitar"))
    }

    @Test
    fun `sin preposicion a felicitar la Navidad sigue NULL deliberado`() {
        assertNull(analyze("felicitar la Navidad"))
    }

    // ---------- regresiones / pines byte-idénticos (medidos PRE) ----------

    @Test
    fun `regresion llamar a mama sigue CALL`() {
        val r = analyze("llamar a mamá esta noche")!!
        assertEquals(ContextIntentKind.CALL, r.kind)
        assertEquals("Llamar a mamá", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin envolvente recuerdame felicitar kind-titulo-dueAt byte-identicos`() {
        // Pin medido PRE 0.45 → POST 0.54 (+0.09 keyword «felicitar»,
        // delta honesto documentado: el camino envolvente genérico
        // «recuérdame …» ya capturaba; la keyword nueva sólo suma su
        // bono. Kind/título/dueAt byte-idénticos).
        val r = analyze("recuérdame felicitar a Laura mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Felicitar a Laura", r.title)
        assertTrue(r.dueAt != null)
        assertEquals(0.54f, r.confidence, 0.001f)
    }

    @Test
    fun `regresion enviar la invitacion sigue TASK`() {
        val r = analyze("enviar la invitación del cumpleaños esta tarde")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Enviar la invitación del cumpleaños", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `regresion comprar regalo sigue SHOPPING`() {
        val r = analyze("comprar un regalo para el cumpleaños de Ana")!!
        assertEquals(ContextIntentKind.SHOPPING, r.kind)
        assertEquals("Comprar un regalo para el cumpleaños de Ana", r.title)
        assertNull(r.dueAt)
    }

    @Test
    fun `pin apuntar a los ninos al campamento sigue EXERCISE`() {
        // Pin honestidad-de-kind (lateral P2 ABIERTA registrada c.1165):
        // el compromiso se captura y el título es correcto; el kind
        // EXERCISE es semánticamente erróneo — candidata de ciclo propio.
        val r = analyze("apuntar a los niños al campamento de verano")!!
        assertEquals(ContextIntentKind.EXERCISE, r.kind)
        assertEquals("Apuntar a los niños al campamento de verano", r.title)
    }

    @Test
    fun `regresion confirmar asistencia sigue TASK`() {
        val r = analyze("confirmar asistencia a la boda esta semana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Confirmar asistencia a la boda esta semana", r.title)
        assertNull(r.dueAt)
    }

    @Test
    fun `regresion empadronarme c1156 sigue TASK`() {
        val r = analyze("empadronarme en el nuevo piso este mes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertEquals("Empadronarme en el nuevo piso este mes", r.title)
        assertNull(r.dueAt)
    }

    @Test
    fun `regresion quedar con los primos sigue MEETING`() {
        val r = analyze("quedar con los primos para cenar el sábado")!!
        assertEquals(ContextIntentKind.MEETING, r.kind)
        assertEquals("Quedar con los primos para cenar", r.title)
        assertTrue(r.dueAt != null)
    }
}

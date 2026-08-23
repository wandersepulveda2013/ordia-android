package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.900 — «traer <objeto> a <persona/lugar>»: SEGUNDA forma NULL de
 * la clase NOVENA-b (coordinación y préstamos con personas, sonda
 * `NinthClassCoordinationProbe.kt` c.890b, candidata (a) del BACKLOG).
 * Medida NULL en la sonda PRE persistida `tools/probe/TraerObjetoProbe.kt`
 * (5/5 candidatas NULL — declarativa, prefijo temporal, acuse, poseído —;
 * 7/7 guards NULL; 6/6 regresiones HIT), ejecutada con el motor real vía
 * `tools/run_probe.sh` contra el HEAD c.899.
 *
 * Causa raíz: «traer» es keyword TASK suelta (0.12) y nunca fue verbo de
 * ERRAND (c.854 se limitó al dativo enclítico), así la forma cotidiana
 * sin envolvente quedaba en ~0.22 (< [ContextIntentEngine.MINIMUM_CONFIDENCE])
 * y se DESCARTABA: el usuario capturaba una gestión interpersonal real y
 * Ordía la olvidaba. La forma envolvente («recuérdame traer…») ya
 * capturaba vía el piso TASK c.613 — rendija pasiva↔manual, misma clase
 * que c.583/c.893.
 *
 * Decisión de dominio deliberada en este ciclo (BACKLOG P1): ERRAND, no
 * TASK — hermana de «llevarle su cuaderno a Ana» (c.854): la forma
 * implica desplazamiento hacia un tercero; el verbo «traer» es bivalente
 * («traer pan» roza la compra; «traer suerte/consecuencias/la alegría»
 * son figurados), así el piso se ACOTA a infinitivo + objeto
 * (determinante opcional) + ancla DATIVA «a <destino>» con lookahead
 * anti-figurado (suerte/consecuencias/alegría/desgracia medidos en la
 * sonda). Sin destino interpersonal, «traer <objeto>» sigue siendo
 * keyword TASK sola.
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso acotado `ERRAND_BRING_FLOOR` — verbo «traer» + objeto +
 *     ancla dativa, guard `(?<!no )` (familia c.640/c.643), posición
 *     libre `\b` (prefijo temporal/acuse cubiertos);
 * (2) keyword-verbo «traer» YA existía en [ContextIntentKind.TASK]
 *     (lección c.751 satisfecha: la forma llega al análisis; NO se mueve
 *     de lista — sigue alimentando la deliberación TASK de sus otras
 *     formas, p.ej. «traer pan»);
 * (3) plantilla de título «traer» en [ContextIntentEngine.extractTitle]
 *     (verbo preservado, lección c.616; grafía del objeto y del
 *     destinatario preservadas, doctrina c.653).
 * Cinturón y tirantes: cláusula de negación dedicada en
 * [ContextIntentEngine.imperativeIsNegated] con ancla-objeto/datativa y
 * guards anti-figurado IDÉNTICOS al piso (precedente c.854); el guard de
 * envolvente [ContextIntentEngine.imperativeIsWrapped] fluye por
 * `ERRAND_FLOORS` (fuente única, lección c.648/c.652).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): laterales «traerle <objeto>» enclítico y «traer <objeto>» sin
 * dativo quedan a medir; la familia de la clase NOVENA-b sigue
 * registrada en el BACKLOG.
 */
class ContextIntentEngineTraerObjetoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: el piso + la plantilla de título) ────

    @Test
    fun `traer objeto a persona captura ERRAND con titulo limpio`() {
        val r1 = analyze("traer el cargador a Ana mañana")
        assertNotNull("«traer el cargador a Ana mañana» debe capturar (NULL hasta c.900)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Traer el cargador a Ana", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("traer el libro a Marta el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Traer el libro a Marta", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)

        // Poseído: el determinante se preserva en el título (doctrina c.653).
        val r3 = analyze("traer mi cargador a Ana mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Traer mi cargador a Ana", r3.title)

        // Destino-lugar (oficina): la ancla dativa cubre persona y lugar.
        val r4 = analyze("traer el informe a la oficina mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.ERRAND, r4!!.kind)
        assertEquals("Traer el informe a la oficina", r4.title)
    }

    @Test
    fun `prefijo temporal y acuse no ensucian el titulo`() {
        // Prefijo temporal: el match arranca en el verbo (lección c.616).
        val r1 = analyze("mañana traer el cuaderno a Irene")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Traer el cuaderno a Irene", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Acuse despojado del título + residuo temporal de cola depurado.
        val r2 = analyze("vale, traer las llaves a papá hoy")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Traer las llaves a papá", r2.title)
        assertNotNull("«hoy» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (verde desde RED: «traer» preexistía) ─────

    @Test
    fun `keyword verbo traer llega a TRIGGER_WORDS (lockstep c751)`() {
        assertTrue(
            "keyword-verbo «traer» (preexistente en TASK; lockstep c.900 vía c.751)",
            ContextIntentKind.TRIGGER_WORDS.contains("traer")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no traer el cargador a Ana» (negación; cinturón y tirantes c.900)",
            analyze("no traer el cargador a Ana"))
        assertNull("«quizá traiga…» (subjuntivo no imperativo)",
            analyze("quizá traiga el cargador a Ana"))
        assertNull("«traje… ayer» (pasado; nota de hecho, no compromiso)",
            analyze("traje el cargador a Ana ayer"))
        assertNull("«la traída…» (sustantivo)",
            analyze("la traída del cargador"))
    }

    @Test
    fun `figurados de traer quedan fuera (anti-overreach bivalente)`() {
        assertNull("«traer suerte a la casa» (figurado; lookahead anti-figurado)",
            analyze("traer suerte a la casa"))
        assertNull("«traer consecuencias a largo plazo» (figurado)",
            analyze("eso puede traer consecuencias a largo plazo"))
        assertNull("«traer a colación el tema» (locución; sin objeto antes de «a»)",
            analyze("traer a colación el tema"))
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `piso dativo c854 y vecinos siguen intactos`() {
        val r1 = analyze("llevarle su cuaderno a Ana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)

        val r2 = analyze("recoger el paquete en Correos")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)

        val r3 = analyze("devolver el libro a la biblioteca")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)

        val r4 = analyze("comprar pan mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.SHOPPING, r4!!.kind)

        val r5 = analyze("llamar a Ana mañana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.CALL, r5!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue ganando TASK (guard c652)`() {
        // El imperativo envolvente gobierna: el verbo subordinado es
        // CONTENIDO del recordatorio, no una acción autónoma (c.652); el
        // guard fluye por ERRAND_FLOORS (fuente única).
        val r = analyze("recuérdame traer el cargador a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}

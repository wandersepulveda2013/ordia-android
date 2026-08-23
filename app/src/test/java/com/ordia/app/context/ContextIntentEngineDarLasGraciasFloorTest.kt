package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.901 — «dar las gracias a <persona> (por <objeto>)»: candidata
 * (b), ÚLTIMA forma NULL de la clase NOVENA-b (coordinación y préstamos
 * con personas, sonda `NinthClassCoordinationProbe.kt` c.890b). Medida
 * NULL en la sonda PRE persistida `tools/probe/DarLasGraciasProbe.kt`
 * (5/5 candidatas NULL — declarativa con objeto, prefijo temporal,
 * acuse, plural —; 5/5 guards NULL; 2/2 laterales NULL; 6/6 regresiones
 * HIT), ejecutada con el motor real vía `tools/run_probe.sh` contra el
 * HEAD c.900 (suite base 5961 OK).
 *
 * Causa raíz: ni «dar» ni «gracias» son keyword ni verbo de piso
 * alguno, así la forma cotidiana sin envolvente ni siquiera llegaba al
 * análisis (lección c.751) y se DESCARTABA: el usuario capturaba un
 * agradecimiento pendiente (comunicación interpersonal real, hermana de
 * «avisar a…») y Ordía lo olvidaba. La forma envolvente («recuérdame
 * dar las gracias a Ana…») ya capturaba vía el piso TASK c.613 —
 * rendija pasiva↔manual, misma clase que c.583/c.893/c.900.
 *
 * Decisión de dominio deliberada en este ciclo (BACKLOG P1): TASK, no
 * ERRAND — es comunicación de gratitud pendiente (hermana de «avisar a
 * mamá de la cita» TASK c.711), gestión interpersonal SIN
 * desplazamiento físico; la doctrina ERRAND c.842/c.862 gobierna solo
 * el desplazamiento. El verbo-frase «dar las gracias» es
 * cuasi-monosemántico (gratitud; hermano de «dar de baja» c.895c), así
 * el piso se ancla a la forma EXACTA + ancla DATIVA «a <destino>» —
 * «dar las gracias» suelto (sin destino) queda NULL deliberado.
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso acotado en [ContextIntentEngine.hasStrongTaskImperative] —
 *     «dar las gracias a \w», guard `(?<!no )` (familia c.640/c.643),
 *     posición de piso con acuse/prefijo temporal (c.651);
 * (2) keyword-FRASE «dar las gracias» en [ContextIntentKind.TASK]
 *     (lección c.751: sin ella la notificación sin palabra gatillo ni
 *     llega al análisis; hermana de «dar de baja» c.895c — 0.12 sola
 *     inerte < umbral y el piso exige la ancla dativa);
 * (3) plantilla de título «dar las gracias» en
 *     [ContextIntentEngine.extractTitle] (verbo-frase preservado,
 *     lección c.616; grafía del destinatario y del objeto preservadas,
 *     doctrina c.653).
 * La negada la cubre el guard `(?<!no )` del piso; la keyword 0.12 +
 * bono temporal 0.1 = 0.22 < umbral: no hace falta cláusula dedicada
 * en [ContextIntentEngine.imperativeIsNegated] (mismo argumento medido
 * que c.895b/c.895c).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): laterales «darle las gracias…» enclítico y «dar gracias a…»
 * sin artículo medidas NULL en la sonda — quedan registradas para
 * ciclos futuros.
 */
class ContextIntentEngineDarLasGraciasFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: el piso + la plantilla de título) ────

    @Test
    fun `dar las gracias a persona captura TASK con titulo limpio`() {
        val r1 = analyze("dar las gracias a Ana por el regalo")
        assertNotNull("«dar las gracias a Ana por el regalo» debe capturar (NULL hasta c.901)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias a Ana por el regalo", r1.title)

        val r2 = analyze("dar las gracias a Marta mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Dar las gracias a Marta", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)

        // Plural: el destinatario colectivo se preserva (doctrina c.653).
        val r3 = analyze("dar las gracias a los vecinos el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Dar las gracias a los vecinos", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `prefijo temporal y acuse no ensucian el titulo`() {
        // Prefijo temporal: el match arranca en el verbo (lección c.616).
        val r1 = analyze("mañana dar las gracias a Irene")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias a Irene", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Acuse despojado del título + residuo temporal de cola depurado.
        val r2 = analyze("vale, dar las gracias a papá por el favor hoy")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Dar las gracias a papá por el favor", r2.title)
        assertNotNull("«hoy» debe anclar dueAt", r2.dueAt)
    }

    // ─── c.902: contracción «al» (delta STALE_RUN; RED medido en la ──
    // sonda CAND-F/G sobre b956cc5: NULL — el piso c.901 exigía «a» literal)

    @Test
    fun `contraccion al tambien ancla el destinatario`() {
        val r1 = analyze("dar las gracias al jefe por el ascenso hoy")
        assertNotNull("«dar las gracias al jefe…» debe capturar (NULL medido hasta c.902)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias al jefe por el ascenso", r1.title)
        assertNotNull("«hoy» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("mañana dar las gracias al médico")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Dar las gracias al médico", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED: la frase no existía) ────────────────

    @Test
    fun `keyword frase dar las gracias llega a TRIGGER_WORDS (lockstep c751)`() {
        assertTrue(
            "keyword-frase «dar las gracias» (lockstep c.901 vía c.751, hermana de «dar de baja» c.895c)",
            ContextIntentKind.TRIGGER_WORDS.contains("dar las gracias")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no dar las gracias a Ana» (negación; guard del piso)",
            analyze("no dar las gracias a Ana"))
        assertNull("«quizá dé las gracias a Ana» (subjuntivo no imperativo)",
            analyze("quizá dé las gracias a Ana"))
        assertNull("«di las gracias a Ana ayer» (pasado; nota de hecho, no compromiso)",
            analyze("di las gracias a Ana ayer"))
        assertNull("«las gracias de Ana» (sustantivo)",
            analyze("las gracias de Ana"))
        assertNull("«dar las gracias» suelto (sin destino; ancla dativa exigida)",
            analyze("dar las gracias"))
    }

    // ─── Laterales a medir (NULL deliberado, NO de este ciclo) ──────

    @Test
    fun `lateral sin articulo queda a medir (anti-overreach)`() {
        // c.903: la lateral enclítica «darle las gracias a Ana…» se resolvió
        // (ver ContextIntentEngineDarleLasGraciasFloorTest); esta guarda pasa
        // a cubrir solo la forma sin artículo, que sigue fuera de alcance.
        assertNull("«dar gracias a Ana…» (sin artículo; lateral registrada)",
            analyze("dar gracias a Ana por el regalo"))
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `pisos vecinos siguen intactos`() {
        val r1 = analyze("avisar a mamá de la cita mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("llamar a Ana mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.CALL, r2!!.kind)

        val r3 = analyze("dar de baja el gimnasio")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)

        val r4 = analyze("comprar pan mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.SHOPPING, r4!!.kind)

        val r5 = analyze("llevarle su cuaderno a Ana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.ERRAND, r5!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue ganando TASK (guard c613)`() {
        // El imperativo envolvente gobierna: el verbo subordinado es
        // CONTENIDO del recordatorio, no una acción autónoma.
        val r = analyze("recuérdame dar las gracias a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1148 — candidata (a) FUERTE de la clase DECIMOSÉPTIMA (vida laboral,
 * sonda persistida `tools/probe/SeventeenthClassWorkProbe.kt` de la
 * auditoría c.1147 del hermano, C5): «echar el currículum en la oferta
 * de infojobs». NULL PRE medido con sonda efímera (motor real vía
 * `tools/run_probe.sh`, base `5a39f45`): 4/4 candidatas desnudas NULL
 * (C1/C2/C3/C5), la envolvente «recuérdame…» ya captura por camino
 * genérico (C4 TASK 0.45), 6/6 guards NULL, 4/4 regresiones HIT.
 * Olvido silencioso P1: la oferta de empleo tiene plazo — olvidarla
 * cuesta la oportunidad entera (el olvido más caro de la clase
 * DECIMOSÉPTIMA).
 *
 * Decisión de dominio: TASK (gestión laboral SIN desplazamiento
 * explícito; hermana de «sellar el paro» TASK c.1143 — la doctrina
 * ERRAND c.842/c.862 gobierna solo el desplazamiento).
 *
 * Lockstep TRES puntos (lección c.616/c.751, hermano EXACTO de c.1143):
 * (1) keywords-OBJETO «currículum»/«curriculum» en TASK (grafías con y
 * sin tilde — el matching es substring `contains` sin normalizar;
 * precedente EXACTO en la misma lista: «suscripción»/«suscripcion»
 * c.895c, «nómina» c.895b; cuasi-monosemánticas: contexto laboral,
 * 0.12 sola inerte < umbral); (2) piso NUEVO «echar (el)? curr[ií]culums?»
 * en `hasStrongTaskImperative` junto al piso «sellar (el)? paro» c.1143
 * (ancla ^|acuse|temporal y guard `(?<!no )` heredados de la familia;
 * el objeto «currículum» es EXIGIDO — «echar» es bivalente c.829:
 * «echar de menos», «echar la carta», «echar gasolina» no casan);
 * (3) plantilla hermana matchEcharCurriculum en [extractTitle]
 * (doctrina c.653: verbo-frase preservado).
 *
 * Acotado deliberado: «echar de menos a la familia», «echar la carta
 * al buzón» NULL — el objeto «currículum» es EXIGIDO por el piso.
 * Laterales ABIERTAS (UNA por ciclo): resto de familias de la auditoría
 * c.1147 ((b) «cubrir el turno», (c) «hacer el curso de…», (d) «preparar
 * la entrevista», (e) «llevar el portátil»).
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
    fun `echar el curriculum en oferta captura TASK`() {
        val intent = analyze("echar el currículum en la oferta de infojobs")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum en la oferta de infojobs", intent.title)
    }

    @Test
    fun `echar el curriculum sin tilde manana captura TASK con titulo limpio`() {
        val intent = analyze("echar el curriculum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el curriculum", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `echar curriculums plural sin articulo captura TASK`() {
        val intent = analyze("echar currículums en varias webs esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        // Lateral de título (heredada del motor, NO del piso): la cola
        // «esta semana» no la depura [sanitizeTitle] — pin del
        // comportamiento real medido (sonda POST c.1148). Misma familia
        // que la cola «el día N» documentada en c.1143.
        assertEquals("Echar currículums en varias webs esta semana", intent.title)
        assertEquals(null, intent.dueAt)
    }

    @Test
    fun `echar el curriculum hoy captura TASK con dueAt`() {
        val intent = analyze("echar el currículum hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `acuse vale echar el curriculum captura TASK`() {
        val intent = analyze("vale, echar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
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
    fun `bivalente echar de menos no captura`() {
        assertNull(analyze("echar de menos a la familia"))
    }

    @Test
    fun `otro objeto echar la carta no captura`() {
        assertNull(analyze("echar la carta al buzón"))
    }

    @Test
    fun `sustantivo el curriculum ya enviado no captura`() {
        assertNull(analyze("el currículum ya está enviado"))
    }

    // ─── Regresiones (byte-idénticas) ─────────────────────────────

    @Test
    fun `enviar el informe sigue TASK`() {
        val intent = analyze("enviar el informe antes del viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `quedar con el jefe sigue MEETING`() {
        val intent = analyze("quedar con el jefe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun `imprimir el informe sigue TASK`() {
        val intent = analyze("imprimir el informe esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `llevarle el informe al jefe sigue ERRAND`() {
        val intent = analyze("llevarle el informe al jefe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // ─── Envolvente (camino genérico, re-pin legítimo c.1035) ─────

    @Test
    fun `envolvente recuerdame echar el curriculum sigue TASK`() {
        val intent = analyze("recuérdame echar el currículum el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar el currículum", intent.title)
        assertNotNull(intent.dueAt)
    }
}

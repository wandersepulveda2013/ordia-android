package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.888 — «reescanear <documento>»: lateral medida NULL desde
 * c.864 (el prefijo re- del piso «escanear»; la sonda PRE persistida
 * `tools/probe/ReescanearDniProbe.kt` mide 7/7 candidatas NULL sobre el
 * encoder sin el piso — olvido silencioso P1: el trámite exige la
 * segunda captura cuando la primera quedó borrosa/cortada, y la orden
 * de repetir el escaneo se descartaba en silencio pese a que la
 * envolvente «recuérdame reescanear el DNI…» ya ruteaba TASK 0.54 vía
 * candado c.613: asimetría hermana de c.765…c.887).
 *
 * Lockstep DOS puntos (lección c.616, hermana de c.864/c.887):
 * (1) piso acotado al verbo monosemántico «reescanear» + objetos-ancla
 * del hermano («dni/contratos?/notas?/código qr», misma ancla ^/ACK/
 * temporal y guard `(?<!no )`);
 * (2) plantilla de título en [extractTitle] (grafía preservada, doctrina
 * c.653 — «Reescanear el DNI…»).
 * CERO cambios en [ContextIntent.kt]: «reescanear» contiene la
 * subcadena «escanear» (keyword-VERBO c.864) → la frase ya llega al
 * análisis con 0.12 y el piso la eleva (lockstep coste-cero, hermana de
 * c.860/c.862/c.863/c.877/c.878/c.882/c.886).
 *
 * Kind TASK (convergente con «escanear/fotocopiar el DNI» y la
 * envolvente c.613). Anti-overreach intacto: objeto ACOTADO (bivalente
 * «reescanear el examen» sigue ruteando STUDY 0.47, no TASK), negación
 * por `(?<!no )`, duda por [HEDGE_PENALTY] post-piso, pasado
 * «reescaneé…» y sustantivo «reescaneo» no casan.
 */
class ContextIntentEngineReescanearDniFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas — RED exacto: únicamente estas fallan ─────────────

    @Test
    fun `reescanear el DNI declarativo captura TASK con titulo limpio`() {
        val r1 = analyze("reescanear el DNI esta tarde")
        assertNotNull("«reescanear el DNI esta tarde» debe capturar (NULL hasta c.888)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Reescanear el DNI", r1.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r1.dueAt)

        // Prefijo de acuse y prefijo temporal se despojan del título.
        val r2 = analyze("vale, reescanear el DNI mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Reescanear el DNI", r2.title)
        assertNotNull(r2.dueAt)

        val r3 = analyze("mañana reescanear mi DNI")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Reescanear mi DNI", r3.title)

        // Forma desnuda sin fecha: captura igualmente, dueAt nulo.
        val r4 = analyze("reescanear el DNI")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Reescanear el DNI", r4.title)
        assertNull("sin expresión temporal no debe anclar dueAt", r4.dueAt)
    }

    @Test
    fun `objetos documentales del hermano capturan igual`() {
        val r1 = analyze("reescanear el contrato mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Reescanear el contrato", r1.title)

        val r2 = analyze("reescanear las notas mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Reescanear las notas", r2.title)

        val r3 = analyze("reescanear el código QR mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Reescanear el código QR", r3.title)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ──────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación", analyze("no reescanear el DNI mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá reescanear el DNI mañana"))
        assertNull("narrativa pasado", analyze("reescaneé el DNI ayer"))
        assertNull("verbo aislado", analyze("reescanear"))
        assertNull(
            "sustantivo «reescaneo» no casa el verbo",
            analyze("el reescaneo del DNI quedó ilegible")
        )
    }

    @Test
    fun `objeto bivalente examen sigue en STUDY y no roba TASK`() {
        val r = analyze("reescanear el examen mañana")
        // Anti-overreach: el piso exige objeto documental acotado; la
        // ruta preexistente del objeto («examen» → STUDY 0.47) se
        // conserva intacta en vez de quedar absorbida por TASK.
        assertNotNull(r)
        assertEquals(ContextIntentKind.STUDY, r!!.kind)
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ────────────

    @Test
    fun `regresiones hermanas documentales y envolvente`() {
        val escanear = analyze("escanear el DNI mañana") // c.864
        assertNotNull(escanear)
        assertEquals(ContextIntentKind.TASK, escanear!!.kind)

        val fotocopiar = analyze("fotocopiar el DNI mañana") // c.887
        assertNotNull(fotocopiar)
        assertEquals(ContextIntentKind.TASK, fotocopiar!!.kind)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613); la
        // forma declarativa converge al mismo kind con el piso.
        val wrapper = analyze("recuérdame reescanear el DNI mañana")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
    }
}

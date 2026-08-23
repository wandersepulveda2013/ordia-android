package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.891 — «fotocopiar <documento no-DNI>»: extensión de la clase de
 * objetos del piso c.887 («fotocopiar el DNI») — hermana de la extensión
 * c.884 sobre el piso c.864 «escanear». La gestión documental fotocopiable
 * no termina en el DNI: trámite (banco/notaría/ayuntamiento) pide a veces
 * contrato, notas o código QR en copia física. Medida NULL en la sonda PRE
 * persistida `tools/probe/FotocopiarDocumentosProbe.kt` (6/6 candidatas
 * NULL — declarativas, acuse, prefijo temporal, compuesta «y guardarlo
 * en la carpeta»; 5/5 guards NULL; 4/4 regresiones HIT), ejecutada con
 * el motor real vía `tools/run_probe.sh` sobre HEAD 151d8f7.
 *
 * Lockstep DOS puntos (lección c.616 — CERO cambios en ContextIntent.kt,
 * hermano de c.888 «reescanear»/c.886 «reclamar una»/c.884 objetos):
 * (1) el piso c.887 extiende el objeto-ancla `dni` a
 * `dni|contratos?|notas?|código\s+qr` (misma ancla ^/ACK/temporal y guard
 * `(?<!no )`);
 * (2) la plantilla de título con la misma alternancia (grafía preservada,
 * doctrina c.653 — «Fotocopiar el contrato…»/«…las notas»/«…el código QR»);
 * keyword«fotocopiar» ya es VERBO en ContextIntent.kt desde c.887 (la
 * frase llega al análisis y el piso la eleva).
 * Kind TASK (convergente con c.864/c.887 y con la envolvente c.613).
 *
 * Acotado deliberado (UNA extensión por ciclo, doctrina anti-overreach
 * c.615): «hacerme la prueba de sonido…» (decisión de dominio pendiente)
 * queda como lateral candidata.
 */
class ContextIntentEngineFotocopiarDocumentosFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: únicamente estas fallan) ─────────────

    @Test
    fun `fotocopiar contrato notas o código QR captura TASK con título limpio`() {
        val r1 = analyze("fotocopiar el contrato mañana")
        assertNotNull("«fotocopiar el contrato mañana» debe capturar (NULL hasta c.891)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Fotocopiar el contrato", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("fotocopiar las notas esta tarde")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Fotocopiar las notas", r2.title)
        assertNotNull(r2.dueAt)

        val r3 = analyze("fotocopiar el código QR mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Fotocopiar el código QR", r3.title)
        assertNotNull(r3.dueAt)

        // Poseído: el posesivo se preserva en el título (doctrina c.653).
        val r4 = analyze("fotocopiar mi contrato mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Fotocopiar mi contrato", r4.title)

        // Prefijos de acuse/temporal se despojan del título.
        val r5 = analyze("vale, fotocopiar el contrato mañana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.TASK, r5!!.kind)
        assertEquals("Fotocopiar el contrato", r5.title)

        val r6 = analyze("mañana fotocopiar las notas")
        assertNotNull(r6)
        assertEquals(ContextIntentKind.TASK, r6!!.kind)
        assertEquals("Fotocopiar las notas", r6.title)
    }

    @Test
    fun `forma compuesta conserva el resto de la frase en el titulo`() {
        val r = analyze("fotocopiar el contrato y guardarlo en la carpeta mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Fotocopiar el contrato y guardarlo en la carpeta", r.title)
        assertNotNull(r.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación", analyze("no fotocopiar el contrato mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá fotocopiar las notas mañana"))
        assertNull("narrativa pasado", analyze("fotocopié el contrato ayer"))
        assertNull("verbo aislado", analyze("fotocopiar"))
        assertNull(
            "sustantivo «fotocopia» no contiene «fotocopiar» (keyword inerte)",
            analyze("la fotocopia del contrato salió borrosa")
        )
    }

    // ─── Lockstep keyword (verde desde RED) ─────────────────────────

    @Test
    fun `keyword fotocopiar permanece en TRIGGER_WORDS (lockstep CERO cambios en ContextIntent kt)`() {
        assertTrue(
            "«fotocopiar» sigue siendo keyword-VERBO (c.887); el piso se extiende en objeto-ancla sin tocar ContextIntent.kt",
            ContextIntentKind.TRIGGER_WORDS.contains("fotocopiar")
        )
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas documentales y envolvente`() {
        val dni = analyze("fotocopiar el DNI mañana") // c.887
        assertNotNull(dni)
        assertEquals(ContextIntentKind.TASK, dni!!.kind)
        assertEquals("Fotocopiar el DNI", dni.title)

        val escanear = analyze("escanear el contrato mañana") // c.884
        assertNotNull(escanear)
        assertEquals(ContextIntentKind.TASK, escanear!!.kind)
        assertEquals("Escanear el contrato", escanear.title)

        val reescanear = analyze("reescanear el DNI mañana") // c.888
        assertNotNull(reescanear)
        assertEquals(ContextIntentKind.TASK, reescanear!!.kind)
        assertEquals("Reescanear el DNI", reescanear.title)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613); la forma
        // declarativa converge al mismo kind con el piso.
        val wrapper = analyze("recuérdame fotocopiar el contrato mañana")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
        assertEquals("Fotocopiar el contrato", wrapper.title)
    }
}

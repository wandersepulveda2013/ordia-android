package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.887 — «fotocopiar el DNI»: lateral medida NULL desde c.864
 * (verbo distinto del piso «escanear el DNI»; registrada como candidata
 * propia en la sonda de la octava clase y guardada como guard NULL en
 * [ContextIntentEngineEscanearDniFloorTest], convertida a regresión de
 * captura en este ciclo — precedente c.843). Fotocopia documental es la
 * gestión documental hermana del escaneo: el trámite (banco, notaría,
 * ayuntamiento) pide la fotocopia «por las dos caras» igual que pide el
 * escaneo. Sin piso se DESCARTABA en silencio (0.0: el verbo no era
 * keyword ni verbo de piso) — olvido silencioso P1.
 *
 * Lockstep TRES puntos (lección c.616/c.751, hermano de c.864):
 * (1) piso acotado al verbo «fotocopiar» (monosemántico, precedente
 * c.752 «votar» / c.864 «escanear») + objeto-ancla «dni», misma ancla
 * (inicio/acuse/prefijo temporal) y guard `(?<!no )` del piso c.864;
 * (2) plantilla de título en [extractTitle] (grafía preservada, doctrina
 * c.653 — «Fotocopiar el DNI…»);
 * (3) keyword-VERBO «fotocopiar» en [ContextIntentKind.TASK] — sin ella
 * la frase ni llegaba al análisis en producción vía
 * [ContextIntent.TRIGGER_WORDS]. Subcadenas inertes: «fotocopia»
 * (sustantivo) y «fotocopié» (pasado) NO contienen «fotocopiar»; 0.12
 * sola queda bajo el umbral (0.45) y el piso las excluye por la ancla.
 *
 * Kind decidido: TASK (convergente con la «escanear el DNI» c.864 y con
 * la envolvente «recuérdame fotocopiar el DNI…» que ya ruteaba TASK 0.45
 * por el candado c.613 — asimetría hermana de c.765…c.865). Acotado
 * deliberado (UNA forma por ciclo, medida NULL en la sonda PRE
 * persistida `tools/probe/FotocopiarDniProbe.kt`): «reescanear el DNI…»
 * (prefijo re-) y «hacerme la prueba de sonido…» (decisión de dominio)
 * siguen como laterales candidatas.
 */
class ContextIntentEngineFotocopiarDniFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (7) — RED exacto: únicamente estas fallan ────────

    @Test
    fun `fotocopiar el DNI declarativo captura TASK con titulo limpio`() {
        val r1 = analyze("fotocopiar el DNI esta tarde")
        assertNotNull("«fotocopiar el DNI esta tarde» debe capturar (NULL hasta c.887)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Fotocopiar el DNI", r1.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("fotocopiar el DNI mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Fotocopiar el DNI", r2.title)
        assertNotNull(r2.dueAt)

        val r3 = analyze("fotocopiar mi DNI mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Fotocopiar mi DNI", r3.title)

        // Prefijo de acuse y prefijo temporal se despojan del título.
        val r4 = analyze("vale, fotocopiar el DNI esta tarde")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Fotocopiar el DNI", r4.title)

        val r5 = analyze("mañana fotocopiar el DNI")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.TASK, r5!!.kind)
        assertEquals("Fotocopiar el DNI", r5.title)

        // Forma desnuda sin fecha: captura igualmente, dueAt nulo.
        val r6 = analyze("fotocopiar el DNI")
        assertNotNull(r6)
        assertEquals(ContextIntentKind.TASK, r6!!.kind)
        assertEquals("Fotocopiar el DNI", r6.title)
        assertNull("sin expresión temporal no debe anclar dueAt", r6.dueAt)
    }

    @Test
    fun `forma compuesta conserva el resto de la frase en el titulo`() {
        val r = analyze("fotocopiar el DNI por las dos caras esta tarde")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Fotocopiar el DNI por las dos caras", r.title)
        assertNotNull(r.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación", analyze("no fotocopiar el DNI mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá fotocopiar el DNI mañana"))
        assertNull("narrativa pasado", analyze("fotocopié el DNI ayer"))
        assertNull("verbo aislado", analyze("fotocopiar"))
        assertNull(
            "sustantivo «fotocopia» no contiene «fotocopiar» (keyword inerte)",
            analyze("la fotocopia del DNI está en el cajón")
        )
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas documentales y envolvente`() {
        val escanear = analyze("escanear el DNI mañana") // c.864
        assertNotNull(escanear)
        assertEquals(ContextIntentKind.TASK, escanear!!.kind)

        val renovar = analyze("renovar el DNI la semana que viene") // c.698
        assertNotNull(renovar)
        assertEquals(ContextIntentKind.TASK, renovar!!.kind)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613); la forma
        // declarativa converge al mismo kind con el piso.
        val wrapper = analyze("recuérdame fotocopiar el DNI mañana")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
        assertEquals("Fotocopiar el DNI", wrapper.title)
    }
}

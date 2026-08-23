package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.914 — lateral del piso de destino `ir a <destino>` (familia
 * c.639/c.647, hermano de «cajero» c.893 y «atm» c.896): «ir a la
 * biblioteca» (devolver/sacar un libro, estudiar allí, llevar a los
 * niños). Medida NULL con sonda efímera PRE (`/tmp/probe914/PreProbe.kt`,
 * motor real vía `tools/run_probe.sh`, HEAD `7551b1f`): 3/3 candidatas
 * NULL (desnuda+temporal, acuse «vale,», prefijo temporal), 4/4 guards
 * NULL, regresiones HIT (banco/cajero ERRAND, médico APPOINTMENT,
 * gimnasio EXERCISE, devolver-libro ERRAND). Causa raíz (lección c.751):
 * el piso c.647 exige el destino en su alternación y «biblioteca» no era
 * keyword de ningún kind — «ir a la biblioteca mañana» ni llegaba al
 * análisis (0.12 < 0.45 sin keyword).
 *
 * Decisión de dominio deliberada: ERRAND, hermano de c.893 — «ir a la
 * biblioteca» es una diligencia con destino (doctrina c.842/c.862 «la
 * diligencia gobierna»). «Biblioteca» es monosémica (el edificio/servicio;
 * no colide con «ir a la biblio» informal porque el regex exige la palabra
 * completa) y la keyword sola suma 0.12 inerte < umbral, así «la
 * biblioteca está cerrada los domingos» sigue descartado aun con bono
 * temporal (medido NULL en la sonda).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de la alternación de destinos del piso
 *     c.639/c.647 en [ERRAND_FLOORS]: `…|\bbiblioteca\b` (cero
 *     reescritura, mismo hueco que «cajero» c.893);
 * (2) keyword-DESTINO «biblioteca» en [ContextIntentKind.ERRAND]
 *     (monosémica; 0.12 sola inerte < umbral). Sin ella la notificación
 *     ni llegaría al análisis (lección c.751);
 * (3) plantilla de título — rama «ir a <destino>» en [extractTitle]:
 *     el match arranca en el verbo (lección c.616), así el acuse
 *     («vale, …») y el prefijo temporal («mañana …») se despojan —
 *     antes nacían «Vale, ir al banco» / «Mañana ir al banco» por la
 *     ruta genérica (medido en sonda `/tmp/probe914/AcuseProbe.kt`),
 *     ahora «Ir a la biblioteca». La alternación de destinos se
 *     extrae a la constante ÚNICA [IR_A_DESTINATIONS] compartida por
 *     piso y plantilla (patrón [TRAMITE_DESTINATIONS] c.718) para que
 *     no puedan divergir.
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no ir a la biblioteca» (la keyword-DESTINO + el bono
 * temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.893/c.909).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «pasar por la biblioteca» tiene ancla distinta (piso STOPBY
 * c.718, familia de destinos de trámite) y queda a medir como lateral
 * hermano; «devolver el libro a la biblioteca» YA captura ERRAND vía
 * el piso de devolución c.682 (regresión verificada).
 */
class ContextIntentEngineIrBibliotecaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `ir a la biblioteca captura ERRAND con titulo limpio`() {
        val r1 = analyze("ir a la biblioteca mañana")
        assertNotNull("«ir a la biblioteca mañana» debe capturar (NULL hasta c.914)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Ir a la biblioteca", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Desnuda: el piso c.647 eleva por sí mismo (paridad con «ir al
        // banco», c.639).
        val r2 = analyze("ir a la biblioteca")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Ir a la biblioteca", r2.title)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo biblioteca`() {
        // Acuse «vale, …» (lección c.616; antes «Vale, ir al banco»).
        val r1 = analyze("vale, ir a la biblioteca esta tarde")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Ir a la biblioteca", r1.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r1.dueAt)

        // Prefijo temporal «mañana …» (antes «Mañana ir al banco»).
        val r2 = analyze("mañana ir a la biblioteca")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Ir a la biblioteca", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword destino biblioteca llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DESTINO «biblioteca» (lockstep c.914, lección c.751; monosémica, 0.12 sola inerte < umbral)",
            ContextIntentKind.TRIGGER_WORDS.contains("biblioteca")
        )
    }

    // ─── Guards anti-overreach (NULL deseado) — verdes desde RED ────

    @Test
    fun `guards anti-overreach permanecen NULL biblioteca`() {
        assertNull("negación inmediata (lookbehind)", analyze("no ir a la biblioteca mañana"))
        assertNull("negación cinturón y tirantes", analyze("no ir a la biblioteca"))
        assertNull("duda (hedge c.649)", analyze("quizá vaya a la biblioteca mañana"))
        assertNull("narrativa pasado", analyze("fui a la biblioteca ayer"))
        assertNull(
            "declarativo sin imperativo (keyword sola 0.12 inerte < umbral)",
            analyze("la biblioteca está cerrada los domingos")
        )
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas intactas biblioteca`() {
        // Destinos hermanos del piso c.647 (la plantilla lockstep mejora
        // además sus títulos con acuse/prefijo — mejora aditiva).
        val banco = analyze("ir al banco mañana") // piso c.639/c.647
        assertNotNull(banco)
        assertEquals(ContextIntentKind.ERRAND, banco!!.kind)
        assertEquals("Ir al banco", banco.title)

        val cajero = analyze("ir al cajero mañana") // piso c.893
        assertNotNull(cajero)
        assertEquals(ContextIntentKind.ERRAND, cajero!!.kind)
        assertEquals("Ir al cajero", cajero.title)

        val pasar = analyze("pasar por el banco el viernes") // piso STOPBY c.718
        assertNotNull(pasar)
        assertEquals(ContextIntentKind.ERRAND, pasar!!.kind)

        // «devolver el libro a la biblioteca» ya captura vía el piso de
        // devolución c.682: no colide con el nuevo destino.
        val devolver = analyze("devolver el libro a la biblioteca mañana")
        assertNotNull(devolver)
        assertEquals(ContextIntentKind.ERRAND, devolver!!.kind)

        // Rutas deliberadas hermanas (no son diligencias).
        val medico = analyze("ir al médico mañana") // APPOINTMENT c.639
        assertNotNull(medico)
        assertEquals(ContextIntentKind.APPOINTMENT, medico!!.kind)

        val gimnasio = analyze("ir al gimnasio mañana") // EXERCISE c.639
        assertNotNull(gimnasio)
        assertEquals(ContextIntentKind.EXERCISE, gimnasio!!.kind)

        // «tengo que …» es TAREA (route deliberada c.690, empate TASK
        // primero en el enum): la keyword-DESTINO no la desvía a ERRAND.
        val obligacion = analyze("tengo que ir a la biblioteca el lunes")
        assertNotNull(obligacion)
        assertEquals(ContextIntentKind.TASK, obligacion!!.kind)
    }
}

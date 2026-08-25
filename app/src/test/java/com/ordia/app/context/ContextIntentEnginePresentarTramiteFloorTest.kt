package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.1134 — «presentar <documento/trámite>»: candidata (a) de la
 * clase DECIMOQUINTA (burocracia/administración pública), medida NULL
 * 3/3 en c.1132 por `tools/probe/FifteenthClassAdminProbe.kt` y 9/9 en
 * la sonda PRE efímera de este ciclo (`/tmp/PreProbePresentar.kt`,
 * motor real vía `tools/run_probe.sh`). Sin piso ni keyword el verbo
 * «presentar» no alcanza el análisis (0.0) y la frase se DESCARTA
 * silenciosamente. Consecuencia real: olvido de plazos oficiales con
 * coste directo (recurso de multa caducado, matrícula fuera de plazo,
 * ayuda del alquiler perdida).
 *
 * El piso vive en [ContextIntentEngine.hasStrongTaskImperative] y exige:
 * ancla de inicio/acuse/prefijo temporal, guard anti-negación `(?<!no )`,
 * determinante opcional (el/la/los/las/mi/tu/su) y objeto ACOTADO a
 * trámites monosemánticos: recurso(s), matrícula(s), papeles,
 * instancia(s), alegación(es), solicitud(es), documentación,
 * escrito(s).
 * Las tres formas medidas (recurso/matrícula/papeles) más las hermanas
 * burocráticas del mismo piso (instancia/alegaciones/solicitud/
 * documentación/escrito). El resto de objetos sigue FUERA (doctrina
 * anti-overreach: extensiones medidas NULL ciclo a ciclo).
 *
 * Bivalencias excluidas por diseño (guards, medidas NULL en la sonda
 * PRE): «presentar a los invitados» (a + persona — el piso exige
 * determinante + objeto de la lista), «presentar el programa» (objeto
 * fuera de la lista), «va a presentar los resultados» (3ª persona +
 * objeto fuera de la lista). «presentar» NO es monosemántico (a
 * diferencia de «escanear» c.864 o «votar» c.752), así que la
 * keyword-VERBO pesca la frase para el análisis (0.12 inerte sola,
 * bajo el umbral) y es el PISO quien decide: las bivalentes sin piso
 * quedan NULL (aritmética c.859…c.864).
 *
 * Kind TASK (no ERRAND): acción completable; precedentes hermanos
 * c.863/c.875 «la declaración de la renta», c.865 «reclamar la
 * factura». El piso c.875 «presentar la declaración de la renta» es
 * más específico y va ANTES (regresión hermana cubierta abajo: título
 * y kind idénticos).
 *
 * Lockstep lección c.616 (piso + plantilla de título en extractTitle,
 * tras la plantilla específica c.875). La keyword-VERBO «presentar» ya
 * existía en [ContextIntentKind.TASK] desde c.875 (lección c.751): sin
 * piso solo pesca la frase para el análisis (0.12 + temporal 0.1 =
 * 0.22 < umbral), así que los gaps seguían NULL. Guard NULL →
 * regresión de captura (precedente c.843).
 *
 * Cobertura: 8 capturas (3 medidas + 5 hermanas burocráticas) + 1
 * compuesta + 8 guards (negación compuesta, pretérito, duda
 * subjuntivo, bivalente programa, a+persona, 3ª persona resultados,
 * sustantivo suelto, verbo aislado) + regresión hermana c.875.
 */
class ContextIntentEnginePresentarTramiteFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `las tres formas medidas NULL en c1132 capturan TASK con titulo limpio`() {
        val r1 = analyze("presentar el recurso de la multa esta semana")
        assertNotNull("«presentar el recurso de la multa esta semana» debe capturar (era NULL en c.1132)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("presentar la matrícula del máster antes del viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        // Hallazgo heredado (NO de este piso): el recorte temporal de
        // «antes del <día>» deja el residuo «antes» en el título
        // («Presentar la matrícula del máster antes», medido con sonda
        // efímera /tmp/ProbeDebug.kt en este ciclo). dueAt SÍ se ancla
        // bien. Se aserta el prefijo fiel y el anclaje; el residuo se
        // registra en BACKLOG como candidata cosmética de títulos.
        assertEquals(true, r2.title.startsWith("Presentar la matrícula del máster"))
        assertNotNull("«antes del viernes» debe anclar dueAt", r2.dueAt)

        val r3 = analyze("presentar los papeles de la ayuda del alquiler mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Presentar los papeles de la ayuda del alquiler", r3.title)
        assertNotNull("«mañana» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `hermanas burocraticas del mismo piso capturan TASK`() {
        val r1 = analyze("presentar la instancia en el registro mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Presentar la instancia en el registro", r1.title)

        val r2 = analyze("presentar las alegaciones de la multa el lunes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Presentar las alegaciones de la multa", r2.title)
        assertNotNull("«el lunes» debe anclar dueAt", r2.dueAt)

        val r3 = analyze("presentar la solicitud de la beca esta tarde")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Presentar la solicitud de la beca", r3.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r3.dueAt)

        // Prefijo de acuse despojado del título (lección c.616: el
        // match arranca en el verbo).
        val r4 = analyze("vale, presentar la documentación del piso mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Presentar la documentación del piso", r4.title)

        // «un escrito»: el determinante indefinido casa vía el grupo
        // opcional ampliado? NO — el piso exige el/la/los/las/mi/tu/su;
        // «presentar un escrito…» queda FUERA (lateral candidata).
        // La forma medida usa determinante definido:
        val r5 = analyze("presentar los escritos en el juzgado el jueves")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.TASK, r5!!.kind)
        assertEquals("Presentar los escritos en el juzgado", r5.title)
    }

    @Test
    fun `forma compuesta captura conservando el resto de la frase`() {
        val r = analyze("presentar el recurso de la multa y pagar la tasa mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar el recurso de la multa y pagar la tasa", r.title)
        assertNotNull(r.dueAt)
    }

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación compuesta", analyze("no voy a presentar el recurso mañana"))
        assertNull("narrativa pretérito", analyze("presenté el recurso ayer"))
        assertNull("duda subjuntivo", analyze("quizá presente el recurso mañana"))
        assertNull("bivalente: objeto fuera de la lista", analyze("presentar el programa de la fiesta"))
        assertNull("bivalente: a + persona", analyze("presentar a los invitados mañana"))
        assertNull("3ª persona + objeto fuera de la lista", analyze("va a presentar los resultados el lunes"))
        assertNull("sustantivo suelto", analyze("el recurso de la multa"))
        assertNull("verbo aislado", analyze("presentar"))
    }

    @Test
    fun `regresion hermana c875 presentar la declaracion de la renta`() {
        val r = analyze("presentar la declaración de la renta este mes")
        assertNotNull("el piso específico c.875 no debe romperse", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar la declaración de la renta este mes", r.title)
    }
}

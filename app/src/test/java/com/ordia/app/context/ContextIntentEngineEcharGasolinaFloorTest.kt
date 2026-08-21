package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.829 (P1 olvido silencioso en captura pasiva — «echar gasolina» sin piso;
 * una forma por ciclo, doctrina anti-overreach c.822): "echar gasolina esta
 * tarde"/"ir a echar gasolina mañana" se DESCARTABAN (analyze → NULL),
 * verificado por sonda JVM fuente real PRE-fix (`tools/probe/
 * CaptureCoverageProbe.kt` c.822; pool de dispersión por epoch-day eligió
 * «echar gasolina», 2/4 del pool restante). Fix: piso ERRAND de posición
 * libre [ContextIntentEngine] `ERRAND_FUEL_FLOOR` ACOTADO al objeto
 * combustible (`gasolina|gasoil|diésel`) sobre el verbo bivalente "echar"
 * (echar agua/de menos/a perder/la culpa — criterio de acotamiento al objeto
 * de c.684/c.717/c.728/c.731/c.751) + keyword-OBJETO "gasolina" en ERRAND
 * (lockstep c.713/c.773; alimenta TRIGGER_WORDS, lección c.751) + plantilla
 * de título "echar gasolina"→"Echar gasolina" (lección c.616, match arranca
 * en el verbo; [sanitizeTitle] depura el residuo temporal de cola) +
 * cláusula de negación en [imperativeIsNegated] (cinturón y tirantes,
 * precedente c.717/c.748/c.757). Kind decidido: ERRAND (desplazamiento a la
 * gasolinera, hermano de "llevar el coche al taller" c.684), deliberado
 * contra TASK (no es gestión abstracta) y SHOPPING (el verbo cotidiano es
 * "echar", no "comprar"). Anti-overreach: `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el declarativo "la gasolina está cara" no
 * casa piso, la 1ª persona pasada "eché" no casa. La orden inversa
 * "gasolina: echar antes del viaje" era candidata documentada y se
 * implementó en c.832 (ver [ContextIntentEngineGasolinaInversaFloorTest]).
 * Determinista (regex), sin random, sin IA fingida.
 *
 * c.833 (P1 olvido silencioso — candidata documentada desde c.829; sonda
 * PRE-fix `/tmp/probe833/EcharlePreProbe.kt`: 7/7 capturas NULL): la
 * familia enclítica «echarle/echarles <combustible> [al coche]» — la forma
 * MÁS cotidiana de la orden — no casaba el piso `echar\s+` (el pronombre
 * dativo "le/les" va pegado al infinitivo). Fix: `(?:les?)?` opcional en
 * lockstep en los tres puntos de siempre (piso `ERRAND_FUEL_FLOOR` —
 * alternativa DIRECTA; el orden inverso c.832 no se toca — + cláusula de
 * negación de [imperativeIsNegated] + plantilla de título, que preserva el
 * pronombre: "echarle gasolina al coche" → "Echarle gasolina al coche",
 * doctrina c.653). Anti-overreach heredado intacto: "no echarle gasolina"
 * (lookbehind + cláusula), "quizá…", narrativa pasada ("le eché/le
 * echamos"), bivalentes ("echarle agua al radiador"/"echarle la culpa") y
 * el envolvente ("recuérdame echarle…" → TASK vía fuente única
 * `ERRAND_FLOORS`) siguen exactamente igual. [COLISIÓN cycle-ID:
 * provisional c.832 tomado por el hermano (orden inversa); renumerado
 * c.832→c.833, convención c.655/c.826/c.829.]
 */
class ContextIntentEngineEcharGasolinaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "echar <combustible>" es una diligencia clara ---

    @Test
    fun echarGasolinaEstaTarde_capturesErrandWithDueAt() {
        val intent = analyze("echar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun irAEcharGasolinaManana_capturesErrandWithDueAt() {
        val intent = analyze("ir a echar gasolina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarGasolinaSinFecha_capturesErrandWithoutDueAt() {
        val intent = analyze("echar gasolina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
    }

    @Test
    fun echarGasolinaTrasAcuse_capturesErrand() {
        val intent = analyze("vale, echar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarGasolinaTrasPrefijoTemporal_capturesErrand() {
        val intent = analyze("mañana echar gasolina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarGasoilManana_capturesErrand() {
        val intent = analyze("echar gasoil mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasoil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarDieselManana_capturesErrand() {
        val intent = analyze("echar diésel mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar diésel", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Envolventes: el verbo subordinado es CONTENIDO, no diligencia
    // autónoma (guard c.652 vía fuente única ERRAND_FLOORS) ---

    @Test
    fun recuerdameEcharGasolina_wrapperGovernsAsTask() {
        val intent = analyze("recuérdame echar gasolina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hayQueEcharGasolina_wrapperGovernsAsTask() {
        val intent = analyze("hay que echar gasolina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix) ---

    @Test
    fun noEcharGasolina_negatedStaysNull() {
        assertNull(analyze("no echar gasolina mañana"))
    }

    @Test
    fun quizasEcharGasolina_hedgedStaysNull() {
        assertNull(analyze("quizá echar gasolina mañana"))
    }

    @Test
    fun echeGasolinaAyer_pastNarrativeStaysNull() {
        assertNull(analyze("eché gasolina ayer"))
    }

    @Test
    fun laGasolinaEstaCara_declarativeStaysNull() {
        assertNull(analyze("la gasolina está cara"))
    }

    @Test
    fun gasolinaEcharAntesDelViaje_reverseOrderNowCapturesErrand() {
        // c.832: la candidata documentada se implementó — la alternativa
        // inversa de ERRAND_FUEL_FLOOR captura la taquigrafía "tema: acción"
        // y el título reordena a la forma canónica verbo-primero. Suite
        // completa: ContextIntentEngineGasolinaInversaFloorTest.
        val intent = analyze("gasolina: echar antes del viaje")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina antes del viaje", intent.title)
    }

    @Test
    fun echarAguaAlRadiador_bivalentVerbObjectNotFuelStaysNull() {
        // "echar" bivalente: objeto no combustible no casa el piso acotado.
        assertNull(analyze("echar agua al radiador mañana"))
    }

    // --- c.833: familia enclítica «echarle/echarles <combustible>» ---

    @Test
    fun echarleGasolinaAlCoche_capturesErrand() {
        val intent = analyze("echarle gasolina al coche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle gasolina al coche", intent.title)
    }

    @Test
    fun echarleGasolinaAlCocheEstaTarde_capturesErrandWithDueAt() {
        val intent = analyze("echarle gasolina al coche esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle gasolina al coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarleGasoilAlCocheManana_capturesErrand() {
        val intent = analyze("echarle gasoil al coche mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle gasoil al coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarleDieselALaFurgonetaEstaNoche_capturesErrand() {
        val intent = analyze("echarle diésel a la furgoneta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle diésel a la furgoneta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarlesGasolinaALosCochesElSabado_capturesErrandPluralEnclitic() {
        val intent = analyze("echarles gasolina a los coches el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarles gasolina a los coches", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun echarleGasolinaTrasAcuse_capturesErrand() {
        val intent = analyze("vale, echarle gasolina al coche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle gasolina al coche", intent.title)
    }

    @Test
    fun echarleGasolinaTrasPrefijoTemporal_capturesErrand() {
        val intent = analyze("hoy echarle gasolina al coche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echarle gasolina al coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach c.833 (deben permanecer NULL; todos
    // verificados NULL en la sonda POST-fix) ---

    @Test
    fun noEcharleGasolina_negatedStaysNull() {
        assertNull(analyze("no echarle gasolina al coche"))
    }

    @Test
    fun quizasEcharleGasolina_hedgedStaysNull() {
        assertNull(analyze("quizá echarle gasolina al coche"))
    }

    @Test
    fun leEcheGasolinaAyer_pastNarrativeStaysNull() {
        assertNull(analyze("le eché gasolina al coche ayer"))
    }

    @Test
    fun leEchamosGasolinaAyer_pastNarrativePluralStaysNull() {
        assertNull(analyze("le echamos gasolina al coche ayer"))
    }

    @Test
    fun echarleAguaAlRadiador_bivalentObjectNotFuelStaysNull() {
        assertNull(analyze("echarle agua al radiador"))
    }

    @Test
    fun echarleLaCulpa_bivalentIdiomStaysNull() {
        assertNull(analyze("echarle la culpa a alguien"))
    }

    @Test
    fun recuerdameEcharleGasolina_wrapperGovernsAsTask() {
        val intent = analyze("recuérdame echarle gasolina al coche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echarle gasolina al coche", intent.title)
    }
}

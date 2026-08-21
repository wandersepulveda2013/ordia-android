package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.832 (P1 olvido silencioso en captura pasiva — orden inverso
 * «gasolina: echar antes del viaje»; una forma por ciclo, doctrina
 * anti-overreach c.822): la taquigrafía de nota rápida "tema: acción" (el
 * objeto encabeza y el ":" estructural marca el atajo) se DESCARTABA
 * (analyze → NULL), último NULL de captura documentado por la sonda JVM
 * fuente real `tools/probe/CaptureCoverageProbe.kt` (c.822) y asignado como
 * siguiente unidad en c.831. Fix (lockstep, precedente c.829/c.831):
 * alternativa inversa en el piso [ContextIntentEngine] `ERRAND_FUEL_FLOOR`
 * ACOTADA al objeto combustible (`gasolina|gasoil|diésel`) con ":" exigido
 * como ancla inequívoca de la notación (sin él "gasolina echar mañana"
 * sigue NULL) + plantilla de título que reordena a la forma canónica
 * verbo-primero "Echar gasolina antes del viaje" (lección c.616: el match
 * arranca en el objeto, así el acuse no ensucia; doctrina c.653: ortografía
 * del objeto preservada; [sanitizeTitle] depura el residuo temporal y
 * conserva el contenido "…antes del viaje") + cláusula inversa en
 * [imperativeIsNegated] (cinturón y tirantes, precedente c.748/c.757).
 * Anti-overreach: `(?<!no )` bloquea la negada; flag `(?U)` (límites de
 * palabra Unicode — el `\b` ASCII consideraba "echar|é" frontera y
 * capturaba el futuro "echaré", verificado en sonda PRE-fix) deja fuera el
 * pasado "eché" y el futuro "echaré" (verbo exacto); c.649 mantiene
 * "quizá…"→NULL; el declarativo "la gasolina está cara" no casa. Las formas
 * directas de c.829 ("echar gasolina…") y c.831 ("repostar…") no cambian.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineGasolinaInversaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «objeto: echar…» es la misma diligencia en taquigrafía ---

    @Test
    fun gasolinaEcharAntesDelViaje_capturesErrandPreservingContent() {
        val intent = analyze("gasolina: echar antes del viaje")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina antes del viaje", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun gasoilEcharManana_capturesErrandWithDueAt() {
        val intent = analyze("gasoil: echar mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasoil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun dieselEcharEstaTarde_capturesErrandWithDueAt() {
        val intent = analyze("diésel: echar esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar diésel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun gasolinaEcharSinFecha_capturesErrandWithoutDueAt() {
        val intent = analyze("gasolina: echar")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun gasolinaEcharTrasAcuse_capturesErrand() {
        val intent = analyze("vale, gasolina: echar mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Envolvente: el verbo subordinado es CONTENIDO, no diligencia
    // autónoma (guard c.652 vía fuente única ERRAND_FLOORS) ---

    @Test
    fun recuerdameEcharGasolina_wrapperStillGovernsAsTask() {
        val intent = analyze("recuérdame echar gasolina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE/POST-fix) ---

    @Test
    fun gasolinaNoEchar_negatedStaysNull() {
        assertNull(analyze("gasolina: no echar antes del viaje"))
    }

    @Test
    fun quizasGasolinaEchar_hedgedStaysNull() {
        assertNull(analyze("quizá gasolina: echar mañana"))
    }

    @Test
    fun gasolinaEcheAyer_pastNarrativeStaysNull() {
        assertNull(analyze("gasolina: eché ayer"))
    }

    @Test
    fun gasolinaEchareManana_futureNarrativeStaysNull() {
        assertNull(analyze("gasolina: echaré mañana"))
    }

    @Test
    fun gasolinaEcharSinDosPuntos_noAnchorStaysNull() {
        assertNull(analyze("gasolina echar mañana"))
    }

    @Test
    fun laGasolinaEstaCara_declarativeStaysNull() {
        assertNull(analyze("la gasolina está cara"))
    }

    @Test
    fun gasolinaSola_bareKeywordStaysNull() {
        assertNull(analyze("gasolina"))
    }

    // --- Regresiones: las formas directas de c.829/c.831 no cambian ---

    @Test
    fun echarGasolinaEstaTarde_directFormStillCaptures() {
        val intent = analyze("echar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun repostarGasolinaEstaTarde_directFormStillCaptures() {
        val intent = analyze("repostar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }
}

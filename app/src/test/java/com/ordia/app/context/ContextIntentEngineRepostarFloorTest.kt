package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.831 (P1 olvido silencioso en captura pasiva — «repostar» sin piso; una
 * forma por ciclo, doctrina anti-overreach c.822; candidata documentada en
 * c.829): "repostar el coche mañana"/"repostar gasolina esta tarde" se
 * DESCARTABAN (analyze → NULL), verificado por sonda JVM fuente real PRE-fix
 * (6 capturas NULL, 6 guards NULL, envolvente TASK 0.49 — asimetría de ruta
 * idéntica a c.822…c.830). "repostar" es verbo monosémico (proveer de
 * combustible; sin acepción figurada frecuente, a diferencia del bivalente
 * "echar" de c.829), así el piso es de POSICIÓN LIBRE (precedente c.727
 * "tender"/c.828 "vaciar"): se añade a `ERRAND_VERBS` y fluye
 * automáticamente al piso [hasStrongErrandImperative], al guard de negación
 * [imperativeIsNegated] y al guard de envolvente [imperativeIsWrapped]
 * (fuente única, lección c.648/c.652). Lockstep (lección c.639/c.751):
 * keyword-VERBO "repostar" en `ContextIntentKind.ERRAND.keywords` (alimenta
 * TRIGGER_WORDS, sin lo cual la notificación ni llegaría al análisis;
 * monosémico → keyword verbo, no objeto) + plantilla de título "repostar X"
 * →"Repostar X" (lección c.616, match arranca en el verbo; [sanitizeTitle]
 * depura el residuo temporal de cola). Kind deliberado: ERRAND
 * (desplazamiento a la gasolinera, hermano de "echar gasolina" c.829 y de
 * "llevar el coche al taller" c.684). Anti-overreach: `(?<!no )` bloquea la
 * negada, c.649 mantiene "quizá…"→NULL, `\s+\w` exige objeto (verbo suelto
 * no casa), pasados "reposté"/"repostamos" no contienen "repostar", futuro
 * conjugado "repostaré" no casa `\b` y keyword+bono 0.22 < umbral, sustantivo
 * "repostaje" no casa. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRepostarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "repostar" es una diligencia clara (desplazamiento a
    // la gasolinera) ---

    @Test
    fun repostarElCocheManana_capturesErrandWithDueAt() {
        val intent = analyze("repostar el coche mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar el coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun repostarGasolinaEstaTarde_capturesErrandWithDueAt() {
        val intent = analyze("repostar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun repostarGasoilAntesDelViaje_capturesErrand() {
        // "antes del viaje" no es residuo temporal: se conserva en el título
        // (honesto, es contenido); sin fecha explícita → dueAt null.
        val intent = analyze("repostar gasoil antes del viaje")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar gasoil antes del viaje", intent.title)
    }

    @Test
    fun repostarSinFecha_capturesErrandWithoutDueAt() {
        val intent = analyze("repostar el coche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar el coche", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun repostarTrasAcuse_capturesErrand() {
        val intent = analyze("vale, repostar diésel esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar diésel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun repostarTrasPrefijoTemporal_capturesErrand() {
        val intent = analyze("hoy repostar en la autopista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostar en la autopista", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Envolvente: el verbo subordinado es CONTENIDO, no diligencia
    // autónoma (guard c.652 vía fuente única ERRAND_FLOORS) ---

    @Test
    fun recuerdameRepostar_wrapperGovernsAsTask() {
        val intent = analyze("recuérdame repostar el coche mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Repostar el coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Lockstep keyword (lección c.751): sin ella la notificación sin
    // palabra gatillo ni llega al análisis en producción ---

    @Test
    fun repostar_isInTriggerWords() {
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("repostar"))
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix) ---

    @Test
    fun noRepostar_negatedStaysNull() {
        assertNull(analyze("no repostar el coche"))
    }

    @Test
    fun quizasRepostar_hedgedStaysNull() {
        assertNull(analyze("quizá repostar gasolina mañana"))
    }

    @Test
    fun reposteAyer_pastNarrativeStaysNull() {
        assertNull(analyze("reposté en la autopista ayer"))
    }

    @Test
    fun repostamosAyer_pastPluralNarrativeStaysNull() {
        assertNull(analyze("repostamos gasoil ayer"))
    }

    @Test
    fun elRepostaje_nounStaysNull() {
        assertNull(analyze("el repostaje fue rápido"))
    }

    @Test
    fun repostareManana_futureConjugatedStaysNull() {
        // "repostaré" no casa el piso (`\b` tras "repostar" falla con "é");
        // keyword 0.12 + bono temporal 0.1 = 0.22 < umbral 0.45.
        assertNull(analyze("repostaré el coche mañana"))
    }

    @Test
    fun repostarSuelto_bareVerbStaysNull() {
        assertNull(analyze("repostar"))
    }

    // --- Regresión: el piso acotado de combustible (c.829) no es robado ---

    @Test
    fun echarGasolina_fuelFloorNotStolen() {
        val intent = analyze("echar gasolina esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Echar gasolina", intent.title)
        assertNotNull(intent.dueAt)
    }
}

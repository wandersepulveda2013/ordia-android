package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1169 (P1 olvido silencioso en captura pasiva; renumerada
 * c.1168→c.1169 por carrera de marcador: el hermano fijó c.1168
 * «facturar la maleta» primero en el remoto, primer-marcador-gana,
 * lección c.1077): lateral (d-bis) de MI cierre c.1156 — forma C20
 * «hacer la mudanza…» de la sonda persistida
 * `tools/probe/FifteenthClassAdminProbe.kt` (c.1132, clase DECIMOQUINTA
 * burocracia/mudanza), NULL medido allí y re-medido PRE con sonda
 * efímera (motor real vía `tools/run_probe.sh`, HEAD base
 * `1bd44e21`): 4/4 capturas NULL (pelada, «el sábado»,
 * «el fin de semana», «una mudanza este mes»), guards 6/6
 * NULL (negación, pasado, subjuntivo-duda, nominal, ayudar-a, 3ª
 * persona), pines 6/6 (compra SHOPPING, curso c.1152, maleta c.715,
 * deberes STUDY, envolvente «tengo que…» TASK 0.45, acuse
 * «recuérdame…» TASK 0.54). «hacer la mudanza el
 * sábado» se DESCARTABA (analyze → NULL) pese a llevar
 * temporal explícito: «hacer» keyword TASK lleva la frase al
 * análisis (gate c.751 satisfecho) pero todo queda < 0.45. Olvido
 * real: la mudanza es la gestión doméstica de mayor coste de
 * coordinación (camiones, ayudantes, ascensor, plazos de entrega de
 * llaves); perder el día acordado cuesta dinero y semanas.
 *
 * Fix lockstep DOS puntos (lección c.616/c.751 — CERO keywords
 * nuevas), hermano EXACTO de c.1152 «hacer el curso» y c.1140
 * «hacer el check-in del vuelo» (mismo verbo «hacer»,
 * objeto EXIGIDO acotado): (1) piso acotado «hacer (det)? mudanzas?»
 * en hasStrongTaskImperative (ancla ^|acuse|temporal + lookbehind
 * «no » heredados; objeto «mudanza» EXIGIDO — como
 * objeto de «hacer» es monosemántico-traslado, sin acepción
 * bivalente frecuente; «hacer la mudanza» solo significa
 * trasladarse de vivienda); (2) plantilla hermana matchHacerMudanza en
 * extractTitle (grafía preservada, doctrina c.653; residuo temporal
 * lo depura sanitizeTitle). Determinantes/posesivos/indefinidos/
 * demostrativos/plural casan («una mudanza», «esta mudanza»).
 *
 * Kind TASK (gestión logística puntual con plazo, hermana de
 * «hacer la maleta» c.715 y «hacer el curso» c.1152).
 * Determinista (regex), sin random, sin IA fingida. Alcance: SOLO
 * infinitivo «hacer»; presente «hago», futuro
 * «haré» y subjuntivo «haga» quedan laterales (UNA
 * forma por ciclo, doctrina anti-overreach).
 */
class ContextIntentEngineHacerMudanzaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «hacer (la)? mudanza» es una tarea clara ---
    // Títulos/dueAt pineados tras medir POST con sonda efímera
    // (motor real, tools/run_probe.sh).

    @Test
    fun hacerLaMudanza_capturesTask() {
        val intent = analyze("hacer la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza", intent.title)
    }

    @Test
    fun hacerLaMudanzaSabado_capturesTaskWithDueAt() {
        val intent = analyze("hacer la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLaMudanzaDelPiso_capturesTask() {
        val intent = analyze("hacer la mudanza del piso el fin de semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hacerUnaMudanza_capturesTaskIndefinido() {
        val intent = analyze("hacer una mudanza este mes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun valeHacerLaMudanza_capturesTaskAckPrefix() {
        val intent = analyze("vale, hacer la mudanza del piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza del piso", intent.title)
    }

    @Test
    fun mananaHacerLaMudanza_capturesTaskTemporalPrefix() {
        // Pin medido POST (sonda efímera, motor real): la ruta de
        // extractTitle previa a la rama c.899 conserva el prefijo
        // temporal en el título (dueAt sí resuelve). Captura NUEVA
        // gracias a este piso (PRE medido sobre HEAD: NULL).
        val intent = analyze("mañana hacer la mudanza")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mañana hacer la mudanza", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Envolvente: candado c.613 (pin byte-idéntico, ya capturaba) ---

    @Test
    fun tengoQueHacerLaMudanza_remainsTaskWrapper() {
        val intent = analyze("tengo que hacer la mudanza del piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la mudanza del piso", intent.title)
    }

    // --- Guards (deben quedarse NULL) ---

    @Test
    fun negadaNoHacerMudanza_isNull() {
        assertNull(analyze("no hacer la mudanza todavía"))
    }

    @Test
    fun pasadoHiceMudanza_isNull() {
        assertNull(analyze("hice la mudanza ayer"))
    }

    @Test
    fun futuroHareMudanza_isNull() {
        assertNull(analyze("haré la mudanza en octubre"))
    }

    @Test
    fun dudaSubjuntivoHagaMudanza_isNull() {
        assertNull(analyze("quizá haga la mudanza en octubre"))
    }

    @Test
    fun hedgeNoSeSiHacerMudanza_isNull() {
        assertNull(analyze("no sé si hacer la mudanza"))
    }

    @Test
    fun nominalMudanzaCara_isNull() {
        assertNull(analyze("la mudanza del piso es cara"))
    }

    @Test
    fun ayudarAmigoMudanza_isNull() {
        // Región del piso «ayudar a <persona>» (c.1102-bis), no
        // de este piso: «hacer» no aparece → NULL garantizado.
        assertNull(analyze("ayudar a un amigo con la mudanza"))
    }

    @Test
    fun presenteHagoMudanza_capturesTaskLateral() {
        // Pin c.1171 (lateral (d-bis) de este piso): el presente 1ª
        // persona «hago la mudanza» quedó NULL en c.1169 (una-forma-
        // por-ciclo) y este lateral lo habilita con la extensión
        // aditiva del verbo (hacer|hago). Pin medido POST con sonda
        // efímera (motor real, HEAD con lockstep aplicado).
        val intent = analyze("hago la mudanza esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun terceraPersonaHaceMudanza_isNull() {
        assertNull(analyze("mi hermano hace la mudanza mañana"))
    }

    // --- Regresiones (formas que YA capturan — pin byte-idéntico) ---

    @Test
    fun hacerLaCompra_remainsShopping() {
        val intent = analyze("hacer la compra mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun hacerLaMaleta_remainsTask() {
        val intent = analyze("hacer la maleta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hacerDeberes_remainsStudy() {
        val intent = analyze("hacer deberes mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun hacerElCurso_remainsTask() {
        val intent = analyze("hacer el curso de prevención antes del día 30")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * c.959 (P2 calidad de título — la resolución canónica c.587 era correcta;
 * el hueco era el título). «recoger el paquete al mediodía» / «revisar el
 * informe a la medianoche» enrutaban y fechaban bien (paridad c.587 existente),
 * pero su título nacía con el conector huérfano: «Recoger el paquete al». La
 * rama numérica ya consume su prefijo («a las ») al despojar el residuo temporal;
 * la rama canónica («medianoche» / «mediodía») despojaba SÓLO la palabra,
 * dejando colgado «al » / «a la ».
 * Verificado PRE con sonda efímera `/tmp/probe959/` (4/4 capturas con residuo,
 * guards correctos). Fix CENTRAL en `stripTrailingTemporalResidue` (vale para
 * todos los kinds, lección c.616): la alternativa canónica con conector
 * («al »/«a la ») se evalúa primero; la forma desnuda sigue cubierta por la
 * rama sin conector. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineCanonicalTimeResidueTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    private fun hourOf(epochMillis: Long): Int =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).hour

    // --- Capturas RED: el conector se consume con la palabra canónica ---

    @Test
    fun recogerElPaqueteAlMediodia_noOrphanConnector() {
        val intent = analyze("recoger el paquete al mediodía")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Recoger el paquete", intent.title)
        assertNotNull(intent.dueAt)
        assertEquals("'al mediodía' sigue siendo 12:00 tras despojar el conector", 12, hourOf(intent.dueAt!!))
    }

    @Test
    fun traerElCuadernoAIreneAlMediodia_noOrphanConnector() {
        val intent = analyze("traer el cuaderno a Irene al mediodía")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Traer el cuaderno a Irene", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun revisarElInformeALaMedianoche_noOrphanConnector() {
        val intent = analyze("revisar el informe a la medianoche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
        assertEquals("'a la medianoche' sigue siendo 00:00 tras despojar el conector", 0, hourOf(intent.dueAt!!))
    }

    @Test
    fun revisarElInformeAMedianoche_noOrphanConnector() {
        val intent = analyze("revisar el informe a medianoche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cogerElTrenAlMediodia_noOrphanConnector() {
        val intent = analyze("coger el tren al mediodía")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Coger el tren", intent.title)
    }

    @Test
    fun entregarElInformeAlMediodia_noOrphanConnectorTasksKind() {
        val intent = analyze("entregar el informe al mediodía")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
    }

    // --- Guards: conducta vigente byte-idéntica ---

    @Test
    fun revisarElInformeALas3_numericBranchUnaffected() {
        val intent = analyze("revisar el informe a las 3")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
    }

    @Test
    fun pagarAlContable_connectorContentPreserved() {
        val intent = analyze("pagar al contable")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        // El conector «al » sólo se consume cuando encabeza una palabra
        // canónica; ante contenido se conserva byte a byte.
        assertEquals("Pagar al contable", intent.title)
    }

    @Test
    fun hacerEjercicioPorLaManana_bandBranchUnaffected() {
        // c.688: la franja blanda se sigue despojando completa, sin residuo
        // «po la » / «por la »: mi alternativa canónica no la sobrepinta.
        val intent = analyze("hacer ejercicio por la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Hacer ejercicio", intent.title)
    }
}

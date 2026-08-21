package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * c.839 (P3 calidad de título — residual documentado por el hermano c.837
 * `15bf841`, fila BACKLOG P3 ABIERTA). «reservar el hotel para el sábado»
 * enrutaba bien (TASK 0.45, dueAt) pero el título nacía con una «para»
 * HUÉRFANA: «Reservar el hotel para» — el despoje de cola temporal
 * (`stripTrailingTemporalResidue`) elimina la fecha «el sábado» y deja
 * colgando la preposición que la introducía. Verificado PRE con sonda
 * efímera `/tmp/probe839/OrphanParaProbe.kt` (4 casos con «para» huérfana
 * en reservar/confirmar; guards correctos). Fix CENTRAL en
 * `stripTrailingTemporalResidue` (vale para todos los kinds, lección
 * c.616): sólo cuando ESTA iteración eliminó un anclaje temporal se
 * despoja además la «para» final huérfana que lo introducía — un título
 * que termina en contenido («para el abuelo») nunca se toca, y la guard
 * genitiva de días desnudos («para mañana») queda intacta. Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineOrphanParaTitleTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas RED: la «para» huérfana se despoja junto con la fecha ---

    @Test
    fun reservarElHotelParaElSabado_orphanParaStripped() {
        val intent = analyze("reservar el hotel para el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun reservarElRestauranteParaElViernes_orphanParaStripped() {
        val intent = analyze("reservar el restaurante para el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el restaurante", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun reservarElHotelParaSabadoSinArticulo_orphanParaStripped() {
        val intent = analyze("reservar el hotel para sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
    }

    @Test
    fun confirmarLaCitaParaElLunes_orphanParaStrippedCentral() {
        val intent = analyze("confirmar la cita para el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guards: contenido legítimo y guard genitiva intactos ---

    @Test
    fun reservarElHotelElSabado_sinParaSigueLimpio() {
        val intent = analyze("reservar el hotel el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
    }

    @Test
    fun reservarElHotelParaElAbueloElSabado_paraDeContenidoSeConserva() {
        val intent = analyze("reservar el hotel para el abuelo el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel para el abuelo", intent.title)
    }

    @Test
    fun reservarElHotelParaManana_guardGenitivaDiaDesnudoIntacta() {
        val intent = analyze("reservar el hotel para mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel para mañana", intent.title)
    }
}

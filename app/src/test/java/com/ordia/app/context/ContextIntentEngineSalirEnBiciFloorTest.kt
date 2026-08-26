package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1233: «salir en (bici|bicicleta)» (lateral (e) de la auditoría
 * c.1227 cl.XXX deporte, sonda persistida `tools/probe/SalirEnBiciProbe.kt`,
 * PRE 5/5 NULL targets — olvido silencioso: la salida de dos ruedas es
 * el compromiso vehicular del usuario, no una banalidad). Verbo «salir»
 * acotado al objeto-vehículo cerrado (bici|bicicleta), precedente
 * «pilates» c.1232 / «mueble» c.1224. Gate c.751: CERO keywords nuevas.
 * Lockstep DOS puntos (lección c.616): piso acotado + plantilla
 * matchSalirEnBici en extractTitle. Kind: EXERCISE (hermana de «partido»/
 * «ir a pilates»). Guards pinadas: negación/pretérito/mutilación.
 */
class ContextIntentEngineSalirEnBiciFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // T1
    @Test
    fun `salir en bici por la manana es exercise`() {
        val intent = analyze("salir en bici por la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Salir en bici", intent?.title)
    }

    // T2
    @Test
    fun `salir en bici el domingo es exercise`() {
        val intent = analyze("salir en bici el domingo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Salir en bici", intent?.title)
    }

    // T3
    @Test
    fun `salir en bicicleta por la tarde es exercise`() {
        val intent = analyze("salir en bicicleta por la tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Salir en bicicleta", intent?.title)
    }

    // T4
    @Test
    fun `voy a salir en bici es exercise`() {
        val intent = analyze("voy a salir en bici")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Salir en bici", intent?.title)
    }

    // T5
    @Test
    fun `salir en bici desnuda es exercise`() {
        val intent = analyze("salir en bici")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Salir en bici", intent?.title)
    }

    // Guards (NULL esperado — anti-overreach)
    @Test
    fun `guardia negacion es null`() {
        assertNull(analyze("no salir en bici"))
    }

    @Test
    fun `guardia preterito es null`() {
        assertNull(analyze("salí en bici ayer"))
    }

    @Test
    fun `guardia verbo mutilado es null`() {
        assertNull(analyze("salgamos en bici"))
    }

    // Regresiones (hermanas intactas)
    @Test
    fun `regresion ir al gimnasio sigue exercise`() {
        val intent = analyze("ir al gimnasio el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
    }

    @Test
    fun `regresion partido de tenis sigue exercise`() {
        val intent = analyze("partido de tenis el domingo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
    }
}

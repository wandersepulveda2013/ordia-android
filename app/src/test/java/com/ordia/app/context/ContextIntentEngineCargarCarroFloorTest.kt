package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1084: «cargar el carro» — candidata (b) de la clase DUODÉCIMA
 * (vida con vehículo), medida NULL por la sonda persistida
 * `tools/probe/TwelfthClassVehicleProbe.kt` (c.1079, C9) y re-medida
 * PRE en este ciclo con sonda efímera propia (4/5 capturas NULL —
 * la quinta «tengo que cargar…» ya capturaba por el piso modal —
 * 5/5 guards NULL, 3/3 regresiones HIT, HEAD `fe17a58`): «carro» es
 * LA forma LatAm de «coche» (hermana de «cole» c.850 y «móvil» c.851)
 * y caía a NULL.
 * Fix en los 3 puntos lockstep (lección c.616/c.717/c.751): la
 * alternancia del objeto en el piso acotado «cargar» (c.751) admite
 * `carros?` + keyword-OBJETO «carro» en [ContextIntentKind.TASK] (la
 * subcadena de «carrocería»/«carrito» suma 0.12 inerte < umbral,
 * mismo argumento que «cochera»/«automóvil») + la misma plantilla de
 * título acotada (el match arranca en el verbo y preserva las
 * palabras del usuario).
 * Deliberación de la bivalencia (heredada de c.853): «cargar el
 * carro» es bivalente REAL en LatAm (equipaje del viaje vs carga del
 * VE) pero AMBAS lecturas son deberes genuinos del usuario y el
 * título preserva sus palabras exactas sin desambiguar. Siguen FUERA
 * los objetos cuya segunda lectura NO es un deber personal («la
 * tarjeta», «el archivo»).
 */
class ContextIntentEngineCargarCarroFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // ---- Capturas directas (objeto «carro», diagonal LatAm) ----

    @Test
    fun `captura sonda esta noche`() {
        val intent = analyze("cargar el carro esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el carro", intent.title)
    }

    @Test
    fun `captura a secas`() {
        val intent = analyze("cargar el carro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el carro", intent.title)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = analyze("cargar mi carro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar mi carro", intent.title)
    }

    @Test
    fun `captura con acuse y cola`() {
        val intent = analyze("vale, cargar el carro antes de salir")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el carro antes de salir", intent.title)
    }

    // ---- Guards (siguen NULL) ----

    @Test
    fun `negada simple no captura`() {
        assertNull(analyze("no cargue el carro todavia"))
    }

    @Test
    fun `negada compuesta no captura`() {
        assertNull(analyze("no pienso cargar el carro esta noche"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("cargué el carro ayer"))
    }

    @Test
    fun `duda no captura`() {
        assertNull(analyze("quizá cargar el carro esta noche"))
    }

    @Test
    fun `keyword sola bajo el umbral`() {
        assertNull(analyze("el carro nuevo es eléctrico"))
    }

    // ---- Pins de acotación (objetos excluidos deliberados) ----

    @Test
    fun `tarjeta sigue fuera`() {
        assertNull(analyze("cargar la tarjeta"))
    }

    // ---- Regresiones (formas que YA capturan vía piso c.751) ----

    @Test
    fun `regresion coche`() {
        val intent = analyze("cargar el coche mañana")
        assertNotNull(intent)
        assertEquals("Cargar el coche", intent!!.title)
    }

    @Test
    fun `regresion celular`() {
        val intent = analyze("cargar el celular")
        assertNotNull(intent)
        assertEquals("Cargar el celular", intent!!.title)
    }

    @Test
    fun `regresion movil`() {
        val intent = analyze("cargar el móvil esta noche")
        assertNotNull(intent)
        assertEquals("Cargar el móvil", intent!!.title)
    }
}

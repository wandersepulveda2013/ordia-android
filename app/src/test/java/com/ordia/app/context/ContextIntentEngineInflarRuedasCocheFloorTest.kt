package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1105: «inflar las ruedas del coche» — candidata (d) de la clase
 * DUODÉCIMA (vida con vehículo), pinzada en c.1097 (una forma por
 * ciclo, doctrina anti-overreach) y medida PRE en este ciclo con
 * sonda efímera (D1 «inflar las ruedas del coche hoy» NULL, D2
 * «…del carro mañana» NULL, D3 «…de mi auto» NULL; regresión bici
 * HIT; canario «tengo que inflar las ruedas del coche» HIT por los
 * 0.45 del ancla; guards 6/6 NULL, HEAD `cea218b`): la contracción
 * «del» no la casa el piso c.1097 (ruedas? de (la )?(bicicleta|
 * bici|moto)) y caía a NULL con 0.22 (keyword-OBJETO «ruedas»
 * c.1082 + bono temporal 0.1). Inflar las ruedas del coche es EL
 * mantenimiento básico del coche (presión baja: desgaste irregular,
 * consumo extra, reventón) — misma clase que el hermano c.1097.
 * Fix en los 2 puntos lockstep (lección c.616/c.717; la keyword-
 * OBJETO «ruedas» sigue en su sitio desde c.1082 — CERO cambios en
 * `ContextIntent.kt`): extensión del objeto del piso anclado en
 * [ContextIntentEngine.hasStrongTaskImperative] con la rama del
 * vehículo de cuatro ruedas `(?:del\s+|de\s+(?:mi|tu|su)\s+)
 * (?:coche|carro|auto)` — «carro» (LatAm) y «auto» (Río de la
 * Plata) hermanos dialectales — + plantilla de título hermana
 * (el match arranca en el verbo y preserva las palabras del
 * usuario, «del» incluido).
 * Kind decidido: TASK, hermano de «inflar las ruedas de la bici»
 * c.1097, «poner las ruedas de invierno» c.1082 y «cambiar el
 * aceite» c.710 (deber de mantenimiento del vehículo).
 * Anti-overreach: `(?<!no )` bloquea la negada directa y la
 * guardia de plan/volición c.1009 las compuestas («no voy a
 * inflar…»); el pasado «inflé…», la duda «quizá infle…», el decoy
 * de objeto «inflar el precio del coche…» y el sintagma nominal
 * «las ruedas del coche» no casan.
 */
class ContextIntentEngineInflarRuedasCocheFloorTest {

    // ---- Capturas directas (piso, rama «del/de mi coche|carro|auto») ----

    @Test
    fun `captura sonda coche hoy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas del coche hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura carro manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas del carro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del carro", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura de mi auto sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de mi auto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de mi auto", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura del auto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas del auto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del auto", intent.title)
    }

    @Test
    fun `captura singular del coche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar la rueda del coche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar la rueda del coche", intent.title)
    }

    @Test
    fun `captura de tu carro`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de tu carro", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de tu carro", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, inflar las ruedas del coche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del coche", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana inflar las ruedas del coche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame inflar las ruedas del coche esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del coche", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no inflar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no inflar las ruedas del coche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `no voy a inflar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a inflar las ruedas del coche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda subjuntivo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá infle las ruedas del coche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado infle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflé las ruedas del coche ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pin camion sigue fuera`() {
        // c.1111 (complemento): pin de alcance — «inflar las ruedas del
        // camión» queda FUERA de la rama c.1105 (doctrina UNA forma por
        // ciclo). Si un ciclo futuro la captura, este pin se gira a
        // captura (precedente: pin c.1097 girado en c.1105).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas del camión", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `decoy objeto precio descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar el precio del coche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sintagma nominal descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las ruedas del coche", 1000)
        )
        assertNull(intent)
    }
}

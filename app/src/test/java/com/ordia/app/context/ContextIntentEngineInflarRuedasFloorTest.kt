package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1097: «inflar las ruedas de la bici/bicicleta/moto» — candidata (c)
 * de la clase DUODÉCIMA (vida con vehículo), medida NULL por la sonda
 * persistida `tools/probe/TwelfthClassVehicleProbe.kt` (c.1079, C12:
 * «inflar» sin piso ni keyword) y re-medida PRE en este ciclo con sonda
 * efímera propia (N1 «inflar las ruedas de la bici hoy» NULL, N2
 * «…bicicleta mañana» NULL, N4 singular «…la rueda de la bici» NULL;
 * guards 4/4 NULL; regresiones 4/4 HIT, HEAD `92e6a2b`): inflar las
 * ruedas es EL mantenimiento básico de la bici/moto (coste real de
 * olvido: rueda baja, llanta dañada, caída) y caía a NULL — «inflar»
 * no tenía piso (0.12 keyword-OBJETO «ruedas» c.1082 + bono temporal
 * 0.1 = 0.22 < 0.45). Con prefijo «tengo que…» ya capturaba por los
 * 0.45 del ancla (N3 del PRE: HIT 0.45 — canario de coherencia).
 * Fix en los 2 puntos lockstep (lección c.616/c.717; la keyword-OBJETO
 * «ruedas» YA estaba en su sitio desde c.1082 — CERO cambios en
 * `ContextIntent.kt`): piso anclado en
 * [ContextIntentEngine.hasStrongTaskImperative] ACOTADO al objeto
 * `ruedas? de (la )?(bicicleta|bici|moto)` — el vehículo de dos ruedas
 * es lo que hace inequívoco el mantenimiento; «inflar las ruedas del
 * coche» queda FUERA como candidata propia (una forma por ciclo, pin)
 * — + plantilla de título hermana (el match arranca en el verbo y
 * preserva las palabras del usuario).
 * Kind decidido: TASK, hermano de «poner las ruedas de invierno» c.1082
 * y «cambiar el aceite» c.710 (deber de mantenimiento del vehículo;
 * deliberación contra ERRAND — no implica desplazamiento enunciado —
 * y contra HOUSEHOLD — no es quehacer del hogar).
 * Anti-overreach: `(?<!no )` bloquea la negada directa y la guardia
 * de plan/volición c.1009 las compuestas («no voy a inflar…»); el
 * pasado «inflé…», la duda «quizá infle…», el decoy financiero
 * «inflar el saldo…» y el otro objeto «inflar globos…» no casan; la
 * keyword sola queda bajo el umbral.
 */
class ContextIntentEngineInflarRuedasFloorTest {

    // ---- Capturas directas (piso, objeto «ruedas de la bici/bicicleta/moto») ----

    @Test
    fun `captura sonda bici hoy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de la bici hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la bici", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura bicicleta manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de la bicicleta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la bicicleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura singular sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar la rueda de la bici", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar la rueda de la bici", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura moto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de la moto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la moto", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, inflar las ruedas de la bici", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la bici", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana inflar las ruedas de la bici", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la bici", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame inflar las ruedas de la bici esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas de la bici", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no inflar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no inflar las ruedas de la bici", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `no voy a inflar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a inflar las ruedas de la bici", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda subjuntivo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá infle las ruedas de la bici", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado infle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflé las ruedas de la bici ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sintagma nominal descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las ruedas de la bici", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `decoy financiero descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar el saldo de la tarjeta mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `otro objeto globos descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar globos para el cumpleaños el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `candidata d ruedas del coche girada en c1105`() {
        // c.1105: la candidata (d) pinzada en este archivo ya captura
        // (rama «del/de mi coche|carro|auto» del piso + plantilla).
        // Contrato pin girado de assertNull a captura TASK; la
        // cobertura completa vive en
        // ContextIntentEngineInflarRuedasCocheFloorTest.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas del coche mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Inflar las ruedas del coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Regresiones (pisos hermanos intactos) ----

    @Test
    fun `regresión poner ruedas temporada intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas de invierno en diciembre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno en diciembre", intent.title)
    }

    @Test
    fun `regresión poner ruedas a secas sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresión cargar coche intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el coche esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche", intent.title)
    }

    @Test
    fun `regresión llevar taller intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el coche al taller mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche al taller", intent.title)
    }
}

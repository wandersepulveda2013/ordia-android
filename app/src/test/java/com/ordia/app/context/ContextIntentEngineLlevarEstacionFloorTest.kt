package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1158: candidata (d) de la auditoría c.1137 (clase DECIMOSEXTA, viajes) —
 * HALLAZGO inesperado de la sonda persistida
 * `tools/probe/SixteenthClassTravelProbe.kt` (R8 «llevar a los niños al
 * aeropuerto mañana» NULL). «Llevar a <familia> al aeropuerto / a la
 * estación» es la diligencia familiar de acompañamiento al transporte,
 * hermana EXACTA del piso escolar c.773 y del médico c.776: el olvido
 * silencioso cuesta el vuelo/tren del familiar (P1). Causa raíz medida:
 * `ERRAND_SCHOOL_RUN_FLOOR` cierra objeto (`niñ[oa]s?` + acarreos escolares)
 * y destino (educativo/parque) con listas propias; aeropuerto/estación no
 * están en ninguna. Fix lockstep piso↔plantilla (lección c.616): piso NUEVO
 * acotado `ERRAND_STATION_RUN_FLOOR` (lista cerrada de parentesco + destino
 * `aeropuerto|estaci[oó]n`, guard `(?<!no )` heredado, CERO keywords nuevas —
 * gate c.751: el piso da MINIMUM_CONFIDENCE por sí solo vía
 * [hasStrongErrandImperative], hermano c.1128) + plantilla `matchStationRun`
 * en `extractTitle` (grafía preservada c.653). UNA forma por ciclo: nombres
 * propios («María», «Ana») quedan FUERA (bivalentes sin acotar). PRE medido
 * con sondas efímeras `/tmp/probe1157/LlevarAeropuertoPreProbe2.kt` y
 * `PreProbe3.kt` (motor real vía `tools/run_probe.sh`) sobre HEAD `b2c33f2`
 * (re-medido tras integrar c.1152 del hermano): 6/6 capturas NULL
 * (incl. la frontera «estación de esquí», consecuencia deliberada), 5/5
 * guards NULL, 4/4 pines HIT byte-idénticos.
 */
class ContextIntentEngineLlevarEstacionFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura ninos aeropuerto manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al aeropuerto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mis padres estacion viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mis padres a la estación el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mis padres a la estación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo abuelos aeropuerto hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a los abuelos al aeropuerto a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a los abuelos al aeropuerto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura acuse mama estacion esta tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a mamá a la estación esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mamá a la estación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura familia aeropuerto sin temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la familia al aeropuerto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la familia al aeropuerto", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura suegros estacion del ave lunes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los suegros a la estación del ave el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los suegros a la estación del ave", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura frontera estacion de esqui consecuencia deliberada`() {
        // Consecuencia medida y aceptada: «estación de esquí» no es la
        // estación de tren, pero el compromiso de desplazamiento familiar
        // es real (precedente consecuencia documentada c.1135 campamento).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la estación de esquí el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la estación de esquí", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (deben quedarse NULL) ----

    @Test
    fun `guard negacion no lleves`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no lleves a los niños al aeropuerto tan pronto", 1000)
        ))
    }

    @Test
    fun `guard preterito lleve`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ayer llevé a los niños al aeropuerto", 1000)
        ))
    }

    @Test
    fun `guard duda subjuntivo quiza lleve`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá lleve a los abuelos a la estación", 1000)
        ))
    }

    @Test
    fun `guard nombre propio maria fuera`() {
        // Frontera deliberada (UNA forma por ciclo): nombres propios sin
        // acotar son bivalentes — quedan FUERA como lateral abierta.
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María a la estación el viernes", 1000)
        ))
    }

    @Test
    fun `guard nombre propio ana presente fuera`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a Ana al aeropuerto mañana", 1000)
        ))
    }

    // ---- Pines / regresiones byte-idénticos ----

    @Test
    fun `pin escolar c773 byte identico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `pin medico c776 byte identico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `pin salir aeropuerto c1150 byte identico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "salir para el aeropuerto a las 5", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Salir para el aeropuerto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `pin envolvente recordame c613`() {
        // Envolvente «recuérdame…»: ya capturaba por el candado c.613
        // (TASK 0.54 medido PRE). Si el bono del piso la re-pinea, es un
        // re-pin legítimo documentado (precedente c.1035/c.1143).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a los niños al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertNotNull(intent!!.dueAt)
    }
}

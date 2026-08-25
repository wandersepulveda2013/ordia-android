package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1082: «poner las ruedas de invierno/verano» — candidata (a) de la
 * clase DUODÉCIMA (vida con vehículo), medida NULL por la sonda
 * persistida `tools/probe/TwelfthClassVehicleProbe.kt` (c.1079, C5
 * «poner las ruedas de invierno en diciembre») y re-medida PRE en este
 * ciclo con sonda efímera propia (5/5 capturas NULL, 6/6 guards NULL,
 * 4/4 regresiones HIT, HEAD `3faea01`): el cambio de ruedas de
 * temporada es EL mantenimiento estacional del coche (coste real de
 * olvido: circular con neumáticos inadecuados) y caía a NULL — «poner»
 * es bivalente (la lavadora/la mesa/el lavavajillas ya tienen piso
 * HOUSEHOLD propio c.729/c.736/c.738) y «ruedas» no era keyword
 * (gate c.751: sin ella la notificación sin palabra gatillo ni llega
 * al análisis en producción).
 * Fix en los 3 puntos lockstep (lección c.616/c.717/c.751): piso
 * anclado en [ContextIntentEngine.hasStrongTaskImperative] ACOTADO al
 * objeto `ruedas? de (invierno|verano)` — la temporada es lo que hace
 * inequívoco el mantenimiento estacional; «poner las ruedas» a secas
 * sigue FUERA (pin) — + keyword-OBJETO «ruedas» en
 * [ContextIntentKind.TASK] (0.12 sola queda bajo el umbral: «las
 * ruedas están gastadas» sigue descartado; «inflar las ruedas de la
 * bici» — candidata (c) — sigue FUERA: 0.12 + bono temporal 0.1 =
 * 0.22 < 0.45, pin) + plantilla de título (el match arranca en el
 * verbo y preserva las palabras del usuario).
 * Kind decidido: TASK, hermano de «cargar el coche» c.853 y
 * «cambiar el aceite» c.710 (deber de mantenimiento del vehículo;
 * deliberación contra ERRAND — no implica desplazamiento enunciado —
 * y contra HOUSEHOLD — no es quehacer del hogar).
 * Anti-overreach: `(?<!no )` bloquea la negada; el pasado «puse…»,
 * el suelto «poner» y el sintagma nominal «las ruedas de invierno»
 * no casan; la duda «quizá poner…» no casa el ancla y la keyword
 * sola queda bajo el umbral. Acotado deliberado (una forma por
 * ciclo): «neumáticos de invierno», la candidata (b) «cargar el
 * carro» y la (c) «inflar las ruedas de la bici» quedan como
 * candidatas propias.
 */
class ContextIntentEnginePonerRuedasTemporadaFloorTest {

    // ---- Capturas directas (piso, objeto «ruedas de invierno/verano») ----

    @Test
    fun `captura sonda en diciembre`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas de invierno en diciembre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno en diciembre", intent.title)
    }

    @Test
    fun `captura invierno sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas de invierno", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura verano manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas de verano mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de verano", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, poner las ruedas de invierno", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana poner las ruedas de verano", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de verano", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura esta noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas de invierno esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame poner las ruedas de invierno esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner las ruedas de invierno", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no poner las ruedas de invierno", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `no voy a poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a poner las ruedas de invierno", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá poner las ruedas de invierno", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado puse descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "puse las ruedas de invierno ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sintagma nominal descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las ruedas de invierno", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sin temporada descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las ruedas", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `keyword inerte estado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las ruedas están gastadas", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `candidata c inflar sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "inflar las ruedas de la bici hoy", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (pisos hermanos intactos) ----

    @Test
    fun `regresión lavadora intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la lavadora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `regresión mesa intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la mesa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
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
    fun `regresión cambiar aceite intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cambiar el aceite del coche mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar el aceite del coche", intent.title)
    }
}

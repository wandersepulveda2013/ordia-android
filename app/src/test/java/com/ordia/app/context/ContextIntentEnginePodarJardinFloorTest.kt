package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.748 (número provisional, confirmado al fetch pre-push final): forma
 * "podar el jardín el sábado" (hogar no cubierto, sonda paralela
 * `FourthClassVerbDiscoveryProbe.kt` c.740, PRE NULL — elegida por
 * dispersión anti-colisión: ni compra/colada anunciadas, ni "bañar al
 * perro" señalada c.740, ni "pintar la casa" primera-del-pool del
 * listado c.747) — piso HOUSEHOLD acotado al objeto `jardín/jardines`
 * sobre el verbo bivalente "podar" (las rosas/los setos/el árbol;
 * familia de pisos acotados de jardinería [HOUSEHOLD_LAWN_FLOOR] c.731).
 * La keyword "jardín" ya existía (HOUSEHOLD, desde la cobertura léxica
 * base); el lockstep añade el VERBO "podar" (precedente de verbos
 * keyword c.639/c.727/c.730).
 * Kind: HOUSEHOLD (jardinería = quehacer doméstico canónico, misma
 * deliberación que "cortar el césped" c.731; TASK solo en envolvente
 * c.613).
 */
class ContextIntentEnginePodarJardinFloorTest {

    @Test
    fun `captura podar el jardin plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar el jardín el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Podar el jardín", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, podar el jardín mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Podar el jardín", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana podar el jardín", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Podar el jardín", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar mi jardín hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Podar mi jardín", intent.title)
    }

    @Test
    fun `captura plural jardines`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar los jardines esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Podar los jardines", intent.title)
    }

    @Test
    fun `no podar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no podar el jardín mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quiza podar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá podar el jardín mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado pode descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podé el jardín ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `podar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no jardin descartado`() {
        // Objeto no acotado: "las rosas" no es la forma sondeada; el piso
        // se restringe a `jardín/jardines` (familia control kind-drift
        // c.728/c.731/c.744). "podar las rosas" sigue sin piso.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar las rosas mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `diminutivo jardincito descartado`() {
        // `\b` final tras `jardín/jardines`: el diminutivo no casa (misma
        // frontera que "perrito" c.740 / "gatito" c.744).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "podar el jardincito mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame podar el jardín": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el
        // piso TASK c.613 gobierna. PRE-verificado: ya capturaba como
        // TASK antes del piso (el envolvente basta).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame podar el jardín el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Podar el jardín", intent.title)
    }
}

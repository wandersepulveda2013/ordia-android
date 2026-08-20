package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.751 (renumerado de c.750: STALE_RUN ajena bc32516 reclamó c.750 para
 * "donar sangre" durante este ciclo; base reintegrada pre-push): forma
 * "cargar el celular hoy" (14/14 de la CUARTA clase de verbos cotidianos,
 * sonda `FourthClassVerbDiscoveryProbe.kt` c.740, PRE NULL — elegida por
 * dispersión anti-colisión: ni compra/colada anunciadas, ni "bañar al
 * perro" señalada c.740, ni "pintar la casa" primera-del-pool del listado
 * c.747; el heurístico de hermanos apunta a "votar el domingo", así se
 * toma la ÚLTIMA del pool restante para máxima dispersión) — piso TASK
 * anclado (^|ACUSE|TEMPORAL, familia c.691…c.726) ACOTADO al objeto
 * `celular/celulares` sobre el verbo bivalente "cargar" (el archivo/la
 * tarjeta/gasolina/al bebé; criterio de acotamiento al objeto de los
 * pisos kind-drift c.717/c.728/c.731/c.740/c.744/c.748, aquí primer piso
 * TASK acotado: los anteriores de la familia usaban `\s+\w` porque su
 * verbo era unívoco). Lockstep (lección c.713): la keyword-objeto
 * "celular" se añade a TASK — no el verbo "cargar", bivalente y subcadena
 * de "descargar" (c.725); la keyword alimenta además TRIGGER_WORDS, sin
 * la cual la notificación ni siquiera llegaría al análisis.
 * Kind decidido: TASK, en deliberación contra HOUSEHOLD — no es quehacer
 * físico del hogar (limpieza/orden: familia de HOUSEHOLD_VERBS), sino
 * deber de mantenimiento del móvil; los envolventes c.613 ya lo trataban
 * como TASK ("tengo que cargar el celular" PRE-verificado).
 */
class ContextIntentEngineCargarCelularFloorTest {

    @Test
    fun `captura cargar el celular plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el celular hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, cargar el celular", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana cargar el celular", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar mi celular esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar mi celular", intent.title)
    }

    @Test
    fun `captura desnuda al inicio`() {
        // Ancla `^` de la familia c.691…c.726: el imperativo al inicio sin
        // pista temporal también captura (el piso no exige fecha).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el celular", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `captura plural celulares`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar los celulares mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar los celulares", intent.title)
    }

    @Test
    fun `no cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no cargar el celular hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quiza cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá cargar el celular hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado cargue descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargué el celular anoche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `cargar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no celular descartado`() {
        // Objeto no acotado: "la tarjeta" no es la forma sondeada; el piso
        // se restringe a `celular/celulares` (familia control kind-drift
        // c.728/c.731/c.744). "cargar la tarjeta" sigue sin piso.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar la tarjeta mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo carga descartado`() {
        // "la carga del celular" es sustantivo, no imperativo: la keyword
        // "celular" sola (0.12) no alcanza el umbral.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la carga del celular fue rápida", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame cargar el celular": el piso TASK c.613 (envolvente)
        // gobierna; PRE-verificado: ya capturaba como TASK antes del piso
        // acotado (el envolvente basta) — test de no-regresión.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame cargar el celular mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }
}

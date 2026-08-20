package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.770: forma reflexiva "tomarme la medicina" (sonda
 * `tools/probe/FifthClassLifeProbe.kt`, QUINTA clase — salud/autocuidado;
 * pool OPEN tras c.765/c.766/c.768; dispersión epoch-day 20685 % 7 = 0).
 * NULL PRE verificado por sonda sobre HEAD b025444: la 1ª persona reflexiva
 * con enclítico (-me) es la forma MÁS cotidiana de autocuidado ("tengo que
 * tomarme la medicina", "tomarme la medicina esta noche") y quedaba fuera
 * del piso c.765 que solo reconoce el infinitivo desnudo "tomar".
 * Fix mínimo: extensión por ALTERNANCIA del piso existente c.765 —
 * verbo `tomar|tomarme`, objeto acotado `medicinas?|medicamentos?|
 * pastillas?` intacto (el objeto acota; "tomar" sigue bivalente: café/
 * autobús/vuelo/decisión FUERA). Keyword-OBJETO "medicina" ya existía
 * (c.765): lockstep coste-cero (precedente c.755). Plantilla ampliada al
 * enclítico: "Tomarme la medicina". Kind: TASK (hermano de c.765).
 */
class ContextIntentEngineTomarmeMedicinaFloorTest {

    @Test
    fun `captura base esta noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme la medicina esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme la medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura las pastillas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme las pastillas hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme las pastillas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura el medicamento con hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme el medicamento a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme el medicamento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura posesivo mi medicina`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme mi medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme mi medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura envolvente tengo que`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que tomarme la medicina esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion c765 infinitivo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `descarta negada`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no tomarme la medicina mañana", 1000)
        ))
    }

    @Test
    fun `descarta duda`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá tomarme la medicina mañana", 1000)
        ))
    }

    @Test
    fun `descarta pasado`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "me tomé la medicina ayer", 1000)
        ))
    }

    @Test
    fun `descarta suelto sin objeto`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme", 1000)
        ))
    }

    @Test
    fun `descarta sustantivo declarativo`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mi medicina está en la mesa", 1000)
        ))
    }
}

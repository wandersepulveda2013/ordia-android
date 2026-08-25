package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1029: forma «configurar <dispositivo>» («configurar el móvil nuevo por
 * la noche») — candidata (b) medida NULL por la sonda persistida
 * `tools/probe/EleventhClassDigitalProbe.kt` (c.1026, clase UNDÉCIMA: vida
 * digital cotidiana; C10 NULL sobre HEAD `8af00df1`, re-medido NULL en el
 * PRE de este ciclo). Olvido silencioso P1: configurar el dispositivo
 * nuevo (móvil/ordenador/router/impresora) es de los trámites digitales
 * más cotidianos y su coste de olvido es real (días con el dispositivo a
 * medias). El verbo «configurar» no tenía piso ni keyword (0.0 suelto;
 * «móvil» keyword c.851 solo suma 0.12 < umbral).
 *
 * Fix lockstep TRES puntos (lección c.616/c.751; hermano «reiniciar el
 * router» c.771 y «escanear el DNI» c.864): (1) piso TASK acotado al
 * objeto-dispositivo (móvil/celular/ordenador/computadora/portátil/
 * tablet/router/impresora/tele(visor)/wifi — el verbo es bivalente: la
 * cuenta/el perfil/la alarma quedan FUERA, una forma por ciclo); (2)
 * keyword-VERBO «configurar» en [ContextIntentKind.TASK] (monosemántico,
 * precedente c.752 «votar»/c.864 «escanear»; subcadenas inertes: el
 * sustantivo «configuración» y el pasado «configuré…» NO la contienen,
 * «reconfigurar» la contiene pero 0.12 sola < umbral y el piso anclado
 * la excluye); (3) plantilla de título «Configurar <dispositivo>…».
 * Negación sin cláusula dedicada: keyword 0.12 + keyword «móvil» 0.12 +
 * bono temporal 0.1 = 0.34 < umbral 0.45 (aritmética hermana c.859/
 * c.860/c.771) y el piso lleva `(?<!no )`. Kind TASK (hermano del
 * dispositivo «cargar el celular» c.751 y «reiniciar el router» c.771;
 * deliberación contra HOUSEHOLD: no es quehacer doméstico, es puesta en
 * marcha de dispositivo).
 */
class ContextIntentEngineConfigurarDispositivoFloorTest {

    @Test
    fun `captura base movil nuevo por la noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el móvil nuevo por la noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el móvil nuevo", intent.title)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el móvil mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el móvil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, configurar el router hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el router", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana configurar la impresora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar la impresora", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura ordenador con dia de semana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el ordenador el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el ordenador", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin tilde preserva grafia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el movil nuevo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el movil nuevo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura wifi`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el wifi esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Configurar el wifi", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame configurar el móvil nuevo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no configurar el móvil mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá configurar el móvil mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configuré el móvil ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la configuración del móvil", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente cuenta descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar la cuenta del banco mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion reiniciar el router intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el router mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion cargar el movil intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el móvil hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

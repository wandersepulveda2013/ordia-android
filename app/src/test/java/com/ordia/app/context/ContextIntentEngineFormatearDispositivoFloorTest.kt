package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1036: forma «formatear <dispositivo>» («formatear el ordenador el
 * sábado») — candidata (c) medida NULL por la sonda persistida
 * `tools/probe/EleventhClassDigitalProbe.kt` (c.1026, clase UNDÉCIMA: vida
 * digital cotidiana; C11 NULL sobre HEAD `8af00df1`, re-medido NULL en el
 * PRE de este ciclo con sonda efímera: 7/7 candidatas NULL, 7/7 guards
 * NULL correctos, envolvente ya TASK vía wrapper, 3/3 regresiones HIT).
 * Olvido silencioso P1: formatear el dispositivo (ordenador/portátil/
 * móvil) es un trámite digital cotidiano de coste real (requiere copia
 * previa y ventana de tiempo; olvidarlo deja el problema sin resolver).
 * El verbo «formatear» no tenía piso ni keyword (0.0 suelto). Cribada
 * contra `ContextPrivacyFilter` antes de planear (lección SU c.1029):
 * «formatear» NO está en `blockedContentPatterns`.
 *
 * Fix lockstep TRES puntos (lección c.616/c.751; hermano «configurar
 * <dispositivo>» c.1032): (1) piso TASK acotado al objeto-dispositivo
 * formateable (ordenador/computadora/portátil/móvil/celular/tablet — el
 * verbo es bivalente: el documento/el texto quedan FUERA, una forma por
 * ciclo; [óo]/[áa] admiten la grafía sin tilde, hermana «tensi[oó]n»
 * c.772); (2) keyword-VERBO «formatear» en [ContextIntentKind.TASK]
 * (monosemántico, precedente c.752 «votar»/c.864 «escanear»/c.1032
 * «configurar»; subcadenas inertes: el sustantivo «formateo» y el pasado
 * «formateé…» NO la contienen, «reformatear» la contiene pero 0.12 sola
 * < umbral y el piso anclado la excluye, precedente «reescanear» c.888);
 * (3) plantilla de título «Formatear <dispositivo>…». Negación sin
 * cláusula dedicada: keyword 0.12 + bono temporal 0.1 = 0.22 < umbral
 * 0.45 (con «móvil»/«celular» keyword c.851: 0.34 < 0.45, aritmética
 * hermana c.1032/c.771) y el piso lleva `(?<!no )`. Kind TASK (hermano
 * del dispositivo «configurar el móvil» c.1032 y «reiniciar el router»
 * c.771; deliberación contra HOUSEHOLD: no es quehacer doméstico, es
 * mantenimiento de dispositivo).
 */
class ContextIntentEngineFormatearDispositivoFloorTest {

    @Test
    fun `captura base ordenador el sabado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear el ordenador el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear el ordenador", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear el ordenador mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear el ordenador", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, formatear el portátil hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear el portátil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana formatear la tablet", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear la tablet", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura movil con parte del dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear el móvil esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear el móvil", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura computadora con dia de semana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear la computadora el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear la computadora", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura celular nuevo preserva resto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear el celular nuevo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Formatear el celular nuevo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame formatear el ordenador el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no formatear el ordenador mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá formatee el ordenador mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formateé el ordenador ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el formateo del ordenador", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente documento descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "formatear el documento mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `prefijo reformatear descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reformatear el ordenador mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion configurar el movil intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "configurar el móvil nuevo por la noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion reiniciar el router intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el router mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

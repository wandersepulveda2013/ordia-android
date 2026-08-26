package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1226: forma transitiva «cepillar al perro/gato» (lateral (a) ABIERTA
 * por la auditoría c.1225, clase VIGESIMONOVENA hogar+mascotas — sonda
 * persistida `tools/probe/CepillarMascotaProbe.kt`, PRE 5/5 NULL targets —
 * olvido silencioso). «Cepillar» es BIVALENTE (los dientes / reflexivo
 * «cepillarse»), así el piso se ACOTA al objeto mascota
 * `(?:perrit[oa]|perr[oa]|gatit[oa]|gat[oa])s?` y el destinatario humano
 * queda FUERA (anti-overreach, precedente estructural
 * [HOUSEHOLD_DEWORM_FLOOR] c.1017 / [HOUSEHOLD_NEUTER_FLOOR] c.1202).
 * Lockstep 2 puntos (lección c.616; hermano c.1017/c.1202 sin
 * keyword-verb; gate c.751 intacto — la keyword-mascota gato/gata
 * preexistente c.744 dispara el análisis). Determinista (regex), sin IA
 * fingida. Kind: HOUSEHOLD (higiene de la mascota = cuidado en el hogar,
 * hermana de «bañar» c.761; TASK solo en envolvente c.613).
 */
class ContextIntentEngineCepillarMascotaTest {

    @Test
    fun `captura cepillar al gato plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar al gato mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cepillar al gato", intent.title)
    }

    @Test
    fun `captura cepillar al perro`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar al perro esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cepillar al perro", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar a mi gata el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cepillar a mi gata", intent.title)
    }

    @Test
    fun `captura plural perros`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar a los perros hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cepillar a los perros", intent.title)
    }

    @Test
    fun `regresion banar mascota intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "bañar al gato mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Bañar al gato", intent.title)
    }

    @Test
    fun `control negada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no cepillar al perro este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control negada con envolvente de plan`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a cepillar al gato", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control pasado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillé al gato ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control bivalente dientes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar los dientes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control bivalente reflexivo dientes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillarse los dientes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `control objeto no mascota`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cepillar a los niños este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame cepillar al gato el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

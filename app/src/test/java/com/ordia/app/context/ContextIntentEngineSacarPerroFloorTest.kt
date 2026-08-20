package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.736: forma "sacar al perro" (1/12 CUARTA clase, mascotas — sonda
 * `FourthClassVerbDiscoveryProbe.kt` c.736, PRE NULL) — piso HOUSEHOLD
 * "sacar a(l) perr[oa](s)" ACOTADO al objeto (precedente basura c.717,
 * cama c.728): "sacar" sin objeto mascota es ambiguo (sacar la basura ya tiene
 * piso propio; "sacar la cuenta"/"sacar provecho" no son quehaceres), así la
 * posición libre se reserva al objeto mascota (`perro|perra`, singular/
 * plural, con posesivo "a mi perro" como en `ERRAND_CARRY_FLOOR` c.684) — keyword
 * `perro` (lockstep c.717) + plantilla "(sacar) (al) perr…"→"Sacar al
 * perro …".
 * Kind: HOUSEHOLD, en deliberación contra ERRAND y TASK — sacar al perro es
 * el cuidado doméstico diario canónico (misma familia que "sacar la
 * basura" c.717); no hay destino de trámite (ERRAND) ni verbo gestión de
 * tarea (TASK). Determinista (regex), sin IA fingida, anti-overreach
 * (negada/duda/sustantivo/pasado/verbo suelto/objeto ajeno descartados).
 */
class ContextIntentEngineSacarPerroFloorTest {

    @Test
    fun `captura sacar perro plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, sacar al perro hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche sacar al perro", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }

    @Test
    fun `captura perros plural y perra`() {
        val intentPerros = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar a los perros mañana", 1000)
        )
        assertNotNull(intentPerros)
        assertEquals(ContextIntentKind.HOUSEHOLD, intentPerros!!.kind)
        assertEquals("Sacar a los perros", intentPerros.title)
        val intentPerra = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar a la perra mañana", 1000)
        )
        assertNotNull(intentPerra)
        assertEquals(ContextIntentKind.HOUSEHOLD, intentPerra!!.kind)
        assertEquals("Sacar a la perra", intentPerra.title)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar a mi perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar a mi perro", intent.title)
    }

    @Test
    fun `no sacar perro descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no sacar al perro mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá sacar perro descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá sacar al perro mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado saqué perro descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "saqué al perro ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sacar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sacar otro objeto no roba HOUSEHOLD`() {
        // Objeto no mascota ("sacar la basura") sigue gobernado por su piso
        // propio (c.717) — el piso acotado a mascota NO debe capturarlo con
        // título de perro (familia control kind-drift c.728/c.731).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar la basura mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame sacar al perro": piso HOUSEHOLD descartado vía
        // imperativeIsWrapped; piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame sacar al perro", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }
}

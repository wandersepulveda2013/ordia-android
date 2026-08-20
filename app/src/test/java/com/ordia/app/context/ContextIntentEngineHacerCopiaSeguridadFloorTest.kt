package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.774: forma "hacer copia de seguridad" (sonda `tools/probe/FifthClassLifeProbe.kt`,
 * QUINTA clase — hogar/tecnología; elegida por dispersión determinista
 * epoch-day 20686 % 3 = 1 sobre el pool OPEN residual de 3 ítems). NULL PRE
 * verificado por la sonda sobre HEAD 027826b. La copia de seguridad periódica
 * es el acto de protección de datos más cotidiano (DATOS SAGRADOS): capturarla
 * evita el olvido silencioso (hermana de "reiniciar el router" c.771). Piso
 * TASK acotado al objeto `copias? de seguridad` con ALTERNANCIA `backups?`
 * (el verbo "hacer" es muy bivalente: la compra —SHOPPING c.758—, la cama
 * —HOUSEHOLD c.728—, ejercicio… quedan FUERA; "hacer la copia de la llave"
 * tampoco casa — no es respaldo de datos). Lockstep keyword-OBJETO "backup"
 * (lección c.713/c.751/c.765; NO el verbo "hacer"; "copia" no es keyword
 * segura: subcadena de "fotocopia"/"copiar") + plantilla "(hacer) copia de
 * seguridad/backup". Kind: TASK (deliberación contra HOUSEHOLD: no es
 * quehacer doméstico, es mantenimiento de datos, igual que c.771).
 * Negación sin cláusula dedicada: keyword 0.12 + bono temporal 0.1 = 0.22
 * < umbral (hermana c.765/c.766/c.768/c.771/c.772).
 */
class ContextIntentEngineHacerCopiaSeguridadFloorTest {

    @Test
    fun `captura base hoy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer copia de seguridad hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copia de seguridad", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con articulo manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la copia de seguridad mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la copia de seguridad", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, hacer copia de seguridad hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copia de seguridad", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana hacer copia de seguridad", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copia de seguridad", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura alternancia backup`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer backup hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        // El título preserva la grafía del usuario (doctrina c.653).
        assertEquals("Hacer backup", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame hacer copia de seguridad esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no hacer copia de seguridad hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá hacer copia de seguridad hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hice la copia de seguridad ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente llave descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la copia de la llave hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `truncado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer copia", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la copia de seguridad falló", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion medir la tension intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir la tensión hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion llevar a los ninos al colegio intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1157: candidata (e) de la auditoría c.1147 (clase DECIMOSÉPTIMA, vida
 * laboral — sonda persistida `tools/probe/SeventeenthClassWorkProbe.kt` C20,
 * medida 1/1 NULL; re-medida PRE en este ciclo con sonda efímera: 6/6
 * capturas NULL, 9/9 guards NULL, pines HIT). «llevar el portátil al trabajo
 * mañana» se DESCARTABA en silencio: los pisos «llevar» existentes tienen
 * listas de objetos CERRADAS sin «portátil/ordenador» (niños/cole c.773,
 * merienda c.1128, almuerzo c.1129, dinero c.1133, ropa c.1141, proyecto
 * c.1144, coche/taller c.684, niña/médico c.776, dativo c.854). Olvido
 * silencioso P1: olvidar el portátil en casa cuesta el día de trabajo.
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — «llevar» ya
 * es keyword TASK histórica, gate c.751 satisfecho): piso acotado
 * objeto-dispositivo `ERRAND_WORK_DEVICE_FLOOR` + plantilla
 * `matchWorkDeviceRun` en [ContextIntentEngine.extractTitle] (grafía del
 * usuario preservada, doctrina c.653). Kind ERRAND (acarreo físico,
 * doctrina de la familia c.1144). UNA forma por ciclo: destino «al trabajo»
 * solamente; «a la oficina»/«al curro» y plural «los portátiles» quedan
 * laterales ABIERTAS.
 */
class ContextIntentEngineLlevarPortatilTrabajoFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura portatil trabajo manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura ordenador trabajo el lunes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el ordenador al trabajo el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el ordenador al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar el portátil al trabajo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar el portátil al trabajo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNull(intent!!.dueAt)
    }

    @Test
    fun `captura presente llevo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura grafia sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portatil al trabajo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portatil al trabajo", intent.title)
        assertNull(intent!!.dueAt)
    }

    @Test
    fun `captura posesivo mi portatil`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar mi portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar mi portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura grafia latam la portatil`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (pin byte-idéntico PRE) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (deben seguir NULL) ----

    @Test
    fun `negacion imperativa descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no lleves el portátil al trabajo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `negacion infinitivo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar el portátil al trabajo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda subjuntivo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá lleve el portátil al trabajo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé el portátil al trabajo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `tercera persona subjuntivo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "que lleve el portátil al trabajo marta", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `destino no laboral playa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil a la playa", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `destino no laboral salon descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al salón", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `plural portatiles sigue fuera`() {
        // Lateral ABIERTA (UNA forma por ciclo): el plural queda fuera.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar los portátiles al trabajo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `declarativo sin compromiso descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el portátil del trabajo está roto", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sin destino sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape con los pisos «llevar» existentes) ----

    @Test
    fun `hermana proyecto ciencias c1144 intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el proyecto de ciencias al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `hermano coche taller c684 intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el coche al taller mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche al taller", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `hermano dativo c854 intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle el informe al jefe mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarle el informe al jefe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `creo que hay que sigue TASK`() {
        // Pin byte-idéntico PRE (sonda efímera): «hay que» gobierna TASK,
        // igual que en la hermana c.1144 (proyecto de ciencias).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "creo que hay que llevar el portátil al trabajo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNull(intent!!.dueAt)
    }
}

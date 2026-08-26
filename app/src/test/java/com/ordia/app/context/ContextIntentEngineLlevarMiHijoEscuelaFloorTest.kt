package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1172: lateral P2 del piso escolar c.1170/c.773 — objeto posesivo
 * singular «a mi/tu/su hija/o». El alternador del piso escolar cubría
 * `los|las|mis|tus|sus` pero NO el singular `mi|tu|su` (la plantilla
 * médica matchMedicalRun SÍ lo admite desde c.776 — asimetría hermana),
 * y el objeto era solo `niñ[oa]s?`, no `hij[oa]s?`. El hermano pinó P2
 * «llevar a mi hija a la fiesta del cole el viernes» como NULL deliberado
 * anti-overreach al cerrar c.1170 (`d00b0acb`) — lateral genuina: un
 * padre escribe «a mi hija» con la misma intención que «a los niños».
 * PRE medido (sonda persistida `tools/probe/SchoolRunMiHijoProbe.kt`
 * sobre el motor real): 5/5 capturas NULL (C1-C5), guards 2/2 NULL,
 * regresiones 6/6 HIT (incluida R6 aeropuerto c.1158, que YA admite el
 * objeto de parentesco completo mi/tu/su + hij[oa]s? — el ecosistema ya
 * unificó ese sub-patrón en el piso más reciente), pines NULL,
 * envolvente «tengo que…» TASK 0.49.
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — «llevar»
 * ya es keyword TASK histórica, gate c.751 satisfecho): objeto del piso
 * escolar `ERRAND_SCHOOL_RUN_FLOOR` extendido al sub-patrón c.1158
 * ACOTADO a parentesco nuclear menor (`a(?:l| la| los| las| mis| tus|
 * sus| mi| tu| su)?(?:niñ[oa]s?|hij[oa]s?)` — SIN padres/abuelos/mujer/
 * marido, que quedan FUERA pineados NULL: «llevar a mi mujer al colegio»
 * no es diligencia escolar) + MISMO objeto en la plantilla
 * `matchSchoolRun` de [ContextIntentEngine.extractTitle]. «a la hija»
 * sin posesivo casa vía ` la\s+` + hij[oa]s? — DELIBERADO, coherente con
 * c.1158. Laterales hermanas ABIERTAS: piso médico c.776 con
 * hij[oa]s? («llevar a mi hijo al médico», pin P3 NULL) y destino
 * «fiesta de cumpleaños» (pin P2 NULL, P1 del hermano).
 */
class ContextIntentEngineLlevarMiHijoEscuelaFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura mi hija fiesta cole`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hija a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hija a la fiesta del cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mi hijo colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hijo al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente primera persona guarderia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi hija a la guardería esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi hija a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mi hijo fiesta colegio sabado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo a la fiesta del colegio el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hijo a la fiesta del colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura la hija sin posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la hija al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la hija al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura tu hijo escuela`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a tu hijo a la escuela el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a tu hijo a la escuela", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards de negación ----

    @Test
    fun `negacion no llevar mi hija descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a mi hija a la fiesta del cole", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `negacion no llevo mi hijo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevo a mi hijo al colegio", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines anti-overreach (NULL deliberado) ----

    @Test
    fun `pin fiesta de cumpleanos fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hija a la fiesta de cumpleaños el sábado", 1000)
        )
        assertNull(intent)
    }

    // RE-PIN legítimo c.1176 (doctrina c.1133/c.1141/c.1144/c.1172): el
    // pin NULL original se pineó cuando el objeto hij[oa]s? del piso
    // médico aún no estaba cerrado; c.1176 cerró exactamente esa forma.
    @Test
    fun `repin mi hijo al medico captura c1176`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hijo al médico", intent.title)
    }

    @Test
    fun `pin parentesco adulto fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi mujer al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (misma región de regex) ----

    @Test
    fun `regresion los ninos colegio`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
    }

    @Test
    fun `regresion fiesta cole c1170`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños a la fiesta del cole", intent.title)
    }

    @Test
    fun `regresion piso medico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion portatil trabajo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
    }

    @Test
    fun `regresion piso aeropuerto parentesco completo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // ---- Envolvente ----

    @Test
    fun `envolvente tengo que captura`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que llevar a mi hija a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
    }
}

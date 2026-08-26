package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1216 (lateral ABIERTA de la auditoría clase VIGESIMOSÉPTIMA jardinería/plantas c.1211):
 * «trasplantar (el|la|los|las|mi|tu|su)? + planta» — verbo monovalente «trasplantar» sin piso
 * ni keyword; pellizco de la familia «podar» c.748 / «quitar…hierbas» c.1214.
 * Lockstep DOS puntos (lección c.616, gate c.751 sin keyword-OBJETO si el piso basta):
 * (1) piso `HOUSEHOLD_TRANSPLANT_FLOOR` añadido a `HOUSEHOLD_FLOORS`;
 * (2) plantilla canónica `matchTrasplantarPlanta` (titulación). Grafías preservadas c.653.
 */
class ContextIntentEngineTrasplantarFloorTest {
    private fun event(t: String) = ContextEvent(
        source = ContextCaptureSource.SHARED_TEXT,
        rawText = t,
        timestampMs = 0L
    )
    private fun analyze(t: String) = ContextIntentEngine.analyze(event(t))

    @Test
    fun `trasplantar el bonsai captura HOUSEHOLD`() {
        val intent = analyze("trasplantar el bonsái")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar el bonsái", intent?.title)
    }

    @Test
    fun `trasplantar la orquidea captura HOUSEHOLD`() {
        val intent = analyze("trasplantar la orquídea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar la orquídea", intent?.title)
    }

    @Test
    fun `trasplantar las suculentas captura HOUSEHOLD`() {
        val intent = analyze("trasplantar las suculentas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar las suculentas", intent?.title)
    }

    @Test
    fun `trasplantar con temporal captura y titula limpio`() {
        val intent = analyze("mañana trasplantar el bonsái al balcón")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar el bonsái al balcón", intent?.title)
    }

    @Test
    fun `por favor trasplantar la planta captura HOUSEHOLD`() {
        val intent = analyze("por favor trasplantar la planta del salón")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar la planta del salón", intent?.title)
    }

    @Test
    fun `trasplantar el rosal del fondo captura HOUSEHOLD`() {
        val intent = analyze("trasplantar el rosal del fondo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Trasplantar el rosal del fondo", intent?.title)
    }

    @Test
    fun `guard pasado null`() {
        assertNull(analyze("trasplanté el bonsái"))
    }

    @Test
    fun `guard negada null`() {
        assertNull(analyze("no trasplantar el bonsái"))
    }

    @Test
    fun `guard sustantivo null`() {
        assertNull(analyze("el trasplante fue ayer"))
    }

    @Test
    fun `guard objeto fuera de familia null`() {
        assertNull(analyze("trasplantar los archivos al nuevo servidor"))
    }

    @Test
    fun `guard futuro sparse null`() {
        assertNull(analyze("vale, trasplantaré el bonsái mañana"))
    }

    @Test
    fun `regresiones jardineria byte-identicas`() {
        assertEquals("Podar los setos", analyze("podar los setos")?.title)
        assertEquals("Quitar las malas hierbas", analyze("quitar las malas hierbas")?.title)
        assertEquals("Podar el árbol", analyze("podar el árbol")?.title)
    }
}

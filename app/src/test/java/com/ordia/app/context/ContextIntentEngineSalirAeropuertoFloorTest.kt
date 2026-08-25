package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.1150 — «salir para el aeropuerto / la estación»: candidata (b)
 * de la clase DECIMOSEXTA (viajes/reservas/ocio), medida NULL 1/1 en
 * `tools/probe/SixteenthClassTravelProbe.kt` C9 (c.1137) y re-medida PRE
 * sobre HEAD ce87703 con sonda efímera `/tmp/SalirAeropuertoPreProbe.kt`
 * (5/5 candidatas NULL, 11/11 guards NULL, 7/8 regresiones HIT intactas
 * — R7 «llevar las maletas al coche» NULL status quo, pineado). Sin
 * piso, «salir para el aeropuerto a las 5 del lunes» se DESCARTABA
 * silenciosamente: NI «salir» NI «aeropuerto»/«estación» son keywords
 * (gate c.751: la frase ni llega al análisis). Consecuencia real: la
 * logística previa al viaje — si no sales a tiempo, lo pierdes todo
 * (vuelo/tren perdido, el olvido con mayor coste de la clase).
 * Hermano EXACTO de c.1143 «sellar el paro»: verbo polivalente acotado
 * por objeto-ancla, lockstep en TRES puntos (lección c.616):
 *
 *  1. Keywords-frase «salir para el aeropuerto» + «salir para la
 *     estación» en ContextIntent.kt TASK (monosemánticas: partida al
 *     transporte; «salir» solo NO se añade — extremadamente
 *     polivalente: salir de fiesta/con alguien/del trabajo).
 *  2. Piso NUEVO acotado en [ContextIntentEngine.hasStrongTaskImperative]:
 *     ancla de inicio/acuse/prefijo temporal, guard anti-negación
 *     `(?<!no )` y objeto EXIGIDO «el aeropuerto» | «la estación»
 *     (anti-overreach: «salir para el trabajo/la oficina» FUERA).
 *  3. Plantilla hermana matchSalirAeropuerto en la rama TASK de
 *     extractTitle (el residuo temporal de cola lo depura
 *     [sanitizeTitle]).
 *
 * Kind decidido: TASK — partida con hora crítica, hermana de «preparar
 * la maleta» c.715, «facturar el vuelo» c.1140 y «sellar el paro»
 * c.1143 (todas TASK); la envolvente «recuérdame salir para el
 * aeropuerto a las 5» ya rutea TASK 0.5 vía candado c.613 (medido PRE)
 * → convergencia de kind. TRAVEL no tiene piso y «aeropuerto»/
 * «estación» ni siquiera son keywords: no compite.
 *
 * Cobertura: 5 capturas (aeropuerto+hora/día, estación+fecha, acuse
 * «vale,», prefijo temporal «el lunes», «estación de tren» con objeto
 * extendido) + 11 guards (negación, negación tras temporal, duda
 * «no sé si», pasado «salí», presente declarativo «salgo», bivalentes
 * «trabajo»/«oficina», sustantivo «la salida», lateral contracción
 * «salir al aeropuerto», declarativo «el aeropuerto cierra», polivalente
 * «salir de fiesta») + 8 regresiones (envolvente c.613: kind/título/
 * dueAt byte-idénticos y confianza 0.5→0.62 medida y aceptada — el
 * bono del piso ahora suma dentro de la envolvente, como en toda
 * keyword-frase envuelta; c.1140 facturar-vuelo, c.1143 sellar-paro,
 * reservar mesa, quedar MEETING, comprar billetes SHOPPING, «llevar
 * las maletas» NULL status quo pineado, preparar maleta).
 */
class ContextIntentEngineSalirAeropuertoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `salir para el aeropuerto con hora y dia captura TASK con titulo limpio`() {
        val r = analyze("salir para el aeropuerto a las 5 del lunes")
        assertNotNull("«salir para el aeropuerto a las 5 del lunes» debe capturar (era NULL en c.1137)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para el aeropuerto", r.title)
        assertTrue("debe resolver «a las 5 del lunes»", r.dueAt != null)
    }

    @Test
    fun `salir para la estacion manana captura TASK con titulo limpio`() {
        val r = analyze("salir para la estación mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para la estación", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `acuse vale no ensucia el titulo`() {
        val r = analyze("vale, salir para el aeropuerto a las 6")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para el aeropuerto", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `prefijo temporal el lunes no ensucia el titulo`() {
        val r = analyze("el lunes salir para el aeropuerto a las 5")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para el aeropuerto", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `estacion de tren conserva el objeto extendido`() {
        val r = analyze("salir para la estación de tren el viernes a las 7")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para la estación de tren", r.title)
        assertTrue(r.dueAt != null)
    }

    // ---------- Guards (NULL deliberado, byte-idénticos PRE/POST) ----------

    @Test
    fun `negacion no salir queda NULL`() {
        assertNull(analyze("no salir para el aeropuerto a las 5"))
    }

    @Test
    fun `negacion tras prefijo temporal queda NULL`() {
        assertNull(analyze("el lunes no salir para el aeropuerto"))
    }

    @Test
    fun `duda no se si salir queda NULL`() {
        assertNull(analyze("no sé si salir para el aeropuerto a las 5"))
    }

    @Test
    fun `pasado sali queda NULL`() {
        assertNull(analyze("salí para el aeropuerto a las 5"))
    }

    @Test
    fun `presente declarativo salgo queda NULL`() {
        assertNull(analyze("salgo para el aeropuerto a las 5"))
    }

    @Test
    fun `bivalente salir para el trabajo queda NULL`() {
        assertNull(analyze("salir para el trabajo a las 8"))
    }

    @Test
    fun `bivalente salir para la oficina queda NULL`() {
        assertNull(analyze("salir para la oficina a las 8"))
    }

    @Test
    fun `sustantivo la salida queda NULL`() {
        assertNull(analyze("la salida para el aeropuerto es a las 5"))
    }

    @Test
    fun `lateral contraccion salir al aeropuerto queda NULL`() {
        assertNull(analyze("salir al aeropuerto a las 5"))
    }

    @Test
    fun `declarativo el aeropuerto cierra queda NULL`() {
        assertNull(analyze("el aeropuerto cierra a las 10 de la noche"))
    }

    @Test
    fun `polivalente salir de fiesta queda NULL`() {
        assertNull(analyze("salir de fiesta mañana por la noche"))
    }

    // ---------- Regresiones (byte-idénticas PRE/POST) ----------

    @Test
    fun `envolvente recuerdame sigue TASK via candado c613`() {
        // PRE (sin piso): TASK 0.5 vía candado c.613. POST: 0.62 medido
        // y aceptado — el bono del piso suma dentro de la envolvente
        // (mismo comportamiento que cualquier keyword-frase envuelta);
        // kind, título y dueAt son byte-idénticos PRE/POST.
        val r = analyze("recuérdame salir para el aeropuerto a las 5")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(0.62f, r.confidence)
        assertEquals("Salir para el aeropuerto", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin c1140 facturar el vuelo intacto`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Facturar el vuelo", r.title)
    }

    @Test
    fun `pin c1143 sellar el paro intacto`() {
        val r = analyze("sellar el paro el día 3")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Sellar el paro el día 3", r.title)
    }

    @Test
    fun `reservar mesa sigue TASK`() {
        val r = analyze("reservar mesa para el sábado")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Reservar mesa", r.title)
    }

    @Test
    fun `quedar con ana sigue MEETING`() {
        val r = analyze("quedar con ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.MEETING, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Quedar con ana", r.title)
    }

    @Test
    fun `comprar billetes sigue SHOPPING`() {
        val r = analyze("comprar los billetes del concierto")
        assertNotNull(r)
        assertEquals(ContextIntentKind.SHOPPING, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Comprar los billetes del concierto", r.title)
    }

    @Test
    fun `llevar las maletas al coche NULL status quo pineado`() {
        assertNull(analyze("llevar las maletas al coche"))
    }

    @Test
    fun `preparar la maleta sigue TASK`() {
        val r = analyze("preparar la maleta esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Preparar la maleta", r.title)
    }

    // Cobertura adicional del hermano (este lado, c.1150): convergencia
    // funcional detectada en integración (ambos lados implementaron la
    // candidata (b) con piso/plantilla/keywords idénticos; primer-push-gana,
    // lección c.1077). Se cede el canónico y se aportan los 2 guards
    // únicos del lado perdedor, medidos POST sobre la implementación
    // canónica (`saldre` NULL, `ir al aeropuerto` NULL).

    @Test
    fun `futuro saldre para la estacion queda NULL`() {
        assertNull(analyze("saldré para la estación a las 7"))
    }

    @Test
    fun `ir al aeropuerto NULL status quo pineado`() {
        assertNull(analyze("ir al aeropuerto"))
    }
}

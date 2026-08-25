package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.1151 — «sacar el visado»: candidata (c) de la clase
 * DECIMOSEXTA (viajes/reservas/ocio), medida NULL 1/1 en
 * `tools/probe/SixteenthClassTravelProbe.kt` C11 (c.1137) y re-medida
 * PRE sobre HEAD b614c5b con sonda efímera `/tmp/probe1151/
 * SacarVisadoPreProbe.kt` (4/4 candidatas desnudas/acuse/temporal
 * NULL; las envolventes «tengo que…» y «recuérdame…» ya capturaban
 * TASK 0.45 vía candado c.613 — pineadas como posibles re-pins de
 * confianza/título, medidas POST). Sin piso, «sacar el visado antes
 * del viaje» se DESCARTABA silenciosamente: «sacar» está acotado a
 * basura (c.717), mascota (c.740), dinero (c.893) y cita/turno/hora
 * (c.1117), y «visado» NO es keyword (gate c.751: la forma desnuda ni
 * llega al análisis). Consecuencia real: sin visado no hay viaje —
 * el olvido de mayor coste de la clase junto a salir-aeropuerto.
 * Hermano EXACTO de c.1150 «salir para el aeropuerto»: verbo
 * polivalente acotado por objeto-ancla, lockstep en TRES puntos
 * (lección c.616):
 *
 *  1. Keyword-frase «sacar el visado» en ContextIntent.kt TASK
 *     (monosemántica: trámite de viaje; «sacar» solo NO se toca —
 *     bivalente consolidado en 4 pisos; «visado» solo tampoco —
 *     sustantivo declarativo: «el visado cuesta 80 euros» NULL).
 *  2. Piso NUEVO acotado en [ContextIntentEngine.hasStrongTaskImperative]:
 *     ancla de inicio/acuse/prefijo temporal, guard anti-negación
 *     `(?<!no )` y objeto EXIGIDO «el visado» (anti-overreach:
 *     «sacar el pasaporte» queda lateral — UNA forma por ciclo).
 *  3. Plantilla hermana matchSacarVisado en la rama TASK de
 *     extractTitle: captura SOLO «el visado» (+ calificador opcional
 *     «de turista»); la cola «antes del viaje» no es temporal
 *     parseable y se descarta del título (familia documentada c.1137).
 *
 * Kind decidido: TASK — trámite previo al viaje, hermano de «facturar
 * el vuelo» c.1140 y convergente con las envolventes PRE («tengo que
 * sacar el visado» ya era TASK 0.45 vía c.613, medido).
 *
 * Cobertura: 4 capturas (desnuda, cola «antes del viaje», cola «esta
 * semana», prefijo temporal «mañana» con calificador «de turista») +
 * 6 guards (negación, pasado «saqué», duda «no sé si», nominal «el
 * visado de turista», bivalente «sacar el pasaporte», declarativo
 * «el visado cuesta») + 7 pines (2 envolventes c.613 — posibles
 * re-pins medidos POST; c.1117 sacar-cita, c.1150 salir-aeropuerto,
 * c.1140 facturar-vuelo, c.740 sacar-perro HOUSEHOLD, c.893
 * sacar-euros ERRAND — kinds disjuntos intactos).
 */
class ContextIntentEngineSacarVisadoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `sacar el visado antes del viaje captura TASK con titulo limpio`() {
        val r = analyze("sacar el visado antes del viaje")
        assertNotNull("«sacar el visado antes del viaje» debe capturar (era NULL en c.1137 C11)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el visado", r.title)
    }

    @Test
    fun `sacar el visado esta semana captura TASK`() {
        val r = analyze("sacar el visado esta semana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el visado", r.title)
    }

    @Test
    fun `sacar el visado desnuda captura TASK`() {
        val r = analyze("sacar el visado")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el visado", r.title)
    }

    @Test
    fun `prefijo temporal manana con calificador de turista captura TASK con dueAt`() {
        val r = analyze("mañana sacar el visado de turista")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el visado de turista", r.title)
        assertTrue("debe resolver «mañana»", r.dueAt != null)
    }

    // ---------- Guards (NULL deliberado, byte-idénticos PRE/POST) ----------

    @Test
    fun `negacion no sacar queda NULL`() {
        assertNull(analyze("no sacar el visado todavia"))
    }

    @Test
    fun `pasado saque queda NULL`() {
        assertNull(analyze("ya saque el visado el mes pasado"))
    }

    @Test
    fun `duda no se si sacar queda NULL`() {
        assertNull(analyze("no se si sacar el visado este año"))
    }

    @Test
    fun `nominal el visado de turista queda NULL`() {
        assertNull(analyze("el visado de turista"))
    }

    @Test
    fun `bivalente sacar el pasaporte queda NULL lateral`() {
        assertNull(analyze("sacar el pasaporte mañana"))
    }

    @Test
    fun `declarativo el visado cuesta queda NULL`() {
        assertNull(analyze("el visado cuesta 80 euros"))
    }

    // ---------- Pines (PRE medidos; re-pins legítimos documentados si cambian) ----------

    @Test
    fun `pin envolvente tengo que sacar el visado sigue TASK`() {
        val r = analyze("tengo que sacar el visado antes del dia 20")
        assertNotNull("la envolvente c.613 ya capturaba PRE (medido 0.45)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin envolvente recuerdame sacar el visado sigue TASK`() {
        val r = analyze("recuerdame sacar el visado antes del viaje")
        assertNotNull("la envolvente c.613 ya capturaba PRE (medido 0.45)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `pin sacar cita c1117 intacto`() {
        val r = analyze("sacar una cita para el medico mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar una cita para el medico", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin salir aeropuerto c1150 intacto`() {
        val r = analyze("salir para el aeropuerto a las 5 del lunes")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Salir para el aeropuerto", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin facturar vuelo c1140 intacto`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar el vuelo", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin sacar al perro c740 kind HOUSEHOLD intacto`() {
        val r = analyze("sacar al perro a las 8")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals("Sacar al perro", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin sacar euros c893 kind ERRAND intacto`() {
        val r = analyze("sacar 50 euros mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        assertEquals("Sacar 50 euros", r.title)
        assertTrue(r.dueAt != null)
    }
}

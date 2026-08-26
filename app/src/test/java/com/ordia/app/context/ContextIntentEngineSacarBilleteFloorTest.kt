package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.1219 — «sacar (el|los) billete(s)»: candidata (a) de la
 * clase VIGESIMOQUINTA (viajes), medida NULL exacta en c.1200 (7/10
 * HIT auditoría) y re-medida PRE/POST con sonda persistida
 * `tools/probe/SacarBilleteProbe.kt` (PRE 5/5 candidatas NULL —
 * desnuda/temporal sujeta/temporal prefija/acuse/plural; las
 * envolventes «tengo que…»/«recuérdame…» ya capturaban TASK 0.45
 * vía candado c.613 — re-pines legítimos medidos POST 0.45→0.49
 * y 0.45→0.54 por las keywords-frase nuevas, precedente
 * c.1035/c.1139). Sin piso, «sacar el billete de tren mañana» se
 * DESCARTABA silenciosamente: «sacar» estaba acotado a basura
 * (c.717), mascota (c.740), dinero (c.893), cita/turno/hora
 * (c.1117) y visado (c.1151), y «billete» NO era keyword (gate
 * c.751: la forma desnuda ni llegaba al análisis). Olvido P1:
 * sin billete no hay viaje — reserva/compra del desplazamiento,
 * hermana de salir-aeropuerto c.1150 y facturar-vuelo c.1140.
 * Hermano EXACTO de c.1151 «sacar el visado»: verbo polivalente
 * («sacar» es el verbo con más pisos acotados del motor) acotado
 * por objeto-ancla, lockstep en TRES puntos (lección c.616):
 *
 *  1. Keywords-frase «sacar el billete»/«sacar los billetes» en
 *     ContextIntent.kt TASK (monosemánticas: reserva del viaje;
 *     «sacar» solo NO se toca — bivalente consolidado en 5 pisos;
 *     «billete» solo tampoco — sustantivo declarativo: «el billete
 *     de tren cuesta 50 euros» sigue NULL).
 *  2. Piso NUEVO acotado en [ContextIntentEngine.hasStrongTaskImperative]:
 *     ancla de inicio/acuse/prefijo temporal, guard anti-negación
 *     `(?<!no )` (sin cláusula dedicada — keyword 0.12 + bono 0.1
 *     = 0.22 < umbral, mismo argumento c.895b/c.895c/c.1140) y
 *     objeto EXIGIDO «(el|los) billete(s)» (anti-overreach: el
 *     bivalente «sacar el pasaporte» queda lateral NULL deliberado).
 *  3. Plantilla hermana matchSacarBillete en la rama TASK de
 *     extractTitle: captura objeto + calificador opcional
 *     «de|del <producto>» («de tren», «del bus»); la cola temporal
 *     va a dueAt, no al título (familia documentada c.1137).
 *
 * Kind decidido: TASK — reserva previa al viaje (la doctrina ERRAND
 * c.842/c.862 gobierna solo el desplazamiento físico; aquí el acto
 * es de reserva/gestión, convergente con las envolventes PRE
 * «tengo que sacar el billete» ya TASK 0.45 vía c.613, medido).
 *
 * Cobertura: 6 capturas (sujeta, desnuda, prefija, acuse, plural,
 * calificador «del tren») + 6 guards (negación conjugada/infinitivo,
 * pasado «saqué», duda «no sé si», nominal «el billete…»,
 * bivalente «sacar el pasaporte», declarativo «…cuesta 50 euros»)
 * + 8 pines (2 envolventes c.613 re-pines medidos; c.1151 visado,
 * c.717 basura, c.740 perro, c.893 dinero, c.1117 cita, c.1140
 * facturar-vuelo — kinds disjuntos intactos).
 */
class ContextIntentEngineSacarBilleteFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `sacar el billete de tren manana captura TASK con titulo limpio y dueAt`() {
        val r = analyze("sacar el billete de tren mañana")
        assertNotNull("«sacar el billete de tren mañana» debe capturar (era NULL en c.1200)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete de tren", r.title)
        assertTrue("debe resolver «mañana»", r.dueAt != null)
    }

    @Test
    fun `sacar el billete de tren desnuda captura TASK`() {
        val r = analyze("sacar el billete de tren")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete de tren", r.title)
    }

    @Test
    fun `prefijo temporal manana captura TASK con dueAt`() {
        val r = analyze("mañana sacar el billete de tren")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete de tren", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `acuse vale se despoja y captura TASK`() {
        val r = analyze("vale, sacar el billete de tren mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete de tren", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `plural los billetes del tren captura TASK`() {
        val r = analyze("sacar los billetes del tren mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar los billetes del tren", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `calificador del bus se conserva en el titulo`() {
        val r = analyze("sacar el billete del bus el lunes")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete del bus", r.title)
        assertTrue(r.dueAt != null)
    }

    // ---------- Guards (NULL deliberado, byte-idénticos PRE/POST) ----------

    @Test
    fun `negacion conjugada no saques queda NULL`() {
        assertNull(analyze("no saques el billete de tren todavía"))
    }

    @Test
    fun `negacion infinitivo no sacar queda NULL`() {
        assertNull(analyze("no sacar el billete de tren todavía"))
    }

    @Test
    fun `pasado saque queda NULL`() {
        assertNull(analyze("saqué el billete de tren ayer"))
    }

    @Test
    fun `duda no se si sacar queda NULL`() {
        assertNull(analyze("no sé si sacar el billete de tren mañana"))
    }

    @Test
    fun `nominal el billete de tren queda NULL`() {
        assertNull(analyze("el billete de tren"))
    }

    @Test
    fun `bivalente sacar el pasaporte queda NULL lateral`() {
        assertNull(analyze("sacar el pasaporte antes del vuelo"))
    }

    @Test
    fun `declarativo el billete cuesta queda NULL`() {
        assertNull(analyze("el billete de tren cuesta 50 euros"))
    }

    // ---------- Pines (PRE medidos; re-pins legítimos documentados) ----------

    @Test
    fun `pin envolvente tengo que sacar el billete sigue TASK`() {
        val r = analyze("tengo que sacar el billete de tren mañana")
        assertNotNull("la envolvente c.613 ya capturaba PRE (medido 0.45)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `pin envolvente recuerdame sacar el billete sigue TASK`() {
        val r = analyze("recuérdame sacar el billete de tren el lunes")
        assertNotNull("la envolvente c.613 ya capturaba PRE (medido 0.45)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `pin sacar el visado c1151 intacto`() {
        val r = analyze("sacar el visado antes del viaje")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el visado", r.title)
    }

    @Test
    fun `pin sacar la basura c717 kind HOUSEHOLD intacto`() {
        val r = analyze("sacar la basura mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test
    fun `pin sacar al perro c740 kind HOUSEHOLD intacto`() {
        val r = analyze("sacar al perro mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test
    fun `pin sacar dinero c893 kind ERRAND intacto`() {
        val r = analyze("sacar dinero mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
    }

    @Test
    fun `pin sacar cita c1117 intacto`() {
        val r = analyze("sacar cita mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `pin facturar el vuelo c1140 intacto`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}

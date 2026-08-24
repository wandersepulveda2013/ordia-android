package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1018 (renumerada c.1017->c.1018 por colisión de cycle-ID con el
 * c.1017 «desparasitar al perro/gato» del hermano): candidata (e) de la
 * fila clase DÉCIMA mascotas c.1007
 * (sonda fuente `TenthClassPetProbe.kt`) — «pasear al perro»,
 * sinónimo directo dicho-como-se-habla de «sacar al perro» (que SÍ
 * captura HOUSEHOLD desde c.740, con artículo directo c.756). El
 * paseo diario del perro es de los quehaceres de mascota más
 * frecuentes; decirlo con el verbo «pasear» caía a NULL (olvido
 * silencioso P1) mientras su sinónimo exacto se registraba.
 * Acotado deliberado, UNA por ciclo: (a) dativo «ponerle la
 * vacuna» RESUELTA c.1011, (b) dativo «darle la pastilla» RESUELTA
 * c.1012, (c) dativo «cortarle las uñas» RESUELTA c.1015 (hermano;
 * tomada durante este run — por eso esta unidad es (e) y no (c),
 * anti-colisión), (d) «desparasitar al perro» queda ABIERTA.
 * NULL PRE medido con sonda efímera `/tmp/probe1017/Probe.kt` sobre
 * HEAD 9920a22 (motor real vía `tools/run_probe.sh`; suite base OK
 * 7338): 6/6 formas con «pasear» NULL (gap confirmado — «pasear»
 * no es keyword de ningún kind; la keyword-mascota «perro» sola
 * queda bajo el umbral, misma aritmética medida c.1011/c.1012),
 * 8/8 guards NULL correctos (negación inmediata, «no voy a…» guard
 * c.1009, pasado «paseé…», hedge subjuntivo «quizá pasee…»,
 * sintagma nominal «el paseo del perro», destinatario humano «al
 * bebé», sin objeto mascota «salir a pasear», gato) y 8/8
 * regresiones HIT intactas (sacar al perro c.740, sacar la perra
 * c.756, veterinario c.747, vacunar c.757, pastilla dativa c.1012,
 * vacuna dativa indefinida c.1014, uñas dativas c.1015, médico
 * APPOINTMENT).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * directo del piso [HOUSEHOLD_PET_FLOOR] c.740): NUEVO piso
 * [HOUSEHOLD_WALK_DOG_FLOOR] con el MISMO acotamiento al objeto
 * mascota `perr[oa]s?` («pasear» suelto es bivalente — salir a
 * pasear uno mismo / pasear al bebé / pasear la mirada — así el
 * objeto humano y la forma sin mascota quedan FUERA) incluyendo la
 * alternancia de artículo directo de c.756 («pasear la perra») +
 * la MISMA forma en la cláusula de negación dedicada de
 * [imperativeIsNegated] (cinturón y tirantes, precedente c.740
 * «sacar al perro») + plantilla de título dedicada en
 * [extractTitle] (objeto = ANCLA, no se despoja; [sanitizeTitle]
 * depura la cola temporal). CERO keywords nuevas en
 * `ContextIntent.kt`: la keyword-mascota «perro/perra» ya existe
 * (c.740) y el piso basta (medido PRE); «pasear» NO se añade como
 * keyword (bivalente: «salir a pasear» quedaría a un bono temporal
 * del umbral — pin NULL).
 * Anti-overreach intacto: «pasear al gato» queda FUERA (pin NULL —
 * el piso hermano c.740 es perro-only; lateral documentada), el
 * destinatario humano («pasear al bebé») NULL (pin), la negación
 * inmediata la bloquean el lookbehind del piso Y la cláusula, el
 * guard c.1009 descarta «no voy a pasear…» antes del piso (pin),
 * el pasado («paseé al perro») y el hedge («quizá pasee…») no
 * casan el infinitivo literal, y el sintagma nominal («el paseo del
 * perro») sigue NULL (keyword sola bajo el umbral).
 */
class ContextIntentEnginePasearPerroFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (piso, sinónimo de «sacar al perro» c.740) ----

    @Test
    fun `captura pasear al perro con cola temporal relativa`() {
        // Cola relativa «después de cenar»: sin hora concreta → dueAt
        // ausente y la cola queda en el título, comportamiento
        // BYTE-IDÉNTICO al hermano «sacar al perro después de cenar»
        // (c.740; medido en sonda POST, caso C1b). Depurar colas relativas
        // del título es lateral preexistente de TODA la familia de pisos,
        // no de esta unidad.
        val i = analyze("pasear al perro después de cenar")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al perro después de cenar", i.title)
        assertNull(i.dueAt)
    }

    @Test
    fun `captura pasear al perro con hora`() {
        val i = analyze("pasear al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a la perra`() {
        val i = analyze("pasear a la perra mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a la perra", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a los perros plural`() {
        val i = analyze("pasear a los perros esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a los perros", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura pasear a mi perro posesivo`() {
        val i = analyze("pasear a mi perro el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear a mi perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("mañana pasear al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con articulo directo variante c756`() {
        val i = analyze("pasear la perra a las 7")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear la perra", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no pasear al perro hoy"))
    }

    @Test
    fun `negacion de envolvente de plan bloqueada por guard c1009`() {
        assertNull(analyze("no voy a pasear al perro"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("paseé al perro ayer"))
    }

    @Test
    fun `hedge subjuntivo no captura`() {
        assertNull(analyze("quizá pasee al perro mañana"))
    }

    @Test
    fun `sintagma nominal no captura`() {
        assertNull(analyze("el paseo del perro"))
    }

    @Test
    fun `destinatario humano fuera`() {
        assertNull(analyze("pasear al bebé a las 6"))
    }

    @Test
    fun `sin objeto mascota fuera`() {
        assertNull(analyze("salir a pasear esta tarde"))
    }

    @Test
    fun `gato fuera lateral documentada piso hermano perro-only c740`() {
        assertNull(analyze("pasear al gato mañana"))
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion piso sacar al perro c740`() {
        val i = analyze("sacar al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar al perro", i.title)
    }

    @Test
    fun `regresion piso sacar la perra articulo directo c756`() {
        val i = analyze("sacar la perra mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Sacar la perra", i.title)
    }

    @Test
    fun `regresion piso dativo pastilla c1012`() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    @Test
    fun `regresion piso dativo unas c1015`() {
        val i = analyze("cortarle las uñas al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
    }
}

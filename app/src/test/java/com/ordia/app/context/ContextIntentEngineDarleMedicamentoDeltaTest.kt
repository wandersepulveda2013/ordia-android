package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1063: lateral ABIERTA (2) documentada en c.1012/c.1059/c.1061/
 * c.1062 — el objeto «medicamento»/«medicina» del piso dativo de
 * mascota («darle el medicamento al perro», «darle la medicina al
 * gato», «dale un medicamento al perro»). El piso
 * [HOUSEHOLD_PILL_DATIVE_FLOOR] c.1012 (+c.1050 imperativo, +c.1059
 * indefinido) sólo admitía el objeto `pastillas?`; la medicación
 * veterinaria líquida o genérica se dice «medicamento»/«medicina»
 * dicho-como-se-habla (el jarabe de la tos del perro, el
 * medicamento del tratamiento del gato) y caía a NULL (olvido
 * silencioso P1: tratamiento de la mascota sin recordatorio ni
 * What Now). La hermana humana «tomar el medicamento» ya captura
 * TASK (c.859, c.1062) y la dativa «darle la pastilla» captura
 * HOUSEHOLD (c.1012). «medicamento»/«medicina» sueltos son
 * bivalentes (medicación humana — de hecho son objetos del piso
 * TASK «tomar»), así el destinatario sigue ACOTADO a mascota
 * `(?:perr[oa]s?|gat[oa]s?)` (paridad estructural exacta con
 * `pastillas?` — «darle el medicamento al niño» queda FUERA, pin
 * en test). Medida PRE con sonda efímera `/tmp/probe1063/Probe.kt`
 * (motor real vía `tools/run_probe.sh`, HEAD c.1062 `4437726`):
 * 6/6 candidatas NULL (gap confirmado), 3/3 guards NULL correctos,
 * 3/3 regresiones HIT intactas (pastilla definida c.1012, pastilla
 * indefinida c.1059, vacuna c.1011), 3/3 FUERA NULL correctos;
 * envolvente «tengo que darle el medicamento al perro» ya ruteaba
 * TASK 0.45 con título limpio PRE vía el candado genérico de
 * recordatorio (RED-pass medido honestamente, patrón c.1055);
 * negación envolvente «tengo que no darle el medicamento al perro»
 * → TASK afirmativa con la negación en el título («No darle el
 * medicamento al perro») — comportamiento ESTABLE transversal
 * (paridad con c.1012/c.1015/c.1018/c.859 medidos c.1059/c.1061/
 * c.1062), pin aquí, NO regresión: lateral ABIERTA transversal.
 * Fix: alternancia de objeto del piso extendida de `pastillas?` a
 * `(?:pastillas?|medicamentos?|medicinas?)` en lockstep 3 puntos
 * (piso `score` + cláusula de negación `imperativeIsNegated` +
 * plantilla de título `extractTitle`; doctrina c.653). CERO
 * keywords nuevas (lección c.751): el piso basta; objeto
 * «medicamento» NO se añade como keyword para no capturar el
 * sintagma nominal «el medicamento del perro». Acotado deliberado
 * (UNA por ciclo): la negación envolvente transversal queda
 * ABIERTA; las colas relativas en títulos quedan ABIERTAS.
 */
class ContextIntentEngineDarleMedicamentoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas objeto medicamento/medicina (piso extendido) ----

    @Test
    fun `captura definido darle el medicamento al perro`() {
        val i = analyze("darle el medicamento al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle el medicamento al perro", i.title)
    }

    @Test
    fun `captura indefinido darle un medicamento al gato`() {
        val i = analyze("darle un medicamento al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle un medicamento al gato", i.title)
    }

    @Test
    fun `captura definido darle la medicina al perro`() {
        val i = analyze("darle la medicina al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la medicina al perro", i.title)
    }

    @Test
    fun `captura imperativo dale el medicamento al perro`() {
        val i = analyze("dale el medicamento al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale el medicamento al perro", i.title)
    }

    @Test
    fun `captura con temporal despojada del titulo`() {
        val i = analyze("darle el medicamento al perro a las 9")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle el medicamento al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse de escucha`() {
        val i = analyze("vale, darle el medicamento al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle el medicamento al gato", i.title)
    }

    // ---- Envolvente (RED-pass medida PRE: candado genérico) ----

    @Test
    fun `envolvente tengo que rutea task con titulo limpio`() {
        val i = analyze("tengo que darle el medicamento al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Darle el medicamento al perro", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata bloqueada`() {
        assertNull(analyze("no darle el medicamento al perro"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("le di el medicamento al perro ayer"))
    }

    @Test
    fun `hedge no captura`() {
        assertNull(analyze("quizá darle el medicamento al perro"))
    }

    // ---- Negación envolvente (comportamiento ESTABLE transversal — pin) ----

    @Test
    fun `negacion envolvente rutea task afirmativa pin estable`() {
        val i = analyze("tengo que no darle el medicamento al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No darle el medicamento al perro", i.title)
    }

    // ---- Regresiones (pisos hermanos intactos) ----

    @Test
    fun `regresion pastilla definida c1012`() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    @Test
    fun `regresion pastilla indefinida c1059`() {
        val i = analyze("dale una pastilla al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale una pastilla al gato", i.title)
    }

    @Test
    fun `regresion vacuna dativa c1011`() {
        val i = analyze("ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }

    // ---- FUERA estructural (NULL correctos) ----

    @Test
    fun `destinatario humano fuera`() {
        assertNull(analyze("darle el medicamento al niño"))
    }

    @Test
    fun `dativo sin objeto ancla fuera`() {
        assertNull(analyze("darle el medicamento"))
    }

    @Test
    fun `sintagma nominal fuera`() {
        assertNull(analyze("el medicamento del perro"))
    }
}

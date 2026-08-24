package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1029: PIN de privacidad deliberada — la candidata (a) de la clase
// UNDÉCIMA ("cambiar la contraseña del banco", sonda c.1026 C3) es
// RECHAZADA por diseño P0 (renumerado c.1027->c.1029 por colisión
// de cycle-ID con SU c.1027 parser «ya»): `ContextPrivacyFilter.blockedContentPatterns`
// descarta TODA notificación que contenga \bcontraseña\b "antes de
// cualquier análisis y en todas las fuentes" (el texto podría contener
// el secreto mismo: "tu contraseña es abc123"). TDD RED->fix->REVERT:
// la keyword-OBJETO "contraseña" en TASK no movía nada (el filtro manda
// sobre el gate de keywords) y relajar el filtro para distinguir la
// acción ("cambiar la contraseña") del secreto ("cambiar la contraseña
// a abc123") es una ventana estrecha con riesgo de fuga en el título
// capturado — anti-overreach + datos sagrados: NO se toca el filtro.
// Esta clase pinea el comportamiento medido PRE=POST (sonda efímera):
//   TODA forma con \bcontraseña\b sigue NULL (imperativa, envolvente,
//   declarativa, pasado, subjuntivo, negación).
//   El plural "contraseñas" NO casa el filtro (\b estricto, deliberado):
//   "cambiar las contraseñas de las cuentas el sábado" sigue capturando
//   TASK 0.45 por la keyword "cuentas" (pin byte-idéntico).
//   Los pisos vecinos sin la palabra bloqueada quedan intactos
//   ("cambiar euros/sábanas/de tema" c.710, "llamar a mamá" c.605).
class ContextIntentEnginePasswordPrivacyPinTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(
            source = ContextCaptureSource.NOTIFICATION,
            rawText = text,
            timestampMs = 1_700_000_000_000L,
        ),
    )

    // ---------- Privacidad deliberada: \bcontraseña\b -> NULL ----------

    @Test
    fun `cambiar la contrasena del banco queda NULL por privacidad deliberada`() {
        assertNull(analyze("cambiar la contraseña del banco esta tarde"))
    }

    @Test
    fun `cambiar la contrasena del correo queda NULL por privacidad deliberada`() {
        assertNull(analyze("cambiar la contraseña del correo mañana"))
    }

    @Test
    fun `cambiar mi contrasena queda NULL por privacidad deliberada`() {
        assertNull(analyze("cambiar mi contraseña esta noche"))
    }

    @Test
    fun `envolvente tengo que cambiar la contrasena queda NULL por privacidad`() {
        assertNull(analyze("tengo que cambiar la contraseña del banco esta tarde"))
    }

    @Test
    fun `envolvente recuerdame cambiar la contrasena queda NULL por privacidad`() {
        assertNull(analyze("recuérdame cambiar la contraseña del correo mañana"))
    }

    @Test
    fun `declarativo la contrasena es segura sigue NULL`() {
        assertNull(analyze("la contraseña es segura"))
    }

    @Test
    fun `contrasena aislada sigue NULL`() {
        assertNull(analyze("contraseña"))
    }

    @Test
    fun `pasado cambie la contrasena sigue NULL`() {
        assertNull(analyze("cambié la contraseña ayer"))
    }

    @Test
    fun `duda subjuntivo quizas cambie la contrasena sigue NULL`() {
        assertNull(analyze("quizá cambie la contraseña mañana"))
    }

    @Test
    fun `negacion plan no voy a cambiar la contrasena sigue NULL`() {
        assertNull(analyze("no voy a cambiar la contraseña hoy"))
    }

    // ---------- Plural fuera del filtro (\b estricto): pin byte-idéntico ----------

    @Test
    fun `declarativo plural las contrasenas estan guardadas sigue NULL`() {
        assertNull(analyze("las contraseñas están guardadas"))
    }

    @Test
    fun `cambiar las contrasenas de las cuentas sigue capturando por keyword cuentas`() {
        val i = analyze("cambiar las contraseñas de las cuentas el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence, 1e-6f)
        assertEquals("Cambiar las contraseñas de las cuentas", i.title)
        assertTrue(i.dueAt != null)
    }

    // ---------- Pisos vecinos intactos ----------

    @Test
    fun `cambiar euros en el banco sigue TASK piso c710`() {
        val i = analyze("cambiar euros en el banco")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence, 1e-6f)
        assertEquals("Cambiar euros en el banco", i.title)
        assertEquals(null, i.dueAt)
    }

    @Test
    fun `cambiar las sabanas el domingo sigue TASK piso c710`() {
        val i = analyze("cambiar las sábanas el domingo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence, 1e-6f)
        assertEquals("Cambiar las sábanas", i.title)
        assertTrue(i.dueAt != null)
    }

    @Test
    fun `cambiar de tema sigue TASK piso abierto deliberado c710`() {
        val i = analyze("cambiar de tema")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals(0.45f, i.confidence, 1e-6f)
        assertEquals("Cambiar de tema", i.title)
        assertEquals(null, i.dueAt)
    }

    @Test
    fun `llamar a mama esta noche sigue CALL`() {
        val i = analyze("llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
        assertEquals(0.67f, i.confidence, 1e-6f)
        assertEquals("Llamar a mamá", i.title)
        assertTrue(i.dueAt != null)
    }
}

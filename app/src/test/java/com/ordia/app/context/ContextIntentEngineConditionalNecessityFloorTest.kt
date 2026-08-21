package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de necesidad CONDICIONAL (c.835): «habría que…», «tendría que…» y
 * «debería…» + verbo.
 *
 * La sonda ConditionalNecessityProbe (c.826) reveló que la familia enrutaba
 * sólo cuando el verbo subordinado tenía piso de kind propio («habría que
 * llamar al fontanero» → CALL), pero caía a NULL con los verbos genéricos
 * («habría que comprar leche», «tendría que terminar el informe»): olvido
 * silencioso P1 asimétrico — las formas hermanas «tengo que/hay que» y las
 * formas desnudas SÍ enrutan (BareControlProbe c.835). El condicional de
 * necesidad reconoce una obligación real (doctrina c.649: «debería» no es
 * duda), así recibe el MISMO piso mínimo (0.45) que «tengo que» — nunca alta
 * confianza sobre lo condicional (anti-overreach). El título despoja el
 * envolvente centralmente en sanitizeTitle (alineación piso↔título, c.616).
 *
 * El mismo ciclo cierra el overreach hermano: la forma NEGADA del envolvente
 * condicional («no habría que llamar al fontanero») se persistía como CALL
 * 0.57 — lo OPUESTO de lo dicho (misma clase P1 que c.681); ahora es NULL.
 * El pasado de deber («debía/debí») sigue descartado (anti-overreach c.824).
 */
class ContextIntentEngineConditionalNecessityFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- GAP P1: verbos genéricos bajo envolvente condicional (antes NULL) ---

    @Test
    fun habriaQueShoppingIsCaptured() {
        val intent = analyze("habría que comprar leche")
        assertNotNull("'habría que comprar leche' reconoce una necesidad real", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
        assertEquals(0.45f, intent.confidence, 0.001f)
    }

    @Test
    fun habriaQueGenericTaskIsCaptured() {
        val intent = analyze("habría que terminar el informe")
        assertNotNull("'habría que terminar el informe' reconoce una necesidad real", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
    }

    @Test
    fun tendriaQueGenericTaskIsCaptured() {
        val intent = analyze("tendría que terminar el informe")
        assertNotNull("'tendría que terminar el informe' reconoce una necesidad real", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
    }

    @Test
    fun deberiaGenericTaskIsCaptured() {
        val intent = analyze("debería hacer copias de seguridad")
        assertNotNull("'debería hacer copias de seguridad' reconoce una necesidad real", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copias de seguridad", intent.title)
    }

    @Test
    fun habriaQuePaymentIsCaptured() {
        val intent = analyze("habría que pagar la factura de la luz")
        assertNotNull("'habría que pagar la factura' reconoce una necesidad real", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pagar la factura de la luz", intent.title)
    }

    // --- Plurales / 2ª persona de la familia (antes NULL) ---

    @Test
    fun habriamosQueIsCaptured() {
        val intent = analyze("habríamos que comprar leche")
        assertNotNull("'habríamos que…' es la misma necesidad en plural", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun deberiamosIsCaptured() {
        val intent = analyze("deberíamos hacer copias de seguridad")
        assertNotNull("'deberíamos…' es la misma necesidad en plural", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copias de seguridad", intent.title)
    }

    // --- Kinds específicos: el piso de kind sigue ganando (sin regresión) ---

    @Test
    fun habriaQueCallStillRoutesToCall() {
        val intent = analyze("habría que llamar al fontanero")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Llamar al fontanero", intent.title)
    }

    @Test
    fun habriaQueAppointmentStillRoutesToAppointment() {
        val intent = analyze("habría que ir al médico")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Ir al médico", intent.title)
    }

    // --- P3: títulos residuales despojados (backlog c.826) ---

    @Test
    fun habriaQueErrandTitleIsStripped() {
        val intent = analyze("habría que recoger el paquete")
        assertNotNull(intent)
        assertEquals("Recoger el paquete", intent!!.title)
    }

    @Test
    fun recuerdameQueDeberiaTitleIsStripped() {
        val intent = analyze("recuérdame que debería llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar al banco", intent.title)
    }

    // --- Anti-overreach: negación del envolvente condicional (antes CALL 0.57) ---

    @Test
    fun negatedHabriaQueIsNotCaptured() {
        val intent = analyze("no habría que llamar al fontanero")
        assertNull("'no habría que…' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedDeberiaIsNotCaptured() {
        val intent = analyze("no debería llamar al banco")
        assertNull("'no debería…' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedTendriaQueIsNotCaptured() {
        val intent = analyze("no tendría que comprar leche")
        assertNull("'no tendría que…' niega la obligación, no debe capturarse", intent)
    }

    // --- Anti-overreach: pasado de deber sigue descartado (c.824) ---

    @Test
    fun pastDeberIsNotCaptured() {
        assertNull("'debía llamar al banco' es pasado, no compromiso futuro",
            analyze("debía llamar al banco"))
        assertNull("'debí llamar al banco' es pasado, no compromiso futuro",
            analyze("debí llamar al banco"))
    }

    // --- Controles: títulos legítimos con "que" no se tocan ---

    @Test
    fun legitimateQueTitleIsPreserved() {
        val intent = analyze("tengo que confirmar que llegó el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        // La "que" INTERNA es contenido, no subordinación residual inicial.
        assertEquals("Confirmar que llegó el paquete", intent.title)
    }
}

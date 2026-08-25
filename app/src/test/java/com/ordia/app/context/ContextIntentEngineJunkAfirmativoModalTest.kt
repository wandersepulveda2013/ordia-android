package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1068 (P1 precisión — junk AFIRMATIVO de envolvente modal de obligación).
 * Sub-lateral medida por SU c.1064 (sonda Probe4) y registrada con doctrina
 * aparte: el piso de envolvente (c.613/c.835) sólo exige `\s+\w` tras el
 * wrapper, así «tengo que es eso», «tengo que mañana», «hay que eso»,
 * «habría que eso», «debería eso»… se persistían como TASK 0.45 basura
 * («Es eso», «Mañana», «Eso») — ruido conversacional confirmado como
 * pendiente en la lista del usuario. Doctrina aparte (NO exigir infinitivo
 * tras TODO envolvente): «no olvides las llaves», «recuérdame tu
 * cumpleaños», «cancelar la cita del dentista» aceptan complemento
 * NOMINAL legítimo, y el piso REMINDER (c.619) acepta complemento
 * temporal/relativo («avísame mañana de la reunión», «avisame cuando
 * llegue el paquete» — pin c.1067). Los modales de obligación («tengo
 * que/q», «hay que», «habría/tendría que», «debería (que)») exigen
 * INFINITIVO por gramática: cualquier complemento no-infinitivo es junk.
 * Fix: guard [obligationModalLacksInfinitive] en el sitio del piso TASK
 * (hermano de [wrapperNegationLacksInfinitive] c.1064): salta un «no»
 * opcional (la captura FIEL de prohibición «tengo que no llamar a mamá»
 * se conserva) y suprime el piso si la primera palabra alfabética no es
 * infinitivo-like ([INFINITIVE_LIKE] con enclíticos y tildes ár/ér/ír).
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineJunkAfirmativoModalTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Junk afirmativo: modal de obligación + NO infinitivo → NULL ---

    @Test
    fun tengoQueEsEso_quedaNull() {
        assertNull(analyze("tengo que es eso"))
    }

    @Test
    fun tengoQueSiClaro_quedaNull() {
        assertNull(analyze("tengo que sí, claro"))
    }

    @Test
    fun tengoQueManana_quedaNull() {
        assertNull(analyze("tengo que mañana"))
    }

    @Test
    fun tengoQManana_quedaNull() {
        assertNull(analyze("tengo q mañana"))
    }

    @Test
    fun hayQueEso_quedaNull() {
        assertNull(analyze("hay que eso"))
    }

    @Test
    fun hayQueManana_quedaNull() {
        assertNull(analyze("hay que mañana"))
    }

    @Test
    fun habriaQueEso_quedaNull() {
        assertNull(analyze("habría que eso"))
    }

    @Test
    fun tendriaQueManana_quedaNull() {
        assertNull(analyze("tendría que mañana"))
    }

    @Test
    fun deberiaEso_quedaNull() {
        assertNull(analyze("debería eso"))
    }

    @Test
    fun deberiaQueManana_quedaNull() {
        assertNull(analyze("debería que mañana"))
    }

    @Test
    fun tengoQueElLunes_quedaNull() {
        assertNull(analyze("tengo que el lunes"))
    }

    @Test
    fun hayQueSi_quedaNull() {
        assertNull(analyze("hay que sí"))
    }

    // --- Pins legítimos: infinitivo tras el modal → TASK intacto ---

    @Test
    fun tengoQueLlamarAMama_sigueTask() {
        val intent = analyze("tengo que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun tengoQueComprarPan_sigueTask() {
        val intent = analyze("tengo que comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar pan", intent.title)
    }

    @Test
    fun hayQueComprarLeche_sigueTask() {
        val intent = analyze("hay que comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun habriaQueComprarLeche_sigueTask() {
        val intent = analyze("habría que comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun tendriaQueTerminarElInforme_sigueTask() {
        val intent = analyze("tendría que terminar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
    }

    @Test
    fun deberiaHacerCopias_sigueTask() {
        val intent = analyze("debería hacer copias de seguridad")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer copias de seguridad", intent.title)
    }

    @Test
    fun tengoQueIr_sigueTask() {
        val intent = analyze("tengo que ir")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ir", intent.title)
    }

    // --- Captura FIEL de prohibición: «no» + infinitivo se conserva ---

    @Test
    fun tengoQueNoLlamarAMama_capturaFiel() {
        val intent = analyze("tengo que no llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("No llamar a mamá", intent.title)
    }

    @Test
    fun hayQueNoFumar_capturaFiel() {
        val intent = analyze("hay que no fumar")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("No fumar", intent.title)
    }

    // --- Doctrina aparte: complementos NOMINALES/temporales legítimos ---

    @Test
    fun noOlvidesLasLlaves_complementoNominalSigueTask() {
        val intent = analyze("no olvides las llaves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Las llaves", intent.title)
    }

    @Test
    fun recuerdameTuCumpleanos_complementoNominalSigueTask() {
        val intent = analyze("recuérdame tu cumpleaños")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tu cumpleaños", intent.title)
    }

    @Test
    fun cancelarLaCitaDelDentista_objetoNominalSigueTask() {
        val intent = analyze("cancelar la cita del dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cancelar la cita del dentista", intent.title)
    }

    @Test
    fun avisameMananaDeLaReunion_complementoTemporalSigueReminder() {
        val intent = analyze("avísame mañana de la reunión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Mañana de la reunión", intent.title)
    }

    @Test
    fun avisameCuandoLlegueElPaquete_clausulaRelativaSigueReminder() {
        val intent = analyze("avisame cuando llegue el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Cuando llegue el paquete", intent.title)
    }
}

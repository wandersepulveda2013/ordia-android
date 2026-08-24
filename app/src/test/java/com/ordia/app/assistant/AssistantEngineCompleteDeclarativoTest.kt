package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1000: UNIÓN transversal tras COLISIÓN cycle-ID c.998/c.998 CONVERGENTE con
// el hermano (misma lateral «completé/terminé <tarea>» → COMPLETE_TASK; su
// implementación — rama unificada en markDoneCapture con «acabé» y despoje
// del «de» en la regex — integrada NO-destructiva; producción propia
// descartada, precedente c.988→c.989). Esta clase queda como pin
// transversal independiente (18 tests) y su delta medido sobre la base del
// hermano (RED exacto: EXACTAMENTE 3 fallos): stopwords += «lo»
// («ya terminé lo del gimnasio») y «tarea» («completé la tarea de …»)
// + pelada meta-palabra («completé la tarea») → guía honesta en vez del
// «no encuentro…«la tarea»» confuso.
// Historia de la medida (c.998 propio, base d243d2e): sonda efímera
// /tmp/probe998/CompleteDeclarativoProbe.kt PRE — 6/6 candidatas al menú
// genérico (mentira por omisión); 2/2 peladas al menú; 7/7 guards NONE;
// 3/3 controles hermanos intactos.
// Anti-overreach: ancla ^ disjunta negación («no terminé…»), futuro
// («terminaré…»), presente («casi termino…») e interrogativa («¿ya terminé…?»);
// coincidencia EXACTAMENTE única sobre tareas pendientes; cero → guía honesta;
// varias → lista honesta SIN acción; completadas/archivadas NUNCA se ofrecen.
class AssistantEngineCompleteDeclarativoTest {

    private val base = listOf(
        TaskEntity(id = 1, title = "Llamar al banco"),
        TaskEntity(id = 2, title = "Pagar la luz"),
        TaskEntity(id = 3, title = "Enviar el informe"),
        TaskEntity(id = 4, title = "Ir al gimnasio"),
        TaskEntity(id = 5, title = "Revisión médica")
    )

    private fun answer(request: String, tasks: List<TaskEntity> = base) =
        AssistantEngine.answer(request, tasks, emptyList(), emptyList())

    // --- Captura declarativa con coincidencia única → COMPLETE_TASK ---

    @Test fun termine_elInforme_confirmaCompletar() {
        val a = answer("terminé el informe")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("3", a.actionPayload)
        assertEquals(listOf(3L), a.relatedTaskIds)
        assertTrue(a.text.contains("Enviar el informe"))
    }

    @Test fun termineSinTilde_escrituraMovilCaptura() {
        val a = answer("termine el informe")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("3", a.actionPayload)
    }

    @Test fun complete_laRevisionMedica_confirmaCompletar() {
        val a = answer("completé la revisión médica")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("5", a.actionPayload)
    }

    @Test fun yaTermine_loDelGimnasio_confirmaCompletar() {
        val a = answer("ya terminé lo del gimnasio")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("4", a.actionPayload)
    }

    @Test fun termineDe_infinitivo_confirmaCompletar() {
        val a = answer("terminé de pagar la luz")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("2", a.actionPayload)
    }

    @Test fun yaComplete_llamarAlBanco_confirmaCompletar() {
        val a = answer("ya completé llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    @Test fun complete_laTareaDe_metaPalabraNoEnvenena() {
        // «la tarea de …» es meta-referencia: «tarea» no debe exigirse en el título.
        val a = answer("completé la tarea de llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    // --- Peladas → guía honesta SIN acción (NUNCA completar a ciegas) ---

    @Test fun terminePelada_guiaHonestaSinAccion() {
        val a = answer("terminé")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("qué tarea"))
    }

    @Test fun completeLaTarea_peladaMetaPalabra_guiaHonestaSinAccion() {
        val a = answer("completé la tarea")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("qué tarea"))
    }

    // --- Sin coincidencia / múltiples → honesto SIN acción ---

    @Test fun termineDeTrabajar_sinCoincidencia_guiaHonestaSinAccion() {
        val a = answer("terminé de trabajar")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("no encuentro"))
    }

    @Test fun termineCompletada_noLaOfrece() {
        val tasks = listOf(TaskEntity(id = 6, title = "Sacar al perro", completed = true))
        val a = answer("terminé de sacar al perro", tasks)
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("no encuentro"))
    }

    @Test fun termine_multiplesCoincidencias_listaHonestaSinAccion() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Llamar a Ana"),
            TaskEntity(id = 2, title = "Llamar a Luis")
        )
        val a = answer("terminé de llamar", tasks)
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertEquals(listOf(1L, 2L), a.relatedTaskIds)
        assertTrue(a.text.lowercase().contains("varias"))
    }

    // --- Guards anti-overreach (pasan ya) ---

    @Test fun noTermine_negacionNoCaptura() {
        val a = answer("no terminé el informe")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertFalse(a.text.contains("¿Marco"))
    }

    @Test fun terminare_futuroNoCaptura() {
        val a = answer("terminaré el informe mañana")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertFalse(a.text.contains("¿Marco"))
    }

    @Test fun casiTermino_presenteNoCaptura() {
        val a = answer("casi termino el informe")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertFalse(a.text.contains("¿Marco"))
    }

    @Test fun interrogativa_noCaptura() {
        val a = answer("¿ya terminé el informe?")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertFalse(a.text.contains("¿Marco"))
    }

    // --- Regresiones hermanas (pasan ya) ---

    @Test fun marcaComoHecha_regresionIntacta() {
        val a = answer("marca como hecha llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    @Test fun recuerdame_regresionIntacta() {
        val a = answer("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar al banco mañana", a.actionPayload)
    }
}

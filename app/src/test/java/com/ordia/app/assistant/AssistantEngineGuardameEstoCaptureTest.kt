package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.980 (re-numerado desde c.979 por COLISIÓN cycle-ID c.979/c.979 convergente
// con el hermano — precedentes c.977/c.978, c.972/c.973): este run implementó
// la lateral (a) «guárdame/guardame esto/eso: …» (sonda PRE reconstruida
// /tmp/probe979/GuardameEstoProbe.kt: 4/4 GAP; TDD RED exacto 12 run/6 fallos
// → GREEN 12/12) cuando el re-fetch OBLIGATORIO pre-push detectó que el
// hermano había publicado `34ffbea` (SU c.979) con la MISMA lateral (a) —
// regex funcionalmente idénticas — MÁS la lateral (b) «-melo» y 21 tests.
// Su producción es ESTRICTAMENTE superior → la mía se descartó
// NO-destructivo (reset --soft del commit propio NO publicado → stash →
// pull --ff-only → checkout del test desde el stash → drop).
// Verificación independiente sobre `21b0726` con la MISMA sonda: GAPS=0,
// 16/16 OK (4/4 capturas + 6/6 guards + 4/4 controles + 2 implícitos).
// Este archivo conserva ÚNICAMENTE el delta test-only: 4 pins que sus 21
// tests no ejercen — mayúsculas, indefinido sin «esto/eso», enclítico
// totalmente desnudo y perífrasis sin ancla (versión del hermano como base;
// el resto de mis 12 tests era subconjunto de los suyos).
class AssistantEngineGuardameEstoCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- pin: (?i) — la captura es insensible a mayúsculas ----------

    @Test fun guardameEstoMayusculas_creaNota() {
        val answer = ask("Guárdame esto: llamar a Ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    // ---------- pins guard: el enclítico desnudo NUNCA es captura ----------

    @Test fun guardameUnAsiento_noEsCaptura() {
        val answer = ask("guárdame un asiento")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameASecas_noEsCaptura() {
        val answer = ask("guárdame")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeGuardesEsto_noEsCaptura() {
        val answer = ask("quiero que me guardes esto")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ContextualAnalyzerTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = LocalDateTime.of(2026, 7, 30, 8, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun visitTomorrow_becomesEvent() {
        val result = ContextualAnalyzer.analyze("Mañana iré a tu casa a las 5 pm", now, zone)
        assertNotNull(result)
        assertEquals(ContextualKind.EVENT, result?.kind)
        assertNotNull(result?.dueAt)
        assertTrue(result!!.confidence >= 0.8)
    }

    @Test fun study_becomesStudy() {
        assertEquals(ContextualKind.STUDY, ContextualAnalyzer.analyze("Estoy estudiando para el examen", now, zone)?.kind)
    }

    @Test fun password_isRejected() {
        assertNull(ContextualAnalyzer.analyze("Mi contraseña es 123456", now, zone))
    }

    @Test fun fingerprint_isStable() {
        val a = ContextualAnalyzer.analyze("Mañana debo pagar", now, zone)
        val b = ContextualAnalyzer.analyze("  mañana   debo pagar ", now, zone)
        assertEquals(a?.id, b?.id)
    }
}

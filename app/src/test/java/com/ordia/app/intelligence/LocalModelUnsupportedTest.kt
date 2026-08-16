package com.ordia.app.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del contrato honesto del motor local (ORD-003).
 *
 * El modelo generativo NO está integrado. Estas pruebas garantizan que el
 * estado sea explícito y que ninguna respuesta finja inferencia.
 */
class LocalModelUnsupportedTest {

    @Test
    fun unsupportedReasonIsDocumentedAndNonBlank() {
        val reason = GenerativeModelStatus.UNAVAILABLE_REASON
        assertTrue(reason.isNotBlank())
        // Debe explicar la causa técnica (tokenizador / API de tarea).
        assertTrue(
            "La razón debe documentar la causa técnica: $reason",
            reason.contains("no está integrado")
        )
    }

    @Test
    fun unsupportedReasonExplainsWhatWorks() {
        val reason = GenerativeModelStatus.UNAVAILABLE_REASON
        assertTrue(reason.contains("reglas deterministas"))
        assertTrue(reason.contains("no ofrece descargas"))
    }

    @Test
    fun responsePropagatesUnsupportedReason() {
        val response = IntelligenceResponse(
            schema = IntelligenceSchema(),
            providerSource = ProviderSource.LOCAL_MODEL,
            unsupportedReason = GenerativeModelStatus.UNAVAILABLE_REASON
        )
        assertEquals(GenerativeModelStatus.UNAVAILABLE_REASON, response.unsupportedReason)
        assertEquals(ProviderSource.LOCAL_MODEL, response.providerSource)
    }

    @Test
    fun responseDefaultsToNoReason() {
        val response = IntelligenceResponse(schema = IntelligenceSchema())
        assertNull(response.unsupportedReason)
    }

    @Test
    fun unsupportedResponseIsNeverActionable() {
        val response = IntelligenceResponse(
            schema = IntelligenceSchema(),
            providerSource = ProviderSource.LOCAL_MODEL,
            unsupportedReason = GenerativeModelStatus.UNAVAILABLE_REASON
        )
        assertFalse(response.isActionable)
        assertEquals(0f, response.confidenceScore)
    }

    @Test
    fun supportedProvidersLeaveReasonNull() {
        // BasicRuleProvider produce respuestas reales sin razón de no soporte.
        val response = IntelligenceResponse(
            schema = IntelligenceSchema(actionSuggested = ActionSuggested.TASK),
            confidenceScore = 0.6f,
            providerSource = ProviderSource.BASIC_RULE
        )
        assertNull(response.unsupportedReason)
        assertTrue(response.isActionable)
    }

    @Test
    fun reasonIsStableConstant() {
        assertNotNull(GenerativeModelStatus.UNAVAILABLE_REASON)
        // Una constante const no cambia entre llamadas.
        assertEquals(GenerativeModelStatus.UNAVAILABLE_REASON, GenerativeModelStatus.UNAVAILABLE_REASON)
    }
}

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
 * La inferencia local NO está implementada (Gemma TFLite requiere la API de
 * tarea con tokenizador). Estas pruebas garantizan que el estado Unsupported
 * sea explícito y documentado, y que la respuesta lo propague sin fingir éxito.
 */
class LocalModelUnsupportedTest {

    @Test
    fun unsupportedReasonIsDocumentedAndNonBlank() {
        val reason = LocalModelProvider.UNSUPPORTED_REASON
        assertTrue(reason.isNotBlank())
        // Debe explicar la causa técnica (tokenizador / API de tarea).
        assertTrue(
            "La razón debe documentar la causa técnica: $reason",
            reason.contains("tokenizador") || reason.contains("API de tarea")
        )
    }

    @Test
    fun unsupportedReasonExplainsWhatWorks() {
        val reason = LocalModelProvider.UNSUPPORTED_REASON
        // Debe ser honesta sobre lo que sí funciona (descarga/carga).
        assertTrue(reason.contains("descarga"))
        assertTrue(reason.contains("carga"))
    }

    @Test
    fun responsePropagatesUnsupportedReason() {
        val response = IntelligenceResponse(
            schema = IntelligenceSchema(),
            providerSource = ProviderSource.LOCAL_MODEL,
            unsupportedReason = LocalModelProvider.UNSUPPORTED_REASON
        )
        assertEquals(LocalModelProvider.UNSUPPORTED_REASON, response.unsupportedReason)
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
            unsupportedReason = LocalModelProvider.UNSUPPORTED_REASON
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
        assertNotNull(LocalModelProvider.UNSUPPORTED_REASON)
        // Una constante const no cambia entre llamadas.
        assertEquals(LocalModelProvider.UNSUPPORTED_REASON, LocalModelProvider.UNSUPPORTED_REASON)
    }
}

package com.ordia.app.conversations

import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentEngineTest {
    @Test
    fun classifiesOwnAndOtherCommitmentsFromSelectedIdentity() {
        val messages = listOf(
            ChatMessage("Yo", "Te envío el informe mañana a las 8"),
            ChatMessage("Carlos", "Yo me encargo de llamar el lunes")
        )

        val result = CommitmentEngine.extract(messages, selfParticipant = "Yo", scopeHash = "chat-1")

        assertEquals(2, result.size)
        assertEquals(CommitmentOwner.SELF, result[0].owner)
        assertEquals(CommitmentOwner.OTHER, result[1].owner)
        assertNotNull(result[0].dueAt)
    }

    @Test
    fun classifiesRequestsAndMeetings() {
        val result = CommitmentEngine.extract(
            listOf(
                ChatMessage("Ana", "Envíame el informe antes del viernes"),
                ChatMessage("Ana", "Nos vemos el lunes en la oficina central")
            ),
            selfParticipant = "Yo",
            scopeHash = "chat-2"
        )

        assertEquals(CommitmentKind.REQUEST, result[0].kind)
        assertEquals(CommitmentKind.MEETING, result[1].kind)
        assertTrue(result[1].location.contains("oficina", ignoreCase = true))
    }

    @Test
    fun blocksVerificationCodesBeforeExtraction() {
        val text = "Tu código de verificación es 482913. No olvides guardarlo"

        assertTrue(ConversationPrivacyPolicy.containsSensitiveContent(text))
        assertTrue(
            CommitmentEngine.extract(
                listOf(ChatMessage("Sistema", text)),
                scopeHash = "chat-3"
            ).isEmpty()
        )
    }

    @Test
    fun duplicateMessagesProduceOneProposal() {
        val message = ChatMessage("Ana", "Te llamo mañana")
        val result = CommitmentEngine.extract(listOf(message, message), scopeHash = "chat-4")

        assertEquals(1, result.size)
        assertFalse(result.single().fingerprint.isBlank())
    }

    @Test
    fun summaryDoesNotCopyWholeConversation() {
        val preview = ChatImportParser.parse("Ana: Te llamo mañana\nYo: Gracias", "chat.txt")
        val commitments = CommitmentEngine.extract(preview.messages, "Yo", preview.contentHash)
        val summary = ConversationSummaryEngine.summarize(preview, commitments)

        assertTrue(summary.contains("2 mensajes"))
        assertFalse(summary.contains("Te llamo mañana"))
    }

    // --- Detección de verbos de compromiso naturales (c.278) ---
    // "me encargo" y "me ocupo" son las formas más naturales en español de
    // asumir un compromiso ("¿Quién llama?" → "me encargo"). Antes la señal
    // exigía "yo me encargo" (con pronombre), de modo que "me encargo de
    // llamar al fontanero" NO generaba compromiso alguno (falso negativo:
    // riesgo de olvido). Estas pruebas fijan la cobertura con y sin "yo".

    @Test
    fun detectsMeEncargoWithoutExplicitYo() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "me encargo de llamar al fontanero")),
            selfParticipant = "Yo",
            scopeHash = "chat-5"
        )

        assertEquals(1, result.size)
        assertEquals(CommitmentOwner.SELF, result[0].owner)
        assertEquals(CommitmentKind.SELF_COMMITMENT, result[0].kind)
    }

    @Test
    fun stillDetectsYoMeEncargo() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "yo me encargo de llamar el lunes")),
            selfParticipant = "Yo",
            scopeHash = "chat-6"
        )

        assertEquals(1, result.size)
        assertEquals(CommitmentOwner.SELF, result[0].owner)
    }

    @Test
    fun detectsMeOcupoAsCommitment() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "me ocupo de avisar a todos")),
            selfParticipant = "Yo",
            scopeHash = "chat-7"
        )

        assertEquals(1, result.size)
        assertEquals(CommitmentOwner.SELF, result[0].owner)
        assertEquals(CommitmentKind.SELF_COMMITMENT, result[0].kind)
    }

    @Test
    fun meEncargoFromOtherParticipantIsOtherCommitment() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Carlos", "me encargo de traer las sillas")),
            selfParticipant = "Yo",
            scopeHash = "chat-8"
        )

        assertEquals(1, result.size)
        assertEquals(CommitmentOwner.OTHER, result[0].owner)
    }

    // c.279: una negacion directa ("no te llamo", "no me encargo") es una
    // NEGATIVA, no un compromiso. Marcarla como compromiso es un falso positivo
    // (IA deshonesta: el usuario dijo que NO hara la accion). Verificado por probe
    // JVM: 6 formas de negativa directa se detectaban como compromiso.
    @Test
    fun directNegationIsNotACommitment() {
        val negatives = listOf(
            "no te llamo hasta manana",
            "no me encargo de eso",
            "no lo hago yo",
            "no voy a ir",
            "no debo olvidarlo",
            "no tengo que hacerlo hoy"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "neg-$text"
            )
            assertEquals("una negativa NO debe generar draft: \"$text\"", 0, result.size)
        }
    }

    @Test
    fun negationFarFromVerbDoesNotBlockRealCommitment() {
        // "no" aparece pero lejos del verbo de compromiso -> SI es compromiso real.
        // Garantiza que la guarda de negacion no sea excesiva (no rompa positivos).
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "no tengo tiempo, lo hago manana")),
            selfParticipant = "Yo",
            scopeHash = "neg-far"
        )
        assertEquals(1, result.size)
        assertEquals(CommitmentKind.SELF_COMMITMENT, result[0].kind)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * TDD RED (c.658): [CHAT_WORDS] contiene frases multi-palabra ("buenos días",
 * "qué tal", "cómo estás", "nos vemos", "buenas noches") pero [isCasualChat]
 * comparaba cada token por espacios contra la lista → las entradas multi-palabra
 * eran INALCANZABLES (entrada muerta). La proporción de chat era 0 para una
 * frase que DEBÍA ser descartada como charla casual.
 */
class ContextIntentChatWordsTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    @Test
    fun multiWordChatBuenosDiasIsDiscarded() {
        assertNull(analyze("buenos días"))
    }

    @Test
    fun multiWordChatQueTalIsDiscarded() {
        assertNull(analyze("qué tal ahi"))
    }

    @Test
    fun multiWordChatNosVemosIsDiscarded() {
        assertNull(analyze("nos vemos"))
    }

    @Test
    fun multiWordChatComoEstasIsDiscarded() {
        assertNull(analyze("cómo estás"))
    }

    @Test
    fun singleWordChatStillDiscardedControl() {
        assertNull(analyze("ok gracias"))
    }

    @Test
    fun taskStillNotCasualControl() {
        assertNotNull(analyze("recuérdame llamar a mamá"))
    }
}

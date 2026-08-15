package com.ordia.app.conversations

import com.ordia.app.context.ContextPrivacyFilter
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

    @Test
    fun blocksFinancialAndCryptoContentThatEscapedNotificationsGate() {
        // Estos contenidos llegan vía SMS/mensajería (paquete no bancario): pasan el
        // filtro de paquete de NotificationObservationPolicy y dependen del filtro de
        // contenido. Antes de c.286 escapaban porque ConversationPrivacyPolicy no
        // cubría contexto financiero/cripto en texto plano. (c.286)
        val leaks = listOf(
            "Tu saldo disponible es 45000 MXN",
            "Estado de cuenta de tu tarjeta listo",
            "Confirma la transferencia a la cuenta",
            "Realiza el depósito antes del cierre",
            "Mi frase semilla es uno dos tres cuatro",
            "Guarda tu recovery phrase en lugar seguro",
            "Te paso el IBAN para el pago",
            "anota el numero de cuenta del cliente"
        )
        leaks.forEach { text ->
            assertTrue("debería bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "leak").isEmpty()
            )
        }
    }

    @Test
    fun innocentConversationTextIsNotBlockedByPrivacyGate() {
        // Regresión: la paridad financiera no debe bloquear chats cotidianos legítimos.
        val innocent = listOf(
            "Te envío el informe mañana",
            "Nos vemos el viernes a las 3",
            "Reunion en la oficina a las 10",
            "Te llamo despues para coordinar",
            // c.292: "clave" en peludo sin secreto numérico cercano NO debe bloquearse
            // (falso positivo → pérdida de chat legítimo). El otpCode exige dígitos.
            "la clave del éxito es la constancia",
            "me dio la clave para resolverlo",
            "vamos en clave de tranquilidad"
        )
        innocent.forEach { text ->
            assertFalse("no debería bloquearse (falso positivo): \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
        }
    }

    @Test
    fun blocksPrivateKeysThatEscapedNotificationsGate() {
        // Claves privadas cripto (hex 64, con/sin prefijo 0x) y bloques PEM
        // -----BEGIN ... PRIVATE KEY-----. Llegan por SMS/mensajería (paquete no
        // bancario): pasan el filtro de paquete y dependen del de contenido. El gate
        // de contexto/IME (ContextPrivacyFilter) ya las bloquea, pero el de
        // notificaciones (este) NO → el texto se persistía en la BD de conversaciones.
        // Misma clase de fuga que c.287 cerró para seed phrases. (c.290)
        val leaks = listOf(
            "Guarda tu clave: 0x4c0883a6940d54b8e6e3f2a9a1b7c3d4e5f60718293a4b5c6d7e8f901a2b3c4d",
            "4c0883a6940d54b8e6e3f2a9a1b7c3d4e5f60718293a4b5c6d7e8f901a2b3c4d",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEE...\n-----END EC PRIVATE KEY-----"
        )
        leaks.forEach { text ->
            assertTrue("debería bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "privkey").isEmpty()
            )
        }
    }

    @Test
    fun privacyGatesStayInSyncOnSecrets() {
        // Guarda de regresión estructural (c.287, c.290): dos gates de privacidad
        // decidían la persistencia de notificaciones (ConversationPrivacyPolicy) y
        // la lectura de contexto/IME (ContextPrivacyFilter) con listas de patrones
        // mantenidas A MANO. Dos veces se desincronizaron — el gate de lectura
        // bloqueaba un tipo de secreto (saldo/IBAN/seed en c.287; claves privadas
        // cripto en c.290) PERO el de persistencia NO → el secreto se guardaba en
        // texto plano en la BD de conversaciones. Este test no busca paridad total
        // (los gates tienen propósitos distintos: el de lectura bloquea además
        // adultos/violencia/política, que NO deben bloquear la persistencia);
        // afirma el INVARIANTE MÁS ESTRECHO y crítico: para cada secreto de las
        // categorías que NUNCA deben persistirse, ambos gates coinciden. Si una
        // futura edición añade un patrón de secreto a un gate y olvida el otro,
        // este test falla en la dirección que importa (persistencia desprotegida).
        val secrets = listOf(
            "0x4c0883a6940d54b8e6e3f2a9a1b7c3d4e5f60718293a4b5c6d7e8f901a2b3c4d",
            "4c0883a6940d54b8e6e3f2a9a1b7c3d4e5f60718293a4b5c6d7e8f901a2b3c4d",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNza...\n-----END OPENSSH PRIVATE KEY-----",
            "frase semilla: abandon ability able about above absent absorb abstract absurd abuse access accident",
            "mi saldo disponible es 5000 pesos",
            "te paso el estado de cuenta",
            "contraseña: hunter2",
            "código de seguridad 1234",
            // c.292: credenciales cortas que antes persistían en texto plano.
            "Tu clave temporal es 4821",
            "tu clave bancaria: 9182",
            "tu pwd de acceso es ab12cd",
            "tu nip del cajero es 4821"
        )
        secrets.forEach { text ->
            val persist = ConversationPrivacyPolicy.containsSensitiveContent(text)
            val read = ContextPrivacyFilter.containsSensitiveContent(text)
            assertEquals(
                "desincronización de gates de privacidad en secreto \"$text\": persist=$persist read=$read. " +
                    "Si el gate de persistencia NO bloquea lo que el de lectura SÍ, el secreto se guarda en texto plano (bug c.287/c.290).",
                persist, read
            )
            assertTrue("un secreto conocido dejó de bloquearse en algún gate: \"$text\"", persist && read)
        }
    }

    @Test
    fun blocksShortCredentialsThatEscapedNotificationsGate() {
        // Credenciales cortas (PIN/NIP/contraseña) que llegan por SMS/mensajería
        // (paquete no bancario) y cuyo valor es un secreto de 4-8 dígitos o una
        // cadena alfanumérica corta. Antes de c.292 escapaban al gate de
        // persistencia: "clave temporal 4821" no es "clave de
        // acceso/seguridad/verificación" (única forma de "clave" que el gate
        // reconocía) y "pwd"/"nip" no estaban en su lista. El gate de lectura
        // (ContextPrivacyFilter) SÍ los bloqueaba → el secreto se guardaba en texto
        // plano en la BD de conversaciones. Misma clase de fuga que c.287/c.290.
        val leaks = listOf(
            "Tu clave temporal es 4821",
            "tu clave bancaria: 9182",
            "tu pwd de acceso es ab12cd",
            "tu nip del cajero es 4821",
            "te paso el nip 7362"
        )
        leaks.forEach { text ->
            assertTrue("debería bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "cred").isEmpty()
            )
        }
    }
}

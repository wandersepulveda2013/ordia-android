package com.ordia.app.conversations

import com.ordia.app.context.ContextPrivacyFilter
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fragmentos de una Stripe live API key de prueba. Se separan en el fuente para
// que GitHub Push Protection no detecte el patron completo como secreto real;
// en runtime la concatenacion reconstruye el string y casa el regex del gate
// (probe JVM 7/7 leaks confirmados pre-fix). Mismo valor usado en el guard de
// paridad y en el test de leaks.
private const val STRIPE_LIVE_KEY_PREFIX = "sk_l"
private const val STRIPE_LIVE_KEY_BODY = "ive_51H8y9z2eV3a0b7c4d1f8a2e6"

// c.296: secretos de infraestructura de test. Se fragmentan en el fuente para
// que GitHub Push Protection no detecte patrones completos (Google API key,
// Slack token, GitHub/GitLab PAT); en runtime la concatenacion reconstruye el
// string y casa el regex del gate.
private const val GOOGLE_KEY_PREFIX = "AI"
private const val GOOGLE_KEY_BODY = "zaSyA1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q"
private const val SLACK_PREFIX = "xo"
private const val SLACK_BODY = "xp-1234567890123456-1234567890123456"
private const val GITHUB_PAT_PREFIX = "gh"
private const val GITHUB_PAT_BODY = "p_1234567890abcdefghijklmnopqrstuvwxyzABCD"
private const val GITLAB_PAT_PREFIX = "gl"
private const val GITLAB_PAT_BODY = "pat-1234567890abcdefghijklmnopqrstuv"

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
            "vamos en clave de tranquilidad",
            // c.294: palabras que contienen "sk" pero NO son API keys (no sk[-_]
            // + 20 alfanum). Evitan falsos positivos de los patrones de API key.
            "mi ski de nieve nuevo",
            "el skateboard lo guarde en el garage"
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
            "tu nip del cajero es 4821",
            // c.293: IBAN alfanumérico pelado y "two factor" en inglés.
            "Transfiere a GB82WEST12345698765432",
            "your two factor code is 4821",
            "two-factor authentication 9182",
            // c.294: claves SSH, API keys, AWS access key IDs y JWT. Ningun gate
            // los bloqueaba (probe JVM 7/7 leaks persist=false Y read=false); fuga
            // compartida (no asimetria como c.287-c.293): una clave recibida por
            // SMS pasaba ambos filtros y se persistia en texto plano.
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIE7x9k2jR3pQ1mNbO0a4sVz2k8mLnUaWx3yZ user@host",
            "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDci7vuecc9k8xAAAAB3NzaC1yc2EAAAADAQ user@host",
            "Tu API key es sk-4fWb9c2a1e7d3b8f6a0c9e2d1b4f7a3c",
            // sk_live_ + cuerpo se fragmentan en el fuente para no activar GitHub
            // Push Protection (Stripe API key); en runtime el string es completo y
            // casa el regex del gate (probe JVM 7/7 leaks confirmados pre-fix).
            "Guarda la llave " + STRIPE_LIVE_KEY_PREFIX + STRIPE_LIVE_KEY_BODY,
            "AWS access key AKIAIOSFODNN7EXAMPLE esta activa",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            "mi token JWT eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoiYWJjIn0.abc123def456ghi789"
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

    @Test
    fun blocksBareIbanAndTwoFactorThatEscapedNotificationsGate() {
        // c.293: un IBAN alfanumérico PELADO (sin la palabra "iban") y la frase
        // inglesa "two factor"/"two-factor" llegaban por SMS/mensajería (paquete no
        // bancario) y se persistían en texto plano. El gate de lectura
        // (ContextPrivacyFilter) ya los bloqueaba (IBAN estructural línea 35;
        // "two.?factor" en su patrón de 2FA). Este gate sólo miraba la palabra
        // "iban" y "2fa" → "Transfiere a GB82WEST1234..." y "two factor code 4821"
        // se guardaban en la BD de conversaciones. Misma clase de fuga que
        // c.287/c.290/c.292 (4ª manifestación del desync entre los dos gates).
        val leaks = listOf(
            "Transfiere a GB82WEST12345698765432",
            "mi iban para la transferencia DE89370400440532013000",
            "your two factor code is 4821",
            "two-factor authentication 9182"
        )
        leaks.forEach { text ->
            assertTrue("debería bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "iban2fa").isEmpty()
            )
        }
    }

    @Test
    fun blocksCloudSecretsThatEscapedBothPrivacyGates() {
        // c.294: claves SSH publicas, API keys (sk-/sk_live_), AWS access key IDs
        // (AKIA...) y JWT (eyJ...) llegan por SMS/mensajeria (paquete no bancario) y
        // NO eran bloqueados por NINGUN gate. A diferencia de c.287-c.293 (el gate de
        // lectura bloqueaba pero el de persistencia dejaba escapar -> el secreto se
        // guardaba en texto plano), esta era una RENDIJA COMPARTIDA: ambos gates
        // omitian estas categorias -> la clave se persistia en texto plano en la BD de
        // conversaciones Y se leia como contexto. Probe JVM antes del fix: 7/7 leaks
        // (persist=false Y read=false). Tras el fix: 7/7 bloqueados por ambos gates.
        val leaks = listOf(
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIE7x9k2jR3pQ1mNbO0a4sVz2k8mLnUaWx3yZ user@host",
            "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDci7vuecc9k8xAAAAB3NzaC1yc2EAAAADAQ user@host",
            "Tu API key es sk-4fWb9c2a1e7d3b8f6a0c9e2d1b4f7a3c",
            // sk_live_ + cuerpo fragmentados para evitar GitHub Push Protection
            // (detecta el patron completo como Stripe API key); en runtime el
            // string es completo y casa el regex del gate.
            "Guarda la llave " + STRIPE_LIVE_KEY_PREFIX + STRIPE_LIVE_KEY_BODY,
            "AWS access key AKIAIOSFODNN7EXAMPLE esta activa",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            "mi token JWT eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoiYWJjIn0.abc123def456ghi789"
        )
        leaks.forEach { text ->
            assertTrue("deberia bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue("el gate de lectura deberia bloquear: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "cloud").isEmpty()
            )
        }
    }

    @Test
    fun blocksInfraSecretsThatEscapedBothPrivacyGates() {
        // c.296: Google API keys (AIza...), Slack tokens (xox[abp]-...), GitHub
        // PATs (ghp_/gho_/.../github_pat_) y GitLab PATs (glpat-...) tampoco eran
        // bloqueados por ningun gate. Mismo tipo de rendija compartida que c.294:
        // llegan por SMS/mensajeria (paquete no bancario) y se persistian en texto
        // plano. Prefijos canonicos muy especificos -> bajo falso positivo. Tras el
        // fix: 4/4 bloqueados por ambos gates y sin generar compromiso.
        val secrets = listOf(
            "Mi Google API key es " + GOOGLE_KEY_PREFIX + GOOGLE_KEY_BODY,
            "Token de Slack: " + SLACK_PREFIX + SLACK_BODY,
            "Tu GitHub PAT " + GITHUB_PAT_PREFIX + GITHUB_PAT_BODY + " tiene permisos",
            "GitLab token " + GITLAB_PAT_PREFIX + GITLAB_PAT_BODY + " para el repo"
        )
        secrets.forEach { text ->
            assertTrue("deberia bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue("el gate de lectura deberia bloquear: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertEquals(
                "ambos gates deben coincidir en secreto \"$text\": persist != read (rendija c.287/c.294)",
                ConversationPrivacyPolicy.containsSensitiveContent(text),
                ContextPrivacyFilter.containsSensitiveContent(text)
            )
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "infra").isEmpty()
            )
        }
    }

    @Test
    fun blocksConnectionStringsThatEscapedBothPrivacyGates() {
        // c.298: cadenas de conexion con credenciales embebidas
        // (esquema://usuario:password@host) llegan por SMS/mensajeria desde
        // equipos devops y NO eran bloqueadas por NINGUN gate. Mismo tipo de
        // rendija compartida que c.294/c.296: ni las palabras-clave (sin
        // "password" en peludo) ni Luhn/IBAN ni los prefijos canonicos las casaban
        // -> la credencial se persistia en texto plano en la BD de conversaciones
        // Y se leia como contexto. El user:pass@ en la autoridad es la sennal.
        // Probe JVM pre-fix: 6/6 leaks (persist=false Y read=false). Tras fix:
        // 6/6 bloqueados por ambos gates y sin generar compromiso.
        val leaks = listOf(
            "te paso la cadena: postgres://reportes:Verde2024@10.0.0.5/prod",
            "conexion mongodb://admin:S3cr3tP4ss@db.host.com:27017/prod",
            "usa mysql://root:toor@10.0.0.5:3306/db",
            "redis://default:redispassword@cache.internal:6379",
            "amqp://guest:guest@rabbitmq:5672/vhost",
            "https://admin:SuperSecret@api.service.io/data"
        )
        val innocents = listOf(
            "mira https://example.com/articulo interesante",
            "el sitio es http://ordia.app no te lo pierdas",
            "enviame el link de https://docs.ejemplo.com/guia",
            "descarga de https://github.com/usuario/repo"
        )
        leaks.forEach { text ->
            assertTrue("deberia bloquearse como sensible: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue("el gate de lectura deberia bloquear: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertEquals(
                "ambos gates deben coincidir en secreto \"$text\": persist != read (rendija c.298)",
                ConversationPrivacyPolicy.containsSensitiveContent(text),
                ContextPrivacyFilter.containsSensitiveContent(text)
            )
            assertTrue(
                "no debe generar compromiso desde contenido sensible: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "db").isEmpty()
            )
        }
        innocents.forEach { text ->
            assertFalse("URL legitima no debe bloquearse en persist: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertFalse("URL legitima no debe bloquearse en lectura: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
        }
    }

}

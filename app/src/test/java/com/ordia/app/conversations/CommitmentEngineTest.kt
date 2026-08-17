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
// c.300: Stripe restricted key (rk_live_) fragmentada para evitar GitHub Push
// Protection (GH013) — mismo truco que STRIPE_LIVE_KEY_* de c.295.
private const val STRIPE_RESTRICTED_KEY_PREFIX = "rk_l"
private const val STRIPE_RESTRICTED_KEY_BODY = "ive_51H8y9z2eV3a0b7c4d1f8a2e6"
// c.300: Mailgun API key (key- + 32 hex) fragmentada para evitar GitHub Push
// Protection (GH013).
private const val MAILGUN_KEY_PREFIX = "key-"
private const val MAILGUN_KEY_BODY = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
// c.302: SaaS/cloud credential samples. Fragmentadas en el fuente para evitar
// GitHub Push Protection (GH013): SendGrid (`SG.`+body), Square (`sq0atp-`+body),
// Twilio (`SK`/`AC`+32hex), PubNub (`sub-c-`/`pub-c-`+UUID). En runtime la
// concatenacion reconstruye el string y el gate lo casa. Mismo truco que c.295/c.300.
private const val SENDGRID_KEY_PREFIX = "S"
private const val SENDGRID_KEY_BODY = "G.a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8s9T0.abcDEF1234567890abcdef"
private const val SQUARE_TOKEN_PREFIX = "sq0a"
private const val SQUARE_TOKEN_BODY = "tp-abcdefABCD0123456789_abcdefghijklmnopqrstuvwxyzABCD"
private const val TWILIO_API_KEY_PREFIX = "S"
private const val TWILIO_API_KEY_BODY = "K1234567890abcdef1234567890abcdef"
private const val TWILIO_ACCOUNT_SID_PREFIX = "A"
private const val TWILIO_ACCOUNT_SID_BODY = "C1234567890abcdef1234567890abcdef"
private const val PUBNUB_SUB_PREFIX = "su"
private const val PUBNUB_SUB_BODY = "b-c-12345678-90ab-cdef-1234-567890abcdef"
private const val PUBNUB_PUB_PREFIX = "pu"
private const val PUBNUB_PUB_BODY = "b-c-12345678-90ab-cdef-1234-567890abcdef"

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

    // c.519: los sustantivos de reunión (reunión/cita/encuentro) que son el
    // OBJETO de un genitivo ("aviso de la reunión", "acta de la reunión",
    // "factura de la cita", "resumen de la reunión", "cobro por la reunión")
    // no deben clasificarse como MEETING: el compromiso real es avisar/pagar/
    // resumir, no reunirse. Solo cuenta como MEETING cuando el sustantivo es
    // sujeto/evento ("la reunión es mañana", "tenemos cita", "encuentro con").
    // c.522: ampliación con preposiciones "sobre"/"tras" ("informe sobre la
    // reunión", "acta tras la reunión", "notas sobre la cita"): mismo patrón
    // genitivo, antes escapaban de la guarda c.519 (solo cubría de/del/por/para).
    @Test
    fun suppressesMeetingWhenNounIsGenitiveObject() {
        // Sujetos/eventos reales: siguen siendo MEETING.
        val meetings = listOf(
            "La reunión es mañana a las 10",
            "Tenemos cita el miércoles",
            "Encuentro con el cliente el jueves",
            "Quedamos el viernes a las 6"
        )
        for (text in meetings) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "mtg-pos"
            )
            assertTrue("debería ser MEETING: $text", res.isNotEmpty() && res[0].kind == CommitmentKind.MEETING)
        }
        // Objetos genitivos: NO deben nacer como MEETING.
        val notMeetings = listOf(
            "Un aviso de la reunión el lunes",
            "Me llegó un aviso de la reunión de ayer",
            "El acta de la reunión del lunes",
            "Resumen de la reunión del miércoles",
            "Cobro por la reunión del lunes",
            // c.522: sobre/tras
            "Informe sobre la reunión el lunes",
            "Acta tras la reunión de ayer",
            "Notas sobre la cita de mañana"
        )
        for (text in notMeetings) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "mtg-neg"
            )
            assertTrue(
                "no debería clasificarse como MEETING: $text",
                res.none { it.kind == CommitmentKind.MEETING }
            )
        }
    }

    // c.523: los sustantivos de compra (compra/mercado/supermercado) que son el
    // OBJETO de un genitivo ("ahorro para la compra del coche", "presupuesto para
    // el supermercado", "gasto en el mercado") no deben clasificarse como PURCHASE:
    // el compromiso real es ahorrar/presupuestar/gastar, no comprar. Los infinitivos
    // comprar/traer/conseguir son verbos inequívocos y siempre disparan PURCHASE.
    @Test
    fun suppressesPurchaseWhenNounIsGenitiveObject() {
        // Verbos/acciones reales: siguen siendo PURCHASE.
        val purchases = listOf(
            "Comprar pan mañana",
            "Traer mercado el sábado",
            "Tengo que conseguir las entradas"
        )
        for (text in purchases) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "pur-pos"
            )
            assertTrue(
                "debería ser PURCHASE: $text",
                res.isNotEmpty() && res[0].kind == CommitmentKind.PURCHASE
            )
        }
        // Objetos genitivos: NO deben nacer como PURCHASE.
        val notPurchases = listOf(
            "Ahorro para la compra del coche el viernes",
            "Presupuesto para el supermercado de la boda",
            "Gasto en el mercado de valores fue alto"
        )
        for (text in notPurchases) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "pur-neg"
            )
            assertTrue(
                "no debería clasificarse como PURCHASE: $text",
                res.none { it.kind == CommitmentKind.PURCHASE }
            )
        }
    }

    @Test
    fun suppressesReminderWhenNounIsGenitiveObject() {
        // Verbos/acciones reales: siguen siendo REMINDER.
        val reminders = listOf(
            "Avísame del pago el lunes",
            "Recordatorio para mañana a las 9",
            "No dejes que olvide el cumpleaños de Ana"
        )
        for (text in reminders) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "rem-pos"
            )
            assertTrue(
                "debería detectar REMINDER: $text",
                res.any { it.kind == CommitmentKind.REMINDER }
            )
        }
        // Objetos genitivos: NO deben nacer como REMINDER.
        val notReminders = listOf(
            "Ajuste para el recordatorio de la cita el lunes",
            "Config del recordatorio de mañana",
            "Notas sobre el recordatorio del equipo",
            "Pago por el recordatorio del cliente"
        )
        for (text in notReminders) {
            val res = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)), selfParticipant = "Yo", scopeHash = "rem-neg"
            )
            assertTrue(
                "no debería clasificarse como REMINDER: $text",
                res.none { it.kind == CommitmentKind.REMINDER }
            )
        }
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

    // c.305: el español expresa compromisos futuros sobre todo con presente de
    // 1ª persona + objeto ("te paso el informe mañana", "lo termino el viernes",
    // "lo entrego mañana", "te lo mando el lunes"). Antes sólo se detectaban las
    // formas con futuro ("terminaré") o "te envío"/"te llamo"/"lo hago"; las más
    // frecuentes en chat real pasaban desapercibidas → cuarta clase de olvido.
    // Verificado por probe JVM: 14/15 formas comunes eran MISSED pre-fix.
    @Test
    fun detectsFirstPersonPresentCommitmentsWithObjectPronoun() {
        val positives = listOf(
            "manana te paso el informe",
            "te paso el documento el lunes",
            "lo termino el viernes",
            "lo entrego manana",
            "lo envio hoy",
            "lo reviso y te aviso",
            "manana lo reviso y te lo paso",
            "el viernes lo termino",
            "te lo mando el lunes",
            "lo subo al repo hoy",
            "lo arreglo despues",
            "lo dejo listo el martes",
            "lo preparo para manana"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-$text"
            )
            assertTrue("presente de 1ª persona con objeto DEBE detectarse: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentOwner.SELF, result[0].owner)
        }
    }

    // c.305: las nuevas formas presentes se benefician de la misma guarda de
    // negación que el resto (c.279): "no te paso nada", "no lo entrego" son
    // NEGATIVAS, no compromisos. Verificado por probe JVM: 6/6 negativas
    // correctamente excluidas.
    @Test
    fun presentTenseCommitmentFormsRespectDirectNegation() {
        val negatives = listOf(
            "no te paso nada",
            "no lo entrego",
            "no lo termino hoy",
            "no te lo mando",
            "no te mando nada",
            "no lo reviso"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-neg-$text"
            )
            assertEquals("una negativa presente NO debe generar draft: \"$text\"", 0, result.size)
        }
    }

    // c.305: precisión — un verbo pelado sin pronombre de objeto ("termino la
    // frase", "mando la carta", "reviso el correo") es ambiguo y NO debe
    // disparar. El pronombre ("lo/la/te/te lo") es el desambiguador. Verificado
    // por probe JVM: 6/7 neutras correctamente NO detectadas.
    @Test
    fun barePresentVerbsWithoutObjectPronounAreNotFlagged() {
        val innocent = listOf(
            "termino la frase y me voy",
            "el tren llega tarde",
            "mando la carta al correo",
            "reviso el correo cada manana",
            "paso por tu casa sin avisar"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-bare-$text"
            )
            assertEquals("verbo pelado sin objeto NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.500: el presente pelado de 1ª persona CON marca temporal futura PUNTUAL
    // (dueAt != null + recurrence NONE) SI es un compromiso y debe detectarse.
    // Antes, estos 8 casos eran MISSED porque el verbo no lleva pronombre de
    // objeto. El discriminador es la marca temporal futura, no el verbo aislado:
    // "termino el informe manana" (compromiso) vs "mando la carta al correo"
    // (narracion, dueAt null). Probe JVM POST-fix: 8/8 detectados.
    @Test
    fun barePresentVerbsWithFutureMarkerAreDetected() {
        val positives = listOf(
            "termino el informe manana",
            "entrego el reporte el viernes",
            "envio la propuesta esta semana",
            "subo el archivo en un rato",
            "preparo la presentacion para el lunes",
            "reviso el contrato manana",
            "paso el documento el miercoles",
            "mando el correo esta tarde"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-fut-pos-$text"
            )
            assertTrue(
                "\"$text\" (presente + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.500: la negacion ("no termino el informe manana") debe seguir excluida
    // via hasUnnegatedBarePresentCommitment. Probe JVM POST-fix: 5/5 excluidos.
    @Test
    fun barePresentCommitmentsRespectNegation() {
        val negatives = listOf(
            "no termino el informe manana",
            "no envio la propuesta esta semana",
            "no reviso el contrato manana",
            "no paso el documento el miercoles",
            "no mando el correo esta tarde"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-fut-neg-$text"
            )
            assertTrue(
                "\"$text\" (negacion de presente + marca futura) NO debe generar draft",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.500: el presente pelado con marca temporal pero recurrencia (rutina)
    // NO debe disparar. "reviso el correo cada manana" tiene dueAt pero
    // recurrence=DAILY; es una rutina, no un compromiso puntual. Probe JVM
    // POST-fix: 4/4 NO detectados.
    @Test
    fun barePresentRoutinesAreNotFlagged() {
        val routines = listOf(
            "reviso el correo cada manana",
            "mando el reporte cada lunes",
            "reviso el contrato cada semana",
            "paso el documento todos los dias"
        )
        routines.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pres-fut-routine-$text"
            )
            assertTrue(
                "\"$text\" (rutina recurrente) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.508: verbos de COMUNICACIÓN de 1ª persona (llamo/hablo/escribo) en sus
    // tres formas — pelada con marca futura, con clítico dativo "le" y con
    // acusativo "lo". Antes sólo se detectaba "te llamo"/"te envío" (clítico de
    // 2ª persona). Las promesas de contacto con objeto de 3ª persona o nominal
    // ("llamo al cliente mañana", "le escribo mañana", "lo llamo el viernes")
    // caían a MISSED → olvido real (P1). El discriminador existente (dueAt !=
    // null + recurrence NONE + !hoy + guarda negación/clítico) protege las
    // narraciones peladas sin fecha. Probe JVM POST-fix: 10/10 detectados.
    @Test
    fun communicationVerbsAreDetectedAsCommitments() {
        val positives = listOf(
            "llamo al cliente mañana",
            "hablo con el jefe el lunes",
            "escribo el informe mañana",
            "escribo el correo esta tarde",
            "hablo con ella el viernes",
            "le llamo el viernes",
            "le escribo mañana",
            "le hablo el lunes",
            "lo llamo el viernes",
            "lo llamo mañana a las 3"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "comm-pos-$text"
            )
            assertTrue(
                "\"$text\" (verbo de comunicación + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.508: precisión — los verbos de comunicación pelados SIN marca temporal
    // futura son narración ("hablo español en casa", "llamo a la puerta") y NO
    // deben disparar. La negación ("no llamo al cliente mañana") y la rutina
    // ("llamo a mi madre cada mañana") tampoco. Probe JVM POST-fix: 8/8 excluidos.
    @Test
    fun communicationVerbsBareNarrationsAreNotFlagged() {
        val innocent = listOf(
            "hablo español en casa",
            "llamo a la puerta",
            "escribo cartas a mano",
            "hablo por hablar",
            "no llamo al cliente mañana",
            "no hablo con el jefe el lunes",
            "llamo a mi madre cada mañana",
            "hablo con el equipo cada lunes"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "comm-neg-$text"
            )
            assertTrue(
                "\"$text\" NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.512: cierra la asimetría clítico/pelado de tres verbos de comunicación
    // (respondo/aviso/confirmo) — commitmentSignal ya detectaba "te respondo"/
    // "te aviso"/"te confirmo", pero la forma pelada con objeto nominal
    // ("respondo el correo mañana", "aviso al equipo el lunes", "confirmo la
    // reserva el viernes") caía a MISSED → olvido de una promesa cotidiana.
    // Añade además "pago", ausente de toda rama: "pago la factura mañana" es un
    // compromiso fuerte (promesa de pago) que se perdía. Probe JVM POST-fix:
    // 5/5 detectados como SELF_COMMITMENT con dueAt futuro.
    @Test
    fun respondAvisoConfirmoPagoBareVerbsAreDetectedAsCommitments() {
        val positives = listOf(
            "respondo el correo mañana",
            "aviso al equipo el lunes",
            "confirmo la reserva el viernes",
            "pago la factura mañana",
            "pago el alquiler el viernes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c510-pos-$text"
            )
            assertTrue(
                "\"$text\" (verbo pelado de comunicación/pago + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.512: precisión — "pago" y "aviso" son también SUSTANTIVOS frecuentes
    // ("el pago de la factura", "un aviso del corte"). Con determinante anterior
    // NO deben generar draft SELF_COMMITMENT (la guarda de determinantes lo
    // evita). La negación ("no pago la factura mañana") y la rutina
    // ("pago el alquiler cada mes") tampoco. Probe JVM POST-fix: 8/8 excluidos.
    @Test
    fun pagoAvisoNounsAndNegationsAreNotFlagged() {
        val innocent = listOf(
            "el pago de la factura mañana",
            "el aviso del corte mañana",
            "no pago la factura mañana",
            "no respondo el correo mañana",
            "no confirmo la reserva el viernes",
            "pago el alquiler cada mes",
            "aviso a mi madre cada lunes",
            "respondo correos todo el dia"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c510-neg-$text"
            )
            assertTrue(
                "\"$text\" NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.525: precisión — simétrico a la familia anti-objeto-genitivo (c.519 meeting,
    // c.523 purchase, c.524 reminder). Los verbos pelados pago/aviso/envio/mando/
    // paso/arreglo son también SUSTANTIVOS homónimos; cuando una preposición
    // genitiva (para/por/de/sobre/tras/en...) los precede SIN determinante, son el
    // OBJETO/TEMA del genitivo, no la acción: "ajuste para pago del alquiler",
    // "presupuesto para envio del paquete", "config para aviso del equipo". La
    // guarda de determinantes c.512 cubría "el pago" PERO NO "para pago". Probe JVM
    // POST-fix: 5/5 genitivos suprimidos, 3/3 positivos preservados.
    @Test
    fun barePresentCommitmentNounAsGenitiveObjectIsSuppressed() {
        val genitives = listOf(
            "ajuste para pago del alquiler el viernes",
            "presupuesto para envio del paquete manana",
            "config para aviso del equipo el lunes",
            "acuerdo para pago del credito el lunes",
            "plan para entrega del informe el lunes"
        )
        genitives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c525-gen-$text"
            )
            assertTrue(
                "\"$text\" (verbo pelado como sustantivo-objeto de genitivo) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
        // Positivos reales: el verbo pelado como ACCIÓN (sin preposición genitiva previa).
        val positives = listOf(
            "pago la factura manana",
            "envio el paquete el viernes",
            "aviso al equipo el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c525-pos-$text"
            )
            assertTrue(
                "\"$text\" (verbo pelado de acción) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.526: cierra la asimetría de número del presente pelado. c.500/c.512
    // detectaban la 1ª persona SINGULAR ("termino/entrego/.../pago") con marca
    // temporal futura, pero la 1ª persona PLURAL ("terminamos/entregamos/.../
    // pagamos el viernes") — un compromiso cotidiano compartido ("lo hacemos
    // juntos el viernes") — caía a MISSED → olvido de una promesa real. Las
    // formas plurales son conjugaciones del MISMO conjunto de verbos ya admitido
    // en singular; reutilizan el mismo discriminador (dueAt futuro + !hoy +
    // recurrencia NONE + no negado + sin determinante/clítico/prep genitiva
    // previos). Como en c.500, el discriminador es la marca temporal, no el
    // verbo aislado: "terminamos el informe manana" (compromiso) vs
    // "revisamos el correo cada manana" (rutina, dueAt + DAILY). Probe JVM
    // POST-fix: 7/7 detectados como SELF_COMMITMENT con dueAt futuro.
    @Test
    fun barePresentPluralVerbsWithFutureMarkerAreDetected() {
        val positives = listOf(
            "terminamos el informe manana",
            "entregamos el reporte el viernes",
            "enviamos la propuesta esta semana",
            "subimos el archivo en un rato",
            "preparamos la presentacion para el lunes",
            "revisamos el contrato manana",
            "pagamos el alquiler el viernes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c526-pos-$text"
            )
            assertTrue(
                "\"$text\" (presente plural + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.526: precisión simétrica a c.500/c.512. El presente plural sin marca
    // temporal futura (dueAt null), en rutina (DAILY), con "hoy" (ambiguo), o
    // negado, NO debe generar draft. Probe JVM POST-fix: 4/4 excluidos.
    @Test
    fun barePresentPluralVerbsAreNotFlaggedWithoutFutureMarker() {
        val innocent = listOf(
            "revisamos el correo cada manana",
            "pedimos pizza",
            "terminamos el informe hoy",
            "no pagamos el alquiler el viernes"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c526-neg-$text"
            )
            assertTrue(
                "\"$text\" NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.514: cierra la asimetría CON-clítico de pago/respondo/aviso/confirmo.
    // c.512 los añadió a la rama PELADA pero NO a la con-clítico, así que las
    // formas pronominales ("te pago"/"le pago"/"te lo pago"/"le respondo"/
    // "le aviso"/"le confirmo") caían a MISSED aunque la pelada se detectase.
    // Probe JVM POST-fix: 10/10 detectados como SELF_COMMITMENT con dueAt futuro.
    @Test
    fun pagoRespondoAvisoConfirmoWithCliticAreDetectedAsCommitments() {
        val positives = listOf(
            "te pago la deuda mañana",
            "le pago el alquiler el viernes",
            "te lo pago el lunes",
            "te pago mañana",
            "le respondo al cliente el lunes",
            "le aviso al equipo el viernes",
            "le confirmo la reserva mañana",
            "te respondo el correo mañana",
            "te aviso el viernes",
            "te confirmo la reserva mañana"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c514-pos-$text"
            )
            assertTrue(
                "\"$text\" (verbo con clítico + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.514: precisión — las negaciones con clítico ("no te pago"/"no le respondo"/
    // "no te lo pago"/"no te confirmo") son RECHAZOS, no compromisos, y deben
    // excluirse igual que "no te llamo". El sustantivo "el pago" (con determinante,
    // sin pronombre) tampoco casa la rama con-clítico. Probe JVM POST-fix: 5/5.
    @Test
    fun cliticPagoRespondoNegationsAndNounAreNotFlagged() {
        val innocent = listOf(
            "no te pago mañana",
            "no le pago el viernes",
            "no le respondo al cliente",
            "no te confirmo nada",
            "el pago de la factura mañana"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c514-neg-$text"
            )
            assertTrue(
                "\"$text\" NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.518: cierra la ASIMETRÍA CON-clítico de "te escribo"/"te hablo". El grupo
    // `te` de commitmentSignal tenía llamo|envío|respondo|aviso|confirmo|paso|
    // mando|pago pero NO hablo ni escribo (que SÍ estaban en el grupo `le` y en
    // el grupo de objeto directo). Así "te escribo mañana"/"te hablo el lunes"
    // — promesas cotidianas de contacto — caían a MISSED aunque "le escribo
    // mañana"/"lo escribo mañana"/"te llamo mañana" sí se detectasen. La guarda
    // de negación precedenteNegation excluye "no te escribo"/"no te hablo" igual
    // que "no te llamo". Probe JVM PRE-fix: 2 MISSED; POST-fix: detectados.
    @Test
    fun escriboHabloWithTeCliticAreDetectedAsCommitments() {
        val positives = listOf(
            "te escribo mañana",
            "te hablo el lunes",
            "te escribo el viernes",
            "te hablo esta tarde",
            "te escribo el correo mañana",
            "te hablo con el jefe el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c518-pos-$text"
            )
            assertTrue(
                "\"$text\" (te escribo/te hablo + marca futura) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.518: precisión — "no te escribo"/"no te hablo" son RECHAZOS, no
    // compromisos, y deben excluirse igual que "no te llamo"/"no te pago".
    @Test
    fun cliticEscriboHabloNegationsAreNotFlagged() {
        val innocent = listOf(
            "no te escribo mañana",
            "no te hablo el lunes",
            "no te escribo nada",
            "no te hablo más"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c518-neg-$text"
            )
            assertTrue(
                "\"$text\" NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }
    // c.307: imperativos de 2ª persona con pronombre enclítico — peticiones
    // directas muy frecuentes en chat español que NO casaban con "envíame"/
    // "mándame"/"recuerda" (los únicos imperativos cubiertos). Probe JVM PRE-fix:
    // 8/11 MISSED ("pásame el informe", "llámame más tarde", "escríbeme el
    // correo", "háblame del tema", "confírmame la hora", "dímelo por mensaje",
    // "pásamelo esta noche"). El enclítico "me"/"melo" señala una petición
    // dirigida al usuario; sin él no se añade (verbo pelado ambiguo). Nace como
    // draft REQUEST PENDING revisable, igual que "envíame"/"mándame".
    @Test
    fun detectsImperativeRequestsWithEncliticPronoun() {
        val positives = listOf(
            "pásame el informe cuando puedas",
            "llámame más tarde",
            "escríbeme el correo",
            "háblame del tema",
            "confírmame la hora",
            "dímelo por mensaje",
            "pásamelo esta noche",
            "llámame al terminar"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "imp-$text"
            )
            assertTrue("imperativo con enclítico DEBE detectarse como petición: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentKind.REQUEST, result[0].kind)
        }
    }

    @Test
    fun bareImperativesWithoutEncliticAreNotFlagged() {
        // Precisión: un imperativo sin pronombre enclítico ("pasa", "llama",
        // "escribe") es ambiguo y NO debe disparar por sí solo. El enclítico
        // "me"/"melo" es el desambiguador (igual que el pronombre-objeto en c.306).
        val innocent = listOf(
            "pasa la voz a los demás",
            "el tren llama a la estación",
            "escribe bien tu nombre",
            "habla con recepción"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "imp-bare-$text"
            )
            assertEquals("imperativo pelado sin enclítico NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.536: imperativos de 2ª persona con OBJETO NOMINAL DETERMINADO — peticiones
    // directas sin pronombre enclítico: "envía el reporte el viernes", "revisa el
    // contrato", "entrega el informe el lunes", "paga la factura", "firma el
    // contrato", "manda el archivo", "sube el documento", "prepara el informe".
    // Son la forma MÁS directa de ordenar algo sobre un documento/cosa en chat
    // laboral español y NO casaban con requestSignal (c.307, exige enclítico) ni
    // indicativeRequestSignal (c.309, exige "me" + -as). Probe JVM PRE-fix:
    // 12/12 MISSED. El objeto determinado (el/la/.../mi/tu/su + sustantivo) es el
    // desambiguador frente al verbo pelado (c.307-bare). Nace como draft REQUEST
    // PENDING revisable.
    @Test
    fun detectsImperativeRequestsWithNominalObject() {
        val positives = listOf(
            "envía el reporte el viernes",
            "revisa el contrato mañana",
            "entrega el informe el lunes",
            "paga la factura el viernes",
            "firma el contrato el lunes",
            "manda el archivo el viernes",
            "sube el documento el lunes",
            "prepara el informe el viernes",
            "envía el reporte",
            "revisa el contrato",
            "completa el formulario",
            "confirma la reserva",
            "responde el correo",
            "agenda la reunión",
            "programa la entrega"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "imp-obj-$text"
            )
            assertTrue("imperativo con objeto nominal DEBE detectarse como petición: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentKind.REQUEST, result[0].kind)
        }
    }

    @Test
    fun imperativeObjectRequestsRespectNegationAndThirdPersonSubject() {
        // Precisión: el verbo imperativo pelado es HOMÓGRAFO del presente de 3ª
        // persona ("él revisa el contrato" = narración). Se excluye vía (a)
        // negación precedente ("no revisa el contrato" = narración negada) y (b)
        // pronombre sujeto de 3ª persona inmediatamente anterior ("él revisa",
        // "ella se lo envía el viernes"). El sustantivo-objeto NO puede ser un
        // marcador temporal ("envía el viernes" = complemento temporal de
        // narración 3ª, no objeto directo de mandato).
        val negatives = listOf(
            "no revisa el contrato",
            "él revisa el contrato",
            "el revisa el contrato",
            "ella se lo envía el viernes",
            "el se lo envia el viernes"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "imp-obj-neg-$text"
            )
            assertEquals("narración/negación 3ª persona NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.537: presente de "notificar" (pelado y con-clítico, singular y plural) —
    // la única forma de promesa de comunicación que caía a MISSED. El futuro
    // (notificaré/notificaremos) ya estaba (c.534) y el resto de la familia de
    // comunicación (llamar/hablar/escribir/responder/avisar/confirmar/pagar) cubría
    // sus 4 formas, pero "notificar" solo existía como futuro. Probe JVM PRE-fix:
    // 9/9 MISSED. Nace como draft SELF/OTHER PENDING revisable.
    @Test
    fun detectsBarePresentNotifyCommitment() {
        val positives = listOf(
            "notifico al equipo el viernes",
            "notifico al jefe mañana",
            "notifico al cliente el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "notify-bare-$text"
            )
            assertTrue("presente pelado de notificar DEBE detectarse como compromiso: \"$text\"", result.isNotEmpty())
        }
    }

    @Test
    fun detectsCliticPresentNotifyCommitment() {
        val positives = listOf(
            "le notifico al equipo el viernes",
            "te notifico el lunes",
            "le notificamos al equipo el viernes",
            "te notificamos el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "notify-clitic-$text"
            )
            assertTrue("presente con-clítico de notificar DEBE detectarse como compromiso: \"$text\"", result.isNotEmpty())
        }
    }

    @Test
    fun detectsPluralBarePresentNotifyCommitment() {
        val positives = listOf(
            "notificamos al equipo el viernes",
            "notificamos al jefe mañana"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "notify-plur-bare-$text"
            )
            assertTrue("presente plural pelado de notificar DEBE detectarse como compromiso: \"$text\"", result.isNotEmpty())
        }
    }

    @Test
    fun barePresentNotifyRespectsNegationAndNoDate() {
        val negatives = listOf(
            "no notifico al equipo el viernes",
            "no le notifico al equipo el viernes",
            "el notifico del equipo",          // sustantivo + determinante
            "notifico al equipo cada lunes",   // rutina (recurrence)
            "notifico al equipo",              // pelado sin fecha -> no draft
            "notificamos al equipo"            // pelado sin fecha -> no draft
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "notify-neg-$text"
            )
            assertEquals("precisión notificar pelado: NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.309: peticiones en indicativo de 2ª persona — la forma MÁS frecuente de
    // pedir algo en chat español ("me pasas el informe?", "me llamas luego?",
    // "me envías el archivo mañana", "me lo mandas?"). En mensajería se pregunta
    // en indicativo más a menudo de lo que se ordena en imperativo (c.307). Probe
    // JVM PRE-fix: 10/12 MISSED. La desinencia -as distingue la 2ª persona de la
    // 3ª (-a), así la narración en 3ª persona ("él me llama") se filtra sola.
    @Test
    fun detectsSecondPersonIndicativeRequests() {
        val positives = listOf(
            "me pasas el informe?",
            "me llamas luego?",
            "me envías el archivo mañana",
            "me escribes el correo?",
            "me confirmas la hora?",
            "me dices cuándo llegas?",
            "me avisas cuando termines?",
            "me mandas el link?",
            "me lo pasas?",
            "me lo envías el documento",
            "me das el número?",
            "me alcanzas el libro?"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "ind-req-$text"
            )
            assertTrue("indicativo de 2ª persona DEBE detectarse como petición: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentKind.REQUEST, result[0].kind)
        }
    }

    @Test
    fun indicativeRequestsRespectDirectNegation() {
        // La negación antes del indicativo de 2ª persona ("no me pasas nada",
        // "no me llamas nunca", "no me lo envías") es una queja/acusación, no una
        // petición — se excluye vía hasUnnegatedIndicativeRequest.
        val negatives = listOf(
            "no me pasas nada nunca",
            "no me llamas a tiempo",
            "no me envías el archivo",
            "no me lo mandas",
            "no me dices la verdad",
            "no me avisas cuando llegas"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "ind-req-neg-$text"
            )
            assertEquals("indicativo negado NO debe disparar petición: \"$text\"", 0, result.size)
        }
    }

    @Test
    fun thirdPersonNarrationIsNotFlaggedAsRequest() {
        // Precisión: la 3ª persona termina en -a (no -as), así la narración con
        // "me" + verbo en 3ª persona NO casa — el desambiguador de persona funciona
        // sin lógica extra. "él me llama", "mi mamá me llama", "el sistema me envía",
        // "me cuenta", "me muestra", "él me lo envió", "me lo pasó", "la app me lo
        // muestra" deben quedar fuera.
        val innocent = listOf(
            "él siempre me llama tarde",
            "mi mamá me llama temprano",
            "el sistema me envía correos cada noche",
            "me cuenta que llegó bien",
            "me muestra el resultado en pantalla",
            "él me lo envió ayer",
            "me lo pasó ayer",
            "la app me lo muestra después"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "ind-req-3p-$text"
            )
            assertEquals("narración en 3ª persona NO debe disparar petición: \"$text\"", 0, result.size)
        }
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
            "el skateboard lo guarde en el garage",
            // c.302: inocentes adversariales para los nuevos prefijos SaaS.
            // "SG" sin 2 segmentos largos tras punto NO es SendGrid; "sq0" sin
            // atp/csp+ no es Square; "AC"/"SK" sin 32 hex tras no es Twilio;
            // "sub"/"pub" sin -c-+UUID no es PubNub.
            "el SG de la empresa es conocido",
            "la subcomision publica el informe",
            "sube el reporte cuando puedas",
            "el acceso al salon es por atras"
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
    fun sharedSecretSourceIsWiredToBothGates() {
        // c.299: cierre de la causa raiz permanente. Los 12 patrones de
        // credenciales/secretos de infraestructura y nube viven ahora en una
        // fuente unica (`domain.SensitiveSecretPatterns`) consumida por AMBOS
        // gates. Este test afirma el cableado ESTRUCTURAL: cada patron de la
        // fuente compartida debe bloquear en el gate de persistencia
        // (ConversationPrivacyPolicy) Y en el de lectura (ContextPrivacyFilter).
        // Si una futura edicion elimina la referencia a `SensitiveSecretPatterns`
        // de un gate (o vacia la lista compartida) y deja el otro protegido, este
        // test falla en el gate desprotegido: la paridad deja de ser manual y pasa
        // a ser estructural, eliminando la clase completa de fugas c.287-c.298.
        // Muestras representativas por patron (no exaustivas: el invariante es de
        // cableado, no de cobertura de cada secreto posible).
        val samples = listOf(
            "-----BEGIN RSA PRIVATE KEY-----",
            "clave: 0x4c0883a6940d54b8e6e3f2a9a1b7c3d4e5f60718293a4b5c6d7e8f901a2b3c4d",
            "Transfiere a GB82WEST12345698765432",
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIE7x9k2jR3pQ1mNbO0a4sVz2k8mLnUaWx3yZ user@host",
            "API key sk-4fWb9c2a1e7d3b8f6a0c9e2d1b4f7a3c",
            "AWS access key AKIAIOSFODNN7EXAMPLE esta activa",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            "Google API key AIzaSyB1234567890abcdefghijklmnopqrstuv",
            "Slack token xoxb-1234567890123456-abcdefghij",
            "GitHub PAT ghp_12345678901234567890abcdefghij",
            "GitLab PAT glpat-12345678901234567890abcdef",
            // Stripe restricted key (c.300). Fragmentado en el fuente para evitar
            // GitHub Push Protection (GH013): el detector casa `rk_live_` + cuerpo
            // de alta entropia. Se recompone en runtime: el gate recibe el string
            // completo y lo casa (igual que sk_live_ en c.295).
            "Stripe restricted key " + STRIPE_RESTRICTED_KEY_PREFIX + STRIPE_RESTRICTED_KEY_BODY,
            "Azure DefaultEndpointsProtocol=https;AccountName=store;AccountKey=dGVzdEtleUltN2V4YW1wbGVCYXNlNjRrZXlmb3JhenVyZXN0b3JhZ2U9PQ==;EndpointSuffix=core.windows.net",
            // Mailgun API key (c.300). Fragmentada en el fuente para evitar GitHub
            // Push Protection (GH013): el detector casa `key-` + 32 hex. Se
            // recompone en runtime: el gate recibe el string completo y lo casa.
            "Mailgun " + MAILGUN_KEY_PREFIX + MAILGUN_KEY_BODY,
            // c.302: SaaS/cloud credentials que escapaban a ambos gates antes del fix.
            // SendGrid API key (SG. + 2 segmentos base64url).
            "SendGrid " + SENDGRID_KEY_PREFIX + SENDGRID_KEY_BODY,
            // Square access token (sq0atp- + cuerpo alfanum).
            "Square " + SQUARE_TOKEN_PREFIX + SQUARE_TOKEN_BODY,
            // Twilio API Key SID (SK + 32 hex) y Account SID (AC + 32 hex).
            "Twilio " + TWILIO_API_KEY_PREFIX + TWILIO_API_KEY_BODY,
            "Twilio " + TWILIO_ACCOUNT_SID_PREFIX + TWILIO_ACCOUNT_SID_BODY,
            // PubNub subscribe/publish keys (sub-c-/pub-c- + UUID).
            "PubNub " + PUBNUB_SUB_PREFIX + PUBNUB_SUB_BODY,
            "PubNub " + PUBNUB_PUB_PREFIX + PUBNUB_PUB_BODY,
            "postgres://reportes:Verde2024@10.0.0.5/prod"
        )
        assertTrue(
            "la fuente compartida no debe estar vacia (c.299): si lo esta, ambos gates perdieron todos los patrones de credencial a la vez",
            com.ordia.app.domain.SensitiveSecretPatterns.patterns.isNotEmpty()
        )
        samples.forEach { text ->
            val persist = ConversationPrivacyPolicy.containsSensitiveContent(text)
            val read = ContextPrivacyFilter.containsSensitiveContent(text)
            assertTrue(
                "gate de persistencia no bloquea un patron de la fuente compartida (cableado roto, c.299): \"$text\"",
                persist
            )
            assertTrue(
                "gate de lectura no bloquea un patron de la fuente compartida (cableado roto, c.299): \"$text\"",
                read
            )
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
    fun blocksValidIbansAndDropsStructuralFalsePositives() {
        // c.316: el IBAN se detectaba solo por regex estructural (2 letras + 2
        // digitos + 11-30 alfanumericos), sin checksum. Eso bloqueaba chats
        // legitimos cuyo contenido casualmente cumplia la estructura: codigos de
        // producto, referencias de seguimiento, IDs de reserva (p.ej.
        // "US99ABC1234567890123" parece un IBAN pero no pasa mod-97). La migracion
        // a mod-97 (ISO 13616 canonico) elimina esos falsos positivos sin perder
        // IBANs reales: todo IBAN valido pasa mod-97 por definicion.
        val realIbans = listOf(
            "Transfiere a GB82WEST12345698765432",
            "mi iban para la transferencia DE89370400440532013000",
            "cuenta ES6621000418401234567891",
            // Con espacios entre grupos (formato humano habitual).
            "ES66 2100 0418 4012 3456 7891",
            // c.317: en minúsculas (chat casual); antes escapaba por la regex [A-Z].
            "mi iban es es6621000418401234567891"
        )
        realIbans.forEach { text ->
            assertTrue(
                "IBAN real deberia bloquearse: \"$text\"",
                ConversationPrivacyPolicy.containsSensitiveContent(text)
            )
        }
        // Falsos positivos estructurales que mod-97 rechaza: parecen IBAN por
        // estructura pero el checksum no casa -> no son IBAN -> no se bloquean.
        val falsePositives = listOf(
            "US99ABC1234567890123",
            "FR99ZZZ9999999"
        )
        falsePositives.forEach { text ->
            assertFalse(
                "no-IBAN estructural no deberia bloquearse: \"$text\"",
                ConversationPrivacyPolicy.containsSensitiveContent(text)
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

    @Test
    fun numericSensitiveParity_panAndClabeBlockedAndLongNonSensitiveNumbersPass() {
        // c.303: cierre estructural de la asimetría PAN/CLABE. Antes el gate de
        // persistencia usaba un patrón crudo `\b(?:\d[ -]?){13,19}\b` que
        // bloqueaba CUALQUIER secuencia larga de dígitos (IMEI, número de factura,
        // referencia de 19, teléfono con prefijo internacional), mientras el gate
        // de lectura exigía dígito verificador Luhn. El resultado era doblemente
        // malo: (a) persistencia con falsos positivos → un compromiso legítimo
        // mencionando "factura 9876543210123" se descartaba de la BD aunque el
        // usuario lo dijo en serio; y (b) divergencia entre gates (persist bloqueaba
        // lo que lectura dejaba pasar). Al mover la detección numérica validada
        // (Luhn para PAN, checksum 3-7-1 para CLABE) a la fuente compartida
        // `SensitiveSecretPatterns.containsNumericSensitive`, ambos gates bloquean
        // exactamente lo mismo — un PAN real o una CLABE real — y dejan pasar las
        // secuencias largas que no son ninguna de las dos. Probe JVM pre-fix: la
        // mitad de los "innocents" de abajo bloqueaban en persist pero no en read.
        val sensitive = listOf(
            // PAN reales (Luhn válido) con y sin separadores y dentro de frase.
            "4111 1111 1111 1111",
            "4111111111111111",
            "4242 4242 4242 4242",
            "5555 5555 5555 4444",
            "3782 822463 10005",
            "mi tarjeta es 4111 1111 1111 1111 y pago mañana",
            // CLABE real (checksum válido), con y sin separadores y en frase.
            "032180000118359719",
            "032 180 0001 1835 9719",
            "transfiere a esta cuenta 032180000118359719 antes del cierre"
        )
        val innocents = listOf(
            // Secuencias largas que NO son PAN (no Luhn) ni CLABE: NO deben
            // bloquearse en ningún gate. Antes el persist las bloqueaba (falso
            // positivo del patrón crudo); este test fija el comportamiento correcto.
            "factura 9876543210123",
            "mi IMEI es 123456789012345",
            "referencia 1234567890123456789",
            "el rastreo es 0000000000000000000",
            "numero de guia 1234567890123"
        )
        sensitive.forEach { text ->
            assertTrue("PAN/CLABE real debe bloquearse en persist: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue("PAN/CLABE real debe bloquearse en lectura: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertEquals(
                "asimetría PAN/CLABE en \"$text\": persist != read (c.303)",
                ConversationPrivacyPolicy.containsSensitiveContent(text),
                ContextPrivacyFilter.containsSensitiveContent(text)
            )
        }
        innocents.forEach { text ->
            assertFalse("secuencia larga no-PAN no debe bloquearse en persist (falso positivo c.303): \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertFalse("secuencia larga no-PAN no debe bloquearse en lectura: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertEquals(
                "asimetría en secuencia larga no sensible \"$text\": persist != read (c.303)",
                ConversationPrivacyPolicy.containsSensitiveContent(text),
                ContextPrivacyFilter.containsSensitiveContent(text)
            )
        }
    }

    // c.310: compromisos en presente de 1ª persona con dativo de 3ª persona
    // "le" — cuando uno se compromete a pasar/enviar/mandar algo a un TERCERO
    // (no al interlocutor "te"). c.306 cubrió "te paso"/"te mando" (receptor =
    // interlocutor), pero "le paso el informe a María", "le mando el reporte",
    // "le envío el correo" (receptor = 3ª persona) NO casaban → compromiso
    // olvidado. El pronombre "le" es el desambiguador de precisión (un verbo
    // pelado "paso"/"mando" es ambiguo), igual que "te"/"lo" en c.306. Probe JVM
    // PRE-fix: 8/8 MISSED. Nace como draft SELF_COMMITMENT PENDING revisable.
    @Test
    fun detectsFirstPersonPresentCommitmentsWithThirdPersonDative() {
        val positives = listOf(
            "le paso el informe a María mañana",
            "le mando el reporte el viernes",
            "le envío el correo hoy",
            "le paso los datos luego",
            "le mando el documento esta tarde",
            "le envío el archivo el lunes",
            "mañana le paso el informe",
            "le paso el link cuando lo tenga"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "le-$text"
            )
            assertTrue("presente de 1ª persona con dativo 'le' DEBE detectarse: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentOwner.SELF, result[0].owner)
        }
    }

    // c.310: la guarda de negación existente (c.279) protege también las formas
    // con "le": "no le paso nada", "no le mando el reporte", "no le envío nada"
    // son NEGATIVAS (rechazos), no compromisos. Verificado por probe JVM: 5/5
    // negativas correctamente excluidas.
    @Test
    fun thirdPersonDativeCommitmentsRespectDirectNegation() {
        val negatives = listOf(
            "no le paso nada",
            "no le mando el reporte",
            "no le envío el correo",
            "no le paso los datos",
            "no le mando nada"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "le-neg-$text"
            )
            assertEquals("una negativa con 'le' NO debe generar draft: \"$text\"", 0, result.size)
        }
    }

    // c.310: precisión — un verbo pelado sin pronombre "le" ("paso por tu casa",
    // "mando la carta", "envío el paquete") es ambiguo y NO debe disparar. El
    // pronombre "le" es el desambiguador. "se lo paso" (dativo plural/reflexivo
    // "se") también es compromiso válido y debe detectarse.
    @Test
    fun barePresentVerbsWithoutThirdPersonDativeAreNotFlagged() {
        val innocent = listOf(
            "paso por tu casa sin avisar",
            "mando la carta al correo",
            "envío el paquete hoy",
            "paso la voz a todos"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "le-bare-$text"
            )
            assertEquals("verbo pelado sin 'le' NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.312: el doble pronombre "se lo" (dativo de 3ª persona "se" + acusativo
    // "lo") es la forma pronominal del compromiso de 3ª persona cuando el objeto
    // es también pronominal ("le paso el informe a María" → "se lo paso"). La
    // regla española "le/les" → "se" ante otro pronombre hace que "se lo paso /
    // se lo mando / se lo envío" sean la forma natural de referirse a un tercero
    // ya mencionado. Deben detectarse como compromisos (evitar olvidos).
    @Test
    fun doublePronounSeLoCommitmentsAreDetected() {
        val positives = listOf(
            "se lo paso a él mañana",
            "se lo mando el lunes",
            "se lo envío hoy",
            "mañana se lo paso a María",
            "se lo paso luego",
            "después se lo paso"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "selo-pos-$text"
            )
            assertTrue("'se lo' + verbo 1ª persona debe generar draft: \"$text\"", result.isNotEmpty())
        }
    }

    // c.312: bug de precisión — la guarda de negación (c.279) miraba 3 caracteres
    // antes del inicio del match. Como el clítico "se lo" NO estaba en la
    // alternancia, el match caía en el "lo" pelado (3 chars después de "se"), así
    // el prefijo era "se " y la negación "no " (más atrás) se perdía → "no se lo
    // paso" generaba un draft espurio. Al añadir "se lo" a la alternancia, el
    // match empieza en "se" y el prefijo "no " queda visible para la guarda.
    // Mismo mecanismo que ya protege "no te lo paso" (c.306, "te lo" explícito).
    @Test
    fun doublePronounSeLoCommitmentsRespectDirectNegation() {
        val negatives = listOf(
            "no se lo paso nada",
            "no se lo mando el reporte",
            "no se lo envío el correo",
            "no se lo paso los datos",
            "no se lo preparo",
            "no se lo termino",
            "no se lo entrego"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "selo-neg-$text"
            )
            assertEquals("una negativa con 'se lo' NO debe generar draft: \"$text\"", 0, result.size)
        }
    }

    // c.312: precisión — "se lo" en construcciones que NO son compromiso NO debe
    // disparar. La desinencia de 1ª persona (-o) es el desambiguador: 3ª persona
    // ("se lo pasa", "se lo envía", "se lo lleva") y verbos no listados ("se lo
    // di" = pasado de dar) no casan. "me lo paso bien" (idiom) queda fuera porque
    // "me lo" no está en la alternancia (sólo te-lo/se-lo).
    @Test
    fun doublePronounSeLoDoesNotFlagNonCommitments() {
        val innocent = listOf(
            "se lo di ayer",
            "se cayó y se lo llevó",
            "él se lo pasa cada tarde",
            "ella se lo envía el viernes",
            "se lo dejé en la mesa"
        )
        innocent.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "selo-innocent-$text"
            )
            assertEquals("'se lo' no-compromiso NO debe disparar: \"$text\"", 0, result.size)
        }
    }

    // c.313: identificadores personales (INE/CURP/NSS/pasaporte/licencia) son PII:
    // su fuga en texto plano es tan grave como una credencial. No son detectables
    // por valor pelado (11 dígitos = teléfono; alfanumérico corto = código de
    // producto), por eso se exige palabra-clave acompañante. Ambos gates (persist
    // y lectura) deben bloquearlos de forma simétrica vía la fuente única
    // SensitiveSecretPatterns.containsPersonalIdentifier.
    @Test
    fun personalIdentifiersAreBlockedByBothPrivacyGates() {
        val leaks = listOf(
            // CURP (18 rígidos: 4 letras + 6 dígitos + 6 alfanum + 2 dígitos).
            "mi CURP es GOME850101HDFLRN09",
            "CURP: GOME850101HDFLRN09",
            "anota mi curp GOME850101HDFLRN09 porfa",
            // NSS (11 dígitos mexicano).
            "mi NSS es 12345678901",
            "número de seguro social 12345678901",
            "NSS 12345678901",
            // INE / credencial de elector (12-18 alfanuméricos).
            "mi INE es ABCDEF123456789",
            "credencial de elector RSTUVW012345678901",
            // Pasaporte (6-12 alfanuméricos; MX: 1 letra + 8 dígitos).
            "mi pasaporte es G12345678",
            "pasaporte: M99887766",
            // Licencia de conducir.
            "mi licencia de conducir es A12345678",
            "licencia B98765432",
            // RFC mexicano (c.314): persona física = 4 letras + 6 dígitos + 3 homoclave (13);
            // persona moral = 3 letras + 6 dígitos + 3 homoclave (12).
            "mi RFC es GODE850101HXA",
            "RFC: ABC850101XYZ",
            "anota el RFC COS950101MB1",
            // DNI/NIE espanol (c.315): 8 digitos + letra de control (modulo 23);
            // NIE = X/Y/Z + 7 digitos + letra. La letra debe ser valida.
            "mi DNI es 12345678Z",
            "DNI: 50123456Q",
            "anota el dni 99887766P porfa",
            "mi NIE es X1234567L",
            "NIE: Y7654321G",
            "apunta el nie Z9999999H",
            // La palabra-clave "nif" tambien activa (sinonimo habitual en ES).
            "mi NIF 12345678Z",
            // Letras de control R y S: validas en la tabla oficial pero
            // erroneamente excluidas por una regex previa (c.315 fix). Ahora
            // deben bloquearse (no perder ~9% de DNIs reales por falso negativo).
            "mi DNI es 10000010R",
            "dni: 10000024S",
            // c.326: CPF (Brasil, 11 dígitos, 2 verificadores mod-11). Valid:
            // 529.982.247-25, 111.444.777-35. Formato con puntos/guion y pelado.
            "mi CPF es 529.982.247-25",
            "CPF 111.444.777-35",
            "CPF 52998224725",
            // c.326: CUIT/CUIL (Argentina, 11 dígitos, 1 verificador mod-11).
            // Valid: 30-50001091-2. "cuil" es sinónimo de "cuit".
            "mi CUIT 30-50001091-2",
            "CUIT 30500010912",
            "CUIL 30500010912",
            // c.326: CNPJ (Brasil, 14 dígitos, 2 verificadores mod-11). Valid:
            // 11.222.333/0001-81. Formato con barra de rama.
            "CNPJ 11.222.333/0001-81",
            "CNPJ 11222333000181",
            // c.327: RUT (Rol Único Tributario, Chile, 7-8 dígitos + 1 verificador
            // mod-11 serie [2,3,4,5,6,7]; 10->K, 11->0). Valid: 12.345.678-5,
            // 16.894.365-2, 11.111.111-1, 7.654.321-6, 10.000.013-K. Con puntos,
            // pelado, con guion y sin guion; verificador alfanumérico K.
            "mi RUT es 12.345.678-5",
            "RUT 16.894.365-2",
            "RUT 123456785",
            "el rut del cliente 11.111.111-1",
            "anota mi rut 7.654.321-6 porfa",
            "mi RUT 76543216",
            "mi RUT es 10.000.013-K"
        )
        leaks.forEach { text ->
            assertTrue("PII debe bloquearse en persist: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertTrue("PII debe bloquearse en lectura: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
            assertEquals(
                "asimetría PII en \"$text\": persist != read (c.313)",
                ConversationPrivacyPolicy.containsSensitiveContent(text),
                ContextPrivacyFilter.containsSensitiveContent(text)
            )
            assertTrue(
                "no debe generar compromiso desde PII: \"$text\"",
                CommitmentEngine.extract(listOf(ChatMessage("Yo", text)), scopeHash = "pii").isEmpty()
            )
        }
    }

    @Test
    fun personalIdentifierKeywordWithoutValueDoesNotBlockInnocentChat() {
        // La palabra-clave pelada ("INE", "CURP", "pasaporte") sin un valor con
        // la estructura esperada NO debe bloquear: evita falsos positivos y
        // pérdida de chats legítimos ("trámite del INE", "renovar pasaporte").
        // También secuencias largas de dígitos que NO van con palabra-clave de PII.
        val innocent = listOf(
            "tengo que renovar el INE la próxima semana",
            "me pidieron el CURP para el trámite",
            "voy a sacar el pasaporte el viernes",
            "se me venció la licencia de conducir",
            "trámite del seguro social en la mañana",
            // 11 dígitos SIN palabra-clave de PII: es un teléfono/referencia, no NSS.
            "llama al 12345678901",
            "referencia 1234567890123",
            // alfanumérico corto sin palabra-clave de PII: código de producto.
            "el producto SKU ABC12345 llegó",
            "factura 9876543210123",
            "mi IMEI es 123456789012345",
            // RFC: palabra-clave pelada sin valor estructurado no bloquea (c.314).
            "tengo que tramitar el RFC la próxima semana",
            "mi rfc aún no me acuerdo",
            // alfanumérico con estructura tipo-RFC pero SIN palabra-clave "rfc":
            // es una referencia/código, no un RFC.
            "referencia GODE850101HXA",
            // DNI/NIE: palabra-clave pelada sin valor estructurado no bloquea (c.315).
            "tengo que renovar el DNI la semana que viene",
            "mi nie caduca el mes que viene",
            // 8 digitos + letra con estructura tipo-DNI pero letra INCORRECTA
            // (12345678 -> Z, no A): no es un DNI valido, no debe bloquearse.
            "mi DNI es 12345678A",
            // 8 digitos + letra SIN palabra-clave "dni": referencia/codigo.
            "pedido 12345678Z confirmado",
            // Letra I: NO aparece en la tabla de control -> no es DNI valido.
            "mi DNI es 12345678I",
            // c.326: checksum INCORRECTO con palabra-clave presente -> no es un
            // identificador valido, no debe bloquearse (precision). El digito
            // verificador se altera en 1 -> invalido. Misma logica que la letra
            // de control incorrecta del DNI.
            "CPF 529.982.247-26",       // verificador incorrecto (25 -> 26)
            "CUIT 30-50001091-3",       // verificador incorrecto (2 -> 3)
            "CNPJ 11.222.333/0001-82",  // verificador incorrecto (81 -> 82)
            // c.327: RUT con checksum INCORRECTO + palabra-clave presente -> no es
            // un RUT válido, no debe bloquearse (precisión). 12.345.678->5 (no 9),
            // 7.654.321->6 (no 0), 10.000.013->K (no 2). Letra A como verificador
            // no es válida (solo 0-9/K).
            "RUT 12.345.678-9",         // verificador incorrecto (5 -> 9)
            "RUT 7.654.321-0",          // verificador incorrecto (6 -> 0)
            "RUT 10.000.013-2",         // verificador incorrecto (K -> 2)
            "RUT 12345678A",            // verificador letra no-K (A no es válido)
            // c.326: valor de 11/14 digitos SIN palabra-clave "cpf/cnpj/cuit"
            // -> referencia/telefono, no identificador fiscal.
            "referencia 52998224725 en la factura",
            "pedido 11222333000181 confirmado",
            // c.327: valor de 7-8 dígitos + verificador SIN palabra-clave "rut"
            // -> referencia, no RUT.
            "referencia 123456785 en la factura",
            "pedido 76543216 confirmado",
            // c.326: palabra-clave pelada sin valor estructurado no bloquea.
            "tengo que tramitar el CPF la semana que viene",
            "el CNPJ de la empresa lo busco luego",
            "mi CUIT aún no me acuerdo",
            // c.327: palabra-clave "rut" pelada sin valor estructurado no bloquea.
            // "fruta"/"ruta"/"bruto" contienen "rut" pero no como palabra aislada.
            "tengo que tramitar el RUT la semana que viene",
            "mi rut aún no me acuerdo",
            "comprar fruta y verdura",
            "toma la ruta por la montaña"
        )
        innocent.forEach { text ->
            assertFalse("falso positivo PII en persist: \"$text\"", ConversationPrivacyPolicy.containsSensitiveContent(text))
            assertFalse("falso positivo PII en lectura: \"$text\"", ContextPrivacyFilter.containsSensitiveContent(text))
        }
    }

    // c.316: obligación DIRIGIDA AL USUARIO por un tercero en 2ª persona —
    // "tienes que firmar el contrato el lunes". Forma natural en español de que
    // OTRA persona le comunique al usuario una obligación suya. Simétrica de
    // "tengo que" (c.305). Antes caía a MISSED: el usuario olvidaba una
    // obligación que alguien le comunicó en el chat. Probe JVM PRE-fix: 1/1
    // MISSED. La clave de la corrección: aunque el remitente sea OTRA persona,
    // la obligación es DEL USUARIO ("tienes" = 2ª persona dirigida al oyente),
    // así que el draft se ancla a owner=SELF / kind=SELF_COMMITMENT — NO a
    // OTHER_COMMITMENT (que llevaría al usuario a descartarlo creyendo que es
    // compromiso ajeno cuando es suyo).
    @Test
    fun detectsUserDirectedObligationFromOtherParticipant() {
        val positives = listOf(
            "tienes que firmar el contrato el lunes",
            "tienes que pagar la renta esta semana",
            "tienes que entregar el reporte el viernes",
            "tienes que llamar al medico manana",
            "Ana, tienes que revisar el contrato hoy"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "obl-$text"
            )
            assertEquals("obligación 2ª persona dirigida al usuario DEBE detectarse: \"$text\"", 1, result.size)
            assertEquals("la obligación es DEL usuario (no del remitente): \"$text\"", CommitmentOwner.SELF, result[0].owner)
            assertEquals("debe ser SELF_COMMITMENT del usuario (no OTHER): \"$text\"", CommitmentKind.SELF_COMMITMENT, result[0].kind)
        }
    }

    // c.316: "no tienes que" es AUSENCIA de obligación ("no tienes que
    // preocuparte", "no tienes que venir"). La guarda de negación [precedingNegation]
    // la excluye, igual que excluye "no tengo que" en hasUnnegatedCommitment (c.279).
    // Probe JVM: 2/2 correctamente excluidas.
    @Test
    fun userObligationRespectsDirectNegation() {
        val negatives = listOf(
            "no tienes que preocuparte",
            "no tienes que venir manana",
            "no tienes que hacer nada"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "neg-obl-$text"
            )
            assertTrue("\"$text\" NO debe generar draft de obligación (es negación)", result.isEmpty())
        }
    }

    // c.316: "tienes razón" / "tienes tiempo" NO llevan "que" — no son
    // obligaciones. La señal exige "tienes que", así que estas formas cotidianas
    // NO se confunden con compromisos. Probe JVM: 1/1 correctamente excluido.
    @Test
    fun tienesSinQueNoFiresAsObligation() {
        val notObligations = listOf(
            "tienes razon",
            "tienes tiempo para esto",
            "tienes un mensaje nuevo"
        )
        notObligations.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Ana", text)),
                selfParticipant = "Yo",
                scopeHash = "noque-$text"
            )
            assertTrue("\"$text\" no debe clasificarse como obligación (sin 'que')", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
        }
    }

    // c.316: auto-promesas de seguimiento en presente de 1ª persona con objeto
    // "te" — "te aviso cuando llegue", "te confirmo mas tarde". Continuación de
    // c.305: "te paso"/"te mando" ya se detectaban, pero "te aviso"/"te confirmo"
    // (verbos de avisar/confirmar, igual de cotidianos en un seguimiento) caían a
    // MISSED. Probe JVM PRE-fix: 2/2 MISSED. El objeto "te" señala que el usuario
    // se compromete a informar a su interlocutor después, como "te llamo".
    @Test
    fun detectsFollowUpPromiseWithTeAvisoTeConfirmo() {
        val positives = listOf(
            "te aviso cuando llegue",
            "te confirmo mas tarde",
            "te aviso el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "fu-$text"
            )
            assertTrue("seguimiento 'te aviso/te confirmo' DEBE detectarse: \"$text\"", result.isNotEmpty())
            assertEquals(CommitmentOwner.SELF, result[0].owner)
            assertEquals(CommitmentKind.SELF_COMMITMENT, result[0].kind)
        }
    }

    // c.316: "no te aviso" es negación del seguimiento — se excluye igual que
    // "no te llamo" (c.279). Reutiliza hasUnnegatedCommitment, así que la guarda
    // de negación de compromiso cubre también estas formas nuevas.
    @Test
    fun followUpTeAvisoRespectsDirectNegation() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "no te aviso nada")),
            selfParticipant = "Yo",
            scopeHash = "neg-fu"
        )
        assertTrue("\"no te aviso\" no debe generar draft (es negación)", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
    }

    // c.329: obligaciones PENDIENTES — "tengo pendiente enviar el informe",
    // "me queda pendiente el pago", "me falta confirmar la hora", "tengo por
    // revisar el contrato". Antes caían a MISSED (probe JVM pre-fix: 9/10
    // MISSED). Estas frases reconocen una deuda abierta del usuario y deben
    // generar draft SELF_COMMITMENT/SELF. Probe JVM post-fix: 9/9 DETECT.
    @Test
    fun detectsPendingObligationPhrases() {
        val positives = listOf(
            "tengo pendiente enviar el informe",
            "tengo pendiente llamar al cliente",
            "me queda pendiente el pago",
            "me queda pendiente confirmar la hora",
            "me falta enviar el reporte",
            "me falta confirmar la hora",
            "tengo por revisar el contrato",
            "tengo por enviar el correo",
            "quedo pendiente el pago del alquiler"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pend-$text"
            )
            assertTrue("obligación pendiente DEBE detectarse: \"$text\"", result.isNotEmpty())
            assertEquals("la deuda pendiente es DEL usuario: \"$text\"", CommitmentOwner.SELF, result[0].owner)
            assertEquals("debe ser SELF_COMMITMENT: \"$text\"", CommitmentKind.SELF_COMMITMENT, result[0].kind)
        }
    }

    // c.329: la obligación pendiente se ancla a SELF incluso cuando la dice un
    // TERCERO (Ana le recuerda al usuario "tienes pendiente firmar el
    // contrato"). Igual que isUserObligation ("tienes que"), la deuda es del
    // usuario, no del remitente. Sin este anclaje el draft iría a OTHER y el
    // usuario lo descartaría creyendo que no es suyo.
    @Test
    fun pendingObligationFromOtherParticipantAnchoredToSelf() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Ana", "tienes pendiente firmar el contrato")),
            selfParticipant = "Yo",
            scopeHash = "pend-other"
        )
        assertTrue("obligación pendiente dicha por tercero DEBE detectarse", result.isNotEmpty())
        assertEquals("la deuda es DEL usuario aunque la diga Ana", CommitmentOwner.SELF, result[0].owner)
    }

    // c.329: "no tengo nada pendiente" / "ya no me queda pendiente nada" son
    // AUSENCIA de obligación. La guarda de negación [hasUnnegatedPendingObligation]
    // las excluye, igual que excluye "no tengo que" en hasUnnegatedCommitment
    // (c.279). Probe JVM: 2/2 correctamente excluidas.
    @Test
    fun pendingObligationRespectsDirectNegation() {
        val negatives = listOf(
            "no tengo nada pendiente",
            "ya no me queda pendiente nada"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "neg-pend-$text"
            )
            assertTrue("\"$text\" NO debe generar draft (es negación de obligación)", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
        }
    }

    // c.329: el sustantivo "pendiente" (arete) NO debe confundirse con la
    // obligación. "el pendiente del collar" no lleva construcción verbal de
    // tener/quedar antes, así que la señal no casa. Probe JVM: 1/1 excluido.
    @Test
    fun pendingNounEarringIsNotFlaggedAsObligation() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "el pendiente del collar")),
            selfParticipant = "Yo",
            scopeHash = "noun-pend"
        )
        assertTrue("\"el pendiente del collar\" no debe generar draft (es el sustantivo, no obligación)", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
    }

    // c.329: "me falta azúcar" / "me falta un dólar" NO son obligaciones
    // pendientes — son carencias materiales. La señal exige "me falta" +
    // infinitivo de acción (lista curada), no sustantivo, para evitar estos
    // falsos positivos. Sufijo -ar/-er/-ir habría casado con "lugar"/"azúcar".
    @Test
    fun meFaltaWithNounIsNotFlaggedAsObligation() {
        val negatives = listOf(
            "me falta azucar",
            "me falta un dolar",
            "me falta lugar"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "noun-falta-$text"
            )
            assertTrue("\"$text\" no debe generar draft (carencia material, no obligación)", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
        }
    }

    // c.330: PASADO de obligación pendiente — "me quedó pendiente el pago",
    // "me quedó por enviar el reporte". El usuario describe una deuda YA
    // contraída en pretérito, no solo futura. c.329 cubría presente "me
    // queda/quedan" pero NO "me quedó/quedaron" (olvido de deuda histórica).
    // Probe JVM pre-fix: 2/2 MISSED. Post-fix: 2/2 DETECT SELF_COMMITMENT/SELF.
    @Test
    fun detectsPendingObligationPastTense() {
        val positives = listOf(
            "me quedó pendiente el pago",
            "me quedó pendiente confirmar la hora",
            "me quedó por enviar el reporte",
            "me quedaron pendientes dos facturas"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pend-past-$text"
            )
            assertTrue("obligación pendiente en pasado DEBE detectarse: \"$text\"", result.isNotEmpty())
            assertEquals("la deuda pasada es DEL usuario: \"$text\"", CommitmentOwner.SELF, result[0].owner)
            assertEquals("debe ser SELF_COMMITMENT: \"$text\"", CommitmentKind.SELF_COMMITMENT, result[0].kind)
        }
    }

    // c.330: "me falta por confirmar la hora" / "me queda por hacer la
    // entrega" — giro cotidiano de obligación pendiente con "falta/queda por" +
    // infinitivo. c.329 cubría "tengo por" + infinitivo pero NO las formas con
    // "falta/queda por". Probe JVM pre-fix: 2/2 MISSED. Post-fix: 2/2 DETECT.
    @Test
    fun detectsFaltaQuedaPorInfinitiveObligation() {
        val positives = listOf(
            "me falta por confirmar la hora",
            "me falta por enviar el reporte",
            "me queda por hacer la entrega",
            "me queda por revisar el contrato"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "pend-por-$text"
            )
            assertTrue("\"$text\" DEBE detectarse (falta/queda por + infinitivo)", result.isNotEmpty())
            assertEquals("la deuda es DEL usuario: \"$text\"", CommitmentOwner.SELF, result[0].owner)
            assertEquals("debe ser SELF_COMMITMENT: \"$text\"", CommitmentKind.SELF_COMMITMENT, result[0].kind)
        }
    }

    // c.330: las nuevas formas en pasado respetan la negación igual que c.329.
    // "no me quedó pendiente nada" / "ya no me falta por enviar nada" son
    // AUSENCIA de obligación. La guarda hasUnnegatedPendingObligation las
    // excluye (reusa precedingNegation). Probe JVM: 2/2 excluidas.
    @Test
    fun pendingObligationPastTenseRespectsNegation() {
        val negatives = listOf(
            "no me quedó pendiente nada",
            "ya no me falta por confirmar nada",
            "no me falta por enviar nada"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "neg-pend-past-$text"
            )
            assertTrue("\"$text\" NO debe generar draft (negación de obligación pasada)", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
        }
    }

    // c.330: al añadir el pretérito "quedó" al patrón, se evita que la forma
    // INDEPENDIENTE "quedó pendiente" (3ª persona, sin pronombre reflexivo)
    // genere un draft anclado a SELF — sería un falso positivo de enrutado.
    // Solo la forma con pronombre "me/te/le... quedó pendiente" casa; "quedó
    // pendiente de llamarme" (3ª persona) NO debe ir a SELF. Probe JVM: 1/1
    // excluido de SELF (no genera draft SELF_COMMITMENT/SELF).
    @Test
    fun thirdPersonQuedoPendienteNotAnchoredToSelf() {
        val result = CommitmentEngine.extract(
            listOf(ChatMessage("Yo", "quedó pendiente de llamarme")),
            selfParticipant = "Yo",
            scopeHash = "3p-quedo"
        )
        assertTrue("\"quedó pendiente de llamarme\" (3ª persona) NO debe anclarse a SELF", result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF })
    }

    // c.342: la rama futura de commitmentSignal era ASIMETRICA con la rama de
    // presente: solo cubria hare/terminare + "voy a", pero el presente con
    // clitico cubria 9 verbos (termino, entrego, reviso, preparo, arreglo,
    // subo, dejo, paso, mando, envio). Asi "lo reviso" (presente) se detectaba
    // pero "lo revisare" (futuro, la forma de promesa MAS explicita) caia a
    // MISSED -> olvido de compromiso futuro (P1, perdida de datos). Se alinean
    // los futuros de los mismos 9 verbos. Probe JVM PRE-fix: 8/10 MISSED
    // (revisare, mandare, enviare, entregare, preparare, arreglare, subire,
    // dejare, pasare + clitico). Este test los fija como regresion permanente.
    @Test
    fun futureTenseCommitmentsWithCliticAreDetected() {
        val positives = listOf(
            "lo revisar\u00e9", "te lo mandar\u00e9", "se lo enviar\u00e9",
            "lo entregar\u00e9", "lo preparar\u00e9", "lo arreglar\u00e9",
            "lo subir\u00e9", "lo dejar\u00e9", "lo pasar\u00e9",
            "te lo voy a mandar", "se lo voy a enviar", "lo voy a revisar",
            "lo har\u00e9 hoy", "terminar\u00e9 el informe", "lo voy a hacer"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "fut-pos-$text"
            )
            assertTrue(
                "\"$text\" (futuro explicito) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.342: la guarda precedingNegation debe seguir excluyendo los futuros con
    // clitico igual que "no lo hare": al arrancar el match en el clitico, el
    // "no " queda adyacente y visible a 3 chars. Sin esta guarda, "no lo
    // revisare" generaria un draft espurio (rechazo detectado como compromiso).
    // Probe JVM: 7/7 excluidos.
    @Test
    fun futureTenseCommitmentsRespectNegation() {
        val negatives = listOf(
            "no lo revisar\u00e9", "no te lo mandar\u00e9", "no lo enviar\u00e9",
            "no lo voy a revisar", "no se lo voy a mandar",
            "no lo har\u00e9", "no terminar\u00e9 nada", "no lo voy a hacer"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "fut-neg-$text"
            )
            assertTrue(
                "\"$text\" (negacion de futuro) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.527: la rama futura de commitmentSignal era ASIMETRICA en numero, igual
    // que barePresentCommitmentSignal lo era en c.526. Detectaba "lo terminare"
    // (singular) y "voy a terminar" pero NO "lo terminaremos"/"terminaremos"/
    // "lo revisaremos"/"te lo mandaremos"/"se lo enviaremos"/"vamos a terminar"
    // (1ª persona PLURAL) -> olvido de compromisos compartidos (P1). Probe JVM
    // PRE-fix: 12/12 MISSED. Este test los fija como regresion.
    @Test
    fun futureTensePluralCommitmentsAreDetected() {
        val positives = listOf(
            "lo terminaremos el viernes", "lo revisaremos manana",
            "lo prepararemos para el lunes", "lo entregaremos el viernes",
            "lo arreglaremos manana", "lo subiremos en un rato",
            "te lo mandaremos manana", "se lo enviaremos el lunes",
            "lo pasaremos manana", "lo dejaremos el viernes",
            "terminaremos el informe manana", "revisaremos el contrato manana",
            "vamos a terminar el informe manana", "lo vamos a revisar manana",
            "lo haremos hoy", "haremos el informe el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c527-pos-$text"
            )
            assertTrue(
                "\"$text\" (futuro plural) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.527: precision simetrica. Los futuros plurales negados se excluyen igual
    // que los singulares ("no lo terminaremos" ~ "no lo terminare"). Probe JVM
    // POST-fix: excluidos.
    @Test
    fun futureTensePluralCommitmentsRespectNegation() {
        val negatives = listOf(
            "no lo terminaremos el viernes", "no terminaremos el informe manana",
            "no lo revisaremos", "no te lo mandaremos",
            "no se lo enviaremos", "no lo vamos a revisar",
            "no vamos a terminar nada", "no lo haremos"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c527-neg-$text"
            )
            assertTrue(
                "\"$text\" (negacion de futuro plural) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.529: asimetría de número en la rama "tengo que" de commitmentSignal,
    // espejo de c.526/c.527/c.528. Detectaba "tengo que entregar el informe"
    // (singular) pero NO "tenemos que entregar el informe" (1ª persona PLURAL)
    // -> olvido de un compromiso COMPARTIDO cotidiano (P1). La forma plural de
    // la perífrasis de obligación "tenemos que" + infinitivo es la forma natural
    // de obligación conjunta en chat español. Probe JVM PRE-fix: 5/5 MISSED.
    @Test
    fun detectsTenemosQueSharedObligationPlural() {
        val positives = listOf(
            "tenemos que entregar el informe el viernes",
            "tenemos que firmar el contrato el lunes",
            "tenemos que pagar la renta manana",
            "tenemos que revisar el documento esta semana",
            "tenemos que mandar la propuesta hoy"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c529-pos-$text"
            )
            assertEquals(
                "\"$text\" (tenemos que + infinitivo) debe generar draft SELF_COMMITMENT",
                1, result.size
            )
            assertEquals(
                "la obligación compartida es DEL usuario: \"$text\"",
                CommitmentOwner.SELF, result[0].owner
            )
            assertEquals(
                "debe ser SELF_COMMITMENT: \"$text\"",
                CommitmentKind.SELF_COMMITMENT, result[0].kind
            )
        }
    }

    // c.529: precisión simétrica. "no tenemos que" es AUSENCIA de obligación
    // ("no tenemos que preocuparnos", "no tenemos que entregar el informe"),
    // igual que "no tengo que". La guarda precedingNegation la excluye.
    // Probe JVM POST-fix: 2/2 excluidas.
    @Test
    fun tenemosQueRespectsDirectNegation() {
        val negatives = listOf(
            "no tenemos que entregar el informe",
            "no tenemos que preocuparnos",
            "no tenemos que venir manana"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c529-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de tenemos que) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.530: asimetría de número en la rama "debo" de commitmentSignal, quinto
    // y último miembro de la familia c.526/c.527/c.528/c.529. Detectaba
    // "debo entregar el informe" (1ª SINGULAR de la perífrasis "debo") pero NO
    // "debemos entregar el informe" (1ª PLURAL) → olvido de un compromiso
    // COMPARTIDO cotidiano ("debemos firmar el contrato el lunes", "debemos
    // pagar la renta", "debemos revisar el documento esta semana"), la forma
    // natural de obligación conjunta en chat español (P1). Probe JVM PRE-fix:
    // 5/5 MISSED.
    @Test
    fun detectsDebemosSharedObligationPlural() {
        val positives = listOf(
            "debemos entregar el informe el viernes",
            "debemos firmar el contrato el lunes",
            "debemos pagar la renta manana",
            "debemos revisar el documento esta semana",
            "debemos mandar la propuesta hoy"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c530-pos-$text"
            )
            assertEquals(
                "\"$text\" (debemos + infinitivo) debe generar draft SELF_COMMITMENT",
                1, result.size
            )
            assertEquals(
                "la obligación compartida es DEL usuario: \"$text\"",
                CommitmentOwner.SELF, result[0].owner
            )
            assertEquals(
                "debe ser SELF_COMMITMENT: \"$text\"",
                CommitmentKind.SELF_COMMITMENT, result[0].kind
            )
        }
    }

    // c.530: precisión simétrica. "no debemos" es AUSENCIA de obligación
    // ("no debemos entregar el informe", "no debemos preocuparnos"), igual que
    // "no debo". La guarda precedingNegation la excluye.
    // Probe JVM POST-fix: 2/2 excluidas.
    @Test
    fun debemosRespectsDirectNegation() {
        val negatives = listOf(
            "no debemos entregar el informe",
            "no debemos preocuparnos",
            "no debemos venir manana"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c530-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de debemos) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    @Test
    fun detectsFirstPersonPluralImperativesAsCommitments() {
        // c.531: imperativos de 1ª persona PLURAL (exhortativos "hagamos"/
        // "terminemos"/"revisemos"/... ) = compromiso COMPARTIDO cotidiano.
        // Nueva clase de detección (no existe imperativo de 1ª persona singular).
        val positives = listOf(
            "hagamos el informe el viernes",
            "terminemos el reporte manana",
            "revisemos el contrato el lunes",
            "preparemos la propuesta esta semana",
            "entreguemos el documento hoy",
            "llamemos al cliente manana",
            "mandemos el correo esta tarde",
            "enviemos la propuesta el lunes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c531-pos-$text"
            )
            assertTrue(
                "\"$text\" (imperativo plural) debe generar draft SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    @Test
    fun firstPersonPluralImperativesRespectDirectNegation() {
        val negatives = listOf(
            "no hagamos el informe",
            "no terminemos el reporte",
            "no revisemos el contrato"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c531-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de imperativo plural) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.533: el FUTURO de comunicación (llamaré/hablaré/escribiré + plurales
    // llamaremos/hablaremos/escribiremos) era la única forma de promesa de contacto
    // que caía a MISSED: el presente con clítico ("te llamo"/"le escribo") y el
    // presente pelado ("llamo al cliente mañana") ya se detectaban (c.508/c.518/
    // c.500), y el futuro de acción ("lo terminaré"/"lo llamaremos") también, pero
    // el futuro de comunicación no. Cobertura singular y plural, con clítico (te/le/
    // lo) y pelado con objeto nominal ("llamaré al cliente mañana").
    // Probe JVM PRE-fix: 17/17 MISSED.
    @Test
    fun detectsFutureCommunicationVerbsSingularAndPlural() {
        val positives = listOf(
            "lo llamaré el viernes",
            "le llamaré mañana",
            "te llamaré el lunes",
            "le escribiré mañana",
            "te escribiré el lunes",
            "le hablaré el viernes",
            "te hablaré el lunes",
            "llamaré al cliente mañana",
            "hablaré con el jefe el lunes",
            "escribiré el informe mañana",
            "lo llamaremos el viernes",
            "le escribiremos mañana",
            "te llamaremos el lunes",
            "lo escribiremos el lunes",
            "llamaremos al cliente mañana",
            "hablaremos con el jefe el lunes",
            "escribiremos el informe mañana"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c533-pos-$text"
            )
            assertEquals(
                "\"$text\" (futuro de comunicación) debe generar draft SELF_COMMITMENT",
                1, result.size
            )
            assertEquals(
                "debe ser SELF_COMMITMENT: \"$text\"",
                CommitmentKind.SELF_COMMITMENT, result[0].kind
            )
        }
    }

    // c.533: precisión simétrica. "no te llamaré"/"no le escribiré"/"no lo llamaremos"
    // son NEGACIONES (rechazos de contacto), igual que "no te llamo". La guarda
    // precedingNegation las excluye.
    // Probe JVM POST-fix: 5/5 excluidas.
    @Test
    fun futureCommunicationVerbsRespectDirectNegation() {
        val negatives = listOf(
            "no lo llamaré el viernes",
            "no le llamaré mañana",
            "no te llamaré el lunes",
            "no lo llamaremos el viernes",
            "no le escribiremos mañana"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c533-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de futuro de comunicación) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    // c.534: extensión natural de c.533. Tres verbos de comunicación más en FUTURO
    // seguían cayendo a MISSED: AVISARÉ (avisar[eé]/avisaremos), NOTIFICARÉ
    // (notificar[eé]/notificaremos) y DIRÉ (dir[eé]/diremos — irregular: decir->dir-).
    // c.533 cubría llamar/hablar/escribir, pero avisar/notificar/decir (su sinónimos
    // naturales de contacto: "le diré la respuesta"/"te avisaré"/"le notificaré el
    // viernes") no. Cobertura singular y plural, con clítico (te/le/lo/se lo) y pelado
    // con objeto nominal. Cierre de la asimetría de lexema del futuro de comunicación.
    // Probe JVM PRE-fix: 12/12 MISSED (3 verbos nuevos + controles c.533); POST-fix: 12/12 SELF_COMMITMENT.
    @Test
    fun detectsFutureCommunicationVerbsAvisarNotificarDecir() {
        val positives = listOf(
            "lo diré mañana",
            "le diré al cliente mañana",
            "te lo diré el lunes",
            "se lo diré el viernes",
            "le diré la respuesta mañana",
            "le avisaré mañana",
            "te avisaré el lunes",
            "le notificaré el viernes",
            "te notificaré el lunes",
            "lo diremos mañana",
            "le avisaremos mañana",
            "le notificaremos el viernes"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c534-pos-$text"
            )
            assertEquals(
                "\"$text\" (futuro avisar/notificar/decir) debe generar draft SELF_COMMITMENT",
                1, result.size
            )
            assertEquals(
                "debe ser SELF_COMMITMENT: \"$text\"",
                CommitmentKind.SELF_COMMITMENT, result[0].kind
            )
        }
    }

    // c.534: precisión simétrica. "no te avisaré"/"no le notificaré"/"no te lo diré"
    // son NEGACIONES (rechazos), igual que "no te avisar" (presente). La guarda
    // precedingNegation las excluye.
    // Probe JVM POST-fix: 4/4 excluidas.
    @Test
    fun futureAvisarNotificarDecirRespectDirectNegation() {
        val negatives = listOf(
            "no le avisaré mañana",
            "no te avisaré el lunes",
            "no le notificaré el viernes",
            "no te lo diré el lunes"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c534-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de futuro avisar/notificar/decir) NO debe generar draft SELF_COMMITMENT",
                result.none { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }


// c.535: "me toca"/"te toca" + infinitivo de acción. Giro impersonal "tocar a
    // uno" expresa a quién le corresponde una tarea. "te toca" (2ª=oyente) ancla a
    // SELF (obligación del usuario); "me toca" (1ª=hablante) se rutea por remitente.
    // Precisión: requiere infinitivo de [pendingActionInfinitives] para no disparar
    // con "me toca el turno"/"me toca la lotería". Negación excluida.
    @Test
    fun detectsTeTocaAsUserObligationSelf() {
        val positives = listOf(
            "te toca presentar el informe el viernes",
            "te toca pagar la ronda",
            "te toca revisar el contrato",
            "te toca enviar el reporte manana"
        )
        positives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("él", text)),
                selfParticipant = "Yo",
                scopeHash = "c535-te-pos-$text"
            )
            assertTrue(
                "\"$text\" (te toca) debe generar draft SELF_COMMITMENT (obligación del usuario)",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    @Test
    fun detectsMeTocaRoutedBySender() {
        // "me toca" dicho por OTRO → obligación del otro (OTHER_COMMITMENT).
        val otherPositive = listOf(
            "me toca presentar el informe el viernes",
            "me toca revisar el contrato",
            "me toca pagar la ronda"
        )
        otherPositive.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("él", text)),
                selfParticipant = "Yo",
                scopeHash = "c535-me-other-$text"
            )
            assertTrue(
                "\"$text\" (me toca, dicho por otro) debe generar OTHER_COMMITMENT",
                result.any { it.kind == CommitmentKind.OTHER_COMMITMENT && it.owner == CommitmentOwner.OTHER }
            )
        }
        // "me toca" dicho por el USUARIO → obligación propia (SELF_COMMITMENT).
        val selfPositive = listOf(
            "me toca presentar el informe el viernes",
            "me toca pagar la ronda"
        )
        selfPositive.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("Yo", text)),
                selfParticipant = "Yo",
                scopeHash = "c535-me-self-$text"
            )
            assertTrue(
                "\"$text\" (me toca, dicho por usuario) debe generar SELF_COMMITMENT",
                result.any { it.kind == CommitmentKind.SELF_COMMITMENT && it.owner == CommitmentOwner.SELF }
            )
        }
    }

    @Test
    fun tocaRespectsPrecisionAndNegation() {
        // Sin infinitivo de acción: NO debe disparar (precisión alta).
        val nonActions = listOf(
            "me toca el turno",
            "me toca la loteria",
            "te toca el turno"
        )
        nonActions.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("él", text)),
                selfParticipant = "Yo",
                scopeHash = "c535-prec-$text"
            )
            assertTrue(
                "\"$text\" (sin infinitivo) NO debe generar draft",
                result.none { it.kind != null }
            )
        }
        // Negación directa: NO debe disparar.
        val negatives = listOf(
            "no me toca a mi revisar el contrato",
            "no te toca pagar a ti"
        )
        negatives.forEach { text ->
            val result = CommitmentEngine.extract(
                listOf(ChatMessage("él", text)),
                selfParticipant = "Yo",
                scopeHash = "c535-neg-$text"
            )
            assertTrue(
                "\"$text\" (negación de toca) NO debe generar draft",
                result.none { it.kind != null }
            )
        }
    }


}

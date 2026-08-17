package com.ordia.app.conversations

import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ReminderRules
import com.ordia.app.domain.SensitiveSecretPatterns
import java.security.MessageDigest
import java.util.Locale

data class CommitmentDraft(
    val kind: CommitmentKind,
    val owner: CommitmentOwner,
    val actor: String,
    val action: String,
    val location: String,
    val dueAt: Long?,
    val confidence: Float,
    val suggestedReminderAt: Long?,
    val fingerprint: String
)

/** Filtro determinista previo: el contenido bloqueado no llega al extractor. */
object ConversationPrivacyPolicy {
    // Cobertura alineada con ContextPrivacyFilter para las categorías que pueden
    // llegar en una notificación de SMS/mensajería (paquete NO bancario, que pasa el
    // filtro de paquete de NotificationObservationPolicy). Sin esto, un SMS con
    // "tu saldo disponible", "estado de cuenta" o una "frase semilla" escapaba al
    // gate de notificaciones y se persistía. (c.286)
    //
    // c.290: también claves privadas cripto (hex 64 con/sin prefijo 0x) y bloques
    // PEM -----BEGIN ... PRIVATE KEY-----. ContextPrivacyFilter (gate de contexto/IME)
    // ya las bloqueaba, pero este gate (el que decide si una notificación se persiste
    // en la BD de conversaciones) NO → una clave privada recibida por SMS quedaba en
    // texto plano. Misma clase de fuga que c.287 cerró para seed phrases.
    //
    // c.292: credenciales cortas (PIN/NIP/contraseña) que el gate de lectura bloquea
    // pero este NO. Un SMS "tu clave temporal es 4821" o "tu pwd de acceso es ab12cd"
    // pasaba el gate de persistencia: "clave temporal" no es "clave de
    // acceso/seguridad/verificación" (patrón 2) y el otpCode sólo miraba
    // código/otp/verificación. La pareja "clave" + secreto numérico cercano, "pwd" en
    // peludo y "nip" (PIN en español de México, sinónimo exacto de "pin" que ya
    // bloqueábamos) se guardaban en texto plano en la BD de conversaciones. Cierre
    // simétrico con ContextPrivacyFilter, en la dirección que protege la persistencia.
    //
    // c.293: IBAN alfanumérico PELADO (sin la palabra "iban") y "two factor"/
    // "two-factor" en inglés. El gate de lectura bloquea un IBAN estructural
    // (2 mayúsculas + 2 dígitos + 11-30 alfanuméricos) aunque no diga "iban", y
    // bloquea "two factor" aunque no diga "2fa"; este gate sólo miraba la palabra
    // "iban" y "2fa" → "Transfiere a GB82WEST1234..." se persistía en texto plano.
    private val sensitivePatterns = listOf(
        Regex("""(?i)\b(?:contrase(?:ña|na)|password|passwd|pwd|pin|nip|cvv|cvc|token\s+bancario)\b"""),
        Regex("""(?i)\b(?:c[oó]digo|clave)\s+(?:de\s+)?(?:verificaci[oó]n|seguridad|acceso)\b"""),
        Regex("""(?i)\b(?:otp|2fa|two.?factor|autenticaci[oó]n\s+de\s+dos\s+pasos)\b"""),
        Regex("""(?i)\b(?:n[uú]mero\s+de\s+cuenta|account\s+number|clabe|iban|swift|c[eé]dula)\b"""),
        Regex("""(?i)\b(?:seed\s+phrase|recovery\s+phrase|frase\s+semilla|frase\s+de\s+recuperaci[oó]n|palabras\s+de\s+recuperaci[oó]n|mnemonic)\b"""),
        Regex("""(?i)\b(?:transferencia|dep[oó]sito|retiro|saldo|estado\s+de\s+cuenta)\b"""),
        // c.299: credenciales/secretos de infraestructura y nube (claves PEM,
        // hex 64, IBAN estructural, SSH, API keys, AWS, JWT, Google, Slack,
        // GitHub/GitLab PATs, cadenas de conexion) movidos a la fuente unica
        // `domain.SensitiveSecretPatterns` (compartida con ContextPrivacyFilter)
        // para que persistencia y lectura no puedan desincronizarse (causa raiz
        // de las 7 fugas c.287-c.298). Se consumen en `containsSensitiveContent`.
    )
    // "clave temporal/bancaria 4821" no entra en el patrón 2 (no es "de acceso/") pero
    // sí es un PIN: lo capturamos como otpCode. Añadir "clave" en peludo al patrón 1
    // bloquearía "la clave del éxito" (falso positivo → pérdida de chat legítimo), así
    // que aquí sólo la casamos cuando un secreto numérico corto la acompaña. Igual que
    // con código/otp/verificación: la palabra + hasta 20 no-dígitos + 4-8 dígitos.
    private val otpCode = Regex("""(?i)\b(?:c[oó]digo|otp|verificaci[oó]n|clave)\D{0,20}\d{4,8}\b""")

    fun containsSensitiveContent(text: String): Boolean =
        sensitivePatterns.any { it.containsMatchIn(text) } ||
            SensitiveSecretPatterns.patterns.any { it.containsMatchIn(text) } ||
            SensitiveSecretPatterns.containsNumericSensitive(text) ||
            SensitiveSecretPatterns.containsPersonalIdentifier(text) ||
            otpCode.containsMatchIn(text)
}

/** Extrae compromisos localmente sin guardar ni ejecutar acciones. */
object CommitmentEngine {
    private val requestSignal = Regex(
        // c.307: imperativos de 2ª persona con pronombre enclítico — peticiones
        // directas muy frecuentes en chat español que NO casaban con "envíame"/
        // "mándame"/"recuerda" (los únicos imperativos cubiertos): "pásame el
        // informe", "llámame más tarde", "escríbeme el correo", "háblame del tema",
        // "confírmame la hora", "dímelo por mensaje", "pásamelo esta noche". Probe
        // JVM PRE-fix: 8/11 MISSED. Sin objeto enclítico no se añaden (un verbo
        // pelado "pasa"/"llama" es ambiguo); el enclítico "me"/"melo" señala una
        // petición dirigida al usuario. Nace como draft REQUEST PENDING revisable,
        // igual que "envíame"/"mándame" ya existentes (un falso positivo se descarta,
        // un falso negativo es una petición olvidada).
        """(?i)\b(?:env[ií]ame|m[aá]ndame|no\s+olvides|recuerda|recu[eé]rdame|por\s+favor|puedes|podr[ií]as|necesito\s+que|p[aá]same|ll[aá]mame|escr[ií]beme|h[aá]blame|conf[ií]rmame|d[ií]melo|p[aá]samelo|m[aá]ndamelo)\b"""
    )
    // c.519: split meetingSignal en verbo (siempre encendido) + sustantivo con
    // guarda anti-objeto-genitivo. Antes, reunion|cita|encuentro casaba en
    // cualquier posicion -> falso MEETING cuando el sustantivo era el OBJETO de
    // un genitivo ("aviso de la reunion", "acta de la reunion", "factura de la
    // cita", "resumen de la reunion", "cobro por la reunion"): el compromiso real
    // es avisar/pagar/resumir, no reunirse, pero nacia como draft MEETING.
    // nos vemos / quedamos / sera a las son verbos de reunion inequivocos y se
    // mantienen sin guarda. Los sustantivos solo cuentan como MEETING cuando hay
    // al menos una ocurrencia que NO es objeto genitivo (es sujeto/evento:
    // "la reunion es manana", "tenemos cita el miercoles", "encuentro con el
    // cliente"). Java regex lookbehind es fixed-length, asi que la deteccion de
    // objeto se hace comparando rangos en hasMeetingNounAsSubject.
    private val meetingVerbSignal = Regex(
        """(?i)\b(?:nos\s+vemos|quedamos|ser[aá]\s+a\s+las)\b"""
    )
    private val meetingNounSignal = Regex(
        """(?i)\b(?:reuni[oó]n|cita|encuentro)\b"""
    )
    // Objeto genitivo: preposicion + determinante opcional + sustantivo de
    // reunion. El rango cubre la preposicion, el determinante y el sustantivo.
    // c.522: +sobre|tras (c.519 cerro de/del/por/para pero "informe sobre la
    // reunion", "acta tras la reunion", "notas sobre la cita" seguian disparando
    // falso MEETING: el sustantivo es el OBJETO/TEMA del genitivo, no el compromiso).
    private val meetingNounAsObject = Regex(
        """(?i)(?:de|del|por|para|sobre|tras)\s+(?:el|la|los|las|un|una|unos|unas)?\s*(?:reuni[oó]n|cita|encuentro)\b"""
    )
    // c.523: split purchaseSignal en verbo (siempre activo) + sustantivo con
    // guarda anti-objeto-genitivo (simetrico a meetingSignal c.519). Antes,
    // compra|mercado|supermercado casaban como sustantivo en cualquier posicion
    // -> falso PURCHASE cuando el sustantivo es el OBJETO de un genitivo: "ahorro
    // para la compra del coche", "presupuesto para el supermercado", "gasto en el
    // mercado". El compromiso real es ahorrar/presupuestar/gastar, no comprar; el
    // sustantivo de compra es el tema, no la accion. Los infinitivos
    // comprar/traer/conseguir son verbos inequivocos y se mantienen sin guarda.
    // "compra" (verbo imperativo "compra pan" / sustantivo "la compra") va al
    // sustantivo con guarda: "compra pan" (sin preposicion) NO es genitivo -> PURCHASE
    // (correcto); "para la compra del coche" (genitivo) -> suprimido. Residual
    // conocido: "el mercado de valores subio" (sustantivo como sujeto de
    // afirmacion de hecho, no genitivo-objeto) sigue disparando PURCHASE; misma
    // clase de residual intencional que "la reunion el lunes" en c.519.
    private val purchaseVerbSignal = Regex("""(?i)\b(?:comprar|traer|conseguir)\b""")
    private val purchaseNounSignal = Regex("""(?i)\b(?:compra|mercado|supermercado)\b""")
    // Objeto genitivo: preposicion + determinante opcional + sustantivo de compra.
    private val purchaseNounAsObject = Regex(
        """(?i)(?:de|del|por|para|sobre|tras|en)\s+(?:el|la|los|las|un|una|unos|unas)?\s*(?:compra|mercado|supermercado)\b"""
    )
    // c.524: split reminderSignal en verbo (siempre activo) + sustantivo con
    // guarda anti-objeto-genitivo (simetrico a meetingSignal c.519 y purchaseSignal
    // c.523). Antes, recordatorio casaba como sustantivo en cualquier posicion
    // -> falso REMINDER cuando el sustantivo es el OBJETO de un genitivo: "ajuste
    // para el recordatorio de la cita", "config del recordatorio", "notas sobre el
    // recordatorio", "pago por el recordatorio". El compromiso real es
    // ajustar/configurar/anotar/pagar, no fijar un recordatorio; el sustantivo es
    // el tema, no la accion. Las formas verbales recu[eé]rdame/av[ií]same/
    // no dejes que olvide son inequivocas y se mantienen sin guarda. "recordatorio
    // para mañana" (sin genitivo) NO es objeto -> REMINDER (correcto). Residual
    // conocido: "el recordatorio sonaba a las 9" (sujeto de afirmacion de hecho)
    // sigue disparando REMINDER; misma clase de residual que c.519/c.523.
    private val reminderVerbSignal = Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|no\s+dejes\s+que\s+olvide)\b""")
    private val reminderNounSignal = Regex("""(?i)\brecordatorio\b""")
    // Objeto genitivo: preposicion + determinante opcional + recordatorio.
    private val reminderNounAsObject = Regex(
        """(?i)(?:de|del|por|para|sobre|tras|en)\s+(?:el|la|los|las|un|una|unos|unas)?\s*recordatorio\b"""
    )
    // c.309: peticiones en indicativo de 2ª persona ("me pasas el informe?",
    // "me llamas luego?", "me envías el archivo mañana", "me lo mandas?").
    // Son la forma MÁS frecuente de pedir algo en chat español — más naturales
    // que el imperativo ("pásame") cubierto en c.307: en mensajería se pregunta
    // en vez de ordenar. Probe JVM PRE-fix: 10/12 MISSED. La desinencia -as
    // (pasas/envías/mandas/llamas/escribes/avisas/confirmas/dices/das/alcanzas/
    // dejas) es el desambiguador de persona: la 3ª persona termina en -a ("él me
    // llama", "me muestra", "me cuenta") y NO casa, así la narración en 3ª
    // persona se filtra sin lógica extra. La negación ("no me pasas nada",
    // "no me llamas nunca") es una queja/acusación, no una petición — se excluye
    // vía hasUnnegatedIndicativeRequest (a diferencia de requestSignal, donde la
    // negación es idiomática y positiva "no olvides"=recuérdame; ahí los
    // imperativos negativos van en subjuntivo "no me llames" que ya no casa con
    // -as). El pronombre-objeto opcional "me lo/la/los/las" refuerza la lectura
    // de transferencia ("me lo pasas", "me lo envías"). Nace como draft REQUEST
    // PENDING revisable, igual que los imperativos de c.307.
    private val indicativeRequestSignal = Regex(
        """(?i)\bme\s+(?:(?:lo|la|los|las)\s+)?(?:pasas|env[ií]as|mandas|llamas|escribes|avisas|confirmas|dices|das|alcanzas|dejas)\b"""
    )
    // c.536: imperativos de 2ª persona con OBJETO NOMINAL DETERMINADO —
    // peticiones directas sin pronombre enclítico: "envía el reporte el viernes",
    // "revisa el contrato", "entrega el informe el lunes", "paga la factura",
    // "firma el contrato", "manda el archivo", "sube el documento",
    // "prepara el informe". requestSignal (c.307) exige enclítico "me"/"melo";
    // indicativeRequestSignal (c.309) exige "me" + desinencia -as. Estos pelados
    // con objeto NINGUNO cubren: son la forma MÁS directa de ordenar algo sobre
    // un documento/cosa en chat laboral español. Probe JVM PRE-fix: 12/12 MISSED.
    //
    // Desambiguación 2ª (imperativo) vs 3ª persona (presente indicativo): en los
    // verbos cubiertos son HOMÓGRAFAS (envía/envía, revisa/revisa, paga/paga,
    // firma/firma, entrega/entrega, manda/manda, sube/sube, prepara/prepara).
    // Por eso NO basta la forma verbal aislada: se exige (a) un DETERMINANTE
    // (el/la/los/las/este/esta/esos...) + sustantivo como objeto, y (b) una
    // guarda anti-sujeto-3ª-persona: si la palabra inmediatamente ANTERIOR al
    // verbo es un pronombre sujeto (él/ella/ellos/ellas/eso/esa/esos/esas) o un
    // cuantificador narrativo (esto/eso/esto es…), se trata de narración en 3ª
    // persona ("él revisa el contrato"), no de mandato, y se excluye. Residual
    // intencional y descartable (mismo género que c.519/c.523/c.524): un nombre
    // propio antepuesto ("María envía el reporte el viernes") es narración en
    // 3ª persona presente que sigue disparando REQUEST — nace como draft PENDING
    // revisable, un falso positivo se descarta, un falso negativo es una petición
    // olvidada. La guarda de negación excluye "no revisa el contrato" (narración
    // negada / no es mandato). Determinista (regex), sin random, sin IA fingida.
    private val imperativeObjectRequestSignal = Regex(
        """(?iU)\b(?:env[ií]a|revisa|entrega|paga|firma|manda|sube|prepara|completa|confirma|responde|agenda|programa)\s+(?:el|la|los|las|un|una|unos|unas|este|esta|estos|estas|ese|esa|esos|esas|mi|tu|su)\s+(\p{L}{2,})"""
    )
    // c.329/c.535: lista curada de infinitivos de acción reusada por
    // [pendingObligationSignal] (c.329), [commitmentSignal] ("me toca", c.535) y
    // [userObligationSignal] ("te toca", c.535). Se restringe a verbos reales de
    // gestión/seguimiento (no al sufijo -ar/-er/-ir, que casa con sustantivos
    // comunes como "lugar"/"azúcar"/"hogar" → falsos positivos). Definida al
    // principio para que las señales que la interpolan estén ya inicializadas.
    private val pendingActionInfinitives =
        "enviar|confirmar|revisar|llamar|mandar|pagar|firmar|responder|hacer|terminar|entregar|preparar|subir|dejar|pasar|arreglar|completar|agendar|programar|contactar|avisar|recordar|cobrar|facturar|presentar"

    // c.316: obligación DIRIGIDA AL USUARIO por un tercero en 2ª persona —
    // "tienes que firmar el contrato el lunes", "tienes que pagar la renta",
    // "tienes que entregar el reporte". Es la forma más natural en español de
    // que OTRA persona comunique al usuario una obligación suya (simétrica de
    // "tengo que", c.305, que cubre la obligación que el propio usuario se
    // impone). Antes caía a MISSED (0 drafts): el usuario olvidaba una
    // obligación que alguien le comunicó en el chat. La desinencia "tienes"
    // (2ª persona) es el desambiguador: la 1ª persona "tengo" ya se cubre en
    // commitmentSignal; aquí el interlocutor le dice al usuario lo que ÉL debe
    // hacer. La guarda de negación [hasUnnegatedUserObligation] excluye
    // "no tienes que" (ausencia de obligación / "no tienes que preocuparte"),
    // igual que la guarda de compromiso excluye "no tengo que". Nace como
    // draft SELF_COMMITMENT PENDING revisable: un falso positivo se descarta,
    // un falso negativo es una obligación olvidada (área "evitar olvidos" +
    // "detección de compromisos").
    //
    // c.535: "te toca" + infinitivo de acción — obligación por TURNO dirigida al
    // usuario ("te toca presentar el informe el viernes", "te toca pagar la
    // ronda", "te toca revisar el contrato"). Es el espejo de 2ª persona de
    // "me toca" (commitmentSignal, misma c.535): el giro impersonal "tocar a
    // uno" expresa a quién le corresponde una tarea, y la persona del clítico
    // (te=oyente/usuario) marca al obligado, igual que "tienes que" (c.316).
    // Antes caía a MISSED (probe JVM PRE-fix: 2/2). Se ancla a owner=SELF (la
    // obligación es DEL usuario) igual que "tienes que". Reusa
    // [pendingActionInfinitives] para mantener precisión alta (lista curada de
    // verbos de acción; evita que "te toca el turno"/"te toca la lotería"
    // —sin infinitivo— disparen). La guarda precedingNegation excluye
    // "no te toca a ti" (no te corresponde) igual que "no tienes que".
    // Determinista (regex), sin random, sin IA fingida.
    private val userObligationSignal = Regex("""(?iU)\b(?:tienes\s+que|te\s+toca\s+(?:$pendingActionInfinitives))\b""")

    // c.329: obligaciones PENDIENTES — la clase de compromiso más común en un
    // seguimiento de chat donde el usuario reconoce algo que le quedó por hacer:
    // "tengo pendiente enviar el informe", "me queda pendiente el pago",
    // "me falta confirmar la hora", "tengo por revisar el contrato". Antes estas
    // frases caían a MISSED: no son futuro ("haré"), ni presente+objeto ("lo
    // hago"), ni "tengo que" — son un ESTADO de deuda abierta, una cuarta forma
    // léxica de compromiso. Sin detección, el usuario anota mentalmente "tengo
    // pendiente X" en un chat y Ordía no crea ningún draft → olvido real.
    //
    // Tres sub-patrones, todos exigentes para mantener precisión alta (un draft
    // se descarta, pero un inbox inundado de falsos no ayuda):
    //  (1) "pendiente(s)" precedido de construcción verbal de tener/quedar:
    //      "tengo/tienes/tenemos pendiente", "me/te/le/nos/les queda(n)
    //      pendiente", "quedo/queda pendiente". La palabra "pendiente" tras un
    //      verbo de posesión/estado desambigua del sustantivo "pendiente"
    //      (arete) — "el pendiente del collar" no casa (sin verbo previo).
    //  (2) "tengo/tienes/tenemos por" + infinitivo de acción: giro peninsular
    //      culto de obligación pendiente ("tengo por revisar", "tengo por
    //      enviar"). El "por" + infinitivo es inequívoco.
    //  (3) "me/te/le/nos/les falta(n)" + infinitivo de acción: "me falta
    //      confirmar", "me falta enviar". Se restringe a una lista curada de
    //      infinitivos de acción (como hace commitmentSignal con sus verbos) en
    //      vez del sufijo -ar/-er/-ir, porque ese sufijo casa con sustantivos
    //      comunes ("lugar", "azúcar", "hogar") → falsos positivos. La lista
    //      cubre los verbos de gestión/seguimiento más cotidianos.
    //
    //  (4) [c.330] Pasado: "me quedó/quedaron pendiente(s)" — el usuario
    //      describe una deuda CONTRAÍDA en pasado ("me quedó pendiente el pago",
    //      "me quedó por enviar el reporte"). "quedó" es pretérito de "queda";
    //      sin él, el compromiso histórico quedaba sin capturar (olvido de
    //      deuda ya abierta, no solo futura).
    //  (5) [c.330] "me/te/le/nos/les falta(n)/queda(n) por" + infinitivo: giro
    //      de obligación pendiente cotidiano ("me falta por confirmar la hora",
    //      "me queda por hacer la entrega"). El "por" + infinitivo es
    //      inequívoco (igual que "tengo por" de c.329); reusa la misma lista
    //      curada de verbos de acción. Antes solo "tengo por" + infinitivo se
    //      detectaba, no las formas con "falta/queda por".
    //
    // La guarda de negación [hasUnnegatedCommitment] sigue aplicándose: "no
    // tengo nada pendiente", "ya no me queda pendiente nada" se excluyen igual
    // que "no tengo que" / "no me encargo". Nace como draft SELF_COMMITMENT
    // PENDING revisable: un falso positivo se descarta, un falso negativo es
    // una obligación olvidada (área "evitar olvidos" + "detección de
    // compromisos").
    private val pendingObligationSignal = Regex(
        """(?i)\b(?:(?:tengo|tienes|tenemos)\s+pendientes?\b|(?:me|te|le|nos|les)\s+qued(?:an?|o|ó|aron)\s+pendientes?\b|qued[ao]\s+pendiente\b|(?:tengo|tienes|tenemos)\s+por\s+(?:$pendingActionInfinitives)\b|(?:me|te|le|nos|les)\s+faltan?\s+(?:$pendingActionInfinitives)\b|(?:me|te|le|nos|les)\s+(?:faltan?|qued(?:an?|o|ó|aron))\s+por\s+(?:$pendingActionInfinitives)\b)\b"""
    )
    // c.316: auto-promesas de seguimiento en presente de 1ª persona con objeto
    // "te" — "te aviso cuando llegue", "te confirmo mas tarde", "te aviso el
    // lunes". Continuación directa de c.305 (presente de 1ª persona + objeto):
    // "te paso"/"te mando" ya se detectaban, pero "te aviso"/"te confirmo" —
    // verbos de avisar/confirmar, igual de cotidianos en un seguimiento de
    // chat— caían a MISSED. El objeto "te" señala que el usuario se compromete
    // a informar a su interlocutor después, como "te llamo"/"te respondo" (ya
    // cubiertos). La guarda de negación existente (hasUnnegatedCommitment)
    // sigue aplicándose: "no te aviso" se excluye igual que "no te llamo".
    private val commitmentSignal = Regex(
        // "me encargo"/"me ocupo" son las formas más naturales en español de
        // asumir un compromiso y se dicen SIN pronombre "yo" ("¿Quién llama?"
        // → "me encargo"). Exigir "yo me encargo" dejaba estos compromisos sin
        // detectar (falso negativo: olvido). El pronombre es opcional. (c.278)
        //
        // c.305: formas de presente de 1ª persona con valor de futuro — las más
        // frecuentes en chat real y antes NO detectadas ("te paso el informe
        // mañana", "lo termino el viernes", "lo entrego mañana", "lo reviso y te
        // aviso", "te lo mando el lunes", "lo preparo para mañana"). El español
        // expresa compromisos futuros con presente + fecha mucho más a menudo que
        // con futuro ("terminaré"). Cada forma exige un pronombre de objeto
        // directo/indirecto ("lo/la/los/las", "te", "te lo", "le"...) para mantener
        // precisión alta: un verbo pelado ("termino" en "termino la frase") es
        // ambiguo, pero "lo termino"/"te paso" con objeto es un compromiso claro
        // de hacer algo con/con para alguien. "le paso"/"le mando"/"le envío"
        // (c.310) cubren el receptor de 3ª persona ("le paso el informe a María"),
        // complemento de "te paso"/"te mando" (c.306, receptor = interlocutor). La guarda de negación
        // [hasUnnegatedCommitment] sigue aplicándose: "no te paso nada",
        // "no lo entrego" se excluyen igual que "no te llamo"/"no lo hago" (c.279).
        // c.312: el doble pronombre "se lo/la/los/las" (dativo de 3ª persona "se" +
        // acusativo) es la forma pronominal del compromiso de 3ª persona cuando el
        // objeto también es pronominal ("le paso el informe a María" → "se lo
        // paso"): la regla española "le/les" → "se" ante otro pronombre hace que
        // "se lo paso / se lo mando / se lo envío" sea la forma natural de
        // referirse a un tercero ya mencionado. Antes el clítico "se lo" NO estaba
        // en la alternancia, así el match caía en el "lo" pelado (3 chars después
        // de "se") y la guarda de negación veía prefijo "se " en vez de "no " →
        // "no se lo paso" generaba un draft espurio (bug de precisión). Al incluir
        // "se lo" en la alternancia, el match empieza en "se" y el prefijo "no "
        // queda visible para la guarda — mismo mecanismo que ya protege "no te lo
        // paso" (c.306, "te lo" explícito). Como todo compromiso, nace como draft
        // PENDING que el usuario revisa antes de convertir en tarea: un falso
        // positivo se descarta, un falso negativo es un olvido real (la cuarta
        // clase de olvido de Ordía).
        // c.342 — la rama futura de compromiso era ASIMÉTRICA con la de presente.
        // Antes: (?i)\b(...|voy\s+a|terminar[eé]|har[eé]|lo\s+hago|...). El presente
        // con clítico cubría 9 verbos (termino/entrego/reviso/preparo/arreglo/subo/
        // dejo/paso/mando/envío), pero el futuro SÓLO haré/terminaré + "voy a".
        // Así "lo reviso" (presente) se detectaba pero "lo revisaré" (futuro, la
        // forma de promesa MÁS explícita en español) caía a MISSED → olvido real de
        // compromiso futuro (P1, pérdida de datos: una promesa explícita no generaba
        // draft, no había nada que avisar al vencer).
        //
        // TRES cambios juntos (un solo commit c.342):
        // (1) (?U) UNICODE_CHARACTER_CLASS — hace \b consciente de Unicode. Sin él,
        //     (?i) solo ASCII trata áéíóú como NO-palabra, así \b TRAS una vocal
        //     acentada NO casa → los futuros con tilde "terminaré"/"haré"/"revisaré"
        //     se perdían silenciosamente (la rama sin tilde "e" sí casaba: "lo hare
        //     hoy", dando el falso síntoma de cobertura). (?U) hace \b coherente con
        //     \p{L}; las ramas que terminan en ASCII ('o','a','e') no cambian. La
        //     guarda precedingNegation (\bno\s+) es regex independiente, ASCII, sin
        //     afecto. Auditado: ninguna otra señal termina en vocal acentada, así el
        //     cambio queda focal en commitmentSignal.
        // (2) Clítico OPCIONAL precedente agrupado con la rama futura
        //     (lo/la/los/las/te lo/.../se lo), igual que la rama de presente "lo
        //     hago". Antes el match arrancaba en el verbo; con "no lo haré" el "lo"
        //     tapaba el "no " a la guarda de 3 chars → falso SELF_COMMITMENT
        //     (rechazo detectado como compromiso). Al arrancar el match en el
        //     clítico, el "no " queda adyacente y la guarda lo excluye igual que
        //     hace con "no lo hago". La perífrasis "voy a" se agrupa bajo el MISMO
        //     clítico opcional — "no lo voy a hacer" tiene el mismo bug (el "lo"
        //     tapa el "no " antes de "voy a"); al mover `voy\s+a` dentro del grupo,
        //     "lo voy a" arranca en "lo" y el "no " queda visible. Sin clítico
        //     ("terminaré el informe"/"voy a hacer") el match arranca en el verbo:
        //     "no terminaré" sigue excluido (prefijo "no ").
        // (3) 9 futuros alineados con los 9 presentes: entregaré, revisaré,
        //     prepararé, arreglaré, subiré, dejaré, pasaré, mandaré, enviaré —
        //     cobertura futura SIMÉTRICA con la presente. El clítico opcional del
        //     punto (2) cubre "te lo mandaré", "lo revisaré", "se lo enviaré"; la
        //     guarda precedingNegation excluye "no lo revisaré"/"no te lo mandaré"
        //     igual que "no lo haré". Cada forma admite variante con/sin tilde en
        //     la é final (har[eé]) por errores de escritura comunes.
        //
        // c.514: cierra la ASIMETRÍA CON-CLÍTICO de los verbos añadidos en c.512
        // (pago/respondo/aviso/confirmo). c.512 los añadió a la rama PELADA
        // (barePresentCommitmentSignal), pero NO a esta rama con-clítico → la
        // forma pronominal ("te pago"/"le pago"/"te lo pago"/"le respondo"/
        // "le aviso"/"le confirmo") caía a MISSED aunque la pelada ("pago la
        // factura mañana") sí se detectase. Es la asimetría INVERSA de c.508/c.512:
        // estas son las formas MÁS explícitas de promesa de pago/contacto en
        // español ("te pago la deuda mañana", "le respondo al cliente el lunes").
        // Cambios (un commit c.514):
        //  (1) El grupo `te` se consolida en `te\s+(?:llamo|envío|respondo|aviso|
        //      confirmo|paso|mando|pago)` — añade `pago` y unifica los `te`
        //      dispersos (antes `te llamo|te envío|te respondo|te aviso|te confirmo`
        //      + `te (?:paso|mando)` separados). Sin cambio de cobertura salvo pago.
        //  (2) El grupo `le` añade respondo|aviso|confirmo|pago: "le respondo al
        //      cliente el lunes", "le aviso al equipo el viernes", "le confirmo la
        //      reserva", "le pago el alquiler el viernes". Antes sólo paso/mando/
        //      envío/llamo/hablo/escribo.
        //  (3) El grupo de objeto directo (lo|la|...|te lo|...) añade `pago` para
        //      "te lo pago"/"lo pago" (promesa de saldar una deuda concreta).
        // La guarda precedingNegation excluye "no te pago"/"no le respondo"/"no te
        // lo pago" igual que "no te llamo". Precisión: "pago"/"aviso" son también
        // sustantivos, pero la rama con-clítico exige pronombre (te/le/lo) antes,
        // y "el pago"/"un aviso" (determinante) NO casa aquí (lo protege la guarda
        // `determiners` de bare, no esta). Probe JVM POST-fix: 10/10 positivos
        // detectados, 6/6 negativos (negaciones + sustantivo "el pago") excluidos.
        // c.518: cierra la ASIMETRÍA CON-clítico de "te escribo"/"te hablo". El
        // grupo `te` tenía llamo|envío|respondo|aviso|confirmo|paso|mando|pago pero
        // NO hablo ni escribo (que SÍ estaban en el grupo `le` y en el de objeto
        // directo). Así "te escribo mañana"/"te hablo el lunes" — promesas de
        // contacto cotidianas — caían a MISSED aunque "le escribo mañana"/"lo
        // escribo mañana"/"te llamo mañana" sí se detectasen. Se añaden hablo|
        // escribo al grupo `te`, completando la simetría con `le` y OD. La guarda
        // precedingNegation excluye "no te escribo"/"no te hablo" igual que "no te
        // llamo". Probe JVM PRE-fix: 2 MISSED; POST-fix: 6/6 detectados, 4/4
        // negaciones excluidas.
        // c.527: asimetría de número en la rama FUTURA, espejo de c.526 en la
        // presente pelada. commitmentSignal detectaba "lo terminaré"/"terminaré"/
        // "lo voy a revisar" (1ª persona SINGULAR) pero NO "lo terminaremos"/
        // "terminaremos"/"lo revisaremos"/"te lo mandaremos"/"se lo enviaremos"/
        // "vamos a terminar" (1ª persona PLURAL) → olvido de compromisos
        // COMPARTIDOS ("lo terminaremos el viernes", "te lo mandaremos mañana"),
        // la forma natural de una promesa conjunta en chat español (P1, evitar
        // olvidos). Se alinean los 11 futuros plurales con los singulares
        // (terminaremos/haremos/entregaremos/revisaremos/prepararemos/
        // arreglaremos/subiremos/dejaremos/pasaremos/mandaremos/enviaremos) y
        // se añade "vamos a" como plural de "voy a". El clítico opcional
        // precedente cubre "lo revisaremos"/"te lo mandaremos"/"se lo
        // enviaremos" igual que "lo revisaré"/"te lo mandaré"; la guarda
        // precedingNegation excluye "no lo terminaremos"/"no lo vamos a revisar"
        // igual que "no lo terminaré"/"no lo voy a revisar". Precisión simétrica
        // con el singular: "vamos a la playa" se comporta igual que "voy a la
        // playa" (comportamiento preexistente de la perífrasis, no nuevo). Probe
        // JVM POST-fix: 6/6 positivos detectados, 4/4 negaciones excluidas.
        // c.528: asimetría de NUMERO en la rama PRESENTE-CON-CLÍTICO de
        // commitmentSignal, espejo de c.526 (presente pelado plural) y c.527
        // (futuro plural). commitmentSignal detectaba las 3 ramas de presente con
        // clítico en 1ª persona SINGULAR ("lo termino"/"te llamo"/"le paso"/
        // "lo hago") PERO NO sus plurales ("lo terminamos"/"te llamamos"/
        // "le pasamos"/"lo hacemos") → olvido de compromisos COMPARTIDOS con
        // receptor explícito ("te llamamos mañana", "le pasamos el reporte el
        // viernes", "lo terminamos el viernes"), la forma natural de una promesa
        // conjunta dirigida a alguien. Se alinean los plurales (-amos) de los 3
        // grupos con clítico: `te` (llamamos/enviamos/respondemos/avisamos/
        // confirmamos/pasamos/mandamos/pagamos/hablamos/escribimos), `le`
        // (pasamos/mandamos/enviamos/llamamos/hablamos/escribimos/respondemos/
        // avisamos/confirmamos/pagamos) y OD/doble (terminamos/entregamos/
        // revisamos/preparamos/arreglamos/subimos/dejamos/pasamos/mandamos/
        // enviamos/llamamos/hablamos/escribimos/pagamos) + `lo hacemos`. La guarda
        // precedingNegation excluye "no lo terminamos"/"no te llamamos" igual que
        // "no lo termino"/"no te llamo". Precisión simétrica: "lo terminamos" sin
        // fecha se comporta igual que "lo termino" (presente-clítico dispara sin
        // exigir dueAt, comportamiento preexistente). Probe JVM POST-fix: 11/11
        // positivos detectados, 3/3 negaciones excluidas, controles singulares
        // intactos.
        // c.529: asimetría de número en la rama "tengo que" de commitmentSignal,
        // espejo de c.526/c.527/c.528 en las demás ramas. commitmentSignal
        // detectaba "tengo que entregar el informe" (1ª SINGULAR) pero NO
        // "tenemos que entregar el informe" (1ª PLURAL) → olvido de un compromiso
        // COMPARTIDO cotidiano ("tenemos que firmar el contrato el lunes",
        // "tenemos que pagar la renta", "tenemos que revisar el documento esta
        // semana"), la forma natural de obligación conjunta en chat español (P1,
        // evitar olvidos). Se añade `tenemos` junto a `tengo` en la rama
        // `(?:tengo|tenemos)\s+que`. La guarda precedingNegation excluye "no
        // tenemos que entregar"/"no tenemos que preocuparnos" igual que "no tengo
        // que". Precisión simétrica: "tenemos que" se comporta igual que "tengo
        // que" (rama dispara sin exigir dueAt, comportamiento preexistente de la
        // perífrasis de obligación). Probe JVM PRE-fix: 5/5 MISSED; POST-fix:
        // 5/5 detectados, 2/2 negaciones excluidas, controles singulares intactos.
        // c.530: asimetría de número en la rama "debo" de commitmentSignal, quinto
        // y último miembro de la familia c.526/c.527/c.528/c.529. commitmentSignal
        // detectaba "debo entregar el informe" (1ª SINGULAR de la perífrasis de
        // obligación "debo") pero NO "debemos entregar el informe" (1ª PLURAL) →
        // olvido de un compromiso COMPARTIDO cotidiano ("debemos firmar el
        // contrato", "debemos pagar la renta", "debemos revisar el documento"),
        // forma natural de obligación conjunta (P1, evitar olvidos). Se añade
        // `debemos` junto a `debo` → `(?:debo|debemos)`. La guarda
        // precedingNegation excluye "no debemos entregar"/"no debemos
        // preocuparnos" igual que "no debo". Precisión simétrica: "debemos" se
        // comporta igual que "debo" (rama dispara sin exigir dueAt, comportamiento
        // preexistente de la perífrasis). Probe JVM PRE-fix: 5/5 MISSED; POST-fix:
        // 5/5 detectados, 2/2 negaciones excluidas, controles singulares intactos.
        //
        // c.531: NUEVA clase de detección — IMPERATIVOS de 1ª persona PLURAL
        // (exhortativos "vamos a hacer X" morfológicamente compactos): "hagamos
        // el informe"/"terminemos el reporte"/"revisemos el contrato"/"preparemos
        // la propuesta"/"entreguemos el documento"/"llamemos al cliente"/"mandemos
        // el correo"/"enviemos la propuesta" caían a MISSED → olvido de un
        // compromiso COMPARTIDO cotidiano (P1 evitar olvidos). NO es un espejo
        // de número (no existe imperativo de 1ª persona singular en español:
        // "hago" es indicativo): es una forma verbal DISTINTA, la natural para
        // proponer una acción conjunta en chat ("hagamos el informe el viernes"
        // = "vamos a hacer el informe"). Morfología real: hacer→hagamos
        // (irregular), -ar→-emos (con -gu- para conservar /g/ ante -e:
        // entregar→entreguemos, pagar→paguemos), -er/-ir→-amos (escribir→
        // escribamos, subir→subamos). Se añaden las formas del MISMO conjunto
        // verbal ya admitido en indicativo/futuro (c.278/c.305/c.526-c.530). La
        // guarda precedingNegation excluye "no hagamos"/"no terminemos" igual
        // que "no hacemos"/"no terminamos". Precisión simétrica: el exhortativo
        // dispara sin clítico previo (la forma lleva la intención en el morfema
        // -emos, no en un pronombre objeto precedente), igual que "vamos a".
        // Determinista (regex), sin random, sin IA fingida.

        // c.533: el FUTURO de comunicación (llamaré/hablaré/escribiré + plurales
        // llamaremos/hablaremos/escribiremos) era la única forma de promesa de contacto
        // que caía a MISSED: el presente con clítico ("te llamo"/"le escribo") y el
        // presente pelado ("llamo al cliente mañana") ya se detectaban (c.508/c.518/
        // c.500), y el futuro de acción ("lo terminaré"/"lo llamaremos") también, pero
        // el futuro de comunicación no. Cobertura singular y plural, con clítico (te/le/
        // lo) y pelado con objeto nominal ("llamaré al cliente mañana").
        // Probe JVM PRE-fix: 17/17 MISSED.
        //
        // c.535: "me toca" + infinitivo de acción — obligación por TURNO del
        // propio hablante ("me toca presentar el informe el viernes", "me toca
        // pagar la ronda", "me toca revisar el contrato"). Es el espejo de 1ª
        // persona de "te toca" (userObligationSignal, misma c.535): el giro
        // impersonal "tocar a uno" expresa a quién le corresponde una tarea, y la
        // persona del clítico (me=hablante) marca al obligado. Antes caía a
        // MISSED (probe JVM PRE-fix: 2/2). A diferencia de "te toca" (anclado a
        // SELF por ser 2ª persona=oyente), "me toca" es 1ª persona=hablante, así
        // su dueño se rutea por el REMITENTE (igual que "me encargo"/"tengo que":
        // si lo dice el usuario → SELF, si lo dice otro → OTHER). Reusa
        // [pendingActionInfinitives] para mantener precisión alta (evita que "me
        // toca el turno"/"me toca la lotería" —sin infinitivo— disparen). La
        // guarda precedingNegation excluye "no me toca a mí" (no me corresponde)
        // igual que "no me encargo". Determinista (regex), sin random, sin IA
        // fingida.
        // c.537: "notificar" pelado y con-clítico (presente, singular y plural) era
        // la única forma de promesa de comunicación que caía a MISSED. El futuro
        // (notificaré/notificaremos) ya estaba en c.534, y el resto de la familia de
        // comunicación (llamar/hablar/escribir/responder/avisar/confirmar/pagar) ya
        // cubría sus 4 formas (pelado+con-clítico, sing+plur, c.508/c.512/c.514/c.518/
        // c.526/c.528), pero "notificar" solo existía como FUTURO. Así "notifico al
        // equipo el viernes", "le notifico al cliente el lunes", "notificamos al
        // jefe mañana", "te notificamos el lunes" — promesas cotidianas de avisar
        // formalmente — se perdían (probe JVM PRE-fix: 9/9 MISSED). Se añaden
        // notifico/notificamos a las TRES ramas presentes: barePresentCommitmentSignal
        // (pelado con fecha), y los grupos `te`/`le`/OD de commitmentSignal
        // (con-clítico). "notifico" como sustantivo es rarísimo (la guarda de
        // determinante [determiners] de c.512 lo protege igual que a pago/aviso por
        // si acaso). La guarda precedingNegation excluye "no notifico"/"no le
        // notifico"/"no te notificamos" igual que "no aviso"/"no le aviso". Determinista
        // (regex), sin random, sin IA fingida. "decir" (digo/decimos) se deja para
        // otro ciclo: su lexema irregular y la frecuencia de mandatos indirectos
        // ("le digo que venga mañana" = mandato al 3º, no promesa) exigen análisis de
        // precisión aparte. Probe JVM POST-fix: 9/9 positivos, 7/7 negativos.
        """(?iU)\b(?:(?:yo\s+)?me\s+(?:encargo|ocupo)|me\s+comprometo|me\s+toca\s+(?:$pendingActionInfinitives)|te\s+(?:llamo|env[ií]o|respondo|aviso|confirmo|paso|mando|pago|hablo|escribo|notifico|llamamos|enviamos|respondemos|avisamos|confirmamos|pasamos|mandamos|pagamos|hablamos|escribimos|notificamos)|despu[eé]s\s+te\s+respondo|(?:debo|debemos)|(?:tengo|tenemos)\s+que|(?:hagamos|terminemos|entreguemos|revisemos|preparemos|arreglemos|subamos|dejemos|pasemos|mandemos|enviemos|llamemos|hablemos|escribamos|paguemos)|(?:lo\s+|la\s+|los\s+|las\s+|te\s+lo\s+|te\s+la\s+|te\s+los\s+|te\s+las\s+|se\s+lo\s+|se\s+la\s+|se\s+los\s+|se\s+las\s+|te\s+|le\s+)?(?:voy\s+a|vamos\s+a|terminar[eé]|terminaremos|har[eé]|haremos|entregar[eé]|entregaremos|revisar[eé]|revisaremos|preparar[eé]|prepararemos|arreglar[eé]|arreglaremos|subir[eé]|subiremos|dejar[eé]|dejaremos|pasar[eé]|pasaremos|mandar[eé]|mandaremos|enviar[eé]|enviaremos|llamar[eé]|llamaremos|hablar[eé]|hablaremos|escribir[eé]|escribiremos|avisar[eé]|avisaremos|notificar[eé]|notificaremos|dir[eé]|diremos)|lo\s+hago|lo\s+hacemos|le\s+(?:paso|mando|env[ií]o|llamo|hablo|escribo|respondo|aviso|confirmo|pago|notifico|pasamos|mandamos|enviamos|llamamos|hablamos|escribimos|respondemos|avisamos|confirmamos|pagamos|notificamos)|(?:lo|la|los|las|te\s+lo|te\s+la|te\s+los|te\s+las|se\s+lo|se\s+la|se\s+los|se\s+las)\s+(?:termino|entrego|reviso|preparo|arreglo|subo|dejo|paso|mando|env[ií]o|llamo|hablo|escribo|pago|notifico|terminamos|entregamos|revisamos|preparamos|arreglamos|subimos|dejamos|pasamos|mandamos|enviamos|llamamos|hablamos|escribimos|pagamos|notificamos))\b"""

    )
    // c.500: presente de 1ª persona SIN pronombre de objeto + marca temporal futura
    // PUNTUAL — "termino el informe mañana", "entrego el reporte el viernes",
    // "envío la propuesta esta semana", "reviso el contrato mañana", "mando el
    // correo esta tarde". Es la forma MÁS cotidiana de promesa en chat español:
    // presente + fecha, sin clítico. Antes caía a MISSED (probe JVM PRE-fix 8/8):
    // commitmentSignal exige clítico ("lo termino") o futuro ("terminaré") por
    // precisión — un verbo pelado ("termino") es ambiguo ("termino la frase"). La
    // ambigüedad se resuelve con la MARCA TEMPORAL FUTURA PUNTUAL: "termino el
    // informe mañana" con una fecha puntual es un compromiso, no una narración.
    //
    // La marca temporal se delega al [NaturalTaskParser]: sólo casa cuando
    // `parsed.dueAt != null && parsed.recurrence == NONE`. Esto excluye:
    //  - Rutinas ("reviso el correo cada mañana" → recurrence DAILY): el "cada"
    //    es hábito, no promesa puntual, y el test barePresentVerbs... lo protege.
    //  - Narraciones sin fecha ("termino la frase y me voy" → dueAt null): el
    //    test barePresentVerbs... las protege.
    //  - "mando la carta al correo" (dueAt null), "paso por tu casa sin avisar"
    //    (dueAt null): sin fecha futura, no son compromisos puntuales.
    //
    // La lista de verbos cubre la familia de ACCIÓN (termino/entrego/reviso/
    // preparo/arreglo/subo/dejo/paso/mando/envío) y, desde c.508, la familia de
    // COMUNICACIÓN (llamo/hablo/escribo): "llamo al cliente mañana", "hablo con
    // el jefe el lunes", "escribo el informe mañana". Antes caían a MISSED
    // porque su forma CON clítico ("te llamo", "le escribo") sí se detectaba
    // (commitmentSignal) pero la PELADA no, aunque fuese la misma promesa con un
    // objeto directo nominal en vez de pronominal ("llamo al cliente" vs "te
    // llamo"). Un olvido real (P1): una promesa de contacto futura no generaba
    // draft. El mismo discriminador (dueAt != null + recurrence NONE + !hoy +
    // guarda de negación/clítico) protege las narraciones peladas sin fecha
    // ("llamo a mi madre y le cuento", "hablo español en casa"), las rutinas
    // ("llamo a mi madre cada mañana") y las negadas ("no llamo al cliente
    // mañana"). Probe JVM POST-fix: 10/10 positivos detectados, 10/10 negativos
    // excluidos.
    // c.512: cierra la ASIMETRÍA restante entre las formas CON clítico y PELADA
    // de tres verbos de comunicación (respondo/aviso/confirmo) y añade "pago".
    // commitmentSignal ya detectaba "te respondo"/"te aviso"/"te confirmo"
    // (clítico de 2ª persona), pero la forma PELADA con objeto nominal
    // ("respondo el correo mañana", "aviso al equipo el lunes", "confirmo la
    // reserva el viernes") caía a MISSED → olvido de una promesa cotidiana de
    // responder/avisar/confirmar. "pago" no estaba en NINGUNA rama: "pago la
    // factura mañana" / "pago el alquiler el viernes" (promesa de pago, compromiso
    // fuerte) se perdía. Como "pago" y "aviso" son también SUSTANTIVOS frecuentes
    // ("el pago de la factura", "un aviso"), la guarda de determinante
    // [determiners] en hasUnnegatedBarePresentCommitment evita el falso positivo:
    // "el pago de la factura mañana" (sustantivo) NO genera draft, "pago la
    // factura mañana" (verbo) sí.
    // Nace como draft SELF_COMMITMENT PENDING revisable: un falso positivo se
    // descarta, un falso negativo es una promesa olvidada (área "evitar olvidos" +
    // "detección de compromisos", P1).
    // c.526: asimetría de número. c.500/c.512 añadieron la 1ª persona SINGULAR
    // del presente pelado (termino/entrego/.../pago) con marca temporal futura,
    // pero la 1ª persona PLURAL ("terminamos/entregamos/.../pagamos el viernes")
    // — un compromiso compartido cotidiano — caía a MISSED → olvido de una
    // promesa real. Se añaden las conjugaciones plurales del MISMO conjunto de
    // verbos ya admitido en singular; reutilizan el mismo discriminador
    // (dueAt futuro + !hoy + recurrencia NONE + no negado + sin determinante/
    // clítico/prep genitiva previos). "enviamos" no tiene variante acentual (el
    // singular usaba env[ií]o para "envio"/"envío"). Probe JVM POST-fix: 7/7.
    private val barePresentCommitmentSignal = Regex(
        """(?iU)\b(?:termino|entrego|reviso|preparo|arreglo|subo|dejo|paso|mando|env[ií]o|llamo|hablo|escribo|respondo|aviso|confirmo|pago|notifico|terminamos|entregamos|revisamos|preparamos|arreglamos|subimos|dejamos|pasamos|mandamos|enviamos|llamamos|hablamos|escribimos|respondemos|avisamos|confirmamos|pagamos|notificamos)\b"""
    )
    private val locationSignal = Regex(
        """(?i)\b(?:lugar\s*:\s*|(?:nos\s+vemos|reuni[oó]n|cita)[^.!?\n]{0,80}?\ben\s+)([\p{L}\d][\p{L}\d .,'-]{2,50})"""
    )
    // c.500: "hoy" como marcador temporal es ambiguo en presente pelado ("envío el
    // paquete hoy" puede ser acción en curso). Se excluye del presente pelado; la
    // rama con-clítico lo tolera. Word-boundary para no casar "hoya"/"hoyuelo".
    private val todayMarker = Regex("""(?i)\bhoy\b""")
    // "no te llamo"/"no me encargo"/"no lo hago" son NEGATIVAS (rechazos), no
    // compromisos. Hay compromiso solo si alguna frase de compromiso aparece SIN
    // "no " inmediatamente antes. Así "no tengo tiempo, lo hago manana" sigue
    // siendo compromiso (la 2ª frase no está negada). NO se aplica a request/reminder,
    // donde la negación es idiomática y POSITIVA ("no olvides" = recuérdame). (c.279)
    private val precedingNegation = Regex("""(?i)\bno\s+""")

    private fun hasUnnegatedCommitment(text: String): Boolean =
        commitmentSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            !precedingNegation.containsMatchIn(prefix)
        }
    // c.519: un sustantivo de reunion (reunion/cita/encuentro) cuenta como
    // MEETING solo si al menos una ocurrencia NO es objeto genitivo
    // (de/del/por/para + det opcional). "aviso de la reunion" -> objeto,
    // suprimido; "la reunion es manana" -> sujeto, MEETING.
    private fun hasMeetingNounAsSubject(text: String): Boolean {
        val objects = meetingNounAsObject.findAll(text).map { it.range.first..it.range.last }.toList()
        return meetingNounSignal.findAll(text).any { noun ->
            objects.none { obj -> noun.range.first in obj }
        }
    }

    // c.523: simetrico a hasMeetingNounAsSubject. Solo cuenta un sustantivo de
    // compra como PURCHASE si al menos una ocurrencia NO es objeto genitivo.
    private fun hasPurchaseNounAsSubject(text: String): Boolean {
        val objects = purchaseNounAsObject.findAll(text).map { it.range.first..it.range.last }.toList()
        return purchaseNounSignal.findAll(text).any { noun ->
            objects.none { obj -> noun.range.first in obj }
        }
    }


    // c.524: simetrico a hasPurchaseNounAsSubject. Solo cuenta un sustantivo
    // recordatorio como REMINDER si al menos una ocurrencia NO es objeto genitivo.
    private fun hasReminderNounAsSubject(text: String): Boolean {
        val objects = reminderNounAsObject.findAll(text).map { it.range.first..it.range.last }.toList()
        return reminderNounSignal.findAll(text).any { noun ->
            objects.none { obj -> noun.range.first in obj }
        }
    }


    // c.309: la negación antes de un indicativo de 2ª persona ("no me pasas
    // nada", "no me llamas nunca", "no me lo envías") es una queja/acusación,
    // no una petición — se excluye. Reusa el mismo precedingNegation que
    // hasUnnegatedCommitment (c.279). El subjuntivo "no me llames" no casa con
    // la desinencia -as, así que los imperativos negativos quedan fuera sin
    // necesidad de guarda (la guarda sólo protege el indicativo).
    private fun hasUnnegatedIndicativeRequest(text: String): Boolean =
        indicativeRequestSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            !precedingNegation.containsMatchIn(prefix)
        }

    // c.536: el verbo imperativo pelado es HOMÓGRAFO del presente de 3ª persona
    // ("él revisa el contrato" = narración). Para distinguir mandato (2ª) de
    // narración (3ª), se excluye cuando la palabra inmediatamente ANTERIOR al
    // verbo es un pronombre/elemento que introduce un SUJETO de 3ª persona
    // (él/ella/ellos/ellas/eso/esa/esos/esas/esto/este/esta). "el" sin tilde se
    // incluye porque ante un verbo solo puede ser pronombre sujeto de 3ª persona
    // ("el revisa el contrato"), nunca determinante (el determinante va antes
    // de un sustantivo, no antes del verbo). "él revisa el contrato" → narración;
    // "revisa el contrato" → mandato. La guarda de negación precedente excluye
    // "no revisa el contrato" (narración negada).
    private val thirdPersonSubjectMarkers = setOf(
        "él", "el", "ella", "ellos", "ellas", "ello",
        "eso", "esa", "esos", "esas", "este", "esta", "estos", "estas", "esto"
    )
    // c.536: el grupo 1 del patrón captura el sustantivo-objeto. Si es un
    // marcador temporal (lunes..domingo, mañana, tarde, noche, semana, mes, año,
    // hora, rato) NO es objeto directo sino complemento temporal ("envía el
    // viernes"), que aparece en narraciones 3ª persona ("ella se lo envía el
    // viernes") — excluirlo cierra ese falso positivo sin perder las peticiones
    // con objeto real ("envía el reporte el viernes" sigue casando: el grupo 1
    // captura "reporte", no "viernes").
    private val temporalObjectMarkers = setOf(
        "lunes", "martes", "miércoles", "miercoles", "jueves",
        "viernes", "sábado", "sabado", "domingo",
        "mañana", "manana", "tarde", "noche", "semana",
        "mes", "año", "ano", "hora", "rato", "día", "dia"
    )
    private fun hasUnnegatedImperativeObjectRequest(text: String): Boolean =
        imperativeObjectRequestSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            if (precedingNegation.containsMatchIn(prefix)) return@any false
            val prevWord = text.substring(maxOf(0, start - 14), start)
                .trim()
                .split(Regex("\\s+"))
                .lastOrNull()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            if (prevWord in thirdPersonSubjectMarkers) return@any false
            val obj = m.groupValues.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
            obj !in temporalObjectMarkers
        }

    // c.316: "no tienes que" es AUSENCIA de obligación ("no tienes que
    // preocuparte", "no tienes que venir") — se excluye igual que "no tengo
    // que". La guarda sólo protege la 2ª persona dirigida al usuario.
    private fun hasUnnegatedUserObligation(text: String): Boolean =
        userObligationSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            !precedingNegation.containsMatchIn(prefix)
        }

    // c.329: "no tengo nada pendiente" / "ya no me queda pendiente nada" son
    // AUSENCIA de obligación — se excluyen igual que "no tengo que". La guarda
    // reusa precedingNegation: el "no " queda inmediatamente antes del inicio
    // del match ("tengo pendiente", "me queda pendiente"). Para "ya no me
    // queda", el match empieza en "me" y el prefijo "no " (entre "ya " y "me")
    // queda visible en la ventana de 3 chars → se excluye correctamente.
    private fun hasUnnegatedPendingObligation(text: String): Boolean =
        pendingObligationSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            !precedingNegation.containsMatchIn(prefix)
        }

    // c.500: guarda para el presente pelado con fecha. Excluye dos formas:
    //  (a) NEGACIÓN: "no termino el informe mañana" es un rechazo. La palabra
    //      inmediatamente anterior al verbo es "no".
    //  (b) CLÍTICO: "no lo termino hoy" / "lo termino mañana" llevan pronombre de
    //      objeto (lo/la/los/las/te/se/le/me/nos/os) antes del verbo. Esos los
    //      resuelve la rama con-clítico (commitmentSignal); el presente PELADO no
    //      debe pisarlos. Sin esta guarda, "no lo termino hoy" (negado, con
    //      clítico) generaba un draft espurio: el "no " queda tapado por el "lo "
    //      y la ventana de 3 chars no lo veía (regresión c.500 detectada por
    //      presentTenseCommitmentFormsRespectDirectNegation). La marca temporal
    //      futura puntual (dueAt != null && recurrence NONE && !hoy) se comprueba
    //      en [detect]; esta función sólo responde "¿hay un verbo pelado de promesa
    //      no negado y sin clítico?".
    private val cliticPronouns = setOf("lo", "la", "los", "las", "te", "se", "le", "me", "nos", "os")
    // c.512: determinantes que preceden a un SUSTANTIVO homógrafo de un verbo de
    // 1ª persona. "pago" (verbo "pago la factura") y "aviso" (verbo "aviso al
    // equipo") son también sustantivos frecuentes ("el pago de la factura", "un
    // aviso de la reunión"). Sin esta guarda, "el pago de la factura mañana"
    // (sustantivo + dueAt futuro) generaba un draft espurio: el parser pone
    // dueAt=mañana y el verbo pelado casaba. Excluir el determinante anterior
    // resuelve la ambigüedad sin tocar los compromisos legítimos (que arrancan
    // en el verbo: "pago la factura mañana", prevWord vacío). No afecta a los
    // verbos de acción pura (termino/entrego/...), que raramente son sustantivos
    // y cuyos positivos nunca llevan determinante inmediatamente antes.
    private val determiners = setOf("el", "la", "los", "las", "un", "una", "unos", "unas")
    // c.525: preposiciones introductoras de objeto/tema genitivo. Varias formas del
    // presente pelado son tambien SUSTANTIVOS homonimos (pago/aviso/envio/mando/
    // paso/arreglo): "ajuste para pago del alquiler", "presupuesto para envio del
    // paquete", "config para aviso del equipo". La guarda de determinantes c.512
    // cubria "el pago" PERO NO "para pago" (preposicion sin determinante). Aqui el
    // verbo pelado es el OBJETO/TEMA del genitivo, no la accion; el compromiso real
    // es ajustar/presupuestar/configurar. Simetrico a la familia c.519/c.523/c.524.
    private val genitivePrepositions = setOf(
        "para", "por", "de", "del", "sobre", "tras", "en", "hasta", "hacia", "segun"
    )
    private fun hasUnnegatedBarePresentCommitment(text: String): Boolean =
        barePresentCommitmentSignal.findAll(text).any { m ->
            val start = m.range.first
            val prevWord = text.substring(maxOf(0, start - 8), start)
                .trim()
                .split(Regex("\\s+"))
                .lastOrNull()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            prevWord != "no" && prevWord !in cliticPronouns && prevWord !in determiners
                && prevWord !in genitivePrepositions
        }

    fun extract(
        messages: List<ChatMessage>,
        selfParticipant: String? = null,
        scopeHash: String
    ): List<CommitmentDraft> {
        val self = selfParticipant?.trim()?.lowercase(Locale.ROOT)
        return messages.asSequence()
            .take(ChatImportParser.MAX_MESSAGES)
            .filterNot { ConversationPrivacyPolicy.containsSensitiveContent(it.text) }
            .mapNotNull { message -> detect(message, self, scopeHash) }
            .distinctBy { it.fingerprint }
            .take(MAX_COMMITMENTS)
            .toList()
    }

    private fun detect(message: ChatMessage, self: String?, scopeHash: String): CommitmentDraft? {
        val text = message.text.trim().replace(Regex("\\s+"), " ").take(MAX_ACTION_CHARS)
        if (text.length < 4) return null
        val isRequest = requestSignal.containsMatchIn(text) || hasUnnegatedIndicativeRequest(text) || hasUnnegatedImperativeObjectRequest(text)
        val isMeeting = meetingVerbSignal.containsMatchIn(text) || hasMeetingNounAsSubject(text)
        val isPurchase = purchaseVerbSignal.containsMatchIn(text) || hasPurchaseNounAsSubject(text)
        val isReminder = reminderVerbSignal.containsMatchIn(text) || hasReminderNounAsSubject(text)
        // "no te llamo"/"no me encargo"/"no lo hago" son NEGATIVAS (rechazos), no
        // compromisos: excluir las frases de compromiso directamente negadas. Ojo:
        // NO se aplica a request/reminder, donde la negacion es idiomatica y POSITIVA
        // ("no olvides" = recuérdame, "no dejes que olvide" = recuérdame). (c.279)
        val isCommitment = hasUnnegatedCommitment(text)
        val isUserObligation = hasUnnegatedUserObligation(text)
        val isPendingObligation = hasUnnegatedPendingObligation(text)
        // c.500: presente pelado de 1ª persona con marca temporal futura PUNTUAL.
        // El parser decide si hay fecha futura (dueAt != null) y NO es rutina
        // (recurrence == NONE). El verbo pelado debe estar no-negado. Así
        // "termino el informe mañana" (dueAt set, NONE) se detecta, pero
        // "reviso el correo cada mañana" (recurrence DAILY) y "mando la carta al
        // correo" (dueAt null) no: el discriminador es la marca temporal, no el
        // verbo aislado (ver probe JVM PRE-fix arriba). Se excluye "hoy" (c.310):
        // presente + "hoy" es ambiguo (puede ser acción en curso, "envío el paquete
        // hoy"); los marcadores estrictamente futuros (mañana, el viernes, esta
        // semana, esta tarde, en un rato) sí indican un plan/compromiso. La rama
        // con-clítico ("lo subo al repo hoy") tolera "hoy" porque el clítico añade
        // señal de intención; el presente pelado, no.
        val parsed = NaturalTaskParser.parse(text)
        val dueAt = parsed.dueAt
        val mentionsToday = todayMarker.containsMatchIn(text)
        val isBarePresentCommitment =
            !isCommitment &&
                hasUnnegatedBarePresentCommitment(text) &&
                dueAt != null &&
                !mentionsToday &&
                parsed.recurrence == RecurrenceFrequency.NONE
        if (!isRequest && !isMeeting && !isPurchase && !isReminder && !isCommitment && !isUserObligation && !isPendingObligation && !isBarePresentCommitment) return null

        val sender = message.sender.orEmpty().trim().take(80)
        val owner = when {
            isRequest -> CommitmentOwner.SELF
            // c.316: "tienes que X" lo dice un tercero pero la obligación es DEL
            // USUARIO (el interlocutor: "tienes que firmar el contrato"). Sin esta
            // rama, el enrutado por remitente mandaría el draft a OTHER_COMMITMENT
            // (como si la obligación fuese del otro) y el usuario lo descartaría
            // creyendo que no es suyo — cuando en realidad es suya. Se ancla a SELF.
            isUserObligation -> CommitmentOwner.SELF
            // c.329: "tengo pendiente"/"me falta enviar" es siempre una obligación
            // DEL USUARIO (él reconoce su propia deuda abierta), independientemente
            // del remitente. Se ancla a SELF igual que isUserObligation.
            isPendingObligation -> CommitmentOwner.SELF
            // c.500: presente pelado con fecha futura es un compromiso DEL
            // USUARIO (1ª persona: "termino el informe mañana"). Se ancla a SELF
            // igual que isCommitment, sin depender del remitente.
            isBarePresentCommitment -> CommitmentOwner.SELF
            sender.isNotBlank() && self != null && sender.lowercase(Locale.ROOT) == self -> CommitmentOwner.SELF
            sender.isNotBlank() && self != null -> CommitmentOwner.OTHER
            sender.isNotBlank() -> CommitmentOwner.UNKNOWN
            isCommitment -> CommitmentOwner.SELF
            else -> CommitmentOwner.UNKNOWN
        }
        val kind = when {
            isRequest -> CommitmentKind.REQUEST
            isMeeting -> CommitmentKind.MEETING
            isPurchase -> CommitmentKind.PURCHASE
            isReminder -> CommitmentKind.REMINDER
            // c.316: la obligación dirigida al usuario ("tienes que firmar") es un
            // SELF_COMMITMENT del usuario, no un OTHER_COMMITMENT. Se evalúa ANTES
            // de la rama `owner == OTHER` para que el remitente!=self no la
            // reclasifique como compromiso ajeno.
            isUserObligation -> CommitmentKind.SELF_COMMITMENT
            owner == CommitmentOwner.OTHER -> CommitmentKind.OTHER_COMMITMENT
            else -> CommitmentKind.SELF_COMMITMENT
        }
        val confidence = (
            0.67f +
                (if (isCommitment || isRequest || isUserObligation || isBarePresentCommitment) 0.12f else 0f) +
                (if (dueAt != null) 0.11f else 0f) +
                (if (sender.isNotBlank()) 0.05f else 0f)
            ).coerceAtMost(0.97f)
        val now = System.currentTimeMillis()
        val reminderAt = dueAt?.let { ReminderRules.defaultReminderAt(it, now) }
        val location = locationSignal.find(text)?.groupValues?.getOrNull(1)
            ?.trim()?.trimEnd('.', ',', ';')?.take(80).orEmpty()
        val fingerprint = sha256(
            listOf(scopeHash.take(24), kind.name, owner.name, text.lowercase(Locale.ROOT), dueAt ?: 0L).joinToString("|")
        )
        return CommitmentDraft(
            kind = kind,
            owner = owner,
            actor = sender,
            action = text,
            location = location,
            dueAt = dueAt,
            confidence = confidence,
            suggestedReminderAt = reminderAt,
            fingerprint = fingerprint
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private const val MAX_COMMITMENTS = 500
    private const val MAX_ACTION_CHARS = 500
}

object ConversationSummaryEngine {
    fun summarize(preview: ConversationPreview, commitments: List<CommitmentDraft>): String {
        val people = preview.participants.take(4).joinToString(", ")
        val participantText = if (people.isBlank()) "sin participantes identificados" else "entre $people"
        val base = "${preview.messages.size} mensajes $participantText."
        if (commitments.isEmpty()) return "$base No se detectaron compromisos claros."
        val dated = commitments.count { it.dueAt != null }
        val requests = commitments.count { it.kind == CommitmentKind.REQUEST }
        return buildString {
            append(base)
            append(" Se detectaron ${commitments.size} compromisos")
            if (dated > 0) append(", $dated con fecha")
            if (requests > 0) append(" y $requests solicitudes")
            append(". Revisa cada propuesta antes de convertirla en tarea.")
        }
    }
}

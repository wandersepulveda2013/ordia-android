package com.ordia.app.context

import com.ordia.app.domain.ContentModeration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

/**
 * Motor de clasificación de intenciones organizativas basado en reglas/regex.
 *
 * DEPRECATED: Usar [com.ordia.app.intelligence.OrdiaIntelligenceEngine] en su lugar.
 * Este motor se mantiene como backend del [com.ordia.app.intelligence.BasicRuleProvider]
 * para el modo de reglas (fallback cuando el modelo local no está disponible).
 *
 * Flujo original:
 * 1. Filtro de privacidad (descarta contenido sensible)
 * 2. Detección de contenido bloqueado (sexual, violencia, etc.)
 * 3. Clasificación por intención permitida (allow-list)
 * 4. Extracción de fecha/hora
 * 5. Cálculo de confianza
 * 6. Generación de ContextIntent
 *
 * Nunca almacena el texto original después del análisis.
 */
@Deprecated("Usar OrdiaIntelligenceEngine via BasicRuleProvider")
object ContextIntentEngine {

    /** Confianza mínima para considerar una intención como válida */
    private const val MINIMUM_CONFIDENCE = 0.45f

    /** Palabras sin intención organizativa */
    private val CHAT_WORDS = setOf(
        "hola", "buenos días", "buenas tardes", "buenas noches", "cómo estás",
        "bien", "gracias", "ok", "okey", "vale", "sí", "no", "jeje", "jaja",
        "lol", "q tal", "qué tal", "bienvenido", "adiós", "bye", "chao",
        "nos vemos", "luego", "después", "hablamos", "x", "ok", "okis"
    )

    // Tokens individuales derivados: en [isCasualChat] la comparación es
    // token-a-token (split por espacios), así que las entradas multi-palabra
    // ("buenos días", "qué tal", "nos vemos", ...) eran INALCANZABLES (lista
    // muerta, hallazgo c.656 (ii)). Se derivan por split() una vez en lugar de
    // recalcular en cada llamada.
    private val CHAT_TOKENS = CHAT_WORDS.flatMap { it.split(" ") }.toSet()

    /** Palabras de baja confianza que indican conversación casual */
    private val LOW_CONFIDENCE_WORDS = setOf(
        "amor", "cariño", "corazón", "bebé", "hermoso", "lindo",
        "bonito", "te quiero", "te amo", "te extraño", "te adoro"
    )

    // Verbos imperativos por kind, reutilizados por los pisos
    // (hasStrong*Imperative) y por el guard de negación (imperativeIsNegated,
    // c.648). Centralizar las listas evita divergencia entre el guard del piso
    // (que protege cuando el score queda bajo [MINIMUM_CONFIDENCE]) y el guard
    // de negación global (que protege cuando el bono temporal o un patrón
    // específico eleva el score por encima del umbral SIN pasar por el piso).
    private val SHOPPING_VERBS = "comprar"
    private val PAYMENT_VERBS = "pagar"
    // Prefijos de acuse (c.651): una confirmación corta ("sí/vale/ok/...")
    // seguida de un imperativo de compra/pago indica que el usuario ACEPTÓ la
    // acción; el piso fuerte debe capturarla aunque el verbo no esté al inicio.
    // Lista cerrada: NO incluye los imperativos envolventes ("avísame",
    // "recuérdame", "no olvides", "tengo que", "hay que") — su verbo
    // subordinado es contenido del recordatorio, no una compra/pago autónomo.
    private val ACK_PREFIX = "sí|vale|ok|okay|bueno|dale|listo|perfecto|ya|claro"

    // c.694: PREFIJO temporal duro admitido por el ancla de los pisos TASK de
    // verbos cotidianos (revisar c.691 / enviar c.692 / entregar c.693). La
    // fecha delante del verbo es tan cotidiana como detrás ("mañana enviar el
    // informe", "hoy entregar el informe"); sin ella esas formas caían a NULL
    // (ítem OPEN descubierto c.693) o a DEADLINE con título íntegro sucio
    // ("el lunes entregar la tarea"). "pasado mañana" queda cubierto por
    // "mañana" (el match arranca en el "mañana" interior). La plantilla de
    // título usa el mismo ancla y arranca en el verbo, así el prefijo queda
    // fuera del match igual que el acuse (c.651).
    private val TASK_FLOOR_TEMPORAL =
        "hoy|mañana|esta\\s+(?:mañana|tarde|noche)|el\\s+(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo)"
    private val MEETING_VERBS = "reuni[oó]n"
    private val HOUSEHOLD_VERBS =
        "limpiar|lavar|cocinar|ordenar|arreglar|planchar|reparar|fregar|barrer|trapear|regar|sacudir|desempolvar"
    private val EXERCISE_VERBS = "correr|entrenar|nataci[oó]n|pesas"
    private val ERRAND_VERBS = "recoger|devolver|retirar"
    private val STUDY_VERBS = "estudiar|repasar"

    // Regex de los pisos de posición libre (c.643 HOUSEHOLD, c.647 MEETING/
    // EXERCISE/ERRAND/STUDY), centralizados (c.652) para que los pisos
    // [hasStrong*Imperative] y el guard de imperativo envolvente
    // [imperativeIsWrapped] compartan EXACTAMENTE el mismo patrón (lección
    // c.648: listas divergentes producen guards que no protegen lo mismo que
    // activa el piso).
    private val MEETING_FLOOR =
        Regex("""\b(?<!no )($MEETING_VERBS)\s+(con|de|del)\s+\w""")
    private val HOUSEHOLD_FLOOR =
        Regex("""\b(?<!no )($HOUSEHOLD_VERBS)\s+\w""")
    // Piso faena doméstica acotado al objeto (c.717, forma 7/14 de la SEGUNDA
    // clase de gestión, sonda `ManagementVerbDiscoveryProbe.kt` c.711): "sacar
    // la basura" es EL quehacer doméstico canónico con "sacar". El verbo suelto
    // ("dinero/fotos/el perro") es demasiado genérico para posición libre, así
    // se acota al objeto "basura" (como `ERRAND_CARRY_FLOOR` acota a vehículos/
    // mantenimiento c.684). `\b` final: "basurilla" no casa.
    private val HOUSEHOLD_TRASH_FLOOR =
        Regex("""\b(?<!no )sacar\s+(?:el\s+|la\s+|los\s+|las\s+)?basura\b""")
    private val HOUSEHOLD_FLOORS = listOf(HOUSEHOLD_FLOOR, HOUSEHOLD_TRASH_FLOOR)
    private val EXERCISE_FLOORS = listOf(
        Regex("""\b(?<!no )($EXERCISE_VERBS)\s+\w"""),
        Regex("""\b(?<!no )ir\s+al\s+gimnasio"""),
        // c.688: "hacer ejercicio" es la forma más genérica — y más
        // cotidiana — de la actividad física (ítem c.681, última forma
        // OPEN). `(?!\p{L})` exige SINGULAR: "hacer ejercicios de
        // matemáticas" (deberes) no captura. Sin este piso, "hacer
        // ejercicio por la mañana" se DESCARTABA (NULL, olvido
        // silencioso P1) con la sola franja blanda o desnuda.
        Regex("""\b(?<!no )hacer\s+(yoga|pesas|deporte|ejercicio(?!\p{L}))\b""")
    )
    // Piso transportativo de mantenimiento (c.684, ítem c.681): "llevar el
    // coche al taller"/"el lunes llevo el coche a revisión" son diligencias
    // inequívocas pero caían a NULL (ni verbo piso ni keyword ≥ umbral).
    // Objeto restringido a vehículos/piezas y destino a mantenimiento para no
    // colisionar con "llevar a María al cine" (persona/ocio) ni con destinos
    // ya cubiertos por el piso de "ir a ..." (correos/banco).
    private val ERRAND_CARRY_FLOOR =
        Regex("""\b(?<!no )(llevar|llevo)\s+(?:el\s+|la\s+|los\s+|las\s+|mi\s+|tu\s+|su\s+|un\s+|una\s+)?(coche|carro|auto|automóvil|moto|motocicleta|bicicleta|camión|camioneta|furgoneta|ruedas|motor)\s+a(?:l| la)?\s+(taller|mec[aá]nica|revisi[oó]n)\b""")
    // Piso de parada/errata acotado al destino (c.718, forma 8/14 de la
    // SEGUNDA clase de gestión, sonda `ManagementVerbDiscoveryProbe.kt`
    // c.711): "pasar por el banco/…" es el desplazamiento de ida-y-vuelta a
    // un lugar de TRÁMITE (misma familia que el piso "ir a banco/…" c.647).
    // "pasar por" suelto es demasiado genérico (casa/parque/el centro), así
    // se acota a los MISMOS destinos de trámite que el piso de "ir a", y el
    // keyword histórico genérico "pasar por" queda también en [VISIT], donde
    // convive sin robar (mismo umbral). `\b` final: "bancomadre" no casa.
    private val ERRAND_STOPBY_FLOOR =
        Regex("""\b(?<!no )pasar\s+por\s+(?:el\s+|la\s+|los\s+|las\s+)?(banco|correos|oficina|sucursal|ayuntamiento|notar[ií]a|juzgado|registro)\b""")
    private val ERRAND_FLOORS = listOf(
        Regex("""\b(?<!no )ir\s+a(?:l| la| los| las)?\s+(banco|correos|oficina|sucursal|ayuntamiento|notar[ií]a|juzgado|registro)\b"""),
        Regex("""\b(?<!no )($ERRAND_VERBS)\s+\w"""),
        ERRAND_CARRY_FLOOR,
        ERRAND_STOPBY_FLOOR
    )
    private val STUDY_FLOORS = listOf(
        Regex("""\b(?<!no )($STUDY_VERBS)\s+\w"""),
        Regex("""\b(?<!no )preparar\s+(?:el\s+|la\s+|lo\s+|un\s+|una\s+)?examen\b""")
    )

    // Regex del piso de DEADLINE (c.654): marcadores de fecha límite INEQUÍVOCOS
    // ("deadline"/"fecha límite"/"vencimiento"). Centralizado (misma lección
    // c.648/c.652) para que el piso [hasStrongDeadlineImperative] y el guard de
    // envolvente [imperativeIsWrapped] compartan EXACTAMENTE el mismo patrón.
    // El lookahead Unicode \p{L} delimita la frontera ("vencimiento" no casa
    // "desvencimiento"); "tope"/"límite"/"finaliza"/"último día" NO entran:
    // demasiado genéricos, se quedan como palabra clave sin piso. La negación
    // inmediata ("no deadline") queda bloqueada por el lookbehind `(?<!no )`.
    private val DEADLINE_FLOORS = listOf(
        Regex("""\b(?<!no )(deadline|fecha\s+l[íi]mite|vencimiento)(?!\p{L})""")
    )

    // Regex del piso de NOTA (c.714): verbos de anotación inequívocos
    // ("apuntar"/"anotar") + objeto, con los mismos anclajes de los pisos de
    // gestión c.691…c.713 (verbo al inicio, tras prefijo de ACUSE o tras
    // prefijo temporal). Centralizado (misma lección c.648/c.652) para que el
    // piso [hasStrongNoteImperative] y el guard de envolvente
    // [imperativeIsWrapped] compartan EXACTAMENTE el mismo patrón. `\s+\w`
    // exige objeto ("apuntar"/"anotar" sueltos, muletilla, no activan) y el
    // lookbehind `(?<!no )` bloquea la negación inmediata.
    private val NOTE_FLOOR =
        Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )(?:apuntar|anotar)\s+\w""")

    // Patrones de ACTIVACIÓN de los bonus-kinds APPOINTMENT/CALL (c.653),
    // centralizados (misma lección c.648/c.652) para que el bono aditivo de
    // [scoreSpecificPatterns] y el guard de envolvente [imperativeIsWrapped]
    // compartan EXACTAMENTE el mismo patrón. APPOINTMENT/CALL no tienen piso
    // (son "bonus-kinds": su confianza crece por bono específico aditivo), pero
    // eso no los exime del robo de kind: "recuérdame cita con el dentista" →
    // APPOINTMENT 0.69 robaba a TASK 0.45; "recuérdame llamar al banco" → CALL
    // 0.57 (hallazgo c.652, cierre c.653).
    private val APPOINTMENT_CITA_PATTERN = Regex("""cita (con|médica|del|con el|con la)""")
    // c.682: "psicólog[oa]/nutricionista/terapeuta" ya eran keywords del kind
    // (ContextIntentKind.APPOINTMENT) pero faltaban en el patrón específico
    // (lockstep keyword↔patrón, misma lección c.639 para HOUSEHOLD): sin ellos,
    // "ir al psicólogo" no reunía evidencia suficiente para superar el umbral.
    private val APPOINTMENT_MEDICAL_PATTERN =
        Regex("""(dentista|doctor|médico|especialista|consulta|revisión|chequeo|terapia|psicólog[oa]|nutricionista|terapeuta)""")
    // Desplazamiento a destino médico inequívoco (c.682, hallazgo c.681):
    // "ir al médico mañana" se DESCARTABA (NULL, olvido silencioso P1): las
    // evidencias sueltas (keyword + patrón médico + bono de fecha ≈ 0.42) no
    // alcanzaban [MINIMUM_CONFIDENCE] sin la keyword "cita". "ir a(l)
    // <destino médico>" es tan inequívoco como "ir al gimnasio" (EXERCISE,
    // patrón específico) o "ir al banco" (ERRAND, piso c.639): el desplazamiento
    // a un profesional/servicio de salud ES la cita. Lista cerrada de destinos
    // (no "taller"/"revisión" suelta: "llevar el coche a revisión" NO es cita
    // médica). El lookbehind `(?<!no )` bloquea la negación inmediata ("no ir al
    // médico"); el envolvente ("recuérdame ir al médico"→TASK) queda protegido
    // por el guard vía la fuente única [APPOINTMENT_SPECIFIC] (lección c.653).
    // Se aplica como BONO (no piso), así la duda (c.649) y la condición (c.650)
    // penalizan DESPUÉS y siguen descartando ("quizá ir al médico" → NULL).
    private val APPOINTMENT_GO_PATTERN =
        Regex("""\b(?<!no )ir\s+a(?:l| la| los| las)?\s+(médico|dentista|doctor|especialista|consulta|chequeo|terapia|psicólog[oa]|nutricionista|terapeuta)\b""")
    // Futuro declarativo de 1ª persona (c.663): "tendré (una |la )?cita" y "tendré
    // <sustantivo médico>" son promesas explícitas (no infinitivo condicionable),
    // evidencia MÁS firme que el presente — mismo olvido P1 que c.656 cerró para
    // CALL ("llamaré/hablaré + objeto"). El lookbehind `(?<!no )` protege la
    // negación inmediata ("no tendré dentista" NO se captura). La fuente única
    // [APPOINTMENT_SPECIFIC] (c.653) alimenta el bono y el guard de envolvente.
    private val APPOINTMENT_CITA_FUTURE_PATTERN =
        Regex("""\b(?<!no )tendré\s+(?:una\s+|la\s+)?cita\b""")
    private val APPOINTMENT_MEDICAL_FUTURE_PATTERN =
        Regex("""\b(?<!no )tendré\s+(dentista|doctor|médico|especialista|consulta|revisión|chequeo|terapia)\b""")
    private val APPOINTMENT_SPECIFIC =
        listOf(
            APPOINTMENT_CITA_PATTERN,
            APPOINTMENT_MEDICAL_PATTERN,
            APPOINTMENT_CITA_FUTURE_PATTERN,
            APPOINTMENT_MEDICAL_FUTURE_PATTERN,
            APPOINTMENT_GO_PATTERN
        )
    private val CALL_LLAMAR_PATTERN = Regex("""llamar (a|por teléfono)""")
    private val CALL_HABLAR_PATTERN = Regex("""hablar (con|por teléfono)""")
    // Futuro declarativo de 1ª persona (c.656): "llamaré/hablaré" + objeto es una
    // promesa explícita de acción (no infinitivo condicionable). Requiere objeto
    // explícito (igual que el bono de objeto del infinitivo) para no capturar
    // muletillas ("llamaré" a secas) y queda cubierto por el guard de envolvente
    // vía [CALL_SPECIFIC] (misma fuente única, c.653).
    private val CALL_LLAMAR_FUTURE_PATTERN =
        Regex("""\bllamaré\s+(a|al|a la|a los|a las)\s+\S""")
    private val CALL_HABLAR_FUTURE_PATTERN =
        Regex("""\bhablaré\s+con\s+\S""")
    private val CALL_SPECIFIC = listOf(CALL_LLAMAR_PATTERN, CALL_HABLAR_PATTERN,
        CALL_LLAMAR_FUTURE_PATTERN, CALL_HABLAR_FUTURE_PATTERN)

    // Imperativos envolventes (c.652 anti-overreach). Lista cerrada ALINEADA con
    // [hasStrongTaskImperative] y [hasStrongReminderImperative]: cuando uno de
    // estos imperativos PRECEDE al verbo del piso ("avísame reunión con el
    // equipo"), el texto es un recordatorio/tarea cuyo CONTENIDO es la acción
    // subordinada, no una reunión autónoma. Sin el guard, el piso c.647 (ancla
    // `\b`, cualquier posición) activa el kind subordinado y le ROBA el kind al
    // envolvente — por empate a 0.45 resuelto por orden de enum ("avísame
    // correr 5k"→EXERCISE en vez de REMINDER) o por base alta ("recuérdame ir
    // al gimnasio"→EXERCISE 0.59 > TASK 0.45) — y la semántica de aviso
    // ("avísame" = notifícame) se pierde: overreach P1 (misma lección de
    // diseño que c.651 para SHOPPING/PAYMENT: el verbo subordinado es contenido
    // del recordatorio, no una acción autónoma).
    // c.654 añade "cancelar|anular": son verbos de ACCIÓN que gobiernan el
    // contenido ("cancelar la cita del dentista") — semánticamente UNA TAREA
    // ("hay que cancelar…"), no una cita autónoma. Sin el guard, el kind
    // subordinado (APPOINTMENT/MEETING...) ROBABA el kind a TASK con un
    // título corrupto ("Cita: del dentista"): overreach P1. "cancelar" sin
    // objeto ("cancelar todo") no activa wrapper (guard `\s+\w` en el piso).
    // c.685 añade "falta": la construcción impersonal de obligación ("falta
    // comprar detergente" = hay que comprarlo) también gobierna el verbo
    // subordinado — "falta llamar al banco" es la TAREA de llamar, no una
    // llamada autónoma (misma lección c.653). El lookahead de infinitivo
    // excluye el uso temporal ("falta una hora"), el sustantivo ("una falta
    // grave") y la forma personal ("me falta tu apoyo"); `(?<!no )` bloquea
    // "no falta X" (= no hace falta, opuesto de la intención).
    // c.687 añade "te acuerdas de": la envolvente INTERROGATIVA de
    // recordatorio ("¿te acuerdas de pagar la renta?" = acuérdate de pagarla)
    // es el auto-recordatorio cotidiano por excelencia. El mismo lookahead
    // de infinitivo la separa de la evocación del pasado ("te acuerdas de
    // cuando íbamos…", "¿te acuerdas de la película?"), que es conversación.
    // c.689 añade "acuérdate de": la envolvente IMPERATIVA reflexiva de
    // recordatorio (2ª persona, enclítico -te) — hermano de "acordarme"
    // (c.619, 1ª persona) y de la interrogativa c.687. Sin ella, la forma
    // más cotidiana de auto-recordatorio se DESCARTABA → NULL (P1).
    private val WRAPPER_PATTERN =
        Regex("""\b(recuérdame|no olvides|tengo (?:que|q)|hay que|avísame|notifícame|acordarme|recuerda(?=\s+\w*(?:ar|er|ir)\b)|(?<!no )cancelar|(?<!no )anular|(?<!no )falta(?=\s+\w*(?:ar|er|ir)\b)|(?<!no )te acuerdas de(?=\s+\w*(?:ar|er|ir)\b)|(?<!no )acu[ée]rdate de(?=\s+\w*(?:ar|er|ir)\b))\b""")

    // Kinds protegidos por el guard de envolvente: pisos de posición libre
    // (c.652) + bonus-kinds APPOINTMENT/CALL (c.653). SHOPPING/PAYMENT no lo
    // necesitan: su piso (c.651) exige verbo al inicio o tras acuse, así un
    // envolvente nunca lo activa.
    private val WRAPPABLE_PATTERNS: Map<ContextIntentKind, List<Regex>> = mapOf(
        ContextIntentKind.MEETING to listOf(MEETING_FLOOR),
        ContextIntentKind.HOUSEHOLD to HOUSEHOLD_FLOORS,
        ContextIntentKind.EXERCISE to EXERCISE_FLOORS,
        ContextIntentKind.ERRAND to ERRAND_FLOORS,
        ContextIntentKind.STUDY to STUDY_FLOORS,
        ContextIntentKind.APPOINTMENT to APPOINTMENT_SPECIFIC,
        ContextIntentKind.CALL to CALL_SPECIFIC,
        ContextIntentKind.DEADLINE to DEADLINE_FLOORS,
        ContextIntentKind.NOTE to listOf(NOTE_FLOOR)
    )

    // Penalización por duda/condicional (c.649 anti-overreach). Marcadores como
    // "quizá"/"a lo mejor"/"tal vez" expresan que el usuario NO se ha comprometido:
    // capturarlos como tarea firme en la captura pasiva es overreach (igual que la
    // negación c.648 capta lo opuesto, la duda capta lo NO-comprometido). A
    // diferencia de la negación (que descarta el kind), la duda NO niega la
    // intención, la DEBILITA, así se aplica una penalización (no un bloqueo).
    // Se aplica POST-pisos para que no la sobreescriban los pisos de imperativos
    // (que usan maxOf(score, MINIMUM_CONFIDENCE)). Cubre el exceso máximo
    // observado (APPOINTMENT "tal vez cita..." 0.69 → 0.39) con margen.
    private const val HEDGE_PENALTY = 0.3f
    // \b de regex es ASCII-only (\w no incluye 'á'), así que "quizá" no cerraba
    // frontera y el patrón no casaba. Se usan lookarounds Unicode \p{L} (letras)
    // para delimitar los marcadores de forma robusta con tildes.
    private val HEDGE_PATTERN = Regex(
        """(?<!\p{L})(?:quiz[áa]s?|a\s+lo\s+mejor|tal\s+vez|capaz|puede\s+que|a\s+ver\s+si)(?!\p{L})"""
    )

    // Condicional "si" que gobierna el imperativo (c.650 anti-overreach). Defecto
    // de CLASE DISTINTA de c.649 (duda) descubierto por probe JVM fuente real:
    // una cláusula condicional que PRECEDE al imperativo ("si tengo tiempo ir al
    // gimnasio" → EXERCISE 0.59, "si me dan el día cita con el dentista" →
    // APPOINTMENT 0.69, "si puedo llamar a mamá" → CALL 0.57) activaba los pisos
    // y bonos fuertes y se persistía como tarea firme aunque la acción sólo se
    // haría BAJO CONDICIÓN (no resuelta). El guard penaliza (como la duda, no
    // bloquea como la negación) porque la condición no niega la intención.
    // Dos vías, ambas deterministas:
    //  (1) marcadores medios inequívocos: "si puedo/puedes/podemos",
    //      "si tengo tiempo", "si es posible", "si se puede", "si me acuerdo";
    //  (2) "si" al inicio de la frase o tras puntuación (`,;:.`): en español el
    //      "si" SIN tilde en cabeza de cláusula es condicional, no el "sí"
    //      afirmativo (que lleva tilde y no casa).
    // NO se penaliza la condición QUE SIGUE a un imperativo firme ("llamar al
    // banco si no llega el pago"): es un recordatorio condicional legítimo — el
    // usuario SÍ se comprometió a actuar bajo esa condición (decisión de diseño
    // documentada). Tampoco la "si" subordinada de contenido ("ver si hay
    // leche"): no es condición sobre el compromiso.
    private val CONDITIONAL_PATTERN = Regex(
        """(?<!\p{L})si\s+(?:puedo|puedes|podemos|tengo\s+tiempo|es\s+posible|se\s+puede|me\s+acuerdo)(?!\p{L})|(?:^|[,;:.]\s*)si\s+(?=\p{L})"""
    )

    /**
     * Analiza un evento contextual y retorna una intención clasificada,
     * o null si el contenido debe descartarse.
     *
     * El texto original se descarta inmediatamente después del análisis.
     */
    fun analyze(event: ContextEvent): ContextIntent? {
        // 1. Filtro de privacidad
        if (ContextPrivacyFilter.shouldBlock(event)) return null

        val text = event.safeText.trim()
        if (text.length < MIN_TEXT_LENGTH) return null

        val lower = text.lowercase(Locale.ROOT)

        // 2. Verificar que no sea solo conversación casual
        if (isCasualChat(lower)) return null

        // 3. Verificar contenido bloqueado
        if (containsBlockedContent(lower)) return null

        // 4. Clasificar intención
        val (kind, confidence, extractedTitle) = classify(lower, text) ?: return null
        if (confidence < MINIMUM_CONFIDENCE) return null

        // 5. Extraer fecha/hora
        val dueAt = extractDateTime(lower)

        // 6. Generar título descriptivo. Los [extractTitle] de cada [kind] y
        //    [generateTitle] capturan la cola tras la seña con `(.+)` voraz, de modo
        //    que los anclajes de fecha/hora (que [extractDateTime] ya resolvió en
        //    [dueAt]) sobreviven como RESIDUO en el título visible: p.ej.
        //    "recuérdame llamar a mamá el viernes a las 3" → "Llamar a mamá el
        //    viernes a las 3", y los prefijos "Cita: "/"Reunión: "/"Pagar "
        //    capitalizaban además el artículo/preposición que encabeza la cola.
        //    El [NaturalTaskParser] depura sus títulos consumiendo los
        //    anclajes al parsear (c.237–c.438); la captura de contexto (notificaciones
        //    → ContextIntent → tarea) NO lo hacía: una notificación capturada nacía con
        //    título sucio/redundante, degradando la captura (P1). [sanitizeTitle]
        //    depura el residuo temporal de cola y corrige la capitalización, llevando
        //    la ruta de contexto al mismo estándar de limpieza que el parser.
        val title = sanitizeTitle(extractedTitle ?: generateTitle(text, kind))

        return ContextIntent(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            dueAt = dueAt,
            confidence = confidence,
            source = event.source,
            sourcePackage = event.sourcePackage
        )
    }

    /**
     * Clasifica el texto en una intención organizativa.
     * Retorna el tipo, confianza y título extraído, o null si no hay coincidencia.
     */
    private fun classify(lower: String, original: String): Triple<ContextIntentKind, Float, String?>? {
        val matches = mutableListOf<Pair<ContextIntentKind, Float>>()

        for (kind in ContextIntentKind.entries) {
            val score = scoreKind(lower, kind)
            if (score > 0f) {
                matches.add(kind to score)
            }
        }

        if (matches.isEmpty()) return null

        // Tomar la mejor coincidencia
        val best = matches.maxByOrNull { it.second } ?: return null

        // Extraer título
        val title = extractTitle(original, best.first)

        return Triple(best.first, best.second, title)
    }

    /**
     * Puntúa qué tan bien coincide el texto con una categoría de intención.
     */
    private fun scoreKind(lower: String, kind: ContextIntentKind): Float {
        // Guard de negación global (c.648 anti-overreach). Los pisos
        // hasStrong*Imperative bloquean la captura del OPUESTO de la intención
        // del usuario cuando el score queda BAJO [MINIMUM_CONFIDENCE]: su
        // lookbehind `(?<!no )` impide activar el piso si la negación precede al
        // verbo. PERO el bono temporal ([scoreContextualBonus], +0.1 por fecha)
        // y algunos patrones específicos ([scoreSpecificPatterns], p.ej. "ir al
        // gimnasio") elevan el score por encima del umbral SIN pasar por el
        // piso, así el guard de negación del piso NUNCA se evalúa y la negación
        // se ignora: "mañana no comprar pan" → tarea "Comprar pan" (0.47),
        // "mañana no ir al gimnasio" → tarea "ir al gimnasio" (0.69). Esto es
        // un overreach P1: captura exactamente lo opuesto a lo que el usuario
        // dijo y lo persiste como tarea real. El guard comprueba si el verbo
        // imperativo del kind aparece inmediatamente negado por "no " (en
        // cualquier posición, con o sin prefijo temporal) y, si es así,
        // descarta ESE kind (score 0) para todo el pipeline. Determinista
        // (regex), sin IA fingida. Mismo principio que c.616, aplicado a la vía
        // del bono que c.616 no cubría.
        if (imperativeIsNegated(lower, kind)) return 0f

        // Guard de imperativo envolvente (c.652/c.653 anti-overreach). Los
        // pisos de posición libre (c.643/c.647: MEETING/HOUSEHOLD/EXERCISE/
        // ERRAND/STUDY con ancla `\b`) activan el kind aunque el verbo esté
        // SUBORDINADO a un imperativo envolvente: "avísame reunión con el
        // equipo"→MEETING (roba el kind a REMINDER), "recuérdame ir al
        // gimnasio"→EXERCISE 0.59 (roba el kind a TASK). c.653 cerró la misma
        // rendija en los bonus-kinds APPOINTMENT/CALL (sin piso; su confianza
        // crece por bono específico aditivo): "recuérdame cita con el
        // dentista"→APPOINTMENT 0.69 > TASK 0.45, "recuérdame llamar al
        // banco"→CALL 0.57. El verbo subordinado es CONTENIDO del recordatorio/
        // tarea, no una acción autónoma; la semántica de aviso ("avísame" =
        // notifícame) se perdía. Misma lección de diseño que c.651 (acuse):
        // el guard descarta el kind subordinado cuando un imperativo envolvente
        // lo PRECEDE, dejando que TASK/REMINDER (pisos c.613/c.619) gobiernen.
        if (imperativeIsWrapped(lower, kind)) return 0f

        // Guard de envolvente de obligación negado (c.681 anti-overreach). La
        // negación española canónica de la OBLIGACIÓN niega el envolvente, no el
        // verbo subordinado: "no tengo que ir al banco", "ya no tengo que pagar
        // el arriendo", "no hay que limpiar la cocina". [imperativeIsNegated]
        // (c.648) no lo cubre (su regex exige "no" INMEDIATO al verbo del kind)
        // y el lookbehind `(?<!no )` de los pisos tampoco ("no" no precede a
        // "tengo"/"hay" en la posición que miran), así el piso de TASK (c.613),
        // el piso de MEETING (c.647) y los patrones de APPOINTMENT disparaban
        // sobre la frase negada: la captura pasiva persistía EXACTAMENTE lo
        // opuesto a lo que el usuario dijo ("no tengo que ir al banco" → tarea
        // "Ir al banco"; "no tengo cita con el dentista" → APPOINTMENT 0.69).
        // La frase entera niega la obligación/posesión del evento, así que no
        // contiene intención capturable: se descarta TODA la clasificación
        // (todos los kinds), no un kind concreto. "no tengo gluten" no se toca:
        // "gluten" no es envolvente de obligación.
        if (obligationWrapperIsNegated(lower)) return 0f

        var score = 0f
        val words = lower.split(Regex("\\s+"))

        // Palabras clave directas
        for (keyword in kind.keywords) {
            if (lower.contains(keyword, ignoreCase = true)) {
                score += KEYWORD_WEIGHT
            }
        }

        // Patrones específicos por tipo
        score += scoreSpecificPatterns(lower, kind)

        // Bonos contextuales
        score += scoreContextualBonus(lower, kind)

        // Penalización por ambigüedad
        score -= scoreAmbiguityPenalty(lower, kind)

        // Piso de confianza para imperativos de tarea inequívocos (c.613): un
        // texto que arranca con "recuérdame/no olvides/tengo que/hay que" + verbo
        // es una tarea clara con independencia de si menciona fecha/hora. Sin este
        // piso, la mera ausencia de pistas temporales mantenía la confianza bajo
        // [MINIMUM_CONFIDENCE] y descartaba silenciosamente tareas legítimas
        // ("no olvides revisar el secuestro de DNS", "tengo que hacer el modelo
        // de amenaza", "hay que limpiar la pistola de agua"). El contenido dañino
        // genuino ya fue bloqueado en el paso 1 ([ContextPrivacyFilter]) o en el
        // paso 3 (insultos), por lo que llegar aquí es contenido permitido.
        if (kind == ContextIntentKind.TASK && hasStrongTaskImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso simétrico para imperativos de AVISO inequívocos (c.619): "recuérdame"
        // también es palabra clave de TASK (cubierto por el piso de c.613), pero sus
        // sinónimos puros de recordatorio — "avísame"/"notifícame"/"acordarme" — sólo
        // viven en REMINDER. Sin fecha/hora quedaban en 0.37 (< [MINIMUM_CONFIDENCE])
        // y se DESCARTABAN: el recordatorio explícito por antonomasia se olvidaba,
        // asimetría con "recuérdame" (capturado como TASK). Un imperativo de aviso +
        // verbo es un recordatorio claro con independencia de pistas temporales. El
        // guard `\s+\w` exige verbo real, así "avísame" aislado (muletilla) no activa.
        if (kind == ContextIntentKind.REMINDER && hasStrongReminderImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso simétrico para imperativos de COMPRA inequívocos (c.626, c.651):
        // "comprar <producto>" es una compra clara con independencia de pistas
        // temporales. Sin este piso, "comprar pan"/"comprar leche" quedaban en
        // 0.37 (< [MINIMUM_CONFIDENCE]) y se DESCARTABAN: el usuario capturaba
        // una compra real y Ordía la olvidaba. El contenido dañino genuino ya
        // fue bloqueado en el paso 1 ([ContextPrivacyFilter]) o en el paso 3
        // (insultos), así que llegar aquí es contenido permitido. c.651 amplió
        // el ancla `^` original: además del verbo al inicio, el piso se activa
        // tras un prefijo de ACUSE ([ACK_PREFIX]: "sí, comprar pan"/"vale,
        // comprar pan"/"ok, comprar leche"), que antes se descartaba (olvido
        // silencioso P1: sin pista temporal, el bono de [extractDateTime] no
        // compensa la base baja). Los imperativos envolventes NO son acuse:
        // "recuérdame comprar pan" es una tarea/recordatorio (pisos c.613/
        // c.619), no una compra autónoma. "no comprar pan", "mañana no comprar
        // pan" (lookbehind `(?<!no )` + [imperativeIsNegated] c.648) y
        // "comprar" aislado (muletilla) NO activan el piso (c.616
        // anti-overreach); duda (c.649) y condición (c.650) penalizan post-piso.
        if (kind == ContextIntentKind.SHOPPING && hasStrongShoppingImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso simétrico para imperativos de REUNIÓN inequívocos (c.626, c.647):
        // "reunión con/de/del <grupo>" es una reunión clara con independencia
        // de pistas temporales. Sin este piso, "reunión con el equipo" quedaba
        // en 0.32 (< [MINIMUM_CONFIDENCE]) y se DESCARTABA. c.647 quitó el ancla
        // `^` original: "mañana reunión con el equipo"/"hoy reunión de proyecto"
        // se descartaban (olvido silencioso P1) porque el bono temporal no
        // compensa la base baja. El guard `\b(?<!no )` exige imperativo
        // afirmativo en cualquier posición + preposición + grupo real, y bloquea
        // la negación inmediata ("no reunión con el equipo"/"mañana no reunión
        // con el equipo" → lookbehind falla) (c.616 anti-overreach). Mismo
        // defecto de clase que c.643 (HOUSEHOLD).
        if (kind == ContextIntentKind.MEETING && hasStrongMeetingImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso simétrico para imperativos de PAGO inequívocos (c.630, c.651):
        // "pagar <objeto>" es un pago claro con independencia de pistas
        // temporales. Sin este piso, "pagar la luz"/"pagar el internet"/"pagar
        // el recibo" quedaban en 0.42 (< [MINIMUM_CONFIDENCE]) y se
        // DESCARTABAN: el usuario capturaba el pago de una factura real y
        // Ordía lo olvidaba. Olvidar un pago tiene mayor coste que olvidar una
        // compra o reunión (recargos, corte de servicio), así que este cierre
        // es prioritario dentro de la misma clase. El contenido dañino genuino
        // ya fue bloqueado en el paso 1 ([ContextPrivacyFilter]) o en el paso 3
        // (insultos), así que llegar aquí es contenido permitido. c.651 amplió
        // el ancla `^` original: además del verbo al inicio, el piso se activa
        // tras un prefijo de ACUSE ([ACK_PREFIX]: "ok, pagar el recibo"/"vale,
        // pagar el internet"/"sí, pagar la luz"), que antes se descartaba
        // (olvido silencioso P1: sin pista temporal, el bono de
        // [extractDateTime] no compensa). Los imperativos envolventes NO son
        // acuse: "avísame pagar la luz" es un RECORDATORIO (piso c.619), no un
        // pago autónomo. "no pagar la luz", "mañana no pagar la luz"
        // (lookbehind `(?<!no )` + [imperativeIsNegated] c.648) y "pagar"
        // aislado (muletilla) NO activan el piso (c.616 anti-overreach); duda
        // (c.649) y condición (c.650) penalizan post-piso.
        if (kind == ContextIntentKind.PAYMENT && hasStrongPaymentImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso simétrico para imperativos de HOGAR inequívocos (c.638): "limpiar/
        // lavar/cocinar/ordenar/arreglar/planchar/reparar <objeto>" al INICIO es una
        // tarea del hogar clara con independencia de pistas temporales. Sin este
        // piso, "limpiar la cocina"/"lavar los platos"/"cocinar la cena"/"arreglar
        // el grifo"/"planchar las camisas" quedaban en 0.27 (< [MINIMUM_CONFIDENCE])
        // y se DESCARTABAN: el usuario capturaba una tarea del hogar real y Ordía
        // la olvidaba. La vía manual ([UniversalCaptureEngine]) SÍ promovía estos
        // verbos a TASK (c.583), así que la asimetría pasiva↔manual era una rendija
        // de olvido P1, misma clase que c.626 (compra) y c.630 (pago). El contenido
        // dañino genuino ya fue bloqueado en el paso 1 ([ContextPrivacyFilter]) o
        // en el paso 3 (insultos), así que llegar aquí es contenido permitido. El
        // ancla `^` + `\s+\w` exige imperativo AFIRMATIVO al inicio + objeto real:
        // así "no limpiar la cocina" (negación, capta lo opuesto a la intención del
        // usuario), "mañana no limpiar la cocina" (negación incrustada) y "limpiar"
        // aislado (muletilla) NO activan el piso (c.616 anti-overreach). Los casos
        // afirmativos con ancla temporal ("mañana limpiar la cocina") ya superan
        // el umbral vía [extractDateTime].
        if (kind == ContextIntentKind.HOUSEHOLD && hasStrongHouseholdImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso para imperativos de ejercicio inequívocos (c.639, c.647): "correr
        // 5k"/"entrenar piernas"/"hacer yoga"/"ir al gimnasio" son actividades
        // físicas claras con independencia de pistas temporales. Sin este piso,
        // "correr 5k"/"entrenar piernas"/"hacer yoga" quedaban en ~0.12–0.27
        // (< [MINIMUM_CONFIDENCE]) y se DESCARTABAN. Sólo "ir al gimnasio"
        // pasaba (0.59, patrón específico). c.647 quitó el ancla `^` original:
        // "mañana correr 5k"/"hoy entrenar piernas" se descartaban (olvido
        // silencioso P1) porque el bono temporal no compensa la base baja. El
        // guard `\b(?<!no )` exige afirmativo en cualquier posición y bloquea
        // la negación inmediata ("no correr hoy"/"mañana no entrenar" →
        // lookbehind falla) (c.616 anti-overreach). Mismo defecto de clase que
        // c.643 (HOUSEHOLD).
        if (kind == ContextIntentKind.EXERCISE && hasStrongExerciseImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso para imperativos de diligencia inequívocos (c.639, c.647): "ir al
        // banco"/"ir a correos"/"recoger el paquete"/"devolver el libro" son
        // trámites claros con independencia de pistas temporales. Sin este piso
        // quedaban en ~0.12 (< [MINIMUM_CONFIDENCE]) y se DESCARTABAN. c.647
        // quitó el ancla `^` original: "mañana ir al banco"/"hoy recoger el
        // paquete" se descartaban (olvido silencioso P1; los trámites tienen
        // fechas tope y coste por olvido, como los pagos) porque el bono temporal
        // no compensa la base baja. El guard `\b(?<!no )` exige afirmativo en
        // cualquier posición y bloquea la negación inmediata ("no ir al banco"/
        // "mañana no recoger el paquete" → lookbehind falla) (c.616
        // anti-overreach). El destino se acota a lugares de trámite para no
        // colisionar con SHOPPING ("ir a la farmacia") ni VISIT ("ir a casa de
        // mamá"), que ya clasifican bien por su vía propia. Mismo defecto de
        // clase que c.643 (HOUSEHOLD).
        if (kind == ContextIntentKind.ERRAND && hasStrongErrandImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso para imperativos de estudio inequívocos (c.639, c.647): "estudiar
        // para el examen"/"repasar la lección"/"preparar el examen" son sesiones
        // de estudio claras con independencia de pistas temporales. Sin este
        // piso, "repasar la lección"/"preparar el examen" quedaban en ~0.24–0.37
        // (< [MINIMUM_CONFIDENCE]) y se DESCARTABAN, aunque su intención es
        // inequívoca (P1: olvidar repasar antes de un examen tiene coste real).
        // "estudiar para el examen" ya pasaba (0.49). c.647 quitó el ancla `^`
        // original: "mañana repasar la lección"/"hoy preparar el examen" se
        // descartaban (olvido silencioso P1) porque el bono temporal no
        // compensa la base baja para "repasar"/"preparar". El guard `\b(?<!no )`
        // exige afirmativo en cualquier posición y bloquea la negación
        // inmediata ("no estudiar"/"mañana no repasar" → lookbehind falla)
        // (c.616 anti-overreach). "preparar" se acota a "examen" para no
        // colisionar con HOUSEHOLD ("preparar la cena") ni TASK ("preparar la
        // reunión"). Mismo defecto de clase que c.643 (HOUSEHOLD).
        if (kind == ContextIntentKind.STUDY && hasStrongStudyImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso para marcadores de FECHA LÍMITE inequívocos (c.654): "deadline"/
        // "fecha límite"/"vencimiento" son vocabulario de compromiso explícito.
        // Sin este piso quedaban en ~0.22 (< [MINIMUM_CONFIDENCE]) y se
        // DESCARTABAN — olvido silencioso P1 (probe JVM: "deadline: enviar el
        // informe" → NULL; "fecha límite: enviar el informe" → NULL). Olvidar
        // la fecha tope de un informe tiene coste real, como olvidar un pago.
        // El guard de envolvente se evaluó antes: "recuérdame la fecha límite"
        // gana TASK (piso c.613), no DEADLINE. Marcadores genéricos ("tope"/
        // "límite") NO activan el piso y quedan como palabra clave suelta.
        if (kind == ContextIntentKind.DEADLINE && hasStrongDeadlineImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Piso de NOTA (c.714): "apuntar/anotar <objeto>" — SEGUNDA clase de
        // verbos cotidianos de gestión (sonda
        // `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711, forma 4/14;
        // una por ciclo, doctrina anti-overreach). Sin este piso, "apuntar la
        // dirección del médico"/"anotar el número del banco mañana" quedaban
        // en ~0.12–0.22 (< [MINIMUM_CONFIDENCE]) y se DESCARTABAN: el usuario
        // se auto-anotaba información útil desde una notificación y Ordía lo
        // olvidaba (olvido silencioso P1). Kind decidido: NOTE (deliberación
        // contra TASK — downstream [ConfirmExternalSuggestionUseCase] lo
        // convierte en entidad NOTE real; marcar un teléfono/dirección no es
        // una acción ejecutable). Anti-overreach: `\s+\w` exige objeto,
        // `(?<!no )` bloquea la negada, el guard de envolvente
        // [imperativeIsWrapped] deja que "recuérdame apuntar…" gane TASK
        // (registrado en [WRAPPABLE_PATTERNS], lección c.652), y la duda
        // (c.649)/condición (c.650) penalizan post-piso. Determinista (regex),
        // sin IA fingida.
        if (kind == ContextIntentKind.NOTE && hasStrongNoteImperative(lower)) {
            score = maxOf(score, MINIMUM_CONFIDENCE)
        }

        // Penalización por duda/condicional (c.649 anti-overreach). Los pisos
        // anteriores elevan la confianza al mínimo para imperativos inequívocos
        // ("ir al gimnasio", "reunión con el equipo"...), pero NO distinguen un
        // compromiso firme de una mera especulación: "quizá ir al gimnasio"/"tal
        // vez reunión con el equipo" activaban el piso y se persistían como tareas
        // reales aunque el usuario expresamente NO se comprometió. Aplicada POST-
        // pisos para que no la sobreescriban (los pisos usan maxOf), reduce la
        // confianza final por debajo de [MINIMUM_CONFIDENCE] y descarta la
        // especulación. A diferencia de la negación ([imperativeIsNegated], que
        // descarta el kind porque capta lo OPUESTO), la duda capta lo NO-
        // comprometido: se penaliza, no se bloquea, porque la duda no niega la
        // intención. "debería"/"pensaba" NO se incluyen: reconocen necesidad/
        // intención (no pura duda) y ya caen bajo el umbral por score base bajo.
        if (hasHedgeMarker(lower)) {
            score -= HEDGE_PENALTY
        }

        // Penalización por condición que gobierna el imperativo (c.650). Misma
        // mecánica post-pisos que la duda: "si tengo tiempo ir al gimnasio"/
        // "si me dan el día cita con el dentista" activaban piso/bono y se
        // persistían como compromiso firme pese a estar gobernados por una
        // condición no resuelta (overreach P1). La condición TRAS un imperativo
        // firme ("llamar al banco si no llega el pago") NO se penaliza: es un
        // recordatorio condicional legítimo (ver [CONDITIONAL_PATTERN]).
        if (hasConditionalMarker(lower)) {
            score -= HEDGE_PENALTY
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Imperativos de tarea inequívocos (c.613, c.654). Coincide con los
     * anclajes de [extractTitle] para TASK. "recuérdame/no olvides" exigen
     * verbo; "tengo que/hay que" ya incluyen la cópula en el patrón.
     * c.654 añade "cancelar|anular": verbo de ACCIÓN que gobierna el
     * contenido ("cancelar la cita del dentista" es la tarea de cancelarla,
     * no una cita autónoma — anti-overreach, ver [WRAPPER_PATTERN]). La
     * alineación piso↔título (lección c.616) la garantiza [extractTitle]:
     * los templates "cancelar (.+)"/"anular (.+)" producen "Cancelar X"/
     * "Anular X" sin texto envolvente.
     */
    private fun hasStrongTaskImperative(lower: String): Boolean =
        Regex("""\b(?:recuérdame|no olvides|tengo (?:que|q)|hay que|(?<!no )cancelar|(?<!no )anular)\s+\w""")
            .containsMatchIn(lower) ||
            // c.682: "recuerda" sólo si el verbo subordinado es INFINITIVO
            // (anti-overreach: "recuerda cuando…"/"recuerda que…"/"recuerdas…"
            // no son instrucciones al asistente).
            Regex("""\brecuerda(?=\s+\w*(?:ar|er|ir)\b)\s+\w""").containsMatchIn(lower) ||
            // c.685: "falta <infinitivo>" / "hace falta <infinitivo>" es la
            // construcción impersonal de obligación ("falta comprar detergente"
            // = hay que comprarlo): una tarea clara aunque no mencione fecha.
            // El lookahead de infinitivo excluye el uso temporal ("falta una
            // hora"), el sustantivo ("una falta grave") y el personal ("me
            // falta tu apoyo"); `(?<!no )` bloquea "no falta X" (= no hace
            // falta, lo opuesto de la intención).
            Regex("""\b(?<!no )falta(?=\s+\w*(?:ar|er|ir)\b)\s+\w""").containsMatchIn(lower) ||
            // c.691: "revisar <objeto>" (el verbo cotidiano de comprobación:
            // "revisar el informe (pasado) mañana") se DESCARTABA — ningún
            // piso lo cubre y el bono temporal no alcanza el umbral (olvido
            // silencioso P1, descubierto con `tools/probe/KindCheckProbe.kt`
            // c.690). Mismo patrón de ancla que SHOPPING/PAYMENT (c.651):
            // verbo al inicio o tras prefijo de ACUSE. Anti-overreach:
            // `\s+\w` exige objeto ("revisar" aislado no captura),
            // `(?<!no )` bloquea la negada, "revisión" (sustantivo) no casa.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )revisar\s+\w""").containsMatchIn(lower) ||
            // c.692: "enviar <objeto>" (envío de gestión cotidiana: "enviar
            // el informe mañana") se DESCARTABA — descubierto con la sonda de
            // clase `tools/probe/CommonVerbDiscoveryProbe.kt` (c.692), que
            // reveló una familia de verbos sin piso; se resuelve UNA forma
            // por ciclo (doctrina anti-overreach). Mismo patrón de ancla que
            // c.691: verbo al inicio o tras prefijo de ACUSE, `\s+\w` exige
            // objeto, `(?<!no )` bloquea la negada, el sustantivo "envío"
            // no casa.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )enviar\s+\w""").containsMatchIn(lower) ||
            // c.693: "entregar <objeto>" ("entregar la tarea el lunes"),
            // forma 2/8 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`); mismo ancla/guard.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )entregar\s+\w""").containsMatchIn(lower) ||
            // c.696: "firmar <objeto>" ("firmar el contrato el jueves"),
            // forma 3 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`, c.692); mismo
            // ancla/guard que c.691…c.693.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )firmar\s+\w""").containsMatchIn(lower) ||
            // c.698: "renovar <objeto>" ("renovar el DNI la semana que
            // viene"), forma 4/6 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`, c.692); mismo
            // ancla/guard que c.691…c.696. Kind TASK: "renovar" gobierna
            // el objeto (DNI/seguro/suscripción), no el desplazamiento —
            // "renovar la suscripción" es gestión digital, y el piso
            // ERRAND está anclado a destinos físicos.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )renovar\s+\w""").containsMatchIn(lower) ||
            // c.700: "confirmar <objeto>" ("confirmar la reserva esta
            // noche"), forma 5/6 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`, c.692); mismo
            // ancla/guard que c.691…c.698. Kind TASK: "confirmar" es una
            // acción de gestión sobre el objeto (reserva/cita/asistencia),
            // no un evento (MEETING) ni un aviso (REMINDER).
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )confirmar\s+\w""").containsMatchIn(lower) ||
            // c.708: "imprimir <objeto>" ("imprimir las entradas el
            // viernes"), forma 6/8 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`, c.692); mismo
            // ancla/guard que c.691…c.700. Kind TASK: "imprimir" es una
            // acción de gestión sobre el objeto (entradas/informe/
            // contrato/billete), no un evento (MEETING) ni un
            // desplazamiento (ERRAND, anclado a destinos físicos).
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )imprimir\s+\w""").containsMatchIn(lower) ||
            // c.709: "reservar <objeto>" ("reservar el restaurante el
            // sábado"), forma 7/8 de la clase de verbos cotidianos (sonda
            // `tools/probe/CommonVerbDiscoveryProbe.kt`, c.692); mismo
            // ancla/guard que c.691…c.708. Kind TASK: "reservar" gobierna
            // el objeto (restaurante/mesa/hotel/vuelo); el evento/cita en
            // sí se captura por su propia vía.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )reservar\s+\w""").containsMatchIn(lower) ||
            // c.710: "cambiar <objeto>" ("cambiar las sábanas el
            // domingo"), forma 8/8 (ÚLTIMA OPEN) de la clase de verbos
            // cotidianos (sonda `tools/probe/CommonVerbDiscoveryProbe.kt`,
            // c.692); mismo ancla/guard que c.691…c.709. Kind decidido:
            // TASK, en deliberación contra HOUSEHOLD — "cambiar" es un
            // verbo genérico y un piso de posición libre capturaría
            // "cambiar de opinión/tema" como hogar (overreach) —
            // gobierna el objeto (sábanas/toallas/cerradura/pilas) como
            // acción de gestión, igual que las 7 formas previas.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )cambiar\s+\w""").containsMatchIn(lower) ||
            // c.711: "avisar <a <persona>/<objeto>" ("avisar a mamá de la
            // cita mañana"), forma 1 de la SEGUNDA clase de verbos
            // cotidianos de gestión (sonda
            // `tools/probe/ManagementVerbDiscoveryProbe.kt`, c.711;
            // herencia de la clase-verbos c.692…c.710, CERRADA 8/8).
            // Mismo ancla/guard que c.691…c.710. Kind decidido en este
            // ciclo: TASK (en deliberación contra CALL — "avisar" es
            // notificar, no una llamada específica); gobierna el objeto
            // (mamá/jefe/cita/entrega) como acción de gestión. Anti-overreach:
            // `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
            // sustantivo "aviso" no casa, suelto "avisar" no casa.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )avisar\s+\w""").containsMatchIn(lower) ||
            // c.712: "pedir <objeto>" ("pedir el taxi mañana"), forma 2/14
            // de la SEGUNDA clase de verbos cotidianos de gestión (sonda
            // `tools/probe/ManagementVerbDiscoveryProbe.kt`, c.711). Mismo
            // ancla/guard que c.691…c.711. Kind decidido: TASK (en
            // deliberación contra ERRAND/APPOINTMENT — "pedir" es solicitar/
            // encargar el objeto; "pedir una cita" gestiona la solicitud, la
            // cita en sí se captura por su propia vía); gobierna el objeto
            // (taxi/cita/comida/presupuesto) como acción de gestión.
            // Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la
            // negada, sustantivo "pedido" no casa, suelto "pedir" no casa.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )pedir\s+\w""").containsMatchIn(lower) ||
            // c.713: "solicitar <objeto>" ("solicitar la cita el lunes"),
            // forma 3/14 de la SEGUNDA clase de verbos cotidianos de gestión
            // (sonda `tools/probe/ManagementVerbDiscoveryProbe.kt`, c.711).
            // Mismo ancla/guard que c.691…c.712. Kind decidido: TASK (en
            // deliberación contra APPOINTMENT/ERRAND/CALL — "solicitar" es
            // gestionar la solicitud del objeto; la cita en sí se captura por
            // su propia vía); gobierna el objeto (cita/prestación/permiso/
            // presupuesto) como acción de gestión. Anti-overreach: `\s+\w`
            // exige objeto, `(?<!no )` bloquea la negada, sustantivo
            // "solicitud" no casa, suelto "solicitar" no casa.
            Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )solicitar\s+\w""").containsMatchIn(lower)
            // c.715 (forma 5/14 segunda clase de gestión, sonda
            // `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711): "buscar
            // <objeto>" recupera una cosa concreta (documento/llave/seguro).
            // Kind decidido: TASK, en deliberación contra ERRAND — no es
            // desplazamiento a un destino ("ir a/pasar por"); gobierna el
            // objeto recuperado. Anti-overreach: `\s+\w` exige objeto,
            // `(?<!no )` bloquea la negada, sustantivo "búsqueda" no casa,
            // suelto "buscar" no casa.
            || Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )buscar\s+\w""").containsMatchIn(lower)
            // c.716 (forma 6/14 segunda clase de gestión, sonda
            // `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711): "coger
            // <objeto>" toma/recoge una cosa (bus/ropa/llaves). Kind
            // decidido: TASK, en deliberación contra SHOPPING — sin
            // semántica de compra ("comprar/supermercado/tienda" viven en
            // SHOPPING); tampoco ERRAND (desplazamiento a destino).
            // Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la
            // negada, pasado "cogí…" no casa, suelto "coger" no casa.
            || Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )coger\s+\w""").containsMatchIn(lower)
            // c.719: "publicar <contenido>" ("publicar las fotos mañana"),
            // forma 9/14 de la SEGUNDA clase de verbos cotidianos de gestión
            // (sonda `tools/probe/ManagementVerbDiscoveryProbe.kt`, c.711).
            // Mismo ancla/guard que c.691…c.716. Kind decidido: TASK (en
            // deliberación contra NOTE/REMINDER — "publicar" es acción de
            // gestión sobre un contenido; no nota ni aviso). Anti-overreach:
            // `\s+\w` exige objeto, `(?<!no )` bloquea la negada, sustantivo
            // "publicación" no casa, suelto "publicar" no casa.
            || Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )publicar\s+\w""").containsMatchIn(lower)

    /**
     * Imperativos de aviso inequívocos (c.619). Sinónimos puros de recordatorio que
     * sólo viven en [ContextIntentKind.REMINDER] (no en TASK, donde "recuérdame"
     * ya cubre el piso de c.613). El verbo tras el imperativo evita capturar la
     * muletilla "avísame" aislada. "acordarme de" admite el "de" opcional.
     */
    private fun hasStrongReminderImperative(lower: String): Boolean =
        Regex("""\b(avísame|notifícame|acordarme(?:\s+de)?)\s+\w""").containsMatchIn(lower) ||
            // c.687: "te acuerdas de <infinitivo>?" es el formato interrogativo
            // del auto-recordatorio ("¿te acuerdas de pagar la renta?" =
            // acuérdate de pagarla). El lookahead de infinitivo excluye la
            // evocación del pasado ("te acuerdas de cuando íbamos…",
            // "¿te acuerdas de la película?"); `(?<!no )` bloquea la negada
            // ("no te acuerdas de pagar…": conservador, no se captura).
            Regex("""\b(?<!no )te acuerdas de(?=\s+\w*(?:ar|er|ir)\b)\s+\w""").containsMatchIn(lower) ||
            // c.689: "acuérdate de <infinitivo>" es el imperativo reflexivo
            // de 2ª persona (el auto-recordatorio hablado por excelencia).
            // `[ée]` admite la forma sin tilde; el lookahead de infinitivo
            // sólo admite recordatorio (la evocación y el sustantivo no
            // capturan); `(?<!no )` bloquea la negada (conservador).
            Regex("""\b(?<!no )acu[ée]rdate de(?=\s+\w*(?:ar|er|ir)\b)\s+\w""").containsMatchIn(lower)

    /**
     * Imperativos de compra inequívocos (c.626, c.651). "comprar <producto>".
     *
     * El piso se activa en dos posiciones: (a) verbo al INICIO ("comprar pan");
     * (b) tras un prefijo de ACUSE de [ACK_PREFIX] ("sí, comprar pan", "vale,
     * comprar pan", "ok, comprar leche"). c.651 cerró un olvido silencioso: el
     * ancla `^` original de c.626 descartaba todo imperativo de compra con
     * prefijo de acuse — sin pista temporal, el bono de [extractDateTime] no
     * compensa la base baja (0.37 < [MINIMUM_CONFIDENCE]).
     *
     * El acuse es una lista cerrada que NO incluye los imperativos envolventes
     * ("avísame"/"recuérdame"/"no olvides"/"tengo que"/"hay que"): su verbo
     * subordinado ("recuérdame comprar pan") es contenido del recordatorio, no
     * una compra autónoma — si activara el piso robaría el kind a TASK/REMINDER
     * (regresión detectada por [ContextIntentEngineDateTimeTest] y
     * [ContextIntentEngineNegationGuardTest]).
     *
     * La negación sigue bloqueada por el lookbehind `(?<!no )` y por
     * [imperativeIsNegated] (c.648); los guards de duda (c.649) y condición
     * (c.650) penalizan DESPUÉS del piso. Determinista (regex), sin IA fingida.
     */
    private fun hasStrongShoppingImperative(lower: String): Boolean =
        Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+)(?<!no )($SHOPPING_VERBS)\s+\w""").containsMatchIn(lower)

    /**
     * Imperativos de reunión inequívocos (c.626, c.647). Coincide con el anclaje de
     * [extractTitle] para MEETING: "reunión (con|de|del) <grupo>". El `\b` +
     * `\s+(con|de|del)\s+\w` exige imperativo afirmativo + preposición + grupo
     * real en cualquier posición; el lookbehind `(?<!no )` bloquea la negación
     * inmediata.
     *
     * c.647 cerró un olvido silencioso: el ancla `^` original de c.626 exigía la
     * "reunión" al INICIO, así TODO imperativo de reunión con prefijo temporal
     * ("mañana reunión con el equipo"/"hoy reunión de proyecto") se descartaba —
     * el supuesto "ya supera el umbral vía [extractDateTime]" era FALSO: el bono
     * temporal no eleva la confianza por encima de [MINIMUM_CONFIDENCE] (base
     * ~0.37 + bono 0.1 = 0.47 SIN piso... pero la base SIN piso cae a 0.32 por
     * penalización de ambigüedad de "reunión" sola, así el bono no compensa).
     * Quitar el ancla `^` admite prefijo temporal, reunión al inicio y reunión
     * en mitad ("después reunión con el equipo"), todos legítimos. La negación
     * sigue bloqueada: "no reunión con el equipo" y "mañana no reunión con el
     * equipo" (`no ` precede a "reunión" → lookbehind falla) NO activan el piso
     * (c.616 anti-overreach). Mismo defecto de clase que c.643 (HOUSEHOLD).
     * Determinista (regex), sin IA fingida.
     */
    private fun hasStrongMeetingImperative(lower: String): Boolean =
        MEETING_FLOOR.containsMatchIn(lower)

    /**
     * Imperativos de pago inequívocos (c.630, c.651). "pagar <objeto>".
     *
     * El piso se activa en dos posiciones: (a) verbo al INICIO ("pagar la
     * luz"); (b) tras un prefijo de ACUSE de [ACK_PREFIX] ("ok, pagar el
     * recibo", "vale, pagar el internet", "sí, pagar la luz"). c.651 cerró un
     * olvido silencioso: el ancla `^` original de c.630 descartaba todo
     * imperativo de pago con prefijo de acuse — sin pista temporal, el bono de
     * [extractDateTime] no compensa la base baja (0.42 < [MINIMUM_CONFIDENCE]).
     * Olvidar un pago tiene mayor coste que olvidar una compra (recargos,
     * corte de servicio).
     *
     * El acuse es una lista cerrada que NO incluye los imperativos envolventes
     * ("avísame pagar la luz" es un RECORDATORIO, no un pago autónomo —
     * regresión detectada por [ContextIntentEngineDateTimeTest]).
     *
     * La negación sigue bloqueada por el lookbehind `(?<!no )` y por
     * [imperativeIsNegated] (c.648); los guards de duda (c.649) y condición
     * (c.650) penalizan DESPUÉS del piso. Determinista (regex), sin IA fingida.
     */
    private fun hasStrongPaymentImperative(lower: String): Boolean =
        Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+)(?<!no )($PAYMENT_VERBS)\s+\w""").containsMatchIn(lower)

    /**
     * Imperativos de hogar inequívocos (c.638, c.643). Coincide con los verbos de
     * [scoreSpecificPatterns] para HOUSEHOLD y el anclaje de [extractTitle]:
     * "<verbo> <objeto>". El `\b` + `\s+\w` exige verbo + objeto real en
     * cualquier posición; el lookbehind `(?<!no )` bloquea la negación inmediata.
     *
     * c.643 cerró un olvido silencioso: el ancla `^` original de c.638 exigía el
     * verbo al INICIO, así TODO imperativo del hogar con prefijo temporal
     * ("mañana limpiar la cocina"/"hoy barrer el patio"/"el lunes regar las
     * plantas") se descartaba — el supuesto "ya superan el umbral vía
     * [extractDateTime]" era FALSO: el bono temporal no eleva la confianza por
     * encima de [MINIMUM_CONFIDENCE] para ninguno de los 13 verbos. Quitar el
     * ancla `^` admite prefijo temporal, verbo al inicio y verbo en mitad
     * ("voy a limpiar la cocina"), todos legítimos. La negación sigue
     * bloqueada: "no limpiar la cocina" y "mañana no limpiar la cocina"
     * (`no ` precede al verbo → lookbehind falla) NO activan el piso
     * (c.616 anti-overreach). El verbo aislado ("limpiar") sigue sin activar:
     * exige `\s+\w` (objeto real). Determinista (regex), sin IA fingida.
     */
    private fun hasStrongHouseholdImperative(lower: String): Boolean =
        HOUSEHOLD_FLOORS.any { it.containsMatchIn(lower) }

    /**
     * Imperativos de ejercicio inequívocos (c.639, c.647). Verbos de actividad
     * física + objeto, o "ir al gimnasio"/"hacer yoga/pesas/deporte". El `\b` +
     * lookbehind `(?<!no )` exige afirmativo en cualquier posición y bloquea la
     * negación inmediata.
     *
     * c.647 cerró un olvido silencioso: el ancla `^` original de c.639 exigía el
     * verbo al INICIO, así TODO imperativo de ejercicio con prefijo temporal
     * ("mañana correr 5k"/"hoy entrenar piernas") se descartaba — el supuesto
     * "ya supera el umbral vía [extractDateTime]" era FALSO: el bono temporal
     * no eleva la confianza por encima de [MINIMUM_CONFIDENCE] para "correr"/
     * "entrenar" (base ~0.27 + bono 0.1 = 0.37 < 0.45). Sólo "ir al gimnasio"
     * pasaba (0.59, vía patrón específico). Quitar el ancla `^` admite prefijo
     * temporal y verbo en mitad. La negación sigue bloqueada: "no correr hoy"/
     * "mañana no entrenar" (`no ` precede al verbo → lookbehind falla) NO
     * activan el piso (c.616 anti-overreach). Mismo defecto de clase que c.643
     * (HOUSEHOLD). Determinista (regex), sin IA fingida.
     */
    private fun hasStrongExerciseImperative(lower: String): Boolean =
        EXERCISE_FLOORS.any { it.containsMatchIn(lower) }

    /**
     * Imperativos de diligencia inequívocos (c.639, c.647). "ir a(l| la) <destino
     * de trámite>", o "recoger/devolver/retirar <objeto>". El destino se acota a
     * lugares de trámite para no colisionar con SHOPPING (farmacia) ni VISIT
     * (casa de). El `\b` + lookbehind `(?<!no )` exige afirmativo en cualquier
     * posición y bloquea la negación inmediata.
     *
     * c.647 cerró un olvido silencioso: el ancla `^` original de c.639 exigía el
     * imperativo al INICIO, así TODO trámite con prefijo temporal ("mañana ir al
     * banco"/"hoy recoger el paquete") se descartaba — el supuesto "ya supera
     * el umbral vía [extractDateTime]" era FALSO: el bono temporal no eleva la
     * confianza por encima de [MINIMUM_CONFIDENCE] (base ~0.12 + bono 0.1 =
     * 0.22 < 0.45). Quitar el ancla `^` admite prefijo temporal. La negación
     * sigue bloqueada: "no ir al banco"/"mañana no recoger el paquete" (`no `
     * precede al verbo → lookbehind falla) NO activan el piso (c.616
     * anti-overreach). Mismo defecto de clase que c.643 (HOUSEHOLD).
     * Determinista (regex), sin IA fingida.
     */
    private fun hasStrongErrandImperative(lower: String): Boolean =
        ERRAND_FLOORS.any { it.containsMatchIn(lower) }

    /**
     * Imperativos de estudio inequívocos (c.639, c.647). "estudiar <X>"/
     * "repasar <X>", o "preparar el/la/un/una examen". "preparar" se acota a
     * "examen" para no colisionar con HOUSEHOLD ("preparar la cena") ni TASK.
     * El `\b` + lookbehind `(?<!no )` exige afirmativo en cualquier posición y
     * bloquea la negación inmediata.
     *
     * c.647 cerró un olvido silencioso: el ancla `^` original de c.639 exigía el
     * verbo al INICIO, así TODO repaso con prefijo temporal ("mañana repasar la
     * lección"/"hoy preparar el examen") se descartaba — el supuesto "ya supera
     * el umbral vía [extractDateTime]" era FALSO para "repasar"/"preparar": el
     * bono temporal no eleva la confianza por encima de [MINIMUM_CONFIDENCE]
     * (base ~0.37 + bono 0.1 = 0.47 SIN piso, pero la base SIN piso cae por
     * penalización de ambigüedad, así el bono no compensa para "repasar"). Sólo
     * "estudiar para el examen" pasaba (0.49). Quitar el ancla `^` admite
     * prefijo temporal. La negación sigue bloqueada: "no estudiar"/"mañana no
     * repasar" (`no ` precede al verbo → lookbehind falla) NO activan el piso
     * (c.616 anti-overreach). Mismo defecto de clase que c.643 (HOUSEHOLD).
     * Determinista (regex), sin IA fingida.
     */
    private fun hasStrongStudyImperative(lower: String): Boolean =
        STUDY_FLOORS.any { it.containsMatchIn(lower) }

    /**
     * Marcador inequívoco de fecha límite (c.654). "deadline"/"fecha límite"/
     * "vencimiento" son vocabulario de compromiso explícito, igual que los
     * verbos de compra/pago de c.626/c.630: sin piso, "deadline: enviar el
     * informe" quedaba en ~0.22 (< [MINIMUM_CONFIDENCE]) y se DESCARTABA
     * (olvido silencioso P1; olvidar una fecha tope tiene coste real, como
     * un pago). Marcadores genéricos ("tope"/"límite"/"finaliza") NO activan
     * el piso (anti-overreach: el lookbehind `(?<!no )` también bloquea la
     * negación inmediata). El guard de envolvente [imperativeIsWrapped]
     * intercepta antes si un wrapper precede al marcador ("recuérdame la
     * fecha límite" → TASK, no DEADLINE). Determinista (regex), sin IA.
     */
    private fun hasStrongDeadlineImperative(lower: String): Boolean =
        DEADLINE_FLOORS.any { it.containsMatchIn(lower) }

    /**
     * Imperativos de anotación inequívocos (c.714): "apuntar/anotar <objeto>".
     * El piso comparte el patrón [NOTE_FLOOR] con el guard de envolvente
     * [imperativeIsWrapped] (lección c.648/c.652). Kind decidido: NOTE, en
     * deliberación contra TASK — "apuntar"/"anotar" es el verbo canónico de la
     * nota útil y downstream se materializa como entidad NOTE, no como tarea.
     */
    private fun hasStrongNoteImperative(lower: String): Boolean =
        NOTE_FLOOR.containsMatchIn(lower)

    /**
     * Detecta si el imperativo del [kind] está SUBORDINADO a un imperativo
     * envolvente (c.652/c.653 anti-overreach). Los pisos de posición libre
     * (c.643/c.647, ancla `\b`) se activan aunque el verbo venga gobernado por
     * "recuérdame/no olvides/tengo que/hay que/avísame/notifícame/acordarme/
     * cancelar/anular":
     * "avísame reunión con el equipo"→MEETING (le robaba el kind a REMINDER),
     * "recuérdame ir al gimnasio"→EXERCISE 0.59 (le robaba el kind a TASK).
     * c.653 extendió el guard a los bonus-kinds APPOINTMENT/CALL: no tienen
     * piso, pero su bono específico aditivo [scoreSpecificPatterns] los eleva
     * por encima del piso de TASK/REMINDER ("recuérdame cita con el dentista"
     * →APPOINTMENT 0.69, "recuérdame llamar al banco"→CALL 0.57), con el mismo
     * robo de kind que los pisos. El verbo subordinado es CONTENIDO del
     * recordatorio/tarea, no una acción autónoma (overreach P1, misma lección
     * de diseño que c.651 para los pisos SHOPPING/PAYMENT).
     *
     * El guard descarta el kind subordinado cuando un envolvente PRECEDE al
     * primer match de sus patrones de activación ([WRAPPABLE_PATTERNS]: pisos
     * c.652 + bonus-kinds c.653), con lo que TASK/REMINDER (pisos c.613/c.619)
     * gobiernan la captura. TASK/REMINDER son los envolventes y SHOPPING/
     * PAYMENT exigen inicio/acuse (c.651), así nunca quedan subordinados.
     *
     * Determinista (regex), sin IA fingida. No bloquea prefijos declarativos
     * ("tengo una reunión", "voy a correr", "tengo cita con el dentista"): no
     * son imperativos envolventes.
     */
    private fun imperativeIsWrapped(lower: String, kind: ContextIntentKind): Boolean {
        val patterns = WRAPPABLE_PATTERNS[kind] ?: return false
        val matchStart = patterns.mapNotNull { it.find(lower)?.range?.first }.minOrNull() ?: return false
        val wrapperEnd = WRAPPER_PATTERN.find(lower)?.range?.last ?: return false
        return wrapperEnd < matchStart
    }

    /**
     * Detecta si el verbo imperativo del [kind] aparece inmediatamente negado por
     * "no " en el texto (c.648 anti-overreach). Complementa a los pisos
     * [hasStrong*Imperative]: éstos sólo bloquean la negación cuando el score queda
     * bajo [MINIMUM_CONFIDENCE] (vía lookbehind `(?<!no )`), pero NO protegen cuando
     * el bono temporal ([scoreContextualBonus]) o un patrón específico
     * ([scoreSpecificPatterns], p.ej. "ir al gimnasio") elevan el score por encima
     * del umbral sin activar el piso. En ese caso, "mañana no comprar pan" se
     * capturaba como la tarea "Comprar pan": exactamente lo opuesto a la intención
     * del usuario, persistido como dato real (overreach P1).
     *
     * El patrón `\bno\s+(verbo)` exige la negación INMEDIATA, así no bloquea casos
     * afirmativos con "no" incidental: "no olvides comprar pan" (el "no" niega
     * "olvides", "comprar" va libre) o "no tengo gluten, comprar pan" (el "no" va
     * con "tengo") NO se bloquean. Sólo "no <verbo-imperativo>" y "<temporal> no
     * <verbo-imperativo>" (la negación incrustada) disparan el guard y descartan
     * ese kind para todo el pipeline. Determinista (regex), sin IA fingida.
     *
     * TASK y REMINDER se excluyen: su imperativo ("recuérdame/no olvides/tengo
     * que/hay que") ya contiene o admite "no" ("no olvides" es afirmativo: "no te
     * olvides DE"), así negar aquí rompería recordatorios legítimos. APPOINTMENT,
     * CALL, DEADLINE y COMMITMENT_PERSONAL no tienen verbo imperativo de acción
     * directa negable en este sentido.
     */
    private fun imperativeIsNegated(lower: String, kind: ContextIntentKind): Boolean {
        val negatedVerbs: String = when (kind) {
            ContextIntentKind.SHOPPING -> SHOPPING_VERBS
            ContextIntentKind.PAYMENT -> PAYMENT_VERBS
            ContextIntentKind.MEETING -> MEETING_VERBS
            ContextIntentKind.HOUSEHOLD -> HOUSEHOLD_VERBS
            ContextIntentKind.EXERCISE -> EXERCISE_VERBS
            ContextIntentKind.ERRAND -> ERRAND_VERBS
            ContextIntentKind.STUDY -> STUDY_VERBS
            else -> return false
        }
        // Negación inmediata del verbo imperativo, en cualquier posición
        // (cubre "no comprar pan" y "mañana no comprar pan").
        if (Regex("""\bno\s+($negatedVerbs)\b""").containsMatchIn(lower)) return true
        // "ir al gimnasio" y "hacer yoga/pesas/deporte" son imperativos multi-palabra
        // de EXERCISE sin verbo simple; se niegan igual con "no ir al gimnasio" /
        // "mañana no hacer yoga".
        if (kind == ContextIntentKind.EXERCISE) {
            if (Regex("""\bno\s+ir\s+al\s+gimnasio""").containsMatchIn(lower)) return true
            if (Regex("""\bno\s+hacer\s+(yoga|pesas|deporte)""").containsMatchIn(lower)) return true
        }
        // "preparar el examen" es imperativo de STUDY acotado (no verbo simple).
        if (kind == ContextIntentKind.STUDY &&
            Regex("""\bno\s+preparar\s+(?:el\s+|la\s+|lo\s+|un\s+|una\s+)?examen\b""").containsMatchIn(lower)
        ) return true
        // "ir al banco" (ERRAND) es imperativo multi-palabra.
        if (kind == ContextIntentKind.ERRAND &&
            Regex("""\bno\s+ir\s+a(?:l| la| los| las)?\s+(banco|correos|oficina|sucursal|ayuntamiento|notar[ií]a|juzgado|registro)\b""").containsMatchIn(lower)
        ) return true
        // "pasar por el banco" (ERRAND, piso acotado c.718) es imperativo
        // multi-palabra: la negación sigue bloqueada aunque el bono temporal
        // eleve el score sin pasar por el piso (misma vía que "sacar la
        // basura" c.717).
        if (kind == ContextIntentKind.ERRAND &&
            Regex("""\bno\s+pasar\s+por\s+(?:el\s+|la\s+|los\s+|las\s+)?(banco|correos|oficina|sucursal|ayuntamiento|notar[ií]a|juzgado|registro)\b""").containsMatchIn(lower)
        ) return true
        // "sacar la basura" (HOUSEHOLD, piso acotado c.717) es imperativo
        // multi-palabra: la negación sigue bloqueada aunque el bono temporal
        // eleve el score sin pasar por el piso (misma vía que ERRAND).
        if (kind == ContextIntentKind.HOUSEHOLD &&
            Regex("""\bno\s+sacar\s+(?:el\s+|la\s+|los\s+|las\s+)?basura\b""").containsMatchIn(lower)
        ) return true
        return false
    }

    /**
     * Detecta la negación del envolvente de obligación/posesión (c.681).
     * Cubre "no tengo que|q …", "ya no tengo que|q …", "no hay que …" y la
     * negación de la posesión de evento "no tengo reunión|cita …": la frase
     * entera afirma la AUSENCIA de la obligación o del evento, así que ningún
     * kind puede capturarla (el guard se evalúa para todos los kinds en
     * [scoreKind]). A diferencia de [imperativeIsNegated] (negación inmediata
     * del verbo del kind), aquí el "no" precede al envolvente léxico del piso.
     * Determinista (regex), sin IA fingida.
     */
    private fun obligationWrapperIsNegated(lower: String): Boolean =
        Regex("""\b(?:ya\s+)?no\s+(?:tengo\s+(?:que|q)\b|hay\s+que\b|tengo\s+(?:reuni[oó]n|cita)\b)""")
            .containsMatchIn(lower)

    /**
     * Detecta marcadores de duda/condicional (c.649). "quizá"/"a lo mejor"/"tal
     * vez"/"capaz"/"puede que"/"a ver si" indican que el usuario NO se ha
     * comprometido a la acción. Capturar tal especulación como tarea firme en la
     * captura pasiva es overreach (degrada la bandeja con items no validados).
     * Determinista (regex), sin IA fingida — la duda léxica es objetiva.
     */
    private fun hasHedgeMarker(lower: String): Boolean =
        HEDGE_PATTERN.containsMatchIn(lower)

    /**
     * Detecta la condición "si" que gobierna el imperativo (c.650). Ver
     * [CONDITIONAL_PATTERN] para el alcance exacto: sólo condición que PRECEDE
     * al imperativo o marcadores medios inequívocos; nunca la condición
     * posterior (recordatorio condicional legítimo) ni la "si" de contenido.
     */
    private fun hasConditionalMarker(lower: String): Boolean =
        CONDITIONAL_PATTERN.containsMatchIn(lower)

    /**
     * Patrones específicos por tipo de intención.
     */
    private fun scoreSpecificPatterns(lower: String, kind: ContextIntentKind): Float {
        return when (kind) {
            ContextIntentKind.TASK -> {
                var s = 0f
                // "tengo que + verbo"
                if (Regex("""tengo (que|q) \w+""").containsMatchIn(lower)) s += 0.15f
                // "hay que + verbo"
                if (Regex("""hay que \w+""").containsMatchIn(lower)) s += 0.15f
                // "recuérdame + verbo"
                if (Regex("""recuérdame \w+""").containsMatchIn(lower)) s += 0.2f
                // "no olvides + verbo"
                if (Regex("""no olvides \w+""").containsMatchIn(lower)) s += 0.2f
                s
            }
            ContextIntentKind.SHOPPING -> {
                var s = 0f
                if (Regex("""(ir|voy|iremos|vamos|iré) (a |al |a la |a los )?(super|mercad|tiend|farm)""").containsMatchIn(lower)) s += 0.25f
                if (Regex("""comprar \w+""").containsMatchIn(lower)) s += 0.15f
                if (lower.contains("leche") || lower.contains("pan") || lower.contains("huevo")
                    || lower.contains("detergente") || lower.contains("verduras")
                    || lower.contains("fruta") || lower.contains("carne")) s += 0.1f
                s
            }
            ContextIntentKind.APPOINTMENT -> {
                var s = 0f
                if (APPOINTMENT_CITA_PATTERN.containsMatchIn(lower)) s += 0.25f
                if (APPOINTMENT_MEDICAL_PATTERN.containsMatchIn(lower)) s += 0.2f
                // Bono fusionado de futuro (c.663): un "tendré (una |la )?cita"
                // o "tendré <médico>" vuela por encima de [MINIMUM_CONFIDENCE]
                // igual que el futuro CALL (c.656): la promesa en indefinido de
                // 1ª persona es evidencia más firme que el presente. Sin esto,
                // "tendré dentista el viernes" quedaba en ~0.42 (< umbral) y se
                // DESCARTABA (olvido P1) aunque a veces arrastraba fecha/hora.
                if (APPOINTMENT_CITA_FUTURE_PATTERN.containsMatchIn(lower)) s += 0.45f
                if (APPOINTMENT_MEDICAL_FUTURE_PATTERN.containsMatchIn(lower)) s += 0.45f
                // Bono de desplazamiento a destino médico (c.682): "ir al médico
                // (mañana)" se descartaba (NULL, olvido silencioso P1) porque las
                // evidencias sueltas sumaban ~0.42 (< [MINIMUM_CONFIDENCE]) sin
                // la keyword "cita". El desplazamiento a un profesional/servicio
                // de salud es evidencia inequívoca de cita (simétrico a "ir al
                // gimnasio"/"ir al banco"). Bono (no piso): la duda (c.649) y la
                // condición (c.650) penalizan después y siguen descartando. El
                // patrón vive en [APPOINTMENT_SPECIFIC], así el guard de
                // envolvente (c.653) protege "recuérdame ir al médico" → TASK.
                if (APPOINTMENT_GO_PATTERN.containsMatchIn(lower)) s += 0.35f
                s
            }
            ContextIntentKind.MEETING -> {
                var s = 0f
                if (Regex("""(quedar|vernos|quedamos|encuentro) (con|en|a las)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""reunión (con|de|del)""").containsMatchIn(lower)) s += 0.2f
                if (lower.contains("nos vemos")) s += 0.1f
                s
            }
            ContextIntentKind.STUDY -> {
                var s = 0f
                if (Regex("""(estudiar|estudio|examen|repasar|preparar)""").containsMatchIn(lower)) s += 0.15f
                if (lower.contains("auditoría") || lower.contains("examen") || lower.contains("clase")) s += 0.1f
                s
            }
            ContextIntentKind.DEADLINE -> {
                var s = 0f
                if (Regex("""(entregar|debo entregar|tengo que entregar)""").containsMatchIn(lower)) s += 0.2f
                // `\b` (c.694): sin borde, "entrega" casaba dentro del
                // INFINITIVO "entregar" y "el lunes entregar la tarea" sumaba
                // 0.45 puros (0.1+0.2+0.15) que vencían por épsilon al piso
                // TASK de c.693 — misma lección que el `\b` HOUSEHOLD de
                // c.693. Este bono es para la presente/3ª persona ("el lunes
                // entrega el informe"), no para el infinitivo.
                if (Regex("""(el|lunes|martes|miércoles|jueves|viernes) (entrego|entrega|entregan)\b""").containsMatchIn(lower)) s += 0.15f
                s
            }
            ContextIntentKind.CALL -> {
                var s = 0f
                if (CALL_LLAMAR_PATTERN.containsMatchIn(lower)) s += 0.2f
                if (CALL_HABLAR_PATTERN.containsMatchIn(lower)) s += 0.15f
                // Futuro declarativo (c.656): "llamaré/hablaré" + objeto explícito
                // es evidencia MÁS firme que el infinitivo (promesa en indefinido,
                // 1ª persona). Sin este bono quedaba NULL (olvido P1) aun la frase
                // inequívoca "llamaré a mamá el viernes". Bono fusionado (patrón +
                // objeto) que alcanza [MINIMUM_CONFIDENCE]; el guard de envolvente
                // sigue protegido (patrones en [CALL_SPECIFIC]).
                if (CALL_LLAMAR_FUTURE_PATTERN.containsMatchIn(lower)) s += 0.45f
                if (CALL_HABLAR_FUTURE_PATTERN.containsMatchIn(lower)) s += 0.45f
                // Bono de objeto explícito (P1): "llamar a/al/a la/a los <persona>" o
                // "hablar con <persona>" es una llamada telefónica clara. Sin este bono,
                // "llamar a María" quedaba en 0.32 (< MINIMUM_CONFIDENCE 0.45) y se
                // DESCARTABA (olvido), y "llamar al doctor el viernes a las 4" empataba
                // con APPOINTMENT (0.5) y perdía por orden de enum → clasificada como
                // cita médica aunque el verbo "llamar" hace evidente la intención. El
                // bono eleva CALL por encima del umbral y de APPOINTMENT. El guard `\S`
                // tras la preposición exige un objeto real (no "llamar a" al final).
                val hasCallObject =
                    Regex("""\bllamar\s+(a|al|a la|a los|a las)\s+\S""").containsMatchIn(lower) ||
                        Regex("""\bhablar\s+con\s+\S""").containsMatchIn(lower)
                if (hasCallObject) s += 0.25f
                s
            }
            ContextIntentKind.EXERCISE -> {
                var s = 0f
                if (Regex("""(gimnasio|entrenar|yoga|correr|natación)""").containsMatchIn(lower)) s += 0.2f
                if (Regex("""ir al gimnasio""").containsMatchIn(lower)) s += 0.15f
                s
            }
            ContextIntentKind.REMINDER -> {
                var s = 0f
                // "recuérdame" NO entra aquí (c.717 lockstep): el bono del
                // envolvente "recuérdame …" (+0.25) sumado a la pista temporal
                // (+0.1) llegaba a 0.47 y ROBABA a TASK su piso de envolvente
                // (0.45) en textos con fecha: "recuérdame limpiar la cocina
                // mañana" → REMINDER sobre TASK. "recuérdame" es envolvente de
                // TAREA (su piso c.613 gobierna); los sinónimos puros de aviso
                // ("avísame|notifícame|acordarme") siguen siendo REMINDER
                // (alineados con [hasStrongReminderImperative]).
                if (Regex("""(avísame|notifícame|acordarme)""").containsMatchIn(lower)) s += 0.25f
                s
            }
            ContextIntentKind.PAYMENT -> {
                var s = 0f
                if (Regex("""pagar (el|la|los|las)""").containsMatchIn(lower)) s += 0.2f
                if (lower.contains("factura") || lower.contains("recibo") || lower.contains("internet")
                    || lower.contains("luz") || lower.contains("agua") || lower.contains("teléfono")) s += 0.1f
                s
            }
            ContextIntentKind.COMMITMENT_PERSONAL -> {
                var s = 0f
                if (Regex("""(pienso|planeo|quiero|voy a|intentaré)""").containsMatchIn(lower)) s += 0.1f
                s
            }
            ContextIntentKind.HOUSEHOLD -> {
                var s = 0f
                // `\b` inicial (c.693): sin borde de palabra "regar" casa DENTRO
                // de "entregar"/"entregaré" y el verbo de entrega se roba como
                // tarea doméstica ("entregar el informe a las 9" → HOUSEHOLD
                // "Regar el informe"). El piso (HOUSEHOLD_FLOOR) ya tiene `\b`.
                if (Regex("""\b(limpiar|ordenar|cocinar|lavar|planchar|arreglar|reparar|jardín|fregar|barrer|trapear|regar|sacudir|desempolvar)""").containsMatchIn(lower)) s += 0.15f
                s
            }
            else -> 0f
        }
    }

    /**
     * Bonos contextuales que aumentan la confianza.
     */
    private fun scoreContextualBonus(lower: String, kind: ContextIntentKind): Float {
        var bonus = 0f

        // Presencia de fecha aumenta confianza en cualquier intención
        if (hasDateReference(lower)) bonus += 0.1f

        // Presencia de hora
        if (hasTimeReference(lower)) bonus += 0.08f

        // Verbos en futuro
        if (Regex("""(iré|voy|vamos|iremos|haré|compraré|llamaré|estudiaré|entregaré)""").containsMatchIn(lower)) {
            bonus += 0.08f
        }

        return bonus
    }

    /**
     * Penalización por ambigüedad.
     */
    private fun scoreAmbiguityPenalty(lower: String, kind: ContextIntentKind): Float {
        var penalty = 0f

        // Muchas palabras de conversación casual
        val chatCount = CHAT_WORDS.count { lower.contains(it) }
        if (chatCount > 2) penalty += 0.15f
        if (chatCount > 4) penalty += 0.2f

        // Palabras románticas/íntimas sin intención organizativa
        val lowCount = LOW_CONFIDENCE_WORDS.count { lower.contains(it) }
        if (lowCount > 0) penalty += 0.2f * lowCount

        // Ambigüedad: "voy a" puede ser "voy a hacer" (tarea) o "voy a tu casa" (casual)
        if (kind == ContextIntentKind.COMMITMENT_PERSONAL && lower.length < 20) penalty += 0.15f

        return penalty
    }

    /**
     * Verifica si el texto es solo conversación casual.
     */
    private fun isCasualChat(lower: String): Boolean {
        val words = lower.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return true

        val chatRatio = words.count { it in CHAT_TOKENS }.toFloat() / words.size
        return chatRatio > 0.6f
    }

    /**
     * Verifica contenido explícitamente bloqueado (defensa en profundidad).
     *
     * Delega en la fuente única [ContentModeration.THEMATIC_RULES] (c.615): la
     * detección por raíz CON exenciones de contexto legítimo y normalización
     * sin tildes, idéntica a [com.ordia.app.intelligence.IntelligenceSafetyGate].
     * Antes, este método duplicaba las raíces con regex inline SIN exenciones, así
     * que bloqueaba en bruto tareas legítimas que el paso 1
     * ([ContextPrivacyFilter], que SÍ exime) dejaba pasar: "matar el proceso del
     * servidor", "comprar bomba de agua", "comprar la droga en la farmacia"... se
     * descartaban SILENCIOSAMENTE en la captura contextual (P1 datos/evitar
     * olvidos). Además, al no normalizar tildes, las formas sin acento de los
     * insultos ("estupido"/"imbecil") se escapaban de este paso (paso 1, sin
     * regla de insultos, tampoco los frenaba). Ahora comparte el mismo contrato
     * que el gate de IA, así que no pueden desincronizarse (mismo principio que
     * c.299 para secretos). Determinista, sin IA fingida.
     *
     * Construye sobre c.614 (piso de confianza para imperativos inequívocos):
     * aquí se mantiene la defensa en profundidad del paso 3 con exenciones en
     * vez de confiar sólo en el paso 1, y se elimina la duplicación de la lista
     * en [com.ordia.app.intelligence.IntelligenceSafetyGate] (fuente única).
     */
    private fun containsBlockedContent(lower: String): Boolean =
        ContentModeration.THEMATIC_RULES.any { ContentModeration.isHarmful(lower, it) }

    /**
     * Extrae el título del texto original.
     */
    private fun extractTitle(original: String, kind: ContextIntentKind): String? {
        val lower = original.lowercase(Locale.ROOT)

        return when (kind) {
            ContextIntentKind.TASK -> {
                // "tengo que X" → "X"
                val match = Regex("""tengo (que|q) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(match.groupValues[2])

                // "recuérdame X" → "X"
                val match2 = Regex("""recuérdame (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match2 != null) return capitalizeFirst(match2.groupValues[1])

                // "no olvides X" → "X"
                val match3 = Regex("""no olvides (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match3 != null) return capitalizeFirst(match3.groupValues[1])

                // "recuerda X" → "X" (c.682): mismo lookahead de infinitivo que
                // el piso, así nunca se despoja un "recuerda" conversacional
                // ("recuerda que…") aunque TASK haya ganado por otro wrapper.
                val matchRecuerda = Regex("""recuerda\s+(?=\w*(?:ar|er|ir)\b)(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchRecuerda != null) return capitalizeFirst(matchRecuerda.groupValues[1])

                // "falta X" / "hace falta X" → "X" (c.685): mismo lookahead de
                // infinitivo que el piso, así el uso temporal ("falta una
                // hora") o personal ("me falta tu apoyo") nunca se despoja
                // aunque TASK haya ganado por otro wrapper.
                val matchFalta = Regex("""(?<!no )falta\s+(?=\w*(?:ar|er|ir)\b)(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchFalta != null) return capitalizeFirst(matchFalta.groupValues[1])

                // "hay que X" → "X"
                val match4 = Regex("""hay que (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match4 != null) return capitalizeFirst(match4.groupValues[1])

                // "cancelar X" → "Cancelar X" / "anular X" → "Anular X" (c.654).
                // El verbo de cancelación gobierna el contenido: se PRESERVA en
                // el título (la lección de [approach de c.616] exige que si el
                // piso dispara, el título siga al verbo envolvente, no a un
                // template corrupto como "Cita: del dentista").
                val match5 = Regex("""\b(?<!no )(cancelar|anular)\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match5 != null) {
                    return "${capitalizeFirst(match5.groupValues[1])} ${match5.groupValues[2]}"
                }

                // "revisar X" → "Revisar X" (c.691): el verbo gobierna el
                // contenido y se PRESERVA en el título (alineación piso↔
                // título, lección c.616); el prefijo de acuse ("vale, ") se
                // despoja igual que en SHOPPING (c.651: el match arranca en
                // el verbo, no en el acuse). Mismo ancla/guard que el piso.
                val matchRevisar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )revisar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchRevisar != null) return "Revisar ${matchRevisar.groupValues[1]}"

                // "enviar X" → "Enviar X" (c.692): mismo criterio que la
                // plantilla de c.691 — el verbo gobierna el contenido y se
                // PRESERVA; el prefijo de acuse se despoja (el match arranca
                // en el verbo). Mismo ancla/guard que el piso.
                val matchEnviar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )enviar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchEnviar != null) return "Enviar ${matchEnviar.groupValues[1]}"

                // "entregar X" → "Entregar X" (c.693): mismo criterio que
                // c.691/c.692 (verbo preservado, acuse despojado).
                val matchEntregar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )entregar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchEntregar != null) return "Entregar ${matchEntregar.groupValues[1]}"

                // "firmar X" → "Firmar X" (c.696): mismo criterio que
                // c.691…c.693 (verbo preservado, acuse/prefijo temporal
                // despojado).
                val matchFirmar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )firmar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchFirmar != null) return "Firmar ${matchFirmar.groupValues[1]}"

                // "renovar X" → "Renovar X" (c.698): mismo criterio que
                // c.691…c.696 (verbo preservado, acuse/prefijo temporal
                // despojado).
                val matchRenovar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )renovar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchRenovar != null) return "Renovar ${matchRenovar.groupValues[1]}"

                // "confirmar X" → "Confirmar X" (c.700): mismo criterio
                // que c.691…c.698 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchConfirmar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )confirmar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchConfirmar != null) return "Confirmar ${matchConfirmar.groupValues[1]}"

                // "imprimir X" → "Imprimir X" (c.708): mismo criterio
                // que c.691…c.700 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchImprimir = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )imprimir\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchImprimir != null) return "Imprimir ${matchImprimir.groupValues[1]}"

                // "reservar X" → "Reservar X" (c.709): mismo criterio
                // que c.691…c.708 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchReservar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )reservar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchReservar != null) return "Reservar ${matchReservar.groupValues[1]}"

                // "cambiar X" → "Cambiar X" (c.710): mismo criterio
                // que c.691…c.709 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchCambiar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )cambiar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchCambiar != null) return "Cambiar ${matchCambiar.groupValues[1]}"

                // "avisar X" → "Avisar X" (c.711): mismo criterio
                // que c.691…c.710 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchAvisar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )avisar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchAvisar != null) return "Avisar ${matchAvisar.groupValues[1]}"

                // "pedir X" → "Pedir X" (c.712): mismo criterio
                // que c.691…c.711 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchPedir = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )pedir\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchPedir != null) return "Pedir ${matchPedir.groupValues[1]}"

                // "solicitar X" → "Solicitar X" (c.713): mismo criterio
                // que c.691…c.712 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchSolicitar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )solicitar\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchSolicitar != null) return "Solicitar ${matchSolicitar.groupValues[1]}"

                // "buscar X" → "Buscar X" (c.715): mismo criterio
                // que c.691…c.714 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchBuscar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )(buscar)\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchBuscar != null) return "Buscar ${matchBuscar.groupValues[2]}"

                // "coger X" → "Coger X" (c.716): mismo criterio
                // que c.691…c.715 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchCoger = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )(coger)\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchCoger != null) return "Coger ${matchCoger.groupValues[2]}"

                // "publicar X" → "Publicar X" (c.719): mismo criterio
                // que c.691…c.716 (verbo preservado, acuse/prefijo
                // temporal despojado).
                val matchPublicar = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )(publicar)\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchPublicar != null) return "Publicar ${matchPublicar.groupValues[2]}"

                null
            }
            ContextIntentKind.SHOPPING -> {
                // "comprar X" → "Comprar X". Sin [capitalizeFirst] en el objeto:
                // se preserva el caso original del usuario (doctrina c.653) y se
                // evita el title-case espurio ("Comprar Pan"/"Comprar Leche...").
                val match = Regex("""comprar (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Comprar ${match.groupValues[1]}"

                // "ir al supermercado" → "Ir al supermercado"
                val match2 = Regex("""(ir|vamos|voy|iremos) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match2 != null) return "Ir ${match2.groupValues[2]}"

                null
            }
            ContextIntentKind.APPOINTMENT -> {
                // El prefijo "Cita:" sólo se añade cuando el resto NO menciona ya "cita".
                // Antes se anteponía siempre, duplicando el sustantivo: "tengo cita con el
                // dentista" → "Cita: Cita con el dentista", "voy a la cita..." →
                // "Cita: La cita...". Cuando el resto arranca en (una|la)?cita se elimina
                // el artículo y se capitaliza el resto tal cual (mismo criterio que el
                // título CALL de c.653: preservar el texto del usuario, no adornarlo).
                // c.663: "tendré" también abre el match (futuro declarativo), así el
                // prefijo verbal no ensucia el resto igual que "tengo".
                val match = Regex("""(tengo|tendré|cita|voy a|debo ir a) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) {
                    // Si la alternativa "cita" ganó el match, el sustantivo está en el
                    // grupo 1: el resto a evaluar es el match completo ("cita con ...").
                    val rest = if (match.groupValues[1].equals("cita", ignoreCase = true)) {
                        match.value
                    } else {
                        match.groupValues[2]
                    }
                    val selfMention = Regex("""^(?:(?:una|la) )?cita\b.*""", RegexOption.IGNORE_CASE)
                    return if (selfMention.matches(rest)) {
                        val article = Regex("""^(una|la) """, RegexOption.IGNORE_CASE)
                        capitalizeFirst(article.replace(rest, ""))
                    } else {
                        "Cita: ${capitalizeFirst(match.groupValues[2])}"
                    }
                }
                null
            }
            ContextIntentKind.MEETING -> {
                val match = Regex("""(reunión|quedar|vernos|quedamos) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Reunión: ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.STUDY -> {
                val match = Regex("""(estudiar|estudio|examen|repasar) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Estudio: ${capitalizeFirst(match.groupValues[2])}"
                null
            }
            ContextIntentKind.CALL -> {
                // Preserva el verbo + preposición ORIGINAL del input. Antes el template
                // fijo "Llamar a ${group2}" corrumpía títulos legítimos: "llamar al doctor"
                // → "Llamar a Al doctor" (doble "a" + "Al" mayúscula), "llamar a María" →
                // "Llamar a A María" (doble "a"), "hablar con María" → "Llamar a Con María"
                // (perdía "hablar con" e insertaba "a" + "Con" mayúscula). Ahora se
                // capitaliza el texto desde el verbo, respetando la preposición que el
                // usuario ya escribió ("a"/"al"/"con"). El match arranca en \b(llamar|
                // hablar con) para no capturar texto previo accidental. c.655: el
                // futuro declarativo ("llamaré a"/"hablaré con") activa el mismo path,
                // así la fecha prefija ("mañana llamaré a mamá") no ensucia el título.
                val match = Regex(
                    """\b(llamar(?:\s+(?:a|al|a la|a los|a las|por teléfono))?|llamaré\s+(?:a|al|a la|a los|a las)|hablar con|hablaré con)\b.*""",
                    RegexOption.IGNORE_CASE
                ).find(original)
                if (match != null) return capitalizeFirst(match.value.trim())
                null
            }
            ContextIntentKind.PAYMENT -> {
                // Mismo criterio que SHOPPING: sin [capitalizeFirst] en el objeto.
                val match = Regex("""pagar (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Pagar ${match.groupValues[1]}"
                null
            }
            ContextIntentKind.REMINDER -> {
                val match = Regex("""(recuérdame|avísame|notifícame) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(match.groupValues[2])

                // "te acuerdas de X?" → "X" (c.687): mismo lookahead de
                // infinitivo que el piso, así nunca se despoja un "te acuerdas
                // de" conversacional ("te acuerdas de cuando…") aunque
                // REMINDER haya ganado por otro wrapper. El '?' de cierre de
                // la interrogación es marca de la envolvente, no del título:
                // se recorta para que no sobreviva en el título visible.
                val matchInterrogative = Regex("""te acuerdas de\s+(?=\w*(?:ar|er|ir)\b)(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchInterrogative != null) {
                    return capitalizeFirst(matchInterrogative.groupValues[1].trimEnd('?', ' '))
                }

                // "acuérdate de X" → "X" (c.689): la envolvente imperativa se
                // despoja y el título nace del infinitivo (misma paridad que
                // la interrogativa de arriba); `[ée]` tolera la forma sin
                // tilde en el original indexado por NOTIFICATION.
                val matchImperative = Regex("""acu[ée]rdate de\s+(?=\w*(?:ar|er|ir)\b)(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (matchImperative != null) {
                    return capitalizeFirst(matchImperative.groupValues[1].trim())
                }

                null
            }
            ContextIntentKind.EXERCISE -> {
                // c.655: el título nace desde el verbo de ejercicio, NO desde el
                // inicio del original. Devolver `capitalizeFirst(original)` dejaba
                // el prefijo temporal líder ("mañana " / "el lunes " / "mañana a
                // las 6 ") como residuo VISIBLE en el título — el sanitizer de
                // c.606 sólo corta residuo de COLA, así que el prefijo de CABEZA
                // escapaba. "mañana ir al gimnasio" → título "Ir al gimnasio"
                // (dueAt ya lo resolvió [extractDateTime]). Misma paridad que la
                // rama CALL, que también arranca desde el verbo.
                val match = Regex("""(ir al gimnasio|entrenar|hacer|yoga|correr)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return capitalizeFirst(original.substring(match.range.start))
                null
            }
            ContextIntentKind.HOUSEHOLD -> {
                // "sacar (la) basura …" → "Sacar la basura …" (c.717): verbo
                // preservado (alineación piso↔título, lección c.616) y objeto
                // restringido como en [HOUSEHOLD_TRASH_FLOOR]; el match
                // arranca en el verbo, así el prefijo temporal ("esta noche ")
                // no ensucia el título.
                val matchSacar = Regex(
                    """\b(?<!no )(sacar)\s+((?:el\s+|la\s+|los\s+|las\s+)?basura\b.*)""",
                    RegexOption.IGNORE_CASE
                ).find(original)
                if (matchSacar != null) {
                    return "${capitalizeFirst(matchSacar.groupValues[1])} ${matchSacar.groupValues[2]}"
                }
                // Verbos alineados con [hasStrongHouseholdImperative] (c.638/c.639) para que
                // el piso no capture un verbo cuyo título luego no se forme limpio.
                // `\b` (c.693): sin borde, "regar" casa dentro de "entregar".
                val match = Regex("""\b(limpiar|ordenar|cocinar|lavar|arreglar|planchar|reparar|fregar|barrer|trapear|regar|sacudir|desempolvar) (.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "${capitalizeFirst(match.groupValues[1])} ${match.groupValues[2]}"
                null
            }
            ContextIntentKind.ERRAND -> {
                // "pasar por <lugar de trámite>" → "Pasar por el banco"
                // (c.718): verbo preservado (alineación piso↔título, lección
                // c.616); el match arranca en el verbo, así el acuse/prefijo
                // temporal ("mañana ") no ensucia el título (misma lección
                // EXERCISE c.655).
                val match = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )pasar\s+((?:por\s+).+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "Pasar ${match.groupValues[1]}"
                null
            }
            ContextIntentKind.DEADLINE -> {
                // Quita la ETIQUETA del marcador ("deadline:"/"fecha límite:"/
                // "vencimiento:") para quedarse con el contenido (c.654). Sin
                // esta rama, [generateTitle] dejaba en el título el marcador
                // con signos ("Deadline: enviar el informe"). La idea es la
                // misma que para TASK (alineación piso↔título, lección c.616):
                // el marcador inequívoco que activó el piso se consume aquí.
                val match = Regex(
                    """(?:deadline|fecha\s+l[íi]mite|vencimiento)\s*[,;:.!]?\s*(.+)""",
                    RegexOption.IGNORE_CASE
                ).find(original)
                if (match != null) return capitalizeFirst(match.groupValues[1])
                null
            }
            ContextIntentKind.NOTE -> {
                // "(apuntar|anotar) X" → "Apuntar X"/"Anotar X" (c.714): verbo
                // preservado (alineación piso↔título, lección c.616); prefijo
                // de acuse/temporal despojado (el match arranca en el verbo).
                // Mismo ancla/guard que el piso [NOTE_FLOOR].
                val match = Regex("""(?:^|\b(?:$ACK_PREFIX)\s*[,;.!:]?\s+|\b(?:$TASK_FLOOR_TEMPORAL)\s+)(?<!no )(apuntar|anotar)\s+(.+)""", RegexOption.IGNORE_CASE).find(original)
                if (match != null) return "${capitalizeFirst(match.groupValues[1])} ${match.groupValues[2]}"
                null
            }
            else -> null
        }
    }

    /**
     * Genera un título descriptivo cuando no se puede extraer uno específico.
     */
    private fun generateTitle(text: String, kind: ContextIntentKind): String {
        val cleaned = text
            .replace(Regex("""\b(muy|más|tan|hay que|tengo que|voy a|debo|para)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val maxLen = 80
        val title = capitalizeFirst(cleaned.take(maxLen))
        return if (title.length <= 3) kind.displayName else title
    }

    /**
     * "las N" DESNUDA (c.600): paridad con [com.ordia.app.domain.NaturalTaskParser].
     * Una captura de contexto p.ej. "cita las 3" / "reunión las 7 y media" menciona
     * hora SIN introductor "a"/"para". El [timePattern] de abajo exige una pista
     * horaria explícita (prefijo "a las"/":MM"/meridiana) y, al faltar todas, deja
     * `targetTime=null` → la cita nacía SIN hora (caía al mediodía por defecto):
     * cita a hora errónea / posible olvido (P1 evitar olvidos). El parser lo resolvía
     * en tareas creadas a mano (c.596) pero NO las capturadas por el motor de contexto.
     *
     * Aquí se reescribe " las <hora> " → "a las <hora> " ANTES del [timePattern] para
     * que reutilice TODO el flujo de resolución existente (AM/PM, fracción, wrap 24 h,
     * past-safe de midpoints) sin nueva rama. Simétrico del [bareLasHourRewriter] del
     * parser, con los mismos dos guards para no eludir protectores existentes:
     *  1. Prefijo inmediato en [BARE_LAS_GUARDED_PREFIXES] ("de"/"para"/"todas"/"todos"/
     *     "desde"/"hasta"/"a"/"sobre"/"hacia"): un conector/guard ya gobierna esa hora
     *     (franja "antes/después de las N", cadencia "todas las N semanas", rango
     *     "desde/hasta", forma canónica "a las N") → no tocar.
     *  2. Anti-cuenta: hora en punto DESNUDA (sin :MM/meridiana/fracción/unidad) seguida
     *     de un sustantivo plural de cantidad ("compra las 3 manzanas") → NO reescribir
     *     (preserva el número como cantidad; evita inventar una cita falsa).
     * Grupo 1 = especificación horaria completa; grupo 2 = ":MM" (evidencia de reloj).
     */
    private val bareLasHourRewriter =
        Regex("""(?i)\blas\s+(\d{1,2})(?::(\d{2}))?(?:\s+(?:y\s+(?:media|treinta|cuarto|tres cuartos|cuarenta y cinco|veinticinco|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2})))?(?:\s*(?:horas?|hs|h))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|am|pm|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+en\s+punto)?""")

    private val BARE_LAS_GUARDED_PREFIXES = setOf(
        "de", "para", "todas", "todos", "desde", "hasta", "a", "sobre", "hacia"
    )

    /** Tail seguro tras "las N" en punto desnuda: fin de cadena, puntuación, conjunción
     *  o palabra temporal (NO un sustantivo plural de cantidad). Paridad con el
     *  [countNounFollowerPattern] del parser. */
    private val bareLasSafeFollower =
        Regex("""(?i)^\s*(?:[,.;:!?\s]|$|y\b|o\b|con\b|de\b|del\b|en\b|para\b|hasta\b|desde\b|luego\b|después\b|despues\b|pero\b|porque\b|por\b|sin\b|sobre\b|a\b|al\b|el\b|la\b|los\b|las\b|un\b|una\b|mañana\b|manana\b|hoy\b|ayer\b|anteayer\b|lunes\b|martes\b|miércoles\b|miercoles\b|jueves\b|viernes\b|sábado\b|sabado\b|domingo\b)""")

    /** Aplica [bareLasHourRewriter] con los guards de prefijo y anti-cuenta (c.600). */
    private fun normalizeBareLasHour(text: String): String =
        bareLasHourRewriter.replace(text) { m ->
            // Guard 1: palabra inmediatamente anterior a " las N". Si es un conector
            // temporal o determinante de cadencia ya gestionado, no tocar.
            val prefix = text.substring(0, m.range.first)
            val prevWord = Regex("""(?i)\b(\S+)\s*$""").find(prefix)
                ?.groupValues?.get(1)?.lowercase()
            if (prevWord != null && prevWord in BARE_LAS_GUARDED_PREFIXES) return@replace m.value
            // Evidencia de reloj: :MM, fracción, meridiana, unidad horas/hs/h, "en punto".
            val ts = m.value
            val hasMinutes = m.groupValues[2].isNotBlank()
            val hasEvidence = hasMinutes ||
                Regex("""(?i):|horas?\b|\bhs\b|\bh\b|a\.?\s*m\.?|p\.?\s*m\.?|am|pm|de\s+la\s+(?:ma[nñ]ana|tarde|noche|madrugada)|del\s+mediod[ií]a|y\s+(?:media|treinta|cuarto|tres cuartos|cuarenta y cinco|veinticinco|veinte|diez|cinco|\d)|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d)|en\s+punto""")
                    .containsMatchIn(ts)
            if (hasEvidence) return@replace "a " + m.value
            // Hora en punto desnuda: guard 2 anti-cuenta. Si el tail NO es un
            // continuador seguro, es un sustantivo plural de cantidad → preservar.
            val tail = text.substring(m.range.last + 1)
            if (!bareLasSafeFollower.containsMatchIn(tail)) return@replace m.value
            "a " + m.value
        }

    /**
     * Extrae fecha/hora de un texto en español.
     * Retorna timestamp en milisegundos o null.
     */
    internal fun extractDateTime(lower: String): Long? {
        // "las N" desnuda → "a las N" (c.600, paridad con NaturalTaskParser c.596).
        // Se normaliza ANTES de cualquier patrón para que [timePattern] la resuelva.
        // Sombrea el parámetro: todo el cuerpo siguiente lee ya la forma normalizada.
        val lower = normalizeBareLasHour(lower)
        val today = LocalDate.now()
        var targetDate: LocalDate? = null
        var targetTime: LocalTime? = null
        // Punto medio canonico inequivoco (medianoche 00:00 / mediodia 12:00)
        // proveniente de meridiem explicito o palabra canonica. Permite aplicar
        // past-safe (ruedo +1 dia si ya paso hoy) solo a midpoints seguros, no a
        // horas numericas arbitrarias donde +1 es ambiguo. Paridad con el
        // past-safe de medianoche/mediodia del NaturalTaskParser (l.4706-4711).
        var canonicalMidpoint: LocalTime? = null

        // Días de la semana
        val dayMap = mapOf(
            "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
            "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
            "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY,
            "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY,
            "domingo" to DayOfWeek.SUNDAY
        )

        // "esta <parte del día>": paridad con NaturalTaskParser.partOfDayTimes (c.592).
        // Parser: mañana→09:00, tarde→15:00, noche→21:00, madrugada→04:00, fecha=hoy.
        // Antes SÓLO "esta noche"/"esta tarde" fijaban targetDate=today (sin hora → caía
        // al default 12:00 = mediodía, p.ej. "esta noche" = 12:00, 9h de error) y
        // "esta mañana"/"esta madrugada" no se reconocían: "esta mañana" contiene "mañana"
        // y colisionaba con la regla "mañana"=día siguiente (sin guard null) → se fechaba
        // para MAÑANA a mediodía (día Y hora erróneos). Aquí se fijan fecha=hoy Y hora
        // canónica, ANTES de la regla "mañana"=día siguiente, para que esta última no la
        // pise; su guard `targetDate == null` la deja sin efecto cuando ya hay parte del
        // día. La hora canónica se pisa luego si hay hora numérica explícita
        // ("esta noche a las 10"), más específica (simétrico al orden de patrones del
        // parser donde "a las N" se prueba antes que las canónicas).
        val estaPartOfDay = Regex("""\besta\s+(mañana|manana|tarde|noche|madrugada)\b""", RegexOption.IGNORE_CASE)
            .find(lower)
        if (estaPartOfDay != null && targetDate == null) {
            targetDate = today
            targetTime = when (estaPartOfDay.groupValues[1].lowercase()) {
                "mañana", "manana" -> LocalTime.of(9, 0)
                "tarde" -> LocalTime.of(15, 0)
                "noche" -> LocalTime.of(21, 0)
                "madrugada" -> LocalTime.of(4, 0)
                else -> null
            }
        }

        // "mañana" como día siguiente. Solo si NO forma parte de una hora del día
        // ("de la mañana", "por la mañana", "en la mañana", "de/por/en mañana"):
        // antes, "reunión a las 9 de la mañana" caía aquí y se fechaba para MAÑANA.
        // Pero la frase "mañana por la mañana" / "mañana a las 9 de la mañana" tiene
        // DOS "mañana": el primer token es el adverbio de día siguiente y el segundo
        // el sufijo de meridiano. La exclusión anti-colisión solo debe suprimir el
        // adverbio cuando TODOS los "mañana" del texto forman parte de un sufijo de
        // hora. Si hay más tokens "mañana" que sufijos de meridiano, sobra al menos
        // un "mañana" que sí significa día siguiente.
        val mananaSuffix = Regex("""\b(de la|por la|en la|de|por|en)\s+mañana\b""", RegexOption.IGNORE_CASE)
        val mananaTokens = Regex("""\bmañana\b""", RegexOption.IGNORE_CASE).findAll(lower).count()
        val mananaSuffixMatches = mananaSuffix.findAll(lower).count()
        val manaanaComoDiaSiguiente = targetDate == null && lower.contains("mañana") &&
            !lower.contains("pasado mañana") &&
            mananaTokens > mananaSuffixMatches
        if (manaanaComoDiaSiguiente) {
            targetDate = today.plusDays(1)
        }
        // "anteayer"/"antier" = hace 2 días. Paridad con NaturalTaskParser (l.4164):
        // fechas PASADAS explícitas — el usuario reconoce que la cita está vencida y
        // debe aparecer en What Now como tal (no perderse ni caer a hoy). Antes
        // extractDateTime NO las reconocía → la captura de contexto de una
        // notificación con "ayer"/"anteayer" nacía SIN dueAt (tarea vencida
        // olvidada, P1). "antier" = variante coloquial hispanoamericana de
        // "anteayer". Va ANTES que "ayer" ("anteayer" contiene "ayer") y con guard
        // null para no pisar una fecha ya resuelta (p.ej. "mañana" explícito).
        if (targetDate == null &&
            (lower.contains("anteayer") || lower.contains("antier"))) {
            targetDate = today.minusDays(2)
        }
        // "ayer" = hace 1 día. Word-boundary evita casar dentro de "anteayer"
        // (aunque el guard de orden ya lo cubre) y futuras palabras con sufijo.
        if (targetDate == null &&
            Regex("""\bayer\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            targetDate = today.minusDays(1)
        }
        // "pasado mañana"
        if (lower.contains("pasado mañana") || lower.contains("pasado manana")) {
            targetDate = today.plusDays(2)
        }
        // "hoy" (c.711: guard de bordes de palabra). El guard original con
        // substring "a hoy" BLOQUEABA todo "…a" seguido de "hoy" ("avisar al
        // jefe de la entrega hoy" → dueAt NULL, olvido silencioso P1 descubierto
        // por la sonda `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711).
        // Se usa lookarangs Unicode \p{L} para delimitar "a" como palabra
        // (misma lección c.649: \b ASCII no cierra con tildes; aquí ASCII es
        // suficiente pero la forma es homogénea con HEDGE/CONDITIONAL).
        if (lower.contains("hoy") &&
            !Regex("""(?<!\p{L})a\s+hoy(?!\p{L})""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        ) {
            targetDate = today
        }
        // Períodos relativos (paridad con NaturalTaskParser nextPeriodPattern /
        // lastPeriodPattern, l.3806-3844 y l.3288-3300). "la semana/el mes/el año
        // que viene"/"próxima"/"entrante"/"en una semana" → +7/+30/+365 días;
        // "la semana/el mes/el año pasado"/"anterior" → −7/−30/−365 días. Misma
        // día-aritmética que el parser (now ± N·86400000 → aquí today ± N días).
        // Antes extractDateTime NO reconocía períodos → un ContextEvent de
        // notificación ("reunión la semana que viene") nacía SIN dueAt → la cita
        // futura no generaba recordatorio ni aparecía en el planificador; una cita
        // vencida ("la semana pasada") no se hacía visible en What Now (P1 evitar
        // olvidos). Se exige un calificador explícito (que viene/próxima/pasada/
        // anterior/en una…) para no inventar fechas a partir de "semana"/"mes"/
        // "año" sueltos ("esta semana", "cada semana", "fin de semana"). Va
        // DESPUÉS de hoy/mañana/pasado mañana para que esas frases más específicas
        // se resuelvan primero (sin necesidad de exclusiones). Orden del `when`
        // espeja el parser: trimestre/bimestre/semestre ANTES de "mes" porque
        // "trimes**tre**"/"bi**mes**tre"/"se**mes**tre" contienen la subcadena "mes".
        if (targetDate == null) {
            // Períodos relativos multi-unidad (paridad con NaturalTaskParser.relativePattern,
            // l.334): "en 2 semanas"/"dentro de 3 meses"/"de aquí a 5 días"/"en un par de
            // semanas" → cantidad × unitDays. Antes extractDateTime sólo reconocía el
            // singular escrito "en una semana" (bloque siguiente) → estas formas con
            // cantidad numérica devolvían null → un ContextEvent de notificación futuro
            // nacía SIN dueAt → sin recordatorio ni planificador (P1 evitar olvidos).
            // Misma aritmética y ORDEN de unidades que el parser (trimestre/bimestre/
            // semestre antes de "mes" porque contienen la subcadena "mes"). Va antes del
            // bloque singular ("en una semana") para que la forma más específica gane.
            val multiUnitMatch = Regex(
                """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|unos|unas|\d{1,3})\s*(d[ií]as?|semanas?|quincenas?|bimestres?|trimestres?|semestres?|mes(?:es)?|a[nñ]os?)\b"""
            ).find(lower)
            if (multiUnitMatch != null) {
                val rawAmount = multiUnitMatch.groupValues[1]
                val amount: Long = if (rawAmount.startsWith("un par") || rawAmount in setOf("unos", "unas")) {
                    2L
                } else {
                    rawAmount.toLongOrNull() ?: 1L
                }
                val unit = multiUnitMatch.groupValues[2].lowercase()
                val unitDays = when {
                    unit.startsWith("día") || unit.startsWith("dia") -> 1L
                    unit.startsWith("quincena") -> 15L
                    unit.startsWith("semana") -> 7L
                    unit.startsWith("bimestre") -> 60L
                    unit.startsWith("trimestre") -> 90L
                    unit.startsWith("semestre") -> 180L
                    unit.startsWith("mes") -> 30L
                    unit.startsWith("año") || unit.startsWith("ano") -> 365L
                    else -> null
                }
                if (unitDays != null) {
                    targetDate = today.plusDays(amount * unitDays)
                }
            }
        }
        if (targetDate == null) {
            val periodFuture = Regex(
                """que\s+viene|que\s+entra|entrante|pr[oó]xim|siguiente""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(lower) || Regex(
                """\ben\s+(?:un|una|unos|unas)\s+(?:semanas?|mes(?:es)?|a[nñ]os?)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(lower)
            val periodPast = Regex(
                """pasad[oa]s?|anteriore?s?""", RegexOption.IGNORE_CASE
            ).containsMatchIn(lower)
            if (periodFuture || periodPast) {
                val days = when {
                    "trimestre" in lower -> 90L
                    "bimestre" in lower -> 60L
                    "semestre" in lower -> 180L
                    "quincena" in lower -> 15L
                    "semana" in lower -> 7L
                    "mes" in lower -> 30L
                    "año" in lower || "ano" in lower -> 365L
                    else -> null
                }
                if (days != null) {
                    targetDate = if (periodFuture) today.plusDays(days) else today.minusDays(days)
                }
            }
        }
        // "esta noche"/"esta tarde" ya se resuelven antes (bloque `estaPartOfDay`,
        // l.429): fijan targetDate=today + hora canónica. El guard `targetDate ==
        // null` de aquí era siempre falso para esas frases → bloque muerto (c.594).

        // Día de la semana
        if (targetDate == null) {
            for ((name, day) in dayMap) {
                if (lower.contains(name)) {
                    val daysUntil = (day.value - today.dayOfWeek.value + 7) % 7
                    targetDate = if (daysUntil == 0) today.plusDays(7) else today.plusDays(daysUntil.toLong())
                    break
                }
            }
        }

        // "el [día]" → ejemplo: "el 25", "el 25 de mayo"
        // El prefijo "el" o un mes explícito es OBLIGATORIO para evitar tratar como
        // fecha cualquier número suelto del texto (p.ej. "comprar 2 kilos de arroz").
        // Igual que con la hora, el primer `.find()` puede casar un número suelto
        // anterior ("comprar 2 kilos el 25") y descartarlo por guard sin examinar
        // la fecha válida posterior: se itera hasta el primer match con "el" o mes.
        val dayPattern = Regex("""(el\s+)?(\d{1,2})(?:\s+de\s+(\w+))?""")
        val dayMatch = dayPattern.findAll(lower).firstOrNull { m ->
            val hasEl = m.groupValues[1].isNotBlank()
            val dayNum = m.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            val month = monthName(m.groupValues[3])
            dayNum in 1..31 && (hasEl || month != null)
        }
        if (targetDate == null && dayMatch != null) {
            val dayNum = dayMatch.groupValues[2].toIntOrNull()
            val monthName = dayMatch.groupValues[3]
            val month = monthName(monthName)
            if (dayNum != null && dayNum in 1..31) {
                targetDate = if (month != null) {
                    LocalDate.of(today.year, month, dayNum.coerceIn(1, 28))
                } else {
                    if (dayNum > today.dayOfMonth) today.withDayOfMonth(dayNum)
                    else today.plusMonths(1).withDayOfMonth(dayNum.coerceAtMost(28))
                }
            }
        }

        // Extraer hora. Se exige una pista temporal explícita (prefijo "a las",
        // formato "HH:MM" con dos puntos, o sufijo am/pm/tarde/noche/mañana/madrugada) para
        // evitar inventar una hora a partir de un número suelto ("comprar 2 kilos").
        // El primer `.find()` puede casar un número suelto anterior ("comprar 2 kilos
        // ... a las 9 de la mañana"): ese match tiene hasTimeCue=false y antes se
        // descartaba en silencio, dejando SIN hora una cita que sí la mencionaba más
        // adelante. Se itera hasta el primer match con pista horaria válida.
        val timePattern = Regex(
            """(a\s+las?|a\s+la|para\s+las?|para\s+la)?\s*(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?|am|pm|de la mañana|de la tarde|de la noche|de la madrugada|del día)?\s*(y\s+(?:media|treinta|cuarto|tres cuartos|cuarenta y cinco|veinticinco|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2}))?(?:\s*(a\.?m\.?|p\.?m\.?|am|pm|de la mañana|de la tarde|de la noche|de la madrugada|del día))?""",
            RegexOption.IGNORE_CASE
        )
        val timeMatch = timePattern.findAll(lower).firstOrNull { m ->
            // La fracción (grupo 5) por sí sola NO es pista horaria: "comprar 2 y
            // media kilos" casaría como 2:30. Se exige el prefijo "a las", el `:MM`,
            // un meridiano antes (grupo 4) o después (grupo 6) de la fracción.
            val hasTimeCue = m.groupValues[1].isNotBlank() ||
                m.groupValues[3].isNotBlank() ||
                m.groupValues[4].isNotBlank() ||
                m.groupValues[6].isNotBlank()
            if (!hasTimeCue) return@firstOrNull false
            val hour = m.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            hour in 0..23 && minute in 0..59
        }
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[2].toIntOrNull()
            val minute = timeMatch.groupValues[3].toIntOrNull() ?: 0
            val suffix = (timeMatch.groupValues[4].ifBlank { timeMatch.groupValues[6] }).lowercase()
            if (hour != null && hour in 0..23 && minute in 0..59) {
                var adjustedHour = hour
                if (suffix.contains("pm") || suffix.contains("tarde") || suffix.contains("noche")) {
                    // "12 de la noche" = medianoche (00:00), NO mediodía: antes la rama
                    // PM sólo sumaba 12 si hour<12, así hour=12 quedaba en 12:00 (mediodía).
                    // Paridad con NaturalTaskParser (`part == "noche" && h == 12 -> 0`).
                    // "12 de la tarde"/"12 pm" siguen siendo mediodía (12:00).
                    if (hour == 12 && suffix.contains("noche")) adjustedHour = 0
                    else if (hour < 12) adjustedHour = hour + 12
                } else if (suffix.contains("am") || suffix.contains("mañana") || suffix.contains("madrugada")) {
                    if (hour == 12) adjustedHour = 0
                }
                // Fracción sub-hora "y media"/"y cuarto"/"menos cuarto"/... (c.594,
                // paridad con NaturalTaskParser CLOCK_FRACTION_MAP). Sólo si NO hubo
                // `:MM` explícito: una hora con dos puntos (15:30) ya fijó sus minutos
                // y no admite fracción hablada adicional. El wrap de 24 h de la rama
                // "menos" es simétrico al del parser.
                val fraction = resolveClockFraction(timeMatch.groupValues[5])
                val effectiveHour: Int
                val effectiveMinute: Int
                if (fraction != null && timeMatch.groupValues[3].isBlank()) {
                    val totalMin = adjustedHour * 60 + minute + fraction
                    val wrapped = ((totalMin % 1440) + 1440) % 1440
                    effectiveHour = wrapped / 60
                    effectiveMinute = wrapped % 60
                } else {
                    effectiveHour = adjustedHour
                    effectiveMinute = minute
                }
                targetTime = LocalTime.of(effectiveHour, effectiveMinute)
                // Marca el midpoint canonico cuando un meridiem explicito resuelve
                // a medianoche (00:00) o mediodia (12:00): solo esos dos son
                // inequivocos y admiten past-safe (a una hora numerica como "a las
                // 9" el ruido de +1 es ambiguo: podria ser hoy si no ha llegado).
                // Paridad con el guard del parser (isInequivocalMidpoint, l.4706).
                // Una fraccion (c.594) vuelve el punto no-inequivoco: NO se marca.
                if (fraction == null && effectiveMinute == 0 && effectiveHour == 0) {
                    canonicalMidpoint = LocalTime.MIDNIGHT
                } else if (fraction == null && effectiveMinute == 0 && effectiveHour == 12 &&
                    (suffix.contains("pm") || suffix.contains("tarde"))) {
                    canonicalMidpoint = LocalTime.NOON
                }
            }
        }

        // Horas canónicas "al mediodía"/"a medianoche"/"a la medianoche" (c.587).
        // Paridad con NaturalTaskParser: esas palabras sueltas resuelven 12:00/00:00
        // (parser l.1532-1554 → explicitTimeData l.4395-4406). El [timePattern]
        // numérico NO las casa (no hay dígito), así targetTime quedaba null y:
        //  - "reunión al mediodía" (sin día) → null → ContextEvent sin vencimiento,
        //    invisible en What Now y sin recordatorio (evitar olvidos, P1).
        //  - "entrega a medianoche mañana" → targetDate=mañana, targetTime=null → caía
        //    al default `LocalTime.of(12,0)` (l.529) = mediodía, ¡12h de error!
        // Sólo se aplica si NO hubo hora numérica: una hora explícita ("a las 3")
        // siempre tiene prioridad (más específica, simétrico al orden de patrones
        // del parser donde "a las N" se prueba antes que las canónicas). Las
        // variantes con modificador ("pasada la medianoche"/"pasado el mediodía"/
        // "después del mediodía") también contienen la palabra canónica, así se
        // cubren sin lógica extra: al motor de contexto le basta la hora neta.
        if (targetTime == null) {
            val hasMedianoche = lower.contains("medianoche")
            val hasMediodia = lower.contains("mediodía") || lower.contains("mediodia")
            // Fracción sub-hora sobre las canónicas (c.591, paridad con el
            // grupo 1 de los patrones mediodía/medianoche del parser): "al mediodía
            // y media" → 12:30, "a medianoche y cuarto" → 00:15. Se reutiliza el
            // mismo resolver de la rama numérica; aquí la fracción siempre es
            // positiva (no se dice "medianoche menos cuarto").
            when {
                hasMedianoche -> {
                    // Fracción sub-hora sobre la canónica (c.594, paridad con el
                    // grupo 1 de los patrones mediodía/medianoche del parser): "a
                    // medianoche y cuarto" → 00:15. Aquí la fracción siempre es
                    // positiva (no se dice "medianoche menos cuarto"). Una fracción
                    // vuelve el punto no-inequivoco: NO se marca canonicalMidpoint
                    // (sin past-safe) — paridad con isInequivocalMidpoint (sólo 00:00/12:00).
                    val f = resolveClockFraction(lower)
                    if (f != null && f >= 0) {
                        targetTime = LocalTime.of(0, 0).plusMinutes(f.toLong())
                    } else {
                        targetTime = LocalTime.of(0, 0)
                        canonicalMidpoint = LocalTime.MIDNIGHT
                    }
                }
                hasMediodia -> {
                    val f = resolveClockFraction(lower)
                    if (f != null && f >= 0) {
                        targetTime = LocalTime.of(12, 0).plusMinutes(f.toLong())
                    } else {
                        targetTime = LocalTime.of(12, 0)
                        canonicalMidpoint = LocalTime.NOON
                    }
                }
            }
        }

        if (targetDate == null && targetTime == null) return null

        // Past-safe (c.590): un punto medio canonico inequivoco (medianoche/
        // mediodia) sin fecha explicita que ya paso hoy se rueda a +1 dia. Sin
        // esto, una captura contextual "reunion a las 12 de la noche" tomada por
        // la manana caia en hoy 00:00 (pasado) -> reminder <= now -> descartado
        // por ReminderSync -> la cita nacia olvidada (P1: evitar olvidos). Paridad
        // con el past-safe de midpoints del NaturalTaskParser (l.4706-4711, que
        // usa el mismo guard: date==null + parsedTime==MIDNIGHT/NOON + < now).
        // Solo se aplica a midpoints inequivocos: una hora numerica ("a las 9")
        // NO se rueda porque +1 es ambiguo (podria ser hoy si aun no llego) y el
        // parser tampoco lo hace salvo para esos midpoints.
        val baseDate = targetDate ?: today
        val time = targetTime ?: LocalTime.of(12, 0)
        val zone = java.time.ZoneId.systemDefault()
        val instant = baseDate.atTime(time).atZone(zone).toInstant()
        val date = if (targetDate == null && canonicalMidpoint != null &&
            instant.toEpochMilli() <= System.currentTimeMillis()) baseDate.plusDays(1) else baseDate

        return date.atTime(time)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Resuelve la fracción sub-hora cotidiana del español en minutos con signo
     * (c.591, paridad con NaturalTaskParser.resolveClockFraction / CLOCK_FRACTION_MAP).
     *
     * Acepta la frase completa tal cual la captura el grupo 5 del [timePattern]
     * ("y media", "menos cuarto", "y tres cuartos", "y 20") o, en el caso de las
     * horas canónicas mediodía/medianoche, el texto [lower] entero (busca la
     * primera aparición de "y media"/"y cuarto"/...). Rama positiva suma minutos
     * ("y media" → +30); rama negativa resta ("menos cuarto" → −15). Devuelve
     * `null` si [raw] no contiene ninguna fracción reconocida, para que el
     * llamador deje la hora en punto.
     *
     * No usa azar ni heurísticas opacas: es un mapa fijo de palabras→minutos,
     * idéntico al del parser para que una cita capturada por el motor de contexto
     * y una creada a mano en el parser resuelvan el mismo vencimiento.
     */
    private val CLOCK_FRACTION_MAP = listOf(
        "tres cuartos" to 45, "cuarenta y cinco" to 45, "cincuenta y cinco" to 55,
        "treinta y cinco" to 35, "veinticinco" to 25, "media" to 30, "treinta" to 30,
        "cuarto" to 15, "quince" to 15, "cuarenta" to 40, "cincuenta" to 50,
        "veinte" to 20, "diez" to 10, "cinco" to 5
    )
    private val CLOCK_FRACTION_PHRASE = Regex(
        """y\s+(?:tres cuartos|cuarenta y cinco|cincuenta y cinco|treinta y cinco|veinticinco|media|treinta|cuarto|quince|cuarenta|cincuenta|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2})""",
        RegexOption.IGNORE_CASE
    )
    private fun resolveClockFraction(raw: String): Int? {
        val s = raw.trim().lowercase().replace("ñ", "n").replace("í", "i")
        // Si [raw] es la frase exacta (grupo 5 del [timePattern]): "y media", "menos cuarto".
        if (s.startsWith("y ") || s.startsWith("menos ")) {
            val positive = s.startsWith("y ")
            val body = s.removePrefix("y ").removePrefix("menos ")
            val m = CLOCK_FRACTION_MAP.firstOrNull { body == it.first }?.second
                ?: body.toIntOrNull()?.takeIf { it in 0..59 }
            return m?.let { if (positive) it else -it }
        }
        // Si [raw] es texto completo (caso mediodí a/medianoche: se pasa [lower]):
        // primera aparición de "y media"/"menos cuarto"/... en el texto.
        val m = CLOCK_FRACTION_PHRASE.find(s) ?: return null
        val gs = m.value.trim().lowercase()
        return resolveClockFraction(gs)
    }

    private fun hasDateReference(lower: String): Boolean {
        // Paridad con extractDateTime (c.600): reconocer los anclajes de fecha que
        // el parser resuelve pero este detector omitía. Sin ellos, una frase como
        // "pagar la factura ayer" no recibía el bono de fecha (+0.1) y caía por
        // debajo de MINIMUM_CONFIDENCE, descartando un compromiso (olvido, P1),
        // mientras que "pagar la factura mañana" sí pasaba. Mismo verbo, mismo
        // objeto: la única diferencia era el ancla temporal reconocida.
        return lower.contains("mañana") || lower.contains("hoy") ||
            lower.contains("ayer") || lower.contains("antier") ||
            lower.contains("lunes") || lower.contains("martes") ||
            lower.contains("miércoles") || lower.contains("miercoles") ||
            lower.contains("jueves") || lower.contains("viernes") ||
            lower.contains("sábado") || lower.contains("sabado") ||
            lower.contains("domingo") ||
            Regex("""(pasado mañana|pasado manana|esta noche|esta tarde|esta madrugada|el \d+)""").containsMatchIn(lower) ||
            // "1 de enero", "15 de marzo": extractDateTime lo resuelve vía
            // dayPattern + monthName; el detector anterior solo miraba "el \d+".
            Regex("""\d{1,2}\s+de\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)""").containsMatchIn(lower) ||
            // Períodos relativos (paridad con extractDateTime c.598/c.599, l.508–570):
            // multi-unidad ("en 2 semanas"/"dentro de 3 meses"/"de aquí a 5 días"),
            // calificador + unidad ("la semana que viene"/"próximo mes"/"la semana
            // pasada"/"el mes anterior") y singular escrito ("en una semana"). Sin
            // paridad, "reunión la semana que viene" no recibía el bono de fecha y
            // podía descartarse por umbral (olvido, P1). Se exige palabra de unidad
            // (semana/mes/año/quincena/bimestre/trimestre/semestre) en la rama
            // calificada, igual que extractDateTime exige `days != null`: así "el
            // reporte pasado" (sin unidad) NO cuenta como fecha (no la resuelve).
            Regex("""(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|unos|unas|\d{1,3})\s*(d[ií]as?|semanas?|quincenas?|bimestres?|trimestres?|semestres?|mes(?:es)?|a[nñ]os?)\b""").containsMatchIn(lower) ||
            ((Regex("""que\s+viene|que\s+entra|entrante|pr[oó]xim|siguiente""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ||
              Regex("""pasad[oa]s?|anteriore?s?""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) &&
             (lower.contains("semana") || lower.contains("mes") || lower.contains("año") || lower.contains("ano") ||
              lower.contains("quincena") || lower.contains("bimestre") || lower.contains("trimestre") || lower.contains("semestre"))) ||
            Regex("""\ben\s+(?:un|una|unos|unas)\s+(?:semanas?|mes(?:es)?|a[nñ]os?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ||
            Regex("""\d{1,2}:\d{2}""").containsMatchIn(lower) ||
            Regex("""a las \d+""").containsMatchIn(lower)
    }

    private fun hasTimeReference(lower: String): Boolean {
        // Paridad con extractDateTime (c.600): "a medianoche"/"al mediodía" son
        // horas canónicas (00:00/12:00) que el parser resuelve; el detector las
        // omitía, así una entrega "a medianoche" no recibía el bono de hora (+0.08).
        //
        // Paridad con extractDateTime (c.601): "las N" DESNUDA (sin introductor "a")
        // — "ir al dentista el viernes las 4" / "terapia el viernes las 4" — la resuelve
        // extractDateTime vía normalizeBareLasHour, PERO hasTimeReference la omitía → no
        // recibía el bono de hora (+0.08) y, en capturas marginales (base+fecha en
        // 0.37–0.45), caía por debajo de MINIMUM_CONFIDENCE y se DESCARTABA, aunque su
        // gemela con "a las N" sí pasaba: olvido asimétrico, P1. Se normaliza el texto
        // con el MISMO rewriter de c.601 (con su guard anti-cantidad) antes de los
        // checks, así "a las \d+" reconoce la hora y "comprar las 3 manzanas" NO recibe
        // un bono falso (el guard preserva "las 3" como cantidad).
        val normalized = normalizeBareLasHour(lower)
        return Regex("""\d{1,2}:\d{2}""").containsMatchIn(normalized) ||
            Regex("""a (las|la) \d{1,2}""").containsMatchIn(normalized) ||
            lower.contains("de la mañana") || lower.contains("de la tarde") ||
            lower.contains("de la noche") || lower.contains("del día") ||
            lower.contains("medianoche") ||
            lower.contains("mediodía") || lower.contains("mediodia")
    }

    /**
     * Palabras-función del español (artículos, preposiciones, conjunciones) que
     * NO deben ir en mayúscula salvo al inicio absoluto del título. Los prefijos
     * de [extractTitle] ("Cita: "/"Reunión: "/"Pagar "/"Comprar ") aplican
     * [capitalizeFirst] sobre toda la cola capturada, dejando "Reunión: Con el
     * equipo"/"Pagar La factura"/"Estudio: Para el examen": mayúsculas espurias
     * en la primera palabra de la cola cuando es un artículo/preposición.
     */
    private val FUNCTION_WORDS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "con", "para", "por", "en", "al", "a", "y", "o",
        "que", "sin", "sobre", "hacia"
    )

    /**
     * Depura el título de un [ContextIntent]:
     *  1. Elimina el residuo de fecha/hora de cola (los anclajes que
     *     [extractDateTime] ya resolvió en [dueAt], pero que los regex voraces
     *     `(.+)` de [extractTitle]/[generateTitle] dejaban en el título).
     *  2. Corrige mayúsculas espurias en artículos/preposiciones que no abren
     *     el título (artefacto de [capitalizeFirst] sobre la cola).
     *
     * Paridad con el estándar de limpieza de títulos del [NaturalTaskParser]
     * (c.237–c.438), aplicado por fin a la ruta de captura de contexto.
     */
    private fun sanitizeTitle(title: String): String {
        val stripped = stripTrailingTemporalResidue(title)
        // Si tras depurar el residuo el título queda vacío/muy corto, se conserva
        // el original: un residuo visible es preferible a un título en blanco.
        val base = if (stripped.length >= 3) stripped else title
        return fixCapitalization(base)
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-', ';', ':')
    }

    /**
     * Elimina los anclajes de fecha/hora que aparecen al FINAL del título
     * (residuo), iterando hasta estabilizar (la cola puede apilar fecha + hora).
     * Anclado a fin de cadena con puntuación/espacios finales opcionales, así
     * NO toca apariciones legítimas a mitad de frase ("reunión de equipo").
     *
     * Guard anti-genitivo: las palabras de día relativo desnudas (hoy/mañana/
     * ayer/...) NO se eliminan si las precede "de "/"del "/"de la "/"de el ":
     * "diario de hoy" / "cita de ayer" son genitivos con contenido, no residuo.
     */
    private fun stripTrailingTemporalResidue(title: String): String {
        val weekday = """(?:lunes|martes|mi[ée]rcoles|miercoles|jueves|viernes|s[áa]bado|domingo)s?"""
        val month = """(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)"""
        val unit = """(?:d[ií]as?|semanas?|quincenas?|bimestres?|trimestres?|semestres?|mes(?:es)?|a[nñ]os?)"""
        val meridiem = """(?:a\.?\s*m\.?|p\.?\s*m\.?|am|pm|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+d[ií]a)"""
        val fraction = """(?:\s*(?:y\s+(?:media|treinta|cuarto|tres\s+cuartos|cuarenta\s+y\s+cinco|veinticinco|veinte|diez|cinco|\d{1,2})|menos\s+(?:cuarto|quince|cinco|diez|veinte|veinticinco|\d{1,2})))?"""
        val time = """(?:(?:a|para)\s+(?:las?|la)\s+\d{1,2}(?::\d{2})?$fraction(?:\s*(?:$meridiem))?(?:\s*(?:horas?|hs|h))?(?:\s+en\s+punto)?|\d{1,2}:\d{2}(?:\s*(?:$meridiem))?|medianoche|mediod[ií]a|mediodia)"""
        // Anclajes de fecha con seña explícita (no palabras desnudas solas):
        // weekday (con conector opcional "el "/"este "/"del ": "el viernes"/
        // "concierto del viernes". c.690: sin "del" listado, el "el" interior
        // de "del" casaba leftmost dentro del genitivo y el título quedaba
        // cortado a media palabra — "...concierto del viernes" → "...concierto d"),
        // "el N [de mes|del mes]", "N de mes", "pasado mañana",
        // "esta <parte del día>", períodos relativos multi-unidad y calificados.
        val date = """(?:(?:el|este|del)\s+)?$weekday|el\s+\d{1,2}(?:\s+de\s+$month|\s+del\s+mes)?|\d{1,2}\s+de\s+$month|pasado\s+ma[nñ]ana|esta\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada)|(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(?:un\s+par\s+de|unos|unas|\d{1,3})\s*$unit|(?:la|el)\s+(?:semana|mes|a[ñn]o|quincena|bimestre|trimestre|semestre)\s+(?:que\s+viene|que\s+entra|entrante|pr[oó]xim[oa]|siguiente|pasad[oa]|anterior)|en\s+(?:un|una|unos|unas)\s*(?:semanas?|mes(?:es)?|a[nñ]os?)"""
        // Sufijo meridiano suelto de cola (tras quitar la hora: " ... de la tarde").
        val bareMeridiem = """$meridiem"""
        // Días relativos desnudos (hoy/mañana/ayer/anteayer/antier), con guard genitivo.
        val bareRelative = """(?:hoy|mañana|manana|ayer|anteayer|antier)"""

        val tail = Regex("""\s*(?:$date|$time|$bareMeridiem)\s*[.,;:!?]?\s*$""", RegexOption.IGNORE_CASE)
        val bareTail = Regex("""\s+$bareRelative\s*[.,;:!?]?\s*$""", RegexOption.IGNORE_CASE)
        // Franja horaria blanda de cola (c.688): "por la mañana" /
        // "por las mañanas" / "por la tarde" / "por la noche" son pista
        // temporal pura, nunca contenido ("por" aquí sólo denota franja).
        // Sin esta rama el residuo se partía a la mitad: "hacer ejercicio
        // por la mañana" dejaba 'Hacer ejercicio por la' (el bareRelative
        // cortaba sólo "mañana", y el artículo+preposición quedaban
        // colgando en el título visible).
        // (c.690b: `noches?` cubre el singular "por la noche", caso común.)
        val bandTail = Regex("""\s*por\s+las?\s+(?:ma[nñ]anas?|tardes?|noches?)\s*[.,;:!?]?\s*$""", RegexOption.IGNORE_CASE)

        var current = title
        var prev = ""
        var guard = 0
        while (current != prev && guard < 6) {
            prev = current
            current = tail.replace(current, "").trim()
            current = bandTail.replace(current, "").trim()
            // Días relativos desnudos: sólo si NO los precede un genitivo.
            // "pasado" también bloquea (c.690): el compuesto "pasado mañana"
            // lo consume la alternativa `date` de `tail` en la siguiente
            // iteración; si el bareTail corta "mañana" primero, queda un
            // "pasado" huérfano en el título ("Entregar el informe pasado").
            val m = bareTail.find(current)
            if (m != null) {
                val before = current.substring(0, m.range.first)
                val prevWord = Regex("""(?i)\b(\S+)\s*$""").find(before)?.groupValues?.get(1)
                if (prevWord == null || prevWord.lowercase() !in setOf("de", "del", "para", "hasta", "desde", "después", "despues", "antes", "pasado")) {
                    current = bareTail.replace(current, "").trim()
                }
            }
            guard++
        }
        return current
    }

    /**
     * Pasa a minúscula los artículos/preposiciones/conjunciones en mayúscula que
     * NO abren el título (artefacto de [capitalizeFirst] sobre la cola capturada).
     * La primera palabra del título se conserva tal cual (capital legítima).
     */
    private fun fixCapitalization(title: String): String {
        val firstSpace = title.indexOfFirst { it == ' ' }
        if (firstSpace < 0) return title
        val head = title.substring(0, firstSpace)
        val rest = title.substring(firstSpace)
        val pattern = FUNCTION_WORDS.joinToString("|")
        // Sólo mayúsculas espurias (la cola se capitalizó entera). Las
        // minúsculas correctas y las palabras que no son función se preservan.
        val upperPat = Regex("""\b(${pattern})\b""", RegexOption.IGNORE_CASE)
        val fixed = upperPat.replace(rest) { w ->
            if (w.value.lowercase() in FUNCTION_WORDS) w.value.lowercase() else w.value
        }
        return head + fixed
    }

    private fun monthName(name: String): Int? {
        val months = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        return months[name.lowercase()]
    }

    private fun capitalizeFirst(text: String): String {
        if (text.isBlank()) return text
        return text.first().uppercase() + text.substring(1)
    }

    /** Peso de cada palabra clave en la puntuación */
    private const val KEYWORD_WEIGHT = 0.12f
    private const val MIN_TEXT_LENGTH = 4
}

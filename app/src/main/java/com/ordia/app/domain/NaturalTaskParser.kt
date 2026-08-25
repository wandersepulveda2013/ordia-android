package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Resultado del analizador de lenguaje natural en español.
 *
 * El analizador es 100 % local y determinista (sin red ni IA remota).
 * Los campos nuevos usan valores por defecto para no romper llamadas existentes.
 */
data class ParsedTaskInput(
    val title: String,
    val dueAt: Long?,
    val priority: TaskPriority,
    /** Duración estimada en minutos, p. ej. "durante 45 minutos". */
    val durationMinutes: Int? = null,
    /** Recordatorio "N antes" de la fecha límite, p. ej. "recuérdame 2 horas antes". */
    val reminderOffsetMinutes: Int? = null,
    val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val recurrenceInterval: Int = 1,
    /** Días de repetición semanal (ISO 1..7, CSV), p. ej. "1,4" para lunes y jueves. */
    val recurrenceDays: String = "",
    /** Categoría inferida por contexto (trabajo, casa, compras, salud, personal). */
    val category: String = "",
    /** 1.0 si la captura es interpretable, 0.35 si es texto libre sin señales. */
    val confidence: Float = 1f
)

object NaturalTaskParser {
    /**
     * Números escritos en español admitidos como cantidad (1-99), fragmento de regex
     * reutilizado por todas las patrones que admiten dígitos o palabras: fechas
     * relativas, "hace N", recordatorios, duraciones y cadencias de recurrencia.
     *
     * Antes cada patrón repetía su propia lista de palabras, acotada a 1-30, así que
     * "cuarenta y cinco minutos" no casaba: `parseWrittenNumber` devolvía null y la
     * tarea quedaba SIN vencimiento (P1: tarea olvidada, invisible en What Now).
     * Ahora se centraliza en un único fragmento y se amplía a:
     * - 21-29 en una palabra (veintidós…veintinueve) y compuesta ("veinte y dos").
     * - 31-99 compuesta ("treinta y cinco", "cuarenta y cinco"…): forma estándar
     *   del español coloquial ("en cuarenta y cinco minutos").
     * - decenas sueltas (treinta…noventa).
     * El orden pone las formas compuestas (con "y") ANTES que las decenas sueltas
     * para que "cuarenta y cinco" case entero y no solo "cuarenta".
     */
    private val writtenNumberGroup: String = run {
        val units = "un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve"
        val tens = "treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa"
        "(?:$tens) y (?:$units)|veinte y (?:$units)|" +
            "diez|once|doce|trece|catorce|quince|diecis[eé]is|dieciseis|diecisiete|dieciocho|diecinueve|" +
            "veinte|veintiuno|veintid[oó]s|veintitr[eé]s|veinticuatro|veinticinco|veintis[eé]is|veintiseis|veintisiete|veintiocho|veintinueve|" +
            tens + "|" + units
    }

    /** Nombres de mes (completos + abreviaturas) como fragmento de regex, sincronizado
     *  con el mapa `months` (c.1208). Reutilizado por los límites mensuales para casar
     *  el mes explícito opcional ("fin de mes de octubre"). */
    private val monthNameGroup: String =
        "(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|set|sept|oct|nov|dic)"

    private val numericDatePattern = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val weekdayPattern = Regex("""(?i)\b(?:el\s+|del\s+|de\s+|este\s+)?(?:pr[oó]ximo\s+|pr[oó]xima\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+que\s+viene|\s+pr[oó]ximos?|\s+pr[oó]ximas?|\s+siguientes?|\s+posterior(?:es)?)?\b""")

    /**
     * Fracción de hora sin dígitos como fragmento de regex, compartida por todos los
     * patrones fraccionarios (duración, fecha relativa, fecha pasada, recordatorio):
     * "media hora" (30 min), "una media hora" (30, con artículo femenino) y
     * "(un) cuarto de hora" (15). El artículo es parte de la frase fraccionaria en
     * español, no contenido del título: "una media hora" es tan natural como "un cuarto
     * de hora". Antes solo se reconocía "media hora" (sin artículo), de modo que "una"
     * sobrevivía como residuo en el título y, en fecha relativa/pasada, la frase caía a
     * duración (c.385). Centralizar el grupo evita que un contexto admita "una" y otro
     * no (inconsistencia que generaba residuo o clasificación errónea). (c.385)
     */
    private val fractionalHourGroup: String =
        """(?:una\s+)?media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora"""
    // Reescritores de conectores direccionales-temporales (c.371/c.378). Se aplican a
    // `working` durante el parseo y TAMBIÉN al `original` crudo en el respaldo de título
    // vacío (línea `working.ifBlank { ... }`): cuando el usuario escribe SÓLO una frase
    // de agenda ("al viernes", "de aquí al 15") sin acción, `working` queda en blanco
    // (toda la fecha se limpió) y el respaldo resucitaba el `original` sin reescribir →
    // el conector "al"/"de aquí al" sobrevivía como título visible ("al viernes" en vez
    // de "el viernes"). Reutilizar los mismos regex garantiza coherencia sin duplicar.
    private val deAquiConnectorRewriter = Regex("""(?i)\bde\s+aqu[íi]\s+al\b|\bde\s+ac[aá]\s+al\b""")
    private val deAquiToRewriter = Regex("""(?i)\bde\s+aqu[íi]\s+a\b|\bde\s+ac[aá]\s+a\b""")
    private val alWeekdayRewriter = Regex("""(?i)\bal(\s+(?:pr[oó]xim[oa]\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))\b""")
    /**
     * Abreviaturas de días de la semana → nombre completo ("lun"→"lunes",
     * "mie"→"miércoles", "jue"→"jueves", "vie"→"viernes", "sab/sáb"→"sábado",
     * "dom"→"domingo"). Al capturar rutinas es cotidiano escribir los días
     * abreviados ("gimnasio lun mie vie", "clase lun-mie-vie", "fútbol sab y
     * dom"). Antes estas formas NO casaban con [dayListPattern]/[weekdayPattern]
     * (que sólo admiten el nombre completo) → la rutina quedaba como tarea ÚNICA
     * sin fecha ni recurrencia (P1: la rutina semanal se olvidaba, sin
     * recordatorio ni visibilidad en el planificador), pese a ser la misma
     * intención que "lunes miércoles viernes" (que sí funcionaba). Expandir las
     * abreviaturas a nombre completo al inicio del pipeline hace que todo el
     * razonamiento posterior (lista de días, rango Lun-Vie, "próximo lunes",
     * ordinales mensuales, etc.) las trate idéntico al nombre completo, sin
     * añadir lógica de recurrencia nueva ni falsos positivos más allá de los que
     * el nombre completo ya produce.
     *
     * "mar" (martes) se EXCLUYE deliberadamente: colisiona con la abreviatura de
     * mes "mar" (marzo) ya admitida en [months] ("pago el 5 de mar" = 5 de marzo).
     * Expandirla a martes rompería fechas como "5 de mar" y, a la inversa, dejarla
     * como mes haría fallar "clase mar jue". Por simetría con `months` (que ya
     * trata "mar" como marzo), se conserva esa convención; quien escriba martes
     * abreviado puede usar "martes" completo o "lun mar mie" no casa (queda como
     * antes). Es una concesión de seguridad razonable: el coste de un falso
     * positivo (fecha de marzo corrompida) supera al de no reconocer "mar"=martes.
     *
     * El punto opcional final ("lun.", "mie.") es la forma escrita habitual al
     * abreviar; se admite y se descarta. Límites de palabra (\b) evitan tocar
     * subcadenas dentro de otra palabra ("alunizar", "adomicilio").
     */
    private val weekdayAbbrevRewriter = Regex(
        """(?i)(?<![a-záéíóúüñ])(lun|mi[eé](?:rc?)?|vier|jue|vie|s[aá]b|dom)\.?(?![a-záéíóúüñ])"""
    )
    /** "este/el/próximo fin de semana" o "fin de semana" suelto → próximo sábado.
     *  Acepta también "finales de semana" (plural análogo a "finales de mes") y la
     *  variante regional latinoamericana "final de semana" (singular, común en
     *  Colombia/Venezuela/Centroamérica, equivalente exacto de "fin de semana"):
     *  señala un fin de semana concreto, no un hábito. Acepta el apócope coloquial
     *  "finde" ("este finde"/"el finde"/"próximo finde"/"finde" suelto) como fecha
     *  única, NO como recurrencia: el singular señala UN fin de semana concreto,
     *  mientras que el determinante plural/cada ("los findes"/"cada finde") es hábito
     *  y se resuelve aparte en parseRecurrence. Antes "este finde" caía por error a la
     *  recurrencia semanal (WEEKLY sábado+domingo para siempre) cuando el usuario pedía
     *  una sola fecha. OJO: "fines de semana" (f-i-n-e-s) y "los findes"/"cada finde"
     *  son recurrencia semanal y se resuelven aparte en parseRecurrence, no aquí.
     *  "final de semana" se añade aquí (no en parseRecurrence) porque es sinónimo de
     *  UN fin de semana concreto, igual que "fin de semana"; sin esto la tarea queda
     *  SIN fecha (P1: olvidada, invisible en What Now). */
    private val weekendPastModifier = """(?:pasad[oa]|anterior|últim[oa]|ultim[oa])"""
    // Nota: el \b va DESPUÉS de "semana"/"finde" (antes del modificador opcional final), no al
    // final del grupo opcional. Con \b al final, el retroceso ASCII (\b no considera 'ó' como
    // carácter de palabra) hace que "que pasó" NO se capture: el motor suelta el grupo opcional
    // para satisfacer \b tras "semana", dejando "que pasó" como residuo y fechando el PRÓXIMO
    // sábado (futuro) en lugar del pasado. Por eso el modificador final va sin \b posterior.
    private val weekendPattern = Regex(
        """(?i)\b(?<!cada\s)(?<!los\s)(?:a\s+)?(?:este\s+|el\s+|pr[oó]ximo\s+)?(?:(?:$weekendPastModifier)\s+)?(?:fin|final|finales)\s+de\s+semana\b(?:\s+(?:$weekendPastModifier|que\s+pas[oó]))?""" +
            """|\b(?:a\s+)?(?:este\s+|el\s+|pr[oó]ximo\s+)?(?:(?:$weekendPastModifier)\s+)?(?<!cada\s)(?<!los\s)finde\b(?:\s+(?:$weekendPastModifier|que\s+pas[oó]))?"""
    )
    /** `true` si el match de [weekendPattern] señala el fin de semana PASADO
     *  ("el fin de semana pasado"/"el pasado fin de semana"/"finde anterior"/
     *  "el fin de semana que pasó") en vez del próximo. Simétrico a previousWeekday:
     *  resuelve al sábado anterior para una tarea vencida honesta, no al próximo
     *  sábado (fecha futura errónea). Antes "el fin de semana pasado" caía a
     *  weekendPattern sin capturar "pasado", se fechaba en el PRÓXIMO sábado y
     *  "pasado" sobrevivía como residuo en el título (P1: fecha olvidada +
     *  título corrupto, a diferencia de "el sábado pasado" que sí iba al pasado). */
    private fun weekendMatchIsPast(m: MatchResult): Boolean {
        val t = m.value.lowercase()
        return !t.contains("próxim") && !t.contains("proxim") &&
            (t.contains("pasad") || t.contains("anterior") ||
                t.contains("últim") || t.contains("ultim") || t.contains("que pas"))
    }
    /**
     * "el jueves pasado" / "el último lunes" / "el martes anterior": última ocurrencia
     * PASADA de ese día de la semana. El usuario reconoce que la tarea está vencida
     * ("pagar la factura el viernes pasado"). Antes "el jueves pasado" se leía como
     * "jueves" (próximo) por weekdayPattern y "pasado" quedaba como residuo en el
     * título -> fecha futura errónea + título sucio. Se detecta y borra ANTES que
     * weekdayPattern para que no capture el día como próximo.
     */
    private val previousWeekdayPattern = Regex("""(?i)\b(?:el|del|de)\s+([a-záéíóúüñ]+)\s+(?:pasado|anterior|último|ultimo)\b""")
    // Orden inverso: "el último lunes"/"el pasado martes" (modificador antes del día).
    private val previousWeekdayReversedPattern = Regex("""(?i)\b(?:el|del|de)\s+(?:último|ultimo|pasado|anterior)\s+([a-záéíóúüñ]+)\b""")
    // "pasado el lunes" / "pasado el viernes" (modificador ANTES de "el <día>"):
    // forma coloquial FUTURA ("llevar el coche al taller pasado el lunes" = el próximo
    // lunes). A diferencia de "el lunes pasado" (pasado, [previousWeekdayPattern]) y de
    // "el pasado martes" (pasado, [previousWeekdayReversedPattern]), aquí "pasado" va
    // DELANTE del artículo + día. Antes ningún reescritor lo consumía: weekdayPattern
    // capturaba "el lunes" y fechaba el PRÓXIMO lunes (fecha correcta para este uso), PERO
    // "pasado" sobrevivía pegado al título ("Llevar el coche al taller pasado") — contenido
    // degradado P1 (captura sucia). Aquí se borra SÓLO "pasado" y se deja "el lunes" para
    // que weekdayPattern lo procese igual que antes (sin cambiar la fecha ya calculada).
    // Exige weekday real (vía [MatchResult.toDayOfWeek] en el llamador) para no tocar
    // contenido ("pasado el incidente", "pasado el informe") ni "pasado mañana" (la cadena
    // "pasado el" no aparece en "pasado mañana" — no choca) ni "pasado el mediodía"
    // (mediodía no es weekday).
    private val futureWeekdayPostArticlePattern = Regex("""(?i)(?<!\p{L})pasado\s+el\s+([a-záéíóúüñ]+)\b""")
    // Ordinal + weekday SUELTO sin calificador de mes ("el primer lunes", "el segundo martes",
    // "el tercer jueves", "el cuarto sábado"): no casa [lastWeekdayOfMonthPattern] (éste exige
    // "del mes"/"de cada mes"/"de <mes>") ni [previousWeekdayReversedPattern] (éste sólo admite
    // último/pasado/anterior). Se normaliza a "el <weekday>" para que [weekdayPattern] consuma
    // el weekday limpio y el ordinal no quede como residuo en el título. Grupo 1 = ordinal
    // (excluye último/ultimo, que sí son fecha pasada válida); grupo 2 = weekday. El lookahead
    // negativo descarta cualquier calificador de mes superviviente (ya borrados arriba, pero
    // protege contra reordenamientos futuros) y "de cada/todos los/mensual" (cadencia mensual).
    private val looseOrdinalWeekdayPattern = Regex(
        """(?i)\b(?:el\s+)?(primer|primero|segundo|tercer|tercero|cuarto|quinto)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?!\s+(?:del?\s+(?:mes|cada|este|esta|pr[oó]xim[oa]|enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|set|sept|oct|nov|dic)|todos\s+los|mensual))\b"""
    )
    // "el último viernes del mes" / "el primer lunes de agosto" / "el tercer viernes del mes que
    // viene" / "el último viernes del mes pasado": ocurrencia ORDINAL de ese weekday en un mes
    // (no la semana pasada, que es lo que resuelve previousWeekdayReversed al casar "el último
    // viernes"). Más específico: exige el calificador "del mes"/"del mes que viene/próximo/
    // entrante/pasado/anterior"/"de <mes>" tras el día. Se procesa ANTES que lastPeriodPattern
    // y previousWeekdayReversed para consumir la frase entera (así el calificador no queda como
    // residuo en el título, el día no se captura dos veces y "del mes pasado" NO lo roba
    // lastPeriodPattern como "el mes pasado" suelto → fecha now−30d ignorando ordinal+weekday).
    // Sin este patrón, "el último viernes del mes" caía en previousWeekdayReversed → viernes
    // ANTERIOR (fecha equivocada) + "del mes" como basura en el título. Ordinales:
    // último = última ocurrencia; primer/segundo/tercer/cuarto = N-ésima desde el inicio
    // (todo mes tiene ≥4 de cada weekday).
    private val lastWeekdayOfMonthPattern = Regex(
        // Grupo 1: ordinal (último=-1 | primer/segundo/tercer/cuarto = N-ésimo desde el inicio).
        // Grupo 2: día de la semana (alternancia explícita: "el último día/informe del mes" NO
        // casa → cae a endOfMonth, sin falso positivo ni guard takeIf). Grupo 3: mes NOMBRE
        // opcional (con abreviaturas del mapa `months`; admite forma pleonástica "del mes de
        // septiembre"). Grupo 4: año explícito opcional (2/4 cifras). La cola "del? <este/próximo>?
        // mes <que viene/que entra/próximo/entrante>?" es opcional → "del mes" (mes en curso),
        // "de este mes", "del mes que viene/próximo/entrante" (mes siguiente). La alternación
        // explícita de meses hace que "del mes a las 9" NO robe la "a" (sólo meses reales casan)
        // y deja la hora explícita intacta. Ordinales: último = última ocurrencia; N = N-ésima.
        //
        // Alternativa de CADENCIA (lookahead, c.256): la cola "del? <mes>" exige el puente "de".
        // La forma cotidiana SIN "de" —"el primer lunes cada mes", "el último viernes todos los
        // meses", "renta mensual el primer lunes"— NO casaba → el parser perdía el ordinal
        // (recurrenceDays='' → el motor anclaba al día del mes: 2ª cita en día 7 aunque cayera
        // miércoles, deriva silenciosa) Y dejaba "el primer" como residuo en el título. El
        // lookahead `(?=\s+(?:cada mes|...))` casa el ordinal-weekday SIN consumir el conector:
        // parseRecurrence (que corre DESPUÉS) sigue viendo "cada mes"/"todos los meses"/"mensual"
        // y emite MONTHLY, y aquí se captura el ordinal para anclar el motor. Simétrico del
        // puente "de" (que consume sólo "de" y deja "cada mes"). Sin conector de cadencia la
        // alternativa NO dispara → fecha suelta (no recurrente), sin regresión.
        """(?i)(?<!\p{L})(?:el\s+)?(antepenúltimo|antepenultimo|penúltimo|penultimo|último|ultimo|primer|primero|segundo|tercer|tercero|cuarto|quinto)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+del?\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+|pasad[oa]\s+|anterior\s+)?(?:mes(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante|pasad[oa]|anterior))?(?:\s+del?\s+)?)?((?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|set|sept|oct|nov|dic))?(?:\s+del?\s+(\d{2,4}))?|(?=\s+(?:cada\s+mes|todos\s+los\s+meses|mensual(?:mente)?|mensualidades?)\b))\b"""
    )

    /**
     * Forma de cadencia PRECEDENTE: "cada mes el primer lunes", "mensual el primer lunes",
     * "todos los meses el ultimo viernes". El patrón [lastWeekdayOfMonthPattern] detecta el
     * ordinal-weekday sólo cuando la cadencia va DESPUÉS ("el primer lunes de cada mes" /
     * "el primer lunes cada mes" vía su lookahead). Cuando la cadencia va ANTES, el lookahead
     * no dispara → el ordinal NO se captura: la recurrencia MONTHLY quedaba anclada al día del
     * mes (2ª cita derivaba a un weekday distinto) Y "el primer" se mantenía como residuo en
     * el título (P1: programación incorrecta + título corrupto). Este patrón captura el
     * ordinal+weekday para que el motor ancle cada ciclo al N-ésimo/último weekday del mes.
     *
     * No consume la cadencia: el rango a borrar es sólo "el primer lunes" (grupo 1), de modo
     * que parseRecurrence sigue viendo "cada mes"/"mensual" y emite MONTHLY. Sólo se usa si
     * [lastWeekdayOfMonthPattern] no casó (cadenas mutuamente excluyentes por posición).
     * Grupo 1 = "el primer lunes" (span a borrar); grupo 2 = ordinal; grupo 3 = weekday.
     *
     * c.273: la alternancia de cadencia se amplía a los adjetivos PLURIMENSUALES
     * (bimestral/trimestral/cuatrimestral/semestral) y al intervalo explícito "cada N meses".
     * Estas cadencias ya emitían MONTHLY+interval (c.258), PERO el ordinal precedente no se
     * capturaba → `recurrenceDays=''` (motor anclaba al día del mes → deriva) Y "el primer"
     * quedaba como residuo. Misma rendija que c.256/c.271, ahora cerrada para plazo largo.
     * El número del intervalo va en grupo NO capturador para no desplazar los grupos 2/3.
     *
     * c.276: se añade la forma hablada "todos los N meses" (determinante plural en vez de
     * "cada N meses"). Esta cadencia ahora emite MONTHLY+interval vía intervalPattern, PERO
     * el ordinal precedente seguía sin capturarse → deriva del weekday + residuo "el primer".
     * Cierra la misma rendija que c.273 para la forma plural, en paralelo a "todas las N
     * semanas" (c.276 en detectWeekInterval/intervalPattern).
     */
    private val precedingCadenceOrdinalPattern = Regex(
        """(?i)(?<!\p{L})(?:cada\s+mes|todos\s+los\s+meses|mensual(?:mente)?|bimestral(?:mente)?|trimestral(?:mente)?|cuatrimestral(?:mente)?|semestral(?:mente)?|cada\s+(?:\d{1,3}|$writtenNumberGroup)\s*meses?|todos\s+los\s+(?:\d{1,3}|$writtenNumberGroup)\s*meses?)\s+((?:el\s+)?(antepenúltimo|antepenultimo|penúltimo|penultimo|último|ultimo|primer|primero|segundo|tercer|tercero|cuarto|quinto)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))\b"""
    )

    /**
     * Fecha relativa VAGA de futuro cotidiano: "en/dentro de/de aquí a/de acá a un rato",
     * "en/dentro de un momento", "al rato", "pasado un rato", "enseguida"/"en seguida" (adverbio de inmediatez, sin "un rato"). Forma coloquial frecuente que
     * antes no casaba ningún patrón → dueAt=null y la tarea quedaba sin recordatorio (olvidada,
     * P1). Simétrica futura de "hace un rato" (pasado, −3 h). "un rato"/"un momento" son
     * intencionalmente imprecisos; se resuelve a +1 h (heurística honesta, no IA): agenda el
     * recordatorio para que la tarea no desaparezca. Se procesa ANTES que [relativePattern]
     * para robar la frase completa y dejar el título limpio.
     *
     * Admite los DIMINUTIVOS coloquiales "un ratito"/"un ratico"/"un momentito" (y el
     * suelto "al ratito"): formas extremadamente frecuentes en español latinoamericano
     * informal ("llamar en un ratito", "dentro de un momentito", "al ratito"). Antes
     * caían a dueAt=null (tarea olvidada, sin recordatorio). Misma heurística +1 h,
     * mismo comportamiento de limpieza del título. "ratito"/"momentito" se listan ANTES
     * que "rato"/"momento" para que la alternancia capture el diminutivo completo y no
     * deje el sufijo "-ito" como residuo en el título.
     *
     * Familia "poco"/"breve"/"nada" (c.610): "dentro de poco", "en breve", "dentro de
     * poco rato", "en poco rato", "de aquí/de acá a poco", "dentro de nada". Formas
     * cotidianísimas de "pronto" que antes NO casaban → dueAt=null + residuo en el título
     * ("reunión dentro de poco" → título "reunión dentro de poco", tarea olvidada). Exigen
     * el prefijo relativo futuro ("en"/"dentro de"/"de aquí a"/"de acá a") para no colisionar
     * con "hace poco" (pasado, que captura [agoPattern]) ni con contenido legítimo ("tengo
     * poco tiempo", "poco dinero"). "nada" se restringe a "dentro de nada" (no "en nada",
     * que rara vez significa "pronto" y sí "en absoluto"/contenido). Misma heurística +1 h
     * y cede ante hora explícita (c.397).
     */
    private val vagueRelativePattern = Regex(
        """(?i)\b(?:(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(?:un\s+ratito|un\s+ratico|un\s+momentito|un\s+rato|un\s+momento|poco\s+rato|poco|breve)|dentro\s+de\s+nada|al\s+ratito|al\s+rato|pasado\s+un\s+rato|en\s*seguida|enseguida)\b"""
    )
    /**
     * "Ahora" inmediato cotidiano: "ahora mismo", "ahorita", "ahora",
     * "lo antes posible", "cuanto antes", "a la brevedad",
     * "lo más pronto/temprano posible", "ya", "ya mismo". Estas frases significan
     * literalmente "ya" y son extremadamente frecuentes; antes no casaban ningún
     * patrón → dueAt=null → tarea SIN vencimiento, invisible en "What Now"/
     * planificador, sin recordatorio programable → olvidada (P1). Se resuelve a
     * `now` (no a +N min aproximado: el usuario pidió "ahora"/"ya", y la app debe
     * sacar la tarea a la superficie de inmediato). Heurística honesta, no IA. Se
     * procesa junto a [vagueRelativePattern] para consumir la frase completa y
     * dejar el título limpio. "ya mismo" va antes que "ya" en la alternancia para
     * que el match capture la frase entera. NO entra en [relativeIsDays]: "ya" es
     * sub-hora, no debe combinarse con una hora explícita (no se rueda a hoy+hora).
     * Guard narrativo c.1027: un «ya» suelto seguido de pretérito inequívoco
     * ([yaPreteriteNarrativeSuffix]) NO ancla: es relato de un hecho cumplido,
     * no una petición de inmediatez.
     */
    private val nowPattern = Regex(
        """(?i)\b(?:ahorita\s+mismo|ahorita|ahora\s+mismo|ahora|lo\s+m[aá]s\s+(?:pronto|temprano)\s+posible|lo\s+antes\s+posible|cuanto\s+antes|a\s+la\s+brevedad|ya\s+mismo|ya)\b"""
    )
    /**
     * "Más tarde"/"más rato"/"después" (con o sin tilde, suelto o "más tarde de N"):
     * adverbios cotidianísimos de "luego, no ahora pero hoy mismo". Antes NO casaban
     * ningún patrón → dueAt=null → tarea SIN vencimiento, invisible en "What Now"/
     * planificador, sin recordatorio → olvidada (P1). Se resuelve a +3 h: un
     * intervalo mayor que el de las vagas ("un rato"=+1 h) y que no es "ya" (ahora),
     * aproximando "más tarde" a "esta tarde/más tarde hoy". Heurística honesta, no IA.
     * Excluye "después del/de la N" (dependencia/evento) y "después de N minutos/horas":
     * esos los cubren patrones específicos; aquí solo interesa el adverbio suelto.
     * "luego" se trata como sinónimo de "después"/"más tarde" (uso cotidísimo; antes
     * no casaba → tarea sin vencimiento → olvidada), pero se excluye "luego de N" y
     * "luego del/de la N" (dependencia: "luego del almuerzo") que patrones específicos
     * u horas explícitas deben resolver. No se combina con hora explícita.
     */
    private val laterRelativePattern = Regex(
        """(?i)\b(?:(?:m[aá]s\s+(?:tarde|rato)|despu[eé]s|luego)(?!\s+(?:de\b|del\b|de\s+la\b)))\b"""
    )
    /**
     * Fecha relativa: "en N minutos/horas/días/semanas/meses/años" o "dentro de N ...".
     * Acepta dígitos o números escritos (una/un, dos, ..., veinte, treinta). "una"/"un" → 1.
     * Las semanas (×7 días), meses (×30 días) y años (×365 días) son formas muy
     * comunes ("en una semana", "en un mes", "en un año", "en 2 años") que antes quedaban
     * sin fecha → la tarea se olvidaba (sin recordatorio, invisible en planificador/What Now).
     * Admite también las formas coloquiales "de aquí a N ..." y "de acá a N ..."
     * (equivalentes a "en/dentro de N ..."), simétricas al prefijo estándar.
     * Admite el sufijo fraccionario "y media"/"y medio" (media unidad más): "en una
     * semana y media" = +7d + 3,5d, "en un mes y medio" = +45 d, "en un día y medio"
     * = +1,5 d. Acepta ambos géneros (la unidad es femenina o masculina según el
     * sustantivo, pero el usuario los mezcla); antes el sufijo NO casaba aquí y caía
     * como residuo en el título, con un vencimiento prematuro (media unidad antes de
     * lo pedido). Simétrico del "y media" sub-hora de [compoundFractionalRelativePattern].
     * Admite los cuantificadores vagos "unos"/"unas" (plural indeterminado pequeño,
     * sinónimo coloquial de "un par de"): "llamar en unos minutos", "reunión en unos
     * días", "en unas horas". Antes estas formas no casaban → `dueAt=null` y título
     * basura → tarea olvidada (P1, captura rápida fallida). Se resuelven a 2 (mismo
     * valor que "un par de"). El prefijo ("en/dentro de/de aquí a") + la palabra de
     * unidad protegen de falsos positivos: "comprar unos libros"/"tengo unos
     * pendientes" no casan (libros/pendientes no son unidades de tiempo).
     * Admite el prefijo idiomático "de hoy en" (coloquial muy común: "llamar de hoy
     * en dos semanas"): antes sólo casaba la subcadena "en dos semanas" y el "de
     * hoy" suelto activaba el keyword "hoy" o quedaba como residuo, y si la frase de
     * agenda era TODO el contenido, el fallback de título resucitaba el texto
     * completo ("de hoy en quince días" como título). La forma SIN unidad
     * ("de hoy en ocho" = +8d) la captura [deHoyEnIdiomPattern], procesada antes.
     */
    private val relativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a|de\s+hoy\s+en)\s+(un\s+par\s+de|unos|unas|\d{1,3}(?:[.,]\d+)?|$writtenNumberGroup)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)(?:\s+y\s+(media|medio))?\b"""
    )
    /**
     * Guard anti-secuestro (c.849) de [relativePattern]: con artículo indefinido
     * ("un/una/unos/unas"), "en una <unidad> <adjetivo>" es una frase nominal
     * descriptiva — "en una semana difícil" = "durante una semana difícil", no
     * un ancla temporal. Antes se robaba "en una semana" (+7d) y el adjetivo
     * quedaba como TÍTULO ("difícil"): tarea inventada, agendada y con el
     * contenido real destruido. El ancla con artículo exige fin de frase,
     * puntuación o conector/determinante tras la unidad; si sigue una palabra
     * de contenido (adjetivo), no se fecha y el contenido se conserva íntegro
     * (queda en la bandeja sin fecha: visible, no agendado en falso). Con
     * dígitos NO aplica: "en 3 días hábiles" sí es un plazo (cantidad contada,
     * no descripción). "un par de" tampoco: cuantifica, no describe.
     */
    private val relativeArticleQuantifiers = setOf("un", "una", "unos", "unas")
    private val relativeSafeFollowers = setOf(
        // Preposiciones y conectores que introducen más contenido de la tarea.
        "a", "al", "ante", "bajo", "con", "contra", "de", "del", "desde", "en",
        "entre", "hacia", "hasta", "para", "por", "según", "sin", "sobre", "tras",
        "y", "e", "o", "u", "que", "como", "más", "mas", "menos",
        // Determinantes: "pagar en una semana el alquiler" sí es plazo.
        "el", "la", "los", "las", "mi", "mis", "tu", "tus", "su", "sus",
        // Adverbios de aproximación al plazo: "en una semana máximo/exactamente".
        "aprox", "aproximadamente", "exactamente", "justo",
        "máximo", "maximo", "mínimo", "minimo"
    )

    private fun articleRelativeHijacksContent(match: MatchResult, text: String): Boolean {
        if (match.groupValues[1].lowercase() !in relativeArticleQuantifiers) return false
        val rest = text.substring(match.range.last + 1).trimStart()
        if (rest.isEmpty() || !rest.first().isLetter()) return false
        val nextWord = rest.takeWhile { it.isLetter() }.lowercase()
        return nextWord !in relativeSafeFollowers
    }

    /**
     * Idioma "de hoy en ocho/quince/N (días)" SIN unidad explícita: coloquialismo
     * cotidiano (España y LatAm) para "+N días". Antes NO casaba ningún patrón
     * relativo (no hay unidad), el keyword "hoy" agendaba la tarea PARA HOY y el
     * "en ocho" quedaba como residuo en el título ("llamar de hoy en ocho" →
     * título "llamar en ocho", vencimiento hoy): compromiso futuro agendado en el
     * presente → recordatorio disparado 8 días antes y tarea vencida mañana (P1:
     * fecha errónea). Se asume días (es lo único que el idioma admite sin unidad).
     * El lookahead negativo rechaza una unidad distinta explícita ("de hoy en 8
     * horas") para que la capture [relativePattern] con su prefijo "de hoy en" y la
     * unidad real; el "día(s)" opcional lo consume aquí la alternativa final.
     */
    private val deHoyEnIdiomPattern = Regex(
        """(?i)\bde\s+hoy\s+en\s+($writtenNumberGroup|\d{1,3})(?!\s+(?:minutos?|mins?|horas?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)\b)(?:\s+d[ií]as?)?\b"""
    )
    /**
     * Fecha relativa fraccionaria sin dígitos: "en media hora", "dentro de media hora",
     * "de aquí a un cuarto de hora". Simétrica de [relativePattern] para las fracciones
     * cotidianas "media hora" (30 min) y "(un) cuarto de hora" (15 min). Antes estas
     * formas NO casaban aquí (solo aceptan números enteros), caían a [fractionalDurationPattern]
     * → `dueAt=null`, `durationMinutes=30` y el prefijo "en"/"dentro de" quedaba como residuo
     * en el título ("llamar en media hora" → título "llamar en", sin fecha). El usuario
     * pedía un punto en el tiempo (+30 min) y obtenía una duración sin fecha → tarea SIN
     * vencimiento, invisible en "What Now"/planificador, recordatorio imposible de programar.
     * Con este patrón se resuelve como ahora + (30|15) min y se consume la frase completa
     * (prefijo incluido) para que el título quede limpio y no la robe [fractionalDurationPattern].
     * Exige el prefijo relativo para no colisionar con la duración real ("reunión media hora"
     * sin prefijo sigue siendo duración 30 min) ni con el recordatorio ("media hora antes"
     * lo captura [reminderPatterns]).
     */
    private val fractionalRelativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+($fractionalHourGroup)\b"""
    )
    /**
     * Fecha relativa fraccionaria + cuarto: "en media hora y cuarto" (45 min),
     * "dentro de un cuarto de hora y cuarto" (30). Antes [fractionalRelativePattern]
     * robaba solo "en media hora" (+30) y dejaba "y cuarto" como residuo en el título
     * ("cita en media hora y cuarto" → título "cita y cuarto", vencimiento 30 min en
     * vez de 45). Se procesa ANTES que [fractionalRelativePattern] para robar la frase
     * completa: base (30 si "media" | 15 si "cuarto") + 15. Simétrica del compuesto
     * [compoundFractionalRelativePattern] para fracciones sin número.
     */
    private val fractionalAndQuarterRelativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+($fractionalHourGroup)\s+y\s+cuarto\b"""
    )
    /**
     * Fecha relativa fraccionaria COMPUESTA: "en una hora y media" (90 min),
     * "en dos horas y media" (150), "en una hora y cuarto" (75), "en 3 horas y cuarto".
     * Admite también cuartos en plural como fracción: "en una hora y tres cuartos"
     * (60+45=105), "en dos horas y dos cuartos" (120+30=150). Antes el plural "tres
     * cuartos" no casaba (el grupo solo aceptaba "media|un cuarto|cuarto") y caía a
     * [relativePattern], que robaba solo "en una hora" (+60) dejando "y tres cuartos"
     * como residuo en el título ("cita en una hora y tres cuartos" → "cita y tres
     * cuartos", vencimiento 60 min en vez de 105). Se procesa ANTES que
     * [relativePattern] para robar la frase completa: amount×60 + (45 si "tres
     * cuartos" | 30 si "dos cuartos" o "media" | 15 si "cuarto"). Simétrica de
     * [fractionalRelativePattern].
     */
    private val compoundFractionalRelativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+($writtenNumberGroup|\d{1,3})\s*horas?\s+y\s+(tres\s+cuartos|dos\s+cuartos|media|un\s+cuarto|cuarto)\b"""
    )
    /**
     * Fecha relativa multi-cuarto: "en tres cuartos de hora" (45 min), "en dos cuartos"
     * (30). Cada "cuarto" = 15 min. Admite el sufijo "+ y cuarto" (un cuarto extra):
     * "en tres cuartos de hora y cuarto" = 3+1 = 4 cuartos = 60 min, "en dos cuartos
     * de hora y cuarto" = 45 min. Antes el sufijo no se consumía y quedaba como
     * residuo en el título ("llamar en tres cuartos de hora y cuarto" → "llamar y
     * cuarto") y el vencimiento era 45 min en vez de 60. Se procesa ANTES que
     * [fractionalDurationPattern] para robar la frase completa (prefijo incluido) y
     * dejar título limpio.
     */
    private val multiQuarterRelativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+($writtenNumberGroup|\d{1,3})\s+cuartos(?:\s+de\s+hora)?(?:\s+y\s+cuarto)?\b"""
    )
    /**
     * Fecha relativa PASADA: "hace N días/semanas/meses/años" o "hace una semana".
     * Simétrico de "en/dentro de N": el usuario reconoce que la tarea quedó vencida
     * ("pagué la factura hace 2 días", "envié el correo hace una semana"). Antes no se
     * parseaba -> dueAt=null, la tarea vencida no aparecía en What Now como atrasada ni
     * disparaba seguimiento. Se resuelve a hoy−N (honesto: vencida, visible). Acepta
     * los mismos números escritos que el patrón futuro (parseWrittenNumber). "hace un
     * rato"/"hace poco" → −3 h (heurística honesta de "acaba de pasar").
     *
     * Cuantificadores vagos "un par de"/"unos"/"unas" (sinónimo coloquial de 2, igual
     * que el lado futuro [vagueQuantitativeRelativePattern], c.242): "hace un par de
     * horas", "hace unos minutos", "hace unas semanas". Antes estas formas NO casaban:
     * "hace unos minutos" caía a dueAt=null → tarea vencida olvidada (P1 evitar
     * olvidos); y "hace un par de horas" robaba solo "hace un" (→ −3 h heurística "un
     * rato") dejando "par de horas" como residuo en el título (fecha errónea + título
     * sucio). Se listan ANTES de `un rato`/`$writtenNumberGroup` (longest-match) para
     * que la regex capture la frase completa y no deje "par de"/"nos"/"nas" sueltos.
     * [parseWrittenNumber] ya resuelve "un par de"/"unos"/"unas" → 2 (c.4638).
     */
    private val agoPattern = Regex(
        """(?i)\bhace\s+(un\s+par\s+de|unos|unas|\d{1,3}|un\s+rato|poco|$writtenNumberGroup)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|mes(?:es)?|a[nñ]os?)?\b"""
    )
    /**
     * Fecha relativa PASADA fraccionaria + cuarto: "hace media hora y cuarto" (−45 min),
     * "hace un cuarto de hora y cuarto" (−30). Simétrica PASADA de
     * [fractionalAndQuarterRelativePattern] ("en media hora y cuarto" → +45). Antes estas
     * formas caían a [fractionalDurationPattern], que robaba "media hora"/"cuarto de hora"
     * como DURACIÓN (30/15 min) y dejaba "hace ... y cuarto" como residuo corrupto en el
     * título, con `dueAt=null`: una tarea que el usuario registraba como "acaba de pasar"
     * ("llamé hace media hora y cuarto") quedaba SIN vencimiento, invisible en What Now y
     * sin recordatorio. Se procesa ANTES que [fractionalAgoPattern] y que [agoPattern]
     * para robar la frase completa y resolver now − (base + 15) min. base = 30 si "media",
     * 15 si "cuarto".
     */
    private val fractionalAndQuarterAgoPattern = Regex(
        """(?i)\bhace\s+($fractionalHourGroup)\s+y\s+cuarto\b"""
    )
    /**
     * Fecha relativa PASADA fraccionaria sin dígitos: "hace media hora" (−30 min),
     * "hace un cuarto de hora" (−15). Simétrica PASADA de [fractionalRelativePattern]
     * ("en media hora" → +30). Antes estas formas NO casaban [agoPattern] (solo admite
     * enteros/escritos, no "media"/"cuarto de hora") y caían a [fractionalDurationPattern],
     * que robaba "media hora"/"cuarto de hora" como DURACIÓN (30/15 min) con `dueAt=null`:
     * el usuario pedía un punto en el tiempo pasado ("llamé hace media hora") y obtenía una
     * duración sin fecha → tarea SIN vencimiento, título corrupto ("llamé hace", "hace
     * llamé") y la tarea vencida no aparecía en What Now como atrasada ni disparaba
     * seguimiento. Con este patrón se resuelve como now − (30|15) min y se consume la frase
     * completa (prefijo "hace" incluido) para que el título quede limpio. Se procesa ANTES
     * que [agoPattern] para que este no robe parcialmente "hace un" (→ "un"=1, unidad
     * vacía → −3 h) de "hace un cuarto de hora" y produzca una fecha errónea (−3 h en vez
     * de −15 min), y antes que [fractionalDurationPattern] para que no la robe como
     * duración. Exige el prefijo "hace" para no colisionar con la duración real ("reunión
     * media hora" sin "hace" sigue siendo duración 30 min) ni con el futuro ("en media
     * hora" lo captura [fractionalRelativePattern]) ni con el recordatorio ("media hora
     * antes" lo captura [reminderPatterns]).
     */
    private val fractionalAgoPattern = Regex(
        """(?i)\bhace\s+($fractionalHourGroup)\b"""
    )
    /**
     * Fecha relativa PASADA vaga con DIMINUTIVO coloquial: "hace un ratito",
     * "hace un ratico", "hace un momentito". Simétrica PASADA de los diminutivos
     * futuros de [vagueRelativePattern] ("en un ratito" → +1 h). Antes estas formas
     * NO casaban [agoPattern] (su alternativa "un rato" no casa "ratito"), por lo que
     * [agoPattern] robaba solo "hace un" (→ "un"=1, unidad vacía → −3 h) y dejaba
     * "ratito"/"ratico"/"momentito" como RESIDUO en el título ("hace un ratito llamé"
     * → título "ratito llamé"), con un vencimiento correcto en magnitud (−3 h, igual
     * que "hace un rato") pero el título corrupto. Con este patrón se consume la frase
     * completa (prefijo "hace" incluido) y se resuelve a now − 3 h (misma heurística
     * honesta que "hace un rato": el diminutivo no cambia el orden de magnitud, solo
     * matiza; así se mantiene consistente con el lado futuro donde "en un ratito"
     * también vale +1 h). Se procesa ANTES que [agoPattern] para que este no robe
     * parcialmente "hace un". "ratito"/"ratico"/"momentito" se listan explícitamente
     * para que la captura sea completa y no deje sufijo "-ito" suelto.
     */
    private val diminutiveAgoPattern = Regex(
        """(?i)\bhace\s+un\s+(?:ratito|ratico|momentito)\b"""
    )
    /**
     * Fecha relativa PASADA fraccionaria COMPUESTA: "hace una hora y media" (−90 min),
     * "hace dos horas y media" (−150), "hace una hora y cuarto" (−75), "hace 3 horas y
     * cuarto". Simétrica PASADA de [compoundFractionalRelativePattern] ("en una hora y
     * media" → +90). Admite también cuartos en plural: "hace una hora y tres cuartos"
     * (−105). Antes [agoPattern] robaba solo "hace una hora" (→ −60) y dejaba "y media"
     * como residuo en el título ("hace una hora y media llamé" → título "y media llamé"),
     * con la fracción (30 min) perdida y el vencimiento subestimado. Se procesa ANTES que
     * [agoPattern] para robar la frase completa: now − (amount×60 + (45 si "tres cuartos"
     * | 30 si "dos cuartos" o "media" | 15 si "cuarto")). La cantidad admite dígitos o
     * número escrito (vía [writtenNumberGroup], igual que la familia futura).
     */
    private val compoundFractionalAgoPattern = Regex(
        """(?i)\bhace\s+($writtenNumberGroup|\d{1,3})\s*horas?\s+y\s+(tres\s+cuartos|dos\s+cuartos|media|un\s+cuarto|cuarto)\b"""
    )
    /**
     * "la semana pasada" / "el mes pasado" / "el año pasado": período completo
     * anterior. El usuario registra una tarea vencida refiriéndose al período previo
     * ("revisé el informe la semana pasada"). Se resuelve a hoy−1 período (semana/mes/
     * año) y se borra del título. No debe confundirse con "el jueves pasado" (día de
     * semana): aquí la unidad es el período, no el día.
     *
     * "anterior" es sinónimo pleno de "pasado" para períodos (no para días de semana):
     * "la semana anterior", "el mes anterior", "el año anterior". Antes estas formas
     * caían a `dueAt=null` y dejaban "anterior" como residuo en el título (P1:
     * vencimiento olvidado, asimetría frente a "...pasado" que sí se fechaba). Como
     * este patrón se procesa ANTES que previousWeekdayPattern, "el mes anterior" se
     * captura aquí (período) en vez de caer a "el <mes> anterior" (mes no es día →
     * null). "anterior" es siempre pasado, sin ambigüedad futura como "próximo".
     *
     * "la quincena pasada/anterior" (c.512): simétrico a semana/mes/año. La quincena
     * (15 días) es un período cotidiano en español (cobros/nóminas quincenales) y
     * "la quincena pasada" / "de la quincena anterior" son formas habituales para
     * registrar una tarea vencida del período previo. Antes estas formas caían a
     * `quincenaPattern` (próximo hito FUTURO) y dejaban "pasada"/"anterior" como
     * residuo del título: fecha equivocada (futura en vez de pasada) + título sucio.
     * Al capturarlas aquí (procesado ANTES que nextPeriodPattern/quincenaPattern)
     * se resuelven a hoy−15d y se borran limpias, igual que "...semana/mes/año".
     */
    private val lastPeriodPattern = Regex(
        """(?i)\b(?:la\s+semana|el\s+mes|el\s+a[n\u00f1]o|la\s+quincena)\s+(?:pasad[oa]|anterior)\b|\bsemana\s+(?:pasada|anterior)\b|\bmes\s+(?:pasado|anterior)\b|\ba[n\u00f1]o\s+(?:pasado|anterior)\b|\bquincena\s+(?:pasada|anterior)\b"""
    )

    /**
     * c.985-(iii): palabra-límite + período relativo PASADO: "a finales del mes
     * pasado", "a mediados de la semana pasada", "a fin de año pasado". Antes
     * [lastPeriodPattern] consumía solo «el mes pasado» (hoy−1 período) y la
     * palabra-límite sobrevivía como residuo en el título ("revisar las facturas a
     * finales"), con una fecha que además ignoraba el límite pedido. Este patrón
     * captura la frase ÍNTEGRA (límite + genitivo + período + "pasado/anterior") y
     * se procesa ANTES que [lastPeriodPattern] para que no robe el período. Se
     * resuelve al límite del período ANTERIOR: fin → último día del mes / domingo
     * / 31-dic; mediados → 15 / miércoles / 30-jun (convención de
     * [yearBaseForBoundary]); principios → 1 / lunes / 1-ene. Hora 09:00, como el
     * resto de límites. Semana = lunes→domingo (doctrina c.778). El artículo "la"
     * es OBLIGATORIO para semana: "fin de semana pasado" (sin artículo) es
     * territorio de [weekendPattern] (sábado), no de esta clase.
     * "cierre"/"corte" se EXCLUYEN a propósito de la lista de límites: a diferencia
     * de su uso en [endOfMonthPattern] (futuro), ante un período pasado "Cierre del
     * mes anterior" es con mucha más frecuencia una tarea LLAMADA "Cierre" fechada
     * «del mes anterior» (test genitivoDelMesAnterior_noDejaResiduoDel) que una
     * fecha-límite. Robarla dejaría la tarea sin título.
     * Grupo 1 = palabra-límite, grupo 2 = período (con artículo).
     * c.908: «últimos?» ("revisar a últimos del mes pasado") = "finales" → límite
     * "end" del período anterior (clasificado en la resolución de bWord).
     */
    private val lastPeriodBoundaryPattern = Regex(
        """(?i)\b(?:a\s+|al\s+)?(finales?|fin(?:al|es)?|mediados?|mitad|principios?|comienzos?|inicios?|primeros?|[uú]ltimos?)\s+(?:de\s+|del\s+)(la\s+semana|(?:el\s+)?mes|(?:el\s+)?a[nñ]o)\s+(?:pasad[oa]|anterior)\b"""
    )

    /**
     * Período próximo ("la semana que viene", "el mes que viene", "el año que
     * viene", "próximo mes", "la próxima semana", "la semana entrante"):
     * +1 período (semana/mes/año). "trimestre que viene" / "próximo trimestre"
     * = +3 meses = +90 días (plazo largo cotidiano: impuestos trimestrales,
     * revisiones, informes). "quincena" (+15d), "bimestre" (+60d) y "semestre"
     * (+180d) son períodos cotidianos en español (pagos quincenales, reportes
     * bimestrales, cierres semestrales). Antes estas formas quedaban sin fecha y
     * con la frase «que viene» como residuo en el título → tarea olvidada (sin
     * recordatorio ni visibilidad). "próximos días" (con o sin "en los/el/las")
     * es la forma vaga de "dentro de poco": +3 días (heurística honesta, ni IA
     * ni azar). Antes quedaba sin fecha → la tarea se olvidaba.
     *
     * "entrante" es el sinónimo caribeño de "que viene"/"próximo" (la app usa
     * America/Santo_Domingo como zona canónica): "la semana entrante", "el mes
     * entrante", "el año entrante". Sin esta rama, esas formas caían a
     * `dueAt=null` + residuo "entrante" en el título → vencimiento olvidado
     * (invisible en What Now/planificador, sin recordatorio). Se reusa la misma
     * resolución +1 período que "que viene".
     *
     * "que entra" es la variante regional mexicana/centroamericana de "que viene":
     * "el mes que entra", "la semana que entra", "el año que entra". Sinónimo
     * exacto de "que viene"/"entrante" (mismo sentido: el período que está por
     * comenzar). Antes estas formas caían a `dueAt=null` + residuo "que entra"
     * en el título (P1: vencimiento olvidado, tarea sin recordatorio ni
     * visibilidad). Se reusa la misma resolución +1 período.
     *
     * PLURAL de período próximo: "próximas semanas", "próximos meses", "próximos
     * años", "próximos trimestres", "las semanas que vienen". El plural es la
     * forma vaga de futuro más cotidiana ("el proyecto estará listo en las
     * próximas semanas", "entrega en los próximos meses"). Antes el singular
     * ("próxima semana") se resolvía pero el plural no coincidía → `dueAt=null` +
     * frase íntegra como residuo en el título → vencimiento olvidado (P1, misma
     * brecha de simetría que "próximos días"). El sustantivo y el adjetivo aceptan
     * plural (`semanas`, `próximos`, `que vienen`) y el artículo admite `los/las`.
     * La resolución reusa el `when` de subcadenas (los plurales contienen el
     * singular: "semanas"→"semana"→+7d), así que no cambia la heurística. El
     * prefijo "en los/el/las/la" es opcional (como en "próximos días") para que no
     * quede "en" como residuo en el título.
     */
    private val nextPeriodPattern = Regex(
        """(?i)(?<!\p{L})(?:en\s+(?:los|el|las|la)?\s+)?(?:a\s+)?(?:el|la|los|las)?\s*(?:semanas?|mes(?:es)?|a[nñ]os?|trimestres?|bimestres?|semestres?|quincenas?)\s+(?:que\s+viene[n]?|que\s+entra[n]?|pr[oó]ximos?|pr[oó]ximas?|entrante[s]?|siguientes?)\b|(?<!\p{L})(?:en\s+(?:los|el|las|la)?\s+)?(?:a\s+)?(?:el|la|los|las)?\s*(?:pr[oó]ximos?|pr[oó]ximas?|otr[oa]s?)\s+(?:semanas?|mes(?:es)?|a[nñ]os?|trimestres?|bimestres?|semestres?|quincenas?)(?!\s+(?:pasad[oa]s?|anteriore?s?))\b|(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b"""
    )
    /**
     * "el 15 del mes que viene" / "el 15 del próximo mes" / "el 15 del mes próximo" /
     * "alquiler del 15 del mes que viene" / "pago del 20 del mes que viene":
     * día N del mes SIGUIENTE. Es un compromiso mensual anclado a un día concreto
     * (vencimiento, cobro, cita). Antes, nextPeriodPattern capturaba "mes que viene"
     * y descartaba el día explícito (→ +30d desde hoy, fecha errónea) y dejaba "el 15
     * del" como residuo en el título. Se procesa ANTES que nextPeriodPattern para
     * consumir la frase completa (día + cualificador) y evitar ambos fallos. Se
     * resuelve como día (epoch medianoche) para combinarse con hora explícita
     * ("el 15 del mes que viene a las 10"). El día imposible (p. ej. 31 de feb)
     * se ajusta al último día válido del mes objetivo.
     * c.479: el introductor del día también admite el artículo genitivo "del"
     * ("alquiler del 15", "pago del 20", "cita del 15") — forma habitual de
     * vencimientos/cobros en español. Sin esto, el día no casaba y nextPeriodPattern
     * robaba "del mes que viene" como +30d genérico (fecha errónea) dejando el día
     * como residuo corrupto en el título (p. ej. "alquiler del 15 del"). El
     * cualificador "mes que viene/próximo/entrante" sigue siendo obligatorio, así
     * que no se inventan fechas para un "del 15" aislado sin mes relativo.
     */
    private val nextMonthDayPattern = Regex(
        """(?i)\b(?:el\s+(?:(?:d[ií]a\s+)?|(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+)?|del\s+(?:(?:d[ií]a\s+)?|(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+)?|d[ií]a\s+)(\d{1,2})\s+(?:del?\s+)?(?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b"""
    )
    /**
     * Orden inverso del anterior: "el mes que viene el 5" / "el mes que viene el
     * día 5" / "el próximo mes el 10" / "el mes próximo el 20" / "el mes entrante
     * el 15". Misma semántica (día N del mes siguiente) pero con el período ANTES
     * del día — forma tan cotidiana como la directa. Sin este patrón,
     * nextPeriodPattern robaba "el mes que viene" como +30d genérico (fecha
     * errónea: p. ej. 12/09 en vez del 05/09) y el día quedaba como residuo en el
     * título o se sombreaba. Se procesa ANTES que nextPeriodPattern para consumir
     * la frase completa.
     */
    private val nextMonthDayReversePattern = Regex(
        """(?i)\b(?:el\s+)?(?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\s+el\s+(?:(?:d[ií]a\s+)?|(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+)?(\d{1,2})\b"""
    )
    /**
     * "el próximo 15" / "próximo 15" / "el próximo día 15": día N del mes SIGUIENTE
     * (próximo = mes que viene). Vencimientos/cobros anclados a un día concreto pero
     * sin "del mes" ("pago el próximo 15", "entrega el próximo 20"). Antes caía a
     * dueAt=null: dayOfMonthPattern exige "el <dígito>" y la palabra "próximo" rompía
     * el match, y nextPeriodPattern requiere un período (semana/mes/año), no un día
     * numérico → vencimiento olvidado (sin recordatorio, invisible en What Now/planificador).
     * "próximo N" es inequívocamente temporal (no sustantivo de contenido), así que se
     * resuelve como día N del mes siguiente y se consume ANTES que nextPeriodPattern
     * (que robaría "próximo" sin día) y que dayOfMonthPattern. El día imposible
     * (p. ej. 31 de feb) se ajusta al último día válido del mes objetivo (igual que
     * nextMonthDayPattern). Se resuelve como día (epoch medianoche) para combinarse
     * con hora explícita ("el próximo 15 a las 10").
     */
    private val nextMonthDayShortPattern = Regex(
        """(?i)\b(?:el\s+)?(?:d[ií]a\s+)?pr[oó]xim[oa]\s+(?:d[ií]a\s+)?(\d{1,2})\b"""
    )
    /**
     * Orden inverso del anterior: "el 15 próximo" / "el 15 proximo" (calificador
     * "próximo" DESPUÉS del día). Misma semántica (día N del mes siguiente). Antes
     * dayOfMonthPattern capturaba "el 15" como de ESTE mes (fecha equivocada: P1) y
     * "próximo" sobrevivía como residuo en el título (contenido degradado). Se procesa
     * DESPUÉS de nextMonthDayShort (forma directa primero) para no doble-procesar y
     * ANTES que dayOfMonthPattern (que exige "el <dígito>" sin intercalar).
     */
    private val nextMonthDayShortReversePattern = Regex(
        """(?i)\bel\s+(\d{1,2})\s+pr[oó]ximo\b"""
    )
    /**
     * "la semana que viene el lunes" / "la próxima semana el viernes" / "la
     * semana entrante el sábado": día de la semana objetivo de la SEMANA PRÓXIMA
     * (no +7d genérico desde hoy, que es lo que daba nextPeriodPattern). Sin este
     * patrón, nextPeriodPattern robaba "la semana que viene" como +7d e ignoraba
     * el día explícito → "la semana que viene el viernes" dicho un miércoles daba
     * el próximo miércoles (mañana+7) en vez del viernes de la semana que viene
     * (cita/reunión en día equivocado). Se procesa ANTES que nextPeriodPattern
     * para consumir la frase completa (período + día) y evitar que ésta la robe.
     */
    private val nextWeekWeekdayReversePattern = Regex(
        """(?i)\b(?:la\s+)?(?:semana\s+(?:que\s+viene|que\s+entra|pr[oó]xima|entrante)|pr[oó]xima\s+semana)\s+el\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""
    )
    /**
     * Orden inverso del anterior: "el lunes de la semana que viene" / "el viernes
     * de la próxima semana" / "el lunes de la semana entrante". Misma semántica
     * (día objetivo de la semana próxima) pero con el día ANTES del período —
     * forma tan cotidiana como la directa. Sin este patrón, weekdayPattern
     * capturaba "el lunes" como fecha suelta (nextWeekdayOrSame) y
     * nextPeriodPattern robaba "la semana que viene" como +7d; al combinarse, el
     * +7d ganaba → día equivocado. Se procesa ANTES que nextPeriodPattern y
     * weekdayPattern para consumir la frase completa.
     */
    private val nextWeekWeekdayForwardPattern = Regex(
        """(?i)\bel\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+de\s+(?:la\s+)?(?:semana\s+(?:que\s+viene|que\s+entra|pr[oó]xima|entrante)|pr[oó]xima\s+semana)\b"""
    )
    /**
     * "esta semana el viernes" / "esta misma semana el martes": día de la semana
     * explícito anclado a la SEMANA ACTUAL (ISO lunes→domingo). Sin este patrón,
     * thisWeekPattern robaba "esta semana" como plazo blando (domingo 09:00) y ese
     * ancla ganaba la cascada effectiveRelativeDueAt sobre el weekday explícito →
     * "dentista el viernes de esta semana" (dicho un viernes) caía en el DOMINGO
     * (fecha errónea silenciosa, P1; medido en probe c.852). El weekday explícito
     * es más específico que el plazo blando: gobierna la fecha. Se procesa ANTES
     * que thisWeekPattern para consumir la frase completa (período + día).
     */
    private val thisWeekWeekdayReversePattern = Regex(
        """(?i)\besta\s+(?:misma\s+)?semana\s+el\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""
    )
    /**
     * Orden inverso del anterior: "el viernes de esta semana" / "el martes de esta
     * misma semana" / "el viernes esta semana" (sin "de", forma coloquial). Misma
     * semántica (día objetivo de la semana actual). Si el día ya pasó esta semana,
     * queda como tarea vencida honesta (misma doctrina que "el lunes pasado"): no
     * se rueda a la semana siguiente porque el calificador ancla ESTA semana. El
     * lookahead rechaza "de esta semana que viene" (forma confusa que
     * thisWeekPattern ya ancla al domingo de la próxima, c.488).
     */
    private val thisWeekWeekdayForwardPattern = Regex(
        """(?i)\bel\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+(?:de\s+)?esta\s+(?:misma\s+)?semana\b(?!\s+que\s+viene)"""
    )
    /**
     * "fin de mes" / "a finales de mes" / "fin del mes" / "cierre de mes" / "cierre del mes"
     * / "final de mes" / "al final del mes" → último día del mes actual (o del siguiente
     * si hoy ya es el último día). "mediados de mes" / "a mediados de mes" → día 15 del
     * mes actual (o del siguiente si hoy ≥ 15).
     * "principios de mes" / "a principios de mes" → día 1 del mes siguiente (si hoy ≥ 1,
     * es decir, siempre: el día 1 de hoy ya pasó salvo que sea hoy mismo, en cuyo caso
     * se mantiene hoy). Vencimientos mensuales cotidianos (alquiler, tarjeta, servicios,
     * facturas, renovaciones): antes quedaban sin fecha → vencimiento olvidado (sin
     * recordatorio ni visibilidad). Se detecta y borra ANTES del período próximo para
     * que "fin de mes" (que contiene la subcadena "mes") no deje residuo ni active por
     * error "mes que viene".
     *
     * Calificador de MES QUE VIENE: "fin del mes que viene" / "fin del próximo mes" /
     * "fin del mes próximo" (y análogos para mediados/principios) anclan al mes
     * SIGUIENTE, no al actual. Antes el patrón terminaba en "mes" e ignoraba el
     * modificador → un vencimiento explícitamente fijado para fin del mes próximo caía
     * un mes antes (P1: fecha de vencimiento errónea, pago/renta olvidados o
     * adelantados). El modificador se consume en el match (limpieza de título) y se
     * detecta en la resolución para desplazar un mes.
     *
     * Mes EXPLÍCITO opcional: "fin de mes de octubre" / "mediados de mes de septiembre"
     * / "principios de mes de marzo" nombran el mes de vencimiento. Antes el patrón
     * sólo casaba "<límite> de mes" y dejaba "de <mes>" como residuo en el título
     * Y peor: el mes nombrado se IGNORABA y la fecha caía al límite del mes EN CURSO
     * (p. ej. "renta finales de mes de octubre" → 31 de agosto en vez de 31 de
     * octubre, P1: vencimiento un mes antes, pago mal fechado). El grupo 1 captura el
     * nombre del mes y el grupo 2 el año opcional; si el mes es válido la resolución
     * usa `parseMonthBoundaryName` (mismo criterio que "finales de octubre" sin "de
     * mes"), si no, mantiene la lógica original de mes en curso/que viene.
     */
    // El conector entre el límite y "mes" admite ahora "cada"/"todos los" intercalados:
    // "el último día de cada mes", "mediados de todos los meses". Antes el conector exigía
    // "de/del" + "mes" contiguos, así "de cada mes" (con "cada" en medio) NO casaba → el
    // límite mensual se perdía, "cada mes" caía a fixedPatterns (MONTHLY día-de-hoy) y
    // "el último día de" sobrevivía como residuo del título (P1: renta/vencimiento mal
    // fechado al día de captura y título corrupto). El grupo es NO capturante para no
    // desplazar los índices de mes (g1)/año (g2) que usa `boundaryDueAt`. La promoción a
    // recurrencia la detecta `cadaBoundaryRecurrence` vía `cadaInBoundaryMatch` (c.311).
    // "todos los" exige "meses" (plural); "cada" va con "mes" (singular, forma canónica).
    // c.908: «últimos?» (sin "día") es la forma dialectal de fin de mes ("a últimos del
    // mes", "a últimos del mes que viene"); misma resolución que "fin de mes".
    private val endOfMonthPattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?(?:fin(?:al|ales|es)?|cierre|corte|[uú]ltim[oa]\s+d[ií]a|[uú]ltimos?)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+mism[oa])?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    // c.575: "el último día hábil/laborable/laboral del mes" — vencimientos que no
    // pueden caer en fin de semana (renta, nómina, pago de servicios cuando el banco
    // no opera sábado/domingo). El adjetivo "hábil"/"laborable"/"laboral" rompe la
    // secuencia `día de/del mes` de [endOfMonthPattern], así que sin este patrón la
    // frase NO se reconocía: dueAt=null, título corrupto y conf=0.35 → el pago no se
    // recordaba (olvido de vencimiento recurrente, P1). Misma cola de mes que
    // [endOfMonthPattern] (grupos 1=nombre de mes, 2=año) para reutilizar
    // `boundaryDueAt`; el retroceso a viernes lo resuelve el motor (sentinel EOM-BD).
    private val lastBusinessDayOfMonthPattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?[uú]ltim[oa]\s+d[ií]a\s+(?:h[áa]bil|laborable|laboral)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+mism[oa])?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    private val midOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+mism[oa])?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    private val startOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|primeros?|inicios?)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+mism[oa])?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    // c.471: "el último día" SIN "de/del mes" bajo cadencia mensual explícita
    // (alquiler/nómina "pago mensual el último día"). El patrón anterior exige
    // "de mes", así que esta forma cotidiana no se reconocía: el límite no se borraba
    // (quedaba en el título), dueAt caía al día de captura y la recurrencia MONTHLY
    // venía sin anclaje. El lookahead negativo evita casar "el último día del mes"
    // (ya cubierto por endOfMonthPattern) o "...del <sustantivo>" ("el último día
    // del congreso"); la guard de cadencia (evaluada en uso) impide anclar "el último
    // día" cuando NO hay "mensual"/"cada mes"/"todos los meses" (p. ej. "reunión el
    // último día del congreso" no es un límite mensual).
    private val endOfMonthNoMesPattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?el\s+[uú]ltim[oa]\s+d[ií]a\b(?!\s+(?:de|del)\s+(?:mes|meses))(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    /**
     * Prefijo "cada" inmediatamente antes de un límite mensual ("cada fin de mes",
     * "cada mediados de mes", "cada principios de mes"): convierte el vencimiento
     * ÚNICO en RECURRENCIA mensual anclada (c.257). Se ancla al final del texto
     * anterior al match del límite (`$`); `\bcada` evita casar dentro de palabras
     * ("sacada", "bocadillo"). El recorte de "cada " se suma al borrado del límite.
     */
    private val cadaBoundaryPrefixPattern = Regex("""(?i)\bcada\s+$""")
    /**
     * "mediados de septiembre" / "a finales de octubre" / "principios de enero":
     * calificador de límite mensual aplicado a un mes NOMBRE (no al "mes" en curso).
     * Vencimientos/plazos dichos sin día exacto ("pago a mediados de septiembre",
     * "renta a finales de octubre", "entregar a principios de enero"): antes caían a
     * dueAt=null y la frase quedaba como título → vencimiento olvidado (sin
     * recordatorio, invisible en What Now/planificador). Resuelve principios→día 1,
     * mediados→día 15, finales/fin/cierre→último día del mes nombrado. El mes se
     * valida contra el mapa `months` (acepta nombre completo y abreviatura, c.135);
     * el año es implícito con roll al siguiente si la fecha ya pasó (como
     * parseMonthNameDate). Se consume ANTES que monthNamePattern para no dejar
     * residuo ni doble-match.
     * c.908: «últimos?» ("pagar la renta a últimos de agosto") = "finales" → último
     * día del mes nombrado; la rama `else` de [parseMonthBoundaryName] ya lo resuelve.
     */
    private val monthBoundaryNamePattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?(mediados?|mitad|principios?|comienzos?|primeros?|inicios?|final(?:es)?|fin|cierre|corte|[uú]ltimos?)\s+(?:de\s+|del\s+)([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
    /**
     * "en <mes>" / "en <mes> de [<año>]": el nombre de mes SUELTO tras la preposición
     * "en", sin día explícito ("apuntarme al gimnasio en septiembre", "viaje en
     * diciembre", "entrega en agosto de 2027"): antes caía a dueAt=null y la frase
     * entera quedaba como título → compromiso del mes olvidado, sin recordatorio ni
     * visibilidad (P1), pese a ser la misma intención que "a inicios de <mes>" (que
     * sí ancla vía [monthBoundaryNamePattern]). Se ancla al día 1 del mes nombrado
     * (mismo criterio y roll anual que "a inicios de <mes>" si el día 1 ya pasó).
     * El lookbehind (?<!\p{L}) impide casar "en" dentro de palabras ("orden en",
     * "tren en..."), y exigir nombre completo/abreviatura en [monthNameGroup] con
     * `\b` final evita tallos ("en marcha"/"en mercado" no casan: "mar" queda sin
     * límite de palabra). Va DESPUÉS de [monthBoundaryNamePattern] (el calificador
     * explícito "a inicios/mediados/finales de..." gana) y ANTES de
     * [monthNamePattern] para no dejar residuo ni doble-match ("el 15 de septiembre"
     * lleva "de" antes del mes, no "en").
     */
    private val bareMonthPattern = Regex("""(?i)(?<!\p{L})en\s+($monthNameGroup)(?:\s+del?\s+(\d{2,4}))?\b""")
    /**
     * "para <mes>" / "para <mes> de [<año>]": plazo de fin de mes idiomático
     * ("entregar informe para septiembre", "liquidación para febrero de 2028",
     * "renovar afiliación para marzo"). En español "para <mes>" sin día denota un
     * deadline que se cumple "para finales de <mes>": por eso se ancla al ÚLTIMO
     * día del mes nombrado, reutilizando el mismo resolver de límite que
     * [monthBoundaryNamePattern] con calificador "finales". El conector durativo
     * "hasta <mes>" se declina intencionadamente (forma de rango, no plazo) —
     * decisión evidenciada en tools/probe/ParaHastaMesProbe.kt (c.676). Va DESPUÉS
     * de [monthBoundaryNamePattern] (calificador explícito gana) y de
     * [bareMonthPattern] ("en <mes>" gana: la captura "entregar para septiembre"
     * no se confunde con "en septiembre"), y ANTES de [monthNamePattern].
     */
    private val paraMonthPattern = Regex("""(?i)(?<!\p{L})para\s+($monthNameGroup)(?:\s+del?\s+(\d{2,4}))?\b""")
    /**
     * "fin de año" / "a fin de año" / "finales de año" / "fin del año" / "cierre de año"
     * → 31 de diciembre del año actual (o del siguiente si hoy ya es 31/12).
     * "principios de año" / "a principios de año" → 1 de enero del año siguiente.
     * "mediados de año" / "a mediados de año" → 30 de junio (mitad del año).
     * Vencimientos y plazos anuales cotidianos (cierre fiscal, renovaciones,
     * seguros, propósitos de año nuevo): antes caían a dueAt=null (la frase entera
     * se quedaba en el título) porque ningún patrón las reconocía → vencimiento
     * olvidado (P1). Se detectan y borran ANTES del período próximo para que la
     * subcadena "año" no active "año que viene" como +365d genérico (que adelantaría
     * "fin de año" a un año desde hoy en vez de al 31/12 real). El calificador de
     * año que viene/entrante desplaza al año siguiente.
     * c.908: «últimos de/del año» ("cierre fiscal a últimos de año") = fin de año;
     * [yearBaseForBoundary] lo clasifica como "end" (31/12 del año en curso).
     */
    private val endOfYearPattern = Regex("""(?i)(?<!\p{L})(?:a\s+)?(?:fin(?:ales|es)?|cierre|corte|[uú]ltimos?)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?a[nñ]o(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
    private val midOfYearPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?a[nñ]o(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
    private val startOfYearPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|primeros?|inicios?)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?a[nñ]o(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
    /**
     * "este mes" / "este año": plazo blando cotidiano ("renovar licencia este mes",
     * "cerrar ejercicio este año") = "antes de que acabe el mes/año en curso". Antes
     * caían a dueAt=null (tarea olvidada) y la frase entera quedaba como residuo en el
     * título. Plazo blando simétrico a "esta semana" (fin de semana): "este mes" ancla
     * al último día del mes en curso; "este año" al 31/12 del año en curso. Sin roll:
     * es explícitamente el periodo ACTUAL (si hoy es el último día, vence hoy).
     *
     * Los lookbehinds evitan robar "este mes/año" cuando forma parte de una frase ya
     * resuelta por otro patrón: "el 15 de este mes" (dayOfMonthPattern), "fin de este
     * mes"/"finales de este mes" (endOfMonthPattern), "fin de este año" (endOfYearPattern)
     * y el posesivo "renta de este mes/año" (no es plazo). Sin estas guardas, el token
     * suelto se tragaría la subcadena, dejaría residuo ("el 15 de") y/o cambiaría la
     * fecha (p. ej. 15→31). Se detecta y borra ANTES del período próximo para que la
     * subcadena "mes"/"año" no active "mes/año que viene".
     */
    private val thisMonthPattern = Regex("""(?i)(?<!\d\s)(?<!de\s)(?<!del\s)este\s+(?:mismo\s+)?mes(?:\s+mismo)?\b""")
    private val thisYearPattern = Regex("""(?i)(?<!\d\s)(?<!de\s)(?<!del\s)este\s+(?:mismo\s+)?a[nñ]o(?:\s+mismo)?\b""")
    /**
     * "la quincena" / "de la quincena" / "primera quincena" / "segunda quincena":
     * hito financiero mensual (cobro, nómina, pago). La "primera quincena" es el día
     * 15; la "segunda quincena" el fin de mes. Sin cualificar, resuelve al próximo
     * hito (día 15 o fin de mes). NO casa "próxima quincena"/"quincena que viene":
     * esas las resuelve nextPeriodPattern (+15d) y se procesan antes, dejándolas
     * fuera de `working` antes de que este patrón corra. El código descarta además
     * los matches que formen parte de recurrencias ("cada quincena",
     * "quincenalmente", "todas las quincenas") para no robar la palabra a
     * parseRecurrence (que las mapea a WEEKLY x2); sin esa guarda la recurrencia
     * caería a NONE (regresión del otro run).
     *
     * El prefijo "fin de "/"a fin de " (con "la" opcional) se consume también:
     * "fin de quincena"/"a fin de la quincena" son sinónimos cotidianos del hito de
     * cierre de quincena (igual que "fin de mes" = cierre de mes). Antes el patrón
     * solo tragaba "de quincena" y dejaba "a fin"/"fin" como residuo de título
     * ("cobrar a fin de quincena" → título "cobrar a fin"). La fecha resolvía bien
     * (próximo hito, igual que "la quincena" sin cualificar); el cambio solo limpia
     * el título sin alterar la fecha.
     */
    private val quincenaPattern = Regex("""(?i)\b(?:(?:a\s+)?fin\s+de\s+(?:la\s+)?|de\s+la\s+|de\s+|la\s+)?(primera|1ra|1\.?a|segunda|2da|2\.?a)?\s*quincena\b""")
    private val quincenaRecurrencePattern = Regex("""(?i)\b(?:cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""")
    /**
     * "esta semana" / "esta semana que viene": plazo blando de "antes de que acabe la
     * semana" (plazos cotidianos de unos días, sin un día concreto). Antes quedaba sin
     * fecha (la tarea se olvidaba) o, con hora explícita, se fechaba en HOY por error
     * ("esta semana a las 18" → hoy 18:00 en vez de "fin de esta semana a las 18").
     *
     * Resuelve al próximo domingo (fin de la semana ISO, lunes→domingo) a las 9:00; si
     * hoy ya es domingo, se mantiene hoy. Se detecta y borra ANTES del período próximo
     * para que "semana" no active "semana que viene" y para limpiar "esta semana que
     * viene" (frase confusa que el usuario usa como sinónimo de "esta semana").
     *
     * c.488: "fin de la semana" / "finales de la semana" / "al final de la semana"
     * (con o sin "que viene"). Antes solo "fin/fines de la semana" casaba (sin "que
     * viene"); las variantes "finales de la semana" y "al final de la semana" caían a
     * `dueAt=null` + frase íntegra como residuo en el título → vencimiento olvidado
     * (P1). Y "fin de la semana que viene" casaba como "fin de la semana" (esta
     * semana) y el modificador "que viene" se perdía silenciosamente → fecha
     * errónea (domingo de esta semana en vez del domingo de la semana próxima). La
     * regex ahora admite `fin|fines|finales|final` (singular/plural y apócope) y el
     * prefijo `a ` / `al ` (coloquial), más el modificador opcional "que viene" (que
     * suma +7d, simétrico a "esta semana que viene"). "a fin de la semana" y
     * "a finales de la semana" son sinónimos cotidianos del cierre de la semana.
     * weekendPattern (que exige `de semana` sin "la") no casa estas formas con "la",
     * así que no hay colisión; este patrón se procesa antes que nextPeriodPattern
     * para que "la semana que viene" no se robe el +7d genérico dejando "a finales
     * de" como residuo.
     */
    // c.982: la rama «fin/finales de la …» admite el intensificador intercalado
    // tras «la» («finales de la misma semana») — antes caía a dueAt=null con la
    // frase íntegra de residuo (sonda c.982). La post-puesta ya existía.
    private val thisWeekPattern = Regex("""(?i)\b(?:esta\s+(?:misma\s+)?semana(?:\s+misma)?(?:\s+que\s+viene)?|(?:a\s+|al\s+)?(?:fin|fines|final|finales)\s+de\s+(?:la\s+(?:(?:pr[oó]xim[oa]|misma)\s+)?semana(?:\s+misma)?|esta\s+(?:misma\s+)?semana(?:\s+misma)?)(?:\s+que\s+viene)?)\b""")
    /**
     * "principios de semana" / "a principios de semana": plazo blando de "a inicios de
     * la semana" (el lunes). Frases cotidianas ("lo termino a principios de semana") que
     * antes caían a dueAt=null (tarea olvidada) o, con hora explícita, a HOY por error.
     *
     * Resuelve al lunes más cercano en HOY o futuro (ISO, semana lunes→domingo): si hoy
     * es lunes, hoy; si es martes-domingo, el lunes de la semana siguiente. Como plazo
     * blando nunca se fecha en pasado. Se detecta y borra ANTES del período próximo para
     * que "semana" no active "semana que viene".
     *
     * c.489: admite el modificador opcional "que viene" (simétrico a thisWeekPattern).
     * "principios de la semana que viene" → lunes de la SEMANA PRÓXIMA; sin él,
     * al lunes más cercano en hoy/futuro de esta semana.
     */
    // c.982: intensificador «misma» en ambas posiciones (doctrina c.646: neutro, no
    // altera el rango): intercalada tras «la» (alternativa «misma» en el grupo de
    // determinante — el conector «de la » ya se consumió) y post-puesta
    // («semana misma», simétrica a thisWeekPattern). Sin ellas la fecha base se
    // resolvía pero «misma» quedaba de residuo y «que viene» fuera del match
    // (fecha de ESTA semana: doble daño silencioso, medido en sonda c.982).
    private val startOfWeekPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|inicios?)\s+(?:de\s+la\s+|de\s+|del\s+)(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+|misma\s+)?semana(?:\s+misma)?(?:\s+que\s+viene)?\b""")
    /**
     * "mediados de semana" / "a mediados de semana" → miércoles más cercano en HOY o
     * futuro. Análogo a "principios de semana" (lunes) y "mediados de mes" (día 15).
     * Se detecta y borra ANTES del período próximo para que "semana" no active
     * "semana que viene".
     *
     * c.489: admite el modificador opcional "que viene" (simétrico a thisWeekPattern).
     * "mediados de la semana que viene" → miércoles de la SEMANA PRÓXIMA; sin él,
     * al miércoles más cercano en hoy/futuro de esta semana.
     */
    // c.982: mismo tratamiento del intensificador «misma» que startOfWeekPattern
    // (intercalada tras «la» + post-puesta), simétrico a thisWeekPattern.
    private val midOfWeekPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+la\s+|de\s+|del\s+)(?:este\s+(?:mismo\s+)?|esta\s+(?:misma\s+)?|pr[oó]xim[oa]\s+|misma\s+)?semana(?:\s+misma)?(?:\s+que\s+viene)?\b""")
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(?:d[ií]a\s+)?(\d{1,2}|$writtenNumberGroup|primero)\s+de\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
    // Variante de monthNamePattern para la limpieza del título: añade un prefijo
    // opcional no capturador `(?:\bdel?\s+)?` para consumir la preposición genitiva
    // "del"/"de" que introduce la fecha ("concierto del 12 de octubre"). Comparte los
    // mismos grupos de captura (día/mes/año) que monthNamePattern, así la validación
    // de mes del paso de limpieza decide el borrado igual que antes. NO se usa para la
    // RESOLUCIÓN de fecha (monthNamePattern): allí el prefijo sería irrelevante y podría
    // desplazar el inicio del match sin beneficio.
    // El lookbehind `(?<!\bantes\s)` impide consumir el "del" del conector de plazo
    // "antes del <fecha>": ese "del" lo necesita `beforeDeadlineDayPattern` (caso
    // "antes del 30") y el paso `.replace(... antes del? ...)` (caso "antes del 5 de
    // agosto"). Sin el lookbehind, mi strip se llevaría el "del" y dejaría un "antes"
    // huérfano como residuo en el título.
    private val monthNameStripPattern = Regex("""(?i)(?<!\bantes\s)(?:\bdel?\s+)?\b(?:el\s+)?(?:d[ií]a\s+)?(\d{1,2}|$writtenNumberGroup|primero)\s+de\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
    // Día del mes suelto con artículo: "reunión el 15", "cita el 20 a las 18",
    // "entregar el 5 del mes". Antes "el 15" no casa con numericDatePattern (que exige
    // DD/MM con mes) y quedaba como residuo en el título; la hora suelta ("a las 10") se
    // aplicaba a HOY → la cita se programaba hoy en vez del día 15 (P1: día erróneo,
    // reunión perdida). El lookahead negativo evita colisionar con "el 15 de marzo" (lo
    // resuelve monthNameDate) y "el 15 de cada mes" (recurrencia mensual): no se admite
    // "de <palabra>" tras el número salvo la fórmula "del mes"/"de este mes".
    // Admite "el 15", "el día 15" y la forma coloquial sin artículo "día 15"
    // ("pagar día 15", "reunión día 3"). Antes "día N" sin "el" caía a sin fecha y,
    // si la frase traía hora, ésta se aplicaba a HOY → fecha silenciosamente errónea
    // (P1: integridad de datos). El lookahead negativo impide colisionar con
    // "el 15 de marzo" (lo resuelve monthNameDate), "el 15 de cada mes" (recurrencia
    // mensual) y referencias no temporales como "día 15 del libro": no se admite
    // "de/del <palabra>" tras el número salvo las fórmulas de mes en curso, que el
    // grupo opcional consume antes del lookahead. Ese grupo cubre TODOS los sinónimos
    // de "mes en curso" ("del mes", "de este mes", "de este mismo mes", "del presente
    // mes", "del mes actual"): sin esto "el 31 del mes actual" no casaba y, peor,
    // monthlyDayPattern (que se ejecuta ANTES en parseRecurrence) robaba "31 del mes"
    // como recurrencia falsa dejando "actual" como residuo (P1: compromiso único del
    // mes en curso perdido + título sucio). Véase el lookahead negativo allá que rechaza
    // esos mismos calificadores para que no caigan a recurrencia.
    //
    // Los dos lookbehinds negativos de longitud FIJA (?<!\bentre\s) y (?<!\by\s) impiden
    // agendar un "el N" que sea extremo de un rango numérico SIN mes válido ("feria entre
    // el 3 de unidades y el 5", "comprar 3 cajas entre el 5 y el 10"). Esos rangos son
    // ambiguos (no se sabe el mes) y los patrones de rango los dejan intactos a propósito
    // para no inventar fechas; sin esta guarda el extremo "el N" caía aquí como día suelto
    // y se programaba una fecha espuria (p. ej. 5 de septiembre) con el título roto
    // ("...entre ... y"). "entre el N" (inicio de rango, nunca fecha válida por sí sola)
    // y "y el N" (cierre de rango) quedan excluidos. No afecta a "X y el 5 de diciembre"
    // (lo resuelve monthNamePattern, que exige "de <mes>", y por tanto no pasa por aquí)
    // ni a "reunión el 5" (sin conector de rango previo). Longitud fija obligatoria: un
    // lookbehind variable con "\s+" rompe el anclado de \b y permitía casar "el N" dentro
    // de "del N"/"antes del N" (regresión en congreso del 20 al 25 / antes del 30).
    private val dayOfMonthPattern = Regex("""(?i)(?<!\bentre\s)(?<!\by\s)\b(?:el\s+(?:d[ií]a\s+)?|d[ií]a\s+)(\d{1,2})(?![/-])(?:\s+del?\s+(?:mes\s+actual|presente\s+mes|este\s+(?:mismo\s+)?mes|mes))?\b(?!\s*del?\s+[a-záéíóúüñ])""")
    // "antes del 30"/"antes de 15": plazo (deadline) expresado como día del mes suelto,
    // SIN nombre de mes. Antes el día suelto "30" no casaba dayOfMonthPattern (éste exige
    // "el"/"día") ni monthNamePattern (éste exige "de <mes>"), así que el conector "antes
    // del" se borraba (ver limpieza) pero el "30" sobrevivía como residuo del título Y la
    // fecha se perdía → dueAt=null → vencimiento olvidado (P1: la tarea nace sin fecha,
    // recordatorio jamás dispara, invisible en What Now/planificador). El lookahead negativo
    // (?!\s*del?\s+[a-záéíóúüñ]) impide capturar "antes del 30 de agosto" (lo resuelve
    // monthNameDate) y "antes del 15 del mes" (lo resuelve dayOfMonthPattern); sólo se
    // queda con el día suelto, que es justo el gap. "antes del 30" → plazo el día 30
    // (canónica 09:00, igual que "el 15" suelto).
    private val beforeDeadlineDayPattern = Regex("""(?i)\bantes\s+del?\s+(\d{1,2})\b(?!\s*del?\s+[a-záéíóúüñ])""")
    // "pago del 15" / "cita del 20": día del mes suelto introducido por el artículo contracto
    // "del" (= de + el), forma cotidiana de vencimiento. Antes NO se reconocía: dayOfMonthPattern
    // exige "el"/"día" y su lookbehind evita casar dentro de "del N", así que "pago del 15"
    // caía a dueAt=null y "del 15" sobrevivía como residuo del título → vencimiento olvidado
    // (P1: la tarea nace sin fecha, recordatorio jamás dispara, invisible en What Now/plan).
    // Se ancla igual que "el 15" (nextMonthlyDate: día N de este mes, o del siguiente si ya pasó).
    // Guardas para no sombrear otros patrones:
    //  - (?<!antes\s): "antes del 30" lo resuelve beforeDeadlineDayPattern (va antes en `when`).
    //  - (?!\s+(?:al|hasta)): no capturar el extremo inicial de un rango "del 20 al 25"
    //    (sin mes válido → sigue sin inventar fecha, test rangoSinMesNoInventaFecha).
    //  - lookahead final (?!\s*del?\s+[a-záéíóúüñ]): "del 15 de septiembre"/"del 15 de cada mes"
    //    los resuelven monthNamePattern/parseRecurrence; no se ancla el día suelto.
    private val delDayOfMonthPattern = Regex("""(?i)(?<!\bantes\s)\bdel\s+(\d{1,2})(?![/-])(?!\s+(?:al|hasta))\b(?!\s*del?\s+[a-záéíóúüñ])""")
    // Lookahead (?![/-]) tras el dígito: rechaza "el 25/12" para que NO se ancle al
    // día-suelto del mes (25 de agosto) y caiga a numericDatePattern (25/12 → diciembre).
    // Sin esto, dayOfMonthPattern ("el 25") casaba ANTES que numericDatePattern → la
    // fecha numérica completa se perdía y el vencimiento caía en el mes equivocado.

    /**
     * "el lunes 24" / "el martes 25" / "pago el viernes 28": día de la semana SEGUIDO de
     * un número de día del mes suelto, SIN nombre de mes. Forma cotidiana que confirma el
     * weekday y precisa el día ("la reunión es el lunes 24"). Antes weekdayPattern capturaba
     * "el lunes" y el "24" sobrevivía como residuo del título ("reunión 24") — contenido
     * capturado degradado (P1 título limpio). Además, cuando el próximo lunes NO caía en 24
     * (p. ej. "el lunes 25" dicho cuando el próximo lunes es el 24), la fecha se anclaba al
     * weekday ignorando el número explícito → cita en día erróneo (P1 datos/fechas correctas).
     * El número explícito es más específico que el weekday suelto (igual que "el lunes 24 de
     * septiembre" ancla al 24/9 sin importar en qué weekday caiga). Así que aquí se resuelve
     * al día N del mes (vía [nextMonthlyDate]) y se consume "el lunes 24" entero del título.
     *
     * Guards anti-falso-positivo:
     * - Lookahead `(?!\s*(?:[/\-]|\bdel?\b))`: NO casa si tras el número sigue un separador
     *   de fecha (`/`/`-` → numericDate/rango) o la preposición `de`/`del` (→ mes nombrado
     *   "de septiembre", recurrencia "de cada mes", mes relativo "del mes que viene"). Esos
     *   los resuelven patrones más específicos ANTES en el `when`; aquí sólo se ancla la forma
     *   SIN mes. Así "el lunes 24 de septiembre" cae a monthNameDate (fecha+mes correcto) y
     *   "el lunes 24 del mes que viene" cae a nextMonthDayPattern, sin doble resolución.
     * - Exige el número INMEDIATAMENTE tras el weekday: "reunión 24" (número tras un
     *   sustantivo, sin weekday) no se toca — contenido legítimo ("reunión 24" de un comité).
     */
    private val weekdayDayPattern = Regex("""(?i)\b(?:el\s+|del\s+|de\s+|este\s+)?(?:pr[oó]ximo\s+|pr[oó]xima\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+(\d{1,2})(?!\s*(?:[/\-]|\bdel?\b))\b""")

    /**
     * Sufijos ordinales numéricos del español ("1ro"/"1ero", "2do", "3er"/"3ero", "4to", "5to",
     * "7mo", "8vo", "9no", "10mo" y los símbolos "1º"/"2ª") escritos pegados al dígito y
     * seguidos del conector de fecha " de ". Son marcadores de fecha cotidianísimos en LATAM ("pago
     * el 1ro de septiembre", "entrega el 2do de cada mes", "vence el 1º de este mes") pero
     * el sufijo rompía los patrones de fecha (\d{1,2} exige dígito seguido de espacio, así
     * que "1ro de" dejaba "ro" como residuo y la fecha se perdía o el título quedaba
     * mutilado: "pago º de septiembre"). Se normaliza a su dígito base SOLO cuando va
     * seguido de " de " (contexto de fecha inequívoco): así "el 1ro de septiembre" → "el 1
     * de septiembre" reutiliza TODO el flujo existente, mientras que "ver el 3er capítulo"
     * o "comprar 2do piso" (ordinales de contenido sin " de ") NO se tocan y no generan
     * falsas fechas. "primero" escrito no se normaliza: "primer capítulo"/"segunda opción"
     * son contenido, no fecha.
     */
    private val ordinalSuffixPattern = Regex("""(?i)\b(\d{1,2})(?:ero|ro|do|er|to|mo|vo|no|º|ª)(\s+del?\s+)""")

    /**
     * Ordinales numéricos ANTES de un día de la semana ("el 3er viernes del mes",
     * "el 1er lunes de cada mes", "el 2do martes del mes"): forma cotidiana de
     * referirse a la N-ésima ocurrencia mensual de un weekday. Antes los patrones
     * ordinales-mensuales ([lastWeekdayOfMonthPattern]/[precedingCadenceOrdinalPattern])
     * sólo reconocían la forma ESCRITA ("tercer/primer/segundo/cuarto"), así que la
     * numérica perdía el ordinal: la recurrencia mensual NO se anclaba (MONTHLY al
     * día del mes → deriva silenciosa del weekday, o rec=NONE) y el título quedaba
     * corrupto con el residuo "el 3er del mes". Se normaliza a su palabra canónica
     * SÓLO cuando va seguida de un día de la semana (contexto inequívoco de
     * ordinal-weekday), reutilizando TODO el flujo mensual existente. Así "ver el
     * 3er capítulo" o "comprar 2do piso" (contenido) no se tocan. Se limita a 1-5:
     * el motor ya mapea ord=5 con salto de meses sin 5ª ocurrencia (c.575), así
     * que "el 5to viernes del mes" ancla igual que la forma escrita "quinto";
     * fuera de ese rango (≥6) se deja intacto (invalid mes-ordinal → contenido).
     */
    private val ordinalBeforeWeekdayPattern = Regex(
        """(?i)\b(\d{1,2})(?:ero|ro|er|do|to|ra|da|ta)(\s+(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b)"""
    )

    private fun normalizeOrdinalBeforeWeekday(input: String): String =
        ordinalBeforeWeekdayPattern.replace(input) { m ->
            val n = m.groupValues[1].toIntOrNull()
            val word = when (n) {
                1 -> "primer"
                2 -> "segundo"
                3 -> "tercer"
                4 -> "cuarto"
                5 -> "quinto"
                else -> null
            }
            if (word != null) "$word${m.groupValues[2]}" else m.value
        }

    // Genitivo temporal que introduce una frase temporal que resuelve fecha
    // ("la semana pasada", "el mes que viene", "finales de la semana",
    // "principios de la semana", "fin de semana", "fin de mes", "fin de año",
    // "mediados de mes de octubre"): "balance de la semana pasada",
    // "informe del mes pasado", "resumen de finales de la semana",
    // "foto de fin de semana", "cierre de fin de mes", "plan del fin de año",
    // "balance de cada fin de mes". El conector "de"/"del" es siempre el modificador de
    // posesión temporal del contenido, no contenido en sí (la frase temporal que
    // le sigue resuelve una fecha, así que es inequívocamente temporal).
    // Extiende el rango del match hacia atrás para consumir ese conector junto con la
    // frase, evitando el residuo "de"/"del" en el título. Paridad con
    // monthNameStripPattern (c.448) y el genitivo de día relativo (l.4579).
    // No consume "de" cuando la frase NO casa (no resuelve fecha): "menú de la
    // semana" (sin "pasada/que viene") no activa lastPeriod/nextPeriodPattern, y
    // "de la semana santa" no activa thisWeek/startOfWeek/midOfWeek/weekend, y
    // "cierre del mes" (sin "fin/mediados/principios") no activa endOfMonthPattern
    // → este método no se invoca → el conector "de" permanece legítimamente como contenido.
    private fun strippedPeriodRange(working: String, range: IntRange): IntRange {
        var start = range.first
        while (start - 1 >= 0 && working[start - 1].isWhitespace()) start--
        // "del" (de+el) o "de"; se toleran mayúsculas iniciales (inicio de frase).
        val pre = working.substring(0, start)
        val genitive = Regex("""(?i)\b(?:del|de)\s*$""").find(pre)
        return if (genitive != null) IntRange(genitive.range.first, range.last) else range
    }

    // c.495 — paralelo a [strippedPeriodRange] (que consume el genitivo "de/del"
    // ante una FRASE DE FECHA), pero para la "a" distributiva coloquial ante una
    // FRASE DE CADENCIA ("a cada día", "a cada semana", "a cada lunes", "a cada
    // mañana"). En español hablado "a <recurrencia>" equivale a "cada/los
    // <recurrencia>" ("meditar a cada día" = "meditar cada día", "reunión a cada
    // lunes" = "reunión cada lunes"). Los patrones de cadencia casan la frase SIN
    // la "a" inicial (cada día/semana/mes/año/lunes/mañana…), así que ésta quedaba
    // como residuo literal en el título ("Meditar a"). Se extiende el rango del
    // match hacia atrás para consumir esa "a" junto con la frase, evitando el
    // residuo. Igual que con el genitivo: sólo consume "a" cuando la frase SÍ es
    // cadencia (ya casada por parseRecurrence); nunca se invoca sobre contenido.
    // "a diario"/"a fines de semana" ya traen su "a" DENTRO del match, así que aquí
    // no se encuentra otra "a" detrás (sólo espacio/título): no se duplica.
    private fun strippedRecurrenceRange(working: String, range: IntRange): IntRange {
        var start = range.first
        while (start - 1 >= 0 && working[start - 1].isWhitespace()) start--
        val pre = working.substring(0, start)
        // (?<!\S) exige que la "a" sea palabra suelta (precedida de espacio o inicio):
        // NO basta con \b porque Java trata las vocales acentuadas (í, á…) como
        // NO-palabra, así "Auditoría" dejaba su "a" final consumida falsamente.
        val distributive = Regex("""(?i)(?<!\S)a\s*$""").find(pre)
        return if (distributive != null) IntRange(distributive.range.first, range.last) else range
    }

    // c.511 — paralelo a [strippedPeriodRange]/[strippedRecurrenceRange], pero para
    // el calificador de LÍMITE ("a finales/principios/mediados [de la/esta/del]?")
    // que precede a una QUINCENA. Los patrones de quincena (quincenaPattern,
    // nextPeriodPattern con "quincena", quincenaPattern sobre "quincena pasada")
    // resuelven bien la fecha (hito del 15 / fin de mes / +15d), pero casan solo la
    // palabra "quincena" (o "próxima quincena") sin tragarse el calificador de
    // límite coloquial que la precede. Así "cobrar a finales de esta quincena" →
    // fecha correcta pero título "cobrar a finales de esta" (residuo). Igual que en
    // semana/mes/año (c.506, endOfMonth…), el español usa los mismos calificadores
    // de límite sobre la quincena (cobros/nóminas quincenales). Se extiende el
    // rango del match hacia atrás para consumir ese calificador junto con la frase,
    // evitando el residuo. Sólo se invoca cuando la quincena SÍ casó (ya resuelve
    // fecha): nunca consume "finales/mediados/principios" de contenido legítimo. La
    // "a" distributiva inicial ("a finales…") se incluye opcionalmente (?<!\S evita
    // comer la "a" final de palabras como "Auditoría").
    private fun strippedQuincenaLimitRange(working: String, range: IntRange): IntRange {
        var start = range.first
        while (start - 1 >= 0 && working[start - 1].isWhitespace()) start--
        val pre = working.substring(0, start)
        val limitQualifier = Regex(
            """(?i)(?<!\S)(?:a\s+)?(?:finales|principios|comienzos|inicios|mediados|mediado|mitad)(?:\s+de(?:\s+(?:la|las|esta|este|del|de))?)?\s*$"""
        ).find(pre)
        // Si hay calificador de límite, se consume entero (incluye "de la/esta/del").
        // Si no, se delega a [strippedPeriodRange] para consumir el genitivo simple
        // "de/del" (caso "pago de la quincena", sin calificador de límite).
        return if (limitQualifier != null) IntRange(limitQualifier.range.first, range.last)
        else strippedPeriodRange(working, range)
    }

    /**
     * "<día> <mes>" SIN conector "de" ("Reunión 22 ago", "Entregar 1 oct",
     * "Renovar suscripción 1 sept", "Cita 20 agosto"): la forma abreviada y
     * cotidiana de capturar una fecha sin teclear "de". Antes NINGÚN patrón la
     * reconocía (monthNamePattern exige " de ", dayOfMonthPattern exige artículo
     * "el" y devuelve el mes en curso, numericDatePattern exige "/"): la cita
     * caía a `dueAt=null` (olvidada — sin recordatorio, invisible en What
     * Now/planificador) o, si traía hora, ésta se aplicaba a HOY → reunión
     * agendada el día equivocado (P1: compromiso perdido). El normalizador
     * reescribe a la forma canónica "N de <mes>" para reutilizar TODO el flujo
     * monthNamePattern existente (roll de año, clamp de día imposible, acoplamiento
     * con la hora, limpieza del título). El mes se valida contra `months` (nombres
     * + abreviaturas) en el lambda de normalización: si el token no es un mes
     * ("comprar 3 manzanas", "estudiar 2 horas", "enviar 1 correo") NO se reescribe
     * → no se inventan fechas de contenido. Exige el día como dígito (1-2 cifras)
     * y que NO medie "de"/"del" (forma compacta específica); "el 22 de agosto" ya
     * casa monthNamePattern y queda intacto.
     */
    private val bareDayMonthPattern = Regex("""(?i)\b(\d{1,2})\s+([a-záéíóúüñ]+)\b""")

    // "el día siguiente"/"día siguiente" = mañana relativa (sin weekday nombrado).
    // Forma cotidiana de agendar para mañana sin usar la palabra "mañana"
    // ("entregar el día siguiente", "reunión el día siguiente a las 18"). Antes caía a
    // dueAt=null + frase completa como título (vencimiento olvidado), o con hora se
    // agendaba a HOY (fecha equivocada: P1). Se normaliza a "mañana" para reutilizar
    // TODO el flujo existente (mananaAsDate → +1d, hora explícita, limpieza del título).
    // Exige "día" genérico (no weekday): "el martes siguiente" no se toca (lo resuelve
    // weekdayPattern como próxima ocurrencia estricta, c.148). "siguiente" sin "día"
    // tampoco se toca (ambiguo: "capítulo siguiente" es contenido).
    private val dayAfterPattern = Regex("""(?i)\b(?:el\s+)?d[ií]a\s+siguiente\b""")

    // "el 15 del 9" = día 15 del mes 9 (septiembre). Forma numérica LATAM cotidiana para
    // agendar vencimientos. Antes dayOfMonthPattern casaba con "el 15" → 15 del mes en
    // curso (equivocado) y "del 9" sobrevivía como residuo del título → vencimiento en mes
    // equivocado + título sucio (P1). Se normaliza a "N/M" para reutilizar TODO el flujo
    // numericDatePattern existente (roll de año, clamp de día imposible c.146, año
    // explícito opcional). Exige artículo "el"/"día" previo para reducir falsos positivos.
    // "antes del 15 del 9": mismo defecto (beforeDeadlineDayPattern robaba "antes del 15"
    // → 15 de agosto + residuo "del 9"); el prefijo "antes del/de" se normaliza igual a
    // "N/M" y el plazo se ancla al día N (consistente con c.147 "antes del N").
    // "de aquí al 15 del 9"/"de acá al 15 del 9": mismo defecto (el reescritor de conector
    // "de aquí al"→"el" se ejecuta DESPUÉS de esta normalización, así que "al 15" caía a
    // dayOfMonthPattern→mes en curso + residuo "del 9"). El prefijo direccional-temporal
    // se admite aquí directamente → "N/M" y reutiliza TODO el flujo numericDatePattern;
    // el plazo se ancla al día N (consistente con c.134 "de aquí al N" suelto). El
    // reescritor de c.134 sigue limpiando el conector huérfano para las demás fechas
    // (viernes, mañana, N de <mes nombre>).
    private val dayOfMonthNumericMonthPattern =
        Regex("""(?i)\b(?:antes\s+del?\s+|de\s+aqu[íi]\s+al\s+|de\s+ac[aá]\s+al\s+|el\s+(?:d[ií]a\s+)?|d[ií]a\s+)([0-3]?\d)\s+del?\s+([01]?\d)(?![/-])(?:\s+del?\s+(\d{2,4}))?\b(?!\s+de\s+cada)""")

    /**
     * Rango de días multi-evento ("vacaciones del 15 al 20 de diciembre",
     * "viaje del 3 al 8 de enero", "curso del 10 al 14 de marzo del 2027").
     * Antes NO se reconocía: `monthNamePattern` capturaba SOLO el día final
     * ("20 de diciembre") y dejaba "del 15 al" como residuo pegado al título
     * → "vacaciones del 15 al" (contenido mutilado, P1 captura/datos); en
     * algunos casos el día final tampoco casaba y la cita caía a `dueAt=null`
     * con título basura ("congreso del 20 al 25"). Vacaciones, viajes, cursos
     * y ferias son captura cotidiana: el compromiso quedaba olvidado o
     * irreconocible.
     *
     * Se normaliza al día FINAL (cierre del evento) reutilizando TODO el flujo
     * `monthNamePattern` (roll de año, clamp de día, acoplamiento con hora,
     * limpieza del título): "del 15 al 20 de diciembre" → "el 20 de diciembre".
     * Exige nombre de mes explícito (validado) para no agendar rangos numéricos
     * de contenido ("del 15 al 20 por ciento", "del 3 al 5 de unidades"):
     * si el token no es un mes, se deja intacto. El día inicial se descarta a
     * propósito: Ordía ancla el vencimiento al CIERRE del rango (último día),
     * coherente con cómo ya resolvía el día final cuando lo capturaba parcial.
     *
     * El conector de INICIO admite "del"/"de" y también "desde [el]": "desde el
     * 15 al 20 de diciembre" / "desde el 15 hasta el 20 de diciembre" es la misma
     * forma cotidiana que "del 15 al 20". Antes "desde" no casaba: el extremo
     * final ("20 de diciembre") era anclado por `monthNamePattern` al CIERRE
     * (fecha correcta), pero "desde el 15" sobrevivía como residuo del título
     * ("congreso desde", contenido mutilado, P1 captura). Simétrico del conector
     * de CIERRE "hasta [el]" ya admitido. No colisiona con el rewriter de HORA
     * `desdeRangeNormalizerRewriter` (éste exige mes/día, aquél dígitos de hora).
     */
    private val dayRangePattern = Regex(
        """(?i)\b(?:(?:del?|desde\s+el?)\s+)?(\d{1,2})(?![/-])\s+(?:al|hasta(?:\s+el)?)\s+(\d{1,2})(?![/-])""" +
            """(?:\s+del?\s+((?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?))|""" +
            """\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?)?\b"""
    )

    /**
     * Rango de días que CRUZA de mes (o de año): "del 28 de febrero al 1 de marzo",
     * "del 31 de diciembre al 2 de enero", "del 28 de dic del 2026 al 3 de ene del 2027".
     * A diferencia de [dayRangePattern] (un solo mes compartido al final, "del 15 al 20
     * de diciembre"), aquí CADA extremo lleva su propio mes. Antes este rango NO casaba:
     * caía separado, el extremo inicial ("28 de febrero") lo consumía [monthNamePattern]
     * ANCLANDO el vencimiento al día de INICIO en vez del CIERRE (1 de marzo), y los
     * conectores "del al" sobrevivían como residuo del título ("feria del al"). Pérdida
     * del dato temporal real de eventos que cruzan mes/año (ferias, congresos, viajes
     * de fin de mes): la fecha recordada era la de apertura, no la de cierre.
     *
     * Se ancla al CIERRE reescribiendo a "el <díaCierre> de <mesCierre> [del <añoCierre>]"
     * para reutilizar TODO el flujo [monthNamePattern] (roll de año, clamp de día,
     * acoplamiento con hora, limpieza del título). El año del cierre: si viene explícito
     * en el extremo de cierre se usa ése; si no, se hereda el del extremo de apertura
     * (mismo año del evento); si ninguno, se omite y [monthNamePattern] aplica su roll.
     * Va ANTES que [dayRangePattern] (no colisiona: éste exige dos tokens de mes, aquél
     * exige un solo mes al final tras "al N") y antes de [bareDayMonthPattern].
     */
    private val crossMonthDayRangePattern = Regex(
        """(?i)\b(?:(?:del?|desde\s+el?)\s+)?(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?""" +
            """\s+(?:al|hasta(?:\s+el)?)\s+(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b"""
    )

    /**
     * Rango de días con conector "entre...y" que CRUZA de mes (o de año):
     * "entre el 28 de febrero y el 1 de marzo", "entre el 31 de diciembre y el 2 de enero".
     * Forma de rango cotidiana alternativa a "del ... al ..." (c.443 cross-mes). Antes NO
     * se reconocía: [monthNamePattern] consumía el extremo INICIAL y anclaba el vencimiento
     * al día de APERTURA en vez del CIERRE, y "entre ... y ..." sobrevivía como residuo del
     * título ("feria entre y"). Misma clase de bug que c.443, conector distinto. Se ancla al
     * CIERRE reescribiendo a "el <díaCierre> de <mesCierre> [del <añoCierre>]" para reutilizar
     * TODO el flujo [monthNamePattern]. Va ANTES que [entreDayRangePattern] (mismo mes) y de
     * [crossMonthDayRangePattern]/[dayRangePattern]; no colisiona con [entreRangeNormalizerRewriter]
     * (rangos de HORA: aquél exige dígitos de hora sin "de MES"). Exige AMBOS tokens de mes
     * válidos contra `months` (no agenda contenido). El año del cierre: explícito si lo trae;
     * si no, hereda el de apertura; si ninguno, se omite y [monthNamePattern] aplica su roll.
     */
    private val entreCrossMonthDayRangePattern = Regex(
        """(?i)\bentre\s+el?\s+(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?""" +
            """\s+y\s+el?\s+(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b"""
    )

    /**
     * Rango de días con conector "entre...y" y UN solo mes compartido al final:
     * "entre el 15 y el 20 de diciembre". Simétrico de [dayRangePattern] (c.376, "del 15 al 20
     * de diciembre") pero con conector "entre...y". Antes NO se reconocía: anclaba al día
     * INICIAL y dejaba "entre" como residuo del título ("feria entre"). Se ancla al CIERRE
     * reescribiendo a "el <díaCierre> de <mes> [del <año>]". Va después de
     * [entreCrossMonthDayRangePattern] (éste exige DOS meses, aquél UNO al final) y antes de
     * [dayRangePattern]. Admite el cualificador relativo "del mes que viene"/"próximos meses"
     * como [dayRangePattern]. Exige mes válido o cualificador relativo; si no, se deja intacto.
     */
    private val entreDayRangePattern = Regex(
        """(?i)\bentre\s+el?\s+(\d{1,2})(?![/-])\s+y\s+el?\s+(\d{1,2})(?![/-])""" +
            """(?:\s+del?\s+((?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?))|""" +
            """\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?)?\b"""
    )

    /**
     * Rango "del D1 de MES [del A1] al D2" / "entre el D1 de MES [del A1] y el D2":
     * el mes va en el extremo INICIAL y el CIERRE es un día SUELTO (sin mes). Forma
     * cotidiana real ("feria del 15 de diciembre al 20", "feria entre el 15 de diciembre
     * y el 20"). Antes NO casaba: [crossMonthDayRangePattern] exige mes en AMBOS extremos,
     * [dayRangePattern]/[entreDayRangePattern] exigen el mes al FINAL. Al no casar, el
     * extremo inicial ("15 de diciembre") lo consumía [monthNamePattern] ANCLANDO el
     * vencimiento al día de APERTURA en vez del CIERRE, y los conectores sobrevivían como
     * residuo del título ("feria del al 20", "feria entre y"). Se ancla al CIERRE
     * reescribiendo a "el D2 de MES [del A1]" para reutilizar TODO el flujo [monthNamePattern].
     * El `(?!\s+del?\s+[a-záéíóúüñ])` tras D2 evita tragarse un cierre con su propio mes
     * (forma cross-mes), que ya resuelve [crossMonthDayRangePattern]/[entreCrossMonthDayRangePattern].
     * Va DESPUÉS de los patrones de DOS meses y de mes-al-final (no colisiona: ésos exigen
     * mes en la posición donde aquí el cierre es SUELTO). Exige mes de inicio válido contra
     * `months` (no agenda contenido "del 3 de unidades al 5").
     */
    private val startMonthBareEndDayRangePattern = Regex(
        """(?i)\b(?:(?:del?|desde\s+el?)\s+)?(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?""" +
            """\s+(?:al|hasta(?:\s+el)?)\s+(\d{1,2})(?![/-])(?!\s+del?\s+[a-záéíóúüñ])"""
    )
    private val entreStartMonthBareEndDayRangePattern = Regex(
        """(?i)\bentre\s+el?\s+(\d{1,2})(?![/-])\s+del?\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?""" +
            """\s+y\s+el?\s+(\d{1,2})(?![/-])(?!\s+del?\s+[a-záéíóúüñ])"""
    )

    /**
     * Rango de días de la semana como evento ÚNICO ("taller del martes al jueves",
     * "reunión del lunes al viernes", "curso del miércoles al viernes"). Simétrico de
     * [dayRangePattern] (c.377, "del 15 al 20 de diciembre"→"el 20"): el rango
     * "del X al Y" con días de la SEMANA se ancla al CIERRE (Y) y se reescribe a
     * "el Y" para que [weekdayPattern] lo consuma limpio.
     *
     * Antes este rango caía al vacío: [weekdayPattern].find anclaba al PRIMER día
     * ("del lunes", consumido por su prefijo `del`) y el segundo ("al viernes") nunca
     * se re-emparejaba → el conector "al" sobrevivía pegado al título ("reunión al",
     * contenido mutilado, P2) con la fecha anclada al inicio en vez del cierre.
     *
     * Exige la forma contracta "del ... al ..." (de+el, a+el): es la forma de rango
     * puntual (un taller/viaje/curso que abarca esos días). La forma NO contracta
     * "de lunes a viernes" / "entre lunes y viernes" es la SEMANA LABORAL recurrente
     * (Lun-Vie) y la resuelve [weekdayRangePattern] en parseRecurrence (c.282) como
     * WEEKLY; aquí NO se toca (no se reclama como evento único) para no regresar esa
     * cadencia. Tampoco casa el par lunes-viernes específico de la semana laboral.
     *
     * El conector de cierre admite "hasta"/"hasta el" además de "al" (c.380): "del
     * lunes hasta el viernes" es el mismo rango inclusivo. Antes "hasta" no casaba
     * aquí y caía a la normalización de plazo "hasta el viernes"→"el viernes" (c.134),
     * que fragmentaba el par ("del lunes el viernes") y dejaba residuo. Como exige un
     * weekday a AMBOS lados, no colisiona con "hasta el viernes" suelto (fecha límite)
     * ni con "hasta las 5" (hora).
     */
    private val weekdayPairRangePattern = Regex(
        """(?i)\b(?:del|desde\s+el)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+(?:al|hasta(?:\s+el)?)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""
    )

    /**
     * Nombres de hora escritos en español (dos..veintiuno), ordenados de mayor a menor
     * longitud para que la alternación regex no se quede con un prefijo ("tres" dentro de
     * "trece"). Se excluye "un/una/uno" (la hora 1 se dice "a la una", con otro conector).
     * Reutilizado por [timePatterns] (a las N) y [standaloneHourPartOfDayStripPattern] (N de la
     * tarde) para que las horas escritas —cotidianas en español— se resuelvan en vez de
     * caer como residuo del título o agendarse a la hora canónica de la parte del día.
     */
    private const val WRITTEN_HOUR_ALT =
        "veintiuna|veintiuno|veinte|diecinueve|dieciocho|diecisiete|diecis[eé]is|quince|catorce|trece|doce|once|diez|nueve|ocho|siete|seis|cinco|cuatro|tres|dos"

    private fun parseHour(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        return parseWrittenNumber(raw)?.toInt()
    }

    /**
     * Minutos sub-hora escritos que acompañan a "y"/"menos" en una hora de reloj
     * ("a las once y veinte", "a las 3 menos cuarto", "al mediodía y media"). Son los
     * múltiplos cotidianos del español hablado (cinco, diez, cuarto, veinte,
     * veinticinco, media/treinta, treinta y cinco, cuarenta, cuarenta y cinco /
     * tres cuartos, cincuenta, cincuenta y cinco) más el dígito suelto ("y 20").
     *
     * Antes el grupo "y" de [timePatterns] sólo admitía "media"/"cuarto", así que
     * "a las once y veinte" / "a las diez y tres cuartos" dejaban "y veinte"/
     * "y tres cuartos" como residuo del título y la cita caía en punto (15-45 min
     * mal). Es la asimetría positiva del "menos veinte/cinco/diez/veinticinco"
     * (ciclo 114) que SÍ restaba minutos; esta rama positiva ahora suma lo mismo.
     * Simétrico también del "a las N y media"/"y cuarto" ya resuelto. Las horas
     * canónicas mediodía/medianoche reciben la misma fracción ("al mediodía y media"
     * → 12:30) vía el grupo 1 de sus patrones propios.
     *
     * El orden pone las formas compuestas ANTES ("tres cuartos"/"cuarenta y cinco"
     * antes que "cuarenta"/"tres") para que la alternación regex no se quede con un
     * prefijo. La [CLOCK_FRACTION_MAP] es la fuente única de palabras→minutos; el
     * fragmento y el resolver se construyen a partir de ella (DRY).
     */
    private val CLOCK_FRACTION_MAP = listOf(
        "tres cuartos" to 45, "cuarenta y cinco" to 45, "cincuenta y cinco" to 55,
        "treinta y cinco" to 35, "veinticinco" to 25, "media" to 30, "treinta" to 30,
        "cuarto" to 15, "cuarenta" to 40, "cincuenta" to 50, "veinte" to 20,
        "diez" to 10, "cinco" to 5
    )
    private val CLOCK_FRACTION_ALT = CLOCK_FRACTION_MAP.joinToString("|") { Regex.escape(it.first) }
    /** Fragmento de rama POSITIVA "y <min>": `y\s+(?:media|cuarto|tres cuartos|...|\d{1,2})`. */
    private val CLOCK_FRACTION_Y = """y\s+(?:$CLOCK_FRACTION_ALT|\d{1,2})"""
    /** Fragmento de rama NEGATIVA "menos <min>": `menos\s+(?:cuarto|cinco|...|\d{1,2})`. */
    private val CLOCK_FRACTION_MENOS = """menos\s+(?:$CLOCK_FRACTION_ALT|\d{1,2})"""
    /**
     * Sufijo opcional NO capturante "en punto": la frase cotidiana española que marca la
     * hora exacta ("a las 9 en punto", "a las nueve en punto de la tarde"). Antes NO se
     * consumía: el patrón casaba "a las 9" y dejaba "en punto" como residuo del título
     * ("reunión en punto") — la cita se agendaba bien pero el título quedaba mutilado
     * con basura horaria (P2 captura/título limpio). Va al FINAL del patrón (tras
     * fracción/meridiem/sufijo), NO capturante, así no altera el layout de grupos ni la
     * resolución AM/PM. Como "en punto" es evidencia de reloj inequívoca, se cuenta en
     * [hasClockEvidence] para que el guard anti-cuenta (c.361) no la trate como hora
     * ambigua ("a las 9 en punto" no es "a las 9 [personas]").
     *
     * El separador es `\s*` (no `\s+`) porque los grupos opcionales anteriores (meridiem,
     * sufijo horas/hs/h, fracción) son greedy con su `\s*`/`\s+` y, al no casar su
     * contenido, dejan el `\s+` de este sufijo sin espacio que consumir → el sufijo
     * entero falla y "en punto" queda como residuo (bug confirmado: "a las 9 en punto"
     * → match "a las 9 " + título "reunión en punto"). Con `\s*` el sufijo casa aunque el
     * espacio previo ya lo haya consumido un grupo greedy; el `en\s+punto` interno sigue
     * exigiendo su propio separador, así que no casa "9en punto" pegado.
     */
    private val EN_PUNTO_SUFFIX = """(?:\s*en\s+punto)?"""

    /**
     * Sufijo opcional NO capturante de modificador de aproximación post-hora:
     * "más o menos", "aproximadamente", "y pico", "pasados", "justo". Frases cotidianas que matizan una hora
     * ya reconocida ("a las 9 más o menos", "a las nueve y pico", "reunión a las 3
     * aproximadamente"). Antes NO se consumían: el patrón casaba la hora y dejaba el
     * modificador como residuo del título ("reunión más o menos", "reunión y pico") — la
     * cita se agendaba bien pero el título quedaba mutilado con basura (P2 captura/título
     * limpio). Simétrico de [EN_PUNTO_SUFFIX].
     *
     * "justo" (c.428b) es de PRECISIÓN, no aproximación ("a las 7 de la tarde justo" =
     * exactamente 19:00), pero funcionalmente es el mismo caso: sufijo post-hora a
     * limpiar. Antes "justo" como SUFIJO no se consumía (sólo como PREFIJO: c.393/c.401
     * reescriben "justo a las N"→"a las N"): "llamar a las 7 de la tarde justo" →
     * título 'llamar justo' (residuo) pese a agendar 19:00. Aquí los patrones de hora ya
     * capturaron la hora antes del sufijo, así "justo" sólo puede modificar esa hora (no
     * hay uso de tema/cuenta tras una hora capturada: *"a las 7 justo personas" no es
     * gramatical). No colisiona con el prefijo "justo": c.393 ya lo reescribió a "a ".
     *
     * La hora se conserva en punto (sin desplazar): "a las 9 y pico" → 09:00 (la
     * aproximación sub-hora no es modelable sin un campo de jitter; el valor útil es la
     * hora base, igual que "a eso de las 9"). Mejor agendar 09:00 exacto con título limpio
     * que dejar el modificador como residuo.
     *
     * "y pico" es posicional: va SIEMPRE tras la hora (nunca "pico" suelto al inicio),
     * y su "y" NO casa como [CLOCK_FRACTION_Y] ("y media"/"y cuarto"/"y \d") porque
     * "pico" no es fracción ni dígito, así que no roba ni colisiona. Se ubica DESPUÉS de
     * [EN_PUNTO_SUFFIX] ("a las 9 en punto más o menos" es raro pero válido; el orden no
     * afecta la resolución horaria).
     *
     * Estos modificadores son evidencia de reloj inequívoca (no hay "9 más o menos
     * [personas]"): se cuentan en [hasClockEvidence] para el guard anti-cuenta (c.361).
     */
    private val APPROX_TIME_SUFFIX = """(?:\s*(?:m[aá]s\s+o\s+menos|aproximadamente|y\s+pico|pasad[ao]s?|justo))?"""

    /**
     * Resuelve la fracción sub-hora de un grupo de [timePatterns] en minutos (0..59).
     * Acepta la frase completa tal cual la captura el grupo ("y media", "menos cuarto",
     * "y tres cuartos", "y 20") o la palabra suelta ("media", "tres cuartos"); en el
     * caso negativo resta minutos (con wrap de 24 h a cargo del llamador). Devuelve
     * `null` cuando [raw] no es una fracción reconocida (p. ej. un meridiem que llegó
     * en el mismo grupo por el layout variable de los patrones), para que el llamador
     * la trate como meridiem y no como fracción.
     */
    private fun resolveClockFraction(raw: String): Int? {
        val s = raw.trim().lowercase().replace("ñ", "n").replace("í", "i")
        val positive = s.startsWith("y ")
        val negative = s.startsWith("menos ")
        if (!positive && !negative) {
            // Palabra suelta (caso mediodía/medianoche, grupo 1 = "media"/"tres cuartos"):
            // se trata como positiva. Si no casa con ninguna palabra, no es fracción.
            val m = CLOCK_FRACTION_MAP.firstOrNull { s == it.first }?.second
                ?: s.toIntOrNull()?.takeIf { it in 0..59 }
            return m
        }
        val body = s.removePrefix("y ").removePrefix("menos ")
        val m = CLOCK_FRACTION_MAP.firstOrNull { body == it.first }?.second
            ?: body.toIntOrNull()?.takeIf { it in 0..59 }
            ?: return null
        return if (negative) -m else m
    }

    /**
     * Continuación "segura" tras una hora en punto sin evidencia de reloj (`:MM`,
     * meridiem, fracción, sufijo horas/hs/h): puntuación, conjunciones/preposiciones
     * (y/o/con/de/del/en/para/hasta/desde/luego/después/pero/porque/por/sin/sobre/a/al/
     * el/la/los/las/un/una) o adverbios temporales (mañana/hoy/ayer/anteayer/días de la
     * semana). Si lo que sigue NO es ninguna de estas, es un SUSTANTIVO de cantidad
     * ("personas", "cajas", "entradas", "habitaciones", "ventas") y la frase "las N
     * <sustantivo>" es una CUENTA ("a las 10 personas" = "a las 10 [personas]"), no una
     * cita. Reutilizado por el rango horario ([followedByCount] en `rangeMatch`) y por
     * el guard anti-falso-positivo de "a las N" en punto (c.361): evita agendar
     * "hablar a las 10 personas del equipo" como una cita falsa a las 10:00 con título
     * mutilado ("hablar personas del equipo"). Simétrico del lookahead de evidencia de
     * reloj que exigen los marcadores aproximados "hacia/sobre/para" (que rechazan la
     * hora en punto ambigua por completo); aquí la hora en punto SÍ se admite como cita
     * salvo cuando le sigue un sustantivo de cantidad.
     */
    private val countNounFollowerPattern =
        Regex("""(?i)^\s*(?:,|\.|;|:|!|\?|y\b|o\b|con\b|de\b|del\b|en\b|para\b|hasta\b|desde\b|luego\b|después\b|despues\b|pero\b|porque\b|por\b|sin\b|sobre\b|a\b|al\b|el\b|la\b|los\b|las\b|un\b|una\b|mañana\b|manana\b|hoy\b|ayer\b|anteayer\b|lunes\b|martes\b|miércoles\b|miercoles\b|jueves\b|viernes\b|sábado\b|sabado\b|domingo\b|$)""")

    // c.514 — ¿un match de [timePatterns] es en realidad una CUENTA ("a las 3 cajas"),
    // no una cita? Reúne el guard anti-cuenta (c.361) en un predicado reutilizable para
    // que la SELECCIÓN de la hora (saltar al siguiente match válido) y la LIMPIEZA del
    // título (preservar el número cuando es cuenta) compartan la misma definición. Antes
    // el guard vivía sólo en `explicitTimeData`: si el PRIMER match era cuenta ("enviar a
    // las 5 invitaciones a las 9"), se rechazaba y NO se buscaba el siguiente → la cita
    // real "a las 9" se olvidaba (dueAt=null), y además el número "5" se borraba del
    // título (pérdida de cantidad). `source` es el texto completo (para calcular el tail).
    // c.532 — ¿el tail tras "hasta las N"/"hasta la una|\d" (consumido por el rewrite de
    // HORA de "hasta") es una CUENTA ("hasta las 5 cajas") y no una cita? Predicado
    // reutilizable para el guard anti-cuenta simétrico de aPartirDe/desde (c.442). A
    // diferencia de [timeMatchIsCountNoun] (que opera sobre un match de [timePatterns] ya
    // resuelto con sus grupos de meridiem/fracción), aquí el rewrite de "hasta" corre ANTES
    // de [timePatterns] y sólo ha consumido "hasta las N" (sin grupos), así que la
    // evidencia de reloj debe inferirse del tail. Es CUENTA cuando:
    //   1. NO hay evidencia de reloj inmediata tras la hora (`:MM`, meridiem am/pm, parte
    //      del día "de la tarde", fracción "y media"/"menos cuarto", unidad "horas/hs/h",
    //      "en punto"), Y
    //   2. el tail arranca con un sustantivo plural de cantidad (>=3 letras terminadas en
    //      's', p. ej. cajas/personas/habitaciones/invitaciones/entradas/ventas) que NO sea
    //      la unidad horaria "horas/hs/h".
    // Consistente con el baseline "a las N <plural>" de [timeMatchIsCountNoun] (c.514):
    // "hasta las 5 cajas"→count (preserva "hasta"); "hasta las 5 horas"→cita (la unidad
    // "horas" es evidencia de reloj); "hasta las 5 pm"/"hasta las 5:30"/"hasta las 5 y
    // media"/"hasta las 5" (fin)→cita (reescritura normal a "a las N").
    private fun hastaHourTailIsCountNoun(tail: String): Boolean {
        val t = tail.lowercase()
        // 2. ¿Arranca con un sustantivo plural (>=3 letras, termina en 's')?
        val pluralNoun = Regex("""(?i)^\s*[a-záéíóúñ]{3,}s\b""").containsMatchIn(t)
        if (!pluralNoun) return false
        // 1. ¿Ese plural es evidencia de reloj (unidad horaria u otra evidencia inmediata)?
        //    "horas"/"hs"/"h" solas = unidad horaria → cita, no cuenta. \bhoras?\b no casa
        //    "habitaciones" (h-a-b..., no h-o-r-a) y \bh\b exige límite tras la 'h', así que
        //    "habitaciones" (sigue 'a') no casa → cuenta (consistente con "a las 5
        //    habitaciones" de c.514). El resto de evidencia (:MM, meridiem, parte del día,
        //    fracción, "en punto") se admite ADYACENTE (`\s*`) igual que [timePatterns].
        val clockEvidenceAtStart = Regex(
            """(?i)^\s*(?::|\bhoras?\b|\bhs\b|\bh\b|a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada)|del\s+mediod[ií]a|en\s+punto|y\s+(?:media|cuarto|pico)|menos\s+cuarto)"""
        ).containsMatchIn(t)
        return !clockEvidenceAtStart
    }

    /**
     * c.1045: ¿este match de [timePatterns] cierra una cadena narrativa
     * inequívoca «ya/ahora/ahorita <clíticos> <pretérito>» que ABRE el
     * enunciado («ya me llamó a las 8», «ahora llegó el cartero a las 9»)?
     * La hora pertenece al relato de un hecho cumplido: anclarla la dejaba
     * en el PASADO hoy (compromiso vencido falso) y borrarla mutilaba el
     * título (doble daño, hermano del weekday final de c.1041). Usado por
     * la selección de `timeMatch` (fecha) y por el fold del título para que
     * nunca diverjan (doctrina c.930). Conservador: sólo prefijo narrativo
     * ([narrativePreteritePrefix]); el presente con «ya/ahora» («ya te
     * aviso a las 8») sigue anclando byte-idéntico.
     */
    private fun timeMatchIsPreteriteNarrative(match: MatchResult, source: String): Boolean {
        val prefix = source.substring(0, match.range.first)
        // c.1049: MISMO candado «quedar con» del ordinal (c.1048) — «ya
        // quedé con Ana a las 8» es CITA futura, jamás relato. Medida
        // PRE: 4/4 suprimidas injustamente (RUN_LOG c.1049).
        return narrativePreteritePrefix(prefix) && !ordinalHoraQuedarConArrangement.containsMatchIn(prefix)
    }

    private fun timeMatchIsCountNoun(match: MatchResult, source: String): Boolean {
        val mv = match.value.lowercase()
        val hasClockEvidence = mv.contains(":") || mv.contains("h") ||
            mv.contains("en punto") ||
            mv.contains("más o menos") || mv.contains("mas o menos") || mv.contains("aproximadamente") ||
            mv.contains("y pico") || mv.contains("pasada") ||
            match.groupValues.getOrNull(2)?.isNotBlank() == true ||
            match.groupValues.getOrNull(3)?.let { it.lowercase().startsWith("y ") || it.lowercase().startsWith("menos ") } == true ||
            match.groupValues.getOrNull(4)?.isNotBlank() == true ||
            match.groupValues.getOrNull(5)?.isNotBlank() == true
        if (hasClockEvidence) return false
        val tail = source.substring(match.range.last + 1)
        return !countNounFollowerPattern.containsMatchIn(tail) &&
            Regex("""(?i)^\s*[a-záéíóúñ]{3,}s\b""").containsMatchIn(tail)
    }

    private val timePatterns = listOf(
        // "a la una": la hora 1 se dice en femenino singular ("a la una", no "a las 1"),
        // con conector "a la" en vez de "a las". Quedaba sin resolver por el
        // patrón "a las N" (que excluye "un/una/uno" de WRITTEN_HOUR_ALT justo por esto),
        // así que "reunión a la una" caía sin dueAt y con "a la una" como residuo en el
        // título → el usuario olvidaba la cita. Mismo layout de grupos que el patrón
        // "a las N" (1=hora, 2=:MM, 3=y media/cuarto, 4=meridiem) para que
        // [explicitTimeData] lo procese sin ramificación. Admite "del mediodía" como
        // meridiem (PM, → 13:00): "a la una del mediodía" es la forma cotidiana de 1pm.
        // El sufijo de unidad "horas/hs/h" es NO capturante y se admite ANTES y DESPUÉS
        // de la fracción/meridiem (simétrico del reloj "HH:MMh pm" de c.235 y del
        // "a las N" de aquí): así "a la una horas y media" y "a la una h pm" consumen
        // el sufijo completo en vez de dejar fracción/meridiem como residuo en el título.
        // Además de la forma ESCRITA ("una") admite el DÍGITO ("a la 1", "a la 1 pm",
        // "a la 1:30", "a la 1 de la tarde"): es igual de cotidiano y SIN él el dígito
        // caía al patrón de reloj autónomo (N:MM o Nam/pm), que resolvía la hora pero
        // DEJABA el artículo "a la" como residuo del título ("almuerzo con Pedro a la"),
        // o se perdía entera cuando no había evidencia ("reunión a la 1" → NULL aunque
        // "reunión a las 3" sí resuelve a las 03:00: asimetría plural/singular). Sólo el
        // dígito 0?1 es válido en singular ("a la 1"); cualquier otra hora exige el
        // plural ("a las N") — el `\b` final impide casar "a la 12" con hora=1.
        // c.677 — Guard anti-ordinal: "clasificar a la 1ª posición" NO es una cita a
        // la 01:00. El indicador ordinal º/ª no es carácter de palabra en Java regex,
        // así que el `\b` final SÍ existía tras el dígito y la hora casaba con título
        // mutilado ('clasificar ª posición'). El lookahead rechaza el indicador ordinal
        // tras el dígito y, para la forma escrita, "una posición" (simétrico).
        Regex("""(?i)\ba\s+la\s+(una|0?1)(?![ºª]|\s+posici[oó]n\b)(?:(?::|h|[.,])([0-5]\d))?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b"""),
        // Sufijo opcional "(horas?|hs|h)" tras la hora para consumir "a las 9 horas"/
        // "a las 9h" completo: antes "horas" quedaba como residuo en el titulo y, peor,
        // "9 horas" era robado como duracion (540 min falsos). Es NO capturante (no
        // altera la lógica AM/PM ni marca meridiem explícito) y se admite ANTES y DESPUÉS
        // de la fracción/meridiem, simétrico del reloj "HH:MMh pm" de c.235: así el
        // sufijo puede ir primero y la fracción/meridiem después sin romperse. Antes el
        // orden fijo [fracción][meridiem][sufijo] hacía que "a las 9h pm" (→09:00 en vez
        // de 21:00 + residuo "pm"), "a las 9 horas y media" (→09:00 en vez de 09:30 +
        // residuo "y media") y "a las 3:30h pm" (→03:30 en vez de 15:30) dejaran el
        // modificador como residuo y agendaran la cita mal: reunión nocturna 12h antes o
        // 30 min en punto en vez de y media. El `\b` final (con backtracking) deja
        // intacta la "h" de "hola"/"hoy" (igual que el reloj).
        // Grupo 3 opcional "y media"/"y cuarto": fracción sub-hora cotidiana en español
        // ("a las 9 y media" → 09:30, "a las 3 y cuarto" → 03:15). Antes "y media" caía
        // como residuo en el título y la hora quedaba en punto (reunión/cita 30 min mal).
        // Admite horas escritas ("a las nueve", "a las doce y media") vía [WRITTEN_HOUR_ALT];
        // antes esas formas dejaban la hora como residuo y se agendaban a la canónica de
        // la parte del día o sin hora ("reunión a las nueve" → sin dueAt).
        // "h" suelta en el grupo de unidad: "a las 15h"/"a las 9h" (forma compacta de
        // hora más común en español). Sin ella, el `\b` final no casa (entre "5" y "h"
        // no hay límite de palabra) → dueAt perdido + "a las 15h" como residuo. El `\b`
        // tras "h" deja intacta la "h" de "hola"/"hello". Simétrico al reloj "HH:MMh".
        // c.677 — Mismo guard anti-ordinal que "a la 1": "a las 3ª posición" no es una
        // cita a las 03:00 (el `\b` final existía tras el dígito porque º/ª no es
        // carácter de palabra en Java regex) y mutilaba el título ('ª posición').
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?![ºª])(?:(?::|h|[.,])([0-5]\d))?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b"""),
        // Hora de reloj autónoma "HH:MM [h/hs/horas] [am/pm]" en AMBOS órdenes. El sufijo
        // de unidad "h/hs/horas" puede ir ANTES ("3:30h pm") o DESPUÉS ("3:30 pm h") del
        // meridiem: se permite en las dos posiciones (no capturante) para absorberlo
        // siempre. El ":" `:MM` es señal inequívoca de reloj, así que la hora se consume
        // completa y el patrón de duración "Nh" no puede robar los MINUTOS como duración
        // falsa. Antes el sufijo "h" rompía el \b final (entre "30" y "h" no hay límite de
        // palabra): el patrón no casaba, "30h" caía como duración (1440 min clampeados) y
        // la cita quedaba SIN dueAt (olvidada) con título corrupto ("Reunión 15:"); "7:15h"
        // perdía los minutos en silencio (07:00 en vez de 07:15); "3:30h pm" dejaba "pm"
        // como residuo y se agendaba 03:30 AM en vez de 15:30. El layout de grupos se
        // preserva (1=hora, 2=minutos, 3=meridiem): los sufijos son NO capturantes.
        // Sufijo "h" solo permitido con ":" presente (este patrón); el "Nh" sin dos puntos
        // sigue siendo duración (ver [durationPatterns] + guard clockPreceding).
        Regex("""(?i)\b([01]?\d|2[0-4]):([0-5]\d)(?:\s*(?:horas?|hs|h))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?(?:\s*(?:horas?|hs|h))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b"""),
        // "H[:MM] am/pm [h/hs/horas]" con sufijo de unidad en cualquier posición: "9am",
        // "9:30pm", "3:30h pm", "3 pm h". Requiere meridiem (hora 1-12). El sufijo se
        // absorbe antes/después del meridiem para que no quede como residuo en el título.
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?:(?::|h)([0-5]\d))?(?:\s*(?:horas?|hs|h))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)(?:\s*(?:horas?|hs|h))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b"""),
        // "al mediodía"/"a la medianoche" (canónicas 12:00/00:00). Admite prefijo
        // demostrativo opcional ("al"/"a la"/"a") Y los modificadores cotidianos de
        // "a partir de esa hora": "pasada la medianoche"/"pasado el mediodía"/
        // "pasada medianoche"/"pasado mediodía" y "después de la medianoche"/
        // "después del mediodía"/"después de mediodía". Antes estos modificadores NO se
        // consumían: el patrón casaba el "mediodía"/"medianoche" incrustado y dejaba el
        // prefijo ("pasada la"/"pasado"/"después del"/"después de la") como residuo en
        // el título ("llamar pasada la", "llamar pasado") → contenido capturado
        // mutilado (P1). "medianoche"/"mediodía" son inequívocas como hora canónica, así
        // que consumir el modificador no introduce ambigüedad (no colisiona con
        // "el viernes pasado"=previousWeekday ni "la semana pasada"=lastPeriod: esos
        // ponen "pasado/pasada" DESPUÉS del sustantivo; aquí va ANTES de mediodía/
        // medianoche). El negative-lookahead de laterRelativePattern ya excluye
        // "después del/de la N", así que "después del mediodía" no lo roba como +3h.
        // Intensificador "justo" (c.401): "justo al mediodía"/"justo a la medianoche"
        // son formas cotidianas frecuentes (precisión temporal pura). Antes el dueAt
        // se resolvía bien (12:00/00:00) PERO "justo" sobrevivía como residuo en el
        // título. Simétrico de c.391 ("justo" antes de comida/sueño) y c.393
        // ("justo a las N"). Adverbio temporal sin uso de tema/cantidad, sin
        // ambigüedad; admite combinación con modificadores ("justo pasada la
        // medianoche").
        Regex("""(?i)\b(?:justo\s+)?(?:al\s+|a\s+la\s+|a\s+|pasad[oa]\s+(?:el\s+|la\s+)?|despu[eé]s\s+(?:del\s+|de\s+la\s+|de\s+))?mediod[ií]a(?:\s+($CLOCK_FRACTION_Y))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b"""),
        Regex("""(?i)\b(?:justo\s+)?(?:al\s+|a\s+la\s+|a\s+|pasad[oa]\s+(?:el\s+|la\s+)?|despu[eé]s\s+(?:del\s+|de\s+la\s+|de\s+))?medianoche(?:\s+($CLOCK_FRACTION_Y))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b""")
    )
    /**
     * "a eso de" + parte del día: "a eso de la tarde", "a eso del mediodía", "a eso de
     * la noche", "a eso de la madrugada", "a eso de la mañana", "a eso de la medianoche",
     * "a eso de tarde/noche/madrugada" (forma "de" suelta).
     *
     * El patrón "a eso de las N" de [approximateTimePatterns] sólo admite hora
     * numérica/escrita ("a eso de las 5"/"a eso de la una"). Estas formas cotidianas NO
     * se normalizaban: la parte del día SÍ se resolvía a su hora canónica (vía
     * [standalonePartOfDayPattern]/[timePatterns] mediodía/medianoche) PERO "a eso de"
     * sobrevivía como residuo en el título ("pasar recado a eso del", "reunión a eso")
     * → cita bien fechada pero título mutilado (P1 captura/título limpio).
     *
     * Aquí se reescribe al conector canónico que cada patrón downstream espera
     * ("a la tarde"/"al mediodía"/"de tarde"), reutilizando TODO el flujo existente
     * (resolución + limpieza) sin nueva rama de resolución. Es adverbio temporal puro:
     * "a eso de la tarde" no tiene uso de tema/cantidad, así que no necesita evidencia
     * de reloj (igual que "a eso de las 5"). El grupo 1 captura la parte del día para
     * emitir el conector correcto: "del mediodía"→"al mediodía" (contracción),
     * "de la X"→"a la X", "de X"→"de X" (forma "de" suelta, canónica).
     *
     * Se procesa ANTES que [approximateTimePatterns] en [parse] para que "a eso de la
     * tarde" no caiga al patrón "a eso de las N" (que no casa y dejaría residuo).
     */
    private val aEsoDePartOfDayRewriter =
        Regex("""(?i)\ba\s+eso\s+(del\s+mediod[ií]a|de\s+la\s+medianoche|de\s+la\s+(?:ma[nñ]ana|tarde|noche|madrugada)|de\s+(?:tarde|noche|madrugada))\b""")

    /**
     * "a eso de" + hora DESNUDA (sin "las"): "a eso de nueve", "a eso de 9",
     * "a eso de nueve de la noche", "a eso de 3 y media", "a eso de 9pm".
     *
     * "a eso de" es adverbio temporal puro (sin uso de tema/cantidad, a diferencia de
     * "sobre/hacia/cerca/alrededor"): lo que sigue a "a eso de" sólo puede ser una hora.
     * Por eso el patrón "a eso de las N" de [approximateTimePatterns] admite hora en punto
     * sin meridiem ("a eso de las 5"). PERO exigía el artículo "las"/"la una", así que las
     * formas cotidianas que OMITEN "las" ("a eso de nueve", "a eso de 9", muy frecuentes
     * en notas rápidas/móvil) NO se normalizaban: caían a `dueAt=null` → la cita NUNCA se
     * agendaba (sin recordatorio, fuera de What Now: el usuario olvidaba la cita, P1
     * datos/captura/evitar olvidos). Y con parte del día ("a eso de nueve de la noche") la
     * hora SÍ se resolvía vía el patrón autónomo "N de la tarde/noche" PERO "a eso de"
     * sobrevivía como residuo del título ("cita a eso de": contenido capturado mutilado, P1).
     *
     * Aquí se reescribe "a eso de " → "a las " conservando intacta la hora y sus
     * modificadores (`:MM`, "y media"/"menos cuarto", "de la noche"/"del mediodía",
     * am/pm, "horas/hs/h"), reutilizando TODO el flujo de [timePatterns] "a las N"
     * (resolución AM/PM, fracción, limpieza del título) sin nueva rama de resolución.
     *
     * El lookahead exige una hora válida (dígito `[01]?\d|2[0-4]` o escrita
     * [WRITTEN_HOUR_ALT], que EXCLUYE "un/una/uno": "a eso de la una" lo trata
     * [approximateTimePatterns] vía "la una", y aquí no casa para no emitir el agramatical
     * "a las una"). El resto del tail (`:MM`/fracción/meridiem/unidad) es opcional y
     * NO capturante: sólo valida que lo que sigue es una hora de reloj y se queda en el
     * texto para que timePatterns lo consuma. Se procesa ANTES que el fold de
     * [approximateTimePatterns]: así "a eso de nueve" → "a las nueve" (aquí) y luego el
     * fold normaliza cualquier "a eso de las N" restante. "a eso de las 5" no casa aquí
     * (tras "de" viene "las", no una hora desnuda) → lo deja al fold, sin conflicto.
     */
    private val aEsoDeBareHourRewriter =
        Regex("""(?i)\ba\s+eso\s+de\s+(?=(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)[0-5]\d)?(?:\s*(?:horas?|hs|h))?(?:\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\b)""")

    /**
     * "las N" DESENUDA (sin introductor "a"/"para"/"desde"/"hasta"/...): "cita las 3",
     * "reunión las 7 y media", "llamar las 4:30", "almuerzo las 3 de la tarde", "las nueve",
     * "las 10 menos cuarto". Forma cotidiana, sobre todo en móvil/notas rápidas y en
     * español latino ("quedamos las 3", "cita las 7 y media"). Antes estas frases NO se
     * normalizaban: las que NO traían meridiana caían a `dueAt=null` (la cita NUNCA se
     * agendaba → el usuario la olvidaba, P1 datos/evitar olvidos) y las con ":MM" caían al
     * patrón autónomo "HH:MM" que resolvía la hora PERO dejaba "las" como residuo en el
     * título ("cita las" → contenido capturado mutilado, P1 título limpio).
     *
     * Aquí se reescribe " las <hora> " → "a las <hora> " para que reutilice TODO el flujo
     * robusto de [timePatterns] "a las N" (resolución AM/PM, fracción, wrap 24 h, guard
     * anti-cuenta y limpieza del título) sin nueva rama de resolución. Simétrico de
     * [aEsoDeBareHourRewriter] ("a eso de N"→"a las N") y de [paraTimeIntroPattern]
     * ("para las N"→"a las N").
     *
     * Para NO eludir guards ya existentes, el rewriter comprueba en [parse] la palabra
     * inmediatamente anterior a " las N": si es un conector temporal o determinante de
     * cadencia cubierto por su propio flujo (ver [BARE_LAS_HOUR_GUARDED_PREFIXES]:
     * "de"→antes/después/a-partir-de/cerca/alrededor-de; "para"→paraTimeIntroPattern;
     * "todas"/"todos"→cadencia "todas las N semanas"; "desde"/"hasta"/"a"→rangos y forma
     * canónica), se deja intacto y el conector lo gobierna con su guard. Sólo se reescribe
     * "las N" precedida de contenido real (verbo/sustantivo) o del inicio de la frase.
     *
     * Grupo 1 = especificación horaria completa ("3:30"/"7 y media"/"3 de la tarde"/
     * "nueve"/"9"/"10 menos cuarto"/"9 en punto"); grupo 2 = ":MM". Cuando hay evidencia de
     * reloj (`:MM`, meridiana, fracción, "horas/hs/h", "en punto", aproximadores, u hora
     * ESCRITA) la hora es inequívoca y SIEMPRE se reescribe. Cuando NO la hay ("las 3"
     * desnuda) se aplica el guard anti-cuenta [countNounFollowerPattern] (c.442, mismo que
     * [aPartirDeRewriter]/[desdeRewriter]): si lo que sigue es un sustantivo plural
     * ("compra las 3 manzanas") NO se reescribe (preserva el número como cantidad y evita
     * inventar una cita falsa); si es fin de cadena/puntuación/conjunción temporal SÍ
     * ("cita las 3", "reunión las 3, traer café").
     */
    private val bareLasHourRewriter =
        Regex("""(?i)\blas\s+((?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h|[.,])([0-5]\d))?(?:\s*(?:horas?|hs|h))?(?:\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?$EN_PUNTO_SUFFIX$APPROX_TIME_SUFFIX\b)""")

    /**
     * Palabras que, si preceden inmediatamente a " las N", indican que un conector/guard
     * existente ya gobierna esa hora y [bareLasHourRewriter] NO debe tocarla (para no
     * eludir su guard anti-cuenta o de cadencia):
     * - "de" → "antes/después/a partir de/cerca/alrededor de las N" (guards de franja y
     *   anti-invento sin meridio);
     * - "para" → [paraTimeIntroPattern] (guard anti-cuenta "para las 9 personas");
     * - "todas"/"todos" → cadencia ("todas las dos semanas", "todos los dos meses");
     * - "desde"/"hasta" → rangos temporales;
     * - "a" → forma canónica "a las N" (producida por rewriters previos: aEsoDe/aPartirDe/
     *   desde/para/aproximados); reescribirla duplicaría "a a las N".
     * - "sobre"/"hacia" → [approximateTimePatterns] (guard anti-cuenta "sobre las 3 cajas").
     */
    private val BARE_LAS_HOUR_GUARDED_PREFIXES = setOf(
        "de", "para", "todas", "todos", "desde", "hasta", "a", "sobre", "hacia"
    )

    /**
     * "a partir de" + anclaje temporal cotidiano: "a partir de las 3 de la tarde",
     * "a partir de la mañana/tarde/noche/madrugada/medianoche", "a partir del mediodía/
     * amanecer/atardecer/anochecer/ocaso/alba". Significa "desde esa hora en adelante"
     * (inicio de franja): el usuario que escribe "cita a partir de las 3 de la tarde"
     * quiere que empiece a las 3.
     *
     * Antes estas formas NO se normalizaban: la hora/fecha SÍ se resolvía vía
     * [timePatterns]/[standalonePartOfDayPattern]/[amanecerPattern]/[atardecerPattern]
     * PERO "a partir de" sobrevivía como residuo en el título ("cita a partir de",
     * "almuerzo a partir del", "reunión a partir de la") → cita bien fechada pero
     * título mutilado (P1 captura/título limpio). Misma familia que c.424 ("antes de")
     * y c.432 ("después de"), que también dejaban el conector como residuo.
     *
     * Aquí se reescribe al conector canónico que cada patrón downstream espera,
     * reutilizando TODO el flujo existente (resolución + limpieza) sin nueva rama de
     * resolución. Es adverbio temporal puro cuando va seguido de un anclaje de hora
     * inequívoco. El lookahead restringe a anclajes temporales para NO tocar usos de
     * tema/proyecto ("a partir de los datos", "a partir del informe") ni fechas de
     * calendario ("a partir del viernes", "a partir de mañana"), que son rangos de
     * FECHA (no de hora) y cuyo conector no debe reescribirse aquí. Grupo 1 captura el
     * anclaje para emitir el conector correcto:
     *   "las N..." → "a las N..." ([timePatterns]),
     *   "la mañana/tarde/noche/madrugada/medianoche" → "a la X" ([standalonePartOfDayPattern]/[timePatterns]),
     *   "el mediodía/amanecer/atardecer/anochecer/ocaso/alba/..." → "al X" ([timePatterns]/[amanecerPattern]/[atardecerPattern]).
     */
    private val aPartirDeRewriter =
        Regex("""(?i)\ba\s+partir\s+d(?:e|el)\s+(?:(las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)[0-5]\d)?(?:\s*(?:horas?|hs|h))?)|(la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada|medianoche))|(mediod[ií]a|amanecer|alba|atardecer|anochecer|ocaso|despuntar\s+(?:el\s+|la\s+|de\s+la\s+|del\s+)?(?:alba|d[ií]a)|clarear|aclarar|ponerse\s+(?:el\s+sol|del\s+sol)))""")

    /**
     * Normaliza el conector de inicio de franja "desde" + anclaje temporal de HORA
     * ("desde las 3 de la tarde", "desde la mañana", "desde el mediodía/amanecer/
     * atardecer") a la forma canónica ("a las"/"a la"/"al") ANTES del fold de
     * [approximateTimePatterns] y del resto del flujo, para reutilizar TODO el mecanismo
     * de hora/parte-del-día existente (resolución + limpieza) sin dejar "desde" como
     * residuo en el título. Simétrico de [aPartirDeRewriter] ("a partir de") y de la
     * familia "antes de"/"después de"/"hasta". Sólo anclajes de HORA (no fechas de
     * calendario "desde el viernes"/"desde mañana": "desde mañana" ya la resuelve
     * [datePatterns] como fecha, y "desde el viernes" es un rango de FECHA, no de
     * hora). Los grupos 1/2/3 son las tres formas de anclaje ("las N", "la X", "X");
     * se emite el conector según cuál case.
     *
     * IMPORTANTE: se aplica DESPUÉS de [desdeRangeNormalizerRewriter] (rangos
     * "desde las H1 hasta las H2") para no desarmarlos. Asimismo el lookahead exige
     * anclaje de HORA, no fecha de calendario, así "desde el proyecto"/"desde las 3 cajas"
     * (tema/cantidad) no se tocan.
     */
    private val desdeRewriter =
        Regex("""(?i)\bdesde\s+(?:el\s+)?(?:(las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)[0-5]\d)?(?:\s*(?:horas?|hs|h))?)|(la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada|medianoche))|(mediod[ií]a|amanecer|alba|atardecer|anochecer|ocaso|despuntar\s+(?:el\s+|la\s+|de\s+la\s+|del\s+)?(?:alba|d[ií]a)|clarear|aclarar|ponerse\s+(?:el\s+sol|del\s+sol)))""")

    /**
     * Normaliza rangos horarios expresados con "entre...y" o con "las" en cada extremo
     * a la forma canónica "de H1 a H2 [meridiem]" que SÍ digiere [timeRangePattern]
     * (duración M−N + hora de INICIO como dueAt, con propagación de meridiem, cruce de
     * mediodía y guard anti-cuenta). Antes estas formas cotidianas NO casaban en
     * [timeRangePattern] (que exige horas NUMÉRICAS desnudas y conector "a"/"-"): el
     * parser resolvía UNA hora (la del segundo extremo, con meridiem) PERO dejaba el
     * marco del rango como residuo del título ("reunión entre las 3 y las"), e incluso
     * misparseaba "entre 3 y 5 de la tarde" → 15:05 ("3 y 5" leído como 3:05). Ahora:
     *
     *   "entre las 3 y las 5 de la tarde" → "de 3 a 5 de la tarde" → 120 min, inicio 15:00
     *   "de las 3 a las 5 de la tarde"    → "de 3 a 5 de la tarde"
     *   "entre las 3pm y las 5pm"         → "de 3pm a 5pm"
     *
     * Sólo horas NUMÉRICAS (la forma dominante en móvil; las horas escritas en rango son
     * raras y [timeRangePattern] tampoco las admite). El rewriter es pura normalización de
     * conectores: NO exige evidencia de reloj propia porque el guard anti-cuenta y la
     * validación de rango ambiguo de [timeRangePattern]/[rangeMatch] (followedByCount,
     * hasMinutesOrMeridiem, acceptAmbiguous) ya filtran cuentas ("entre 3 y 5 cajas" →
     * "de 3 a 5 cajas" → rechazado) y rangos absurdos. Dos patrones separados (en vez de
     * uno con alternancia) para que los grupos 1-6 (H1/min1/suf1, H2/min2/suf2) ocupen los
     * MISMOS índices en ambos y el lambda de reconstrucción sea común.
     */
    private val entreRangeNormalizerRewriter =
        Regex("""(?i)\bentre\s+(?:las\s+)?(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\s+y\s+(?:las\s+)?(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\b""")

    private val deLasRangeNormalizerRewriter =
        Regex("""(?i)\bde\s+las\s+(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\s+a\s+las\s+(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\b""")

    /**
     * Normaliza rangos horarios con conector "desde ... hasta/a ..." a la forma
     * canónica "de H1 a H2 [meridiem]" que digiere [timeRangePattern] (duración
     * M−N + hora de INICIO como dueAt, propagación de meridiem, cruce de mediodía,
     * guard anti-cuenta). Es la forma cotidiana más natural de expresar un bloque
     * de tiempo ("trabajo desde las 9 hasta las 11"), pero antes NO se normalizaba:
     * [deLasRangeNormalizerRewriter] exige "de las N a las M" (conector "a" + "las"
     * en ambos extremos) y [timeRangePattern] exige "de? H1 a H2" (no admite
     * "desde" ni "hasta"), así que "desde las 9 hasta las 11" caía entera: el
     * rewriter de plazo "hasta las N"→"a las N" resolvía SOLO el extremo final
     * (11:00 como dueAt del CIERRE) y dejaba "desde las 9" como residuo del título
     * — cita mal anclada (cierre en vez de inicio), sin duración y con título
     * mutilado. Peor aún, "desde 9 hasta 11" (sin "las") perdía la cita entera
     * (dueAt=null → olvido, P1). Asimetría flagrante con "de las 9 a las 11", que
     * sí da inicio 09:00 + 120 min + título limpio. Este rewriter cierra la
     * asimetría reutilizando TODO el flujo de rango existente.
     *
     * Admite "las" opcional en cada extremo y conector de cierre "hasta" (con o sin
     * "las") o "a" (con o sin "las"), cubriendo "desde las 9 hasta las 11",
     * "desde las 9 a las 11", "desde 9 hasta 11" y "desde las 9am hasta las 11am".
     * Los grupos 1-6 (H1/min1/suf1, H2/min2/suf2) ocupan los MISMOS índices que
     * [entreRangeNormalizerRewriter]/[deLasRangeNormalizerRewriter], así que
     * [rebuildRange] los reconstruye a "de H1 a H2" sin código propio. Sólo horas
     * NUMÉRICAS (igual que los otros dos rewriters): la validación anti-cuenta y
     * de rango ambiguo la sigue haciendo [timeRangePattern]/[rangeMatch]
     * (followedByCount, hasMinutesOrMeridiem, acceptAmbiguous), así que
     * "desde 3 hasta 5 cajas" → "de 3 a 5 cajas" → rechazado.
     *
     * Se aplica ANTES que [deLasRangeNormalizerRewriter] y que el rewriter de plazo
     * "hasta las N"→"a las N": así el "hasta" del rango se consume aquí entero y no
     * se desarma en un "a las N" suelto que dejaría "desde las 9 a" como residuo.
     */
    private val desdeRangeNormalizerRewriter =
        Regex("""(?i)\bdesde\s+(?:las\s+)?(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\s+(?:hasta\s+(?:las\s+)?|a\s+(?:las\s+)?)(\d{1,2})(?:(?::|h)([0-5]\d))?\s*((?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s+(?:horas?|hs|h))?)\b""")

    /**
     * Marcadores de hora aproximada ("a eso de", "hacia", "cerca de", "alrededor de",
     * "sobre") que se normalizan a la forma canónica "a las"/"a la" reutilizando el
     * flujo de [timePatterns]. Véase el bloque de normalización en [parse].
     *
     * Se reemplaza el marcador por "a " y se conserva intacto el resto ("las 5", "la
     * una", "3 de la tarde"): así "a eso de las 5" → "a las 5", "sobre las 3 de la
     * tarde" → "a las 3 de la tarde". El lookahead exige evidencia de reloj (hora +
     * dígitos/meridiem/parte del día) para no tocar usos de tema ("sobre las ventas",
     * "informe sobre el cliente") ni cuentas ("sobre las 3 cajas"): "sobre"/"hacia"/
     * "cerca"/"alrededor" solo se normalizan cuando lo que sigue es inequívocamente
     * una hora. "a eso de" ya porta "de las"/"de la", así que su lookahead valida la
     * hora; es un adverbio temporal puro, sin uso de tema. La forma "a eso de" + parte
     * del día ("a eso de la tarde"/"a eso del mediodía") la trata
     * [aEsoDePartOfDayRewriter], que emite el conector canónico correcto.
     */
    private val approximateTimePatterns = listOf(
        // "eso de" es un adverbio temporal puro (sin uso de tema/cantidad), así que
        // admite hora en punto sin meridiem ("(a) eso de las 5"): es el caso más común.
        // La "a" inicial es OPCIONAL porque la forma cotidiana la omite ("alarma eso de
        // las 5"): mismo criterio de c.676, no se exige evidencia de reloj.
        Regex("""(?i)\b(?:a\s+)?eso\s+de\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // "casi a las/la" es adverbio de aproximación temporal puro: "casi a las 9" =
        // un poco antes de las 9. No admite lectura de cantidad ("casi a las 9 personas"
        // no es gramatical), así que NO exige evidencia de reloj (igual que "a eso de").
        // El match incluye "casi a " y se reescribe a "a " → "a las 9", reutilizando
        // [timePatterns] (resolución + limpieza del título). Antes "casi a las 9" dejaba
        // "casi" como residuo del título (cita bien fechada pero título mutilado, P2).
        Regex("""(?i)\bcasi\s+a\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // Prefijos de aproximación/intensificación antes de "a las/la N": "aproximadamente
        // a las 9", "más o menos a las 9", "justo a las 9", "exactamente a las 9", "recién a
        // las 9", "apenas a las 3", "como a las 9". Son adverbios temporales puros antes de "a las N" (no
        // admiten lectura de cantidad: "aproximadamente a las 9 personas" no es gramatical; la
        // forma de cuenta es "aproximadamente 9 personas", sin "a las"), así que NO exigen
        // evidencia de reloj (igual que "casi"). El match incluye "aproximadamente a "/"justo a "
        // y se reescribe a "a " → "a las 9", reutilizando [timePatterns] (resolución + limpieza
        // del título). Antes estos prefijos dejaban residuo ("reunión aproximadamente",
        // "cita justo", "reunión recién", "reunión apenas", "reunión como") pese a agendar la hora correcta
        // (P2 captura/título limpio, espejo del sufijo "y pico"/"más o menos"/"aproximadamente"
        // de c.393). El guard anti-cuenta (c.361) sigue activo tras la reescritura: "justo a
        // las 9 personas" → "a las 9 personas" → rechazado por followedByCountNoun; "como a las 9 personas" → "a las 9 personas" (c.424: "como" es la aproximación coloquial caribeña/latina = "alrededor de las N"; la forma de cuenta es "como 9 cajas", sin "a las", así que tampoco exige evidencia de reloj).
        Regex("""(?i)\b(?:aproximadamente|m[aá]s\s+o\s+menos|justo|exactamente|reci[eé]n|apenas|como)\s+a\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // "pasadas las N" / "pasada la una" = un poco después de las N (aproximación
        // post-hora, análoga al sufijo "y pico"/"más o menos" de c.393 y a los prefijos
        // "casi/aproximadamente a las N" de c.393/c.395). A diferencia de "casi a las"/
        // "justo a las", aquí NO hay "a" intermedio: la forma cotidiana es "pasadas las 9"
        // (femenino plural concordando con "las"). El rewriter consume "pasadas "/"pasada "
        // y reescribe a "a " → "a las 9"/"a la una", reutilizando TODO [timePatterns]
        // (resolución + limpieza del título). Antes producía dueAt=null (o título mutilado
        // si había meridiem) → cita olvidada (P1 evitar olvidos/datos (sagrados)). El
        // guard anti-cuenta (c.361) sigue activo tras la reescritura: "pasadas las 9
        // cajas" → "a las 9 cajas" → rechazado por followedByCountNoun (la lectura de
        // cuenta "cajas ya pasadas" es forzada y rara; el uso dominante es temporal).
        Regex("""(?i)\bpasadas?\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // "tipo las N"/"tipo la una": aproximación coloquial Caribe/LatAm ("cita tipo las 8"
        // ≈ "cita a las 8"). A diferencia de "hacia/sobre", no admite lectura de tema ni de
        // cantidad NUNCA: "tipo las" no es gramatical sin hora ("tipo las 8 personas" no se
        // dice; la lectura de tema "documento tipo" va sin artículo). Así que NO exige
        // evidencia de reloj (igual que "a eso de"/"casi"): el lookahead decide y, sin hora
        // válida inmediata, no toca el texto. Guard: usos de tema/categoría sin artículo
        // ("documento tipo 8", "plan tipo estrategia") quedan fuera por construcción.
        // Antes la hora se agendaba pero "tipo" quedaba como residuo del título (cita bien
        // fechada, título mutilado: captura degradada, P1/P2); ahora el rewriter consume
        // "tipo " y reutiliza TODO [timePatterns].
        Regex("""(?i)\btipo\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // "hacia/cerca de/alrededor de/sobre" admiten usos de tema ("sobre las ventas") y
        // de cantidad ("sobre las 3 cajas"), así que exigen evidencia de reloj INMEDIATA
        // tras la hora (minutos `:MM`, meridiem, parte del día, "horas/hs/h") para no
        // agendar una cuenta como cita. La hora en punto sin meridiem queda fuera por
        // ambigua. El meridiem/parte del día se admite ADYACENTE a la hora (`\s*`, no
        // `\s+`): "hacia las 10am"/"sobre las 4pm" (sin espacio) es la forma dominante en
        // móvil. Antes el lookahead exigía `\s+` y el marcador NO se reescribía →
        // `timePatterns` (que SÍ usa `\s*`) resolvía la hora pero dejaba "hacia las"/
        // "sobre las" como residuo en el título (cita bien fechada, título mutilado:
        // contenido capturado degradado, P1). `\s*` equivale a `timePatterns` (líneas
        // 823/841): simetría. El sufijo suelto "h" (forma europea "10 h"/"10h") se admite
        // con `\b` final para no colisionar con palabras que empiezan por h ("hacia las
        // 10 habitaciones"/"10 horario" no se falsifican como cita); idéntico a
        // [paraTimeIntroPattern]. Antes faltaba `|h` aquí (asimetría con timePatterns y
        // paraTime) y "hacia las 10h"/"sobre las 4h" caían a `dueAt=null` + residuo.
        Regex("""(?i)\b(?:hacia|cerca\s+de|alrededor\s+de)\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d|\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s*(?:horas?|hs|h)\b)|la\s+una(?:\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)))"""),
        Regex("""(?i)\bsobre\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d|\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s*(?:horas?|hs|h)\b)|la\s+una(?:\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)))""")
    )

    /**
     * "tipo N" DESNUDO (sin artículo) con evidencia de reloj inmediata: aproximación
     * coloquial ("comida tipo 2 de la tarde" ≈ "comida sobre las 2 de la tarde"). El
     * marcador aproximado "tipo las N" de [approximateTimePatterns] (c.670) exige el
     * artículo precisamente porque la forma de categoría ("documento tipo 8") va SIN
     * artículo; pero la forma desnuda con evidencia de reloj ("tipo 2 de la tarde",
     * "tipo 3 pm", "tipo 10:30") la resolvía el reloj autónomo y "tipo" sobrevivía
     * como residuo en el título (cita bien fechada, título mutilado: captura
     * degradada; sonda c.852, lateral (c)). Se consume el marcador (se elimina, NO se
     * sustituye por "a ": "a 2 de la tarde" no es forma canónica y dejaba "a" como
     * residuo) y el resto lo reutiliza TODO el flujo del reloj autónomo (resolución +
     * limpieza), que ya anclaba esa hora. A diferencia de "tipo las N" (el artículo la
     * hace inequívoca), la forma desnuda es ambigua con la de categoría, así que exige
     * evidencia de reloj INMEDIATA tras la hora (misma doctrina que "hacia/sobre":
     * minutos `:MM`, meridiem, parte del día o "horas/hs/h"); la hora en punto sin
     * evidencia ("reunión tipo 3") y los usos de categoría ("documento tipo 8", "plan
     * tipo estrategia", "documento tipo 8 personas", "mesa tipo 8 de comedor") NO se
     * tocan (sin evidencia el lookahead no casa).
     */
    // c.871: la fracción "y media/cuarto/veinte/..." puede intercalarse entre la hora y
    // la parte del día ("tipo 5 y media de la tarde", "tipo cinco y media de la tarde"):
    // el reloj autónomo resuelve esa forma, así que el puente `(?:\s+$CLOCK_FRACTION_Y)?`
    // la admite SOLO como tránsito hacia la parte del día. Fracción sin parte del día
    // ("tipo 2 y media") o con meridiem ("tipo 3 y cuarto pm") no las resuelve el reloj
    // autónomo: consumir "tipo" mutilaría el título sin agendar → quedan fuera.
    private val bareTipoTimePattern =
        Regex("""(?i)\btipo\s+(?=(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d|\s*(?:a\.?\s*m\.?|p\.?\s*m\.?)|(?:\s+$CLOCK_FRACTION_Y)?\s*(?:de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s*(?:horas?|hs|h)\b))""")

    /**
     * Introductor de hora directo "para las/la", alternativa a "a las/la" ("reunión para
     * las 9h30", "te veo para las 9 pm", "entrega para la una del mediodía"). A diferencia
     * de [approximateTimePatterns] (marcadores de hora *aproximada*), "para" porta la hora
     * exacta; pero comparte el mismo mecanismo: se reescribe a "a las/la" reutilizando TODO
     * el flujo de [timePatterns] (resolución AM/PM, fracción, limpieza del título).
     *
     * "para" es preposición versátil —destinatario ("regalos para las niñas"), cantidad
     * ("para las 9 personas"), propósito ("reunión para las ventas")—, así que el lookahead
     * exige evidencia de reloj INMEDIATA tras la hora, como "sobre/hacia": minutos compactos
     * (`:MM`/`hMM`), meridiem (am/pm), parte del día ("de la noche"), fracción ("y media"/
     * "menos cuarto") o sufijo de unidad ("horas/hs/h"). La hora en punto sin evidencia
     * queda fuera, igual que "sobre las 9": "para las 9" es ambiguo con "mesa para las 9
     * [personas]" y no se falsifica como cita. El `\b` tras la "h" suelta protege contra
     * palabras que empiezan por h ("hamburguesas", "hoy"): "para las 9 hamburguesas" no casa.
     *
     * Antes estas citas caían a `dueAt=null` (el usuario olvidaba la cita) o, cuando el reloj
     * autónomo (`:MM`/meridiem) casaba por separado, dejaban "para las" como residuo del
     * título (cita bien fechada pero título mutilado). Simétrico de la familia "a las Nh"
     * (c.235/239/254) y de la normalización de marcadores aproximados.
     */
    // [paraTimeIntroPattern]: admite el meridiem/parte del día ADYACENTE a la hora
    // (`\s*`, no `\s+`): "para las 3pm"/"para las 9am" (sin espacio, forma dominante en
    // móvil) antes dejaban "para las" como residuo en el título (cita bien fechada, título
    // mutilado: contenido capturado degradado, P1). `\s*` equivale a timePatterns y a
    // approximateTimePatterns (c.359): simetría.
    private val paraTimeIntroPattern =
        Regex("""(?i)\bpara\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)[0-5]\d|\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS)|\s*(?:horas?|hs|h)\b)|la\s+una(?:(?::|h)[0-5]\d|\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS)))""")
    /**
     * Fracción negativa ANTEPUESTA caribeña/latinoamericana: "cuarto para las 8",
     * "cinco para las 9", "diez para las 3" (forma regional de "a las 8 menos cuarto").
     * Grupo 1 = palabra de fracción (canonizada por [CLOCK_FRACTION_ALT], ordenada para
     * que "tres cuartos"/"veinticinco" ganen a prefijos); grupos 2/3/4 = "las N" /
     * "la una" / "N" (uno de los tres). El lookahead implícito en la captura de la hora
     * exige hora válida inmediatamente tras "para las/la", igual que [paraTimeIntroPattern],
     * para no tocar "cinco para las niñas" (destinatario) ni "diez para las 10 personas"
     * (cuenta). La reescritura a "a las N menos <fracción>" reutiliza TODO el flujo de
     * hora explícita (resolución AM/PM, wrap 24 h, limpieza del título). Simétrico del
     * "menos cuarto" pospuesto (ciclo 114) y de paraTimeIntroPattern. Véase su uso en
     * [parse] (antes de paraTimeIntroPattern).
     */
    private val prefixedNegativeFractionPattern =
        Regex("""(?i)\b($CLOCK_FRACTION_ALT)\s+para\s+(?:las\s+([01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)|la\s+(una)|([01]?\d|2[0-4]))""")
    /**
     * Continuador seguro tras la hora en [prefixedNegativeFractionPattern]: conjuncion,
     * puntuacion, palabra temporal, fin de cadena O evidencia de reloj (meridiem/parte
     * del dia). Simetrico del lookahead de [paraTimeIntroPattern] (que exige evidencia
     * de reloj) pero aqui la fraccion va antes, asi que el meridiem "pm"/"de la tarde"
     * aparece DESPUES de la hora y debe aceptarse como continuador legitimo (no es
     * sustantivo de cuenta). Rechaza "diez para las 10 cajas" (sustantivo tras hora
     * sin evidencia de reloj).
     */
    private val prefixedFractionFollowerPattern =
        Regex("""(?i)^\s*(?:,|\.|;|:|!|\?|y\b|o\b|con\b|de\b|del\b|en\b|para\b|hasta\b|desde\b|luego\b|después\b|despues\b|pero\b|porque\b|por\b|sin\b|sobre\b|a\b|al\b|el\b|la\b|los\b|las\b|un\b|una\b|mañana\b|manana\b|hoy\b|ayer\b|anteayer\b|lunes\b|martes\b|miércoles\b|miercoles\b|jueves\b|viernes\b|sábado\b|sabado\b|domingo\b|a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+manana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a|horas?|hs|h\b|$)""")
    /**
     * Cantidad del recordatorio: dígitos o número escrito en español (simétrico con
     * la fecha relativa "en dos horas"). Antes solo se aceptaban dígitos, así que
     * "recuérdame una hora antes" / "dos horas antes" / "treinta minutos antes"
     * caían a `reminderOffsetMinutes=null` y la frase quedaba como residuo en el
     * título → el recordatorio nunca se programaba (el usuario olvidaba la cita).
     */
    private val writtenAmountPattern =
        """\d{1,3}|un\s+par\s+de|$writtenNumberGroup"""

    private val reminderPatterns = listOf(
        // Forma sustantivada "con aviso/alerta/recordatorio [de] N unidad [antes]":
        // cotidiana en español ("reunión con aviso 15 min", "cita con recordatorio de 20
        // minutos antes"). Antes NO se reconocía: el offset caía a null (recordatorio
        // nunca programado → la cita se olvidaba, P1) y "con aviso"/"con alerta"/"con
        // recordatorio" sobrevivía como residuo en el título. Va PRIMERO para que el
        // match (que incluye "con X") gane sobre los patrones de verbos ("N unidad antes")
        // en la selección del offset (más a la izquierda) y en el blanqueo del residuo.
        // Acepta "de" opcional ("con aviso de 15 min") y "antes"/"de anticipación" opcional
        // (la mera mención de "aviso"/"alerta"/"recordatorio" ya es intención de aviso).
        Regex("""(?i)\bcon\s+(?:aviso|alerta|recordatorio)(?:\s+de)?\s+($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)(?:\s+(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto))?\b"""),
        // "con N unidad antes|de anticipación|de adelanto" (sin sustantivo aviso, pero con
        // "con" como conector de plazo de aviso): "cita con 15 min antes", "llamar con 10 min
        // de anticipación", "reunión con 15 minutos de adelanto". Antes el patrón "N unidad
        // antes" (más abajo) capturaba el offset PERO dejaba "con" como residuo huérfano en el
        // título ("cita con"), y "con N unidad de anticipación/adelanto" ni siquiera casaba →
        // caía como duración (falsa) y "con … de anticipación" sobrevivía como residuo (P1:
        // recordatorio perdido + título mutilado). Exige sufijo inequívoco (antes/anticipación/
        // adelanto) para no robar una duración real ("reunión con 30 min" sin sufijo = duración).
        Regex("""(?i)\bcon\s+($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s+(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto)\b"""),
        // Orden invertido "con anticipación/adelanto de N unidad" (más formal:
        // sustantivo-anticipación ANTES de la cantidad): "reunión con anticipación
        // de 15 min", "cita con adelanto de 20 minutos". Antes NO casaba (el patrón
        // anterior exige "con N unidad sufijo", no "con sufijo de N unidad") → caía
        // como duración falsa + residuo "con anticipación" en el título (P1:
        // recordatorio perdido → cita olvidada). Mismo fix que c.401, forma invertida.
        Regex("""(?i)\bcon\s+(?:anticipaci[oó]n|adelanto)\s+de\s+($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\b"""),
        // Variante sin "con": "aviso 15 min" / "alerta 10 min" / "recordatorio 20 min"
        // (sustantivo suelto, sin verbo imperativo). "recordatorio N unidad" ya lo cubre
        // el patrón de verbos de abajo, pero "aviso"/"alerta" no. Va tras "con X" para no
        // robar el "con" del patrón anterior.
        Regex("""(?i)\b(?:aviso|alerta|recordatorio)(?:\s+de)?\s+($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)(?:\s+(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto))?\b"""),
        Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        // "N min/hora antes": debe aceptar las mismas abreviaturas que la duración
        // (min, hora) para que "30 min antes" sea recordatorio y no caiga como duración.
        Regex("""(?i)\b($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s+antes\b"""),
        // Fracciones sin dígitos como recordatorio: "media hora antes",
        // "(un) cuarto de hora antes", "recuérdame media hora de anticipación".
        // Requiere contexto de recordatorio ("antes"/"anticipación"/verbo) para no
        // robar una duración real ("reunión media hora" sin "antes" sigue siendo
        // duración). Antes "media hora antes" era robado por la duración (30 min
        // falsos) y el recordatorio quedaba en null → la cita se olvidaba.
        Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?($fractionalHourGroup)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        Regex("""(?i)\b($fractionalHourGroup)\s+antes\b""")
    )
    /**
     * Verbo de recordatorio sin cantidad explícita: "recuérdame llamar a mamá mañana",
     * "avísame pagar la luz el viernes", "no dejes que olvide...", "recordarme llamar
     * al dentista mañana". El usuario pide un recordatorio pero no dice cuánto antes;
     * antes el verbo quedaba como residuo en el título y NO se programaba ningún
     * recordatorio (la cita se olvidaba aunque el usuario lo hubiera pedido
     * expresamente). Aquí se detecta la intención para:
     * (a) limpiar el verbo del título y (b) aplicar un offset de respaldo (30 min)
     * cuando hay fecha límite — sin dueAt no se puede programar reminderAt, así que no
     * se falsifica nada. Simétrico con `UniversalCaptureEngine.reminderSignal`.
     *
     * c.403: el sustantivo `recordatorio` es ambiguo — al inicio de frase
     * ("recordatorio cada 2 días", "recordatorio: pagar la luz") es etiqueta de
     * aviso (petición), PERO precedido de artículo/posesivo es contenido real
     * ("leer el recordatorio del profesor", "borrar mi recordatorio viejo") y NO debe
     * blanquearse (lo mutilaba a "leer el del profesor"). El negative lookbehind
     * permite `recordatorio` solo al inicio o tras puntuación/conector, no tras
     * determinante. Los verbos imperativos (recuérdame/avísame/...) son inequívocos.
     *
     * c.447: los INFINITIVOS con clítico de 1ª/2ª persona ("recordarme",
     * "avisarme", "notificarme", "acordarme de") son la forma cotidiana de pedir un
     * recordatorio en infinitivo ("recordarme llamar al dentista mañana a las 9"),
     * simétrica al imperativo "recuérdame". Antes NO se reconocían: el verbo
     * sobrevivía como residuo en el título ("recordarme llamar al dentista") y el
     * recordatorio NUNCA se programaba (la cita se olvidaba pese a pedirse
     * expresamente). El clítico `-me`/`-te` los vuelve inequívocos como petición de
     * aviso (no son sustantivos ni verbos de contenido: "recordarme" no significa
     * "acordarse de mí" salvo tras preposición, caso que el lookbehind de
     * `recordatorio` no aplica aquí; el infinitivo con clítico de objeto indirecto
     * "recordarme X" = "que se me recuerde X", intención de aviso pura).
     *
     * c.471: los imperativos "mándame"/"envíame" + sustantivo de aviso
     * ("mándame un recordatorio", "envíame una alerta") son peticiones explícitas de
     * aviso tan cotidianas como "recuérdame", PERO a diferencia de "recuérdame" estos
     * verbos son de ACCIÓN por sí solos ("envíame un correo", "mándame el documento").
     * Por eso solo cuentan como aviso cuando van seguidos de un sustantivo de aviso
     * (recordatorio/alerta/aviso/notificación), exigido con un `(?=...)` que NO consume
     * el sustantivo (este queda como título honesto, igual que en "recuérdame un
     * recordatorio"). Antes NO se reconocían: el verbo sobrevivía como residuo del
     * título y reminderOffset=null (el recordatorio NUNCA se programaba pese a
     * pedirse expresamente → olvido).
     *
     * c.476 extiende el mismo patrón a "ponme"/"dame" ("ponme un aviso para la reunión
     * del viernes", "dame un recordatorio de pagar la luz"): tan cotidianos como
     * "mándame/envíame" pero igualmente verbos de acción por sí solos ("dame el
     * documento", "ponme el libro"), por lo que solo cuentan como aviso con el
     * sustantivo vía lookahead. Sin él, el recordatorio nunca se programaba pese a
     * pedirse expresamente con fecha → olvido silencioso.
     *
     * c.678: el ENCUADRE REFLEXIVO de recordatorio —"que no se me olvide X",
     * "que no se me pase X", "no dejes que se me olvide X"— es junto a
     * "recuérdame X" la petición de aviso más cotidiana en español, y NO se
     * reconocía: quedaba íntegro como residuo del título (título sucio desde la
     * captura, P1) y el recordatorio nunca se programaba pese a pedirse
     * expresamente (olvido). Comportamiento idéntico al imperativo: se borra el
     * encuadre del título (el "que" inicial subordinador lo limpia el paso
     * genérico, ver el fold del título) y, con fecha límite, se aplica el mismo
     * offset de respaldo (30 min, u 0 si el encuadre era el único contenido).
     * El "olvides?" existente se amplía del único reflexivo "se te" al conjunto
     * "se me/te/le/les/nos/os" y también dentro de "no dejes que se me olvide".
     * "pasen?" sólo se admite en forma reflexiva ("que no se me pase/pasen") —
     * sin "se"+pronombre "pasar" es verbo de acción ("haz que pasen la lista").
     * El pretérito "se me olvidó" (contenido: confiesa un olvido pasado) queda
     * intacto: la forma "olvidó" no casa en "olvides?". El modismo literal
     * "no vaya a ser que se me pase/olvide X" es inequívoco (avisar, nunca
     * contenido) y también se borra del título; el subjuntivo suelto "que se
     * me pase" SÍ es ambiguo ("espero que se me pase el dolor" = contenido)
     * y NO se toca: sólo entra con el prefijo inequívoco "no vaya a ser que".
     */
    private val bareReminderVerbPattern =
        Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|notif[ií]came|recordarme|avisarme|notificarme|acordarme(?:\s+de\b)?|no\s+dejes\s+que\s+(?:se\s+(?:me|te|le|les|nos|os)\s+)?olvide|no\s+(?:se\s+(?:te|me|le|les|nos|os)\s+|te\s+|me\s+|le\s+)?olvides?(?:\s+de\b)?(?:\s+que\b)?|no\s+se\s+(?:me|te|le|les|nos|os)\s+pasen?|no\s+vaya\s+a\s+ser\s+que\s+se\s+(?:me|te|le|les|nos|os)\s+(?:olvides?|pasen?)|acu[eé]rdate(?:\s+de\b)?|recuerda|(?:m[aá]ndame|env[ií]ame|p[oó]nme|dame)(?=\s+(?:un(?:a|os|as)?\s+)?(?:recordatorio|alerta|aviso|notificaci[oó]n)\b)|(?<!(?:el|la|los|las|un|una|unos|unas|mi|mis|tu|tus|su|sus|nuestros?|nuestras?|estes?|estas?|esos?|esas?|aquella?)\s)recordatorio)\b""")
    private const val BARE_REMINDER_DEFAULT_OFFSET_MINUTES = 30
    private val durationPatterns = listOf(
        Regex("""(?i)\((\d{1,3}(?:[.,]\d+)?)\s*(minutos?|min|horas?|hora)\)"""),
        Regex("""(?i)\b(?:durante|por)\s+(\d{1,3}(?:[.,]\d+)?)\s*(minutos?|min|horas?|hora)\b"""),
        // Keyword "duración (de/:) N [unidad]": la forma más natural de declarar la
        // duración de una reunión/cita en español. Antes la palabra "duración" NO se
        // reconocía como señal de duración: con unidad ("duración 30 minutos") el
        // patrón "N minutos" casaba y daba durationMinutes=30, pero "duración" quedaba
        // como residuo en el título; sin unidad ("duración 45") no casaba nada →
        // durationMinutes=null y la frase entera se conservaba. La unidad es opcional:
        // si falta se asume minutos (convención del proyecto). Va antes que los
        // patrones "N unidad" para que, al quedar más a la izquierda, [durationMatch]
        // la elija y consuma la frase completa ("duración" incluida).
        Regex("""(?i)\bduraci[oó]n\s*(?::|de)?\s*(\d{1,3}(?:[.,]\d+)?)\s*(minutos?|min|horas?|hora)?\b"""),
        Regex("""(?i)\b(\d{1,3}(?:[.,]\d+)?)\s*(minutos?|min)\b"""),
        Regex("""(?i)\b(\d{1,3}(?:[.,]\d+)?)\s*(horas?)\b"""),
        // Compacto "Nh" (p. ej. "Trabajar 2h", "Estudiar 1h"). El \b final evita
        // casar "2horas" (h seguida de 'o' no es límite de palabra), así no roba
        // ni deja residuo frente al patrón completo "horas?".
        Regex("""(?i)\b(\d{1,3}(?:[.,]\d+)?)\s*(h)\b""")
    )

    /**
     * Duraciones con número escrito (sin dígitos) y unidad de tiempo: "dos horas",
     * "una hora", "treinta minutos", "un par de horas". Antes solo se aceptaban
     * dígitos y las fracciones "media hora"/"cuarto de hora", así que las cantidades
     * escritas caían a `durationMinutes=null`: el planificador las trataba como
     * [TaskRules.MIN_PLAN_MINUTES] (10 min) y "What Now"/el resumen del día subestimaban
     * el trabajo real. Simétrico con los recordatorios y las fechas relativas, que SÍ
     * aceptan números escritos. Se limita a minutos/horas (la duración se acota a
     * ≤24 h, así que "dos días" no es una duración significativa y se deja fuera).
     *
     * Reusa [writtenAmountPattern] (misma lista de palabras) y [parseWrittenNumber]
     * como fuente única de los literales en español.
     */
    private val writtenDurationPattern =
        Regex("""(?i)\b($writtenAmountPattern)\s*(minutos?|min|horas?|hora)\b""")

    /**
     * Duraciones fraccionarias comunes en español sin dígitos: "media hora" (30 min) y
     * "(un) cuarto de hora" (15 min). Los patrones de dígitos no las capturan, así que
     * quedaban como residuo en el título y `durationMinutes` era null. "cuarto" requiere
     * "hora" después para no casar "cuarto" = habitación ("limpiar el cuarto").
     *
     * Admite el artículo femenino "una media hora" vía [fractionalHourGroup] (c.385).
     */
    private val fractionalDurationPattern =
        Regex("""(?i)\b($fractionalHourGroup)\b""")

    /**
     * Duración fraccionaria COMPUESTA: "2 horas y media" (150 min), "1 hora y media"
     * (90), "3 horas y cuarto" (195), "dos horas y media". Antes solo se capturaba la
     * parte entera: el patrón "N horas" robaba "2 horas" (→ 120) y dejaba "y media"
     * como residuo en el título ("Estudiar y media"), subestimando la duración real que
     * usan el planificador, la carga del día y "What Now". Simétrico de
     * [compoundFractionalRelativePattern] ("en una hora y media"), que sí resolvía la
     * fracción entera para fechas relativas. La cantidad admite dígitos o número
     * escrito (vía [writtenAmountPattern]); se procesa con los mismos guards que la
     * duración numérica para no robar "a las 2 ... horas" (hora de un evento) ni "en 2
     * horas y media" (fecha relativa, ya consumida antes en el flujo).
     *
     * Admite también cuartos en plural como fracción, simétrico de
     * [compoundFractionalRelativePattern]: "dos horas y tres cuartos" (120+45=165),
     * "una hora y dos cuartos" (60+30=90). Antes el plural "tres cuartos" no casaba aquí
     * (el grupo solo aceptaba "media|cuarto") y caía al patrón "N horas", que robaba
     * solo la parte entera (→ 120) y dejaba "y tres cuartos" como residuo en el título
     * ("Estudiar y tres cuartos"), con duración subestimada. amount×60 + (45 si "tres
     * cuartos" | 30 si "dos cuartos" o "media" | 15 si "cuarto").
     *
     * Admite el prefijo opcional "durante"/"por" (no capturante): sin él, en
     * "durante 1 hora y media" el [durationPatterns] simple casaba "durante 1 hora"
     * (desde la posición 0) y este patrón solo casaba "1 hora y media" (desde la
     * posición 8), de modo que el tie-break compound.range.first <= durationMatch.range.first
     * (8 <= 0) fallaba y ganaba el simple → 60 min en vez de 90, con "y media" como
     * residuo en el título. Con el prefijo, este patrón casa desde la misma posición 0
     * y la rama compuesta prevalece, capturando la fracción completa. (c.501)
     */
    private val compoundFractionalDurationPattern =
        Regex("""(?i)\b(?:durante\s+|por\s+)?($writtenAmountPattern)\s*horas?\s+y\s+(tres\s+cuartos|dos\s+cuartos|media|un\s+cuarto|cuarto)\b""")

    /**
     * Duración multi-cuarto SIN número de horas, simétrica de
     * [multiQuarterRelativePattern] ("en tres cuartos de hora"): "tres cuartos de hora"
     * (45 min), "dos cuartos de hora" (30). Cada cuarto = 15 min. Antes estas formas no
     * casaban ningún patrón de duración (la cantidad escrita no es "horas"/"minutos" y
     * [fractionalDurationPattern] solo admite "media hora"/"(un) cuarto de hora" en
     * singular), así que `durationMinutes` era null y la frase entera ("tres cuartos de
     * hora") quedaba como residuo en el título — captura degradada y planificador/What
     * Now sin la duración real.
     *
     * EXIGE "de hora" (no opcional) para desambiguar de "cuartos" = habitaciones: a
     * diferencia de [multiQuarterRelativePattern], que se protege con el prefijo
     * "en/dentro de" (un punto en el tiempo no puede ser "en tres habitaciones"), la
     * duración NO lleva prefijo, así que el único desambiguador fiable es "de hora"
     * (mismo criterio que [fractionalDurationPattern], que también exige "hora" para no
     * casar "cuarto" = habitación). Sin "de hora" ("los tres cuartos de la casa") NO es
     * duración y se deja intacta. La forma con prefijo "en/dentro de ..." ya la consume
     * [multiQuarterRelativePattern] antes en el flujo. Admite el sufijo "+ y cuarto"
     * (un cuarto extra): "tres cuartos de hora y cuarto" = 4 cuartos = 60. amount×15 +
     * (15 si "y cuarto").
     */
    private val multiQuarterDurationPattern =
        Regex("""(?i)\b($writtenAmountPattern|\d{1,3})\s+cuartos\s+de\s+hora(?:\s+y\s+cuarto)?\b""")

    /**
     * Rango horario "de H1[MM] [meridiem] [horas] a H2[MM] [meridiem] [horas]" (citas, clases,
     * reuniones con ventana). Implica duración = (fin − inicio) en minutos y se elimina
     * del título. Cada extremo admite minutos (`9:30`), meridiem (`9am`, `9 de la tarde`),
     * sufijo de unidad (`9h`/`9hs`/`9 horas`) y la forma en punto (`9`).
     *
     * El sufijo de unidad por extremo (c.247) es simétrico del reloj "HH:MMh" (c.235) y del
     * "a las Nh" (c.245): antes SOLO se admitía la unidad FINAL ("de 9 a 11 horas"). Formas
     * cotidianas como "9h a 11h", "9hs a 11hs" o "9 horas a 11 horas" rompían el patrón
     * (el sufijo inicial no es meridiem ni separador) y, peor, "9 horas" era robado como
     * duración falsa (540 min) con el rango perdido → dato falseado. Ahora cada extremo
     * lleva una unidad NO capturante; la presencia de unidad se detecta escaneando el
     * emparejamiento completo con `rangeUnitToken` (límites de palabra, para no confundir
     * la "h" de "hola"/"hoy") en vez de un grupo fijo, así basta una unidad en cualquier
     * extremo como evidencia de reloj.
     *
     * Minutos compactos "NhMM" por extremo (c.248): la forma `11h30` (unidad ENTRE hora y
     * minutos, sin dos puntos) no casaba: el extremo final fallaba, el rango se perdía y el
     * extremo inicial ("9h") era robado como duración falsa (540 min) con residuo "a 11h30"
     * en el título → dato falseado. Ahora los minutos de cada extremo admiten `:` O `h`
     * como separador (`(?:(?::|h)([0-5]\d))?`), simétrico del reloj "HH:MMh" (c.235). Sigue
     * siendo UN solo grupo de minutos por extremo (índices 2/5 intactos): el `h` de la forma
     * en punto/unitaria ("9h") NO se consume aquí porque no le siguen dos dígitos, y cae al
     * sufijo de unidad NO capturante de después. Así "9h a 11h30" casa completo, la duración
     * es real (150 min) y el título queda limpio.
     *
     * Para no falsear datos (p. ej. "comprar de 2 a 5 entradas") solo se acepta cuando hay
     * evidencia de horario: unidad en algún extremo ("horas"/"hs"/"h"), minutos en algún
     * extremo (`:30` o `h30`, inequívocos de reloj), meridiem explícito, o alguna hora >= 13
     * (24h). Sin esa evidencia, el rango en punto y ambiguo (<13) requiere además que no le
     * siga un sustantivo de cantidad (ver `followedByCount`). No fija hora de inicio (ambigua
     * sin contexto); solo la duración.
     *
     * Grupo 1/2/3 = hora/minuto/meridiem del INICIO; 4/5/6 = fin. Las unidades por extremo
     * son NO capturantes (no alteran los índices 1-6 que consumen [rangeMatch] y
     * [rangeStartTime]); la evidencia de unidad se obtiene vía [rangeUnitToken].
     */
    private val timeRangePattern =
        Regex("""(?i)\b(?:de\s+)?(\d{1,2})(?:(?::|h)([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s*(?:horas?|hs|h))?\s*(?:a|-)\s*(\d{1,2})(?:(?::|h)([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s*(?:horas?|hs|h))?\b""")

    /** Token de unidad horaria ("h"/"hs"/"hora"/"horas") acotado por límites de palabra. */
    private val rangeUnitToken = Regex("""(?i)(?:\bhoras?\b|\bhs\b|\bh\b)""")

    /**
     * "urgente"/"importante" como palabra INICIAL (sin prefijo !/#): detección de
     * prioridad en captura libre ("urgente enviar documento", "importante llamar
     * al cliente"). Simétrica con [trailingPriorityPattern]: ambos extremos de la
     * frase activan la prioridad. No se casa a mitad de frase para evitar falsos
     * positivos ("revisar si es urgente el documento"). El anclaje `^` garantiza
     * que sea palabra inicial real (no "no es importante" ni contenido interior).
     */
    private val leadingPriorityPattern = Regex("""(?i)^(urgente|importante)\b""")

    /**
     * "urgente"/"importante" como palabra FINAL (con puntuación opcional): el usuario añade la
     * prioridad como sufijo en texto libre ("Llamar mamá urgente"). Más honesto que casar a
     * mitad de frase (evita "no es urgente el documento" a mitad, que no sería palabra final).
     */
    private val trailingPriorityPattern = Regex("""(?i)\b(urgente|importante)\b\s*[.!?]?$""")

    /** Formas copulativas negadas que neutralizan un "urgente"/"importante" final real. */
    private val negatedPriorityPattern = Regex("""(?i)\bno\s+(?:es|era|fue|parece|ser[áa])\s+(?:lo\s+)?(?:urgente|importante)\b\s*[.!?]?$""")

    /**
     * Partes del día: "esta mañana/tarde/noche/madrugada". Implican fecha=hoy + hora
     * canónica. "madrugada" se resuelve a 04:00 (misma hora canónica que en
     * [standalonePartOfDayTimes] y [compactDayPartOfDayTimes]). Antes "esta madrugada"
     * no casaba este patrón (sólo mañana/tarde/noche): caía a `dueAt=null` y la frase
     * entera quedaba como residuo en el título — una cita capturada de madrugada
     * (p.ej. "reunión esta madrugada") se perdía sin vencimiento ni recordatorio (P1,
     * tarea olvidada). "madrugada" es inequívoca como parte del día (no hay marcador de
     * día "madrugada" con el que colisionar, a diferencia de "mañana"), por lo que es
     * seguro incluirla aquí simétricamente con mañana/tarde/noche, igual que el
     * intensificador opcional "misma" ("esta misma tarde/noche/mañana/madrugada"),
     * paridad con "esta misma semana/este mismo mes" (thisWeekPattern/softMonthPattern).
     */
    private val partOfDayPattern = Regex("""(?i)\besta\s+(?:misma\s+)?(ma[nñ]ana|tarde|noche|madrugada)\b""")
    private val partOfDayTimes = mapOf(
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0),
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0)
    )

    /**
     * Parte del día suelta: "a la tarde/noche/madrugada", "de la tarde/noche/madrugada",
     * "por la tarde/noche/mañana". NO fuerza fecha (solo hora del día sobre la fecha
     * parseada). Sirve como contexto PM para horas sin meridiem ("mañana a la tarde
     * a las 4" → 16:00) y como hora canónica de respaldo ("jugar tenis de la tarde"
     * → 15:00). "mañana/madrugada" son AM.
     *
     * "por la" se incluye porque "mañana por la tarde/noche/mañana" es la forma natural
     * más común de combinar día relativo + parte del día en español; antes el conector
     * "por la" no se reconocía y la frase quedaba como residuo en el título, con hora
     * 09:00 en vez de la canónica de la parte del día.
     *
     * "en la" se incluye (forma caribeña/hispanoamericana: "hoy en la tarde",
     * "mañana en la noche") propia de la zona de la app (America/Santo_Domingo). Antes
     * "en la tarde" no se reconocía: la hora caía a 09:00 y "en la tarde" quedaba como
     * residuo en el título.
     *
     * "de madrugada"/"de noche"/"de tarde" (sin "la") son adverbios temporales muy
     * comunes en español ("salir de madrugada", "trabajo de noche"). Antes no casaban:
     * la tarea quedaba sin hora (dueAt=null) y la frase quedaba como residuo en el
     * título. Se añade el conector "de" suelto para estas partes; no aplica a
     * "mañana" porque "de mañana" colisionaría con la fecha relativa ("mañana").
     */
    // Intensificador "justo" (c.401): "justo a la tarde/noche/mañana", "justo de
    // madrugada" son formas cotidianas frecuentes. Antes la hora se resolvía bien
    // (vía el conector "a la"/"de") PERO "justo" sobrevivía como residuo en el título.
    // Simétrico de c.391 ("justo" antes de comida/sueño) y de mediodía/medianoche
    // (c.401). Adverbio temporal puro, sin ambigüedad.
    //
    // c.550: conector "durante la" para tarde/noche/madrugada ("trabajar durante la
    // tarde"), tan natural como "trabajar a la tarde" (que SÍ funcionaba). Antes estas
    // frases caían a dueAt=null (tarea olvidada) y la frase entera quedaba como residuo
    // en el título. Se EXCLUYE "mañana" de este conector (simétrico al conector "de",
    // que también la excluye): "durante la mañana" es ambigua (parte del día vs. fecha
    // "mañana") y ya resuelve vía la fecha relativa; no se altera su comportamiento.
    // c.672: perífrasis caribeña/latam "entrando/entrada la tarde/noche/…" (al caer la
    // tarde/noche) se une a la familia de conectores "a la|de la|por la|en la"; antes
    // caía a dueAt=null con el residuo íntegro en el título (paridad con "en la").
    // c.928: conector "para la" ("lo necesito para la mañana", "para la tarde revisar
    // el informe") — forma cotidiana del vencimiento intradía, hermana del "para las 3"
    // de hora explícita (l.2109). Antes caía a dueAt=null (tarea olvidada) y, con
    // "mañana", con doble daño: el borrado del token-fecha consumía "mañana" y dejaba
    // el residuo mutilado "para la" en el título. Doctrina SIMÉTRICA a los conectores
    // hermanos (misma resolución y mismo borrado); la simetría de riesgo se midió con
    // sonda: los hermanos YA consumen en los mismos contextos ("el tren por la tarde
    // sale tarde"), así que no se introduce ninguna clase de riesgo nueva. El artículo
    // "la" es OBLIGATORIO: "para mañana" (sin artículo) es la forma-fecha (+1d) y no
    // se toca.
    // c.929: sufijo "siguiente(s)" ("lo necesito para la mañana siguiente", "llamar en
    // la tarde siguiente") — la parte del día del día SIGUIENTE (+1d), forma cotidiana
    // del vencimiento al día siguiente. Antes el rewrite del pleonasmo "mañana
    // siguiente"→"mañana" (c.148) robaba "siguiente" pese al artículo "la" y la ancla
    // resolvía la parte del día de HOY (09:00 ya pasada al mediodía → tarea vencida al
    // nacer, P1); con "tarde"/"noche", además "siguiente" quedaba de residuo en el
    // título. Doctrina SIMÉTRICA para todos los conectores: el sufijo se consume con
    // la ancla (título limpio) y desplaza la fecha +1d (ver standalonePartOfDayNext y
    // el guard anti-artículo del pleonasmo en la pre-normalización).
    private val standalonePartOfDayPattern = Regex("""(?i)\b(?:justo\s+)?(?:(?:a\s+la|de\s+la|por\s+la|en\s+la|entrando\s+la|entrada\s+la|para\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)|de\s+(tarde|noche|madrugada)|durante\s+la\s+(tarde|noche|madrugada))(?:\s+de\s+(?:hoy|ma[nñ]ana|ayer|anteayer|antier)|\s+siguientes?)?\b""")
    private val standalonePartOfDayTimes = mapOf(
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0),
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0)
    )

    /**
     * Parte del día COMPACTA (coloquial, sin conector): un marcador de día
     * ("hoy"/"mañana"/"pasado mañana"/"antepasado mañana"/"ayer"/"anteayer") seguido
     * DIRECTAMENTE de "tarde"/"noche"/"madrugada" — p.ej. "hoy tarde", "mañana noche",
     * "pasado mañana tarde", "ayer noche", "anteayer tarde". Es la forma abreviada de
     * "hoy en la tarde"/"mañana por la noche"/"ayer por la tarde", muy común al escribir
     * rápido en móvil.
     *
     * Antes NO se reconocía: el marcador de día fijaba la fecha, pero la parte del día
     * quedaba como residuo en el título y la hora caía al default 09:00 — agenda errónea
     * (una tarea "hoy noche" se vencía a las 09:00 de hoy, no a las 21:00) y título
     * corrupto ("hoy noche" se mostraba tal cual). P1.
     *
     * "ayer"/"anteayer"/"antier" (variante coloquial hispanoamericana) también admiten
     * la forma compacta ("ayer tarde", "anteayer noche"): tan comunes como las futuras al
     * capturar eventos pasados. Antes la asimetría hacía que "hoy tarde" resolviera a
     * 15:00 pero "ayer tarde" cayera a 09:00 con "tarde" como residuo en el título — la
     * cita pasada quedaba mal agendada y mal titulada. Simétrica ahora.
     *
     * Se EXCLUYE "mañana" como parte del día aquí (sólo tarde/noche/madrugada): la
     * palabra "mañana" es ambigua (día vs. parte del día) y la forma compacta "hoy
     * mañana"/"mañana mañana" es rara y propensa a fechar mal (choca con el marcador
     * "mañana" como día). La forma con conector ("hoy en la mañana") ya funciona vía
     * [standalonePartOfDayPattern]; la compacta de "mañana" no aporta suficiente valor
     * para justificar el riesgo de ambigüedad. "madrugada" se incluye: es inequívoca
     * (sólo parte del día, no hay marcador "madrugada") y se resuelve a 04:00.
     *
     * El marcador de día se captura sólo para anclar la parte del día a una referencia
     * temporal (evitar robar "tarde"/"noche" sueltas de otras construcciones); la fecha
     * la resuelve el `when` de fecha existente ("hoy"→hoy, "mañana"→+1, etc.).
     */
    private val compactDayPartOfDayPattern =
        // "después de mañana" (≡ "pasado mañana", c.846) va ANTES que "mañana"
        // suelto: si no, la forma compacta "después de mañana tarde" casaba sólo
        // "mañana tarde" y dejaba "después" como residuo huérfano en el título.
        Regex("""(?i)\b(?:antepasad[oa]\s+ma[nñ]ana|pasado\s+ma[nñ]ana|despu[eé]s\s+de\s+ma[nñ]ana|ma[nñ]ana|hoy|anteayer|antier|ayer)\s+(tarde|noche|madrugada)\b""")
    private val compactDayPartOfDayTimes = mapOf(
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0)
    )

    /**
     * "a primera(s) hora(s)" (opcionalmente "de la mañana/madrugada"): inicio de jornada
     * ~09:00. Acepta la forma plural del adjetivo ("a primeras horas"), variante
     * cotidiana del singular; antes dejaba residuo en el título aunque el dueAt se
     * resolvía vía la parte del día (c.400). Como es una hora canónica de respaldo
     * (no un reloj explícito), no fuerza contexto PM.
     */
    // c.931: el conector admite el artículo «la» («avisar a la primera hora») —
    // `(?:a\s+la\s+|a\s+)?`; NUNCA «la» desnuda sin «a» («avisar la primera
    // hora» = objeto bivalente, lateral registrada FUERA). La narrativa «a la
    // primera hora de clase» sigue protegida por ordinalHoraOccurrenceIsContent.
    // c.933: el artículo admite el plural «las» («avisar a las primeras
    // horas») — `(?:a\s+las?\s+|a\s+)?`; NUNCA «las» desnuda sin «a» (objeto
    // bivalente, hermano del pin «avisar la última hora»). La narrativa
    // plural «a las primeras horas de clase» sigue protegida por el guard.
    private val primeraHoraPattern =
        Regex("""(?i)(?:justo\s+)?\b(?:a\s+las?\s+|a\s+)?(?:primeras?\s+horas?|primer\s+momento)(?:\s+(?:de\s+la\s+(?:ma[nñ]ana|manana|madrugada)|del\s+d[ií]a|de\s+(?:la\s+)?jornada|de\s+los\s+d[ií]as|de\s+d[ií]a))?\b""")
    private val primeraHoraTime = LocalTime.of(9, 0)

    /**
     * "al inicio del día/días"/"al inicio de la jornada": sinónimo cotidiano de
     * "a primera hora" (inicio de jornada ~09:00). Simétrico de [alFinalDelDiaPattern]
     * (fin de jornada 18:00): antes el FIN de jornada SÍ se interpretaba como hora
     * canónica, pero el INICIO NO — "al inicio del día" dejaba la tarea SIN `dueAt`
     * (olvidada, invisible en What Now/planificador, sin recordatorio) y la frase
     * quedaba como residuo en el título. Exige el conector "al "/"a " + "día/jornada"
     * para no colisionar con "al inicio del proyecto" ni "fase inicial del día".
     * Como [primeraHoraTime], es hora de respaldo: si hay una hora/parte del día
     * explícita, ésta tiene prioridad y el patrón solo limpia "al inicio del día".
     */
    private val alInicioDelDiaPattern =
        Regex("""(?i)(?:justo\s+)?(?:al\s+inicio|a\s+inicio)\s+(?:del\s+d[ií]a|de\s+(?:la\s+)?jornada|de\s+los\s+d[ií]as|de\s+d[ií]a)\b""")

    /**
     * "a última hora"/"a último momento" (opcionalmente "de la mañana/tarde/noche/madrugada"
     * o "del día/días/jornada"): fin de jornada ~18:00. Simétrica de "a primera hora".
     * Antes no se interpretaba como hora canónica: caía al default 09:00 (agenda errónea)
     * y "a última hora" quedaba como residuo en el título. Como es hora de respaldo, no
     * fuerza contexto PM; si hay una parte del día explícita ("de la tarde"), ésta tiene
     * prioridad en la resolución y el patrón solo limpia "a última hora" (la parte del día
     * la limpia su propio patrón).
     *
     * El sufijo "del día/días/jornada" se añadió en c.548 para cerrar la asimetría con
     * [primeraHoraPattern] (c.546): "a primera hora del día" limpiaba el título pero
     * "a última hora del día" dejaba "del día"/"de la jornada" como residuo aunque la
     * hora canónica (18:00) sí se resolvía. Mismo cierre de jornada que "al final del día".
     */
    // c.931: conector «a la» simétrico de [primeraHoraPattern] («avisar a la
    // última hora» → 18:00 con título limpio «avisar», sin residuo «a la»).
    // c.933: artículo plural «a las» simétrico («avisar a las últimas
    // horas» → 18:00 con título limpio «avisar», sin residuo «a las»).
    private val ultimaHoraPattern =
        Regex("""(?i)(?:justo\s+)?(?<![a-záéíóúñ])(?:a\s+las?\s+|a\s+)?[uú]ltim[ao]s?\s+(?:horas?|momento)(?:\s+de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada)|\s+del\s+d[ií]a|\s+de\s+(?:la\s+)?jornada|\s+de\s+los\s+d[ií]as|\s+de\s+d[ií]a)?\b""")
    private val ultimaHoraTime = LocalTime.of(18, 0)

    /**
     * "al final del día/días"/"al final de la jornada": sinónimo cotidiano de
     * "a última hora" (fin de jornada ~18:00). Antes no se interpretaba como hora
     * canónica: la tarea quedaba SIN `dueAt` (olvidada, invisible en What Now/
     * planificador, sin recordatorio) y la frase quedaba como residuo en el título.
     * Exige el conector "al " para no colisionar con "fase final del proyecto" ni
     * "en la fase final" (no son fin de jornada). Como [ultimaHoraTime], es hora de
     * respaldo: si hay una parte del día explícita elsewhere, ésta tiene prioridad y
     * el patrón solo limpia "al final del día".
     */
    private val alFinalDelDiaPattern =
        Regex("""(?i)(?:justo\s+)?(?:al\s+final|al\s+fin|a\s+fin)\s+(?:del\s+d[ií]a|de\s+(?:la\s+)?jornada|de\s+los\s+d[ií]as|de\s+d[ií]a)\b""")
    private val alFinalDelDiaTime = LocalTime.of(18, 0)

    /**
     * "temprano"/"muy temprano" como modificador de franja tras una parte del día o
     * fecha relativa ya resuelta ("mañana temprano", "por la mañana temprano",
     * "esta tarde temprano"): la dueAt se calcula correctamente, pero el adverbio
     * sobrevivía como residuo en el título ("reunión mañana temprano" → "reunión
     * temprano"). NO asigna hora por sí solo: "temprano" suelto es ambiguo y puede ser
     * contenido legítimo ("llegué temprano"); por eso sólo se aplica en la cascada de
     * limpieza del título cuando YA se agendó algo (dueAt != null), evitando degradar
     * notas sin valor de agenda.
     */
    private val earlyModifierPattern =
        Regex("""(?i)\b(?:muy\s+)?temprano\b""")

    /**
     * "al amanecer"/"al alba"/"al despuntar el día": salida del sol, forma cotidiana de
     * "muy temprano" (antes ~06:00). Antes no se interpretaba como hora canónica: la tarea
     * quedaba SIN `dueAt` (olvidada, invisible en What Now/planificador, sin recordatorio) y
     * la frase quedaba como residuo en el título. Distinta de "madrugada" (04:00, franja
     * nocturna) y de "a primera hora" (09:00, inicio de jornada): el amanecer es la primera
     * luz, intermedia. Exige conector ("al" o, desde c.549, "hacia el/la"/"hacia") para no
     * colisionar con "hoy amanece lloviendo" (verbo) ni con "un amanecer hermoso" (sustantivo
     * poético sin valor de agenda). El conector sigue siendo OBLIGATORIO: no se hace opcional
     * (eso reabriría la colisión); sólo se amplía qué conectores válidos se admiten, igual que
     * mediodía ya acepta "hacia el mediodía"/"hacia mediodía". Hora de respaldo: si hay una
     * parte del día/hora explícita, ésta tiene prioridad y el patrón solo limpia "al amanecer".
     */
    private val amanecerPattern =
        Regex("""(?i)(?:justo\s+)?(?:al\s+|hacia\s+(?:el\s+|la\s+)?)(?:amanecer|alba|despuntar\s+(?:el|la|de\s+la|del)\s+(?:alba|d[ií]a)|clarear|aclarar)\b""")
    private val amanecerTime = LocalTime.of(6, 0)

    /**
     * "al atardecer"/"al anochecer"/"al ocaso"/"al ponerse el sol": puesta/entrada de la noche,
     * contraparte vespertina del amanecer (primera luz). Forma cotidiana de "al final del día"
     * ("caminar al atardecer", "reunión al anochecer"). Antes no se interpretaba como hora
     * canónica: la tarea quedaba SIN `dueAt` (olvidada, invisible en What Now/planificador, sin
     * recordatorio) y la frase quedaba como residuo en el título. Asimetría flagrante con
     * [amanecerPattern] (06:00) que SÍ funcionaba: el amanecer se agendaba y el atardecer se
     * perdía. Hora de respaldo 18:00 (tarde tardía / ocaso, canónica ya usada por "a última
     * hora"/"al final del día"): en el trópico la puesta de sol ronda las ~18:30-19:00, y 18:00
     * es la canónica vespertina establecida, sin falsa precisión. Exige conector ("al" o, desde
     * c.549, "hacia el/la"/"hacia") para no colisionar con el verbo ("atardece lloviendo") ni
     * con el sustantivo suelto ("un atardecer hermoso"). El conector sigue siendo OBLIGATORIO
     * (no se hace opcional: reabriría la colisión); sólo se amplía qué conectores válidos se
     * admiten, igual que mediodía acepta "hacia el mediodía". Como las demás horas canónicas,
     * es hora de respaldo: si hay una hora explícita, ésta gana y el patrón solo limpia
     * "al atardecer".
     */
    private val atardecerPattern =
        Regex("""(?i)(?:justo\s+)?(?:al\s+|hacia\s+(?:el\s+|la\s+)?)(?:atardecer|anochecer|ocaso|ponerse\s+(?:el\s+sol|del\s+sol))\b""")
    private val atardecerTime = LocalTime.of(18, 0)

    /**
     * "antes de/después de + comida o sueño": ancla temporal cotidiano en español (LATAM)
     * para citas recordatorias — "reunión después del almuerzo", "llamar antes de dormir",
     * "medicina después de comer", "cita antes de la cena". Antes estas frases caían a
     * `dueAt=null` (olvidada, invisible en What Now/planificador, sin recordatorio) y la
     * frase entera quedaba como residuo en el título — o, peor, el conector "antes del?/de"
     * se borraba tarde y mutilaba el título ("cita antes del almuerzo"→"cita almuerzo").
     *
     * `laterRelativePattern` excluye "después de/del" (para no tocar "después de N minutos",
     * que resuelve otro patrón), así estas frases caían al vacío. La diferencia con ese
     * adverbio suelto: aquí hay un ANCLA concreta (comida/sueño) que aporta hora de respaldo
     * honesta (no IA: horas canónicas de comidas/ritmo de sueño LATAM).
     *
     * El grupo 1 es el modificador ("antes"/"después") y el grupo 2 el ancla (comida/sueño):
     *   · desayuno/desayunar: antes→07:30, después→09:00
     *   · almuerzo/comer/comida/almorzar: antes→11:30, después→14:00
     *   · merienda/merendar: antes→16:30, después→17:30
     *   · cena/cenar: antes→19:30, después→21:00
     *   · dormir/acostarse: antes→21:30 (no "después de dormir": no es idiomático)
     *   · siesta: antes→13:30, después→15:30 (ritual post-almuerzo LATAM, hora canónica honesta)
     * "después de la cena" admite el artículo opcional; "antes del almuerzo" la contracción.
     * El modificador (antes/después) es obligatorio: "almuerzo" solo no es cita (es el evento),
     * no se agenda. Hora de respaldo: si hay hora explícita, ésta gana (igual que amanecer).
     *
     * Simétrica de [amanecerPattern]/[atardecerPattern]: mismo contrato (hora canónica de
     * respaldo, título limpio, no colisión con verbos). El verbo infinitivo ("desayunar",
     * "comer", "merendar", "cenar", "dormir") y el sustantivo ("desayuno", "almuerzo",
     * "merienda", "cena") son ambos idiomáticos ("después de comer"="después del almuerzo").
     */
    private val mealSleepAnchorPattern = Regex(
        """(?i)\b(?:justo\s+)?(antes|despu[eé]s)\s+de(?:l)?\s+(?:la\s+)?(desayuno|desayunar|almuerzo|comer|comida|almorzar|merienda|merendar|cena|cenar|dormir|acostarse|acostar|siesta)\b"""
    )
    private val mealSleepAnchorTimes: Map<String, Map<String, LocalTime>> = mapOf(
        "desayuno" to mapOf("antes" to LocalTime.of(7, 30), "después" to LocalTime.of(9, 0)),
        "desayunar" to mapOf("antes" to LocalTime.of(7, 30), "después" to LocalTime.of(9, 0)),
        "almuerzo" to mapOf("antes" to LocalTime.of(11, 30), "después" to LocalTime.of(14, 0)),
        "comer" to mapOf("antes" to LocalTime.of(11, 30), "después" to LocalTime.of(14, 0)),
        "comida" to mapOf("antes" to LocalTime.of(11, 30), "después" to LocalTime.of(14, 0)),
        "almorzar" to mapOf("antes" to LocalTime.of(11, 30), "después" to LocalTime.of(14, 0)),
        "merienda" to mapOf("antes" to LocalTime.of(16, 30), "después" to LocalTime.of(17, 30)),
        "merendar" to mapOf("antes" to LocalTime.of(16, 30), "después" to LocalTime.of(17, 30)),
        "cena" to mapOf("antes" to LocalTime.of(19, 30), "después" to LocalTime.of(21, 0)),
        "cenar" to mapOf("antes" to LocalTime.of(19, 30), "después" to LocalTime.of(21, 0)),
        "dormir" to mapOf("antes" to LocalTime.of(21, 30), "después" to LocalTime.of(23, 0)),
        "acostarse" to mapOf("antes" to LocalTime.of(21, 30), "después" to LocalTime.of(23, 0)),
        "acostar" to mapOf("antes" to LocalTime.of(21, 30), "después" to LocalTime.of(23, 0)),
        "siesta" to mapOf("antes" to LocalTime.of(13, 30), "después" to LocalTime.of(15, 30))
    )

    /**
     * "media mañana/tarde/noche/madrugada" y "medio/media día/noche": el PUNTO MEDIO de una
     * parte del día, forma cotidiana muy común ("reunión a media tarde", "llamar a media
     * noche", "revisar a media mañana", "almuerzo a medio día"). Antes NO se interpretaba
     * como hora: la tarea caía a `dueAt=null` (olvidada, invisible en What Now/planificador,
     * sin recordatorio) y "media tarde"/"media noche" quedaba como residuo en el título; o
     * peor, "media mañana" colisionaba con el marcador de fecha "mañana" (+1 día) y la cita
     * se agendaba MAÑANA a 09:00 con el título mutilado ("revisar a media") — fecha Y hora
     * Y título equivocados a la vez (P1: cita perdida + contenido degradado).
     *
     * Asimetría flagrante: "mediodía"/"medianoche" (una palabra) SÍ funcionaban (12:00/
     * 00:00), pero sus formas separadas "medio día"/"media noche" NO — el almuerzo agendado
     * "a medio día" se perdía mientras "al mediodía" sí se agendaba. Este patrón cierra esa
     * brecha y cubre toda la familia "media <parte>".
     *
     * Horas: punto medio entre la canónica de la parte y el límite de la siguiente.
     *   media mañana → 10:30 (entre 09:00 "primera hora" y 12:00 "mediodía")
     *   media tarde  → 16:30 (entre 15:00 "tarde" y 18:00 "última hora"/"atardecer")
     *   media noche  → 00:00 (lexicalizada = medianoche, igual que "medianoche" una palabra)
     *   media madrugada → 03:00 (entre 00:00 y 06:00 "amanecer"; madrugada canónica=04:00)
     *   medio/media día → 12:00 (lexicalizado = mediodía)
     *
     * El conector opcional (a/de/por/en + la) se consume para no dejar residuo ("a media
     * tarde" → se borra entero, no deja "a"). "media noche"=00:00 es hora canónica
     * inequívoca (midnight), NO contexto PM: no se añade a las PM keys (00:00 es AM, inicio
     * del día); sólo "media tarde" aporta contexto PM (vespertino, como "tarde").
     *
     * Colisión con "mañana" (fecha): el patrón consume "media mañana" entero en la limpieza
     * del título (antes del borrado genérico de "mañana"), y [mananaAsDate] excluye el
     * prefijo "media " de su timeMarker para no tratar "media mañana" como fecha +1.
     */
    private val mediaPartOfDayPattern =
        Regex("""(?i)(?:justo\s+)?(?:(?:\b(?:al\s+|a\s+la\s+|a\s+|de\s+la\s+|de\s+|por\s+la\s+|por\s+|en\s+la\s+|en\s+)|pasad[oa]\s+(?:el\s+|la\s+)?|despu[eé]s\s+(?:del\s+|de\s+la\s+|de\s+)))?(media|medio)\s+(ma[nñ]ana|manana|tarde|noche|madrugada|d[ií]a)\b""")
    private val mediaPartOfDayTimes = mapOf(
        "mañana" to LocalTime.of(10, 30),
        "manana" to LocalTime.of(10, 30),
        "tarde" to LocalTime.of(16, 30),
        "noche" to LocalTime.of(0, 0),
        "madrugada" to LocalTime.of(3, 0),
        "día" to LocalTime.of(12, 0),
        "dia" to LocalTime.of(12, 0)
    )

    /**
     * Hora suelta con parte del día, sin "a las" ni rango: "Taller 9 de la tarde",
     * "Cena 9 de la noche", "Cita 10 de la mañana", "Evento 9 de la madrugada". Antes la
     * hora caía a la canónica de la parte del día (15:00/21:00/09:00/04:00) ignorando el
     * número, y éste quedaba como residuo en el título ("Taller 9"). Aquí se resuelve la
     * hora absoluta con su meridiem (tarde/noche → +12 si N<12; mañana/madrugada → AM, 12→0).
     * El patrón exige el conector "de la" para no colisionar con "9 de marzo" (fecha con mes
     * —lo resuelve monthNameDate) ni "el 9" aislado (dayOfMonthPattern); el lookahead negativo
     * descarta "9 de la mañana" seguido de un nombre de mes ("9 de la mañana de marzo" no es
     * una forma real, pero protege de ambigüedades), PERO admite el calificador de fecha
     * relativa ("9 de la noche de mañana"/"9 de la tarde de hoy") — antes el lookahead rechazaba
     * cualquier "de <letra>", así que "9 de la noche de mañana" no casaba: el número quedaba
     * como residuo en el título ("reunión 9") aunque la hora se resolviera vía contexto PM.
     * Admite minutos opcionales ("9:30 de la
     * tarde"), aunque esa forma ya la cubre timePatterns[1] + contexto PM; se deja por simetría.
     *
     * Admite además el sufijo fraccionario cotidiano "y media"/"y cuarto" entre la hora y
     * "de la" (grupo 3): "Cena 9 y media de la noche" → 21:30, "Cita 8 y cuarto de la
     * mañana" → 08:15. Es la forma simétrica del "a las N y media" de [timePatterns] (grupo
     * 3 de ese patrón), que YA se resolvía; pero la forma SIN "a las" (esta) NO lo aceptaba,
     * así que "9 y media de la tarde" caía a la canónica de la parte (15:00, no 21:30) y
     * dejaba "9 y media" como residuo en el título ("Cita 9 y media") → cita mal agendada
     * (30 min antes) y contenido capturado degradado. El conector "de la <parte>" sigue
     * siendo la señal de desambiguación que evita robar cantidades ("diez y media botellas"
     * no lleva "de la tarde"), igual que ya ocurre con la hora en punto.
     *
     * Admite también la fracción TRAS "de la <parte>" (grupo 5): "9 de la noche y media" →
     * 21:30, "9 de la tarde y cuarto" → 21:15. Es el orden inverso al del grupo 3 y el mismo
     * hueco que [timePatterns] cubre con su grupo 5 ("a las 9 pm y media"): antes la fracción
     * posterior no casaba, la cita caía en punto (21:00 en vez de 21:30) y "y media" quedaba
     * como residuo en el título. El grupo 3 (fracción anterior) tiene prioridad; el 5 actúa
     * como fallback (sólo positiva "y media/cuarto/..."). El `\b` final deja intacta la
     * palabra siguiente ("9 de la noche y hablar" → "y hablar" sobrevive, no se roba:
     * "hablar" no casa CLOCK_FRACTION_Y).
     *
     * c.425 — artículo "las" opcional antes de la hora ("las 9 de la noche"): la forma
     * cotidiana de dictado/captura rápida omite la "a" ("reunión las 9 de la noche").
     * Antes el patrón exigía el número justo tras el límite de palabra, así "las" quedaba
     * como residuo del título ("reunión las") pese a agendar 21:00. Ahora `(?:las\s+)?`
     * consume el artículo cuando lo hay, simétrico a [timePatterns] ("a las N"). El
     * lookahead anti-"de <mes>" y la exigencia de "de la <parte>" siguen filtrando
     * cuentas ("las 9 cajas" no casa: no hay "de la noche/tarde/..."), así no se
     * falsifica como cita.
     */
    // Hora suelta con parte del día ("N de la tarde"/"N de la noche"): resuelve la hora
    // absoluta con su meridiem Y, en la limpieza del título, consume también la preposición
    // "de" que introduce la hora ("cita de 5 de la tarde" → "cita"). El prefijo opcional
    // no capturador `(?:\bde\s+)?` cumple ambas funciones: no altera los grupos de
    // resolución (hora/min/fracción/parte) y permite que replaceRange borre el genitivo
    // junto con la hora, evitando el residuo "de" (`title='cita de'`). El lookbehind
    // `(?<![:\d])` (antes del prefijo) evita casar dígitos pegados; el prefijo sólo casa
    // "de" inmediatamente antes de la hora, no contenido intermedio ("de 5 personas de la
    // tarde" no se confunde: el patrón casa "5 de la tarde" empezando en "5").
    private val standaloneHourPartOfDayStripPattern =
        Regex("""(?i)(?<![:\d])(?:\bde\s+)?(?:las\s+)?(\d{1,2}|$WRITTEN_HOUR_ALT)(?:(?::|h)([0-5]\d))?(?:\s+($CLOCK_FRACTION_Y))?\s+de\s+la\s+(tarde|noche|madrugada|ma[nñ]ana|manana)(?!\s+de\s+(?!hoy\b|ma[nñ]ana\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b)[a-záéíóúüñ])(?:\s+($CLOCK_FRACTION_Y))?$APPROX_TIME_SUFFIX\b""")

    private fun resolveStandaloneHourPartOfDay(match: MatchResult): LocalTime? {
        val h = parseHour(match.groupValues[1]) ?: return null
        // Minutos explícitos ":MM" (grupo 2) con prioridad; si no, la fracción sub-hora
        // "y media"/"y cuarto"/"y veinte"/"y tres cuartos"/... (grupo 3, frase "y <min>")
        // aporta los minutos; si tampoco, 0. Simétrico del "a las N y <min>" de [timePatterns].
        // Grupo 5: fracción TRAS "de la <parte>" ("9 de la noche y media") como fallback del
        // grupo 3 (fracción ANTES). Véase el comentario del patrón.
        val g3 = match.groupValues.getOrNull(3)?.lowercase().orEmpty()
        val g5 = match.groupValues.getOrNull(5)?.lowercase().orEmpty()
        val frac = resolveClockFraction(g3) ?: resolveClockFraction(g5)
        val min = match.groupValues[2].toIntOrNull() ?: (frac ?: 0).coerceIn(0, 59)
        if (h !in 0..24 || min !in 0..59) return null
        val part = match.groupValues[4].lowercase()
        val hour = when {
            h == 24 -> 0
            part == "noche" && h == 12 -> 0    // "12 de la noche" = medianoche
            part == "tarde" && h == 12 -> 12   // "12 de la tarde" = mediodía
            (part == "tarde" || part == "noche") && h < 12 -> h + 12
            h == 12 -> 0                       // "12 de la mañana/madrugada" = 12am → 00:00
            else -> h
        }
        return if (hour in 0..23) LocalTime.of(hour, min) else null
    }

    private val weekdays = mapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )

    /** Abreviatura → nombre canónico del día (sin "mar"/martes: colisión con
     *  marzo; ver [weekdayAbbrevRewriter]). */
    private val weekdayAbbrevToFull = mapOf(
        "lun" to "lunes",
        "mie" to "miércoles",
        "mié" to "miércoles",
        "mierc" to "miércoles",
        "miérc" to "miércoles",
        "jue" to "jueves",
        "vier" to "viernes",
        "vie" to "viernes",
        "sab" to "sábado",
        "sáb" to "sábado",
        "dom" to "domingo"
    )

    private val months = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
        // Abreviaturas informales de uso común al capturar ("el 25 de dic",
        // "pago el 1 de ene", "cita el 28 de feb"). Sin ellas la fecha caía en
        // dueAt=null y el compromiso quedaba como título basura sin aviso, pese
        // a ser la misma intención que el nombre completo ("25 de diciembre").
        "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "may" to 5,
        "jun" to 6, "jul" to 7, "ago" to 8, "sep" to 9, "set" to 9,
        "sept" to 9, "oct" to 10, "nov" to 11, "dic" to 12
    )

    /** Nombre canónico de cada mes (1..12) para inferir el mes de CIERRE en rangos
     *  de día suelto que cruzan de mes ("del 31 de diciembre al 2" → cierre en enero). */
    private val monthNameByIndex = mapOf(
        1 to "enero", 2 to "febrero", 3 to "marzo", 4 to "abril",
        5 to "mayo", 6 to "junio", 7 to "julio", 8 to "agosto",
        9 to "septiembre", 10 to "octubre", 11 to "noviembre", 12 to "diciembre"
    )

    private val categories = listOf(
        "trabajo" to listOf("reunión", "reunion", "informe", "reporte", "cliente", "contrato", "presentación", "presentacion", "entregar", "proyecto", "oficina", "correo", "email", "junta", "gerente", "jefe"),
        "compras" to listOf("comprar", "compra", "supermercado", "mercado", "farmacia", "tienda", "recados", "mandado", "leche", "víveres", "viveres"),
        "salud" to listOf("médico", "medico", "doctor", "cita", "gimnasio", "ejercicio", "correr", "dentista", "salud", "medicina", "pastillas", "vacuna", "análisis", "analisis"),
        "casa" to listOf("limpiar", "cocinar", "lavar", "cocina", "casa", "hogar", "reparar", "jardín", "jardin", "basura", "tramitar", "luz", "agua", "gas"),
        "personal" to listOf("llamar a", "familia", "mamá", "mama", "papá", "papa", "herman", "pareja", "amigo", "amiga", "cumpleaños", "cumpleanos", "aniversario")
    )

    /**
     * Categoría explícita del usuario vía etiqueta "#cat" o "@cat". Solo se reconoce
     * si coincide con un nombre de categoría conocido; así "#proyecto" (un hashtag de
     * contenido) no se roba como categoría ni se elimina del título. La etiqueta
     * explícita tiene prioridad sobre la inferencia por keywords (el usuario dijo qué
     * quiere) y se limpia del título para no dejar residuo.
     */
    private val explicitCategoryPattern =
        Regex("""(?i)[#@](${categories.joinToString("|") { Regex.escape(it.first) }})\b""")

    /**
     * Reescritura compartida de los rangos "del D1 de MES [del A1] al D2" /
     * "entre el D1 de MES [del A1] y el D2" (día de CIERRE suelto, sin mes).
     * Ancla al CIERRE reescribiendo a "el D2 de <mesCierre> [del <añoCierre>]"
     * para reutilizar TODO el flujo [monthNamePattern]. El mes de CIERRE se
     * infiere: si D2 >= D1 el cierre es el mismo mes de inicio; si D2 < D1 el
     * rango cruza al mes SIGUIENTE ("del 31 de diciembre al 2" → "el 2 de
     * enero"), con roll de año cuando el paso diciembre→enero y el año venía
     * explícito. Sin año explícito, [monthNamePattern] aplica su roll anual.
     * Exige mes de inicio válido contra `months` (no agenda contenido).
     */
    private fun rewriteStartMonthBareEndDayRange(
        m: MatchResult, startDayIdx: Int, monthIdx: Int, yearIdx: Int, endDayIdx: Int
    ): CharSequence {
        val monthTok = m.groupValues[monthIdx].lowercase()
        val startMonthNum = months[monthTok] ?: return m.value
        val startDay = m.groupValues[startDayIdx].trim().toIntOrNull() ?: return m.value
        val endDay = m.groupValues[endDayIdx].trim()
        val endDayNum = endDay.toIntOrNull() ?: return m.value
        val year = m.groupValues[yearIdx].trim()

        val crossMonth = endDayNum < startDay
        val (endMonthName, endYear) = if (crossMonth) {
            val next = if (startMonthNum == 12) 1 else startMonthNum + 1
            val rolledYear = if (year.isNotEmpty() && startMonthNum == 12) {
                (year.toIntOrNull()?.plus(1))?.toString() ?: year
            } else year
            (monthNameByIndex[next] ?: monthTok) to rolledYear
        } else {
            monthTok to year
        }
        return if (endYear.isNotEmpty()) "el $endDay de $endMonthName del $endYear"
        else "el $endDay de $endMonthName"
    }

    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ParsedTaskInput {
        val base = Instant.ofEpochMilli(now).atZone(zone)
        var working = text.trim()
        val original = text.trim()

        // "anoche"/"antenoche" = palabras únicas que funden "ayer noche"/"anteayer noche":
        // formas cotidianísimas (más aún que "ayer noche" compacto) para una cita pasada de
        // la franja nocturna. Antes NO se reconocían: "reunión anoche" → dueAt=null + residuo,
        // y peor, "anoche a las 10" → HOY 10:00 (debería AYER 22:00): cita pasada agendada en
        // el futuro. Normalizando a la forma de dos palabras se reutiliza TODO el flujo
        // existente (el `when` de fecha fija "ayer"=−1d, el patrón compacto aporta noche=
        // 21:00, y con hora explícita "anoche a las 10" → PM-context → 22:00). "antenoche"
        // se expande ANTES para que su substring "anoche" no case el reemplazo equivocado.
        // \b evita colisión con otras palabras ("anoche" no es sustantivo común: adverbio puro).
        working = working
            .replace(Regex("""(?i)\bantenoche\b"""), "anteayer noche")
            .replace(Regex("""(?i)\banoche\b"""), "ayer noche")

        // "el día siguiente"/"día siguiente" → "mañana": reutiliza TODO el flujo de
        // "mañana" (fecha +1d, hora explícita, limpieza del título). Va aquí (antes de
        // mananaAsDate y de la cascada de fecha) para que la fecha relativa se resuelva.
        working = dayAfterPattern.replace(working, "mañana")

        // "mañana siguiente": pleonasmo coloquial ("mañana" ya = día siguiente, "siguiente"
        // refuerza). Antes el borrado de "mañana" dejaba el residuo "siguiente" en el título
        // ("envío siguiente" en vez de "envío") — contenido capturado degradado (P1). Se
        // normaliza a "mañana" para reutilizar TODO el flujo existente. \b evita colisionar
        // con "mañanas siguientes"; no se toca "siguiente" sin "mañana" (ambiguo como
        // contenido: "capítulo siguiente").
        // c.929: NO reescribir cuando «mañana» lleva el artículo «la» — «la mañana
        // siguiente» = the (next) morning (sustantivo): la fecha la gobierna la ancla
        // parte-del-día con el sufijo «siguiente» (+1d, ver standalonePartOfDayPattern)
        // o es contenido narrativo (ver mananaOccurrenceIsContent G3). El pleonasmo
        // sólo aplica SIN artículo ("envío mañana siguiente"); antes el rewrite ciego
        // robaba «siguiente» y la ancla resolvía la mañana de HOY — 09:00 ya pasada al
        // mediodía → tarea vencida al nacer (P1 evitar-olvidos).
        working = working.replace(Regex("""(?i)(?<!la\s)\bma[nñ]ana\s+siguientes?\b"""), "mañana")

        // Ordinales numéricos: "1ro"/"2do"/"3er"/"1º"… seguidos de " de " se normalizan a
        // su dígito base para que los patrones de fecha (que exigen \d seguido de espacio)
        // los reconozcan. Solo en contexto de fecha (" de ") para no tocar contenido.
        working = ordinalSuffixPattern.replace(working) { m -> m.groupValues[1] + m.groupValues[2] }
        working = normalizeOrdinalBeforeWeekday(working)

        // Rango de días multi-evento ("del 15 al 20 de diciembre") → "el 20 de diciembre":
        // reutiliza TODO el flujo monthNamePattern (fecha + limpieza del título). Va ANTES
        // que bareDayMonthPattern/monthNamePattern para que éstos vean sólo el día final
        // y no dejen "del 15 al" como residuo. Exige mes válido o cualificador relativo
        // ("del mes que viene"); si no, se deja intacto (no se inventan fechas de contenido).

        // ANTES los rangos con conector "entre...y" (c.444): misma intención que "del ... al
        // ..." pero con conector cotidiano alternativo. Van primero porque "entre ... y ..." no
        // casa en dayRangePattern/crossMonthDayRangePattern (que exigen "al"/"hasta") y caía a
        // monthNamePattern anclando al día INICIAL + residuo "entre [y]" en el título. Primero
        // el que CRUZA de mes (cada extremo con su mes), luego el de un solo mes al final.
        working = entreCrossMonthDayRangePattern.replace(working) { m ->
            val startMonthTok = m.groupValues[2].lowercase()
            val endMonthTok = m.groupValues[5].lowercase()
            if (startMonthTok !in months || endMonthTok !in months) return@replace m.value
            val endDay = m.groupValues[4]
            val endYear = m.groupValues[6].trim().ifBlank { m.groupValues[3].trim() }
            if (endYear.isNotEmpty()) "el $endDay de $endMonthTok del $endYear"
            else "el $endDay de $endMonthTok"
        }

        working = entreDayRangePattern.replace(working) { m ->
            val relQualifier = m.groupValues[3].trim()
            val monthTok = m.groupValues[4].trim().lowercase()
            when {
                relQualifier.isNotEmpty() -> "el ${m.groupValues[2]} del $relQualifier"
                monthTok.isNotEmpty() && monthTok in months -> {
                    val endDay = m.groupValues[2]
                    val year = m.groupValues[5].trim()
                    if (year.isNotEmpty()) "el $endDay de $monthTok del $year"
                    else "el $endDay de $monthTok"
                }
                else -> m.value
            }
        }

        // PRIMERO el rango que CRUZA de mes ("del 28 de febrero al 1 de marzo"): cada
        // extremo lleva su propio mes, forma que [dayRangePattern] no casa. Se ancla al
        // CIERRE y reescribe a "el <cierre> de <mesCierre> [del <año>]" para reutilizar
        // monthNamePattern. Sólo si AMBOS tokens son meses válidos (valida contra `months`)
        // para no agendar contenido ("del 3 al 5 de unidades y de piezas"). El año del
        // cierre: explícito del cierre si lo hay; si no, el de apertura; si ninguno, se
        // omite y monthNamePattern aplica su roll anual.
        working = crossMonthDayRangePattern.replace(working) { m ->
            val startMonthTok = m.groupValues[2].lowercase()
            val endMonthTok = m.groupValues[5].lowercase()
            if (startMonthTok !in months || endMonthTok !in months) return@replace m.value
            val endDay = m.groupValues[4]
            val endYear = m.groupValues[6].trim().ifBlank { m.groupValues[3].trim() }
            if (endYear.isNotEmpty()) "el $endDay de $endMonthTok del $endYear"
            else "el $endDay de $endMonthTok"
        }

        working = dayRangePattern.replace(working) { m ->
            val relQualifier = m.groupValues[3].trim()
            val monthTok = m.groupValues[4].trim().lowercase()
            when {
                relQualifier.isNotEmpty() -> "el ${m.groupValues[2]} del $relQualifier"
                monthTok.isNotEmpty() && monthTok in months -> {
                    val endDay = m.groupValues[2]
                    val year = m.groupValues[5].trim()
                    if (year.isNotEmpty()) "el $endDay de $monthTok del $year"
                    else "el $endDay de $monthTok"
                }
                else -> m.value
            }
        }

        // Rango con mes en el extremo INICIAL y día de CIERRE suelto: "del 15 de diciembre
        // al 20", "entre el 15 de diciembre y el 20", "del 31 de diciembre al 2". Va DESPUÉS
        // de crossMonth/dayRange (ésos exigen mes en la posición del cierre) y ANTES de
        // monthNamePattern para anclar al CIERRE reescribiendo a "el D2 de <mesCierre>
        // [del <añoCierre>]". El mes de CIERRE: si D2 >= D1 es el mismo mes ("del 15 de
        // diciembre al 20" → 20 de diciembre); si D2 < D1 el cierre cruza al mes SIGUIENTE
        // ("del 31 de diciembre al 2" → 2 de enero), con roll de año si diciembre→enero y
        // el año era explícito. Exige mes de inicio válido contra `months`; el lookahead de
        // cierre evita tragarse la forma cross-mes (cierre con su propio mes). "entre...y" primero.
        working = entreStartMonthBareEndDayRangePattern.replace(working) { m ->
            rewriteStartMonthBareEndDayRange(m, startDayIdx = 1, monthIdx = 2, yearIdx = 3, endDayIdx = 4)
        }
        working = startMonthBareEndDayRangePattern.replace(working) { m ->
            rewriteStartMonthBareEndDayRange(m, startDayIdx = 1, monthIdx = 2, yearIdx = 3, endDayIdx = 4)
        }

        // Rango de días de la SEMANA como evento único ("del martes al jueves") → "el jueves"
        // (cierre). Simétrico al dayRange numérico de arriba: reescribe al día FINAL para que
        // [weekdayPattern] ancle al cierre y limpie el título entero. Va ANTES que el rewriter
        // "al <weekday>"→"el <weekday>" (más abajo) y antes de dayListPattern/weekdayPattern,
        // así el par "del X al Y" no se descompone en dos días sueltos (lo que generaría una
        // recurrencia WEEKLY fantasma [1,5] ni dejaba "al" como residuo). Sólo la forma
        // contracta "del ... al ..."; la semana laboral recurrente ("de lunes a viernes") la
        // resuelve parseRecurrence y aquí no se toca.
        working = weekdayPairRangePattern.replace(working) { m ->
            "el ${m.groupValues[2]}"
        }

        // "<día> <mes>" sin conector "de" → "N de <mes>": reutiliza TODO el flujo
        // monthNamePattern (roll de año, clamp de día, acoplamiento con hora,
        // limpieza del título). El mes se valida contra `months`: si el token no es
        // un mes, se deja intacto (no se inventan fechas de contenido).
        working = bareDayMonthPattern.replace(working) { m ->
            val monthTok = m.groupValues[2].lowercase()
            if (monthTok in months) "${m.groupValues[1]} de $monthTok" else m.value
        }

        // "el 15 del 9" → "15/9" (día/mes numérico): reutiliza TODO el flujo
        // numericDatePattern (parseo + limpieza del título + roll + clamp c.146).
        working = dayOfMonthNumericMonthPattern.replace(working) { m ->
            val day = m.groupValues[1]
            val month = m.groupValues[2]
            val year = m.groupValues[3].ifBlank { null }
            if (year != null) "$day/$month/$year" else "$day/$month"
        }

        // Hora aproximada: el usuario capta una hora sin precisión exacta ("llamar a eso
        // de las 5", "reunión sobre las 3 de la tarde", "pasa hacia las 4", "llego cerca
        // de las 10", "cobro alrededor de las 9"). Antes estos marcadores NO se reconocían
        // y la hora subyacente quedaba sin capturar: la tarea caía a `dueAt=null` y se
        // olvidaba, o (con parte del día) la hora sí se resolvía pero el marcador ("sobre
        // las") sobrevivía como residuo en el título → cita bien fechada pero título
        // mutilado. Se normaliza el marcador a la forma canónica "a las"/"a la" para
        // reutilizar TODO el flujo de hora explícita existente (misma resolución AM/PM,
        // misma limpieza del título), sin fingir precisión: la hora es la mejor estimación
        // del usuario. "sobre" es ambiguo (preposición de tema: "sobre las ventas"), así
        // solo se normaliza con hora + evidencia de reloj; el resto de marcadores son
        // inequívocamente temporales con un número.
        // "a eso de" + parte del día ("a eso de la tarde"/"a eso del mediodía"/"a eso de
        // la noche"/"a eso de la madrugada"/"a eso de la mañana"/"a eso de la medianoche"/
        // "a eso de tarde"): se reescribe al conector canónico ("a la tarde"/"al mediodía"/
        // "de tarde") ANTES del fold de [approximateTimePatterns] para que reutilice TODO
        // el flujo de parte-del-día existente (resolución + limpieza) sin dejar "a eso de"
        // como residuo en el título. Antes estas formas cotidianas NO se normalizaban: la
        // parte del día sí se resolvía pero "a eso de" sobrevivía ("pasar recado a eso del",
        // "reunión a eso") → cita bien fechada pero título mutilado (P1). Véase
        // [aEsoDePartOfDayRewriter].
        working = aEsoDePartOfDayRewriter.replace(working) { m ->
            val part = m.groupValues[1]
            when {
                part.startsWith("del mediod") -> "al mediodía"
                part.startsWith("de la ") -> "a la " + part.substring(6)
                else -> part
            }
        }

        // "a eso de" + hora DESNUDA (sin "las"): "a eso de nueve"/"a eso de 9"/"a eso de
        // nueve de la noche" → "a las nueve"/"a las 9"/"a las nueve de la noche", para que
        // [timePatterns] "a las N" resuelva la hora y limpie el título. ANTES del fold de
        // [approximateTimePatterns]: "a eso de las N" no casa aquí (viene "las", no hora
        // desnuda) y lo normaliza el fold. Véase [aEsoDeBareHourRewriter].
        working = aEsoDeBareHourRewriter.replace(working, "a las ")

        // "a partir de" + anclaje temporal de HORA ("a partir de las 3 de la tarde",
        // "a partir de la mañana", "a partir del mediodía/amanecer/atardecer"): se
        // reescribe al conector canónico ("a las"/"a la"/"al") ANTES del fold de
        // [approximateTimePatterns] y del resto del flujo, para que reutilice TODO el
        // mecanismo de hora/parte-del-día existente (resolución + limpieza) sin dejar
        // "a partir de" como residuo en el título. Sólo anclajes de HORA (no fechas de
        // calendario "a partir del viernes"): véase [aPartirDeRewriter]. Los grupos
        // 1/2/3 son las tres formas de anclaje ("las N", "la X", "X"); se emite el
        // conector según cuál case.
        working = aPartirDeRewriter.replace(working) { m ->
            val las = m.groupValues[1]
            val la = m.groupValues[2]
            val bare = m.groupValues[3]
            // Guard anti-cuenta (c.442): cuando el anclaje es "las N" en punto sin
            // evidencia de reloj, lo que sigue puede ser un SUSTANTIVO de cantidad
            // ("a partir de las 3 cajas") y no una cita. Reutiliza [countNounFollowerPattern]
            // (mismo guard que "a las N" en punto, c.361): si el tail NO es un
            // continuador seguro, es una cuenta y NO se reescribe (preserva "a partir de
            // las 3" integro en el titulo en vez de mutilarlo a "cajas"). Las anclas
            // "la X"/parte-del-dia no admiten lectura de cantidad, solo "las N".
            if (las.isNotEmpty()) {
                val tail = working.substring(m.range.last + 1)
                if (!countNounFollowerPattern.containsMatchIn(tail)) return@replace m.value
            }
            when {
                las.isNotEmpty() -> "a $las"
                la.isNotEmpty() -> "a $la"
                else -> "al $bare"
            }
        }

        // Normaliza rangos "entre [las] H1 y [las] H2 [meridiem]" y "de las H1 a las H2
        // [meridiem]" a la forma canónica "de H1 a H2 [meridiem]" ANTES de [timeRangePattern]
        // (que se evalúa más abajo en [parse]): así reutiliza TODO el flujo de rango
        // existente (duración M−N + hora de INICIO como dueAt, propagación de meridiem,
        // cruce de mediodía, guard anti-cuenta) en vez de resolver una sola hora y dejar
        // el marco del rango como residuo del título. Véase [entreRangeNormalizerRewriter]/
        // [deLasRangeNormalizerRewriter]; grupos 1/2/3 = H1[:MM][suf1], 4/5/6 = H2[:MM][suf2].
        fun rebuildRange(m: MatchResult): String {
            val h1 = m.groupValues[1]
            val min1 = m.groupValues[2].let { if (it.isNotEmpty()) ":$it" else "" }
            val suf1 = m.groupValues[3].trim()
            val h2 = m.groupValues[4]
            val min2 = m.groupValues[5].let { if (it.isNotEmpty()) ":$it" else "" }
            val suf2 = m.groupValues[6].trim()
            return "de $h1$min1${if (suf1.isNotEmpty()) " $suf1" else ""} a $h2$min2${if (suf2.isNotEmpty()) " $suf2" else ""}"
        }
        working = desdeRangeNormalizerRewriter.replace(working) { rebuildRange(it) }
        working = entreRangeNormalizerRewriter.replace(working) { rebuildRange(it) }
        working = deLasRangeNormalizerRewriter.replace(working) { rebuildRange(it) }

        // c.436: "desde" + anclaje de HORA ("desde las 3 de la tarde", "desde la
        // mañana", "desde el mediodía/amanecer/atardecer") se reescribe al conector
        // canónico ("a las"/"a la"/"al") ANTES del fold de [approximateTimePatterns]
        // y del resto del flujo, para reutilizar TODO el mecanismo de hora/parte-del-día
        // existente (resolución + limpieza) sin dejar "desde" como residuo en el título.
        // Simétrico de [aPartirDeRewriter]. Se aplica DESPUÉS de los normalizadores de
        // rango ([desdeRangeNormalizerRewriter]/[deLasRangeNormalizerRewriter]) para no
        // desarmar "desde las 9 hasta las 11". Sólo anclajes de HORA: el regex exige
        // "las N"/"la X"/parte-del-día, así "desde el viernes"/"desde mañana" (fecha) y
        // "desde el proyecto" (tema) no se tocan. Para "las N" en punto sin evidencia de
        // reloj se aplica el guard anti-cuenta [countNounFollowerPattern] (c.442): así
        // "desde las 3 cajas" (cantidad) preserva el "3" en el título en vez de
        // mutilarlo a "cajas". Grupos 1/2/3 = "las N"/"la X"/"X"; se emite el conector.
        working = desdeRewriter.replace(working) { m ->
            val las = m.groupValues[1]
            val la = m.groupValues[2]
            val bare = m.groupValues[3]
            // Guard anti-cuenta (c.442): simétrico de [aPartirDeRewriter]. Si el anclaje
            // es "las N" en punto y lo que sigue es un sustantivo de cantidad
            // ("desde las 3 cajas"), NO se reescribe (preserva el número en el título).
            if (las.isNotEmpty()) {
                val tail = working.substring(m.range.last + 1)
                if (!countNounFollowerPattern.containsMatchIn(tail)) return@replace m.value
            }
            when {
                las.isNotEmpty() -> "a $las"
                la.isNotEmpty() -> "a $la"
                else -> "al $bare"
            }
        }

        // c.868: "tipo N" desnudo con evidencia de reloj inmediata ("comida tipo 2 de
        // la tarde") se consume ANTES del fold de [approximateTimePatterns] (que sólo
        // trata la forma con artículo "tipo las N", c.670): se elimina el marcador para
        // que el reloj autónomo resuelva y limpie la hora sin dejar "tipo" de residuo
        // en el título. El guard de evidencia de [bareTipoTimePattern] protege los usos
        // de categoría ("documento tipo 8", "mesa tipo 8 de comedor").
        working = bareTipoTimePattern.replace(working, "")

        working = approximateTimePatterns.fold(working) { acc, p -> p.replace(acc, "a ") }

        // Fracción negativa ANTEPUESTA caribeña/latinoamericana: "cuarto para las 8",
        // "cinco para las 9", "diez para las 3" (forma regional de "a las 8 menos cuarto").
        // La fracción va ANTES del introductor "para", patrón que ni [timePatterns]
        // (la espera DESPUÉS de la hora) ni [paraTimeIntroPattern] (su lookahead exige
        // evidencia de reloj tras la hora) reconocen: caía a dueAt=null (cita olvidada)
        // y "cuarto para las 8" sobrevivía como residuo en el título. Se normaliza a la
        // forma resuelta "a las N menos <fracción>" ANTES de paraTimeIntroPattern, así
        // reutiliza TODO el flujo de hora explícita (resolución AM/PM, wrap 24 h,
        // limpieza del título). El lookahead exige hora válida inmediatamente tras
        // "para las/la" (igual que paraTimeIntroPattern) para no tocar "cinco para las
        // niñas" (destinatario) ni "diez para las 10 personas" (cuenta). El `$1` es la
        // palabra de fracción (ya canonizada por CLOCK_FRACTION_ALT); se reintroduce
        // como "menos $1" para que resolveClockFraction la reste con wrap 24 h.
        working = prefixedNegativeFractionPattern.replace(working) { m ->
            val frac = m.groupValues[1]
            val las = m.groupValues[2] // hora tras "para las"
            val una = m.groupValues[3] // "una" tras "para la"
            val bare = m.groupValues[4] // hora desnuda tras "para" (sin artículo)
            // Guard anti-cuenta: si lo que sigue a la hora NO es un continuador seguro
            // (conjuncion/puntuacion/palabra temporal/fin) NI evidencia de reloj (meridiem/
            // parte del dia), es un sustantivo de cantidad ("diez para las 10 cajas") y NO
            // se reescribe. A diferencia de paraTimeIntroPattern, aqui el meridiem va
            // DESPUES de la hora y debe aceptarse como continuador legitimo.
            val tail = working.substring(m.range.last + 1)
            if (!prefixedFractionFollowerPattern.containsMatchIn(tail)) return@replace m.value
            val intro = when {
                las.isNotEmpty() -> "a las $las"
                una.isNotEmpty() -> "a la $una"
                else -> "a las $bare"
            }
            "$intro menos $frac"
        }

        // Introductor de hora directo "para las/la" → "a las/la" (simétrico de los marcadores
        // aproximados de arriba, pero con hora exacta). Reutiliza TODO el flujo de hora
        // explícita. Véase [paraTimeIntroPattern]: el lookahead exige evidencia de reloj para
        // no agendar destinatarios/cantidades ("para las 9 personas") como cita.
        working = paraTimeIntroPattern.replace(working, "a ")

        // "las N" DESENUDA (sin introductor) → "a las N" para que [timePatterns] la resuelva
        // y limpie el título. Se aplica DESPUÉS de todos los rewriters de conector
        // (aEsoDe/aPartirDe/desde/rangos/para/aproximados) para que sólo toquen "las N"
        // genuinamente sin conector. Para no eludir guards existentes (anti-cuenta de
        // "para las 9", "antes/después de las 5" sin meridio, y la cadencia "todas/todos las
        // N <unidades>") se comprueba el prefijo inmediato: si la palabra previa es un
        // conector temporal o determinante de cadencia ya cubierto, NO se reescribe.
        // Véase [bareLasHourRewriter]: el guard anti-cuenta preserva las cuentas
        // ("compra las 3 manzanas").
        working = bareLasHourRewriter.replace(working) { m ->
            // Prefijo inmediato (lo que precede a " las N"): si termina en un conector
            // temporal o determinante de cadencia YA gestionado, se deja intacto.
            val prefix = working.substring(0, m.range.first)
            val prevWord = Regex("""(?i)\b(\S+)\s*$""").find(prefix)?.groupValues?.get(1)?.lowercase()
            // "de" cubre "antes de las N", "después de las N", "a partir de las N",
            // "cerca de las N", "alrededor de las N" (todos con guard propio); "para" cubre
            // paraTimeIntroPattern (guard anti-cuenta "para las 9 personas"); "todas"/"todos"
            // cubren la cadencia ("todas las dos semanas"); "desde"/"hasta"/"a" cubren los
            // conectores de rango y la forma canónica. Si la palabra previa es una de estas,
            // el conector la gobierna: no tocar.
            if (prevWord != null && prevWord in BARE_LAS_HOUR_GUARDED_PREFIXES) return@replace m.value
            val ts = m.groupValues[1]
            val hasMinutes = m.groupValues[2].isNotBlank()
            // ¿Hora escrita ("nueve"/"doce")? No es cantidad (las cuentas van con dígitos).
            val firstTok = ts.trim().substringBefore(' ').substringBefore(':').substringBefore(',')
                .substringBefore('.')
            val isWrittenHour = firstTok.toIntOrNull() == null
            // Evidencia de reloj: :MM, meridiana, fracción, unidad horas/hs/h, "en punto",
            // aproximadores, u hora escrita. Si la hay la hora es inequívoca → siempre.
            val hasEvidence = hasMinutes || isWrittenHour ||
                ts.contains(":") ||
                Regex("""(?i)\bhoras?\b|\bhs\b|\bh\b|a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada)|del\s+mediod[ií]a|y\s+(?:media|cuarto|pico|\d)|menos\s+(?:cuarto|cinco|diez|veinte|veinticinco|media|treinta|cuarenta|cincuenta|tres cuartos|\d)|en\s+punto|m[aá]s\s+o\s+menos|aproximadamente|y\s+pico|pasad[ao]s?|justo""")
                    .containsMatchIn(ts)
            if (hasEvidence) return@replace "a " + m.value
            // Hora en punto desnuda: guard anti-cuenta. Si el tail NO es un continuador
            // seguro, es un sustantivo de cantidad → no reescribir (preserva el número).
            val tail = working.substring(m.range.last + 1)
            if (!countNounFollowerPattern.containsMatchIn(tail)) return@replace m.value
            "a " + m.value
        }

        val lower = working.lowercase()
        val trailingPriorityWord = trailingPriorityPattern.find(lower)
            ?.takeIf { !negatedPriorityPattern.containsMatchIn(lower) }
            ?.groupValues?.get(1)
        val priority = when {
            "!urgente" in lower || "#urgente" in lower -> TaskPriority.URGENT
            "!alta" in lower || "#alta" in lower -> TaskPriority.HIGH
            "!baja" in lower || "#baja" in lower -> TaskPriority.LOW
            // "urgente"/"importante" como palabra INICIAL (ej. "urgente enviar documento
            // mañana", "importante llamar al cliente") sin prefijo. No se detecta a mitad
            // de frase para evitar falsos positivos como "no es urgente"/"no es importante".
            leadingPriorityPattern.find(lower)?.groupValues?.get(1) == "urgente" -> TaskPriority.URGENT
            leadingPriorityPattern.containsMatchIn(lower) -> TaskPriority.HIGH
            // "urgente"/"importante" como palabra FINAL: sufijo de prioridad en texto libre
            // ("Llamar mamá urgente"), salvo negación ("no es urgente").
            trailingPriorityWord == "urgente" -> TaskPriority.URGENT
            trailingPriorityWord == "importante" -> TaskPriority.HIGH
            else -> TaskPriority.NORMAL
        }
        working = working.replace(Regex("""(?i)(?:!|#)(urgente|alta|baja)\b"""), " ")
            .replace(leadingPriorityPattern, " ")
        if (trailingPriorityWord != null) {
            working = working.replace(trailingPriorityPattern, " ")
        }

        // Recordatorio "N antes" (se extrae antes que la duración para no confundir unidades).
        // Acepta dígitos o números escritos ("dos horas antes") y fracciones
        // comunes ("media hora antes", "un cuarto de hora antes"). Antes solo
        // funcionaba con dígitos, así que las formas escritas quedaban en null.
        val reminderOffsetMinutes = reminderPatterns.asSequence()
            .mapNotNull { it.find(working) }
            .minByOrNull { it.range.first }
            ?.let { match ->
                val amountStr = match.groupValues[1].trim().lowercase()
                // Los patrones de cantidad+unidad exponen la unidad como grupo 2;
                // los de fracción ("media hora") solo tienen grupo 1.
                val unit = match.groupValues.getOrNull(2)?.lowercase().orEmpty()
                // Fracción "media hora" = 30, "(un) cuarto de hora" = 15. Se detecta por
                // contenido (no por igualdad exacta) para tolerar el artículo femenino
                // "una media hora" (c.385): antes `amountStr == "media hora"` fallaba y
                // caía a parseWrittenNumber("una media hora") → null → recordatorio perdido.
                val isFraction = amountStr.contains("media") || amountStr.contains("cuarto")
                // Fracciones: media hora = 30 min, cuarto de hora = 15 min.
                val amount = when {
                    amountStr.contains("media") -> 30L
                    amountStr.contains("cuarto") -> 15L
                    else -> parseWrittenNumber(amountStr) ?: return@let null
                }
                val minutes = when {
                    isFraction -> amount
                    unit.startsWith("min") -> amount
                    unit.startsWith("hora") -> amount * 60
                    else -> amount * 24 * 60
                }
                minutes.toInt().coerceIn(1, 60 * 24 * 30)
            }
        // Se materializan los rangos ANTES de mutar `working` (findAll es perezoso y
        // reasignar `working` dejaría los rangos siguientes obsoletos) y se blanquean
        // en orden descendente para no desplazar los índices anteriores. Sustituye
        // SOLO el rango del match (no `replace(it.value, ...)` global), que corrompería
        // el título si el token aparece como subcadena de una palabra de contenido
        // (p. ej. token "quincena" dentro de "quincenal", "semana" en "semanal").
        //
        // c.474: cuando el match termina en "antes"/"anticipación"/"adelanto" (formas
        // con cantidad: "2 horas antes", "15 min de anticipación"), va seguido casi
        // siempre de un genitivo "de <contenido>" que indica QUÉ se avisa
        // ("avísame 2 horas antes de la reunión", "recuérdame 30 min antes de la
        // cita"). La unidad léxica es "antes de": blanquear solo "antes" deja el
        // conector "de" huérfano y el título queda corrupto ('de la reunión'). Aquí
        // se extiende el rango a blanquear para consumir también el "de"/"del"
        // inmediatamente posterior. Es seguro: un match de reminderPatterns exige
        // cantidad+unidad, así que "antes del 30" (plazo puro, sin cantidad) NO casa
        // aquí — su "del" sigue gestionado por la limpieza de "antes del" (c.4342).
        // Solo se consume un único "de"/"del" para no devorar preposiciones de
        // contenido más allá del genitivo del aviso.
        reminderPatterns.forEach { pattern ->
            val matches = pattern.findAll(working).toList()
            for (m in matches.sortedByDescending { it.range.first }) {
                var end = m.range.last + 1
                val matched = m.value
                val endsInAntesToken = Regex("""(?i)(antes|anticipaci[oó]n|adelanto)\s*$""").containsMatchIn(matched)
                if (endsInAntesToken && end <= working.lastIndex) {
                    val afterTail = working.substring(end)
                    val leadWs = afterTail.takeWhile { it.isWhitespace() }.length
                    val deMatch = Regex("""(?i)^de(?:l|la|los|las)?\b""").find(afterTail.substring(leadWs))
                    if (deMatch != null) {
                        // c.805: NO consumir el conector si lo que sigue arranca un
                        // ancla de fecha/hora digital (día del mes "del 25", hora
                        // "de las 5"): blanquearlo destruía el plazo ("pago tres días
                        // antes del 25" → dueAt=null, vencimiento olvidado, P1). Las
                        // anclas con nombre (weekdayPattern acepta el día suelto) se
                        // resuelven sin el conector, así que el guard sólo protege
                        // el caso digital; el genitivo de contenido ("antes de la
                        // reunión") sigue consumiendo "de/de la/..." para un título limpio.
                        val rest = afterTail.substring(leadWs).removePrefix(deMatch.value).trimStart()
                        val startsDigitalAnchor = rest.firstOrNull()?.isDigit() == true
                        if (!startsDigitalAnchor) {
                            end += leadWs + deMatch.range.last + 1
                        }
                    }
                }
                working = working.replaceRange(m.range.first, end, " ")
            }
        }

        // ¿El usuario pidió un recordatorio pero sin cantidad explícita? Sirve para
        // (a) limpiar el verbo del título y (b) aplicar un offset de respaldo cuando
        // haya fecha límite. Se detecta tras extraer los recordatorios con cantidad,
        // así "recuérdame 2 horas antes" (offset explícito) NO cae aquí.
        val hasBareReminderVerb = bareReminderVerbPattern.containsMatchIn(working)

        // Conector de plazo "hasta" + fecha/hora: la fecha subyacente se resuelve bien,
        // pero el conector "hasta" sobrevivía como residuo en el título ("entregar hasta")
        // porque los patrones de fecha (weekdayPattern/dayOfMonthPattern/weekendPattern)
        // y los relativos ("dentro de 3 días"/"mañana") consumían la fecha ANTES del
        // borrado de "hasta el", dejándolo huérfano (contenido capturado degradado, P1).
        // Simétrico a c.134 ("de aquí a/al"). Se procesa ANTES que los relativos y las
        // fechas específicas para que el marcador temporal aún esté presente. Dos rewrites:
        //  · HORA: "hasta las 5"/"hasta la una" → "a las 5"/"a la una" (timePatterns exige
        //    el conector "a"); así la hora se resuelve Y se limpia del título de golpe.
        //  · FECHA: "hasta el viernes"/"hasta fin de mes"/"hasta mañana"/"hasta dentro de
        //    3 días" → se borra "hasta " dejando la fecha intacta para su patrón.
        // c.532: el lookahead negativo `(?!(?:las\s+\d{1,2}|la\s+(?:una|\d)))` excluye los
        // anclajes de HORA, que ya los procesa (y guarda contra cuentas) el rewrite de
        // HORA anterior. Sin esta exclusión, la rama `\d{1,2}\b` de FECHA (pensada para
        // "hasta el 15", día de mes) casaba también "hasta las 5" (hora) y, tras el guard
        // anti-cuenta que PRESERVA "hasta las 5 cajas", re-stripaba "hasta" → "entregar
        // las 5 cajas" (perdía el límite). Ahora la rama de FECHA sólo toca fechas.
        // El lookahead restringe a marcadores temporales reales para preservar "hasta" como
        // límite de acción ("trabajar hasta terminar", "leer hasta la página 50"): allí no
        // hay marcador → no se toca. "final" NO es marcador (sí "fin de"): el lookahead
        // exige "fin(?:es)?\s+de", no "fin" suelto.
        // c.532: guard anti-cuenta en la rama de HORA, simétrico de aPartirDe/desde (c.442)
        // y de [timeMatchIsCountNoun] (c.514). "hasta las 5 cajas"/"hasta las 10 personas"
        // es una CUENTA (límite de cantidad "hasta"), NO una cita: antes este rewrite
        // disparaba SIEMPRE ("hasta las N"→"a las N") sin mirar el tail, así que "entregar
        // hasta las 5 cajas" perdía el sentido de límite y quedaba "entregar a las 5
        // cajas" (el número y el sustantivo sí se preservaban vía [timeMatchIsCountNoun]
        // downstream, PERO el conector "hasta" se corrompía a "a": contenido capturado
        // degradado, P1 título limpio). Ahora se consume "hasta las N"/"hasta la una|\d"
        // (grupo 1) y se reescribe a "a $1" SÓLO cuando NO es cuenta: si lo que sigue al
        // "las N" en punto es un sustantivo plural de cantidad (cajas/personas/habitaciones/
        // invitaciones) que NO sea la unidad horaria "horas/hs/h", se preserva "hasta las
        // N" íntegro. Las horas CON evidencia de reloj (:MM, meridiem, parte del día,
        // fracción "y media", sufijo "horas/hs/h", "en punto") o al final de frase siguen
        // reescribiéndose como antes (consistente con el baseline "a las N <plural>" de
        // c.514: "hasta las 5 horas"→"a las 5 horas"→05:00; "hasta las 5 cajas"→count).
        working = working
            .replace(Regex("""(?i)\bhasta\s+(las\s+\d{1,2}|la\s+(?:una|\d))""")) { m ->
                val tail = working.substring(m.range.last + 1)
                if (hastaHourTailIsCountNoun(tail)) m.value else "a ${m.groupValues[1]}"
            }
            .replace(
                Regex(
                    """(?i)\bhasta\s+(?!(?:las\s+\d{1,2}|la\s+(?:una|\d)))(?=(?:el|la|los|las)\s+(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|primer|primero|segundo|tercer|tercero|cuarto|[uú]ltim[oa]?|semana|mes(?:es)?|a[ñn]os?|\d{1,2}\b)|fin(?:es)?\s+de\b|ma[nñ]ana\b|hoy\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b|dentro\s+de\b|en\s+(?:\d|un|una|unos|unas))""",
                ),
            ) { m ->
                // c.1075: «hasta ayer/anteayer/antier» al INICIO del texto es
                // genitivo de rango narrativo («hasta ayer trabajé en el
                // proyecto»), nunca un plazo (nadie manda algo "hasta ayer"):
                // se conserva íntegro; fecha y título fluyen del flag
                // [ayerRangeGenitiveNarrative]. «hasta ayer» NO inicial sigue
                // consumiéndose como plazo vencido (doctrina vigente).
                val tail = working.substring(m.range.last + 1)
                if (m.range.first == 0 && ayerRangeGenitiveDayHead.containsMatchIn(tail)) m.value else " "
            }
            // Conector de plazo "antes/después de/del" + fecha/hora: simétrico a "hasta"/c.134. La
            // fecha subyacente se resuelve bien, pero el conector sobrevivía como residuo en
            // el título ("enviar antes", "llamar las", "llegar después de") porque el patrón de
            // fecha/hora consumía la fecha ANTES del borrado tardío del conector. Se procesa aquí,
            // ANTES que los patrones de fecha/hora.
            //  · HORA: "antes/después de las 5 de la tarde" → "a las 5 de la tarde" (timePatterns
            //    exige "a"). "después" ancla al inicio honesto de esa franja (igual que
            //    "después del almuerzo"→14:00): anclar a la hora dicha es la mejor estimación,
            //    no precisión fingida. Se exige meridio/parte del día tras la hora para NO tocar
            //    la forma ambigua "antes/después de las 5" (5am/5pm): esa queda sin resolver,
            //    igual que antes (sin regresión), en vez de fijar un 05:00 pasado y engañoso.
            //  · FECHA: "antes del viernes"/"antes de mañana" → se borra "antes del?/de "
            //    dejando el día (weekdayPattern admite weekday suelto). Se EXCLUYE \d (día del
            //    mes): "antes del 30" lo resuelve beforeDeadlineDayPattern y "antes del 15 de
            //    agosto" monthNameDate (ambos ya limpios); tocarlos aquí los rompería.
            //    "después" sólo se trata en HORA (meridio presente): "después del viernes"
            //    (semántica difusa de inicio, no plazo) se deja intacto para no fingir un
            //    vencimiento; su residuo se limpia cuando la hora ya se resolvió (caso HORA).
            //  · EVIDENCIA DE RELOJ (c.603): antes sólo se aceptaba meridio/parte del día como
            //    prueba de que la hora NO es la ambigua "las 5" (5am/5pm). Pero la hora en forma
            //    de reloj inequívoca —HH:MM ("antes de las 18:30"), sufijo "horas/hs/h", "en
            //    punto" o fracción "y media/cuarto"— TAMBIÉN la resuelve timePatterns sin ambigüedad
            //    (18:30 no es 06:30; "18 horas" es 24h). Sin embargo el conector sobrevivía como
            //    residuo sucio en el título ("enviar antes de las") porque el lookahead no admitía
            //    esas evidencias, y además "antes de las 18 horas" ni siquiera llegaba a resolver.
            //    Ahora el lookahead acepta, además del meridio, cualquiera de esas evidencias de
            //    reloj, dejando el conector simétrico a "hasta las 18:30" (limpio) y resolviendo
            //    la hora. La forma ambigua "antes/después de las 5" (SIN evidencia) sigue SIN
            //    casar → dueAt=null, sin regresión (guard c.237/c.432).
            .replace(
                Regex(
                    """(?i)\b(?:antes|despu[eé]s)\s+de\s+(?=(?:las\s+\d{1,2}|la\s+una)\b\s*(?::\d{2}(?:\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a|horas?\b|hs\b|h\b))?|a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a|horas?\b|hs\b|h\b|en\s+punto\b|y\s+(?:media\b|cuarto\b|tres\s+cuartos\b)))""",
                ),
                "a ",
            )
            .replace(
                Regex(
                    """(?i)\bantes\s+del?\s+(?=(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana\b|hoy\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b))""",
                ),
                " ",
            )
            // Marcador de plazo "a más tardar" (no later than) + ancla temporal: la fecha
            // se resolvía bien pero el marcador sobrevivía como residuo en el título
            // ("entregar informe a más tardar"). Simétrico del borrado de "antes del/de":
            // se borra sólo cuando hay ancla (weekday/mañana/hoy...); sin ancla ("terminarlo
            // a más tardar") se conserva para no falsificar una fecha que no existe.
            .replace(
                Regex(
                    """(?i)\ba\s+m[aá]s\s+tardar\s+(?:el\s+)?(?=(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana\b|hoy\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b))""",
                ),
                " ",
            )
            // Intensificador de plazo "sin falta" (without fail) pegado a un ancla
            // temporal: la fecha se resolvía bien pero el intensificador sobrevivía
            // como residuo en el título ("pagar la luz sin falta"). Simétrico de
            // "a más tardar": se borra sólo cuando hay ancla adyacente —tras ella
            // ("el viernes sin falta") o antes ("sin falta mañana")—; sin ancla
            // ("pagar la luz sin falta") se conserva para no mutar contenido que
            // quizá no es intensificador ("el informe sin falta de ortografía").
            .replace(
                Regex(
                    """(?i)\b(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana|hoy|pasado\s+ma[nñ]ana|antepasad[oa]\s+ma[nñ]ana)\s+sin\s+falta\b""",
                ),
                "$1",
            )
            .replace(
                Regex(
                    """(?i)\bsin\s+falta\s+(?=(?:el\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana\b|hoy\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b))""",
                ),
                " ",
            )
            // Enfático "mismo" pegado a "hoy"/"mañana" ("hoy mismo", "mañana
            // mismo"): la fecha se resolvía bien pero el enfático sobrevivía
            // como residuo en el título ("terminar el informe mismo"). Simétrico
            // de "sin falta": se borra sólo tras el ancla; "ahora mismo"/"ya
            // mismo" ya los cubre su patrón y "mismo" no adyacente ("el mismo
            // lugar", "el mismo día") es contenido y se conserva íntegro.
            .replace(
                Regex(
                    """(?i)\b(hoy|ma[nñ]ana)\s+mismo\b""",
                ),
                "$1",
            )
            // Enfático "misma" tras ancla parte-del-día con preposición ("por la
            // mañana misma", "en la tarde misma"): la parte del día se resolvía
            // bien pero el enfático sobrevivía como residuo en el título
            // ("avisar a Juan misma"). Hermana femenina de "hoy/mañana mismo":
            // se borra SÓLO tras preposición + "la" + parte del día; sin
            // preposición ("la mañana misma del accidente") o con genitivo a
            // continuación ("en la mañana misma del accidente") es contenido y
            // se conserva íntegro (guard (?!\s+(?:de|del)\b)).
            .replace(
                Regex(
                    """(?i)\b((?:a|de|por|en|entrando|entrada|durante)\s+la\s+(?:tarde|noche|madrugada|ma[nñ]ana))\s+misma\b(?!\s+(?:de|del)\b)""",
                ),
                "$1",
            )
            // Marcador de intención coloquial "de cara a(l)" (with an eye to)
            // pegado a un ancla de día ("de cara al lunes", "de cara a mañana"):
            // la fecha se resolvía bien pero el marcador sobrevivía como residuo
            // gramaticalmente colgado en el título ("...de cara"). Hermano
            // leading de "como muy tarde": se borra SÓLO cuando va seguido
            // DIRECTAMENTE de un ancla de día; con sustantivo de contenido a
            // continuación ("de cara al examen del viernes", "de cara a la
            // maratón") se conserva íntegro (lookahead restringido a anclas).
            .replace(
                Regex(
                    """(?i)\bde cara a(?:l| la)?\s+(?=(?:lunes|martes|mi[ée]rcoles|jueves|viernes|s[áa]bado|domingo|hoy|pasado ma[nñ]ana|ma[nñ]ana|fin de semana|finde)\b)""",
                ),
                "",
            )
            // Marcador de plazo coloquial "como muy tarde" (no later than) pegado
            // a un ancla temporal: la fecha se resolvía bien pero el marcador
            // sobrevivía como residuo en el título ("pagar la renta como muy
            // tarde"). Hermano directo de "a más tardar" y simétrico de "sin
            // falta": se borra sólo cuando hay ancla adyacente —tras ella
            // ("mañana como muy tarde") o antes ("como muy tarde mañana")—; sin
            // ancla ("terminarlo como muy tarde") se conserva para no mutar
            // contenido que quizá no es marcador ("llegó como muy tarde a la
            // reunión").
            .replace(
                Regex(
                    """(?i)\b(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana|hoy|pasado\s+ma[nñ]ana|antepasad[oa]\s+ma[nñ]ana)\s+como\s+muy\s+tarde\b""",
                ),
                "$1",
            )
            .replace(
                Regex(
                    """(?i)\bcomo\s+muy\s+tarde\s+(?=(?:el\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana\b|hoy\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b))""",
                ),
                " ",
            )

        // Fecha relativa COMPUESTA fraccionaria ("en una hora y media"/"en 2 horas y
        // cuarto"): se procesa ANTES que [relativePattern] para que este no robe solo
        // "en una hora" (+60) y deje "y media" como residuo en el título. Resuelve
        // now + amount×60 + (30 | 15) min y consume la frase completa.
        val compoundFractionalRelativeMatch = compoundFractionalRelativePattern.find(working)
        val compoundFractionalRelativeDueAt = compoundFractionalRelativeMatch?.let { match ->
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
            val frac = match.groupValues[2].lowercase()
            val extra = when {
                frac.startsWith("tres") -> 45L
                frac.startsWith("dos") -> 30L
                frac.startsWith("media") -> 30L
                else -> 15L
            }
            now + (amount * 60 + extra) * 60_000L
        }
        compoundFractionalRelativeMatch?.let { working = working.replaceRange(it.range, " ") }
        // Fecha relativa multi-cuarto ("en tres cuartos de hora" → 45 min): procesada
        // antes que la duración para que no quede sin vencimiento ni residuo en el título.
        val multiQuarterRelativeMatch = multiQuarterRelativePattern.find(working)
        val multiQuarterRelativeDueAt = multiQuarterRelativeMatch?.let { match ->
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
            val extraQuarter = if (match.value.contains(Regex("""(?i)\sy\scuarto\b"""))) 1L else 0L
            now + (amount + extraQuarter) * 15 * 60_000L
        }
        multiQuarterRelativeMatch?.let { working = working.replaceRange(it.range, " ") }

        // Fecha relativa VAGA "en un rato"/"dentro de un rato"/"de aquí a un rato"
        // → +1 h. Se procesa ANTES que [relativePattern] para robar la frase completa
        // (si no, "en un rato" no casa y queda en el título sin agendar recordatorio).
        val vagueRelativeMatch = vagueRelativePattern.find(working)
        val vagueRelativeDueAt = vagueRelativeMatch?.let { now + 60 * 60_000L }
        vagueRelativeMatch?.let { working = working.replaceRange(it.range, " ") }

        // "Ahora" inmediato ("ahora mismo"/"ahorita"/"lo antes posible"/...)
        // → vence ahora: la tarea sale a la superficie en "What Now" y puede recordar.
        // Se procesa después de [vagueRelativePattern] (por si una frase los combina)
        // y consume la frase completa para no dejar residuo en el título.
        // Guard narrativo c.1027: «ya» suelto seguido de pretérito inequívoco
        // («ya sonó la alarma») NO es inmediatez sino relato de un hecho
        // cumplido: el ancla se suprime (sin fecha falsa ni título mutilado).
        // c.1037: la MISMA guard (misma regex de sufijo, sin duplicarla —
        // doctrina c.1016) cubre «ahora»/«ahorita» + pretérito, acabativo
        // («ahora llegó el cartero» = recién llegó). Los comandos en
        // presente/imperativo/infinitivo («ahora llamo», «hazlo ahora») y la
        // frase completa «ahora mismo» siguen anclando.
        val nowMatch = nowPattern.find(working)?.takeUnless { match ->
            match.value.trim().let {
                it.equals("ya", ignoreCase = true) ||
                    it.equals("ahora", ignoreCase = true) ||
                    it.equals("ahorita", ignoreCase = true)
            } &&
                yaPreteriteNarrativeSuffix.containsMatchIn(working.substring(match.range.last + 1))
        }
        val nowDueAt = nowMatch?.let { now }
        nowMatch?.let { working = working.replaceRange(it.range, " ") }

        // "Más tarde"/"más rato"/"después" (adverbio suelto, sin "de/del/de la" detrás)
        // → +3 h: aproxima "más tarde" a "esta tarde". Se procesa tras now/vague para no
        // robarles sus frases y consume la frase para dejar el título limpio.
        val laterRelativeMatch = laterRelativePattern.find(working)
        val laterRelativeDueAt = laterRelativeMatch?.let { now + 3 * 60 * 60_000L }
        laterRelativeMatch?.let { working = working.replaceRange(it.range, " ") }

        // Idioma "de hoy en ocho/quince/N (días)" sin unidad → now + N días. Se
        // procesa ANTES que [relativePattern] para robar la frase completa (si no,
        // "de hoy en ocho" no casa nada, "hoy" agenda hoy y "en ocho" queda de
        // residuo en el título). Con unidad explícita no-día ("de hoy en 8 horas")
        // el lookahead del patrón falla y la forma la captura [relativePattern].
        val deHoyEnIdiomMatch = deHoyEnIdiomPattern.find(working)
        val deHoyEnIdiomDueAt = deHoyEnIdiomMatch?.let { match ->
            val days = match.groupValues[1].toLongOrNull()
                ?: parseWrittenNumber(match.groupValues[1])?.toLong() ?: 0L
            now + days * 24 * 60 * 60_000L
        }
        deHoyEnIdiomMatch?.let { working = working.replaceRange(it.range, " ") }

        // Fecha relativa "en/dentro de N minutos/horas/días" (N = dígitos o palabra).
        // Guard c.849: con artículo indefinido se descarta el candidato cuya unidad
        // va seguida de una palabra de contenido (frase nominal: "en una semana
        // difícil"); se toma el primer candidato que NO secuestra contenido.
        val relativeMatch = relativePattern.findAll(working)
            .firstOrNull { !articleRelativeHijacksContent(it, working) }
        val relativeDueAt = relativeMatch?.let { match ->
            // La cantidad admite parte decimal ("en 1.5 horas"/"en 2,5 días", forma
            // habitual con coma decimal en español). Antes el patrón solo aceptaba
            // enteros: "en 1.5 horas" NO casaba → caía a la duración → dueAt=null
            // (tarea olvidada, sin recordatorio posible) y título corrupto. Ahora se
            // resuelve el decimal a milisegundos redondeando al minuto.
            val rawAmount = match.groupValues[1].replace(',', '.')
            val amount = rawAmount.toDoubleOrNull()
                ?: parseWrittenNumber(match.groupValues[1])?.toDouble() ?: 0.0
            val unit = match.groupValues[2].lowercase()
            // "y media"/"y medio" (grupo 3): suma media unidad. Si la unidad base son
            // `unitDays` días, media unidad = unitDays/2 días. Aplica por igual a todas
            // las unidades: para "minutos" es medio minuto (inhabitual, inofensivo); lo
            // idiomático es día/semana/mes/quincena/año ("en una semana y media").
            val unitDays = when {
                unit.startsWith("min") -> 0L
                unit.startsWith("hora") -> 0L
                unit.startsWith("quincena") -> 15L
                unit.startsWith("semana") -> 7L
                // "bimestre"/"semestre"/"trimestre" contienen "mes": van antes que "mes".
                unit.startsWith("bimestre") -> 60L
                unit.startsWith("trimestre") -> 90L
                unit.startsWith("semestre") -> 180L
                unit.startsWith("mes") -> 30L
                unit.startsWith("a") || unit.contains("añ") -> 365L
                else -> 1L
            }
            val baseMillis = when {
                unit.startsWith("min") -> (amount * 60_000L).toLong()
                unit.startsWith("hora") -> (amount * 60 * 60_000L).toLong()
                else -> (amount * unitDays * 24 * 60 * 60_000L).toLong()
            }
            val halfMillis = if (match.groupValues[3].isNotEmpty()) unitDays * 24 * 60 * 60_000L / 2 else 0L
            now + baseMillis + halfMillis
        }
        relativeMatch?.let { working = working.replaceRange(it.range, " ") }
        // Fecha relativa fraccionaria + cuarto ("en media hora y cuarto" → 45 min,
        // "en un cuarto de hora y cuarto" → 30): se procesa ANTES que
        // [fractionalRelativePattern] para que este no robe solo "en media hora" (+30)
        // y deje "y cuarto" como residuo en el título. base + 15 min.
        val fractionalAndQuarterRelativeMatch = fractionalAndQuarterRelativePattern.find(working)
        val fractionalAndQuarterRelativeDueAt = fractionalAndQuarterRelativeMatch?.let { match ->
            val base = if (match.groupValues[1].lowercase().contains("media")) 30L else 15L
            now + (base + 15L) * 60_000L
        }
        fractionalAndQuarterRelativeMatch?.let { working = working.replaceRange(it.range, " ") }
        // Fecha relativa fraccionaria ("en media hora"/"dentro de un cuarto de hora"):
        // se procesa ANTES que la duración para que [fractionalDurationPattern] no robe
        // "media hora" como duración y deje el prefijo "en" como residuo en el título.
        val fractionalRelativeMatch = fractionalRelativePattern.find(working)
        val fractionalRelativeDueAt = fractionalRelativeMatch?.let { match ->
            val minutes = if (match.groupValues[1].lowercase().contains("media")) 30L else 15L
            now + minutes * 60_000L
        }
        fractionalRelativeMatch?.let { working = working.replaceRange(it.range, " ") }

        // Conector direccional-temporal "de aquí a/al"/"de acá a/al" huérfano: las
        // formas con CANTIDAD ("de aquí a 3 días"/"de aquí a media hora") ya fueron
        // consumidas arriba por [relativePattern]/[fractionalRelativePattern]/etc.
        // Pero la forma con FECHA ESPECÍFICA ("de aquí al viernes", "de aquí a
        // mañana", "de aquí a hoy", "de aquí a la semana que viene", "de aquí al 15")
        // NO casa ningún patrón relativo → el conector sobrevivía como residuo en el
        // título ("entregar de aquí al" aunque la fecha era correcta) e, peor, "de aquí
        // al 15" caía a dueAt=null (dayOfMonthPattern exige "el"/"día", no "al") →
        // vencimiento olvidado (P1). Se reescribe "al"→"el" (así "al 15"→"el 15" casa
        // dayOfMonthPattern y "al viernes"→"el viernes" casa weekdayPattern) y se borra
        // "a"→" " (así "a mañana"→"mañana", "a hoy"→"hoy", "a la semana que viene"→"la
        // semana que viene", todos ya capturados por sus patrones). Se procesa aquí
        // (tras TODOS los relativos, antes de las fechas específicas) para no interferir.
        working = working
            .replace(deAquiConnectorRewriter, "el")
            .replace(deAquiToRewriter, " ")
        // Abreviaturas de días ("lun mie vie", "sab y dom", "lun-mie-vie") → nombre
        // completo, ANTES de cualquier emparejamiento de fecha/día, para que todas las
        // ramas posteriores (dayListPattern, weekdayPattern, rangos, ordinales…) las
        // traten idéntico al nombre completo. La expansión es puramente léxica y no
        // inventa recurrencia: la decide el mismo razonamiento que para "lunes". Ver
        // [weekdayAbbrevRewriter] para la exclusión de "mar".
        working = working.replace(weekdayAbbrevRewriter) { m ->
            weekdayAbbrevToFull[m.groupValues[1].lowercase()] ?: m.value
        }
        // Contracción direccional-temporal "al" + día de la semana SUELTA
        // ("reunión al viernes", "llamar al sábado", "salida al domingo"): "al" = "a" + "el"
        // (contracción), es un introductor temporal tan cotidiano como "el viernes", pero
        // [weekdayPattern] sólo admite los prefijos el|del|de|este (no "al"). Así "al viernes"
        // se capturaba como weekday pelado ("viernes") y el conector "al" sobrevivía como
        // residuo pegado al título ("reunión al" — contenido capturado degradado, P2). También
        // rompía el rango de días "del lunes al viernes": [weekdayPattern].find anclaba al
        // PRIMER día ("del lunes", consumido) y el segundo ("al viernes") nunca se re-emparejaba
        // → título "reunión al" con la fecha anclada al lunes. Se reescribe "al"→"el" igual que
        // el conector "de aquí al" de arriba (así "al viernes"→"el viernes" casa weekdayPattern
        // y la limpieza de título lo consume entero). Se procesa aquí, tras "de aquí/al" (ya
        // reescrito a "el", sin doble proceso) y antes de cualquier emparejamiento de fecha.
        // El lookahead exige un día de la semana real (con optional "próximo/que viene") para
        // no tocar "al" en otros contextos ("ir al cine", "almorzar al mediodía"). "al próximo
        // viernes" y "al viernes que viene" también se normalizan (se conserva el modificador).
        working = working.replace(alWeekdayRewriter) { m -> "el${m.groupValues[1]}" }
        // El "fin de semana" se detecta y se borra ANTES del período próximo para que
        // "fin de semana que viene" no active por error el patrón "semana que viene"
        // (que dejaría el residuo «fin de» en el título). El match se conserva para la
        // resolución de fecha posterior (weekendMatch != null).
        val weekendEarlyMatch = weekendPattern.find(working)
        weekendEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        // "el último viernes del mes" / "el primer lunes de agosto" / "el tercer viernes del mes
        // que viene" / "el último viernes del mes pasado": weekday ORDINAL del mes. Se detecta
        // y borra ANTES que lastPeriodPattern y previousWeekdayReversedPattern para que el
        // weekday no se capture como "último viernes" (viernes anterior), el calificador
        // "del mes"/"de agosto"/"del mes pasado" no quede como residuo Y "del mes pasado" no
        // lo robe lastPeriodPattern como "el mes pasado" suelto (→ fecha now−30d ignorando el
        // ordinal+weekday). El patrón es específico (exige ordinal+weekday+calificador de mes),
        // así que un "el mes pasado" aislado NO casa y lastPeriodPattern sigue manejándolo.
        val lastWeekdayOfMonthMatch = lastWeekdayOfMonthPattern.find(working)
        // Cadencia PRECEDENTE ("cada mes el primer lunes"): si el patrón directo no casó,
        // se intenta el de cadencia-antes. Ambos son excluyentes por posición. Se captura el
        // ordinal+weekday para anclar la recurrencia mensual; se borra SÓLO "el primer lunes"
        // (grupo 1 del patrón precedente) preservando "cada mes"/"mensual" para que
        // parseRecurrence emita MONTHLY.
        val precedingCadenceOrdinalMatch =
            if (lastWeekdayOfMonthMatch == null) precedingCadenceOrdinalPattern.find(working) else null
        // Se consume el ordinal-weekday-mes AHORA (antes de lastPeriodPattern) para que la
        // subfrase "del mes pasado"/"del mes que viene" no la robe lastPeriodPattern u otros
        // patrones de período. La captura ordinalMonthly se construye tras consumir (usa los
        // groupValues del match ya guardados, no el texto vivo).
        if (lastWeekdayOfMonthMatch != null) {
            working = working.replaceRange(lastWeekdayOfMonthMatch.range, " ")
        } else if (precedingCadenceOrdinalMatch != null) {
            val g = precedingCadenceOrdinalMatch.groups[1]!!.range
            working = working.replaceRange(g, " ")
        }
        // "la semana/el mes/el año pasado": período anterior. Se detecta y borra ANTES
        // que previousWeekdayPattern, que de otro modo capturaría "mes"/"semana" como
        // si fuera un día de semana ("el mes pasado" -> grupo1="mes", no es día ->
        // sin fecha y la frase ya borrada -> dueAt=null). Así se captura como período
        // (resta 1 semana/mes/año) y se combina con hora explícita. Se procesa DESPUÉS
        // de lastWeekdayOfMonthPattern: si la frase era "el último viernes del mes
        // pasado", aquél ya consumió la totalidad y aquí no queda nada por robar.
        // c.985-(iii): límite + período pasado se consume ANTES que lastPeriodPattern
        // (si no, éste roba "del mes pasado" y deja "a finales" como residuo).
        val lastPeriodBoundaryMatch = lastPeriodBoundaryPattern.find(working)
        val lastPeriodBoundaryDueAt = lastPeriodBoundaryMatch?.let { m ->
            val bWord = m.groupValues[1].lowercase()
            val period = m.groupValues[2].lowercase()
            val today = base.toLocalDate()
            val bKind = when {
                bWord.startsWith("fin") || bWord.startsWith("últim") || bWord.startsWith("ultim") -> "end"
                bWord.startsWith("med") || bWord == "mitad" -> "mid"
                else -> "start"
            }
            val date = when {
                "semana" in period -> {
                    val prevMonday = today.minusWeeks(1).with(DayOfWeek.MONDAY)
                    when (bKind) {
                        "end" -> prevMonday.plusDays(6)
                        "mid" -> prevMonday.plusDays(2)
                        else -> prevMonday
                    }
                }
                "mes" in period -> {
                    val prev = today.minusMonths(1)
                    when (bKind) {
                        "end" -> prev.withDayOfMonth(prev.lengthOfMonth())
                        "mid" -> prev.withDayOfMonth(15)
                        else -> prev.withDayOfMonth(1)
                    }
                }
                else -> {
                    val prev = today.minusYears(1)
                    when (bKind) {
                        "end" -> prev.withMonth(12).withDayOfMonth(31)
                        "mid" -> prev.withMonth(6).withDayOfMonth(30)
                        else -> prev.withMonth(1).withDayOfMonth(1)
                    }
                }
            }
            DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        }
        lastPeriodBoundaryMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        val lastPeriodMatch = lastPeriodPattern.find(working)
        val lastPeriodDueAt = lastPeriodMatch?.let { m ->
            val text = m.value.lowercase()
            val days = when {
                "semana" in text -> 7L
                "quincena" in text -> 15L
                "mes" in text -> 30L
                "año" in text -> 365L
                else -> 7L
            }
            now - days * 24 * 60 * 60_000L
        }
        lastPeriodMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        // Captura ordinal-mensual unificada (cualquiera de las dos formas). La directa pone
        // ordinal en grupo 1 y weekday en grupo 2; la precedente los pone en 2 y 3 (su grupo 1
        // es el span "el primer lunes"). Se normalizan a campos comunes para anclar el motor.
        val ordinalMonthly: OrdinalMonthlyCapture? = when {
            lastWeekdayOfMonthMatch != null -> OrdinalMonthlyCapture(
                ordinalWord = lastWeekdayOfMonthMatch.groupValues[1],
                weekdayWord = lastWeekdayOfMonthMatch.groupValues[2],
                isNext = lastWeekdayOfMonthMatch.value.lowercase().let { t ->
                    t.contains("que viene") || t.contains("que entra") ||
                        t.contains("próxim") || t.contains("proxim") || t.contains("entrante")
                },
                isPrevious = lastWeekdayOfMonthMatch.value.lowercase().let { t ->
                    t.contains("pasad") || t.contains("anterior")
                } && lastWeekdayOfMonthMatch.groupValues[3].isBlank(),
                monthName = lastWeekdayOfMonthMatch.groupValues[3].takeIf { it.isNotBlank() },
                yearStr = lastWeekdayOfMonthMatch.groupValues[4].takeIf { it.isNotBlank() }
            )
            precedingCadenceOrdinalMatch != null -> OrdinalMonthlyCapture(
                ordinalWord = precedingCadenceOrdinalMatch.groupValues[2],
                weekdayWord = precedingCadenceOrdinalMatch.groupValues[3],
                isNext = false,
                isPrevious = false,
                monthName = null,
                yearStr = null
            )
            else -> null
        }
        // "pasado el lunes" (forma coloquial FUTURA, modificador ANTES de "el <día>"):
        // se borra SÓLO "pasado" dejando "el lunes" para que [weekdayPattern] consuma
        // limpio la fecha (próximo lunes, la que ya se calculaba). Va ANTES que
        // [previousWeekdayPattern]/[previousWeekdayReversedPattern] para consumir
        // "pasado" primero y que éstos no lo vean como residuo. Sólo se borra si el
        // sustantivo es un día de la semana real (valida vía toDayOfWeekOrNull), para
        // no tocar contenido ("pasado el informe") ni "pasado mañana" (no casa: no hay
        // "pasado el" ahí) ni "pasado el mediodía" (mediodía no es weekday).
        val futureWeekdayPostArticleMatch = futureWeekdayPostArticlePattern.find(working)
            ?.takeIf { it.groupValues[1].toDayOfWeekOrNull() != null }
        futureWeekdayPostArticleMatch?.let { m ->
            // Borra solo "pasado" (preservando " el lunes"): replaceRange sobre el span
            // de "pasado" + el espacio que sigue, dejando "el lunes" intacto.
            val start = m.range.first
            val end = m.groups[1]!!.range.first // inicio de "lunes"
            working = working.replaceRange(start, end, "")
        }
        // Ordinal + weekday SUELTO sin calificador de mes ("reunión el primer lunes",
        // "el segundo martes", "el tercer jueves"): los patrones ordinales-mensuales
        // no casan (exigen "del mes"/"de cada mes"/"de <mes>"), y previousWeekdayReversed
        // tampoco (sólo admite último/pasado/anterior). Así weekdayPattern capturaba SÓLO
        // el weekday ("lunes") y dejaba "el primer"/"el segundo" como residuo pegado al
        // título ("reunión el primer" = título corrupto, P2). Un ordinal de semana sin mes
        // es semánticamente inválido/ambiguo (no hay "primer lunes" sin decir de qué mes),
        // así que se degrada honestamente a "el <weekday>" (= próximo lunes) y weekdayPattern
        // lo consume limpio. El lookahead negativo protege por si un calificador de mes
        // sobreviviera (aunque ya fueron borrados arriba). "último" se excluye: "el último
        // lunes" SÍ es fecha pasada válida (previousWeekdayReversed).
        working = looseOrdinalWeekdayPattern.replace(working) { m ->
            if (m.groupValues[2].toDayOfWeekOrNull() == null) return@replace m.value
            "el ${m.groupValues[2]}"
        }
        // "el jueves pasado" / "el último lunes": fecha pasada. Se borra ANTES que
        // weekdayPattern para que el día no se capture como próximo y "pasado" no
        // quede como residuo en el título.
        // Solo se consume del título si el sustantivo es realmente un día de la
        // semana. Antes el borrado era incondicional: "entregar el informe pasado
        // mañana" hacía casar "el informe pasado" (grupo="informe", no es día) y se
        // borraba igual, dejando "mañana" como fecha suelta → cita programada un día
        // antes (P1: fecha de vencimiento errónea, "pasado mañana" roto) y eliminando
        // "informe" del título. Validar el día antes de borrar preserva el título y
        // deja que "pasado mañana" se resuelva correctamente en la rama de fecha.
        val previousWeekdayMatch = previousWeekdayPattern.find(working)
            ?.takeIf { it.groupValues[1].toDayOfWeekOrNull() != null }
        val previousWeekdayReversedMatch = previousWeekdayReversedPattern.find(working)
            ?.takeIf { it.groupValues[1].toDayOfWeekOrNull() != null }
        previousWeekdayMatch?.let { working = working.replaceRange(it.range, " ") }
        previousWeekdayReversedMatch?.let { working = working.replaceRange(it.range, " ") }

        // Fecha relativa PASADA fraccionaria: "hace media hora"/"hace un cuarto de hora"
        // (y compuestas "hace media hora y cuarto"/"hace una hora y media"). Simétrica
        // PASADA de la familia [fractionalRelativePattern]/[compoundFractionalRelativePattern].
        // Se procesa ANTES que [agoPattern] para que este no robe parcialmente "hace un" de
        // "hace un cuarto de hora" (→ "un"=1, unidad vacía → −3 h erróneos) y ANTES que
        // [fractionalDurationPattern] (que robaría "media hora" como DURACIÓN con dueAt=null,
        // corrompiendo el título). Todas son sub-hora: resuelven un instante preciso now−N min
        // (no medianoche), por lo que NO entran en relativeIsDays. Orden: compuesta > +cuarto
        // > simple (match más largo gana, igual que la familia futura).
        val compoundFractionalAgoMatch = compoundFractionalAgoPattern.find(working)
        val compoundFractionalAgoDueAt = compoundFractionalAgoMatch?.let { match ->
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
            val frac = match.groupValues[2].lowercase()
            val extra = when {
                frac.startsWith("tres") -> 45L
                frac.startsWith("dos") -> 30L
                frac.startsWith("media") -> 30L
                else -> 15L
            }
            now - (amount * 60 + extra) * 60_000L
        }
        compoundFractionalAgoMatch?.let { working = working.replaceRange(it.range, " ") }

        val fractionalAndQuarterAgoMatch = fractionalAndQuarterAgoPattern.find(working)
        val fractionalAndQuarterAgoDueAt = fractionalAndQuarterAgoMatch?.let { match ->
            val base = if (match.groupValues[1].lowercase().contains("media")) 30L else 15L
            now - (base + 15L) * 60_000L
        }
        fractionalAndQuarterAgoMatch?.let { working = working.replaceRange(it.range, " ") }

        val fractionalAgoMatch = fractionalAgoPattern.find(working)
        val fractionalAgoDueAt = fractionalAgoMatch?.let { match ->
            val minutes = if (match.groupValues[1].lowercase().contains("media")) 30L else 15L
            now - minutes * 60_000L
        }
        fractionalAgoMatch?.let { working = working.replaceRange(it.range, " ") }

        // "hace un ratito/ratico/momentito" (diminutivo coloquial pasado) -> -3 h.
        // Se procesa ANTES que [agoPattern] para consumir la frase completa y evitar
        // que este robe solo "hace un" (→ -3 h) dejando "ratito" en el título.
        val diminutiveAgoMatch = diminutiveAgoPattern.find(working)
        val diminutiveAgoDueAt = diminutiveAgoMatch?.let { now - 3L * 60 * 60_000L }
        diminutiveAgoMatch?.let { working = working.replaceRange(it.range, " ") }

        // "hace N días/semanas/...": fecha relativa PASADA. Se trata como días
        // relativos (epoch a medianoche) para combinarse con hora explícita
        // ("hace 2 días a las 10"). "hace un rato"/"hace poco" -> -3 h.
        val agoMatch = agoPattern.find(working)
        val agoDueAt = agoMatch?.let { m ->
            val raw = m.groupValues[1].lowercase()
            val unit = m.groupValues[2].lowercase()
            val amount = when {
                raw == "un rato" || raw == "poco" -> 3L * 60 * 60_000L
                else -> (parseWrittenNumber(raw) ?: 1L)
            }
            val millis = when {
                unit.isEmpty() -> 3L * 60 * 60_000L
                unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("hora") -> amount * 60 * 60_000L
                unit.startsWith("semana") -> amount * 7 * 24 * 60 * 60_000L
                unit.startsWith("mes") -> amount * 30 * 24 * 60 * 60_000L
                unit.startsWith("a") || unit.contains("añ") -> amount * 365 * 24 * 60 * 60_000L
                else -> amount * 24 * 60 * 60_000L
            }
            now - millis
        }
        agoMatch?.let { working = working.replaceRange(it.range, " ") }

        // "fin de mes" / "finales de mes" / "mediados de mes": vencimientos mensuales
        // (alquiler, tarjeta, servicios). Se borran ANTES del período próximo para que
        // la subcadena "mes" no active "mes que viene". Se trata como días relativos
        // (epoch a medianoche) para combinarse con hora explícita ("fin de mes a las 18").
        // c.575: "el último día hábil/laborable/laboral del mes" se reconoce ANTES
        // que [endOfMonthPattern]: el adjetivo "hábil" rompe la secuencia genérica
        // "día de/del mes" y, sin esta rama específica, la frase caía sin límite
        // (dueAt=null, título corrupto). Mismo flujo que fin de mes, pero el motor
        // retrocede al viernes si el último día real cae en sábado/domingo.
        val lastBusinessDayEarlyMatch = lastBusinessDayOfMonthPattern.find(working)
        var endOfMonthEarlyMatch = if (lastBusinessDayEarlyMatch != null) null else endOfMonthPattern.find(working)
        val midOfMonthEarlyMatch = midOfMonthPattern.find(working)
        val startOfMonthEarlyMatch = startOfMonthPattern.find(working)
        // c.471: si NO se reconoció un límite "fin de mes" canónico (requiere "de mes")
        // pero hay cadencia mensual explícita, "el último día" suelto se trata como
        // límite de fin de mes (mismo flujo que endOfMonthPattern: borra, fija dueAt a EOM
        // y boundaryKind="end" para que la recurrencia se promueva a MONTHLY+EOM).
        val hasMonthlyCadence = Regex("""(?i)\bmensual(?:es|mente)?\b|\bcada\s+mes\b|\btodos\s+los\s+meses\b""").containsMatchIn(working)
        if (endOfMonthEarlyMatch == null && hasMonthlyCadence) {
            endOfMonthNoMesPattern.find(working)?.let { endOfMonthEarlyMatch = it }
        }
        // Límite mensual ganador (últ-día-hábil > fin > mediados > principios) y su
        // "tipo": sirve para detectar el prefijo "cada" (recurrencia) y extender su
        // borrado en un solo paso. c.575: "último día hábil" gana sobre "fin de mes"
        // (no se solapan: el patrón hábil excluye al genérico vía la rama temprana).
        val boundaryWinner: MatchResult? = lastBusinessDayEarlyMatch ?: endOfMonthEarlyMatch ?: midOfMonthEarlyMatch ?: startOfMonthEarlyMatch
        val boundaryKind: String? = when {
            lastBusinessDayEarlyMatch != null -> "end-business"
            endOfMonthEarlyMatch != null -> "end"
            midOfMonthEarlyMatch != null -> "mid"
            startOfMonthEarlyMatch != null -> "start"
            else -> null
        }
        // Resolución del límite mensual. Si el match trae un mes EXPLÍCITO (grupo 1,
        // p. ej. "fin de mes de octubre") se resuelve a ese mes con
        // `parseMonthBoundaryName` (mismo criterio y roll anual que "finales de
        // octubre"); si no, se usa el mes en curso/que viene (`monthBaseForBoundary`).
        // c.575: retrocede un día dado al último Lunes-Viernes anterior (anclaje de
        // "último día hábil"). Sin festivos locales (jurisdicción desconocida): "hábil"
        // = Lun-Vie; se documenta la limitación en lugar de fingir un calendario.
        fun lastBusinessDayOf(date: LocalDate): LocalDate {
            var d = date
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
            return d
        }
        fun boundaryDueAt(match: MatchResult, kind: String): Long? {
            val namedMonth = months[match.groupValues[1].lowercase()]
            // c.575: "último día hábil" comparte la resolución del último día del mes
            // ("finales") y luego retrocede al viernes si ese día cae en sábado/domingo.
            val isBusinessDay = kind == "end-business"
            val effectiveKind = if (isBusinessDay) "end" else kind
            val qualifier = when (effectiveKind) {
                "end" -> "finales"
                "mid" -> "mediados"
                else -> "principios"
            }
            if (namedMonth != null) {
                val lastDay = parseMonthBoundaryName(base.toLocalDate(), qualifier, namedMonth, match.groupValues[2])
                if (lastDay == null) return null
                val resolved = if (isBusinessDay) lastBusinessDayOf(lastDay) else lastDay
                return DateRules.toEpochMillis(resolved, LocalTime.of(9, 0), zone)
            }
            val baseMonth = monthBaseForBoundary(base.toLocalDate(), match.value)
            return when (effectiveKind) {
                "end" -> {
                    var lastDay = baseMonth.withDayOfMonth(baseMonth.lengthOfMonth())
                    if (isBusinessDay) lastDay = lastBusinessDayOf(lastDay)
                    DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
                }
                "mid" -> DateRules.toEpochMillis(baseMonth.withDayOfMonth(15), LocalTime.of(9, 0), zone)
                else -> DateRules.toEpochMillis(baseMonth.withDayOfMonth(1), LocalTime.of(9, 0), zone)
            }
        }
        val monthBoundaryDueAt = when (boundaryKind) {
            "end-business" -> boundaryDueAt(lastBusinessDayEarlyMatch!!, "end-business")
            "end" -> boundaryDueAt(endOfMonthEarlyMatch!!, "end")
            "mid" -> boundaryDueAt(midOfMonthEarlyMatch!!, "mid")
            "start" -> boundaryDueAt(startOfMonthEarlyMatch!!, "start")
            else -> null
        }
        // "cada fin/mediados/principios de mes" (c.257): el prefijo "cada" convierte el
        // vencimiento único en recurrencia MONTHLY. Fin→anclaje al último día REAL del
        // mes (EOM, no omite meses cortos); mediados/principios→anclaje al día 15/1 vía
        // dueAt. Se borra "cada <límite>" de golpe para que parseRecurrence no vea "cada".
        // Si el límite nombra un mes EXPLÍCITO ("cada fin de mes de octubre") la frase
        // es contradictoria (un mes concreto no es hábito mensual): se borra igual (sin
        // residuo) pero NO se promueve a recurrencia — prima el vencimiento único del
        // mes nombrado, que ya resolvió `monthBoundaryDueAt`.
        //
        // c.311: además del prefijo "cada <límite>" (cada ANTES del límite), ahora se
        // detecta "cada"/"todos los" DENTRO del match — "el último día de cada mes",
        // "mediados de todos los meses" (cada DESPUÉS de "de/del"). El patrón de límite
        // (c.308) ahora consume esa palabra intercalada, así el título queda limpio; aquí
        // sólo se decide la promoción a recurrencia. Sin esto, "el último día de cada mes"
        // caía a fixedPatterns ("cada mes"→MONTHLY día-de-hoy) con título corrupto
        // ('renta el último día de') y anclaje al día de captura en vez de fin de mes (P1).
        val cadaBoundaryRecurrence: RecurrenceResult? = if (boundaryWinner != null && boundaryKind != null) {
            val before = working.substring(0, boundaryWinner.range.first)
            val cadaPrefix = cadaBoundaryPrefixPattern.find(before)
            // "cada"/"todos los" intercalados entre "de/del" y "mes" dentro del propio match.
            val cadaInBoundaryMatch = Regex("""(?i)\bcada\b|\btodos\s+los\b""").containsMatchIn(boundaryWinner.value)
            val hasNamedMonth = months[boundaryWinner.groupValues[1].lowercase()] != null
            if (cadaPrefix != null) {
                // c.492: el genitivo "de/del" antes del prefijo "cada"/"todos los" introduce
                // el límite temporal ("Balance de cada fin de mes") y debe consumirse junto
                // al límite, igual que en la rama sin "cada". Sin esto, el título queda con
                // residuo "de" ("Balance de").
                val combined = strippedPeriodRange(working, cadaPrefix.range.first..boundaryWinner.range.last)
                working = working.replaceRange(combined, " ")
                if (hasNamedMonth) null
                else when (boundaryKind) {
                    "end-business" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastBusinessDay = true)
                    "end" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastDay = true)
                    else -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList())
                }
            } else if (cadaInBoundaryMatch) {
                // El "cada"/"todos los" va dentro del match (tras "de/del"): se borra sólo
                // el rango del match (que ya incluye la palabra de cadencia) — no hay prefijo
                // externo que extender. Misma promoción que la rama del prefijo. c.492:
                // strippedPeriodRange consume el genitivo "de/del" externo si lo hay.
                working = working.replaceRange(strippedPeriodRange(working, boundaryWinner.range), " ")
                if (hasNamedMonth) null
                else when (boundaryKind) {
                    "end-business" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastBusinessDay = true)
                    "end" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastDay = true)
                    else -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList())
                }
            } else {
                lastBusinessDayEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
                endOfMonthEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
                midOfMonthEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
                startOfMonthEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
                null
            }
        } else null

        // "mediados/finales/principios de [mes nombre]": límite mensual con mes
        // explícito (sin día). Se procesa aquí (tras monthBoundary, antes que
        // monthNamePattern) para consumir la frase completa y evitar residuo/doble.
        val monthBoundaryNameEarlyMatch = monthBoundaryNamePattern.find(working)
        val monthBoundaryNameMonthNum = monthBoundaryNameEarlyMatch?.let { months[it.groupValues[2].lowercase()] }
        val monthBoundaryNameDueAt = monthBoundaryNameEarlyMatch?.let { m ->
            val monthNum = monthBoundaryNameMonthNum ?: return@let null
            parseMonthBoundaryName(base.toLocalDate(), m.groupValues[1], monthNum, m.groupValues[3])
                ?.let { DateRules.toEpochMillis(it, LocalTime.of(9, 0), zone) }
        }
        // Solo se consume la frase si el mes nombrado es realmente un mes válido:
        // "mediados de semana"/"fin de año" no son límites mensuales y deben caer a
        // su handler original.
        if (monthBoundaryNameMonthNum != null) {
            monthBoundaryNameEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        }

        // "en <mes> [de <año>]": nombre de mes suelto tras "en" (sin día explícito).
        // Ancla blanda cotidiana ("viaje en diciembre", "renovar en enero"): se
        // consume aquí (tras [monthBoundaryNamePattern], cuyo calificador gana, y
        // antes de [monthNamePattern], que exige día) para no dejar residuo. Misma
        // resolución que "a inicios de <mes>": día 1, roll anual si ya pasó.
        val bareMonthEarlyMatch = bareMonthPattern.find(working)
        val bareMonthMonthNum = bareMonthEarlyMatch?.let { months[it.groupValues[1].lowercase()] }
        val bareMonthDueAt = bareMonthEarlyMatch?.let { m ->
            val monthNum = bareMonthMonthNum ?: return@let null
            parseMonthBoundaryName(base.toLocalDate(), "inicios", monthNum, m.groupValues[2])
                ?.let { DateRules.toEpochMillis(it, LocalTime.of(9, 0), zone) }
        }
        if (bareMonthMonthNum != null) {
            bareMonthEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        }

        // "para <mes> [de <año>]": plazo de fin de mes (calificador "finales").
        // Tras "en <mes>" para no competir, y tras el calificador explícito
        // [monthBoundaryNamePattern].
        val paraMonthEarlyMatch = paraMonthPattern.find(working)
        val paraMonthMonthNum = paraMonthEarlyMatch?.let { months[it.groupValues[1].lowercase()] }
        val paraMonthDueAt = paraMonthEarlyMatch?.let { m ->
            val monthNum = paraMonthMonthNum ?: return@let null
            parseMonthBoundaryName(base.toLocalDate(), "finales", monthNum, m.groupValues[2])
                ?.let { DateRules.toEpochMillis(it, LocalTime.of(9, 0), zone) }
        }
        if (paraMonthMonthNum != null) {
            paraMonthEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        }

        // "fin de año" / "mediados de año" / "principios de año": vencimientos anuales
        // (cierre fiscal, renovaciones). Se borran ANTES del período próximo para que
        // la subcadena "año" no active "año que viene" como +365d genérico. Días
        // relativos (epoch a medianoche) para combinarse con hora explícita.
        val endOfYearEarlyMatch = endOfYearPattern.find(working)
        val midOfYearEarlyMatch = midOfYearPattern.find(working)
        val startOfYearEarlyMatch = startOfYearPattern.find(working)
        val yearBoundaryDueAt = when {
            endOfYearEarlyMatch != null -> {
                val baseYear = yearBaseForBoundary(base.toLocalDate(), endOfYearEarlyMatch.value)
                val lastDay = baseYear.withMonth(12).withDayOfMonth(31)
                DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
            }
            midOfYearEarlyMatch != null -> {
                val baseYear = yearBaseForBoundary(base.toLocalDate(), midOfYearEarlyMatch.value)
                DateRules.toEpochMillis(baseYear.withMonth(6).withDayOfMonth(30), LocalTime.of(9, 0), zone)
            }
            startOfYearEarlyMatch != null -> {
                val baseYear = yearBaseForBoundary(base.toLocalDate(), startOfYearEarlyMatch.value)
                DateRules.toEpochMillis(baseYear.withMonth(1).withDayOfMonth(1), LocalTime.of(9, 0), zone)
            }
            else -> null
        }
        endOfYearEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        midOfYearEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }
        startOfYearEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        // "este mes" / "este año": plazo blando = fin del mes/año en curso. Se procesa
        // tras los límites anuales/mensuales (que ya consumieron "fin/mediados/... de
        // este mes/año" gracias a los lookbehinds) y ANTES del período próximo para que
        // "mes"/"año" no active "mes/año que viene". Días (epoch medianoche) para
        // combinarse con hora explícita ("este mes a las 18").
        val thisMonthEarlyMatch = thisMonthPattern.find(working)
        val thisMonthDueAt = thisMonthEarlyMatch?.let {
            val lastDay = base.toLocalDate().withDayOfMonth(base.toLocalDate().lengthOfMonth())
            DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
        }
        thisMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

        val thisYearEarlyMatch = thisYearPattern.find(working)
        val thisYearDueAt = thisYearEarlyMatch?.let {
            val lastDay = base.toLocalDate().withMonth(12).withDayOfMonth(31)
            DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
        }
        thisYearEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        // "principios de semana": el lunes más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        // c.489: si el match trae "que viene", se ancla al lunes de la SEMANA PRÓXIMA.
        // No se puede usar nextOrSame(MON)+7d: si hoy NO es lunes, nextOrSame(MON) ya salta
        // al lunes de la semana próxima (el de esta semana ya pasó), y +7d saltaría una
        // semana de más. previousOrSame(MON).plusWeeks(1) da el lunes de la semana próxima
        // sin depender del día de hoy. Sin "que viene", nextOrSame(MON) (hoy/futuro).
        // c.506: se procesa ANTES que thisWeekPattern para que "a principios de esta
        // semana" no sea robado por la alternativa "esta semana" de ese patrón, lo que
        // dejaba "a principios de" como residuo en el título.
        val startOfWeekEarlyMatch = startOfWeekPattern.find(working)
        val startOfWeekDueAt = startOfWeekEarlyMatch?.let {
            // c.506: "proxim[oa]" ancla a la semana proxima, igual que "que viene".
            val nextWeek = "que viene" in it.value.lowercase() ||
                Regex("(?i)pr[oó]xim").containsMatchIn(it.value)
            val monday = if (nextWeek) {
                base.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(1)
            } else {
                base.toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            }
            DateRules.toEpochMillis(monday, LocalTime.of(9, 0), zone)
        }
        startOfWeekEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        // "mediados de semana": el miércoles más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        // c.489: si el match trae "que viene", se ancla al miércoles de la SEMANA PRÓXIMA
        // = (lunes de la semana próxima) + 2 días. Misma razón que startOfWeek: usar
        // nextOrSame(WED)+7d saltaría de más cuando hoy no es miércoles. Sin "que viene",
        // nextOrSame(WED) (hoy/futuro).
        val midOfWeekEarlyMatch = midOfWeekPattern.find(working)
        val midOfWeekDueAt = midOfWeekEarlyMatch?.let {
            // c.506: "proxim[oa]" ancla a la semana proxima, igual que "que viene".
            val nextWeek = "que viene" in it.value.lowercase() ||
                Regex("(?i)pr[oó]xim").containsMatchIn(it.value)
            val wednesday = if (nextWeek) {
                base.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(1)
                    .plusDays(2)
            } else {
                base.toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            }
            DateRules.toEpochMillis(wednesday, LocalTime.of(9, 0), zone)
        }
        midOfWeekEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        // "esta semana el viernes" / "el viernes de esta semana": día de la semana
        // explícito anclado a la SEMANA ACTUAL. Se procesa ANTES que thisWeekPattern
        // para que el plazo blando ("esta semana" → domingo) no robe la frase y se
        // imponga sobre el weekday explícito (probe c.852: "dentista el viernes de
        // esta semana", dicho un viernes, caía en el DOMINGO — fecha errónea, P1).
        // El "de" genitivo precedente ("dentista de esta semana el viernes") se
        // consume vía strippedPeriodRange para no dejar residuo en el título.
        val thisWeekWeekdayReverseMatch = thisWeekWeekdayReversePattern.find(working)
        val thisWeekWeekdayReverseDueAt = thisWeekWeekdayReverseMatch?.let { m ->
            m.groupValues[1].toDayOfWeekOrNull()?.let { target ->
                thisWeekWeekdayDate(base.toLocalDate(), target, zone)
            }
        }
        thisWeekWeekdayReverseMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        val thisWeekWeekdayForwardMatch = thisWeekWeekdayForwardPattern.find(working)
        val thisWeekWeekdayForwardDueAt = thisWeekWeekdayForwardMatch?.let { m ->
            m.groupValues[1].toDayOfWeekOrNull()?.let { target ->
                thisWeekWeekdayDate(base.toLocalDate(), target, zone)
            }
        }
        thisWeekWeekdayForwardMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        // "esta semana" / "esta semana que viene" / "fin de la semana" (con o sin
        // "que viene"): fin de la semana (próximo domingo, ISO lunes→domingo). Se
        // borra ANTES del período próximo para que "semana" no active "semana que
        // viene" y para limpiar "esta semana que viene" / "fin de la semana que viene".
        // c.488: si el match trae "que viene", se ancla al domingo de la SEMANA PRÓXIMA
        // (+7d); sin él, al domingo de esta semana.
        val thisWeekEarlyMatch = thisWeekPattern.find(working)
        val thisWeekDueAt = thisWeekEarlyMatch?.let {
            val baseSunday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            // c.506: "a finales de la proxima semana" / "a finales de la semana que
            // viene" anclan al domingo de la SEMANA PROXIMA. Antes estas formas con
            // determinante ("esta"/"proxima") no casaban y dejaban "a finales de" como
            // residuo en el titulo (c.506). Se trata "proxim[oa]" igual que "que viene".
            val nextWeek = "que viene" in it.value.lowercase() ||
                Regex("(?i)pr[oó]xim").containsMatchIn(it.value)
            val sunday = if (nextWeek) baseSunday.plusWeeks(1) else baseSunday
            DateRules.toEpochMillis(sunday, LocalTime.of(9, 0), zone)
        }
        thisWeekEarlyMatch?.let { working = working.replaceRange(strippedPeriodRange(working, it.range), " ") }

        // "el 15 del mes que viene": día N del mes siguiente. Se procesa ANTES que
        // nextPeriodPattern para consumir la frase completa (día + "mes que viene")
        // y evitar que éste la robe como +30d genérico (fecha errónea) dejando
        // residuo "el N del" en el título.
        // c.344: en "el 15 y el 30 del mes que viene" la lista multi-día debe quedarse
        // entera para parseRecurrence (rutina quincenal anclada al mes siguiente). Sin
        // este guard, nextMonthDayPattern consume "el 30 del mes que viene" como día 30
        // único y deja "el 15 y" roto → la rutina se pierde (P1 de datos). Se descarta
        // el match cuando lo precede una lista multi-día con cadencia.
        val dualDayListRegex = Regex(
            """(?i)(?:(?:los|las)?\s*d[ií]as?\s+\d{1,2}(?:\s*,\s*\d{1,2})*(?:\s+y\s+(?:el|la|los|las)?\s*\d{1,2})+)|(?:(?:el|la)\s+\d{1,2}\s+y\s+(?:el|la)?\s*\d{1,2})"""
        )
        fun precedesDualDayList(idx: Int): Boolean {
            if (idx <= 0) return false
            val before = working.substring(0, idx)
            // La lista multi-día puede aparecer COMPLETA antes del match
            // ("los días 15 y 30  del mes que viene") o el match puede ser el ÚLTIMO
            // elemento de la lista ("el 15 y el 30 del mes que viene" — el match
            // empieza en "el 30", y "el 15 y " queda en `before`). Se detectan ambas:
            // lista completa, o cola "N y (el|la|los|las)? " justo antes del match.
            val fullList = dualDayListRegex.containsMatchIn(before)
            val trailingJoiner = Regex("""(?i)\d{1,2}\s+y\s+(?:el\s+|la\s+|los\s+|las\s+)?$""").containsMatchIn(before)
            return fullList || trailingJoiner
        }
        val nextMonthDayMatch = nextMonthDayPattern.find(working)?.let { m ->
            if (precedesDualDayList(m.range.first)) null else m
        }
        val nextMonthDayDueAt = nextMonthDayMatch?.let { m ->
            val day = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let null
            val today = base.toLocalDate()
            val nextMonth = today.plusMonths(1)
            val dim = nextMonth.lengthOfMonth()
            val safeDay = minOf(day, dim)
            DateRules.toEpochMillis(nextMonth.withDayOfMonth(safeDay), LocalTime.of(9, 0), zone)
        }
        nextMonthDayMatch?.let { working = working.replaceRange(it.range, " ") }

        // Orden inverso: "el mes que viene el 5" / "el mes que viene el día 5".
        // Misma resolución que nextMonthDayDueAt (día N del mes siguiente, clamp al
        // último día válido). Se procesa ANTES que nextPeriodPattern para consumir
        // período+día en una sola frase y evitar que ésta la robe como +30d.
        val nextMonthDayReverseMatch = nextMonthDayReversePattern.find(working)
        val nextMonthDayReverseDueAt = nextMonthDayReverseMatch?.let { m ->
            val day = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let null
            val today = base.toLocalDate()
            val nextMonth = today.plusMonths(1)
            val dim = nextMonth.lengthOfMonth()
            val safeDay = minOf(day, dim)
            DateRules.toEpochMillis(nextMonth.withDayOfMonth(safeDay), LocalTime.of(9, 0), zone)
        }
        nextMonthDayReverseMatch?.let { working = working.replaceRange(it.range, " ") }

        // "el próximo 15" / "próximo 15" / "el próximo día 15": día N del mes SIGUIENTE.
        // Forma corta de "el 15 del mes que viene" SIN "del mes". Se procesa DESPUÉS de
        // nextMonthDay[Reverse] (que consumen la frase completa "N del mes que viene")
        // para no doble-procesar, y ANTES que nextPeriodPattern (que robaría "próximo"
        // como +30d sin día) y que dayOfMonthPattern (que exige "el <dígito>"). Resolución
        // idéntica a nextMonthDayDueAt: día N del mes siguiente con clamp de día imposible.
        val nextMonthDayShortMatch = nextMonthDayShortPattern.find(working)
        val nextMonthDayShortDueAt = nextMonthDayShortMatch?.let { m ->
            val day = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let null
            val today = base.toLocalDate()
            val nextMonth = today.plusMonths(1)
            val dim = nextMonth.lengthOfMonth()
            val safeDay = minOf(day, dim)
            DateRules.toEpochMillis(nextMonth.withDayOfMonth(safeDay), LocalTime.of(9, 0), zone)
        }
        nextMonthDayShortMatch?.let { working = working.replaceRange(it.range, " ") }

        // Orden inverso: "el 15 próximo" → día N del mes siguiente (ver patrón).
        // Se procesa DESPUÉS de nextMonthDayShort (forma directa) para no doble-procesar
        // y ANTES que dayOfMonthPattern (que exigiría "el 15" como de este mes).
        val nextMonthDayShortReverseMatch = nextMonthDayShortReversePattern.find(working)
        val nextMonthDayShortReverseDueAt = nextMonthDayShortReverseMatch?.let { m ->
            val day = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let null
            val today = base.toLocalDate()
            val nextMonth = today.plusMonths(1)
            val dim = nextMonth.lengthOfMonth()
            val safeDay = minOf(day, dim)
            DateRules.toEpochMillis(nextMonth.withDayOfMonth(safeDay), LocalTime.of(9, 0), zone)
        }
        nextMonthDayShortReverseMatch?.let { working = working.replaceRange(it.range, " ") }

        // "la semana que viene el lunes" / "la próxima semana el viernes":
        // día de la semana objetivo de la SEMANA PRÓXIMA. start-of-next-week (próximo
        // lunes estricto) + offset del weekday objetivo. Se procesa ANTES que
        // nextPeriodPattern para consumir período+día y evitar el +7d genérico.
        val nextWeekWeekdayReverseMatch = nextWeekWeekdayReversePattern.find(working)
        val nextWeekWeekdayReverseDueAt = nextWeekWeekdayReverseMatch?.let { m ->
            m.groupValues[1].toDayOfWeekOrNull()?.let { target ->
                nextWeekWeekdayDate(base.toLocalDate(), target, zone)
            }
        }
        nextWeekWeekdayReverseMatch?.let { working = working.replaceRange(it.range, " ") }

        // Orden inverso: "el lunes de la semana que viene". Misma resolución.
        val nextWeekWeekdayForwardMatch = nextWeekWeekdayForwardPattern.find(working)
        val nextWeekWeekdayForwardDueAt = nextWeekWeekdayForwardMatch?.let { m ->
            m.groupValues[1].toDayOfWeekOrNull()?.let { target ->
                nextWeekWeekdayDate(base.toLocalDate(), target, zone)
            }
        }
        nextWeekWeekdayForwardMatch?.let { working = working.replaceRange(it.range, " ") }

        // Período próximo ("la semana que viene", "el mes que viene", "el año que
        // viene", "próximo mes", "la próxima semana"): +1 período (semana/mes/año).
        // Se trata como días relativos (como relativePattern) para combinarse con hora
        // explícita ("el mes que viene a las 10" → fecha +30d a las 10:00).
        val nextPeriodMatch = nextPeriodPattern.find(working)
        // c.344: "los días 15 y 30 del mes que viene" — el calificador "del mes que
        // viene" pertenece a la LISTA multi-día (rutina quincenal anclada al mes
        // siguiente), no a un +30d genérico. Sin este guard, nextPeriodPattern consume
        // "mes que viene" como +30d ANTES de parseRecurrence, dejando el calificador
        // borrado → la lista se anclaba al mes actual (cita en mes erróneo, P1 de
        // datos). Si el período es "mes" y lo precede una lista multi-día con cadencia
        // (plural "los días"/"días" o forma "el N y el M"), se pospone el consumo:
        // parseRecurrence lo resolverá vía scanTrailingNamedMonth (relativo) y anclará
        // al mes siguiente correcto.
        val dualDayListPrecedesMes = nextPeriodMatch?.let { m ->
            if ("mes" !in m.value.lowercase()) return@let false
            // Lista multi-día con cadencia plural ("los días 15 y 30", "días 1 y 15")
            // o forma "el N y el M" (c.342 la reclama aunque sin "días" cuando hay mes
            // nombrado/relativo trasero). precedesDualDayList reusa la misma detección
            // que el guard de nextMonthDayMatch (consistencia).
            precedesDualDayList(m.range.first)
        } ?: false
        val effectiveNextPeriodMatch = if (dualDayListPrecedesMes) null else nextPeriodMatch
        val nextPeriodDueAt = effectiveNextPeriodMatch?.let { m ->
            val text = m.value.lowercase()
            val days = when {
                // "trimestre"/"bimestre"/"semestre" se comprueban antes que "mes":
                // "trimes**tre**"/"bi**mes**tre"/"se**mes**tre" contienen la subcadena
                // "mes", así que si "mes" fuera primero ganaría (+30d).
                "trimestre" in text -> 90L
                "bimestre" in text -> 60L
                "semestre" in text -> 180L
                "quincena" in text -> 15L
                "semana" in text -> 7L
                "mes" in text -> 30L
                "año" in text -> 365L
                // "próximos días" → dentro de ~3 días (heurística honesta de "pocos días").
                else -> 3L
            }
            now + days * 24 * 60 * 60_000L
        }
        effectiveNextPeriodMatch?.let { m ->
            // c.511: si el período es "quincena", el calificador de límite coloquial
            // ("a finales/principios/mediados de la/esta") que la precede quedaría como
            // residuo en el título (la fecha se resuelve bien como +15d). Se usa el
            // helper específico; para los demás períodos, [strippedPeriodRange] basta.
            val r = if ("quincena" in m.value.lowercase()) strippedQuincenaLimitRange(working, m.range)
            else strippedPeriodRange(working, m.range)
            working = working.replaceRange(r, " ")
        }

        // "quincena": hito financiero/laboreal en español (cobro/nómina/pago). La
        // quincena son dos hitos mensuales: el día 15 (primera quincena) y el fin de
        // mes (segunda quincena). Se procesa DESPUÉS de nextPeriodMatch para que
        // "próxima quincena"/"quincena que viene" (+15d, ya resuelto) no se afecten y
        // para que la subcadena "quincena" no active por error "mes que viene".
        //   - "primera quincena" → día 15 (este mes, o el próximo si hoy ≥ 15).
        //   - "segunda quincena" → fin de mes (este mes, o el próximo si hoy es el
        //     último día). Coincide con "fin de mes", pero es un sinónimo cotidiano.
        //   - "la quincena" / "de la quincena" (sin cualificar) → el próximo hito:
        //     si hoy < 15 → día 15; si hoy ≥ 15 → fin de mes.
        // Se trata como días relativos (epoch a medianoche) para combinarse con hora
        // explícita ("pago de la quincena a las 18"). Antes estas tareas caían a
        // dueAt=null → vencimiento olvidado (sin recordatorio ni visibilidad).
        val quincenaMatch = quincenaPattern.find(working)?.let { m ->
            // Descarta el match si es parte de una forma de recurrencia
            // ("cada quincena", "quincenalmente", "todas las quincenas"): esas las
            // resuelve parseRecurrence como WEEKLY x2 y no deben ser consumidas aquí.
            if (quincenaRecurrencePattern.containsMatchIn(working)) null else m
        }
        val quincenaDueAt = quincenaMatch?.let { m ->
            val today = base.toLocalDate()
            val which = m.groupValues[1].lowercase()
            val target = when {
                which.startsWith("primera") || which.startsWith("1") -> {
                    val fifteenthThis = today.withDayOfMonth(15)
                    if (today.isBefore(fifteenthThis)) fifteenthThis
                    else fifteenthThis.plusMonths(1)
                }
                which.startsWith("segunda") || which.startsWith("2") -> {
                    val lastDayThis = today.withDayOfMonth(today.lengthOfMonth())
                    if (today.isBefore(lastDayThis)) lastDayThis
                    else lastDayThis.plusMonths(1).withDayOfMonth(
                        lastDayThis.plusMonths(1).lengthOfMonth())
                }
                else -> {
                    // Próximo hito: si hoy < 15 → día 15; si hoy ≥ 15 → fin de mes.
                    // Si hoy es ya el último día (la quincena de fin de mes cae HOY), el
                    // próximo hito es el 15 del mes siguiente (consistente con "fin de
                    // mes", que rueda al mes próximo cuando hoy es el último día).
                    val fifteenthThis = today.withDayOfMonth(15)
                    if (today.isBefore(fifteenthThis)) fifteenthThis
                    else {
                        val lastDayThis = today.withDayOfMonth(today.lengthOfMonth())
                        if (today.isBefore(lastDayThis)) lastDayThis
                        else fifteenthThis.plusMonths(1)
                    }
                }
            }
            DateRules.toEpochMillis(target, LocalTime.of(9, 0), zone)
        }
        quincenaMatch?.let { working = working.replaceRange(strippedQuincenaLimitRange(working, it.range), " ") }

        // La fecha relativa (relativePattern) tiene prioridad; luego los límites de mes
        // ("fin de mes"/"mediados de mes"); "esta semana"; "principios/mediados de semana";
        // la quincena; el período próximo es el respaldo final. Todos son días (no
        // min/hora) para combinarse con una hora explícita.
        // Fechas pasadas (ago/lastPeriod) tienen prioridad: son explícitas y no
        // deben sobrescribirse por una fecha futura ambigua. La hora explícita se
        // aplica sobre la fecha pasada (tarea vencida con hora).
        // Las fraccionarias PASADAS ("hace media hora"/"hace una hora y media"/...) van
        // PRIMERO: son sub-hora (instante preciso now−N min) y más específicas que el
        // ago entero genérico; como se blanquean antes de [agoPattern], a lo sumo una de
        // ellas y agoDueAt están activas a la vez, pero por seguridad se prefieren aquí.
        // diminutiveAgoDueAt ("hace un ratito") va antes que agoDueAt: se blanquea antes
        // que [agoPattern], así a lo sumo uno de los dos está activo; se prefiere por
        // seguridad y para que el título quede limpio (agoPattern robaría solo "hace un").
        // deHoyEnIdiomDueAt ("de hoy en ocho") va antes que relativeDueAt: a lo sumo
        // uno de los dos está activo (el lookahead del idiom rechaza las unidades
        // explícitas que captura relativePattern), pero se prefiere por seguridad.
        val effectiveRelativeDueAt =
            compoundFractionalAgoDueAt ?: fractionalAndQuarterAgoDueAt ?: fractionalAgoDueAt ?:
            diminutiveAgoDueAt ?:
            agoDueAt ?: lastPeriodBoundaryDueAt ?: lastPeriodDueAt ?: deHoyEnIdiomDueAt ?: relativeDueAt ?: vagueRelativeDueAt ?: nowDueAt ?:
            laterRelativeDueAt ?: fractionalAndQuarterRelativeDueAt ?: fractionalRelativeDueAt ?:
            compoundFractionalRelativeDueAt ?: multiQuarterRelativeDueAt ?: monthBoundaryDueAt ?:
            monthBoundaryNameDueAt ?: bareMonthDueAt ?: paraMonthDueAt ?: yearBoundaryDueAt ?:
            thisMonthDueAt ?: thisYearDueAt ?:
            thisWeekWeekdayReverseDueAt ?: thisWeekWeekdayForwardDueAt ?:
            thisWeekDueAt ?: startOfWeekDueAt ?: midOfWeekDueAt ?: quincenaDueAt ?:
            nextMonthDayDueAt ?: nextMonthDayReverseDueAt ?: nextMonthDayShortDueAt ?:
            nextMonthDayShortReverseDueAt ?:
            nextWeekWeekdayReverseDueAt ?: nextWeekWeekdayForwardDueAt ?: nextPeriodDueAt
        val relativeIsDays = (agoMatch != null || lastPeriodBoundaryMatch != null || lastPeriodMatch != null ||
            deHoyEnIdiomMatch != null || relativeMatch != null || fractionalRelativeMatch != null ||
            fractionalAndQuarterRelativeMatch != null ||
            compoundFractionalRelativeMatch != null || multiQuarterRelativeMatch != null ||
            monthBoundaryDueAt != null || monthBoundaryNameDueAt != null || bareMonthDueAt != null || paraMonthDueAt != null || yearBoundaryDueAt != null ||
            thisMonthEarlyMatch != null || thisYearEarlyMatch != null ||
            thisWeekWeekdayReverseMatch != null || thisWeekWeekdayForwardMatch != null ||
            thisWeekEarlyMatch != null || startOfWeekEarlyMatch != null || midOfWeekEarlyMatch != null ||
            quincenaMatch != null || nextMonthDayMatch != null || nextMonthDayReverseMatch != null ||
            nextMonthDayShortMatch != null || nextMonthDayShortReverseMatch != null ||
            nextWeekWeekdayReverseMatch != null || nextWeekWeekdayForwardMatch != null || nextPeriodMatch != null) &&
            (fractionalRelativeMatch == null) &&
            (fractionalAndQuarterRelativeMatch == null) &&
            (compoundFractionalRelativeMatch == null) &&
            (multiQuarterRelativeMatch == null) &&
            (fractionalAgoMatch == null) &&
            (fractionalAndQuarterAgoMatch == null) &&
            (compoundFractionalAgoMatch == null) &&
            (relativeMatch?.let { m ->
                val unit = m.groupValues[2].lowercase()
                !unit.startsWith("min") && !unit.startsWith("hora")
            } ?: true)

        // c.397 — anclas sub-hora imprecisos: "ya"/"ahora"/"ya mismo" (nowMatch), "en un
        // rato" (vagueRelativeMatch), "más tarde"/"después" (laterRelativeMatch). No son
        // días ni horas precisas: expresan urgencia/aproximación vaga desde `now`. Cuando
        // coexisten con una hora o fecha explícita, éstas deben ganar (ver rawDueAt). El
        // KDoc de nowPattern/laterRelativePattern declara "no debe combinarse con hora
        // explícita"; esta bandera materializa esa intención en el flujo de dueAt.
        val relativeIsSubHourImprecise =
            nowMatch != null || vagueRelativeMatch != null || laterRelativeMatch != null

        // Repetición: se procesa antes que la fecha para que "cada viernes" no se lea como fecha suelta.
        // Recurrencia mensual + ocurrencia ordinal de día de la semana ("el primer lunes de
        // cada mes"): se captura el (ordinal, weekday) del match para que el motor ancle cada
        // ciclo al N-ésimo/último día de la semana en vez del día del mes (c.215: sin esto
        // "primer lunes de cada mes" derivaba al día 7 de cada mes y la 2ª cita se desplazaba).
        // Sólo aplica a MONTHLY: WEEKLY usa `days` (lista de días) y la 1ª ocurrencia ordinal
        // ya resolvió la fecha de `dueAt`.
        val recurrence = parseRecurrence(working, now).let { r ->
            // c.316 — evitar-olvidos: "el primer lunes del mes" / "el último viernes del
            // mes" SIN "cada"/"mensual"/"todos los meses" explícitos. Antes quedaba como
            // fecha ÚNICA vencida en el pasado (1er lunes de este mes ya pasó → recurrencia
            // NONE, dueAt en pasado): la rutina mensual nacía olvidada (recordatorio jamás
            // disparaba, jamás en What Now). Simétrico con el NUMÉRICO "el 1 del mes"
            // (monthlyDayPattern, que SÍ promueve a MONTHLY sin "cada"): la ocurrencia
            // ORDINAL de weekday "del mes" genérico (sin mes nombrado, sin "que viene") se
            // promueve aquí a MONTHLY; `withOrdinal` adjunta el anclaje (ord,weekday) y
            // lastWeekdayOfMonth pasa isRecurring=true → la 1ª cita avanza al próximo mes
            // válido, nunca en pasado. Mes nombrado ("de agosto") y "del mes que viene"
            // siguen siendo fecha única (no se promueven): prima el vencimiento concreto.
            val promoted = if (
                r.frequency == RecurrenceFrequency.NONE &&
                ordinalMonthly != null &&
                !ordinalMonthly.isNext &&
                !ordinalMonthly.isPrevious &&
                ordinalMonthly.monthName == null &&
                ordinalMonthly.yearStr == null
            ) {
                RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), r.phraseRanges)
            } else r
            val withOrdinal = if (promoted.frequency != RecurrenceFrequency.MONTHLY || ordinalMonthly == null) promoted
            else {
                val ordWord = ordinalMonthly.ordinalWord.lowercase()
                val ordinal = when (ordWord) {
                    "último", "ultimo" -> -1
                    "penúltimo", "penultimo" -> -2
                    "antepenúltimo", "antepenultimo" -> -3
                    "primer", "primero" -> 1
                    "segundo" -> 2
                    "tercer", "tercero" -> 3
                    "cuarto" -> 4
                    "quinto" -> 5
                    else -> null
                }
                val weekday = ordinalMonthly.weekdayWord.toDayOfWeekOrNull()
                if (ordinal != null && weekday != null) promoted.copy(monthlyOrdinalWeekday = ordinal to weekday.value) else promoted
            }
            // "cada fin/mediados/principios de mes" (c.257): si no quedó otra recurrencia
            // explícita, el límite mensual se promueve a recurrencia MONTHLY anclada.
            // c.471: cadencia mensual explícita ("mensual"/"cada mes") + límite de fin de
            // mes ("fin de mes"/"el último día[ del mes]"). Sin este, la recurrencia
            // MONTHLY venía de fixedPatterns SIN monthlyLastDay: nextMonthly conservaba
            // base.dayOfMonth=31 y SALTABA los meses cortos (septiembre, abril, junio,
            // noviembre), desplazando silenciosamente la rutina de alquiler/nómina. Aquí
            // se adopta el anclaje EOM del límite cuando coincide el boundaryKind=="end"
            // y no hay anclaje ordinal de weekday (que tiene su propia codificación).
            // c.575: "último día hábil del mes" (boundaryKind=="end-business") adopta
            // el anclaje EOM-BD (retroceso a viernes) simétrico, en vez de EOM puro.
            val promotedEom = when {
                withOrdinal.frequency == RecurrenceFrequency.MONTHLY &&
                    !withOrdinal.monthlyLastDay && !withOrdinal.monthlyLastBusinessDay &&
                    withOrdinal.monthlyOrdinalWeekday == null &&
                    boundaryKind == "end" -> withOrdinal.copy(monthlyLastDay = true)
                withOrdinal.frequency == RecurrenceFrequency.MONTHLY &&
                    !withOrdinal.monthlyLastDay && !withOrdinal.monthlyLastBusinessDay &&
                    withOrdinal.monthlyOrdinalWeekday == null &&
                    boundaryKind == "end-business" -> withOrdinal.copy(monthlyLastBusinessDay = true)
                else -> withOrdinal
            }
            if (promotedEom.frequency == RecurrenceFrequency.NONE && cadaBoundaryRecurrence != null) cadaBoundaryRecurrence
            else promotedEom
        }
        // c.495 (remoto): strippedPeriodRange consume el genitivo "de/del" externo
        // inmediatamente anterior a una frase de recurrencia ("Resumen de cada mes",
        // "Balance de todos los meses", "Cobro de cada quincena", "Informe de cada
        // bimestre"). Sin esto, el conector sobrevivía como residuo del título
        // ("Resumen de"). Simétrico de todos los sitios de período (fin de mes, la
        // quincena...) que ya usan este helper. El "de/del" de contenido ("reunión
        // del equipo cada mes") se respeta: no hay genitivo inmediatamente antes de
        // la frase de recurrencia.
        // c.496 (este run): strippedRecurrenceRange consume además la "a"
        // distributiva coloquial que antecede a CUALQUIER cadencia ("Meditar a cada
        // día" → "Meditar a", "Reunión a cada mes" → "Reunión a"). c.494 lo parcheó
        // sólo para fin de semana; aquí se generaliza a todas las familias de cadencia.
        // Se aplican ambos helpers en cadena: una frase de recurrencia puede ir
        // precedida de "de/del" (genitivo) O de "a" (distributiva) — primero se consume
        // el genitivo y, sobre el resultado, la "a". Ambos Sólo actúan cuando YA se casó
        // una cadencia real; "a cada reunión"/"reunión del equipo cada mes" no se tocan.
        // "a diario"/"a fines de semana" ya incluyen su "a" dentro del match → no se
        // duplica. Orden descendente (se borra de atrás adelante para no desplazar índices).
        recurrence.phraseRanges.sortedByDescending { it.first }.forEach { range ->
            val genitiveStripped = strippedPeriodRange(working, range)
            val fullyStripped = strippedRecurrenceRange(working, genitiveStripped)
            working = working.substring(0, fullyStripped.first) + " " + working.substring(range.last + 1)
        }

        // c.936: weekday genitivo dentro de una cadena narrativa H3 protegida
        // («las primeras horas de la mañana del lunes son tranquilas») NO es
        // ancla de fecha: pertenece al sujeto narrativo.
        val ordinalNarrativeWeekdayRanges = ordinalHoraNarrativeWeekdayRanges(working)
        val weekdayFirstMatch = weekdayPattern.find(working)
        // c.950: la captura ES narrativa en pretérito (decidido sobre el texto
        // original); el flag se propaga a [eraseWeekdayToken] para que el
        // título conserve el weekday exactamente cuando la fecha lo descartó.
        val weekdayPreteriteNarrative = weekdayFirstMatch != null &&
            weekdayOccurrenceIsPreteriteNarrative(working, weekdayFirstMatch)
        val weekdayMatch = weekdayFirstMatch
            ?.takeUnless { wm ->
                ordinalNarrativeWeekdayRanges.any { it.containsRange(wm.range) } ||
                    weekdayOccurrenceIsPreteriteNarrative(working, wm)
            }
        val weekendMatch = weekendEarlyMatch
        val numericDateMatch = numericDatePattern.find(working)
        // find() devolvía solo el PRIMER match; si su mes era inválido ("9 de la" → "la"),
        // parseMonthNameDate retornaba null y nunca se examinaba un match posterior con mes
        // válido ("el 15 de agosto") → "9 de la tarde el 15 de agosto" agendaba para HOY en
        // lugar del 15/8 (cita futura perdida). findAll + firstOrNull con mes válido lo corrige.
        val monthNameMatch = monthNamePattern.findAll(working)
            .firstOrNull { m -> months.any { (name, _) -> m.groupValues[2].equals(name, ignoreCase = true) } }
        // Solo cuenta como fecha si el mes es válido: así "8 de la manana" (sufijo de
        // hora, mes "la" inexistente) no sombra y anula la resolución de fecha de
        // repeticiones mensuales/semanales con hora.
        val monthNameDate = monthNameMatch?.let { parseMonthNameDate(base.toLocalDate(), it) }
        // "el 15" / "el 15 del mes" suelto: día del mes sin mes explícito. Se resuelve
        // DESPUÉS de monthNameDate (así "el 15 de marzo" gana) y ANTES de numericDateMatch
        // (así "el 15" aislado se ancla al día N en vez de caer al fallback de hoy).
        val dayOfMonthMatch = dayOfMonthPattern.find(working)
        val dayOfMonthDate = dayOfMonthMatch?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                nextMonthlyDate(base.toLocalDate(), day)
            }
        }
        // "pago del 15" / "cita del 20": día del mes suelto con artículo contracto "del".
        // Se resuelve DESPUÉS de dayOfMonthDate (que exige "el"/"día") y tras todos los
        // patrones de mes/rango/recurrencia (que el lookahead del patrón ya descarta). Va
        // DESPUÉS de dayOfMonthDate para que "el 15" gane cuando ambas formas coexistan.
        val delDayOfMonthMatch = delDayOfMonthPattern.find(working)
        val delDayOfMonthDate = delDayOfMonthMatch?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                nextMonthlyDate(base.toLocalDate(), day)
            }
        }
        // "antes del 30": día del mes suelto como plazo. Se resuelve ANTES que
        // dayOfMonthDate (que exige "el"/"día") para que el día suelto no caiga al vacío.
        val beforeDeadlineDayMatch = beforeDeadlineDayPattern.find(working)
        val beforeDeadlineDayDate = beforeDeadlineDayMatch?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                nextMonthlyDate(base.toLocalDate(), day)
            }
        }
        // "el lunes 24": weekday + número de día del mes suelto (sin mes). El número
        // explícito es más específico que el weekday suelto, así que ancla al día N del
        // mes (igual que "el lunes 24 de septiembre" ancla al 24/9). Va DESPUÉS de
        // monthNameDate/numericDateMatch (cuyos guards del lookahead negativo dejan fuera
        // las formas CON mes) y se resuelve ANTES del weekdayMatch suelto en el `when`.
        val weekdayDayMatch = weekdayDayPattern.find(working)
        val weekdayDayDate = weekdayDayMatch?.let { m ->
            m.groupValues[2].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                nextMonthlyDate(base.toLocalDate(), day)
            }
        }
        val partOfDayMatch = partOfDayPattern.find(working)
        val partOfDayTime = partOfDayMatch?.let { partOfDayTimes[it.groupValues[1].lowercase()] }
        // c.932: si el ordinal narrativo (H3) gobierna la parte del día dentro
        // de su propio match («las primeras horas de la mañana son las
        // mejores»), la parte del día interior NO es ancla independiente.
        val ordinalNarrativeRanges = ordinalHoraNarrativeRanges(working)
        // c.954 (remoto): la parte del día INTERCALADA de una narrativa en
        // pretérito c.950 («el lunes en la mañana llegó el paquete») tampoco
        // es ancla: pertenece al enunciado narrativo — guard evaluado ANTES.
        val weekdayPreteriteNarrativeIntercalatedRanges =
            weekdayPreteriteNarrativeIntercalatedPartOfDayRanges(working)
        val standalonePartOfDayOccurrence = standalonePartOfDayPattern.find(working)
            ?.takeUnless { sm -> ordinalNarrativeRanges.any { it.containsRange(sm.range) } }
            ?.takeUnless { sm -> weekdayPreteriteNarrativeIntercalatedRanges.any { it.containsRange(sm.range) } }
        // c.955: «hoy/ayer» + conector + parte del día + predicado en pretérito
        // («ayer en la mañana llegó el paquete») ES cadena narrativa, hermano
        // de c.950 weekday: la decisión se toma sobre el texto original y el
        // flag se propaga a los borradores del título para que fecha y título
        // nunca diverjan. El guard remoto c.954 va ANTES: el weekday con
        // parte del día intercalada ya no llega aquí.
        val dayPreteriteNarrative = standalonePartOfDayOccurrence != null &&
            dayPreteriteNarrativeOccurrence(working, standalonePartOfDayOccurrence)
        // c.1075: genitivo de RANGO con día relativo PASADO («desde/hasta/de +
        // ayer/anteayer/antier») en posición de contenido — abre el enunciado
        // («desde ayer no duermo bien») o sigue a una cópula («el informe es
        // desde/de ayer») — NO es ancla de fecha: anclarlo fabricaba una fecha
        // PASADA falsa y mutilaba el título (doble daño P1, medida 10/10). El
        // flag se propaga al borrador del título ([eraseRelativeDayToken]) para
        // que fecha y título nunca diverjan (doctrina c.930/c.950).
        val ayerRangeGenitiveNarrative = ayerRangeGenitiveRanges(working).isNotEmpty()
        val standalonePartOfDayMatch = standalonePartOfDayOccurrence
            ?.takeUnless { dayPreteriteNarrative }
        val standalonePartOfDayKey = standalonePartOfDayMatch?.let {
            (it.groupValues[1].ifBlank { it.groupValues[2] }.ifBlank { it.groupValues[3] }).lowercase().ifEmpty { null }
        }
        val standalonePartOfDayTime = standalonePartOfDayKey?.let { standalonePartOfDayTimes[it] }
        // c.929: la ancla lleva el sufijo «siguiente(s)» («la mañana siguiente») → la
        // parte del día es la del día SIGUIENTE (+1d). Se aplica sobre la fecha base en
        // `effectiveDate` (sólo cuando no hay fecha explícita, que siempre gana).
        val standalonePartOfDayNext =
            standalonePartOfDayMatch?.value?.lowercase()?.contains("siguiente") == true
        val compactDayPartOfDayMatch = compactDayPartOfDayPattern.find(working)
        val compactDayPartOfDayKey = compactDayPartOfDayMatch?.groupValues?.get(1)?.lowercase()
        val compactDayPartOfDayTime = compactDayPartOfDayKey?.let { compactDayPartOfDayTimes[it] }
        // c.930 — guard anti-robo narrativo: el ordinal sustantivo («la primera
        // hora de clase») no es ancla; ver ordinalHoraOccurrenceIsContent.
        val primeraHoraMatch = primeraHoraPattern.find(working)
            ?.takeUnless { ordinalHoraOccurrenceIsContent(working, it) }
        val ultimaHoraMatch = ultimaHoraPattern.find(working)
            ?.takeUnless { ordinalHoraOccurrenceIsContent(working, it) }
        val alFinalDelDiaMatch = alFinalDelDiaPattern.find(working)
        val alInicioDelDiaMatch = alInicioDelDiaPattern.find(working)
        val amanecerMatch = amanecerPattern.find(working)
        val atardecerMatch = atardecerPattern.find(working)
        val mediaPartOfDayMatch = mediaPartOfDayPattern.find(working)
        val mediaPartOfDayKey = mediaPartOfDayMatch?.let { it.groupValues[2].lowercase() }
        val mediaPartOfDayTime = mediaPartOfDayKey?.let { mediaPartOfDayTimes[it] }
        // "antes de/después de + comida/sueño": hora canónica de respaldo (ver mealSleepAnchorPattern).
        val mealSleepAnchorMatch = mealSleepAnchorPattern.find(working)
        val mealSleepAnchorTime = mealSleepAnchorMatch?.let {
            // El patrón casa "después" (con tilde) y "despues" (sin tilde, forma
            // cotidiana al escribir rápido en móvil), pero las claves del map
            // [mealSleepAnchorTimes] usan "después". Sin esta normalización,
            // "despues del almuerzo"/"despues de comer" caseaban el patrón pero el
            // lookup devolvía null → dueAt=null (cita recordatoria olvidada, P1).
            val mod = it.groupValues[1].lowercase()
            val modKey = if (mod.startsWith("despu")) "después" else mod
            val anchor = it.groupValues[2].lowercase()
            mealSleepAnchorTimes[anchor]?.get(modKey)
        }
        // Contexto PM: una parte del día de tarde/noche (explícita "esta tarde" o suelta "a la noche")
        // aplica offset +12 a una hora sin meridiem ("esta tarde a las 4" → 16:00). Las horas
        // canónicas vespertinas "al atardecer"/"al anochecer"/"al ocaso" también aportan contexto
        // PM: "al atardecer a las 7" → 19:00 (la puesta del sol es vespertina, 7 es 7pm).
        val partOfDayPmKeys = setOf("tarde", "noche")
        val hasPartOfDayPmContext =
            partOfDayMatch?.let { it.groupValues[1].lowercase() in partOfDayPmKeys } == true ||
            standalonePartOfDayKey in partOfDayPmKeys ||
            compactDayPartOfDayKey in partOfDayPmKeys ||
            recurrence.partOfDayIsPm ||
            atardecerMatch != null ||
            mediaPartOfDayKey == "tarde"
        // True solo cuando la fecha proviene de un día de la semana suelto ("el viernes")
        // y ese día ES hoy: la cita puede ser hoy mismo si su hora aún no pasó.
        var weekdaySameDayCandidate = false
        val date = when {
            // Debe ir antes que el "mañana" genérico: "esta mañana" contiene "mañana"
            // y no debe interpretarse como "el día de mañana".
            partOfDayMatch != null -> base.toLocalDate()
            // "anteayer"/"antier"/"ayer" son fechas PASADAS explícitas: el usuario reconoce que
            // la tarea está vencida ("Hacer X ayer"). Antes quedaban sin fecha (dueAt=null)
            // o, combinadas con hora ("ayer a las 4"), resolvían a HOY — fecha errónea.
            // Se mantiene en el pasado (honesto: la tarea es vencida, aparece en What Now).
            // "antier" = variante coloquial hispanoamericana de "anteayer".
            // c.957: EXCEPTO cuando la cadena es narrativa en pretérito con parte
            // del día («anteayer por la noche sonó la alarma») — no anclar.
            Regex("""(?i)\banteayer\b|\bantier\b""").containsMatchIn(working) && !dayPreteriteNarrative &&
                !ayerRangeGenitiveNarrative ->
                base.toLocalDate().minusDays(2)
            // c.954: si «hoy/ayer» introduce una cadena narrativa en pretérito
            // con parte del día («ayer en la mañana llegó el paquete»), no hay
            // ancla de fecha: la narrativa queda sin dueAt y el título íntegro.
            // c.1075: tampoco ancla el genitivo de rango narrativo («desde
            // ayer no duermo bien», «el informe es desde/de ayer»).
            Regex("""(?i)\bayer\b""").containsMatchIn(working) && !dayPreteriteNarrative &&
                !ayerRangeGenitiveNarrative ->
                base.toLocalDate().minusDays(1)
            // "antepasado mañana" = dentro de 3 días (mañana+2). Debe ir ANTES que
            // "pasado mañana" y que "mañana" suelto: la palabra "mañana" dentro de la
            // frase casaba con mananaAsDate → +1 (fecha errónea) y "antepasado" quedaba
            // como residuo en el título (P1: cita 2 días antes y título corrupto).
            Regex("""(?i)\bantepasad[oa]\s+ma[nñ]ana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(3)
            Regex("""(?i)\bpasado\s+ma[nñ]ana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            // "después de mañana" ≡ "pasado mañana" (el día después de mañana, +2):
            // forma coloquial extendidísima. Antes la "mañana" interna casaba con
            // mananaAsDate → +1 día (P1: tarea agendada un día ANTES de lo pedido,
            // fecha errónea silenciosa). Debe ir ANTES que "mañana" suelto, igual
            // que "antepasado mañana"/"pasado mañana".
            Regex("""(?i)\bdespu[eé]s\s+de\s+ma[nñ]ana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            // "mañana" como fecha (el día de mañana) sólo si NO forma parte de un
            // marcador de parte del día ("de la mañana", "por la mañana", "a la
            // mañana"). Antes, "Reunión a las 9 de la mañana" se fechaba en MAÑANA
            // por la mera coincidencia de la palabra "mañana", programando para
            // mañana una reunión de hoy (P1: tarea en día erróneo, reunión perdida
            // el mismo día). Se buscan todas las apariciones y basta con que una
            // sea un token de fecha suelto.
            mananaAsDate(working) -> base.toLocalDate().plusDays(1)
            // c.674/675: "este (mismo )?día" ≡ hoy. "día" no tiene marcador propio
            // (a diferencia de semana/mes/año, que viven en patrones de período), pero
            // la forma idiomática post-puesta "este mismo día" debe agendar HOY; antes
            // quedaba dueAt=null con el residuo íntegro en el título (P1: olvidada).
            Regex("""(?i)\beste\s+(?:mismo\s+)?d[ií]a\b""").containsMatchIn(working) -> base.toLocalDate()
            Regex("""(?i)\bhoy\b""").containsMatchIn(working) && !dayPreteriteNarrative -> base.toLocalDate()
            // "el último viernes del mes" / "el primer lunes de agosto" / "el tercer viernes
            // del mes que viene": ocurrencia ORDINAL de ese weekday en el mes (del mes = mes
            // actual, sin roll; del mes que viene/próximo/entrante = mes siguiente; de <mes> =
            // ese mes, con avance de año si ya pasó, igual que parseMonthNameDate). Debe ir
            // ANTES de previousWeekday para no caer en "último viernes" = viernes anterior.
            // Cubre también la cadencia precedente ("cada mes el primer lunes") vía
            // [ordinalMonthly], que normaliza ambas formas.
            ordinalMonthly != null ->
                lastWeekdayOfMonth(base.toLocalDate(), ordinalMonthly, recurrence.frequency != RecurrenceFrequency.NONE)
            // "el jueves pasado" / "el último lunes" / "el martes anterior": última
            // ocurrencia pasada de ese día. Tarea vencida honesta (What Now la muestra
            // como atrasada), no se proyecta al futuro como hacía antes weekdayMatch.
            previousWeekdayMatch != null && previousWeekdayMatch.groupValues[1].toDayOfWeekOrNull() != null ->
                previousWeekday(base.toLocalDate(), previousWeekdayMatch.groupValues[1].toDayOfWeek())
            previousWeekdayReversedMatch != null && previousWeekdayReversedMatch.groupValues[1].toDayOfWeekOrNull() != null ->
                previousWeekday(base.toLocalDate(), previousWeekdayReversedMatch.groupValues[1].toDayOfWeek())
            weekendMatch != null ->
                if (weekendMatchIsPast(weekendMatch)) previousWeekday(base.toLocalDate(), DayOfWeek.SATURDAY)
                else nextWeekday(base.toLocalDate(), DayOfWeek.SATURDAY)
            // Fecha con mes NOMBRADO ("24 de septiembre") tiene prioridad sobre un día de la
            // semana suelto ("lunes"): cuando ambos aparecen juntos ("reunión el lunes 24 de
            // septiembre") el usuario especifica una fecha concreta y nombra el weekday como
            // confirmación. Antes weekdayMatch (línea siguiente) se evaluaba ANTES → anclaba al
            // lunes más cercano (hoy) y "24 de septiembre" se borraba del título sin fijar la
            // fecha → cita agendada en día equivocado (P1: cita perdida). Una fecha completa con
            // mes es más específica que un weekday suelto, por lo que gana aquí. Los ordinales
            // ("el primer lunes de agosto") se resuelven antes vía [ordinalMonthly].
            monthNameDate != null -> monthNameDate
            // Fecha numérica con mes explícito ("24/9", "24/09", "30/10") tiene prioridad
            // sobre un día de la semana suelto ("lunes"): cuando ambos aparecen juntos
            // ("reunión el lunes 24/9") el usuario especifica una fecha concreta y nombra el
            // weekday como confirmación, igual que "el lunes 24 de septiembre" (c.467).
            // Antes weekdayMatch se evaluaba ANTES → anclaba al lunes más cercano (hoy) y
            // "24/9" se borraba del título sin fijar la fecha, dejando la cita en un día/mes
            // equivocado (P1: cita perdida en mes erróneo). Una fecha completa con mes
            // numérico es más específica que un weekday suelto, por lo que gana aquí.
            numericDateMatch != null -> {
                val day = numericDateMatch.groupValues[1].toIntOrNull()
                val month = numericDateMatch.groupValues[2].toIntOrNull()
                val rawYear = numericDateMatch.groupValues[3].toIntOrNull()
                val year = when {
                    rawYear == null -> base.year
                    rawYear < 100 -> 2000 + rawYear
                    else -> rawYear
                }
                if (day == null || month == null) null else {
                    // "29/2" sin año en año no bisiesto: el usuario se refiere al PRÓXIMO
                    // 29 de febrero real (año bisiesto), no a un 28/2 cualquiera. Sin
                    // esto LocalDate.of lanzaba -> dueAt=null -> fecha descartada
                    // silenciosamente, vencimiento olvidado. Paridad con parseMonthNameDate
                    // ("el 29 de febrero"), que SÍ rollaba; la forma numérica se descartaba
                    // (asimetría flagrante: "29/2"→null, "29 de febrero"→2028-02-29).
                    if (rawYear == null && month == 2 && day == 29) {
                        var y = base.year
                        if (!Year.isLeap(y.toLong()) || LocalDate.of(y, 2, 29).isBefore(base.toLocalDate())) {
                            do { y++ } while (!Year.isLeap(y.toLong()))
                        }
                        LocalDate.of(y, 2, 29)
                    } else {
                        runCatching { LocalDate.of(year, month, day) }.getOrNull()
                            // Día imposible para el mes/año ("31/4", "30/2", "29/2/2026"):
                            // se ajusta al último día válido, consistente con
                            // parseMonthNameDate ("31 de abril" -> 30/4). Honesto: el mes
                            // se respeta, el día se normaliza. Antes lanzaba -> null
                            // (fecha explícitamente escrita, descartada).
                            ?: LocalDate.of(year, month, minOf(day, YearMonth.of(year, month).lengthOfMonth()))
                    }.let { date ->
                        // Sin año explícito, una fecha pasada se entiende como del próximo año
                        // (consistente con parseMonthNameDate). Evita programar tareas en el
                        // pasado, donde los recordatorios nunca dispararían.
                        if (rawYear == null && date.isBefore(base.toLocalDate())) date.plusYears(1) else date
                    }
                }
            }
            // "el viernes a las 18" escrito el propio viernes ANTES de esa hora debe
            // vencer HOY (la reunión es hoy), no la semana siguiente. nextWeekday
            // siempre salta +7 cuando hoy es el día objetivo (lo reutilizan las
            // recurrencias, que necesitan ese "próximo" estricto). Para la fecha
            // suelta usamos nextWeekdayOrSame (incluye hoy) y diferimos al final del
            // parseo el descarte de "hoy si la hora ya pasó" → ahí se rueda +7 días.
            // Sin esto, una cita de hoy con hora futura se perdía una semana entera.
            // "el lunes 24": weekday + día del mes explícito. Va ANTES del weekday suelto:
            // el número es más específico y ancla al día N del mes (no al próximo lunes),
            // evitando cita en día erróneo y residuo "24" en el título.
            weekdayDayDate != null -> weekdayDayDate
            weekdayMatch != null -> {
                val target = weekdayMatch.groupValues[1].toDayOfWeek()
                // "jueves que viene"/"jueves próximos"/"próximo jueves": el usuario pide
                // explícitamente la PRÓXIMA ocurrencia, aunque hoy sea ese día. Antes, dicho
                // un jueves, "jueves que viene" caía en HOY (tarea agendada el día equivocado;
                // P1). Con modificador "próximo"/"que viene" forzamos +7 (nextWeekday estricto);
                // sin él, el día suelto "el jueves" dicho en jueves puede seguir siendo hoy.
                val mv = weekdayMatch.value.lowercase()
                // "próximo"/"proximo" (con o sin tilde) y "que viene" significan la PRÓXIMA
                // ocurrencia. La escritura sin tilde ("proximo viernes") es habitual en móvil;
                // antes solo se detectaba la forma acentuada y la cita caía en HOY (P1). Se
                // alinea con monthBaseForBoundary (que ya aceptaba ambas formas).
                val nextExplicit = mv.contains("que viene") || mv.contains("próxim") || mv.contains("proxim") ||
                    mv.contains("siguiente") || mv.contains("posterior")
                weekdaySameDayCandidate = !nextExplicit && base.toLocalDate().dayOfWeek == target
                if (nextExplicit) nextWeekday(base.toLocalDate(), target)
                else nextWeekdayOrSame(base.toLocalDate(), target)
            }
            // "antes del 30": plazo como día del mes suelto (sin nombre de mes, que ya
            // se resolvió arriba como monthNameDate). Debe ir ANTES de dayOfMonthDate
            // ("el 15"), que exige el artículo "el"/"día" y no casa "antes del 30".
            beforeDeadlineDayDate != null -> beforeDeadlineDayDate
            // "reunión el 15 a las 10": día del mes suelto. Ancla al día N de este mes, o
            // del siguiente si ese día ya pasó (hoy > N). La hora se combina luego; si
            // cae en pasado (mismo día, hora ya transcurrida) la cita queda como vencida
            // (honesto: ya ocurrió), consistente con el resto del parser.
            dayOfMonthDate != null -> dayOfMonthDate
            // "pago del 15": día del mes suelto con "del". Va tras dayOfMonthDate y tras los
            // patrones de rango/recurrencia/mes-nombrado (descartados por guardas del patrón).
            delDayOfMonthDate != null -> delDayOfMonthDate
            // Repetición semanal con días explícitos: primera ocurrencia futura.
            recurrence.frequency == RecurrenceFrequency.WEEKLY && recurrence.days.isNotEmpty() -> recurrence.days
                .mapNotNull { it.toDayOfWeekOrNull() }
                .map { nextWeekday(base.toLocalDate(), it) }
                .minOrNull()
            // Mensual anclado a día del mes ("el 15 de cada mes"): primera ocurrencia
            // futura con ese día (avanza de mes si ya pasó o si el día no existe).
            recurrence.frequency == RecurrenceFrequency.MONTHLY && recurrence.monthlyDayOfMonth != null ->
                nextMonthlyDate(base.toLocalDate(), recurrence.monthlyDayOfMonth)
            // c.315: mensual con VARIOS días ("el 1 y 15 de cada mes"): 1ª ocurrencia
            // futura = el menor día de la lista estrictamente posterior a hoy, o el
            // menor día del mes siguiente si ninguno cabe este mes (sin omitir el 2º).
            // c.341: si la lista lleva mes NOMBRADO ("los días 15 y 30 de septiembre"),
            // la 1ª ocurrencia se ancla a ese mes (antes se anclaba al mes actual por
            // la pérdida del mes nombrado al consumir los dígitos la lista → P1).
            // c.344: si la lista lleva calificador RELATIVO siguiente ("los días 15 y
            // 30 del mes que viene/próximo/entrante"), la 1ª ocurrencia se ancla al
            // mes SIGUIENTE (antes se ignoraba el calificador y se anclaba al mes
            // actual → rutina quincenal en mes erróneo, P1 de datos).
            recurrence.frequency == RecurrenceFrequency.MONTHLY && recurrence.monthlyDays != null ->
                if (recurrence.monthlyNamedMonth != null) {
                    nextMonthlyDateFromListInMonth(
                        base.toLocalDate(),
                        recurrence.monthlyDays,
                        recurrence.monthlyNamedMonth,
                        recurrence.monthlyNamedYear
                    )
                } else if (recurrence.monthlyNextMonth) {
                    // c.344: anclar al mes siguiente (con rollover de año). Se reutiliza
                    // nextMonthlyDateFromListInMonth (resuelve el menor día futuro >= hoy
                    // del mes objetivo), pasando el mes/año del mes que viene.
                    val nm = base.toLocalDate().plusMonths(1)
                    nextMonthlyDateFromListInMonth(
                        base.toLocalDate(),
                        recurrence.monthlyDays,
                        nm.monthValue,
                        nm.year
                    )
                } else {
                    nextMonthlyDateFromList(base.toLocalDate(), recurrence.monthlyDays)
                }
            // Recurrencias de intervalo (diaria, semanal/quincenal/mensual/anual sin día
            // explícito): se anclan a la fecha de captura. Antes quedaban con dueAt=null y
            // la tarea recurrente era invisible (sin recordatorio, sin aparición en What
            // Now/planificador → se olvidaba). La fecha explícita ya se resolvió arriba en
            // este when, así que esto solo alcanza las recurrencias sin anclaje específico.
            // EXCEPCIÓN: HOURLY con immediateDueAt ("cada 8 horas" sin hora escrita). La
            // 1ª dosis debe vencer AHORA (medicación que el usuario acaba de capturar y
            // debe tomar ya), no a las 09:00 canónicas ni rodada a mañana. Si se anclara a
            // hoy aquí, la cascada usaría 09:00 (o rodaría al día siguiente si ya pasó) y
            // la primera dosis se retrasaría: dejar effectiveDate=null hace que la cascada
            // caiga a immediateDueAt=now. Cuando hay hora explícita ("cada 8 horas a las
            // 3pm") parsedTime!=null y el effectiveDate de la cascada usa hoy+hora antes
            // de llegar aquí, así que la excepción no afecta ese caso.
            recurrence.frequency != RecurrenceFrequency.NONE && recurrence.immediateDueAt == null -> base.toLocalDate()
            else -> null
        }

        // c.514: el primer match de [timePatterns] puede ser una CUENTA ("a las 3 cajas"),
        // no una cita. Antes se tomaba el primer match sin más y el guard anti-cuenta lo
        // rechazaba DENTRO de `explicitTimeData` (return@let null) sin buscar el siguiente:
        // "enviar a las 5 invitaciones a las 9" → el primer match "a las 5" era cuenta,
        // se rechazaba, y la cita real "a las 9" se OLVIDABA (dueAt=null). Ahora se salta
        // todo match que sea cuenta hasta encontrar el primero que SÍ es una cita.
        val timeMatch = timePatterns.asSequence()
            .flatMap { pattern -> pattern.findAll(working) }
            .filterNot { timeMatchIsCountNoun(it, working) }
            // c.1045: la hora que cierra una narrativa en pretérito («ya me
            // llamó a las 8») no es cita; ver timeMatchIsPreteriteNarrative.
            .filterNot { timeMatchIsPreteriteNarrative(it, working) }
            .minByOrNull { it.range.first }
        val explicitTimeData = timeMatch?.let { match ->
            val mv = match.value.lowercase()
            // ANTI FALSO POSITIVO (c.361): "a las 10 personas"/"a la una personas" en
            // punto (sin evidencia de reloj: `:MM`, meridiem, fracción "y media", sufijo
            // horas/hs/h) es una CUENTA ("a las 10 [personas]"), no una cita. Se rechaza
            // el match si, tras la hora en punto sin evidencia, le sigue un SUSTANTIVO de
            // cantidad PLURAL (palabra alfabética de >=3 letras terminada en 's' que no sea
            // una continuación segura): "personas", "cajas", "entradas", "habitaciones",
            // "ventas". Así "hablar a las 10 personas del equipo" NO se agenda a las 10:00
            // ni mutila el título; cae a dueAt=null con el texto intacto. Las horas CON
            // evidencia de reloj ("a las 10:30", "a las 10 pm", "a las 10 horas", "a las 10
            // y media") son inequívocamente una cita y no se filtran. Exige PLURAL (no
            // 'hola'/'equipo'/'mañana') para no rechazar capturas legítimas donde una
            // palabra singular sigue a la hora ("reunión a las 9 hola" → 09:00, "hola" se
            // conserva en el título): el.quantity reading exige concordancia plural "las N
            // <plural>". Simétrico del lookahead de evidencia de reloj de "hacia/sobre/
            // para"; aquí la hora en punto SÍ se admite salvo tras sustantivo plural.
            // c.514: la lógica del guard se centralizó en [timeMatchIsCountNoun] y la
            // selección de `timeMatch` ya filtra las cuentas, así que este check es ahora
            // defensa en profundidad (un match-cuenta no debería llegar aquí).
            if (timeMatchIsCountNoun(match, working)) return@let null
            when {
                // "a la una del mediodía" captura hora (grupo 1 = "una") + meridiem "del
                // mediodía": NO debe caer a NOON, sino resolver 1pm (13:00) en la rama
                // genérica. Esta rama solo aplica a las frases puras "al mediodía"/
                // "a la medianoche" (patrón cuyo grupo 1 es la fracción "y media", no una
                // hora). Se distingue porque el grupo 1 NO es una hora parseable
                // (parseHour==null): "una" sí lo es (→ rama genérica), "y media"/"" no.
                // La fracción sub-hora ("al mediodía y media" → 12:30) se resuelve aquí
                // mismo; antes esas horas canónicas no admitían fracción y la cita caía en
                // punto. Simétrico del "a las N y media" ya soportado.
                (mv.contains("mediodía") || mv.contains("mediodia")) &&
                    parseHour(match.groupValues.getOrNull(1).orEmpty()) == null &&
                    match.groupValues.getOrNull(2).isNullOrBlank() -> {
                    val frac = resolveClockFraction(match.groupValues.getOrNull(1).orEmpty()) ?: 0
                    LocalTime.of(12, frac.coerceIn(0, 59)) to true
                }
                mv.contains("medianoche") &&
                    parseHour(match.groupValues.getOrNull(1).orEmpty()) == null &&
                    match.groupValues.getOrNull(2).isNullOrBlank() -> {
                    val frac = resolveClockFraction(match.groupValues.getOrNull(1).orEmpty()) ?: 0
                    LocalTime.of(0, frac.coerceIn(0, 59)) to true
                }
                else -> {
                    var hour = parseHour(match.groupValues[1]) ?: return@let null
                    val explicitMinute = match.groupValues[2].toIntOrNull()
                    // Los patrones tienen layout de grupos DISTINTO: timePattern[0]
                    // ("a las N") pone la fracción "y media/cuarto" en el grupo 3 y el
                    // meridiem en el 4; timePattern[1]/[2] (N:MM y Nam/Pm) ponen el
                    // meridiem en el grupo 3 (no hay fracción). El grupo 3 es, pues, O
                    // fracción O meridiem según el patrón: se disambigua por contenido.
                    val raw3 = match.groupValues.getOrNull(3)?.lowercase().orEmpty()
                    val raw4 = match.groupValues.getOrNull(4)?.lowercase().orEmpty()
                    // Grupo 5: fracción sub-hora TRAS el meridiem ("a las 9 pm y media",
                    // "a las 9 de la tarde y cuarto"). Sólo la tienen los patrones
                    // "a las N"/"a la una" (que admiten la fracción en ambos lados del
                    // meridiem); los patrones N:MM/Nam no capturan grupo 5. Antes el
                    // orden fijo [fracción][meridiem] hacía que la fracción POSTERIOR al
                    // meridiem no casara: se agendaba la cita en punto (21:00 en vez de
                    // 21:30) y "y media" quedaba como residuo en el título. Ahora se usa
                    // como fallback de la fracción principal (g3 tiene prioridad).
                    val raw5 = match.groupValues.getOrNull(5)?.lowercase().orEmpty()
                    val isFraction = { s: String -> s.startsWith("y ") || s.startsWith("menos ") }
                    // Fracción sub-hora del grupo 3: positiva "y media"/"y veinte"/
                    // "y tres cuartos" (suma minutos) o negativa "menos cuarto"/"menos
                    // veinte" (resta, con wrap 24h más abajo). Sólo el grupo 3 de los
                    // patrones "a las N"/"a la una" empieza por "y "/"menos "; el meridiem
                    // "am"/"pm" de los patrones N:MM/Nam no, así [resolveClockFraction]
                    // devuelve null y se trata como meridiem. Antes sólo "y media"/
                    // "y cuarto" sumaban y "menos {cuarto|cinco|diez|veinte|veinticinco|N}"
                    // restaba: "a las once y veinte"/"a las diez y tres cuartos" caían en
                    // punto con residuo en el título (asimetría positiva de c.114).
                    val frac3 = if (isFraction(raw3)) resolveClockFraction(raw3) else null
                    val frac5 = if (isFraction(raw5)) resolveClockFraction(raw5) else null
                    val frac = frac3 ?: frac5
                    val addMin = frac?.takeIf { it > 0 }
                    val subMin = frac?.takeIf { it < 0 }?.let { -it }
                    val minute = explicitMinute ?: (addMin ?: 0)
                    // "a las 24" / "24:00" = medianoche (00:00), forma común en horarios.
                    // Se marca como meridiem explícito para evitar que el contexto PM de
                    // parte del día aplique un offset (24 ya es absoluto).
                    if (hour == 24) {
                        LocalTime.MIDNIGHT to true
                    } else {
                        // Meridiem: grupo 4 si existe (patrón 0); si no, grupo 3 cuando
                        // no es fracción (patrones 1/2). Antes se leía solo el grupo 4,
                        // así que "2pm"/"8:30pm" sin "a las" ignoraban el meridiem y se
                        // agendaban como AM ("reunión 3pm" → 03:00, 3am). Ahora se aplica
                        // el offset PM/AM correcto para todas las formas.
                        val meridiem = (if (raw4.isNotEmpty()) raw4 else raw3)
                            .replace(".", "").replace(" ", "")
                        // Normaliza ñ→n para que "de la manana" (escritura sin tilde, habitual
                        // en móvil) se reconozca igual que "de la mañana". Antes la comparación
                        // literal "delamanaana" (doble a) no casaba nunca y "12 de la manana"
                        // se agendaba 12:00 mientras "12 de la mañana" caía a 00:00 (asimetría).
                        val mer = meridiem.lowercase().replace("ñ", "n").replace("í", "i")
                        // "de la tarde"/"de la noche" → 12h posterior; "de la mañana/madrugada" → am.
                        // "del mediodía" → PM: "a la una del mediodía" = 13:00 (forma cotidiana de 1pm).
                        val isPm = mer == "pm" || mer == "delatarde" || mer == "delanoche" || mer == "delmediodia"
                        val isAm = mer == "am" || mer == "delamanana" || mer == "delamadrugada"
                        if (isPm && hour < 12) hour += 12
                        if (isAm && hour == 12) hour = 0
                        // "12 de la noche" = medianoche (00:00), no 12:00 del mediodía.
                        if (isPm && hour == 12 && mer == "delanoche") hour = 0
                        // "menos cuarto/cinco/diez/veinte/veinticinco" resta minutos a la
                        // hora ya resuelta (con meridiem aplicado) envolviendo 24h. Así
                        // "a las 3 menos cuarto de la tarde" → 14:45 y "a las 12 menos
                        // cuarto" → 11:45. Se aplica al final para que el wrap respete el
                        // offset PM/AM (no descuadra el meridiem).
                        if (subMin != null && explicitMinute == null && subMin in 1..59) {
                            val total = (hour * 60 + minute - subMin + 1440) % 1440
                            LocalTime.of(total / 60, total % 60) to meridiem.isNotEmpty()
                        } else {
                            LocalTime.of(hour, minute) to meridiem.isNotEmpty()
                        }
                    }
                }
            }
        }
        // Duración: no se aplica a "en N minutos" (esa es fecha relativa, ya eliminada).
        // Rango horario "de H1[MM] [meridiem] a H2[MM] [meridiem] [horas]": se procesa
        // primero para que el segundo número no sea robado como duración numérica
        // ("de 18 a 20 horas" → 20h falsas). Cada extremo se resuelve a hora absoluta
        // (offset PM) y la duración es (fin − inicio) en minutos reales.
        var rangeDurationMinutes: Int? = null
        val rangeMatch = timeRangePattern.find(working)?.let { m ->
            val startH = m.groupValues[1].toIntOrNull()
            val startM = m.groupValues[2].toIntOrNull() ?: 0
            val startMer = m.groupValues[3].lowercase().replace(".", "").replace(" ", "")
            val endH = m.groupValues[4].toIntOrNull()
            val endM = m.groupValues[5].toIntOrNull() ?: 0
            val endMer = m.groupValues[6].lowercase().replace(".", "").replace(" ", "")
            val hasUnit = rangeUnitToken.containsMatchIn(m.value)
            val startPm = startMer == "pm" || startMer == "delatarde" || startMer == "delanoche"
            val endPm = endMer == "pm" || endMer == "delatarde" || endMer == "delanoche"
            // Propagación de meridiem: si SOLO un extremo lleva un meridiem PM, el otro
            // (sin meridiem) comparte el contexto de tarde/noche.
            //  - FIN con PM ("de 6 a 8 de la tarde") → inicio bare hereda PM → 18:00.
            //  - INICIO con PM ("de 6pm a 8") → fin bare hereda PM → 20:00. Sin esto el
            //    fin (8) caía antes que el inicio (18) → rango inválido, duración null y
            //    título sucio ("Reunión de a 8").
            // CRUCE DEL MEDIODÍA: la propagación solo aplica cuando startHr <= endHr
            // (mismo lado del mediodía). En un cruce ("de 11 a 1 de la tarde") el inicio
            // es AM (11:00) y el fin PM (13:00); propagar PM convertiría 11→23 y la
            // duración/dueAt serían absurdos. Sin este guard, "11 a 1 de la tarde" daba
            // dueAt=23:00 y duración 5 (clamp de -600).
            // ANTI FALSO POSITIVO: la propagación inversa (inicio PM → fin bare) se
            // suprime si al fin le sigue un sustantivo de cantidad ("de 2pm a 4
            // entradas"), que es una cuenta, no un rango horario. La propagación hacia
            // adelante no lo necesita porque el fin ya lleva meridiem explícito.
            if (startH == null || endH == null) return@let null
            // followedByCount no depende del meridiem; se calcula antes para gatear la
            // propagación inversa.
            val followedByCount = m.range.last + 1 < working.length &&
                !countNounFollowerPattern.containsMatchIn(working.substring(m.range.last + 1))
            val startPmEffective = startPm || (startMer.isEmpty() && endPm && startH <= endH)
            val endPmEffective = endPm || (endMer.isEmpty() && startPm && startH <= endH && !followedByCount)
            fun resolve(h: Int, mer: String, pm: Boolean): Int? = when {
                h == 24 && mer.isEmpty() -> 0
                h !in 0..24 -> null
                pm && h < 12 -> h + 12
                pm && h == 12 && mer == "delanoche" -> 0
                pm && h == 12 -> 12
                mer.isEmpty() -> h
                else -> if (h == 12) 0 else h   // AM / de la mañana / madrugada
            }
            val sAbs = resolve(startH, startMer, startPmEffective)
            val eAbs = resolve(endH, endMer, endPmEffective)
            if (sAbs == null || eAbs == null) return@let null
            val startMin = sAbs * 60 + startM
            val endMin = eAbs * 60 + endM
            // CRUCE DE MEDIANOCHE INVERSO: inicio PM con fin bare/AM que cae ANTES en el
            // reloj ("de 11pm a 1" → 23:00→01:00). El fin NO hereda PM (sería 13:00); se
            // queda en su hora (01:00) y el rango envuelve al día siguiente (+24h). Solo
            // cuando el inicio es PM-efectivo y el fin no lo es (distinto lado del
            // mediodía), y sin sustantivo de cantidad tras el fin. Así "de 8pm a 3pm"
            // (ambos PM, descendente) NO envuelve: se rechaza como antes.
            val midnightWrap = endMin <= startMin &&
                startPmEffective && !endPmEffective && !followedByCount
            // Duración solo si fin > inicio (mismo día o envuelto) y rango plausible (<= 24h).
            val hasMinutesOrMeridiem = startM != 0 || endM != 0 ||
                startMer.isNotEmpty() || endMer.isNotEmpty()
            // Rango en punto y ambiguo (sin unidad/minutos/meridiem, ambas < 13): solo se
            // acepta si no le sigue un sustantivo de cantidad ("entradas", "personas").
            val ambiguousOnTheHour = !hasUnit && !hasMinutesOrMeridiem &&
                startH < 13 && endH < 13
            // RANGO AMBIGUO QUE CRUZA EL MEDIODÍA ("de 9 a 5", "de 8 a 4", "de 10 a 1"):
            // fin <= inicio en un rango ambiguo bare. La lectura natural en español es
            // fin PM (9→17, 8→16) — la jornada/turno, la forma más común de bloque de
            // trabajo. Antes se rechazaba entero (dueAt=null, duración perdida) aunque
            // "de 3 a 5" (fin > inicio, la MISMA ambigüedad) sí se aceptaba: asimetría
            // que perdía el bloque laboral (P1). El wrap +12h al fin se aplica bajo las
            // mismas condiciones del caso ascendente (sin sustantivo de cantidad,
            // duración 1..11h) y EXCLUYE el inicio en 12: "de 12 a 2" sigue rechazado
            // (decisión deliberada de ciclo 79 — el límite del mediodía sin meridiem es
            // irreductiblemente ambiguo). Mutuamente excluyente con midnightWrap (éste
            // exige inicio PM-efectivo; el rango ambiguo bare no lo tiene).
            val noonWrap = !midnightWrap && ambiguousOnTheHour && !followedByCount &&
                startH in 1..11 && endH in 1..11 && endMin <= startMin &&
                (endMin + 12 * 60 - startMin) in 60..(11 * 60)
            val endMinEffective = when {
                midnightWrap -> endMin + 24 * 60
                noonWrap -> endMin + 12 * 60
                else -> endMin
            }
            val acceptAmbiguous = !ambiguousOnTheHour || noonWrap ||
                (!followedByCount && (endMin - startMin) in 60..(11 * 60))
            val valid = endMinEffective > startMin &&
                (endMinEffective - startMin) <= 24 * 60 &&
                sAbs in 0..23 && eAbs in 0..23 && startM in 0..59 && endM in 0..59 &&
                (hasUnit || hasMinutesOrMeridiem || sAbs >= 13 || eAbs >= 13 || acceptAmbiguous)
            // La duración se calcula con las horas ABSOLUTAS resueltas (sAbs/eAbs), no con
            // las horas crudas del texto. Sin esto, un rango que cruza el mediodía
            // ("de 12 a 2 de la tarde": start=12, end=14) computaba end−start con horas
            // crudas (2−12=−600) → coerceIn(5,…) dejaba 5 min en vez de 120.
            if (valid) {
                rangeDurationMinutes = (endMinEffective - startMin).coerceIn(5, 24 * 60)
                m
            } else {
                null
            }
        }
        rangeMatch?.let { working = working.replaceRange(it.range, " ") }
        // Hora de inicio del rango resuelta a absoluta (con su meridiem). Sin tiempo
        // explícito ("a las"), la hora de inicio del rango es la mejor estimación de la
        // fecha límite del evento; antes caía a la canónica de la parte del día
        // ("de 9 de la tarde a 11 de la noche" → 15:00 por "de la tarde"), ignorando la
        // hora real de inicio (21:00). Solo se usa si el rango fue validado (rangeMatch).
        val rangeStartTime: LocalTime? = rangeMatch?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val mer = m.groupValues[3].lowercase().replace(".", "").replace(" ", "")
            val endH = m.groupValues[4].toIntOrNull() ?: return@let null
            val endMer = m.groupValues[6].lowercase().replace(".", "").replace(" ", "")
            val startPmOwn = mer == "pm" || mer == "delatarde" || mer == "delanoche"
            val endPm = endMer == "pm" || endMer == "delatarde" || endMer == "delanoche"
            // Propagación: el inicio bare hereda el PM del extremo final
            // ("de 6 a 8 de la tarde" → 18:00). Véase rangeMatch.
            // CRUCE DEL MEDIODÍA: solo si startHr <= endHr (mismo lado del mediodía);
            // en "de 11 a 1 de la tarde" el inicio es AM (11:00) y el fin PM (13:00).
            val startPmEff = startPmOwn || (mer.isEmpty() && endPm && h <= endH)
            val abs = when {
                h == 24 && mer.isEmpty() && !startPmEff -> 0
                h !in 0..24 -> return@let null
                startPmEff && h < 12 -> h + 12
                startPmEff && h == 12 && mer == "delanoche" -> 0
                startPmEff && h == 12 -> 12
                mer.isEmpty() -> h
                else -> if (h == 12) 0 else h
            }
            if (abs in 0..23 && min in 0..59) LocalTime.of(abs, min) else null
        }
        val explicitTime = explicitTimeData?.first
        val hasExplicitMeridiem = explicitTimeData?.second == true
        // Un tiempo explícito (timePatterns) que cae DENTRO del span de un rango validado
        // es, en realidad, el extremo final del rango ("de 6 a 8 pm": "8 pm" no es una
        // hora suelta, es el FIN). En ese caso la hora de inicio (rangeStartTime) es la
        // fecha límite correcta del evento, no la de fin. Sin este guard, "8 pm"→20:00
        // sombreaba rangeStartTime 18:00 y agendaba 2h tarde. La forma "de la tarde"
        // (c.76) no se ve afectada: timePatterns no captura "8 de la tarde".
        val explicitTimeIsRangeEnd = rangeMatch != null && timeMatch != null &&
            timeMatch!!.range.first >= rangeMatch!!.range.first &&
            timeMatch.range.last <= rangeMatch.range.last
        // Hora suelta con parte del día ("Taller 9 de la tarde"): resuelve la hora absoluta
        // con su meridiem. Se procesa ANTES de borrar el título y ANTES de la canónica de
        // parte del día, así "9 de la tarde" → 21:00 y no 15:00. El patrón exige "de la
        // <parte>" para no colisionar con fechas ("9 de marzo") y no roba lo que ya capturó
        // timePatterns/timeRangePattern (que se corren primero y dejan "9:30 de la tarde"
        // resuelto); aquí se captura solo lo que sobrevive: la hora simple.
        val standaloneHourPartOfDayMatch =
            if (explicitTime == null) standaloneHourPartOfDayStripPattern.find(working) else null
        val standaloneHourPartOfDayTime = standaloneHourPartOfDayMatch?.let { resolveStandaloneHourPartOfDay(it) }
        // El propio patrón (standaloneHourPartOfDayStripPattern) ya incluye el prefijo opcional
        // "de", así replaceRange consume el genitivo junto con la hora ("cita de 5 de la tarde"
        // → "cita"); el prefijo no capturador no altera los grupos de hora/parte que usa
        // resolveStandaloneHourPartOfDay.
        standaloneHourPartOfDayMatch?.let { working = working.replaceRange(it.range, " ") }
        // Un tiempo explícito tiene prioridad sobre la hora canónica de la parte del día.
        // Si la hora explícita vino sin meridiem (p.ej. "a las 4") y hay contexto PM de
        // parte del día ("esta tarde"/"a la noche"), se aplica el offset +12 ("esta tarde
        // a las 4" → 16:00, no 04:00).
        // Excepción: si el tiempo explícito es el extremo final de un rango
        // (explicitTimeIsRangeEnd), gana rangeStartTime (hora de inicio del evento).
        val explicitTimeForParsing = if (explicitTimeIsRangeEnd) null else explicitTime
        val hasExplicitMeridiemForParsing =
            if (explicitTimeIsRangeEnd) false else hasExplicitMeridiem
        val parsedTime = explicitTimeForParsing?.let { t ->
            if (!hasExplicitMeridiemForParsing && hasPartOfDayPmContext && t.hour in 1..11)
                t.plusHours(12) else t
        } ?: rangeStartTime
            ?: partOfDayTime
            ?: standaloneHourPartOfDayTime
            ?: standalonePartOfDayTime
            ?: compactDayPartOfDayTime
            ?: recurrence.partOfDayTime
            ?: primeraHoraMatch?.let { primeraHoraTime }
            ?: alInicioDelDiaMatch?.let { primeraHoraTime }
            ?: ultimaHoraMatch?.let { ultimaHoraTime }
            ?: alFinalDelDiaMatch?.let { alFinalDelDiaTime }
            ?: amanecerMatch?.let { amanecerTime }
            ?: atardecerMatch?.let { atardecerTime }
            ?: mediaPartOfDayTime
            ?: mealSleepAnchorTime
        // c.929: sin fecha explícita, la ancla parte-del-día con sufijo «siguiente»
        // («para la mañana siguiente») ancla al día SIGUIENTE (+1d), no a hoy.
        val effectiveDate = date
            ?: if (parsedTime != null) base.toLocalDate().plusDays(if (standalonePartOfDayNext) 1 else 0)
            else null
        // c.397 — anclas sub-hora imprecisos ("ya"/"ahora"/"ya mismo" = now,
        // "en un rato" = now+1h, "más tarde"/"después" = now+3h) capturaban el dueAt
        // ANTES que una hora/fecha explícita y la descartaban: "reunión ya a las 5 de la
        // tarde" → 12:00 (now) en vez de 17:00; "reunión ya el viernes" → now en vez de
        // viernes. El KDoc de nowPattern/laterRelativePattern declara "no debe combinarse con
        // hora explícita", pero la cascada effectiveRelativeDueAt lo permitía. Principio
        // (consistente con l.3367 "un tiempo explícito tiene prioridad"): una hora o fecha
        // explícita gana sobre cualquier ancla sub-hora impreciso; éste sólo significa algo
        // sin dato horario/fecha preciso. Se anula aquí (no en la cascada original, que sigue
        // informando confidence/past-safe vía `effectiveRelativeDueAt`) para que el `else`
        // aplique la hora/fecha sobre hoy y el past-safe de medianoche/mediodía (que exige
        // relativeDueAt == null) actúe igual que sin el ancla — evitando el olvido de citas.
        val hasExplicitDateTime = parsedTime != null || date != null
        val relativeDueAtForDueAt =
            if (relativeIsSubHourImprecise && hasExplicitDateTime) null else effectiveRelativeDueAt
        val rawDueAt = when {
            effectiveRelativeDueAt != null && relativeIsDays && parsedTime != null ->
                DateRules.toEpochMillis(DateRules.toLocalDate(effectiveRelativeDueAt, zone), parsedTime, zone)
            // immediateDueAt (cadencia sub-diaria "cada N horas": el motor no repite por
            // hora, pero se saca la primera dosis a la superficie venciendo ahora) es el
            // último recurso: sólo aplica si NO hay otra fecha/hora resuelta. Así "cada 8
            // horas a las 3pm" usa la hora explícita, no "ahora".
            else -> relativeDueAtForDueAt
                ?: effectiveDate?.let { DateRules.toEpochMillis(it, parsedTime ?: LocalTime.of(9, 0), zone) }
                ?: recurrence.immediateDueAt
        }
        // "el viernes a las 18" escrito el viernes a las 10:00 → hoy 18:00 (se conserva).
        // Pero si la hora ya pasó ("el viernes a las 6" a las 10:00) o no había hora y el
        // mediodía canónico (09:00) ya pasó, se rueda a la semana siguiente, igual que
        // antes. Así no se agenda nada en el pasado y no se pierde una cita de hoy.
        //
        // Hora canónica INEQUÍVOCA (medianoche/mediodía, "12 de la noche"/"12 de la
        // tarde") SIN fecha explícita cae por defecto en hoy; si ese instante ya pasó,
        // se rueda al día siguiente (medianoche → madrugada de mañana; mediodía →
        // mediodía de mañana). Antes "cena a la medianoche" capturada al mediodía caía
        // en hoy 00:00 (12h en el pasado): el recordatorio (dueAt - offset) también
        // quedaba en el pasado y ReminderSync.triggers lo descartaba (trigger <= now →
        // null) → la cita se olvidaba sin aviso. Past-safe consistente con el contrato
        // de reminders (DECISIONES: "no quedar al olvido"). Sólo horas inequívocas
        // (meridiem explícito + valor 00:00/12:00 y la hora vino del tiempo explícito,
        // no de un rango): las horas sueltas ("a las 9") y de franja ambigua
        // ("9 de la mañana", cuyo día es ambiguo: registrar pasado vs. mañana) se dejan
        // en hoy para no alterar la semántica existente ni romper la ambigüedad AM/PM.
        val isInequivocalMidpoint = mediaPartOfDayTime != null &&
            (parsedTime == LocalTime.MIDNIGHT || parsedTime == LocalTime.NOON)
        val dueAt = when {
            weekdaySameDayCandidate && rawDueAt != null && rawDueAt < now ->
                DateRules.toEpochMillis(date!!.plusDays(7), parsedTime ?: LocalTime.of(9, 0), zone)
            // "media noche"/"medio día" (formas separadas de medianoche/mediodía) son
            // canónicas inequívocas (00:00/12:00) y reciben el mismo past-safe: sin esto,
            // "cena a media noche" capturada por la tarde caía en hoy 00:00 (pasado) y se
            // olvidaba, mientras "cena a medianoche" (una palabra) sí se rodaba — asimetría
            // que dejaba la forma separada al olvido (P1). Las demás "media X" (10:30/16:30/
            // 03:00) no son midnight/noon y no ruedan, igual que las canónicas afines.
            date == null && relativeDueAtForDueAt == null && !explicitTimeIsRangeEnd &&
                parsedTime != null && (hasExplicitMeridiem || isInequivocalMidpoint) &&
                (parsedTime == LocalTime.MIDNIGHT || parsedTime == LocalTime.NOON) &&
                rawDueAt != null && rawDueAt < now ->
                DateRules.toEpochMillis(base.toLocalDate().plusDays(1), parsedTime, zone)
            // "antes de/después de + comida/sueño" (mealSleepAnchor): hora canónica de respaldo
            // sin fecha explícita. Si el instante ya pasó hoy (p.ej. "antes del almuerzo"=11:30
            // capturado a las 12:00, o "después de comer"=14:00 capturado a las 15:00), se rueda
            // al día siguiente — consistente con el past-safe de medianoche/mediodía: sin esto,
            // el recordatorio (dueAt - offset) caía en el pasado y ReminderSync.triggers lo
            // descartaba (trigger <= now → null) → cita olvidada sin aviso (P1 evitar olvidos).
            // El ancla es inequívoca (11:30/14:00/21:30…), así el rodado es seguro.
            date == null && relativeDueAtForDueAt == null && !explicitTimeIsRangeEnd &&
                mealSleepAnchorTime != null && rawDueAt != null && rawDueAt < now ->
                DateRules.toEpochMillis(base.toLocalDate().plusDays(1), parsedTime!!, zone)
            else -> rawDueAt
        }


        // Duración numérica: se descarta si el número está precedido por una frase
        // horaria ("a las 9 horas", "a la 1 horas", "de la tarde 2 horas"), porque ahí
        // "N horas" es la HORA de un evento, no su duración. Sin este guard, "reunión a
        // las 9 horas" robaba "9 horas" como 540 min falsos y dejaba el residuo "a las".
        // El "en N" final (fecha relativa) ya se filtra con la regex existente.
        val timePhrasePreceding = Regex(
            """(?i)(?:a\s+las|a\s+la(?:\s+ma[ñn]ana)?|de\s+la\s+(?:ma[ñn]ana|tarde|noche|madrugada))\s*$"""
        )
        // Reloj precediendo: descarta la duración cuando el número casado es, en realidad,
        // la parte de MINUTOS de un reloj "HH:MM[h/hs/horas]" (p.ej. "15:30h" → la duración
        // "Nh" casa "30h" pero el ":" anterior y pegado delata que "30" son minutos de reloj,
        // no una duración). Sin este guard, "15:30h" robaba "30h" como 1440 min (clampeados) y
        // dejaba el título corrupto "Reunión 15:" aunque dueAt ya fuese 15:30. El ":" debe ir
        // PEGADO al número (sin espacio, patrón `HH:` tight): así NO confunde un "Nh" real
        // tras una etiqueta con dos puntos ("Versión 2: 1h de trabajo" → el "2: " lleva
        // espacio tras ":", el guard NO casa y "1h" sigue siendo duración legítima). Tampoco
        // afecta a "Nh" tras reloj SIN sufijo ("15:30 2h" → "2h" va tras "30 ", sin ":" final,
        // sigue siendo duración). Cubre "HH:MMh pm"/"HH:MMhs"/"HH:MM horas" porque el ":"
        // precede igual al número casado.
        val clockPreceding = Regex("""\d{1,2}:$""")
        val durationMatch = durationPatterns.asSequence()
            .mapNotNull { it.find(working) }
            .filter { match ->
                !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
                !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first)) &&
                !clockPreceding.containsMatchIn(working.substring(0, match.range.first))
            }
            .minByOrNull { it.range.first }
        // Duración con número escrito ("dos horas"/"treinta minutos"/"un par de horas"):
        // mismos guards que la duración numérica para no robar "a las nueve horas" (hora
        // de un evento) ni "en dos horas" (fecha relativa, ya consumida antes). Se procesa
        // aparte porque su cantidad se resuelve con [parseWrittenNumber], no con toIntOrNull.
        val writtenMatch = writtenDurationPattern.find(working)?.takeIf { match ->
            !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
            !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first)) &&
            !clockPreceding.containsMatchIn(working.substring(0, match.range.first))
        }
        // Duración fraccionaria sin dígitos ("media hora"/"cuarto de hora"): se computa
        // aparte y se elige la ocurrencia más a la izquierda respecto a las demás.
        val fractionalMatch = fractionalDurationPattern.find(working)
        // Duración fraccionaria COMPUESTA ("2 horas y media"/"dos horas y cuarto"):
        // mismos guards que la duración numérica/escrita. Captura la frase completa
        // (incluida la fracción) para que no quede "y media" como residuo en el título.
        val compoundFractionalDurationMatch = compoundFractionalDurationPattern.find(working)?.takeIf { match ->
            !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
            !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first)) &&
            !clockPreceding.containsMatchIn(working.substring(0, match.range.first))
        }
        // Duración multi-cuarto ("tres cuartos de hora"/"dos cuartos"): mismos guards que
        // la duración numérica/escrita. La forma con prefijo "en/dentro de ..." ya la
        // consume [multiQuarterRelativePattern] antes en el flujo (fecha relativa), así
        // que aquí solo llega la de cantidad (sin prefijo) y se procesa como duración.
        val multiQuarterDurationMatch = multiQuarterDurationPattern.find(working)?.takeIf { match ->
            !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
            !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first)) &&
            !clockPreceding.containsMatchIn(working.substring(0, match.range.first))
        }
        val durationMinutes = when {
            rangeDurationMinutes != null -> rangeDurationMinutes
            compoundFractionalDurationMatch != null &&
                (durationMatch == null || compoundFractionalDurationMatch.range.first <= durationMatch.range.first) &&
                (writtenMatch == null || compoundFractionalDurationMatch.range.first <= writtenMatch.range.first) &&
                (fractionalMatch == null || compoundFractionalDurationMatch.range.first <= fractionalMatch.range.first) &&
                (multiQuarterDurationMatch == null || compoundFractionalDurationMatch.range.first <= multiQuarterDurationMatch.range.first) -> {
                val amount = compoundFractionalDurationMatch.groupValues[1].let {
                    it.toIntOrNull() ?: parseWrittenNumber(it)?.toInt()
                }
                val fraction = compoundFractionalDurationMatch.groupValues[2].lowercase()
                amount?.let { (it * 60 + when {
                    fraction.contains("tres") -> 45
                    fraction.contains("dos") || fraction.startsWith("media") -> 30
                    else -> 15
                }).coerceIn(5, 24 * 60) }
            }
            multiQuarterDurationMatch != null &&
                (durationMatch == null || multiQuarterDurationMatch.range.first <= durationMatch.range.first) &&
                (writtenMatch == null || multiQuarterDurationMatch.range.first <= writtenMatch.range.first) &&
                (fractionalMatch == null || multiQuarterDurationMatch.range.first <= fractionalMatch.range.first) -> {
                val amount = multiQuarterDurationMatch.groupValues[1].let {
                    it.toIntOrNull() ?: parseWrittenNumber(it)?.toInt()
                }
                val hasExtraQuarter = multiQuarterDurationMatch.value.contains(Regex("""(?i)\by\s+cuarto\b"""))
                amount?.let { (it * 15 + if (hasExtraQuarter) 15 else 0).coerceIn(5, 24 * 60) }
            }
            durationMatch != null && (fractionalMatch == null ||
                durationMatch.range.first <= fractionalMatch.range.first) &&
                (writtenMatch == null || durationMatch.range.first <= writtenMatch.range.first) -> {
                // La cantidad admite parte decimal ("1.5 horas"/"2,5 horas"): antes el
                // patrón solo capturaba (\d{1,3}) y en "1.5 horas" casaba "5" → 5 h=300
                // con residuo "1." en el título. Ahora se captura el decimal entero y se
                // computa cantidad×60 (horas) o cantidad (minutos) redondeando al minuto.
                val rawAmount = durationMatch.groupValues[1]
                val unit = durationMatch.groupValues[2].lowercase()
                val amount = if (rawAmount.contains('.') || rawAmount.contains(',')) {
                    rawAmount.replace(',', '.').toDoubleOrNull()
                } else {
                    rawAmount.toIntOrNull()?.toDouble()
                }
                amount?.let {
                    (if (unit.startsWith("hora") || unit == "h") it * 60.0 else it)
                        .roundToInt().coerceIn(5, 24 * 60)
                }
            }
            writtenMatch != null && (fractionalMatch == null ||
                writtenMatch.range.first <= fractionalMatch.range.first) -> {
                val amount = parseWrittenNumber(writtenMatch.groupValues[1])
                val unit = writtenMatch.groupValues[2].lowercase()
                amount?.let { (if (unit.startsWith("hora")) it * 60 else it).toInt().coerceIn(5, 24 * 60) }
            }
            fractionalMatch != null -> {
                val text = fractionalMatch.value.lowercase()
                (if (text.contains("media")) 30 else 15).coerceIn(5, 24 * 60)
            }
            else -> null
        }
        // Borrado de tokens de duración del título. Se recolectan los rangos exactos de
        // cada token (con conector "de|durante|por" inmediatamente anterior, si existe) y
        // se aplican en orden descendente para preservar índices. Antes se usaba
        // working.replace(match.value, " ") (replace LITERAL GLOBAL): si el texto del
        // token ("30 min", "2 horas", "dos horas") aparecía varias veces en el título,
        // TODAS las ocurrencias se borraban y se corrompía contenido legítimo.
        // Varios patrones pueden casar el mismo span ("30 min" casa durationMatch Y
        // writtenMatch), así que se deduplican por solapamiento: borrar dos veces el
        // mismo rango además es inútil y, al mutar `working`, dejaría índices fuera de rango.
        val durationBlankRanges = buildList {
            durationMatch?.let { match -> add(connectorRange(working, match.range, match.range.first)) }
            writtenMatch?.let { match -> add(connectorRange(working, match.range, match.range.first)) }
            fractionalMatch?.let { match -> add(connectorRange(working, match.range, match.range.first)) }
            // La duración compuesta abarca "N horas" + fracción; se blanquea entera para
            // no dejar "y media" como residuo. Su rango contiene al de durationMatch/
            // writtenMatch (mismo inicio, mayor extensión), así que la deduplicación por
            // solapamiento conservará solo este (el exterior) en la práctica.
            compoundFractionalDurationMatch?.let { match -> add(connectorRange(working, match.range, match.range.first)) }
            // Multi-cuarto ("tres cuartos de hora"): se blanquea entero (incluido "de
            // hora" y el sufijo "y cuarto") para no dejar residuo en el título.
            multiQuarterDurationMatch?.let { match -> add(connectorRange(working, match.range, match.range.first)) }
        }
        // Varias coincidencias pueden anidarse: con el keyword "duración 30 minutos",
        // [durationMatch] casa toda la frase ("duración 30 minutos") Y el patrón
        // "N minutos" casa solo "30 minutos" (rango interior). Procesar el rango
        // interior primero (orden descendente) y luego saltar el exterior por
        // solapamiento dejaría "duración" como residuo en el título. Se descartan
        // los rangos estrictamente contenidos en otro: así el exterior (el keyword,
        // que abarca "duración"+cantidad+unidad) sobrevive y se blanquea entero.
        // Para rangos idénticos (p. ej. "30 min" casado por durationMatch y
        // writtenMatch a la vez) se conserva uno solo, evitando el doble borrado.
        val dedupedRanges = durationBlankRanges.filter { a ->
            durationBlankRanges.none { b ->
                b !== a && b.first <= a.first && b.last >= a.last &&
                    (b.first < a.first || b.last > a.last)
            }
        }
        var lastEnd = Int.MAX_VALUE
        for (r in dedupedRanges.sortedByDescending { it.first }) {
            if (r.last >= lastEnd) continue // solapa con uno ya borrado → saltar
            working = working.replaceRange(r, " ")
            lastEnd = r.first
        }

        // Categoría: la etiqueta explícita "#cat"/"@cat" del usuario tiene prioridad
        // sobre la inferencia por keywords. Se extrae y se elimina del título.
        val explicitCategoryMatch = explicitCategoryPattern.find(working)
        val explicitCategory = explicitCategoryMatch?.groupValues?.get(1)?.lowercase().orEmpty()
        if (explicitCategoryMatch != null) {
            working = explicitCategoryPattern.replace(working, " ")
        }
        val category = explicitCategory.ifEmpty {
            categories.firstOrNull { (_, keywords) -> keywords.any { working.contains(it, ignoreCase = true) } }?.first.orEmpty()
        }

        // Limpieza de la frase para el título.
        // Orden crítico: partOfDay ("esta mañana") y las horas ("a las 9 de la mañana")
        // deben eliminarse ANTES del borrado genérico de "mañana"/"hoy", porque ambos
        // contienen "mañana"; si se borra primero, dejan restos huérfanos ("esta", "de la").
        working = working
            // Cuando una recurrencia fue detectada por una FRASE ANCLADA ("el 15 de cada
            // mes", "los lunes", "cada quincena"), el adjetivo/adverbio redundante que la
            // acompañaba ("mensual", "semanalmente"...) no se consumió (parseRecurrence
            // retornó antes de fixedPatterns) y filtraba al título. Aquí se limpia; es
            // no-op cuando el adjetivo mismo fue el detector (ya borrado vía phraseRanges).
            .let { value -> if (recurrence.frequency != RecurrenceFrequency.NONE) recurrenceAdjectiveLeakPattern.replace(value, " ") else value }
            .let { value -> partOfDayPattern.replace(value, " ") }
            // c.514 — preserva las horas que son CUENTAS ("a las 3 cajas"): el número es
            // cantidad, no hora, así que NO debe borrarse del título. Antes el fold borraba
            // TODO match de timePatterns sin distinción, mutilando "llamar a las 3 cajas" →
            // "llamar cajas" (se perdía la cantidad). Ahora, por cada match, si es cuenta se
            // conserva intacto y si es cita real se blanquea como siempre. `replace(value) {}`
            // con lambda recibe el MatchResult sobre el texto actual de `acc`, permitiendo
            // consultar el tail justo detrás del match para aplicar [timeMatchIsCountNoun].
            .let { value ->
                var acc = value
                for (pattern in timePatterns) {
                    acc = pattern.replace(acc) { m ->
                        // c.1045: la hora narrativa (cierre de relato en
                        // pretérito) se conserva íntegra, como las cuentas.
                        if (timeMatchIsCountNoun(m, acc) || timeMatchIsPreteriteNarrative(m, acc)) m.value else " "
                    }
                }
                acc
            }
            // c.932: borrado por rangos — la parte del día GOBERNADA por un
            // ordinal narrativo (H3, «las primeras horas de la mañana son las
            // mejores») se conserva íntegra; ver eraseStandalonePartOfDayToken.
            // c.954: borrado por rangos con flag de narrativa en pretérito —
            // la parte del día de «hoy/ayer en la mañana llegó…» se conserva
            // íntegra cuando la fecha no la ancló; ver eraseRelativeDayToken.
            .let { value -> eraseStandalonePartOfDayToken(value, dayPreteriteNarrative) }
            .let { value -> compactDayPartOfDayPattern.replace(value, " ") }
            // c.930: borrado por rangos con guard anti-robo narrativo — las
            // apariciones de ordinal que son CONTENIDO («la primera hora de
            // clase») se conservan íntegras; ver ordinalHoraOccurrenceIsContent.
            .let { value -> eraseOrdinalHoraToken(value, primeraHoraPattern, primeraHoraMatch != null) }
            .let { value -> eraseOrdinalHoraToken(value, ultimaHoraPattern, ultimaHoraMatch != null) }
            .let { value -> alFinalDelDiaPattern.replace(value, " ") }
            .let { value -> alInicioDelDiaPattern.replace(value, " ") }
            .let { value -> amanecerPattern.replace(value, " ") }
            .let { value -> atardecerPattern.replace(value, " ") }
            .let { value -> mediaPartOfDayPattern.replace(value, " ") }
            // "antes de/después de + comida/sueño": consume la frase completa (conector +
            // artículo + ancla) para no dejar residuo ("antes del", "de almuerzo") en el
            // título. Antes el conector se borraba tarde y mutilaba el título
            // ("cita antes del almuerzo"→"cita almuerzo"); ahora se consume junto con el
            // ancla, como las demás horas canónicas (amanecer, atardecer, media X).
            .let { value -> mealSleepAnchorPattern.replace(value, " ") }
            // "temprano"/"muy temprano" como modificador de franja: sólo se borra cuando
            // YA se agendó algo (dueAt != null: parte del día, fecha relativa, hora
            // canónica u hora explícita). "temprano" suelto sin agenda puede ser contenido
            // legítimo ("llegué temprano"), así que no se toca si no hay valor que
            // justifique limpiarlo (evita degradar notas). Cuando sí hay agenda, el
            // adverbio es residuo ("mañana temprano"→"reunión temprano") y se consume
            // como los demás modificadores de franja.
            .let { value -> if (dueAt != null) earlyModifierPattern.replace(value, " ") else value }
            // "el día de mañana"/"el día de hoy"/"para el día de mañana": forma
            // pleonástica coloquial de "mañana"/"hoy". El borrado genérico de abajo
            // consume sólo la palabra "mañana"/"hoy" y deja el residuo "el día de"
            // en el título (p. ej. "reunión el día de" en vez de "reunión"), que es
            // contenido capturado degradado (P1: integridad de datos). Se consume la
            // frase completa primero; el resto del regex sigue borrando los tokens
            // sueltos ("hoy"/"ayer"/"anteayer"/"pasado mañana"/"antepasado mañana").
            .replace(Regex("""(?i)\b(?:para\s+)?(?:el|del)\s+d[ií]a\s+de\s+(?:ma[nñ]ana|hoy)\b"""), " ")
            // c.674/675 — "este (mismo )?día" ≡ hoy: el borrado genérico de abajo
            // consume "hoy"/"mañana"/etc. sueltos, pero esta frase post-puesta quedaría
            // íntegra como residuo en el título; se borra completa antes del genérico.
            .let { value -> if (dueAt != null) value.replace(Regex("""(?i)\beste\s+(?:mismo\s+)?d[ií]a\b"""), " ") else value }
            // "de hoy en adelante": coloquialismo sin cantidad que cae al keyword "hoy"
            // (fecha = hoy, correcta), pero cuyo borrado palabra-suelta dejaba el residuo
            // "en adelante" en el título (P3, follow-up de c.667). Se consume la frase
            // íntegra antes del borrado genérico de abajo.
            .replace(Regex("""(?i)\bde\s+hoy\s+en\s+adelante\b"""), " ")
            // c.846 — "después de mañana" ≡ "pasado mañana" (fecha +2 resuelta en el
            // `when` de fecha): se consume la frase ÍNTEGRA antes del borrado genérico
            // de abajo. Si no, el genérico consume sólo "de mañana" y deja "después"
            // huérfano al final: el recorte de conector final (c.548) sólo conoce
            // "después" CON tilde, así "Cita despues de manana" quedaba "Cita despues"
            // (título degradado, P1 captura). Con tilde ya salía limpio vía c.548.
            .replace(Regex("""(?i)\bdespu[eé]s\s+de\s+ma[nñ]ana\b"""), " ")
            // Calificador "de/del/desde + día relativo" ("reunión de mañana", "tarea de hoy",
            // "cita de ayer", "llamada de pasado mañana", "trabajo desde hoy", "estudio desde
            // mañana"): la preposición "de"/"del"/"desde" antes de un marcador de día relativo
            // es siempre un calificador temporal (genitivo de posesión temporal / punto de
            // partida temporal en español). Antes el borrado de "mañana"/"hoy"/etc. como palabra
            // suelta dejaba el conector como residuo en el título ("llamar de", "reunión desde"
            // en vez de "llamar"/"reunión") — contenido capturado degradado (P1). Se consume el
            // conector junto con el día relativo. El \b impide coincidir dentro de palabras como "desde"→ no aplica.
            // c.954: borrado por rangos con flag de narrativa en pretérito —
            // el «hoy/ayer» cabeza de «hoy/ayer en la mañana llegó…» se
            // conserva íntegro cuando la fecha no la ancló; ver
            // [eraseRelativeDayToken].
            .let { value -> eraseRelativeDayToken(value, dayPreteriteNarrative, ayerRangeGenitiveNarrative) }
            // c.927: el «mañana» suelto se borra con guard anti-robo — las apariciones
            // que son CONTENIDO ("la mañana del accidente", "esa misma mañana") se
            // conservan íntegras (ver [mananaOccurrenceIsContent]); el resto se borra
            // con su conector "de/del/desde" como hacía la alternativa de arriba.
            // c.954: con flag — la «mañana» de la parte del día narrativa en
            // pretérito («hoy en la mañana llegó…») se conserva íntegra.
            .let { value -> eraseMananaDateToken(value, dayPreteriteNarrative) }
            // "el lunes 24": consume el weekday + el número de día JUNTOS para que el "24"
            // no quede como residuo del título. Va ANTES que weekdayPattern.replace (que
            // sólo borraría "el lunes"). Guard `dueAt != null`: no inventa fecha si no se
            // resolvió (p. ej. forma con mes nombrado, excluida por el lookahead del patrón).
            .let { value -> if (weekdayDayMatch != null && dueAt != null) weekdayDayPattern.replace(value, " ") else value }
            // c.936: borrado por rangos con guard anti-robo narrativo — el
            // weekday genitivo de una cadena narrativa H3 protegida («las
            // primeras horas de la mañana del lunes son tranquilas») se
            // conserva íntegro; ver ordinalHoraNarrativeWeekdayRanges.
            .let { value ->
                eraseWeekdayToken(
                    value, primeraHoraMatch != null, ultimaHoraMatch != null,
                    forcePreteriteNarrativeAnchor = weekdayPreteriteNarrative
                )
            }
            .let { value -> weekendPattern.replace(value, " ") }
            // "que viene" queda como residuo cuando la fecha asociada (fin de
            // semana, día de la semana) se consume pero la frase modificadora no.
            // Se borra aquí para no dejar basura en el título.
            .replace(Regex("""(?i)\bque\s+viene\b"""), " ")
            // Solo se elimina la fecha "5 de marzo" si el mes es válido: así "9 de la"
            // (en "a las 9 de la tarde") no se destruye y deja restos en el título.
            // Se consume también la preposición genitiva "del"/"de" inmediatamente
            // anterior cuando la fecha es válida: "concierto del 12 de octubre",
            // "reporte del proyecto del 15 de agosto" → el "del" que introduce la fecha
            // es parte del modificador temporal, no del título. Sin esto el conector
            // sobrevivía como residuo ("concierto del", "reporte del proyecto del") —
            // contenido capturado degradado. El prefijo es OPCIONAL y no capturador, así
            // los grupos de monthNamePattern (día/mes/año) no cambian y la validación de
            // mes sigue gobernando el borrado: "9 de la" (mes inválido) se conserva y,
            // con él, su "de" precedente ("a las 9 de la tarde" no se mutila).
            .replace(monthNameStripPattern) { m ->
                if (months.any { (name, _) ->
                        m.groupValues[2].equals(name, ignoreCase = true)
                    }) " " else m.value
            }
            .let { value -> numericDatePattern.replace(value, " ") }
            // "el 15" suelto ya consumido como fecha; se borra el residuo del título.
            .let { value -> dayOfMonthPattern.replace(value, " ") }
            // "pago del 15": día del mes con "del" ya consumido como fecha; se borra el
            // residuo del título. El patrón incluye sus guardas (no casa rangos "del 20
            // al 25" ni "del 15 de septiembre"/"del 15 de cada mes", que ya se borraron
            // arriba vía sus patrones propios); aquí sólo queda el día suelto que ancló
            // fecha. Lookbehind del patrón (no "antes del") ya excluye el plazo, cuyo
            // conector lo borra el paso siguiente.
            .let { value -> if (delDayOfMonthMatch != null && dueAt != null) delDayOfMonthPattern.replace(value, " ") else value }
            // "antes del 30": se consume la frase COMPLETA (conector + día) para que
            // no quede el "30" como residuo en el título. La fecha ya se resolvió arriba.
            // Los casos con mes ("antes del 30 de agosto") no casan aquí (lookahead) y
            // su conector "antes del" lo borra el paso siguiente.
            .let { value -> beforeDeadlineDayPattern.replace(value, " ") }
            // Conectores de plazo/residuo temporal huérfanos que sobreviven tras consumir
            // la fecha/hora: "para mañana" (propósito→fecha relativa ya consumida).
            // NOTA (c.497): aquí antes se borraba "\bpara\s+el\b", y (c.498) también
            // "\bantes\s+del?\b" y "\bhasta\s+el\b", INCONDICIONALMENTE. A esa altura del
            // pipeline cualquier "el <ancla temporal>" / "antes del <fecha>" / "hasta el
            // <fecha>" ya fue consumido por sus patrones (weekday, monthNameStrip,
            // beforeDeadlineDay, etc.), de modo que el "antes del"/"hasta el"/"para el"
            // REMANENTE sólo aparece ante un SUSTANTIVO DE CONTENIDO ("Estudiar hasta el
            // examen", "Preparar antes del examen", "Estudiar para el examen") y borrarlo
            // mutilaba el título (P1: integridad de datos). El residuo de conector que SÍ
            // queda huérfano al FINAL del título (la fecha ya se consumió, p. ej. "pagar
            // antes del" tras borrar "15 de agosto") lo elimina la limpieza de conector
            // huérfano al final del título (más abajo, simétrica a la de "para"/"el" de
            // c.497 y al genitivo de c.493).
            .replace(Regex("""(?i)\bpara\s+ma[nñ]ana\b"""), " ")
            // c.574: modismos adverbiales de prioridad "primero que nada" / "antes que
            // nada" ("first of all / first thing") son puro énfasis, SIN semántica de
            // fecha/hora ni de contenido: sobrevivían íntegros como residuo del título
            // en TODA posición ("reunión mañana primero que nada" → "reunión primero que
            // nada", "primero que nada pagar la luz" → título entero contaminado con el
            // modismo de prefijo, "reunión antes que nada mañana" → "reunión antes que
            // nada"). Muy cotidianos en español; degradan la captura del título (P1:
            // título limpio / captura ultrarrápida). Se eliminan INCONDICIONALMENTE (no
            // llevan fecha ni hora, así que no interfieren con el parseo y no requieren
            // el guard `dueAt != null` de los conectores huérfanos). El "que nada" los
            // delimita sin ambigüedad: NO se toca "primero de mes" (fecha = día 1, ya
            // resuelto arriba), "primer lunes del mes" (ordinal+weekday), "lo primero
            // que haré" / "lo primero de la lista" ("lo primero" = contenido legítimo,
            // sin "que nada") ni "para empezar" (ambiguo: idiomático sólo ante verbo,
            // contenido ante sustantivo: "para empezar el proyecto"). \b y la secuencia
            // literal "que nada" evitan colisiones con subcadenas legítimas.
            .replace(Regex("""(?i)\b(?:primero|antes)\s+que\s+nada\b"""), " ")
            // El verbo de recordatorio sin cantidad ("recuérdame", "avísame",
            // "no dejes que olvide") expresa intención de aviso, no contenido; se
            // elimina del título. Se hace aquí (tras consumir fechas/horas) para no
            // alterar el parseo de "recuérdame mañana a las 3" (donde "mañana" es fecha).
            // PERO si limpiar el verbo dejara el título vacío (el usuario escribió SÓLO
            // el recordatorio + frases de agenda, p. ej. "recordatorio cada 2 días a las
            // 8"), se CONSERVA el verbo/nombre como título honesto: antes el verbo se
            // eliminaba último → título en blanco → el respaldo `ifBlank { original }`
            // resucitaba la frase cruda completa (cadencia/hora/fecha como basura visible).
            // `reminderVerbIsOnlyContent` marca ese caso (verb = único contenido): sirve
            // para decidir el offset abajo, porque cuando el usuario da SÓLO el verbo +
            // una hora ("recuérdame en 30 min", "recuérdame mañana", "recuérdame a las 5")
            // la hora que dio ES la hora del aviso, no la hora de la cita con un nudge
            // previo. Aplicar 30 min antes ahí hacía que el aviso se disparara hasta un
            // día antes ("recuérdame el viernes" → aviso el jueves). Se excluyen las
            // recurrencias ("recuérdame cada lunes a las 8"), donde la hora es de cita.
            .let { value ->
                if (!hasBareReminderVerb) value
                else {
                    val stripped = bareReminderVerbPattern.replace(value, " ")
                        .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
                        // Verbo consumido: el "que" inicial que queda es subordinador
                        // puro ("recuérdame que X" = "recuérdame X"), no contenido.
                        // Si borrarlo dejara el título vacío ("recuérdame que mañana"
                        // → la fecha ya se consumió), se conserva: prefiero un título
                        // con residuo a uno en blanco (ver guarda isNotBlank abajo).
                        .replace(Regex("""(?i)^\s*que\s+"""), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim(' ', ',', '.', '-')
                    if (stripped.isNotBlank()) stripped else value
                }
            }
            .let { value ->
                // Genitivo temporal huérfano: "cita de 3 pm", "reunión del mediodía",
                // "comida de medianoche", "reunión de la medianoche". Las horas/canónicas
                // (timePatterns, mediodía/medianoche, reloj HH:MM, am/pm) se borran arriba
                // PERO la preposición genitiva "de/del/de la" que las introducía sobrevive
                // como residuo al final del título — contenido capturado degradado (P1:
                // el usuario ve "reunión del" en vez de "reunión"). Simétrico del consumo
                // de genitivo de los patrones de hora con "de la tarde" y del genitivo de
                // día relativo (más abajo), pero aquí el token temporal ya fue borrado, así
                // que se elimina el conector huérfano que quedó al final. Sólo cuando se
                // resolvió una hora/fecha (dueAt != null): sin agenda, "de" final puede ser
                // contenido legítimo ("nota de"). El conector de rango "de 9 a 11" ya lo
                // consumió timeRangePattern (su prefijo opcional "de"), así que aquí no
                // queda "de" en esos casos y el paso es no-op (no rompe los tests de rango).
                // Anclado al final (con espacios) para no tocar "de" legítimo seguido de
                // contenido ("reunión de equipo a las 5" → tras borrar "a las 5" queda "de
                // equipo", "de" NO está al final → se conserva). c.493.
                //
                // El `\b` antes de `de` evita recortar el sufijo "de" de palabras que
                // terminan en "de" pero NO son la preposición: "desde" ("vacaciones desde
                // el lunes" → al borrar weekday+artículo queda "vacaciones desde", y sin
                // `\b` el "de" final se recortaba dejando "vacaciones des", P1 título
                // corrupto) y "adrede" ("cita adrede mañana" → "cita adre"). c.499.
                //
                // "desde" como conector de INICIO temporal ("vacaciones desde el lunes",
                // "trabajo desde esta semana"): el rewriter desdeRewriter sólo normaliza
                // "desde" + HORA/parte-del-día (no fechas de calendario), así que al
                // resolver y borrar la fecha, "desde" queda como conector huérfano al
                // final ("vacaciones desde"). Se consume aquí —misma lógica que el
                // genitivo "de": sólo cuando se resolvió fecha (dueAt != null), porque
                // "desde" sin agenda es contenido legítimo ("nadar desde temprano",
                // "llamar desde casa", "reunión desde el equipo"). Como "desde" termina
                // en "de", se prueba ANTES que `\bde` para consumirlo entero (si no,
                // `\bde` no casaría por el límite y "desde" sobreviviría como orphan).
                // c.499.
                //
                // Simétrico para "a partir de/del" + FECHA de calendario ("fumar menos
                // a partir de mañana", "dieta a partir del viernes", "ahorrar a partir
                // del 1 de septiembre"): aPartirDeRewriter sólo reescribe "a partir de"
                // + HORA/parte-del-día (no fechas), así que al resolver y borrar la fecha
                // el conector queda huérfano al final ("fumar menos a partir de").
                // Antes la limpieza sólo consumía "desde"/"del"/"de", así que "a partir
                // de" recortaba sólo su "de" final y dejaba "a partir" como residuo
                // (título degradado, P1 captura). Se consume la frase entera, ANTES que
                // las demás alternativas (contiene "de", si no iría primero el motor
                // casaría sólo "de"). Mismo guard dueAt != null: sin agenda, "a partir
                // de" es contenido legítimo ("decisión a partir de los datos").
                // c.546: "hacia" y "durante" NO se reescriben a conector canónico;
                // al resolver y borrar fecha/hora quedaban huérfanos al final
                // ("entregar hacia", "estudiar durante"). Mismo guard dueAt != null:
                // sin agenda son contenido legítimo ("trabajar durante la semana").
                // c.548: "después" es el mismo caso ("después del lunes" -> "revisar
                // después"), simétrico a "antes" (c.497) que SÍ se limpiaba. Sin agenda
                // "después de la reunión" se conserva (contenido legítimo).
                // End-anchored: "caminar hacia el parque el sábado" (hacia NO al
                // final) se conserva. "durante" puede llevar artículo rezagado
                // ("durante la mañana" -> tras consumir "mañana" queda "durante la").
                if (dueAt != null)
                    value.replace(Regex("""(?i)\s*(?:después|(?:hacia|durante)(?:\s+(?:la|el|los|las|del|de))?|a\s+partir(?:\s+d(?:e|el))?|desde|de\s+la|del|\bde)\s*$"""), " ")
                else value
            }
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            // Conector de plazo/residuo temporal huérfano al FINAL del título (la fecha ya
            // se consumió arriba): "pagar antes del" (tras borrar "15 de agosto"),
            // "cobrar hasta el" (raro: el lookahead de "hasta " consume la mayoría),
            // "enviar hasta". Simétrico a la limpieza de "para"/"el" (c.497) y al
            // genitivo (c.493). END-ANCHORED: "antes del examen" / "hasta el examen" NO
            // están al final (los sigue el sustantivo de contenido) → se conservan
            // íntegros (P1: integridad de datos). Sólo se borra el conector VIUDO tras
            // consumirse su fecha. "antes del" debe ir antes que "antes" en la
            // alternación para no dejar un "del" suelto.
            .replace(Regex("""(?i)\s*\b(?:antes\s+del?|hasta\s+el|antes|hasta)\b\s*$"""), " ")
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

        // ¿El verbo de recordatorio era el ÚNICO contenido tras limpiar la agenda?
        // (sin recurrencia: las cadencias mantienen la hora como hora de cita).
        // c.678: el "que" inicial que encabeza el encuadre ("recuérdame que",
        // "que no se me olvide") es subordinador puro, no contenido: sin él la
        // comprobación no veía el encuadre reflexivo como verb-only y aplicaba
        // 30 min de nudge a una hora que el usuario dio como hora DEL AVISO
        // (offset 0, simétrico a "recuérdame mañana"). `\b` evita recortar el
        // prefijo "que" de contenido legítimo ("queda", "quédate").
        val reminderVerbIsOnlyContent = hasBareReminderVerb &&
            recurrence.frequency == RecurrenceFrequency.NONE &&
            bareReminderVerbPattern.replace(working, " ")
                .replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '-')
                .replace(Regex("""(?i)^que\b\s*"""), " ")
                .trim(' ', ',', '.', '-').isBlank()

        val confidence = when {
            effectiveRelativeDueAt != null -> 1.0f
            dueAt != null && parsedTime != null -> 1.0f
            dueAt != null -> 0.9f
            priority != TaskPriority.NORMAL || durationMinutes != null || reminderOffsetMinutes != null ||
                recurrence.frequency != RecurrenceFrequency.NONE || category.isNotEmpty() -> 0.6f
            else -> 0.35f
        }

        // Respaldo de título vacío: si `working` quedó en blanco (el usuario escribió
        // SÓLO una frase de agenda sin acción), se usa el `original` PERO con los mismos
        // reescritores de conectores aplicados (c.371/c.378), así el respaldo no resucita
        // "al"/"de aquí al" crudos como título visible ("al viernes"→"el viernes").
        // Los reescritores de anclaje de hora (c.435/c.436) llevan aquí el mismo guard
        // anti-cuenta (c.442) que en `parse`: si el anclaje "las N" en punto va seguido
        // de un sustantivo de cantidad, NO se reescribe (preserva el número en el título).
        // Pasos con variable intermedia `tf` para que el guard inspeccione el tail exacto.
        var tf = original
            .replace(deAquiConnectorRewriter, "el")
            .replace(deAquiToRewriter, " ")
            .replace(alWeekdayRewriter) { m -> "el${m.groupValues[1]}" }
            .replace(aEsoDePartOfDayRewriter) { m ->
                val part = m.groupValues[1]
                when {
                    part.startsWith("del mediod") -> "al mediodía"
                    part.startsWith("de la ") -> "a la " + part.substring(6)
                    else -> part
                }
            }
        // c.435: respaldo normaliza "a partir de" + anclaje de hora (simétrico al
        // rewriter en `parse`): un standalone de franja ("a partir de las 3 de la tarde"
        // solo) muestra "a las 3 de la tarde" en vez de resucitar "a partir de" crudo.
        // Guard anti-cuenta (c.442): si el anclaje "las N" en punto va seguido de un
        // sustantivo de cantidad ("a partir de las 3 cajas"), NO se reescribe (preserva
        // el número en el título). Pasos con variable intermedia para inspeccionar el tail.
        tf = aPartirDeRewriter.replace(tf) { m ->
            val las = m.groupValues[1]
            val la = m.groupValues[2]
            val bare = m.groupValues[3]
            if (las.isNotEmpty()) {
                val tail = tf.substring(m.range.last + 1)
                if (!countNounFollowerPattern.containsMatchIn(tail)) return@replace m.value
            }
            when {
                las.isNotEmpty() -> "a $las"
                la.isNotEmpty() -> "a $la"
                else -> "al $bare"
            }
        }
        // c.436: respaldo normaliza "desde" + anclaje de hora (simétrico al rewriter en
        // `parse`). Mismo guard anti-cuenta (c.442) que arriba.
        tf = desdeRewriter.replace(tf) { m ->
            val las = m.groupValues[1]
            val la = m.groupValues[2]
            val bare = m.groupValues[3]
            if (las.isNotEmpty()) {
                val tail = tf.substring(m.range.last + 1)
                if (!countNounFollowerPattern.containsMatchIn(tail)) return@replace m.value
            }
            when {
                las.isNotEmpty() -> "a $las"
                la.isNotEmpty() -> "a $la"
                else -> "al $bare"
            }
        }
        val titleFallback = tf
            // c.382: respaldo normaliza el marcador aproximado "a eso de las N"
            // (simétrico al fold de `approximateTimePatterns` en `parse`): un standalone
            // de hora aproximada ("a eso de la una y media" solo) muestra "a la una y
            // media" en vez de resucitar "a eso de" crudo. Consistente con c.379/c.381.
            .let { approximateTimePatterns.fold(it) { acc, p -> p.replace(acc, "a ") } }
            // c.868: respaldo consume "tipo N" desnudo con evidencia de reloj
            // (simétrico al rewriter en `parse`).
            .let { bareTipoTimePattern.replace(it, "") }
            .replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '-')

        return ParsedTaskInput(
            title = working.ifBlank { titleFallback }.take(240),
            dueAt = dueAt,
            priority = priority,
            durationMinutes = durationMinutes,
            // Si el usuario pidió un recordatorio ("recuérdame") sin cantidad y hay fecha
            // límite, se asume 30 min antes (convención del proyecto, ver CommitmentEngine).
            // EXCEPCIÓN: cuando el verbo es el ÚNICO contenido ("recuérdame en 30 min",
            // "recuérdame mañana", "recuérdame a las 5", "avísame el viernes 5pm"), la hora
            // que dio el usuario ES la hora del aviso, no la hora de una cita con nudge
            // previo: offset 0 (el recordatorio se dispara EN dueAt). Sin dueAt no se
            // programa reminderAt, así que no se falsifica el offset.
            reminderOffsetMinutes = reminderOffsetMinutes
                ?: when {
                    hasBareReminderVerb && dueAt != null && reminderVerbIsOnlyContent -> 0
                    hasBareReminderVerb && dueAt != null -> BARE_REMINDER_DEFAULT_OFFSET_MINUTES
                    else -> null
                },
            recurrence = recurrence.frequency,
            recurrenceInterval = recurrence.interval,
            // MONTHLY ordinal de weekday ("primer lunes de cada mes") → codificación
            // "ord:weekday" que `RecurrenceEngine` decodifica para anclar el N-ésimo/último
            // día de la semana cada ciclo. MONTHLY "fin de mes" (c.257) → sentinel "EOM"
            // que ancla al último día REAL del mes (no omite meses cortos). WEEKLY usa
            // `days` (lista) y el resto queda vacío (día del mes puro: monthlyDayOfMonth
            // se ancla vía dueAt).
            recurrenceDays = when {
                recurrence.monthlyDays != null ->
                    "d:" + recurrence.monthlyDays.joinToString(",")
                recurrence.monthlyOrdinalWeekday != null ->
                    recurrence.monthlyOrdinalWeekday!!.let { (ord, wd) -> "$ord:$wd" }
                recurrence.monthlyLastDay -> RecurrenceEngine.LAST_DAY_OF_MONTH
                recurrence.monthlyLastBusinessDay -> RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH
                else -> recurrence.days.joinToString(",")
            },
            category = category,
            confidence = confidence
        )
    }

    private data class RecurrenceResult(
        val frequency: RecurrenceFrequency,
        val interval: Int,
        val days: List<Int>,
        val phraseRanges: List<IntRange>,
        /** Para recurrencia mensual anclada a un día del mes ("el 15 de cada mes"). */
        val monthlyDayOfMonth: Int? = null,
        /** Para recurrencia mensual anclada al ÚLTIMO día REAL del mes ("cada fin de
         *  mes", c.257). A diferencia del anclaje por día del mes (día 31), que omite
         *  meses cortos (febrero, abril, junio, septiembre, noviembre), este anclaje
         *  aterriza siempre en el último día del mes objetivo. Se emite como
         *  `recurrenceDays = "EOM"` ([RecurrenceEngine.LAST_DAY_OF_MONTH]) para que el
         *  motor despache cada ciclo al último día real del mes objetivo. Sólo afecta
         *  el vencimiento recurrente; la PRIMERA fecha la fija el límite ("fin de mes"). */
        val monthlyLastDay: Boolean = false,
        /** Para recurrencia mensual anclada al ÚLTIMO DÍA HÁBIL del mes ("el último
         *  día hábil/laborable de cada mes", c.575). Igual que [monthlyLastDay] pero
         *  retrocede al viernes anterior cuando el último día real cae en sábado/domingo.
         *  Se emite como `recurrenceDays = "EOM-BD"` ([RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH]).
         *  "Hábil" = Lun-Vie (sin festivos locales: la app no conoce la jurisdicción;
         *  se documenta la limitación en lugar de fingir un calendario). */
        val monthlyLastBusinessDay: Boolean = false,
        /** Para recurrencia mensual ORDINAL de día de la semana ("el primer lunes de
         *  cada mes", "el último viernes del mes" recurrente): `(ord, weekday)` con
         *  `ord ∈ {1,2,3,4,-1}` (-1 = último) y `weekday ∈ 1..7` (ISO, 1=lunes). Se
         *  emite como `recurrenceDays = "ord:weekday"` para que `RecurrenceEngine`
         *  ancle cada ciclo al N-ésimo/último día de la semana (c.216); sin esto el
         *  motor sólo veía el día del mes y "primer lunes" derivaba al día 7. */
        val monthlyOrdinalWeekday: Pair<Int, Int>? = null,
        /** Hora canónica de la parte del día para "cada mañana/tarde/noche/madrugada"
         *  (hábito diario): 09:00/15:00/21:00/04:00. Se usa como hora de respaldo de la
         *  primera ocurrencia y como contexto PM para horas sin meridiem. */
        val partOfDayTime: LocalTime? = null,
        val partOfDayIsPm: Boolean = false,
        /** Vencimiento inmediato (epoch ms) para cadencias sub-diarias que el motor de
         *  recurrencia no puede representar ("cada 8/12 horas": no existe frecuencia
         *  HOURLY). En vez de dejar la tarea SIN fecha (medicación silenciosamente
         *  olvidada: recordatorio jamás disparaba, jamás en What Now), se saca la PRIMERA
         *  dosis a la superficie venciendo ahora —honesto sobre lo que la app SÍ puede
         *  (un aviso ahora) sin fingir una repetición horaria que no existe. Simétrico de
         *  "ahora"/"lo antes posible" → now. Sólo aplica cuando no hay otra fecha/hora. */
        val immediateDueAt: Long? = null,
        /** Para recurrencia mensual anclada a una LISTA de días del mes ("el 1 y 15 de
         *  cada mes", c.315): días 1..31 únicos, ordenados. Se emite como
         *  `recurrenceDays = "d:N1,N2"` para que `RecurrenceEngine` dispare una
         *  ocurrencia por cada día de la lista dentro del ciclo (quincena/nómina).
         *  El 1er vencimiento lo fija el día más cercano al hoy; los siguientes los
         *  genera el motor alternando entre días. */
        val monthlyDays: List<Int>? = null,
        /** c.341: mes nombrado explícito tras la lista de días ("reunión los días 15 y
         *  30 de septiembre"). Antes el día-lista consumía los dígitos y `monthNamePattern`
         *  (que exige dígito+mes) ya no casaba → el mes nombrado se ignoraba y la 1ª fecha
         *  se anclaba al mes actual (agosto en vez de septiembre: cita en mes erróneo, P1
         *  de datos). Aquí guardamos el mes/año nombrados para que la 1ª ocurrencia de la
         *  lista se ancle a ese mes (paridad con `parseMonthNameDate`). Sólo afecta a la
         *  PRIMERA fecha; las siguientes las genera `RecurrenceEngine` mensualmente. */
        val monthlyNamedMonth: Int? = null,
        val monthlyNamedYear: Int? = null,
        /** c.344: calificador relativo "del mes que viene/próximo/entrante" tras la
         *  lista de días ("reunión los días 15 y 30 del mes que viene"). Antes se
         *  ignoraba (scanTrailingNamedMonth sólo reconocía meses nombrados) y la 1ª
         *  fecha se anclaba al mes ACTUAL en vez del siguiente (rutina quincenal en
         *  mes erróneo, P1). True = anclar la 1ª ocurrencia al mes siguiente. */
        val monthlyNextMonth: Boolean = false
    )

    /** Captura normalizada de un anclaje mensual ORDINAL de weekday ("el primer lunes del
     *  mes" directa, o "cada mes el primer lunes" con cadencia precedente). Unifica ambas
     *  formas para que el motor ancle cada ciclo al N-ésimo/último weekday del mes y para
     *  resolver la primera fecha. monthName/yearStr sólo aplican a la forma directa. */
    private data class OrdinalMonthlyCapture(
        val ordinalWord: String,
        val weekdayWord: String,
        val isNext: Boolean,
        val isPrevious: Boolean,
        val monthName: String?,
        val yearStr: String?
    )

    private fun parseRecurrence(working: String, now: Long): RecurrenceResult {
        val base = RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), emptyList())
        val phrases = mutableListOf<IntRange>()

        // Números escritos admitidos como intervalo de cadencia: se reutiliza el
        // fragmento compartido [writtenNumberGroup] (1-99, incluida la forma
        // compuesta "treinta y cinco"), de modo que "cada dos semanas los lunes"
        // se comporte igual que "cada 2 semanas los lunes" y la rama de días
        // comparta el mismo universo de cantidades que el resto del parser.

        // "cada mañana/tarde/noche/madrugada" (y "todas las mañanas/tardes/noches") como
        // recurrencia DIARIA con hora canónica de la parte del día. Es la forma natural
        // más común de un hábito cotidiano en español ("meditar cada mañana", "tomar
        // pastillas cada mañana", "pasear al perro cada tarde"). Antes NO se reconocía:
        // "mañana" colisionaba con la fecha "mañana" (día siguiente) y el hábito quedaba
        // como tarea ÚNICA para mañana sin recurrencia (P1: la rutina diaria se perdía,
        // el recordatorio disparaba una sola vez y nunca más). Se procesa PRIMERO: así
        // "mañana" deja de ser candidato a fecha y la hora canónica sustituye a la del
        // respaldo genérico (09:00). "todos los días" / "diariamente" (sin parte del día)
        // cae abajo en fixedPatterns y conservan su hora de respaldo 09:00.
        val partOfDayDailyMap = mapOf(
            "mañana" to LocalTime.of(9, 0),
            "manana" to LocalTime.of(9, 0),
            "tarde" to LocalTime.of(15, 0),
            "noche" to LocalTime.of(21, 0),
            "madrugada" to LocalTime.of(4, 0)
        )
        val partOfDayDailyPattern = Regex(
            """(?i)\bcada\s+(ma[nñ]ana|manana|tarde|noche|madrugada)\b""" +
                """|\btodas\s+las\s+(ma[nñ]anas|mananas|tardes|noches|madrugadas)\b"""
        )
        partOfDayDailyPattern.find(working)?.let { match ->
            val group = match.groupValues.firstOrNull { it.isNotBlank() && it != match.value }?.lowercase()
            val key = when {
                group == null -> null
                group.endsWith("s") -> group.dropLast(1)
                else -> group
            }
            val time = key?.let { partOfDayDailyMap[it] }
            if (time != null) {
                phrases += match.range
                val isPm = key == "tarde" || key == "noche"
                return RecurrenceResult(
                    RecurrenceFrequency.DAILY, 1, emptyList(), phrases,
                    partOfDayTime = time, partOfDayIsPm = isPm
                )
            }
        }

        // Intervalo de cadencia que puede acompañar a una lista de días ("cada 2
        // semanas los lunes", "cada quincena los lunes y viernes", "cada 3 semanas
        // entre semana"). Sin esto, la rama de días se quedaba solo con la lista y
        // devolvía interval=1: la cadencia quedaba como "todas las semanas" aunque el
        // usuario pidió quincenal/cada-N-semanas, y la frase de intervalo sobraba como
        // residuo en el título. Devuelve el intervalo y el rango a consumir, o null si
        // no hay intervalo explícito (cadencia semanal normal).
        fun detectWeekInterval(): Pair<Int, IntRange>? {
            // N puede ser dígito O número escrito ("cada dos semanas los lunes"):
            // la forma escrita debe comportarse igual que la con dígitos. Antes
            // solo `\d{1,3}` se reconocía aquí, así "cada dos semanas los lunes"
            // devolvía null → interval=1 (el doble de frecuente) y "cada dos
            // semanas" quedaba como residuo en el título. `parseWrittenNumber`
            // resuelve la palabra; el grupo está acotado a los números conocidos
            // para no colisionar con "semanas".
            // El determinante plural "todas las" es la forma hablada de "cada"
            // para semanas espaciadas ("todas las dos semanas los lunes"): sin él
            // esa cadena caía a interval=1 (cada semana, el doble de frecuente) y
            // "todas las dos semanas" quedaba como residuo. Mismo cierre que c.275
            // aplicó a "bisemanal los lunes". No casa "todas las semanas" (sin
            // número → cadencia semanal normal resuelta en otra rama).
            Regex("""(?i)\b(?:cada|todas\s+las)\s+(\d{1,3}|$writtenNumberGroup)\s*semanas?\b""").find(working)?.let { m ->
                val rawN = m.groupValues[1]
                val n = rawN.toLongOrNull()?.toInt()
                    ?: parseWrittenNumber(rawN)?.toInt()
                if (n != null) {
                    return n.coerceIn(1, 366) to m.range
                }
            }
            Regex("""(?i)\b(?:cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""").find(working)?.let { m ->
                return 2 to m.range
            }
            // "bisemanal"/"bisemanalmente" = cada dos semanas (quincenal en cadencia de
            // semana). Aquí acompaña a una lista de días ("bisemanal los lunes" → cada 2
            // semanas los lunes), igual que "cada dos semanas los lunes" o "cada quincena
            // los lunes". Sin esto, "bisemanal los lunes" caía a interval=1 (cada semana,
            // el doble de frecuente) y "bisemanal" quedaba como residuo en el título.
            Regex("""(?i)\bbisemanal(?:mente)?\b""").find(working)?.let { m ->
                return 2 to m.range
            }
            // "semana por medio" aquí acompaña a una lista de días ("fútbol semana por
            // medio los sábados" -> cada 2 semanas los sábados), igual que "cada dos
            // semanas los sábados". Sin esto, el combo caía a interval=1 (cada sábado, el
            // doble de frecuente) y "semana por medio" quedaba pegado al título. La forma
            // SIN días ("fisioterapia semana por medio") no llega aquí (ningún
            // dayListPattern casa) y la resuelve alternatePeriodPattern más abajo (WEEKLY/2).
            Regex("""(?i)\b(?:una\s+)?semanas?\s+por\s+medio\b""").find(working)?.let { m ->
                return 2 to m.range
            }
            return null
        }

        // "entre semana" / "días laborables/hábiles/de semana" / "de lunes a viernes"
        // como recurrencia semanal de lunes a viernes (hábito cotidiano: gimnasio,
        // trabajo, estudio). Se evalúa ANTES que dayListPattern: "lunes a viernes" es
        // un rango (Lun-Vie), no la lista ["lunes"]; si dayListPattern ganara en
        // "los lunes a viernes" capturaría solo "lunes" y el viernes se perdería.
        // Antes quedaba sin recurrencia (tarea única: la intención de repetir se
        // perdía) y, peor, "de lunes a viernes" dejaba "lunes" como fecha suelta y
        // residuo en el título. La primera ocurrencia la resuelve la rama WEEKLY+days
        // (próximo día hábil).
        //
        // "entre lunes y viernes" / "entre lunes a viernes" es la misma semana laboral
        // con conectores "entre...y/a". Antes esta forma NO casaba aquí y caía a
        // dayListPattern, que la leía como lista de DOS días sueltos {lunes, viernes}
        // (recDays="1,5": rutina mutilada en silencio) y dejaba "entre" como residuo
        // en el título. Ahora se resuelve como el rango Lun-Vie, simétrico a "de lunes
        // a viernes". c.282.
        //
        // "hasta" como conector de cierre (c.380): "de lunes hasta viernes" es la misma
        // semana laboral. La normalización de plazo "hasta el viernes" (c.134) no toca
        // "hasta viernes" (exige artículo), así llega intacto aquí. Simétrico a "a".
        // Rango de weekdays GENERALIZADO a cualquier par de días (no sólo Lun-Vie)
        // con conectores de rango sin artículo: "de martes a jueves", "los lunes a
        // viernes", "lunes hasta viernes", "entre martes y jueves". Antes sólo casaba
        // el par literal "lunes ... viernes"; cualquier otro par (mar-jue, mié-vie,
        // dom-jue) caía a dayListPattern, que capturaba SOLO el día inicial como lista
        // de un elemento y el día de cierre sobrevivía como residuo del título
        // ("reunión a") y la rutina se perdía (P1: hábito olvidado, sin recurrencia).
        // Se expande al rango inclusivo hacia adelante (con wraparound cuando el día
        // de inicio > cierre: "de viernes a lunes" = vie,sáb,dom,lun = 5,6,7,1).
        //
        // NO casa las formas CON artículo ("del martes al jueves", "desde el lunes al
        // viernes"): "del"/"al" no casan con "de "/"a " (límites de palabra + espacio),
        // así que esos rangos siguen siendo evento único anclado al cierre (curso,
        // conferencia de varios días). c.487.
        val weekdayRangePattern =
            Regex("""(?i)\b(?:(?:los\s+|de\s+)?(?<w1>lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+(?:a|hasta)\s+(?<w2>lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)|entre\s+(?<w3>lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+(?:a|y|hasta)\s+(?<w4>lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))\b""")
        val weekdayRangeMatch = weekdayRangePattern.find(working)
        if (weekdayRangeMatch != null) {
            val start = (weekdayRangeMatch.groups["w1"] ?: weekdayRangeMatch.groups["w3"])!!.value
            val end = (weekdayRangeMatch.groups["w2"] ?: weekdayRangeMatch.groups["w4"])!!.value
            val s = start.toDayOfWeekOrNull()!!.value
            val e = end.toDayOfWeekOrNull()!!.value
            val days = if (s <= e) (s..e).toList()
                else (s..7).toList() + (1..e).toList()
            phrases += weekdayRangeMatch.range
            val interval = detectWeekInterval()
            if (interval != null) phrases += interval.second
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, interval?.first ?: 1, days, phrases)
        }

        // "cada dos lunes" / "todos los dos martes" / "cada tres jueves": cadencia
        // semanal espaciada por CONTEO de weekday ("cada N-ésimo lunes") sin la
        // palabra "semanas". Es la forma hablada de "cada N semanas los lunes": el
        // número cuenta ocurrencias del día, no semanas, pero para un único weekday
        // el efecto es idéntico (cada 2 lunes = cada 2 semanas los lunes). Antes el
        // número NO se reconocía aquí: el `dayListPattern` abajo exigía un weekday
        // justo tras el prefijo, así "dos" lo descartaba y el día casaba DESNUDO
        // (sin prefijo). Eso producía dos fallos reales según el día:
        //  • día INVARIABLE (lunes/martes/...): caía al "cada N" a-secas (c.306) y
        //    nacía como MONTHLY día N (frecuencia equivocada: "gym cada dos lunes"
        //    → mensual día 2, recordatorio y What Now engañados).
        //  • día PLURAL (sábados/domingos): `barePluralSingle` sí reclamaba WEEKLY
        //    interval=1 (cada semana, el doble de frecuente) y "cada dos" quedaba
        //    pegado al título ("futbol cada dos").
        // c.343: se reconoce ANTES que dayListPattern. Captura prefijo + número +
        // lista de días (reusa la misma continuación de día-lista para "cada dos
        // lunes y jueves") y emite WEEKLY interval=N (coercido 1..366, igual que
        // detectWeekInterval). El motor `nextWeekly` ya despacha interval>1 con
        // días correctamente (verificado: desde el lunes de la semana N, interval=2
        // → siguiente lunes en la semana N+2). La 1ª ocurrencia la resuelve la
        // rama WEEKLY+days de la cascada de dueAt (nextWeekday), igual que "cada N
        // semanas los lunes". No casa "cada 2 semanas los lunes" (tras el número
        // viene "semanas", no un weekday → cae a dayListPattern + detectWeekInterval,
        // intacto). N puede ser dígito o número escrito ("cada dos lunes").
        // Nombre de día reusable (lo usa el conteo "cada N lunes" de arriba y la
        // lista de días de abajo). Definido aquí para estar disponible en ambos.
        val dayNameRegex = Regex("""(?i)lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo""")
        val weekdayCountPattern =
            Regex("""(?i)\b(?:cada|todos\s+los|todas\s+las)\s+(\d{1,3}|$writtenNumberGroup)\s+((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?)(?:\s*(?:,|y|-)?\s*(?:(?:el|los)\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?))*)\b""")
        weekdayCountPattern.find(working)?.let { match ->
            val rawN = match.groupValues[1]
            val n = rawN.toLongOrNull()?.toInt() ?: parseWrittenNumber(rawN)?.toInt()
            if (n != null) {
                val days = dayNameRegex.findAll(match.groupValues[2])
                    .mapNotNull { it.value.toDayOfWeekOrNull()?.value }
                    .distinct().sorted().toList()
                if (days.isNotEmpty()) {
                    phrases += match.range
                    return RecurrenceResult(RecurrenceFrequency.WEEKLY, n.coerceIn(1, 366), days, phrases)
                }
            }
        }

        // "todos los viernes" / "cada lunes y jueves" / "los lunes y jueves".
// Un único patrón captura una lista de días separada por ",", "y" o solo
        // espacio ("lunes miércoles viernes" / "lunes miércoles y viernes"), forma
        // habitual en español. El separador es opcional: en español informal
        // "los lunes miércoles y viernes" (sin coma entre los dos primeros) es
        // tan común como la forma con coma; con conector obligatorio el parser
        // capturaba solo el primer día y perdía el resto → rutina silenciosamente
        // mutilada. Como los nombres de día son palabras cerradas y específicas,
        // admitir separador vacío solo casa cuando la palabra siguiente es otro
        // día, sin riesgo de robar texto ajeno ("los lunes con el equipo" para en
        // "lunes" porque "con" no es un día). Plural `s?` para sábado/domingo.
        // Prefijo ("todos los"/"cada"/"los") opcional: la forma BARE de lista de 2+
        // días ("gym sábados y domingos", "reunión lunes miércoles y viernes") es tan
        // común como la prefijada y antes caía sin recurrencia, dejando los días como
        // residuo en el título (la rutina se olvidaba). Se exige 2+ días para el caso
        // bare: un día suelto ("reunión martes") es ambiguo (¿fecha?), así que se deja
        // para el patrón de fecha para no programar una recurrencia equivocada.
        //
        // Artículo inicial opcional "el/los" (grupo propio, NO hasPrefix): permite
        // consumir "el lunes y el miércoles" / "los lunes y los miércoles" sin que el
        // "el" inicial quede como residuo, y SIN convertir un día suelto con artículo
        // ("reunión el martes", sin cadencia) en recurrencia: ese caso cae igual al
        // patrón de fecha porque hasPrefix es falso y solo hay 1 día.
        //
        // Artículo opcional "el/los" en CADA paso de la continuación: en español es tan
        // natural repetir el artículo ante cada día ("los lunes y los miércoles",
        // "el martes y el jueves") como omitirlo. Antes la continuación no toleraba un
        // artículo antes del día siguiente, así "los lunes y los miércoles" casaba SÓLO
        // "lunes", perdía el resto (rutina mutilada: un solo día en silencio) y dejaba
        // "y los" como residuo en el título. c.258.
        // Conector inicial opcional "entre " (no capturador): "entre sábado y
        // domingo" / "entre lunes y jueves" son listas de días válidas; sin
        // consumir "entre" aquí, ese conector quedaba como residuo en el título.
        // No capturador para no alterar los índices de grupo (hasPrefix=[1],
        // artículo=[2], días=[3]). El rango Lun-Vie ("entre lunes y viernes") ya
        // se resolvió arriba, así que aquí solo llegan listas reales. c.282.
        val dayListPattern =
            Regex("""(?i)\b(?:entre\s+)?(?:(todos\s+los|cada|los)\s+)?(?:(el|los)\s+)?((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?)(?:\s*(?:,|y|-)?\s*(?:(?:el|los)\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?))*)\b""")
        val weeklyMatch = dayListPattern.find(working)
        if (weeklyMatch != null) {
            val days = dayNameRegex.findAll(weeklyMatch.groupValues[3])
                .mapNotNull { it.value.toDayOfWeekOrNull()?.value }
                .distinct().sorted().toList()
            val hasPrefix = weeklyMatch.groupValues[1].isNotBlank()
            // Bare: 2+ días siempre es recurrencia. Un día suelto solo lo es si es
            // plural marcado (sábados/domingos), forma habitual de hábito semanal
            // ("fútbol domingos"). Los demás días son invariables (lunes/martes…),
            // así que "reunión martes" queda como fecha ambigua, no recurrencia.
            val barePluralSingle = !hasPrefix && days.size == 1 &&
                weeklyMatch.groupValues[3].lowercase().let { g ->
                    g.contains("sábados") || g.contains("sabados") || g.contains("domingos")
                }
            if (days.isNotEmpty() && (hasPrefix || days.size >= 2 || barePluralSingle)) {
                phrases += weeklyMatch.range
                val interval = detectWeekInterval()
                if (interval != null) phrases += interval.second
                return RecurrenceResult(RecurrenceFrequency.WEEKLY, interval?.first ?: 1, days, phrases)
            }
        }

        // "entre semana" / "días laborables/hábiles/de semana" como recurrencia
        // semanal de lunes a viernes (hábito cotidiano). El rango explícito "de lunes
        // a viernes" ya se resolvió arriba. Antes estas frases quedaban sin recurrencia
        // (tarea única: la intención de repetir se perdía). La primera ocurrencia la
        // resuelve la rama WEEKLY+days (próximo día hábil).
        val weekdaySetPattern =
            Regex("""(?i)\b(?:todos\s+los\s+|cada\s+|los\s+)?d[ií]as\s+(?:laborables|h[aá]biles|de\s+semana)\b|\bentre\s+semana\b""")
        weekdaySetPattern.find(working)?.let { match ->
            phrases += match.range
            val interval = detectWeekInterval()
            if (interval != null) phrases += interval.second
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, interval?.first ?: 1, listOf(1, 2, 3, 4, 5), phrases)
        }

        // "fines de semana" / "los findes" / "cada finde" como recurrencia semanal de
        // sabado+domingo. Es la forma natural de "hago esto los findes" (estudiar,
        // limpiar, deporte). Antes quedaba sin recurrencia y sin fecha -> la tarea
        // repetitiva se olvidaba o aparecia una sola vez. La primera ocurrencia la
        // resuelve la rama WEEKLY+days (proximo sabado o domingo). Distinto del
        // singular "fin de semana"/"finde"/"final de semana" (fecha unica, proximo
        // sabado): el plural o el determinante "cada/los" = habito. "este finde"/"el
        // finde" se resuelve arriba como fecha (weekendPattern), NO aqui, porque el
        // singular con "este/el" señala UN fin de semana concreto, no un habito
        // recurrente. "cada final de semana" (variante regional) se admite aquí como
        // hábito semanal, simétrico a "cada fin de semana".
        // c.494: la preposición distributiva "a" (= "los", "cada") sobrevivía al
        // título ("Gimnasio a fines de semana" → "Gimnasio a"). Paralelo al genitivo
        // "de/del" de c.493: el "a" coloquial ante recurrencia de fin de semana
        // ("a fines de semana", "a los findes", "a cada fin de semana") se consume
        // aquí como prefijo opcional para que el título quede limpio. Solo casa la
        // "a" cuando va seguida de fin de semana/findes; no toca "a" aislada.
        val weekendRecurrencePattern =
            Regex("""(?i)\b(?:a\s+)?(?:cada\s+)?(?:los\s+)?fines\s+de\s+semana\b|\b(?:a\s+)?(?:cada\s+)?(?:los\s+)?findes?\b|\b(?:a\s+)?cada\s+(?:fin|final)\s+de\s+semana\b""")
        weekendRecurrencePattern.find(working)?.let { match ->
            phrases += match.range
            val interval = detectWeekInterval()
            if (interval != null) phrases += interval.second
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, interval?.first ?: 1, listOf(6, 7), phrases)
        }

        // Mensual anclado a día del mes: "el 15 de cada mes" / "los 1 del mes" /
        // "15 de cada mes". Antes "el 15 de cada mes" dejaba "el 15 de" como residuo en
        // el título y dueAt=null (la tarea mensual nunca tenía fecha, los recordatorios
        // no disparaban). Ahora se ancla la primera ocurrencia al próximo día N.
        // P1: parseRecurrence se ejecuta ANTES que dayOfMonthPattern, así que este
        // patrón vería "el 31 del mes actual" PRIMERO y robaría "31 del mes" como
        // recurrencia falsa, dejando "actual" como residuo (compromiso único del mes en
        // curso perdido). El lookahead negativo `(?!\s+(?:actual|presente|este|…))`
        // rechaza "del mes" cuando le sigue un calificador de mes CONCRETO (actual,
        // presente, este, entrante, próximo, siguiente, "que viene/entra/sigue"): ésos
        // son fecha única y los resuelve dayOfMonthPattern/nextMonthDayPattern después.
        // Solo "el N del mes" a secas (sin calificador) y "de cada mes" casan aquí como
        // recurrencia mensual (decisión de producto: forma genérica recurrente).
        //
        // "cada N del mes" (sin "de" entre "cada" y el día) es la forma cotidiana del
        // vencimiento mensual ("renta cada 1 del mes", "pago cada 15 del mes"). Antes
        // el prefijo "cada" NO se consumía y quedaba pegado al título ("renta cada"),
        // ensuciando el texto de una rutina financiera real. c.306: el grupo opcional
        // inicial ahora admite "cada" además de "el/los" para que el rango capturado
        // incluya el prefijo y el título quede limpio.
        //
        // c.315: VARIOS días del mes ("el 1 y 15 de cada mes", "cobro los días 15 y
        // 30 de cada mes") — quincena/nómina/cobro LATAM. Antes el parser sólo anclaba
        // el 1er día (monthlyDayPattern casaba "el 1") y dejaba el 2º ("y 15") como
        // fecha suelta descartada, generando residuo " y" pegado al título ("pagar y"):
        // un día de pago real nacía olvidado (P1, pérdida de datos). Ahora se capturan
        // TODOS los días, se codifican en `recurrenceDays = "d:N1,N2,..."` y el 1er
        // vencimiento aterriza en el día más cercano al hoy. El patrón va ANTES del de
        // un solo día para que "el 1 y 15" no se reduzca a "el 1".
        //
        // c.321: generalización a N días + sinónimos léxicos. El patrón c.315 limitaba
        // a 2 días y dejaba 3 clases de residuo/pérdida (probe JVM c.321): (a) "el 1,
        // 15 y 30 de cada mes" → casaba "15 y 30" y dejaba "1, " pegado al título; (b)
        // "el 1 y el 15 de cada mes" (artículo repetido, forma natural) → NO casaba,
        // caía al single-day y dejaba "y el 15" como residuo; (c) "el 1 y 15 todos los
        // meses" (sinónimo de "cada mes") → NO casaba, caía al single-day y dejaba "y
        // 15". RESUELTO: capturar la LISTA entera de días (separada por "," y/o "y" con
        // artículo opcional repetido) en UN grupo y parsearla en código (split por
        // [,\s] + "y"); admitir "todos los meses" como sinónimo de "de cada mes". Si la
        // lista tiene ≥2 días válidos (1..31) distintos → monthlyDays; si tiene 1 → no
        // reclamar (cae al single-day limpio); 0 → tampoco. El motor/backup ya soportan
        // N días (c.315) sin cambios.
        //
        // c.324: marcador de cadencia quincenal COMO alternativa al sufijo "mes". Cuando
        // el usuario especifica DÍAS DEL MES explícitos ("pago quincenal los días 1 y
        // 15", "nómina quincenal el 1 y el 15") la lista de días fija el significado:
        // son los días de pago mensuales, NO un intervalo de 15 días. Antes el adjetivo
        // "quincenal" se tragaba antes (rama `quincenaRecurrencePattern` → DAILY x15) y
        // "los días 1 y 15" quedaba como residuo pegado al título ("pago los días 1 y
        // 15") con recurrencia DAILY x15 (cada 15 días), que deriva 1 día por ciclo y
        // desfasa los días de pago reales (1 y 15) mes a mes (P1: dato de pago
        // silenciosamente erróneo). La señal honesta de desambiguación es la LISTA de
        // días del mes: si el usuario pincha días concretos del mes junto a un marcador
        // de cadencia quincenal/mensual, esos días ANCLAN la recurrencia mensual. El
        // sufijo "de cada mes"/"todos los meses" sigue siendo la forma canónica; el
        // adjetivo quincenal se admite como marcador DELANTERO ("quincenal los días 1 y
        // 15") o TRASERO ("los días 1 y 15 quincenal"). Sin marcador (lista pelada "los
        // días 1 y 15") NO se reclama: una lista sin cadencia es ambigua y se deja al
        // resto de la cascada (comportamiento previo intacto). "quincenal" SOLO (sin
        // lista de días) sigue siendo DAILY x15 (c.276/c.321, inafectado): la
        // generalización sólo aplica cuando hay días del mes explícitos. Los días de
        // SEMANA ("quincenal los lunes") ya los resuelve dayListPattern ANTES (WEEKLY
        // x2) y no llegan aquí.
        val quincenaCadenceMarker =
            """cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas"""
        // Grupo 1 = lista de días; grupo 2 = sufijo "mes"/"todos los meses" (opcional);
        // el prefijo delantero y el sufijo trasero quincenal son NO capturadores (se
        // detectan vía containsMatchIn sobre match.value). El sufijo completo es
        // OPCIONAL (incluido el `\s+`) para que la lista pueda cerrar al final del
        // texto ("quincenal los días 1 y 15") o ir seguida de un marcador ("de cada
        // mes" / "quincenal"). Antes el `\s+` obligatorio tras la lista hacía que
        // retrocediera y capturara sólo el 1er día cuando la lista terminaba en final
        // de string. Se exige ≥2 días Y un marcador de cadencia para reclamar como
        // mensual: la lista pelada sin cadencia es ambigua y se deja a la cascada.
        //
        // c.331: el prefijo plural explícito "los días"/"días" (la palabra "días"
        // en plural, con o sin artículo) es POR SÍ MISMO un marcador de cadencia
        // mensual honesto. "cobro los días 15 y 30" / "pago los días 1 y 15" /
        // "nómina los días 15 y 30" son las frases canónicas LATAM de cobro/pago
        // quincenal; antes caían a NONE+dueAt=null → rutina olvidada (sin
        // recordatorio, invisible en What Now/planificador). La decisión c.324 dejaba
        // la "lista pelada sin cadencia" como ambigua, pero esa decisión no distinguía
        // la lista CON "días" plural explícito (señal fuerte de periodicidad) de la
        // lista SIN "días" ("el 1 y 15", que SÍ es ambigua). Ahora: si el match
        // contiene "días" en plural → hasCadence=true. La lista SIN "días" sigue
        // siendo ambigua (intacto). Con mes nombrado ("de agosto") la lista sigue
        // reclamándose como MONTHLY (días+fecha correctos); el residuo "de agosto" en
        // el título es cosmético y prefible a NONE+fecha perdida.
        val monthlyDualDayPattern =
            Regex("""(?i)\b(?:(?:$quincenaCadenceMarker)\s+)?(?:cada|el|los)?\s*(?:d[ií]as?\s+)?(\d{1,2}(?:\s*,\s*\d{1,2})*(?:\s+y\s+(?:el|la|los|las)?\s*\d{1,2})?)(?:\s+(?:(de\s+(?:cada\s+)?mes|del\s+(?:cada\s+)?mes|todos\s+los\s+meses)|(?:$quincenaCadenceMarker))?(?:es)?)?(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))""")
        monthlyDualDayPattern.find(working)?.let { match ->
            // Extraer todos los enteros del grupo de la lista de días ("1, 15 y 30" → [1,15,30]).
            val days = Regex("""\d{1,2}""").findAll(match.groupValues[1])
                .mapNotNull { it.value.toIntOrNull()?.takeIf { d -> d in 1..31 } }
                .distinct()
                .sorted()
                .toList()
            // hasCadence: hay marcador de cadencia (delantero quincenal, sufijo "mes",
            // sufijo quincenal trasero, o el prefijo plural explícito "los días"/"días"
            // en plural — c.331). Sin marcador la lista es ambigua → no reclamar.
            val hasCadence = match.value.let { v ->
                v.contains(Regex("""(?i)\b(?:$quincenaCadenceMarker)\b""")) ||
                    match.groupValues[2].isNotBlank() ||
                    v.contains(Regex("""(?i)\b(?:los|las)\s+d[ií]as\b""")) ||
                    v.contains(Regex("""(?i)\bd[ií]as\s+\d"""))
            }
            // c.342: el guard original exigía `hasCadence` (marcador "días" plural o
            // "de cada mes"/quincenal) ANTES de escanear el mes nombrado. Así la forma
            // con "el" repetido sin "días" plural ("reunión el 15 y el 30 de septiembre")
            // nunca llegaba a scanTrailingNamedMonth → caía a NONE + título roto
            // ('reunión y'). Pero un mes nombrado trasero es por sí mismo señal de
            // cadencia honesta (quita la ambigüedad de la lista pelada, c.324): es la
            // misma señal que c.341 ya aceptaba para la forma plural. Aquí calculamos
            // `named` primero y relajamos el guard a `hasCadence || named != null`. La
            // lista pelada SIN mes nombrado sigue siendo ambigua → NONE (no-regresión).
            val named = scanTrailingNamedMonth(working, match.range.last + 1)
            if (days.size >= 2 && (hasCadence || named != null)) {
                // c.341: "reunión los días 15 y 30 de septiembre" — el día-lista consume
                // los dígitos, así `monthNamePattern` (que exige dígito+mes) no casa y el
                // mes nombrado se pierde (1ª fecha anclada al mes actual → cita en mes
                // erróneo, P1 de datos). Aquí escaneamos el texto INMEDIATAMENTE posterior
                // al match por un mes nombrado opcional (+ año), y una cadencia opcional
                // trasera ("de cada mes"). Ambos se incluyen en `phrases` para que se
                // borren del título (residuo "de septiembre" eliminado). El mes/año se
                // guardan en el RecurrenceResult para anclar la 1ª ocurrencia.
                if (named != null) {
                    phrases += match.range
                    phrases += named.range
                    if (named.cadenceRange != null) phrases += named.cadenceRange
                    return RecurrenceResult(
                        RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases,
                        monthlyDays = days,
                        monthlyNamedMonth = named.month,
                        monthlyNamedYear = named.year,
                        monthlyNextMonth = named.nextMonth
                    )
                }
                phrases += match.range
                return RecurrenceResult(
                    RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases,
                    monthlyDays = days
                )
            }
        }
        // c.583 — rutina anual anclada a una fecha de calendario concreta con "cada":
        // "renovar suscripción cada 1 de enero", "pago cada 15 de marzo",
        // "renovar suscripción anual cada 1 de enero". La palabra "cada" expresa la
        // recurrencia y el día+mes nombrado fija el anclaje anual (aniversario,
        // renovación, cumpleaños). Antes la rama MONTHLY "cada N" (línea siguiente)
        // reclamaba "cada 1" y devolvía MONTHLY + due=1 de septiembre: ignoraba el mes
        // nombrado (el dígito se consumía y monthNameDate se quedaba sin casar) Y la
        // eventual señal "anual" nunca llegaba a fixedPatterns (MONTHLY retornaba antes).
        // La rutina anual nacía MENSUAL (12× más frecuente) en el mes equivocado (P1:
        // recordatorio erróneo, fecha olvidada). Aquí se reclama como YEARLY dejando el
        // día+mes intactos en el rango de frase (sólo se consume el token "cada") para
        // que [monthNamePattern] resuelva la fecha (con su roll anual y saneo de día),
        // igual que ya ocurre con "el 1 de enero" / "anual el 1 de enero". El día admite
        // dígitos o número escrito ("cada quince de marzo"); el mes debe ser nombrado
        // (NO "cada 1 de cada mes" ni "cada 1 del mes" — ésos siguen siendo MONTHLY:
        // aquí no casan porque exigen un mes del calendario, no la palabra "mes").
        Regex(
            """(?i)\bcada\s+(\d{1,2}|$writtenNumberGroup)\s+(?:de|del)\s+$monthNameGroup""" +
                """(?:\s+del?\s+\d{2,4})?\b"""
        ).find(working)?.let { match ->
            val rawDay = match.groupValues[1]
            val day = rawDay.toIntOrNull() ?: parseWrittenNumber(rawDay)?.toInt()
            if (day != null && day in 1..31) {
                // Se consume SÓLO el token "cada" del rango de frase: el "N de <mes>"
                // debe permanecer en `working` para que monthNamePattern lo resuelva y
                // monthNameStripPattern lo borre del título. "anual"/"anualmente" que
                // acompañen se limpian vía recurrenceAdjectiveLeakPattern.
                phrases += match.range.first..(match.range.first + 3)
                return RecurrenceResult(RecurrenceFrequency.YEARLY, 1, emptyList(), phrases)
            }
        }

        // Prefijo opcional no capturador que consume el genitivo "del"/"de"
        // introductor de la fecha mensual ("renta del 15 de cada mes") para que no
        // quede como residuo del título. Simétrico de c.448; el "del" de contenido se
        // respeta porque el prefijo es opcional y único ("cuenta del banco del 15...").
        // c.467: la CADENCIA se amplía más allá de "de/del mes"/"de cada mes" para
        // cubrir las formas cotidianas de los compromisos periódicos más frecuentes
        // (renta/pago/factura/cuota): "de todos los meses", "todos los meses",
        // "mensual", "mensualmente". Antes estas NO casaban → el día N caía sin
        // anclaje y el vencimiento se programaba HOY (incorrecto) con recurrencia
        // MONTHLY sin saber qué día repetir. El lookahead negativo (sólo en la rama
        // "mes") descarta "del mes actual/entrante/próximo/que viene" (fecha única, no
        // recurrente); las formas "todos los meses"/"mensual" no tienen esa ambigüedad.
        // c.674: el sustantivo del conector admite también el PLURAL "días". La
        // forma financiera cotidiana "pagar la luz los días 15 de cada mes"
        // dejaba el plural como residuo del título (sólo se consumía "los"); el
        // singular "el día 15 de cada mes" ya se cubría y la cadencia seguía
        // anclada igual ("de cada mes"/"todos los meses"/"mensual").
        val monthlyDayPattern =
            Regex("""(?i)\b(?:\bdel?\s+)?(?:cada|el|los)?\s*(?:d[ií]as?\s+)?(\d{1,2})\s+(?:(?:de|del)\s+(?:cada\s+)?mes(?:es)?(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))|(?:de\s+)?todos\s+los\s+meses|mensual(?:mente|idades)?)""")
        monthlyDayPattern.find(working)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: return@let
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases, day)
        }

        // "cada N" a secas (SIN unidad: días/semanas/meses/años) es el vencimiento
        // mensual cotidiano ("reporte cada 15", "nomina cada 1", "cobro cada 30"):
        // en español financiero/empresarial el día del mes implícito es la quincena,
        // la nómina, el corte o el pago recurrente. La AUSENCIA de unidad es la señal
        // — "cada 15 días"/"cada 2 semanas" llevan unidad y los resuelve intervalPattern;
        // sin unidad, el número NO es un intervalo sino un día del mes. Antes caía a
        // NONE sin fecha: la rutina mensual nacía olvidada (recordatorio jamás
        // disparaba, jamás en What Now). c.306: se reconoce como MONTHLY anclado al día
        // N (1-28 por seguridad: 29-31 omiten meses cortos y son ambiguos con fecha
        // suelta "el 30"; el rango seguro cubre nómina/quincena/cobro/pago). Admite
        // dígitos o número escrito ("cada quince", "cada uno"). El lookahead negativo
        // rechaza TODO lo que tenga unidad o fracción horaria a continuación:
        //  • `días|semanas|meses|años|minutos` → intervalPattern (cadencia espaciada).
        //  • `horas?|hs?` → hourlyIntervalPattern (medicación "cada 8 h"/"cada 12 hs").
        //  • `y (media|cuarto) horas?` → cadencia fraccionaria no representable
        //    ("cada 3 y media horas") que resuelve hourlyFractionPattern a NONE+now.
        // Sin este rechazo, "cada 12 hs" caería aquí como MONTHLY día 12 (falso) y
        // dejaría " hs" como residuo del título, rompiendo la medicación.
        Regex("""(?i)\bcada\s+(\d{1,2}|$writtenNumberGroup)(?!\s*(?:d[ií]as?|semanas?|meses?|a[nñ]os?|horas?|hs?|minutos|y\s+(?:media|cuarto)\s*(?:horas?)?))\b""").find(working)?.let { match ->
            val rawN = match.groupValues[1]
            val n = rawN.toLongOrNull()?.toInt()
                ?: parseWrittenNumber(rawN)?.toInt()
            if (n != null && n in 1..28) {
                phrases += match.range
                return RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases, n)
            }
        }

        // "cada N días/semanas/meses/años" y "todos los/todas las N ..." — N puede ser
        // dígito O número escrito ("cada dos semanas", "cada tres meses", "cada quince
        // días"; "todas las dos semanas", "todos los tres meses", "todos los 3 días").
        // Antes el grupo sólo admitía `\d{1,3}`, así que las formas con palabra caían a
        // NONE y la tarea recurrente nacía sin fecha (recordatorio jamás disparaba). Se
        // reutiliza `parseWrittenNumber` para resolver la palabra; la alternación está
        // acotada a los números conocidos para no colisionar con la unidad
        // (días/semanas/...). El determinante plural "todos los/todas las" es la forma
        // hablada de "cada N" para cadencias espaciadas (N≥2); sin él caían a NONE SIN
        // fecha Y dejaban "todas las dos semanas" como residuo literal del título
        // (rutina olvidada + captura sucia). No casa la forma SIN número ("todas las
        // semanas"→N=1 la resuelve fixedPatterns), así que no hay colisión con ésta.
        val intervalPattern =
            Regex("""(?i)\b(?:cada|todos\s+los|todas\s+las)\s+(\d{1,3}|$writtenNumberGroup)\s*(d[ií]as?|semanas?|meses?|a[nñ]os?)\b""")
        intervalPattern.find(working)?.let { match ->
            val rawN = match.groupValues[1]
            val interval = rawN.toLongOrNull()?.toInt()?.coerceIn(1, 366)
                ?: parseWrittenNumber(rawN)?.toInt()?.coerceIn(1, 366)
                ?: return@let
            val unit = match.groupValues[2].lowercase()
            val frequency = when {
                unit.startsWith("d") -> RecurrenceFrequency.DAILY
                unit.startsWith("s") -> RecurrenceFrequency.WEEKLY
                unit.startsWith("mes") -> RecurrenceFrequency.MONTHLY
                unit.contains("añ") || unit.startsWith("an") -> RecurrenceFrequency.YEARLY
                else -> return@let
            }
            phrases += match.range
            return RecurrenceResult(frequency, interval, emptyList(), phrases)
        }

        // "cada N horas": cadencia sub-diaria (medicación: "cada 8 horas", "cada 12
        // horas", "cada 6 horas"; también "cada 24/48 horas" = diario/cada 2 días).
        // Antes `intervalPattern` no admite "horas" y no existe frecuencia HOURLY, así
        // que estas frases caían a NONE sin fecha → la duración "N horas" robaba el
        // número (480 min falsos para "8 horas") y la medicación quedaba SIN
        // vencimiento: el recordatorio jamás disparaba y nunca aparecía en What Now
        // (rutina olvidada, P1). Aquí se maneja de forma honesta:
        //  • N múltiplo de 24 (≥24) → DAILY interval=N/24 (fiel: 24h=diario, 48h=cada
        //    2 días), reutilizando el flujo de intervalo existente.
        //  • N sub-diario (6/8/12/18...) → NONE + immediateDueAt=now: el motor no
        //    puede repetir por hora, así que se saca la PRIMERA dosis a la superficie
        //    venciendo ahora (aviso real, What Now) en vez de fingir una recurrencia
        //    horaria inexistente o dejarla olvidada. El rango se añade a `phrases`
        //    para limpiar el título y evitar que la duración robe "N horas".
        // La cantidad admite dígitos o número escrito (vía [parseWrittenNumber]),
        // simétrico con [intervalPattern].
        //
        // Variantes sin número ("cada hora"/"cada media hora"): misma clase de olvido
        // — el patrón de abajo exige una cantidad, así que éstas caían a NONE SIN
        // fecha y, en "cada media hora", la duración robaba "media hora" como 30 min
        // falsos y truncaba el título ("Tomar cada"). Son cadencias sub-diarias muy
        // comunes en medicación (jarabes, gotas, gárgaras). Se tratan ANTES y de la
        // misma forma honesta: NONE + immediateDueAt=now + título limpio. "cada media
        // hora" se evalúa primero para que "media" no quede colgando.
        Regex("""(?i)\bcada\s+media\s+horas?\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), phrases, immediateDueAt = now)
        }
        // "cada hora"/"cada horas" (cada 1 hora): cadencia sub-diaria REAL. Ahora el
        // motor SÍ repite por hora (HOURLY), así que se reconoce como recurrencia
        // horaria interval=1 en vez de dosis única olvidada: al completar la 1ª dosis
        // el motor genera la siguiente +1h. La primera dosis sale a la superficie
        // venciendo ahora (immediateDueAt) salvo que haya hora explícita.
        Regex("""(?i)\bcada\s+horas?\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.HOURLY, 1, emptyList(), phrases, immediateDueAt = now)
        }
        // "cada N horas y media/cuarto" y "cada N y media horas" (medicación con precisión
        // sub-hora: "cada 3 horas y media", "cada 2 horas y cuarto", "cada 6 y media horas"):
        // cadencia que el motor NO puede representar (HOURLY usa intervalo entero de horas;
        // 3,5 h no es representable en el modelo). ANTES hourlyIntervalPattern casaba "cada 3
        // horas" y dejaba "y media" como residuo del título, PERO se asignaba HOURLY
        // interval=3 → la medicación RECURRE cada 3 h en vez de 3,5 h (cadencia falsa: la
        // 2ª dosis sale 30 min antes, acumulando error de timing) Y el título nacía
        // mutilado ("medicación y media"). En "cada N y media horas" además caía a NONE SIN
        // fecha con la frase entera como residuo (dosis olvidada + título sucio). Se trata
        // ANTES de hourlyIntervalPattern y de la MISMA forma honesta que "cada media hora"
        // / "cada N minutos" (cadencias no representables): NONE + immediateDueAt=now +
        // título limpio. Así la 1ª dosis sale a la superficie (aviso real, What Now) sin
        // fingir una recurrencia de 3 h que el usuario no pidió. La fracción acepta "media"
        // (30 min) y "cuarto" (15 min) —las fracciones de reloj canónicas en español—, y el
        // número admite dígitos o número escrito (simétrico de hourlyIntervalPattern). Las
        // dos formas ("y media" tras "horas" o antes de "horas") se cubren juntas.
        val hourlyFractionPattern =
            Regex("""(?i)\bcada\s+(\d{1,3}|$writtenNumberGroup)\s+(?:horas?\s+)?(?:y\s+(?:media|cuarto))\s*(?:horas?)?\b""")
        hourlyFractionPattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), phrases, immediateDueAt = now)
        }
        val hourlyIntervalPattern =
            Regex("""(?i)\bcada\s+(\d{1,3}|$writtenNumberGroup)\s*(?:horas?|hs?)\b""")
        hourlyIntervalPattern.find(working)?.let { match ->
            val rawN = match.groupValues[1]
            val hours = rawN.toLongOrNull()?.toInt()
                ?: parseWrittenNumber(rawN)?.toInt()
                ?: return@let
            phrases += match.range
            if (hours >= 24 && hours % 24 == 0) {
                return RecurrenceResult(RecurrenceFrequency.DAILY, hours / 24, emptyList(), phrases)
            }
            // "cada N horas" con N<24 (p. ej. 8, 12): cadencia sub-diaria REAL para
            // medicación. Antes era NONE + dosis única → la 2ª/3ª dosis se olvidaban
            // (P1, evitar olvidos). Ahora HOURLY interval=N: al completar la 1ª dosis
            // el motor genera la siguiente +N horas. La primera dosis sale a la
            // superficie venciendo ahora (immediateDueAt) salvo que haya hora
            // explícita ("cada 8 horas a las 3pm" → 1ª dosis 15:00, luego 23:00, …).
            if (hours >= 1) {
                return RecurrenceResult(RecurrenceFrequency.HOURLY, hours, emptyList(), phrases, immediateDueAt = now)
            }
            return RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), phrases, immediateDueAt = now)
        }

        // "cada N minutos" y "cada cuarto de hora" (=cada 15 min): cadencia sub-horaria
        // común en medicación (gárgaras, gotas, enjuagues). El motor no repite por minuto,
        // así que —igual que "cada N horas"— se saca la primera dosis a la superficie
        // venciendo AHORA (aviso real, What Now) sin fingir recurrencia inexistente. Antes
        // la duración "N minutos" robaba el número (p. ej. 30 min falsos) y "cada" quedaba
        // como residuo en el título; la tarea nacía SIN vencimiento → recordatorio jamás
        // disparaba, dosis olvidada (P1). La frase se añade a `phrases` para limpiar el
        // título y evitar que la duración robe "N minutos"/"cuarto de hora". Se evalúa
        // tras hourlyIntervalPattern (éstas no casan ahí: la unidad es "minutos"/"cuarto")
        // y antes de everyOtherDay. "cada cuarto de hora" se evalúa primero: "cuarto de
        // hora" sin "cada" es duración, así que el prefijo "cada " lo acota a cadencia.
        Regex("""(?i)\bcada\s+(?:un\s+)?cuarto\s+de\s+horas?\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), phrases, immediateDueAt = now)
        }
        val minuteIntervalPattern =
            Regex("""(?i)\bcada\s+(\d{1,3}|$writtenNumberGroup)\s*(minutos?|mins?)\b""")
        minuteIntervalPattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), phrases, immediateDueAt = now)
        }

        // "cada otro día" / "un día sí y otro no" = cada dos días (DAILY interval=2).
        // Son los equivalentes idiomáticos de "cada dos días" (que sí casa arriba en
        // intervalPattern): "cada otro día" (calque de "every other day", muy usado en
        // LATAM para medicación) y "un día sí y otro no" (forma nativa) significan
        // exactamente lo mismo. Antes caían a NONE → la tarea recurrente nacía sin fecha
        // ni cadencia (rutina/medicación silenciosamente olvidada: recordatorio jamás
        // disparaba, nunca aparecía en What Now). Se mapea a DAILY+2, idéntico a
        // "cada dos días", reutilizando todo el flujo de intervalo existente. Se evalúa
        // tras intervalPattern (éstas no casan ahí: "otro"/"sí…otro no" no son números)
        // y antes de fixedPatterns ("cada día"→DAILY+1 no colisiona: exige "día" justo
        // tras "cada ", y aquí media "otro"). Se admite "sí"/"si" (acento opcional) y
        // plural "días"/"otros" por si el usuario lo escribe así.
        val everyOtherDayPattern =
            Regex("""(?i)\bcada\s+otros?\s+d[ií]as?\b|\bun\s+d[ií]a\s+s[ií]\s+y\s+otro\s+no\b""")
        everyOtherDayPattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.DAILY, 2, emptyList(), phrases)
        }

        // "<período> sí [y] [<un/una>] <período|otro/a> no" (c.804): la forma nativa
        // de "cada dos períodos" — "día sí día no" (DAILY/2), "semana sí semana no"
        // (WEEKLY/2), "mes sí mes no" (MONTHLY/2). Antes caía a NONE: la rutina
        // nacía sin cadencia ni fecha (medicación/limpieza/pago olvidados; P1), y
        // con hora explícita la cadencia se perdía y la frase quedaba como residuo
        // en el título ("gym día sí día no a las 7" → one-off 07:00, título sucio).
        // El segundo período debe coincidir con el primero (o ser "otro/a"): así
        // "día sí semana no" no casa (frase sin sentido como cadencia). El cierre
        // literal "no" es OBLIGATORIO: "la semana sí fue dura" (afirmación+verbo) y
        // "comprar un día sí y otro también" (sin "no") NO casan. Se evalúa tras
        // everyOtherDayPattern (superconjunto disjunto: exige segundo período/"otro"
        // + "no") y antes de fixedPatterns ("cada día" exige "día" tras "cada ").
        val siNoAlternatingPattern =
            Regex("""(?i)\b(?:(?:un|una)\s+)?(d[ií]a|semana|mes)\s+s[ií]\s+(?:y\s+)?(?:(?:un|una)\s+)?(d[ií]a|semana|mes|otr[oa])\s+no\b""")
        siNoAlternatingPattern.find(working)?.let { match ->
            val period = match.groupValues[1].lowercase()
            val period2 = match.groupValues[2].lowercase()
            val freq = when (period) {
                "día", "dia" -> RecurrenceFrequency.DAILY
                "semana" -> RecurrenceFrequency.WEEKLY
                "mes" -> RecurrenceFrequency.MONTHLY
                else -> null
            }
            val consistent = period2 == period || period2 == "otro" || period2 == "otra"
            if (freq != null && consistent) {
                phrases += match.range
                return RecurrenceResult(freq, 2, emptyList(), phrases)
            }
        }

        // Giros idiomáticos de "cada 2 días" sin cantidad numérica: "días alternos",
        // "días alternativos", "día por medio", "un día por medio". Son las formas
        // nativas/cotidianas de espaciar una rutina cada dos días (gimnasio, medicación,
        // riego de plantas) — semánticamente idénticos a "cada dos días" (que sí casa en
        // [intervalPattern]) y a "cada otro día"/"un día sí y otro no" ([everyOtherDayPattern]).
        // Antes caían a NONE → la rutina nacía sin cadencia ni fecha (recordatorio jamás
        // disparaba, invisible en What Now: P1 evitar olvidos/rutinas adaptables). Se
        // mapean a DAILY+2, reutilizando todo el flujo de intervalo existente.
        //   • Se exige PLURAL "días" para "alternos/alternativos": el singular "día alterno"
        //     es ambiguo (un día alternativo concreto, no hábito) y se evita.
        //   • "día por medio"/"un día por medio" es frase fija singular (idiomática).
        //   • El "cada" inicial es opcional ("cada días alternos" es raro pero válido);
        //     sin "cada" el plural sigue señalando hábito.
        // Se evalúa tras everyOtherDayPattern (disjunto) y antes de fixedPatterns/quincena.
        val alternateDaysPattern =
            Regex("""(?i)\b(?:cada\s+)?d[ií]as\s+(?:alternos|alternativos)\b|\b(?:un\s+)?d[ií]a\s+por\s+medio\b""")
        alternateDaysPattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.DAILY, 2, emptyList(), phrases)
        }

        // NOTA: "semana por medio"/"mes por medio" (formas aisladas, con "cada"/"una"/"un",
        // semana+mes, singular+plural) se resuelven en alternatePeriodPattern más abajo
        // (c.348 c33d25b, superconjunto). El COMBO con lista de días se resuelve arriba
        // en detectWeekInterval() (único caso disjunto que requiere conservar los días).

        // "cada tercer/cuarto/quinto/sexto día": cadencia espaciada con ordinal, equivalente
        // exacto de "cada 3/4/5/6 días" ([intervalPattern] sólo admite cardinales: dígitos o
        // números escritos como "tres", NO ordinales "tercer"). "cada tercer día" = cada 3
        // días, "cada cuarto día" = cada 4, etc. Común en medicación y rutinas (ciclos de
        // tratamiento, riego, descanso activo). Antes caían a NONE → rutina olvidada (P1).
        // El prefijo "cada" lo acota a cadencia: "el tercer día del curso" (sin "cada") NO
        // casa → un ordinal que señala una posición, no una cadencia. Se mapea a DAILY+N
        // (N = ordinal+1: tercer→3, cuarto→4, quinto→5, sexto→6), idéntico a "cada N días".
        val ordinalDayIntervalMap = mapOf(
            "tercer" to 3, "tercero" to 3,
            "cuarto" to 4,
            "quinto" to 5,
            "sexto" to 6
        )
        val ordinalDayIntervalPattern =
            Regex("""(?i)\bcada\s+(tercer(?:o)?|cuarto|quinto|sexto)\s+d[ií]a\b""")
        ordinalDayIntervalPattern.find(working)?.let { match ->
            val n = ordinalDayIntervalMap[match.groupValues[1].lowercase()]
            if (n != null) {
                phrases += match.range
                return RecurrenceResult(RecurrenceFrequency.DAILY, n, emptyList(), phrases)
            }
        }

        // Giros idiomáticos de "cada 2 semanas"/"cada 2 meses" sin cantidad numérica:
        // "semana por medio", "mes por medio" (y con "cada"/"una"/"un"). Familia
        // simétrica de "día por medio" (c.332 → DAILY+2): el giro "X por medio"
        // significa intercalar cada dos períodos. "semana por medio" = cada 2 semanas,
        // "mes por medio" = cada 2 meses. Antes caían a NONE (sin "cada") o, peor,
        // "cada semana por medio"/"cada mes por medio" casaban en [fixedPatterns] como
        // WEEKLY/MONTHLY interval=1 (cadencia WRONG: disparaba cada semana/mes en vez
        // de cada dos) y dejaban "por medio" como residuo en el título — error real de
        // planificación para pagos/medicación/rutinas quincenales o bimensuales (P1
        // datos/recurrencia). Se mapean a WEEKLY+2 / MONTHLY+2 (plusWeeks(2)/plusMonths(2)),
        // idéntico a "cada dos semanas"/"cada dos meses", reutilizando el flujo de
        // intervalo. La regex admite singular y plural ("semana"/"semanas",
        // "mes"/"meses") porque ambas son frase idiomática fija; el cuantificador
        // opcional "cada"/"una"/"un" cubre "cada semana por medio" y "una semana por
        // medio". Se evalúa tras ordinalDayIntervalPattern (disjunto) y ANTES de
        // multiMonthNoun/multiMonthAdjective/fixedPatterns para que "por medio" gane
        // sobre "cada semana" (fixedPatterns interval=1) y limpie el título completo.
        val alternatePeriodPattern =
            Regex("""(?i)\b(?:cada\s+|una\s+|un\s+)?(?:semanas?|mes(?:es)?)\s+por\s+medio\b""")
        alternatePeriodPattern.find(working)?.let { match ->
            val isMonthly = match.value.contains(Regex("""(?i)mes"""))
            phrases += match.range
            return RecurrenceResult(
                if (isMonthly) RecurrenceFrequency.MONTHLY else RecurrenceFrequency.WEEKLY,
                2,
                emptyList(),
                phrases
            )
        }

        // "cada quincena" / "quincenalmente" / "quincenal" (adjetivo) / "todas las
        // quincenas": cadencia quincenal cotidiana en español (nóminas, pagos,
        // reportes cada 15 días). Una quincena son 15 días (media mes), NO 14 (dos
        // semanas): antes se mapeaba a WEEKLY interval=2 (cada 2 semanas ≈ quincena),
        // lo que programaba los pagos un día antes en cada ciclo y derivaba 1 día por
        // quincena — error real de planificación para nóminas/rentas/pagos. Ahora se
        // mapea a DAILY interval=15 (avance plusDays(15), cadencia quincenal exacta),
        // sin añadir enum ni migración. La forma con lista de días ("cada quincena
        // los lunes") sigue siendo semanal (detectWeekInterval): ahí "quincena" actúa
        // como "cada dos semanas" sobre días concretos, no como período de 15 días.
        Regex("""(?i)\b(?:cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.DAILY, 15, emptyList(), phrases)
        }

        // Sustantivos plurimensuales como CADENCIA recurrente: "cada bimestre",
        // "cada trimestre", "cada cuatrimestre", "cada semestre", y la forma plural
        // "todos los bimestres/trimestres/cuatrimestres/semestres" (par léxico simétrico
        // de "todos los meses"/"todos los años", ya cubiertos en fixedPatterns). Hitos
        // financieros de plazo largo (renta, impuestos, declaraciones, renovaciones).
        // `intervalPattern` solo admite "días|semanas|meses|años" y fixedPatterns solo
        // día/semana/mes/año, así estas frases caían a NONE → la tarea recurrente nacía
        // sin fecha ni cadencia (P1: compromiso periódico olvidado, invisible en What
        // Now/planificador, recordatorio jamás disparaba) y la frase quedaba como residuo
        // literal en el título. Se mapean a MONTHLY + intervalo (2/3/4/6), igual que el
        // adjetivo equivalente, sin añadir enum ni migración: RecurrenceEngine ya avanza
        // `plusMonths(interval)`. El prefijo es "cada" o "todos los": "próximo bimestre"/
        // "el bimestre que viene"/"en un bimestre" son FECHAS únicas (resueltas en la
        // cascada de períodos) y no capturan aquí. La rama "todos los" exige PLURAL
        // ("bimestres"), paralelo gramatical a "todos los meses". Se procesa ANTES que
        // multiMonthAdjective y fixedPatterns para limpiar el título.
        val multiMonthNounPattern =
            Regex("""(?i)\bcada\s+(bimestres?|trimestres?|cuatrimestres?|semestres?)\b|\btodos\s+los\s+(bimestres|trimestres|cuatrimestres|semestres)\b""")
        multiMonthNounPattern.find(working)?.let { match ->
            val months = when {
                match.value.contains(Regex("""(?i)cuatrimestre""")) -> 4
                match.value.contains(Regex("""(?i)semestre""")) -> 6
                match.value.contains(Regex("""(?i)trimestre""")) -> 3
                else -> 2 // bimestre
            }
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.MONTHLY, months, emptyList(), phrases)
        }

        // Adjetivos plurimensuales cotidianos en español: "pago bimestral",
        // "impuesto trimestral", "cierre semestral", "informe cuatrimestral". Son hitos
        // financieros de plazo largo tan comunes como el propio "mensual". Antes estas
        // formas adjetivas caían a NONE (la única vía era el numeral "cada 2/3/4/6 meses"):
        // la tarea recurrente nacía sin cadencia → vencimiento invisible, recordatorio
        // jamás disparaba (P1: compromiso periódico olvidado). Se reutilizan MONTHLY +
        // intervalo (2=bimestral, 3=trimestral, 4=cuatrimestral, 6=semestral):
        // RecurrenceEngine ya avanza `plusMonths(interval)`, sin añadir enum ni migración.
        // Se procesa ANTES que fixedPatterns porque aquél solo admite interval=1.
        val multiMonthAdjective = listOf(
            Regex("""(?i)\bbimestral(?:mente)?\b""") to 2,
            Regex("""(?i)\btrimestral(?:mente)?\b""") to 3,
            Regex("""(?i)\bcuatrimestral(?:mente)?\b""") to 4,
            Regex("""(?i)\bsemestral(?:mente)?\b""") to 6
        )
        multiMonthAdjective.forEach { (pattern, months) ->
            pattern.find(working)?.let { match ->
                phrases += match.range
                return RecurrenceResult(RecurrenceFrequency.MONTHLY, months, emptyList(), phrases)
            }
        }

        // "bisemanal"/"bisemanalmente" (cada dos semanas, quincenal en cadencia semanal):
        // el análogo WEEKLY de "bimestral" (MONTHLY/2). Adjetivo/adverbio cotidiano para
        // rutinas quincenales ancladas a la SEMANA ("reunión bisemanal", "pago bisemanal",
        // "terapia bisemanal"). Antes caía a NONE sin fecha → la rutina nacía sin cadencia
        // ni vencimiento (P1: rutina olvidada, invisible en What Now/planificador, el
        // recordatorio jamás disparaba) y "bisemanal" quedaba como residuo literal en el
        // título. Se mapea a WEEKLY interval=2 (plusWeeks(2) = 14 días), idéntico a "cada
        // dos semanas", sin añadir enum ni migración. Distinto de "quincenal" (DAILY/15):
        // una quincena son 15 días, no 14. La forma con días ("bisemanal los lunes") ya
        // se resolvió arriba vía detectWeekInterval; aquí sólo llega la forma aislada.
        Regex("""(?i)\bbisemanal(?:mente)?\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 2, emptyList(), phrases)
        }

        // Cadencia de frecuencia cotidiana: "N veces por semana" / "N veces a la
        // semana" / "N veces al día" / "N veces por día" / "N veces al mes" / "N
        // veces por mes" / "N veces al año" / "N veces por año" ("ir al gym tres
        // veces por semana", "tomar medicamento 3 veces al día", "nómina dos veces
        // al mes", "revisión médica dos veces al año"). Antes caían a NONE +
        // dueAt=null: la rutina nacía sin cadencia ni fecha → el recordatorio
        // jamás disparaba, invisible en What Now (P1 evitar olvidos/rutinas
        // adaptables) y la frase entera quedaba como residuo en el título. El
        // modelo de cadencia sólo admite intervalos enteros por frecuencia (no
        // existe "N veces por período" como tal), así que se mapea al intervalo
        // exacto o al más próximo, con truncación hacia MÁS frecuente (nunca menos)
        // para no estirar una rutina de medicación/pago: por semana → cada ⌊7/N⌋
        // días (2→3, 3→2, ≥4→1); al día → cada ⌊24/N⌋ horas (2→12, 3→8 — exactos);
        // al mes → cada ⌊30/N⌋ días (2→15 — exacto, igual que quincena); al año →
        // cada ⌊12/N⌋ meses (2→6 — semestral, 3→4 — cuatrimestral, 4→3 — trimestral,
        // 6→2 — bimestral, 12→1 — mensual). Con N=1 se usa la frecuencia natural
        // del período ("una vez por semana"→WEEKLY, "una vez al día"→DAILY, "una vez
        // al mes"→MONTHLY, "una vez al año"→YEARLY). N admite dígito o número
        // escrito ("dos"/"tres") y la singular "vez" cuando N=1. Se evalúa tras las
        // ramas de lista de días e intervalo (no casan: exigen "cada" o unidad sin
        // "veces") y ANTES de fixedPatterns (cuyo "cada semana"/"cada día" no casa
        // por la palabra "veces" interpuesta, pero el orden mantiene la norma
        // específico-antes-que-general). No casa "dos veces" sin período: el título
        // se conserva. Tampoco casa la interrogativa "cuántas veces al año"
        // ("cuántas" no es número escrito → el grupo 1 no captura).
        val timesPerPeriodPattern = Regex(
            """(?i)\b(\d{1,3}|$writtenNumberGroup)\s+(?:veces|vez)\s+(por\s+semana|a\s+la\s+semana|al\s+d[ií]a|por\s+d[ií]a|al\s+mes|por\s+mes|al\s+a[nñ]o|por\s+a[nñ]o)\b"""
        )
        timesPerPeriodPattern.find(working)?.let { match ->
            val rawN = match.groupValues[1]
            val n = rawN.toIntOrNull() ?: parseWrittenNumber(rawN)?.toInt()
            if (n != null && n >= 1) {
                phrases += match.range
                val period = match.groupValues[2].lowercase()
                return when {
                    period.endsWith("semana") -> if (n == 1) {
                        RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, emptyList(), phrases)
                    } else {
                        RecurrenceResult(
                            RecurrenceFrequency.DAILY,
                            (7 / n).coerceAtLeast(1),
                            emptyList(),
                            phrases
                        )
                    }
                    period.endsWith("mes") -> if (n == 1) {
                        RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases)
                    } else {
                        RecurrenceResult(
                            RecurrenceFrequency.DAILY,
                            (30 / n).coerceAtLeast(1),
                            emptyList(),
                            phrases
                        )
                    }
                    period.endsWith("año") || period.endsWith("ano") -> if (n == 1) {
                        RecurrenceResult(RecurrenceFrequency.YEARLY, 1, emptyList(), phrases)
                    } else {
                        RecurrenceResult(
                            RecurrenceFrequency.MONTHLY,
                            (12 / n).coerceAtLeast(1),
                            emptyList(),
                            phrases
                        )
                    }
                    else -> if (n == 1) { // "al día"/"por día"
                        RecurrenceResult(RecurrenceFrequency.DAILY, 1, emptyList(), phrases)
                    } else {
                        RecurrenceResult(
                            RecurrenceFrequency.HOURLY,
                            (24 / n).coerceAtLeast(1),
                            emptyList(),
                            phrases
                        )
                    }
                }
            }
        }

        val fixedPatterns = listOf(
            Regex("""(?i)\btodos\s+los\s+d[ií]as\b|\bcada\s+d[ií]a\b|\bdiariamente\b|\ba\s+diario\b""") to RecurrenceFrequency.DAILY,
            Regex("""(?i)\btodas\s+las\s+[sS]emanas\b|\bcada\s+[sS]emana\b|\bsemanalmente\b|\bsemanal\b""") to RecurrenceFrequency.WEEKLY,
            Regex("""(?i)\btodos\s+los\s+meses\b|\bcada\s+mes\b|\bmensualmente\b|\bmensual\b""") to RecurrenceFrequency.MONTHLY,
            Regex("""(?i)\btodos\s+los\s+a[nñ]os\b|\bcada\s+a[nñ]o\b|\banualmente\b|\banual\b""") to RecurrenceFrequency.YEARLY
        )
        fixedPatterns.forEach { (pattern, frequency) ->
            pattern.find(working)?.let { match ->
                phrases += match.range
                return RecurrenceResult(frequency, 1, emptyList(), phrases)
            }
        }

        // Adjetivo "diario/diaria" como cadencia DIARIA: la forma adjetiva más común de
        // un hábito cotidiano en español ("repaso diario", "reunión diaria", "medicación
        // diaria"), simétrica a "mensual/semanal/anual". Antes solo se reconocían las
        // frases adverbiales ("cada día", "a diario", "diariamente"): el adjetivo caía a
        // NONE y el hábito nacía SIN recurrencia ni recordatorio periódico (P1: rutina
        // silenciosamente perdida). Se procesa DESPUÉS de fixedPatterns para que "a diario"
        // siga limpiando la frase completa y conserve su título esperado.
        // Guardas contra el sustantivo "diario" (periódico/cuaderno): no precedido de
        // artículo (el/la/... un diario) ni seguido de " de " (diario de viaje). La forma
        // femenina "diaria" es casi siempre adjetivo, pero aplica las mismas guardas.
        dailyAdjectivePattern.find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.DAILY, 1, emptyList(), phrases)
        }

        return base
    }

    // (?i) + \b para no trocear palabras. Lookbehind negativo de artículo y lookahead
    // negativo de " de" filtran el sustantivo. Kotlin/Java soportan lookbehind acotado.
    private val dailyAdjectivePattern =
        Regex("""(?i)(?<!\b(?:el|la|los|las|un|una|unos|unas)\s)\bdiari[oa]\b(?! de\b)""")

    // Adjetivos/adverbios de recurrencia redundantes cuando una FRASE ANCLADA ya expresa
    // la cadencia. parseRecurrence() retorna desde la rama anclada (monthlyDayPattern,
    // dayListPattern, cada-N-quincena...) ANTES de llegar a fixedPatterns, que es donde
    // se consumen estos adjetivos. Sin este paso, "pago mensual el 15 de cada mes" o
    // "pago semanal los lunes" dejaban el adjetivo filtrado en el título ("pago mensual"),
    // inconsistente con "pago mensual el 15" → "pago". La cadencia ya la porta el anclaje,
    // así que el adjetivo no es contenido. Es no-op cuando el adjetivo fue el detector
    // (caso "pago mensual" a secas): ya se borró vía phraseRanges arriba. No incluye
    // "diario/diaria" (sustantivo "el diario" = periódico, con guarda propia arriba) ni
    // aplica guardas de artículo: estos adjetivos no son sustantivos cotidianos.
    // Incluye también la frase de intervalo-1 "cada <unidad>" ("cada semana", "cada día",
    // "cada mes", "cada año"): misma clase de fuga. detectWeekInterval/intervalPattern
    // exigen una cantidad ("cada 2 semanas"), así que la forma sin número es detectada en
    // fixedPatterns (que se salta al retornar antes desde la rama anclada) y filtraba al
    // título. "reunión cada semana los lunes" dejaba "reunión cada semana" (la cadencia ya
    // la porta "los lunes"), inconsistente con "reunión semanal los lunes" → "reunión".
    // No-op cuando "cada <unidad>" fue el detector ("gym cada semana" a secas): ya se
    // borró vía phraseRanges. Sólo casa las formas sin cantidad; las con número
    // ("cada dos semanas") las añade detectWeekInterval/intervalPattern a phraseRanges.
    private val recurrenceAdjectiveLeakPattern =
        Regex("""(?i)\b(?:semanal(?:mente)?|bisemanal(?:mente)?|mensual(?:mente)?|anual(?:mente)?|bimestral(?:mente)?|trimestral(?:mente)?|semestral(?:mente)?|quincenal(?:mente)?|cada\s+(?:semanas?|d[ií]as?|mes(?:es)?|a[nñ]os?))\b""")

    private fun parseMonthNameDate(today: LocalDate, match: MatchResult): LocalDate? {
        val day = parseWrittenNumber(match.groupValues[1])?.toInt()?.takeIf { it in 1..31 } ?: return null
        val month = months[match.groupValues[2].lowercase()] ?: return null
        val rawYear = match.groupValues[3].toIntOrNull()
        val year = when {
            rawYear == null -> today.year
            rawYear < 100 -> 2000 + rawYear
            else -> rawYear
        }
        // "el 29 de febrero" (sin año) en año no bisiesto: el usuario se refiere al
        // PRÓXIMO 29 de febrero real (año bisiesto), no a un 28/2 cualquiera. Recuperar
        // la intención evita dueAt=null y que la frase quede como título basura.
        // Antes: LocalDate.of lanzaba -> null -> tarea sin fecha y con "el 29 de
        // febrero" como título (pérdida de la señal temporal).
        if (rawYear == null && month == 2 && day == 29) {
            var y = today.year
            if (!Year.isLeap(y.toLong()) || LocalDate.of(y, 2, 29).isBefore(today)) {
                do { y++ } while (!Year.isLeap(y.toLong()))
            }
            return LocalDate.of(y, 2, 29)
        }
        // Día imposible para el mes/año (p. ej. "el 31 de abril", "el 30 de febrero"):
        // se ajusta al último día válido del mes (abril 30, febrero 28/29). Honesto:
        // el mes nombrado se respeta; el día se normaliza. Con año explícito y 29 de
        // feb no bisiesto, también cae aquí (-> 28 de feb de ese año).
        var date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: LocalDate.of(year, month, minOf(day, YearMonth.of(year, month).lengthOfMonth()))
        if (rawYear == null && date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    /**
     * "mediados/finales/principios de [mes nombre]": resuelve la fecha canónica del
     * límite mensual en el mes nombrado. principios/comienzos/primeros→día 1,
     * mediados/mitad→día 15, finales/fin/cierre/corte→último día del mes. El año es
     * implícito (hoy) salvo que venga explícito (2 o 4 cifras); sin año explícito,
     * si la fecha resultante ya pasó se rueda al año siguiente (mismo criterio que
     * parseMonthNameDate, para no agendar vencimientos en el pasado).
     */
    private fun parseMonthBoundaryName(
        today: LocalDate,
        qualifier: String,
        month: Int,
        rawYear: String?
    ): LocalDate? {
        val yearStr = rawYear?.takeIf { it.isNotBlank() }
        val year = when {
            yearStr == null -> today.year
            yearStr.toIntOrNull()?.let { it < 100 } == true -> 2000 + yearStr.toInt()
            else -> yearStr.toIntOrNull() ?: return null
        }
        val q = qualifier.lowercase()
        val day = when {
            q.contains("princip") || q.contains("comienz") || q.contains("primer") || q.contains("inicio") -> 1
            q.contains("mediad") || q.contains("mitad") -> 15
            else -> YearMonth.of(year, month).lengthOfMonth()
        }
        var date = LocalDate.of(year, month, day)
        if (yearStr == null && date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    private fun nextWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (target.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(if (delta == 0) 7 else delta.toLong())
    }

    /**
     * Mes de referencia para un límite mensual ("fin de mes", "mediados de mes",
     * "principios de mes"). Sin modificador, replica el avance original: "fin de mes"
     * cae en el último día de este mes salvo que hoy YA sea el último (→ siguiente mes);
     * "mediados de mes" en el 15 salvo que hoy ≥ 15 (→ siguiente mes); "principios de
     * mes" rueda al 1 del mes siguiente (hoy ≥ 1 salvo hoy=1). Con modificador de MES
     * QUE VIENE / PRÓXIMO, la fecha se ancla al mes SIGUIENTE al actual (today+1 mes),
     * sin roll adicional — "principios del mes que viene" dicho a medidados de agosto es
     * 1 de septiembre, no 1 de octubre (antes el modificador se ignoraba y la fecha
     * adelantaba un mes, P1; un doble-desplazamiento sería el error simétrico).
     */
    private fun monthBaseForBoundary(today: LocalDate, matched: String): LocalDate {
        val t = matched.lowercase()
        val isNext = t.contains("que viene") || t.contains("que entra") || t.contains("próxim") || t.contains("proxim") || t.contains("entrante")
        if (isNext) return today.plusMonths(1)
        val kind = when {
            t.contains("fin") || t.contains("finales") || t.contains("cierre") || t.contains("corte") || t.contains("últim") || t.contains("ultim") -> "end"
            t.contains("mediados") || t.contains("mediado") || t.contains("mitad") -> "mid"
            else -> "start"
        }
        return when (kind) {
            "end" -> {
                val lastDayThis = today.withDayOfMonth(today.lengthOfMonth())
                if (today.isBefore(lastDayThis)) today else lastDayThis.plusMonths(1)
            }
            "mid" -> {
                val fifteenthThis = today.withDayOfMonth(15)
                if (today.isBefore(fifteenthThis)) today else fifteenthThis.plusMonths(1)
            }
            else -> {
                // "principios de mes": si hoy es 1, se mantiene hoy; si no, rueda al 1
                // del mes siguiente.
                val firstThis = today.withDayOfMonth(1)
                if (today.isAfter(firstThis)) firstThis.plusMonths(1) else firstThis
            }
        }
    }

    /**
     * Año de referencia para un límite anual ("fin de año", "mediados de año",
     * "principios de año"). Sin modificador, replica la lógica de
     * [monthBaseForBoundary]: "fin de año" cae en 31/12 de este año salvo que hoy
     * YA sea 31/12 (→ año siguiente); "mediados de año" en 30/6 salvo que hoy ≥ 30/6
     * (→ año siguiente); "principios de año" rueda al 1/1 del año siguiente (hoy ≥ 1/1
     * salvo hoy=1/1). Con modificador de AÑO QUE VIENE / ENTRANTE, ancla al año
     * siguiente sin roll adicional, consistente con el calificador mensual.
     */
    private fun yearBaseForBoundary(today: LocalDate, matched: String): LocalDate {
        val t = matched.lowercase()
        val isNext = t.contains("que viene") || t.contains("que entra") || t.contains("próxim") || t.contains("proxim") || t.contains("entrante")
        if (isNext) return today.plusYears(1)
        val kind = when {
            t.contains("fin") || t.contains("finales") || t.contains("cierre") || t.contains("corte") ||
                t.contains("últim") || t.contains("ultim") -> "end"
            t.contains("mediados") || t.contains("mediado") || t.contains("mitad") -> "mid"
            else -> "start"
        }
        return when (kind) {
            "end" -> {
                val lastDayThis = today.withMonth(12).withDayOfMonth(31)
                if (today.isBefore(lastDayThis)) today else lastDayThis.plusYears(1)
            }
            "mid" -> {
                val midThis = today.withMonth(6).withDayOfMonth(30)
                if (today.isBefore(midThis)) today else midThis.plusYears(1)
            }
            else -> {
                val firstThis = today.withMonth(1).withDayOfMonth(1)
                if (today.isAfter(firstThis)) firstThis.plusYears(1) else firstThis
            }
        }
    }

    /**
     * Próxima ocurrencia de [target] desde [from] INCLUDING hoy si hoy es ese día.
     * A diferencia de [nextWeekday] (que las recurrencias usan para exigir "próximo"
     * estricto), ésta sirve para la fecha suelta "el viernes" dicho el propio viernes:
     * la cita puede ser hoy. El descarte de "hoy si la hora ya pasó" se resuelve al
     * combinar fecha+hora, no aquí.
     */
    private fun nextWeekdayOrSame(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (target.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(delta.toLong())
    }

    /**
     * Día [target] de la SEMANA PRÓXIMA (lunes→domingo). "la semana que viene el viernes"
     * no es +7d ni el próximo viernes relativo a hoy: es el viernes de la semana que
     * empieza el próximo lunes. Se ancla al próximo lunes estricto
     * (TemporalAdjusters.next(MONDAY), excluye la semana actual) y se suma el offset del
     * weekday objetivo. "la semana que viene el lunes" dicho un lunes → lunes de la
     * semana siguiente (no hoy), consistente con "semana que viene" = semana no actual.
     */
    private fun nextWeekWeekdayDate(today: LocalDate, target: DayOfWeek, zone: ZoneId): Long {
        val startOfNextWeek = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val date = startOfNextWeek.plusDays((target.value - DayOfWeek.MONDAY.value).toLong())
        return DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
    }

    /**
     * "el viernes de esta semana": el weekday [target] de la semana ISO actual
     * (lunes→domingo). Si ya pasó, queda en el pasado (vencida honesta, doctrina
     * "el lunes pasado"): el calificador ancla ESTA semana, no la próxima.
     */
    private fun thisWeekWeekdayDate(today: LocalDate, target: DayOfWeek, zone: ZoneId): Long {
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val date = startOfWeek.plusDays((target.value - DayOfWeek.MONDAY.value).toLong())
        return DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
    }

    /**
     * última ocurrencia PASADA de [target] desde [from] (excluye hoy: si hoy es el
     * día target, "el X pasado" refiere al de la semana anterior, no a hoy).
     */
    private fun previousWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
        val delta = (from.dayOfWeek.value - target.value + 7) % 7
        return from.minusDays(if (delta == 0) 7 else delta.toLong())
    }

    /**
     * Ocurrencia ORDINAL de [weekday] en un mes: "el último viernes del mes" (última),
     * "el primer lunes de agosto" (primera), "el tercer viernes del mes que viene".
     *
     * - Ordinal: último/ultimo → última del mes (`lastDayOfMonth`+`previousOrSame`).
     *   primer/primero/segundo/tercer/tercero/cuarto → N-ésima desde el inicio
     *   (`firstInMonth`+`plusWeeks(n-1)`); todo mes tiene ≥4 de cada weekday, así que
     *   siempre existe.
     * - Calificador de mes: "del mes" = mes actual, sin roll (vencida honesta si el
     *   último weekday ya pasó, como previousWeekday); "del mes que viene/próximo/entrante"
     *   = mes siguiente; "de <mes>" = ese mes este año, con recálculo en el año siguiente
     *   si ya pasó (mismo fin que parseMonthNameDate, pero recalculando el weekday objetivo
     *   en vez de `plusYears`, que desplazaría el día de la semana).
     * - Recurrencia: una fecha suelta vencida es honesta (deuda real). Pero una recurrencia
     *   mensual ("el primer lunes de cada mes") sembrada en el pasado OLVIDA la primera
     *   ocurrencia: queda vencida al instante y su recordatorio se descarta (trigger ≤ now).
     *   Simétrico con el patrón past-safe de ReminderRules/RecurrenceEngine/deferToNextDay:
     *   si hay recurrencia y la ocurrencia ordinal calculada ya pasó, se avanza al próximo
     *   mes válido con el mismo ordinal+weekday (nunca en pasado). Así la cadencia arranca
     *   en la próxima cita real en vez de en una ya vencida que nadie recuerda.
     */
    private fun lastWeekdayOfMonth(today: LocalDate, capture: OrdinalMonthlyCapture, isRecurring: Boolean = false): LocalDate {
        val ordinalWord = capture.ordinalWord.lowercase()
        val weekday = capture.weekdayWord.toDayOfWeek()
        val ordinal = when (ordinalWord) {
            "último", "ultimo" -> -1
            "penúltimo", "penultimo" -> -2
            "antepenúltimo", "antepenultimo" -> -3
            "primer", "primero" -> 1
            "segundo" -> 2
            "tercer", "tercero" -> 3
            "cuarto" -> 4
            "quinto" -> 5
            else -> -1
        }
        // "del mes que viene/que entra/próximo/entrante" → mes siguiente; "del mes pasado/
        // anterior" → mes anterior (fecha vencida honesta, sin roll al futuro); "este mes" NO.
        val isNext = capture.isNext
        val isPrevious = capture.isPrevious
        val monthName = capture.monthName?.lowercase()
        val yearStr = capture.yearStr
        val namedMonth = monthName?.let { months[it] }
        // Mes nombrado: año actual salvo explícito (2 cifras → 2000+). "del mes" (sin
        // mes-nombre ni isNext ni isPrevious) = mes en curso (vencida honesta si ya pasó).
        var year = when {
            yearStr == null -> today.year
            yearStr.toIntOrNull()?.let { it < 100 } == true -> 2000 + yearStr.toInt()
            else -> yearStr.toIntOrNull() ?: today.year
        }
        val month = when {
            namedMonth != null -> namedMonth
            isNext -> if (today.monthValue == 12) { year = today.year + 1; 1 } else today.monthValue + 1
            isPrevious -> if (today.monthValue == 1) { year = today.year - 1; 12 } else today.monthValue - 1
            else -> today.monthValue
        }
        // ord ≥5 ("quinto"): un mes sólo tiene 5ª ocurrencia de un weekday si tiene 31 días
        // y el día 1 cae en ese weekday o antes. Cuando el mes objetivo no tiene 5ª, se
        // avanza/retrocede mes a mes (hasta 24) hasta hallar uno que sí; así "el quinto
        // viernes del mes" no agenda silenciosamente el 1er viernes del mes siguiente
        // (plusWeeks(4)). "del mes pasado" retrocede (fecha vencida honesta); el resto
        // avanza (nunca en pasado salvo que el propio mes objetivo sea futuro válido).
        var date = if (isPrevious) nthWeekdayBackward(year, month, ordinal, weekday)
                   else nthWeekdayForward(year, month, ordinal, weekday)
        // "del mes pasado/anterior" es una fecha PASADA explícita (el usuario registra una
        // tarea vencida refiriéndose al mes previo): se mantiene honesta en el pasado, sin
        // roll al año siguiente ni avance de recurrencia (igual que "ayer"/"el jueves pasado").
        if (isPrevious) return date
        // Mes nombrado (no "del mes"/"este mes"/isNext) ya pasado SIN año explícito: si es un
        // mes DISTINTO al actual (p. ej. "de enero" dicho en agosto), se recalcula en el año
        // siguiente (no agendar en pasado). Si es el mes ACTUAL ("primer lunes de agosto" dicho
        // el 14, cuando el lunes 3 ya pasó), se mantiene vencido honesto (igual que "del mes"):
        // el usuario se refiere a este agosto, no al del año que viene. Se RECALCULA el weekday
        // en el nuevo año vía nthWeekdayInMonth (no `plusYears(1)`, que desplaza el día de la
        // semana: "último viernes de junio" rodado a 2027 caería en sábado 2027-06-26, no viernes
        // 2027-06-25).
        if (namedMonth != null && yearStr == null && month != today.monthValue && date.isBefore(today)) {
            date = nthWeekdayForward(year + 1, month, ordinal, weekday)
        }
        // Recurrencia con ocurrencia ordinal ya pasada: avanzar al próximo mes que mantenga
        // el mismo ordinal+weekday sin caer en pasado (ver cabecera). El mes siguiente siempre
        // es posterior, así que una iteración basta; el bucle es seguro por guardián. Ord 5
        // puede saltar meses sin 5ª ocurrencia (nthWeekdayForward ya los ignora).
        if (isRecurring && date.isBefore(today)) {
            var y = year
            var m = month
            var guard = 0
            while (date.isBefore(today) && guard++ < 24) {
                m += 1
                if (m > 12) { m = 1; y += 1 }
                date = nthWeekdayForward(y, m, ordinal, weekday)
            }
        }
        return date
    }

    /**
     * N-ésima ocurrencia de [weekday] en (year, month), avanzando mes a mes (hasta 24)
     * cuando el ordinal es ≥5 y el mes objetivo no tiene 5ª ocurrencia. Siempre devuelve
     * una fecha no nula: ord 1..4 y negativos existen en todo mes; ord 5 halla un mes con
     * 5ª ocurrencia en pocas iteraciones, y como reserva última se ancla al último weekday
     * del mes objetivo (ordinal -1, que siempre existe). Evita que `plusWeeks(4)` ruede
     * silenciosamente al mes siguiente agendando una fecha errónea.
     */
    private fun nthWeekdayForward(year: Int, month: Int, ordinal: Int, weekday: DayOfWeek): LocalDate {
        var y = year
        var m = month
        var guard = 0
        var date = nthWeekdayInMonth(y, m, ordinal, weekday)
        while (date == null && guard++ < 24) {
            m += 1
            if (m > 12) { m = 1; y += 1 }
            date = nthWeekdayInMonth(y, m, ordinal, weekday)
        }
        return date ?: nthWeekdayInMonth(year, month, -1, weekday)!!
    }

    /**
     * Simétrico hacia atrás de [nthWeekdayForward]: busca la N-ésima ocurrencia de
     * [weekday] en (year, month) retrocediendo mes a mes (hasta 24) cuando el ordinal
     * es ≥5 y el mes objetivo no tiene 5ª ocurrencia. Se usa para "del mes pasado":
     * el usuario se refiere al 5º viernes MÁS RECIENTE EN EL PASADO, no a uno futuro.
     * Reserva última: ancla al último weekday del mes objetivo (ordinal -1, siempre
     * existe) si ningún mes previo en 24 iteraciones tuviera 5ª ocurrencia.
     */
    private fun nthWeekdayBackward(year: Int, month: Int, ordinal: Int, weekday: DayOfWeek): LocalDate {
        var y = year
        var m = month
        var guard = 0
        var date = nthWeekdayInMonth(y, m, ordinal, weekday)
        while (date == null && guard++ < 24) {
            m -= 1
            if (m < 1) { m = 12; y -= 1 }
            date = nthWeekdayInMonth(y, m, ordinal, weekday)
        }
        return date ?: nthWeekdayInMonth(year, month, -1, weekday)!!
    }

    /**
     * N-ésima (ordinal<0 = última, -2 = penúltima, -3 = antepenúltima) ocurrencia de
     * [weekday] en (year, month). Devuelve `null` únicamente para ordinales ≥5 cuando el
     * mes no tiene una 5ª ocurrencia de ese weekday (sólo posible en meses de 31 días con
     * el día 1 en el weekday adecuado): `plusWeeks(4)` rodaría silenciosamente al mes
     * siguiente y agendaría una fecha errónea. Para ord 1..4 y negativos siempre existe.
     */
    private fun nthWeekdayInMonth(year: Int, month: Int, ordinal: Int, weekday: DayOfWeek): LocalDate? =
        if (ordinal < 0) {
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.previousOrSame(weekday))
                .minusWeeks((-ordinal - 1).toLong())
        } else {
            val candidate = LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.firstInMonth(weekday))
                .plusWeeks((ordinal - 1).toLong())
            // ord ≥5: la 5ª ocurrencia no existe si el candidato cayó fuera de este mes
            // (plusWeeks rueda al mes siguiente). Ord 1..4 siempre cae dentro del mes.
            if (ordinal <= 4 || candidate.monthValue == month && candidate.year == year) candidate else null
        }

    /**
     * Próxima fecha con el día del mes [day], desde [from] (inclusive: si hoy es el día N,
     * vence hoy). Avanza de mes si el día no existe en el mes actual (p. ej. 31 en feb)
     * —java.time.date fallaría— o si ese día ya pasó este mes. Recorre como máximo 12
     * meses para que días ≤ 31 siempre hallen un mes válido (31 existe en jul/ago/oct/dic).
     */
    private fun nextMonthlyDate(from: LocalDate, day: Int): LocalDate {
        var candidate = from.withDayOfMonth(1)
        repeat(24) {
            val dim = candidate.month.length(candidate.isLeapYear)
            if (day <= dim) {
                val date = candidate.withDayOfMonth(day)
                if (!date.isBefore(from)) return date
            }
            candidate = candidate.plusMonths(1)
        }
        // Reserva: día válido en algún mes, nunca debe llegarse aquí (day ≤ 31).
        return from.withDayOfMonth(minOf(day, from.month.length(from.isLeapYear)))
    }

    /**
     * c.315: 1ª ocurrencia futura de una recurrencia mensual con varios días
     * ("el 1 y 15 de cada mes"). Devuelve el menor día de [days] estrictamente
     * posterior a `from.dayOfMonth` dentro del mismo mes (p. ej. hoy=29-jul con
     * [15,30] → 30-jul); si ningún día cabe este mes, el menor día del mes
     * siguiente que exista (sin omitir ciclos). Simétrico a [nextMonthlyDate].
     */
    private fun nextMonthlyDateFromList(from: LocalDate, days: List<Int>): LocalDate {
        val sameMonth = days.firstOrNull { it > from.dayOfMonth && it <= from.month.length(from.isLeapYear) }
        if (sameMonth != null) return from.withDayOfMonth(sameMonth)
        val target = days.min()
        var candidate = from.withDayOfMonth(1).plusMonths(1)
        repeat(24) {
            val dim = candidate.month.length(candidate.isLeapYear)
            if (target <= dim) return candidate.withDayOfMonth(target)
            candidate = candidate.plusMonths(1)
        }
        return from.withDayOfMonth(minOf(target, from.month.length(from.isLeapYear)))
    }

    /**
     * c.341 — primera ocurrencia de una LISTA de días del mes anclada a un mes/año
     * NOMBRADOS ("reunión los días 15 y 30 de septiembre"). Paridad con
     * `nextMonthlyDateFromList` (que ancla al mes actual) y con `parseMonthNameDate`
     * (regla de año implícito: si la fecha ya pasó, rueda al año siguiente).
     *
     * Semántica:
     *  • Mes nombrado estrictamente futuro este año → 1ª = menor día de la lista en
     *    ese mes ("septiembre" dicho en julio → 15-sep).
     *  • Mes nombrado = mes actual → 1ª = menor día de la lista que sea ≥ hoy (si
     *    quedan); si no, rueda al año siguiente.
     *  • Mes nombrado ya pasado este año (sin año explícito) → rueda al año
     *    siguiente y toma el menor día ("mayo" dicho en julio → 15-may año próximo).
     *  • Año explícito → se respeta literal (sin roll), normalizando días imposibles
     *    al último día válido del mes (consistente con parseMonthNameDate).
     */
    private fun nextMonthlyDateFromListInMonth(
        today: LocalDate,
        days: List<Int>,
        namedMonth: Int,
        namedYear: Int?
    ): LocalDate {
        val sorted = days.sorted()
        var year = namedYear ?: today.year
        val dim = YearMonth.of(year, namedMonth).lengthOfMonth()
        // ¿Queda algún día futuro (>= hoy) en el (year, namedMonth)?
        val upcoming = sorted.firstOrNull { d ->
            val dd = minOf(d, dim)
            !LocalDate.of(year, namedMonth, dd).isBefore(today)
        }
        if (upcoming != null) {
            return LocalDate.of(year, namedMonth, minOf(upcoming, dim))
        }
        // Ningún día futuro en el mes objetivo este año: rueda al próximo año sólo si
        // el año era implícito (con año explícito es la fecha literal del usuario).
        if (namedYear == null) year += 1
        val dim2 = YearMonth.of(year, namedMonth).lengthOfMonth()
        return LocalDate.of(year, namedMonth, minOf(sorted.first(), dim2))
    }

    /** c.341 — datos de un mes hallado tras una lista de días: NOMBRADO ("de
     *  septiembre") o RELATIVO siguiente ("del mes que viene"/"del mes próximo"/
     *  "del mes entrante"). `month==null && nextMonth==true` señala el mes siguiente
     *  relativo (c.344); `month!=null` es un mes nombrado (c.341). */
    private data class TrailingNamedMonth(
        val month: Int?,
        val year: Int?,
        /** c.344 — true cuando el calificador es relativo "del mes que viene/próximo/
         *  entrante" (mes siguiente, sin mes nombrado). */
        val nextMonth: Boolean = false,
        /** Rango del texto "de <mes> [del <año>]" o "del mes que viene" en working
         *  (para borrarlo del título). */
        val range: IntRange,
        /** Rango de una cadencia trasera opcional ("de cada mes"/"todos los meses"/
         *  "quincenal") que sigue al mes nombrado, para también limpiarla del título. */
        val cadenceRange: IntRange?
    )

    /**
     * c.341 — escanea el texto de [working] a partir de [fromIndex] buscando un mes
     * NOMBRADO opcional ("de septiembre" / "del 2026") y, tras él, una cadencia
     * opcional ("de cada mes"). c.344 — si NO hay mes nombrado, busca el calificador
     * RELATIVO siguiente ("del mes que viene"/"del mes próximo"/"del mes entrante"):
     * la lista multi-día con calificador relativo debe anclar la 1ª fecha al mes
     * siguiente, no al actual (rutina quincenal anclada al mes equivocado = P1).
     * Devuelve [TrailingNamedMonth] sólo si hay un mes válido o un calificador
     * relativo; en caso contrario null (la lista de días se ancla al mes actual).
     *
     * Se invoca desde el bloque `monthlyDualDayPattern` porque ese patrón consume los
     * dígitos de la lista, lo que deja al mes nombrado sin el dígito que exige
     * `monthNamePattern` y lo hacía invisible (P1: cita en mes erróneo).
     */
    private fun scanTrailingNamedMonth(working: String, fromIndex: Int): TrailingNamedMonth? {
        val monthNames = months.keys.joinToString("|")
        // c.341: el mes nombrado va INMEDIATAMENTE tras el match de la lista de días.
        // OJO: `monthlyDualDayPattern` lleva un sufijo opcional `(?:\s+...)?` que puede
        // consumir el espacio entre "30" y "de" → `fromIndex` puede caer justo en la
        // "d" de "de", sin whitespace delantero. Por eso el patrón NO exige `\s+`
        // inicial: usa `\b` (boundary) que casa tanto tras un espacio consumido como al
        // inicio del texto "de <mes>". `\b` también evita falsos positivos dentro de
        // palabras ("render" no casa). Año opcional ("del 2026"/"de 26").
        val monthRegex = Regex("""(?i)\b(?:de|del)\s+($monthNames)(?:\s+del?\s+(\d{2,4}))?\b""")
        val m = monthRegex.find(working, fromIndex)
        if (m != null) {
            val monthName = m.groupValues[1].lowercase()
            val month = months[monthName]
            if (month != null) {
                val rawYear = m.groupValues[2].toIntOrNull()
                val year = when {
                    rawYear == null -> null
                    rawYear < 100 -> 2000 + rawYear
                    else -> rawYear
                }
                // Cadencia trasera opcional tras el mes nombrado ("... de septiembre de cada
                // mes"). Se captura para limpiarla del título; no afecta a la recurrencia (ya
                // es MONTHLY por la lista de días). Empieza justo donde terminó el match del mes.
                val cadenceRegex = Regex("""(?i)\s+(?:de\s+(?:cada\s+)?mes|del\s+(?:cada\s+)?mes|todos\s+los\s+meses|cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""")
                val cadenceMatch = cadenceRegex.find(working, m.range.last + 1)
                return TrailingNamedMonth(month, year, false, m.range, cadenceMatch?.range)
            }
        }
        // c.344: no hay mes nombrado tras la lista. Buscar el calificador relativo
        // "del mes que viene/próximo/entrante" (mismas formas que nextMonthDayPattern
        // pero aquí van tras una LISTA de días, no tras un día único). `del?` admite
        // "de"/"del"; el calificador entero se borra del título. Sin esto la lista
        // multi-día ignoraba "del mes que viene" y anclaba al mes actual (P1).
        val relativeRegex = Regex("""(?i)\b(?:de|del)\s+(?:mes\s+(?:que\s+(?:viene|entra)|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b""")
        val rel = relativeRegex.find(working, fromIndex) ?: return null
        // Cadencia trasera opcional también tras el calificador relativo
        // ("... del mes que viene de cada mes"). Se captura para limpiarla del título.
        val cadenceRegex = Regex("""(?i)\s+(?:de\s+(?:cada\s+)?mes|del\s+(?:cada\s+)?mes|todos\s+los\s+meses|cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""")
        val cadenceMatch = cadenceRegex.find(working, rel.range.last + 1)
        return TrailingNamedMonth(null, null, true, rel.range, cadenceMatch?.range)
    }

    private fun String.toDayOfWeek(): DayOfWeek = weekdays[this.lowercase()] ?: DayOfWeek.MONDAY

    private fun String.toDayOfWeekOrNull(): DayOfWeek? = weekdays[this.lowercase()]

    /**
     * ¿Aparece "mañana" como token de FECHA (el día de mañana) y no sólo como parte
     * de un marcador de parte del día ("de/por/a la mañana")? Recorre todas las
     * apariciones: basta con una suelta para contar como fecha. Así "mañana por la
     * mañana" (primer "mañana" = fecha) sigue siendo mañana, mientras que
     * "a las 9 de la mañana" (única aparición precedida de "la ") no se fecha.
     */
    private fun mananaAsDate(working: String): Boolean {
        // "media mañana" (parte del día, no fecha) se excluye: el prefijo "media " indica
        // punto medio de la mañana, no el día de mañana. Sin esto, "a media mañana" se
        // agendaba MAÑANA (fecha +1) en vez de hoy (P1: cita en día equivocado).
        val timeMarker = Regex("""(?i)(?:de|por|a|en)\s+la\s+$|\besta\s+$|\bmedia\s+$""")
        var idx = 0
        while (true) {
            val m = Regex("""(?i)\bma[nñ]ana\b""").find(working, idx) ?: return false
            val prefix = working.substring(0, m.range.first)
            if (!timeMarker.containsMatchIn(prefix) && !mananaOccurrenceIsContent(working, m.range)) return true
            idx = m.range.last + 1
        }
    }

    /**
     * c.927: ¿es esta aparición de «mañana» CONTENIDO ("la mañana" = the morning)
     * y no la fecha "mañana" (tomorrow)? Sólo con evidencia gramatical inequívoca:
     *  (G1) demostrativo precedente — «esa/aquella/dicha/tal [misma] mañana» o
     *       «la misma mañana»: nadie dice «esa mañana» por "tomorrow".
     *  (G2) genitivo con artículo a continuación — «mañana del accidente»,
     *       «mañana misma del partido», «mañana de la victoria»: un "tomorrow"
     *       nunca gobierna genitivo.
     *  (G3) artículo + adjetivo «siguiente» (c.929) — «la mañana siguiente (me
     *       desperté tarde)» = the following morning, contenido narrativo: un
     *       "tomorrow" nunca lleva artículo ni adjetivo pospuesto. Con conector
     *       («para la mañana siguiente») la frase ya la gobierna la ancla
     *       parte-del-día (+1d) y ésta la consume antes del borrado por rangos,
     *       así que G3 no interfiere; sólo protege la aparición desnuda, que
     *       antes sufría robo de fecha (+1d) y título mutilado.
     * Sin evidencia («el informe de mañana», genitivo de posesión temporal sobre
     * la FECHA) sigue contando como fecha — doctrina vigente del borrado con
     * conector. Usada por [mananaAsDate] (fecha) y [eraseMananaDateToken] (título)
     * para que fecha y título nunca diverjan.
     */
    private fun mananaOccurrenceIsContent(text: String, range: IntRange): Boolean {
        val prefix = text.substring(0, range.first)
        if (Regex("""(?i)\b(?:esa|aquella|dicha|tal)(?:\s+misma)?\s+$""").containsMatchIn(prefix)) return true
        if (Regex("""(?i)\bla\s+misma\s+$""").containsMatchIn(prefix)) return true
        val suffix = text.substring(range.last + 1)
        if (Regex("""(?i)^\s+(?:misma\s+)?(?:del|de\s+(?:la|las|los))\s+\p{L}""").containsMatchIn(suffix)) return true
        if (Regex("""(?i)\bla\s+$""").containsMatchIn(prefix) &&
            Regex("""(?i)^\s+siguientes?\b""").containsMatchIn(suffix)
        ) return true
        // (G4) c.932: «mañana» gobernada por un ordinal narrativo (H3) —
        //      «las primeras horas de la mañana son las mejores»: la parte
        //      del día interior pertenece al sujeto narrativo; sin este guard
        //      el borrado del token-fecha mutilaba el título protegido
        //      («…de la son las mejores»). La fecha ya está excluida por el
        //      timeMarker «de la» de [mananaAsDate], así que este guard sólo
        //      blinda el título.
        if (ordinalHoraNarrativeRanges(text).any { it.containsRange(range) }) return true
        // c.954 del día INTERCALADA de una
        //      narrativa en pretérito c.950 («el lunes en la mañana llegó el
        //      paquete»): sin este guard eraseMananaDateToken mutilaba el
        //      título protegido («…en la llegó el paquete»). La fecha ya está
        //      excluida por el timeMarker «en/por la» de [mananaAsDate].
        if (weekdayPreteriteNarrativeIntercalatedPartOfDayRanges(text).any { it.containsRange(range) }) return true
        return false
    }

    /**
     * c.927: borra del título las apariciones de «mañana» que son FECHA (con su
     * conector "de/del/desde" precedente, como hacía la alternativa del borrado
     * genérico) y conserva íntegras las que son CONTENIDO
     * ([mananaOccurrenceIsContent]). Opera por rangos (no replace global) para no
     * tocar las apariciones protegidas.
     */
    private fun eraseMananaDateToken(title: String, forceDayPreteriteNarrative: Boolean = false): String {
        val token = Regex("""(?i)\bma[nñ]ana\b""")
        val connector = Regex("""(?i)\b(?:de|del|desde)\s+$""")
        var result = title
        var idx = 0
        while (true) {
            val m = token.find(result, idx) ?: return result
            // c.954: el «mañana» que pertenece a la parte del día de una cadena
            // narrativa «hoy/ayer (en|por|…) la mañana …llegó…» se conserva
            // íntegro cuando la fecha no la ancló (decisión tomada en [parse];
            // la cubierta de rangos se recalcula en cada iteración).
            val protectedStandalone = forceDayPreteriteNarrative &&
                standalonePartOfDayPattern.findAll(result)
                    .firstOrNull { m.range.first in it.range }
                    ?.let { dayPreteriteNarrativeOccurrence(result, it) } == true
            if (mananaOccurrenceIsContent(result, m.range) || protectedStandalone) {
                idx = m.range.last + 1
                continue
            }
            val start = connector.find(result.substring(0, m.range.first))?.range?.first ?: m.range.first
            result = result.removeRange(start, m.range.last + 1)
            idx = start
        }
    }

    /**
     * c.930: genitivos que siguen siendo ANCLA tras un ordinal de hora desnudo
     * (parte del día/día/jornada canónicos del propio patrón y weekdays: la forma
     * con weekday legítima («a primera hora del lunes») siempre lleva conector «a»,
     * así que aquí sólo se usa para el caso desnudo ambiguo — conservador).
     */
    private val ordinalHoraAnchorGenitives = setOf(
        "mañana", "manana", "tarde", "noche", "madrugada",
        "día", "dias", "días", "jornada",
        "lunes", "martes", "miércoles", "miercoles", "jueves", "viernes",
        "sábado", "sabado", "domingo"
    )

    /**
     * c.930: ¿es esta aparición de «primera(s) hora(s)»/«última(s) hora(s)»/
     * «primer/último momento» CONTENIDO narrativo ordinal («la primera hora de
     * clase» = the first hour of class) y no el ancla canónica («a primera
     * hora» = 09:00, «a última hora» = 18:00)? Sólo con evidencia gramatical
     * inequívoca, y NUNCA con el conector «a»/«justo a» consumido por el
     * patrón (ancla por doctrina c.102/c.546; excepción c.931: «a la» +
     * genitivo de contenido a continuación = sustantivo narrativo):
     *  (H1) demostrativo precedente («esa primera hora»): nadie la usa como
     *       ancla de las 09:00/18:00.
     *  (H2) artículo precedente («la/las/el/los [misma/o/s/as]») + genitivo de
     *       contenido a continuación («de clase», «del partido»): el ancla
     *       tampoco gobierna genitivo de contenido. Los genitivos-ancla
     *       ([ordinalHoraAnchorGenitives]) quedan como ancla. Desde c.956 el
     *       prefijo «en blanco» (aparición al inicio del texto, sin
     *       determinante ni «en») también dispara esta rama, añadido a los
     *       determinantes/«en» ya admitidos (c.937/c.951/c.952).
     *  (H3) genitivo canónico DENTRO del match («las primeras horas de la
     *       mañana son las mejores», c.932): contenido sólo con determinante
     *       al inicio del texto + predicado a continuación; la parte del día
     *       gobernada se suprime vía [ordinalHoraNarrativeRanges] (fecha y
     *       título) y la «mañana» interior vía G4 de
     *       [mananaOccurrenceIsContent].
     * Usada en la resolución (fecha) y en el
     * borrado del título ([eraseOrdinalHoraToken]) para que nunca diverjan.
     */
    private val ordinalHoraCanonicalSuffix = Regex(
        """(?i)(?:de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada)|del\s+d[ií]a|de\s+(?:la\s+)?jornada|de\s+los\s+d[ií]as|de\s+d[ií]a)$"""
    )
    private val ordinalHoraContentGenitive =
        Regex("""(?i)^\s+(?:del|de(?:\s+(?:la|las|los))?)\s+(\p{L}+)""")

    /**
     * c.932 (H3): prefijo que convierte en CONTENIDO narrativo un ordinal con
     * genitivo canónico dentro del match: TODO el prefijo es sólo un
     * determinante (artículo/demostrativo, opcional «en») al inicio del texto
     * («las primeras horas de la mañana son…», «en las primeras horas del día
     * trabajé»). El ancla de las 09:00/18:00 siempre lleva conector «a»
     * (c.102/c.546/c.931); el determinante desnudo al inicio + predicado es
     * sujeto narrativo. Ver [ordinalHoraOccurrenceIsContent].
     */
    private val ordinalHoraNarrativeDeterminer = Regex(
        """(?i)^\s*(?:en\s+)?(?:la|las|el|los|esa|esas|ese|esos|esta|estas|este|estos|aquella|aquellas|aquel|aquellos|dicha|dichas|dicho|dichos|tal|tales)(?:\s+mism[oa]s?)?\s+$"""
    )

    /**
     * c.935 (extensión de H3): prefijo alternativo que convierte en CONTENIDO
     * narrativo un ordinal con genitivo canónico dentro del match — una
     * cláusula de OPINIÓN inequívoca («creo/pienso/opino/considero/digo/
     * diría/siento que», «me parece que», «para mí», «a mi juicio», «en mi
     * opinión») seguida del determinante al inicio de la subordinada («creo
     * que las primeras horas de la mañana son las mejores», lateral medida
     * FUERA en c.932/c.933). El marcador de opinión convierte el nominal en
     * SUJETO de la subordinada: nunca es ancla (el ancla siempre lleva
     * conector «a», c.102/c.546/c.931/c.933). Sigue exigiéndose predicado a
     * continuación ([ordinalHoraOccurrenceIsContent]); sin él («creo que las
     * primeras horas de la mañana») o con verbo NO de opinión («quiero
     * trabajar las…», «avisar las…») se mantiene la doctrina bivalente/ancla.
     */
    private val ordinalHoraNarrativeOpinionPrefix = Regex(
        """(?i)^\s*(?:(?:creo|pienso|opino|considero|digo|dir[ií]a|siento)\s+que|me\s+parece\s+que|para\s+m[ií]|a\s+mi\s+juicio|en\s+mi\s+opini[oó]n)\s+(?:la|las|el|los|esa|esas|ese|esos|esta|estas|este|estos|aquella|aquellas|aquel|aquellos|dicha|dichas|dicho|dichos|tal|tales)(?:\s+mism[oa]s?)?\s+$"""
    )

    /**
     * c.932: rangos de las apariciones de ordinal de hora que son CONTENIDO
     * narrativo ([ordinalHoraOccurrenceIsContent]). Se usa para la supresión
     * de la parte-del-día GOBERNADA: cuando el ordinal narrativo consumió el
     * genitivo canónico dentro de su propio match («las primeras horas de la
     * mañana son las mejores»), `standalonePartOfDayPattern` no debe robar la
     * parte del día interior ni en la fecha ni en el título, y la «mañana»
     * interior queda protegida del borrado de token-fecha (G4).
     */
    private fun ordinalHoraNarrativeRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        for (pattern in listOf(primeraHoraPattern, ultimaHoraPattern)) {
            var idx = 0
            while (true) {
                val m = pattern.find(text, idx) ?: break
                if (ordinalHoraOccurrenceIsContent(text, m)) {
                    // c.941: el rango narrativo se extiende hasta el weekday
                    // genitivo cuando hay genitivo INTERIOR de parte del día
                    // («de la noche del sábado»): así la cascada de borrado
                    // (parte del día / mañana) no roba la cadena del sujeto
                    // narrativo; fecha y título siguen el mismo predicado.
                    val interior = ordinalHoraInteriorPartOfDayWeekdayRange(text, m)
                    ranges += if (interior != null) m.range.first..interior.last else m.range
                }
                idx = m.range.last + 1
            }
        }
        return ranges
    }

    /**
     * c.936: weekday genitivo («del/de lunes…») que sigue DIRECTAMENTE a un
     * ordinal narrativo H3 protegido («las primeras horas de la mañana del
     * lunes son tranquilas»). El weekday pertenece a la cadena genitiva del
     * sujeto narrativo: no es ancla. Ver [ordinalHoraNarrativeWeekdayRanges].
     */
    private val ordinalHoraNarrativeWeekdayGenitive = Regex(
        """(?i)^\s+(?:del|de)\s+(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""
    )

    /**
     * c.938: demostrativo AL INICIO del texto (sin verbo precedente) que hace
     * narrativo al ordinal vía H1 ([ordinalHoraOccurrenceIsContent]) y basta
     * por sí solo como evidencia para proteger el weekday genitivo DIRECTO
     * («esa primera hora del lunes fue terrible»). Con verbo precedente
     * («quiero esa primera hora del lunes para estudiar») la forma es
     * bivalente (petición de hueco) y sigue la doctrina ancla — doctrina
     * simétrica a la de H3 («determinante al inicio, sin verbo precedente»).
     * El artículo al inicio siguió la misma doctrina en c.939
     * ([ordinalHoraNarrativeArticlePrefix]).
     */
    private val ordinalHoraNarrativeDemonstrativePrefix = Regex(
        """(?i)^\s*(?:esa|esas|ese|esos|esta|estas|este|estos|aquella|aquellas|aquel|aquellos|dicha|dichas|dicho|dichos|tal|tales)(?:\s+mism[oa]s?)?\s+$"""
    )

    /**
     * c.939: ARTÍCULO AL INICIO del texto (sin verbo precedente) que, con
     * weekday genitivo DIRECTO tras el match y predicado a continuación, hace
     * narrativo al ordinal («la primera hora del lunes fue aburrida») —
     * doctrina H1-artículo, simétrica a [ordinalHoraNarrativeDemonstrativePrefix]
     * (c.938). Con verbo precedente («quiero/prefiero/avisar la primera hora
     * del lunes…») la forma es bivalente (petición de hueco) y sigue la
     * doctrina ancla. Sólo artículos definidos: «en la…»/«una…» quedan fuera
     * (laterales medidas c.939).
     */
    private val ordinalHoraNarrativeArticlePrefix = Regex(
        """(?i)^\s*(?:la|las|el|los)(?:\s+mism[oa]s?)?\s+$"""
    )

    /**
     * c.942: preposición «en» + artículo definido AL INICIO del texto —
     * evidencia inequívoca de sujeto narrativo («en la primera hora del lunes
     * me quedé dormido» = during the first hour of Monday I fell asleep).
     * Doctrina simétrica a [ordinalHoraNarrativeArticlePrefix] (c.939): con
     * verbo/nombre/cláusula precedente («avisar en la…», «reunión en la…»,
     * «quiero en la…», «creo que en la…») el prefijo no empieza en «en» y la
     * forma sigue la doctrina ancla (byte-idéntica). Lateral medida FUERA:
     * sin artículo («en primera hora del lunes…»), que es bivalente.
     */
    private val ordinalHoraNarrativeEnArticlePrefix = Regex(
        """(?i)^\s*en\s+(?:la|las|el|los)\s+$"""
    )

    /**
     * c.943: ARTÍCULO INDEFINIDO AL INICIO del texto — evidencia inequívoca
     * de sujeto narrativo («una primera hora del lunes fue rara» = a first
     * hour of Monday was weird). Doctrina simétrica a
     * [ordinalHoraNarrativeArticlePrefix] (c.939) y
     * [ordinalHoraNarrativeEnArticlePrefix] (c.942): con verbo precedente
     * («quiero/prefiero una…») el prefijo no empieza en el indefinido y la
     * forma sigue la doctrina ancla (byte-idéntica). Se admite con weekday
     * genitivo (directo o tras genitivo interior de parte del día, c.943) y
     * con el genitivo canónico DENTRO del match (rama H3, c.944: «unas
     * primeras horas de la mañana son duras»): el genitivo de contenido H2
     * con indefinido («una primera hora de clase…») se admitió en c.951
     * (lateral medida FUERA c.943…c.948, doctrina simétrica a c.937).
     */
    private val ordinalHoraNarrativeIndefinitePrefix = Regex(
        """(?i)^\s*(?:una|unas|un|unos)\s+$"""
    )

    /**
     * c.946: preposición «en» SIN artículo AL INICIO del texto — la misma
     * evidencia de circunstancial narrativo que
     * [ordinalHoraNarrativeEnArticlePrefix] (c.942) con el artículo elidido
     * («en primera hora del lunes me quedé dormido», común en español
     * informal). Doctrina simétrica: con verbo/nombre/cláusula precedente
     * («avisar en…», «reunión en…», «quiero en…») el prefijo no empieza en
     * «en» y la forma sigue la doctrina ancla (byte-idéntica). Se admite con
     * weekday genitivo (directo o tras genitivo interior de parte del día):
     * el genitivo canónico DENTRO del match (H3) sin determinante queda
     * FUERA — «en primera hora de la mañana llamar al banco» (comando) y
     * «…llamé al banco» (narrativa) son indistinguibles por regex (lateral
     * bivalente medida c.946, pin conservador).
     */
    private val ordinalHoraNarrativeEnPrefix = Regex(
        """(?i)^\s*en\s+$"""
    )

    /**
     * c.947: preposición «en» + artículo INDEFINIDO AL INICIO del texto — el
     * hueco entre [ordinalHoraNarrativeEnArticlePrefix] (c.942, «en la/el…») y
     * [ordinalHoraNarrativeIndefinitePrefix] (c.944, «una/un…» sin «en»):
     * «en una primera hora del día trabajé mejor». Doctrina simétrica a c.944:
     * con verbo/nombre/cláusula precedente el prefijo no empieza en «en» y la
     * forma sigue la doctrina ancla (byte-idéntica); la bivalente pura con
     * predicado de comando queda del lado narrativo, mismo compromiso que
     * c.932/c.944. c.948: la misma evidencia se admite también con weekday
     * genitivo (directo o interior) — hueco entre c.943 y c.946. c.951: y con
     * el genitivo de CONTENIDO H2 («en una primera hora de clase…»), doctrina
     * simétrica a c.937 con el indefinido de c.943/c.948.
     */
    private val ordinalHoraNarrativeEnIndefinitePrefix = Regex(
        """(?i)^\s*en\s+(?:una|unas|un|unos)\s+$"""
    )

    /** c.939: palabras weekday admitidas como genitivo directo narrativo. */
    private val ordinalHoraNarrativeWeekdayWords = setOf(
        "lunes", "martes", "miércoles", "miercoles", "jueves", "viernes",
        "sábado", "sabado", "domingo"
    )

    /** c.941: palabras de parte del día admitidas como genitivo INTERIOR narrativo. */
    private val ordinalHoraInteriorPartOfDayWords = setOf(
        "mañana", "manana", "tarde", "noche", "madrugada"
    )

    /**
     * c.941: genitivo genérico de parte del día tras el ordinal («de la
     * noche/tarde/mañana/madrugada», con o sin artículo) en una cadena
     * narrativa — hermana de [ordinalHoraNarrativeWeekdayGenitive] (weekday).
     * Sólo se usa ya verificado el prefijo narrativo
     * ([ordinalHoraNarrativeDemonstrativePrefix]/[ordinalHoraNarrativeArticlePrefix]).
     */
    private val ordinalHoraNarrativePartOfDayGenitive = Regex(
        """(?i)^\s+(?:del\s+d[ií]a|de\s+(?:la\s+)?(?:ma[nñ]ana|madrugada|tarde|noche)|de\s+d[ií]a)\b"""
    )

    /**
     * c.941: rango ABSOLUTO del genitivo INTERIOR de parte del día seguido de
     * un weekday genitivo cuando la cadena es narrativa (prefijo
     * demostrativo/artículo al inicio + predicado a continuación y sin
     * modificador del weekday): «las primeras horas de la noche del sábado
     * fueron mágicas». Sin este guard el weekday robaba una fecha FALSA y el
     * ordinal + ambos genitivos se borraban del título (doble daño P1 — el
     * sufijo canónico de [primeraHoraPattern] sólo admite mañana/madrugada,
     * así «de la noche/tarde» quedaba fuera de la doctrina H3/c.936 y de la
     * directa/c.939). El predicado exigido es el mismo de la doctrina
     * H1-artículo c.939 (sufijo no-blanco tras el genitivo); el modificador
     * («del sábado que viene») sigue siendo ancla, simétrico a c.936/c.939.
     * Usado en la resolución ([ordinalHoraOccurrenceIsContent]), en los rangos
     * narrativos ([ordinalHoraNarrativeRanges]) y en los rangos de weekday
     * ([ordinalHoraNarrativeWeekdayRanges]) para que nunca diverjan.
     */
    private fun ordinalHoraInteriorPartOfDayWeekdayRange(
        text: String,
        match: MatchResult
    ): IntRange? {
        val prefix = text.substring(0, match.range.first)
        // c.948: y «en» + artículo INDEFINIDO al inicio («en una primera hora
        // de la noche del sábado sonó el teléfono» — hueco entre c.943 y
        // c.946, simétrico a la admisión de la rama H1-weekday directo).
        if (!ordinalHoraNarrativeDemonstrativePrefix.containsMatchIn(prefix) &&
            !ordinalHoraNarrativeArticlePrefix.containsMatchIn(prefix) &&
            !ordinalHoraNarrativeEnArticlePrefix.containsMatchIn(prefix) &&
            !ordinalHoraNarrativeIndefinitePrefix.containsMatchIn(prefix) &&
            !ordinalHoraNarrativeEnPrefix.containsMatchIn(prefix) &&
            !ordinalHoraNarrativeEnIndefinitePrefix.containsMatchIn(prefix)
        ) return null
        val suffix = text.substring(match.range.last + 1)
        val pod = ordinalHoraNarrativePartOfDayGenitive.find(suffix) ?: return null
        val tail = suffix.substring(pod.range.last + 1)
        val wg = ordinalHoraNarrativeWeekdayGenitive.find(tail) ?: return null
        if (tail.substring(wg.range.last + 1).isBlank()) return null
        val wm = weekdayPattern.find(tail)
        if (wm != null && wm.range.last > wg.range.last) return null
        val start = match.range.last + 1 + pod.range.first
        val end = match.range.last + 1 + pod.range.last + 1 + wg.range.last
        return start..end
    }

    /**
     * c.936: rangos de las apariciones de weekday que son CONTENIDO narrativo
     * por gobernarlas un ordinal H3 protegido: el genitivo canónico está
     * dentro del match del ordinal ([ordinalHoraCanonicalSuffix]), el ordinal
     * es narrativo ([ordinalHoraOccurrenceIsContent]), el weekday genitivo lo
     * sigue directamente y hay predicado a continuación (doctrina H3: sin
     * predicado el fragmento es bivalente y sigue la doctrina vigente). Un
     * weekday con modificador («del lunes que viene») no queda contenido en
     * el rango: el match de [weekdayPattern] lo extiende más allá y
     * [containsRange] lo excluye de forma natural. Se usa en la resolución
     * (fecha) y en el borrado del título ([eraseWeekdayToken]) para que
     * nunca diverjan, como [ordinalHoraNarrativeRanges].
     *
     * c.937 (extensión a H2): el genitivo entre el ordinal y el weekday puede
     * ser de CONTENIDO (H1/H2/«a la» de [ordinalHoraOccurrenceIsContent]) en
     * vez del canónico dentro del match: «la primera hora de clase del lunes
     * fue aburrida». En ese caso el weekday genitivo sigue al genitivo de
     * contenido; se exige el mismo predicado a continuación.
     *
     * c.938 (extensión a H1 puro): el demostrativo AL INICIO del texto
     * ([ordinalHoraNarrativeDemonstrativePrefix]) basta como evidencia — no
     * necesita genitivo de contenido — y protege el weekday genitivo DIRECTO:
     * «esa primera hora del lunes fue terrible». Con verbo precedente la forma
     * es bivalente y sigue la doctrina ancla.
     *
     * c.939 (extensión a H1-artículo): el ARTÍCULO al inicio del texto
     * ([ordinalHoraNarrativeArticlePrefix]) con weekday genitivo DIRECTO y
     * predicado a continuación es la misma evidencia de sujeto narrativo:
     * «la primera hora del lunes fue aburrida». La ocurrencia del ordinal se
     * vuelve contenido en [ordinalHoraOccurrenceIsContent] con el mismo
     * predicado (nunca divergen); con verbo precedente, sin predicado o con
     * modificador («que viene») sigue la doctrina ancla vigente.
     */
    private fun ordinalHoraNarrativeWeekdayRanges(
        text: String,
        forcePrimeraHoraAnchor: Boolean = false,
        forceUltimaHoraAnchor: Boolean = false
    ): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        for ((patternIdx, pattern) in listOf(primeraHoraPattern, ultimaHoraPattern).withIndex()) {
            // c.939: el borrado del título re-computa sobre texto YA mutado
            // (p. ej. «de la noche» consumido como hora), donde un genitivo-
            // ancla intermedio desapareció y el weekday genitivo parecería
            // DIRECTO. Si la RESOLUCIÓN ancló la primera ocurrencia del patrón
            // (match != null), esa ocurrencia es ancla por doctrina «nunca
            // divergen» y aquí se fuerza igual — el resto sigue con guard.
            val forceFirst = if (patternIdx == 0) forcePrimeraHoraAnchor else forceUltimaHoraAnchor
            var isFirst = true
            var idx = 0
            while (true) {
                val m = pattern.find(text, idx) ?: break
                val isContent = if (forceFirst && isFirst) {
                    false
                } else {
                    ordinalHoraOccurrenceIsContent(text, m)
                }
                isFirst = false
                if (isContent) {
                    val wgSearchStart = if (ordinalHoraCanonicalSuffix.containsMatchIn(m.value)) {
                        // H3 (c.936): genitivo canónico dentro del match; el
                        // weekday genitivo lo sigue directamente.
                        m.range.last + 1
                    } else {
                        val gen = ordinalHoraContentGenitive.find(text.substring(m.range.last + 1))
                        if (gen != null && gen.groupValues[1].lowercase() !in ordinalHoraAnchorGenitives) {
                            // c.937 (H2): el weekday genitivo sigue al genitivo de
                            // contenido («de clase», «del partido») que hizo
                            // narrativo al ordinal.
                            m.range.last + 1 + gen.range.last + 1
                        } else if (ordinalHoraNarrativeDemonstrativePrefix
                                .containsMatchIn(text.substring(0, m.range.first)) ||
                            ordinalHoraNarrativeArticlePrefix
                                .containsMatchIn(text.substring(0, m.range.first)) ||
                            ordinalHoraNarrativeEnArticlePrefix
                                .containsMatchIn(text.substring(0, m.range.first)) ||
                            ordinalHoraNarrativeIndefinitePrefix
                                .containsMatchIn(text.substring(0, m.range.first)) ||
                            ordinalHoraNarrativeEnPrefix
                                .containsMatchIn(text.substring(0, m.range.first)) ||
                            ordinalHoraNarrativeEnIndefinitePrefix
                                .containsMatchIn(text.substring(0, m.range.first))
                        ) {
                            // c.938 (H1 puro) / c.939 (H1-artículo) / c.942 («en»
                            // + artículo al inicio) / c.943 (indefinido al
                            // inicio) / c.946 («en» sin artículo al inicio) /
                            // c.948 («en» + indefinido al inicio):
                            // determinante al inicio; el weekday
                            // genitivo sigue DIRECTAMENTE al match. c.941:
                            // o tras el genitivo INTERIOR de parte del día («de
                            // la noche/tarde»), ya validado por
                            // [ordinalHoraInteriorPartOfDayWeekdayRange].
                            val pod = ordinalHoraNarrativePartOfDayGenitive
                                .find(text.substring(m.range.last + 1))
                            if (pod != null && ordinalHoraInteriorPartOfDayWeekdayRange(text, m) != null) {
                                m.range.last + 1 + pod.range.last + 1
                            } else {
                                m.range.last + 1
                            }
                        } else -1
                    }
                    if (wgSearchStart >= 0) {
                        val wg = ordinalHoraNarrativeWeekdayGenitive.find(text.substring(wgSearchStart))
                        if (wg != null) {
                            val wgFirst = wgSearchStart + wg.range.first
                            val wgLast = wgSearchStart + wg.range.last
                            if (text.substring(wgLast + 1).isNotBlank()) ranges += wgFirst..wgLast
                        }
                    }
                }
                idx = m.range.last + 1
            }
        }
        return ranges
    }

    private fun IntRange.containsRange(other: IntRange): Boolean =
        other.first >= first && other.last <= last

    private fun ordinalHoraOccurrenceIsContent(text: String, match: MatchResult): Boolean {
        if (Regex("""(?i)^(?:justo\s+)?a\s""").containsMatchIn(match.value)) {
            // c.931: con el conector «a» consumido por el patrón la aparición es
            // ancla por doctrina c.102/c.546 — EXCEPTO «a la» + genitivo de
            // contenido a continuación («a la primera hora de clase me quedé
            // dormido»): el artículo la vuelve sustantivo narrativo (protegida
            // vía H2 antes de que el patrón consumiera «a la»). Sin artículo
            // («a primera hora») o sin genitivo de contenido («avisar a la
            // última hora») sigue siendo ancla.
            // c.933: el artículo admite el plural («a las primeras horas de
            // clase me quedé dormido») — ya protegida PRE vía H2 (match sin
            // conector + artículo precedente); tras consumir «a las» en el
            // match el chequeo debe admitir «las» o la protección se pierde.
            // c.1016 (H4): SIN artículo, la aparición es CONTENIDO narrativo
            // cuando el predicado adyacente es pretérito inequívoco («a
            // primera hora llegó el cartero», «llegué a primera hora») — la
            // nota nacía como tarea vencida hoy 09:00/18:00 con el título
            // mutilado (doble daño P1, medido c.1008/c.1016).
            if (!Regex("""(?i)^(?:justo\s+)?a\s+las?\s""").containsMatchIn(match.value))
                return ordinalHoraOccurrenceIsPreteriteNarrative(text, match)
            // c.1019 (delta UNIÓN sobre H4 c.1016): CON artículo («a la/las»)
            // vale la MISMA evidencia inequívoca — el pretérito adyacente
            // ([ordinalHoraOccurrenceIsPreteriteNarrative], sufijo inmediato o
            // prefijo pretérito SOLO) — para declarar la aparición CONTENIDO
            // narrativo («a la primera hora vino el técnico», «llegué a la
            // primera hora»): nacía con fecha FALSA hoy 09:00/18:00 y título
            // mutilado (doble daño P1, medido c.1019 con sonda efímera sobre
            // la base c.1016: 4/4 candidatas con-artículo afectadas). Sin esa
            // evidencia sigue la doctrina ancla/genitivo vigente
            // byte-idéntica (guards y pines FUERA en el test c.1017).
            if (ordinalHoraOccurrenceIsPreteriteNarrative(text, match)) return true
            if (ordinalHoraCanonicalSuffix.containsMatchIn(match.value)) return false
            val suffixA = text.substring(match.range.last + 1)
            val genitiveA = ordinalHoraContentGenitive.find(suffixA) ?: return false
            return genitiveA.groupValues[1].lowercase() !in ordinalHoraAnchorGenitives
        }
        if (ordinalHoraCanonicalSuffix.containsMatchIn(match.value)) {
            // c.932 (H3): el genitivo canónico DENTRO del match («las primeras
            // horas de la mañana son las mejores») es CONTENIDO narrativo sólo
            // con evidencia inequívoca: determinante (artículo/demostrativo,
            // opcional «en») AL INICIO del texto — sin verbo precedente —
            // y predicado a continuación. El sujeto narrativo gobierna la
            // parte del día interior; sin predicado («las primeras horas de
            // la mañana») o con verbo/cláusula precedente («avisar las…»)
            // sigue la doctrina bivalente/ancla (c.931).
            // c.935: el determinante también es evidencia tras una cláusula de
            // OPINIÓN inequívoca («creo que las…», «para mí las…») — el nominal
            // es sujeto de la subordinada ([ordinalHoraNarrativeOpinionPrefix]).
            // c.944: y el ARTÍCULO INDEFINIDO al inicio («unas primeras horas
            // de la mañana son duras») es la misma evidencia de sujeto
            // narrativo (lateral medida FUERA en c.943 — doctrina simétrica;
            // con verbo precedente el prefijo no empieza en el indefinido y
            // la forma sigue ancla byte-idéntica). La supresión de la
            // parte-del-día gobernada (fecha y título) fluye de
            // [ordinalHoraNarrativeRanges].
            // c.947: y «en» + ARTÍCULO INDEFINIDO al inicio («en una primera
            // hora del día trabajé mejor») — el hueco entre c.942 («en la…»)
            // y c.944 («una…» sin «en»); misma evidencia de sujeto narrativo
            // (lateral medida FUERA en c.945, doctrina simétrica a c.944).
            val prefixH3 = text.substring(0, match.range.first)
            // c.958: también el prefijo «en blanco» (aparición al inicio del
            // texto, sin determinante) es evidencia para H3 CUANDO el predicado
            // abre con pretérito inequívoco ([weekdayPreteriteNarrativeSuffix]),
            // módulo un weekday genitivo DIRECTO opcional
            // ([ordinalHoraNarrativeWeekdayGenitive]) — lateral medida FUERA en
            // c.952…c.956 (sonda /tmp/probe957/PreProbe.kt: 6/6 capturas con
            // doble daño P1, 4/4 guards ancla correctos, 2/2 pines FUERA). Sin
            // pretérito el fragmento nominal con genitivo-ancla es bivalente y
            // sigue la doctrina ancla vigente (pin conservador). La supresión
            // de la parte-del-día gobernada y del weekday genitivo fluye de
            // [ordinalHoraNarrativeRanges]/[ordinalHoraNarrativeWeekdayRanges],
            // hermano de los flujos de c.932/c.936.
            val barePreterite = if (prefixH3.isBlank()) {
                val suffixH3 = text.substring(match.range.last + 1)
                val wg = ordinalHoraNarrativeWeekdayGenitive.find(suffixH3)
                val predSuffix = if (wg != null) suffixH3.substring(wg.range.last + 1) else suffixH3
                weekdayPreteriteNarrativeSuffix.containsMatchIn(predSuffix)
            } else false
            if (!ordinalHoraNarrativeDeterminer.containsMatchIn(prefixH3) &&
                !ordinalHoraNarrativeOpinionPrefix.containsMatchIn(prefixH3) &&
                !ordinalHoraNarrativeIndefinitePrefix.containsMatchIn(prefixH3) &&
                !ordinalHoraNarrativeEnIndefinitePrefix.containsMatchIn(prefixH3) &&
                !barePreterite
            ) return false
            if (text.substring(match.range.last + 1).isBlank()) return false
            return true
        }
        val prefix = text.substring(0, match.range.first)
        if (Regex("""(?i)\b(?:esa|esas|ese|esos|esta|estas|este|estos|aquella|aquellas|aquel|aquellos|dicha|dichas|dicho|dichos|tal|tales)(?:\s+mism[oa]s?)?\s+$""")
                .containsMatchIn(prefix)
        ) return true
        val articleBefore = Regex("""(?i)\b(?:la|las|el|los)(?:\s+mism[oa]s?)?\s+$""").containsMatchIn(prefix)
        // c.943: el artículo INDEFINIDO al inicio («una/un/unas») es la misma
        // evidencia de sujeto narrativo; hasta c.948 SÓLO se admitía con
        // weekday genitivo (directo o interior). c.951: también con genitivo
        // de contenido H2 («una primera hora de clase…») — ver la rama H2.
        // c.946: la misma restricción para «en» SIN artículo al inicio — sólo
        // weekday genitivo (directo o interior); H3-bare y weekday-bare quedan
        // fuera (genitivos-ancla bivalentes, pin conservador). c.956: el
        // prefijo «en blanco» (aparición al inicio del texto, módulo espacios)
        // es evidencia aditiva válida sólo para el genitivo de CONTENIDO (H2).
        val indefiniteBefore = ordinalHoraNarrativeIndefinitePrefix.containsMatchIn(prefix)
        val enPrefixBefore = ordinalHoraNarrativeEnPrefix.containsMatchIn(prefix)
        val enIndefiniteBefore = ordinalHoraNarrativeEnIndefinitePrefix.containsMatchIn(prefix)
        val bareBefore = prefix.isBlank()
        if (!articleBefore && !indefiniteBefore && !enPrefixBefore && !enIndefiniteBefore && !bareBefore) return false
        val suffix = text.substring(match.range.last + 1)
        val genitive = ordinalHoraContentGenitive.find(suffix) ?: return false
        val genWord = genitive.groupValues[1].lowercase()
        if (genWord !in ordinalHoraAnchorGenitives) {
            // H2 (genitivo de contenido): artículo definido (c.937); c.951:
            // también el artículo INDEFINIDO al inicio del texto («una primera
            // hora de clase fue genial») y «en» + indefinido al inicio («en
            // una primera hora de clase me quedé dormido») — la misma evidencia
            // de sujeto narrativo que c.943/c.948, ahora con genitivo de
            // contenido (lateral medida FUERA c.943…c.948). Con verbo/nombre
            // precedente («quiero una…», «avisar en una…») el prefijo no
            // empieza en el indefinido y la forma sigue ancla (byte-idéntica).
            // Como la H2 de c.937, no se exige predicado: el fragmento nominal
            // «una primera hora de clase» tampoco es ancla. c.952: también
            // «en» SIN artículo al inicio («en primera hora de clase me quedé
            // dormido») — lateral medida FUERA en c.951 (sonda
            // /tmp/probe952/PreProbe.kt: 7/7 candidatas con doble daño P1, 4/4
            // guards ancla correctos). c.956: y el prefijo «en blanco» (sin
            // determinante ni «en», aparición al inicio del texto: «primera
            // hora de clase me quedé dormido») — lateral medida FUERA en
            // c.952 (sonda /tmp/probe954/ProbePreFix.kt: 5/5 candidatas con
            // doble daño P1, 4/4 guards ancla correctos, 4/4 pines FUERA).
            // Laterales FUERA (pins byte-idénticos): H3 «en» sin determinante
            // («en primera hora de la mañana…») y weekday-bare («primera hora
            // del lunes…») — genitivos-ancla bivalentes. c.958: H3 con prefijo
            // en blanco + pretérito inequívoco ya ES contenido (rama H3).
            if (articleBefore || indefiniteBefore || enIndefiniteBefore || enPrefixBefore || bareBefore) return true
        }
        // c.939 (H1-artículo): artículo AL INICIO del texto + weekday genitivo
        // DIRECTO + predicado a continuación → sujeto narrativo («la primera
        // hora del lunes fue aburrida»). El weekday con modificador («del lunes
        // que viene»: el match de [weekdayPattern] se extiende más allá del
        // genitivo) sigue la doctrina ancla vigente, como en c.936/c.938. Sin
        // artículo al inicio («quiero/prefiero/avisar la…») o sin predicado el
        // fragmento es bivalente y sigue ancla (pins byte-idénticos). c.942:
        // la misma doctrina con «en» + artículo al inicio («en la primera
        // hora del lunes me quedé dormido»). c.943: y con artículo INDEFINIDO
        // al inicio («una primera hora del lunes fue rara»). c.946: y con
        // «en» SIN artículo al inicio («en primera hora del lunes me quedé
        // dormido»). c.948: y con «en» + artículo INDEFINIDO al inicio («en
        // una primera hora del lunes fue rara» — hueco entre c.943 y c.946).
        if (genWord in ordinalHoraNarrativeWeekdayWords &&
            (ordinalHoraNarrativeArticlePrefix.containsMatchIn(prefix) ||
                ordinalHoraNarrativeEnArticlePrefix.containsMatchIn(prefix) ||
                ordinalHoraNarrativeIndefinitePrefix.containsMatchIn(prefix) ||
                ordinalHoraNarrativeEnPrefix.containsMatchIn(prefix) ||
                ordinalHoraNarrativeEnIndefinitePrefix.containsMatchIn(prefix))
        ) {
            val afterWg = suffix.substring(genitive.range.last + 1)
            if (afterWg.isNotBlank()) {
                val wm = weekdayPattern.find(suffix)
                if (wm == null || wm.range.last <= genitive.range.last) return true
            }
        }
        // c.941 (H1-artículo/demostrativo + genitivo INTERIOR de parte del día
        // + weekday genitivo + predicado): «las primeras horas de la noche del
        // sábado fueron mágicas». El sufijo canónico de [primeraHoraPattern]
        // no admite noche/tarde, así el genitivo caía en ANCLA (fecha y título
        // falsos). Ver [ordinalHoraInteriorPartOfDayWeekdayRange].
        if (ordinalHoraInteriorPartOfDayWeekdayRange(text, match) != null) return true
        return false
    }

    /**
     * c.1016 (H4): ¿es esta aparición de ordinal de hora con conector «a» (sin
     * artículo) la marca temporal de una CADENA NARRATIVA en pretérito y no un
     * ancla de las 09:00/18:00? Sólo con evidencia gramatical inequívoca
     * ([preteriteNarrativeVerbAlternation], la lista cerrada de c.950: un
     * encargo real jamás abre ni se reduce a un pretérito):
     *  (H4-sufijo) el texto TRAS el match abre con pretérito («a primera hora
     *      llegó el cartero», «a última hora de la noche cerró la tienda» — el
     *      sufijo canónico «de la mañana/noche» lo consume el propio patrón,
     *      así que el pretérito queda inmediatamente después del match);
     *  (H4-prefijo) TODO el prefijo es un predicado pretérito SOLO («llegué a
     *      primera hora», «me desperté a primera hora», «ya salí a última
     *      hora») — ver [ordinalHoraPreteriteNarrativeLonePrefix].
     * FUERA a propósito (laterales medidas, pins byte-idénticos en
     * NaturalTaskParserOrdinalHoraPreteritoNarrativoTest): weekday genitivo
     * («a primera hora del lunes llegó…» — doctrina ancla vigente), pretérito
     * con complemento antes del ancla («me quedé dormido a primera hora»,
     * «sonó la alarma a primera hora») y formas ambiguas pretérito/presente
     * («salimos/comimos a primera hora», excluidas por doctrina c.950). El
     * artículo tras «a» («a la primera hora llegó…») quedó cubierto por el
     * delta c.1019 (misma evidencia en la rama con-artículo del guard).
     * Usado por [ordinalHoraOccurrenceIsContent], así que la resolución
     * (fecha), el borrado del título ([eraseOrdinalHoraToken]) y la
     * protección de la parte del día gobernada comparten el mismo predicado:
     * fecha y título nunca divergen (doctrina c.930/c.950).
     */
    private fun ordinalHoraOccurrenceIsPreteriteNarrative(text: String, match: MatchResult): Boolean {
        val suffix = text.substring(match.range.last + 1)
        if (weekdayPreteriteNarrativeSuffix.containsMatchIn(suffix)) return true
        val prefix = text.substring(0, match.range.first)
        // c.1048: la marca narrativa «ahora/ahorita» que ABRE la cadena declara
        // la aparición CONTENIDO («ahora llegó el cartero a primera hora»,
        // «ahorita me escribió a última hora») — MISMO guard
        // [narrativePreteritePrefix] que weekday (c.1041) y hora numérica
        // (c.1045): una verdad, tres superficies, nunca divergen. La rama «ya»
        // ya estaba cubierta por [ordinalHoraPreteriteNarrativeLonePrefix]
        // (abajo, byte-idéntica). Candado conservador c.1023 preservado: el
        // idiom «quedar con» es cita futura, jamás relato
        // («ya quedé con Ana a primera hora» sigue anclando hoy 09:00).
        if (narrativePreteritePrefix(prefix) && !ordinalHoraQuedarConArrangement.containsMatchIn(prefix)) return true
        if (ordinalHoraPreteriteNarrativeLonePrefix.matches(prefix)) return true
        // c.1023 (H5): el prefijo ABRE con pretérito inequívoco y sigue con un
        // complemento narrativo («me quedé dormido…», «sonó la alarma…»,
        // «fui al banco…», «hablé con María…»). Anti-compromiso-embebido: el
        // complemento no puede contener infinitivo ni «que» («avisé a Juan de
        // llamar al banco…» conserva su ancla real) y el idiom «quedar con»
        // («quedé con Ana a primera hora» = cita futura) nunca dispara.
        val head = ordinalHoraPreteriteNarrativePrefixHead.find(prefix) ?: return false
        if (ordinalHoraQuedarConArrangement.containsMatchIn(prefix)) return false
        val complement = prefix.substring(head.range.last + 1)
        return !ordinalHoraEmbeddedCommandToken.containsMatchIn(complement)
    }

    /**
     * c.930: borra del título las apariciones de ordinal de hora que son ANCLA
     * y conserva íntegras las que son CONTENIDO narrativo
     * ([ordinalHoraOccurrenceIsContent]). Opera por rangos (no replace global)
     * para no tocar las apariciones protegidas, como [eraseMananaDateToken].
     */
    private fun eraseOrdinalHoraToken(
        title: String,
        pattern: Regex,
        forceFirstOccurrenceAnchor: Boolean = false
    ): String {
        var result = title
        var isFirst = true
        var idx = 0
        while (true) {
            val m = pattern.find(result, idx) ?: return result
            // c.939: si la RESOLUCIÓN ancló la primera ocurrencia de este patrón
            // (match != null), se borra sin re-evaluar — el texto ya mutado
            // (p. ej. «de la noche» consumido) podría fingir un weekday genitivo
            // DIRECTO que en el original no lo era («las primeras horas de la
            // noche del sábado fueron mágicas»). Doctrina «nunca divergen».
            if (!forceFirstOccurrenceAnchor || !isFirst) {
                if (ordinalHoraOccurrenceIsContent(result, m)) {
                    isFirst = false
                    idx = m.range.last + 1
                    continue
                }
            }
            isFirst = false
            // c.965: la cadena «(en|para|por|de) la/las» que precede DIRECTAMENTE
            // a la ocurrencia ancla borrada es residuo puro — su único referente
            // era el ancla que se acaba de consumir («avisar la última hora» →
            // 'avisar', no 'avisar la'; «llegar en la última hora» → 'llegar').
            // Las ocurrencias de CONTENIDO se preservan antes (continue), así que
            // el artículo de contenido («la última hora del partido») está a salvo.
            val orphan = ordinalHoraOrphanArticlePattern
                .find(result.substring(0, m.range.first))
            val removeStart = orphan?.range?.first ?: m.range.first
            result = result.removeRange(removeStart, m.range.last + 1)
            idx = removeStart
        }
    }

    /**
     * c.965: artículo (con conector opcional) huérfano inmediatamente ANTES de una
     * ocurrencia ancla de ordinal de hora borrada por [eraseOrdinalHoraToken].
     * Exige límite de palabra real (`^` o espacio) para no tocar «la» pegada a un
     * pronombre («pagarla última hora» conserva «pagarla»).
     */
    private val ordinalHoraOrphanArticlePattern =
        Regex("""(?i)(?:^|\s)(?:(?:en|para|por|de)\s+)?las?\s+$""")

    /**
     * c.932: borra del título las apariciones de parte del día suelta y
     * conserva íntegra la GOBERNADA por un ordinal narrativo (H3): en «las
     * primeras horas de la mañana son las mejores» el «de la mañana» interior
     * pertenece al sujeto narrativo, no es ancla. Opera por rangos (no replace
     * global) para no tocar las apariciones protegidas, como
     * [eraseOrdinalHoraToken]/[eraseMananaDateToken].
     * c.954: conserva igualmente la parte del día de una cadena narrativa en
     * pretérito «hoy/ayer (en|por|…) la <parte del día> …llegó…», SÓLO cuando
     * [forceDayPreteriteNarrative] lo indica: la decisión se tomó en [parse]
     * sobre el texto original, como el flag c.950 de [eraseWeekdayToken].
     */
    private fun eraseStandalonePartOfDayToken(
        title: String,
        forceDayPreteriteNarrative: Boolean = false
    ): String {
        var result = title
        var idx = 0
        while (true) {
            val m = standalonePartOfDayPattern.find(result, idx) ?: return result
            // c.954 (remoto) + c.955 (narrativa hoy/ayer día-parte en
            // pretérito): ambas protecciones conmutan, como el guard ordinal.
            if (ordinalHoraNarrativeRanges(result).any { it.containsRange(m.range) } ||
                weekdayPreteriteNarrativeIntercalatedPartOfDayRanges(result).any { it.containsRange(m.range) } ||
                (forceDayPreteriteNarrative && dayPreteriteNarrativeOccurrence(result, m))
            ) {
                idx = m.range.last + 1
                continue
            }
            result = result.removeRange(m.range.first, m.range.last + 1)
            idx = m.range.first
        }
    }

    /**
     * c.954: borra del título los días relativos con calificador opcional
     * («de/del/desde hoy», «ayer», «pasado mañana»…) y conserva íntegro el
     * «hoy/ayer» que INTRODUCE una cadena narrativa en pretérito con parte del
     * día («hoy en la mañana llegó el paquete»), SÓLO cuando
     * [forceDayPreteriteNarrative] lo indica (decisión tomada en [parse] sobre
     * el texto original). Opera por rangos como [eraseWeekdayToken] para que
     * fecha y título nunca diverjan.
     */
    private fun eraseRelativeDayToken(
        title: String,
        forceDayPreteriteNarrative: Boolean = false,
        forceRangeGenitiveNarrative: Boolean = false
    ): String {
        var result = title
        var idx = 0
        while (true) {
            val m = relativeDayErasePattern.find(result, idx) ?: return result
            if ((forceDayPreteriteNarrative && dayPreteriteNarrativeGuard(result, m)) ||
                (forceRangeGenitiveNarrative &&
                    ayerRangeGenitiveRanges(result).any { it.containsRange(m.range) })
            ) {
                idx = m.range.last + 1
                continue
            }
            result = result.removeRange(m.range.first, m.range.last + 1)
            idx = m.range.first
        }
    }

    private val relativeDayErasePattern = Regex(
        """(?i)(?:\b(?:de|del|desde)\s+)?(?:antepasad[oa]\s+ma[nñ]ana\b|\bpasado\s+ma[nñ]ana\b|\bhoy\b|\banteayer\b|\bantier\b|\bayer\b)"""
    )

    // c.1075: genitivo de RANGO con día relativo PASADO en posición de
    // contenido: «desde/hasta/de + ayer/anteayer/antier» ABRIENDO el enunciado
    // («desde ayer no duermo bien», «hasta ayer trabajé en el proyecto») o
    // inmediatamente tras una CÓPULA («el informe es desde/de ayer»).
    // Evidencia gramatical inequívoca (doctrina c.927/c.930): un encargo real
    // jamás abre con un límite de rango pasado ni lo predica con cópula.
    // Conservador: «cita de ayer» (genitivo de posesión temporal sin cópula),
    // «trabajo desde hoy»/«estudio desde mañana» (día presente/futuro) y
    // «hasta ayer» NO inicial siguen anclando byte-idénticos.
    private val ayerRangeGenitivePattern = Regex(
        """(?i)(?:^\s*|(?<=\b(?:es|era|son|eran)\s))(?:desde|hasta|de)\s+(?:ayer|anteayer|antier)\b"""
    )

    private fun ayerRangeGenitiveRanges(text: String): List<IntRange> =
        ayerRangeGenitivePattern.findAll(text).map { it.range }.toList()

    // c.1075: día pasado relativo inmediatamente tras un «hasta» de rango (el
    // reescritor de plazos lo consulta con la cola que sigue al conector).
    private val ayerRangeGenitiveDayHead = Regex("""(?i)^(?:anteayer|antier|ayer)\b""")

    // c.954: para el borrado genérico de días relativos, ¿es esta aparición de
    // «hoy/ayer» la cabeza protegida de una cadena narrativa en pretérito con
    // parte del día?
    private fun dayPreteriteNarrativeGuard(text: String, match: MatchResult): Boolean {
        val mv = match.value.trim().lowercase()
        // c.957: «anteayer/antier» también protegidas (superset conservador).
        if (mv != "hoy" && mv != "ayer" && mv != "anteayer" && mv != "antier") return false
        val sm = standalonePartOfDayPattern.find(text, match.range.first) ?: return false
        return dayPreteriteNarrativeOccurrence(text, sm)
    }

    /**
     * c.936: borra del título las apariciones de weekday y conserva íntegro
     * el weekday genitivo de una cadena narrativa H3 protegida («las primeras
     * horas de la mañana del lunes son tranquilas»): pertenece al sujeto
     * narrativo, no es token de fecha. Opera por rangos (no replace global)
     * para no tocar las apariciones protegidas, como [eraseOrdinalHoraToken]/
     * [eraseStandalonePartOfDayToken].
     * c.950: conserva igualmente el weekday inicial de una cadena narrativa en
     * pretérito («el lunes llegó el paquete»), pero SÓLO cuando
     * [forcePreteriteNarrativeAnchor] lo indica: la decisión se tomó en
     * [parse] sobre el texto original; aquí el título ya pudo perder tokens
     * intermedios («el lunes en la mañana llegó…» → «el lunes llegó…»), y sin
     * el flag esa forma reducida sería indistinguible de la captura pura —
     * fecha y título divergirían (la fecha ancla, el título conservaría).
     */
    private fun eraseWeekdayToken(
        title: String,
        forcePrimeraHoraAnchor: Boolean = false,
        forceUltimaHoraAnchor: Boolean = false,
        forcePreteriteNarrativeAnchor: Boolean = false
    ): String {
        var result = title
        var idx = 0
        while (true) {
            val m = weekdayPattern.find(result, idx) ?: return result
            if (ordinalHoraNarrativeWeekdayRanges(result, forcePrimeraHoraAnchor, forceUltimaHoraAnchor)
                    .any { it.containsRange(m.range) } ||
                (forcePreteriteNarrativeAnchor && weekdayOccurrenceIsPreteriteNarrative(result, m))
            ) {
                idx = m.range.last + 1
                continue
            }
            result = result.removeRange(m.range.first, m.range.last + 1)
            idx = m.range.first
        }
    }

    /**
     * c.950: predicado en PRETÉRITO inequívoco tras un weekday — evidencia de
     * cadena narrativa («el lunes llegó el paquete» = algo que YA ocurrió, no
     * un compromiso). Lista cerrada de pretéritos perfectos simples y
     * copulativos pretéritos frecuentes (un comando jamás abre su predicado en
     * pretérito); admite adverbio «ya» y un pronombre reflexivo/ácito («me
     * quedé…», «se rompió…»). Se EXCLUYEN a propósito las formas ambiguas
     * pretérito/presente (1ª plural «salimos», «comimos»…) y los imperfectos
     * descriptivos/habituales («trabajaba»: también describe rutina), salvo el
     * copulativo «era/eran», inequívoco como arranque narrativo.
     * Cierre con lookahead de separador en lugar de \b: en java.util.regex \b
     * no trata las vocales con tilde como word-char (salvo flag explícito de
     * Pattern, que el flag embebido (?iu) no propaga), así que «llegó\b»
     * jamás casaría; el lookahead exige separador real y bloquea prefijos
     * («vio» ≠ «violento», «fue» ≠ «fuera»).
     */
    // c.1016: la alternación de pretéritos inequívocos se extrae a constante
    // compartida (mismo contenido, byte a byte) para reutilizarla en el guard
    // narrativo del ordinal de hora ([ordinalHoraOccurrenceIsPreteriteNarrative])
    // sin duplicar la lista ni arriesgar deriva entre copias.
    // c.1034: cobertura ampliada con las familias devolver/confirmar/mandar
    // (pretérito inequívoco; medida P1 con sonda /tmp/probe1034 — «ya devolvió
    // el libro» anclaba AHORA falso y mutilaba el título). Mismo
    // conservadurismo: sólo pretéritos, las formas ambiguas no se tocan.
    private val preteriteNarrativeVerbAlternation =
        "llegó|llegué|llegaste|llegaron|fue|fui|fuiste|fueron|era|eran|" +
            "estuvo|estuve|estuviste|estuvieron|vino|vine|viniste|vinieron|" +
            "pasó|pasé|pasaste|pasaron|ocurrió|ocurrieron|sucedió|sucedieron|" +
            "sonó|sonaste|sonaron|llamó|llamé|llamaste|llamaron|" +
            "escribió|escribí|escribiste|escribieron|compró|compré|compraste|compraron|" +
            "pagó|pagué|pagaste|pagaron|envió|envié|enviaste|enviaron|" +
            "devolvió|devolví|devolviste|devolvieron|confirmó|confirmé|confirmaste|confirmaron|" +
            "mandó|mandé|mandaste|mandaron|" +
            "recibió|recibí|recibiste|recibieron|volvió|volví|volviste|volvieron|" +
            "regresó|regresé|regresaste|regresaron|terminó|terminé|terminaste|terminaron|" +
            "empezó|empecé|empezaste|empezaron|comenzó|comencé|comenzaste|comenzaron|" +
            "acabó|acabé|acabaste|acabaron|cerró|cerré|cerraste|cerraron|" +
            "abrió|abrí|abriste|abrieron|salió|salí|saliste|salieron|" +
            "entró|entré|entraste|entraron|subió|subí|subiste|subieron|" +
            "bajó|bajé|bajaste|bajaron|ganó|gané|ganaste|ganaron|" +
            "perdió|perdí|perdiste|perdieron|llovió|nevó|tembló|" +
            "nació|nacieron|murió|murieron|dijo|dije|dijiste|dijeron|" +
            "hizo|hice|hiciste|hicieron|trajo|traje|trajiste|trajeron|" +
            "puso|puse|pusiste|pusieron|vio|vi|viste|vieron|" +
            "dio|di|diste|dieron|supo|supe|supiste|supieron|" +
            "pudo|pude|pudiste|pudieron|tuvo|tuve|tuviste|tuvieron|" +
            "duró|duraste|duraron|quedó|quedé|quedaste|quedaron|" +
            "funcionó|funcionaron|falló|fallaron|rompió|rompieron|" +
            "apareció|aparecieron|desapareció|desaparecieron|" +
            "comí|comió|comiste|comieron|cené|cenó|cenaste|cenaron|" +
            "dormí|durmió|dormiste|durmieron|desperté|despertó|despertaste|despertaron|" +
            "levanté|levantó|levantaste|levantaron|trabajé|trabajó|trabajaste|trabajaron|" +
            "estudié|estudió|estudiaste|estudiaron|hablé|habló|hablaste|hablaron|" +
            "encontré|encontró|encontraste|encontraron|dejé|dejó|dejaste|dejaron|" +
            "tomé|tomó|tomaste|tomaron|leí|leyó|leyeron|sentí|sintió|sintieron"

    private val weekdayPreteriteNarrativeSuffix = Regex(
        """(?i)^\s*,?\s*(?:ya\s+)?(?:(?:me|te|se|nos|os|lo|la|los|las|le|les)\s+){0,2}(?:$preteriteNarrativeVerbAlternation)(?=\s|$|[,.;:!?)])"""
    )

    /**
     * c.1027: ¿el predicado que sigue a un «ya» suelto abre con pretérito
     * inequívoco («ya sonó la alarma», «ya me tomé la pastilla»)? Entonces el
     * «ya» NO es inmediatez de comando (c.112) sino relato de un hecho
     * cumplido: el ancla `now` se suprime (la anécdota no es un compromiso
     * que vence hoy) y el título conserva el «ya» (contenido del usuario).
     * Misma lista cerrada de c.950 (un encargo real jamás abre su predicado
     * en pretérito) y mismo conservadurismo: las formas ambiguas
     * pretérito/presente («ya salimos») siguen ancla, y sólo el «ya» suelto
     * se evalúa («ya mismo» queda intacto para comandos). c.1029: la cadena
     * proclítica admite hasta DOS clíticos (indirecto + directo: «ya me lo
     * pagó», «ya se lo dije») — la cadena estándar del español no pasa de
     * dos en proclisis. c.1035: la lista proclítica se completa con los
     * acusativos plurales («los», «las» — «ya se las di» anclaba AHORA y
     * mutilaba el título, medida sonda `/tmp/probe1035/Probe.kt`) y el MISMO
     * grupo se extiende a las otras tres superficies narrativas de la familia
     * (weekday + ordinal lone/head): «ya se lo dije a última hora» anclaba
     * 18:00 falso con título mutilado (misma sonda, O1/O2).
     */
    // c.1035: la guard admite UNA cláusula adverbial acotada entre comas
    // («ya, a primera hora, sonó la alarma» — pin FUERA c.1027 resuelto):
    // `[^,.;:!?]{1,60},` salta el adverbial sin tragar puntuación ni desbordar.
    // Mismo conservadurismo: el predicado sigue exigiendo pretérito inequívoco.
    private val yaPreteriteNarrativeSuffix = Regex(
        """(?i)^\s*,?\s*(?:[^,.;:!?]{1,60},\s*)?(?:(?:me|te|se|nos|os|lo|la|los|las|le|les)\s+){0,2}(?:$preteriteNarrativeVerbAlternation)(?=\s|$|[,.;:!?)])"""
    )

    /**
     * c.1016 (H4-prefijo): el prefijo COMPLETO antes de un ordinal de hora con
     * conector «a» es un predicado pretérito SOLO («llegué a primera hora»,
     * «me desperté a primera hora», «ya salí a última hora»): un encargo real
     * jamás se reduce a un pretérito antes del ancla (doctrina c.950), así que
     * la marca temporal pertenece a la narración. Conservador a propósito:
     * pretérito + complemento («me quedé dormido…», «sonó la alarma…») NO casa
     * (lateral registrada FUERA en el test del ciclo, pin byte-idéntico).
     */
    private val ordinalHoraPreteriteNarrativeLonePrefix = Regex(
        """(?i)^\s*(?:ya\s+)?(?:(?:me|te|se|nos|os|lo|la|los|las|le|les)\s+){0,2}(?:$preteriteNarrativeVerbAlternation)\s*[,.;:!?]?$"""
    )

    /**
     * c.1023 (H5): cabeza del prefijo narrativo con complemento — la misma
     * forma de arranque del predicado SOLO ([ordinalHoraPreteriteNarrativeLonePrefix])
     * pero SIN exigir fin: lo que siga se valida aparte
     * ([ordinalHoraEmbeddedCommandToken], [ordinalHoraQuedarConArrangement]).
     */
    private val ordinalHoraPreteriteNarrativePrefixHead = Regex(
        """(?i)^\s*(?:ya\s+)?(?:(?:me|te|se|nos|os|lo|la|los|las|le|les)\s+){0,2}(?:$preteriteNarrativeVerbAlternation)(?=\s|$)"""
    )

    /**
     * c.1023 (H5): compromiso embebido dentro del complemento del prefijo —
     * cualquier infinitivo («avisé a Juan de llamar…», «quedé en llamar…») o
     * el subordinador «que» («me pidió que llamara…») bloquea el disparo:
     * la ambigüedad se resuelve a favor de NO suprimir un compromiso real
     * (doctrina c.950). Coste conocido y aceptado: falsos infinitivos por
     * terminación («ayer», «lugar») dejan narrativas ancladas (lateral FUERA
     * pineada en el test del ciclo).
     */
    private val ordinalHoraEmbeddedCommandToken = Regex(
        """(?i)(?:^|\s)(?:que|[a-záéíóúüñ]+(?:ar|er|ir|arse|erse|irse))(?=\s|$|[,.;:!?])"""
    )

    /**
     * c.1023 (H5): «quedar con» + persona es CITA futura («quedé con Ana a
     * primera hora» = nos vemos a primera hora), no narrativa. Incluye el
     * clítico a propósito («me quedé con Ana…» también se ancla: bivalente,
     * doctrina c.950).
     * c.1048: tolera marca narrativa inicial «ya/ahora/ahorita» — el idiom
     * sigue siendo cita futura («ahora quedé con Ana a primera hora» ancla
     * hoy 09:00; medida RUN_LOG c.1048 POST).
     */
    private val ordinalHoraQuedarConArrangement = Regex(
        """(?i)^\s*(?:(?:ya|ahora|ahorita)\s+)?(?:(?:me|te|se|nos|os|lo|la|le|les)\s+)?qued(?:é|ó|aste|aron)(?=\s+con(?:\s|$))"""
    )

    /**
     * c.954 admitida entre el weekday y el
     * predicado en pretérito de una narrativa c.950 («el lunes en la mañana
     * llegó el paquete», «el martes por la tarde llegó la noticia»).
     * Conservadora: sólo «en la X»/«por la X» (los conectores «a la X» y
     * «de la X» quedan como ancla, medidos en los pins de c.954).
     */
    private val weekdayPreteriteNarrativeIntercalatedPartOfDay = Regex(
        """(?i)^\s*,?\s*(?:en|por)\s+la\s+(?:ma[nñ]ana|tarde|noche|madrugada)\s+"""
    )

    /**
     * c.950: ¿es esta aparición de weekday el inicio de una CADENA NARRATIVA en
     * pretérito («el lunes llegó el paquete») y no un ancla de fecha? Sólo con
     * evidencia gramatical inequívoca:
     *  (N1) artículo «el»/demostrativo «este» al frente o —desde c.1006—
     *       weekday DESNUDO con pretérito inmediato («lunes llegó el
     *       paquete», lateral c.949); los genitivos «del/de <weekday>»
     *       —«la reunión del lunes»— quedan FUERA: su weekday modifica al
     *       sustantivo y la doctrina vigente los ancla;
     *  (N2) SIN modificador de dirección futura («que viene», «próximo»,
     *       «siguiente», «posterior»): si el usuario pidió futuro explícito,
     *       el modificador gana aunque el enunciado sea contradictorio;
     *  (N3) el predicado abre con pretérito inequívoco
     *       ([weekdayPreteriteNarrativeSuffix]): un encargo real jamás
     *       empieza en pretérito («el lunes llega/tengo/hay…» siguen ancla);
     *  (N3 c.1041) o la narrativa pretérito ya viene cerrada en el PREFIJO
     *       (weekday al final: «ya me lo pagó el lunes»), con los candados
     *       «quedar con» e infinitivo/«que» de c.1023 aplicados al resto.
     * Antes, «el lunes llegó el paquete» se agendaba al PRÓXIMO lunes y el
     * título perdía el weekday (doble daño: compromiso futuro falso +
     * contenido mutilado). FUERA a propósito (laterales medidas): weekday +
     * parte del día intercalada («el lunes en la mañana llegó…»), «ayer/hoy/
     * anoche/esta mañana» + pretérito, y formas verbales ambiguas. Usado por
     * [parse] (fecha) y [eraseWeekdayToken] (título) para que nunca diverjan.
     */
    private fun weekdayOccurrenceIsPreteriteNarrative(text: String, match: MatchResult): Boolean {
        val mv = match.value.trimStart().lowercase()
        val withArticle = mv.startsWith("el ") || mv.startsWith("este ")
        // c.1006 (lateral c.949): el weekday DESNUDO (sin artículo: «lunes
        // llegó el paquete») también abre narrativa cuando el pretérito es
        // INMEDIATO — la misma clase de doble daño de c.950 (compromiso
        // futuro falso + título mutilado). Los genitivos «del/de <weekday>»
        // siguen FUERA (modifican al sustantivo, doctrina ancla vigente) y
        // la parte del día intercalada con weekday desnudo («lunes en la
        // mañana llegó…») sigue anclada (pin c.954 byte-idéntico: esa
        // extensión conservadora sólo aplica a «el/este»).
        if (!withArticle && (mv.startsWith("del ") || mv.startsWith("de "))) return false
        if (mv.contains("que viene") || mv.contains("próxim") || mv.contains("proxim") ||
            mv.contains("siguiente") || mv.contains("posterior")
        ) return false
        // c.1041 (lateral ABIERTA tras c.1039): weekday AL FINAL de una
        // cadena narrativa inequívoca que ABRE el enunciado («ya me lo
        // pagó el lunes», «ahora me lo dijo el martes»): el weekday
        // cierra el relato de un hecho cumplido; jamás es ancla.
        val prefix = text.substring(0, match.range.first)
        // c.1049: MISMO candado «quedar con» del ordinal (c.1048) — «ya
        // quedé con Ana el lunes» es CITA futura, jamás relato. Medida
        // PRE: 4/4 suprimidas injustamente (RUN_LOG c.1049).
        if (narrativePreteritePrefix(prefix) && !ordinalHoraQuedarConArrangement.containsMatchIn(prefix)) return true
        val suffix = text.substring(match.range.last + 1)
        if (weekdayPreteriteNarrativeSuffix.containsMatchIn(suffix)) return true
        // c.1041 (UNIÓN con la rama [narrativePreteritePrefix — ex
        // `weekdayPreteriteNarrativePrefix`, renombrado c.1045] del
        // hermano — colisión convergente sobre la MISMA lateral): weekday AL
        // FINAL con el predicado pretérito en el PREFIJO SIN marca «ya/ahora/
        // ahorita» («llegué el miércoles», «pagué la luz el viernes»): la
        // narrativa viene cerrada antes del weekday, así que su fecha es
        // relato, no ancla. Re-uso sin duplicación (lección c.1016): cabeza
        // compartida [ordinalHoraPreteriteNarrativePrefixHead] con los candados
        // conservadores c.1023 — «quedar con» en prefijo O sufijo ([ordinalHoraQuedarConArrangement])
        // sigue ancla («quedé con Ana el lunes», «quedé el lunes con Ana») y
        // un infinitivo/«que» en el resto bloquea el disparo
        // ([ordinalHoraEmbeddedCommandToken]; pin FUERA «salí a comprar…»).
        // Mismo conservadurismo c.950: ambiguas pretérito/presente no disparan.
        val head = ordinalHoraPreteriteNarrativePrefixHead.find(prefix)
        if (head != null) {
            val rest = (prefix.substring(head.range.last + 1) + " " + suffix).trimStart()
            val chain = head.value.trim() + " " + rest
            if (ordinalHoraQuedarConArrangement.containsMatchIn(chain)) return false
            if (ordinalHoraEmbeddedCommandToken.containsMatchIn(rest)) return false
            return true
        }
        // c.954 entre el weekday y el
        // pretérito («el lunes en la mañana llegó el paquete») — extensión de
        // la lateral que c.950 midió y pinó como FUERA, ahora resuelta: la
        // cadena completa sigue siendo narrativa. Sólo se admiten las formas
        // «en la X»/«por la X» (conservador: «a la X»/«de la X» siguen como
        // ancla, medidas en los pins de c.954). El weekday con modificador
        // (N2) sigue excluido y, desde c.1006, el desnudo sólo vale con
        // pretérito inmediato (arriba): sin artículo no hay intercalada.
        if (!withArticle) return false
        val intercalated = weekdayPreteriteNarrativeIntercalatedPartOfDay.find(suffix) ?: return false
        return weekdayPreteriteNarrativeSuffix.containsMatchIn(suffix.substring(intercalated.range.last + 1))
    }

    /**
     * c.1041: ¿el PREFIJO (todo lo que precede al ancla candidata) abre con
     * una cadena narrativa inequívoca «ya/ahora/ahorita <clíticos>
     * <pretérito>»? Conservador: sólo cuando la marca narrativa abre el
     * enunciado (un prefijo «por favor ya…» sigue anclando, igual que antes
     * de c.1041); los genitivos y la dirección futura ya quedaron fuera en
     * [weekdayOccurrenceIsPreteriteNarrative] (N1/N2) antes de llamar.
     * c.1045: reutilizado por [timeMatchIsPreteriteNarrative] para la hora
     * numérica «a las H» AL FINAL de la misma cadena («ya me llamó a las
     * 8») — renombrado de `weekdayPreteriteNarrativePrefix` a nombre neutro.
     * c.1048: reutilizado por [ordinalHoraOccurrenceIsPreteriteNarrative]
     * para el ordinal de hora «a primera/última hora» AL FINAL de la misma
     * cadena («ahora llegó el cartero a primera hora»).
     */
    private fun narrativePreteritePrefix(prefix: String): Boolean {
        val s = prefix.trim().lowercase()
        val sub = when {
            s.startsWith("ya ") -> s.substring(3)
            s.startsWith("ahora ") -> s.substring(6)
            s.startsWith("ahorita ") -> s.substring(8)
            else -> return false
        }
        return yaPreteriteNarrativeSuffix.containsMatchIn(sub)
    }

    /**
     * c.954: rangos de las partes del día que son el segmento INTERCALADO de
     * una narrativa en pretérito c.950 («el lunes en la mañana llegó el
     * paquete»): la parte del día pertenece al enunciado narrativo, así que
     * no debe resolver fecha/hora ni borrarse del título. Requiere: un
     * weekday que ya es narrativa c.950 (con la extensión N3') y que entre el
     * match del weekday y el de la parte del día sólo haya separadores. Se
     * usa en la resolución (fecha/hora), en [eraseStandalonePartOfDayToken]
     * y en [mananaOccurrenceIsContent] (G5) para que nunca diverjan.
     */
    private fun weekdayPreteriteNarrativeIntercalatedPartOfDayRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var idx = 0
        while (true) {
            val sm = standalonePartOfDayPattern.find(text, idx) ?: break
            idx = sm.range.last + 1
            var wIdx = 0
            while (true) {
                val wm = weekdayPattern.find(text, wIdx) ?: break
                wIdx = wm.range.last + 1
                if (wm.range.last >= sm.range.first) continue
                if (text.substring(wm.range.last + 1, sm.range.first).trim(',', ' ', ';').isNotEmpty()) continue
                if (weekdayOccurrenceIsPreteriteNarrative(text, wm)) {
                    ranges.add(sm.range)
                    break
                }
            }
        }
        return ranges
    }

    /**
     * c.954: ¿es esta aparición de parte del día suelta ([standalonePartOfDayPattern])
     * la cabeza de una CADENA NARRATIVA en pretérito («hoy en la mañana llegó el
     * paquete», «ayer por la tarde cerró el banco») y no un ancla de hora? Sólo
     * con evidencia gramatical inequívoca y simétrica a
     * [weekdayOccurrenceIsPreteriteNarrative]:
     *  (N1) el prefijo del match es EXACTAMENTE «hoy» o «ayer» (la cadena abre
     *       con el día; «llamar hoy en la mañana» o cualquier prefijo de
     *       comando quedan FUERA: conservan su ancla);
     *  (N2) SIN modificador de dirección futura en el propio match («la mañana
     *       siguiente») ni calificador de día explícito («la tarde de hoy»):
     *       ambas convierten la frase en ancla real y ganan;
     *  (N3) el predicado abre con pretérito inequívoco
     *       ([weekdayPreteriteNarrativeSuffix]): un encargo real jamás empieza
     *       en pretérito (presente/comando/fragmento siguen ancla).
     * Antes, la hora canónica fabricaba un compromiso (hoy a las 09:00 siendo
     * ya mediodía = falso vencido) y el título quedaba mutilado (doble daño).
     * Formas ambiguas (1ª plural «salimos») quedan FUERA como en c.950.
     * Usada por [parse] (fecha), [eraseStandalonePartOfDayToken] y
     * [eraseRelativeDayToken] (título) para que nunca diverjan.
     */
    private fun dayPreteriteNarrativeOccurrence(text: String, match: MatchResult): Boolean {
        val mv = match.value.lowercase()
        if (mv.contains("siguiente")) return false
        if (Regex("""(?i)\s+de\s+(?:hoy|ma[nñ]ana|ayer|anteayer|antier)\b""")
                .containsMatchIn(mv)) return false
        // c.957: «anteayer/antier» también encabeza la cadena narrativa (superset
        // conservador medido FUERA del remoto c.955: nacía con fecha falsa y
        // título mutilado — doble daño 2/2). Mismo dominio «en/por la X».
        val prefix = text.substring(0, match.range.first).trim().lowercase()
        if (prefix != "hoy" && prefix != "ayer" && prefix != "anteayer" && prefix != "antier") return false
        val suffix = text.substring(match.range.last + 1)
        return weekdayPreteriteNarrativeSuffix.containsMatchIn(suffix)
    }

    private fun Int.toDayOfWeekOrNull(): DayOfWeek? =
        if (this in 1..7) DayOfWeek.of(this) else null

    /**
     * Convierte un grupo capturado (dígitos o número escrito en español, 1-99) a Long.
     *
     * Admite la forma compuesta estándar del español "decena y unidad"
     * ("treinta y cinco" = 35, "cuarenta y cinco" = 45) y "veinte y unidad"
     * ("veinte y dos" = 22), además de las palabras únicas (veintidós…noventa).
     * Antes solo se mapeaban 1-21 y 30, así que "cuarenta y cinco minutos" no se
     * resolvía y la tarea quedaba SIN vencimiento.
     */
    private fun parseWrittenNumber(raw: String): Long? {
        raw.toLongOrNull()?.let { return it }
        val s = raw.lowercase().trim()
        if (s == "un par de" || s == "unos" || s == "unas") return 2L
        wordToNumber[s]?.let { return it }
        // Forma compuesta "X y Y" (31-99, o 21-29 como "veinte y dos").
        val yIdx = s.indexOf(" y ")
        if (yIdx > 0) {
            val left = s.substring(0, yIdx).trim()
            val right = s.substring(yIdx + 3).trim()
            val l = wordToNumber[left]
            val r = wordToNumber[right]
            // left debe ser una decena redonda (20,30,…,90) y right una unidad (1-9).
            if (l != null && r != null && l in ROUND_TENS && r in 1L..9L) return l + r
        }
        return null
    }

    private val ROUND_TENS = setOf(20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L)

    /**
     * Extiende [tokenRange] hacia atrás para incluir un conector ("de", "durante", "por")
     * inmediatamente anterior al token, de modo que su borrado no deje residuo en el título.
     * Si no hay conector, devuelve el rango del token sin cambios. Opera por rango, no por
     * replace global, para no corromper ocurrencias legítimas del mismo texto en el título.
     */
    private fun connectorRange(working: String, tokenRange: IntRange, tokenStart: Int): IntRange {
        val prefix = working.substring(0, tokenStart)
        val m = Regex("(?i)\\b(?:de|durante|por)\\s+$").find(prefix)
        return if (m != null) IntRange(m.range.first, tokenRange.last) else tokenRange
    }

    private val wordToNumber = mapOf(
        "un" to 1L, "una" to 1L, "uno" to 1L, "primero" to 1L,
        "dos" to 2L, "tres" to 3L, "cuatro" to 4L, "cinco" to 5L,
        "seis" to 6L, "siete" to 7L, "ocho" to 8L, "nueve" to 9L,
        "diez" to 10L, "once" to 11L, "doce" to 12L, "trece" to 13L,
        "catorce" to 14L, "quince" to 15L,
        "dieciséis" to 16L, "dieciseis" to 16L, "diecisiete" to 17L,
        "dieciocho" to 18L, "diecinueve" to 19L,
        "veinte" to 20L, "veintiuno" to 21L, "veintiuna" to 21L,
        "veintidós" to 22L, "veintidos" to 22L,
        "veintitrés" to 23L, "veintitres" to 23L,
        "veinticuatro" to 24L, "veinticinco" to 25L,
        "veintiséis" to 26L, "veintiseis" to 26L, "veintisiete" to 27L,
        "veintiocho" to 28L, "veintinueve" to 29L,
        "treinta" to 30L, "cuarenta" to 40L, "cincuenta" to 50L,
        "sesenta" to 60L, "setenta" to 70L, "ochenta" to 80L, "noventa" to 90L
    )
}

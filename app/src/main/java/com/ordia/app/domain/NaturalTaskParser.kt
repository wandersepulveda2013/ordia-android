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
    /** "este/el/próximo fin de semana" o "fin de semana" suelto → próximo sábado.
     *  Acepta también "finales de semana" (plural análogo a "finales de mes"): señala un
     *  fin de semana concreto, no un hábito. Acepta el apócope coloquial "finde"
     *  ("este finde"/"el finde"/"próximo finde"/"finde" suelto) como fecha única, NO como
     *  recurrencia: el singular señala UN fin de semana concreto, mientras que el
     *  determinante plural/cada ("los findes"/"cada finde") es hábito y se resuelve
     *  aparte en parseRecurrence. Antes "este finde" caía por error a la recurrencia
     *  semanal (WEEKLY sábado+domingo para siempre) cuando el usuario pedía una sola
     *  fecha. OJO: "fines de semana" (f-i-n-e-s) y "los findes"/"cada finde" son
     *  recurrencia semanal y se resuelven aparte en parseRecurrence, no aquí. */
    private val weekendPattern = Regex("""(?i)\b(?<!cada\s)(?<!los\s)(?:a\s+)?(?:este\s+|el\s+|pr[oó]ximo\s+)?(?:fin|finales)\s+de\s+semana\b|\b(?:a\s+)?(?:este\s+|el\s+|pr[oó]ximo\s+)?(?<!cada\s)(?<!los\s)finde\b""")
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
    // "el último viernes del mes" / "el primer lunes de agosto" / "el tercer viernes del mes que
    // viene": ocurrencia ORDINAL de ese weekday en un mes (no la semana pasada, que es lo que
    // resuelve previousWeekdayReversed al casar "el último viernes"). Más específico: exige el
    // calificador "del mes"/"del mes que viene/próximo/entrante"/"de <mes>" tras el día. Se
    // procesa ANTES que previousWeekdayReversed para consumir la frase entera (así el calificador
    // no queda como residuo en el título y el día no se captura dos veces). Sin este patrón,
    // "el último viernes del mes" caía en previousWeekdayReversed → viernes ANTERIOR (fecha
    // equivocada) + "del mes" como basura en el título. Ordinales: último = última ocurrencia;
    // primer/segundo/tercer/cuarto = N-ésima desde el inicio (todo mes tiene ≥4 de cada weekday).
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
        """(?i)(?<!\p{L})(?:el\s+)?(último|ultimo|primer|primero|segundo|tercer|tercero|cuarto)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+del?\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?(?:mes(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+)?)?((?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|set|sept|oct|nov|dic))?(?:\s+del?\s+(\d{2,4}))?|(?=\s+(?:cada\s+mes|todos\s+los\s+meses|mensual(?:mente)?|mensualidades?)\b))\b"""
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
        """(?i)(?<!\p{L})(?:cada\s+mes|todos\s+los\s+meses|mensual(?:mente)?|bimestral(?:mente)?|trimestral(?:mente)?|cuatrimestral(?:mente)?|semestral(?:mente)?|cada\s+(?:\d{1,3}|$writtenNumberGroup)\s*meses?|todos\s+los\s+(?:\d{1,3}|$writtenNumberGroup)\s*meses?)\s+((?:el\s+)?(último|ultimo|primer|primero|segundo|tercer|tercero|cuarto)\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))\b"""
    )

    /**
     * Fecha relativa VAGA de futuro cotidiano: "en/dentro de/de aquí a/de acá a un rato",
     * "en/dentro de un momento", "al rato", "pasado un rato", "enseguida"/"en seguida" (adverbio de inmediatez, sin "un rato"). Forma coloquial frecuente que
     * antes no casaba ningún patrón → dueAt=null y la tarea quedaba sin recordatorio (olvidada,
     * P1). Simétrica futura de "hace un rato" (pasado, −3 h). "un rato"/"un momento" son
     * intencionalmente imprecisos; se resuelve a +1 h (heurística honesta, no IA): agenda el
     * recordatorio para que la tarea no desaparezca. Se procesa ANTES que [relativePattern]
     * para robar la frase completa y dejar el título limpio.
     */
    private val vagueRelativePattern = Regex(
        """(?i)\b(?:(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(?:un\s+rato|un\s+momento)|al\s+rato|pasado\s+un\s+rato|en\s*seguida|enseguida)\b"""
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
     */
    private val relativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|unos|unas|\d{1,3}(?:[.,]\d+)?|$writtenNumberGroup)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)(?:\s+y\s+(media|medio))?\b"""
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
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\b"""
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
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\s+y\s+cuarto\b"""
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
     */
    private val agoPattern = Regex(
        """(?i)\bhace\s+(\d{1,3}|un\s+rato|poco|$writtenNumberGroup)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|mes(?:es)?|a[nñ]os?)?\b"""
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
     */
    private val lastPeriodPattern = Regex(
        """(?i)\b(?:la\s+semana|el\s+mes|el\s+a[n\u00f1]o)\s+(?:pasad[oa]|anterior)\b|\bsemana\s+(?:pasada|anterior)\b|\bmes\s+(?:pasado|anterior)\b|\ba[n\u00f1]o\s+(?:pasado|anterior)\b"""
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
        """(?i)(?<!\p{L})(?:en\s+(?:los|el|las|la)?\s+)?(?:a\s+)?(?:el|la|los|las)?\s*(?:semanas?|mes(?:es)?|a[nñ]os?|trimestres?|bimestres?|semestres?|quincenas?)\s+(?:que\s+viene[n]?|que\s+entra[n]?|pr[oó]ximos?|pr[oó]ximas?|entrante[s]?)\b|(?<!\p{L})(?:en\s+(?:los|el|las|la)?\s+)?(?:a\s+)?(?:el|la|los|las)?\s*(?:pr[oó]ximos?|pr[oó]ximas?)\s+(?:semanas?|mes(?:es)?|a[nñ]os?|trimestres?|bimestres?|semestres?|quincenas?)\b|(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b"""
    )
    /**
     * "el 15 del mes que viene" / "el 15 del próximo mes" / "el 15 del mes próximo":
     * día N del mes SIGUIENTE. Es un compromiso mensual anclado a un día concreto
     * (vencimiento, cobro, cita). Antes, nextPeriodPattern capturaba "mes que viene"
     * y descartaba el día explícito (→ +30d desde hoy, fecha errónea) y dejaba "el 15
     * del" como residuo en el título. Se procesa ANTES que nextPeriodPattern para
     * consumir la frase completa (día + cualificador) y evitar ambos fallos. Se
     * resuelve como día (epoch medianoche) para combinarse con hora explícita
     * ("el 15 del mes que viene a las 10"). El día imposible (p. ej. 31 de feb)
     * se ajusta al último día válido del mes objetivo.
     */
    private val nextMonthDayPattern = Regex(
        """(?i)\b(?:el\s+(?:d[ií]a\s+)?|d[ií]a\s+)(\d{1,2})\s+(?:del?\s+)?(?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b"""
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
        """(?i)\b(?:el\s+)?(?:mes\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\s+el\s+(?:d[ií]a\s+)?(\d{1,2})\b"""
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
    private val endOfMonthPattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?(?:fin(?:al|ales|es)?|cierre|corte|[uú]ltim[oa]\s+d[ií]a)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    private val midOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
    private val startOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|primeros?|inicios?)\s+(?:de\s+|del\s+)(?:(?:cada\s+(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?)|(?:todos\s+los\s+))?(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:es)?(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?(?:\s+del?\s+($monthNameGroup))?(?:\s+del?\s+(\d{2,4}))?\b""")
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
     */
    private val monthBoundaryNamePattern = Regex("""(?i)(?<!\p{L})(?:a\s+|al\s+)?(mediados?|mitad|principios?|comienzos?|primeros?|inicios?|final(?:es)?|fin|cierre|corte)\s+(?:de\s+|del\s+)([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
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
     */
    private val endOfYearPattern = Regex("""(?i)(?<!\p{L})(?:a\s+)?(?:fin(?:ales|es)?|cierre|corte)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?a[nñ]o(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
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
    private val thisMonthPattern = Regex("""(?i)(?<!\d\s)(?<!de\s)(?<!del\s)este\s+mes\b""")
    private val thisYearPattern = Regex("""(?i)(?<!\d\s)(?<!de\s)(?<!del\s)este\s+a[nñ]o\b""")
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
     */
    private val thisWeekPattern = Regex("""(?i)\b(?:esta\s+semana(?:\s+que\s+viene)?|(?:a\s+)?fin(?:es)?\s+de\s+la\s+semana)\b""")
    /**
     * "principios de semana" / "a principios de semana": plazo blando de "a inicios de
     * la semana" (el lunes). Frases cotidianas ("lo termino a principios de semana") que
     * antes caían a dueAt=null (tarea olvidada) o, con hora explícita, a HOY por error.
     *
     * Resuelve al lunes más cercano en HOY o futuro (ISO, semana lunes→domingo): si hoy
     * es lunes, hoy; si es martes-domingo, el lunes de la semana siguiente. Como plazo
     * blando nunca se fecha en pasado. Se detecta y borra ANTES del período próximo para
     * que "semana" no active "semana que viene".
     */
    private val startOfWeekPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|inicios?)\s+(?:de\s+la\s+|de\s+|del\s+)semana\b""")
    /**
     * "mediados de semana" / "a mediados de semana" → miércoles más cercano en HOY o
     * futuro. Análogo a "principios de semana" (lunes) y "mediados de mes" (día 15).
     * Se detecta y borra ANTES del período próximo para que "semana" no active
     * "semana que viene".
     */
    private val midOfWeekPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+la\s+|de\s+|del\s+)semana\b""")
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(?:d[ií]a\s+)?(\d{1,2}|$writtenNumberGroup|primero)\s+de\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
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
    private val dayOfMonthPattern = Regex("""(?i)\b(?:el\s+(?:d[ií]a\s+)?|d[ií]a\s+)(\d{1,2})(?![/-])(?:\s+del?\s+(?:mes\s+actual|presente\s+mes|este\s+(?:mismo\s+)?mes|mes))?\b(?!\s*del?\s+[a-záéíóúüñ])""")
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
    // Lookahead (?![/-]) tras el dígito: rechaza "el 25/12" para que NO se ancle al
    // día-suelto del mes (25 de agosto) y caiga a numericDatePattern (25/12 → diciembre).
    // Sin esto, dayOfMonthPattern ("el 25") casaba ANTES que numericDatePattern → la
    // fecha numérica completa se perdía y el vencimiento caía en el mes equivocado.

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
     * Nombres de hora escritos en español (dos..veintiuno), ordenados de mayor a menor
     * longitud para que la alternación regex no se quede con un prefijo ("tres" dentro de
     * "trece"). Se excluye "un/una/uno" (la hora 1 se dice "a la una", con otro conector).
     * Reutilizado por [timePatterns] (a las N) y [standaloneHourPartOfDayPattern] (N de la
     * tarde) para que las horas escritas —cotidianas en español— se resuelvan en vez de
     * caer como residuo del título o agendarse a la hora canónica de la parte del día.
     */
    private const val WRITTEN_HOUR_ALT =
        "veintiuno|veinte|diecinueve|dieciocho|diecisiete|diecis[eé]is|quince|catorce|trece|doce|once|diez|nueve|ocho|siete|seis|cinco|cuatro|tres|dos"

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
        Regex("""(?i)\ba\s+la\s+(una)(?:(?::|h)([0-5]\d))?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\b"""),
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
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)([0-5]\d))?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(?:horas?|hs|h))?(?:\s+($CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS))?\b"""),
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
        Regex("""(?i)\b([01]?\d|2[0-4]):([0-5]\d)(?:\s*(?:horas?|hs|h))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?(?:\s*(?:horas?|hs|h))?\b"""),
        // "H[:MM] am/pm [h/hs/horas]" con sufijo de unidad en cualquier posición: "9am",
        // "9:30pm", "3:30h pm", "3 pm h". Requiere meridiem (hora 1-12). El sufijo se
        // absorbe antes/después del meridiem para que no quede como residuo en el título.
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?:(?::|h)([0-5]\d))?(?:\s*(?:horas?|hs|h))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)(?:\s*(?:horas?|hs|h))?\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+|a\s+)?mediod[ií]a(?:\s+($CLOCK_FRACTION_Y))?\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+|a\s+)?medianoche(?:\s+($CLOCK_FRACTION_Y))?\b""")
    )
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
     * hora; es un adverbio temporal puro, sin uso de tema.
     */
    private val approximateTimePatterns = listOf(
        // "a eso de" es un adverbio temporal puro (sin uso de tema/cantidad), así que
        // admite hora en punto sin meridiem ("a eso de las 5"): es el caso más común.
        Regex("""(?i)\ba\s+eso\s+de\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d)?|la\s+una(?::[0-5]\d)?)"""),
        // "hacia/cerca de/alrededor de/sobre" admiten usos de tema ("sobre las ventas") y
        // de cantidad ("sobre las 3 cajas"), así que exigen evidencia de reloj INMEDIATA
        // tras la hora (minutos `:MM`, meridiem, parte del día u "horas") para no agendar
        // una cuenta como cita. La hora en punto sin meridiem queda fuera por ambigua.
        Regex("""(?i)\b(?:hacia|cerca\s+de|alrededor\s+de)\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d|\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s*(?:horas?|hs))|la\s+una(?:\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)))"""),
        Regex("""(?i)\bsobre\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::[0-5]\d|\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s*(?:horas?|hs))|la\s+una(?:\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)))""")
    )

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
    private val paraTimeIntroPattern =
        Regex("""(?i)\bpara\s+(?=las\s+(?:[01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?:(?::|h)[0-5]\d|\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS)|\s*(?:horas?|hs|h)\b)|la\s+una(?:(?::|h)[0-5]\d|\s+(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)|\s+(?:$CLOCK_FRACTION_Y|$CLOCK_FRACTION_MENOS)))""")
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
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        // "N min/hora antes": debe aceptar las mismas abreviaturas que la duración
        // (min, hora) para que "30 min antes" sea recordatorio y no caiga como duración.
        Regex("""(?i)\b($writtenAmountPattern)\s*(minutos?|min|horas?|hora|d[ií]as?|d[ií]a)\s+antes\b"""),
        // Fracciones sin dígitos como recordatorio: "media hora antes",
        // "(un) cuarto de hora antes", "recuérdame media hora de anticipación".
        // Requiere contexto de recordatorio ("antes"/"anticipación"/verbo) para no
        // robar una duración real ("reunión media hora" sin "antes" sigue siendo
        // duración). Antes "media hora antes" era robado por la duración (30 min
        // falsos) y el recordatorio quedaba en null → la cita se olvidaba.
        Regex("""(?i)\b(?:recuérdame|av[ií]same|notif[ií]came|recordatorio)\s*(?:con\s+)?(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\s*(?:de\s+anticipaci[oó]n|antes|de\s+adelanto|adelanto|de)?\b"""),
        Regex("""(?i)\b(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\s+antes\b""")
    )
    /**
     * Verbo de recordatorio sin cantidad explícita: "recuérdame llamar a mamá mañana",
     * "avísame pagar la luz el viernes", "no dejes que olvide...". El usuario pide un
     * recordatorio pero no dice cuánto antes; antes el verbo quedaba como residuo en el
     * título y NO se programaba ningún recordatorio (la cita se olvidaba aunque el
     * usuario lo hubiera pedido expresamente). Aquí se detecta la intención para:
     * (a) limpiar el verbo del título y (b) aplicar un offset de respaldo (30 min)
     * cuando hay fecha límite — sin dueAt no se puede programar reminderAt, así que no
     * se falsifica nada. Simétrico con `UniversalCaptureEngine.reminderSignal`.
     */
    private val bareReminderVerbPattern =
        Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|notif[ií]came|recordatorio|no\s+dejes\s+que\s+olvide|no\s+(?:se\s+te\s+|te\s+|me\s+|le\s+)?olvides?(?:\s+de\b)?(?:\s+que\b)?|acu[eé]rdate(?:\s+de\b)?|recuerda)\b""")
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
     */
    private val fractionalDurationPattern =
        Regex("""(?i)\b(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\b""")

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
     */
    private val compoundFractionalDurationPattern =
        Regex("""(?i)\b($writtenAmountPattern)\s*horas?\s+y\s+(tres\s+cuartos|dos\s+cuartos|media|un\s+cuarto|cuarto)\b""")

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

    /** "urgente" como palabra inicial, para detección de prioridad sin prefijo. */
    private val leadingUrgentPattern = Regex("""(?i)^urgente\b""")

    /**
     * "urgente"/"importante" como palabra FINAL (con puntuación opcional): el usuario añade la
     * prioridad como sufijo en texto libre ("Llamar mamá urgente"). Más honesto que casar a
     * mitad de frase (evita "no es urgente el documento" a mitad, que no sería palabra final).
     */
    private val trailingPriorityPattern = Regex("""(?i)\b(urgente|importante)\b\s*[.!?]?$""")

    /** Formas copulativas negadas que neutralizan un "urgente"/"importante" final real. */
    private val negatedPriorityPattern = Regex("""(?i)\bno\s+(?:es|era|fue|parece|ser[áa])\s+(?:lo\s+)?(?:urgente|importante)\b\s*[.!?]?$""")

    /** Partes del día: "esta mañana/tarde/noche". Implican fecha=hoy + hora canónica. */
    private val partOfDayPattern = Regex("""(?i)\besta\s+(ma[nñ]ana|tarde|noche)\b""")
    private val partOfDayTimes = mapOf(
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0),
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0)
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
    private val standalonePartOfDayPattern = Regex("""(?i)\b(?:(?:a\s+la|de\s+la|por\s+la|en\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)|de\s+(tarde|noche|madrugada))(?:\s+de\s+(?:hoy|ma[nñ]ana|ayer|anteayer|antier))?\b""")
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
        Regex("""(?i)\b(?:antepasad[oa]\s+ma[nñ]ana|pasado\s+ma[nñ]ana|ma[nñ]ana|hoy|anteayer|antier|ayer)\s+(tarde|noche|madrugada)\b""")
    private val compactDayPartOfDayTimes = mapOf(
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0)
    )

    /**
     * "a primera hora" (opcionalmente "de la mañana/madrugada"): inicio de jornada ~09:00.
     * Frase natural muy común; antes dejaba residuo en el título y no se interpretaba.
     * Como es una hora canónica de respaldo (no un reloj explícito), no fuerza contexto PM.
     */
    private val primeraHoraPattern =
        Regex("""(?i)\b(?:a\s+)?primera\s+horas?(?:\s+de\s+la\s+(?:ma[nñ]ana|manana|madrugada))?\b""")
    private val primeraHoraTime = LocalTime.of(9, 0)

    /**
     * "a última hora" (opcionalmente "de la mañana/tarde/noche/madrugada"): fin de
     * jornada ~18:00. Simétrica de "a primera hora". Antes no se interpretaba como hora
     * canónica: caía al default 09:00 (agenda errónea) y "a última hora" quedaba como
     * residuo en el título. Como es hora de respaldo, no fuerza contexto PM; si hay una
     * parte del día explícita ("de la tarde"), ésta tiene prioridad en la resolución y el
     * patrón solo limpia "a última hora" (la parte del día la limpia su propio patrón).
     */
    private val ultimaHoraPattern =
        Regex("""(?i)(?<![a-záéíóúñ])(?:a\s+)?[uú]ltima\s+horas?(?:\s+de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?\b""")
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
        Regex("""(?i)al\s+final\s+(?:del\s+d[ií]a|de\s+la\s+jornada|de\s+los\s+d[ií]as)\b""")
    private val alFinalDelDiaTime = LocalTime.of(18, 0)

    /**
     * "al amanecer"/"al alba"/"al despuntar el día": salida del sol, forma cotidiana de
     * "muy temprano" (antes ~06:00). Antes no se interpretaba como hora canónica: la tarea
     * quedaba SIN `dueAt` (olvidada, invisible en What Now/planificador, sin recordatorio) y
     * la frase quedaba como residuo en el título. Distinta de "madrugada" (04:00, franja
     * nocturna) y de "a primera hora" (09:00, inicio de jornada): el amanecer es la primera
     * luz, intermedia. Exige el conector "al " para no colisionar con "hoy amanece lloviendo"
     * (verbo) ni con "un amanecer hermoso" (sustantivo poético sin valor de agenda). Hora de
     * respaldo: si hay una parte del día/hora explícita, ésta tiene prioridad y el patrón
     * solo limpia "al amanecer".
     */
    private val amanecerPattern =
        Regex("""(?i)al\s+(?:amanecer|alba|despuntar\s+(?:el|la|de\s+la|del)\s+(?:alba|d[ií]a)|clarear|aclarar)\b""")
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
     * es la canónica vespertina establecida, sin falsa precisión. Exige el conector "al " para
     * no colisionar con el verbo ("atardece lloviendo") ni con el sustantivo suelto ("un
     * atardecer hermoso"). Como las demás horas canónicas, es hora de respaldo: si hay una hora
     * explícita, ésta gana y el patrón solo limpia "al atardecer".
     */
    private val atardecerPattern =
        Regex("""(?i)al\s+(?:atardecer|anochecer|ocaso|ponerse\s+(?:el\s+sol|del\s+sol))\b""")
    private val atardecerTime = LocalTime.of(18, 0)

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
     * "y <verbo>" no fraccionaria ("Cena 9 de la noche y hablar" → 21:00 + "Cena y hablar"):
     * "hablar" no casa CLOCK_FRACTION_Y.
     */
    private val standaloneHourPartOfDayPattern =
        Regex("""(?i)(?<![:\d])(\d{1,2}|$WRITTEN_HOUR_ALT)(?:(?::|h)([0-5]\d))?(?:\s+($CLOCK_FRACTION_Y))?\s+de\s+la\s+(tarde|noche|madrugada|ma[nñ]ana|manana)(?!\s+de\s+(?!hoy\b|ma[nñ]ana\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b)[a-záéíóúüñ])(?:\s+($CLOCK_FRACTION_Y))?\b""")

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
        working = working.replace(Regex("""(?i)\bma[nñ]ana\s+siguientes?\b"""), "mañana")

        // Ordinales numéricos: "1ro"/"2do"/"3er"/"1º"… seguidos de " de " se normalizan a
        // su dígito base para que los patrones de fecha (que exigen \d seguido de espacio)
        // los reconozcan. Solo en contexto de fecha (" de ") para no tocar contenido.
        working = ordinalSuffixPattern.replace(working) { m -> m.groupValues[1] + m.groupValues[2] }

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
        working = approximateTimePatterns.fold(working) { acc, p -> p.replace(acc, "a ") }

        // Introductor de hora directo "para las/la" → "a las/la" (simétrico de los marcadores
        // aproximados de arriba, pero con hora exacta). Reutiliza TODO el flujo de hora
        // explícita. Véase [paraTimeIntroPattern]: el lookahead exige evidencia de reloj para
        // no agendar destinatarios/cantidades ("para las 9 personas") como cita.
        working = paraTimeIntroPattern.replace(working, "a ")

        val lower = working.lowercase()
        val trailingPriorityWord = trailingPriorityPattern.find(lower)
            ?.takeIf { !negatedPriorityPattern.containsMatchIn(lower) }
            ?.groupValues?.get(1)
        val priority = when {
            "!urgente" in lower || "#urgente" in lower -> TaskPriority.URGENT
            "!alta" in lower || "#alta" in lower -> TaskPriority.HIGH
            "!baja" in lower || "#baja" in lower -> TaskPriority.LOW
            // "urgente" como palabra inicial (ej. "urgente enviar documento mañana")
            // sin prefijo. No se detecta a mitad de frase para evitar falsos positivos
            // como "no es urgente".
            leadingUrgentPattern.containsMatchIn(lower) -> TaskPriority.URGENT
            // "urgente"/"importante" como palabra FINAL: sufijo de prioridad en texto libre
            // ("Llamar mamá urgente"), salvo negación ("no es urgente").
            trailingPriorityWord == "urgente" -> TaskPriority.URGENT
            trailingPriorityWord == "importante" -> TaskPriority.HIGH
            else -> TaskPriority.NORMAL
        }
        working = working.replace(Regex("""(?i)(?:!|#)(urgente|alta|baja)\b"""), " ")
            .replace(leadingUrgentPattern, " ")
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
                val isFraction = amountStr == "media hora" || amountStr.contains("cuarto")
                // Fracciones: media hora = 30 min, cuarto de hora = 15 min.
                val amount = when {
                    amountStr == "media hora" -> 30L
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
        reminderPatterns.forEach { pattern ->
            val ranges = pattern.findAll(working).map { it.range }.toList()
            for (r in ranges.sortedByDescending { it.first }) {
                working = working.replaceRange(r, " ")
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
        // El lookahead restringe a marcadores temporales reales para preservar "hasta" como
        // límite de acción ("trabajar hasta terminar", "leer hasta la página 50"): allí no
        // hay marcador → no se toca. "final" NO es marcador (sí "fin de"): el lookahead
        // exige "fin(?:es)?\s+de", no "fin" suelto.
        working = working
            .replace(Regex("""(?i)\bhasta\s+(?=(?:las\s+\d|la\s+(?:una|\d)))"""), "a ")
            .replace(
                Regex(
                    """(?i)\bhasta\s+(?=(?:el|la|los|las)\s+(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|primer|primero|segundo|tercer|tercero|cuarto|[uú]ltim[oa]?|semana|mes(?:es)?|a[ñn]os?|\d{1,2}\b)|fin(?:es)?\s+de\b|ma[nñ]ana\b|hoy\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b|dentro\s+de\b|en\s+(?:\d|un|una|unos|unas))""",
                ),
                " ",
            )
            // Conector de plazo "antes de/del" + fecha/hora: simétrico a "hasta"/c.134. La
            // fecha subyacente se resuelve bien, pero el conector sobrevivía como residuo en
            // el título ("enviar antes", "llamar las") porque el patrón de fecha consumía la
            // fecha antes del borrado tardío de "antes del" (que además exige "de" y no casa
            // con el "antes" huérfano). Se procesa aquí, ANTES que los patrones de fecha.
            //  · HORA: "antes de las 5 de la tarde" → "a las 5 de la tarde" (timePatterns
            //    exige "a"). Se exige meridio/parte del día tras la hora para NO tocar la
            //    forma ambigua "antes de las 5" (5am/5pm): esa queda sin resolver, igual que
            //    antes (sin regresión), en vez de fijar un 05:00 pasado y engañoso.
            //  · FECHA: "antes del viernes"/"antes de mañana" → se borra "antes del?/de "
            //    dejando el día (weekdayPattern admite weekday suelto). Se EXCLUYE \d (día del
            //    mes): "antes del 30" lo resuelve beforeDeadlineDayPattern y "antes del 15 de
            //    agosto" monthNameDate (ambos ya limpios); tocarlos aquí los rompería.
            .replace(
                Regex(
                    """(?i)\bantes\s+de\s+(?=(?:las\s+\d{1,2}|la\s+una)\b\s*(?:a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a))""",
                ),
                "a ",
            )
            .replace(
                Regex(
                    """(?i)\bantes\s+del?\s+(?=(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[nñ]ana\b|hoy\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b))""",
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
        val nowMatch = nowPattern.find(working)
        val nowDueAt = nowMatch?.let { now }
        nowMatch?.let { working = working.replaceRange(it.range, " ") }

        // "Más tarde"/"más rato"/"después" (adverbio suelto, sin "de/del/de la" detrás)
        // → +3 h: aproxima "más tarde" a "esta tarde". Se procesa tras now/vague para no
        // robarles sus frases y consume la frase para dejar el título limpio.
        val laterRelativeMatch = laterRelativePattern.find(working)
        val laterRelativeDueAt = laterRelativeMatch?.let { now + 3 * 60 * 60_000L }
        laterRelativeMatch?.let { working = working.replaceRange(it.range, " ") }

        // Fecha relativa "en/dentro de N minutos/horas/días" (N = dígitos o palabra).
        val relativeMatch = relativePattern.find(working)
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
            .replace(Regex("""(?i)\bde\s+aqu[íi]\s+al\b|\bde\s+ac[aá]\s+al\b"""), "el")
            .replace(Regex("""(?i)\bde\s+aqu[íi]\s+a\b|\bde\s+ac[aá]\s+a\b"""), " ")
        // El "fin de semana" se detecta y se borra ANTES del período próximo para que
        // "fin de semana que viene" no active por error el patrón "semana que viene"
        // (que dejaría el residuo «fin de» en el título). El match se conserva para la
        // resolución de fecha posterior (weekendMatch != null).
        val weekendEarlyMatch = weekendPattern.find(working)
        weekendEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        // "la semana/el mes/el año pasado": período anterior. Se detecta y borra ANTES
        // que previousWeekdayPattern, que de otro modo capturaría "mes"/"semana" como
        // si fuera un día de semana ("el mes pasado" -> grupo1="mes", no es día ->
        // sin fecha y la frase ya borrada -> dueAt=null). Así se captura como período
        // (resta 1 semana/mes/año) y se combina con hora explícita.
        val lastPeriodMatch = lastPeriodPattern.find(working)
        val lastPeriodDueAt = lastPeriodMatch?.let { m ->
            val text = m.value.lowercase()
            val days = when {
                "semana" in text -> 7L
                "mes" in text -> 30L
                "año" in text -> 365L
                else -> 7L
            }
            now - days * 24 * 60 * 60_000L
        }
        lastPeriodMatch?.let { working = working.replaceRange(it.range, " ") }
        // "el último viernes del mes" / "el primer lunes de agosto" / "el tercer viernes del mes
        // que viene": weekday ORDINAL del mes. Se detecta y borra ANTES que
        // previousWeekdayReversedPattern para que el weekday no se capture como "último viernes"
        // (viernes anterior) y el calificador "del mes"/"de agosto" no quede como residuo.
        val lastWeekdayOfMonthMatch = lastWeekdayOfMonthPattern.find(working)
        // Cadencia PRECEDENTE ("cada mes el primer lunes"): si el patrón directo no casó,
        // se intenta el de cadencia-antes. Ambos son excluyentes por posición. Se captura el
        // ordinal+weekday para anclar la recurrencia mensual; se borra SÓLO "el primer lunes"
        // (grupo 1 del patrón precedente) preservando "cada mes"/"mensual" para que
        // parseRecurrence emita MONTHLY.
        val precedingCadenceOrdinalMatch =
            if (lastWeekdayOfMonthMatch == null) precedingCadenceOrdinalPattern.find(working) else null
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
                monthName = lastWeekdayOfMonthMatch.groupValues[3].takeIf { it.isNotBlank() },
                yearStr = lastWeekdayOfMonthMatch.groupValues[4].takeIf { it.isNotBlank() }
            )
            precedingCadenceOrdinalMatch != null -> OrdinalMonthlyCapture(
                ordinalWord = precedingCadenceOrdinalMatch.groupValues[2],
                weekdayWord = precedingCadenceOrdinalMatch.groupValues[3],
                isNext = false,
                monthName = null,
                yearStr = null
            )
            else -> null
        }
        if (lastWeekdayOfMonthMatch != null) {
            working = working.replaceRange(lastWeekdayOfMonthMatch.range, " ")
        } else if (precedingCadenceOrdinalMatch != null) {
            val g = precedingCadenceOrdinalMatch.groups[1]!!.range
            working = working.replaceRange(g, " ")
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
        val endOfMonthEarlyMatch = endOfMonthPattern.find(working)
        val midOfMonthEarlyMatch = midOfMonthPattern.find(working)
        val startOfMonthEarlyMatch = startOfMonthPattern.find(working)
        // Límite mensual ganador (fin > mediados > principios) y su "tipo": sirve para
        // detectar el prefijo "cada" (recurrencia) y extender su borrado en un solo paso.
        val boundaryWinner: MatchResult? = endOfMonthEarlyMatch ?: midOfMonthEarlyMatch ?: startOfMonthEarlyMatch
        val boundaryKind: String? = when {
            endOfMonthEarlyMatch != null -> "end"
            midOfMonthEarlyMatch != null -> "mid"
            startOfMonthEarlyMatch != null -> "start"
            else -> null
        }
        // Resolución del límite mensual. Si el match trae un mes EXPLÍCITO (grupo 1,
        // p. ej. "fin de mes de octubre") se resuelve a ese mes con
        // `parseMonthBoundaryName` (mismo criterio y roll anual que "finales de
        // octubre"); si no, se usa el mes en curso/que viene (`monthBaseForBoundary`).
        fun boundaryDueAt(match: MatchResult, kind: String): Long? {
            val namedMonth = months[match.groupValues[1].lowercase()]
            val qualifier = when (kind) {
                "end" -> "finales"
                "mid" -> "mediados"
                else -> "principios"
            }
            if (namedMonth != null) {
                return parseMonthBoundaryName(base.toLocalDate(), qualifier, namedMonth, match.groupValues[2])
                    ?.let { DateRules.toEpochMillis(it, LocalTime.of(9, 0), zone) }
            }
            val baseMonth = monthBaseForBoundary(base.toLocalDate(), match.value)
            return when (kind) {
                "end" -> {
                    val lastDay = baseMonth.withDayOfMonth(baseMonth.lengthOfMonth())
                    DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
                }
                "mid" -> DateRules.toEpochMillis(baseMonth.withDayOfMonth(15), LocalTime.of(9, 0), zone)
                else -> DateRules.toEpochMillis(baseMonth.withDayOfMonth(1), LocalTime.of(9, 0), zone)
            }
        }
        val monthBoundaryDueAt = when (boundaryKind) {
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
                working = working.replaceRange(cadaPrefix.range.first..boundaryWinner.range.last, " ")
                if (hasNamedMonth) null
                else when (boundaryKind) {
                    "end" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastDay = true)
                    else -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList())
                }
            } else if (cadaInBoundaryMatch) {
                // El "cada"/"todos los" va dentro del match (tras "de/del"): se borra sólo
                // el rango del match (que ya incluye la palabra de cadencia) — no hay prefijo
                // externo que extender. Misma promoción que la rama del prefijo.
                working = working.replaceRange(boundaryWinner.range, " ")
                if (hasNamedMonth) null
                else when (boundaryKind) {
                    "end" -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList(), monthlyLastDay = true)
                    else -> RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), emptyList())
                }
            } else {
                endOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
                midOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
                startOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
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
            monthBoundaryNameEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
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
        endOfYearEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        midOfYearEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        startOfYearEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

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

        // "esta semana" / "esta semana que viene": fin de la semana actual (próximo
        // domingo, ISO lunes→domingo). Se borra ANTES del período próximo para que
        // "semana" no active "semana que viene" y para limpiar "esta semana que viene".
        val thisWeekEarlyMatch = thisWeekPattern.find(working)
        val thisWeekDueAt = thisWeekEarlyMatch?.let {
            val sunday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            DateRules.toEpochMillis(sunday, LocalTime.of(9, 0), zone)
        }
        thisWeekEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

        // "principios de semana": el lunes más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        val startOfWeekEarlyMatch = startOfWeekPattern.find(working)
        val startOfWeekDueAt = startOfWeekEarlyMatch?.let {
            val monday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            DateRules.toEpochMillis(monday, LocalTime.of(9, 0), zone)
        }
        startOfWeekEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

        // "mediados de semana": el miércoles más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        val midOfWeekEarlyMatch = midOfWeekPattern.find(working)
        val midOfWeekDueAt = midOfWeekEarlyMatch?.let {
            val wednesday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            DateRules.toEpochMillis(wednesday, LocalTime.of(9, 0), zone)
        }
        midOfWeekEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

        // "el 15 del mes que viene": día N del mes siguiente. Se procesa ANTES que
        // nextPeriodPattern para consumir la frase completa (día + "mes que viene")
        // y evitar que éste la robe como +30d genérico (fecha errónea) dejando
        // residuo "el N del" en el título.
        val nextMonthDayMatch = nextMonthDayPattern.find(working)
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
        val nextPeriodDueAt = nextPeriodMatch?.let { m ->
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
        nextPeriodMatch?.let { working = working.replaceRange(it.range, " ") }

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
        quincenaMatch?.let { working = working.replaceRange(it.range, " ") }

        // La fecha relativa (relativePattern) tiene prioridad; luego los límites de mes
        // ("fin de mes"/"mediados de mes"); "esta semana"; "principios/mediados de semana";
        // la quincena; el período próximo es el respaldo final. Todos son días (no
        // min/hora) para combinarse con una hora explícita.
        // Fechas pasadas (ago/lastPeriod) tienen prioridad: son explícitas y no
        // deben sobrescribirse por una fecha futura ambigua. La hora explícita se
        // aplica sobre la fecha pasada (tarea vencida con hora).
        val effectiveRelativeDueAt =
            agoDueAt ?: lastPeriodDueAt ?: relativeDueAt ?: vagueRelativeDueAt ?: nowDueAt ?:
            laterRelativeDueAt ?: fractionalAndQuarterRelativeDueAt ?: fractionalRelativeDueAt ?:
            compoundFractionalRelativeDueAt ?: multiQuarterRelativeDueAt ?: monthBoundaryDueAt ?:
            monthBoundaryNameDueAt ?: yearBoundaryDueAt ?:
            thisMonthDueAt ?: thisYearDueAt ?:
            thisWeekDueAt ?: startOfWeekDueAt ?: midOfWeekDueAt ?: quincenaDueAt ?:
            nextMonthDayDueAt ?: nextMonthDayReverseDueAt ?: nextMonthDayShortDueAt ?:
            nextMonthDayShortReverseDueAt ?:
            nextWeekWeekdayReverseDueAt ?: nextWeekWeekdayForwardDueAt ?: nextPeriodDueAt
        val relativeIsDays = (agoMatch != null || lastPeriodMatch != null ||
            relativeMatch != null || fractionalRelativeMatch != null ||
            fractionalAndQuarterRelativeMatch != null ||
            compoundFractionalRelativeMatch != null || multiQuarterRelativeMatch != null ||
            monthBoundaryDueAt != null || monthBoundaryNameDueAt != null || yearBoundaryDueAt != null ||
            thisMonthEarlyMatch != null || thisYearEarlyMatch != null ||
            thisWeekEarlyMatch != null || startOfWeekEarlyMatch != null || midOfWeekEarlyMatch != null ||
            quincenaMatch != null || nextMonthDayMatch != null || nextMonthDayReverseMatch != null ||
            nextMonthDayShortMatch != null || nextMonthDayShortReverseMatch != null ||
            nextWeekWeekdayReverseMatch != null || nextWeekWeekdayForwardMatch != null || nextPeriodMatch != null) &&
            (fractionalRelativeMatch == null) &&
            (fractionalAndQuarterRelativeMatch == null) &&
            (compoundFractionalRelativeMatch == null) &&
            (multiQuarterRelativeMatch == null) &&
            (relativeMatch?.let { m ->
                val unit = m.groupValues[2].lowercase()
                !unit.startsWith("min") && !unit.startsWith("hora")
            } ?: true)

        // Repetición: se procesa antes que la fecha para que "cada viernes" no se lea como fecha suelta.
        // Recurrencia mensual + ocurrencia ordinal de día de la semana ("el primer lunes de
        // cada mes"): se captura el (ordinal, weekday) del match para que el motor ancle cada
        // ciclo al N-ésimo/último día de la semana en vez del día del mes (c.215: sin esto
        // "primer lunes de cada mes" derivaba al día 7 de cada mes y la 2ª cita se desplazaba).
        // Sólo aplica a MONTHLY: WEEKLY usa `days` (lista de días) y la 1ª ocurrencia ordinal
        // ya resolvió la fecha de `dueAt`.
        val recurrence = parseRecurrence(working, now).let { r ->
            val withOrdinal = if (r.frequency != RecurrenceFrequency.MONTHLY || ordinalMonthly == null) r
            else {
                val ordWord = ordinalMonthly.ordinalWord.lowercase()
                val ordinal = when (ordWord) {
                    "último", "ultimo" -> -1
                    "primer", "primero" -> 1
                    "segundo" -> 2
                    "tercer", "tercero" -> 3
                    "cuarto" -> 4
                    else -> null
                }
                val weekday = ordinalMonthly.weekdayWord.toDayOfWeekOrNull()
                if (ordinal != null && weekday != null) r.copy(monthlyOrdinalWeekday = ordinal to weekday.value) else r
            }
            // "cada fin/mediados/principios de mes" (c.257): si no quedó otra recurrencia
            // explícita, el límite mensual se promueve a recurrencia MONTHLY anclada.
            if (withOrdinal.frequency == RecurrenceFrequency.NONE && cadaBoundaryRecurrence != null) cadaBoundaryRecurrence
            else withOrdinal
        }
        recurrence.phraseRanges.sortedByDescending { it.first }.forEach { range ->
            working = working.substring(0, range.first) + " " + working.substring(range.last + 1)
        }

        val weekdayMatch = weekdayPattern.find(working)
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
        // "antes del 30": día del mes suelto como plazo. Se resuelve ANTES que
        // dayOfMonthDate (que exige "el"/"día") para que el día suelto no caiga al vacío.
        val beforeDeadlineDayMatch = beforeDeadlineDayPattern.find(working)
        val beforeDeadlineDayDate = beforeDeadlineDayMatch?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                nextMonthlyDate(base.toLocalDate(), day)
            }
        }
        val partOfDayMatch = partOfDayPattern.find(working)
        val partOfDayTime = partOfDayMatch?.let { partOfDayTimes[it.groupValues[1].lowercase()] }
        val standalonePartOfDayMatch = standalonePartOfDayPattern.find(working)
        val standalonePartOfDayKey = standalonePartOfDayMatch?.let {
            (it.groupValues[1].ifBlank { it.groupValues[2] }).lowercase().ifEmpty { null }
        }
        val standalonePartOfDayTime = standalonePartOfDayKey?.let { standalonePartOfDayTimes[it] }
        val compactDayPartOfDayMatch = compactDayPartOfDayPattern.find(working)
        val compactDayPartOfDayKey = compactDayPartOfDayMatch?.groupValues?.get(1)?.lowercase()
        val compactDayPartOfDayTime = compactDayPartOfDayKey?.let { compactDayPartOfDayTimes[it] }
        val primeraHoraMatch = primeraHoraPattern.find(working)
        val ultimaHoraMatch = ultimaHoraPattern.find(working)
        val alFinalDelDiaMatch = alFinalDelDiaPattern.find(working)
        val amanecerMatch = amanecerPattern.find(working)
        val atardecerMatch = atardecerPattern.find(working)
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
            atardecerMatch != null
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
            Regex("""(?i)\banteayer\b|\bantier\b""").containsMatchIn(working) -> base.toLocalDate().minusDays(2)
            Regex("""(?i)\bayer\b""").containsMatchIn(working) -> base.toLocalDate().minusDays(1)
            // "antepasado mañana" = dentro de 3 días (mañana+2). Debe ir ANTES que
            // "pasado mañana" y que "mañana" suelto: la palabra "mañana" dentro de la
            // frase casaba con mananaAsDate → +1 (fecha errónea) y "antepasado" quedaba
            // como residuo en el título (P1: cita 2 días antes y título corrupto).
            Regex("""(?i)\bantepasad[oa]\s+ma[nñ]ana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(3)
            Regex("""(?i)\bpasado\s+ma[nñ]ana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
            // "mañana" como fecha (el día de mañana) sólo si NO forma parte de un
            // marcador de parte del día ("de la mañana", "por la mañana", "a la
            // mañana"). Antes, "Reunión a las 9 de la mañana" se fechaba en MAÑANA
            // por la mera coincidencia de la palabra "mañana", programando para
            // mañana una reunión de hoy (P1: tarea en día erróneo, reunión perdida
            // el mismo día). Se buscan todas las apariciones y basta con que una
            // sea un token de fecha suelto.
            mananaAsDate(working) -> base.toLocalDate().plusDays(1)
            Regex("""(?i)\bhoy\b""").containsMatchIn(working) -> base.toLocalDate()
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
            weekendMatch != null -> nextWeekday(base.toLocalDate(), DayOfWeek.SATURDAY)
            // "el viernes a las 18" escrito el propio viernes ANTES de esa hora debe
            // vencer HOY (la reunión es hoy), no la semana siguiente. nextWeekday
            // siempre salta +7 cuando hoy es el día objetivo (lo reutilizan las
            // recurrencias, que necesitan ese "próximo" estricto). Para la fecha
            // suelta usamos nextWeekdayOrSame (incluye hoy) y diferimos al final del
            // parseo el descarte de "hoy si la hora ya pasó" → ahí se rueda +7 días.
            // Sin esto, una cita de hoy con hora futura se perdía una semana entera.
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
            monthNameDate != null -> monthNameDate
            // "antes del 30": plazo como día del mes suelto (sin nombre de mes, que ya
            // se resolvió arriba como monthNameDate). Debe ir ANTES de dayOfMonthDate
            // ("el 15"), que exige el artículo "el"/"día" y no casa "antes del 30".
            beforeDeadlineDayDate != null -> beforeDeadlineDayDate
            // "reunión el 15 a las 10": día del mes suelto. Ancla al día N de este mes, o
            // del siguiente si ese día ya pasó (hoy > N). La hora se combina luego; si
            // cae en pasado (mismo día, hora ya transcurrida) la cita queda como vencida
            // (honesto: ya ocurrió), consistente con el resto del parser.
            dayOfMonthDate != null -> dayOfMonthDate
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
            recurrence.frequency == RecurrenceFrequency.MONTHLY && recurrence.monthlyDays != null ->
                nextMonthlyDateFromList(base.toLocalDate(), recurrence.monthlyDays)
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

        val timeMatch = timePatterns.asSequence().mapNotNull { it.find(working) }.minByOrNull { it.range.first }
        val explicitTimeData = timeMatch?.let { match ->
            val mv = match.value.lowercase()
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
                !Regex("""^\s*(?:,|\.|;|:|!|\?|y\b|o\b|con\b|de\b|del\b|en\b|para\b|hasta\b|desde\b|luego\b|después\b|despues\b|pero\b|porque\b|por\b|sin\b|sobre\b|a\b|al\b|el\b|la\b|los\b|las\b|un\b|una\b|mañana\b|manana\b|hoy\b|ayer\b|anteayer\b|lunes\b|martes\b|miércoles\b|miercoles\b|jueves\b|viernes\b|sábado\b|sabado\b|domingo\b|$)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(working.substring(m.range.last + 1))
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
            val endMinEffective = if (midnightWrap) endMin + 24 * 60 else endMin
            // Duración solo si fin > inicio (mismo día o envuelto) y rango plausible (<= 24h).
            val hasMinutesOrMeridiem = startM != 0 || endM != 0 ||
                startMer.isNotEmpty() || endMer.isNotEmpty()
            // Rango en punto y ambiguo (sin unidad/minutos/meridiem, ambas < 13): solo se
            // acepta si no le sigue un sustantivo de cantidad ("entradas", "personas").
            val ambiguousOnTheHour = !hasUnit && !hasMinutesOrMeridiem &&
                startH < 13 && endH < 13
            val acceptAmbiguous = !ambiguousOnTheHour ||
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
            if (explicitTime == null) standaloneHourPartOfDayPattern.find(working) else null
        val standaloneHourPartOfDayTime = standaloneHourPartOfDayMatch?.let { resolveStandaloneHourPartOfDay(it) }
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
            ?: ultimaHoraMatch?.let { ultimaHoraTime }
            ?: alFinalDelDiaMatch?.let { alFinalDelDiaTime }
            ?: amanecerMatch?.let { amanecerTime }
            ?: atardecerMatch?.let { atardecerTime }
        val effectiveDate = date ?: if (parsedTime != null) base.toLocalDate() else null
        val rawDueAt = when {
            effectiveRelativeDueAt != null && relativeIsDays && parsedTime != null ->
                DateRules.toEpochMillis(DateRules.toLocalDate(effectiveRelativeDueAt, zone), parsedTime, zone)
            // immediateDueAt (cadencia sub-diaria "cada N horas": el motor no repite por
            // hora, pero se saca la primera dosis a la superficie venciendo ahora) es el
            // último recurso: sólo aplica si NO hay otra fecha/hora resuelta. Así "cada 8
            // horas a las 3pm" usa la hora explícita, no "ahora".
            else -> effectiveRelativeDueAt
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
        val dueAt = when {
            weekdaySameDayCandidate && rawDueAt != null && rawDueAt < now ->
                DateRules.toEpochMillis(date!!.plusDays(7), parsedTime ?: LocalTime.of(9, 0), zone)
            date == null && effectiveRelativeDueAt == null && !explicitTimeIsRangeEnd &&
                parsedTime != null && hasExplicitMeridiem &&
                (parsedTime == LocalTime.MIDNIGHT || parsedTime == LocalTime.NOON) &&
                rawDueAt != null && rawDueAt < now ->
                DateRules.toEpochMillis(base.toLocalDate().plusDays(1), parsedTime, zone)
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
            .let { value -> timePatterns.fold(value) { acc, pattern -> pattern.replace(acc, " ") } }
            .let { value -> standalonePartOfDayPattern.replace(value, " ") }
            .let { value -> compactDayPartOfDayPattern.replace(value, " ") }
            .let { value -> primeraHoraPattern.replace(value, " ") }
            .let { value -> ultimaHoraPattern.replace(value, " ") }
            .let { value -> alFinalDelDiaPattern.replace(value, " ") }
            .let { value -> amanecerPattern.replace(value, " ") }
            .let { value -> atardecerPattern.replace(value, " ") }
            // "el día de mañana"/"el día de hoy"/"para el día de mañana": forma
            // pleonástica coloquial de "mañana"/"hoy". El borrado genérico de abajo
            // consume sólo la palabra "mañana"/"hoy" y deja el residuo "el día de"
            // en el título (p. ej. "reunión el día de" en vez de "reunión"), que es
            // contenido capturado degradado (P1: integridad de datos). Se consume la
            // frase completa primero; el resto del regex sigue borrando los tokens
            // sueltos ("hoy"/"ayer"/"anteayer"/"pasado mañana"/"antepasado mañana").
            .replace(Regex("""(?i)\b(?:para\s+)?(?:el|del)\s+d[ií]a\s+de\s+(?:ma[nñ]ana|hoy)\b"""), " ")
            // Calificador "de/del/desde + día relativo" ("reunión de mañana", "tarea de hoy",
            // "cita de ayer", "llamada de pasado mañana", "trabajo desde hoy", "estudio desde
            // mañana"): la preposición "de"/"del"/"desde" antes de un marcador de día relativo
            // es siempre un calificador temporal (genitivo de posesión temporal / punto de
            // partida temporal en español). Antes el borrado de "mañana"/"hoy"/etc. como palabra
            // suelta dejaba el conector como residuo en el título ("llamar de", "reunión desde"
            // en vez de "llamar"/"reunión") — contenido capturado degradado (P1). Se consume el
            // conector junto con el día relativo. "para" ya lo limpia el paso posterior (para
            // mañana/para el). El \b impide coincidir dentro de palabras como "desde"→ no aplica.
            .replace(Regex("""(?i)(?:\b(?:de|del|desde)\s+)?(?:antepasad[oa]\s+ma[nñ]ana\b|\bpasado\s+ma[nñ]ana\b|\bma[nñ]ana\b|\bhoy\b|\banteayer\b|\bantier\b|\bayer\b)"""), " ")
            .let { value -> weekdayPattern.replace(value, " ") }
            .let { value -> weekendPattern.replace(value, " ") }
            // "que viene" queda como residuo cuando la fecha asociada (fin de
            // semana, día de la semana) se consume pero la frase modificadora no.
            // Se borra aquí para no dejar basura en el título.
            .replace(Regex("""(?i)\bque\s+viene\b"""), " ")
            // Solo se elimina la fecha "5 de marzo" si el mes es válido: así "9 de la"
            // (en "a las 9 de la tarde") no se destruye y deja restos en el título.
            .replace(monthNamePattern) { m ->
                if (months.any { (name, _) ->
                        m.groupValues[2].equals(name, ignoreCase = true)
                    }) " " else m.value
            }
            .let { value -> numericDatePattern.replace(value, " ") }
            // "el 15" suelto ya consumido como fecha; se borra el residuo del título.
            .let { value -> dayOfMonthPattern.replace(value, " ") }
            // "antes del 30": se consume la frase COMPLETA (conector + día) para que
            // no quede el "30" como residuo en el título. La fecha ya se resolvió arriba.
            // Los casos con mes ("antes del 30 de agosto") no casan aquí (lookahead) y
            // su conector "antes del" lo borra el paso siguiente.
            .let { value -> beforeDeadlineDayPattern.replace(value, " ") }
            .replace(Regex("""(?i)\bantes\s+del?\b|\bpara\s+el\b|\bpara\s+ma[nñ]ana\b|\bhasta\s+el\b"""), " ")
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
                        .replace(Regex("""\s+"""), " ")
                        .trim(' ', ',', '.', '-')
                    if (stripped.isNotBlank()) stripped else value
                }
            }
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

        // ¿El verbo de recordatorio era el ÚNICO contenido tras limpiar la agenda?
        // (sin recurrencia: las cadencias mantienen la hora como hora de cita).
        val reminderVerbIsOnlyContent = hasBareReminderVerb &&
            recurrence.frequency == RecurrenceFrequency.NONE &&
            bareReminderVerbPattern.replace(working, " ")
                .replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '-').isBlank()

        val confidence = when {
            effectiveRelativeDueAt != null -> 1.0f
            dueAt != null && parsedTime != null -> 1.0f
            dueAt != null -> 0.9f
            priority != TaskPriority.NORMAL || durationMinutes != null || reminderOffsetMinutes != null ||
                recurrence.frequency != RecurrenceFrequency.NONE || category.isNotEmpty() -> 0.6f
            else -> 0.35f
        }

        return ParsedTaskInput(
            title = working.ifBlank { original }.take(240),
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
        val monthlyDays: List<Int>? = null
    )

    /** Captura normalizada de un anclaje mensual ORDINAL de weekday ("el primer lunes del
     *  mes" directa, o "cada mes el primer lunes" con cadencia precedente). Unifica ambas
     *  formas para que el motor ancle cada ciclo al N-ésimo/último weekday del mes y para
     *  resolver la primera fecha. monthName/yearStr sólo aplican a la forma directa. */
    private data class OrdinalMonthlyCapture(
        val ordinalWord: String,
        val weekdayWord: String,
        val isNext: Boolean,
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
        val weekdayRangePattern =
            Regex("""(?i)\b(?:(?:los\s+|de\s+)?lunes\s+a\s+viernes|entre\s+lunes\s+(?:a|y)\s+viernes)\b""")
        val weekdayRangeMatch = weekdayRangePattern.find(working)
        if (weekdayRangeMatch != null) {
            phrases += weekdayRangeMatch.range
            val interval = detectWeekInterval()
            if (interval != null) phrases += interval.second
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, interval?.first ?: 1, listOf(1, 2, 3, 4, 5), phrases)
        }

        // "todos los viernes" / "cada lunes y jueves" / "los lunes y jueves".
// Un único patrón captura una lista de días separados por ",", "y" o solo
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
            Regex("""(?i)\b(?:entre\s+)?(?:(todos\s+los|cada|los)\s+)?(?:(el|los)\s+)?((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?)(?:\s*(?:,|y)?\s*(?:(?:el|los)\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?))*)\b""")
        val dayNameRegex = Regex("""(?i)lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo""")
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
        // singular "fin de semana"/"finde" (fecha unica, proximo sabado): el plural o
        // el determinante "cada/los" = habito. "este finde"/"el finde" se resuelve
        // arriba como fecha (weekendPattern), NO aqui, porque el singular con "este/el"
        // señala UN fin de semana concreto, no un habito recurrente.
        val weekendRecurrencePattern =
            Regex("""(?i)\b(?:cada\s+)?(?:los\s+)?fines\s+de\s+semana\b|\b(?:cada\s+)?(?:los\s+)?findes?\b|\bcada\s+fin\s+de\s+semana\b""")
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
        val monthlyDualDayPattern =
            Regex("""(?i)\b(?:cada|el|los)?\s*(?:d[ií]as?\s+)?(\d{1,2}(?:\s*,\s*\d{1,2})*(?:\s+y\s+(?:el|la|los|las)?\s*\d{1,2})?)\s+(?:de\s+(?:cada\s+)?mes|del\s+(?:cada\s+)?mes|todos\s+los\s+meses)(?:es)?(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))""")
        monthlyDualDayPattern.find(working)?.let { match ->
            // Extraer todos los enteros del grupo de la lista de días ("1, 15 y 30" → [1,15,30]).
            val days = Regex("""\d{1,2}""").findAll(match.groupValues[1])
                .mapNotNull { it.value.toIntOrNull()?.takeIf { d -> d in 1..31 } }
                .distinct()
                .sorted()
                .toList()
            if (days.size >= 2) {
                phrases += match.range
                return RecurrenceResult(
                    RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases,
                    monthlyDays = days
                )
            }
        }
        val monthlyDayPattern =
            Regex("""(?i)\b(?:cada|el|los)?\s*(?:d[ií]a\s+)?(\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes(?:es)?(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))""")
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
        // "cada trimestre", "cada cuatrimestre", "cada semestre". Hitos financieros
        // de plazo largo (renta, impuestos, declaraciones, renovaciones). `intervalPattern`
        // solo admite "días|semanas|meses|años", así estas frases caían a NONE → la tarea
        // recurrente nacía sin fecha ni cadencia (P1: compromiso periódico olvidado,
        // invisible en What Now/planificador, recordatorio jamás disparaba) y "cada X"
        // quedaba como residuo literal en el título. Se mapean a MONTHLY + intervalo
        // (2/3/4/6), igual que el adjetivo equivalente, sin añadir enum ni migración:
        // RecurrenceEngine ya avanza `plusMonths(interval)`. El prefijo "cada" es
        // obligatorio: "próximo bimestre"/"el bimestre que viene"/"en un bimestre" son
        // FECHAS únicas (resueltas en la cascada de períodos) y no deben capturarse aquí.
        // Se procesa ANTES que multiMonthAdjective y fixedPatterns para limpiar el título.
        val multiMonthNounPattern =
            Regex("""(?i)\bcada\s+(bimestres?|trimestres?|cuatrimestres?|semestres?)\b""")
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
            t.contains("fin") || t.contains("finales") || t.contains("cierre") || t.contains("corte") -> "end"
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
            "primer", "primero" -> 1
            "segundo" -> 2
            "tercer", "tercero" -> 3
            "cuarto" -> 4
            else -> -1
        }
        // "del mes que viene/que entra/próximo/entrante" → mes siguiente; "este mes" NO.
        val isNext = capture.isNext
        val monthName = capture.monthName?.lowercase()
        val yearStr = capture.yearStr
        val namedMonth = monthName?.let { months[it] }
        // Mes nombrado: año actual salvo explícito (2 cifras → 2000+). "del mes" (sin
        // mes-nombre ni isNext) = mes en curso (vencida honesta si ya pasó).
        var year = when {
            yearStr == null -> today.year
            yearStr.toIntOrNull()?.let { it < 100 } == true -> 2000 + yearStr.toInt()
            else -> yearStr.toIntOrNull() ?: today.year
        }
        val month = when {
            namedMonth != null -> namedMonth
            isNext -> if (today.monthValue == 12) { year = today.year + 1; 1 } else today.monthValue + 1
            else -> today.monthValue
        }
        var date = nthWeekdayInMonth(year, month, ordinal, weekday)
        // Mes nombrado (no "del mes"/"este mes"/isNext) ya pasado SIN año explícito: si es un
        // mes DISTINTO al actual (p. ej. "de enero" dicho en agosto), se recalcula en el año
        // siguiente (no agendar en pasado). Si es el mes ACTUAL ("primer lunes de agosto" dicho
        // el 14, cuando el lunes 3 ya pasó), se mantiene vencido honesto (igual que "del mes"):
        // el usuario se refiere a este agosto, no al del año que viene. Se RECALCULA el weekday
        // en el nuevo año vía nthWeekdayInMonth (no `plusYears(1)`, que desplaza el día de la
        // semana: "último viernes de junio" rodado a 2027 caería en sábado 2027-06-26, no viernes
        // 2027-06-25).
        if (namedMonth != null && yearStr == null && month != today.monthValue && date.isBefore(today)) {
            date = nthWeekdayInMonth(year + 1, month, ordinal, weekday)
        }
        // Recurrencia con ocurrencia ordinal ya pasada: avanzar al próximo mes que mantenga
        // el mismo ordinal+weekday sin caer en pasado (ver cabecera). El mes siguiente siempre
        // es posterior, así que una iteración basta; el bucle es seguro por guardián.
        if (isRecurring && date.isBefore(today)) {
            var y = year
            var m = month
            var guard = 0
            while (date.isBefore(today) && guard++ < 24) {
                m += 1
                if (m > 12) { m = 1; y += 1 }
                date = nthWeekdayInMonth(y, m, ordinal, weekday)
            }
        }
        return date
    }

    /** N-ésima (ordinal<0 = última) ocurrencia de [weekday] en (year, month). */
    private fun nthWeekdayInMonth(year: Int, month: Int, ordinal: Int, weekday: DayOfWeek): LocalDate =
        if (ordinal < 0) {
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.previousOrSame(weekday))
        } else {
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.firstInMonth(weekday))
                .plusWeeks((ordinal - 1).toLong())
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
        val timeMarker = Regex("""(?i)(?:de|por|a|en)\s+la\s+$|\besta\s+$""")
        var idx = 0
        while (true) {
            val m = Regex("""(?i)\bma[nñ]ana\b""").find(working, idx) ?: return false
            val prefix = working.substring(0, m.range.first)
            if (!timeMarker.containsMatchIn(prefix)) return true
            idx = m.range.last + 1
        }
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
        "veinte" to 20L, "veintiuno" to 21L,
        "veintidós" to 22L, "veintidos" to 22L,
        "veintitrés" to 23L, "veintitres" to 23L,
        "veinticuatro" to 24L, "veinticinco" to 25L,
        "veintiséis" to 26L, "veintiseis" to 26L, "veintisiete" to 27L,
        "veintiocho" to 28L, "veintinueve" to 29L,
        "treinta" to 30L, "cuarenta" to 40L, "cincuenta" to 50L,
        "sesenta" to 60L, "setenta" to 70L, "ochenta" to 80L, "noventa" to 90L
    )
}

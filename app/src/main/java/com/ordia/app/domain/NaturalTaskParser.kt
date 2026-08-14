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
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|unos|unas|\d{1,3}|$writtenNumberGroup)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)(?:\s+y\s+(media|medio))?\b"""
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
     */
    private val nextPeriodPattern = Regex(
        """(?i)(?<!\p{L})(?:a\s+)?(?:el|la)?\s*(?:semana|mes|a[nñ]o|trimestre|bimestre|semestre|quincena)\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante)\b|(?<!\p{L})(?:a\s+)?(?:el|la)?\s*(?:pr[oó]ximo|pr[oó]xima)\s+(?:semana|mes|a[nñ]o|trimestre|bimestre|semestre|quincena)\b|(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b"""
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
     * → último día del mes actual (o del siguiente si hoy ya es el último día). "mediados de mes" /
     * "a mediados de mes" → día 15 del mes actual (o del siguiente si hoy ≥ 15).
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
     */
    private val endOfMonthPattern = Regex("""(?i)(?<!\p{L})(?:a\s+)?(?:fin(?:ales|es)?|cierre|corte|[uú]ltim[oa]\s+d[ií]a)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
    private val midOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:mediados?|mitad)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
    private val startOfMonthPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|primeros?)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
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
    private val monthBoundaryNamePattern = Regex("""(?i)(?<!\p{L})(?:a\s+)?(mediados?|mitad|principios?|comienzos?|primeros?|finales?|fin|cierre|corte)\s+(?:de\s+|del\s+)([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
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
    private val startOfYearPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?|primeros?)\s+(?:de\s+|del\s+)(?:este\s+|esta\s+|pr[oó]xim[oa]\s+)?a[nñ]o(?:\s+(?:que\s+viene|que\s+entra|pr[oó]ximo|pr[oó]xima|entrante))?\b""")
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
     */
    private val quincenaPattern = Regex("""(?i)\b(?:de\s+la\s+|de\s+|la\s+)?(primera|1ra|1\.?a|segunda|2da|2\.?a)?\s*quincena\b""")
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
    private val startOfWeekPattern = Regex("""(?i)\b(?:a\s+)?(?:principios?|comienzos?)\s+(?:de\s+la\s+|de\s+|del\s+)semana\b""")
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

    private val timePatterns = listOf(
        // "a la una": la hora 1 se dice en femenino singular ("a la una", no "a las 1"),
        // con conector "a la" en vez de "a las". Quedaba sin resolver por el
        // patrón "a las N" (que excluye "un/una/uno" de WRITTEN_HOUR_ALT justo por esto),
        // así que "reunión a la una" caía sin dueAt y con "a la una" como residuo en el
        // título → el usuario olvidaba la cita. Mismo layout de grupos que el patrón
        // "a las N" (1=hora, 2=:MM, 3=y media/cuarto, 4=meridiem, 5=horas) para que
        // [explicitTimeData] lo procese sin ramificación. Admite "del mediodía" como
        // meridiem (PM, → 13:00): "a la una del mediodía" es la forma cotidiana de 1pm.
        Regex("""(?i)\ba\s+la\s+(una)(?::([0-5]\d))?(?:\s+(y\s+(?:media|cuarto)|menos\s+(?:cuarto|cinco|diez|veinte|veinticinco|\d{1,2})))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(horas?|hs))?\b"""),
        // Sufijo opcional "(horas?|hs)" tras la hora (con o sin meridiem) para consumir
        // "a las 9 horas" completo: antes "horas" quedaba como residuo en el titulo y,
        // peor, "9 horas" era robado como duracion (540 min falsos). Como grupo propio
        // (no meridiem), no altera la logica AM/PM ni marca meridiem explicito.
        // Grupo 3 opcional "y media"/"y cuarto": fracción sub-hora cotidiana en español
        // ("a las 9 y media" → 09:30, "a las 3 y cuarto" → 03:15). Antes "y media" caía
        // como residuo en el título y la hora quedaba en punto (reunión/cita 30 min mal).
        // Admite horas escritas ("a las nueve", "a las doce y media") vía [WRITTEN_HOUR_ALT];
        // antes esas formas dejaban la hora como residuo y se agendaban a la canónica de
        // la parte del día o sin hora ("reunión a las nueve" → sin dueAt).
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4]|$WRITTEN_HOUR_ALT)(?::([0-5]\d))?(?:\s+(y\s+(?:media|cuarto)|menos\s+(?:cuarto|cinco|diez|veinte|veinticinco|\d{1,2})))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada|del\s+mediod[ií]a)?(?:\s*(horas?|hs))?\b"""),
        Regex("""(?i)\b([01]?\d|2[0-4]):([0-5]\d)\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+|a\s+)?mediod[ií]a\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+|a\s+)?medianoche\b""")
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
        Regex("""(?i)\((\d{1,3})\s*(minutos?|min|horas?|hora)\)"""),
        Regex("""(?i)\b(?:durante|por)\s+(\d{1,3})\s*(minutos?|min|horas?|hora)\b"""),
        // Keyword "duración (de/:) N [unidad]": la forma más natural de declarar la
        // duración de una reunión/cita en español. Antes la palabra "duración" NO se
        // reconocía como señal de duración: con unidad ("duración 30 minutos") el
        // patrón "N minutos" casaba y daba durationMinutes=30, pero "duración" quedaba
        // como residuo en el título; sin unidad ("duración 45") no casaba nada →
        // durationMinutes=null y la frase entera se conservaba. La unidad es opcional:
        // si falta se asume minutos (convención del proyecto). Va antes que los
        // patrones "N unidad" para que, al quedar más a la izquierda, [durationMatch]
        // la elija y consuma la frase completa ("duración" incluida).
        Regex("""(?i)\bduraci[oó]n\s*(?::|de)?\s*(\d{1,3})\s*(minutos?|min|horas?|hora)?\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|min)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(horas?)\b"""),
        // Compacto "Nh" (p. ej. "Trabajar 2h", "Estudiar 1h"). El \b final evita
        // casar "2horas" (h seguida de 'o' no es límite de palabra), así no roba
        // ni deja residuo frente al patrón completo "horas?".
        Regex("""(?i)\b(\d{1,3})\s*(h)\b""")
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
     */
    private val compoundFractionalDurationPattern =
        Regex("""(?i)\b($writtenAmountPattern)\s*horas?\s+y\s+(media|cuarto)\b""")

    /**
     * Rango horario "de H1[MM] [meridiem] a H2[MM] [meridiem] [horas]" (citas, clases,
     * reuniones con ventana). Implica duración = (fin − inicio) en minutos y se elimina
     * del título. Cada extremo admite minutos (`9:30`) y meridiem (`9am`, `9 de la tarde`)
     * además de la forma en punto (`9`).
     *
     * Para no falsear datos (p. ej. "comprar de 2 a 5 entradas") solo se acepta cuando hay
     * evidencia de horario: unidad final ("horas"/"hs"/"h"), minutos en algún extremo
     * (`:30`, inequívoco de reloj), meridiem explícito, o alguna hora >= 13 (24h). Sin esa
     * evidencia, el rango en punto y ambiguo (<13) requiere además que no le siga un
     * sustantivo de cantidad (ver `followedByCount`). Antes solo se capturaban horas en
     * punto: "clase de 9:30 a 11" casaba `30 a 11` con números equivocados → `dur=null`
     * y título sucio. No fija hora de inicio (ambigua sin contexto); solo la duración.
     *
     * Grupo 1/2/3 = hora/minuto/meridiem del INICIO; 4/5/6 = fin; 7 = "horas" opcional.
     */
    private val timeRangePattern =
        Regex("""(?i)\b(?:de\s+)?(\d{1,2})(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?\s*(?:a|-)\s*(\d{1,2})(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+(?:ma[nñ]ana|manana|tarde|noche|madrugada))?(?:\s*((?:horas?|hs|h)))?\b""")

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
     */
    private val standaloneHourPartOfDayPattern =
        Regex("""(?i)(?<![:\d])(\d{1,2}|$WRITTEN_HOUR_ALT)(?::([0-5]\d))?(?:\s+y\s+(media|cuarto))?\s+de\s+la\s+(tarde|noche|madrugada|ma[nñ]ana|manana)(?!\s+de\s+(?!hoy\b|ma[nñ]ana\b|ayer\b|anteayer\b|antier\b|pasado\s+ma[nñ]ana\b|antepasad[oa]\s+ma[nñ]ana\b)[a-záéíóúüñ])""")

    private fun resolveStandaloneHourPartOfDay(match: MatchResult): LocalTime? {
        val h = parseHour(match.groupValues[1]) ?: return null
        // Minutos explícitos ":MM" (grupo 2) con prioridad; si no, la fracción
        // "y media"/"y cuarto" (grupo 3) aporta 30/15; si tampoco, 0.
        val min = match.groupValues[2].toIntOrNull()
            ?: when (match.groupValues[3].lowercase()) {
                "media" -> 30
                "cuarto" -> 15
                else -> 0
            }
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
        "oct" to 10, "nov" to 11, "dic" to 12
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
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
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
                unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("hora") -> amount * 60 * 60_000L
                else -> amount * unitDays * 24 * 60 * 60_000L
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
        val monthBoundaryDueAt = when {
            endOfMonthEarlyMatch != null -> {
                val baseMonth = monthBaseForBoundary(base.toLocalDate(), endOfMonthEarlyMatch.value)
                val lastDay = baseMonth.withDayOfMonth(baseMonth.lengthOfMonth())
                DateRules.toEpochMillis(lastDay, LocalTime.of(9, 0), zone)
            }
            midOfMonthEarlyMatch != null -> {
                val baseMonth = monthBaseForBoundary(base.toLocalDate(), midOfMonthEarlyMatch.value)
                DateRules.toEpochMillis(baseMonth.withDayOfMonth(15), LocalTime.of(9, 0), zone)
            }
            startOfMonthEarlyMatch != null -> {
                val baseMonth = monthBaseForBoundary(base.toLocalDate(), startOfMonthEarlyMatch.value)
                DateRules.toEpochMillis(baseMonth.withDayOfMonth(1), LocalTime.of(9, 0), zone)
            }
            else -> null
        }
        endOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        midOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }
        startOfMonthEarlyMatch?.let { working = working.replaceRange(it.range, " ") }

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
            thisWeekDueAt ?: startOfWeekDueAt ?: midOfWeekDueAt ?: quincenaDueAt ?:
            nextMonthDayDueAt ?: nextMonthDayReverseDueAt ?: nextMonthDayShortDueAt ?:
            nextMonthDayShortReverseDueAt ?:
            nextWeekWeekdayReverseDueAt ?: nextWeekWeekdayForwardDueAt ?: nextPeriodDueAt
        val relativeIsDays = (agoMatch != null || lastPeriodMatch != null ||
            relativeMatch != null || fractionalRelativeMatch != null ||
            fractionalAndQuarterRelativeMatch != null ||
            compoundFractionalRelativeMatch != null || multiQuarterRelativeMatch != null ||
            monthBoundaryDueAt != null || monthBoundaryNameDueAt != null || yearBoundaryDueAt != null ||
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
        val recurrence = parseRecurrence(working)
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
            // Recurrencias de intervalo (diaria, semanal/quincenal/mensual/anual sin día
            // explícito): se anclan a la fecha de captura. Antes quedaban con dueAt=null y
            // la tarea recurrente era invisible (sin recordatorio, sin aparición en What
            // Now/planificador → se olvidaba). La fecha explícita ya se resolvió arriba en
            // este when, así que esto solo alcanza las recurrencias sin anclaje específico.
            recurrence.frequency != RecurrenceFrequency.NONE -> base.toLocalDate()
            else -> null
        }

        val timeMatch = timePatterns.asSequence().mapNotNull { it.find(working) }.minByOrNull { it.range.first }
        val explicitTimeData = timeMatch?.let { match ->
            val mv = match.value.lowercase()
            when {
                // "a la una del mediodía" captura hora (grupo 1 = "una") + meridiem "del
                // mediodía": NO debe caer a NOON, sino resolver 1pm (13:00) en la rama
                // genérica. Esta rama solo aplica a las frases puras "al mediodía"/
                // "a la medianoche" (patrón sin grupo de hora). Se distingue por ausencia
                // de hora capturada (grupo 1) y de minutos (grupo 2).
                (mv.contains("mediodía") || mv.contains("mediodia")) && match.groupValues.getOrNull(1).isNullOrBlank() && match.groupValues.getOrNull(2).isNullOrBlank() -> LocalTime.NOON to true
                mv.contains("medianoche") && match.groupValues.getOrNull(1).isNullOrBlank() && match.groupValues.getOrNull(2).isNullOrBlank() -> LocalTime.MIDNIGHT to true
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
                    // Fracción positiva "y media"/"y cuarto": +30/+15 min sobre la hora
                    // en punto (sin minutos explícitos). "a las 9 y media" → 09:30. La
                    // fracción negativa "menos cuarto/cinco/diez/veinte/veinticinco" se
                    // resuelve más abajo (resta minutos con wrap de 24h); antes no se
                    // interpretaba: caía como residuo del título y la cita quedaba en
                    // punto (reunión/cita 15-25 min mal). El grupo 3 llega como frase
                    // completa ("y media", "menos cuarto").
                    val addFraction = when {
                        raw3.endsWith("y media") || raw3.endsWith("y cuarto") -> raw3
                        else -> ""
                    }
                    val subFraction = if (raw3.startsWith("menos ")) raw3 else ""
                    val minute = explicitMinute ?: when {
                        addFraction.endsWith("media") -> 30
                        addFraction.endsWith("cuarto") -> 15
                        else -> 0
                    }
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
                        if (subFraction.isNotEmpty() && explicitMinute == null) {
                            val sub = when {
                                subFraction.endsWith("veinticinco") -> 25
                                subFraction.endsWith("veinte") -> 20
                                subFraction.endsWith("cuarto") -> 15
                                subFraction.endsWith("diez") -> 10
                                subFraction.endsWith("cinco") -> 5
                                else -> Regex("""\d{1,2}""").find(subFraction)?.value?.toIntOrNull() ?: 0
                            }
                            if (sub in 1..59) {
                                val total = (hour * 60 + minute - sub + 1440) % 1440
                                LocalTime.of(total / 60, total % 60) to meridiem.isNotEmpty()
                            } else {
                                LocalTime.of(hour, minute) to meridiem.isNotEmpty()
                            }
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
            val hasUnit = m.groupValues[7].isNotEmpty()
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
            else -> effectiveRelativeDueAt ?: effectiveDate?.let { DateRules.toEpochMillis(it, parsedTime ?: LocalTime.of(9, 0), zone) }
        }
        // "el viernes a las 18" escrito el viernes a las 10:00 → hoy 18:00 (se conserva).
        // Pero si la hora ya pasó ("el viernes a las 6" a las 10:00) o no había hora y el
        // mediodía canónico (09:00) ya pasó, se rueda a la semana siguiente, igual que
        // antes. Así no se agenda nada en el pasado y no se pierde una cita de hoy.
        val dueAt = if (weekdaySameDayCandidate && rawDueAt != null && rawDueAt < now) {
            DateRules.toEpochMillis(date!!.plusDays(7), parsedTime ?: LocalTime.of(9, 0), zone)
        } else rawDueAt


        // Duración numérica: se descarta si el número está precedido por una frase
        // horaria ("a las 9 horas", "a la 1 horas", "de la tarde 2 horas"), porque ahí
        // "N horas" es la HORA de un evento, no su duración. Sin este guard, "reunión a
        // las 9 horas" robaba "9 horas" como 540 min falsos y dejaba el residuo "a las".
        // El "en N" final (fecha relativa) ya se filtra con la regex existente.
        val timePhrasePreceding = Regex(
            """(?i)(?:a\s+las|a\s+la(?:\s+ma[ñn]ana)?|de\s+la\s+(?:ma[ñn]ana|tarde|noche|madrugada))\s*$"""
        )
        val durationMatch = durationPatterns.asSequence()
            .mapNotNull { it.find(working) }
            .filter { match ->
                !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
                !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first))
            }
            .minByOrNull { it.range.first }
        // Duración con número escrito ("dos horas"/"treinta minutos"/"un par de horas"):
        // mismos guards que la duración numérica para no robar "a las nueve horas" (hora
        // de un evento) ni "en dos horas" (fecha relativa, ya consumida antes). Se procesa
        // aparte porque su cantidad se resuelve con [parseWrittenNumber], no con toIntOrNull.
        val writtenMatch = writtenDurationPattern.find(working)?.takeIf { match ->
            !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
            !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first))
        }
        // Duración fraccionaria sin dígitos ("media hora"/"cuarto de hora"): se computa
        // aparte y se elige la ocurrencia más a la izquierda respecto a las demás.
        val fractionalMatch = fractionalDurationPattern.find(working)
        // Duración fraccionaria COMPUESTA ("2 horas y media"/"dos horas y cuarto"):
        // mismos guards que la duración numérica/escrita. Captura la frase completa
        // (incluida la fracción) para que no quede "y media" como residuo en el título.
        val compoundFractionalDurationMatch = compoundFractionalDurationPattern.find(working)?.takeIf { match ->
            !Regex("""(?i)\ben\s*$""").containsMatchIn(working.substring(0, match.range.first)) &&
            !timePhrasePreceding.containsMatchIn(working.substring(0, match.range.first))
        }
        val durationMinutes = when {
            rangeDurationMinutes != null -> rangeDurationMinutes
            compoundFractionalDurationMatch != null &&
                (durationMatch == null || compoundFractionalDurationMatch.range.first <= durationMatch.range.first) &&
                (writtenMatch == null || compoundFractionalDurationMatch.range.first <= writtenMatch.range.first) &&
                (fractionalMatch == null || compoundFractionalDurationMatch.range.first <= fractionalMatch.range.first) -> {
                val amount = compoundFractionalDurationMatch.groupValues[1].let {
                    it.toIntOrNull() ?: parseWrittenNumber(it)?.toInt()
                }
                val fraction = compoundFractionalDurationMatch.groupValues[2].lowercase()
                amount?.let { (it * 60 + if (fraction.startsWith("media")) 30 else 15).coerceIn(5, 24 * 60) }
            }
            durationMatch != null && (fractionalMatch == null ||
                durationMatch.range.first <= fractionalMatch.range.first) &&
                (writtenMatch == null || durationMatch.range.first <= writtenMatch.range.first) -> {
                val amount = durationMatch.groupValues[1].toIntOrNull()
                val unit = durationMatch.groupValues[2].lowercase()
                amount?.let { (if (unit.startsWith("hora") || unit == "h") it * 60 else it).coerceIn(5, 24 * 60) }
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
            .let { value -> if (hasBareReminderVerb) bareReminderVerbPattern.replace(value, " ") else value }
            .replace(Regex("""(?i)\b(para|el)\b\s*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')

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
            // Sin dueAt no se programa reminderAt, así que no se falsifica el offset.
            reminderOffsetMinutes = reminderOffsetMinutes
                ?: if (hasBareReminderVerb && dueAt != null) BARE_REMINDER_DEFAULT_OFFSET_MINUTES else null,
            recurrence = recurrence.frequency,
            recurrenceInterval = recurrence.interval,
            recurrenceDays = recurrence.days.joinToString(","),
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
        /** Hora canónica de la parte del día para "cada mañana/tarde/noche/madrugada"
         *  (hábito diario): 09:00/15:00/21:00/04:00. Se usa como hora de respaldo de la
         *  primera ocurrencia y como contexto PM para horas sin meridiem. */
        val partOfDayTime: LocalTime? = null,
        val partOfDayIsPm: Boolean = false
    )

    private fun parseRecurrence(working: String): RecurrenceResult {
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
            Regex("""(?i)\bcada\s+(\d{1,3}|$writtenNumberGroup)\s*semanas?\b""").find(working)?.let { m ->
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
        val weekdayRangePattern =
            Regex("""(?i)\b(?:los\s+|de\s+)?lunes\s+a\s+viernes\b""")
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
        val dayListPattern =
            Regex("""(?i)\b(?:(todos\s+los|cada|los)\s+)?((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?)(?:\s*(?:,|y)?\s*(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?))*)\b""")
        val dayNameRegex = Regex("""(?i)lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo""")
        val weeklyMatch = dayListPattern.find(working)
        if (weeklyMatch != null) {
            val days = dayNameRegex.findAll(weeklyMatch.groupValues[2])
                .mapNotNull { it.value.toDayOfWeekOrNull()?.value }
                .distinct().sorted().toList()
            val hasPrefix = weeklyMatch.groupValues[1].isNotBlank()
            // Bare: 2+ días siempre es recurrencia. Un día suelto solo lo es si es
            // plural marcado (sábados/domingos), forma habitual de hábito semanal
            // ("fútbol domingos"). Los demás días son invariables (lunes/martes…),
            // así que "reunión martes" queda como fecha ambigua, no recurrencia.
            val barePluralSingle = !hasPrefix && days.size == 1 &&
                weeklyMatch.groupValues[2].lowercase().let { g ->
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
        val monthlyDayPattern =
            Regex("""(?i)\b(?:el|los)?\s*(?:d[ií]a\s+)?(\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes(?:es)?(?!\s+(?:actual|presente|este|entrante|pr[oó]ximos?|siguientes?|que\s+(?:viene|entra|sigue)))""")
        monthlyDayPattern.find(working)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: return@let
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.MONTHLY, 1, emptyList(), phrases, day)
        }

        // "cada N días/semanas/meses/años" — N puede ser dígito O número escrito
        // ("cada dos semanas", "cada tres meses", "cada quince días"). Antes el grupo
        // sólo admitía `\d{1,3}`, así que las formas con palabra caían a NONE y la
        // tarea recurrente nacía sin fecha (recordatorio jamás disparaba). Se reutiliza
        // `parseWrittenNumber` para resolver la palabra; la alternación está acotada a
        // los números conocidos para no colisionar con la unidad (días/semanas/...).
        val intervalPattern =
            Regex("""(?i)\bcada\s+(\d{1,3}|$writtenNumberGroup)\s*(d[ií]as?|semanas?|meses?|a[nñ]os?)\b""")
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

        // "cada quincena" / "quincenalmente" / "quincenal" (adjetivo) / "todas las
        // quincenas": cadencia quincenal cotidiana en español (nóminas, pagos,
        // reportes cada 15 días). `intervalPattern` solo admite dígitos ("cada 2
        // semanas"), así que la forma con palabra "quincena" caía a NONE y la tarea
        // recurrente nacía sin fecha (invisible en What Now/planificador, recordatorio
        // jamás disparaba). La forma ADJETIVA ("pago quincenal", "reunión
        // quincenal") tampoco casaba: solo se reconocía el adverbio "quincenalmente".
        // Ahora el adjetivo "quincenal" (sin "-mente") también genera la cadencia.
        // Se mapea a WEEKLY interval=2 (cada 2 semanas ≈ quincena) sin añadir enum ni
        // migración: representación honesta y reutiliza el avance semanal existente.
        Regex("""(?i)\b(?:cada\s+quincena|quincenal(?:mente)?|todas\s+las\s+quincenas)\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 2, emptyList(), phrases)
        }

        // Adjetivos plurimensuales cotidianos en español: "pago bimestral",
        // "impuesto trimestral", "cierre semestral". Son hitos financieros de plazo
        // largo tan comunes como el propio "mensual". Antes estas formas adjetivas
        // caían a NONE (la única vía era el numeral "cada 2/3/6 meses"): la tarea
        // recurrente nacía sin cadencia → vencimiento invisible, recordatorio jamás
        // disparaba (P1: compromiso periódico olvidado). Se reutilizan MONTHLY +
        // intervalo (2=bimestral, 3=trimestral, 6=semestral): RecurrenceEngine ya
        // avanza `plusMonths(interval)`, sin añadir enum ni migración. Se procesa
        // ANTES que fixedPatterns porque aquél solo admite interval=1.
        val multiMonthAdjective = listOf(
            Regex("""(?i)\bbimestral(?:mente)?\b""") to 2,
            Regex("""(?i)\btrimestral(?:mente)?\b""") to 3,
            Regex("""(?i)\bsemestral(?:mente)?\b""") to 6
        )
        multiMonthAdjective.forEach { (pattern, months) ->
            pattern.find(working)?.let { match ->
                phrases += match.range
                return RecurrenceResult(RecurrenceFrequency.MONTHLY, months, emptyList(), phrases)
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
    private val recurrenceAdjectiveLeakPattern =
        Regex("""(?i)\b(?:semanal(?:mente)?|mensual(?:mente)?|anual(?:mente)?|bimestral(?:mente)?|trimestral(?:mente)?|semestral(?:mente)?|quincenal(?:mente)?)\b""")

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
            q.contains("princip") || q.contains("comienz") || q.contains("primer") -> 1
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

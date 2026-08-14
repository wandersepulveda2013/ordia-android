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
    private val numericDatePattern = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val weekdayPattern = Regex("""(?i)\b(?:el\s+|del\s+|de\s+)?(?:pr[oó]ximo\s+|pr[oó]xima\s+)?(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)(?:\s+que\s+viene|\s+pr[oó]ximos?|\s+pr[oó]ximas?)?\b""")
    /** "este/el/próximo fin de semana" o "fin de semana" suelto → próximo sábado.
     *  Acepta también "finales de semana" (plural análogo a "finales de mes"): señala un
     *  fin de semana concreto, no un hábito. OJO: "fines de semana" (f-i-n-e-s) es
     *  recurrencia semanal y se resuelve aparte en parseRecurrence, no aquí. */
    private val weekendPattern = Regex("""(?i)\b(?:a\s+)?(?:este\s+|el\s+|pr[oó]ximo\s+)?(?:fin|finales)\s+de\s+semana\b""")
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
     * Fecha relativa: "en N minutos/horas/días/semanas/meses/años" o "dentro de N ...".
     * Acepta dígitos o números escritos (una/un, dos, ..., veinte, treinta). "una"/"un" → 1.
     * Las semanas (×7 días), meses (×30 días) y años (×365 días) son formas muy
     * comunes ("en una semana", "en un mes", "en un año", "en 2 años") que antes quedaban
     * sin fecha → la tarea se olvidaba (sin recordatorio, invisible en planificador/What Now).
     * Admite también las formas coloquiales "de aquí a N ..." y "de acá a N ..."
     * (equivalentes a "en/dentro de N ..."), simétricas al prefijo estándar.
     */
    private val relativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de|de\s+aqu[íi]\s+a|de\s+ac[aá]\s+a)\s+(un\s+par\s+de|\d{1,3}|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|treinta)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)\b"""
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
        """(?i)\bhace\s+(\d{1,3}|un\s+rato|poco|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[e\u00e9]is|diecisiete|dieciocho|diecinueve|veinte|treinta)\s*(minutos?|mins?|horas?|d[i\u00ed]as?|semanas?|mes(?:es)?|a[n\u00f1]os?)?\b"""
    )
    /**
     * "la semana pasada" / "el mes pasado" / "el año pasado": período completo
     * anterior. El usuario registra una tarea vencida refiriéndose al período previo
     * ("revisé el informe la semana pasada"). Se resuelve a hoy−1 período (semana/mes/
     * año) y se borra del título. No debe confundirse con "el jueves pasado" (día de
     * semana): aquí la unidad es el período, no el día.
     */
    private val lastPeriodPattern = Regex(
        """(?i)\b(?:la\s+semana|el\s+mes|el\s+a[n\u00f1]o)\s+pasad[oa]\b|\bsemana\s+pasada\b|\bmes\s+pasado\b|\ba[n\u00f1]o\s+pasado\b"""
    )
    /**
     * Período próximo ("la semana que viene", "el mes que viene", "el año que
     * viene", "próximo mes", "la próxima semana"): +1 período (semana/mes/año).
     * "trimestre que viene" / "próximo trimestre" = +3 meses = +90 días
     * (plazo largo cotidiano: impuestos trimestrales, revisiones, informes).
     * "quincena" (+15d), "bimestre" (+60d) y "semestre" (+180d) son períodos
     * cotidianos en español (pagos quincenales, reportes bimestrales, cierres
     * semestrales). Antes estas formas quedaban sin fecha y con la frase «que
     * viene» como residuo en el título → tarea olvidada (sin recordatorio ni
     * visibilidad). "próximos días" (con o sin "en los/el/las") es la forma vaga
     * de "dentro de poco": +3 días (heurística honesta, ni IA ni azar). Antes
     * quedaba sin fecha → la tarea se olvidaba.
     */
    private val nextPeriodPattern = Regex(
        """(?i)\b(?:el|la)?\s*(?:semana|mes|a[nñ]o|trimestre|bimestre|semestre|quincena)\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)\b|(?:el|la)?\s*(?:pr[oó]ximo|pr[oó]xima)\s+(?:semana|mes|a[nñ]o|trimestre|bimestre|semestre|quincena)\b|(?:en\s+(?:los|el|las)?\s+)?pr[oó]ximos?\s+d[ií]as\b"""
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
        """(?i)\bel\s+(\d{1,2})\s+(?:del?\s+)?(?:mes\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\b"""
    )
    /**
     * Orden inverso del anterior: "el mes que viene el 5" / "el mes que viene el
     * día 5" / "el próximo mes el 10" / "el mes próximo el 20". Misma semántica
     * (día N del mes siguiente) pero con el período ANTES del día — forma tan
     * cotidiana como la directa. Sin este patrón, nextPeriodPattern robaba
     * "el mes que viene" como +30d genérico (fecha errónea: p. ej. 12/09 en vez
     * del 05/09) y el día quedaba como residuo en el título o se sombreaba. Se
     * procesa ANTES que nextPeriodPattern para consumir la frase completa.
     */
    private val nextMonthDayReversePattern = Regex(
        """(?i)\b(?:el\s+)?(?:mes\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima)|pr[oó]ximos?\s+mes|mes\s+pr[oó]ximos?)\s+el\s+(?:d[ií]a\s+)?(\d{1,2})\b"""
    )
    /**
     * "la semana que viene el lunes" / "la próxima semana el viernes" /
     * "semana que viene el sábado": día de la semana objetivo de la SEMANA PRÓXIMA
     * (no +7d genérico desde hoy, que es lo que daba nextPeriodPattern). Sin este
     * patrón, nextPeriodPattern robaba "la semana que viene" como +7d e ignoraba
     * el día explícito → "la semana que viene el viernes" dicho un miércoles daba
     * el próximo miércoles (mañana+7) en vez del viernes de la semana que viene
     * (cita/reunión en día equivocado). Se procesa ANTES que nextPeriodPattern
     * para consumir la frase completa (período + día) y evitar que éste la robe.
     */
    private val nextWeekWeekdayReversePattern = Regex(
        """(?i)\b(?:la\s+)?(?:semana\s+(?:que\s+viene|pr[oó]xima)|pr[oó]xima\s+semana)\s+el\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""
    )
    /**
     * Orden inverso del anterior: "el lunes de la semana que viene" /
     * "el viernes de la próxima semana". Misma semántica (día objetivo de la
     * semana próxima) pero con el día ANTES del período — forma tan cotidiana
     * como la directa. Sin este patrón, weekdayPattern capturaba "el lunes" como
     * fecha suelta (nextWeekdayOrSame) y nextPeriodPattern robaba "la semana que
     * viene" como +7d; al combinarse, el +7d ganaba → día equivocado. Se procesa
     * ANTES que nextPeriodPattern y weekdayPattern para consumir la frase completa.
     */
    private val nextWeekWeekdayForwardPattern = Regex(
        """(?i)\bel\s+(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\s+de\s+(?:la\s+)?(?:semana\s+(?:que\s+viene|pr[oó]xima)|pr[oó]xima\s+semana)\b"""
    )
    /**
     * "fin de mes" / "a finales de mes" / "fin del mes" → último día del mes actual
     * (o del siguiente si hoy ya es el último día). "mediados de mes" /
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
    private val endOfMonthPattern = Regex("""(?i)\b(?:a\s+)?fin(?:ales|es)?\s+(?:de\s+|del\s+)(?:pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima))?\b""")
    private val midOfMonthPattern = Regex("""(?i)\b(?:a\s+)?mediados?\s+(?:de\s+|del\s+)(?:pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima))?\b""")
    private val startOfMonthPattern = Regex("""(?i)\b(?:a\s+)?principios?\s+(?:de\s+|del\s+)(?:pr[oó]xim[oa]\s+)?mes(?:\s+(?:que\s+viene|pr[oó]ximo|pr[oó]xima))?\b""")
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
    private val thisWeekPattern = Regex("""(?i)\besta\s+semana(?:\s+que\s+viene)?\b""")
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
    private val startOfWeekPattern = Regex("""(?i)\b(?:a\s+)?principios?\s+(?:de\s+|del\s+)semana\b""")
    /**
     * "mediados de semana" / "a mediados de semana" → miércoles más cercano en HOY o
     * futuro. Análogo a "principios de semana" (lunes) y "mediados de mes" (día 15).
     * Se detecta y borra ANTES del período próximo para que "semana" no active
     * "semana que viene".
     */
    private val midOfWeekPattern = Regex("""(?i)\b(?:a\s+)?mediados?\s+(?:de\s+|del\s+)semana\b""")
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(\d{1,2})\s+de\s+([a-záéíóúüñ]+)(?:\s+del?\s+(\d{2,4}))?\b""")
    // Día del mes suelto con artículo: "reunión el 15", "cita el 20 a las 18",
    // "entregar el 5 del mes". Antes "el 15" no casa con numericDatePattern (que exige
    // DD/MM con mes) y quedaba como residuo en el título; la hora suelta ("a las 10") se
    // aplicaba a HOY → la cita se programaba hoy en vez del día 15 (P1: día erróneo,
    // reunión perdida). El lookahead negativo evita colisionar con "el 15 de marzo" (lo
    // resuelve monthNameDate) y "el 15 de cada mes" (recurrencia mensual): no se admite
    // "de <palabra>" tras el número salvo la fórmula "del mes".
    private val dayOfMonthPattern = Regex("""(?i)\bel\s+(\d{1,2})(?:\s+del?\s+mes)?\b(?!\s*de\s+[a-záéíóúüñ])""")
    private val timePatterns = listOf(
        // Sufijo opcional "(horas?|hs)" tras la hora (con o sin meridiem) para consumir
        // "a las 9 horas" completo: antes "horas" quedaba como residuo en el titulo y,
        // peor, "9 horas" era robado como duracion (540 min falsos). Como grupo propio
        // (no meridiem), no altera la logica AM/PM ni marca meridiem explicito.
        // Grupo 3 opcional "y media"/"y cuarto": fracción sub-hora cotidiana en español
        // ("a las 9 y media" → 09:30, "a las 3 y cuarto" → 03:15). Antes "y media" caía
        // como residuo en el título y la hora quedaba en punto (reunión/cita 30 min mal).
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4])(?::([0-5]\d))?(?:\s+y\s+(media|cuarto))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada)?(?:\s*(horas?|hs))?\b"""),
        Regex("""(?i)\b([01]?\d|2[0-4]):([0-5]\d)\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b"""),
        Regex("""(?i)\b(0?[1-9]|1[0-2])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?)\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+)?mediod[ií]a\b"""),
        Regex("""(?i)\b(?:al\s+|a\s+la\s+)?medianoche\b""")
    )
    /**
     * Cantidad del recordatorio: dígitos o número escrito en español (simétrico con
     * la fecha relativa "en dos horas"). Antes solo se aceptaban dígitos, así que
     * "recuérdame una hora antes" / "dos horas antes" / "treinta minutos antes"
     * caían a `reminderOffsetMinutes=null` y la frase quedaba como residuo en el
     * título → el recordatorio nunca se programaba (el usuario olvidaba la cita).
     */
    private val writtenAmountPattern =
        """\d{1,3}|un\s+par\s+de|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veintiuno|treinta"""

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
        Regex("""(?i)\b(?:recu[eé]rdame|av[ií]same|notif[ií]came|recordatorio|no\s+dejes\s+que\s+olvide)\b""")
    private const val BARE_REMINDER_DEFAULT_OFFSET_MINUTES = 30
    private val durationPatterns = listOf(
        Regex("""(?i)\((\d{1,3})\s*(minutos?|min|horas?|hora)\)"""),
        Regex("""(?i)\b(?:durante|por)\s+(\d{1,3})\s*(minutos?|min|horas?|hora)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(minutos?|min)\b"""),
        Regex("""(?i)\b(\d{1,3})\s*(horas?)\b"""),
        // Compacto "Nh" (p. ej. "Trabajar 2h", "Estudiar 1h"). El \b final evita
        // casar "2horas" (h seguida de 'o' no es límite de palabra), así no roba
        // ni deja residuo frente al patrón completo "horas?".
        Regex("""(?i)\b(\d{1,3})\s*(h)\b""")
    )

    /**
     * Duraciones fraccionarias comunes en español sin dígitos: "media hora" (30 min) y
     * "(un) cuarto de hora" (15 min). Los patrones de dígitos no las capturan, así que
     * quedaban como residuo en el título y `durationMinutes` era null. "cuarto" requiere
     * "hora" después para no casar "cuarto" = habitación ("limpiar el cuarto").
     */
    private val fractionalDurationPattern =
        Regex("""(?i)\b(media\s+hora|(?:un\s+)?cuarto\s+(?:de\s+)?hora)\b""")

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
     */
    private val standalonePartOfDayPattern = Regex("""(?i)\b(?:a\s+la|de\s+la|por\s+la|en\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)\b""")
    private val standalonePartOfDayTimes = mapOf(
        "tarde" to LocalTime.of(15, 0),
        "noche" to LocalTime.of(21, 0),
        "madrugada" to LocalTime.of(4, 0),
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0)
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
     * Hora suelta con parte del día, sin "a las" ni rango: "Taller 9 de la tarde",
     * "Cena 9 de la noche", "Cita 10 de la mañana", "Evento 9 de la madrugada". Antes la
     * hora caía a la canónica de la parte del día (15:00/21:00/09:00/04:00) ignorando el
     * número, y éste quedaba como residuo en el título ("Taller 9"). Aquí se resuelve la
     * hora absoluta con su meridiem (tarde/noche → +12 si N<12; mañana/madrugada → AM, 12→0).
     * El patrón exige el conector "de la" para no colisionar con "9 de marzo" (fecha con mes
     * —lo resuelve monthNameDate) ni "el 9" aislado (dayOfMonthPattern); el lookahead negativo
     * descarta "9 de la mañana" seguido de un nombre de mes ("9 de la mañana de marzo" no es
     * una forma real, pero protege de ambigüedades). Admite minutos opcionales ("9:30 de la
     * tarde"), aunque esa forma ya la cubre timePatterns[1] + contexto PM; se deja por simetría.
     */
    private val standaloneHourPartOfDayPattern =
        Regex("""(?i)(?<![:\d])(\d{1,2})(?::([0-5]\d))?\s+de\s+la\s+(tarde|noche|madrugada|ma[nñ]ana|manana)(?!\s+de\s+[a-záéíóúüñ])""")

    private fun resolveStandaloneHourPartOfDay(match: MatchResult): LocalTime? {
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val min = match.groupValues[2].toIntOrNull() ?: 0
        if (h !in 0..24 || min !in 0..59) return null
        val part = match.groupValues[3].lowercase()
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
        "noviembre" to 11, "diciembre" to 12
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
        reminderPatterns.forEach { pattern ->
            pattern.findAll(working).forEach { working = working.replace(it.value, " ") }
        }

        // ¿El usuario pidió un recordatorio pero sin cantidad explícita? Sirve para
        // (a) limpiar el verbo del título y (b) aplicar un offset de respaldo cuando
        // haya fecha límite. Se detecta tras extraer los recordatorios con cantidad,
        // así "recuérdame 2 horas antes" (offset explícito) NO cae aquí.
        val hasBareReminderVerb = bareReminderVerbPattern.containsMatchIn(working)

        // Fecha relativa "en/dentro de N minutos/horas/días" (N = dígitos o palabra).
        val relativeMatch = relativePattern.find(working)
        val relativeDueAt = relativeMatch?.let { match ->
            val amount = parseWrittenNumber(match.groupValues[1]) ?: 0L
            val unit = match.groupValues[2].lowercase()
            val millis = when {
                unit.startsWith("min") -> amount * 60_000L
                unit.startsWith("hora") -> amount * 60 * 60_000L
                unit.startsWith("quincena") -> amount * 15 * 24 * 60 * 60_000L
                unit.startsWith("semana") -> amount * 7 * 24 * 60 * 60_000L
                // "bimestre"/"semestre"/"trimestre" contienen "mes": van antes que "mes".
                unit.startsWith("bimestre") -> amount * 60 * 24 * 60 * 60_000L
                unit.startsWith("trimestre") -> amount * 90 * 24 * 60 * 60_000L
                unit.startsWith("semestre") -> amount * 180 * 24 * 60 * 60_000L
                unit.startsWith("mes") -> amount * 30 * 24 * 60 * 60_000L
                unit.startsWith("a") || unit.contains("añ") -> amount * 365 * 24 * 60 * 60_000L
                else -> amount * 24 * 60 * 60_000L
            }
            now + millis
        }
        relativeMatch?.let { working = working.replace(it.value, " ") }

        // El "fin de semana" se detecta y se borra ANTES del período próximo para que
        // "fin de semana que viene" no active por error el patrón "semana que viene"
        // (que dejaría el residuo «fin de» en el título). El match se conserva para la
        // resolución de fecha posterior (weekendMatch != null).
        val weekendEarlyMatch = weekendPattern.find(working)
        weekendEarlyMatch?.let { working = working.replace(it.value, " ") }
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
        lastPeriodMatch?.let { working = working.replace(it.value, " ") }
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
        previousWeekdayMatch?.let { working = working.replace(it.value, " ") }
        previousWeekdayReversedMatch?.let { working = working.replace(it.value, " ") }

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
        agoMatch?.let { working = working.replace(it.value, " ") }

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
        endOfMonthEarlyMatch?.let { working = working.replace(it.value, " ") }
        midOfMonthEarlyMatch?.let { working = working.replace(it.value, " ") }
        startOfMonthEarlyMatch?.let { working = working.replace(it.value, " ") }

        // "esta semana" / "esta semana que viene": fin de la semana actual (próximo
        // domingo, ISO lunes→domingo). Se borra ANTES del período próximo para que
        // "semana" no active "semana que viene" y para limpiar "esta semana que viene".
        val thisWeekEarlyMatch = thisWeekPattern.find(working)
        val thisWeekDueAt = thisWeekEarlyMatch?.let {
            val sunday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            DateRules.toEpochMillis(sunday, LocalTime.of(9, 0), zone)
        }
        thisWeekEarlyMatch?.let { working = working.replace(it.value, " ") }

        // "principios de semana": el lunes más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        val startOfWeekEarlyMatch = startOfWeekPattern.find(working)
        val startOfWeekDueAt = startOfWeekEarlyMatch?.let {
            val monday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            DateRules.toEpochMillis(monday, LocalTime.of(9, 0), zone)
        }
        startOfWeekEarlyMatch?.let { working = working.replace(it.value, " ") }

        // "mediados de semana": el miércoles más cercano en hoy/futuro. Se borra ANTES
        // del período próximo para que "semana" no active "semana que viene".
        val midOfWeekEarlyMatch = midOfWeekPattern.find(working)
        val midOfWeekDueAt = midOfWeekEarlyMatch?.let {
            val wednesday = base.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            DateRules.toEpochMillis(wednesday, LocalTime.of(9, 0), zone)
        }
        midOfWeekEarlyMatch?.let { working = working.replace(it.value, " ") }

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
        nextMonthDayMatch?.let { working = working.replace(it.value, " ") }

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
        nextMonthDayReverseMatch?.let { working = working.replace(it.value, " ") }

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
        nextWeekWeekdayReverseMatch?.let { working = working.replace(it.value, " ") }

        // Orden inverso: "el lunes de la semana que viene". Misma resolución.
        val nextWeekWeekdayForwardMatch = nextWeekWeekdayForwardPattern.find(working)
        val nextWeekWeekdayForwardDueAt = nextWeekWeekdayForwardMatch?.let { m ->
            m.groupValues[1].toDayOfWeekOrNull()?.let { target ->
                nextWeekWeekdayDate(base.toLocalDate(), target, zone)
            }
        }
        nextWeekWeekdayForwardMatch?.let { working = working.replace(it.value, " ") }

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
        nextPeriodMatch?.let { working = working.replace(it.value, " ") }

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
        quincenaMatch?.let { working = working.replace(it.value, " ") }

        // La fecha relativa (relativePattern) tiene prioridad; luego los límites de mes
        // ("fin de mes"/"mediados de mes"); "esta semana"; "principios/mediados de semana";
        // la quincena; el período próximo es el respaldo final. Todos son días (no
        // min/hora) para combinarse con una hora explícita.
        // Fechas pasadas (ago/lastPeriod) tienen prioridad: son explícitas y no
        // deben sobrescribirse por una fecha futura ambigua. La hora explícita se
        // aplica sobre la fecha pasada (tarea vencida con hora).
        val effectiveRelativeDueAt =
            agoDueAt ?: lastPeriodDueAt ?: relativeDueAt ?: monthBoundaryDueAt ?:
            thisWeekDueAt ?: startOfWeekDueAt ?: midOfWeekDueAt ?: quincenaDueAt ?:
            nextMonthDayDueAt ?: nextMonthDayReverseDueAt ?:
            nextWeekWeekdayReverseDueAt ?: nextWeekWeekdayForwardDueAt ?: nextPeriodDueAt
        val relativeIsDays = (agoMatch != null || lastPeriodMatch != null ||
            relativeMatch != null || monthBoundaryDueAt != null ||
            thisWeekEarlyMatch != null || startOfWeekEarlyMatch != null || midOfWeekEarlyMatch != null ||
            quincenaMatch != null || nextMonthDayMatch != null || nextMonthDayReverseMatch != null ||
            nextWeekWeekdayReverseMatch != null || nextWeekWeekdayForwardMatch != null || nextPeriodMatch != null) &&
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
        val partOfDayMatch = partOfDayPattern.find(working)
        val partOfDayTime = partOfDayMatch?.let { partOfDayTimes[it.groupValues[1].lowercase()] }
        val standalonePartOfDayMatch = standalonePartOfDayPattern.find(working)
        val standalonePartOfDayKey = standalonePartOfDayMatch?.groupValues?.get(1)?.lowercase()
        val standalonePartOfDayTime = standalonePartOfDayKey?.let { standalonePartOfDayTimes[it] }
        val primeraHoraMatch = primeraHoraPattern.find(working)
        // Contexto PM: una parte del día de tarde/noche (explícita "esta tarde" o suelta "a la noche")
        // aplica offset +12 a una hora sin meridiem ("esta tarde a las 4" → 16:00).
        val partOfDayPmKeys = setOf("tarde", "noche")
        val hasPartOfDayPmContext =
            partOfDayMatch?.let { it.groupValues[1].lowercase() in partOfDayPmKeys } == true ||
            standalonePartOfDayKey in partOfDayPmKeys ||
            recurrence.partOfDayIsPm
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
            Regex("""(?i)\bantepasad[oa]\s+mañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(3)
            Regex("""(?i)\bpasado\s+mañana\b""").containsMatchIn(working) -> base.toLocalDate().plusDays(2)
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
                val nextExplicit = mv.contains("que viene") || mv.contains("próxim")
                weekdaySameDayCandidate = !nextExplicit && base.toLocalDate().dayOfWeek == target
                if (nextExplicit) nextWeekday(base.toLocalDate(), target)
                else nextWeekdayOrSame(base.toLocalDate(), target)
            }
            monthNameDate != null -> monthNameDate
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
                    runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { date ->
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
                mv.contains("mediodía") || mv.contains("mediodia") -> LocalTime.NOON to true
                mv.contains("medianoche") -> LocalTime.MIDNIGHT to true
                else -> {
                    var hour = match.groupValues[1].toInt()
                    val explicitMinute = match.groupValues[2].toIntOrNull()
                    // Los patrones tienen layout de grupos DISTINTO: timePattern[0]
                    // ("a las N") pone la fracción "y media/cuarto" en el grupo 3 y el
                    // meridiem en el 4; timePattern[1]/[2] (N:MM y Nam/Pm) ponen el
                    // meridiem en el grupo 3 (no hay fracción). El grupo 3 es, pues, O
                    // fracción O meridiem según el patrón: se disambigua por contenido.
                    val raw3 = match.groupValues.getOrNull(3)?.lowercase().orEmpty()
                    val raw4 = match.groupValues.getOrNull(4)?.lowercase().orEmpty()
                    val fraction = if (raw3 == "media" || raw3 == "cuarto") raw3 else ""
                    // "y media" = +30 min, "y cuarto" = +15 min sobre la hora en punto
                    // (sin minutos explícitos). "a las 9 y media" → 09:30, no 09:00.
                    val minute = explicitMinute ?: when (fraction) {
                        "media" -> 30
                        "cuarto" -> 15
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
                        // "de la tarde"/"de la noche" → 12h posterior; "de la mañana/madrugada" → am.
                        val isPm = meridiem == "pm" || meridiem == "delatarde" || meridiem == "delanoche"
                        val isAm = meridiem == "am" || meridiem == "delamañana" || meridiem == "delamanaana" || meridiem == "delamadrugada"
                        if (isPm && hour < 12) hour += 12
                        if (isAm && hour == 12) hour = 0
                        // "12 de la noche" = medianoche (00:00), no 12:00 del mediodía.
                        if (isPm && hour == 12 && meridiem == "delanoche") hour = 0
                        LocalTime.of(hour, minute) to meridiem.isNotEmpty()
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
        rangeMatch?.let { working = working.replace(it.value, " ") }
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
        standaloneHourPartOfDayMatch?.let { working = working.replace(it.value, " ") }
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
            ?: recurrence.partOfDayTime
            ?: primeraHoraMatch?.let { primeraHoraTime }
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
        // Duración fraccionaria sin dígitos ("media hora"/"cuarto de hora"): se computa
        // aparte y se elige la ocurrencia más a la izquierda respecto a la duración numérica.
        val fractionalMatch = fractionalDurationPattern.find(working)
        val durationMinutes = when {
            rangeDurationMinutes != null -> rangeDurationMinutes
            durationMatch != null && (fractionalMatch == null ||
                durationMatch.range.first <= fractionalMatch.range.first) -> {
                val amount = durationMatch.groupValues[1].toIntOrNull()
                val unit = durationMatch.groupValues[2].lowercase()
                amount?.let { (if (unit.startsWith("hora") || unit == "h") it * 60 else it).coerceIn(5, 24 * 60) }
            }
            fractionalMatch != null -> {
                val text = fractionalMatch.value.lowercase()
                (if (text.contains("media")) 30 else 15).coerceIn(5, 24 * 60)
            }
            else -> null
        }
        durationMatch?.let { match ->
            // "Reunión de 30 min": el "de" antes de la duración es conector, se elimina junto.
            // "durante"/"por" (de los patrones "durante/por N ...") también son conectores
            // y deben borrarse junto con la duración para no dejar residuo en el título.
            val withConnector = Regex(
                "(?i)\\b(?:de|durante|por)\\s+" + Regex.escape(match.value)
            )
            working = if (withConnector.containsMatchIn(working)) withConnector.replace(working, " ")
                else working.replace(match.value, " ")
        }
        fractionalMatch?.let { working = working.replace(it.value, " ") }

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
            .let { value -> partOfDayPattern.replace(value, " ") }
            .let { value -> timePatterns.fold(value) { acc, pattern -> pattern.replace(acc, " ") } }
            .let { value -> standalonePartOfDayPattern.replace(value, " ") }
            .let { value -> primeraHoraPattern.replace(value, " ") }
            .replace(Regex("""(?i)\bantepasad[oa]\s+mañana\b|\bpasado\s+mañana\b|\bmañana\b|\bhoy\b|\banteayer\b|\bantier\b|\bayer\b"""), " ")
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
            .replace(Regex("""(?i)\bantes\s+del?\b|\bpara\s+el\b|\bpara\s+mañana\b|\bhasta\s+el\b"""), " ")
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
            Regex("""(?i)\bcada\s+(\d{1,3})\s*semanas?\b""").find(working)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 366) ?: return null
                return n to m.range
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
                    g.contains("sábados") || g.contains("domingos")
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

        // "fines de semana" / "los findes" / "este finde" como recurrencia semanal de
        // sabado+domingo. Es la forma natural de "hago esto los findes" (estudiar,
        // limpiar, deporte). Antes quedaba sin recurrencia y sin fecha -> la tarea
        // repetitiva se olvidaba o aparecia una sola vez. La primera ocurrencia la
        // resuelve la rama WEEKLY+days (proximo sabado o domingo). Distinto del
        // singular "fin de semana" (fecha unica, proximo sabado): el plural = habito.
        val weekendRecurrencePattern =
            Regex("""(?i)\b(?:cada\s+)?(?:los\s+)?fines\s+de\s+semana\b|\b(?:cada\s+)?(?:los\s+|este\s+)?findes?\b""")
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
        val monthlyDayPattern =
            Regex("""(?i)\b(?:el|los)?\s*(\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes(?:es)?\b""")
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
        val writtenNumberGroup =
            "un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|" +
            "catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|veintiuno|treinta"
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

        // "cada quincena" / "quincenalmente" / "todas las quincenas": cadencia
        // quincenal cotidiana en español (nóminas, pagos, reportes cada 15 días).
        // `intervalPattern` solo admite dígitos ("cada 2 semanas"), así que la forma
        // con palabra "quincena" caía a NONE y la tarea recurrente nacía sin fecha
        // (invisible en What Now/planificador, recordatorio jamás disparaba). Se mapea
        // a WEEKLY interval=2 (cada 2 semanas ≈ quincena) sin añadir enum ni migración:
        // representación honesta y reutiliza el avance semanal existente.
        Regex("""(?i)\b(?:cada\s+quincena|quincenalmente|todas\s+las\s+quincenas)\b""").find(working)?.let { match ->
            phrases += match.range
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 2, emptyList(), phrases)
        }

        val fixedPatterns = listOf(
            Regex("""(?i)\btodos\s+los\s+d[ií]as\b|\bcada\s+d[ií]a\b|\bdiariamente\b""") to RecurrenceFrequency.DAILY,
            Regex("""(?i)\btodas\s+las\s+[sS]emanas\b|\bcada\s+[sS]emana\b|\bsemanalmente\b""") to RecurrenceFrequency.WEEKLY,
            Regex("""(?i)\btodos\s+los\s+meses\b|\bcada\s+mes\b|\bmensualmente\b""") to RecurrenceFrequency.MONTHLY,
            Regex("""(?i)\btodos\s+los\s+a[nñ]os\b|\bcada\s+a[nñ]o\b|\banualmente\b""") to RecurrenceFrequency.YEARLY
        )
        fixedPatterns.forEach { (pattern, frequency) ->
            pattern.find(working)?.let { match ->
                phrases += match.range
                return RecurrenceResult(frequency, 1, emptyList(), phrases)
            }
        }

        return base
    }

    private fun parseMonthNameDate(today: LocalDate, match: MatchResult): LocalDate? {
        val day = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
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
        val isNext = t.contains("que viene") || t.contains("próxim") || t.contains("proxim")
        if (isNext) return today.plusMonths(1)
        val kind = when {
            t.contains("fin") || t.contains("finales") -> "end"
            t.contains("mediados") || t.contains("mediado") -> "mid"
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
            val m = Regex("""(?i)\bmañana\b""").find(working, idx) ?: return false
            val prefix = working.substring(0, m.range.first)
            if (!timeMarker.containsMatchIn(prefix)) return true
            idx = m.range.last + 1
        }
    }

    private fun Int.toDayOfWeekOrNull(): DayOfWeek? =
        if (this in 1..7) DayOfWeek.of(this) else null

    /** Convierte un grupo capturado (dígitos o número escrito en español) a Long. */
    private fun parseWrittenNumber(raw: String): Long? {
        raw.toLongOrNull()?.let { return it }
        return when (raw.lowercase().trim()) {
            "un par de" -> 2L
            "un", "una", "uno" -> 1L
            "dos" -> 2L
            "tres" -> 3L
            "cuatro" -> 4L
            "cinco" -> 5L
            "seis" -> 6L
            "siete" -> 7L
            "ocho" -> 8L
            "nueve" -> 9L
            "diez" -> 10L
            "once" -> 11L
            "doce" -> 12L
            "trece" -> 13L
            "catorce" -> 14L
            "quince" -> 15L
            "dieciséis" -> 16L
            "dieciseis" -> 16L
            "diecisiete" -> 17L
            "dieciocho" -> 18L
            "diecinueve" -> 19L
            "veinte" -> 20L
            "veintiuno" -> 21L
            "treinta" -> 30L
            else -> null
        }
    }
}

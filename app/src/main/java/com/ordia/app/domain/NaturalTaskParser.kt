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
     */
    private val relativePattern = Regex(
        """(?i)\b(?:en|dentro\s+de)\s+(un\s+par\s+de|\d{1,3}|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|diecis[eé]is|diecisiete|dieciocho|diecinueve|veinte|treinta)\s*(minutos?|mins?|horas?|d[ií]as?|semanas?|quincenas?|mes(?:es)?|bimestres?|trimestres?|semestres?|a[nñ]os?)\b"""
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
     */
    private val endOfMonthPattern = Regex("""(?i)\b(?:a\s+)?fin(?:ales|es)?\s+(?:de\s+|del\s+)mes\b""")
    private val midOfMonthPattern = Regex("""(?i)\b(?:a\s+)?mediados?\s+(?:de\s+|del\s+)mes\b""")
    private val startOfMonthPattern = Regex("""(?i)\b(?:a\s+)?principios?\s+(?:de\s+|del\s+)mes\b""")
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
    private val monthNamePattern = Regex("""(?i)\b(?:el\s+)?(\d{1,2})\s+de\s+([a-záéíóúüñ]+)(?:\s+de\s+(\d{2,4}))?\b""")
    private val timePatterns = listOf(
        // Sufijo opcional "(horas?|hs)" tras la hora (con o sin meridiem) para consumir
        // "a las 9 horas" completo: antes "horas" quedaba como residuo en el titulo y,
        // peor, "9 horas" era robado como duracion (540 min falsos). Como grupo propio
        // (no meridiem), no altera la logica AM/PM ni marca meridiem explicito.
        Regex("""(?i)\ba\s+las\s+([01]?\d|2[0-4])(?::([0-5]\d))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de\s+la\s+ma[nñ]ana|de\s+la\s+tarde|de\s+la\s+noche|de\s+la\s+madrugada)?(?:\s*(horas?|hs))?\b"""),
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
     * Rango horario "de H1 a H2" / "H1 a H2 horas" (citas, clases, reuniones con ventana).
     * Implica duración = (H2-H1)*60 min y se elimina del título. Para no falsear datos
     * (p. ej. "comprar de 2 a 5 entradas") solo se acepta cuando hay evidencia de horario:
     * unidad final ("horas"/"hs"/"h") o alguna hora >= 13 (formato 24h inequívoco).
     * Antes "de 18 a 20 horas" dejaba "20 horas" como duración de 20h (1200 min, falso).
     * No fija hora de inicio (ambigua sin meridiem); solo la duración, de forma honesta.
     */
    private val timeRangePattern =
        Regex("""(?i)\b(?:de\s+)?(\d{1,2})\s*(?:a|-)\s*(\d{1,2})(\s*(?:horas?|hs|h))?\b""")

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
     */
    private val standalonePartOfDayPattern = Regex("""(?i)\b(?:a\s+la|de\s+la|por\s+la)\s+(tarde|noche|madrugada|ma[nñ]ana)\b""")
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
        val previousWeekdayMatch = previousWeekdayPattern.find(working)
        val previousWeekdayReversedMatch = previousWeekdayReversedPattern.find(working)
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
                val today = base.toLocalDate()
                val lastDayThis = today.withDayOfMonth(today.lengthOfMonth())
                val target = if (today.isBefore(lastDayThis)) lastDayThis
                    else lastDayThis.plusMonths(1).withDayOfMonth(
                        lastDayThis.plusMonths(1).lengthOfMonth())
                DateRules.toEpochMillis(target, LocalTime.of(9, 0), zone)
            }
            midOfMonthEarlyMatch != null -> {
                val today = base.toLocalDate()
                val fifteenthThis = today.withDayOfMonth(15)
                val target = if (today.isBefore(fifteenthThis)) fifteenthThis
                    else fifteenthThis.plusMonths(1)
                DateRules.toEpochMillis(target, LocalTime.of(9, 0), zone)
            }
            startOfMonthEarlyMatch != null -> {
                val today = base.toLocalDate()
                val firstThis = today.withDayOfMonth(1)
                // Si hoy es 1 (o ya pasó el 1 este mes), rueda al 1 del mes siguiente.
                val target = if (today.isAfter(firstThis)) firstThis.plusMonths(1) else firstThis
                DateRules.toEpochMillis(target, LocalTime.of(9, 0), zone)
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

        // La fecha relativa (relativePattern) tiene prioridad; luego los límites de mes
        // ("fin de mes"/"mediados de mes"); "esta semana"; "principios/mediados de semana";
        // el período próximo es el respaldo final. Todos son días (no min/hora) para
        // combinarse con una hora explícita.
        // Fechas pasadas (ago/lastPeriod) tienen prioridad: son explícitas y no
        // deben sobrescribirse por una fecha futura ambigua. La hora explícita se
        // aplica sobre la fecha pasada (tarea vencida con hora).
        val effectiveRelativeDueAt =
            agoDueAt ?: lastPeriodDueAt ?: relativeDueAt ?: monthBoundaryDueAt ?:
            thisWeekDueAt ?: startOfWeekDueAt ?: midOfWeekDueAt ?: nextPeriodDueAt
        val relativeIsDays = (agoMatch != null || lastPeriodMatch != null ||
            relativeMatch != null || monthBoundaryDueAt != null ||
            thisWeekEarlyMatch != null || startOfWeekEarlyMatch != null || midOfWeekEarlyMatch != null ||
            nextPeriodMatch != null) &&
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
        val monthNameMatch = monthNamePattern.find(working)
        // Solo cuenta como fecha si el mes es válido: así "8 de la manana" (sufijo de
        // hora, mes "la" inexistente) no sombra y anula la resolución de fecha de
        // repeticiones mensuales/semanales con hora.
        val monthNameDate = monthNameMatch?.let { parseMonthNameDate(base.toLocalDate(), it) }
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
            standalonePartOfDayKey in partOfDayPmKeys
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
                weekdaySameDayCandidate = base.toLocalDate().dayOfWeek == target
                nextWeekdayOrSame(base.toLocalDate(), target)
            }
            monthNameDate != null -> monthNameDate
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
                    val minute = match.groupValues[2].toIntOrNull() ?: 0
                    // "a las 24" / "24:00" = medianoche (00:00), forma común en horarios.
                    // Se marca como meridiem explícito para evitar que el contexto PM de
                    // parte del día aplique un offset (24 ya es absoluto).
                    if (hour == 24) {
                        LocalTime.MIDNIGHT to true
                    } else {
                        val meridiem = match.groupValues[3].lowercase().replace(".", "").replace(" ", "")
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
        val explicitTime = explicitTimeData?.first
        val hasExplicitMeridiem = explicitTimeData?.second == true
        // Un tiempo explícito tiene prioridad sobre la hora canónica de la parte del día.
        // Si la hora explícita vino sin meridiem (p.ej. "a las 4") y hay contexto PM de
        // parte del día ("esta tarde"/"a la noche"), se aplica el offset +12 ("esta tarde
        // a las 4" → 16:00, no 04:00).
        val parsedTime = explicitTime?.let { t ->
            if (!hasExplicitMeridiem && hasPartOfDayPmContext && t.hour in 1..11)
                t.plusHours(12) else t
        } ?: partOfDayTime ?: standalonePartOfDayTime ?: primeraHoraMatch?.let { primeraHoraTime }
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

        // Duración: no se aplica a "en N minutos" (esa es fecha relativa, ya eliminada).
        // Rango horario "de H1 a H2 [horas]": se procesa primero para que el segundo
        // número no sea robado como duración numérica ("de 18 a 20 horas" → 20h falsas).
        val rangeMatch = timeRangePattern.find(working)?.let { m ->
            val start = m.groupValues[1].toIntOrNull()
            val end = m.groupValues[2].toIntOrNull()
            // Rango horario sin unidad ("de 9 a 11") y ambas horas < 13 (formato 12h
            // ambiguo): se acepta SOLO si al rango no le sigue un sustantivo de cantidad
            // ("entradas", "personas"). Si va seguido de fin de cadena o de un conector/
            // preposición/puntuación ("con Juan", "y luego…", ", luego…") se entiende
            // como ventana horaria. Esto distingue "clase de 9 a 11" (horario) de
            // "comprar de 2 a 5 entradas" (cantidad) sin inventar IA ni romper 24h.
            // Followers seguros ampliados: días de la semana y sus artículos
            // ("el viernes"), días relativos ("mañana") y marcadores de parte del día
            // ("a la tarde", "por la noche") que antes dejaban residuo.
            val followedByCount = m.range.last + 1 < working.length &&
                !Regex("""^\s*(?:,|\.|;|:|!|\?|y\b|o\b|con\b|de\b|del\b|en\b|para\b|hasta\b|desde\b|luego\b|después\b|despues\b|pero\b|porque\b|por\b|sin\b|sobre\b|a\b|al\b|el\b|la\b|los\b|las\b|un\b|una\b|mañana\b|manana\b|hoy\b|ayer\b|anteayer\b|lunes\b|martes\b|miércoles\b|miercoles\b|jueves\b|viernes\b|sábado\b|sabado\b|domingo\b|$)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(working.substring(m.range.last + 1))
            if (start != null && end != null && end > start && end <= 24 &&
                start in 0..23 && (end - start) * 60 <= 24 * 60 &&
                (m.groupValues[3].isNotEmpty() || start >= 13 || end >= 13 ||
                    (!followedByCount && end - start in 1..11))
            ) m else null
        }
        val rangeDurationMinutes = rangeMatch?.let { (it.groupValues[2].toInt() - it.groupValues[1].toInt()) * 60 }
            ?.coerceIn(5, 24 * 60)
        rangeMatch?.let { working = working.replace(it.value, " ") }

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
            .replace(Regex("""(?i)\bpasado\s+mañana\b|\bmañana\b|\bhoy\b|\banteayer\b|\bantier\b|\bayer\b"""), " ")
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
            .replace(Regex("""(?i)\bantes\s+del?\b|\bpara\s+el\b|\bpara\s+mañana\b|\bhasta\s+el\b"""), " ")
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
            reminderOffsetMinutes = reminderOffsetMinutes,
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
        val monthlyDayOfMonth: Int? = null
    )

    private fun parseRecurrence(working: String): RecurrenceResult {
        val base = RecurrenceResult(RecurrenceFrequency.NONE, 1, emptyList(), emptyList())
        val phrases = mutableListOf<IntRange>()

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
        val dayListPattern =
            Regex("""(?i)\b(?:todos\s+los|cada|los)\s+((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?)(?:\s*(?:,|y)?\s*(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bados?|domingos?))*)\b""")
        val dayNameRegex = Regex("""(?i)lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo""")
        val weeklyMatch = dayListPattern.find(working)
        if (weeklyMatch != null) {
            val days = dayNameRegex.findAll(weeklyMatch.groupValues[1])
                .mapNotNull { it.value.toDayOfWeekOrNull()?.value }
                .distinct().sorted().toList()
            if (days.isNotEmpty()) {
                phrases += weeklyMatch.range
                return RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, days, phrases)
            }
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
            return RecurrenceResult(RecurrenceFrequency.WEEKLY, 1, listOf(6, 7), phrases)
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

        // "cada N días/semanas/meses/años"
        val intervalPattern = Regex("""(?i)\bcada\s+(\d{1,3})\s*(d[ií]as?|semanas?|meses?|a[nñ]os?)\b""")
        intervalPattern.find(working)?.let { match ->
            val interval = match.groupValues[1].toIntOrNull()?.coerceIn(1, 366) ?: return@let
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
        val day = match.groupValues[1].toIntOrNull() ?: return null
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
        val timeMarker = Regex("""(?i)(?:de|por|a)\s+la\s+$|\besta\s+$""")
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

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import com.ordia.app.domain.RecurrenceEngine
import java.time.ZoneId
import java.time.ZonedDateTime

fun main() {
    // Jueves 2026-08-20 12:00 ART (UTC-3).
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    data class Case(val text: String, val note: String? = null)
    // Audit de palabras de cadencia. Buscamos: (a) NONE (rutina olvidada),
    // (b) intervalo/período INCORRECTO, (c) residuo en el título.
    val cases = listOf(
        // Adjetivos de cadencia cotidianos — deben recurrir.
        Case("pago quincenal", "quincenal adjetivo"),
        Case("renta mensual", "mensual adjetivo"),
        Case("reporte semanal", "semanal adjetivo"),
        Case("renovación anual", "anual adjetivo"),
        Case("impuesto bimestral", "bimestral adjetivo"),
        Case("declaración trimestral", "trimestral adjetivo"),
        Case("cierre semestral", "semestral adjetivo"),
        Case("informe cuatrimestral", "cuatrimestral adjetivo"),
        // Formas adverbiales -mente.
        Case("pagar quincenalmente"),
        Case("reportar mensualmente"),
        Case("revisar semanalmente"),
        // Sustantivos de cadencia con "cada".
        Case("pago cada quincena"),
        Case("renta cada mes"),
        Case("reporte cada semana"),
        Case("renovación cada año"),
        Case("impuesto cada bimestre"),
        Case("declaración cada trimestre"),
        Case("cierre cada semestre"),
        Case("informe cada cuatrimestre"),
        // Intervalos explícitos.
        Case("medicación cada 8 horas"),
        Case("reunión cada 2 semanas"),
        Case("pago cada 15 días"),
        Case("renta cada 3 meses"),
        // Candidatos a GAP (posiblemente olvidados / mal computados):
        Case("pago decenal", "decenal = cada 10 días (¿reconocido?)"),
        Case("pago catorcenal", "catorcenal = cada 14 días (¿reconocido?)"),
        Case("evento decenal", "decenal otra palabra"),
        Case("pago cada decena", "cada decena (sustantivo)"),
        Case("reunión cada década", "cada década (10 años)"),
        Case("reunión decenalmente", "decenalmente adverbio"),
        Case("pago catorcenal", "catorcenal"),
        Case("cita quincenal los lunes y viernes", "quincenal + días"),
        Case("pago bisemanal", "bisemanal ambiguo"),
        Case("medicación cada doce horas", "cada doce horas escrito"),
        Case("reunión cada dos semanas los lunes", "cada dos semanas + días"),
        // "cada N" sin unidad — ambiguo, debería NONE.
        Case("reunión cada 2"),
        // Doble cadencia — ¿cuál gana?
        Case("pago mensual cada 15 días", "conflicto mensual vs cada 15 días"),
        // No-regresión: no es cadencia.
        Case("comprar pastillas cada mes", "cada mes + título"),
        Case("leer el diario", "diario sustantivo (no recurrencia)")
    )

    println("=== CADENCE AUDIT (now=2026-08-20 12:00 ART) ===")
    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val rec = r.recurrence
        val flag = if (rec == RecurrenceFrequency.NONE && shouldRecur(c.text)) " <<< POSSIBLE GAP (NONE)" else ""
        println("[${c.text}] -> rec=$rec int=${r.recurrenceInterval} days='${r.recurrenceDays}' title='${r.title}'$flag")
        if (c.note != null) println("    # ${c.note}")
    }

    println()
    println("=== RECURRENCE ENGINE EDGE-CASE AUDIT ===")
    recurrenceEngineAudit(zone)
}

fun recurrenceEngineAudit(zone: ZoneId) {
    val dt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE").withZone(zone)
    fun zdt(y: Int, m: Int, d: Int, hh: Int = 9, mm: Int = 0) =
        ZonedDateTime.of(y, m, d, hh, mm, 0, 0, zone).toInstant().toEpochMilli()

    data class ECase(val label: String, val due: Long, val completedAt: Long, val freq: RecurrenceFrequency, val interval: Int, val days: String)
    val eCases = listOf(
        // WEEKLY interval=2, days [1,3] (Lun, Mié): cada 2 semanas.
        ECase("biweekly Mon/Wed from Mon", zdt(2026,8,17), zdt(2026,8,17,10), RecurrenceFrequency.WEEKLY, 2, "1,3"),
        ECase("biweekly Mon/Wed from Wed", zdt(2026,8,19), zdt(2026,8,19,10), RecurrenceFrequency.WEEKLY, 2, "1,3"),
        ECase("weekly Mon/Wed/Fri from Fri", zdt(2026,8,21), zdt(2026,8,21,10), RecurrenceFrequency.WEEKLY, 1, "1,3,5"),
        // MONTHLY día 31 saltando febrero.
        ECase("monthly day-31 Jan->?", zdt(2026,1,31), zdt(2026,1,31,10), RecurrenceFrequency.MONTHLY, 1, ""),
        ECase("monthly day-30 Jan->Feb", zdt(2026,1,30), zdt(2026,1,30,10), RecurrenceFrequency.MONTHLY, 1, ""),
        // MONTHLY EOM no salta febrero.
        ECase("monthly EOM Jan->Feb", zdt(2026,1,31), zdt(2026,1,31,10), RecurrenceFrequency.MONTHLY, 1, "EOM"),
        // MONTHLY ordinal "1er lunes" Sept->Oct (c.216 anti-deriva).
        ECase("monthly 1st-Mon Sep->Oct", zdt(2026,9,7), zdt(2026,9,7,10), RecurrenceFrequency.MONTHLY, 1, "1:1"),
        // YEARLY 29-feb bisiesto.
        ECase("yearly Feb29 2024->2028", zdt(2024,2,29), zdt(2024,2,29,10), RecurrenceFrequency.YEARLY, 1, ""),
        // Late completion: due ya pasada, completado 5 días tarde (WEEKLY).
        ECase("weekly late-complete", zdt(2026,8,10), zdt(2026,8,17,10), RecurrenceFrequency.WEEKLY, 1, "1"),
        // HOURLY medicación.
        ECase("hourly every 8h", zdt(2026,8,20,8), zdt(2026,8,20,8,1), RecurrenceFrequency.HOURLY, 8, "")
    )

    eCases.forEach { c ->
        val task = com.ordia.app.data.local.TaskEntity(
            title = c.label, dueAt = c.due, reminderAt = null,
            recurrence = c.freq, recurrenceInterval = c.interval, recurrenceDays = c.days
        )
        val next = RecurrenceEngine.nextOccurrence(task, c.completedAt, zone)
        val nextDue = next?.dueAt
        val s = nextDue?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        val dueStr = dt.format(java.time.Instant.ofEpochMilli(c.due))
        val compStr = dt.format(java.time.Instant.ofEpochMilli(c.completedAt))
        println("[${c.label}] due=$dueStr completed=$compStr => next=$s")
    }
}


// Heurística: el texto parece pedir recurrencia (contiene palabra de cadencia o "cada ...").
fun shouldRecur(text: String): Boolean {
    val t = text.lowercase()
    val cadenceWords = listOf(
        "quincenal", "quincenalmente", "mensual", "mensualmente", "semanal", "semanalmente",
        "anual", "anualmente", "bimestral", "trimestral", "semestral", "cuatrimestral",
        "decenal", "catorcenal", "bisemanal", "decenalmente"
    )
    if (cadenceWords.any { t.contains(it) }) return true
    if (Regex("""cada\s+\S""").containsMatchIn(t)) return true
    return false
}

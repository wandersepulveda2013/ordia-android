import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.data.local.RecurrenceFrequency
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val now = ZonedDateTime.of(2026, 8, 16, 9, 0, 0, 0, ZoneId.of("America/Bogota")).toInstant().toEpochMilli()
    val zone = ZoneId.of("America/Bogota")
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String, val expectFreq: RecurrenceFrequency, val expectInterval: Int? = null, val note: String = "")

    val cases = listOf(
        // cadencias mensuales plurimensuales
        Case("pago bimestral", RecurrenceFrequency.MONTHLY, 2),
        Case("cada bimestre", RecurrenceFrequency.MONTHLY, 2),
        Case("cada dos meses", RecurrenceFrequency.MONTHLY, 2),
        Case("informe trimestral", RecurrenceFrequency.MONTHLY, 3),
        Case("cada trimestre", RecurrenceFrequency.MONTHLY, 3),
        Case("cierre semestral", RecurrenceFrequency.MONTHLY, 6),
        Case("cada semestre", RecurrenceFrequency.MONTHLY, 6),
        Case("declaración cuatrimestral", RecurrenceFrequency.MONTHLY, 4),
        // bisemanal / quincenal
        Case("reunión bisemanal", RecurrenceFrequency.WEEKLY, 2),
        Case("cada dos semanas", RecurrenceFrequency.WEEKLY, 2),
        Case("pago quincenal", RecurrenceFrequency.DAILY, 15),
        Case("cada quincena", RecurrenceFrequency.DAILY, 15),
        // bimensual (ambiguo - solo informativo)
        Case("pago bimensual", RecurrenceFrequency.NONE, null, "bimensual ambiguo"),
        // combos con días
        Case("gym lunes y jueves", RecurrenceFrequency.WEEKLY),
        Case("cada dos semanas los lunes", RecurrenceFrequency.WEEKLY, 2),
        Case("fútbol domingos", RecurrenceFrequency.WEEKLY),
        Case("estudio fines de semana", RecurrenceFrequency.WEEKLY),
        Case("trabajo de lunes a viernes", RecurrenceFrequency.WEEKLY),
        // sub-diarios
        Case("medicina cada 8 horas", RecurrenceFrequency.HOURLY, 8),
        Case("tomar cada hora", RecurrenceFrequency.HOURLY, 1),
        Case("gárgaras cada 15 minutos", RecurrenceFrequency.NONE),
        // cada N a secas (mensual implícito)
        Case("reporte cada 15", RecurrenceFrequency.MONTHLY, 1),
        Case("nómina cada 1", RecurrenceFrequency.MONTHLY, 1),
        // alternos
        Case("cada otro día", RecurrenceFrequency.DAILY, 2),
        Case("un día sí y otro no", RecurrenceFrequency.DAILY, 2),
        Case("días alternos", RecurrenceFrequency.DAILY, 2),
        Case("día por medio", RecurrenceFrequency.DAILY, 2),
        // ordinales
        Case("cada tercer día", RecurrenceFrequency.DAILY, 3),
        Case("cada cuarto día", RecurrenceFrequency.DAILY, 4),
        // parte del día
        Case("meditar cada mañana", RecurrenceFrequency.DAILY, 1),
        Case("pasear cada tarde", RecurrenceFrequency.DAILY, 1),
        // --- POSIBLES GAPS ---
        Case("pago mensual el 15", RecurrenceFrequency.MONTHLY, 1, "mensual+fecha"),
        Case("renta mensual", RecurrenceFrequency.MONTHLY, 1),
        Case("ración diaria", RecurrenceFrequency.DAILY, 1, "diaria adjetivo"),
        Case("reunión semanal los lunes", RecurrenceFrequency.WEEKLY, 1, "semanal+días"),
        Case("reporte mensual el primer lunes", RecurrenceFrequency.MONTHLY, 1, "mensual+ordinal"),
        Case("cada 1 y 15 del mes", RecurrenceFrequency.MONTHLY, 1, "días duales"),
        Case("todos los lunes", RecurrenceFrequency.WEEKLY, 1),
        Case("sesión quincenal los lunes", RecurrenceFrequency.WEEKLY, 2, "quincenal+días"),
        Case("cada 30 del mes", RecurrenceFrequency.MONTHLY, 1),
        Case("anualmente", RecurrenceFrequency.YEARLY, 1),
        Case("cumpleaños anual", RecurrenceFrequency.YEARLY, 1)
    )

    var fails = 0
    cases.forEach { c ->
        val r = NaturalTaskParser.parse(c.text, now, zone)
        val okFreq = r.recurrence == c.expectFreq
        val okInt = c.expectInterval == null || r.recurrenceInterval == c.expectInterval
        val ok = okFreq && okInt
        if (!ok) fails++
        val due = r.dueAt?.let { fmt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("%-7s freq=%-8s int=%-3s due=%-17s title='%s' | %-32s %s".format(
            if (ok) "OK" else "FAIL", r.recurrence, r.recurrenceInterval, due, r.title, c.text, c.note))
    }
    println("\nFAILS: $fails / ${cases.size}")
}

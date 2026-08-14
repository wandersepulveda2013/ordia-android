import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    // Miércoles 2026-08-19 12:00 ART (UTC-3). Lunes 17/08 primer día de la semana ISO.
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String, val note: String? = null)
    val cases = listOf(
        Case("reunión el próximo lunes", "próximo lunes con el"),
        Case("reunión próximo lunes", "próximo lunes sin el"),
        Case("reunión el lunes que viene", "lunes que viene"),
        Case("llamar la semana que viene", "la semana que viene"),
        Case("enviar informe el fin de semana", "fin de semana como fecha"),
        Case("revisar el fin de semana que viene", "fin de semana que viene"),
        Case("comprar anteayer pan", "anteayer pasado"),
        Case("reunión antier", "antier mx"),
        Case("llamar en un rato", "en un rato"),
        Case("llamar en un momento", "en un momento"),
        Case("reunión el próximo mes", "próximo mes con el"),
        Case("reunión próximo mes", "próximo mes"),
        Case("factura a fin de mes", "a fin de mes con a"),
        Case("enviar a mediados del mes que viene", "mediados del mes que viene"),
        Case("reunión el día de mañana", "el día de mañana pleonastico"),
        Case("reunión el día de hoy", "el día de hoy pleonastico"),
        Case("reunión para el día de mañana", "para el día de mañana"),
        Case("pagar a primeros de mes", "a primeros de mes"),
        Case("entregar a finales de mes", "a finales de mes"),
        Case("reunión el 15 del mes que viene", "el 15 del mes que viene"),
        Case("entregar a fin de la semana", "a fin de la semana"),
        Case("reunión el próximo viernes", "próximo viernes"),
        Case("llamar esta noche", "esta noche compacta"),
        Case("reunión mañana al mediodía", "mañana al mediodía")
    )

    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[${c.text}] -> due=$due title='${r.title}' rec=${r.recurrence} dur=${r.durationMinutes} rem=${r.reminderOffsetMinutes}")
        if (c.note != null) println("    # ${c.note}")
    }
}

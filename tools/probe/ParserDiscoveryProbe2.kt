import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    // now = jueves 2026-08-13 12:00
    val now = ZonedDateTime.of(2026, 8, 13, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    val cases = listOf(
        "pagar la renta el 15 de cada mes",
        "pagar la renta el día 15 de cada mes",
        "gym de lunes a viernes a las 7",
        "informe a más tardar el viernes",
        "entregar a más tardar mañana",
        "llamar de hoy en ocho",
        "cita de hoy en ocho días",
        "pago a principios de mes",
        "pago a mediados de mes",
        "reunión para el lunes",
        "llamar a juan para mañana",
        "cita el lunes en la mañana",
        "reunión el lunes en la noche",
        "revisión la semana que entra",
        "cita la semana entrante",
        "medicina cada ocho horas",
        "medicina cada 8 horas",
        "cambiar aceite cada tres meses",
        "gym lunes miércoles y viernes",
        "llamar mañana temprano",
        "entregar mañana a primera hora",
        "pago los primeros días del mes",
        "reunión este próximo lunes",
        "informe para fin de mes",
        "revisión para la semana que viene"
    )

    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}/${r.recurrenceInterval} days='${r.recurrenceDays}' dur=${r.durationMinutes} rem=${r.reminderOffsetMinutes}")
    }
}

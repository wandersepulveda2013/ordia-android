import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 13, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    val cases = listOf(
        "llamar de hoy en ocho",
        "cita de hoy en ocho días",
        "reunión de hoy en dos semanas",
        "pago de hoy en un mes",
        "entregar de hoy en 8 horas",
        "llamar de hoy en 15 minutos",
        "revisar de hoy en quince",
        "cita de hoy en quince días",
        "informe de hoy en 30 días",
        "de hoy en adelante",
        "entrevista en adelante",
        "llamar de hoy en adelante",
        "llamar en ocho",
        "dentro de ocho días",
        "de aquí a ocho días"
    )
    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}")
    }
}

import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 7, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String)
    val cases = listOf(
        Case("reunión hacia las nueve de la noche"),
        Case("reunión sobre las diez de la mañana"),
        Case("llamar hacia las tres de la tarde"),
        Case("reunión a las nueve en punto"),
        Case("cita a las tres en punto de la tarde"),
        Case("llamar a las 9 en punto"),
        Case("reunión a las nueve y media de la noche"),
        Case("cita a las diez de la noche"),
        Case("cita a las nueve menos cuarto de la mañana"),
        Case("reunión mañana por la tarde"),
        Case("llamar mañana por la mañana"),
        Case("reunión mañana por la noche"),
        Case("reunión pasado mañana"),
        Case("reunión pasado mañana a las tres"),
        Case("reunión este viernes"),
        Case("llamar este viernes a las cuatro"),
        Case("almuerzo a la una"),
        Case("almuerzo a la una y media"),
        Case("cita a la una de la tarde"),
        Case("reunión al mediodía"),
        Case("reunión a mediodía"),
        Case("cita a la medianoche"),
        Case("cita a medianoche"),
        Case("reunión mañana a las tres"),
        Case("reunión mañana a las 15"),
        Case("reunión de tres cuartos de hora"),
        Case("llamar en 30 minutos"),
        Case("llamar en media hora"),
        Case("reunión en dos horas"),
        Case("gym cada lunes a las 6"),
        Case("medicación cada 8 horas"),
        Case("reunión semanal los martes"),
        Case("gym todos los días a las 6"),
        Case("reunión todas las semanas"),
        Case("reunión a las 3pm"),
        Case("reunión a las 3 pm"),
        Case("reunión a las 3 p.m."),
        Case("reunión a las 15:30"),
        Case("cita a las 09:00"),
        Case("llamar a juan mañana a las tres"),
        Case("ver a maría el viernes"),
        Case("reunión hoy a las cuatro"),
        Case("llamar hoy"),
        Case("reunión antes de las cinco"),
        Case("reunión después de las tres")
    )

    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[${c.text}] -> due=$due title='${r.title}' rec=${r.recurrence} dur=${r.durationMinutes} rem=${r.reminderOffsetMinutes}")
    }
}

import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.data.local.TaskPriority
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santiago")
    // Lunes 2026-08-10 09:00 (lunes real) como "now"
    val now = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val p = NaturalTaskParser
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    val cases = listOf(
        "reunión de lunes hasta viernes",
        "reunión del lunes hasta el viernes",
        "reunión del lunes hasta viernes",
        "viaje del 15 hasta el 20 de diciembre",
        "curso del 3 hasta el 8 de enero",
        "gym de lunes hasta viernes",
        "reunión de lunes a viernes",
        "reunión del lunes al viernes",
        "reunión de aquí al viernes",
        "reunión de aquí al viernes a las 15",
        "trabajo de aquí a viernes",
        "reunión a eso de las 3",
        "llamar a eso de las 15:00",
        "reunión a eso de las 3 de la tarde",
        "pasar recado a eso del mediodía",
        "cita aproximadamente a las 4",
        "entregar alrededor de las 18",
        "reunión de 9 a 11",
        "taller de 9 a 11 del viernes",
        "estudiar de hoy a jueves"
    )
    for (c in cases) {
        val r = p.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "—"
        println("IN : $c")
        println("   title='${r.title}'  due=$due  prio=${r.priority}  dur=${r.durationMinutes}  recur=${r.recurrence}/${r.recurrenceDays}")
    }
}

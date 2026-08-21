import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sonda (c.804): formas "<período> sí <período> no" (cada dos períodos).
 */
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE").withZone(zone)

    val cases = listOf(
        "gym día sí día no",
        "gym día sí y día no",
        "medicina un día sí un día no",
        "medicina un día sí y otro no",
        "limpieza semana sí semana no",
        "limpieza semana sí y semana no",
        "visita una semana sí y otra no",
        "pago mes sí mes no",
        "gym día sí día no a las 7 am",
        "un día sí y otro no a las 8",
        // guards
        "comprar un día sí y otro también",
        "día sí día no pienso en ella",
        "la semana sí fue dura",
        "el día sí estuvo bueno"
    )

    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}/${r.recurrenceInterval} days='${r.recurrenceDays}' dur=${r.durationMinutes}")
    }
}

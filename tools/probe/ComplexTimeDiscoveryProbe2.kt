import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sonda de descubrimiento 2 (c.804): confirma GAPs de la sonda 1 con
 * verbos de tarea reales (residuo verdadero vs input puramente temporal)
 * y explora rangos sin parte del día y cadencias "día sí día no".
 */
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE").withZone(zone)

    val cases = listOf(
        // GAP candidato 1: "día sí día no" (cada dos días)
        "gym día sí día no",
        "gym día sí día no a las 7",
        "medicina un día sí y otro no",
        "regar las plantas un día sí un día no",
        // GAP candidato 2: rangos horarios SIN parte del día
        "trabajo de 9 a 5",
        "clase de 10 a 12",
        "almuerzo de 12 a 2",
        "reunión de 3 a 5",
        "turno de 8 a 4",
        // Controles: rango CON parte del día (ya funcionan)
        "reunión de 3 a 5 de la tarde",
        // Residuo con verbo real (sonda 1 tenía input puramente temporal)
        "llamar a mamá pasado mañana por la mañana",
        "reunión con juan el viernes por la noche a las 9",
        "cita con el dentista mañana al mediodía",
        "entregar informe dentro de dos semanas",
        "pagar el recibo de aquí a tres días",
        // Guards: NO deben parsear como agenda
        "comprar un día sí y otro también",
        "día sí día no pienso en ella",
        "informe de 9 a 12 páginas",
        "almuerzo de 12 a 2 personas"
    )

    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}/${r.recurrenceInterval} days='${r.recurrenceDays}' dur=${r.durationMinutes}")
    }
}

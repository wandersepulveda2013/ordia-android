import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sonda de descubrimiento (c.804): tiempos naturales complejos.
 * Familias: rangos horarios, partes del día matizadas, cadencias
 * poco frecuentes, relativos compuestos. Busca GAPs honestos:
 * dueAt=null injustificado, residuo en el título o recurrencia
 * equivocada. now = jueves 2026-08-21 12:00 (America/Santo_Domingo).
 */
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE").withZone(zone)

    val cases = listOf(
        // Rangos horarios
        "reunión de 3 a 5 de la tarde",
        "almuerzo de 12 a 2",
        "clase de yoga de 7 a 8 de la mañana",
        // Partes del día matizadas
        "reunión a mediodía",
        "cita a medianoche",
        "llamar a primera hora de la tarde",
        "reunión a última hora de la mañana",
        "vuelo a las 3 de la madrugada",
        "cita a las 8 en punto",
        "reunión a eso del mediodía",
        // Cadencias poco frecuentes
        "gym día sí día no",
        "gym días alternos",
        "medicina cada tercer día",
        "pago quincenal",
        "nómina bisemanal",
        "visita cada dos semanas",
        "cambio de aceite cada 15 días",
        // Relativos compuestos
        "dentro de dos semanas",
        "de aquí a tres días",
        "en una hora y media",
        "pasado mañana por la mañana",
        "mañana al mediodía",
        "el viernes por la noche a las 9",
        "a finales de la semana que viene",
        "a mediados de la semana que viene",
        "el fin de semana que viene",
        "a principios de la semana que viene"
    )

    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}/${r.recurrenceInterval} days='${r.recurrenceDays}' dur=${r.durationMinutes}")
    }
}

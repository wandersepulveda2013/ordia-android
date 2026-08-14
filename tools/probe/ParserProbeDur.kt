import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.DateRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
    val cases = listOf(
        "Reunión duración 30 minutos",
        "Reunión duración 45",
        "Reunión duración 1 hora",
        "Reunión duración: 30",
        "Reunión duración 2 horas mañana a las 9",
        "no olvides llamar al doctor mañana",
        "no olvides que pago el 10",
        "no olvides llamar a mamá",
        "Cita con duración 90 min el viernes a las 3",
        "duración 30 minutos de ejercicio 30 minutos extra",
        "recuérdame llamar a mamá mañana a las 3 de la tarde",
        "Reunión de dos horas",
        "Trabajar 2h"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        println("'$c' -> title='${r.title}' dur=${r.durationMinutes} reminder=${r.reminderOffsetMinutes} due=${r.dueAt != null}")
    }
}

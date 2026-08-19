import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Probe "misma/mismo" intensifier (BACKLOG fila 42, c.630 PENDIENTE).
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    val cases = listOf(
        "revisar la propuesta esta misma semana",
        "revisar la propuesta esta semana",
        "pagar la renta este mismo mes",
        "pagar la renta este mes",
        "reunión esta misma semana que viene",
        "informe a fin de esta misma semana",
        "informe a finales de esta misma semana",
        "informe a fin de esta semana",
        "entrega para este mismo mes",
        "este mismo día",
        "la misma semana",
        "revisar la semana",
        "revisar la propuesta esta semana misma",
        "pagar la renta este mes mismo",
        "informe a fin de la semana",
        "esta semana misma"
    )
    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}")
    }
}

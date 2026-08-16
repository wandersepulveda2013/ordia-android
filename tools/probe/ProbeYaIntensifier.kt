import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Probe c.397-diagnóstico: ¿"ya"/"en un rato"/"más tarde" + hora explícita
// producen dueAt=now (o now+desfase) en vez de la hora explícita sobre hoy?
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 7, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String, val expect: String)
    val cases = listOf(
        // "ya" + hora explícita (bug P1 datos sagrados)
        Case("reunión ya a las 5", "esperado: hoy 17:00"),
        Case("reunión ya a las 5 de la tarde", "esperado: hoy 17:00"),
        Case("reunión ya a las 9 de la mañana", "esperado: hoy 09:00"),
        Case("reunión ya a la 1", "esperado: ¿hoy 13:00? (a la 1 digit no parsea ES)"),
        // "ya" solo = ahora (legítimo, debe preservarse)
        Case("reunión ya", "esperado: now=12:00 (legítimo)"),
        Case("reunión ahora", "esperado: now=12:00 (legítimo)"),
        Case("reunión ya mismo", "esperado: now=12:00 (legítimo)"),
        // "ya" + fecha explícita (no hora): ¿"ya el viernes"?
        Case("reunión ya el viernes", "esperado: viernes (¿ahora=now o viernes?)"),
        // "en un rato" + hora explícita (vagueRelativeDueAt)
        Case("reunión en un rato a las 5", "esperado: ¿hoy 17:00? (¿o now+1h?)"),
        // "más tarde" + hora explícita (laterRelativeDueAt)
        Case("reunión más tarde a las 5", "esperado: ¿hoy 17:00? (¿o now+3h?)"),
        Case("reunión después a las 5", "esperado: ¿hoy 17:00? (¿o now+3h?)"),
        // Controles de hora explícita sola
        Case("reunión a las 5 de la tarde", "control: hoy 17:00"),
        Case("reunión a las 9 de la mañana", "control: hoy 09:00"),
        // "ahora" + hora explícita (raro pero posible)
        Case("reunión ahora a las 5", "esperado: ¿hoy 17:00?")
    )

    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[${c.text}] -> due=$due title='${r.title}'   # ${c.expect}")
    }
}

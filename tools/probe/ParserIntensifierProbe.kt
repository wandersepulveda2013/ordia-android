import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Probe c.396-candidate: ¿dejan residuo en el título los intensificadores menos
// comunes "recién"/"apenas"/"ya casi" antes de "a las/la N" y antes de anclas?
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 7, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String, val note: String? = null)
    val cases = listOf(
        // --- Diagnóstico de "ya": ¿"ya" = ahora = now? ---
        Case("reunión ya", "solo 'ya'"),
        Case("reunión ya a las 5", "ya + a las N"),
        Case("reunión a las 5", "a las 5 solo (control)"),
        Case("reunión ya a las 5 de la tarde", "ya + hora + parte del día"),
        Case("reunión ya casi a las 5", "ya casi + a las N"),
        Case("reunión casi a las 5", "casi a las 5 (control c.393)"),
        // --- "recién" antes de hora explícita ---
        Case("reunión recién a las 9", "recién + a las N"),
        Case("cita recién a la 1", "recién + a la una"),
        Case("llamar recién a las 9 de la noche", "recién + hora + parte del día"),
        // --- "apenas" antes de hora explícita ---
        Case("reunión apenas a las 3", "apenas + a las N"),
        Case("cita apenas a las 9 de la mañana", "apenas + hora + parte del día"),
        // --- "ya casi" antes de hora explícita ---
        Case("reunión ya casi a las 5", "ya casi + a las N"),
        Case("cita ya casi a la 1", "ya casi + a la una"),
        // --- "recién/apenas" antes de anclas comida/sol/jornada ---
        Case("reunión recién al mediodía", "recién + mediodía"),
        Case("cita recién al amanecer", "recién + amanecer"),
        Case("llamar apenas después de comer", "apenas + después de comer"),
        // --- Casos negativos (no deben robar / no son citas) ---
        Case("recién llegado del trabajo", "recién adjetival (no cita)"),
        Case("apenas 3 cajas", "apenas + cantidad (no cita)"),
        Case("ya casi termino", "ya casi sin hora (no cita)")
    )

    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[${c.text}] -> due=$due title='${r.title}'")
        if (c.note != null) println("    # ${c.note}")
    }
}

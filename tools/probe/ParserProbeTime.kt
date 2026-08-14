import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    // Jueves 2026-08-13 12:00 ART (UTC-3).
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 13, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    data class Case(val text: String, val note: String? = null)
    val cases = listOf(
        // Hora del reloj con decimales (gap léxico pendiente P3 "a las 3.5").
        Case("reunión a las 3.5", "decimal de hora de reloj"),
        Case("reunión a las 3,5", "decimal coma de hora de reloj"),
        // Hora escrita con fracción: "diez y media" / "diez y cuarto" (bare, sin 'punto'/sin 'horas').
        Case("reunión diez y media", "diez y media bare"),
        Case("reunión a las diez y media", "a las diez y media"),
        Case("cita a las diez y cuarto", "diez y cuarto"),
        Case("cita a las diez y tres cuartos", "diez y tres cuartos"),
        // "mediodía y cuarto" / "medianoche y media" (fracciones de mediodía/medianoche).
        Case("reunión al mediodía y media", "mediodía y media"),
        Case("reunión a la medianoche y cuarto", "medianoche y cuarto"),
        // Hora con minutos escritos: "a las diez menos cuarto" / "a las once y veinte".
        Case("cita a las once menos cuarto", "once menos cuarto"),
        Case("cita a las once y veinte", "once y veinte"),
        // Recurrencia por día-semana del mes: "el primer lunes del mes" / "el segundo viernes".
        Case("reunión el primer lunes del mes", "primer lunes del mes"),
        Case("pago el segundo viernes de cada mes", "segundo viernes de cada mes"),
        Case("cita el tercer martes del mes que viene", "tercer martes del mes que viene"),
        // "cada N semanas" + weekday: "cada 2 semanas los lunes".
        Case("reunión cada 2 semanas los lunes", "cada 2 semanas los lunes"),
        Case("reunión cada dos lunes", "cada dos lunes"),
        // Plazo relativo mixto: "en un par de días" / "en un par de horas".
        Case("llamar en un par de días", "un par de días"),
        Case("llamar en un par de horas", "un par de horas"),
        Case("llamar en un par de semanas", "un par de semanas"),
        // "dentro de un rato" / "en un ratito" coloquial.
        Case("llamar en un ratito", "en un ratito"),
        Case("llamar dentro de un rato", "dentro de un rato"),
        // Vencimiento "pasado mañana" + hora.
        Case("reunión pasado mañana a las 3", "pasado mañana a las 3"),
        Case("reunión anteayer a las 10", "anteayer a las 10 (vencida honesta)"),
        // "a primera hora" / "a última hora".
        Case("enviar a primera hora del lunes", "primera hora del lunes"),
        Case("enviar a última hora del viernes", "última hora del viernes"),
        // Duración compacta con coma y forma "una media hora".
        Case("reunión una media hora", "una media hora como duracion"),
        Case("reunión media hora", "media hora suelta"),
        Case("reunión tres cuartos de hora", "tres cuartos de hora"),
        // Anti-regresión tras fixes recientes.
        Case("pago el último viernes del mes", "ultimo viernes del mes (c.180)"),
        Case("el último viernes de septiembre", "ultimo viernes de septiembre (c.180)"),
        Case("estudiar 1.5 horas", "decimal duracion (c.179)"),
        Case("ideal proyecto", "captura prefijo (c.179)")
    )

    cases.forEach { c ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(c.text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[${c.text}] -> due=$due title='${r.title}' rec=${r.recurrence} dur=${r.durationMinutes} rem=${r.reminderOffsetMinutes}")
        if (c.note != null) println("    # ${c.note}")
    }
}

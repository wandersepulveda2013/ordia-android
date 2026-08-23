import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ParsedTaskInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    // now = domingo 2026-08-23 12:00
    val now = ZonedDateTime.of(2026, 8, 23, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    val cases = listOf(
        // (A) intensificador de plazo "sin falta"
        "llamar a Juan el viernes sin falta",
        "pagar la luz sin falta mañana",
        "entregar el informe mañana sin falta",
        // (B) "como muy tarde" (a más tardar, coloquial)
        "entregar el informe el viernes como muy tarde",
        "pagar la renta mañana como muy tarde",
        "terminar la tarea hoy como muy tarde",
        // (C) "mismo" enfático
        "terminar el informe hoy mismo",
        "llamar al médico mañana mismo",
        "revisar el correo ahora mismo",
        // (D) "finde" coloquial
        "salir el finde que viene",
        "descansar este finde",
        "viajar el próximo finde",
        // (E) "de cara a"
        "preparar la presentación de cara al lunes",
        "estudiar de cara al examen del viernes",
        // (F) primera/última hora genitiva
        "llamar al banco a primera hora del lunes",
        "reunión a última hora de la tarde",
        // (G) día del mes desnudo (observación registrada c.845/c.852)
        "pagar la renta el día 15",
        "cobrar la nómina el día 30",
        "cita con el dentista el día 5",
        // (H) "de aquí al <weekday/día>"
        "pagar la factura de aquí al viernes",
        "entregar el trabajo de aquí al 30",
        // (I) controles (deben permanecer intactos)
        "el informe sin falta de ortografía",
        "reunión el viernes",
        "pagar la renta el 15 de cada mes",
        "cita mañana a las 9"
    )

    cases.forEach { text ->
        val r: ParsedTaskInput = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence}/${r.recurrenceInterval} days='${r.recurrenceDays}' rem=${r.reminderOffsetMinutes}")
    }
}

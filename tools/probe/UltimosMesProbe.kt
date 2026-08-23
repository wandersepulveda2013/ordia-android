import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Probe c.907 PRE — «a últimos de <mes>» (forma dialectal fin de mes). */
fun main() {
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 21, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(zone)

    val cases = listOf(
        // candidatas «a últimos de <mes>»
        "pagar la renta a ultimos de agosto",
        "entregar el informe a ultimos de septiembre",
        "cita con el banco a ultimos de octubre de 2026",
        "viaje a ultimos de agosto",
        // regresiones (familia ya cubierta)
        "entregar el informe a primeros de septiembre",
        "pago a mediados de octubre",
        "renta a finales de octubre",
        "pago a fin de mes",
        // guards (NO deben capturar como límite)
        "los ultimos de la fila",
        "repasar los ultimos detalles",
        "a ultimos",
        // observacion: forma relativa desnuda (sin mes nombrado)
        "pagar a ultimos del mes",
        "pagar a ultimos del mes que viene",
        "revisar a ultimos del mes pasado"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$c] -> title='${r.title}' due=$due rec=${r.recurrence}")
    }
}

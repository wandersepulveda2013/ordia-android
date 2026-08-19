import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santiago")
    val now = ZonedDateTime.of(2026, 8, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    val cases = listOf(
        "entregar informe para diciembre",
        "informe listo para septiembre",
        "tenerlo para enero",
        "para finales de diciembre",
        "para el 15 de diciembre",
        "hasta diciembre",
        "quedarme hasta septiembre"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "—"
        println("IN : $c\n    title='${r.title}' due=$due")
    }
}

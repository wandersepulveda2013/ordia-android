import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santiago")
    val now = ZonedDateTime.of(2026, 8, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    val cases = listOf(
        "clasificar a la 1ª posición",
        "poner esto a la 2ª posición",
        "subir el enlace a tercera posición",
        "dejarlo en la primera posición",
        "a la 1ª posición en la lista",
        "a las 3ª posición",
        "mover a las 2º fila",
        "a la una posición",
        "a la 1:30 pm",
        "a la una",
        "a las 3",
        "cena a la 1"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "—"
        println("IN : $c\n    title='${r.title}' due=$due")
    }
}

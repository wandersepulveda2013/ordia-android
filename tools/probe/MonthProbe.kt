import com.ordia.app.domain.NaturalTaskParser
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val cases = listOf(
        "apuntarme al gimnasio en septiembre",
        "ir al dentista en octubre",
        "viaje en diciembre",
        "renovar contrato en enero",
        "examenes en junio",
        "boda en agosto",
        "entrega en agosto de 2027",
        "pagar cuota en febrero de 2026",
        "curso en mayo todos los años",
        "a inicios de septiembre",
        "a mediados de octubre",
        "a finales de noviembre",
        "a principios de enero"
    )
    for (c in cases) {
        val p = NaturalTaskParser.parse(c, now, zone)
        val due = p.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().format(fmt) } ?: "NULL"
        println("%-52s | title=%-42s | due=%s".format("'$c'", "'${p.title}'", due))
    }
}

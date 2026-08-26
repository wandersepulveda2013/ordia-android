// Sonda PRE (c.1233; DISJUNTA del hermano — mi lateral «colgar» hooks-boda):
// medir si «colgar las fotos de la boda» es capturable (gate de NECESIDAD —
// si ya ancla/notancian la integración la sigue pasando, registramos y NO
// implementamos por menos es más; si no hay hook, decisión de gate c.751).
import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = java.time.ZonedDateTime.of(2026, 8, 23, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val phrases = listOf(
        "colgar las fotos de la boda",
        "colgard las foutos de la boda",
        "colgé las fotos de la boda",       // pretendida guard (no real, buth hooks)
        "no colgar las fotos de la boda"    // negación (guard)
    )
    for (p in phrases) {
        val r = NaturalTaskParser.parse(p, now, zone)
        println("«$p» -> title='${r.title}' due=${r.dueAt}")
    }
}
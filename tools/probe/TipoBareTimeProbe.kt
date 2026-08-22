import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Probe c.852 lateral (c) — c.868: "tipo N" desnudo con evidencia de reloj inmediata.
 * RED (c.868, medido 2026-08-22): las 6 candidatas anclaban la hora correctamente pero
 * "tipo" sobrevivía como residuo en el título ("comida tipo"); los 5 controles de
 * categoría/ambigüedad quedaban dueAt=null y las 4 regresiones capturaban limpio.
 * GREEN esperado: las 6 candidatas capturan con título limpio (sin "tipo"), los 5
 * controles siguen intactos (null, título preservado) y las 4 regresiones no cambian.
 */
fun main() {
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 21, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(zone)

    val cases = listOf(
        // candidatas (lateral c.852 (c))
        "comida tipo 2 de la tarde",
        "reunion tipo 3 pm",
        "cita tipo 10:30",
        "salir tipo 7 de la manana",
        "reunion tipo 9 de la noche",
        "cita tipo 8 am",
        // controles (NO deben tocarse)
        "documento tipo 8",
        "plan tipo estrategia",
        "reunion tipo 3",            // hora desnuda sin evidencia -> ambigua (categoria)
        "documento tipo 8 personas", // cuenta/categoria
        "mesa tipo 8 de comedor",    // "8 de comedor" no es evidencia de reloj
        // regresiones (ya capturan)
        "cita tipo las 8",
        "reunion tipo la una",
        "comida a las 2 de la tarde",
        "reunion hacia las 9 pm"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("due=$due | title='${r.title}' | <= $c")
    }
}

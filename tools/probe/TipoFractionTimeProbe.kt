import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sonda c.870: "tipo N" desnudo con FRACCIÓN entre hora y parte del día
 * ("salir tipo 5 y media de la tarde"). Antes: el reloj autónomo anclaba la hora
 * pero "tipo" sobrevivía como residuo en el título. Uso: tools/run_probe.sh.
 * OJO: pasar SIEMPRE `zone` explícita a parse(); sin ella el motor usa
 * ZoneId.systemDefault() (UTC en contenedores) y la lectura sale desplazada.
 */
fun main() {
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 21, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(zone)

    val cases = listOf(
        // candidatas (deben anclar y dejar título limpio)
        "salir tipo 5 y media de la tarde",
        "salir tipo 5 y cuarto de la tarde",
        "cita tipo 10 y media de la manana",
        "cena tipo 9 y media de la noche",
        "salir tipo cinco y media de la tarde",
        "salir tipo 5 y veinte de la tarde",
        // guards (NO deben tocarse: el reloj autónomo no las resuelve)
        "comida tipo 2 y media",
        "cita tipo 3 y cuarto pm",
        "documento tipo 8",
        "mesa tipo 8 de comedor",
        "reunion tipo 3",
        // regresiones (rutas hermanas)
        "cita tipo las 8",
        "comida tipo 2 de la tarde",
        "comida a las 2 y media"
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        println("due=${r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) }} | title='${r.title}' | <= $c")
    }
}

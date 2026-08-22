import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Probe c.852 lateral (a) — c.867: "el <weekday> de esta semana" / "esta semana el <weekday>".
 * RED (c.866, medido 2026-08-22): TODOS los weekdays calificados con "de esta semana"
 * colapsaban al plazo blando del domingo 09:00 (fecha errónea silenciosa, P1).
 * GREEN esperado: el weekday explícito ancla a su día de la SEMANA ISO actual
 * (lunes 17 .. domingo 23 con now = viernes 2026-08-21 15:00 UTC = 12:00 ART);
 * si el día ya pasó esta semana queda como vencida honesta (doctrina "el lunes pasado").
 * Los controles ("esta semana" sola, "el viernes" suelto, "de la semana que viene")
 * no deben cambiar.
 */
fun main() {
    // Viernes 2026-08-21 15:00 UTC = 12:00 ART (UTC-3). Lunes 17/08 primer día de la semana ISO.
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 21, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(zone)

    val cases = listOf(
        "dentista el viernes de esta semana",   // hoy 21/8 (no rueda +7d: calificador fija esta semana)
        "dentista el viernes esta semana",      // forma coloquial sin "de" -> hoy 21/8
        "dentista el lunes de esta semana",     // lunes 17/8 pasado: vencida honesta
        "dentista el domingo de esta semana",   // 23/8 (coincide con plazo blando)
        "dentista el sabado de esta semana",    // 22/8
        "dentista el viernes",                  // control: weekday suelto -> 28/8
        "dentista esta semana",                 // control: plazo blando -> 23/8
        "dentista el viernes de la semana que viene", // control: 28/8
        "pagar la luz el martes de esta semana",// 18/8 pasado
        "dentista de esta semana el viernes"    // genitivo + orden inverso -> 21/8
    )
    for (c in cases) {
        val r = NaturalTaskParser.parse(c, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("due=$due | title='${r.title}' | <= $c")
    }
}

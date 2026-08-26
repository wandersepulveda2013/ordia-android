import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sonda c.1229: lateral P1 ABIERTA registrada en c.1041 (UNIÓN) — weekday AL
 * FINAL con SUJETO NOMINAL en el PREFIJO («el paquete llegó el lunes»). La
 * ruta vocab de [weekdayOccurrenceIsPreteriteNarrative] exige que el prefijo
 * ARRANQUE con (ya)? clíticos+pretérito; un sujeto nominal delante lo rompe
 * → ancla weekday FALSA + título mutilado (doble daño P1). Uso:
 * tools/run_probe.sh. OJO: pasar SIEMPRE `zone` explícita a parse().
 * T: capturas esperadas narrativa (due=null, título íntegro) tras el fix.
 * G: guards que DEBEN seguir anclando (preterite inexistente/ambiguo,
 * quedar-con, infinitivo embebido, «que viene», presente).
 * R: regresiones de las rutas hermanas (weekday-primero, ya-route,
 * verbo-primero) y anclas reales.
 */
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = java.time.ZonedDateTime.of(2026, 8, 23, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(zone)

    val targets = listOf(
        "el paquete llegó el lunes",
        "el pedido llegó el miércoles",
        "la alarma sonó el viernes",
        "los resultados llegaron el martes",
        "mi hermano vino el jueves",
        "el cartero pasó el sábado",
        "las noticias salieron el domingo"
    )
    val guards = listOf(
        "la reunión es el lunes",
        "tengo cita con el dentista el lunes",
        "quedé el lunes con Ana",
        "salí a comprar el lunes",
        "el dentista el lunes por la mañana",
        "mañana me voy el lunes",
        "el paquete el lunes que viene",
        "salimos el lunes"
    )
    val regressions = listOf(
        "lunes llegó el paquete",
        "ya me lo pagó el lunes",
        "llegué el miércoles",
        "el dentista el lunes",
        "comprar pan el lunes"
    )
    var n = 0
    for ((label, cases) in listOf("T" to targets, "G" to guards, "R" to regressions)) {
        for (c in cases) {
            n++
            val r = NaturalTaskParser.parse(c, now, zone)
            println("$label$n due=${r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) }} | title='${r.title}' | <= $c")
        }
    }
}

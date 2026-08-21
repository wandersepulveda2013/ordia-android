import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Sonda de descubrimiento c.805: formas temporales COMPLEJAS (fecha+hora+alcance
// combinadas) que las personas usan a diario. Cada candidato DEBE producir
// dueAt != null; los nulos son GAP candidatos (se evalúa si es ambigüedad
// deliberada como los DECISIONS, o hueco real).
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli() // viernes 21 ago
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    val cases = listOf(
        // fecha específica con parte del día (combinación calendario + franja)
        "reunión el 3 de septiembre por la mañana",
        "cita el 20 de este mes por la tarde",
        "entrega el 25 de diciembre por la noche",
        // día de semana + meridiem combinados
        "lunes a las ocho de la noche",
        "el viernes que viene a las tres de la tarde",
        // ordinal de día de semana en el mes
        "reunión el segundo lunes de septiembre",
        "cita el primer viernes del mes a las 9",
        // relativos con hora explícita
        "dentro de tres días a las 5 de la tarde",
        "en dos semanas a las 10 de la mañana",
        // fin de semana con hora
        "cita este fin de semana a las 11",
        "descanso el fin de semana que viene",
        // alcance de semana con hora (ambicioso)
        "reunión esta semana a las 9",
        // «N antes/después de <ancla>»
        "pago tres días antes del viernes",
        "cita dos días después del 25",
        // controles (deben ser NULL — sombreados documentados en DECISIONS)
        "reunión sobre las cinco de la tarde",      // aproximado con evidencia → sí
        "reunión hacia las nueve",                  // sin evidencia → NULL deliberado
        "abierta",                                  // sin temporales → NULL
        "tarea sin fecha",
        "reunión sábado",                           // sábado = 22 ago (mañana)
        "medianoche a las 12",                      // medianoche explícita
        "el 15 a las 4"                             // día suelto + hora
    )

    var gaps = 0
    for (c in cases) {
        val p = NaturalTaskParser.parse(c, now, zone)
        val due = p.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "NULL"
        if (p.dueAt == null && c !in listOf("reunión hacia las nueve", "abierta", "tarea sin fecha")) {
            gaps++
            println("<<< GAP: '$c' -> title='${p.title}' due=NULL")
        } else {
            println("ok:    '$c' -> title='${p.title}' due=$due")
        }
    }
    println("GAPs: $gaps")
}

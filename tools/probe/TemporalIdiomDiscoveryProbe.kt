// Sonda de DESCUBRIMIENTO de modismos temporales del parser natural (c.846).
// Auditoría del área NaturalTaskParser: «dentro de N», «de aquí a», «esta
// <parte del día>», compactas día+parte, recurrencias, límites, «después de
// mañana», «en una <unidad> <adjetivo>», y guards de contenido.
//
// Ejecutar: bash tools/run_probe.sh tools/probe/TemporalIdiomDiscoveryProbe.kt
// (now fijo = viernes 2026-08-21 12:00 America/Argentina/Buenos_Aires).
//
// Resultado POST c.846: TODAS las formas documentadas resuelven correctamente;
// «entregar después de mañana» → +2 días (fix c.846: antes +1, P1 fecha errónea).
// Hallazgo lateral RESUELTO c.849: «en una semana difícil» ya NO secuestra
// contenido (antes dueAt +7d, título 'difícil'; ahora dueAt=null y título
// íntegro) — el ancla con artículo indefinido exige fin de frase o conector/
// determinante tras la unidad (guard articleRelativeHijacksContent).
import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    // Viernes 2026-08-21 12:00 local. Domingo 23/08. Próximo lunes 24/08.
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    val now = ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm").withZone(zone)

    val cases = listOf(
        // --- "dentro de <n> <unidad>" ---
        "llamar al banco dentro de 3 días",
        "revisar el informe dentro de una semana",
        "volver dentro de 2 horas",
        "pagar la luz dentro de un mes",
        // --- "de aquí a/en <n>" ---
        "cita de aquí a una semana",
        "entregar de aquí al martes",
        // --- "en <n> <unidad>" ---
        "llamar en 3 días",
        "reunión en una semana",
        "volver en 2 horas",
        "revisión en un mes",
        // --- partes del día con ancla ---
        "correr esta madrugada",
        "gimnasio esta mañana",
        "comprar esta tarde",
        "mañana a primera hora",
        "mañana a última hora",
        "el domingo por la mañana",
        "pasado mañana por la tarde",
        "hoy por la noche",
        // --- recurrencias ---
        "regar las plantas cada dos días",
        "gimnasio cada lunes",
        "revisar el correo cada semana",
        "pagar el alquiler una vez al mes",
        "limpiar cada fin de semana",
        "clase de yoga los martes y jueves",
        // --- rangos / límites ---
        "terminar antes del fin de semana",
        "entregar después de mañana",
        "reunión a primeros de septiembre",
        "pagar a fin de mes",
        "informe para la semana que viene",
        // --- guardas (NO deben fabricar) ---
        "reunión cada cuando quieras",
        "pensar dentro de la caja",
        "en una semana difícil",
        "comprar leche"
    )

    cases.forEach { text ->
        val r = NaturalTaskParser.parse(text, now, zone)
        val due = r.dueAt?.let { dt.format(java.time.Instant.ofEpochMilli(it)) } ?: "null"
        println("[$text] -> due=$due title='${r.title}' rec=${r.recurrence} dur=${r.durationMinutes} rem=${r.reminderOffsetMinutes}")
    }
}

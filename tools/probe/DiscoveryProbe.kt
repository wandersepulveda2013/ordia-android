import com.ordia.app.domain.NaturalTaskParser
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Sonda de descubrimiento c.672: frases cotidianas de captura variadas.
// No es un test: imprime title/dueAt/recurrence para inspección humana.
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 19, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val cases = listOf(
        "pedir cita médico mañana",
        "coger cita dentista el jueves",
        "solicitar turno banco la semana que viene",
        "hacer la colada este fin de semana",
        "pasar la ITV antes de que caduque",
        "renovar el pasaporte el mes que viene",
        "cancelar Netflix cuando acabe el mes",
        "devolver el libro a Laura cuando la vea",
        "comprar regalo de cumpleaños de mamá",
        "preparar la mochila antes del viaje",
        "apuntarme al gimnasio en septiembre",
        "recordarle a Juan que pague el alquiler",
        "mandar el currículum cuanto antes",
        "cambiar las llantas cuando llegue a 10000 km",
        "llevar el coche a revisión esta semana",
        "cortarme el pelo de una vez",
        "poner la alarma para las 6",
        "quedar con Ana a tomar café",
        "cenar con los suegros el sábado",
        "descargar la factura de la luz",
        "verificar cuentas con el contador",
        "vacunar al perro el lunes",
        "pagar la tarjeta entre el 1 y el 5",
        "escribirle a Papá el día de San Valentín",
        "ir de compras antes de Navidad",
        "confirmar la reserva del restaurante esta noche",
        "entregar llaves mañana a primera hora",
        "pasar por el taller a recoger el coche",
        "hacerme el análisis de sangre en ayunas"
    )
    for (c in cases) {
        val p = NaturalTaskParser.parse(c, now, zone)
        val due = p.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().format(fmt) } ?: "NULL"
        println("%-60s | title=%-38s | due=%s | rec=%s".format("'$c'", "'${p.title}'", due, p.recurrence))
    }
}

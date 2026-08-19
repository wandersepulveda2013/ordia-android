import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.UniversalCaptureEngine
import java.time.Instant
import java.time.ZoneId

// Sonda de descubrimiento: formas cotidianas de captura/recordatorio en español.
// Mide (a) el destino inferido por UniversalCaptureEngine y (b) qué hace el parser.
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = Instant.parse("2026-08-19T16:00:00Z").toEpochMilli()
    val cases = listOf(
        "que no se me olvide comprar leche",
        "que no se me olvide llamar al banco mañana a las 10",
        "no dejes que se me olvide pagar la luz",
        "que no se me pase recoger el paquete",
        "apunta esto: la wifi es clave1234",
        "apunta: comprar pilas",
        "anota lo siguiente",
        "anota esto porfa",
        "ponme una alarma para mañana a las 7",
        "quiero recordar hacer la maleta",
        "recuérdame que tengo que llamar a mamá",
        "recuérdame comprar leche mañana",
        "avísame cuando llegue el paquete",
        "no vaya a ser que se me pase la cita",
        "acuérdate de sacar al perro",
        "tengo que acordarme de pagar la tarjeta"
    )
    for (c in cases) {
        val interp = UniversalCaptureEngine.interpret(c)
        val parsed = NaturalTaskParser.parse(c)
        val due = parsed.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toString().dropLast(11) } ?: "null"
        println("IN:  $c")
        println("  -> target=${interp.target} conf=${"%.2f".format(interp.confidence)} title='${interp.title}'")
        println("  -> parser title='${parsed.title}' due=$due conf=${"%.2f".format(parsed.confidence)}")
    }
}

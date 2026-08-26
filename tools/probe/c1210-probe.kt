import com.ordia.app.domain.DateRules
import com.ordia.app.domain.NaturalTaskParser
import java.time.LocalDateTime
import java.time.ZoneId

/** Sonda PRE c.1210: familia «contar» ausente de la lista cerrada de
 * pretéritos narrativos c.950 (lateral ABIERTA registrada c.1095).
 */
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = LocalDateTime.of(2026, 8, 22, 12, 0).atZone(zone).toInstant().toEpochMilli()
    fun show(label: String, t: String) {
        val r = NaturalTaskParser.parse(t, now, zone)
        val due = r.dueAt?.let { DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone)) }
        println("$label | due=$due | title=\"${r.title}\" <- $t")
    }
    // capturas sospechadas
    show("C1", "ya me contaste el plan")
    show("C2", "ahorita me contó el plan")
    show("C3", "me contó el plan el lunes")
    show("C4", "me contó a primera hora")
    // guards: comandos/presente/ambiguas deben anclar igual (byte-idénticos)
    show("G1", "cuéntame el plan mañana")
    show("G2", "quiero contarte mañana")
    show("G3", "contamos todos los días")
    show("G4", "ya salimos")
    show("G5", "te conté mañana")
    // regresiones: narrativas ya cubiertas incluido nominal pin
    show("R1", "ya me llamó mamá")
    show("R2", "ya, por la mañana, me tomé la pastilla")
    show("R3", "el paquete llegó el lunes")
    show("R4", "ya pagó la luz")
}

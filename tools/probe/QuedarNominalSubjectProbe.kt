// Sonda PRE/POST (c.1229 complemento nominal-quedar-con; DISJUNTA de la sonda
// hermano SubjectPrefixWeekdayProbe): medir la lateral «(determinante sujeto nominal)
// quedar con <nombre> el <weekday>» evaluada sobre la ruta
// narrativeSubjectPrefixHead del hermano (colisión convergente c.1211).
// PRE: el guard «quedar con» del hermano sólo admite clíticos; con sujeto
// nominal (mis padres/tu hermana/mi tía) la narrativa se suprime injustamente
// (due → null) — olvido silencioso P1 sobre una CITA real.
// POST (tras exte-nder weekdayNominalSubjectQuedarCon): C* ancla
// correctamente (cita), G* sigue narrativa-intacta, R* byte-idénticas.
import com.ordia.app.domain.NaturalTaskParser
import java.time.ZoneId

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = java.time.ZonedDateTime.of(2026, 8, 23, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    var failures = 0
    fun narrativeOrAnchor(label: String, phrase: String, expectAnchor: Boolean, annot: String) {
        val r = NaturalTaskParser.parse(phrase, now, zone)
        val ok = if (expectAnchor) r.dueAt != null else r.dueAt == null
        if (!ok) failures++
        println((if (ok) "[OK]  " else "[FAIL]") + " $label «$phrase» -> due=${r.dueAt} title='${r.title}' — $annot")
    }
    fun report() = if (failures == 0) println("NominalQuedarConProbe — OK") else { println("NominalQuedarConProbe — $failures errores"); kotlin.system.exitProcess(1) }

    // --- Capturas (cita futura real) ---
    narrativeOrAnchor("C1", "mis padres quedaron con Ana el lunes", true, "cita")
    narrativeOrAnchor("C2", "tu hermana quedó con mis primos el martes", true, "cita")
    narrativeOrAnchor("C3", "mi tía quedó con el dentista el viernes", true, "cita")

    // --- Guards narrativa (no sujeto quedar) ---
    narrativeOrAnchor("G1", "el paquete llegó el lunes", false, "narrativa")
    narrativeOrAnchor("G2", "los resultados llegaron el martes", false, "narrativa")

    // --- Guards de clítico (ruta hermano) ---
    narrativeOrAnchor("G3", "quedé el lunes con Ana", true, "cita clítico")
    narrativeOrAnchor("G4", "mis padres quedaron con Ana a la una", true, "quedar-con ordinal")

    // --- Regresiones (byte-idénticas) ---
    narrativeOrAnchor("R1", "quedé con Ana a primera hora", true, "cita (ruta ordinal)")
    narrativeOrAnchor("R2", "mis padres quedaron con Ana el lunes por la tarde", true, "cita con parte del día")

report()
}

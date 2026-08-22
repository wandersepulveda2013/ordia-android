import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextCaptureSource

/**
 * Sonda c.888 (persistida): lateral medida NULL desde c.864 — el
 * prefijo re- «reescanear el DNI…». El trámite exige la fotocopia y
 * también la segunda captura cuando la primera quedó borrosa/cortada;
 * el verbo «reescanear» contiene la subcadena «escanear» (keyword
 * TRIGGER 0.12 — la frase SÍ llega al análisis), pero el piso c.864 la
 * excluía por ancla → NULL deliberado documentado como candidata.
 *
 * Sonda PRE c.888 (run_probe.sh): 7/7 candidatas NULL — asimetría
 * con la envolvente c.613 («recuérdame reescanear el DNI…» ya rutea
 * TASK 0.54). POST c.888: 7/7 HIT TASK 0.45 con títulos limpios y
 * objetos del hermano («dni/contrato/notas/código QR»); guards
 * intactas (negación, duda, pasado «reescaneé…», verbo suelto,
 * sustantivo «reescaneo» → NULL; objeto bivalente «reescanear el
 * examen» → STUDY 0.47, NO absorbido por TASK); regresiones HIT
 * («escanear» c.864 / «fotocopiar» c.887 TASK 0.45; envolvente c.613
 * TASK 0.54). Guard de NULL del test c.864 convertida a regresión de
 * captura (precedente c.843, hermana de c.887).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t" else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }
    show("C1", "reescanear el DNI mañana")
    show("C2", "reescanear el DNI esta tarde")
    show("C3", "reescanear el contrato mañana")
    show("C4", "reescanear las notas mañana")
    show("C5", "reescanear el código QR mañana")
    show("C6", "mañana reescanear el DNI")
    show("C7", "vale, reescanear el DNI mañana")
    show("G1", "no reescanear el DNI mañana")
    show("G2", "quizá reescanear el DNI mañana")
    show("G3", "reescaneé el DNI ayer")
    show("G4", "reescanear")
    show("G5", "reescanear el examen mañana")
    show("G6", "el reescaneo del DNI quedó ilegible")
    show("R1", "escanear el DNI mañana")
    show("R2", "fotocopiar el DNI mañana")
    show("R3", "recuérdame reescanear el DNI mañana")
}

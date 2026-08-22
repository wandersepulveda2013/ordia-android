import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextCaptureSource

/**
 * Sonda c.887 (persistida): lateral medida NULL desde c.864 — verbo
 * distinto «fotocopiar el DNI…» (fotocopia documental, gestión docume-
 * ntal cotidiana hermana de la vía «escanear el DNI» c.864). El verbo
 * «fotocopiar» es monosemántico (como «escanear», precedente c.752
 * «votar» / c.864 «escanear»); el sustantivo «fotocopia» NO lo contiene
 * («fotocopia» ≠ «fotocopiar») y la forma pasada «fotocopié…» tampoco.
 *
 * Sonda PRE c.887 (HEAD d9a45b5): 5/5 candidatas NULL — asimetría con
 * la envolvente c.613 (TASK 0.45 PRE). Guardada; luego POST c.887
 * (run_probe.sh sobre el encoder con el piso ya activo):
 *   C.. — 5/5 candidatas HIT TASK 0.45, títulos limpios («Fotocopiar el
 *      DNI», «…por las dos caras» conservado).
 *   G.. — 5/5 guards NULL (negación, duda, pasado, suelto, sustantivo
 *      «fotocopia»).
 *   R.. — regresiones HIT (`escanear` c.864 / `renovar` c.698 TASK);
 *      envolvente c.613 TASK 0.54 (la keyword nueva suma al lock).
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
    show("C1", "fotocopiar el DNI mañana")
    show("C2", "fotocopiar el DNI esta tarde")
    show("C3", "fotocopiar el DNI por las dos caras esta tarde")
    show("C4", "mañana fotocopiar el DNI")
    show("C5", "vale, fotocopiar el DNI mañana")
    show("G1", "no fotocopiar el DNI mañana")
    show("G2", "quizá fotocopiar el DNI mañana")
    show("G3", "fotocopié el DNI ayer")
    show("G4", "fotocopiar")
    show("G5", "la fotocopia del DNI está en el cajón")
    show("R1", "escanear el DNI mañana")
    show("R2", "renovar el DNI la semana que viene")
    show("R3", "recuérdame fotocopiar el DNI mañana")
}

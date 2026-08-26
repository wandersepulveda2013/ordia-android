import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1212: gap (a) de la auditoría clase XXVII — «quitar la
 * hierba» / «quitar las malas hierbas» (verbo bivalente «quitar» sin
 * piso ni keyword-OBJETO; familias DUST c.732 / CLEAR_TABLE c.754).
 */
fun main() {
    fun event(t: String) = ContextEvent(
        source = ContextCaptureSource.SHARED_TEXT,
        rawText = t,
        timestampMs = 0L
    )
    fun show(label: String, t: String) {
        val intent = ContextIntentEngine.analyze(event(t))
        println("$label | ${intent?.kind} | ${intent?.confidence} | \"${intent?.title}\" <- $t")
    }
    // candidatas NULL (gap abierto)
    show("A1", "quitar la hierba")
    show("A2", "quitar las malas hierbas")
    show("A3", "mañana quitar las malas hierbas")
    show("A4", "por favor quitar la hierba")
    show("A5", "vale, quitaré las malas hierbas mañana")
    show("A6", "quitar el hierbajillo de la esquina")
    // guards: forma pasada / negada / declarativa / bivalente
    show("G1", "quité las malas hierbas")
    show("G2", "no quitar las malas hierbas")
    show("G3", "las malas hierbas no dejan crecer la huerta")
    show("G4", "las hierbas aromáticas están en la cocina")
    // regresiones: pisos «quitar» vecinos (objeto disjunto)
    show("R1", "quitar la mesa")
    show("R2", "quitar el polvo")
    // regresión: jardinería vecina (verbo/objeto disjuntos)
    show("R3", "podar los setos")
}

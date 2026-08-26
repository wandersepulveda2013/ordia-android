import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1221: lateral (d) de la auditoría clase XXVIII ROPA —
 * «quitar la mancha (de la camisa)» (verbo bivalente «quitar» sin piso
 * ni keyword-OBJETO; familias DUST c.732 / CLEAR_TABLE c.754).
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
    // candidatas NULL (lateral (d) abierta)
    show("A1", "quitar la mancha de la camisa")
    show("A2", "quitar la mancha del pantalón")
    show("A3", "quitar una mancha de vino del vestido")
    show("A4", "mañana quitar la mancha de grasa de la sudadera")
    show("A5", "vale, quitaré la mancha del sofá")
    show("A6", "por favor quitar la mancha")
    // guards: pasada / negada / declarativa / bivalente
    show("G1", "quité la mancha ayer")
    show("G2", "no quitar la mancha todavía")
    show("G3", "la mancha de tinta no salió")
    show("G4", "quitaré la mancha mañana si recuerdo")
    // regresiones: pisos «quitar» vecinos (objeto disjunto)
    show("R1", "quitar la mesa")
    show("R2", "quitar el polvo")
    // regresión: ropa vecina (verbo/objeto disjuntos)
    show("R3", "coser el botón")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1216: lateral ABIERTA de la auditoría clase XXVII — verbo
 * monovalente «trasplantar» + familia de objetos jardín trasplantables
 * (sin piso ni keyword; familia «podar» c.748 / «quitar…hierbas» c.1214).
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
    show("A1", "trasplantar el bonsái")
    show("A2", "trasplantar la orquídea")
    show("A3", "trasplantar las suculentas")
    show("A4", "mañana trasplantar el bonsái al balcón")
    show("A5", "por favor trasplantar la planta del salón")
    show("A6", "trasplantar el rosal del fondo")
    // guards: forma pasada / negada / sustantivo / objeto fuera de familia / futuro pinnable
    show("G1", "trasplanté el bonsái")
    show("G2", "no trasplantar el bonsái")
    show("G3", "el trasplante fue ayer")
    show("G4", "trasplantar los archivos al nuevo servidor")
    show("G5", "vale, trasplantaré el bonsái mañana")
    // regresiones: jardinería vecina byte-idénticas
    show("R1", "podar los setos")
    show("R2", "quitar las malas hierbas")
    show("R3", "podar el árbol")
}

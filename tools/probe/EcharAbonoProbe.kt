import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1220: lateral ABIERTA de la auditoría clase XXVII c.1211 —
 * «echar (abono|fertilizante) a (tus)? (plantas)» jardinería (verbo
 * bivalente `echar` acotado al objeto sustancia abono/fertilizante,
 * sin piso ni keyword; familia «podar» c.748 / «plantar» hermano c.1215).
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
    show("A1", "echar abono a las plantas")
    show("A2", "echar fertilizante a tus plantas")
    show("A3", "echar el abono a los rosales")
    show("A4", "mañana echar abono a las plantas")
    show("A5", "por favor echar fertilizante a mis plantas")
    show("A6", "echar abono a las suculentas")
    // guards: forma pasada / negada / sustantivo / objeto fuera de familia / futuro pinnable
    show("G1", "eché abono a las plantas")
    show("G2", "no echar abono a las plantas")
    show("G3", "el abono está caducado")
    show("G4", "echar a los archivos al servidor")
    show("G5", "vale, echaré abono a las plantas")
    // regresiones: jardinería vecina byte-idénticas
    show("R1", "podar los setos")
    show("R2", "quitar las malas hierbas")
    show("R3", "trasplantar el bonsái")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE/POST c.1215: lateral ABIERTA del hermano c.1211 (auditoría
 * clase XXVII) — «plantar (los) tomates» / «plantar la orquídea».
 * «plantar» es monosemántico de jardinería (hermano estructural de
 * «podar» c.748/c.1211): lockstep TRES puntos keyword-VERBO + piso +
 * plantilla, objetos cerrados (tomates/árboles/orquídeas/menta/tomillo/
 * hierbabuena/jardín).
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
    // candidatas NULL (gap medido PRE) / capturadas POST
    show("A1", "plantar los tomates")
    show("A2", "plantar la orquídea")
    show("A3", "plantar un árbol")
    show("A4", "mañana plantar los tomates")
    show("A5", "por favor plantar la hierbabuena")
    show("A6", "plantar la menta y el tomillo")
    // guards: futuro / negado / declarativa / sin objeto acotado
    show("G1", "plantaré los tomates el sábado")
    show("G2", "no plantar los tomates")
    show("G3", "los tomates no gustan a los caracoles")
    show("G4", "vamos a plantar mañana en la huerta")
    // regresiones: jardinería vecina (verbos disjuntos)
    show("R1", "podar los setos")
    show("R2", "quitar las malas hierbas")
    show("R3", "cortar el césped")
}

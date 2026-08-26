import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1223: lateral ABIERTA de la auditoría clase XXVII c.1211 —
 * «cubrir (las)? plantas (del frío)» jardinería (verbo bivalente `cubrir`
 * acotado al objeto plantas, con motivo frío/helada/viento opcional; sin
 * keyword nueva, gate c.751; familia «podar» c.748 / «echar abono» c.1220).
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
    show("A1", "cubrir las plantas")
    show("A2", "cubrir las plantas cuando hace frío")
    show("A3", "cubrir los rosales del frío")
    show("A4", "mañana cubrir las plantas")
    show("A5", "por favor cubrir las suculentas")
    show("A6", "cubrir el jardín por la helada")
    // guards: forma pasada / negada / sustantivo / objeto fuera de familia / futuro pinnable
    show("G1", "cubrí las plantas")
    show("G2", "no cubrir las plantas")
    show("G3", "la cubierta está rota")
    show("G4", "cubrir la mesa")
    show("G5", "vale, cubriré las plantas")
    // regresiones: jardinería/ropa vecina byte-idénticas
    show("R1", "quitar la mancha de la camisa")
    show("R2", "coser el botón")
    show("R3", "echar abono a las plantas")
}

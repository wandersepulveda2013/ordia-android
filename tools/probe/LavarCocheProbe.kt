import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1218: lateral ABIERTA de MI marcador c.1218 (clase
 * extrapolada cochera/jardinería familiar) — «lavar (el) coche /
 * (la) camioneta». Bivalencia «lavar»: platos/ropa/coche son objetos
 * disjuntos (guard: regresiones Hermanos c.862/CORE deben quedar
 * byte-idénticas).
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
    // candidatas
    show("A1", "lavar el coche")
    show("A2", "lavar la camioneta")
    show("A3", "lavar el coche mañana")
    show("A4", "mañana lavar el coche")
    show("A5", "por favor lavar la camioneta")
    show("A6", "lavar mi coche")
    show("A7", "lavar el auto")
    // guards: futuro / negado / declarativa / sin objeto
    show("G1", "lavaré el coche el sábado")
    show("G2", "no lavar el coche")
    show("G3", "el coche está sucio")
    show("G4", "lavar")
    // regresiones: lavar hermanas (platos/ropa) + coche ERRAND vecina
    show("R1", "lavar los platos")
    show("R2", "lavar la ropa")
    show("R3", "llevar el coche al taller")
    show("R4", "llenar el coche")
}

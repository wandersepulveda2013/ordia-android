import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda PRE c.1224: lateral D15 de la auditoría clase XXVII jardinería
 * (c.1211) — «sacar (los|mis) muebles (a la terraza)». Verbo «sacar»
 * bivalente (consolidado en 5 pisos disjuntos: visado c.1151 / billete
 * c.1219 / reciclaje c.1057 / mono-almuerdo c.1034 / perro c.740) sin
 * keyword-OBJETO «mueble» todavía (gate c.751; hermana «mancha» c.1221,
 * «hierba» c.1212, «polvo» c.732, «mesa» c.754).
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
    // candidatas NULL (lateral D15 abierta — última de la audit c.1211)
    show("D15-1", "sacar los muebles a la terraza")
    show("D15-2", "sacar los muebles")
    show("D15-3", "sacar el mueble a la terraza")
    show("D15-4", "sacar mis muebles")
    show("D15-5", "mañana sacar los muebles a la terraza")
    show("D15-6", "por favor sacar los muebles")
    show("D15-7", "sacar tus muebles")
    show("D15-8", "sacar sus muebles")
    // guards: pasado / negado / declarativo / futuro bivalente
    show("G1", "saqué los muebles de la terraza")
    show("G2", "no sacar los muebles todavía")
    show("G3", "los muebles están en la terraza")
    show("G4", "sacaré los muebles mañana si recuerdo")
    show("G5", "sacar los juguetes a la terraza")
    // regresiones: pisos «sacar» vecinos (objeto disjunto)
    show("R1", "sacar al perro")
    show("R2", "sacar la basura")
    show("R3", "sacar dinero")
    show("R4", "sacar dinero del cajero")
    // regresión: región vecina (objeto disjunto)
    show("R7", "quitar la hierba")
    show("R8", "trasplantar la orquídea")
}

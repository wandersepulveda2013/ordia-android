import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST c.1250: «clase(s) de <fitness>» (lateral (g) DISJUNTA de
 * MI auditoría c.1227 — clase TRIGÉSIMA deporte). Gate c.751: «clase» es
 * nominal bivalente (escuela), pero con objeto acotado a disciplinas
 * fitness (yoga|pilates|spinning|aeróbic|aerobic|zumba|gimnasia) es
 * monosemántica → EXERCISE. CERO keyword nueva («yoga» ya keyword).
 * Targets: PRE = NULL / POST = HIT EXERCISE (piso acotado).
 * Guards: escolar («clase de matemáticas»), negación, pretérito → NULL.
 * Regresiones: fórmulas heredadas (yoga/pilates/entrenamiento) → HIT.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else {
            val due = i.dueAt != null
            println("$label [HIT] ${i.kind} | ${i.title} | dueAt=$due <- $t")
        }
    }
    // Targets (capturas esperadas EXERCISE)
    show("T1", "clase de yoga mañana")
    show("T2", "clase de pilates el lunes")
    show("T3", "clase de spinning por la tarde")
    show("T4", "mi clase de yoga el jueves")
    show("T5", "la clase de aeróbic mañana")
    show("T6", "clase de zumba el martes")
    show("T7", "clases de gimnasia el sábado")
    // Guards (NULL esperado — anti-overreach)
    show("G1", "clase de matemáticas mañana")
    show("G2", "clases de retórica el lunes")
    show("G3", "no clases de yoga mañana")
    show("G4", "la clase de zumba fue ayer")
    // Regresiones (HIT por fórmulas heredadas)
    show("R1", "hacer yoga")
    show("R2", "ir a pilates")
    show("R3", "entrenamiento de fútbol")
    show("R4", "entrenar mañana")
    show("R5", "partido de tenis el domingo")
    // Envolvente (TASK por la policy envolvente)
    show("E1", "recuérdame clase de yoga mañana")
    println("sonda c.1250 ok")
}

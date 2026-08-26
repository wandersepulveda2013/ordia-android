import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST c.1231: «partido de <deporte>» (lateral (c) DISJUNTA).
 * Targets: PRE = NULL / POST = HIT EXERCISE (piso acotado a partido+deporte).
 * Guards: bivalentes (partido político, "ver") / pasado → NULL.
 * Regresiones: fórmulas heredadas (jugar, entrenar, envolvente) → HIT.
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
    show("T1", "partido de tenis el domingo")
    show("T2", "partido de baloncesto mañana")
    show("T3", "partido de fútbol el sábado")
    show("T4", "partido de pádel el lunes")
    show("T5", "partido de voleibol el jueves")
    show("T6", "partido de balonmano el viernes")
    // Guards (NULL esperado — anti-overreach)
    show("G1", "el partido político es mañana")
    show("G2", "ver el partido a las siete")
    show("G3", "viendo el partido de ayer")
    // Regresiones (HIT por fórmulas heredadas)
    show("R1", "jugar al tenis mañana")
    show("R2", "entrenar mañana")
    // Envolvente (TASK por la policy envolvente)
    show("E1", "recuérdame que tengo partido de tenis el domingo")
    println("sonda c.1231 ok")
}

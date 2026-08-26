import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1233 (lateral (e) «salir en (bici|bicicleta)» de la
 * auditoría c.1227 cl.XXX deporte). PRE medido: T1–T5 NULL (olvido
 * silencioso; el paseo/salida en bicicleta es el plan vehicular de dos
 * ruedas — bicis no van al taller, son el propio vehículo). Guards G1–G4
 * NULL correctos (negación/pretérito «salí»/declarativa/verbo mutilado).
 * R1–R2 regresiones (jugar c.1228 / partido c.1231) HIT estables.
 * Determinista (sondeo real del motor vía run_probe.sh), sin IA fingida,
 * CERO UI.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} <- $t")
    }
    // T1–T5: capturas «salir en Bici» (vehículo de dos ruedas; las rutas
    // ocasionales — la bici que se saca el domingo es un compromiso
    // determinista, no una banalidad)
    show("T1", "salir en bici por la mañana")
    show("T2", "salir en bici el domingo")
    show("T3", "salir en bicicleta por la tarde")
    show("T4", "voy a salir en bici")
    show("T5", "salir en bici")
    // G1–G4: guards NULL correctos (negación, pretérito, declarativa,
    // verbo mutilado [salir-se])
    show("G1", "no salir en bici")
    show("G2", "salí en bici ayer")
    show("G3", "salir en bici es bueno")
    show("G4", "salgamos en bici")
    // R1–R2: regresiones hermanas (jugar c.1228, partido c.1231)
    show("R1", "jugar al fútbol el sábado")
    show("R2", "partido de tenis el domingo")
}

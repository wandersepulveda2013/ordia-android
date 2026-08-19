import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    fun probe(text: String) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1723939200000L)
        )
        if (intent == null) {
            println("  NULL        | $text")
        } else {
            println("  ${intent.kind} dueAt=${intent.dueAt != null} | title='${intent.title}' | $text")
        }
    }
    println("=== CAPTURAS ESPERADAS (falta + infinitivo) ===")
    listOf(
        "falta comprar detergente",
        "falta pagar la renta",
        "falta llamar al banco mañana",
        "hace falta comprar leche",
        "hace falta renovar el seguro",
        "falta hacer la compra",
        "falta ir al gimnasio",
        "falta reunión con el equipo",
        "falta llevar el coche al taller"
    ).forEach(::probe)
    println("=== CONTROLES (NO deben capturar o no vía wrapper falta) ===")
    listOf(
        // temporal: "falta una hora" no es tarea
        "falta una hora para la reunión",
        "faltan cinco minutos",
        // sustantivo: falta = foul/error
        "cometió una falta grave",
        // negación: no falta = no es necesario
        "no falta comprar detergente",
        // conversación casual
        "me falta tu apoyo",
        // control afirmativo existente (debe seguir TASK)
        "recuerda comprar leche",
        "tengo que comprar pan"
    ).forEach(::probe)
}

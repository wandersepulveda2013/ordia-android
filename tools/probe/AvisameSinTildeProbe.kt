import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda c.1067: envolventes hermanas «avisame»/«notificame» SIN TILDE
// (escritura real en notificaciones de chat, donde el teclado omite la
// tilde). La lateral quedó registrada en c.1065 (medida CALL 0.57 en la
// sonda EnvelopeSinTildeProbe c.1055). Con tilde, «avísame»/«notifícame»
// gobiernan REMINDER vía piso c.619 + guard c.652 + bono 0.25 y despojan
// el título vía extractTitle; sin tilde, la envolvente era invisible en
// TODOS los puntos (piso, guard, bono, título) y el verbo subordinado
// enrutaba como acción autónoma (o NULL si no tenía piso propio). Misma
// clase de defecto que «recuerdame» (c.1065), resuelta con la misma
// alternancia de tilde en lockstep (5 puntos, incluido
// WRAPPER_NEGATION_SPAN de la UNIÓN SU c.1064).
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun s(t: String) {
        val i = a(t)
        if (i != null) println("$t -> ${i.kind} conf=${i.confidence} title='${i.title}'")
        else println("$t -> NULL")
    }
    println("== avisame SIN TILDE (objetivo del ciclo: paridad con tilde = REMINDER) ==")
    listOf(
        "avisame llamar a mamá",
        "avisame comprar pan",
        "avisame revisar el correo",
        "avisame ir al médico",
        "avisame mañana de la reunión",
        "avisame cuando llegue el paquete"
    ).forEach { s(it) }
    println("== notificame SIN TILDE ==")
    listOf(
        "notificame llamar a mamá",
        "notificame comprar pan",
        "notificame pagar la luz mañana"
    ).forEach { s(it) }
    println("== referencia CON TILDE (comportamiento pinado) ==")
    listOf(
        "avísame llamar a mamá",
        "avísame comprar pan",
        "avísame pagar la luz mañana",
        "notifícame llamar a mamá",
        "notifícame comprar pan"
    ).forEach { s(it) }
    println("== guard wrapper+no (UNIÓN c.1064) sin tilde ==")
    listOf(
        "avisame no se que",
        "avisame no sé qué",
        "avisame no llamar a mamá",
        "notificame no se que",
        "avísame no se que",
        "avísame no llamar a mamá"
    ).forEach { s(it) }
    println("== anti-overreach / regresiones ==")
    listOf(
        "avisame",
        "notificame",
        "Avisame llamar a mamá",
        "me avisaste ayer",
        "avisame pagar la luz mañana"
    ).forEach { s(it) }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda c.1065: envolvente «recuerdame» SIN TILDE (escritura real en
// notificaciones de chat, donde el teclado omite la tilde). La lateral quedó
// medida NULL en la sonda EnvelopeSinTildeProbe (c.1055) y el pin
// CortarLosPelosPluralTest documenta «la variante sin tilde sigue fuera».
// Con tilde, «recuérdame X» gobierna TASK vía piso c.613 + guard c.652 y
// despoja el título vía extractTitle; sin tilde, la envolvente es invisible
// en TODOS los puntos (piso, guard, bono, título) y el verbo subordinado
// enruta como acción autónoma (o NULL si no tiene piso propio).
// También se miden (sólo registro, fuera de alcance del ciclo) las hermanas
// «avisame»/«notificame» sin tilde.
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun s(t: String) {
        val i = a(t)
        if (i != null) println("$t -> ${i.kind} conf=${i.confidence} title='${i.title}'")
        else println("$t -> NULL")
    }
    println("== recuerdame SIN TILDE (objetivo del ciclo: paridad con tilde = TASK) ==")
    listOf(
        "recuerdame llamar a mamá",
        "recuerdame comprar pan",
        "recuerdame revisar el correo",
        "recuerdame cortarme los pelos mañana",
        "recuerdame pagar el arriendo mañana",
        "recuerdame ir al médico"
    ).forEach { s(it) }
    println("== referencia CON TILDE (comportamiento pinado) ==")
    listOf(
        "recuérdame llamar a mamá",
        "recuérdame comprar pan",
        "recuérdame revisar el correo"
    ).forEach { s(it) }
    println("== hermanas sin tilde (REGISTRO, fuera de alcance) ==")
    listOf(
        "avisame llamar a mamá",
        "notificame llamar a mamá"
    ).forEach { s(it) }
}

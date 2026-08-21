import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId

// Sonda exploratory (desc. c.797): frases cotidianas reales que el asistente
// podría haber caído al menú genérico "Puedo organizar tu día". Fixture vacío:
// el routing correcto no depende del dato.
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    val phrases = listOf(
        "cuantas tareas tengo",
        "que tarea es mas larga",
        "que tarea tengo primero",
        "cuando termino hoy",
        "cuanta carga tengo hoy",
        "tengo muchas tareas",
        "como va mi dia",
        "que proyectos tengo",
        "cuales son mis tareas",
        "tengo cosas urgente",
        "que pospongo",
        "posponer algo",
        "cual es mi mas posponible",
        "que rutinas tengo",
        "cuales son mis proyectos",
        "que habitos tengo",
        "que tiempo tengo",
        "cuantas pendientes tengo",
        "que se me olvidaba",
        "que mes pasado hice",
        "tiempos libres hoy",
        "que debo hacer ahora",
        "que horario tengo libre",
        "cuanto tiempo me falta",
        "cuando estoy libre"
    )
    var gaps = 0
    for (p in phrases) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val fallback = ans.text.startsWith("Puedo organizar tu día") || ans.text.startsWith("Puedo organizar tu dia")
        if (fallback) {
            gaps++
            println("[GAP] $p")
        } else {
            println("[ok] $p -> ${ans.action}")
        }
    }
    println("=== RESUMEN: $gaps GAPs de ${phrases.size} ===")
}

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
        // c.798 p.3: formas «ver …» hermanas del listado «tareas».
        "ver tareas",
        "ver las tareas",
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
        "cuando estoy libre",
        // c.801/c.802: tanda extendida — imperativos, recuento, pinned,
        // marcadores de agenda, hueco y carga.
        "cuanto me falta",
        "cuando termino manana",
        "que tengo fijado",
        "dime que hacer",
        "que no debo olvidar",
        "que viene esta semana",
        "que me espera hoy",
        "algo importante que olvide",
        "me queda poco tiempo",
        "cuanto falta",
        "que tan llena es mi semana",
        "cuales tareas son urgente",
        "que es urgente",
        "que tengo pendiente mañana",
        "cuando tienes tiempo libre",
        "que notas tengo fijadas",
        "que me recomiendas hacer",
        "que es lo mas importante",
        "que hice ayer",
        "que hice hoy",
        "ensename mis tareas",
        "muestrame mis rutinas",
        "que tengo para el lunes",
        "como voy esta semana",
        "cuanto me queda de carga"
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

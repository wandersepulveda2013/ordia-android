import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId

// Sonda de descubrimiento (desc. c.792): familias de ENTIDAD que la búsqueda
// cubre (TASK/NOTE/HABIT/PROJECT) pero el asistente podría no routear — caer al
// menú genérico «Puedo organizar tu día» cuando hay ruta honesta (OPEN_SEARCH
// rama c.788) es mentira por omisión cruzada. Fixture deliberadamente vacía:
// el routing correcto no depende del dato.
fun main() {
    val now = System.currentTimeMillis()
    val zone = ZoneId.of("America/Bogota")
    val phrases = listOf(
        // familias de entidad de búsqueda (SearchKind)
        "mis habitos",
        "los habitos",
        "mis rutinas",
        "las rutinas",
        "mis notas",
        "búsqueda de notas",
        "mis proyectos",
        "los proyectos",
        // scopes temporales (igual que SearchEngine DateScope)
        "ayer", "anteayer", "antier",
        "semana pasada", "próxima semana", "pasado mañana",
        "mes pasado", "próximo mes", "este mes",
        "el finde", "los fines de semana",
        "los lunes", "los viernes",
        // franjas del día
        "tareas de madrugada", "tareas por la tarde", "tareas por la noche",
        // atributos (ya cubiertos — sanity no GAP)
        "recurrentes", "marcadas", "completadas",
        "notas fijadas", "prioridad alta"
    )
    var gaps = 0
    for (p in phrases) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val menu = ans.text.startsWith("Puedo organizar tu día") || ans.text.startsWith("Puedo organizar tu dia")
        val openSearch = ans.action == com.ordia.app.assistant.AssistantAction.OPEN_SEARCH
        if (menu) gaps++
        val tag = when {
            openSearch -> "ok→SEARCH"
            menu -> "GAP-menu"
            else -> "ok"
        }
        val t = if (menu || openSearch) "" else "  [${ans.action}]"
        println(String.format("%-30s %-12s %s", p, tag, t))
    }
    println("=== RESUMEN: $gaps GAP de ${phrases.size} consultas ===")
}

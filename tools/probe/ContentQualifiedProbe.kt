// Sonda persistida c.792: familia "tareas/pendientes de|del|de la <contenido>"
// (calificador por contenido, no temporal) comparando SearchEngine vs
// AssistantEngine. Residuo documentado c.784 del conector temporal «bare».
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.SearchEngine
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction

fun main() {
    val tasks = listOf(
        TaskEntity(id = 1, title = "Examen de matemáticas"),
        TaskEntity(id = 2, title = "El proyecto final"),
        TaskEntity(id = 3, title = "Limpiar la casa"),
        TaskEntity(id = 4, title = "Proyecto final de química"),
        TaskEntity(id = 5, title = "Tarea sin calificador")
    )
    val queries = listOf(
        "tareas de matemáticas",
        "tareas del proyecto",
        "tareas de la casa",
        "tareas de química",
        "pendientes de la casa",
        "pendientes con matemáticas"
    )
    for (q in queries) {
        val searchRes = SearchEngine.search(q, tasks, emptyList<com.ordia.app.data.local.ProjectEntity>(), emptyList<com.ordia.app.data.local.NoteEntity>(), emptyList<com.ordia.app.data.local.HabitEntity>(), emptyList<com.ordia.app.data.local.ConversationEntity>())
            .filter { it.kind == com.ordia.app.domain.SearchKind.TASK }
        val a = AssistantEngine.answer(q, tasks, emptyList(), emptyList())
        val gap = (searchRes.isNotEmpty() && a.relatedTaskIds.isEmpty()) ||
            (searchRes.isNotEmpty() && a.action == AssistantAction.NONE && a.text.startsWith("Puedo"))
        println(q.padEnd(28) + " -> SEARCH ids=" + searchRes.map { it.id } + " | ASSISTANT action=" + a.action + " textStart=" + a.text.take(45).replace("\n","") + " ... " + (if (gap) "<<< GAP" else ""))
    }
}

// Sonda persistida c.794: familia «notas de|del|de la <contenido>»
// (calificador por contenido sobre la superficie de notas, que el asistente
// NO recibe) comparando SearchEngine vs AssistantEngine. Hermana de la sonda
// persistida c.792 (tareas de <contenido>) y de c.788 (notas fijadas).
// El listado desnudo («notas»/«mis notas») lo cubre c.793 hermano
// (isNotesListingQuery); esta sonda se enfoca en el calificador de contenido.
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.SearchEngine
import com.ordia.app.domain.SearchKind
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction

fun main() {
    val notes = listOf(
        NoteEntity(id = 1, title = "Ideas de matemáticas", body = "teorema y práctica"),
        NoteEntity(id = 2, title = "Apuntes de la reunión", body = "acuerdos del martes"),
        NoteEntity(id = 3, title = "Lista de la casa", body = "sábanas y toallas"),
        NoteEntity(id = 4, title = "Nota suelta", body = "sin calificador")
    )
    val queries = listOf(
        "notas de matemáticas",
        "notas de la reunión",
        "notas del trabajo",
        "notas de la casa",
        "mis notas de química",
        "notas fijadas",
        "tareas de matemáticas",
        "notas",
        "notas de hoy"
    )
    for (q in queries) {
        val searchRes = SearchEngine.search(q, emptyList<TaskEntity>(), emptyList(), notes, emptyList(), emptyList())
            .filter { it.kind == SearchKind.NOTE }
        val a = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())
        val gap = searchRes.isNotEmpty() && (a.action != AssistantAction.OPEN_SEARCH)
        println(q.padEnd(26) + " -> SEARCH ids=" + searchRes.map { it.id } +
            " | ASSISTANT action=" + a.action + " textStart=" + a.text.take(40).replace("\n", "") +
            " ... " + (if (gap) "<<< GAP" else ""))
    }
}

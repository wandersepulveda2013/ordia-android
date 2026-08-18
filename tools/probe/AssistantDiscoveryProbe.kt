import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.CommitmentOwner
import java.time.ZoneId

fun main() {
    val now = System.currentTimeMillis()
    val zone = ZoneId.of("America/Bogota")
    // Empty task list: forces most "list"-style branches to their empty/honest path,
    // so a phrase that ROUTES correctly still gives a specific answer, not the menu.
    val tasks = emptyList<TaskEntity>()
    val convos = emptyList<ConversationEntity>()
    val commits = emptyList<CommitmentEntity>()

    // Everyday Spanish phrases a confused/tired user might type. Each SHOULD route to
    // a specific intent. We flag any that fall to the generic "Puedo organizar tu día" menu.
    val phrases = listOf(
        "que hago ahora",
        "que sigo",
        "a que hora es mi proxima cita",
        "tengo algo pronto",
        "tengo algo pronto hoy",
        "me queda tiempo",
        "tengo tiempo",
        "tengo hueco",
        "tengo un rato libre",
        "tengo un hueco",
        "que puedo hacer rapido",
        "algo rapido",
        "tengo 20 minutos",
        "tengo veinte minutos",
        "que hago en 15 minutos",
        "como voy",
        "como voy hoy",
        "voy bien o mal",
        "que pase si pospongo",
        "puedo posponer algo",
        "que puedo dejar para manana",
        "que es lo mas importante",
        "lo mas importante",
        "que es urgente",
        "hay algo urgente",
        "tengo algo urgente",
        "que no puedo dejar para despues",
        "por donde empiezo",
        "por donde arranco",
        "que hago primero",
        "que hago primero hoy",
        "en que gasto mi tiempo",
        "en que estoy gastando tiempo",
        "tengo algo esta semana",
        "que tengo esta semana",
        "que tengo esta manana",
        "tengo algo esta manana",
        "que viene despues",
        "cual es la siguiente",
        "cual hago",
        "cual hago primero",
        "cual me conviene hacer",
        "que me recomiendas",
        "recomiendame algo",
        "ayudame a decidir",
        "no se por donde empezar",
        "estoy abrumado",
        "tengo mucho que hacer",
        "por donde me recomiendas empezar"
    )

    val engine = AssistantEngine
    var genericCount = 0
    for (p in phrases) {
        val ans = engine.answer(p, tasks, convos, commits, now, zone).text
        val isGeneric = ans.startsWith("Puedo organizar tu día")
        if (isGeneric) genericCount++
        val tag = if (isGeneric) "GENERIC ❌" else "ok"
        val preview = ans.replace('\n', ' ').take(90)
        println("[$tag] \"$p\" -> $preview")
    }
    println("\n=== $genericCount / ${phrases.size} cayeron al menú genérico ===")
}

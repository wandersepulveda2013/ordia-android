import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun desc(t: String) = a(t)?.let { "${it.kind} ${it.confidence} | ${it.title}" } ?: "NULL"
    listOf(
        "cortarme los pelos este viernes",
        "cortar los pelos ma\u00F1ana",
        "cortarse los pelos",
        "cortarle los pelos al ni\u00F1o",
        "vale, cortarme los pelos hoy",
        "cortarle los pelos al perro",
        "recu\u00E9rdame cortarme los pelos ma\u00F1ana",
        // guards
        "no cortarme los pelos",
        "me cort\u00E9 los pelos",
        "quiz\u00E1 cortarme los pelos",
        "el corte de los pelos",
        "no voy a cortarme los pelos",
        "cortar la comunicaci\u00F3n",
        // regresiones
        "cortarme el pelo",
        "cortar el cabello",
        "cortarle el pelo al ni\u00F1o"
    ).forEach { println(desc(it)) }
}

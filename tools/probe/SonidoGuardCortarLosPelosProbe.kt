import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun s(t: String) {
        val i = a(t)
        if (i != null) println("COLATERAL HIT: $t -> ${i.kind}")
        else println("ok NULL: $t")
    }
    listOf(
        "no recoger los pelos",
        "me quit\u00E9 los pelos",
        "recoge los pelos",
        "tener el pelo largo",
        "un hombre con pelo largo"
    ).forEach { s(it) }
    val ok = a("con pelo largo")
    if (ok != null) println("COLATERAL HIT: con pelo largo -> ${ok.kind}")
    else println("ok NULL: con pelo largo")
}

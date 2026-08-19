import com.ordia.app.context.ContextIntentEngine
import java.time.Instant
import java.time.ZoneId

// Auditoría del ítem P2 pendiente desde c.592: ¿extractDateTime soporta fracción
// de palabra sobre anclas de punto medio ("al mediodía y media" → 12:30,
// "a medianoche y cuarto" → 00:15) con paridad al parser (explicitTimeData)?
fun main() {
    val zone = ZoneId.systemDefault()
    fun LocalDateTime(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime()
    fun fmt(epoch: Long?) = epoch?.let { LocalDateTime(it).toString() } ?: "null"
    val m = ContextIntentEngine::class.java.declaredMethods
        .first { it.name.startsWith("extractDateTime") }
    m.isAccessible = true
    listOf(
        "reunión al mediodía y media",       // esperado hoy 12:30
        "entrega a medianoche y cuarto",     // esperado hoy 00:15
        "descanso al mediodía",              // control: hoy 12:00 (canonico + past-safe)
        "examen a medianoche",               // control: 00:00 (past-safe roll si ya pasó)
        "alarmar a medianoche y media",      // esperado hoy 00:30
        "paseo al mediodía y cuarto"         // esperado hoy 12:15
    ).forEach {
        val r = m.invoke(ContextIntentEngine, it.lowercase()) as Long?
        println("  \"$it\" -> ${fmt(r)}")
    }
    println("DONE")
}

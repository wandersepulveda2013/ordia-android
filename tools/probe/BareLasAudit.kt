import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.domain.NaturalTaskParser
import java.time.Instant
import java.time.ZoneId

// Re-audición pendiente (BACKLOG): guard anti-cuenta "las N" desnuda (c.442/596/600/630).
// Verifica: cuentas preservadas (null en motor), citas reales desnudas SÍ fechadas.
fun main() {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    fun fmt(epoch: Long?) = epoch?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString() } ?: "null"
    val m = ContextIntentEngine::class.java.declaredMethods.first { it.name.startsWith("extractDateTime") }
    m.isAccessible = true
    listOf(
        // cuentas/objetos (esperado: null en motor, sin fecha en parser)
        "matemáticas las 4",
        "física las 2",
        "comprar las 3 cajas",
        "hacer las 4 tareas",
        "el código las 3",
        "guardian las 2",
        // citas desnudas legítimas (esperado: fecha hoy/mañana con hora)
        "cena las 3",
        "examen las 5 de la tarde",
        "reunión las 2 y media",
        "cita las 10 en punto",
        "llamada las 7 de la noche"
    ).forEach {
        val eng = m.invoke(ContextIntentEngine, it.lowercase()) as Long?
        val par = NaturalTaskParser.parse(it, now, zone)
        println("  \"$it\" | motor=${fmt(eng)} | parser=${fmt(par.dueAt)} | title=\"${par.title}\"")
    }
    println("DONE")
}

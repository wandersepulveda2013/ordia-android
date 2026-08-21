// Sonda persistente c.821 — familia "recuperación de tareas olvidadas"
// (paráfrasis de las pendientes más antiguas por createdAt). RED antes de la
// rama isForgottenQuery: 8/10 caían al menú genérico. Tras la implementación:
// 0 GAPs. Si vuelve a aparecer 1 GAP → exit 1 (regresión de ruta honesta).
// Exclusiones honestas ya ruteadas por otras ramas: «que tengo olvidado»
// (olvido urgente) y «que llevo posponiendo» (rama de posponer).
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import java.time.ZoneId
import kotlin.system.exitProcess

fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    val phrases = listOf(
        "que tareas tengo olvidadas", "tareas abandonadas", "tareas viejas",
        "lo que siempre dejo para despues", "mis tareas mas antiguas",
        "lo mas antiguo que tengo pendiente", "que tengo pendiente desde hace mucho"
    )
    var gaps = 0
    for (p in phrases) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val ok = !ans.text.startsWith("Puedo organizar tu día") &&
            !ans.text.startsWith("Puedo organizar tu dia") && "nada olvidado" in ans.text
        if (!ok) { gaps++; println("[GAP regresión] $p -> ${ans.text.take(80)}") }
        else println("[ok vacío] $p")
    }
    val tasks = listOf(
        TaskEntity(id = 1, title = "Revisar presupuesto", createdAt = 10_000L),
        TaskEntity(id = 2, title = "Devolver libro", createdAt = 5_000L),
        TaskEntity(id = 3, title = "Renovar seguro", createdAt = 20_000L),
        TaskEntity(id = 4, title = "Pagar factura", createdAt = 30_000L)
    )
    val ans = AssistantEngine.answer("mis tareas mas antiguas", tasks, emptyList(), emptyList(), now, zone)
    // orden estricto: más antiguo primero (createdAt ascendente, id en empate)
    val ordered = ans.text.indexOf("«Devolver libro»") >= 0 &&
        ans.text.indexOf("«Devolver libro»") < ans.text.indexOf("«Revisar presupuesto»") &&
        ans.text.indexOf("«Revisar presupuesto»") < ans.text.indexOf("«Renovar seguro»")
    if (!ordered) { gaps++; println("[GAP orden] ${ans.text.take(120)}") }
    else println("[ok orden] más antiguo primero")
    println("=== RESUMEN: $gaps GAPs ===")
    if (gaps > 0) exitProcess(1)
}

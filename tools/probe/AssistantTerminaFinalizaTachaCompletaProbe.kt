import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.1089): lateral ABIERTA (1) de la
// auditoría c.1085 — acción marca-hecha «termina/finaliza/tacha/completa
// <tarea>». PRE efímera: 4 GAPs al MENÚ medidos. Cierre: MARK_DONE_IMPL_*
// wired en markDoneCapture (hermana de marca-como-hecha c.997 y complete-past
// c.998). Guards: «haz la tarea» (bivalente) FUERA por diseño; pelada,
// negativo y sin-coincidencia → guía SIN acción. Si alguna cerrada vuelve a
// GAP o un guard empieza a capturar, la sonda falla (exit 1).
fun main() {
    val tasks = listOf(
        TaskEntity(id = 1, title = "Llamar al banco"),
        TaskEntity(id = 2, title = "Pagar la luz"),
        TaskEntity(id = 3, title = "Revisión médica")
    )
    var fail = 0
    fun check(tag: String, expected: AssistantAction?, req: String, payloadId: Long? = null) {
        val a = try { AssistantEngine.answer(req, tasks, emptyList(), emptyList()) } catch (t: Throwable) { println("FAIL $tag ex=${t.message}"); fail++; return }
        val ok = if (expected == null) a.action == AssistantAction.NONE
                 else a.action == expected && (payloadId == null || a.actionPayload == payloadId.toString())
        println((if (ok) "OK  " else "FAIL") + " $tag -> " + a.action)
        if (!ok) fail++
    }
    check("captura-termina", AssistantAction.COMPLETE_TASK, "termina llamar al banco", 1)
    check("captura-finaliza", AssistantAction.COMPLETE_TASK, "finaliza pagar la luz", 2)
    check("captura-tacha", AssistantAction.COMPLETE_TASK, "tacha revisión médica", 3)
    check("captura-completa", AssistantAction.COMPLETE_TASK, "completa la de llamar al banco", 1)
    check("guard-haz-tarea", null, "haz la tarea")
    check("guard-pelada", null, "termina la tarea")
    check("guard-negativo", null, "no termina la luz")
    check("guard-sin-coincidencia", null, "termina limpiar la cocina")
    check("regresion-marca-hecha", AssistantAction.COMPLETE_TASK, "marca como hecha llamar al banco", 1)
    check("regresion-cuando-completa", null, "completa la tarea")
    if (fail == 0) println("PROBE OK") else { println("PROBE FAIL: $fail"); exitProcess(1) }
}

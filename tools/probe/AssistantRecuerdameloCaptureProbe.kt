import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAnswer
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.1088): lateral ABIERTA (3) de la
// auditoría c.1085 — «recuérdamelo: <contenido>». PRE con sonda efímera
// /tmp/probe1085/Probe.kt: la forma CON contenido caía al MENÚ (mentira
// por omisión); la pelada exacta/temporal caía a la guía honesta c.996.
// Cierre: remindMeLoCapture → CREATE_TASK; pelada y temporal pelado →
// guía SIN acción (c.996); negativo → menú (nunca capturar lo contrario);
// hermanas (c.987) intactas. Si alguna frase cerrada vuelve a GAP o un
// guard empieza a capturar, la sonda falla (exit 1).
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    val captures = listOf(
        "recuérdamelo: llamar al banco",
        "recuérdamelo pagar la luz",
        "recuérdamelo: comprar leche",
        "recuerdamelo: llamar a Ana",
    )
    val guards = listOf(
        "recuérdamelo", // pelada → guía honesta, NUNCA CREATE_TASK
        "recuérdamelo mañana", // temporal pelado → guía honesta (c.996)
        "recuérdamelo no ir al banco", // negativo → menú
        "recuérdame llamar al banco", // regresión hermana (c.987) — sigue CREATE_TASK
    )
    var unexpected = 0
    for (p in captures) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.CREATE_TASK) {
            unexpected++
            println("[GAP inesperado] $p -> ${ans.action}")
        } else println("[ok] $p -> CREATE_TASK")
    }
    for (p in guards.subList(0, 2)) {
        val ans: AssistantAnswer = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.NONE || !ans.text.contains("Escríbeme qué")) {
            unexpected++
            println("[guard roto] $p -> ${ans.action} («${ans.text}») no-guía")
        } else println("[ok guard] $p -> guía sin acción")
    }
    run {
        val p = guards[2]
        val ans: AssistantAnswer = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.NONE) {
            unexpected++
            println("[guard roto] $p -> ${ans.action} (negativo no debe capturar)")
        } else println("[ok guard] $p -> NONE (negativo no captura)")
    }
    run {
        val p = guards[3]
        val ans: AssistantAnswer = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.CREATE_TASK) {
            unexpected++
            println("[regresión rota] $p -> ${ans.action}")
        } else println("[ok regresión] $p -> CREATE_TASK")
    }
    println("captures:${captures.size} guards:${guards.size} base:0 inesperados:$unexpected")
    if (unexpected != 0) exitProcess(1)
}

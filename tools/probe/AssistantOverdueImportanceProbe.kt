import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (desc. ex-c.801→c.803): ronda sugerida por
// c.800 — «vencidas importantes» + «resumen del día» + «horario libre
// avanzado». Fixture vacío: el routing correcto no depende del dato
// (GAP = cae al menú genérico, la mentira por omisión). c.803 cerró los
// 4 GAPs limpios (resume mi día / cómo va mi jornada / cuánto falta por
// hacer hoy / qué huecos tengo hoy); queda uno AMBIGUO documentado y se
// tolera: si vuelve a aparecer GAP en otra frase la sonda falla.
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    // Frases cerradas (regresión: deben seguir sin caer al menú).
    val phrases = listOf(
        // Familia A: vencidas importantes (combinación overdue × prioridad)
        "que tengo de vencido importante",
        "que tareas vencidas importantes tengo",
        "lo vencido mas urgente",
        "que es lo mas importante que tengo vencido",
        "vencidas urgentes",
        "tareas atrasadas importantes",
        // Familia B: resumen del día (variantes cotidianas)
        "resume mi dia",
        "dame el resumen de hoy",
        "como va mi jornada",
        "cuanto me queda hoy",
        "cuanto falta por hacer hoy",
        // Familia C: horario libre avanzado
        "a que hora tengo hueco",
        "cuando tengo tiempo libre hoy",
        "que huecos tengo hoy",
        "cuando estoy libre en la tarde",
        "tengo algo de tiempo esta noche",
        "me queda tiempo libre"
    )
    // AMBIGUO (documentado c.801): «qué me queda por hacer hoy» puede ser
    // what-now (la hermana «qué me falta» NO excluye agenda/scope y rutea
    // a sugerencia) o agenda-hoy («¿qué tengo hoy?»). Se deja fuera por
    // guarda conservadora (no se rutea a la rama equivocada).
    val ambiguous = listOf("que me queda por hacer hoy")
    var gaps = 0
    var unexpected = 0
    for (p in phrases) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val fallback = ans.text.startsWith("Puedo organizar tu día") || ans.text.startsWith("Puedo organizar tu dia")
        if (fallback) { gaps++; unexpected++; println("[GAP inesperado] $p") }
        else println("[ok] $p -> ${ans.action}")
    }
    for (p in ambiguous) {
        println("[ambiguo tolerado] $p")
        gaps++
    }
    println("=== RESUMEN: $gaps GAPs de ${phrases.size + ambiguous.size} (tolerados: ${ambiguous.size}) ===")
    if (unexpected > 0) exitProcess(1)
}

// POST c.1093 — sonda persistida: auditoría probabilística sobre las guardas
// del verbo-family (olvide/seOlvido/recuérdamelo). Medida PRE con sonda
// efímera: 25/27 guardas OK y 2 GAP (interrogativa colgante «se olvidó
// comprar leche?» / «olvidé comprar leche?» capturaban). Fix: guarda en
// `olvideCapture` — contenido crudo terminado en «?» → MENÚ, espejo del
// ancla «^» (deliberado: interrogativa ambigua, NUNCA capturar), evaluada
// tras la guía pelada. Con el fix deben fallar 0 de 27.
// Uso: bash tools/run_probe.sh tools/probe/AssistantOlvideInterrogativaProbe.kt
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction

data class C(val name: String, val expected: AssistantAction, val phrase: String)

fun main() {
    val cases = listOf(
        // A. Interrogativas (deliberado menú, nunca capturar)
        C("int-lleva-se-olvido", AssistantAction.NONE, "¿se olvidó comprar leche?"),
        C("int-se-olvido-de", AssistantAction.NONE, "¿se olvidó de comprar leche?"),
        C("int-lleva-olvide", AssistantAction.NONE, "¿olvidé comprar leche?"),
        C("int-recuerdamelo", AssistantAction.NONE, "¿recuérdamelo: llamar a mamá?"),
        C("int-trailing-se-olvido", AssistantAction.NONE, "se olvidó comprar leche?"),
        C("int-trailing-olvide", AssistantAction.NONE, "olvidé comprar leche?"),
        // B. Huecas -> guía honesta, sin acción
        C("bare-se-olvido", AssistantAction.NONE, "se olvidó"),
        C("bare-se-olvido-algo", AssistantAction.NONE, "se olvidó algo"),
        C("bare-se-olvido-nada", AssistantAction.NONE, "se olvidó nada"),
        C("bare-olvide", AssistantAction.NONE, "olvidé"),
        C("bare-olvide-algo", AssistantAction.NONE, "olvidé algo"),
        C("bare-recuerdamelo", AssistantAction.NONE, "recuérdamelo"),
        C("bare-recuerdamelo-manana", AssistantAction.NONE, "recuérdamelo mañana"),
        // C. Negativas -> nunca capturar lo contrario
        C("neg-no-se-olvido", AssistantAction.NONE, "no se olvidó comprar leche"),
        C("neg-no-olvide", AssistantAction.NONE, "no olvidé comprar leche"),
        C("neg-recuerdamelo-no", AssistantAction.NONE, "recuérdamelo no hacer algo"),
        C("neg-se-olvido-de-nada", AssistantAction.NONE, "se olvidó de nada"),
        // D. Capturas afirmativas deliberadas (regresiones)
        C("aff-se-olvido", AssistantAction.CREATE_TASK, "se olvidó comprar leche"),
        C("aff-se-olvido-de", AssistantAction.CREATE_TASK, "se olvidó de comprar leche"),
        C("aff-se-olvido-sin-tilde", AssistantAction.CREATE_TASK, "se olvido comprar leche"),
        C("aff-olvide", AssistantAction.CREATE_TASK, "olvidé comprar leche"),
        C("aff-olvide-sin-tilde", AssistantAction.CREATE_TASK, "olvide comprar leche"),
        C("aff-recuerdamelo", AssistantAction.CREATE_TASK, "recuérdamelo: llamar a mamá"),
        C("aff-recuerdamelo-que", AssistantAction.CREATE_TASK, "recuérdamelo llamar a mamá"),
        C("aff-no-olvides", AssistantAction.CREATE_TASK, "no olvides comprar leche"),
        // E. Pronominales «se me/nos» (recapitulación c.797, viven fuera)
        C("pron-se-me-olvido", AssistantAction.NONE, "se me olvidó la cita"),
        C("pron-se-nos-olvido", AssistantAction.NONE, "se nos olvidó la cita")
    )
    var fail = 0; var ok = 0
    for (c in cases) {
        val got = AssistantEngine.answer(c.phrase, emptyList(), emptyList(), emptyList())
        val pass = got.action == c.expected
        if (!pass) { fail++; println("GAP  ${c.name} :: esperaba ${c.expected} -> obtuvo ${got.action} :: «${c.phrase}»") }
        else { ok++ }
    }
    println("AUDIT POST c.1093: $ok ok / $fail gap de ${cases.size}")
    if (fail > 0) kotlin.system.exitProcess(1)
}

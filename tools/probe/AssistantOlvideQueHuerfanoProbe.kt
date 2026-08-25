// c.1095 — sonda persistente POST: «que» huérfano en olvide (lateral
// ABIERTA de la auditoría c.1093). PRE medido con la sonda efímera
// /tmp/probe1095/Probe.kt: «olvidé que» / «se olvidó que» creaban TAREA
// BASURA «que» (CREATE_TASK payload=«que») y «olvidé que llamar a mamá»
// capturaba con título residual «que llamar a mamá». Fix: UN punto —
// despojar LEADING_QUE del crudo (val c.993 reutilizado) + guarda
// isNullOrEmpty («olvidé que» despoja TODO el crudo → vacío).
// POST: títulos limpios, pelada-con-«que» → guía honesta, negación tras
// «que» llega a la guarda, byte-equivalencia de guards/regresiones pineada.
@file:Suppress("ClassName")
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantAnswer
import com.ordia.app.data.local.TaskEntity

fun main() {
    val tasks = listOf(TaskEntity(id = 1, title = "Llamar al banco"))
    var ok = true
    fun check(name: String, expected: AssistantAction, got: AssistantAnswer) {
        val pass = got.action == expected
        if (!pass) ok = false
        println("${if (pass) "OK  " else "FAIL"} $name -> ${got.action} payload=${got.actionPayload}")
    }
    fun checkPayload(name: String, expected: String, got: AssistantAnswer) {
        val pass = got.actionPayload == expected
        if (!pass) ok = false
        println("${if (pass) "OK  " else "FAIL"} $name -> payload=${got.actionPayload}")
    }
    // Huecos resueltos: pelada CON «que» → guía honesta (NONE), nunca tarea basura
    check("olvide-que-pelada", AssistantAction.NONE, AssistantEngine.answer("olvidé que", tasks, emptyList(), emptyList()))
    check("se-olvido-que-pelada", AssistantAction.NONE, AssistantEngine.answer("se olvidó que", tasks, emptyList(), emptyList()))
    // Capturas con «que» subordinado → título limpio (espejo c.993)
    checkPayload("olvide-que-llamar", "llamar a mamá", AssistantEngine.answer("olvidé que llamar a mamá", tasks, emptyList(), emptyList()))
    checkPayload("olvide-que-comprar", "comprar leche", AssistantEngine.answer("olvidé que comprar leche", tasks, emptyList(), emptyList()))
    checkPayload("se-olvido-que-pagar", "tenía que pagar la luz", AssistantEngine.answer("se olvidó que tenía que pagar la luz", tasks, emptyList(), emptyList()))
    checkPayload("olvide-sin-tilde-que", "avisar al banco", AssistantEngine.answer("olvide que avisar al banco", tasks, emptyList(), emptyList()))
    // Anti-overreach: negación tras «que» llega a la guarda
    check("olvide-que-negado", AssistantAction.NONE, AssistantEngine.answer("olvidé que no llamar a mamá", tasks, emptyList(), emptyList()))
    // Byte-equivalencia: guards/capturas sin «que» intactas
    check("olvide-sin-que-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvidé comprar leche", tasks, emptyList(), emptyList()))
    check("remindme-que-sigue", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdame que comprar leche", tasks, emptyList(), emptyList()))
    check("no-olvides-que-sigue", AssistantAction.CREATE_TASK, AssistantEngine.answer("no olvides que comprar leche", tasks, emptyList(), emptyList()))
    check("olvide-algo-guia", AssistantAction.NONE, AssistantEngine.answer("olvidé algo", tasks, emptyList(), emptyList()))
    check("olvide-interrog-acentuada", AssistantAction.NONE, AssistantEngine.answer("¿olvidé comprar leche?", tasks, emptyList(), emptyList()))
    check("recuerdamelo-dos-puntos", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdamelo: comprar leche", tasks, emptyList(), emptyList()))
    if (ok) println("POST sonda c.1095 que-huerfano: OK (13/13)") else println("POST sonda c.1095 que-huerfano: FAIL")
}

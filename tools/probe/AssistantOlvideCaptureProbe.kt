// c.1090 — sonda persistente: «olvidé/olvide <contenido>» declaración pasada
// (auditoría c.1085, lateral (4), la última restante). PRE medida en /tmp:
// 2 candidatas al MENÚ (mentira por omisión). POST: captura honesta con el
// botón (UI → vm.addSmartTask), pelada → guía, imperativo guard intacto.
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
        println("${if (pass) "OK  " else "FAIL"} $name -> ${got.action}")
    }
    check("olvide-con-contenido", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvidé comprar leche", tasks, emptyList(), emptyList()))
    check("olvide-sin-tilde", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvide comprar leche", tasks, emptyList(), emptyList()))
    check("olvide-de", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvidé de pagar el recibo", tasks, emptyList(), emptyList()))
    check("olvide-bare", AssistantAction.NONE, AssistantEngine.answer("olvidé", tasks, emptyList(), emptyList()))
    check("olvide-algo-bare", AssistantAction.NONE, AssistantEngine.answer("olvidé algo", tasks, emptyList(), emptyList()))
    check("negativo-no-olvide", AssistantAction.NONE, AssistantEngine.answer("no olvidé comprar leche", tasks, emptyList(), emptyList()))
    check("guard-noOlvides-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("no olvides comprar leche", tasks, emptyList(), emptyList()))
    check("regresion-recuerdamelo-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdamelo: comprar leche", tasks, emptyList(), emptyList()))
    if (ok) println("POST sonda olvidé: OK (8/8)") else println("POST sonda olvidé: FAIL")
}

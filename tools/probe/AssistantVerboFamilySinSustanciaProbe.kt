// c.1093 — sonda persistente POST: guarda de contenido sin sustancia en
// el verbo-family (olvide / seOlvido / recuérdamelo). PRE medido con la
// sonda efímera /tmp/probe1093/Probe.kt: «olvide de» / «se olvidó de»
// creaban tarea basura «de»; «recuérdamelo a las 5» / «…mañana por la
// mañana» / «olvidé mañana» creaban tarea basura temporal. POST: guía
// honesta (NONE), capturas reales intactas, anti-overreach pineado.
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
    // Huecos resueltos → guía honesta (NONE)
    check("olvide-de-solo", AssistantAction.NONE, AssistantEngine.answer("olvide de", tasks, emptyList(), emptyList()))
    check("se-olvido-de-solo", AssistantAction.NONE, AssistantEngine.answer("se olvidó de", tasks, emptyList(), emptyList()))
    check("recuerdamelo-a-las-5", AssistantAction.NONE, AssistantEngine.answer("recuérdamelo a las 5", tasks, emptyList(), emptyList()))
    check("recuerdamelo-manana-por-la-manana", AssistantAction.NONE, AssistantEngine.answer("recuérdamelo mañana por la mañana", tasks, emptyList(), emptyList()))
    check("recuerdamelo-por-la-noche", AssistantAction.NONE, AssistantEngine.answer("recuérdamelo por la noche", tasks, emptyList(), emptyList()))
    check("olvide-manana", AssistantAction.NONE, AssistantEngine.answer("olvidé mañana", tasks, emptyList(), emptyList()))
    // Capturas reales intactas
    check("olvide-real-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvidé comprar leche", tasks, emptyList(), emptyList()))
    check("se-olvido-real-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("se olvidó de la cita", tasks, emptyList(), emptyList()))
    check("recuerdamelo-real-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdamelo: llamar al banco", tasks, emptyList(), emptyList()))
    // Anti-overreach: temporal DENTRO de contenido real sigue capturando
    check("recuerdamelo-llamar-a-las-5", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdamelo llamar a las 5", tasks, emptyList(), emptyList()))
    check("recuerdamelo-comprar-manana", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdamelo comprar leche mañana", tasks, emptyList(), emptyList()))
    // Guards preexistentes intactos
    check("olvide-algo-guia", AssistantAction.NONE, AssistantEngine.answer("olvidé algo", tasks, emptyList(), emptyList()))
    check("no-olvides-captura", AssistantAction.CREATE_TASK, AssistantEngine.answer("no olvides comprar leche", tasks, emptyList(), emptyList()))
    if (ok) println("POST sonda c.1093 sin-sustancia: OK (13/13)") else println("POST sonda c.1093 sin-sustancia: FAIL")
}

// c.1096 — sonda persistente POST: mayúsculas con tilde SÍ capturan en el
// engine del asistente (lateral ABIERTA (B) de la auditoría SU c.1093).
// PRE medido con la sonda efímera /tmp/probe1096/Probe.kt (base 24bc0c1):
// 10/10 mayúsculas acentuadas → NONE honesto (captura perdida; «(?i)» en
// JVM es ASCII-only); controles 7/7 ya correctos.
// Fix UN punto mecánico: «(?i)» → «(?iu)» en los 60 patrones de
// AssistantEngine.kt (UNICODE_CASE añade fold Unicode; ASCII idéntico).
// POST: capturas hermanas de las minúsculas, guards byte-equivalentes
// (pelada-guía, interrogativa, negación, despedida), minúsculas intactas.
@file:Suppress("ClassName")
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantAnswer
import com.ordia.app.data.local.TaskEntity

fun main() {
    val tasks = listOf(TaskEntity(id = 1, title = "Llamar al banco", dueAt = 1_800_000_000_000L))
    var ok = true
    fun check(name: String, expected: AssistantAction, got: AssistantAnswer) {
        val pass = got.action == expected
        if (!pass) ok = false
        println("${if (pass) "OK  " else "FAIL"} $name -> ${got.action} payload=${got.actionPayload}")
    }
    // Capturas: mayúsculas acentuadas → acción hermana de las minúsculas
    check("caps-recuerdame", AssistantAction.CREATE_TASK, AssistantEngine.answer("RECUÉRDAME LLAMAR A MAMÁ", tasks, emptyList(), emptyList()))
    check("caps-recuerdamelo", AssistantAction.CREATE_TASK, AssistantEngine.answer("RECUÉRDAMELO: COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("caps-olvide", AssistantAction.CREATE_TASK, AssistantEngine.answer("OLVIDÉ COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("caps-se-olvido", AssistantAction.CREATE_TASK, AssistantEngine.answer("SE OLVIDÓ COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("caps-avisame", AssistantAction.CREATE_TASK, AssistantEngine.answer("AVÍSAME MAÑANA DE LLAMAR AL BANCO", tasks, emptyList(), emptyList()))
    check("caps-escribeme-nota", AssistantAction.CREATE_NOTE, AssistantEngine.answer("ESCRÍBEME UNA NOTA: IDEAS", tasks, emptyList(), emptyList()))
    check("caps-apuntame-nota", AssistantAction.CREATE_NOTE, AssistantEngine.answer("APÚNTAME UNA NOTA: IDEAS", tasks, emptyList(), emptyList()))
    check("caps-marca-hecha", AssistantAction.COMPLETE_TASK, AssistantEngine.answer("MÁRCALA COMO HECHA LLAMAR AL BANCO", tasks, emptyList(), emptyList()))
    check("caps-anade-tarea", AssistantAction.CREATE_TASK, AssistantEngine.answer("AÑADE UNA TAREA: COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("caps-pospon", AssistantAction.POSTPONE_TASK, AssistantEngine.answer("POSPÓN LLAMAR AL BANCO PARA MAÑANA", tasks, emptyList(), emptyList()))
    // Guards byte-equivalentes: NUNCA capturar lo contrario
    check("pelada-caps-guia", AssistantAction.NONE, AssistantEngine.answer("OLVIDÉ ALGO", tasks, emptyList(), emptyList()))
    check("interrog-caps", AssistantAction.NONE, AssistantEngine.answer("¿OLVIDÉ COMPRAR LECHE?", tasks, emptyList(), emptyList()))
    check("negada-caps", AssistantAction.NONE, AssistantEngine.answer("NO OLVIDÉ COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("despedida-caps", AssistantAction.NONE, AssistantEngine.answer("NO ME OLVIDES", tasks, emptyList(), emptyList()))
    // Controles: minúsculas hermanas + caps SIN tilde (ya capturaban) intactas
    check("minusculas-hermana", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdame llamar a mamá", tasks, emptyList(), emptyList()))
    check("caps-sin-tilde-ya", AssistantAction.CREATE_TASK, AssistantEngine.answer("NO OLVIDES COMPRAR LECHE", tasks, emptyList(), emptyList()))
    check("caps-sin-tilde-nota-ya", AssistantAction.CREATE_NOTE, AssistantEngine.answer("GUARDA UNA NOTA: IDEAS", tasks, emptyList(), emptyList()))
    if (ok) println("POST sonda c.1096 mayusculas-tilde: OK (17/17)") else println("POST sonda c.1096 mayusculas-tilde: FAIL")
}

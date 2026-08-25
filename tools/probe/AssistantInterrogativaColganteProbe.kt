// c.1099 — sonda persistente POST: «?» de cierre SIN «¿» de apertura
// (interrogativa colgante, teclado laxo) nunca captura en las hermanas de
// olvide (última lateral ABIERTA de la auditoría SU c.1093).
// PRE medido con la sonda efímera /tmp/probe1099/Probe.kt (base 277aaa6):
// 11 candidatas: 10 capturas con «?» residual en el payload (recuérdame,
// recuérdamelo, avísame, escríbeme-nota, hazme-nota, apúntame, guárdame
// esto, añade-tarea, no-olvides, quiero-que) + 1 pelada basura literal
// («recuérdame ?» → CREATE_TASK payload «?», c.969 violada); 11 pines ya
// correctos (olvide/se-olvido c.1093, match-engines, hermanas sin «?»,
// «¿…?» completa, negación, despedida).
// Fix UN punto espejo de la guarda c.1093 de olvideCapture: contenido
// crudo terminado en «?» → MENÚ (null), tras la guía pelada y antes de la
// guarda negativa, en 7 capturas (takeNote, remindMe, noOlvides,
// avisaMe, quieroQue, remindMeLo, createTask). POST: 22/22 byte-exacta.
@file:Suppress("ClassName")
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.assistant.AssistantAction
import com.ordia.app.data.local.TaskEntity

fun main() {
    val tasks = listOf(TaskEntity(id = 1, title = "Llamar al banco", dueAt = 1_800_000_000_000L))
    var ok = true
    fun check(name: String, expected: AssistantAction, got: com.ordia.app.assistant.AssistantAnswer, expectedPayload: String? = null) {
        val pass = got.action == expected && (expectedPayload == null || got.actionPayload == expectedPayload)
        if (!pass) ok = false
        println("${if (pass) "OK  " else "FAIL"} $name -> ${got.action} payload=[${got.actionPayload}]")
    }
    // Colgantes → MENÚ honesto (espejo doctrina c.1093)
    check("recuerdame?", AssistantAction.NONE, AssistantEngine.answer("recuérdame llamar a mamá?", tasks, emptyList(), emptyList()))
    check("recuerdamelo?", AssistantAction.NONE, AssistantEngine.answer("recuérdamelo: comprar leche?", tasks, emptyList(), emptyList()))
    check("avisame?", AssistantAction.NONE, AssistantEngine.answer("avísame mañana de llamar al banco?", tasks, emptyList(), emptyList()))
    check("escribeme-nota?", AssistantAction.NONE, AssistantEngine.answer("escríbeme una nota: ideas?", tasks, emptyList(), emptyList()))
    check("hazme-nota?", AssistantAction.NONE, AssistantEngine.answer("hazme una nota: ideas?", tasks, emptyList(), emptyList()))
    check("apuntame-nota?", AssistantAction.NONE, AssistantEngine.answer("apúntame una nota: ideas?", tasks, emptyList(), emptyList()))
    check("guardame-esto?", AssistantAction.NONE, AssistantEngine.answer("guárdame esto: llamar al banco?", tasks, emptyList(), emptyList()))
    check("anade-tarea?", AssistantAction.NONE, AssistantEngine.answer("añade una tarea: comprar leche?", tasks, emptyList(), emptyList()))
    check("no-olvides?", AssistantAction.NONE, AssistantEngine.answer("no olvides comprar leche?", tasks, emptyList(), emptyList()))
    check("quiero-que?", AssistantAction.NONE, AssistantEngine.answer("quiero que me recuerdes llamar a mamá?", tasks, emptyList(), emptyList()))
    // Pelada con «?»: NUNCA tarea basura literal «?» (c.969)
    check("recuerdame-pelada?", AssistantAction.NONE, AssistantEngine.answer("recuérdame ?", tasks, emptyList(), emptyList()))
    // Pines byte-idénticos: olvide-family (c.1093), match-engines, hermanas, «¿…?», negación, despedida
    check("olvide?-pin1093", AssistantAction.NONE, AssistantEngine.answer("olvidé comprar leche?", tasks, emptyList(), emptyList()))
    check("se-olvido?-pin1093", AssistantAction.NONE, AssistantEngine.answer("se olvidó comprar leche?", tasks, emptyList(), emptyList()))
    check("marca-hecha?-inmune", AssistantAction.COMPLETE_TASK, AssistantEngine.answer("márcala como hecha llamar al banco?", tasks, emptyList(), emptyList()), "1")
    check("pospon?-inmune", AssistantAction.POSTPONE_TASK, AssistantEngine.answer("pospón llamar al banco para mañana?", tasks, emptyList(), emptyList()), "1")
    check("recuerdame-sin-?", AssistantAction.CREATE_TASK, AssistantEngine.answer("recuérdame llamar a mamá", tasks, emptyList(), emptyList()), "llamar a mamá")
    check("olvide-sin-?", AssistantAction.CREATE_TASK, AssistantEngine.answer("olvidé comprar leche", tasks, emptyList(), emptyList()), "comprar leche")
    check("nota-sin-?", AssistantAction.CREATE_NOTE, AssistantEngine.answer("escríbeme una nota: ideas", tasks, emptyList(), emptyList()), "ideas")
    check("olvide-interrog-completa", AssistantAction.NONE, AssistantEngine.answer("¿olvidé comprar leche?", tasks, emptyList(), emptyList()))
    check("que-olvide-interrog", AssistantAction.NONE, AssistantEngine.answer("¿qué olvidé?", tasks, emptyList(), emptyList()))
    check("negada-?", AssistantAction.NONE, AssistantEngine.answer("no olvidé comprar leche?", tasks, emptyList(), emptyList()))
    check("despedida-?", AssistantAction.NONE, AssistantEngine.answer("no me olvides?", tasks, emptyList(), emptyList()))
    if (ok) println("POST sonda c.1099 interrogativa-colgante: OK (22/22)") else println("POST sonda c.1099 interrogativa-colgante: FAIL")
}

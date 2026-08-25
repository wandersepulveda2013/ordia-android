// c.1068: sonda PRE/POST del junk AFIRMATIVO de envolvente modal de obligación
// (sub-lateral medida SU c.1064, doctrina aparte). PRE: 12/12 formas junk
// («tengo que es eso», «tengo que mañana», «hay que eso», «habría que eso»,
// «debería eso»…) → TASK 0.45 basura («Es eso», «Mañana», «Eso»). POST:
// 12/12 → NULL (guard obligationModalLacksInfinitive en el sitio del piso
// TASK); pins legítimos 15/15 intactos (infinitivo tras modal → TASK,
// captura fiel «no»+infinitivo, complementos nominales/temporales fuera
// del guard). Uso: bash tools/run_probe.sh tools/probe/JunkAfirmativoModalProbe.kt

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun s(t: String) {
        val i = a(t)
        if (i != null) println("$t -> ${i.kind} conf=${i.confidence} title='${i.title}'")
        else println("$t -> NULL")
    }
    println("== junk AFIRMATIVO candidato (modal de obligacion + NO infinitivo) ==")
    listOf(
        "tengo que es eso",
        "tengo que sí, claro",
        "tengo que mañana",
        "tengo q mañana",
        "hay que eso",
        "hay que mañana",
        "habría que eso",
        "tendría que mañana",
        "debería eso",
        "debería que mañana",
        "tengo que el lunes",
        "hay que sí"
    ).forEach { s(it) }
    println("== pins legitimos (NO deben cambiar) ==")
    listOf(
        "tengo que llamar a mamá",
        "tengo que comprar pan",
        "hay que comprar leche",
        "habría que comprar leche",
        "tendría que terminar el informe",
        "debería hacer copias de seguridad",
        "tengo que ir",
        "tengo que no llamar a mamá",
        "hay que no fumar",
        "no olvides las llaves",
        "recuérdame tu cumpleaños",
        "avísame mañana de la reunión",
        "avisame cuando llegue el paquete",
        "cancelar la cita del dentista",
        "falta una hora"
    ).forEach { s(it) }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// c.1213: sonda PRE/POST persistida (esto lado) — «podar árbol(es)/arbusto(s)»
// objetos extensos del piso jardín (lateral ABIERTA de MI auditoría c.1211,
// clase VIGESIMOSÉPTIMA jardinería/plantas). PRE medido con la misma sonda
// efímera (/tmp/probe1213.kt): C1–C4 NULL; POST: C1–C4 HIT HOUSEHOLD 0.45 con
// títulos exactos; G1–G5 NULL byte-idénticos (negación, pretérito, duda,
// nominalización, diminutivo); R1–R3 jardín/rosal/setos regresión intacta;
// E1 envoltura TASK intacta.
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun nullOrNot(id: String, t: String) {
        val r = a(t)
        println(if (r == null) "["+id+"] NULL  <- "+t else "["+id+"] HIT "+r.kind+" "+r.confidence+" |"+r.title+"|  <- "+t)
    }
    listOf(
        "C1" to "podar el árbol",
        "C2" to "podar los árboles",
        "C3" to "podar el arbusto",
        "C4" to "podar los arbustos",
        "G1" to "no podar los árboles",
        "G2" to "ya podé el árbol",
        "G3" to "quizás pode el árbol",
        "G4" to "la poda del árbol",
        "G5" to "podar el arbolito",
        "R1" to "podar el jardín",
        "R2" to "podar el rosal",
        "R3" to "podar los setos",
        "E1" to "recuérdame podar el árbol"
    ).forEach { (id, t) -> nullOrNot(id, t) }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Gate-evaluación PRE de laterales DÉBILES de MI auditoría c.1252 (clase
// XXXVI belleza/cuidado personal) — c.1259, paridad c.1255 (gate-sonda
// DÉBIL música → NO-IMPLEMENTAR). Sonda PERSISTIDA (convención
// c.1194/c.1227/c.1255). CERO producto en gate negativo.
// (d) barba: «recortar/afeitar/arreglar/cortar(me) la barba» — verbo
//     acotado al objeto (auditoría: DÉBIL, pretérito/negación ya guardan).
// (e) tinte/tratamiento facial: «tinte (del pelo)», «tratamiento facial» —
//     nominal acotado; EVITAR dominio médico («tratamiento de <enfermedad>»).
// D = directas bare; E = envolventes (¿preservan ya título+dueAt?);
// G = guards (NULL esperado); R = regresiones (HIT esperado).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    // (d) barba — directas
    tag("D1", "recortar la barba mañana")
    tag("D2", "afeitarme la barba mañana")
    tag("D3", "arreglarme la barba el sábado")
    tag("D4", "cortarme la barba el viernes")
    // (e) tinte/tratamiento facial — directas
    tag("D5", "tinte del pelo el miércoles")
    tag("D6", "hacerme el tinte el jueves")
    tag("D7", "tratamiento facial mañana")
    tag("D8", "hacerme un tratamiento facial el lunes")
    // Envolventes — ¿ya preservan el contenido completo (título + dueAt)?
    tag("E1", "recuérdame recortarme la barba mañana")
    tag("E2", "recuérdame hacerme el tinte el jueves")
    tag("E3", "tengo que afeitarme la barba mañana")
    tag("E4", "recuérdame el tratamiento facial del lunes")
    // Guards (NULL esperado)
    tag("G1", "no voy a recortarme la barba")
    tag("G2", "me recorté la barba ayer")
    tag("G3", "mi hermano se afeita la barba los lunes")
    tag("G4", "el tratamiento de la diabetes")
    tag("G5", "el tratamiento para la alergia funciona")
    tag("G6", "el tinte")
    tag("G7", "habla del tratamiento facial")
    tag("G8", "la barba de mi padre es blanca")
    tag("G9", "recortar gastos")
    tag("G10", "recortar la foto del documento")
    // Regresiones (HIT esperado por fórmulas heredadas)
    tag("R1", "peluquería el martes")
    tag("R2", "la manicura el viernes")
    tag("R3", "depilación el lunes")
    tag("R4", "recuérdame mañana")
    tag("R5", "cita con el médico mañana")
    tag("R6", "comprar leche")
    tag("R7", "hacer yoga")
    tag("R8", "pagar la luz el día 4")
    println("sonda c.1259 ok")
}

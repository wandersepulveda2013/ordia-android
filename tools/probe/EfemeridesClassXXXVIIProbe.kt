import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE c.1261: AUDITORÍA clase TRIGÉSIMA SÉPTIMA (XXXVII) EFEMÉRIDES/
 * CELEBRACIONES FAMILIARES (cumpleaños, aniversario, boda, bautizo, comunión,
 * graduación, santo/onomástica, cena de Navidad, celebrar). CERO producto
 * (descubrimiento — convención c.1127/c.1165/c.1194/c.1225/c.1252).
 * Targets: medir NULL/HIT sobre HEAD 2b3719c (auditoría, no fix).
 * Envolventes: ¿preserva ya la ruta «recuérdame/tengo que…» el contenido
 * íntegro (título + dueAt)? → insumo del gate de necesidad c.1233 futuro.
 * Guards: pretérito/duda/mención/polisemia/pregunta → NULL.
 * Regresiones: fórmulas heredadas (TASK/APPOINTMENT/SHOPPING/CALL/ERRAND/
 * EXERCISE/PAYMENT/MEETING) → HIT.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label ...NULL... $t")
        else {
            val due = i.dueAt != null
            println("$label [HIT] ${i.kind} | ${i.title} | dueAt=$due <- $t")
        }
    }
    // Targets (candidatas efemérides/celebraciones familiares)
    show("T1", "el cumpleaños de Ana el viernes")
    show("T2", "cumpleaños de mamá el 12")
    show("T3", "la boda de mi primo el sábado")
    show("T4", "el aniversario de mis padres el lunes")
    show("T5", "el bautizo del bebé el domingo")
    show("T6", "la comunión de Lucía en mayo")
    show("T7", "la graduación de Marta el jueves")
    show("T8", "la cena de Navidad con los suegros el 24")
    show("T9", "comprar el regalo de cumpleaños de Ana")
    show("T10", "ir a la boda de mi primo el sábado")
    show("T11", "fiesta de cumpleaños de Ana el sábado")
    show("T12", "el santo de mi madre el 15")
    show("T13", "cena de aniversario el sábado")
    show("T14", "celebrar el cumpleaños de Ana el viernes")
    // Envolventes (¿preservan ya el contenido?)
    show("E1", "recuérdame el cumpleaños de Ana el viernes")
    show("E2", "recuérdame la boda de mi primo el sábado")
    show("E3", "tengo que comprar el regalo de Ana mañana")
    // Guards (NULL esperado)
    show("G1", "el cumpleaños de Ana fue el viernes pasado")
    show("G2", "no sé si ir a la boda de mi primo")
    show("G3", "háblame de la boda real")
    show("G4", "la boda estuvo muy bonita")
    show("G5", "la graduación del alcohol es del 40%")
    show("G6", "el cumpleaños")
    show("G7", "qué día es el cumpleaños de Ana")
    show("G8", "celebrar la vida cada día")
    // Regresiones (HIT por fórmulas heredadas)
    show("R1", "recuérdame mañana")
    show("R2", "cita con el médico mañana")
    show("R3", "comprar leche")
    show("R4", "llamar a mamá")
    show("R5", "ir al banco mañana")
    show("R6", "hacer yoga")
    show("R7", "pagar la luz el día 4")
    show("R8", "reunión con el equipo mañana")
    println("sonda c.1261 ok")
}

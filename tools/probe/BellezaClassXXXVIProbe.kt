import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE c.1252: AUDITORÍA clase TRIGÉSIMA SEXTA (XXXVI) CUIDADO
 * PERSONAL/BELLEZA (peluquería, barbero, corte de pelo, manicura, pedicura,
 * depilación, cera, tinte, barba, tratamiento facial, uñas, cejas).
 * CERO producto (descubrimiento — convención c.1127/c.1165/c.1194/c.1225).
 * Targets: medir NULL/HIT sobre HEAD f17986f (auditoría, no fix).
 * Guards: negación/pretérito/declarativa/hablar → NULL; adjetivo/sustantivo
 * en contextos inertes → NULL.
 * Regresiones: fórmulas heredadas (TASK/EXERCISE/APPOINTMENT/SHOPPING/CALL/
 * HOUSEHOLD/PAYMENT/ERRAND) → HIT.
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
    // Targets (candidatas belleza/cuidado personal)
    show("T1", "cita en la peluquería el viernes")
    show("T2", "corte de pelo mañana")
    show("T3", "cita con el barbero el sábado")
    show("T4", "manicura a las cinco")
    show("T5", "depilación el lunes")
    show("T6", "cera el lunes")
    show("T7", "pedicura el viernes")
    show("T8", "recortar la barba mañana")
    show("T9", "afeitarme la barba mañana")
    show("T10", "hacerme las uñas el jueves")
    show("T11", "tinte del pelo el miércoles")
    show("T12", "tratamiento facial mañana")
    show("T13", "peluquería el martes")
    show("T14", "arreglarme las cejas el sábado")
    // Guards (NULL esperado)
    show("G1", "no voy a la peluquería mañana")
    show("G2", "fui al barbero ayer")
    show("G3", "la manicura fue el lunes")
    show("G4", "el corte de pelo se canceló")
    show("G5", "habla del corte de pelo")
    show("G6", "el salón de belleza está cerrado")
    show("G7", "el tinte")
    show("G8", "mi hermana se hace la manicura los lunes")
    // Regresiones (HIT por fórmulas heredadas)
    show("R1", "recuérdame mañana")
    show("R2", "cita con el médico mañana")
    show("R3", "comprar leche")
    show("R4", "llamar a mamá")
    show("R5", "pagar la luz el día 4")
    show("R6", "lavar al perro")
    show("R7", "hacer yoga")
    show("R8", "ir al gimnasio el lunes")
    println("sonda c.1252 ok")
}

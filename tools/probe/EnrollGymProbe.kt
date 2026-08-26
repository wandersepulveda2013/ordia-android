import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1230 (lateral (b) ABIERTA por la auditoría c.1227,
 * clase TRIGÉSIMA autocuidado/deporte — «apuntarme al gimnasio en enero»
 * medida 0.22 NULL en sonda efímera sobre HEAD 2e66959): la alta del
 * propio usuario en el gimnasio es de los gestos de autocuidado de mayor
 * peso (iniciar rutina, no solo asistir a ella). Distinta de las formas
 * contiguas: «apuntarse a <actividad>» → NOTE (c.856) e «inscribir a
 * <objeto>» mascotas/kids → EXERCISE (c.1165/c.1228 hermano). El lateral
 * son los enclíticos me/te/nos (NO «se») sobre el objeto gimnasio(s);
 * se cierra con piso acotado [EXERCISE_ENROLL_GYM_FLOOR] — zero keywords
 * nuevas (gate c.751: «gimnasio» ya existía en EXERCISE; «apuntar» vive
 * en NOTE como subcadena — el piso no requiere keyword). Lockstep 2
 * puntos (lección c.616): piso + plantilla matchEnrollGym en
 * extractTitle. Determinista (regex), sin IA fingida.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] <- $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | <- $t")
    }
    show("T1", "apuntarme al gimnasio en enero")
    show("T2", "apuntarte al gimnasio")
    show("T3", "apuntarnos al gimnasio")
    show("T4", "inscribirme al gimnasio")
    show("T5", "inscribirte al gimnasio")
    show("T6", "inscribirnos al gimnasio")
    show("G1", "me apunté al gimnasio el verano pasado")
    show("G2", "no me apuntaré al gimnasio")
    show("G3", "inscribirse en la academia de danza")
    show("G4", "háblame del gimnasio")
    show("G5", "inscribirse al gimnasio")
    show("R1", "ir al gimnasio")
    show("R2", "hacer yoga")
    show("R3", "apuntarse a clases de yoga")
    show("R4", "apuntar los gastos en el cuaderno")
    show("R5", "recuérdame apuntarme al gimnasio")
    show("R6", "tengo que apuntarme al gimnasio")
}

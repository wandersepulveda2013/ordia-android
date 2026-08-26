import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1232 (lateral (b) MEDIA ABIERTa por MI auditoría
 * c.1227 — clase TRIGÉSIMA deporte, `DeporteClassXXXProbe.kt`: «apuntarme
 * al gimnasio» media NULL 0.22 = keyword 0.12 + bono temporal 0.1 <
 * umbral; hermana de la histórica «gimnasio-alta» b-ter clase XV:
 * «dar de alta el gimnasio» media NULL por el acotado deliberado del
 * piso c.1139). Familia enroll-gimnasio (alta en el gimnasio / apuntarse
 * al gimnasio): el compromiso deportivo de enero por antonomasia, olvido
 * silencioso P1. «Apuntar» es BIVALENTE (anotar, apuntar a los niños al
 * fútbol NOTE c.714/c.856), así el piso se ACOTA al objeto «gimnasio»
 * (gate c.751: keyword-OBJETO «gimnasio» preexistente — CERO keywords
 * nuevas; «apuntar» ya vive en NOTE y alimenta TRIGGER_WORDS).
 * Determinista (regex), sin IA fingida.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} <- $t")
    }
    // Targets (capturas esperadas; (b) EXERCISE 0.45, título desde el verbo)
    show("T1", "apuntarme al gimnasio en enero")
    show("T2", "apuntarme al gimnasio el lunes")
    show("T3", "apuntar al gimnasio mañana")
    show("T4", "apuntarse al gimnasio este mes")
    show("T5", "dar de alta el gimnasio esta semana")
    show("T6", "apuntarme al gimnasio con Ana")
    // Guards (NULL esperado — anti-overreach)
    show("G1", "no me apuntaré al gimnasio")
    show("G2", "me apunté al gimnasio ayer")
    show("G3", "no apuntarme al gimnasio")
    show("G4", "dar de alta a un paciente")
    show("G5", "el gimnasio está cerrado")
    show("G6", "apuntarse un tanto en el partido")
    // Regresiones (HIT esperado — intactas)
    show("R1", "ir al gimnasio el lunes")
    show("R2", "apuntarse al curso de inglés")
    show("R3", "dar de baja el gimnasio mañana")
    show("R4", "apuntar a los niños al fútbol")
    show("R5", "hacer yoga los martes")
    show("R6", "dar de alta la luz del piso nuevo mañana")
    // Envolventes (TASK 0.45 por la policy envolvente, hermana c.613)
    show("E1", "recuérdame apuntarme al gimnasio el lunes")
    show("E2", "tengo que apuntarme al gimnasio en enero")
}

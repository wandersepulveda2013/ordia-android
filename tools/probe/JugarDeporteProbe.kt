import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1228 (lateral (a) FUERTE ABIERTA por MI auditoría
 * c.1227 — clase TRIGÉSIMA deporte, `DeporteClassXXXProbe.kt` D7 NULL
 * medido): «jugar (al|a la) <deporte>» es la sesión deportiva social
 * canónica del español hablado (el partido semanal con amigos = el
 * compromiso deportivo más olvidable). «Jugar» suelto es BIVALENTE
 * (cartas / niños / videojuegos / escondite), así el piso se ACOTA al
 * objeto deporte — keyword-OBJETO monosemántica (precedente «mueble»
 * c.1224, gate c.751: la keyword alimenta TRIGGER_WORDS; sin ella la
 * notificación ni llega al análisis). Lockstep TRES puntos (lección
 * c.616): keyword-OBJETO + piso acotado + plantilla matchJugarDeporte;
 * grafías preservadas (c.653 — «fútbol»/«futbol», «pádel»/«padel» doble
 * literal por la tilde-rompe-subcadena, lección c.1217). Determinista
 * (regex), sin IA fingida.
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
    // Targets (capturas esperadas EXERCISE 0.45, título desde el verbo)
    show("T1", "jugar al fútbol el sábado")
    show("T2", "jugar al tenis el domingo")
    show("T3", "jugar al pádel con los colegas")
    show("T4", "mañana jugar al baloncesto a las 7")
    show("T5", "jugar al voleibol el lunes")
    show("T6", "jugar futbol mañana")
    // Guards (NULL esperado — anti-overreach)
    show("G1", "no jugar al fútbol el sábado")
    show("G2", "jugué al fútbol ayer")
    show("G3", "jugar a las cartas con los abuelos")
    show("G4", "jugar al escondite con los niños")
    show("G5", "jugar con los niños en el parque")
    show("G6", "jugamos al fútbol el sábado")
    show("G7", "el fútbol de mañana se cancela")
    // Regresiones (HIT esperado)
    show("R1", "correr por el parque mañana")
    show("R2", "ir al gimnasio el lunes")
    show("R3", "hacer yoga los martes")
    show("R4", "cepillar al gato")
    // Envolventes (TASK 0.45 por la policy envolvente, hermana c.613)
    show("E1", "recuérdame jugar al fútbol el sábado")
    show("E2", "tengo que jugar al tenis mañana")
}

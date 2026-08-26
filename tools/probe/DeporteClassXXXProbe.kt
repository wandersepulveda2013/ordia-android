import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1227 (auditoría clase TRIGÉSIMA [XXX]: DEPORTE Y
 * ACTIVIDAD FÍSICA dichas como se hablan — dominio fresco, DISJUNTO de
 * todos los marcadores del hermano [su último es FIXED VERIFIED c.1226
 * «cepillar mascota»]; alternancia audit↔fix: hermano audit c.1225 →
 * fix c.1226; este lado audit c.1227). CERO cambio de producto: la sonda
 * MIDE la cobertura heredada del dominio EXERCISE (pisos: posición libre
 * `correr|entrenar|natación|pesas|campamento|extraescolar(es)? \w` +
 * `ir al gimnasio` + `hacer (yoga|pesas|deporte|ejercicio)`; keywords
 * EXERCISE c.1135/c.1146) para descubrir gaps reales (UNA por ciclo,
 * doctrina anti-overreach c.822/c.1165/c.1173/c.1194). R1–R8 regresiones
 * (incl. «cepillar al gato» byte-idéntica del hermano c.1226). Clases
 * I–XXIX cubiertas en BACKLOG; XXX es el siguiente dominio fresco
 * (deporte/ejercicio — EXERCISE es el kind menos auditado salvo laterales
 * c.1135 campamento / c.1146 extraescolar). Determinista (sondeo real del
 * motor vía run_probe.sh), sin IA fingida, CERO UI.
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
    // D1-D14: candidatas deporte/física (cobertura heredada esperada en las
    // primeras 6 [pisos EXERCISE existentes]; D7-D12/D14 gaps a medir)
    show("D1", "ir al gimnasio el lunes")
    show("D2", "correr por el parque mañana")
    show("D3", "entrenar con el equipo el jueves")
    show("D4", "hacer yoga los martes")
    show("D5", "natación los lunes")
    show("D6", "hacer pesas el miércoles")
    show("D7", "jugar al fútbol el sábado")
    show("D8", "apuntarme al gimnasio en enero")
    show("D9", "partido de tenis el domingo")
    show("D10", "ir a pilates el lunes")
    show("D11", "salir en bici por la mañana")
    show("D12", "entrenamiento de fútbol mañana")
    show("D13", "hacer deporte por la tarde")
    show("D14", "clase de yoga mañana")
    // G1-G8: controles NULL correctos (negación, duda, pretérito, declarativa,
    // pretérito sin keyword, verbo solo, sustantivo solo, sustantivo declarativa)
    show("G1", "no voy al gimnasio")
    show("G2", "no sé si iré al gimnasio mañana")
    show("G3", "fui al gimnasio ayer")
    show("G4", "el gimnasio está cerrado")
    show("G5", "jugué al fútbol ayer")
    show("G6", "correr")
    show("G7", "el partido de tenis")
    show("G8", "mi clase de yoga era buena")
    // R1-R8: regresiones (pisos vecinos + cierre hermano c.1226 byte-idéntico).
    // R1 en INFINITIVO canónico (pin CALL): la forma imperativa «llama a
    // mamá» es NULL (medido c.1227, comportamiento preexistente del piso
    // CALL infinitivo-scoped — lateral documentada, CERO fix en esta sonda).
    show("R1", "llamar a mamá")
    show("R2", "comprar leche")
    show("R3", "pagar la luz")
    show("R4", "cita con el médico mañana")
    show("R5", "llevar el coche al taller")
    show("R6", "bañar al perro")
    show("R7", "cepillar al gato")
    show("R8", "recuérdame llamar a papá")
}

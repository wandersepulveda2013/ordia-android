import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST «instalar la app» — candidata FUERTE de la auditoría cl.XXXI
 * tecnología (medida propia incidental del descarte convergente c.1236).
 * Gate c.751: «instalar» monosemántico (precedente c.752 votar / c.864
 * escanear / c.1032 configurar / c.1036 formatear) → floor acotado a
 * objeto app/software, CERO keywords-OBJETO, lockstep keyword-VERBO +
 * plantilla. Determinista (sondeo real del motor).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} <- $t")
    }
    // D: intención objetivo
    show("D1", "instalar la app de banca mañana")
    show("D2", "instalar la app nueva por la noche")
    show("D3", "instala la app del banco esta noche")
    show("D4", "instalar el software de contabilidad el sábado")
    show("D5", "instalar la app")
    // G: guardas anti-overreach
    show("G1", "no instalar la app")
    show("G2", "instalé la app ayer")
    show("G3", "la instalación de la app")
    show("G4", "instalar")
    show("G5", "quizá instale la app")
    // R: regresiones
    show("R1", "recuérdame llamar a papá")
    show("R2", "actualizar la app mañana")
    show("R3", "configurar el móvil nuevo")
    show("R4", "formatear el ordenador el sábado")
    show("R5", "escanear el informe mañana")
}

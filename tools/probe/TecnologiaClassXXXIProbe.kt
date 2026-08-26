import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1236 (auditoría clase TRIGÉSIMOPRIMERA [XXXI]:
 * TECNOLOGÍA/INFORMÁTICA dichas como se hablan — actualizar/app, copia de
 * seguridad, imprimir, escanear, reiniciar router, wifi, apagar/encender
 * ordenador, formatear, adaptadores — dominio fresco, DISJUNTO de todos
 * los marcadores del hermano [su c.1235 «entrenamiento de <deporte>» se
 * queda FUERA]). CERO cambio de producto: la sonda MIDE la cobertura
 * heredada del dominio (keywords/pisos TASK/SHOPPING/EXERCISE existentes)
 * para descubrir gaps reales (UNA por ciclo, doctrina anti-overreach
 * c.822/c.1165/c.1173/c.1194). Clases I–XXX ya cubiertas en BACKLOG; XXXI
 * es el siguiente dominio fresco. Determinista (sondeo real del motor vía
 * run_probe.sh), sin IA fingida, CERO UI.
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
    // D1-D14: candidatas tecnología/informática (cobertura heredada esperada
    // cuando un verbo/monosemántico como «imprimir/escanear» ya está en
    // keywords; D4+/D7+ medir posibles gaps de verbos bivalentes en posición
    // libre acotada por objeto)
    show("D1", "actualizar la app mañana")
    show("D2", "hacer la copia de seguridad el sábado")
    show("D3", "imprimir el contrato hoy")
    show("D4", "escanear el informe mañana")
    show("D5", "reiniciar el router esta tarde")
    show("D6", "conectar el wifi en casa")
    show("D7", "apagar el ordenador por la noche")
    show("D8", "encender la tablet")
    show("D9", "formatear el portátil mañana")
    show("D10", "comprar un adaptador nuevo mañana")
    show("D11", "subir las fotos a la nube esta noche")
    show("D12", "descargar el vídeo hoy")
    show("D13", "sincronizar el drive esta tarde")
    show("D14", "mi copia de seguridad mañana")
    // G1-G8: controles NULL correctos (negación, duda, pretérito, declarativo,
    // verbo solo, sustantivo solo, pretérito sin keyword, estado/declarativa)
    show("G1", "no actualizar la app")
    show("G2", "pensé en actualizar la app ayer")
    show("G3", "el router está apagado")
    show("G4", "imprimí el contrato ayer")
    show("G5", "imprimir")
    show("G6", "el wifi")
    show("G7", "la copia de seguridad tardó horas")
    show("G8", "el ordenador es bueno")
    // R1-R6: regresiones (vecinas TASK/SHOPPING/EXERCISE/CALL/ERRAND/PAYMENT)
    show("R1", "pagar la luz")
    show("R2", "comprar leche")
    show("R3", "llamar a mamá")
    show("R4", "ir al banco mañana")
    show("R5", "hacer la cama")
    show("R6", "recuérdame actualizar la app")
}

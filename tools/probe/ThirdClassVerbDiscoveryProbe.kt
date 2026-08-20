import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.721: TERCERA clase de formas cotidianas — verbos
 * de gestión de cierre/completitud (terminar/completar/organizar/redactar/
 * leer/escribir/corregir/subir/descargar/llenar) y verbos de hogar que faltan
 * en [ContextIntentEngine.HOUSEHOLD_VERBS] (tender/hacer cama/poner lavadora/
 * cortar césped/aspirar/quitar el polvo).
 *
 * Misma metodología que CommonVerbDiscoveryProbe (c.692, clase 1, CERRADA 8/8)
 * y ManagementVerbDiscoveryProbe (c.711, clase 2, CERRADA 10/10): frases de
 * captura real (objeto + fecha) y controles. NO es un test; su salida alimenta
 * el BACKLOG (un ítem/forma por ciclo, anti-overreach).
 */
@Suppress("DEPRECATION")
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Candidatos: verbos de gestión de cierre/completitud con fecha
        "terminar el informe mañana",
        "completar el formulario el lunes",
        "organizar el armario hoy",
        "redactar el correo mañana",
        "leer el contrato hoy",
        "escribir el informe el martes",
        "corregir el ensayo mañana",
        "traducir el documento mañana",
        "actualizar el currículum mañana",
        "subir el documento hoy",
        "descargar la factura mañana",
        "archivar el contrato el viernes",
        "llenar la solicitud mañana",
        // Candidatos: verbos de hogar no cubiertos por HOUSEHOLD_VERBS
        "tender la ropa hoy",
        "hacer la cama mañana",
        "poner la lavadora esta tarde",
        "cortar el césped el sábado",
        "aspirar la alfombra mañana",
        "quitar el polvo hoy",
        // Controles: negación / duda / sustantivo / pasado / verbo suelto /
        // chat deben permanecer NULL (anti-overreach).
        "no terminar el informe mañana",
        "quizá terminar el informe mañana",
        "el término del informe fue ayer",
        "terminé el informe ayer",
        "terminar",
        "no organizar el armario hoy",
        "quizá organizar el armario hoy",
        "la organización del armario fue ayer",
        "organicé el armario ayer",
        "organizar",
        "no poner la lavadora esta tarde",
        "quizá poner la lavadora esta tarde",
        "poner",
        // Controles c.722 (forma "actualizar"): negada/duda/sustantivo/
        // pasado/verbo suelto deben permanecer NULL.
        "no actualizar el currículum mañana",
        "quizá actualizar el currículum mañana",
        "la actualización del documento fue ayer",
        "actualicé el currículum ayer",
        "actualizar",
        // Controles c.723 (forma "archivar"): negada/duda/sustantivo/
        // pasado/verbo suelto deben permanecer NULL.
        "no archivar el contrato el viernes",
        "quizá archivar el contrato el viernes",
        "el archivo del contrato fue ayer",
        "archivé el contrato ayer",
        "archivar",
        // Controles c.724 (forma "subir"): negada/duda/sustantivo/
        // pasado/verbo suelto deben permanecer NULL.
        "no subir el documento hoy",
        "quizá subir el documento hoy",
        "la subida del documento fue ayer",
        "subí el documento ayer",
        "subir",
        // Controles c.725 (forma "descargar"): negada/duda/sustantivo/
        // pasado/verbo suelto deben permanecer NULL.
        "no descargar la factura mañana",
        "quizá descargar la factura mañana",
        "la descarga de la factura fue ayer",
        "descargué la factura ayer",
        "descargar",
        "y al final terminé contento jeje",
        "hola buenos días"
    )
    for (c in cases) {
        @Suppress("DEPRECATION")
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

package com.ordia.app.context

/**
 * Intención organizativa clasificada a partir de un evento contextual.
 * Solo se crea cuando el texto coincide con una intención permitida
 * y pasa el filtro de privacidad.
 */
data class ContextIntent(
    /** Identificador único del intent */
    val id: String,
    /** Categoría de la intención */
    val kind: ContextIntentKind,
    /** Título descriptivo extraído */
    val title: String,
    /** Fecha/hora estimada (puede ser nula si no se detectó) */
    val dueAt: Long? = null,
    /** Confianza de la clasificación (0.0 - 1.0) */
    val confidence: Float,
    /** Fuente original del evento */
    val source: ContextCaptureSource,
    /** Paquete de la aplicación de origen */
    val sourcePackage: String? = null,
    /** Marca de tiempo de creación */
    val createdAtMs: Long = System.currentTimeMillis(),
    /** Detalles adicionales estructurados */
    val details: Map<String, String> = emptyMap()
)

/**
 * Categorías de intención organizativa permitidas.
 * Cualquier texto que no coincida con estas categorías se descarta silenciosamente.
 */
enum class ContextIntentKind(val displayName: String, val keywords: List<String>) {
    TASK("Tarea", listOf("tengo que", "debo", "toca", "hay que", "pendiente", "recuérdame",
        "no olvides", "necesito", "preparar", "terminar", "hacer", "completar",
        "entregar", "enviar", "llamar", "avisar", "confirmar", "reservar",
        "comprar", "traer", "llevar", "conseguir", "buscar", "pedir",
        "acordarme de", "acordarse de")),
    EVENT("Evento", listOf("evento", "cita", "reunión", "conferencia", "sesión",
        "taller", "clase", "curso", "entrevista", "webinar")),
    APPOINTMENT("Cita", listOf("cita con", "cita médica", "dentista", "doctor", "médico",
        "especialista", "consulta", "revisión", "chequeo", "terapia",
        "psicólogo", "nutricionista")),
    MEETING("Reunión", listOf("reunión con", "reunión de", "junta", "encuentro",
        "quedar con", "vernos", "nos vemos", "quedamos")),
    STUDY("Estudio", listOf("estudiar", "estudio", "examen", "prueba", "curso",
        "tarea escolar", "lección", "práctica", "ejercicio",
        "repasar", "preparar examen")),
    SHOPPING("Compra", listOf("comprar", "supermercado", "mercado", "tienda",
        "farmacia", "mandado", "despensa", "víveres",
        "ir al super", "ir a comprar")),
    ERRAND("Diligencia", listOf("diligencia", "trámite", "banco", "oficina",
        "correo", "paquete", "devolver", "recoger",
        "dejar", "pagar", "factura", "recibo")),
    CALL("Llamada", listOf("llamar a", "llamar por teléfono", "hablar con",
        "llamada", "telefonear")),
    PAYMENT("Pago", listOf("pagar", "pago", "transferencia", "depósito",
        "recarga", "suscripción", "cuota", "mensualidad")),
    DELIVERY("Entrega", listOf("entregar", "entrega", "envío", "paquete",
        "pedido", "encomienda")),
    TRAVEL("Viaje", listOf("viaje", "viajar", "vuelo", "vuelo", "hotel",
        "reservación", "itinerario", "destino", "maletas")),
    VISIT("Visita", listOf("visitar", "visita", "ir a casa de", "pasar por",
        "ir a ver", "recibir")),
    EXERCISE("Ejercicio", listOf("ejercicio", "gimnasio", "entrenar", "entreno",
        "yoga", "correr", "natación", "pesas", "rutina",
        "hacer deporte", "ir al gimnasio")),
    HABIT("Hábito", listOf("hábito", "rutina", "diario", "todos los días",
        "cada día", "semanal", "mañana", "lectura")),
    REMINDER("Recordatorio", listOf("recordatorio", "avísame", "notifícame",
        "recuérdame", "alarma", "temporizador")),
    DEADLINE("Fecha límite", listOf("fecha límite", "deadline", "vencimiento",
        "límite", "tope", "último día", "finaliza")),
    PROJECT("Proyecto", listOf("proyecto", "plan", "iniciativa", "meta",
        "objetivo", "propósito")),
    GOAL("Meta", listOf("meta", "objetivo", "propósito", "aspiración",
        "lograr", "conseguir")),
    NOTE("Nota útil", listOf("apuntar", "anotar", "nota", "idea", "ocurrencia",
        "apunte", "recordar que")),
    COMMITMENT_PERSONAL("Compromiso personal", listOf("prometo", "me comprometo",
        "voy a", "pienso", "planeo", "quiero", "intentaré")),
    COMMITMENT_WORK("Compromiso laboral", listOf("informe", "reporte", "presentación",
        "cliente", "proyecto de", "trabajo de", "asignación")),
    HOUSEHOLD("Actividad doméstica", listOf("limpiar", "ordenar", "cocinar",
        "lavar", "planchar", "arreglar", "reparar",
        "jardín", "mantenimiento", "tramitar"));

    companion object {
        /** Términos que disparan análisis de intención (acumulados de todas las categorías) */
        val TRIGGER_WORDS: List<String> = ContextIntentKind.entries.flatMap { it.keywords }
    }
}

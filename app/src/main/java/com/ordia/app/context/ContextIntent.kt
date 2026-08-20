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
        "organizar", "redactar", "leer", "escribir", "corregir", "traducir",
        "actualizar", "archivar", "subir", "descargar", "llenar", "entregar", "enviar", "llamar", "avisar", "confirmar", "reservar",
        "comprar", "traer", "llevar", "conseguir", "buscar", "pedir", "solicitar",
        "coger", "publicar", "recordar a", "acordarme de", "acordarse de")),
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
        "dejar", "pagar", "factura", "recibo",
        // c.718: parada de trámite (lockstep keyword↔piso `ERRAND_STOPBY_FLOOR`,
        // lección c.639/c.717). El destino lo acota el piso; el keyword sólo
        // suma base y permanece por debajo del umbral sin piso, así no roba
        // el keyword genérico histórico de [VISIT].
        "pasar por")),
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
        "jardín", "mantenimiento", "tramitar",
        // c.639: verbos domésticos comunes que faltaban en la cobertura léxica
        // (mismo lockstep que scoreSpecificPatterns / piso / extractTitle).
        // c.727: "tender" — mismo lockstep (c.639) por la sonda tercera clase
        // (14/19): quehacer doméstico canónico ("tender la ropa/cama/mesa").
        // c.730: "aspirar" — mismo lockstep (17/19): aspiradora, inequívoco
        // como "barrer"/"fregar" (la acepción figurada "aspirar a un cargo"
        // no captura: guardia en el piso y baja puntuación de keyword).
        "fregar", "barrer", "trapear", "regar", "sacudir", "desempolvar", "tender",
        "aspirar",
        // c.717: objeto del piso `sacar la basura` (lockstep keyword↔piso,
        // lección c.639). "sacar" suelto es demasiado genérico, así se
        // alinea el OBJETO restringido en el piso.
        // c.728: objeto del piso `hacer la(s) cama(s)` (lockstep keyword↔piso;
        // "hacer" suelto es demasiado genérico — ya keyword de TASK — así se
        // alinea el OBJETO restringido en el piso).
        // c.729: objeto del piso `poner la lavadora` (lockstep keyword↔piso;
        // "poner" suelto es demasiado genérico, así se alinea el OBJETO
        // restringido en el piso).
        // c.731: objeto del piso `cortar el césped` (lockstep keyword↔piso;
        // "cortar" suelto es ambiguo — pelo/pan/comunicación).
        // c.732: objeto del piso `quitar el polvo` (lockstep keyword↔piso;
        // "quitar" suelto es ambiguo — el protector/la mancha/la ropa).
        // c.736: objeto del piso `poner la mesa` (lockstep keyword↔piso;
        // "poner" suelto es ambiguo — la música/la alarma/las pilas).
        // c.738: objeto del piso `poner el lavavajillas` (lockstep
        // keyword↔piso; primer electrodoméstico de la CUARTA clase,
        // sonda `FourthClassChoreProbe.kt` c.734; interop c.729: el
        // guard del piso lavavajillas exige el literal, "lavadora" no
        // roba lavavajillas ni viceversa).
        // c.740: mascota del piso `sacar al perro` (lockstep
        // keyword↔piso; "sacar" suelto es ambiguo — la basura/las
        // entradas/los críos, piso TRASH c.717; sonda
        // `FourthClassVerbDiscoveryProbe.kt` c.740, primera mascota
        // del dominio).
        // c.742: objeto del piso `pasar la aspiradora` (lockstep
        // keyword↔piso; el verbo "aspirar" ya era keyword desde c.730,
        // pero el sustantivo no; "pasar" suelto es ambiguo — la tarde/
        // la página/la mano — así se alinea el OBJETO restringido).
        // c.743: objeto del piso `colgar la ropa` (lockstep
        // keyword↔piso; "colgar" suelto es ambiguo — el cuadro/el
        // teléfono/de la barra — así se alinea el OBJETO restringido).
        // c.744: mascota del piso `alimentar al gato` (lockstep
        // keyword↔piso; "alimentar" suelto es bivalente — al bebé/la
        // planta/la relación — así se alinea el OBJETO mascota,
        // familia `sacar al perro` c.740).
        "basura", "cama", "lavadora", "césped", "polvo", "mesa",
        "lavavajillas", "perro", "perra", "aspiradora", "ropa", "gato", "gata")),
    UNKNOWN("Sin clasificar", emptyList());

    companion object {
        /** Términos que disparan análisis de intención (acumulados de todas las categorías) */
        val TRIGGER_WORDS: List<String> = ContextIntentKind.entries.flatMap { it.keywords }
    }
}

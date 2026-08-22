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
        "actualizar", "archivar", "subir", "descargar", "llenar", "donar", "entregar", "enviar", "llamar", "avisar", "confirmar", "reservar",
        "comprar", "traer", "llevar", "conseguir", "buscar", "pedir", "solicitar",
        "coger", "publicar", "recordar a", "acordarme de", "acordarse de",
        // c.751: keyword-OBJETO "celular" (lockstep con el piso acotado
        // "cargar el celular", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO se añade el verbo "cargar": bivalente (el archivo/la tarjeta/
        // gasolina) y subcadena de "descargar" (c.725).
        "celular",
        // c.851: keyword-OBJETO "móvil" (lockstep con la diagonal dialectal
        // del piso "cargar el celular", lección c.751: sin ella la
        // notificación sin palabra gatillo ni llega al análisis en
        // producción). La subcadena de "automóvil" suma 0.12 inerte <
        // umbral (mismo argumento que "extensión"/"pretensión" c.772):
        // "el automóvil está sucio" sigue descartado.
        "móvil",
        // c.853: keyword-OBJETO "coche" (lockstep con la extensión del piso
        // "cargar el celular/móvil", lección c.751). La subcadena de
        // "cochera"/"cochecito" suma 0.12 inerte < umbral (mismo argumento
        // que "automóvil" c.851): "la cochera está cerrada" sigue
        // descartado.
        "coche",
        // c.752: keyword-verbo "votar" (lockstep con el piso; verbo
        // unívoco, ningún objeto bivalente queda abierto).
        "votar",
        // c.765: keyword-OBJETO "medicina" (lockstep con el piso acotado
        // "tomar la medicina", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO se añade el verbo "tomar": bivalente (el café/el autobús/un
        // vuelo/una decisión). 0.12 sola queda bajo el umbral: el sustantivo
        // suelto ("la medicina está en la mesa") sigue descartado.
        "medicina",
        // c.859: keyword-OBJETO "medicación" (lockstep con la extensión del
        // piso "tomar la medicina", lección c.751/c.765: "medicina" no la
        // cubre por subcadena). 0.12 sola queda bajo el umbral: "la
        // medicación está en el botiquín" sigue descartado. Sin tilde no es
        // keyword (precedente c.772 «tensión»): el piso sí la admite por
        // `[oó]` cuando el texto llega al análisis vía otra keyword.
        "medicación",
        // c.766: keyword-OBJETO "insulina" (lockstep con el piso acotado
        // "ponerse la insulina"). NO el verbo "ponerse": bivalente (la
        // chaqueta/enfermo/contento). 0.12 sola bajo el umbral: "la
        // insulina está en la nevera" sigue descartado.
        "insulina",
        // c.768: keyword-OBJETO "itv" (lockstep con el piso acotado "pasar
        // la ITV", ver ContextIntentEngine.hasStrongTaskImperative). NO el
        // verbo "pasar": bivalente (la tarde/el rato/la película). 0.12 sola
        // queda bajo el umbral: "la ITV está cara" sigue descartado. Sin
        // colisión de subcadena en español corriente.
        "itv",
        // c.771: keyword-OBJETO "router" (lockstep con el piso acotado
        // "reiniciar el router", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO el verbo "reiniciar": bivalente (el ordenador/la app/el móvil).
        // 0.12 sola queda bajo el umbral: "el router está apagado" sigue
        // descartado. Sin colisión de subcadena en español corriente.
        "router",
        // c.772: keyword-OBJETO "tensión" (lockstep con el piso acotado
        // "medir la tensión", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO el verbo "medir": bivalente (la mesa/el espacio/el rendimiento).
        // 0.12 sola queda bajo el umbral: "la tensión está alta" sigue
        // descartado. Las colisiones de subcadena ("extensión"/"pretensión"/
        // "hipertensión") suman como mucho 0.12, inertes bajo el umbral —
        // ninguna activa captura sin el piso.
        "tensión",
        // c.775: lockstep con el piso "medirme la presión" (reflexivo
        // enclítico, hermano c.770). misma doctrina: keyword "presión",
        // no el verbo bivalente; "depresión"/"compresión"/"expresión"
        // quedan inertes (0.12 < umbral sola)
        "presión",
        // c.774: keyword-OBJETO "backup" (lockstep con el piso acotado
        // "hacer copia de seguridad", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO el verbo "hacer": muy bivalente (la compra/la cama/ejercicio).
        // Tampoco "copia": subcadena de "fotocopia"/"copiar" — insegura como
        // keyword aunque sumara sólo 0.12. "backup" es inequívoco en español
        // corriente; 0.12 sola queda bajo el umbral: "el backup está
        // corrupto" sigue descartado.
        "backup",
        // c.827: keyword-OBJETO "maleta" (lockstep con el piso acotado
        // "hacer/preparar/meter la maleta", ver ContextIntentEngine.hasStrongTaskImperative).
        // NO los verbos: "hacer"/"preparar" ya son keywords genéricas y
        // "meter" es bivalente (la pata/ruido/gol). "maleta" es inequívoca
        // en español corriente ("maletín" no la contiene: «malet-í» ≠
        // «malet-a»); 0.12 sola queda bajo el umbral: "la maleta está
        // hecha" sigue descartado.
        "maleta",
        // c.830: keyword verbo "gestionar" (lockstep con el piso acotado
        // "gestionar <objeto>", ver ContextIntentEngine.hasStrongTaskImperative;
        // lección c.751: sin ella una notificación "gestionar el envío" sin
        // palabra gatillo ni llega al análisis en producción). El sustantivo
        // "gestión" no la contiene («gestión» ≠ «gestionar»); 0.12 sola queda
        // bajo el umbral (0.45): "la gestión quedó bien" sigue descartado.
        "gestionar")),
    EVENT("Evento", listOf("evento", "cita", "reunión", "conferencia", "sesión",
        "taller", "clase", "curso", "entrevista", "webinar")),
    APPOINTMENT("Cita", listOf("cita con", "cita médica", "dentista", "doctor", "médico",
        "especialista", "consulta", "revisión", "chequeo", "terapia",
        "psicólogo", "nutricionista")),
    MEETING("Reunión", listOf("reunión con", "reunión de", "junta", "encuentro",
        "quedar con", "vernos", "nos vemos", "quedamos",
        // c.847: lockstep del piso «quedar con|para» (lección c.751).
        "quedamos con", "quedar para")),
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
        "pasar por",
        // c.773: keyword-OBJETO "niños" (lockstep con el piso acotado
        // `ERRAND_SCHOOL_RUN_FLOOR` "llevar a los niños al colegio",
        // ver ContextIntentEngine.hasStrongErrandImperative). NO el verbo
        // "llevar": bivalente (el coche/a María/la cuenta). 0.12 sola queda
        // bajo el umbral: "los niños van al colegio" (declarativo) sigue
        // descartado; con bono temporal 0.22 < 0.45 (negada/duda inertes).
        "niños",
        // c.829: keyword-OBJETO "gasolina" (lockstep con el piso acotado
        // `ERRAND_FUEL_FLOOR` "echar gasolina", lección c.713/c.773). NO el
        // verbo "echar": bivalente (echar agua/de menos/a perder/la culpa).
        // 0.12 sola queda bajo el umbral: "la gasolina está cara" sigue
        // descartada; con bono temporal 0.22 < 0.45. Además alimenta
        // [TRIGGER_WORDS], sin lo cual la notificación ni llegaría al
        // análisis (lección c.751).
        "gasolina",
        // c.842: keyword-OBJETO "pelo" (lockstep con el piso acotado
        // `ERRAND_HAIRCUT_FLOOR` "cortar/cortarme el pelo", lección
        // c.751/c.829). NO el verbo "cortar": bivalente (césped/pan/
        // comunicación). 0.12 sola queda bajo el umbral: "el pelo está
        // largo" (declarativo) sigue descartado; con bono temporal
        // 0.22 < 0.45.
        "pelo",
        // c.831: keyword-VERBO "repostar" (lockstep keyword↔piso
        // `ERRAND_VERBS` posición libre, lección c.639/c.751). Monosémico
        // (proveer de combustible), así keyword verbo — a diferencia del
        // bivalente "echar" de c.829, que fue keyword-OBJETO. 0.12 sola
        // queda bajo el umbral: el futuro conjugado "repostaré el coche
        // mañana" (keyword 0.12 + bono temporal 0.1 = 0.22 < 0.45) sigue
        // descartado.
        "repostar",
        // c.854: keyword-VERBO-enclítico "llevarle" (lockstep keyword↔piso
        // `ERRAND_DATIVE_FLOOR` "llevarle/devolverle <objeto> a <persona>",
        // lección c.751: sin ella la notificación "llevarle el informe al
        // jefe" sin otra keyword ni llegaría al análisis en producción).
        // Subcadena: también casa "llevarles". La forma NO enclítica
        // "llevar" sigue sin ser keyword (bivalente, c.773). La forma
        // figurada ("llevarle la contraria/ventaja") suma 0.12 inerte
        // (<0.45 sin piso, que además la bloquea con su guard). Con bono
        // temporal 0.22 < 0.45 (negada/duda inertes). "devolverle" no
        // necesita keyword propia: la cubre "devolver" por subcadena.
        "llevarle")),
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
        // c.828: "vaciar" — lockstep c.639 con el piso de posición libre
        // (precedente c.727 "tender"): quehacer monosémico (vaciar un
        // contenedor/espacio), sin acepción figurada frecuente.
        "vaciar",
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
        // c.747: destino del piso `llevar al perro al veterinario`
        // (lockstep keyword↔piso; "llevar" suelto es bivalente — el
        // coche/la cuenta/a los niños al colegio — así se alinea el
        // DESTINO restringido, familia mascota c.740/c.744).
        // c.748: verbo del piso `podar el jardín` (lockstep keyword↔piso;
        // la keyword-objeto "jardín" ya existía en la cobertura léxica
        // base, así el lockstep añade el VERBO — precedente de verbos
        // keyword c.639 "fregar"/c.727 "tender"/c.730 "aspirar";
        // familia jardinería `cortar el césped` c.731).
        // c.757: verbo del piso `vacunar al perro/gato` (lockstep
        // keyword↔piso; las keyword-mascota "perro"/"gato" ya existían
        // c.740/c.744, así el lockstep añade el VERBO — precedente de
        // verbos keyword c.748 "podar"; familia mascota c.740/c.747).
        // c.761: verbo del piso `bañar al perro/gato` (lockstep
        // keyword↔piso; las keyword-mascota "perro"/"gato" ya existían
        // c.740/c.744, así el lockstep añade el VERBO — precedente de
        // verbos keyword c.748 "podar", c.757 "vacunar"; familia mascota
        // c.740/c.747/c.757).
        "basura", "cama", "lavadora", "césped", "polvo", "mesa",
        // c.758: objeto del piso `pintar la(s) casa(s)` (lockstep
        // keyword↔piso; "pintar" suelto es bivalente — un cuadro/la
        // veranda — así se alinea el OBJETO restringido, sonda
        // `FourthClassVerbDiscoveryProbe.kt`).
        "lavavajillas", "perro", "perra", "aspiradora", "ropa", "gato", "gata",
        "veterinario", "veterinaria", "podar", "vacunar", "bañar", "casa")),
    UNKNOWN("Sin clasificar", emptyList());

    companion object {
        /** Términos que disparan análisis de intención (acumulados de todas las categorías) */
        val TRIGGER_WORDS: List<String> = ContextIntentKind.entries.flatMap { it.keywords }
    }
}

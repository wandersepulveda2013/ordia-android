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
        // c.1084: keyword-OBJETO "carro" (lockstep con la extensión del
        // piso "cargar el coche" a la diagonal LatAm, lección c.751).
        // La subcadena de "carrocería"/"carrito" suma 0.12 inerte <
        // umbral (mismo argumento que "cochera"/"automóvil"):
        // "el carrito de la compra" sigue descartado.
        "carro",
        // c.1082: keyword-OBJETO "ruedas" (lockstep con el piso acotado
        // "poner las ruedas de invierno/verano", lección c.713/c.751/
        // c.765: sin ella la notificación sin palabra gatillo ni llega al
        // análisis en producción). Cubre el singular por subcadena
        // ("rueda"). 0.12 sola queda bajo el umbral: "las ruedas están
        // gastadas" sigue descartado; "inflar las ruedas de la bici"
        // (candidata (c) de la clase DUODÉCIMA) sigue FUERA: 0.12 + bono
        // temporal 0.1 = 0.22 < 0.45 (pins en el test).
        "ruedas",
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
        // c.1111: keyword-OBJETO "dieta" (lockstep con el piso acotado
        // "empezar (con )?(la )?dieta", lección c.713/c.751/c.765: sin
        // ella la notificación sin palabra gatillo ni llega al análisis).
        // NO se añade el verbo "empezar": bivalente (el libro/la serie/
        // la carrera). Cubre el plural por subcadena ("dietas"); no es
        // subcadena de "dietética"/"dietista" (é/i rompen). 0.12 sola
        // queda bajo el umbral: "la dieta mediterránea es sana" sigue
        // descartado; con bono temporal 0.22 < 0.45 (pin en el test).
        "dieta",
        // c.1131: keyword-OBJETO "rodilla" (lockstep con el piso acotado
        // "operar (…) la rodilla", lección c.713/c.751/c.765: sin ella la
        // notificación sin palabra gatillo ni llega al análisis).
        // NO se añade el verbo "operar": bivalente (la máquina/en
        // bolsa). Cubre el plural por subcadena ("rodillas"); no es
        // subcadena de palabra común alguna. 0.12 sola queda bajo el
        // umbral: "la rodilla me duele" sigue descartado (pin en el
        // test); "la operación de rodilla es en enero" también (pin).
        "rodilla",
        // c.861: keyword-FRASE "contestar a" (lockstep con el piso acotado
        // «contestar a <persona>», ver ContextIntentEngine.hasStrongTaskImperative;
        // lección c.751: sin ella la notificación "contestar a Juan esta
        // tarde" sin palabra gatillo ni llegaría al análisis en producción —
        // a diferencia de c.860, ninguna keyword previa la cubre). NO el
        // verbo suelto "contestar": bivalente (a la pregunta/en el examen/
        // al teléfono). La frase multi-palabra es hermana de "llamar a"/
        // "hablar con" (CALL): subcadena de "contestar al…" y de
        // "contestar a la pregunta", pero ambas suman 0.12 inerte < umbral
        // y el piso las rechaza (a+artículo fuera de su alcance). Con bono
        // temporal 0.22 < 0.45 (negada/duda inertes).
        "contestar a",
        // c.879: keyword-FRASE "contestarle" (lockstep con la extensión
        // dativa del piso «contestar…», cubre el plural «contestarles» por
        // subcadena). Sin ella «contestarle a Juan» ni llega al análisis
        // (el enclítico intercalado rompe la subcadena de «contestar a»).
        "contestarle",
        // c.880: keyword-OBJETO "carta" (lockstep con la extensión del
        // piso hermano c.873; la carta física sigue siendo correo real en
        // español). Bivalente medido en sonda PRE: «la carta del
        // restaurante/menú» 0.12 < umbral sola, inerte.
        "carta",
        // c.881: keyword-OBJETO "tatuaje" (lockstep con la extensión del
        // piso «hacerse» c.862; 0.12 sola inerte y el piso exige el verbo
        // reflexivo + objeto acotado, anti-overreach).
        "tatuaje",
        // c.766: keyword-OBJETO "insulina" (lockstep con el piso acotado
        // "ponerse la insulina"). NO el verbo "ponerse": bivalente (la
        // chaqueta/enfermo/contento). 0.12 sola bajo el umbral: "la
        // insulina está en la nevera" sigue descartado.
        "insulina",
        // c.1044: keyword-OBJETO "vacuna" (lockstep con el piso reflexivo
        // «ponerme la vacuna»; lección c.751 — sin ella la notificación ni
        // llega al análisis). NO el verbo «poner(se)»: bivalente. 0.12 sola
        // bajo el umbral: «la vacuna de la gripe está disponible» sigue
        // descartado; con «perro» (keyword mascota) + bono temporal suma
        // 0.34 < 0.45: «la vacuna del perro mañana» sigue NULL. Subcadenas
        // «vacunar»/«vacunación» la contienen pero son inertes bajo el
        // umbral (el piso mascota c.757/c.1011 gobierna esas rutas).
        "vacuna",
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
        "gestionar",
        // c.864: keyword-VERBO "escanear" (lockstep con el piso acotado
        // "escanear el DNI", ver ContextIntentEngine.hasStrongTaskImperative;
        // lección c.751: sin ella una notificación "escanear el DNI mañana"
        // sin palabra gatillo ni llega al análisis en producción). Verbo
        // monosemántico (precedente c.752 "votar"); subcadena inerte:
        // "reescanear" la contiene pero 0.12 sola queda bajo el umbral y
        // el piso anclado la excluye (precedente "descargar"/"cargar"
        // c.853). "el DNI está caducado" sigue descartado.
        "escanear",
        // c.887: keyword-VERBO "fotocopiar" (lockstep con el piso acotado
        // "fotocopiar el DNI", ver ContextIntentEngine.hasStrongTaskImperative;
        // lección c.751: sin ella una notificación "fotocopiar el DNI mañana"
        // sin palabra gatillo ni llega al análisis en producción). Verbo
        // monosemántico (precedente c.864 "escanear"/c.752 "votar"); subcadenas
        // inertes: el sustantivo "fotocopia" y la forma pasada "fotocopié"
        // NO contienen "fotocopiar"; 0.12 sola queda bajo el umbral.
        // "la fotocopia está en el cajón" sigue descartado.
        "fotocopiar",
        // c.1032: keyword-VERBO "configurar" (lockstep con el piso acotado
        // "configurar <dispositivo>", ver ContextIntentEngine
        // .hasStrongTaskImperative; lección c.751: sin ella una notificación
        // "configurar el móvil nuevo mañana" sin palabra gatillo ni llega al
        // análisis en producción). Verbo monosemántico (precedente c.864
        // "escanear"/c.752 "votar"); subcadenas inertes: el sustantivo
        // "configuración" y la forma pasada "configuré" NO contienen
        // "configurar"; "reconfigurar" la contiene pero 0.12 sola queda bajo
        // el umbral y el piso anclado la excluye (precedente "reescanear"
        // c.888). "la configuración quedó bien" sigue descartado.
        "configurar",
        // c.1036: keyword-VERBO "formatear" (lockstep con el piso acotado
        // "formatear <dispositivo>", ver ContextIntentEngine
        // .hasStrongTaskImperative; lección c.751: sin ella una notificación
        // "formatear el ordenador mañana" sin palabra gatillo ni llega al
        // análisis en producción). Verbo monosemántico (precedente c.864
        // "escanear"/c.752 "votar"/c.1032 "configurar"); subcadenas inertes:
        // el sustantivo "formateo" y la forma pasada "formateé" NO contienen
        // "formatear"; "reformatear" la contiene pero 0.12 sola queda bajo
        // el umbral y el piso anclado la excluye (precedente "reescanear"
        // c.888). "el formateo quedó a medias" sigue descartado.
        "formatear",
        // c.875: keyword-VERBO "presentar" (lockstep con el piso acotado
        // "presentar la declaración de la renta", ver ContextIntentEngine
        // .hasStrongTaskImperative; lección c.751: sin ella la notificación
        // ni llega al análisis). Bivalente: la frase-objeto la acota al
        // piso ("presentar la solicitud/documentación" medidas NULL).
        "presentar",
        // c.876: keyword-VERBO "declarar" (lockstep con el piso acotado
        // "declarar la renta", ver ContextIntentEngine
        // .hasStrongTaskImperative; lección c.751). Bivalente: el piso
        // la acota al objeto "renta" ("declarar el amor"/"en el juicio"
        // medidas NULL; 0.12 sola < umbral).
        "declarar",
        // c.895b: keywords-OBJETO «nómina»+grafía sin tilde (lockstep con
        // el piso acotado «cobrar la nómina/reembolso», familia 3/8 de la
        // clase NOVENA sonda c.892; lección c.751/c.765). NO el verbo
        // «cobrar»: bivalente (la compra/los favores/el alquiler). 0.12
        // sola inerte < umbral y el piso la exige anclada al verbo —
        // «la nómina llegó ayer» sigue descartado. Además alimenta
        // [TRIGGER_WORDS]: sin ella una notificación cuyo gatillo fuera
        // solo «nómina» ni llegaría al análisis (lección c.751).
        "nómina", "nomina",
        // c.896 (laterales hermanos c.895b): «pensión/pension/sueldo/salario»
        // — objetos salariales lockstep del mismo piso ampliado en aditivo.
        "pensión", "pension", "sueldo", "salario",
        // c.895c: keyword-frase «dar de baja» + keywords-OBJETO
        // «suscripción»/grafía sin tilde (lockstep con el piso acotado
        // «dar de baja el gimnasio/la suscripción», familia 4/8 de la
        // clase NOVENA; ver ContextIntentEngine.hasStrongTaskImperative).
        // «dar de baja» es cuasi-monosemántico (baja administrativa) y
        // frase exacta: «dar de alta» no la contiene. Sin ellas TASK no
        // gana la carrera de puntuación frente a EXERCISE («gimnasio» vive
        // en EXERCISE) y el piso nunca se evalúa (lección c.751). 0.12
        // sola inerte < umbral y el piso exige ancla-objeto — «dar de baja
        // la línea telefónica» sigue descartado (NULL deliberado, sonda
        // `tools/probe/DarDeBajaProbe.kt`).
        "dar de baja", "suscripción", "suscripcion",
        // c.1139: keyword-frase «dar de alta» (lockstep con el piso
        // acotado «dar de alta <suministro>», candidata (b) de la clase
        // DECIMOQUINTA; ver ContextIntentEngine.hasStrongTaskImperative).
        // Cuasi-monosemántica: alta administrativa; el bivalente médico
        // «dar de alta a un paciente» queda fuera por el piso exigiendo
        // objeto-suministro (luz/agua/gas/internet). «dar» solo NO se
        // añade (extremadamente polivalente).
        "dar de alta",
        // c.1143: keyword-frase «sellar el paro» (lockstep con el piso
        // acotado «sellar (el)? paro», candidata (c) de la clase
        // DECIMOQUINTA; ver ContextIntentEngine.hasStrongTaskImperative).
        // Monosemántica: sólo la obligación periódica del SEPE. «sellar»
        // solo NO se añade (bivalente: «sellar el pasaporte/la carta»);
        // el piso exige el objeto «paro», así que esos bivalentes siguen
        // NULL deliberado (sonda `tools/probe/SellarParoProbe.kt`).
        "sellar el paro",
        // c.901: keyword-frase «dar las gracias» (lockstep con el piso
        // acotado «dar las gracias a <persona>», candidata (b) y última
        // forma NULL de la clase NOVENA-b; ver
        // ContextIntentEngine.hasStrongTaskImperative). Ni «dar» ni
        // «gracias» solas eran gatillo: sin la frase la notificación ni
        // llegaba al análisis (lección c.751) y el agradecimiento
        // pendiente se olvidaba. 0.12 sola inerte < umbral y el piso
        // exige ancla dativa «a <destino>» — «dar las gracias» suelto
        // sigue descartado (NULL deliberado, sonda
        // `tools/probe/DarLasGraciasProbe.kt`); la lateral «dar gracias
        // a…» se resuelve en c.904 (rama propia con guard anti-figurado).
        "dar las gracias",
        // c.903: keyword-frase «darle las gracias» (lockstep con la
        // extensión enclítica del piso c.901; ver
        // ContextIntentEngine.hasStrongTaskImperative). «darle las
        // gracias» NO contiene «dar las gracias» (el enclítico rompe la
        // cadena, lección c.751): sin la frase la notificación ni
        // llegaba al análisis (medido NULL en la sonda c.901) y el
        // agradecimiento pendiente en su forma MÁS cotidiana se olvidaba.
        // 0.12 sola inerte < umbral y el piso exige ancla dativa
        // «a <destino>» (sonda `tools/probe/DarleLasGraciasProbe.kt`).
        "darle las gracias",
        // c.904: keyword-frase «dar gracias» (lockstep con la rama sin
        // artículo del piso c.901; ver
        // ContextIntentEngine.hasStrongTaskImperative). «dar gracias a
        // Ana» NO contiene «dar las gracias» (sin el artículo no casa la
        // frase, lección c.751): sin ella la notificación ni llegaba al
        // análisis (medido NULL en la sonda c.901) y el agradecimiento
        // pendiente en su forma pelada se olvidaba. 0.12 sola inerte <
        // umbral y el piso exige ancla dativa «a <destino>» + guard
        // anti-figurado (sonda
        // `tools/probe/DarGraciasSinArticuloProbe.kt`).
        "dar gracias",
        // c.905: keyword-frase «darle gracias» (lockstep con la lateral
        // FINAL del piso c.901 — enclítico sin artículo; ver
        // ContextIntentEngine.hasStrongTaskImperative). «darle gracias a
        // Ana» NO contiene «dar gracias» ni «dar las gracias» (el
        // enclítico rompe ambas cadenas, lección c.751): sin ella la
        // notificación ni llegaba al análisis (medido NULL en las sondas
        // c.903/c.904 y re-medido PRE en
        // `tools/probe/DarleGraciasSinArticuloProbe.kt`). 0.12 sola
        // inerte < umbral y el piso exige ancla dativa «a <destino>» +
        // guard anti-figurado — «darle gracias» suelto y «darle gracias
        // a Dios/a la vida/al cielo» siguen descartados (NULL deliberado).
        "darle gracias")),
    EVENT("Evento", listOf("evento", "cita", "reunión", "conferencia", "sesión",
        "taller", "clase", "curso", "entrevista", "webinar")),
    APPOINTMENT("Cita", listOf("cita con", "cita médica", "dentista", "doctor", "médico",
        "especialista", "consulta", "revisión", "chequeo", "terapia",
        "psicólogo", "nutricionista", "pediatra", "dermatólogo",
        // c.1126: keyword-frase «limpieza dental» (candidata (f) clase
        // DECIMOTERCERA; lockstep con MEDICAL/GO/FUTURE, lección
        // c.682/c.1110). Frase de DOS palabras: «limpieza» a secas (de
        // casa/del piso/del coche) jamás entra (anti-overreach).
        "limpieza dental",
        // c.1136: keyword «empaste» (candidata (k) clase DECIMOTERCERA;
        // lockstep con MEDICAL/GO/FUTURE, hermana EXACTA de c.1126).
        // Sustantivo inequívoco dental: el verbo albañil «empastar» NO
        // lo contiene como substring (difieren en la 7ª letra).
        "empaste")),
    MEETING("Reunión", listOf("reunión con", "reunión de", "junta", "encuentro",
        "quedar con", "vernos", "nos vemos", "quedamos",
        // c.847: lockstep del piso «quedar con|para» (lección c.751).
        "quedamos con", "quedar para")),
    STUDY("Estudio", listOf("estudiar", "estudio", "examen", "prueba", "curso",
        "tarea escolar", "lección", "práctica", "ejercicio",
        "repasar", "preparar examen",
        // c.898: objeto del piso `hacer/entregar (los) deberes`
        // (lockstep keyword↔piso↔título; familia NOVENA 5/8).
        "deberes")),
    SHOPPING("Compra", listOf("comprar", "supermercado", "mercado", "tienda",
        "farmacia", "mandado", "despensa", "víveres",
        "ir al super", "ir a comprar")),
    ERRAND("Diligencia", listOf("diligencia", "trámite", "banco", "oficina",
        // c.867: keyword-OBJETO «email» (lockstep del piso «responder el
        // correo/email», lección c.751/c.859: sin ella la frase ni llega al
        // análisis). 0.12 sola < umbral y el piso exige el verbo+objeto, así
        // no roba rutas.
        "correo", "email", "mensaje", "paquete", "devolver", "recoger",
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
        // c.1013: keyword-OBJETO "cabello" (lockstep con el piso
        // acotado `ERRAND_HAIRCUT_FLOOR`, sinónimo mayoritario en
        // español latinoamericano). Monosémica (el cabello es siempre
        // el de la cabeza). 0.12 sola queda bajo el umbral: "el
        // cabello está largo" sigue descartado; con bono temporal
        // 0.22 < 0.45 (misma aritmética que "pelo" c.842).
        "cabello",
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
        "llevarle",
        // c.893: keywords-OBJETO «dinero»/«efectivo» (lockstep con el piso
        // acotado [ERRAND_CASH_FLOOR] «sacar dinero/efectivo», lección
        // c.713/c.773/c.829). NO el verbo «sacar»: bivalente (la basura
        // c.717, al perro c.740, a bailar, fotos, la tarjeta). 0.12 sola
        // queda bajo el umbral: «el dinero está en la mesa» (declarativo)
        // sigue descartado; con bono temporal 0.22 < 0.45. Además alimenta
        // [TRIGGER_WORDS], sin lo cual la notificación ni llegaría al
        // análisis (lección c.751).
        "dinero", "efectivo",
        // c.893: keyword-DESTINO «cajero» (lockstep con la extensión del
        // piso `ir a` que añade «cajero|atm», lección c.751). Bivalente
        // (cajero bancario/empleado de pago), así 0.12 sola inerte <
        // umbral y el piso exige el verbo de destino — «el cajero cobra
        // bien» sigue descartado.
        "cajero",
        // c.896: keyword-DESTINO «atm» (delta lockstep sobre el piso c.893
        // del hermano: el destino «atm» quedó en el regex del piso pero SIN
        // keyword; lección c.751 — alimenta el score y [TRIGGER_WORDS]).
        // Sigla inequívoca del cajero automático; 0.12 sola inerte < umbral,
        // así «atm» aislado o declarativo no cola y el piso exige el verbo
        // de destino igual que «cajero».
        "atm",
        // c.914: keyword-DESTINO «biblioteca» (lockstep con la extensión
        // del piso `ir a` que añade «biblioteca», lección c.751 — sin ella
        // la notificación «ir a la biblioteca mañana» ni llegaría al
        // análisis). Monosémica (el lugar); 0.12 sola inerte < umbral, así
        // «la biblioteca cierra a las 20h» (declarativo) sigue descartado
        // aun con bono temporal (0.22 < 0.45) y el piso exige el verbo de
        // destino igual que «banco»/«cajero».
        "biblioteca",
        // c.909: keyword-DIVISA «euro» (lockstep con la rama cantidad del
        // piso [ERRAND_CASH_FLOOR] «sacar <N> euros», lección c.751 — sin
        // ella la notificación «sacar 50 euros mañana» ni llegaría al
        // análisis). Subcadena: cubre «euro»/«euros». 0.12 sola inerte <
        // umbral: «la tarifa son 50 euros» (declarativo) sigue descartado
        // aun con bono temporal (0.22 < 0.45); el piso exige «sacar» +
        // cantidad, y «pagar 50 euros» sigue PAYMENT por su propio piso
        // (la keyword solo suma 0.12 inerte a ERRAND).
        "euro",
        // c.910: keyword-DIVISA «dólar»/«dolar» (lockstep con la extensión
        // de la rama cantidad del piso [ERRAND_CASH_FLOOR] «sacar <N>
        // dólares», lección c.751 — sin ella «sacar 100 dólares mañana» ni
        // llegaría al análisis). Ambas grafías: el motor no normaliza
        // tildes (precedente «nómina»/«nomina» c.895b). Subcadena: cubre
        // «dólar»/«dólares». 0.12 sola inerte < umbral: «la entrada cuesta
        // 50 dólares» (declarativo, medido NULL en sonda PRE) sigue
        // descartado aun con bono temporal (0.22 < 0.45); «pagar 50
        // dólares» sigue PAYMENT y «coger 50 dólares» sigue TASK (piso
        // deliberado c.716) porque la keyword solo suma 0.12 inerte a
        // ERRAND.
        "dólar", "dolar",
        // c.911: keyword-DIVISA «peso» (lockstep con la extensión de la
        // rama cantidad del piso [ERRAND_CASH_FLOOR] «sacar <N> pesos»,
        // lección c.751 — sin ella «sacar 2000 pesos mañana» ni llegaría
        // al análisis). Subcadena: cubre «peso»/«pesos». 0.12 sola
        // inerte < umbral: «la entrada cuesta 500 pesos» (declarativo,
        // medido NULL en sonda PRE) sigue descartado aun con bono
        // temporal (0.22 < 0.45); «pagar 500 pesos» sigue PAYMENT y
        // «cambiar pesos por dólares» sigue TASK (piso c.710) porque la
        // keyword solo suma 0.12 inerte a ERRAND. La bivalencia
        // «peso» = pesas/balanza no colide: el piso exige «sacar» +
        // cantidad + «pesos» en posición de divisa («medirme el peso»,
        // «pesar las maletas» medidos NULL).
        "peso",
        // c.912: keyword-DIVISA «libra» (lockstep con la extensión de la
        // rama cantidad del piso [ERRAND_CASH_FLOOR] «sacar <N> libras»,
        // lección c.751 — sin ella «sacar 50 libras mañana» ni llegaría
        // al análisis). Subcadena: cubre «libra»/«libras». 0.12 sola
        // inerte < umbral: la bivalencia «libras» = unidad de peso no
        // colide («perder 5 libras», «pesar 150 libras», «levantar 100
        // libras» medidos NULL aun con bono temporal); «la entrada
        // cuesta 50 libras» (declarativo) sigue descartado; «pagar 50
        // libras» sigue PAYMENT y «cambiar libras por euros» sigue
        // TASK (piso c.710) porque la keyword solo suma 0.12 inerte a
        // ERRAND.
        "libra",
        // c.917: keyword-DIVISA «yen» (lockstep TRES puntos con la
        // rama cantidad del piso [ERRAND_CASH_FLOOR] «sacar <N> yenes»,
        // lección c.751 — sin ella «sacar 20000 yenes mañana» ni
        // llegaría al análisis, medido NULL 4/4 en sonda PRE
        // `/tmp/probe915/`). Subcadena: cubre «yen»/«yenes» (JPY,
        // monosémica — sin bivalencia que guardar). 0.12 sola inerte
        // < umbral: declarativos financieros («los yenes están
        // caros», «el yen se fortalece») siguen descartados aun con
        // bono temporal; «pagar 500 yenes» sigue PAYMENT y «cambiar
        // yenes por euros» sigue TASK (piso c.710) porque la keyword
        // solo suma 0.12 inerte a ERRAND.
        "yen",
        // c.894: keyword-OBJETO «reembolso» (lockstep con el piso acotado
        // [ERRAND_DEPOSIT_FLOOR] «ingresar dinero/reembolso», hermano de
        // «dinero» c.893). NO el verbo «ingresar»: bivalente (en el club,
        // de la gente, deberes). 0.12 sola inerte < umbral —
        // declarativos como «el reembolso tardó dos semanas» siguen
        // descartados aun con bono temporal (0.22 < 0.45).
        "reembolso",
        // c.895: keywords-OBJETO de las laterales del hermano («depositar
        // el cheque»/«hacer el ingreso», lockstep con la ampliación del
        // piso [ERRAND_DEPOSIT_FLOOR]). NO el verbo «depositar» (bivalente
        // — la basura/la confianza). 0.12 cada una inertes solas < umbral
        // (0.32 con bono temporal < 0.45) → «el cheque llegó ayer» sigue
        // descartado; el piso exige el verbo anclado al objeto.
        "cheque",
        "ingreso")),
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
        "hacer deporte", "ir al gimnasio",
        // c.1135: «campamento» (actividad escolar/estival de los hijos —
        // «inscribir al niño en el campamento en julio» era NULL aunque las
        // hermanas con keyword como «natación» capturaban). Lockstep con el
        // bono EXERCISE de ContextIntentEngine.scoreKind.
        "campamento",
        // c.1146: «extraescolar» (lateral (b-bis) de c.1135 — «inscribir al
        // niño en las extraescolares en septiembre» era NULL medido por
        // sonda efímera). Lockstep con EXERCISE_VERBS del engine y con la
        // extensión coherente del guard declarativo c.1145.
        "extraescolar")),
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
        // c.898: objetos del piso comida `hacer/preparar la cena/el
        // almuerzo/la comida/el desayuno/la merienda` (lockstep
        // keyword↔piso↔título; verbos bivalentes acotados al OBJETO —
        // familia NOVENA 5/8) y el verbo inequívoco `descongelar`
        // (familia "cocinar" posición libre).
        "cena", "comida", "almuerzo", "desayuno", "merienda", "descongelar",
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

package com.ordia.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lock de regresión del algoritmo de moderación temática compartido
 * ([ContentModeration.isHarmful]). Es P0 (c.582): las listas anteriores casaban
 * RAÍCES ("matar", "bomba", "violar", "droga", "pene"...) sin distinguir el
 * sentido legítimo, así que 16/17 tareas reales se descartaban silenciosamente
 * en el canal de captura contextual o se rechazaban en el asistente del overlay.
 * Pérdida de datos en las rutas de mayor valor.
 *
 * El algoritmo es por RAÍZ + EXENCIÓN: una raíz dañina solo bloquea si aparece en
 * una ocurrencia NO cubierta por una forma legítima (colocación que CONTIENE al
 * rango de la raíz, o proximidad que cubre TODAS las ocurrencias). Así
 * "matar el proceso y luego matar a juan" sigue bloqueando (la 2ª ocurrencia no
 * está cubierta) mientras "matar el proceso" pasa. Determinista (regex + rangos),
 * sin random, sin IA fingida.
 *
 * Estos tests reproducen las reglas canónicas de IntelligenceSafetyGate (c.582)
 * para validar el algoritmo sin acoplarse al gate concreto (que también filtra
 * secretos/PII). Falsos positivos (tareas legítimas) → isHarmful == false;
 * verdaderos positivos → isHarmful == true.
 */
class ContentModerationTest {

    // Reglas canónicas del gate de inteligencia (c.582 + c.630). Se replican aquí
    // para testear el algoritmo sin depender de los filtros de secreto del gate.
    // c.630 separa las raíces flexionadas (desnud/eroti/masturb) de las palabras
    // completas: aquéllas usan `\b` inicial sin `\b` final (ver ContentModeration).
    private val sexual = ContentModeration.ModerationRule(
        stem = Regex("""\b(sexo|sexual|porno|xxx|culo|culos|teta|tetas|pene|vagina|orgasmo)\b"""),
        contain = listOf(
            Regex("""\b(cita con el ur[oó]logo|cita con la ginec[oó]loga?)\b[^.]*\bpene\b"""),
            Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
        ),
        proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
    )
    private val sexualStems = ContentModeration.ModerationRule(
        stem = Regex("""\b(desnud|eroti|masturb)"""),
        proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
    )
    private val violencia = ContentModeration.ModerationRule(
        stem = Regex("""\b(matar|asesinar|violar|bomba|amenaza|escopeta|pistola)"""),
        contain = listOf(
            Regex("""\bmatar\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
            Regex("""\bmatar\s+(el|la|los|las)\s+(hambre|tiempo|ganas|aburrimiento|sed|sue[nñ]o|rabia|enojo|ansiedad|estr[eé]s|dolor|cansa(?:d|c)io|curiosidad)\b"""),
            Regex("""\bviolar(?:on|[éeo]|a|as|an)?\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|clausula|cl[áa]usula|t[ée]rminos?)\b"""),
            // c.645: `amenazas?` (plural opcional) — espeja a producción.
            Regex("""\b(modelo|m[oó]delo)\s+de\s+amenazas?\b"""),
            Regex("""\bamenaza(?:s)?\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
            Regex("""\b(bomba[ns]?|pistola[ns]?|escopeta[ns]?)\s+de\s+agua\b"""),
            Regex("""\b(matar|asesinar)\s+(un|el)\s+proceso\b""")
        )
    )
    // c.636: `cuchill` SEPARADA de violencia (mismo split que producción).
    // PRE-fix la raíz iba con `\b` final en el stem conjunto de violencia y NO
    // casaba "cuchillo/cuchillada/cuchillazo/acuchillar" → "amenazar con
    // cuchillo" PASABA. Sin `\b` final casa la familia, PERO se añaden `contain`
    // culinarios para no bloquear "cuchillo de cocina/chef/pan" (falso positivo
    // P1 datos). Duplica la regla de producción para testear isHarmful aislado.
    private val cuchilloStems = ContentModeration.ModerationRule(
        stem = Regex("""\b(acuchill|cuchill)"""),
        contain = listOf(
            Regex("""\bcuchill[oa]s?\s+(?:de\s+(el|la|los|las)?|del)\s*(cocina|chef|pan|mes[oó]n|m[aá]rmol|carnicer[ií]a|caza|pescado|mesa|untar|trinchar|cocinero|palo|mantequilla|fruta|carne|queso)\b"""),
            Regex("""\b(afilad[oa]r(es)?|afi[cz]a(c)?dor(es)?)\s+de\s+cuchill[oa]s?\b"""),
            Regex("""\b(set|juego|bloque|cubierto|cubre)\s+de\s+cuchill[oa]s?\b"""),
            Regex("""\bcuchill[oa]\s+(?:(?:de\s+(el|la)?|del)\s*(mesa|untar|cocina)|para\s+(el|la|los|las)?\s*(pan|cocina|fruta|carne|queso))\b""")
        )
    )
    private val secuestroStems = ContentModeration.ModerationRule(
        stem = Regex("""\bsecuestr"""),
        contain = listOf(
            Regex("""\b(revisi[oó]n|revisar|diagn[oó]stico|diag|audit|auditor[íi]a)\s+(de[l]?)\s*secuestro\b"""),
            // c.642: artículo definido intermedio opcional (MISMA CLASE que c.641).
            // Admite "de la sesion" (artículo separado), "de las sesiones" (plural)
            // y "del token" (de+el contraído sin espacio), además de "de DNS" (sin
            // artículo). La forma natural del español técnico es con artículo.
            Regex("""\bsecuestro\s+(?:del|de\s*(?:la|las)?)\s*(dns|sesi[oó]n|cookie|token|sesiones?)\b""")
        )
    )
    // c.635: "droga" (genérica, proximity médica) SEPARADA de las específicas.
    // Mismo split que la producción (ver ContentModeration): las específicas NO
    // comparten la proximity médica — su mención aislada es señal fuerte y la
    // co-aparición de "farmacia" no las legitima (cierra el falso-negativo
    // "fumar marihuana y luego ir a la farmacia"). Duplica la regla de producción
    // para testear isHarmful aislado de los filtros de secreto del gate.
    private val drogas = ContentModeration.ModerationRule(
        stem = Regex("""\bdroga"""),
        proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
    )
    private val drogasEspecificas = ContentModeration.ModerationRule(
        stem = Regex("""\b(cocaina|heroina|marihuana|metanfetamina|narcotrafic)""")
    )
    // c.632: insultos como raíces flexionadas (`\b` inicial sin `\b` final).
    private val insultos = ContentModeration.ModerationRule(
        stem = Regex("""\b(pendej|estupid|imbecil|malparid|hijueputa)""")
    )

    private fun sexualHarmful(text: String) = ContentModeration.isHarmful(text, sexual)
    private fun sexualStemHarmful(text: String) = ContentModeration.isHarmful(text, sexualStems)
    private fun violentHarmful(text: String) = ContentModeration.isHarmful(text, violencia)
    private fun cuchilloHarmful(text: String) = ContentModeration.isHarmful(text, cuchilloStems)
    private fun secuestroHarmful(text: String) = ContentModeration.isHarmful(text, secuestroStems)
    private fun drugHarmful(text: String) = ContentModeration.isHarmful(text, drogas)
    private fun specificDrugHarmful(text: String) = ContentModeration.isHarmful(text, drogasEspecificas)
    private fun insultHarmful(text: String) = ContentModeration.isHarmful(text, insultos)

    // ── Falsos positivos corregidos: tareas legítimas que antes se bloqueaban ──

    @Test fun matarElProceso_noEsDanino() {
        assertFalse(violentHarmful("recuérdame matar el proceso del servidor"))
    }

    @Test fun matarElHiloZombie_noEsDanino() {
        assertFalse(violentHarmful("matar el hilo zombie que quedó colgado"))
    }

    @Test fun violarLaPolitica_noEsDanino() {
        assertFalse(violentHarmful("esto violar la política de la empresa"))
    }

    @Test fun modeloDeAmenaza_noEsDanino() {
        assertFalse(violentHarmful("hacer el modelo de amenaza del nuevo módulo"))
    }

    // c.645: PRE-fix el contain `\b...amenaza\b` mataba el PLURAL "amenazas"
    // (forma estándar de ingeniería de seguridad). La captura de esa nota
    // técnica legítima se rechazaba (falso-positivo P1 datos).
    @Test fun modeloDeAmenazas_plural_noEsDanino() {
        assertFalse(violentHarmful("hacer el modelo de amenazas del API"))
    }

    @Test fun modeloDeAmenazas_stride_noEsDanino() {
        assertFalse(violentHarmful("el modelo de amenazas STRIDE del módulo"))
    }

    @Test fun modeloDeAmenazasDelSistema_noEsDanino() {
        assertFalse(violentHarmful("actualizar el modelo de amenazas del sistema"))
    }

    // c.645 guardia anti-falso-negativo: "amenazas" como amenaza REAL sigue
    // bloqueada cuando NO va tras "modelo de" (el contain solo cubre ese rango).
    @Test fun amenazasDeMuerte_sigueDanino() {
        assertTrue(violentHarmful("te voy a hacer amenazas de muerte"))
    }

    @Test fun bombaDeAgua_noEsDanino() {
        assertFalse(violentHarmful("comprar bomba de agua para el jardín"))
    }

    @Test fun pistolaDeAgua_noEsDanino() {
        assertFalse(violentHarmful("limpiar la pistola de agua de los niños"))
    }

    @Test fun secuestroDeDns_noEsDanino() {
        // c.630: `secuestr` ahora SÍ casa "secuestro"; la exención de colocación
        // ("revisar el secuestro de DNS") legitima el sentido técnico.
        assertFalse(secuestroHarmful("revisar el secuestro de DNS del servicio"))
    }

    @Test fun secuestroDeSesion_noEsDanino() {
        assertFalse(secuestroHarmful("auditar el secuestro de sesion del login"))
    }

    // ── c.642: artículo definido intermedio en la exención técnica de `secuestr`
    //    (MISMA CLASE que c.641/cuchill). El habla técnica natural es
    //    "secuestro de la sesión/cookie/token" (con artículo); PRE-fix el contain
    //    `secuestro de (dns|sesion|...)` NO admitía artículo intermedio → la forma
    //    natural era bloqueada (falso-positivo, captura de nota técnica perdida). ──

    @Test fun secuestroDeLaSesion_noEsDanino() {
        // PRE-fix RED: "secuestro de la sesion" no casaba el contain (artículo "la")
        // → falso-positivo. Ahora el artículo intermedio es opcional.
        assertFalse(secuestroHarmful("secuestro de la sesion de usuario"))
    }

    @Test fun secuestroDeLaCookie_noEsDanino() {
        assertFalse(secuestroHarmful("revisar el secuestro de la cookie de auth"))
    }

    @Test fun secuestroDelToken_noEsDanino() {
        assertFalse(secuestroHarmful("auditar el secuestro del token de refresco"))
    }

    @Test fun secuestroDeLasSesiones_noEsDanino() {
        // Plural con artículo "las": forma natural "secuestro de las sesiones".
        assertFalse(secuestroHarmful("diagnostico del secuestro de las sesiones"))
    }

    @Test fun secuestroTecnicoSinArticulo_sigueNoDanino() {
        // Regresión: la forma sin artículo (c.630) sigue pasando tras c.642.
        assertFalse(secuestroHarmful("secuestro de cookie del backend"))
    }

    // ── Stems muertos por `\b` final en VIOLENCIA (c.630): `secuestr` no casaba
    //    secuestrar/secuestraron → PASABAN el gate. Ahora SÍ. ──

    @Test fun secuestrarAlVecino_esDanino() {
        // PRE-fix RED: `\bsecuestr\b` no casa "secuestrar" → PASABA (amenaza no bloqueada).
        assertTrue(secuestroHarmful("secuestrar al vecino esta noche"))
    }

    @Test fun secuestraronAHermano_esDanino() {
        assertTrue(secuestroHarmful("secuestraron a mi hermano en la carretera"))
    }

    @Test fun secuestroDeNino_esDanino() {
        // "secuestro de un niño" → stem casa, la exención DNS no aplica → bloqueado.
        assertTrue(secuestroHarmful("investigar el secuestro de un niño"))
    }

    @Test fun drogaEnFarmacia_noEsDanina() {
        // Proximidad médica/farmacéutica cubre TODAS las ocurrencias de "droga".
        assertFalse(drugHarmful("comprar la droga en la farmacia"))
    }

    @Test fun drogaRecetada_noEsDanina() {
        assertFalse(drugHarmful("ir a buscar la droga recetada al médico"))
    }

    @Test fun citaUrologoPorPene_noEsDanino() {
        // Colocación: "cita con el urólogo..." contiene el rango de "pene".
        assertFalse(sexualHarmful("cita con el urólogo por el pene"))
    }

    @Test fun examenProstate_noEsDanino() {
        // Proximidad médica cubre la mención anatómica.
        assertFalse(sexualHarmful("examen de la prostate de rutina"))
    }

    // c.638: SINGULARES "culo"/"teta". PRE-fix el stem listaba SÓLO los plurales
    // "culos"/"tetas" → el gate dejaba pasar "mostró el culo"/"enseña la teta"
    // (contenido sexual explícito no bloqueado, P1 privacidad/gate, misma clase
    // que c.630/c.633 — `\b` final mata el número).
    @Test fun mostrarElCulo_esDanino() {
        assertTrue(sexualHarmful("mostró el culo en la foto"))
    }

    @Test fun ensenarLaTeta_esDanino() {
        assertTrue(sexualHarmful("enseña la teta"))
    }

    @Test fun sacoElCulo_esDanino() {
        assertTrue(sexualHarmful("sacó el culo"))
    }

    @Test fun tetasPlural_preservado_esDanino() {
        // Plural ya casaba PRE-fix; debe seguir cazando (regresión).
        assertTrue(sexualHarmful("fotos de tetas"))
    }

    @Test fun contenidoPorno_preservado_esDanino() {
        // Palabra completa sin número, ya casaba; sin regresión.
        assertTrue(sexualHarmful("contenido porno"))
    }

    // ── Verdaderos positivos: contenido dañino que debe seguir bloqueándose ──

    @Test fun matarAUnaPersona_esDanino() {
        // 2ª ocurrencia sin cobertura: "matar a juan" no es técnica.
        assertTrue(violentHarmful("matar el proceso y luego matar a juan"))
    }

    @Test fun comprarBomba_esDanino() {
        assertTrue(violentHarmful("comprar una bomba para el ataque"))
    }

    @Test fun violarAAlguien_esDanino() {
        assertTrue(violentHarmful("violar a alguien esta noche"))
    }

    @Test fun amenazarConArma_esDanino() {
        assertTrue(violentHarmful("amenazar al vecino con una pistola"))
    }

    // c.637: enclíticos/pronominales/flexiones verbales que PRE-fix (stem con
    // `\b` final) NO casaban → "matarte"/"violarla"/"violaron"/"amenazarte"
    // PASABAN el gate (amenaza directa no bloqueada). Sin `\b` final casan.
    @Test fun matarEnclitico_esDanino() {
        assertTrue(violentHarmful("te voy a matar y matarte hoy"))
    }

    @Test fun matarseReflexivo_esDanino() {
        assertTrue(violentHarmful("matarse a si mismo"))
    }

    @Test fun violarlaEnclitico_esDanino() {
        assertTrue(violentHarmful("violarla esta noche"))
    }

    @Test fun violaronPretérito_esDanino() {
        assertTrue(violentHarmful("violaron a mi hermana"))
    }

    @Test fun amenazarteEnclitico_esDanino() {
        assertTrue(violentHarmful("amenazarte de muerte"))
    }

    @Test fun asesinarteEnclitico_esDanino() {
        assertTrue(violentHarmful("asesinarte mañana"))
    }

    // c.637 regression guards: idiomáticos/técnicos que sin `\b` final ahora
    // casarían el stem → se eximen con contains ampliados.
    @Test fun matarElHambre_noEsDanino() {
        assertFalse(violentHarmful("comprar algo para matar el hambre"))
    }

    @Test fun matarElTiempo_noEsDanino() {
        assertFalse(violentHarmful("jugar para matar el tiempo"))
    }

    @Test fun matarLasGanas_noEsDanino() {
        assertFalse(violentHarmful("dulce para matar las ganas de fumar"))
    }

    @Test fun matarLaSed_noEsDanino() {
        assertFalse(violentHarmful("agua para matar la sed"))
    }

    @Test fun matarElSueno_noEsDanino() {
        assertFalse(violentHarmful("cafe para matar el sueno"))
    }

    @Test fun matarElAburrimiento_noEsDanino() {
        assertFalse(violentHarmful("leer para matar el aburrimiento"))
    }

    @Test fun violaronPolitica_noEsDanino() {
        assertFalse(violentHarmful("violaron la politica de privacidad"))
    }

    @Test fun bombasDeAguaPlural_noEsDanino() {
        assertFalse(violentHarmful("comprar bombas de agua para el jardin"))
    }

    @Test fun pistolasDeAguaPlural_noEsDanino() {
        assertFalse(violentHarmful("pistolas de agua para los niños"))
    }


    @Test fun comprarCocaina_esDanino() {
        assertTrue(specificDrugHarmful("vende cocaina barata en la zona"))
    }

    @Test fun traeHeroina_esDanino() {
        assertTrue(specificDrugHarmful("trae heroina pura para el viernes"))
    }

    @Test fun narcotrafico_esDanino() {
        assertTrue(specificDrugHarmful("hay narcotrafico en la frontera"))
    }

    @Test fun contenidoPornografico_esDanino() {
        assertTrue(sexualHarmful("buscar porno en el móvil"))
    }

    @Test fun peneAislado_esDanino() {
        // Sin contexto médico que lo cubra.
        assertTrue(sexualHarmful("envíame una foto de tu pene"))
    }

    // ── Stems rotos por `\b` final (c.630): desnud/eroti/masturb no casaban
    //    desnudo/erotico/masturbacion (transición word→word). Ahora sí. ──

    @Test fun fotosDeDesnudos_esDanino() {
        // PRE-fix RED: `\bdesnud\b` no casa "desnudos" → PASABA (contenido explícito no bloqueado).
        assertTrue(sexualStemHarmful("ver fotos de desnudos en el móvil"))
    }

    @Test fun contenidoErotico_esDanino() {
        assertTrue(sexualStemHarmful("buscar contenido erotico en la red"))
    }

    @Test fun masturbacion_esDanina() {
        assertTrue(sexualStemHarmful("videos de masturbacion diaria"))
    }

    @Test fun chicasDesnudas_esDanino() {
        assertTrue(sexualStemHarmful("chicas desnudas en la cam"))
    }

    @Test fun drogaSinContextoMedico_esDanina() {
        assertTrue(drugHarmful("conseguir droga para la fiesta"))
    }

    // ── Normalización tolerante a acentos (c.519) ──

    @Test fun cocainaSinTilde_esDanina() {
        // La raíz ASCII casa el texto normalizado (sin diacríticos).
        assertTrue(specificDrugHarmful("vende cocaína barata"))
    }

    @Test fun urologoConTilde_noEsDanino() {
        // La exención de proximidad también normaliza: "urólogo" con tilde cubre.
        assertFalse(sexualHarmful("cita con el urólogo por el pene"))
    }

    // ── Stems rotos por `\b` final en drogas e insultos (c.632) ──
    //    Mismo defecto de clase que c.630 (sexual) y c.631 (violencia):
    //    `\b` final mata plurales y flexiones de género/número.

    @Test fun drogasPlural_esDanino() {
        // PRE-fix RED: `\bdroga\b` no casa "drogas" → PASABA.
        assertTrue(drugHarmful("comprar drogas ilegales para la fiesta"))
    }
    @Test fun narcotraficante_esDanino() {
        // PRE-fix RED: `\bnarcotrafico\b` no casa "narcotraficante" → PASABA.
        assertTrue(specificDrugHarmful("es un narcotraficante famoso de la zona"))
    }
    @Test fun drogasEnFarmacia_noEsDanina() {
        // Plural + proximidad médica: el legit sigue pasando.
        assertFalse(drugHarmful("comprar las drogas en la farmacia del barrio"))
    }
    @Test fun drogadictoEnTratamiento_noEsDanino() {
        // "drogadicto" casa el stem "droga" pero la proximidad "tratamiento" lo cubre.
        assertFalse(drugHarmful("drogadicto en tratamiento medico"))
    }

    @Test fun pendeja_esDanina() {
        // PRE-fix RED: `\bpendejo\b` no casa "pendeja" (femenino) → PASABA.
        assertTrue(insultHarmful("eres una pendeja"))
    }
    @Test fun estupidos_esDanino() {
        // PRE-fix RED: `\bestupido\b` no casa "estupidos" (plural) → PASABA.
        assertTrue(insultHarmful("estupidos los dos"))
    }
    @Test fun imbeciles_esDanino() {
        assertTrue(insultHarmful("imbeciles todos en esta oficina"))
    }
    @Test fun malparida_esDanina() {
        assertTrue(insultHarmful("malparida esa mujer"))
    }

    // ── c.635: falso-negativo P0 — drogas específicas eximidas por proximity ──
    //    La proximity médica era GLOBAL y cubría TODAS las ocurrencias del stem
    //    conjunto (droga|cocaina|marihuana|...). Así "fumar marihuana y luego ir
    //    a la farmacia" PASABA: "marihuana" es específica (señal fuerte) pero la
    //    co-aparición de "farmacia" la legitimaba. Fix: separar "droga" (proximity)
    //    de las específicas (sin proximity), alineando implementación ↔ KDoc.

    @Test fun marihuanaCercaDeFarmacia_esDanino() {
        // PRE-fix RED: la proximity "farmacia" cubría "marihuana" → PASABA.
        assertTrue(specificDrugHarmful("fumar marihuana y luego ir a la farmacia"))
    }
    @Test fun cocainaCercaDeMedico_esDanino() {
        // PRE-fix RED: la proximity "médico" cubría "cocaína" → PASABA.
        assertTrue(specificDrugHarmful("comprar cocaina y pasar a ver al medico"))
    }
    @Test fun heroinaRecetada_esDanino() {
        // PRE-fix RED: la proximity "recetada" cubría "heroína" → PASABA.
        assertTrue(specificDrugHarmful("traer heroina que está recetada"))
    }
    @Test fun metanfetaminaEnFarmacia_esDanino() {
        // PRE-fix RED: la proximity "farmacia" cubría "metanfetamina" → PASABA.
        assertTrue(specificDrugHarmful("vender metanfetamina afuera de la farmacia"))
    }

    // ── c.635: regression guards — "droga" genérica MANTIENE la proximity ──

    @Test fun drogaEnFarmacia_sigueNoEsDanina() {
        // El legit "droga en farmacia" sigue pasando: "droga" es genérica.
        assertFalse(drugHarmful("comprar la droga en la farmacia"))
    }
    @Test fun drogadictoEnTratamiento_sigueNoEsDanino() {
        // El legit "drogadicto en tratamiento" sigue pasando.
        assertFalse(drugHarmful("drogadicto en tratamiento medico"))
    }
    @Test fun drogaSinContextoMedico_sigueEsDanina() {
        // "droga" aislada sin proximity sigue siendo dañina.
        assertTrue(drugHarmful("conseguir droga para la fiesta"))
    }

    // ── c.636: `cuchill` stem muerto por `\b` final — falso-negativo P1 ──
    //    PRE-fix la raíz `\bcuchill\b` (con `\b` final, en el stem conjunto de
    //    violencia) NO casaba "cuchillo/cuchillada/cuchillazo/acuchillar" (esas
    //    formas añaden letras tras "cuchill") → "amenazar con cuchillo" PASABA.
    //    Fix: separar `cuchill` con `\b` inicial SIN `\b` final + `contain`
    //    culinarios para no bloquear "cuchillo de cocina/chef/pan" (falso
    //    positivo P1 datos). Mismo defecto de clase que c.630/c.631/c.633.

    @Test fun amenazarConCuchillo_esDanino() {
        // PRE-fix RED: `\bcuchill\b` no casa "cuchillo" → PASABA.
        assertTrue(cuchilloHarmful("amenazar al vecino con un cuchillo"))
    }
    @Test fun cuchillada_esDanina() {
        // PRE-fix RED: `\bcuchill\b` no casa "cuchillada" → PASABA.
        assertTrue(cuchilloHarmful("le dio una cuchillada en la cara"))
    }
    @Test fun cuchillazo_esDanino() {
        // PRE-fix RED: `\bcuchill\b` no casa "cuchillazo" → PASABA.
        assertTrue(cuchilloHarmful("sufrió un cuchillazo en el cuello"))
    }
    @Test fun acuchillar_esDanino() {
        // PRE-fix RED: `\bcuchill\b` no casa "acuchillar" → PASABA.
        assertTrue(cuchilloHarmful("decidió acuchillar al intruso"))
    }
    @Test fun cuchilloAislado_esDanino() {
        // "llevar un cuchillo" (mención aislada, ambigua) es señal suficiente en
        // captura de tareas personales: NO se exime (regla general del gate).
        assertTrue(cuchilloHarmful("llevar un cuchillo en la mochila"))
    }

    // ── c.636: regression guards — contextos culinarios legítimos PASAN ──

    @Test fun cuchilloDeCocina_noEsDanino() {
        // Contain "cuchillo de cocina" envuelve el stem "cuchill" → eximido.
        assertFalse(cuchilloHarmful("comprar un cuchillo de cocina nuevo"))
    }
    @Test fun cuchilloDeChef_noEsDanino() {
        assertFalse(cuchilloHarmful("regalar un cuchillo de chef profesional"))
    }
    @Test fun cuchilloDePan_noEsDanino() {
        assertFalse(cuchilloHarmful("traer el cuchillo de pan a la mesa"))
    }
    @Test fun cuchillosDePescado_noEsDanino() {
        // Plural "cuchillos" + contexto culinario.
        assertFalse(cuchilloHarmful("afilar los cuchillos de pescado"))
    }
    @Test fun afiladorDeCuchillos_noEsDanino() {
        assertFalse(cuchilloHarmful("comprar afilador de cuchillos"))
    }
    @Test fun setDeCuchillos_noEsDanino() {
        assertFalse(cuchilloHarmful("pedir un set de cuchillos para la boda"))
    }
    @Test fun cuchilloParaCocina_noEsDanino() {
        // Forma alternativa "cuchillo para cocina" (contain `para`).
        assertFalse(cuchilloHarmful("cuchillo para cocina de acero"))
    }

    // ── c.641: regression guards — artículo definido entre nexo y sustantivo ──
    // El habla natural española intercala "el/la/los/las" entre "de"/"para" y el
    // sustantivo culinario ("cuchillo para el pan", "cuchillo de la cocina").
    // PRE-fix las exenciones `de\s+(sust)` y `para\s+(sust)` NO casaban el
    // artículo → el stem "cuchill" casaba pero NINGÚN contain lo envolvía →
    // BLOQUEADO (falso-positivo: captura de cocina legítima rechazada).

    @Test fun cuchilloParaElPan_noEsDanino() {
        // PRE-fix RED: `para\s+pan` no casa "para el pan" (artículo intermedio).
        assertFalse(cuchilloHarmful("comprar cuchillo para el pan"))
    }
    @Test fun cuchilloParaLaCarne_noEsDanino() {
        assertFalse(cuchilloHarmful("regalar cuchillo para la carne"))
    }
    @Test fun cuchilloParaLaFruta_noEsDanino() {
        assertFalse(cuchilloHarmful("traer cuchillo para la fruta"))
    }
    @Test fun cuchilloDeLaCocina_noEsDanino() {
        // PRE-fix RED: `de\s+cocina` no casa "de la cocina".
        assertFalse(cuchilloHarmful("guardar el cuchillo de la cocina"))
    }
    @Test fun cuchillosDeLaCarniceria_noEsDanino() {
        // Plural + artículo: `cuchillos de la carnicería`.
        assertFalse(cuchilloHarmful("afilar los cuchillos de la carnicería"))
    }

    // ── c.644: exenciones culinarias ahora admiten la CONTRACCIÓN `del`
    //    (de+el) además del artículo espaciado (el/la/los/las). Mismo defecto
    //    de clase que c.642 (secuestr): la regex `de\s+(el|la|...)?` exige un
    //    espacio tras `de` y no casa "del" (sin espacio tras la "e"). Así
    //    "cuchillo del chef"/"cuchillo del pan"/"cuchillo del mesón" — formas
    //    naturales contractas — se BLOQUEABAN. ──

    @Test fun cuchilloDelChef_noEsDanino() {
        assertFalse(cuchilloHarmful("comprar cuchillo del chef"))
    }
    @Test fun cuchilloDelPan_noEsDanino() {
        assertFalse(cuchilloHarmful("regalar cuchillo del pan"))
    }
    @Test fun cuchilloDelMeson_noEsDanino() {
        assertFalse(cuchilloHarmful("traer cuchillo del meson"))
    }
    @Test fun cuchillosDelPescado_noEsDanino() {
        // Plural + contracción: `cuchillos del pescado`.
        assertFalse(cuchilloHarmful("afilar los cuchillos del pescado"))
    }
    @Test fun cuchilloDeLaMesa_noEsDanino() {
        // 2.ª contain: `del` como alternativa aparte; caso espaciado intacto.
        assertFalse(cuchilloHarmful("guardar el cuchillo de la mesa"))
    }
    @Test fun cuchilloDeLaCocina2_noEsDanino() {
        // 2.ª contain con artículo espaciado intacto.
        assertFalse(cuchilloHarmful("comprar cuchillo de la cocina"))
    }

    // ── Regression guards (true-positivos que deben seguir bloqueados pese
    //    a la nueva flexibilidad de la contracción `del`). ──

    @Test fun cuchilloSicario_esDanino() {
        // "cuchillo del sicario": "del" casa la contracción PERO "sicario" no
        // está en el listado culinario → sigue bloqueado.
        assertTrue(cuchilloHarmful("amenazar con cuchillo del sicario"))
    }
    @Test fun cuchilloDelEnemigo_esDanino() {
        // "cuchillo del enemigo": contracción casa pero "enemigo" no es
        // culinario → bloqueado (true-positivo).
        assertTrue(cuchilloHarmful("llevar cuchillo del enemigo"))
    }
}

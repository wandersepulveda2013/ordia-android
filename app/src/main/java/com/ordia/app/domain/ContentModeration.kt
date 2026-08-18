package com.ordia.app.domain

import java.text.Normalizer

/**
 * Moderación temática de contenido para los gates de captura/lectura
 * ([`ContextPrivacyFilter`][com.ordia.app.context.ContextPrivacyFilter] y
 * [`IntelligenceSafetyGate`][com.ordia.app.intelligence.IntelligenceSafetyGate]).
 *
 * Modera el TEMA del que la inteligencia puede ocuparse (sexual, violencia,
 * drogas). No detecta secretos/credenciales (eso vive en
 * [SensitiveSecretPatterns]). Su propósito es evitar que se procese o persista
 * contenido dañino; nunca debe descartar captura legítima de tareas cotidianas.
 *
 * Causa raíz cerrada (c.582): las listas anteriores casaban RAÍCES de palabras
 * (`matar`, `bomba`, `violar`, `droga`, `amenaza`, `pistola`, `pene`, `vagina`)
 * sin distinguir el sentido legítimo. Eso bloqueaba 16/17 tareas reales
 * ("matar el proceso del servidor", "comprar bomba de agua", "violar la
 * política de la empresa", "comprar la droga en la farmacia", "revisar el
 * secuestro de DNS", "hacer el modelo de amenaza", "limpiar la pistola de
 * agua", "cita con el urólogo por el pene") y se descartaban SILENCIOSAMENTE en
 * el canal de captura contextual (ContextEngine.PRIVACY_FILTER) o se rechazaban
 * en el asistente del overlay. Pérdida de datos en las rutas de mayor valor.
 *
 * Solución: la detección es por RAÍZ con EXENCIONES de contexto legítimo. Una
 * raíz "dañina" solo bloquea si aparece en una ocurrencia NO cubierta por una
 * forma legítima. Así "matar el proceso y luego matar a juan" sigue bloqueando
 * (la 2ª ocurrencia no está cubierta) mientras "matar el proceso" pasa.
 * Determinista (regex + rangos), sin random, sin IA fingida.
 *
 * Cada gate define sus propias [ModerationRule] (su conjunto de raíces) y llama
 * a [isHarmful] — el ALGORITMO (raíz + exenciones de colocación/proximidad) se
 * comparte y no puede desincronizarse (mismo principio que c.299 para secretos).
 * Las CATEGORÍAS específicas de solo-lectura (acoso, autodaño, transferencias,
 * política) siguen vivas en ContextPrivacyFilter porque no aplican al gate de
 * inteligencia.
 */
object ContentModeration {

    /**
     * Una raíz de contenido potencialmente dañino y las formas legítimas que la
     * eximen.
     *
     * @property stem raíz a buscar en el texto normalizado (sin tildes,
     *  minúsculas). Se busca como palabra/límite según el regex provisto.
     * @property contain expresiones legítimas cuyo rango de coincidencia
     *  CONTIENE al de la raíz (colocación: "matar el proceso",
     *  "modelo de amenaza"). Cubre una ocurrencia de la raíz si su rango cae
     *  dentro del rango de una coincidencia.
     * @property proximity expresión cuyo emparejamiento en cualquier lugar del
     *  texto cubre TODAS las ocurrencias de la raíz (señal médica, farmacéutica,
     *  etc.). Úsese con precaución: solo para contextos que legitiman toda la
     *  mención (p.ej. "droga" + palabra de farmacia/receta/médico).
     */
    data class ModerationRule(
        val stem: Regex,
        val contain: List<Regex> = emptyList(),
        val proximity: Regex? = null
    )

    /**
     * @return true si alguna ocurrencia de la raiz de [rule] en [text] no esta
     *  cubierta por una forma legitima (colocacion o proximidad).
     */
    fun isHarmful(text: String, rule: ModerationRule): Boolean {
        val norm = normalize(text)
        val stems = rule.stem.findAll(norm).toList()
        if (stems.isEmpty()) return false
        val containRanges = rule.contain
            .flatMap { it.findAll(norm).map { m -> m.range } }
        val proximityHit = rule.proximity?.containsMatchIn(norm) ?: false
        for (s in stems) {
            val covered = proximityHit ||
                containRanges.any { it.first <= s.range.first && it.last >= s.range.last }
            if (!covered) return true
        }
        return false
    }

    /**
     * Conjunto canónico de reglas de moderación temática (sexual, violencia,
     * drogas, insultos) con exenciones de contexto legítimo (c.582).
     *
     * FUENTE ÚNICA para TODO gate de captura/IA que deba bloquear contenido
     * dañino antes de procesarlo: [IntelligenceSafetyGate] (gate de IA) y
     * [com.ordia.app.context.ContextIntentEngine] (captura contextual → tarea).
     * Antes de c.611, [ContextIntentEngine.containsBlockedContent] duplicaba
     * estas raíces con regex inline SIN exenciones, así que bloqueaba en bruto
     * ("matar el proceso", "comprar bomba de agua", "comprar la droga en la
     * farmacia") justo las tareas legítimas que c.582 eximió en los otros dos
     * gates — descarte silencioso de captura (P1 datos/evitar olvidos). Al
     * consumir esta lista, la captura contextual nunca puede desincronizarse de
     * la puerta de IA: añadir una exención o sentido legítimo aquí corrige
     * ambos gates a la vez (mismo principio que c.299 para secretos).
     *
     * Las categorías de solo-lectura de [com.ordia.app.context.ContextPrivacyFilter]
     * (acoso, autodaño, transferencias, política) NO viven aquí: no aplican al
     * gate de IA ni a la decisión de crear una sugerencia de tarea.
     */
    val THEMATIC_RULES: List<ModerationRule> = listOf(
        // Contenido sexual explícito. "sexo"/"sexual"/"porno"/"xxx"/"culos"/"tetas"/"pene"/"vagina"/"orgasmo"
        // son PALABRAS COMPLETAS que rara vez aparecen en tareas legítimas, así que se
        // casan con `\b` a ambos lados para no alcanzar prefijos accidentales (p.ej. "pene"
        // en "Penélope" — `pene` requiere límite final). Las raíces anatómicas (pene/vagina)
        // SÍ se eximen en contexto médico (cita con el urólogo/ginecólogo por...).
        ModerationRule(
            stem = Regex("""\b(sexo|sexual|porno|xxx|culos|tetas|pene|vagina|orgasmo)\b"""),
            contain = listOf(
                Regex("""\b(cita con el ur[oó]logo|cita con la ginec[oó]loga?)\b[^.]*\bpene\b"""),
                Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
            ),
            proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
        ),
        // Raíces de palabras flexionadas (c.630): "desnud"/"eroti"/"masturb" NUNCA aparecen
        // aisladas en español — existen como "desnudo/desnuda/desnudos/desnudarse",
        // "erotico/erotica/eroticos", "masturbacion/masturbarse". Antes compartían la
        // regex de arriba con `\b` FINAL, lo que las MATABA: "desnud" seguido de 'o' es
        // word→word (sin límite) → "desnudos"/"contenido erotico"/"masturbacion" PASABAN
        // el gate (contenido explícito no bloqueado, P0 privacidad/gate). Fix: su propia
        // regla con `\b` INICIAL pero SIN `\b` final, así casa la raíz y sus flexiones.
        // Misma exención médica por proximidad que las palabras anatómicas (paridad).
        ModerationRule(
            stem = Regex("""\b(desnud|eroti|masturb)"""),
            proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
        ),
        // Violencia y amenazas. Las raíces tienen sentidos legítimos muy
        // frecuentes en tareas técnicas ("matar el proceso", "violar la
        // política", "modelo de amenaza", "pistola/bomba de agua") que se
        // eximen por colocación. `cuchill` vive en su propia regla (c.636,
        // ver abajo): la familia "cuchillo/cuchillada/cuchillazo/acuchillar"
        // escribe letras tras la raíz, así `\bcuchill\b` (con `\b` final, la
        // forma previa) NO casaba ninguna de ellas — el gate dejaba pasar
        // "amenazar con cuchillo", "cuchillada en la cara", "acuchillar al
        // intruso" (contenido dañino no bloqueado, mismo defecto de clase
        // que c.630/c.631/c.632/c.633). Sin `\b` final la raíz caza, PERO eso
        // atrapa también "cuchillo de cocina/chef/pan" (falso positivo P1
        // datos — pérdida de captura legítima), así que se añaden `contain`
        // para los contextos culinarios legítimos ANTES de revivir el stem
        // (careful-design, anti-falso-positivo P1).
        ModerationRule(
            stem = Regex("""\b(matar|asesinar|violar|bomba|amenaza|escopeta|pistola)\b"""),
            contain = listOf(
                Regex("""\bmatar\b\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
                Regex("""\bviolar\b\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|clausula|cl[áa]usula|t[ée]rminos?)\b"""),
                Regex("""\b(modelo|m[oó]delo)\s+de\s+amenaza\b"""),
                Regex("""\bamenaza\b\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
                Regex("""\b(bomba|pistola|escopeta)\s+de\s+agua\b"""),
                Regex("""\b(matar|asesinar)\s+(un|el)\s+proceso\b""")
            )
        ),
        // `cuchill`: raíz flexionada (c.636). `\b` INICIAL sin `\b` final para
        // casar la familia "cuchillo/cuchilla/cuchillada/cuchillazo/acuchillar/
        // acuchilló". PRE-fix la raíz iba con `\b` final en el stem conjunto de
        // violencia, así NO casaba ninguna de esas formas (exigía límite de
        // palabra justo después de "cuchill") → "amenazar con cuchillo" PASABA.
        // `\b(acuchill|cuchill)`: el `\b` va al INICIO de palabra, así casa
        // "acuchillar" (la "a" inicial crea el límite) Y "cuchillo" (la "c"
        // inicial crea el límite); sin esta alternativa "acuchillar" no casa
        // porque "cuchill" va precedido de "a" (ambas letras, sin límite).
        // Exenciones `contain` para los contextos culinarios legítimos: el
        // algoritmo exime un match del stem si un `contain` ENVUELVE su rango
        // (ver [isHarmful]), así "cuchillo de cocina" casa el stem "cuchill"
        // PERO también el contain "cuchillo de cocina" que lo envuelve → pasa.
        // "amenazar con cuchillo" casa el stem pero NINGÚN contain lo envuelve
        // → bloqueado. `\b` final en cada contain acota el contexto (evita que
        // "cuchillo de cocinero sicario" se exima). Casos no-culinarios de
        // "cuchillo" como objeto personal ("llevar un cuchillo" — ambiguo) NO
        // se eximen: en captura de tareas personales esa mención aislada es
        // señal suficiente, alineado con la regla general de este gate.
        ModerationRule(
            stem = Regex("""\b(acuchill|cuchill)"""),
            contain = listOf(
                Regex("""\bcuchill[oa]s?\s+de\s+(cocina|chef|pan|mes[oó]n|m[aá]rmol|carnicer[ií]a|caza|pescado|mesa|untar|trinchar|cocinero|palo|mantequilla|fruta|carne|queso)\b"""),
                Regex("""\b(afilad[oa]r(es)?|afi[cz]a(c)?dor(es)?)\s+de\s+cuchill[oa]s?\b"""),
                Regex("""\b(set|juego|bloque|cubierto|cubre)\s+de\s+cuchill[oa]s?\b"""),
                Regex("""\bcuchill[oa]\s+(de\s+(mesa|untar|cocina)|para\s+(pan|cocina|fruta|carne|queso))\b""")
            )
        ),
        // Raíz flexionada SECUESTR (secuestrar/secuestro/secuestrado/...).
        // c.630: separada de la regla de violencia porque el `\b` FINAL mataba
        // todas sus flexiones (r→o/a word→word) → "secuestrar al vecino" /
        // "secuestraron a mi hermano" PASABAN el gate. Sin `\b` final casa la
        // raíz y todas sus formas; las exenciones de colocación legitiman el
        // sentido técnico ("revisar el secuestro de DNS/ sesión/ cookie/ token").
        ModerationRule(
            stem = Regex("""\bsecuestr"""),
            contain = listOf(
                Regex("""\b(revisi[oó]n|revisar|diagn[oó]stico|diag|audit|auditor[íi]a)\s+(de[l]?)\s*secuestro\b"""),
                Regex("""\bsecuestro\s+de\s+(dns|sesi[oó]n|cookie|token|sesiones?)\b""")
            )
        ),
        // "droga" (genérica): se exonera por proximidad médica/farmacéutica — la
        // mención entera es legítima en "comprar la droga en la farmacia", "ir a
        // buscar la droga recetada", "drogadicto en tratamiento". c.632: `\b`
        // INICIAL sin `\b` final para casar plurales/flexiones ("drogas") que con
        // `\b` final pasaban el gate.
        ModerationRule(
            stem = Regex("""\bdroga"""),
            proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
        ),
        // Drogas ESPECÍFICAS (cocaína, heroína, marihuana, metanfetamina,
        // narcotráfico): SIN proximity. c.635 cierra un falso-negativo P0: antes
        // compartían la proximity médica de "droga", así "fumar marihuana y luego
        // ir a la farmacia" PASABA (la proximity "farmacia" cubría TODAS las
        // ocurrencias del stem, incluso las ilegítimas). La proximity es GLOBAL por
        // diseño (ver [ModerationRule.proximity]) y solo legitima contextos que
        // eximen la mención ENTERA; una droga específica cuya mención aislada ya es
        // señal fuerte (así lo declara el KDoc de esta regla desde c.582) NO debe
        // quedar cubierta por la mera co-aparición de "farmacia/receta/médico" en
        // cualquier punto del texto — eso es justo el caso patológico de
        // disimular una mención dañina tras una palabra blanca. Alinea
        // implementación ↔ KDoc (misma familia de divergencia que c.372/c.373).
        // `narcotrafic` no tiene exención posible ("narcotráfico de DNS" no existe).
        // Los casos técnicos legítimos de drogas específicas (anestésico tópico de
        // cocaína, metanfetamina recetada) son raros en captura de tareas
        // personales; si surgen, se eximen con `contain` explícito (careful-design,
        // registrado), no con una proximity vaga que reabre el falso-negativo.
        // `\b` INICIAL sin `\b` final: casa plurales/flexiones ("cocainas",
        // "marihuanas", "narcotraficante") — misma clase que c.632/c.633.
        ModerationRule(
            stem = Regex("""\b(cocaina|heroina|marihuana|metanfetamina|narcotrafic)""")
        ),
        // Insultos graves: raíces flexionadas (c.632). `\b` INICIAL sin `\b` final
        // para casar género/número ("pendeja", "estupidos", "imbeciles",
        // "malparida") — justo las formas que se escriben en móvil y que con
        // `\b` final pasaban el gate. Sin exención: su mención aislada es señal
        // fuerte y rara vez legitima una tarea.
        ModerationRule(stem = Regex("""\b(pendej|estupid|imbecil|malparid|hijueputa)"""))
    )

    /** Normaliza a minúsculas sin tildes/diacríticos para comparación tolerante
     *  a acentos (el español casual de móvil escribe sin acentos con frecuencia). */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}

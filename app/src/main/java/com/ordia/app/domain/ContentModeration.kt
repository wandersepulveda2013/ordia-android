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

    /** Normaliza a minúsculas sin tildes/diacríticos para comparación tolerante
     *  a acentos (el español casual de móvil escribe sin acentos con frecuencia). */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}

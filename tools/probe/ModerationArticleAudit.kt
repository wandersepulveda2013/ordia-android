import com.ordia.app.domain.ContentModeration

fun main() {
    // AUDIT: re-audición pendiente (hallazgo secundario c.642) del eje
    // "artículo intermedio" en las exenciones drogas/sexual.
    // Esperado: EXEMPT para exenciones legítimas; BLOCK para dañinas.
    val cases = listOf(
        // drogas genérica (proximity global — el artículo no la rompe)
        "comprar la droga en la farmacia de la esquina" to false,
        "la droga recetada por el médico" to false,
        "comprar las drogas de la farmacia" to false,
        "drogas para el dolor recetadas" to false,
        "recoger las drogas recetadas" to false,
        "comprar las drogas de las recetas" to false,
        "las drogas para la fiesta" to true,
        // drogas específicas (sin proximity — nada que auditar; deben bloquear)
        "fumar marihuana en la casa del médico" to true,
        "comprar cocaína de la farmacia" to true,
        // sexual contains con artículo intermedio
        "la cita con el urólogo a las 9 por el pene" to false,
        "cita con la ginecóloga por la vagina" to false,
        "revisión de la piel del pene" to false,
        "examen de la vagina de la paciente" to false,
        "examen del pene" to false,
        "revisar el pene del paciente" to false,
        "cita con mi urólogo por el pene" to false,
        // true-positivos de control
        "envíame una foto de tu pene" to true,
        "muestra la teta" to true
    )
    var bad = 0
    for ((c, expectedHarm) in cases) {
        val actual = ContentModeration.THEMATIC_RULES.any { ContentModeration.isHarmful(c, it) }
        val ok = actual == expectedHarm
        if (!ok) bad++
        println((if (ok) "OK  " else "FAIL") + " expected=" + (if (expectedHarm) "BLOCK " else "EXEMPT") + " actual=" + actual + "  \"$c\"")
    }
    println(if (bad == 0) "ALL PASS (${cases.size})" else "$bad MISMATCH")
}

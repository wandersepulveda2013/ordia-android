import com.ordia.app.context.ContextIntentEngine

fun main() {
    val engine = ContextIntentEngine
    val klass = engine.javaClass
    fun call(name: String, arg: String): Boolean {
        val m = klass.getDeclaredMethod(name, String::class.java)
        m.isAccessible = true
        return m.invoke(engine, arg) as Boolean
    }

    val dateCases = listOf(
        "ayer", "anteayer", "antier",
        "esta mañana", "esta madrugada", "esta noche", "esta tarde",
        "mañana", "hoy",
        "a medianoche", "al mediodía", "al mediodia",
        "el 15 de marzo", "1 de enero",
        // Períodos relativos (paridad c.602 con extractDateTime c.598/c.599)
        "la semana que viene", "el próximo mes", "el año que viene",
        "la semana pasada", "el mes anterior", "la quincena pasada",
        "en 2 semanas", "dentro de 3 meses", "de aquí a 5 días",
        "en un par de semanas", "en una semana", "en unos meses"
    )
    val timeCases = listOf(
        "a medianoche", "al mediodía", "al mediodia",
        "a las 3 y media", "a las 3 y cuarto", "a las 10 menos cuarto",
        "a las 3", "3:00"
    )

    println("=== PARITY: extractDateTime vs hasDateReference ===")
    for (c in dateCases) {
        val dt = ContextIntentEngine.extractDateTime(c)
        val hdr = call("hasDateReference", c)
        val flag = if (dt != null && !hdr) "  <<< GAP" else ""
        println("  %-22s extractDateTime=%-7s hasDateReference=%-5s%s".format(c, dt != null, hdr, flag))
    }
    println("=== PARITY: extractDateTime vs hasTimeReference ===")
    for (c in timeCases) {
        val dt = ContextIntentEngine.extractDateTime(c)
        val htr = call("hasTimeReference", c)
        val flag = if (dt != null && !htr) "  <<< GAP" else ""
        println("  %-22s extractDateTime=%-7s hasTimeReference=%-5s%s".format(c, dt != null, htr, flag))
    }

    println("=== IMPACT: analyze() borderline cases (would the gap drop a commitment?) ===")
    // Stronger realistic commitment phrases. Compare same verb + "ayer"/"medianoche" (gap)
    // vs + "mañana"/"a las 3" (recognized) to isolate the bonus effect.
    val phrases = listOf(
        "pagar la factura ayer", "pagar la factura mañana",
        "pagar la factura la semana que viene", "pagar la factura en una semana",
        "pagar la factura en 2 semanas", "pagar la factura el próximo mes",
        "pagar la factura dentro de 3 meses",
        "entregar el reporte a medianoche", "entregar el reporte a las 3",
        "revisar el documento al mediodía", "revisar el documento a las 3",
        "confirmar la cita ayer", "confirmar la cita mañana",
        "enviar el correo a medianoche", "enviar el correo a las 3"
    )
    val now = System.currentTimeMillis()
    for (p in phrases) {
        val ev = com.ordia.app.context.ContextEvent(
            com.ordia.app.context.ContextCaptureSource.SHARED_TEXT,
            p, now, null, null
        )
        val intent = try { ContextIntentEngine.analyze(ev) } catch (e: Exception) { null }
        val hdr = call("hasDateReference", p)
        val htr = call("hasTimeReference", p)
        println("  %-34s hdr=%-5s htr=%-5s analyze=%s".format(p, hdr, htr, intent != null))
    }

    println("=== NEGATIVE: calificador SIN unidad (NO debe contar como fecha) ===")
    // extractDateTime exige palabra de unidad en la rama calificada (days != null),
    // así "el reporte pasado" (sin semana/mes/...) devuelve null → hasDateReference
    // debe ser false para no inflar el bono contextual sin ancla real (falso positivo).
    val negatives = listOf(
        "el reporte pasado", "la tarea anterior", "el siguiente paso",
        "el documento entrante"
    )
    for (n in negatives) {
        val dt = ContextIntentEngine.extractDateTime(n)
        val hdr = call("hasDateReference", n)
        val flag = if (dt == null && hdr) "  <<< FALSE POSITIVE" else ""
        println("  %-30s extractDateTime=%-7s hasDateReference=%-5s%s".format(n, dt != null, hdr, flag))
    }
}

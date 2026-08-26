import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextPrivacyFilter

/**
 * Sonda persistida c.1198 (cierre POR DISEÑO de la lateral (a) de la
 * auditoría VIGESIMOTERCERA finanzas, c.1197): verifica que TODA forma con
 * \btransferencia\b llega NULL por el paso 1 de `analyze`
 * ([ContextPrivacyFilter], blocklist transferencia/depósito/retiro/saldo/
 * estado de cuenta) — pin de privacidad deliberada, precedente c.1029
 * «contraseña». El hermano c.1198 (primer-push, precedente c.1077) cerró
 * además la rendija PLURAL (plurales del bloqueo recalibrados), de modo
 * que las formas con «transferencias» (antes pin byte-idéntico por bajo
 * score) quedan también NULL-BLOQUEADO directo desde el paso 1.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t")
    }
    println("== (a) formas «transferencia» (esperado NULL-BLOQUEADO):")
    listOf(
        "hacer la transferencia al casero el lunes",
        "hacer la transferencia del alquiler mañana",
        "recuérdame hacer la transferencia al casero",
        "tengo que hacer la transferencia del mes",
        "ya hice la transferencia ayer",
        "quizá haga la transferencia mañana",
        "no voy a hacer la transferencia hoy"
    ).forEachIndexed { i, t -> show("A${i + 1}", t) }

    println("== plural (rendija cerrada por el hermano c.1198; esperado NULL-BLOQUEADO):")
    show("P1", "hacer las transferencias del mes el lunes")
    show("P2", "recuérdame hacer las transferencias de la casa")

    println("== vecinos sin palabra bloqueada (esperado HIT):")
    listOf(
        "pagar el alquiler al casero el lunes",
        "ingresar dinero en el cajero",
        "retirar dinero en el cajero mañana",
        "depositar el cheque en el banco",
        "cobrar la nómina mañana"
    ).forEachIndexed { i, t -> show("V${i + 1}", t) }

    println("== verificación directa del paso 1 (containsSensitiveContent):")
    listOf(
        "hacer la transferencia al casero el lunes",
        "pagar el alquiler al casero el lunes"
    ).forEach { t ->
        println("  blocked=${ContextPrivacyFilter.containsSensitiveContent(t)} ← $t")
    }
}

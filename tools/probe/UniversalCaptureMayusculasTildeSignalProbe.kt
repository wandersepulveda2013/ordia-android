// c.1101 — sonda persistente POST: mayúsculas con tilde SÍ capturan en las
// señales de la captura universal (bug sistémico (?i) ASCII-only; misma clase
// resuelta en el asistente c.1096). PRE medido con sonda efímera (base c56291b):
// «RECUÉRDAME LLAMAR A MAMÁ» → TASK, «AVÍSAME MAÑANA DE LLAMAR AL BANCO» →
// TASK (recordatorio degradado a tarea), «REUNIÓN CON ANA» → INBOX (acción
// perdida). Fix UN punto mecánico: «(?i)» → «(?iu)» en reminderSignal y
// taskSignal de UniversalCaptureEngine.kt (UNICODE_CASE añade fold Unicode;
// semántica ASCII idéntica; \b intacto). POST: 3 GAPs capturan, 6 pines
// byte-equivalentes intactos (minúsculas, ASCII-caps, comandos, INBOX sin
// señal, reflexiva). Determinista (regex), cero random, cero IA fingida.
@file:Suppress("ClassName")
import com.ordia.app.domain.UniversalCaptureEngine
import com.ordia.app.data.local.CaptureTarget

fun main() {
    var ok = true
    fun check(name: String, expected: CaptureTarget, raw: String) {
        val got = UniversalCaptureEngine.interpret(raw)
        val pass = got.target == expected
        if (!pass) ok = false
        println("${if (pass) "OK  " else "FAIL"} $name -> ${got.target} (esperado $expected)")
    }
    // Capturas GAP cerradas por el fix
    check("caps-recuerdame-reminder", CaptureTarget.REMINDER, "RECUÉRDAME LLAMAR A MAMÁ")
    check("caps-avisame-reminder", CaptureTarget.REMINDER, "AVÍSAME MAÑANA DE LLAMAR AL BANCO")
    check("caps-reunion-task", CaptureTarget.TASK, "REUNIÓN CON ANA")
    // Pines byte-equivalentes (eran OK en PRE y deben seguir OK)
    check("minusculas-recuerdame", CaptureTarget.REMINDER, "recuérdame llamar a mamá")
    check("caps-sin-tilde-recordatorio", CaptureTarget.REMINDER, "RECORDATORIO COMPRAR LECHE")
    check("caps-reflexiva-olvidar", CaptureTarget.REMINDER, "QUE NO SE ME OLVIDE COMPRAR LECHE")
    check("caps-nota-sin-tilde", CaptureTarget.NOTE, "GUARDAR ESTO COMO NOTA: IDEAS")
    check("caps-tarea-comando", CaptureTarget.TASK, "CREAR UNA TAREA: COMPRAR LECHE")
    // Anti-overreach: sin señal sigue INBOX (el fold no inventa señales)
    check("caps-inbox-fallback", CaptureTarget.INBOX, "UNA IDEA SUELTA QUE TODAVÍA NO SÉ ORGANIZAR")
    if (ok) println("POST sonda c.1101 mayusculas-tilde-signals: OK (9/9)") else println("POST sonda c.1101 mayusculas-tilde-signals: FAIL")
}

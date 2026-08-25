import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import java.time.ZoneId
import java.time.ZonedDateTime

// Sonda de aceptación c.1112 — paridad sustantivo/verbo en entity-lookup.
// PRE (efímera /tmp/probe1112/Probe.kt): 4/4 gaps FALLABAN («No encuentro nada
// que sea «pago de la luz»…» con «Pagar luz» EXISTENTE). POST: los 4 gaps
// resuelven, las 2 inversas resuelven, los 2 anti-overreach siguen en NULL y
// los pins de subcadena no se mueven. El fallback por tokens exige que TODOS
// los tokens de contenido casen y que al menos un lado del par raíz sea
// infinitivo (ar/er/ir): «Marta» no casa con «martes».
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    fun ask(q: String, tasks: List<TaskEntity>) =
        AssistantEngine.answer(q, tasks, emptyList(), emptyList(), now = now, zone = zone)

    val verbTitles = listOf(
        TaskEntity(id = 1, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)),
        TaskEntity(id = 2, title = "Cenar con los abuelos", dueAt = at(2026, 8, 29, 21, 0)),
        TaskEntity(id = 3, title = "Llamar al banco", startAt = at(2026, 8, 27, 11, 0)),
        TaskEntity(id = 4, title = "Comprar el pan", dueAt = at(2026, 8, 27, 9, 0))
    )
    val nounTitles = listOf(
        TaskEntity(id = 5, title = "Pago de la luz", dueAt = at(2026, 9, 15, 12, 0)),
        TaskEntity(id = 6, title = "La cena de empresa", dueAt = at(2026, 8, 29, 21, 0))
    )
    val overreach = listOf(
        TaskEntity(id = 7, title = "Visitar a Marta", dueAt = at(2026, 8, 30, 10, 0)),
        TaskEntity(id = 8, title = "Pagar el médico", dueAt = at(2026, 9, 1, 9, 0)),
        TaskEntity(id = 11, title = "Cena con Marta", dueAt = at(2026, 8, 27, 21, 0))
    )
    val pins = listOf(
        TaskEntity(id = 9, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)),
        TaskEntity(id = 10, title = "Reunión con Ana", startAt = at(2026, 8, 27, 11, 0))
    )

    var ok = 0
    var total = 0
    fun check(label: String, cond: Boolean, detail: String) {
        total++
        if (cond) { ok++; println("OK   $label") } else { println("FAIL $label :: $detail") }
    }

    // GAPS sustantivo (consulta) ↔ infinitivo (título)
    check("G1 «pago de la luz»↔«Pagar luz»",
        ask("¿cuándo es el pago de la luz?", verbTitles).relatedTaskIds == listOf(1L), "")
    check("G2 «cena con los abuelos»↔«Cenar…»",
        ask("¿a qué hora es la cena con los abuelos?", verbTitles).relatedTaskIds == listOf(2L), "")
    check("G3 «llamada con el banco»↔«Llamar…»",
        ask("¿cuándo es la llamada con el banco?", verbTitles).relatedTaskIds == listOf(3L), "")
    check("G4 «compra del pan»↔«Comprar el pan»",
        ask("¿cuándo es la compra del pan?", verbTitles).relatedTaskIds == listOf(4L), "")

    // Dirección inversa: infinitivo (consulta) ↔ sustantivo (título)
    check("G5 «es pagar la luz»↔«Pago de la luz»",
        ask("¿cuándo es pagar la luz?", nounTitles).relatedTaskIds == listOf(5L), "")
    check("G6 «a qué hora es pagar la luz»↔«Pago de la luz»",
        ask("¿a qué hora es pagar la luz?", nounTitles).relatedTaskIds == listOf(5L), "")

    // ANTI-OVERREACH: paridad parcial o sin lado verbo NO casa
    check("A1 «visita al médico»✗«Visitar a Marta»",
        ask("¿cuándo es la visita al médico?", overreach).relatedTaskIds.isEmpty(), "")
    check("A2 «pago de la luz»✗«Pagar el médico»",
        ask("¿cuándo es el pago de la luz?", overreach).relatedTaskIds.isEmpty(), "")
    check("A3 «cena del martes»✗«Cena con Marta» (sin lado verbo)",
        ask("¿a qué hora es la cena del martes?", overreach).relatedTaskIds.isEmpty(), "")

    // PINS: subcadena literal intacta
    check("P1 «¿cuándo tengo la cita con el dentista?»",
        ask("¿cuándo tengo la cita con el dentista?", pins).relatedTaskIds == listOf(9L), "")
    check("P2 «¿a qué hora tengo la reunión?»",
        ask("¿a qué hora tengo la reunión?", pins).relatedTaskIds == listOf(10L), "")
    check("P3 pin inverso «cena de empresa»",
        ask("¿cuándo es la cena de empresa?", nounTitles).relatedTaskIds == listOf(6L), "")

    // Desambiguación honesta: dos candidatas con paridad se nombran
    val duo = listOf(
        TaskEntity(id = 1, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)),
        TaskEntity(id = 2, title = "Pagar agua", dueAt = at(2026, 9, 16, 12, 0))
    )
    val du = ask("¿cuándo es el pago?", duo)
    check("D1 dos candidatas → «Tienes varias…»",
        du.text.startsWith("Tienes varias") && "«Pagar luz»" in du.text && "«Pagar agua»" in du.text, du.text)

    println("== $ok/$total OK ==")
    if (ok != total) kotlin.system.exitProcess(1)
}

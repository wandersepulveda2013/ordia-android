import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.data.local.TaskEntity
import java.time.ZoneId
import java.time.ZonedDateTime

// Sonda POST persistida c.1109 — aguja de entity-lookup SIN relleno
// «es lo del/la» (pre-fix la familia buscaba «es lo del dentista» por
// SUBCADENA del título → «No encuentro nada que sea …» con la tarea EXISTENTE;
// 7/7 medidas con sonda efímera /tmp/probe1109/Probe.kt).
// Ejecutar: bash tools/run_probe.sh tools/probe/AssistantEntityNeedleFillerProbe.kt
fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val now = ZonedDateTime.of(2026, 8, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    val tasks = listOf(
        TaskEntity(id = 1, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)),
        TaskEntity(id = 2, title = "Reunión con Ana", startAt = at(2026, 8, 27, 11, 0)),
        TaskEntity(id = 3, title = "Cena de empresa", dueAt = at(2026, 8, 29, 21, 0)),
        TaskEntity(id = 4, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)),
        TaskEntity(id = 5, title = "Recoger lo de la tintorería", dueAt = at(2026, 8, 27, 12, 0)),
        TaskEntity(id = 6, title = "Lo del dentista", dueAt = at(2026, 8, 30, 9, 0)),
        TaskEntity(id = 7, title = "Tarea para mañana", dueAt = at(2026, 8, 27, 9, 0))
    )

    var ok = 0
    var total = 0
    fun check(label: String, cond: Boolean, detail: String) {
        total++
        if (cond) { ok++; println("OK   $label :: $detail") }
        else println("FAIL $label :: $detail")
    }

    // GAPS pre-fix (fixture SIN «Lo del dentista»: una sola candidata cada uno).
    val clean = tasks.filterNot { it.id == 6L }
    val gaps = listOf(
        Triple("¿cuándo es lo del dentista?", 1L, "relleno «es lo del »"),
        Triple("¿a qué hora es lo del dentista?", 1L, "relleno «es lo del » + hora"),
        Triple("¿cuándo es la reunión?", 2L, "relleno «es la »"),
        Triple("¿qué fecha es lo del dentista?", 1L, "marcador «que fecha »"),
        Triple("¿qué día es lo de la tintorería?", 5L, "relleno «lo de la »"),
        Triple("¿dónde es lo de la tintorería?", 5L, "marcador «donde es »"),
        Triple("¿cuándo es el pago de la luz?", 4L, "relleno «es el » (aguja «pago de la luz» vs título «Pagar luz»: no resuelve — pin honesto)")
    )
    for ((q, id, label) in gaps) {
        val a = AssistantEngine.answer(q, clean, emptyList(), emptyList(), now = now, zone = zone)
        if (label.startsWith("relleno «es el »")) {
            // Lateral fuera de alcance (paridad sustantivo/verbo): la aguja sale
            // limpia pero «Pagar luz» no contiene «pago de la luz»; se exige el
            // NO-mensaje honesto CON aguja limpia (sin relleno).
            check(label, a.relatedTaskIds.isEmpty() && "«pago de la luz»" in a.text && "«es " !in a.text,
                a.text.replace("\n", " ").take(100))
        } else {
            check(label, a.relatedTaskIds == listOf(id), a.text.replace("\n", " ").take(100))
        }
    }

    // Paridad: fixture con título «Lo del dentista» (id 6) SOLO — la aguja
    // limpia + el casefold limpio se siguen casando (pre-fix también casaba por
    // la aguja sucia; el corte simétrico preserva el vínculo).
    val loDelOnly = tasks.filter { it.id == 6L }
    val parity = AssistantEngine.answer("¿cuándo es lo del dentista?", loDelOnly, emptyList(), emptyList(), now = now, zone = zone)
    check("paridad «lo del» titulo+consulta", parity.relatedTaskIds == listOf(6L), parity.text.replace("\n", " ").take(100))
    // Con ambas candidatas presentes la respuesta honesta es desambiguación
    // (pre-existente): nombra las dos en vez de elegir a ciegas.
    val both = AssistantEngine.answer("¿cuándo es lo del dentista?", tasks, emptyList(), emptyList(), now = now, zone = zone)
    check("desambiguacion honesta 2 candidatas", "Tienes varias" in both.text, both.text.replace("\n", " ").take(100))

    // PINS (idénticos al pre-fix): routing y pares no se mueven.
    val pins = listOf(
        Pair("¿a qué hora tengo la reunión?", 2L),
        Pair("¿a qué hora es la cena de empresa?", 3L),
        Pair("¿cuándo pago la luz?", 4L),
        Pair("¿cuándo tengo la cita con el dentista?", 1L),
        Pair("¿dónde es la cena de empresa?", 3L)
    )
    for ((q, id) in pins) {
        val a = AssistantEngine.answer(q, clean, emptyList(), emptyList(), now = now, zone = zone)
        check("PIN $q", a.relatedTaskIds == listOf(id), a.text.replace("\n", " ").take(100))
    }

    // PIN routing: agenda y what-now no capturados por entity-lookup.
    val agenda = AssistantEngine.answer("¿qué tengo mañana?", clean, emptyList(), emptyList(), now = now, zone = zone)
    check("PIN agenda mañana", agenda.text.startsWith("Mañana:"), agenda.text.take(80))
    val whatNow = AssistantEngine.answer("¿qué hago ahora?", clean, emptyList(), emptyList(), now = now, zone = zone)
    check("PIN what-now", !whatNow.text.startsWith("No encuentro"), whatNow.text.take(80))

    // Aguja vacía: fallback de fecha más próxima (comportamiento preexistente;
    // evalúa tareas activas — la tintorería ya quedó "pasada" a las 12:00 del
    // día de `now`, así que la próxima con fecha es la cena del 29).
    val bare = AssistantEngine.answer("¿cuándo es?", clean, emptyList(), emptyList(), now = now, zone = zone)
    check("PIN «¿cuándo es?» fallback", bare.relatedTaskIds == listOf(3L), bare.text.take(100))

    println("== $ok/$total OK ==")
    if (ok != total) error("fallos en la sonda c.1109")
}

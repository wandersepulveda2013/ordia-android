package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * c.1112 — paridad sustantivo/verbo en entity-lookup (lateral (a) de c.1109).
 * Pre-fix (sonda efímera /tmp/probe1112/Probe.kt, 4/4 fallando): el usuario
 * pregunta con el SUSTANTIVO («¿cuándo es el pago de la luz?») y la tarea se
 * tituló con el INFINITIVO («Pagar luz») — la coincidencia por subcadena
 * exigía la forma literal y respondía «No encuentro nada…» con la tarea
 * EXISTENTE (mentira por omisión, P1 recuperación). El fix añade un fallback
 * POR TOKENS con paridad léxica determinista (raíces compartidas con sufijos
 * cerrados de infinitivo ar/er/ir y nominales o/a/os/as/es/s/ada/ida/cion/
 * miento), exigiendo que TODOS los tokens de contenido de la aguja casen y
 * que al menos un lado del par sea infinitivo (anti-overreach: «Marta» no
 * casa con «martes»). La vía de subcadena literal queda intacta (pins).
 */
class AssistantEngineNounVerbParityTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        ZonedDateTime.of(2026, 8, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun ask(q: String, tasks: List<TaskEntity>): AssistantAnswer =
        AssistantEngine.answer(q, tasks, emptyList(), emptyList(), now = now, zone = zone)

    // ---- GAPS medidos PRE (sonda /tmp/probe1112): sustantivo vs infinitivo ----

    @Test fun pagoDeLaLuz_resuelveTituloPagarLuz() {
        val tasks = listOf(TaskEntity(id = 1, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)))
        val answer = ask("¿cuándo es el pago de la luz?", tasks)
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("contesta la fecha: ${answer.text}", answer.text.startsWith("«Pagar luz»"))
    }

    @Test fun cenaConLosAbuelos_resuelveTituloCenar() {
        val tasks = listOf(TaskEntity(id = 2, title = "Cenar con los abuelos", dueAt = at(2026, 8, 29, 21, 0)))
        val answer = ask("¿a qué hora es la cena con los abuelos?", tasks)
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun llamadaConElBanco_resuelveTituloLlamar() {
        val tasks = listOf(TaskEntity(id = 3, title = "Llamar al banco", startAt = at(2026, 8, 27, 11, 0)))
        val answer = ask("¿cuándo es la llamada con el banco?", tasks)
        assertEquals(listOf(3L), answer.relatedTaskIds)
    }

    @Test fun compraDelPan_resuelveTituloComprar() {
        val tasks = listOf(TaskEntity(id = 4, title = "Comprar el pan", dueAt = at(2026, 8, 27, 9, 0)))
        val answer = ask("¿cuándo es la compra del pan?", tasks)
        assertEquals(listOf(4L), answer.relatedTaskIds)
    }

    // ---- Dirección inversa: consulta con infinitivo, título sustantivado ----

    @Test fun cuandoEsPagarLaLuz_resuelveTituloPagoDeLaLuz() {
        val tasks = listOf(TaskEntity(id = 5, title = "Pago de la luz", dueAt = at(2026, 9, 15, 12, 0)))
        val answer = ask("¿cuándo es pagar la luz?", tasks)
        assertEquals(listOf(5L), answer.relatedTaskIds)
    }

    @Test fun aQueHoraEsPagarLuz_resuelveTituloPagoDeLaLuz() {
        val tasks = listOf(TaskEntity(id = 5, title = "Pago de la luz", dueAt = at(2026, 9, 15, 12, 0)))
        val answer = ask("¿a qué hora es pagar la luz?", tasks)
        assertEquals(listOf(5L), answer.relatedTaskIds)
    }

    // ---- ANTI-OVERREACH: paridad parcial NO basta (todos los tokens exigidos) ----

    @Test fun visitaAlMedico_noCasaConVisitarAMarta() {
        val tasks = listOf(TaskEntity(id = 7, title = "Visitar a Marta", dueAt = at(2026, 8, 30, 10, 0)))
        val answer = ask("¿cuándo es la visita al médico?", tasks)
        assertTrue("no finge coincidencia: ${answer.text}", answer.relatedTaskIds.isEmpty())
    }

    @Test fun pagoDeLaLuz_noCasaConPagarElMedico() {
        val tasks = listOf(TaskEntity(id = 8, title = "Pagar el médico", dueAt = at(2026, 9, 1, 9, 0)))
        val answer = ask("¿cuándo es el pago de la luz?", tasks)
        assertTrue("no finge coincidencia: ${answer.text}", answer.relatedTaskIds.isEmpty())
    }

    // Sin infinitivo en NINGÚN lado no hay paridad léxica (anti-overreach de la
    // guarda «al menos un lado verbo»: «marta»/«martes» comparten raíz «mart»).
    @Test fun cenaConMarta_noCasaPorMartaMartes() {
        val tasks = listOf(TaskEntity(id = 9, title = "Cena con Marta", dueAt = at(2026, 8, 27, 21, 0)))
        val answer = ask("¿a qué hora es la cena del martes?", tasks)
        assertTrue("marta/martes sin verbo no casa: ${answer.text}", answer.relatedTaskIds.isEmpty())
    }

    // ---- Desambiguación honesta: dos títulos con paridad se NOMBRAN, no se elige ----

    @Test fun dosCandidatasConParidad_desambiguanHonestamente() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Pagar luz", dueAt = at(2026, 9, 15, 12, 0)),
            TaskEntity(id = 2, title = "Pagar agua", dueAt = at(2026, 9, 16, 12, 0))
        )
        val answer = ask("¿cuándo es el pago?", tasks)
        assertTrue("nombra ambas: ${answer.text}", answer.text.startsWith("Tienes varias"))
    }

    // ---- PINS: la vía de subcadena literal queda byte-idéntica ----

    @Test fun pin_cuandoPagoLaLuz_sigueResolviendo() {
        val tasks = listOf(TaskEntity(id = 5, title = "Pago de la luz", dueAt = at(2026, 9, 15, 12, 0)))
        val answer = ask("¿cuándo pago la luz?", tasks)
        assertEquals(listOf(5L), answer.relatedTaskIds)
    }

    @Test fun pin_cuandoEsLaCenaDeEmpresa_sigueResolviendo() {
        val tasks = listOf(TaskEntity(id = 6, title = "La cena de empresa", dueAt = at(2026, 8, 29, 21, 0)))
        val answer = ask("¿cuándo es la cena de empresa?", tasks)
        assertEquals(listOf(6L), answer.relatedTaskIds)
    }

    @Test fun pin_cuandoTengoLaCita_sigueResolviendo() {
        val tasks = listOf(TaskEntity(id = 9, title = "Cita con el dentista", dueAt = at(2026, 8, 28, 16, 0)))
        val answer = ask("¿cuándo tengo la cita con el dentista?", tasks)
        assertEquals(listOf(9L), answer.relatedTaskIds)
    }

    @Test fun pin_aQueHoraTengoLaReunion_sigueResolviendo() {
        val tasks = listOf(TaskEntity(id = 10, title = "Reunión con Ana", startAt = at(2026, 8, 27, 11, 0)))
        val answer = ask("¿a qué hora tengo la reunión?", tasks)
        assertEquals(listOf(10L), answer.relatedTaskIds)
    }
}

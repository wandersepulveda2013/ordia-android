package com.ordia.app.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del deduplicador contextual (ORD-024).
 *
 * Cubre la semántica de ventana de deduplicación: un intent debe poder
 * volver a sugerirse una vez expirada la ventana, sin esperar al purge
 * interno (que ocurre a 2× ventana). Antes del fix, [isDuplicate] usaba
 * solo `containsKey` tras `expireEntries` (purge a 2× ventana), así que un
 * intent visto hacía entre 1× y 2× ventana se reportaba como duplicado
 * cuando ya debía poder sugerirse de nuevo → ventana efectiva de 2 h en
 * lugar de 1 h, suprimiendo sugerencias el doble del tiempo previsto.
 */
class ContextDeduplicatorTest {

    private fun intent(title: String) = ContextIntent(
        id = "id-$title",
        kind = ContextIntentKind.TASK,
        title = title,
        confidence = 0.8f,
        source = ContextCaptureSource.NOTIFICATION
    )

    @Test
    fun isDuplicate_false_para_intento_nunca_visto() {
        val now = mutableListOf(1_000L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        assertFalse(dedup.isDuplicate(intent("Llamar a mamá")))
    }

    @Test
    fun isDuplicate_true_dentro_de_la_ventana() {
        val now = mutableListOf(0L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        dedup.markAsSeen(intent("Comprar pan"))
        now[0] = 500L // dentro de la ventana

        assertTrue(dedup.isDuplicate(intent("Comprar pan")))
    }

    @Test
    fun isDuplicate_false_justo_despues_de_la_ventana_antes_del_purge() {
        // Caso clave del bug: edad entre 1× y 2× ventana. expireEntries (purge a
        // 2×) NO lo elimina, pero por semántica ya debe poder sugerirse.
        val now = mutableListOf(0L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        dedup.markAsSeen(intent("Pagar la luz"))
        now[0] = 1_001L // 1 ms más allá de la ventana; muy por debajo de 2× (2_000)

        assertFalse(
            "Un intent visto hace más de la ventana ya no debe ser duplicado, " +
                "incluso antes de que expireEntries lo purgue (purge a 2× ventana).",
            dedup.isDuplicate(intent("Pagar la luz"))
        )
    }

    @Test
    fun isDuplicate_con_diferente_titulo_no_es_duplicado() {
        val now = mutableListOf(0L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        dedup.markAsSeen(intent("Comprar pan"))
        now[0] = 100L

        assertFalse(dedup.isDuplicate(intent("Comprar leche")))
    }

    @Test
    fun isDuplicate_normaliza_mayusculas_y_espacios() {
        val now = mutableListOf(0L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        dedup.markAsSeen(intent("Comprar   pan"))
        now[0] = 100L

        assertTrue(dedup.isDuplicate(intent("comprar pan")))
    }

    @Test
    fun isDuplicate_puede_sugerirse_de_nuevo_tras_markAsCompleted() {
        val now = mutableListOf(0L)
        val dedup = ContextDeduplicator(dedupWindowMs = 1_000L, clock = { now[0] })

        dedup.markAsSeen(intent("Enviar reporte"))
        // markAsCompleted adelanta la marca a ahora - ventana/2 → queda "medio expirado".
        dedup.markAsCompleted(intent("Enviar reporte"))
        // Avanzamos pasado el medio-vencimiento restante (ventana/2 + margen).
        now[0] = 600L // ahora - marca = 600 - (0 - 500) = 1100 > ventana(1000)

        assertFalse(dedup.isDuplicate(intent("Enviar reporte")))
    }
}

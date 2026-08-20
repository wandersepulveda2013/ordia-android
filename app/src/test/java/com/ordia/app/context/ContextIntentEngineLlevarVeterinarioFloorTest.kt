package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.747 (número provisional, confirmado al fetch pre-push final): forma
 * "llevar al perro al veterinario" (3/8 mascotas, sonda paralela
 * `FourthClassVerbDiscoveryProbe.kt` c.740, PRE NULL) — piso HOUSEHOLD
 * acotado a la forma completa mascota+destino (`perr[oa]s?` +
 * `veterinari[oa]s?`) sobre el verbo bivalente "llevar" (el coche/la
 * cuenta/a los niños al colegio; familia de pisos acotados
 * [HOUSEHOLD_PET_FLOOR] c.740 / [HOUSEHOLD_FEED_CAT_FLOOR] c.744) +
 * keywords "veterinario"/"veterinaria" (lockstep keyword↔piso).
 * Interop c.740/c.744: verbos disjuntos (sacar/alimentar/llevar) —
 * no hay solape.
 * Kind: HOUSEHOLD (salud de la mascota = cuidado de la mascota en el
 * hogar, misma deliberación c.740/c.744; TASK solo en envolvente c.613).
 */
class ContextIntentEngineLlevarVeterinarioFloorTest {

    @Test
    fun `captura llevar al perro al veterinario plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al perro al veterinario mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar al perro al veterinario", intent.title)
    }

    @Test
    fun `captura perra con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a la perra al veterinario el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a la perra al veterinario", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar al perro al veterinario", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar al perro al veterinario", intent.title)
    }

    @Test
    fun `captura posesivo y franja hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi perro al veterinario a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a mi perro al veterinario", intent.title)
    }

    @Test
    fun `captura veterinaria femenino`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los perros a la veterinaria hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a los perros a la veterinaria", intent.title)
    }

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar al perro al veterinario mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar al perro al veterinario mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé al perro al veterinario ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `llevar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no mascota no roba HOUSEHOLD`() {
        // Objeto no acotado: "el coche" no es la forma sondeada; el piso
        // se restringe a `perr[oa]s?` + destino veterinario (familia
        // control kind-drift c.728/c.731/c.744). Nota: esta frase captura
        // como ERRAND por vía PRE-EXISTENTE (piso llevar-objeto de ERRAND,
        // verificado pre-cambio) — aquí solo se exige que no robe
        // HOUSEHOLD.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el coche al taller mañana", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `destino no veterinario no roba HOUSEHOLD`() {
        // Destino inocuo y estable: "al parque" no es la forma sondeada;
        // el piso se acota al destino `veterinari[oa]s?` (anti-overreach:
        // "llevar al perro a pasear/al parque" sigue sin piso).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al perro al parque mañana", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame llevar al perro al veterinario": el piso HOUSEHOLD
        // se descarta vía imperativeIsWrapped (WRAPPABLE_PATTERNS +
        // HOUSEHOLD_FLOORS); el piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar al perro al veterinario", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar al perro al veterinario", intent.title)
    }
}

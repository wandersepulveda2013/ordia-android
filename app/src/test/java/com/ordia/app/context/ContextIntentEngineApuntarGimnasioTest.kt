package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1232: familia enroll-gimnasio (lateral (b) MEDIA ABIERTa de MI
 * auditoría c.1227 — clase TRIGÉSIMA deporte — y hermana histórica
 * «gimnasio-alta» b-ter clase XV). Sonda persistida
 * `tools/probe/ApuntarGimnasioProbe.kt` medida PRE sobre HEAD de entrada:
 * T1/T2/T5/T6 NULL (4/6), T3/T4 HIT NOTE (capture existente con kind
 * subóptimo), G1–G6 NULL, R1–R6 HIT. «Apuntar(se)» es BIVALENTE
 * (anotar/transitiva-personas NOTE c.714/c.856), así el piso se ACOTA al
 * objeto «gimnasio» (gate c.751: keyword-OBJETO «gimnasio» preexistente
 * en EXERCISE — CERO keywords nuevas). Lockstep DOS puntos (lección
 * c.616): piso de posición libre [EXERCISE_ENROLL_FLOOR] + plantilla
 * matchApuntarGimnasio en extractTitle (rama EXERCISE). Kind decidido:
 * EXERCISE (la inscripción deportiva es contenido de actividad física;
 * NOTE era captura accidental de un piso por verbo anotación). El desborde
 * sobre NOTE lo resuelve el desempate por orden del enum (EXERCISE se
 * declara antes). «Dar de alta el gimnasio» entra en el piso TASK
 * hermana c.1139/c.1206 (objeto alternado extendido; re-pines legítimos
 * en `ContextIntentEngineDarDeBajaTest` y
 * `ContextIntentEngineDarDeAltaSuministroFloorTest`, precedente
 * c.1033/c.1035). Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineApuntarGimnasioTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: enroll al gimnasio (EXERCISE) / alta (TASK) ---

    @Test
    fun apuntarmeAlGimnasioEnEnero_capturesExercise() {
        val intent = analyze("apuntarme al gimnasio en enero")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        // «en enero» no ancla fecha en extractDateTime: sobrevive en el
        // título como cola visible (información preservada, misma decisión
        // que «esta semana» del hermano ApuntarseFloorTest).
        assertEquals("Apuntarme al gimnasio en enero", intent.title)
    }

    @Test
    fun apuntarmeAlGimnasioElLunes_capturesExerciseWithDueAt() {
        val intent = analyze("apuntarme al gimnasio el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntarme al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun apuntarAlGimnasioManana_capturesExerciseWithDueAt() {
        val intent = analyze("apuntar al gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntar al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun apuntarseAlGimnasio_capturesExercise() {
        val intent = analyze("apuntarse al gimnasio este mes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntarse al gimnasio este mes", intent.title)
    }

    @Test
    fun apuntarmeAlGimnasioConAna_capturesExercise() {
        val intent = analyze("apuntarme al gimnasio con Ana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntarme al gimnasio con Ana", intent.title)
    }

    @Test
    fun acuseValeApuntarme_capturesExercise() {
        val intent = analyze("vale, apuntarme al gimnasio el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Apuntarme al gimnasio", intent.title)
    }

    @Test
    fun darDeAltaElGimnasio_capturesTask() {
        val intent = analyze("dar de alta el gimnasio esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta el gimnasio esta semana", intent.title)
    }

    // --- Guards (contratos anti-overreach) ---

    @Test
    fun negacionEnclitica_descartada() {
        assertNull(analyze("no apuntarme al gimnasio"))
    }

    @Test
    fun futuroEnclitico_descartado() {
        assertNull(analyze("no me apuntaré al gimnasio"))
    }

    @Test
    fun pretéritoEnclitico_descartado() {
        assertNull(analyze("me apunté al gimnasio ayer"))
    }

    @Test
    fun altaMedicoBivalente_descartada() {
        assertNull(analyze("dar de alta a un paciente"))
    }

    @Test
    fun declarativa_descartada() {
        assertNull(analyze("el gimnasio está cerrado"))
    }

    @Test
    fun figuradoApuntarseUnTanto_descartado() {
        assertNull(analyze("apuntarse un tanto en el partido"))
    }

    // --- Regresiones (HERMANAS intactas) ---

    @Test
    fun irAlGimnasio_sigueExercise() {
        val intent = analyze("ir al gimnasio el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun apuntarseAlCursoDeIngles_sigueNote() {
        val intent = analyze("apuntarse al curso de inglés")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
    }

    @Test
    fun darDeBajaElGimnasio_sigueTask() {
        val intent = analyze("dar de baja el gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun apuntarALosNinosAlFutbol_sigueNote() {
        val intent = analyze("apuntar a los niños al fútbol")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
    }

    @Test
    fun hacerYogaLosMartes_sigueExercise() {
        val intent = analyze("hacer yoga los martes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun darDeAltaLaLuz_sigueTask() {
        val intent = analyze("dar de alta la luz del piso nuevo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun apuntarseAYoga_sigueNote() {
        val intent = analyze("apuntarse a yoga el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
    }

    // --- Envolventes (TASK gobierna el verbo subordinado, policy c.613) ---

    @Test
    fun recuerdameApuntarmeAlGimnasio_gobiernaTask() {
        val intent = analyze("recuérdame apuntarme al gimnasio el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Apuntarme al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tengoQueApuntarmeAlGimnasio_gobiernaTask() {
        val intent = analyze("tengo que apuntarme al gimnasio en enero")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Apuntarme al gimnasio en enero", intent.title)
    }
}

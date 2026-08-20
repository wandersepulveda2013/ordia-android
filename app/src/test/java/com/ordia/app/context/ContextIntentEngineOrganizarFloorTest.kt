package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721c (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 3/19: "organizar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "organizar el armario hoy" se DESCARTABA
 * (analyze → NULL) por ausencia de piso (misma clase de raíz que
 * c.691…c.721b). Fix: piso "organizar" en [hasStrongTaskImperative]
 * (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige
 * objeto) + keyword "organizar" en TASK (paridad lockstep piso+keyword,
 * lección c.713) + plantilla de título "(organizar) X"→"Organizar X"
 * (patrón c.691…c.721b; lección c.616: el match arranca en el verbo).
 * Kind decidido: TASK, en deliberación contra HOUSEHOLD — "organizar" es un
 * verbo genérico de orden (armario/cajón/documentos/proyecto), la acción de
 * gestión sobre el objeto gobierna (criterio c.704 "arreglar").
 * Anti-overreach: objeto requerido, negada/duda/sustantivo
 * "organización"/past "organicé…"/suelto "organizar" NULL; envolvente c.613
 * gobierna TASK. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineOrganizarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "organizar <objeto>" es una gestión clara ---

    @Test
    fun organizarElArmarioHoy_capturesTaskWithDueAt() {
        val intent = analyze("organizar el armario hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Organizar el armario", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun organizarLosDocumentos_capturesTaskWithoutDueAt() {
        val intent = analyze("organizar los documentos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Organizar los documentos", intent.title)
    }

    @Test
    fun organizarTrasAcuse_capturesTask() {
        val intent = analyze("vale, organizar el cajón hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Organizar el cajón", intent.title)
    }

    @Test
    fun organizarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana organizar el proyecto")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Organizar el proyecto", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noOrganizarElArmario_negatedStaysNull() {
        assertNull(analyze("no organizar el armario"))
    }

    @Test
    fun quizasOrganizarElArmario_hedgeStaysNull() {
        assertNull(analyze("quizá organizar el armario hoy"))
    }

    @Test
    fun sustantivoOrganizacion_nounStaysNull() {
        assertNull(analyze("la organización del armario fue ayer"))
    }

    @Test
    fun organiceElArmario_pastStaysNull() {
        assertNull(analyze("organicé el armario ayer"))
    }

    @Test
    fun organizarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("organizar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameOrganizarElArmario_wrapperWinsTask() {
        val intent = analyze("recuérdame organizar el armario")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

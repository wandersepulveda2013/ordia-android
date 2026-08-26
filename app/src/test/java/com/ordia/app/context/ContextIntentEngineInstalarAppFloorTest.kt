package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1240 (P1 olvido silencioso en captura pasiva): «instalar la app» se
 * DESCARTABA (analyze → NULL) por ausencia de piso; candidata FUERTE de la
 * auditoría cl.XXXI tecnología (medida propia del descarte convergente
 * c.1236, sonda persistida `tools/probe/InstalarAppProbe.kt`: D1–D5 NULL,
 * G1–G5 NULL, R1–R5 HIT en PRE). Gate c.751: «instalar» monosemántico
 * (instalación de app/software; precedente c.752 votar / c.864 escanear /
 * c.1032 configurar / c.1036 formatear) → keyword-VERBO «instalar» en TASK
 * con piso acotado al objeto (la app/el software/la actualización quiere
 * ordenarse) — CERO keywords-OBJETO. Lockstep keyword + piso + plantilla
 * (lección c.616/c.713; doctrina c.653: ortografía preservada, solo
 * capitalización inicial). Anti-overreach: negada, pretérito, sustantivo
 * «instalación» y subjuntivo dudoso NULL. Determinista (regex).
 */
class ContextIntentEngineInstalarAppFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    @Test
    fun instalarLaAppMañana_capturesTask() {
        val intent = analyze("instalar la app de banca mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Instalar la app de banca", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun instalarLaAppSinTemporal_capturesTask() {
        val intent = analyze("instalar la app")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Instalar la app", intent.title)
    }

    @Test
    fun instalarTrasAcuse_capturesTask() {
        val intent = analyze("ok, instalar la app nueva por la noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Instalar la app nueva", intent.title)
    }

    @Test
    fun instalarElSoftware_capturesTask() {
        val intent = analyze("instalar el software de contabilidad el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Instalar el software de contabilidad", intent.title)
    }

    // --- Anti-overreach: sólo comanda de instalación con objeto app/software ---

    @Test
    fun negada_discarded() {
        assertNull(analyze("no instalar la app"))
    }

    @Test
    fun preterito_discarded() {
        assertNull(analyze("instalé la app ayer"))
    }

    @Test
    fun sustantivoInstalacion_discarded() {
        assertNull(analyze("la instalación de la app"))
    }

    @Test
    fun suelto_discarded() {
        assertNull(analyze("instalar"))
    }

    @Test
    fun subjuntivoDudoso_discarded() {
        assertNull(analyze("quizá instale la app"))
    }

    // --- Regresiones título/envolvente inmutado ---

    @Test
    fun regresionEnvolvente_unchanged() {
        val intent = analyze("recuérdame llamar a papá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a papá", intent.title)
    }

    @Test
    fun regresionActualizar_unchanged() {
        val intent = analyze("actualizar la app mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Actualizar la app", intent.title)
    }
}

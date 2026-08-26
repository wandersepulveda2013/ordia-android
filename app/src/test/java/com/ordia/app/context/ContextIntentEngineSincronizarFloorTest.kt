package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED→GREEN c.1238 (lateral (e) DÉBIL, FUERTE para piso acotado de
 * MI auditoría c.1236, clase XXXI tecnología): «sincronizar (los )
 * archivos/fotos/informes/documentos/drive». CERO keywords nuevas —
 * floor-only: «sincronizar» es monosemántico (gate c.751, precedente
 * c.752 «votar»).
 */
class ContextIntentEngineSincronizarFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )

    @Test
    fun `sincronizar archivos y fotos captura task`() {
        val r1 = analyze("sincronizar los archivos mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertTrue(r1.title.startsWith("Sincronizar los archivos"))

        val r2 = analyze("sincronizar fotos con el móvil")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Sincronizar fotos con el móvil", r2.title)

        val r3 = analyze("sincronizar el drive hoy")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
    }

    @Test
    fun `sincronizar documentos e informes captura task`() {
        val r1 = analyze("sincronizar los documentos")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Sincronizar los documentos", r1.title)

        val r2 = analyze("sincronizar los informes mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertTrue(r2.title.startsWith("Sincronizar los informes"))
    }

    @Test
    fun `guards correctos NULL`() {
        assertNull(analyze("no sincronizar los archivos"))
        assertNull(analyze("quizás sincronizar los documentos"))
        assertNull(analyze("la sincronización terminó"))
        assertNull(analyze("sincronicé los informes ayer"))
    }

    @Test
    fun `regresiones sincronizar existentes no se rompen`() {
        // hermandad piso c.1238 no aplica a texto común; «la sincronización»
        // no es imperativo de sincronizar-asíncrono; verifica también que
        // «sincronizar <objeto no acotado>» sigue NULL (pilot minimalista)
        assertNull(analyze("sincronizar con la pareja"))
        // reescanear/transcribir documento (DT-hermano) — regresión
        val r = analyze("escanear el informe mañana")
        assertNotNull(r)
    }
}

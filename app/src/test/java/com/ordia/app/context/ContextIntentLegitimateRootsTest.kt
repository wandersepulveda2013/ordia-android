package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression locks para el olvido de compromisos por moderación temática con
 * sentido técnico/legítimo (c.614, paridad con [IntelligenceSafetyGate]
 * c.578/582 y [ContentModeration] c.586).
 *
 * Antes de la corrección, [ContextIntentEngine.containsBlockedContent] usaba
 * una lista de raíces de moderación (matar, bomba, droga, secuestr…) sin
 * exenciones de contexto legítimo. Una notificación de sentido técnico
 * legítimo como "tengo que llamar al técnico para matar el proceso del
 * servidor" caía en la raíz `\bmatar\b`, se marcaba como contenido bloqueado
 * y `analyze()` devolvía `null` SILENCIOSAMENTE: el compromiso nunca llegaba
 * a Inbox (pérdida de captura, P1). Lo mismo ocurría con "llamar a la tienda
 * para comprar la bomba de agua" (`\bbomba\b`) y "llamar a la farmacia por la
 * droga recetada" (`\bdroga\b` sin proximity de farmacia).
 *
 * La corrección elimina el gate de moderación duplicado de `containsBlockedContent`
 * y delega a [com.ordia.app.domain.ContentModeration], que aplica exenciones de
 * contexto legítimo (cuerpo técnico, farmacia/proximidad médica, frases hechas
 * como "bomba de agua"), de forma paritaria con [IntelligenceSafetyGate]. Así
 * los compromisos legítimos con vocabulario ambiguo se capturan en lugar de
 * descartarse en silencio.
 *
 * Casos RED → GREEN: antes `analyze(...)` devolvía `null` para los tres
 * textos legítimos; ahora se capturan. c.653 (guard de envolvente extendido a
 * los bonus-kinds APPOINTMENT/CALL) reclasificó "tengo que llamar..." de
 * CALL a [ContextIntentKind.TASK]: el envolvente gobierna, el verbo
 * subordinado es su contenido. La garantía que protege este archivo (captura,
 * no bloqueo silencioso) se mantiene; sólo cambió el kind resultante.
 */
class ContextIntentLegitimateRootsTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    @Test
    fun wrappedCallWithKillProcessRootIsCapturedNotBlocked() {
        val intent = analyze("tengo que llamar al técnico para matar el proceso del servidor")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun wrappedCallWithWaterBombRootIsCapturedNotBlocked() {
        val intent = analyze("tengo que llamar a la tienda para comprar la bomba de agua")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun wrappedCallWithPrescriptionDrugRootIsCapturedNotBlocked() {
        val intent = analyze("tengo que llamar a la farmacia por la droga recetada")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

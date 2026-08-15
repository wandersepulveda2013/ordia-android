package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationObservationPolicyTest {
    private fun evaluate(
        globalEnabled: Boolean = true,
        notificationAccessEnabled: Boolean = true,
        sourceEnabled: Boolean = true,
        text: String = "Te envío el informe mañana",
        packageName: String = "com.whatsapp",
        alreadySeen: Boolean = false,
        pausedUntil: Long = 0L,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false
    ) = NotificationObservationPolicy.evaluate(
        globalEnabled = globalEnabled,
        notificationAccessEnabled = notificationAccessEnabled,
        pausedUntil = pausedUntil,
        sourceEnabled = sourceEnabled,
        packageName = packageName,
        notificationKey = "notification-42",
        title = "Ana",
        text = text,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
        alreadySeen = alreadySeen,
        now = 1_000L
    )

    @Test fun disabledObservationRejectsBeforeProcessing() {
        val decision = evaluate(globalEnabled = false)
        assertFalse(decision.accepted)
        assertEquals(ObservationRejection.DISABLED, decision.rejection)
    }

    @Test fun disabledNotificationAccessRejectsBeforeProcessing() {
        val decision = evaluate(notificationAccessEnabled = false)
        assertFalse(decision.accepted)
        assertEquals(ObservationRejection.DISABLED, decision.rejection)
    }

    @Test fun pausedObservationRejectsBeforeProcessing() {
        val decision = evaluate(pausedUntil = 2_000L)
        assertFalse(decision.accepted)
        assertEquals(ObservationRejection.PAUSED, decision.rejection)
    }

    @Test fun unauthorizedSourceIsRejected() {
        val decision = evaluate(sourceEnabled = false)
        assertFalse(decision.accepted)
        assertEquals(ObservationRejection.SOURCE_NOT_AUTHORIZED, decision.rejection)
    }

    @Test fun ongoingAndGroupSummaryNotificationsAreRejected() {
        assertEquals(ObservationRejection.SYSTEM_NOTIFICATION, evaluate(isOngoing = true).rejection)
        assertEquals(ObservationRejection.SYSTEM_NOTIFICATION, evaluate(isGroupSummary = true).rejection)
    }

    @Test fun bankingPackagesAndVerificationCodesAreRejected() {
        assertEquals(
            ObservationRejection.SENSITIVE_PACKAGE,
            evaluate(packageName = "com.example.mobilebank").rejection
        )
        assertEquals(
            ObservationRejection.SENSITIVE_CONTENT,
            evaluate(text = "Tu código de verificación es 482913").rejection
        )
    }

    @Test fun financialContentFromNonBankingPackagesIsRejected() {
        // Un SMS/mensaje bancario llega desde la app de mensajería (paquete no
        // bancario) y pasa el filtro de paquete; debe bloquearse por contenido.
        // Antes de c.286 este contenido escapaba al gate de notificaciones. (c.286)
        assertEquals(
            ObservationRejection.SENSITIVE_CONTENT,
            evaluate(packageName = "com.google.android.apps.messaging", text = "Tu saldo disponible es 45000 MXN").rejection
        )
        assertEquals(
            ObservationRejection.SENSITIVE_CONTENT,
            evaluate(packageName = "com.android.mms", text = "Estado de cuenta de tu tarjeta listo").rejection
        )
        assertEquals(
            ObservationRejection.SENSITIVE_CONTENT,
            evaluate(packageName = "com.whatsapp", text = "Mi frase semilla es uno dos tres").rejection
        )
    }

    @Test fun authorizedCommitmentIsAcceptedWithoutRetainingExtraWhitespace() {
        val decision = evaluate(text = "  Te envío   el informe mañana  ")
        assertTrue(decision.accepted)
        assertEquals("Te envío el informe mañana", decision.normalizedText)
        assertTrue(decision.fingerprint.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test fun persistentFingerprintMakesDuplicateDecisionExplicit() {
        val first = evaluate()
        val duplicate = evaluate(alreadySeen = true)
        assertTrue(first.accepted)
        assertFalse(duplicate.accepted)
        assertEquals(ObservationRejection.DUPLICATE, duplicate.rejection)
        assertEquals(
            first.fingerprint,
            NotificationObservationPolicy.fingerprint(
                "com.whatsapp",
                "notification-42",
                "Te envío el informe mañana"
            )
        )
    }
}

package com.ordia.app.updates

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Tests del canal HTTP directo de actualización.
 *
 * La pieza críticamente testeable sin Robolectric es la clasificación de fallos:
 * determina si el controller debe reintentar con DownloadManager (fallo de red) o
 * fallar en firme (fallo de validación: SHA/firma/tamaño). Una clasificación errónea
 * haría que una APK corrupta se reintentase indefinidamente, o que un fallo de red
 * transitorio bloquease la actualización sin fallback.
 */
class HttpUpdateChannelTest {

    @Test fun ioException_classifiedAsNetwork_forDownloadManagerFallback() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(IOException("Connection timed out"))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.NETWORK, cls)
    }

    @Test fun githubHttpStatusFailure_classifiedAsNetwork() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(RuntimeException("GitHub respondió 404."))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.NETWORK, cls)
    }

    @Test fun tooManyRedirects_classifiedAsNetwork() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(RuntimeException("GitHub devolvió demasiadas redirecciones."))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.NETWORK, cls)
    }

    @Test fun untrustedHost_classifiedAsNetwork_notValidation() {
        // Un host no confiable es de seguridad, pero el controller de todas formas no
        // debe reintentar con DownloadManager esperando otro resultado: clasificarlo
        // como NETWORK hace que el fallback se intente una vez y luego falle limpio.
        val cls = OrdiaUpdateManager.classifyHttpFailure(RuntimeException("URL de actualización no confiable."))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.NETWORK, cls)
    }

    @Test fun signatureMismatch_classifiedAsSignature_notRetried() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(RuntimeException("SIGNATURE_MISMATCH"))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.SIGNATURE, cls)
    }

    @Test fun shaMismatch_classifiedAsValidation_notRetriedWithDownloadManager() {
        // Un fallo de SHA significa que los bytes descargados no coinciden con el
        // manifiesto: reintentar con DownloadManager devolvería los mismos bytes,
        // así que debe ser VALIDATION (fallo en firme), no NETWORK.
        val cls = OrdiaUpdateManager.classifyHttpFailure(
            IllegalStateException("El hash SHA-256 no coincide con el esperado.")
        )
        assertEquals(OrdiaUpdateManager.HttpFailureClass.VALIDATION, cls)
    }

    @Test fun sizeMismatch_classifiedAsValidation_notRetried() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(
            IllegalArgumentException("Tamaño descargado 1000, esperado 2000")
        )
        assertEquals(OrdiaUpdateManager.HttpFailureClass.VALIDATION, cls)
    }

    @Test fun emptyApk_classifiedAsValidation() {
        val cls = OrdiaUpdateManager.classifyHttpFailure(RuntimeException("La APK descargada está vacía."))
        assertEquals(OrdiaUpdateManager.HttpFailureClass.VALIDATION, cls)
    }
}

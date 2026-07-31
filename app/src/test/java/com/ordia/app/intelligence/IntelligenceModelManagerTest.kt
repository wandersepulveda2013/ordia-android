package com.ordia.app.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la configuración de descarga de modelos.
 *
 * Cubren la configuración que alimenta al worker de descarga real
 * (ModelDownloadWorker): URLs https, checksum presente, tamaños dentro del
 * límite de seguridad MAX_MODEL_BYTES y unicidad de archivos.
 */
class IntelligenceModelManagerTest {

    private val models = IntelligenceModelManager.getAllModels()

    @Test
    fun atLeastTwoModelsAreRegistered() {
        assertTrue("Deben existir los perfiles LIGERO y MEJOR_COMPRENSION", models.size >= 2)
    }

    @Test
    fun everyModelHasHttpsDownloadUrl() {
        models.forEach { model ->
            assertTrue(
                "URL de descarga debe ser https: ${model.downloadUrl}",
                model.downloadUrl.startsWith("https://")
            )
        }
    }

    @Test
    fun everyModelHasChecksumUrl() {
        models.forEach { model ->
            assertNotNull("Checksum URL requerida para ${model.filename}", model.checksumUrl)
            assertTrue(model.checksumUrl.startsWith("https://"))
        }
    }

    @Test
    fun everyModelFitsWithinMaxModelBytes() {
        // El tope MAX_MODEL_BYTES protege contra servidores comprometidos;
        // ningún modelo legítimo debe superarlo.
        models.forEach { model ->
            assertTrue(
                "${model.filename}: ${model.expectedSizeBytes} bytes > tope de seguridad",
                model.expectedSizeBytes < IntelligenceModelManager.MAX_MODEL_BYTES
            )
        }
    }

    @Test
    fun everyModelHasPositiveExpectedSize() {
        models.forEach { model ->
            assertTrue("Tamaño esperado debe ser positivo", model.expectedSizeBytes > 0)
        }
    }

    @Test
    fun filenamesAreUnique() {
        val names = models.map { it.filename }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun getModelReturnsRegisteredMetadata() {
        assertNotNull(IntelligenceModelManager.getModel("gemma3-1b-it-q4.tflite"))
    }

    @Test
    fun getModelReturnsNullForUnknownFile() {
        assertNull(IntelligenceModelManager.getModel("no-existe.tflite"))
    }

    @Test
    fun ligeroAndMejorComprensionProfilesMapToRegisteredModels() {
        val ligero = LocalModelProvider.ModelProfile.LIGERO
        val mejor = LocalModelProvider.ModelProfile.MEJOR_COMPRENSION
        assertNotNull(IntelligenceModelManager.getModel(ligero.modelFile))
        assertNotNull(IntelligenceModelManager.getModel(mejor.modelFile))
    }
}

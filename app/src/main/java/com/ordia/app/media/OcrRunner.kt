package com.ordia.app.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reconocimiento de texto (OCR) on-device vía ML Kit.
 *
 * Usamos reflexión para no acoplar el código común a la dependencia de ML Kit,
 * que solo está presente en previewAdvanced/previewFull. En previewSafe,
 * [isAvailable] es false y [recognize] devuelve [OcrResult.Unavailable].
 *
 * 100% offline: el modelo latino va empaquetado en el APK (sin descarga).
 */
object OcrRunner {
    private const val TAG = "OcrRunner"

    sealed class OcrResult {
        data class Success(val text: String) : OcrResult()
        object Empty : OcrResult()
        object Unavailable : OcrResult()
        data class Failed(val message: String) : OcrResult()
    }

    /** ¿La dependencia de ML Kit está presente en el classpath? */
    val isAvailable: Boolean by lazy {
        runCatching { Class.forName("com.google.mlkit.vision.text.TextRecognition") }.isSuccess
    }

    suspend fun recognize(context: Context, bitmap: Bitmap): OcrResult {
        if (!isAvailable) return OcrResult.Unavailable
        return suspendCancellableCoroutine { cont ->
            runCatching {
                val textClass = Class.forName("com.google.mlkit.vision.text.Text")
                val recognizerClass = Class.forName("com.google.mlkit.vision.text.TextRecognizer")
                val textRecognitionClass = Class.forName("com.google.mlkit.vision.text.TextRecognition")
                // TextRecognition.getClient(LatinTextRecognizerOptions.DEFAULT_OPTIONS)
                val latinClass = Class.forName("com.google.mlkit.vision.text.latin.LatinTextRecognizerOptions")
                val defaultOpts = latinClass.getField("DEFAULT_OPTIONS").get(null)
                val getClient = textRecognitionClass.getMethod("getClient", defaultOpts::class.java)
                val recognizer = getClient.invoke(null, defaultOpts)

                // InputImage.fromBitmap(bitmap, 0)
                val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
                val fromBitmap = inputImageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.javaPrimitiveType)
                val image = fromBitmap.invoke(null, bitmap, 0)

                // recognizer.process(image).addOnSuccessListener { }.addOnFailureListener { }
                val task = recognizer.javaClass.getMethod("process", inputImageClass).invoke(recognizer, image)
                val taskClass = task.javaClass
                val onSuccess = taskClass.getMethod("addOnSuccessListener", Class.forName("com.google.android.gms.tasks.OnSuccessListener"))
                val onFailure = taskClass.getMethod("addOnFailureListener", Class.forName("com.google.android.gms.tasks.OnFailureListener"))

                val successListener = object {
                    @Suppress("unused")
                    fun onSuccess(result: Any?) {
                        val text = extractText(result, textClass)
                        if (cont.isActive) {
                            cont.resume(if (text.isBlank()) OcrResult.Empty else OcrResult.Success(text))
                        }
                    }
                }
                val failureListener = object {
                    @Suppress("unused")
                    fun onFailure(e: Exception) {
                        if (cont.isActive) cont.resume(OcrResult.Failed(e.message ?: "OCR failed"))
                    }
                }

                // Wrap into the expected listener interfaces via Proxy.
                val listenerClass = Class.forName("com.google.android.gms.tasks.OnSuccessListener")
                val failClass = Class.forName("com.google.android.gms.tasks.OnFailureListener")
                val successProxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader, arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onSuccess" && args != null && args.isNotEmpty()) {
                        successListener.onSuccess(args[0])
                    }
                    null
                }
                val failureProxy = java.lang.reflect.Proxy.newProxyInstance(
                    failClass.classLoader, arrayOf(failClass)
                ) { _, method, args ->
                    if (method.name == "onFailure" && args != null && args.isNotEmpty()) {
                        failureListener.onFailure(args[0] as Exception)
                    }
                    null
                }
                onSuccess.invoke(task, successProxy)
                onFailure.invoke(task, failureProxy)
            }.onFailure {
                Log.w(TAG, "recognize failed", it)
                if (cont.isActive) cont.resume(OcrResult.Failed(it.message ?: "OCR error"))
            }
        }
    }

    /** Extrae texto del resultado ML Kit Text por reflexión. */
    private fun extractText(result: Any?, textClass: Class<*>): String {
        if (result == null) return ""
        return runCatching {
            // text.getText()
            val getText = textClass.getMethod("getText")
            getText.invoke(result) as? String ?: ""
        }.getOrDefault("")
    }
}

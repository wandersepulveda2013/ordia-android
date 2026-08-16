package com.ordia.app.intelligence

/** Estado público y verificable del modelo generativo en esta versión. */
object GenerativeModelStatus {
    const val IS_AVAILABLE = false
    const val UNAVAILABLE_REASON =
        "El modelo generativo local no está integrado. Ordía usa reglas deterministas " +
            "en el dispositivo y no ofrece descargas que todavía no pueda ejecutar."
}

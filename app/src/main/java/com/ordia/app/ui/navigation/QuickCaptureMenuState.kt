package com.ordia.app.ui.navigation

/** Eventos que pueden cambiar el menú de captura; se mantienen puros para probar
 * toque, retroceso, Escape, toque exterior y navegación sin depender de Compose. */
enum class QuickCaptureMenuEvent { TOGGLE, OUTSIDE, BACK, ESCAPE, NAVIGATE }

object QuickCaptureMenuState {
    fun reduce(open: Boolean, event: QuickCaptureMenuEvent): Boolean = when (event) {
        QuickCaptureMenuEvent.TOGGLE -> !open
        QuickCaptureMenuEvent.OUTSIDE,
        QuickCaptureMenuEvent.BACK,
        QuickCaptureMenuEvent.ESCAPE,
        QuickCaptureMenuEvent.NAVIGATE -> false
    }
}

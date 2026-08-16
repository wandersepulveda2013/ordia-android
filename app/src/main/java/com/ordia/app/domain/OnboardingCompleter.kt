package com.ordia.app.domain

/**
 * Política de finalización del onboarding (pura, testeable en JVM).
 *
 * - Bloquea dobles disparos: mientras una escritura está en curso, [run] devuelve false
 *   y no vuelve a persistir (evita dobles toques en "Entrar a Ordia").
 * - Devuelve true únicamente cuando la persistencia terminó con éxito.
 * - Si la escritura falla, `busy` vuelve a false y el usuario puede reintentar sin
 *   quedar atrapado en la pantalla de selección de modo.
 */
class OnboardingCompleter(
    private val persist: suspend () -> Unit
) {
    var busy: Boolean = false
        private set

    suspend fun run(): Boolean {
        if (busy) return false
        busy = true
        return try {
            persist()
            true
        } catch (t: Throwable) {
            false
        } finally {
            busy = false
        }
    }
}

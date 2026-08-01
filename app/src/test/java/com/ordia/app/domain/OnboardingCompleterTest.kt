package com.ordia.app.domain

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del bloqueo de acceso: la finalización del onboarding debe persistir
 * exactamente una vez, bloquear dobles toques y permitir reintentar tras un error
 * de escritura (nunca dejar al usuario atrapado en Simple/Medio/Avanzado).
 */
class OnboardingCompleterTest {

    @Test
    fun success_persistsOnce_andResetsBusy() = runBlocking {
        var persists = 0
        val completer = OnboardingCompleter { persists++ }
        assertTrue("debe devolver true cuando la escritura terminó", completer.run())
        assertFalse("busy debe quedar libre tras terminar", completer.busy)
        assertEquals("debe persistir exactamente una vez", 1, persists)
    }

    @Test
    fun secondCallWhileBusy_isRejectedWithoutPersisting() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var persists = 0
        val completer = OnboardingCompleter {
            persists++
            gate.await()
        }
        val first = async { completer.run() }
        while (!completer.busy) yield()
        val second = completer.run()
        assertFalse("un segundo toque mientras se guarda no debe dispararse", second)
        assertEquals("solo la primera pulsación debe persistir", 1, persists)
        gate.complete(Unit)
        assertTrue(first.await())
    }

    @Test
    fun writeError_returnsFalse_andAllowsRetry() = runBlocking {
        var fail = true
        var persists = 0
        val completer = OnboardingCompleter {
            persists++
            if (fail) throw IOException("fallo de escritura simulado")
        }
        assertFalse("un fallo de escritura no debe quedar silencioso", completer.run())
        assertFalse("busy debe liberarse para permitir reintentar", completer.busy)
        assertEquals(1, persists)
        fail = false
        assertTrue("tras el error se debe poder reintentar", completer.run())
        assertEquals(2, persists)
    }

    @Test
    fun sequentialCallsAfterSuccess_areAllowed() = runBlocking {
        var persists = 0
        val completer = OnboardingCompleter { persists++ }
        assertTrue(completer.run())
        assertTrue("una segunda llamada después de terminar no está bloqueada", completer.run())
        assertEquals(2, persists)
    }

    @Test
    fun neverLeavesBusyLocked_whenPersistThrows() = runBlocking {
        val completer = OnboardingCompleter { throw IllegalStateException("cualquier error") }
        repeat(3) { assertFalse(completer.run()) }
        assertFalse("busy nunca debe quedar bloqueado tras errores repetidos", completer.busy)
    }
}

package com.ordia.app.domain

import com.ordia.app.data.preferences.InterfaceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del modo de interfaz: los tres modos (Simple, Medio, Avanzado) deben
 * seguir siendo seleccionables y una preferencia ausente o corrupta debe resolverse
 * a un valor seguro que siempre permita continuar el onboarding.
 */
class InterfaceModeResolverTest {

    @Test
    fun knownNames_resolveToSameMode() {
        assertEquals(InterfaceMode.SIMPLE, resolveInterfaceMode("SIMPLE"))
        assertEquals(InterfaceMode.ORGANIZED, resolveInterfaceMode("ORGANIZED"))
        assertEquals(InterfaceMode.ADVANCED, resolveInterfaceMode("ADVANCED"))
    }

    @Test
    fun absentValue_fallsBackToOrganized() {
        assertEquals(InterfaceMode.ORGANIZED, resolveInterfaceMode(null))
    }

    @Test
    fun unknownValue_fallsBackToOrganized() {
        assertEquals("valor inventado no debe bloquear", InterfaceMode.ORGANIZED, resolveInterfaceMode("MEGA"))
        assertEquals("el emparejamiento es por nombre exacto", InterfaceMode.ORGANIZED, resolveInterfaceMode("simple"))
    }

    @Test
    fun allThreeModes_remainSelectable() {
        assertEquals(3, InterfaceMode.entries.size)
        assertTrue(
            InterfaceMode.entries.containsAll(
                listOf(InterfaceMode.SIMPLE, InterfaceMode.ORGANIZED, InterfaceMode.ADVANCED)
            )
        )
    }
}

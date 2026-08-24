package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Franja blanda con conector «en» (c.960): «avisar en la mañana» dejaba
 * 'Avisar en la' en el título — el bareRelative cortaba «mañana» y el
 * conector «en la» quedaba huérfano (medida con probe `BandProbe`).
 * La plural «en las mañanas» además no entraba ni siquiera en la familia.
 * El fix extiende `bandTail` con alternancia `(?:por|en)` simétrica al
 * introductor «por» de c.688.
 */
class ContextIntentEngineEnLaBandaResidueTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                text,
                System.currentTimeMillis()
            )
        )

    @Test
    fun tituloSinResiduo_enLaManana() {
        val i = analyze("avisar en la mañana")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_enLasMananas() {
        val i = analyze("avisar en las mañanas")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_enLaTarde() {
        val i = analyze("avisar en la tarde")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_enLasTardes() {
        val i = analyze("avisar en las tardes")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_enLaNoche() {
        val i = analyze("avisar en la noche")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_enLasNoches() {
        val i = analyze("avisar en las noches")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    // Guards anti-overreach: el conector «en la» seguido de contenido no
    // se toca cuando no es franja temporal.
    @Test
    fun contenido_enLaEntrada_tituloIntegro() {
        val i = analyze("avisar en la entrada")
        assertNotNull(i)
        assertEquals("Avisar en la entrada", i!!.title)
    }

    @Test
    fun contenido_enLasMontanas_tituloIntegro() {
        val i = analyze("avisar a papá en las montañas")
        assertNotNull(i)
        assertEquals(i!!.title, "Avisar a papá en las montañas")
    }
}

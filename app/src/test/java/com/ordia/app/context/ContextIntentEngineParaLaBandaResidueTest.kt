package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Franja blanda con conector «para» (c.962): «avisar para la mañana» dejaba
 * 'Avisar para la' en el título — el bareRelative cortaba «mañana» y el
 * conector «para la» quedaba huérfano (medida con probe efímerico). La
 * plural «para las mañanas» y las síngulares «para la tarde/noche» no se
 * despojaban en absoluto. El fix extiende la alternancia de `bandTail` a
 * `(?:por|en|para)`, simétrica a los introductores «por» (c.688) y «en»
 * (c.960). Guards: «para la/las + contenido» (entrada, personas, casa)
 * nunca se toca — el ancla de franja exige mañana/tarde/noche.
 */
class ContextIntentEngineParaLaBandaResidueTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                text,
                System.currentTimeMillis()
            )
        )

    @Test
    fun tituloSinResiduo_paraLaManana() {
        val i = analyze("avisar para la mañana")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_paraLasMananas() {
        val i = analyze("avisar para las mañanas")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_paraLaTarde() {
        val i = analyze("avisar para la tarde")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_paraLasTardes() {
        val i = analyze("avisar para las tardes")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_paraLaNoche() {
        val i = analyze("avisar para la noche")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    @Test
    fun tituloSinResiduo_paraLasNoches() {
        val i = analyze("avisar para las noches")
        assertNotNull(i)
        assertEquals("Avisar", i!!.title)
    }

    // Guards anti-overreach: el conector «para la/las» seguido de contenido
    // no se toca cuando no es franja temporal.
    @Test
    fun contenido_paraLaEntrada_tituloIntegro() {
        val i = analyze("avisar a papá para la entrada")
        assertNotNull(i)
        assertEquals("Avisar a papá para la entrada", i!!.title)
    }

    @Test
    fun contenido_paraLasPersonas_tituloIntegro() {
        val i = analyze("avisar para las personas mayores")
        assertNotNull(i)
        assertEquals("Avisar para las personas mayores", i!!.title)
    }
}

package com.ordia.app.context

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression locks for [ContextIntentEngine.extractDateTime].
 *
 * Antes de la corrección, los regex de fecha y hora trataban cualquier número
 * suelto del texto como día del mes / hora: frases cotidianas como
 * "comprar 2 kilos de arroz" recibían un dueAt espurio (día 2 / hora 02:00),
 * asignando fechas de vencimiento inventadas a tareas que no las mencionan.
 *
 * La corrección exige una pista temporal explícita (prefijo "el" o nombre de
 * mes para la fecha; "a las"/"HH:MM"/am|pm|mañana|tarde|noche para la hora).
 */
class ContextIntentEngineDateTimeTest {

    // --- Falsos positivos eliminados: número suelto sin pista temporal ---

    @Test
    fun bareQuantityDoesNotProduceDate() {
        assertNull(
            "una cantidad numérica sin 'el'/'de <mes>' no debe generar fecha",
            ContextIntentEngine.extractDateTime("comprar 2 kilos de arroz")
        )
    }

    @Test
    fun bareNumberAloneDoesNotProduceTime() {
        assertNull(
            "un número suelto sin 'a las'/dos puntos/am-pm no debe generar hora",
            ContextIntentEngine.extractDateTime("pedir 3 tacos")
        )
    }

    @Test
    fun bareNumberInLongerSentenceDoesNotProduceDate() {
        assertNull(
            "números sueltos en frases largas no deben generar fecha",
            ContextIntentEngine.extractDateTime("llamar a soporte 15 veces esta semana")
        )
    }

    // --- Fechas válidas que deben seguir detectándose ---

    @Test
    fun elDayNoMonthProducesDate() {
        assertNotNull(
            "el 25 debe generar fecha",
            ContextIntentEngine.extractDateTime("reunión el 25")
        )
    }

    @Test
    fun elDayOfMonthProducesDate() {
        assertNotNull(
            "el 25 de mayo debe generar fecha",
            ContextIntentEngine.extractDateTime("el 25 de mayo")
        )
    }

    @Test
    fun dayOfMonthWithoutElProducesDate() {
        assertNotNull(
            "25 de mayo debe generar fecha (mes explícito)",
            ContextIntentEngine.extractDateTime("para el examen 25 de mayo")
        )
    }

    // --- Horas válidas que deben seguir detectándose ---

    @Test
    fun aLasProducesTime() {
        assertNotNull(
            "el 25 a las 3 debe generar fecha con hora",
            ContextIntentEngine.extractDateTime("reunión el 25 a las 3")
        )
    }

    @Test
    fun colonTimeProducesTime() {
        assertNotNull(
            "el 25 15:30 debe generar fecha con hora",
            ContextIntentEngine.extractDateTime("reunión el 25 15:30")
        )
    }

    @Test
    fun pmSuffixProducesTime() {
        assertNotNull(
            "el 25 3 pm debe generar fecha con hora",
            ContextIntentEngine.extractDateTime("reunión el 25 3 pm")
        )
    }

    @Test
    fun tardeSuffixProducesTime() {
        assertNotNull(
            "el 25 3 de la tarde debe generar fecha con hora",
            ContextIntentEngine.extractDateTime("reunión el 25 3 de la tarde")
        )
    }
}

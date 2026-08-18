package com.ordia.app.context

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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

    // --- "de/por/en la mañana" es hora del día, NO "mañana" = día siguiente ---
    // Regresión c.<run>: "reunión a las 9 de la mañana" se fechaba para MAÑANA
    // porque el sufijo "mañana" colisionaba con la regla de día siguiente.
    // Las aserciones son RELATIVAS (comparan dos llamadas con el mismo "today"
    // del motor) para no depender de la zona horaria del sistema.

    @Test
    fun deLaManana_isToday_notTomorrow() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 de la mañana")
        val hoy = ContextIntentEngine.extractDateTime("reunión hoy a las 9")
        assertNotNull("'de la mañana' debe extraer fecha/hora", due)
        assertNotNull("'hoy a las 9' debe extraer fecha/hora", hoy)
        val z = ZoneId.systemDefault()
        val dueDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val hoyDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(hoy!!), z).toLocalDate()
        assertEquals(
            "'de la mañana' es hora del día (hoy), no 'mañana' = día siguiente",
            hoyDate, dueDate
        )
    }

    @Test
    fun deLaManana_isOneDayBefore_manana() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 de la mañana")
        val manana = ContextIntentEngine.extractDateTime("reunión mañana a las 9")
        assertNotNull(due); assertNotNull(manana)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(manana!!), z).toLocalDate()
        assertEquals(
            "'de la mañana' (hoy) debe ser un día antes que 'mañana' real",
            1L, ChronoUnit.DAYS.between(dDue, dMan)
        )
    }

    @Test
    fun porLaManana_isToday_notTomorrow() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 por la mañana")
        val hoy = ContextIntentEngine.extractDateTime("reunión hoy a las 9")
        assertNotNull(due); assertNotNull(hoy)
        val z = ZoneId.systemDefault()
        val dueDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val hoyDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(hoy!!), z).toLocalDate()
        assertEquals(hoyDate, dueDate)
    }

    @Test
    fun enLaManana_isToday_notTomorrow() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 en la mañana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dueDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals(today, dueDate)
    }

    @Test
    fun deLaManana_hourIs9() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 de la mañana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'9 de la mañana' debe ser 09:00", 9, hour)
    }

    @Test
    fun bareManana_stillMeansTomorrow() {
        // La corrección no debe romper el sentido real de "mañana" = día siguiente.
        val manana = ContextIntentEngine.extractDateTime("reunión mañana")
        assertNotNull(manana)
        val z = ZoneId.systemDefault()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(manana!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'mañana' sin hora del día sigue siendo día siguiente", 1L,
            ChronoUnit.DAYS.between(today, dMan))
    }

    @Test
    fun paraManana_stillMeansTomorrow() {
        // "para mañana" no contiene ningún sufijo de hora del día → sigue siendo mañana.
        val manana = ContextIntentEngine.extractDateTime("entregar informe para mañana")
        assertNotNull(manana)
        val z = ZoneId.systemDefault()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(manana!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals(1L, ChronoUnit.DAYS.between(today, dMan))
    }

    @Test
    fun desdeManana_stillMeansTomorrow() {
        // "desde mañana" no debe verse afectado: el límite de palabra evita
        // confundir el "de" final de "desde" con el prefijo "de" de "de la mañana".
        val manana = ContextIntentEngine.extractDateTime("reuniones desde mañana")
        assertNotNull(manana)
        val z = ZoneId.systemDefault()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(manana!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'desde mañana' debe ser día siguiente", 1L,
            ChronoUnit.DAYS.between(today, dMan))
    }

    @Test
    fun pasadoManana_isDayAfterTomorrow() {
        val pm = ContextIntentEngine.extractDateTime("reunión pasado mañana")
        assertNotNull(pm)
        val z = ZoneId.systemDefault()
        val dPm = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pm!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals(2L, ChronoUnit.DAYS.between(today, dPm))
    }
}

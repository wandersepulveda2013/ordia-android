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

    // --- "12 de la noche" = medianoche (00:00), NO mediodía (12:00) ---
    // Regresión c.<run>: el sufijo "de la noche" se trataba como PM genérico, así
    // que hour=12 NO se ajustaba (la rama PM sólo suma 12 si hour<12) → quedaba
    // 12:00 (mediodía) en vez de 00:00 (medianoche). Un recordatorio "a las 12 de
    // la noche" se disparaba 12 h antes (al mediodía): IA deshonesta + olvido.
    // Paridad con NaturalTaskParser (línea `part == "noche" && h == 12 -> 0`).
    @Test
    fun doceDeLaNoche_esMedianoche_noMediodia() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 12 de la noche")
        assertNotNull("'12 de la noche' debe extraer fecha/hora", due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'12 de la noche' = medianoche (00:00), no mediodía (12:00)", 0, hour)
    }

    @Test
    fun doceDeLaTarde_esMediodia_noMedianoche() {
        // No-regresión: "12 de la tarde" SÍ es mediodía (12:00), simétrico a la
        // rama `part == "tarde" && h == 12 -> 12` del parser.
        val due = ContextIntentEngine.extractDateTime("reunión a las 12 de la tarde")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'12 de la tarde' = mediodía (12:00)", 12, hour)
    }

    @Test
    fun docePm_esMediodia_noMedianoche() {
        // No-regresión: "12 pm" (sin "noche") sigue siendo mediodía.
        val due = ContextIntentEngine.extractDateTime("reunión el 25 a las 12 pm")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'12 pm' = mediodía (12:00)", 12, hour)
    }

    @Test
    fun doceDeLaMadrugada_esMedianoche_noMediodia() {
        // Paridad con NaturalTaskParser: "12 de la madrugada" = medianoche (00:00).
        // El parser lo resuelve vía "de la madrugada" = meridiano AM → hour==12 → 0.
        val due = ContextIntentEngine.extractDateTime("reunión a las 12 de la madrugada")
        assertNotNull("'12 de la madrugada' debe extraer fecha/hora", due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'12 de la madrugada' = medianoche (00:00), no mediodía (12:00)", 0, hour)
    }

    @Test
    fun tresDeLaMadrugada_esTresAm_noQuince() {
        // No-regresión: "3 de la madrugada" = 03:00 (AM), NO 15:00 (PM).
        // La madrugada es siempre AM (pre-amanecer); el sufijo debe rutear a la rama AM.
        val due = ContextIntentEngine.extractDateTime("reunión a las 3 de la madrugada")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'3 de la madrugada' = 03:00 (AM)", 3, hour)
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

    // --- Noche / Tarde: sufijo de hora del día vs. señal de fecha ---
    // A diferencia de "mañana" (donde el sufijo horario "de la mañana" colisionaba
    // con el adverbio de día siguiente "mañana"), aquí la señal de fecha es la
    // secuencia literal "esta noche"/"esta tarde" y el sufijo de meridiano es
    // "de la noche"/"de la tarde": NO comparten un único disparador, por lo que
    // no hay colisión. Estos tests fijan ese comportamiento correcto.

    @Test
    fun deLaNoche_isToday_and21h() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 de la noche")
        val hoy = ContextIntentEngine.extractDateTime("reunión hoy a las 9")
        assertNotNull(due); assertNotNull(hoy)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val dHoy = ZonedDateTime.ofInstant(Instant.ofEpochMilli(hoy!!), z).toLocalDate()
        assertEquals("'de la noche' es hoy (no mañana)", dHoy, dDue)
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due), z).hour
        assertEquals("'9 de la noche' debe ser 21:00", 21, hour)
    }

    @Test
    fun deLaTarde_isToday_and21h() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 de la tarde")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'de la tarde' es hoy", today, dDue)
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due), z).hour
        assertEquals("'9 de la tarde' debe ser 21:00", 21, hour)
    }

    @Test
    fun estaNoche_isToday() {
        val due = ContextIntentEngine.extractDateTime("reunión esta noche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'esta noche' es hoy", today, dDue)
    }

    @Test
    fun estaTarde_isToday() {
        val due = ContextIntentEngine.extractDateTime("reunión esta tarde")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'esta tarde' es hoy", today, dDue)
    }

    @Test
    fun mananaPorLaNoche_isTomorrow_and21h() {
        // "mañana a las 9 de la noche" combina día siguiente + sufijo nocturno:
        // la regla "de la noche" NO debe enmascarar "mañana" = día siguiente.
        val due = ContextIntentEngine.extractDateTime("reunión mañana a las 9 de la noche")
        val man = ContextIntentEngine.extractDateTime("reunión mañana a las 9")
        assertNotNull(due); assertNotNull(man)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(man!!), z).toLocalDate()
        assertEquals("'mañana ... de la noche' debe ser mañana", dMan, dDue)
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due), z).hour
        assertEquals(21, hour)
    }

    // --- "mañana por la mañana" / "mañana ... de la mañana" (día siguiente + meridiano) ---
    // Frase cotidiana: el primer "mañana" = adverbio de día siguiente; el segundo
    // ("de/por la mañana") = sufijo de meridiano. La exclusión anti-colisión de c.579
    // (que suprime "mañana"=día siguiente cuando aparece como sufijo de hora) NO debe
    // suprimirlo cuando hay DOS "mañana": una es sufijo, la otra es el adverbio real.

    @Test
    fun mananaPorLaManana_isTomorrow() {
        val due = ContextIntentEngine.extractDateTime("reunión mañana por la mañana")
        val man = ContextIntentEngine.extractDateTime("reunión mañana")
        assertNotNull(due); assertNotNull(man)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(man!!), z).toLocalDate()
        assertEquals(
            "'mañana por la mañana' debe fecharse para mañana (no null, no hoy)",
            dMan, dDue
        )
    }

    @Test
    fun mananaDeLaManana_isTomorrow_and9h() {
        val due = ContextIntentEngine.extractDateTime("reunión mañana a las 9 de la mañana")
        val man = ContextIntentEngine.extractDateTime("reunión mañana a las 9")
        assertNotNull(due); assertNotNull(man)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val dMan = ZonedDateTime.ofInstant(Instant.ofEpochMilli(man!!), z).toLocalDate()
        assertEquals("'mañana ... de la mañana' debe ser mañana", dMan, dDue)
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due), z).hour
        assertEquals("'9 de la mañana' debe ser 09:00", 9, hour)
    }

    // --- Número suelto ANTES de la pista horaria: no debe robar el match ---
    // "comprar 2 kilos de arroz a las 9 de la mañana": el regex `.find()` casaba
    // el primer número ("2", sin prefijo "a las") y, como ese match tenía
    // hasTimeCue=false, se descartaba — pero al ser el único `.find()` el motor
    // NUNCA examinaba el "9 de la mañana" posterior. La cita perdía su hora
    // (y su fecha si no había otra pista). El usuario agendaba "a las 9" y la
    // app la dejaba sin horario (P1 captura/IA honesta).
    @Test
    fun leadingBareNumberDoesNotStealLaterTimeCue() {
        val due = ContextIntentEngine.extractDateTime("comprar 2 kilos de arroz a las 9 de la mañana")
        assertNotNull(
            "una pista horaria válida tras un número suelto debe extraerse", due
        )
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'9 de la mañana' debe quedar en 09:00", 9, hour)
    }

    // --- Mismo anti-colisión para FECHA: número suelto antes de "el <día>" ---
    // "comprar 2 kilos el 25": el regex de fecha `.find()` casaba "2" (sin "el"),
    // lo descartaba por guard y NUNCA examinaba "el 25" → fecha perdida.
    @Test
    fun leadingBareNumberDoesNotStealLaterDateCue() {
        val due = ContextIntentEngine.extractDateTime("comprar 2 kilos el 25")
        assertNotNull("una fecha válida tras un número suelto debe extraerse", due)
    }

    // --- "al mediodía"/"a medianoche" (palabra suelta, sin número) ---
    // Paridad parser↔context: NaturalTaskParser resuelve estas horas canónicas
    // ("reunión al mediodía" → 12:00, "entrega a medianoche" → 00:00) vía sus
    // patrones de palabra suelta (líneas ~1532-1554 → explicitTimeData). Antes
    // ContextIntentEngine.extractDateTime SÓLO extraía hora de la regex numérica
    // (\d{1,2})...: "al mediodía" no producía targetTime (dueAt=null o sólo fecha)
    // → un ContextEvent "reunión al mediodía" nacía sin vencimiento, invisible en
    // What Now y sin recordatorio (evitar olvidos). Misma clase de inconsistencia
    // parser↔context de c.579/c.583/c.584. c.587 cierra la brecha de paridad.

    @Test
    fun alMediodia_produceHora_noNull() {
        val due = ContextIntentEngine.extractDateTime("reunión al mediodía")
        assertNotNull("'al mediodía' debe extraer fecha/hora (no null)", due)
    }

    @Test
    fun alMediodia_hourIs12() {
        val due = ContextIntentEngine.extractDateTime("reunión al mediodía")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'al mediodía' = 12:00 (mediodía)", 12, hour)
    }

    @Test
    fun aMedianoche_produceHora_noNull() {
        val due = ContextIntentEngine.extractDateTime("entrega a medianoche")
        assertNotNull("'a medianoche' debe extraer fecha/hora (no null)", due)
    }

    @Test
    fun aMedianoche_hourIs0() {
        val due = ContextIntentEngine.extractDateTime("entrega a medianoche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'a medianoche' = 00:00 (medianoche)", 0, hour)
    }

    @Test
    fun aLaMedianoche_hourIs0() {
        // Forma con artículo: "a la medianoche" (paridad con parser l.1554).
        val due = ContextIntentEngine.extractDateTime("entrega a la medianoche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'a la medianoche' = 00:00 (medianoche)", 0, hour)
    }

    @Test
    fun alMediodia_isToday() {
        // Sin otra referencia de día, "al mediodía" cae a hoy (default del motor).
        val due = ContextIntentEngine.extractDateTime("reunión al mediodía")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val today = LocalDate.now(z)
        assertEquals("'al mediodía' (sin día) es hoy", today, dDue)
    }
}

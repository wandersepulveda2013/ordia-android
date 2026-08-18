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
    fun alMediodia_isToday_or_manana() {
        // Sin otra referencia de día, "al mediodía" cae a hoy si aún no llegó (mañana),
        // o a mañana si ya pasó (past-safe c.590). Antes de las 12 es hoy; a partir de
        // las 12 se rueda. Se respeta la hora real para no romper en CI a distintas horas.
        val due = ContextIntentEngine.extractDateTime("reunión al mediodía")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val now = ZonedDateTime.now(z)
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val esperado = if (now.hour < 12) LocalDate.now(z) else LocalDate.now(z).plusDays(1)
        assertEquals("'al mediodía' (sin día) respeta past-safe", esperado, dDue)
    }

    // --- "esta <parte del día>": paridad con NaturalTaskParser.partOfDayTimes (c.588) ---
    // Parser: mañana→09:00, tarde→15:00, noche→21:00, madrugada→04:00, fecha=hoy.
    // Antes ContextIntentEngine: "esta noche"/"esta tarde" sólo fijaban targetDate=today y
    // dejaban targetTime=null → caía al default LocalTime.of(12,0)=MEDIODÍA ("esta noche"
    // = 12:00, 9h de error). Peor: "esta mañana"/"esta madrugada" ni siquiera se reconocían
    // → "esta mañana" contiene "mañana" y colisionaba con la regla "mañana"=día siguiente
    // (l.429, sin guard null) → se fechaba para MAÑANA + mediodía (día Y hora erróneos).
    // Un ContextEvent "reunión esta noche" nacía a las 12:00 y "cita esta mañana" para
    // mañana a mediodía: citas invisibles/mal situadas en What Now y recordatorios (P1).

    @Test
    fun estaNoche_hourIs21() {
        val due = ContextIntentEngine.extractDateTime("reunión esta noche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'esta noche' = 21:00 (no mediodía 12:00)", 21, hour)
    }

    @Test
    fun estaTarde_hourIs15() {
        val due = ContextIntentEngine.extractDateTime("comprar pan esta tarde")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'esta tarde' = 15:00 (no mediodía 12:00)", 15, hour)
    }

    @Test
    fun estaManana_isToday_notTomorrow() {
        // Crítico: "esta mañana" NO debe colisionar con "mañana"=día siguiente.
        val due = ContextIntentEngine.extractDateTime("reunión esta mañana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'esta mañana' es hoy (no mañana)", LocalDate.now(z), dDue)
    }

    @Test
    fun estaManana_hourIs9() {
        val due = ContextIntentEngine.extractDateTime("reunión esta mañana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'esta mañana' = 09:00", 9, hour)
    }

    @Test
    fun estaMadrugada_isToday() {
        val due = ContextIntentEngine.extractDateTime("viaje esta madrugada")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'esta madrugada' es hoy", LocalDate.now(z), dDue)
    }

    @Test
    fun estaMadrugada_hourIs4() {
        val due = ContextIntentEngine.extractDateTime("viaje esta madrugada")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).hour
        assertEquals("'esta madrugada' = 04:00", 4, hour)
    }

// --- Past-safe: midpoints canónicos en el pasado se ruedan a +1 día (c.593) ---
    // Regresión c.593: extractDateTime carecía del past-safe de medianoche/mediodía
    // del NaturalTaskParser (l.4706-4711). Una captura contextual "reunión a las 12 de
    // la noche" (medianoche) tomada a cualquier hora del día caía en hoy 00:00 (pasado)
    // → recordatorio (dueAt-offset) <= now → ReminderSync lo descarta → cita olvidada
    // (P1: evitar olvidos). Ahora el motor rueda a mañana, paridad con el parser.
    @Test
    fun medianoche_pasada_se_ruega_a_manana() {
        // Medianoche de hoy (00:00) SIEMPRE está en el pasado al capturar (la ventana
        // "medianoche justo ahora" es despreciable), así esta aserción es determinista.
        val due = ContextIntentEngine.extractDateTime("reunión a las 12 de la noche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val manana = LocalDate.now(z).plusDays(1)
        assertEquals("'12 de la noche' ya pasada se rueda a mañana (no hoy pasado)", manana, dDue)
    }

    @Test
    fun medianoche_madrugada_pasada_se_ruega_a_manana() {
        // Misma divergencia P1 con "12 de la madrugada" (también medianoche).
        val due = ContextIntentEngine.extractDateTime("reunión a las 12 de la madrugada")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val manana = LocalDate.now(z).plusDays(1)
        assertEquals("'12 de la madrugada' ya pasada se rueda a mañana", manana, dDue)
    }

    @Test
    fun palabra_medianoche_pasada_se_ruega_a_manana() {
        // "a medianoche" (palabra suelta) sin día: mismo past-safe que la forma numérica.
        val due = ContextIntentEngine.extractDateTime("entrega a medianoche")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        val manana = LocalDate.now(z).plusDays(1)
        assertEquals("'a medianoche' ya pasada se rueda a mañana", manana, dDue)
    }

    // --- Fracciones sub-hora "y media"/"y cuarto"/"menos cuarto"/"y N" ---
    // Brecha de paridad parser↔context (c.594). NaturalTaskParser resuelve la rama
    // positiva "y media" (+30), "y cuarto" (+15), "y tres cuartos"/"y cuarenta y
    // cinco" (+45), "y N" (+N) y la negativa "menos cuarto" (−15), "menos N" (−N)
    // vía [CLOCK_FRACTION_Y]/[CLOCK_FRACTION_MENOS] (líneas ~1307-1397), aplicando
    // la fracción a la hora numérica Y a las canónicas mediodía/medianoche (grupo 1
    // de sus patrones). Antes ContextIntentEngine.extractDateTime SÓLO leía minutos
    // del `:MM` (group 3 del [timePattern]): "a las 3 y media de la tarde" caía en
    // 15:00 (no 15:30), "al mediodía y media" en 12:00 (no 12:30). Un ContextEvent
    // de captura se agendaba hasta 30 min mal → recordatorio desplazado (evitar
    // olvidos, P1). c.594 cierra la brecha con paridad de palabras→minutos.

    // --- Fechas pasadas relativas: paridad con NaturalTaskParser (l.4164-4165) ---
    // "ayer"/"anteayer"/"antier" son fechas PASADAS explícitas. Antes
    // extractDateTime NO las reconocía (ninguna rama las trataba) → devolvía null
    // → un ContextEvent capturado de una notificación ("reunión de ayer") nacía
    // SIN dueAt y la tarea VENCIDA no aparecía en What Now (olvido, P1). El parser
    // ya las resolvía (ayer=−1d, anteayer/antier=−2d); el contexto debe mantener
    // paridad para que la captura de contexto no pierda la urgencia de vencimiento.
    // Al mantenerse en el pasado, la cita vencida se hace visible (honesto: el
    // usuario reconoce que la tarea está atrasada).

    @Test
    fun ayer_isYesterday() {
        val due = ContextIntentEngine.extractDateTime("reunión de ayer")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'ayer' = hoy menos 1", LocalDate.now(z).minusDays(1), dDue)
    }

    @Test
    fun anteayer_isTwoDaysAgo() {
        val due = ContextIntentEngine.extractDateTime("cita de anteayer")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'anteayer' = hoy menos 2", LocalDate.now(z).minusDays(2), dDue)
    }

    @Test
    fun antier_isTwoDaysAgo() {
        // "antier" = variante coloquial hispanoamericana de "anteayer".
        val due = ContextIntentEngine.extractDateTime("antier tenía que llamar")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'antier' = hoy menos 2", LocalDate.now(z).minusDays(2), dDue)
    }

    @Test
    fun ayer_conHora_keepsPastDate() {
        // "ayer a las 4": la hora se fija, pero la fecha debe seguir siendo AYER
        // (no caer a hoy por la rama de hora). El parser lo mantiene en el pasado.
        val due = ContextIntentEngine.extractDateTime("reunión ayer a las 4")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'ayer a las 4' sigue siendo ayer", LocalDate.now(z).minusDays(1), dDue)
    }

    @Test
    fun aLasTresYMediaDeLaTarde_es15_30() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 3 y media de la tarde")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'3 y media de la tarde' = 15:00 (PM)", 15, dt.hour)
        assertEquals("'3 y media de la tarde' = 15:30 (media)", 30, dt.minute)
    }

    @Test
    fun aLasNueveYCuarto_es9_15() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 9 y cuarto")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'9 y cuarto' = 09:00", 9, dt.hour)
        assertEquals("'9 y cuarto' = 09:15 (cuarto)", 15, dt.minute)
    }

    @Test
    fun aLasOnceYVeinte_es11_20() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 11 y veinte")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'11 y veinte' = 11:00", 11, dt.hour)
        assertEquals("'11 y veinte' = 11:20", 20, dt.minute)
    }

    @Test
    fun aLasDiezMenosCuarto_es9_45() {
        val due = ContextIntentEngine.extractDateTime("reunión a las 10 menos cuarto")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'10 menos cuarto' = 09:00 (wrap)", 9, dt.hour)
        assertEquals("'10 menos cuarto' = 09:45", 45, dt.minute)
    }

    @Test
    fun alMediodiaYMedia_es12_30() {
        val due = ContextIntentEngine.extractDateTime("reunión al mediodía y media")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'al mediodía y media' = 12:00", 12, dt.hour)
        assertEquals("'al mediodía y media' = 12:30 (media)", 30, dt.minute)
    }

    @Test
    fun aMedianocheYCuarto_es0_15() {
        val due = ContextIntentEngine.extractDateTime("entrega a medianoche y cuarto")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z)
        assertEquals("'a medianoche y cuarto' = 00:00", 0, dt.hour)
        assertEquals("'a medianoche y cuarto' = 00:15 (cuarto)", 15, dt.minute)
    }
    // --- Períodos relativos (paridad con NaturalTaskParser) ---
    // "la semana que viene"/"el mes que viene"/"el año que viene" y sus sinónimos
    // ("próxima semana"/"próximo mes"/"la semana entrante") se resuelven como
    // +7/+30/+365 días (mismo día-aritmética que el parser, l.3830-3844). Antes
    // extractDateTime NO las reconocía → un ContextEvent de notificación
    // ("reunión la semana que viene") nacía SIN dueAt → la cita futura no generaba
    // recordatorio ni aparecía en el planificador (P1 evitar olvidos).

    @Test
    fun laSemanaQueViene_isPlus7Days() {
        val due = ContextIntentEngine.extractDateTime("reunión la semana que viene")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'la semana que viene' = hoy + 7", LocalDate.now(z).plusDays(7), dDue)
    }

    @Test
    fun elMesQueViene_isPlus30Days() {
        val due = ContextIntentEngine.extractDateTime("pago el mes que viene")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'el mes que viene' = hoy + 30", LocalDate.now(z).plusDays(30), dDue)
    }

    @Test
    fun elAnoQueViene_isPlus365Days() {
        val due = ContextIntentEngine.extractDateTime("revisión el año que viene")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'el año que viene' = hoy + 365", LocalDate.now(z).plusDays(365), dDue)
    }

    @Test
    fun proximaSemana_isPlus7Days() {
        val due = ContextIntentEngine.extractDateTime("entrega próxima semana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'próxima semana' = hoy + 7", LocalDate.now(z).plusDays(7), dDue)
    }

    @Test
    fun proximoMes_isPlus30Days() {
        val due = ContextIntentEngine.extractDateTime("alquiler próximo mes")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'próximo mes' = hoy + 30", LocalDate.now(z).plusDays(30), dDue)
    }

    @Test
    fun enUnaSemana_isPlus7Days() {
        // "en una semana" = +7d (paridad parser l.316). Forma coloquial frecuente.
        val due = ContextIntentEngine.extractDateTime("cita en una semana")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'en una semana' = hoy + 7", LocalDate.now(z).plusDays(7), dDue)
    }

    // --- Períodos pasados (paridad con NaturalTaskParser lastPeriodPattern) ---
    // "la semana pasada"/"el mes pasado"/"el año pasado" y "anterior" se resuelven
    // como -7/-30/-365 días (parser l.3288-3300). El usuario reconoce que la cita
    // está vencida → debe aparecer en What Now como atrasada (no perderse).

    @Test
    fun laSemanaPasada_isMinus7Days() {
        val due = ContextIntentEngine.extractDateTime("reunión la semana pasada")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'la semana pasada' = hoy - 7", LocalDate.now(z).minusDays(7), dDue)
    }

    @Test
    fun elMesPasado_isMinus30Days() {
        val due = ContextIntentEngine.extractDateTime("cita el mes pasado")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'el mes pasado' = hoy - 30", LocalDate.now(z).minusDays(30), dDue)
    }

    @Test
    fun elAnoPasado_isMinus365Days() {
        val due = ContextIntentEngine.extractDateTime("visita el año pasado")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'el año pasado' = hoy - 365", LocalDate.now(z).minusDays(365), dDue)
    }

    @Test
    fun laSemanaAnterior_isMinus7Days() {
        // "anterior" = sinónimo pleno de "pasado" para períodos (parser l.497-512).
        val due = ContextIntentEngine.extractDateTime("reunión la semana anterior")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'la semana anterior' = hoy - 7", LocalDate.now(z).minusDays(7), dDue)
    }

    // --- Anti-falsos-positivos: "semana"/"mes"/"año" SIN calificador → sin fecha ---
    // El bloque de períodos exige un calificador explícito (que viene/próxima/pasada/
    // anterior/en una…) para no inventar fechas. "esta semana"/"cada semana"/
    // "fin de semana" NO llevan calificador → deben quedar SIN dueAt (no asumir
    // +7d solo por la palabra "semana"). Protege contra regresiones que ignoren
    // los calificadores y produzcan fechas espurias.

    @Test
    fun estaSemana_sinCalificador_noDueAt() {
        assertNull(
            "'esta semana' sin calificador = sin fecha (no +7d)",
            ContextIntentEngine.extractDateTime("comprar pan esta semana")
        )
    }

    @Test
    fun cadaSemana_sinCalificador_noDueAt() {
        assertNull(
            "'cada semana' sin calificador = sin fecha (no +7d)",
            ContextIntentEngine.extractDateTime("revisar el informe cada semana")
        )
    }

    @Test
    fun finDeSemana_sinCalificador_noDueAt() {
        assertNull(
            "'fin de semana' sin calificador = sin fecha (no +7d)",
            ContextIntentEngine.extractDateTime("descansar el fin de semana")
        )
    }

    // --- Períodos relativos multi-unidad (paridad con NaturalTaskParser.relativePattern) ---
    // "en 2 semanas"/"dentro de 3 meses"/"de aquí a 5 días"/"en un par de semanas"
    // deben resolverse como N×unitDays (igual que el parser). Antes extractDateTime
    // sólo reconocía "en una semana" (singular escrito) → estas formas cotidianas con
    // cantidad numérica devolvían null → un ContextEvent de notificación futuro nacía
    // SIN dueAt → sin recordatorio ni planificador (P1 evitar olvidos).

    @Test
    fun enDosSemanas_isPlus14Days() {
        val due = ContextIntentEngine.extractDateTime("reunión en 2 semanas")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'en 2 semanas' = hoy + 14 días", LocalDate.now(z).plusDays(14), dDue)
    }

    @Test
    fun dentroDeTresMeses_isPlus90Days() {
        val due = ContextIntentEngine.extractDateTime("control en 3 meses")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'en 3 meses' = hoy + 90 días (3×30)", LocalDate.now(z).plusDays(90), dDue)
    }

    @Test
    fun deAquiACincoDias_isPlus5Days() {
        val due = ContextIntentEngine.extractDateTime("entrega de aquí a 5 días")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'de aquí a 5 días' = hoy + 5 días", LocalDate.now(z).plusDays(5), dDue)
    }

    @Test
    fun dentroDeDosAnos_isPlus730Days() {
        val due = ContextIntentEngine.extractDateTime("vencimiento dentro de 2 años")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'dentro de 2 años' = hoy + 730 días (2×365)", LocalDate.now(z).plusDays(730), dDue)
    }

    @Test
    fun enDiezDias_isPlus10Days() {
        val due = ContextIntentEngine.extractDateTime("llamar en 10 días")
        assertNotNull(due)
        val z = ZoneId.systemDefault()
        val dDue = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), z).toLocalDate()
        assertEquals("'en 10 días' = hoy + 10 días", LocalDate.now(z).plusDays(10), dDue)
    }

    // --- Paridad parser↔detector de anclaje (c.600) ---
    // hasDateReference/hasTimeReference alimentan el bono contextual de confianza
    // (+0.1 fecha, +0.08 hora) en scoreContextualBonus. Antes omitían anclajes que
    // extractDateTime sí resolvía, así una frase con "ayer" no recibía el bono y caía
    // por debajo de MINIMUM_CONFIDENCE: el compromiso se descartaba (olvido, P1).
    // Estos tests bloquean la paridad a nivel analyze() de extremo a extremo.

    private fun analyzeAnchor(raw: String): com.ordia.app.context.ContextIntent? =
        ContextIntentEngine.analyze(
            com.ordia.app.context.ContextEvent(
                com.ordia.app.context.ContextCaptureSource.SHARED_TEXT,
                raw, System.currentTimeMillis(), null, null
            )
        )

    @Test
    fun ayerProducesIntent_parityWithManana() {
        // "pagar la factura ayer" y "pagar la factura mañana" deben capturarse ambos:
        // mismo verbo + objeto, sólo cambia el ancla temporal reconocido.
        assertNotNull(
            "ayer es ancla de fecha resuelto por extractDateTime; el bono de fecha " +
                "debe aplicarse y el compromiso no debe descartarse",
            analyzeAnchor("pagar la factura ayer")
        )
    }

    @Test
    fun ayerAndMananaCaptureSymmetry() {
        val conAyer = analyzeAnchor("pagar la factura ayer")
        val conManana = analyzeAnchor("pagar la factura mañana")
        assertNotNull(conAyer)
        assertNotNull(conManana)
        // Ambos capturados: la paridad restaura la simetría de anclaje de fecha.
    }

    @Test
    fun anteayerProducesIntent() {
        assertNotNull(
            "anteayer es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura anteayer")
        )
    }

    @Test
    fun antierProducesIntent() {
        assertNotNull(
            "antier (variante coloquial) es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura antier")
        )
    }

    // Nota: la paridad de hora (medianoche/mediodía en hasTimeReference) se
    // verifica a nivel de detector con tools/run_parity_probe.sh. No se bloca vía
    // analyze() porque la captura de extremo a extremo depende del puntaje base de
    // la categoría, no sólo del bono de hora (+0.08): una aserción de captura sería
    // frágil y no aislaria el fix del detector.


    // --- "las N" DESNUDA (c.600): paridad con NaturalTaskParser (c.596).
    // Una captura de contexto p.ej. "cita las 3" / "reunión las 7 y media" menciona
    // hora SIN introductor "a"/"para". El [timePattern] casaba el grupo 1 vacío y,
    // al no haber :MM ni meridiano, hasTimeCue=false → targetTime=null → la cita
    // nacía SIN hora (caía al mediodía por defecto), P1 (evitar olvidos / cita a
    // hora errónea). La rewriter del parser resolvía "las N" en tareas creadas a
    // mano, pero NO las capturadas por el motor de contexto. ---

    @Test
    fun bareLasN_resuelveHora() {
        val due = ContextIntentEngine.extractDateTime("cita las 3")
        assertNotNull("'cita las 3' debe resolver hora 03:00", due)
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), ZoneId.systemDefault())
        assertEquals("'las 3' = 03:00", 3, dt.hour)
        assertEquals("'las 3' = minuto 0", 0, dt.minute)
    }

    @Test
    fun bareLasNYMedia_resuelve7_30() {
        val due = ContextIntentEngine.extractDateTime("reunión las 7 y media")
        assertNotNull(due)
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), ZoneId.systemDefault())
        assertEquals("'las 7 y media' = 07:00", 7, dt.hour)
        assertEquals("'las 7 y media' = 07:30", 30, dt.minute)
    }

    @Test
    fun bareLasNDeLaTarde_resuelve15_00() {
        val due = ContextIntentEngine.extractDateTime("cita las 3 de la tarde")
        assertNotNull(due)
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), ZoneId.systemDefault())
        assertEquals("'las 3 de la tarde' = 15:00 (PM)", 15, dt.hour)
        assertEquals("'las 3 de la tarde' = minuto 0", 0, dt.minute)
    }

    @Test
    fun bareLasColon_resuelve4_30() {
        val due = ContextIntentEngine.extractDateTime("llamar las 4:30")
        assertNotNull(due)
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), ZoneId.systemDefault())
        assertEquals("'las 4:30' = 04:00", 4, dt.hour)
        assertEquals("'las 4:30' = 04:30", 30, dt.minute)
    }

    @Test
    fun bareLasConFecha_resuelveHoraEseDia() {
        // "reunión el 25 las 3": la hora desnuda debe combinarse con la fecha.
        val due = ContextIntentEngine.extractDateTime("reunión el 25 las 3")
        assertNotNull(due)
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due!!), ZoneId.systemDefault())
        assertEquals("'el 25 las 3' = 03:00", 3, dt.hour)
        assertEquals("'el 25 las 3' = día 25", 25, dt.dayOfMonth)
    }

    @Test
    fun bareLasNoInventadaDeCantidad() {
        // "compra las 3 manzanas": "las 3" aquí es cantidad, NO hora. Tras la
        // corrección debe seguir SIN producir hora (guard anti-cantidad).
        val due = ContextIntentEngine.extractDateTime("compra las 3 manzanas")
        // Aceptamos null (sin fecha/hora) o fecha con hora canónica por defecto
        // (mediodía), PERO nunca hora 03:00 inventada de la cantidad "3".
        if (due != null) {
            val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(due), ZoneId.systemDefault())
            assert(dt.hour != 3 || dt.minute != 0) {
                "'las 3 manzanas' no debe inventar hora 03:00 desde la cantidad"
            }
        }
    }

    // --- Paridad de períodos relativos (c.602) ---
    // extractDateTime (c.598/c.599) resuelve "la semana que viene"/"en 2 semanas"/
    // "en una semana"; hasDateReference debe reconocerlos para que reciban el bono
    // de fecha (+0.1) y superen MINIMUM_CONFIDENCE. Sin paridad, una cita futura
    // ("reunión la semana que viene") se descartaba por umbral (olvido, P1).

    @Test
    fun semanaQueViene_matchesManyanaSymmetry() {
        val conPeriodo = analyzeAnchor("pagar la factura la semana que viene")
        val conManana = analyzeAnchor("pagar la factura mañana")
        assertNotNull(conPeriodo)
        assertNotNull(conManana)
        // Ambos capturados: el ancla de período relativo recibe el mismo bono de
        // fecha que "mañana", restableciendo la simetría.
    }

    @Test
    fun enUnaSemana_matchesManyanaSymmetry() {
        assertNotNull(
            "\"en una semana\" es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura en una semana")
        )
    }

    @Test
    fun en2Semanas_matchesManyanaSymmetry() {
        assertNotNull(
            "\"en 2 semanas\" (multi-unidad) es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura en 2 semanas")
        )
    }

    @Test
    fun dentroDe3Meses_isAnchor() {
        assertNotNull(
            "\"dentro de 3 meses\" es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura dentro de 3 meses")
        )
    }

    @Test
    fun proximoMes_isAnchor() {
        assertNotNull(
            "\"el próximo mes\" es ancla de fecha resuelto por extractDateTime",
            analyzeAnchor("pagar la factura el próximo mes")
        )
    }

    // --- Paridad de hora "las N" DESNUDA en hasTimeReference (c.605) ---
    // Continuación directa de c.600 (hasDateReference) y c.601 (extractDateTime):
    // extractDateTime resuelve "las N" desnuda desde c.601, PERO hasTimeReference
    // (que alimenta el bono de hora +0.08 en scoreContextualBonus) NO la reconocía.
    // Sin ese bono, una captura marginal caía por debajo de MINIMUM_CONFIDENCE y se
    // DESCARTABA, aunque su gemela con "a las N" (mismo verbo, misma fecha) sí pasaba:
    // olvido asimétrico, P1. El rewriter normalizeBareLasHour (c.601) ya tiene el
    // guard anti-cantidad, así "comprar las 3 manzanas" NO recibe un bono de hora falso.

    @Test
    fun dentistaBareLasN_matchesAlasSymmetry() {
        // "ir al dentista el viernes a las 4" → capturado (conf=0.5).
        // "ir al dentista el viernes las 4"   → DESCARTADO sin el fix (base+fecha=0.4 < 0.45).
        val conCue = analyzeAnchor("ir al dentista el viernes a las 4")
        val sinCue = analyzeAnchor("ir al dentista el viernes las 4")
        assertNotNull(conCue)
        assertNotNull(
            "'ir al dentista el viernes las 4' es la misma cita que '... a las 4'; la " +
                "hora desnuda resuelta por extractDateTime (c.601) debe dar el bono de " +
                "hora en hasTimeReference para no descartarse por umbral (olvido, P1)",
            sinCue
        )
    }

    @Test
    fun terapiaBareLasN_matchesAlasSymmetry() {
        val conCue = analyzeAnchor("terapia el viernes a las 4")
        val sinCue = analyzeAnchor("terapia el viernes las 4")
        assertNotNull(conCue)
        assertNotNull(sinCue)
    }

    @Test
    fun reunionEquipoBareLasN_matchesAlasSymmetry() {
        val conCue = analyzeAnchor("reunión de equipo el viernes a las 4")
        val sinCue = analyzeAnchor("reunión de equipo el viernes las 4")
        assertNotNull(conCue)
        assertNotNull(sinCue)
    }

    @Test
    fun revisionMedicaBareLasN_matchesAlasSymmetry() {
        val conCue = analyzeAnchor("revisión médica el viernes a las 4")
        val sinCue = analyzeAnchor("revisión médica el viernes las 4")
        assertNotNull(conCue)
        assertNotNull(sinCue)
    }

    @Test
    fun llamarDoctorBareLasN_matchesAlasSymmetry() {
        val conCue = analyzeAnchor("llamar al doctor el viernes a las 4")
        val sinCue = analyzeAnchor("llamar al doctor el viernes las 4")
        assertNotNull(conCue)
        assertNotNull(sinCue)
    }

    @Test
    fun bareLasNYMedia_matchesAlasSymmetry() {
        // Fracción "y media": extractDateTime la resuelve (c.601); hasTimeReference
        // debe reconocerla (cue de fracción horaria) para el bono.
        val conCue = analyzeAnchor("ir al dentista el viernes a las 4 y media")
        val sinCue = analyzeAnchor("ir al dentista el viernes las 4 y media")
        assertNotNull(conCue)
        assertNotNull(sinCue)
    }

    @Test
    fun cantidadNoInventaBonoHora() {
        // Control anti-cuenta: "comprar las 3 manzanas el viernes" debe seguir SIN
        // capturarse como cita horaria. El guard anti-cantidad de normalizeBareLasHour
        // (c.601) preserva "las 3" como cantidad → hasTimeReference=false → sin bono falso.
        // Verificamos que NO se genere un intent cuya hora sea 03:00 (la "cantidad 3").
        val intent = analyzeAnchor("comprar las 3 manzanas el viernes")
        if (intent != null && intent.dueAt != null) {
            val dt = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(intent.dueAt!!), ZoneId.systemDefault()
            )
            assert(dt.hour != 3 || dt.minute != 0) {
                "'las 3 manzanas' no debe inventar hora 03:00 desde la cantidad (guard anti-cuenta)"
            }
        }
        // En cualquier caso, no debe capturarse como cita con hora falsa: si hay intent,
        // no debe llevar hora 03:00. La aserción anterior ya lo cubre.
    }

    // --- CALL: olvido de llamadas puras + título corrupto (P1) ---

    @Test
    fun llamarApersonaSinAncla_seCapturaComoCall() {
        // Antes: "llamar a María" (sin fecha/hora) quedaba en 0.32 (< 0.45) y se
        // DESCARTABA → una llamada legítima se olvidaba. Ahora el bono de objeto
        // explícito la eleva sobre el umbral.
        val intent = analyzeAnchor("llamar a María")
        assertNotNull("una llamada clara con objeto no debe descartarse (olvido, P1)", intent)
        assertEquals(
            "el kind debe ser CALL, no otro",
            com.ordia.app.context.ContextIntentKind.CALL,
            intent?.kind
        )
    }

    @Test
    fun llamarAMama_seCapturaComoCall() {
        val intent = analyzeAnchor("llamar a mamá")
        assertNotNull(intent)
        assertEquals(com.ordia.app.context.ContextIntentKind.CALL, intent?.kind)
    }

    @Test
    fun llamarAlDoctor_ganaSobreAppointment() {
        // "llamar al doctor el viernes a las 4": el verbo "llamar" hace evidente que
        // es una LLAMADA; "doctor" es solo el objeto. Antes empataba con APPOINTMENT
        // (0.5) y perdía por orden de enum → mal clasificada como cita médica.
        val intent = analyzeAnchor("llamar al doctor el viernes a las 4")
        assertNotNull(intent)
        assertEquals(
            "el verbo 'llamar' debe imponerse sobre el objeto 'doctor'",
            com.ordia.app.context.ContextIntentKind.CALL,
            intent?.kind
        )
    }

    @Test
    fun hablarConDentista_seCapturaComoCall() {
        val intent = analyzeAnchor("hablar con el dentista")
        assertNotNull(intent)
        assertEquals(com.ordia.app.context.ContextIntentKind.CALL, intent?.kind)
    }

    @Test
    fun callTitleSinDobleA_llamarAlDoctor() {
        // Antes: "llamar al doctor" → "Llamar a Al doctor" (doble "a" + "Al" mayúscula).
        // Con c.609 (sanitizeTitle) el residuo temporal de cola se depura → "Llamar al doctor".
        val intent = analyzeAnchor("llamar al doctor el viernes a las 4")
        assertNotNull(intent)
        assertEquals("Llamar al doctor", intent?.title)
    }

    @Test
    fun callTitleSinDobleA_llamarAMaria() {
        // Antes: "llamar a María" → "Llamar a A María" (doble "a").
        val intent = analyzeAnchor("llamar a María")
        assertNotNull(intent)
        assertEquals("Llamar a María", intent?.title)
    }

    @Test
    fun callTitleHablarCon_preservaVerbo_noInsertaA() {
        // Antes: "hablar con el dentista" → "Llamar a Con el dentista" (corrupto:
        // perdía "hablar con", insertaba "a" y "Con" mayúscula).
        val intent = analyzeAnchor("hablar con el dentista")
        assertNotNull(intent)
        assertEquals("Hablar con el dentista", intent?.title)
    }

    @Test
    fun callTitle_llamarAMama_minusculaPreservada() {
        // El objeto común ("mamá") no debe capitalizarse a "Mamá": respeta lo que el
        // usuario escribió.
        val intent = analyzeAnchor("llamar a mamá")
        assertNotNull(intent)
        assertEquals("Llamar a mamá", intent?.title)
    }

    @Test
    fun callTitle_residuoTemporalDepurado_llamarAlPediatraManana() {
        // Paridad c.609 sanitizeTitle + fix CALL: "llamar al pediatra mañana" debe
        // quedar "Llamar al pediatra" (el "mañana" se resolvió en dueAt, no en título).
        val intent = analyzeAnchor("llamar al pediatra mañana")
        assertNotNull(intent)
        assertEquals("Llamar al pediatra", intent?.title)
    }

    // --- REMINDER imperativo sin ancla temporal: paridad de piso con TASK (c.619) ---
    // El piso de confianza de c.613 cubre SOLO TASK ("recuérdame/no olvides/tengo que/
    // hay que"). Pero "recuérdame" también es palabra clave de REMINDER, y sus sinónimos
    // puros de aviso — "avísame"/"notifícame"/"acordarme" — sólo viven en REMINDER (no en
    // TASK). Sin fecha/hora, "avísame pagar la luz" / "notifícame llamar a mamá" quedaban
    // en conf 0.37 (< MINIMUM_CONFIDENCE 0.45) y se DESCARTABAN: el recordatorio explícito
    // por excelencia se olvidaba. Asimetría con "recuérdame pagar la luz" (TASK piso c.613
    // la captura). Paridad: un imperativo de aviso inequívoco + verbo es un recordatorio
    // claro con independencia de pistas temporales.

    @Test
    fun avisameSinAncla_noSeDescarta_paridadConRecordarme() {
        val recordarme = analyzeAnchor("recuérdame pagar la luz")
        val avisame = analyzeAnchor("avísame pagar la luz")
        assertNotNull("'recuérdame pagar la luz' se captura (piso TASK c.613)", recordarme)
        assertNotNull(
            "'avísame pagar la luz' es el mismo recordatorio; el imperativo de aviso " +
                "inequívoco no debe descartarse por ausencia de ancla temporal (olvido, P1)",
            avisame
        )
        assertEquals(
            "'avísame' debe clasificarse como REMINDER (no colapsar a TASK): el piso " +
                "de c.619 eleva REMINDER sin reescribir el kind",
            com.ordia.app.context.ContextIntentKind.REMINDER,
            avisame?.kind
        )
    }

    @Test
    fun notificameSinAncla_noSeDescarta() {
        assertNotNull(
            "'notifícame llamar a mamá' es un recordatorio explícito; no debe descartarse",
            analyzeAnchor("notifícame llamar a mamá")
        )
    }

    @Test
    fun acordarmeSinAncla_noSeDescarta() {
        assertNotNull(
            "'acordarme de pagar la factura' es un recordatorio explícito; no debe descartarse",
            analyzeAnchor("acordarme de pagar la factura")
        )
    }

    @Test
    fun imperativoAviso_conVerbo_requerido_noCasaChatCasual() {
        // Anti-falso-positivo: el piso exige verbo tras el imperativo (\s+\w), así que
        // "avísame" sola (saludo/muletilla) no debe generar recordatorio.
        assertNull(
            "'avísame' aislado no casa imperativo+verbo; no debe crear recordatorio",
            analyzeAnchor("avísame")
        )
    }
}

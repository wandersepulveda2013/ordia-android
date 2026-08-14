package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NaturalTaskParserTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun parsesTomorrowTimeAndPriority() {
        val result = NaturalTaskParser.parse("Llamar a Ana mañana a las 9:30 !alta", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun textWithoutCommand_remainsUnchanged() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun parsesNextWeekdayInSpanish() {
        val result = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun parsesRelativeDuration() {
        val result = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull("La fecha relativa no debe leerse como duración", result.durationMinutes)
    }

    @Test fun parsesWeeklyRecurrence() {
        val result = NaturalTaskParser.parse("Preparar informe todos los viernes", now, zone)
        assertEquals("Preparar informe", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesMultipleWeekdaysRecurrence() {
        val result = NaturalTaskParser.parse("Revisar reporte cada lunes y jueves", now, zone)
        assertEquals("Revisar reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,4", result.recurrenceDays)
        // Desde miércoles 29-jul, la primera ocurrencia es el jueves 30-jul.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "los lunes y jueves" es la forma natural más común para varios días en español.
    // Antes el parser solo admitía dos días con "cada X y Z"; con "los X y Z" casaba
    // un solo día y dejaba "y jueves" como residuo, creando una rutina que repetía solo
    // el primer día y perdía el resto → pérdida de datos silenciosa en rutinas.
    @Test fun parsesLosWeekdaysWithY() {
        val result = NaturalTaskParser.parse("Reunión los lunes y jueves a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesTodosLosWeekdaysWithY() {
        val result = NaturalTaskParser.parse("Gimnasio todos los lunes y miércoles", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3", result.recurrenceDays)
        // Desde miércoles 29-jul al mediodía: el miércoles (hoy) ya pasó su slot,
        // así que la siguiente ocurrencia es el lunes 03-ago.
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesCommaDayList() {
        val result = NaturalTaskParser.parse("Clases los lunes, miércoles y viernes", now, zone)
        assertEquals("Clases", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
        // Desde miércoles 29-jul al mediodía: el miércoles (hoy) ya pasó, el viernes 31-jul.
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Forma natural en español para varios días: espacio entre los primeros, "y"
    // solo antes del último ("lunes miércoles y viernes"). Antes el separador
    // entre pares solo admitía ","/"y", así esta forma casaba únicamente el primer
    // día y la rutina semanal perdía los demás (pérdida de datos silenciosa).
    @Test fun parsesSpaceSeparatedWeekdayListWithY() {
        val result = NaturalTaskParser.parse("Gimnasio cada lunes miércoles y viernes a las 6", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
        // Desde miércoles 29-jul: el miércoles (hoy) ya pasó su slot de 6:00,
        // la siguiente ocurrencia es el viernes 31-jul.
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesSpaceSeparatedWeekdayListAllSpaces() {
        val result = NaturalTaskParser.parse("Clase los lunes miércoles viernes", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    @Test fun parsesFourDaySpaceSeparatedList() {
        val result = NaturalTaskParser.parse("Estudiar cada lunes miércoles viernes y sábado", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5,6", result.recurrenceDays)
    }

    // "los lunes miércoles y viernes" (sin coma entre los dos primeros días) es la
    // forma informal más común en español escrito/voz. Antes el parser exigía
    // conector ","/"y" entre cada par, así que solo capturaba el primer día y
    // perdía el resto → rutina que se repetía un solo día en silencio (pérdida de
    // datos). El separador ahora es opcional: casa cuando la palabra siguiente
    // es otro día, sin robar texto ajeno ("los lunes con el equipo" para en "lunes").
    @Test fun parsesDayListWithoutCommaSeparator() {
        val result = NaturalTaskParser.parse("Regar plantas los lunes miércoles y viernes", now, zone)
        assertEquals("Regar plantas", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Plural de sábado/domingo ("los martes jueves y sábados"): antes el patrón
    // usaba forma singular con \b, así que "sábados"/"domingos" se rechazaba y
    // quedaba como residuo en el título, perdiendo ese día de la recurrencia.
    @Test fun parsesDayListWithPluralSabadoDomingo() {
        val result = NaturalTaskParser.parse("Clases los martes jueves y sábados", now, zone)
        assertEquals("Clases", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("2,4,6", result.recurrenceDays)
        // Desde miércoles 29-jul: el jueves 30-jul es la primera ocurrencia.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // El separador opcional NO debe robar texto que no es un día: "los lunes con el
    // comité" captura solo "lunes" y conserva "con el comité" en el título.
    @Test fun dayListStopsAtNonDayWord() {
        val result = NaturalTaskParser.parse("Reunión de equipo los lunes con el comité", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1", result.recurrenceDays)
        assertEquals("Reunión de equipo con el comité", result.title)
    }

    // Forma BARE (sin "los"/"cada"/"todos los") de lista de días: "gym sábados y
    // domingos", "fútbol domingos". Es tan común como la prefijada y antes caía sin
    // recurrencia, dejando los días como residuo en el título → la rutina se
    // olvidaba. La lista bare de 2+ días siempre es recurrencia; el singular plural
    // marcado (sábados/domingos) también lo es (hábito semanal). Un día suelto no
    // plural ("reunión martes") queda como fecha, no recurrencia (es ambiguo).
    @Test fun parsesBareDayListRecurrence() {
        val result = NaturalTaskParser.parse("Gym sábados y domingos", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
        // Desde miércoles 29-jul: la primera ocurrencia es el sábado 01-ago.
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesBareDayListWithExplicitTime() {
        val result = NaturalTaskParser.parse("Reunión sábados y domingos a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun parsesBarePluralSingleDayRecurrence() {
        val result = NaturalTaskParser.parse("Fútbol domingos a las 18", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("7", result.recurrenceDays)
        // Desde miércoles 29-jul: la primera ocurrencia es el domingo 02-ago.
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Variante sin tilde del bare-plural de sábado: "fútbol sabados". La regex de
    // lista casa ambas formas (s[aá]bados?), pero el guard `barePluralSingle` solo
    // comprobaba "sábados" con tilde → "sabados" caía sin recurrencia (NONE) y el
    // día se quedaba como residuo en el título: la rutina se olvidaba. El usuario
    // que escribe sin acentos no debe perder su hábito semanal más típico.
    @Test fun parsesBarePluralSingleDayRecurrenceUnaccented() {
        val result = NaturalTaskParser.parse("Fútbol sabados a las 18", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6", result.recurrenceDays)
        // Desde miércoles 29-jul: la primera ocurrencia es el sábado 01-ago.
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Lista bare de 2+ días con sábado sin tilde: la rama 2+ días ya la rescata,
    // pero se prueba para garantizar que el fix del guard no rompió la simetría.
    @Test fun parsesBareDayListUnaccentedSabado() {
        val result = NaturalTaskParser.parse("Gym sabados y domingos", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Un día suelto no plural ("reunión martes") es ambiguo (¿fecha?): NO debe
    // convertirse en recurrencia; se deja como fecha única para no programar una
    // rutina equivocada.
    @Test fun bareSingleNonPluralDayIsNotRecurrence() {
        val result = NaturalTaskParser.parse("Reunión martes", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("", result.recurrenceDays)
        // Fecha: el martes siguiente al miércoles 29-jul es 04-ago.
        assertEquals(LocalDate.of(2026, 8, 4), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesDailyRecurrence() {
        val result = NaturalTaskParser.parse("Tomar vitaminas cada día", now, zone)
        assertEquals("Tomar vitaminas", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        // Antes dueAt=null: la tarea diaria nunca tenía fecha ni recordatorio y se olvidaba.
        // Ahora se ancla a hoy (la fecha de captura) para ser accionable y recordable.
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "a diario" = la frase adverbial cotidiana más común para un hábito diario en español
    // ("llevar al niño al colegio a diario", "revisar correos a diario"). Antes caía a
    // rec=NONE: el compromiso diario nacía como tarea ÚNICA sin recurrencia ni recordatorio
    // periódico (P1: rutina silenciosamente perdida). Simétrico a "todos los días"/"cada día".
    @Test fun parsesADiarioRecurrence() {
        val result = NaturalTaskParser.parse("llevar al niño al colegio a diario", now, zone)
        assertEquals("llevar al niño al colegio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // "cada mañana/tarde/noche/madrugada" es la forma natural más común de un hábito
    // cotidiano en español. Antes NO se reconocía como recurrencia: "mañana" colisionaba
    // con la fecha "mañana" (día siguiente) y el hábito nacía como tarea ÚNICA para
    // mañana, sin recurrencia (P1: la rutina diaria se perdía; el recordatorio disparaba
    // una sola vez y nunca más). Ahora se mapea a DAILY con hora canónica de la parte del
    // día (mañana=09:00, tarde=15:00, noche=21:00, madrugada=04:00), anclada a hoy.
    @Test fun cadaMananaParsesDailyWithMorningTime() {
        val result = NaturalTaskParser.parse("Meditar cada mañana", now, zone)
        assertEquals("Meditar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaTardeParsesDailyWithAfternoonTime() {
        val result = NaturalTaskParser.parse("Pasear al perro cada tarde", now, zone)
        assertEquals("Pasear al perro", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaNocheParsesDailyWithNightTime() {
        val result = NaturalTaskParser.parse("Leer cada noche", now, zone)
        assertEquals("Leer", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaMananaWithExplicitTimeKeepsExplicitTime() {
        val result = NaturalTaskParser.parse("Regar plantas cada mañana a las 7", now, zone)
        assertEquals("Regar plantas", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(7, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaNochePmContextAppliesOffsetToHourWithoutMeridiem() {
        // "cada noche a las 10": contexto PM de la parte del día aplica +12 → 22:00, no 10:00.
        val result = NaturalTaskParser.parse("Tomar agua cada noche a las 10", now, zone)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalTime.of(22, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun todasLasNochesParsesDailyPlural() {
        val result = NaturalTaskParser.parse("Rezar todas las noches", now, zone)
        assertEquals("Rezar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun cadaMananaDoesNotBecomeTomorrowSingleTask() {
        // Regresión: antes "cada mañana" se fechaba en MAÑANA con recurrencia NONE porque
        // "mañana" colisionaba con la fecha "mañana". El hábito diario se perdía.
        val result = NaturalTaskParser.parse("Meditar cada mañana", now, zone)
        assertNotEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesIntervalRecurrence() {
        val result = NaturalTaskParser.parse("Lavar el coche cada 2 semanas", now, zone)
        assertEquals("Lavar el coche", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        // Antes dueAt=null: la recurrencia quincenal era invisible hasta su completado.
        // Ahora se ancla a hoy para arrancar el ciclo inmediatamente.
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Recurrencia quincenal con palabra (no dígito): "cada quincena", "quincenalmente".
    // Antes `intervalPattern` (que solo admite dígitos) no casaba, la recurrencia caía
    // a NONE y la tarea nacía SIN fecha (invisible en What Now/planificador, recordatorio
    // jamás disparaba). Ahora se mapea a WEEKLY interval=2 (cada 2 semanas ≈ quincena).
    @Test fun cadaQuincenaParsesBiweeklyRecurrence() {
        val result = NaturalTaskParser.parse("Nómina cada quincena", now, zone)
        assertEquals("Nómina", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenalmenteParsesBiweeklyRecurrence() {
        val result = NaturalTaskParser.parse("Reporte quincenalmente a las 9", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaQuincenaRespetaFechaExplicita() {
        val result = NaturalTaskParser.parse("Cobro cada quincena el 15/8", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        // La fecha explícita tiene prioridad sobre el anclaje a la fecha de captura.
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Formas ADJETIVAS de recurrencia cotidiana en español: "pago mensual",
    // "reunión semanal", "renta anual". Tan comunes como el adverbio "-mente"
    // ("mensualmente"), pero antes solo se reconocía el adverbio: el adjetivo caía
    // a NONE y la tarea recurrente nacía SIN cadencia (vencimiento olvidado, P1).
    // Ahora el adjetivo genera la misma cadencia que el adverbio y se limpia del título.
    @Test fun adjetivoMensualParsesMonthlyRecurrence() {
        val result = NaturalTaskParser.parse("Pago mensual", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun adjetivoSemanalParsesWeeklyRecurrence() {
        val result = NaturalTaskParser.parse("Reunión semanal", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun adjetivoAnualParsesYearlyRecurrence() {
        val result = NaturalTaskParser.parse("Renta anual", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun adjetivoQuincenalParsesBiweeklyRecurrence() {
        val result = NaturalTaskParser.parse("Reporte quincenal", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Adjetivos plurimensuales de plazo largo (bimestral/trimestral/semestral): hitos
    // financieros tan comunes como "mensual". Antes solo se reconocían vía numeral
    // ("cada 2/3/6 meses"); el adjetivo caía a NONE (compromiso periódico olvidado).
    // Se reutilizan MONTHLY + intervalo: RecurrenceEngine avanza plusMonths(interval).
    @Test fun adjetivoBimestralParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Factura bimestral", now, zone)
        assertEquals("Factura", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun adjetivoTrimestralParsesMonthlyInterval3() {
        val result = NaturalTaskParser.parse("Impuesto trimestral", now, zone)
        assertEquals("Impuesto", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun adjetivoSemestralParsesMonthlyInterval6() {
        val result = NaturalTaskParser.parse("Cierre semestral", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // El adjetivo respeta una fecha explícita (prioridad sobre el anclaje a la captura),
    // simétrico a "cada quincena el 15/8": la cadencia se conserva y la fecha manda.
    @Test fun adjetivoMensualRespetaFechaExplicita() {
        val result = NaturalTaskParser.parse("Pago mensual el 10", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Recurrencias de intervalo (diaria/quincenal/anual) sin fecha explícita se anclan a
    // la fecha de captura para no ser invisibles (P1: antes dueAt=null → sin recordatorio,
    // sin aparición en What Now/planificador → tarea olvidada). Simétrico al anclaje
    // mensual y semanal-con-días. La fecha explícita sigue teniendo prioridad.
    @Test fun dailyRecurrenceAnchorsToCaptureDateAndKeepsExplicitTime() {
        val result = NaturalTaskParser.parse("Tomar medicina cada día a las 8", now, zone)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun yearlyIntervalRecurrenceAnchorsToCaptureDate() {
        val result = NaturalTaskParser.parse("Renovar licencia cada año", now, zone)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun intervalRecurrenceWithExplicitDateKeepsExplicitDate() {
        // "cada 2 semanas el viernes": la fecha explícita tiene prioridad sobre el anclaje.
        val result = NaturalTaskParser.parse("Lavar el coche cada 2 semanas el viernes", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Intervalo de cadencia + lista de días: "cada 2 semanas los lunes" debe combinar
    // ambos (interval=2, días=[lunes]) y limpiar del título la frase de intervalo.
    // Antes la rama de días devolvía interval=1 (cadencia semanal errónea) y dejaba
    // "cada 2 semanas" como residuo en el título → rutina mal programada y título sucio.
    @Test fun biweeklyIntervalWithDayListCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Gym cada 2 semanas los lunes", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun quincenaIntervalWithDayListCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Reunión cada quincena los lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun triweeklyIntervalWithMultipleDaysCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Clase cada 3 semanas los martes y jueves", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("2,4", result.recurrenceDays)
    }

    // Intervalo ESCRITO (no dígito) + lista de días: "cada dos semanas los lunes"
    // debe combinar ambos (interval=2, días=[lunes]) y limpiar el título. Antes
    // `detectWeekInterval()` solo reconocía dígitos (`\d{1,3}`) para "cada N semanas",
    // así la forma escrita devolvía null → la rama de días usaba interval=1 (cadencia
    // semanal errónea, el doble de frecuente) y dejaba "cada dos semanas" como
    // residuo en el título → rutina mal programada y título sucio. Asimetría con la
    // forma con dígitos ("cada 2 semanas los lunes" ya funcionaba) y con el
    // intervalo escrito SIN días ("cada dos semanas" solo, resuelto en c.57). El
    // `intervalPattern` que sí admite números escritos NO se alcanza cuando hay una
    // lista de días (la rama de días devuelve antes).
    @Test fun writtenBiweeklyIntervalWithDayListCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Gym cada dos semanas los lunes", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun writtenTriweeklyIntervalWithMultipleDaysCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Clase cada tres semanas los martes y jueves", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("2,4", result.recurrenceDays)
    }

    @Test fun writtenBiweeklyIntervalWithWeekdayRangeCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Estudio cada dos semanas de lunes a viernes", now, zone)
        assertEquals("Estudio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun writtenBiweeklyIntervalWithWeekendCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Limpieza cada dos semanas los findes", now, zone)
        assertEquals("Limpieza", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun biweeklyIntervalWithWeekdayRangeCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Estudio cada 2 semanas de lunes a viernes", now, zone)
        assertEquals("Estudio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun biweeklyIntervalWithWeekendCombinesIntervalAndDays() {
        val result = NaturalTaskParser.parse("Limpieza cada 2 semanas los findes", now, zone)
        assertEquals("Limpieza", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("6,7", result.recurrenceDays)
    }

    // Sin intervalo explícito, la cadencia semanal normal sigue siendo interval=1.
    @Test fun dayListWithoutIntervalKeepsWeeklyInterval() {
        val result = NaturalTaskParser.parse("Fútbol los lunes y viernes", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals("1,5", result.recurrenceDays)
    }

    // Mensual anclado a día del mes ("el 15 de cada mes"): antes el día quedaba como
    // residuo en el título y dueAt=null (la tarea nunca tenía fecha, los recordatorios
    // no disparaban). Ahora se ancla al próximo día 15 y se limpia el título.
    @Test fun parsesMonthlyDayOfMonthRecurrence() {
        val result = NaturalTaskParser.parse("Pagar la cuenta el 15 de cada mes", now, zone)
        assertEquals("Pagar la cuenta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // 29-jul → el 15 ya pasó este mes → 15-ago.
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesMonthlyDayOfMonthTodayInclusive() {
        val result = NaturalTaskParser.parse("Cobro el 29 de cada mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // El sufijo de hora "de la manana" no debe crear una falsa fecha "8 de la" que
    // anule la recurrencia mensual: la fecha debe ser el día del mes, la hora la dada.
    @Test fun monthlyDayOfMonthKeepsExplicitTime() {
        val result = NaturalTaskParser.parse("Renta el 10 de cada mes a las 8 de la manana", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesReminderOffsetBeforeDue() {
        val result = NaturalTaskParser.parse("Presentar propuesta el viernes recuérdame 2 horas antes", now, zone)
        assertEquals("Presentar propuesta", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "N min antes" debe interpretarse como recordatorio, no como duración.
    // Antes, "30 min antes" caía en el patrón de duración (\d min) porque el
    // patrón de recordatorio "\d antes" no aceptaba la abreviatura "min".
    @Test fun parsesAbbreviatedMinBeforeAsReminder() {
        val result = NaturalTaskParser.parse("Avisar 30 min antes", now, zone)
        assertEquals("Avisar", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun parsesAbbreviatedMinBeforeAsReminderWithoutVerb() {
        val result = NaturalTaskParser.parse("Reunión 15 min antes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    // --- Recordatorio con números escritos (ciclo 38) ---
    // "recuérdame una hora antes" / "dos horas antes" / "treinta minutos antes":
    // antes solo se aceptaban dígitos, así que la frase no se reconocía como
    // recordatorio, quedaba como residuo en el título y el recordatorio nunca se
    // programaba (la cita se olvidaba).
    @Test fun parsesWrittenAmountReminderWithVerb() {
        val result = NaturalTaskParser.parse("Reunión recuérdame una hora antes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(60, result.reminderOffsetMinutes)
    }

    @Test fun parsesWrittenAmountReminderTwoHours() {
        val result = NaturalTaskParser.parse("Reunión recuérdame dos horas antes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
    }

    @Test fun parsesWrittenAmountReminderThirtyMinutes() {
        val result = NaturalTaskParser.parse("Vuelo recuérdame treinta minutos antes", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun parsesWrittenAmountReminderWithoutVerb() {
        val result = NaturalTaskParser.parse("Cita una hora antes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(60, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    // "media hora antes" es recordatorio (30 min), NO duración. Antes era robado
    // por el patrón de duración fraccionaria (30 min falsos como duración) y el
    // recordatorio quedaba en null.
    @Test fun mediaHoraAntesEsRecordatorio() {
        val result = NaturalTaskParser.parse("Reunión media hora antes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun cuartoDeHoraAntesEsRecordatorio() {
        val result = NaturalTaskParser.parse("Cita un cuarto de hora antes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun recuerdameMediaHoraDeAnticipacion() {
        val result = NaturalTaskParser.parse("Cita recuérdame media hora de anticipación", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // Regresión: "media hora" SIN "antes"/verbo sigue siendo DURACIÓN (30 min),
    // no recordatorio. El contexto de recordatorio es obligatorio para las
    // fracciones.
    @Test fun mediaHoraSinAntesSigueSiendoDuracion() {
        val result = NaturalTaskParser.parse("Reunión media hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
        assertNull(result.reminderOffsetMinutes)
    }

    // --- Verbo de recordatorio sin cantidad explícita (ciclo 58) ---
    // "recuérdame llamar a mamá mañana a las 3": el usuario pide un recordatorio pero
    // no dice cuánto antes. Antes el verbo quedaba como residuo en el título Y no se
    // programaba ningún recordatorio (la cita se olvidaba). Ahora se asume 30 min antes
    // (convención del proyecto) y se elimina el verbo del título.
    @Test fun verboRecordatorioSinCantidadConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("recuérdame llamar a mamá mañana a las 3 de la tarde", now, zone)
        assertEquals("llamar a mamá", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun verboAvisameSinCantidadConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("avísame pagar la luz el viernes", now, zone)
        assertEquals("pagar la luz", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun verboNoDejesQueOlvideConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("no dejes que olvide llamar al doctor mañana", now, zone)
        assertEquals("llamar al doctor", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // Sin fecha límite no se puede programar reminderAt (dueAt=null → reminderAt=null):
    // no se falsifica el offset; el verbo igualmente se limpia del título.
    @Test fun verboRecordatorioSinCantidadSinDueNoFalsificaOffset() {
        val result = NaturalTaskParser.parse("recuérdame llamar a mamá", now, zone)
        assertEquals("llamar a mamá", result.title)
        assertNull(result.reminderOffsetMinutes)
        assertNull(result.dueAt)
    }

    // El offset explícito tiene prioridad: "recuérdame 2 horas antes" NO cae en el
    // respaldo de 30 min, usa los 120 min explícitos.
    @Test fun verboRecordatorioConCantidadExplicitaUsaOffsetExplicito() {
        val result = NaturalTaskParser.parse("Reunión recuérdame 2 horas antes", now, zone)
        assertEquals(120, result.reminderOffsetMinutes)
    }

    @Test fun parsesDurationPhrase() {
        val result = NaturalTaskParser.parse("Preparar presentación durante 45 minutos", now, zone)
        assertEquals("Preparar presentación", result.title)
        assertEquals(45, result.durationMinutes)
        assertNull(result.dueAt)
    }

    @Test fun parsesStandaloneDuration() {
        val result = NaturalTaskParser.parse("Estudiar 30 minutos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
    }

    // "Nh" compacto debe reconocerse como duración en horas, no dejar "2h" en el título
    // ni interpretarse como minutos.
    @Test fun parsesCompactHoursDuration() {
        val result = NaturalTaskParser.parse("Trabajar 2h", now, zone)
        assertEquals("Trabajar", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun parsesCompactSingleHourDuration() {
        val result = NaturalTaskParser.parse("Estudiar 1h", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(60, result.durationMinutes)
    }

    // "2horas" (palabra completa) sigue funcionando y el compacto "2h" no la roba ni deja residuo.
    @Test fun compactHoursDoesNotStealFullWord() {
        val result = NaturalTaskParser.parse("Reunión 2horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // "Nh" compacto como duración NO interfiere con un recordatorio "N min antes".
    @Test fun compactHoursDurationWithReminder() {
        val result = NaturalTaskParser.parse("Estudiar 2h recuérdame 15 min antes", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(15, result.reminderOffsetMinutes)
    }

    // --- Duraciones fraccionarias sin dígitos (ciclo 14) ---
    // "media hora" y "(un) cuarto de hora" no casan con los patrones de dígitos y
    // dejaban residuo en el título + durationMinutes=null.
    @Test fun mediaHoraEsDuracionDe30Min() {
        val result = NaturalTaskParser.parse("Estudiar media hora", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun cuartoDeHoraEsDuracionDe15Min() {
        val result = NaturalTaskParser.parse("Leer un cuarto de hora", now, zone)
        assertEquals("Leer", result.title)
        assertEquals(15, result.durationMinutes)
    }

    @Test fun cuartoHoraSinUnEsDuracionDe15Min() {
        val result = NaturalTaskParser.parse("Pausa cuarto de hora", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(15, result.durationMinutes)
    }

    @Test fun mediaHoraConFechaYHoraNoInterfiere() {
        val result = NaturalTaskParser.parse("Estudiar media hora mañana a las 3pm", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "cuarto" como habitación NO debe interpretarse como duración.
    @Test fun cuartoComoHabitacionNoEsDuracion() {
        val result = NaturalTaskParser.parse("Limpiar el cuarto", now, zone)
        assertEquals("Limpiar el cuarto", result.title)
        assertNull(result.durationMinutes)
    }

    // --- Duraciones con número escrito (ciclo 85) ---
    // "dos horas"/"una hora"/"treinta minutos"/"un par de horas" caían a
    // durationMinutes=null (solo se aceptaban dígitos y "media"/"cuarto de hora"),
    // así el planificador las trataba como 10 min y "What Now" subestimaba el trabajo.
    @Test fun dosHorasEscritasEsDuracionDe120Min() {
        val result = NaturalTaskParser.parse("Estudiar dos horas", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun unaHoraEscritasEsDuracionDe60Min() {
        val result = NaturalTaskParser.parse("Leer una hora", now, zone)
        assertEquals("Leer", result.title)
        assertEquals(60, result.durationMinutes)
    }

    @Test fun treintaMinutosEscritosEsDuracionDe30Min() {
        val result = NaturalTaskParser.parse("Meditar treinta minutos", now, zone)
        assertEquals("Meditar", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun unParDeHorasEsDuracionDe120Min() {
        val result = NaturalTaskParser.parse("Reunión un par de horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // "reunión de dos horas": el conector "de" se elimina junto con la duración,
    // sin dejar residuo en el título (como ya ocurre con "de 30 min").
    @Test fun duracionEscritaConConectorDeSeLimpiaDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión de dos horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // P0 integridad de datos: "30 min" casa A LA VEZ durationMatch (numérico) y
    // writtenMatch (el patrón escrito admite dígitos), generando dos rangos
    // idénticos. Antes del fix, replaceRange aplicado dos veces sobre el mismo
    // span crasheaba con IndexOutOfBoundsException al mutar `working` (Range [17,9)
    // out of bounds for length 9). Ahora se deduplica por solapamiento y la
    // duración correcta se captura sin crash.
    @Test fun duracionNumericaConConectorNoCrashaPorMatchDuplicado() {
        val result = NaturalTaskParser.parse("reunion de 30 min", now, zone)
        assertEquals("reunion", result.title)
        assertEquals(30, result.durationMinutes)
    }

    // P0 integridad de datos: el token de duración ("2 horas") aparece DOS veces
    // en el título, pero solo la PRIMERA ocurrencia es la duración real; la segunda
    // es contenido legítimo. No debe borrarse globalmente ni corromperse el título.
    @Test fun duracionRepetidaComoContenidoPreservaSegundaOcurrencia() {
        val result = NaturalTaskParser.parse("30 min de ejercicio 30 minutos extra", now, zone)
        assertEquals(30, result.durationMinutes)
        assertTrue("La segunda ocurrencia debe preservarse: ${result.title}",
            result.title.contains("30 minutos"))
    }

    // P0 integridad de datos: duración escrita ("dos horas") repetida como
    // contenido. Solo se borra la primera; la segunda ("dos horas mas") se conserva.
    @Test fun duracionEscritaRepetidaComoContenidoPreservaSegundaOcurrencia() {
        val result = NaturalTaskParser.parse("dos horas de estudio y dos horas mas", now, zone)
        assertEquals(120, result.durationMinutes)
        assertTrue("La segunda ocurrencia debe preservarse: ${result.title}",
            result.title.contains("dos horas"))
    }

    // "a las nueve horas" es HORA de un evento, NO duración: el guard
    // timePhrasePreceding ("a las" antes) debe impedir robar "nueve horas" como
    // duración. (El parser de horas no lee "nueve" como 9, así que la frase se
    // conserva en el título; lo importante aquí es que NO se convierta en duración.)
    @Test fun horasEscritasTrasALasNoSonDuracion() {
        val result = NaturalTaskParser.parse("Vuelo a las nueve horas", now, zone)
        assertNull(result.durationMinutes)
    }

    // "en dos horas" es fecha relativa, NO duración: se consume antes y el guard
    // "en$" también lo protege. No debe quedar "dos horas" como duración falsa.
    @Test fun enDosHorasNoEsDuracionEscrita() {
        val result = NaturalTaskParser.parse("Llamar a Ana en dos horas", now, zone)
        assertNull(result.durationMinutes)
    }

    // "recuérdame dos horas antes" es recordatorio, NO duración: el patrón de
    // recordatorio (con "antes") se consume antes que la duración escrita.
    @Test fun dosHorasAntesEsRecordatorioNoDuracion() {
        val result = NaturalTaskParser.parse("Reunión recuérdame dos horas antes", now, zone)
        assertEquals(120, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun parsesMonthNameDate() {
        val result = NaturalTaskParser.parse("Entregar reporte antes del 5 de agosto", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun monthNameDateBeforeTodayRollsToNextYear() {
        val result = NaturalTaskParser.parse("Llamar al dentista el 5 de julio", now, zone)
        assertEquals("Llamar al dentista", result.title)
        assertEquals(LocalDate.of(2027, 7, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun detectsCategoryByContext() {
        val result = NaturalTaskParser.parse("Comprar leche en el supermercado", now, zone)
        assertEquals("compras", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    @Test fun plainTextWithoutSignalsHasLowConfidence() {
        val result = NaturalTaskParser.parse("Revisar la factura del teléfono", now, zone)
        assertEquals("", result.category)
        assertEquals(0.35f, result.confidence, 0.001f)
    }

    @Test fun explicitTimeHasFullConfidence() {
        val result = NaturalTaskParser.parse("Llamar a Ana a las 9:30", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun highPriorityWithoutDateStaysInboxCandidate() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
        assertEquals("trabajo", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    // ── Categoría explícita por etiqueta "#cat"/"@cat" ──

    @Test fun explicitHashTagSetsCategoryAndCleansTitle() {
        val result = NaturalTaskParser.parse("Comprar leche #compras", now, zone)
        assertEquals("Comprar leche", result.title)
        assertEquals("compras", result.category)
    }

    @Test fun explicitAtTagSetsCategoryAndCleansTitle() {
        val result = NaturalTaskParser.parse("Llamar a Ana @trabajo", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals("trabajo", result.category)
    }

    @Test fun explicitTagOverridesKeywordInference() {
        // "comprar"/"leche" inferirían "compras", pero el usuario pidió "personal".
        val result = NaturalTaskParser.parse("Comprar leche #personal", now, zone)
        assertEquals("Comprar leche", result.title)
        assertEquals("personal", result.category)
    }

    @Test fun unknownTagStaysInTitleAndDoesNotSetCategory() {
        // "#proyecto" no es un nombre de categoría: queda como contenido del usuario
        // y la categoría se infiere normalmente por keywords (trabajo).
        val result = NaturalTaskParser.parse("Reunión #proyecto", now, zone)
        assertEquals("Reunión #proyecto", result.title)
        assertEquals("trabajo", result.category)
    }

    @Test fun explicitTagCombinedWithDateAndTime() {
        val result = NaturalTaskParser.parse("Reunión de equipo #trabajo mañana a las 10", now, zone)
        assertEquals("Reunión de equipo", result.title)
        assertEquals("trabajo", result.category)
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // ── Regresión BUG1: fecha numérica sin año en el pasado rueda al año siguiente ──

    @Test fun numericDateWithoutYearInPastRollsToNextYear() {
        val result = NaturalTaskParser.parse("Pagar factura 5/3", now, zone)
        assertEquals("Pagar factura", result.title)
        // 5/3 = 5 de marzo; hoy es 29-jul-2026 → debe rodar a 2027.
        assertEquals(LocalDate.of(2027, 3, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithoutYearInFutureStaysThisYear() {
        val result = NaturalTaskParser.parse("Pagar factura 15/12", now, zone)
        assertEquals(LocalDate.of(2026, 12, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateTodayDoesNotRollForward() {
        val result = NaturalTaskParser.parse("Pagar factura 29/7", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithExplicitYearNeverRolls() {
        // Año explícito en el pasado: no se interpreta como futuro (el usuario lo dijo).
        val result = NaturalTaskParser.parse("Revisar contrato 10/1/2024", now, zone)
        assertEquals(LocalDate.of(2024, 1, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── Fechas imposibles: recuperación honesta en vez de pérdida ──
    // "el 29 de febrero" (sin año, año no bisiesto) → próximo 29 de feb real.
    // "el 31 de abril" / "el 30 de febrero" → último día válido del mes nombrado.
    // Antes: LocalDate.of lanzaba → dueAt=null y la frase quedaba como título basura.

    @Test fun feb29SinAnioRuedaAProximoBisiesto() {
        // hoy = 29-jul-2026 (no bisiesto). "el 29 de febrero" → 29-feb-2028.
        val result = NaturalTaskParser.parse("Renovación el 29 de febrero", now, zone)
        assertEquals("Renovación", result.title)
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun feb29SinAnioSoloFechaRuedaAProximoBisiesto() {
        val result = NaturalTaskParser.parse("el 29 de febrero", now, zone)
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun feb29ConAnioExplicitoNoBisiestoClampaA28() {
        // Año explícito 2027 (no bisiesto): se respeta el año, día 29→28.
        val result = NaturalTaskParser.parse("Cita el 29 de febrero de 2027", now, zone)
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dia31DeAbrilClampaA30() {
        // Abril tiene 30 días: "el 31 de abril" → 30-abr. 30-abr-2026 ya pasó → 2027.
        val result = NaturalTaskParser.parse("Entrega el 31 de abril", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2027, 4, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dia30DeFebreroClampaA28() {
        val result = NaturalTaskParser.parse("Pago el 30 de febrero", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── Regresión BUG2: "esta mañana/tarde/noche" ──

    @Test fun estaNocheSetsTonightCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta noche", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaTardeSetsAfternoonCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta tarde", now, zone)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun estaMananaIsNotMistakenForTomorrow() {
        // "esta mañana" contiene "mañana"; antes se interpretaba como "el día de mañana".
        val result = NaturalTaskParser.parse("Pagar factura esta mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // ── "el día de mañana/hoy": pleonasmo coloquial de "mañana/hoy" ──
    // La frase completa debe consumirse; antes el borrado de "mañana"/"hoy" dejaba el
    // residuo "el día de" en el título (p. ej. "reunión el día de" en vez de "reunión"),
    // que es contenido capturado degradado (P1: integridad de datos).

    @Test fun elDiaDeMananaNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("reunión el día de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaDeHoyNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("reunión el día de hoy", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun paraElDiaDeMananaNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("reunión para el día de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun explicitTimeOverridesPartOfDayCanonicalTime() {
        val result = NaturalTaskParser.parse("Pagar factura esta noche a las 22:15", now, zone)
        assertEquals(LocalTime.of(22, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun partOfDayIsAccentTolerant() {
        val result = NaturalTaskParser.parse("Pagar factura esta manana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // ── Regresión: "manana" (sin tilde) debe reconocerse como fecha = mañana ──
    // Antes solo "mañana" (con tilde) se reconocía como fecha; "manana" caía a
    // dueAt=null (tarea olvidada) o se agendaba HOY (día equivocado). La escritura
    // sin tilde es muy común al escribir rápido en el móvil.

    @Test fun mananaWithoutTildeIsTomorrow() {
        val result = NaturalTaskParser.parse("llamar a Ana manana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaWithoutTildeWithTime() {
        val result = NaturalTaskParser.parse("reunion manana a las 9", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun pasadoMananaWithoutTilde() {
        val result = NaturalTaskParser.parse("entrega pasado manana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── Regresión: meridiem sin tilde ("12 de la manana" = 00:00, no 12:00) ──
    // Antes "12 de la manana" se agendaba a 12:00 (mediodía) mientras "12 de la
    // mañana" caía a 00:00 (madrugada) por una asimetría en la comparación del
    // meridiem (el literal "delamanaana" con doble 'a' nunca casaba).

    @Test fun twelveDeLaMananaIsMidnight() {
        val result = NaturalTaskParser.parse("cita a las 12 de la manana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaPartOfDayWithoutTildeMatchesWithTilde() {
        val sinTilde = NaturalTaskParser.parse("cita a las 9 de la manana", now, zone)
        val conTilde = NaturalTaskParser.parse("cita a las 9 de la mañana", now, zone)
        assertEquals(DateRules.toLocalTime(conTilde.dueAt!!, zone), DateRules.toLocalTime(sinTilde.dueAt!!, zone))
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(sinTilde.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(sinTilde.dueAt, zone))
    }

    @Test fun unaDelMediodiaWithoutTildeIs13h() {
        val result = NaturalTaskParser.parse("rendir examen a la una del mediodia", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // ── Regresión: "de madrugada"/"de noche"/"de tarde" (sin "la") ──
    // Adverbios temporales muy comunes ("salir de madrugada", "trabajar de noche").
    // Antes no casaban con el conector "de" suelto: la tarea quedaba sin hora
    // (dueAt=null) y la frase quedaba como residuo en el título.

    @Test fun deMadrugadaSetsCanonicalHour() {
        val result = NaturalTaskParser.parse("salir de madrugada", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertEquals("salir", result.title)
    }

    @Test fun deNocheSetsCanonicalHour() {
        val result = NaturalTaskParser.parse("trabajar de noche", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertEquals("trabajar", result.title)
    }

    @Test fun deTardeSetsCanonicalHour() {
        val result = NaturalTaskParser.parse("jugar tenis de tarde", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deMadrugadaWithDateKeepsDate() {
        val result = NaturalTaskParser.parse("salir de madrugada mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // ── Regresión BUG4: "urgente" como palabra inicial (sin prefijo !/#) ──

    @Test fun leadingUrgenteSetsUrgentPriority() {
        val result = NaturalTaskParser.parse("urgente enviar documento mañana", now, zone)
        assertEquals(TaskPriority.URGENT, result.priority)
        // "urgente" se limpia del título.
        assertEquals("enviar documento", result.title)
    }

    @Test fun midSentenceUrgenteDoesNotSetPriority() {
        // "es urgente" a mitad de frase NO debe activar URGENT (evita falsos positivos).
        val result = NaturalTaskParser.parse("Revisar si es urgente el documento", now, zone)
        assertEquals(TaskPriority.NORMAL, result.priority)
        assertEquals("Revisar si es urgente el documento", result.title)
    }

    // ── Regresión BUG4b: "urgente"/"importante" como palabra FINAL (sufijo de prioridad) ──

    @Test fun trailingUrgenteSetsUrgentPriority() {
        val result = NaturalTaskParser.parse("Llamar mamá urgente", now, zone)
        assertEquals(TaskPriority.URGENT, result.priority)
        assertEquals("Llamar mamá", result.title)
    }

    @Test fun trailingImportanteSetsHighPriority() {
        val result = NaturalTaskParser.parse("Enviar factura importante", now, zone)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertEquals("Enviar factura", result.title)
    }

    @Test fun trailingUrgenteWithPunctuationSetsUrgentPriority() {
        val result = NaturalTaskParser.parse("Comprar leche urgente!", now, zone)
        assertEquals(TaskPriority.URGENT, result.priority)
        assertEquals("Comprar leche", result.title)
    }

    @Test fun negatedTrailingUrgenteDoesNotSetPriority() {
        // "no es urgente" como palabra final NO debe activar URGENT (negación).
        val result = NaturalTaskParser.parse("Revisar el documento, no es urgente", now, zone)
        assertEquals(TaskPriority.NORMAL, result.priority)
        assertEquals("Revisar el documento, no es urgente", result.title)
    }

    // ── Regresión BUG3: números escritos en expresiones relativas ──

    @Test fun writtenNumberRelativeHoursParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar a cliente en dos horas", now, zone)
        assertEquals("Llamar a cliente", result.title)
        assertEquals(now + 2 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberRelativeDaysParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar propuesta en tres días", now, zone)
        assertEquals("Enviar propuesta", result.title)
        assertEquals(now + 3 * 24 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberUnaHoraParsesDueAt() {
        val result = NaturalTaskParser.parse("Revisar correo en una hora", now, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(now + 1 * 60 * 60_000L, result.dueAt)
    }

    @Test fun writtenNumberUnDiaParsesDueAt() {
        val result = NaturalTaskParser.parse("Comprar pan en un día", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(now + 1 * 24 * 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeWrittenNumberParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión dentro de dos horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 2 * 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeDigitsParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión dentro de 30 minutos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
    }

    // --- Fecha relativa fraccionaria (ciclo 90) ---
    // "en media hora" es un punto en el tiempo (ahora + 30 min), NO una duración.
    // Antes "media hora" caía a fractionalDurationPattern → dueAt=null,
    // durationMinutes=30 y el prefijo "en" quedaba como residuo ("llamar en").
    @Test fun enMediaHoraEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Llamar a Ana en media hora", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enUnCuartoDeHoraEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Llamar a Ana en un cuarto de hora", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(now + 15 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enCuartoDeHoraSinUnEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Cita en cuarto de hora", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 15 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeMediaHoraEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Reunión dentro de media hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun deAquiAMediaHoraEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Cita de aquí a media hora", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // Regresión: "media hora" SIN prefijo relativo sigue siendo DURACIÓN (30 min).
    // El prefijo "en/dentro de/de aquí a" es obligatorio para la fecha relativa.
    @Test fun mediaHoraSinPrefijoSigueSiendoDuracion() {
        val result = NaturalTaskParser.parse("Reunión media hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
        assertNull(result.dueAt)
    }

    // --- Fecha relativa fraccionaria COMPUESTA (ciclo 94) ---
    // "en una hora y media" = 60 + 30 = 90 min. Antes [relativePattern] robaba solo
    // "en una hora" (60) y dejaba "y media" como residuo ("llamar y media"),
    // agendando 30 min antes de lo pedido.
    @Test fun enUnaHoraYMediaEsFechaRelativaDe90Min() {
        val result = NaturalTaskParser.parse("Llamar a Ana en una hora y media", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(now + 90 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enDosHorasYMediaEsFechaRelativaDe150Min() {
        val result = NaturalTaskParser.parse("Reunión en dos horas y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 150 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enUnaHoraYCuartoEsFechaRelativaDe75Min() {
        val result = NaturalTaskParser.parse("Cita en una hora y cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 75 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun en3HorasYMediaDigitosEsFechaRelativaDe210Min() {
        val result = NaturalTaskParser.parse("Vuelo en 3 horas y media", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(now + 210 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeUnaHoraYMediaEsFechaRelativaDe90Min() {
        val result = NaturalTaskParser.parse("Llamar dentro de una hora y media", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 90 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "en tres cuartos de hora" = 3 × 15 = 45 min. Antes no casaba ningún patrón y
    // la tarea quedaba sin vencimiento (dueAt=null).
    @Test fun enTresCuartosDeHoraEsFechaRelativaDe45Min() {
        val result = NaturalTaskParser.parse("Reunión en tres cuartos de hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enDosCuartosDeHoraEsFechaRelativaDe30Min() {
        val result = NaturalTaskParser.parse("Pausa en dos cuartos de hora", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Fecha relativa multi-cuarto + y cuarto (ciclo 103) ---
    // "en tres cuartos de hora y cuarto" = 3 + 1 = 4 cuartos = 60 min. Antes el sufijo
    // "y cuarto" no se consumía: [multiQuarterRelativePattern] robaba solo "en tres
    // cuartos de hora" (+45) y dejaba "y cuarto" como residuo en el título ("llamar y
    // cuarto"), agendando 15 min antes de lo pedido.
    @Test fun enTresCuartosDeHoraYCuartoEsFechaRelativaDe60Min() {
        val result = NaturalTaskParser.parse("Llamar en tres cuartos de hora y cuarto", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enDosCuartosDeHoraYCuartoEsFechaRelativaDe45Min() {
        val result = NaturalTaskParser.parse("Pausa en dos cuartos de hora y cuarto", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Fecha relativa compuesta + cuartos plurales (ciclo 103) ---
    // "en una hora y tres cuartos" = 60 + 45 = 105 min. Antes el plural "tres cuartos"
    // no casaba en [compoundFractionalRelativePattern] (solo admitía media/cuarto) y
    // caía a [relativePattern], que robaba "en una hora" (+60) dejando "y tres cuartos"
    // como residuo ("cita y tres cuartos"), agendando 45 min antes de lo pedido.
    @Test fun enUnaHoraYTresCuartosEsFechaRelativaDe105Min() {
        val result = NaturalTaskParser.parse("Cita en una hora y tres cuartos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 105 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enDosHorasYDosCuartosEsFechaRelativaDe150Min() {
        val result = NaturalTaskParser.parse("Reunión en dos horas y dos cuartos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 150 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Fecha relativa VAGA "en un rato" (ciclo 104) ---
    // "en un rato" no casaba ningún patrón → dueAt=null y la tarea quedaba sin
    // recordatorio (olvidada). Heurística honesta: +1 h.
    @Test fun enUnRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar a mamá en un rato", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeUnRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Pausa dentro de un rato", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun deAquiAUnRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita de aquí a un rato", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Fecha relativa VAGA familia "un momento"/"al rato"/"pasado un rato" (ciclo 105) ---
    // Extensiones cotidianas de la familia vaga de futuro (c.104): mismas frases imprecisas
    // que antes dejaban dueAt=null (tarea olvidada). Heurística honesta: +1 h.
    @Test fun enUnMomentoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en un momento", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeUnMomentoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Pausa dentro de un momento", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun alRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita al rato", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun pasadoUnRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar pasado un rato", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    // --- "enseguida"/"en seguida" (adverbio puro de inmediatez, sin "un rato") (ciclo 106) ---
    // No casa ningún otro patrón (vagueRelative exige "un rato"/"un momento"; relative exige
    // unidad) → antes dueAt=null + residuo en el título. +1 h (misma heurística honesta).
    @Test fun enseguidaEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Avisar enseguida", now, zone)
        assertEquals("Avisar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enSeguidaSeparadoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en seguida", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Fecha relativa fraccionaria + cuarto (ciclo 101) ---
    // "en media hora y cuarto" = 30 + 15 = 45 min. Antes [fractionalRelativePattern]
    // robaba solo "en media hora" (+30) y dejaba "y cuarto" como residuo en el título
    // ("cita en media hora y cuarto" → título "cita y cuarto", vencimiento 12:30 en vez
    // de 12:45), agendando 15 min antes de lo pedido y ensuciando el título.
    @Test fun enMediaHoraYCuartoEsFechaRelativaDe45Min() {
        val result = NaturalTaskParser.parse("Cita en media hora y cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enUnCuartoDeHoraYCuartoEsFechaRelativaDe30Min() {
        val result = NaturalTaskParser.parse("Cita en un cuarto de hora y cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeMediaHoraYCuartoEsFechaRelativaDe45Min() {
        val result = NaturalTaskParser.parse("Llamar dentro de media hora y cuarto", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun deAquiAMediaHoraYCuartoEsFechaRelativaDe45Min() {
        val result = NaturalTaskParser.parse("Nos vemos de aquí a media hora y cuarto", now, zone)
        assertEquals("Nos vemos", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun writtenNumberUpToTwelveParsesDueAt() {
        val result = NaturalTaskParser.parse("Entregar en doce horas", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(now + 12 * 60 * 60_000L, result.dueAt)
    }

    // Números escritos > 30 y compuestos: antes "cuarenta y cinco"/"cincuenta"/
    // "sesenta"/"veinticinco" no se reconocían → dueAt=null (tarea olvidada, P1).
    @Test fun writtenCompoundNumberRelativeParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar en cuarenta y cinco minutos", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
    }

    @Test fun writtenTensRelativeParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión en cincuenta minutos", now, zone)
        assertEquals(now + 50 * 60_000L, result.dueAt)
        val sesenta = NaturalTaskParser.parse("Llamar en sesenta minutos", now, zone)
        assertEquals(now + 60 * 60_000L, sesenta.dueAt)
        val noventa = NaturalTaskParser.parse("Llamar en noventa minutos", now, zone)
        assertEquals(now + 90 * 60_000L, noventa.dueAt)
    }

    @Test fun writtenCompoundNoLeakLowUnitAsDuration() {
        // "cuarenta y cinco" NO debe dejar "cinco" como duración residual.
        val result = NaturalTaskParser.parse("Llamar en cuarenta y cinco minutos con Juan", now, zone)
        assertEquals("Llamar con Juan", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun writtenTwentiesSingleWordParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar en veinticinco minutos", now, zone)
        assertEquals(now + 25 * 60_000L, result.dueAt)
    }

    @Test fun writtenCompoundTensParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar en treinta y cinco minutos", now, zone)
        assertEquals(now + 35 * 60_000L, result.dueAt)
        val setentaYCinco = NaturalTaskParser.parse("Llamar en setenta y cinco minutos", now, zone)
        assertEquals(now + 75 * 60_000L, setentaYCinco.dueAt)
    }

    @Test fun writtenCompoundDurationParsesMinutes() {
        val result = NaturalTaskParser.parse("Trabajar cuarenta y cinco minutos", now, zone)
        assertEquals("Trabajar", result.title)
        assertEquals(45, result.durationMinutes)
    }

    @Test fun writtenTensReminderParsesOffset() {
        val result = NaturalTaskParser.parse("Vuelo recuérdame treinta minutos antes", now, zone)
        assertEquals(30, result.reminderOffsetMinutes)
        val comp = NaturalTaskParser.parse("Vuelo recuérdame cuarenta y cinco minutos antes", now, zone)
        assertEquals(45, comp.reminderOffsetMinutes)
    }

    @Test fun digitRelativeStillParsesAfterWrittenNumberSupport() {
        // Regresión: el soporte de números escritos no debe romper los dígitos.
        val result = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
    }

    // Fecha relativa en DÍAS + hora explícita/canónica: la hora del día debe respetarse
    // (antes se ignoraba y se usaba la hora actual del timestamp). Solo aplica a días:
    // minutos/horas son eventos cercanos donde la hora actual es intencional.
    @Test fun relativeDaysRespectsExplicitTime() {
        val result = NaturalTaskParser.parse("Entregar informe dentro de 3 días a las 9", now, zone)
        assertEquals("Entregar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun relativeDaysRespectsPrimeraHora() {
        val result = NaturalTaskParser.parse("Entregar informe en 2 días a primera hora", now, zone)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun relativeDaysWithoutTimeKeepsCurrentTime() {
        // Sin hora explícita, se conserva el comportamiento previo (now + N días).
        val result = NaturalTaskParser.parse("Comprar pan en un día", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(now + 1 * 24 * 60 * 60_000L, result.dueAt)
    }

    // --- Meridiem "de la tarde/noche/mañana" (antes se ignoraba: hora errónea + título sucio) ---

    @Test fun deLaTardeAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Cita hoy a las 4 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaNocheAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Reunión hoy a las 7 de la noche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(19, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaMananaKeepsAmHour() {
        val result = NaturalTaskParser.parse("Desayuno hoy a las 9 de la mañana", now, zone)
        assertEquals("Desayuno", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaTardeWithMinutesAppliesPmOffset() {
        val result = NaturalTaskParser.parse("Cita hoy a las 4:30 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaTardeDoesNotBreakTitle() {
        val result = NaturalTaskParser.parse("Cita a las 9 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Frases "al mediodía" / "a la medianoche" limpias en el título ---

    @Test fun alMediodiaParsesNoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Almuerzo mañana al mediodía", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.NOON, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLaMedianocheParsesMidnightAndCleanTitle() {
        val result = NaturalTaskParser.parse("Entregar tarea a la medianoche", now, zone)
        assertEquals("Entregar tarea", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "a la una" (hora 1, femenino singular) (ciclo 94b/c) ---
    // La hora 1 se dice "a la una" (no "a las 1"). Antes no había patrón para esa
    // forma, así que "reunión a la una" caía sin dueAt y con "a la una" como residuo
    // del título → cita olvidada. Además "a la una del mediodía" caía a la canónica
    // NOON (12:00) en vez de 13:00.

    @Test fun aLaUnaParsesOneOclockAndCleanTitle() {
        val result = NaturalTaskParser.parse("Reunión a la una", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(1, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaYMediaParsesHalfPastOne() {
        val result = NaturalTaskParser.parse("Cita a la una y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(1, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaYCuartoParsesQuarterPastOne() {
        val result = NaturalTaskParser.parse("Cita a la una y cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(1, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaDeLaTardeParsesOnePm() {
        val result = NaturalTaskParser.parse("Reunión a la una de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaDelMediodiaParsesOnePmNotNoon() {
        val result = NaturalTaskParser.parse("Almuerzo a la una del mediodía", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaColonMinutesParsesCorrectly() {
        val result = NaturalTaskParser.parse("Cita a la una:30", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(1, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Regresión BUG: "de la mañana"/"por la mañana" como marcador de hora NO
    //     debe interpretarse como la fecha "mañana" (antes "Reunión a las 9 de la
    //     mañana" se programaba para MAÑANA en vez de HOY → reunión perdida el mismo día). ---

    @Test fun deLaMananaWithoutDateStaysToday() {
        val result = NaturalTaskParser.parse("Reunión a las 9 de la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun porLaMananaWithoutDateStaysToday() {
        val result = NaturalTaskParser.parse("Llamar a mamá por la mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaPorLaMananaStillResolvesTomorrow() {
        val result = NaturalTaskParser.parse("Hacer X mañana por la mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "esta mañana" no debe dejar "esta" huérfano en el título ---

    @Test fun estaMananaCleanedFullyFromTitle() {
        val result = NaturalTaskParser.parse("Correo al jefe esta mañana", now, zone)
        assertEquals("Correo al jefe", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Contexto PM de parte del día aplicado a hora sin meridiem (ciclo 6) ---

    @Test fun estaTardeConHoraSinMeridiemAplicaPm() {
        val result = NaturalTaskParser.parse("Reunión esta tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaNocheConHoraSinMeridiemAplicaPm() {
        val result = NaturalTaskParser.parse("Llamar hoy esta noche a las 9", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLaTardeSueltaDefineHoraYNoFuerzaFecha() {
        val result = NaturalTaskParser.parse("Ver a juan mañana a la tarde", now, zone)
        assertEquals("Ver a juan", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deLaTardeSueltaDaHoraCanonicaHoy() {
        val result = NaturalTaskParser.parse("Jugar tenis de la tarde", now, zone)
        assertEquals("Jugar tenis", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "12 de la noche" = medianoche (00:00), no 12:00 del mediodía (ciclo 6) ---

    @Test fun doceDeLaNocheEsMedianoche() {
        val result = NaturalTaskParser.parse("Fiesta a las 12 de la noche", now, zone)
        assertEquals("Fiesta", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLaMadrugadaEsAmYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Despertar a las 4 de la madrugada", now, zone)
        assertEquals("Despertar", result.title)
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- horas escritas ("a las nueve", "doce de la noche") (ciclo 88) ---
    // "nueve"/"diez"/"doce" no se resolvían como hora: quedaban en el título y/o se
    // agendaban a la canónica de la parte del día (doce de la noche → 21:00, no 00:00).

    @Test fun aLasNueveEscritaResuelveHoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a las nueve", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasDiezDeLaNocheEscritaEs22h() {
        val result = NaturalTaskParser.parse("Cita a las diez de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(22, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasDosDeLaTardeEscritaEs14h() {
        val result = NaturalTaskParser.parse("Reunión a las dos de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasOchoYMediaEscritaEs830() {
        val result = NaturalTaskParser.parse("Cita a las ocho y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(8, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveYCuartoEscritaEs915() {
        val result = NaturalTaskParser.parse("Reunión a las nueve y cuarto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun doceDeLaNocheEscritoEsMedianoche() {
        val result = NaturalTaskParser.parse("Compra doce de la noche", now, zone)
        assertEquals("Compra", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveDeLaNocheEscritoEs21h() {
        val result = NaturalTaskParser.parse("Cena nueve de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ochoDeLaMananaEscritoEs8h() {
        val result = NaturalTaskParser.parse("Cita ocho de la mañana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun dosDeLaTardeEscritoEs14h() {
        val result = NaturalTaskParser.parse("Llamada dos de la tarde", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasDiezHorasEscritaSigueSiendoHoraNoDuracion() {
        val result = NaturalTaskParser.parse("Vuelo a las diez horas", now, zone)
        assertNull(result.durationMinutes)
        assertEquals("Vuelo", result.title)
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "mañana por la tarde/noche/mañana" (ciclo 21) ---
    // "por la" no era conector reconocido de parte del día (solo "a la"/"de la"), así
    // "mañana por la tarde" dejaba "por la tarde" en el título Y usaba 09:00 en vez de
    // la hora canónica de la tarde. Frase cotidianísima en español.

    @Test fun mananaPorLaTardeDefineFechaYHoraCanonicaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión mañana por la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaPorLaNocheEs21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a mamá mañana por la noche", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaPorLaMananaEs9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Enviar informe mañana por la mañana", now, zone)
        assertEquals("Enviar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaPorLaTardeConHoraSinMeridiemAplicaPm() {
        val result = NaturalTaskParser.parse("Reunión mañana por la tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "a las 24" / "24:00" = medianoche (ciclo 7) ---

    @Test fun aLas24EsMedianocheYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan a las 24", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas24ConMinutosEsMedianoche() {
        val result = NaturalTaskParser.parse("Reunión a las 24:00", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas24DeLaNocheLimpiaTituloYEsMedianoche() {
        val result = NaturalTaskParser.parse("Cena a las 24 de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "y media" / "y cuarto": fracción sub-hora cotidiana en español ---
    // "a las 9 y media" antes dejaba "y media" en el título y la hora en 09:00
    // (reunión/cita 30 minutos mal programados). Ahora → 09:30 con título limpio.

    @Test fun aLas9YMediaEs9_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 9 y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas3YCuartoEs3_15YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3 y cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(3, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun yMediaConDeLaTardeAplicaPm() {
        val result = NaturalTaskParser.parse("Cita a las 9 y media de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun yMediaConDeLaNocheAplicaPm() {
        val result = NaturalTaskParser.parse("Cita a las 7 y media de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(19, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "a las N menos cuarto/cinco/diez/veinte/veinticinco" (ciclo 114) ---
    // Expresiones analógicas de reloj: "a las 3 menos cuarto" = 02:45. Antes la
    // fracción negativa no se reconocía: la hora quedaba como 03:00 y "menos cuarto"
    // como residuo en el título. Ahora se resta la fracción a la hora con wrap 24 h.
    @Test fun aLas3MenosCuartoEs2_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3 menos cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(2, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9MenosCuartoEs8_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a las 9 menos cuarto", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(8, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas12MenosCuartoWrapA11_45() {
        // 12:00 menos cuarto hace wrap a 11:45 (no 11:45 negativo).
        val result = NaturalTaskParser.parse("Cita a las 12 menos cuarto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(11, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas3MenosCuartoDeLaTardeAplicaPm() {
        val result = NaturalTaskParser.parse("Cita a las 3 menos cuarto de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 45), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLas10Menos5DigitosEs9_55YLimpiaTitulo() {
        // Forma numérica de la fracción negativa: "a las 10 menos 5" = 09:55.
        val result = NaturalTaskParser.parse("Cita a las 10 menos 5", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 55), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas10MenosDiezEs9_50YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 10 menos diez", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 50), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas10MenosVeinticincoEs9_35YLimpiaTitulo() {
        // "veinticinco" contiene "cinco": el orden del when debe priorizarlo.
        val result = NaturalTaskParser.parse("Cita a las 10 menos veinticinco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 35), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasUnaMenosCuartoDelMediodiaEs12_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a la una menos cuarto del mediodia", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(12, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun yMediaConDeLaMadrugadaEsAm() {
        val result = NaturalTaskParser.parse("Cita a las 4 y media de la madrugada", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(4, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun yMediaConPmExplicitoAplicaPm() {
        val result = NaturalTaskParser.parse("Cita a las 9 y media pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun yMediaConAmExplicitoEsAm() {
        val result = NaturalTaskParser.parse("Cita a las 9 y media am", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "en la tarde/noche/mañana": forma caribeña/hispanoamericana ---
    // "en la tarde" no era conector reconocido de parte del día (solo "a la"/"de la"/"por la"),
    // así "hoy en la tarde" caía a 09:00 y "en la tarde" quedaba como residuo en el título.
    // Forma propia de la zona de la app (America/Santo_Domingo).

    @Test fun hoyEnLaTardeEs15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión hoy en la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaEnLaNocheEsManana21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar mañana en la noche", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enLaMananaEs9hYLimpiaTituloSinFecha() {
        // "en la mañana" sin otro marcador de fecha → hoy 09:00 (mañana no se cuenta como
        // fecha porque va precedida del conector "en la").
        val result = NaturalTaskParser.parse("Reunión en la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enLaTardeConHoraSinMeridiemAplicaPm() {
        // "en la tarde" aporta contexto PM: "a las 4" → 16:00.
        val result = NaturalTaskParser.parse("Reunión en la tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Parte del día COMPACTA (coloquial, sin conector) — ciclo 109 ---
    // "hoy tarde"/"hoy noche"/"mañana tarde"/"mañana noche"/"pasado mañana tarde":
    // forma abreviada de "hoy en la tarde". Antes: hora 09:00 (errónea) + residuo en título.

    @Test fun hoyTardeEs15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan hoy tarde", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hoyNocheEs21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a mamá hoy noche", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaTardeEsManana15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión mañana tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaNocheEsManana21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pagar factura mañana noche", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun pasadoMananaTardeEsPasadoManana15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita pasado mañana tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Paridad sin tilde del compacto "día + parte del día" (c.111) ---
    // "manana" (sin tilde) debe comportarse igual que "mañana" en las formas
    // compactas, incluyendo fecha correcta, hora canónica y título limpio.

    @Test fun mananaSinTildeTardeEsManana15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión manana tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mananaSinTildeNocheEsManana21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pagar factura manana noche", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun pasadoMananaSinTildeNocheEsPasadoManana21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita pasado manana noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antepasadoMananaSinTildeMadrugadaEsDosDias4hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Vuelo antepasado manana madrugada", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun compactTardeConHoraSinMeridiemAplicaPm() {
        // "hoy tarde" aporta contexto PM: "a las 4" → 16:00.
        val result = NaturalTaskParser.parse("Reunión hoy tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hoyMadrugadaEs4hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Vuelo hoy madrugada", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Ayer / anteayer / antier compactos (simetría de "hoy tarde") ---
    // "ayer tarde"/"ayer noche"/"ayer madrugada" son tan cotidianos como "hoy tarde"
    // al capturar eventos pasados. Antes la asimetría los dejaba en 09:00 con la parte
    // del día como residuo en el título (cita pasada mal agendada y mal titulada).

    @Test fun ayerTardeEsAyer15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión ayer tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ayerNocheEsAyer21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a mamá ayer noche", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ayerMadrugadaEsAyer4hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Vuelo ayer madrugada", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun anteayerTardeEsAnteayer15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión anteayer tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun anteayerNocheEsAnteayer21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita anteayer noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "antier" = variante coloquial hispanoamericana de "anteayer": misma resolución.

    @Test fun antierTardeEsAnteayer15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión antier tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antierNocheEsAnteayer21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita antier noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ayerTardeConHoraSinMeridiemAplicaPm() {
        // "ayer tarde" aporta contexto PM: "a las 4" → 16:00 (simétrico de "hoy tarde a las 4").
        val result = NaturalTaskParser.parse("Reunión ayer tarde a las 4", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "anoche"/"antenoche" (palabra única = "ayer noche"/"anteayer noche") ---
    // Más cotidiana aún que la forma compacta "ayer noche". Antes: dueAt=null + residuo,
    // y "anoche a las 10" agendaba HOY 10:00 (cita pasada en el futuro, P1 grave).

    @Test fun anocheEsAyer21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión anoche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun anocheConHoraAplicaPmYFechaAyer() {
        // "anoche a las 10" → AYER 22:00 (no HOY 10:00). El contexto PM de "noche" + fecha ayer.
        val result = NaturalTaskParser.parse("Reunión anoche a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(22, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antenocheEsAnteayer21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión antenoche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antenocheConHoraAplicaPmYFechaAnteayer() {
        // "antenoche a las 9" → ANTEAYER 21:00 (no HOY 09:00).
        val result = NaturalTaskParser.parse("Reunión antenoche a las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Limpieza de prefijos/sufijos de día de la semana (ciclo 8) ---
    // "del jueves", "el viernes que viene", "el miércoles próximo" dejaban
    // residuos ("del", "que viene", "próximo") en el título.

    @Test fun delWeekdayLimpiaPrefijoDel() {
        val result = NaturalTaskParser.parse("Reunión del jueves", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delWeekdayConHoraLimpiaPrefijoDel() {
        val result = NaturalTaskParser.parse("Reunión a las 3pm del jueves", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elWeekdayQueVieneLimpiaSufijo() {
        val result = NaturalTaskParser.parse("Llamar a mamá el viernes que viene", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elWeekdayProximoLimpiaSufijo() {
        val result = NaturalTaskParser.parse("Ir al dentista el miércoles próximo", now, zone)
        assertEquals("Ir al dentista", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "próximo" sin tilde debe forzar +7 cuando hoy es ese día (ciclo N) ---
    // El modificador "próximo"/"proximo" (con o sin tilde) significa "la PRÓXIMA
    // ocurrencia", incluso si hoy es ese día. Dicho en viernes, "el proximo viernes"
    // (escritura rápida sin tilde, habitual en móvil) caía en HOY en lugar de +7:
    // la cita se agendaba una semana antes y el recordatorio disparaba 7d temprano.
    // La rama acentuada ("próximo") ya forzaba +7; la sin tilde no (P1).
    @Test fun proximoSinTildeViernesHoyFuerzaProximaSemana() {
        // 8:00 (antes de la hora canónica 09:00): sin el fix, la cita caía en HOY.
        val viernesNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.of(8, 0), zone)
        val r = NaturalTaskParser.parse("Ir al dentista el proximo viernes", viernesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun proximoSinTildeSufijoFuerzaProximaSemana() {
        val viernesNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.of(8, 0), zone)
        val r = NaturalTaskParser.parse("Ir al dentista el viernes proximo", viernesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun proximoConTildeSigueForzandoProximaSemana() {
        val viernesNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.of(8, 0), zone)
        val r = NaturalTaskParser.parse("Ir al dentista el próximo viernes", viernesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(r.dueAt!!, zone))
    }


    @Test fun delWeekdayQueVieneConHoraLimpiaTodo() {
        val result = NaturalTaskParser.parse("Clase de yoga del viernes que viene a las 8", now, zone)
        assertEquals("Clase de yoga", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elWeekdaySinSufijoSigueFuncionando() {
        val result = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun primeraHoraInterpretaInicioJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Ir al dentista mañana a primera hora", now, zone)
        assertEquals("Ir al dentista", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun primeraHoraSinFechaUsaHoy() {
        val result = NaturalTaskParser.parse("Llamar a Ana a primera hora", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun primeraHoraDeLaMananaSeLimpiaCompleta() {
        val result = NaturalTaskParser.parse("Reunión del jueves a primera hora de la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun primeraHoraNoAnadeResiduo() {
        val result = NaturalTaskParser.parse("Enviar el reporte a primera hora", now, zone)
        assertEquals("Enviar el reporte", result.title)
        assertFalse(result.title.contains("primera", ignoreCase = true))
        assertFalse(result.title.contains("hora", ignoreCase = true))
    }

    // Rango horario "de H1 a H2 [horas]": la ventana se interpreta como duración y no
    // deja residuo. Antes "Clase de 18 a 20 horas" dejaba "20 horas" como 20h (1200 min).
    @Test fun rangeWithHoursUnitParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 18 a 20 horas", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun range24hFormatWithoutUnitParsesDuration() {
        val result = NaturalTaskParser.parse("Cita de 18 a 20", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeSmallHoursWithUnitParsesDuration() {
        // "9 a 11 horas": con unidad sí es rango aunque ambas < 13.
        val result = NaturalTaskParser.parse("Clase de 9 a 11 horas", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeDoesNotFalsePositiveOnItemCount() {
        // "de 2 a 5 entradas": sin unidad y ambas < 13 → NO es rango horario.
        val result = NaturalTaskParser.parse("Comprar de 2 a 5 entradas", now, zone)
        assertEquals("Comprar de 2 a 5 entradas", result.title)
        assertNull(result.durationMinutes)
    }

    // Rango sin "horas" (ambas < 13) seguido de un DÍA de la semana o de un marcador
    // temporal ("mañana", "a la tarde"): el primer envío del fix sólo aceptaba
    // conectores básicos (con/y/para...) y dejaba residuo cuando el rango iba seguido de
    // "el lunes", "mañana" o "a la tarde". Estas formas se añaden como followers seguros.
    @Test fun bareRangeSmallHoursFollowedByWeekdayParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9 a 11 el viernes", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun bareRangeSmallHoursFollowedByRelativeDayParsesDuration() {
        val result = NaturalTaskParser.parse("Taller de 10 a 12 mañana", now, zone)
        assertEquals("Taller", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun bareRangeSmallHoursFollowedByATardeParsesDuration() {
        // "a la tarde" empieza por "a" (no estaba en el set original). Antes dejaba residuo.
        val result = NaturalTaskParser.parse("Curso de 4 a 6 a la tarde", now, zone)
        assertEquals("Curso", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun bareRangeSmallHoursFollowedByPorLaNocheParsesDuration() {
        val result = NaturalTaskParser.parse("Turno de 9 a 11 por la noche", now, zone)
        assertEquals("Turno", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun bareRangeSmallHoursFollowedByCountableNounStillRejected() {
        // Extender el set de followers NO debe romper el rechazo de "de 2 a 5 personas".
        val result = NaturalTaskParser.parse("Reunión de 2 a 5 personas", now, zone)
        assertEquals("Reunión de 2 a 5 personas", result.title)
        assertNull(result.durationMinutes)
    }

    @Test fun rangeRemovesWindowButKeepsTrailingText() {
        val result = NaturalTaskParser.parse("Reunión de 18 a 20 con Juan", now, zone)
        assertEquals("Reunión con Juan", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // Rango horario sin la palabra "horas" y ambas horas < 13: antes caía a
    // dueAt=null/dur=null con el rango crudo como residuo en el título. Forma
    // cotidiana ("clase de 9 a 11", "taller de 10 a 12"). Se acepta porque el
    // rango va al final de la frase (sin sustantivo de cantidad después).
    @Test fun rangeSmallHoursWithoutUnitAtEndParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9 a 11", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeSmallHoursWithoutUnitTrailingConnectorParsesDuration() {
        val result = NaturalTaskParser.parse("Taller de 10 a 12 con proyector", now, zone)
        assertEquals("Taller con proyector", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeSmallHoursWithoutUnitFollowedByNounIsNotDuration() {
        // "de 2 a 5 entradas": hay un sustantivo después → cantidad, no horario.
        val result = NaturalTaskParser.parse("Comprar de 2 a 5 entradas", now, zone)
        assertEquals("Comprar de 2 a 5 entradas", result.title)
        assertNull(result.durationMinutes)
    }

    // Rango horario CON MINUTOS en el extremo inicial ("clase de 9:30 a 11"). Antes la
    // regex solo capturaba horas en punto y casaba "30 a 11" con números equivocados →
    // dur=null y título sucio ("Clase de a 11"). Ahora calcula 90 min reales.
    @Test fun rangeWithStartMinutesParsesRealDuration() {
        val result = NaturalTaskParser.parse("Clase de 9:30 a 11", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun rangeWithBothEndpointsMinutesParsesRealDuration() {
        val result = NaturalTaskParser.parse("Clase de 9:30 a 11:30", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithMinutesAndHoursUnitParsesRealDuration() {
        val result = NaturalTaskParser.parse("Clase de 9:30 a 11 horas", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(90, result.durationMinutes)
    }

    // Rango con MERIDIEM en ambos extremos ("de 9am a 11am", "de 2pm a 4pm"). Antes
    // dur=null y título sucio. El offset PM se aplica a cada extremo por separado.
    @Test fun rangeWithMeridiemAmParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9am a 11am", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithMeridiemPmParsesDuration() {
        val result = NaturalTaskParser.parse("Reunión de 2pm a 4pm", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithMinutesAndMeridiemParsesDuration() {
        val result = NaturalTaskParser.parse("Curso de 8:30am a 10:30am", now, zone)
        assertEquals("Curso", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithDeLaTardeMeridiemParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9 de la tarde a 11 de la noche", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // --- Meridiem sin "a las" y hora de inicio del rango como dueAt (ciclo 61) ---
    // BUG A: una hora con meridiem "pm"/"am" pero SIN "a las" ("Reunión 2pm") se
    // agendaba como AM (02:00) porque el meridiem se leía del grupo 4, que solo existe
    // en el patrón "a las N"; los patrones N:MM y Nam/Pm lo llevan en el grupo 3.
    @Test fun barePmTimeWithoutAParsesAsPm() {
        val result = NaturalTaskParser.parse("Reunión 2pm", now, zone)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareAmTimeWithoutAParsesAsAm() {
        val result = NaturalTaskParser.parse("Cita 9am", now, zone)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun barePmTimeWithMinutesWithoutAParsesAsPm() {
        val result = NaturalTaskParser.parse("Vuelo 8:30pm", now, zone)
        assertEquals(LocalTime.of(20, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // BUG B: en un rango "de H1 [meridiem] a H2 [meridiem]" la fecha límite debe ser la
    // hora de INICIO del rango (resuelta con su meridiem), no la canónica de la parte
    // del día. Antes "de 9 de la tarde a 11 de la noche" daba due=15:00 (por "de la
    // tarde") en vez de 21:00 (inicio real).
    @Test fun rangeWithDeLaTardeSetsDueAtToStart() {
        val result = NaturalTaskParser.parse("Clase de 9 de la tarde a 11 de la noche", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithPmMeridiemSetsDueAtToStart() {
        val result = NaturalTaskParser.parse("Reunión de 2pm a 4pm", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithAmMeridiemSetsDueAtToStart() {
        val result = NaturalTaskParser.parse("Clase de 9am a 11am", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithMinutesDoesNotClampToDayMax() {
        // "de 8:30 a 10:30 horas": antes (solo horas en punto) capturaba fin=10 → 120,
        // pero un caso previo con residuo devolvía 1440 (clamp 24h). Ahora 120 reales.
        val result = NaturalTaskParser.parse("Curso de 8:30 a 10:30 horas", now, zone)
        assertEquals("Curso", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // --- Propagación de meridiem del extremo final al inicio bare (ciclo 75) ---
    // BUG: en "de 6 a 8 de la tarde" solo el EXTREMO FINAL lleva meridiem; el inicio
    // (sin meridiem) no heredaba el contexto de tarde y se agendaba como 06:00 en vez
    // de 18:00. La duración ya era correcta (120, diff de horas en punto), pero la
    // fecha límite apuntaba a la mañana → el recordatorio se disparaba 12h antes.
    @Test fun rangeWithTrailingDeLaTardePropagatesPmToStart() {
        val result = NaturalTaskParser.parse("Reunión de 6 a 8 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingDeLaNochePropagatesPmToStart() {
        val result = NaturalTaskParser.parse("Reunión de 3 a 5 de la noche", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingDeLaMananaKeepsAmStart() {
        // "de la mañana" es AM: la propagación no debe sumar 12 (el inicio 9 sigue 09:00).
        val result = NaturalTaskParser.parse("Reunión de 9 a 11 de la mañana", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingDeLaTardeAndStartMinutesPropagatesPm() {
        // El inicio con minutos pero sin meridiem también hereda el PM del extremo final.
        val result = NaturalTaskParser.parse("Reunión de 6:30 a 8 de la tarde", now, zone)
        assertEquals(90, result.durationMinutes)
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingDeLaMadrugadaKeepsAmStart() {
        val result = NaturalTaskParser.parse("Reunión de 1 a 3 de la madrugada", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(1, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Meridiem compacto (am/pm) solo en el extremo final: el explicitTime capturaba
    // el FIN y sombreaba rangeStartTime (c.77, P1). Ahora gana la hora de INICIO. ---
    // "reunión de 6 a 8 pm": antes dueAt=20:00 (FIN), ahora 18:00 (INICIO). El meridiem
    // "pm" del extremo final se propaga al inicio bare (igual que "de la tarde" en c.76).
    @Test fun rangeWithTrailingCompactPmResolvesStartNotEnd() {
        val result = NaturalTaskParser.parse("Reunión de 6 a 8 pm", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingCompactAmResolvesStartNotEnd() {
        val result = NaturalTaskParser.parse("Taller de 9 a 11 am", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingPmDotsResolvesStartNotEnd() {
        val result = NaturalTaskParser.parse("Clase de 6 a 8 p.m.", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingPmSpacedResolvesStartNotEnd() {
        val result = NaturalTaskParser.parse("Evento de 3 a 5 p m", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun rangeWithTrailingCompactPmAndStartMinutesResolvesStart() {
        val result = NaturalTaskParser.parse("Curso de 2:30 a 4:30 pm", now, zone)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(14, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Hora suelta con meridiem (NO rango): sigue resolviéndose correctamente. El guard
    // solo actúa cuando el tiempo explícito cae DENTRO del span de un rango validado.
    @Test fun standaloneHourWithMeridiemNotAffectedByRangeGuard() {
        val r1 = NaturalTaskParser.parse("Llamada 8pm", now, zone)
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(r1.dueAt!!, zone))
        val r2 = NaturalTaskParser.parse("Cita 9am", now, zone)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r2.dueAt!!, zone))
    }

    // --- Cruce del mediodía en rangos con meridiem solo al final (ciclo 79) ---
    // BUG: "de 12 a 2 de la tarde" computaba la duración con horas crudas del texto
    // (2−12=−600) y coerceIn(5,…) dejaba 5 min en vez de 120. Además la propagación de
    // PM al inicio bare era incondicional: "de 11 a 1 de la tarde" convertía el inicio
    // 11→23 (PM propagado) dando dueAt=23:00 y duración absurda. Ahora la propagación
    // solo aplica cuando startHr <= endHr (mismo lado del mediodía); en un cruce real
    // (start>end) el inicio se queda en AM y el fin en PM, que es lo correcto.
    @Test fun noonCrossingRangeDe12A2DeLaTarde() {
        val result = NaturalTaskParser.parse("Almuerzo de 12 a 2 de la tarde", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun noonCrossingRangeDe12A2pm() {
        val result = NaturalTaskParser.parse("Almuerzo de 12 a 2pm", now, zone)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun noonCrossingRangeDe11A1DeLaTarde() {
        // 11 (AM) → 1 (PM, +12=13): cruce del mediodía. El inicio NO hereda PM.
        val result = NaturalTaskParser.parse("Clase de 11 a 1 de la tarde", now, zone)
        assertEquals(LocalTime.of(11, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun noonCrossingRangeDe12A1pm() {
        val result = NaturalTaskParser.parse("Curso de 12 a 1pm", now, zone)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(60, result.durationMinutes)
    }

    @Test fun noonCrossingRangeDe1A2DeLaTarde() {
        // Mismo lado del mediodía (1<=2): el inicio SÍ hereda PM → 13:00.
        val result = NaturalTaskParser.parse("Siesta de 1 a 2 de la tarde", now, zone)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(60, result.durationMinutes)
    }

    @Test fun noonCrossingRangeAmbiguousRejected() {
        // "de 12 a 2" sin meridiem ni unidad es ambiguo (¿horas? ¿cantidad?): se rechaza.
        val result = NaturalTaskParser.parse("Reunión de 12 a 2", now, zone)
        assertNull(result.dueAt)
        assertNull(result.durationMinutes)
    }

    // --- Meridiem solo en el INICIO (PM): el fin bare hereda PM (ciclo 79, BUG-001) ---
    // BUG: "de 6pm a 8" dejaba el fin (8) sin resolver → 08:00 < 18:00 → rango inválido,
    // duración null y título sucio ("Reunión de a 8"). El inicio con PM explícito
    // propaga su contexto al fin bare (mismo lado del mediodía) → 18:00→20:00, dur 120.
    @Test fun rangeWithLeadingCompactPmPropagatesToEnd() {
        val result = NaturalTaskParser.parse("Reunión de 6pm a 8", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithLeadingCompactPmAndTrailingTextPropagatesToEnd() {
        // El fin bare hereda PM aunque vaya seguido de texto conector ("con el cliente").
        val result = NaturalTaskParser.parse("Reunión de 6pm a 8 con el cliente", now, zone)
        assertEquals("Reunión con el cliente", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithLeadingCompactPmAndMinutesPropagatesToEnd() {
        val result = NaturalTaskParser.parse("Curso de 2:30pm a 4:30", now, zone)
        assertEquals(LocalTime.of(14, 30), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithLeadingDeLaTardePropagatesToEnd() {
        // "de la tarde" en el inicio también propaga al fin bare.
        val result = NaturalTaskParser.parse("Clase de 6 de la tarde a 8", now, zone)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    // ANTI FALSO POSITIVO: el fin bare NO hereda PM si le sigue un sustantivo de
    // cantidad ("de 2pm a 4 entradas" es una compra, no un rango horario).
    @Test fun rangeWithLeadingPmAndCountNounIsRejected() {
        val result = NaturalTaskParser.parse("Comprar de 2pm a 4 entradas", now, zone)
        assertEquals("Comprar de a 4 entradas", result.title)
        assertNull(result.durationMinutes)
    }

    @Test fun rangeWithLeadingAmPropagatesToEnd() {
        // AM explícito en el inicio: el fin bare hereda AM (no suma 12). "8am a 12" → 12:00.
        val result = NaturalTaskParser.parse("Turno de 8am a 12", now, zone)
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(240, result.durationMinutes)
    }

    // CRUCE inverso de medianoche: "de 11pm a 1" (inicio PM, fin bare con endHr <
    // startHr). El fin NO hereda PM (sería 13:00); se queda en 01:00 y el rango envuelve
    // al día siguiente → 23:00→01:00, dur 120.
    @Test fun rangeWithLeadingPmCrossingMidnightWraps() {
        val result = NaturalTaskParser.parse("de 11pm a 1", now, zone)
        assertEquals(LocalTime.of(23, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithLeadingPmCrossingMidnightWithDeLaMadrugadaWraps() {
        val result = NaturalTaskParser.parse("Turno de 11pm a 1 de la madrugada", now, zone)
        assertEquals(LocalTime.of(23, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(120, result.durationMinutes)
    }

    // "de 8pm a 3pm" (ambos PM, descendente) NO envuelve: se rechaza como antes.
    @Test fun rangeWithBothPmDescendingNotWrapped() {
        val result = NaturalTaskParser.parse("Reunión de 8pm a 3pm", now, zone)
        assertNull(result.durationMinutes)
    }

    // "de" antes de una duración numérica es conector ("Reunión de 30 min") y debe
    // eliminarse junto con la duración, sin dejar residuo.
    @Test fun deConnectorBeforeDurationIsRemoved() {
        val result = NaturalTaskParser.parse("Reunión de 30 minutos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun deConnectorBeforeHourDurationIsRemoved() {
        val result = NaturalTaskParser.parse("Juntada de 2 horas", now, zone)
        assertEquals("Juntada", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // "a las N horas" es una hora (N en reloj de 24h con sufijo "horas"), NO una duración.
    // Antes el sufijo "horas" no se consumía: "9 horas" era robado como duración (540 min
    // falsos) y "a las" quedaba como residuo en el título. El timePattern ahora consume el
    // sufijo opcional y el durationMatch ignora horas precedidas por frase temporal.
    @Test fun aLasNHorasEsHoraNoDuracion() {
        val result = NaturalTaskParser.parse("Reunión a las 9 horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun aLasNHorasConFechaNoEsDuracion() {
        val result = NaturalTaskParser.parse("Clase mañana a las 10 horas", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    // "durante Nh" compacto: el conector "durante" debe borrarse junto con la duración
    // para no dejar residuo en el título.
    @Test fun duranteConnectorBeforeCompactDurationIsRemoved() {
        val result = NaturalTaskParser.parse("Reunión de equipo el viernes a las 15 horas durante 1h", now, zone)
        assertEquals("Reunión de equipo", result.title)
        assertEquals(60, result.durationMinutes)
    }

    // "este/el/próximo fin de semana" y "fin de semana" suelto → próximo sábado.
    // Es una de las frases de fecha más comunes en español y antes quedaba sin fecha
    // (INBOX) con "fin de semana" como residuo en el título. now=miércoles 2026-07-29 →
    // sábado 2026-08-01, hora canónica 09:00 (mismo default que los días sueltos).
    @Test fun esteFinDeSemanaProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan este fin de semana", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun finDeSemanaSueltoProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pintar la valla fin de semana", now, zone)
        assertEquals("Pintar la valla", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elFinDeSemanaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Fiesta el fin de semana a las 20:00", now, zone)
        assertEquals("Fiesta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "fin de semana que viene": el patron "semana que viene" (periodo proximo) coincide
    // con la subcadena de "fin de semana que viene" y dejaba el residuo "fin de" en el
    // titulo, ademas de programar +7d en lugar del proximo sabado. Regresion introducida
    // al anadir el periodo proximo; ahora el "fin de semana" se procesa primero.
    @Test fun finDeSemanaQueVieneProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Viaje fin de semana que viene", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "finde" (apócope coloquial) en SINGULAR es una fecha única (próximo sábado),
    // NO un hábito. Antes "este finde" caía por error a la recurrencia semanal
    // (WEEKLY sábado+domingo para siempre) cuando el usuario pedía una sola fecha.
    // now=miércoles 2026-07-29 → sábado 2026-08-01.
    @Test fun esteFindeProgramaProximoSabadoSinRecurrencia() {
        val result = NaturalTaskParser.parse("Viaje este finde", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun findeSueltoProgramaProximoSabadoSinRecurrencia() {
        val result = NaturalTaskParser.parse("Cine finde", now, zone)
        assertEquals("Cine", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun cadaFindeSigueSiendoHabitoSemanalFinDeSemana() {
        val result = NaturalTaskParser.parse("Estudiar cada finde", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun losFindesSigueSiendoHabitoSemanalFinDeSemana() {
        val result = NaturalTaskParser.parse("Limpiar los findes", now, zone)
        assertEquals("Limpiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // "cada fin de semana" (forma larga + "cada") era hábito perdido: caía a fecha única
    // (rec=NONE) porque weekendPattern lo consumía antes de parseRecurrence. "cada finde"
    // (apócope) sí era hábito, pero la forma larga no. Brecha preexistente descubierta c.99.
    @Test fun cadaFinDeSemanaEsHabitoSemanalFinDeSemana() {
        val result = NaturalTaskParser.parse("Estudiar cada fin de semana", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // --- Fechas relativas en semanas/meses ---
    // "en una semana"/"en un mes" son de las formas más comunes en español y antes
    // quedaban SIN fecha (dueAt=null) → la tarea se olvidaba (sin recordatorio). now=2026-07-29.

    @Test fun enUnaSemanaParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar propuesta en una semana", now, zone)
        assertEquals("Enviar propuesta", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnMesParsesDueAt() {
        val result = NaturalTaskParser.parse("Renovar licencia en un mes", now, zone)
        assertEquals("Renovar licencia", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enDigitSemanasParsesDueAt() {
        val result = NaturalTaskParser.parse("Revisión en 3 semanas", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 8, 19), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enDigitMesesParsesDueAt() {
        val result = NaturalTaskParser.parse("Auditoría en 2 meses", now, zone)
        assertEquals("Auditoría", result.title)
        // 2 meses = 60 días a partir de 2026-07-29 → 2026-09-27.
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dentroDeUnMesParsesDueAt() {
        val result = NaturalTaskParser.parse("Cita dentro de un mes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enQuinceDiasParsesDueAt() {
        // "quince" antes no estaba en el diccionario de números escritos → dueAt=null.
        val result = NaturalTaskParser.parse("Entregar en quince días", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun semanaRelativaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Enviar en una semana a las 9", now, zone)
        assertEquals("Enviar", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "y media/medio" en plazos de día/semana/mes/año: media unidad más.
    // Simétrico al "y media" sub-hora (c.94 "en una hora y media"=90 min), pero a
    // escala de días: "en una semana y media" = +7d + 3,5d = 10,5 d, "en un mes y
    // medio" = +45 d. Antes [relativePattern] robaba solo "en una semana" (+7d) y
    // dejaba "y media" como residuo en el título ("enviar y media"), con lo que el
    // plazo quedaba 3,5 días (o 15 días para un mes) ANTES de lo que el usuario pidió:
    // un vencimiento prematuro silencioso que hace olvidar el margen real de la tarea.
    @Test fun enUnaSemanaYMediaSumaMediaSemana() {
        val result = NaturalTaskParser.parse("Enviar informe en una semana y media", now, zone)
        assertEquals("Enviar informe", result.title)
        // +10,5 d desde 2026-07-29 12:00 → 2026-08-09 00:00.
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnMesYMedioSumaMedioMes() {
        val result = NaturalTaskParser.parse("Renovar en un mes y medio", now, zone)
        assertEquals("Renovar", result.title)
        // +45 d desde 2026-07-29 12:00 → 2026-09-12 12:00.
        assertEquals(LocalDate.of(2026, 9, 12), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enDosSemanasYMediaSumaMediaSemana() {
        val result = NaturalTaskParser.parse("Terminar en dos semanas y media", now, zone)
        assertEquals("Terminar", result.title)
        // +17,5 d desde 2026-07-29 12:00 → 2026-08-16 00:00.
        assertEquals(LocalDate.of(2026, 8, 16), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnDiaYMedioSumaMedioDia() {
        val result = NaturalTaskParser.parse("Llamar en un día y medio", now, zone)
        assertEquals("Llamar", result.title)
        // +1,5 d desde 2026-07-29 12:00 → 2026-07-31 00:00.
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnAnioYMedioSumaMedioAnio() {
        val result = NaturalTaskParser.parse("Revisar en un año y medio", now, zone)
        assertEquals("Revisar", result.title)
        // +1,5 años = 365 d + 182,5 d = 547,5 d desde 2026-07-29 12:00 → 2028-01-28 00:00.
        assertEquals(LocalDate.of(2028, 1, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enSemanaYMedioGeneroMasculinoTambienFunciona() {
        // El usuario mezcla género ("una semana y medio" en vez de "y media"): aceptar
        // ambas formas evita una tarea sin fecha por un detalle gramatical.
        val result = NaturalTaskParser.parse("Enviar en una semana y medio", now, zone)
        assertEquals("Enviar", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "en N años": años como unidad relativa (antes: dueAt=null) ---
    // "en un año"/"en 2 años"/"dentro de un año" son formas comunes para plazos largos
    // (renovar licencia, presentar impuestos). Antes no se parseaban → tarea sin
    // recordatorio, olvidada durante meses/años.

    @Test fun enUnAnioParsesDueAt() {
        val result = NaturalTaskParser.parse("Renovar licencia en un año", now, zone)
        assertEquals("Renovar licencia", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enDosAniosParsesDueAt() {
        val result = NaturalTaskParser.parse("Vacaciones en 2 años", now, zone)
        assertEquals("Vacaciones", result.title)
        assertEquals(LocalDate.of(2028, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dentroDeUnAnioParsesDueAt() {
        val result = NaturalTaskParser.parse("Renovar pasaporte dentro de un año", now, zone)
        assertEquals("Renovar pasaporte", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnAnioRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cita en un año a las 10", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "el/la (semana|mes|año) que viene" / "próximo X": período siguiente ---
    // "la semana que viene", "el mes que viene", "el año que viene" son las formas
    // cotidianísimas de posponer a la siguiente unidad. Antes: dueAt=null y la frase
    // "que viene" quedaba como residuo en el título → tarea olvidada.

    @Test fun semanaQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar informe la semana que viene", now, zone)
        assertEquals("Enviar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Pagar renta el mes que viene", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun anioQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Presentar impuestos el año que viene", now, zone)
        assertEquals("Presentar impuestos", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximoMesParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar el próximo mes", now, zone)
        assertEquals("Enviar", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximaSemanaParsesDueAt() {
        val result = NaturalTaskParser.parse("Revisión la próxima semana", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pagar el mes que viene a las 10", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "entrante": sinónimo caribeño de "que viene"/"próximo" ---
    // La app usa America/Santo_Domingo como zona canónica; "la semana entrante",
    // "el mes entrante", "el año entrante" son cotidianísimos en el español
    // caribeño. Antes caían a dueAt=null + residuo "entrante" en el título →
    // vencimiento olvidado (invisible en What Now/planificador, sin recordatorio).

    @Test fun semanaEntranteParsesDueAt() {
        val result = NaturalTaskParser.parse("Enviar informe la semana entrante", now, zone)
        assertEquals("Enviar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesEntranteParsesDueAt() {
        val result = NaturalTaskParser.parse("Pagar renta el mes entrante", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun anioEntranteParsesDueAt() {
        val result = NaturalTaskParser.parse("Presentar impuestos el año entrante", now, zone)
        assertEquals("Presentar impuestos", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesEntranteRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pagar el mes entrante a las 10", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun entranteNoEsFalsoPositivoEnSustantivoAjeno() {
        // "documento/llamada/factura entrante": "entrante" modifica un sustantivo
        // que NO es un período → no debe casar (dueAt=null, título intacto).
        val r = NaturalTaskParser.parse("Revisar el documento entrante", now, zone)
        assertEquals("Revisar el documento entrante", r.title)
        assertNull(r.dueAt)
    }

    // --- "que entra": variante regional (MX/CA) de "que viene"/"entrante" ---
    // "el mes que entra", "la semana que entra", "el año que entra" son sinónimos
    // plenos de "...que viene". Antes caían a dueAt=null + residuo "que entra" en
    // el título → vencimiento olvidado (asimetría frente a "que viene"). now=2026-07-29.

    @Test fun laSemanaQueEntraResuelveProximaSemana() {
        // 2026-07-29 + 7 días = 2026-08-05.
        val result = NaturalTaskParser.parse("Enviar informe la semana que entra", now, zone)
        assertEquals("Enviar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesQueEntraResuelveProximoMes() {
        // 2026-07-29 + 30 días = 2026-08-28.
        val result = NaturalTaskParser.parse("Pagar renta el mes que entra", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elAnioQueEntraResuelveProximoAnio() {
        // 2026-07-29 + 365 días = 2027-07-29.
        val result = NaturalTaskParser.parse("Presentar impuestos el año que entra", now, zone)
        assertEquals("Presentar impuestos", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesQueEntraConHoraAplicaHora() {
        val result = NaturalTaskParser.parse("Pagar el mes que entra a las 10", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elNDelMesQueEntraResuelveDiaNDelMesSiguiente() {
        // "el 15 del mes que entra": compromiso mensual con calificador regional.
        val result = NaturalTaskParser.parse("Cobro el 15 del mes que entra", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "el N del mes que viene": día concreto del mes siguiente ---
    // Compromiso mensual anclado a un día (vencimiento, cobro, cita). Antes
    // nextPeriodPattern robaba "mes que viene" como +30d genérico ignorando el día
    // explícito (→ fecha errónea, 2026-08-28 en vez del día N) y dejaba el residuo
    // "el N del" en el título. Ahora se resuelve al día N del mes siguiente.

    @Test fun elNDelMesQueVieneResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Llamar al banco el 15 del mes que viene", now, zone)
        assertEquals("Llamar al banco", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNDelProximoMesResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro el 10 del próximo mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNDelMesProximoResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro el 10 del mes próximo", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNDelMesEntranteResuelveDiaNDelMesSiguiente() {
        // "el 15 del mes entrante": compromiso mensual con calificador caribeño.
        // Antes: nextPeriodPattern no reconocía "entrante" → dueAt=null + residuo.
        val result = NaturalTaskParser.parse("Cobro el 10 del mes entrante", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNDelMesQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago el 5 del próximo mes a las 9", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elNDelMesQueVieneRespetaDia31CuandoMesTiene31() {
        // Desde 29/07 el mes que viene es agosto (31 días): el 31 existe y se respeta
        // (el clamp solo actúa si el día no existe en el mes destino).
        val result = NaturalTaskParser.parse("Vence el 31 del mes que viene", now, zone)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesQueVieneSinDiaSigueSiendoMas30Dias() {
        // No-regresión: sin día explícito, "el mes que viene" sigue siendo +30d.
        val result = NaturalTaskParser.parse("Reunión el mes que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Orden inverso: "el mes que viene el N" / "el mes que viene el día N" ---
    // Misma semántica que la forma directa pero con el período ANTES del día. Antes
    // nextPeriodPattern robaba "el mes que viene" como +30d (2026-08-28) ignorando el
    // día explícito, y dejaba "el N" como residuo en el título. Ahora se ancla al día N.

    @Test fun elMesQueVieneElNResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Llamar al banco el mes que viene el 15", now, zone)
        assertEquals("Llamar al banco", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesQueVieneElDiaNResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Pago el mes que viene el día 5", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elProximoMesElNResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro el próximo mes el 10", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesProximoElNResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Vence el mes próximo el 20", now, zone)
        assertEquals("Vence", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesEntranteElNResuelveDiaNDelMesSiguiente() {
        // Orden inverso con calificador caribeño: "el mes entrante el 20".
        val result = NaturalTaskParser.parse("Vence el mes entrante el 20", now, zone)
        assertEquals("Vence", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesQueVieneElNRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago el mes que viene el 5 a las 9", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elMesQueVieneElNClampDia31CuandoMesTiene30() {
        // Desde 29/07 el mes que viene es agosto (31 días): el 31 existe y se respeta
        // (el clamp solo actúa si el día no existe en el mes destino).
        val result = NaturalTaskParser.parse("Vence el mes que viene el 31", now, zone)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "la semana que viene el <día>" / "el <día> de la semana que viene" ---
    // Antes nextPeriodPattern robaba "la semana que viene" como +7d (2026-08-05) ignorando
    // el día explícito → cita/reunión en día equivocado. Ahora se ancla al día objetivo de
    // la semana próxima (próximo lunes + offset). Base: 2026-07-29 (miércoles); próximo
    // lunes = 2026-08-03.

    @Test fun laSemanaQueVieneElLunesResuelveLunesDeLaSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión la semana que viene el lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaQueVieneElViernesResuelveViernesDeLaSemanaProxima() {
        // Antes daba 2026-08-05 (+7d desde miércoles): fecha errónea (no es viernes).
        val result = NaturalTaskParser.parse("Cita la semana que viene el viernes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaQueVieneElDomingoResuelveDomingoDeLaSemanaProxima() {
        val result = NaturalTaskParser.parse("Cena la semana que viene el domingo", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laProximaSemanaElMiercolesResuelveMiercolesDeLaSemanaProxima() {
        val result = NaturalTaskParser.parse("Pago la próxima semana el miércoles", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elLunesDeLaSemanaQueVieneResuelveLunesDeLaSemanaProxima() {
        // Orden inverso (día ANTES del período). Misma resolución.
        val result = NaturalTaskParser.parse("Reunión el lunes de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elViernesDeLaProximaSemanaResuelveViernesDeLaSemanaProxima() {
        val result = NaturalTaskParser.parse("Cita el viernes de la próxima semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaEntranteElViernesResuelveViernesDeLaSemanaProxima() {
        // Calificador caribeño en "semana + día": ancla al viernes de la semana próxima.
        val result = NaturalTaskParser.parse("Cita la semana entrante el viernes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elLunesDeLaSemanaEntranteResuelveLunesDeLaSemanaProxima() {
        // Orden inverso (día ANTES del período) con calificador caribeño.
        val result = NaturalTaskParser.parse("Reunión el lunes de la semana entrante", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaQueVieneElViernesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cita la semana que viene el viernes a las 18", now, zone)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun laSemanaQueVieneSinDiaSigueSiendoMasSieteDias() {
        // No-regresión: sin día explícito, "la semana que viene" sigue siendo +7d.
        val result = NaturalTaskParser.parse("Entrega la semana que viene", now, zone)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "ayer"/"anteayer": fechas pasadas explícitas ---
    // Antes no se parseaban → dueAt=null, o combinadas con hora resolvían a HOY (fecha errónea).
    // Se mantienen en el pasado (honesto: tarea vencida, visible en What Now).

    @Test fun ayerParsesDueAtYesterday() {
        val result = NaturalTaskParser.parse("Llamar a Ana ayer", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun anteayerParsesDueAtTwoDaysAgo() {
        val result = NaturalTaskParser.parse("Enviar correo anteayer", now, zone)
        assertEquals("Enviar correo", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ayerConHoraResuelveAyerNoHoy() {
        // Regresión crítica: antes "ayer a las 4 de la tarde" resolvía a HOY a las 16:00.
        val result = NaturalTaskParser.parse("Reunión ayer a las 4 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun anteayerConPartoDeDiaResuelvePasado() {
        val result = NaturalTaskParser.parse("Visita anteayer a la tarde", now, zone)
        assertEquals("Visita", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antierParsesDueAtTwoDaysAgo() {
        // "antier" = variante coloquial hispanoamericana de "anteayer" (MX/CA/parts SA).
        // Antes no se parseaba → dueAt=null → tarea vencida olvidada.
        val result = NaturalTaskParser.parse("Enviar correo antier", now, zone)
        assertEquals("Enviar correo", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antierConHoraResuelvePasadoNoHoy() {
        // Regresión: "antier a las 4 de la tarde" debe resolver a antier (no a HOY),
        // igual que "ayer a las 4". Antes la combinación con hora rompía la fecha.
        val result = NaturalTaskParser.parse("Reunión antier a las 4 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun proximosDiasParsesDueAt() {
        // "próximos días" = forma vaga de "dentro de poco": +3 días (heurística honesta).
        // Antes quedaba sin fecha → tarea olvidada (sin recordatorio ni visibilidad).
        val result = NaturalTaskParser.parse("Pagar factura próximos días", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximosDiasConPrefijoParsesDueAt() {
        // "en los próximos días" / "en el próximo días" / "en las próximos días":
        // el prefijo "en los/el/las" es opcional y no debe quedar como residuo.
        val result = NaturalTaskParser.parse("Llamar al dentista en los próximos días", now, zone)
        assertEquals("Llamar al dentista", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximosDiasRespetaHoraExplicita() {
        // Como los demás períodos próximos, "próximos días" combina con hora explícita.
        val result = NaturalTaskParser.parse("Revisar correo en los próximos días a las 10", now, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "trimestre": período próximo de 3 meses (+90 días) ---
    // "próximo trimestre" / "el trimestre que viene" / "el próximo trimestre":
    // plazo largo cotidiano (impuestos trimestrales, revisiones, informes). Antes
    // no se parseaba → dueAt=null → tarea olvidada (sin recordatorio ni visibilidad).

    @Test fun proximoTrimestreParsesDueAt() {
        // +90 días (3 meses × 30d, consistente con "mes que viene" = +30d).
        val result = NaturalTaskParser.parse("Auditoría próximo trimestre", now, zone)
        assertEquals("Auditoría", result.title)
        assertEquals(LocalDate.of(2026, 10, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun trimestreQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Cerrar informe el trimestre que viene", now, zone)
        assertEquals("Cerrar informe", result.title)
        assertEquals(LocalDate.of(2026, 10, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximoTrimestreRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión el próximo trimestre a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 10, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "quincena" (+15d) / "bimestre" (+60d) / "semestre" (+180d): períodos ---
    // Formas relativas ("en una quincena") y de período próximo ("próxima quincena",
    // "el bimestre que viene", "próximo semestre"). Plazos cotidianos en español
    // (pagos quincenales, reportes bimestrales, cierres semestrales). Antes no se
    // parseaban → dueAt=null → tarea olvidada. "bimestre"/"semestre" contienen "mes",
    // por lo que se comprueban antes que "mes" para no colisionar (+30d).

    @Test fun enUnaQuincenaParsesDueAt() {
        val result = NaturalTaskParser.parse("Pago en una quincena", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximaQuincenaParsesDueAt() {
        val result = NaturalTaskParser.parse("Revisión próxima quincena", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenaQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Reporte la quincena que viene", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnBimestreParsesDueAt() {
        val result = NaturalTaskParser.parse("Cierre en un bimestre", now, zone)
        assertEquals("Cierre", result.title)
        // +60 días a partir de 2026-07-29 → 2026-09-27.
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximoBimestreParsesDueAt() {
        val result = NaturalTaskParser.parse("Impuestos próximo bimestre", now, zone)
        assertEquals("Impuestos", result.title)
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bimestreQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Informe el bimestre que viene", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnSemestreParsesDueAt() {
        val result = NaturalTaskParser.parse("Auditoría en un semestre", now, zone)
        assertEquals("Auditoría", result.title)
        // +180 días a partir de 2026-07-29 → 2027-01-25.
        assertEquals(LocalDate.of(2027, 1, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximoSemestreParsesDueAt() {
        val result = NaturalTaskParser.parse("Renovación próximo semestre", now, zone)
        assertEquals("Renovación", result.title)
        assertEquals(LocalDate.of(2027, 1, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun semestreQueVieneParsesDueAt() {
        val result = NaturalTaskParser.parse("Cierre el semestre que viene", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2027, 1, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun semestreRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión el próximo semestre a las 11", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2027, 1, 25), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "fin de mes" / "finales de mes" / "mediados de mes": vencimientos mensuales ---
    // Plazos cotidianos de pagos (alquiler, tarjeta, servicios, facturas). Antes no se
    // parseaban → dueAt=null → vencimiento olvidado (sin recordatorio ni visibilidad).
    // now = 2026-07-29 (julio tiene 31 días).

    @Test fun finDeMesParsesDueAtUltimoDiaMesActual() {
        val result = NaturalTaskParser.parse("Pagar alquiler fin de mes", now, zone)
        assertEquals("Pagar alquiler", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeMesParsesDueAt() {
        val result = NaturalTaskParser.parse("Vencer tarjeta a finales de mes", now, zone)
        assertEquals("Vencer tarjeta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pagar factura fin de mes a las 18", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun finDeMesRuedaAProximoMesSiHoyEsUltimoDia() {
        // 2026-08-31 = último día de agosto → "fin de mes" rueda al último día de septiembre.
        val ultNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 31), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cierre contable a fin de mes", ultNow, zone)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "corte de mes"/"corte del mes" = sinónimo latinoamericano de "fin de mes"/"cierre de
    // mes" (corte de caja, corte de nómina, pago de renta al corte del mes). Antes caía a
    // dueAt=null: el vencimiento se olvidaba (P1: pago/renta sin recordatorio). Reutiliza
    // el mismo flujo de fin de mes.
    @Test fun corteDeMesParsesDueAtFinDeMes() {
        val result = NaturalTaskParser.parse("pago corte de mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun corteDelMesParsesDueAtFinDeMes() {
        val result = NaturalTaskParser.parse("renta corte del mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeMesParsesDueAtDia15ProximoMes() {
        // hoy = 29/7 ≥ 15 → mediados rueda al 15 del mes siguiente.
        val result = NaturalTaskParser.parse("Reporte a mediados de mes", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeMesResuelveDia15MesActualSiAunNoLlega() {
        // 2026-08-05 < 15 → mediados = 15/8 (mes actual).
        val tempranoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 5), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Entrega mediados de mes", tempranoNow, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeMesNoColisionaConPeriodoProximo() {
        // "fin de mes" contiene la subcadena "mes" pero NO debe activar "mes que viene"
        // ni dejar residuo. El titulo queda limpio y la fecha es fin de mes (no +30d).
        val result = NaturalTaskParser.parse("Renovar suscripcion a finales del mes", now, zone)
        assertEquals("Renovar suscripcion", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- límites mensuales con modificador "mes que viene"/"próximo" ---
    // Antes el patrón terminaba en "mes" e ignoraba el calificador → un vencimiento
    // fijado para "fin del mes que viene" caía un mes ANTES (fin de este mes). P1:
    // pago/renta/card olvidados o adelantados. El modificador se consume (título limpio)
    // y ancla al mes SIGUIENTE. now = 2026-07-29 → "mes que viene" = agosto.

    @Test fun finDelMesQueVieneAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Pagar tarjeta fin del mes que viene", now, zone)
        assertEquals("Pagar tarjeta", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDelMesProximoAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Cierre fin del mes próximo", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeMesQueVieneSinDelAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Pago fin de mes que viene", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDelMesEntranteAnclaFinMesSiguiente() {
        // Calificador caribeño en límite mensual: ancla a fin del mes siguiente.
        val result = NaturalTaskParser.parse("Pagar tarjeta fin del mes entrante", now, zone)
        assertEquals("Pagar tarjeta", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDelMesEntranteAnclaInicioMesSiguiente() {
        // "principios del mes entrante" = día 1 del mes siguiente (agosto).
        val result = NaturalTaskParser.parse("Cobro principios del mes entrante", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "último día del mes": sinónimo cotidiano de "fin de mes" ---
    // Antes "último día del mes" (y "último día del mes que viene") no se parseaban
    // → dueAt=null → la tarea quedaba SIN fecha (olvido de vencimiento). P1.
    // El modificador de mes siguiente se respeta; el título queda limpio de la frase.

    @Test fun ultimoDiaDelMesParsesDueAtFinMesActual() {
        val result = NaturalTaskParser.parse("Entregar informe último día del mes", now, zone)
        assertEquals("Entregar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoDiaDelMesConDelRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago el último día del mes a las 9", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ultimoDiaDelMesSinTildeFuncionaIgual() {
        val result = NaturalTaskParser.parse("Cobro ultimo dia del mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoDiaDelMesQueVieneAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Renta último día del mes que viene", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoDiaDelMesProximoAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Cierre último día del mes próximo", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDelMesQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión fin del mes que viene a las 18", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aFinalesDelMesQueVieneAnclaFinMesSiguiente() {
        val result = NaturalTaskParser.parse("Vencer a finales del mes que viene", now, zone)
        assertEquals("Vencer", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDelMesQueVieneAnclaDia15MesSiguiente() {
        val result = NaturalTaskParser.parse("Reporte mediados del mes que viene", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDelMesQueVieneAnclaDia1MesSiguiente() {
        // "principios del mes que viene" = 1 del mes siguiente, NO 1 del subsiguiente
        // (regresión de doble-desplazamiento que se evita anclando a today+1 mes).
        val result = NaturalTaskParser.parse("Cobro principios del mes que viene", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDelMesProximoRespetaUltimoDiaMesDestino() {
        // 2026-11-30 (último día de nov) → "fin del mes que viene" = fin dic = 31/12.
        val novNow = DateRules.toEpochMillis(LocalDate.of(2026, 11, 30), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cierre fin del mes que viene", novNow, zone)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "antepasado mañana" = dentro de 3 días ---
    // Antes la palabra "mañana" casaba con el token de fecha suelto → +1 (fecha
    // errónea) y "antepasado" quedaba como residuo en el título. P1: cita 2 días
    // antes de lo pedido y título corrupto. now = 2026-07-29 → +3 = 2026-08-01.

    @Test fun antepasadoMananaResuelveTresDiasYConservaTitulo() {
        val result = NaturalTaskParser.parse("Cita antepasado mañana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antepasadoMananaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cita antepasado mañana a las 10", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }



    // --- "principios de mes": vencimientos a inicios del mes (día 1) ---
    // Complemento natural de "fin de mes"/"mediados de mes": pagos, cierres, rentas que
    // vencen el día 1. Antes el patrón existía pero no había resolución (quedaba sin
    // fecha o caía a "+30d" por "mes"). now = 2026-07-29 (día 29 > 1) -> rueda al 1/8.

    @Test fun principiosDeMesRuedaAlDia1ProximoMes() {
        val result = NaturalTaskParser.parse("Pagar renta a principios de mes", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesResuelveDia1MesActualSiAunNoLlega() {
        // 2026-08-05 > 1 -> principios = 1/9 (el 1/8 ya pasó).
        val tempranoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 5), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Entrega principios de mes", tempranoNow, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesElMismoDia1EsHoy() {
        // hoy = 1/8 -> "principios de mes" vence hoy (no rueda al mes siguiente).
        val primeroNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 1), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro principios de mes", primeroNow, zone)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago principios de mes a las 10", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "primeros de mes": variante coloquial de "principios de mes" (vencimientos
    // financieros: alquiler, tarjeta, servicios). Antes caía a dueAt=null →
    // vencimiento olvidado. Se resuelve igual que "principios de mes" (día 1).

    @Test fun primerosDeMesVarianteDePrincipios() {
        val result = NaturalTaskParser.parse("pagar a primeros de mes", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "comienzos de mes": sinónimo pleno de "principios de mes". Antes caía a
    // dueAt=null → vencimiento olvidado. Se resuelve igual (día 1 del mes siguiente).

    @Test fun comienzosDeMesVarianteDePrincipios() {
        val result = NaturalTaskParser.parse("pago a comienzos de mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "la quincena" / "primera quincena" / "segunda quincena": hito financiero ---
    // La quincena es el hito de cobro/nómina/pago: dos por mes, el día 15 (primera) y el
    // fin de mes (segunda). Antes "cobro de la quincena" caía a dueAt=null → vencimiento
    // olvidado (sin recordatorio ni visibilidad), y "pago de la quincena a las 18" se
    // fechaba en HOY 18:00 (día erróneo). Simétrico a "fin de mes"/"mediados de mes".
    // now = 2026-07-29 (≥ 15): la quincena sin cualificar → fin de mes.

    @Test fun laQuincenaSinCualificarResuelveProximoHitoDia15SiAntes() {
        // 2026-08-13 < 15 → "la quincena" resuelve al día 15 (próximo hito).
        val antesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 13), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro de la quincena", antesNow, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laQuincenaSinCualificarResuelveFinDeMesSiPosteriorAl15() {
        // hoy = 29/7 ≥ 15 → "la quincena" resuelve a fin de mes (próximo hito).
        val result = NaturalTaskParser.parse("Cobro la quincena", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laQuincenaSinCualificarRuedaAl15ProximoMesSiHoyEsUltimoDia() {
        // 2026-08-31 = último día de agosto. La quincena de fin de mes cae HOY, así que
        // el próximo hito es el 15/9 (consistente con "fin de mes" que rueda al mes próximo).
        val ultNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 31), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro de la quincena", ultNow, zone)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laQuincenaRespetaHoraExplicita() {
        // 2026-08-13 < 15 → día 15; la hora explícita se aplica sobre la fecha del hito.
        val antesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 13), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago de la quincena a las 18", antesNow, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun primeraQuincenaResuelveDia15() {
        // 2026-08-13 < 15 → primera quincena = 15/8 (mes actual).
        val antesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 13), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Nómina de la primera quincena", antesNow, zone)
        assertEquals("Nómina", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun primeraQuincenaRuedaAProximoMesSiHoyPasadoEl15() {
        // hoy = 29/7 ≥ 15 → primera quincena rueda al 15 del mes siguiente.
        val result = NaturalTaskParser.parse("Cobro de la primera quincena", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun segundaQuincenaResuelveFinDeMes() {
        // hoy = 29/7 < 31 → segunda quincena = fin de mes (31/7).
        val result = NaturalTaskParser.parse("Pago de la segunda quincena", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun segundaQuincenaRuedaAProximoMesSiHoyEsUltimoDia() {
        // 2026-08-31 = último día de agosto → segunda quincena rueda al fin de septiembre.
        val ultNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 31), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro de la segunda quincena", ultNow, zone)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun primeraQuincenaAbreviada1ra() {
        val antesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 13), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro de la 1ra quincena", antesNow, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun segundaQuincenaAbreviada2da() {
        val result = NaturalTaskParser.parse("Pago de la 2da quincena", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximaQuincenaSigueResolviendoseComoPeriodoProximo() {
        // "próxima quincena" la resuelve nextPeriodPattern (+15d), no el hito de quincena.
        // Se asegura de que el nuevo patrón NO sombree ni rompa el comportamiento existente.
        // hoy = 2026-07-29 12:00 + 15d = 2026-08-13.
        val result = NaturalTaskParser.parse("Cobro próxima quincena", now, zone)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenaNoInterfiereConEnNQuincenasRelativo() {
        // "en 2 quincenas" lo resuelve relativePattern (+2×15d = +30d), no el hito.
        // hoy = 2026-07-29 + 30d = 2026-08-28.
        val result = NaturalTaskParser.parse("Reunión en 2 quincenas", now, zone)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "fines de semana" (plural) como recurrencia WEEKLY sábado+domingo ---
    // "cada fines de semana" / "los findes" expresa una tarea que se repite sábado Y
    // domingo. Antes "fines de semana" no coincidía con el patrón singular "fin de
    // semana" (queda como residuo en el título) ni generaba recurrencia. Ahora -> WEEKLY
    // con days=[6,7]. now = 2026-07-29 (miércoles) -> primera ocurrencia sábado 1/8.

    @Test fun finesDeSemanaComoRecurrenciaWeekendSabadoDomingo() {
        val result = NaturalTaskParser.parse("Limpieza cada fines de semana", now, zone)
        assertEquals("Limpieza", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
        // primera ocurrencia: sábado 2026-08-01
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun losFindesComoRecurrenciaWeekend() {
        val result = NaturalTaskParser.parse("Salir a correr los findes", now, zone)
        assertEquals("Salir a correr", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // --- "entre semana" / "días laborables/hábiles/de semana" / "de lunes a viernes" ---
    // Antes estas frases cotidianas (gimnasio, trabajo, estudio) quedaban sin recurrencia
    // (freq=NONE, tarea única) y "de lunes a viernes" dejaba "lunes" como residuo en el
    // título. Ahora -> WEEKLY days=[1..5]. Se evalúa ANTES que dayListPattern para que
    // "los lunes a viernes" sea un rango (no la lista ["lunes"]). now=2026-07-29
    // (miércoles) al mediodía: el miércoles (hoy) ya pasó su slot -> jueves 30-07.

    @Test fun entreSemanaComoRecurrenciaWeekdayLunAVie() {
        val result = NaturalTaskParser.parse("Gimnasio entre semana", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diasLaborablesComoRecurrenciaWeekday() {
        val result = NaturalTaskParser.parse("Estudiar días laborables", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun diasHabilesConDeterminanteComoRecurrenciaWeekday() {
        val result = NaturalTaskParser.parse("Reunión los días hábiles", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun deLunesAViernesComoRecurrenciaWeekdayLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Trabajo de lunes a viernes", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun losLunesAViernesComoRangoNoLista() {
        // Sin el orden de patrones, dayListPattern capturaría solo "lunes" (days=[1]).
        val result = NaturalTaskParser.parse("los lunes a viernes entrenar", now, zone)
        assertEquals("entrenar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun entreSemanaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Clase entre semana a las 7", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(7, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun finDeSemanaSingularNoEsRecurrenciaWeekday() {
        // "fin de semana" (singular) = fecha única (próximo sábado), NO recurrencia.
        val result = NaturalTaskParser.parse("Fin de semana viajar", now, zone)
        assertEquals("viajar", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    // --- "el jueves pasado" / "el último lunes" / "el martes anterior": fecha pasada ---
    // El usuario reconoce que la tarea quedó vencida ("pagué la factura el viernes
    // pasado"). Antes "el jueves pasado" se leía como "jueves" (próximo) por
    // weekdayPattern y "pasado" quedaba en el título -> fecha FUTURA errónea y título
    // sucio. Ahora se resuelve a la última ocurrencia PASADA (tarea vencida honesta,
    // visible en What Now como atrasada). now = 2026-07-29 (miércoles).

    @Test fun juevesPasadoResuelveUltimoJuevesPasado() {
        // miércoles 29 -> último jueves = 2026-07-23.
        val result = NaturalTaskParser.parse("Pagar factura el jueves pasado", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 23), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoLunesResuelveUltimoLunesPasado() {
        // miércoles 29 -> último lunes = 2026-07-27.
        val result = NaturalTaskParser.parse("Enviar reporte el último lunes", now, zone)
        assertEquals("Enviar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun martesAnteriorResuelveUltimoMartesPasado() {
        // miércoles 29 -> último martes = 2026-07-28.
        val result = NaturalTaskParser.parse("Revisión el martes anterior", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaPasadoSiHoyEsEseDiaVaASemanaAnterior() {
        // Si hoy es ese día, "el X pasado" refiere al de la semana anterior, no a hoy.
        // jueves 2026-07-23 -> "el jueves pasado" = 2026-07-16.
        val juevesNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 23), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el jueves pasado", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 7, 16), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaPasadoRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago el jueves pasado a las 15", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 23), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "pasado mañana": fecha relativa de dos días. Regresión P1: "entregar el
    // informe pasado mañana" se fechaba en MAÑANA (un día antes) y borraba "informe"
    // del título, porque previousWeekdayPattern casaba "el informe pasado" y se
    // consumía incondicionalmente (aunque "informe" no es día de la semana). ---

    @Test fun pasadoMananaResuelveDosDiasYConservaTitulo() {
        // hoy = miércoles 2026-07-29 -> pasado mañana = viernes 2026-07-31.
        val result = NaturalTaskParser.parse("Entregar el informe pasado mañana", now, zone)
        assertEquals("Entregar el informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun pasadoMananaConHoraConservaOffsetYTitulo() {
        val result = NaturalTaskParser.parse("Reunión pasado mañana a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun pasadoMananaNoConfundeSustantivoConDiaDeSemana() {
        // "el proyecto pasado mañana": "proyecto" no es día de semana, no debe
        // consumirse ni romper "pasado mañana", ni eliminarse del título.
        val result = NaturalTaskParser.parse("Revisar el proyecto pasado mañana", now, zone)
        assertEquals("Revisar el proyecto", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "esta semana": plazo blando = fin de la semana actual (próximo domingo) ---

    @Test fun estaSemanaParsesDueAtProximoDomingo() {
        // hoy = miércoles 2026-07-29 -> fin de semana ISO = domingo 2026-08-02.
        val result = NaturalTaskParser.parse("Llamar a mamá esta semana", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun estaSemanaRespetaHoraExplicita() {
        // Antes este caso se fechaba en HOY por error ("esta semana a las 18" → hoy 18:00).
        val result = NaturalTaskParser.parse("Terminar informe esta semana a las 18", now, zone)
        assertEquals("Terminar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaSemanaSiHoyEsDomingoEsHoy() {
        // hoy = domingo 2026-08-02 -> "esta semana" vence hoy (no rueda al próximo domingo).
        val domingoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 2), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cita esta semana", domingoNow, zone)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun estaSemanaNoColisionaConSemanaQueViene() {
        // "la semana que viene" debe seguir siendo +7d, no "esta semana".
        val result = NaturalTaskParser.parse("Entrega la semana que viene", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "a fin de la semana": plazo = fin de la semana actual (próximo domingo).
    // Sinónimo coloquial de "esta semana". Antes caía a dueAt=null → olvido.

    @Test fun finDeLaSemanaResuelveProximoDomingo() {
        val result = NaturalTaskParser.parse("Entregar a fin de la semana", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "principios de semana": plazo blando al lunes más cercano (hoy/futuro) ---
    // Antes caía a dueAt=null (olvido) o, con hora, a HOY por error. Ahora -> lunes.
    // now = 2026-07-29 (miércoles) -> "principios de semana" = lunes 2026-08-03.

    @Test fun principiosDeSemanaParsesDueAtProximoLunes() {
        // hoy = miércoles 2026-07-29 -> el lunes ya pasó esta semana -> lunes siguiente 2026-08-03.
        val result = NaturalTaskParser.parse("Revisar informe principios de semana", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeSemanaRespetaHoraExplicita() {
        // Antes este caso se fechaba en HOY por error ("principios de semana a las 9" -> hoy 9:00).
        val result = NaturalTaskParser.parse("Envío principios de semana a las 9", now, zone)
        assertEquals("Envío", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun principiosDeSemanaSiHoyEsLunesEsHoy() {
        // hoy = lunes 2026-08-03 -> "principios de semana" vence hoy (no rueda al lunes siguiente).
        val lunesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 3), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Kickoff principios de semana", lunesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "comienzos de semana": sinónimo pleno de "principios de semana". Antes caía a
    // dueAt=null (olvido). Se resuelve igual (lunes más cercano en hoy/futuro).

    @Test fun comienzosDeSemanaVarianteDePrincipios() {
        // hoy = miércoles 2026-07-29 -> lunes siguiente 2026-08-03.
        val result = NaturalTaskParser.parse("Revisar informe comienzos de semana", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeSemanaNoColisionaConSemanaQueViene() {
        // "la semana que viene" debe seguir siendo +7d (2026-08-05), no "principios de semana".
        val result = NaturalTaskParser.parse("Entrega la semana que viene", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "mediados de semana" (= miércoles más cercano en hoy/futuro) ---

    @Test fun mediadosDeSemanaSiHoyEsMiercolesEsHoy() {
        // hoy = miércoles 2026-07-29 -> "mediados de semana" vence hoy.
        val result = NaturalTaskParser.parse("Llamar al banco mediados de semana", now, zone)
        assertEquals("Llamar al banco", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeSemanaDesdeLunesEsMiercolesMismaSemana() {
        // hoy = lunes 2026-08-03 -> "mediados de semana" = miércoles 2026-08-05 (misma semana).
        val lunesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 3), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Revisar correo a mediados de semana", lunesNow, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeSemanaRespetaHoraExplicita() {
        // hoy = miércoles 2026-07-29 -> "mediados de semana a las 9" = hoy 09:00.
        val result = NaturalTaskParser.parse("Cita mediados de semana a las 9", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediadosDeSemanaNoColisionaConMediadosDeMes() {
        // "mediados de mes" sigue siendo día 15 (2026-07-15 ya pasó -> 2026-08-15),
        // no se confunde con "mediados de semana".
        val result = NaturalTaskParser.parse("Pagar factura a mediados de mes", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "mediados/mitad/principios DE LA semana": forma cotidiana con artículo ---
    // Antes el patrón sólo admitía "de semana"/"del semana", no "de LA semana" →
    // dueAt=null (vencimiento olvidado). Asimetría con "mediados de mes" (c.32) que SÍ
    // funcionaba. Resolución simétrica a las formas sin artículo.

    @Test fun mediadosDeLaSemanaAnclaMiercoles() {
        // hoy = miércoles 2026-07-29 -> "mediados de la semana" = hoy.
        val result = NaturalTaskParser.parse("Llamar al banco mediados de la semana", now, zone)
        assertEquals("Llamar al banco", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDeLaSemanaEsSinonimoDeMediados() {
        // "mitad de la semana" = "mediados de la semana" → miércoles (hoy).
        val result = NaturalTaskParser.parse("Revisar correo a mitad de la semana", now, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeLaSemanaAnclaLunes() {
        // hoy = miércoles 2026-07-29 -> "principios de la semana" = lunes siguiente 2026-08-03.
        val result = NaturalTaskParser.parse("Revisar informe principios de la semana", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeLaSemanaRespetaHoraExplicita() {
        // hoy = miércoles 2026-07-29 -> "mediados de la semana a las 9" = hoy 09:00.
        val result = NaturalTaskParser.parse("Cita mediados de la semana a las 9", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aMediadosDeLaSemanaConPrefijoAOpcional() {
        // "a mediados de la semana" (con "a" inicial) → miércoles (hoy).
        val result = NaturalTaskParser.parse("Reunión a mediados de la semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "hace N días/semanas/meses/años" y "la semana/el mes/el año pasado" ---
    // El usuario registra una tarea ya vencida ("pagué hace 2 días", "revisé el
    // informe la semana pasada"). Antes estas formas quedaban SIN fecha y con la frase
    // temporal intacta en el título -> tarea sin recordatorio y con basura. Ahora se
    // resuelven a una fecha PASADA (tarea vencida honesta, visible en What Now) y se
    // borran del título. now = 2026-07-29 (miércoles) al mediodía.

    @Test fun haceNdiasResuelveFechaPasada() {
        // 2026-07-29 - 2 días = 2026-07-27.
        val result = NaturalTaskParser.parse("Pagar factura hace 2 días", now, zone)
        assertEquals("Pagar factura", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceUnaSemanaResuelveFechaPasada() {
        // 2026-07-29 - 7 días = 2026-07-22.
        val result = NaturalTaskParser.parse("Enviar correo hace una semana", now, zone)
        assertEquals("Enviar correo", result.title)
        assertEquals(LocalDate.of(2026, 7, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceNmesesResuelveFechaPasada() {
        // 2026-07-29 - 3 meses (90 días) = 2026-04-30.
        val result = NaturalTaskParser.parse("Auditar hace 3 meses", now, zone)
        assertEquals("Auditar", result.title)
        assertEquals(LocalDate.of(2026, 4, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceNdiasConHoraAplicaHoraSobreFechaPasada() {
        // La hora explícita se aplica sobre la fecha pasada (tarea vencida con hora).
        val result = NaturalTaskParser.parse("Pago hace 2 días a las 10", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun laSemanaPasadaResuelveFechaPasada() {
        // 2026-07-29 - 7 días = 2026-07-22.
        val result = NaturalTaskParser.parse("Revisar informe la semana pasada", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesPasadoResuelveFechaPasada() {
        // 2026-07-29 - 30 días = 2026-06-29. Antes previousWeekdayPattern capturaba
        // "el mes pasado" (grupo1="mes", no es día -> sin fecha) y borraba la frase,
        // dejando dueAt=null. Ahora lastPeriodPattern se detecta antes y resta el período.
        val result = NaturalTaskParser.parse("Reunión el mes pasado", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesPasadoConHoraAplicaHora() {
        val result = NaturalTaskParser.parse("Reunión el mes pasado a las 15", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "anterior": sinónimo de "pasado" para períodos (semana/mes/año) ---
    // "la semana anterior", "el mes anterior", "el año anterior" son sinónimos
    // plenos de "...pasado/a". Antes caían a dueAt=null + residuo "anterior" en el
    // título (asimetría: "...pasado" sí se fechaba). now=2026-07-29 (miércoles).

    @Test fun laSemanaAnteriorResuelveFechaPasada() {
        // 2026-07-29 - 7 días = 2026-07-22.
        val result = NaturalTaskParser.parse("Revisar informe la semana anterior", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesAnteriorResuelveFechaPasada() {
        // 2026-07-29 - 30 días = 2026-06-29.
        val result = NaturalTaskParser.parse("Reunión el mes anterior", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elAnioAnteriorResuelveFechaPasada() {
        // 2026-07-29 - 365 días = 2025-07-29.
        val result = NaturalTaskParser.parse("Auditoría el año anterior", now, zone)
        assertEquals("Auditoría", result.title)
        assertEquals(LocalDate.of(2025, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elMesAnteriorConHoraAplicaHora() {
        val result = NaturalTaskParser.parse("Reunión el mes anterior a las 15", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hacePocoResuelveHaceTresHoras() {
        // "hace poco"/"hace un rato" = -3 h (heurística honesta de "recién").
        // now=12:00 -> 09:00 del mismo día.
        val result = NaturalTaskParser.parse("hace poco", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnRatoLimpiaTituloSinResiduo() {
        // "un rato" debe capturarse completo, no trocearse dejando "rato" en el título.
        val result = NaturalTaskParser.parse("Llamé hace un rato", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "a finales de semana" / "finales de semana" (= próximo sábado, fecha única) ---
    // Forma plural análoga a "finales de mes": señala un fin de semana concreto, no un
    // hábito ("lo termino a finales de semana"). Antes NO casaba "fin de semana" (singular)
    // y caía a dueAt=null -> tarea olvidada. Ahora resuelve como "fin de semana" = sábado.
    // OJO: "fines de semana" (f-i-n-e-s) sigue siendo recurrencia semanal, no se toca.

    @Test fun aFinalesDeSemanaProgramaProximoSabadoYLimpiaTitulo() {
        // hoy = miércoles 2026-07-29 -> sábado 2026-08-01, hora canónica 09:00.
        val result = NaturalTaskParser.parse("Enviar el informe a finales de semana", now, zone)
        assertEquals("Enviar el informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun finalesDeSemanaSueltoProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar código finales de semana", now, zone)
        assertEquals("Revisar código", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeSemanaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Fiesta a finales de semana a las 20:00", now, zone)
        assertEquals("Fiesta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun finalesDeSemanaNoColisionaConRecurrenciaFinesDeSemana() {
        // "fines de semana" (f-i-n-e-s) sigue siendo recurrencia WEEKLY sáb+dom, no fecha única.
        val result = NaturalTaskParser.parse("Limpieza cada fines de semana", now, zone)
        assertEquals("Limpieza", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // --- "un par de" (coloquial = 2): "en un par de días/semanas/meses" ---

    @Test fun unParDeDiasResuelveMasDosDias() {
        // now 2026-07-29 + 2 días = 2026-07-31.
        val result = NaturalTaskParser.parse("Revisar propuesta en un par de días", now, zone)
        assertEquals("Revisar propuesta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun unParDeSemanasResuelveMasCatorceDias() {
        // now 2026-07-29 + 14 días = 2026-08-12.
        val result = NaturalTaskParser.parse("Enviar borrador en un par de semanas", now, zone)
        assertEquals("Enviar borrador", result.title)
        assertEquals(LocalDate.of(2026, 8, 12), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun unParDeMesesResuelveMasSesentaDias() {
        // now 2026-07-29 + 60 días = 2026-09-27.
        val result = NaturalTaskParser.parse("Renovar suscripción en un par de meses", now, zone)
        assertEquals("Renovar suscripción", result.title)
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun unParDeDiasConHoraExplicita() {
        // La fecha relativa se combina con hora explícita: +2d a las 10:00.
        val result = NaturalTaskParser.parse("Llamar al cliente en un par de días a las 10", now, zone)
        assertEquals("Llamar al cliente", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "el viernes a las 18" escrito el PROPIO viernes ANTES de esa hora: la cita es HOY.
    // Antes nextWeekday siempre saltaba +7 y la reunión de hoy se perdía una semana.
    private val fridayNow = DateRules.toEpochMillis(LocalDate.of(2026, 2, 13), LocalTime.of(10, 30), zone)

    @Test fun weekdayHoyConHoraFuturaQuedaHoy() {
        val result = NaturalTaskParser.parse("Reunión el viernes a las 18", fridayNow, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 2, 13), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun weekdayHoyConHoraPasadaRuedaProximaSemana() {
        // 10:00 < ahora (10:30) → próximo viernes.
        val result = NaturalTaskParser.parse("Reunión el viernes a las 10", fridayNow, zone)
        assertEquals(LocalDate.of(2026, 2, 20), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun weekdayHoySinHoraYMediodiaPasadoRuedaProximaSemana() {
        // Sin hora → default 09:00, ya pasó a las 10:00 → próximo viernes (sin regresión).
        val result = NaturalTaskParser.parse("Cita el viernes", fridayNow, zone)
        assertEquals(LocalDate.of(2026, 2, 20), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun weekdayHoyTardeConHoraFuturaQuedaHoy() {
        // viernes 18:00 dicho a las 10:00 → hoy; prueba también "de la tarde".
        val result = NaturalTaskParser.parse("Cena el viernes a las 8 de la tarde", fridayNow, zone)
        assertEquals(LocalDate.of(2026, 2, 13), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- día del mes suelto ("el 15"): anclar al día N del mes corriente/próximo ---
    // Antes "el 15" no casaba con numericDatePattern (exige DD/MM) y se ignoraba como
    // fecha: la hora suelta ("a las 10") se aplicaba a HOY → la cita quedaba hoy en vez
    // del día 15 (P1: día erróneo, reunión perdida). now = 2026-07-29.

    @Test fun diaDelMesSueltoRuedaAlProximoMesSiYaPaso() {
        // "el 15" con hoy=29 → 15 ya pasó este mes → 2026-08-15.
        val result = NaturalTaskParser.parse("Reunión el 15 a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun diaDelMesSueltoSinHoraUsaMediodiaCanónico() {
        // Sin hora → default 09:00. hoy=29 → "el 5" → 2026-08-05 09:00.
        val result = NaturalTaskParser.parse("Cita el 5", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun diaDelMesSueltoFuturoEsteMesSeConserva() {
        // "el 31" con hoy=29 (julio tiene 31) → 2026-07-31 (mismo mes, futuro).
        val result = NaturalTaskParser.parse("Cerrar facturas el 31", now, zone)
        assertEquals("Cerrar facturas", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaDelMesSueltoNoColisionaConFechaConMes() {
        // "el 15 de marzo" debe resolver mes explícito (monthNameDate), no "el 15" suelto.
        val result = NaturalTaskParser.parse("Viaje el 15 de marzo", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2027, 3, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "de aquí a N ..." / "de acá a N ...": formas coloquiales de "en/dentro de N".
    // Antes no se parseaban → dueAt=null y la frase quedaba como residuo en el título
    // (tarea sin recordatorio, invisible en planificador/What Now → olvidada).

    @Test fun deAquiATresDiasParsesDueAt() {
        val result = NaturalTaskParser.parse("Llamar al dentista de aquí a tres días", now, zone)
        assertEquals("Llamar al dentista", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAUnaSemanaParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión de aquí a una semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAUnMesParsesDueAt() {
        val result = NaturalTaskParser.parse("Viaje de aquí a un mes", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiANDiasRespetaHoraExplicita() {
        // La fecha relativa debe combinar con la hora explícita (+2 días a las 9:00).
        val result = NaturalTaskParser.parse("Entrega de aquí a dos días a las 9", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deAcaAUnaSemanaParsesDueAt() {
        // Variante "de acá a" (coloquial, sin tilde en 'a').
        val result = NaturalTaskParser.parse("Control de acá a una semana", now, zone)
        assertEquals("Control", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Recurrencia con intervalo escrito (no dígito): "cada dos semanas",
    // "cada tres meses", "cada quince días". Antes `intervalPattern` sólo admitía
    // `\d{1,3}`, así que estas formas caían a NONE y la tarea nacía SIN fecha
    // (invisible en What Now/planificador, recordatorio jamás disparaba). Ahora el
    // grupo admite números escritos y se resuelven vía `parseWrittenNumber`.
    @Test fun cadaDosSemanasParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Visitar a mi madre cada dos semanas", now, zone)
        assertEquals("Visitar a mi madre", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaTresMesesParsesMonthlyInterval3() {
        val result = NaturalTaskParser.parse("Dentista cada tres meses", now, zone)
        assertEquals("Dentista", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaQuinceDiasParsesDailyInterval15() {
        val result = NaturalTaskParser.parse("Reunión cada quince días", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaDosAnosParsesYearlyInterval2() {
        val result = NaturalTaskParser.parse("Renovar pasaporte cada dos años", now, zone)
        assertEquals("Renovar pasaporte", result.title)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // --- Hora suelta con parte del día, SIN "a las" (ciclo 62) ---
    // "Taller 9 de la tarde" debe resolver la HORA EXPLÍCITA (21:00), no la canónica de la
    // tarde (15:00). Antes el número se ignoraba y caía a 15:00/21:00/09:00/04:00, dejando
    // además el número como residuo en el título ("Taller 9"). Solo aplica a la forma simple
    // sin "a las"; con "a las" ya lo resuelve timePatterns y no debe haber residuo.

    @Test fun standaloneNueveDeLaTardeResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Taller 9 de la tarde", now, zone)
        assertEquals("Taller", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneOchoDeLaNocheResuelve20h() {
        val result = NaturalTaskParser.parse("Clase 8 de la noche", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneDiezDeLaMananaResuelve10h() {
        val result = NaturalTaskParser.parse("Cita 10 de la mañana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneNueveDeLaMadrugadaResuelve9h() {
        val result = NaturalTaskParser.parse("Evento 9 de la madrugada", now, zone)
        assertEquals("Evento", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneDosDeLaTardeResuelve14h() {
        val result = NaturalTaskParser.parse("Reunión 2 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneDoceDeLaNocheEsMedianoche() {
        val result = NaturalTaskParser.parse("Cita 12 de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun standaloneDoceDeLaTardeEsMediodia() {
        val result = NaturalTaskParser.parse("Cita 12 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Con "a las" no debe dejar residuo ("a las") ni duplicar resolución.
    @Test fun conALasNueveDeLaTardeNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Reunión el viernes a las 9 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "Jugar tenis de la tarde" (sin número) sigue dando la canónica 15:00.
    @Test fun sinNumeroDeLaTardeMantieneCanonica15h() {
        val result = NaturalTaskParser.parse("Jugar tenis de la tarde", now, zone)
        assertEquals("Jugar tenis", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Regresión monthName: find() casaba "9 de la" (mes "la" inválido) primero y
    // ocultaba "el 15 de agosto" posterior → la cita se agendaba para HOY en lugar del
    // 15/8 (cita futura perdida como evento de hoy). ---

    @Test fun nueveDeLaTardeConFechaMesResuelveAmbos() {
        val result = NaturalTaskParser.parse("Taller 9 de la tarde el 15 de agosto", now, zone)
        assertEquals("Taller", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nueveDeLaMananaConFechaMesResuelveAmbos() {
        val result = NaturalTaskParser.parse("Reunión 9 de la mañana el 20 de septiembre", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 20), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Regresión P0: "el 0 de septiembre" (día fuera de rango en fecha con mes
    // nombrado) lanzaba DateTimeException no capturada -> crash de la app ante input
    // de texto libre. Ahora devuelve dueAt=null y deja la frase como título, sin caer. ---

    @Test fun diaCeroDeMesNoCrashYDejaSinFecha() {
        val result = NaturalTaskParser.parse("el 0 de septiembre", now, zone)
        assertNull(result.dueAt)
    }

    @Test fun diaNoventaYNueveDeMesNoCrashYDejaSinFecha() {
        val result = NaturalTaskParser.parse("el 99 de enero", now, zone)
        assertNull(result.dueAt)
    }

    @Test fun diaCeroCeroDeMesNoCrashYDejaSinFecha() {
        val result = NaturalTaskParser.parse("el 00 de marzo", now, zone)
        assertNull(result.dueAt)
    }

    // --- Regresión P1: "el 15 de agosto del 2027" (español usa "del" antes del año,
    // no "de") no capturaba el año -> se agendaba para 2026 en vez de 2027 y dejaba
    // "del 2027" como residuo en el título. ---

    @Test fun mesNombreConDelAnioAgendaAnioCorrecto() {
        val result = NaturalTaskParser.parse("el 15 de agosto del 2027 a las 10", now, zone)
        assertEquals(LocalDate.of(2027, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mesNombreConDelAnioNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("el 10 de septiembre del 2026", now, zone)
        assertEquals(LocalDate.of(2026, 9, 10), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("el 10 de septiembre del 2026", result.title)
    }

    @Test fun mesNombreConDelAnioDosDigitosAgendaCorrecto() {
        val result = NaturalTaskParser.parse("el 10 de septiembre del 26", now, zone)
        assertEquals(LocalDate.of(2026, 9, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Regresión "día que viene"/"próximo día" dicho en el propio día objetivo ---
    // Antes, "jueves que viene" escrito un jueves caía en HOY (weekdaySameDayCandidate
    // permitía hoy) en vez de la próxima semana → tarea agendada el día equivocado (P1).
    // El modificador "que viene"/"próximo" debe forzar +7 incluso cuando hoy es ese día.

    // Jueves 2026-08-13 a medianoche (antes de las 09:00 canónicas, hora futura).
    private val juevesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 13), LocalTime.MIDNIGHT, zone)

    @Test fun juevesQueVieneDichoEnJuevesAvanzaUnaSemana() {
        val result = NaturalTaskParser.parse("Reunión el jueves que viene", juevesNow, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun juevesQueVieneConHoraAvanzaUnaSemana() {
        val result = NaturalTaskParser.parse("Reunión jueves que viene a las 9", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun proximoJuevesDichoEnJuevesAvanzaUnaSemana() {
        val result = NaturalTaskParser.parse("Reunión el próximo jueves", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun juevesSueltoDichoEnJuevesPuedeSerHoySiHoraFutura() {
        // Sin modificador "que viene"/"próximo", "el jueves a las 18" dicho el jueves
        // (hora futura respecto a medianoche) sigue siendo HOY: la cita es hoy.
        val result = NaturalTaskParser.parse("Reunión el jueves a las 18", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun viernesQueVieneEsManana() {
        // "viernes que viene" dicho en jueves → viernes 2026-08-14 (mañana).
        val result = NaturalTaskParser.parse("Reunión viernes que viene", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun martesQueVieneEsLaProximaSemana() {
        val result = NaturalTaskParser.parse("Reunión martes que viene", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 18), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun lunesProximosAvanzaUnaSemana() {
        // Hoy jueves 13/8; "lunes próximos" → lunes 17/8.
        val result = NaturalTaskParser.parse("Reunión lunes próximos", juevesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 17), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el día N" es la forma más cotidiana en español de fijar un día de mes suelto.
    // Antes solo "el N" casaba con dayOfMonthPattern; "el día 30" caía a dueAt=null
    // (pérdida silenciosa de la cita) o dejaba "el día" como residuo en el título
    // cuando otra rama (mensual, mes con nombre) consumía la fecha. La palabra
    // "día" (con y sin tilde) ahora se admite en dayOfMonthPattern, nextMonthDayPattern,
    // monthlyDayPattern y monthNamePattern para que la fecha se resuelva y el título
    // quede limpio.
    @Test fun parsesElDiaStandaloneDayOfMonth() {
        val result = NaturalTaskParser.parse("Reunión el día 30", now, zone)
        assertEquals("Reunión", result.title)
        // now=29-jul; el 30 todavía no llegó → 30-jul.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesElDiaWithoutTilde() {
        val result = NaturalTaskParser.parse("Reunión el dia 3", now, zone)
        assertEquals("Reunión", result.title)
        // now=29-jul; el 3 ya pasó en julio → 3-ago.
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaNextMonthResolvesToNextMonth() {
        val result = NaturalTaskParser.parse("Entregar el día 1 del mes que viene", now, zone)
        assertEquals("Entregar", result.title)
        // now=29-jul; "mes que viene" = agosto → 1-ago.
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaWithExplicitHour() {
        val result = NaturalTaskParser.parse("Reunión el día 15 a las 10", now, zone)
        assertEquals("Reunión", result.title)
        // now=29-jul; el 15 ya pasó en julio → 15-ago.
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaMonthlyRecurrenceCleanTitle() {
        // "el día 15 de cada mes": antes "el día" sobraba porque monthlyDayPattern
        // no consumía la palabra "día". Ahora se ancla y se limpia.
        val result = NaturalTaskParser.parse("Pago el día 15 de cada mes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // 29-jul → el 15 ya pasó este mes → 15-ago.
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaMonthNameCleanTitle() {
        // "el día 1 de enero": antes "el día" sobraba porque monthNamePattern no
        // consumía la palabra "día". Ahora se resuelve al 1 de enero (próximo año).
        val result = NaturalTaskParser.parse("Reunión el día 1 de enero", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "a última hora" (hora canónica de fin de jornada, simétrica a "a primera hora") ---

    @Test fun ultimaHoraInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión el viernes a última hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ultimaHoraSinFechaUsaHoy() {
        val result = NaturalTaskParser.parse("Terminar a última hora", now, zone)
        assertEquals("Terminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ultimaHoraConParteDelDiaEspecificaRespetaEsaHora() {
        // "a última hora de la tarde": la parte del día explícita (tarde) tiene prioridad
        // sobre la canónica genérica de fin de jornada. No debe quedar residuo.
        val result = NaturalTaskParser.parse("Reunión a última hora de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ultimaHoraDeLaNocheRespetaCanonicaNoche() {
        val result = NaturalTaskParser.parse("Cena a última hora de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ultimaHoraConFechaRelativaCombinaBien() {
        val result = NaturalTaskParser.parse("Llamar mañana a última hora", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ultimaHoraSinConectorATambienFunciona() {
        // "última hora" sin el conector "a" (con tilde en la ú) también debe
        // interpretarse como fin de jornada y limpiar el título. El boundary \b
        // ASCII no funciona antes de "ú", por eso se usa un lookbehind Unicode.
        val result = NaturalTaskParser.parse("Terminar el viernes última hora", now, zone)
        assertEquals("Terminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "al final del día" / "al final de la jornada" (sinónimo de "a última hora") ---
    // Antes estas frases cotidianas de fin de jornada no casaban ningún patrón →
    // dueAt=null (tarea SIN vencimiento → olvidada) y la frase quedaba como residuo
    // en el título. Asimetría con "a última hora"=18:00 (c.102). Ahora resuelven a
    // 18:00 y limpian el título, igual que "a última hora".

    @Test fun alFinalDelDiaInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión al final del día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alFinalDelDiaSinTildeTambienFunciona() {
        val result = NaturalTaskParser.parse("Reunión al final del dia", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinalDeLaJornadaFunciona() {
        val result = NaturalTaskParser.parse("Terminar al final de la jornada", now, zone)
        assertEquals("Terminar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinalDelDiaConFechaRelativaCombinaBien() {
        val result = NaturalTaskParser.parse("Llamar mañana al final del día", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alFinalDelDiaConFechaSemanaCombinaBien() {
        val result = NaturalTaskParser.parse("Reunión el viernes al final del día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alFinalDelDiaNoEsFalsoPositivoEnFraseDistinta() {
        // "fase final del proyecto": "final" no va precedido de "al " y no es fin de
        // jornada. No debe asignar 18:00 ni borrar nada.
        val result = NaturalTaskParser.parse("Reunión fase final del proyecto", now, zone)
        assertEquals("Reunión fase final del proyecto", result.title)
        assertEquals(null, result.dueAt)
    }

    // --- "al amanecer" / "al alba" / "al despuntar el día" (hora canónica de salida del sol) ---
    // Antes estas frases cotidianas de muy temprano no casaban ningún patrón → dueAt=null
    // (tarea SIN vencimiento → olvidada, invisible en What Now/planificador, sin
    // recordatorio) y la frase quedaba como residuo en el título. Asimetría con
    // "al mediodía"=12:00, "a medianoche"=00:00, "a primera hora"=09:00. El amanecer es
    // la primera luz (~06:00): distinta de "madrugada" (04:00, franja nocturna) y de
    // "a primera hora" (09:00, inicio de jornada). Exige conector "al " para no casar
    // el verbo "amanecer" ni el sustantivo poético suelto.

    @Test fun alAmanecerInterpretaPrimeraLuzYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Caminar al amanecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alAlbaEsSinonimoDeAmanecer() {
        val result = NaturalTaskParser.parse("Caminar al alba", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alDespuntarElDiaEsSinonimoDeAmanecer() {
        val result = NaturalTaskParser.parse("Caminar al despuntar el día", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alClarearYAlAclararSonSinonimosDeAmanecer() {
        val r1 = NaturalTaskParser.parse("Caminar al clarear", now, zone)
        val r2 = NaturalTaskParser.parse("Caminar al aclarar", now, zone)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(r1.dueAt!!, zone))
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(r2.dueAt!!, zone))
    }

    @Test fun alAmanecerCombinaConFechaRelativa() {
        val result = NaturalTaskParser.parse("Caminar mañana al amanecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alAmanecerHoraExplicitaTienePrioridad() {
        // "al amanecer a las 5": la hora explícita gana sobre la canónica de respaldo.
        val result = NaturalTaskParser.parse("Caminar al amanecer a las 5", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun amanecerSinConectorAlNoEsFalsoPositivo() {
        // "un amanecer hermoso" (sustantivo poético sin "al") no debe agendarse.
        val result = NaturalTaskParser.parse("Ver un amanecer hermoso", now, zone)
        assertEquals("Ver un amanecer hermoso", result.title)
        assertEquals(null, result.dueAt)
    }

    // --- "al atardecer" / "al anochecer" / "al ocaso": contraparte vespertina del amanecer ---

    @Test fun alAtardecerInterpretaOcasoYLimpiaTitulo() {
        // Antes "caminar al atardecer" → due=null + residuo "al atardecer" → tarea olvidada.
        val result = NaturalTaskParser.parse("Caminar al atardecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alAnochecerEsSinonimoDeAtardecer() {
        val result = NaturalTaskParser.parse("Reunión al anochecer", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alOcasoEsSinonimoDeAtardecer() {
        val result = NaturalTaskParser.parse("Pasear al ocaso", now, zone)
        assertEquals("Pasear", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alPonerseElSolEsSinonimoDeAtardecer() {
        val result = NaturalTaskParser.parse("Caminar al ponerse el sol", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alAtardecerCombinaConFechaRelativa() {
        val result = NaturalTaskParser.parse("Caminar mañana al atardecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alAtardecerHoraExplicitaTienePrioridad() {
        // "al atardecer a las 7": la hora explícita gana sobre la canónica de respaldo.
        val result = NaturalTaskParser.parse("Caminar al atardecer a las 7", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(19, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun atardecerSinConectorAlNoEsFalsoPositivo() {
        // "ver el atardecer" (sustantivo sin "al") no debe agendarse.
        val result = NaturalTaskParser.parse("Ver el atardecer", now, zone)
        assertEquals("Ver el atardecer", result.title)
        assertEquals(null, result.dueAt)
    }

    // --- "a mediodía" / "a medianoche" sin contracción "al" limpian el conector del título ---

    @Test fun aMediodiaSinContraccionLimpiaTitulo() {
        // Antes "a mediodía" (sin "al") dejaba residuo "a" en el título.
        val result = NaturalTaskParser.parse("Reunión a mediodía", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.NOON, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aMedianocheSinContraccionLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a medianoche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "Ahora" inmediato ("ahora mismo"/"ahorita"/"ahora"/"lo antes posible") ---
    // Antes estas frases cotidianas no casaban ningún patrón → dueAt=null → tarea SIN
    // vencimiento, invisible en "What Now"/planificador, sin recordatorio → olvidada
    // (P1). Ahora se resuelven a `now` y consumen la frase para dejar el título limpio.
    // NOTA: "enseguida"/"en seguida" quedan cubiertas a +1h por [vagueRelativePattern]
    // (ciclo 106, commit 30b62d5 de otra ejecución); aquí NO se duplican para no
    // sobrescribir trabajo válido ni diverger en semántica.
    @Test fun ahoraMismoVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a Ana ahora mismo", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun ahoritaVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Avisar ahorita", now, zone)
        assertEquals("Avisar", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun ahoraSoloVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión ahora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun loAntesPosibleVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar bug lo antes posible", now, zone)
        assertEquals("Revisar bug", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun cuantoAntesVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Responder cuanto antes", now, zone)
        assertEquals("Responder", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun aLaBrevedadVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Confirmar a la brevedad", now, zone)
        assertEquals("Confirmar", result.title)
        assertEquals(now, result.dueAt)
    }

    // --- "ya" / "ya mismo" como "ahora" inmediato (P1, ciclo 112) ---
    // "ya" es la forma cotidiana por excelencia de "hazlo ahora"; antes no casaba
    // ningún patrón → dueAt=null → tarea SIN vencimiento, invisible en "What Now"/
    // planificador, sin recordatorio → olvidada. Ahora se resuelve a `now`. El \b
    // de la regex protege contra falsos en palabras que contienen "ya" (playa/raya).
    @Test fun yaFinalVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan ya", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun yaMismoVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a mamá ya mismo", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun paraYaVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión para ya", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now, result.dueAt)
    }

    // --- Residuo de determinante "este <día>" (P1, ciclo 129) ---
    // "este lunes"/"este martes"/... indican el próximo día de la semana (igual que
    // "el lunes"), pero el determinante "este" no lo consumía weekdayPattern: la fecha
    // se resolvía bien pero "este" quedaba pegado al título ("reunión este"). P1 de
    // integridad de título. El determinante se consume ahora en el propio patrón.
    @Test fun esteLunesResuelveFechaYLimpiaDeterminante() {
        val result = NaturalTaskParser.parse("reunión este lunes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteSabadoResuelveFechaYLimpiaDeterminante() {
        val result = NaturalTaskParser.parse("reunión este sábado", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "este" como determinante de contenido NO debe borrarse: solo se consume cuando
    // antecede a un día de la semana. Antes ningún patrón lo tocaba; ahora weekdayPattern
    // lo consume sólo en ese contexto, dejando intacto "este proyecto"/"este libro".
    @Test fun esteComoDeterminanteDeContenidoNoSeBorra() {
        val result = NaturalTaskParser.parse("revisar este proyecto", now, zone)
        assertEquals("revisar este proyecto", result.title)
        assertNull(result.dueAt)
    }

    // --- Residuo "en la <parte> de hoy/mañana" (P1, ciclo 129) ---
    // "en la tarde de hoy"/"en la noche de mañana" (forma caribeña) dejaba el residuo
    // "de hoy"/"de mañana" en el título: standalonePartOfDayPattern consumía solo "en la
    // tarde" y el sufijo "de hoy" caía al título. P1 de integridad. Ahora el patrón
    // consume también el sufijo "de hoy/mañana/ayer".
    @Test fun enLaTardeDeHoyLimpiaSufijoYResuelveHoraCanonica() {
        val result = NaturalTaskParser.parse("reunión en la tarde de hoy", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun enLaNocheDeHoyLimpiaSufijoYResuelve21h() {
        val result = NaturalTaskParser.parse("reunión en la noche de hoy", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun porLaTardeDeMananaLimpiaSufijoYResuelveFechaYHora() {
        val result = NaturalTaskParser.parse("reunión por la tarde de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Conector "a" en "a la semana que viene" (P1, ciclo 129) ---
    // "entregar a la semana que viene": la preposición "a" (dirección temporal) no la
    // consumía nextPeriodPattern, así que "a" quedaba como residuo ("entregar a") aunque
    // la fecha se resolvía bien. P1 de integridad de título. Ahora el patrón admite un
    // "a" conector opcional (con guard de letra previa para no robar la "a" final de
    // palabras como "Auditoría").
    @Test fun aLaSemanaQueVieneLimpiaConectorYResuelveFecha() {
        val result = NaturalTaskParser.parse("entregar a la semana que viene", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaQueVieneSinConectorSigueFuncionando() {
        val result = NaturalTaskParser.parse("entregar la semana que viene", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Guard: la "a" final de "Auditoría" NO debe robarse como conector. Regresión del
    // ciclo 129 al añadir el conector "a" sin guard de letra previa.
    @Test fun palabraConAFinalNoSeTruncaComoConector() {
        val result = NaturalTaskParser.parse("Auditoría próximo trimestre", now, zone)
        assertEquals("Auditoría", result.title)
        assertEquals(LocalDate.of(2026, 10, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "ahorita mismo" (P1, ciclo 129) ---
    // "ahorita mismo" (caribeño) no casaba: nowPattern tenía "ahorita" pero la
    // alternancia corta ganaba y dejaba "mismo" como residuo en el título. Ahora se
    // lista "ahorita mismo" antes que "ahorita" para robar la frase completa.
    @Test fun ahoritaMismoVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar ahorita mismo", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun yaNoCasadentroDeOtraPalabra() {
        // "playa"/"raya" contienen "ya" pero NO deben vencer a `now`: son contenido.
        val result = NaturalTaskParser.parse("Comprar una playa", now, zone)
        assertEquals("Comprar una playa", result.title)
        assertNull(result.dueAt)
    }

    // P0 integridad de datos: si el título contiene "ya" dentro de otra palabra
    // (maya/playa/raya) Y termina con el token "ya", el parser solo debe borrar el
    // token final, no todas las ocurrencias de "ya". Antes usaba working.replace(it.value)
    // (global, literal) y corrompía "comprar maya ya" → "comprar ma".
    @Test fun yaFinalNoCorrompePalabrasQueContienenYa() {
        val result = NaturalTaskParser.parse("Comprar maya ya", now, zone)
        assertEquals("Comprar maya", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun yaFinalNoCorrompePlayaNiRaya() {
        val result1 = NaturalTaskParser.parse("Reservar en la playa para ya", now, zone)
        assertEquals("Reservar en la playa", result1.title)
        assertEquals(now, result1.dueAt)
        val result2 = NaturalTaskParser.parse("Volar cometa en la raya ya", now, zone)
        assertEquals("Volar cometa en la raya", result2.title)
        assertEquals(now, result2.dueAt)
    }

    // P0 integridad de datos (generalización del fix "ya"): el borrado de un token
    // temporal del título debe afectar SOLO la ocurrencia matched, no todas las
    // apariciones literales del token. Si el usuario repite el mismo texto del
    // token como contenido (p. ej. "revisar quincena y otra quincena pasada"), el
    // parser no debe borrar la segunda ocurrencia. Antes usaba
    // working.replace(it.value, " ") (global, literal) y la eliminaba.
    @Test fun tokenRepetidoComoContenidoNoSeBorraGlobalmente_quincena() {
        val result = NaturalTaskParser.parse("Revisar quincena y otra quincena pasada", now, zone)
        assertEquals("Revisar y otra quincena pasada", result.title)
    }

    @Test fun tokenRepetidoComoContenidoNoSeBorraGlobalmente_semanaPasada() {
        val result = NaturalTaskParser.parse("Resumen semana pasada y otra semana pasada", now, zone)
        assertEquals("Resumen y otra semana pasada", result.title)
    }

    @Test fun tokenRepetidoComoContenidoNoSeBorraGlobalmente_proximosDias() {
        val result = NaturalTaskParser.parse("Viaje próximos días y más próximos días", now, zone)
        assertEquals("Viaje y más próximos días", result.title)
    }

    @Test fun loMasProntoPosibleVenceAhoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Empezar lo más pronto posible", now, zone)
        assertEquals("Empezar", result.title)
        assertEquals(now, result.dueAt)
    }

    @Test fun masTardeVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a mamá más tarde", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun masRatoVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar correo más rato", now, zone)
        assertEquals("Revisar correo", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun despuesSueltoVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Enviar factura después", now, zone)
        assertEquals("Enviar factura", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun despuesSinTildeVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Enviar factura despues", now, zone)
        assertEquals("Enviar factura", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun masTardeSinTildeVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a Ana mas tarde", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun despuesDelAlmuerzoNoEsAdverbioSuelto() {
        // "después del almuerzo" es dependencia/evento, no adverbio "luego": NO casa.
        val result = NaturalTaskParser.parse("Llamar después del almuerzo", now, zone)
        assertNull(result.dueAt)
        assertEquals("Llamar después del almuerzo", result.title)
    }

    // --- "luego" como sinónimo de "después"/"más tarde" (P1, ciclo 113) ---
    // "luego" es uso cotidísimo ("avísale luego", "lo hago luego"); antes no casaba
    // ningún patrón → dueAt=null → tarea sin vencimiento, invisible en What Now y sin
    // recordatorio programable → olvidada. Ahora se resuelve a +3 h, igual que
    // "después"/"más tarde", y limpia el título. La exclusión "luego del/de la N"
    // (dependencia) se respeta, simétrica a "después del/de la N".
    @Test fun luegoSueltoVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Avisar luego", now, zone)
        assertEquals("Avisar", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun luegoEnFraseVenceMasTardeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Enviar factura luego", now, zone)
        assertEquals("Enviar factura", result.title)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
    }

    @Test fun luegoDelAlmuerzoNoEsAdverbioSuelto() {
        // "luego del almuerzo" es dependencia/evento, no adverbio: NO casa.
        val result = NaturalTaskParser.parse("Llamar luego del almuerzo", now, zone)
        assertNull(result.dueAt)
        assertEquals("Llamar luego del almuerzo", result.title)
    }

    @Test fun luegoDeLaReunionNoEsAdverbioSuelto() {
        // "luego de la reunión" es dependencia/evento, no adverbio: NO casa.
        val result = NaturalTaskParser.parse("Avisar luego de la reunión", now, zone)
        assertNull(result.dueAt)
        assertEquals("Avisar luego de la reunión", result.title)
    }

    // --- Límites anuales y sinónimos regionales (P1, ciclo 124) ---
    // "fin de año"/"a fin de año" → 31/12 de este año; "finales de este mes" →
    // fin de mes actual (antes "este" no casaba → +30d genérico y residuo en título);
    // "mitad del mes/semana/año" = sinónimo de "mediados" en América Latina.

    @Test fun aFinDeAnoAncla31Diciembre() {
        val result = NaturalTaskParser.parse("Reunión a fin de año", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeAnoSinPreposicionAncla31Diciembre() {
        val result = NaturalTaskParser.parse("Cerrar libros fin de año", now, zone)
        assertEquals("Cerrar libros", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeEsteMesAnclaFinMesActual() {
        // "este mes" antes no casaba → la tarea quedaba sin fecha (residuo en título).
        // ahora = 29/7 → fin de julio = 31/7.
        val result = NaturalTaskParser.parse("Pagar a finales de este mes", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDelMesEsSinonimoDeMediados() {
        // 2026-08-05 < 15 → mitad del mes = 15/8 (mes actual).
        val tempranoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 5), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión a mitad del mes", tempranoNow, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDeSemanaEsSinonimoDeMediados() {
        // ahora = 2026-07-29 (miércoles) → midOfWeek = nextOrSame(WED) = hoy.
        val result = NaturalTaskParser.parse("Reunión a mitad de semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDelAnoAncla30Junio() {
        // ahora = 29/7/2026, ya pasó 30/6 → mitad del año = 30/6/2027.
        val result = NaturalTaskParser.parse("Balance a mitad del año", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2027, 6, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDelAnoAntesDeMediadosAnclaAnoActual() {
        // 2026-05-01 < 30/6 → mitad del año = 30/6/2026.
        val mayoNow = DateRules.toEpochMillis(LocalDate.of(2026, 5, 1), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Balance a mitad del año", mayoNow, zone)
        assertEquals(LocalDate.of(2026, 6, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── Calificador "de/del/desde + día relativo": no debe dejar residuo de conector ──
    // "llamar de mañana"/"tarea de hoy"/"cita de ayer"/"trabajo desde hoy": la preposición
    // antes de un día relativo es siempre un calificador temporal. Antes el borrado del día
    // como palabra suelta dejaba el conector "de"/"desde" como residuo en el título
    // ("llamar de", "reunión desde") — contenido capturado degradado (P1: integridad de datos).

    @Test fun deMananaNoDejaResiduoDeEnTitulo() {
        val result = NaturalTaskParser.parse("llamar de mañana", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyNoDejaResiduoDeEnTitulo() {
        val result = NaturalTaskParser.parse("tarea de hoy", now, zone)
        assertEquals("tarea", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAyerNoDejaResiduoDeEnTitulo() {
        val result = NaturalTaskParser.parse("cita de ayer", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dePasadoMananaNoDejaResiduoDeEnTitulo() {
        val result = NaturalTaskParser.parse("llamada de pasado mañana", now, zone)
        assertEquals("llamada", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun desdeHoyNoDejaResiduoDesdeEnTitulo() {
        val result = NaturalTaskParser.parse("reunión desde hoy", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dePreposicionDeContenidoNoSeBorra() {
        // "de" sin día relativo tras él es preposición de contenido: debe conservarse.
        val result = NaturalTaskParser.parse("cambio de aceite", now, zone)
        assertEquals("cambio de aceite", result.title)
        assertEquals(null, result.dueAt)
    }

    // ── "N de la noche/mañana/tarde de mañana/hoy": hora + calificador de fecha relativa ──
    // El lookahead de standaloneHourPartOfDayPattern rechazaba cualquier "de <letra>" tras la
    // parte del día, así que "9 de la noche de mañana" no casaba: el número "9" quedaba como
    // residuo en el título ("reunión 9") aunque la hora se resolviera vía contexto PM.
    // Ahora el lookahead admite el calificador de fecha relativa pero sigue rechazando un
    // nombre de mes ("9 de la mañana de marzo" no es forma real, se protege por ambigüedad).

    @Test fun nueveDeLaNocheDeMananaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión 9 de la noche de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nueveDeLaNocheDeHoyResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar 9 de la noche de hoy", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nueveDeLaNocheDePasadoMananaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión 9 de la noche de pasado mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nueveDeLaMananaDeMananaResuelve9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar a las 9 de la mañana de mañana", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nueveDeLaTardeDeMananaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión 9 de la tarde de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }
}

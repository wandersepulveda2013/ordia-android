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

    // c.343: "cada N <weekday>" (cada dos lunes, cada tres jueves…) es la forma
    // hablada de "cada N semanas los lunes": el número cuenta ocurrencias del día.
    // Antes el número se ignoraba y el día casaba desnudo → frecuencia equivocada
    // (MONTHLY día N para días invariables) o interval=1 + residuo "cada N" en el
    // título (para sábados/domingos plurales). Ahora: WEEKLY interval=N, días
    // correctos, título limpio y 1ª ocurrencia en el próximo día de la semana.
    @Test fun parsesEveryNthWeekdayCount_singleInvariable() {
        // 2026-07-29 es miércoles; el próximo lunes es 2026-08-03.
        val result = NaturalTaskParser.parse("gym cada dos lunes", now, zone)
        assertEquals("gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesEveryNthWeekdayCount_writtenNumber() {
        // "cada tres jueves": próximo jueves es 2026-07-30 (al día siguiente del now).
        val result = NaturalTaskParser.parse("reunion cada tres jueves", now, zone)
        assertEquals("reunion", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesEveryNthWeekdayCount_pluralWeekend() {
        // Antes "futbol cada dos domingos" dejaba "cada dos" pegado al título e
        // interval=1 (cada semana, el doble de frecuente). Ahora título limpio e
        // interval=2. Próximo domingo: 2026-08-02.
        val result = NaturalTaskParser.parse("futbol cada dos domingos", now, zone)
        assertEquals("futbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("7", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesEveryNthWeekdayCount_multiDayList() {
        // "cada dos lunes y jueves": interval=2 sobre ambos días. Próximo lunes
        // (2026-08-03) es anterior al próximo jueves (2026-07-30) → 1ª ocurrencia
        // es el jueves 30 (el menor de los nextWeekday).
        val result = NaturalTaskParser.parse("clase cada dos lunes y jueves", now, zone)
        assertEquals("clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1,4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaNWeekday_doesNotStealEveryNSemanas() {
        // Regresión: "cada 2 semanas los lunes" NO debe caer al conteo de weekday
        // (tras el número viene "semanas", no un weekday). Sigue por la ruta de
        // dayListPattern + detectWeekInterval: WEEKLY interval=2 days=[1].
        val result = NaturalTaskParser.parse("meditar cada 2 semanas los lunes", now, zone)
        assertEquals("meditar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun cadaUnicoWeekday_staysWeeklyInterval1() {
        // "cada lunes" (sin número) sigue siendo WEEKLY interval=1, intacto.
        val result = NaturalTaskParser.parse("meditar cada lunes", now, zone)
        assertEquals("meditar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
    }

    // "cada N horas" sub-diario (medicación: cada 8/12/6 horas): recurrencia horaria
    // REAL (HOURLY). Antes la duración "N horas" robaba el número (480 min falsos) y la
    // tarea nacía SIN vencimiento → medicación olvidada (recordatorio jamás disparaba,
    // jamás en What Now). Luego se sacó la 1ª dosis a la superficie (NONE + ahora) PERO
    // era dosis única: la 2ª/3ª dosis se olvidaban. Ahora HOURLY interval=N: la 1ª dosis
    // vence ahora y al completarla el motor genera la siguiente +N horas. El título queda
    // limpio y la duración NO roba "8 horas".
    @Test fun cadaNHorasSubDiarioVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Medicamento cada 8 horas", now, zone)
        assertEquals("Medicamento", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(8, result.recurrenceInterval)
        assertNotNull("La primera dosis debe tener vencimiento (no olvidada)", result.dueAt)
        assertEquals(now, result.dueAt)
        // La duración NO debe robar "8 horas" como 480 min falsos.
        assertNull(result.durationMinutes)
    }

    @Test fun cadaNHorasEscritoVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Antibiótico cada doce horas", now, zone)
        assertEquals("Antibiótico", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(12, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "cada N hs" / "cada N h" / "cada Nhs": abreviaturas cotidianísimas de medicación
    // (jarabes, gotas, pastillas: "cada 12 hs", "cada 8 h", "cada 6h"). El patrón de
    // recurrencia solo admitía "horas?", así que estas formas caían a NONE SIN fecha y,
    // peor, la duración "Nh" robaba el número (p. ej. "12 h" → 720 min falsos) dejando
    // "cada" como residuo en el título → la cadencia de medicación se olvidaba por
    // completo (P1, evitar olvidos). Ahora la unidad admite "hs?"/"h" (simétrico a
    // timePatterns): se reconoce la recurrencia HOURLY, la 1ª dosis vence ahora y el
    // título queda limpio.
    @Test fun cadaNHorasAbreviaturaHsVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Farmacia cada 12 hs", now, zone)
        assertEquals("Farmacia", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(12, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaNHorasAbreviaturaHSeparaVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Pastillas cada 8 h", now, zone)
        assertEquals("Pastillas", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(8, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaNHorasAbreviaturaPegadaVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Jarabe cada 6h", now, zone)
        assertEquals("Jarabe", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "cada 24 horas" = diario, "cada 48 horas" = cada 2 días: múltiplos de 24 se mapean
    // fielmente a DAILY + intervalo, reutilizando el flujo de intervalo existente.
    @Test fun cada24HorasEsDiario() {
        val result = NaturalTaskParser.parse("Vitamina cada 24 horas", now, zone)
        assertEquals("Vitamina", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cada48HorasEsCadaDosDias() {
        val result = NaturalTaskParser.parse("Inyección cada 48 horas", now, zone)
        assertEquals("Inyección", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "cada 8 horas a las 3pm": si hay hora explícita, ésta manda (no se fuerza "ahora").
    // Sigue siendo HOURLY interval=8: 1ª dosis 15:00, siguientes 23:00, 07:00, …
    @Test fun cadaNHorasConHoraExplicitaUsaLaHora() {
        val result = NaturalTaskParser.parse("Medicamento cada 8 horas a las 3pm", now, zone)
        assertEquals("Medicamento", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(8, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    // "cada hora" (cada 1 hora, sin número): cadencia sub-diaria REAL (HOURLY interval=1).
    // "cada media hora" (sub-horaria) sigue siendo NONE + ahora (el motor no repite por
    // minuto). Antes el patrón de c.213 (que exige una cantidad) no casaba con "cada hora"
    // → caía a NONE SIN fecha y, en "cada media hora", la duración robaba "media hora"
    // como 30 min falsos y truncaba el título ("Tomar cada"). Misma clase de olvido que
    // c.213. Ahora "cada hora" repite de verdad; "cada media hora" saca la 1ª dosis a la
    // superficie venciendo AHORA (NONE + immediateDueAt) y se limpia el título.
    @Test fun cadaHoraSubDiarioVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Tomar jarabe cada hora", now, zone)
        assertEquals("Tomar jarabe", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull("La primera dosis debe tener vencimiento (no olvidada)", result.dueAt)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaHoraPluralSubDiarioVenceAhoraYRepite() {
        val result = NaturalTaskParser.parse("Gárgaras cada horas", now, zone)
        assertEquals("Gárgaras", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaMediaHoraNoRobaDuracionNiTitulo() {
        val result = NaturalTaskParser.parse("Tomar gotas cada media hora", now, zone)
        assertEquals("Tomar gotas", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        // "media hora" es cadencia, NO duración: no debe aparecer como 30 min.
        assertNull(result.durationMinutes)
    }

    // "cada N horas y media/cuarto" (medicación con precisión sub-hora: "cada 3 horas y
    // media", "cada 6 horas y media", "cada 2 horas y cuarto"): cadencia que el motor NO
    // puede representar (HOURLY usa intervalo entero de horas; 3,5 h no es representable).
    // ANTES hourlyIntervalPattern casaba "cada 3 horas" y dejaba "y media" colgando como
    // residuo del título, PERO se asignaba HOURLY interval=3 → la medicación RECURRE cada
    // 3 h en vez de 3,5 h (cadencia falsa: la 2ª dosis sale 30 min antes, acumulando
    // error de timing) Y el título nacía mutilado ("medicación y media"). Doble defecto:
    // cadencia silenciosamente errónea + residuo. Simétrico de "cada media hora" (que es
    // NONE + ahora + título limpio, porque el motor no repite por minuto): la cadencia
    // fraccionaria horaria se trata de la MISMA forma honesta — NONE + immediateDueAt=now
    // + título limpio — en vez de inventar una recurrencia de 3 h que el usuario no pidió.
    // Así la 1ª dosis sale a la superficie (aviso real, What Now) sin fingir cadencia.
    @Test fun cadaNHorasYMediaNoFalsaRecurrenciaNiResiduo() {
        val result = NaturalTaskParser.parse("Medicación cada 3 horas y media", now, zone)
        assertEquals("Medicación", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaNHorasYCuartoNoFalsaRecurrenciaNiResiduo() {
        val result = NaturalTaskParser.parse("Jarabe cada 2 horas y cuarto", now, zone)
        assertEquals("Jarabe", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaNHorasYMediaEscritoNoFalsaRecurrenciaNiResiduo() {
        val result = NaturalTaskParser.parse("Pastillas cada seis horas y media", now, zone)
        assertEquals("Pastillas", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "cada N y media horas" (fracción ANTES de "horas"): misma cadencia fraccionaria,
    // pero como el número y la unidad quedan separados por "y media", hourlyIntervalPattern
    // NO casa ("cada 3 y media horas" no es "cada N horas") → antes caía a NONE SIN fecha
    // y la frase entera sobrevivía como residuo del título ("medicación cada 3 y media
    // horas"), es decir, dosis olvidada + título sucio. Misma solución honesta: NONE +
    // immediateDueAt=now + título limpio.
    @Test fun cadaNYMediaHorasNoFalsaRecurrenciaNiResiduo() {
        val result = NaturalTaskParser.parse("Gotas cada 3 y media horas", now, zone)
        assertEquals("Gotas", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // No-regresión: "cada N horas" ENTERO (sin fracción) sigue siendo HOURLY interval=N
    // (la fracción es la que marca la cadencia no representable; sin fracción, el motor
    // SÍ repite por hora entera). La 1ª dosis vence ahora y el título queda limpio.
    @Test fun cadaNHorasEnteroSigueHourlySinFraccion() {
        val result = NaturalTaskParser.parse("Medicación cada 3 horas", now, zone)
        assertEquals("Medicación", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // No-regresión: "y media" tras una HORA de reloj (no cadencia horaria) sigue siendo
    // fracción de reloj ("a las 3 y media" = 03:30), NO se confunde con cadencia. La
    // cadencia fraccionaria sólo aplica tras "cada N horas". "Reunión cada lunes a las 3
    // y media" → recurre los lunes, hora 03:30.
    @Test fun yMediaRelojNoSeConfundeConCadenciaFraccionaria() {
        val result = NaturalTaskParser.parse("Reunión cada lunes a las 3 y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(LocalTime.of(3, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "cada N minutos" (medicación sub-horaria: "cada 30 minutos", "cada 15 minutos",
    // "cada 20 minutos"): cadencia más fina que "cada hora". El motor no repite por
    // minuto, así que —igual que "cada 8 horas"— se saca la primera dosis a la superficie
    // venciendo AHORA (aviso real, What Now) sin fingir recurrencia inexistente. Antes la
    // duración "N minutos" robaba el número (p. ej. 30 min falsos) y la tarea nacía SIN
    // vencimiento → recordatorio jamás disparaba, gárgaras/gotas olvidadas (P1). El título
    // queda limpio y la duración NO debe robar "N minutos".
    @Test fun cadaNMinutosSubDiarioVenceAhora() {
        val result = NaturalTaskParser.parse("Gárgaras cada 30 minutos", now, zone)
        assertEquals("Gárgaras", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertNotNull("La primera dosis debe tener vencimiento (no olvidada)", result.dueAt)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cada15MinutosSubDiarioVenceAhora() {
        val result = NaturalTaskParser.parse("Gotas cada 15 minutos", now, zone)
        assertEquals("Gotas", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    // "cada cuarto de hora" = cada 15 min: forma idiomática sin dígitos, simétrica de
    // "cada media hora". Misma clase de olvido: antes la duración robaba "cuarto de hora"
    // y la tarea nacía sin vencimiento. Se saca la primera dosis venciendo ahora.
    @Test fun cadaCuartoDeHoraSubDiarioVenceAhora() {
        val result = NaturalTaskParser.parse("Enjuague cada cuarto de hora", now, zone)
        assertEquals("Enjuague", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(now, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun cadaHoraConHoraExplicitaUsaLaHora() {
        val result = NaturalTaskParser.parse("Jarabe cada hora a las 3pm", now, zone)
        assertEquals("Jarabe", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    // Recurrencia quincenal con palabra (no dígito): "cada quincena", "quincenalmente".
    // Antes `intervalPattern` (que solo admite dígitos) no casaba, la recurrencia caía
    // a NONE y la tarea nacía SIN fecha (invisible en What Now/planificador, recordatorio
    // jamás disparaba). Una quincena son 15 días (media mes), no 14 (dos semanas): el
    // mapeo histórico a WEEKLY interval=2 programaba los pagos un día antes cada ciclo.
    // Ahora se mapea a DAILY interval=15 (cadencia quincenal exacta, plusDays(15)).
    @Test fun cadaQuincenaParsesQuincenalRecurrence() {
        val result = NaturalTaskParser.parse("Nómina cada quincena", now, zone)
        assertEquals("Nómina", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenalmenteParsesQuincenalRecurrence() {
        val result = NaturalTaskParser.parse("Reporte quincenalmente a las 9", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaQuincenaRespetaFechaExplicita() {
        val result = NaturalTaskParser.parse("Cobro cada quincena el 15/8", now, zone)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
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

    @Test fun adjetivoQuincenalParsesQuincenalRecurrence() {
        val result = NaturalTaskParser.parse("Reporte quincenal", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Adjetivo "diario/diaria" como cadencia DIARIA: la forma adjetiva más común de un
    // hábito cotidiano en español ("repaso diario", "reunión diaria", "medicación diaria"),
    // simétrica a "mensual/semanal/anual". Antes solo se reconocían las frases adverbiales
    // ("cada día", "a diario", "diariamente"): el adjetivo caía a NONE y el hábito nacía
    // SIN recurrencia ni recordatorio periódico (P1: rutina silenciosamente perdida).
    @Test fun adjetivoDiarioParsesDailyRecurrence() {
        val result = NaturalTaskParser.parse("Repaso diario", now, zone)
        assertEquals("Repaso", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun adjetivoDiariaParsesDailyRecurrence() {
        val result = NaturalTaskParser.parse("Reunión diaria", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // El sustantivo "diario" (periódico, cuaderno) NO debe activar recurrencia: con
    // artículo ("leer el diario") o seguido de " de " ("diario de viaje") es un objeto,
    // no una cadencia. Antes de las guardas, una regla ingenua habría creado un hábito
    // falso y mutilado el título.
    @Test fun sustantivoDiarioConArticuloNoActivaRecurrencia() {
        val result = NaturalTaskParser.parse("Leer el diario", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("Leer el diario", result.title)
    }

    @Test fun sustantivoDiarioDeViajeNoActivaRecurrencia() {
        val result = NaturalTaskParser.parse("Diario de viaje", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("Diario de viaje", result.title)
    }

    // "a diario" sigue comportándose como antes: la frase adverbial se consume completa
    // y el adjetivo "diario" no deja residuo. Regresión de la interacción entre bloques.
    @Test fun aDiarioSigueConsumiendoLaFraseCompleta() {
        val result = NaturalTaskParser.parse("llevar al niño al colegio a diario", now, zone)
        assertEquals("llevar al niño al colegio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
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

    // Adjetivo plurimensual faltante: "cuatrimestral" (4 meses, p. ej. "informe
    // cuatrimestral"). Simétrico a bimestral/trimestral/semestral que ya funcionan.
    // Antes caía a NONE → el compromiso de 4 meses nacía sin cadencia ni vencimiento
    // (P1: plazo olvidado, recordatorio jamás disparaba). MONTHLY + interval=4.
    @Test fun adjetivoCuatrimestralParsesMonthlyInterval4() {
        val result = NaturalTaskParser.parse("Informe cuatrimestral", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(4, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // "bisemanal"/"bisemanalmente" = cada dos semanas (quincenal en cadencia semanal):
    // el análogo WEEKLY de "bimestral" (MONTHLY/2). Antes caía a NONE sin fecha ni
    // vencimiento (P1: rutina olvidada) y "bisemanal" quedaba como residuo en el título.
    // Se mapea a WEEKLY interval=2 (plusWeeks(2) = 14 días), idéntico a "cada dos semanas".
    @Test fun adjetivoBisemanalParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reunión bisemanal", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }

    @Test fun adverbioBisemanalmenteParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reporte bisemanalmente", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // "bisemanal los lunes": cadencia quincenal sobre día concreto. Antes el día
    // disparaba la rama de lista semanal con interval=1 (cada semana, el doble de
    // frecuente) y "bisemanal" quedaba en el título. Ahora WEEKLY interval=2 + days.
    @Test fun bisemanalConDiasParsesWeeklyInterval2YDias() {
        val result = NaturalTaskParser.parse("Reunión bisemanal los lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }

    @Test fun bisemanalConVariosDiasParsesWeeklyInterval2YDias() {
        val result = NaturalTaskParser.parse("Reunión bisemanal los lunes y jueves", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1,4", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }


    // --- "todos los/todas las N <unidad>" = forma hablada de "cada N <unidad>" (c.276) ---
    // El determinante plural ("todas las dos semanas", "todos los tres meses",
    // "todos los 3 días") es la cadencia espaciada más natural en español hablado.
    // Antes caía a NONE SIN fecha (rutina olvidada, P1) Y dejaba la frase entera como
    // residuo literal del título (captura sucia). Ahora resuelve la misma
    // frecuencia/intervalo que "cada N <unidad>", con título limpio.

    @Test fun todasLasDosSemanasParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reunión todas las dos semanas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }

    @Test fun todasLas2SemanasDigitoParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reunión todas las 2 semanas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosDosMesesParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Pago todos los dos meses", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosTresMesesParsesMonthlyInterval3() {
        val result = NaturalTaskParser.parse("Control todos los tres meses", now, zone)
        assertEquals("Control", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLos3DiasParsesDailyInterval3() {
        val result = NaturalTaskParser.parse("Revisar todos los 3 días", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosTresDiasEscritoParsesDailyInterval3() {
        val result = NaturalTaskParser.parse("Revisar todos los tres días", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Combo: "todas las dos semanas los lunes" → WEEKLY interval=2 + día. Antes la
    // rama de lista semanal no reconocía el determinante plural → interval=1 (el doble
    // de frecuente) y "todas las dos semanas" quedaba como residuo. Mismo cierre que
    // c.275 aplicó a "bisemanal los lunes".
    @Test fun todasLasDosSemanasConDiasParsesWeeklyInterval2YDias() {
        val result = NaturalTaskParser.parse("Reunión todas las dos semanas los lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1", result.recurrenceDays)
        assertNotNull(result.dueAt)
    }

    // Combo ordinal: "todos los dos meses el primer lunes" → MONTHLY interval=2 anclado
    // al 1er lunes (no al día del mes). Antes el ordinal precedente no se capturaba
    // (recurrenceDays='') y "el primer" quedaba como residuo → cada ciclo derivaba a un
    // weekday distinto. Mismo cierre que c.273 aplicó a "cada N meses el primer lunes".
    @Test fun todosLosDosMesesPrimerLunesParsesMonthlyInterval2Ordinal() {
        val result = NaturalTaskParser.parse("Reunión todos los dos meses el primer lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1:1", result.recurrenceDays)
        assertNotNull(result.dueAt)
        // El anclaje ordinal fuerza el vencimiento al 1er lunes: debe caer en lunes.
        assertEquals(
            java.time.DayOfWeek.MONDAY,
            DateRules.toLocalDate(result.dueAt!!, zone).dayOfWeek
        )
    }

    @Test fun todosLosTresMesesUltimoViernesParsesMonthlyInterval3Ordinal() {
        val result = NaturalTaskParser.parse("Pago todos los tres meses el último viernes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("-1:5", result.recurrenceDays)
        assertNotNull(result.dueAt)
        assertEquals(
            java.time.DayOfWeek.FRIDAY,
            DateRules.toLocalDate(result.dueAt!!, zone).dayOfWeek
        )
    }

    // No-regresión: las formas SIN número (N=1) siguen resolviendo interval=1.
    @Test fun todasLasSemanasSigueSiendoWeeklyInterval1() {
        val result = NaturalTaskParser.parse("Reunión todas las semanas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }
    // P1 evitar-olvidos: "el primer lunes del mes" SIN "cada"/"mensual" explícito.
    // Antes (c.315): recurrence=NONE y dueAt = 1er lunes del mes ACTUAL ya pasado
    // (2026-07-06, con now=2026-07-29) → rutina mensual nacía olvidada (vencida en
    // el pasado, recordatorio jamás disparaba). Simétrico con "el 1 del mes"
    // (monthlyDayPattern, que SÍ promueve a MONTHLY sin "cada"): ahora "el (ordinal)
    // (weekday) del mes" genérico (sin mes nombrado, sin "que viene") se promueve a
    // MONTHLY anclada al ordinal+weekday y la 1ª cita avanza al próximo mes válido
    // (2026-08-03), nunca en pasado. "renta el primer lunes del mes" deja de olvidarse.
    @Test fun primerLunesDelMesSinCadaPromueveMonthlyYAvanzaAFuturo() {
        val result = NaturalTaskParser.parse("renta el primer lunes del mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals("1:1", result.recurrenceDays)
        assertNotNull(result.dueAt)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.MONDAY, due.dayOfWeek)
        // 1er lunes de agosto (07-06 ya pasó con now=2026-07-29).
        assertEquals(LocalDate.of(2026, 8, 3), due)
    }

    @Test fun ultimoViernesDelMesSinCadaPromueveMonthlyYAvanzaAFuturo() {
        val result = NaturalTaskParser.parse("pago el último viernes del mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-1:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        // Último viernes de julio = 2026-07-31; dicho el 29 (aún no llega) → sin roll.
        assertEquals(LocalDate.of(2026, 7, 31), due)
    }

    // No-regresión: mes nombrado NO se promueve a MONTHLY (fecha única en mes concreto).
    @Test fun primerLunesDeAgostoSigueSiendoFechaUnicaNoRecurrente() {
        val result = NaturalTaskParser.parse("cita el primer lunes de agosto", now, zone)
        assertEquals("cita", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        // Agosto dicho en julio → 1er lunes de agosto 2026-08-03 (futuro, sin roll).
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // No-regresión: "del mes que viene" NO se promueve a MONTHLY sin "cada" (fecha
    // única del próximo mes).
    @Test fun primerLunesDelMesQueVieneSinCadaEsFechaUnica() {
        val result = NaturalTaskParser.parse("cita el primer lunes del mes que viene", now, zone)
        assertEquals("cita", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // No-regresión: con "cada"/"todos los meses" explícito, el comportamiento mensual
    // ya existente se conserva (anclaje ordinal + avance al futuro).
    @Test fun primerLunesDeCadaMesSigueMonthlyOrdinal() {
        val result = NaturalTaskParser.parse("renta el primer lunes de cada mes", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el último viernes del mes pasado" / "del mes anterior": ocurrencia ORDINAL del weekday
    // en el mes PREVIO, como fecha pasada honesta (tarea vencida registrada refiriéndose al
    // mes anterior). Antes lastPeriodPattern robaba "del mes pasado" como "el mes pasado"
    // suelto → dueAt = now−30d ignorando ordinal+weekday (fecha equivocada + título corrupto).
    @Test fun ultimoViernesDelMesPasadoEsFechaOrdinalEnMesPrevio() {
        val result = NaturalTaskParser.parse("pago el último viernes del mes pasado", now, zone)
        assertEquals("pago", result.title)
        // No se promueve a MONTHLY: es una fecha pasada única (no "cada mes").
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        // Último viernes de junio 2026 = 2026-06-26.
        assertEquals(LocalDate.of(2026, 6, 26), due)
    }

    // Variante con tilde y "mes anterior" (sinónimo).
    @Test fun ultimoViernesDelMesAnteriorConTildeEsFechaOrdinalEnMesPrevio() {
        val result = NaturalTaskParser.parse("pago el último viernes del mes anterior", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 26), due)
    }

    // Ordinal no-último + mes pasado: "primer lunes del mes pasado" = 1er lunes de junio.
    @Test fun primerLunesDelMesPasadoEsPrimerLunesDeJunio() {
        val result = NaturalTaskParser.parse("reunión el primer lunes del mes pasado", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.MONDAY, due.dayOfWeek)
        // 1er lunes de junio 2026 = 2026-06-01.
        assertEquals(LocalDate.of(2026, 6, 1), due)
    }

    @Test fun todosLosDiasSigueSiendoDailyInterval1() {
        val result = NaturalTaskParser.parse("Revisar todos los días", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }

    // Contradicción: anclaje mensual explícito gana sobre el adjetivo bisemanal, pero
    // "bisemanal" se limpia del título (recurrenceAdjectiveLeakPattern), consistente con
    // "pago mensual el 15 de cada mes" → "pago".
    @Test fun bisemanalConAnclajeMensualLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pago bisemanal el 15 de cada mes", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("Pago", result.title)
    }

    // Sustantivos plurimensuales como CADENCIA recurrente ("cada bimestre/trimestre/
    // cuatrimestre/semestre"): hitos financieros de plazo largo (renta, impuestos,
    // declaraciones). `intervalPattern` solo admite "días|semanas|meses|años", así que
    // estas frases caían a NONE → la tarea recurrente nacía sin fecha ni cadencia
    // (P1: compromiso periódico olvidado, invisible en What Now/planificador). Se
    // mapean a MONTHLY + intervalo (2/3/4/6), igual que el adjetivo equivalente, sin
    // añadir enum ni migración: RecurrenceEngine ya avanza plusMonths(interval).
    @Test fun cadaBimestreParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Renta cada bimestre", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaTrimestreParsesMonthlyInterval3() {
        val result = NaturalTaskParser.parse("Impuestos cada trimestre", now, zone)
        assertEquals("Impuestos", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaCuatrimestreParsesMonthlyInterval4() {
        val result = NaturalTaskParser.parse("Declaración cada cuatrimestre", now, zone)
        assertEquals("Declaración", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(4, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaSemestreParsesMonthlyInterval6() {
        val result = NaturalTaskParser.parse("Renovación cada semestre", now, zone)
        assertEquals("Renovación", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Par léxico plural de "cada bimestre/trimestre/cuatrimestre/semestre":
    // "todos los bimestres/trimestres/cuatrimestres/semestres" — simétrico de "todos los
    // meses"/"todos los años" (fixedPatterns). Antes caía a NONE + título con la frase
    // pegada (rutina periódica olvidada: sin cadencia, sin recordatorio, invisible en
    // What Now/planificador). P1 consistencia léxica / datos (sagrados) / evitar olvidos.
    @Test fun todosLosBimestresParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Renta todos los bimestres", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosTrimestresParsesMonthlyInterval3() {
        val result = NaturalTaskParser.parse("Impuestos todos los trimestres", now, zone)
        assertEquals("Impuestos", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosCuatrimestresParsesMonthlyInterval4() {
        val result = NaturalTaskParser.parse("Declaración todos los cuatrimestres", now, zone)
        assertEquals("Declaración", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(4, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun todosLosSemestresParsesMonthlyInterval6() {
        val result = NaturalTaskParser.parse("Renovación todos los semestres", now, zone)
        assertEquals("Renovación", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // La rama "todos los" exige PLURAL ("bimestres"): el singular "todos los bimestre"
    // es gramaticalmente anómalo y no casa — paralelo a "todos los meses" (plural).
    @Test fun todosLosBimestreSingularNoCasa() {
        val result = NaturalTaskParser.parse("Renta todos los bimestre", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
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

    // --- Lista de días con artículo repetido ("el/los X y el/los Y") ---
    // En español es tan cotidiano repetir el artículo ante cada día ("los lunes y
    // los miércoles", "el martes y el jueves") como omitirlo ("los lunes y
    // miércoles"). Antes la continuación de dayListPattern NO toleraba un artículo
    // antes de cada día siguiente, así "los lunes y los miércoles" casaba SÓLO
    // "lunes", perdía el miércoles (rutina mutilada: se repetía un solo día en
    // silencio) y dejaba "y los" como residuo en el título. Asimetría flagrante con
    // la forma sin repetir ("los lunes y miércoles" ya funcionaba). c.258 cierra la
    // rendija: la continuación admite un artículo opcional (el/los) antes de cada
    // día, y un artículo inicial opcional se consume sin marcar hasPrefix (así
    // "el martes" suelto sigue siendo fecha, no recurrencia).
    @Test fun pluralRepeatedArticleDayListCapturesAllDays() {
        val result = NaturalTaskParser.parse("Gym los lunes y los miércoles", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3", result.recurrenceDays)
    }

    @Test fun pluralRepeatedArticleDayListWithIntervalCapturesAllDays() {
        val result = NaturalTaskParser.parse("Clase cada 3 semanas los martes y los jueves", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("2,4", result.recurrenceDays)
    }

    @Test fun singularRepeatedArticleDayListCapturesAllDays() {
        val result = NaturalTaskParser.parse("Fisio el lunes y el miércoles", now, zone)
        assertEquals("Fisio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3", result.recurrenceDays)
    }

    @Test fun singularRepeatedArticleDayListWithIntervalCapturesAllDays() {
        val result = NaturalTaskParser.parse("Cita cada 2 semanas el martes y el jueves", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("2,4", result.recurrenceDays)
    }

    @Test fun threeDayListWithRepeatedArticlesCapturesAllDays() {
        val result = NaturalTaskParser.parse("Clases los lunes, los miércoles y los viernes", now, zone)
        assertEquals("Clases", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    // No-regresión: un día suelto en singular sin cadencia sigue siendo FECHA
    // (no recurrencia). "el martes" con artículo inicial opcional consumido por
    // dayListPattern pero SIN hasPrefix y con 1 solo día → cae al patrón de fecha.
    @Test fun singularWeekdayWithArticleNoCadenceStaysDateNotRecurrence() {
        val result = NaturalTaskParser.parse("Reunión el martes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
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

    // Adjetivo de recurrencia + frase anclada coexistentes: "pago mensual el 15 de
    // cada mes". El adjetivo "mensual" expresa la cadencia (ya dicha por "el 15 de
    // cada mes") y NO es contenido; antes filtraba al título ("pago mensual") porque
    // parseRecurrence retornaba desde la rama anclada (monthlyDayPattern) sin llegar
    // a fixedPatterns, que es donde se consume "mensual". Inconsistencia con
    // "pago mensual el 15" → "pago" (limpio). El adjetivo redundante debe limpiarse.
    @Test fun monthlyAdjectiveDoesNotLeakWhenAnchorAlsoPresent() {
        val result = NaturalTaskParser.parse("Pago mensual el 15 de cada mes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun monthlyAdjectiveDoesNotLeakWhenDayOneAnchorPresent() {
        val result = NaturalTaskParser.parse("Renta mensual el 1 de cada mes", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "cada N del mes" (sin "de" entre "cada" y el día) es la forma cotidiana del
    // vencimiento mensual ("renta cada 1 del mes"). Antes el prefijo "cada" NO se
    // consumía y quedaba pegado al título ("renta cada"), ensuciando el texto de una
    // rutina financiera. c.306: el rango capturado incluye el prefijo "cada".
    @Test fun cadaPrefixDoesNotLeakInMonthlyDayAnchor() {
        val result = NaturalTaskParser.parse("Renta cada 1 del mes", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaPrefixDoesNotLeakInMonthlyMidMonthAnchor() {
        val result = NaturalTaskParser.parse("Pago cada 15 del mes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "cada N" a secas (SIN unidad días/semanas/meses/años) es el vencimiento mensual
    // cotidiano ("reporte cada 15", "nomina cada 1"): el día del mes implícito es la
    // quincena, la nómina, el corte. La AUSENCIA de unidad es la señal. Antes caía a
    // NONE sin fecha: la rutina mensual nacía olvidada (recordatorio jamás disparaba).
    // c.306: se reconoce como MONTHLY anclado al día N.
    @Test fun bareCadaNumberParsesAsMonthlyDayOfMonth() {
        val result = NaturalTaskParser.parse("Reporte cada 15", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bareCadaDayOneParsesAsMonthlyDayOfMonth() {
        val result = NaturalTaskParser.parse("Nomina cada 1", now, zone)
        assertEquals("Nomina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "cada quince" (número escrito, sin unidad) = mensual anclado al día 15, simétrico
    // de "cada 15". Admite dígitos o número escrito como el resto del parser.
    @Test fun bareCadaWrittenNumberParsesAsMonthlyDayOfMonth() {
        val result = NaturalTaskParser.parse("Reporte cada quince", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Regresión: "cada N días/semanas/meses/años/horas/minutos" NO debe caer en la rama
    // de "cada N" mensual. El lookahead negativo los rechaza y los resuelve intervalPattern
    // (o la rama horaria), como antes de c.306.
    @Test fun cadaNumberWithUnitDoesNotBecomeMonthly() {
        val dias = NaturalTaskParser.parse("Revisar cada 15 días", now, zone)
        assertEquals(RecurrenceFrequency.DAILY, dias.recurrence)
        assertEquals("Revisar", dias.title)

        val horas = NaturalTaskParser.parse("Medicación cada 12 horas", now, zone)
        assertEquals(RecurrenceFrequency.HOURLY, horas.recurrence)
        assertEquals("Medicación", horas.title)

        val semanas = NaturalTaskParser.parse("Reunión cada 2 semanas", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, semanas.recurrence)
        assertEquals("Reunión", semanas.title)

        val meses = NaturalTaskParser.parse("Pago cada 3 meses", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, meses.recurrence)
        assertEquals("Pago", meses.title)
    }

    // Mismo leak con recurrencia semanal anclada a días: "pago semanal los lunes"
    // dejaba "pago semanal" (el adjetivo "semanal" sobrevivía porque dayListPattern
    // retornaba antes que fixedPatterns). La cadencia ya la da "los lunes".
    @Test fun weeklyAdjectiveDoesNotLeakWhenDayListAlsoPresent() {
        val result = NaturalTaskParser.parse("Pago semanal los lunes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun weeklyAdjectiveDoesNotLeakWithMultipleDays() {
        val result = NaturalTaskParser.parse("Reunion semanal los lunes y miercoles", now, zone)
        assertEquals("Reunion", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3", result.recurrenceDays)
    }

    // Paridad léxica "cada <unidad>" (intervalo-1) cuando el anclaje ya porta la cadencia:
    // "reunión cada semana los lunes" dejaba "reunión cada semana" (la cadencia ya la da
    // "los lunes"), inconsistente con "reunión semanal los lunes" → "reunión". La forma
    // sin número de "cada semana/día/mes/año" caía fuera de detectWeekInterval/intervalPattern
    // (que exigen cantidad) y filtraba al título. Ahora recurrenceAdjectiveLeakPattern la
    // cubre, igual que "semanal/mensual/...". No-regresión: la forma CON número
    // ("cada dos semanas") también se limpia (la añade intervalPattern a phraseRanges).
    @Test fun cadaSemanaIntervaloUnoNoFiltraCuandoDayListPresente() {
        val result = NaturalTaskParser.parse("Reunion cada semana los lunes", now, zone)
        assertEquals("Reunion", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun cadaMesIntervaloUnoNoFiltraCuandoAnchorDiaDelMesPresente() {
        val result = NaturalTaskParser.parse("Reunion cada mes el 15", now, zone)
        assertEquals("Reunion", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun cadaDosSemanasConNumeroNoRegresionaTituloSucio() {
        // Guard anti-regresión: la forma con número "cada dos semanas los lunes" debe seguir
        // limpiando el título (la añade intervalPattern, no el nuevo leak pattern).
        val result = NaturalTaskParser.parse("Reunion cada dos semanas los lunes", now, zone)
        assertEquals("Reunion", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
    }

    // No-regresión: el adjetivo solo (sin anclaje) sigue limpiándose como antes
    // (fixedPatterns lo consume vía phraseRanges; el nuevo paso es no-op allí).
    @Test fun monthlyAdjectiveAloneStillCleansTitle() {
        val result = NaturalTaskParser.parse("Pago mensual", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    // Adverbio "-mente" + anclaje: "cobro mensualmente el 5 de cada mes" también
    // filtraba "mensualmente". El adverbio es igualmente redundante con el anclaje.
    @Test fun monthlyAdverbDoesNotLeakWhenAnchorAlsoPresent() {
        val result = NaturalTaskParser.parse("Cobro mensualmente el 5 de cada mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- Verbo de recordatorio como ÚNICO contenido (ciclo 230) ---
    // Cuando el usuario escribe SÓLO el recordatorio + frases de agenda ("recordatorio
    // cada 2 días a las 8", "recuérdame cada lunes a las 8"), no hay un sustantivo de
    // contenido que sobreviva. Antes el verbo/nombre se eliminaba último, dejando el
    // título en blanco, y el respaldo `ifBlank { original }` RESUCITABA la frase cruda
    // completa: el título quedaba como "recordatorio cada 2 días a las 8" (con la
    // cadencia, la hora y el verbo como basura visible). Es un bug de captura: la
    // agenda (dueAt/recurrencia/offset) SÍ se parseaba bien, pero el título mentía.
    // Ahora, si limpiar el verbo dejaría el título vacío, se CONSERVA el verbo/nombre
    // como título honesto (lo que el usuario escribió, sin las frases ya consumidas).
    @Test fun recordatorioCadaNdiasConHora_tituloLimpioConservaRecordatorio() {
        val result = NaturalTaskParser.parse("recordatorio cada 2 días a las 8", now, zone)
        assertEquals("recordatorio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun recordatorioCadaLunesConHora_tituloLimpioConservaRecordatorio() {
        val result = NaturalTaskParser.parse("recordatorio cada lunes a las 8", now, zone)
        assertEquals("recordatorio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun recordatorioCadaLunesSinHora_tituloLimpioConservaRecordatorio() {
        val result = NaturalTaskParser.parse("recordatorio cada lunes", now, zone)
        assertEquals("recordatorio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
    }

    @Test fun recuerdameCadaNdiasConHora_tituloNoResucitaFrasesDeAgenda() {
        val result = NaturalTaskParser.parse("recuérdame cada 2 días a las 8", now, zone)
        // No hay sustantivo de contenido: se conserva el verbo como título honesto,
        // sin resucitar "cada 2 días a las 8".
        assertEquals("recuérdame", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun recordatorioMananaConHora_tituloLimpioConservaRecordatorio() {
        val result = NaturalTaskParser.parse("recordatorio mañana a las 8", now, zone)
        assertEquals("recordatorio", result.title)
        assertNotNull(result.dueAt)
        // "recordatorio" es el sustantivo (el aviso que se programa), no una cita con
        // nudge previo: la hora dada ES la hora del aviso → offset 0 (se dispara EN la
        // hora), no 30 min antes. Antes se aplicaba 30 min y el aviso se adelantaba.
        assertEquals(0, result.reminderOffsetMinutes)
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

    // --- Verbo de recordatorio como ÚNICO contenido + hora: la hora es del AVISO (c.318) ---
    // Cuando el usuario escribe SÓLO el verbo de recordatorio + una hora ("recuérdame en
    // 30 min", "recuérdame mañana", "recuérdame el viernes", "avísame a las 5"), la hora
    // que dio ES la hora en la que quiere ser avisado, no la hora de una cita con un nudge
    // 30 min antes. Antes se aplicaba el offset 30 por defecto y el aviso se disparaba
    // hasta un día antes ("recuérdame el viernes" → aviso el jueves). Ahora offset=0
    // (el recordatorio se dispara EN dueAt).
    @Test fun recuerdameSoloConHoraRelativa_offsetCero() {
        val result = NaturalTaskParser.parse("recuérdame en 30 min", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(0, result.reminderOffsetMinutes)
    }

    @Test fun recuerdameSoloConDia_offsetCero() {
        val result = NaturalTaskParser.parse("recuérdame el viernes", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(0, result.reminderOffsetMinutes)
    }

    @Test fun avisameSoloConHora_offsetCero() {
        val result = NaturalTaskParser.parse("avísame el viernes 5pm", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(0, result.reminderOffsetMinutes)
    }

    @Test fun recuerdameSoloConHoraExplicita_offsetCero() {
        val result = NaturalTaskParser.parse("recuérdame a las 5", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(0, result.reminderOffsetMinutes)
    }

    // Contraste: cuando hay un sustantivo de acción ("recuerda llamar mañana"), el verbo
    // NO es el único contenido → sigue aplicándose el offset 30 (la cita es "llamar",
    // "mañana" es la fecha de la cita, el nudge va 30 min antes).
    @Test fun recuerdameConAccionMantieneOffset30() {
        val result = NaturalTaskParser.parse("recuerda llamar mañana", now, zone)
        assertEquals("llamar", result.title)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
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

    // ── Hora de reloj con sufijo de unidad "h"/"hs" (ciclo 235) ──
    // "15:30h"/"15:30hs"/"7:15h" son horas de reloj cotidianas (sufijo "h"/"hs" = "horas").
    // El ":" `:MM` es señal inequívoca de reloj. Antes el sufijo "h" rompía el \b del
    // patrón de hora `HH:MM` (no casaba) y el patrón de duración "Nh" robaba los MINUTOS
    // como duración falsa ("15:30h" → dueAt=null, dur=1440, título "15:") → la cita se
    // OLVIDABA (sin vencimiento) y el título quedaba corrupto. Peor, "7:15h" perdía los
    // minutos en silencio (due=07:00 en vez de 07:15). Ahora la hora se consume completa.
    @Test fun horaColonyHSuffixSeResuelveComoReloj() {
        val result = NaturalTaskParser.parse("Reunión 15:30h", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun horaColonyHSuffixPlural() {
        val result = NaturalTaskParser.parse("Reunión 15:30hs", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun horaColonyHSuffixConservaMinutos() {
        val result = NaturalTaskParser.parse("Tren 7:15h", now, zone)
        assertEquals("Tren", result.title)
        assertEquals(LocalTime.of(7, 15), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun horaColonyHorasSuffixConservaMinutos() {
        val result = NaturalTaskParser.parse("Cita 7:15 horas", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(7, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun horaColonyHSuffixConMeridiem() {
        val result = NaturalTaskParser.parse("Cita 3:30h pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun horaColonyHSuffixAm() {
        val result = NaturalTaskParser.parse("Cita 9:30h am", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // ── "a las Nh" sufijo compacto (c.239) ──
    // "a las 15h"/"a las 9h" es la forma compacta de hora más común en español. Antes el
    // sufijo "h" NO estaba en el grupo de unidad del patrón "a las N", así que `\b` fallaba
    // (entre "5" y "h" no hay límite de palabra), la cita quedaba SIN dueAt (OLVIDADA) y
    // "a las 15h" se conservaba íntegro como residuo en el título. Mismo origen que el
    // reloj "HH:MMh" (c.235) pero en el patrón "a las": era la asimetría que causaba la
    // fecha perdida. Ahora "h" se consume y la hora se resuelve.
    @Test fun aLasNhSuffixNoPierdeFecha() {
        val result = NaturalTaskParser.parse("Reunión a las 15h", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun aLasNhSuffixManana() {
        val result = NaturalTaskParser.parse("Reunión a las 9h", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhSuffixConParteDelDia() {
        val result = NaturalTaskParser.parse("Reunión a las 9h de la noche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhSuffixEspaciado() {
        val result = NaturalTaskParser.parse("Reunión a las 9 h", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "a las 9 hola": la "h" del sufijo NO debe robar la "h" de "hola". El `\b` tras "h"
    // solo casa con espacio/fín/no-palabra, así "hola" se conserva íntegro en el título.
    @Test fun aLasNhSuffixNoRobaHDePalabra() {
        val result = NaturalTaskParser.parse("Reunión a las 9 hola", now, zone)
        assertEquals("Reunión hola", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Las formas ya soportadas "a las 9 horas"/"a las 9 hs" siguen funcionando sin cambio.
    @Test fun aLasNHorasYHsSiguenFuncionando() {
        val horas = NaturalTaskParser.parse("Reunión a las 9 horas", now, zone)
        assertEquals("Reunión", horas.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(horas.dueAt!!, zone))
        val hs = NaturalTaskParser.parse("Reunión a las 9 hs", now, zone)
        assertEquals("Reunión", hs.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(hs.dueAt!!, zone))
    }

    // ── Anti falso positivo: "a las N <sustantivo plural de cantidad>" es CUENTA, no cita ──
    // "hablar a las 10 personas del equipo" = hablar con las 10 personas, NO una cita a las
    // 10:00. Antes se creaba una cita falsa a las 10:00 (pérdida de integridad: cita
    // inventada). Ahora dueAt es null y no se falsifica la agenda. El título conserva la
    // acción y el objeto (el recorte de "a las 10" preexistía; aquí se valida la cita falsa).
    @Test fun aLasNPersonasNoEsCita() {
        val r = NaturalTaskParser.parse("Hablar a las 10 personas del equipo", now, zone)
        assertNull("una cuenta de personas no debe agendar una cita falsa", r.dueAt)
        assertTrue("el objeto debe conservarse: ${r.title}", r.title.contains("personas"))
        assertTrue("la acción debe conservarse: ${r.title}", r.title.contains("Hablar"))
    }

    @Test fun aLasNCajasNoEsCita() {
        val r = NaturalTaskParser.parse("Comprar a las 3 cajas de leche", now, zone)
        assertNull("una cuenta de cajas no debe agendar una cita falsa", r.dueAt)
        assertTrue("el objeto debe conservarse: ${r.title}", r.title.contains("cajas"))
    }

    @Test fun aLaUnaPersonasNoEsCita() {
        val r = NaturalTaskParser.parse("Reunión a la una personas", now, zone)
        assertNull("'a la una personas' es cuenta, no cita a la 1:00", r.dueAt)
    }

    // Las horas CON evidencia de reloj (meridiem, :MM, fracción, sufijo horas/hs/h) siguen
    // siendo cita aunque siga un sustantivo plural: la evidencia desambigua.
    @Test fun aLasNConMeridiemSiempreEsCitaAunqueSigaPlural() {
        val r = NaturalTaskParser.parse("Reunión a las 10 pm personas invitadas", now, zone)
        assertNotNull("con 'pm' inequívoco es cita aunque siga un plural", r.dueAt)
        assertEquals(LocalTime.of(22, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aLasNConMMSiempreEsCitaAunqueSigaPlural() {
        val r = NaturalTaskParser.parse("Reunión a las 10:30 personas", now, zone)
        assertNotNull("con ':30' inequívoco es cita aunque siga un plural", r.dueAt)
        assertEquals(LocalTime.of(10, 30), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // Singular tras "a las N" NO es cuenta (no hay concordancia plural "las N <plural>"):
    // "reunión a las 9 hola" sigue siendo cita a las 9:00 con "hola" en el título.
    @Test fun aLasNSingularNoSeFiltra() {
        val r = NaturalTaskParser.parse("Reunión a las 9 hola", now, zone)
        assertNotNull("singular tras la hora sigue siendo cita", r.dueAt)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ── Formato compacto "NhMM" (hora:minutos con "h" como separador, ciclo 253) ──
    // "a las 9h30"/"9h30 am"/"9h30 de la noche" son formas compactas cotidianas en español
    // donde la "h" separa hora y minutos (equivalente del ":"). Antes el patrón "a las N"
    // sólo aceptaba ":MM" como minutos, así "9h30" no casaba (la "h" se consumía como sufijo
    // de unidad y "30" caía como residuo) → la cita quedaba SIN dueAt (OLVIDADA) y con el
    // título corrupto. Ahora "h" es también separador de minutos —pero SOLO junto a una
    // señal inequívoca de reloj ("a las"/"a la", meridiem am/pm o "de la tarde/noche")—:
    // el "Nh" puro sin señal de reloj sigue siendo duración (decisión c.235), así no se
    // falsifica "estudiar 2h30" como una cita a las 2:30.
    @Test fun aLasNhMMSeparaHoraYMinutos() {
        val result = NaturalTaskParser.parse("Reunión a las 9h30", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun aLasNhMM24h() {
        val result = NaturalTaskParser.parse("Reunión a las 15h30", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun NhMMConMeridiemPm() {
        val result = NaturalTaskParser.parse("Cita 9h30 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(null, result.durationMinutes)
    }

    @Test fun NhMMConMeridiemAm() {
        val result = NaturalTaskParser.parse("Cita 9h30 am", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun NhMMConParteDelDia() {
        val result = NaturalTaskParser.parse("Cita 9h30 de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El "Nh" puro (sin minutos tras la "h") sigue siendo hora en punto, no se rompe.
    @Test fun aLasNhSinMinutosSigueSiendoEnPunto() {
        val result = NaturalTaskParser.parse("Reunión a las 9h", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "Nh" puro sin señal de reloj sigue siendo DURACIÓN (no se falsifica como hora).
    // "estudiar 2h" → 120 min, sin dueAt. El cambio de c.253 no debe alterar esto.
    @Test fun NhPuroSinSenalDeRelojSigueSiendoDuracion() {
        val result = NaturalTaskParser.parse("Estudiar 2h", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(null, result.dueAt)
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

    // --- Duración fraccionaria compuesta (ciclo actual) ---
    // "2 horas y media"/"1 hora y media"/"3 horas y cuarto": antes el patrón "N horas"
    // robaba solo la parte entera (2 horas → 120) y dejaba "y media" como residuo en el
    // título, subestimando la duración real (150) que usa el planificador y "What Now".
    // Simétrico del "en una hora y media" (fecha relativa) que SÍ se resolvía entero.
    @Test fun dosHorasYMediaEsDuracionDe150Min() {
        val result = NaturalTaskParser.parse("Estudiar 2 horas y media", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(150, result.durationMinutes)
    }

    @Test fun unaHoraYMediaEsDuracionDe90Min() {
        val result = NaturalTaskParser.parse("Reunión de 1 hora y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun tresHorasYCuartoEsDuracionDe195Min() {
        val result = NaturalTaskParser.parse("Trabajo 3 horas y cuarto", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(195, result.durationMinutes)
    }

    @Test fun dosHorasYMediaEscritasEsDuracionDe150Min() {
        val result = NaturalTaskParser.parse("Estudiar dos horas y media", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(150, result.durationMinutes)
    }

    @Test fun duracionFraccionCompuestaConFechaNoInterfiere() {
        val result = NaturalTaskParser.parse("Estudiar 2 horas y media para el viernes", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(150, result.durationMinutes)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // La fecha relativa "en 2 horas y media" ya se resuelve como vencimiento (no duración):
    // el patrón compuesto de duración NO debe robarla cuando va con prefijo "en".
    @Test fun relativaCompuestaNoSeLeeComoDuracion() {
        val result = NaturalTaskParser.parse("Revisar el horno en 2 horas y media", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 150 * 60_000L, result.dueAt)
        assertNull("La fecha relativa no debe leerse como duración", result.durationMinutes)
    }

    // --- Fecha relativa con cantidad DECIMAL (ciclo 228) ---
    // "en 1.5 horas"/"en 2,5 horas" (coma decimal, habitual en español) NO casaban
    // [relativePattern] (solo aceptaba enteros) → caían a la duración → dueAt=null
    // (tarea olvidada, sin recordatorio posible) y título corrupto ("... en 1"). Ahora
    // el decimal se resuelve como vencimiento redondeando al minuto y el título queda limpio.
    @Test fun relativaDecimalHorasPunto() {
        val result = NaturalTaskParser.parse("Llamar en 1.5 horas", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 90 * 60_000L, result.dueAt)
        assertNull("La fecha relativa decimal no debe leerse como duración", result.durationMinutes)
    }

    @Test fun relativaDecimalHorasComa() {
        val result = NaturalTaskParser.parse("Llamar en 2,5 horas", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 150 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun relativaDecimalMediaHora() {
        val result = NaturalTaskParser.parse("Llamar en 0.5 horas", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
    }

    @Test fun relativaDecimalMinutosRedondeaAlMinuto() {
        val result = NaturalTaskParser.parse("Llamar en 1.5 minutos", now, zone)
        assertEquals("Llamar", result.title)
        // 1.5 min → 90 s → redondea a 90000 ms (al minuto).
        assertEquals(now + 90_000L, result.dueAt)
    }

    @Test fun relativaDecimalDias() {
        val result = NaturalTaskParser.parse("Llamar en 2.5 días", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + (2.5 * 24 * 60 * 60_000L).toLong(), result.dueAt)
    }

    @Test fun relativaDecimalNoEsDuracion() {
        // Sin prefijo "en", "1.5 horas" sigue siendo DURACIÓN (90 min), no vencimiento.
        val result = NaturalTaskParser.parse("Estudiar 1.5 horas", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(90, result.durationMinutes)
        assertNull(result.dueAt)
    }

    // --- Cuartos en plural como fracción de duración (ciclo 175) ---
    // "2 horas y tres cuartos" = 120+45=165, "una hora y dos cuartos" = 60+30=90. Antes
    // [compoundFractionalDurationPattern] solo aceptaba "media|cuarto" (no "tres
    // cuartos"/"dos cuartos"), así que el patrón "N horas" robaba la parte entera (→ 120)
    // y dejaba "y tres cuartos" como residuo en el título, con duración subestimada.
    // Simétrico de [compoundFractionalRelativePattern] ("en una hora y tres cuartos").
    @Test fun dosHorasYTresCuartosEsDuracion165() {
        val result = NaturalTaskParser.parse("Estudiar 2 horas y tres cuartos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(165, result.durationMinutes)
    }

    @Test fun unaHoraYDosCuartosEsDuracion90() {
        val result = NaturalTaskParser.parse("Estudiar una hora y dos cuartos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun dosHorasYDosCuartosEsDuracion150() {
        val result = NaturalTaskParser.parse("Estudiar dos horas y dos cuartos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(150, result.durationMinutes)
    }

    // --- Multi-cuarto como duración (sin número de horas) (ciclo 175) ---
    // "tres cuartos de hora" = 45, "dos cuartos de hora" = 30. Antes no casaban ningún
    // patrón de duración (la cantidad escrita no es "horas"/"minutos" y
    // [fractionalDurationPattern] solo admite "media hora"/"(un) cuarto de hora" en
    // singular), así que durationMinutes era null y la frase entera quedaba como residuo
    // en el título. Simétrico de [multiQuarterRelativePattern] ("en tres cuartos de hora").
    @Test fun tresCuartosDeHoraEsDuracion45() {
        val result = NaturalTaskParser.parse("Reunión de tres cuartos de hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(45, result.durationMinutes)
    }

    @Test fun dosCuartosDeHoraEsDuracion30() {
        val result = NaturalTaskParser.parse("Reunión dos cuartos de hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun tresCuartosDeHoraYCuartoEsDuracion60() {
        val result = NaturalTaskParser.parse("Reunión tres cuartos de hora y cuarto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(60, result.durationMinutes)
    }

    @Test fun multiCuartoDuracionConFechaNoInterfiere() {
        val result = NaturalTaskParser.parse("Pagar tres cuartos de hora y luego salir", now, zone)
        assertEquals("Pagar y salir", result.title)
        assertEquals(45, result.durationMinutes)
    }

    // "tres cuartos" SIN "de hora" NO es duración: "cuartos" = habitaciones
    // ("los tres cuartos de la casa"). El patrón exige "de hora" para desambiguar
    // (mismo criterio que [fractionalDurationPattern], que también exige "hora"), así
    // que NO se roba duración falsa ni se corrompe el título.
    @Test fun tresCuartosSinDeHoraNoEsDuracion() {
        val result = NaturalTaskParser.parse("Los tres cuartos de la casa", now, zone)
        assertEquals("Los tres cuartos de la casa", result.title)
        assertNull(result.durationMinutes)
    }

    // --- Cantidad decimal como duración ("1.5 horas"/"2,5 horas") (ciclo 179) ---
    // Antes el patrón "N horas" usaba (\d{1,3}) para la cantidad: en "1.5 horas"
    // casaba SOLO "5" (el dígito tras el punto) → 5 horas = 300 min, y "1." quedaba
    // como residuo en el título ("Estudiar 1"). Mismo fallo con coma decimal ("2,5
    // horas" → 300) y con la forma compacta ("1.5h" → 300). Duración absurda +
    // título sucio. Ahora la cantidad admite parte decimal y se computa como
    // cantidad×60 (horas) redondeada al minuto. Simétrico de que la fecha relativa
    // "en 1.5 horas" ya se resolvía; la duración libre no.
    @Test fun unPuntoCincoHorasEsDuracion90() {
        val result = NaturalTaskParser.parse("Estudiar 1.5 horas", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun dosComaCincoHorasEsDuracion150() {
        val result = NaturalTaskParser.parse("Estudiar 2,5 horas", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(150, result.durationMinutes)
    }

    @Test fun unComaCincoHorasConDeEsDuracion90() {
        val result = NaturalTaskParser.parse("Reunión de 1,5 horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun ceroPuntoCincoHorasEsDuracion30() {
        val result = NaturalTaskParser.parse("Trabajo 0.5 horas", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun decimalHorasSingularHora() {
        val result = NaturalTaskParser.parse("Estudiar 1.5 hora", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun decimalHorasCompacto1punto5h() {
        val result = NaturalTaskParser.parse("Trabajar 1.5h", now, zone)
        assertEquals("Trabajar", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun decimalHorasLimpiaResiduoYConservaResto() {
        val result = NaturalTaskParser.parse("Estudiar 1.5 horas para el examen", now, zone)
        assertEquals("Estudiar examen", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun decimalMinutosSeRedondeaAlMinuto() {
        val result = NaturalTaskParser.parse("Pausa 10.25 minutos", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(10, result.durationMinutes)
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

    // --- Keyword "duración" (ciclo 141) ---
    // "duración 30 minutos": la palabra "duración" no se reconocía como señal de
    // duración, así que aunque el número+unidad casaba y durationMinutes=30, la
    // palabra "duración" quedaba como residuo en el título. Además, "duración 45"
    // (sin unidad) no casaba con nada → durationMinutes=null y todo el título
    // se conservaba. "duración" es la forma más natural de declarar la duración
    // de una reunión/cita en español.
    @Test fun duracionKeywordConUnidadSeCapturaYLimpia() {
        val result = NaturalTaskParser.parse("Reunión duración 30 minutos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun duracionKeywordSinUnidadDefaultMinutos() {
        val result = NaturalTaskParser.parse("Reunión duración 45", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(45, result.durationMinutes)
    }

    @Test fun duracionKeywordConHoraSeResuelve() {
        val result = NaturalTaskParser.parse("Reunión duración 1 hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(60, result.durationMinutes)
    }

    @Test fun duracionKeywordConDosPuntos() {
        val result = NaturalTaskParser.parse("Reunión duración: 30", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
    }

    // "no olvides" es la perífrasis de recordatorio más común en español, pero
    // no estaba en [bareReminderVerbPattern] (solo "no dejes que olvide"). Así
    // "no olvides llamar mañana" dejaba "no olvides" en el título Y no aplicaba
    // el offset de respaldo de 30 min pese a tener fecha.
    @Test fun noOlvidesConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("no olvides llamar al doctor mañana", now, zone)
        assertEquals("llamar al doctor", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun noOlvidesQueConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("no olvides que pago el 10", now, zone)
        assertEquals("pago", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun noOlvidesSinDueNoFalsificaOffset() {
        val result = NaturalTaskParser.parse("no olvides llamar a mamá", now, zone)
        assertEquals("llamar a mamá", result.title)
        assertNull(result.reminderOffsetMinutes)
        assertNull(result.dueAt)
    }

    // "no se te olvide" / "no te olvides de" / "no me olvides": perífrasis de
    // recordatorio cotidianas con pronombre clítico. Antes dejaban el verbo en el
    // título y no aplicaban el offset de respaldo aunque hubiera fecha.
    @Test fun noSeTeOlvideConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("no se te olvide pagar la luz mañana", now, zone)
        assertEquals("pagar la luz", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun noSeTeOlvideSustantivoConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("no se te olvide la cita el viernes", now, zone)
        assertEquals("la cita", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun noTeOlvidesDeConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("no te olvides de la reunión mañana", now, zone)
        assertEquals("la reunión", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun noMeOlvidesConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("no me olvides llamar al doctor mañana", now, zone)
        assertEquals("llamar al doctor", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    // "acuérdate de" (imperativo de "acordarse"): muy común, antes no se reconocía.
    @Test fun acuerdateDeConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("acuérdate de la reunión mañana", now, zone)
        assertEquals("la reunión", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun acuerdateDeVerboConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("acuérdate de llamar a juan mañana", now, zone)
        assertEquals("llamar a juan", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    // "recuerda" (imperativo tú, sin pronombre): asimétrico con "recuérdame"
    // (con pronombre) que sí funcionaba. Antes quedaba en el título.
    @Test fun recuerdaImperativoConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("recuerda llamar a juan mañana", now, zone)
        assertEquals("llamar a juan", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun recuerdaImperativoSustantivoConDueLimpiaTituloYAplicaOffset() {
        val result = NaturalTaskParser.parse("recuerda la cita del viernes", now, zone)
        assertEquals("la cita", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    // "recuerda" NO debe casar cuando es sustantivo (recuerdos/recuerdo/recordando):
    // evita falsos positivos sobre sustantivos.
    @Test fun recuerdosSustantivoNoDisparaRecordatorio() {
        val result = NaturalTaskParser.parse("Comprar recuerdos para la fiesta", now, zone)
        assertEquals("Comprar recuerdos para la fiesta", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun recuerdoSustantivoNoDisparaRecordatorio() {
        val result = NaturalTaskParser.parse("Foto recuerdo de la boda", now, zone)
        assertEquals("Foto recuerdo de la boda", result.title)
        assertNull(result.reminderOffsetMinutes)
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

    @Test fun parsesMonthAbbreviations() {
        // Abreviaturas informales ("dic", "ene", "feb"): misma intención que el
        // nombre completo. Antes caían en dueAt=null y el compromiso se perdía.
        val dic = NaturalTaskParser.parse("llamar el 25 de dic", now, zone)
        assertEquals("llamar", dic.title)
        assertEquals(LocalDate.of(2026, 12, 25), DateRules.toLocalDate(dic.dueAt!!, zone))

        val ene = NaturalTaskParser.parse("pago el 1 de ene", now, zone)
        assertEquals("pago", ene.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(ene.dueAt!!, zone))

        val feb = NaturalTaskParser.parse("pago el 28 de feb", now, zone)
        assertEquals("pago", feb.title)
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(feb.dueAt!!, zone))
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

    // "mañana siguiente": forma reforzada/redundante de "mañana" (c.148 ABIERTO en BACKLOG).
    // Debe comportarse como "mañana" (dueAt = hoy+1, título limpio).
    @Test fun mananaSiguienteSeResuelveComoManana() {
        val result = NaturalTaskParser.parse("envío mañana siguiente", now, zone)
        assertEquals("envío", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Guard anti-falso-positivo: "siguiente" sin "mañana" es contenido, no fecha.
    @Test fun siguienteSoloEsContenidoNoFecha() {
        val result = NaturalTaskParser.parse("leer capítulo siguiente", now, zone)
        assertEquals("leer capítulo siguiente", result.title)
        assertNull(result.dueAt)
    }

    // "mañana siguiente" + hora explícita: la fecha y la hora se combinan sin residuo.
    @Test fun mananaSiguienteConHoraNoDejaResiduo() {
        val result = NaturalTaskParser.parse("envío mañana siguiente a las 8", now, zone)
        assertEquals("envío", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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
        // "12 de la manana" = medianoche (00:00). Capturada al mediodía, la medianoche de
        // hoy ya pasó (12h en el pasado) → se rueda a la medianoche de mañana (past-safe,
        // evita agendar la cita en el pasado donde el recordatorio se descartaría). La
        // aserción de hora (00:00, no 12:00) es el guardia real de la regresión del tilde.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // ── Regresión BUG4c: "importante" como palabra INICIAL (asimetría con "urgente") ──

    @Test fun leadingImportanteSetsHighPriority() {
        // "importante" como palabra inicial debe activar HIGH, simétrico a "urgente"→URGENT.
        // Antes solo se detectaba "urgente" inicial; "importante" caía a NORMAL (asimetría).
        val result = NaturalTaskParser.parse("importante llamar al cliente", now, zone)
        assertEquals(TaskPriority.HIGH, result.priority)
        // "importante" se limpia del título.
        assertEquals("llamar al cliente", result.title)
    }

    @Test fun leadingImportanteCaseInsensitive() {
        val result = NaturalTaskParser.parse("Importante revisar contrato", now, zone)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertEquals("revisar contrato", result.title)
    }

    @Test fun midSentenceImportanteDoesNotSetPriority() {
        // "es importante" a mitad de frase NO debe activar HIGH (evita falsos positivos),
        // simétrico al comportamiento de "urgente".
        val result = NaturalTaskParser.parse("el documento es importante revisarlo", now, zone)
        assertEquals(TaskPriority.NORMAL, result.priority)
        assertEquals("el documento es importante revisarlo", result.title)
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

    // --- Hora suelta con parte del día + fracción "y media"/"y cuarto" (ciclo 153) ---
    // "9 y media de la noche" → 21:30 (no la canónica 21:00) y título limpio. Antes el
    // sufijo fraccionario NO se reconocía en la forma SIN "a las": la hora caía a la
    // canónica de la parte del día y el número quedaba como residuo en el título
    // ("Cena 9 y media") → cita agendada 30 min antes y contenido degradado. Simétrica
    // del "a las N y media" ya soportado por [timePatterns]. El conector "de la <parte>"
    // es la señal de desambiguación que evita robar cantidades ("diez y media botellas"
    // no lleva "de la tarde").

    @Test fun nueveYMediaDeLaNocheEs21y30() {
        val result = NaturalTaskParser.parse("Cena 9 y media de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ochoYCuartoDeLaMananaEscritoEs8y15() {
        val result = NaturalTaskParser.parse("Cita ocho y cuarto de la mañana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(8, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun dosYMediaDeLaTardeEscritoEs14y30() {
        val result = NaturalTaskParser.parse("Llamada dos y media de la tarde", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalTime.of(14, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El patrón standalone ("N y <frac> de la <parte>", sin "a las") ahora admite la
    // misma fracción sub-hora que "a las N y <frac>": antes sólo "y media"/"y cuarto".
    @Test fun nueveYVeinteDeLaNocheStandaloneEs21_20() {
        val result = NaturalTaskParser.parse("Cena 9 y veinte de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 20), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun cuatroYTresCuartosDeLaTardeStandaloneEs16_45() {
        val result = NaturalTaskParser.parse("Cita cuatro y tres cuartos de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(16, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Fracción sub-hora TRAS el meridiem ("9 pm y media", "9 de la tarde y cuarto") ---
    // En español hablado la fracción puede ir DESPUÉS de la parte del día/meridiem, no
    // solo antes ("9 y media de la tarde"). Antes el orden fijo [fracción][meridiem] del
    // patrón "a las N" hacía que la fracción posterior no casara: la cita caía en punto
    // (21:00 en vez de 21:30) y "y media" quedaba como residuo en el título. Para una app
    // de recordatorios eso es una cita perdida/media hora mal + título degradado.

    @Test fun aLasNuevePmYMediaEs21y30() {
        val result = NaturalTaskParser.parse("Reunión a las 9 pm y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveDeLaNocheYMediaEs21y30() {
        val result = NaturalTaskParser.parse("Reunión a las 9 de la noche y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveAmYMediaEs9y30() {
        val result = NaturalTaskParser.parse("Reunión a las 9 am y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNuevePmYCuartoEs21y15() {
        val result = NaturalTaskParser.parse("Reunión a las 9 pm y cuarto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNuevePmYCuarentaYCincoEs21y45() {
        val result = NaturalTaskParser.parse("Reunión a las 9 pm y cuarenta y cinco", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveDeLaTardeYMediaEs21y30() {
        val result = NaturalTaskParser.parse("Reunión a las 9 de la tarde y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaPmYMediaEs13y30() {
        val result = NaturalTaskParser.parse("Cita a la una pm y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(13, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaDeLaTardeYMediaEs13y30() {
        val result = NaturalTaskParser.parse("Cita a la una de la tarde y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(13, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Fracción NEGATIVA tras meridiem: "3 pm menos cuarto" = 14:45 (resta sobre la hora
    // ya ajustada a PM, con wrap 24h).
    @Test fun aLasTresPmMenosCuartoEs14y45() {
        val result = NaturalTaskParser.parse("Tren a las 3 pm menos cuarto", now, zone)
        assertEquals("Tren", result.title)
        assertEquals(LocalTime.of(14, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasTresDeLaTardeMenosCuartoEs14y45() {
        val result = NaturalTaskParser.parse("Tren a las 3 de la tarde menos cuarto", now, zone)
        assertEquals("Tren", result.title)
        assertEquals(LocalTime.of(14, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // No-regresión: la fracción ANTES del meridiem sigue funcionando.
    @Test fun aLasNueveYMediaPmSigueSiendo21y30() {
        val result = NaturalTaskParser.parse("Reunión a las 9 y media pm", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // No-regresión: "y <palabra>" tras meridiem NO se roba como fracción si no es un
    // múltiplo de reloj ("y hablar" no es fracción) — la hora cae en punto y el verbo
    // queda en el título.
    @Test fun aLasNuevePmYHablarNoRobaFraccion() {
        val result = NaturalTaskParser.parse("Llamar a las 9 pm y hablar", now, zone)
        assertEquals("Llamar y hablar", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Forma SIN "a las": fracción TRAS la parte del día ("9 de la noche y media") ---
    // Simétrico del "a las N de la X y media" pero en la forma standalone (sin "a las").
    // Antes [standaloneHourPartOfDayPattern] sólo aceptaba la fracción ANTES de "de la"
    // ("9 y media de la noche"); la posterior no casaba y "y media" quedaba en el título
    // con la cita en punto (21:00 en vez de 21:30).

    @Test fun nueveDeLaNocheYMediaStandaloneEs21y30() {
        val result = NaturalTaskParser.parse("Cena 9 de la noche y media", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveDeLaNocheYCuartoStandaloneEs21y15() {
        val result = NaturalTaskParser.parse("Cena 9 de la noche y cuarto", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ochoDeLaMananaYMediaStandaloneEs8y30() {
        val result = NaturalTaskParser.parse("Cita 8 de la mañana y media", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(8, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // No-regresión: la fracción ANTES de "de la" sigue funcionando en la forma standalone.
    @Test fun nueveYMediaDeLaNocheStandaloneSigueSiendo21y30() {
        val result = NaturalTaskParser.parse("Cena 9 y media de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // No-regresión: "y <verbo>" tras la parte del día NO se roba como fracción standalone.
    @Test fun nueveDeLaNocheYHablarNoRobaFraccion() {
        val result = NaturalTaskParser.parse("Cena 9 de la noche y hablar", now, zone)
        assertEquals("Cena y hablar", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Una cantidad NO debe casar como hora aunque contenga "y media": no hay "de la <parte>".
    @Test fun cantidadConYMediaNoSeRobaComoHora() {
        val result = NaturalTaskParser.parse("diez y media botellas", now, zone)
        assertEquals("diez y media botellas", result.title)
        assertEquals(null, result.dueAt)
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

    // --- "y <minutos escritos>"/"y tres cuartos": minutos sub-hora cotidianos (ciclo 181) ---
    // "a las once y veinte"/"a las diez y tres cuartos"/"a las 9 y cuarenta y cinco" antes NO
    // se reconocían: el grupo 3 de [timePatterns] sólo admitía "y media"/"y cuarto", así que
    // "y veinte"/"y tres cuartos" quedaban como residuo en el título y la hora se agendaba en
    // punto (reunión/cita 20-45 min mal programados). Forma hablada cotidiana: el usuario dice
    // la hora con minutos ("nos vemos a las once y veinte") y la cita cae en el momento justo.
    // Simétrico del "menos veinte/cinco/diez/veinticinco" (ciclo 114) que SÍ restaba minutos:
    // la rama positiva tenía la misma asimetría — "menos veinte" funcionaba, "y veinte" no.

    @Test fun aLasOnceYVeinteEs11_20YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las once y veinte", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(11, 20), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasDiezYTresCuartosEs10_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las diez y tres cuartos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(10, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9YCuarentaYCincoEs9_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 9 y cuarenta y cinco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasDiezYDiezEs10_10YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las diez y diez", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(10, 10), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas2YVeinticincoEs2_25YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 2 y veinticinco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(2, 25), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas7YVeinteDeLaTardeAplicaPm() {
        val result = NaturalTaskParser.parse("Cita a las 7 y veinte de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(19, 20), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9Y25PmEs21_25() {
        // Forma numérica del minuto escrito: "a las 9 y 25" → 09:25, con PM → 21:25.
        val result = NaturalTaskParser.parse("Cita a las 9 y 25 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 25), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaYTresCuartosEs1_45() {
        // "a la una y tres cuartos" = 1:45 (simétrico de "a la una y media" = 1:30).
        val result = NaturalTaskParser.parse("Cita a la una y tres cuartos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(1, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasCincoYCincuentaYCincoEs5_55() {
        val result = NaturalTaskParser.parse("Cita a las cinco y cincuenta y cinco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(5, 55), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun yVeinteNoCorrompeTituloConFecha() {
        // La fracción se consume y el título queda limpio junto con la fecha.
        val result = NaturalTaskParser.parse("Reunión mañana a las once y veinte", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 20), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLas8YTreintaYCincoEs8_35() {
        val result = NaturalTaskParser.parse("Cita a las 8 y treinta y cinco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(8, 35), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "mediodía y media"/"medianoche y cuarto": fracción sub-hora en horas canónicas ---
    // "al mediodía y media"/"a la medianoche y cuarto" antes dejaban "y media"/"y cuarto"
    // como residuo en el título y la hora quedaba en punto (12:00/00:00, no 12:30/00:15):
    // el almuerzo o el cierre de día se agendaban 15-30 min antes de lo pedido. Simétrico
    // del "a las N y media" ya soportado: las horas canónicas mediodía/medianoche reciben
    // ahora la misma fracción sub-hora.

    @Test fun alMediodiaYMediaEs12_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Almuerzo al mediodía y media", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(12, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaMedianocheYCuartoEs0_15YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Entregar a la medianoche y cuarto", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalTime.of(0, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alMediodiaYTresCuartosEs12_45() {
        val result = NaturalTaskParser.parse("Reunión al mediodía y tres cuartos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alMediodiaYVeinteEs12_20() {
        val result = NaturalTaskParser.parse("Reunión al mediodía y veinte", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 20), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun mediodiaSoloSigueSiendo12_00() {
        // No-regresión: "al mediodía" sin fracción sigue siendo 12:00.
        val result = NaturalTaskParser.parse("Almuerzo al mediodía", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // Rango con sufijo de unidad COMPACTO "h"/"hs" en cada extremo ("9h a 11h",
    // "9hs a 11hs"). Es la forma simétrica del reloj "HH:MMh"/"a las Nh" (c.235/c.245):
    // antes el sufijo por extremo no se admitía y el rango caía (dur=null, título sucio).
    @Test fun rangeWithCompactUnitPerBoundParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9h a 11h", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    @Test fun rangeWithHsUnitPerBoundParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9hs a 11hs", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // "9 horas a 11 horas": sufijo de palabra completa en cada extremo. Antes el extremo
    // inicial "9 horas" rompía el patrón (no es meridiem) y, peor, "9 horas" era robado
    // como duración falsa (540 min) con el rango perdido.
    @Test fun rangeWithWordUnitPerBoundParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9 horas a 11 horas", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // Unidad sólo en el extremo inicial ("9h a 11"): también es evidencia de reloj.
    @Test fun rangeWithLeadingCompactUnitParsesDuration() {
        val result = NaturalTaskParser.parse("Clase de 9h a 11", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
    }

    // --- Minutos compactos "NhMM" por extremo (c.248) ---
    // La forma "11h30" (unidad ENTRE hora y minutos, sin dos puntos) no casaba como extremo
    // de rango: el extremo final fallaba → el rango se perdía y el inicial "9h" era robado
    // como duración falsa (540 min) con residuo "a 11h30" en el título → dato falseado.
    // "9h a 11h30": inicio en punto con unidad, fin NhMM. Antes dur=540 + título "clase a 11h30".
    @Test fun rangeWithNhMmEndParsesRealDurationNotFalseDuration() {
        val result = NaturalTaskParser.parse("Clase 9h a 11h30", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(150, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Mismo rango con guion como separador: antes también robaba "9h" como 540 min y dejaba
    // "-11h30" en el título.
    @Test fun rangeWithNhMmEndAndDashSeparatorParsesRealDuration() {
        val result = NaturalTaskParser.parse("Clase 9h-11h30", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(150, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "de 9h30 a 11h30": ambos extremos NhMM. Antes NO casaba (dur=null, título sucio). Ahora
    // dur real 120 y hora de inicio 09:30.
    @Test fun rangeWithNhMmBothBoundsParsesRealDurationAndStartTime() {
        val result = NaturalTaskParser.parse("Clase de 9h30 a 11h30", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El extremo inicial NhMM conserva los minutos en la hora de inicio (no los pierde como
    // ocurría con "7:15h" antes del c.235).
    @Test fun rangeWithNhMmStartKeepsStartMinutes() {
        val result = NaturalTaskParser.parse("Tren de 7h15 a 9h45", now, zone)
        assertEquals("Tren", result.title)
        assertEquals(150, result.durationMinutes)
        assertEquals(LocalTime.of(7, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Rango NhMM con meridiem en el extremo final: "de 9h30 a 11h30 pm" → inicio hereda el PM
    // (9<=11) → 21:30-23:30 = 120 min. Antes NO casaba (dur=null).
    @Test fun rangeWithNhMmBothBoundsAndTrailingPmPropagatesToStart() {
        val result = NaturalTaskParser.parse("Clase de 9h30 a 11h30 pm", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Mezcla de formas: inicio NhMM + fin con dos puntos ("9h30 a 11:30"). Debe casar igual.
    @Test fun rangeWithNhMmStartAndColonEndParsesRealDuration() {
        val result = NaturalTaskParser.parse("Clase de 9h30 a 11:30", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Seguridad (c.248): la forma compacta "NhMM" no debe robar la "h" de palabras. "2 h30"
    // (con espacio) no es reloj ni rango → se conserva íntegro, sin falsear duración.
    @Test fun nhMmLikeTokenNotStolenAsDurationInNonRange() {
        val result = NaturalTaskParser.parse("Comprar 2 h30 cosas", now, zone)
        assertEquals("Comprar 2 h30 cosas", result.title)
        assertNull(result.durationMinutes)
        assertNull(result.dueAt)
    }

    // Seguridad (c.247): la detección de unidad por escaneo usa límites de palabra, así
    // que la "h" inicial de palabras como "hola"/"hoy"/"hablar" tras un rango <13 sin
    // unidad NO se confunde con "h" horaria. Sigue siendo cantidad, no rango horario.
    @Test fun rangeDoesNotStealWordInitialHAsUnit() {
        val result = NaturalTaskParser.parse("Comprar de 2 a 5 helados", now, zone)
        assertEquals("Comprar de 2 a 5 helados", result.title)
        assertNull(result.durationMinutes)
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

    // --- plural de período próximo: "próximas semanas", "próximos meses",
    // "próximos años", "próximos trimestres" ---
    // El plural es la forma vaga de futuro más cotidiana ("el proyecto estará listo
    // en las próximas semanas", "entrega en los próximos meses"). Antes el singular
    // ("próxima semana") se resolvía pero el plural no coincidía → dueAt=null + frase
    // íntegra como residuo en el título → vencimiento olvidado (sin recordatorio ni
    // visibilidad en What Now/planificador). Es la misma brecha de simetría que
    // "próximos días" (c.32, P1) extendida a los demás períodos en plural.

    @Test fun proximasSemanasParsesDueAt() {
        // +7 días, igual que el singular "próxima semana".
        val result = NaturalTaskParser.parse("Viaje en las próximas semanas", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximasSemanasSinPrefijoParsesDueAt() {
        // "próximas semanas" sin "en las" también debe resolver y limpiar el título.
        val result = NaturalTaskParser.parse("Viaje próximas semanas", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximosMesesParsesDueAt() {
        // +30 días, igual que el singular "próximo mes".
        val result = NaturalTaskParser.parse("Proyecto en los próximos meses", now, zone)
        assertEquals("Proyecto", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximosAniosParsesDueAt() {
        // +365 días, igual que el singular "próximo año".
        val result = NaturalTaskParser.parse("Meta para los próximos años", now, zone)
        assertEquals("Meta", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximosTrimestresParsesDueAt() {
        // +90 días, igual que el singular "próximo trimestre".
        val result = NaturalTaskParser.parse("Revisión en los próximos trimestres", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 10, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun semanasQueVienenParsesDueAt() {
        // Plural de "la semana que viene": +7 días.
        val result = NaturalTaskParser.parse("Viaje en las semanas que vienen", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximasSemanasRespetaHoraExplicita() {
        // Como el singular, el plural combina con hora explícita.
        val result = NaturalTaskParser.parse("Entrega en las próximas semanas a las 10", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "principios/mediados/finales de mes de <mes>": límite mensual con mes EXPLÍCITO ---
    // El usuario a menudo nombra el mes ("finales de mes de octubre", "a fin de mes de
    // diciembre"). Antes el "<de mes>" se consumía como límite del mes ACTUAL y el
    // "de <mes>" quedaba pegado al título → fecha WRONG (mes en curso) y residuo. Ahora
    // se respeta el mes nombrado y el año implícito/explícito (con roll al año siguiente
    // si la fecha ya pasó, igual que parseMonthNameDate). P1: vencimiento de
    // pago/renta/cobro sin fecha correcta = recordatorio incorrecto. now = 2026-07-29.

    @Test fun finalesDeMesConMesExplicitoResuelveMesNombrado() {
        val result = NaturalTaskParser.parse("renta finales de mes de octubre", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2026, 10, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeMesConMesExplicitoResuelveDia15() {
        val result = NaturalTaskParser.parse("cobro mediados de mes de septiembre", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesConMesExplicitoResuelveDia1() {
        val result = NaturalTaskParser.parse("pago principios de mes de agosto", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeMesConMesExplicitoRuedaAlAnoSiguienteSiPaso() {
        // 31 de marzo de 2026 < 29/7/2026 → rueda a 31 de marzo de 2027.
        val result = NaturalTaskParser.parse("pago finales de mes de marzo", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2027, 3, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeMesConMesExplicitoYAno() {
        val result = NaturalTaskParser.parse("renta finales de mes de octubre de 2027", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2027, 10, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeMesConMesExplicitoLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pago a fin de mes de diciembre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDelMesConMesExplicitoLimpiaTitulo() {
        // "a principios del mes de agosto": variante con "del" + título "pago" limpio.
        val result = NaturalTaskParser.parse("pago a principios del mes de agosto", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "cada fin de mes de <mes>": NO recurre (cada + mes específico = plazo único) ---
    // "cada" + límite mensual genera recurrencia, PERO "cada fin de mes de octubre" fija
    // un vencimiento único en octubre: combinarlo con recurrencia sería un sinsentido.
    // El guard evita la recurrencia, respeta la fecha del mes nombrado y limpia el título.
    @Test fun cadaFinDeMesConMesExplicitoNoRecurre() {
        val result = NaturalTaskParser.parse("pago cada fin de mes de octubre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(LocalDate.of(2026, 10, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "al final/al principio/al inicio de <mes>": límite + mes NOMBRE sin "de mes" ---
    // monthBoundaryNamePattern cubre "mediados/finales/principios de <mes nombre>" sin
    // la frase "de mes". El singular "final" no casaba (solo "finales") y el prefijo "al "
    // quedaba pegado al título. Ahora "final" singular se resuelve (último día del mes
    // nombrado) y "al/principio/inicio" limpian el título. now = 2026-07-29.

    @Test fun alFinalDeMesNombreResuelveUltimoDia() {
        val result = NaturalTaskParser.parse("pago al final de agosto", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alPrincipioDeMesNombreResuelveDia1() {
        val result = NaturalTaskParser.parse("renta al principio de agosto", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alInicioDeMesNombreResuelveDia1() {
        val result = NaturalTaskParser.parse("entregar al inicio de agosto", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "cada fin/mediados/principios de mes": RECURRENCIA mensual de límite (c.257) ---
    // Antes, "cada fin de mes" se trataba como fecha única (rec=NONE): al completar la
    // tarea ésta NO generaba próxima ocurrencia → pago/cierre mensual recurrente se
    // olvidaba ciclo a ciclo (P1: "evitar olvidos"). El prefijo "cada" ahora convierte
    // el límite mensual en recurrencia MONTHLY anclada: fin→último día real (EOM),
    // mediados→día 15, principios→día 1. La 1ª ocurrencia usa el propio límite; el motor
    // avanza ciclo a ciclo sin deriva. now = 2026-07-29 → fin=7/31, mediados=8/15,
    // principios=8/1.

    @Test fun cadaFinDeMesGeneraRecurrenciaMensualUltimoDia() {
        val result = NaturalTaskParser.parse("Reporte cada fin de mes", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaFinDeMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago cada fin de mes a las 18", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cadaMediadosDeMesGeneraRecurrenciaMensualDia15() {
        val result = NaturalTaskParser.parse("Reporte cada mediados de mes", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // mediados ancla al día 15 (existe en todo mes): sin codificación especial.
        assertEquals("", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaPrincipiosDeMesGeneraRecurrenciaMensualDia1() {
        val result = NaturalTaskParser.parse("Renta cada principios de mes", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // principios ancla al día 1 (existe en todo mes): sin codificación especial.
        assertEquals("", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Sin "cada", el límite sigue siendo fecha única (regresión: no convertir a recurrencia).
    @Test fun finDeMesSinCadaSigueSiendoFechaUnica() {
        val result = NaturalTaskParser.parse("Reporte fin de mes", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Sinónimos de fin de mes ("finales"/"cierre"/"corte"/"último día") también generan
    // recurrencia con "cada".
    @Test fun cadaFinalesDeMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("Cierre cada finales de mes", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaUltimoDiaDelMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("Auditoría cada último día del mes", now, zone)
        assertEquals("Auditoría", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "el último día de cada mes": cadencia "cada" DENTRO del límite (c.311) ---
    // Antes el patrón de límite exigía "de/del" + "mes" contiguos, así "de cada mes"
    // (con "cada" intercalado) NO casaba: el límite mensual se perdía, "cada mes" caía a
    // fixedPatterns (MONTHLY anclado al día de captura) y "el último día de" sobrevivía
    // como residuo del título ('renta el último día de'). P1: renta/vencimiento mal
    // fechado al día de captura (no fin de mes) y título corrupto. now=2026-07-29 → fin
    // del mes en curso = 7/31, recurrencia MONTHLY EOM (no omite meses cortos).
    @Test fun ultimoDiaDeCadaMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("renta el último día de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoDiaDeCadaMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("alquiler último día de cada mes a las 18", now, zone)
        assertEquals("alquiler", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "mediados de todos los meses": misma cadencia intercalada, forma plural. Ancla al
    // día 15 (existe en todo mes) vía dueAt, no EOM.
    @Test fun mediadosDeTodosLosMesesGeneraRecurrenciaMensualDia15() {
        val result = NaturalTaskParser.parse("reporte mediados de todos los meses", now, zone)
        assertEquals("reporte", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Sin "cada"/"todos los" intercalado, el límite sigue siendo fecha única (regresión:
    // no convertir "el último día del mes" en recurrencia). Cubre también la forma sin
    // artículo ("último día de mes").
    @Test fun ultimoDiaDelMesSinCadaSigueSiendoFechaUnica() {
        val result = NaturalTaskParser.parse("pago el último día del mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
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

    // --- "final de mes" (singular) = sinónimo cotidiano de "fin de mes" ---
    // "fin"/"finales" ya se parseaban, pero la forma SINGULAR "final" NO: el patrón
    // `fin(?:ales|es)?` casa fin/fines/finales pero NO "final" (fin+al). Así "enviar
    // reporte a final de mes" / "final del mes" / "al final del mes" caían a dueAt=null
    // → el vencimiento se olvidaba (P1: sin recordatorio ni visibilidad en What Now),
    // con la frase completa como residuo en el título. Asimetría léxica: mismo
    // significado, distinta suerte según la palabra elegida. Fix: el patrón admite
    // "final" y el prefijo "al " (cotidiano: "al final del mes"). now = 2026-07-29.

    @Test fun finalDeMesParsesDueAtFinDeMes() {
        val result = NaturalTaskParser.parse("Enviar reporte final de mes", now, zone)
        assertEquals("Enviar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalDeMesParsesDueAtFinDeMes() {
        val result = NaturalTaskParser.parse("Pagar renta a final de mes", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalDelMesParsesDueAtFinDeMes() {
        val result = NaturalTaskParser.parse("Cierre final del mes", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alFinalDelMesParsesDueAtFinDeMesTituloLimpio() {
        val result = NaturalTaskParser.parse("Entregar informe al final del mes", now, zone)
        assertEquals("Entregar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalDelMesQueVieneAnclaFinMesSiguiente() {
        // El modificador "mes que viene" se consume completo (título limpio) y ancla al
        // mes siguiente. Antes "final" no casaba → "mes que viene" caía al patrón de
        // período y dejaba "final del" como residuo en el título.
        val result = NaturalTaskParser.parse("Pago final del mes que viene", now, zone)
        assertEquals("Pago", result.title)
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

    // --- "mediados/finales/principios de [mes nombre]": límite mensual con mes explícito ---
    // Plazos como "pago a mediados de septiembre", "cierre a finales de octubre",
    // "renta a principios de enero" son cotidianísimos al agendar vencimientos futuros.
    // Antes caían a dueAt=null: el vencimiento quedaba sin fecha (olvido, P1: pago/renta
    // sin recordatorio) y la frase entera sobrevivía como título basura. principios→día 1,
    // mediados/mitad→día 15, finales/fin/cierre→último día del mes. Sin año explícito, si
    // la fecha ya pasó se rueda al año siguiente (mismo criterio que "25 de enero").
    // now = 2026-07-29.

    @Test fun mediadosDeMesNombreAnclaDia15() {
        val result = NaturalTaskParser.parse("Pago a mediados de septiembre", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeMesNombreAnclaUltimoDia() {
        val result = NaturalTaskParser.parse("Envío a finales de octubre", now, zone)
        assertEquals("Envío", result.title)
        assertEquals(LocalDate.of(2026, 10, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesNombreAnclaDia1() {
        val result = NaturalTaskParser.parse("Entregar a principios de enero", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeMesNombreFuturoNoRuedaAnioSiAunNoPasa() {
        // "principios de diciembre" desde julio 2026 → 1/12/2026 (aún no pasa).
        val result = NaturalTaskParser.parse("Cobro a principios de diciembre", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 12, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeMesNombreAnclaUltimoDia() {
        val result = NaturalTaskParser.parse("Reunión fin de septiembre", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDeMesNombreAnclaDia15() {
        val result = NaturalTaskParser.parse("pago mitad de enero", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2027, 1, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeMesAbreviaturaAnclaDia15() {
        // Abreviatura de mes ("dic") se acepta igual que el nombre completo.
        val result = NaturalTaskParser.parse("Pago a mediados de dic", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 12, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreLimiteRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pago a finales de octubre a las 18", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 10, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mesNombreLimiteConAnioExplicitoNoRueda() {
        // "principios de febrero del 2028" → 1/2/2028, sin rolado de año.
        val result = NaturalTaskParser.parse("cierre a principios de febrero del 2028", now, zone)
        assertEquals("cierre", result.title)
        assertEquals(LocalDate.of(2028, 2, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreLimiteNoColisionaConSemanaAno() {
        // "mediados de semana"/"fin de año"/"mitad de año" NO son límites mensuales:
        // el handler original debe seguir resolviéndolos (sin robarlos ni romper título).
        val sem = NaturalTaskParser.parse("Reunión mediados de semana", now, zone)
        assertEquals("Reunión", sem.title)
        assertNotNull(sem.dueAt)
        val ano = NaturalTaskParser.parse("Cierre fin de año", now, zone)
        assertEquals("Cierre", ano.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(ano.dueAt!!, zone))
    }

    // --- "inicio/inicios de mes": sinónimo cotidiano de "principios de mes" ---
    // "inicio(s) de mes" / "a inicios de mes" son la forma más natural de decir
    // "principios de mes" (alquileres, nómina, facturas que vencen a inicios de mes).
    // Antes NO se parseaban → dueAt=null (vencimiento olvidado, P1: sin recordatorio ni
    // visibilidad en planificador/What Now) y la frase entera sobrevivía como título.
    // Peor, "inicio del mes que viene" caía a +1 mes desde hoy (2026-08-28 en vez de
    // 2026-08-01): vencimiento ERRÓNEO, no olvidado — el usuario creía agendar el 1 del
    // mes siguiente y la fecha quedaba casi un mes desplazada. Asimetría flagrante con
    // "principios/comienzo" que SÍ funcionaban. "inicios?" se trata idéntico a
    // "principios". now = 2026-07-29 (día 29 > 1) → rueda al 1/8.

    @Test fun inicioDeMesAnclaDia1MesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro inicio de mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun iniciosDeMesAnclaDia1MesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro inicios de mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aIniciosDeMesAnclaDia1MesSiguiente() {
        val result = NaturalTaskParser.parse("Renta a inicios de mes", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun inicioDelMesQueVieneAnclaDia1MesSiguiente() {
        // "inicio del mes que viene" = 1 del mes siguiente, NO +1 mes desde hoy.
        // Antes caía a 2026-08-28 (fecha errónea) con título basura "Cobro inicio del".
        val result = NaturalTaskParser.parse("Cobro inicio del mes que viene", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun inicioDelMesProximoAnclaDia1MesSiguiente() {
        val result = NaturalTaskParser.parse("Pago inicio del mes próximo", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun inicioDeMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cobro inicio de mes a las 9", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "inicio/inicios de semana": sinónimo de "principios de semana" (lunes) ---
    // El propio comentario del patrón startOfWeekPattern describe la frase mental como
    // "a inicios de la semana" — pero la forma "inicios" no se reconocía → dueAt=null.
    // now = 2026-07-29 (miércoles) → lunes siguiente = 2026-08-03.

    @Test fun iniciosDeSemanaAnclaProximoLunes() {
        val result = NaturalTaskParser.parse("Reunión inicios de semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aIniciosDeLaSemanaAnclaProximoLunes() {
        val result = NaturalTaskParser.parse("Reunión a inicios de la semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun inicioDeSemanaAnclaProximoLunes() {
        val result = NaturalTaskParser.parse("Reunión inicio de semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "inicio/inicios de año": sinónimo de "principios de año" (1 de enero) ---
    // "inicios de año" (propósitos de año nuevo, renovaciones anuales) caía a dueAt=null.
    // now = 2026-07-29 → 1/1 del año siguiente = 2027-01-01.

    @Test fun iniciosDeAnoAnclaPrimeroDeEneroSiguiente() {
        val result = NaturalTaskParser.parse("Cierre inicios de año", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun inicioDeAnoAnclaPrimeroDeEneroSiguiente() {
        val result = NaturalTaskParser.parse("Cierre inicio de año", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "inicios de [mes nombre]": sinónimo de "principios de [mes nombre]" ---
    // "a inicios de enero"/"a inicios de septiembre" (vencimientos a principio de un mes
    // futuro) caían a dueAt=null. Antes caía a null: vencimiento olvidado. principios→1.

    @Test fun iniciosDeMesNombreAnclaDia1() {
        val result = NaturalTaskParser.parse("Entregar a inicios de enero", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun iniciosDeMesNombreFuturoNoRuedaAnioSiAunNoPasa() {
        // "inicios de diciembre" desde julio 2026 → 1/12/2026 (aún no pasa).
        val result = NaturalTaskParser.parse("Cobro a inicios de diciembre", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 12, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun iniciosDeMesNombreProximoNoRuedaAnioSiAunNoPasa() {
        // "inicios de septiembre" desde julio 2026 → 1/9/2026 (aún no pasa).
        val result = NaturalTaskParser.parse("Cobro a inicios de septiembre", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "fin de quincena" / "a fin de quincena" / "a fin de la quincena": sinónimo de cierre ---
    // "fin de quincena"/"a fin de quincena" son sinónimos cotidianos del hito de cierre de
    // quincena (como "fin de mes" = cierre de mes). Antes el patrón solo tragaba "de quincena"
    // y dejaba "a fin"/"fin" como residuo de título ("cobrar a fin de quincena" → título
    // "cobrar a fin"), pese a fechar bien. La fecha resuelve igual que "la quincena" sin
    // cualificar (próximo hito: día 15 si <15, fin de mes si ≥15).
    // now = 2026-07-29 (≥ 15) → fin de mes (31/7).

    @Test fun aFinDeQuincenaLimpiaTituloYFechaProximoHito() {
        val result = NaturalTaskParser.parse("Cobrar a fin de quincena", now, zone)
        assertEquals("Cobrar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeLaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pagar a fin de la quincena", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeQuincenaSinAInicialLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cobro fin de quincena", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeLaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pago fin de la quincena", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeLaPrimeraQuincenaLimpiaYResuelveDia15() {
        // "a fin de la primera quincena" conserva el cualificador (primera→día 15) y limpia
        // el prefijo completo. hoy = 29/7 ≥ 15 → primera quincena rueda al 15/8.
        val result = NaturalTaskParser.parse("Cobro a fin de la primera quincena", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeQuincenaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cobro a fin de quincena a las 18", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
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

    // "entre lunes y viernes" significa lo mismo que "de lunes a viernes" y
    // "entre semana": la semana laboral Lun-Vie. Antes esta forma cotidiana NO
    // casaba como rango y caía a la lista de días sueltos, produciendo
    // recDays="1,5" (¡solo lunes y viernes!) y dejando "entre" como residuo en
    // el título. Rutina mutilada en silencio + título sucio. c.281.
    @Test fun entreLunesYViernesComoRangoWeekdayLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Gimnasio entre lunes y viernes", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "entre sabado y domingo" = hábito de fin de semana (Sáb+Dom). Los días son
    // correctos como lista {6,7} (no hay día entre sábado y domingo), pero el
    // conector "entre" quedaba como residuo en el título. c.281.
    @Test fun entreSabadoYDomingoLimpiaTitulo() {
        val result = NaturalTaskParser.parse("futbol entre sabado y domingo", now, zone)
        assertEquals("futbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
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

    // --- "este mes" / "este a\u00f1o": plazo blando = fin del mes/a\u00f1o en curso ---
    // Frases cotidianas ("renovar licencia este mes", "cerrar ejercicio este a\u00f1o") que
    // antes ca\u00edan a dueAt=null (tarea olvidada) dejando la frase entera como residuo en
    // el t\u00edtulo. Plazo blando sim\u00e9trico a "esta semana" (fin de semana): "este mes" =
    // \u00faltimo d\u00eda del mes en curso; "este a\u00f1o" = 31/12 del a\u00f1o en curso.

    @Test fun esteMesAnclaFinMesActual() {
        // ahora = 29/7 -> fin de julio = 31/7.
        val result = NaturalTaskParser.parse("Renovar licencia este mes", now, zone)
        assertEquals("Renovar licencia", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cerrar cuentas este mes a las 18", now, zone)
        assertEquals("Cerrar cuentas", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun esteMesSiHoyEsUltimoDiaEsHoy() {
        // hoy = 31/8 (\u00faltimo d\u00eda de agosto) -> "este mes" vence hoy (no rueda al mes pr\u00f3ximo).
        val ultimoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 31), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago este mes", ultimoNow, zone)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteAnoAncla31Diciembre() {
        val result = NaturalTaskParser.parse("Cerrar ejercicio este a\u00f1o", now, zone)
        assertEquals("Cerrar ejercicio", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMesNoColisionaConDiaDelMes() {
        // "el 15 de este mes" ya lo resuelve dayOfMonthPattern (15 del mes en curso): el
        // "este mes" suelto NO debe robarlo ni dejar residuo ni cambiar la fecha al 31.
        val result = NaturalTaskParser.parse("Reuni\u00f3n el 15 de este mes", now, zone)
        assertEquals("Reuni\u00f3n", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMesNoColisionaConFinDeMes() {
        // "fin de este mes" ya lo resuelve endOfMonthPattern; el "este mes" suelto NO debe
        // actuar aqu\u00ed (su lookbehind (?<!de\\s) lo bloquea) ni dejar doble residuo.
        val result = NaturalTaskParser.parse("Pago fin de este mes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "hace media hora / un cuarto de hora" y fraccionarias PASADAS (c.362) ---
    // Espejo de la familia futura ("en media hora"). Antes "hace media hora" caía a
    // fractionalDurationPattern -> dueAt=null, durationMinutes=30 y residuo en el título
    // ("hace media hora llamé" -> título "hace llamé"). El usuario registra una tarea
    // vencida honesta y se quedaba SIN fecha + título corrupto (P1: recordatorio perdido).
    // "hace un cuarto de hora" además casaba multiHourPattern y fechaba a -3 h en vez de
    // -15 min (falso vencimiento). now=2026-07-29 (miércoles) 12:00.

    @Test fun haceMediaHoraEsFechaPasadaYSinDuracion() {
        // 12:00 - 30 min = 11:30 mismo día.
        val result = NaturalTaskParser.parse("Llamé hace media hora", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 30), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun haceUnCuartoDeHoraEsFechaPasadaYSinDuracion() {
        // 12:00 - 15 min = 11:45 mismo día.
        val result = NaturalTaskParser.parse("Llamé hace un cuarto de hora", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 45), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun haceMediaHoraAlInicioDelTituloLimpiaSinResiduo() {
        // "hace media hora llamé" -> "llamé" (no "hace llamé").
        val result = NaturalTaskParser.parse("hace media hora llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "hace una hora y media" = -(60 + 30) = -90 min. Antes [agoPattern] robaba solo
    // "hace una hora" (-60) y "y media" quedaba en el título ("y media llamé"), agendando
    // 30 min antes de lo pedido y ensuciando el título.
    @Test fun haceUnaHoraYMediaEsFechaPasadaDeMenos90Min() {
        // 12:00 - 90 min = 10:30 mismo día.
        val result = NaturalTaskParser.parse("hace una hora y media llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 30), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun haceUnaHoraYCuartoEsFechaPasadaDeMenos75Min() {
        // 12:00 - 75 min = 10:45 mismo día.
        val result = NaturalTaskParser.parse("hace una hora y cuarto llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 45), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceMediaHoraYCuartoEsFechaPasadaDeMenos45Min() {
        // 12:00 - 45 min = 11:15 mismo día. Antes "media hora" robaba +30 y "y cuarto"
        // se perdía, dejando vencimiento a 11:30 (15 min antes de lo pedido).
        val result = NaturalTaskParser.parse("hace media hora y cuarto llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 15), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hace2HorasYMediaEsFechaPasadaDeMenos150Min() {
        // 12:00 - 150 min = 09:30 mismo día.
        val result = NaturalTaskParser.parse("hace dos horas y media llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hace3HorasYCuartoDigitosEsFechaPasadaDeMenos195Min() {
        // 12:00 - 195 min = 08:45 mismo día.
        val result = NaturalTaskParser.parse("hace 3 horas y cuarto llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 45), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnaHoraYTresCuartosEsFechaPasadaDeMenos105Min() {
        // 12:00 - 105 min = 10:15 mismo día.
        val result = NaturalTaskParser.parse("hace una hora y tres cuartos llamé", now, zone)
        assertEquals("llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 15), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Regresión: la familia FUTURA no debe romperse (simetría).
    @Test fun enMediaHoraSigueSiendoFechaFutura() {
        val result = NaturalTaskParser.parse("Llamar en media hora", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

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

    // --- "unos"/"unas" (plural indeterminado pequeño, coloquial = "un par de" = 2) ---
    // Asimetría P1: "un par de minutos" sí agendaba, pero "unos minutos" (la forma móvil
    // natural) no casaba → dueAt=null, título basura → tarea olvidada (sin recordatorio,
    // invisible en What Now/planificador). Se resuelve a 2 unidades. El prefijo ("en"…)
    // + la palabra de unidad protegen de falsos positivos.

    @Test fun enUnosMinutosResuelveMasDosMin() {
        val result = NaturalTaskParser.parse("Llamar a mamá en unos minutos", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertNotNull(result.dueAt)
        // now 2026-07-29 12:00 + 2 min → mismo día, 12:02.
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 2), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enUnosDiasResuelveMasDosDias() {
        // now 2026-07-29 + 2 días = 2026-07-31.
        val result = NaturalTaskParser.parse("Revisar propuesta en unos días", now, zone)
        assertEquals("Revisar propuesta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnasHorasResuelveMasDosHoras() {
        // now 2026-07-29 12:00 + 2 h = 14:00.
        val result = NaturalTaskParser.parse("Reunión en unas horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enUnasSemanasResuelveMasCatorceDias() {
        // now 2026-07-29 + 14 días = 2026-08-12.
        val result = NaturalTaskParser.parse("Enviar borrador en unas semanas", now, zone)
        assertEquals("Enviar borrador", result.title)
        assertEquals(LocalDate.of(2026, 8, 12), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnosMesesResuelveMasSesentaDias() {
        // now 2026-07-29 + 60 días = 2026-09-27.
        val result = NaturalTaskParser.parse("Renovar suscripción en unos meses", now, zone)
        assertEquals("Renovar suscripción", result.title)
        assertEquals(LocalDate.of(2026, 9, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enUnosDiasConHoraExplicita() {
        // La fecha relativa se combina con hora explícita: +2d a las 10:00.
        val result = NaturalTaskParser.parse("Llamar al cliente en unos días a las 10", now, zone)
        assertEquals("Llamar al cliente", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun unosNoGeneraFechaCuandoNoEsUnidadDeTiempo() {
        // Guard anti-falso-positivo: "unos" + sustantivo que no es unidad de tiempo no
        // crea vencimiento ("comprar unos libros" no es "en unos …"). Título intacto.
        val result = NaturalTaskParser.parse("Comprar unos libros", now, zone)
        assertEquals("Comprar unos libros", result.title)
        assertNull(result.dueAt)
    }

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

    // --- "de aquí a/al + FECHA ESPECÍFICA" (día de semana, mañana, hoy, día N):
    // el conector direccional-temporal "de aquí a/al" no casa ningún patrón
    // relativo (no hay cantidad) y sobrevivía como residuo en el título
    // ("entregar de aquí al" aunque la fecha era correcta); peor aún, "de aquí
    // al 15" caía a dueAt=null (dayOfMonthPattern exige "el", no "al") →
    // vencimiento olvidado (P1). Ahora el conector se consume/reescribe.

    @Test fun deAquiAlViernesParsesDueAtSinResiduo() {
        val result = NaturalTaskParser.parse("Entregar de aquí al viernes", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAlLunesParsesDueAtSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión de aquí al lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAl15ParsesDueAtSinResiduo() {
        // Peor caso del defecto: vencimiento olvidado (dueAt=null antes del fix).
        val result = NaturalTaskParser.parse("Pago de aquí al 15", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAManhanaParsesDueAtSinResiduo() {
        val result = NaturalTaskParser.parse("Envío de aquí a mañana", now, zone)
        assertEquals("Envío", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAHoyParsesDueAtSinResiduo() {
        val result = NaturalTaskParser.parse("Llamar de aquí a hoy", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAlaSemanaQueVieneParsesDueAtSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión de aquí a la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAcaAlDomingoParsesDueAtSinResiduo() {
        // Variante "de acá al" (coloquial, sin tilde en 'a').
        val result = NaturalTaskParser.parse("Reunión de acá al domingo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAMediaHoraNoRegression() {
        // Regresión guard: el conector "de aquí a" con CANTIDAD fraccionaria
        // ("media hora") ya era capturado por fractionalRelativePattern; el fix
        // del conector huérfano NO debe romperlo (debe seguir dando +30 min).
        val result = NaturalTaskParser.parse("Llamar de aquí a media hora", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
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

    // --- "cada otro día" / "un día sí y otro no" = cada dos días (DAILY interval=2) ---
    // Equivalentes idiomáticos de "cada dos días" (que casa en `intervalPattern`).
    // "cada otro día" (calque de "every other day", muy usado en LATAM para medicación) y
    // "un día sí y otro no" (forma nativa) significan cada dos días. Antes caían a NONE
    // → la tarea recurrente nacía SIN fecha ni cadencia (rutina/medicación olvidada:
    // recordatorio jamás disparaba). Ahora se mapean a DAILY interval=2, idéntico a
    // "cada dos días", con título limpio y primera ocurrencia anclada a la captura.
    @Test fun cadaOtroDiaParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Tomar pastilla cada otro día", now, zone)
        assertEquals("Tomar pastilla", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaOtrosDiasPluralParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Tomar pastilla cada otros días", now, zone)
        assertEquals("Tomar pastilla", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unDiaSiYOtroNoParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Medicamento un día sí y otro no", now, zone)
        assertEquals("Medicamento", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unDiaSiYOtroNoSinAcentosParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Medicamento un dia si y otro no", now, zone)
        assertEquals("Medicamento", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Falsos positivos: "otro día" sin "cada" es fecha (pasada/relativa), NO recurrencia.
    // La guarda `\bcada\s+otros?` evita que "el otro día" o "otro día hablo con juan"
    // se malinterpreten como cadencia cada-dos-días.
    @Test fun elOtroDiaNoEsRecurrencia() {
        val result = NaturalTaskParser.parse("Revisar el otro día el informe", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }

    @Test fun otroDiaSinCadaNoEsRecurrencia() {
        val result = NaturalTaskParser.parse("Otro día hablo con Juan", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }

    // --- "días alternos" / "días alternativos" / "día por medio" = cada dos días ---
    // Giros idiomáticos sin cantidad de "cada 2 días" (semánticamente idénticos a
    // "cada otro día"/"un día sí y otro no"). Antes caían a NONE → rutina olvidada (P1).
    // Ahora DAILY interval=2, título limpio y primera ocurrencia anclada a la captura.
    @Test fun diasAlternosParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Gimnasio días alternos", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diasAlternativosParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Toma días alternativos", now, zone)
        assertEquals("Toma", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun diaPorMedioParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Gimnasio día por medio", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unDiaPorMedioParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Riego un día por medio", now, zone)
        assertEquals("Riego", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // --- "semana por medio" / "mes por medio" = cada 2 semanas / cada 2 meses ---
    // Giros idiomáticos LATAM sin cantidad numérica, simétricos de "día por medio"
    // (DAILY/2). Antes caían a NONE + dueAt=null → rutina olvidada (P1 evitar olvidos).
    // "semana por medio" = WEEKLY interval=2 (idéntico a "cada 2 semanas");
    // "mes por medio" = MONTHLY interval=2 (idéntico a "cada 2 meses").
    // Las formas aisladas (semana/una semana/mes/un mes por medio) y con "cada" se
    // cubren en los tests c.348 (c33d25b) más abajo; aquí los COMBOS con día/días.
    @Test fun unMesPorMedioParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Auditoría un mes por medio", now, zone)
        assertEquals("Auditoría", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Forma plural "semanas por medio" (giro habitual en LATAM): misma semántica que
    // el singular "semana por medio" (WEEKLY/2), simétrica de "días por medio".
    @Test fun semanasPorMedioPluralParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Clase semanas por medio", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Combo "mes por medio + día del mes": MONTHLY/2 anclado al día N (igual que
    // "cada 2 meses el 15"). Antes perdía el interval o no anclaba el día.
    @Test fun mesPorMedioConDiaDelMesParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Clínica mes por medio el 15", now, zone)
        assertEquals("Clínica", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Combo "semana por medio + 2 días": interval=2 y ambos días conservados.
    @Test fun semanaPorMedioMultiDiaParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Fútbol semana por medio lunes y jueves", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1,4", result.recurrenceDays)
    }

    // No-regresión: "cada 2 semanas"/"cada 2 meses" (forma cardinal) sigue igual.
    @Test fun cada2SemanasStaysWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reunión cada 2 semanas", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
    }

    // No-regresión: el "por medio" suelto sin unidad temporal NO se roba como recurrencia
    // (frases como "dejar por medio" no son cadencia). Se exige semana/mes justo antes.
    @Test fun porMedioSinUnidadTemporalNoEsRecurrencia() {
        val result = NaturalTaskParser.parse("Comentar lo del proyecto por medio", now, zone)
        assertNotEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertNotEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    // Combo "semana por medio + días de semana": antes perdía el interval (WEEKLY/1 en
    // vez de /2) y dejaba "semana por medio" pegado al título. Ahora igual que
    // "cada 2 semanas los sábados" (interval=2, días=[6], título limpio).
    @Test fun semanaPorMedioConDiasParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Fútbol semana por medio los sábados", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("6", result.recurrenceDays)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cada2SemanasLosSabadosStaysWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Fútbol cada 2 semanas los sábados", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("6", result.recurrenceDays)
        assertEquals("Fútbol", result.title)
    }

    // --- "cada tercer/cuarto/quinto/sexto día" = cada N días (ordinal) ---
    // Equivalente exacto de "cada 3/4/5/6 días" (intervalPattern sólo admite cardinales).
    // "cada tercer día"=cada 3 días, "cada cuarto día"=cada 4, etc. Antes caían a NONE
    // → rutina olvidada (P1). El prefijo "cada" acota a cadencia: sin él es posición, no hábito.
    @Test fun cadaTercerDiaParsesDailyInterval3() {
        val result = NaturalTaskParser.parse("Medicina cada tercer día", now, zone)
        assertEquals("Medicina", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaCuartoDiaParsesDailyInterval4() {
        val result = NaturalTaskParser.parse("Tratamiento cada cuarto día", now, zone)
        assertEquals("Tratamiento", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(4, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaQuintoDiaParsesDailyInterval5() {
        val result = NaturalTaskParser.parse("Riego cada quinto día", now, zone)
        assertEquals("Riego", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(5, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaSextoDiaParsesDailyInterval6() {
        val result = NaturalTaskParser.parse("Descanso cada sexto día", now, zone)
        assertEquals("Descanso", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // Falso positivo: ordinal SIN "cada" señala una posición (un día concreto), no cadencia.
    @Test fun tercerDiaSinCadaNoEsRecurrencia() {
        val result = NaturalTaskParser.parse("Resumen el tercer día del curso", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }

    // Falso positivo: "día alterno" SINGULAR es ambiguo (un día alternativo), no hábito.
    // Sólo el plural "días alternos" señala cadencia.
    @Test fun diaAlternoSingularNoEsRecurrencia() {
        val result = NaturalTaskParser.parse("Reunión el día alterno", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
    }

    // --- "semana por medio" / "mes por medio" = cada 2 semanas / cada 2 meses ---
    // Familia simétrica de "día por medio" (c.332): el giro "X por medio" significa
    // intercalar cada dos períodos. "semana por medio" = cada 2 semanas, "mes por medio"
    // = cada 2 meses. Antes caían a NONE → la rutina nacía sin cadencia ni fecha
    // (recordatorio jamás disparaba, invisible en What Now: P1 rutina olvidada).
    // Ahora WEEKLY/MONTHLY interval=2, título limpio y primera ocurrencia anclada.
    @Test fun semanaPorMedioParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Reunión semana por medio", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaSemanaPorMedioParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Pago cada semana por medio", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaSemanaPorMedioParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Pago una semana por medio", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun mesPorMedioParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Factura mes por medio", now, zone)
        assertEquals("Factura", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cadaMesPorMedioParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Renta cada mes por medio", now, zone)
        assertEquals("Renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    // No-regresión: "cada semana" (SIN "por medio") sigue siendo WEEKLY interval=1.
    // El patrón "X por medio" exige la cola "por medio", así la forma canónica de
    // cadencia semanal simple no debe verse afectada.
    @Test fun cadaSemanaSinPorMedioSigueWeeklyInterval1() {
        val result = NaturalTaskParser.parse("Reunión cada semana", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
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

    // --- Día ESCRITO en fecha con mes nombre: "el veinte de septiembre", "el quince de
    // agosto", "el primero de enero". Antes monthNamePattern sólo aceptaba \d{1,2} para el
    // día → estos compromisos caían a dueAt=null y la frase entera quedaba como título
    // (vencimiento invisible en planificador/What Now → olvidado). Las horas, duraciones,
    // recurrencias y recordatorios SÍ aceptaban números escritos; el día de una fecha con
    // mes nombre era la única asimetría. Se reutiliza writtenNumberGroup + parseWrittenNumber. ---

    @Test fun mesNombreDiaEscritoVeinteDeSeptiembre() {
        val result = NaturalTaskParser.parse("Pagar el veinte de septiembre", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 9, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreDiaEscritoQuinceDeAgosto() {
        val result = NaturalTaskParser.parse("Cita el quince de agosto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreDiaEscritoPrimeroDeEneroRodaAno() {
        // "el primero de enero" (1 de enero) ya pasó este año (hoy 29-jul-2026) → rueda a 2027.
        val result = NaturalTaskParser.parse("Renovar el primero de enero", now, zone)
        assertEquals("Renovar", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreDiaEscritoTreintaYUnoDeMarzoRodaAno() {
        // "el treinta y uno de marzo" → 31 de marzo 2026 ya pasó → rueda a 2027-03-31.
        val result = NaturalTaskParser.parse("Vence el treinta y uno de marzo", now, zone)
        assertEquals("Vence", result.title)
        assertEquals(LocalDate.of(2027, 3, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesNombreDiaEscritoTreintaDeFebreroClampaYRoda() {
        // "el treinta de febrero": día imposible → clamp a 28 (2026 no bisiesto) y, al ser
        // pasado, rueda a 2027-02-28. Honesto: respeta el mes nombrado, normaliza el día.
        val result = NaturalTaskParser.parse("el treinta de febrero", now, zone)
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "día N" sin artículo "el" (forma coloquial: "pagar día 15", "reunión día 3") ---
    // Antes dayOfMonthPattern exigía "el"; "día N" caía a dueAt=null. Si la frase
    // traía hora ("entregar día 5 a las 18"), ésta se aplicaba a HOY → fecha
    // silenciosamente errónea (P1: integridad de datos). Ahora "día N" se admite.
    @Test fun diaNWithoutArticleResolves() {
        val result = NaturalTaskParser.parse("Pagar día 15", now, zone)
        assertEquals("Pagar", result.title)
        // now=29-jul; el 15 ya pasó en julio → 15-ago.
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaNWithoutArticleExplicitHour() {
        // Caso P1: antes la hora se aplicaba a hoy (29-jul) en lugar del día 5.
        val result = NaturalTaskParser.parse("Entregar día 5 a las 18", now, zone)
        assertEquals("Entregar", result.title)
        // now=29-jul; el 5 ya pasó en julio → 5-ago.
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun diaNWithoutArticleNearTodayRollsForward() {
        // "día 3": el 3 ya pasó en julio (now=29-jul) → 3-ago.
        val result = NaturalTaskParser.parse("Reunión día 3", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Falso positivo evitado: "día 15 del libro" no es fecha (referencia no temporal).
    // El lookahead negativo "del? <palabra>" bloquea la forma sin artículo, donde
    // "del" (de+el, sin espacio) es más propensa a no-temporales que "el día N".
    @Test fun diaNOfBookIsNotDate() {
        val result = NaturalTaskParser.parse("el capítulo día 15 del libro", now, zone)
        assertEquals("el capítulo día 15 del libro", result.title)
        assertNull(result.dueAt)
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

    // --- "día N del mes que viene" sin artículo "el" (P1 integridad de fecha) ---
    // Antes nextMonthDayPattern exigía "el"; "día N del mes que viene" caía a
    // nextPeriodPattern (genérico +30d), fechando p. ej. al 28-ago en vez del 15-ago
    // y dejando residuo en el título. Caso de uso real muy frecuente.
    @Test fun diaNNextMonthWithoutArticleResolves() {
        val result = NaturalTaskParser.parse("Pagar día 15 del mes que viene", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaNNextMonthWithoutArticleRolled() {
        val result = NaturalTaskParser.parse("Reunión día 5 del próximo mes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // ── Past-safe: hora canónica inequívoca en el pasado se rueda al día siguiente ──
    // now = 2026-07-29 12:00 (mediodía). La medianoche de hoy (00:00) ya pasó 12h →
    // "cena a la medianoche" debe caer en la madrugada de MAÑANA (2026-07-30 00:00), no
    // en hoy 00:00 (pasado), donde el recordatorio (dueAt - offset) también quedaría en
    // el pasado y ReminderSync.triggers lo descartaría (trigger <= now → null) → cita
    // olvidada. El mediodía (12:00) == now → se queda hoy (no es pasado).

    @Test fun medianochePasadaSeRuedaAMadrugadaDeManana() {
        val result = NaturalTaskParser.parse("Cena a la medianoche", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun doceDeLaNochePasadaSeRuedaAMadrugadaDeManana() {
        val result = NaturalTaskParser.parse("Fiesta a las 12 de la noche", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediodiaNoSeRuedaSiEsAhora() {
        // 12:00 == now → no es pasado, se queda hoy al mediodía.
        val result = NaturalTaskParser.parse("Almuerzo al mediodía", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.NOON, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun horaSueltaPasadaNoSeRuedaAmbiguedadAmPm() {
        // "a las 9" (sin meridiem) capturada al mediodía cae en hoy 09:00 (pasado) y NO se
        // rueda: la hora es ambigua (AM/PM) y el día también (registrar pasado vs. mañana).
        // Se preserva la semántica existente en lugar de adivinar.
        val result = NaturalTaskParser.parse("Reunión a las 9", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun medianocheConFechaExplicitaMañanaNoSeRueda() {
        // "mañana a la medianoche" → fecha explícita mañana (2026-07-31 00:00), no se toca.
        val result = NaturalTaskParser.parse("Entregar mañana a la medianoche", now, zone)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt, zone))
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

    // --- "antes del N": plazo como día del mes suelto (P1, ciclo 147) ---
    // "entregar tesis antes del 30" expresaba un vencimiento (deadline) usando el día
    // del mes suelto, SIN nombre de mes. Antes el "30" no casaba dayOfMonthPattern (que
    // exige "el"/"día") ni monthNamePattern (que exige "de <mes>"): el conector "antes
    // del" se borraba pero el "30" sobrevivía como residuo del título Y la fecha se
    // perdía -> dueAt=null -> vencimiento olvidado (sin recordatorio, invisible en What
    // Now/planificador). Ahora "antes del N" se ancla al día N (canónica 09:00).
    @Test fun antesDelNDiaSueltoResuelvePlazoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("entregar tesis antes del 30", now, zone)
        assertEquals("entregar tesis", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Día ya pasado en el mes en curso -> rueda al próximo mes (como "el 15" suelto).
    @Test fun antesDelNDiaSueltoRuedaAlProximoMesSiYaPaso() {
        val result = NaturalTaskParser.parse("pagar antes del 15", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Variante sin "l": "antes de 15" (coloidal, articulo elidido). Mismo plazo.
    @Test fun antesDeNSinLResuelvePlazoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pagar antes de 15", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Regresión: "antes del 30 de agosto" (con mes) NO debe interceptarlo
    // beforeDeadlineDayPattern (lo resuelve monthNameDate) y el título queda limpio.
    @Test fun antesDelNDeMesPrevaleceMonthNameDate() {
        val result = NaturalTaskParser.parse("entregar antes del 30 de agosto", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Guard anti-falso-positivo: "antes del proyecto 5" -> el "5" NO es inmediatamente
    // posterior a "antes del" (hay "proyecto" entre medias), así que NO se inventa una
    // fecha; "antes del" se limpia del título (comportamiento preexistente) y el "5"
    // queda como contenido (no es plazo).
    @Test fun antesDelPalabraNNoEsFalsoPlazo() {
        val result = NaturalTaskParser.parse("reunion antes del proyecto 5", now, zone)
        assertNull(result.dueAt)
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

    @Test fun aEsoDeLasCincoResuelveHoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar a eso de las 5", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLasDiezDeLaMananaResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("enviar a eso de las 10 de la mañana", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaUnaDeLaTardeResuelve13hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llama a eso de la una de la tarde", now, zone)
        assertEquals("llama", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun sobreLasTresDeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión sobre las 3 de la tarde", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun sobreLaUnaDelMediodiaResuelve13hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pasa sobre la una del mediodía", now, zone)
        assertEquals("pasa", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haciaLasCuatroDeLaTardeResuelve16hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión hacia las 4 de la tarde", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cercaDeLasDiezDeLaMananaResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llego cerca de las 10 de la mañana", now, zone)
        assertEquals("llego", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alrededorDeLasNueveDeLaNocheResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("cobro alrededor de las 9 de la noche", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun sobreLasVentasNoEsHora() {
        // "sobre" como preposición de tema: no debe agendarse ni mutilar el título.
        val result = NaturalTaskParser.parse("reunión sobre las ventas de la tienda", now, zone)
        assertEquals("reunión sobre las ventas de la tienda", result.title)
        assertNull(result.dueAt)
    }

    @Test fun sobreLasTresCajasNoEsHora() {
        // "sobre las 3 cajas" es una cantidad, no una cita: no debe agendarse.
        val result = NaturalTaskParser.parse("comprar sobre las 3 cajas de leche", now, zone)
        assertEquals("comprar sobre las 3 cajas de leche", result.title)
        assertNull(result.dueAt)
    }

    // --- Sufijo "h" suelto (forma europea "10h"/"10 h") con marcadores aproximados ---
    // Antes approximateTimePatterns omitía `|h` (asimetría con timePatterns y
    // paraTimeIntroPattern): "hacia las 10h"/"sobre las 4h" caían a dueAt=null y dejaban
    // "hacia las"/"sobre las" como residuo (cita perdida + título degradado, P1).
    @Test fun haciaLasDiezHResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión hacia las 10h", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haciaLasDiezHEspaciadoResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión hacia las 10 h", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun sobreLasCuatroHResuelve4hYLimpiaTitulo() {
        // "4h" es formato 24h puro (sin meridiem): 04:00, no 16:00.
        val result = NaturalTaskParser.parse("reunión sobre las 4h", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cercaDeLasDiezHResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llego cerca de las 10h", now, zone)
        assertEquals("llego", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun alrededorDeLasNueveHsResuelve9hYLimpiaTitulo() {
        // "9hs" es formato 24h puro (sin meridiem): 09:00, no 21:00.
        val result = NaturalTaskParser.parse("cobro alrededor de las 9hs", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haciaLasDiezHorasResuelve10hYLimpiaTitulo() {
        // Regresión: "hacia las 10 horas" ya funcionaba; se re-verifica tras añadir `|h\b`.
        val result = NaturalTaskParser.parse("reunión hacia las 10 horas", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haciaLasDiezHabitacionesNoEsHora() {
        // El `\b` tras la "h" suelta evita falsificar "10 habitaciones" como cita:
        // antes (con `horas?` sin `\b`) "hacia las 10 horario" se agendaba a 10:00.
        val result = NaturalTaskParser.parse("reunión hacia las 10 habitaciones", now, zone)
        assertEquals("reunión hacia las 10 habitaciones", result.title)
        assertNull(result.dueAt)
    }

    @Test fun haciaLasDiezHorarioNoEsHora() {
        // "horario" empieza por "hora": sin `\b`, `horas?` casaba su prefijo y agendaba.
        val result = NaturalTaskParser.parse("reunión hacia las 10 horario", now, zone)
        assertEquals("reunión hacia las 10 horario", result.title)
        assertNull(result.dueAt)
    }

    // --- Meridiem ADYACENTE sin espacio ("10am"/"4pm") con marcadores aproximados ---
    // Antes el lookahead exigía `\s+` antes del meridiem/parte del día, mientras
    // `timePatterns` (l.~823) usaba `\s*`: "hacia las 10am"/"sobre las 4pm" (sin espacio,
    // forma dominante en móvil) NO reescribían el marcador → `timePatterns` resolvía la
    // hora PERO dejaba "hacia las"/"sobre las" como residuo en el título (cita bien
    // fechada, título mutilado, P1). Ahora `\s*` = simetría con `timePatterns`.
    @Test fun haciaLasDiezAmAdyacenteResuelve10hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión hacia las 10am", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun sobreLasCuatroPmAdyacenteResuelve16hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión sobre las 4pm", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haciaLasDiezAmEspaciadoSigueResolviendo10hYLimpiaTitulo() {
        // No-regresión: la forma CON espacio "10 am" (que `\s+` cubría) sigue funcionando.
        val result = NaturalTaskParser.parse("reunión hacia las 10 am", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun informeSobreElClienteNoEsHora() {
        val result = NaturalTaskParser.parse("informe sobre el cliente del jueves", now, zone)
        assertEquals("informe sobre el cliente", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Ordinales numéricos en fechas: "1ro de septiembre", "2do de cada mes", "1º del mes"
    // Antes el sufijo ("ro"/"º") rompía los patrones de fecha (\d+espacio) y la fecha se
    // perdía o el título quedaba mutilado ("pago º de septiembre"). Ahora se normaliza a su
    // dígito base SOLO en contexto de fecha (" de "/" del ") para reutilizar todo el flujo.
    @Test fun ordinalNumericSuffixParsesAsDate() {
        val result = NaturalTaskParser.parse("pago el 1ro de septiembre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSymbolParsesAsDate() {
        val result = NaturalTaskParser.parse("pago el 1º de septiembre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSuffixMonthlyRecurrence() {
        val result = NaturalTaskParser.parse("renta el 2do de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSuffixDelMes() {
        val result = NaturalTaskParser.parse("cita el 5to del mes a las 10", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Ordinales de contenido (no fecha) NO deben agendarse: no hay " de [mes]" tras el
    // sufijo, así que la normalización no aplica y no se genera una falsa fecha.
    @Test fun ordinalContentWordNotScheduled() {
        val result = NaturalTaskParser.parse("ver el 3er capítulo", now, zone)
        assertEquals("ver el 3er capítulo", result.title)
        assertNull(result.dueAt)
    }

    @Test fun ordinalContentPisoNotScheduled() {
        val result = NaturalTaskParser.parse("comprar 2do piso del edificio", now, zone)
        assertEquals("comprar 2do piso del edificio", result.title)
        assertNull(result.dueAt)
    }

    // Variante "ero" del ordinal ("1ero"/"3ero"/"21ero de septiembre"): tan cotidiana en
    // LATAM como "1ro", pero el sufijo "ero" rompía los patrones de fecha → dueAt=null
    // (vencimiento olvidado) y la frase entera sobrevivía como título basura. La
    // normalización a dígito base en contexto de fecha (" de "/" del ") resuelve ambos.
    @Test fun ordinalEroSuffixParsesAsDate() {
        val result = NaturalTaskParser.parse("pago el 1ero de septiembre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalEroSuffixTercero() {
        val result = NaturalTaskParser.parse("cita el 3ero de octubre", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 10, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalEroSuffixDelMesRecurrence() {
        val result = NaturalTaskParser.parse("renta el 2ero de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalEroContentNotScheduled() {
        val result = NaturalTaskParser.parse("ver el 3ero capítulo", now, zone)
        assertEquals("ver el 3ero capítulo", result.title)
        assertNull(result.dueAt)
    }

    // BUG: "el 25/12" (fecha numérica DD/MM con artículo) se agendaba al 25 de AGOSTO
    // (día-suelto del mes) en vez del 25 de DICIEMBRE. dayOfMonthPattern ("el 25")
    // casaba ANTES que numericDatePattern ("25/12") → el día se anclaba a este mes y el
    // "/12" se ignoraba → vencimiento en mes equivocado (tarea olvidada en su fecha real).
    @Test fun numericDateWithArticleSlash_notShadowedByDayOfMonth() {
        val result = NaturalTaskParser.parse("pago el 25/12", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 12, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithArticleSlashShortMonth_notShadowedByDayOfMonth() {
        val result = NaturalTaskParser.parse("cita el 15/9", now, zone)
        assertEquals("cita", result.title)
        // 15/9 (sep) está en el futuro de este año (hoy 29/7) → 2026-09-15
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun numericDateWithArticleDash_notShadowedByDayOfMonth() {
        val result = NaturalTaskParser.parse("entregar el 3-1", now, zone)
        assertEquals("entregar", result.title)
        // 3-1 (3 enero) ya pasó → próximo año 2027-01-03
        assertEquals(LocalDate.of(2027, 1, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Guard: el día-suelto del mes ("el 15" sin "/") sigue funcionando.
    @Test fun dayOfMonthStandaloneStillWorks() {
        val result = NaturalTaskParser.parse("reunión el 15", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 29/2" en año no bisiesto (2026): el usuario se refiere al PRÓXIMO 29 de febrero
    // real (2028, año bisiesto). Antes LocalDate.of lanzaba -> dueAt=null -> tarea sin
    // recordatorio (vencimiento olvidado). Paridad con parseMonthNameDate ("el 29 de
    // febrero" sin año), que SÍ rollaba al próximo bisiesto. Asimetría: la forma
    // numérica "29/2" se descartaba, la nominal "29 de febrero" no.
    @Test fun numericLeapDayNoYear_rollsToNextLeapYear() {
        val result = NaturalTaskParser.parse("pago el 29/2", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "29/2/2026" con año explícito NO bisiesto: día imposible -> se ajusta al último
    // día válido del mes (28/2/2026), consistente con parseMonthNameDate ("31 de abril"
    // -> 30/4). Antes lanzaba -> dueAt=null (fecha escrita explícitamente, descartada).
    @Test fun numericImpossibleDayWithYear_clampsToLastValidDay() {
        val result = NaturalTaskParser.parse("cita el 31/4/2026", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 4, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 15 del 9" = día 15 del mes 9 (septiembre). Forma numérica LATAM cotidiana para
    // agendar ("pago el 15 del 9", "cita el 3 del 1"). Antes dayOfMonthPattern casaba con
    // "el 15" → 15 de agosto (mes en curso, equivocado) y "del 9" sobrevivía como residuo
    // del título → vencimiento en mes equivocado + título sucio (P1). El usuario escribe
    // un vencimiento explícito y la fecha cae en el mes incorrecto → recordatorio dispara
    // en la fecha equivocada, tarea invisible en la fecha real.
    @Test fun dayOfMonthNumericMonth_Del9_ParsesAsSeptember() {
        val result = NaturalTaskParser.parse("entregar el 15 del 9", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthNumericMonth_Del12_ParsesAsDecember() {
        val result = NaturalTaskParser.parse("pago el 15 del 12", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 12, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 3 del 1" (3 de enero) en julio 2026 → enero ya pasó → roll al próximo año
    // (2027-01-03), consistente con numericDatePattern y parseMonthNameDate.
    @Test fun dayOfMonthNumericMonth_PastMonth_RollsToNextYear() {
        val result = NaturalTaskParser.parse("cita el 3 del 1", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2027, 1, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 15 del 9 de 2026" = año explícito: no hay roll (la fecha es futura).
    @Test fun dayOfMonthNumericMonth_WithExplicitYear() {
        val result = NaturalTaskParser.parse("pago el 15 del 9 de 2026", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 31 del 4" = 31 de abril (día imposible) → clamp a 30/4, y como 2026-04-30 ya
    // pasó (now=2026-07-29) → roll al próximo año (2027-04-30). Verifica que la
    // normalización reutiliza la paridad numérica/nominal de c.146 (clamp) Y el roll de
    // fecha pasada de numericDatePattern.
    @Test fun dayOfMonthNumericMonth_ImpossibleDay_ClampsAndRollsToNextYear() {
        val result = NaturalTaskParser.parse("cita el 31 del 4", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2027, 4, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "antes del 15 del 9" = vencimiento el 15 de septiembre. Misma familia que "el 15
    // del 9" pero con prefijo "antes del". Antes beforeDeadlineDayPattern robaba "antes
    // del 15" → 15 de agosto (mes en curso, equivocado) y "del 9" sobrevivía como residuo
    // del título → vencimiento en mes equivocado + título sucio (P1). El plazo se ancla
    // al día N (canónica 09:00), consistente con c.147 "antes del N" suelto.
    @Test fun dayOfMonthNumericMonth_AntesDel_ParsesAsSeptember() {
        val result = NaturalTaskParser.parse("pago antes del 15 del 9", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthNumericMonth_AntesDe_ParsesAsDecember() {
        val result = NaturalTaskParser.parse("renta antes de 15 de 12", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2026, 12, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "antes del 3 del 1" (3 de enero) en julio 2026 → enero pasado → roll 2027-01-03.
    @Test fun dayOfMonthNumericMonth_AntesDel_PastMonth_RollsToNextYear() {
        val result = NaturalTaskParser.parse("cita antes del 3 del 1", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2027, 1, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "de aquí al 15 del 9"/"de acá al 15 del 9" = día 15 del mes 9 (septiembre). El conector
    // direccional-temporal "de aquí al"/"de acá al" se reconocía para cantidades ("de aquí a
    // 3 días", c.50) y fechas con artículo ("de aquí al 15" suelto, c.134), pero NO con mes
    // numérico: el reescritor de c.134 ("de aquí al"→"el") corre DESPUÉS de la normalización
    // "el N del M"→"N/M" (c.148), así que "al 15" caía a dayOfMonthPattern→mes en curso
    // (agosto, equivocado) y "del 9" sobrevivía como residuo → vencimiento en mes
    // equivocado + título sucio (P1). El prefijo se admite directamente en
    // dayOfMonthNumericMonthPattern → "N/M" reutilizando TODO el flujo numericDatePattern.
    @Test fun dayOfMonthNumericMonth_DeAquiAl_ParsesAsSeptember() {
        val result = NaturalTaskParser.parse("pago de aquí al 15 del 9", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthNumericMonth_DeAcaAl_ParsesAsSeptember() {
        val result = NaturalTaskParser.parse("envío de acá al 15 del 9", now, zone)
        assertEquals("envío", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "de acá al 3 del 1" (3 de enero) en julio 2026 → enero pasado → roll 2027-01-03.
    // Verifica que el prefijo direccional también hereda el roll de año de numericDatePattern.
    @Test fun dayOfMonthNumericMonth_DeAcaAl_PastMonth_RollsToNextYear() {
        val result = NaturalTaskParser.parse("cita de acá al 3 del 1", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2027, 1, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "de aquí al 31 del 4" = 31 de abril (imposible) → clamp 30/4, y como 2026-04-30 ya
    // pasó → roll 2027-04-30. Verifica paridad con c.146 (clamp) y c.148 (roll) bajo el
    // prefijo direccional.
    @Test fun dayOfMonthNumericMonth_DeAquiAl_ImpossibleDay_ClampsAndRolls() {
        val result = NaturalTaskParser.parse("renta de aquí al 31 del 4", now, zone)
        assertEquals("renta", result.title)
        assertEquals(LocalDate.of(2027, 4, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthDeEsteMes() {
        val result = NaturalTaskParser.parse("reunión el 15 de este mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthDeEsteMesWithDiaWord() {
        val result = NaturalTaskParser.parse("reunión el día 15 de este mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "el 15 de este proyecto" NO es fecha: "de este" restringido a "de este mes" evita el
    // falso positivo que agendaría una cita inexistente con texto de proyecto.
    @Test fun deEsteMesNotMatchingDeEsteProyecto() {
        val result = NaturalTaskParser.parse("reunión el 15 de este proyecto", now, zone)
        assertEquals("reunión el 15 de este proyecto", result.title)
        assertNull(result.dueAt)
    }

    // Sinónimos de "mes en curso" como fecha única (día N del mes actual): "del presente
    // mes", "del mes actual", "de este mismo mes". Antes monthlyDayPattern (que corre
    // ANTES en parseRecurrence) robaba "N del mes" como recurrencia falsa y dejaba el
    // calificador ("actual"/"presente"/"mismo") como residuo en el título, además de
    // perder el carácter de compromiso único (P1). El lookahead negativo en
    // monthlyDayPattern lo rechaza y dayOfMonthPattern lo resuelve como fecha.
    @Test fun elNDelMesActualEsSinonimoDeEsteMes() {
        val result = NaturalTaskParser.parse("Envío el 31 del mes actual", now, zone)
        assertEquals("Envío", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun elNDelPresenteMesEsSinonimoDeEsteMes() {
        val result = NaturalTaskParser.parse("Cobro el 15 del presente mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun elNDeEsteMismoMesEsSinonimoDeEsteMes() {
        val result = NaturalTaskParser.parse("Pago el 5 de este mismo mes", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    // --- "siguiente"/"posterior" como sufijo de día de la semana (ciclo 148) ---
    // "el martes siguiente"/"el lunes posterior" son sinónimos de "que viene"/"próximo"
    // (próxima ocurrencia estricta) pero NO se reconocían: el modificador quedaba como
    // residuo del título ("reunión siguiente") y, peor, NO forzaba +7 (un martes dicho
    // en martes caía en HOY en vez de la semana próxima). P2: título sucio + semántica
    // débil. "siguiente" es ambiguo solo tras día genérico ("el día siguiente"); tras un
    // día de la semana nombrado ("el martes siguiente") significa sin ambigüedad la
    // próxima ocurrencia, igual que "el martes que viene".

    @Test fun weekdaySiguienteLimpiaTituloYFuerzaProximaOcurrencia() {
        // Miércoles 29-jul; "el martes siguiente" = próximo martes 04-ago (no hoy).
        val result = NaturalTaskParser.parse("Reunión el martes siguiente", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 4), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun weekdaySiguienteConHoraLimpiaTodo() {
        val result = NaturalTaskParser.parse("Reunión el martes siguiente a las 3", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 4), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun weekdaySiguienteHoyEsEseDiaFuerzaProximaSemana() {
        // Martes 04-ago 8:00; "el martes siguiente" dicho en martes = próxima semana.
        val martesNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 4), LocalTime.of(8, 0), zone)
        val r = NaturalTaskParser.parse("Ir al dentista el martes siguiente", martesNow, zone)
        assertEquals(LocalDate.of(2026, 8, 11), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun weekdayPosteriorLimpiaTituloYFuerzaProximaOcurrencia() {
        // "el lunes posterior" = próximo lunes estricto (03-ago), sinónimo de "que viene".
        val result = NaturalTaskParser.parse("Pago el lunes posterior", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun weekdaySiguienteNoInterfiereConDiaSuelto() {
        // "el viernes" sin modificador sigue funcionando (dicho en miércoles = 31-jul).
        val result = NaturalTaskParser.parse("Entregar el viernes", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── "el día siguiente" = mañana relativa (sin weekday) ──
    // Forma cotidiana de agendar para mañana sin usar la palabra "mañana"
    // ("entregar el día siguiente", "reunión el día siguiente a las 18"). Antes caía a
    // dueAt=null + frase completa como título (vencimiento olvidado), o con hora se
    // agendaba a HOY (fecha equivocada). Se normaliza a "mañana" reutilizando todo el flujo.
    @Test fun elDiaSiguienteResuelveAMananaSinResiduo() {
        val result = NaturalTaskParser.parse("Entregar el día siguiente", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaSiguienteSinArticuloResuelveAManana() {
        val result = NaturalTaskParser.parse("Reunión día siguiente", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaSiguienteConHoraExplicitaEsMananaALaHora() {
        // P1: antes se agendaba a HOY 18:00 (fecha equivocada) + residuo "el día siguiente".
        val result = NaturalTaskParser.parse("Reunión el día siguiente a las 18", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elDiaSiguienteEscritoSinTildeFunciona() {
        val result = NaturalTaskParser.parse("Pagar dia siguiente", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elDiaSiguienteNoAtrapalosDiasDeSemanaNombrados() {
        // "el martes siguiente" sigue siendo el próximo martes (04-ago), NO mañana.
        // No debe normalizarse porque "día siguiente" exige "día" genérico, no weekday.
        val result = NaturalTaskParser.parse("Ir el martes siguiente", now, zone)
        assertEquals("Ir", result.title)
        assertEquals(LocalDate.of(2026, 8, 4), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // ── "el próximo N" = día N del mes que viene (forma corta sin "del mes") ──
    // Vencimientos/cobros anclados a un día concreto ("pago el próximo 15"). Antes caía
    // a dueAt=null: la palabra "próximo" rompía dayOfMonthPattern y nextPeriodPattern no
    // acepta día numérico → vencimiento olvidado (P2). "próximo N" = día N del mes
    // siguiente (consistente con "el N del mes que viene").
    @Test fun elProximoNDiaResuelveADiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Pago el próximo 15", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun proximoNSinArticuloResuelveAMesSiguiente() {
        val result = NaturalTaskParser.parse("Entrega próximo 20", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elProximoDiaNResuelveAMesSiguiente() {
        val result = NaturalTaskParser.parse("Cobro el próximo día 10", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 10), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elProximoNConHoraExplicitaCombinaFechaYHora() {
        val result = NaturalTaskParser.parse("Reunión el próximo 15 a las 10", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elProximoNDiaImposibleClampaAUltimoDiaDelMesSiguiente() {
        // now=2026-01-15 → mes siguiente = febrero 2026 (28 días). "próximo 31" → 28.
        val eneNow = DateRules.toEpochMillis(LocalDate.of(2026, 1, 15), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Vence el próximo 31", eneNow, zone)
        assertEquals("Vence", result.title)
        assertEquals(LocalDate.of(2026, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elProximoNNoInterfiereConNDelMesQueViene() {
        // "el 15 del mes que viene" sigue usando la forma completa (también → 15-ago),
        // sin doble-procesamiento ni residuo.
        val result = NaturalTaskParser.parse("Pago el 15 del mes que viene", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNProximoOrdenInversoResuelveAMesSiguiente() {
        // "el 15 próximo" (calificador DESPUÉS del día) = día 15 del mes siguiente.
        // Antes dayOfMonthPattern capturaba "el 15" como de ESTE mes (julio) y "próximo"
        // quedaba de residuo en el título (fecha equivocada + título degradado: P1).
        val result = NaturalTaskParser.parse("Pago el 15 próximo", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNProximoSinTildeOrdenInversoFunciona() {
        val result = NaturalTaskParser.parse("Cobro el 20 proximo", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNProximoDiaBajoResuelveAMesSiguienteSinResiduo() {
        // Día bajo (1) que ya pasó este mes: igualmente → mes siguiente + título limpio.
        val result = NaturalTaskParser.parse("Entrega el 1 próximo", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elNProximoNoInterfiereConProximoDiaDeSemana() {
        // "el próximo lunes" (forma directa + weekday) sigue resolviendo como próxima
        // ocurrencia estricta del weekday, sin que el patrón inverso lo robe.
        val result = NaturalTaskParser.parse("Reunión el próximo lunes", now, zone)
        assertEquals("Reunión", result.title)
        // 2026-07-29 es miércoles → próximo lunes = 2026-08-03.
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "el último <día> del mes" / "el último <día> de <mes>": último weekday del mes ---
    // Antes "el último viernes del mes" caía en previousWeekdayReversedPattern ("el
    // último viernes") → viernes ANTERIOR (2026-08-07), y "del mes" quedaba como
    // residuo en el título. Debe ser el ÚLTIMO viernes del mes (2026-08-28). El
    // usuario fija un vencimiento explícito y la tarea caía en fecha equivocada
    // (recordatorio el día erróneo, invisible en la fecha real). now = 2026-08-14
    // (viernes), zona America/Santiago.

    @Test fun ultimoViernesDelMesResuelveUltimoViernesDelMes() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Pago el último viernes del mes a las 10", agoNow, ZoneId.of("America/Santiago"))
        // Último viernes de agosto 2026 = 2026-08-28.
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDeMesNombradoResuelveUltimoViernesDeEseMes() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Cita el último viernes de septiembre", agoNow, ZoneId.of("America/Santiago"))
        // Último viernes de septiembre 2026 = 2026-09-25 (no 2026-08-07, no agosto).
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoLunesDeAgostoResuelve31DeAgosto() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Entrega el último lunes de agosto", agoNow, ZoneId.of("America/Santiago"))
        // Último lunes de agosto 2026 = 2026-08-31.
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoDomingoDelMesResuelveUltimoDomingo() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Retiro el último domingo del mes", agoNow, ZoneId.of("America/Santiago"))
        // Último domingo de agosto 2026 = 2026-08-30.
        assertEquals("Retiro", result.title)
        assertEquals(LocalDate.of(2026, 8, 30), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDelMesSinEspacioNoCasaComoUltimoDelMes() {
        // "últimoviernes del mes" (sin espacio entre último y el día) NO casa el patrón
        // de "último <día> del mes" (exige límite de palabra tras "último"). No debe
        // resolverse como el último viernes del mes (2026-08-28): guard anti-falso-positivo.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Revisar últimoviernes del mes", agoNow, ZoneId.of("America/Santiago"))
        assertNotEquals(LocalDate.of(2026, 8, 28),
            result.dueAt?.let { DateRules.toLocalDate(it, ZoneId.of("America/Santiago")) })
    }

    @Test fun ultimoViernesSinCalificadorSigueSiendoViernesAnterior() {
        // "el último viernes" (SIN "del mes"/"de <mes>") sigue resolviendo al viernes
        // ANTERIOR (no-regresión: previousWeekdayReversedPattern intacto).
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Enviar reporte el último viernes", agoNow, ZoneId.of("America/Santiago"))
        // viernes 2026-08-14 → último viernes = 2026-08-07 (semana anterior).
        assertEquals("Enviar reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 7), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    // --- Ordinales primer/segundo/tercer/cuarto: "el primer lunes de agosto", etc. ---
    // Extensión complementaria al fix "último ... del mes". Un weekday ordinal del mes es una
    // forma cotidiana de fijar vencimientos ("cobro el primer viernes del mes", "reunión el
    // segundo martes"). Sin soporte, "el primer lunes de agosto" caía a un día libre de la
    // semana y "de agosto" quedaba como residuo. now = 2026-08-14 (viernes), America/Santiago.

    @Test fun primerLunesDeAgostoResuelve3DeAgosto() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Cobro el primer lunes de agosto", agoNow, ZoneId.of("America/Santiago"))
        // Primer lunes de agosto 2026 = 2026-08-03.
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun primeroViernesDelMesResuelvePrimerViernes() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // c.316 — "el primero viernes del mes" (variante -o de "primer") SIN "cada":
        // antes quedaba como fecha ÚNICA vencida en pasado (07-ago, < now 14-ago) → rutina
        // mensual olvidada. Ahora se promueve a MONTHLY anclada al 1er viernes y la 1ª cita
        // avanza al próximo mes válido (04-sep). Sustituye el guard "vencida honesta" previo:
        // BACKLOG c.318 redefinió este caso como P1 (rutina periódica olvidada).
        val result = NaturalTaskParser.parse("Pago el primero viernes del mes", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 4), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun segundoMartesDeSeptiembreResuelve8DeSeptiembre() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Reunión el segundo martes de septiembre", agoNow, ZoneId.of("America/Santiago"))
        // Septiembre 2026: 1º=martes → segundo martes = 2026-09-08.
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 8), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun tercerViernesDelMesResuelve21DeAgosto() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // Tercer viernes de agosto 2026 = 21 (1er=7, 2º=14, 3er=21). Hoy es el 2º viernes.
        val result = NaturalTaskParser.parse("Entrega el tercer viernes del mes", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 8, 21), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun cuartoDomingoDelMesResuelve23DeAgosto() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // Cuarto domingo de agosto 2026 = 2026-08-23 (1er=2, 2º=9, 3er=16... no: ago 2026
        // domingos: 2,9,16,23,30 → cuarto = 23).
        val result = NaturalTaskParser.parse("Cita el cuarto domingo del mes", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ordinalConHoraCombinaFechaYHora() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // "el primer lunes de agosto a las 9" = 2026-08-03 09:00.
        val result = NaturalTaskParser.parse("Cobro el primer lunes de agosto a las 9", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    // --- "del mes que viene" / "del mes próximo" / "del mes entrante": mes siguiente ---
    @Test fun ultimoViernesDelMesQueVieneResuelveUltimoViernesSeptiembre() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Pago el último viernes del mes que viene", agoNow, ZoneId.of("America/Santiago"))
        // Último viernes de septiembre 2026 = 2026-09-25.
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    // --- c.183: extensión de "último <día> del mes". El fix c.180 cubría "del mes" y
    // "de <mes>", pero 4 formas frecuentes caían a viernes ANTERIOR y dejaban residuo
    // de título: "de este mes", "del próximo mes" y la redundante "del mes de <mes>".
    // Más el año explícito ("del 2027") y la conservación de la hora explícita. Todas
    // deben anclar al último <día> del mes correcto y no dejar basura en el título.
    // now = 2026-08-14 (viernes), America/Santiago. ---

    @Test fun ultimoViernesDeEsteMesResuelveMesEnCurso() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Pago el último viernes de este mes", agoNow, ZoneId.of("America/Santiago"))
        // viernes de agosto 2026: 7, 14, 21, 28 → último = 28 (no viernes anterior 07).
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDelProximoMesResuelveMesSiguiente() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Cierre el último viernes del próximo mes", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDelMesDeSeptiembreNoDejaResiduoNiConfundeMes() {
        // "del mes de septiembre" = mismo anclaje que "de septiembre"; el "del mes" es
        // redundante y NO debe dejar "septiembre"/"mes" en el título.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Pago el último viernes del mes de septiembre", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun primerLunesDelMesProximoResuelve7DeSeptiembre() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // "próximo" con tilde. Primer lunes de septiembre 2026 = 2026-09-07.
        val result = NaturalTaskParser.parse("Reunión el primer lunes del mes próximo", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun tercerViernesDelMesEntranteResuelve18DeSeptiembre() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        // Calificador caribeño "entrante". Tercer viernes septiembre 2026 = 18 (1er=4,2º=11,3er=18).
        val result = NaturalTaskParser.parse("Entrega el tercer viernes del mes entrante", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2026, 9, 18), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ordinalMesNombradoYaPasadoRuedaAlProximoAno() {
        // "el primer lunes de enero" dicho en agosto 2026 → enero ya pasó → enero 2027.
        // Primer lunes de enero 2027 = 2027-01-04.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Planificación el primer lunes de enero", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Planificación", result.title)
        assertEquals(LocalDate.of(2027, 1, 4), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ordinalNoEsDiaSemanaNoConsumeElTitulo() {
        // Guard anti-falso-positivo: "el primer informe del mes" (informe ≠ día de la semana)
        // NO casa el patrón ordinal → "del mes" queda como frase, no se mutila el título.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Revisar el primer informe del mes", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Revisar el primer informe del mes", result.title)
    }

    // --- Ordinal + weekday SUELTO sin calificador de mes (c.320: título corrupto) ---
    // "reunión el primer lunes" (sin "del mes"/"de cada mes") no casa el patrón ordinal-mensual
    // ni previousWeekdayReversed. Antes weekdayPattern capturaba SÓLO "lunes" y dejaba "el primer"
    // como residuo pegado al título ("reunión el primer" = título corrupto, P2). El ordinal de
    // semana sin mes es semánticamente inválido, así que se degrada a "el lunes" (= próximo lunes)
    // y el título queda limpio. now = 2026-08-16 (domingo) → próximo lunes = 2026-08-17.
    @Test fun ordinalSueltoPrimerLunesLimpiaTituloYResuelveProximoLunes() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el primer lunes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 17), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun ordinalSueltoSegundoMartesLimpiaTitulo() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el segundo martes", now, zone)
        assertEquals("reunión", result.title)
        // próximo martes desde dom 16 = 2026-08-18.
        assertEquals(LocalDate.of(2026, 8, 18), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoTercerJuevesLimpiaTitulo() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el tercer jueves", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoCuartoSabadoLimpiaTitulo() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el cuarto sábado", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoSinElInicialLimpiaTitulo() {
        // "reunión primer lunes" (sin "el"): el ordinal queda como residuo igual.
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión primer lunes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 17), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoConHoraConservaHora() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el primer lunes a las 5", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 17), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ordinalSueltoConContenidoMezcladoLimpiaTitulo() {
        // "entregar el segundo viernes informe": antes weekday consumía "viernes" pero
        // "el segundo" quedaba pegado ANTES de "informe" → "entregar el segundo informe".
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("entregar el segundo viernes informe", now, zone)
        assertEquals("entregar informe", result.title)
        // próximo viernes desde dom 16 = 2026-08-21.
        assertEquals(LocalDate.of(2026, 8, 21), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoUltimoViernesSigueSiendoFechaPasada() {
        // Guard anti-regresión: "el último viernes" SÍ es fecha pasada válida
        // (previousWeekdayReversed), NO se degrada a próximo viernes.
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 16), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el último viernes", now, zone)
        assertEquals("reunión", result.title)
        // viernes anterior al dom 16 = 2026-08-14.
        assertEquals(LocalDate.of(2026, 8, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalSueltoNoRompeCalificadorDelMes() {
        // "el primer lunes del mes" (genérico, sin "cada"/mes nombrado/"que viene"):
        // originalmente c.322-paralelo lo trataba como fecha única vencida (due=08-03 PASADO,
        // recur=NONE) como "guard anti-regresión". c.318 redefinió eso como el BUG P1
        // (rutina mensual olvidada — asimetría con "el 1 del mes" que SÍ promueve a MONTHLY).
        // c.323 lo PROMUEVE a MONTHLY y avanza al próximo 1er lunes válido (nunca pasado).
        // now = 2026-08-14 (jueves) → 1er lunes ago = 03 (pasado) → rueda a 1er lunes sept = 07.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro el primer lunes del mes", agoNow, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun ultimoViernesDeJunioPasadoRuedaAlAnioSiguiente() {
        // junio ya terminó (now = agosto) → rueda a junio 2027.
        // viernes de junio 2027: 4, 11, 18, 25 → último = 25.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Auditoría el último viernes de junio", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Auditoría", result.title)
        assertEquals(LocalDate.of(2027, 6, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDeSeptiembreConAnioExplicito() {
        // año explícito de 4 cifras: septiembre 2027.
        // viernes de septiembre 2027: 3, 10, 17, 24 → último = 24.
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Cobro el último viernes de septiembre del 2027", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2027, 9, 24), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun ultimoViernesDeSeptiembreRespetaHoraExplicita() {
        // El mes nombrado no debe comer la hora explícita "a las 10".
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("Pago el último viernes de septiembre a las 10", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 25), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, ZoneId.of("America/Santiago")))
    }

    // --- Recurrencia mensual ordinal sembrada en el pasado (P1: no olvidar la 1ª ocurrencia) ---
    // "el primer lunes de cada mes" dicho DESPUÉS de que el primer lunes del mes ya pasó sembraba
    // la cadencia en el pasado → vencida al instante + recordatorio descartado → 1ª cita olvidada.
    // La fecha suelta vencida sigue siendo honesta (deuda real); la recurrente rueda al próximo
    // mes con el mismo ordinal+weekday. now = 2026-08-20 (jueves). Primer lunes ago = 03 (pasado).

    @Test fun recurrenciaPrimerLunesPasadoRuedaAProximoMes() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el primer lunes de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // Primer lunes de septiembre 2026 = 07 (ago-03 quedó en pasado).
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun recurrenciaSegundoMartesPasadoRuedaAProximoMes() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el segundo martes de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        // Segundo martes ago = 11 (pasado) → segundo martes sep = 08.
        assertEquals(LocalDate.of(2026, 9, 8), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun recurrenciaOrdinalFuturoNoSeMueve() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        // Último viernes de agosto 2026 = 28 (futuro desde el 20): no debe rodar.
        val result = NaturalTaskParser.parse("Reunión el último viernes de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun recurrenciaOrdinalHoyNoRueda() {
        val zone = ZoneId.of("America/Santiago")
        // Tercer jueves de agosto 2026 = 20 = hoy. La ocurrencia de hoy es válida (no en pasado).
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el tercer jueves de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalDelMesPasadoSePromueveMonthlyYAvanzaAFuturo() {
        // c.316 — SUSTITUYE al guard "vencida honesta" previo. SIN "de cada mes" la rutina
        // mensual ordinal "del mes" ya NO queda como deuda vencida en pasado: se promueve a
        // MONTHLY (BACKLOG c.318 P1: rutina periódica olvidada). now=2026-08-20, 1er lunes
        // de agosto=08-03 (pasado) → avanza al 1er lunes de septiembre=09-07.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cobro el primer lunes del mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun recurrenciaOrdinalPasadoConservaHoraExplicita() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el primer lunes de cada mes a las 18", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Persistencia del anclaje ordinal en `recurrenceDays` (c.215: guard anti-deriva) ---
    // Los tests anteriores verifican la 1ª ocurrencia (dueAt). Pero el bug real de deriva
    // estaba en la 2ª cita: si el parser NO persiste el ordinal en `recurrenceDays`, el motor
    // sólo ve el día del mes (p. ej. 7) y la 2ª cita deriva a "el 7 de cada mes" (un miércoles)
    // en vez del 1er lunes. Estos tests fijan el CONTRATO parser→motor: el parser DEBE emitir
    // "ord:weekday" para que `RecurrenceEngine` ancle cada ciclo al N-ésimo/último día de la
    // semana. Sin ellos, una regresión que vacíe `recurrenceDays` pasaría los tests de dueAt
    // (1ª ocurrencia) y los tests del motor (que alimentan la codificación a mano) pero
    // reintroduciría la deriva silenciosa en el mundo real. Codificación: ord∈{1,2,3,4,-1},
    // weekday∈1..7 ISO (1=lunes).

    @Test fun recurrenciaOrdinalEmiteCodificacionPrimerLunes() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el primer lunes de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays) // 1er lunes (ISO lunes=1)
    }

    @Test fun recurrenciaOrdinalEmiteCodificacionUltimoViernes() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el último viernes de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-1:5", result.recurrenceDays) // último viernes (ISO viernes=5)
    }

    @Test fun recurrenciaOrdinalEmiteCodificacionTercerJueves() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el tercer jueves de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("3:4", result.recurrenceDays) // 3er jueves (ISO jueves=4)
    }

    @Test fun recurrenciaMensualDiaDelMesNoEmiteCodificacionOrdinal() {
        // Guard anti-falso-positivo: "el 15 de cada mes" (día del mes puro) NO debe emitir
        // codificación ordinal; `recurrenceDays` queda vacío y el motor ancla al día 15.
        // Si el parser emitiara "ord:weekday" aquí, el motor descartaría el día 15.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el 15 de cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("", result.recurrenceDays)
    }

    @Test fun recurrenciaOrdinalIntervalo2EmiteCodificacionYIntervalo() {
        // "tercer miércoles de cada 2 meses": codificación ordinal + intervalo 2.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el tercer miércoles de cada 2 meses", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("3:3", result.recurrenceDays) // 3er miércoles (ISO miércoles=3)
    }

    // --- Paridad léxica "cada mes" (sin "de") para recurrencia ordinal mensual ---
    // El conector de cadencia "de cada mes" funciona (tests de arriba), pero la forma
    // cotidiana SIN el "de" —"el primer lunes cada mes", "el último viernes cada mes"—
    // NO casaba el patrón ordinal (exigía "del? <mes>"), así que el parser:
    //   (1) perdía el ordinal → recurrenceDays='' → el motor anclaba al DÍA DEL MES
    //       (deriva: 1ª cita el lunes 7, 2ª el día 7 aunque caiga miércoles); y
    //   (2) dejaba "el primer"/"el último" como residuo en el título.
    // now = 2026-08-20 (jueves). Primer lunes ago = 03 (pasado) → rueda a sep-07; último
    // viernes ago = 28 (futuro) → se queda. Paridad EXACTA con la forma "de cada mes".

    @Test fun recurrenciaPrimerLunesCadaMesSinDeEmiteCodificacionYRueda() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el primer lunes cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Pago", result.title)
    }

    @Test fun recurrenciaUltimoViernesCadaMesSinDeEmiteCodificacionYConservaFecha() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el último viernes cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-1:5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Reunión", result.title)
    }

    @Test fun recurrenciaSegundoMartesCadaMesSinDeEmiteCodificacion() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el segundo martes cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("2:2", result.recurrenceDays)
        assertEquals("Reunión", result.title)
    }

    @Test fun recurrenciaOrdinalCadaMesSinElInicialLimpiaTitulo() {
        // Sin "el" inicial pero con "cada mes": el ordinal debe anclar y el título quedar limpio.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Renta primer lunes cada mes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals("Renta", result.title)
    }

    @Test fun recurrenciaOrdinalTodosLosMesesSinDeEmiteCodificacion() {
        // "todos los meses" como conector de cadencia (paridad con "cada mes"/"mensual").
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago el tercer jueves todos los meses", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("3:4", result.recurrenceDays)
        assertEquals("Pago", result.title)
    }

    @Test fun recurrenciaOrdinalMensualSinDeEmiteCodificacion() {
        // "mensual" como conector de cadencia tras el weekday (paridad con "cada mes"/"de cada mes").
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Renta el primer lunes mensual", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals("Renta", result.title)
    }

    @Test fun fechaSueltaPrimerLunesSinCadenciaNoEsRecurrente() {
        // Guard anti-falso-positivo: "el primer lunes" SIN conector de cadencia sigue
        // siendo fecha suelta (no recurrente), igual que antes. Sin "cada mes"/"todos
        // los meses"/"mensual" no hay nada que anclar como recurrencia.
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cita el primer lunes", agoNow, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("", result.recurrenceDays)
    }

    // --- Cadencia PRECEDENTE: "cada mes el primer lunes" (orden inverso) ---
    // La forma cotidiana con la cadencia ANTES del ordinal —"cada mes el primer lunes",
    // "mensual el primer lunes", "todos los meses el último viernes"— NO casaba el patrón
    // ordinal (su lookahead exige cadencia DESPUÉS del weekday). El parser:
    //   (1) perdía el ordinal → recurrenceDays='' → el motor anclaba al DÍA DEL MES
    //       (deriva: 1ª cita el lunes 7, 2ª el día 7 aunque caiga miércoles); y
    //   (2) dejaba "el primer"/"el último" como residuo en el título.
    // now = 2026-08-20 (jueves). Primer lunes ago = 03 (pasado) → rueda a sep-07; último
    // viernes ago = 28 (futuro) → se queda. Paridad EXACTA con la forma "el ... de cada mes".

    @Test fun recurrenciaCadaMesPrimerLunesPrecedenteEmiteCodificacionYRueda() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Gym cada mes el primer lunes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Gym", result.title)
    }

    @Test fun recurrenciaCadaMesUltimoViernesPrecedenteEmiteCodificacionYConservaFecha() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión cada mes el último viernes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-1:5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Reunión", result.title)
    }

    @Test fun recurrenciaMensualPrimerLunesPrecedenteLimpiaTitulo() {
        // "mensual" antes del ordinal: paridad con "cada mes el primer lunes".
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Gym mensual el primer lunes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals("Gym", result.title)
    }

    @Test fun recurrenciaTodosLosMesesUltimoViernesPrecedenteLimpiaTitulo() {
        // "todos los meses" antes del ordinal: paridad con "cada mes el último viernes".
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago todos los meses el último viernes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-1:5", result.recurrenceDays)
        assertEquals("Pago", result.title)
    }

    // c.272 — Recurrencia ordinal de weekday con CADENCIA PRECEDENTE PLURIMENSUAL
    // (trimestral/bimestral/cuatrimestral/semestral) y con intervalo explícito
    // ("cada N meses"). Estos adjetivos/cadencias SÍ emitían MONTHLY+interval (c.258),
    // PERO el ordinal "el primer lunes" NO se capturaba: el lookahead del patrón directo
    // y la alternancia del patrón precedente sólo reconocían "mensual/cada mes/todos los
    // meses". Así `recurrenceDays=''` (motor anclaba al día del mes → 2ª cita derivaba a
    // otro weekday) Y "el primer"/"el último" quedaba como residuo del título. Misma clase
    // de bug que c.256/c.271, ahora cerrada para las cadencias de plazo largo. now=2026-08-20
    // (jueves): primer lunes ago=03 (pasado) → rueda a sep-07; último viernes ago=28 (futuro).

    @Test fun recurrenciaTrimestralPrimerLunesPrecedenteEmiteCodificacionYRueda() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión trimestral el primer lunes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 7), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Reunión", result.title)
    }

    @Test fun recurrenciaSemestralUltimoViernesPrecedenteEmiteCodificacionYConservaFecha() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago semestral el último viernes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertEquals("-1:5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("Pago", result.title)
    }

    @Test fun recurrenciaBimestralSegundoMartesPrecedenteLimpiaTitulo() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Control bimestral el segundo martes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("2:2", result.recurrenceDays)
        assertEquals("Control", result.title)
    }

    @Test fun recurrenciaCadaDosMesesPrimerLunesPrecedenteEmiteCodificacion() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión cada dos meses el primer lunes", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals("1:1", result.recurrenceDays)
        assertEquals("Reunión", result.title)
    }

    // Guard: el adjetivo plurimensual SIN ordinal sigue MONTHLY+interval (sin
    // codificación ordinal), sin que el fix introduzca falsa captura de ordinal.
    @Test fun recurrenciaTrimestralSinOrdinalNoEmiteCodificacionOrdinal() {
        val zone = ZoneId.of("America/Santiago")
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 20), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Pago trimestral", agoNow, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertEquals("", result.recurrenceDays)
        assertEquals("Pago", result.title)
    }

    // Conector de plazo "hasta" + fecha: el conector sobrevivía como residuo en el título
    // ("entregar hasta" aunque la fecha era correcta) porque weekdayPattern/dayOfMonthPattern
    // consumían "el viernes"/"el 20" ANTES del borrado de "hasta el", dejándolo huérfano.
    @Test fun hastaElViernesLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("entregar hasta el viernes", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaFinDeMesLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("enviar hasta fin de mes", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaElDiaSueltoLimpiaTituloYRuedaAlMesSiguiente() {
        // "hasta el 20" dicho el 29: el 20 ya pasó → rueda al 20 del mes siguiente.
        val result = NaturalTaskParser.parse("entregar hasta el 20", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaMananaLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("reunión hasta mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaFechaConMesLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("pago hasta el 15 de septiembre", now, zone)
        assertEquals("pago", result.title)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaDentroDeRelativoLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("llamar hasta dentro de 3 días", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun hastaLasHoraLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("llamar hasta las 5 de la tarde", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Seguridad de contenido: "hasta" como límite de acción (no plazo con fecha) NO debe
    // tocarse ni inventarse vencimiento.
    @Test fun hastaComoLimiteDeAccionPreservaContenidoSinVencimiento() {
        val result = NaturalTaskParser.parse("trabajar hasta terminar el informe", now, zone)
        assertEquals("trabajar hasta terminar el informe", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun hastaAnteSustantivoPreservaContenidoSinVencimiento() {
        val result = NaturalTaskParser.parse("leer hasta la página 50", now, zone)
        assertEquals("leer hasta la página 50", result.title)
        assertEquals(null, result.dueAt)
    }

    // Conector de plazo "antes de/del" + fecha/hora: simétrico a "hasta". La fecha
    // subyacente se resolvía bien, pero el conector sobrevivía como residuo en el título
    // ("enviar antes", "llamar las") porque el patrón de fecha consumía la fecha ANTES del
    // borrado tardío de "antes del". Ahora se normaliza temprano (c.237).
    @Test fun antesDelViernesLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("enviar antes del viernes", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antesDeMananaLimpiaTituloYResuelveFecha() {
        val result = NaturalTaskParser.parse("reunión antes de mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antesDeLasHoraConMeridianoLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("llamar antes de las 5 de la tarde", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasMananaLimpiaTituloYResuelveHoraPasada() {
        // 09:00 dicho al mediodía: el plazo ya pasó → vencida honesta (no se proyecta).
        val result = NaturalTaskParser.parse("llamar antes de las 9 de la mañana", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "antes del 30" (día suelto) y "antes del 15 de agosto" (día+mes) ya funcionaban vía
    // beforeDeadlineDayPattern/monthNameDate: la normalización nueva NO debe romperlos.
    @Test fun antesDelDiaSueltoSigueResolviendoViaBeforeDeadline() {
        val result = NaturalTaskParser.parse("enviar antes del 30", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antesDelDiaConMesSigueResolviendoViaMonthNameDate() {
        val result = NaturalTaskParser.parse("pagar antes del 15 de agosto", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "antes de las 5" (sin meridio) es ambiguo (5am/5pm): NO se inventa vencimiento.
    @Test fun antesDeLasHoraSinMeridioNoInventaVencimiento() {
        val result = NaturalTaskParser.parse("llamar antes de las 5", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- ciclo 240: "<día> <mes>" sin conector "de" (forma abreviada de captura) ---

    @Test fun bareDayMonthAbbrConHoraResuelveDiaYMesCorrectos() {
        // "Reunión 22 ago a las 15:30": antes la fecha caía a HOY (día equivocado)
        // y "ago" sobrevivía en el título. Ahora resuelve 22 de agosto a las 15:30.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión 22 ago a las 15:30", baseNow, zone)
        assertEquals("Reunión", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 22), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun bareDayMonthAbbrSinHoraNoSeOlvida() {
        // "Renovar suscripción 1 sept": antes dueAt=null (compromiso olvidado, sin
        // recordatorio ni visibilidad en What Now/planificador). Ahora → 1 de septiembre.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Renovar suscripción 1 sept", baseNow, zone)
        assertEquals("Renovar suscripción", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bareDayMonthAbbrTresLetrasNoSeOlvida() {
        // "Entregar 1 oct": abreviatura de 3 letras, antes dueAt=null (olvidado).
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Entregar 1 oct", baseNow, zone)
        assertEquals("Entregar", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 10, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bareDayMonthNombreCompletoNoSeOlvida() {
        // "Cita 20 agosto": nombre completo sin "de", antes caía a día suelto del mes en
        // curso o null. Ahora → 20 de agosto.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cita 20 agosto", baseNow, zone)
        assertEquals("Cita", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bareDayMesPasadoRuedaAlProximoAno() {
        // "Cita 5 enero" dicho en julio: enero ya pasó este año → rueda a enero del año
        // siguiente (mismo roll que "5 de enero").
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cita 5 ene", baseNow, zone)
        assertEquals("Cita", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2027, 1, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun bareDayMonthNoFalsificaFechaDeContenido() {
        // "comprar 3 manzanas": "manzanas" NO es mes → no se inventa fecha. El número
        // y la palabra se conservan íntegros en el título.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("comprar 3 manzanas", baseNow, zone)
        assertEquals("comprar 3 manzanas", result.title)
        assertNull(result.dueAt)
    }

    @Test fun fechaConConectorDeSigueFuncionandoSinRegresion() {
        // Regresión: la forma canónica "el 22 de agosto" (con "de") sigue resolviéndose.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Reunión el 22 de agosto a las 15:30", baseNow, zone)
        assertEquals("Reunión", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 22), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun fechaNumericaConSeparadorSigueFuncionandoSinRegresion() {
        // Regresión: la fecha numérica "20/8" no debe ser alterada por el normalizador
        // de "<día> <mes>".
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("Cita 20/8 a las 10", baseNow, zone)
        assertEquals("Cita", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun duracionEscritaTrasDiaMesNoSeRompe() {
        // Regresión: "estudiar 2 horas" — "horas" NO es mes → no se fecha; la duración
        // "2 horas" se sigue capturando como tal.
        val zone = ZoneId.of("America/Santo_Domingo")
        val baseNow = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("estudiar 2 horas", baseNow, zone)
        assertEquals("estudiar", result.title)
        assertEquals(120, result.durationMinutes)
        assertNull(result.dueAt)
    }

    // --- Sufijo de unidad (h/hs/horas) ANTES del meridiem o de la fracción (c.242) ---
    // El orden natural del usuario no siempre es [fracción][meridiem][sufijo]: escribe
    // "9h pm", "9 horas y media", "3:30h pm". Antes el orden fijo del patrón dejaba el
    // modificador (pm / y media) como residuo en el título y agendaba la hora en punto y
    // sin offset → cita 12 h antes (reunión nocturna) o 30 min mal. Ahora el sufijo es
    // no capturante y se admite antes y después (simétrico del reloj "HH:MMh pm" de c.235).
    private fun suffixOrderNow() = DateRules.toEpochMillis(
        LocalDate.of(2026, 7, 29), LocalTime.NOON, ZoneId.of("America/Santo_Domingo")
    )

    @Test fun aLasNhPmAplicaOffsetYDejaTituloLimpio() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9h pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhAmAplicaOffsetYDejaTituloLimpio() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9h am", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhorasYMediaResuelveFraccionTrasSufijo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9 horas y media", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhYMediaResuelveFraccionTrasSufijoCompacto() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9h y media", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhorasYCuartoResuelveFraccionTrasSufijo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9 horas y cuarto", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhPmConRelojResuelveOffsetCompleto() {
        // "3:30h pm" → antes 03:30 + residuo "pm"; ahora 15:30 limpio.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 3:30h pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhorasPmResuelveSufijoYMeridiemJuntos() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión a las 9 horas pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNhPmNoSeComePalabraDeContenido() {
        // Regresión: la "h" del sufijo no debe devorar la "h" de "hoy"/"hola".
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("llamar a las 9h hoy", suffixOrderNow(), zone)
        // "hoy" se consume como fecha (hoy), no queda en el título; la "h" del sufijo
        // no se confunde con la "h" inicial de "hoy".
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "para las/la": introductor de hora alternativo a "a las/la" ("reunión para las 9h30",
    // "te veo para las 9 pm"). Antes estas citas caían a dueAt=null (olvidadas) o, cuando el
    // reloj independiente (:MM/meridiem) casaba, dejaban "para las" como residuo del título.
    // Se normaliza a "a las/la" exigiendo evidencia de reloj (no agendar cantidades).
    @Test fun paraLasNhMmResuelveHoraYDejaTituloLimpio() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9h30", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNPMResuelveOffsetYDejaTituloLimpio() {
        // Antes: el reloj "9 pm" casaba (21:00) pero "para las" sobrevivía como residuo.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9 pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNRelojResuelveHoraYDejaTituloLimpio() {
        // Antes: "9:30" casaba (09:30) pero "para las" sobrevivía como residuo.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9:30", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNDeLaNocheResuelveOffsetYDejaTituloLimpio() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9 de la noche", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNhResuelveHoraCompactaSinMeridiem() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9h", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNhorasResuelveSufijoDeUnidad() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9 horas", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNuevaYMediaResuelveHoraEscritaYFraccion() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las nueve y media", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNyMediaResuelveFraccion() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9 y media", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLaUnaDelMediodiaResuelvePm() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para la una del mediodía", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasQuinceh30ResuelveHora24Compacta() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 15h30", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNAmResuelveMeridiem() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("entrega para las 9 am", suffixOrderNow(), zone)
        assertEquals("entrega", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- SEGURIDAD: "para" como destinatario/cantidad/propósito NO se agenda como cita ---
    @Test fun paraLasNPersonasNoInventaCita() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("comprar para las 9 personas", suffixOrderNow(), zone)
        assertNull(result.dueAt)
        assertEquals("comprar para las 9 personas", result.title)
    }

    @Test fun paraLasNinasNoInventaCita() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("regalos para las niñas", suffixOrderNow(), zone)
        assertNull(result.dueAt)
    }

    @Test fun paraLasNCajasNoInventaCita() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("comprar para las 3 cajas", suffixOrderNow(), zone)
        assertNull(result.dueAt)
    }

    @Test fun paraLasVentasNoInventaCita() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las ventas", suffixOrderNow(), zone)
        assertNull(result.dueAt)
    }

    @Test fun paraLasNEnPuntoSinEvidenciaNoInventaCita() {
        // "para las 9" en punto (sin meridiem/minutos) es ambiguo con "mesa para las 9
        // [personas]": como "sobre las 9", queda fuera (no se falsifica cita).
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9", suffixOrderNow(), zone)
        assertNull(result.dueAt)
    }

    @Test fun paraMananaSigueResolviendoFechaSinRegresion() {
        // "para mañana" ya existía como forma de fecha; no debe romperse.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para mañana", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun paraElLunesSigueResolviendoFechaSinRegresion() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para el lunes", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertNotNull(result.dueAt)
    }

    // "el 1 y 15 de cada mes" / "cobro los días 15 y 30 de cada mes": recurrencia
    // mensual con DOS días del mes (quincena/nómina/cobro, LATAM). Antes el parser
    // sólo anclaba el 1er día, perdía el 2º silenciosamente y dejaba " y" como residuo
    // en el título ("pagar y") — un día de pago real nacía olvidado (P1). Ahora ambos
    // días se codifican en recurrenceDays="d:N1,N2" y el título queda limpio (c.315).
    @Test fun dualDayMonthlyParsesBothDaysAndCleanTitle() {
        val result = NaturalTaskParser.parse("renta el 1 y 15 de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        // hoy=29-jul-2026: la 1ª ocurrencia futura de {1,15} es 01-ago.
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayMonthlyWithDiasWordAndLastOfMonth() {
        // "cobro los días 15 y 30 de cada mes": hoy=29-jul → 1ª = 30-jul (2º día).
        val result = NaturalTaskParser.parse("cobro los días 15 y 30 de cada mes", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayMonthlyDoesNotBreakSingleDay() {
        // Regresión: la forma de un solo día "el 15 de cada mes" sigue siendo MONTHLY
        // anclado al día (recurrenceDays vacío), NO "d:15".
        val result = NaturalTaskParser.parse("renta el 15 de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("", result.recurrenceDays)
    }

    // c.321: generalización a N días + sinónimos léxicos. El patrón c.315 limitaba a 2
    // días y dejaba 3 clases de residuo/pérdida (probe JVM c.321). Estas pruebas blindan
    // las 3 correcciones: (a) lista de 3 días sin perder el 1º; (b) artículo repetido
    // "el 1 y el 15"; (c) sinónimo "todos los meses".

    @Test fun tripleDayMonthlyCapturesAllThreeDaysNoLoss() {
        // "el 1, 15 y 30 de cada mes": antes el dual-day casaba "15 y 30" y perdía el "1"
        // (residuo "1, " en título + día de pago olvidado). Ahora se capturan los 3.
        val result = NaturalTaskParser.parse("pago el 1, 15 y 30 de cada mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15,30", result.recurrenceDays)
    }

    @Test fun dualDayMonthlyRepeatedArticleParsesBothDays() {
        // "el 1 y el 15 de cada mes" (artículo repetido, forma natural en español):
        // antes NO casaba (esperaba "y 15" no "y el 15"), caía al single-day y dejaba
        // "y el 15" como residuo. Ahora se anclan ambos días y el título queda limpio.
        val result = NaturalTaskParser.parse("pago el 1 y el 15 de cada mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayMonthlyTodosLosMesesSynonym() {
        // "todos los meses" = sinónimo cotidiano de "cada mes": antes NO casaba, caía al
        // single-day ("el 1") y dejaba "y 15" como residuo. Ahora se anclan ambos días.
        val result = NaturalTaskParser.parse("renta el 1 y 15 todos los meses", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.324: cadencia quincenal + días del mes explícitos → MONTHLY anclado a esos días
    // (NO DAILY x15). El usuario que especifica "pago quincenal los días 1 y 15" pincha
    // días de pago mensuales concretos; la lista de días fija el significado y anula la
    // cadencia genérica DAILY x15 (cada 15 días), que derivaba 1 día por ciclo y
    // desfasaba los días de pago reales mes a mes (P1: dato de pago erróneo). El
    // adjetivo "quincenal" actúa como marcador de cadencia DELANTERO o TRASERO,
    // equivalente a "de cada mes". "quincenal" SOLO (sin días) sigue siendo DAILY x15.

    @Test fun quincenalCadenceWithDayListParsesMonthlyAnchoredDays() {
        // "pago quincenal los días 1 y 15": antes caía a DAILY x15 + residuo
        // "pago los días 1 y 15" (días de pago perdidos como recurrencia). Ahora MONTHLY.
        val result = NaturalTaskParser.parse("pago quincenal los días 1 y 15", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenalCadenceWithDayListMatchesCanonicalForm() {
        // Paridad: "quincenal los días 1 y 15" == "los días 1 y 15 de cada mes" (canónico).
        val quincenal = NaturalTaskParser.parse("cobro quincenal los días 1 y 15", now, zone)
        val canonico = NaturalTaskParser.parse("cobro los días 1 y 15 de cada mes", now, zone)
        assertEquals(canonico.recurrence, quincenal.recurrence)
        assertEquals(canonico.recurrenceDays, quincenal.recurrenceDays)
        assertEquals(canonico.dueAt, quincenal.dueAt)
        assertEquals("cobro", quincenal.title)
    }

    @Test fun quincenalCadenceTrailingMarkerParsesMonthlyDays() {
        // Marcador TRASERO: "los días 1 y 15 quincenal" (forma menos común pero natural).
        val result = NaturalTaskParser.parse("nómina los días 1 y 15 quincenal", now, zone)
        assertEquals("nómina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
    }

    @Test fun quincenalCadenceWithRepeatedArticleParsesMonthlyDays() {
        // "quincenal el 1 y el 15" (artículo repetido, como en c.321 para la forma canónica).
        val result = NaturalTaskParser.parse("pago quincenal el 1 y el 15", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
    }

    @Test fun quincenalAloneStaysDailyInterval15() {
        // NO-regresión: "quincenal" SIN días del mes sigue siendo DAILY x15 (c.276/c.321).
        // La generalización c.324 sólo aplica cuando hay días del mes explícitos.
        val result = NaturalTaskParser.parse("cobro quincenal", now, zone)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertEquals("", result.recurrenceDays)
    }

    @Test fun quincenalCadenceWithWeekdaysStaysWeeklyInterval2() {
        // NO-regresión: "quincenal los lunes" → WEEKLY x2 (días de semana), inafectado por
        // c.324 (éste sólo actúa sobre días del MES, los de semana los resuelve dayListPattern
        // ANTES).
        val result = NaturalTaskParser.parse("reporte quincenal los lunes", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
    }

    @Test fun bareDayListWithoutCadenceDoesNotClaimMonthly() {
        // NO-regresión: "el 1 y 15" SIN la palabra "días" y sin NINGÚN marcador de
        // cadencia (ni "mes" ni "quincenal") sigue siendo ambigua y NO se reclama como
        // MONTHLY con "d:1,15" (se deja a la cascada, que resuelve el 1er día por
        // monthlyDayPattern). c.331 refinó la decisión c.324: la lista CON "días"
        // plural explícito ("los días 15 y 30") SÍ es cadencia mensual; la lista SIN
        // "días" sigue siendo ambigua. Este test ancla el caso SIN "días".
        val result = NaturalTaskParser.parse("el 1 y 15", now, zone)
        // Sin "días" plural, la recurrencia NO debe ser MONTHLY con d:1,15.
        assertFalse(result.recurrence == RecurrenceFrequency.MONTHLY && result.recurrenceDays == "d:1,15")
    }

    // c.331: "los días N y M" (plural explícito) SIN "de cada mes"/"quincenal" es la
    // frase canónica LATAM de cobro/pago quincenal. Antes caía a NONE+dueAt=null →
    // rutina olvidada (sin recordatorio, invisible en What Now/planificador). Ahora el
    // prefijo "los días"/"días" plural es POR SÍ MISMO marcador de cadencia mensual.
    @Test fun losDiasPluralSinSufijoParsesMonthlyAnchoredDays() {
        // "cobro los días 15 y 30": hoy=29-jul → 1ª = 30-jul (2º día de la lista).
        val result = NaturalTaskParser.parse("cobro los días 15 y 30", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun losDiasPluralSinArticuloParsesMonthlyAnchoredDays() {
        // "días 1 y 15" sin "los": la palabra "días" plural basta como marcador.
        val result = NaturalTaskParser.parse("pago días 1 y 15", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun losDiasPluralMatchesCanonicalWithDeCadaMes() {
        // Paridad: "los días 15 y 30" == "los días 15 y 30 de cada mes" (canónico).
        val bare = NaturalTaskParser.parse("cobro los días 15 y 30", now, zone)
        val canonico = NaturalTaskParser.parse("cobro los días 15 y 30 de cada mes", now, zone)
        assertEquals(canonico.recurrence, bare.recurrence)
        assertEquals(canonico.recurrenceDays, bare.recurrenceDays)
        assertEquals(canonico.dueAt, bare.dueAt)
    }

    @Test fun losDiasPluralTresDiasParsesAllThree() {
        // "los días 1, 15 y 30": N días con coma + "y".
        val result = NaturalTaskParser.parse("cobro los días 1, 15 y 30", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15,30", result.recurrenceDays)
    }

    @Test fun losDiasPluralRespetaHoraExplicita() {
        // "cobro los días 15 y 30 a las 10": la hora explícita se respeta.
        val result = NaturalTaskParser.parse("cobro los días 15 y 30 a las 10", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun listaSinDiasPluralSigueSiendoAmbigua() {
        // NO-regresión: "reunión el 1 y 15" (sin "días") sigue sin reclamar d:1,15.
        val result = NaturalTaskParser.parse("reunión el 1 y 15", now, zone)
        assertFalse(result.recurrence == RecurrenceFrequency.MONTHLY && result.recurrenceDays == "d:1,15")
    }

    // c.341: lista de días con mes NOMBRADO. Antes el día-lista consumía los dígitos y
    // `monthNamePattern` (que exige dígito+mes) ya no casaba → el mes nombrado se
    // ignoraba, la 1ª fecha se anclaba al mes ACTUAL y "de septiembre" quedaba como
    // residuo del título. P1 de datos: cita agendada en mes erróneo. Ahora la 1ª fecha
    // se ancla al mes nombrado (paridad con parseMonthNameDate) y el título queda limpio.
    // now = 2026-07-29 (julio).

    @Test fun dualDayListWithNamedMonthAnchorsToThatMonth() {
        // "reunión los días 15 y 30 de septiembre": septiembre es futuro desde julio →
        // 1ª = 15-sep (menor día de la lista en el mes objetivo). Antes: 30-jul (mes
        // actual) — cita 2 meses antes de lo pedido.
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 de septiembre", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListWithNamedMonthCurrentMonthPicksFutureDay() {
        // "cobro los días 15 y 30 de agosto": agosto dicho en julio (29) → agosto es
        // futuro → 1ª = 15-ago (menor día). Confirma que no hay confusión mes actual.
        val result = NaturalTaskParser.parse("cobro los días 15 y 30 de agosto", now, zone)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("cobro", result.title)
    }

    @Test fun dualDayListWithPastNamedMonthRollsNextYear() {
        // "pago los días 15 y 30 de mayo": mayo ya pasó (julio) → rueda al año
        // siguiente y toma el menor día (15-may-2027). Paridad con parseMonthNameDate.
        val result = NaturalTaskParser.parse("pago los días 15 y 30 de mayo", now, zone)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2027, 5, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListWithNamedMonthAndExplicitYearRespectsYear() {
        // "cita los días 1 y 15 de marzo del 2028": año explícito → fecha literal (sin
        // roll), 1ª = 01-mar-2028.
        val result = NaturalTaskParser.parse("cita los días 1 y 15 de marzo del 2028", now, zone)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2028, 3, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListWithNamedMonthCleansTitleResidue() {
        // El mes nombrado ("de septiembre") NO debe quedar como residuo del título.
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 de septiembre", now, zone)
        assertEquals("reunión", result.title)
        assertFalse(result.title.contains("septiembre"))
    }

    @Test fun dualDayListWithNamedMonthAbbreviationParses() {
        // Abreviatura informal "sep" (común al capturar): debe anclar igual que "septiembre".
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 de sep", now, zone)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListWithNamedMonthThenCadenceCleansTitle() {
        // "los días 15 y 30 de septiembre de cada mes": mes nombrado + cadencia trasera.
        // La 1ª fecha se ancla al mes nombrado (15-sep) y el título queda limpio (ni
        // "de septiembre" ni "de cada mes").
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 de septiembre de cada mes", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("reunión", result.title)
    }

    @Test fun dualDayListNamedMonthNormalizesImpossibleDay() {
        // "el 30 y 31 de febrero": febrero no tiene 31 → se normaliza al último día
        // válido (28-feb, año no bisiesto 2027 por roll, ya que feb-2026 pasó). 1ª =
        // 28-feb-2027 (días clamped: 30→28, 31→28; el menor día válido futuro).
        val result = NaturalTaskParser.parse("cita los días 30 y 31 de febrero", now, zone)
        assertEquals("d:30,31", result.recurrenceDays)
        // feb-2026 ya pasó → rueda a 2027; 30/31 se normalizan a 28 → 1ª = 28-feb-2027.
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListElRepeatedWithNamedMonthClaimsMonthly() {
        // c.342: "reunión el 15 y el 30 de septiembre" (forma con "el" repetido, sin
        // "días" plural). Asimetría con "los días 15 y 30 de septiembre" (c.341, que SÍ
        // es MONTHLY d:15,30 due=15-sep). Antes caía a NONE + título roto ('reunión y').
        // El mes nombrado trasero es por sí mismo señal de cadencia (quita la ambigüedad
        // de la lista pelada, c.324): mes futuro sep → 1ª menor día = 15-sep-2026.
        val result = NaturalTaskParser.parse("reunión el 15 y el 30 de septiembre", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 9, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("reunión", result.title)
    }

    @Test fun dualDayListElRepeatedWithNamedMonthCleansTitle() {
        // La forma con "el" repetido también limpia el título: tanto la lista
        // "el 15 y el 30" como "de septiembre" se borran → título "pago".
        val result = NaturalTaskParser.parse("pago el 1 y el 15 de agosto", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:1,15", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("pago", result.title)
    }

    @Test fun bareDualDayListWithoutNamedMonthStillAmbiguous() {
        // No-regresión c.324/c.331: la lista SIN "días" plural NI mes nombrado sigue
        // siendo ambigua ("el 1 y 15" sin mes) → NO reclama MONTHLY (recurrence=NONE).
        // El fix c.342 sólo reclama cuando hay mes nombrado trasero (señal de cadencia).
        // (Nota: "el 1" sí se fecha individualmente por otro patrón → dueAt no es null,
        // pero la cadencia NO se reclama. El residuo de título "y 15" es un problema UX
        // separado, registrado en BACKLOG — fuera del alcance de c.342.)
        val result = NaturalTaskParser.parse("reunión el 1 y 15", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("", result.recurrenceDays)
    }

    // --- c.344: "los días N y M del mes que viene/próximo/entrante" ---
    // REPRODUCCIÓN del bug: la lista multi-día se reclama MONTHLY y ancla la 1ª fecha
    // al mes ACTUAL (ignora "del mes que viene") porque scanTrailingNamedMonth solo
    // reconoce meses NOMBRADOS ("de septiembre"), no relativos ("del mes que viene").
    // Rutina quincenal anclada al mes equivocado = cita en mes erróneo (P1 de datos).
    // Hoy = 2026-07-29; "del mes que viene" = agosto; 1ª = 2026-08-15 (menor día).

    @Test fun dualDayListDelMesQueVieneAnclaAlMesSiguiente() {
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 del mes que viene", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListDelMesProximoAnclaAlMesSiguiente() {
        val result = NaturalTaskParser.parse("cobro los días 15 y 30 del mes próximo", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListDelMesEntranteAnclaAlMesSiguiente() {
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 del mes entrante", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListElRepeatedDelMesQueVieneAnclaAlMesSiguiente() {
        // Forma con "el" repetido (sin "días" plural) + "del mes que viene": mismo
        // anclaje al mes siguiente. Paridad con el fix c.343 (mes nombrado).
        val result = NaturalTaskParser.parse("reunión el 15 y el 30 del mes que viene", now, zone)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("d:15,30", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dualDayListDelMesQueVieneLimpiaTitulo() {
        // El calificador "del mes que viene" se borra del título (no queda residuo "del").
        val result = NaturalTaskParser.parse("reunión los días 15 y 30 del mes que viene", now, zone)
        assertEquals("reunión", result.title)
    }
}

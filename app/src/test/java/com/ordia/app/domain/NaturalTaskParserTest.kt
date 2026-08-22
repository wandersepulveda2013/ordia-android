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

    // Abreviaturas de días ("lun mie vie") deben tratarse idéntico al nombre
    // completo ("lunes miércoles viernes"). Al capturar rutinas por teclado/voz
    // es la forma más común y antes se perdía: rutina semanal sin recurrencia ni
    // fecha (P1: se olvidaba). La expansión léxica hace que el resto del pipeline
    // la procese igual que la forma completa. "mar" se excluye (colisión con
    // marzo: ver [weekdayAbbrevRewriter]).
    @Test fun parsesAbbrevWeekdayListSpaceSeparated() {
        val result = NaturalTaskParser.parse("Gimnasio lun mie vie", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    @Test fun parsesAbbrevWeekdayListCommaSeparated() {
        val result = NaturalTaskParser.parse("Clase lun, mie, vie", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    @Test fun parsesAbbrevWeekdayListWithY() {
        val result = NaturalTaskParser.parse("Fútbol sab y dom", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun parsesAbbrevWeekdayWithTrailingDot() {
        val result = NaturalTaskParser.parse("Clase lun. mie. vie.", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    // Lista de días abreviada separada por GUIONES: "clase lun-mie-vie". Es la
    // forma de captura rápida más natural para rutinas tipo clase/turno, y antes
    // caía sin recurrencia (recur=NONE) dejando "-mie" como residuo corrupto en el
    // título → la rutina se olvidaba por completo. El guion ahora es conector de
    // lista válido, al igual que coma, "y" y espacio.
    @Test fun parsesAbbrevWeekdayListHyphenSeparated() {
        val result = NaturalTaskParser.parse("Clase lun-mie-vie", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    // Mismo caso con nombres completos: "clase lunes-miercoles-viernes".
    @Test fun parsesFullWeekdayListHyphenSeparated() {
        val result = NaturalTaskParser.parse("Clase lunes-miercoles-viernes", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    // Abreviatura de 4 letras "mierc"/"miérc" (estándar de calendarios dominicanos
    // para miércoles, que evita la ambigüedad de "mie"). Antes el rewriter solo
    // conocía 3 letras ("mie"/"mié") → "mierc" no se expandía y corrompía la
    // captura de rutinas ("gym lun mierc vier" → recur=NONE, días perdidos).
    @Test fun parsesFourLetterAbbrevMierc() {
        val result = NaturalTaskParser.parse("Gym lun mierc vier", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    @Test fun parsesFourLetterAbbrevMiercAccented() {
        val result = NaturalTaskParser.parse("Gym miérc vie", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("3,5", result.recurrenceDays)
    }

    // "vier" es la abreviatura de 4 letras de viernes (análoga a "mierc"). Antes
    // no estaba en el mapa → se quedaba como residuo en el título y rompía la lista.
    @Test fun parsesFourLetterAbbrevVier() {
        val result = NaturalTaskParser.parse("Clase mie vier", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("3,5", result.recurrenceDays)
    }

    // La expansión de "mierc"/"vier" NO debe tocar el nombre completo "miércoles"/
    // "viernes": el lookahead de no-letra tras la coincidencia lo impide. Si se
    // corrompiera, el título quedaría roto (regresión histórica del \b ASCII).
    @Test fun fourLetterAbbrevDoesNotCorruptFullWeekday() {
        val result = NaturalTaskParser.parse("Reunión miércoles y viernes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("3,5", result.recurrenceDays)
    }

    // La abreviatura "mié" (con tilde) también debe expandirse, y NUNCA debe
    // tocar "miércoles" completo (regresión: el \b ASCII trató "é" como no-palabra
    // y "mié" casaba dentro de "miércoles" → "miércolesrcoles").
    @Test fun abbrevExpansionDoesNotCorruptFullWeekday() {
        val result = NaturalTaskParser.parse("Regar plantas los lunes miércoles y viernes", now, zone)
        assertEquals("Regar plantas", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,3,5", result.recurrenceDays)
    }

    // "mar" NO debe expandirse a martes: colisiona con la abreviatura de mes
    // marzo ("pago el 5 de mar"). La fecha de marzo debe preservarse intacta.
    // "now" = 29-jul-2026, así que el 5 de marzo siguiente cae en 2027.
    @Test fun abbrevMarIsMarchNotTuesday() {
        val result = NaturalTaskParser.parse("Pago el 5 de mar", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2027, 3, 5), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // c.360: "penúltimo/antepenúltimo (weekday) del mes" recurrente. Antes el
    // regex de último-día-de-la-semana-del-mes NO capturaba penúltimo/antepenúltimo
    // (sólo el weekday) → fecha errónea y título sucio; y aunque se capturase el
    // ordinal, no se mapeaba a recurrenceDays (caía a '' → motor derivaba). Ahora
    // el ordinal -2/-3 se persiste simétrico a -1.
    @Test fun penultimoLunesDelMesParsesMonthlyOrdinalPenultimate() {
        // now=2026-07-29. "el penúltimo lunes del mes" recurrente → penúltimo lunes
        // de ago 2026 = 24-ago (lunes ago: 3,10,17,24,31 → penúltimo=24).
        val result = NaturalTaskParser.parse("reunión el penúltimo lunes del mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals("-2:1", result.recurrenceDays)
        assertNotNull(result.dueAt)
        assertEquals(java.time.DayOfWeek.MONDAY, DateRules.toLocalDate(result.dueAt!!, zone).dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antepenultimoViernesDelMesParsesMonthlyOrdinalAntepenultimate() {
        // "el antepenúltimo viernes del mes" recurrente → antepenúltimo viernes de
        // ago 2026 = 14-ago (viernes ago: 7,14,21,28 → antepenúltimo=14).
        val result = NaturalTaskParser.parse("cobro el antepenúltimo viernes del mes", now, zone)
        assertEquals("cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-3:5", result.recurrenceDays)
        assertEquals(java.time.DayOfWeek.FRIDAY, DateRules.toLocalDate(result.dueAt!!, zone).dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaMesElPenultimoLunesParsesRecurringPenultimate() {
        // "cada mes el penúltimo lunes" (cadencia precedente) → mismo anclaje -2:1.
        val result = NaturalTaskParser.parse("reunión cada mes el penúltimo lunes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("-2:1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // P1 evitar-olvidos: ordinales NUMÉRICOS antes de un weekday ("el 3er viernes
    // del mes", "el 1er lunes de cada mes"). Antes los patrones ordinales-mensuales
    // sólo reconocían la forma escrita ("tercer/primer"), así que la numérica NO
    // anclaba la recurrencia mensual al weekday (rec=NONE o MONTHLY al día del mes
    // → deriva silenciosa del weekday) y el título quedaba corrupto con el residuo
    // "el 3er del mes". Se pre-normaliza el ordinal numérico a su palabra canónica
    // sólo cuando va seguido de un día de la semana (contexto inequívoco), reutilizando
    // todo el flujo mensual existente. Formas masculinas (3er/1er/2do/4to) y femeninas
    // (3ra/1ra/2da/4ta).
    @Test fun ordinalNumericoTercerViernesDelMesAnclaMonthlyWeekday() {
        val result = NaturalTaskParser.parse("reunión el 3er viernes del mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("3:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        // 3er viernes de julio = 2026-07-17 (ya pasó con now=2026-07-29) → avanza a
        // 3er viernes de agosto = 2026-08-21, nunca en pasado.
        assertEquals(LocalDate.of(2026, 8, 21), due)
    }

    @Test fun ordinalNumericoFemeninoTercerViernesDelMesAnclaMonthlyWeekday() {
        val result = NaturalTaskParser.parse("reunión el 3ra viernes del mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("3:5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 8, 21), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ordinalNumericoPrimerLunesDeCadaMesAnclaMonthlyWeekday() {
        val result = NaturalTaskParser.parse("renta el 1er lunes de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("1:1", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.MONDAY, due.dayOfWeek)
        // 1er lunes de julio = 2026-07-06 (ya pasó) → 1er lunes de agosto = 2026-08-03.
        assertEquals(LocalDate.of(2026, 8, 3), due)
    }

    @Test fun ordinalNumericoSegundoMartesDeCadaMesAnclaMonthlyWeekday() {
        val result = NaturalTaskParser.parse("cita el 2do martes de cada mes", now, zone)
        assertEquals("cita", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("2:2", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.TUESDAY, due.dayOfWeek)
        // 2do martes de julio = 2026-07-14 (ya pasó) → 2do martes de agosto = 2026-08-11.
        assertEquals(LocalDate.of(2026, 8, 11), due)
    }

    @Test fun ordinalNumericoCuartoJuevesDelMesAnclaMonthlyWeekday() {
        val result = NaturalTaskParser.parse("pago el 4to jueves del mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("4:4", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.THURSDAY, due.dayOfWeek)
        // 4to jueves de julio = 2026-07-23 (ya pasó) → 4to jueves de agosto = 2026-08-27.
        assertEquals(LocalDate.of(2026, 8, 27), due)
    }

    // ─── Ordinal mensual "quinto" (5ª ocurrencia, c.575) ──────────────────
    // "el quinto viernes del mes": ord=5 SÓLO existe en meses cuyo día 1 cae en
    // viernes o antes y tienen 31 días. El parser debe capturarlo (no contaminar el
    // título) y anclar MONTHLY con codificación "5:weekday". Cuando el mes actual
    // NO tiene 5ª ocurrencia, avanza al próximo mes que sí (sin rodar al día 35 ni
    // colapsar al 1er weekday del mes siguiente). Regresión P1: antes "quinto" no
    // estaba en ningún patrón ordinal → el título quedaba corrupto y la fecha era
    // la del día del mes o del 1er weekday del mes siguiente (fecha equivocada).
    @Test fun quintoViernesDelMesAnclaMonthlyOrdinalCinco() {
        // now=2026-07-29. Julio 2026 (1=Mié) tiene 5 viernes: 3,10,17,24,31 → 5º =
        // 31-jul (futuro). Recurrence MONTHLY anclado a "5:5".
        val result = NaturalTaskParser.parse("reunión el quinto viernes del mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("5:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 31), due)
    }

    @Test fun quintoViernesDeCadaMesAvanzaAlMesConQuintaOcurrencia() {
        // now=2026-07-29 pero el 5º viernes de julio (31-jul) YA venía; para forzar el
        // salto al próximo mes con 5º viernes, se parte de agosto (sin 5º viernes):
        // ago 2026 (1=Sáb) viernes=7,14,21,28 (4) → NO 5º. Próximo mes con 5º viernes =
        // octubre 2026 (1=Jue, viernes 2,9,16,23,30) → 30-oct. Se emite "el quinto
        // viernes de cada mes" y se verifica que NO ruede al 1er viernes de septiembre.
        val result = NaturalTaskParser.parse("reunión el quinto viernes de cada mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("5:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        // El parser parte de julio (mes en curso), cuyo 5º viernes (31-jul) aún no ha
        // llegado → dueAt = 31-jul. NO debe rodar a agosto ni a septiembre.
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 31), due)
    }

    @Test fun quintoViernesDelMesPasadoMantieneFechaPasadaHonest() {
        // "el quinto viernes del mes pasado": junio 2026 (1=Lun, viernes=5,12,19,26 →
        // 4 viernes) NO tiene 5º viernes. El parser avanza mes a mes hasta hallar uno
        // con 5º viernes, pero como es "mes pasado" (fecha PASADA explícita), el
        // avance se hace desde el mes anterior (junio) hacia adelante sólo para
        // encontrar una fecha que cumpla "5º viernes" y que sea HONESTA (no se inventa
        // un 5º viernes en junio que no existe). El resultado debe ser un viernes, no
        // contaminar el título y NO promoverse a MONTHLY (fecha única vencida).
        val result = NaturalTaskParser.parse("pago el quinto viernes del mes pasado", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        // Es una fecha vencida honesta (≤ now=2026-07-29) y cae en viernes.
        assertTrue("5º viernes del mes pasado debe ser fecha pasada honesta", !due.isAfter(LocalDate.of(2026, 7, 29)))
    }

    // No-regresión: ordinal numérico NO seguido de día de la semana es contenido, no
    // se normaliza ("ver el 3er capítulo", "comprar 2do piso") — no debe producir
    // fecha/recurrencia espurias ni alterar el título.
    @Test fun ordinalNumericoSinWeekdayEsContenidoSinTocar() {
        val r1 = NaturalTaskParser.parse("ver el 3er capítulo", now, zone)
        assertEquals("ver el 3er capítulo", r1.title)
        assertEquals(RecurrenceFrequency.NONE, r1.recurrence)
        assertNull(r1.dueAt)
        val r2 = NaturalTaskParser.parse("comprar 2do piso", now, zone)
        assertEquals("comprar 2do piso", r2.title)
        assertEquals(RecurrenceFrequency.NONE, r2.recurrence)
        assertNull(r2.dueAt)
        val r3 = NaturalTaskParser.parse("ver el 5to capítulo", now, zone)
        assertEquals("ver el 5to capítulo", r3.title)
        assertEquals(RecurrenceFrequency.NONE, r3.recurrence)
        assertNull(r3.dueAt)
    }

    // Ordinal numérico "5to" antes de un weekday ("el 5to viernes del mes"): forma
    // cotidiana equivalente a la escrita "quinto" (c.575). El motor ya la soporta
    // (ord=5 con salto de meses sin 5ª ocurrencia, c.575); antes el normalizador
    // numérico se limitaba a 1-4 por un comentario obsoleto ("el motor no lo mapea")
    // y "el 5to viernes" se agendaba MAL: caía al próximo viernes suelto con rec=NONE
    // y residuo "el 5to del mes" en el título (compromiso agendado en fecha errónea).
    @Test fun ordinalNumericoQuintoViernesDelMesAnclaMonthlyOrdinalCinco() {
        // Simétrico a quintoViernesDelMesAnclaMonthlyOrdinalCinco (forma escrita):
        // julio 2026 tiene 5 viernes (3,10,17,24,31) → due 2026-07-31 futuro.
        val result = NaturalTaskParser.parse("reunión el 5to viernes del mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("5:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 31), due)
    }

    @Test fun ordinalNumericoQuintoViernesDeCadaMesAnclaMonthlyOrdinalCinco() {
        // Cadencia explícita tras el ordinal ("el 5to viernes de cada mes"): mismo
        // anclaje que la forma escrita, sin rodar al 1er viernes del mes siguiente.
        val result = NaturalTaskParser.parse("reunión el 5to viernes de cada mes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals("5:5", result.recurrenceDays)
        val due = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, due.dayOfWeek)
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

    // c.583 — rutina anual anclada a una fecha de calendario concreta con "cada":
    // "renovar suscripción anual cada 1 de enero" / "pago anual cada 15 de marzo".
    // Antes la rama MONTHLY "cada N" (día-del-mes) reclamaba "cada 1" y devolvía
    // MONTHLY + due=1 de septiembre (ignoraba "enero" y "anual"): la rutina anual
    // nacía MENSUAL (12× más frecuente) y anclada al mes equivocado (P1: recordatorio
    // erróneo, fecha olvidada). Con señal YEARLY explícita + día+mes nombrado, la
    // recurrencia debe ser YEARLY anclada a ese día/mes (1 de enero, 15 de marzo).
    @Test fun anualCadaDiaDeMesNombradoParsesYearlyAnchoredToThatDate() {
        val result = NaturalTaskParser.parse("Renovar suscripción anual cada 1 de enero", now, zone)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun anualCadaDiaDeMesNombradoMidYearDateParsesYearlyAnchored() {
        val result = NaturalTaskParser.parse("Pago anual cada 15 de marzo", now, zone)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(LocalDate.of(2027, 3, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "cada 1 de enero" (con "cada" pero sin "anual") es una repetición anual cotidiana
    // (aniversario, cumpleaños, renovación): la palabra "cada" expresa la recurrencia y
    // la fecha de calendario fija el anclaje. Antes caía a MONTHLY + 1 de septiembre.
    @Test fun cadaDiaDeMesNombradoParsesYearlyAnchoredToThatDate() {
        val result = NaturalTaskParser.parse("Renovar suscripción cada 1 de enero", now, zone)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun cadaDiaDeMesNombradoCleansMonthFromTitle() {
        val result = NaturalTaskParser.parse("Renovar suscripción cada 1 de enero", now, zone)
        assertEquals("Renovar suscripción", result.title)
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

    // Genitivo "del"/"de" introductor de la fecha mensual ("renta del 15 de cada mes"):
    // antes monthlyDayPattern casaba "15 de cada mes" PERO NO consumía el "del"/"de"
    // inmediatamente anterior, que sobrevivía como residuo del título ("renta del"). El
    // genitivo introduce el MODIFICADOR TEMPORAL, no el contenido; dejarlo degrada la
    // captura de los compromisos periódicos más cotidianos (renta/pago/factura/cuota).
    // Simétrico del fix c.448 para fechas con nombre de mes.
    @Test fun rentaDel15DeCadaMesLimpiaGenitivoDel() {
        val result = NaturalTaskParser.parse("renta del 15 de cada mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun pagoDel30DeCadaMesLimpiaGenitivoDel() {
        val result = NaturalTaskParser.parse("pago del 30 de cada mes", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rentaDel15DelMesLimpiaGenitivoDel() {
        val result = NaturalTaskParser.parse("renta del 15 del mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "de" (sin contracción) como genitivo introductor: forma menos frecuente pero
    // válida ("cuota de 5 de cada mes").
    @Test fun cuotaDe5DeCadaMesLimpiaGenitivoDe() {
        val result = NaturalTaskParser.parse("cuota de 5 de cada mes", now, zone)
        assertEquals("cuota", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // El genitivo se consume sólo cuando introduce la fecha mensual: el "del" de
    // contenido ("cuenta del banco del 15 de cada mes") se respeta y sólo se limpia el
    // "del" que introduce el día.
    @Test fun cuentaDelBancoDel15DeCadaMesConservaContenido() {
        val result = NaturalTaskParser.parse("cuenta del banco del 15 de cada mes", now, zone)
        assertEquals("cuenta del banco", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // No-regresión: la forma SIN genitivo ("el 15 de cada mes") sigue igual.
    @Test fun el15DeCadaMesSinGenitivoSigueLimpio() {
        val result = NaturalTaskParser.parse("Pagar la cuenta el 15 de cada mes", now, zone)
        assertEquals("Pagar la cuenta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Cobertura de cadencia mensual: "del N de todos los meses", "del N todos los
    // meses", "del N mensual", "del N mensualmente" son formas cotidianas de los
    // compromisos periódicos más frecuentes (renta/pago/factura/cuota). Antes estas
    // cadencias NO casaban monthlyDayPattern (que sólo admitía "de/del mes"/"de cada
    // mes"), así el día N caía sin anclaje → el vencimiento se programaba HOY
    // (incorrecto) y la recurrencia MONTHLY nacía SIN saber qué día repetir
    // (recurrenceDays='' → el motor derivaba al día de captura). Un compromiso
    // periódico nacía con fecha Y recurrencia incorrectas. Simétrico de "del N de
    // cada mes": la cadencia se reconoce y ancla al día N del mes.
    @Test fun rentaDel15DeTodosLosMesesAnclaAlDia15() {
        val result = NaturalTaskParser.parse("renta del 15 de todos los meses", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rentaDel15TodosLosMesesAnclaAlDia15() {
        val result = NaturalTaskParser.parse("renta del 15 todos los meses", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rentaDel15MensualAnclaAlDia15() {
        val result = NaturalTaskParser.parse("renta del 15 mensual", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun pagoDel30MensualmenteAnclaAlDia30() {
        val result = NaturalTaskParser.parse("pago del 30 mensualmente", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // c.493: Genitivo "de/del" externo introductor de una FRASE DE RECURRENCIA pura
    // (sin día anclado): "Resumen de cada mes", "Balance de todos los meses",
    // "Cobro de cada mes". La frase la detectan fixedPatterns (no monthlyDayPattern,
    // que exige un día N), así que el genitivo no se consumía por la lógica de anclaje
    // y sobrevivía como residuo colgante del título ("Resumen de"). El genitivo
    // introduce el modificador temporal, no el contenido. Simétrico de los fixes de
    // genitivo para fechas ancladas (c.448/c.316) y de todos los sitios de período
    // (fin de mes, la quincena). Aquí se cubre la limpieza vía el loop de phraseRanges.
    @Test fun genitiveBeforeCadaMesIsCleaned() {
        val result = NaturalTaskParser.parse("Resumen de cada mes", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun genitiveBeforeTodosLosMesesIsCleaned() {
        val result = NaturalTaskParser.parse("Balance de todos los meses", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun genitiveBeforeCadaBimestreIsCleaned() {
        val result = NaturalTaskParser.parse("Cobro de cada bimestre", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
    }

    @Test fun genitiveBeforeCadaSemestreIsCleaned() {
        val result = NaturalTaskParser.parse("Nomina de cada semestre", now, zone)
        assertEquals("Nomina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
    }

    @Test fun genitiveBeforeCadaQuincenaIsCleaned() {
        val result = NaturalTaskParser.parse("Resumen de cada quincena", now, zone)
        assertEquals("Resumen", result.title)
    }

    @Test fun genitiveBeforeFinDeQuincenaIsCleaned() {
        val result = NaturalTaskParser.parse("Pago de fin de quincena", now, zone)
        assertEquals("Pago", result.title)
    }

    // No-regresión: el genitivo de CONTENIDO antes de una frase de recurrencia se
    // respeta. "reunión del equipo cada mes" → "reunión del equipo" (no "reunión"):
    // el "del" introduce el equipo (contenido), no la recurrencia. La limpieza sólo
    // consume el conector inmediatamente anterior a la frase temporal.
    @Test fun contentGenitiveBeforeRecurrenceIsPreserved() {
        val result = NaturalTaskParser.parse("reunión del equipo cada mes", now, zone)
        assertEquals("reunión del equipo", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun contentGenitiveDelBancoBeforeRecurrenceIsPreserved() {
        val result = NaturalTaskParser.parse("cuenta del banco cada mes", now, zone)
        assertEquals("cuenta del banco", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
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

    // --- Infinitivos de recordatorio con clítico (c.447) ---
    // "recordarme"/"avisarme"/"notificarme"/"acordarme de" son la forma cotidiana en
    // infinitivo de pedir un recordatorio ("recordarme llamar al dentista mañana"),
    // simétrica al imperativo "recuérdame". Antes NO se reconocían: el verbo quedaba
    // como residuo en el título y el recordatorio NUNCA se programaba (la cita se
    // olvidaba pese a pedirse expresamente, P1). Ahora se limpia el verbo y se aplica
    // el offset de respaldo (30 min) cuando hay fecha límite.
    @Test fun infinitivoRecordarmeConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("recordarme llamar al dentista mañana a las 9", now, zone)
        assertEquals("llamar al dentista", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun infinitivoAvisarmeConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("avisarme pagar la luz el viernes", now, zone)
        assertEquals("pagar la luz", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun infinitivoNotificarmeConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("notificarme enviar el reporte el lunes a las 10", now, zone)
        assertEquals("enviar el reporte", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun infinitivoAcordarmeDeConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("acordarme de llamar a mi hermana el sábado", now, zone)
        assertEquals("llamar a mi hermana", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun verboNoDejesQueOlvideConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("no dejes que olvide llamar al doctor mañana", now, zone)
        assertEquals("llamar al doctor", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // --- Subordinador "que" tras verbo de recordatorio ---
    // "recuérdame que X" = "recuérdame X": el "que" es subordinador puro (el verbo
    // envolvente ya se consumió), no contenido. Antes sobrevivía como residuo al
    // inicio del título ("que tengo clases"), degradando la captura (verbo = acción
    // oculta tras un conector vacío). Simétrico de la limpieza del verbo mismo.
    @Test fun recuerdameQueSubordinadorLimpiaTitulo() {
        val result = NaturalTaskParser.parse("recuérdame que tengo clases mañana", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("tengo clases", result.title)
    }

    @Test fun recuerdameQueDeboLimpiaTitulo() {
        val result = NaturalTaskParser.parse("recuérdame que debo llamar a mamá mañana", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("debo llamar a mamá", result.title)
    }

    // El subordinador se borra sólo cuando queda contenido real tras él: si el
    // título quedara vacío, se conserva lo original (honesto, sin inventar).
    @Test fun recuerdameQueSinContenidoConservaQue() {
        val result = NaturalTaskParser.parse("recuérdame que mañana", now, zone)
        assertNotNull(result.dueAt)
        assertTrue(result.title.isNotBlank())
    }

    // Sin verbo de recordatorio, un "que" inicial NO se toca: es contenido legítimo.
    @Test fun queInicialSinVerboSeConserva() {
        val result = NaturalTaskParser.parse("que sea honesto el informe mañana", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("que sea honesto el informe", result.title)
    }

    // --- "mándame/envíame + sustantivo de aviso" (c.471) ---
    // "mándame un recordatorio"/"envíame una alerta" son peticiones explícitas de aviso,
    // tan cotidianas como "recuérdame". Antes NO se reconocían: el verbo sobrevivía como
    // residuo del título y reminderOffset=null (el recordatorio NUNCA se programaba pese a
    // pedirse expresamente → olvido). A diferencia de "recuérdame" (inequívoco), estos
    // verbos son de acción por sí solos ("envíame un correo"), así que solo cuentan como
    // aviso cuando van seguidos de un sustantivo de aviso (recordatorio/alerta/aviso/
    // notificación). El clítico `-me` + sustantivo de aviso los vuelve inequívocos.
    @Test fun mandameRecordatorioConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("mandame un recordatorio mañana a las 9", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun enviameAlertaConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("enviame una alerta el viernes a las 10", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun mandameAvisoConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("mandame un aviso pasado mañana a las 8", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun enviameNotificacionConDueAplicaOffset30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("enviame una notificacion el lunes a las 7 am", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // --- "ponme/dame + sustantivo de aviso" (c.476) ---
    // "ponme un aviso"/"dame un recordatorio" son peticiones explícitas de aviso tan
    // cotidianas como "mándame/envíame" (c.471), pero NO se reconocían: el verbo
    // sobrevivía como residuo del título y, con fecha, reminderOffset=null (el
    // recordatorio NUNCA se programaba pese a pedirse expresamente → olvido). Mismo
    // patrón que c.471: solo cuentan como aviso con sustantivo (aviso/recordatorio/
    // alerta/notificación) vía lookahead, porque "dame"/"ponme" son verbos de acción
    // por sí solos ("dame el documento", "ponme el libro").
    @Test fun ponmeAvisoConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("ponme un aviso el viernes a las 10", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun dameRecordatorioConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("dame un recordatorio mañana a las 9", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun dameAlertaConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("dame una alerta pasado mañana a las 8", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun ponmeNotificacionConDueAplicaOffset30() {
        val result = NaturalTaskParser.parse("ponme una notificacion el lunes a las 7 am", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // Sin sustantivo de aviso, "dame"/"ponme" son de acción: NO debe falsificar aviso.
    @Test fun dameDocumentoSinSustantivoDeAvisoNoProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("dame el documento el viernes", now, zone)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun ponmeLibroSinSustantivoDeAvisoNoProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("ponme el libro mañana a las 9", now, zone)
        assertNull(result.reminderOffsetMinutes)
    }

    // Sin sustantivo de aviso, el verbo es de acción (no aviso): NO debe falsificar
    // recordatorio. "envíame un correo" es contenido real, no petición de aviso.
    @Test fun enviameCorreoSinSustantivoDeAvisoNoProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("enviame un correo a juan mañana a las 9", now, zone)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun mandameDocumentoSinSustantivoDeAvisoNoProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("mandame el documento el viernes", now, zone)
        assertNull(result.reminderOffsetMinutes)
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

    // --- Sustantivo "recordatorio" como contenido, no petición (c.403) ---
    // El sustantivo "recordatorio" precedido de artículo/posesivo es contenido real
    // ("leer el recordatorio del profesor"), NO una petición de aviso. Antes el
    // bareReminderVerbPattern lo blanqueaba mutilando el título ("leer el del profesor").
    // Ahora un negative lookbehind lo permite solo al inicio/tras puntuación (petición),
    // no tras determinante (contenido). Título limpio + sin recordatorio falso.
    @Test fun leerElRecordatorioDelProfesorNoSeMutila() {
        val result = NaturalTaskParser.parse("Leer el recordatorio del profesor", now, zone)
        assertEquals("Leer el recordatorio del profesor", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun borrarElRecordatorioViejoNoSeMutila() {
        val result = NaturalTaskParser.parse("Borrar el recordatorio viejo", now, zone)
        assertEquals("Borrar el recordatorio viejo", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun unRecordatorioDeLaCitaEsContenido() {
        val result = NaturalTaskParser.parse("Un recordatorio de la cita", now, zone)
        assertEquals("Un recordatorio de la cita", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun miRecordatorioDelDentistaEsContenido() {
        val result = NaturalTaskParser.parse("Mi recordatorio del dentista", now, zone)
        assertEquals("Mi recordatorio del dentista", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    // --- Regresión c.403: "recordatorio" al inicio SIGUE siendo petición (no se rompe) ---
    @Test fun recordatorioAlInicioConDueSigueSiendoPeticion() {
        val result = NaturalTaskParser.parse("recordatorio llamar a mamá mañana", now, zone)
        assertEquals("llamar a mamá", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun recordatorioConDosPuntosSigueSiendoPeticion() {
        val result = NaturalTaskParser.parse("recordatorio: pagar la luz el viernes", now, zone)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
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

    // c.514 — cuando "a las N" es una CUENTA (sustantivo plural tras hora en punto sin
    // evidencia de reloj), el número N es una CANTIDAD, no una hora: debe conservarse en el
    // título. Antes la limpieza del título borraba TODO match de timePatterns, así que
    // "llamar a las 3 cajas" → "llamar cajas" (se perdía la cantidad 3). Esto es pérdida de
    // datos del usuario.
    @Test fun cuentaConservaNumeroEnTitulo() {
        val r = NaturalTaskParser.parse("llamar a las 3 cajas", now, zone)
        assertNull("una cuenta no debe agendar una cita falsa", r.dueAt)
        assertEquals("la cantidad 3 debe conservarse en el título", "llamar a las 3 cajas", r.title)
    }

    @Test fun cuentaConservaNumeroEnTituloLlantas() {
        val r = NaturalTaskParser.parse("revisar a las 3 llantas", now, zone)
        assertNull(r.dueAt)
        assertEquals("revisar a las 3 llantas", r.title)
    }

    // c.514 (olvido de cita) — si el PRIMER match de timePatterns es una cuenta, el parser
    // debe saltar al SIGUIENTE match válido en vez de descartar toda hora. Antes el guard
    // anti-cuenta rechazaba el primer match y NO buscaba el siguiente: "enviar a las 5
    // invitaciones a las 9" → dueAt=null, la cita real a las 9 se OLVIDABA.
    @Test fun citaTrasCuentaNoSeOlvida() {
        val r = NaturalTaskParser.parse("enviar a las 5 invitaciones a las 9", now, zone)
        assertNotNull("la cita real 'a las 9' no debe olvidarse tras una cuenta", r.dueAt)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals("la cantidad 5 debe conservarse en el título", "enviar a las 5 invitaciones", r.title)
    }

    @Test fun citaTrasCuentaPreservaCantidadYAgendaHoraReal() {
        val r = NaturalTaskParser.parse("reunión a las 3 cajas a las 5", now, zone)
        assertNotNull("la cita real 'a las 5' no debe olvidarse", r.dueAt)
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals("reunión a las 3 cajas", r.title)
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

    // c.532 — el conector de plazo "hasta" + "las N <sustantivo plural de cantidad>" es una
    // CUENTA (límite "hasta las 5 cajas" = un máximo de 5 cajas), NO una cita. Antes el rewrite
    // "hasta las N"→"a las N" disparaba SIEMPRE sin mirar el tail: el número y el sustantivo sí
    // se preservaban (vía timeMatchIsCountNoun c.514) PERO el conector "hasta" se corrompía a
    // "a", perdiendo el sentido de límite ("entregar hasta las 5 cajas" → "entregar a las 5
    // cajas": contenido capturado degradado, P1 título limpio). Ahora el rewrite de HORA de
    // "hasta" aplica el mismo guard anti-cuenta que aPartirDe/desde (c.442): preserva "hasta
    // las N" íntegro. Simétrico de "enviar a las 5 cajas" (count) y consistente con el baseline.
    @Test fun hastaLasNPluralPreservaConectorComoCuenta() {
        val r = NaturalTaskParser.parse("entregar hasta las 5 cajas", now, zone)
        assertNull("una cuenta con 'hasta' no debe agendar una cita falsa", r.dueAt)
        assertEquals("el conector 'hasta' debe conservarse como límite de cantidad",
            "entregar hasta las 5 cajas", r.title)
    }

    @Test fun hastaLasNPersonasPreservaConectorComoCuenta() {
        val r = NaturalTaskParser.parse("llevar hasta las 10 personas", now, zone)
        assertNull(r.dueAt)
        assertEquals("llevar hasta las 10 personas", r.title)
    }

    // "hasta las N <plural> a las M" — la cuenta (hasta) se preserva y la cita real (a las M)
    // se agenda. Simétrico de "enviar a las 5 invitaciones a las 9" (c.514).
    @Test fun hastaLasNPluralMasCitaRealPreservaConectorYAgendaHora() {
        val r = NaturalTaskParser.parse("entregar hasta las 5 cajas a las 9", now, zone)
        assertNotNull("la cita real 'a las 9' no debe olvidarse tras una cuenta con 'hasta'", r.dueAt)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals("entregar hasta las 5 cajas", r.title)
    }

    // El guard del rewrite de "hasta" NO debe comerse las horas reales: las horas CON evidencia
    // de reloj (meridiem, :MM, fracción "y media", sufijo "horas", parte del día) o al final
    // siguen reescribiéndose "hasta las N"→"a las N" y resolviéndose como cita. Sin regresión.
    @Test fun hastaLasNConEvidenciaSigueSiendoCita() {
        val r1 = NaturalTaskParser.parse("trabajar hasta las 5 de la tarde", now, zone)
        assertNotNull(r1.dueAt)
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(r1.dueAt!!, zone))
        val r2 = NaturalTaskParser.parse("trabajar hasta las 5 pm", now, zone)
        assertNotNull(r2.dueAt)
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(r2.dueAt!!, zone))
        val r3 = NaturalTaskParser.parse("trabajar hasta las 5:30", now, zone)
        assertNotNull(r3.dueAt)
        assertEquals(LocalTime.of(5, 30), DateRules.toLocalTime(r3.dueAt!!, zone))
        val r4 = NaturalTaskParser.parse("trabajar hasta las 5 y media", now, zone)
        assertNotNull(r4.dueAt)
        assertEquals(LocalTime.of(5, 30), DateRules.toLocalTime(r4.dueAt!!, zone))
        // "horas" es unidad horaria (evidencia de reloj), no sustantivo de cantidad → cita.
        val r5 = NaturalTaskParser.parse("trabajar hasta las 5 horas", now, zone)
        assertNotNull(r5.dueAt)
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(r5.dueAt!!, zone))
    }

    @Test fun hastaLasNEnPuntoSigueSiendoCita() {
        val r = NaturalTaskParser.parse("trabajar hasta las 5", now, zone)
        assertNotNull("'hasta las 5' al final es cita 05:00", r.dueAt)
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(r.dueAt!!, zone))
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

    // "una media hora" es la forma con artículo de "media hora" (igual que "un cuarto de
    // hora" admite "un"): "reunión una media hora" dejaba "una" como residuo en el título
    // aunque la duración (30) SÍ se resolvía. El artículo es parte de la frase de duración,
    // no contenido del título. (c.385)
    @Test fun unaMediaHoraEsDuracionDe30MinSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión una media hora", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun unaMediaHoraLimpiaTituloConAction() {
        val result = NaturalTaskParser.parse("Llamada una media hora", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun unaMediaHoraNoInterfiereConFechaYHora() {
        val result = NaturalTaskParser.parse("Estudiar una media hora mañana a las 3pm", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "una media hora" como fecha relativa y recordatorio (c.385) ---
    // El artículo "una" también es parte de la frase fraccionaria en los contextos de
    // fecha relativa y recordatorio (simétrico de "un cuarto de hora" que SÍ admite "un"):
    // "en una media hora" debe ser fecha relativa (+30, dueAt NO nulo), NO duración; y
    // "una media hora antes"/"recuérdame una media hora de anticipación" deben consumir
    // "una" para no dejar residuo en el título.

    @Test fun enUnaMediaHoraEsFechaRelativaNoDuracion() {
        val result = NaturalTaskParser.parse("Reunión en una media hora", now, zone)
        assertEquals("Reunión", result.title)
        // Es fecha relativa: dueAt = ahora + 30 min (NO nulo), no duración.
        assertEquals(now + 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeUnaMediaHoraEsFechaRelativa() {
        val result = NaturalTaskParser.parse("Llamar dentro de una media hora", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 30 * 60_000L, result.dueAt)
    }

    @Test fun unaMediaHoraAntesEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita una media hora antes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        // No es duración: el artículo "una media hora" se consume como recordatorio.
        assertNull(result.durationMinutes)
    }

    @Test fun recuerdameUnaMediaHoraDeAnticipacion() {
        val result = NaturalTaskParser.parse("Cita recuérdame una media hora de anticipación", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun haceUnaMediaHoraEsFechaPasada() {
        val result = NaturalTaskParser.parse("Llamé hace una media hora", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(now - 30 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
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

    // c.501: prefijo "durante"/"por" + cantidad en DÍGITOS + fracción. Antes
    // [compoundFractionalDurationPattern] no admitía el prefijo, de modo que en
    // "durante 1 hora y media" el [durationPatterns] simple casaba "durante 1 hora"
    // (desde la posición 0) y el compuesto solo "1 hora y media" (posición 8); el
    // tie-break por posición elegía el simple → 60 min en vez de 90 y "y media"
    // quedaba como residuo en el título. El prefijo ahora es no capturante y opcional.
    @Test fun duranteDigitosHoraYMediaEsDuracion90() {
        val result = NaturalTaskParser.parse("Descansar durante 1 hora y media", now, zone)
        assertEquals("Descansar", result.title)
        assertEquals(90, result.durationMinutes)
    }

    @Test fun porDigitosHorasYMediaEsDuracion150() {
        val result = NaturalTaskParser.parse("Descansar por 2 horas y media", now, zone)
        assertEquals("Descansar", result.title)
        assertEquals(150, result.durationMinutes)
    }

    @Test fun duranteDigitosHoraYCuartoEsDuracion75() {
        val result = NaturalTaskParser.parse("Descansar durante 1 hora y cuarto", now, zone)
        assertEquals("Descansar", result.title)
        assertEquals(75, result.durationMinutes)
    }

    @Test fun duranteDigitosHoraYTresCuartosEsDuracion105() {
        val result = NaturalTaskParser.parse("Descansar durante 1 hora y tres cuartos", now, zone)
        assertEquals("Descansar", result.title)
        assertEquals(105, result.durationMinutes)
    }

    // c.497: "para el" ante un SUSTANTIVO DE CONTENIDO (no un ancla temporal) es
    // destinatario/propósito y debe conservarse íntegro en el título. Antes el paso de
    // limpieza borraba "para el" incondicionalmente y mutilaba títulos
    // ("Estudiar para el examen" → "Estudiar examen"). P1: integridad de datos.
    @Test fun paraElAnteSustantivoDeContenidoSeConserva() {
        val result = NaturalTaskParser.parse("Estudiar para el examen", now, zone)
        assertEquals("Estudiar para el examen", result.title)
    }

    @Test fun paraElAnteContenidoConFechaSeConserva() {
        val result = NaturalTaskParser.parse("Estudiar para el examen el 20", now, zone)
        assertEquals("Estudiar para el examen", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun paraElAnteContenidoCumpleanosSeConserva() {
        val result = NaturalTaskParser.parse("Comprar regalo para el cumpleaños", now, zone)
        assertEquals("Comprar regalo para el cumpleaños", result.title)
    }

    @Test fun paraElAnteAnclaTemporalSeEliminaComoResiduo() {
        val result = NaturalTaskParser.parse("Reunión para el lunes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun paraElAnteAnclaTemporalConContenidoPrevioSeConserva() {
        val result = NaturalTaskParser.parse("Preparar para el lunes el examen", now, zone)
        assertEquals("Preparar para el examen", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
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
        assertEquals("Estudiar para el examen", result.title)
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

    // El genitivo "del" que introduce una fecha de nombre de mes debe consumirse con la
    // fecha y no sobrevivir como residuo en el título. Antes "concierto del 12 de
    // octubre" dejaba "concierto del"; ahora deja "concierto" (contenido limpio).
    // "para el concierto" es contenido (destinatario/propósito), NO residuo temporal:
    // el conector "para el" sólo se elimina ante un ancla temporal ya consumida
    // ("para el lunes"); aquí "el concierto" es un sustantivo de contenido y se
    // conserva íntegro (c.497).
    @Test fun genitivoDelAntesDeFechaDeMesSeConsumeDelTitulo() {
        val result = NaturalTaskParser.parse("comprar entradas para el concierto del 12 de octubre", now, zone)
        assertEquals("comprar entradas para el concierto", result.title)
        assertEquals(LocalDate.of(2026, 10, 12), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.498: "hasta el" / "antes del" ante un SUSTANTIVO DE CONTENIDO se mutilaban
    // (igual que "para el" en c.497). El paso de limpieza incondicional borraba el
    // conector sin distinguir si lo seguía un ancla temporal (ya consumida) o contenido.
    // "Estudiar hasta el examen" → "Estudiar examen" (P1: integridad de datos). Ahora el
    // borrado del conector sólo ocurre al FINAL del título (fecha ya consumida); ante
    // contenido se conserva íntegro.
    @Test fun hastaElAntesSustantivoContenidoSeConserva() {
        val r1 = NaturalTaskParser.parse("Estudiar hasta el examen", now, zone)
        assertEquals("Estudiar hasta el examen", r1.title)
        assertNull(r1.dueAt)
        val r2 = NaturalTaskParser.parse("Preparar antes del examen", now, zone)
        assertEquals("Preparar antes del examen", r2.title)
        assertNull(r2.dueAt)
        val r3 = NaturalTaskParser.parse("Leer hasta el capitulo 5", now, zone)
        assertEquals("Leer hasta el capitulo 5", r3.title)
        assertNull(r3.dueAt)
        val r4 = NaturalTaskParser.parse("Trabajar hasta el final del proyecto", now, zone)
        assertEquals("Trabajar hasta el final del proyecto", r4.title)
        assertNull(r4.dueAt)
    }

    // c.498 no-regresión: los plazos temporales con "hasta el"/"antes del" siguen
    // resolviéndose y limpiando el conector huérfano (la fecha se consume, el conector
    // queda al final del título y se elimina).
    @Test fun hastaElAntesAnclaTemporalSigueResolviendoYLimpia() {
        val r1 = NaturalTaskParser.parse("Entregar hasta el viernes", now, zone)
        assertEquals("Entregar", r1.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(r1.dueAt!!, zone))
        val r2 = NaturalTaskParser.parse("Pagar antes del viernes", now, zone)
        assertEquals("Pagar", r2.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(r2.dueAt!!, zone))
        val r3 = NaturalTaskParser.parse("Cobrar hasta el 20 de septiembre", now, zone)
        assertEquals("Cobrar", r3.title)
        assertEquals(LocalDate.of(2026, 9, 20), DateRules.toLocalDate(r3.dueAt!!, zone))
        val r4 = NaturalTaskParser.parse("pagar antes del 15 de agosto", now, zone)
        assertEquals("pagar", r4.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(r4.dueAt!!, zone))
    }

    // Cuando hay DOS "del" y sólo el último introduce la fecha ("reporte del proyecto
    // del 15 de agosto"), el primer "del" (contenido) se conserva y el segundo (fecha)
    // se consume.
    @Test fun genitivoDelDeFechaNoConsumeDelAnteriorDeContenido() {
        val result = NaturalTaskParser.parse("enviar el reporte del proyecto del 15 de agosto", now, zone)
        assertEquals("enviar el reporte del proyecto", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelEnCadenaDeServicioYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pagar la factura del servicio del 20 de septiembre", now, zone)
        assertEquals("pagar la factura del servicio", result.title)
        assertEquals(LocalDate.of(2026, 9, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // El conector de plazo "antes del <fecha>" no debe romperse: el "del" de "antes del"
    // se reserva para beforeDeadlineDayPattern / limpieza "antes del", y la fecha se
    // resuelve vía monthNameDate sin dejar "antes" huérfano.
    @Test fun antesDelConMesNoDejaAntesHuerfano() {
        val result = NaturalTaskParser.parse("pagar antes del 15 de agosto", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun antesDelConMesYContenidoNoDejaAntesHuerfano() {
        val result = NaturalTaskParser.parse("enviar el reporte antes del 5 de agosto", now, zone)
        assertEquals("enviar el reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.474: "avísame 2 horas antes de la reunión" (recordatorio con cantidad +
    // genitivo "antes de <contenido>") captura el offset correctamente PERO dejaba
    // el conector "de" huérfano en el título como 'de la reunión'. La unidad léxica
    // es "antes de": el blanqueo del recordatorio debe consumir el "de"/"del" del
    // contenido que se avisa, no dejarlo colgado. Cotidiano en español ("recuérdame
    // 30 min antes de la cita", "avísame 1 hora antes de la presentación").
    @Test fun recordatorioConAntesDeContenidoLimpiaConectorDe() {
        val result = NaturalTaskParser.parse("avisame 2 horas antes de la reunion del viernes a las 3", now, zone)
        assertEquals("la reunion", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
        assertNotNull(result.dueAt)
    }

    @Test fun recordatorioConAntesDeContenidoLimpiaConectorDeSinFecha() {
        val result = NaturalTaskParser.parse("recuerdame 30 min antes de la llamada con juan", now, zone)
        assertEquals("la llamada con juan", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun recordatorioConAntesDeContenidoSustantivadoLimpia() {
        // "reunión con aviso 15 min antes de la salida": la forma sustantivada
        // ("con aviso N antes") también arrastra el "de" del genitivo.
        val result = NaturalTaskParser.parse("reunion con aviso 15 min antes de la salida", now, zone)
        assertEquals("reunion la salida", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
    }

    // Anti-regresión: "antes de" como conector de plazo/acción SIN recordatorio no
    // debe producir títulos rotos. "antes de" sin cantidad no casa reminderPatterns,
    // así que el conector se gestiona por la limpieza existente (c.4342).
    @Test fun antesDeSinRecordatorioNoRompeTitulo() {
        val result = NaturalTaskParser.parse("leer el informe antes de la reunion a las 4", now, zone)
        // El contenido (la reunión) sigue presente; "antes de" se gestiona por
        // la rama existente. Solo verificamos que NO quede 'de la reunion' huérfano
        // por el lado del recordatorio (no aplica: sin cantidad, sin offset).
        assertEquals(null, result.reminderOffsetMinutes)
        assertTrue(result.title, result.title.contains("reunion"))
    }

    // c.805: bug P1 — "N <unidad> antes del <día numérico>" perdía el plazo entero
    // (dueAt=null y "N" como residuo; sólo el recordatorio se programaba → olvido).
    // El blanqueo c.474 consumía el conector "del" a ciegas destruyendo el ancla
    // numérica. Ahora se guarda cuando lo que sigue al conector empieza por dígito.
    @Test fun antesDelNumericoConRecordatorioNoPierdePlazo() {
        val result = NaturalTaskParser.parse("pago tres días antes del 25", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 25), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("pago", result.title)
    }

    @Test fun antesDelNumericoConRecordatorioUnDia() {
        // now=29-jul-2026: el 30 aún no pasó → ancla al propio julio (igual que
        // "el 30" suelto). Perder el plazo = el bug; el mes exacto lo define la
        // semántica vigente de `delDayOfMonthPattern` (día N de este mes o del siguiente).
        val result = NaturalTaskParser.parse("pago un día antes del 30", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("pago", result.title)
    }

    @Test fun antesDelNumericoConRecordatorioDosDia() {
        val result = NaturalTaskParser.parse("enviar el reporte dos días antes del 5", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals("enviar el reporte", result.title)
    }

    // Anti-regresión del guard: el genitivo de contenido ("antes de <sustantivo>")
    // sigue consumiendo el conector — el guard sólo protege anclas digitales.
    @Test fun antesDeContenidoSigueLimpioConElGuard() {
        val result = NaturalTaskParser.parse("avisame 2 horas antes de la reunion", now, zone)
        assertEquals(120, result.reminderOffsetMinutes)
        assertTrue(result.title, result.title.contains("reunion"))
    }

    // Anti-regresion critica: plazo puro "antes del N" (sin cantidad+unidad de
    // recordatorio) NO debe ser afectado por c.474: no casa reminderPatterns, asi
    // que su "del" sigue gestionado por la limpieza existente (c.4342) y la fecha
    // se resuelve por monthNameDate. Verifica que no rompimos los plazos.
    @Test fun antesDelPlazoPuroNoAfectadoPorC474() {
        val r1 = NaturalTaskParser.parse("pagar antes del 15 de agosto", now, zone)
        assertEquals("pagar", r1.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(r1.dueAt!!, zone))
        val r2 = NaturalTaskParser.parse("presentar informe antes del 30 de septiembre", now, zone)
        assertEquals("presentar informe", r2.title)
    }

    // c.474: "de anticipacion/de adelanto" tambien termina en token-antes y arrastra
    // el genitivo "de <contenido>" ("recuerdame 15 min de anticipacion de la cita").
    @Test fun recordatorioAnticipacionDeContenidoLimpia() {
        val result = NaturalTaskParser.parse("recuerdame 15 min de anticipacion de la cita", now, zone)
        assertEquals(15, result.reminderOffsetMinutes)
        assertFalse(result.title, result.title.contains("de la cita"))
    }

    // c.475: el patron verbal CON cantidad usaba "recuerdame" literal con tilde,
    // mientras el patron sin cantidad aceptaba tilde opcional. "recuerdame" sin
    // tilde (forma cotidiana) NO casaba el patron con cantidad -> el recordatorio
    // NUNCA se programaba (la cita se olvidaba pese a pedirse expresamente, P1).
    @Test fun recuerdameSinTildeConCantidadProgramaRecordatorio() {
        val r1 = NaturalTaskParser.parse("recuerdame 30 min antes de la reunion", now, zone)
        assertEquals(30, r1.reminderOffsetMinutes)
        val r2 = NaturalTaskParser.parse("recuerdame media hora antes de la cita", now, zone)
        assertEquals(30, r2.reminderOffsetMinutes)
        val r3 = NaturalTaskParser.parse("recuerdame 2 horas antes de la cita", now, zone)
        assertEquals(120, r3.reminderOffsetMinutes)
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

    // ── "esta madrugada": simetría con "esta mañana/tarde/noche" ──
    // Antes "esta madrugada" NO casaba [partOfDayPattern] (sólo mañana/tarde/noche):
    // caía a dueAt=null y la frase entera quedaba como residuo en el título — tarea
    // capturada de madrugada se perdía sin vencimiento ni recordatorio (P1: olvido).
    @Test fun estaMadrugadaSetsCanonicalTimeToday() {
        val result = NaturalTaskParser.parse("Reunión esta madrugada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun estaMadrugadaExplicitHourOverridesCanonicalTime() {
        val result = NaturalTaskParser.parse("Estudiar esta madrugada a las 3", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // ── Intensificador "misma" en parte del día (c.672) ──
    // "esta misma tarde/noche/mañana/madrugada" es la forma enfática de "esta tarde…"
    // (paridad con "esta misma semana/este mismo mes", ya soportada en thisWeekPattern/
    // softMonthPattern). Antes "misma" rompía [partOfDayPattern]: caía a dueAt=null y la
    // frase entera quedaba como residuo en el título — captura olvidada (P1).
    @Test fun estaMismaTardeSetsAfternoonCanonicalTime() {
        val result = NaturalTaskParser.parse("Revisión esta misma tarde", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun estaMismaNocheSetsTonightCanonicalTime() {
        val result = NaturalTaskParser.parse("Entregarlo esta misma noche", now, zone)
        assertEquals("Entregarlo", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // ── "entrando/entrada la tarde/noche" (c.672, forma caribeña) ──
    // Perífrasis común en español caribeño ("entrar la tarde/noche" ≈ al caer la
    // tarde/noche). Antes caía a dueAt=null con el residuo íntegro en el título.
    // Paridad con el conector "en la" ya admitido (forma caribeña/hispanoamericana).
    @Test fun entrandoLaTardeSetsAfternoonTime() {
        val result = NaturalTaskParser.parse("Llamar entrando la tarde", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun entradaLaNocheSetsTonightTime() {
        val result = NaturalTaskParser.parse("Cita entrada la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // ── "después de mañana" ≡ "pasado mañana" (c.846) ──
    // Forma coloquial extendidísima de "pasado mañana" (el día después de mañana).
    // Antes la "mañana" interna casaba con mananaAsDate → fecha +1 (la tarea se
    // agendaba un día ANTES de lo pedido: fecha errónea silenciosa, P1). Debe
    // comportarse exactamente como "pasado mañana": fecha +2, hora canónica,
    // título limpio, con y sin tildes, compacta con parte del día.

    @Test fun despuesDeMananaEsPasadoMananaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Entregar el informe después de mañana", now, zone)
        assertEquals("Entregar el informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeMananaSinTildeEsPasadoMananaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita despues de manana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun despuesDeMananaTardeEsPasadoManana15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita después de mañana tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeMananaNocheEsPasadoManana21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Vuelo después de mañana noche", now, zone)
        assertEquals("Vuelo", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeMananaConHoraExplicitaEsPasadoMananaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar a Ana después de mañana a las 8", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Guardas: el prefijo "después de" no invade las formas existentes.

    @Test fun despuesDeLaReunionSigueSinFecha() {
        val result = NaturalTaskParser.parse("llamar después de la reunión", now, zone)
        assertNull(result.dueAt)
    }

    @Test fun despuesAdverbioSoloSigueMasTresHoras() {
        val result = NaturalTaskParser.parse("llamar después", now, zone)
        assertEquals(now + 3 * 60 * 60_000L, result.dueAt)
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

    // --- Diminutivos coloquiales "un ratito"/"un ratico"/"un momentito"/"al ratito" ---
    // Formas extremadamente frecuentes en español latinoamericano informal ("llamar en un
    // ratito", "dentro de un momentito", "al ratito"). Antes NO casaban vagueRelative →
    // dueAt=null y la tarea quedaba SIN recordatorio (olvidada, P1). Misma heurística +1 h
    // y título limpio, igual que "un rato"/"un momento".
    @Test fun enUnRatitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar a mamá en un ratito", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun dentroDeUnRatitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Pausa dentro de un ratito", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun enUnRaticoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en un ratico", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun enUnMomentitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en un momentito", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeUnMomentitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Pausa dentro de un momentito", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun alRatitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita al ratito", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun deAquiAUnRatitoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita de aquí a un ratito", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    // --- "dentro de poco"/"en breve"/"dentro de poco rato"/"dentro de nada" (ciclo 610) ---
    // Familia coloquial cotidiana de "pronto" que antes NO casaba vagueRelative →
    // dueAt=null (tarea olvidada, sin recordatorio ni visibilidad en What Now) y residuo
    // en el título ("reunión dentro de poco" → título "reunión dentro de poco"). Mismo
    // contrato que "en un rato": +1 h (heurística honesta), título limpio, cede ante hora
    // explícita. Simétrica futura de "hace poco" (pasado, −3 h).
    @Test fun dentroDePocoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Reunión dentro de poco", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun enBreveEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en breve", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDePocoRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Pausa dentro de poco rato", now, zone)
        assertEquals("Pausa", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun enPocoRatoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Llamar en poco rato", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun deAquiAPocoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita de aquí a poco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun deAcaAPocoEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Cita de acá a poco", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    @Test fun dentroDeNadaEsFechaRelativaDe1Hora() {
        val result = NaturalTaskParser.parse("Reunión dentro de nada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now + 60 * 60_000L, result.dueAt)
    }

    // "dentro de poco a las 5": el ancla vaga cede ante la hora explícita (c.397).
    @Test fun dentroDePocoCedeAnteHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión dentro de poco a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertNotEquals(now + 60 * 60_000L, result.dueAt)
        assertNotNull(result.dueAt)
    }

    // "dentro de poco" NO debe colisionar con "hace poco" (pasado, −3 h).
    @Test fun hacePocoSigueSiendoPasado3Horas() {
        val result = NaturalTaskParser.parse("Pagué la factura hace poco", now, zone)
        assertEquals(now - 3 * 60 * 60_000L, result.dueAt)
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

    // --- "media mañana/tarde/noche/madrugada" y "medio/media día" (ciclo 374) ---
    // Formas separadas del punto medio de una parte del día. Antes NO se interpretaban
    // como hora: la tarea caía sin dueAt (olvidada) y "media X" quedaba como residuo en
    // el título; o "media mañana" colisionaba con el marcador de fecha "mañana" (+1 día,
    // hora 09:00, título mutilado "revisar a media"). Asimetría con "mediodía"/
    // "medianoche" (una palabra) que SÍ funcionaban. now = 2026-07-29 12:00.

    @Test fun mediaTardeParsesMidAfternoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Reunión a media tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aLaMediaTardeParsesMidAfternoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Reunión a la media tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deMediaTardeParsesMidAfternoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Reunión de media tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun mediaMananaParsesMidMorningAndCleanTitle() {
        val result = NaturalTaskParser.parse("Revisar a media mañana", now, zone)
        assertEquals("Revisar", result.title)
        // "media mañana" NO es fecha "mañana": cae hoy (2026-07-29), no +1 día.
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediaMananaNoColisionaConMananaComoFecha() {
        // Sin el guard de mananaAsDate, "media mañana" se agendaba MAÑANA (30/7) a 09:00.
        val result = NaturalTaskParser.parse("Revisar a la media mañana", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediaNocheParsesMidnightAndCleanTitle() {
        val result = NaturalTaskParser.parse("Llamar a media noche", now, zone)
        assertEquals("Llamar", result.title)
        // 00:00 ya pasó a las 12:00 → past-safe rueda a la madrugada de mañana (30/7).
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediaNocheParidadConMedianocheUnaPalabra() {
        // "media noche" (dos palabras) y "medianoche" (una) deben resolver igual.
        val separada = NaturalTaskParser.parse("Llamar a la media noche", now, zone)
        val compuesta = NaturalTaskParser.parse("Llamar a la medianoche", now, zone)
        assertEquals("Llamar", separada.title)
        assertEquals("Llamar", compuesta.title)
        assertEquals(
            DateRules.toLocalDate(compuesta.dueAt!!, zone),
            DateRules.toLocalDate(separada.dueAt!!, zone)
        )
        assertEquals(
            DateRules.toLocalTime(compuesta.dueAt, zone),
            DateRules.toLocalTime(separada.dueAt, zone)
        )
    }

    @Test fun medioDiaParidadConMediodiaUnaPalabra() {
        // "a medio día" (dos palabras) y "al mediodía" (una) deben resolver igual (12:00).
        val separada = NaturalTaskParser.parse("Almuerzo a medio día", now, zone)
        assertEquals("Almuerzo", separada.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(separada.dueAt!!, zone))
        assertEquals(LocalTime.NOON, DateRules.toLocalTime(separada.dueAt, zone))
    }

    @Test fun pasadaLaMediaNocheParsesMidnightAndCleanTitle() {
        val result = NaturalTaskParser.parse("Llamar pasada la media noche", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mediaTardeMananaCombinesMidpointAndNextDay() {
        // "media tarde" (16:30) + "mañana" (fecha +1): ambos presentes, fecha gana.
        val result = NaturalTaskParser.parse("Reunión media tarde mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "antes de/después de + comida/sueño" (c.387): citas cotidianas olvidadas ---
    // "reunión después del almuerzo", "llamar antes de dormir", "cita antes de la cena":
    // antes NO casaban ningún patrón → dueAt=null (tarea SIN vencimiento → olvidada,
    // invisible en What Now/planificador, sin recordatorio). El modificador es obligatorio:
    // "almuerzo"/"cena" solos no son cita (son el evento). Verbo y sustantivo son ambos
    // idiomáticos ("después de comer" = "después del almuerzo").

    @Test fun despuesDelAlmuerzoParsesEarlyAfternoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Reunión después del almuerzo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeDormirParsesLateEveningAndCleanTitle() {
        val result = NaturalTaskParser.parse("Llamar antes de dormir", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeComerEsSinonimoDeDespuesDelAlmuerzo() {
        val result = NaturalTaskParser.parse("Medicina después de comer", now, zone)
        assertEquals("Medicina", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "después" sin tilde ("despues") en anclas de comida/sueño: forma cotidiana al
    // escribir rápido en móvil. Antes el patrón caseaba "despues del almuerzo" pero el
    // lookup de hora devolvía null (las claves del map usan "después") → dueAt=null (cita
    // recordatoria olvidada, P1). c.426: normalización del modificador.

    @Test fun despuesSinTildeDelAlmuerzoInterpretaHoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión despues del almuerzo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesSinTildeDeComerEsSinonimoDeAlmuerzo() {
        val result = NaturalTaskParser.parse("Medicina despues de comer", now, zone)
        assertEquals("Medicina", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesSinTildeDeLaCenaInterpretaHoraYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamada despues de la cena", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDeLaCenaParsesEarlyEvening() {
        val result = NaturalTaskParser.parse("Cita antes de la cena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(19, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesDeLaCenaParsesLateEvening() {
        val result = NaturalTaskParser.parse("Llamada después de la cena", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDelAlmuerzoPasadoHoySeRuedaAManana() {
        // "antes del almuerzo"=11:30; capturado a las 12:00 (ya pasó) → se rueda al día
        // siguiente (past-safe, consistente con medianoche/mediodía), evitando que el
        // recordatorio caiga en el pasado y sea descartado (cita olvidada).
        val result = NaturalTaskParser.parse("Reunión antes del almuerzo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDelAlmuerzoSinArticuloCasaIgual() {
        val result = NaturalTaskParser.parse("Reunión después de almuerzo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDeAcostarseEsSinonimoDeAntesDeDormir() {
        val result = NaturalTaskParser.parse("Pasear antes de acostarse", now, zone)
        assertEquals("Pasear", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDeDormirCombinaConFechaRelativa() {
        val result = NaturalTaskParser.parse("Llamar antes de dormir mañana", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeComerAlInicioDelTextoCasaYRespetaTituloRestante() {
        // "después de comer revisar email": el modificador abre el texto; el ancla se
        // consume y queda "revisar email" como título.
        val result = NaturalTaskParser.parse("después de comer revisar email", now, zone)
        assertEquals("revisar email", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDelAlmuerzoHoraExplicitaTienePrioridad() {
        // "antes del almuerzo a las 11": la hora explícita gana sobre la canónica de
        // respaldo; como 11:00 < now (12:00), se rueda al día siguiente (past-safe).
        val result = NaturalTaskParser.parse("Reunión antes del almuerzo a las 11", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(11, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun almuerzoSoloNoEsCitaSinModificador() {
        // "almuerzo" sin modificador no debe agendarse (es el evento, no una cita).
        val result = NaturalTaskParser.parse("almuerzo", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- "antes/después de la siesta" (ciclo 389) ---
    // "siesta" es ancla post-almuerzo LATAM con hora canónica honesta (no IA):
    // antes→ 13:30, después→ 15:30. Sin esto, "llamar después de la siesta" caía sin
    // vencimiento → cita olvidada. Simétrica del resto de mealSleepAnchorPattern.

    @Test fun despuesDeLaSiestaParsesEarlyAfternoonAndCleanTitle() {
        val result = NaturalTaskParser.parse("Llamar después de la siesta", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLaSiestaParsesEarlyAfternoon() {
        val result = NaturalTaskParser.parse("Reunión antes de la siesta", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(13, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesDeSiestaSinArticuloCasaIgual() {
        val result = NaturalTaskParser.parse("Pasear después de siesta", now, zone)
        assertEquals("Pasear", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun siestaSoloNoEsCitaSinModificador() {
        // "siesta" sin modificador no debe agendarse (es el evento, no una cita).
        val result = NaturalTaskParser.parse("siesta", now, zone)
        assertEquals(null, result.dueAt)
    }

    @Test fun antesDeSiestaPasadoHoySeRuedaAManana() {
        // "antes de la siesta"=13:30; capturado a las 12:00 (aún no llega) → hoy mismo.
        val result = NaturalTaskParser.parse("Reunión antes de la siesta", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(13, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeSiestaCombinaConFechaRelativa() {
        val result = NaturalTaskParser.parse("Llamar después de la siesta mañana", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeSiestaInicioDelTextoCasaYRespetaTituloRestante() {
        // "después de la siesta revisar email": el modificador abre el texto; el ancla se
        // consume y queda "revisar email" como título.
        val result = NaturalTaskParser.parse("después de la siesta revisar email", now, zone)
        assertEquals("revisar email", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- intensificador "justo" + ancla comida/sueño/siesta (c.391) ---
    // "justo" intensifica al modificador ("justo después de comer"); el patrón de ancla
    // consumía "antes/después de + ancla" pero NO el "justo" precedente → quedaba huérfano
    // en el título ("cita justo" en vez de "cita"), contenido capturado degradado (P1).

    @Test fun justoDespuesDeComerConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita justo después de comer", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAntesDelAlmuerzoConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("Reunión justo antes del almuerzo", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(11, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoDespuesDeLaSiestaConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("Llamar justo después de la siesta", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAntesDeDormirConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("Cita justo antes de dormir", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoComoAdjetivoNoSeConsumeSinAncla() {
        // "justo" legítimo (adjetivo, sin ancla de comida/sueño) NO debe desaparecer.
        val result = NaturalTaskParser.parse("comprar justo lo necesario", now, zone)
        assertEquals("comprar justo lo necesario", result.title)
        assertEquals(null, result.dueAt)
    }

    // --- "justo" + anclas canónicos de sol/jornada (ciclo 392) ---
    // El intensificador "justo" antes de un ancla canónico de sol/jornada (amanecer,
    // atardecer, primera/última hora, final del día, media X) quedaba huérfano en el
    // título ("cita justo al amanecer"→"cita justo"). Simétrico de c.391 (comida/sueño).

    @Test fun justoAlAmanecerConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("cita justo al amanecer", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAlAtardecerConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("reunión justo al atardecer", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAPrimeraHoraConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("llamar justo a primera hora", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAUltimaHoraConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("cita justo a última hora", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAlFinalDelDiaConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("reunión justo al final del día", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoAMediaTardeConsumeElIntensificador() {
        val result = NaturalTaskParser.parse("cita justo a media tarde", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(16, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- intensificador "justo" antes de mediodía/medianoche/parte del día (c.401) ---
    // Simétrico de c.391 (justo + comida/sueño) y c.393 (justo a las N). El dueAt ya
    // se resolvía, pero "justo" sobrevivía como residuo en el título.
    @Test fun justoAlMediodiaConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión justo al mediodía", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoALaMedianocheConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar justo a la medianoche", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoALaTardeConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión justo a la tarde", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoALaMananaConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión justo a la mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoDeMadrugadaConsumeElIntensificadorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("salir justo de madrugada", now, zone)
        assertEquals("salir", result.title)
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoPasadaLaMedianocheCombinaIntensificadorYModificador() {
        val result = NaturalTaskParser.parse("llegar justo pasada la medianoche", now, zone)
        assertEquals("llegar", result.title)
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- "a la 1" en DÍGITOS (femenino singular) ---
    // La forma cotidiana "a la 1 pm"/"a la 1:30"/"a la 1 de la tarde" caía al
    // reloj autónomo (N:MM / Nam/pm): la hora se resolvía pero el artículo "a la"
    // quedaba como residuo del título ("almuerzo con Pedro a la"). La forma sin
    // evidencia ("reunión a la 1") se perdía entera (NULL) aunque su simétrica
    // plural ("reunión a las 3") sí resuelve. El patrón "a la una" ahora admite el
    // dígito 0?1 además de la forma escrita.

    @Test fun aLaDigitFormsResolveAndCleanTitle() {
        listOf(
            "Almuerzo con Pedro a la 1 pm" to LocalTime.of(13, 0),
            "Almuerzo con Pedro a la 1" to LocalTime.of(1, 0),
            "Cena a la 1:30" to LocalTime.of(1, 30),
            "Almuerzo con Pedro a la 1 de la tarde" to LocalTime.of(13, 0),
            "Cenar a la 1 y media" to LocalTime.of(1, 30)
        ).forEach { (input, time) ->
            val result = NaturalTaskParser.parse(input, now, zone)
            val expectedTitle = input.substringBefore(" a la")
            assertEquals(expectedTitle, result.title)
            assertEquals(time, DateRules.toLocalTime(result.dueAt!!, zone))
        }
    }

    @Test fun aLaSingularRejectsDigitOtherThanOne() {
        // Only hour 1 is grammatically singular ("la una"). "a la 12" must stay
        // unresolved; the plural "a las 12" is the correct form.
        val result = NaturalTaskParser.parse("reunión a la 12", now, zone)
        assertNull(result.dueAt)
        assertEquals("reunión a la 12", result.title)
    }

    @Test fun aLaDigitCountNounStaysUnresolved() {
        // "enviar a la 1 invitaciones" is a count (1 invitation), not an
        // appointment at 01:00: the timeMatchIsCountNoun guard extends to the
        // new digit form exactly as it does for "a la una personas".
        val result = NaturalTaskParser.parse("enviar a la 1 invitaciones", now, zone)
        assertNull(result.dueAt)
        assertEquals("enviar a la 1 invitaciones", result.title)
    }

    // --- c.677: ordinales posicionales con dígito ("1ª/2º") no son citas a esa hora ---
    // "clasificar a la 1ª posición" casaba con "a la 1" → 01:00 falso y título mutilado
    // ('clasificar ª posición'): el indicador ordinal º/ª no es carácter de palabra en
    // Java regex, así que el `\b` final SÍ existía tras el dígito.

    @Test fun aLaDigitOrdinalPositionStaysUnresolved() {
        listOf("clasificar a la 1ª posición", "subirlo a la 1º posición").forEach { input ->
            val result = NaturalTaskParser.parse(input, now, zone)
            assertNull("'$input' es ordinal posicional, no cita a la 1:00", result.dueAt)
            assertEquals(input, result.title)
        }
    }

    @Test fun aLasDigitOrdinalPositionStaysUnresolved() {
        listOf("a las 3ª posición", "mover a las 2º fila").forEach { input ->
            val result = NaturalTaskParser.parse(input, now, zone)
            assertNull("'$input' es ordinal posicional, no cita a esa hora", result.dueAt)
            assertEquals(input, result.title)
        }
    }

    @Test fun aLaUnaPosicionEscritaStaysUnresolved() {
        // Simétrico al dígito: "a la una posición" (posición escrita) tampoco es cita.
        val result = NaturalTaskParser.parse("a la una posición", now, zone)
        assertNull(result.dueAt)
        assertEquals("a la una posición", result.title)
    }

    @Test fun aLaDigitOrdinalIndicatorDoesNotBlockLegitForms() {
        // Regresión del guard: las formas legítimas de "a la 1"/"a las N" siguen resolviendo.
        listOf(
            "cena a la 1" to LocalTime.of(1, 0),
            "a las 3" to LocalTime.of(3, 0),
            "cena a la 1:30" to LocalTime.of(1, 30)
        ).forEach { (input, time) ->
            val result = NaturalTaskParser.parse(input, now, zone)
            assertEquals(time, DateRules.toLocalTime(result.dueAt!!, zone))
        }
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

    // --- "veintiuna": forma femenina de la hora 21 (ciclo 387) ---
    // Las horas son femeninas en español: "a las 21" se dice "a las veintiuna", no
    // "a las veintiuno". Antes WRITTEN_HOUR_ALT sólo tenía la forma masculina y "a las
    // veintiuna" no casaba → la cita nocturna quedaba SIN dueAt (un compromiso vespertino
    // silenciosamente olvidado). Cubre el conector "a las", el aproximado "a eso de las"
    // (admite hora en punto escrita), la fracción "y media" y la ruta standalone con
    // parte del día.

    @Test fun aLasVeintiunaEscritaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a las veintiuna", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aEsoDeLasVeintiunaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a eso de las veintiuna", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasVeintiunaYMediaEs21y30() {
        val result = NaturalTaskParser.parse("Reunión a las veintiuna y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun veintiunaDeLaNocheStandaloneEs21h() {
        val result = NaturalTaskParser.parse("Cena veintiuna de la noche", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Sufijo "en punto" (ciclo 388) ---
    // "a las 9 en punto" / "a las nueve en punto" marca la hora exacta en español. Antes
    // el sufijo NO se consumía: el patrón casaba "a las 9" y dejaba "en punto" como residuo
    // del título ("reunión en punto") — la cita se agendaba bien pero el título quedaba
    // mutilado con basura horaria. El separador del sufijo es `\s*` (no `\s+`) porque los
    // grupos greedy anteriores (meridiem/fracción/sufijo horas) roban el espacio y dejan
    // el `\s+` sin nada que consumir → el sufijo entero fallaba. Además "en punto" cuenta
    // como evidencia de reloj en [hasClockEvidence] para que el guard anti-cuenta (c.361)
    // no trate "a las 9 en punto" como "a las 9 [personas]".

    @Test fun aLasNueveEnPuntoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las nueve en punto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9EnPuntoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Llamar a las 9 en punto", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasTresEnPuntoDeLaTardeLimpiaTituloYResuelve15h() {
        val result = NaturalTaskParser.parse("Cita a las tres en punto de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas900EnPuntoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Llamada a las 9:00 en punto", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveYMediaEnPuntoLimpiaTituloYResuelve9y30() {
        val result = NaturalTaskParser.parse("Reunión a las nueve y media en punto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9PmEnPuntoLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 pm en punto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta (c.361) sigue activo: "a las 9 en punto" es cita inequívoca,
    // pero "a las 10 personas" (sin "en punto") sigue siendo CUENTA, no cita.
    @Test fun aLas10EnPuntoNoEsCuentaResuelve10h() {
        val result = NaturalTaskParser.parse("Reunión a las 10 en punto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- "cuarto/cinco/diez para las N" (forma caribeña/latinoamericana, ciclo 476) ---
    // Simétrico regional de "a las N menos cuarto": en el Caribe/LatAm la fracción
    // negativa se antepone al introductor ("cuarto para las 8" = 7:45, "cinco para
    // las 8" = 7:55). Antes la fracción iba DESPUÉS ("a las 8 menos cuarto"), forma
    // peninsular: la forma antepuesta caía a dueAt=null (cita olvidada) y "cuarto
    // para las 8" sobrevivía como residuo en el título. Se normaliza a la forma
    // resuelta "a las N menos <fracción>" reutilizando TODO el flujo de hora explícita
    // (resolución AM/PM, wrap 24 h, limpieza del título), igual que paraTimeIntroPattern.
    @Test fun cuartoParaLas8Es7_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita cuarto para las 8", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(7, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun cincoParaLas9Es8_55YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamar cinco para las 9", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(8, 55), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun diezParaLas3Es2_50YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita diez para las 3", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(2, 50), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun cuartoParaLaUnaWrapA12_45() {
        // 1:00 menos cuarto hace wrap a 0:45 (00:45), no a 0:45 negativo.
        val result = NaturalTaskParser.parse("Cita cuarto para la una", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(0, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun cuartoParaLas3DeLaTardeAplicaPm() {
        val result = NaturalTaskParser.parse("Cita cuarto para las 3 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 45), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun cuartoParaLas8PmEs19_45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita cuarto para las 8 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(19, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun diezParaLas10PersonasNoEsHora() {
        // "diez" es cantidad de personas, no fracción de reloj: no debe reescribirse
        // ni asignarse dueAt. El guard rechaza el sustantivo "personas" tras la hora.
        val result = NaturalTaskParser.parse("Reservar diez para las 10 personas", now, zone)
        assertNull(result.dueAt)
        assertEquals("Reservar diez para las 10 personas", result.title)
    }

    @Test fun cuartoParaLas8SinEvidenciaDeRelojNoToca() {
        // Sin meridiem/parte del día ni continuador seguro tras la hora: el guard
        // deja la frase intacta si lo siguiente es texto plano ambiguo (no reloj).
        // Aquí "cuarto para las 8 cena" -> "cena" no es continuador ni reloj -> no reescribe.
        val result = NaturalTaskParser.parse("Cita cuarto para las 8 cena", now, zone)
        assertNull(result.dueAt)
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

    // --- "a las N.MM" / "a las N,MM": punto/coma decimal como separador de minutos (c.479) ---
    // "a las 3.30"/"a las 3,30" antes NO se reconocían como hora con minutos: el grupo `:MM`
    // de [timePatterns] sólo admitía ":" o "h" como separador. El "3" casaba como hora en
    // punto (03:00) y el ".30"/",30" quedaba como residuo en el título ("Cita .30"/"Cita ,30"):
    // la cita se agendaba 30 min antes y el contenido capturado quedaba mutilado (P1
    // captura/título limpio/datos). El punto/coma es la notación decimal de reloj cotidiana
    // ("3.30" = 3:30) distinta de la cadencia fraccionaria "cada N horas y media" (c.260) y
    // de la duración decimal "1.5 horas" (c.2793, que exige unidad "horas"). Aquí el
    // separador va tras una hora en notación "a las N", así que es inequívoco. Sólo se admite
    // con DOS dígitos `[0-5]\d` (no "3.5" de un dígito: ese caso es genuinamente ambiguo —
    // ¿minuto 05 o decimal .5=30min?— y queda ABIERTO; el patrón ya exige dos dígitos, así
    // que no hace falta guard extra). El `.` se añade SÓLO al patrón "a las N"/"a la una"
    // (con introductor), NO al patrón autónomo de reloj "HH:MM" suelto: así "2.50 kg" (cantidad
    // con dos decimales) no se roba como hora 02:50 porque no lleva "a las".

    @Test fun aLas3_30ConPuntoEs3_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3.30", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(3, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas3_30ConComaEs3_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3,30", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(3, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas3_30DeLaTardeConPuntoEs15_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3.30 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9_15ConPuntoEs9_15YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 9.15", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas3_30PmConPuntoEs15_30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a las 3.30 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- mediodía/medianoche + sufijos "y pico"/"en punto"/"más o menos"/"aproximadamente" ---
    // Los patrones standalone de mediodía/medianoche (no los "a las N") carecían de
    // [APPROX_TIME_SUFFIX]/[EN_PUNTO_SUFFIX]: el modificador NO se consumía y dejaba residuo
    // en el título ("Reunión y pico"/"Reunión en punto") aunque la hora canónica sí se
    // resolvía (12:00/00:00) — asimetría con "a las 9 y pico"/"a las 9 en punto" (c.388/c.393)
    // que sí limpiaban. "y pico" no inventa minutos: resuelve a la hora en punto (12:00/00:00).
    @Test fun alMediodiaYPicoLimpiaTituloYResuelve12h() {
        val result = NaturalTaskParser.parse("Reunión al mediodía y pico", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaMedianocheYPicoLimpiaTituloYResuelve0h() {
        val result = NaturalTaskParser.parse("Llamar a la medianoche y pico", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alMediodiaEnPuntoLimpiaTituloYResuelve12h() {
        val result = NaturalTaskParser.parse("Reunión al mediodía en punto", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaMedianocheMasOMenosLimpiaTituloYResuelve0h() {
        val result = NaturalTaskParser.parse("Entregar a la medianoche más o menos", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalTime.MIDNIGHT, DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alMediodiaAproximadamenteLimpiaTituloYResuelve12h() {
        val result = NaturalTaskParser.parse("Almuerzo al mediodía aproximadamente", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "pasada la medianoche"/"pasado el mediodía"/"después del mediodía": título limpio ---
    // Antes estos modificadores cotidianos de "a partir de esa hora" dejaban el prefijo
    // como residuo en el título ("llamar pasada la", "llamar pasado", "llamar después del")
    // aunque la hora canónica (00:00/12:00) sí se resolvía: contenido capturado mutilado
    // (P1). "medianoche"/"mediodía" son inequívocas como hora canónica, así que consumir
    // el modificador no introduce ambigüedad. Se verifica título limpio Y hora correcta.
    @Test fun pasadaLaMedianocheNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Llamar pasada la medianoche", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun pasadoElMediodiaNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Llamar pasado el mediodía", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun pasadaMedianocheSinArticuloNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Desayunar pasada medianoche", now, zone)
        assertEquals("Desayunar", result.title)
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesDelMediodiaNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Llamar después del mediodía", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesDeLaMedianocheNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Llamar después de la medianoche", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun despuesDeMediodiaSinArticuloNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Reunión después de mediodía", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // No-regresión: "pasado"/"pasada" DESPUÉS del sustantivo (uso demostrativo) y
    // "pasado mañana" (relativo) NO deben verse afectados por el modificador-prefijo.
    @Test fun pasadoDespuesDeSustantivoSigueIntacto() {
        // "el viernes pasado" = viernes anterior; "la semana pasada" = semana anterior.
        val r1 = NaturalTaskParser.parse("el viernes pasado", now, zone)
        assertNotNull(r1.dueAt)
        val r2 = NaturalTaskParser.parse("la semana pasada", now, zone)
        assertNotNull(r2.dueAt)
        val r3 = NaturalTaskParser.parse("pasado mañana", now, zone)
        assertNotNull(r3.dueAt)
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

    // Forma plural del adjetivo: "a primeras horas" (variantes cotidiana de
    // "a primera hora"). Antes dejaba residuo "a primeras horas" en el título aunque el
    // dueAt se resolvía vía la parte del día. c.400: limpieza simétrica al singular.
    @Test fun primerasHorasLimpiaTituloYResuelveInicioJornada() {
        val result = NaturalTaskParser.parse("Ir al dentista a primeras horas de la mañana", now, zone)
        assertEquals("Ir al dentista", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun primerasHorasSinParteDelDiaUsaCanonicaPrimeraHora() {
        val result = NaturalTaskParser.parse("Llamar a Ana a primeras horas", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertFalse(result.title.contains("primeras", ignoreCase = true))
        assertFalse(result.title.contains("horas", ignoreCase = true))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- Rangos con conector "desde ... hasta/a ..." (c.428, P1) ---
    // BUG: "desde las N hasta las M" (la forma cotidiana más natural de un bloque de
    // tiempo) NO se normalizaba a la forma canónica "de N a M": el extremo final se
    // resolvía como dueAt del CIERRE, "desde las N" quedaba como residuo del título y
    // la duración se perdía. "desde 9 hasta 11" (sin "las") perdía la cita entera
    // (dueAt=null → olvido). Ahora "desde...hasta/a" se reescribe a "de N a M" y
    // reutiliza todo el flujo de rango existente (inicio como dueAt + duración real).
    @Test fun desdeRangeConHastaParsesStartAndDuration() {
        val result = NaturalTaskParser.parse("Trabajo desde las 9 hasta las 11", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun desdeRangeConAIsCanonicalEquivalent() {
        val result = NaturalTaskParser.parse("Trabajo desde las 9 a las 11", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun desdeRangeBareHoursParsesStartAndDuration() {
        // "desde 9 hasta 11" (sin "las"): antes dueAt=null (cita perdida). Ahora 09:00.
        val result = NaturalTaskParser.parse("Trabajo desde 9 hasta 11", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun desdeRangeWithMeridiemPropagatesPmToStart() {
        val result = NaturalTaskParser.parse("Estudiar desde las 7pm hasta las 9pm", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(19, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun desdeRangeWithTrailingDeLaTardePropagatesPmToStart() {
        val result = NaturalTaskParser.parse("Reunión desde las 3 de la tarde hasta las 5", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun desdeRangeDoesNotFalsePositiveOnItemCount() {
        // Guard anti-cuenta: "desde 3 hasta 5 cajas" no debe agendar una cita.
        val result = NaturalTaskParser.parse("Comprar desde 3 hasta 5 cajas", now, zone)
        assertNull(result.dueAt)
        assertNull(result.durationMinutes)
    }

    // Hora suelta con meridiem (NO rango): sigue resolviéndose correctamente. El guard
    // solo actúa cuando el tiempo explícito cae DENTRO del span de un rango validado.
    @Test fun standaloneHourWithMeridiemNotAffectedByRangeGuard() {
        val r1 = NaturalTaskParser.parse("Llamada 8pm", now, zone)
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(r1.dueAt!!, zone))
        val r2 = NaturalTaskParser.parse("Cita 9am", now, zone)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r2.dueAt!!, zone))
    }

    // --- Sufijo "justo" tras hora (c.428b, P2 título limpio) ---
    // BUG: "justo" como SUFIJO ("a las 7 de la tarde justo") no se consumía (sólo el
    // PREFIJO "justo a las N" se normalizaba en c.393), así quedaba como residuo del
    // título ('llamar justo') pese a agendar la hora correcta. Ahora el sufijo se
    // consume y el título queda limpio. La hora se conserva en punto (igual que "y pico").
    @Test fun justoSuffixAfterHourDoesNotLeakToTitle() {
        val result = NaturalTaskParser.parse("Llamar a las 7 de la tarde justo", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(19, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoSuffixAfterBareHourDoesNotLeakToTitle() {
        val result = NaturalTaskParser.parse("Cita a las 9 justo", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoSuffixAfterMediodiaDoesNotLeakToTitle() {
        val result = NaturalTaskParser.parse("Almuerzo al mediodía justo", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- Rango ambiguo que cruza el mediodía sin meridiem: "de 9 a 5" (c.804, P1) ---
    // Asimetría: "de 3 a 5" (fin > inicio) SÍ se aceptaba con la misma ambigüedad,
    // pero "de 9 a 5"/"de 8 a 4" (fin <= inicio) se rechazaba entero (dueAt=null,
    // duración perdida) — la forma más común de describir una jornada o turno en
    // español. La lectura natural es fin PM (9→17, 8→16): se aplica wrap +12h al
    // fin bajo las mismas condiciones del rango ascendente ambiguo (sin meridiem/
    // unidad/minutos, ambas <13, sin sustantivo de cantidad, duración 1..11h).
    // Se EXCLUYE el inicio en 12: "de 12 a 2" sigue rechazado (decisión deliberada
    // de ciclo 79: el límite del mediodía sin meridiem es irreductiblemente ambiguo).
    @Test fun noonWrapRangeWorkShiftDe9a5() {
        val result = NaturalTaskParser.parse("Trabajo de 9 a 5", now, zone)
        assertEquals("Trabajo", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(480, result.durationMinutes)
    }

    @Test fun noonWrapRangeShiftDe8a4() {
        val result = NaturalTaskParser.parse("Turno de 8 a 4", now, zone)
        assertEquals("Turno", result.title)
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(480, result.durationMinutes)
    }

    @Test fun noonWrapRangeShortShiftDe10a1() {
        val result = NaturalTaskParser.parse("Clase de 10 a 1", now, zone)
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(180, result.durationMinutes)
    }

    @Test fun noonWrapRangeKeepsNoonBoundaryRejected() {
        // "de 12 a 2" (inicio en el límite del mediodía) sigue rechazado:
        // decisión deliberada de ciclo 79 (noonCrossingRangeAmbiguousRejected).
        val result = NaturalTaskParser.parse("Reunión de 12 a 2", now, zone)
        assertNull(result.dueAt)
        assertNull(result.durationMinutes)
    }

    @Test fun noonWrapRangeCountNounGuard() {
        // "de 9 a 5 entradas" es una cuenta, no un rango horario: se rechaza y
        // el título se conserva intacto (guard anti-cuenta).
        val result = NaturalTaskParser.parse("Compra de 9 a 5 entradas", now, zone)
        assertNull(result.dueAt)
        assertNull(result.durationMinutes)
        assertEquals("Compra de 9 a 5 entradas", result.title)
    }

    @Test fun noonWrapRangeDegenerateRejected() {
        // "de 5 a 5" (fin == inicio): el wrap daría 12h (> 11h máximo) → se rechaza.
        val result = NaturalTaskParser.parse("Turno de 5 a 5", now, zone)
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

    // "final de semana" (singular) es la variante regional latinoamericana de "fin de
    // semana" (común en Colombia/Venezuela/Centroamérica). Antes NO se reconocía: la
    // tarea quedaba SIN fecha (dueAt=null) con "final de semana" pegado al título →
    // tarea olvidada, invisible en What Now/búsqueda. P1: pérdida de fecha para una
    // frase de captura cotidiana en el dialecto de la base de usuarios. Ahora resuelve
    // idéntico a "fin de semana" (próximo sábado, hora canónica 09:00) y limpia el título.
    // Simetría: cada variante de "este/el/próximo/suelto" debe comportarse igual que su
    // equivalente con "fin".
    @Test fun finalDeSemanaSueltoProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pintar la valla final de semana", now, zone)
        assertEquals("Pintar la valla", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteFinalDeSemanaProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Comprar pan este final de semana", now, zone)
        assertEquals("Comprar pan", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun elFinalDeSemanaRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Fiesta el final de semana a las 20:00", now, zone)
        assertEquals("Fiesta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun proximoFinalDeSemanaProgramaProximoSabadoYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Viaje próximo final de semana", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "cada final de semana" (variante regional + "cada") es hábito semanal sáb+dom,
    // simétrico a "cada fin de semana". Antes caía a dueAt=null/NONE → tarea repetitiva
    // olvidada. P1 en el dialecto regional.
    @Test fun cadaFinalDeSemanaEsHabitoSemanalFinDeSemana() {
        val result = NaturalTaskParser.parse("Estudiar cada final de semana", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // --- c.494: preposición distributiva "a" (= "los"/"cada") antes de fin de semana ---
    // El "a" coloquial ante recurrencia de fin de semana ("a fines de semana", "a los
    // findes", "a cada fin de semana") sobrevivía pegado al título ("Gimnasio a"). P1 de
    // calidad de captura: un hábito real nacía con basura visible. Paralelo al genitivo
    // "de/del" de c.493. La "a" solo se consume cuando antecede al token de fin de semana;
    // "Ir a la playa los findes" conserva "a la playa" intacto (caso negativo abajo).
    @Test fun aFinesDeSemanaNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Gimnasio a fines de semana", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun aLosFindesNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Correr a los findes", now, zone)
        assertEquals("Correr", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun aCadaFinDeSemanaNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Limpiar a cada fin de semana", now, zone)
        assertEquals("Limpiar", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // Caso negativo: la "a" que NO antecede al token de fin de semana se preserva.
    // "Ir a la playa los findes" → "a la playa" es parte del título; solo "los findes"
    // es recurrencia. La "a" distributiva solo casa pegada a "fines/findes/cada fin".
    @Test fun aLaPlayaFindesConservaADeDestinoEnTitulo() {
        val result = NaturalTaskParser.parse("Ir a la playa los findes", now, zone)
        assertEquals("Ir a la playa", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    @Test fun aPerrosFinesDeSemanaConservaADeObjetoEnTitulo() {
        val result = NaturalTaskParser.parse("Pasear a perros fines de semana", now, zone)
        assertEquals("Pasear a perros", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("6,7", result.recurrenceDays)
    }

    // --- c.542: formas de PASADO del fin de semana ---
    // "el fin de semana pasado"/"el finde pasado"/"el fin de semana que pasó"/
    // "el pasado fin de semana"/"el fin de semana anterior" deben resolver al sábado
    // ANTERIOR (tarea vencida honesta, visible en What Now/vencidas), no al PRÓXIMO
    // sábado (futuro). Bug P1: una tarea claramente pasada se fechaba en el futuro y
    // se ocultaba de la vista de vencidas. Simétrico a "el sábado pasado" (cuya rama
    // previousWeekday ya existía). now=2026-07-29 (miércoles) → sábado pasado 2026-07-25.
    @Test fun elFinDeSemanaPasadoProgramaSabadoAnteriorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar informe el fin de semana pasado", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elFinDeSemanaQuePasoProgramaSabadoAnteriorYLimpiaTitulo() {
        // "que pasó" (con y sin tilde) coloquial = fin de semana pasado.
        val result = NaturalTaskParser.parse("Revisar informe el fin de semana que pasó", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elFinDeSemanaQuePasoSinTildeProgramaSabadoAnterior() {
        val result = NaturalTaskParser.parse("Revisar informe el fin de semana que paso", now, zone)
        assertEquals("Revisar informe", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elFindePasadoProgramaSabadoAnteriorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cine el finde pasado", now, zone)
        assertEquals("Cine", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elPasadoFinDeSemanaProgramaSabadoAnteriorYLimpiaTitulo() {
        // Modificador reverso (prefijo): "el pasado fin de semana".
        val result = NaturalTaskParser.parse("Cita el pasado fin de semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun elFinDeSemanaAnteriorProgramaSabadoAnteriorYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Llamada el fin de semana anterior", now, zone)
        assertEquals("Llamada", result.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- c.495: "a" distributiva GENERALIZADA ante cualquier cadencia ---
    // La sonda mostró que el residuo "a" de c.494 NO era exclusivo de fin de semana: la
    // misma "a" coloquial ante "cada día/semana/mes/año/lunes/mañana" dejaba el título
    // sucio ("Meditar a", "Reporte a", "Reunión a", "Estudio a"). En vez de parchear
    // patrón por patrón, se consume en un único punto: la limpieza de phraseRanges de
    // recurrencia extiende cada rango hacia atrás para tragarse la "a" que antecede
    // (paralelo al genitivo "de/del" de strippedPeriodRange). Así cubre de una vez
    // diaria, semanal, mensual, anual, por weekday y por parte del día.
    @Test fun aCadaDiaNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Meditar a cada día", now, zone)
        assertEquals("Meditar", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
    }

    @Test fun aCadaSemanaNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Reporte a cada semana", now, zone)
        assertEquals("Reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
    }

    @Test fun aCadaMesNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Reunión a cada mes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
    }

    @Test fun aCadaAnoNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Chequeo a cada año", now, zone)
        assertEquals("Chequeo", result.title)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
    }

    @Test fun aCadaLunesNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Fútbol a cada lunes", now, zone)
        assertEquals("Fútbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1", result.recurrenceDays)
    }

    @Test fun aCadaMananaNoDejaResiduoAEnTitulo() {
        val result = NaturalTaskParser.parse("Estudio a cada mañana", now, zone)
        assertEquals("Estudio", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
    }

    // Caso negativo: la "a" que NO antecede a una cadencia se preserva. "Ir a la
    // reunión cada lunes" → "a la reunión" es parte del título; solo "cada lunes" es
    // recurrencia. La "a" que sobrevive aquí NO está pegada a la cadencia.
    @Test fun aDestinoNoRecurrenciaConservaAEnTitulo() {
        val result = NaturalTaskParser.parse("Ir a la oficina cada lunes", now, zone)
        assertEquals("Ir a la oficina", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1", result.recurrenceDays)
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

    // "la otra semana" / "otra semana" son sinónimos cotidianos de "la próxima
    // semana" en español. Antes caían a dueAt=null + frase completa como título
    // (vencimiento olvidado, P1). Ahora resuelven +7d como el resto de períodos
    // próximos. El lookahead negativo evita que "otra semana pasada" (contenido,
    // no fecha futura) se consuma como fecha.
    @Test fun otraSemanaParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión la otra semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun otraSemanaSinArticuloParsesDueAt() {
        val result = NaturalTaskParser.parse("Reunión otra semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun otraSemanaNoSeConsumeComoFuturoCuandoEsPasada() {
        // "otra semana pasada" es contenido referido al pasado, no una fecha futura.
        // nextPeriodPattern (futuro, +7d) NO debe consumirla: el lookahead negativo lo
        // impide. "semana pasada" si es una fecha legitima (lastPeriodPattern), asi
        // que el vencimiento es pasado (no +7d futuro). Garantiza que "otra" no
        // promueva una frase pasada a vencimiento futuro.
        val result = NaturalTaskParser.parse("Resumen otra semana pasada", now, zone)
        assertNotEquals(LocalDate.of(2026, 8, 5), result.dueAt?.let { DateRules.toLocalDate(it, zone) })
    }

    // "la semana siguiente" / "el mes siguiente" / "el año siguiente" son sinónimos
    // de "que viene"/"próximo". Antes caían a dueAt=null + residuo (P1). Ahora
    // resuelven +1 período. No colisiona con weekdayPattern ("el martes siguiente",
    // que exige weekday) ni con dayAfterPattern ("el día siguiente", que exige "día").
    @Test fun semanaSiguienteParsesDueAt() {
        val result = NaturalTaskParser.parse("Cita la semana siguiente", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mesSiguienteParsesDueAt() {
        val result = NaturalTaskParser.parse("Pago el mes siguiente", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun anioSiguienteParsesDueAt() {
        val result = NaturalTaskParser.parse("Renovación el año siguiente", now, zone)
        assertEquals("Renovación", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun semanaSiguienteRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Cita la semana siguiente a las 10", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun mesQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Pagar el mes que viene a las 10", now, zone)
        assertEquals("Pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- Genitivo temporal "de/del" antes de un período relativo ("balance de la
    // semana pasada", "informe del mes pasado", "plan de la semana que viene") ---
    // El conector "de"/"del" es el modificador de posesión temporal del contenido,
    // no contenido en sí (la frase de período que le sigue resuelve una fecha, así que
    // es inequívocamente temporal). Antes el borrado de la frase de período dejaba ese
    // conector como residuo en el título ("balance de", "informe del", "plan de") —
    // contenido capturado degradado (P1). Paridad con monthNameStripPattern (c.448) y
    // el genitivo de día relativo (l.4579), pero para períodos relativos.

    @Test fun genitivoDeLaSemanaPasada_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Balance de la semana pasada", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2026, 7, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelMesPasado_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Informe del mes pasado", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaSemanaAnterior_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Resumen de la semana anterior", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 7, 22), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelMesAnterior_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Cierre del mes anterior", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelAnioPasado_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Balance del año pasado", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2025, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.512 — "la quincena pasada/anterior": simétrico a semana/mes/año. Antes caía a
    // quincenaPattern (próximo hito FUTURO, 2026-07-31) y dejaba "pasada"/"anterior"
    // como residuo del título. Ahora se resuelve a hoy−15d (2026-07-14) y se borra limpia,
    // igual que "...semana/mes/año pasado". El genitivo "de/del" se consume también.
    @Test fun laQuincenaPasada_resuelvePasadoYNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Cobro la quincena pasada", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun quincenaAnterior_resuelvePasadoYNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Revisión de la quincena anterior", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(LocalDate.of(2026, 7, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaQuincenaPasada_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Pago de la quincena pasada", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 7, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaQuincenaAnterior_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Balance de la quincena anterior", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2026, 7, 14), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaSemanaQueViene_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Plan de la semana que viene", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelMesQueViene_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Previsión del mes que viene", now, zone)
        assertEquals("Previsión", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelMesProximo_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Objetivos del mes próximo", now, zone)
        assertEquals("Objetivos", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelAnioQueViene_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Plan del año que viene", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2027, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaSemanaEntrante_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Previsión de la semana entrante", now, zone)
        assertEquals("Previsión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeLaProximaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Agenda de la próxima semana", now, zone)
        assertEquals("Agenda", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Contra-regresión: "de la semana"/"de la semana santa" SIN marcador de
    // período (pasada/que viene/próxima/entrante/anterior) NO resuelve fecha, así que
    // el conector "de" NO es temporal → debe PERMANECER como contenido del título.
    // El fix consume el genitivo SÓLO cuando la frase de período casa (resuelve fecha).

    @Test fun deLaSemanaSinMarcador_conservaDeComoContenido() {
        val result = NaturalTaskParser.parse("Menú de la semana", now, zone)
        assertEquals("Menú de la semana", result.title)
    }

    @Test fun deLaSemanaSanta_conservaDeComoContenido() {
        val result = NaturalTaskParser.parse("Foto de la semana santa", now, zone)
        assertEquals("Foto de la semana santa", result.title)
    }

    // --- Genitivo temporal "de/del" antes de frases de semana NO periódicas: "finales
    // de la semana", "principios de la semana", "mediados de la semana", "fin de
    // semana" (c.491). Misma clase de residuo que c.490, pero en las familias
    // thisWeek/startOfWeek/midOfWeek/weekend (no en lastPeriod/nextPeriod). El fix
    // extiende strippedPeriodRange a esos 4 sitios de borrado temporal. ---
    // Contra-regresión incluida: "foto de la semana santa" y "menú de la semana"
    // (sin marcador) conservan su "de" (la frase NO casa → no resuelve fecha).

    @Test fun genitivoDeFinalesDeLaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Balance de finales de la semana", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeFinDeLaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Informe de fin de la semana", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeFinDeLaSemanaQueViene_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Resumen de fin de la semana que viene", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDePrincipiosDeLaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Plan de principios de la semana", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDePrincipiosDeLaSemanaQueViene_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Informe de principios de la semana que viene", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeMediadosDeLaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Resumen de mediados de la semana", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeMediadosDeLaSemanaQueViene_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Informe de mediados de la semana que viene", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeFinDeSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Foto de fin de semana", now, zone)
        assertEquals("Foto", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelFinDeSemana_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Resumen del fin de semana", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeEstaSemana_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Balance de esta semana", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Genitivo temporal huérfano antes de HORA / canónica (mediodía/medianoche) ---
    // El conector "de/del/de la" que introduce una hora o canónica temporal
    // ("cita de 3 pm", "reunión del mediodía", "comida de medianoche",
    // "reunión de la medianoche") sobrevivía como residuo al final del título
    // aunque la hora sí se agendaba: el token temporal se borraba pero la
    // preposición genitiva que lo introducía quedaba pegada. Simétrico del
    // genitivo de período (c.490/c.491), de fecha de mes (c.448) y de día
    // relativo, pero para horas/canónicas sueltas. P1: título irreconocible
    // en captura ultrarrápida.

    @Test fun genitivoDeHoraAmPm_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Cita de 3 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun genitivoDeHoraBareAm_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Cita de 9am", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun genitivoDeHoraConMinutos_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Cita de 3:30", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(3, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun genitivoDelMediodia_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Reunión del mediodía", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun genitivoDeMedianoche_noDejaResiduoDe() {
        val result = NaturalTaskParser.parse("Reunión de medianoche", now, zone)
        assertEquals("Reunión", result.title)
    }

    @Test fun genitivoDeLaMedianoche_noDejaResiduoDeLa() {
        val result = NaturalTaskParser.parse("Reunión de la medianoche", now, zone)
        assertEquals("Reunión", result.title)
    }

    @Test fun genitivoDelMediodiaYMedia_noDejaResiduoDel() {
        val result = NaturalTaskParser.parse("Reunión del mediodía y media", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(12, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun genitivoDeTardeConContenidoPrevio_noTocaDeDeContenido() {
        // "reunión de equipo de 3 pm": el "de equipo" es contenido legítimo
        // (NO al final tras borrar "de 3 pm"); sólo el "de" final huérfano se elimina.
        val result = NaturalTaskParser.parse("Reunión de equipo de 3 pm", now, zone)
        assertEquals("Reunión de equipo", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- Contra-regresión: "de" final sin agenda es contenido legítimo ---
    // Sin dueAt (no se resolvió hora/fecha), el "de/del" final NO se toca:
    // "nota de", "reunión de equipo" (sin hora), "foto de la semana santa".

    @Test fun deFinalSinAgenda_conservaDeComoContenido() {
        val result = NaturalTaskParser.parse("Nota de", now, zone)
        assertEquals("Nota de", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun deContenidoSinAgenda_noSeElimina() {
        val result = NaturalTaskParser.parse("Reunión de equipo", now, zone)
        assertEquals("Reunión de equipo", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun genitivoDeRango_noTocaConectorDeRango() {
        // El conector "de" inicial de un rango "de 9am a 11am" lo consume
        // timeRangePattern; el paso de genitivo huérfano no debe tocarlo.
        val result = NaturalTaskParser.parse("Clase de 9am a 11am", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(120, result.durationMinutes)
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

    // --- "del N del mes que viene": forma genitiva (vencimientos/cobros) ---
    // En español los vencimientos usan el artículo genitivo "del" para introducir el
    // día ("alquiler del 15", "pago del 20"). Antes nextMonthDayPattern solo admitía
    // "el N", así que el día no casaba y nextPeriodPattern robaba "del mes que viene"
    // como +30d genérico (→ 2026-08-28, fecha errónea) dejando el residuo corrupto
    // "alquiler del 15 del" en el título. Ahora se ancla al día N del mes siguiente.

    @Test fun delNDelMesQueVieneResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Alquiler del 15 del mes que viene", now, zone)
        assertEquals("Alquiler", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delNDelMesProximoResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Alquiler del 15 del mes próximo", now, zone)
        assertEquals("Alquiler", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delNDelMesEntranteResuelveDiaNDelMesSiguiente() {
        val result = NaturalTaskParser.parse("Alquiler del 15 del mes entrante", now, zone)
        assertEquals("Alquiler", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delNDelMesQueVieneRespetaDiaDistinto() {
        // "pago del 20": día distinto del 15 para confirmar que se lee del grupo.
        val result = NaturalTaskParser.parse("Pago del 20 del mes que viene", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(LocalDate.of(2026, 8, 20), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun delNDelMesQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Alquiler del 15 del mes que viene a las 10", now, zone)
        assertEquals("Alquiler", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun delNDelMesQueVieneHaceClampDiaImposible() {
        // Desde 29/07 el mes que viene es agosto (31 días): el 31 existe y se respeta.
        val result = NaturalTaskParser.parse("Alquiler del 31 del mes que viene", now, zone)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "último día hábil del mes" / "cada último día hábil del mes" (c.575) ---
    // Distinto de "último día del mes" (EOM): el día HÁBIL retrocede de sáb/dom al
    // viernes anterior. now=2026-07-29 → julio tiene 31 días y 7/31 es viernes (día
    // hábil), así que la 1ª ocurrencia = 7/31 y la recurrencia se codifica como el
    // sentinel [RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH] (EOM-BD). Antes este
    // patrón caía a "último día del mes" (EOM) → una tarea de nómina/renta que NO debe
    // caer en fin de semana vencía en sábado → recordatorio inútil (P1 recordatorios +
    // P0 datos: deriva de fecha real). Esta prueba ancla la distinción hábil↔natural.
    @Test fun cadaUltimoDiaHabilDelMesGeneraRecurrenciaMensualEOMBD() {
        val result = NaturalTaskParser.parse("nómina cada último día hábil del mes", now, zone)
        assertEquals("nómina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // 7/31 viernes = día hábil, sin retroceso (cubre el happy path de boundaryDueAt).
    @Test fun cadaUltimoDiaHabilDelMesRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("renta cada último día hábil del mes a las 18", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // Retroceso hábil: con now en octubre 2026, "último día hábil del mes" donde
    // 10/31 = SÁBADO retrocede al viernes 10/30. Sin el fix, caería a "último día del
    // mes" (EOM) venciendo en sábado → recordatorio de pago en fin de semana (P1).
    @Test fun cadaUltimoDiaHabilDelMesRetrocedeDeSabadoAViernes() {
        val octNow = DateRules.toEpochMillis(LocalDate.of(2026, 10, 15), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("nómina cada último día hábil del mes", octNow, zone)
        assertEquals("nómina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_BUSINESS_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 10, 30), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- c.492: genitivo "de/del" antes de límite mensual/anual (P1) ---
    // "Cierre de fin de mes" / "Resumen del fin de mes" / "Plan del fin de año" son la
    // forma natural de nombrar un contenido ("cierre/resumen/plan") cuyo vencimiento es
    // un límite temporal ("fin de mes/año"). El conector "de/del" introduce la frase
    // temporal (genitivo de posesión temporal del contenido), igual que c.490/c.491 para
    // período/semana. Antes el conector NO se consumía → título corrupto con residuo
    // "de"/"del" ("Cierre de") aunque la fecha sí se resolvía. P1: el título es lo que
    // el usuario ve/gestiona en What Now y planificador; un residuo "de" final queda
    // como basura visible y rompe la edición/identificación de la tarea. now=2026-07-29.

    @Test fun genitivoDeAntesDeFinDeMesNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Cierre de fin de mes", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelAntesDeFinDeMesNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Resumen del fin de mes", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDelAntesDeFinDeAnoNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Plan del fin de año", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeFinDeAnoNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Resumen de fin de año", now, zone)
        assertEquals("Resumen", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDePrincipiosDeMesNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Plan de principios de mes", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeMediadosDeMesNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Balance de mediados de mes", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeFinalesDeMesNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Cierre de finales de mes", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDePrincipiosDeAnoNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Informe de principios de año", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeMediadosDeAnoNoDejaResiduo() {
        // now=2026-07-29 ya pasó mediados de 2026 (30/6) → rueda a 2027-06-30.
        val result = NaturalTaskParser.parse("Balance de mediados de año", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(LocalDate.of(2027, 6, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeFinDeMesQueVieneNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Informe de fin de mes que viene", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeFinDeMesConMesExplicitoNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Cierre de fin de mes de diciembre", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeAntesDeMediadosDeMesConMesExplicitoNoDejaResiduo() {
        val result = NaturalTaskParser.parse("Plan de mediados de mes de octubre", now, zone)
        assertEquals("Plan", result.title)
        assertEquals(LocalDate.of(2026, 10, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun genitivoDeCadaFinDeMesRecurreYNoDejaResiduo() {
        // "cada" DENTRO del match (cadaInBoundaryMatch): el genitivo externo "de" debe
        // consumirse igual, y la recurrencia mensual EOM debe preservarse.
        val result = NaturalTaskParser.parse("Balance de cada fin de mes", now, zone)
        assertEquals("Balance", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- Contra-regresión: el genitivo se consume SÓLO si la frase temporal casa.
    // "Cierre del mes" (sin "fin/mediados/principios") NO activa endOfMonthPattern:
    // el conector "del" debe permanecer legítimamente como contenido. "Cierre del mes"
    // cae a monthNamePattern ("mes" no es un mes válido → no resuelve fecha → título
    // íntegro preservado, sin dueAt).
    @Test fun genitivoSinLimiteTemporalPermaneceComoContenido() {
        val result = NaturalTaskParser.parse("Cierre del mes", now, zone)
        assertEquals("Cierre del mes", result.title)
        // No resuelve fecha (no hay día ni mes válido): dueAt puede ser null o fin de mes
        // según el flujo, pero el TÍTULO debe quedar íntegro, sin residuo artificial.
    }

    @Test fun genitivoDeEsteMesNoSeConsumeIncorrectamente() {
        // "Balance de este mes": thisMonthPattern tiene lookbehind que RECHAZA "de " justo
        // antes → NO casa → no se borra nada → el conector "de" queda legítimamente como
        // contenido (no hay frase temporal que resolver). Título íntegro.
        val result = NaturalTaskParser.parse("Balance de este mes", now, zone)
        assertEquals("Balance de este mes", result.title)
    }

    @Test fun genitivoDeEsteAnoNoSeConsumeIncorrectamente() {
        val result = NaturalTaskParser.parse("Resumen de este año", now, zone)
        assertEquals("Resumen de este año", result.title)
    }
    // --- c.471: cadencia "mensual" + anclaje de fin de mes (P1) ---
    // "mensual el último día" / "mensual fin de mes" / "mensual a fin de mes" son las
    // formas cotidianas de alquiler/nómina/pago recurrentes anclados a fin de mes. La
    // cadencia "mensual" actúa como "cada mes" y el límite fija el día. Antes había DOS
    // fallos ligados: (1) "el último día" SIN "de/del mes" no casaba en endOfMonthPattern
    // → dueAt caía al día de captura (vencimiento incorrecto), recurrencia MONTHLY sin
    // anclaje y "el último día" sobrevivía como residuo del título; (2) "mensual fin de
    // mes" / "mensual el último día del mes" SÍ fijaban dueAt a fin de mes, pero la
    // recurrencia se emitía SIN monthlyLastDay (vía fixedPatterns, sin EOM) → al
    // completar la 1ª cita, nextMonthly conservaba base.dayOfMonth=31 y SALTABA los meses
    // cortos (septiembre, abril...), desplazando silenciosamente la rutina. Fix unificado:
    // cuando la cadencia mensual explícita ("mensual"/"cada mes"/"todos los meses")
    // coexiste con un límite de fin de mes, la recurrencia se promueve a MONTHLY+EOM
    // (anclaje al último día REAL del mes objetivo, no omite meses cortos) y el límite
    //"último día" se reconoce también sin "de mes" bajo cadencia (evita el falso positivo
    // "reunión el último día del congreso", que NO lleva cadencia). now=2026-07-29 → fin
    // de mes en curso = 7/31.

    @Test fun mensualUltimoDiaSinDeMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("pago mensual el último día", now, zone)
        assertEquals("pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mensualFinDeMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("renta mensual fin de mes", now, zone)
        assertEquals("renta", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mensualAFinDeMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("factura mensual a fin de mes", now, zone)
        assertEquals("factura", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mensualUltimoDiaDelMesGeneraRecurrenciaMensualEOM() {
        val result = NaturalTaskParser.parse("nómina mensual el último día del mes", now, zone)
        assertEquals("nómina", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(RecurrenceEngine.LAST_DAY_OF_MONTH, result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // Anti-falso-positivo: sin cadencia mensual, "el último día" NO se ancla a fin de mes
    // (no es un límite mensual sino parte del contexto semántico). Antes quedaba en el
    // título (correcto); debe seguir así tras el fix (la guard de cadencia lo impide).
    @Test fun ultimoDiaSinCadenciaMensualNoSeAnclaAFinDeMes() {
        val result = NaturalTaskParser.parse("reunión el último día del congreso", now, zone)
        assertEquals("reunión el último día del congreso", result.title)
        assertNull(result.dueAt)
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

    // --- "en <mes>": nombre de mes suelto tras la preposición "en" (sin día) ---
    // Anclas blandas cotidianas ("apuntarme al gimnasio en septiembre",
    // "viaje en diciembre") caían a dueAt=null y la frase entera quedaba en el
    // título → compromiso del mes olvidado, sin recordatorio ni visibilidad aunque
    // el usuario sí informó el mes. Mismo criterio que "a inicios de <mes>": día 1,
    // roll anual si el día 1 ya pasó; año explícito opcional con "de/del".

    @Test fun enMesNombreAnclaDia1FuturoNoRuedaAnio() {
        // "en septiembre" desde julio 2026 → 1/9/2026 (aún no pasa).
        val result = NaturalTaskParser.parse("Apuntarme al gimnasio en septiembre", now, zone)
        assertEquals("Apuntarme al gimnasio", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enMesNombrePasadoRuedaUnAnio() {
        // "en enero" desde julio 2026: 1/1/2026 ya pasó → 1/1/2027 (como "inicios de enero").
        val result = NaturalTaskParser.parse("Renovar contrato en enero", now, zone)
        assertEquals("Renovar contrato", result.title)
        assertEquals(LocalDate.of(2027, 1, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enMesActualRuedaUnAnioSiDia1YaPaso() {
        // "en julio" dicho el 29/7/2026: el día 1 ya pasó → 1/7/2027 (paridad "inicios de julio").
        val result = NaturalTaskParser.parse("Preparar viaje en julio", now, zone)
        assertEquals("Preparar viaje", result.title)
        assertEquals(LocalDate.of(2027, 7, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enMesNombreConAnioExplicitoNoRueda() {
        // "en agosto de 2027": año explícito → 1/8/2027 (sin roll).
        val result = NaturalTaskParser.parse("Entrega en agosto de 2027", now, zone)
        assertEquals("Entrega", result.title)
        assertEquals(LocalDate.of(2027, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enMesNombreRespetaHoraExplicita() {
        // La hora explícita reemplaza el default 09:00 del ancla de día.
        val result = NaturalTaskParser.parse("Viaje en octubre a las 8", now, zone)
        assertEquals("Viaje", result.title)
        assertEquals(LocalDate.of(2026, 10, 1), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enMesAbreviadoAnclaDia1() {
        // Abreviatura "dic" → diciembre, igual que "el 25 de dic" (monthNamePattern).
        val result = NaturalTaskParser.parse("Cierre en dic", now, zone)
        assertEquals("Cierre", result.title)
        assertEquals(LocalDate.of(2026, 12, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enTalloDeMesNoCasa() {
        // "en marcha"/"en mercado": el tallo "mar" NO es el mes "marzo"; sin \b no
        // debe casar ni anclar fecha (paridad anti-residuo del monthNameGroup).
        val result = NaturalTaskParser.parse("Poner el plan en marcha", now, zone)
        assertEquals("Poner el plan en marcha", result.title)
        assertNull(result.dueAt)
    }

    @Test fun enMesDoNoRobaLimitesExplicitos() {
        // "a inicios de septiembre" (calificador explícito) sigue ganando a "en":
        // una captura con "a inicios de" no debe rebajarse al patrón blando.
        val explicit = NaturalTaskParser.parse("Cobro a inicios de septiembre", now, zone)
        assertEquals("Cobro", explicit.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(explicit.dueAt!!, zone))
        val soft = NaturalTaskParser.parse("Cobro en septiembre", now, zone)
        assertEquals("Cobro", soft.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(soft.dueAt!!, zone))
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

    // --- c.511: calificador de LÍMITE ("a finales/principios/mediados [de la/esta]? quincena") ---
    // El español usa los mismos calificadores de límite coloquial sobre la quincena
    // (cobros/nóminas quincenales: "cobrar a finales de esta quincena", "pago a principios
    // de la próxima quincena"). Los patrones de quincena resolvían la fecha (hito/periodo
    // próximo) pero casaban solo "quincena" y dejaban "a finales/principios/mediados" como
    // residuo de título. La fecha NO cambia (ya resolvía el hito correcto); el cambio solo
    // limpia el título consumiendo el calificador entero. hoy = 2026-07-29 (≥ 15):
    // "esta/la quincena" → fin de mes (31/7); "próxima/que viene" → +15d (13/8).

    @Test fun aFinalesDeEstaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a finales de esta quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeLaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a finales de la quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a finales de quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeEstaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a principios de esta quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMediadosDeEstaQuincenaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a mediados de esta quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeLaProximaQuincenaLimpiaTitulo() {
        // "próxima quincena" la resuelve nextPeriodPattern (+15d): 2026-07-29 + 15d = 2026-08-13.
        val result = NaturalTaskParser.parse("Cita a principios de la proxima quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeLaQuincenaQueVieneLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a principios de la quincena que viene", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMediadosDeLaQuincenaQueVieneLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita a mediados de la quincena que viene", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeEstaQuincenaSinAInicialLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita finales de esta quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeLaProximaQuincenaSinAInicialLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Cita principios de la proxima quincena", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- Rangos de weekdays generalizados (cualquier par, no sólo Lun-Vie) ---
    // Antes el patrón de rango sólo casaba el par literal "lunes ... viernes";
    // cualquier otro par ("de martes a jueves", "de miércoles a viernes", "de
    // domingo a jueves") caía a dayListPattern, que capturaba SOLO el día inicial
    // (1 elemento, no recurrencia) y dejaba el día de cierre como residuo del
    // título ("Gimnasio a") y la rutina se perdía en silencio (P1: hábito olvidado).
    // Ahora el rango se expande inclusivo hacia adelante (con wraparound si inicio
    // > cierre). Las formas CON artículo ("del martes al jueves") siguen siendo
    // evento único (curso/conferencia) y NO se ven afectadas. c.487.
    @Test fun rangoWeekdayMarJueEsRecurrenciaInclusiva() {
        val result = NaturalTaskParser.parse("Gimnasio de martes a jueves", now, zone)
        assertEquals("Gimnasio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("2,3,4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoWeekdayMieVieEsRecurrenciaInclusiva() {
        val result = NaturalTaskParser.parse("Estudio de miercoles a viernes", now, zone)
        assertEquals("Estudio", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("3,4,5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoWeekdayDomJueEsRecurrenciaConWraparound() {
        // "de domingo a jueves" cruza el límite de semana: dom(7),lun..jue(4).
        val result = NaturalTaskParser.parse("Clase de domingo a jueves", now, zone)
        assertEquals("Clase", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("7,1,2,3,4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoWeekdayLunSabIncluyeFinDeSemana() {
        val result = NaturalTaskParser.parse("Curso de lunes a sabado", now, zone)
        assertEquals("Curso", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5,6", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoWeekdayVieLunWraparoundFinDeSemana() {
        // "de viernes a lunes" = vie,sáb,dom,lun (rutina de fin de semana extendido).
        val result = NaturalTaskParser.parse("Futbol de viernes a lunes", now, zone)
        assertEquals("Futbol", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("5,6,7,1", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoEntreMartesJuevesEsRangoNoLista() {
        // "entre martes y jueves" antes caía a dayListPattern como lista {2,4} (rutina
        // mutilada: faltaba el miércoles); ahora es el rango inclusivo {2,3,4}.
        val result = NaturalTaskParser.parse("Natacion entre martes y jueves", now, zone)
        assertEquals("Natacion", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("2,3,4", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun rangoWeekdayConArticuloSigueSiendoEventoUnico() {
        // "del martes al jueves" (con artículo) sigue siendo evento único anclado al
        // cierre (jueves 30-07), NO recurrencia. Regresión de seguridad: la
        // generalización del rango no convierte un curso/conferencia de varios días
        // en hábito semanal.
        val result = NaturalTaskParser.parse("curso del martes al jueves", now, zone)
        assertEquals("curso", result.title.trim())
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
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

    // c.641 — Intensificador "misma/mismo": "esta misma semana", "este mismo mes",
    // "este mismo año" son sinónimo enfático del período ACTUAL (mismo rango que
    // "esta semana"/"este mes"/"este año"). El intensificador NO cambia el plazo,
    // solo recalca que es el período en curso. Antes these forms caían a dueAt=null
    // (olvido de vencimiento, P1) y la frase íntegra quedaba como residuo en el título.

    @Test fun estaMismaSemanaAnclaFinSemanaActual() {
        val result = NaturalTaskParser.parse("Llamar a mamá esta misma semana", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMismoMesAnclaFinMesActual() {
        val result = NaturalTaskParser.parse("Renovar licencia este mismo mes", now, zone)
        assertEquals("Renovar licencia", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMismoAnoAncla31Diciembre() {
        val result = NaturalTaskParser.parse("Cerrar ejercicio este mismo a\u00f1o", now, zone)
        assertEquals("Cerrar ejercicio", result.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun estaMismaSemanaQueVieneNoDejaResiduo() {
        // "esta misma semana que viene" (sinónimo confuso de "esta semana que viene"):
        // antes el título quedaba "enviar el correo esta misma" con residuo.
        val result = NaturalTaskParser.parse("Enviar correo esta misma semana que viene", now, zone)
        assertEquals("Enviar correo", result.title)
    }

    // c.643 — Extensión del intensificador a los límites mensuales (mediados/finales/
    // principios/último día hábil de "este mismo mes"). Antes estas formas caían a
    // dueAt=null (vencimiento enfático olvidado, P1): los patrones de boundary mensual
    // aceptaban "este mes" pero NO "este mismo mes". Misma familia que c.641/c.642;
    // el intensificador recalca el período en curso sin cambiar el rango. now=2026-07-29.

    @Test fun mediadosDeEsteMismoMesNoCaeANull() {
        val result = NaturalTaskParser.parse("Pagar renta a mediados de este mismo mes", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finalesDeEsteMismoMesAnclaFinMesActual() {
        val result = NaturalTaskParser.parse("Pagar renta a finales de este mismo mes", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun principiosDeEsteMismoMesNoCaeANull() {
        val result = NaturalTaskParser.parse("Pagar renta a principios de este mismo mes", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeEsteMismoMesAnclaFinMesActual() {
        val result = NaturalTaskParser.parse("Reunión a fin de este mismo mes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun ultimoDiaHabilDeEsteMismoMesNoCaeANull() {
        val result = NaturalTaskParser.parse("Cobro el último día hábil de este mismo mes", now, zone)
        assertEquals("Cobro", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // "a fin de la semana": plazo = fin de la semana actual (próximo domingo).
    // Sinónimo coloquial de "esta semana". Antes caía a dueAt=null → olvido.

    @Test fun finDeLaSemanaResuelveProximoDomingo() {
        val result = NaturalTaskParser.parse("Entregar a fin de la semana", now, zone)
        assertEquals("Entregar", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.672 — Intensificador POST-puesto: "esta semana misma"/"este mes mismo" son el
    // mismo ancla del período en curso que "esta misma semana"/"este mismo mes" (c.641).
    // Antes la fecha resolvía pero "misma"/"mismo" quedaba como residuo en el título
    // (el match consumía solo la forma pre-puesta). El intensificador es semánticamente
    // neutro (recalca el período en curso), así que debe limpiarse siempre.

    @Test fun estaSemanaMismaPospuestaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar propuesta esta semana misma", now, zone)
        assertEquals("Revisar propuesta", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun esteMesMismoPospuestoLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pagar renta este mes mismo", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeLaSemanaMismaPospuestaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Informe a fin de la semana misma", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun finDeEsteMesMismoPospuestoLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pagar renta a fin de este mes mismo", now, zone)
        assertEquals("Pagar renta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun laSemanaMismaSinDeterminanteNoCasa() {
        // Guard: sin "esta/este" no se consume un "misma/mismo" colgado por error; el
        // intensificador nunca debe limpiarse si no hay ancla.
        val result = NaturalTaskParser.parse("revisar la semana", now, zone)
        assertEquals("revisar la semana", result.title)
        assertNull(result.dueAt)
    }

    // c.488: variantes "finales de la semana" / "al final de la semana" (sin "que viene")
    // antes caían a dueAt=null + frase íntegra como residuo en el título → vencimiento
    // olvidado (P1). Ahora resuelven al domingo de esta semana (próximo domingo).
    // now = 2026-07-29 (miércoles) -> domingo de esta semana = 2026-08-02.

    @Test fun finalesDeLaSemanaResuelveProximoDomingo() {
        val result = NaturalTaskParser.parse("Reunión finales de la semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeLaSemanaResuelveProximoDomingo() {
        val result = NaturalTaskParser.parse("Reunión a finales de la semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alFinalDeLaSemanaResuelveProximoDomingo() {
        val result = NaturalTaskParser.parse("Reunión al final de la semana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.488: "... fin de la semana que viene" antes casaba como "fin de la semana" (esta
    // semana) y el modificador "que viene" se perdía silenciosamente → fecha errónea
    // (domingo de esta semana en vez del domingo de la semana próxima). Ahora el
    // modificador "que viene" suma +7d, simétrico a "esta semana que viene".

    @Test fun finDeLaSemanaQueVieneResuelveDomingoSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión fin de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeLaSemanaQueVieneResuelveDomingoSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión a finales de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeLaSemanaQueVieneResuelveDomingoSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión a fin de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alFinalDeLaSemanaQueVieneResuelveDomingoSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión al final de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.488: "fin de la semana que viene" no debe dejar residuo ("que viene" ya no
    // sobrevive porque el patrón lo consume entero).

    @Test fun finDeLaSemanaQueVieneNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("Reunión a finales de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
    }

    // c.488: con hora explícita, el plazo blando +7d debe respetar la hora.

    @Test fun finDeLaSemanaQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión a finales de la semana que viene a las 18", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // c.506: "a finales/principios/mediados de esta semana" y "... de la proxima semana"
    // antes dejaban el calificador ("a finales de" / "a principios de") como residuo en el
    // título porque los patrones de límite de semana no aceptaban determinante intermedio
    // ("esta"/"proxima"). Ahora consumen la frase completa y anclan correctamente.
    // now = 2026-07-29 (miércoles). Domingo de esta semana = 2026-08-02; de la próxima =
    // 2026-08-09. Lunes de la semana próxima (previousOrSame(MON)+1sem) = 2026-08-03.
    // Miércoles más cercano en hoy/futuro = 2026-07-29 (hoy); miércoles próxima = 2026-08-05.

    @Test fun aFinalesDeEstaSemanaLimpiaTituloYAnclaDomingoEstaSemana() {
        val result = NaturalTaskParser.parse("Cita a finales de esta semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 2), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeLaProximaSemanaLimpiaTituloYAnclaDomingoProxima() {
        val result = NaturalTaskParser.parse("Cita a finales de la proxima semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinalesDeLaProximaSemanaConTildeLimpiaTituloYAnclaDomingoProxima() {
        val result = NaturalTaskParser.parse("Cita a finales de la próxima semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 9), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeEstaSemanaLimpiaTituloYAnclaLunesEstaSemana() {
        // hoy = miércoles 2026-07-29 -> el lunes de esta semana ya pasó -> lunes siguiente 2026-08-03.
        val result = NaturalTaskParser.parse("Cita a principios de esta semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeLaProximaSemanaLimpiaTituloYAnclaLunesProxima() {
        val result = NaturalTaskParser.parse("Cita a principios de la proxima semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMediadosDeEstaSemanaLimpiaTituloYAnclaMiercolesEstaSemana() {
        // hoy = miércoles 2026-07-29 -> miércoles más cercano en hoy/futuro = hoy.
        val result = NaturalTaskParser.parse("Cita a mediados de esta semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMediadosDeLaProximaSemanaLimpiaTituloYAnclaMiercolesProxima() {
        val result = NaturalTaskParser.parse("Cita a mediados de la proxima semana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // c.489: "... principios/mediados de la semana que viene" antes casaban como
    // "principios/mediados de la semana" (esta semana) y el modificador "que viene" se
    // perdía silenciosamente → fecha errónea (lunes/miércoles de esta semana en vez del
    // de la semana próxima). Simétrico al fix c.488 para "finales de la semana que viene".
    // now = 2026-07-29 (miércoles) -> semana próxima: lunes 2026-08-03, miércoles 2026-08-05.

    @Test fun principiosDeLaSemanaQueVieneResuelveLunesSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión principios de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPrincipiosDeLaSemanaQueVieneResuelveLunesSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión a principios de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun iniciosDeLaSemanaQueVieneSinonimoDePrincipios() {
        val result = NaturalTaskParser.parse("Reunión a inicios de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mediadosDeLaSemanaQueVieneResuelveMiercolesSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión mediados de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMediadosDeLaSemanaQueVieneResuelveMiercolesSemanaProxima() {
        val result = NaturalTaskParser.parse("Reunión a mediados de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun mitadDeLaSemanaQueVieneSinonimoDeMediados() {
        val result = NaturalTaskParser.parse("Reunión a mitad de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // c.489: el modificador "que viene" no debe dejar residuo en el título (el patrón lo
    // consume entero) y la hora explícita debe respetarse sobre el día desplazado.

    @Test fun principiosDeLaSemanaQueVieneNoDejaResiduoEnTitulo() {
        val result = NaturalTaskParser.parse("Reunión a principios de la semana que viene", now, zone)
        assertEquals("Reunión", result.title)
    }

    @Test fun mediadosDeLaSemanaQueVieneRespetaHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión a mediados de la semana que viene a las 18", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
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

    // --- Diminutivos PASADOS "hace un ratito"/"hace un ratico"/"hace un momentito" (c.366) ---
    // Espejo PASADO de los diminutivos futuros de c.365 ("en un ratito" -> +1 h). Antes
    // [agoPattern] (alternativa "un rato") NO casaba "ratito" y robaba solo "hace un"
    // (-> "un"=1, unidad vacía -> -3 h) dejando "ratito"/"ratico"/"momentito" como RESIDUO
    // en el título ("hace un ratito llamé" -> título "ratito llamé"). La fecha era correcta
    // en magnitud (-3 h) pero el título quedaba corrupto. Ahora se consume la frase completa
    // (prefijo "hace" incluido) y se resuelve a -3 h (misma heurística que "hace un rato").
    @Test fun haceUnRatitoLimpiaTituloSinResiduo() {
        val result = NaturalTaskParser.parse("Llamé hace un ratito", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun haceUnRaticoLimpiaTituloSinResiduo() {
        val result = NaturalTaskParser.parse("Llamé hace un ratico", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnMomentitoLimpiaTituloSinResiduo() {
        val result = NaturalTaskParser.parse("Avisé hace un momentito", now, zone)
        assertEquals("Avisé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnRatitoAlFinalDeLaFraseLimpiaTitulo() {
        // El diminutivo puede ir al final ("lo envié hace un ratito"): el residuo no debe
        // quedar en el título aunque la frase venga después del verbo.
        val result = NaturalTaskParser.parse("lo envié hace un ratito", now, zone)
        assertEquals("lo envié", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnRatitoSoloProduceFechaPasada() {
        // "hace un ratito" sin verbo: la fecha pasada se resuelve (-3 h). El título
        // queda con la frase restante igual que "hace un rato"/"hace poco" solos (la
        // app trata el input completo como título cuando no hay verbo). Lo importante
        // es que NO queda el residuo "ratito" suelto de un match parcial de agoPattern.
        val result = NaturalTaskParser.parse("hace un ratito", now, zone)
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

    // --- Cuantificadores vagos en pasado ("un par de"/"unos"/"unas") ---
    // Simétrica PASADA del lado futuro (vagueQuantitativeRelativePattern, c.242). Antes
    // el agoPattern NO incluía "un par de|unos|unas" en su alternancia de cantidad:
    // "hace unos minutos"/"hace unas horas"/"hace unos días" caían a dueAt=null → tarea
    // vencida olvidada (P1 evitar olvidos); y "hace un par de horas" robaba solo "hace un"
    // (→ -3 h heurística "un rato") dejando "par de horas" como residuo en el título
    // (fecha errónea + título sucio). Ahora se resuelven como 2 unidades y la frase se
    // consume completa para título limpio.
    @Test fun haceUnParDeHorasResuelveDosHorasYPoneTituloLimpio() {
        // 12:00 - 2 h = 10:00 mismo día. Título sin residuo "par de horas".
        val result = NaturalTaskParser.parse("Llamé hace un par de horas", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnParDeDiasResuelveDosDiasPasados() {
        // 2026-07-29 - 2 días = 2026-07-27.
        val result = NaturalTaskParser.parse("Pagué hace un par de días", now, zone)
        assertEquals("Pagué", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceUnosMinutosResuelveDosMinutosPasados() {
        // 12:00 - 2 min = 11:58 mismo día.
        val result = NaturalTaskParser.parse("envié hace unos minutos", now, zone)
        assertEquals("envié", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(11, 58), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnasHorasResuelveDosHorasPasadas() {
        // 12:00 - 2 h = 10:00 mismo día.
        val result = NaturalTaskParser.parse("revisé hace unas horas", now, zone)
        assertEquals("revisé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun haceUnosDiasResuelveDosDiasPasados() {
        // 2026-07-29 - 2 días = 2026-07-27.
        val result = NaturalTaskParser.parse("completé hace unos días", now, zone)
        assertEquals("completé", result.title)
        assertEquals(LocalDate.of(2026, 7, 27), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceUnasSemanasResuelveDosSemanasPasadas() {
        // 2026-07-29 - 14 días = 2026-07-15.
        val result = NaturalTaskParser.parse("envié hace unas semanas", now, zone)
        assertEquals("envié", result.title)
        assertEquals(LocalDate.of(2026, 7, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceUnParDeMesesResuelveDosMesesPasados() {
        // 2026-07-29 - 2 meses (60 días) = 2026-05-30.
        val result = NaturalTaskParser.parse("audité hace un par de meses", now, zone)
        assertEquals("audité", result.title)
        assertEquals(LocalDate.of(2026, 5, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haceUnParDeHorasConHoraExplicitaRespetaHora() {
        // La hora explícita se aplica sobre la fecha pasada (simetría con "hace 2 días a las 10").
        val result = NaturalTaskParser.parse("Llamé hace un par de horas a las 9", now, zone)
        assertEquals("Llamé", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
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

    // --- Idioma "de hoy en ocho/quince/N (días)": coloquialismo (+N días) ---
    // P1: sin unidad no casaba ningún relativo → keyword "hoy" agendaba PARA HOY y
    // "en ocho" quedaba de residuo en el título (fecha errónea + título sucio).
    // P2: con "días" el prefijo "de hoy en" no casaba → el alert residual caía al
    // fallback y el título resucitaba el texto completo ("de hoy en quince días").

    @Test fun deHoyEnOchoResuelveMasOchoDias() {
        // now 2026-07-29 + 8 días = 2026-08-06.
        val result = NaturalTaskParser.parse("llamar de hoy en ocho", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 8, 6), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyEnQuinceResuelveMasQuinceDias() {
        // now 2026-07-29 + 15 días = 2026-08-13.
        val result = NaturalTaskParser.parse("revisar de hoy en quince", now, zone)
        assertEquals("revisar", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyEnQuinceDiasLimpiaTitulo() {
        // Con unidad explícita: el prefijo completo se consume y el título queda limpio.
        val result = NaturalTaskParser.parse("cita de hoy en quince días", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 13), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyEnDigitosResuelveMasNDias() {
        // now 2026-07-29 + 30 días = 2026-08-28.
        val result = NaturalTaskParser.parse("informe de hoy en 30", now, zone)
        assertEquals("informe", result.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyEnOchoDiasResuelveMasOchoDias() {
        val result = NaturalTaskParser.parse("cita de hoy en ocho días", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 6), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deHoyConUnidadNoDiaUsaLaUnidadReal() {
        // Guard anti-falso-positivo: el idioma sin unidad no debe robar "8 horas" ni
        // "15 minutos": la unidad explícita gana (lookahead negativo del idiom).
        val result = NaturalTaskParser.parse("entrega de hoy en 8 horas", now, zone)
        assertEquals("entrega", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deHoyEnOchoConHoraExplicita() {
        // El idioma es a nivel día y se combina con la hora explícita: +8d a las 10:00.
        val result = NaturalTaskParser.parse("cita de hoy en ocho a las 10", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 8, 6), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun deHoyEnAdelanteUsaHoyYLimpiaTitulo() {
        // Follow-up P3 de c.667 (c.668): sin cantidad cae al keyword "hoy" (fecha correcta)
        // y pre-fix el título quedaba con el residuo "en adelante". Ahora la frase íntegra
        // se consume en la limpieza del título.
        val result = NaturalTaskParser.parse("llamar de hoy en adelante", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun enAdelanteNoDetectadoSinDeHoy() {
        // Guard anti-falso-positivo: "en adelante" suelto (sin "de hoy") no se toca.
        val result = NaturalTaskParser.parse("entrevista en adelante", now, zone)
        assertEquals("entrevista en adelante", result.title)
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

    // --- Rango de días de la semana como evento ÚNICO ("del lunes al viernes"):
    // [weekdayPattern].find anclaba al PRIMER día ("del lunes", consumido por su
    // prefijo `del`) y el segundo ("al viernes") nunca se re-emparejaba → el conector
    // "al" sobrevivía pegado al título ("reunión al") con la fecha anclada al INICIO
    // en vez del cierre (P2 contenido capturado degradado). Ahora [weekdayPairRangePattern]
    // reescribe el rango contracto "del X al Y" al CIERRE ("el viernes"), simétrico a
    // [dayRangePattern] numérico (c.377, "del 15 al 20 de diciembre"→"el 20"): ancla al
    // vencimiento/cierre del evento, sin recurrencia fantasma.

    @Test fun delLunesAlViernesParsesComoEventoUnicoAncladoAlCierre() {
        val result = NaturalTaskParser.parse("reunión del lunes al viernes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    @Test fun delMartesAlJuevesParsesComoEventoUnicoAncladoAlCierre() {
        val result = NaturalTaskParser.parse("taller del martes al jueves", now, zone)
        assertEquals("taller", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    // --- "al <día de semana>" SUELTO ("reunión al viernes", "llamar al sábado"):
    // la contracción direccional-temporal "al" es un introductor tan cotidiano como
    // "el viernes", pero [weekdayPattern] sólo admite los prefijos el|del|de|este (no
    // "al"). Así "al viernes" se capturaba como weekday pelado ("viernes") y el
    // conector "al" sobrevivía pegado al título ("reunión al"). Ahora el rewriter
    // "al <weekday>"→"el <weekday>" normaliza (exige un día de la semana real para no
    // tocar "ir al cine" ni "almorzar al mediodía").

    @Test fun alViernesSueltoParsesSinResiduoAl() {
        val result = NaturalTaskParser.parse("reunión al viernes", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alSabadoSueltoParsesSinResiduoAl() {
        val result = NaturalTaskParser.parse("salida al sábado", now, zone)
        assertEquals("salida", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alCineNoSeTocaComoFecha() {
        // "ir al cine": "al" NO precede a un día de la semana → el rewriter no actúa y
        // "al cine" permanece como contenido del título (no se inventa fecha).
        val result = NaturalTaskParser.parse("ir al cine", now, zone)
        assertEquals("ir al cine", result.title)
    }

    // --- Respaldo de título vacío (c.379): cuando el usuario escribe SÓLO una frase de
    // agenda ("al viernes", "de aquí al 15") sin acción, `working` queda en blanco y el
    // respaldo resucitaba el `original` crudo con el conector "al"/"de aquí al" como
    // título visible. Ahora se aplican los mismos reescritores al respaldo → "el viernes".

    @Test fun alViernesSoloFallbackSinResiduoAl() {
        val result = NaturalTaskParser.parse("al viernes", now, zone)
        assertEquals("el viernes", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alViernesQueVieneSoloFallbackSinResiduoAl() {
        val result = NaturalTaskParser.parse("al viernes que viene", now, zone)
        assertEquals("el viernes que viene", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun alProximoViernesSoloFallbackSinResiduoAl() {
        val result = NaturalTaskParser.parse("al próximo viernes", now, zone)
        assertEquals("el próximo viernes", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAlViernesSoloFallbackSinResiduoDeAquiAl() {
        val result = NaturalTaskParser.parse("de aquí al viernes", now, zone)
        assertEquals("el viernes", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAMananaSoloFallbackSinResiduoDeAquiA() {
        val result = NaturalTaskParser.parse("de aquí a mañana", now, zone)
        assertEquals("mañana", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun deAquiAl15SoloFallbackSinResiduoDeAquiAl() {
        val result = NaturalTaskParser.parse("de aquí al 15", now, zone)
        assertEquals("el 15", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun reunionConAccionNoUsaFallback() {
        // Caso con acción: `working` NO queda en blanco → no se toca el respaldo.
        val result = NaturalTaskParser.parse("entregar de aquí al viernes", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
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

    // --- "<período> sí [y] <período> no" = cada dos períodos (c.804, P1) ---
    // La forma nativa "<período> sí <período> no" (día/semana/mes) significa cada
    // dos períodos, idéntica a "cada dos días"/"un día sí y otro no". Antes caía a
    // NONE → rutina sin cadencia ni fecha (medicación/limpieza/pago olvidados:
    // recordatorio jamás disparaba, invisible en What Now); con hora explícita la
    // cadencia se perdía y la frase quedaba como residuo en el título. Se mapea a
    // interval=2 del período natural: día→DAILY/2, semana→WEEKLY/2, mes→MONTHLY/2.
    @Test fun diaSiDiaNoParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Gym día sí día no", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun diaSiYDiaNoParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Gym día sí y día no", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unDiaSiUnDiaNoParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Medicina un día sí un día no", now, zone)
        assertEquals("Medicina", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun semanaSiSemanaNoParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Limpieza semana sí semana no", now, zone)
        assertEquals("Limpieza", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaSemanaSiYOtraNoParsesWeeklyInterval2() {
        val result = NaturalTaskParser.parse("Visita una semana sí y otra no", now, zone)
        assertEquals("Visita", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun mesSiMesNoParsesMonthlyInterval2() {
        val result = NaturalTaskParser.parse("Pago mes sí mes no", now, zone)
        assertEquals("Pago", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun diaSiDiaNoConHoraMantieneCadenciaYLimpiaTitulo() {
        // Antes: la hora (07:00) se resolvía pero la cadencia caía a NONE y
        // "día sí día no" quedaba como residuo pegado al título.
        val result = NaturalTaskParser.parse("Gym día sí día no a las 7", now, zone)
        assertEquals("Gym", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertEquals(LocalTime.of(7, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun unDiaSiYOtroTambienNoEsRecurrencia() {
        // Guard: "otro también" no es la cadencia "sí…no" (falta el cierre "no").
        val result = NaturalTaskParser.parse("Comprar un día sí y otro también", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("Comprar un día sí y otro también", result.title)
    }

    @Test fun semanaSiFueDuraNoEsRecurrencia() {
        // Guard: "sí" afirmativo seguido de verbo, sin período de cierre + "no".
        val result = NaturalTaskParser.parse("La semana sí fue dura", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("La semana sí fue dura", result.title)
    }


    // --- "N veces por semana" / "N veces al día" / "N veces al mes" ---
    // Cadencia de frecuencia cotidiana ("ir al gym tres veces por semana", "tomar
    // medicamento 3 veces al día"). Antes caían a NONE + dueAt=null → la rutina
    // nacía sin cadencia ni fecha (recordatorio jamás disparaba, invisible en What
    // Now: P1 evitar olvidos/rutinas) y la frase entera quedaba como residuo en el
    // título. Sin modelo exacto de "N veces" en la cadencia (intervalos enteros),
    // se mapea al intervalo diario/horario más próximo (truncado: "3 veces por
    // semana" → cada 2 días; "3 veces al día" → cada 8 horas — exacto; "2 veces al
    // mes" → cada 15 días — exacto). n=1 usa la frecuencia natural del período.
    @Test fun dosVecesPorSemanaParsesDailyInterval3() {
        val result = NaturalTaskParser.parse("Regar las plantas dos veces por semana", now, zone)
        assertEquals("Regar las plantas", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun tresVecesPorSemanaParsesDailyInterval2() {
        val result = NaturalTaskParser.parse("Ir al gym tres veces por semana", now, zone)
        assertEquals("Ir al gym", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaVezPorSemanaParsesWeekly() {
        val result = NaturalTaskParser.parse("Revisión una vez por semana", now, zone)
        assertEquals("Revisión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun dosVecesALaSemanaParsesDailyInterval3() {
        val result = NaturalTaskParser.parse("Piano 2 veces a la semana", now, zone)
        assertEquals("Piano", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(3, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun tresVecesAlDiaParsesHourlyInterval8() {
        val result = NaturalTaskParser.parse("Tomar medicamento 3 veces al día", now, zone)
        assertEquals("Tomar medicamento", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(8, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun dosVecesAlDiaParsesHourlyInterval12() {
        val result = NaturalTaskParser.parse("Correr dos veces al día", now, zone)
        assertEquals("Correr", result.title)
        assertEquals(RecurrenceFrequency.HOURLY, result.recurrence)
        assertEquals(12, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaVezAlDiaParsesDaily() {
        val result = NaturalTaskParser.parse("Meditación una vez al día", now, zone)
        assertEquals("Meditación", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun dosVecesAlMesParsesDailyInterval15() {
        val result = NaturalTaskParser.parse("Nómina dos veces al mes", now, zone)
        assertEquals("Nómina", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(15, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaVezPorMesParsesMonthly() {
        val result = NaturalTaskParser.parse("Corte de cabello una vez por mes", now, zone)
        assertEquals("Corte de cabello", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun vecesSinPeriodoNoEsCadencia() {
        // "dos veces" sin período no es cadencia: no debe emitir recurrencia.
        val result = NaturalTaskParser.parse("Repasar el tema dos veces", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
    }

    // --- "N veces al año" (período anual de la familia N-veces) ---
    // Contraparte anual de "N veces por semana/al mes/al día": "revisión médica dos
    // veces al año", "matrícula una vez al año". Antes caía a NONE + dueAt=null → la
    // rutina nacía sin cadencia ni fecha (recordatorio jamás disparaba, invisible en
    // What Now: P1 evitar olvidos) y la frase entera quedaba como residuo en el título.
    // n=1 → YEARLY (frecuencia natural del período, simétrico a una vez por semana→
    // WEEKLY); n≥2 → MONTHLY intervalo ⌊12/n⌋ (2→6 = semestral, 3→4 = cuatrimestral,
    // 4→3 = trimestral, 6→2 = bimestral, 12→1 = mensual), truncado hacia MÁS frecuente
    // igual que el resto de la familia (sin enum ni migración nuevos).
    @Test fun dosVecesAlAnoParsesMonthlyInterval6() {
        val result = NaturalTaskParser.parse("Revisión médica dos veces al año", now, zone)
        assertEquals("Revisión médica", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(6, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun unaVezAlAnoParsesYearly() {
        val result = NaturalTaskParser.parse("Matrícula una vez al año", now, zone)
        assertEquals("Matrícula", result.title)
        assertEquals(RecurrenceFrequency.YEARLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun tresVecesAlAnoParsesMonthlyInterval4() {
        val result = NaturalTaskParser.parse("Limpieza profunda tres veces al año", now, zone)
        assertEquals("Limpieza profunda", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(4, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun doceVecesAlAnoParsesMonthly() {
        val result = NaturalTaskParser.parse("Informe 12 veces al año", now, zone)
        assertEquals("Informe", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun cuantasVecesAlAnoNoEsCadencia() {
        // Guard: pregunta sobre el pasado, no cadencia. "cuántas" no es número escrito
        // → no captura; la rutina NO aparece (título intacto, sin recurrencia).
        val result = NaturalTaskParser.parse("cuántas veces al año voy al médico", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
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

    // c.495 regresión: Java trata las vocales acentuadas como NO-palabra, así un primer
    // intento con \b consumía la "a" final de "Auditoría"/"Día"/"Garantía" cuando una
    // cadencia seguía. La "a" distributiva sólo casa como palabra suelta (precedida de
    // espacio/inicio). "Día" termina en "a" tras una vocal acentuada: guarda-runner.
    @Test fun tituloTerminadoEnAConAcentoNoPierdeAFinal() {
        val result = NaturalTaskParser.parse("Día cada semana", now, zone)
        assertEquals("Día", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
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

    // c.674: el conector PLURAL "los días N" también debe consumirse entero.
    // PRE-fix monthlyDayPattern sólo admitía el singular `(día)`; el plural quedaba
    // como residuo del título ("pagar la luz los días") al anillar el día N.
    @Test fun losDiasMonthlyRecurrenceCleanTitle() {
        val result = NaturalTaskParser.parse("pagar la luz los días 15 de cada mes", now, zone)
        assertEquals("pagar la luz", result.title)
        assertEquals(RecurrenceFrequency.MONTHLY, result.recurrence)
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

    // Forma plural del adjetivo: "a últimas horas" (variante cotidiana de
    // "a última hora"). Antes dejaba residuo en el título aunque el dueAt se resolvía
    // vía la parte del día. c.400: limpieza simétrica al singular.
    @Test fun ultimasHorasLimpiaTituloYRespetaParteDelDia() {
        val result = NaturalTaskParser.parse("Reunión a últimas horas de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ultimasHorasSinParteDelDiaUsaCanonicaUltimaHora() {
        val result = NaturalTaskParser.parse("Terminar a últimas horas", now, zone)
        assertEquals("Terminar", result.title)
        assertFalse(result.title.contains("últimas", ignoreCase = true))
        assertFalse(result.title.contains("horas", ignoreCase = true))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // "a última hora del día/días/jornada": sufijo de cierre de jornada, simétrico
    // del de "a primera hora del día/días/jornada" (c.546). Antes el patrón de
    // última hora SÓLO admitía "de la (mañana|tarde|noche|madrugada)", así que
    // "a última hora del día" resolvía la hora canónica (18:00) pero dejaba
    // "del día"/"de la jornada" como residuo en el título. c.548: extensión de
    // sufijo simétrica para cerrar la asimetría mañana/tarde.
    @Test fun ultimaHoraDelDiaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar a última hora del día", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ultimaHoraDeLaJornadaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Revisar a última hora de la jornada", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun ultimasHorasDelDiaLimpiaTituloFormaPlural() {
        val result = NaturalTaskParser.parse("Revisar a últimas horas del día", now, zone)
        assertEquals("Revisar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- Variantes cotidianas de fin de jornada: "a fin de día/dia", "al fin del
    // día/dia" (sin "final"). Antes estas formas abreviadas no casaban el patrón de
    // "al final del día" → dueAt=null (tarea SIN vencimiento → olvidada, invisible en
    // What Now/planificador, sin recordatorio) y la frase quedaba como residuo en el
    // título. c.426: el patrón ahora acepta "al fin"/"a fin" + "del día"/"de día".

    @Test fun aFinDeDiaInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a fin de día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aFinDeDiaSinTildeTambienFunciona() {
        val result = NaturalTaskParser.parse("Reunión a fin de dia", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinDelDiaInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión al fin del día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinDelDiaSinTildeTambienFunciona() {
        val result = NaturalTaskParser.parse("Reunión al fin del dia", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aFinDeDiaNoColisionaConFinDeMes() {
        // "a fin de mes" es un concepto distinto (fin de mes calendárico), no fin de
        // jornada 18:00. Debe seguir resolviéndose por su propio patrón de fecha.
        val result = NaturalTaskParser.parse("Reunión a fin de mes", now, zone)
        assertEquals("Reunión", result.title)
        // No debe ser 18:00 de hoy; fin de mes es otra fecha.
        assertNotEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    // --- "al inicio del día" / "al inicio de la jornada" (sinónimo de "a primera hora").
    // Asimetría flagrante con "al final del día"=18:00 (c.2247): el fin de jornada SÍ se
    // interpretaba como hora canónica, pero el inicio de jornada NO — "al inicio del día"
    // dejaba la tarea SIN dueAt (olvidada, invisible en What Now/planificador, sin
    // recordatorio) y la frase quedaba como residuo en el título. Ahora resuelve a 09:00
    // (inicio de jornada, simétrico de "a primera hora") y limpia el título. Exige el
    // conector "al "/"a " + "día/jornada" para no colisionar con "al inicio del proyecto"
    // ni "fase inicial del día".

    @Test fun alInicioDelDiaInterpretaInicioJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión al inicio del día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alInicioDelDiaSinTildeTambienFunciona() {
        val result = NaturalTaskParser.parse("Reunión al inicio del dia", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alInicioDeLaJornadaFunciona() {
        val result = NaturalTaskParser.parse("Empezar al inicio de la jornada", now, zone)
        assertEquals("Empezar", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alInicioDelDiaConFechaRelativaCombinaBien() {
        val result = NaturalTaskParser.parse("Llamar mañana al inicio del día", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alInicioDelDiaNoEsFalsoPositivoEnFraseDistinta() {
        // "al inicio del proyecto": "inicio" sin "día/jornada" no es inicio de jornada.
        // No debe asignar 09:00 ni borrar nada.
        val result = NaturalTaskParser.parse("Reunión al inicio del proyecto", now, zone)
        assertEquals("Reunión al inicio del proyecto", result.title)
        assertEquals(null, result.dueAt)
    }

    // --- "a primera hora del día": el patrón de "a primera hora" casaba y resolvía 09:00,
    // PERO su sufijo opcional sólo admitía "de la mañana/madrugada", no "del día", así que
    // "del día" sobrevivía como residuo en el título ("Reunión del día"). Ahora el sufijo
    // también consume "del día/día/días/jornada" (simétrico a como alFinalDelDiaPattern
    // cubre esas variantes), dejando el título limpio.

    @Test fun primeraHoraDelDiaLimpiaResiduoDelDiaDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión a primera hora del día", now, zone)
        assertEquals("Reunión", result.title)
        assertFalse(result.title.contains("día", ignoreCase = true))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun primerasHorasDelDiaLimpiaResiduoDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión a primeras horas del día", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun primeraHoraDeLaJornadaLimpiaResiduoDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión a primera hora de la jornada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "a último momento" (sinónimo masculino de "a última hora"). Antes el patrón
    // de "a última hora" exigía el adjetivo en femenino ("última") y no casaba la
    // forma masculina "último momento" → dueAt=null (tarea SIN vencimiento →
    // olvidada) y la frase quedaba como residuo en el título. c.426: el patrón ahora
    // acepta "último momento" (masculino) además de "última hora(s)" (femenino).

    @Test fun aUltimoMomentoInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a último momento", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aUltimoMomentoSinTildeTambienFunciona() {
        val result = NaturalTaskParser.parse("Reunión a ultimo momento", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- "temprano"/"muy temprano" como modificador de franja tras agenda ya resuelta
    // ("mañana temprano", "por la mañana temprano", "esta tarde temprano"): la dueAt se
    // calculaba bien, pero el adverbio sobrevivía como residuo en el título. c.426:
    // se limpia sólo cuando dueAt != null. "temprano" suelto (sin agenda) es contenido
    // legítimo ("llegué temprano") y NO se toca.

    @Test fun mananaTempranoLimpiaResiduoTempranoDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión mañana temprano", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun porLaMananaTempranoLimpiaResiduoTempranoDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión por la mañana temprano", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun muyTempranoTrasAgendaSeLimpiaDelTitulo() {
        val result = NaturalTaskParser.parse("Reunión mañana muy temprano", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun tempranoSueltoSinAgendaNoSeBorraNiAgenda() {
        // "temprano" sin agenda es contenido legítimo: no se asigna dueAt ni se borra.
        val result = NaturalTaskParser.parse("Llegué temprano", now, zone)
        assertEquals("Llegué temprano", result.title)
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

    // c.549 — "hacia el amanecer"/"hacia amanecer": forma aproximada del amanecer (igual que
    // "hacia el mediodía" ya funcionaba). Antes el patrón exigía "al" literal, así que la
    // variante con "hacia" no casaba → dueAt=null (tarea olvidada) y "hacia el amanecer"
    // quedaba como residuo en el título. Asimetría con mediodía (que admite "hacia el"/"hacia").
    // Se añade "hacia el/la" como conector alternativo EXPLÍCITO (no se hace opcional "al":
    // eso reabriría la colisión con "un amanecer hermoso"/"hoy amanece"). El conector sigue
    // siendo obligatorio; sólo se amplía cuáles conectores válidos admitir.
    @Test fun haciaElAmanecerResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Caminar hacia el amanecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun haciaAmanecerSinArticuloResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Salir hacia amanecer", now, zone)
        assertEquals("Salir", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun haciaElAlbaResuelveYLimpiaTitulo() {
        // "al alba" es sinónimo de "al amanecer" dentro del mismo patrón.
        val result = NaturalTaskParser.parse("Caminar hacia el alba", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // c.549 — "hacia el atardecer"/"hacia el anochecer"/"hacia el ocaso": forma aproximada
    // del ocaso. Simétrico de "hacia el amanecer" y de "hacia el mediodía" (que ya funcionaba).
    // Antes quedaban sin dueAt y con residuo. Mismo enfoque: "hacia el/la" como conector
    // alternativo explícito, manteniendo el conector obligatorio (sin reabrir colisión con
    // "un atardecer hermoso").
    @Test fun haciaElAtardecerResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Caminar hacia el atardecer", now, zone)
        assertEquals("Caminar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun haciaElAnochecerResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión hacia el anochecer", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun haciaElOcasoResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Pasear hacia el ocaso", now, zone)
        assertEquals("Pasear", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- "ya"/"ahora"/"en un rato"/"más tarde" + HORA EXPLÍCITA: la hora gana (c.397) ---
    // Estos anclas sub-hora imprecisos ("ya"=ahora, "en un rato"=now+1h, "más tarde"=now+3h)
    // capturaban el dueAt ANTES que la hora explícita y la descartaban: "reunión ya a las 5
    // de la tarde" → 12:00 (now) en vez de 17:00. El KDoc de nowPattern/laterRelativePattern
    // declara "no debe combinarse con hora explícita", pero la implementación lo permitía.
    // Principio (consistente con l.3367 "un tiempo explícito tiene prioridad"): una hora
    // explícita gana sobre cualquier ancla sub-hora impreciso. El ancla impreciso sólo
    // significa algo sin dato horario preciso. P1 datos (sagrados): el dueAt era incorrecto.
    @Test fun yaALas5DeLaTardeResuelve17hNoNow() {
        val result = NaturalTaskParser.parse("Reunión ya a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun yaALas9DeLaMananaResuelve9hNoNow() {
        val result = NaturalTaskParser.parse("Reunión ya a las 9 de la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun yaALas5SinMeridiemResuelve5hNoNow() {
        // Sin meridiem, la hora es ambigua AM/PM (como "a las 5" solo): 05:00 (no now=12:00).
        val result = NaturalTaskParser.parse("Reunión ya a las 5", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun ahoraALas5DeLaTardeResuelve17hNoNow() {
        val result = NaturalTaskParser.parse("Reunión ahora a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun yaMismoALas5DeLaTardeResuelve17hNoNow() {
        val result = NaturalTaskParser.parse("Reunión ya mismo a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun enUnRatoALas5DeLaTardeResuelve17hNoNowPlus1h() {
        // "en un rato"=now+1h (13:00) debe ceder ante la hora explícita 17:00.
        val result = NaturalTaskParser.parse("Reunión en un rato a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun masTardeALas5DeLaTardeResuelve17hNoNowPlus3h() {
        // "más tarde"=now+3h (15:00) debe ceder ante la hora explícita 17:00.
        val result = NaturalTaskParser.parse("Reunión más tarde a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesALas5DeLaTardeResuelve17hNoNowPlus3h() {
        // "después" (adverbio suelto, sin "de/del") = now+3h, debe ceder ante 17:00.
        val result = NaturalTaskParser.parse("Reunión después a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // No-regresión: "ya" solo (sin hora explícita) sigue venciendo ahora (caso legítimo).
    @Test fun yaSoloSigueVenciendoAhoraTrasFixHoraExplicita() {
        val result = NaturalTaskParser.parse("Reunión ya", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(now, result.dueAt)
    }

    // Fecha explícita + "ya": la fecha gana sobre el ancla "ya" (mismo bug de clase: "ya"
    // capturaba el due y descartaba "el viernes"). Viernes 31-jul, canónica 09:00.
    @Test fun yaElViernesResuelveViernesNoNow() {
        val result = NaturalTaskParser.parse("Reunión ya el viernes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
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
        // c.512: "quincena pasada" es ahora frase temporal (hoy-15d), asi que la
        // segunda ocurrencia SI se consume como fecha (no es contenido residual como
        // antes). La primera "quincena" (hito) tambien casa y se borra. El invariant que
        // este test vigila (no borrar globalmente con replace literal) lo cubre el caso
        // _semanaPasada (mismo token repetido, solo se borra la primera). Aqui se afirma
        // el nuevo comportamiento correcto: titulo limpio y vencimiento = quincena pasada.
        val result = NaturalTaskParser.parse("Revisar quincena y otra quincena pasada", now, zone)
        assertEquals("Revisar y otra", result.title)
        assertEquals(LocalDate.of(2026, 7, 14), DateRules.toLocalDate(result.dueAt!!, zone))
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

    @Test fun despuesDelAlmuerzoEsCitaNoAdverbioSuelto() {
        // "después del almuerzo" es dependencia/evento, no adverbio "luego" (+3 h): NO
        // cae al adverbio suelto. Sí casa como cita del almuerzo (14:00) — antes caía a
        // null (olvidada); ahora se resuelve como ancla de comida (c.387) y limpia el título.
        val result = NaturalTaskParser.parse("Llamar después del almuerzo", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        // El adverbio "después"=+3h NO capturó: el vencimiento es 14:00, no now+3h.
        assertNotEquals(now + 3 * 60 * 60_000L, result.dueAt)
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

    // --- "a eso de" + parte del día (c.381) ---
    // Antes el patrón "a eso de las N" sólo admitía hora numérica/escrita, así estas
    // formas cotidianas NO se normalizaban: la parte del día SÍ se resolvía a su canónica
    // PERO "a eso de" sobrevivía como residuo en el título ("pasar recado a eso del",
    // "reunión a eso") → cita bien fechada pero título mutilado (P1 captura/título limpio).
    @Test fun aEsoDelMediodiaResuelve12hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pasar recado a eso del mediodía", now, zone)
        assertEquals("pasar recado", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de la tarde", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaNocheResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de la noche", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaMadrugadaResuelve4hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de la madrugada", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaMananaResuelve9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de la mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaMedianocheResuelve0hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de la medianoche", now, zone)
        assertEquals("reunión", result.title)
        // now=12:00 → la medianoche de hoy ya pasó (12h en el pasado), así se rueda a la
        // medianoche de mañana (past-safe, igual que "a la medianoche"). No se aserta fecha.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDelMediodiaConDestinatarioLimpiaTitulo() {
        // "ver a juan a eso del mediodía": el destinatario debe conservarse y "a eso del
        // mediodía" limpiarse sin residuo.
        val result = NaturalTaskParser.parse("ver a juan a eso del mediodía", now, zone)
        assertEquals("ver a juan", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDelMediodiaMananaResuelveDiaSiguienteY12h() {
        // "a eso del mediodía mañana": día relativo + parte del día aproximada.
        val result = NaturalTaskParser.parse("almuerzo a eso del mediodía mañana", now, zone)
        assertEquals("almuerzo", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDelMediodiaStandaloneNoDejaResiduoAEso() {
        // Frase de agenda sin acción: el respaldo de título no debe resucitar "a eso del"
        // crudo; se normaliza al conector canónico ("al mediodía"), igual que "al viernes"
        // standalone → "el viernes" (c.379).
        val result = NaturalTaskParser.parse("a eso del mediodía", now, zone)
        assertEquals("al mediodía", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaUnaYMediaStandaloneNoDejaResiduoAEso() {
        // c.382: standalone de hora aproximada con fracción — el respaldo debe normalizar
        // "a eso de" → "a" (fold de approximateTimePatterns), no resucitar "a eso de" crudo.
        val result = NaturalTaskParser.parse("a eso de la una y media", now, zone)
        assertEquals("a la una y media", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(1, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaUnaYMediaDeLaTardeStandaloneNoDejaResiduoAEso() {
        val result = NaturalTaskParser.parse("a eso de la una y media de la tarde", now, zone)
        assertEquals("a la una y media de la tarde", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(13, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLasNueveYMediaStandaloneNoDejaResiduoAEso() {
        val result = NaturalTaskParser.parse("a eso de las nueve y media", now, zone)
        assertEquals("a las nueve y media", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLasCincoYMediaStandaloneNoDejaResiduoAEso() {
        val result = NaturalTaskParser.parse("a eso de las 5 y media", now, zone)
        assertEquals("a las 5 y media", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(5, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLaUnaYCuartoStandaloneNoDejaResiduoAEso() {
        val result = NaturalTaskParser.parse("a eso de la una y cuarto", now, zone)
        assertEquals("a la una y cuarto", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(1, 15), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "a partir de" + anclaje de HORA (c.435) ---
    // "a partir de" significa "desde esa hora en adelante" (inicio de franja). Antes la
    // hora/fecha SÍ se resolvía vía los patrones existentes PERO "a partir de" sobrevivía
    // como residuo en el título ("cita a partir de", "almuerzo a partir del", "reunión a
    // partir de la") → cita bien fechada pero título mutilado (P1 captura/título limpio).
    // Misma familia que c.424 ("antes de") y c.432 ("después de"). Sólo anclajes de HORA:
    // las fechas de calendario ("a partir del viernes") NO se reescriben (es un rango de
    // fecha distinto, no una hora canónica).
    @Test fun aPartirDeLasTresDeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("cita a partir de las 3 de la tarde", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLasCatorceResuelve14hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("enviar a partir de las 14", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pintar a partir de la tarde", now, zone)
        assertEquals("pintar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLaMananaResuelve9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a partir de la mañana", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLaMedianocheResuelve0hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a partir de la medianoche", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDelMediodiaResuelve12hYLimpiaTitulo() {
        // Contracción "del" (de+el), forma cotidiana.
        val result = NaturalTaskParser.parse("almuerzo a partir del mediodía", now, zone)
        assertEquals("almuerzo", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDelAmanecerResuelve6hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("caminar a partir del amanecer", now, zone)
        assertEquals("caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDelAtardecerResuelve18hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("correr a partir del atardecer", now, zone)
        assertEquals("correr", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLasTresDeLaTardeStandaloneNoDejaResiduoAPartir() {
        // Frase de agenda sin acción: el respaldo de título no debe resucitar "a partir de"
        // crudo; se normaliza al conector canónico ("a las 3 de la tarde").
        val result = NaturalTaskParser.parse("a partir de las 3 de la tarde", now, zone)
        assertEquals("a las 3 de la tarde", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDelMediodiaStandaloneNoDejaResiduoAPartir() {
        val result = NaturalTaskParser.parse("a partir del mediodía", now, zone)
        assertEquals("al mediodía", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aPartirDeLasNueveDeLaNocheEscritaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a partir de las nueve de la noche", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "a partir de" + FECHA de calendario (c.544) ---
    // Simétrico al fix de "desde" + fecha (c.499). aPartirDeRewriter sólo reescribe
    // "a partir de" + HORA/parte-del-día (no fechas de calendario), así que al resolver
    // y borrar la fecha el conector "a partir de/del" quedaba huérfano y el limpiador
    // final recortaba sólo su "de", dejando "a partir" como residuo del título
    // ("fumar menos a partir de mañana" → "fumar menos a partir", P1 título degradado).
    // Ahora se consume la frase entera cuando se resolvió fecha.
    @Test fun aPartirDeMananaLimpiaTituloSinDejarAPartir() {
        val result = NaturalTaskParser.parse("fumar menos a partir de mañana", now, zone)
        assertEquals("fumar menos", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPartirDelViernesLimpiaTituloSinDejarAPartir() {
        // 2026-07-29 es miércoles; "el viernes" → 2026-07-31.
        val result = NaturalTaskParser.parse("dieta a partir del viernes", now, zone)
        assertEquals("dieta", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPartirDel1DeSeptiembreLimpiaTituloSinDejarAPartir() {
        val result = NaturalTaskParser.parse("ahorrar a partir del 1 de septiembre", now, zone)
        assertEquals("ahorrar", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPartirDeHoyLimpiaTituloSinDejarAPartir() {
        val result = NaturalTaskParser.parse("fumar menos a partir de hoy", now, zone)
        assertEquals("fumar menos", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aPartirDeFechaNoTocaContenidoLegitimoSinAgenda() {
        // Sin fecha resuelta, "a partir de" es contenido legítimo y NO se borra.
        val result = NaturalTaskParser.parse("decisión a partir de los datos")
        assertEquals("decisión a partir de los datos", result.title)
    }
    // --- "hacia"/"durante" + FECHA/HORA (c.546) ---
    // Simétrico al fix de "desde"/"a partir de" + fecha (c.499/c.544). "hacia" y
    // "durante" son conectores temporales que NO se reescriben a un conector canónico;
    // al resolver y borrar la fecha/hora quedaban huérfanos al final del título
    // ("entregar hacia", "estudiar durante", "descansar durante la"), degradando la
    // entrada de mayor valor (captura ultrarrápida). Se consumen SOLO cuando se
    // resolvió fecha (dueAt != null): sin agenda son contenido legítimo ("trabajar
    // durante la semana", "leer durante las vacaciones"). "durante" puede llevar
    // artículo rezagado ("durante la mañana" → tras consumir "mañana" queda "durante
    // la") que también se limpia. End-anchored: "caminar hacia el parque el sábado"
    // (hacia NO al final, es contenido) se conserva íntegro.
    @Test fun haciaElViernesLimpiaTituloSinDejarHacia() {
        // 2026-07-29 es miércoles; "el viernes" → 2026-07-31.
        val result = NaturalTaskParser.parse("entregar hacia el viernes", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun haciaElMediodiaLimpiaTituloSinDejarHacia() {
        val result = NaturalTaskParser.parse("llegar hacia el mediodía", now, zone)
        assertEquals("llegar", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun haciaMananaLimpiaTituloSinDejarHacia() {
        // "mañana" fecha relativa → 2026-07-30.
        val result = NaturalTaskParser.parse("revisar hacia mañana", now, zone)
        assertEquals("revisar", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun duranteElFinDeSemanaLimpiaTituloSinDejarDurante() {
        // "el fin de semana" → próximo sábado 2026-08-01.
        val result = NaturalTaskParser.parse("estudiar durante el fin de semana", now, zone)
        assertEquals("estudiar", result.title)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun duranteLaMananaLimpiaTituloConArticuloRezagado() {
        // "la mañana" (parte del día) se resuelve PERO "la" queda rezagada tras
        // "durante": "descansar durante la" → debe limpiar "durante la" completo.
        val result = NaturalTaskParser.parse("descansar durante la mañana", now, zone)
        assertEquals("descansar", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // c.550 — "durante la tarde/noche/madrugada": ancla temporal cotidiana ("trabajar durante
    // la tarde") tan natural como "trabajar a la tarde" (que SÍ funciona). Antes el patrón
    // standalonePartOfDayPattern NO admitía el conector "durante la", así que estas frases
    // caían a dueAt=null (tarea olvidada, invisible en What Now/planificador) y la frase
    // entera quedaba como residuo en el título. Asimetría con "a la/en la/de la/por la tarde"
    // (15:00) y con "durante la mañana" (que resolvía vía "mañana"=fecha). Se añade "durante
    // la" como conector para tarde/noche/madrugada, EXCLUYENDO "mañana" (simétrico al
    // conector "de", que también la excluye): "durante la mañana" es ambigua (parte del día
    // vs. fecha "mañana") y ya resuelve vía la fecha; no se altera su comportamiento.
    @Test fun duranteLaTardeResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("trabajar durante la tarde", now, zone)
        assertEquals("trabajar", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun duranteLaNocheResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("estudiar durante la noche", now, zone)
        assertEquals("estudiar", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun duranteLaMadrugadaResuelveYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("descansar durante la madrugada", now, zone)
        assertEquals("descansar", result.title)
        assertEquals(LocalTime.of(4, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun duranteLaNoTocaContenidoLegitimoSinParteDelDia() {
        // "durante la" sin parte del día canónica es contenido legítimo: NO debe agendarse.
        val result = NaturalTaskParser.parse("trabajar durante la reunión", now, zone)
        assertEquals("trabajar durante la reunión", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun haciaDuranteNoTocanContenidoLegitimoSinAgenda() {
        // Sin fecha resuelta, "hacia"/"durante" son contenido legítimo y NO se borran.
        val r1 = NaturalTaskParser.parse("trabajar durante la semana", now, zone)
        assertEquals("trabajar durante la semana", r1.title)
        val r2 = NaturalTaskParser.parse("leer durante las vacaciones", now, zone)
        assertEquals("leer durante las vacaciones", r2.title)
    }

    @Test fun haciaDurantePreservanContenidoNoAlFinal() {
        // "hacia"/"durante" seguidos de sustantivo de contenido (NO al final) se
        // conservan íntegros aunque haya agenda: el conector es parte del contenido.
        val r1 = NaturalTaskParser.parse("caminar hacia el parque el sábado", now, zone)
        assertEquals("caminar hacia el parque", r1.title)
        val r2 = NaturalTaskParser.parse("trabajar durante la reunión el lunes", now, zone)
        assertEquals("trabajar durante la reunión", r2.title)
    }

    // --- "después" + fecha de calendario (c.548) ---
    // "después" es conector de plazo ("después del lunes"/"después de mañana"/
    // "después del 15"/"después de la semana que viene"/"después de fin de mes").
    // Al resolver y borrar la fecha, "después" quedaba huérfano al final del título
    // ("revisar después"), degradando la captura ultrarrápida. Asimetría flagrante con
    // "antes" ("enviar antes del viernes" → 'enviar', limpio c.497): el limpiador de
    // conector huérfano cubría "antes"/"antes del"/"hasta" PERO NO "después". Se
    // consume SOLO cuando se resolvió fecha (dueAt != null): sin agenda, "después de
    // la reunión" es contenido legítimo y NO se toca. End-anchored: "después" NO al
    // final se conserva.
    @Test fun despuesDelLunesLimpiaTituloSinDejarDespues() {
        // 2026-07-29 es miércoles; "después del lunes" → próximo lunes 2026-08-03.
        val result = NaturalTaskParser.parse("revisar después del lunes", now, zone)
        assertEquals("revisar", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun despuesDelViernesLimpiaTituloSinDejarDespues() {
        // "después del viernes" → próximo viernes 2026-07-31.
        val result = NaturalTaskParser.parse("entregar después del viernes", now, zone)
        assertEquals("entregar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun despuesDeMananaLimpiaTituloSinDejarDespues() {
        // c.846: "después de mañana" ≡ "pasado mañana" (forma coloquial fija del día
        // después de mañana) → 2026-07-31. Antes resolvía a 2026-07-30 por la "mañana"
        // interna (fecha errónea, un día antes de lo pedido). La limpieza del título
        // ("cobrar", sin "después" huérfano) se mantiene como en c.548.
        val result = NaturalTaskParser.parse("cobrar después de mañana", now, zone)
        assertEquals("cobrar", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun despuesDel15LimpiaTituloSinDejarDespues() {
        // "después del 15" → próximo día 15 = 2026-08-15.
        val result = NaturalTaskParser.parse("pagar después del 15", now, zone)
        assertEquals("pagar", result.title)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun despuesNoTocaContenidoLegitimoSinAgenda() {
        // Sin fecha resuelta, "después de la reunión" es contenido legítimo y NO se
        // borra ("reunión" no es fecha/hora de calendario).
        val result = NaturalTaskParser.parse("revisar después de la reunión", now, zone)
        assertEquals("revisar después de la reunión", result.title)
        assertNull(result.dueAt)
    }

    // --- "desde" + anclaje de HORA (c.436) ---
    // Simétrico a "a partir de" (c.435). "desde las N"/"desde la parte-del-día"/"desde el
    // mediodía/amanecer/..." significa "a partir de esa hora". Antes la hora SÍ se
    // resolvía para "las N" y "la parte-del-día" PERO "desde" sobrevivía como residuo en el
    // título ("cita desde", "reunión desde el"). Peor: las partes-del-día con artículo "el"
    // ("desde el mediodía/amanecer/atardecer/anochecer") NO se agendaban (dueAt=null → tarea
    // olvidada, P1 datos/captura) porque el rewriter no admitía el prefijo "el". Ahora el
    // cuerpo del rewriter es idéntico al de "a partir de" (variantes ASCII incluidas) con el
    // prefijo "desde (?:el )?".
    @Test fun desdeLasTresDeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("cita desde las 3 de la tarde", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeLasCatorceResuelve14hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("enviar desde las 14", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeLaTardeResuelve15hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("pintar desde la tarde", now, zone)
        assertEquals("pintar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElMediodiaResuelve12hYLimpiaTitulo() {
        // Antes: dueAt=null (no agendaba) y título "reunión desde el". Ahora agenda y limpia.
        val result = NaturalTaskParser.parse("reunión desde el mediodía", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElMediodiaAsciiResuelve12hYLimpiaTitulo() {
        // Variante ASCII ("mediodia" sin tilde): antes el rewriter sólo admitía "mediodía"
        // con í acentuada → esta forma cotidiana caía a dueAt=null. Ahora alineado al
        // cuerpo de "a partir de" (mediod[ií]a) agenda y limpia.
        val result = NaturalTaskParser.parse("reunión desde el mediodia", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElAmanecerResuelve6hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("caminar desde el amanecer", now, zone)
        assertEquals("caminar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElAtardecerResuelve18hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("correr desde el atardecer", now, zone)
        assertEquals("correr", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElAnochecerResuelve18hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("leer desde el anochecer", now, zone)
        assertEquals("leer", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeLasTresDeLaTardeStandaloneNoDejaResiduoDesde() {
        // Frase de agenda sin acción: el respaldo de título no debe resucitar "desde" crudo;
        // se normaliza al conector canónico ("a las 3 de la tarde").
        val result = NaturalTaskParser.parse("desde las 3 de la tarde", now, zone)
        assertEquals("a las 3 de la tarde", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeElMediodiaStandaloneNoDejaResiduoDesde() {
        val result = NaturalTaskParser.parse("desde el mediodía", now, zone)
        assertEquals("al mediodía", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun desdeLasNueveDeLaNocheEscritaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión desde las nueve de la noche", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "desde"/"a partir de" + "las N" + SUSTANTIVO DE CANTIDAD (c.442) ---
    // REGRESIÓN DE PÉRDIDA DE DATOS: antes los reescritores c.435/c.436 (y el fallback de
    // título) trataban "las 3" siempre como hora en punto, así que "comprar desde las 3 cajas"
    // se reescribía "desde las 3" → "a las 3", el "3" se agendaba como 3:00 y se ELIMINABA del
    // título, dejando "comprar cajas" (el usuario perdía la cantidad 3). El guard
    // [countNounFollowerPattern] (mismo que "a las N" en punto, c.361) ahora bloquea la
    // reescritura cuando el tail tras "las N" es un sustantivo de cantidad: el "las N"
    // nunca se agenda y se PRESERVA íntegro en el título.
    @Test fun desdeLasNConSustantivoCantidadPreservaNumeroEnTitulo() {
        val result = NaturalTaskParser.parse("comprar desde las 3 cajas", now, zone)
        assertEquals("comprar desde las 3 cajas", result.title)
        // No debe agendar hora: "3 cajas" no es una cita.
        assertNull(result.dueAt)
    }

    @Test fun aPartirDeLasNConSustantivoCantidadPreservaNumeroEnTitulo() {
        val result = NaturalTaskParser.parse("comprar a partir de las 3 cajas", now, zone)
        assertEquals("comprar a partir de las 3 cajas", result.title)
        assertNull(result.dueAt)
    }

    @Test fun desdeLasNConSustantivoCantidadCompuestoPreservaNumeroEnTitulo() {
        val result = NaturalTaskParser.parse("desde las 3 cajas de leche", now, zone)
        assertEquals("desde las 3 cajas de leche", result.title)
        assertNull(result.dueAt)
    }

    @Test fun desdeLasNConSustantivoCantidadStandalonePreservaNumeroEnTitulo() {
        // Frase de agenda sin acción: el respaldo de título (c.435/c.436 + fallback) también
        // lleva el guard, así que NO resucita "a las 3 cajas" ni pierde el número.
        val result = NaturalTaskParser.parse("desde las 3 cajas", now, zone)
        assertEquals("desde las 3 cajas", result.title)
        assertNull(result.dueAt)
    }

    @Test fun desdeLasNConSustantivoCantidadPersonasPreservaNumeroEnTitulo() {
        val result = NaturalTaskParser.parse("reunión desde las 10 personas del equipo", now, zone)
        assertEquals("reunión desde las 10 personas del equipo", result.title)
        assertNull(result.dueAt)
    }

    // Contraparte: el guard NO debe afectar a las horas reales con "las N" en punto
    // (continuador seguro: coma, fin de frase, "y"/"o"/"hasta"). Cubierto por los tests
    // c.435/c.436 de arriba; este es un ancla de no-regresión explícita.
    @Test fun desdeLasNEnPuntoConComaSigueAgendandoHora() {
        val result = NaturalTaskParser.parse("desde las 3, llegar temprano", now, zone)
        assertEquals("llegar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // --- "a eso de" + hora DESNUDA (sin "las") (c.386) ---
    // "a eso de" es adverbio temporal puro, así que admite hora en punto sin "las" como la
    // forma "a eso de las N". Antes estas variantes cotidianas ("a eso de nueve", "a eso de
    // 9", frecuentes en notas rápidas) caían a dueAt=null → la cita nunca se agendaba (el
    // usuario olvidaba la cita, P1 datos/captura). Y con parte del día ("a eso de nueve de
    // la noche") la hora sí se resolvía vía el patrón autónomo "N de la noche" PERO "a eso
    // de" sobrevivía como residuo del título ("cita a eso de": P1 título mutilado).
    @Test fun aEsoDeNueveDesnudaResuelve9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de nueve", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeNueveDigitoDesnudaResuelve9hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar a eso de 9", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeNueveDeLaNocheDesnudaResuelve21hYLimpiaTitulo() {
        // Antes: due=21:00 (vía patrón autónomo "N de la noche") PERO title="cita a eso de"
        // (residuo). Ahora: hora resuelta + título limpio.
        val result = NaturalTaskParser.parse("cita a eso de nueve de la noche", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeTresYMediaDesnudaResuelve3h30YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de 3 y media", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(3, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeNueveMenosCuartoDesnudaResuelve8h45YLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de 9 menos cuarto", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 45), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeNuevePMDesnudaResuelve21hYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("reunión a eso de 9pm", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeNueveHorasDesnudaNoEsRobadaComoDuracion() {
        // "9 horas" tras "a eso de" debe ser sufijo de unidad (→09:00), NO duración (540 min).
        val result = NaturalTaskParser.parse("reunión a eso de 9 horas", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
        assertNull(result.durationMinutes)
    }

    @Test fun aEsoDeNueveConCantidadPreservaCantidadEnTitulo() {
        // "comprar 9 panes a eso de la tarde": "9 panes" NO se falsifica como hora (va con
        // "a eso de la tarde", parte del día); la cantidad queda en el título.
        val result = NaturalTaskParser.parse("comprar 9 panes a eso de la tarde", now, zone)
        assertEquals("comprar 9 panes", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun aEsoDeLasNueveSigueFuncionandoTrasRewriterDesnuda() {
        // Regresión: la forma con "las" no debe romperse al añadir el rewriter de hora desnuda.
        val result = NaturalTaskParser.parse("reunión a eso de las 9", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
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

    // --- Weekday + día de mes nombrado (P1: fecha equivocada) ---
    // "reunión el lunes 24 de septiembre": el weekday ("lunes") califica/repite una fecha
    // concreta (24 de septiembre). Antes weekdayMatch ganaba la resolución de fecha sobre
    // monthNameDate → anclaba al lunes más cercano (hoy) y "24 de septiembre" se borraba del
    // título sin fijar la fecha → cita agendada en día equivocado (P1: cita perdida). Ahora
    // la fecha con mes nombrado gana. now = 2026-08-14 (viernes); 24/9/2026 es jueves (futuro).
    @Test fun weekdayConDiaMesNombradoResuelveLaFechaConMes() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("reunión el lunes 24 de septiembre", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 24), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
    }

    @Test fun weekdayConDiaMesNombradoYHoraNoDejaResiduo() {
        val agoNow = DateRules.toEpochMillis(LocalDate.of(2026, 8, 14), LocalTime.NOON, ZoneId.of("America/Santiago"))
        val result = NaturalTaskParser.parse("asamblea el miércoles 30 de octubre a las 15", agoNow, ZoneId.of("America/Santiago"))
        assertEquals("asamblea", result.title)
        assertEquals(LocalDate.of(2026, 10, 30), DateRules.toLocalDate(result.dueAt!!, ZoneId.of("America/Santiago")))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, ZoneId.of("America/Santiago")))
    }

    // --- Weekday + fecha numérica (P1: fecha equivocada, análogo a c.467 pero forma N/M) ---
    // "reunión el lunes 24/9": el weekday califica/repite una fecha numérica concreta. Antes
    // weekdayMatch ganaba sobre numericDateMatch → anclaba al lunes más cercano (hoy) y "24/9"
    // se borraba del título sin fijar la fecha, luego la lógica past-safe la empujaba a un día
    // equivocado de este mes (P1: cita agendada en mes erróneo). Ahora la fecha completa con
    // mes numérico gana. now = 2026-08-17 (lunes); 24/9/2026 es jueves (futuro).
    @Test fun weekdayConFechaNumericaResuelveLaFechaNumerica() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 17), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("reunión el lunes 24/9", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalDate.of(2026, 9, 24), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun weekdayConFechaNumericaYHoraNoDejaResiduo() {
        val zone = ZoneId.of("America/Santiago")
        val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 17), LocalTime.NOON, zone)
        val result = NaturalTaskParser.parse("asamblea el miércoles 30/10 a las 9", now, zone)
        assertEquals("asamblea", result.title)
        assertEquals(LocalDate.of(2026, 10, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
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

    // --- ciclo 428: conector "después de" simétrico a "antes de" (sólo con meridio) ---
    // El conector "después de las N <parte>" sobrevivía como residuo en el título
    // ("llegar después"). Ahora se normaliza a "a" para que timePatterns lo resuelva
    // y el título quede limpio. "después" ancla al inicio honesto de la franja dicha.
    @Test fun despuesDeLasHoraConMeridianoLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("llegar después de las 8 de la noche", now, zone)
        assertEquals("llegar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeLasTardeLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("reunir después de las 5 de la tarde", now, zone)
        assertEquals("reunir", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    // "después de las 5" (sin meridio) es ambiguo: NO se inventa vencimiento (sin regresión).
    @Test fun despuesDeLasHoraSinMeridioNoInventaVencimiento() {
        val result = NaturalTaskParser.parse("reunir después de las 5", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- ciclo 599: "antes/después de las HH:MM" y sufijos de reloj (BUG 2) ---
    // La hora en forma de reloj inequívoca (HH:MM, "horas", "en punto", "y media") SÍ la
    // resuelve timePatterns, pero el conector "antes/después de las" sobrevivía como residuo
    // sucio en el título ("enviar antes de las") porque el reescritor sólo aceptaba meridio en
    // el lookahead. Ahora esas evidencias también disparan el reescritor: el título queda
    // limpio y la hora se resuelve, simétrico a "hasta las 18:30".
    @Test fun antesDeLasHoraMinutoLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 18:30", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasHoraMinutoAmPmLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 6:30 pm", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasHoraMinutoTardeLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 6:30 de la tarde", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasHoraHorasLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 18:30 horas", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasHoraSueltaConHorasResuelveYNoDejaResiduo() {
        // Antes ni siquiera resolvía ("antes de las 18 horas" → null). Ahora, simétrico a
        // "a las 18 horas"/"hasta las 18 horas", resuelve 18:00 con título limpio.
        val result = NaturalTaskParser.parse("enviar antes de las 18 horas", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun despuesDeLasHoraMinutoLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("llegar después de las 9:15", now, zone)
        assertEquals("llegar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 15), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun antesDeLasEnPuntoLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 18 en punto", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun antesDeLasYMediaLimpiaTituloYResuelveHora() {
        val result = NaturalTaskParser.parse("enviar antes de las 6 y media", now, zone)
        assertEquals("enviar", result.title)
        assertEquals(LocalTime.of(6, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Guard: la forma ambigua "antes de las 5" (sin evidencia de reloj) sigue SIN resolver.
    @Test fun antesDeLasHoraSueltaSinEvidenciaSigueSinResolver() {
        val result = NaturalTaskParser.parse("enviar antes de las 5", now, zone)
        assertEquals(null, result.dueAt)
    }

    // Guard: "antes de las 5 cajas" (número = cantidad, no hora) no se inventa vencimiento.
    @Test fun antesDeLasCantidadNoInventaVencimiento() {
        val result = NaturalTaskParser.parse("comprar antes de las 5 cajas", now, zone)
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

    // --- c.362: meridiem COMPACTO (sin espacio) "para las Npm/Nam" no deja residuo ---
    // La forma dominante en móvil omite el espacio antes del meridiem. Antes el reloj
    // (:MM) casaba pero el introductor "para las" sobrevivía como residuo del título
    // (cita bien fechada, título mutilado). `\s+`→`\s*` en el grupo de meridiem de
    // paraTimeIntroPattern, simétrico de approximateTimePatterns (c.359) y timePatterns.
    @Test fun paraLasNPmCompactoNoDejaResiduo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("comprar regalos para las 3pm", suffixOrderNow(), zone)
        assertEquals("comprar regalos", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNAmCompactoNoDejaResiduo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("llamar a juan para las 9am", suffixOrderNow(), zone)
        assertEquals("llamar a juan", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasUnaPmCompactoResuelve13h() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita para las 1pm", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun paraLasNPmConEspacioSigueFuncionando() {
        // Sin regresión: la forma CON espacio ("3 pm") sigue resolviendo y limpiando.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 3 pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
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

    // --- ciclo 589: "las N" DESENUDA (sin introductor "a"/"para"/...) → hora resuelta ---
    // Forma cotidiana en móvil/notas rápidas y español latino ("quedamos las 3",
    // "cita las 7 y media"). Antes las SIN meridiana caían a dueAt=null (la cita NUNCA se
    // agendaba → olvidada, P1 evitar olvidos) y las con ":MM" caían al patrón autónomo
    // "HH:MM" que resolvía la hora PERO dejaba "las" como residuo en el título (P1 título).
    // Ahora se normaliza "las N" → "a las N" reutilizando TODO el flujo robusto de
    // timePatterns. Simétrico de aEsoDeBareHourRewriter y paraTimeIntroPattern.
    @Test fun bareLasNResuelveHoraYLimpiaTitulo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita las 3", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNyMediaResuelveHoraYLimpiaTitulo() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión las 7 y media", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(7, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNConMMLimpiaTituloSinResiduoLas() {
        // Antes ":MM" caía al patrón autónomo y dejaba "las" como residuo ("llamar las").
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("llamar las 4:30", suffixOrderNow(), zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalTime.of(4, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNDeLaTardeResuelveMeridiana() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("almuerzo las 3 de la tarde", suffixOrderNow(), zone)
        assertEquals("almuerzo", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNPmResuelveMeridiana() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión las 7:30 pm", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(19, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNyCuartoResuelveFraccion() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita las 3 y cuarto", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(3, 15), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNMenosCuartoResuelveFraccionNegativa() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita las 10 menos cuarto", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(9, 45), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasHoraEscritaResuelve() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita las nueve", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNHorasSufijoResuelve() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión las 9 horas", suffixOrderNow(), zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun bareLasNEnPuntoResuelve() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("cita las 9 en punto", suffixOrderNow(), zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // --- guards NO eludidos por el rewriter de "las N" desnuda ---
    @Test fun bareLasNManzanasNoEsCita() {
        // "las 3 manzanas" es una cantidad: el guard anti-cuenta lo preserva.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("compra las 3 manzanas", suffixOrderNow(), zone)
        assertEquals("compra las 3 manzanas", result.title)
        assertNull(result.dueAt)
    }

    @Test fun bareLasNTareasNoEsCita() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("las 3 tareas", suffixOrderNow(), zone)
        assertEquals("las 3 tareas", result.title)
        assertNull(result.dueAt)
    }

    @Test fun bareLasNTrasConectorPreservaGuardDeEseConector() {
        // "para las 9" sigue sin inventar cita (paraTimeIntroPattern la gobierna).
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("reunión para las 9", suffixOrderNow(), zone)
        assertNull(result.dueAt)
    }

    @Test fun bareLasNCadenciaNoSeRompe() {
        // "todas las dos semanas" sigue siendo cadencia WEEKLY/interval=2 (no se reescribe
        // a "a las dos").
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("Reunión todas las dos semanas", suffixOrderNow(), zone)
        assertEquals("Reunión", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNotNull(result.dueAt)
    }

    @Test fun bareLasNAntesDePreservaGuardSinMeridio() {
        // "antes de las 5" (sin meridio) sigue SIN inventar vencimiento.
        val zone = ZoneId.of("America/Santo_Domingo")
        val result = NaturalTaskParser.parse("llamar antes de las 5", suffixOrderNow(), zone)
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

    // --- Sufijos de aproximación post-hora (ciclo 389) ---
    // "a las 9 y pico" / "a las 9 más o menos" / "a las 9 aproximadamente" marcan la
    // hora como aproximada en español. Antes el modificador NO se consumía: el patrón
    // casaba "a las 9" y dejaba "y pico"/"más o menos"/"aproximadamente" como residuo
    // del título ("reunión y pico") — la cita se agendaba pero el título quedaba
    // mutilado. [APPROX_TIME_SUFFIX] los consume opcionalmente tras la hora (con word
    // boundary final `\b` para no robar "y pico de todo") y cuentan como evidencia de
    // reloj en [hasClockEvidence] para que el guard anti-cuenta no los trate como cuenta.

    @Test fun aLas9YPicoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 y pico", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLasNueveYPicoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las nueve y pico", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9MasOMenosLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 más o menos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9AproximadamenteLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 aproximadamente", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9AproximadamenteDeLaTardeLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita a las 9 aproximadamente de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "y pico" sin hora precedente NO debe generar cita (no hay hora base reconocida):
    // el sufijo sólo consume tras un [timePattern] que ya casó la hora.
    @Test fun informeYPicoDePaginasNoEsCita() {
        val result = NaturalTaskParser.parse("Un informe y pico de páginas", now, zone)
        assertEquals(null, result.dueAt)
    }

    @Test fun comprarAproximadamente3KilosNoEsCita() {
        // "aproximadamente" modificando una cantidad, no una hora.
        val result = NaturalTaskParser.parse("Comprar aproximadamente 3 kilos", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- Sufijo de aproximación "pasadas"/"pasada" post-hora (ciclo 419) ---
    // "a las 9 pasadas" = un poco después de las 9: forma cotidiana simétrica del PREFIJO
    // "pasadas las 9" (que ya resolvía [aproximateTimePatterns]). Antes el sufijo NO se
    // reconocía y, en hora en punto sin meridiem ("a las 9 pasadas"), el guard anti-cuenta
    // (c.361) tomaba "pasadas" por un sustantivo plural de cantidad y RECHAZABA la cita
    // (dueAt=null y "pasadas" como residuo): la cita se OLVIDABA (P1 datos/evitar olvidos).
    // Con meridiem ("a las 9 de la tarde pasadas") la hora sí se agendaba pero "pasadas"
    // quedaba como residuo del título. Ahora [APPROX_TIME_SUFFIX] consume "pasadas/pasada"
    // como sufijo de aproximación y cuenta como evidencia de reloj en [hasClockEvidence].

    @Test fun aLas9PasadasLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 pasadas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas9DeLaTardePasadasLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Reunión a las 9 de la tarde pasadas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLaUnaPasadaLimpiaTituloYResuelve1h() {
        val result = NaturalTaskParser.parse("Reunión a la una pasada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(1, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aLas930PasadasResuelve930() {
        val result = NaturalTaskParser.parse("Reunión a las 9:30 pasadas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta sigue activo sobre lo que siga al sufijo: "a las 9 pasadas
    // cajas" no es gramatical, pero confirma que el sufijo da evidencia de reloj (la hora
    // sí se agenda) sin desactivar el guard para el resto del tail. No es una cita limpia:
    // el residuo "cajas" permanece, consistente con "a las 9 más o menos cajas".

    // El uso demostrativo de "pasada" ("la semana pasada", "el viernes pasado") NO debe
    // verse afectado: el sufijo sólo consume tras un [timePattern] que casó la hora.

    // --- Sufijo de aproximación y artículo "las" en hora suelta con parte del día (c.425) ---
    // [standaloneHourPartOfDayPattern] ("N de la noche", forma de captura rápida sin "a las")
    // NO incluía [APPROX_TIME_SUFFIX] ni admitía el artículo "las", a diferencia de
    // [timePatterns] ("a las N"). Esto dejaba dos huecos asimétricos: (1) los modificadores
    // "más o menos"/"y pico"/"pasadas"/"aproximadamente" se limpiaban con "a las 9 pasadas"
    // (c.419) PERO dejaban residuo en "9 de la noche pasadas" ("reunión pasadas"); (2) la
    // forma cotidiana "reunión las 9 de la noche" (artículo sin "a") dejaba "las" como
    // residuo ("reunión las"). Ambos: cita bien fechada pero título mutilado (P1 captura/
    // título limpio). Ahora el patrón incluye $APPROX_TIME_SUFFIX y `(?:las\s+)?`, simétrico
    // de [timePatterns]. El lookahead anti-"de <mes>" y la exigencia de "de la <parte>"
    // siguen filtrando cuentas ("las 9 cajas" no casa: no hay "de la noche/tarde/...").

    @Test fun nueveDeLaNocheMasOMenosLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Reunión 9 de la noche mas o menos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveDeLaNocheYPicoLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita 9 de la noche y pico", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveDeLaNochePasadasLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cena 9 de la noche pasadas", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveDeLaNocheAproximadamenteLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita 9 de la noche aproximadamente", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun nueveYMediaDeLaNocheMasOMenosLimpiaTituloYResuelve2130() {
        val result = NaturalTaskParser.parse("Reunión 9 y media de la noche mas o menos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun lasNueveDeLaNocheLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Reunión las 9 de la noche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun lasOchoYMediaDeLaNocheLimpiaTituloYResuelve2030() {
        val result = NaturalTaskParser.parse("Cita las 8 y media de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(20, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun lasNueveDeLaNochePasadasLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cena las 9 de la noche pasadas", now, zone)
        assertEquals("Cena", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Guard anti-cuenta: "las 9 cajas" NO debe falsificarse como cita (no hay "de la
    // <parte>", así el patrón autónomo no casa y el guard anti-cuenta retiene el título).
    @Test fun lasNueveCajasNoSeFalsificaComoCita() {
        val result = NaturalTaskParser.parse("Comprar las 9 cajas de leche", now, zone)
        assertEquals("Comprar las 9 cajas de leche", result.title)
        assertNull(result.dueAt)
    }

    // --- Genitivo "de" antes de hora suelta con parte del día (c.448) ---
    // En español, la preposición "de" que introduce un complemento de hora en estilo
    // genitivo ("cita DE las 5", "reunión DE 9 de la noche") es cotidiana y muy frecuente
    // en captura rápida. Antes, [standaloneHourPartOfDayStripPattern] sólo casaba "N de la
    // <parte>" empezando en el número, dejando el "de" anterior como residuo pegado al
    // título ("cita de"). La cita se agendaba bien, pero el título quedaba mutilado (P1
    // captura/título limpio). Ahora el patrón consume opcionalmente el "de" precedente
    // (prefijo `(?:\bde\s+)?`), simétrico del fix de "del"/"de" en fechas de nombre de mes.
    // El guard anti-"de <mes>" (lookahead) sigue filtrando "de 5 de diciembre".

    @Test fun citaDe5DeLaTardeConsumeDeYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("cita de 5 de la tarde", now, zone)
        assertEquals("cita", result.title)
        assertEquals(LocalTime.of(17, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun reunionDe9DeLaNocheConsumeDeYResuelve21h() {
        val result = NaturalTaskParser.parse("reunión de 9 de la noche", now, zone)
        assertEquals("reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun claseDe3YMediaDeLaTardeConsumeDeYResuelve1530() {
        val result = NaturalTaskParser.parse("clase de 3 y media de la tarde", now, zone)
        assertEquals("clase", result.title)
        assertEquals(LocalTime.of(15, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun citaDe5PersonasDeLaTardeNoRobaContenidoNiResiduoDe() {
        // Aquí el "de 5 personas" NO es hora: el patrón NO casa "5 ... de la tarde" (hay
        // "personas" entre el número y "de la tarde"), así que NO se roba "5 personas" del
        // título. En cambio, "de la tarde" sí casa como parte-del-día suelta y resuelve la
        // hora canónica de tarde (15:00), dejando "cita de 5 personas" como contenido.
        // Confirma que el fix NO introdujo sobre-consumo del número en contexto de cantidad.
        val result = NaturalTaskParser.parse("cita de 5 personas de la tarde", now, zone)
        assertEquals("cita de 5 personas", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun llamadaDe9DeLaMananaConsumeDeYResuelve9h() {
        // "manana" (sin ñ) es aceptado por el patrón `ma[nñ]ana`; se usa ASCII para evitar
        // problemas de codificación en el string del test. El "de" genitivo se consume.
        val result = NaturalTaskParser.parse("llamar de 9 de la manana", now, zone)
        assertEquals("llamar", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }


    // --- Prefijo "casi a las/la" (ciclo 389) ---
    // "casi a las 9" = un poco antes de las 9. Es adverbio de aproximación temporal
    // puro (no admite lectura de cantidad: "casi a las 9 personas" no es gramatical),
    // así que NO exige evidencia de reloj (igual que "a eso de"). Antes dejaba "casi"
    // como residuo del título ("reunión casi") pese a agendar la cita correctamente.

    @Test fun casiALas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión casi a las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun casiALasNueveLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión casi a las nueve", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun casiALaUnaLimpiaTituloYResuelve13h() {
        val result = NaturalTaskParser.parse("Almuerzo casi a la una pm", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "casi a las 9 personas" (cuenta rara): el guard anti-cuenta sigue activo tras
    // reescribir "casi a " → "a ", así que "a las 9 personas" se rechaza como cita.
    @Test fun casiALas9PersonasNoEsCita() {
        val result = NaturalTaskParser.parse("Casi a las 9 personas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // "casi" sin "a las/la" NO es hora: no debe robar el modificador en otros contextos.
    @Test fun casiTerminoElInformeNoEsCita() {
        val result = NaturalTaskParser.parse("Casi termino el informe", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- Prefijos de aproximación/intensificación antes de "a las/la N" (ciclo 395) ---
    // "aproximadamente a las 9", "más o menos a las 9", "justo a las 9", "exactamente
    // a las 9" son adverbios temporales puros antes de "a las N" (no admiten lectura de
    // cantidad: "justo a las 9 personas" no es gramatical), así que NO exigen evidencia
    // de reloj (igual que "casi"). Antes dejaban residuo ("reunión aproximadamente",
    // "cita justo") pese a agendar la cita correctamente. Espejo del sufijo de c.393.

    @Test fun aproximadamenteALas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión aproximadamente a las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun masOMenosALas9DeLaNocheLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Reunión más o menos a las 9 de la noche", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoALas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Cita justo a las 9", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun justoALaUnaPmLimpiaTituloYResuelve13h() {
        val result = NaturalTaskParser.parse("Almuerzo justo a la una pm", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun exactamenteALas9DeLaTardeLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita exactamente a las 9 de la tarde", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Interacción combinada: prefijo "justo a las" + sufijo "y pico" (c.393) deben
    // convivir sin residuo, reutilizando ambos mecanismos en la misma frase.
    @Test fun justoALas9YPicoLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión justo a las 9 y pico", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta sigue activo tras reescribir "justo a " → "a ":
    // "justo a las 9 personas" → "a las 9 personas" → rechazado como cita.
    @Test fun justoALas9PersonasNoEsCita() {
        val result = NaturalTaskParser.parse("Cita justo a las 9 personas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // "justo" sin "a las/la" NO es hora: no debe robar el modificador en otros contextos.
    @Test fun justoTerminoElInformeNoEsCita() {
        val result = NaturalTaskParser.parse("Justo termino el informe", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- Prefijos de aproximación/intensificación "recién"/"apenas" antes de "a las/la N" (ciclo 396) ---
    // "recién a las 9"/"apenas a las 3" son adverbios temporales puros antes de "a las N" (igual que
    // "justo"/"casi" de c.395/c.393): "recién a las 9 personas"/"apenas a las 3 cajas" no son lectura
    // de cantidad (la forma de cuenta es "recién 9 personas"/"apenas 3 cajas", sin "a las"), así que NO
    // exigen evidencia de reloj. Antes dejaban residuo en el título ("reunión recién", "reunión apenas")
    // pese a agendar la hora correctamente.
    @Test fun recienALas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión recién a las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun recienALas9DeLaNocheLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Llamar recién a las 9 de la noche", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun apenasALas3LimpiaTituloYResuelve3h() {
        val result = NaturalTaskParser.parse("Reunión apenas a las 3", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun apenasALas9DeLaMananaLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Cita apenas a las 9 de la mañana", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta sigue activo tras reescribir "recién a "/"apenas a " → "a ":
    // "recién a las 9 personas" → "a las 9 personas" → rechazado como cita.
    @Test fun recienALas9PersonasNoEsCita() {
        val result = NaturalTaskParser.parse("Cita recién a las 9 personas", now, zone)
        assertEquals(null, result.dueAt)
    }

    @Test fun apenasALas3CajasNoEsCita() {
        val result = NaturalTaskParser.parse("Comprar apenas a las 3 cajas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- "como a las/la N" (ciclo c.424) ---
    // "como a las N" es la aproximación temporal coloquial más común en el español
    // caribeño/latino ("reunión como a las 9" = alrededor de las 9). Antes el parser
    // agendaba la hora correctamente PERO dejaba "como" como residuo del título
    // ("reunión como") â mismo bug-clase de captura/título limpio que c.393/c.395/c.396
    // ("casi"/"aproximadamente"/"recién"/"apenas" a las N). "como" es adverbio de
    // aproximación temporal puro antes de "a las N" (no admite lectura de cantidad: la
    // forma de cuenta es "como 9 cajas", sin "a las"), así que NO exige evidencia de
    // reloj (igual que "casi"/"recién"). El guard anti-cuenta (c.361) sigue activo
    // tras reescribir "como a " â "a ": "como a las 9 personas" â "a las 9 personas"
    // â rechazado como cita. Sin nueva pantalla/botón, sin IA fingida.
    @Test fun comoALas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión como a las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun comoALas9DeLaNocheLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita como a las 9 de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // "como a la una" sin meridiem sigue la resolución canónica de "a la una"
    // (01:00 a partir del mediodía, igual que aLaUnaParsesOneOclockAndCleanTitle): el
    // rewriter no altera la resolución, solo limpia el prefijo "como".
    @Test fun comoALaUnaLimpiaTituloYResuelve1h() {
        val result = NaturalTaskParser.parse("Reunión como a la una", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(1, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El meridiem fluye a través de la reescritura: "como a la una de la tarde" → "a la
    // una de la tarde" → 13:00.
    @Test fun comoALaUnaDeLaTardeLimpiaTituloYResuelve13h() {
        val result = NaturalTaskParser.parse("Reunión como a la una de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta sigue activo tras reescribir "como a " â "a ":
    // "como a las 9 personas" â "a las 9 personas" â rechazado como cita.
    @Test fun comoALas9PersonasNoEsCita() {
        val result = NaturalTaskParser.parse("Mesa como a las 9 personas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // "como" + cantidad directa (sin "a las") no es cita: preservado.
    @Test fun como9CajasNoEsCita() {
        val result = NaturalTaskParser.parse("Comprar como 9 cajas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- "pasadas las/la N" (ciclo c.398) ---
    // "pasadas las N" = un poco después de las N (aproximación post-hora, como "y pico"
    // de c.393). Antes producía dueAt=null → cita olvidada. Es adverbio temporal puro
    // antes de "las N" (sin "a" intermedio, a diferencia de "casi a las"/"justo a las"):
    // el rewriter consume "pasadas " y reescribe a "a " → "a las N", reutilizando
    // [timePatterns] (resolución + limpieza del título). El guard anti-cuenta (c.361)
    // sigue activo tras la reescritura: "pasadas las 9 cajas" → "a las 9 cajas" →
    // rechazado por followedByCountNoun (uso de cuenta forzado, no cotidiano).

    @Test fun pasadasLas9LimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión pasadas las 9", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun pasadasLasNueveLimpiaTituloYResuelve9h() {
        val result = NaturalTaskParser.parse("Reunión pasadas las nueve", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun pasadasLas9DeLaNocheLimpiaTituloYResuelve21h() {
        val result = NaturalTaskParser.parse("Cita pasadas las 9 de la noche", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun pasadaLaUnaPmLimpiaTituloYResuelve13h() {
        val result = NaturalTaskParser.parse("Almuerzo pasada la una pm", now, zone)
        assertEquals("Almuerzo", result.title)
        assertEquals(LocalTime.of(13, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // El guard anti-cuenta sigue activo tras reescribir "pasadas " → "a ":
    // "pasadas las 9 cajas" → "a las 9 cajas" → rechazado como cita.
    @Test fun pasadasLas9CajasNoEsCita() {
        val result = NaturalTaskParser.parse("Pasadas las 9 cajas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // "pasadas" sin "las/la N" NO es hora: no debe robar el modificador en otros contextos.
    @Test fun pasadasLasNormasNoEsCita() {
        val result = NaturalTaskParser.parse("Revisar las pasadas normas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- Recordatorio sustantivado "con aviso/alerta/recordatorio N unidad" (c.399) ---
    // Antes estas formas NO se reconocían como recordatorio: el offset caía a null (la cita
    // se olvidaba, P1) y "con aviso"/"con alerta"/"con recordatorio" sobrevivía como residuo
    // en el título. Ahora se captura el offset Y se limpia el residuo.

    @Test fun conAvisoNMinEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión con aviso 15 min", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conAvisoDeNMinutosEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión con aviso de 15 minutos", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
    }

    @Test fun conAvisoNMinAntesEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión con aviso 15 min antes", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
    }

    @Test fun conAlertaNMinEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita con alerta 10 min", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(10, result.reminderOffsetMinutes)
    }

    @Test fun conRecordatorioNMinEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita con recordatorio 20 min", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(20, result.reminderOffsetMinutes)
    }

    @Test fun conRecordatorioDeNMinutosEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita con recordatorio de 20 minutos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(20, result.reminderOffsetMinutes)
    }

    @Test fun conAvisoUnDiaEsRecordatorio1440SinResiduo() {
        val result = NaturalTaskParser.parse("Pagar luz con aviso de un día", now, zone)
        assertEquals("Pagar luz", result.title)
        assertEquals(1440, result.reminderOffsetMinutes)
    }

    @Test fun conAvisoDosHorasAntesEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita con aviso dos horas antes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
    }

    // "aviso N unidad" sin "con" también es recordatorio.
    @Test fun avisoSueltoNMinEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión aviso 15 min", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
    }

    // --- "con N unidad antes|de anticipación|de adelanto" (c.399) ---
    // Antes el patrón "N unidad antes" capturaba el offset PERO dejaba "con" como residuo
    // ("cita con"), y "con N unidad de anticipación/adelanto" ni casaba → caía como duración
    // falsa + residuo. Ahora se captura el offset, NO la duración, y se limpia todo.

    @Test fun conNMinAntesLimpiaConDelTitulo() {
        val result = NaturalTaskParser.parse("Cita con 15 min antes", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conNMinDeAnticipacionEsRecordatorioNoDuracion() {
        val result = NaturalTaskParser.parse("Llamar a mamá con 10 min de anticipación", now, zone)
        assertEquals("Llamar a mamá", result.title)
        assertEquals(10, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conNMinutosDeAdelantoEsRecordatorioNoDuracion() {
        val result = NaturalTaskParser.parse("Cita con 15 minutos de adelanto", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    // --- Orden invertido "con anticipación/adelanto de N unidad" (c.402) ---
    @Test fun conAnticipacionDeNMinEsRecordatorioNoDuracion() {
        val result = NaturalTaskParser.parse("Reunión con anticipación de 15 min", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(15, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conAnticipacionDeNMinutosEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Cita con anticipación de 20 minutos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(20, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conAdelantoDeNMinutosEsRecordatorioNoDuracion() {
        val result = NaturalTaskParser.parse("Cita con adelanto de 20 minutos", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(20, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conAnticipacionDeNHorasEsRecordatorioSinResiduo() {
        val result = NaturalTaskParser.parse("Reunión con anticipación de 2 horas", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun conAnticipacionDeNDiasEsRecordatorio1440() {
        val result = NaturalTaskParser.parse("Pagar luz con anticipación de un día", now, zone)
        assertEquals("Pagar luz", result.title)
        assertEquals(1440, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    // --- Hardening c.402: el conector "de" EXIGIDO evita falsos positivos sobre contenido real ---
    // El patrón "con (anticipación|adelanto) de N unidad" sólo casa con unidad horaria tras "de";
    // no roba "anticipación"/"adelanto" cuando son contenido real del título.
    @Test fun conAnticipacionSolaNoEsRecordatorioNiDuracion() {
        val result = NaturalTaskParser.parse("Reunión con anticipación", now, zone)
        assertEquals("Reunión con anticipación", result.title)
        assertNull(result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    @Test fun anticipacionDeLaReunionNoEsRecordatorioNiResiduo() {
        val result = NaturalTaskParser.parse("Revisar la anticipación de la reunión", now, zone)
        assertEquals("Revisar la anticipación de la reunión", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun adelantoDelPagoNoEsRecordatorioNiResiduo() {
        val result = NaturalTaskParser.parse("Solicitar adelanto del pago", now, zone)
        assertEquals("Solicitar adelanto del pago", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun anticipacionDeNPersonasNoEsRecordatorio() {
        // "con anticipación de 3 personas": hay "de N" pero la unidad NO es horaria → no robar.
        val result = NaturalTaskParser.parse("Evento con anticipación de 3 personas", now, zone)
        assertEquals("Evento con anticipación de 3 personas", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    // --- No-regresión: la forma directa "con N unidad de anticipación" (c.401) sigue intacta ---
    @Test fun conNMinDeAnticipacionDirectoSigueRecordatorio() {
        val result = NaturalTaskParser.parse("Llamar con 10 min de anticipación", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(10, result.reminderOffsetMinutes)
        assertNull(result.durationMinutes)
    }

    // --- Falsos positivos: "aviso"/"alerta" como contenido real, no recordatorio ---
    @Test fun avisoDeLaComunidadNoEsRecordatorioNiResiduo() {
        val result = NaturalTaskParser.parse("Revisar el aviso de la comunidad", now, zone)
        assertEquals("Revisar el aviso de la comunidad", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun alertaDelSistemaNoEsRecordatorioNiResiduo() {
        val result = NaturalTaskParser.parse("Responder alerta del sistema", now, zone)
        assertEquals("Responder alerta del sistema", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    // "con N unidad" sin sufijo de aviso (antes/anticipación/adelanto) sigue siendo duración.
    @Test fun conNDuracionSinSufijoSigueSiendoDuracion() {
        val result = NaturalTaskParser.parse("Reunión con 30 min", now, zone)
        assertEquals(30, result.durationMinutes)
        assertNull(result.reminderOffsetMinutes)
    }

    // --- Rangos horarios "entre las N y las M [meridiem]" / "de las N a las M [meridiem]" ---
    // Antes estas formas NO las reconocía [timeRangePattern] (que admite "de H1 a H2" con
    // horas NUMÉRICAS desnudas, sin "las" ni "entre...y"): el parser resolvía UNA hora (la
    // segunda, con meridiem) PERO dejaba el marco del rango como residuo del título
    // ("reunión entre las 3 y las"), e incluso misparseaba "entre 3 y 5 de la tarde" →
    // 15:05 ("3 y 5" leído como 3:05). El rewriter normaliza "entre [las ]H1 y [las ]H2
    // [meridiem]" y "de las H1 a las H2 [meridiem]" a la forma canónica "de H1 a H2
    // [meridiem]" que SÍ digiere [timeRangePattern] → duración (M−N) + hora de INICIO como
    // dueAt, idéntico a "de 9 a 11 de la mañana" (c.76/c.77). Reutiliza TODO el flujo
    // existente (propagación de meridiem, cruce de mediodía, guard anti-cuenta).

    @Test fun entreLas3YLas5DeLaTardeNormalizaARangoDuracion2hInicio15h() {
        val result = NaturalTaskParser.parse("Reunión entre las 3 y las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun entreLas9YLas11DeLaMananaNormalizaARangoInicio9h() {
        val result = NaturalTaskParser.parse("Reunión entre las 9 y las 11 de la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun entre3Y5DeLaTardeNormalizaSinMisparsear1505() {
        val result = NaturalTaskParser.parse("Reunión entre 3 y 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun entreLas2YLas4PmNormalizaARangoInicio14h() {
        val result = NaturalTaskParser.parse("Cita entre las 2 y las 4 pm", now, zone)
        assertEquals("Cita", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(14, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun entreLas3pmYLas5pmNormalizaARangoInicio15h() {
        val result = NaturalTaskParser.parse("Reunión entre las 3pm y las 5pm", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun deLas3ALas5DeLaTardeNormalizaARangoInicio15h() {
        val result = NaturalTaskParser.parse("Reunión de las 3 a las 5 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun entreLas3YLas5DeLaTardeMananaCombinaFechaYHoraInicio() {
        val result = NaturalTaskParser.parse("Reunión entre las 3 y las 5 de la tarde mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Rango en punto ambiguo (<13, sin meridiem/unidad) al FINAL de la frase: se acepta
    // como duración + hora de INICIO (rangeStartTime) como dueAt, igual que "Clase de 9 a
    // 11" (c. existente). Antes "entre las 3 y las 5" dejaba el rango crudo como residuo.
    @Test fun entreLas3YLas5SinMeridiemAlFinalDaDuracionEInicio3h() {
        val result = NaturalTaskParser.parse("Reunión entre las 3 y las 5", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(120, result.durationMinutes)
        assertEquals(LocalTime.of(3, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    // Guard anti-cuenta: "entre 3 y 5 cajas" NO es un rango horario (no agendar como cita).
    @Test fun entre3Y5CajasNoEsCita() {
        val result = NaturalTaskParser.parse("Reunión entre 3 y 5 cajas", now, zone)
        assertEquals(null, result.dueAt)
    }

    // --- "a fin de jornada" / "al final de jornada" (sin artículo "la"): misma familia
    // que c.429 "a fin de día". El patrón exigía "de la jornada" → la forma
    // cotidiana "de jornada" (sin "la") no casaba → dueAt=null (tarea olvidada) +
    // residuo. c.430: "de (la)? jornada" ahora admite ambas formas. Guard anti-colisión
    // con "a fin de mes/semana/año" intacto (palabra "jornada" vs "mes/semana/año").

    @Test fun aFinDeJornadaInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a fin de jornada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinalDeJornadaInterpretaFinJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión al final de jornada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aFinDeLaJornadaSigueFuncionando() {
        // Forma con artículo "la" (regresión guard): sigue resolviendo 18:00.
        val result = NaturalTaskParser.parse("Reunión a fin de la jornada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun alFinalDeLaJornadaSigueFuncionando() {
        val result = NaturalTaskParser.parse("Reunión al final de la jornada", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aFinDeJornadaNoColisionaConFinDeMes() {
        // "a fin de mes" sigue siendo fecha de fin de mes calendárico, NO 18:00 de hoy.
        val result = NaturalTaskParser.parse("Reunión a fin de mes", now, zone)
        assertEquals("Reunión", result.title)
        assertNotEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aFinDeJornadaNoColisionaConFinDeSemana() {
        val result = NaturalTaskParser.parse("Reunión a fin de semana", now, zone)
        assertEquals("Reunión", result.title)
        assertNotEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }


    // --- "a primer momento" / "primer momento" (espejo masculino de "a primera hora"):
    // inicio de jornada ~09:00. c.431: simátrico del fix c.429 "a Último momento".
    // Antes el patrón "primera hora" exigía el adjetivo en femenino ("primera") y no
    // casaba la forma masculina "primer momento" → dueAt=null (tarea SIN vencimiento
    // → olvidada) + residuo. Guard: "a primero de mes" (ordinal, no "primer momento")
    // y "primer cliente"/"primer día del mes" (sin "momento") no se ven afectados.

    @Test fun aPrimerMomentoInterpretaInicioJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión a primer momento", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun primerMomentoSinAInterpretaInicioJornadaYLimpiaTitulo() {
        val result = NaturalTaskParser.parse("Reunión primer momento", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aPrimerMomentoDeLaMananaCombinaFranja() {
        val result = NaturalTaskParser.parse("Reunión a primer momento de la mañana", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun aPrimerMomentoNoColisionaConAPrimeroDeMes() {
        // "a primero de mes" es fecha (día 1 del mes), NO 09:00 de hoy.
        val result = NaturalTaskParser.parse("Reunión a primero de mes", now, zone)
        assertEquals("Reunión", result.title)
        assertNotEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun primerClienteNoEsAnclaHoraria() {
        // Contenido legítimo, no debe interpretarse como hora.
        val result = NaturalTaskParser.parse("Reunión primer cliente", now, zone)
        assertEquals(null, result.dueAt)
    }

    // c.499: el genitivo huérfano final `de\s*$` no debía recortar el sufijo "de"
    // de palabras terminadas en "de" que NO son la preposición ("desde", "adrede").
    // Antes "vacaciones desde el lunes" → al borrar weekday+artículo quedaba
    // "vacaciones desde" y el "de" final se recortaba dejando "vacaciones des"
    // (P1: título corrupto, fecha correcta). El `\b` evita el recorte del sufijo y
    // además se consume el conector de inicio "desde" como orphan cuando se resolvió
    // fecha. "adrede" (contenido) se conserva intacto.
    @Test fun desdeElLunesLimpiaTituloSinRecortarSufijoDe() {
        val result = NaturalTaskParser.parse("Vacaciones desde el lunes", now, zone)
        assertEquals("Vacaciones", result.title)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun desdeMananaLimpiaTituloSinResiduo() {
        val result = NaturalTaskParser.parse("Curso desde mañana", now, zone)
        assertEquals("Curso", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun desdeEstaSemanaLimpiaTituloSinResiduo() {
        val result = NaturalTaskParser.parse("Trabajo desde esta semana", now, zone)
        assertEquals("Trabajo", result.title)
        assertNotNull(result.dueAt)
    }

    @Test fun adredeMananaConservaContenidoYResuelveFecha() {
        // "adrede" es contenido, NO conector temporal: el sufijo "de" no debe
        // recortarse. La fecha "mañana" sí se resuelve.
        val result = NaturalTaskParser.parse("Cita adrede mañana", now, zone)
        assertEquals("Cita adrede", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun desdeSinAgendaEsContenidoLegitimo() {
        // "desde" sin fecha resuelta es contenido: NO debe consumirse como orphan.
        val result = NaturalTaskParser.parse("Nadar desde temprano", now, zone)
        assertEquals("Nadar desde temprano", result.title)
        assertNull(result.dueAt)
    }

    @Test fun desdeElEquipoSinFechaNoEsOrphan() {
        // "el equipo" no resuelve fecha → "desde" permanece como contenido legítimo.
        val result = NaturalTaskParser.parse("Reunión desde el equipo", now, zone)
        assertEquals("Reunión desde el equipo", result.title)
        assertNull(result.dueAt)
    }

    @Test fun tipoLasOchoResuelveHoraYLimpiaTitulo() {
        // "tipo" = aproximación coloquial (Caribe/LatAm) antes de "las N": se reescribe a
        // "a " reutilizando timePatterns. Antes la hora se agendaba pero "tipo" quedaba
        // como residuo en el título (cita bien fechada, título mutilado).
        val result = NaturalTaskParser.parse("cita tipo las 8", now, zone)
        assertEquals("cita", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun tipoLasOchoPmResuelveMeridiem() {
        val result = NaturalTaskParser.parse("cita tipo las 8 pm", now, zone)
        assertEquals("cita", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun tipoLaUnaResuelveHoraSingular() {
        val result = NaturalTaskParser.parse("reunión tipo la una", now, zone)
        assertEquals("reunión", result.title)
        assertNotNull(result.dueAt)
    }

    @Test fun tipoSinArticuloNoEsMarcadorDeHora() {
        // "documento tipo 8": "tipo" + número sin "las/la" es uso de tema/categoría → no
        // es hora. Guard: sin reescritura, sin fecha.
        val result = NaturalTaskParser.parse("documento tipo 8", now, zone)
        assertEquals("documento tipo 8", result.title)
        assertNull(result.dueAt)
    }

    @Test fun tipoTemaNoEsMarcadorDeHora() {
        // "plan tipo estrategia": uso de tema legítimo; nada debe resolverse.
        val result = NaturalTaskParser.parse("plan tipo estrategia", now, zone)
        assertEquals("plan tipo estrategia", result.title)
        assertNull(result.dueAt)
    }

    @Test fun aMasTardarConWeekdayLimpiaTitulo() {
        // Marcador de plazo "a más tardar" (no later than): el weekday se resolvía pero el
        // marcador sobrevivía como residuo en el título. Se borra sólo cuando hay ancla
        // temporal (igual que "antes del viernes").
        val result = NaturalTaskParser.parse("entregar informe a más tardar el viernes", now, zone)
        assertEquals("entregar informe", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMasTardarConMananaLimpiaTitulo() {
        val result = NaturalTaskParser.parse("llamar a más tardar mañana", now, zone)
        assertEquals("llamar", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun aMasTardarSinAnclaConservaEsfuerzo() {
        // Sin ancla temporal el marcador se conserva (no se borra para no falsificar una
        // fecha): el título queda íntegro y dueAt null.
        val result = NaturalTaskParser.parse("terminarlo a más tardar", now, zone)
        assertEquals("terminarlo a más tardar", result.title)
        assertNull(result.dueAt)
    }

    @Test fun esoDeSinAResuelveHoraYLimpiaTitulo() {
        // "eso de las N" sin la "a" inicial: forma coloquial cotidiana de "a eso de las N"
        // (adverbio temporal puro, mismo criterio que la familia de c.676). Antes: NULL.
        val result = NaturalTaskParser.parse("alarma eso de las 5", now, zone)
        assertEquals("alarma", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalTime.of(5, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun esoDeSinAConCuentaNoEsHora() {
        // Guard anti-cuenta del flujo común (c.361): la aproximación se reescribe a "a "
        // y el guard rechaza → no agenda (dueAt null). El título refleja la reescritura.
        val result = NaturalTaskParser.parse("comprar eso de las 3 cajas", now, zone)
        assertEquals("comprar a las 3 cajas", result.title)
        assertNull(result.dueAt)
    }

    // --- c.674/675: "este mismo día" ≡ hoy (follow-up post-puesto de c.646 (ii)) ---

    @Test
    fun `c675 este mismo dia se agenda a hoy y limpia titulo`() {
        // "día" no tiene patrón pre-puesto (a diferencia de semana/mes/años y partes
        // del día), pero el post-puesto es idiomático: "este mismo día" ≡ hoy.
        val result = NaturalTaskParser.parse("llamar a funda este mismo día", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals("llamar a funda", result.title)
    }

    @Test
    fun `c675 este mismo dia al inicio tampoco rompe`() {
        // Guard: frase al inicio; la PhraseSpanConsumer debe quitarla por completo.
        val result = NaturalTaskParser.parse("este mismo día llamar a funda", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals("llamar a funda", result.title)
    }

    @Test
    fun `c676 para mes suelto se ancla a fin de mes y limpia titulo`() {
        // "para <mes>" sin día = plazo de fin de mes (equivalente honesto a
        // "para finales de <mes>", que ya se ancla con el calificador explícito).
        val result = NaturalTaskParser.parse("entregar informe para septiembre", now, zone)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals("entregar informe", result.title)
    }

    @Test
    fun `c676 para mes con anno explicito usa ese anno`() {
        val result = NaturalTaskParser.parse("liquidación para febrero de 2028", now, zone)
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals("liquidación", result.title)
    }

    @Test
    fun `c676 para mes ya pasado hace roll anual`() {
        // now=2026-07-29: "para febrero" ya pasó → 28/2/2027 (manejo del roll del
        // resolver de límite, no simple fecha fija).
        val result = NaturalTaskParser.parse("renovar afiliación para febrero", now, zone)
        assertEquals(LocalDate.of(2027, 2, 28), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt!!, zone))
        assertEquals("renovar afiliación", result.title)
    }

}

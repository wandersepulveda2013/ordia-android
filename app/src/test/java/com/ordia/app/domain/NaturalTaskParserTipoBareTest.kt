package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "tipo N" DESNUDO (sin artículo) con evidencia de reloj inmediata: aproximación
 * coloquial ("comida tipo 2 de la tarde" ≈ "comida sobre las 2 de la tarde"). La hora
 * ya se resolvía por el reloj autónomo, pero "tipo" sobrevivía como residuo en el
 * título (cita bien fechada, título mutilado: captura degradada; medido en la sonda
 * c.852, lateral (c)). A diferencia de "tipo las N" (c.670, con artículo: la forma de
 * categoría "documento tipo 8" va SIN artículo), la forma desnuda es ambigua con la de
 * categoría, así que exige evidencia de reloj INMEDIATA tras la hora (misma doctrina
 * que "hacia/sobre": minutos `:MM`, meridiem, parte del día o "horas/hs/h"); la hora
 * en punto sin evidencia ("reunión tipo 3") y los usos de categoría ("documento tipo
 * 8", "plan tipo estrategia", "mesa tipo 8 de comedor") NO se tocan.
 */
class NaturalTaskParserTipoBareTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 21), LocalTime.NOON, zone)

    private fun hora(r: ParsedTaskInput): LocalTime =
        Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalTime()

    // --- capturas ---

    @Test fun tipoBareDeLaTardeCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("comida tipo 2 de la tarde", now, zone)
        assertNotNull("tipo 2 de la tarde debe anclar hora", r.dueAt)
        assertEquals("comida", r.title.trim())
        assertEquals(LocalTime.of(14, 0), hora(r))
    }

    @Test fun tipoBareMeridiemPmCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("reunión tipo 3 pm", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalTime.of(15, 0), hora(r))
    }

    @Test fun tipoBareMinutosCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("cita tipo 10:30", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("cita", r.title.trim())
        assertEquals(LocalTime.of(10, 30), hora(r))
    }

    @Test fun tipoBareDeLaMananaCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("salir tipo 7 de la mañana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("salir", r.title.trim())
        assertEquals(LocalTime.of(7, 0), hora(r))
    }

    @Test fun tipoBareDeLaNocheCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("reunión tipo 9 de la noche", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalTime.of(21, 0), hora(r))
    }

    @Test fun tipoBareMeridiemAmCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("cita tipo 8 am", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("cita", r.title.trim())
        assertEquals(LocalTime.of(8, 0), hora(r))
    }

    // --- c.871: fracciones "N y media/cuarto/..." entre hora y parte del día ---

    @Test fun tipoFraccionYMediaTardeCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("salir tipo 5 y media de la tarde", now, zone)
        assertNotNull("tipo 5 y media de la tarde debe anclar hora", r.dueAt)
        assertEquals("salir", r.title.trim())
        assertEquals(LocalTime.of(17, 30), hora(r))
    }

    @Test fun tipoFraccionYCuartoTardeCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("salir tipo 5 y cuarto de la tarde", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("salir", r.title.trim())
        assertEquals(LocalTime.of(17, 15), hora(r))
    }

    @Test fun tipoFraccionYMediaMananaCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("cita tipo 10 y media de la mañana", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("cita", r.title.trim())
        assertEquals(LocalTime.of(10, 30), hora(r))
    }

    @Test fun tipoFraccionYMediaNocheCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("cena tipo 9 y media de la noche", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("cena", r.title.trim())
        assertEquals(LocalTime.of(21, 30), hora(r))
    }

    @Test fun tipoFraccionHoraEscritaCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("salir tipo cinco y media de la tarde", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("salir", r.title.trim())
        assertEquals(LocalTime.of(17, 30), hora(r))
    }

    @Test fun tipoFraccionMinutosEscritosCapturaYLimpiaTitulo() {
        val r = NaturalTaskParser.parse("salir tipo 5 y veinte de la tarde", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("salir", r.title.trim())
        assertEquals(LocalTime.of(17, 20), hora(r))
    }

    @Test fun tipoFraccionSinParteDelDiaNoSeToca() {
        // "tipo 2 y media" sin evidencia posterior: el reloj autónomo NO resuelve la
        // fracción desnuda, así que "tipo" no se consume (título íntegro, dueAt=null).
        val r = NaturalTaskParser.parse("comida tipo 2 y media", now, zone)
        assertNull(r.dueAt)
        assertEquals("comida tipo 2 y media", r.title.trim())
    }

    @Test fun tipoFraccionMeridiemNoSeToca() {
        // "tipo 3 y cuarto pm": fracción+meridiem no la resuelve el reloj autónomo;
        // consumir "tipo" mutilaría el título sin agendar. No se toca.
        val r = NaturalTaskParser.parse("cita tipo 3 y cuarto pm", now, zone)
        assertNull(r.dueAt)
        assertEquals("cita tipo 3 y cuarto pm", r.title.trim())
    }

    // --- guards: usos de categoría / ambiguos NO se tocan ---

    @Test fun tipoCategoriaSinEvidenciaNoSeToca() {
        val r = NaturalTaskParser.parse("documento tipo 8", now, zone)
        assertNull("documento tipo 8 no es hora", r.dueAt)
        assertEquals("documento tipo 8", r.title.trim())
    }

    @Test fun tipoCategoriaConPalabraNoSeToca() {
        val r = NaturalTaskParser.parse("plan tipo estrategia", now, zone)
        assertNull(r.dueAt)
        assertEquals("plan tipo estrategia", r.title.trim())
    }

    @Test fun tipoHoraEnPuntoSinEvidenciaNoSeToca() {
        // "reunión tipo 3" desnudo es ambiguo con categoría ("tipo 3"): sin meridiem
        // ni parte del día no se falsifica como cita (doctrina "hacia/sobre").
        val r = NaturalTaskParser.parse("reunión tipo 3", now, zone)
        assertNull(r.dueAt)
        assertEquals("reunión tipo 3", r.title.trim())
    }

    @Test fun tipoCuentaNoSeToca() {
        val r = NaturalTaskParser.parse("documento tipo 8 personas", now, zone)
        assertNull(r.dueAt)
        assertEquals("documento tipo 8 personas", r.title.trim())
    }

    @Test fun tipoDeNoParteDelDiaNoSeToca() {
        // "8 de comedor" no es parte del día: la evidencia exige "de la tarde/...".
        val r = NaturalTaskParser.parse("mesa tipo 8 de comedor", now, zone)
        assertNull(r.dueAt)
        assertEquals("mesa tipo 8 de comedor", r.title.trim())
    }

    // --- regresiones: las rutas hermanas no cambian ---

    @Test fun tipoConArticuloLasSigueCapturando() {
        val r = NaturalTaskParser.parse("cita tipo las 8", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("cita", r.title.trim())
        assertEquals(LocalTime.of(8, 0), hora(r))
    }

    @Test fun tipoConArticuloLaUnaSigueCapturando() {
        val r = NaturalTaskParser.parse("reunión tipo la una", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalTime.of(1, 0), hora(r))
    }

    @Test fun conectorCanonicoALasIntacto() {
        val r = NaturalTaskParser.parse("comida a las 2 de la tarde", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("comida", r.title.trim())
        assertEquals(LocalTime.of(14, 0), hora(r))
    }

    @Test fun aproximadoHaciaIntacto() {
        val r = NaturalTaskParser.parse("reunión hacia las 9 pm", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        assertEquals(LocalTime.of(21, 0), hora(r))
    }
}

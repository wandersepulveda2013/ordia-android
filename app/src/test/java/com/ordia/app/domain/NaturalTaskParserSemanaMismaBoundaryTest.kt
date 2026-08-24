package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.982: follow-up (i) de c.646 — compuestos «límite semanal + intensificador
 * "misma"», en ambas posiciones (post-puesta «la semana misma» e intercalada tras
 * el artículo «la misma semana»), con y sin «que viene». Medida PRE (sonda efímera
 * /tmp/probe981/IntensificadorQueVieneProbe.kt, motor real, now=lunes 2026-08-24
 * 12:00 America/Santo_Domingo):
 *
 *  - Post-puesta en mediados/principios ([midOfWeekPattern]/[startOfWeekPattern] no
 *    la admitían; la familia «fin» de [thisWeekPattern] sí): la fecha base se
 *    resolvía pero «misma» quedaba como RESIDUO en el título y, peor, con «que
 *    viene» el modificador quedaba FUERA del match → fecha de ESTA semana (fecha
 *    errónea silenciosa, doble daño: fecha mentirosa + título sucio).
 *  - Intercalada tras «la» («la misma semana»): ninguna de las tres palabras de
 *    límite casaba → dueAt=null con la frase íntegra de residuo (vencimiento
 *    olvidado).
 *  - Mensuales (fin/mediados/principios/último día hábil + intensificador + «que
 *    viene»): YA correctas desde c.672/c.975 (controles de la sonda, byte-idénticos).
 *
 * El intensificador es semánticamente NEUTRO (no cambia el rango; doctrina c.646);
 * «que viene» sigue anclando a la semana próxima (c.489/c.506). Guards: «el mismo
 * médico» nunca es límite; «trabajar la semana misma» sin palabra-límite no ancla.
 */
class NaturalTaskParserSemanaMismaBoundaryTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // lunes 2026-08-24 12:00 local
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 24), LocalTime.NOON, zone)

    private fun parsed(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertBoundary(text: String, expected: LocalDate) {
        val r = parsed(text)
        assertEquals("avisar", r.title)
        assertEquals(expected, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), java.time.Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalTime())
    }

    // --- intensificador POST-PUUESTO (mediados/principios) ---

    @Test fun mediadosDeLaSemanaMisma_resuelveMiercolesSinResiduo() {
        assertBoundary("avisar a mediados de la semana misma", LocalDate.of(2026, 8, 26))
    }

    @Test fun mediadosDeLaSemanaMismaQueViene_anclaSemanaProxima() {
        assertBoundary("avisar a mediados de la semana misma que viene", LocalDate.of(2026, 9, 2))
    }

    @Test fun principiosDeLaSemanaMisma_resuelveLunesSinResiduo() {
        assertBoundary("avisar a principios de la semana misma", LocalDate.of(2026, 8, 24))
    }

    @Test fun principiosDeEstaSemanaMisma_resuelveLunesSinResiduo() {
        assertBoundary("avisar a principios de esta semana misma", LocalDate.of(2026, 8, 24))
    }

    @Test fun principiosDeLaSemanaMismaQueViene_anclaSemanaProxima() {
        assertBoundary("avisar a principios de la semana misma que viene", LocalDate.of(2026, 8, 31))
    }

    // --- intensificador INTERCALADO tras «la» ---

    @Test fun mediadosDeLaMismaSemana_resuelveMiercoles() {
        assertBoundary("avisar a mediados de la misma semana", LocalDate.of(2026, 8, 26))
    }

    @Test fun principiosDeLaMismaSemana_resuelveLunes() {
        assertBoundary("avisar a principios de la misma semana", LocalDate.of(2026, 8, 24))
    }

    @Test fun finalesDeLaMismaSemana_resuelveDomingo() {
        assertBoundary("avisar a finales de la misma semana", LocalDate.of(2026, 8, 30))
    }

    @Test fun finalesDeLaMismaSemanaQueViene_anclaSemanaProxima() {
        assertBoundary("avisar a finales de la misma semana que viene", LocalDate.of(2026, 9, 6))
    }

    @Test fun mediadosDeLaMismaSemanaQueViene_anclaSemanaProxima() {
        assertBoundary("avisar a mediados de la misma semana que viene", LocalDate.of(2026, 9, 2))
    }

    // --- regresiones (ya cubiertas: familia «fin» post-puesta) ---

    @Test fun finalesDeLaSemanaMisma_regresionPostpuesta() {
        assertBoundary("avisar a finales de la semana misma", LocalDate.of(2026, 8, 30))
    }

    @Test fun finalesDeLaSemanaMismaQueViene_regresionPostpuesta() {
        assertBoundary("avisar a finales de la semana misma que viene", LocalDate.of(2026, 9, 6))
    }

    // --- guards ---

    @Test fun elMismoMedicoDeSiempre_noEsLimiteSemanal() {
        val r = parsed("el mismo médico de siempre")
        assertNull(r.dueAt)
        assertEquals("el mismo médico de siempre", r.title)
    }

    @Test fun trabajarLaSemanaMisma_sinPalabraLimite_noAncla() {
        val r = parsed("trabajar la semana misma")
        assertNull(r.dueAt)
        assertEquals("trabajar la semana misma", r.title)
    }
}

package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1050 — DELTA de la lateral «ordinal «a primera/última hora» AL FINAL
 * tras cadena narrativa «ahora/ahorita <clíticos> <pretérito>» que ABRE el
 * enunciado» (medida PRE con sonda efímera /tmp/probe1046/Probe2.kt sobre
 * HEAD 2d3320a: 5/5 candidatas con doble daño P1 — ancla FALSA 09:00/18:00
 * hoy [compromiso vencido al nacer que ensucia What Now] + título mutilado
 * sin su marca temporal). Hermana simétrica de c.1041 (weekday final) y
 * c.1045 (hora numérica final): la guard ordinal reutiliza el MISMO prefijo
 * narrativo compartido [narrativePreteritePrefix]. La vertiente «ya» ya
 * estaba cubierta por la cabeza c.1016/c.1023 (regresiones R1/R2). Guards
 * conservadores (sin marca narrativa, presente con «ya/ahora») siguen
 * byte-idénticos. Pin FUERA: verbo «contó» fuera de la lista cerrada
 * c.950 sigue anclando (lateral ABIERTA de cobertura, UNA por ciclo).
 *
 * COLISIÓN CONVERGENTE TOTAL con SU c.1048 `4f2fd28` (misma lateral,
 * producción funcionalmente idéntica MÁS ampliación propia del idiom
 * «quedar con»): medición cruzada 22/22 (mis 11 contra SU producción y
 * los suyos 11); SU = superconjunto → delta de producción propio
 * descartado NO-destructivo. Este test se conserva por cobertura
 * DISJUNTA: variante CON ARTÍCULO «a la primera hora», pin FUERA
 * byte-idéntico «ya me lo contó a primera hora», guards de presente
 * «ahora llamo…»/«ya te aviso…» y regresión doble clítico.
 */
class NaturalTaskParserAhoraPreteritoNarrativoOrdinalFinalDeltaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertAnchoredToday(input: String, hour: Int, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        val dt = Instant.ofEpochMilli(result.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 23), dt.toLocalDate())
        assertEquals(hour, dt.hour)
        assertEquals(expectedTitle, result.title)
    }

    @Test
    fun `ahora narrativo con ordinal primera hora al final no ancla`() {
        assertNarrativeIntact("ahora llegó el cartero a primera hora")
    }

    @Test
    fun `ahorita narrativo con ordinal ultima hora al final no ancla`() {
        assertNarrativeIntact("ahorita me escribió a última hora")
    }

    @Test
    fun `ahora narrativo sonido con ordinal primera hora al final no ancla`() {
        assertNarrativeIntact("ahora sonó la alarma a primera hora")
    }

    @Test
    fun `ahorita narrativo llamada con ordinal ultima hora al final no ancla`() {
        assertNarrativeIntact("ahorita me llamó a última hora")
    }

    @Test
    fun `ahora narrativo con ordinal con articulo al final no ancla`() {
        assertNarrativeIntact("ahora llegó el cartero a la primera hora")
    }

    @Test
    fun `guard sin marca narrativa sigue anclando ordinal`() {
        assertAnchoredToday("avisar a primera hora", 9, "avisar")
    }

    @Test
    fun `guard presente con ahora sigue anclando ordinal`() {
        assertAnchoredToday("ahora llamo a primera hora", 9, "llamo")
    }

    @Test
    fun `guard presente con ya sigue anclando ordinal`() {
        assertAnchoredToday("ya te aviso a última hora", 18, "te aviso")
    }

    @Test
    fun `regresion ya con cliticos y ordinal al final sigue intacta`() {
        assertNarrativeIntact("ya me lo dijo a primera hora")
    }

    @Test
    fun `regresion ya con doble clitico y ordinal al final sigue intacta`() {
        assertNarrativeIntact("ya se lo dije a última hora")
    }

    @Test
    fun `pin FUERA verbo contado fuera de la lista cerrada sigue anclando`() {
        assertAnchoredToday("ya me lo contó a primera hora", 9, "me lo contó")
    }
}

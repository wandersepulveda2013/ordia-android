package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1095: lateral c.1077 RESUELTA — la parte del día INTERCALADA entre DOS
 * comas de una narrativa «ahora/ahorita, <adverbial>, <pretérito>»
 * («ahora, por la tarde, llegó el paquete») no es ancla: pertenece al
 * enunciado narrativo, igual que con «ya». Antes el parser fabricaba fecha
 * falsa (hoy a la hora canónica) Y mutilaba el título con coma residual
 * («ahora, , llegó el paquete») — doble daño P1, simétrico a c.1077.
 *
 * Medida PRE con sonda efímera /tmp/probe1095/Probe.kt (motor real,
 * now=sábado 2026-08-22 12:00 America/Santo_Domingo): 5/5 capturas con
 * ancla falsa + título mutilado; hermano «ya» (c.1077) y una sola coma
 * («ya,» c.1027/c.1037) intactos; pins/exclusiones intactos.
 *
 * Evidencia gramatical: la familia c.1027/c.1037 ya trata las TRES marcas
 * («ya/ahora/ahorita») uniformemente como marca de inmediatez/narrativa;
 * c.1077 acotó N1 a «ya,» por doctrina de conservadorismo (UNA lateral por
 * ciclo). Aquí se extiende N1 del guard
 * [yaCommaPreteriteNarrativeIntercalatedPartOfDay] a las tres marcas con el
 * mismo predicado de pretérito inequívoco (N2/N3 idénticos): fecha y título
 * fluyen del MISMO flag en [parse], [eraseStandalonePartOfDayToken] y
 * [mananaOccurrenceIsContent] — nunca divergen. Fix en UN punto (el guard),
 * byte-equivalente para todas las formas pineadas (paridad de comas
 * disjunta con las hermanas c.1077/c.1083/c.1094).
 *
 * Conservador (UNA lateral por ciclo, doctrina anti-overreach c.615):
 * laterales FUERA pineadas byte-idénticas abajo — «siguiente»/«de hoy»
 * (exclusiones doctrina c.955), presente/imperativo/ambigua («salimos») y
 * verbos fuera de la lista cerrada c.950 («recogieron», como «acosté» en
 * c.1094).
 */
class NaturalTaskParserAhoraAhoritaDosComasParteDiaNarrativaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 22, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertPin(input: String, expectedDue: LocalDateTime?, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals(expectedDue, result.dueAt?.let {
            DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
        })
        assertEquals(expectedTitle, result.title)
    }

    // ---------- capturas: «ahora,»/«ahorita,» + parte del día entre DOS comas + pretérito inequívoco ----------

    @Test
    fun ahoraComaPorLaTardeLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ahora, por la tarde, llegó el paquete")

    @Test
    fun ahoraComaPorLaMananaSeFueLaLuz_esContenidoNarrativo() =
        assertNarrativeIntact("ahora, por la mañana, se fue la luz")

    @Test
    fun ahoraComaPorLaMananaMeDesperte_esContenidoNarrativo() =
        assertNarrativeIntact("ahora, por la mañana, me desperté con el ruido")

    @Test
    fun ahoritaComaEnLaNocheSeDurmo_esContenidoNarrativo() =
        assertNarrativeIntact("ahorita, en la noche, se durmió")

    @Test
    fun ahoritaComaDeLaTardeMeLlameConAna_esContenidoNarrativo() =
        assertNarrativeIntact("ahorita, de la tarde, me llamé con Ana")

    // ---------- regresiones: las hermanas (c.1077/c.1094 con «ya») quedan íntegras ----------

    @Test
    fun yaDosComasPorLaMananaMeTomeLaPastilla_sigueNarrativaIntacta() =
        assertNarrativeIntact("ya, por la mañana, me tomé la pastilla")

    @Test
    fun yaComaSoloApertura_sigueNarrativaIntacta() =
        assertNarrativeIntact("ya, por la mañana me tomé la pastilla")

    @Test
    fun yaComaSoloCierre_sigueNarrativaIntacta() =
        assertNarrativeIntact("ya por la mañana, me tomé la pastilla")

    // ---------- laterales FUERA pineadas byte-idénticas ----------

    @Test
    fun ahoritaComaPorLaMananaSiguienteMeFui_lateralFueraPin() =
        assertPin("ahorita, por la mañana siguiente, me fui",
            LocalDateTime.of(2026, 8, 23, 9, 0), "ahorita, , me fui")

    @Test
    fun ahoraComaPorLaMananaDeHoySeLoDije_lateralFueraPin() =
        assertPin("ahora, por la mañana de hoy, se lo dije",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ahora, , se lo dije")

    @Test
    fun ahoraComaPorLaTardePuedoComprarElPan_presenteGana_lateralFueraPin() =
        assertPin("ahora, por la tarde, puedo comprar el pan",
            LocalDateTime.of(2026, 8, 22, 15, 0), "puedo comprar el pan")

    @Test
    fun ahoritaComaPorLaNocheSalimos_ambiguaLateralForaPin() =
        assertPin("ahorita, por la noche, salimos",
            LocalDateTime.of(2026, 8, 22, 21, 0), "salimos")

    @Test
    fun yaComaPorLaMananaLlamoAlBanco_presenteGana_lateralFueraPin() =
        assertPin("ya, por la mañana, llamo al banco",
            LocalDateTime.of(2026, 8, 22, 9, 0), "llamo al banco")

    // «recogieron» no está en la lista cerrada de pretéritos c.950 (igual que
    // «acosté» en c.1094): el guard no la reconoce y el comportamiento queda
    // pineado byte-idéntico — ancla real que gana y marca contenido borrada.
    @Test
    fun ahoritaComaPorLaNocheRecogieronLaRopa_verboCerradoFuera_lateralFueraPin() =
        assertPin("ahorita, por la noche, recogieron la ropa",
            LocalDateTime.of(2026, 8, 22, 21, 0), "recogieron la ropa")
}

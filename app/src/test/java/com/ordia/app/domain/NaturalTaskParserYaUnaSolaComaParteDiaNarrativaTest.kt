package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1094: segunda lateral ABIERTA registrada en c.1077 (y re-pineada FUERA
 * en c.1083) RESUELTA — la parte del día INTERCALADA con UNA sola coma de
 * una narrativa «ya/ahora/ahorita» en pretérito («ya, por la mañana me tomé
 * la pastilla» — coma de apertura — / «ya por la mañana, me tomé la
 * pastilla» — coma de cierre —) no es ancla: pertenece al enunciado
 * narrativo. Antes el parser fabricaba una fecha falsa (hoy a la hora
 * canónica — relato de hecho cumplido convertido en compromiso que ensucia
 * What Now / recordatorios) Y mutilaba el título de dos formas según la
 * coma («me tomé la pastilla» — borraba marca y parte del día — / «ya , me
 * tomé la pastilla» — dejaba la coma huérfana tras borrar la parte del
 * día) — doble daño P1, simétrico a c.954/c.955/c.1077/c.1083.
 *
 * Medida PRE con sonda efímera /tmp/probe1094/Probe.kt (motor real,
 * now=sábado 2026-08-22 12:00 America/Santo_Domingo): 9/9 capturas con
 * ancla falsa + título mutilado (A1-A4 coma de apertura, B1-B4 coma de
 * cierre); guards/pins intactos. POST: 9/9 resueltas (dueAt=null + título
 * íntegro) con guards byte-idénticos.
 *
 * Evidencia gramatical inequívoca (doctrina c.950, espejo de c.1077/c.1083):
 *  (N1) el prefijo del match es EXACTAMENTE la marca narrativa («ya»,
 *       «ahora» o «ahorita» — la familia c.1027/c.1037 ya trata las tres
 *       uniformemente) con UNA coma de apertura o sin ella cuando la coma
 *       va de cierre;
 *  (N2) el match no lleva «siguiente» ni calificador de día explícito
 *       («de hoy/mañana/ayer»): ambos convierten la frase en ancla real y
 *       ganan (exclusiones doctrina c.955/c.1077/c.1083);
 *  (N3) el predicado abre con pretérito inequívoco
 *       ([weekdayPreteriteNarrativeSuffix]) y la paridad de comas es XOR
 *       (una sola): 0 comas → c.1083, 2 comas → c.1077 (para «ya»; las de
 *       «ahora/ahorita» con dos comas SIGUEN FUERA pineadas byte-idénticas
 *       en esos tests), las ambiguas «salimos» quedan FUERA como en c.950.
 *
 * Fix en DOS puntos (misma doctrina c.1077/c.1083 — decisión sobre el texto
 * ORIGINAL en [parse] + flag propagado):
 *  (1) [yaPreteriteNarrativeSuffix] admite la coma de APERTURA delante del
 *      adverbial de parte del día ACOTADO (alternativa nueva factorizada con
 *      [yaPreteriteNarrativePartOfDay], byte-equivalente para las formas de
 *      dos comas c.1077 y sin comas c.1083): el guard de inmediatez
 *      c.1027/c.1037 suprime el ancla AHORA y conserva la marca; la coma de
 *      CIERRE ya caía en la rama adverbial acotada de c.1035;
 *  (2) el guard hermano [yaSingleCommaPreteriteNarrativePartOfDay] suprime
 *      el ancla de la parte del día y protege el título bajo el MISMO flag
 *      ([forceYaPreteriteNarrative]) en [parse],
 *      [eraseStandalonePartOfDayToken] y [eraseMananaDateToken] — fecha y
 *      título nunca divergen. Las tres formas guardan paridad disjunta
 *      (0/1/2 comas) con las hermanas.
 *
 * Conservador (UNA lateral por ciclo, doctrina anti-overreach c.615):
 * laterales FUERA pineadas byte-idénticas en los tests de las hermanas —
 * las de «ahora/ahorita» con DOS comas (pins c.1077), «siguiente»/«de
 * hoy», presente/imperativo/infinitivo/ambigua, y verbos fuera de la lista
 * cerrada c.950 («acosté»).
 */
class NaturalTaskParserYaUnaSolaComaParteDiaNarrativaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 22, 8, 0).atZone(zone).toInstant().toEpochMilli()

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

    // ---------- capturas: marca narrativa + parte del día con coma de APERTURA + pretérito inequívoco ----------

    @Test
    fun yaComaAperturaPorLaMananaMeTomeLaPastilla_esContenidoNarrativo() =
        assertNarrativeIntact("ya, por la mañana me tomé la pastilla")

    @Test
    fun yaComaAperturaEnLaMananaMeLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("ya, en la mañana me llamó mamá")

    @Test
    fun ahoraComaAperturaPorLaNocheLlegoElCartero_esContenidoNarrativo() =
        assertNarrativeIntact("ahora, por la noche llegó el cartero")

    @Test
    fun ahoritaComaAperturaEnLaTardeSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("ahorita, en la tarde sonó la alarma")

    // ---------- capturas: marca narrativa + parte del día con coma de CIERRE + pretérito inequívoco ----------

    @Test
    fun yaComaCierrePorLaMananaMeTomeLaPastilla_esContenidoNarrativo() =
        assertNarrativeIntact("ya por la mañana, me tomé la pastilla")

    @Test
    fun yaComaCierreEnLaNocheLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ya en la noche, llegó el paquete")

    @Test
    fun ahoraComaCierrePorLaTardeMeLlameAlMedico_esContenidoNarrativo() =
        assertNarrativeIntact("ahora por la tarde, me llamé al médico")

    @Test
    fun ahoritaComaCierreEnLaMananaSalio_esContenidoNarrativo() =
        assertNarrativeIntact("ahorita por la mañana, salió")

    // ---------- regresiones: las hermanas (dos comas c.1077 / sin comas c.1083) quedan íntegras ----------

    @Test
    fun yaDosComasPorLaMananaMeTomeLaPastilla_sigueNarrativaIntacta() =
        assertNarrativeIntact("ya, por la mañana, me tomé la pastilla")

    @Test
    fun yaSinComasPorLaMananaMeTomeLaPastilla_sigueNarrativaIntacta() =
        assertNarrativeIntact("ya por la mañana me tomé la pastilla")

    // ---------- anti-overreach: anclas reales pineadas byte-idénticas ----------

    @Test
    fun yaComaAperturaPorLaMananaDeHoySeLoDije_calificadorDiaSigueAncla() =
        assertPin("ya, por la mañana de hoy se lo dije",
            LocalDateTime.of(2026, 8, 22, 9, 0), "se lo dije")

    @Test
    fun yaSinComaPorLaMananaSiguienteMeLoDije_siguienteSigueAncla() =
        assertPin("ya por la mañana siguiente me lo dije",
            LocalDateTime.of(2026, 8, 23, 9, 0), "me lo dije")

    @Test
    fun compraElPanPorLaManana_anclaRealIntacta() =
        assertPin("compra el pan por la mañana",
            LocalDateTime.of(2026, 8, 22, 9, 0), "compra el pan")

    @Test
    fun yaComaAperturaPorLaMananaPuedoComprarElPan_presenteSigueAncla() =
        assertPin("ya, por la mañana puedo comprar el pan",
            LocalDateTime.of(2026, 8, 22, 9, 0), "puedo comprar el pan")

    @Test
    fun yaComaAperturaPorLaMananaSalimos_ambiguaSigueAncla() =
        assertPin("ya, por la mañana salimos",
            LocalDateTime.of(2026, 8, 22, 9, 0), "salimos")

    @Test
    fun yaComaCierreImperativoCompraElPan_imperativoSigueAncla() =
        assertPin("ya por la mañana, compra el pan",
            LocalDateTime.of(2026, 8, 22, 9, 0), "compra el pan")
}

package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1083: lateral ABIERTA registrada en c.1077 RESUELTA — la parte del día
 * INTERCALADA sin comas de una narrativa «ya/ahora/ahorita» en pretérito
 * («ya por la mañana me tomé la pastilla») no es ancla: pertenece al
 * enunciado narrativo. Antes el parser fabricaba una fecha falsa (hoy a la
 * hora canónica — relato de hecho cumplido convertido en compromiso que
 * ensucia What Now / recordatorios) Y mutilaba el título (borraba la marca
 * narrativa y la parte del día: «me tomé la pastilla») — doble daño P1,
 * simétrico a c.954/c.955/c.1077.
 *
 * Medida PRE con sonda efímera /tmp/probe1083/Probe.kt (motor real,
 * now=sábado 2026-08-22 08:00 America/Santo_Domingo): 10/10 capturas con
 * ancla falsa + título mutilado (incl. la variante con hora explícita al
 * final «…a las 8» → 08:00 falso, hermana de c.1045); pins/hermanos
 * intactos.
 *
 * Evidencia gramatical inequívoca (doctrina c.950, espejo de c.1077):
 *  (N1) el prefijo del match es EXACTAMENTE la marca narrativa («ya»,
 *       «ahora» o «ahorita» — la familia c.1027/c.1037 ya trata las tres
 *       uniformemente) SIN coma: la forma con coma de apertura es lateral
 *       FUERA pineada byte-idéntica abajo;
 *  (N2) el match no lleva «siguiente» ni calificador de día explícito
 *       («de hoy/mañana/ayer»): ambos convierten la frase en ancla real y
 *       ganan (exclusiones doctrina c.955/c.1077);
 *  (N3) el predicado abre con pretérito inequívoco
 *       ([weekdayPreteriteNarrativeSuffix]) y SIN coma de cierre
 *       inmediata: la forma con una sola coma de cierre es lateral FUERA
 *       pineada byte-idéntica (un encargo real jamás abre su predicado en
 *       pretérito; las ambiguas «salimos» quedan FUERA como en c.950).
 *
 * Fix en DOS puntos (misma doctrina c.1077 — decisión sobre el texto
 * ORIGINAL en [parse] + flag propagado):
 *  (1) [yaPreteriteNarrativeSuffix] admite el adverbial de parte del día
 *      SIN comas entre la marca y el predicado (alternativa nueva junto a
 *      la cláusula entre comas de c.1035): así el guard de inmediatez
 *      c.1027/c.1037 suprime el ancla AHORA y conserva la marca, y
 *      [narrativePreteritePrefix] reconoce la cadena completa (la hora
 *      explícita final «…a las 8» queda protegida como en c.1045). La
 *      reestructura de la regex es conservadora: la coma de apertura sólo
 *      vale con la cláusula adverbial cerrada en coma (c.1035), así las
 *      formas con una sola coma quedan byte-idénticas;
 *  (2) el guard [yaNoCommaPreteriteNarrativeStandalonePartOfDay] suprime
 *      el ancla de la parte del día y protege el título bajo el MISMO flag
 *      (renombrado forceYaPreteriteNarrative: ahora cubre la forma con
 *      comas de c.1077 y la forma sin comas) en [parse],
 *      [eraseStandalonePartOfDayToken] y [eraseMananaDateToken] — fecha y
 *      título nunca divergen.
 *
 * Conservador (UNA lateral por ciclo, doctrina anti-overreach c.615):
 * laterales FUERA pineadas byte-idénticas abajo — una sola coma (apertura
 * o cierre), las formas con comas de «ahora/ahorita» (pins c.1077),
 * «siguiente»/«de hoy», presente/infinitivo/ambigua, y verbos fuera de la
 * lista cerrada c.950 («acosté»).
 */
class NaturalTaskParserYaSinComaParteDiaNarrativaTest {

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

    // ---------- capturas: marca narrativa SIN comas + parte del día + pretérito inequívoco ----------

    @Test
    fun yaSinComasPorLaMananaMeTomeLaPastilla_esContenidoNarrativo() =
        assertNarrativeIntact("ya por la mañana me tomé la pastilla")

    @Test
    fun yaSinComasPorLaNocheLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ya por la noche llegó el paquete")

    @Test
    fun yaSinComasEnLaTardeSeFueLaLuz_esContenidoNarrativo() =
        assertNarrativeIntact("ya en la tarde se fue la luz")

    @Test
    fun yaSinComasEnLaMananaMeDesperte_esContenidoNarrativo() =
        assertNarrativeIntact("ya en la mañana me desperté con el ruido")

    @Test
    fun yaSinComasPorLaMadrugadaSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("ya por la madrugada sonó la alarma")

    @Test
    fun yaSinComasDeLaNocheMeLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("ya de la noche me llamó mamá")

    @Test
    fun yaSinComasDuranteLaNocheMeLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("ya durante la noche me llamó mamá")

    @Test
    fun ahoraSinComasPorLaTardeLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ahora por la tarde llegó el paquete")

    @Test
    fun ahoritaSinComasEnLaMananaMeLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("ahorita en la mañana me llamó mamá")

    @Test
    fun yaSinComasPorLaMananaMeTomeLaPastillaALas8_esContenidoNarrativo() =
        assertNarrativeIntact("ya por la mañana me tomé la pastilla a las 8")

    // ---------- hermanos narrativos ya resueltos: regresión ----------

    @Test
    fun yaComaPorLaMananaMeTomeLaPastilla_regresionC1077() =
        assertNarrativeIntact("ya, por la mañana, me tomé la pastilla")

    @Test
    fun yaMeTomeLaPastillaALas8_regresionC1045() =
        assertNarrativeIntact("ya me tomé la pastilla a las 8")

    @Test
    fun yaComaAPrimeraHoraSonoLaAlarma_regresionC1035() =
        assertNarrativeIntact("ya, a primera hora, sonó la alarma")

    @Test
    fun yaSonoLaAlarma_regresionC1027() =
        assertNarrativeIntact("ya sonó la alarma")

    @Test
    fun yaComaSonoLaAlarma_regresionComaAntesDelVerbo() =
        assertNarrativeIntact("ya, sonó la alarma")

    @Test
    fun ahoraLlegoElCartero_regresionC1037() =
        assertNarrativeIntact("ahora llegó el cartero")

    // ---------- anclas reales: pineadas byte-idénticas ----------

    @Test
    fun tomarLaPastillaPorLaManana_anclaRealIntacta() =
        assertPin("tomar la pastilla por la mañana",
            LocalDateTime.of(2026, 8, 22, 9, 0), "tomar la pastilla")

    @Test
    fun reunionPorLaNoche_anclaRealIntacta() =
        assertPin("reunión por la noche",
            LocalDateTime.of(2026, 8, 22, 21, 0), "reunión")

    @Test
    fun llamameYa_inmediatezComandoIntacta() =
        assertPin("llámame ya",
            LocalDateTime.of(2026, 8, 22, 8, 0), "llámame")

    @Test
    fun ahoraLlamoAlBanco_inmediatezComandoIntacta() =
        assertPin("ahora llamo al banco",
            LocalDateTime.of(2026, 8, 22, 8, 0), "llamo al banco")

    // ---------- anti-overreach: presente/ambigua/«siguiente»/«de hoy»/verbo fuera de la lista SIGUEN ancla ----------

    @Test
    fun yaSinComasPorLaMananaLlamoAlBanco_presenteSigueAncla() =
        assertPin("ya por la mañana llamo al banco",
            LocalDateTime.of(2026, 8, 22, 9, 0), "llamo al banco")

    @Test
    fun yaSinComasPorLaMananaSalimos_ambiguaSigueAncla() =
        assertPin("ya por la mañana salimos",
            LocalDateTime.of(2026, 8, 22, 9, 0), "salimos")

    @Test
    fun yaSinComasPorLaMananaSiguienteMeFui_siguienteSigueAncla() =
        assertPin("ya por la mañana siguiente me fui",
            LocalDateTime.of(2026, 8, 23, 9, 0), "me fui")

    @Test
    fun yaSinComasPorLaMananaDeHoyMeFui_calificadorSigueAncla() =
        assertPin("ya por la mañana de hoy me fui",
            LocalDateTime.of(2026, 8, 22, 9, 0), "me fui")

    @Test
    fun yaSinComasALaNocheMeAcoste_verboFueraDeLaListaSigueAncla() =
        assertPin("ya a la noche me acosté temprano",
            LocalDateTime.of(2026, 8, 22, 21, 0), "me acosté")

    // ---------- laterales FUERA pineadas byte-idénticas (registradas en BACKLOG) ----------

    @Test
    fun yaComaSoloApertura_lateralFueraPin() =
        assertPin("ya, por la mañana me tomé la pastilla",
            LocalDateTime.of(2026, 8, 22, 9, 0), "me tomé la pastilla")

    @Test
    fun yaComaSoloCierre_lateralFueraPin() =
        assertPin("ya por la mañana, me tomé la pastilla",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ya , me tomé la pastilla")

    @Test
    fun ahoraComaPorLaTardeLlegoElPaquete_lateralFueraPinC1077() =
        assertPin("ahora, por la tarde, llegó el paquete",
            LocalDateTime.of(2026, 8, 22, 15, 0), "ahora, , llegó el paquete")

    @Test
    fun ahoritaComaPorLaMananaMeLlamoMama_lateralFueraPinC1077() =
        assertPin("ahorita, por la mañana, me llamó mamá",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ahorita, , me llamó mamá")
}

package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1077: residual c.1035 RESUELTO — la parte del día que es el adverbial
 * INTERCALADO entre comas de una narrativa «ya, <adverbial>, <pretérito>»
 * («ya, por la mañana, me tomé la pastilla») no es ancla: pertenece al
 * enunciado narrativo. Antes el parser fabricaba una fecha falsa (hoy a la
 * hora canónica — relato de hecho cumplido convertido en compromiso que
 * ensucia What Now / recordatorios) Y mutilaba el título con una coma
 * residual («ya, , me tomé la pastilla») — doble daño P1.
 *
 * Medida PRE con sonda efímera /tmp/probe1077/Probe.kt (motor real,
 * now=sábado 2026-08-22 08:00 America/Santo_Domingo): 6/6 capturas con
 * ancla falsa + título mutilado; hermanos c.1035 («ya, a primera hora,
 * sonó la alarma») y c.1045 («ya me tomé la pastilla a las 8») ya
 * narrativos; anclas reales («tomar la pastilla por la mañana») intactas.
 *
 * Evidencia gramatical inequívoca y simétrica a c.954/c.955 (doctrina
 * c.950): (N1) el prefijo es EXACTAMENTE «ya,» (la cadena abre con la
 * marca narrativa y su coma); (N2) el adverbial cierra con coma antes del
 * predicado (forma intercalada medida); (N3) el predicado abre con
 * pretérito inequívoco ([weekdayPreteriteNarrativeSuffix]) — un encargo
 * real jamás abre su predicado en pretérito. Fecha y título fluyen del
 * MISMO guard ([yaCommaPreteriteNarrativeIntercalatedPartOfDay]) en la
 * resolución, en [eraseStandalonePartOfDayToken] y en
 * [mananaOccurrenceIsContent] (G5'): nunca divergen.
 *
 * Conservador (UNA lateral por ciclo, doctrina anti-overreach c.615):
 * laterales FUERA pineadas byte-idénticas abajo — prefijos «ahora/ahorita»
 * con el mismo daño medido, variantes con una sola coma, forma sin comas,
 * «siguiente»/«de hoy» (exclusiones doctrina c.955) y ambiguas
 * pretérito/presente («salimos»).
 */
class NaturalTaskParserYaComaParteDiaNarrativaTest {

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

    // ---------- capturas: «ya,» + parte del día entre comas + pretérito inequívoco ----------

    @Test
    fun yaComaPorLaMananaMeTomeLaPastilla_esContenidoNarrativo() =
        assertNarrativeIntact("ya, por la mañana, me tomé la pastilla")

    @Test
    fun yaComaPorLaNocheLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ya, por la noche, llegó el paquete")

    @Test
    fun yaComaEnLaTardeSeFueLaLuz_esContenidoNarrativo() =
        assertNarrativeIntact("ya, en la tarde, se fue la luz")

    @Test
    fun yaComaEnLaMananaMeDesperte_esContenidoNarrativo() =
        assertNarrativeIntact("ya, en la mañana, me desperté con el ruido")

    @Test
    fun yaComaPorLaMadrugadaSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("ya, por la madrugada, sonó la alarma")

    @Test
    fun yaComaDeLaNocheMeLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("ya, de la noche, me llamó mamá")

    @Test
    fun yaEspacioComaPorLaManana_esContenidoNarrativo() =
        assertNarrativeIntact("ya , por la mañana, me tomé la pastilla")

    // ---------- hermanos narrativos ya resueltos: regresión ----------

    @Test
    fun yaComaAPrimeraHoraSonoLaAlarma_regresionC1035() =
        assertNarrativeIntact("ya, a primera hora, sonó la alarma")

    @Test
    fun yaComaAUltimaHoraSeLoDije_regresionC1035() =
        assertNarrativeIntact("ya, a última hora, se lo dije")

    @Test
    fun yaMeTomeLaPastillaALas8_regresionC1045() =
        assertNarrativeIntact("ya me tomé la pastilla a las 8")

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
    fun dejarElPaqueteEnLaTarde_anclaRealIntacta() =
        assertPin("dejar el paquete en la tarde",
            LocalDateTime.of(2026, 8, 22, 15, 0), "dejar el paquete")

    // ---------- anti-overreach: presente/infinitivo/ambigua SIGUEN ancla (byte-idénticos) ----------

    @Test
    fun yaComaPorLaMananaLlamoAlBanco_presenteSigueAncla() =
        assertPin("ya, por la mañana, llamo al banco",
            LocalDateTime.of(2026, 8, 22, 9, 0), "llamo al banco")

    @Test
    fun yaComaPorLaNocheCenoConAna_presenteSigueAncla() =
        assertPin("ya, por la noche, ceno con Ana",
            LocalDateTime.of(2026, 8, 22, 21, 0), "ceno con Ana")

    @Test
    fun yaComaPorLaMananaTomarLaPastilla_infinitivoSigueAncla() =
        assertPin("ya, por la mañana, tomar la pastilla",
            LocalDateTime.of(2026, 8, 22, 9, 0), "tomar la pastilla")

    @Test
    fun yaComaPorLaMananaSalimos_ambiguaSigueAncla() =
        assertPin("ya, por la mañana, salimos",
            LocalDateTime.of(2026, 8, 22, 9, 0), "salimos")

    // ---------- laterales FUERA pineadas byte-idénticas (registradas en BACKLOG) ----------

    @Test
    fun ahoraComaPorLaTardeLlegoElPaquete_lateralFueraPin() =
        assertPin("ahora, por la tarde, llegó el paquete",
            LocalDateTime.of(2026, 8, 22, 15, 0), "ahora, , llegó el paquete")

    @Test
    fun ahoritaComaPorLaMananaMeLlamoMama_lateralFueraPin() =
        assertPin("ahorita, por la mañana, me llamó mamá",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ahorita, , me llamó mamá")

    // c.1083: re-pin legítimo (precedente c.1035/c.1041) — la forma SIN
    // comas era lateral FUERA pineada byte-idéntica (09:00 falso + título
    // «me tomé la pastilla»); la misma medida la mostró narrativa y c.1083
    // la resolvió con la tercera alternativa de [yaPreteriteNarrativeSuffix]
    // + el guard hermano sin comas. Las formas con UNA sola coma y las de
    // «ahora/ahorita» con comas SIGUEN FUERA pineadas byte-idénticas abajo.
    @Test
    fun yaSinComasPorLaManana_resueltaC1083() =
        assertNarrativeIntact("ya por la mañana me tomé la pastilla")

    @Test
    fun yaComaSoloApertura_lateralFueraPin() =
        assertPin("ya, por la mañana me tomé la pastilla",
            LocalDateTime.of(2026, 8, 22, 9, 0), "me tomé la pastilla")

    @Test
    fun yaComaSoloCierre_lateralFueraPin() =
        assertPin("ya por la mañana, me tomé la pastilla",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ya , me tomé la pastilla")

    @Test
    fun yaComaPorLaMananaSiguienteMeFui_lateralFueraPin() =
        assertPin("ya, por la mañana siguiente, me fui",
            LocalDateTime.of(2026, 8, 23, 9, 0), "ya, , me fui")

    @Test
    fun yaComaPorLaMananaDeHoyMeFui_lateralFueraPin() =
        assertPin("ya, por la mañana de hoy, me fui",
            LocalDateTime.of(2026, 8, 22, 9, 0), "ya, , me fui")
}

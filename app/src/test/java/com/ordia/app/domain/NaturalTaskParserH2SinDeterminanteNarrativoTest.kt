package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.954: narrativa ordinal H2 (genitivo de CONTENIDO tras el match: «de
 * clase», «del partido», «de trabajo») SIN determinante ni «en» al inicio del
 * texto («primera hora de clase me quedé dormido») — lateral medida FUERA en
 * c.950/c.952 (pin byte-idéntico
 * [NaturalTaskParserH2EnSinArticuloNarrativoTest.primeraHoraDeClase_sinDeterminanteNiEnLateralFueraPin]
 * — re-pin legítimo MÁS estricto en este ciclo, precedente c.925…c.951)
 * y verificada con sonda efímera `/tmp/probe954/ProbePreFix.kt` (motor real
 * vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo,
 * base c.953 merge `27ea1f8`): PRE — 5/5 candidatas con DOBLE daño P1 (fecha
 * FALSA [hoy 09:00/18:00, lun 2026-08-24 09:00 en la compuesta con weekday]
 * Y título mutilado [«de clase», «de trabajo», «del partido llegó el gol»… —
 * el ordinal borrado: contenido del usuario]); 4/4 guards bivalentes ancla
 * correctos (verbo precedente «avisar/quiero/recordar», conector «avisar a
 * primera…»); 4/4 regresiones c.937/c.951/c.952 intactas; 4/4 pines FUERA
 * medidos (H3 «en» sin determinante c.946, H3-bare, weekday-bare).
 *
 * Doctrina (extensión simétrica de la rama H2 c.937, como c.951 lo fue del
 * «en» y c.952 del «en» sin artículo): cuando TODO el prefijo es VACÍO
 * (aparición al inicio del texto, «en blanco», módulo espacios) y hay
 * genitivo de contenido tras el match [ordinalHoraContentGenitive], el
 * ordinal es CONTENIDO narrativo: no ancla fecha ni se borra del título.
 * Con verbo/nombre anterior («avisar…», «quiero…») o conector «a»
 * [ordinalHoraOccurrenceIsContent] el prefijo no está «en blanco» y la forma
 * sigue la doctrina ancla (byte-idéntica). Como la H2 con artículo definido
 * (c.937), con indefinido (c.951) y con «en» (c.952), no se exige predicado:
 * el fragmento nominal «primera hora de clase» tampoco es ancla (PRE nacía
 * con fecha falsa y título mutilado — mejora estricta medida). La H3-bare
 * («primera hora de la mañana…») y la weekday-bare («primera hora del
 * lunes…») quedan FUERA: genitivos-ancla [ordinalHoraAnchorGenitives]
 * bivalentes, pin conservador (ancla byte-idéntica). Determinista (regex),
 * cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserH2SinDeterminanteNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    private fun assertAnchor(text: String, date: LocalDate, hour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(date, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(hour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas: H2 + prefijo «en blanco» (± predicado, ± weekday compuesto) ----

    @Test fun primeraHoraDeClaseMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("primera hora de clase me quedé dormido")

    @Test fun ultimaHoraDelPartidoLlegoElGol_esContenidoNarrativo() =
        assertNarrativeIntact("última hora del partido llegó el gol")

    @Test fun primerasHorasDeTrabajoAvanceMucho_esContenidoNarrativo() =
        assertNarrativeIntact("primeras horas de trabajo avancé mucho")

    @Test fun ultimasHorasDeLaReunionLlegoElAcuerdo_esContenidoNarrativo() =
        assertNarrativeIntact("últimas horas de la reunión llegó el acuerdo")

    @Test fun primerMomentoDeLaPeliculaSupeQueEraBuena_esContenidoNarrativo() =
        assertNarrativeIntact("primer momento de la película supe que era buena")

    @Test fun primeraHoraDeClaseDelLunesMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("primera hora de clase del lunes me quedé dormido")

    @Test fun primeraHoraDeClase_fragmentoNominalSinPredicado_esContenidoNarrativo() =
        assertNarrativeIntact("primera hora de clase")

    // ---- Guards bivalentes: la forma sigue ANCLA byte-idéntica ----

    @Test fun avisarPrimeraHoraDeClase_verboPrecedenteSigueAncla() =
        assertAnchor(
            "avisar primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar de clase"
        )

    @Test fun quieroPrimerasHorasDeTrabajo_verboPrecedenteSigueAncla() =
        assertAnchor(
            "quiero primeras horas de trabajo",
            LocalDate.of(2026, 8, 23), 9, "quiero de trabajo"
        )

    @Test fun avisarAPrimeraHoraDeClase_conectorASigueAncla() =
        assertAnchor(
            "avisar a primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar de clase"
        )

    @Test fun recordarUltimaHoraDelPartido_verboPrecedenteSigueAncla() =
        assertAnchor(
            "recordar última hora del partido",
            LocalDate.of(2026, 8, 23), 18, "recordar del partido"
        )

    // ---- Regresiones (conducta ya protegida por c.937/c.951/c.952) ----

    @Test fun enPrimeraHoraDeClaseMeQuedeDormido_regresionC952() =
        assertNarrativeIntact("en primera hora de clase me quedé dormido")

    @Test fun unaPrimeraHoraDeClaseFueGenial_regresionC951() =
        assertNarrativeIntact("una primera hora de clase fue genial")

    @Test fun laPrimeraHoraDeClaseFueGenial_regresionC937() =
        assertNarrativeIntact("la primera hora de clase fue genial")

    @Test fun avisarAPrimeraHora_regresionConector() =
        assertAnchor(
            "avisar a primera hora",
            LocalDate.of(2026, 8, 23), 9, "avisar"
        )

    // ---- Pines byte-idénticos de laterales FUERA (medidos PRE en la sonda) ----

    @Test fun enPrimeraHoraDeLaMananaLlameAlBanco_h3SinDeterminanteBivalentePinC946() =
        assertAnchor(
            "en primera hora de la mañana llamé al banco",
            LocalDate.of(2026, 8, 23), 9, "en llamé al banco"
        )

    @Test fun primeraHoraDeLaMananaLlameAlBanco_h3BareGenitivoAnclaPin() =
        assertAnchor(
            "primera hora de la mañana llamé al banco",
            LocalDate.of(2026, 8, 23), 9, "llamé al banco"
        )

    @Test fun primeraHoraDelLunesMeQuedeDormido_weekdayBareBivalentePin() =
        assertAnchor(
            "primera hora del lunes me quedé dormido",
            LocalDate.of(2026, 8, 24), 9, "me quedé dormido"
        )
}

package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.952: narrativa ordinal H2 (genitivo de CONTENIDO tras el match: «de
 * clase», «del partido», «de la película») con preposición «en» SIN artículo
 * al inicio del texto («en primera hora de clase me quedé dormido») — lateral
 * medida FUERA en c.951 (pins byte-idénticos
 * [NaturalTaskParserH2IndefinidoNarrativoTest.enPrimeraHoraDeClase_h2EnSinArticuloLateralFueraPin]
 * y hermano — re-pin legítimo MÁS estricto en este ciclo, precedente
 * c.925…c.951) y verificada con sonda efímera `/tmp/probe952/PreProbe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, base c.951 merge `81506f1`): PRE — 7/7 candidatas
 * con DOBLE daño P1 (fecha FALSA [hoy 09:00/18:00, y lun 2026-08-24 09:00 en
 * la compuesta con weekday] Y título mutilado [«en de clase me quedé
 * dormido», «en del partido llegó el gol»… — el ordinal borrado: contenido
 * del usuario]); 4/4 guards bivalentes ancla correctos (verbo precedente
 * «avisar en…»/«quiero en…»/«recordar en…», conector «avisar a primera…»);
 * 5/5 regresiones c.937/c.946/c.951 intactas; 3/3 laterales FUERA medidas
 * (H3 sin determinante bivalente c.946; ordinal sin determinante ni «en» —
 * resuelto narrativo en c.954 via prefijo «en blanco», pin volcado abajo).
 *
 * Doctrina (extensión simétrica de la rama H2 c.937, como c.946 lo fue de la
 * rama weekday y c.951 del indefinido H2): cuando TODO el prefijo es «en»
 * SIN artículo al inicio del texto ([NaturalTaskParser] val
 * `ordinalHoraNarrativeEnPrefix`, anclado `^…$`) y hay genitivo de contenido
 * tras el match, el ordinal es CONTENIDO narrativo: no ancla fecha ni se
 * borra del título. Con verbo/nombre precedente («avisar en…», «quiero
 * en…») el prefijo no empieza en «en» y la forma sigue la doctrina ancla
 * (byte-idéntica). Como la H2 con artículo definido (c.937) y con indefinido
 * (c.951), no se exige predicado: el fragmento nominal «en primera hora de
 * clase» tampoco es ancla (PRE nacía con fecha falsa y título mutilado —
 * mejora estricta medida). La H3 sin determinante («en primera hora de la
 * mañana llamé/llamar al banco») sigue FUERA: bivalente real
 * comando/narrativa indistinguible por regex (pin conservador c.946, pins
 * byte-idénticos en este test). Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserH2EnSinArticuloNarrativoTest {

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

    // ---- Capturas: H2 + «en» SIN artículo al inicio (± weekday compuesto, ± predicado) ----

    @Test fun enPrimeraHoraDeClaseMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora de clase me quedé dormido")

    @Test fun enUltimaHoraDelPartidoLlegoElGol_esContenidoNarrativo() =
        assertNarrativeIntact("en última hora del partido llegó el gol")

    @Test fun enPrimerasHorasDeTrabajoAvanceMucho_esContenidoNarrativo() =
        assertNarrativeIntact("en primeras horas de trabajo avancé mucho")

    @Test fun enUltimasHorasDeLaReunionLlegoElAcuerdo_esContenidoNarrativo() =
        assertNarrativeIntact("en últimas horas de la reunión llegó el acuerdo")

    @Test fun enPrimerMomentoDeLaPeliculaSupeQueEraBuena_esContenidoNarrativo() =
        assertNarrativeIntact("en primer momento de la película supe que era buena")

    @Test fun enPrimeraHoraDeClaseDelLunesMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora de clase del lunes me quedé dormido")

    @Test fun enPrimeraHoraDeClase_fragmentoNominalSinPredicado_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora de clase")

    // ---- Guards bivalentes: la forma sigue ANCLA byte-idéntica ----

    @Test fun avisarEnPrimeraHoraDeClase_verboPrecedenteSigueAncla() =
        assertAnchor(
            "avisar en primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar en de clase"
        )

    @Test fun quieroEnPrimeraHoraDeClaseLlamarAJuan_verboPrecedenteSigueAncla() =
        assertAnchor(
            "quiero en primera hora de clase llamar a juan",
            LocalDate.of(2026, 8, 23), 9, "quiero en de clase llamar a juan"
        )

    @Test fun avisarAPrimeraHoraDeClase_conectorASigueAncla() =
        assertAnchor(
            "avisar a primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar de clase"
        )

    @Test fun recordarEnUltimaHoraDelPartido_verboPrecedenteSigueAncla() =
        assertAnchor(
            "recordar en última hora del partido",
            LocalDate.of(2026, 8, 23), 18, "recordar en del partido"
        )

    // ---- Regresiones (conducta ya protegida por c.937/c.946/c.951) ----

    @Test fun enPrimeraHoraDelLunesMeQuedeDormido_regresionC946() =
        assertNarrativeIntact("en primera hora del lunes me quedé dormido")

    @Test fun enUnaPrimeraHoraDeClaseMeQuedeDormido_regresionC951() =
        assertNarrativeIntact("en una primera hora de clase me quedé dormido")

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
    // c.954: el pin «sin determinante ni «en»» («primera hora de clase») se
    // resolvió narrativo vía prefijo «en blanco» (NaturalTaskParserH2SinDeterminanteNarrativoTest);
    // quedan FUERA sólo los genitivos-ancla (H3 sin determinante, c.946).

    @Test fun enPrimeraHoraDeLaMananaLlameAlBanco_h3SinDeterminanteBivalentePinC946() =
        assertAnchor(
            "en primera hora de la mañana llamé al banco",
            LocalDate.of(2026, 8, 23), 9, "en llamé al banco"
        )

    @Test fun enPrimeraHoraDeLaMananaLlamarAlBanco_h3SinDeterminanteBivalentePinC946() =
        assertAnchor(
            "en primera hora de la mañana llamar al banco",
            LocalDate.of(2026, 8, 23), 9, "en llamar al banco"
        )
}

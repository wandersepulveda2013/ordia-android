package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.944: narrativa ordinal H3-CANÓNICA (genitivo canónico DENTRO del match:
 * «de la mañana/madrugada/tarde/noche», «del día») con ARTÍCULO INDEFINIDO al
 * inicio del texto + predicado («unas primeras horas de la mañana son
 * duras» = the first hours of the morning are tough). Lateral medida FUERA
 * en c.943 (extensión propia de [NaturalTaskParser] `ordinalHoraNarrativeDeterminer`
 * / rama H3) y verificada en este ciclo con sonda efímera
 * `/tmp/probe944/PreProbe.kt` (motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00 America/Santo_Domingo, base c.943 `61b79c3`):
 * PRE — 8/8 candidatas con DOBLE daño P1 (fecha FALSA [hoy 09:00 / 15:00 /
 * 18:00 / 21:00 / 04:00, y lun 2026-08-24 09:00 en la compuesta con weekday]
 * Y título mutilado [«unas son duras», «una fue eterna», «un fue especial»…
 * — ordinal + genitivo canónico borrados: contenido del usuario]); 7/7
 * guards bivalentes ancla correctos (verbo precedente «quiero/prefiero …»,
 * fragmento sin predicado, anclas con conector «a primera/última hora»);
 * 5/5 regresiones c.932/c.935/c.943 intactas; pines de laterales FUERA
 * («en primera hora del lunes…» sin artículo, «una primera hora de clase…»
 * H2 con indefinido) medidos byte-idénticos. c.946: la lateral «en» sin
 * artículo quedó RESUELTA (re-pin estricto en
 * [enPrimeraHoraDelLunes_sinArticuloLateralResueltaC946]; cobertura canónica
 * en `NaturalTaskParserWeekdayEnSinArticuloNarrativoTest`).
 *
 * Doctrina (extensión simétrica de la rama H3 c.932, como la cláusula de
 * opinión c.935): cuando TODO el prefijo es un artículo INDEFINIDO al inicio
 * del texto («una/un/unas/unos», anclado `^…$`) y hay predicado a
 * continuación, el ordinal con genitivo canónico dentro del match es
 * CONTENIDO narrativo: no ancla fecha ni se borra del título. «unas» al
 * inicio es inequívoco de sujeto narrativo (el ancla de las 09:00/18:00
 * siempre lleva conector «a», c.102/c.546/c.931); con verbo precedente
 * («quiero/prefiero una…») el prefijo no empieza en el indefinido y la
 * forma sigue la doctrina ancla (byte-idéntica). El fragmento sin predicado
 * sigue bivalente/ancla. La resolución, la supresión de la parte-del-día
 * gobernada, la protección del weekday genitivo posterior (rama H3 de
 * `ordinalHoraNarrativeWeekdayRanges`) y el borrado del título fluyen del
 * mismo predicado (nunca divergen). Determinista (regex), cero random, cero
 * IA fingida, cero UI.
 */
class NaturalTaskParserIndefinidoH3NarrativoTest {

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

    // ---- Capturas: indefinido al inicio + genitivo canónico DENTRO del match + predicado ----

    @Test fun unasPrimerasHorasDeLaMananaSonDuras_esContenidoNarrativo() =
        assertNarrativeIntact("unas primeras horas de la mañana son duras")

    @Test fun unaUltimaHoraDeLaTardeFueEterna_esContenidoNarrativo() =
        assertNarrativeIntact("una última hora de la tarde fue eterna")

    @Test fun unaPrimeraHoraDeLaMananaFueReveladora_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de la mañana fue reveladora")

    @Test fun unasUltimasHorasDelDiaFueronEternas_esContenidoNarrativo() =
        assertNarrativeIntact("unas últimas horas del día fueron eternas")

    @Test fun unPrimerMomentoDelDiaFueEspecial_esContenidoNarrativo() =
        assertNarrativeIntact("un primer momento del día fue especial")

    @Test fun unaUltimaHoraDeLaNocheFueTensa_esContenidoNarrativo() =
        assertNarrativeIntact("una última hora de la noche fue tensa")

    @Test fun unasPrimerasHorasDeLaMadrugadaFueronLargas_esContenidoNarrativo() =
        assertNarrativeIntact("unas primeras horas de la madrugada fueron largas")

    @Test fun unaPrimeraHoraDeLaMananaDelLunesFueRara_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de la mañana del lunes fue rara")

    // ---- Guards bivalentes: ancla vigente, BYTE-IDÉNTICOS (medidos PRE) ----

    @Test fun quieroUnaPrimeraHoraDeLaManana_sigueAncla() =
        assertAnchor(
            "quiero una primera hora de la mañana para estudiar",
            LocalDate.of(2026, 8, 23), 9, "quiero una para estudiar"
        )

    @Test fun unasPrimerasHorasDeLaManana_sinPredicadoSigueAncla() =
        assertAnchor(
            "unas primeras horas de la mañana",
            LocalDate.of(2026, 8, 23), 9, "unas"
        )

    @Test fun unaUltimaHoraDeLaTarde_sinPredicadoSigueAncla() =
        assertAnchor(
            "una última hora de la tarde",
            LocalDate.of(2026, 8, 23), 15, "una"
        )

    @Test fun avisarAPrimeraHoraDeLaManana_sigueAncla() =
        assertAnchor(
            "avisar a primera hora de la mañana",
            LocalDate.of(2026, 8, 23), 9, "avisar"
        )

    @Test fun avisarAUltimaHoraDeLaTarde_sigueAncla() =
        assertAnchor(
            "avisar a última hora de la tarde",
            LocalDate.of(2026, 8, 23), 15, "avisar"
        )

    @Test fun prefieroUnasPrimerasHorasDeLaManana_sigueAncla() =
        assertAnchor(
            "prefiero unas primeras horas de la mañana",
            LocalDate.of(2026, 8, 23), 9, "prefiero unas"
        )

    @Test fun quieroUnasUltimasHorasDelDia_sigueAncla() =
        assertAnchor(
            "quiero unas últimas horas del día para cerrar",
            LocalDate.of(2026, 8, 23), 18, "quiero unas para cerrar"
        )

    // ---- Regresiones narrativas ya protegidas (BYTE-IDÉNTICAS) ----

    @Test fun lasPrimerasHorasDeLaMananaSonLasMejores_regresionC932() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun creoQueLasPrimerasHoras_regresionC935() =
        assertNarrativeIntact("creo que las primeras horas de la mañana son las mejores")

    @Test fun unaPrimeraHoraDelLunesFueRara_regresionC943() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun enLasPrimerasHorasDelDiaTrabajeMejor_regresionC932() =
        assertNarrativeIntact("en las primeras horas del día trabajé mejor")

    @Test fun lasPrimerasHorasDeLaMananaDelLunes_regresionC936() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    // ---- Pines byte-idénticos de laterales FUERA (medidos PRE) ----

    // c.946: la lateral «en» SIN artículo quedó RESUELTA (doctrina simétrica
    // c.942 con el artículo elidido). Re-pin legítimo MÁS estricto del pin
    // c.944 (precedente c.925…c.944): ahora aserta contenido narrativo
    // íntegro. La cobertura canónica vive en
    // NaturalTaskParserWeekdayEnSinArticuloNarrativoTest.
    @Test fun enPrimeraHoraDelLunes_sinArticuloLateralResueltaC946() =
        assertNarrativeIntact("en primera hora del lunes me quedé dormido")

    // c.950: la lateral H2 con indefinido quedó RESUELTA (doctrina simétrica
    // a la rama H2 c.937 con el indefinido de c.943). Re-pin legítimo MÁS
    // estricto (precedente c.925…c.948): ahora aserta contenido narrativo
    // íntegro. La cobertura canónica vive en
    // NaturalTaskParserH2IndefinidoNarrativoTest.
    @Test fun unaPrimeraHoraDeClase_h2IndefinidoLateralResueltaC950() =
        assertNarrativeIntact("una primera hora de clase fue genial")
}

package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.948: narrativa ordinal con preposición «en» + ARTÍCULO INDEFINIDO al
 * inicio del texto + weekday genitivo (DIRECTO o tras genitivo INTERIOR de
 * parte del día) + predicado («en una primera hora del lunes fue rara»,
 * «en una primera hora de la noche del sábado sonó el teléfono») — el hueco
 * entre c.943 (indefinido SIN «en» + weekday) y c.946 («en» SIN artículo +
 * weekday). Lateral medida FUERA en c.947 (registrada como pin byte-idéntico
 * [enUnaPrimeraHoraDelLunes_weekdayEnIndefinidoLateralFueraPin] en
 * `NaturalTaskParserEnIndefinidoH3NarrativoTest` — re-pin legítimo MÁS
 * estricto en este ciclo, precedente c.925…c.947) y verificada con sonda
 * efímera `/tmp/probe948/PreProbe.kt` (motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00 America/Santo_Domingo, base c.947 `1b24074`):
 * PRE — 6/6 candidatas con DOBLE daño P1 (fecha FALSA del weekday
 * [lun 2026-08-24 09:00 / vie 2026-08-28 09:00 / dom 2026-08-30 09:00 /
 * mar 2026-08-25 18:00 / sáb 2026-08-29 21:00 interior noche / sáb 15:00
 * interior tarde] Y título mutilado [«en una fue rara», «en unas cerré el
 * trato»… — ordinal + genitivo borrados: contenido del usuario]); 4/4 guards
 * bivalentes ancla correctos (verbo precedente «avisar/quiero en una…»,
 * fragmento sin predicado, «que viene»); 5/5 regresiones c.942/c.943/c.946/
 * c.947 intactas; 2/2 pines de laterales FUERA (H2 con indefinido) medidos
 * byte-idénticos.
 *
 * Doctrina (extensión simétrica de c.943, como c.946 lo fue de c.942): cuando
 * TODO el prefijo es «en» + artículo INDEFINIDO al inicio del texto
 * (anclado `^…$`) y hay predicado a continuación, el ordinal con weekday
 * genitivo es CONTENIDO narrativo: no ancla fecha ni se borra del título.
 * Con verbo/nombre/cláusula precedente («avisar en una…») el prefijo no
 * empieza en «en» y la forma sigue la doctrina ancla (byte-idéntica). El
 * genitivo de CONTENIDO H2 («en una primera hora de clase…») sigue exigiendo
 * artículo definido — lateral conservadora (pin byte-idéntico). Determinista
 * (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserWeekdayEnIndefinidoNarrativoTest {

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

    // ---- Capturas: «en» + indefinido al inicio + weekday genitivo (directo/interior) + predicado ----

    @Test fun enUnaPrimeraHoraDelLunesFueRara_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora del lunes fue rara")

    @Test fun enUnasPrimerasHorasDelViernesCierreElTrato_esContenidoNarrativo() =
        assertNarrativeIntact("en unas primeras horas del viernes cerré el trato")

    @Test fun enUnPrimerMomentoDelDomingoSupeQueEraElla_esContenidoNarrativo() =
        assertNarrativeIntact("en un primer momento del domingo supe que era ella")

    @Test fun enUnaUltimaHoraDelMartesLlegoLaNoticia_esContenidoNarrativo() =
        assertNarrativeIntact("en una última hora del martes llegó la noticia")

    @Test fun enUnaPrimeraHoraDeLaNocheDelSabadoSonoElTelefono_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora de la noche del sábado sonó el teléfono")

    @Test fun enUnasPrimerasHorasDeLaTardeDelSabadoAvanceMucho_esContenidoNarrativo() =
        assertNarrativeIntact("en unas primeras horas de la tarde del sábado avancé mucho")

    // ---- Guards bivalentes: ancla vigente, BYTE-IDÉNTICOS (medidos PRE) ----

    @Test fun avisarEnUnaPrimeraHoraDelLunes_sigueAncla() =
        assertAnchor(
            "avisar en una primera hora del lunes",
            LocalDate.of(2026, 8, 24), 9, "avisar en una"
        )

    @Test fun quieroEnUnaPrimeraHoraDelLunes_sigueAncla() =
        assertAnchor(
            "quiero en una primera hora del lunes",
            LocalDate.of(2026, 8, 24), 9, "quiero en una"
        )

    @Test fun enUnaPrimeraHoraDelLunes_sinPredicadoSigueAncla() =
        assertAnchor(
            "en una primera hora del lunes",
            LocalDate.of(2026, 8, 24), 9, "en una"
        )

    @Test fun enUnaPrimeraHoraDelLunesQueViene_sigueAncla() =
        assertAnchor(
            "en una primera hora del lunes que viene",
            LocalDate.of(2026, 8, 24), 9, "en una"
        )

    // ---- Regresiones narrativas ya protegidas (BYTE-IDÉNTICAS) ----

    @Test fun unaPrimeraHoraDelLunesFueRara_regresionC943() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun enPrimeraHoraDelLunesMeQuedeDormido_regresionC946() =
        assertNarrativeIntact("en primera hora del lunes me quedé dormido")

    @Test fun enLaPrimeraHoraDelLunesMeQuedeDormido_regresionC942() =
        assertNarrativeIntact("en la primera hora del lunes me quedé dormido")

    @Test fun enUnaPrimeraHoraDelDiaTrabajeMejor_regresionC947() =
        assertNarrativeIntact("en una primera hora del día trabajé mejor")

    @Test fun avisarAPrimeraHoraDelLunes_sigueAncla() =
        assertAnchor(
            "avisar a primera hora del lunes",
            LocalDate.of(2026, 8, 24), 9, "avisar"
        )

    // ---- Pines byte-idénticos de laterales FUERA (medidos PRE) ----

    @Test fun enUnaPrimeraHoraDeClase_h2EnIndefinidoLateralFueraPin() =
        assertAnchor(
            "en una primera hora de clase me quedé dormido",
            LocalDate.of(2026, 8, 23), 9, "en una de clase me quedé dormido"
        )

    @Test fun unaPrimeraHoraDeClase_h2IndefinidoLateralFueraPin() =
        assertAnchor(
            "una primera hora de clase fue genial",
            LocalDate.of(2026, 8, 23), 9, "una de clase fue genial"
        )
}

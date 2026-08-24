package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.947: narrativa ordinal H3-CANÓNICA (genitivo canónico DENTRO del match:
 * «de la mañana/madrugada/tarde/noche», «del día») con preposición «en» +
 * ARTÍCULO INDEFINIDO al inicio del texto + predicado («en una primera hora
 * del día trabajé mejor» = I worked better in the first hour of the day).
 * Hueco entre c.942 («en» + artículo DEFINIDO) y c.944 (indefinido SIN «en»):
 * lateral medida FUERA en c.945 (registrada por el run remoto) y verificada
 * en este ciclo con sonda efímera `/tmp/probe947/PreProbe.kt` (motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo,
 * base c.946 `d54daeb`): PRE — 4/4 candidatas con DOBLE daño P1 (fecha FALSA
 * [hoy 09:00 o 18:00] Y título mutilado [«en una trabajé mejor», «en unas son
 * duras»… — ordinal + genitivo canónico borrados: contenido del usuario]);
 * 3/3 guards bivalentes ancla correctos (verbo precedente «avisar/quiero en
 * una…», fragmento sin predicado); 5/5 regresiones c.932/c.942/c.944 intactas;
 * pines de laterales FUERA (weekday «en una… del lunes…», H2 «en una primera
 * hora de clase…») medidos byte-idénticos.
 *
 * Doctrina (extensión simétrica de c.944): cuando TODO el prefijo es «en» +
 * artículo INDEFINIDO al inicio del texto (anclado `^…$`) y hay predicado a
 * continuación, el ordinal con genitivo canónico dentro del match es
 * CONTENIDO narrativo: no ancla fecha ni se borra del título. Con verbo o
 * cláusula precedente el prefijo no empieza en «en» y la forma sigue la
 * doctrina ancla (byte-idéntica). La bivalente pura con predicado de comando
 * («en una primera hora del día llamar al banco») queda del lado narrativo,
 * exactamente como ya aceptaron c.932/c.944 para sus determinantes (mismo
 * compromiso doctrinal: determinante al inicio + predicado = contenido).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserEnIndefinidoH3NarrativoTest {

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

    // ---- Capturas: «en» + indefinido al inicio + genitivo canónico DENTRO del match + predicado ----

    @Test fun enUnaPrimeraHoraDelDiaTrabajeMejor_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora del día trabajé mejor")

    @Test fun enUnaPrimeraHoraDeLaMananaTrabajeMejor_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora de la mañana trabajé mejor")

    @Test fun enUnasPrimerasHorasDeLaMananaSonDuras_esContenidoNarrativo() =
        assertNarrativeIntact("en unas primeras horas de la mañana son duras")

    @Test fun enUnaUltimaHoraDelDiaLlegoLaNoticia_esContenidoNarrativo() =
        assertNarrativeIntact("en una última hora del día llegó la noticia")

    // ---- Guards bivalentes: ancla vigente, BYTE-IDÉNTICOS (medidos PRE) ----

    @Test fun avisarEnUnaPrimeraHoraDelDia_sigueAncla() =
        assertAnchor(
            "avisar en una primera hora del día",
            LocalDate.of(2026, 8, 23), 9, "avisar en una"
        )

    @Test fun quieroEnUnaPrimeraHoraDelDia_sigueAncla() =
        assertAnchor(
            "quiero en una primera hora del día",
            LocalDate.of(2026, 8, 23), 9, "quiero en una"
        )

    @Test fun enUnaPrimeraHoraDelDia_sinPredicadoSigueAncla() =
        assertAnchor(
            "en una primera hora del día",
            LocalDate.of(2026, 8, 23), 9, "en una"
        )

    // ---- Regresiones narrativas ya protegidas (BYTE-IDÉNTICAS) ----

    @Test fun unaPrimeraHoraDelDiaTrabajeMejor_regresionC944() =
        assertNarrativeIntact("una primera hora del día trabajé mejor")

    @Test fun enLaPrimeraHoraDelDiaTrabajeMejor_regresionC942() =
        assertNarrativeIntact("en la primera hora del día trabajé mejor")

    @Test fun lasPrimerasHorasDeLaMananaSonLasMejores_regresionC932() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun avisarAPrimeraHoraDelDia_sigueAncla() =
        assertAnchor(
            "avisar a primera hora del día",
            LocalDate.of(2026, 8, 23), 9, "avisar"
        )

    @Test fun avisarALaUltimaHora_sigueAncla() =
        assertAnchor(
            "avisar a la última hora",
            LocalDate.of(2026, 8, 23), 18, "avisar"
        )

    // ---- Pines byte-idénticos de laterales FUERA (medidos PRE) ----

    // c.948: la lateral weekday con «en»+indefinido quedó RESUELTA (doctrina
    // simétrica c.943 con «en» prefijado). Re-pin legítimo MÁS estricto del
    // pin c.947 (precedente c.925…c.947): ahora aserta contenido narrativo
    // íntegro. Cobertura canónica en
    // NaturalTaskParserWeekdayEnIndefinidoNarrativoTest.
    @Test fun enUnaPrimeraHoraDelLunes_weekdayEnIndefinidoLateralResueltaC948() =
        assertNarrativeIntact("en una primera hora del lunes fue rara")

    // c.951: la lateral H2 con «en»+indefinido quedó RESUELTA (doctrina
    // simétrica a la rama H2 c.937 con el «en»+indefinido de c.948). Re-pin
    // legítimo MÁS estricto (precedente c.925…c.948): ahora aserta contenido
    // narrativo íntegro. Cobertura canónica en
    // NaturalTaskParserH2IndefinidoNarrativoTest.
    @Test fun enUnaPrimeraHoraDeClase_h2EnIndefinidoLateralResueltaC951() =
        assertNarrativeIntact("en una primera hora de clase me quedé dormido")
}

package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.951: narrativa ordinal H2 (genitivo de CONTENIDO tras el match: «de
 * clase», «del partido», «de la película») con ARTÍCULO INDEFINIDO al inicio
 * del texto («una primera hora de clase fue genial») o «en» + indefinido al
 * inicio («en una primera hora de clase me quedé dormido») — lateral medida
 * FUERA en c.943/c.944/c.945/c.947/c.948 (pins byte-idénticos
 * [NaturalTaskParserIndefinidoH3NarrativoTest.unaPrimeraHoraDeClase_h2IndefinidoLateralFueraPin]
 * y hermanos — re-pin legítimo MÁS estricto en este ciclo, precedente
 * c.925…c.948) y verificada con sonda efímera `/tmp/probe951/PreProbe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, base c.949 `27c0c5e`): PRE — 9/9 candidatas con
 * DOBLE daño P1 (fecha FALSA [hoy 09:00/18:00, y lun 2026-08-24 09:00 en las
 * compuestas con weekday] Y título mutilado [«una de clase fue genial»,
 * «en una de clase me quedé dormido»… — el ordinal borrado: contenido del
 * usuario]); 5/5 guards bivalentes ancla correctos (verbo precedente
 * «quiero/prefiero una…», «avisar en una…», conector «avisar a primera…»,
 * fragmento con genitivo canónico «una última hora de la noche»); 5/5
 * regresiones c.930/c.931/c.943/c.947/c.948 intactas; 2/2 laterales FUERA
 * («en» SIN artículo + H2) medidas.
 *
 * Doctrina (extensión simétrica de la rama H2 c.937, como c.943/c.944/c.948
 * lo fueron de las ramas weekday/H3): cuando TODO el prefijo es el artículo
 * INDEFINIDO al inicio del texto ([NaturalTaskParser] val
 * `ordinalHoraNarrativeIndefinitePrefix`, anclado `^…$`) o «en» + indefinido
 * (`ordinalHoraNarrativeEnIndefinitePrefix`) y hay genitivo de contenido tras
 * el match, el ordinal es CONTENIDO narrativo: no ancla fecha ni se borra
 * del título. Con verbo/nombre precedente («quiero una…», «avisar en una…»)
 * el prefijo no empieza en el indefinido y la forma sigue la doctrina ancla
 * (byte-idéntica). Como la H2 con artículo definido (c.930/c.937), no se
 * exige predicado: el fragmento nominal «una primera hora de clase» tampoco
 * es ancla (evidencia de sujeto idéntica; PRE nacía con fecha falsa y título
 * mutilado — mejora estricta medida). La lateral «en» SIN artículo + H2
 * («en primera hora de clase me quedé dormido») quedó RESUELTA en c.952 —
 * re-pin legítimo más estricto en este test. Determinista (regex), cero
 * random, cero IA fingida, cero UI.
 */
class NaturalTaskParserH2IndefinidoNarrativoTest {

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

    // ---- Capturas: H2 + indefinido al inicio (± «en», ± weekday compuesto, ± predicado) ----

    @Test fun unaPrimeraHoraDeClaseFueGenial_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de clase fue genial")

    @Test fun enUnaPrimeraHoraDeClaseMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora de clase me quedé dormido")

    @Test fun unaUltimaHoraDelPartidoFueEmocionante_esContenidoNarrativo() =
        assertNarrativeIntact("una última hora del partido fue emocionante")

    @Test fun unasPrimerasHorasDeTrabajoFueronDuras_esContenidoNarrativo() =
        assertNarrativeIntact("unas primeras horas de trabajo fueron duras")

    @Test fun enUnaUltimaHoraDeLaReunionLlegoElAcuerdo_esContenidoNarrativo() =
        assertNarrativeIntact("en una última hora de la reunión llegó el acuerdo")

    @Test fun unPrimerMomentoDeLaPeliculaFueClave_esContenidoNarrativo() =
        assertNarrativeIntact("un primer momento de la película fue clave")

    @Test fun unaPrimeraHoraDeClaseDelLunesFueAburrida_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de clase del lunes fue aburrida")

    @Test fun enUnaPrimeraHoraDeClaseDelLunesMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en una primera hora de clase del lunes me quedé dormido")

    @Test fun unaPrimeraHoraDeClase_fragmentoNominalSinPredicado_esContenidoNarrativo() =
        assertNarrativeIntact("una primera hora de clase")

    // ---- Guards bivalentes: la forma sigue ANCLA byte-idéntica ----

    @Test fun quieroUnaPrimeraHoraDeClaseLibre_verboPrecedenteSigueAncla() =
        assertAnchor(
            "quiero una primera hora de clase libre",
            LocalDate.of(2026, 8, 23), 9, "quiero una de clase libre"
        )

    @Test fun prefieroUnaUltimaHoraDelPartidoParaLlegar_verboPrecedenteSigueAncla() =
        assertAnchor(
            "prefiero una última hora del partido para llegar",
            LocalDate.of(2026, 8, 23), 18, "prefiero una del partido para llegar"
        )

    @Test fun avisarEnUnaPrimeraHoraDeClase_verboPrecedenteSigueAncla() =
        assertAnchor(
            "avisar en una primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar en una de clase"
        )

    @Test fun avisarAPrimeraHoraDeClase_conectorASigueAncla() =
        assertAnchor(
            "avisar a primera hora de clase",
            LocalDate.of(2026, 8, 23), 9, "avisar de clase"
        )

    @Test fun unaUltimaHoraDeLaNoche_fragmentoConGenitivoCanonicoSigueAncla() =
        assertAnchor(
            "una última hora de la noche",
            LocalDate.of(2026, 8, 23), 21, "una"
        )

    // ---- Regresiones (conducta ya protegida por c.930/c.931/c.943/c.947/c.948) ----

    @Test fun laPrimeraHoraDeClaseFueAburrida_regresionC930() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")

    @Test fun avisarALaPrimeraHoraDeClase_regresionC931() =
        assertNarrativeIntact("avisar a la primera hora de clase")

    @Test fun unaPrimeraHoraDelLunesFueRara_regresionC943() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun enUnaPrimeraHoraDelLunesFueRara_regresionC948() =
        assertNarrativeIntact("en una primera hora del lunes fue rara")

    @Test fun enUnaPrimeraHoraDelDiaTrabajeMejor_regresionC947() =
        assertNarrativeIntact("en una primera hora del día trabajé mejor")

    // ---- Laterales medidas FUERA en c.943…c.951; RESUELTAS en c.952 (re-pin
    // legítimo MÁS estricto: ahora narrativa intacta, precedente c.925…c.951) ----

    @Test fun enPrimeraHoraDeClase_h2EnSinArticuloLateralResueltaC952() =
        assertNarrativeIntact("en primera hora de clase me quedé dormido")

    @Test fun enUltimaHoraDelPartido_h2EnSinArticuloLateralResueltaC952() =
        assertNarrativeIntact("en última hora del partido llegó el gol")
}

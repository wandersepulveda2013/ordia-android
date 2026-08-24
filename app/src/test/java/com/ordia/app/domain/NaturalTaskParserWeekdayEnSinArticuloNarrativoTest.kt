package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.946: narrativa ordinal con preposición «en» SIN artículo AL INICIO del
 * texto y weekday genitivo (directo o tras genitivo INTERIOR de parte del
 * día) + predicado («en primera hora del lunes me quedé dormido» = during
 * the first hour of Monday I fell asleep). Lateral medida FUERA en c.942 y
 * c.944, verificada en este ciclo con sonda efímera `/tmp/probe945/PreProbe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, base c.944 `7ba3ae6`): PRE — 8/8 candidatas con
 * DOBLE daño P1 (fecha FALSA del weekday [«lun 09:00», «vie 18:00», «sáb
 * 18:00», «dom 09:00», «mar 09:00» bivalente, «sáb 21:00» interior noche,
 * «sáb 15:00» interior tarde] y título mutilado [«en me quedé dormido»,
 * «en trabajé mejor»… — ordinal + genitivo borrados: contenido del
 * usuario]); 7/7 guards ancla correctos (verbo o nombre precedente, fragmento
 * sin predicado, «que viene», cláusula precedente, conector «a»); 5/5
 * regresiones intactas.
 *
 * Doctrina (extensión simétrica de la «en»+artículo c.942): cuando el texto
 * ARRANCA con «en» (sin artículo) + ordinal narrativo y el weekday genitivo
 * tiene predicado a continuación (sufijo no-blanco, misma convención de
 * c.939/c.942), la cadena completa es CONTENIDO narrativo: no ancla fecha ni
 * se borra del título. «en» al inicio es inequívoco de circunstancial
 * narrativo; con verbo/nombre/cláusula precedente («avisar en…», «reunión
 * en…», «quiero en…», «creo que en…») el prefijo no empieza en «en» y la
 * forma sigue la doctrina ancla (byte-idéntica). El fragmento sin predicado
 * y el weekday con modificador («que viene») siguen ancla; las colas
 * no-copulativas («tengo clase») cuentan como predicado por simetría
 * c.939/c.941/c.942. Determinista (regex), cero random, cero IA fingida,
 * cero UI.
 *
 * Laterales medidas FUERA (registradas, UNA por ciclo): «avisar la última
 * hora» (objeto sin conector → residuo 'avisar la', heredada); «en primera
 * hora de la mañana llamé al banco» / «…llamar al banco» (H3 sin
 * determinante: pasado narrativo y comando imperativo son indistinguibles
 * por regex — bivalente real, pin conservador byte-idéntico).
 */
class NaturalTaskParserWeekdayEnSinArticuloNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertDueAt(text: String, date: LocalDate, hour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(date, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(hour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    // ---- Capturas: «en» SIN artículo al inicio + weekday genitivo + predicado ----

    @Test fun enPrimeraHoraDelLunesMeQuedeDormido_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora del lunes me quedé dormido")

    @Test fun enPrimerasHorasDelLunesTrabajeMejor_esContenidoNarrativo() =
        assertNarrativeIntact("en primeras horas del lunes trabajé mejor")

    @Test fun enUltimaHoraDelViernesCerreElTrato_esContenidoNarrativo() =
        assertNarrativeIntact("en última hora del viernes cerré el trato")

    @Test fun enUltimasHorasDelSabadoLlegoLaNoticia_esContenidoNarrativo() =
        assertNarrativeIntact("en últimas horas del sábado llegó la noticia")

    @Test fun enPrimerMomentoDelDomingoSupeQueEraElla_esContenidoNarrativo() =
        assertNarrativeIntact("en primer momento del domingo supe que era ella")

    // Bivalente protegida por simetría c.939/c.941/c.942: la cola no-copulativa
    // «tengo clase» cuenta como predicado (sujeto narrativo del lunes).
    @Test fun enPrimeraHoraDelMartesTengoClase_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora del martes tengo clase")

    // Genitivo INTERIOR de parte del día + weekday (simetría c.941/c.942).
    @Test fun enPrimeraHoraDeLaNocheDelSabadoSonoElTelefono_esContenidoNarrativo() =
        assertNarrativeIntact("en primera hora de la noche del sábado sonó el teléfono")

    @Test fun enPrimerasHorasDeLaTardeDelSabadoAvanceMucho_esContenidoNarrativo() =
        assertNarrativeIntact("en primeras horas de la tarde del sábado avancé mucho")

    // ---- Guards bivalentes/ancla: BYTE-IDÉNTICOS a PRE (medidos en la sonda) ----

    @Test fun avisarEnPrimeraHoraDelLunes_pinAncla() =
        assertDueAt("avisar en primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar en")

    @Test fun reunionEnPrimeraHoraDelLunes_pinAncla() =
        assertDueAt("reunión en primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "reunión en")

    @Test fun enPrimeraHoraDelLunesSinPredicado_pinAncla() =
        assertDueAt("en primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "en")

    @Test fun enPrimeraHoraDelLunesQueViene_pinAncla() =
        assertDueAt("en primera hora del lunes que viene", LocalDate.of(2026, 8, 24), 9, "en")

    @Test fun quieroEnPrimeraHoraDelLunes_pinAncla() =
        assertDueAt("quiero en primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "quiero en")

    @Test fun creoQueEnPrimeraHoraDelLunes_pinAncla() =
        assertDueAt("creo que en primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "creo que en")

    @Test fun avisarAPrimeraHoraDelLunes_pinAncla() =
        assertDueAt("avisar a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar")

    // ---- Regresiones de doctrinas vigentes: conducta intacta ----

    @Test fun regresionC942_enLaPrimeraHoraDelLunes_sigueNarrativa() =
        assertNarrativeIntact("en la primera hora del lunes me quedé dormido")

    @Test fun regresionC943_unaPrimeraHoraDelLunes_sigueNarrativa() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun regresionC944_unasPrimerasHorasDeLaManana_sigueNarrativa() =
        assertNarrativeIntact("unas primeras horas de la mañana son duras")

    @Test fun regresionC936_h3ConWeekday_sigueNarrativa() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun regresionAncla_avisarALaUltimaHora_sigueAncla() =
        assertDueAt("avisar a la última hora", LocalDate.of(2026, 8, 23), 18, "avisar")

    // ---- Pines de laterales FUERA: BYTE-IDÉNTICOS (conducta vigente) ----
    // [c.965: la lateral «avisar la última hora» se resuelve — re-pin abajo.
    //  Las laterales «en primera hora…» (sin artículo) siguen vigentes.]

    @Test fun avisarLaUltimaHora_pinLateralFuera() =
        // Re-pin legítimo c.965: la lateral se resuelve — el artículo huérfano
        // «la» se consume con la ocurrencia ancla (eraseOrdinalHoraToken).
        assertDueAt("avisar la última hora", LocalDate.of(2026, 8, 23), 18, "avisar")

    // H3 sin determinante con «en»: bivalente real (pasado narrativo vs
    // comando imperativo indistinguibles por regex) — pin conservador.
    @Test fun enPrimeraHoraDeLaMananaLlameAlBanco_pinLateralFuera() =
        assertDueAt("en primera hora de la mañana llamé al banco", LocalDate.of(2026, 8, 23), 9, "en llamé al banco")

    @Test fun enPrimeraHoraDeLaMananaLlamarAlBanco_pinLateralFuera() =
        assertDueAt("en primera hora de la mañana llamar al banco", LocalDate.of(2026, 8, 23), 9, "en llamar al banco")
}

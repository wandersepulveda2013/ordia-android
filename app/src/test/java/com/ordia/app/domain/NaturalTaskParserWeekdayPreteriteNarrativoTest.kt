package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.950 — narrativa con «el <weekday>» + predicado en PRETÉRITO inequívoco:
 * la cadena es un enunciado narrativo (algo que YA pasó ese día), no un
 * compromiso agendable. Doctrina simétrica al clúster ordinal narrativo
 * (c.936…c.948): sin este guard el parser anclaba el weekday a su PRÓXIMA
 * ocurrencia futura y mutilaba el título («el lunes llegó el paquete» →
 * due=lunes futuro, título 'llegó el paquete') — doble daño (fecha falsa +
 * contenido perdido). Con el fix: due=null y título íntegro.
 *
 * Evidencia (lista cerrada y conservadora): el predicado tras el match debe
 * empezar con verbo en pretérito perfecto simple / copulativo pretérito
 * INEQUÍVOCO (un comando jamás empieza en pretérito), con pronombre
 * reflexivo/ácito opcional. FUERA (laterales medidas, pins byte-idénticos):
 *  - modificador de dirección futura («el lunes que viene llegó el paquete»
 *    — contradictorio, conservador: el futuro explícito gana);
 *  - genitivo «del/de <weekday>» («la reunión del lunes fue productiva»);
 *  - parte del día intercalada («el lunes en la mañana llegó el paquete»);
 *  - otras anclas de día con pretérito («ayer compré leche», «hoy llegó…»);
 *  - formas ambiguas pretérito/presente (1ª plural «salimos/comimos»).
 */
class NaturalTaskParserWeekdayPreteriteNarrativoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val nowMillis = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, nowMillis, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull("«$text» no debe producir fecha (es narrativa, no compromiso)", r.dueAt)
        assertEquals("el título debe conservar la cadena narrativa íntegra", text, r.title)
    }

    private fun assertAnchor(text: String, date: LocalDate, hour: Int, title: String) {
        val r = parse(text)
        assertEquals(date, r.dueAt?.let { DateRules.toLocalDate(it, zone) })
        assertEquals(LocalTime.of(hour, 0), r.dueAt?.let { DateRules.toLocalTime(it, zone) })
        assertEquals(title, r.title)
    }

    // ---- Capturas narrativas: «el <weekday>» + pretérito ----

    @Test fun elLunesLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("el lunes llegó el paquete")

    @Test fun elMartesSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("el martes sonó la alarma")

    @Test fun elViernesFueFeriado_esContenidoNarrativo() =
        assertNarrativeIntact("el viernes fue feriado")

    @Test fun elMiercolesMeQuedeDormido_enClasePronominalEsContenidoNarrativo() =
        assertNarrativeIntact("el miércoles me quedé dormido en clase")

    @Test fun elSabadoLlegoElPedido_esContenidoNarrativo() =
        assertNarrativeIntact("el sábado llegó el pedido")

    @Test fun elDomingoSeRompioLaTuberia_pronominalEsContenidoNarrativo() =
        assertNarrativeIntact("el domingo se rompió la tubería")

    // ---- Guards de ancla (comando/presente/fragmento: comportamiento vigente) ----

    @Test fun elLunesTengoReunion_presenteSigueAncla() =
        assertAnchor("el lunes tengo reunión", LocalDate.of(2026, 8, 24), 9, "tengo reunión")

    @Test fun elLunesLlegaElPaquete_presenteSigueAncla() =
        assertAnchor("el lunes llega el paquete", LocalDate.of(2026, 8, 24), 9, "llega el paquete")

    @Test fun reunionElLunes_comandoSigueAncla() =
        assertAnchor("reunión el lunes", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun elLunes_fragmentoSinPredicadoSigueAncla() =
        assertAnchor("el lunes", LocalDate.of(2026, 8, 24), 9, "el lunes")

    // ---- Pines byte-idénticos de laterales FUERA (medidos PRE c.950) ----

    @Test fun elLunesQueVieneLlegoElPaquete_modificadorFuturoExplicitoSigueAnclaPin() =
        assertAnchor(
            "el lunes que viene llegó el paquete",
            LocalDate.of(2026, 8, 24), 9, "llegó el paquete"
        )

    @Test fun laReunionDelLunesFueProductiva_genitivoDelWeekdayLateralFueraPin() =
        assertAnchor(
            "la reunión del lunes fue productiva",
            LocalDate.of(2026, 8, 24), 9, "la reunión fue productiva"
        )

    @Test fun elLunesEnLaMananaLlegoElPaquete_parteDelDiaIntercaladaLateralFueraPin() =
        assertAnchor(
            "el lunes en la mañana llegó el paquete",
            LocalDate.of(2026, 8, 24), 9, "llegó el paquete"
        )

    @Test fun ayerCompreLeche_ayerPreteritoLateralFueraPin() =
        assertAnchor("ayer compré leche", LocalDate.of(2026, 8, 22), 9, "compré leche")

    @Test fun hoyLlegoElPaquete_hoyPreteritoLateralFueraPin() =
        assertAnchor("hoy llegó el paquete", LocalDate.of(2026, 8, 23), 9, "llegó el paquete")

    // ---- Regresiones de doctrinas vigentes (no deben cambiar) ----

    @Test fun enLaPrimeraHoraDelLunes_ordinalNarrativaC942Intacta() =
        assertNarrativeIntact("en la primera hora del lunes me quedé dormido")

    @Test fun lasPrimerasHorasDeLaMananaDelLunes_ordinalNarrativaC936Intacta() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun unaPrimeraHoraDelLunes_ordinalNarrativaC943Intacta() =
        assertNarrativeIntact("una primera hora del lunes fue rara")

    @Test fun avisarAPrimeraHoraDelLunes_anclaOrdinalVigente() =
        assertAnchor("avisar a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar")

    @Test fun mudanzaElFinDeSemanaQueViene_findeSigueAncla() =
        assertAnchor("mudanza el fin de semana que viene", LocalDate.of(2026, 8, 29), 9, "mudanza")

    @Test fun turnoElLunesEnLaManana_comandoParteDelDiaSigueAncla() =
        assertAnchor("turno el lunes en la mañana", LocalDate.of(2026, 8, 24), 9, "turno")
}

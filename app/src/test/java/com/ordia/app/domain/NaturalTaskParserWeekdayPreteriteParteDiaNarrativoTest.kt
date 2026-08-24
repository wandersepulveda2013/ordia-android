package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.954 — extensión de c.950: narrativa en pretérito con parte del día
 * INTERCALADA entre el weekday y el predicado («el lunes en la mañana llegó…»).
 * c.950 la midió como lateral FUERA (pin byte-idéntico) y la registró como
 * siguiente candidata; esta extensión admite «en la X»/«por la X»
 * (mañana/tarde/noche/madrugada) entre el weekday y el pretérito, conservando
 * título y due null simétricos (sin divergir fecha/título).
 *
 * FUERA a propósito (pins byte-idénticos PRE-medidos en c.954):
 *  - fragmento sin predicado («el lunes en la mañana» — sigue ancla);
 *  - modificador futuro explícito («el lunes que viene en la mañana llegó…»);
 *  - sin artículo/demostrativo inicial («lunes en la mañana llegó…»);
 *  - genitivo de weekday («la reunión del lunes en la mañana fue…»);
 *  - otros conectores de parte del día («a la mañana», «de la mañana»).
 */
class NaturalTaskParserWeekdayPreteriteParteDiaNarrativoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val nowMillis = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, nowMillis, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull("«$text» no debe producir fecha (narrativa, no compromiso)", r.dueAt)
        assertEquals("el título debe conservar la cadena narrativa íntegra", text, r.title)
    }

    private fun assertAnchor(text: String, date: LocalDate, hour: Int, title: String) {
        val r = parse(text)
        assertEquals(date, r.dueAt?.let { DateRules.toLocalDate(it, zone) })
        assertEquals(LocalTime.of(hour, 0), r.dueAt?.let { DateRules.toLocalTime(it, zone) })
        assertEquals(title, r.title)
    }

    // ---- Capturas narrativas: «el/este <weekday> (en|por) la <parte> … pretérito» ----

    @Test fun elLunesEnLaMananaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("el lunes en la mañana llegó el paquete")

    @Test fun elMartesPorLaTardeLlegoLaNoticia_esContenidoNarrativo() =
        assertNarrativeIntact("el martes por la tarde llegó la noticia")

    @Test fun elViernesEnLaNocheSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("el viernes en la noche sonó la alarma")

    @Test fun elSabadoPorLaNocheSeRompioLaTuberia_esContenidoNarrativo() =
        assertNarrativeIntact("el sábado por la noche se rompió la tubería")

    @Test fun elLunesPorLaMananaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("el lunes por la mañana llegó el paquete")

    @Test fun elLunesEnLaTardeLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("el lunes en la tarde llegó el paquete")

    @Test fun elLunesEnLaMadrugadaSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("el lunes en la madrugada sonó la alarma")

    @Test fun esteLunesEnLaMananaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("este lunes en la mañana llegó el paquete")

    // ---- Guards de ancla (comando/presente/fragmento/modificador: medidos PRE) ----

    @Test fun turnoElLunesEnLaManana_comandoSigueAncla() =
        assertAnchor("turno el lunes en la mañana", LocalDate.of(2026, 8, 24), 9, "turno")

    @Test fun elLunesEnLaMananaTengoReunion_presenteSigueAncla() =
        assertAnchor("el lunes en la mañana tengo reunión", LocalDate.of(2026, 8, 24), 9, "tengo reunión")

    @Test fun elLunesPorLaTardeLlegaElPaquete_presenteSigueAncla() =
        assertAnchor("el lunes por la tarde llega el paquete", LocalDate.of(2026, 8, 24), 15, "llega el paquete")

    @Test fun elLunesEnLaManana_fragmentoSinPredicadoSigueAnclaPin() =
        assertAnchor("el lunes en la mañana", LocalDate.of(2026, 8, 24), 9, "el lunes en la mañana")

    // ---- Pines byte-idénticos de FUERA medidos PRE ----

    @Test fun elLunesQueVieneEnLaMananaLlegoElPaquete_modificadorFuturoSigueAnclaPin() =
        assertAnchor("el lunes que viene en la mañana llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun lunesEnLaMananaLlegoElPaquete_sinArticuloSigueAnclaPin() =
        assertAnchor("lunes en la mañana llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun laReunionDelLunesEnLaMananaFueProductiva_genitivoSigueAnclaPin() =
        assertAnchor("la reunión del lunes en la mañana fue productiva", LocalDate.of(2026, 8, 24), 9, "la reunión fue productiva")

    @Test fun elLunesALaMananaLlegoElPaquete_conectorALaSigueAnclaPin() =
        assertAnchor("el lunes a la mañana llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun elLunesDeLaMananaLlegoElPaquete_conectorDeLaSigueAnclaPin() =
        assertAnchor("el lunes de la mañana llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    // ---- Regresiones de doctrinas vigentes (c.950/c.936) ----

    @Test fun elLunesLlegoElPaquete_directoC950Intacto() =
        assertNarrativeIntact("el lunes llegó el paquete")

    @Test fun lasPrimerasHorasDeLaMananaDelLunes_ordinalNarrativaC936Intacta() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun avisarAPrimeraHoraDelLunes_anclaOrdinalVigente() =
        assertAnchor("avisar a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar")
}

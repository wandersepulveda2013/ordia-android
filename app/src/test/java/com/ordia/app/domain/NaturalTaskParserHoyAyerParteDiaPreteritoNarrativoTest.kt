package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.955: «hoy/ayer en/por la <parte del día> …» + predicado en PRETÉRITO =
 * CADENA NARRATIVA (algo que ya ocurrió, no un compromiso). Antes el parser
 * fabricaba una hora canónica de parte del día (09:00/15:00/21:00) y además
 * borraba la marca del título (doble daño: compromiso vencido falso — hoy a
 * las 09:00 si son las 12:00 — + contenido mutilado).
 *
 * Extensión de la doctrina c.950 (weekday en pretérito) a la candidata medida
 * allí: el guard es simétrico y reutiliza la misma lista cerrada de pretéritos
 * inequívocos de [weekdayPreteriteNarrativeSuffix]. Los laterales pin
 * byte-idénticos «hoy compré leche»/«ayer compré leche» (doctrina absoluta
 * vigente: ancla) y guardias de comando/presente/fragmento quedan INTACTOS
 * (ver [NaturalTaskParserWeekdayPreteriteNarrativoTest]).
 */
class NaturalTaskParserHoyAyerParteDiaPreteritoNarrativoTest {

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

    // ---- Capturas narrativas: «hoy/ayer» + conector + parte del día + pretérito ----

    @Test fun hoyEnLaMananaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("hoy en la mañana llegó el paquete")

    @Test fun hoyEnLaTardeSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("hoy en la tarde sonó la alarma")

    @Test fun hoyEnLaNocheSeRompioLaTuberia_esContenidoNarrativo() =
        assertNarrativeIntact("hoy en la noche se rompió la tubería")

    @Test fun ayerEnLaMananaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ayer en la mañana llegó el paquete")

    @Test fun ayerEnLaTardeCerróElBanco_esContenidoNarrativo() =
        assertNarrativeIntact("ayer en la tarde cerró el banco")

    @Test fun ayerEnLaNocheSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("ayer en la noche sonó la alarma")

    @Test fun hoyPorLaMananaLlegoElPaquete_conectorPorEsContenidoNarrativo() =
        assertNarrativeIntact("hoy por la mañana llegó el paquete")

    @Test fun ayerPorLaMananaMeQuedeDormido_pronominalEsContenidoNarrativo() =
        assertNarrativeIntact("ayer por la mañana me quedé dormido")

    // ---- Guards de ancla (comando/presente/fragmento: comportamiento vigente) ----

    @Test fun hoyEnLaMananaTengoReunion_presenteSigueAncla() =
        assertAnchor("hoy en la mañana tengo reunión", LocalDate.of(2026, 8, 23), 9, "tengo reunión")

    @Test fun hoyEnLaTardeLlegaElPaquete_presenteSigueAncla() =
        assertAnchor("hoy en la tarde llega el paquete", LocalDate.of(2026, 8, 23), 15, "llega el paquete")

    @Test fun hoyEnLaManana_fragmentoSinPredicadoSigueAncla() =
        assertAnchor("hoy en la mañana", LocalDate.of(2026, 8, 23), 9, "hoy en la mañana")

    @Test fun llamarAlBancoHoyEnLaManana_comandoSigueAncla() =
        assertAnchor("llamar al banco hoy en la mañana", LocalDate.of(2026, 8, 23), 9, "llamar al banco")

    @Test fun hoyEnLaMananaSalimosACorrer_ambiguo1aPluralSigueAncla() =
        assertAnchor("hoy en la mañana salimos a correr", LocalDate.of(2026, 8, 23), 9, "salimos a correr")

    // ---- Pines byte-idénticos (doctrina absoluta vigente; NO cambian) ----

    @Test fun hoyLlegoElPaquete_c950DoctrinaAbsolutaPin() =
        assertAnchor("hoy llegó el paquete", LocalDate.of(2026, 8, 23), 9, "llegó el paquete")

    @Test fun ayerCompreLeche_c950DoctrinaAbsolutaPin() =
        assertAnchor("ayer compré leche", LocalDate.of(2026, 8, 22), 9, "compré leche")

    // c.954 (run hermano integrado): el weekday + parte del día intercalada en
    // pretérito también es narrativa (re-pin legítimo más estricto, precedente
    // c.925..c.954 — ver AI_AUTONOMY).
    @Test fun elLunesEnLaMananaLlegoElPaquete_c954IntegradoWeekdayPin() =
        assertNarrativeIntact("el lunes en la mañana llegó el paquete")

    @Test fun estaMananaLlegoElPaquete_partOfDayAbsolutaPin() =
        assertAnchor("esta mañana llegó el paquete", LocalDate.of(2026, 8, 23), 9, "llegó el paquete")

    @Test fun mananaEnLaMananaLlegaElPaquete_futuroExplicitoPin() =
        assertAnchor("mañana en la mañana llega el paquete", LocalDate.of(2026, 8, 24), 9, "llega el paquete")

    @Test fun hoyMismoLlegoElPaquete_qualificadorAnclaPin() =
        assertAnchor("hoy mismo llegó el paquete", LocalDate.of(2026, 8, 23), 9, "llegó el paquete")

    // ---- Regresión explícita de hora (doctrina c.950: la hora explícita ancla,
    //      la marca narrativa se protege simétricamente) ----

    @Test fun hoyEnLaMananaLlegoElPaqueteALasCinco_horaExplicitaAnclaTituloProtegido() =
        assertAnchor("hoy en la mañana llegó el paquete a las 5", LocalDate.of(2026, 8, 23), 5, "hoy en la mañana llegó el paquete")
}

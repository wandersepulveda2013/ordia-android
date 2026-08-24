package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * c.958: narrativa ordinal H3 SIN determinante con pretérito — «primera hora
 * de la mañana llamé al banco» (prefijo en blanco + genitivo canónico dentro
 * del match + predicado en pretérito inequívoco). Hermano de c.952 («en» sin
 * artículo + H2) y c.956 (H2 en blanco): el ordinal «bare» con genitivo de
 * ANCLA es bivalente — con infinitivo/presente («llamar al banco») el tiempo
 * canónico (09:00/18:00) es el sentido y el guard conservador lo mantiene
 * ANCLA byte-idéntico; el pretérito inequívoco lo convierte en SUJETO
 * narrativo y el fragmento queda protegido (fecha null, título íntegro).
 * La supresión de la parte-del-día gobernada y del weekday genitivo
 * («…de la mañana del lunes llamé…») fluye de ordinalHoraNarrativeRanges /
 * ordinalHoraNarrativeWeekdayRanges. Laterales FUERA re-pinneadas: «en» H3
 * sin determinante («en primera hora de la mañana llamé…») y weekday-bare
 * («primera hora del lunes me quedé dormido») siguen ANCLA.
 */
class NaturalTaskParserH3SinDeterminanteNarrativoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val dt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    private val sunday1200 = LocalDateTime.of(2026, 8, 23, 12, 0)

    private fun parse(text: String, now: LocalDateTime = sunday1200): ParsedTaskInput =
        NaturalTaskParser.parse(text, now.atZone(zone).toInstant().toEpochMilli(), zone)

    private fun assertNarrative(input: String) {
        val r = parse(input)
        assertEquals("título conserva el enunciado íntegro (narrativa)", input, r.title)
        assertNull("sin fecha falsa (narrativa)", r.dueAt)
    }

    private fun assertAnchor(input: String, expectedTitle: String, expectedDue: String) {
        val r = parse(input)
        assertEquals("ancla conservadora (guard/pin)", expectedTitle, r.title)
        assertEquals("dueAt", expectedDue, dt.format(Instant.ofEpochMilli(r.dueAt!!)))
    }

    // ——— Capturas narrativas (prefijo en blanco + pretérito) —————

    @Test
    fun primeraHoraDeLaMananaLlamoAlBanco_narrativa() =
        assertNarrative("primera hora de la mañana llamé al banco")

    @Test
    fun ultimaHoraDeLaTardeMeQuedeDormido_narrativa() =
        assertNarrative("última hora de la tarde me quedé dormido")

    @Test
    fun primerasHorasDeLaMadrugadaSonoLaAlarma_plural() =
        assertNarrative("primeras horas de la madrugada sonó la alarma")

    @Test
    fun ultimasHorasDeLaNocheLlegoLaNoticia_plural() =
        assertNarrative("últimas horas de la noche llegó la noticia")

    @Test
    fun primerasHorasDeLaMananaLlegoElPaquete_mananaSinTilde() =
        assertNarrative("primeras horas de la manana llegó el paquete")

    @Test
    fun primerMomentoDelDiaFueMagico_varianteMomento() =
        assertNarrative("primer momento del día fue mágico")

    @Test
    fun primeraHoraDeLaMananaDelLunesLlamoAlBanco_compositeWeekdayGenitivo() =
        assertNarrative("primera hora de la mañana del lunes llamé al banco")

    // ——— Guards bivalentes (siguen ANCLA byte-idénticos) ———

    @Test
    fun primeraHoraDeLaMananaLlamarAlBanco_infinitivoSigueAncla() =
        assertAnchor("primera hora de la mañana llamar al banco", "llamar al banco", "2026-08-23 09:00")

    @Test
    fun primeraHoraDeLaMananaLlegaElPaquete_presenteSigueAncla() =
        assertAnchor("primera hora de la mañana llega el paquete", "llega el paquete", "2026-08-23 09:00")

    @Test
    fun avisarPrimeraHoraDeLaManana_verboPrecedenteSigueAncla() =
        assertAnchor("avisar primera hora de la mañana", "avisar", "2026-08-23 09:00")

    // ——— Pins FUERA (laterales registradas, siguen ANCLA) ———

    @Test
    fun primeraHoraDelLunesMeQuedeDormido_weekdayBareSigueAncla() =
        assertAnchor("primera hora del lunes me quedé dormido", "me quedé dormido", "2026-08-24 09:00")

    @Test
    fun enPrimeraHoraDeLaMananaLlamoAlBanco_enSinDeterminanteH3SigueAncla() =
        assertAnchor("en primera hora de la mañana llamé al banco", "en llamé al banco", "2026-08-23 09:00")

    // ——— Regresiones H3 con determinante (doctrina vigente c.932/c.942) ———

    @Test
    fun lasPrimerasHorasDeLaMananaSonLasMejores_regresionH3Articulo() =
        assertNarrative("las primeras horas de la mañana son las mejores")

    @Test
    fun enLasPrimerasHorasDeLaMananaTrabaje_regresionH3EnArticulo() =
        assertNarrative("en las primeras horas de la mañana trabajé")
}

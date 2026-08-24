package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.1006 — lateral c.949 (registrada, medida PRE con sonda efímera
 * `/tmp/probe1006/WeekdayBareProbe.kt`, 8/8 doble daño): narrativa en pretérito
 * con weekday DESNUDO (sin artículo: «lunes llegó el paquete», «viernes sonó
 * la alarma»). Antes el guard [weekdayOccurrenceIsPreteriteNarrative] (c.950)
 * exigía «el »/«este » (N1) y la forma desnuda caía al ancla: la narrativa se
 * agendaba al PRÓXIMO <weekday> y el título perdía el weekday (doble daño:
 * compromiso futuro falso + contenido mutilado — la misma clase de c.950).
 *
 * Fix mínimo de UN punto: N1 admite el weekday desnudo SOLO para el pretérito
 * INMEDIATO (el mismo [weekdayPreteriteNarrativeSuffix] inequívoco de c.950;
 * un encargo jamás abre su predicado en pretérito: «lunes llamar…»/«lunes
 * tengo…» siguen ancla). Los genitivos «del/de <weekday>» quedan FUERA
 * (modifican al sustantivo, doctrina ancla vigente, pin c.954 intacto) y la
 * parte del día INTERCALADA con weekday desnudo («lunes en la mañana llegó…»)
 * sigue anclada (pin c.954 byte-idéntico: extensión conservadora c.954 solo
 * para «el/este»).
 *
 * Fecha y título no divergen: ambos consultan el mismo guard (c.950).
 */
class NaturalTaskParserWeekdayBarePreteriteNarrativoTest {

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

    // ---- Capturas narrativas: «<weekday> [ya|,] [pronombre] <pretérito> …» ----

    @Test fun lunesLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("lunes llegó el paquete")

    @Test fun viernesSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("viernes sonó la alarma")

    @Test fun martesVinoElTecnico_esContenidoNarrativo() =
        assertNarrativeIntact("martes vino el técnico")

    @Test fun miercolesSeRompioLaTuberia_pronombreEsContenidoNarrativo() =
        assertNarrativeIntact("miércoles se rompió la tubería")

    @Test fun juevesComaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("jueves, llegó el paquete")

    @Test fun sabadoLlovio_esContenidoNarrativo() =
        assertNarrativeIntact("sábado llovió")

    @Test fun domingoFueElConcierto_esContenidoNarrativo() =
        assertNarrativeIntact("domingo fue el concierto")

    @Test fun lunesMayusculaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("Lunes llegó el paquete")

    // ---- Guards de ancla (comando/presente/modificador/genitivo: medidos PRE) ----

    @Test fun lunesLlamarAlBanco_comandoSigueAncla() =
        assertAnchor("lunes llamar al banco", LocalDate.of(2026, 8, 24), 9, "llamar al banco")

    @Test fun lunesTengoReunion_presenteSigueAncla() =
        assertAnchor("lunes tengo reunión", LocalDate.of(2026, 8, 24), 9, "tengo reunión")

    @Test fun lunesQueVieneLlegoElPaquete_modificadorFuturoSigueAncla() =
        assertAnchor("lunes que viene llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun laReunionDelLunesFueProductiva_genitivoSigueAnclaPin() =
        assertAnchor("la reunión del lunes fue productiva", LocalDate.of(2026, 8, 24), 9, "la reunión fue productiva")

    @Test fun deLunesAViernesTrabaje_genitivoDeSigueAnclaPin() =
        assertAnchor("de lunes a viernes trabajé", LocalDate.of(2026, 8, 24), 9, "trabajé")

    // ---- Pines/regresiones de doctrinas vigentes (c.950/c.954) ----

    @Test fun elLunesLlegoElPaquete_directoC950Intacto() =
        assertNarrativeIntact("el lunes llegó el paquete")

    @Test fun lunesEnLaMananaLlegoElPaquete_intercaladaSinArticuloSigueAnclaPinC954() =
        assertAnchor("lunes en la mañana llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun elLunesEnLaMananaLlegoElPaquete_intercaladaC954Intacta() =
        assertNarrativeIntact("el lunes en la mañana llegó el paquete")
}

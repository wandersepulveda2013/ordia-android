package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.1075: genitivo de RANGO con día relativo PASADO («desde/hasta/de +
 * ayer/anteayer/antier») en posición de CONTENIDO = narrativa/estado, nunca
 * ancla de fecha: «desde ayer no duermo bien» (since yesterday…), «hasta
 * ayer trabajé en el proyecto» (until yesterday…), «el informe es desde/de
 * ayer» (the report is from yesterday). Antes el parser fabricaba una fecha
 * PASADA falsa (ayer/anteayer 09:00 — compromiso vencido al nacer que
 * ensucia What Now / planificador / recordatorios) Y mutilaba el título
 * (borraba el genitivo, contenido del usuario) — doble daño P1 (medida
 * c.1075: 10/10 PRE con sondas efímeras `/tmp/probe1075/Probe.kt` y
 * `GuardProbe.kt`, motor real, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo; lateral registrada c.957, medida c.1006).
 *
 * Evidencia gramatical inequívoca (doctrina c.927/c.930): un encargo real
 * jamás ABRE con un límite de rango pasado («desde/hasta ayer…») ni lo
 * predica con una CÓPULA («es/era/son/eran + desde/de/hasta ayer»).
 * Conservador (UNA lateral por ciclo, doctrina anti-overreach c.615):
 *  - «cita de ayer» / «entregar el informe de ayer» (genitivo de posesión
 *    temporal SIN cópula) SIGUEN ancla — doctrina ancla vigente pineada
 *    byte-idéntica;
 *  - «reunión desde hoy» / «Curso desde mañana» (día presente/futuro)
 *    SIGUEN ancla;
 *  - «hasta ayer» NO inicial («lo tenía que entregar hasta ayer») SIGUE
 *    ancla (reconocimiento de plazo vencido, doctrina vigente);
 *  - «el plazo es hasta el viernes» (weekday, familia distinta) pin
 *    byte-idéntico — lateral de título propia registrada.
 * Fecha y título fluyen del mismo flag ([ayerRangeGenitiveNarrative]):
 * nunca divergen (doctrina c.930/c.950).
 */
class NaturalTaskParserGenitivoRangoAyerNarrativoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val nowMillis = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, nowMillis, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull("«$text» no debe producir fecha (genitivo de rango narrativo, no compromiso)", r.dueAt)
        assertEquals("el título debe conservar la cadena narrativa íntegra", text, r.title)
    }

    private fun assertAnchor(text: String, date: LocalDate, hour: Int, title: String) {
        val r = parse(text)
        assertEquals(date, r.dueAt?.let { DateRules.toLocalDate(it, zone) })
        assertEquals(LocalTime.of(hour, 0), r.dueAt?.let { DateRules.toLocalTime(it, zone) })
        assertEquals(title, r.title)
    }

    // ---- Capturas: genitivo de rango al INICIO del enunciado ----

    @Test fun desdeAyerAlInicio_esContenidoNarrativo() =
        assertNarrativeIntact("desde ayer no duermo bien")

    @Test fun hastaAyerAlInicio_esContenidoNarrativo() =
        assertNarrativeIntact("hasta ayer trabajé en el proyecto")

    @Test fun desdeAnteayerAlInicio_esContenidoNarrativo() =
        assertNarrativeIntact("desde anteayer no duermo bien")

    @Test fun hastaAntierAlInicio_esContenidoNarrativo() =
        assertNarrativeIntact("hasta antier trabajé en el proyecto")

    // ---- Capturas: genitivo de rango tras CÓPULA ----

    @Test fun esDesdeAyer_esContenidoNarrativo() =
        assertNarrativeIntact("el informe es desde ayer")

    @Test fun reunionEsDesdeAyer_esContenidoNarrativo() =
        assertNarrativeIntact("la reunión es desde ayer")

    @Test fun esDeAyer_esContenidoNarrativo() =
        assertNarrativeIntact("el informe es de ayer")

    @Test fun eraDeAyer_esContenidoNarrativo() =
        assertNarrativeIntact("la carta era de ayer")

    @Test fun sonDeAyer_esContenidoNarrativo() =
        assertNarrativeIntact("los informes son de ayer")

    @Test fun eranDeAnteayer_esContenidoNarrativo() =
        assertNarrativeIntact("las actas eran de anteayer")

    // ---- Guards ancla BYTE-IDÉNTICOS (doctrina vigente, medida PRE c.1075) ----

    @Test fun citaDeAyer_sinCopula_sigueAncla() =
        assertAnchor("cita de ayer", LocalDate.of(2026, 8, 22), 9, "cita")

    @Test fun entregarInformeDeAyer_sinCopula_sigueAncla() =
        assertAnchor("entregar el informe de ayer", LocalDate.of(2026, 8, 22), 9, "entregar el informe")

    @Test fun reunionDesdeHoy_diaPresente_sigueAncla() =
        assertAnchor("reunión desde hoy", LocalDate.of(2026, 8, 23), 9, "reunión")

    @Test fun cursoDesdeManana_diaFuturo_sigueAncla() =
        assertAnchor("Curso desde mañana", LocalDate.of(2026, 8, 24), 9, "Curso")

    @Test fun hacerLaTareaAyer_ayerDesnudo_sigueAncla() =
        assertAnchor("hacer la tarea ayer", LocalDate.of(2026, 8, 22), 9, "hacer la tarea")

    @Test fun hastaAyerNoInicial_plazoVencido_sigueAncla() =
        assertAnchor("lo tenía que entregar hasta ayer", LocalDate.of(2026, 8, 22), 9, "lo tenía que entregar")

    @Test fun plazoHastaElViernes_weekday_pinByteIdentico() =
        assertAnchor("el plazo es hasta el viernes", LocalDate.of(2026, 8, 28), 9, "el plazo es")

    // ---- Regresiones doctrina vigente (c.954/c.957, ancla «ayer» deliberada) ----

    @Test fun ayerEnLaMananaLlego_narrativaParteDia_intacta() =
        assertNarrativeIntact("ayer en la mañana llegó el paquete")

    @Test fun anteayerPorLaNocheSono_narrativaParteDia_intacta() =
        assertNarrativeIntact("anteayer por la noche sonó la alarma")

    @Test fun reunionAyerALas4_vencidaExplicita_sigueAncla() =
        assertAnchor("reunión ayer a las 4", LocalDate.of(2026, 8, 22), 4, "reunión")

    @Test fun llamarAlBancoManana_anclaFutura_intacta() =
        assertAnchor("llamar al banco mañana", LocalDate.of(2026, 8, 24), 9, "llamar al banco")
}

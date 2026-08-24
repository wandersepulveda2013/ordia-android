package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1019 [renumerado c.1017→c.1018→c.1019: colisiones cycle-ID con SU c.1017 «desparasitar al perro» (`d8a815e`+`f339c4a`) y SU c.1018 «pasear al perro» (`23b2761`+`171e448`), ambas contexto, llegadas en los re-fetch pre-push] (delta UNIÓN sobre H4 c.1016 — colisión convergente: este run tenía
 * implementada la lateral MADRE «a primera hora + pretérito» completa cuando
 * el hermano publicó su c.1016 con el subconjunto SIN artículo; precedente de
 * integración delta c.1014-hermano): el CON-ARTÍCULO («a la/las primeras?/
 * últimas? horas?») seguía con DOBLE daño P1 — fecha FALSA hoy 09:00/18:00 y
 * título MUTILADO. Lateral registrada FUERA por SU c.1016 (pin byte-idéntico
 * en [NaturalTaskParserOrdinalHoraPreteritoNarrativoTest]) y medida de nuevo
 * en ESTE ciclo con sonda efímera `/tmp/probe1014/Probe4.kt` (motor real vía
 * `tools/run_probe.sh`, now=domingo 2026-08-23 12:00 America/Santo_Domingo,
 * HEAD `9920a22` c.1016): 4/4 candidatas con-artículo con doble daño
 * («a la primera hora vino el técnico» → 09:00 + «vino el técnico»);
 * 4/4 guards ancla correctos; 3/3 regresiones H4 intactas; 6/6 pines FUERA
 * byte-idénticos.
 * Fix mínimo (1 punto): rama «a» CON artículo de
 * [ordinalHoraOccurrenceIsContent] — la MISMA evidencia inequívoca de H4
 * ([ordinalHoraOccurrenceIsPreteriteNarrative]: pretérito adyacente de la
 * lista cerrada c.950 — un encargo real jamás abre ni se reduce a un
 * pretérito) convierte la aparición en CONTENIDO narrativo. Al fluir por el
 * mismo predicado, fecha y título NUNCA divergen (doctrina c.930/c.950).
 * Re-pin legítimo MÁS estricto (precedente c.957/c.965): el pin FUERA de SU
 * c.1016 «a la primera hora llegó el cartero» pasa de ancla a narrativa con
 * comentario c.1019.
 * FUERA a propósito (byte-idénticos — pins abajo): weekday genitivo tras
 * artículo («a la primera hora del lunes llegó…» — doctrina ancla vigente),
 * pretérito con complemento antes del ancla («me quedé dormido a la primera
 * hora», «sonó la alarma a la primera hora» — no es predicado SOLO, doctrina
 * c.1016) y formas ambiguas pretérito/presente («salimos a la primera hora»,
 * doctrina c.950). La lateral «ya <pretérito>» (regla de inmediatez «ya» que
 * ancla a AHORA narrativas sin marca temporal — medida c.1019 en
 * `/tmp/probe1014/Probe3.kt`: «ya sonó/pagué/llegó…» → now) es INDEPENDIENTE
 * y sigue ABIERTA registrada en BACKLOG.
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraALasPreteritoNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    private fun assertAnchor(text: String, expectedDate: LocalDate, expectedHour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(expectedDate, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(expectedHour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas: «a la/las» + pretérito → due=null + título íntegro ----

    @Test fun aLaPrimeraHoraVinoElTecnico_esContenidoNarrativo() =
        assertNarrativeIntact("a la primera hora vino el técnico")

    @Test fun aLasPrimerasHorasEmpezoLaReunion_esContenidoNarrativo() =
        assertNarrativeIntact("a las primeras horas empezó la reunión")

    @Test fun aLaUltimaHoraCerroLaTienda_esContenidoNarrativo() =
        assertNarrativeIntact("a la última hora cerró la tienda")

    @Test fun aLaPrimeraHoraDeLaMananaLlamoMama_esContenidoNarrativo() =
        assertNarrativeIntact("a la primera hora de la mañana llamó mamá")

    @Test fun llegueALaPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("llegué a la primera hora")

    @Test fun meDesperteALaPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("me desperté a la primera hora")

    @Test fun aLaPrimeraHoraYaSonoLaAlarma_pinLateralYa() {
        // Pin de la conducta POST: el ordinal narrativo YA se suprime (título
        // íntegro, sin 09:00), pero la regla de inmediatez «ya» (intencional
        // para comandos: «avisar ya» → ahora) sigue anclando «ya <pretérito>»
        // a AHORA — lateral narrativa INDEPENDIENTE registrada ABIERTA en
        // BACKLOG (medida c.1019 `/tmp/probe1014/Probe3.kt` sobre la base
        // c.1016 + delta: «ya sonó/pagué/llegó…» → now, byte-idéntica PRE/POST
        // de este ciclo).
        assertAnchor("a la primera hora ya sonó la alarma", LocalDate.of(2026, 8, 23), 12, "a la primera hora sonó la alarma")
    }

    // ---- Guards ancla (byte-idénticos): sin pretérito inequívoco adyacente ----

    @Test fun avisarALaUltimaHora_sigueAncla() =
        assertAnchor("avisar a la última hora", LocalDate.of(2026, 8, 23), 18, "avisar")

    @Test fun avisarALasPrimerasHoras_sigueAncla() =
        assertAnchor("avisar a las primeras horas", LocalDate.of(2026, 8, 23), 9, "avisar")

    @Test fun aLaPrimeraHoraTengoCita_sigueAncla() =
        assertAnchor("a la primera hora tengo cita", LocalDate.of(2026, 8, 23), 9, "tengo cita")

    @Test fun aLaPrimeraHoraLlamarAlBanco_sigueAncla() =
        assertAnchor("a la primera hora llamar al banco", LocalDate.of(2026, 8, 23), 9, "llamar al banco")

    @Test fun salimosALaPrimeraHora_ambiguaSigueAncla() =
        assertAnchor("salimos a la primera hora", LocalDate.of(2026, 8, 23), 9, "salimos")

    // ---- Pines FUERA (laterales registradas, byte-idénticos) ----

    @Test fun aLaPrimeraHoraDelLunesLlego_lateralWeekdayGenitivoFuera() =
        assertAnchor("a la primera hora del lunes llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun meQuedeDormidoALaPrimeraHora_lateralPreteritoConComplementoFuera() =
        assertAnchor("me quedé dormido a la primera hora", LocalDate.of(2026, 8, 23), 9, "me quedé dormido")

    @Test fun sonoLaAlarmaALaPrimeraHora_lateralPreteritoConObjetoFuera() =
        assertAnchor("sonó la alarma a la primera hora", LocalDate.of(2026, 8, 23), 9, "sonó la alarma")

    // ---- Regresiones narrativas hermanas (c.931 H2 / c.1016 H4) intactas ----

    @Test fun aLaPrimeraHoraDeClaseMeQuedeDormido_regresionH2Intacta() =
        assertNarrativeIntact("a la primera hora de clase me quedé dormido")

    @Test fun aLaUltimaHoraDelPartidoLlegoElGol_regresionH2Intacta() =
        assertNarrativeIntact("a la última hora del partido llegó el gol")

    @Test fun aPrimeraHoraLlegoElCartero_regresionH4Intacta() =
        assertNarrativeIntact("a primera hora llegó el cartero")
}

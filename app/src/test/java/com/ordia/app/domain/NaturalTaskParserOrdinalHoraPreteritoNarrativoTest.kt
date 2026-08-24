package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1016: ordinal de hora con conector «a» (sin artículo) en CADENA NARRATIVA
 * en pretérito («a primera hora llegó el cartero», «llegué a primera hora»).
 * Lateral medida FUERA en c.1008 (sonda efímera `/tmp/probe1013/Probe.kt` y
 * `Probe2.kt`, motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23
 * 12:00 America/Santo_Domingo, HEAD `dd7d4f54`): 9/9 candidatas con DOBLE
 * daño P1 — fecha FALSA (la nota nacía como tarea de hoy 09:00/18:00/21:00,
 * ya PASADA al mediodía: compromiso vencido al nacer que ensucia What Now y
 * dispara recordatorios absurdos) y título MUTILADO («llegó el cartero» sin
 * su marca temporal — contenido del usuario borrado). 5/5 guards ancla
 * correctos, 4/4 regresiones narrativas hermanas intactas, 4/4 pines FUERA
 * medidos, 2/2 formas ambiguas ancladas (doctrina c.950).
 * Fix mínimo (1 punto): rama «a» sin artículo de
 * [ordinalHoraOccurrenceIsContent] — nueva heurística (H4): la aparición es
 * CONTENIDO narrativo cuando el predicado adyacente es pretérito INEQUÍVOCO
 * ([preteriteNarrativeVerbAlternation], la misma lista de c.950 extraída sin
 * cambiar un byte del regex original):
 *  (H4-sufijo) el texto TRAS el match abre con pretérito («a primera hora
 *      llegó el cartero», «a última hora de la noche cerró la tienda» —
 *      el sufijo canónico «de la mañana/noche» lo consume el propio patrón);
 *  (H4-prefijo) TODO el prefijo es un predicado pretérito SOLO («llegué…»,
 *      «me desperté…», «ya salí…»): un encargo real jamás se reduce a un
 *      pretérito antes del ancla (doctrina c.950).
 * Al fluir por [ordinalHoraOccurrenceIsContent], la supresión de la fecha, la
 * conservación del título y la protección de la parte del día gobernada
 * («de la mañana») comparten el mismo predicado: fecha y título NUNCA
 * divergen (doctrina c.930/c.950).
 * FUERA (laterales registradas, byte-idénticas — pins de alcance abajo):
 *  «a primera hora del lunes llegó…» (weekday genitivo: doctrina ancla
 *  vigente c.950), «me quedé dormido a primera hora» y «sonó la alarma a
 *  primera hora» (prefijo pretérito + complemento: no es predicado SOLO).
 *  La lateral «a LA primera hora llegó…» (artículo tras «a») quedó RESUELTA
 *  por el delta c.1018 (re-pin abajo; su clase propia cubre la familia
 *  con-artículo: NaturalTaskParserOrdinalHoraALasPreteritoNarrativoTest).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraPreteritoNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    private fun assertDueAt(text: String, expectedDate: LocalDate, expectedHour: Int, expectedTitle: String) {
        val r = parse(text)
        assertEquals(expectedDate, DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(expectedHour, 0), DateRules.toLocalTime(r.dueAt!!, zone))
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas: narrativa en pretérito → due=null + título íntegro ----

    @Test fun aPrimeraHoraLlegoElCartero_esContenidoNarrativo() =
        assertNarrativeIntact("a primera hora llegó el cartero")

    @Test fun aPrimeraHoraSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("a primera hora sonó la alarma")

    @Test fun llegueAPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("llegué a primera hora")

    @Test fun meDesperteAPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("me desperté a primera hora")

    @Test fun aPrimeraHoraDeLaMananaLlego_esContenidoNarrativo() =
        assertNarrativeIntact("a primera hora de la mañana llegó el cartero")

    @Test fun aUltimaHoraLlegoElCartero_esContenidoNarrativo() =
        assertNarrativeIntact("a última hora llegó el cartero")

    @Test fun aUltimaHoraDeLaNocheCerro_esContenidoNarrativo() =
        assertNarrativeIntact("a última hora de la noche cerró la tienda")

    @Test fun justoAPrimeraHoraLlego_esContenidoNarrativo() =
        assertNarrativeIntact("justo a primera hora llegó el cartero")

    @Test fun aPrimeraHoraEmpezoLaReunion_esContenidoNarrativo() =
        assertNarrativeIntact("a primera hora empezó la reunión")

    // ---- Guards: ancla legítima (encargo) — byte-idénticos ----

    @Test fun avisarAPrimeraHora_sigueAncla() =
        assertDueAt("avisar a primera hora", LocalDate.of(2026, 8, 23), 9, "avisar")

    @Test fun llamarAlBancoAPrimeraHora_sigueAncla() =
        assertDueAt("llamar al banco a primera hora", LocalDate.of(2026, 8, 23), 9, "llamar al banco")

    @Test fun salirAPrimeraHoraManana_sigueAncla() =
        assertDueAt("salir a primera hora mañana", LocalDate.of(2026, 8, 24), 9, "salir")

    @Test fun avisarAUltimaHora_sigueAncla() =
        assertDueAt("avisar a última hora", LocalDate.of(2026, 8, 23), 18, "avisar")

    @Test fun avisarALaPrimeraHora_sigueAncla() =
        assertDueAt("avisar a la primera hora", LocalDate.of(2026, 8, 23), 9, "avisar")

    @Test fun avisarALasPrimerasHoras_sigueAncla() =
        assertDueAt("avisar a las primeras horas", LocalDate.of(2026, 8, 23), 9, "avisar")

    @Test fun quieroLasPrimerasHorasDeLaManana_sigueAncla() =
        assertDueAt("quiero las primeras horas de la mañana", LocalDate.of(2026, 8, 23), 9, "quiero")

    // ---- Formas ambiguas pretérito/presente (1ª plural): excluidas por
    //      doctrina c.950 («salimos/comimos» también son presente) — pin
    //      byte-idéntico de la doctrina vigente (ancla conservadora) ----

    @Test fun salimosAPrimeraHora_ambiguaSigueAncla() =
        assertDueAt("salimos a primera hora", LocalDate.of(2026, 8, 23), 9, "salimos")

    @Test fun comimosAPrimeraHora_ambiguaSigueAncla() =
        assertDueAt("comimos a primera hora", LocalDate.of(2026, 8, 23), 9, "comimos")

    // ---- Pines FUERA (laterales registradas en el KDoc, byte-idénticos) ----

    @Test fun aLaPrimeraHoraLlego_resueltaC1018RePinNarrativa() {
        // Re-pin legítimo MÁS estricto (precedente c.957/c.965): este pin FUERA
        // (ancla 09:00 + título mutilado) quedó RESUELTO por el delta c.1018 —
        // el artículo tras «a» ya no exime de la evidencia de pretérito
        // adyacente; ahora narrativa intacta (due=null, título íntegro).
        assertNarrativeIntact("a la primera hora llegó el cartero")
    }

    @Test fun aPrimeraHoraDelLunesLlego_lateralWeekdayGenitivoFuera() =
        assertDueAt("a primera hora del lunes llegó el paquete", LocalDate.of(2026, 8, 24), 9, "llegó el paquete")

    @Test fun meQuedeDormidoAPrimeraHora_lateralPreteritoConComplementoFuera() =
        assertDueAt("me quedé dormido a primera hora", LocalDate.of(2026, 8, 23), 9, "me quedé dormido")

    @Test fun sonoLaAlarmaAPrimeraHora_lateralPreteritoConObjetoFuera() =
        assertDueAt("sonó la alarma a primera hora", LocalDate.of(2026, 8, 23), 9, "sonó la alarma")

    // ---- Regresiones narrativas hermanas (c.930/c.932/c.956) re-pineadas ----

    @Test fun laPrimeraHoraDeClase_regresionH2Intacta() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")

    @Test fun lasPrimerasHorasDeLaManana_regresionH3Intacta() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")
}

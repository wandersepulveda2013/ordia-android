package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.937: narrativa ordinal con genitivo de CONTENIDO (H2/H1/«a la», c.930/c.931)
 * seguida de weekday genitivo («del lunes…») y predicado: «la primera hora de
 * clase del lunes fue aburrida». El weekday pertenece a la cadena genitiva del
 * sujeto narrativo: no es ancla de fecha ni token borrable (extensión de la
 * doctrina c.936, que sólo cubría el genitivo canónico DENTRO del match — H3).
 *
 * Medida PRE (sonda efímera /tmp/probe937/PreProbe.kt, motor real vía
 * tools/run_probe.sh, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 * 8/8 candidatas con DOBLE daño — fecha FALSA del weekday (lun/mar/jue/vie/sáb
 * 09:00, hora ajena incluso al ordinal: «última hora… del viernes» → vie 09:00)
 * y título mutilado («del lunes» borrado — contenido del usuario). Guards,
 * pins y regresiones en conducta vigente (ver tests).
 *
 * Pins byte-idénticos (doctrina vigente NO tocada): sin predicado tras el
 * weekday («la primera hora de clase del lunes»), «que viene» (el match de
 * weekdayPattern se extiende más allá del rango protegido y containsRange lo
 * excluye), y weekday genitivo DIRECTO sin genitivo de contenido intermedio
 * («esa primera hora del lunes fue terrible» — hermano del pin c.936
 * «las primeras horas del lunes son tranquilas»: sigue la doctrina ancla).
 */
class NaturalTaskParserWeekdayGenitivoNarrativoH2Test {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de las sondas del ciclo)
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

    // ---- Capturas: weekday genitivo tras genitivo de contenido (H2/H1/«a la») ----

    @Test fun laPrimeraHoraDeClaseDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora de clase del lunes fue aburrida")

    @Test fun laUltimaHoraDelPartidoDelViernes_esContenidoNarrativo() =
        assertNarrativeIntact("la última hora del partido del viernes fue emocionante")

    @Test fun lasPrimerasHorasDeClaseDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de clase del lunes fueron duras")

    @Test fun esaPrimeraHoraDeClaseDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("esa primera hora de clase del lunes fue terrible")

    @Test fun laPrimeraHoraDeTrabajoDelMartes_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora de trabajo del martes fue lenta")

    @Test fun aLaPrimeraHoraDeClaseDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("a la primera hora de clase del lunes me quedé dormido")

    @Test fun laUltimaHoraDeLaReunionDelJueves_esContenidoNarrativo() =
        assertNarrativeIntact("la última hora de la reunión del jueves fue clave")

    @Test fun elPrimerMomentoDeLaPeliculaDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("el primer momento de la película del sábado fue clave")

    // ---- Guards: anclas con weekday fuera de la narrativa (byte-idénticos) ----

    @Test fun llamarAlBancoAPrimeraHoraDelLunes_sigueAnclando() =
        assertDueAt("llamar al banco a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "llamar al banco")

    @Test fun reunionElLunesAPrimeraHora_sigueAnclando() =
        assertDueAt("reunión el lunes a primera hora", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun terminarElViernesUltimaHora_sigueAnclando() =
        assertDueAt("terminar el viernes última hora", LocalDate.of(2026, 8, 28), 18, "terminar")

    @Test fun reunionElLunes_sigueAnclando() =
        assertDueAt("reunión el lunes", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun citaMananaALas9_sigueAnclando() =
        assertDueAt("cita mañana a las 9", LocalDate.of(2026, 8, 24), 9, "cita")

    // ---- Pins byte-idénticos: doctrina vigente NO tocada por esta unidad ----

    @Test fun h2SinPredicadoTrasWeekday_pinBivalente() =
        assertDueAt(
            "la primera hora de clase del lunes",
            LocalDate.of(2026, 8, 24), 9, "la primera hora de clase"
        )

    @Test fun h2WeekdayConModificadorQueViene_pinBivalente() =
        assertDueAt(
            "la primera hora de clase del lunes que viene",
            LocalDate.of(2026, 8, 24), 9, "la primera hora de clase"
        )

    @Test fun weekdayGenitivoDirectoSinGenitivoDeContenido_pinAncla() =
        assertDueAt(
            "esa primera hora del lunes fue terrible",
            LocalDate.of(2026, 8, 24), 9, "esa primera hora fue terrible"
        )

    // ---- Regresiones: doctrinas hermanas intactas ----

    @Test fun narrativaH3SinWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun narrativaH3ConWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun narrativaH2SinWeekday_sigueProtegida() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")

    @Test fun narrativaOpinionH3_sigueProtegida() =
        assertNarrativeIntact("creo que las primeras horas de la mañana son las mejores")
}

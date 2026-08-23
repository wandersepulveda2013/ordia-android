package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * c.938: narrativa ordinal H1 (demostrativo AL INICIO del texto, c.930) seguida
 * de weekday genitivo DIRECTO — sin genitivo de contenido intermedio — y
 * predicado: «esa primera hora del lunes fue terrible». El weekday pertenece a
 * la cadena genitiva del sujeto narrativo: no es ancla de fecha ni token
 * borrable (extensión de la doctrina c.936/c.937, que exigían el genitivo
 * canónico dentro del match — H3 — o un genitivo de contenido intermedio — H2;
 * el demostrativo al inicio ya es evidencia inequívoca por sí solo, doctrina
 * H1, y no necesita genitivo alguno para ser contenido).
 *
 * Medida PRE (sonda efímera /tmp/probe938/PreProbe.kt, motor real vía
 * tools/run_probe.sh, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 * 8/8 candidatas con DOBLE daño — fecha FALSA del weekday (lun/mar/mié/jue/vie/
 * sáb/dom 09:00, hora ajena incluso al ordinal: «esa última hora del viernes»
 * → vie 09:00, no 18:00) y título mutilado («del lunes» borrado — contenido
 * del usuario). Guards, pins y regresiones en conducta vigente (ver tests).
 *
 * Anti-overreach (doctrina c.615, simétrica a H3 «sin verbo precedente»): el
 * demostrativo debe estar AL INICIO del texto. Con verbo precedente («quiero
 * esa primera hora del lunes para estudiar», «necesito esa primera hora del
 * lunes libre») la forma es bivalente (petición de hueco) y sigue la doctrina
 * ancla — pins byte-idénticos. Con artículo sin demostrativo («la primera hora
 * del lunes fue aburrida») el genitivo «del lunes» es genitivo-ancla y H2 no
 * dispara: sigue ancla (pin byte-idéntico, lateral registrada).
 *
 * Pins byte-idénticos (doctrina vigente NO tocada): sin predicado tras el
 * weekday («esa primera hora del lunes»), «que viene» (el match de
 * weekdayPattern se extiende más allá del rango protegido y containsRange lo
 * excluye) y verbo + demostrativo sin predicado («avisar esa primera hora del
 * lunes»).
 */
class NaturalTaskParserWeekdayGenitivoDirectoNarrativoTest {

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

    // ---- Capturas: weekday genitivo DIRECTO tras demostrativo al inicio (H1) ----

    @Test fun esaPrimeraHoraDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("esa primera hora del lunes fue terrible")

    @Test fun esaUltimaHoraDelViernes_esContenidoNarrativo() =
        assertNarrativeIntact("esa última hora del viernes fue eterna")

    @Test fun esasPrimerasHorasDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("esas primeras horas del lunes fueron duras")

    @Test fun estePrimerMomentoDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("este primer momento del sábado fue clave")

    @Test fun esaUltimaHoraDelMiercoles_esContenidoNarrativo() =
        assertNarrativeIntact("esa última hora del miércoles me costó mucho")

    @Test fun esasUltimasHorasDelJueves_esContenidoNarrativo() =
        assertNarrativeIntact("esas últimas horas del jueves fueron lentas")

    @Test fun aquelPrimerMomentoDelDomingo_esContenidoNarrativo() =
        assertNarrativeIntact("aquel primer momento del domingo fue especial")

    @Test fun estaPrimeraHoraDelMartes_esContenidoNarrativo() =
        assertNarrativeIntact("esta primera hora del martes fue tranquila")

    // ---- Guards bivalentes: siguen ancla (byte-idénticos) ----

    @Test fun demostrativoSinPredicadoTrasWeekday_pinBivalente() =
        assertDueAt("esa primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "esa primera hora")

    @Test fun demostrativoWeekdayQueViene_pinBivalente() =
        assertDueAt("esa primera hora del lunes que viene", LocalDate.of(2026, 8, 24), 9, "esa primera hora")

    @Test fun verboMasDemostrativoSinPredicado_pinBivalente() =
        assertDueAt("avisar esa primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "avisar esa primera hora")

    @Test fun verboMasDemostrativoConPredicado_pinBivalente() =
        assertDueAt("quiero esa primera hora del lunes para estudiar", LocalDate.of(2026, 8, 24), 9, "quiero esa primera hora para estudiar")

    @Test fun verboNecesitoMasDemostrativo_pinBivalente() =
        assertDueAt("necesito esa primera hora del lunes libre", LocalDate.of(2026, 8, 24), 9, "necesito esa primera hora libre")

    @Test fun articuloSinDemostrativo_pinAncla() =
        assertDueAt("la primera hora del lunes fue aburrida", LocalDate.of(2026, 8, 24), 9, "la fue aburrida")

    // ---- Guards ancla hermanos (byte-idénticos) ----

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

    // ---- Regresiones: doctrinas hermanas intactas ----

    @Test fun narrativaH2ConWeekday_sigueProtegida() =
        assertNarrativeIntact("la primera hora de clase del lunes fue aburrida")

    @Test fun narrativaH3ConWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun narrativaH2SinWeekday_sigueProtegida() =
        assertNarrativeIntact("la primera hora de clase fue aburrida")

    @Test fun narrativaH3SinWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun narrativaOpinionH3_sigueProtegida() =
        assertNarrativeIntact("creo que las primeras horas de la mañana son las mejores")
}

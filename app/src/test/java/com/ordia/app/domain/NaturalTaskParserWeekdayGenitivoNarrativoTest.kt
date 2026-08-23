package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.936: narrativa ordinal H3 con weekday genitivo DENTRO de la cadena
 * («las primeras horas de la mañana del lunes son tranquilas» = the early
 * hours of Monday morning are quiet). Lateral medida FUERA en c.932/c.933/
 * c.935. Sondas efímeras `/tmp/probe936/PreProbe.kt` + `EdgeProbe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo): PRE — 9/9 candidatas con DOBLE daño P1: fecha
 * FALSA del weekday (lun/mié/vie/sáb 09:00) Y título mutilado (el genitivo
 * «del lunes», contenido del usuario, borrado del título); guards/regresiones
 * en su conducta vigente.
 * Doctrina (extensión de H3, c.932): cuando el ordinal narrativo protegido
 * (genitivo canónico dentro del match + determinante/cláusula de opinión +
 * predicado) gobierna además un weekday genitivo («del/de lunes…») seguido
 * de predicado, ese weekday es CONTENIDO narrativo: no ancla fecha ni se
 * borra del título. El weekday genitivo sin genitivo canónico («las primeras
 * horas del lunes», weekdays en ordinalHoraAnchorGenitives), el fragmento
 * sin predicado, el weekday con modificador («que viene») y el compuesto H2
 * («la primera hora de clase del lunes…», lateral FUERA) siguen la doctrina
 * vigente (pins byte-idénticos). Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserWeekdayGenitivoNarrativoTest {

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

    // ---- Capturas: weekday genitivo dentro de la cadena narrativa H3 ----

    @Test fun lasPrimerasHorasDeLaMananaDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son tranquilas")

    @Test fun lasPrimerasHorasDeLaMananaDelLunesSonLasMejores_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la mañana del lunes son las mejores")

    @Test fun lasUltimasHorasDelDiaDelViernes_esContenidoNarrativo() =
        assertNarrativeIntact("las últimas horas del día del viernes fueron agotadoras")

    @Test fun lasPrimerasHorasDeLaMadrugadaDelSabado_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la madrugada del sábado fueron duras")

    @Test fun laPrimeraHoraSingularDeLaMananaDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("la primera hora de la mañana del lunes es la mejor")

    @Test fun creoQueLasPrimerasHorasDeLaMananaDelLunes_esContenidoNarrativo() =
        assertNarrativeIntact("creo que las primeras horas de la mañana del lunes son tranquilas")

    @Test fun enLasPrimerasHorasDelDiaDelViernes_esContenidoNarrativo() =
        assertNarrativeIntact("en las primeras horas del día del viernes trabajé mejor")

    @Test fun lasUltimasHorasDeLaTardeDelMiercoles_esContenidoNarrativo() =
        assertNarrativeIntact("las últimas horas de la tarde del miércoles fueron intensas")

    @Test fun lasPrimerasHorasDeLaMananaDeLunesSinArticuloContracto_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas de la mañana de lunes son tranquilas")

    // ---- Guards: anclas con weekday fuera de la narrativa (byte-idénticos) ----

    @Test fun reunionLaMananaDelLunes_sigueAnclando() =
        assertDueAt("reunión la mañana del lunes", LocalDate.of(2026, 8, 24), 9, "reunión la mañana")

    @Test fun avisarLaMananaDelLunes_sigueAnclando() =
        assertDueAt("avisar la mañana del lunes", LocalDate.of(2026, 8, 24), 9, "avisar la mañana")

    @Test fun citaElLunesPorLaManana_sigueAnclando() =
        assertDueAt("cita el lunes por la mañana", LocalDate.of(2026, 8, 24), 9, "cita")

    @Test fun llamarAlBancoAPrimeraHoraDelLunes_sigueAnclando() =
        assertDueAt("llamar al banco a primera hora del lunes", LocalDate.of(2026, 8, 24), 9, "llamar al banco")

    @Test fun reunionElLunesAPrimeraHora_sigueAnclando() =
        assertDueAt("reunión el lunes a primera hora", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun terminarElViernesUltimaHora_sigueAnclando() =
        assertDueAt("terminar el viernes última hora", LocalDate.of(2026, 8, 28), 18, "terminar")

    // ---- Pins byte-idénticos: doctrina vigente NO tocada por esta unidad ----

    @Test fun sinPredicadoTrasWeekday_pinBivalente() =
        assertDueAt(
            "las primeras horas de la mañana del lunes",
            LocalDate.of(2026, 8, 24), 9, "las primeras horas de la mañana"
        )

    @Test fun weekdayConModificadorQueViene_pinBivalente() =
        assertDueAt(
            "las primeras horas de la mañana del lunes que viene",
            LocalDate.of(2026, 8, 24), 9, "las primeras horas de la mañana"
        )

    // c.939: esta misma entrada dejó de ser pin de conducta vieja para ser
    // captura protegida (artículo AL INICIO + weekday genitivo DIRECTO +
    // predicado = sujeto narrativo, doctrina c.939 simétrica a H1/c.938). El
    // pin de conducta vieja era byte-idéntico a la medida PRE c.939;
    // convertirlo en aserción más estricta es un re-pin legítimo (precedente:
    // guard c.937 re-pineado en c.938, pin c.938 re-pineado en c.939).
    @Test fun genitivoWeekdaySinGenitivoCanonico_esContenidoNarrativo() =
        assertNarrativeIntact("las primeras horas del lunes son tranquilas")

    @Test fun primeraHoraDeLaNocheNoCanonicoEnPattern_pin() =
        assertDueAt(
            "las primeras horas de la noche del sabado fueron magicas",
            LocalDate.of(2026, 8, 29), 21, "las fueron magicas"
        )

    // c.937: esta misma entrada dejó de ser pin de conducta vieja para ser
    // captura protegida (weekday tras genitivo de contenido — H2). El pin de
    // conducta vieja era byte-idéntico a la medida PRE c.936; convertirlo en
    // aserción más estricta es un re-pin legítimo (precedente: guard
    // «reunión el lunes» convertido en pin tras c.935).
    @Test fun compuestoH2ConWeekday_capturaDesdeC937() =
        assertNarrativeIntact("la primera hora de clase del lunes fue aburrida")

    // ---- Regresiones: doctrina hermanas intactas ----

    @Test fun narrativaSinWeekday_sigueProtegida() =
        assertNarrativeIntact("las primeras horas de la mañana son las mejores")

    @Test fun narrativaOpinionSinWeekday_sigueProtegida() =
        assertNarrativeIntact("creo que las primeras horas de la mañana son las mejores")

    @Test fun reunionElLunes_regresion() =
        assertDueAt("reunión el lunes", LocalDate.of(2026, 8, 24), 9, "reunión")

    @Test fun citaMananaALas9_regresion() =
        assertDueAt("cita mañana a las 9", LocalDate.of(2026, 8, 24), 9, "cita")
}

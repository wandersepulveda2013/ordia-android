package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * c.925: enfático «misma» pegado a un ancla parte-del-día con preposición
 * («avisar a Juan por la mañana misma», «llamar al médico por la tarde misma»).
 * Forma cotidiana del habla de vencimientos intradía, hermana femenina de
 * «hoy/mañana mismo» (c.923): la parte del día ya se resolvía bien (mañana→09:00,
 * tarde→15:00, noche→21:00, madrugada→04:00) pero el enfático sobrevivía como
 * residuo en el título («avisar a Juan misma»), medido 7/7 por la sonda efímera
 * del ciclo (`/tmp/probe925/PreProbe.kt`, motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00; lateral (F) registrada en c.923). Doctrina
 * simétrica a «sin falta» (c.918) y «como muy tarde» (c.924): se borra SÓLO tras
 * preposición + «la» + parte del día; sin preposición («la mañana misma del
 * accidente») o con genitivo a continuación («en la mañana misma del accidente»)
 * es contenido y se conserva íntegro (guard genitivo `(?!\s+(?:de|del)\b)`).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserMananaMismaMarkerTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: ancla parte-del-día + enfático "misma" ----

    @Test fun porLaMananaMisma_limpiaTitulo() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("avisar a Juan por la mañana misma"))
    }

    @Test fun porLaTardeMisma_limpiaTitulo() {
        val r = parse("llamar al médico por la tarde misma")
        assertEquals("llamar al médico", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("llamar al médico por la tarde misma"))
    }

    @Test fun porLaNocheMisma_limpiaTitulo() {
        val r = parse("salir a correr por la noche misma")
        assertEquals("salir a correr", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("salir a correr por la noche misma"))
    }

    @Test fun enLaMananaMisma_limpiaTitulo() {
        val r = parse("revisar el informe en la mañana misma")
        assertEquals("revisar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("revisar el informe en la mañana misma"))
    }

    @Test fun duranteLaTardeMisma_limpiaTitulo() {
        val r = parse("leer el contrato durante la tarde misma")
        assertEquals("leer el contrato", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("leer el contrato durante la tarde misma"))
    }

    @Test fun deLaMananaMisma_limpiaTitulo() {
        val r = parse("terminar la tarea de la mañana misma")
        assertEquals("terminar la tarea", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar la tarea de la mañana misma"))
    }

    @Test fun porLaMadrugadaMisma_limpiaTitulo() {
        val r = parse("levantarse por la madrugada misma")
        assertEquals("levantarse", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("levantarse por la madrugada misma"))
    }

    // ---- Guards: contenido legítimo NO se toca (título byte-idéntico PRE/POST) ----

    @Test fun guardGenitivo_conservaMisma() {
        // "en la mañana misma del accidente": el genitivo posterior hace contenido
        // al enfático; el guard (?!\s+(?:de|del)\b) lo conserva. El ancla "en la
        // mañana" se consume igual que antes (comportamiento preexistente).
        val r = parse("llegó en la mañana misma del accidente")
        assertEquals("llegó misma del accidente", r.title)
    }

    @Test fun guardSinPreposicion_conservaMisma() {
        // "la mañana misma del accidente" sin preposición: la rama no casa y el
        // título queda byte-idéntico al PRE (el robo de "mañana" desnuda como
        // "tomorrow" es comportamiento PREEXISTENTE ajeno a esta rama — pin de
        // alcance, observación registrada en BACKLOG).
        val r = parse("la mañana misma del accidente fue terrible")
        assertEquals("la misma del accidente fue terrible", r.title)
    }

    @Test fun guardOrdenInvertido_conservaFrase() {
        // "esa misma mañana" (misma ANTES del sustantivo) no es el enfático de
        // esta familia: la rama no casa; título byte-idéntico al PRE.
        val r = parse("esa misma mañana volví a casa")
        assertEquals("esa misma volví a casa", r.title)
    }

    @Test fun guardMismoComoAdjetivo_conservaFrase() {
        // "el mismo pan de la mañana": "mismo" adjetivo de "pan", no enfático del
        // ancla: intacto (la parte del día se consume como antes).
        val r = parse("comprar el mismo pan de la mañana")
        assertEquals("comprar el mismo pan", r.title)
    }

    // ---- Regresiones: hermanas de la familia de enfáticos intactas ----

    @Test fun regresionPorLaManana_intacta() {
        val r = parse("avisar a Juan por la mañana")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("avisar a Juan por la mañana"))
    }

    @Test fun regresionHoyMismo_intacta() {
        val r = parse("terminar el informe hoy mismo")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar el informe hoy mismo"))
    }

    @Test fun regresionMananaMismo_intacta() {
        val r = parse("llamar al médico mañana mismo")
        assertEquals("llamar al médico", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("llamar al médico mañana mismo"))
    }
}

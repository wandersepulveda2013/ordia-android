package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.927: guard anti-robo de «mañana» desnuda cuando es CONTENIDO ("la mañana"
 * = the morning) y no la fecha "mañana" (tomorrow). Observación preexistente
 * registrada en c.925, medida 6/6 por la sonda efímera del ciclo
 * (`/tmp/probe927/PreProbe.kt`, motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00): «la mañana misma del accidente fue terrible» /
 * «esa misma mañana volví a casa» / «la mañana del accidente fue terrible» /
 * «esa mañana llovió mucho» / «recuerdo aquella mañana perfectamente» sufrían
 * DOBLE daño — fecha FALSA (+1d, la nota saltaba como tarea de mañana) y título
 * mutilado («la misma del accidente fue terrible») — porque `mananaAsDate` y el
 * borrado genérico del título no distinguían el sustantivo «mañana» del
 * adverbio-fecha «mañana». Doctrina (decisión de alcance del ciclo): se protege
 * SÓLO cuando la evidencia gramatical es inequívoca — (G1) demostrativo
 * precedente («esa/aquella/dicha/tal [misma] mañana», «la misma mañana»:
 * nadie dice «esa mañana» por "tomorrow") o (G2) genitivo con artículo a
 * continuación («mañana del accidente», «mañana de la victoria»: un "tomorrow"
 * nunca gobierna genitivo). «el informe de mañana» (genitivo de posesión
 * temporal sobre la FECHA) sigue resolviendo mañana — doctrina vigente l.5611,
 * pineada byte-idéntica. Laterales medidas FUERA (registradas en RUN_LOG):
 * «lo necesito para la mañana» (due=null preexistente + residuo «para la» —
 * familia parte-del-día tras «para», no genitivo), «la mañana de sábado» (sin
 * artículo), «toda la mañana». Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserMananaGenitivoGuardTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: «mañana» = contenido ("la mañana"), no fecha ----

    @Test fun laMananaMismaGenitivo_noRobaFechaNiTitulo() {
        val r = parse("la mañana misma del accidente fue terrible")
        assertNull(r.dueAt)
        assertEquals("la mañana misma del accidente fue terrible", r.title)
    }

    @Test fun laMananaGenitivo_noRobaFechaNiTitulo() {
        val r = parse("la mañana del accidente fue terrible")
        assertNull(r.dueAt)
        assertEquals("la mañana del accidente fue terrible", r.title)
    }

    @Test fun esaMismaManana_noRobaFechaNiTitulo() {
        val r = parse("esa misma mañana volví a casa")
        assertNull(r.dueAt)
        assertEquals("esa misma mañana volví a casa", r.title)
    }

    @Test fun esaManana_noRobaFechaNiTitulo() {
        val r = parse("esa mañana llovió mucho")
        assertNull(r.dueAt)
        assertEquals("esa mañana llovió mucho", r.title)
    }

    @Test fun aquellaManana_noRobaFechaNiTitulo() {
        val r = parse("recuerdo aquella mañana perfectamente")
        assertNull(r.dueAt)
        assertEquals("recuerdo aquella mañana perfectamente", r.title)
    }

    // ---- Regresiones: «mañana» = fecha (byte-idénticas al PRE) ----

    @Test fun mananaSuelta_sigueFechaManana() {
        val r = parse("llamar a Juan mañana")
        assertEquals("llamar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("llamar a Juan mañana"))
    }

    @Test fun mananaInicial_sigueFechaManana() {
        val r = parse("mañana llamar a Juan")
        assertEquals("llamar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("mañana llamar a Juan"))
    }

    @Test fun mananaConHora_sigueFechaManana() {
        val r = parse("cita mañana a las 9")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("cita mañana a las 9"))
    }

    @Test fun pasadoManana_sigueMasDosDias() {
        val r = parse("pasado mañana ir al médico")
        assertEquals("ir al médico", r.title)
        assertEquals(LocalDate.of(2026, 8, 25), date("pasado mañana ir al médico"))
    }

    @Test fun mananaPorLaManana_byteIdenticaAlPre() {
        val r = parse("mañana por la mañana")
        assertEquals("mañana por la mañana", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("mañana por la mañana"))
    }

    @Test fun genitivoDePosesionTemporal_sigueFechaManana() {
        // «el informe de mañana» = "tomorrow's report": genitivo sobre la FECHA,
        // doctrina vigente del borrado con conector (l.5611) — byte-idéntico al PRE.
        val r = parse("el informe de mañana")
        assertEquals("el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("el informe de mañana"))
    }

    // ---- Guards de vecindad (c.923/c.925 intactos) ----

    @Test fun porLaMananaMisma_guardC925Intacto() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("avisar a Juan por la mañana misma"))
    }

    @Test fun mananaMismo_guardC923Intacto() {
        val r = parse("terminarlo mañana mismo")
        assertEquals("terminarlo", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("terminarlo mañana mismo"))
    }
}

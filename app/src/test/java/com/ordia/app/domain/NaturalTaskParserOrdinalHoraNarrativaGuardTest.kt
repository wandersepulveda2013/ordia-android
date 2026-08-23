package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.930: guard anti-robo narrativo del ordinal de hora («la primera hora de
 * clase fue aburrida», «la última hora del partido fue emocionante»). Lateral
 * medida FUERA en c.929 (sondas efímeras `/tmp/probe930/PreProbe.kt` y
 * `EdgeProbe.kt`, motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23
 * 12:00): 15/15 narrativas robadas con DOBLE daño — fecha FALSA (la nota
 * saltaba como tarea de hoy 09:00/18:00) y título mutilado («la de clase fue
 * aburrida») — porque `primeraHoraPattern`/`ultimaHoraPattern` no distinguían
 * el ordinal sustantivo («la primera hora de X» = the first hour of X) del
 * ancla canónica («a primera hora» = 09:00, «a última hora» = 18:00).
 * Fix: predicado compartido `ordinalHoraOccurrenceIsContent` — contenido SÓLO
 * con evidencia gramatical inequívoca y NUNCA con el conector «a»/«justo a»
 * consumido por el patrón (ancla por doctrina c.102/c.546):
 *  (H1) demostrativo precedente («esa primera hora», «esa última hora»):
 *       nadie dice «esa primera hora» como ancla de las 09:00.
 *  (H2) artículo precedente («la/las/el/los») + genitivo de contenido a
 *       continuación («de clase», «del partido», «de la película»): un ancla
 *       de las 09:00/18:00 nunca gobierna genitivo de contenido. Se excluyen
 *       los genitivos-canónico del propio patrón (parte del día/día/jornada)
 *       y los weekdays («a primera hora del lunes» ya lleva conector «a»).
 * Usado en la resolución (fecha) y en el borrado del título (por rangos, como
 * `eraseMananaDateToken`) para que fecha y título nunca diverjan.
 * FUERA (lateral registrada, mecanismo apilado): genitivo canónico DENTRO del
 * match («las primeras horas de la mañana son las mejores») — proteger el
 * ordinal no basta porque `standalonePartOfDayPattern` robaría la parte del
 * día interior; requiere doctrina propia. Byte-idéntico pre-fix (pin de
 * alcance documentado abajo).
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraNarrativaGuardTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    // ---- Capturas: narrativa ordinal → due=null + título íntegro ----

    @Test fun laPrimeraHoraDeClase_esContenidoNarrativo() {
        val r = parse("la primera hora de clase fue aburrida")
        assertNull(r.dueAt)
        assertEquals("la primera hora de clase fue aburrida", r.title)
    }

    @Test fun laPrimeraHoraDelPartido_esContenidoNarrativo() {
        val r = parse("la primera hora del partido fue aburrida")
        assertNull(r.dueAt)
        assertEquals("la primera hora del partido fue aburrida", r.title)
    }

    @Test fun laPrimeraHoraDeLaPelicula_esContenidoNarrativo() {
        val r = parse("la primera hora de la película fue lenta")
        assertNull(r.dueAt)
        assertEquals("la primera hora de la película fue lenta", r.title)
    }

    @Test fun laPrimeraHoraDeVuelo_esContenidoNarrativo() {
        val r = parse("la primera hora de vuelo fue tranquila")
        assertNull(r.dueAt)
        assertEquals("la primera hora de vuelo fue tranquila", r.title)
    }

    @Test fun enLaPrimeraHoraDeClase_esContenidoNarrativo() {
        val r = parse("en la primera hora de clase no hablamos de nada")
        assertNull(r.dueAt)
        assertEquals("en la primera hora de clase no hablamos de nada", r.title)
    }

    @Test fun laUltimaHoraDelPartido_esContenidoNarrativo() {
        val r = parse("la última hora del partido fue emocionante")
        assertNull(r.dueAt)
        assertEquals("la última hora del partido fue emocionante", r.title)
    }

    @Test fun laUltimaHoraDeLaPelicula_esContenidoNarrativo() {
        val r = parse("la última hora de la película fue intensa")
        assertNull(r.dueAt)
        assertEquals("la última hora de la película fue intensa", r.title)
    }

    @Test fun esaPrimeraHora_demostrativoEsContenidoNarrativo() {
        val r = parse("esa primera hora fue terrible")
        assertNull(r.dueAt)
        assertEquals("esa primera hora fue terrible", r.title)
    }

    @Test fun esaUltimaHora_demostrativoEsContenidoNarrativo() {
        val r = parse("esa última hora fue decisiva")
        assertNull(r.dueAt)
        assertEquals("esa última hora fue decisiva", r.title)
    }

    @Test fun elPrimerMomentoDeLaPelicula_masculinoEsContenidoNarrativo() {
        val r = parse("el primer momento de la película fue clave")
        assertNull(r.dueAt)
        assertEquals("el primer momento de la película fue clave", r.title)
    }

    @Test fun enLasUltimasHorasDelPartido_pluralEsContenidoNarrativo() {
        val r = parse("en las últimas horas del partido hubo dos goles")
        assertNull(r.dueAt)
        assertEquals("en las últimas horas del partido hubo dos goles", r.title)
    }

    // ---- Guards: las anclas legítimas NO se tocan (verdes desde RED) ----

    @Test fun aPrimeraHora_sigueSiendoAncla() {
        val r = parse("reunión a primera hora")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aUltimaHora_sigueSiendoAncla() {
        val r = parse("reunión a última hora")
        assertEquals("reunión", r.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aPrimeraHoraDelLunes_conectorSemanaIntacto() {
        val r = parse("llamar al banco a primera hora del lunes")
        assertEquals("llamar al banco", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aPrimeraHoraDeLaManana_conectorParteDelDiaIntacto() {
        val r = parse("terminar el informe a primera hora de la mañana")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aPrimerasHoras_pluralAnclaIntacto() {
        val r = parse("revisar el correo a primeras horas")
        assertEquals("revisar el correo", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aUltimaHoraDelDia_conectorGenericoIntacto() {
        val r = parse("pagar la factura a última hora del día")
        assertEquals("pagar la factura", r.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun ultimaHoraDesnudaSinArticulo_pinC102Intacto() {
        // Pin c.102: «terminar el viernes última hora» (sin «a», sin artículo)
        // es ancla capturada deliberadamente; el guard no debe revertirla.
        val r = parse("terminar el viernes última hora")
        assertEquals("terminar", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun aLaUltimaHora_coloquialAncla() {
        // Coloquial «a la última hora» (≈ a última hora): ancla 18:00 con
        // título limpio. El residuo «a la» preexistente era la lateral
        // registrada aquí en c.930 — RESUELTA en c.931 (el patrón consume el
        // conector «a la»; ver NaturalTaskParserALaOrdinalHoraTest).
        val r = parse("avisar a la última hora")
        assertEquals("avisar", r.title)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun elInformeDePrimeraHora_genitivoPospuestoByteIdentico() {
        // «de primera hora» pospuesto (sin artículo antes del ordinal):
        // comportamiento ambiguo preexistente, byte-idéntico pre-fix.
        val r = parse("el informe de primera hora")
        assertEquals("el informe", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun primerasHorasDeLaMananaDentroDelMatch_lateralByteIdentica() {
        // LATERAL REGISTRADA FUERA (pin de alcance, comportamiento pre-fix
        // byte-idéntico): genitivo canónico DENTRO del match — proteger el
        // ordinal no basta porque standalonePartOfDayPattern robaría la parte
        // del día interior («de la mañana» → 09:00); requiere doctrina propia.
        val r = parse("las primeras horas de la mañana son las mejores")
        assertEquals("las son las mejores", r.title)
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    // ---- Regresiones (verdes desde RED) ----

    @Test fun citaMananaALas9_regresion() {
        val r = parse("cita mañana a las 9")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }

    @Test fun avisarPorLaMananaMisma_regresionC925() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(r.dueAt!!, zone))
    }
}

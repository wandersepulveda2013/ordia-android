package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.928: conector «para la» + parte del día («lo necesito para la mañana»,
 * «tenerlo listo para la noche», «para la tarde revisar el informe»). Forma
 * cotidianísima del habla de vencimientos intradía («lo necesito para la
 * mañana» = lo necesito a las 09:00), hermana de los conectores ya soportados
 * «a la|de la|por la|en la|entrando/entrada la» y del «para las 3» de hora
 * explícita (l.2109). Medida 6/6 PRE por la sonda efímera del ciclo
 * (`/tmp/probe928/ParaLaMananaProbe.kt`, motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00; lateral registrada FUERA en c.927): las
 * candidatas caían a dueAt=null (tarea olvidada) y, en el caso de «mañana»,
 * con DOBLE daño — el borrado genérico del token-fecha consumía «mañana» y
 * dejaba el residuo mutilado «para la» en el título («lo necesito para la»).
 * Doctrina SIMÉTRICA a los conectores hermanos: misma resolución (mañana→09:00,
 * tarde→15:00, noche→21:00, madrugada→04:00, fecha base si no hay ancla de
 * día) y mismo borrado del título. La simetría de riesgo se midió con sonda
 * (`/tmp/probe928/SymProbe.kt`): los conectores hermanos YA consumen en los
 * mismos contextos («el tren por la tarde sale tarde», «comprar flores por la
 * noche de bodas»), así que «para la» no introduce ninguna clase de riesgo
 * nueva. El artículo «la» es OBLIGATORIO: «para mañana» (sin artículo) es la
 * forma-fecha (+1d) y queda intacta. Determinista (regex), cero random, cero
 * IA fingida, cero UI.
 */
class NaturalTaskParserParaLaParteDelDiaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    private fun time(text: String) = DateRules.toLocalTime(parse(text).dueAt!!, zone)

    // ---- Positivos: «para la» + parte del día se resuelve y limpia el título ----

    @Test fun paraLaManana_resuelveYLimpia() {
        val r = parse("lo necesito para la mañana")
        assertEquals("lo necesito", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("lo necesito para la mañana"))
        assertEquals(LocalTime.of(9, 0), time("lo necesito para la mañana"))
    }

    @Test fun paraLaTarde_resuelveYLimpia() {
        val r = parse("necesito el informe para la tarde")
        assertEquals("necesito el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("necesito el informe para la tarde"))
        assertEquals(LocalTime.of(15, 0), time("necesito el informe para la tarde"))
    }

    @Test fun paraLaNoche_resuelveYLimpia() {
        val r = parse("tenerlo listo para la noche")
        assertEquals("tenerlo listo", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("tenerlo listo para la noche"))
        assertEquals(LocalTime.of(21, 0), time("tenerlo listo para la noche"))
    }

    @Test fun paraLaMadrugada_resuelveYLimpia() {
        val r = parse("llegar para la madrugada")
        assertEquals("llegar", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("llegar para la madrugada"))
        assertEquals(LocalTime.of(4, 0), time("llegar para la madrugada"))
    }

    @Test fun paraLaTardeLeading_resuelveYLimpia() {
        val r = parse("para la tarde revisar el informe")
        assertEquals("revisar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("para la tarde revisar el informe"))
        assertEquals(LocalTime.of(15, 0), time("para la tarde revisar el informe"))
    }

    @Test fun paraLaMananaDeManana_resuelveManana() {
        // trailing «de mañana» ya admitido por el patrón hermano: fecha +1d, hora 09:00
        val r = parse("tenerlo listo para la mañana de mañana")
        assertEquals("tenerlo listo", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("tenerlo listo para la mañana de mañana"))
        assertEquals(LocalTime.of(9, 0), time("tenerlo listo para la mañana de mañana"))
    }

    // ---- Re-pin legítimo: genitivo weekday se alinea con los conectores hermanos ----

    @Test fun repinParaLaMananaDelLunes_tituloLimpioComoHermanos() {
        // PRE: título 'una alarma para la mañana' (el conector no se consumía) con
        // fecha lunes 09:00 correcta. POST: simétrico al hermano «reunión por la
        // mañana del lunes» → 'reunión' (medido en la sonda del ciclo): el conector
        // se consume y el título queda limpio; fecha y hora IDÉNTICAS (lunes 09:00,
        // el default weekday ya era 09:00). Re-pin MÁS estricto, no se relaja nada.
        val r = parse("una alarma para la mañana del lunes")
        assertEquals("una alarma", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("una alarma para la mañana del lunes"))
        assertEquals(LocalTime.of(9, 0), time("una alarma para la mañana del lunes"))
    }

    // ---- Guards: contenido legítimo NO se toca (byte-idéntico PRE/POST) ----

    @Test fun guardParaMananaSinArticulo_formaFechaIntacta() {
        // «para mañana» (sin artículo) es la forma-fecha (+1d): la rama exige «la».
        val r = parse("hacerlo para mañana")
        assertEquals("hacerlo", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("hacerlo para mañana"))
    }

    @Test fun guardParaLaEmpresa_contenidoIntacto() {
        val r = parse("trabajar para la empresa")
        assertNull(r.dueAt)
        assertEquals("trabajar para la empresa", r.title)
    }

    @Test fun guardParaLaUniversidad_contenidoIntacto() {
        val r = parse("ahorrar para la universidad")
        assertNull(r.dueAt)
        assertEquals("ahorrar para la universidad", r.title)
    }

    // ---- Regresiones: hermanas de la familia parte-del-día intactas ----

    @Test fun regresionPorLaMananaMisma_c925_intacta() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("avisar a Juan por la mañana misma"))
    }

    @Test fun regresionMananaGenitivo_c927_intacta() {
        val r = parse("la mañana del accidente fue terrible")
        assertNull(r.dueAt)
        assertEquals("la mañana del accidente fue terrible", r.title)
    }

    @Test fun regresionPorLaManana_intacta() {
        val r = parse("reunión por la mañana")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("reunión por la mañana"))
        assertEquals(LocalTime.of(9, 0), time("reunión por la mañana"))
    }

    @Test fun regresionCitaMananaALas9_intacta() {
        val r = parse("cita mañana a las 9")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("cita mañana a las 9"))
    }
}

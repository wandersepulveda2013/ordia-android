package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Encuadre reflexivo de recordatorio ("que no se me olvide X", "que no se me
 * pase X", "no dejes que se me olvide X"): la forma más cotidiana de pedir un
 * aviso en español junto a "recuérdame X". Antes NO se reconocía: el encuadre
 * quedaba íntegro como residuo del título ("que no se me olvide comprar leche"
 * → título idéntico) y el recordatorio NUNCA se programaba pese a pedirse
 * expresamente → olvido. Simétrico al verbo imperativo (ciclo 58) y a los
 * infinitivos con clítico (c.447). Determinista, sin random, sin IA fingida.
 */
class NaturalTaskParserReflexiveReminderTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun queNoSeMeOlvideLimpiaEncuadreDelTitulo() {
        val result = NaturalTaskParser.parse("que no se me olvide comprar leche", now, zone)
        assertEquals("comprar leche", result.title)
        // Sin fecha no se falsifica vencimiento ni recordatorio.
        assertNull(result.dueAt)
        assertNull(result.reminderOffsetMinutes)
    }

    @Test fun queNoSeTeOlvideLimpiaEncuadreDelTitulo() {
        val result = NaturalTaskParser.parse("que no se te olvide sacar al perro", now, zone)
        assertEquals("sacar al perro", result.title)
    }

    @Test fun queNoSeNosOlvideLimpiaEncuadreDelTitulo() {
        val result = NaturalTaskParser.parse("que no se nos olvide la reunión", now, zone)
        assertEquals("la reunión", result.title)
    }

    @Test fun queNoSeMeOlvideConFechaProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("que no se me olvide llamar al banco mañana a las 10", now, zone)
        assertEquals("llamar al banco", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(10, 0), DateRules.toLocalTime(result.dueAt, zone))
        // Igual que "recuérdame X con fecha": se asume 30 min antes (convención).
        assertEquals(30, result.reminderOffsetMinutes)
    }

    @Test fun queNoSeMeOlvideSoloFechaEsHoraDelAviso() {
        // El encuadre es el ÚNICO contenido: la hora dada ES la hora del aviso
        // (offset 0, simétrico a "recuérdame mañana"), no una cita con nudge.
        val result = NaturalTaskParser.parse("que no se me olvide mañana a las 10", now, zone)
        assertNotNull(result.dueAt)
        assertEquals(0, result.reminderOffsetMinutes)
        assertTrue(result.title.isNotBlank())
    }

    @Test fun noSeMeOlvideSinQueInicialLimpiaTitulo() {
        val result = NaturalTaskParser.parse("no se me olvide pagar la luz", now, zone)
        assertEquals("pagar la luz", result.title)
    }

    @Test fun noDejesQueSeMeOlvideLimpiaTitulo() {
        val result = NaturalTaskParser.parse("no dejes que se me olvide pagar la luz", now, zone)
        assertEquals("pagar la luz", result.title)
    }

    @Test fun queNoSeMePaseLimpiaTitulo() {
        val result = NaturalTaskParser.parse("que no se me pase recoger el paquete", now, zone)
        assertEquals("recoger el paquete", result.title)
    }

    @Test fun queNoSeMePasenPluralLimpiaTitulo() {
        val result = NaturalTaskParser.parse("que no se me pasen las citas", now, zone)
        assertEquals("las citas", result.title)
    }

    // Contra-regresión: el pretérito "se me olvidó" es CONTENIDO (confiesa un
    // olvido pasado), no petición de aviso; no debe borrarse del título.
    @Test fun seMeOlvidoPreteritoSeConservaComoContenido() {
        val result = NaturalTaskParser.parse("se me olvidó comprar leche", now, zone)
        assertEquals("se me olvidó comprar leche", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    // "no vaya a ser que se me pase/olvide X": modismo literal inequívoco de
    // aviso (nunca contenido); también se borra del título y activa el aviso.
    @Test fun noVayaASerQueSeMePaseLimpiaTitulo() {
        val result = NaturalTaskParser.parse("no vaya a ser que se me pase la cita", now, zone)
        assertEquals("la cita", result.title)
    }

    @Test fun noVayaASerQueSeMeOlvideConFechaProgramaRecordatorio() {
        val result = NaturalTaskParser.parse("no vaya a ser que se me olvide el cumpleaños de Ana mañana", now, zone)
        assertEquals("el cumpleaños de Ana", result.title)
        assertNotNull(result.dueAt)
        assertEquals(30, result.reminderOffsetMinutes)
    }

    // Contra-regresión: el subjuntivo suelto "que se me pase" SÍ puede ser
    // contenido ("espero que se me pase el dolor" = deseo, no petición de
    // aviso); sin el prefijo inequívoco "no vaya a ser que" no se toca.
    @Test fun esperoQueSeMePaseEsContenido() {
        val result = NaturalTaskParser.parse("espero que se me pase el dolor", now, zone)
        assertEquals("espero que se me pase el dolor", result.title)
        assertNull(result.reminderOffsetMinutes)
    }

    // Contra-regresión: el imperativo existente sigue intacto.
    @Test fun imperativoNoOlvidesSigueFuncionando() {
        val result = NaturalTaskParser.parse("no olvides pagar la luz mañana", now, zone)
        assertEquals("pagar la luz", result.title)
        assertEquals(30, result.reminderOffsetMinutes)
    }
}

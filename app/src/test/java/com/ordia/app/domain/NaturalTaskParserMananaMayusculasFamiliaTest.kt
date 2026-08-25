package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1098: familia día-relativo/parte-del-día en MAYÚSCULAS con tilde
 * («MAÑANA», «POR LA MAÑANA», «DESPUÉS DE MAÑANA»…) — paridad byte-idéntica
 * con las hermanas minúsculas. La semilla es la lateral (B) de la auditoría
 * c.1093: `(?i)` Java/Kotlin es ASCII-only, así que cada patrón con un
 * carácter acentuado (ma[nñ]ana, despu[eé]s, d[ií]a) deja de casar cuando
 * la letra acentuada viene en mayúscula.
 *
 * Medida PRE con sondas efímeras /tmp/probe1097/Probe.kt (base 5ad8b4c) y
 * /tmp/probe1098/Probe.kt + Probe3.kt (motor real, now=domingo 2026-08-23
 * 12:00 America/Santo_Domingo): 13/13 encargos en caps GAP (due=null +
 * título residual, o falso ancla EN EL PASADO: «cita MAÑANA A LAS 5» →
 * hoy 05:00, ya vencida al capturar — P1 evitar olvidos) y 4/4 guards
 * narrativos en caps ROTOS (ancla falsa + título mutilado: «EL LUNES EN LA
 * MAÑANA LLEGÓ EL PAQUETE» → due=lunes 09:00 + título sin «el lunes»;
 * «LAS PRIMERAS HORAS DE LA MAÑANA SON LAS MEJORES» → due=hoy 09:00).
 *
 * Fix: case-folding Unicode ((?i)→(?iu)) en los puntos medidos del pipeline
 * de la familia (ancla [mananaAsDate]/when de fecha, patrones de parte del
 * día, normalización «mañana siguiente», borrados y los guards narrativos
 * que protegen la parte del día como contenido) — paridad estricta con la
 * hermana minúscula pineada, ancla Y título Y guards desde la MISMA lectura.
 *
 * Conservador: pins de regresión a hermanas minúsculas incluidos (suite
 * completa); laterales FUERA pineadas abajo: recurrencias «los lunes» en
 * caps, meses en caps, «HOY» ASCII (ya casaba), conteos escritos.
 */
class NaturalTaskParserMananaMayusculasFamiliaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertParse(input: String, expectedDue: LocalDateTime?, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals(
            "«$input» debe resolver la MISMA fecha que su hermana minúscula",
            expectedDue,
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            }
        )
        assertEquals("«$input» debe limpiar el título como su hermana minúscula",
            expectedTitle, result.title)
    }

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    // --- Anclas de día relativo en caps (GAPs medidos) ---

    @Test
    fun mananaMayusculasSuelta() = assertParse(
        "llamar a mamá MAÑANA",
        LocalDateTime.of(2026, 8, 24, 9, 0), "llamar a mamá"
    )

    @Test
    fun mananaMayusculasPorLaManana() = assertParse(
        "llamar a mamá MAÑANA POR LA MAÑANA",
        LocalDateTime.of(2026, 8, 24, 9, 0), "llamar a mamá"
    )

    @Test
    fun pasadoMananaMayusculas() = assertParse(
        "pagar la luz PASADO MAÑANA",
        LocalDateTime.of(2026, 8, 25, 9, 0), "pagar la luz"
    )

    @Test
    fun mananaMayusculasALas5() = assertParse(
        "cita con el dentista MAÑANA A LAS 5",
        LocalDateTime.of(2026, 8, 24, 5, 0), "cita con el dentista"
    )

    @Test
    fun mananaMayusculasPorLaTarde() = assertParse(
        "terminar el informe MAÑANA POR LA TARDE",
        LocalDateTime.of(2026, 8, 24, 15, 0), "terminar el informe"
    )

    @Test
    fun aMananaMayusculas() = assertParse(
        "cita con el dentista a MAÑANA",
        LocalDateTime.of(2026, 8, 24, 9, 0), "cita con el dentista a"
    )

    @Test
    fun mananaTardeCompactaMayusculas() = assertParse(
        "reunión MAÑANA TARDE",
        LocalDateTime.of(2026, 8, 24, 15, 0), "reunión"
    )

    @Test
    fun despuesDeMananaMayusculas() = assertParse(
        "pagar la luz DESPUÉS DE MAÑANA",
        LocalDateTime.of(2026, 8, 25, 9, 0), "pagar la luz"
    )

    @Test
    fun antepasadoMananaMayusculas() = assertParse(
        "pagar la luz ANTEPASADO MAÑANA",
        LocalDateTime.of(2026, 8, 26, 9, 0), "pagar la luz"
    )

    @Test
    fun estaMananaMayusculas() = assertParse(
        "mi cita es ESTA MAÑANA",
        LocalDateTime.of(2026, 8, 23, 9, 0), "mi cita es"
    )

    @Test
    fun estaTardeMayusculasControlAscii() = assertParse(
        "hacer ejercicio ESTA TARDE",
        LocalDateTime.of(2026, 8, 23, 15, 0), "hacer ejercicio"
    )

    @Test
    fun valeMananaLlamoMayusculas() = assertParse(
        "vale, MAÑANA llamo",
        LocalDateTime.of(2026, 8, 24, 9, 0), "vale, llamo"
    )

    @Test
    fun mananaSiguienteMayusculas() = assertParse(
        "cita con el médico MAÑANA SIGUIENTE",
        LocalDateTime.of(2026, 8, 24, 9, 0), "cita con el médico"
    )

    @Test
    fun mananaSolaMayusculas() = assertParse(
        "MAÑANA",
        LocalDateTime.of(2026, 8, 24, 9, 0), "MAÑANA"
    )

    @Test
    fun aLas9DeLaMananaMayusculas() = assertParse(
        "reunión a las 9 de la MAÑANA",
        LocalDateTime.of(2026, 8, 23, 9, 0), "reunión"
    )

    // --- Guards narrativos en caps (rotos: ancla falsa + título mutilado) ---

    @Test
    fun laMananaSiguienteNarrativaMayusculas() =
        assertNarrativeIntact("LA MAÑANA SIGUIENTE ME DESPERTÉ TARDE")

    @Test
    fun esaMananaNarrativaMayusculas() =
        assertNarrativeIntact("ESA MAÑANA LLEGUÉ TARDE")

    @Test
    fun ordinalNarrativoMananaMayusculas() =
        assertNarrativeIntact("LAS PRIMERAS HORAS DE LA MAÑANA SON LAS MEJORES")

    @Test
    fun weekdayNarrativaIntercaladaMayusculas() =
        assertNarrativeIntact("EL LUNES EN LA MAÑANA LLEGÓ EL PAQUETE")

    @Test
    fun hoyNarrativaIntercaladaMayusculas() =
        assertNarrativeIntact("HOY EN LA MAÑANA LLEGÓ EL PAQUETE")

    @Test
    fun yaNarrativaIntercaladaMayusculas() = assertParse(
        "YA, LLEGÓ EL PAQUETE POR LA MAÑANA",
        LocalDateTime.of(2026, 8, 23, 9, 0), "YA, LLEGÓ EL PAQUETE"
    )

    @Test
    fun primeraHoraDeLaMananaMayusculas() = assertParse(
        "avisar a la primera hora de la MAÑANA",
        LocalDateTime.of(2026, 8, 23, 9, 0), "avisar"
    )
}

package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1035 — DELTA test-only DISJUNTO sobre la implementación convergente del
 * hermano (SU c.1027 «ya <pretérito>» narrativa, `d375d996`, + SU c.1030
 * delta `51e27af9`): colisión convergente TOTAL (mi guard `takeUnless` +
 * sufijo con pretérito + 17 tests = duplicado funcional del suyo; descartado
 * NO-destructivo, doctrina c.1014/c.1030). Se conservan SOLO los 3 casos que
 * sus 28 tests NO ejercitan, medidos VERDES con sonda efímera
 * `/tmp/probe1023/Probe.kt` contra la producción de la UNIÓN:
 *  - interacción «ya»-prefijo + ordinal narrativo SIN artículo («ya salí a
 *    última hora»): doble fuga (inmediatez «ya» + H4-prefijo c.1016) —
 *    sus tests de «última hora» no llevan «ya» y sus tests «ya» no llevan
 *    ordinal;
 *  - interacción «ya»-prefijo + ordinal narrativo CON artículo («ya me fui a
 *    la primera hora»): su único test con-artículo tiene el «ya» EN MEDIO
 *    («a la primera hora ya sonó la alarma»), no como prefijo;
 *  - superficie narrativa con el verbo «terminé» (1ª persona -é distinta de
 *    las ejercitadas: «llamé», «tomé», «pagué»).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoOrdinalDeltaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = NaturalTaskParser.parse(text, now, zone)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    @Test fun yaSaliAUltimaHora_esContenidoNarrativo() =
        assertNarrativeIntact("ya salí a última hora")

    @Test fun yaMeFuiALaPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("ya me fui a la primera hora")

    @Test fun yaTermineElInforme_esContenidoNarrativo() =
        assertNarrativeIntact("ya terminé el informe")
}

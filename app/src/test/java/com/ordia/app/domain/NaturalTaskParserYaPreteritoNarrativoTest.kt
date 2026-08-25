package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1027 — lateral «ya <pretérito inequívoco>» narrativa (registrada ABIERTA en
 * c.1019, medida PRE con sonda efímera `/tmp/probe1014/Probe3.kt` y re-medida
 * con `/tmp/probe1020/PreProbe.kt` sobre la base c.1019).
 *
 * La regla de inmediatez «ya» ([NaturalTaskParser.nowPattern], c.112) existe
 * para COMANDOS («avisar ya», «llámame ya» → ahora). Pero «ya» también abre
 * narrativa en pretérito («ya sonó la alarma», «ya pagué la luz»): ahí el
 * usuario REPORTA un hecho cumplido, no pide nada. Antes el ancla de
 * inmediatez casaba igual → doble daño P1:
 *  1. fecha FALSA (dueAt = AHORA): la anécdota se convierte en un compromiso
 *     que vence hoy, ensucia What Now / planificador / recordatorios;
 *  2. título MUTILADO: «ya» (contenido del usuario) se borraba del título.
 *
 * Fix: «ya» suelto (no «ya mismo») se suprime como ancla cuando el predicado
 * inmediato abre con pretérito inequívoco ([NaturalTaskParser.yaPreteriteNarrativeSuffix],
 * la misma lista cerrada de c.950: un encargo real jamás abre su predicado en
 * pretérito). Conservador a propósito (UNA lateral por ciclo, doctrina
 * anti-overreach c.615):
 *  - presente/infinitivo/fragmento nominal tras «ya» SIGUEN ancla («ya voy»,
 *    «ya llego», «ya llamo al banco», «avisar ya», «Comprar pan ya»);
 *  - formas ambiguas pretérito/presente («ya salimos») SIGUEN ancla;
 *  - clíticos MÚLTIPLES («ya me lo pagó») FUERA (pin byte-idéntico);
 *  - «ya» separado por coma de la cadena («ya, a primera hora, sonó…») FUERA
 *    (pin byte-idéntico).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull(r.dueAt)
        assertEquals(text, r.title)
    }

    private fun assertAnchorNow(text: String, expectedTitle: String) {
        val r = parse(text)
        assertEquals(now, r.dueAt)
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas narrativa (due=null + título íntegro) ----

    @Test fun yaSonoLaAlarma_esContenidoNarrativo() =
        assertNarrativeIntact("ya sonó la alarma")

    @Test fun yaPagueLaLuz_esContenidoNarrativo() =
        assertNarrativeIntact("ya pagué la luz")

    @Test fun yaLlegoElPaquete_esContenidoNarrativo() =
        assertNarrativeIntact("ya llegó el paquete")

    @Test fun yaLlameAlBanco_esContenidoNarrativo() =
        assertNarrativeIntact("ya llamé al banco")

    @Test fun yaMeTomeLaPastilla_esContenidoNarrativo() =
        assertNarrativeIntact("ya me tomé la pastilla")

    @Test fun yaVinoElTecnico_esContenidoNarrativo() =
        assertNarrativeIntact("ya vino el técnico")

    @Test fun aLaPrimeraHoraYaSonoLaAlarma_esContenidoNarrativo() =
        // Re-pin legítimo MÁS estricto del pin c.1019
        // `aLaPrimeraHoraYaSonoLaAlarma_pinLateralYa` (precedente c.925…c.1016):
        // la lateral «ya <pretérito>» se resuelve en este ciclo; sin el ancla
        // «ya» robando now, la narrativa ordinal H4 completa la protección
        // (due=null + título íntegro, ordinal narrativo suprimido en cascada).
        assertNarrativeIntact("a la primera hora ya sonó la alarma")

    // ---- Guards inmediatez real (byte-idénticos): el comando «ya» sigue = ahora ----

    @Test fun llamameYa_sigueAncla() = assertAnchorNow("llámame ya", "llámame")

    @Test fun avisarYa_sigueAncla() = assertAnchorNow("avisar ya", "avisar")

    @Test fun hazloYaMismo_sigueAncla() = assertAnchorNow("hazlo ya mismo", "hazlo")

    @Test fun yaVoy_sigueAncla() = assertAnchorNow("ya voy", "voy")

    @Test fun yaLlego_sigueAncla() = assertAnchorNow("ya llego", "llego")

    @Test fun venYaAhora_sigueAncla() = assertAnchorNow("ven ya ahora", "ven ahora")

    @Test fun comprarPanAhora_sigueAncla() = assertAnchorNow("comprar pan ahora", "comprar pan")

    @Test fun pagarLaLuzAhorita_sigueAncla() = assertAnchorNow("pagar la luz ahorita", "pagar la luz")

    // ---- Regresiones bivalentes (conservadoras): presente/ambigua tras «ya» ----

    @Test fun yaLlamoAlBanco_presenteSigueAncla() = assertAnchorNow("ya llamo al banco", "llamo al banco")

    @Test fun yaMismoVoy_sigueAncla() = assertAnchorNow("ya mismo voy", "voy")

    @Test fun yaSalimos_formaAmbiguaSigueAncla() = assertAnchorNow("ya salimos", "salimos")

    // ---- Pines byte-idénticos de laterales medidas FUERA (registradas) ----

    @Test fun yaMeLoPago_cliticosMultiplesResueltaEnC1029() =
        // Re-pin c.1029 (lateral clíticos múltiples RESUELTA): la cadena
        // proclítica admite hasta dos clíticos (cobertura en
        // NaturalTaskParserYaPreteritoNarrativoCliticosMultiplesTest).
        assertNarrativeIntact("ya me lo pagó")

    @Test fun yaComaAPrimeraHoraSonoLaAlarma_resueltaEnC1035() =
        // Re-pin c.1035 (lateral «ya, <adverbial>, <pretérito>» RESUELTA): la
        // guard admite UNA cláusula adverbial acotada entre comas (cobertura en
        // NaturalTaskParserYaPreteritoNarrativoComaAdverbialTest).
        assertNarrativeIntact("ya, a primera hora, sonó la alarma")
}

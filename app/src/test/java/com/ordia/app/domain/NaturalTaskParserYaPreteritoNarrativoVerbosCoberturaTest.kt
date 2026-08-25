package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1034: lateral «cobertura de verbos pretérito narrativo» de la lista
 * cerrada c.950 [NaturalTaskParser.preteriteNarrativeVerbAlternation] —
 * «devolvió», «confirmaron», «mandé» (descubrimiento registrado FUERA
 * en c.1033). La guard c.1027 [NaturalTaskParser.yaPreteriteNarrativeSuffix]
 * comparte la lista; al no estar estos pretéritos inequívocos, «ya
 * devolvió el libro» caía a la ruta genérica: ancla AHORA falsa (relato
 * de un hecho cumplido convertido en compromiso que vence hoy y ensucia
 * What Now) y título MUTILADO (sin su «ya») — mismo doble daño P1 que
 * c.1027/c.1033. Medida PRE con sonda efímera `/tmp/probe1034/Probe.kt`
 * (motor real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, HEAD `f636e8e7` [mi docs-close c.1033, PUSHED]):
 * 9/9 candidatas con ancla falsa + título mutilado; regresiones correctas
 * (comandos «devuelve/confirma/manda» anclan a su fecha, narrativa
 * c.1027/c.1033 intacta, ambigua «ya salimos» ancla, «ya mismo» intacto).
 * Fix mínimo (1 punto): se añaden las familias devolver/confirmar/mandar
 * (personas 1ª/3ª singular y 3ª plural + 2ª singular, pretérito inequívoco)
 * a la lista cerrada — mismo conservadurismo c.950: un encargo real jamás
 * abre su predicado en pretérito; las formas ambiguas no se tocan.
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoVerbosCoberturaTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val domingo: LocalDate = LocalDate.of(2026, 8, 23)
    private val now: Long = domingo.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(text: String) {
        val r = NaturalTaskParser.parse(text, now, zone)
        assertNull("sin ancla falsa: $text", r.dueAt)
        assertEquals("título intacto: $text", text, r.title)
    }

    private fun assertCommandAnchors(text: String, expectedTitle: String) {
        val r = NaturalTaskParser.parse(text, now, zone)
        assertNotNull("comando ancla: $text", r.dueAt)
        assertEquals("título comando: $text", expectedTitle, r.title)
    }

    // ---------- candidatas: pretérito inequívoco de las 3 familias ----------

    @Test fun yaDevolvioElLibro() = assertNarrativeIntact("ya devolvió el libro")
    @Test fun yaMeDevolvioElDinero() = assertNarrativeIntact("ya me devolvió el dinero")
    @Test fun yaDevolviLaHerramienta() = assertNarrativeIntact("ya devolví la herramienta")
    @Test fun yaConfirmaronLaCita() = assertNarrativeIntact("ya confirmaron la cita")
    @Test fun yaConfirmoElMedico() = assertNarrativeIntact("ya confirmó el médico")
    @Test fun yaMeConfirmaronLaReserva() = assertNarrativeIntact("ya me confirmaron la reserva")
    @Test fun yaMandeElCorreo() = assertNarrativeIntact("ya mandé el correo")
    @Test fun yaMandoElPaquete() = assertNarrativeIntact("ya mandó el paquete")
    @Test fun yaSeLoMande() = assertNarrativeIntact("ya se lo mandé")

    // ---------- regresiones comando: imperativo/infinitivo ANCLAN ----------

    @Test fun comandoDevuelveElLibro() = assertCommandAnchors("devuelve el libro mañana", "devuelve el libro")
    @Test fun comandoDevolverElLibro() = assertCommandAnchors("devolver el libro mañana", "devolver el libro")
    @Test fun comandoConfirmaLaCita() = assertCommandAnchors("confirma la cita a las 5", "confirma la cita")
    @Test fun comandoConfirmarLaReserva() = assertCommandAnchors("confirmar la reserva esta tarde", "confirmar la reserva")
    @Test fun comandoMandaElCorreo() = assertCommandAnchors("manda el correo a primera hora", "manda el correo")
    @Test fun comandoMandarElInforme() = assertCommandAnchors("mandar el informe el lunes", "mandar el informe")

    // ---------- regresiones narrativa c.1027/c.1033: intactas ----------

    @Test fun regresionYaSonoLaAlarma() = assertNarrativeIntact("ya sonó la alarma")
    @Test fun regresionYaMeLoPago() = assertNarrativeIntact("ya me lo pagó")
    @Test fun regresionYaMeTomeLaPastilla() = assertNarrativeIntact("ya me tomé la pastilla")

    // ---------- conservadurismo: ambigua y «ya mismo» ----------

    @Test fun ambiguaYaSalimosSigueAncla() {
        val r = NaturalTaskParser.parse("ya salimos", now, zone)
        assertEquals(now, r.dueAt)
        assertEquals("salimos", r.title)
    }

    @Test fun comandoYaMismo() {
        val r = NaturalTaskParser.parse("llamar al banco ya mismo", now, zone)
        assertEquals(now, r.dueAt)
        assertEquals("llamar al banco", r.title)
    }
}

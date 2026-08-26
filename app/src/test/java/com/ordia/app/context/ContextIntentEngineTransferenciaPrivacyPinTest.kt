package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// c.1198: PIN de privacidad deliberada — la lateral (a) del auditoría
// c.1197 (clase VIGESIMOTERCERA finanzas domésticas, «hacer la
// transferencia al casero el lunes») es RECHAZADA POR DISEÑO (falso
// gap): `ContextPrivacyFilter.blockedContentPatterns` descarta TODA
// notificación que contenga \btransferencia\b "antes de cualquier
// análisis y en todas las fuentes" (paso 1 del pipeline [analyze];
// el texto podría contener datos bancarios reales, mismo argumento
// que c.1029 contraseña: el podría-contener-el-secreto domina sobre
// el gate de keywords). TDD RED->fix->REVERT: un piso acotado
// «hacer la transferencia» no movería nada (el filtro manda antes
// que los pisos; y su hermana sin palabra bloqueada «hacer la remesa
// al casero el lunes» queda NULL por umbral <0.45 — el gap sería
// real pero el fix imposible sin tocar el filtro). Relajar el filtro
// para distinguir la acción («hacer la transferencia») del dato
// («hacer la transferencia de 500 euros a ES12…») es una ventana
// estrecha con riesgo de fuga en el título capturado — anti-overreach
// + datos sagrados: NO se toca el filtro. Esta clase pinea el
// comportamiento medido PRE (sonda efímera, 6/6 candidatas NULL):
//   TODA forma con \btransferencia\b sigue NULL (imperativa, envolvente,
//   declarativa, pretérito, negación, con keyword fuerte «pagar»).
//   Las hermanas sin palabra bloqueada («remesa», «envío de dinero»)
//   quedan NULL por UMBRAL (no privacidad) — documentado ABIERTO
//   (futura clase, otra lateral, UNA por ciclo).
//   Los pisos vecinos PAYMENT/TASK sin palabra bloqueada quedan intactos.
class ContextIntentEngineTransferenciaPrivacyPinTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(
            source = ContextCaptureSource.NOTIFICATION,
            rawText = text,
            timestampMs = 1_700_000_000_000L,
        ),
    )

    // ---------- Privacidad deliberada: \btransferencia\b -> NULL ----------

    @Test
    fun `imperativa hacer la transferencia queda NULL por privacidad deliberada`() {
        assertNull(analyze("hacer la transferencia al casero el lunes"))
    }

    @Test
    fun `envolvente tengo que hacer la transferencia queda NULL por privacidad`() {
        assertNull(analyze("tengo que hacer la transferencia al casero mañana"))
    }

    @Test
    fun `declarativa la transferencia al casero queda NULL por privacidad`() {
        assertNull(analyze("la transferencia al casero"))
    }

    @Test
    fun `preterito hice la transferencia queda NULL por privacidad`() {
        assertNull(analyze("hice la transferencia al casero ayer"))
    }

    @Test
    fun `keyword fuerte pagar la transferencia queda NULL por privacidad`() {
        assertNull(analyze("pagar la transferencia al casero el lunes"))
    }

    @Test
    fun `envolvente no olvides la transferencia queda NULL por privacidad`() {
        assertNull(analyze("no olvides hacer la transferencia mañana"))
    }

    // ---------- Hermanas sin palabra bloqueada: NULL por UMBRAL (abiertas) ----------

    @Test
    fun `hermana remesa queda NULL por umbral sin privacidad (lateral abierta)`() {
        assertNull(analyze("hacer la remesa al casero el lunes"))
    }

    @Test
    fun `hermana envio de dinero queda NULL por umbral sin privacidad (lateral abierta)`() {
        assertNull(analyze("hacer el envío de dinero al casero el lunes"))
    }

    // ---------- Regresiones: pisos vecinos sin palabra bloqueada intactos ----------

    @Test
    fun `pagar el alquiler sigue PAYMENT`() {
        val intent = analyze("pagar el alquiler el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `pagar la luz sigue PAYMENT`() {
        val intent = analyze("pagar la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `cobrar la nomina sigue TASK`() {
        val intent = analyze("cobrar la nómina el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `revisar el extracto sigue TASK`() {
        val intent = analyze("revisar el extracto esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `ingresar dinero sigue ERRAND`() {
        val intent = analyze("ingresar dinero en el cajero")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}

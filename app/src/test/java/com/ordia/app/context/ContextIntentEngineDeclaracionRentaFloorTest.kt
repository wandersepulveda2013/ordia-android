package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.863: piso «hacer la declaración de la renta» — candidata 5/7 de la sonda
 * persistida c.857 `tools/probe/EighthClassAdminProbe.kt` (OCTAVA clase de
 * gestiones de adulto; trámite anual, medición PRE sobre HEAD 65c5dd6 con
 * sonda efímera `/tmp/probe863/PreProbe.kt`: 6/6 declarativas NULL — olvido
 * silencioso P1, la declaración de la renta es EL trámite anual con multa —
 * mientras la envolvente «recuérdame hacer la declaración de la renta este
 * mes» ya enrutaba TASK vía candado c.613: asimetría de ruta hermana de
 * c.765…c.862). «hacer» es bivalente por excelencia y el objeto desnudo
 * «declaración» también («hacer una declaración de amor»/«hacer la
 * declaración jurada» — ambas medidas NULL y deben seguir NULL), así el
 * piso se ACOTA a la frase-objeto completa `declaraci[oó]n\s+de\s+la\s+renta`
 * (inequívoca: es el trámite fiscal; grafía [oó] admite la forma sin tilde,
 * precedente c.772 «tensi[oó]n»/c.859 «medicaci[oó]n»). Fix mínimo en DOS
 * puntos lockstep (lección c.616; CERO cambios en ContextIntent.kt: la
 * keyword TASK «hacer» ya lleva la frase al análisis, hermana de c.860/
 * c.862): piso en [hasStrongTaskImperative] + plantilla de título en
 * [extractTitle] (match arranca en el verbo, acuse/prefijo temporal
 * despojados, grafía del usuario preservada, doctrina c.653). Guards
 * heredados del patrón: negación («no hacer…», lookbehind `(?<!no )`),
 * duda («quizá…», hedge c.649: 0.45−0.3 → NULL), pasado («hice…»), verbo
 * ausente (sustantivo suelto) y bivalente («una declaración de amor»)
 * FUERA por la frase-objeto acotada. Sin cláusula dedicada en
 * [imperativeIsNegated]: keyword «hacer» 0.12 + bono temporal 0.1 = 0.22 <
 * umbral (misma aritmética que c.859/c.860/c.862). Kind decidido: TASK, en
 * deliberación contra PAYMENT/ERRAND/APPOINTMENT — presentar la declaración
 * es un trámite con plazo (hermano de «votar» c.752, «pasar la ITV» c.768,
 * «renovar el DNI» c.698, «tramitar el pasaporte» c.822), no un pago
 * («pagar la renta» —alquiler— es PAYMENT, distinto), no hay desplazamiento
 * ni cita concertada. Nota: «este mes» no ancla fecha (lateral parser
 * documentado c.845/c.852) y se preserva en el título (decisión c.856/
 * c.858: información conservada), así esa variante captura con dueAt=null.
 * Acotado deliberado (una forma por ciclo, medidas NULL en la sonda PRE):
 * «hacer la declaración este mes» (desnuda, bivalente), «declarar la
 * renta…» (verbo distinto), «presentar la declaración de la renta…» (verbo
 * distinto) y «hacer la renta este mes» (elipsis del objeto) quedan FUERA
 * como candidatas propias.
 */
class ContextIntentEngineDeclaracionRentaFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la declaración de la renta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la declaración de la renta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura este mes sin ancla preserva titulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la declaración de la renta este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la declaración de la renta este mes", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer mi declaración de la renta este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer mi declaración de la renta este mes", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, hacer la declaración de la renta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la declaración de la renta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana hacer la declaración de la renta", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la declaración de la renta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura grafia sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la declaracion de la renta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la declaracion de la renta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame hacer la declaración de la renta este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no hacer la declaración de la renta este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá hacer la declaración de la renta este mes", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hice la declaración de la renta ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "declaración de la renta", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente declaracion de amor descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer una declaración de amor mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion tomar la medicacion sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicación a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion hacer la compra sigue shopping`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la compra el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun `regresion pagar la renta sigue payment`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pagar la renta mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }
}

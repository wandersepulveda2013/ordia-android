package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.915 — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA, hermana directa de c.909/c.910/c.911: «sacar <cantidad> mil
 * <divisa>». Registrada FUERA de alcance en c.911 por ancla distinta
 * (cuantificador «mil» entre la cantidad y la divisa); medida NULL con
 * sonda efímera PRE (`/tmp/probe912/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`): 5/5 candidatas NULL («sacar 50 mil pesos
 * mañana», «sacar 2 mil euros del cajero el viernes», «sacar 5 mil
 * dólares antes del viaje», acuse «vale,», prefijo temporal), 7/7
 * guards NULL (negación, duda, pasado, declarativo, sin divisa,
 * «puntos», «mil» sin dígito), 8/8 regresiones HIT (pesos/euros/
 * dólares/dinero ERRAND hermanas, «pagar 50 mil pesos» PAYMENT,
 * «cambiar 50 mil pesos por dólares» TASK, basura/perro HOUSEHOLD).
 * Causa raíz (idéntica a c.909…c.911): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige la divisa inmediatamente tras el número y
 * el cuantificador «mil» rompe la adyacencia — «sacar 50 mil pesos»
 * ni llegaba al piso mientras «sacar 2000 pesos» (c.911) ya capturaba.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.911 — «sacar N
 * mil pesos» es la forma cotidiana del retiro de efectivo en LatAm
 * (Colombia/México/Argentina/Chile) y España («sacar 2 mil euros»);
 * doctrina c.842/c.862 «la diligencia gobierna». La ancla
 * cantidad+«mil»+divisa es inequívoca en posición de compromiso: no
 * colide con «pagar N mil pesos» (PAYMENT, verbo distinto), ni con
 * declarativos («la entrada cuesta 50 mil pesos» — sin imperativo el
 * piso no casa), ni con «sacar 5 mil puntos» (sin divisa), ni con la
 * bivalencia «mil» (el piso exige dígito + «mil» + divisa).
 *
 * Lockstep DOS puntos (lección c.616; CERO cambios en
 * [ContextIntent.kt] — las keywords-DIVISA «euro»/«dólar»/«dolar»/
 * «peso» ya existen desde c.909/c.910/c.911 y cubren por subcadena):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: `\d+(?:[.,]\d+)?\s+` →
 *     `\d+(?:[.,]\d+)?(?:\s+mil)?\s+` (la rama `dinero|efectivo` y las
 *     formas sin «mil» casan exactamente igual, cero reescritura);
 * (2) plantilla de título — la rama «sacar» de [extractTitle] admite
 *     el cuantificador («Sacar 50 mil pesos del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <N> mil <divisa>» (la keyword-DIVISA + el
 * bono temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.909/c.910/c.911).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «sacar mil euros» (cuantificador sin dígito) queda FUERA
 * (medida NULL en la sonda, OBS-P6 — ancla distinta); «medio millón»/
 * «un millón de pesos» quedan como laterales a medir (la lateral
 * «libras» la resolvió el hermano en su c.912; ciclo renumerado
 * c.912→c.914→c.915 por DOBLE colisión, convención c.857).
 */
class ContextIntentEngineSacarMilDivisaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos dos métodos) ───────────────────

    @Test
    fun `sacar cantidad mil divisa captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 50 mil pesos mañana")
        assertNotNull("«sacar 50 mil pesos mañana» debe capturar (NULL hasta c.915)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 50 mil pesos", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 2 mil euros del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 2 mil euros del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo mil divisa`() {
        // Acuse «vale, …» + destino «atm» (lección c.616; c.896).
        val r1 = analyze("vale, sacar 20 mil pesos del atm")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 20 mil pesos del atm", r1.title)

        // Prefijo temporal «mañana …» + divisa «dólares» (c.910).
        val r2 = analyze("mañana sacar 5 mil dólares")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 5 mil dólares", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL mil divisa`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 50 mil pesos mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 2 mil euros del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá saque 50 mil pesos mañana"))
        assertNull("narrativa pasado", analyze("saqué 50 mil pesos ayer"))
        assertNull("declarativo sin imperativo", analyze("la entrada cuesta 50 mil pesos"))
        assertNull("sin divisa no hay ancla", analyze("sacar 50 mil mañana"))
        assertNull("«puntos» no es divisa", analyze("sacar 5 mil puntos en el juego"))
        // Forma «mil euros» sin dígito: ancla distinta, FUERA de
        // alcance deliberado (OBS-P6 de la sonda).
        assertNull("«mil euros» sin dígito fuera de alcance", analyze("sacar mil euros mañana"))
        // Refuerzo c.916 (delta de colisión, medido NULL con sonda
        // efímera /tmp/probe915d sobre HEAD 321569a): número en letra
        // («dos mil pesos» — la rama cantidad exige \d+) y bivalencia
        // «mil» = agradecimiento («mil gracias») permanecen NULL.
        assertNull("número en letra sin dígito", analyze("sacar dos mil pesos mañana"))
        assertNull("«mil gracias» agradecimiento bivalente", analyze("mil gracias por todo"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas mil divisa`() {
        val pesos = analyze("sacar 2000 pesos mañana") // piso c.911
        assertNotNull(pesos)
        assertEquals(ContextIntentKind.ERRAND, pesos!!.kind)
        assertEquals("Sacar 2000 pesos", pesos.title)

        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)
        assertEquals("Sacar 50 euros", euros.title)

        val dolares = analyze("sacar 100 dólares mañana") // piso c.910
        assertNotNull(dolares)
        assertEquals(ContextIntentKind.ERRAND, dolares!!.kind)
        assertEquals("Sacar 100 dólares", dolares.title)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // Refuerzo c.916 (delta de colisión, medido HIT con sonda
        // efímera /tmp/probe915d sobre HEAD 321569a): la divisa
        // «libras» (c.912) combina con el cuantificador «mil»
        // (c.915) — el test c.912 pina «libras» SIN «mil» y este
        // test pina «mil» con pesos/euros/dólares; la combinación
        // queda fijada aquí.
        val librasMil = analyze("sacar 10 mil libras del atm")
        assertNotNull("divisa c.912 + cuantificador c.915", librasMil)
        assertEquals(ContextIntentKind.ERRAND, librasMil!!.kind)
        assertEquals("Sacar 10 mil libras del atm", librasMil.title)

        // «pagar N mil pesos» sigue siendo PAYMENT (verbo distinto,
        // keyword «pagar»). Medido HIT PAYMENT en la sonda PRE.
        val pago = analyze("pagar 50 mil pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar N mil pesos por dólares» sigue TASK (piso abierto
        // c.710). Medido HIT TASK en la sonda PRE.
        val cambiar = analyze("cambiar 50 mil pesos por dólares")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)

        // Quehaceres hermanos con «sacar» intactos (c.717/c.740).
        val basura = analyze("sacar la basura mañana")
        assertNotNull(basura)
        assertEquals(ContextIntentKind.HOUSEHOLD, basura!!.kind)
        val perro = analyze("sacar al perro esta tarde")
        assertNotNull(perro)
        assertEquals(ContextIntentKind.HOUSEHOLD, perro!!.kind)
    }
}

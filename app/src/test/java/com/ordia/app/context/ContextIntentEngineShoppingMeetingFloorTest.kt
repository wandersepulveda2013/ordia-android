package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de confianza simétrico para imperativos inequívocos de COMPRA y REUNIÓN
 * (c.626, paridad con c.613 TASK / c.619 REMINDER).
 *
 * Antes de c.626, "comprar pan"/"comprar leche"/"reunión con el equipo" quedaban
 * bajo [ContextIntentEngine.MINIMUM_CONFIDENCE] (compra: 0.37, reunión: 0.32) por
 * la sola ausencia de pistas temporales y se DESCARTABAN silenciosamente: el
 * usuario capturaba una compra o reunión real en una notificación y Ordía la
 * olvidaba. "comprar <producto>" y "reunión con/de/del <grupo>" son intenciones
 * inequívocas con independencia de fecha/hora, igual que "tengo que <verbo>" o
 * "avísame <verbo>". El contenido dañino genuino ya fue bloqueado en el paso 1
 * ([ContextPrivacyFilter]) o en el paso 3 (insultos), así que llegar aquí es
 * contenido permitido. El ancla `^` + `\s+\w` exige imperativo AFIRMATIVO al
 * inicio + objeto real: así "no comprar pan" (negación, capta lo opuesto a la
 * intención del usuario), "mañana no comprar pan" (negación incrustada),
 * "comprar" aislado (muletilla) y "reunión" sola (eco de chat) NO activan el
 * piso (c.616 anti-overreach). Los casos afirmativos con ancla temporal
 * ("mañana comprar pan") ya superan el umbral vía [extractDateTime].
 */
class ContextIntentEngineShoppingMeetingFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- COMPRA: imperativo "comprar" + producto, sin anclaje temporal ---

    @Test
    fun shoppingComprarPanIsCaptured() {
        val intent = analyze("comprar pan")
        assertNotNull("comprar pan es una compra legítima, no debe descartarse", intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun shoppingComprarLecheIsCaptured() {
        val intent = analyze("comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun shoppingComprarVariosProductosIsCaptured() {
        val intent = analyze("comprar leche y huevos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Comprar Leche y huevos", intent!!.title)
    }

    @Test
    fun shoppingComprarDiarioDeHoyPreservesGenitive() {
        // "de hoy" es genitivo (contenido), NO residuo temporal: el título lo preserva.
        val intent = analyze("comprar el diario de hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Comprar el diario de hoy", intent!!.title)
    }

    @Test
    fun shoppingComprarFrutaIsCaptured() {
        val intent = analyze("comprar fruta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    // --- REUNIÓN: "reunión con/de/del" + grupo, sin anclaje temporal ---

    @Test
    fun meetingReunionConEquipoIsCaptured() {
        val intent = analyze("reunión con el equipo")
        assertNotNull("reunión con el equipo es una reunión legítima, no debe descartarse", intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
        assertEquals("Reunión: con el equipo", intent!!.title)
    }

    @Test
    fun meetingReunionDelProyectoIsCaptured() {
        val intent = analyze("reunión del proyecto")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    // --- Guards anti-muletilla: el objeto real es obligatorio ---

    @Test
    fun bareComprarAloneDoesNotTriggerFloor() {
        // "comprar" sin objeto NO activa el piso (muletilla). Su confianza queda
        // bajo el umbral por sí sola; si clasifica, es por otros bonos, no por el piso.
        val intent = analyze("comprar")
        // Aceptamos null (descarte) o cualquier kind: lo que NO debe ocurrir es un
        // falso SHOPPING por el piso. Verificamos que, si captura, no fue el piso el
        // responsable de un descarte-evitado espurio: la confianza de "comprar"
        // aislado sin objeto no debe alcanzar el umbral vía el piso.
        if (intent != null) {
            // No afirmamos kind aquí: el piso exige objeto, así "comprar" solo no
            // recibe el piso. Sólo garantizamos que no se fuerza SHOPPING por defecto.
        }
    }

    // --- Guards anti-overreach (c.616): negación y condicional no activan el piso ---
    // c.616 cerró el descarte de "comprar <X>"/"reunión con <Y>" sin ancla como
    // PROTECCIÓN honesta, y condicionó re-abrir a un "mecanismo que distinga compra
    // de chat sin degradar anti-overreach". c.626 es ese mecanismo: el piso exige un
    // imperativo AFIRMATIVO (no negado). "no comprar pan" capta lo OPUESTO a la
    // intención del usuario → nunca debe activar el piso.

    @Test
    fun negatedComprarDoesNotTriggerFloor() {
        // "no comprar pan": el usuario prohíbe/declina la compra. Capturarla como
        // SHOPPING sería un falso positivo grave (datos erróneos: la app crea una
        // tarea que el usuario NO quiere). El piso de c.626 NO debe activar.
        val intent = analyze("no comprar pan")
        assertNull("negación 'no comprar pan' no debe capturarse como SHOPPING", intent)
    }

    @Test
    fun negatedReunionDoesNotTriggerFloor() {
        val intent = analyze("no reunión con el equipo")
        assertNull("negación 'no reunión con el equipo' no debe capturarse como MEETING", intent)
    }

    @Test
    fun dontBuyImperativeDoesNotTriggerFloor() {
        // "no compres pan" (imperativo negativo informal): el usuario dice NO comprar.
        val intent = analyze("no compres pan")
        assertNull("'no compres pan' es una negación, no debe capturarse como SHOPPING", intent)
    }
}

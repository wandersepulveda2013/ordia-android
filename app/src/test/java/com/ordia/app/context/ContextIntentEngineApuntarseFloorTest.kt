package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.856: reflexivo «apuntarse a <actividad>» — la candidata restante de la
 * sonda c.845 (`tools/probe/SeventhClassErrandProbe.kt`, 7ª clase: la
 * transitiva «apuntar a los niños al fútbol» SÍ captura NOTE desde c.714
 * pero la reflexiva era NULL). Medida 6/6 NULL en sonda efímera
 * post-c.855 sobre motor real (variantes: gimnasio/yoga/curso/club, acuse
 * «vale,…», prefijo temporal «mañana …»; re-verificado sobre HEAD c.855
 * antes de implementar). Sin piso, la keyword «apuntar» (subcadena de
 * «apuntarse», lección c.751) da base ~0.12–0.22 (< [MINIMUM_CONFIDENCE])
 * y el compromiso de inscripción se perdía en silencio (P1 olvido
 * silencioso — misma clase que c.714).
 * Fix: la alternativa del piso [NOTE_FLOOR] gana la rama reflexiva
 * `apuntarse\s+a(?:l| la| los| las)?\s+\w` (una forma por ciclo, doctrina
 * anti-overreach): «a» es OBLIGATORIA — sin ella «apuntarse un tanto»
 * (figurado: anotarse un punto) colaría; el objeto tras ella también lo
 * es («apuntarse» / «apuntarse a» aislados no casan). Kind decidido:
 * NOTE, por paridad con la hermana transitiva c.714 (mismo verbo, mismo
 * kind; el usuario "se apunta" = se anota a sí mismo) y coherencia con el
 * downstream ([ConfirmExternalSuggestionUseCase] la convierte en nota).
 * Acotado deliberado (candidatas propias si se miden, una por ciclo):
 *   - «anotarse a» (variante dialectal rioplatense del mismo reflexivo);
 *   - «apuntarme/apuntarte/apuntarnos» (otras personas gramaticales del
 *     enclítico — la natural bajo envolvente «recuérdame apuntarme…» ya
 *     la gobierna TASK vía c.613).
 * Keywords VERIFICADAS (cero cambios en `ContextIntent.kt`): «apuntar»
 * cubre «apuntarse» por subcadena (misma vía que «devolver»→«devolverle»
 * c.854). Negación: el lookbehind `(?<!no )` del piso basta — NOTE no
 * tiene vía de bono al umbral sin piso (máx. 0.22), igual que el piso
 * transitivo c.714 (sin cláusula dedicada en [imperativeIsNegated]).
 * Envolvente: «recuérdame apuntarse al gimnasio mañana» sigue gobernada
 * por TASK (c.613) — el piso está anclado (inicio/acuse/temporal), así
 * el verbo subordinado ni siquiera lo activa. Determinista (regex), sin
 * random, sin IA fingida.
 */
class ContextIntentEngineApuntarseFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «apuntarse a <actividad>» es un compromiso de inscripción ---

    @Test
    fun apuntarseAlGimnasioManana_capturesNoteWithDueAt() {
        val intent = analyze("apuntarse al gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun apuntarseAYogaElLunes_capturesNote() {
        val intent = analyze("apuntarse a yoga el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse a yoga", intent.title)
        assertNotNull(intent.dueAt)
    }

    // «esta semana» (sin calificador) NO ancla fecha en [extractDateTime]
    // (decisión deliberada del motor: no inventar fechas a partir de
    // «semana»/«mes»/«año» sueltos) y tampoco se despoja del título
    // (información preservada): la nota nace con la acotación temporal
    // visible en el título y sin dueAt.
    @Test
    fun apuntarseAUnCursoDeCocina_capturesNote() {
        val intent = analyze("apuntarse a un curso de cocina esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse a un curso de cocina esta semana", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun apuntarseAlClubElViernes_capturesNote() {
        val intent = analyze("apuntarse al club el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse al club", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun acuseValeApuntarse_capturesNote() {
        val intent = analyze("vale, apuntarse al gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse al gimnasio", intent.title)
    }

    @Test
    fun prefijoTemporalApuntarse_capturesNote() {
        val intent = analyze("mañana apuntarse al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntarse al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Regresiones: la hermana transitiva c.714 y la envolvente c.613 ---

    @Test
    fun transitivaApuntarALosNinos_sigueNote() {
        val intent = analyze("apuntar a los niños al fútbol")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntar a los niños al fútbol", intent.title)
    }

    @Test
    fun envolventeRecuerdameApuntarse_gobiernaTask() {
        val intent = analyze("recuérdame apuntarse al gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Apuntarse al gimnasio", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guards (contratos anti-overreach / anti-falsos-positivos) ---

    @Test
    fun negacionInmediata_descartada() {
        assertNull(analyze("no apuntarse al gimnasio"))
    }

    @Test
    fun dudaQuiza_descartada() {
        assertNull(analyze("quizá apuntarse al gimnasio"))
    }

    @Test
    fun narrativaPasado_descartada() {
        assertNull(analyze("me apunté al gimnasio ayer"))
    }

    @Test
    fun verboAislado_descartado() {
        assertNull(analyze("apuntarse"))
    }

    @Test
    fun aisladoConPreposicion_descartado() {
        assertNull(analyze("apuntarse a"))
    }

    @Test
    fun figuradoApuntarseUnTanto_descartado() {
        assertNull(analyze("apuntarse un tanto"))
    }
}

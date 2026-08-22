package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.855: dativo enclítico de SEGUNDA oleada («recoger/retirar/repostar» +
 * «le/les») — candidata MEDIDA del descubrimiento post-c.854 (registrada en
 * BACKLOG con evidencia; sonda efímera `/tmp/probe855/DativeSecondWaveProbe.kt`
 * sobre motor real: 5/5 NULL, re-verificado sobre HEAD 339d2de antes de
 * implementar). El piso dativo c.854 (`ERRAND_DATIVE_FLOOR`) quedó acotado a
 * «llevar/devolver», así las formas dativas de los verbos restantes de la
 * familia ERRAND seguían perdiéndose en silencio (olvido silencioso):
 *   - «recogerle al aeropuerto mañana» (recoger a una PERSONA — la más
 *     cotidiana: ir a buscar a alguien),
 *   - «retirarle el paquete a la vecina mañana» (dativo de beneficio),
 *   - «repostarle el coche a mi madre mañana».
 * Fix: el grupo de verbos del piso dativo se extiende a
 * `(?:llevar|devolver|recoger|retirar|repostar)les?` en los 3 puntos
 * lockstep (piso + cláusula de negación + plantilla de título, lección
 * c.616/c.751). «recoger/retirar/repostar» son los verbos monosémicos del
 * piso libre `ERRAND_VERBS` (c.639), sin figurados frecuentes con dativo —
 * el guard anti-figurado («la contraria»/«la delantera»/«ventaja») sigue
 * siendo específico de «llevar». Keywords VERIFICADAS (cero cambios en
 * `ContextIntent.kt`): «recoger» y «repostar» ya son keywords de ERRAND
 * (cubren sus dativos por subcadena, como «devolver»→«devolverle» c.854) y
 * la forma medida de «retirar» llega al análisis vía la keyword-OBJETO
 * «paquete» (una forma «retirarle el dinero…» sin keyword-objeto es
 * candidata propia si se mide — doctrina una forma por ciclo). Kind
 * heredado: ERRAND (misma gestión de desplazamiento/trámite que c.854).
 */
class ContextIntentEngineRecogerleRetirarleRepostarFloorTest {

    // ---- Capturas directas (piso dativo extendido) ----

    @Test
    fun `captura recogerle al aeropuerto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recogerle al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Recogerle al aeropuerto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura recogerle a la salida del trabajo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recogerle a la salida del trabajo esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Recogerle a la salida del trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural recogerles`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recogerles a la estación el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Recogerles a la estación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura retirarle el paquete`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "retirarle el paquete a la vecina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Retirarle el paquete a la vecina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura repostarle el coche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "repostarle el coche a mi madre mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Repostarle el coche a mi madre", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame recogerle al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Recogerle al aeropuerto", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no recogerle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no recogerle al aeropuerto mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `no repostarle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no repostarle el coche a mi madre mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá retirarle descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá retirarle el paquete a la vecina", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado le recogi descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "le recogí a Juan ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo dativo aislado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recogerle", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones del piso dativo c.854 y del piso genérico c.639 ----

    @Test
    fun `regresion llevarle el almuerzo c854`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle el almuerzo a papá mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarle el almuerzo a papá", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion figurado llevarle la contraria c854`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevarle la contraria a papá otra vez", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion recoger paquete piso generico c639`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recoger el paquete de correos mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }
}

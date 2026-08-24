package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.980: lateral ABIERTA del backlog (descubierta c.977 — la batería POST de
// «escríbeme esto» midió también esta familia) + COLISIÓN cycle-ID
// c.979/c.979 CONVERGENTE con el hermano (`34ffbea`): ambos resolvimos la
// misma fila (mismo nombre de clase). Comparación de versiones (doctrina
// duplicados): la producción de este run es SUPERSET estricto (las peladas
// «-melo» a secas reciben guía honesta SIN acción — doctrina de peladas
// c.976/c.977 — en vez del menú genérico; la del hermano sólo reconocía la
// pelada con «:» vacío) → producción conservada: la de este run; esta clase
// es la UNIÓN de ambas baterías (21 + 9 únicos del hermano = 30). Sonda efímera
// `/tmp/probe978/GuardameEstoMeloProbe.kt` (motor real vía tools/run_probe.sh,
// base 277add9):
//  PRE — 10/10 GAP (todas las capturas → menú genérico, action=NONE); 4/4
//        peladas → menú en vez de la guía honesta; 7/7 guards en NONE;
//        4/4 controles de captura existentes (c.976/c.977/c.972/c.969)
//        intactos.
// (a) «guárdame/guardame + esto/eso» (enclítico de «guarda», simétrico del
//     «escríbeme esto» c.977): «esto/eso» OBLIGATORIO — «guárdame el archivo»
//     NUNCA es captura.
// (b) deíctico fundido «-melo»: «escríbemelo:/apúntemelo:/anótamelo: …» EXIGE
//     «:» — «escríbemelo mañana»/«apúntemelo en la lista» ni siquiera entran
//     en la rama (lookahead en el prefijo).
class AssistantEngineGuardameMeloCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- (a) enclítico de «guarda» + «esto/eso» + «:» + contenido → CREATE_NOTE ----------

    @Test fun guardameEstoConDosPuntos_creaNota() {
        val answer = ask("guárdame esto: la cita es el martes")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la cita es el martes", answer.actionPayload)
    }

    @Test fun guardameEstoSinTilde_creaNota() {
        val answer = ask("guardame esto: el codigo es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el codigo es 4321", answer.actionPayload)
    }

    @Test fun guardameEsoConDosPuntos_creaNota() {
        val answer = ask("guárdame eso: pagar el alquiler el dia 5")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar el alquiler el dia 5", answer.actionPayload)
    }

    @Test fun guardameEstoMayusculas_creaNota() {
        val answer = ask("Guárdame esto: llamar a Ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    // ---------- (b) deíctico fundido «-melo» + «:» + contenido → CREATE_NOTE ----------

    @Test fun escribemeloConDosPuntos_creaNota() {
        val answer = ask("escríbemelo: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun escribemeloSinTilde_creaNota() {
        val answer = ask("escribemelo: el pasaporte caduca en mayo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el pasaporte caduca en mayo", answer.actionPayload)
    }

    @Test fun apuntemeloConDosPuntos_creaNota() {
        val answer = ask("apúntemelo: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun apuntameloSinTilde_creaNota() {
        val answer = ask("apuntamelo: renovar el DNI")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("renovar el DNI", answer.actionPayload)
    }

    @Test fun anotameloConDosPuntos_creaNota() {
        val answer = ask("anótamelo: el médico a las 9")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el médico a las 9", answer.actionPayload)
    }

    @Test fun anotameloSinTilde_creaNota() {
        val answer = ask("anotamelo: recoger el paquete")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("recoger el paquete", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun guardameEstoPelada_pideContenidoSinAccion() {
        val answer = ask("guárdame esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun guardameEsoPelada_pideContenidoSinAccion() {
        val answer = ask("guardame eso")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun escribemeloPelada_pideContenidoSinAccion() {
        val answer = ask("escríbemelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun apuntemeloPelada_pideContenidoSinAccion() {
        val answer = ask("apúntemelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: el enclítico desnudo / contenido real NUNCA es captura ----------

    @Test fun guardameElArchivo_noEsCaptura() {
        val answer = ask("guárdame el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameLosCambios_noEsCaptura() {
        val answer = ask("guardame los cambios")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardamePelada_noEsCaptura() {
        val answer = ask("guárdame")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeloManana_noEsCaptura() {
        val answer = ask("escríbemelo mañana")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun apuntemeloEnLaLista_noEsCaptura() {
        val answer = ask("apúntemelo en la lista")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeLoGuardes_noEsCaptura() {
        val answer = ask("quiero que me lo guardes")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeloBonito_noEsCaptura() {
        val answer = ask("escríbemelo bonito")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    // ---------- capturas únicas del hermano (c.979) conservadas en la UNIÓN ----------

    @Test fun apuntemeloSinTilde_creaNota() {
        val answer = ask("apuntemelo: renovar el dni")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("renovar el dni", answer.actionPayload)
    }

    @Test fun apuntameloOrtografico_creaNota() {
        val answer = ask("apúntamelo: la matricula es 9988")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la matricula es 9988", answer.actionPayload)
    }

    @Test fun anotemeloConDosPuntos_creaNota() {
        val answer = ask("anótemelo: recoger el paquete")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("recoger el paquete", answer.actionPayload)
    }

    // ---------- pelada con «:» vacío (del hermano, c.979) → guía honesta SIN acción ----------

    @Test fun escribemeloSoloConDosPuntos_pideContenidoSinAccion() {
        val answer = ask("escríbemelo:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards únicos del hermano (c.979) conservados en la UNIÓN ----------

    @Test fun guardameLaCarpeta_noEsCaptura() {
        val answer = ask("guardame la carpeta")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameEstoEnElArchivo_noEsCaptura() {
        val answer = ask("guárdame esto en el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeloASecas_noEsCaptura() {
        val answer = ask("escríbemelo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun apuntameloBien_noEsCaptura() {
        val answer = ask("apúntamelo bien")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeLoEscribas_noEsCaptura() {
        val answer = ask("quiero que me lo escribas")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}

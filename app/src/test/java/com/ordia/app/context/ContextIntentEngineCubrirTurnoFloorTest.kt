package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1149 (P1 olvido silencioso en captura pasiva): candidata (b) de la
 * clase DECIMOSÉPTIMA (vida laboral, sonda persistida
 * `tools/probe/SeventeenthClassWorkProbe.kt` c.1147, C7 — 1/1 NULL
 * medido). «cubrir el turno del sábado» se DESCARTABA (analyze → NULL):
 * «cubrir» no era keyword ni tenía piso y «turno» solo vive dentro de
 * alternancias de objeto de pisos («sacar (cita|turno|hora)» c.1117), no
 * como keyword. Olvido real: el turno descubierto deja colgado al
 * compañero/equipo y la ventana para avisar es corta.
 *
 * PRE medido con sonda efímera (motor real vía `tools/run_probe.sh`,
 * HEAD base 48c3ee6): 7/7 capturas NULL (pelada/temporal/posesivo/
 * indefinido/plural/prefijo temporal/acuse), envolventes TASK 0.45
 * intactas vía candado c.613, controles 8/8 NULL, regresiones 4/4 HIT.
 *
 * Fix lockstep TRES puntos (lección c.616/c.751), hermano EXACTO de
 * c.1117 «sacar (una)? (cita|turno|hora)» (objeto-acotado sobre verbo
 * bivalente): (1) keyword-VERB «cubrir» en ContextIntent TASK (gate
 * c.751: 0.12 sola inerte < umbral; el piso acotado da el score);
 * (2) piso acotado «cubrir (el|la|mi|tu|su|un|una)? turnos?» en
 * hasStrongTaskImperative (ancla ^|acuse|temporal + lookbehind «no »
 * heredados; objeto «turno» EXIGIDO cierra la bivalencia: «cubrir la
 * mesa/los gastos» fuera); (3) plantilla hermana matchCubrirTurno en
 * extractTitle (grafía preservada, doctrina c.653).
 *
 * Kind TASK (gestión laboral sin desplazamiento, hermana de «cambiar el
 * turno» TASK medido en la sonda; doctrina ERRAND c.842/c.862 gobierna
 * solo el desplazamiento). Determinista (regex), sin random, sin IA
 * fingida. Alcance: SOLO infinitivo «cubrir»; 1ª persona «cubro el
 * turno» queda lateral (UNA forma por ciclo, doctrina anti-overreach).
 */
class ContextIntentEngineCubrirTurnoFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «cubrir (el)? turno» es una tarea clara ---

    @Test
    fun cubrirTurnoDelSabado_capturesTaskWithDueAt() {
        val intent = analyze("cubrir el turno del sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir el turno"))
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cubrirTurnoManana_capturesTaskWithDueAtAndCleanTitle() {
        val intent = analyze("cubrir el turno mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cubrir el turno", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cubrirMiTurnoDelViernes_capturesTaskWithDueAt() {
        val intent = analyze("cubrir mi turno del viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir mi turno"))
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cubrirUnTurnoEstaSemana_capturesTask() {
        val intent = analyze("cubrir un turno esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir un turno"))
    }

    @Test
    fun cubrirTurnosFinDeSemana_capturesTaskPlural() {
        val intent = analyze("cubrir turnos el fin de semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir turnos"))
    }

    @Test
    fun mananaCubrirTurno_capturesTaskTemporalPrefix() {
        val intent = analyze("mañana cubrir el turno de la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir el turno"))
        assertNotNull(intent.dueAt)
    }

    @Test
    fun valeCubrirTurnoDeAna_capturesTaskAckPrefix() {
        val intent = analyze("vale, cubrir el turno de ana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertTrue(intent.title.startsWith("Cubrir el turno"))
    }

    // --- Envolventes: candado c.613 (pin, ya capturaban) ---

    @Test
    fun recuerdameCubrirTurno_remainsTaskWrapper() {
        val intent = analyze("recuérdame cubrir el turno del sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tengoQueCubrirTurno_remainsTaskWrapper() {
        val intent = analyze("tengo que cubrir el turno mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    // --- Guards (deben quedarse NULL) ---

    @Test
    fun negadaCubrirTurno_isNull() {
        assertNull(analyze("no cubrir el turno del sábado"))
    }

    @Test
    fun pasadoCubriTurno_isNull() {
        assertNull(analyze("cubrí el turno del sábado"))
    }

    @Test
    fun futuroCubrireTurno_isNull() {
        assertNull(analyze("cubriré el turno la semana que viene"))
    }

    @Test
    fun dudaSubjuntivoCubrirTurno_isNull() {
        assertNull(analyze("no sé si cubrir el turno del sábado"))
    }

    @Test
    fun bivalenteCubrirMesa_isNull() {
        assertNull(analyze("cubrir la mesa antes de cenar"))
    }

    @Test
    fun bivalenteCubrirGastos_isNull() {
        assertNull(analyze("cubrir los gastos del mes"))
    }

    @Test
    fun nominalTurnoDeNoche_isNull() {
        assertNull(analyze("el turno de noche es duro"))
    }

    @Test
    fun declarativoEmpiezoTurno_isNull() {
        assertNull(analyze("empiezo el turno de noche el lunes"))
    }

    // --- Regresiones (formas que YA capturan — pin byte-idéntico) ---

    @Test
    fun cambiarTurnoConMarta_remainsTask() {
        val intent = analyze("cambiar el turno con marta esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun sacarTurnoMedico_remainsTask() {
        val intent = analyze("sacar turno para el médico mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pedirDiaLibre_remainsTask() {
        val intent = analyze("pedir el día libre para el puente")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun confirmarGuardia_remainsTask() {
        val intent = analyze("confirmar la guardia del fin de semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}

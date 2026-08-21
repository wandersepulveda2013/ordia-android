package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura JVM de `FocusRecap` (identificado SIN TEST en la auditoría de c.818):
 * recap determinista del enfoque del día — reglas hermanas de GuardianEngine
 * (solo sesiones completadas, tope defensivo 180 min/sesión, día LOCAL por zona).
 * Determinista puro: sin random, sin IA, sin Android.
 */
class FocusRecapTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 8, 21)
    private val now = DateRules.toEpochMillis(today, LocalTime.NOON, zone)

    private fun task(id: Long, title: String = "tarea $id") = TaskEntity(id = id, title = title)

    private fun session(
        taskId: Long?,
        minutes: Int,
        completed: Boolean = true,
        startedAt: Long = DateRules.toEpochMillis(today, LocalTime.of(9, 0), zone)
    ) = FocusSessionEntity(taskId = taskId, startedAt = startedAt, actualMinutes = minutes, completed = completed)

    @Test
    fun soloSesionesCompletadasDeHoyCuentan() {
        val tasks = listOf(task(1), task(2))
        val sessions = listOf(
            session(1, 30),
            session(2, 20),
            session(1, 50, completed = false),
            session(1, 40, startedAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(20, 0), zone))
        )
        val recap = FocusRecap.today(tasks, sessions, now, zone)
        assertEquals(50, recap.totalMinutes)
        assertEquals(listOf(FocusRecap.TopTask(1, "tarea 1", 30), FocusRecap.TopTask(2, "tarea 2", 20)), recap.topTasks)
    }

    @Test
    fun topeDefensivo180MinutosPorSesion() {
        val recap = FocusRecap.today(listOf(task(1)), listOf(session(1, 400)), now, zone)
        assertEquals(180, recap.totalMinutes)
        assertEquals(180, recap.topTasks.single().minutes)
    }

    @Test
    fun sesionSinTareaOConTareaDesconocidaCuentaEnTotalPeroNoEnTop() {
        val sessions = listOf(
            session(null, 25),
            session(999, 15), // taskId huérfano: no hay TaskEntity con id 999
            session(1, 10)
        )
        val recap = FocusRecap.today(listOf(task(1)), sessions, now, zone)
        assertEquals(50, recap.totalMinutes)
        assertEquals(listOf(FocusRecap.TopTask(1, "tarea 1", 10)), recap.topTasks)
    }

    @Test
    fun tituloEnBlancoSaleDelTopPeroCuentaEnTotal() {
        val recap = FocusRecap.today(listOf(task(1, "  ")), listOf(session(1, 35)), now, zone)
        assertEquals(35, recap.totalMinutes)
        assertTrue(recap.topTasks.isEmpty())
    }

    @Test
    fun minutosNegativosSeAcotanACeroYTareasEnCeroSalenDelTop() {
        val recap = FocusRecap.today(listOf(task(1)), listOf(session(1, -10)), now, zone)
        assertEquals(0, recap.totalMinutes)
        assertTrue(recap.topTasks.isEmpty())
    }

    @Test
    fun topOrdenadoDescYLimitadoATres() {
        val tasks = listOf(task(1), task(2), task(3), task(4))
        val sessions = listOf(session(1, 10), session(2, 40), session(3, 25), session(4, 15))
        val recap = FocusRecap.today(tasks, sessions, now, zone)
        assertEquals(90, recap.totalMinutes)
        assertEquals(listOf(2L, 3L, 4L), recap.topTasks.map { it.taskId })
        assertEquals(3, recap.topTasks.size)
    }

    @Test
    fun laZonaDeterminaElDiaLocal() {
        // 2026-08-21 01:00 UTC = 2026-08-20 21:00 en America/Santo_Domingo (UTC-4):
        // la misma sesión es "ayer" o "hoy" según la zona recibida.
        val startedAtUtc = DateRules.toEpochMillis(LocalDate.of(2026, 8, 21), LocalTime.of(1, 0), ZoneId.of("UTC"))
        val tasks = listOf(task(1))
        val local = FocusRecap.today(tasks, listOf(session(1, 30, startedAt = startedAtUtc)), now, zone)
        assertEquals(0, local.totalMinutes)
        val utc = FocusRecap.today(tasks, listOf(session(1, 30, startedAt = startedAtUtc)), now, ZoneId.of("UTC"))
        assertEquals(30, utc.totalMinutes)
    }

    @Test
    fun humanMinutesFormateaMinutosHorasYAcotaNegativos() {
        assertEquals("45 min", FocusRecap.humanMinutes(45))
        assertEquals("2 h", FocusRecap.humanMinutes(120))
        assertEquals("1 h 35 min", FocusRecap.humanMinutes(95))
        assertEquals("0 min", FocusRecap.humanMinutes(0))
        assertEquals("0 min", FocusRecap.humanMinutes(-5))
    }
}

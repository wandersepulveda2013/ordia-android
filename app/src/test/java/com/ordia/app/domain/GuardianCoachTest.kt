package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class GuardianCoachTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(today, LocalTime.NOON, zone)

    @Test
    fun overdueWorkWinsOverEverythingElse() {
        val overdue = TaskEntity(
            id = 1,
            title = "Enviar informe",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val urgentToday = TaskEntity(
            id = 2,
            title = "Llamar proveedor",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(15, 0), zone),
            priority = TaskPriority.URGENT
        )

        val insight = GuardianCoach.insight(listOf(urgentToday, overdue), emptyList(), emptyList(), now, zone)

        assertEquals(1L, insight.taskId)
        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
    }

    @Test
    fun mildlyOverdueSameDayStaysGentle() {
        val lateToday = TaskEntity(
            id = 1,
            title = "Llamar al dentista",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(lateToday), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
        assertEquals(1L, insight.taskId)
    }

    @Test
    fun forgottenOverdueTaskBecomesFocusedAndSurfacesAge() {
        val forgotten = TaskEntity(
            id = 1,
            title = "Enviar propuesta",
            dueAt = DateRules.toEpochMillis(today.minusDays(4), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(forgotten), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertEquals(1L, insight.taskId)
        assertTrue(insight.message.contains("4 días"))
    }

    @Test
    fun forgottenOverdueUsesWeeksLabelPastSevenDays() {
        val forgotten = TaskEntity(
            id = 1,
            title = "Renovar póliza",
            dueAt = DateRules.toEpochMillis(today.minusDays(14), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(forgotten), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertTrue(insight.message.contains("2 semanas"))
    }

    @Test
    fun forgottenOverdueGroupSurfacesOldestAge() {
        val oldest = TaskEntity(
            id = 1,
            title = "Tarea muy atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val recent = TaskEntity(
            id = 2,
            title = "Tarea recién atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val insight = GuardianCoach.insight(listOf(oldest, recent), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        // nextBestTask desempata por prioridad: la URGENT (aunque menos atrasada)
        // se sugiere primero; el mensaje surface la edad de la MÁS antigua.
        assertEquals(2L, insight.taskId)
        assertTrue(insight.message.contains("3 días"))
        assertTrue(insight.message.contains("2"))
    }

    // Tarea en la bandeja SIN fecha, capturada hace 3 semanas y nunca agendada:
    // el coach debe rescatarla como "olvidada" (no un "siguiente paso" genérico),
    // porque la recuperación de tareas olvidadas solo miraba las vencidas (con
    // dueAt) y dejaba pasar las que no tienen fecha pese a llevar mucho tiempo.
    @Test
    fun staleInboxTaskSurfacesAsForgottenRecovery() {
        val stale = TaskEntity(
            id = 1,
            title = "Idea capturada hace 3 semanas",
            createdAt = DateRules.toEpochMillis(today.minusDays(21), LocalTime.of(9, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(stale), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertEquals(1L, insight.taskId)
        assertTrue(insight.message.contains("3 semanas"))
    }

    // Una tarea de la bandeja capturada hoy no es "olvidada": sigue siendo un
    // "siguiente paso" normal. Evita falsos positivos del nudge de rescate.
    @Test
    fun freshInboxTaskDoesNotTriggerRecovery() {
        val fresh = TaskEntity(id = 1, title = "Captura de hoy", createdAt = now)
        val insight = GuardianCoach.insight(listOf(fresh), emptyList(), emptyList(), now, zone)

        assertNotEquals("RECUPERA EL CONTROL", insight.eyebrow)
    }

    // El umbral de "olvidada" en bandeja es más alto que el de vencida (que
    // tiene una fecha incumplida): una tarea sin fecha lleva 6 días (< 7) y
    // todavía no se considera olvidada.
    @Test
    fun staleInboxBelowThresholdStaysGeneric() {
        val almostStale = TaskEntity(
            id = 1,
            title = "Captura de hace 6 días",
            createdAt = DateRules.toEpochMillis(today.minusDays(6), LocalTime.of(9, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(almostStale), emptyList(), emptyList(), now, zone)

        assertNotEquals("RECUPERA EL CONTROL", insight.eyebrow)
    }

    // Algo que vence hoy (aunque sea normal) tiene prioridad sobre el rescate
    // de la bandeja: el nudge de "olvidada" no debe robarle el lugar a lo que
    // sí tiene fecha hoy. La tarea de hoy se sugiere como "siguiente paso".
    @Test
    fun dueTodayTaskBeatsStaleInboxRecovery() {
        val stale = TaskEntity(
            id = 1,
            title = "Idea olvidada",
            createdAt = DateRules.toEpochMillis(today.minusDays(21), LocalTime.of(9, 0), zone)
        )
        val dueToday = TaskEntity(
            id = 2,
            title = "Vence hoy",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(18, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(stale, dueToday), emptyList(), emptyList(), now, zone)

        assertNotEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(2L, insight.taskId)
    }

    // La edad de la tarea olvidada en bandeja usa la misma etiqueta legible
    // que las vencidas: 14 días → "2 semanas".
    @Test
    fun staleInboxUsesWeeksLabel() {
        val stale = TaskEntity(
            id = 1,
            title = "Idea de hace 2 semanas",
            createdAt = DateRules.toEpochMillis(today.minusDays(14), LocalTime.of(9, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(stale), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertTrue(insight.message.contains("2 semanas"))
    }

    // Varias tareas olvidadas en la bandeja: el mensaje surface el recuento y
    // la edad de la más antigua, y sugiere la mejor de ellas (nextBestTask).
    @Test
    fun multipleStaleInboxSurfacesCountAndOldestAge() {
        val oldest = TaskEntity(
            id = 1,
            title = "Idea más antigua",
            createdAt = DateRules.toEpochMillis(today.minusDays(21), LocalTime.of(9, 0), zone)
        )
        val newer = TaskEntity(
            id = 2,
            title = "Idea menos antigua",
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(oldest, newer), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertTrue(insight.message.contains("2 tareas"))
        assertTrue(insight.message.contains("3 semanas"))
    }

    // Un compromiso agendado cuyo hueco pasó hace días (sin atraso de plazo) es
    // el "olvido silencioso" (isMissedStart, c.201): el usuario le dio un hueco y
    // se le pasó. El coach debe rescatarlo como "RECUPERA EL CONTROL" (igual que
    // las vencidas y las capturas arrinconadas), no como un "siguiente paso"
    // genérico. Antes este tercer olvido honesto no se reencuadraba en la
    // superficie más visible de recuperación, aunque sí lo hacían el asistente
    // ("¿qué olvidé?") y el nudge del guardián.
    @Test
    fun forgottenMissedStartSurfacesAsRecovery() {
        val missedStart = TaskEntity(
            id = 1,
            title = "Llamar al cliente",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(missedStart), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertEquals(1L, insight.taskId)
        assertTrue(insight.message.contains("3 días"))
    }

    // Un compromiso cuyo hueco pasó hoy (mismo día) aún no es "olvidado": el
    // coach lo recupera con tono suave (GENTLE), igual que una vencida del mismo
    // día, en vez de señalarlo como olvidado.
    @Test
    fun recentMissedStartStaysGentle() {
        val recent = TaskEntity(
            id = 1,
            title = "Revisar contrato",
            startAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(recent), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
        assertEquals(1L, insight.taskId)
    }

    // Un compromiso olvidado (hueco pasado) prevalece sobre una captura
    // arrinconada de la bandeja: agendarlo lo hace una señal más fuerte de
    // recuperación que una idea vaga sin fecha. nextBestTask ya ordena
    // isMissedStart por encima de isStaleInbox; el coach lo respeta.
    @Test
    fun missedStartBeatsStaleInboxRecovery() {
        val missedStart = TaskEntity(
            id = 1,
            title = "Compromiso olvidado",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val stale = TaskEntity(
            id = 2,
            title = "Idea de hace 3 semanas",
            createdAt = DateRules.toEpochMillis(today.minusDays(21), LocalTime.of(9, 0), zone)
        )
        val insight = GuardianCoach.insight(listOf(missedStart, stale), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(1L, insight.taskId)
    }

    // Algo que vence hoy y es urgente prevalece sobre el rescate de un compromiso
    // cuyo hueco pasó (plazo aún no vencido): la deadline de hoy es más crítica.
    @Test
    fun dueTodayUrgentBeatsMissedStartRecovery() {
        val missedStart = TaskEntity(
            id = 1,
            title = "Compromiso olvidado",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val urgentToday = TaskEntity(
            id = 2,
            title = "Llamar proveedor",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(15, 0), zone),
            priority = TaskPriority.URGENT
        )
        val insight = GuardianCoach.insight(listOf(missedStart, urgentToday), emptyList(), emptyList(), now, zone)

        assertNotEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(2L, insight.taskId)
    }

    // La edad del compromiso olvidado usa la misma etiqueta legible que las
    // vencidas y las capturas: 14 días → "2 semanas".
    @Test
    fun missedStartUsesWeeksLabel() {
        val missedStart = TaskEntity(
            id = 1,
            title = "Compromiso muy olvidado",
            startAt = DateRules.toEpochMillis(today.minusDays(14), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(missedStart), emptyList(), emptyList(), now, zone)

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertTrue(insight.message.contains("2 semanas"))
    }

    @Test
    fun pendingHabitIsSuggestedWhenTasksAreClear() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val insight = GuardianCoach.insight(emptyList(), listOf(habit), emptyList(), now, zone)

        assertEquals("Leer diez minutos", insight.title)
        assertNotNull(insight.message)
    }

    // Una tarea vencida hace 2 días consultada ANTES de la hora de su
    // vencimiento: el recuento de calendario debe dar 2 (FOCUSED, olvidada),
    // no 1 por la antigua aritmética (now - dueAt)/24h que bajaba a GENTLE.
    @Test
    fun twoDaysOverdueBeforeDueTimeIsStillFocused() {
        val overdue = TaskEntity(
            id = 1,
            title = "Pagar factura",
            dueAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        // Consulta a las 7 a.m.: 2 días de calendario pero <2·24h en milisegundos.
        val earlyNow = DateRules.toEpochMillis(today, LocalTime.of(7, 0), zone)
        val insight = GuardianCoach.insight(listOf(overdue), emptyList(), emptyList(), earlyNow, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertTrue(insight.message.contains("2 días"))
    }

    // La edad se cuenta en días de calendario por la zona del usuario: una
    // zona con horario de verano (DST) donde un "día" no son 24 h no debe
    // desajustar la etiqueta. La tarea vence hace 3 días → "3 días".
    @Test
    fun overdueAgeIsCalendarDayCountAcrossDstBoundary() {
        val dstZone = ZoneId.of("America/New_York")
        // 9 de marzo de 2026: al día siguiente (10) entra el horario de verano
        // (forward 1 h), por lo que el tramo cruza un día de 23 h.
        val dstToday = LocalDate.of(2026, 3, 12)
        val overdue = TaskEntity(
            id = 1,
            title = "Renovar suscripción",
            dueAt = DateRules.toEpochMillis(dstToday.minusDays(3), LocalTime.of(9, 0), dstZone),
            priority = TaskPriority.NORMAL
        )
        val dstNow = DateRules.toEpochMillis(dstToday, LocalTime.of(8, 0), dstZone)
        val insight = GuardianCoach.insight(listOf(overdue), emptyList(), emptyList(), dstNow, dstZone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertTrue(insight.message.contains("3 días"))
    }

    // c.164: una tarea atrasada que el usuario YA está ejecutando ahora mismo no
    // debe aparecer como "comienza por esta" en la tarjeta de insight del coach.
    // Mismo bug que GuardianEngine (c.163), en otra superficie. El coach debe
    // nombrar otra atrasada recuperable; si todas están en curso, cae al insight
    // siguiente en vez de pedir "empezar" lo que ya se hace.
    private fun inProgressOverdueTask(id: Long, title: String, dueAt: Long, startAt: Long): TaskEntity =
        TaskEntity(
            id = id,
            title = title,
            dueAt = dueAt,
            startAt = startAt,
            durationMinutes = 120,
            priority = TaskPriority.URGENT,
            status = TaskStatus.IN_PROGRESS
        )

    @Test
    fun inProgressOverdueTaskIsNotNamedAsRecoverable() {
        // now = mediodía; la atrasada-en-curso empezó a las 11:00 (ventana 11–13).
        val inProgress = inProgressOverdueTask(
            id = 10,
            title = "Reunión que se alargó",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            startAt = DateRules.toEpochMillis(today, LocalTime.of(11, 0), zone)
        )
        val pendingOverdue = TaskEntity(
            id = 20,
            title = "Pagar luz",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(10, 0), zone),
            durationMinutes = 30,
            priority = TaskPriority.NORMAL
        )

        val insight = GuardianCoach.insight(listOf(inProgress, pendingOverdue), emptyList(), emptyList(), now, zone)

        assertEquals(20L, insight.taskId)
        assertEquals("Pagar luz", insight.title)
    }

    @Test
    fun allOverdueInProgressFallsThroughToNextInsight() {
        // Solo hay tareas atrasadas y TODAS están en curso: el coach no debe
        // insistir con "RECUPERA EL CONTROL / comienza por esta". Cae al insight
        // de prioridad de hoy (una tarea para hoy, no atrasada, no en curso).
        val onlyInProgressOverdue = inProgressOverdueTask(
            id = 10,
            title = "Reunión que se alargó",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            startAt = DateRules.toEpochMillis(today, LocalTime.of(11, 0), zone)
        )
        val urgentToday = TaskEntity(
            id = 2,
            title = "Llamar proveedor",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(15, 0), zone),
            priority = TaskPriority.URGENT
        )

        val insight = GuardianCoach.insight(listOf(onlyInProgressOverdue, urgentToday), emptyList(), emptyList(), now, zone)

        assertNotEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(2L, insight.taskId)
    }
}

package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // c.622 — paridad con GuardianEngine.withOverdueTail (c.621): la tarjeta del
    // coach (superficie MÁS visible) decía "Tienes N tareas atrasadas y la más
    // antigua lleva X" SIN distinguir cuántas eran URGENTES. Mismo camuflaje que
    // c.621 cerró en el nudge: un usuario con varias atrasadas urgentes leía el
    // mismo conteo plano que si todas fueran banales, justo cuando "detección de
    // vencidas importantes" más ayuda hace. Sin nueva pantalla: mejora el mensaje.
    @Test
    fun forgottenOverdueGroupSurfacesUrgentCountWhenThereAreUrgents() {
        val oldest = TaskEntity(
            id = 1, title = "Tarea muy atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val recent = TaskEntity(
            id = 2, title = "Tarea recién atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val normal = TaskEntity(
            id = 3, title = "Otra atrasada banal",
            dueAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(oldest, recent, normal), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("3 tareas atrasadas"))
        assertTrue("Debe señalar cuántas son urgentes (2 urgentes)", insight.message.contains("2 urgentes"))
    }

    @Test
    fun forgottenOverdueGroupSurfacesSingleUrgentLabel() {
        val urgent = TaskEntity(
            id = 1, title = "Atrasada urgente",
            dueAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val normal = TaskEntity(
            id = 2, title = "Atrasada banal",
            dueAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(urgent, normal), emptyList(), emptyList(), now, zone)

        assertTrue("Singular: 1 urgente", insight.message.contains("1 urgente"))
    }

    @Test
    fun forgottenOverdueGroupDoesNotSignalUrgentsWhenNoneAreUrgent() {
        val a = TaskEntity(
            id = 1, title = "Atrasada A",
            dueAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val b = TaskEntity(
            id = 2, title = "Atrasada B",
            dueAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(a, b), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("2 tareas atrasadas"))
        assertFalse("Sin urgentes no debe añadir el marcado", insight.message.contains("urgente"))
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

    // c.623 — paridad con c.622 (atrasadas) y c.621 (nudge): la rama de
    // huecos pasados (missedStart) TAMBIÉN tenía mensaje plural con conteo
    // plano ("Tienes N compromisos cuyo hueco pasó...") SIN distinguir
    // urgentes. Un hueco pasado URGENTE es señal crítica (se agendó a
    // propósito y el turno ya pasó), así que su urgencia merece visibilidad
    // como en las atrasadas. Sin nueva pantalla: mejora el mensaje.
    @Test
    fun missedStartGroupSurfacesUrgentCountWhenThereAreUrgents() {
        val oldest = TaskEntity(
            id = 1, title = "Hueco urgente olvidado",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.URGENT
        )
        val recent = TaskEntity(
            id = 2, title = "Otro hueco urgente",
            startAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(2), LocalTime.of(18, 0), zone),
            priority = TaskPriority.URGENT
        )
        val normal = TaskEntity(
            id = 3, title = "Hueco banal",
            startAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(3), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(oldest, recent, normal), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("3 compromisos"))
        assertTrue("Debe señalar cuántas son urgentes (2 urgentes)", insight.message.contains("2 urgentes"))
    }

    @Test
    fun missedStartGroupSurfacesSingleUrgentLabel() {
        val urgent = TaskEntity(
            id = 1, title = "Hueco urgente",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.URGENT
        )
        val normal = TaskEntity(
            id = 2, title = "Hueco banal",
            startAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(2), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(urgent, normal), emptyList(), emptyList(), now, zone)

        assertTrue("Singular: 1 urgente", insight.message.contains("1 urgente"))
    }

    @Test
    fun missedStartGroupDoesNotSignalUrgentsWhenNoneAreUrgent() {
        val a = TaskEntity(
            id = 1, title = "Hueco A",
            startAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val b = TaskEntity(
            id = 2, title = "Hueco B",
            startAt = DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(2), LocalTime.of(18, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(a, b), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("2 compromisos"))
        assertFalse("Sin urgentes no debe añadir el marcado", insight.message.contains("urgente"))
    }

    // c.625 — cierra la paridad 4-superficies: la rama staleInbox (captura
    // arrinconada, sin fecha) era la ÚLTIMA superficie de recuperación basada
    // en tareas con conteo plano SIN distinguir urgentes. Un URGENT en la
    // bandeja sin fecha hace 2+ semanas (capturado como urgente, nunca
    // agendado) es un anti-olvido real: el usuario marcó la prioridad pero
    // nunca le dio hueco. Cerrarlo hace la invariante uniforme —toda tarjeta
    // de recuperación nombra urgentes— lo que SIMPLIFICA el modelo mental.
    @Test
    fun staleInboxGroupSurfacesUrgentCountWhenThereAreUrgents() {
        val oldUrgent = TaskEntity(
            id = 1, title = "Idea urgente arrinconada",
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val oldNormal = TaskEntity(
            id = 2, title = "Otra idea vieja",
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(oldUrgent, oldNormal), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("2 tareas"))
        assertTrue("Debe señalar cuántas son urgentes (1 urgente)", insight.message.contains("1 urgente"))
    }

    @Test
    fun staleInboxGroupSurfacesPluralUrgentLabel() {
        val a = TaskEntity(id = 1, title = "Urgente A", priority = TaskPriority.URGENT,
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone))
        val b = TaskEntity(id = 2, title = "Urgente B", priority = TaskPriority.URGENT,
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone))
        val c = TaskEntity(id = 3, title = "Normal C", priority = TaskPriority.NORMAL,
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone))
        val insight = GuardianCoach.insight(listOf(a, b, c), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("3 tareas"))
        assertTrue("2 urgentes", insight.message.contains("2 urgentes"))
    }

    @Test
    fun staleInboxGroupDoesNotSignalUrgentsWhenNoneAreUrgent() {
        val a = TaskEntity(id = 1, title = "Idea A", priority = TaskPriority.NORMAL,
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone))
        val b = TaskEntity(id = 2, title = "Idea B", priority = TaskPriority.LOW,
            createdAt = DateRules.toEpochMillis(today.minusDays(10), LocalTime.of(9, 0), zone))
        val insight = GuardianCoach.insight(listOf(a, b), emptyList(), emptyList(), now, zone)

        assertTrue(insight.message.contains("2 tareas"))
        assertFalse("Sin urgentes no debe añadir el marcado", insight.message.contains("urgente"))
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

    @Test
    fun manuallyInProgressOverdueWithoutActiveWindowIsNotNamedAsRecoverable() {
        // now = mediodía. Una atrasada marcada IN_PROGRESS a mano cuyo hueco
        // (8:00, 30 min) ya terminó: isInProgressNow=false (sin ventana activa)
        // pero isBeingWorkedOn=true (status). El coach debe excluiría igual que
        // a una en ventana activa: el usuario ya la está haciendo, nombra la
        // otra atrasada recuperable. Antes usaba el predicado parcial
        // isInProgressNow y la incluía → nudgiaba "comienza por esta" sobre lo
        // que ya se hace (divergencia con GuardianEngine.smallestOverdueAction,
        // c.280). Predicado canónico: isBeingWorkedOn.
        val beingWorked = TaskEntity(
            id = 10,
            title = "Informe que arrastré desde la mañana",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            startAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            durationMinutes = 30,
            priority = TaskPriority.URGENT,
            status = TaskStatus.IN_PROGRESS
        )
        val recoverable = TaskEntity(
            id = 20,
            title = "Pagar luz",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(10, 0), zone),
            durationMinutes = 30,
            priority = TaskPriority.NORMAL
        )

        val insight = GuardianCoach.insight(listOf(beingWorked, recoverable), emptyList(), emptyList(), now, zone)

        assertEquals(20L, insight.taskId)
        assertEquals("Pagar luz", insight.title)
    }

    // --- 4ª clase de olvido: compromiso vencido de conversación ---
    // La tarjeta de insight es la superficie MÁS visible de recuperación (el usuario la
    // ve siempre en la pantalla principal). Recuperaba los 3 olvidos de tareas (atrasada
    // / hueco incumplido / captura arrinconada) pero callaba el 4º — un compromiso
    // vencido extraído de una conversación (dueAt pasado, PENDING, sin convertir). c.286
    // lo cerró en el asistente (a demanda) y c.288 en el nudge del guardián (proactivo);
    // aquí se cierra en la tarjeta permanente. Sin él, un día sin vencidas ni huecos
    // pasados pero con una promesa vencida mostraba "SIGUIENTE PASO" o "TODO EN CALMA" —
    // mintiendo por omisión sobre un olvido real.

    private fun commitment(
        id: Long,
        action: String,
        dueAt: Long,
        actor: String = "yo",
        status: CommitmentReviewStatus = CommitmentReviewStatus.PENDING
    ) = CommitmentEntity(
        id = id, conversationId = 1,
        kind = CommitmentKind.SELF_COMMITMENT, owner = CommitmentOwner.SELF,
        actor = actor, action = action, dueAt = dueAt, confidence = 0.8f,
        reviewStatus = status, fingerprint = "fp$id", createdAt = dueAt
    )

    @Test
    fun compromisoVencido_seMuestraCuandoNoHayTareasOlvidadas() {
        // Sin tareas atrasadas ni con hueco pasado ni capturas arrinconadas, pero con un
        // compromiso vencido ("te llamo" debido hace 2 días, PENDING): la tarjeta debe
        // nombrarlo en vez de caer a "SIGUIENTE PASO"/"TODO EN CALMA". Callarlo es mentir
        // por omisión sobre la 4ª clase de olvido.
        val c = commitment(1, "te llamo", DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(12, 0), zone))
        val insight = GuardianCoach.insight(emptyList(), emptyList(), emptyList(), now, zone, commitments = listOf(c))

        assertTrue(insight.title.contains("te llamo"))
        assertTrue(insight.message.contains("Conversaciones"))
    }

    @Test
    fun compromisoVencido_nombraElMasAtrasadoPrimero() {
        // Dos compromisos vencidos: el más atrasado (3 días) encabeza la tarjeta, no el
        // de 1 día. Orden determinista de CommitmentRules.overduePendingSorted (dueAt asc).
        val older = commitment(1, "enviar propuesta", DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(12, 0), zone))
        val newer = commitment(2, "te llamo", DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(12, 0), zone))
        val insight = GuardianCoach.insight(emptyList(), emptyList(), emptyList(), now, zone, commitments = listOf(newer, older))

        assertTrue(insight.title.contains("enviar propuesta"))
        assertFalse(insight.title.contains("te llamo"))
    }

    @Test
    fun compromisoVencido_tareaAtrasadaTomaPrecedenciaYNoCallaElCompromiso() {
        // Una tarea atrasada es accionable directamente: encabeza la tarjeta (taskId de la
        // tarea, "RECUPERA EL CONTROL"). El compromiso vencido NO se calla: aparece como
        // cola informativa en el mensaje para no mentir por omisión (paridad con c.288).
        // No doble señalización: la acción primaria sigue siendo la tarea.
        val overdueTask = TaskEntity(
            id = 1, title = "Factura vencida",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val c = commitment(2, "te llamo", DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(12, 0), zone))
        val insight = GuardianCoach.insight(listOf(overdueTask), emptyList(), emptyList(), now, zone, commitments = listOf(c))

        assertEquals(1L, insight.taskId)
        assertEquals("Factura vencida", insight.title)
        assertTrue(insight.message.contains("te llamo"))
    }

    @Test
    fun compromisoVencido_huecoPasadoTomaPrecedenciaYNoCallaElCompromiso() {
        // Una tarea con hueco pasado (start pasado, due futuro) es accionable directamente:
        // encabeza la tarjeta. El compromiso vencido queda como cola informativa.
        val missed = TaskEntity(
            id = 1, title = "Llamar al banco",
            startAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val c = commitment(2, "enviar propuesta", DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(12, 0), zone))
        val insight = GuardianCoach.insight(listOf(missed), emptyList(), emptyList(), now, zone, commitments = listOf(c))

        assertEquals(1L, insight.taskId)
        assertEquals("Llamar al banco", insight.title)
        assertTrue(insight.message.contains("enviar propuesta"))
    }

    // --- 3.er olvido como cola: capturas de bandeja arrinconadas ---
    // La tarjeta de insight es la superficie MÁS visible de recuperación. c.410 cerró
    // en el nudge del guardián (GuardianEngine) que las ramas con acción nombrada
    // (atrasada / hueco pasado / compromiso vencido) añadían una cola informativa del
    // 3.er olvido (capturas arrinconadas ≥7 días sin fecha ni hueco) para no mentir
    // por omisión. La tarjeta usaba `withCommitmentTail` (4.º olvido) en esas mismas
    // ramas PERO callaba el 3.er olvido: un usuario con una tarea atrasada y seis
    // ideas arrinconadas leía "RECUPERA EL CONTROL: [atrasada]" sin SEÑAL de las seis
    // capturas olvidadas. Asimetría con el nudge y con la propia rama `staleInboxAction`
    // (que sólo dispara cuando la candidata #1 es ella misma arrinconada).

    private fun staleCapture(id: Long, daysOld: Int = 21, title: String = "Idea arrinconada $id") = TaskEntity(
        id = id,
        title = title,
        createdAt = DateRules.toEpochMillis(today.minusDays(daysOld.toLong()), LocalTime.of(9, 0), zone)
    )

    @Test
    fun tareaAtrasada_nombraAdemasLasCapturasArrinconadas() {
        // Una tarea atrasada encabeza la tarjeta (acción primaria). Además hay capturas
        // arrinconadas: la cola debe nombrar su conteo para no mentir por omisión
        // (paridad con el nudge del guardián c.410 y con `withCommitmentTail`).
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val tasks = listOf(overdue, staleCapture(2), staleCapture(3), staleCapture(4))
        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone)

        assertEquals(1L, insight.taskId)
        assertTrue("La cola debe nombrar el conteo de capturas arrinconadas", insight.message.contains("3 capturas"))
        assertTrue(insight.message.contains("bandeja"))
    }

    @Test
    fun huecoPasado_nombraAdemasLasCapturasArrinconadas() {
        // Un compromiso con hueco pasado (start pasado, due futuro) encabeza la tarjeta.
        // Además hay una única captura arrinconada: la cola la nombra (singular).
        val missed = TaskEntity(
            id = 1, title = "Llamar al banco",
            startAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            dueAt = DateRules.toEpochMillis(today.plusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val tasks = listOf(missed, staleCapture(2))
        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone)

        assertEquals(1L, insight.taskId)
        assertTrue("La cola debe nombrar la única captura arrinconada (singular)", insight.message.contains("1 captura"))
    }

    @Test
    fun tareaAtrasada_noInventaCapturasArrinconadasCuandoNoLasHay() {
        // Guard anti-falso-positivo: sin capturas arrinconadas, la cola no se añade
        // (no se inventa el 3.er olvido). Una captura reciente (< 7 días) no cuenta.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val fresh = TaskEntity(id = 2, title = "Idea de ayer", createdAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone))
        val insight = GuardianCoach.insight(listOf(overdue, fresh), emptyList(), emptyList(), now, zone)

        assertFalse("Sin capturas arrinconadas la cola no debe añadirse", insight.message.contains("captura"))
    }

    @Test
    fun compromisoVencido_comoRamaPrimaria_nombraAdemasLasCapturasArrinconadas() {
        // Sin tareas atrasadas ni con hueco pasado, la rama de compromiso vencido es la
        // primaria. Además hay capturas arrinconadas: la cola debe nombrarlas también
        // (paridad con el nudge del guardián c.410, que cerró esta misma rendija en la
        // rama `overdueCommitments.isNotEmpty()`).
        val c = commitment(1, "te llamo", DateRules.toEpochMillis(today.minusDays(2), LocalTime.of(12, 0), zone))
        val tasks = listOf(staleCapture(10), staleCapture(11))
        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone, commitments = listOf(c))

        assertTrue(insight.title.contains("te llamo"))
        assertTrue("La rama de compromiso debe nombrar también las capturas arrinconadas", insight.message.contains("2 capturas"))
    }

    @Test
    fun insightRespetaZonaDelUsuarioAlElegirSiguientePaso() {
        // c.627: GuardianCoach.insight acepta `zone` (la zona del usuario) y ya
        // calcula `today` con ella, PERO llamaba a TaskRules.nextBestTask(..., now)
        // SIN pasar `zone` en 4 sitios (recoverable/urgent/missedStart/pending).
        // nextBestTask → smartListComparator → timeRank → isDueToday usaba
        // ZoneId.systemDefault() (la zona del DISPOSITIVO), que puede diferir de la
        // del usuario. Cuando el dispositivo viaja a una zona adelantada, una tarea
        // que VENCE HOY en la zona del usuario cae a "mañana futuro" en la zona del
        // dispositivo → isDueToday(device)=false → su timeRank decae y otra tarea
        // (p. ej. una ALTA de la bandeja sin fecha) le roba el lugar en "SIGUIENTE
        // PASO". El usuario ve nombrada una captura genérica en vez de la cita de
        // hoy que debe proteger — el mismo defecto de clase c.604 (parámetro zone
        // existe pero se silencia en la sub-llamada), ahora en la superficie MÁS
        // visible de la app (la tarjeta del guardián). Aquí se fuerza la zona del
        // dispositivo a Asia/Tokyo (UTC+9, +13 h respecto a America/Santo_Domingo)
        // para reproducir la divergencia de forma determinista sin depender del
        // huso horario real de la JVM de CI.
        val deviceZone = java.time.ZoneId.of("Asia/Tokyo") // +13 h respecto a -04
        val previousDefault = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(deviceZone))
            // Consulta MUY temprano en el día del usuario (01:00) y cita al final
            // del mismo día (22:00). En la zona del usuario ambas son "hoy 29/7" →
            // isDueToday(user)=true → timeRank 3. En la zona del dispositivo
            // (Tokyo +13 h) la consulta son las 14:00 del 29/7 PERO la cita cae a
            // las 11:00 del 30/7 → isDueToday(device)=false y, sin startAt, su
            // timeRank decae a 0 (NORMAL sin ventana) — una ALTA de la bandeja
            // (rank 1) le roba el lugar en "SIGUIENTE PASO".
            val earlyNow = DateRules.toEpochMillis(today, LocalTime.of(1, 0), zone)
            val dueTonight = TaskEntity(
                id = 1, title = "Reunión de hoy",
                dueAt = DateRules.toEpochMillis(today, LocalTime.of(22, 0), zone),
                priority = TaskPriority.NORMAL
            )
            val highInbox = TaskEntity(
                id = 2, title = "Idea importante de la bandeja",
                priority = TaskPriority.HIGH,
                createdAt = DateRules.toEpochMillis(today, LocalTime.of(0, 30), zone)
            )
            val insight = GuardianCoach.insight(listOf(dueTonight, highInbox), emptyList(), emptyList(), earlyNow, zone)

            assertEquals("La zona del usuario debe decidir la cita: la que vence HOY manda sobre la bandeja", 1L, insight.taskId)
            assertTrue(insight.title.contains("Reunión de hoy"))
        } finally {
            java.util.TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun compromisoVencido_ignoraCompromisoFuturoORevisado() {
        // Un compromiso con dueAt futuro, o ya CONVERTED/DISMISSED, no es un olvido: no
        // debe aparecer en la tarjeta. Guard anti-falso-positivo.
        val future = commitment(1, "te llamo", DateRules.toEpochMillis(today.plusDays(1), LocalTime.of(12, 0), zone))
        val converted = commitment(2, "enviar propuesta", DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(12, 0), zone), status = CommitmentReviewStatus.CONVERTED)
        val dismissed = commitment(3, "avisar a ana", DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(12, 0), zone), status = CommitmentReviewStatus.DISMISSED)
        val insight = GuardianCoach.insight(emptyList(), emptyList(), emptyList(), now, zone, commitments = listOf(future, converted, dismissed))

        assertFalse(insight.title.contains("te llamo"))
        assertFalse(insight.message.contains("enviar propuesta"))
        assertFalse(insight.message.contains("avisar a ana"))
    }
}

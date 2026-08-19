import com.ordia.app.automation.AutomationEngine
import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.repository.AutomationRuleRepository
import com.ordia.app.data.repository.ConversationRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.reminders.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

fun check(cond: Boolean, msg: String) {
    if (!cond) { System.err.println("FAIL: $msg"); kotlin.system.exitProcess(1) }
    println("OK: $msg")
}

fun main() {
    val rules = AutomationRuleRepository()
    val engine = AutomationEngine(rules, TaskRepository(), ConversationRepository(), ReminderScheduler())
    val rule = AutomationRuleEntity(
        id = 7, name = "r", instruction = "i", trigger = AutomationTrigger.DAILY_MORNING,
        condition = AutomationCondition.ALWAYS, action = AutomationAction.PLAN_DAY,
        explanation = "e", enabled = true, definitionHash = "h"
    )
    // Instante fijo: 2026-08-19T01:30:00Z → en NY es 18 ago 21:30, en Sydney es 19 ago 11:30.
    val now = Instant.parse("2026-08-19T01:30:00Z").toEpochMilli()
    val ny = ZoneId.of("America/New_York")
    val syd = ZoneId.of("Australia/Sydney")
    val midnightNy = Instant.ofEpochMilli(now).atZone(ny).toLocalDate().atStartOfDay(ny).toInstant().toEpochMilli()
    val midnightSyd = Instant.ofEpochMilli(now).atZone(syd).toLocalDate().atStartOfDay(syd).toInstant().toEpochMilli()

    runBlocking { engine.runRule(rule, now = now, zone = ny) }
    check(rules.countRunsSince.last() == midnightNy, "runRule usa dayStart de la zona inyectada (NY)")

    runBlocking { engine.runRule(rule, now = now, zone = syd) }
    check(rules.countRunsSince.last() == midnightSyd, "runRule usa dayStart de la zona inyectada (Sydney)")
    check(midnightNy != midnightSyd, "zonas distintas producen dayStart distinto (seam real)")

    rules.rulesForTrigger = listOf(rule)
    rules.countRunsSince.clear()
    runBlocking { engine.runTrigger(AutomationTrigger.DAILY_MORNING, zone = ny) }
    check(rules.countRunsSince.size == 1 && rules.countRunsSince[0] == midnightNy, "runTrigger propaga zone a runRule")

    rules.countRunsSince.clear()
    runBlocking { engine.runRule(rule, now = now) }
    val expectedDefault = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    check(rules.countRunsSince.last() == expectedDefault, "zone por defecto = systemDefault (llamadas existentes intactas)")

    // Seam completo hacia el planificador: RESCHEDULE_OVERDUE deriva el día base
    // y el vencimiento (18:00 local) de la zona → dueAt final debe diferir.
    val tasksRepo = TaskRepository().apply {
        tasks = listOf(TaskEntity(id = 1, title = "vencida", dueAt = now - 3_600_000))
    }
    val engine2 = AutomationEngine(rules, tasksRepo, ConversationRepository(), ReminderScheduler())
    val resched = rule.copy(id = 8, condition = AutomationCondition.HAS_OVERDUE_TASKS, action = AutomationAction.RESCHEDULE_OVERDUE)
    fun localDue(z: ZoneId) = Instant.ofEpochMilli(now).atZone(z).toLocalDate().plusDays(1)
        .atTime(18, 0).atZone(z).toInstant().toEpochMilli()
    runBlocking { engine2.runRule(resched, now = now, zone = ny) }
    check(tasksRepo.updated.size == 1, "RESCHEDULE_OVERDUE actualiza la tarea vencida (NY)")
    check(tasksRepo.updated.last().dueAt == localDue(ny), "zone fluye al planificador (NY: 18 ago→19 ago 18:00 local)")
    runBlocking { engine2.runRule(resched, now = now, zone = syd) }
    check(tasksRepo.updated.last().dueAt == localDue(syd), "zone fluye al planificador (Sydney: 19 ago→20 ago 18:00 local)")
    check(tasksRepo.updated.first().dueAt != tasksRepo.updated.last().dueAt, "zonas distintas ⇒ plan distinto (sin el fix serían iguales)")

    println("AutomationEngine smoke passed: 9 assertions")
}

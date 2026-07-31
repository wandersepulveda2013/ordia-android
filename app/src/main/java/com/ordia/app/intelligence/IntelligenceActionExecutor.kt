package com.ordia.app.intelligence

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ejecutor de acciones reales basadas en el planificador de inteligencia.
 *
 * Acciones soportadas:
 * - TASK: Crear tarea en la base de datos local (Room)
 * - SHOPPING: Crear nota de compra
 * - APPOINTMENT: Crear entrada en calendario (si hay permisos)
 * - MEETING: Crear evento de calendario
 * - REMINDER: Programar recordatorio (WorkManager)
 * - CALL: Abrir marcador telefónico
 * - PAYMENT: Registrar pago pendiente (si hay categoría configurada)
 * - STUDY: Crear tarea de estudio
 * - EXERCISE: Crear tarea de ejercicio
 * - DEADLINE: Crear tarea con fecha límite
 * - HOUSEHOLD: Crear tarea del hogar
 *
 * @property appContext Contexto de aplicación
 */
class IntelligenceActionExecutor(private val appContext: Context) {

    private val db = OrdiaDatabase.getInstance(appContext)

    /**
     * Ejecuta una acción planificada y retorna el resultado.
     *
     * @param planResult Plan de acción del IntelligenceActionPlanner
     * @return Resultado de la ejecución
     */
    suspend fun execute(planResult: IntelligenceActionPlannerResult): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            when (planResult.actionType) {
                ActionSuggested.TASK -> executeTask(planResult)
                ActionSuggested.SHOPPING -> executeShopping(planResult)
                ActionSuggested.APPOINTMENT -> executeAppointment(planResult)
                ActionSuggested.MEETING -> executeMeeting(planResult)
                ActionSuggested.REMINDER -> executeReminder(planResult)
                ActionSuggested.CALL -> executeCall(planResult)
                ActionSuggested.PAYMENT -> executePayment(planResult)
                ActionSuggested.STUDY -> executeTask(planResult.copy(actionType = ActionSuggested.TASK))
                ActionSuggested.EXERCISE -> executeTask(planResult.copy(actionType = ActionSuggested.TASK))
                ActionSuggested.DEADLINE -> executeTask(planResult)
                ActionSuggested.HOUSEHOLD -> executeTask(planResult.copy(actionType = ActionSuggested.TASK))
                ActionSuggested.NONE -> ExecutionResult.Failed("No hay acción que ejecutar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando acción", e)
            ExecutionResult.Failed(e.message ?: "Error desconocido")
        }
    }

    private suspend fun executeTask(plan: IntelligenceActionPlannerResult): ExecutionResult {
        val entity = TaskEntity(
            title = plan.title,
            details = plan.description,
            dueAt = plan.parameters["dueAt"]?.toLongOrNull(),
            priority = TaskPriority.NORMAL, // Valor fijo, el planner no tiene prioridad
            status = TaskStatus.INBOX,
            createdAt = System.currentTimeMillis()
        )
        val id = db.taskDao().insert(entity)
        Log.i(TAG, "Tarea creada: ${plan.title} (id=$id)")
        return ExecutionResult.Success("Tarea creada", "task/$id")
    }

    private suspend fun executeShopping(plan: IntelligenceActionPlannerResult): ExecutionResult {
        // Crear como tarea con detalle de compras
        val entity = TaskEntity(
            title = plan.title,
            details = "Lista de compras: ${plan.description}",
            priority = TaskPriority.NORMAL,
            status = TaskStatus.INBOX,
            createdAt = System.currentTimeMillis()
        )
        val id = db.taskDao().insert(entity)
        return ExecutionResult.Success("Lista de compras creada", "task/$id")
    }

    private suspend fun executeAppointment(plan: IntelligenceActionPlannerResult): ExecutionResult {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, plan.title)
                putExtra(CalendarContract.Events.DESCRIPTION, plan.description)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ExecutionResult.Success("Abriendo calendario", "calendar")
        } catch (e: Exception) {
            // Fallback: crear tarea
            executeTask(plan)
        }
    }

    private suspend fun executeMeeting(plan: IntelligenceActionPlannerResult): ExecutionResult {
        return executeAppointment(plan)
    }

    private suspend fun executeReminder(plan: IntelligenceActionPlannerResult): ExecutionResult {
        // Crear tarea
        val entity = TaskEntity(
            title = plan.title,
            details = plan.description,
            dueAt = plan.parameters["dueAt"]?.toLongOrNull(),
            reminderAt = plan.parameters["dueAt"]?.toLongOrNull(),
            priority = TaskPriority.NORMAL,
            status = TaskStatus.INBOX,
            createdAt = System.currentTimeMillis()
        )
        val id = db.taskDao().insert(entity)

        // Programar la notificación real con el pipeline de recordatorios
        // (trabajo único, quiet hours, notificación con acciones Completar/Snooze).
        plan.parameters["dueAt"]?.toLongOrNull()?.let { dueAt ->
            val scheduler = ReminderScheduler(appContext)
            scheduler.scheduleAt(id, dueAt)
            Log.i(TAG, "Recordatorio programado: tarea $id a las $dueAt")
        }

        return ExecutionResult.Success("Recordatorio creado", "task/$id")
    }

    private suspend fun executeCall(plan: IntelligenceActionPlannerResult): ExecutionResult {
        return try {
            val phone = plan.parameters["person"] ?: plan.parameters["phone"] ?: ""
            if (phone.isNotBlank() && phone.all { it.isDigit() || it == '+' }) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                ExecutionResult.Success("Abriendo marcador", "dial")
            } else {
                // Sin número específico, crear tarea de llamada
                executeTask(plan)
            }
        } catch (e: Exception) {
            executeTask(plan)
        }
    }

    private suspend fun executePayment(plan: IntelligenceActionPlannerResult): ExecutionResult {
        return executeTask(plan)
    }

    sealed class ExecutionResult {
        data class Success(val message: String, val uri: String) : ExecutionResult()
        data class Failed(val reason: String) : ExecutionResult()
    }

    companion object {
        private const val TAG = "IntelligenceActionExecutor"
    }
}

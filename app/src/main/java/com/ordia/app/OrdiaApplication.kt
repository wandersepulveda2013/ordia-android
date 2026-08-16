package com.ordia.app

import android.app.Application
import com.ordia.app.di.AppContainer
import com.ordia.app.BuildConfig
import com.ordia.app.reminders.TaskReminderWorker
import com.ordia.app.updates.OrdiaUpdateController
import com.ordia.app.updates.OrdiaUpdateManager
import com.ordia.app.automation.AutomationScheduler
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.external.ExternalConfirmationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OrdiaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TaskReminderWorker.createChannel(this)
        if (BuildConfig.OVERLAY_ENABLED) {
            // Solo conecta las dependencias. El controlador conserva los opt-ins
            // persistidos y nunca habilita observacion ni consentimiento por defecto.
            ExternalConfirmationController.getInstance(this).initialize(
                engine = ContextEngine.getInstance(this),
                confirmationUseCase = container.confirmExternalSuggestion
            )
        }
        applicationScope.launch {
            val preferences = container.preferencesRepository.preferences.first()
            if (BuildConfig.SELF_UPDATE_ENABLED) {
                OrdiaUpdateManager.cleanupObsolete(this@OrdiaApplication)
                if (preferences.autoUpdateEnabled) OrdiaUpdateManager.schedule(this@OrdiaApplication)
                else OrdiaUpdateManager.cancelSchedule(this@OrdiaApplication)
                // Comprobación de arranque no bloqueante: trabaja en su propio scope
                // (Dispatchers.IO). Sin red o con el feed caído la app abre igual.
                if (preferences.autoUpdateEnabled) {
                    OrdiaUpdateController.checkNow(this@OrdiaApplication)
                }
            } else {
                OrdiaUpdateManager.cancelSchedule(this@OrdiaApplication)
            }
            AutomationScheduler.sync(this@OrdiaApplication, container.automationRuleRepository)
            container.automationEngine.runTrigger(AutomationTrigger.APP_OPEN)
            // The floating foreground service is intentionally not started here. Android may create
            // the process from a background worker, where starting this service is restricted.
        }
    }
}

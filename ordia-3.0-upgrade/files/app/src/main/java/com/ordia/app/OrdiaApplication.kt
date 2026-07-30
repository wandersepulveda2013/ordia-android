package com.ordia.app

import android.app.Application
import com.ordia.app.di.AppContainer
import com.ordia.app.BuildConfig
import com.ordia.app.reminders.TaskReminderWorker
import com.ordia.app.updates.OrdiaUpdateManager
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
        applicationScope.launch {
            val preferences = container.preferencesRepository.preferences.first()
            if (BuildConfig.SELF_UPDATE_ENABLED) {
                OrdiaUpdateManager.cleanupObsolete(this@OrdiaApplication)
                if (preferences.autoUpdateEnabled) OrdiaUpdateManager.schedule(this@OrdiaApplication)
                else OrdiaUpdateManager.cancelSchedule(this@OrdiaApplication)
            } else {
                OrdiaUpdateManager.cancelSchedule(this@OrdiaApplication)
            }
            // The floating foreground service is intentionally not started here. Android may create
            // the process from a background worker, where starting this service is restricted.
        }
    }
}

package com.ordia.app

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ordia.app.di.AppContainer
import com.ordia.app.reminders.TaskReminderWorker
import com.ordia.app.overlay.GuardianOverlayService
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
            if (preferences.guardianEnabled && Settings.canDrawOverlays(this@OrdiaApplication)) {
                ContextCompat.startForegroundService(
                    this@OrdiaApplication,
                    Intent(this@OrdiaApplication, GuardianOverlayService::class.java)
                )
            }
        }
    }
}

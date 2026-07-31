package com.ordia.app.updates

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ordia.app.OrdiaApplication
import kotlinx.coroutines.flow.first

class OrdiaUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OrdiaApplication
        val preferences = app.container.preferencesRepository.preferences.first()
        if (!preferences.autoUpdateEnabled) return Result.success()
        return when (val result = OrdiaUpdateManager.checkDetailed(applicationContext)) {
            OrdiaUpdateManager.CheckResult.UpToDate -> Result.success()
            is OrdiaUpdateManager.CheckResult.Failed -> {
                // A malformed release must not create an endless battery-consuming retry loop.
                if (runAttemptCount < MAX_TRANSIENT_RETRIES) Result.retry() else Result.success()
            }
            is OrdiaUpdateManager.CheckResult.Available -> {
                val canNotify = notificationsAvailable(applicationContext)
                if (preferences.autoDownloadUpdates && canNotify) {
                    if (OrdiaUpdateManager.download(
                            applicationContext,
                            result.release,
                            allowMetered = false,
                            userInitiated = false
                        ) == null
                    ) {
                        if (runAttemptCount < MAX_TRANSIENT_RETRIES) Result.retry() else Result.success()
                    } else Result.success()
                } else {
                    if (canNotify) OrdiaUpdateManager.showAvailable(applicationContext, result.release)
                    Result.success()
                }
            }
        }
    }

    companion object {
        private const val MAX_TRANSIENT_RETRIES = 3
    }
}

/** Performs potentially long APK hashing and package inspection outside BroadcastReceiver limits. */
class UpdateValidationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L || !OrdiaUpdateManager.isManagedDownload(applicationContext, id)) {
            return Result.success()
        }
        return when (val validation = OrdiaUpdateManager.validateDownloadedPackage(applicationContext, id)) {
            is OrdiaUpdateManager.ValidationResult.Valid -> {
                if (notificationsAvailable(applicationContext)) {
                    OrdiaUpdateManager.showInstall(applicationContext, id)
                }
                Result.success()
            }
            is OrdiaUpdateManager.ValidationResult.Invalid -> {
                OrdiaUpdateManager.discardDownload(applicationContext, id)
                if (notificationsAvailable(applicationContext)) {
                    OrdiaUpdateManager.showFailure(applicationContext, validation.reason)
                }
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0L || !OrdiaUpdateManager.isManagedDownload(context, id)) return

        val request = OneTimeWorkRequestBuilder<UpdateValidationWorker>()
            .setInputData(Data.Builder().putLong(UpdateValidationWorker.KEY_DOWNLOAD_ID, id).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "ordia-validate-update-$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

private fun notificationsAvailable(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

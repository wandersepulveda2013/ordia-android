package com.ordia.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.domain.QuietHours
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Worker que dispara una notificación proactiva cuando vence un compromiso
 * PENDING con `dueAt` (c.304). Ordía mostraba los compromisos solo en 5
 * superficies reactivas — al expirar la promesa no avisaba, de modo que una
 * "promesa olvidada" quedaba invisible hasta que el usuario abría
 * conversaciones por su cuenta. Este worker cierra ese hueco: a la hora de la
 * promesa, avisa y ofrece revisarla.
 *
 * Canal: reutiliza "Recordatorios" (igual importancia/persistencia que las
 * tareas). Respeta horas silenciosas y permiso POST_NOTIFICATIONS igual que
 * [TaskReminderWorker].
 */
class CommitmentDueWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val commitmentId = inputData.getLong(KEY_COMMITMENT_ID, -1L)
        if (commitmentId <= 0L) return Result.failure()
        val app = applicationContext as? OrdiaApplication ?: return Result.failure()
        val commitment = app.container.conversationRepository.getCommitment(commitmentId)
            ?: return Result.success()
        if (commitment.reviewStatus != CommitmentReviewStatus.PENDING) return Result.success()

        val preferences = app.container.preferencesRepository.preferences.first()
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val currentMinutes = now.hour * 60 + now.minute
        if (QuietHours.contains(currentMinutes, preferences.quietStartMinutes, preferences.quietEndMinutes)) {
            app.container.reminderScheduler.scheduleCommitmentAt(
                commitmentId,
                QuietHours.nextEndMillis(System.currentTimeMillis(), preferences.quietStartMinutes, preferences.quietEndMinutes)
            )
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return if (runAttemptCount < MAX_PERMISSION_RETRIES) Result.retry() else Result.failure()
        }

        TaskReminderWorker.createChannel(applicationContext)
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            commitmentId.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.OPEN_CONVERSATIONS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detail = buildString {
            append(applicationContext.getString(R.string.commitment_overdue_detail))
            commitment.actor?.takeIf { it.isNotBlank() }?.let {
                append(applicationContext.getString(R.string.commitment_overdue_actor_suffix, it))
            }
        }
        val title = commitment.action.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.commitment_overdue_title)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ordia)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                applicationContext.getString(R.string.commitment_overdue_action_open),
                openIntent
            )
            .build()

        applicationContext.getSystemService(android.app.NotificationManager::class.java)
            .notify(commitmentId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_COMMITMENT_ID = "commitment_id"
        private const val CHANNEL_ID = "ordia_reminders"
        private const val MAX_PERMISSION_RETRIES = 5
    }
}

package com.ordia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.domain.TaskRules
import com.ordia.app.overlay.QuickCaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OrdiaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                OrdiaWidgetUpdater.updateWidgets(appContext, manager, appWidgetIds)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo actualizar el widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "OrdiaWidgetProvider"
    }
}

object OrdiaWidgetUpdater {
    private const val TAG = "OrdiaWidgetUpdater"
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        updateScope.launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, OrdiaWidgetProvider::class.java)
                updateWidgets(appContext, manager, manager.getAppWidgetIds(component))
            } catch (error: Exception) {
                Log.e(TAG, "No se pudieron actualizar los widgets", error)
            }
        }
    }

    internal suspend fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        if (widgetIds.isEmpty()) return
        val app = context.applicationContext as? OrdiaApplication ?: return
        val tasks = app.container.database.taskDao().getAllNow()
        val next = TaskRules.nextBestTask(tasks)
        val pending = tasks.count { !it.completed && !it.archived && it.parentTaskId == null }
        val today = java.time.LocalDate.now()
        val dueToday = tasks.count {
            !it.completed && !it.archived && it.parentTaskId == null && TaskRules.isDueOn(it, today)
        }
        val overdue = tasks.count {
            !it.completed && !it.archived && it.parentTaskId == null && TaskRules.isOverdue(it)
        }
        val todayLabel = buildString {
            if (dueToday > 0) append("$dueToday hoy")
            if (overdue > 0) {
                if (isNotEmpty()) append(" · ")
                append("$overdue atrasad${if (overdue == 1) "a" else "as"}")
            }
        }
        widgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.ordia_widget).apply {
                setTextViewText(R.id.widget_title, next?.title ?: context.getString(R.string.widget_all_clear))
                setTextViewText(
                    R.id.widget_count,
                    context.resources.getQuantityString(R.plurals.widget_pending_count, pending, pending)
                )
                setTextViewText(R.id.widget_today, todayLabel)
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_capture, quickCaptureIntent(context))
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2001,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun quickCaptureIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2002,
        Intent(context, QuickCaptureActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

package com.ordia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.domain.TaskRules
import com.ordia.app.overlay.QuickCaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OrdiaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { OrdiaWidgetUpdater.update(context, manager, it) }
    }
}

object OrdiaWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, OrdiaWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? OrdiaApplication ?: return@launch
            val tasks = app.container.database.taskDao().getAllNow()
            val next = TaskRules.nextBestTask(tasks)
            val pending = tasks.count { !it.completed && !it.archived && it.parentTaskId == null }
            val today = java.time.LocalDate.now()
            val dueToday = tasks.count { !it.completed && !it.archived && it.parentTaskId == null && TaskRules.isDueOn(it, today) }
            val overdue = tasks.count { !it.completed && !it.archived && it.parentTaskId == null && TaskRules.isOverdue(it) }
            val todayLabel = buildString {
                if (dueToday > 0) append("$dueToday hoy")
                if (overdue > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$overdue atrasad${if (overdue == 1) "a" else "as"}")
                }
            }
            val views = RemoteViews(context.packageName, R.layout.ordia_widget).apply {
                setTextViewText(R.id.widget_title, next?.title ?: "Todo está en orden")
                setTextViewText(R.id.widget_count, "$pending ${if (pending == 1) "pendiente" else "pendientes"}")
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

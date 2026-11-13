package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OmniTaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.ACTION_COMPLETE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"

        private var cachedTasks: List<TaskItem> = emptyList()

        fun updateAllWidgets(context: Context, tasks: List<TaskItem>) {
            cachedTasks = tasks
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, OmniTaskWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId, tasks)
                }
            } catch (_: Exception) {
                // Ignore widget update failures when widgets aren't placed or OS service is busy
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            allTasks: List<TaskItem> = cachedTasks
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_omni_task)

            // 1. Today's Date
            val todayStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_today_date, todayStr)

            // 2. Open Main Lists Button
            val openListsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openListsPendingIntent = PendingIntent.getActivity(
                context,
                1001,
                openListsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_main_lists, openListsPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openListsPendingIntent)

            // 3. Filter for due today and active tasks
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endOfTodayMillis = calendar.timeInMillis

            // Tasks due today or urgent uncompleted tasks
            val dueTodayTasks = allTasks.filter { task ->
                !task.isCompleted && (
                        task.dueDateEpoch in 1..endOfTodayMillis ||
                                task.priority == "URGENT" ||
                                (task.dueDateEpoch == 0L && task.priority == "HIGH")
                        )
            }.sortedWith(
                // Sorted strictly by Urgency Status first
                compareByDescending<TaskItem> {
                    when (it.priority) {
                        "URGENT" -> 4
                        "HIGH" -> 3
                        "MEDIUM" -> 2
                        else -> 1
                    }
                }.thenBy { if (it.dueDateEpoch > 0) it.dueDateEpoch else Long.MAX_VALUE }
            )

            val countDue = dueTodayTasks.size
            views.setTextViewText(
                R.id.widget_due_summary,
                if (countDue == 0) "All caught up • Tap Main Lists" else "$countDue due today • Ranked by Urgency"
            )

            // Row IDs
            val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
            val checkIds = listOf(R.id.widget_check_1, R.id.widget_check_2, R.id.widget_check_3, R.id.widget_check_4)
            val titleIds = listOf(R.id.widget_title_1, R.id.widget_title_2, R.id.widget_title_3, R.id.widget_title_4)
            val priorityIds = listOf(R.id.widget_priority_1, R.id.widget_priority_2, R.id.widget_priority_3, R.id.widget_priority_4)

            if (dueTodayTasks.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                for (rowId in rowIds) {
                    views.setViewVisibility(rowId, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                for (i in 0 until 4) {
                    if (i < dueTodayTasks.size) {
                        val task = dueTodayTasks[i]
                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                        views.setTextViewText(titleIds[i], task.title)
                        views.setTextViewText(priorityIds[i], task.priority)

                        // Set priority badge background drawable
                        when (task.priority) {
                            "URGENT" -> views.setInt(priorityIds[i], "setBackgroundResource", R.drawable.widget_tag_urgent)
                            "HIGH" -> views.setInt(priorityIds[i], "setBackgroundResource", R.drawable.widget_tag_high)
                            else -> views.setInt(priorityIds[i], "setBackgroundResource", R.drawable.widget_tag_medium)
                        }

                        // Checkbox to mark as completed (disappears from widget on tap)
                        val completeIntent = Intent(context, OmniTaskWidgetProvider::class.java).apply {
                            action = ACTION_COMPLETE_TASK
                            putExtra(EXTRA_TASK_ID, task.id)
                        }
                        val completePendingIntent = PendingIntent.getBroadcast(
                            context,
                            task.id.hashCode(),
                            completeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(checkIds[i], completePendingIntent)
                    } else {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COMPLETE_TASK) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            com.example.notification.NotificationHelper.cancelReminder(context, taskId)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = TodoRepository.create(context)
                    repo.markTaskCompletedById(taskId)
                    // Remove from cached tasks and update immediately
                    val updatedList = cachedTasks.filter { it.id != taskId }
                    cachedTasks = updatedList
                    updateAllWidgets(context, updatedList)
                } catch (_: Exception) {
                }
            }
        }
    }
}

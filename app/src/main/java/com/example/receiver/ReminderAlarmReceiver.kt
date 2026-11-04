package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notification.NotificationHelper

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_LIST_TITLE = "extra_list_title"
        const val EXTRA_SOUND_KEY = "extra_sound_key"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val listTitle = intent.getStringExtra(EXTRA_LIST_TITLE) ?: "P Task"
        val soundKey = intent.getStringExtra(EXTRA_SOUND_KEY) ?: "chime"

        NotificationHelper.showNotification(
            context = context,
            taskId = taskId,
            title = taskTitle,
            listName = listTitle,
            soundKey = soundKey
        )
    }
}

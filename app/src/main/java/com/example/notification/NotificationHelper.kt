package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.receiver.ReminderAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NotificationHelper {

    const val CHANNEL_ID_DEFAULT = "omnitask_reminders_channel"
    const val NOTIFICATION_ID_BASE = 1000

    val AVAILABLE_SOUNDS = listOf(
        SoundOption("chime", "Harmonic Chime", "Soft chime bells"),
        SoundOption("bell", "Crystal Bell", "Clear resonant alert"),
        SoundOption("digital_pulse", "Digital Pulse", "Modern tech alert"),
        SoundOption("gentle_harp", "Gentle Harp", "Calm and peaceful melodic chime"),
        SoundOption("cosmic_beep", "Cosmic Beep", "Dual tone space chime")
    )

    data class SoundOption(
        val key: String,
        val label: String,
        val description: String
    )

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    fun playSoundPreview(soundKey: String) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                when (soundKey) {
                    "chime" -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        delay(140)
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                    }
                    "bell" -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                    }
                    "digital_pulse" -> {
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_D, 80)
                        delay(90)
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_A, 80)
                        delay(90)
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 140)
                    }
                    "gentle_harp" -> {
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_3, 100)
                        delay(110)
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_6, 100)
                        delay(110)
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_9, 220)
                    }
                    "cosmic_beep" -> {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 120)
                        delay(130)
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 220)
                    }
                    else -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    }
                }
                delay(300)
                toneGenerator.release()
            } catch (_: Exception) {
            }
        }
    }

    fun scheduleReminder(
        context: Context,
        taskId: String,
        taskTitle: String,
        listTitle: String,
        reminderEpoch: Long,
        soundKey: String
    ) {
        if (reminderEpoch <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_TITLE, taskTitle)
            putExtra(ReminderAlarmReceiver.EXTRA_LIST_TITLE, listTitle)
            putExtra(ReminderAlarmReceiver.EXTRA_SOUND_KEY, soundKey)
        }

        val requestCode = taskId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderEpoch,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderEpoch,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminderEpoch,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val requestCode = taskId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun showNotification(
        context: Context,
        taskId: String,
        title: String,
        listName: String,
        soundKey: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TASK_ID", taskId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
            .setSmallIcon(R.drawable.ic_launcher_fg_img)
            .setContentTitle("P Task Reminder")
            .setContentText(title)
            .setSubText(listName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Task: $title\nList: $listName"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)

        // Play the chosen melodic alert tone!
        playSoundPreview(soundKey)
    }
}

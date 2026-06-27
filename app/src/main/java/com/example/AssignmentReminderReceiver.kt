package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AssignmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", 0L)
        val title = intent.getStringExtra("reminder_title") ?: "Homework/Assignment"
        val desc = intent.getStringExtra("reminder_desc") ?: "This task is due now, check details inside!"

        Log.d("AssignmentReminder", "Received reminder alarm for id: $reminderId, title: $title")
        showWarningNotification(context, reminderId, title, desc)
    }

    private fun showWarningNotification(context: Context, id: Long, title: String, desc: String) {
        val channelId = "assignment_warning_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Homework & Assignment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Compulsory urgent warnings for homework and assignments"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt() + 50000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val warningText = "🚨 ATTENTION / WARNING: Deadline Triggered!\n$desc"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ATTENTION: $title")
            .setContentText(desc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(warningText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id.toInt() + 10000, notification)
    }

    companion object {
        fun scheduleReminderAlarm(context: Context, id: Long, title: String, desc: String, dateStr: String, timeStr: String) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val targetDate = sdf.parse("$dateStr $timeStr") ?: return
                val triggerTimeMs = targetDate.time

                if (triggerTimeMs <= System.currentTimeMillis()) {
                    Log.d("AssignmentReminder", "Target time is in the past ($dateStr $timeStr). Alarm not scheduled.")
                    return
                }

                val intent = Intent(context, AssignmentReminderReceiver::class.java).apply {
                    putExtra("reminder_id", id)
                    putExtra("reminder_title", title)
                    putExtra("reminder_desc", desc)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    }
                } catch (se: SecurityException) {
                    Log.w("AssignmentReminder", "Cannot schedule exact alarm. Falling back to regular set.", se)
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
                Log.d("AssignmentReminder", "Alarm scheduled successfully for $dateStr $timeStr (ID: $id)")
            } catch (e: Exception) {
                Log.e("AssignmentReminder", "Error scheduling alarm: ${e.message}", e)
            }
        }

        fun cancelReminderAlarm(context: Context, id: Long) {
            try {
                val intent = Intent(context, AssignmentReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d("AssignmentReminder", "Cancelled alarm for ID: $id")
                }
            } catch (e: Exception) {
                Log.e("AssignmentReminder", "Error cancelling alarm: ${e.message}", e)
            }
        }
    }
}

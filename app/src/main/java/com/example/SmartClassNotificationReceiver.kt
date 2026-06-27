package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class SmartClassNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SmartClassNotification", "Boot completed detected. Rescheduling alarms.")
            SmartClassNotificationScheduler.rescheduleAll(context)
            return
        }

        val slotId = intent.getLongExtra("slot_id", -1L)
        val subjectName = intent.getStringExtra("subject_name") ?: "Class"
        val isLab = intent.getBooleanExtra("is_lab", false)
        val startTime = intent.getStringExtra("start_time") ?: ""
        val room = intent.getStringExtra("room") ?: ""
        val teacher = intent.getStringExtra("teacher") ?: ""
        val reminderMinutes = intent.getIntExtra("reminder_minutes", 10)

        Log.d("SmartClassNotification", "Received class notification alarm for slot ID: $slotId, subject: $subjectName")
        if (slotId != -1L) {
            showNotification(context, slotId, subjectName, isLab, startTime, room, teacher, reminderMinutes)
        }
    }

    private fun showNotification(
        context: Context,
        slotId: Long,
        subjectName: String,
        isLab: Boolean,
        startTime: String,
        room: String,
        teacher: String,
        reminderMinutes: Int
    ) {
        val channelId = "smart_class_notifications"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Smart Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timetable class starting reminders"
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
            slotId.toInt() + 200000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$subjectName starts in $reminderMinutes minutes"
        val bodyText = StringBuilder().apply {
            append("Class starts at $startTime")
            if (room.isNotEmpty()) {
                append(" in $room")
            }
            if (teacher.isNotEmpty()) {
                append(" ($teacher)")
            }
            append(".")
        }.toString()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(slotId.toInt() + 300000, notification)
    }
}

package com.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase

class DailyReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DailyReminder", "DailyReminderWorker triggered. Running reminder job.")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val preferences = db.dao().getUserPreferences()

            // Display Notification if enabled (or if null default to enabled)
            if (preferences == null || preferences.dailyReminderEnabled) {
                showAttendanceNotification()
            }

            // Reschedule the worker for tomorrow to maintain the chain
            if (preferences != null && preferences.dailyReminderEnabled) {
                scheduleNextDailyReminder(applicationContext, preferences.dailyReminderTime)
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("DailyReminder", "Error in DailyReminderWorker: ${e.message}", e)
            return Result.failure()
        }
    }

    private fun showAttendanceNotification() {
        val channelId = "daily_attendance_reminder_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Daily Reminders",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Get notified daily to log your university attendance"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            2002,
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Log Your Attendance Today! 📝")
            .setContentText("Don't forget to mark your classes as Present or Absent to keep your streaks alive!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(7721, notification)
    }

    companion object {
        fun scheduleNextDailyReminder(context: Context, timeStr: String) {
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 18
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            // If time is already in the past for today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val delayMs = calendar.timeInMillis - System.currentTimeMillis()
            Log.d("DailyReminder", "Scheduling notification worker delay: ${delayMs / 1000} seconds")

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("daily_reminder_work")
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "daily_attendance_reminder",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelDailyReminder(context: Context) {
            Log.d("DailyReminder", "Cancelling all daily reminder workers.")
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("daily_attendance_reminder")
        }
    }
}

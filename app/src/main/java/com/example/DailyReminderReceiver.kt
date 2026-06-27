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
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val preferences = db.dao().getUserPreferences()

                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    Log.d("DailyReminder", "Device boot completed. Re-scheduling reminders with WorkManager if enabled.")
                    if (preferences != null && preferences.dailyReminderEnabled) {
                        DailyReminderWorker.scheduleNextDailyReminder(context, preferences.dailyReminderTime)
                    }
                } else if (action == "com.example.ACTION_DAILY_REMINDER") {
                    Log.d("DailyReminder", "Action Daily Reminder received. Migrating to WorkManager for robustness.")
                    if (preferences == null || preferences.dailyReminderEnabled) {
                        val time = preferences?.dailyReminderTime ?: "18:00"
                        DailyReminderWorker.scheduleNextDailyReminder(context, time)
                    }
                }
            } catch (e: Exception) {
                Log.e("DailyReminder", "Error processing receiver boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun scheduleDailyReminder(context: Context, enabled: Boolean, timeStr: String) {
            if (enabled) {
                DailyReminderWorker.scheduleNextDailyReminder(context, timeStr)
            } else {
                DailyReminderWorker.cancelDailyReminder(context)
            }
        }
    }
}

package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.TimetableSlot
import com.example.data.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

object SmartClassNotificationScheduler {

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val prefs = db.dao().getUserPreferences() ?: return@launch
                
                // Get all subjects
                val subjects = db.dao().getAllSubjects().first()
                val slots = db.dao().getAllTimetableSlots().first()
                
                val subjectMap = subjects.associateBy { it.id }
                
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Cancel existing alarms up to 500 potential slot IDs
                val maxSlotSearch = 500
                for (id in 1..maxSlotSearch) {
                    cancelAlarm(context, id.toLong())
                }
                // Also cancel alarms for existing slots explicitly
                for (slot in slots) {
                    cancelAlarm(context, slot.id)
                }
                
                // If both are disabled, do nothing
                if (!prefs.subjectRemindersEnabled && !prefs.labRemindersEnabled) {
                    Log.d("SmartClassScheduler", "Both subject and lab reminders are disabled. Not scheduling anything.")
                    return@launch
                }
                
                val reminderMinutes = prefs.reminderTimeMinutes
                
                for (slot in slots) {
                    val subject = subjectMap[slot.subjectId] ?: continue
                    val isLab = isLabSubjectName(subject.name)
                    
                    // Filter based on toggle state
                    if (isLab) {
                        if (!prefs.labRemindersEnabled) continue
                    } else {
                        if (!prefs.subjectRemindersEnabled) continue
                    }
                    
                    scheduleAlarmForSlot(context, alarmManager, slot, subject, isLab, reminderMinutes)
                }
            } catch (e: Exception) {
                Log.e("SmartClassScheduler", "Error rescheduling smart class notifications: ${e.message}", e)
            }
        }
    }
    
    private fun scheduleAlarmForSlot(
        context: Context,
        alarmManager: AlarmManager,
        slot: TimetableSlot,
        subject: Subject,
        isLab: Boolean,
        reminderMinutes: Int
    ) {
        try {
            val parts = slot.startTime.split(":")
            if (parts.size != 2) return
            val startHour = parts[0].toIntOrNull() ?: return
            val startMinute = parts[1].toIntOrNull() ?: return
            
            // Calculate actual alarm firing time: Start time minus reminderMinutes
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, startHour)
            calendar.set(Calendar.MINUTE, startMinute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.MINUTE, -reminderMinutes)
            
            // Set correct day of week.
            // TimetableSlot: 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri, 6 = Sat, 7 = Sun
            // Java calendar: Sunday is 1, Monday is 2...
            val javaDayOfWeek = when (slot.dayOfWeek) {
                7 -> Calendar.SUNDAY
                1 -> Calendar.MONDAY
                2 -> Calendar.TUESDAY
                3 -> Calendar.WEDNESDAY
                4 -> Calendar.THURSDAY
                5 -> Calendar.FRIDAY
                6 -> Calendar.SATURDAY
                else -> Calendar.MONDAY
            }
            
            calendar.set(Calendar.DAY_OF_WEEK, javaDayOfWeek)
            
            // If the calculated time is already in the past for this week, schedule for next week
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
            
            val intent = Intent(context, SmartClassNotificationReceiver::class.java).apply {
                putExtra("slot_id", slot.id)
                putExtra("subject_name", subject.name)
                putExtra("is_lab", isLab)
                putExtra("start_time", slot.startTime)
                putExtra("room", subject.room)
                putExtra("teacher", subject.teacher)
                putExtra("reminder_minutes", reminderMinutes)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slot.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
                Log.d("SmartClassScheduler", "Scheduled alarm for slot: ${slot.id} (${subject.name}) at ${calendar.time} (Lab: $isLab)")
            } catch (se: SecurityException) {
                Log.w("SmartClassScheduler", "SecurityException scheduling exact alarm, falling back", se)
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("SmartClassScheduler", "Failed to schedule alarm for slot ${slot.id}", e)
        }
    }
    
    private fun cancelAlarm(context: Context, slotId: Long) {
        try {
            val intent = Intent(context, SmartClassNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slotId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("SmartClassScheduler", "Cancelled alarm for slot ID: $slotId")
            }
        } catch (e: Exception) {
            Log.e("SmartClassScheduler", "Failed to cancel alarm for slot ID: $slotId", e)
        }
    }

    private fun isLabSubjectName(name: String): Boolean {
        val low = name.lowercase(Locale.getDefault())
        return low.contains("lab") || 
               low.contains("laboratory") || 
               low.contains("practical") || 
               low.contains("workshop") || 
               low.contains("tutorial") || 
               low.contains("practicum") || 
               low.contains("g1") || 
               low.contains("g2") || 
               low.contains("g3") || 
               low.contains("g4") || 
               low.contains("practice")
    }
}

package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AttendanceRepository(db.dao())

    // UI Navigation/Theme State
    val userPreferences = repository.userPreferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserPreference()
    )

    val subjects = repository.allSubjects.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val timetableSlots = combine(
        repository.allTimetableSlots,
        repository.allSubjects,
        userPreferences
    ) { slots, subs, prefs ->
        val group = prefs?.labGroup ?: "ALL"
        if (group == "ALL" || group.isEmpty()) {
            slots
        } else {
            val cleanGroup = group.trim().uppercase()
            val grpPattern = Regex("""(G\d+|GROUP\s*\d+)""", RegexOption.IGNORE_CASE)
            val subMap = subs.associateBy { it.id }
            
            slots.filter { slot ->
                val sub = subMap[slot.subjectId]
                if (sub == null) {
                    true
                } else {
                    val textToSearch = "${sub.name} ${sub.room} ${sub.teacher}".uppercase()
                    val foundGroups = grpPattern.findAll(textToSearch).map { it.value }.toList()
                    if (foundGroups.isEmpty()) {
                        true // general class
                    } else {
                        foundGroups.any { grp ->
                            val grpNum = grp.replace("GROUP", "").replace("G", "").trim()
                            val userNum = cleanGroup.replace("GROUP", "").replace("G", "").trim()
                            grpNum == userNum
                        }
                    }
                }
            }
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val attendanceRecords = repository.allAttendanceRecords.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val notes = repository.allNotes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val reminders = repository.allReminders.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // BIOMETRIC SYSTEM STATE & PORTAL DATA
    val biometricRecords = repository.allBiometricRecords.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val dailyBiometricRecords = repository.allDailyBiometricRecords.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun updateBiometric(date: String, status: String) {
        viewModelScope.launch {
            repository.insertDailyBiometricRecord(DailyBiometricRecord(date, status))
        }
    }

    fun deleteDailyBiometric(date: String) {
        viewModelScope.launch {
            repository.deleteDailyBiometricRecord(date)
        }
    }

    fun clearAllDailyBiometrics() {
        viewModelScope.launch {
            repository.clearDailyBiometricRecords()
        }
    }

    private val _registeredStudents = MutableStateFlow(listOf(
        StudentProfile("Liam Carter", "CS2026-001"),
        StudentProfile("Olivia Vance", "CS2026-012"),
        StudentProfile("Sophia Reed", "CS2026-024"),
        StudentProfile("Noah Brooks", "CS2026-037"),
        StudentProfile("Emma Hayes", "CS2026-045"),
        StudentProfile("Lucas Thorne", "CS2026-058"),
        StudentProfile("Aria Patel", "CS2026-063"),
        StudentProfile("Ethan Hunt", "CS2016-015"),
        StudentProfile("Ava Sterling", "CS2026-088"),
        StudentProfile("Zayn Malik", "CS2026-099")
    ))
    val registeredStudents: StateFlow<List<StudentProfile>> = _registeredStudents.asStateFlow()

    fun registerStudent(name: String, rollNo: String) {
        _registeredStudents.update { current ->
            if (current.any { it.rollNo.uppercase() == rollNo.uppercase() }) current 
            else current + StudentProfile(name, rollNo)
        }
    }

    fun addBiometricRecord(studentName: String, studentRoll: String, subjectId: Long, subjectName: String, status: String, scanType: String = "Fingerprint") {
        viewModelScope.launch {
            repository.insertBiometricRecord(
                BiometricRecord(
                    studentName = studentName,
                    studentRoll = studentRoll,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    status = status,
                    scanType = scanType
                )
            )
        }
    }

    fun deleteBiometricRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteBiometricRecord(id)
        }
    }

    fun clearAllBiometrics() {
        viewModelScope.launch {
            repository.clearBiometricRecords()
        }
    }

    init {
        viewModelScope.launch {
            // Check if we need to auto-populate a gorgeous initial bio history
            try {
                // Ensure prefs are loaded/initialized if first time
                val currentPrefs = repository.getOrInitPreferences()
                
                repository.allBiometricRecords.first().let { currentLogs ->
                    if (currentLogs.isEmpty()) {
                        val students = listOf(
                            "Liam Carter" to "CS2026-001",
                            "Olivia Vance" to "CS2026-012",
                            "Sophia Reed" to "CS2026-024",
                            "Noah Brooks" to "CS2026-037",
                            "Emma Hayes" to "CS2026-045",
                            "Lucas Thorne" to "CS2026-058",
                            "Aria Patel" to "CS2026-063",
                            "Ethan Hunt" to "CS2016-015",
                            "Ava Sterling" to "CS2026-088"
                        )
                        val subjectsList = listOf(
                            "Computer Networks",
                            "Software Engineering",
                            "Database Systems",
                            "Compiler Design"
                        )
                        val statuses = listOf("PRESENT", "PRESENT", "ABSENT", "PRESENT", "CANCELLED")
                        val scanTypes = listOf("Fingerprint", "Facial Recognition", "Fingerprint")
                        
                        var currentTime = System.currentTimeMillis() - (5 * 24 * 3600 * 1000L) // 5 days ago
                        val random = java.util.Random(101)
                        
                        for (i in 1..26) {
                            val student = students[random.nextInt(students.size)]
                            val subjectNm = subjectsList[random.nextInt(subjectsList.size)]
                            val stat = statuses[random.nextInt(statuses.size)]
                            val sType = scanTypes[random.nextInt(scanTypes.size)]
                            
                            repository.insertBiometricRecord(
                                BiometricRecord(
                                    studentName = student.first,
                                    studentRoll = student.second,
                                    subjectId = (random.nextInt(4) + 1).toLong(),
                                    subjectName = subjectNm,
                                    status = stat,
                                    timestamp = currentTime,
                                    scanType = sType
                                )
                            )
                            currentTime += (5 * 3600 * 1000L) // add 5 hours
                        }
                    }
                }

                // Clear out the old seeded mock data automatically for active users to reset to clean slate
                repository.allDailyBiometricRecords.first().let { currentDaily ->
                    if (currentDaily.size == 25 || currentDaily.size == 26) {
                        repository.clearDailyBiometricRecords()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Current Selection States
    private val _selectedDate = MutableStateFlow(getCurrentDateStr())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _todayDate = MutableStateFlow(getCurrentDateStr())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    private val _selectedCalendarDate = MutableStateFlow(getCurrentDateStr())
    val selectedCalendarDate: StateFlow<String> = _selectedCalendarDate.asStateFlow()

    private val _notesSearchQuery = MutableStateFlow("")
    val notesSearchQuery: StateFlow<String> = _notesSearchQuery.asStateFlow()

    // Filtered Notes
    val filteredNotes = combine(repository.allNotes, _notesSearchQuery) { noteList, query ->
        if (query.isEmpty()) {
            noteList
        } else {
            noteList.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjectAttendanceStats: StateFlow<List<SubjectAttendanceStats>> = combine(
        subjects,
        attendanceRecords,
        userPreferences
    ) { subList, recList, pref ->
        val goal = pref?.attendanceGoal ?: 75
        subList.map { sub ->
            val subRecs = recList.filter { it.subjectId == sub.id }
            val attended = subRecs.count { it.status == "PRESENT" }
            val conducted = subRecs.count { it.status == "PRESENT" || it.status == "ABSENT" }
            val pct = if (conducted == 0) 100.0f else (attended.toFloat() / conducted.toFloat() * 100.0f)
            
            val risk = when {
                pct >= goal -> RiskLevel.SAFE
                pct >= (goal - 10) -> RiskLevel.WARNING
                else -> RiskLevel.CRITICAL
            }
            SubjectAttendanceStats(sub, attended, conducted, pct, risk)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val dashboardOverview: StateFlow<DashboardOverview> = combine(
        subjectAttendanceStats,
        attendanceRecords
    ) { stats, recs ->
        val totalAttended = stats.sumOf { it.attendedCount }
        val totalConducted = stats.sumOf { it.conductedCount }
        val overallPct = if (totalConducted == 0) 100.0f else (totalAttended.toFloat() / totalConducted.toFloat() * 100.0f)
        
        val validStats = stats.filter { it.conductedCount > 0 }
        val best = validStats.maxByOrNull { it.percentage }
        val worst = validStats.minByOrNull { it.percentage }
        
        // Calculate attendance streak
        val streak = calculateStreakCount(recs)
        
        DashboardOverview(
            overallPercentage = overallPct,
            totalAttended = totalAttended,
            totalConducted = totalConducted,
            bestSubjectStats = best ?: stats.firstOrNull(),
            worstSubjectStats = worst ?: stats.firstOrNull(),
            attendanceStreak = streak
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardOverview(100f, 0, 0, null, null, 0)
    )

    val todayClasses: StateFlow<List<ScheduledClassToday>> = combine(
        todayDate,
        timetableSlots,
        subjects,
        attendanceRecords
    ) { date, slots, subs, recs ->
        val dayOfWeek = getDayOfWeekFromDate(date)
        val todaySlots = slots.filter { it.dayOfWeek == dayOfWeek }
        val subMap = subs.associateBy { it.id }
        
        todaySlots.mapNotNull { slot ->
            val sub = subMap[slot.subjectId] ?: return@mapNotNull null
            val status = recs.find { it.subjectId == sub.id && it.dateStr == date }?.status ?: "UNMARKED"
            ScheduledClassToday(
                timetableSlotId = slot.id,
                subject = sub,
                startTime = slot.startTime,
                endTime = slot.endTime,
                room = sub.room,
                teacher = sub.teacher,
                currentStatus = status
            )
        }.sortedBy { it.startTime }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val calendarClasses: StateFlow<List<ScheduledClassToday>> = combine(
        selectedCalendarDate,
        timetableSlots,
        subjects,
        attendanceRecords
    ) { date, slots, subs, recs ->
        val dayOfWeek = getDayOfWeekFromDate(date)
        val todaySlots = slots.filter { it.dayOfWeek == dayOfWeek }
        val subMap = subs.associateBy { it.id }
        
        todaySlots.mapNotNull { slot ->
            val sub = subMap[slot.subjectId] ?: return@mapNotNull null
            val status = recs.find { it.subjectId == sub.id && it.dateStr == date }?.status ?: "UNMARKED"
            ScheduledClassToday(
                timetableSlotId = slot.id,
                subject = sub,
                startTime = slot.startTime,
                endTime = slot.endTime,
                room = sub.room,
                teacher = sub.teacher,
                currentStatus = status
            )
        }.sortedBy { it.startTime }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private fun isSunday(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr) ?: return false
            val cal = Calendar.getInstance().apply { time = date }
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }

    private fun getNonSundayDaysBetween(date1: Date, date2: Date): Int {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        
        cal1.set(Calendar.HOUR_OF_DAY, 0)
        cal1.set(Calendar.MINUTE, 0)
        cal1.set(Calendar.SECOND, 0)
        cal1.set(Calendar.MILLISECOND, 0)
        
        cal2.set(Calendar.HOUR_OF_DAY, 0)
        cal2.set(Calendar.MINUTE, 0)
        cal2.set(Calendar.SECOND, 0)
        cal2.set(Calendar.MILLISECOND, 0)
        
        if (cal1.timeInMillis <= cal2.timeInMillis) return 0
        
        var nonSundays = 0
        val running = Calendar.getInstance().apply { time = cal2.time }
        running.add(Calendar.DAY_OF_YEAR, 1)
        while (running.timeInMillis <= cal1.timeInMillis) {
            if (running.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                nonSundays++
            }
            running.add(Calendar.DAY_OF_YEAR, 1)
        }
        return nonSundays
    }

    private fun calculateStreakCount(recs: List<AttendanceRecord>): Int {
        val presentDates = recs.filter { it.status == "PRESENT" && !isSunday(it.dateStr) }
            .map { it.dateStr }
            .distinct()
            .sortedDescending()
        
        if (presentDates.isEmpty()) return 0
        
        var streak = 1
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            var lastDate = sdf.parse(presentDates[0])!!
            for (i in 1 until presentDates.size) {
                val nextDate = sdf.parse(presentDates[i])!!
                val daysDiff = getNonSundayDaysBetween(lastDate, nextDate)
                if (daysDiff <= 1) {
                    if (daysDiff == 1) {
                        streak++
                    }
                    lastDate = nextDate
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            return 1
        }
        return streak
    }

    // Timetable parsing state (for Gemini)
    private val _ocrState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

    sealed interface OcrUiState {
        object Idle : OcrUiState
        object Loading : OcrUiState
        data class Retrying(val message: String) : OcrUiState
        data class Success(val classes: List<ExtractedClass>) : OcrUiState
        data class Error(val message: String) : OcrUiState
    }

    init {
        viewModelScope.launch {
            repository.getOrInitPreferences()
        }
    }

    // Date Format Utility
    fun getCurrentDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getDayOfWeekLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Monday"
        }
    }

    fun getDayOfWeekShort(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Mon"
        }
    }

    fun selectDate(date: String) {
        _selectedCalendarDate.value = date
        _selectedDate.value = date
    }

    // --- Core Actions ---

    // Subjects
    fun addSubject(
        name: String,
        teacher: String = "",
        room: String = "",
        colorHex: String = "#3F51B5",
        iconName: String = "School",
        daysOfWeek: List<Int> = emptyList(),
        startTime: String = "09:00",
        endTime: String = "09:50"
    ) {
        viewModelScope.launch {
            val subjectId = repository.insertSubject(
                Subject(name = name, teacher = teacher, room = room, colorHex = colorHex, iconName = iconName)
            )
            for (day in daysOfWeek) {
                repository.insertTimetableSlot(
                    TimetableSlot(subjectId = subjectId, dayOfWeek = day, startTime = startTime, endTime = endTime)
                )
            }
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateSubject(subject: Subject) {
        viewModelScope.launch {
            repository.updateSubject(subject)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    // Timetable Manual Setup
    fun addTimetableSlot(subjectId: Long, dayOfWeek: Int, startTime: String, endTime: String) {
        viewModelScope.launch {
            repository.insertTimetableSlot(
                TimetableSlot(subjectId = subjectId, dayOfWeek = dayOfWeek, startTime = startTime, endTime = endTime)
            )
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
        }
    }

    fun deleteTimetableSlot(id: Long) {
        viewModelScope.launch {
            repository.deleteTimetableSlot(id)
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
        }
    }

    // Quick Mark Attendance
    fun markTodayAttendance(subjectId: Long, dateStr: String, status: String, isExtraClass: Boolean = false, timeStr: String = "09:00") {
        viewModelScope.launch {
            // Remove previous logged entries for this subject on this date to support single-tap toggles without duplicate rows
            repository.deleteAttendanceForDay(dateStr, subjectId)
            
            if (status != "UNMARKED") {
                repository.insertAttendanceRecord(
                    AttendanceRecord(
                        subjectId = subjectId,
                        dateStr = dateStr,
                        status = status,
                        isExtraClass = isExtraClass,
                        timeStr = timeStr
                    )
                )
            }
        }
    }

    // Extra Classes
    fun addExtraClassAndMark(subjectId: Long, dateStr: String, timeStr: String, status: String) {
        viewModelScope.launch {
            repository.insertAttendanceRecord(
                AttendanceRecord(
                    subjectId = subjectId,
                    dateStr = dateStr,
                    status = status,
                    isExtraClass = true,
                    timeStr = timeStr
                )
            )
        }
    }

    fun deleteAttendanceRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteAttendanceRecord(id)
        }
    }

    // Notes
    fun addNote(title: String, content: String, subjectId: Long? = null, priority: String = "MEDIUM", audioPath: String? = null, imagePath: String? = null) {
        viewModelScope.launch {
            repository.insertNote(
                NoteItem(
                    title = title,
                    content = content,
                    updatedAt = System.currentTimeMillis(),
                    subjectId = subjectId,
                    priority = priority,
                    audioPath = audioPath,
                    imagePath = imagePath
                )
            )
        }
    }

    fun updateNote(note: NoteItem) {
        viewModelScope.launch {
            repository.insertNote(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateReminderDetails(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.insertReminder(reminder)
            if (!reminder.isDone) {
                com.example.AssignmentReminderReceiver.scheduleReminderAlarm(
                    context = getApplication(),
                    id = reminder.id,
                    title = reminder.title,
                    desc = reminder.description,
                    dateStr = reminder.dateStr,
                    timeStr = reminder.timeStr
                )
            } else {
                com.example.AssignmentReminderReceiver.cancelReminderAlarm(
                    context = getApplication(),
                    id = reminder.id
                )
            }
        }
    }

    fun deleteNote(note: NoteItem) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun updateNotesSearch(query: String) {
        _notesSearchQuery.value = query
    }

    // Reminders
    fun addReminder(title: String, description: String, dateStr: String, timeStr: String, useSound: Boolean = false) {
        viewModelScope.launch {
            val id = repository.insertReminder(
                ReminderItem(
                    title = title,
                    description = description,
                    dateStr = dateStr,
                    timeStr = timeStr,
                    isDone = false,
                    customSoundEnabled = useSound
                )
            )
            com.example.AssignmentReminderReceiver.scheduleReminderAlarm(
                context = getApplication(),
                id = id,
                title = title,
                desc = description,
                dateStr = dateStr,
                timeStr = timeStr
            )
        }
    }

    fun deleteReminder(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            com.example.AssignmentReminderReceiver.cancelReminderAlarm(
                context = getApplication(),
                id = reminder.id
            )
        }
    }

    fun toggleReminderStatus(reminder: ReminderItem) {
        viewModelScope.launch {
            val newStatus = !reminder.isDone
            repository.updateReminderStatus(reminder.id, newStatus)
            if (newStatus) {
                com.example.AssignmentReminderReceiver.cancelReminderAlarm(
                    context = getApplication(),
                    id = reminder.id
                )
            } else {
                com.example.AssignmentReminderReceiver.scheduleReminderAlarm(
                    context = getApplication(),
                    id = reminder.id,
                    title = reminder.title,
                    desc = reminder.description,
                    dateStr = reminder.dateStr,
                    timeStr = reminder.timeStr
                )
            }
        }
    }

    // User Preferences
    fun updateGoal(goal: Int) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(attendanceGoal = goal))
        }
    }

    fun updateDailyReminder(enabled: Boolean, time: String) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(dailyReminderEnabled = enabled, dailyReminderTime = time))
            com.example.DailyReminderReceiver.scheduleDailyReminder(getApplication(), enabled, time)
        }
    }

    fun updateTheme(mode: String) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(themeMode = mode))
        }
    }

    fun updateLabGroup(group: String) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(labGroup = group))
        }
    }

    fun completeFirstLaunch() {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(hasCompletedFirstLaunch = true))
        }
    }

    fun updateShowFeatureShowcaseOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(current.copy(showFeatureShowcaseOnLaunch = enabled))
        }
    }

    fun resetPreferencesAndTimetable() {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(
                current.copy(
                    hasCompletedFirstLaunch = false,
                    attendanceGoal = 75,
                    themeMode = "SYSTEM",
                    labGroup = "ALL",
                    showFeatureShowcaseOnLaunch = true,
                    subjectRemindersEnabled = false,
                    labRemindersEnabled = false,
                    reminderTimeMinutes = 10
                )
            )
            repository.clearTimetable()
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
        }
    }

    fun updateSmartClassNotifications(subjectEnabled: Boolean, labEnabled: Boolean, reminderMinutes: Int) {
        viewModelScope.launch {
            val current = repository.getOrInitPreferences()
            repository.updatePreferences(
                current.copy(
                    subjectRemindersEnabled = subjectEnabled,
                    labRemindersEnabled = labEnabled,
                    reminderTimeMinutes = reminderMinutes
                )
            )
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
        }
    }

    // --- Gemini Parser Integration ---
    fun parseTimetableFromBitmap(bitmap: Bitmap, mimeType: String = "image/jpeg") {
        _ocrState.value = OcrUiState.Loading
        viewModelScope.launch {
            try {
                // Convert Bitmap to Base64 safely
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val bytes = byteArrayOutputStream.toByteArray()
                val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val prefs = repository.getOrInitPreferences()
                val extracted = repository.parseTimetableFromImage(
                    base64Image = base64String,
                    mimeType = mimeType,
                    labGroup = prefs.labGroup,
                    onRetry = { model, attempt, delayMs, reason ->
                        val displayName = if (model.contains("gemini")) "AttendEz Intelligence" else model
                        _ocrState.value = OcrUiState.Retrying("Retrying... $reason\n(Attempt $attempt/3 using $displayName)")
                    }
                )
                val filtered = filterExtractedClassesForGroup(extracted, prefs.labGroup)
                if (filtered.isNotEmpty()) {
                    _ocrState.value = OcrUiState.Success(filtered)
                } else {
                    _ocrState.value = OcrUiState.Error("No classes matching your profile could be extracted. Please check the image quality or group setting.")
                }
            } catch (e: Exception) {
                _ocrState.value = OcrUiState.Error("Extraction failed: ${e.localizedMessage}")
            }
        }
    }

    fun filterExtractedClassesForGroup(classes: List<ExtractedClass>, group: String): List<ExtractedClass> {
        if (group == "ALL" || group.isEmpty()) return classes
        val cleanGroup = group.trim().uppercase()
        val grpPattern = Regex("""(G\d+|GROUP\s*\d+)""", RegexOption.IGNORE_CASE)
        
        return classes.filter { ext ->
            val textToSearch = "${ext.subjectName} ${ext.room} ${ext.teacher}".uppercase()
            val foundGroups = grpPattern.findAll(textToSearch).map { it.value }.toList()
            
            if (foundGroups.isEmpty()) {
                true // general class
            } else {
                foundGroups.any { grp ->
                    val grpNum = grp.replace("GROUP", "").replace("G", "").trim()
                    val userNum = cleanGroup.replace("GROUP", "").replace("G", "").trim()
                    grpNum == userNum
                }
            }
        }
    }

    fun saveExtractedTimetable(extractedList: List<ExtractedClass>) {
        viewModelScope.launch {
            // First, clear any current timetable to prevent duplication
            repository.clearTimetable()

            val prefs = repository.getOrInitPreferences()
            val filteredList = filterExtractedClassesForGroup(extractedList, prefs.labGroup)

            // Map extracted classes to real Room models
            // Create subjects if they don't exist yet, group by subjectName
            val existingSubjectsMap = subjects.value.associateBy { it.name.lowercase().trim() }.toMutableMap()
            val colors = listOf("#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#9C27B0", "#FF9800", "#E91E63", "#009688")

            filteredList.forEach { ext ->
                val searchKey = ext.subjectName.lowercase().trim()
                if (searchKey.isEmpty()) return@forEach

                var dbSubject = existingSubjectsMap[searchKey]
                if (dbSubject == null) {
                    val colorIndex = Math.abs(searchKey.hashCode() % colors.size)
                    val colorHex = colors[colorIndex]
                    val detectedIcon = autoDetectIcon(ext.subjectName)
                    val sId = repository.insertSubject(
                        Subject(
                            name = ext.subjectName,
                            teacher = ext.teacher,
                            room = ext.room,
                            colorHex = colorHex,
                            iconName = detectedIcon
                        )
                    )
                    dbSubject = Subject(
                        id = sId,
                        name = ext.subjectName,
                        teacher = ext.teacher,
                        room = ext.room,
                        colorHex = colorHex,
                        iconName = detectedIcon
                    )
                    existingSubjectsMap[searchKey] = dbSubject
                }

                // Create Slots for day of week lists
                ext.daysOfWeek.forEach { dayInt ->
                    repository.insertTimetableSlot(
                        TimetableSlot(
                            subjectId = dbSubject.id,
                            dayOfWeek = dayInt,
                            startTime = ext.startTime,
                            endTime = ext.endTime
                        )
                    )
                }
            }
            
            com.example.SmartClassNotificationScheduler.rescheduleAll(getApplication())
            
            // Auto complete first launch to navigate home
            completeFirstLaunch()
            _ocrState.value = OcrUiState.Idle
        }
    }

    private fun autoDetectIcon(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("math") || n.contains("calc") || n.contains("algebra") || n.contains("geometry") || n.contains("discrete") -> "Calculate"
            n.contains("physic") || n.contains("chem") || n.contains("scie") || n.contains("bio") || n.contains("lab") -> "Science"
            n.contains("comp") || n.contains("network") || n.contains("soft") || n.contains("coding") || n.contains("program") || n.contains("database") || n.contains("cyber") || n.contains("web") -> "Computer"
            n.contains("art") || n.contains("paint") || n.contains("design") || n.contains("drawing") || n.contains("creative") -> "Palette"
            n.contains("music") || n.contains("sing") || n.contains("instrument") || n.contains("audio") -> "MusicNote"
            n.contains("sport") || n.contains("gym") || n.contains("health") || n.contains("fitness") -> "Sports"
            n.contains("english") || n.contains("lang") || n.contains("french") || n.contains("spanish") || n.contains("german") || n.contains("comm") || n.contains("write") -> "Language"
            n.contains("geog") || n.contains("map") || n.contains("earth") || n.contains("social") || n.contains("environ") || n.contains("space") -> "Public"
            n.contains("hist") || n.contains("civic") || n.contains("ancient") || n.contains("museum") || n.contains("culture") -> "Museum"
            n.contains("eng") || n.contains("mech") || n.contains("build") || n.contains("circuit") || n.contains("robot") -> "Engineering"
            else -> "MenuBook"
        }
    }

    fun clearOcrState() {
        _ocrState.value = OcrUiState.Idle
    }

    // --- Complex Computations: Statistics & History ---

    fun getDayOfWeekFromDate(dateStr: String): Int {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateStr) ?: return 1
            val cal = Calendar.getInstance()
            cal.time = date
            return getDayOfWeekInt(cal.get(Calendar.DAY_OF_WEEK))
        } catch (e: Exception) {
            return 1
        }
    }

    fun getDayOfWeekInt(calendarDayOfWeek: Int): Int {
        return when (calendarDayOfWeek) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    suspend fun backupAllData(): String {
        return withContext(Dispatchers.IO) {
            val root = JSONObject()
            
            // 1. Subjects
            val subjectsVal = repository.allSubjects.firstOrNull() ?: emptyList()
            val subjectsArr = JSONArray()
            subjectsVal.forEach {
                val item = JSONObject()
                item.put("id", it.id)
                item.put("name", it.name)
                item.put("teacher", it.teacher)
                item.put("room", it.room)
                item.put("colorHex", it.colorHex)
                item.put("iconName", it.iconName)
                subjectsArr.put(item)
            }
            root.put("subjects", subjectsArr)

            // 2. TimetableSlots
            val slotsVal = repository.allTimetableSlots.firstOrNull() ?: emptyList()
            val slotsArr = JSONArray()
            slotsVal.forEach {
                val item = JSONObject()
                item.put("id", it.id)
                item.put("subjectId", it.subjectId)
                item.put("dayOfWeek", it.dayOfWeek)
                item.put("startTime", it.startTime)
                item.put("endTime", it.endTime)
                slotsArr.put(item)
            }
            root.put("timetable_slots", slotsArr)

            // 3. AttendanceRecords
            val recordsVal = repository.allAttendanceRecords.firstOrNull() ?: emptyList()
            val recordsArr = JSONArray()
            recordsVal.forEach {
                val item = JSONObject()
                item.put("id", it.id)
                item.put("subjectId", it.subjectId)
                item.put("dateStr", it.dateStr)
                item.put("status", it.status)
                item.put("isExtraClass", it.isExtraClass)
                item.put("timeStr", it.timeStr)
                recordsArr.put(item)
            }
            root.put("attendance_records", recordsArr)

            // 4. Notes
            val notesVal = repository.allNotes.firstOrNull() ?: emptyList()
            val notesArr = JSONArray()
            notesVal.forEach {
                val item = JSONObject()
                item.put("id", it.id)
                item.put("title", it.title)
                item.put("content", it.content)
                item.put("updatedAt", it.updatedAt)
                item.put("subjectId", it.subjectId ?: -1L)
                item.put("priority", it.priority)
                item.put("audioPath", it.audioPath ?: "")
                item.put("imagePath", it.imagePath ?: "")
                notesArr.put(item)
            }
            root.put("notes", notesArr)

            // 5. Reminders
            val remindersVal = repository.allReminders.firstOrNull() ?: emptyList()
            val remindersArr = JSONArray()
            remindersVal.forEach {
                val item = JSONObject()
                item.put("id", it.id)
                item.put("title", it.title)
                item.put("description", it.description)
                item.put("dateStr", it.dateStr)
                item.put("timeStr", it.timeStr)
                item.put("isDone", it.isDone)
                item.put("customSoundEnabled", it.customSoundEnabled)
                remindersArr.put(item)
            }
            root.put("reminders", remindersArr)

            // 6. Preferences
            val prefs = repository.getOrInitPreferences()
            val item = JSONObject()
            item.put("attendanceGoal", prefs.attendanceGoal)
            item.put("themeMode", prefs.themeMode)
            item.put("hasCompletedFirstLaunch", prefs.hasCompletedFirstLaunch)
            item.put("dailyReminderEnabled", prefs.dailyReminderEnabled)
            item.put("dailyReminderTime", prefs.dailyReminderTime)
            item.put("labGroup", prefs.labGroup)
            item.put("showFeatureShowcaseOnLaunch", prefs.showFeatureShowcaseOnLaunch)
            root.put("user_preferences", item)

            root.toString(4)
        }
    }

    suspend fun restoreAllData(jsonStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonStr)
                val dao = db.dao()

                db.runInTransaction {
                    val s = db.openHelper.writableDatabase
                    s.execSQL("DELETE FROM daily_biometric_records")
                    s.execSQL("DELETE FROM biometric_records")
                    s.execSQL("DELETE FROM reminders")
                    s.execSQL("DELETE FROM notes")
                    s.execSQL("DELETE FROM attendance_records")
                    s.execSQL("DELETE FROM timetable_slots")
                    s.execSQL("DELETE FROM subjects")
                    s.execSQL("DELETE FROM user_preferences")

                    // 1. Subjects
                    if (root.has("subjects")) {
                        val arr = root.getJSONArray("subjects")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            kotlinx.coroutines.runBlocking {
                                dao.insertSubject(
                                    Subject(
                                        id = obj.optLong("id", 0L),
                                        name = obj.getString("name"),
                                        teacher = obj.optString("teacher", ""),
                                        room = obj.optString("room", ""),
                                        colorHex = obj.optString("colorHex", "#3F51B5"),
                                        iconName = obj.optString("iconName", "School")
                                    )
                                )
                            }
                        }
                    }

                    // 2. TimetableSlots
                    if (root.has("timetable_slots")) {
                        val arr = root.getJSONArray("timetable_slots")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            kotlinx.coroutines.runBlocking {
                                dao.insertTimetableSlot(
                                    TimetableSlot(
                                        id = obj.optLong("id", 0L),
                                        subjectId = obj.getLong("subjectId"),
                                        dayOfWeek = obj.getInt("dayOfWeek"),
                                        startTime = obj.getString("startTime"),
                                        endTime = obj.getString("endTime")
                                    )
                                )
                            }
                        }
                    }

                    // 3. AttendanceRecords
                    if (root.has("attendance_records")) {
                        val arr = root.getJSONArray("attendance_records")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            kotlinx.coroutines.runBlocking {
                                dao.insertAttendanceRecord(
                                    AttendanceRecord(
                                        id = obj.optLong("id", 0L),
                                        subjectId = obj.getLong("subjectId"),
                                        dateStr = obj.getString("dateStr"),
                                        status = obj.getString("status"),
                                        isExtraClass = obj.optBoolean("isExtraClass", false),
                                        timeStr = obj.optString("timeStr", "08:00")
                                    )
                                )
                            }
                        }
                    }

                    // 4. Notes
                    if (root.has("notes")) {
                        val arr = root.getJSONArray("notes")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            kotlinx.coroutines.runBlocking {
                                dao.insertNote(
                                    NoteItem(
                                        id = obj.optLong("id", 0L),
                                        title = obj.getString("title"),
                                        content = obj.getString("content"),
                                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                        subjectId = if (obj.has("subjectId") && obj.getLong("subjectId") != -1L) obj.getLong("subjectId") else null,
                                        priority = obj.optString("priority", "MEDIUM"),
                                        audioPath = if (obj.has("audioPath") && obj.getString("audioPath").isNotEmpty()) obj.getString("audioPath") else null,
                                        imagePath = if (obj.has("imagePath") && obj.getString("imagePath").isNotEmpty()) obj.getString("imagePath") else null
                                    )
                                )
                            }
                        }
                    }

                    // 5. Reminders
                    if (root.has("reminders")) {
                        val arr = root.getJSONArray("reminders")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            kotlinx.coroutines.runBlocking {
                                dao.insertReminder(
                                    ReminderItem(
                                        id = obj.optLong("id", 0L),
                                        title = obj.getString("title"),
                                        description = obj.optString("description", ""),
                                        dateStr = obj.getString("dateStr"),
                                        timeStr = obj.getString("timeStr"),
                                        isDone = obj.optBoolean("isDone", false),
                                        customSoundEnabled = obj.optBoolean("customSoundEnabled", false)
                                    )
                                )
                            }
                        }
                    }

                    // 6. Preferences
                    if (root.has("user_preferences")) {
                        val obj = root.getJSONObject("user_preferences")
                        kotlinx.coroutines.runBlocking {
                            dao.insertUserPreference(
                                UserPreference(
                                    id = 1,
                                    attendanceGoal = obj.optInt("attendanceGoal", 75),
                                    themeMode = obj.optString("themeMode", "SYSTEM"),
                                    hasCompletedFirstLaunch = obj.optBoolean("hasCompletedFirstLaunch", false),
                                    dailyReminderEnabled = obj.optBoolean("dailyReminderEnabled", false),
                                    dailyReminderTime = obj.optString("dailyReminderTime", "18:00")
                                )
                            )
                        }
                    }
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

// Support definitions for helper classes
data class SubjectAttendanceStats(
    val subject: Subject,
    val attendedCount: Int,
    val conductedCount: Int,
    val percentage: Float, // 0.0 to 100.0
    val riskLevel: RiskLevel // SAFE, WARNING, CRITICAL
)

enum class RiskLevel { SAFE, WARNING, CRITICAL }

data class DashboardOverview(
    val overallPercentage: Float,
    val totalAttended: Int,
    val totalConducted: Int,
    val bestSubjectStats: SubjectAttendanceStats?,
    val worstSubjectStats: SubjectAttendanceStats?,
    val attendanceStreak: Int
)

data class ScheduledClassToday(
    val timetableSlotId: Long,
    val subject: Subject,
    val startTime: String,
    val endTime: String,
    val room: String,
    val teacher: String,
    val currentStatus: String // PRESENT, ABSENT, CANCELLED, HOLIDAY, UNMARKED
)

data class StudentProfile(
    val name: String,
    val rollNo: String,
    val faceImageDeco: String = "Fingerprint"
)

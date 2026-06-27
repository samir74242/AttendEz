package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray

data class ExtractedClass(
    val subjectName: String,
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val daysOfWeek: List<Int>, // 1 = Monday, ..., 7 = Sunday
    val room: String = "",
    val teacher: String = ""
)

class AttendanceRepository(private val dao: AttendanceDao) {

    // Subjects
    val allSubjects: Flow<List<Subject>> = dao.getAllSubjects()

    suspend fun getSubjectById(id: Long): Subject? = dao.getSubjectById(id)

    suspend fun insertSubject(subject: Subject): Long = dao.insertSubject(subject)

    suspend fun updateSubject(subject: Subject) = dao.updateSubject(subject)

    suspend fun deleteSubject(subject: Subject) = dao.deleteSubject(subject)

    // Timetable Slots
    val allTimetableSlots: Flow<List<TimetableSlot>> = dao.getAllTimetableSlots()

    suspend fun insertTimetableSlot(slot: TimetableSlot): Long = dao.insertTimetableSlot(slot)

    suspend fun deleteTimetableSlot(id: Long) = dao.deleteTimetableSlotById(id)

    suspend fun clearTimetable() = dao.clearTimetable()

    // Attendance Records
    val allAttendanceRecords: Flow<List<AttendanceRecord>> = dao.getAllAttendanceRecords()

    fun getAttendanceForDate(dateStr: String): Flow<List<AttendanceRecord>> = dao.getAttendanceRecordsForDate(dateStr)

    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long = dao.insertAttendanceRecord(record)

    suspend fun deleteAttendanceRecord(id: Long) = dao.deleteAttendanceRecordById(id)

    suspend fun deleteAttendanceForDay(dateStr: String, subjectId: Long) = dao.deleteAttendanceRecordForDay(dateStr, subjectId)

    // Notes
    val allNotes: Flow<List<NoteItem>> = dao.getAllNotes()

    fun searchNotes(query: String): Flow<List<NoteItem>> = dao.searchNotes(query)

    suspend fun insertNote(note: NoteItem): Long = dao.insertNote(note)

    suspend fun deleteNote(note: NoteItem) = dao.deleteNote(note)

    // Reminders
    val allReminders: Flow<List<ReminderItem>> = dao.getAllReminders()

    suspend fun insertReminder(reminder: ReminderItem): Long = dao.insertReminder(reminder)

    suspend fun deleteReminder(reminder: ReminderItem) = dao.deleteReminder(reminder)

    suspend fun updateReminderStatus(id: Long, isDone: Boolean) = dao.updateReminderStatus(id, isDone)

    // Preferences
    val userPreferences: Flow<UserPreference?> = dao.getUserPreferencesFlow()

    suspend fun getOrInitPreferences(): UserPreference = withContext(Dispatchers.IO) {
        var pref = dao.getUserPreferences()
        if (pref == null) {
            pref = UserPreference(
                id = 1,
                attendanceGoal = 75,
                themeMode = "SYSTEM",
                hasCompletedFirstLaunch = false
            )
            dao.insertUserPreference(pref)
        }
        pref
    }

    suspend fun updatePreferences(pref: UserPreference) = dao.insertUserPreference(pref)

    // Biometric Records
    val allBiometricRecords: Flow<List<BiometricRecord>> = dao.getAllBiometricRecords()

    suspend fun insertBiometricRecord(record: BiometricRecord): Long = dao.insertBiometricRecord(record)

    suspend fun deleteBiometricRecord(id: Long) = dao.deleteBiometricRecordById(id)

    suspend fun clearBiometricRecords() = dao.clearBiometricRecords()

    // Daily Biometric Presence Records
    val allDailyBiometricRecords: Flow<List<DailyBiometricRecord>> = dao.getAllDailyBiometricRecords()

    suspend fun insertDailyBiometricRecord(record: DailyBiometricRecord) = dao.insertDailyBiometricRecord(record)

    suspend fun deleteDailyBiometricRecord(date: String) = dao.deleteDailyBiometricRecord(date)

    suspend fun clearDailyBiometricRecords() = dao.clearDailyBiometricRecords()

    // Gemini API Direct Timetable Extraction with auto-retry and multi-model fallback to solve 503 errors
    suspend fun parseTimetableFromImage(
        base64Image: String,
        mimeType: String,
        labGroup: String = "ALL",
        onRetry: (model: String, attempt: Int, delayMs: Long, reason: String) -> Unit = { _, _, _, _ -> }
    ): List<ExtractedClass> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("To use Gemini OCR, search for the 'Secrets' panel in AI Studio and add a valid 'GEMINI_API_KEY'. Currently, the app's OCR features are in demo mode. (You can click 'Continue with Demo' to continue with mock data).")
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val groupInstruction = if (labGroup != "ALL") {
            "The student is enrolled in Lab/Batch Group: $labGroup. Timetables often have separate lines or slots for different groups (like 'g1', 'g2', 'g3', 'g4' or similar). Please ONLY extract classes/labs that apply to $labGroup (or are general lectures for everyone). Skip and ignore any slots explicitly designated for other groups (like G1, G2, G3, G4 if they are not $labGroup)."
        } else {
            "Extract all classes. If any slot has group details (like G1, G2, G3, G4), retain them clearly in the subjectName (e.g. 'Network Lab (G1)') so they can be identified."
        }

        val prompt = """
            Analyze the provided timetable screenshot or photo from a college or university.
            Extract all valid classes, including subject names, rooms, faculty/teachers, and timings.
            
            ⚠️ STRICT RESTRAINT: Do NOT add, infer, or hallucinate any extra subjects, classes, or labs that are not explicitly printed in the timetable schedule. Only extract what is clearly visible on the image. Do not make up mock schedules.
            
            $groupInstruction

            You must return ONLY a raw JSON array of objects. Do not include any formatting like markdown ```json or ``` blocks.
            Keep times in exactly 24-hour HH:mm format. Days must be integers where 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday, 7 = Sunday.
            If days of the week are combined or span multiple days, return a list containing all relevant day integers.
            If Room or Teacher details are missing, return them as empty strings.

            Target Format (MUST BE THE ONLY OUTPUT):
            [
              {
                "subjectName": "Mathematics",
                "startTime": "09:00",
                "endTime": "09:55",
                "daysOfWeek": [1, 3, 5],
                "room": "Room 201",
                "teacher": "Prof. Smith"
              },
              {
                "subjectName": "Physics Lab ($labGroup)",
                "startTime": "11:00",
                "endTime": "13:00",
                "daysOfWeek": [2],
                "room": "Lab 1",
                "teacher": "Dr. Watson"
              }
            ]
        """.trimIndent()

        // Build Gemini Direct REST request payload manually to avoid extra complexity
        val requestBodyJson = JSONObject()
        val contentsArray = JSONArray()
        val contentsObj = JSONObject()
        val partsArray = JSONArray()

        // Text Part
        val textPart = JSONObject().put("text", prompt)
        partsArray.put(textPart)

        // Image Part
        val imagePart = JSONObject()
        val inlineDataObj = JSONObject()
        inlineDataObj.put("mimeType", mimeType)
        inlineDataObj.put("data", base64Image)
        imagePart.put("inlineData", inlineDataObj)
        partsArray.put(imagePart)

        contentsObj.put("parts", partsArray)
        contentsArray.put(contentsObj)
        requestBodyJson.put("contents", contentsArray)

        // Add generationConfig for JSON response format (JSON Mode)
        val generationConfigObj = JSONObject()
        generationConfigObj.put("responseMimeType", "application/json")
        requestBodyJson.put("generationConfig", generationConfigObj)

        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // Try these models in sequence when encountering 503 or overload errors
        val candidateModels = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview"
        )

        var lastException: Exception? = null

        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            for (attempt in 1..3) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: throw Exception("Received empty response body.")
                        val responseJson = JSONObject(bodyString)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates == null || candidates.length() == 0) {
                            val errorObj = responseJson.optJSONObject("error")
                            val errMsg = errorObj?.optString("message") ?: "No candidates returned by Gemini. Please check API Key or try again later."
                            throw Exception("Gemini Error: $errMsg")
                        }

                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content") ?: throw Exception("Candidate has no content field.")
                        val parts = content.optJSONArray("parts") ?: throw Exception("Candidate content has no parts array.")
                        if (parts.length() == 0) {
                            throw Exception("No text parts generated in the candidate contents.")
                        }

                        val rawText = parts.getJSONObject(0).optString("text")
                        val cleanedText = cleanJsonResponse(rawText)

                        // Parse JSON into List<ExtractedClass>
                        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                        val type = Types.newParameterizedType(List::class.java, ExtractedClass::class.java)
                        val adapter = moshi.adapter<List<ExtractedClass>>(type)
                        val parsed = adapter.fromJson(cleanedText) ?: throw Exception("JSON response was parsed as null.")
                        return@withContext parsed
                    } else {
                        val errCode = response.code
                        val errBody = response.body?.string() ?: ""
                        
                        val friendlyMsg = when (errCode) {
                            503 -> "The AI is currently under heavy demand. Please wait a moment and try again."
                            429 -> "Too many requests. Please wait a moment before trying again."
                            else -> "Server returned code $errCode. Please check if your plan has enough quota or try again later."
                        }
                        lastException = Exception(friendlyMsg)

                        if (errCode == 503 || errCode == 429 || errCode >= 500) {
                            val backoffTime = attempt * 1200L
                            onRetry(model, attempt, backoffTime, friendlyMsg)
                            delay(backoffTime)
                            continue // Try next attempt for this model
                        } else {
                            throw Exception(friendlyMsg)
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    val friendlyMsg = when {
                        msg.contains("503") || (e is java.io.IOException && msg.contains("unavailable", ignoreCase = true)) -> 
                            "The AI is currently under heavy demand. Please wait a moment and try again."
                        msg.contains("429") -> 
                            "Too many requests. Please wait a moment before trying again."
                        msg.contains("timeout", ignoreCase = true) || msg.contains("TIMEOUT") -> 
                            "Request timed out due to slow response. Re-trying..."
                        else -> 
                            msg.ifEmpty { "Connection or server. Retrying..." }
                    }
                    lastException = Exception(friendlyMsg)

                    if (attempt < 3 && (e is java.io.IOException || msg.contains("503") || msg.contains("429") || msg.contains("timeout", ignoreCase = true) || msg.contains("TIMEOUT"))) {
                        val backoffTime = attempt * 1200L
                        onRetry(model, attempt, backoffTime, friendlyMsg)
                        delay(backoffTime)
                        continue
                    } else {
                        break // Failover to next candidate model
                    }
                }
            }
        }

        throw lastException ?: Exception("All Gemini models failed to extract timetable schedule.")
    }

    private fun cleanJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```").trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```").trim()
        }
        
        // Extract array substring to be resilient to any conversational filler words
        val startIndex = cleaned.indexOf('[')
        val endIndex = cleaned.lastIndexOf(']')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1)
        }
        return cleaned
    }

    private fun getSampleExtractedTimetable(): List<ExtractedClass> {
        return listOf(
            ExtractedClass("Computer Networks", "09:00", "09:50", listOf(1, 3), "CSE Lab 3", "Dr. Alan Turing"),
            ExtractedClass("Software Engineering", "10:00", "10:50", listOf(1, 3, 5), "Room 203", "Prof. Grace Hopper"),
            ExtractedClass("Database Systems", "11:00", "11:50", listOf(2, 4), "Room 105", "Dr. Edgar Codd"),
            ExtractedClass("Artificial Intelligence", "14:00", "15:15", listOf(2, 4), "Auditorium A", "Prof. John McCarthy"),
            ExtractedClass("Compiler Design", "09:00", "10:30", listOf(5), "Room 301", "Dr. Alfred Aho"),
            
            // Group Labs on Monday (Day 1)
            ExtractedClass("Operating Systems Lab (G1)", "14:00", "16:00", listOf(1), "OS Lab 1", "Prof. Linus Torvalds"),
            ExtractedClass("Operating Systems Lab (G2)", "14:00", "16:00", listOf(1), "OS Lab 2", "Dr. Tanenbaum"),
            ExtractedClass("Operating Systems Lab (G3)", "14:00", "16:00", listOf(2), "OS Lab 1", "Prof. Linus Torvalds"),
            ExtractedClass("Operating Systems Lab (G4)", "14:00", "16:00", listOf(2), "OS Lab 2", "Dr. Tanenbaum"),

            // Group Labs on Wednesday (Day 3)
            ExtractedClass("Internet of Things Lab (G1)", "11:15", "13:15", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
            ExtractedClass("Internet of Things Lab (G2)", "11:15", "13:15", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
            ExtractedClass("Internet of Things Lab (G3)", "14:30", "16:30", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
            ExtractedClass("Internet of Things Lab (G4)", "14:30", "16:30", listOf(3), "IoT Lab", "Dr. Vint Cerf")
        )
    }
}

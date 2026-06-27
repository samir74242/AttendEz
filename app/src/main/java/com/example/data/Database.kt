package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Entities
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val colorHex: String = "#3F51B5",
    val iconName: String = "School"
)

@Entity(
    tableName = "timetable_slots",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class TimetableSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: Int, // 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri, 6 = Sat, 7 = Sun
    val startTime: String, // HH:mm
    val endTime: String // HH:mm
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dateStr: String, // yyyy-MM-dd
    val status: String, // PRESENT, ABSENT, CANCELLED, HOLIDAY
    val isExtraClass: Boolean = false,
    val timeStr: String = "08:00" // ordering or extra class time
)

@Entity(tableName = "notes")
data class NoteItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val subjectId: Long? = null,
    val priority: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW"
    val audioPath: String? = null,
    val imagePath: String? = null
)

@Entity(tableName = "reminders")
data class ReminderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dateStr: String, // yyyy-MM-dd
    val timeStr: String, // HH:mm
    val isDone: Boolean = false,
    val customSoundEnabled: Boolean = false
)

@Entity(tableName = "biometric_records")
data class BiometricRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentName: String,
    val studentRoll: String,
    val subjectId: Long,
    val subjectName: String,
    val status: String, // PRESENT, ABSENT, CANCELLED
    val timestamp: Long = System.currentTimeMillis(),
    val scanType: String = "Fingerprint" // Fingerprint, Facial Recognition, Palm Scanner
)

@Entity(tableName = "daily_biometric_records")
data class DailyBiometricRecord(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val status: String // PRESENT, ABSENT, NO_CLASS
)

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey val id: Int = 1,
    val attendanceGoal: Int = 75,
    val themeMode: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val hasCompletedFirstLaunch: Boolean = false,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "18:00", // Format is HH:mm
    val labGroup: String = "ALL", // ALL, G1, G2, G3, G4
    val showFeatureShowcaseOnLaunch: Boolean = true,
    val subjectRemindersEnabled: Boolean = false,
    val labRemindersEnabled: Boolean = false,
    val reminderTimeMinutes: Int = 10
)

// DAO
@Dao
interface AttendanceDao {
    // Subject queries
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    // Timetable queries
    @Query("SELECT * FROM timetable_slots")
    fun getAllTimetableSlots(): Flow<List<TimetableSlot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetableSlot(slot: TimetableSlot): Long

    @Query("DELETE FROM timetable_slots WHERE id = :id")
    suspend fun deleteTimetableSlotById(id: Long)

    @Query("DELETE FROM timetable_slots")
    suspend fun clearTimetable()

    // Attendance queries
    @Query("SELECT * FROM attendance_records")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE dateStr = :dateStr")
    fun getAttendanceRecordsForDate(dateStr: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteAttendanceRecordById(id: Long)

    @Query("DELETE FROM attendance_records WHERE dateStr = :dateStr AND subjectId = :subjectId")
    suspend fun deleteAttendanceRecordForDay(dateStr: String, subjectId: Long)

    // Notes queries
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteItem>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteItem): Long

    @Delete
    suspend fun deleteNote(note: NoteItem)

    // Reminders
    @Query("SELECT * FROM reminders ORDER BY dateStr ASC, timeStr ASC")
    fun getAllReminders(): Flow<List<ReminderItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderItem): Long

    @Delete
    suspend fun deleteReminder(reminder: ReminderItem)

    @Query("UPDATE reminders SET isDone = :isDone WHERE id = :id")
    suspend fun updateReminderStatus(id: Long, isDone: Boolean)

    // User preferences
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getUserPreferencesFlow(): Flow<UserPreference?>

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getUserPreferences(): UserPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPreference(pref: UserPreference)

    // Biometric Record queries
    @Query("SELECT * FROM biometric_records ORDER BY timestamp DESC")
    fun getAllBiometricRecords(): Flow<List<BiometricRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBiometricRecord(record: BiometricRecord): Long

    @Query("DELETE FROM biometric_records WHERE id = :id")
    suspend fun deleteBiometricRecordById(id: Long)

    @Query("DELETE FROM biometric_records")
    suspend fun clearBiometricRecords()

    // Daily Biometric Presence queries
    @Query("SELECT * FROM daily_biometric_records ORDER BY date DESC")
    fun getAllDailyBiometricRecords(): Flow<List<DailyBiometricRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyBiometricRecord(record: DailyBiometricRecord)

    @Query("DELETE FROM daily_biometric_records WHERE date = :date")
    suspend fun deleteDailyBiometricRecord(date: String)

    @Query("DELETE FROM daily_biometric_records")
    suspend fun clearDailyBiometricRecords()
}

// Database
@Database(
    entities = [
        Subject::class,
        TimetableSlot::class,
        AttendanceRecord::class,
        NoteItem::class,
        ReminderItem::class,
        UserPreference::class,
        BiometricRecord::class,
        DailyBiometricRecord::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendez_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

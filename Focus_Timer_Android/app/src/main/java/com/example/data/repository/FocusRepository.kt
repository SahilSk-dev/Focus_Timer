package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.StudyDao
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.sync.FirebaseSyncManager

class FocusRepository(
    private val studyDao: StudyDao,
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_timer_prefs", Context.MODE_PRIVATE)

    val syncManager = FirebaseSyncManager(studyDao, context)

    fun getAllSessions(): Flow<List<StudySessionEntity>> = studyDao.getAllSessions()
    fun getStudySessions(): Flow<List<StudySessionEntity>> = studyDao.getStudySessions()
    fun getAllSubjects(): Flow<List<SubjectEntity>> = studyDao.getAllSubjects()
    fun getAllWorkTypes(): Flow<List<WorkTypeEntity>> = studyDao.getAllWorkTypes()

    var dailyTargetMinutes: Int
        get() = prefs.getInt("daily_target_mins", 120)
        set(value) {
            prefs.edit().putInt("daily_target_mins", value).apply()
        }

    var isDialVisible: Boolean
        get() = prefs.getBoolean("is_dial_visible", true)
        set(value) = prefs.edit().putBoolean("is_dial_visible", value).apply()

    var isSoundMuted: Boolean
        get() = prefs.getBoolean("is_sound_muted", false)
        set(value) = prefs.edit().putBoolean("is_sound_muted", value).apply()

    var alarmToneId: String
        get() = prefs.getString("alarm_tone_id", "ultra_siren") ?: "ultra_siren"
        set(value) = prefs.edit().putString("alarm_tone_id", value).apply()

    var alarmCustomUri: String?
        get() = prefs.getString("alarm_custom_uri", null)
        set(value) = prefs.edit().putString("alarm_custom_uri", value).apply()

    var alarmVolumeBoost: Boolean
        get() = prefs.getBoolean("alarm_volume_boost", true)
        set(value) = prefs.edit().putBoolean("alarm_volume_boost", value).apply()

    // Persistent Active Timer State (survives app kill / close)
    fun saveRunningTimerState(
        isActive: Boolean,
        isRunning: Boolean,
        isStopwatch: Boolean,
        isPomodoro: Boolean,
        pomoPhase: String,
        subject: String,
        subSubject: String?,
        workType: String,
        startAtTs: Long,
        endTs: Long,
        totalSec: Long,
        remainingSec: Long
    ) {
        prefs.edit()
            .putBoolean("timer_active", isActive)
            .putBoolean("timer_running", isRunning)
            .putBoolean("timer_stopwatch", isStopwatch)
            .putBoolean("timer_pomodoro", isPomodoro)
            .putString("timer_pomo_phase", pomoPhase)
            .putString("timer_subject", subject)
            .putString("timer_sub_subject", subSubject ?: "")
            .putString("timer_work_type", workType)
            .putLong("timer_start_at_ts", startAtTs)
            .putLong("timer_end_ts", endTs)
            .putLong("timer_total_sec", totalSec)
            .putLong("timer_remaining_sec", remainingSec)
            .apply()
    }

    fun clearRunningTimerState() {
        prefs.edit()
            .remove("timer_active")
            .remove("timer_running")
            .remove("timer_stopwatch")
            .remove("timer_pomodoro")
            .remove("timer_pomo_phase")
            .remove("timer_start_at_ts")
            .remove("timer_end_ts")
            .remove("timer_total_sec")
            .remove("timer_remaining_sec")
            .apply()
    }

    fun getSavedTimerActive(): Boolean = prefs.getBoolean("timer_active", false)
    fun getSavedTimerRunning(): Boolean = prefs.getBoolean("timer_running", false)
    fun getSavedTimerStopwatch(): Boolean = prefs.getBoolean("timer_stopwatch", false)
    fun getSavedTimerPomodoro(): Boolean = prefs.getBoolean("timer_pomodoro", false)
    fun getSavedTimerPomoPhase(): String = prefs.getString("timer_pomo_phase", "work") ?: "work"
    fun getSavedTimerSubject(): String = prefs.getString("timer_subject", "Bengali") ?: "Bengali"
    fun getSavedTimerSubSubject(): String? = prefs.getString("timer_sub_subject", null).let { if (it.isNullOrEmpty()) null else it }
    fun getSavedTimerWorkType(): String = prefs.getString("timer_work_type", "Revision") ?: "Revision"
    fun getSavedTimerStartAtTs(): Long = prefs.getLong("timer_start_at_ts", 0L)
    fun getSavedTimerEndTs(): Long = prefs.getLong("timer_end_ts", 0L)
    fun getSavedTimerTotalSec(): Long = prefs.getLong("timer_total_sec", 0L)
    fun getSavedTimerRemainingSec(): Long = prefs.getLong("timer_remaining_sec", 0L)

    suspend fun addSession(session: StudySessionEntity): Long {
        val id = studyDao.insertSession(session)
        syncManager.uploadSession(session)
        return id
    }

    suspend fun updateSession(session: StudySessionEntity, previousIsNonStudy: Boolean? = null) {
        studyDao.updateSession(session)
        syncManager.uploadSession(session, previousIsNonStudy)
    }

    suspend fun deleteSession(id: Long) {
        val session = studyDao.getSessionById(id)
        if (session != null) {
            syncManager.deleteSessionFromCloud(session.timestamp, session.isNonStudy)
        }
        studyDao.deleteSessionById(id)
    }

    suspend fun deleteSessionsBetweenDates(fromDate: String, toDate: String): Int {
        val sessions = studyDao.getSessionsBetweenDates(fromDate, toDate)
        if (sessions.isNotEmpty()) {
            syncManager.bulkDeleteSessionsFromCloud(sessions.map { it.timestamp })
        }
        return studyDao.deleteSessionsBetweenDates(fromDate, toDate)
    }

    suspend fun addSubject(subject: SubjectEntity): Long {
        val id = studyDao.insertSubject(subject)
        syncPrefsToCloud()
        return id
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        studyDao.updateSubject(subject)
        syncPrefsToCloud()
    }

    suspend fun deleteSubject(id: Long) {
        studyDao.deleteSubjectById(id)
        syncPrefsToCloud()
    }

    suspend fun addWorkType(name: String): Long {
        val id = studyDao.insertWorkType(WorkTypeEntity(name = name))
        syncPrefsToCloud()
        return id
    }

    suspend fun updateWorkType(id: Long, name: String) {
        studyDao.updateWorkType(WorkTypeEntity(id = id, name = name))
        syncPrefsToCloud()
    }

    suspend fun deleteWorkType(id: Long) {
        studyDao.deleteWorkTypeById(id)
        syncPrefsToCloud()
    }

    suspend fun syncPrefsToCloud() {
        try {
            val subjects = studyDao.getAllSubjects().first()
            val workTypes = studyDao.getAllWorkTypes().first()
            syncManager.uploadPrefsToCloud(dailyTargetMinutes, subjects, workTypes)
        } catch (_: Exception) {}
    }


    fun exportToJson(sessions: List<StudySessionEntity>): String {
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        val studyArray = JSONArray()
        val nonStudyArray = JSONArray()

        for (s in sessions) {
            val obj = JSONObject()
            val idStr = if (s.id > 0) s.id.toString() else "s_${s.timestamp}"
            val workTypeStr = if (s.workType.isBlank()) "N/A" else s.workType

            if (s.isNonStudy) {
                obj.put("id", idStr)
                obj.put("workType", workTypeStr)
                obj.put("ts", s.timestamp)
                obj.put("minutes", s.minutes)
                obj.put("subject", s.subject)
                obj.put("date", s.date)
                obj.put("isNonStudy", true)
                nonStudyArray.put(obj)
            } else {
                obj.put("id", idStr)
                obj.put("date", s.date)
                obj.put("workType", workTypeStr)
                obj.put("minutes", s.minutes)
                obj.put("subject", s.subject)
                obj.put("ts", s.timestamp)
                studyArray.put(obj)
            }
        }

        root.put("sessions", studyArray)
        root.put("nonStudySessions", nonStudyArray)
        return root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Int {
        val root = JSONObject(jsonString)
        val studyArray = root.optJSONArray("sessions")
        val nonStudyArray = root.optJSONArray("nonStudySessions")

        if (studyArray == null && nonStudyArray == null) return 0

        val existingSessions = studyDao.getAllSessions().first()
        val existingTsSet = existingSessions.map { it.timestamp }.toSet()
        val list = mutableListOf<StudySessionEntity>()

        fun parseSession(obj: JSONObject, defaultIsNonStudy: Boolean) {
            val ts = obj.optLong("ts", System.currentTimeMillis())
            // Check for deduplication
            if (existingTsSet.contains(ts) || list.any { it.timestamp == ts }) {
                return
            }

            val subject = obj.optString("subject", "General")
            val sub = obj.optString("subSubject", "")
            val workType = obj.optString("workType", "N/A")
            val minutes = obj.optInt("minutes", 0)
            val date = obj.optString("date", getTodayString())
            val isNonStudy = if (obj.has("isNonStudy")) obj.optBoolean("isNonStudy", defaultIsNonStudy) else defaultIsNonStudy

            list.add(
                StudySessionEntity(
                    date = date,
                    subject = subject,
                    subSubject = if (sub.isNotBlank()) sub else null,
                    workType = if (workType.isBlank()) "N/A" else workType,
                    minutes = minutes,
                    timestamp = ts,
                    isNonStudy = isNonStudy
                )
            )
        }

        if (studyArray != null) {
            for (i in 0 until studyArray.length()) {
                val obj = studyArray.getJSONObject(i)
                parseSession(obj, defaultIsNonStudy = false)
            }
        }

        if (nonStudyArray != null) {
            for (i in 0 until nonStudyArray.length()) {
                val obj = nonStudyArray.getJSONObject(i)
                parseSession(obj, defaultIsNonStudy = true)
            }
        }

        if (list.isNotEmpty()) {
            studyDao.insertSessions(list)
        }
        return list.size
    }

    companion object {
        fun getTodayString(): String {
            return com.example.util.DateFormatterCache.getTodayString()
        }
    }
}

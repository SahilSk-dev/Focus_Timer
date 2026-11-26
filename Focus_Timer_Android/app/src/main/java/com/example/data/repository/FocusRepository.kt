package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.SessionDeduplicator
import com.example.data.local.SessionSyncLock
import com.example.data.local.StudyDao
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.example.data.sync.FirebaseSyncManager

data class ImportResult(
    val studyCount: Int,
    val nonStudyCount: Int
) {
    val totalCount: Int get() = studyCount + nonStudyCount
}

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
        val id = SessionSyncLock.mutex.withLock {
            val existing = studyDao.getAllSessionsOrderByIdAsc()
                .firstOrNull { SessionDeduplicator.isDuplicateOf(it, session) }
            if (existing != null && session.id == 0L) {
                existing.id
            } else {
                studyDao.insertSession(session)
            }
        }
        syncManager.uploadSession(session)
        return id
    }

    suspend fun updateSession(
        session: StudySessionEntity,
        previousIsNonStudy: Boolean? = null,
        previousTimestamp: Long? = null
    ) {
        SessionSyncLock.mutex.withLock {
            studyDao.updateSession(session)
        }
        syncManager.uploadSession(session, previousIsNonStudy, previousTimestamp)
    }

    suspend fun deleteSession(id: Long) {
        val session = SessionSyncLock.mutex.withLock {
            val found = studyDao.getSessionById(id)
            studyDao.deleteSessionById(id)
            found
        }
        if (session != null) {
            syncManager.deleteSessionFromCloud(session.timestamp, session.isNonStudy)
        }
    }

    suspend fun deleteSessionsBetweenDates(fromDate: String, toDate: String): Int {
        val (sessions, count) = SessionSyncLock.mutex.withLock {
            val list = studyDao.getSessionsBetweenDates(fromDate, toDate)
            val deleted = studyDao.deleteSessionsBetweenDates(fromDate, toDate)
            list to deleted
        }
        if (sessions.isNotEmpty()) {
            syncManager.bulkDeleteSessionsFromCloud(sessions.map { it.timestamp })
        }
        return count
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


    fun getExamGoal(): com.example.util.ExamGoal {
        return com.example.util.ExamGoal(
            examName = prefs.getString("exam_goal_name", "Target Exam / Syllabus") ?: "Target Exam / Syllabus",
            targetDate = prefs.getString("exam_goal_date", "") ?: "",
            targetHours = prefs.getFloat("exam_goal_hours", 150f).toDouble(),
            subjectScope = prefs.getStringSet("exam_goal_scope", emptySet()) ?: emptySet()
        )
    }

    fun saveExamGoal(goal: com.example.util.ExamGoal) {
        prefs.edit()
            .putString("exam_goal_name", goal.examName)
            .putString("exam_goal_date", goal.targetDate)
            .putFloat("exam_goal_hours", goal.targetHours.toFloat())
            .putStringSet("exam_goal_scope", goal.subjectScope)
            .apply()
    }

    fun exportToJson(sessions: List<StudySessionEntity>): String {
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        val studyArray = JSONArray()
        val nonStudyArray = JSONArray()

        val deduplicated = SessionDeduplicator.deduplicateList(sessions)

        for (s in deduplicated) {
            val obj = JSONObject()
            val prefix = if (s.isNonStudy) "ns_" else "s_"
            val idStr = "$prefix${s.timestamp}"
            val workTypeStr = if (s.workType.isBlank()) "N/A" else s.workType

            obj.put("id", idStr)
            obj.put("date", s.date)
            obj.put("subject", s.subject)
            if (!s.subSubject.isNullOrBlank()) {
                obj.put("subSubject", s.subSubject)
            }
            obj.put("workType", workTypeStr)
            obj.put("minutes", s.minutes)
            obj.put("ts", s.timestamp)

            if (s.isNonStudy) {
                obj.put("isNonStudy", true)
                nonStudyArray.put(obj)
            } else {
                studyArray.put(obj)
            }
        }

        root.put("sessions", studyArray)
        root.put("nonStudySessions", nonStudyArray)

        // Settings & Exam Goal for cross-platform backup parity
        val settingsObj = JSONObject()
        settingsObj.put("dailyTargetMinutes", dailyTargetMinutes)
        root.put("settings", settingsObj)

        val goal = getExamGoal()
        val goalObj = JSONObject()
        goalObj.put("examName", goal.examName)
        goalObj.put("targetDate", goal.targetDate)
        goalObj.put("targetHours", goal.targetHours)
        val scopeArray = JSONArray()
        goal.subjectScope.forEach { scopeArray.put(it) }
        goalObj.put("subjectScope", scopeArray)
        root.put("examGoal", goalObj)

        return root.toString(2)
    }

    private fun parseTimestampFromJson(obj: JSONObject, fallbackDate: String, itemIndex: Int): Long {
        val rawStr = when {
            obj.has("ts") && !obj.isNull("ts") -> obj.opt("ts")?.toString()?.trim()
            obj.has("timestamp") && !obj.isNull("timestamp") -> obj.opt("timestamp")?.toString()?.trim()
            else -> null
        }
        val parsedDouble = rawStr?.toDoubleOrNull()
        var ts = if (parsedDouble != null && parsedDouble > 0.0 && !parsedDouble.isNaN() && !parsedDouble.isInfinite()) {
            parsedDouble.toLong()
        } else {
            0L
        }
        // Normalize 10-digit epoch seconds to milliseconds
        if (ts in 1_000_000_000L..9_999_999_999L) {
            ts *= 1000L
        }
        if (ts > 0L) return ts

        // Deterministic fallback for legacy exports missing ts so re-imports never generate new duplicates
        val parsedDate = com.example.util.DateFormatterCache.parseIsoDate(fallbackDate)
        val baseMillis = if (parsedDate != null) {
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } else {
            0L
        }
        return if (baseMillis > 0L) baseMillis + (itemIndex * 1000L) else 0L
    }

    suspend fun importFromJson(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val studyArray = root.optJSONArray("sessions")
        val nonStudyArray = root.optJSONArray("nonStudySessions")

        if (studyArray == null && nonStudyArray == null) return@withContext ImportResult(0, 0)

        // Restore settings if present
        if (root.has("settings") && !root.isNull("settings")) {
            val sObj = root.optJSONObject("settings")
            if (sObj != null) {
                if (sObj.has("dailyTargetMinutes") && !sObj.isNull("dailyTargetMinutes")) {
                    val t = sObj.optInt("dailyTargetMinutes", dailyTargetMinutes)
                    if (t >= 10) dailyTargetMinutes = t
                } else if (sObj.has("dailyTarget") && !sObj.isNull("dailyTarget")) {
                    val t = sObj.optInt("dailyTarget", dailyTargetMinutes)
                    if (t >= 10) dailyTargetMinutes = t
                }
            }
        }

        // Restore examGoal if present
        if (root.has("examGoal") && !root.isNull("examGoal")) {
            val gObj = root.optJSONObject("examGoal")
            if (gObj != null) {
                val name = gObj.optString("examName", "Target Exam / Syllabus")
                val date = gObj.optString("targetDate", "")
                val hours = gObj.optDouble("targetHours", 150.0)
                val scopeSet = mutableSetOf<String>()
                val arr = gObj.optJSONArray("subjectScope")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i)
                        if (s.isNotBlank()) scopeSet.add(s)
                    }
                }
                saveExamGoal(com.example.util.ExamGoal(name, date, hours, scopeSet))
            }
        }

        val list = mutableListOf<StudySessionEntity>()
        var studyCount = 0
        var nonStudyCount = 0

        SessionSyncLock.mutex.withLock {
            // Heal any historical duplicates in Room before merging new sessions
            SessionDeduplicator.deduplicateStoredSessions(studyDao)

            val existingSessions = studyDao.getAllSessionsOrderByIdAsc()
            val allSubjects = studyDao.getAllSubjects().first()
            val nonStudyNamesLower = allSubjects
                .filter { it.isNonStudy }
                .map { SessionDeduplicator.normalizeSubjectKey(it.name) }
                .toSet()

            fun parseSession(obj: JSONObject, defaultIsNonStudy: Boolean, idx: Int) {
                val subject = obj.optString("subject", "").trim().take(120)
                if (subject.isBlank()) return

                val minutes = obj.optInt("minutes", 0).coerceAtMost(1440)
                if (minutes <= 0) return

                val rawDate = obj.optString("date", "").trim().take(32)
                val date = if (rawDate.isBlank()) getTodayString() else rawDate
                val ts = parseTimestampFromJson(obj, date, idx)

                val sub = obj.optString("subSubject", "").trim().take(120)
                val workType = obj.optString("workType", "N/A").trim().take(100)
                val idStr = obj.optString("id", "").trim()
                val explicitNS = if (obj.has("isNonStudy") && !obj.isNull("isNonStudy")) {
                    obj.optBoolean("isNonStudy", defaultIsNonStudy)
                } else {
                    defaultIsNonStudy
                }
                val subjLower = SessionDeduplicator.normalizeSubjectKey(subject)
                val baseSubjLower = SessionDeduplicator.normalizeSubjectKey(subject.substringBefore(" - "))
                val isNonStudy = explicitNS ||
                    idStr.startsWith("ns", ignoreCase = true) ||
                    nonStudyNamesLower.contains(subjLower) ||
                    nonStudyNamesLower.contains(baseSubjLower)

                val candidate = StudySessionEntity(
                    date = date,
                    subject = subject,
                    subSubject = if (sub.isNotBlank()) sub else null,
                    workType = if (workType.isBlank()) "N/A" else workType,
                    minutes = minutes,
                    timestamp = ts,
                    isNonStudy = isNonStudy
                )

                val isDuplicate = existingSessions.any { SessionDeduplicator.isDuplicateOf(it, candidate) } ||
                    list.any { SessionDeduplicator.isDuplicateOf(it, candidate) }
                if (isDuplicate) return

                list.add(candidate)
                if (isNonStudy) nonStudyCount++ else studyCount++
            }

            if (studyArray != null) {
                for (i in 0 until studyArray.length()) {
                    val obj = studyArray.optJSONObject(i) ?: continue
                    parseSession(obj, defaultIsNonStudy = false, idx = i)
                }
            }

            if (nonStudyArray != null) {
                for (i in 0 until nonStudyArray.length()) {
                    val obj = nonStudyArray.optJSONObject(i) ?: continue
                    parseSession(obj, defaultIsNonStudy = true, idx = i + 10000)
                }
            }

            if (list.isNotEmpty()) {
                studyDao.insertSessions(list)
                SessionDeduplicator.deduplicateStoredSessions(studyDao)
            }
        }

        if (list.isNotEmpty()) {
            // Sync restored sessions to Firestore after local Room commit
            syncManager.uploadRestoredSessionsToCloud(list)
        }
        ImportResult(studyCount, nonStudyCount)
    }

    companion object {
        fun getTodayString(): String {
            return com.example.util.DateFormatterCache.getTodayString()
        }
    }
}

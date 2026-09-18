package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.StudyDao
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.flow.Flow
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
        set(value) = prefs.edit().putInt("daily_target_mins", value).apply()

    var isDialVisible: Boolean
        get() = prefs.getBoolean("is_dial_visible", true)
        set(value) = prefs.edit().putBoolean("is_dial_visible", value).apply()

    var isSoundMuted: Boolean
        get() = prefs.getBoolean("is_sound_muted", false)
        set(value) = prefs.edit().putBoolean("is_sound_muted", value).apply()

    suspend fun addSession(session: StudySessionEntity): Long {
        val id = studyDao.insertSession(session)
        syncManager.uploadSession(session)
        return id
    }

    suspend fun deleteSession(id: Long) {
        studyDao.deleteSessionById(id)
    }

    suspend fun deleteSessionsBetweenDates(fromDate: String, toDate: String): Int {
        return studyDao.deleteSessionsBetweenDates(fromDate, toDate)
    }

    suspend fun addSubject(subject: SubjectEntity): Long {
        return studyDao.insertSubject(subject)
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        studyDao.updateSubject(subject)
    }

    suspend fun deleteSubject(id: Long) {
        studyDao.deleteSubjectById(id)
    }

    suspend fun addWorkType(name: String): Long {
        return studyDao.insertWorkType(WorkTypeEntity(name = name))
    }

    suspend fun deleteWorkType(id: Long) {
        studyDao.deleteWorkTypeById(id)
    }

    fun exportToJson(sessions: List<StudySessionEntity>): String {
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("appName", "Focus Study Timer")
        val sessArray = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("date", s.date)
            obj.put("subject", s.subject)
            obj.put("subSubject", s.subSubject ?: "")
            obj.put("workType", s.workType)
            obj.put("minutes", s.minutes)
            obj.put("ts", s.timestamp)
            obj.put("isNonStudy", s.isNonStudy)
            sessArray.put(obj)
        }
        root.put("sessions", sessArray)
        return root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Int {
        val root = JSONObject(jsonString)
        val sessArray = root.optJSONArray("sessions") ?: return 0
        val list = mutableListOf<StudySessionEntity>()
        for (i in 0 until sessArray.length()) {
            val obj = sessArray.getJSONObject(i)
            val sub = obj.optString("subSubject", "")
            list.add(
                StudySessionEntity(
                    date = obj.optString("date", getTodayString()),
                    subject = obj.optString("subject", "General"),
                    subSubject = if (sub.isNotBlank()) sub else null,
                    workType = obj.optString("workType", "Other"),
                    minutes = obj.optInt("minutes", 0),
                    timestamp = obj.optLong("ts", System.currentTimeMillis()),
                    isNonStudy = obj.optBoolean("isNonStudy", false)
                )
            )
        }
        if (list.isNotEmpty()) {
            studyDao.insertSessions(list)
        }
        return list.size
    }

    companion object {
        fun getTodayString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }
}

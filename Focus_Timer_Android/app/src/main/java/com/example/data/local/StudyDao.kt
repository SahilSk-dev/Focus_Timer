package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // Sessions
    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE date = :date ORDER BY timestamp DESC")
    fun getSessionsForDate(date: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE isNonStudy = 0 ORDER BY timestamp DESC")
    fun getStudySessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE date >= :fromDate AND date <= :toDate")
    suspend fun getSessionsBetweenDates(fromDate: String, toDate: String): List<StudySessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySessionEntity>)

    @Update
    suspend fun updateSession(session: StudySessionEntity)

    @Delete
    suspend fun deleteSession(session: StudySessionEntity)

    @Query("DELETE FROM study_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM study_sessions WHERE timestamp = :timestamp")
    suspend fun deleteSessionByTimestamp(timestamp: Long): Int

    @Query("DELETE FROM study_sessions WHERE date >= :fromDate AND date <= :toDate")
    suspend fun deleteSessionsBetweenDates(fromDate: String, toDate: String): Int

    // Subjects
    @Query("SELECT * FROM subjects ORDER BY id ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun getSubjectByName(name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubjectById(id: Long)

    @Query("DELETE FROM subjects WHERE name = :name")
    suspend fun deleteSubjectByName(name: String)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query("DELETE FROM subjects WHERE id NOT IN (SELECT MIN(id) FROM subjects GROUP BY name)")
    suspend fun deduplicateSubjects(): Int

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun getSubjectCount(): Int

    // Work Types
    @Query("SELECT * FROM work_types ORDER BY id ASC")
    fun getAllWorkTypes(): Flow<List<WorkTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkType(workType: WorkTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkTypes(workTypes: List<WorkTypeEntity>)

    @Update
    suspend fun updateWorkType(workType: WorkTypeEntity)

    @Query("DELETE FROM work_types WHERE id = :id")
    suspend fun deleteWorkTypeById(id: Long)

    @Query("DELETE FROM work_types WHERE id NOT IN (SELECT MIN(id) FROM work_types GROUP BY name)")
    suspend fun deduplicateWorkTypes(): Int

    @Query("DELETE FROM work_types")
    suspend fun deleteAllWorkTypes()

    @Query("SELECT COUNT(*) FROM work_types")
    suspend fun getWorkTypeCount(): Int
}


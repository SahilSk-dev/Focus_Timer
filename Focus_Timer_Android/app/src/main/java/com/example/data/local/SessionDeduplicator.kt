package com.example.data.local

import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import kotlinx.coroutines.sync.Mutex
import java.util.Locale
import kotlin.math.abs

/**
 * Shared synchronization lock protecting Room session mutations across
 * JSON backup imports and concurrent Firestore snapshot listeners.
 */
object SessionSyncLock {
    val mutex = Mutex()
}

/**
 * Deterministic session deduplication and non-study classification utilities.
 * Collapses duplicate records sharing exact timestamps or matching (date, subject, minutes)
 * within a 2-minute timestamp window, matching the Web client's deduplication rules.
 */
object SessionDeduplicator {

    private const val DEDUP_WINDOW_MS = 120_000L

    fun normalizeSubjectKey(subject: String): String {
        return subject.trim().lowercase(Locale.US)
    }

    fun isNonStudySession(
        session: StudySessionEntity,
        subjects: List<SubjectEntity> = emptyList()
    ): Boolean {
        if (session.isNonStudy) return true
        if (subjects.isEmpty()) return false
        val nonStudyNames = subjects
            .filter { it.isNonStudy }
            .map { normalizeSubjectKey(it.name) }
            .toSet()
        if (nonStudyNames.isEmpty()) return false
        val fullKey = normalizeSubjectKey(session.subject)
        val baseKey = normalizeSubjectKey(session.subject.substringBefore(" - "))
        return nonStudyNames.contains(fullKey) || nonStudyNames.contains(baseKey)
    }

    fun isDuplicateOf(a: StudySessionEntity, b: StudySessionEntity): Boolean {
        if (a.timestamp > 0L && b.timestamp > 0L && a.timestamp == b.timestamp) {
            return true
        }
        val sameCore = a.date.trim() == b.date.trim() &&
            normalizeSubjectKey(a.subject) == normalizeSubjectKey(b.subject) &&
            a.minutes == b.minutes
        if (!sameCore) return false

        if (a.timestamp > 0L && b.timestamp > 0L) {
            return abs(a.timestamp - b.timestamp) <= DEDUP_WINDOW_MS
        }
        return a.workType.trim().equals(b.workType.trim(), ignoreCase = true)
    }

    /**
     * Returns deduplicated sessions preserving insertion/priority order.
     * When duplicates exist, prefers rows with non-study flag preserved and valid timestamp.
     */
    fun deduplicateList(sessions: List<StudySessionEntity>): List<StudySessionEntity> {
        if (sessions.size <= 1) return sessions
        val result = ArrayList<StudySessionEntity>(sessions.size)
        for (candidate in sessions) {
            if (candidate.minutes <= 0) continue
            val matchIdx = result.indexOfFirst { existing -> isDuplicateOf(existing, candidate) }
            if (matchIdx == -1) {
                result.add(candidate)
            } else {
                val prev = result[matchIdx]
                val preferCandidate = (!prev.isNonStudy && candidate.isNonStudy) ||
                    (prev.timestamp <= 0L && candidate.timestamp > 0L)
                if (preferCandidate) {
                    result[matchIdx] = candidate
                }
            }
        }
        return result
    }

    /**
     * Given rows ordered by id ASC, returns the Room primary key IDs of duplicate rows to delete.
     */
    fun findDuplicateIdsToDelete(rowsOrderedByIdAsc: List<StudySessionEntity>): List<Long> {
        if (rowsOrderedByIdAsc.size <= 1) return emptyList()
        val kept = ArrayList<StudySessionEntity>(rowsOrderedByIdAsc.size)
        val toDelete = ArrayList<Long>()
        for (row in rowsOrderedByIdAsc) {
            val matchIdx = kept.indexOfFirst { existing -> isDuplicateOf(existing, row) }
            if (matchIdx == -1) {
                kept.add(row)
            } else {
                val prev = kept[matchIdx]
                // Keep the better row (valid timestamp / non-study flag) and mark the other for deletion
                val preferRow = (prev.timestamp <= 0L && row.timestamp > 0L) ||
                    (!prev.isNonStudy && row.isNonStudy)
                if (preferRow && prev.id > 0L) {
                    toDelete.add(prev.id)
                    kept[matchIdx] = row
                } else if (row.id > 0L) {
                    toDelete.add(row.id)
                }
            }
        }
        return toDelete
    }

    /**
     * Removes existing duplicate rows directly from Room.
     */
    suspend fun deduplicateStoredSessions(dao: StudyDao): Int {
        val rows = dao.getAllSessionsOrderByIdAsc()
        val duplicateIds = findDuplicateIdsToDelete(rows)
        if (duplicateIds.isEmpty()) return 0
        var deleted = 0
        duplicateIds.chunked(400).forEach { chunk ->
            deleted += dao.deleteSessionsByIds(chunk)
        }
        return deleted
    }
}

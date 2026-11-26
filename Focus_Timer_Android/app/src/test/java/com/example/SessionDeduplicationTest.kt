package com.example

import com.example.data.local.SessionDeduplicator
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.util.AnalyticsEngine
import com.example.util.AnalyticsTimeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDeduplicationTest {

    @Test
    fun deduplicateList_collapsesExactAndNearTimestampDuplicates() {
        val baseTs = 1_764_100_000_000L
        val sessions = listOf(
            StudySessionEntity(id = 1, date = "2026-11-25", subject = "Math", minutes = 60, timestamp = baseTs),
            // Exact timestamp duplicate from re-import
            StudySessionEntity(id = 2, date = "2026-11-25", subject = "Math", minutes = 60, timestamp = baseTs),
            // 30-second skew duplicate (e.g. offline fallback vs cloud timestamp)
            StudySessionEntity(id = 3, date = "2026-11-25", subject = "math", minutes = 60, timestamp = baseTs + 30_000L),
            // Genuine second session 40 minutes later
            StudySessionEntity(id = 4, date = "2026-11-25", subject = "Math", minutes = 40, timestamp = baseTs + 40 * 60_000L)
        )

        val deduped = SessionDeduplicator.deduplicateList(sessions)
        assertEquals(2, deduped.size)
        assertEquals(100, deduped.sumOf { it.minutes })

        val idsToDelete = SessionDeduplicator.findDuplicateIdsToDelete(sessions)
        assertEquals(listOf(2L, 3L), idsToDelete)
    }

    @Test
    fun isNonStudySession_detectsFlagAndSubjectNamesCaseInsensitively() {
        val subjects = listOf(
            SubjectEntity(id = 1, name = "Math", isNonStudy = false),
            SubjectEntity(id = 2, name = "Sleep", isNonStudy = true)
        )
        val flagged = StudySessionEntity(id = 1, date = "2026-11-25", subject = "Break", minutes = 30, isNonStudy = true)
        val bySubject = StudySessionEntity(id = 2, date = "2026-11-25", subject = "SLEEP - Nap", minutes = 45, isNonStudy = false)
        val study = StudySessionEntity(id = 3, date = "2026-11-25", subject = "Math", minutes = 90, isNonStudy = false)

        assertTrue(SessionDeduplicator.isNonStudySession(flagged, subjects))
        assertTrue(SessionDeduplicator.isNonStudySession(bySubject, subjects))
        assertFalse(SessionDeduplicator.isNonStudySession(study, subjects))
    }

    @Test
    fun analyticsEngine_excludesDuplicatesAndNonStudyFromTotals() {
        val baseTs = System.currentTimeMillis() - 60_000L
        val sessions = listOf(
            StudySessionEntity(id = 1, date = "2026-11-26", subject = "Physics", minutes = 120, timestamp = baseTs, isNonStudy = false),
            StudySessionEntity(id = 2, date = "2026-11-26", subject = "Physics", minutes = 120, timestamp = baseTs, isNonStudy = false),
            StudySessionEntity(id = 3, date = "2026-11-26", subject = "Sleep", minutes = 360, timestamp = baseTs + 300_000L, isNonStudy = true)
        )

        val report = AnalyticsEngine.computeReport(sessions, AnalyticsTimeframe.ALL_TIME)
        assertEquals(1, report.sessionCount)
        assertEquals(120, report.totalMinutes)
    }
}

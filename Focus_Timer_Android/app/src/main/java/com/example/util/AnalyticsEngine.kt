package com.example.util

import com.example.data.model.StudySessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

enum class AnalyticsTimeframe(val label: String, val days: Int?) {
    LAST_7_DAYS("7 Days", 7),
    LAST_30_DAYS("30 Days", 30),
    ALL_TIME("All Time", null)
}

data class CircadianBucket(
    val id: String,
    val label: String,
    val minutes: Int,
    val percentageOfTotal: Int
)

data class SubjectEquilibriumItem(
    val subjectName: String,
    val minutes: Int,
    val hours: Double,
    val percentage: Int,
    val daysAgo: Int,
    val isNeglected: Boolean,
    val sessionCount: Int = 1,
    val stabilityDays: Double = 2.5,
    val retentionPct: Int = 100,
    val recallStatus: String = "Optimal Retention"
)

data class CognitiveWorkTypeItem(
    val workTypeName: String,
    val minutes: Int,
    val percentage: Int
)

data class SmartInsight(
    val icon: String,
    val title: String,
    val description: String
)

data class AnalyticsReport(
    val timeframe: AnalyticsTimeframe,
    val totalMinutes: Int,
    val totalHours: Double,
    val sessionCount: Int,
    val activeDaysCount: Int,
    val avgSessionMinutes: Int,
    val deepWorkMinutes: Int,
    val deepWorkRatio: Int,
    val velocityPercentage: Int,
    val peakFocusWindow: String,
    val circadianBuckets: List<CircadianBucket>,
    val subjectEquilibrium: List<SubjectEquilibriumItem>,
    val cognitiveWorkTypes: List<CognitiveWorkTypeItem>,
    val smartInsights: List<SmartInsight>,
    val filteredSessions: List<StudySessionEntity>,
    val focusQualityScore: Int = 0,
    val focusQualityTier: String = "Fragmented Focus",
    val consistencyPct: Int = 0,
    val goalHitRate: Int = 0,
    val pacingStability: Int = 0
) {
    companion object {
        fun empty(timeframe: AnalyticsTimeframe = AnalyticsTimeframe.LAST_7_DAYS) = AnalyticsReport(
            timeframe = timeframe,
            totalMinutes = 0,
            totalHours = 0.0,
            sessionCount = 0,
            activeDaysCount = 0,
            avgSessionMinutes = 0,
            deepWorkMinutes = 0,
            deepWorkRatio = 0,
            velocityPercentage = 0,
            peakFocusWindow = "Morning (06:00 - 12:00)",
            circadianBuckets = emptyList(),
            subjectEquilibrium = emptyList(),
            cognitiveWorkTypes = emptyList(),
            smartInsights = emptyList(),
            filteredSessions = emptyList(),
            focusQualityScore = 0,
            focusQualityTier = "Fragmented Focus",
            consistencyPct = 0,
            goalHitRate = 0,
            pacingStability = 0
        )
    }
}

object AnalyticsEngine {

    fun computeReport(
        allSessions: List<StudySessionEntity>,
        timeframe: AnalyticsTimeframe
    ): AnalyticsReport {
        val studySessions = allSessions.filter { !it.isNonStudy }
        val now = Calendar.getInstance()
        val daysLimit = timeframe.days

        val filteredSessions = if (daysLimit != null) {
            val limitCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysLimit + 1) }
            val limitStr = DateFormatterCache.formatIsoDate(limitCal.timeInMillis)
            studySessions.filter { it.date >= limitStr }
        } else {
            studySessions
        }

        // Prior period sessions for velocity calculation
        val priorSessions = if (daysLimit != null) {
            val startPrior = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2 * daysLimit + 1) }
            val endPrior = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysLimit) }
            val startStr = DateFormatterCache.formatIsoDate(startPrior.timeInMillis)
            val endStr = DateFormatterCache.formatIsoDate(endPrior.timeInMillis)
            studySessions.filter { it.date in startStr..endStr }
        } else {
            emptyList()
        }

        val totalMinutes = filteredSessions.sumOf { it.minutes }
        val totalHours = totalMinutes / 60.0
        val sessionCount = filteredSessions.size
        val activeDaysCount = filteredSessions.map { it.date }.distinct().size
        val avgSessionMinutes = if (sessionCount > 0) totalMinutes / sessionCount else 0

        // Deep Work: sessions >= 45m
        val deepWorkMinutes = filteredSessions.filter { it.minutes >= 45 }.sumOf { it.minutes }
        val deepWorkRatio = if (totalMinutes > 0) ((deepWorkMinutes.toFloat() / totalMinutes) * 100).toInt() else 0

        // Deterministic Algorithmic Pillars for Focus Quality Score (FQS / Cognitive Quality Index: 0-100)
        // 1. Consistency Percentage (C)
        val daysInPeriod = when (timeframe) {
            AnalyticsTimeframe.LAST_7_DAYS -> 7
            AnalyticsTimeframe.LAST_30_DAYS -> 30
            AnalyticsTimeframe.ALL_TIME -> {
                if (studySessions.isNotEmpty()) {
                    val sortedDates = studySessions.map { it.date }.sorted()
                    val dFirst = DateFormatterCache.parseIsoDate(sortedDates.first())
                    val dLast = DateFormatterCache.parseIsoDate(sortedDates.last())
                    if (dFirst != null && dLast != null) {
                        max(1, (((dLast.time - dFirst.time) / (24 * 3600 * 1000)).toInt() + 1))
                    } else 1
                } else 1
            }
        }
        val consistencyPct = if (daysInPeriod > 0) {
            ((activeDaysCount.toFloat() / daysInPeriod) * 100).toInt().coerceIn(0, 100)
        } else 0

        // 2. Daily Goal Hit Rate (G, standard target 120m)
        val dailyMinsMap = mutableMapOf<String, Int>()
        filteredSessions.forEach { s ->
            dailyMinsMap[s.date] = (dailyMinsMap[s.date] ?: 0) + s.minutes
        }
        val daysMetTarget = dailyMinsMap.values.count { it >= 120 }
        val goalHitRate = if (activeDaysCount > 0) {
            ((daysMetTarget.toFloat() / activeDaysCount) * 100).toInt().coerceIn(0, 100)
        } else 0

        // 3. Session Pacing Stability (P, benchmark 50m)
        val pacingStability = ((avgSessionMinutes.toFloat() / 50f) * 100).toInt().coerceIn(0, 100)

        // 4. Focus Quality Score (FQS): 0.35*DeepWork + 0.25*Consistency + 0.25*GoalHit + 0.15*Pacing
        val focusQualityScore = if (totalMinutes > 0) {
            (0.35f * deepWorkRatio + 0.25f * consistencyPct + 0.25f * goalHitRate + 0.15f * pacingStability).toInt().coerceIn(0, 100)
        } else 0

        val focusQualityTier = when {
            focusQualityScore >= 85 -> "Elite Cognitive Focus"
            focusQualityScore >= 70 -> "Optimal Focus Stamina"
            focusQualityScore >= 50 -> "Moderate Pacing"
            else -> "Fragmented Focus"
        }

        // Velocity
        val priorMinutes = priorSessions.sumOf { it.minutes }
        val velocityPercentage = if (priorMinutes > 0 && totalMinutes > 0) {
            (((totalMinutes - priorMinutes).toFloat() / priorMinutes) * 100).toInt()
        } else if (totalMinutes > 0 && daysLimit != null) {
            100
        } else {
            0
        }

        // Circadian Time-of-Day Distribution (Single reusable Calendar)
        var morningMins = 0
        var afternoonMins = 0
        var eveningMins = 0
        var nightMins = 0
        val hourlyMins = IntArray(24)
        val reusableCal = Calendar.getInstance()

        filteredSessions.forEach { s ->
            reusableCal.timeInMillis = s.timestamp
            val hour = reusableCal.get(Calendar.HOUR_OF_DAY)
            hourlyMins[hour] += s.minutes

            when (hour) {
                in 5..11 -> morningMins += s.minutes
                in 12..16 -> afternoonMins += s.minutes
                in 17..21 -> eveningMins += s.minutes
                else -> nightMins += s.minutes
            }
        }

        fun toPct(m: Int) = if (totalMinutes > 0) ((m.toFloat() / totalMinutes) * 100).toInt() else 0

        val circadianBuckets = listOf(
            CircadianBucket("morning", "🌅 Morning (5am - 12pm)", morningMins, toPct(morningMins)),
            CircadianBucket("afternoon", "☀️ Afternoon (12pm - 5pm)", afternoonMins, toPct(afternoonMins)),
            CircadianBucket("evening", "🌆 Evening (5pm - 10pm)", eveningMins, toPct(eveningMins)),
            CircadianBucket("night", "🌙 Night (10pm - 5am)", nightMins, toPct(nightMins))
        )

        // Peak 2-hour window
        var max2H = 0
        var peakStartHour = 9
        for (h in 0..22) {
            val sum = hourlyMins[h] + hourlyMins[h + 1]
            if (sum > max2H) {
                max2H = sum
                peakStartHour = h
            }
        }
        fun formatHourStr(h: Int): String {
            val ampm = if (h >= 12) "PM" else "AM"
            val hr = when (h % 12) {
                0 -> 12
                else -> h % 12
            }
            return "$hr:00 $ampm"
        }
        val peakFocusWindow = if (max2H > 0) {
            "${formatHourStr(peakStartHour)} - ${formatHourStr(peakStartHour + 2)}"
        } else {
            "N/A (No study in period)"
        }

        // Subject Equilibrium & Ebbinghaus Scientific Recall Matrix
        val lastDatePerSubject = mutableMapOf<String, String>()
        val subjectTotalCountMap = mutableMapOf<String, Int>()
        studySessions.forEach { s ->
            val mainSub = s.subject.split(" - ").firstOrNull() ?: s.subject
            subjectTotalCountMap[mainSub] = (subjectTotalCountMap[mainSub] ?: 0) + 1
            val curr = lastDatePerSubject[mainSub]
            if (curr == null || s.date > curr) {
                lastDatePerSubject[mainSub] = s.date
            }
        }

        val subjectMinutesMap = mutableMapOf<String, Int>()
        filteredSessions.forEach { s ->
            val mainSub = s.subject.split(" - ").firstOrNull() ?: s.subject
            subjectMinutesMap[mainSub] = (subjectMinutesMap[mainSub] ?: 0) + s.minutes
        }

        val todayTime = now.timeInMillis
        val subjectEquilibrium = subjectMinutesMap.entries.sortedByDescending { it.value }.map { (name, mins) ->
            val pct = toPct(mins)
            val lastDateStr = lastDatePerSubject[name]
            var daysAgo = 0
            if (lastDateStr != null) {
                val d = DateFormatterCache.parseIsoDate(lastDateStr)
                if (d != null) {
                    daysAgo = max(0, ((todayTime - d.time) / (24 * 3600 * 1000)).toInt())
                }
            }
            val count = subjectTotalCountMap[name] ?: 1
            // Hermann Ebbinghaus Spaced Repetition Stability Factor (S):
            // 1 review -> S=2.5 days, 2 reviews -> S=5.0 days, 3+ reviews -> S=9.0 days
            val stabilityDays = when {
                count <= 1 -> 2.5
                count == 2 -> 5.0
                else -> 9.0
            }
            // Retention Curve: R = round(100 * exp(-t / S))
            val retentionPct = if (daysAgo == 0) 100 else {
                (100.0 * kotlin.math.exp(-daysAgo.toDouble() / stabilityDays)).toInt().coerceIn(0, 100)
            }
            val recallStatus = when {
                retentionPct < 60 -> "Critical Recall Due"
                retentionPct < 80 -> "Review Recommended"
                else -> "Optimal Retention"
            }

            SubjectEquilibriumItem(
                subjectName = name,
                minutes = mins,
                hours = mins / 60.0,
                percentage = pct,
                daysAgo = daysAgo,
                isNeglected = daysAgo >= 3,
                sessionCount = count,
                stabilityDays = stabilityDays,
                retentionPct = retentionPct,
                recallStatus = recallStatus
            )
        }

        // Cognitive Work-Type Distribution
        val workTypeMap = mutableMapOf<String, Int>()
        filteredSessions.forEach { s ->
            workTypeMap[s.workType] = (workTypeMap[s.workType] ?: 0) + s.minutes
        }
        val cognitiveWorkTypes = workTypeMap.entries.sortedByDescending { it.value }.map { (type, mins) ->
            CognitiveWorkTypeItem(type, mins, toPct(mins))
        }

        // Deterministic Cognitive Diagnostic Insights (100% Deterministic Algorithmic Logic)
        val smartInsights = mutableListOf<SmartInsight>()
        if (totalMinutes > 0) {
            val topBucket = circadianBuckets.maxByOrNull { it.minutes }
            if (topBucket != null) {
                smartInsights.add(
                    SmartInsight(
                        icon = "🌅",
                        title = "Circadian Prime",
                        description = "Your peak focus window is $peakFocusWindow (${topBucket.percentageOfTotal}% of total focus). Prioritize conceptual subjects here."
                    )
                )
            }

            smartInsights.add(
                SmartInsight(
                    icon = "🎯",
                    title = "Focus Quality Score ($focusQualityScore/100)",
                    description = "Tier: $focusQualityTier. Consistency: $consistencyPct%, Goal Hit: $goalHitRate%, Deep Work: $deepWorkRatio%."
                )
            )

            if (deepWorkRatio >= 60) {
                smartInsights.add(
                    SmartInsight(
                        icon = "🧠",
                        title = "Deep Work Stamina",
                        description = "$deepWorkRatio% of your study occurs in sustained sessions (≥45m). Excellent cognitive endurance."
                    )
                )
            } else {
                smartInsights.add(
                    SmartInsight(
                        icon = "⏱️",
                        title = "Session Pacing",
                        description = "${100 - deepWorkRatio}% of study time is in short sessions. Try lengthening focus blocks to deepen recall."
                    )
                )
            }

            val criticalRecall = subjectEquilibrium.filter { it.retentionPct < 60 }
            if (criticalRecall.isNotEmpty()) {
                val names = criticalRecall.take(2).joinToString(", ") { "${it.subjectName} (R=${it.retentionPct}%, ${it.daysAgo}d ago)" }
                smartInsights.add(
                    SmartInsight(
                        icon = "⚠️",
                        title = "Ebbinghaus Memory Decay",
                        description = "$names fallen below 60% retention. Critical active recall session required today to restore memory stability."
                    )
                )
            } else {
                val neglected = subjectEquilibrium.filter { it.isNeglected }
                if (neglected.isNotEmpty()) {
                    val names = neglected.take(2).joinToString(", ") { "${it.subjectName} (${it.daysAgo}d ago)" }
                    smartInsights.add(
                        SmartInsight(
                            icon = "⚠️",
                            title = "Neglect Warning",
                            description = "$names untouched recently. Schedule a recall session to preserve memory retention."
                        )
                    )
                } else if (subjectEquilibrium.size > 1) {
                    smartInsights.add(
                        SmartInsight(
                            icon = "⚖️",
                            title = "Curriculum Equilibrium",
                            description = "All active subjects were reviewed within the last 48 hours. Excellent syllabus balance."
                        )
                    )
                }
            }

            if (daysLimit != null && (totalMinutes > 0 || priorMinutes > 0)) {
                val arrow = if (velocityPercentage >= 0) "▲" else "▼"
                val trendWord = if (velocityPercentage >= 0) "acceleration" else "dip"
                smartInsights.add(
                    SmartInsight(
                        icon = "📈",
                        title = "Study Velocity",
                        description = "$arrow ${kotlin.math.abs(velocityPercentage)}% $trendWord compared to previous $daysLimit days."
                    )
                )
            }
        } else {
            // totalMinutes == 0
            if (studySessions.isNotEmpty()) {
                val sortedDates = studySessions.map { it.date }.sorted()
                val oldest = sortedDates.first()
                val newest = sortedDates.last()
                val allHours = String.format(Locale.US, "%.1f", studySessions.sumOf { it.minutes } / 60.0)
                smartInsights.add(
                    SmartInsight(
                        icon = "[INFO]",
                        title = "Historical Data Available",
                        description = "No sessions logged in ${timeframe.label}. You have ${studySessions.size} total sessions ($allHours hrs) between $oldest and $newest. Switch to 'All Time' to view full history."
                    )
                )
            } else {
                smartInsights.add(
                    SmartInsight(
                        icon = "💡",
                        title = "Building Insights",
                        description = "Complete study sessions to generate personalized circadian rhythm, stamina, and curriculum balance feedback."
                    )
                )
            }
        }

        return AnalyticsReport(
            timeframe = timeframe,
            totalMinutes = totalMinutes,
            totalHours = totalHours,
            sessionCount = sessionCount,
            activeDaysCount = activeDaysCount,
            avgSessionMinutes = avgSessionMinutes,
            deepWorkMinutes = deepWorkMinutes,
            deepWorkRatio = deepWorkRatio,
            velocityPercentage = velocityPercentage,
            peakFocusWindow = peakFocusWindow,
            circadianBuckets = circadianBuckets,
            subjectEquilibrium = subjectEquilibrium,
            cognitiveWorkTypes = cognitiveWorkTypes,
            smartInsights = smartInsights,
            filteredSessions = filteredSessions,
            focusQualityScore = focusQualityScore,
            focusQualityTier = focusQualityTier,
            consistencyPct = consistencyPct,
            goalHitRate = goalHitRate,
            pacingStability = pacingStability
        )
    }
}

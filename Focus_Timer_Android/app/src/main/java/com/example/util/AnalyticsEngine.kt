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

data class ExamGoal(
    val examName: String = "Target Exam / Syllabus",
    val targetDate: String = "", // "YYYY-MM-DD"
    val targetHours: Double = 150.0,
    val subjectScope: Set<String> = emptySet()
)

data class ExamProjection(
    val examName: String,
    val targetDate: String,
    val targetHours: Double,
    val completedHours: Double,
    val remainingHours: Double,
    val daysRemaining: Int,
    val requiredDailyHours: Double,
    val currentDailyHours: Double,
    val paceDeltaHours: Double,
    val paceRatio: Double,
    val projectedDate: String,
    val status: String,
    val statusBadgeText: String,
    val statusDescription: String,
    val subjectScope: Set<String>,
    val progressPercentage: Int = if (targetHours > 0) Math.round((completedHours / targetHours) * 100).toInt().coerceIn(0, 100) else 0
)

data class FatigueReport(
    val fatigueScore: Int = 0,
    val fatigueTier: String = "OPTIMAL_RECOVERY",
    val tierLabel: String = "Optimal Recovery 🔋",
    val adviceText: String = "Optimal Cognitive Recovery: High endurance reserve, ready for intensive focus blocks.",
    val avgDailyHours7d: Double = 0.0,
    val consecutiveHighDays: Int = 0,
    val recoveryDaysCount: Int = 7
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
    val pacingStability: Int = 0,
    val hourlyMins: IntArray = IntArray(24),
    val examProjection: ExamProjection? = null,
    val fatigueReport: FatigueReport = FatigueReport()
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
            pacingStability = 0,
            hourlyMins = IntArray(24),
            examProjection = null,
            fatigueReport = FatigueReport()
        )
    }
}

object AnalyticsEngine {

    fun computeReport(
        allSessions: List<StudySessionEntity>,
        timeframe: AnalyticsTimeframe,
        examGoal: ExamGoal? = null,
        dailyTargetMinutes: Int = 120
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
        val avgSessionMinutes = if (sessionCount > 0) Math.round(totalMinutes.toDouble() / sessionCount).toInt() else 0

        // Deep Work: sessions >= 45m
        val deepWorkMinutes = filteredSessions.filter { it.minutes >= 45 }.sumOf { it.minutes }
        val deepWorkRatio = if (totalMinutes > 0) Math.round((deepWorkMinutes.toDouble() / totalMinutes) * 100).toInt() else 0

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
                        max(1, Math.round((dLast.time - dFirst.time).toDouble() / (24 * 3600 * 1000)).toInt() + 1)
                    } else 1
                } else 1
            }
        }
        val consistencyPct = if (daysInPeriod > 0) {
            Math.round((activeDaysCount.toDouble() / daysInPeriod) * 100).toInt().coerceIn(0, 100)
        } else 0

        // 2. Daily Goal Hit Rate (G, user configurable daily target)
        val dailyMinsMap = mutableMapOf<String, Int>()
        filteredSessions.forEach { s ->
            dailyMinsMap[s.date] = (dailyMinsMap[s.date] ?: 0) + s.minutes
        }
        val daysMetTarget = dailyMinsMap.values.count { it >= dailyTargetMinutes }
        val goalHitRate = if (activeDaysCount > 0) {
            Math.round((daysMetTarget.toDouble() / activeDaysCount) * 100).toInt().coerceIn(0, 100)
        } else 0

        // 3. Session Pacing Stability (P, benchmark 50m)
        val pacingStability = Math.round((avgSessionMinutes.toDouble() / 50.0) * 100).toInt().coerceIn(0, 100)

        // 4. Focus Quality Score (FQS): 0.35*DeepWork + 0.25*Consistency + 0.25*GoalHit + 0.15*Pacing
        val focusQualityScore = if (totalMinutes > 0) {
            Math.round(0.35 * deepWorkRatio + 0.25 * consistencyPct + 0.25 * goalHitRate + 0.15 * pacingStability).toInt().coerceIn(0, 100)
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
            Math.round(((totalMinutes - priorMinutes).toDouble() / priorMinutes) * 100).toInt()
        } else if (totalMinutes > 0 && daysLimit != null) {
            100
        } else {
            0
        }

        // Circadian Time-of-Day Distribution with exact hour-boundary splitting
        val hourlyDouble = DoubleArray(24)
        val hourlyMins = IntArray(24)
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        filteredSessions.forEach { s ->
            if (s.minutes <= 0) return@forEach
            val endTs = s.timestamp
            val startTs = endTs - (s.minutes.toLong() * 60000L)

            startCal.timeInMillis = startTs
            startCal.set(Calendar.MINUTE, 0)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)

            var curHourStart = startCal.timeInMillis
            while (curHourStart < endTs) {
                val nextHourStart = curHourStart + 3600000L
                val overlapStart = maxOf(startTs, curHourStart)
                val overlapEnd = minOf(endTs, nextHourStart)

                if (overlapEnd > overlapStart) {
                    endCal.timeInMillis = curHourStart
                    val h = endCal.get(Calendar.HOUR_OF_DAY)
                    val mins = (overlapEnd - overlapStart).toDouble() / 60000.0
                    hourlyDouble[h] += mins
                }
                curHourStart = nextHourStart
            }
        }
        for (h in 0..23) {
            hourlyMins[h] = Math.round(hourlyDouble[h]).toInt()
        }

        var morningMins = 0
        var afternoonMins = 0
        var eveningMins = 0
        var nightMins = 0
        for (h in 0..23) {
            val m = hourlyMins[h]
            when (h) {
                in 5..11 -> morningMins += m
                in 12..16 -> afternoonMins += m
                in 17..21 -> eveningMins += m
                else -> nightMins += m
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

        // 🎯 Compute Exam / Syllabus Projection
        val effectiveGoal = examGoal ?: ExamGoal()
        val examName = effectiveGoal.examName.ifBlank { "Target Exam / Syllabus" }
        val targetHours = effectiveGoal.targetHours.coerceAtLeast(1.0)
        val targetDateStr = effectiveGoal.targetDate
        val subjectScope = effectiveGoal.subjectScope

        val scopedSessions = studySessions.filter { s ->
            if (subjectScope.isEmpty()) true
            else {
                val mainSub = s.subject.split(" - ").firstOrNull() ?: s.subject
                subjectScope.contains(mainSub) || subjectScope.contains(s.subject)
            }
        }

        val completedMinutes = scopedSessions.sumOf { it.minutes }
        val completedHours = completedMinutes / 60.0
        val remainingHours = maxOf(0.0, targetHours - completedHours)

        var daysRemaining = 0
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (targetDateStr.isNotBlank()) {
            val targetDateObj = DateFormatterCache.parseIsoDate(targetDateStr)
            if (targetDateObj != null) {
                val targetCal = Calendar.getInstance().apply {
                    time = targetDateObj
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                daysRemaining = Math.round((targetCal.timeInMillis - todayMidnight.timeInMillis).toDouble() / 86400000.0).toInt()
            }
        }

        val requiredDailyHours = if (daysRemaining > 0) remainingHours / daysRemaining else 0.0

        val sevenDaysAgoCal = Calendar.getInstance().apply {
            timeInMillis = todayMidnight.timeInMillis
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val limit7dStr = DateFormatterCache.formatIsoDate(sevenDaysAgoCal.timeInMillis)
        val todayIsoStr = DateFormatterCache.formatIsoDate(todayMidnight.timeInMillis)

        val last7DaysScoped = scopedSessions.filter { it.date in limit7dStr..todayIsoStr }
        val last7DaysMins = last7DaysScoped.sumOf { it.minutes }
        val currentDailyHours = (last7DaysMins / 60.0) / 7.0 // Div by 7 calendar days

        val paceDeltaHours = currentDailyHours - requiredDailyHours
        val paceRatio = if (requiredDailyHours > 0.0) currentDailyHours / requiredDailyHours else 1.0

        val (status, statusBadgeText, statusDescription) = when {
            remainingHours <= 0.0 -> Triple("COMPLETE", "GOAL ACHIEVED 🏆", "Congratulations! You have completed 100% of your targeted syllabus hours.")
            daysRemaining < 0 -> Triple("EXPIRED", "DEADLINE PASSED ⚠️", "The target exam date has passed. Edit the target date in settings to recalibrate.")
            daysRemaining == 0 -> Triple("DEADLINE_TODAY", "EXAM TODAY ⏳", "Target deadline is today! Focus on high-yield formulas and active recall.")
            paceRatio >= 1.0 -> Triple("ON_TRACK", "ON TRACK 🎯", "Velocity surplus of +${String.format(Locale.US, "%.1f", paceDeltaHours)}h/day. Syllabus will be completed on schedule.")
            paceRatio >= 0.8 -> Triple("MINOR_DEFICIT", "MINOR DEFICIT ⚠️", "Velocity lag of ${String.format(Locale.US, "%.1f", kotlin.math.abs(paceDeltaHours))}h/day. Increase study blocks by ~${Math.round(kotlin.math.abs(paceDeltaHours) * 60)} mins/day to regain pace.")
            else -> Triple("CRITICAL_LAG", "CRITICAL LAG 🚨", "Significant pace deficit of ${String.format(Locale.US, "%.1f", kotlin.math.abs(paceDeltaHours))}h/day. Urgent pace recalibration needed to cover remaining ${String.format(Locale.US, "%.1f", remainingHours)}h.")
        }

        val projectedDate = when {
            remainingHours <= 0.0 -> "Completed"
            currentDailyHours >= 0.05 -> {
                val daysNeeded = kotlin.math.ceil(remainingHours / currentDailyHours).toInt()
                val projCal = Calendar.getInstance().apply {
                    timeInMillis = todayMidnight.timeInMillis
                    add(Calendar.DAY_OF_YEAR, daysNeeded)
                }
                DateFormatterCache.formatIsoDate(projCal.timeInMillis)
            }
            else -> "Indeterminate (0h pace)"
        }

        val examProjection = ExamProjection(
            examName = examName,
            targetDate = targetDateStr,
            targetHours = targetHours,
            completedHours = completedHours,
            remainingHours = remainingHours,
            daysRemaining = daysRemaining,
            requiredDailyHours = requiredDailyHours,
            currentDailyHours = currentDailyHours,
            paceDeltaHours = paceDeltaHours,
            paceRatio = paceRatio,
            projectedDate = projectedDate,
            status = status,
            statusBadgeText = statusBadgeText,
            statusDescription = statusDescription,
            subjectScope = subjectScope
        )

        // 🔋 Compute Cognitive Workload & Fatigue Index
        val daily7dMins = IntArray(7)
        val day7dStrings = Array(7) { "" }
        for (i in 6 downTo 0) {
            val dCal = Calendar.getInstance().apply {
                timeInMillis = todayMidnight.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            day7dStrings[6 - i] = DateFormatterCache.formatIsoDate(dCal.timeInMillis)
        }

        studySessions.forEach { s ->
            val idx = day7dStrings.indexOf(s.date)
            if (idx != -1) {
                daily7dMins[idx] += s.minutes
            }
        }

        val daily7dHours = daily7dMins.map { it / 60.0 }
        val total7dHours = daily7dHours.sum()
        val avgDailyHours7d = total7dHours / 7.0

        val loadFactor = minOf(1.0, avgDailyHours7d / 6.0)

        var consecutiveHighDays = 0
        for (i in 6 downTo 0) {
            if (daily7dHours[i] >= 5.0) {
                consecutiveHighDays++
            } else {
                break
            }
        }
        val streakFactor = minOf(1.0, consecutiveHighDays / 4.0)

        val recoveryDaysCount = daily7dHours.count { it < 2.0 }
        val recoveryRatio = recoveryDaysCount / 7.0

        val rawScore = 100.0 * (0.40 * loadFactor + 0.35 * streakFactor + 0.25 * (1.0 - recoveryRatio))
        val fatigueScore = Math.round(rawScore).toInt().coerceIn(0, 100)

        val (fatigueTier, tierLabel, adviceText) = when {
            fatigueScore >= 70 -> Triple(
                "HIGH_FATIGUE_LOAD",
                "High Strain ⚠️",
                "Elevated Cognitive Strain ($fatigueScore/100): Extended peak exertion detected ($consecutiveHighDays consecutive 5h+ days). Schedule structured active rest to prevent fatigue."
            )
            fatigueScore >= 40 -> Triple(
                "SUSTAINED_HIGH_LOAD",
                "Sustained Workload ⚡",
                "Sustained High Workload ($fatigueScore/100): Consistent daily output (${String.format(Locale.US, "%.1f", avgDailyHours7d)}h/day). Maintain proper hydration and short recovery intervals."
            )
            else -> Triple(
                "OPTIMAL_RECOVERY",
                "Optimal Recovery 🔋",
                "Optimal Cognitive Recovery ($fatigueScore/100): Well-paced focus routines with adequate recovery ($recoveryDaysCount light/rest days). High cognitive reserve."
            )
        }

        val fatigueReport = FatigueReport(
            fatigueScore = fatigueScore,
            fatigueTier = fatigueTier,
            tierLabel = tierLabel,
            adviceText = adviceText,
            avgDailyHours7d = avgDailyHours7d,
            consecutiveHighDays = consecutiveHighDays,
            recoveryDaysCount = recoveryDaysCount
        )

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

            smartInsights.add(
                SmartInsight(
                    icon = "🔋",
                    title = "Cognitive Workload Index ($fatigueScore/100)",
                    description = adviceText
                )
            )

            if (examProjection.status == "ON_TRACK") {
                smartInsights.add(
                    SmartInsight(
                        icon = "🎯",
                        title = "Exam Horizon Projection",
                        description = "On schedule for $examName (${String.format(Locale.US, "%.1f", completedHours)}h done, pace: ${String.format(Locale.US, "%.1f", currentDailyHours)}h/d). Projected completion: $projectedDate."
                    )
                )
            } else if (examProjection.status == "MINOR_DEFICIT" || examProjection.status == "CRITICAL_LAG") {
                smartInsights.add(
                    SmartInsight(
                        icon = "⚠️",
                        title = "Exam Pace Deficit",
                        description = "$examName requires ${String.format(Locale.US, "%.1f", requiredDailyHours)}h/day, current velocity is ${String.format(Locale.US, "%.1f", currentDailyHours)}h/d. $statusDescription"
                    )
                )
            }

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
            pacingStability = pacingStability,
            hourlyMins = hourlyMins,
            examProjection = examProjection,
            fatigueReport = fatigueReport
        )
    }
}

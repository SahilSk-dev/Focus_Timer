package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.StudySessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PdfExportHelper {

    private fun formatTimeOnly(ts: Long): String {
        if (ts <= 0) return ""
        val cal = Calendar.getInstance().apply { time = Date(ts) }
        var h = cal.get(Calendar.HOUR)
        if (h == 0) h = 12
        val m = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val ampm = if (cal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
        return "$h:$m $ampm"
    }

    private fun formatTimeRangeOnlyTime(endTs: Long, minutes: Int): String {
        if (endTs <= 0) return "-"
        val startTs = endTs - (minutes.toLong() * 60L * 1000L)
        return "${formatTimeOnly(startTs)} - ${formatTimeOnly(endTs)}"
    }

    fun generateAndSharePdf(
        context: Context,
        sessions: List<StudySessionEntity>,
        rangeLabel: String
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)
        var pageNumber = 1
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val statPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(201, 150, 47) // Web gold [201, 150, 47]
            style = Paint.Style.FILL
        }

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val rowBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val altRowBgPaint = Paint().apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
        }

        val cellTextPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 9.5f
            isAntiAlias = true
        }

        val gridPaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(140, 140, 140)
            textSize = 9f
            isAntiAlias = true
        }

        // Title resolution
        val titleStr = when (rangeLabel.lowercase(Locale.US)) {
            "all", "all time" -> "Full Study Report"
            "today" -> "Study Report (Today)"
            "24h", "last 24 hours" -> "Study Report (Last 24h)"
            "48h", "last 48 hours" -> "Study Report (Last 48h)"
            "7 days", "last 7 days" -> "Study Report (Last 7 Days)"
            "30 days", "last 30 days" -> "Study Report (Last 30 Days)"
            else -> "Study Report ($rangeLabel)"
        }

        val studyMin = sessions.filter { !it.isNonStudy }.sumOf { it.minutes }
        val nonStudyMin = sessions.filter { it.isNonStudy }.sumOf { it.minutes }
        val studyHr = String.format(Locale.US, "%.1f", studyMin / 60.0)
        val nonStudyHr = String.format(Locale.US, "%.1f", nonStudyMin / 60.0)

        // Draw top header
        canvas.drawColor(Color.WHITE)
        canvas.drawText("Focus Study Timer", 36f, 36f, titlePaint)
        canvas.drawText(titleStr, 36f, 54f, subtitlePaint)
        canvas.drawText("Total Study: $studyHr hrs", 36f, 74f, statPaint)
        canvas.drawText("Non-Study: $nonStudyHr hrs", 36f, 90f, statPaint)

        val colX = floatArrayOf(36f, 106f, 216f, 366f, 446f, 498f, 559f)
        var yPos = 108f

        fun drawHeader(c: Canvas, y: Float) {
            c.drawRect(36f, y, 559f, y + 22f, headerBgPaint)
            c.drawRect(36f, y, 559f, y + 22f, gridPaint)
            for (i in 1 until colX.size - 1) {
                c.drawLine(colX[i], y, colX[i], y + 22f, gridPaint)
            }
            c.drawText("Date", colX[0] + 6f, y + 15f, headerTextPaint)
            c.drawText("Time", colX[1] + 6f, y + 15f, headerTextPaint)
            c.drawText("Subject", colX[2] + 6f, y + 15f, headerTextPaint)
            c.drawText("Work Type", colX[3] + 6f, y + 15f, headerTextPaint)
            c.drawText("Minutes", colX[4] + 6f, y + 15f, headerTextPaint)
            c.drawText("Category", colX[5] + 6f, y + 15f, headerTextPaint)
        }

        drawHeader(canvas, yPos)
        yPos += 22f

        // Sort ascending by timestamp, matching Web tableData sort
        val sortedSessions = sessions.sortedBy { it.timestamp }

        for ((idx, session) in sortedSessions.withIndex()) {
            if (yPos + 20f > 800f) {
                // Finish page and begin next page
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                yPos = 36f
                drawHeader(canvas, yPos)
                yPos += 22f
            }

            val isAlt = (idx % 2 == 1)
            val bg = if (isAlt) altRowBgPaint else rowBgPaint
            canvas.drawRect(36f, yPos, 559f, yPos + 20f, bg)
            canvas.drawRect(36f, yPos, 559f, yPos + 20f, gridPaint)
            for (i in 1 until colX.size - 1) {
                canvas.drawLine(colX[i], yPos, colX[i], yPos + 20f, gridPaint)
            }

            val dateStr = session.date
            val timeRange = formatTimeRangeOnlyTime(session.timestamp, session.minutes)
            val subjectStr = if (session.subject.length > 25) session.subject.take(23) + ".." else session.subject
            val workTypeStr = session.workType.ifBlank { "N/A" }
            val minutesStr = "${session.minutes} min"
            val categoryStr = if (session.isNonStudy) "Non-Study" else "Study"

            canvas.drawText(dateStr, colX[0] + 6f, yPos + 14f, cellTextPaint)
            canvas.drawText(timeRange, colX[1] + 6f, yPos + 14f, cellTextPaint)
            canvas.drawText(subjectStr, colX[2] + 6f, yPos + 14f, cellTextPaint)
            canvas.drawText(workTypeStr, colX[3] + 6f, yPos + 14f, cellTextPaint)
            canvas.drawText(minutesStr, colX[4] + 6f, yPos + 14f, cellTextPaint)
            canvas.drawText(categoryStr, colX[5] + 6f, yPos + 14f, cellTextPaint)

            yPos += 20f
        }

        // Draw footer
        val genDate = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Focus Study Timer • Generated on $genDate", 36f, 822f, footerPaint)

        pdfDocument.finishPage(page)

        // Write to Cache
        val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateSlug = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val pdfFile = File(cacheDir, "focus-timer-report-${dateSlug}.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        // Share via FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Focus Study Report ($rangeLabel)")
            putExtra(Intent.EXTRA_TEXT, "Focus Study Timer Report ($rangeLabel) - $studyHr hrs study, $nonStudyHr hrs non-study.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Save or Share PDF Report"))
    }

    private fun formatTimeWindowWithDate(endTs: Long, minutes: Int, dateStr: String): String {
        if (endTs <= 0) return "-"
        val startTs = endTs - (minutes.toLong() * 60L * 1000L)
        val cal = Calendar.getInstance().apply { time = Date(endTs) }
        val day = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        val month = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val year = cal.get(Calendar.YEAR)
        val formattedDate = "$day-$month-$year"
        return "${formatTimeOnly(startTs)} - ${formatTimeOnly(endTs)}, $formattedDate"
    }

    // ============================================================
    // COGNITIVE DEEP ANALYSIS & PERFORMANCE REPORT (100% MATCHING WEB APP PDF)
    // ============================================================
    fun generateAndShareAnalysisPdf(
        context: Context,
        report: AnalyticsReport,
        currentStreak: Int = 0,
        bestStreak: Int = 0
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842)

        // Paints matching Web App design
        val darkBannerPaint = Paint().apply {
            color = Color.rgb(5, 10, 16) // Black/Dark Navy banner
            style = Paint.Style.FILL
        }

        val appBrandPaint = Paint().apply {
            color = Color.rgb(0, 229, 153) // Teal/Emerald Brand #00E599
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val reportTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val reportMetaPaint = Paint().apply {
            color = Color.rgb(148, 163, 184) // Slate 400
            textSize = 8f
            isAntiAlias = true
        }

        val emeraldHeaderPaint = Paint().apply {
            color = Color.rgb(16, 185, 129) // Emerald #10B981
            style = Paint.Style.FILL
        }

        val tealHeaderPaint = Paint().apply {
            color = Color.rgb(15, 76, 58) // Dark Teal #0F4C3A
            style = Paint.Style.FILL
        }

        val goldHeaderPaint = Paint().apply {
            color = Color.rgb(201, 150, 47) // Amber/Gold #C9962F
            style = Paint.Style.FILL
        }

        val navyHeaderPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Dark Navy #1E293B
            style = Paint.Style.FILL
        }

        val darkGreenHeaderPaint = Paint().apply {
            color = Color.rgb(6, 95, 70) // Dark Emerald #064E3B
            style = Paint.Style.FILL
        }

        val subHeadingPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val cellItalicTextPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val cellTextPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8f
            isAntiAlias = true
        }

        val cellBoldTextPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val attentionTextPaint = Paint().apply {
            color = Color.rgb(220, 38, 38) // Red #DC2626
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val warningStatusPaint = Paint().apply {
            color = Color.rgb(217, 119, 6) // Amber/Orange #D97706
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val successStatusPaint = Paint().apply {
            color = Color.rgb(5, 150, 105) // Green
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val rowBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val altRowBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252) // Light Slate #F8FAFC
            style = Paint.Style.FILL
        }

        val gridPaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // #E2E8F0
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
            isAntiAlias = true
        }

        val sortedSessions = report.filteredSessions.sortedWith(
            compareByDescending<StudySessionEntity> { it.date }.thenByDescending { it.timestamp }
        )

        val sessionsOnPage2 = 18
        val sessionsPerPageAfter = 25
        val remaining = maxOf(0, sortedSessions.size - sessionsOnPage2)
        val extraPages = if (remaining > 0) ((remaining + sessionsPerPageAfter - 1) / sessionsPerPageAfter) else 0
        val totalPages = 2 + extraPages

        // ---------------- PAGE 1 ----------------
        var page1 = pdfDocument.startPage(pageInfo)
        var canvas1 = page1.canvas

        // 1. Dark Top Banner
        canvas1.drawRect(36f, 36f, 559f, 106f, darkBannerPaint)
        canvas1.drawText("FOCUS STUDY TIMER", 48f, 60f, appBrandPaint)
        canvas1.drawText("Cognitive Deep Analysis & Performance Report", 48f, 78f, reportTitlePaint)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        canvas1.drawText("Reporting Timeframe: ${report.timeframe.label} | Generated: $todayStr", 48f, 95f, reportMetaPaint)

        var y = 114f

        // 2. Executive Performance & Focus Stamina Summary
        canvas1.drawRect(36f, y, 559f, y + 20f, emeraldHeaderPaint)
        canvas1.drawText("EXECUTIVE PERFORMANCE & FOCUS STAMINA SUMMARY", 44f, y + 10f, headerTextPaint)
        canvas1.drawText("TIMEFRAME: ${report.timeframe.label.uppercase()} | DETERMINISTIC COGNITIVE METRICS", 44f, y + 17f, headerTextPaint)
        y += 20f

        val rowH = 18f
        val colW1 = 180f
        val colW2 = 170f
        val colW3 = 173f

        // Row 1: Focus Volume & Sessions
        canvas1.drawRect(36f, y, 559f, y + rowH, rowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + rowH, gridPaint)

        canvas1.drawText("Total Focus: ${String.format(Locale.US, "%.1f", report.totalHours)} hrs (${report.totalMinutes}m)", 44f, y + 12f, cellTextPaint)
        canvas1.drawText("Total Sessions: ${report.sessionCount}", 36f + colW1 + 8f, y + 12f, cellTextPaint)
        canvas1.drawText("Active Days: ${report.activeDaysCount}", 36f + colW1 + colW2 + 8f, y + 12f, cellTextPaint)
        y += rowH

        // Row 2: Focus Quality Score (FQS)
        canvas1.drawRect(36f, y, 559f, y + rowH, altRowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + rowH, gridPaint)

        canvas1.drawText("Focus Quality Score (FQS): ${report.focusQualityScore}/100", 44f, y + 12f, cellBoldTextPaint)
        canvas1.drawText("Cognitive Tier: ${report.focusQualityTier}", 36f + colW1 + 8f, y + 12f, cellBoldTextPaint)
        canvas1.drawText("FQS Formula: 0.35*D + 0.25*C + 0.25*G + 0.15*P", 36f + colW1 + colW2 + 8f, y + 12f, cellTextPaint)
        y += rowH

        // Row 3: Stamina & Velocity
        canvas1.drawRect(36f, y, 559f, y + rowH, rowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + rowH, gridPaint)

        val velSign = if (report.velocityPercentage >= 0) "+" else ""
        canvas1.drawText("Deep Work Ratio: ${report.deepWorkRatio}% (>= 45m blocks)", 44f, y + 12f, cellTextPaint)
        canvas1.drawText("Avg Session Length: ${report.avgSessionMinutes} mins", 36f + colW1 + 8f, y + 12f, cellTextPaint)
        canvas1.drawText("Study Velocity: $velSign${report.velocityPercentage}% vs prior", 36f + colW1 + colW2 + 8f, y + 12f, cellTextPaint)
        y += rowH

        // Row 4: Rhythm & Discipline
        canvas1.drawRect(36f, y, 559f, y + rowH, altRowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + rowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + rowH, gridPaint)

        canvas1.drawText("Peak Window: ${report.peakFocusWindow}", 44f, y + 12f, cellTextPaint)
        canvas1.drawText("Consistency: ${report.consistencyPct}% | Goal Hit: ${report.goalHitRate}%", 36f + colW1 + 8f, y + 12f, cellTextPaint)
        canvas1.drawText("Streak: $currentStreak days (Best: $bestStreak)", 36f + colW1 + colW2 + 8f, y + 12f, cellTextPaint)
        y += rowH + 8f

        // 3. Exam & Syllabus Projection Audit Table
        report.examProjection?.let { proj ->
            canvas1.drawRect(36f, y, 559f, y + 18f, darkGreenHeaderPaint)
            canvas1.drawText("EXAM & SYLLABUS PROJECTION AUDIT TABLE", 44f, y + 13f, headerTextPaint)
            y += 18f

            val pRowH = 16f

            // Projection Row 1: Target Overview
            canvas1.drawRect(36f, y, 559f, y + pRowH, rowBgPaint)
            canvas1.drawRect(36f, y, 36f + colW1, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + pRowH, gridPaint)

            val cleanExam = if (proj.examName.length > 20) proj.examName.take(18) + ".." else proj.examName
            canvas1.drawText("Exam / Goal: $cleanExam", 44f, y + 11f, cellTextPaint)
            canvas1.drawText("Deadline: ${proj.targetDate} (${proj.daysRemaining}d left)", 36f + colW1 + 8f, y + 11f, cellTextPaint)
            canvas1.drawText("Status: ${proj.statusBadgeText}", 36f + colW1 + colW2 + 8f, y + 11f, cellBoldTextPaint)
            y += pRowH

            // Projection Row 2: Hours & Required Pace
            canvas1.drawRect(36f, y, 559f, y + pRowH, altRowBgPaint)
            canvas1.drawRect(36f, y, 36f + colW1, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + pRowH, gridPaint)

            canvas1.drawText("Completed: ${String.format(Locale.US, "%.1f", proj.completedHours)}h / ${String.format(Locale.US, "%.0f", proj.targetHours)}h", 44f, y + 11f, cellTextPaint)
            canvas1.drawText("Remaining: ${String.format(Locale.US, "%.1f", proj.remainingHours)}h", 36f + colW1 + 8f, y + 11f, cellTextPaint)
            canvas1.drawText("Required Pace: ${String.format(Locale.US, "%.1f", proj.requiredDailyHours)} hrs/day", 36f + colW1 + colW2 + 8f, y + 11f, cellTextPaint)
            y += pRowH

            // Projection Row 3: Velocity & Forecast
            canvas1.drawRect(36f, y, 559f, y + pRowH, rowBgPaint)
            canvas1.drawRect(36f, y, 36f + colW1, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + pRowH, gridPaint)
            canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + pRowH, gridPaint)

            val pSign = if (proj.paceDeltaHours >= 0) "+" else ""
            canvas1.drawText("Actual 7d Velocity: ${String.format(Locale.US, "%.1f", proj.currentDailyHours)} hrs/day", 44f, y + 11f, cellTextPaint)
            canvas1.drawText("Pace Delta: $pSign${String.format(Locale.US, "%.1f", proj.paceDeltaHours)} hrs/day", 36f + colW1 + 8f, y + 11f, cellTextPaint)
            canvas1.drawText("Projected Finish: ${proj.projectedDate.ifBlank { "N/A" }}", 36f + colW1 + colW2 + 8f, y + 11f, cellTextPaint)
            y += pRowH

            // Projection Row 4: Narrative
            canvas1.drawRect(36f, y, 559f, y + pRowH, altRowBgPaint)
            canvas1.drawRect(36f, y, 559f, y + pRowH, gridPaint)
            val cleanDesc = if (proj.statusDescription.length > 105) proj.statusDescription.take(102) + "..." else proj.statusDescription
            canvas1.drawText("Projection Audit: $cleanDesc", 44f, y + 11f, cellItalicTextPaint)
            y += pRowH + 8f
        }

        // 4. Cognitive Workload & Fatigue Assessment Table
        val fatigue = report.fatigueReport
        canvas1.drawRect(36f, y, 559f, y + 18f, navyHeaderPaint)
        canvas1.drawText("COGNITIVE WORKLOAD & RECOVERY ASSESSMENT", 44f, y + 13f, headerTextPaint)
        y += 18f

        val fRowH = 16f

        // Fatigue Row 1: Score & Classification
        canvas1.drawRect(36f, y, 559f, y + fRowH, rowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + fRowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + fRowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + fRowH, gridPaint)

        canvas1.drawText("Fatigue Index Score: ${fatigue.fatigueScore} / 100", 44f, y + 11f, cellBoldTextPaint)
        canvas1.drawText("Classification: ${fatigue.tierLabel}", 36f + colW1 + 8f, y + 11f, cellBoldTextPaint)
        canvas1.drawText("7-Day Daily Load: ${String.format(Locale.US, "%.1f", fatigue.avgDailyHours7d)} hrs/day", 36f + colW1 + colW2 + 8f, y + 11f, cellTextPaint)
        y += fRowH

        // Fatigue Row 2: Strain & Recovery
        canvas1.drawRect(36f, y, 559f, y + fRowH, altRowBgPaint)
        canvas1.drawRect(36f, y, 36f + colW1, y + fRowH, gridPaint)
        canvas1.drawRect(36f + colW1, y, 36f + colW1 + colW2, y + fRowH, gridPaint)
        canvas1.drawRect(36f + colW1 + colW2, y, 559f, y + fRowH, gridPaint)

        canvas1.drawText("High-Strain Days (>=5h): ${fatigue.consecutiveHighDays} consecutive", 44f, y + 11f, cellTextPaint)
        canvas1.drawText("Recovery Days (<2h): ${fatigue.recoveryDaysCount} of 7 days", 36f + colW1 + 8f, y + 11f, cellTextPaint)
        canvas1.drawText("Rest Ratio: ${(fatigue.recoveryDaysCount * 100) / 7}%", 36f + colW1 + colW2 + 8f, y + 11f, cellTextPaint)
        y += fRowH

        // Fatigue Row 3: Recommendation
        canvas1.drawRect(36f, y, 559f, y + fRowH, rowBgPaint)
        canvas1.drawRect(36f, y, 559f, y + fRowH, gridPaint)
        val cleanAdvice = if (fatigue.adviceText.length > 105) fatigue.adviceText.take(102) + "..." else fatigue.adviceText
        canvas1.drawText("Behavioral Assessment: $cleanAdvice", 44f, y + 11f, cellItalicTextPaint)
        y += fRowH + 8f

        // 5. Vector 24-Hour Continuous Focus Density Wave (Drawn directly on Page 1)
        canvas1.drawText("24-HOUR CONTINUOUS FOCUS DENSITY WAVE (00:00 - 23:59)", 36f, y + 10f, subHeadingPaint)
        y += 14f

        drawPdfCircadianWave(canvas1, 36f, y, 523f, 38f, report.hourlyMins)
        y += 54f

        // 6. Circadian Time-of-Day Breakdown
        canvas1.drawRect(36f, y, 559f, y + 18f, tealHeaderPaint)
        canvas1.drawText("CIRCADIAN TIME-OF-DAY BREAKDOWN", 44f, y + 13f, headerTextPaint)
        canvas1.drawText("HOURS", 310f, y + 13f, headerTextPaint)
        canvas1.drawText("MINUTES", 390f, y + 13f, headerTextPaint)
        canvas1.drawText("SHARE (%)", 480f, y + 13f, headerTextPaint)
        y += 18f

        val circadianWindows = listOf(
            Triple("Morning (05:00 AM - 12:00 PM)", report.circadianBuckets.getOrNull(0)?.minutes ?: 0, report.circadianBuckets.getOrNull(0)?.percentageOfTotal ?: 0),
            Triple("Afternoon (12:00 PM - 05:00 PM)", report.circadianBuckets.getOrNull(1)?.minutes ?: 0, report.circadianBuckets.getOrNull(1)?.percentageOfTotal ?: 0),
            Triple("Evening (05:00 PM - 10:00 PM)", report.circadianBuckets.getOrNull(2)?.minutes ?: 0, report.circadianBuckets.getOrNull(2)?.percentageOfTotal ?: 0),
            Triple("Night (10:00 PM - 05:00 AM)", report.circadianBuckets.getOrNull(3)?.minutes ?: 0, report.circadianBuckets.getOrNull(3)?.percentageOfTotal ?: 0)
        )

        circadianWindows.forEachIndexed { i, item ->
            val bg = if (i % 2 == 0) rowBgPaint else altRowBgPaint
            canvas1.drawRect(36f, y, 559f, y + 16f, bg)
            canvas1.drawRect(36f, y, 559f, y + 16f, gridPaint)

            canvas1.drawText(item.first, 44f, y + 12f, cellTextPaint)
            canvas1.drawText("${String.format(Locale.US, "%.1f", item.second / 60.0)} hrs", 310f, y + 12f, cellTextPaint)
            canvas1.drawText("${item.second} mins", 390f, y + 12f, cellTextPaint)
            canvas1.drawText("${item.third}%", 480f, y + 12f, cellTextPaint)
            y += 16f
        }

        // Footer Page 1
        canvas1.drawText("Page 1 of $totalPages | Focus Study Timer Cognitive Analytics Report", 210f, 822f, footerPaint)
        pdfDocument.finishPage(page1)

        // ---------------- PAGE 2 ----------------
        var page2 = pdfDocument.startPage(pageInfo)
        var canvas2 = page2.canvas
        y = 38f

        // 7. Subject Equilibrium & Ebbinghaus Scientific Recall Matrix
        canvas2.drawRect(36f, y, 559f, y + 20f, goldHeaderPaint)
        canvas2.drawText("SUBJECT EQUILIBRIUM & EBBINGHAUS SCIENTIFIC RECALL", 44f, y + 9f, headerTextPaint)
        canvas2.drawText("MATRIX", 44f, y + 17f, headerTextPaint)
        canvas2.drawText("TOTAL TIME", 205f, y + 13f, headerTextPaint)
        canvas2.drawText("SHARE", 270f, y + 13f, headerTextPaint)
        canvas2.drawText("LAST STUDIED", 315f, y + 13f, headerTextPaint)
        canvas2.drawText("RETENTION (R)", 380f, y + 13f, headerTextPaint)
        canvas2.drawText("SCIENTIFIC RECALL STATUS", 455f, y + 13f, headerTextPaint)
        y += 20f

        val eqList = report.subjectEquilibrium.take(6)
        if (eqList.isEmpty()) {
            canvas2.drawRect(36f, y, 559f, y + 17f, rowBgPaint)
            canvas2.drawRect(36f, y, 559f, y + 17f, gridPaint)
            canvas2.drawText("No subject activity in this timeframe.", 44f, y + 12f, cellTextPaint)
            y += 17f
        } else {
            eqList.forEachIndexed { i, s ->
                val bg = if (i % 2 == 0) rowBgPaint else altRowBgPaint
                canvas2.drawRect(36f, y, 559f, y + 17f, bg)
                canvas2.drawRect(36f, y, 559f, y + 17f, gridPaint)

                val cleanSubj = if (s.subjectName.length > 22) s.subjectName.take(20) + ".." else s.subjectName
                val lastStr = if (s.daysAgo == 0) "Today" else if (s.daysAgo == 1) "Yesterday" else "${s.daysAgo}d ago"
                val (recallStr, recallPaint) = when {
                    s.retentionPct < 60 -> "CRITICAL DUE (${s.retentionPct}%)" to attentionTextPaint
                    s.retentionPct < 80 -> "REVIEW REC (${s.retentionPct}%)" to warningStatusPaint
                    else -> "OPTIMAL (${s.retentionPct}%)" to successStatusPaint
                }

                canvas2.drawText(cleanSubj, 44f, y + 12f, cellTextPaint)
                canvas2.drawText("${String.format(Locale.US, "%.1f", s.hours)}h (${s.minutes}m)", 205f, y + 12f, cellTextPaint)
                canvas2.drawText("${s.percentage}%", 270f, y + 12f, cellTextPaint)
                canvas2.drawText(lastStr, 315f, y + 12f, cellTextPaint)
                canvas2.drawText("${s.retentionPct}% (S=${s.stabilityDays}d)", 380f, y + 12f, cellTextPaint)
                canvas2.drawText(recallStr, 455f, y + 12f, recallPaint)
                y += 17f
            }
        }
        y += 8f

        // 8. Vector Subject Equilibrium Radar Chart (Drawn directly on Page 2)
        canvas2.drawText("CURRICULUM EQUILIBRIUM RADAR POLYGON", 36f, y + 10f, subHeadingPaint)
        y += 14f

        drawPdfRadarChart(canvas2, 297.5f, y + 46f, 42f, report.subjectEquilibrium)
        y += 104f

        // 9. Cognitive Work Modality
        canvas2.drawRect(36f, y, 559f, y + 18f, navyHeaderPaint)
        canvas2.drawText("COGNITIVE WORK MODALITY", 44f, y + 13f, headerTextPaint)
        canvas2.drawText("DURATION", 260f, y + 13f, headerTextPaint)
        canvas2.drawText("SHARE (%)", 360f, y + 13f, headerTextPaint)
        canvas2.drawText("COGNITIVE TYPE", 450f, y + 13f, headerTextPaint)
        y += 18f

        val modalities = if (report.cognitiveWorkTypes.isNotEmpty()) {
            report.cognitiveWorkTypes
        } else {
            listOf(
                CognitiveWorkTypeItem("Practice", 0, 0),
                CognitiveWorkTypeItem("New Topic", 0, 0),
                CognitiveWorkTypeItem("Revision", 0, 0)
            )
        }

        modalities.take(4).forEachIndexed { i, mod ->
            val bg = if (i % 2 == 0) rowBgPaint else altRowBgPaint
            canvas2.drawRect(36f, y, 559f, y + 16f, bg)
            canvas2.drawRect(36f, y, 559f, y + 16f, gridPaint)

            val cogType = when (mod.workTypeName.lowercase()) {
                "practice", "revision", "mock test - mcq", "test" -> "Active Recall / Test"
                else -> "Content Acquisition / Notes"
            }

            canvas2.drawText(mod.workTypeName, 44f, y + 12f, cellTextPaint)
            canvas2.drawText("${mod.minutes} mins", 260f, y + 12f, cellTextPaint)
            canvas2.drawText("${mod.percentage}%", 360f, y + 12f, cellTextPaint)
            canvas2.drawText(cogType, 450f, y + 12f, cellTextPaint)
            y += 16f
        }
        y += 8f

        // 10. Deterministic Cognitive Insights & Actionable Recommendations
        canvas2.drawRect(36f, y, 559f, y + 18f, emeraldHeaderPaint)
        canvas2.drawText("DETERMINISTIC COGNITIVE INSIGHTS & ACTIONABLE RECOMMENDATIONS", 44f, y + 13f, headerTextPaint)
        y += 18f

        val obsItems = mutableListOf<String>()
        report.smartInsights.forEach { insight ->
            obsItems.add("[${insight.title}] ${insight.description}")
        }
        if (obsItems.isEmpty()) {
            obsItems.add("[Consistency] Maintain structured daily study routines to build sustained focus momentum.")
        }

        obsItems.take(3).forEachIndexed { i, obs ->
            val bg = if (i % 2 == 0) rowBgPaint else altRowBgPaint
            canvas2.drawRect(36f, y, 559f, y + 20f, bg)
            canvas2.drawRect(36f, y, 559f, y + 20f, gridPaint)

            val clipped = if (obs.length > 115) obs.take(112) + "..." else obs
            canvas2.drawText(clipped, 44f, y + 13f, cellTextPaint)
            y += 20f
        }
        y += 10f

        // 11. Session Audit Log Header
        fun drawAuditTableHeader(c: Canvas, yPos: Float) {
            c.drawRect(36f, yPos, 559f, yPos + 20f, navyHeaderPaint)
            c.drawText("SESSION AUDIT LOG (${report.timeframe.label.uppercase()})", 44f, yPos + 14f, headerTextPaint)
            c.drawText("TIME WINDOW", 180f, yPos + 14f, headerTextPaint)
            c.drawText("SUBJECT", 340f, yPos + 14f, headerTextPaint)
            c.drawText("WORK TYPE", 445f, yPos + 14f, headerTextPaint)
            c.drawText("MINUTES", 515f, yPos + 14f, headerTextPaint)
        }

        drawAuditTableHeader(canvas2, y)
        y += 20f

        var curPageNumber = 2
        var currentCanvas = canvas2
        var currentPage = page2

        sortedSessions.forEachIndexed { index, s ->
            if (y > 780f) {
                currentCanvas.drawText("Page $curPageNumber of $totalPages | Focus Study Timer Cognitive Analytics Report", 210f, 822f, footerPaint)
                pdfDocument.finishPage(currentPage)

                curPageNumber++
                currentPage = pdfDocument.startPage(pageInfo)
                currentCanvas = currentPage.canvas

                y = 44f
                drawAuditTableHeader(currentCanvas, y)
                y += 20f
            }

            val bg = if (index % 2 == 0) rowBgPaint else altRowBgPaint
            currentCanvas.drawRect(36f, y, 559f, y + 16f, bg)
            currentCanvas.drawRect(36f, y, 559f, y + 16f, gridPaint)

            currentCanvas.drawText(s.date, 44f, y + 12f, cellTextPaint)

            val tw = formatTimeWindowWithDate(s.timestamp, s.minutes, s.date)
            val clippedTw = if (tw.length > 27) tw.take(25) + ".." else tw
            currentCanvas.drawText(clippedTw, 180f, y + 12f, cellTextPaint)

            val clippedSubj = if (s.subject.length > 18) s.subject.take(16) + ".." else s.subject
            currentCanvas.drawText(clippedSubj, 340f, y + 12f, cellTextPaint)

            val clippedWork = if (s.workType.length > 13) s.workType.take(11) + ".." else s.workType
            currentCanvas.drawText(clippedWork, 445f, y + 12f, cellTextPaint)

            currentCanvas.drawText("${s.minutes}m", 515f, y + 12f, cellTextPaint)
            y += 16f
        }

        currentCanvas.drawText("Page $curPageNumber of $totalPages | Focus Study Timer Cognitive Analytics Report", 210f, 822f, footerPaint)
        pdfDocument.finishPage(currentPage)

        // Write to Cache
        val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateSlug = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val pdfFile = File(cacheDir, "focus-timer-cognitive-analysis-${report.timeframe.label.replace(" ", "_")}-$dateSlug.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Focus Study Timer Cognitive Deep Analysis Report (${report.timeframe.label})")
            putExtra(Intent.EXTRA_TEXT, "Cognitive Deep Analysis & Performance Report (${report.timeframe.label}) - ${String.format(Locale.US, "%.1f", report.totalHours)} hrs focus time.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Analysis PDF Report"))
    }

    private fun drawPdfCircadianWave(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        width: Float,
        height: Float,
        hourlyMins: IntArray
    ) {
        val maxMins = (hourlyMins.maxOrNull() ?: 10).coerceAtLeast(10).toFloat()
        val baseY = startY + height
        val padX = 14f
        val plotW = width - (padX * 2)

        val gridPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val gridLabelPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 6.5f
            isAntiAlias = true
        }

        // Grid lines (0%, 50%, 100%)
        listOf(0f, 0.5f, 1.0f).forEach { ratio ->
            val gy = baseY - (ratio * height)
            canvas.drawLine(startX + padX, gy, startX + padX + plotW, gy, gridPaint)
            canvas.drawText("${(maxMins * ratio).toInt()}m", startX, gy + 2.5f, gridLabelPaint)
        }

        val points = Array(24) { h ->
            val x = startX + padX + (h.toFloat() / 23f) * plotW
            val py = baseY - (hourlyMins[h].toFloat() / maxMins) * height
            PointF(x, py)
        }

        // Area fill
        val areaPaint = Paint().apply {
            color = Color.rgb(209, 250, 229)
            style = Paint.Style.FILL
        }
        val areaPath = android.graphics.Path().apply {
            moveTo(points[0].x, baseY)
            lineTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]

                val cp1x = p1.x + (p2.x - p0.x) / 6f
                var cp1y = p1.y + (p2.y - p0.y) / 6f
                val cp2x = p2.x - (p3.x - p1.x) / 6f
                var cp2y = p2.y - (p3.y - p1.y) / 6f

                if (cp1y > baseY) cp1y = baseY
                if (cp2y > baseY) cp2y = baseY

                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            }
            lineTo(points[23].x, baseY)
            close()
        }
        canvas.drawPath(areaPath, areaPaint)

        // Stroke line
        val strokePaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val strokePath = android.graphics.Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]

                val cp1x = p1.x + (p2.x - p0.x) / 6f
                var cp1y = p1.y + (p2.y - p0.y) / 6f
                val cp2x = p2.x - (p3.x - p1.x) / 6f
                var cp2y = p2.y - (p3.y - p1.y) / 6f

                if (cp1y > baseY) cp1y = baseY
                if (cp2y > baseY) cp2y = baseY

                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            }
        }
        canvas.drawPath(strokePath, strokePaint)

        // Peak marker
        var peakIdx = 0
        var peakVal = 0
        hourlyMins.forEachIndexed { idx, v ->
            if (v > peakVal) {
                peakVal = v
                peakIdx = idx
            }
        }
        if (peakVal > 0) {
            val peakPt = points[peakIdx]
            val peakCirclePaint = Paint().apply {
                color = Color.rgb(16, 185, 129)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(peakPt.x, peakPt.y, 2.5f, peakCirclePaint)

            val peakTextPaint = Paint().apply {
                color = Color.rgb(6, 95, 70)
                textSize = 6.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val ampm = if (peakIdx >= 12) "PM" else "AM"
            val hr = if (peakIdx % 12 == 0) 12 else peakIdx % 12
            canvas.drawText("Peak: $hr:00 $ampm (${peakVal}m)", peakPt.x, maxOf(startY + 8f, peakPt.y - 4f), peakTextPaint)
        }

        // Hour labels
        val hourTicks = listOf(0, 4, 8, 12, 16, 20, 23)
        val tickPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 6.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        hourTicks.forEach { h ->
            val p = points[h]
            val ampm = if (h >= 12) "p" else "a"
            val hr = if (h % 12 == 0) 12 else h % 12
            canvas.drawText("$hr$ampm", p.x, baseY + 8f, tickPaint)
        }
    }

    private fun drawPdfRadarChart(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        subjectEquilibrium: List<SubjectEquilibriumItem>
    ) {
        val topSubjects = subjectEquilibrium.take(8)
        val n = topSubjects.size

        if (n < 3) {
            val fallbackPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 7.5f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Polygonal radar activates with 3+ subjects (see audit table for balance).", cx, cy, fallbackPaint)
            return
        }

        // Rings
        val ringPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { pct ->
            val r = radius * pct
            val ringPath = android.graphics.Path()
            for (i in 0 until n) {
                val a = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
                val x = (cx + r * kotlin.math.cos(a)).toFloat()
                val y = (cy + r * kotlin.math.sin(a)).toFloat()
                if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
            }
            ringPath.close()
            canvas.drawPath(ringPath, ringPaint)
        }

        // Spokes
        for (i in 0 until n) {
            val a = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
            val x = (cx + radius * kotlin.math.cos(a)).toFloat()
            val y = (cy + radius * kotlin.math.sin(a)).toFloat()
            canvas.drawLine(cx, cy, x, y, ringPaint)
        }

        val maxMins = maxOf(topSubjects.maxOfOrNull { it.minutes } ?: 1, 1).toFloat()
        val points = topSubjects.mapIndexed { i, s ->
            val a = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
            val norm = (s.minutes.toFloat() / maxMins).coerceIn(0.08f, 1.0f)
            val r = radius * norm
            val x = (cx + r * kotlin.math.cos(a)).toFloat()
            val y = (cy + r * kotlin.math.sin(a)).toFloat()
            Triple(PointF(x, y), a, s)
        }

        // Fill data polygon
        val fillPaint = Paint().apply {
            color = Color.argb(60, 16, 185, 129)
            style = Paint.Style.FILL
        }
        val dataPath = android.graphics.Path()
        points.forEachIndexed { i, triple ->
            if (i == 0) dataPath.moveTo(triple.first.x, triple.first.y) else dataPath.lineTo(triple.first.x, triple.first.y)
        }
        dataPath.close()
        canvas.drawPath(dataPath, fillPaint)

        // Stroke data polygon
        val strokePaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawPath(dataPath, strokePaint)

        // Vertices & Labels
        val dotPaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 6.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        points.forEach { triple ->
            val pt = triple.first
            val a = triple.second
            val subj = triple.third

            canvas.drawCircle(pt.x, pt.y, 1.8f, dotPaint)

            val lx = (cx + (radius + 10f) * kotlin.math.cos(a)).toFloat()
            val ly = (cy + (radius + 10f) * kotlin.math.sin(a)).toFloat()
            val clean = if (subj.subjectName.length > 9) subj.subjectName.take(8) + ".." else subj.subjectName

            labelPaint.textAlign = if (kotlin.math.cos(a) > 0.2) Paint.Align.LEFT else if (kotlin.math.cos(a) < -0.2) Paint.Align.RIGHT else Paint.Align.CENTER
            canvas.drawText("$clean (${String.format(Locale.US, "%.1f", subj.hours)}h)", lx, ly + 2.5f, labelPaint)
        }
    }
}

package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
}

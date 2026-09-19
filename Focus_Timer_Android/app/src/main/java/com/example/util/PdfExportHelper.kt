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
import java.util.Date
import java.util.Locale

object PdfExportHelper {

    fun generateAndSharePdf(
        context: Context,
        sessions: List<StudySessionEntity>,
        rangeLabel: String
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(120, 100, 30)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 10f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }

        val accentBarPaint = Paint().apply {
            color = Color.rgb(201, 150, 47) // Gold accent
        }

        var yPos = 40f

        // Top Gold Accent Bar
        canvas.drawRect(36f, yPos, 559f, yPos + 4f, accentBarPaint)
        yPos += 26f

        // Document Title
        canvas.drawText("FOCUS TIMER - STUDY REPORT", 36f, yPos, titlePaint)
        yPos += 18f

        // Subtitle info
        val nowFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val totalMinutes = sessions.sumOf { it.minutes }
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        canvas.drawText("Filter Range: $rangeLabel  |  Generated: $nowFormatted", 36f, yPos, subtitlePaint)
        yPos += 16f
        canvas.drawText("Total Sessions: ${sessions.size}  |  Total Study Time: $timeString ($totalMinutes mins)", 36f, yPos, subtitlePaint)
        yPos += 22f

        // Table Header Line
        canvas.drawLine(36f, yPos, 559f, yPos, linePaint)
        yPos += 14f

        canvas.drawText("Date", 40f, yPos, headerPaint)
        canvas.drawText("Subject / Task", 130f, yPos, headerPaint)
        canvas.drawText("Work Type", 350f, yPos, headerPaint)
        canvas.drawText("Duration", 480f, yPos, headerPaint)
        yPos += 6f
        canvas.drawLine(36f, yPos, 559f, yPos, linePaint)
        yPos += 16f

        val sortedSessions = sessions.sortedByDescending { it.timestamp }
        val maxRows = 36 // Fit comfortably on A4 page
        val displayList = sortedSessions.take(maxRows)

        for (session in displayList) {
            val dateStr = session.date
            val subjectName = if (session.subject.length > 32) session.subject.take(30) + ".." else session.subject
            val workTypeStr = session.workType
            val durationStr = "${session.minutes} mins"

            canvas.drawText(dateStr, 40f, yPos, textPaint)
            canvas.drawText(subjectName, 130f, yPos, textPaint)
            canvas.drawText(workTypeStr, 350f, yPos, textPaint)
            canvas.drawText(durationStr, 480f, yPos, textPaint)

            yPos += 16f
            canvas.drawLine(36f, yPos - 6f, 559f, yPos - 6f, linePaint)

            if (yPos > 790f) break
        }

        if (sortedSessions.size > maxRows) {
            val moreCount = sortedSessions.size - maxRows
            canvas.drawText("... and $moreCount more sessions summarized in total statistics.", 40f, yPos + 10f, subtitlePaint)
        }

        // Footer
        canvas.drawText("Focus Timer • Built for disciplined study tracking", 36f, 815f, subtitlePaint)

        pdfDocument.finishPage(page)

        // Write to Cache
        val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateSlug = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val pdfFile = File(cacheDir, "StudyReport_${dateSlug}.pdf")

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
            putExtra(Intent.EXTRA_TEXT, "Here is my study report from Focus Timer: $timeString total focus time.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Save or Share PDF Report"))
    }
}

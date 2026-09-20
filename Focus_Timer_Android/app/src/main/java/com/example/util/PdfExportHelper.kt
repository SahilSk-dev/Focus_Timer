package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.StudySessionEntity

/**
 * WebPortalHelper handles high-performance redirection to the official Web Dashboard (Vercel)
 * where jsPDF-AutoTable and full-screen cognitive analytics provide flawless rendering.
 */
object WebPortalHelper {
    const val WEB_URL = "https://focustimer-sk.vercel.app"
    const val ANALYTICS_URL = "https://focustimer-sk.vercel.app/#analytics"
    const val HISTORY_PDF_URL = "https://focustimer-sk.vercel.app/#history"

    fun openWebAnalytics(context: Context) {
        openUrl(context, ANALYTICS_URL)
    }

    fun openWebHistoryReport(context: Context) {
        openUrl(context, HISTORY_PDF_URL)
    }

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Backward compatibility wrapper for PdfExportHelper.
 * Redirects PDF generation requests directly to the Web report generator.
 */
object PdfExportHelper {
    fun generateAndSharePdf(context: Context, sessions: List<StudySessionEntity>, periodName: String = "All Time") {
        Toast.makeText(context, "Opening Web PDF Report Generator...", Toast.LENGTH_SHORT).show()
        WebPortalHelper.openWebHistoryReport(context)
    }

    fun generateAndShareAnalysisPdf(context: Context, report: AnalyticsReport) {
        Toast.makeText(context, "Opening Web Analytics Report...", Toast.LENGTH_SHORT).show()
        WebPortalHelper.openWebAnalytics(context)
    }
}

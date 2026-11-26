package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatterCache {
    private val isoDateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val readableDateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy", Locale.US)
    }

    private val timeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.US)
    }

    fun getTodayString(): String {
        return isoDateFormatter.get()?.format(Date()) ?: "2026-01-01"
    }

    fun formatIsoDate(timestamp: Long): String {
        return isoDateFormatter.get()?.format(Date(timestamp)) ?: getTodayString()
    }

    fun formatReadableDate(dateStr: String): String {
        return try {
            val date = isoDateFormatter.get()?.parse(dateStr)
            if (date != null) readableDateFormatter.get()?.format(date) ?: dateStr else dateStr
        } catch (_: Exception) {
            dateStr
        }
    }

    fun formatTime(timestamp: Long): String {
        return timeFormatter.get()?.format(Date(timestamp)) ?: ""
    }

    fun parseIsoDate(dateStr: String): Date? {
        return try {
            isoDateFormatter.get()?.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}

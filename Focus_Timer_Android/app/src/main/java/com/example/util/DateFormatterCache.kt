package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatterCache {
    private val isoDateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private val readableDateFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    private val timeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    @Volatile
    private var cachedTodayDate: String = ""
    @Volatile
    private var cachedTodayEpochDay: Long = -1

    fun getTodayString(): String {
        val currentDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        if (currentDay != cachedTodayEpochDay || cachedTodayDate.isEmpty()) {
            cachedTodayDate = isoDateFormatter.get()?.format(Date()) ?: "2026-01-01"
            cachedTodayEpochDay = currentDay
        }
        return cachedTodayDate
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

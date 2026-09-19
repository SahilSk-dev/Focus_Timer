package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val subject: String,
    val subSubject: String? = null,
    val workType: String = "Other",
    val minutes: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isNonStudy: Boolean = false
)

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCore: Boolean = false,
    val isNonStudy: Boolean = false,
    val subSubjects: List<String> = emptyList()
)

@Entity(tableName = "work_types")
data class WorkTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)

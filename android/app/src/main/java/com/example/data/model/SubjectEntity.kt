package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCore: Boolean = false,
    val isNonStudy: Boolean = false,
    val subSubjects: List<String> = emptyList()
)

@Entity(
    tableName = "work_types",
    indices = [Index(value = ["name"], unique = true)]
)
data class WorkTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)


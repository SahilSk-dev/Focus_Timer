package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity

@Database(
    entities = [StudySessionEntity::class, SubjectEntity::class, WorkTypeEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_timer.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun prepopulateData(dao: StudyDao) {
            // Clean up any historical duplicates first
            try {
                dao.deduplicateSubjects()
                dao.deduplicateWorkTypes()
            } catch (_: Exception) {}

            if (dao.getSubjectCount() == 0) {
                dao.insertSubjects(
                    listOf(
                        SubjectEntity(name = "Bengali", isCore = true, subSubjects = listOf("Text", "Grammar")),
                        SubjectEntity(name = "English", isCore = true, subSubjects = listOf("Text", "Grammar")),
                        SubjectEntity(name = "Math", isCore = true),
                        SubjectEntity(name = "Life Science", isCore = true),
                        SubjectEntity(name = "Physical Science", isCore = true),
                        SubjectEntity(name = "History", isCore = true),
                        SubjectEntity(name = "Geography", isCore = true)
                    )
                )
            }
            if (dao.getWorkTypeCount() == 0) {
                dao.insertWorkTypes(
                    listOf(
                        "Revision", "New Topic", "Memorize", "Reading",
                        "Practice", "Notes", "Mock Test", "Other"
                    ).map { WorkTypeEntity(name = it) }
                )
            }

            // Ensure duplicates are purged
            try {
                dao.deduplicateSubjects()
                dao.deduplicateWorkTypes()
            } catch (_: Exception) {}
        }
    }
}


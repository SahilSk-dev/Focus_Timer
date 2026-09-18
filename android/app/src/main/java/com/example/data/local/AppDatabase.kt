package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [StudySessionEntity::class, SubjectEntity::class, WorkTypeEntity::class],
    version = 1,
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
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).studyDao()
                            prepopulateData(dao)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun prepopulateData(dao: StudyDao) {
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
        }
    }
}

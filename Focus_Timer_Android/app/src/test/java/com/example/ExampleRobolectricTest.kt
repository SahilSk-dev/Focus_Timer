package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.StudySessionEntity
import com.example.data.repository.FocusRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Focus Timer", appName)
    }

    @Test
    fun `test database and repository prepopulation and session saving`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = db.studyDao()
        AppDatabase.prepopulateData(dao)

        val subjects = dao.getAllSubjects().first()
        assertTrue(subjects.isNotEmpty())
        assertTrue(subjects.any { it.name == "Bengali" && it.isCore })

        val repo = FocusRepository(dao, context)
        val newSession = StudySessionEntity(
            date = "2026-09-18",
            subject = "Bengali - Text",
            workType = "Revision",
            minutes = 45
        )
        val insertedId = repo.addSession(newSession)
        assertTrue(insertedId > 0)

        val allSessions = repo.getAllSessions().first()
        assertTrue(allSessions.any { it.subject == "Bengali - Text" && it.minutes == 45 })

        // Test JSON export matching Web schema
        val exportedJson = repo.exportToJson(allSessions)
        assertTrue(exportedJson.contains("sessions"))
        assertTrue(exportedJson.contains("nonStudySessions"))
        assertTrue(exportedJson.contains("Bengali - Text"))
    }
}

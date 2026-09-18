package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.StudyDao
import com.example.data.model.StudySessionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(
    private val studyDao: StudyDao,
    private val context: Context
) {
    companion object {
        private const val TAG = "FirebaseSyncManager"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _syncStatus = MutableStateFlow("Ready")
    val syncStatus = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private var sessionListener: ListenerRegistration? = null
    private var nonStudySessionListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        try {
            _currentUser.value = auth.currentUser
            auth.addAuthStateListener { fbAuth ->
                val user = fbAuth.currentUser
                _currentUser.value = user
                if (user != null) {
                    startRealtimeSync(user.uid)
                } else {
                    stopRealtimeSync()
                }
            }
            if (auth.currentUser != null) {
                startRealtimeSync(auth.currentUser!!.uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseAuth: ${e.message}")
        }
    }

    fun startRealtimeSync(uid: String) {
        stopRealtimeSync()
        _syncStatus.value = "Connecting to Cloud..."

        // 1. Initial 2-way sync: Push local sessions that aren't on cloud yet
        scope.launch {
            uploadLocalSessionsToCloud(uid)
        }

        // 2. Listen to /users/{uid}/sessions
        sessionListener = firestore.collection("users")
            .document(uid)
            .collection("sessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed on sessions: ${error.message}")
                    _syncStatus.value = "Sync error"
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    scope.launch {
                        processIncomingSessions(snapshot.documents, isNonStudy = false)
                        _syncStatus.value = "Synced with Laptop & Cloud"
                    }
                }
            }

        // 3. Listen to /users/{uid}/nonStudySessions
        nonStudySessionListener = firestore.collection("users")
            .document(uid)
            .collection("nonStudySessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed on nonStudySessions: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    scope.launch {
                        processIncomingSessions(snapshot.documents, isNonStudy = true)
                    }
                }
            }
    }

    fun stopRealtimeSync() {
        sessionListener?.remove()
        sessionListener = null
        nonStudySessionListener?.remove()
        nonStudySessionListener = null
        _syncStatus.value = "Guest Mode"
    }

    suspend fun uploadSession(session: StudySessionEntity) {
        val user = auth.currentUser ?: return
        try {
            val collectionName = if (session.isNonStudy) "nonStudySessions" else "sessions"
            val data = hashMapOf(
                "date" to session.date,
                "subject" to session.subject,
                "subSubject" to (session.subSubject ?: ""),
                "workType" to session.workType,
                "minutes" to session.minutes,
                "ts" to session.timestamp,
                "isNonStudy" to session.isNonStudy
            )
            // Use timestamp as document key or auto ID
            val docRef = firestore.collection("users")
                .document(user.uid)
                .collection(collectionName)
                .document(session.timestamp.toString())

            docRef.set(data, SetOptions.merge()).await()
            _syncStatus.value = "Saved to Cloud"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload session: ${e.message}")
            _syncStatus.value = "Offline (Local Only)"
        }
    }

    private suspend fun uploadLocalSessionsToCloud(uid: String) {
        try {
            _isSyncing.value = true
            val localSessions = studyDao.getAllSessions().first()
            val sessionsCol = firestore.collection("users").document(uid).collection("sessions")
            val nonStudyCol = firestore.collection("users").document(uid).collection("nonStudySessions")

            for (s in localSessions) {
                val targetCol = if (s.isNonStudy) nonStudyCol else sessionsCol
                val data = hashMapOf(
                    "date" to s.date,
                    "subject" to s.subject,
                    "subSubject" to (s.subSubject ?: ""),
                    "workType" to s.workType,
                    "minutes" to s.minutes,
                    "ts" to s.timestamp,
                    "isNonStudy" to s.isNonStudy
                )
                targetCol.document(s.timestamp.toString()).set(data, SetOptions.merge())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bulk upload: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun processIncomingSessions(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        isNonStudy: Boolean
    ) {
        try {
            val localSessions = studyDao.getAllSessions().first()
            val localTsSet = localSessions.map { it.timestamp }.toSet()

            val toInsert = mutableListOf<StudySessionEntity>()
            for (doc in docs) {
                val ts = doc.getLong("ts") ?: continue
                if (!localTsSet.contains(ts)) {
                    val date = doc.getString("date") ?: ""
                    val subject = doc.getString("subject") ?: "General"
                    val subSubject = doc.getString("subSubject").takeIf { !it.isNullOrBlank() }
                    val workType = doc.getString("workType") ?: "Other"
                    val minutes = doc.getLong("minutes")?.toInt() ?: 0

                    toInsert.add(
                        StudySessionEntity(
                            date = date,
                            subject = subject,
                            subSubject = subSubject,
                            workType = workType,
                            minutes = minutes,
                            timestamp = ts,
                            isNonStudy = isNonStudy
                        )
                    )
                }
            }

            if (toInsert.isNotEmpty()) {
                studyDao.insertSessions(toInsert)
                Log.d(TAG, "Inserted ${toInsert.size} new sessions from Cloud")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming sessions: ${e.message}")
        }
    }

    suspend fun manualSyncNow() {
        val user = auth.currentUser ?: return
        _isSyncing.value = true
        _syncStatus.value = "Syncing..."
        uploadLocalSessionsToCloud(user.uid)
        _syncStatus.value = "Synced with Laptop & Cloud"
        _isSyncing.value = false
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
        stopRealtimeSync()
    }
}

package com.example.data.sync

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.local.SessionDeduplicator
import com.example.data.local.SessionSyncLock
import com.example.data.local.StudyDao
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import kotlinx.coroutines.sync.withLock
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.DocumentChange
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
import kotlinx.coroutines.withTimeoutOrNull

class FirebaseSyncManager(
    private val studyDao: StudyDao,
    private val context: Context
) {
    companion object {
        private const val TAG = "FirebaseSyncManager"
        const val WEB_CLIENT_ID = "486309866833-g02tgb2ip175690ns4p885csmh89q8ip.apps.googleusercontent.com"
    }

    private val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }

    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            null
        }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_timer_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _syncStatus = MutableStateFlow("Ready")
    val syncStatus = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private var sessionListener: ListenerRegistration? = null
    private var nonStudySessionListener: ListenerRegistration? = null
    private var prefsListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        try {
            val fbAuth = auth
            if (fbAuth != null) {
                _currentUser.value = fbAuth.currentUser
                fbAuth.addAuthStateListener { listenerAuth ->
                    val user = listenerAuth.currentUser
                    _currentUser.value = user
                    if (user != null) {
                        startRealtimeSync(user.uid)
                    } else {
                        stopRealtimeSync()
                    }
                }
                if (fbAuth.currentUser != null) {
                    startRealtimeSync(fbAuth.currentUser!!.uid)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseAuth: ${e.message}")
        }
    }

    fun startRealtimeSync(uid: String) {
        stopRealtimeSync()
        val fs = firestore ?: return
        _isSyncing.value = true
        _syncStatus.value = "Connecting to Cloud..."

        // 1. Initial 2-way sync: push local sessions & prefs with a safe 15s timeout
        scope.launch {
            try {
                withTimeoutOrNull(15000L) {
                    uploadLocalSessionsToCloud(uid)
                    syncLocalPrefsToCloud(uid)
                }
                Log.d(TAG, "Initial sync completed for $uid")
                _syncStatus.value = "Synced with Web & Cloud"
            } catch (e: Exception) {
                Log.e(TAG, "Error during initial sync: ${e.message}")
                _syncStatus.value = "Sync completed with warning"
            } finally {
                _isSyncing.value = false
            }
        }

        // 2. Listen to /users/{uid}/sessions
        sessionListener = fs.collection("users")
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
                        processSessionChanges(snapshot.documentChanges, isNonStudy = false)
                        _syncStatus.value = "Synced with Web & Cloud"
                    }
                }
            }

        // 3. Listen to /users/{uid}/nonStudySessions
        nonStudySessionListener = fs.collection("users")
            .document(uid)
            .collection("nonStudySessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed on nonStudySessions: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    scope.launch {
                        processSessionChanges(snapshot.documentChanges, isNonStudy = true)
                    }
                }
            }

        // 4. Listen to /users/{uid}/meta/prefs (Subjects, WorkTypes, DailyTarget matching Web)
        prefsListener = fs.collection("users")
            .document(uid)
            .collection("meta")
            .document("prefs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed on prefs: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    scope.launch {
                        applyCloudPrefs(snapshot)
                    }
                }
            }
    }

    fun stopRealtimeSync() {
        sessionListener?.remove()
        sessionListener = null
        nonStudySessionListener?.remove()
        nonStudySessionListener = null
        prefsListener?.remove()
        prefsListener = null
        _isSyncing.value = false
        _syncStatus.value = "Guest Mode"
    }

    // Google Sign-In: tries native Credential Manager first, then falls back to Firebase web OAuth
    // so debug APKs without a registered SHA-1 in Firebase Console can still sign in seamlessly.
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> {
        val fbAuth = auth ?: return Result.failure(Exception("Firebase not initialized"))
        _syncStatus.value = "Signing in..."
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val credentialAttempt = tryCredentialSignIn(activity, credentialManager, googleIdOption, fbAuth)
        if (credentialAttempt.isSuccess) return credentialAttempt
        val firstErr = credentialAttempt.exceptionOrNull()
        if (isUserExplicitCancel(firstErr)) {
            _syncStatus.value = "Sign-in cancelled"
            return Result.failure(firstErr!!)
        }

        // Second native attempt: explicit Sign in with Google bottom sheet option
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
        val sheetAttempt = tryCredentialSignIn(activity, credentialManager, signInWithGoogleOption, fbAuth)
        if (sheetAttempt.isSuccess) return sheetAttempt
        val secondErr = sheetAttempt.exceptionOrNull()
        if (isUserExplicitCancel(secondErr)) {
            _syncStatus.value = "Sign-in cancelled"
            return Result.failure(secondErr!!)
        }

        // Fallback when Credential Manager has no matching SHA-1 OAuth client on this APK build
        // (including Play Services "[16] Account reauth failed." mapped to GetCredentialCancellationException)
        Log.w(TAG, "Credential Manager unavailable (${secondErr?.message}); falling back to Firebase OAuthProvider")
        return signInWithWebOAuthFallback(activity, fbAuth)
    }

    private fun isUserExplicitCancel(err: Throwable?): Boolean {
        if (err !is GetCredentialCancellationException) return false
        val msg = err.message ?: return true
        // Play Services throws "[16] Account reauth failed." inside GetCredentialCancellationException
        // when the APK's SHA-1 is not registered in Firebase Console; fall back to OAuthProvider instead of aborting.
        if (msg.contains("reauth", ignoreCase = true) ||
            msg.contains("[16]") ||
            msg.contains("failed", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private suspend fun tryCredentialSignIn(
        activity: Activity,
        credentialManager: CredentialManager,
        option: CredentialOption,
        fbAuth: FirebaseAuth
    ): Result<FirebaseUser> {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = fbAuth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    startRealtimeSync(user.uid)
                    Result.success(user)
                } else {
                    Result.failure(Exception("User authentication failed"))
                }
            } else {
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun signInWithWebOAuthFallback(
        activity: Activity,
        fbAuth: FirebaseAuth
    ): Result<FirebaseUser> {
        return try {
            val pending = fbAuth.pendingAuthResult
            val authResult = if (pending != null) {
                pending.await()
            } else {
                val provider = OAuthProvider.newBuilder("google.com")
                    .addCustomParameter("prompt", "select_account")
                    .build()
                fbAuth.startActivityForSignInWithProvider(activity, provider).await()
            }
            val user = authResult.user
            if (user != null) {
                _currentUser.value = user
                startRealtimeSync(user.uid)
                Result.success(user)
            } else {
                _syncStatus.value = "Sign-in failed"
                Result.failure(Exception("Google authentication returned no user"))
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Google Sign-In failed"
            val cancelled = msg.contains("canceled", ignoreCase = true) ||
                msg.contains("cancelled", ignoreCase = true)
            _syncStatus.value = if (cancelled) "Sign-in cancelled" else "Sign-in failed"
            Log.e(TAG, "OAuthProvider fallback failed: $msg", e)
            Result.failure(Exception(if (cancelled) "Sign-in cancelled" else msg, e))
        }
    }

    suspend fun uploadSession(session: StudySessionEntity, previousIsNonStudy: Boolean? = null) {
        val user = auth?.currentUser ?: return
        val fs = firestore ?: return
        try {
            // If category flipped (Study <-> NonStudy), remove from previous collection first
            if (previousIsNonStudy != null && previousIsNonStudy != session.isNonStudy) {
                val oldCol = if (previousIsNonStudy) "nonStudySessions" else "sessions"
                try {
                    val oldDocRef = fs.collection("users").document(user.uid).collection(oldCol).document(session.timestamp.toString())
                    oldDocRef.delete().await()
                    val querySnap = fs.collection("users").document(user.uid).collection(oldCol).whereEqualTo("ts", session.timestamp).get().await()
                    for (doc in querySnap.documents) {
                        doc.reference.delete().await()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clean old collection doc: ${e.message}")
                }
            }

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
            val docRef = fs.collection("users")
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

    suspend fun deleteSessionFromCloud(timestamp: Long, isNonStudy: Boolean) {
        val user = auth?.currentUser ?: return
        val fs = firestore ?: return
        try {
            val collectionName = if (isNonStudy) "nonStudySessions" else "sessions"
            val col = fs.collection("users").document(user.uid).collection(collectionName)

            // 1. Delete if doc id is the timestamp
            col.document(timestamp.toString()).delete().await()

            // 2. Delete if web created doc with auto-generated ID but same ts
            val querySnap = col.whereEqualTo("ts", timestamp).get().await()
            for (doc in querySnap.documents) {
                doc.reference.delete().await()
            }
            Log.d(TAG, "Successfully deleted session $timestamp from Cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session from cloud: ${e.message}")
        }
    }

    suspend fun bulkDeleteSessionsFromCloud(timestamps: List<Long>) {
        val user = auth?.currentUser ?: return
        val fs = firestore ?: return
        try {
            val sessionsCol = fs.collection("users").document(user.uid).collection("sessions")
            val nonStudyCol = fs.collection("users").document(user.uid).collection("nonStudySessions")

            val distinctTs = timestamps.distinct()
            val refsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
            for (ts in distinctTs) {
                refsToDelete.add(sessionsCol.document(ts.toString()))
                refsToDelete.add(nonStudyCol.document(ts.toString()))
                try {
                    sessionsCol.whereEqualTo("ts", ts).get().await().documents.forEach {
                        refsToDelete.add(it.reference)
                    }
                    nonStudyCol.whereEqualTo("ts", ts).get().await().documents.forEach {
                        refsToDelete.add(it.reference)
                    }
                } catch (_: Exception) {}
            }
            refsToDelete.distinctBy { it.path }.chunked(400).forEach { chunk ->
                val batch = fs.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bulk delete sessions: ${e.message}")
        }
    }

    suspend fun uploadRestoredSessionsToCloud(sessions: List<StudySessionEntity>) {
        val user = auth?.currentUser ?: return
        val fs = firestore ?: return
        val cleanSessions = SessionDeduplicator.deduplicateList(sessions)
        if (cleanSessions.isEmpty()) return
        try {
            val sessionsCol = fs.collection("users").document(user.uid).collection("sessions")
            val nonStudyCol = fs.collection("users").document(user.uid).collection("nonStudySessions")

            cleanSessions.chunked(400).forEach { chunk ->
                val batch = fs.batch()
                for (s in chunk) {
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
                    batch.set(targetCol.document(s.timestamp.toString()), data, SetOptions.merge())
                }
                batch.commit().await()
            }
            _syncStatus.value = "Synced with Web & Cloud"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload restored sessions to cloud: ${e.message}")
        }
    }

    suspend fun uploadPrefsToCloud(
        dailyTarget: Int,
        subjects: List<SubjectEntity>,
        workTypes: List<WorkTypeEntity>
    ) {
        val user = auth?.currentUser ?: return
        val fs = firestore ?: return
        try {
            val subjectsList = subjects.map { s ->
                hashMapOf(
                    "name" to s.name,
                    "isCore" to s.isCore,
                    "isNonStudy" to s.isNonStudy,
                    "sub" to s.subSubjects
                )
            }
            val workTypesList = workTypes.map { it.name }

            val examGoalMap = hashMapOf(
                "examName" to (prefs.getString("exam_goal_name", "Target Exam / Syllabus") ?: "Target Exam / Syllabus"),
                "targetDate" to (prefs.getString("exam_goal_date", "") ?: ""),
                "targetHours" to prefs.getFloat("exam_goal_hours", 150f).toDouble(),
                "subjectScope" to (prefs.getStringSet("exam_goal_scope", emptySet()) ?: emptySet()).toList()
            )

            val data = hashMapOf(
                "dailyTarget" to dailyTarget,
                "subjects" to subjectsList,
                "workTypes" to workTypesList,
                "examGoal" to examGoalMap
            )

            fs.collection("users")
                .document(user.uid)
                .collection("meta")
                .document("prefs")
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload prefs: ${e.message}")
        }
    }

    private suspend fun syncLocalPrefsToCloud(uid: String) {
        try {
            val localSubjects = studyDao.getAllSubjects().first()
            val localWorkTypes = studyDao.getAllWorkTypes().first()
            val dailyTarget = prefs.getInt("daily_target_mins", 120)

            uploadPrefsToCloud(dailyTarget, localSubjects, localWorkTypes)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local prefs to cloud: ${e.message}")
        }
    }

    private suspend fun applyCloudPrefs(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            // 1. Daily Target
            val cloudTarget = snapshot.getLong("dailyTarget")?.toInt()
            if (cloudTarget != null && cloudTarget > 0) {
                prefs.edit().putInt("daily_target_mins", cloudTarget).apply()
            }

            // 2. Subjects (preserve existing row id so cloud changes update in place instead of being purged by MIN(id))
            @Suppress("UNCHECKED_CAST")
            val rawSubjects = snapshot.get("subjects") as? List<Map<String, Any>>
            if (!rawSubjects.isNullOrEmpty()) {
                val list = rawSubjects.mapNotNull { map ->
                    val name = (map["name"] as? String)?.trim() ?: return@mapNotNull null
                    if (name.isBlank()) return@mapNotNull null
                    val isCore = map["isCore"] as? Boolean ?: false
                    val isNonStudy = map["isNonStudy"] as? Boolean ?: false
                    @Suppress("UNCHECKED_CAST")
                    val sub = (map["sub"] as? List<*>)?.mapNotNull { it?.toString() }
                        ?: (map["subSubjects"] as? List<*>)?.mapNotNull { it?.toString() }
                        ?: emptyList()

                    val existingSubj = studyDao.getSubjectByName(name)
                    SubjectEntity(
                        id = existingSubj?.id ?: 0L,
                        name = name,
                        isCore = isCore,
                        isNonStudy = isNonStudy,
                        subSubjects = sub
                    )
                }
                if (list.isNotEmpty()) {
                    studyDao.insertSubjects(list)
                    studyDao.deduplicateSubjects()
                }
            }

            // 3. Work Types
            @Suppress("UNCHECKED_CAST")
            val rawWorkTypes = snapshot.get("workTypes") as? List<*>
            if (!rawWorkTypes.isNullOrEmpty()) {
                val wtEntities = rawWorkTypes.mapNotNull { it?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .map { name ->
                        val existingWt = studyDao.getWorkTypeByName(name)
                        WorkTypeEntity(id = existingWt?.id ?: 0L, name = name)
                    }
                if (wtEntities.isNotEmpty()) {
                    studyDao.insertWorkTypes(wtEntities)
                    studyDao.deduplicateWorkTypes()
                }
            }

            // 4. Exam Goal
            @Suppress("UNCHECKED_CAST")
            val rawExamGoal = snapshot.get("examGoal") as? Map<String, Any>
            if (rawExamGoal != null) {
                val name = rawExamGoal["examName"] as? String ?: "Target Exam / Syllabus"
                val date = rawExamGoal["targetDate"] as? String ?: ""
                val hours = (rawExamGoal["targetHours"] as? Number)?.toFloat() ?: 150f
                @Suppress("UNCHECKED_CAST")
                val scope = (rawExamGoal["subjectScope"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

                prefs.edit()
                    .putString("exam_goal_name", name)
                    .putString("exam_goal_date", date)
                    .putFloat("exam_goal_hours", hours)
                    .putStringSet("exam_goal_scope", scope)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying cloud prefs: ${e.message}")
        }
    }

    private suspend fun uploadLocalSessionsToCloud(uid: String) {
        val fs = firestore ?: return
        try {
            val rawSessions = SessionSyncLock.mutex.withLock {
                SessionDeduplicator.deduplicateStoredSessions(studyDao)
                studyDao.getAllSessionsOrderByIdAsc()
            }
            val localSessions = SessionDeduplicator.deduplicateList(rawSessions)
            if (localSessions.isEmpty()) return

            val sessionsCol = fs.collection("users").document(uid).collection("sessions")
            val nonStudyCol = fs.collection("users").document(uid).collection("nonStudySessions")

            // Process in atomic batches of 400 (well within Firestore 500-op limit)
            localSessions.chunked(400).forEach { chunk ->
                val batch = fs.batch()
                for (s in chunk) {
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
                    batch.set(targetCol.document(s.timestamp.toString()), data, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bulk upload: ${e.message}")
        }
    }

    private suspend fun processSessionChanges(
        changes: List<DocumentChange>,
        isNonStudy: Boolean
    ) {
        if (changes.isEmpty()) return
        try {
            SessionSyncLock.mutex.withLock {
                val localSessions = studyDao.getAllSessionsOrderByIdAsc()
                val sessionsToInsert = mutableListOf<StudySessionEntity>()
                val timestampsToDelete = mutableListOf<Long>()

                for (change in changes) {
                    val doc = change.document
                    val ts = doc.getLong("ts") ?: continue

                    when (change.type) {
                        DocumentChange.Type.REMOVED -> {
                            timestampsToDelete.add(ts)
                        }
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            val date = doc.getString("date") ?: ""
                            val subject = doc.getString("subject") ?: "General"
                            val subSubject = doc.getString("subSubject").takeIf { !it.isNullOrBlank() }
                            val workType = doc.getString("workType") ?: "Other"
                            val minutes = doc.getLong("minutes")?.toInt() ?: 0
                            if (minutes <= 0) continue

                            val incoming = StudySessionEntity(
                                id = 0L,
                                date = date,
                                subject = subject,
                                subSubject = subSubject,
                                workType = workType,
                                minutes = minutes,
                                timestamp = ts,
                                isNonStudy = isNonStudy
                            )

                            // Skip if another cloud doc in this same batch already represents this session
                            if (sessionsToInsert.any { SessionDeduplicator.isDuplicateOf(it, incoming) }) {
                                continue
                            }

                            val existing = localSessions.firstOrNull {
                                SessionDeduplicator.isDuplicateOf(it, incoming)
                            }

                            if (existing == null ||
                                existing.date != date ||
                                existing.minutes != minutes ||
                                existing.subject != subject ||
                                existing.workType != workType ||
                                existing.isNonStudy != isNonStudy
                            ) {
                                sessionsToInsert.add(incoming.copy(id = existing?.id ?: 0L))
                            }
                        }
                    }
                }

                // Batch deletions
                for (ts in timestampsToDelete.distinct()) {
                    studyDao.deleteSessionByTimestamp(ts)
                }

                // Single batch insert + clean up any residual duplicates
                if (sessionsToInsert.isNotEmpty()) {
                    studyDao.insertSessions(sessionsToInsert)
                    SessionDeduplicator.deduplicateStoredSessions(studyDao)
                    Log.d(TAG, "Batch synced ${sessionsToInsert.size} sessions from Cloud")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing session changes: ${e.message}")
        }
    }

    suspend fun manualSyncNow() {
        val user = auth?.currentUser ?: return
        _isSyncing.value = true
        _syncStatus.value = "Syncing..."
        try {
            // Add a 15-second timeout so it never hangs indefinitely
            withTimeoutOrNull(15000L) {
                uploadLocalSessionsToCloud(user.uid)
                syncLocalPrefsToCloud(user.uid)
            }
            _syncStatus.value = "Synced with Web & Cloud"
        } catch (e: Exception) {
            Log.e(TAG, "Error in manualSyncNow: ${e.message}")
            _syncStatus.value = "Sync completed with warning"
        } finally {
            _isSyncing.value = false
        }
    }

    fun signOut() {
        auth?.signOut()
        _currentUser.value = null
        stopRealtimeSync()
    }
}


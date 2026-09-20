package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmHelper
import com.example.alarm.AlarmService
import com.example.data.local.AppDatabase
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import com.example.data.repository.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.ui.theme.AppTheme
import com.example.util.ExamGoal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("focus_timer_prefs", Context.MODE_PRIVATE)
    private val _currentAppTheme = MutableStateFlow(
        AppTheme.fromId(prefs.getString("app_theme", "dark"))
    )
    val currentAppTheme: StateFlow<AppTheme> = _currentAppTheme.asStateFlow()

    private val _examGoal = MutableStateFlow(
        ExamGoal(
            examName = prefs.getString("exam_goal_name", "Target Exam / Syllabus") ?: "Target Exam / Syllabus",
            targetDate = prefs.getString("exam_goal_date", "") ?: "",
            targetHours = prefs.getFloat("exam_goal_hours", 150f).toDouble(),
            subjectScope = prefs.getStringSet("exam_goal_scope", emptySet()) ?: emptySet()
        )
    )
    val examGoal: StateFlow<ExamGoal> = _examGoal.asStateFlow()

    fun updateExamGoal(name: String, targetDate: String, targetHours: Double, subjectScope: Set<String>) {
        val newGoal = ExamGoal(
            examName = name.ifBlank { "Target Exam / Syllabus" },
            targetDate = targetDate,
            targetHours = targetHours.coerceAtLeast(1.0),
            subjectScope = subjectScope
        )
        _examGoal.value = newGoal
        prefs.edit()
            .putString("exam_goal_name", newGoal.examName)
            .putString("exam_goal_date", newGoal.targetDate)
            .putFloat("exam_goal_hours", newGoal.targetHours.toFloat())
            .putStringSet("exam_goal_scope", newGoal.subjectScope)
            .apply()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val subs = repository.getAllSubjects().first()
                val wts = repository.getAllWorkTypes().first()
                repository.syncManager.uploadPrefsToCloud(
                    dailyTarget = _dailyTargetMinutes.value,
                    subjects = subs,
                    workTypes = wts
                )
            } catch (e: Exception) {
                // Ignore network/offline error
            }
        }
    }

    fun reloadSettingsAndExamGoal() {
        _dailyTargetMinutes.value = repository.dailyTargetMinutes
        _examGoal.value = repository.getExamGoal()
    }

    fun toggleTheme() {
        val nextTheme = if (_currentAppTheme.value.isLight) AppTheme.OLED_DARK else AppTheme.PURE_WHITE
        setAppTheme(nextTheme)
    }

    fun setAppTheme(theme: AppTheme) {
        _currentAppTheme.value = theme
        prefs.edit().putString("app_theme", theme.id).apply()
        showToast("${theme.displayName} ${theme.icon} applied")
    }

    private val db = AppDatabase.getInstance(application)
    val repository = FocusRepository(db.studyDao(), application)

    // Pre-populate data if needed
    init {
        viewModelScope.launch {
            AppDatabase.prepopulateData(db.studyDao())
        }
    }

    val allSessions: StateFlow<List<StudySessionEntity>> = repository.getAllSessions()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubjects: StateFlow<List<SubjectEntity>> = repository.getAllSubjects()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkTypes: StateFlow<List<WorkTypeEntity>> = repository.getAllWorkTypes()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Alarm & Sync States
    val isAlarmRinging: StateFlow<Boolean> = AlarmService.isAlarmRinging
    val currentUser = repository.syncManager.currentUser
    val syncStatus = repository.syncManager.syncStatus
    val isSyncing = repository.syncManager.isSyncing

    fun stopAlarm() {
        AlarmService.stop(getApplication())
    }

    fun signInWithGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = repository.syncManager.signInWithGoogle(activity)
            result.onSuccess { user ->
                showToast("Signed in as ${user.email ?: "User"}")
            }.onFailure { err ->
                showToast("Sign in: ${err.message ?: "Cancelled"}")
            }
        }
    }

    fun manualSync() {
        viewModelScope.launch {
            repository.syncManager.manualSyncNow()
            showToast("Cloud sync complete")
        }
    }

    fun signOut() {
        repository.syncManager.signOut()
        showToast("Signed out")
    }


    // Timer States
    private val _isTimerActive = MutableStateFlow(false)
    val isTimerActive: StateFlow<Boolean> = _isTimerActive.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _stopwatchMode = MutableStateFlow(false)
    val stopwatchMode: StateFlow<Boolean> = _stopwatchMode.asStateFlow()

    private val _pomodoroMode = MutableStateFlow(false)
    val pomodoroMode: StateFlow<Boolean> = _pomodoroMode.asStateFlow()

    private val _pomoPhase = MutableStateFlow("work") // "work" or "break"
    val pomoPhase: StateFlow<String> = _pomoPhase.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _totalSeconds = MutableStateFlow(0L)
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    private val _inputHours = MutableStateFlow(0)
    val inputHours: StateFlow<Int> = _inputHours.asStateFlow()

    private val _inputMinutes = MutableStateFlow(0)
    val inputMinutes: StateFlow<Int> = _inputMinutes.asStateFlow()

    private val _selectedSubject = MutableStateFlow("")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val _selectedSubSubject = MutableStateFlow<String?>(null)
    val selectedSubSubject: StateFlow<String?> = _selectedSubSubject.asStateFlow()

    private val _selectedWorkType = MutableStateFlow("Revision")
    val selectedWorkType: StateFlow<String> = _selectedWorkType.asStateFlow()

    private val _showResetConfirmDialog = MutableStateFlow(false)
    val showResetConfirmDialog: StateFlow<Boolean> = _showResetConfirmDialog.asStateFlow()

    private val _pendingResetMinutes = MutableStateFlow(0)
    val pendingResetMinutes: StateFlow<Int> = _pendingResetMinutes.asStateFlow()

    private val _isMuted = MutableStateFlow(repository.isSoundMuted)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isDialVisible = MutableStateFlow(repository.isDialVisible)
    val isDialVisible: StateFlow<Boolean> = _isDialVisible.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _dailyTargetMinutes = MutableStateFlow(repository.dailyTargetMinutes)
    val dailyTargetMinutes: StateFlow<Int> = _dailyTargetMinutes.asStateFlow()

    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _alarmToneId = MutableStateFlow(repository.alarmToneId)
    val alarmToneId: StateFlow<String> = _alarmToneId.asStateFlow()

    private val _alarmCustomUri = MutableStateFlow(repository.alarmCustomUri)
    val alarmCustomUri: StateFlow<String?> = _alarmCustomUri.asStateFlow()

    private val _alarmVolumeBoost = MutableStateFlow(repository.alarmVolumeBoost)
    val alarmVolumeBoost: StateFlow<Boolean> = _alarmVolumeBoost.asStateFlow()

    private val _isPreviewingSound = MutableStateFlow(false)
    val isPreviewingSound: StateFlow<Boolean> = _isPreviewingSound.asStateFlow()

    private var timerJob: Job? = null
    private var startAtTimestamp: Long = 0L
    private var endTimestamp: Long = 0L

    private val vibrator: Vibrator? by lazy {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {}

        restoreSavedTimerState()
    }

    private fun restoreSavedTimerState() {
        if (!repository.getSavedTimerActive()) return

        val wasRunning = repository.getSavedTimerRunning()
        val isStopwatch = repository.getSavedTimerStopwatch()
        val isPomo = repository.getSavedTimerPomodoro()
        val pomoPhaseSaved = repository.getSavedTimerPomoPhase()
        val subj = repository.getSavedTimerSubject()
        val subSubj = repository.getSavedTimerSubSubject()
        val workType = repository.getSavedTimerWorkType()
        val startTs = repository.getSavedTimerStartAtTs()
        val endTs = repository.getSavedTimerEndTs()
        val totalSec = repository.getSavedTimerTotalSec()
        val remSec = repository.getSavedTimerRemainingSec()

        _isTimerActive.value = true
        _stopwatchMode.value = isStopwatch
        _pomodoroMode.value = isPomo
        _pomoPhase.value = pomoPhaseSaved
        _selectedSubject.value = subj
        _selectedSubSubject.value = subSubj
        _selectedWorkType.value = workType
        _totalSeconds.value = totalSec
        startAtTimestamp = startTs
        endTimestamp = endTs

        if (wasRunning) {
            val now = System.currentTimeMillis()
            if (isStopwatch) {
                val elapsed = (now - startTs) / 1000
                _remainingSeconds.value = elapsed.coerceAtLeast(0L)
                _isRunning.value = true
                startForegroundTracking()
                startTicker()
            } else {
                val left = (endTs - now) / 1000
                if (left <= 0) {
                    _remainingSeconds.value = 0L
                    _isRunning.value = false
                    onTimerFinished()
                } else {
                    _remainingSeconds.value = left
                    _isRunning.value = true
                    startForegroundTracking()
                    startTicker()
                }
            }
        } else {
            // Paused state restored
            _remainingSeconds.value = remSec
            _isRunning.value = false
        }
    }

    private fun persistCurrentTimerState() {
        if (!_isTimerActive.value) {
            repository.clearRunningTimerState()
            return
        }
        repository.saveRunningTimerState(
            isActive = _isTimerActive.value,
            isRunning = _isRunning.value,
            isStopwatch = _stopwatchMode.value,
            isPomodoro = _pomodoroMode.value,
            pomoPhase = _pomoPhase.value,
            subject = _selectedSubject.value,
            subSubject = _selectedSubSubject.value,
            workType = _selectedWorkType.value,
            startAtTs = startAtTimestamp,
            endTs = endTimestamp,
            totalSec = _totalSeconds.value,
            remainingSec = _remainingSeconds.value
        )
    }

    private fun startForegroundTracking() {
        val sec = _remainingSeconds.value
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        AlarmService.startTracking(getApplication(), _selectedSubject.value, timeText)
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun dismissConfetti() {
        _showConfetti.value = false
    }

    fun setInputHours(h: Int) {
        _inputHours.value = h.coerceIn(0, 12)
    }

    fun setInputMinutes(m: Int) {
        _inputMinutes.value = m.coerceIn(0, 59)
    }

    fun selectSubject(subject: SubjectEntity) {
        _selectedSubject.value = subject.name
        _selectedSubSubject.value = subject.subSubjects.firstOrNull()
    }

    fun selectSubSubject(sub: String) {
        _selectedSubSubject.value = sub
    }

    fun selectWorkType(wt: String) {
        _selectedWorkType.value = wt
    }


    fun togglePomodoro() {
        if (_isTimerActive.value) return
        val newMode = !_pomodoroMode.value
        _pomodoroMode.value = newMode
        _pomoPhase.value = "work"
    }

    fun toggleDial() {
        val newVisible = !_isDialVisible.value
        _isDialVisible.value = newVisible
        repository.isDialVisible = newVisible
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        repository.isSoundMuted = newMuted
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setDailyTarget(target: Int) {
        val clamped = target.coerceAtLeast(10)
        _dailyTargetMinutes.value = clamped
        repository.dailyTargetMinutes = clamped
        viewModelScope.launch {
            repository.syncPrefsToCloud()
        }
    }


    fun startTimer() {
        if (_selectedSubject.value.isBlank()) {
            showToast("Please select a subject to start")
            return
        }
        vibratePhone(40)
        if (!_isTimerActive.value) {
            // First time start
            if (_pomodoroMode.value) {
                val mins = if (_pomoPhase.value == "work") 25 else 5
                val secs = mins * 60L
                _totalSeconds.value = secs
                _remainingSeconds.value = secs
                _stopwatchMode.value = false
                endTimestamp = System.currentTimeMillis() + secs * 1000
            } else {
                val h = _inputHours.value
                val m = _inputMinutes.value
                val secs = (h * 3600 + m * 60).toLong()
                if (secs == 0L) {
                    // Stopwatch mode
                    _stopwatchMode.value = true
                    _totalSeconds.value = 0L
                    _remainingSeconds.value = 0L
                    startAtTimestamp = System.currentTimeMillis()
                } else {
                    // Countdown mode
                    _stopwatchMode.value = false
                    _totalSeconds.value = secs
                    _remainingSeconds.value = secs
                    endTimestamp = System.currentTimeMillis() + secs * 1000
                }
            }
            _isTimerActive.value = true
        } else {
            // Resuming from pause
            if (_stopwatchMode.value) {
                startAtTimestamp = System.currentTimeMillis() - (_remainingSeconds.value * 1000)
            } else {
                endTimestamp = System.currentTimeMillis() + (_remainingSeconds.value * 1000)
            }
        }

        _isRunning.value = true
        persistCurrentTimerState()
        startForegroundTracking()

        if (!_stopwatchMode.value) {
            AlarmHelper.scheduleAlarm(getApplication(), endTimestamp, _selectedSubject.value)
        }
        startTicker()
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var counter = 0
            while (_isRunning.value) {
                delay(1000L)
                counter++
                if (_stopwatchMode.value) {
                    val elapsed = (System.currentTimeMillis() - startAtTimestamp) / 1000
                    _remainingSeconds.value = elapsed.coerceAtLeast(0L)
                } else {
                    val left = (endTimestamp - System.currentTimeMillis()) / 1000
                    if (left <= 0) {
                        _remainingSeconds.value = 0
                        onTimerFinished()
                        break
                    } else {
                        _remainingSeconds.value = left
                    }
                }

                // Update notification text every 30 seconds to minimize Binder IPC and CPU churn
                if (counter % 30 == 0) {
                    val sec = _remainingSeconds.value
                    val h = sec / 3600
                    val m = (sec % 3600) / 60
                    val s = sec % 60
                    val timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                    val updateIntent = Intent(getApplication(), AlarmService::class.java).apply {
                        action = AlarmService.ACTION_UPDATE_TRACKING
                        putExtra(AlarmService.EXTRA_TITLE, _selectedSubject.value)
                        putExtra(AlarmService.EXTRA_TIME_TEXT, timeText)
                    }
                    try {
                        getApplication<Application>().startService(updateIntent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun pauseTimer() {
        vibratePhone(40)
        _isRunning.value = false
        timerJob?.cancel()
        AlarmHelper.cancelAlarm(getApplication())
        persistCurrentTimerState()
        // Stop foreground notification when user explicitly pauses
        AlarmService.stop(getApplication())
    }

    fun resetTimer() {
        vibratePhone(60)
        AlarmHelper.cancelAlarm(getApplication())
        AlarmService.stop(getApplication())
        val wasActive = _isTimerActive.value
        val wasStopwatch = _stopwatchMode.value
        val finalSecs = if (wasStopwatch) {
            _remainingSeconds.value
        } else {
            _totalSeconds.value - _remainingSeconds.value
        }

        val isBreakPhase = _pomodoroMode.value && _pomoPhase.value == "break"
        if (isBreakPhase) {
            pauseTimer()
            _isTimerActive.value = false
            _remainingSeconds.value = 0
            _totalSeconds.value = 0
            _stopwatchMode.value = false
            repository.clearRunningTimerState()
            _pomoPhase.value = "work"
            showToast("☕ Break cancelled")
            return
        }

        if (wasActive && finalSecs >= 60) {
            pauseTimer()
            _pendingResetMinutes.value = (finalSecs / 60).toInt()
            _showResetConfirmDialog.value = true
            return
        }

        pauseTimer()
        _isTimerActive.value = false
        _remainingSeconds.value = 0
        _totalSeconds.value = 0
        _stopwatchMode.value = false
        repository.clearRunningTimerState()

        if (wasActive && finalSecs > 0) {
            showToast("Not saved as it was less than 1 min")
        }

        if (_pomodoroMode.value) {
            _pomoPhase.value = "work"
        }
    }

    fun confirmResetAndSave() {
        val mins = _pendingResetMinutes.value
        _showResetConfirmDialog.value = false
        _pendingResetMinutes.value = 0
        _isTimerActive.value = false
        _remainingSeconds.value = 0
        _totalSeconds.value = 0
        _stopwatchMode.value = false
        repository.clearRunningTimerState()
        if (_pomodoroMode.value) {
            _pomoPhase.value = "work"
        }
        if (mins >= 1) {
            saveSession(mins)
        }
    }

    fun confirmResetAndDiscard() {
        _showResetConfirmDialog.value = false
        _pendingResetMinutes.value = 0
        _isTimerActive.value = false
        _remainingSeconds.value = 0
        _totalSeconds.value = 0
        _stopwatchMode.value = false
        repository.clearRunningTimerState()
        if (_pomodoroMode.value) {
            _pomoPhase.value = "work"
        }
        showToast("Session discarded")
    }

    fun cancelResetDialog() {
        _showResetConfirmDialog.value = false
        _pendingResetMinutes.value = 0
    }

    private fun onTimerFinished() {
        _isRunning.value = false
        _isTimerActive.value = false
        timerJob?.cancel()
        AlarmHelper.cancelAlarm(getApplication())
        repository.clearRunningTimerState()

        // Trigger continuous loud alarm service
        try {
            val serviceIntent = Intent(getApplication(), AlarmService::class.java).apply {
                action = AlarmService.ACTION_START_ALARM
                putExtra(AlarmService.EXTRA_TITLE, _selectedSubject.value)
                putExtra(AlarmService.EXTRA_IS_MUTED, _isMuted.value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(serviceIntent)
            } else {
                getApplication<Application>().startService(serviceIntent)
            }
        } catch (_: Exception) {
            playAlertSound()
            vibratePhone(800)
        }

        if (_pomodoroMode.value) {
            if (_pomoPhase.value == "work") {
                saveSession(25)
                showToast("⏳ Focus session complete! Take a 5-min break.")
                _pomoPhase.value = "break"
            } else {
                showToast("☕ Break finished! Ready for next session.")
                _pomoPhase.value = "work"
            }
        } else {
            val mins = (_totalSeconds.value / 60).toInt()
            if (mins >= 1) {
                saveSession(mins)
            }
            showToast("⏰ Time is up!")
        }
    }

    private fun saveSession(minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            val subj = allSubjects.value.find { it.name.equals(_selectedSubject.value, ignoreCase = true) }
            val isNS = subj?.isNonStudy ?: false
            val sessionName = if (!_selectedSubSubject.value.isNullOrBlank()) {
                "${_selectedSubject.value} - ${_selectedSubSubject.value}"
            } else {
                _selectedSubject.value
            }

            val entity = StudySessionEntity(
                date = FocusRepository.getTodayString(),
                subject = sessionName,
                subSubject = _selectedSubSubject.value,
                workType = _selectedWorkType.value,
                minutes = minutes,
                timestamp = System.currentTimeMillis(),
                isNonStudy = isNS
            )
            repository.addSession(entity)
            showToast("✅ $minutes mins saved")

            // Check if daily target completed
            checkDailyTargetAchievement(minutes)
        }
    }

    private fun checkDailyTargetAchievement(addedMinutes: Int) {
        val today = FocusRepository.getTodayString()
        val currentTotal = allSessions.value
            .filter { it.date == today && !it.isNonStudy }
            .sumOf { it.minutes }
        val prevTotal = currentTotal - addedMinutes
        val target = _dailyTargetMinutes.value

        if (prevTotal < target && currentTotal >= target) {
            _showConfetti.value = true
            showToast("🎉 Daily target achieved! Great job!")
        }
    }

    private fun playAlertSound() {
        if (_isMuted.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 1000)
        } catch (_: Exception) {}
    }

    private fun vibratePhone(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun updateSession(session: StudySessionEntity, previousIsNonStudy: Boolean? = null) {
        viewModelScope.launch {
            repository.updateSession(session, previousIsNonStudy)
            showToast("Session updated & synced")
        }
    }

    fun restoreSession(session: StudySessionEntity) {
        viewModelScope.launch {
            repository.addSession(session)
            showToast("Session restored")
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.deleteSession(id)
            showToast("Session deleted")
        }
    }

    fun bulkDelete(fromDate: String, toDate: String) {
        viewModelScope.launch {
            val count = repository.deleteSessionsBetweenDates(fromDate, toDate)
            showToast("$count sessions deleted")
        }
    }

    fun addSubject(name: String, isCore: Boolean, isNonStudy: Boolean) {
        viewModelScope.launch {
            repository.addSubject(SubjectEntity(name = name, isCore = isCore, isNonStudy = isNonStudy))
            _selectedSubject.value = name
            _selectedSubSubject.value = null
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.updateSubject(subject)
            if (_selectedSubject.value.equals(subject.name, ignoreCase = true)) {
                _selectedSubject.value = subject.name
            }
            showToast("Subject '${subject.name}' updated & synced")
        }
    }

    fun addSubSubject(subjectName: String, subName: String) {
        viewModelScope.launch {
            val subj = allSubjects.value.find { it.name.equals(subjectName, ignoreCase = true) } ?: return@launch
            val updatedSubs = subj.subSubjects + subName
            repository.updateSubject(subj.copy(subSubjects = updatedSubs))
            _selectedSubSubject.value = subName
        }
    }

    fun deleteSubject(id: Long) {
        viewModelScope.launch {
            val subj = allSubjects.value.find { it.id == id }
            repository.deleteSubject(id)
            if (subj?.name == _selectedSubject.value) {
                val remaining = allSubjects.value.filter { it.id != id }
                _selectedSubject.value = remaining.firstOrNull()?.name ?: "General"
                _selectedSubSubject.value = null
            }
        }
    }

    fun addWorkType(name: String) {
        viewModelScope.launch {
            repository.addWorkType(name)
            _selectedWorkType.value = name
        }
    }

    fun updateWorkType(id: Long, name: String) {
        viewModelScope.launch {
            repository.updateWorkType(id, name)
            if (_selectedWorkType.value.equals(name, ignoreCase = true)) {
                _selectedWorkType.value = name
            }
            showToast("Work type updated & synced")
        }
    }

    fun deleteWorkType(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkType(id)
        }
    }

    fun setAlarmTone(toneId: String) {
        _alarmToneId.value = toneId
        repository.alarmToneId = toneId
        showToast("Alarm sound set to ${com.example.alarm.AlarmToneOption.fromId(toneId).displayName}")
    }

    fun setAlarmCustomUri(uriStr: String?) {
        _alarmCustomUri.value = uriStr
        repository.alarmCustomUri = uriStr
        _alarmToneId.value = com.example.alarm.AlarmToneOption.CUSTOM_PICKER.id
        repository.alarmToneId = com.example.alarm.AlarmToneOption.CUSTOM_PICKER.id
        showToast("Custom alarm tone selected")
    }

    fun toggleAlarmVolumeBoost() {
        val newVal = !_alarmVolumeBoost.value
        _alarmVolumeBoost.value = newVal
        repository.alarmVolumeBoost = newVal
        showToast(if (newVal) "Maximum Alarm Volume Boost: ON 🔊" else "Maximum Volume Boost: OFF")
    }

    fun previewAlarmTone(toneOption: com.example.alarm.AlarmToneOption) {
        _isPreviewingSound.value = true
        com.example.alarm.AlarmSoundManager.playPreview(
            context = getApplication(),
            toneOption = toneOption,
            customUriStr = _alarmCustomUri.value,
            onFinished = {
                _isPreviewingSound.value = false
            }
        )
    }

    fun stopAlarmTonePreview() {
        com.example.alarm.AlarmSoundManager.stopPreview()
        _isPreviewingSound.value = false
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        toneGenerator?.release()
        com.example.alarm.AlarmSoundManager.stopPreview()
    }
}

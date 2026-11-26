package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.alarm.AlarmToneOption
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import com.example.data.repository.FocusRepository
import com.example.util.PdfExportHelper
import com.example.util.WebPortalHelper
import com.example.ui.components.AddSubjectDialog
import com.example.ui.components.AddWorkTypeDialog
import com.example.ui.components.EditSubjectDialog
import com.example.ui.components.EditWorkTypeDialog
import com.example.ui.components.SmoothTabHost
import com.example.ui.theme.ActiveTheme
import com.example.ui.theme.AppTheme
import com.example.ui.theme.BgDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldGradientBrush
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LineBorder
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SettingsSection(val label: String) {
    SYNC("Cloud Sync"),
    DATA("Backup & Data"),
    ALARM("Alarm"),
    SUBJECTS("Subjects")
}

@Composable
fun HistoryScreen(
    viewModel: FocusViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenSettings: (SettingsSection) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allSessions by viewModel.deduplicatedSessions.collectAsState()
    val subjects by viewModel.allSubjects.collectAsState()
    val workTypes by viewModel.allWorkTypes.collectAsState()

    var sessionToDelete by remember { mutableStateOf<StudySessionEntity?>(null) }
    var sessionToEdit by remember { mutableStateOf<StudySessionEntity?>(null) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var recentlyDeletedSession by remember { mutableStateOf<StudySessionEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "History",
                        style = TextStyle(
                            brush = GoldGradientBrush,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${allSessions.size} recorded sessions",
                        color = TextDim,
                        fontSize = 11.5.sp
                    )
                }
            }

            Surface(
                onClick = { onOpenSettings(SettingsSection.DATA) },
                shape = RoundedCornerShape(10.dp),
                color = PanelElevated,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Backup & Data Settings",
                        tint = GoldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Data",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (recentlyDeletedSession != null) {
            val deleted = recentlyDeletedSession!!
            Surface(
                color = PanelElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deleted: ${deleted.subject} (${deleted.minutes}m)",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            viewModel.restoreSession(deleted)
                            recentlyDeletedSession = null
                        }) {
                            Text("UNDO", color = GoldBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { recentlyDeletedSession = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showAddSessionDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, GoldAccent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = PanelDark,
                contentColor = GoldBright
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Missed Session",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        if (allSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No study sessions recorded yet.\nStart a timer or tap Add Missed Session!",
                    color = TextDim,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = allSessions,
                    key = { it.id },
                    contentType = { "session_item" }
                ) { session ->
                    SessionHistoryItem(
                        session = session,
                        onEdit = { sessionToEdit = session },
                        onDelete = { sessionToDelete = session }
                    )
                }
            }
        }
    }

    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session", color = GoldBright) },
            text = { Text("Delete session for '${s.subject}' (${s.minutes} mins) on ${s.date}?", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        recentlyDeletedSession = s
                        viewModel.deleteSession(s.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    if (showAddSessionDialog || sessionToEdit != null) {
        val editing = sessionToEdit
        SessionFormDialog(
            existingSession = editing,
            allSessions = allSessions,
            subjects = subjects,
            workTypes = workTypes,
            onDismiss = {
                showAddSessionDialog = false
                sessionToEdit = null
            },
            onShowToast = { viewModel.showToast(it) },
            onSave = { date, subject, subSubject, workType, minutes, endTs, isNonStudy ->
                if (editing != null) {
                    val updated = editing.copy(
                        subject = subject,
                        subSubject = subSubject,
                        workType = workType,
                        minutes = minutes,
                        timestamp = endTs,
                        isNonStudy = isNonStudy
                    )
                    viewModel.updateSession(
                        updated,
                        previousIsNonStudy = editing.isNonStudy,
                        previousTimestamp = editing.timestamp
                    )
                    sessionToEdit = null
                } else {
                    viewModel.addManualSession(
                        date = date,
                        subject = subject,
                        subSubject = subSubject,
                        workType = workType,
                        minutes = minutes,
                        endTimestamp = endTs,
                        isNonStudy = isNonStudy
                    )
                    showAddSessionDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsToolsScreen(
    viewModel: FocusViewModel,
    selectedSection: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allSessions by viewModel.deduplicatedSessions.collectAsState()
    val subjects by viewModel.allSubjects.collectAsState()
    val workTypes by viewModel.allWorkTypes.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var subjectToEdit by remember { mutableStateOf<SubjectEntity?>(null) }
    var workTypeToEdit by remember { mutableStateOf<WorkTypeEntity?>(null) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddWorkTypeDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var pickingDateFor by remember { mutableStateOf<String?>(null) }

    // Backup & Export & Alarm state
    var exportRange by remember { mutableStateOf("Last 30 Days") }
    var rangeDropdownExpanded by remember { mutableStateOf(false) }
    var ringtoneDropdownExpanded by remember { mutableStateOf(false) }
    var jsonImportText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var isImportingJson by remember { mutableStateOf(false) }

    val alarmToneId by viewModel.alarmToneId.collectAsState()
    val alarmVolumeBoost by viewModel.alarmVolumeBoost.collectAsState()
    val isPreviewingSound by viewModel.isPreviewingSound.collectAsState()
    val alarmCustomUri by viewModel.alarmCustomUri.collectAsState()

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val jsonStr = viewModel.repository.exportToJson(allSessions)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        viewModel.showToast("Backup JSON saved successfully!")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        viewModel.showToast("Failed to save JSON: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                viewModel.setAlarmCustomUri(uri.toString())
            }
        }
    }

    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (isImportingJson) return@let
            isImportingJson = true
            coroutineScope.launch {
                try {
                    val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            stream.bufferedReader().use { r -> r.readText() }
                        }
                    }
                    if (content != null) {
                        val result = viewModel.repository.importFromJson(content)
                        viewModel.reloadSettingsAndExamGoal()
                        val msg = if (result.totalCount == 0) {
                            "Backup checked: all sessions already exist"
                        } else {
                            "Restore successful: ${result.studyCount} study, ${result.nonStudyCount} non-study added!"
                        }
                        viewModel.showToast(msg)
                    } else {
                        viewModel.showToast("Import error: Unable to read selected file")
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Import error: Invalid JSON file")
                } finally {
                    isImportingJson = false
                }
            }
        }
    }

    // Bulk delete dates
    val todayStr = remember { FocusRepository.getTodayString() }
    var fromDateStr by remember { mutableStateOf(todayStr) }
    var toDateStr by remember { mutableStateOf(todayStr) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Settings & Tools",
                    style = TextStyle(
                        brush = GoldGradientBrush,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Menu",
                    tint = TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        // 4-section selector: Cloud Sync | Backup & Data | Alarm | Subjects
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = PanelDark,
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsSection.entries.forEach { section ->
                    val isSelected = selectedSection == section
                    val btnContainerColor by animateColorAsState(
                        targetValue = if (isSelected) GoldBright else PanelElevated,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "settings_tab_bg_${section.name}"
                    )
                    val btnContentColor by animateColorAsState(
                        targetValue = if (isSelected) BgDark else TextDim,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "settings_tab_fg_${section.name}"
                    )
                    Surface(
                        onClick = { onSelectSection(section) },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = btnContainerColor,
                        border = if (isSelected) null else BorderStroke(1.dp, LineBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = section.label,
                                color = btnContentColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val sectionIdx = SettingsSection.entries.indexOf(selectedSection)
        SmoothTabHost(
            selectedIndex = sectionIdx,
            tabCount = SettingsSection.entries.size,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            prewarmAll = true
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                // CLOUD SYNC & ACCOUNT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cloud Sync Card (Web & Mobile Sync)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = GoldBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cloud Sync (Web & Mobile)",
                                        style = TextStyle(
                                            brush = GoldGradientBrush,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Status: $syncStatus",
                                color = GoldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (currentUser != null) {
                                Text(
                                    text = "Account: ${currentUser?.email ?: currentUser?.uid}",
                                    color = TextDim,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "Sign in to synchronize study sessions, custom subjects, and daily targets with your Web app in real-time.",
                                    color = TextDim,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            if (currentUser == null) {
                                Button(
                                    onClick = {
                                        val act = context as? android.app.Activity
                                        if (act != null) {
                                            viewModel.signInWithGoogle(act)
                                        } else {
                                            viewModel.showToast("Cannot launch Google Sign-In")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldAccent,
                                        contentColor = BgDark
                                    ),
                                    enabled = syncStatus != "Signing in...",
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Text(
                                        if (syncStatus == "Signing in...") "Signing in..." else "Sign in with Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.manualSync() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldAccent,
                                            contentColor = BgDark
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        enabled = !isSyncing
                                    ) {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isSyncing) "Syncing..." else "Sync Now")
                                    }

                                    OutlinedButton(
                                        onClick = { showSignOutConfirmDialog = true },
                                        border = BorderStroke(1.dp, DangerRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Sign Out", color = DangerRed)
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Focus Timer v${BuildConfig.VERSION_NAME}",
                            color = TextDim.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            1 -> {
                // BACKUP, EXPORT & BULK DELETE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Export Card
                    // DATA BACKUP & EXPORT (Matching Web layout)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Data Backup & Export",
                                style = TextStyle(
                                    brush = GoldGradientBrush,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Range dropdown selector
                            val ranges = listOf("Last 30 Days", "Last 7 Days", "Last 48h", "Last 24h", "Today", "All Time")
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { rangeDropdownExpanded = true },
                                    color = PanelElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, LineBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = exportRange,
                                            color = TextPrimary,
                                            fontSize = 14.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = GoldAccent
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = rangeDropdownExpanded,
                                    onDismissRequest = { rangeDropdownExpanded = false },
                                    modifier = Modifier.background(PanelDark)
                                ) {
                                    ranges.forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text(r, color = if (exportRange == r) GoldBright else TextPrimary) },
                                            onClick = {
                                                exportRange = r
                                                rangeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // PDF Button (Opens Web Dashboard with jsPDF-AutoTable)
                            Button(
                                onClick = {
                                    WebPortalHelper.openWebHistoryReport(context)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldAccent,
                                    contentColor = BgDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = BgDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Open PDF Report (Web)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Generates multi-page PDF tables with your synced sessions on the Web Dashboard.",
                                color = TextDim,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons: Download JSON (Direct Save), Share JSON, & Restore JSON (Upload)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        jsonSaveLauncher.launch("focus-timer-backup-${FocusRepository.getTodayString()}.json")
                                    },
                                    border = BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Download JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("(Save to Storage)", fontSize = 10.sp, color = TextDim)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showImportDialog = true },
                                    border = BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("(Merge & Restore)", fontSize = 10.sp, color = TextDim)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val jsonStr = viewModel.repository.exportToJson(allSessions)
                                                val cacheDir = java.io.File(context.cacheDir, "backups").apply { mkdirs() }
                                                val backupFile = java.io.File(cacheDir, "focus-timer-backup-${FocusRepository.getTodayString()}.json")
                                                backupFile.writeText(jsonStr)

                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    backupFile
                                                )

                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/json"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_SUBJECT, "Focus Timer Backup (${FocusRepository.getTodayString()})")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    context.startActivity(Intent.createChooser(sendIntent, "Share JSON Backup"))
                                                }
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    viewModel.showToast("Failed to share backup: ${e.localizedMessage}")
                                                }
                                            }
                                        }
                                    },
                                    border = BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .width(48.dp)
                                        .heightIn(min = 52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share JSON",
                                        tint = TextDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bulk Delete Card (moved cleanly into Backup & Data)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Bulk Delete (Date Range)",
                                color = DangerRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Choose a preset or tap From / To dates to remove sessions.",
                                color = TextDim,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Today" to 0,
                                    "Last 7 Days" to -6,
                                    "Last 30 Days" to -29
                                ).forEach { (label, offset) ->
                                    val targetFrom = shiftIsoDate(todayStr, offset)
                                    val isActive = fromDateStr == targetFrom && toDateStr == todayStr
                                    Surface(
                                        onClick = {
                                            fromDateStr = targetFrom
                                            toDateStr = todayStr
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                                        border = BorderStroke(1.dp, if (isActive) GoldBright else LineBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                color = if (isActive) GoldBright else TextDim,
                                                fontSize = 11.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DateSelectorCard(
                                    label = "FROM",
                                    dateStr = fromDateStr,
                                    onClick = { pickingDateFor = "from" },
                                    modifier = Modifier.weight(1f)
                                )
                                DateSelectorCard(
                                    label = "TO",
                                    dateStr = toDateStr,
                                    onClick = { pickingDateFor = "to" },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (fromDateStr.isBlank() || toDateStr.isBlank()) {
                                        viewModel.showToast("Please select From and To dates!")
                                        return@Button
                                    }
                                    if (fromDateStr > toDateStr) {
                                        viewModel.showToast("From Date cannot be greater than To Date!")
                                        return@Button
                                    }
                                    showBulkDeleteDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Sessions")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            2 -> {
                // ALARM SOUND & VOLUME SETTINGS
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Alarm Sound & Ringtone",
                                        style = TextStyle(
                                            brush = GoldGradientBrush,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "High volume alerts & customizable tones",
                                        color = TextDim,
                                        fontSize = 11.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = GoldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Current Selected Tone Selector Dropdown
                            val currentOption = AlarmToneOption.fromId(alarmToneId)
                            Text(
                                text = "Select Alarm Ringtone",
                                color = TextDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { ringtoneDropdownExpanded = true },
                                    color = PanelElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, LineBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = currentOption.displayName,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = currentOption.description,
                                                    color = TextDim,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = GoldAccent
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = ringtoneDropdownExpanded,
                                    onDismissRequest = { ringtoneDropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(PanelDark)
                                ) {
                                    AlarmToneOption.entries.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = if (alarmToneId == opt.id) GoldBright else TextDim,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            opt.displayName,
                                                            color = if (alarmToneId == opt.id) GoldBright else TextPrimary,
                                                            fontWeight = if (alarmToneId == opt.id) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            opt.description,
                                                            color = TextDim,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                ringtoneDropdownExpanded = false
                                                if (opt == AlarmToneOption.CUSTOM_PICKER) {
                                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Ringtone")
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                                        if (!alarmCustomUri.isNullOrBlank()) {
                                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmCustomUri))
                                                        }
                                                    }
                                                    ringtonePickerLauncher.launch(intent)
                                                } else {
                                                    viewModel.setAlarmTone(opt.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Test Sound Button & Custom Picker Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isPreviewingSound) {
                                            viewModel.stopAlarmTonePreview()
                                        } else {
                                            viewModel.previewAlarmTone(currentOption)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPreviewingSound) DangerRed else GoldAccent,
                                        contentColor = if (isPreviewingSound) Color.White else BgDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewingSound) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPreviewingSound) "Stop Test" else "Test Alarm Sound",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Phone Ringtone")
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            if (!alarmCustomUri.isNullOrBlank()) {
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmCustomUri))
                                            }
                                        }
                                        ringtonePickerLauncher.launch(intent)
                                    },
                                    border = BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Pick Ringtone",
                                        tint = GoldBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick File", color = TextPrimary, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Volume Boost Option Row
                            Surface(
                                color = PanelElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Maximum Volume Boost",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Forces alarm to play at 100% device volume",
                                                color = TextDim,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = alarmVolumeBoost,
                                        onCheckedChange = { viewModel.toggleAlarmVolumeBoost() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDark,
                                            checkedTrackColor = GoldAccent,
                                            uncheckedThumbColor = TextDim,
                                            uncheckedTrackColor = PanelDark
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            3 -> {
                // SUBJECTS & WORK TYPES MANAGEMENT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Subjects List
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Managed Subjects",
                                    style = TextStyle(
                                        brush = GoldGradientBrush,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                OutlinedButton(
                                    onClick = { showAddSubjectDialog = true },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, LineBorder),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Add", color = GoldBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            subjects.forEach { subj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { subjectToEdit = subj }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = subj.name,
                                                color = if (subj.isCore) GoldBright else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (subj.isCore) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "PINNED",
                                                    color = GoldAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (subj.isNonStudy) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "NON-STUDY",
                                                    color = TextDim,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        if (subj.subSubjects.isNotEmpty()) {
                                            Text(
                                                text = "Sub-subjects: " + subj.subSubjects.joinToString(", "),
                                                color = TextDim,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { subjectToEdit = subj },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Subject",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteSubject(subj.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Subject",
                                                tint = if (subjects.size > 1) DangerRed else TextDim.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }

                    // Work Types List
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Managed Work Types",
                                    style = TextStyle(
                                        brush = GoldGradientBrush,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                OutlinedButton(
                                    onClick = { showAddWorkTypeDialog = true },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, LineBorder),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Add", color = GoldBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            workTypes.forEach { wt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { workTypeToEdit = wt }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = wt.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { workTypeToEdit = wt },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Work Type",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteWorkType(wt.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Work Type",
                                                tint = if (workTypes.size > 1) DangerRed else TextDim.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
            }
        }
    }

    // Add / Edit Subject & Work Type Dialogs (shared unified dialogs)
    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onAdd = { name, isCore, isNonStudy ->
                viewModel.addSubject(name, isCore, isNonStudy)
                showAddSubjectDialog = false
            }
        )
    }

    if (showAddWorkTypeDialog) {
        AddWorkTypeDialog(
            onDismiss = { showAddWorkTypeDialog = false },
            onAdd = { name ->
                viewModel.addWorkType(name)
                showAddWorkTypeDialog = false
            }
        )
    }

    if (subjectToEdit != null) {
        val subj = subjects.find { it.id == subjectToEdit!!.id } ?: subjectToEdit!!
        EditSubjectDialog(
            subject = subj,
            canDelete = subjects.size > 1,
            onDismiss = { subjectToEdit = null },
            onSave = { updated ->
                viewModel.updateSubject(updated)
                subjectToEdit = null
            },
            onDelete = {
                viewModel.deleteSubject(subj.id)
                subjectToEdit = null
            }
        )
    }

    if (workTypeToEdit != null) {
        val wt = workTypes.find { it.id == workTypeToEdit!!.id } ?: workTypeToEdit!!
        EditWorkTypeDialog(
            workType = wt,
            canDelete = workTypes.size > 1,
            onDismiss = { workTypeToEdit = null },
            onSave = { newName ->
                viewModel.updateWorkType(wt.id, newName)
                workTypeToEdit = null
            },
            onDelete = {
                viewModel.deleteWorkType(wt.id)
                workTypeToEdit = null
            }
        )
    }

    // Sign out confirmation dialog
    if (showSignOutConfirmDialog) {
        val emailHint = currentUser?.email?.let { " ($it)" } ?: ""
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = { Text("Sign out of Google?", color = GoldBright) },
            text = {
                Text(
                    "Your local study sessions stay safe on this device. Cloud sync will pause until you sign back in$emailHint.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.signOut()
                        showSignOutConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Calendar DatePickerDialog for Bulk Delete From / To
    if (pickingDateFor != null) {
        val isFrom = pickingDateFor == "from"
        val initialMillis = remember(pickingDateFor, fromDateStr, toDateStr) {
            dateStrToUtcMillis(if (isFrom) fromDateStr else toDateStr) ?: System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { pickingDateFor = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val chosen = utcMillisToDateStr(millis)
                            if (isFrom) {
                                fromDateStr = chosen
                                if (toDateStr < chosen) toDateStr = chosen
                            } else {
                                toDateStr = chosen
                                if (fromDateStr > chosen) fromDateStr = chosen
                            }
                        }
                        pickingDateFor = null
                    }
                ) {
                    Text("Select", color = GoldBright, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDateFor = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = PanelDark)
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = if (isFrom) "Select Start Date" else "Select End Date",
                        color = GoldBright,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = PanelDark,
                    titleContentColor = GoldBright,
                    headlineContentColor = TextPrimary,
                    weekdayContentColor = TextDim,
                    subheadContentColor = TextPrimary,
                    navigationContentColor = TextPrimary,
                    yearContentColor = TextPrimary,
                    currentYearContentColor = GoldBright,
                    selectedYearContentColor = BgDark,
                    selectedYearContainerColor = GoldBright,
                    dayContentColor = TextPrimary,
                    selectedDayContentColor = BgDark,
                    selectedDayContainerColor = GoldBright,
                    todayContentColor = GoldBright,
                    todayDateBorderColor = GoldAccent
                )
            )
        }
    }

    // Bulk delete confirmation dialog
    if (showBulkDeleteDialog) {
        val fromPretty = com.example.util.DateFormatterCache.formatReadableDate(fromDateStr)
        val toPretty = com.example.util.DateFormatterCache.formatReadableDate(toDateStr)
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Confirm Bulk Delete", color = DangerRed) },
            text = { Text("Permanently delete all sessions from $fromPretty to $toPretty? This cannot be undone.", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDelete(fromDateStr, toDateStr)
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete Range")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Import JSON Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Restore JSON Backup", color = GoldBright) },
            text = {
                Column {
                    Text(
                        text = "Select a backup JSON file or paste JSON text below. New sessions from the backup will be safely merged into your history without deleting or overwriting existing sessions.",
                        color = TextDim,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            showImportDialog = false
                            jsonFilePickerLauncher.launch("application/json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PanelElevated, contentColor = GoldLight),
                        border = BorderStroke(1.dp, GoldAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose JSON File from Storage")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Or paste JSON directly:", color = TextDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = jsonImportText,
                        onValueChange = { jsonImportText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isImportingJson && jsonImportText.isNotBlank(),
                    onClick = {
                        if (isImportingJson) return@Button
                        isImportingJson = true
                        coroutineScope.launch {
                            try {
                                val result = viewModel.repository.importFromJson(jsonImportText)
                                viewModel.reloadSettingsAndExamGoal()
                                val msg = if (result.totalCount == 0) {
                                    "Backup checked: all sessions already exist"
                                } else {
                                    "Merge successful: ${result.studyCount} study, ${result.nonStudyCount} non-study added!"
                                }
                                viewModel.showToast(msg)
                                showImportDialog = false
                                jsonImportText = ""
                            } catch (e: Exception) {
                                viewModel.showToast("Import error: Invalid JSON")
                            } finally {
                                isImportingJson = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text(if (isImportingJson) "Importing..." else "Merge & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }
}

@Composable
private fun SessionHistoryItem(
    session: StudySessionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val endTs = session.timestamp
    val startTs = endTs - (session.minutes.toLong() * 60L * 1000L)

    val timeRangeStr = remember(endTs, session.minutes) {
        val startStr = com.example.util.DateFormatterCache.formatTime(startTs)
        val endStr = com.example.util.DateFormatterCache.formatTime(endTs)
        "$startStr - $endStr"
    }
    val dateStr = remember(endTs, session.date) {
        com.example.util.DateFormatterCache.formatReadableDate(session.date)
    }
    val workTypeStr = if (session.workType.isBlank()) "Other" else session.workType

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Line 1: Subject (left) + Duration & Actions (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.subject,
                        color = GoldBright,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (session.isNonStudy) {
                        Surface(
                            color = PanelElevated,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, LineBorder)
                        ) {
                            Text(
                                text = "Non-Study",
                                color = TextDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${session.minutes} min",
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Session",
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Session",
                            tint = DangerRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: [Work Type] Time Range (left) + Date (right, pinned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$workTypeStr]  $timeRangeStr",
                    color = TextDim,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = dateStr,
                    color = TextDim,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private fun filterSessionsByRange(sessions: List<StudySessionEntity>, range: String): List<StudySessionEntity> {
    val now = System.currentTimeMillis()
    val today = FocusRepository.getTodayString()
    return when (range.lowercase(Locale.US)) {
        "today" -> sessions.filter { it.date == today }
        "last 24 hours", "last 24h", "24h" -> sessions.filter { now - it.timestamp <= 24L * 3600 * 1000 }
        "last 48 hours", "last 48h", "48h" -> sessions.filter { now - it.timestamp <= 48L * 3600 * 1000 }
        "last 7 days", "7 days" -> sessions.filter { now - it.timestamp <= 7L * 86400 * 1000 }
        "last 30 days", "30 days" -> sessions.filter { now - it.timestamp <= 30L * 86400 * 1000 }
        else -> sessions
    }
}

private fun generateReportText(sessions: List<StudySessionEntity>, range: String): String {
    val totalMins = sessions.sumOf { it.minutes }
    val hours = totalMins / 60
    val remMins = totalMins % 60
    val bySubject = sessions.groupBy { it.subject }.mapValues { it.value.sumOf { s -> s.minutes } }

    val sb = StringBuilder()
    sb.appendLine("Focus Study Timer - Study Report ($range)")
    sb.appendLine("Total Focus Time: $hours hrs $remMins mins ($totalMins minutes)")
    sb.appendLine("Total Sessions: ${sessions.size}")
    sb.appendLine("\n--- Breakdown by Subject ---")
    bySubject.entries.sortedByDescending { it.value }.forEach { (subj, m) ->
        sb.appendLine("- $subj: $m mins")
    }
    sb.appendLine("\nKeep studying with consistency!")
    return sb.toString()
}

@Composable
private fun DateSelectorCard(
    label: String,
    dateStr: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = PanelElevated,
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                color = TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = com.example.util.DateFormatterCache.formatReadableDate(dateStr),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun dateStrToUtcMillis(dateStr: String): Long? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        sdf.parse(dateStr)?.time
    } catch (_: Exception) {
        null
    }
}

private fun utcMillisToDateStr(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(millis))
}

private fun shiftIsoDate(baseDateStr: String, deltaDays: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val parsed = sdf.parse(baseDateStr)
        if (parsed != null) cal.time = parsed
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        sdf.format(cal.time)
    } catch (_: Exception) {
        baseDateStr
    }
}

private fun extractHourMinute(timestamp: Long): Pair<Int, Int> {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
}

private fun buildLocalTimestamp(dateStr: String, hourOfDay: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    val parsed = com.example.util.DateFormatterCache.parseIsoDate(dateStr)
    if (parsed != null) cal.time = parsed
    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatMinutesOfDay(hourOfDay: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
    }
    return com.example.util.DateFormatterCache.formatTime(cal.timeInMillis)
}

private fun findOverlappingSession(
    allSessions: List<StudySessionEntity>,
    dateStr: String,
    startTs: Long,
    endTs: Long,
    excludeId: Long?
): StudySessionEntity? {
    return allSessions.firstOrNull { other ->
        if (excludeId != null && other.id == excludeId) return@firstOrNull false
        if (other.date != dateStr) return@firstOrNull false
        val otherEnd = other.timestamp
        val otherStart = otherEnd - (other.minutes.toLong() * 60_000L)
        otherStart < endTs && otherEnd > startTs
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SessionFormDialog(
    existingSession: StudySessionEntity?,
    allSessions: List<StudySessionEntity>,
    subjects: List<SubjectEntity>,
    workTypes: List<WorkTypeEntity>,
    onDismiss: () -> Unit,
    onShowToast: (String) -> Unit,
    onSave: (
        date: String,
        subject: String,
        subSubject: String?,
        workType: String,
        minutes: Int,
        endTimestamp: Long,
        isNonStudy: Boolean
    ) -> Unit
) {
    val isEdit = existingSession != null
    val todayStr = remember { FocusRepository.getTodayString() }
    val yesterdayStr = remember(todayStr) { shiftIsoDate(todayStr, -1) }

    val initialDate = existingSession?.date ?: todayStr
    val defaultEndTs = existingSession?.timestamp ?: System.currentTimeMillis()
    val defaultStartTs = if (existingSession != null) {
        defaultEndTs - (existingSession.minutes.toLong() * 60_000L)
    } else {
        defaultEndTs - (30L * 60_000L)
    }
    val (initStartH, initStartM) = remember(existingSession) { extractHourMinute(defaultStartTs) }
    val (initEndH, initEndM) = remember(existingSession) { extractHourMinute(defaultEndTs) }

    var selectedDate by remember(existingSession) { mutableStateOf(initialDate) }
    var subject by remember(existingSession) { mutableStateOf(existingSession?.subject ?: (subjects.firstOrNull { !it.isNonStudy }?.name ?: "")) }
    var subSubject by remember(existingSession) { mutableStateOf(existingSession?.subSubject ?: "") }
    var workType by remember(existingSession) { mutableStateOf(existingSession?.workType ?: (workTypes.firstOrNull()?.name ?: "Revision")) }
    var isNonStudy by remember(existingSession) { mutableStateOf(existingSession?.isNonStudy ?: false) }

    var startHour by remember(existingSession) { mutableStateOf(initStartH) }
    var startMinute by remember(existingSession) { mutableStateOf(initStartM) }
    var endHour by remember(existingSession) { mutableStateOf(initEndH) }
    var endMinute by remember(existingSession) { mutableStateOf(initEndM) }
    var pickingTimeFor by remember { mutableStateOf<String?>(null) } // "start" or "end"

    val startTotalMins = startHour * 60 + startMinute
    val endTotalMins = endHour * 60 + endMinute
    val computedMinutes = endTotalMins - startTotalMins
    val startTs = remember(selectedDate, startHour, startMinute) {
        buildLocalTimestamp(selectedDate, startHour, startMinute)
    }
    val endTs = remember(selectedDate, endHour, endMinute) {
        buildLocalTimestamp(selectedDate, endHour, endMinute)
    }

    val overlap = remember(allSessions, selectedDate, startTs, endTs, existingSession?.id, computedMinutes) {
        if (computedMinutes in 1..1440) {
            findOverlappingSession(allSessions, selectedDate, startTs, endTs, existingSession?.id)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Session" else "Add Missed Session",
                color = GoldBright
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEdit) {
                    // Quick date toggle for missed sessions (Today / Yesterday)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Today" to todayStr, "Yesterday" to yesterdayStr).forEach { (label, dateVal) ->
                            val isSel = selectedDate == dateVal
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                                border = BorderStroke(1.dp, if (isSel) GoldAccent else LineBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDate = dateVal }
                            ) {
                                Text(
                                    text = "$label (${com.example.util.DateFormatterCache.formatReadableDate(dateVal)})",
                                    color = if (isSel) GoldBright else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Quick missed-duration presets ending now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Last 30m" to 30, "Last 1h" to 60, "Last 2h" to 120).forEach { (label, mins) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PanelElevated,
                                border = BorderStroke(1.dp, LineBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val now = System.currentTimeMillis()
                                        val (eh, em) = extractHourMinute(now)
                                        val (sh, sm) = extractHourMinute(now - mins * 60_000L)
                                        endHour = eh
                                        endMinute = em
                                        startHour = sh
                                        startMinute = sm
                                        selectedDate = todayStr
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Start Time -> End Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PanelElevated,
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { pickingTimeFor = "start" }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Start Time", color = TextDim, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = formatMinutesOfDay(startHour, startMinute),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PanelElevated,
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { pickingTimeFor = "end" }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("End Time", color = TextDim, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = formatMinutesOfDay(endHour, endMinute),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Live calculated duration badge
                val durationValid = computedMinutes in 1..1440
                val durHours = computedMinutes / 60
                val durRemMins = computedMinutes % 60
                val durText = if (durationValid) {
                    if (durHours > 0) "Duration: ${durHours}h ${durRemMins}m ($computedMinutes min)"
                    else "Duration: $computedMinutes min"
                } else {
                    "End time must be after Start time"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (durationValid) GoldAccent.copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (durationValid) GoldAccent.copy(alpha = 0.45f) else DangerRed.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = durText,
                        color = if (durationValid) GoldBright else DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                    )
                }

                // Time Overlap Warning
                if (overlap != null) {
                    val oEnd = overlap.timestamp
                    val oStart = oEnd - (overlap.minutes.toLong() * 60_000L)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x33F59E0B),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Overlaps with ${overlap.subject} (${com.example.util.DateFormatterCache.formatTime(oStart)} - ${com.example.util.DateFormatterCache.formatTime(oEnd)})",
                            color = Color(0xFFFCD34D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Bengali, Math, SLEEP)", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Quick subject chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { subj ->
                        val isSel = subject.equals(subj.name, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                            border = BorderStroke(1.dp, if (isSel) GoldAccent else LineBorder),
                            modifier = Modifier.clickable {
                                subject = subj.name
                                isNonStudy = subj.isNonStudy
                            }
                        ) {
                            Text(
                                text = subj.name + if (subj.isNonStudy) " (NS)" else "",
                                color = if (isSel) GoldBright else TextDim,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = subSubject,
                    onValueChange = { subSubject = it },
                    label = { Text("Sub-Subject (Optional)", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = workType,
                    onValueChange = { workType = it },
                    label = { Text("Work Type (e.g. Revision, Memorize, N/A)", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Quick work type chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    workTypes.forEach { wt ->
                        val isSel = workType.equals(wt.name, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                            border = BorderStroke(1.dp, if (isSel) GoldAccent else LineBorder),
                            modifier = Modifier.clickable { workType = wt.name }
                        ) {
                            Text(
                                text = wt.name,
                                color = if (isSel) GoldBright else TextDim,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Non-Study Category Checkbox
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PanelElevated,
                    border = BorderStroke(1.dp, if (isNonStudy) GoldAccent else LineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNonStudy = !isNonStudy }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Non-Study Session",
                                color = if (isNonStudy) GoldBright else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Excluded from daily study goal & deep focus stats",
                                color = TextDim,
                                fontSize = 11.sp
                            )
                        }
                        Checkbox(
                            checked = isNonStudy,
                            onCheckedChange = { isNonStudy = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isBlank()) {
                        onShowToast("Subject name cannot be empty")
                        return@Button
                    }
                    if (computedMinutes !in 1..1440) {
                        onShowToast("End time must be after Start time")
                        return@Button
                    }
                    onSave(
                        selectedDate,
                        subject.trim(),
                        if (subSubject.isNotBlank()) subSubject.trim() else null,
                        workType.trim().ifBlank { "Other" },
                        computedMinutes,
                        endTs,
                        isNonStudy
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
            ) {
                Text(if (isEdit) "Save & Sync" else "Add & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark
    )

    if (pickingTimeFor != null) {
        val isStart = pickingTimeFor == "start"
        val pickerState = rememberTimePickerState(
            initialHour = if (isStart) startHour else endHour,
            initialMinute = if (isStart) startMinute else endMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { pickingTimeFor = null },
            title = {
                Text(
                    text = if (isStart) "Select Start Time" else "Select End Time",
                    color = GoldBright
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = PanelElevated,
                            selectorColor = GoldAccent,
                            containerColor = PanelDark,
                            periodSelectorBorderColor = LineBorder,
                            periodSelectorSelectedContainerColor = GoldAccent.copy(alpha = 0.25f),
                            periodSelectorSelectedContentColor = GoldBright,
                            periodSelectorUnselectedContentColor = TextDim,
                            timeSelectorSelectedContainerColor = GoldAccent.copy(alpha = 0.25f),
                            timeSelectorUnselectedContainerColor = PanelElevated,
                            timeSelectorSelectedContentColor = GoldBright,
                            timeSelectorUnselectedContentColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isStart) {
                            startHour = pickerState.hour
                            startMinute = pickerState.minute
                        } else {
                            endHour = pickerState.hour
                            endMinute = pickerState.minute
                        }
                        pickingTimeFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Set Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingTimeFor = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }
}

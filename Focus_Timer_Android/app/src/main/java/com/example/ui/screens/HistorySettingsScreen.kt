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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmToneOption
import com.example.data.model.StudySessionEntity
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import com.example.data.repository.FocusRepository
import com.example.util.PdfExportHelper
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistorySettingsScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allSessions by viewModel.allSessions.collectAsState()
    val subjects by viewModel.allSubjects.collectAsState()
    val workTypes by viewModel.allWorkTypes.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: History, 1: Backup & Export, 2: Subjects & Work
    var sessionToDelete by remember { mutableStateOf<StudySessionEntity?>(null) }
    var sessionToEdit by remember { mutableStateOf<StudySessionEntity?>(null) }
    var subjectToEdit by remember { mutableStateOf<SubjectEntity?>(null) }
    var workTypeToEdit by remember { mutableStateOf<WorkTypeEntity?>(null) }
    var recentlyDeletedSession by remember { mutableStateOf<StudySessionEntity?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Backup & Export & Alarm state
    var exportRange by remember { mutableStateOf("Last 30 Days") }
    var rangeDropdownExpanded by remember { mutableStateOf(false) }
    var ringtoneDropdownExpanded by remember { mutableStateOf(false) }
    var jsonImportText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    val alarmToneId by viewModel.alarmToneId.collectAsState()
    val alarmVolumeBoost by viewModel.alarmVolumeBoost.collectAsState()
    val isPreviewingSound by viewModel.isPreviewingSound.collectAsState()
    val alarmCustomUri by viewModel.alarmCustomUri.collectAsState()

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
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val content = stream.bufferedReader().use { r -> r.readText() }
                        val result = viewModel.repository.importFromJson(content)
                        viewModel.reloadSettingsAndExamGoal()
                        viewModel.showToast("Restore successful: ${result.studyCount} study, ${result.nonStudyCount} non-study added!")
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Import error: Invalid JSON file")
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
        Text(
            text = "History & Management",
            style = TextStyle(
                brush = GoldGradientBrush,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                
            ),
            modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
        )

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PanelDark,
            contentColor = GoldBright,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldAccent
                )
            },
            divider = {}
        ) {
            listOf("Sessions", "Backup & Sync", "Subjects & Types").forEachIndexed { idx, title ->
                val isSelected = selectedTab == idx
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = title,
                            color = if (isSelected) GoldBright else TextDim,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Undo banner if a session was just deleted
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
                            Text("UNDO ↩️", color = GoldBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { recentlyDeletedSession = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // SESSION HISTORY LIST
                if (allSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "No study sessions recorded yet.\nStart a timer to record your focus time!",
                            color = TextDim,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

            1 -> {
                // BACKUP, EXPORT & BULK DELETE
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
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Text(
                                        "Sign In with Google 🔑",
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
                                        onClick = { viewModel.signOut() },
                                        border = BorderStroke(1.dp, DangerRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Sign Out", color = DangerRed)
                                    }
                                }
                            }
                        }
                    }

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

                            // PDF Button (matches Web PDF Export)
                            OutlinedButton(
                                onClick = {
                                    val filtered = filterSessionsByRange(allSessions, exportRange)
                                    if (filtered.isEmpty()) {
                                        viewModel.showToast("No study sessions found in selected range")
                                    } else {
                                        try {
                                            PdfExportHelper.generateAndSharePdf(context, filtered, exportRange)
                                        } catch (e: Exception) {
                                            viewModel.showToast("Failed to generate PDF: ${e.localizedMessage}")
                                        }
                                    }
                                },
                                border = BorderStroke(1.dp, LineBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "PDF Export",
                                    color = GoldBright,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Two action buttons: Download JSON (Backup) & Restore JSON (Upload)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                                    context.startActivity(Intent.createChooser(sendIntent, "Download JSON (Backup)"))
                                                }
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    viewModel.showToast("Failed to create backup: ${e.localizedMessage}")
                                                }
                                            }
                                        }
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
                                        Text("(Backup)", fontSize = 10.sp, color = TextDim)
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
                                        Text("Restore JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("(Upload)", fontSize = 10.sp, color = TextDim)
                                    }
                                }
                            }
                        }
                    }

                    // ALARM SOUND & VOLUME SETTINGS
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
                                            Text(text = currentOption.icon, fontSize = 16.sp)
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
                                                    Text(opt.icon, fontSize = 16.sp)
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

                    // Bulk Delete Card
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
                                text = "Permanently remove sessions within the selected start and end dates.",
                                color = TextDim,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = fromDateStr,
                                    onValueChange = { fromDateStr = it },
                                    label = { Text("From (YYYY-MM-DD)", color = TextDim, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = LineBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = toDateStr,
                                    onValueChange = { toDateStr = it },
                                    label = { Text("To (YYYY-MM-DD)", color = TextDim, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = LineBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
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

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            2 -> {
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
                            Text(
                                text = "Managed Subjects",
                                style = TextStyle(
                                    brush = GoldGradientBrush,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            subjects.forEach { subj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
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
                                                    text = "CORE",
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
                                                text = "Subs: " + subj.subSubjects.joinToString(", "),
                                                color = TextDim,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { subjectToEdit = subj },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Subject",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (!subj.isCore) {
                                            IconButton(
                                                onClick = { viewModel.deleteSubject(subj.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Subject",
                                                    tint = DangerRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 4.dp))
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
                            Text(
                                text = "Managed Work Types",
                                style = TextStyle(
                                    brush = GoldGradientBrush,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            workTypes.forEach { wt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = wt.name, color = TextPrimary, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { workTypeToEdit = wt },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Work Type",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteWorkType(wt.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Work Type",
                                                tint = DangerRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // Delete single session confirmation dialog
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

    // Edit session dialog
    if (sessionToEdit != null) {
        val s = sessionToEdit!!
        var editSubject by remember(s) { mutableStateOf(s.subject) }
        var editSubSubject by remember(s) { mutableStateOf(s.subSubject ?: "") }
        var editWorkType by remember(s) { mutableStateOf(s.workType) }
        var editMinutes by remember(s) { mutableStateOf(s.minutes.toString()) }
        var editDate by remember(s) { mutableStateOf(s.date) }
        var editIsNonStudy by remember(s) { mutableStateOf(s.isNonStudy) }

        AlertDialog(
            onDismissRequest = { sessionToEdit = null },
            title = { Text("Edit Session", color = GoldBright) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
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
                            val isSel = editSubject.equals(subj.name, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                                border = BorderStroke(1.dp, if (isSel) GoldAccent else LineBorder),
                                modifier = Modifier.clickable {
                                    editSubject = subj.name
                                    editIsNonStudy = subj.isNonStudy
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
                        value = editSubSubject,
                        onValueChange = { editSubSubject = it },
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
                        value = editWorkType,
                        onValueChange = { editWorkType = it },
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
                            val isSel = editWorkType.equals(wt.name, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) GoldAccent.copy(alpha = 0.2f) else PanelElevated,
                                border = BorderStroke(1.dp, if (isSel) GoldAccent else LineBorder),
                                modifier = Modifier.clickable { editWorkType = wt.name }
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

                    OutlinedTextField(
                        value = editMinutes,
                        onValueChange = { editMinutes = it },
                        label = { Text("Duration (Minutes)", color = TextDim) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Non-Study Category Checkbox
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PanelElevated,
                        border = BorderStroke(1.dp, if (editIsNonStudy) GoldAccent else LineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editIsNonStudy = !editIsNonStudy }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Non-Study Session",
                                    color = if (editIsNonStudy) GoldBright else TextPrimary,
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
                                checked = editIsNonStudy,
                                onCheckedChange = { editIsNonStudy = it },
                                colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = editMinutes.toIntOrNull() ?: s.minutes
                        if (editSubject.isNotBlank() && mins > 0) {
                            val updated = s.copy(
                                subject = editSubject.trim(),
                                subSubject = if (editSubSubject.isNotBlank()) editSubSubject.trim() else null,
                                workType = editWorkType.trim(),
                                minutes = mins,
                                date = editDate.trim(),
                                isNonStudy = editIsNonStudy
                            )
                            viewModel.updateSession(updated, previousIsNonStudy = s.isNonStudy)
                            sessionToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToEdit = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Edit Subject Dialog
    if (subjectToEdit != null) {
        val subj = subjectToEdit!!
        var editName by remember(subj) { mutableStateOf(subj.name) }
        var editIsCore by remember(subj) { mutableStateOf(subj.isCore) }
        var editIsNonStudy by remember(subj) { mutableStateOf(subj.isNonStudy) }
        var subList by remember(subj) { mutableStateOf(subj.subSubjects) }
        var newSubInput by remember(subj) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { subjectToEdit = null },
            title = { Text("Edit Subject", color = GoldBright) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Subject Name", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Core Subject toggle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PanelElevated,
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editIsCore = !editIsCore }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Core Subject", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Pinned permanently to the main subject bar", color = TextDim, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = editIsCore,
                                onCheckedChange = { editIsCore = it },
                                colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                            )
                        }
                    }

                    // Non-Study toggle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PanelElevated,
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editIsNonStudy = !editIsNonStudy }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Non-Study Subject", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Tags all new sessions as non-study/personal", color = TextDim, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = editIsNonStudy,
                                onCheckedChange = { editIsNonStudy = it },
                                colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                            )
                        }
                    }

                    Text("Sub-Subjects", color = GoldLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    if (subList.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subList.forEach { sub ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PanelElevated,
                                    border = BorderStroke(1.dp, LineBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Text(sub, color = TextPrimary, fontSize = 12.sp)
                                        IconButton(
                                            onClick = { subList = subList.filter { it != sub } },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Text("✕", color = DangerRed, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubInput,
                            onValueChange = { newSubInput = it },
                            label = { Text("Add Sub-Subject", color = TextDim) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = LineBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSubInput.isNotBlank() && !subList.contains(newSubInput.trim())) {
                                    subList = subList + newSubInput.trim()
                                    newSubInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                        ) {
                            Text("+")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            val updatedSubj = subj.copy(
                                name = editName.trim(),
                                isCore = editIsCore,
                                isNonStudy = editIsNonStudy,
                                subSubjects = subList
                            )
                            viewModel.updateSubject(updatedSubj)
                            subjectToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToEdit = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Edit Work Type Dialog
    if (workTypeToEdit != null) {
        val wt = workTypeToEdit!!
        var editName by remember(wt) { mutableStateOf(wt.name) }

        AlertDialog(
            onDismissRequest = { workTypeToEdit = null },
            title = { Text("Edit Work Type", color = GoldBright) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Work Type Name", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateWorkType(wt.id, editName.trim())
                            workTypeToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { workTypeToEdit = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Bulk delete confirmation dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Confirm Bulk Delete", color = DangerRed) },
            text = { Text("Permanently delete all sessions between $fromDateStr and $toDateStr? This cannot be undone.", color = TextPrimary) },
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
                    Text("Select a backup file from your phone or paste valid JSON string below:", color = TextDim, fontSize = 12.sp)
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
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val result = viewModel.repository.importFromJson(jsonImportText)
                                viewModel.reloadSettingsAndExamGoal()
                                viewModel.showToast("Restore successful: ${result.studyCount} study, ${result.nonStudyCount} non-study added!")
                                showImportDialog = false
                                jsonImportText = ""
                            } catch (e: Exception) {
                                viewModel.showToast("Import error: Invalid JSON")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Restore")
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
    val dateStr = remember(endTs) {
        com.example.util.DateFormatterCache.formatReadableDate(session.date)
    }
    val workTypeStr = if (session.workType.isBlank()) "N/A" else session.workType
    val subtitleText = "[$workTypeStr] $timeRangeStr, $dateStr"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.subject,
                    color = GoldBright,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (session.isNonStudy) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF2A2825),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF4D483F))
                    ) {
                        Text(
                            text = "Non-Study",
                            color = Color(0xFFC0B8AA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitleText,
                    color = TextDim,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.minutes} min",
                    color = GoldLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Session",
                        tint = GoldAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Session",
                        tint = TextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
    sb.appendLine("📊 Focus Study Timer - Study Report ($range)")
    sb.appendLine("Total Focus Time: $hours hrs $remMins mins ($totalMins minutes)")
    sb.appendLine("Total Sessions: ${sessions.size}")
    sb.appendLine("\n--- Breakdown by Subject ---")
    bySubject.entries.sortedByDescending { it.value }.forEach { (subj, m) ->
        sb.appendLine("• $subj: $m mins")
    }
    sb.appendLine("\nKeep studying with consistency! ⏱️")
    return sb.toString()
}

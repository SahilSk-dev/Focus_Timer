package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.data.model.StudySessionEntity
import com.example.data.repository.FocusRepository
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
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Backup & Export state
    var exportRange by remember { mutableStateOf("All Time") }
    var jsonImportText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

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
                fontFamily = FontFamily.Serif
            ),
            modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
        )

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PanelDark,
            contentColor = GoldBright,
            indicator = {},
            divider = {}
        ) {
            val tabs = listOf("History", "Backup & Export", "Subjects")
            tabs.forEachIndexed { idx, title ->
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
                            fontFamily = FontFamily.Serif
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                        items(allSessions, key = { it.id }) { session ->
                            SessionHistoryItem(
                                session = session,
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
                                            fontFamily = FontFamily.Serif
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
                                    text = "Bi-directional real-time sync with your Laptop and Firestore.",
                                    color = TextDim,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

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

                                if (currentUser != null) {
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Export Report",
                                style = TextStyle(
                                    brush = GoldGradientBrush,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select range and share a summarized report of your study hours.",
                                color = TextDim,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Range selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val ranges = listOf("Today", "7 Days", "30 Days", "All Time")
                                ranges.forEach { r ->
                                    val sel = exportRange == r
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (sel) GoldAccent else PanelElevated)
                                            .clickable { exportRange = r }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = r,
                                            color = if (sel) BgDark else TextDim,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val filtered = filterSessionsByRange(allSessions, exportRange)
                                    val reportText = generateReportText(filtered, exportRange)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Study Report"))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldAccent,
                                    contentColor = BgDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share Report (${filterSessionsByRange(allSessions, exportRange).size} sessions)")
                            }
                        }
                    }

                    // JSON Backup Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PanelDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "JSON Backup & Restore",
                                style = TextStyle(
                                    brush = GoldGradientBrush,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Save your complete session history or restore it on any device.",
                                color = TextDim,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val jsonStr = viewModel.repository.exportToJson(allSessions)
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, jsonStr)
                                            type = "application/json"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Export JSON Backup"))
                                    },
                                    border = BorderStroke(1.dp, GoldAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export JSON", color = GoldLight)
                                }

                                OutlinedButton(
                                    onClick = { showImportDialog = true },
                                    border = BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import JSON", color = TextPrimary)
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
                                text = "Bulk Delete by Date Range",
                                color = DangerRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
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
                                onClick = { showBulkDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Sessions in Range")
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
                                    fontFamily = FontFamily.Serif
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
                                    fontFamily = FontFamily.Serif
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
            title = { Text("Import JSON Backup", color = GoldBright) },
            text = {
                Column {
                    Text("Paste valid JSON backup string below:", color = TextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonImportText,
                        onValueChange = { jsonImportText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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
                                val count = viewModel.repository.importFromJson(jsonImportText)
                                viewModel.showToast("$count sessions restored successfully")
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
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeFormatted = remember(session.timestamp) {
        sdf.format(Date(session.timestamp))
    }

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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(PanelElevated, RoundedCornerShape(4.dp))
                            .border(1.dp, LineBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = session.workType,
                            color = GoldLight,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${session.date} • $timeFormatted",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.minutes} mins",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Session",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun filterSessionsByRange(sessions: List<StudySessionEntity>, range: String): List<StudySessionEntity> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = FocusRepository.getTodayString()
    return when (range) {
        "Today" -> sessions.filter { it.date == today }
        "7 Days" -> {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -7)
            val dStr = sdf.format(c.time)
            sessions.filter { it.date >= dStr }
        }
        "30 Days" -> {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -30)
            val dStr = sdf.format(c.time)
            sessions.filter { it.date >= dStr }
        }
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

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ActiveTheme
import com.example.ui.theme.AppTheme
import com.example.ui.theme.BgDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldGradientBrush
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LineBorder
import com.example.ui.theme.LineBright
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.ThemeBright
import com.example.ui.theme.ThemeDark
import com.example.ui.theme.ThemeGradientBrush
import com.example.ui.theme.ThemeLight
import com.example.ui.theme.ThemePrimary
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    viewModel: FocusViewModel,
    onOpenAddSubject: () -> Unit,
    onOpenAddSubSubject: (String) -> Unit,
    onOpenAddWorkType: () -> Unit,
    onEditSubject: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTimerActive by viewModel.isTimerActive.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val stopwatchMode by viewModel.stopwatchMode.collectAsState()
    val pomodoroMode by viewModel.pomodoroMode.collectAsState()
    val pomoPhase by viewModel.pomoPhase.collectAsState()
    val inputHours by viewModel.inputHours.collectAsState()
    val inputMinutes by viewModel.inputMinutes.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()
    val selectedSubSubject by viewModel.selectedSubSubject.collectAsState()
    val selectedWorkType by viewModel.selectedWorkType.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isDialVisible by viewModel.isDialVisible.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val showResetConfirmDialog by viewModel.showResetConfirmDialog.collectAsState()
    val pendingResetMinutes by viewModel.pendingResetMinutes.collectAsState()

    val subjects by viewModel.allSubjects.collectAsState()
    val workTypes by viewModel.allWorkTypes.collectAsState()

    if (isFullscreen) {
        FullscreenFocusLayout(
            viewModel = viewModel,
            selectedSubject = selectedSubject,
            selectedSubSubject = selectedSubSubject,
            selectedWorkType = selectedWorkType,
            isRunning = isRunning,
            isTimerActive = isTimerActive,
            pomoPhase = if (pomodoroMode) pomoPhase else null,
            onStart = { viewModel.startTimer() },
            onPause = { viewModel.pauseTimer() },
            onReset = { viewModel.resetTimer() },
            onExitFullscreen = { viewModel.toggleFullscreen() }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val currentTheme by viewModel.currentAppTheme.collectAsState()

        // App Header with single DARK / WHITE Mode Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ThemeGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentTheme.isLight) "☀️" else "🌙",
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Focus Timer",
                        style = TextStyle(
                            brush = ThemeGradientBrush,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (currentTheme.isLight) "White Mode" else "Dark Mode",
                        color = ThemeLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Single Toggle Button: DARK MODE / WHITE MODE
            Surface(
                onClick = { viewModel.toggleTheme() },
                shape = RoundedCornerShape(14.dp),
                color = PanelElevated,
                border = BorderStroke(1.dp, LineBorder),
                modifier = Modifier.testTag("dark_white_toggle_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (currentTheme.isLight) "🌙 Dark" else "☀️ White",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Hero Timer Stage (Clock + Phase Badge + Start/Pause/Reset above the fold!)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Fullscreen Button & Active Subject Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedSubject.isNotBlank()) {
                        val subText = if (!selectedSubSubject.isNullOrBlank()) "$selectedSubject - $selectedSubSubject" else selectedSubject
                        Text(
                            text = "🎯 $subText",
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "⚠️ Select a subject below",
                            color = TextDim,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFullscreen() },
                        modifier = Modifier.size(44.dp).testTag("fullscreen_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = GoldLight
                        )
                    }
                }

                // Status hint text
                Text(
                    text = when {
                        selectedSubject.isBlank() -> "Select a subject to begin"
                        !isTimerActive && !isRunning -> "Press Start to begin studying"
                        isRunning && stopwatchMode -> "Stopwatch running..."
                        isRunning && pomodoroMode -> if (pomoPhase == "work") "Focusing (25m)..." else "Break (5m)..."
                        isRunning -> "Countdown running..."
                        else -> "Paused - press Start to resume"
                    },
                    color = if (selectedSubject.isBlank() && !isTimerActive) GoldAccent else GoldLight,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Big Golden Display (Isolated recomposition)
                IsolatedTimerDisplay(viewModel = viewModel)

                // Pomodoro Phase Badge
                if (pomodoroMode && isTimerActive) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (pomoPhase == "work") Color(0x33C9962F) else Color(0x338BA888),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (pomoPhase == "work") GoldAccent else Color(0xFF8BA888),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (pomoPhase == "work") "⏳ Focus Session (25 mins)" else "☕ Break (5 mins)",
                            color = if (pomoPhase == "work") GoldBright else Color(0xFF8BA888),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action Buttons (Start, Pause, Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.startTimer() },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme.isLight) Color(0xFF0F172A) else Color(0xFFFFFFFF),
                            contentColor = if (currentTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF000000),
                            disabledContainerColor = LineBorder,
                            disabledContentColor = TextDim
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_btn")
                    ) {
                        Text("Start", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.pauseTimer() },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme.isLight) Color(0xFFF1F5F9) else PanelElevated,
                            contentColor = if (currentTheme.isLight) Color(0xFF0F172A) else TextPrimary,
                            disabledContainerColor = if (currentTheme.isLight) Color(0xFFF8FAFC) else PanelDark,
                            disabledContentColor = TextDim
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_btn")
                    ) {
                        Text("Pause", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { viewModel.resetTimer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme.isLight) Color(0xFFF1F5F9) else PanelElevated,
                            contentColor = if (currentTheme.isLight) Color(0xFF0F172A) else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reset_btn")
                    ) {
                        Text("Reset", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Subject Selection Area
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subject",
                    color = GoldLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleEditMode() },
                        modifier = Modifier.size(48.dp).testTag("edit_mode_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Done editing subjects" else "Edit subjects",
                            tint = if (isEditMode) GoldBright else TextDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isEditMode) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✏️ Edit Mode: Tap any subject to edit/rename, or ＋ to add.",
                            color = GoldBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                subjects.forEach { subj ->
                    val isSelected = subj.name == selectedSubject
                    SubjectChip(
                        name = subj.name,
                        isCore = subj.isCore,
                        isSelected = isSelected,
                        isEditMode = isEditMode,
                        isTimerActive = isTimerActive,
                        onClick = { viewModel.selectSubject(subj) },
                        onEdit = { onEditSubject(subj.name, subj.id) }
                    )
                }

                if (!isTimerActive && isEditMode) {
                    AddChip(
                        label = "＋ Subject",
                        onClick = onOpenAddSubject
                    )
                }
            }

            // Sub-subject area
            val currentSubjObj = subjects.find { it.name == selectedSubject }
            val subList = currentSubjObj?.subSubjects ?: emptyList()
            if (subList.isNotEmpty() || (!isTimerActive && isEditMode)) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                ) {
                    subList.forEach { sub ->
                        val isSubSelected = sub == selectedSubSubject
                        SubChip(
                            name = sub,
                            isSelected = isSubSelected,
                            isTimerActive = isTimerActive,
                            onClick = { viewModel.selectSubSubject(sub) }
                        )
                    }
                    if (!isTimerActive && isEditMode && currentSubjObj != null) {
                        AddChip(
                            label = "＋ Sub-Subject",
                            onClick = { onOpenAddSubSubject(currentSubjObj.name) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Work Type Selection Area
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Work Type",
                color = GoldLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                workTypes.forEach { wt ->
                    val isSelected = wt.name == selectedWorkType
                    WorkTypeChip(
                        name = wt.name,
                        isSelected = isSelected,
                        isTimerActive = isTimerActive,
                        onClick = { viewModel.selectWorkType(wt.name) }
                    )
                }
                if (!isTimerActive && isEditMode) {
                    AddChip(
                        label = "＋ Work Type",
                        onClick = onOpenAddWorkType
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pomodoro Row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pomodoro Mode (25+5 mins)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.size(48.dp).testTag("mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            contentDescription = if (isMuted) "Alarm sound is muted. Tap to unmute" else "Alarm sound is active. Tap to mute",
                            tint = if (isMuted) TextDim else GoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = pomodoroMode,
                        onCheckedChange = { viewModel.togglePomodoro() },
                        enabled = !isTimerActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GoldBright,
                            checkedTrackColor = GoldDark,
                            uncheckedThumbColor = TextDim,
                            uncheckedTrackColor = LineBorder
                        ),
                        modifier = Modifier.testTag("pomodoro_switch")
                    )
                }
            }
        }

        // Custom Time Row (Hidden in Pomodoro Mode)
        AnimatedVisibility(visible = !pomodoroMode) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = if (inputHours == 0) "" else inputHours.toString(),
                        onValueChange = { str ->
                            viewModel.setInputHours(str.toIntOrNull() ?: 0)
                        },
                        label = { Text("Hours", color = GoldLight) },
                        enabled = !isTimerActive,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("hours_input")
                    )
                    OutlinedTextField(
                        value = if (inputMinutes == 0) "" else inputMinutes.toString(),
                        onValueChange = { str ->
                            viewModel.setInputMinutes(str.toIntOrNull() ?: 0)
                        },
                        label = { Text("Minutes", color = GoldLight) },
                        enabled = !isTimerActive,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("minutes_input")
                    )
                }
                Text(
                    text = "Leave time empty (0) to use as a stopwatch starting from 0",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Reset Confirmation Dialog (When user spent >= 1m)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelResetDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏱️", fontSize = 24.sp)
                    Text(
                        text = "Reset Timer?",
                        color = GoldBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You studied for $pendingResetMinutes minute${if (pendingResetMinutes == 1) "" else "s"}. Would you like to finish and save this session?",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Finish & Save: Records $pendingResetMinutes min into history & resets timer.\n• Discard: Resets timer without saving this session.",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmResetAndSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Finish & Save (${pendingResetMinutes}m)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { viewModel.confirmResetAndDiscard() }) {
                        Text("Discard", color = DangerRed)
                    }
                    TextButton(onClick = { viewModel.cancelResetDialog() }) {
                        Text("Cancel", color = TextDim)
                    }
                }
            },
            containerColor = PanelDark
        )
    }
}

@Composable
private fun SubjectChip(
    name: String,
    isCore: Boolean,
    isSelected: Boolean,
    isEditMode: Boolean,
    isTimerActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val borderColor = when {
        isEditMode -> GoldAccent
        isSelected -> GoldBright
        isCore -> GoldAccent.copy(alpha = 0.8f)
        else -> LineBorder
    }

    val backgroundColor = when {
        isSelected -> GoldAccent.copy(alpha = 0.22f)
        isCore -> PanelElevated
        else -> PanelDark
    }

    val textColor = when {
        isSelected -> if (ActiveTheme.isLight) ActiveTheme.primary else GoldBright
        isCore -> GoldLight
        else -> TextDim
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected || isEditMode) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .semantics {
                this.role = Role.RadioButton
                this.selected = isSelected
            }
            .clickable {
                if (isEditMode) onEdit() else onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isEditMode) {
                Text(text = "✏️", fontSize = 11.sp)
            }
            Text(
                text = name,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isCore) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SubChip(
    name: String,
    isSelected: Boolean,
    isTimerActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) GoldAccent.copy(alpha = 0.22f) else PanelDark)
            .border(
                width = 1.dp,
                color = if (isSelected) GoldBright else LineBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .semantics {
                this.role = Role.RadioButton
                this.selected = isSelected
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "↳ $name",
            color = if (isSelected) (if (ActiveTheme.isLight) ActiveTheme.primary else GoldBright) else TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WorkTypeChip(
    name: String,
    isSelected: Boolean,
    isTimerActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) GoldAccent.copy(alpha = 0.22f) else PanelDark)
            .border(
                width = 1.dp,
                color = if (isSelected) GoldBright else LineBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .semantics {
                this.role = Role.RadioButton
                this.selected = isSelected
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) (if (ActiveTheme.isLight) ActiveTheme.primary else GoldBright) else TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AddChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LineBright, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = GoldLight,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FullscreenFocusLayout(
    viewModel: FocusViewModel,
    selectedSubject: String,
    selectedSubSubject: String?,
    selectedWorkType: String,
    isRunning: Boolean,
    isTimerActive: Boolean,
    pomoPhase: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Exit Fullscreen",
                tint = GoldLight,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val title = if (!selectedSubSubject.isNullOrBlank()) "$selectedSubject - $selectedSubSubject" else selectedSubject
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    color = GoldBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(PanelElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = selectedWorkType,
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (pomoPhase != null) {
                    Text(
                        text = if (pomoPhase == "work") "⏳ Focus" else "☕ Break",
                        color = GoldLight,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FullscreenTimerDisplay(viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ActiveTheme.isLight) Color(0xFF0F172A) else Color(0xFFFFFFFF),
                        contentColor = if (ActiveTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF000000)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Start", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Button(
                    onClick = onPause,
                    enabled = isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ActiveTheme.isLight) Color(0xFFF1F5F9) else PanelElevated,
                        contentColor = if (ActiveTheme.isLight) Color(0xFF0F172A) else TextPrimary
                    ),
                    border = BorderStroke(1.dp, LineBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Pause", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ActiveTheme.isLight) Color(0xFFF1F5F9) else PanelElevated,
                        contentColor = if (ActiveTheme.isLight) Color(0xFF0F172A) else TextPrimary
                    ),
                    border = BorderStroke(1.dp, LineBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Reset", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun IsolatedTimerDisplay(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val isTimerActive by viewModel.isTimerActive.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val pomodoroMode by viewModel.pomodoroMode.collectAsState()
    val pomoPhase by viewModel.pomoPhase.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val inputHours by viewModel.inputHours.collectAsState()
    val inputMinutes by viewModel.inputMinutes.collectAsState()

    val displayDigits = remember(isTimerActive, isRunning, remainingSeconds, inputHours, inputMinutes, pomodoroMode, pomoPhase) {
        val totalSec = when {
            isTimerActive || isRunning -> remainingSeconds
            pomodoroMode -> if (pomoPhase == "work") 25 * 60L else 5 * 60L
            else -> (inputHours * 3600 + inputMinutes * 60).toLong()
        }
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    Text(
        text = displayDigits,
        style = TextStyle(
            brush = GoldGradientBrush,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
            .padding(vertical = 8.dp)
            .testTag("timer_display")
    )
}

@Composable
fun FullscreenTimerDisplay(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val isTimerActive by viewModel.isTimerActive.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val pomodoroMode by viewModel.pomodoroMode.collectAsState()
    val pomoPhase by viewModel.pomoPhase.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val inputHours by viewModel.inputHours.collectAsState()
    val inputMinutes by viewModel.inputMinutes.collectAsState()

    val displayDigits = remember(isTimerActive, isRunning, remainingSeconds, inputHours, inputMinutes, pomodoroMode, pomoPhase) {
        val totalSec = when {
            isTimerActive || isRunning -> remainingSeconds
            pomodoroMode -> if (pomoPhase == "work") 25 * 60L else 5 * 60L
            else -> (inputHours * 3600 + inputMinutes * 60).toLong()
        }
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    Text(
        text = displayDigits,
        style = TextStyle(
            brush = GoldGradientBrush,
            fontSize = 84.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
    )
}


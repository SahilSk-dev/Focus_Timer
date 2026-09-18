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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.example.ui.components.GoldenClockDial
import com.example.ui.theme.BgDark
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
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val inputHours by viewModel.inputHours.collectAsState()
    val inputMinutes by viewModel.inputMinutes.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()
    val selectedSubSubject by viewModel.selectedSubSubject.collectAsState()
    val selectedWorkType by viewModel.selectedWorkType.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isDialVisible by viewModel.isDialVisible.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()

    val subjects by viewModel.allSubjects.collectAsState()
    val workTypes by viewModel.allWorkTypes.collectAsState()

    // Real-time idle clock string
    var idleTimeString by remember { mutableStateOf("") }
    LaunchedEffect(isTimerActive, isRunning) {
        while (!isTimerActive && !isRunning) {
            val now = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
            idleTimeString = now
            delay(500L)
        }
    }

    val displayDigits = remember(isTimerActive, isRunning, remainingSeconds, idleTimeString) {
        if (isTimerActive || isRunning) {
            val totalSec = remainingSeconds
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            idleTimeString.ifEmpty { "00:00:00" }
        }
    }

    if (isFullscreen) {
        FullscreenFocusLayout(
            displayDigits = displayDigits,
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
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(GoldBright, GoldAccent, GoldDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⏱️",
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Focus Study Timer",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
        }

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
                    fontFamily = FontFamily.Serif
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleEditMode() },
                        modifier = Modifier.size(32.dp).testTag("edit_mode_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Mode",
                            tint = if (isEditMode) GoldBright else TextDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        isTimerActive = isTimerActive,
                        onClick = { viewModel.selectSubject(subj) },
                        onLongClick = {
                            if (isEditMode) onEditSubject(subj.name, subj.id)
                        }
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
                fontFamily = FontFamily.Serif
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
                    fontFamily = FontFamily.Serif
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.size(36.dp).testTag("mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            contentDescription = "Mute Toggle",
                            tint = if (isMuted) TextDim else GoldBright,
                            modifier = Modifier.size(20.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        // Golden Dial Toggle Row
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
                    text = "Show Golden Dial",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif
                )
                Switch(
                    checked = isDialVisible,
                    onCheckedChange = { viewModel.toggleDial() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldBright,
                        checkedTrackColor = GoldDark,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = LineBorder
                    ),
                    modifier = Modifier.testTag("dial_switch")
                )
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

        Spacer(modifier = Modifier.height(16.dp))

        // Dial and Digital Timer Display Container
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
                // Top Fullscreen Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { viewModel.toggleFullscreen() },
                        modifier = Modifier.testTag("fullscreen_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = GoldLight
                        )
                    }
                }

                // Golden Analog Clock Dial
                if (isDialVisible) {
                    GoldenClockDial(
                        dialSize = 210.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Status hint text
                Text(
                    text = when {
                        !isTimerActive && !isRunning -> "Press Start to begin studying"
                        isRunning && stopwatchMode -> "Stopwatch running..."
                        isRunning && pomodoroMode -> if (pomoPhase == "work") "Focusing (25m)..." else "Break (5m)..."
                        isRunning -> "Countdown running..."
                        else -> "Paused - press Start to resume"
                    },
                    color = GoldLight,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Big Golden Display
                Text(
                    text = displayDigits,
                    style = TextStyle(
                        brush = GoldGradientBrush,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag("timer_display")
                )

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
                            containerColor = GoldAccent,
                            contentColor = BgDark,
                            disabledContainerColor = LineBorder,
                            disabledContentColor = TextDim
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("start_btn")
                    ) {
                        Text("Start", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.pauseTimer() },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PanelElevated,
                            contentColor = TextPrimary,
                            disabledContainerColor = PanelDark,
                            disabledContentColor = TextDim
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("pause_btn")
                    ) {
                        Text("Pause")
                    }

                    Button(
                        onClick = { viewModel.resetTimer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PanelElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("reset_btn")
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SubjectChip(
    name: String,
    isCore: Boolean,
    isSelected: Boolean,
    isTimerActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerFloat"
    )

    val backgroundBrush = if (isCore) {
        Brush.linearGradient(
            colors = listOf(
                PanelDark,
                Color(0x33E6C875),
                PanelDark
            ),
            start = Offset(shimmerOffset * 100f, 0f),
            end = Offset((shimmerOffset + 1f) * 100f, 100f)
        )
    } else {
        Brush.linearGradient(listOf(PanelDark, PanelDark))
    }

    val borderColor = when {
        isSelected -> GoldBright
        isCore -> GoldAccent
        else -> LineBorder
    }

    val textColor = when {
        isSelected -> GoldBright
        isCore -> GoldLight
        else -> TextDim
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0x33E6C875) else PanelDark)
            .background(backgroundBrush)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .then(
                if (isSelected) Modifier.shadow(8.dp, RoundedCornerShape(20.dp), spotColor = GoldAccent)
                else Modifier
            )
            .clickable(enabled = !isTimerActive) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isCore) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Serif
        )
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
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0x33E6C875) else PanelDark)
            .border(
                width = 1.dp,
                color = if (isSelected) GoldBright else LineBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = !isTimerActive) { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = "↳ $name",
            color = if (isSelected) GoldBright else TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif
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
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0x33E6C875) else PanelDark)
            .border(
                width = 1.dp,
                color = if (isSelected) GoldBright else LineBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = !isTimerActive) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) GoldBright else TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif
        )
    }
}

@Composable
private fun AddChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LineBright, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = GoldLight,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif
        )
    }
}

@Composable
private fun FullscreenFocusLayout(
    displayDigits: String,
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
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Exit Fullscreen",
                tint = GoldLight
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val title = if (!selectedSubSubject.isNullOrBlank()) "$selectedSubject - $selectedSubSubject" else selectedSubject
            Text(
                text = title,
                color = GoldBright,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = "[$selectedWorkType]",
                color = TextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (pomoPhase != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (pomoPhase == "work") "⏳ Focus Session (25m)" else "☕ Break (5m)",
                    color = GoldLight,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = displayDigits,
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier.width(320.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Start", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Button(
                    onClick = onPause,
                    enabled = isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelElevated, contentColor = TextPrimary),
                    border = BorderStroke(1.dp, LineBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Pause", fontSize = 16.sp)
                }

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelElevated, contentColor = TextPrimary),
                    border = BorderStroke(1.dp, LineBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
            }
        }
    }
}

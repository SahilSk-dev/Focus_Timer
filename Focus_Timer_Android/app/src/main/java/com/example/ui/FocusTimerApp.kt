package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.components.ConfettiEffect
import com.example.ui.components.SmoothTabHost
import com.example.ui.screens.HistorySettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TimerScreen
import com.example.ui.theme.BgDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldDark
import com.example.ui.theme.LineBorder
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.FocusViewModel

@Composable
fun FocusTimerApp(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    var currentNavIndex by remember { mutableStateOf(0) }

    val isStartupReady by viewModel.isStartupReady.collectAsState()
    var prewarmTabs by remember { mutableStateOf(false) }
    var showStartupSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(16)
        prewarmTabs = true
    }

    LaunchedEffect(isStartupReady, prewarmTabs) {
        if (isStartupReady && prewarmTabs) {
            delay(360)
            showStartupSplash = false
        }
    }

    val toastMsg by viewModel.toastMessage.collectAsState()
    val showConfetti by viewModel.showConfetti.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    // Dialog states
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    var isNewSubjectCore by remember { mutableStateOf(false) }
    var isNewSubjectNonStudy by remember { mutableStateOf(false) }

    var subSubjectTargetSubject by remember { mutableStateOf<String?>(null) }
    var newSubSubjectName by remember { mutableStateOf("") }

    var showAddWorkTypeDialog by remember { mutableStateOf(false) }
    var newWorkTypeName by remember { mutableStateOf("") }

    var editSubjectTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            viewModel.showToast("Enable notifications to receive study timer alarms")
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notifPermissionLauncher.launch(permission)
            }
        }
    }

    val activity = context as? android.app.Activity
    LaunchedEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isFullscreen) {
                    Surface(
                        color = BgDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = PanelDark,
                            border = BorderStroke(1.dp, LineBorder),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val navItems = listOf(
                                    Triple(0, "Timer", Icons.Default.Timer) to "nav_timer",
                                    Triple(1, "Analytics", Icons.Default.BarChart) to "nav_stats",
                                    Triple(2, "History", Icons.Default.History) to "nav_history"
                                )
                                navItems.forEach { (item, tag) ->
                                    val (index, label, icon) = item
                                    val isSelected = currentNavIndex == index
                                    val containerColor by animateColorAsState(
                                        targetValue = if (isSelected) GoldBright else PanelElevated,
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                        label = "nav_bg_$index"
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (isSelected) BgDark else TextDim,
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                        label = "nav_fg_$index"
                                    )
                                    Surface(
                                        onClick = { currentNavIndex = index },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .testTag(tag),
                                        shape = RoundedCornerShape(16.dp),
                                        color = containerColor,
                                        border = if (isSelected) null else BorderStroke(1.dp, LineBorder),
                                        shadowElevation = if (isSelected) 4.dp else 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = contentColor,
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = label,
                                                color = contentColor,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                SmoothTabHost(
                    selectedIndex = currentNavIndex,
                    tabCount = 3,
                    modifier = Modifier.fillMaxSize(),
                    prewarmAll = prewarmTabs
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> TimerScreen(
                            viewModel = viewModel,
                            onOpenAddSubject = { showAddSubjectDialog = true },
                            onOpenAddSubSubject = { parent -> subSubjectTargetSubject = parent },
                            onOpenAddWorkType = { showAddWorkTypeDialog = true },
                            onEditSubject = { name, id -> editSubjectTarget = name to id }
                        )
                        1 -> StatsScreen(viewModel = viewModel)
                        2 -> HistorySettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Confetti celebration overlay
        if (showConfetti) {
            ConfettiEffect(
                modifier = Modifier.fillMaxSize(),
                onFinished = { viewModel.dismissConfetti() }
            )
        }

        StartupLoadingOverlay(visible = showStartupSplash)
    }

    // Add Subject Dialog
    if (showAddSubjectDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSubjectDialog = false
                newSubjectName = ""
            },
            title = { Text("Add New Subject", color = GoldBright, ) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("Subject Name", color = TextDim) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isNewSubjectCore,
                            onCheckedChange = { isNewSubjectCore = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                        )
                        Text("Core Subject (Highlight Effect)", color = TextPrimary, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isNewSubjectNonStudy,
                            onCheckedChange = { isNewSubjectNonStudy = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                        )
                        Text("Non-Study (Separate category)", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubjectName.isNotBlank()) {
                            viewModel.addSubject(newSubjectName.trim(), isNewSubjectCore, isNewSubjectNonStudy)
                            showAddSubjectDialog = false
                            newSubjectName = ""
                            isNewSubjectCore = false
                            isNewSubjectNonStudy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSubjectDialog = false
                    newSubjectName = ""
                }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Add Sub-Subject Dialog
    if (subSubjectTargetSubject != null) {
        val parent = subSubjectTargetSubject!!
        AlertDialog(
            onDismissRequest = {
                subSubjectTargetSubject = null
                newSubSubjectName = ""
            },
            title = { Text("Add Sub-Subject for $parent", color = GoldBright, ) },
            text = {
                OutlinedTextField(
                    value = newSubSubjectName,
                    onValueChange = { newSubSubjectName = it },
                    label = { Text("Sub-Subject Name (e.g. Text, Grammar)", color = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubSubjectName.isNotBlank()) {
                            viewModel.addSubSubject(parent, newSubSubjectName.trim())
                            subSubjectTargetSubject = null
                            newSubSubjectName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    subSubjectTargetSubject = null
                    newSubSubjectName = ""
                }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Add Work Type Dialog
    if (showAddWorkTypeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddWorkTypeDialog = false
                newWorkTypeName = ""
            },
            title = { Text("Add Work Type", color = GoldBright, ) },
            text = {
                OutlinedTextField(
                    value = newWorkTypeName,
                    onValueChange = { newWorkTypeName = it },
                    label = { Text("Work Type (e.g. Solving Papers)", color = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWorkTypeName.isNotBlank()) {
                            viewModel.addWorkType(newWorkTypeName.trim())
                            showAddWorkTypeDialog = false
                            newWorkTypeName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddWorkTypeDialog = false
                    newWorkTypeName = ""
                }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Edit/Delete Subject Dialog
    if (editSubjectTarget != null) {
        val (name, id) = editSubjectTarget!!
        val allSubjs by viewModel.allSubjects.collectAsState()
        val currentSubj = allSubjs.find { it.id == id }
        var editName by remember(editSubjectTarget) { mutableStateOf(currentSubj?.name ?: name) }
        var editIsCore by remember(editSubjectTarget) { mutableStateOf(currentSubj?.isCore ?: false) }
        var editIsNonStudy by remember(editSubjectTarget) { mutableStateOf(currentSubj?.isNonStudy ?: false) }

        AlertDialog(
            onDismissRequest = { editSubjectTarget = null },
            title = { Text("Edit Subject", color = GoldBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Subject Name", color = TextDim) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editIsCore,
                            onCheckedChange = { editIsCore = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                        )
                        Text("Core Subject", color = TextPrimary, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editIsNonStudy,
                            onCheckedChange = { editIsNonStudy = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
                        )
                        Text("Non-Study (Personal/Excluded from Goals)", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentSubj?.isCore == false) {
                        Button(
                            onClick = {
                                viewModel.deleteSubject(id)
                                editSubjectTarget = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                        ) {
                            Text("Delete")
                        }
                    }
                    Button(
                        onClick = {
                            if (editName.isNotBlank() && currentSubj != null) {
                                viewModel.updateSubject(
                                    currentSubj.copy(
                                        name = editName.trim(),
                                        isCore = editIsCore,
                                        isNonStudy = editIsNonStudy
                                    )
                                )
                            }
                            editSubjectTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editSubjectTarget = null }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }

    // Continuous Alarm Dialog with Stop Button
    if (isAlarmRinging) {
        val selectedSubject by viewModel.selectedSubject.collectAsState()
        AlertDialog(
            onDismissRequest = { viewModel.stopAlarm() },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = GoldBright
                    )
                    Text("Time's Up!", color = GoldBright, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Focus session for $selectedSubject is complete!",
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Alarm is ringing. Tap STOP ALARM to silence.",
                        color = TextDim,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.stopAlarm() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("STOP ALARM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            containerColor = PanelDark
        )
    }
}

@Composable
private fun StartupLoadingOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "startup_pulse")
        val badgeScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "startup_badge_scale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = PanelDark,
                    border = BorderStroke(1.dp, LineBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = GoldBright,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Focus Timer",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        color = TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = GoldBright,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

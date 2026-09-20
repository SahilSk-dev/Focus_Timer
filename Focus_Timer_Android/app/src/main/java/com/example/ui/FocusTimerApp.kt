package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ConfettiEffect
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
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.FocusViewModel

@Composable
fun FocusTimerApp(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    var currentNavIndex by remember { mutableStateOf(0) }

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
                    NavigationBar(
                        containerColor = PanelDark,
                        contentColor = TextPrimary,
                        tonalElevation = 4.dp
                    ) {
                        NavigationBarItem(
                            selected = currentNavIndex == 0,
                            onClick = { currentNavIndex = 0 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer"
                                )
                            },
                            label = { Text("Timer", fontWeight = if (currentNavIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BgDark,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextDim,
                                unselectedTextColor = TextDim,
                                indicatorColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("nav_timer")
                        )
                        NavigationBarItem(
                            selected = currentNavIndex == 1,
                            onClick = { currentNavIndex = 1 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Stats"
                                )
                            },
                            label = { Text("Stats", fontWeight = if (currentNavIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BgDark,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextDim,
                                unselectedTextColor = TextDim,
                                indicatorColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("nav_stats")
                        )
                        NavigationBarItem(
                            selected = currentNavIndex == 2,
                            onClick = { currentNavIndex = 2 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History"
                                )
                            },
                            label = { Text("History", fontWeight = if (currentNavIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BgDark,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextDim,
                                unselectedTextColor = TextDim,
                                indicatorColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                when (currentNavIndex) {
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

        // Confetti celebration overlay
        if (showConfetti) {
            ConfettiEffect(
                modifier = Modifier.fillMaxSize(),
                onFinished = { viewModel.dismissConfetti() }
            )
        }
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
                        Text("Core Subject (Shimmer glow)", color = TextPrimary, fontSize = 13.sp)
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
                    Text("⏰", fontSize = 28.sp)
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
                    Text("STOP ALARM 🔔", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            containerColor = PanelDark
        )
    }
}

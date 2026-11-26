package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.WorkTypeEntity
import com.example.ui.components.AddSubjectDialog
import com.example.ui.components.AddWorkTypeDialog
import com.example.ui.components.ConfettiEffect
import com.example.ui.components.EditSubjectDialog
import com.example.ui.components.EditWorkTypeDialog
import com.example.ui.components.SmoothTabHost
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsSection
import com.example.ui.screens.SettingsToolsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TimerScreen
import com.example.ui.theme.ActiveTheme
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FocusTimerApp(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentNavIndex by remember { mutableStateOf(0) }
    var activeSettingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var lastSettingsSection by remember { mutableStateOf(SettingsSection.SYNC) }
    if (activeSettingsSection != null) {
        lastSettingsSection = activeSettingsSection!!
    }
    val currentAppTheme by viewModel.currentAppTheme.collectAsState()

    BackHandler(enabled = isFullscreen || drawerState.isOpen || activeSettingsSection != null) {
        when {
            isFullscreen -> viewModel.toggleFullscreen()
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            else -> activeSettingsSection = null
        }
    }

    val isStartupReady by viewModel.isStartupReady.collectAsState()
    val isOnboarded by viewModel.isOnboarded.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var warmedTabCount by remember { mutableStateOf(1) }
    var showStartupSplash by remember { mutableStateOf(true) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.markOnboarded()
        }
    }

    LaunchedEffect(isStartupReady) {
        if (isStartupReady) {
            showStartupSplash = false
            // Stagger warming Analytics & History one per idle window so the home screen never stutters after launch
            delay(220)
            if (warmedTabCount < 2) warmedTabCount = 2
            delay(220)
            if (warmedTabCount < 3) warmedTabCount = 3
        }
    }

    val toastMsg by viewModel.toastMessage.collectAsState()
    val showConfetti by viewModel.showConfetti.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    // Dialog states
    var showAddSubjectDialog by remember { mutableStateOf(false) }

    var subSubjectTargetSubject by remember { mutableStateOf<String?>(null) }
    var newSubSubjectName by remember { mutableStateOf("") }

    var showAddWorkTypeDialog by remember { mutableStateOf(false) }

    var editSubjectTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var editWorkTypeTarget by remember { mutableStateOf<WorkTypeEntity?>(null) }

    val allSubjects by viewModel.allSubjects.collectAsState()
    val allWorkTypes by viewModel.allWorkTypes.collectAsState()

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
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isFullscreen,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = PanelDark,
                    drawerContentColor = TextPrimary,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        // Pinned Sidebar Header & Account Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(GoldBright, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = BgDark,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Focus Timer",
                                    color = GoldBright,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    color = TextDim,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Account Status Pill
                        Surface(
                            color = PanelElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LineBorder),
                            onClick = {
                                activeSettingsSection = SettingsSection.SYNC
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = if (currentUser != null) GoldBright else TextDim,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = currentUser?.email ?: "Local mode · Tap to sign in",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(bottom = 12.dp))

                        // Scrollable Middle Navigation + Settings List
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {

                        val itemColors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = GoldAccent.copy(alpha = 0.2f),
                            selectedIconColor = GoldBright,
                            selectedTextColor = GoldBright,
                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextPrimary
                        )

                        Text(
                            text = "WORKSPACE",
                            color = TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                        )

                        listOf(
                            Triple(0, "Timer", Icons.Default.Timer),
                            Triple(1, "Analytics", Icons.Default.BarChart),
                            Triple(2, "History", Icons.Default.History)
                        ).forEach { (idx, label, icon) ->
                            NavigationDrawerItem(
                                label = { Text(label, fontWeight = FontWeight.Medium) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                selected = activeSettingsSection == null && currentNavIndex == idx,
                                onClick = {
                                    activeSettingsSection = null
                                    currentNavIndex = idx
                                    coroutineScope.launch { drawerState.close() }
                                },
                                colors = itemColors,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "SETTINGS & TOOLS",
                            color = TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                        )

                        listOf(
                            Triple(SettingsSection.SYNC, "Account & Cloud Sync", Icons.Default.Cloud),
                            Triple(SettingsSection.DATA, "Backup & Bulk Data", Icons.Default.Download),
                            Triple(SettingsSection.ALARM, "Timer & Alarm Sound", Icons.Default.Notifications),
                            Triple(SettingsSection.SUBJECTS, "Subjects & Work Types", Icons.Default.Edit)
                        ).forEach { (section, label, icon) ->
                            NavigationDrawerItem(
                                label = { Text(label, fontWeight = FontWeight.Medium) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                selected = activeSettingsSection == section,
                                onClick = {
                                    activeSettingsSection = section
                                    coroutineScope.launch { drawerState.close() }
                                },
                                colors = itemColors,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        }

                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 12.dp))

                        // Appearance quick switch (pinned at bottom of Sidebar)
                        Surface(
                            color = PanelElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LineBorder),
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.fillMaxWidth()
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
                                        imageVector = if (currentAppTheme.isLight) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = GoldBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (currentAppTheme.isLight) "Light Mode" else "Black Mode",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Switch(
                                    checked = !currentAppTheme.isLight,
                                    onCheckedChange = { viewModel.toggleTheme() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDark,
                                        checkedTrackColor = GoldAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) {
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
                                    val isSelected = activeSettingsSection == null && currentNavIndex == index
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
                                        onClick = {
                                            activeSettingsSection = null
                                            currentNavIndex = index
                                        },
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
                    warmedCount = warmedTabCount
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> TimerScreen(
                            viewModel = viewModel,
                            onOpenAddSubject = { showAddSubjectDialog = true },
                            onOpenAddSubSubject = { parent -> subSubjectTargetSubject = parent },
                            onOpenAddWorkType = { showAddWorkTypeDialog = true },
                            onEditSubject = { name, id -> editSubjectTarget = name to id },
                            onEditWorkType = { wt -> editWorkTypeTarget = wt },
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                        )
                        1 -> StatsScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                        )
                        2 -> HistoryScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenSettings = { section -> activeSettingsSection = section }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = activeSettingsSection != null,
                    enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 5 },
                    exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { it / 5 }
                ) {
                    SettingsToolsScreen(
                        viewModel = viewModel,
                        selectedSection = lastSettingsSection,
                        onSelectSection = { activeSettingsSection = it },
                        onBack = { activeSettingsSection = null },
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                    )
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

        // Professional Welcome / Auth Gate for first-time or signed-out non-onboarded users
        if (!showStartupSplash && !isOnboarded && currentUser == null) {
            WelcomeOnboardingOverlay(
                onGoogleSignIn = {
                    val hostActivity = context as? android.app.Activity
                    if (hostActivity != null) {
                        viewModel.signInWithGoogle(hostActivity)
                    } else {
                        viewModel.showToast("Unable to launch Google Sign-In from this context")
                    }
                },
                onContinueLocal = {
                    viewModel.markOnboarded()
                    viewModel.showToast("Local mode — sessions stay on this device")
                }
            )
        }

        StartupLoadingOverlay(visible = showStartupSplash)
    }

    // Add Subject Dialog
    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onAdd = { name, isCore, isNonStudy ->
                viewModel.addSubject(name, isCore, isNonStudy)
                showAddSubjectDialog = false
            }
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
            title = { Text("Add Sub-Subject for $parent", color = GoldBright) },
            text = {
                OutlinedTextField(
                    value = newSubSubjectName,
                    onValueChange = { newSubSubjectName = it },
                    label = { Text("Sub-Subject Name (e.g. Text, Grammar)", color = TextDim) },
                    singleLine = true,
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
        AddWorkTypeDialog(
            onDismiss = { showAddWorkTypeDialog = false },
            onAdd = { name ->
                viewModel.addWorkType(name)
                showAddWorkTypeDialog = false
            }
        )
    }

    // Unified Edit/Delete Subject Dialog (includes Sub-Subjects)
    if (editSubjectTarget != null) {
        val (_, id) = editSubjectTarget!!
        val currentSubj = allSubjects.find { it.id == id }
        if (currentSubj != null) {
            EditSubjectDialog(
                subject = currentSubj,
                canDelete = allSubjects.size > 1,
                onDismiss = { editSubjectTarget = null },
                onSave = { updated ->
                    viewModel.updateSubject(updated)
                    editSubjectTarget = null
                },
                onDelete = {
                    viewModel.deleteSubject(currentSubj.id)
                    editSubjectTarget = null
                }
            )
        }
    }

    // Unified Edit/Delete Work Type Dialog
    if (editWorkTypeTarget != null) {
        val targetWt = allWorkTypes.find { it.id == editWorkTypeTarget!!.id } ?: editWorkTypeTarget!!
        EditWorkTypeDialog(
            workType = targetWt,
            canDelete = allWorkTypes.size > 1,
            onDismiss = { editWorkTypeTarget = null },
            onSave = { newName ->
                viewModel.updateWorkType(targetWt.id, newName)
                editWorkTypeTarget = null
            },
            onDelete = {
                viewModel.deleteWorkType(targetWt.id)
                editWorkTypeTarget = null
            }
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
private fun WelcomeOnboardingOverlay(
    onGoogleSignIn: () -> Unit,
    onContinueLocal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(18.dp),
                color = PanelDark,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Focus Timer",
                    color = GoldBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = "Study with intention.\nImprove with evidence.",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "Track every session, understand your focus patterns, and keep your progress safe across devices.",
                    color = TextDim,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PanelDark,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "Pomodoro, custom countdown & stopwatch",
                        "Daily goals, streaks & pace insights",
                        "Cloud sync with Web & JSON backups"
                    ).forEach { point ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PanelElevated,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = GoldBright,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = point,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onGoogleSignIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldBright,
                            contentColor = BgDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Continue with Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = onContinueLocal,
                        border = BorderStroke(1.dp, LineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Continue locally",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = "Local mode stores data on this device. You can connect Google anytime in History & Data.",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupLoadingOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
    ) {
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
                    modifier = Modifier.size(72.dp),
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

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudySessionEntity
import com.example.ui.theme.BgDark
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldGradientBrush
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LineBorder
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.util.WebPortalHelper
import com.example.viewmodel.FocusViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ============================================================
// PERFORMANCE DASHBOARD SCREEN (STREAMLINED & ULTRA-FAST)
// ============================================================
@Composable
fun StatsScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSessions by viewModel.allSessions.collectAsState()
    val studySessions = remember(allSessions) { allSessions.filter { !it.isNonStudy } }
    val dailyTarget by viewModel.dailyTargetMinutes.collectAsState()

    var showTargetEditDialog by remember { mutableStateOf(false) }
    var statsTab by remember { mutableStateOf("daily") } // "daily" or "weekly"

    // Today's date
    val todayStr = remember {
        com.example.util.DateFormatterCache.formatIsoDate(System.currentTimeMillis())
    }

    // Streaks
    val streakPair = remember(studySessions) { calculateStreaks(studySessions) }
    val currentStreak = streakPair.first
    val bestStreak = streakPair.second

    // Daily Target progress
    val todayDoneMinutes = remember(studySessions, todayStr) {
        studySessions.filter { it.date == todayStr }.sumOf { it.minutes }
    }
    val targetPct = remember(todayDoneMinutes, dailyTarget) {
        if (dailyTarget > 0) ((todayDoneMinutes.toFloat() / dailyTarget) * 100).toInt().coerceAtMost(100) else 0
    }

    // Level & XP
    val totalStudyMinutes = remember(studySessions) { studySessions.sumOf { it.minutes } }
    val totalHoursDouble = remember(totalStudyMinutes) { totalStudyMinutes / 60.0 }
    val xpPerLevel = 300
    val level = remember(totalStudyMinutes) { (totalStudyMinutes / xpPerLevel) + 1 }
    val currentXp = remember(totalStudyMinutes) { totalStudyMinutes % xpPerLevel }
    val xpPct = remember(currentXp) { ((currentXp.toFloat() / xpPerLevel) * 100).toInt().coerceIn(0, 100) }

    // Week comparison
    val weekComparison = remember(studySessions) { calculateWeekComparison(studySessions) }

    // Last 7 days ranking
    val rankingList = remember(studySessions) { calculate7DaysRanking(studySessions) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // ============================================================
        // 1. HEADER (Title & Web PDF Action)
        // ============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Performance",
                    style = TextStyle(
                        brush = GoldGradientBrush,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Daily activity & summary",
                    color = TextDim,
                    fontSize = 12.sp
                )
            }

            Surface(
                onClick = {
                    WebPortalHelper.openWebHistoryReport(context)
                },
                shape = RoundedCornerShape(8.dp),
                color = PanelElevated,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Web PDF",
                        tint = GoldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Web PDF ↗",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ============================================================
        // 2. 2x2 METRIC DASHBOARD GRID
        // ============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Streak
            MetricCard(
                icon = "🔥",
                title = "Day Streak",
                value = "$currentStreak days",
                subtext = "Best: $bestStreak days",
                modifier = Modifier.weight(1f)
            )

            // Card 2: Total Study Hours
            MetricCard(
                icon = "⏱️",
                title = "Total Focus",
                value = "${String.format(Locale.US, "%.1f", totalHoursDouble)} hrs",
                subtext = "${studySessions.size} sessions",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Daily Goal
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showTargetEditDialog = true },
                color = PanelDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎯 Daily Goal", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Goal",
                            tint = GoldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$todayDoneMinutes / $dailyTarget m",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LineBorder)
                    ) {
                        if (targetPct > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(targetPct / 100f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(GoldGradientBrush)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$targetPct% achieved", color = GoldLight, fontSize = 11.sp)
                }
            }

            // Card 4: Level & XP
            Surface(
                modifier = Modifier.weight(1f),
                color = PanelDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⚡ Scholar Rank", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Lv. $level", color = GoldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Level $level",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LineBorder)
                    ) {
                        if (xpPct > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(xpPct / 100f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SuccessGreen)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$currentXp / $xpPerLevel XP", color = TextDim, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 3. STUDY ACTIVITY (Daily vs Weekly Comparison)
        // ============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Study Activity",
                    style = TextStyle(
                        brush = GoldGradientBrush,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (weekComparison.isNotBlank()) {
                    Text(text = weekComparison, color = GoldLight, fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier
                    .background(PanelElevated, RoundedCornerShape(6.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (statsTab == "daily") GoldAccent else Color.Transparent)
                        .clickable { statsTab = "daily" }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Today",
                        color = if (statsTab == "daily") BgDark else TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (statsTab == "weekly") GoldAccent else Color.Transparent)
                        .clickable { statsTab = "weekly" }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "7 Days",
                        color = if (statsTab == "weekly") BgDark else TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (statsTab == "daily") {
                    val todaySess = remember(studySessions, todayStr) {
                        studySessions.filter { it.date == todayStr }
                    }
                    val bySubj = remember(todaySess) {
                        todaySess.groupBy { it.subject }.mapValues { it.value.sumOf { s -> s.minutes } }
                    }
                    val maxVal = remember(bySubj) {
                        bySubj.values.maxOrNull() ?: 1
                    }

                    if (bySubj.isEmpty()) {
                        Text(
                            text = "No focus sessions recorded today yet.",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        bySubj.entries.sortedByDescending { it.value }.forEach { entry ->
                            CleanBarChartRow(
                                label = entry.key,
                                minutes = entry.value,
                                pct = (entry.value.toFloat() / maxVal).coerceIn(0.06f, 1f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = "Total today: $todayDoneMinutes mins",
                            color = GoldBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    val last7Days = remember { getLast7Days() }
                    val totals = remember(studySessions, last7Days) {
                        last7Days.map { d ->
                            studySessions.filter { it.date == d.dateStr }.sumOf { it.minutes }
                        }
                    }
                    val grandTotal = remember(totals) { totals.sum() }
                    val maxVal = remember(totals) { (totals.maxOrNull() ?: 1).coerceAtLeast(1) }

                    if (grandTotal == 0) {
                        Text(
                            text = "No focus sessions in the last 7 days.",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        last7Days.forEachIndexed { idx, d ->
                            val mins = totals[idx]
                            CleanBarChartRow(
                                label = d.dayLabel,
                                minutes = mins,
                                pct = if (mins == 0) 0f else (mins.toFloat() / maxVal).coerceIn(0.06f, 1f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = "7-Day Total: $grandTotal mins (${String.format(Locale.US, "%.1f", grandTotal / 60.0)} hrs)",
                            color = GoldBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 4. SUBJECT RANKING (Last 7 Days)
        // ============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subject Ranking (7 Days)",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(text = "Top Ranks", color = TextDim, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (rankingList.isEmpty()) {
                    Text(text = "No study recorded in the last 7 days.", color = TextDim, fontSize = 12.sp)
                } else {
                    rankingList.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeEmoji = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${index + 1}"
                            }
                            Text(
                                text = badgeEmoji,
                                fontSize = if (index < 3) 16.sp else 12.sp,
                                modifier = Modifier.width(26.dp)
                            )
                            Text(
                                text = item.subjectName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.minutes}m",
                                color = GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (index < rankingList.size - 1) {
                            HorizontalDivider(color = LineBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 5. 12-WEEK CONSISTENCY HEATMAP
        // ============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "12-Week Consistency Grid",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(text = "84 Days", color = TextDim, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                HeatmapGrid(sessions = studySessions)

                Spacer(modifier = Modifier.height(10.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Less", color = TextDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF1C1C1A), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF4A3A1A), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF7A5F22), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFC9962F), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "More", color = TextDim, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ============================================================
        // 6. DEEP COGNITIVE ANALYTICS & WEB PDF PORTAL CARD
        // ============================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🌐", fontSize = 24.sp)
                    Column {
                        Text(
                            text = "Deep Cognitive Analytics",
                            color = GoldBright,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "High-Resolution Executive Suite & PDF",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Access your full 24h Circadian Wave, 6-Axis Spider Radar Polygon, Cognitive Fatigue Matrix, Milestone Badges, and high-resolution PDF report with clean multi-page tables on the Web Dashboard.",
                    color = TextPrimary.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Feature Highlights Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("🌊 Circadian Wave", "🕸️ Spider Radar", "⚡ Fatigue", "📄 AutoTable PDF").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PanelDark)
                                .border(1.dp, LineBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(text = tag, color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        WebPortalHelper.openWebAnalytics(context)
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
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = BgDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Open Web Analytics & Reports ↗",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // ============================================================
    // DIALOGS: DAILY TARGET EDIT
    // ============================================================
    if (showTargetEditDialog) {
        var inputTarget by remember(dailyTarget) { mutableStateOf(dailyTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showTargetEditDialog = false },
            title = { Text("Set Daily Goal", color = GoldBright, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Choose daily study target in minutes:", color = TextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 60, 120, 180).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (inputTarget == mins.toString()) GoldAccent else PanelElevated)
                                    .clickable { inputTarget = mins.toString() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = if (inputTarget == mins.toString()) BgDark else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputTarget,
                        onValueChange = { inputTarget = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Target (Minutes)", color = TextDim, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("daily_target_input"),
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
                        val num = inputTarget.toIntOrNull()
                        if (num != null && num >= 10) {
                            viewModel.setDailyTarget(num)
                            showTargetEditDialog = false
                        } else {
                            viewModel.showToast("Please enter at least 10 minutes")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                ) {
                    Text("Save Goal", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetEditDialog = false }) {
                    Text("Cancel", color = TextDim)
                }
            },
            containerColor = PanelDark
        )
    }
}

// ============================================================
// COMPONENT: METRIC CARD
// ============================================================
@Composable
private fun MetricCard(
    icon: String,
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PanelDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "$icon $title", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                color = GoldLight,
                fontSize = 11.sp
            )
        }
    }
}

// ============================================================
// COMPONENT: CLEAN BAR CHART ROW
// ============================================================
@Composable
private fun CleanBarChartRow(
    label: String,
    minutes: Int,
    pct: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PanelElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoldGradientBrush)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${minutes}m",
            color = GoldLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )
    }
}

// ============================================================
// COMPONENT: HEATMAP GRID (FAST 84-CELL CANVAS)
// ============================================================
@Composable
private fun HeatmapGrid(sessions: List<StudySessionEntity>) {
    val totalsByDate = remember(sessions) {
        sessions.groupBy { it.date }.mapValues { it.value.sumOf { s -> s.minutes } }
    }

    val days = 84 // 12 weeks
    val cells = remember(totalsByDate) {
        val list = ArrayList<Triple<String, Int, Int>>(days)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0 until days) {
            val dStr = com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis)
            val mins = totalsByDate[dStr] ?: 0
            val lvl = when {
                mins == 0 -> 0
                mins <= 30 -> 1
                mins <= 60 -> 2
                else -> 3
            }
            list.add(Triple(dStr, mins, lvl))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val colors = listOf(
        Color(0xFF1C1C1A),
        Color(0xFF4A3A1A),
        Color(0xFF7A5F22),
        Color(0xFFC9962F)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Canvas(
            modifier = Modifier
                .width(188.dp) // 12 cols * 12dp + 11 gaps * 4dp = 188dp
                .height(108.dp) // 7 rows * 12dp + 6 gaps * 4dp = 108dp
        ) {
            val cellSize = 12.dp.toPx()
            val spacing = 4.dp.toPx()
            val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

            for (col in 0 until 12) {
                for (row in 0 until 7) {
                    val index = col * 7 + row
                    if (index < cells.size) {
                        val cell = cells[index]
                        val color = colors[cell.third]
                        val x = col * (cellSize + spacing)
                        val y = row * (cellSize + spacing)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// HELPER LOGIC: STREAKS & RANKING
// ============================================================
@Immutable
private data class DayInfo(val dateStr: String, val dayLabel: String)
@Immutable
private data class RankItem(val subjectName: String, val minutes: Int)

private fun getLast7Days(): List<DayInfo> {
    val list = mutableListOf<DayInfo>()
    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val cal = Calendar.getInstance()
    for (i in 6 downTo 0) {
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        val dStr = com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis)
        val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
        list.add(DayInfo(dStr, dayNames.getOrElse(dayIndex) { "" }))
    }
    return list
}

private fun calculateStreaks(sessions: List<StudySessionEntity>): Pair<Int, Int> {
    val uniqueDates = sessions.map { it.date }.distinct().sorted()
    if (uniqueDates.isEmpty()) return 0 to 0

    val dateSet = uniqueDates.toSet()
    val cal = Calendar.getInstance()

    // Current streak
    var current = 0
    if (!dateSet.contains(com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis))) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    while (dateSet.contains(com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis))) {
        current++
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }

    // Best streak
    var best = 1
    var run = 1
    for (i in 1 until uniqueDates.size) {
        val d1 = com.example.util.DateFormatterCache.parseIsoDate(uniqueDates[i - 1]) ?: continue
        val d2 = com.example.util.DateFormatterCache.parseIsoDate(uniqueDates[i]) ?: continue
        val diffDays = (d2.time - d1.time) / (24 * 3600 * 1000)
        if (diffDays == 1L) {
            run++
            if (run > best) best = run
        } else {
            run = 1
        }
    }
    best = maxOf(best, current)
    return current to best
}

private fun calculateWeekComparison(sessions: List<StudySessionEntity>): String {
    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    val thisWeekDays = (0..6).map {
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -it)
        com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis)
    }.toSet()
    val lastWeekDays = (7..13).map {
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -it)
        com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis)
    }.toSet()

    val thisWeek = sessions.filter { it.date in thisWeekDays }.sumOf { it.minutes }
    val lastWeek = sessions.filter { it.date in lastWeekDays }.sumOf { it.minutes }

    if (thisWeek == 0 && lastWeek == 0) return ""
    if (lastWeek == 0) return "Studied for $thisWeek mins this week"

    val diff = ((thisWeek - lastWeek).toFloat() / lastWeek * 100).toInt()
    val arrow = if (diff >= 0) "▲" else "▼"
    val word = if (diff >= 0) "more" else "less"
    return "$arrow ${abs(diff)}% $word than last week"
}

private fun calculate7DaysRanking(sessions: List<StudySessionEntity>): List<RankItem> {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -7)
    val sevenDaysAgoStr = com.example.util.DateFormatterCache.formatIsoDate(cal.timeInMillis)

    val recent = sessions.filter { it.date >= sevenDaysAgoStr }
    val map = mutableMapOf<String, Int>()
    recent.forEach { s ->
        val mainSubj = s.subject.split(" - ").firstOrNull() ?: s.subject
        map[mainSubj] = (map[mainSubj] ?: 0) + s.minutes
    }

    return map.entries
        .sortedByDescending { it.value }
        .take(5)
        .map { RankItem(it.key, it.value) }
}

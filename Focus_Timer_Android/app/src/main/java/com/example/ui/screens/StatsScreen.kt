package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.util.ExamGoal
import com.example.util.ExamProjection
import com.example.util.FatigueReport
import com.example.util.SubjectEquilibriumItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudySessionEntity
import com.example.data.repository.FocusRepository
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
import com.example.util.AnalyticsEngine
import com.example.util.AnalyticsReport
import com.example.util.AnalyticsTimeframe
import com.example.util.PdfExportHelper
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ============================================================
// DATA MODEL FOR MILESTONE BADGES
// ============================================================
@Immutable
data class MilestoneBadge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: String, // Streak, Study Hours, Consistency
    val currentVal: Int,
    val targetVal: Int,
    val unit: String,
    val isUnlocked: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSessions by viewModel.allSessions.collectAsState()
    val dailyTarget by viewModel.dailyTargetMinutes.collectAsState()

    var statsTab by remember { mutableStateOf("daily") } // "daily" or "weekly"
    var showTargetEditDialog by remember { mutableStateOf(false) }

    val todayStr = remember { FocusRepository.getTodayString() }
    val studySessions = remember(allSessions) { allSessions.filter { !it.isNonStudy } }

    // Day Streak calculation (Computed synchronously for instant 60fps render)
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

    // Week comparison (Computed synchronously for instant 60fps render)
    val weekComparison = remember(studySessions) { calculateWeekComparison(studySessions) }

    // Last 7 days ranking (Computed synchronously for instant 60fps render)
    val rankingList = remember(studySessions) { calculate7DaysRanking(studySessions) }

    // Milestone Badges calculation (Computed synchronously for instant 60fps render)
    val allBadges = remember(studySessions, currentStreak, bestStreak, totalStudyMinutes) {
        calculateMilestoneBadges(studySessions, currentStreak, bestStreak, totalStudyMinutes)
    }

    // Cognitive Deep Analysis Engine computation (Computed asynchronously on Dispatchers.Default)
    var analyticsTimeframe by remember { mutableStateOf(AnalyticsTimeframe.LAST_7_DAYS) }
    val examGoal by viewModel.examGoal.collectAsState()
    val availableSubjects = remember(studySessions) {
        studySessions.map { it.subject.split(" - ").firstOrNull() ?: it.subject }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val analyticsReport by produceState(
        initialValue = AnalyticsReport.empty(analyticsTimeframe),
        studySessions, analyticsTimeframe, examGoal, dailyTarget
    ) {
        value = withContext(Dispatchers.Default) {
            AnalyticsEngine.computeReport(
                allSessions = studySessions,
                timeframe = analyticsTimeframe,
                examGoal = examGoal,
                dailyTargetMinutes = dailyTarget
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // ============================================================
        // 1. HEADER (Title & Quick PDF Action)
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
                    text = "Activity & milestone tracker",
                    color = TextDim,
                    fontSize = 12.sp
                )
            }

            Surface(
                onClick = {
                    if (studySessions.isEmpty()) {
                        viewModel.showToast("No study sessions recorded yet")
                    } else {
                        try {
                            PdfExportHelper.generateAndSharePdf(context, studySessions, "All Time")
                        } catch (e: Exception) {
                            viewModel.showToast("PDF Export failed: ${e.localizedMessage}")
                        }
                    }
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
                        contentDescription = "Export PDF",
                        tint = GoldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "PDF",
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
        // 3. COGNITIVE DEEP ANALYSIS ENGINE
        // ============================================================
        DeepAnalysisEngineSection(
            report = analyticsReport,
            currentTimeframe = analyticsTimeframe,
            onTimeframeSelected = { analyticsTimeframe = it },
            examGoal = examGoal,
            onSaveExamGoal = { name, date, hours, scope ->
                viewModel.updateExamGoal(name, date, hours, scope)
            },
            availableSubjects = availableSubjects,
            onExportPdf = {
                try {
                    PdfExportHelper.generateAndShareAnalysisPdf(
                        context = context,
                        report = analyticsReport,
                        currentStreak = currentStreak,
                        bestStreak = bestStreak
                    )
                } catch (e: Exception) {
                    viewModel.showToast("Analysis PDF failed: ${e.localizedMessage}")
                }
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ============================================================
        // 4. STUDY ACTIVITY CHART (Daily vs Weekly)
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
        // 5. SUBJECT RANKING (Last 7 Days)
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
        // 6. 12-WEEK CONSISTENCY HEATMAP
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

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 7. MILESTONES & BADGES
        // ============================================================
        MilestonesAndBadgesSection(allBadges = allBadges)

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
// COMPONENT: MILESTONES & BADGES SECTION (ISOLATED PERFORMANCE)
// ============================================================
@Composable
private fun MilestonesAndBadgesSection(
    allBadges: List<MilestoneBadge>,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var badgeFilter by remember { mutableStateOf("all") }
    var selectedBadgeForDetail by remember { mutableStateOf<MilestoneBadge?>(null) }

    val unlockedBadgeCount = remember(allBadges) { allBadges.count { it.isUnlocked } }
    val displayedBadges = remember(allBadges, badgeFilter) {
        when (badgeFilter) {
            "unlocked" -> allBadges.filter { it.isUnlocked }
            "locked" -> allBadges.filter { !it.isUnlocked }
            else -> allBadges
        }
    }
    val pairedBadges = remember(displayedBadges) {
        displayedBadges.chunked(2)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PanelDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with click-to-expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Milestones & Badges",
                        style = TextStyle(
                            brush = GoldGradientBrush,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (isExpanded) "Earn rewards by hitting focus goals" else "Tap to view milestone achievements",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = PanelElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Text(
                            text = "$unlockedBadgeCount/${allBadges.size} Earned 🏆",
                            color = GoldBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = GoldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Badge Filter Tabs (All / Earned / Locked)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("all", "All (${allBadges.size})", allBadges.size),
                            Triple("unlocked", "Earned ($unlockedBadgeCount)", unlockedBadgeCount),
                            Triple("locked", "Locked (${allBadges.size - unlockedBadgeCount})", allBadges.size - unlockedBadgeCount)
                        ).forEach { (key, label, _) ->
                            val isSelected = badgeFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) GoldAccent else PanelElevated)
                                    .clickable { badgeFilter = key }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) BgDark else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Badges 2-Column Responsive Paired Grid
                    if (displayedBadges.isEmpty()) {
                        Text(
                            text = "No badges match this filter.",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        pairedBadges.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BadgeCard(
                                    badge = pair[0],
                                    onClick = { selectedBadgeForDetail = pair[0] },
                                    modifier = Modifier.weight(1f)
                                )
                                if (pair.size > 1) {
                                    BadgeCard(
                                        badge = pair[1],
                                        onClick = { selectedBadgeForDetail = pair[1] },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    // Isolated Badge Detail Dialog (recomposes ONLY this section)
    selectedBadgeForDetail?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            onDismiss = { selectedBadgeForDetail = null }
        )
    }
}

// ============================================================
// COMPONENT: BADGE DETAIL DIALOG (ISOLATED)
// ============================================================
@Composable
private fun BadgeDetailDialog(
    badge: MilestoneBadge,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = badge.icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = badge.title, color = GoldBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(text = badge.description, color = TextPrimary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(14.dp))

                val pct = ((badge.currentVal.toFloat() / badge.targetVal) * 100).toInt().coerceIn(0, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Milestone Goal", color = TextDim, fontSize = 11.sp)
                    Text(
                        text = "${badge.currentVal} / ${badge.targetVal} ${badge.unit}",
                        color = if (badge.isUnlocked) GoldBright else TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LineBorder)
                ) {
                    if (pct > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (badge.isUnlocked) GoldGradientBrush else SolidColor(SuccessGreen))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = if (badge.isUnlocked) PanelElevated else Color(0xFF1E1E22),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (badge.isUnlocked) GoldAccent else LineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (badge.isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (badge.isUnlocked) GoldBright else TextDim,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (badge.isUnlocked) "Reward Unlocked! ✨" else "In Progress ($pct%)",
                            color = if (badge.isUnlocked) GoldBright else TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = GoldBright, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PanelDark
    )
}

// ============================================================
// COMPONENT: BADGE CARD
// ============================================================
@Composable
private fun BadgeCard(
    badge: MilestoneBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = remember(badge.currentVal, badge.targetVal) {
        ((badge.currentVal.toFloat() / badge.targetVal) * 100).toInt().coerceIn(0, 100)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (badge.isUnlocked) PanelElevated else PanelDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (badge.isUnlocked) GoldAccent else LineBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (badge.isUnlocked) Color(0xFF2E2718) else Color(0xFF1E1E22)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.icon,
                        fontSize = 18.sp
                    )
                }

                if (badge.isUnlocked) {
                    Surface(
                        color = Color(0xFF332B1A),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Earned ✨",
                            color = GoldBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFF1C1C20),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, LineBorder)
                    ) {
                        Text(
                            text = "$pct%",
                            color = TextDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = badge.title,
                color = if (badge.isUnlocked) GoldBright else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = badge.description,
                color = TextDim,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mini progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LineBorder)
            ) {
                if (pct > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (badge.isUnlocked) GoldGradientBrush else SolidColor(SuccessGreen))
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${badge.currentVal}/${badge.targetVal} ${badge.unit}",
                color = if (badge.isUnlocked) GoldLight else TextDim,
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
// COMPONENT: HEATMAP GRID
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

    // 12 columns x 7 rows drawn on a single fast Canvas
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
// HELPER LOGIC: STREAKS, STATS & BADGES
// ============================================================
@Immutable
private data class DayInfo(val dateStr: String, val dayLabel: String)
@Immutable
private data class RankItem(val subjectName: String, val minutes: Int)

private fun calculateMilestoneBadges(
    sessions: List<StudySessionEntity>,
    currentStreak: Int,
    bestStreak: Int,
    totalMinutes: Int
): List<MilestoneBadge> {
    val totalHours = totalMinutes / 60
    val maxSessionMin = sessions.maxOfOrNull { it.minutes } ?: 0
    val totalSessions = sessions.size

    return listOf(
        MilestoneBadge(
            id = "first_step",
            title = "First Step",
            description = "Completed your first study session",
            icon = "🌟",
            category = "Consistency",
            currentVal = totalSessions.coerceAtMost(1),
            targetVal = 1,
            unit = "session",
            isUnlocked = totalSessions >= 1
        ),
        MilestoneBadge(
            id = "streak_3",
            title = "3-Day Streak",
            description = "Maintained study focus for 3 consecutive days",
            icon = "⚡",
            category = "Streak",
            currentVal = bestStreak.coerceAtMost(3),
            targetVal = 3,
            unit = "days",
            isUnlocked = bestStreak >= 3
        ),
        MilestoneBadge(
            id = "streak_7",
            title = "7-Day Warrior",
            description = "Maintained study streak for a full week",
            icon = "🔥",
            category = "Streak",
            currentVal = bestStreak.coerceAtMost(7),
            targetVal = 7,
            unit = "days",
            isUnlocked = bestStreak >= 7
        ),
        MilestoneBadge(
            id = "streak_10",
            title = "10-Day Streak",
            description = "Disciplined focus for 10 days in a row",
            icon = "🏆",
            category = "Streak",
            currentVal = bestStreak.coerceAtMost(10),
            targetVal = 10,
            unit = "days",
            isUnlocked = bestStreak >= 10
        ),
        MilestoneBadge(
            id = "streak_30",
            title = "30-Day Master",
            description = "Elite consistency: 30-day continuous study habit",
            icon = "👑",
            category = "Streak",
            currentVal = bestStreak.coerceAtMost(30),
            targetVal = 30,
            unit = "days",
            isUnlocked = bestStreak >= 30
        ),
        MilestoneBadge(
            id = "hours_10",
            title = "10 Hours Club",
            description = "Completed 10 hours of focused study",
            icon = "⏱️",
            category = "Study Hours",
            currentVal = totalHours.coerceAtMost(10),
            targetVal = 10,
            unit = "hours",
            isUnlocked = totalHours >= 10
        ),
        MilestoneBadge(
            id = "hours_50",
            title = "50 Hours Studied",
            description = "Scholar milestone: 50 hours of total study time",
            icon = "🎓",
            category = "Study Hours",
            currentVal = totalHours.coerceAtMost(50),
            targetVal = 50,
            unit = "hours",
            isUnlocked = totalHours >= 50
        ),
        MilestoneBadge(
            id = "hours_100",
            title = "100 Hours Titan",
            description = "Mastery milestone: 100 hours of deep study",
            icon = "💎",
            category = "Study Hours",
            currentVal = totalHours.coerceAtMost(100),
            targetVal = 100,
            unit = "hours",
            isUnlocked = totalHours >= 100
        ),
        MilestoneBadge(
            id = "deep_focus",
            title = "Deep Focus",
            description = "Completed a continuous session of 60+ mins",
            icon = "🧘",
            category = "Focus",
            currentVal = if (maxSessionMin >= 60) 1 else 0,
            targetVal = 1,
            unit = "session",
            isUnlocked = maxSessionMin >= 60
        )
    )
}

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
    return map.entries.sortedByDescending { it.value }.map { RankItem(it.key, it.value) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepAnalysisEngineSection(
    report: AnalyticsReport,
    currentTimeframe: AnalyticsTimeframe,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit,
    examGoal: ExamGoal,
    onSaveExamGoal: (String, String, Double, Set<String>) -> Unit,
    availableSubjects: List<String>,
    onExportPdf: () -> Unit
) {
    var showGoalDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        ExamGoalEditDialog(
            currentGoal = examGoal,
            availableSubjects = availableSubjects,
            onDismiss = { showGoalDialog = false },
            onSave = { name, date, hours, scope ->
                onSaveExamGoal(name, date, hours, scope)
                showGoalDialog = false
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "🧠 Deep Analysis Engine",
                        style = TextStyle(
                            brush = GoldGradientBrush,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cognitive habits, rhythm & stamina",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                Surface(
                    onClick = { onExportPdf() },
                    shape = RoundedCornerShape(8.dp),
                    color = PanelElevated,
                    border = BorderStroke(1.dp, LineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Analysis PDF Report",
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "PDF Report",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timeframe Selector Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsTimeframe.values().forEach { tf ->
                    val isSelected = tf == currentTimeframe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) GoldAccent else PanelElevated)
                            .border(1.dp, if (isSelected) GoldAccent else LineBorder, RoundedCornerShape(20.dp))
                            .clickable { onTimeframeSelected(tf) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tf.label,
                            color = if (isSelected) BgDark else TextDim,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 0. Exam & Syllabus Target Projection
            report.examProjection?.let { proj ->
                ExamProjectionCard(
                    goal = examGoal,
                    projection = proj,
                    onEditClick = { showGoalDialog = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 0.5 Focus Quality Score (FQS / Cognitive Quality Index)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PanelElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 Focus Quality Score (FQS)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (report.focusQualityTier) {
                                "Elite Cognitive Focus" -> Color(0xFF064E3B)
                                "Optimal Focus Stamina" -> Color(0xFF1E3A8A)
                                "Moderate Pacing" -> Color(0xFF78350F)
                                else -> Color(0xFF450A0A)
                            },
                            border = BorderStroke(1.dp, when (report.focusQualityTier) {
                                "Elite Cognitive Focus" -> SuccessGreen
                                "Optimal Focus Stamina" -> Color(0xFF3B82F6)
                                "Moderate Pacing" -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            })
                        ) {
                            Text(
                                text = if (report.totalMinutes > 0) report.focusQualityTier else "No Study Data",
                                color = when (report.focusQualityTier) {
                                    "Elite Cognitive Focus" -> SuccessGreen
                                    "Optimal Focus Stamina" -> Color(0xFF60A5FA)
                                    "Moderate Pacing" -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF87171)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${report.focusQualityScore}",
                            color = GoldBright,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = " / 100",
                            color = TextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((report.focusQualityScore.toFloat() / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(SuccessGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.consistencyPct}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Consistency", color = TextDim, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.goalHitRate}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Goal Hit", color = TextDim, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.pacingStability}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Pacing", color = TextDim, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.deepWorkRatio}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Deep Work", color = TextDim, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Formula: 0.35·DeepWork + 0.25·Consistency + 0.25·GoalHit + 0.15·Pacing",
                        color = TextDim,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 0.75 Cognitive Workload & Fatigue Index
            CognitiveFatigueCard(fatigue = report.fatigueReport)

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Circadian Peak Focus Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PanelElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val isNoStudy = report.peakFocusWindow.contains("No study", ignoreCase = true) || report.peakFocusWindow.contains("N/A", ignoreCase = true)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌅 Circadian Peak Focus",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isNoStudy) PanelDark else Color(0xFF2E2718),
                            border = BorderStroke(1.dp, if (isNoStudy) LineBorder else GoldAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isNoStudy) "No Study Data" else "Peak: ${report.peakFocusWindow}",
                                color = if (isNoStudy) TextDim else GoldBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "24-HOUR FOCUS DENSITY WAVE",
                        color = TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    CircadianDensityWaveCanvas(
                        hourlyMins = report.hourlyMins,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val maxCirc = (report.circadianBuckets.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
                    report.circadianBuckets.forEach { bucket ->
                        val pctFloat = (bucket.minutes.toFloat() / maxCirc).coerceIn(0f, 1f)
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = bucket.label, color = TextDim, fontSize = 11.sp)
                                Text(
                                    text = "${bucket.minutes}m (${bucket.percentageOfTotal}%)",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pctFloat)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(SuccessGreen)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Deep Work Stamina & Velocity Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PanelElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚡ Deep Work & Velocity",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.deepWorkRatio}%", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Deep Work (≥45m)", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.avgSessionMinutes}m", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Avg Duration", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            val sign = if (report.velocityPercentage >= 0) "+" else ""
                            val col = if (report.velocityPercentage >= 0) SuccessGreen else Color(0xFFEF4444)
                            Text(text = "$sign${report.velocityPercentage}%", color = col, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Velocity", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((report.deepWorkRatio.toFloat() / 100f).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldAccent)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${report.deepWorkMinutes} of ${report.totalMinutes} mins in sustained blocks",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Subject Equilibrium & Ebbinghaus Recall Matrix Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PanelElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚖️ Subject Equilibrium & Ebbinghaus Recall Matrix",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CURRICULUM EQUILIBRIUM RADAR POLYGON",
                        color = TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SubjectRadarCanvas(
                        subjects = report.subjectEquilibrium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (report.subjectEquilibrium.isEmpty()) {
                        Text(text = "No subjects recorded in this period.", color = TextDim, fontSize = 11.sp)
                    } else {
                        report.subjectEquilibrium.take(6).forEach { subj ->
                            val statusColor = when {
                                subj.retentionPct < 60 -> Color(0xFFEF4444)
                                subj.retentionPct < 80 -> Color(0xFFF59E0B)
                                else -> SuccessGreen
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = subj.subjectName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "(${subj.percentage}% share)", color = TextDim, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((subj.percentage.toFloat() / 100f).coerceIn(0f, 1f))
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(GoldAccent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "R: ${subj.retentionPct}% (S=${subj.stabilityDays}d)",
                                            color = statusColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(60.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth((subj.retentionPct.toFloat() / 100f).coerceIn(0f, 1f))
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(statusColor)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", subj.hours)}h",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val statusText = if (subj.daysAgo == 0) "Today" else if (subj.daysAgo == 1) "Yesterday" else "${subj.daysAgo}d ago"
                                    Text(
                                        text = subj.recallStatus,
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = statusText,
                                        color = TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// EXAM GOAL EDIT DIALOG
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamGoalEditDialog(
    currentGoal: ExamGoal,
    availableSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, targetDate: String, targetHours: Double, subjectScope: Set<String>) -> Unit
) {
    var name by remember(currentGoal) { mutableStateOf(currentGoal.examName) }
    var targetDate by remember(currentGoal) { mutableStateOf(currentGoal.targetDate) }
    var targetHoursText by remember(currentGoal) { mutableStateOf(currentGoal.targetHours.toString()) }
    var selectedScope by remember(currentGoal) { mutableStateOf(currentGoal.subjectScope) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Exam & Target",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exam / Goal Name", color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetDate,
                    onValueChange = { targetDate = it },
                    label = { Text("Target Date (YYYY-MM-DD)", color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetHoursText,
                    onValueChange = { targetHoursText = it },
                    label = { Text("Total Syllabus Target (Hours)", color = TextDim) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Subject Scope (optional)",
                    color = TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // All subjects chip
                val isAllSelected = selectedScope.isEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isAllSelected) GoldAccent else PanelElevated)
                        .border(1.dp, if (isAllSelected) GoldAccent else LineBorder, RoundedCornerShape(16.dp))
                        .clickable { selectedScope = emptySet() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "All Subjects",
                        color = if (isAllSelected) BgDark else TextDim,
                        fontSize = 11.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (availableSubjects.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSubjects.forEach { subj ->
                            val isSubjSelected = selectedScope.contains(subj)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSubjSelected) SuccessGreen else PanelElevated)
                                    .border(1.dp, if (isSubjSelected) SuccessGreen else LineBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedScope = if (isSubjSelected) {
                                            selectedScope - subj
                                        } else {
                                            selectedScope + subj
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = subj,
                                    color = if (isSubjSelected) BgDark else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSubjSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedHours = targetHoursText.toDoubleOrNull() ?: currentGoal.targetHours
                    val cleanDate = targetDate.trim().ifBlank { currentGoal.targetDate }
                    val cleanName = name.trim().ifBlank { currentGoal.examName }
                    onSave(cleanName, cleanDate, parsedHours, selectedScope)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
            ) {
                Text("Save Goal", color = BgDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark,
        shape = RoundedCornerShape(14.dp)
    )
}

// ============================================================
// EXAM & SYLLABUS TARGET PROJECTION CARD
// ============================================================
@Composable
private fun ExamProjectionCard(
    goal: ExamGoal,
    projection: ExamProjection,
    onEditClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "🎯 ${goal.examName}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Target Date: ${goal.targetDate}",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val daysRemaining = projection.daysRemaining
                    val (countdownText, countdownColor, countdownBg) = when {
                        daysRemaining > 0 -> Triple("$daysRemaining days left", GoldBright, Color(0xFF2E2718))
                        daysRemaining == 0 -> Triple("Target is Today", Color(0xFF60A5FA), Color(0xFF1E3A8A))
                        else -> Triple("${abs(daysRemaining)}d overdue", Color(0xFFF87171), Color(0xFF450A0A))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = countdownBg,
                        border = BorderStroke(1.dp, countdownColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = countdownText,
                            color = countdownColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Target Goal",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", projection.completedHours)}h / ${String.format(Locale.US, "%.0f", goal.targetHours)}h",
                    color = GoldBright,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${projection.progressPercentage}% Completed",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((projection.progressPercentage / 100f).coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(GoldAccent)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${String.format(Locale.US, "%.1f", projection.completedHours)}h", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Completed", color = TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${String.format(Locale.US, "%.1f", projection.remainingHours)}h", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Remaining", color = TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${String.format(Locale.US, "%.1f", projection.requiredDailyHours)}h/d", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Required Pace", color = TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${String.format(Locale.US, "%.1f", projection.currentDailyHours)}h/d", color = GoldBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "7d Velocity", color = TextDim, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Feasibility status badge & description
            val (statusText, statusBg, statusBorder, statusFg) = when (projection.status) {
                "COMPLETE" -> Quadruple("🎯 Goal Achieved (100%)", Color(0xFF064E3B), SuccessGreen, SuccessGreen)
                "ON_TRACK" -> Quadruple("✓ On Pace / Ahead (+${String.format(Locale.US, "%.1f", projection.paceDeltaHours)}h/d)", Color(0xFF064E3B), SuccessGreen, SuccessGreen)
                "MINOR_DEFICIT" -> Quadruple("⚠️ Minor Deficit (${String.format(Locale.US, "%.1f", projection.paceDeltaHours)}h/d)", Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFBBF24))
                "CRITICAL_LAG" -> Quadruple("🚨 Critical Deficit (${String.format(Locale.US, "%.1f", projection.paceDeltaHours)}h/d)", Color(0xFF450A0A), Color(0xFFEF4444), Color(0xFFF87171))
                "DEADLINE_TODAY" -> Quadruple("⏳ Target Date is Today", Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF60A5FA))
                else -> Quadruple("⚠️ Target Date Passed", Color(0xFF450A0A), Color(0xFFEF4444), Color(0xFFF87171))
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusBg,
                border = BorderStroke(1.dp, statusBorder.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = statusText,
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = projection.statusDescription,
                        color = statusFg.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// COGNITIVE WORKLOAD & FATIGUE INDEX CARD
// ============================================================
@Composable
private fun CognitiveFatigueCard(
    fatigue: FatigueReport
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ Cognitive Workload & Fatigue Index",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                val (tierBg, tierBorder, tierFg) = when (fatigue.fatigueTier) {
                    "OPTIMAL_RECOVERY" -> Triple(Color(0xFF064E3B), SuccessGreen, SuccessGreen)
                    "SUSTAINED_HIGH_LOAD" -> Triple(Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFBBF24))
                    else -> Triple(Color(0xFF450A0A), Color(0xFFEF4444), Color(0xFFF87171))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tierBg,
                    border = BorderStroke(1.dp, tierBorder)
                ) {
                    Text(
                        text = fatigue.tierLabel,
                        color = tierFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${fatigue.fatigueScore}",
                    color = when {
                        fatigue.fatigueScore < 40 -> SuccessGreen
                        fatigue.fatigueScore < 75 -> Color(0xFFFBBF24)
                        else -> Color(0xFFF87171)
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = " / 100",
                    color = TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((fatigue.fatigueScore.toFloat() / 100f).coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            when {
                                fatigue.fatigueScore < 40 -> SuccessGreen
                                fatigue.fatigueScore < 75 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${String.format(Locale.US, "%.1f", fatigue.avgDailyHours7d)}h/d", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "7d Daily Load", color = TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${fatigue.consecutiveHighDays}d", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "High Strain (≥5h)", color = TextDim, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${fatigue.recoveryDaysCount} / 7", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Recovery (<2h)", color = TextDim, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = fatigue.adviceText,
                color = TextDim,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ============================================================
// CIRCADIAN CONTINUOUS DENSITY WAVE CANVAS
// ============================================================
@Composable
private fun CircadianDensityWaveCanvas(
    hourlyMins: IntArray,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(148, 163, 184)
            textSize = with(density) { 8.5.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padLeft = 20.dp.toPx()
        val padRight = 14.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 18.dp.toPx()
        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom

        val maxMins = (hourlyMins.maxOrNull() ?: 10).coerceAtLeast(10).toFloat()

        val points = Array(24) { i ->
            val x = padLeft + (i.toFloat() / 23f) * plotW
            val y = padTop + plotH * (1f - (hourlyMins[i].toFloat() / maxMins))
            Offset(x, y)
        }

        // Draw area path
        val areaPath = Path().apply {
            moveTo(points[0].x, padTop + plotH)
            lineTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]

                val cp1x = p1.x + (p2.x - p0.x) / 6f
                var cp1y = p1.y + (p2.y - p0.y) / 6f
                val cp2x = p2.x - (p3.x - p1.x) / 6f
                var cp2y = p2.y - (p3.y - p1.y) / 6f

                val baseY = padTop + plotH
                if (cp1y > baseY) cp1y = baseY
                if (cp2y > baseY) cp2y = baseY

                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            }
            lineTo(points[23].x, padTop + plotH)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x5510B981),
                    Color(0x1810B981),
                    Color(0x0210B981)
                ),
                startY = padTop,
                endY = padTop + plotH
            )
        )

        // Draw stroke line
        val strokePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]

                val cp1x = p1.x + (p2.x - p0.x) / 6f
                var cp1y = p1.y + (p2.y - p0.y) / 6f
                val cp2x = p2.x - (p3.x - p1.x) / 6f
                var cp2y = p2.y - (p3.y - p1.y) / 6f

                val baseY = padTop + plotH
                if (cp1y > baseY) cp1y = baseY
                if (cp2y > baseY) cp2y = baseY

                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            }
        }

        drawPath(
            path = strokePath,
            color = Color(0xFF34D399),
            style = Stroke(width = 1.8.dp.toPx())
        )

        // Peak point marker
        var peakIdx = 0
        var peakVal = 0
        hourlyMins.forEachIndexed { idx, v ->
            if (v > peakVal) {
                peakVal = v
                peakIdx = idx
            }
        }

        if (peakVal > 0) {
            val peakPt = points[peakIdx]
            drawCircle(
                color = Color(0x5534D399),
                radius = 6.dp.toPx(),
                center = peakPt
            )
            drawCircle(
                color = Color(0xFF10B981),
                radius = 3.dp.toPx(),
                center = peakPt
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = peakPt,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Hour labels on X-axis using nativeCanvas
        val hourTicks = listOf(0, 4, 8, 12, 16, 20, 23)
        hourTicks.forEach { hIdx ->
            val pt = points[hIdx]
            val ampm = if (hIdx >= 12) "p" else "a"
            val hr = if (hIdx % 12 == 0) 12 else hIdx % 12
            drawContext.canvas.nativeCanvas.drawText(
                "$hr$ampm",
                pt.x,
                h - 2.dp.toPx(),
                textPaint
            )
        }
    }
}

// ============================================================
// SUBJECT EQUILIBRIUM RADAR POLYGON CANVAS
// ============================================================
@Composable
private fun SubjectRadarCanvas(
    subjects: List<SubjectEquilibriumItem>,
    modifier: Modifier = Modifier
) {
    if (subjects.size < 3) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(8.dp))
                .border(1.dp, LineBorder, RoundedCornerShape(8.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⚖️", fontSize = 22.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Polygonal Radar Standby",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Radar web polygon requires 3+ subjects (currently ${subjects.size} recorded). Comparative balance bars are displayed below.",
                    color = TextDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val topSubjects = subjects.take(8)
    val n = topSubjects.size

    val density = LocalDensity.current
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(241, 245, 249)
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) - 30.dp.toPx()

        // 1. Concentric Web Rings (25%, 50%, 75%, 100%)
        val rings = listOf(0.25f, 0.50f, 0.75f, 1.0f)
        rings.forEachIndexed { index, pct ->
            val r = radius * pct
            val ringPath = Path().apply {
                for (i in 0 until n) {
                    val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = ringPath,
                color = if (index == 3) Color(0x33FFFFFF) else Color(0x15FFFFFF),
                style = Stroke(width = if (index == 3) 1.2.dp.toPx() else 0.8.dp.toPx())
            )
        }

        // 2. Spokes
        for (i in 0 until n) {
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            drawLine(
                color = Color(0x1EFFFFFF),
                start = Offset(cx, cy),
                end = Offset(x, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 3. Data Polygon
        val maxMins = maxOf(topSubjects.maxOfOrNull { it.minutes } ?: 1, 1).toFloat()
        val dataPoints = topSubjects.mapIndexed { i, s ->
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
            val norm = (s.minutes.toFloat() / maxMins).coerceIn(0.08f, 1.0f)
            val r = radius * norm
            Offset(cx + (r * cos(angle)).toFloat(), cy + (r * sin(angle)).toFloat())
        }

        val dataPath = Path().apply {
            dataPoints.forEachIndexed { i, pt ->
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }

        drawPath(
            path = dataPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0x5510B981), Color(0x2210B981)),
                center = Offset(cx, cy),
                radius = radius
            )
        )
        drawPath(
            path = dataPath,
            color = Color(0xFF34D399),
            style = Stroke(width = 2.dp.toPx())
        )

        // 4. Vertex dots & labels
        dataPoints.forEachIndexed { i, pt ->
            drawCircle(color = Color(0xFF10B981), radius = 3.dp.toPx(), center = pt)
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = pt, style = Stroke(width = 1.dp.toPx()))

            val subj = topSubjects[i]
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n
            val labelDist = radius + 14.dp.toPx()
            val lx = cx + (labelDist * cos(angle)).toFloat()
            val ly = cy + (labelDist * sin(angle)).toFloat()
            val cleanName = if (subj.subjectName.length > 9) subj.subjectName.take(8) + ".." else subj.subjectName

            drawContext.canvas.nativeCanvas.drawText(
                cleanName,
                lx,
                ly + 3.dp.toPx(),
                labelPaint
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


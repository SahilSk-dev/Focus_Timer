package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    var badgeFilter by remember { mutableStateOf("all") } // "all", "unlocked", "locked"
    var selectedBadgeForDetail by remember { mutableStateOf<MilestoneBadge?>(null) }
    var showTargetEditDialog by remember { mutableStateOf(false) }

    val todayStr = remember { FocusRepository.getTodayString() }
    val studySessions = remember(allSessions) { allSessions.filter { !it.isNonStudy } }

    // Day Streak calculation (Computed asynchronously on Dispatchers.Default)
    val streakPair by produceState(
        initialValue = 0 to 0,
        studySessions
    ) {
        value = withContext(Dispatchers.Default) {
            calculateStreaks(studySessions)
        }
    }
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

    // Week comparison (Computed asynchronously on Dispatchers.Default)
    val weekComparison by produceState(
        initialValue = "",
        studySessions
    ) {
        value = withContext(Dispatchers.Default) {
            calculateWeekComparison(studySessions)
        }
    }

    // Last 7 days ranking (Computed asynchronously on Dispatchers.Default)
    val rankingList by produceState(
        initialValue = emptyList(),
        studySessions
    ) {
        value = withContext(Dispatchers.Default) {
            calculate7DaysRanking(studySessions)
        }
    }

    // Milestone Badges calculation (Computed asynchronously on Dispatchers.Default)
    val allBadges by produceState(
        initialValue = emptyList(),
        studySessions, currentStreak, bestStreak, totalStudyMinutes
    ) {
        value = withContext(Dispatchers.Default) {
            calculateMilestoneBadges(studySessions, currentStreak, bestStreak, totalStudyMinutes)
        }
    }
    val unlockedBadgeCount = remember(allBadges) { allBadges.count { it.isUnlocked } }
    val displayedBadges = remember(allBadges, badgeFilter) {
        when (badgeFilter) {
            "unlocked" -> allBadges.filter { it.isUnlocked }
            "locked" -> allBadges.filter { !it.isUnlocked }
            else -> allBadges
        }
    }

    // Cognitive Deep Analysis Engine computation (Computed asynchronously on Dispatchers.Default)
    var analyticsTimeframe by remember { mutableStateOf(AnalyticsTimeframe.LAST_7_DAYS) }
    val analyticsReport by produceState(
        initialValue = AnalyticsReport.empty(analyticsTimeframe),
        studySessions, analyticsTimeframe
    ) {
        value = withContext(Dispatchers.Default) {
            AnalyticsEngine.computeReport(studySessions, analyticsTimeframe)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(targetPct / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(GoldGradientBrush)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$targetPct% achieved", color = GoldLight, fontSize = 10.sp)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(xpPct / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SuccessGreen)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$currentXp / $xpPerLevel XP", color = TextDim, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 3. MILESTONES & BADGES (Milestone Rewards Section)
        // ============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Milestones & Badges",
                    style = TextStyle(
                        brush = GoldGradientBrush,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Earn rewards by hitting focus goals",
                    color = TextDim,
                    fontSize = 11.sp
                )
            }

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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badge Filter Tabs (All / Earned / Locked)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("all", "All (${allBadges.size})", allBadges.size),
                Triple("unlocked", "Earned ($unlockedBadgeCount)", unlockedBadgeCount),
                Triple("locked", "Locked (${allBadges.size - unlockedBadgeCount})", allBadges.size - unlockedBadgeCount)
            ).forEach { (key, label, count) ->
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

        // Badges 2-Column Responsive Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            displayedBadges.forEach { badge ->
                BadgeCard(
                    badge = badge,
                    onClick = { selectedBadgeForDetail = badge },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ============================================================
        // 3.5 COGNITIVE DEEP ANALYSIS ENGINE
        // ============================================================
        DeepAnalysisEngineSection(
            report = analyticsReport,
            currentTimeframe = analyticsTimeframe,
            onTimeframeSelected = { analyticsTimeframe = it },
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
                    val todaySess = studySessions.filter { it.date == todayStr }
                    val bySubj = todaySess.groupBy { it.subject }.mapValues { it.value.sumOf { s -> s.minutes } }
                    val maxVal = bySubj.values.maxOrNull() ?: 1

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
                    val totals = last7Days.map { d ->
                        studySessions.filter { it.date == d.dateStr }.sumOf { it.minutes }
                    }
                    val grandTotal = totals.sum()
                    val maxVal = (totals.maxOrNull() ?: 1).coerceAtLeast(1)

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
                    Text(text = "Less", color = TextDim, fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF1C1C1A), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF4A3A1A), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF7A5F22), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFC9962F), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "More", color = TextDim, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // ============================================================
    // DIALOGS: BADGE DETAIL & DAILY TARGET EDIT
    // ============================================================
    selectedBadgeForDetail?.let { badge ->
        AlertDialog(
            onDismissRequest = { selectedBadgeForDetail = null },
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (badge.isUnlocked) GoldGradientBrush else SolidColor(SuccessGreen))
                        )
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
                TextButton(onClick = { selectedBadgeForDetail = null }) {
                    Text("Done", color = GoldBright, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PanelDark
        )
    }

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
// COMPONENT: BADGE CARD
// ============================================================
@Composable
private fun BadgeCard(
    badge: MilestoneBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = ((badge.currentVal.toFloat() / badge.targetVal) * 100).toInt().coerceIn(0, 100)

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
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (badge.isUnlocked) GoldGradientBrush else SolidColor(SuccessGreen))
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${badge.currentVal}/${badge.targetVal} ${badge.unit}",
                color = if (badge.isUnlocked) GoldLight else TextDim,
                fontSize = 9.sp
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
        val list = mutableListOf<Triple<String, Int, Int>>() // date, minutes, level
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in days - 1 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = sdf.format(c.time)
            val mins = totalsByDate[dStr] ?: 0
            val lvl = when {
                mins == 0 -> 0
                mins <= 30 -> 1
                mins <= 60 -> 2
                else -> 3
            }
            list.add(Triple(dStr, mins, lvl))
        }
        list
    }

    val colors = listOf(
        Color(0xFF1C1C1A),
        Color(0xFF4A3A1A),
        Color(0xFF7A5F22),
        Color(0xFFC9962F)
    )

    // 12 columns x 7 rows
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (col in 0 until 12) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 7) {
                    val index = col * 7 + row
                    if (index < cells.size) {
                        val cell = cells[index]
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors[cell.third])
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
private data class DayInfo(val dateStr: String, val dayLabel: String)
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

@Composable
private fun DeepAnalysisEngineSection(
    report: AnalyticsReport,
    currentTimeframe: AnalyticsTimeframe,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit,
    onExportPdf: () -> Unit
) {
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            Text(text = "Deep Work (≥45m)", color = TextDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = "${report.avgSessionMinutes}m", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Avg Duration", color = TextDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            val sign = if (report.velocityPercentage >= 0) "+" else ""
                            val col = if (report.velocityPercentage >= 0) SuccessGreen else Color(0xFFEF4444)
                            Text(text = "$sign${report.velocityPercentage}%", color = col, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Velocity", color = TextDim, fontSize = 10.sp, textAlign = TextAlign.Center)
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
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Subject Equilibrium & Neglect Matrix Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PanelElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚖️ Subject Equilibrium & Neglect Matrix",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (report.subjectEquilibrium.isEmpty()) {
                        Text(text = "No subjects recorded in this period.", color = TextDim, fontSize = 11.sp)
                    } else {
                        report.subjectEquilibrium.take(6).forEach { subj ->
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
                                        Text(text = "(${subj.percentage}%)", color = TextDim, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
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
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", subj.hours)}h",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val statusText = if (subj.daysAgo == 0) "Today" else if (subj.daysAgo == 1) "Yesterday" else "${subj.daysAgo}d ago"
                                    val statusColor = if (subj.isNeglected) Color(0xFFEF4444) else SuccessGreen
                                    Text(
                                        text = if (subj.isNeglected) "⚠️ $statusText" else statusText,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
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

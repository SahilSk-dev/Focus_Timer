package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.util.PdfExportHelper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudySessionEntity
import com.example.data.repository.FocusRepository
import com.example.ui.theme.BgDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldFlame
import com.example.ui.theme.GoldGradientBrush
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LineBorder
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.FocusViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatsScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSessions by viewModel.allSessions.collectAsState()
    val dailyTarget by viewModel.dailyTargetMinutes.collectAsState()

    var statsTab by remember { mutableStateOf("daily") } // "daily" or "weekly"

    val todayStr = remember { FocusRepository.getTodayString() }
    val studySessions = remember(allSessions) { allSessions.filter { !it.isNonStudy } }

    // Day Streak calculation
    val (currentStreak, bestStreak) = remember(studySessions) {
        calculateStreaks(studySessions)
    }

    // Daily Target progress
    val todayDoneMinutes = remember(studySessions, todayStr) {
        studySessions.filter { it.date == todayStr }.sumOf { it.minutes }
    }
    val targetPct = remember(todayDoneMinutes, dailyTarget) {
        if (dailyTarget > 0) ((todayDoneMinutes.toFloat() / dailyTarget) * 100).toInt().coerceAtMost(100) else 0
    }

    // Level & XP
    val totalStudyMinutes = remember(studySessions) { studySessions.sumOf { it.minutes } }
    val xpPerLevel = 300
    val level = remember(totalStudyMinutes) { (totalStudyMinutes / xpPerLevel) + 1 }
    val currentXp = remember(totalStudyMinutes) { totalStudyMinutes % xpPerLevel }
    val xpPct = remember(currentXp) { ((currentXp.toFloat() / xpPerLevel) * 100).toInt().coerceIn(0, 100) }

    // Week comparison
    val weekComparison = remember(studySessions) {
        calculateWeekComparison(studySessions)
    }

    // Last 7 days ranking
    val rankingList = remember(studySessions) {
        calculate7DaysRanking(studySessions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Stats Title & PDF Download
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stats & Analytics",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            )

            Surface(
                onClick = {
                    if (studySessions.isEmpty()) {
                        viewModel.showToast("No study sessions recorded yet")
                    } else {
                        try {
                            PdfExportHelper.generateAndSharePdf(context, studySessions, "All Time")
                        } catch (e: Exception) {
                            viewModel.showToast("Failed to generate PDF: ${e.localizedMessage}")
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = PanelElevated,
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Download PDF",
                        tint = GoldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PDF Report",
                        color = GoldBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 1. DAY STREAK CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = currentStreak.toString(),
                            style = TextStyle(
                                brush = GoldGradientBrush,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Day Streak",
                            color = TextDim,
                            fontSize = 12.sp,
                            
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Best Streak",
                        color = TextDim,
                        fontSize = 11.sp,
                        
                    )
                    Text(
                        text = "$bestStreak days",
                        color = GoldBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. DAILY TARGET CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Target",
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Target ", color = TextDim, fontSize = 12.sp)
                        var tempTarget by remember(dailyTarget) { mutableStateOf(dailyTarget.toString()) }
                        BasicTextField(
                            value = tempTarget,
                            onValueChange = { str ->
                                tempTarget = str
                                val num = str.toIntOrNull()
                                if (num != null && num >= 10) {
                                    viewModel.setDailyTarget(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                color = GoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(GoldBright),
                            modifier = Modifier
                                .width(50.dp)
                                .background(PanelElevated, RoundedCornerShape(4.dp))
                                .border(1.dp, LineBorder, RoundedCornerShape(4.dp))
                                .padding(vertical = 3.dp)
                                .testTag("daily_target_input")
                        )
                        Text(text = " mins", color = TextDim, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LineBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(targetPct / 100f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GoldGradientBrush)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "$todayDoneMinutes mins", color = TextDim, fontSize = 11.sp)
                    Text(text = "$targetPct%", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. LEVEL & XP CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Level", color = GoldLight, fontSize = 12.sp, )
                        Text(
                            text = "Level $level",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "$currentXp / $xpPerLevel mins",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LineBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(xpPct / 100f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen)
                    )
                }
            }
        }

        // Week comparison line
        if (weekComparison.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = weekComparison,
                color = GoldLight,
                fontSize = 12.sp,
                
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. DAILY / WEEKLY CHART TABS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stats Chart",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    
                )
            )
            Row(
                modifier = Modifier
                    .background(PanelElevated, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (statsTab == "daily") GoldAccent else Color.Transparent)
                        .clickable { statsTab = "daily" }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Daily",
                        color = if (statsTab == "daily") BgDark else TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (statsTab == "weekly") GoldAccent else Color.Transparent)
                        .clickable { statsTab = "weekly" }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Weekly",
                        color = if (statsTab == "weekly") BgDark else TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Render Chart
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (statsTab == "daily") {
                    val todaySess = studySessions.filter { it.date == todayStr }
                    val bySubj = todaySess.groupBy { it.subject }.mapValues { it.value.sumOf { s -> s.minutes } }
                    val maxVal = bySubj.values.maxOrNull() ?: 1

                    if (bySubj.isEmpty()) {
                        Text(
                            text = "No study sessions recorded today yet",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        bySubj.entries.sortedByDescending { it.value }.forEach { entry ->
                            BarChartRow(
                                label = entry.key,
                                minutes = entry.value,
                                pct = (entry.value.toFloat() / maxVal).coerceIn(0.06f, 1f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Total time today: $todayDoneMinutes mins",
                            color = GoldBright,
                            fontSize = 13.sp,
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
                            text = "No study sessions recorded in the last 7 days",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        last7Days.forEachIndexed { idx, d ->
                            val mins = totals[idx]
                            BarChartRow(
                                label = d.dayLabel,
                                minutes = mins,
                                pct = if (mins == 0) 0f else (mins.toFloat() / maxVal).coerceIn(0.06f, 1f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        HorizontalDivider(color = LineBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Total in last 7 days: $grandTotal mins",
                            color = GoldBright,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. LAST 7 DAYS RANKING & BADGES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last 7 Days Ranking",
                style = TextStyle(
                    brush = GoldGradientBrush,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    
                )
            )
            Text(text = "🏆 Badges", color = GoldAccent, fontSize = 12.sp)
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
                    Text(text = "No study in the last 7 days.", color = TextDim, fontSize = 12.sp)
                } else {
                    rankingList.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeEmoji = when (index) {
                                0 -> "🏆"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${index + 1}"
                            }
                            Text(
                                text = badgeEmoji,
                                fontSize = if (index < 3) 16.sp else 12.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                text = item.subjectName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.minutes} mins",
                                color = GoldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. LAST 12 WEEKS HEATMAP (84 DAYS)
        Text(
            text = "Last 12 Weeks Heatmap",
            style = TextStyle(
                brush = GoldGradientBrush,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                
            ),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PanelDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                HeatmapGrid(sessions = studySessions)

                Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun BarChartRow(
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
            
            modifier = Modifier.width(80.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(PanelElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(GoldGradientBrush)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$minutes m",
            color = GoldLight,
            fontSize = 11.sp,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HeatmapGrid(sessions: List<StudySessionEntity>) {
    val totalsByDate = remember(sessions) {
        sessions.groupBy { it.date }.mapValues { it.value.sumOf { s -> s.minutes } }
    }

    val days = 84 // 12 weeks
    val cells = remember(totalsByDate) {
        val list = mutableListOf<Triple<String, Int, Int>>() // date, minutes, level
        val cal = Calendar.getInstance()
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

private data class DayInfo(val dateStr: String, val dayLabel: String)
private data class RankItem(val subjectName: String, val minutes: Int)

private fun getLast7Days(): List<DayInfo> {
    val list = mutableListOf<DayInfo>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    for (i in 6 downTo 0) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        val dStr = sdf.format(cal.time)
        val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
        list.add(DayInfo(dStr, dayNames.getOrElse(dayIndex) { "" }))
    }
    return list
}

private fun calculateStreaks(sessions: List<StudySessionEntity>): Pair<Int, Int> {
    val uniqueDates = sessions.map { it.date }.distinct().sorted()
    if (uniqueDates.isEmpty()) return 0 to 0

    val dateSet = uniqueDates.toSet()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Current streak
    var current = 0
    val cal = Calendar.getInstance()
    if (!dateSet.contains(sdf.format(cal.time))) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    while (dateSet.contains(sdf.format(cal.time))) {
        current++
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }

    // Best streak
    var best = 1
    var run = 1
    for (i in 1 until uniqueDates.size) {
        val d1 = sdf.parse(uniqueDates[i - 1]) ?: continue
        val d2 = sdf.parse(uniqueDates[i]) ?: continue
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
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val thisWeekDays = (0..6).map {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -it)
        sdf.format(c.time)
    }.toSet()
    val lastWeekDays = (7..13).map {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -it)
        sdf.format(c.time)
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
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sevenDaysAgoStr = sdf.format(cal.time)

    val recent = sessions.filter { it.date >= sevenDaysAgoStr }
    val map = mutableMapOf<String, Int>()
    recent.forEach { s ->
        val mainSubj = s.subject.split(" - ").firstOrNull() ?: s.subject
        map[mainSubj] = (map[mainSubj] ?: 0) + s.minutes
    }
    return map.entries.sortedByDescending { it.value }.map { RankItem(it.key, it.value) }
}

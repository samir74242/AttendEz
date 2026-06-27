package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DailyBiometricRecord
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricAttendanceSheet(
    dailyRecords: List<DailyBiometricRecord>,
    targetGoal: Int, // e.g. 75
    themeMode: String,
    onDismissRequest: () -> Unit,
    onUpdateBiometric: (String, String) -> Unit,
    onClearAll: () -> Unit
) {
    val isTechGradient = themeMode == "TECH_GRADIENT"
    val techGradientBrush = Brush.linearGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF9333EA)))
    
    // Background colors based on theme tokens
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    // 1. Math Statistics computations
    val presentCount = dailyRecords.count { it.status == "PRESENT" }
    val absentCount = dailyRecords.count { it.status == "ABSENT" }
    val noClassCount = dailyRecords.count { it.status == "NO_CLASS" }
    val totalTrackedDays = presentCount + absentCount
    
    val overallPercentage = if (totalTrackedDays > 0) {
        (presentCount.toFloat() / totalTrackedDays) * 100f
    } else {
        0f
    }
    
    val flagOnTrack = overallPercentage >= targetGoal
    val trendIcon = if (flagOnTrack) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    val trendColor = if (flagOnTrack) StatusGreen else StatusRed
    val deltaPercent = overallPercentage - targetGoal
    val deltaText = if (deltaPercent >= 0) {
        String.format("%.1f%% above target", deltaPercent)
    } else {
        String.format("%.1f%% below target", kotlin.math.abs(deltaPercent))
    }

    // 2. Streaks computation
    var currentStreak = 0
    var bestStreak = 0
    
    // Sort records chronologically ascending (filter out Sunday exceptions)
    val chronologicalActive = dailyRecords
        .filter { (it.status == "PRESENT" || it.status == "ABSENT") && !isSunday(it.date) }
        .sortedBy { it.date }
        
    var runningStreak = 0
    for (rec in chronologicalActive) {
        if (rec.status == "PRESENT") {
            runningStreak++
            if (runningStreak > bestStreak) {
                bestStreak = runningStreak
            }
        } else {
            runningStreak = 0
        }
    }
    
    // Calculate current streak backwards from today
    var activeCurrent = 0
    for (i in chronologicalActive.indices.reversed()) {
        if (chronologicalActive[i].status == "PRESENT") {
            activeCurrent++
        } else {
            break
        }
    }
    currentStreak = activeCurrent

    // 3. Target dynamic predictions
    var targetCalculatorTitle = ""
    var targetCalculatorSubtitle = ""
    
    if (totalTrackedDays == 0) {
        targetCalculatorTitle = "Sync with settings"
        targetCalculatorSubtitle = "Start tracking daily attendance."
    } else {
        val targetDecimal = targetGoal / 100f
        if (overallPercentage >= targetGoal) {
            val maxSkip = if (targetDecimal > 0f) {
                (presentCount.toFloat() / targetDecimal - totalTrackedDays).toInt()
            } else {
                0
            }
            val skipCount = maxOf(0, maxSkip)
            if (skipCount > 0) {
                targetCalculatorTitle = "Can skip $skipCount days"
                targetCalculatorSubtitle = "consecutively while staying above target %"
            } else {
                targetCalculatorTitle = "Keep current pace"
                targetCalculatorSubtitle = "Currently at target limit"
            }
        } else {
            val needed = if (targetDecimal < 1f) {
                val num = (targetDecimal * totalTrackedDays) - presentCount
                val den = 1f - targetDecimal
                val minN = kotlin.math.ceil(num / den).toInt()
                maxOf(1, minN)
            } else {
                1
            }
            targetCalculatorTitle = "Need $needed more days"
            targetCalculatorSubtitle = "consecutive presence to climb back up to target"
        }
    }

    // 4. Calendar State
    var calendarNavMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var showEditDialogForDate by remember { mutableStateOf<String?>(null) }
    
    // Header format
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    val displayCurrentMonthStr = monthYearFormat.format(calendarNavMonth.time)

    // Build month cells
    val monthCells = remember(calendarNavMonth, dailyRecords) {
        getCalendarCellDetails(calendarNavMonth)
    }

    // Month specific mini metrics
    val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(calendarNavMonth.time)
    val monthRecords = dailyRecords.filter { it.date.startsWith(currentMonthKey) }
    val mPresent = monthRecords.count { it.status == "PRESENT" }
    val mAbsent = monthRecords.count { it.status == "ABSENT" }
    val mNoClass = monthRecords.count { it.status == "NO_CLASS" }
    val mTotalActive = mPresent + mAbsent
    val mPercent = if (mTotalActive > 0) (mPresent.toFloat() / mTotalActive) * 100f else 100f

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        // Scaffold look: slide up surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismissRequest)
        ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clickable(enabled = false, onClick = {}) // absorb clicks
                .testTag("biometric_bottom_sheet"),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Bottom Sheet Notch Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .size(40.dp, 4.dp)
                        .background(outlineVariantColor.copy(alpha = 0.6f), CircleShape)
                )

                // Sheet Header with Fingerprint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(primaryColor.copy(alpha = 0.15f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Header Icon",
                                tint = primaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Biometric Attendance",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Overall campus daily presence logs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("biometric_sheet_reset_btn")
                        ) {
                            Text(
                                "Reset Logs",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("biometric_sheet_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Sheet",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                // Divider
                HorizontalDivider(color = outlineVariantColor.copy(alpha = 0.5f))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // 1. Overview Stats (3-card grid)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Days tracked
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stats_total_days_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = surfaceColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Total Days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$totalTrackedDays",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        // Present days
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stats_present_days_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = surfaceColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Present",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$presentCount",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = StatusGreen
                                )
                            }
                        }

                        // Absent days
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stats_absent_days_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = surfaceColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Absent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$absentCount",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = StatusRed
                                )
                            }
                        }
                    }

                    // 2. Percentage Card with Progress Bar
                    val percentageCardModifier = if (isTechGradient) {
                        Modifier
                            .fillMaxWidth()
                            .border(width = 1.5.dp, brush = techGradientBrush, shape = RoundedCornerShape(20.dp))
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = outlineVariantColor.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                    }

                    Card(
                        modifier = percentageCardModifier.testTag("biometric_percentage_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Overall Biom Presence",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%.1f %%", overallPercentage),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = trendIcon,
                                            contentDescription = "Trend Icon",
                                            tint = trendColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = trendColor.copy(alpha = 0.12f)
                                    )
                                ) {
                                    Text(
                                        text = deltaText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = trendColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Custom progress bar
                            val currentBarColor = trendColor
                            val barBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(barBackground)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(overallPercentage / 100f)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(currentBarColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Settings Goal: $targetGoal%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (flagOnTrack) "On Track 🎉" else "Action Needed ⚠",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = trendColor
                                )
                            }
                        }
                    }

                    // 3. Streak & Target Row (2 cards side by side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Fire Streak Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("biometric_streak_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = surfaceColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🔥 Streak",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = StatusOrange
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$currentStreak",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = " days",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Best: $bestStreak days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Target Calculator Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("biometric_target_calc_card"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = surfaceColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "Target icon",
                                        tint = primaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Target Calc",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = primaryColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = targetCalculatorTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = targetCalculatorSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    maxLines = 2,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }

                    // 4. Interactive Calendar View
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_calendar_card"),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.4f)),
                        colors = CardDefaults.cardColors(containerColor = surfaceColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Month Navigator header (Prev / Next buttons)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayCurrentMonthStr,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val clone = calendarNavMonth.clone() as Calendar
                                            clone.add(Calendar.MONTH, -1)
                                            calendarNavMonth = clone
                                        },
                                        modifier = Modifier.size(36.dp).testTag("calendar_prev_month_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = "Previous Month"
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            val clone = calendarNavMonth.clone() as Calendar
                                            clone.add(Calendar.MONTH, 1)
                                            calendarNavMonth = clone
                                        },
                                        modifier = Modifier.size(36.dp).testTag("calendar_next_month_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Next Month"
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                    .padding(vertical = 6.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Quick Action Information",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Instant Quick Mark:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("✓", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text("Pres", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("✗", color = StatusRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text("Abs", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sun-Sat Day Headers
                            val dayShorts = listOf("S", "M", "T", "W", "T", "F", "S")
                            Row(modifier = Modifier.fillMaxWidth()) {
                                dayShorts.forEach { dayLetter ->
                                    Text(
                                        text = dayLetter,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Grid of monthly cells
                            var cellIndex = 0
                            val rows = monthCells.chunked(7)
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                rows.forEach { rowCells ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowCells.forEach { cellDate ->
                                            if (cellDate == null) {
                                                // Blank placeholder cell
                                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                            } else {
                                                val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cellDate)
                                                val databaseRecordForThisDay = dailyRecords.find { it.date == dateString }
                                                
                                                // Determine dates comparisons
                                                val todayCal = Calendar.getInstance()
                                                val cellCal = Calendar.getInstance().apply { time = cellDate }
                                                
                                                val isToday = todayCal.get(Calendar.YEAR) == cellCal.get(Calendar.YEAR) &&
                                                        todayCal.get(Calendar.DAY_OF_YEAR) == cellCal.get(Calendar.DAY_OF_YEAR)
                                                
                                                val isFuture = cellCal.after(todayCal) && !isToday

                                                // Background / text styling for records
                                                val (bgColor, borderStroke, textColor) = getCellThemeColors(
                                                    status = databaseRecordForThisDay?.status,
                                                    isToday = isToday,
                                                    isFuture = isFuture,
                                                    primaryRingColor = primaryColor,
                                                    outlineVariantColor = outlineVariantColor
                                                )

                                                val status = databaseRecordForThisDay?.status
                                                val cellIsSunday = cellCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                                                // Animated styling for smooth transitions
                                                val animatedBgColor by animateColorAsState(targetValue = bgColor, label = "cell_bg_anim")
                                                val animatedTextColor by animateColorAsState(targetValue = textColor, label = "cell_text_anim")

                                                val targetBorderColor = when {
                                                    isToday -> primaryColor
                                                    status == "PRESENT" -> StatusGreen.copy(alpha = 0.4f)
                                                    status == "ABSENT" -> StatusRed.copy(alpha = 0.4f)
                                                    status == "NO_CLASS" -> outlineVariantColor.copy(alpha = 0.5f)
                                                    else -> outlineVariantColor.copy(alpha = 0.25f)
                                                }
                                                val targetBorderWidth = if (isToday) 2.2.dp else 1.dp
                                                
                                                val animatedBorderColorState = animateColorAsState(targetValue = targetBorderColor, label = "cell_border_color_anim")
                                                val animatedBorderWidthState = animateDpAsState(targetValue = targetBorderWidth, label = "cell_border_width_anim")

                                                val finalBorder = remember(animatedBorderWidthState.value, animatedBorderColorState.value) {
                                                    if (animatedBorderWidthState.value.value > 0f) {
                                                        BorderStroke(animatedBorderWidthState.value, animatedBorderColorState.value)
                                                    } else {
                                                        null
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(animatedBgColor)
                                                        .then(if (finalBorder != null) Modifier.border(finalBorder, RoundedCornerShape(8.dp)) else Modifier)
                                                        .testTag("calendar_day_$dateString")
                                                ) {
                                                    if (isFuture) {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "${cellCal.get(Calendar.DAY_OF_MONTH)}",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = animatedTextColor
                                                            )
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1.1f)
                                                                .fillMaxWidth()
                                                                .clickable { showEditDialogForDate = dateString },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(
                                                                    text = "${cellCal.get(Calendar.DAY_OF_MONTH)}",
                                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                                                    ),
                                                                    color = animatedTextColor
                                                                )
                                                                if (cellIsSunday) {
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text(
                                                                        text = "☀️",
                                                                        fontSize = 10.sp
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(20.dp)
                                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // PRESENT (✓) BUTTON WITH TOGGLE ACTION & ANIMATION
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .fillMaxHeight()
                                                                    .clickable {
                                                                        if (status == "PRESENT") {
                                                                            onUpdateBiometric(dateString, "NONE")
                                                                        } else {
                                                                            onUpdateBiometric(dateString, "PRESENT")
                                                                        }
                                                                    }
                                                                    .testTag("quick_present_$dateString"),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "✓",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp,
                                                                    color = if (status == "PRESENT") StatusGreen else animatedTextColor.copy(alpha = 0.4f)
                                                                )
                                                            }

                                                            // Divider
                                                            Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(outlineVariantColor.copy(alpha = 0.3f)))

                                                            // ABSENT (✗) BUTTON WITH TOGGLE ACTION & ANIMATION
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .fillMaxHeight()
                                                                    .clickable {
                                                                        if (status == "ABSENT") {
                                                                            onUpdateBiometric(dateString, "NONE")
                                                                        } else {
                                                                            onUpdateBiometric(dateString, "ABSENT")
                                                                        }
                                                                    }
                                                                    .testTag("quick_absent_$dateString"),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "✗",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.sp,
                                                                    color = if (status == "ABSENT") StatusRed else animatedTextColor.copy(alpha = 0.4f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Fill up remaining days if row is not full
                                        if (rowCells.size < 7) {
                                            for (fill in 0 until (7 - rowCells.size)) {
                                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Per-month mini stats counts + percent
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text(
                                            "Month Statistics",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusGreen))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("$mPresent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusRed))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("$mAbsent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("$mNoClass", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(0.7f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "Month Presence",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = String.format("%.0f%% PRES", mPercent),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = if (mPercent >= targetGoal) StatusGreen else StatusRed
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

    // 5. Edit Dialog
    showEditDialogForDate?.let { dateStr ->
        val formattedFullDate = remember(dateStr) {
            try {
                val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val outputSdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US)
                val parsed = inputSdf.parse(dateStr)
                outputSdf.format(parsed)
            } catch (e: Exception) {
                dateStr
            }
        }

        // Current status for highlight
        val currentStatus = dailyRecords.find { it.date == dateStr }?.status ?: "NONE"

        Dialog(onDismissRequest = { showEditDialogForDate = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biometric_edit_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Calendar Picker",
                        tint = primaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Mark Campus Presence",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedFullDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // 3 Buttons layout: Present, Absent, No Class
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // PRESENT BUTTON
                        Button(
                            onClick = {
                                if (currentStatus == "PRESENT") {
                                    onUpdateBiometric(dateStr, "NONE")
                                } else {
                                    onUpdateBiometric(dateStr, "PRESENT")
                                }
                                showEditDialogForDate = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("picker_present_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "PRESENT") StatusGreen else StatusGreen.copy(alpha = 0.12f),
                                contentColor = if (currentStatus == "PRESENT") Color.White else StatusGreen
                            ),
                            border = BorderStroke(1.dp, StatusGreen)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = "Present")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Present ✓", fontWeight = FontWeight.Bold)
                            }
                        }

                        // ABSENT BUTTON
                        Button(
                            onClick = {
                                if (currentStatus == "ABSENT") {
                                    onUpdateBiometric(dateStr, "NONE")
                                } else {
                                    onUpdateBiometric(dateStr, "ABSENT")
                                }
                                showEditDialogForDate = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("picker_absent_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStatus == "ABSENT") StatusRed else StatusRed.copy(alpha = 0.12f),
                                contentColor = if (currentStatus == "ABSENT") Color.White else StatusRed
                            ),
                            border = BorderStroke(1.dp, StatusRed)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Close, contentDescription = "Absent")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Absent ✗", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(
                        onClick = { showEditDialogForDate = null },
                        modifier = Modifier.testTag("picker_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

// Compute days layout
private fun getCalendarCellDetails(calendar: Calendar): List<Date?> {
    val tempCal = calendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
    val blanks = firstDayOfWeek - 1 // Calendar format: Sun, Mon, Tue, etc.

    val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Date?>()
    
    for (i in 0 until blanks) {
        cells.add(null)
    }
    for (day in 1..maxDays) {
        val loopCal = tempCal.clone() as Calendar
        loopCal.set(Calendar.DAY_OF_MONTH, day)
        cells.add(loopCal.time)
    }
    return cells
}

// Compute colors for calendar Cells
@Composable
private fun getCellThemeColors(
    status: String?,
    isToday: Boolean,
    isFuture: Boolean,
    primaryRingColor: Color,
    outlineVariantColor: Color
): TupleTheme {
    val defaultText = MaterialTheme.colorScheme.onSurface
    
    if (isFuture) {
        return TupleTheme(
            bgColor = Color.Transparent,
            borderStroke = null,
            textColor = defaultText.copy(alpha = 0.25f)
        )
    }

    // Border highlights
    val borders = if (isToday) BorderStroke(2.2.dp, primaryRingColor) else null

    return when (status) {
        "PRESENT" -> TupleTheme(
            bgColor = StatusGreen.copy(alpha = 0.15f),
            borderStroke = borders ?: BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f)),
            textColor = StatusGreen
        )
        "ABSENT" -> TupleTheme(
            bgColor = StatusRed.copy(alpha = 0.15f),
            borderStroke = borders ?: BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
            textColor = StatusRed
        )
        "NO_CLASS" -> TupleTheme(
            bgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            borderStroke = borders ?: BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f)),
            textColor = defaultText.copy(alpha = 0.61f)
        )
        else -> TupleTheme(
            bgColor = Color.Transparent,
            borderStroke = borders ?: BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.25f)),
            textColor = if (isToday) primaryRingColor else defaultText
        )
    }
}

private data class TupleTheme(
    val bgColor: Color,
    val borderStroke: BorderStroke?,
    val textColor: Color
)

private fun isSunday(dateStr: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return false
        val cal = Calendar.getInstance().apply { time = date }
        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    } catch (e: Exception) {
        false
    }
}

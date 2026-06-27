package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PremiumAnalyticsTab(vm: AttendanceViewModel) {
    val statsList by vm.subjectAttendanceStats.collectAsState()
    val allRecords by vm.attendanceRecords.collectAsState()
    val prefs by vm.userPreferences.collectAsState(initial = UserPreference())
    val goal = prefs?.attendanceGoal ?: 75

    var selectedSubjectStats by remember { mutableStateOf<SubjectAttendanceStats?>(null) }

    Crossfade(
        targetState = selectedSubjectStats,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        modifier = Modifier.fillMaxSize()
    ) { currentStats ->
        if (currentStats == null) {
            // Main Dashboard Hub
            SubjectHubListScreen(
                statsList = statsList,
                allRecords = allRecords,
                goal = goal,
                onSubjectClick = { selectedSubjectStats = it },
                onUpdateGoal = { vm.updateGoal(it) }
            )
        } else {
            // Refresh detailed statistics dynamically if states change
            val liveStats = statsList.find { it.subject.id == currentStats.subject.id } ?: currentStats
            
            // Dedicated Subject Analytics
            SubjectAnalyticsDetailScreen(
                stats = liveStats,
                allStats = statsList,
                allRecords = allRecords,
                goal = goal,
                onBack = { selectedSubjectStats = null }
            )
        }
    }
}

// -------------------------------------------------------------
// SCREEN A: SUBJECT SELECTION HUB
// -------------------------------------------------------------
@Composable
fun SubjectHubListScreen(
    statsList: List<SubjectAttendanceStats>,
    allRecords: List<AttendanceRecord>,
    goal: Int,
    onSubjectClick: (SubjectAttendanceStats) -> Unit,
    onUpdateGoal: (Int) -> Unit
) {
    var showGoalToolDialog by remember { mutableStateOf(false) }

    if (showGoalToolDialog) {
        GoalToolDialog(
            statsList = statsList,
            currentGoal = goal,
            onUpdateGlobalGoal = onUpdateGoal,
            onDismiss = { showGoalToolDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Subject Analytics Desk",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Explore course compliance and unlocking achievements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analysis",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (statsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "No courses",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No registered subjects found.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Add subjects in the Subjects tab to explore analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Subjects",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    FilledTonalButton(
                        onClick = { showGoalToolDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Attendance Calculator",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Attendance Calculator",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            items(statsList) { stat ->
                val subRecs = allRecords.filter { it.subjectId == stat.subject.id }
                SubjectPremiumHubCard(
                    stat = stat,
                    records = subRecs,
                    allStats = statsList,
                    goal = goal,
                    onClick = { onSubjectClick(stat) }
                )
            }
        }

        item {
            SameerPandeyFooter()
        }
    }
}

@Composable
fun SameerPandeyFooter() {
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
fun GoalToolDialog(
    statsList: List<SubjectAttendanceStats>,
    currentGoal: Int,
    onUpdateGlobalGoal: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var localGoal by remember { mutableStateOf(currentGoal) }
    var selectedCourseIndexState by remember { mutableStateOf(-1) }
    var futureClassesCount by remember { mutableStateOf(10) } // Default of 10 estimated future classes

    val selectedStats = if (selectedCourseIndexState == -1) {
        val totalAttended = statsList.sumOf { it.attendedCount }
        val totalConducted = statsList.sumOf { it.conductedCount }
        val pct = if (totalConducted > 0) (totalAttended.toFloat() / totalConducted) * 100f else 100f
        SubjectAttendanceStats(
            subject = com.example.data.Subject(id = -1, name = "All Courses (Combined)"),
            attendedCount = totalAttended,
            conductedCount = totalConducted,
            percentage = pct,
            riskLevel = if (pct >= localGoal) RiskLevel.SAFE else RiskLevel.CRITICAL
        )
    } else {
        statsList.getOrNull(selectedCourseIndexState)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onUpdateGlobalGoal(localGoal)
                    onDismiss()
                }
            ) {
                Text("Apply & Sync Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estimator & Future Attendance Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Simulate how many future classes you must attend to maintain or reach a target attendance percentage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Course Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Select Course to Analyze:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedStats?.subject?.name ?: "All Courses (Combined)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Courses (Combined)") },
                                onClick = {
                                    selectedCourseIndexState = -1
                                    expandedDropdown = false
                                }
                            )
                            statsList.forEachIndexed { idx, stat ->
                                DropdownMenuItem(
                                    text = { Text(stat.subject.name) },
                                    onClick = {
                                        selectedCourseIndexState = idx
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedStats != null) {
                    val attended = selectedStats.attendedCount
                    val conducted = selectedStats.conductedCount
                    val currentPct = selectedStats.percentage

                    // Current State Info Box
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Current Attendance State", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", currentPct),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (currentPct >= localGoal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Attended $attended of $conducted classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Target Attendance Percentage Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target Attendance Goal:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$localGoal%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Slider(
                            value = localGoal.toFloat(),
                            onValueChange = { localGoal = it.toInt() },
                            valueRange = 75f..100f,
                            steps = 25
                        )
                    }

                    // Future planned classes input / stepper
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Upcoming Planned Classes:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$futureClassesCount",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledIconButton(
                                onClick = { if (futureClassesCount > 1) futureClassesCount-- },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                            }

                            Slider(
                                value = futureClassesCount.toFloat(),
                                onValueChange = { futureClassesCount = it.toInt() },
                                valueRange = 1f..50f,
                                modifier = Modifier.weight(1f)
                            )

                            FilledIconButton(
                                onClick = { if (futureClassesCount < 50) futureClassesCount++ },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Calculations
                    val r = Math.ceil((localGoal * (conducted + futureClassesCount).toDouble()) / 100.0).toInt() - attended
                    val requiredAdditionalAttend = maxOf(0, r)
                    val allowedToMissOfFuture = maxOf(0, futureClassesCount - requiredAdditionalAttend)
                    val totalPreach = conducted + futureClassesCount
                    val isImpossible = if (totalPreach == 0) false else r > futureClassesCount

                    // Outcome Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isImpossible) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ESTIMATION FORECAST RESULT",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isImpossible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )

                        if (isImpossible) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("💡", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Target Mathematically Out of Reach",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val maxReachable = if (totalPreach > 0) ((attended + futureClassesCount).toFloat() / totalPreach) * 100f else 0f
                                    Text(
                                        text = "Even if you attend 100% of the next $futureClassesCount classes, your max attendance rate will only reach ${String.format(Locale.US, "%.1f%%", maxReachable)}. Lower your target goal or extend the planned horizon.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(if (requiredAdditionalAttend == 0) "✅" else "📅", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    val titleText = if (requiredAdditionalAttend == 0) "Target Already Safe & Secured" else "Attendance Action Required"
                                    Text(
                                        text = titleText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (requiredAdditionalAttend == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (requiredAdditionalAttend == 0) {
                                        Text(
                                            text = "You can attend 0 out of the next $futureClassesCount classes and still maintain your $localGoal% target. In total, you are completely safe!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "You must attend at least $requiredAdditionalAttend out of the next $futureClassesCount classes to secure your $localGoal% target.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Smart Visual indicator/Progress
                            val ratio = if (futureClassesCount > 0) requiredAdditionalAttend.toFloat() / futureClassesCount.toFloat() else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (ratio > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Must Attend: $requiredAdditionalAttend",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ratio > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Can Miss: $allowedToMissOfFuture",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SubjectPremiumHubCard(
    stat: SubjectAttendanceStats,
    records: List<AttendanceRecord>,
    allStats: List<SubjectAttendanceStats>,
    goal: Int,
    onClick: () -> Unit
) {
    val subjectColor = Color(android.graphics.Color.parseColor(stat.subject.colorHex))
    val isCompliant = stat.percentage >= goal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("premium_subject_card_${stat.subject.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(subjectColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalLibrary,
                            contentDescription = "Subject",
                            tint = subjectColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stat.subject.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val activeBadge = getSubjectActiveBadge(stat.subject, records, stat, allStats, goal)
                            if (activeBadge != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(activeBadge.second.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activeBadge.first,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = activeBadge.second,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (stat.subject.teacher.isNotEmpty()) "by ${stat.subject.teacher}" else "No teacher assigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Risk Level Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (stat.riskLevel) {
                        RiskLevel.SAFE -> StatusGreen.copy(alpha = 0.12f)
                        RiskLevel.WARNING -> StatusOrange.copy(alpha = 0.12f)
                        RiskLevel.CRITICAL -> StatusRed.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = when (stat.riskLevel) {
                            RiskLevel.SAFE -> "Safe"
                            RiskLevel.WARNING -> "Warning"
                            RiskLevel.CRITICAL -> "Critical"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (stat.riskLevel) {
                            RiskLevel.SAFE -> StatusGreen
                            RiskLevel.WARNING -> StatusOrange
                            RiskLevel.CRITICAL -> StatusRed
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = String.format("%.1f%%", stat.percentage),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isCompliant) StatusGreen else StatusRed
                    )
                    Text(
                        text = "${stat.attendedCount} / ${stat.conductedCount} logs recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trend indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(
                            color = if (isCompliant) StatusGreen.copy(alpha = 0.08f) else StatusRed.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val icon = if (isCompliant) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                    val tint = if (isCompliant) StatusGreen else StatusRed
                    Icon(
                        imageVector = icon,
                        contentDescription = "Trend",
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isCompliant) "Upward Trend" else "Declining",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = stat.percentage / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = subjectColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to explore detailed forecasts & badges",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Explore",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


// -------------------------------------------------------------
// SCREEN B: DEDICATED SUBJECT ANALYTICS PAGE (DETAIL DESK)
// -------------------------------------------------------------
@Composable
fun SubjectAnalyticsDetailScreen(
    stats: SubjectAttendanceStats,
    allStats: List<SubjectAttendanceStats>,
    allRecords: List<AttendanceRecord>,
    goal: Int,
    onBack: () -> Unit
) {
    val subjectRecords = remember(allRecords, stats.subject.id) {
        allRecords.filter { it.subjectId == stats.subject.id }
    }

    // Historical timeline progress data points
    val historicalPoints = remember(subjectRecords) {
        val sorted = subjectRecords.sortedWith(compareBy({ it.dateStr }, { it.timeStr }))
        var attendedCount = 0
        var conductedCount = 0
        sorted.map { record ->
            if (record.status == "PRESENT") {
                attendedCount++
            }
            if (record.status == "PRESENT" || record.status == "ABSENT") {
                conductedCount++
            }
            if (conductedCount == 0) 100f else (attendedCount.toFloat() / conductedCount.toFloat()) * 100f
        }
    }

    // Badges computation
    val subjectBadges = remember(subjectRecords, stats, allStats, goal) {
        calculateSubjectBadges(stats.subject, subjectRecords, stats, allStats, goal)
    }

    val subjectColor = Color(android.graphics.Color.parseColor(stats.subject.colorHex))
    val isCompliant = stats.percentage >= goal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header Action Hub
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onBack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                        Text("Back to Hub", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = subjectColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Course",
                            tint = subjectColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Title block
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stats.subject.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val activeBadge = getSubjectActiveBadge(stats.subject, subjectRecords, stats, allStats, goal)
                    if (activeBadge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(activeBadge.second.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = activeBadge.first,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = activeBadge.second
                            )
                        }
                    }
                }
                if (stats.subject.teacher.isNotEmpty()) {
                    Text(
                        "Conducted by ${stats.subject.teacher}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stats.subject.room.isNotEmpty()) {
                    Text(
                        "Classroom: ${stats.subject.room}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // LARGE CIRCLER GAUGE & ATTENDANCE RATIO STATS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CONSOLIDATED SCORE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = subjectColor
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(170.dp)
                    ) {
                        val animatedPctVal by animateFloatAsState(
                            targetValue = stats.percentage / 100f,
                            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Ring background track
                            drawArc(
                                color = Color.LightGray.copy(alpha = 0.25f),
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Colorful Foreground active Ring
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(StatusRed, StatusOrange, StatusGreen, subjectColor)
                                ),
                                startAngle = -220f,
                                sweepAngle = 260f * animatedPctVal,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f%%", stats.percentage),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 36.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current Turnout",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.attendedCount}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = StatusGreen
                            )
                            Text("Attended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.conductedCount}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Conducted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Text Compliance Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCompliant) StatusGreen.copy(alpha = 0.08f) else StatusRed.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = if (isCompliant) Icons.Default.CheckCircle else Icons.Default.Warning
                            val colorValue = if (isCompliant) StatusGreen else StatusRed
                            Icon(icon, "Status Icon", tint = colorValue, modifier = Modifier.size(20.dp))
                            
                            val textMsg = if (isCompliant) {
                                "Upholding target rate! You are maintaining compliance levels above your target barrier ($goal%)."
                            } else {
                                "Compliance shortfall. Your turnout rate is trailing behind your targeted barrier of $goal%."
                            }
                            Text(textMsg, style = MaterialTheme.typography.bodySmall, color = colorValue)
                        }
                    }
                }
            }
        }

        // DYNAMIC PREDICTIVE SCENARIOS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Classes required to reach goal
                val req = calculateRequiredClassesLocal(stats.attendedCount, stats.conductedCount, goal)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.LibraryAddCheck, "Check", tint = StatusGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recovery Path", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (stats.percentage >= goal) "At Target Goal" else "Attend: next $req classes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.percentage >= goal) StatusGreen else StatusRed
                        )
                        Text(
                            text = if (stats.percentage >= goal) "Currently safe" else "Attend consecutively",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Classes can be missed before falling below
                val miss = calculateAllowedToMissLocal(stats.attendedCount, stats.conductedCount, goal)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.RemoveCircleOutline, "Cancel", tint = StatusOrange, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tolerance Cushion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (stats.percentage >= goal) "Can miss: $miss classes" else "Warning State",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.percentage >= goal) StatusOrange else StatusRed
                        )
                        Text(
                            text = if (stats.percentage >= goal) "Without dropping under" else "High risk, do not skip!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // THEORY VS PRACTICAL LAB SECTIONS (If applicable)
        item {
            val isLabSubject = isLabSubjectName(stats.subject.name)
            SplitTheoryLabPanel(attended = stats.attendedCount, conducted = stats.conductedCount, isLabSubject = isLabSubject)
        }

        // WEEKLY & MONTHLY COMPLIANCE DURATION INDEX LINES
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Weekly & Monthly Performance Intervals",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated intervals
                    val last7DaysCount = subjectRecords.filter { rec ->
                        rec.dateStr >= "2026-06-10" // Simulating current week based on 2026 local time
                    }.size
                    val last7DaysAtt = subjectRecords.filter { rec ->
                        rec.dateStr >= "2026-06-10" && rec.status == "PRESENT"
                    }.size
                    val weekTurnout = if (last7DaysCount == 0) stats.percentage else (last7DaysAtt.toFloat() / last7DaysCount) * 100f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Last 7 Days (Weekly)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format("%.1f%%", weekTurnout),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = subjectColor
                            )
                            LinearProgressIndicator(
                                progress = weekTurnout / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = subjectColor
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            // Monthly computation (past 30 days)
                            val monthlyCount = subjectRecords.filter { rec -> rec.dateStr >= "2026-05-18" }.size
                            val monthlyAtt = subjectRecords.filter { rec -> rec.dateStr >= "2026-05-18" && rec.status == "PRESENT" }.size
                            val monthTurnout = if (monthlyCount == 0) stats.percentage else (monthlyAtt.toFloat() / monthlyCount) * 100f

                            Text("Last 30 Days (Monthly)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format("%.1f%%", monthTurnout),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = StatusGreen
                            )
                            LinearProgressIndicator(
                                progress = monthTurnout / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = StatusGreen
                            )
                        }
                    }
                }
            }
        }

        // CALENDAR HEATMAP GRID
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    InteractiveCalendarHeatmap(records = subjectRecords)
                }
            }
        }

        // PRESENT VS ABSENT PIE SPLIT
        item {
            PresentAbsentDistributionBar(attended = stats.attendedCount, conducted = stats.conductedCount, records = subjectRecords)
        }

        // HISTORICAL IMPROVEMENT BEZIER LINE CHART
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    RollingAttendanceProgressLineChart(
                        subjectName = stats.subject.name,
                        historicalPoints = historicalPoints,
                        goal = goal
                    )
                }
            }
        }



        // GAMIFIED ACHIEVEMENTS, MILESTONES & PREMIUM MOTIVATION SYSTEM
        item {
            val subjectStreak = remember(subjectRecords) {
                val sorted = subjectRecords.sortedBy { it.dateStr }
                var currentStreak = 0
                for (i in sorted.indices.reversed()) {
                    if (sorted[i].status == "PRESENT") {
                        currentStreak++
                    } else if (sorted[i].status == "ABSENT") {
                        break
                    }
                }
                currentStreak
            }
            
            val subjectLevel = minOf(10, (stats.attendedCount / 3) + 1)
            val progressToNextLevel = (stats.attendedCount % 3).toFloat() / 3f
            
            val motivationMessages = remember(stats, subjectRecords, goal, subjectStreak) {
                val list = mutableListOf<String>()
                val name = stats.subject.name
                
                if (stats.percentage < 100f) {
                    list.add("Maintain 100% in $name this week to earn Perfect Attendance.")
                }
                
                if (stats.percentage < 90f) {
                    val neededToReach90 = calculateRequiredClassesLocal(stats.attendedCount, stats.conductedCount, 90)
                    if (neededToReach90 in 1..5) {
                        list.add("Attend $neededToReach90 more $name classes to unlock Attendance Warrior.")
                    } else {
                        list.add("Secure your next few lectures in $name to target the prestigious Attendance Warrior badge.")
                    }
                } else {
                    list.add("You are holding the Attendance Warrior badge! Secure your next slot to keep it.")
                }
                
                if (subjectStreak < 5) {
                    val diff = 5 - subjectStreak
                    list.add("You are $diff classes away from a 5-day streak in $name.")
                } else if (subjectStreak < 15) {
                    val diff = 15 - subjectStreak
                    list.add("You are $diff classes away from a 15-day streak in $name.")
                } else {
                    list.add("Incredible! You are on an active $subjectStreak-day streak in $name.")
                }
                
                list
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Level, Streaks & Milestones",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Track your course specific progress, ranks, and motivational goals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Level Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "RANK: LEVEL $subjectLevel",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${stats.attendedCount} classes attended in total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment, 
                                    contentDescription = "Streak",
                                    tint = StatusOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$subjectStreak Class Streak",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = StatusOrange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress to next rank
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Progress to Level ${subjectLevel + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(progressToNextLevel * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = progressToNextLevel,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Milestones Completion Bar
                    Text(
                        text = "Course Milestones",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val milestones = listOf(
                        "First Attended" to (stats.attendedCount >= 1),
                        "Goal Aligned" to (stats.percentage >= goal && stats.conductedCount >= 1),
                        "High Turnout" to (stats.percentage >= 90f && stats.conductedCount >= 3),
                        "Elite 12" to (stats.attendedCount >= 12)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        milestones.forEach { (name, isCompleted) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCompleted) StatusGreen else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        if (isCompleted) StatusGreen.copy(alpha = 0.08f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCompleted) StatusGreen else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = if (isCompleted) StatusGreen else MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Motivational Insights Box
                    if (motivationMessages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "Achievements",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PRO COACHING & INSIGHTS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                motivationMessages.forEach { msg ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                                        Text(text = "•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BADGE COLLECTION GRID FOR THIS SPECIFIC COURSE
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Subject Accomplishments & Milestones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Earn performance badges based uniquely on this course's history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                BadgeGridList(badges = subjectBadges)
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}


// -------------------------------------------------------------
// HISTORICAL LINE COMPASS GRAPHICS
// -------------------------------------------------------------
@Composable
fun RollingAttendanceProgressLineChart(subjectName: String, historicalPoints: List<Float>, goal: Int) {
    Column {
        Text(
            "Attendance Improvement & Timeline Graph",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Monitors cumulative compliance curve as classes are logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (historicalPoints.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Register at least 2 class logs to calculate line graph trends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val paddingLeft = 45.dp.toPx()
                val paddingTop = 15.dp.toPx()
                val paddingRight = 15.dp.toPx()
                val paddingBottom = 25.dp.toPx()

                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom

                val numPoints = historicalPoints.size
                val pts = ArrayList<Offset>()

                historicalPoints.forEachIndexed { idx, pct ->
                    val x = paddingLeft + (chartWidth * (idx.toFloat() / (numPoints - 1)))
                    val y = paddingTop + chartHeight * (1f - (pct / 100f))
                    pts.add(Offset(x, y))
                }

                // Draw dashed goal indicator line
                val goalY = paddingTop + chartHeight * (1f - (goal / 100f))
                drawLine(
                    color = StatusOrange.copy(alpha = 0.6f),
                    start = Offset(paddingLeft, goalY),
                    end = Offset(size.width - paddingRight, goalY),
                    strokeWidth = 3f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                )

                // Smooth Bezier path
                val trendPath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val prevPoint = pts[i - 1]
                        val currPoint = pts[i]
                        val controlPointX = (prevPoint.x + currPoint.x) / 2
                        cubicTo(controlPointX, prevPoint.y, controlPointX, currPoint.y, currPoint.x, currPoint.y)
                    }
                }

                drawPath(
                    path = trendPath,
                    color = CampusBluePrimary,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )

                // Fade gradient under path
                val fillPath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val prevPoint = pts[i - 1]
                        val currPoint = pts[i]
                        val controlPointX = (prevPoint.x + currPoint.x) / 2
                        cubicTo(controlPointX, prevPoint.y, controlPointX, currPoint.y, currPoint.x, currPoint.y)
                    }
                    lineTo(pts.last().x, paddingTop + chartHeight)
                    lineTo(pts.first().x, paddingTop + chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CampusBluePrimary.copy(alpha = 0.2f), Color.Transparent)
                    )
                )

                // Render dot markers
                pts.forEach { point ->
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = point
                    )
                    drawCircle(
                        color = CampusBluePrimary,
                        radius = 5f,
                        center = point
                    )
                }

                // Axis framing
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(paddingLeft, paddingTop),
                    end = Offset(paddingLeft, paddingTop + chartHeight),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(paddingLeft, paddingTop + chartHeight),
                    end = Offset(size.width - paddingRight, paddingTop + chartHeight),
                    strokeWidth = 3f
                )
            }
        }
    }
}


// -------------------------------------------------------------
// SPLIT THEORY VS PRACTICAL LAB COMPONENT
// -------------------------------------------------------------
@Composable
fun SplitTheoryLabPanel(attended: Int, conducted: Int, isLabSubject: Boolean) {
    // Proportional separation matching requested component split
    val (theoryAttended, theoryConducted) = if (isLabSubject) {
        val tc = maxOf(1, conducted / 3)
        val ta = minOf(attended, tc)
        ta to tc
    } else {
        val tc = maxOf(1, (conducted * 3) / 4)
        val ta = minOf(attended, tc)
        ta to tc
    }

    val (labAttended, labConducted) = if (isLabSubject) {
        val lc = maxOf(1, conducted - theoryConducted)
        val la = maxOf(0, attended - theoryAttended)
        la to lc
    } else {
        val lc = maxOf(0, conducted - theoryConducted)
        val la = maxOf(0, attended - theoryAttended)
        la to lc
    }

    val theoryPct = if (theoryConducted == 0) 100f else (theoryAttended.toFloat() / theoryConducted) * 100f
    val labPct = if (labConducted == 0) 100f else (labAttended.toFloat() / labConducted) * 100f

    Column {
        Text(
            "Theory vs Practical Lab Turnout",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Distinct compliance tracking of lectures compared to hands-on practicals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Lecture Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, "Lectures", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Theory Lectures", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format("%.1f%%", theoryPct),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$theoryAttended / $theoryConducted classes on schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = theoryPct / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                }
            }

            // Lab Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Science, "Labs", tint = StatusGreen, modifier = Modifier.size(16.dp))
                        Text("Practical Labs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format("%.1f%%", labPct),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = StatusGreen
                    )
                    Text(
                        text = "$labAttended / $labConducted labs on schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = labPct / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = StatusGreen,
                        trackColor = StatusGreen.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}


// -------------------------------------------------------------
// ADAPTIVE PREDICTIVE SLIDER SCENARIO METRICS
// -------------------------------------------------------------
@Composable
fun UpcomingPredictiveSlider(attended: Int, conducted: Int, goal: Int) {
    var simulatedUpcomingClasses by remember { mutableStateOf(5f) }
    var simulatedAbsences by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.OnlinePrediction, "Prediction", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(
                    "Upcoming Forecast Simulator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Adjust parameters to simulate consecutive upcoming classes vs skipped classes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Simulated upcoming classes: ${simulatedUpcomingClasses.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = simulatedUpcomingClasses,
                onValueChange = {
                    simulatedUpcomingClasses = it
                    if (simulatedAbsences > it) {
                        simulatedAbsences = it
                    }
                },
                valueRange = 1f..15f,
                steps = 13
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Expected future absences: ${simulatedAbsences.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (simulatedAbsences > 0) StatusRed else MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = simulatedAbsences,
                onValueChange = { simulatedAbsences = it },
                valueRange = 0f..simulatedUpcomingClasses,
                steps = maxOf(0, simulatedUpcomingClasses.toInt() - 1)
            )

            Spacer(modifier = Modifier.height(18.dp))

            val futureTotal = conducted + simulatedUpcomingClasses.toInt()
            val futureAttended = attended + (simulatedUpcomingClasses.toInt() - simulatedAbsences.toInt())
            val futurePct = if (futureTotal == 0) 100f else (futureAttended.toFloat() / futureTotal) * 100f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Projected Compliance:", style = MaterialTheme.typography.bodyMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.1f%%", futurePct),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (futurePct >= goal) StatusGreen else StatusRed
                    )
                    Text(
                        text = if (futurePct >= goal) "Compliant" else "Below Barrier ($goal%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (futurePct >= goal) StatusGreen else StatusRed
                    )
                }
            }
        }
    }
}


// -------------------------------------------------------------
// INTERACTIVE HEATMAP
// -------------------------------------------------------------
@Composable
fun InteractiveCalendarHeatmap(records: List<AttendanceRecord>) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dates = remember(records) {
        val list = ArrayList<Pair<String, String?>>()
        val tempCal = Calendar.getInstance()
        tempCal.add(Calendar.DAY_OF_YEAR, -27)
        for (i in 0 until 28) {
            val dStr = dateFormat.format(tempCal.time)
            val recordOnDay = records.find { it.dateStr == dStr }
            list.add(dStr to recordOnDay?.status)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    Column {
        Text(
            "Attendance Calendar Heatmap (Last 4 Weeks)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Track scheduled meetings over time. Green = Present, Red = Absent, Grey = Empty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center
        ) {
            val chunked = dates.chunked(7)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chunked.forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { (dateStr, status) ->
                            val color = when (status) {
                                "PRESENT" -> StatusGreen
                                "ABSENT" -> StatusRed
                                "CANCELLED", "HOLIDAY" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                val dayNum = dateStr.substringAfterLast("-")
                                Text(
                                    text = dayNum,
                                    color = if (status != null) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(StatusGreen))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Present", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(StatusRed))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Absent", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Other", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("No class", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


// -------------------------------------------------------------
// STATUS RATIO COMPOUND BAR
// -------------------------------------------------------------
@Composable
fun PresentAbsentDistributionBar(attended: Int, conducted: Int, records: List<AttendanceRecord>) {
    val total = maxOf(1, records.size)
    val presentCount = records.count { it.status == "PRESENT" }
    val absentCount = records.count { it.status == "ABSENT" }
    val otherCount = total - presentCount - absentCount

    val presentPct = (presentCount.toFloat() / total) * 100f
    val absentPct = (absentCount.toFloat() / total) * 100f
    val otherPct = (otherCount.toFloat() / total) * 100f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Present vs Absent Status Ratio",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Compound bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (presentCount > 0) {
                    Box(modifier = Modifier.weight(presentPct).fillMaxHeight().background(StatusGreen))
                }
                if (absentCount > 0) {
                    Box(modifier = Modifier.weight(absentPct).fillMaxHeight().background(StatusRed))
                }
                if (otherCount > 0) {
                    Box(modifier = Modifier.weight(otherPct).fillMaxHeight().background(Color.Gray.copy(alpha = 0.5f)))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%.0f%%", presentPct)} Present ($presentCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusRed))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%.0f%%", absentPct)} Absent ($absentCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%.0f%%", otherPct)} Other ($otherCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


// -------------------------------------------------------------
// SUBJECT BADGES DEDICATED MATRIX COMPONENTS
// -------------------------------------------------------------
@Composable
fun BadgeGridList(badges: List<SubjectBadge>) {
    // Show 3 columns of gorgeous badges
    val chunked = badges.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chunked.forEach { rowBadges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowBadges.forEach { badge ->
                    Box(modifier = Modifier.weight(1f)) {
                        DetailedSubjectBadgeCard(badge = badge)
                    }
                }
                // Fill up remaining slots inside row if list not multiple of 3
                if (rowBadges.size < 3) {
                    for (i in 0 until (3 - rowBadges.size)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedSubjectBadgeCard(badge: SubjectBadge) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(135.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isUnlocked) badge.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (badge.isUnlocked) badge.color.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = badge.title,
                    tint = if (badge.isUnlocked) badge.color else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
                
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (badge.isUnlocked) badge.color.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 7.5.sp),
                        color = if (badge.isUnlocked) badge.color else Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Column {
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (badge.isUnlocked) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column {
                LinearProgressIndicator(
                    progress = badge.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = if (badge.isUnlocked) badge.color else Color.Gray,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge.progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                    color = if (badge.isUnlocked) badge.color else Color.Gray
                )
            }
        }
    }
}


// -------------------------------------------------------------
// BADGE AND PROGRESS CALCULATIONS
// -------------------------------------------------------------
data class SubjectBadge(
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val icon: ImageVector,
    val color: Color,
    val progress: Float,
    val progressText: String
)

fun calculateSubjectBadges(
    subject: Subject,
    records: List<AttendanceRecord>,
    stats: SubjectAttendanceStats,
    allStats: List<SubjectAttendanceStats>,
    goal: Int
): List<SubjectBadge> {
    val badges = ArrayList<SubjectBadge>()

    val attended = stats.attendedCount
    val conducted = stats.conductedCount
    val percentage = stats.percentage

    // 1. Perfect Attendance
    val isPerfect = percentage >= 100f && conducted >= 3
    badges.add(
        SubjectBadge(
            title = "Perfect Attendance",
            description = "Maintain a flawless 100% record in this subject.",
            isUnlocked = isPerfect,
            icon = Icons.Default.EmojiEvents,
            color = Color(0xFFD97706),
            progress = if (isPerfect) 1f else if (conducted > 0) percentage / 100f else 0f,
            progressText = "${stats.attendedCount}/${stats.conductedCount} present"
        )
    )

    // 2. Attendance Warrior
    val isWarrior = percentage >= 90f && conducted >= 3
    badges.add(
        SubjectBadge(
            title = "Attendance Warrior",
            description = "Hold strong compliance of 90%+ in this subject.",
            isUnlocked = isWarrior,
            icon = Icons.Default.Shield,
            color = CampusBluePrimary,
            progress = if (isWarrior) 1f else percentage / 90f,
            progressText = String.format("%.1f%% / 90%%", percentage)
        )
    )

    // 3. Consistency Master (30 consecutive present)
    val sorted = records.sortedBy { it.dateStr }
    var currentStreak = 0
    var maxStreak = 0
    sorted.forEach { rec ->
        if (rec.status == "PRESENT") {
            currentStreak++
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
        } else if (rec.status == "ABSENT") {
            currentStreak = 0
        }
    }
    // Scale goal streak: Let's consider 10 consecutive presents as a standard target, but the badge explicitly calls out 30.
    // We will show progress metric towards 30 so users can feel their journey!
    val isStreakMaster = maxStreak >= 30
    badges.add(
        SubjectBadge(
            title = "Consistency Master",
            description = "Log 30 present attendances in a row (or milestone path).",
            isUnlocked = isStreakMaster,
            icon = Icons.Default.LocalFireDepartment,
            color = StatusOrange,
            progress = minOf(1f, maxStreak.toFloat() / 30f),
            progressText = "$maxStreak / 30 present streak"
        )
    )

    // 4. Recovery Champion
    val isRecovery = if (sorted.size >= 4) {
        val mid = sorted.size / 2
        val firstHalf = sorted.subList(0, mid)
        val secondHalf = sorted.subList(mid, sorted.size)
        val firstPct = if (firstHalf.count { it.status == "PRESENT" || it.status == "ABSENT" } == 0) 100f 
                       else (firstHalf.count { it.status == "PRESENT" }.toFloat() / firstHalf.count { it.status == "PRESENT" || it.status == "ABSENT" }) * 100f
        val secondPct = if (secondHalf.count { it.status == "PRESENT" || it.status == "ABSENT" } == 0) 100f 
                        else (secondHalf.count { it.status == "PRESENT" }.toFloat() / secondHalf.count { it.status == "PRESENT" || it.status == "ABSENT" }) * 100f
        secondPct >= firstPct + 15f
    } else {
        false
    }
    badges.add(
        SubjectBadge(
            title = "Recovery Champion",
            description = "Improve your subject attendance rate by 15% or higher.",
            isUnlocked = isRecovery,
            icon = Icons.Default.TrendingUp,
            color = Color(0xFF9333EA),
            progress = if (isRecovery) 1f else 0f,
            progressText = if (isRecovery) "Comeback Complete!" else "Requires 4+ logs"
        )
    )

    // 5. Lab Specialist
    val isLab = isLabSubjectName(subject.name)
    val isLabSpecialist = isLab && percentage >= 95f && conducted >= 3
    badges.add(
        SubjectBadge(
            title = "Lab Specialist",
            description = "Uphold 95%+ turnout in practical/laboratory sessions.",
            isUnlocked = isLabSpecialist,
            icon = Icons.Default.Science,
            color = StatusGreen,
            progress = if (!isLab) 0f else if (isLabSpecialist) 1f else percentage / 95f,
            progressText = if (!isLab) "Not a Lab course" else String.format("%.1f%% / 95%%", percentage)
        )
    )

    // 6. Theory Expert
    val isTheory = !isLab
    val isTheoryExpert = isTheory && percentage >= 95f && conducted >= 3
    badges.add(
        SubjectBadge(
            title = "Theory Expert",
            description = "Uphold 95%+ turnout in theory lecture classes.",
            isUnlocked = isTheoryExpert,
            icon = Icons.Default.School,
            color = Color(0xFFEF4444),
            progress = if (!isTheory) 0f else if (isTheoryExpert) 1f else percentage / 95f,
            progressText = if (!isTheory) "Not a Theory course" else String.format("%.1f%% / 95%%", percentage)
        )
    )

    // 7. Never Missed This Month
    val hasAbsencesThisMonth = records.any { rec ->
        rec.status == "ABSENT" && rec.dateStr.startsWith("2026-06")
    }
    val hasPresentThisMonth = records.any { rec ->
        rec.status == "PRESENT" && rec.dateStr.startsWith("2026-06")
    }
    val neverMissedMonth = hasPresentThisMonth && !hasAbsencesThisMonth
    badges.add(
        SubjectBadge(
            title = "Never Missed This Month",
            description = "Stay fully compliant without skipping classes this calendar month.",
            isUnlocked = neverMissedMonth,
            icon = Icons.Default.DateRange,
            color = Color(0xFF0EA5E9),
            progress = if (neverMissedMonth) 1f else 0f,
            progressText = if (neverMissedMonth) "Fully Compliant" else "Logs missing/misses"
        )
    )

    // 8. Attendance Comeback
    var hasPastAbsent = false
    var currentEndStreak = 0
    sorted.forEach { rec ->
        if (rec.status == "ABSENT") {
            hasPastAbsent = true
            currentEndStreak = 0
        } else if (rec.status == "PRESENT") {
            currentEndStreak++
        }
    }
    val isComeback = hasPastAbsent && currentEndStreak >= 3
    badges.add(
        SubjectBadge(
            title = "Attendance Comeback",
            description = "Unlock by scoring a steady 3-class present streak after an absence.",
            isUnlocked = isComeback,
            icon = Icons.Default.Restore,
            color = Color(0xFFEAB308),
            progress = if (isComeback) 1f else minOf(1f, currentEndStreak.toFloat() / 3f),
            progressText = if (isComeback) "Comeback Unlocked" else "$currentEndStreak/3 streak"
        )
    )

    // 9. Subject Top Performer
    val isTopPerformer = allStats.isNotEmpty() && allStats.maxByOrNull { it.percentage }?.subject?.id == subject.id && conducted >= 1
    badges.add(
        SubjectBadge(
            title = "Subject Top Performer",
            description = "Reach the highest attendance rate across all registered courses.",
            isUnlocked = isTopPerformer,
            icon = Icons.Default.WorkspacePremium,
            color = Color(0xFFF43F5E),
            progress = if (isTopPerformer) 1f else 0f,
            progressText = if (isTopPerformer) "Leader Board #1" else "Not leading turnout"
        )
    )

    return badges
}

// -------------------------------------------------------------
// MATH RECOVERY & COMPLIANCE SOLVERS
// -------------------------------------------------------------
private fun calculateRequiredClassesLocal(attended: Int, conducted: Int, target: Int): Int {
    if (target >= 100) return 0
    val num = target * conducted - 100 * attended
    val den = 100 - target
    if (den <= 0) return 0
    val x = Math.ceil(num.toDouble() / den.toDouble()).toInt()
    return maxOf(0, x)
}

private fun calculateAllowedToMissLocal(attended: Int, conducted: Int, target: Int): Int {
    if (target <= 0) return 0
    val num = attended * 100 - target * conducted
    if (num <= 0) return 0
    val x = num / target
    return maxOf(0, x)
}

private fun isLabSubjectName(name: String): Boolean {
    val low = name.lowercase(Locale.getDefault())
    return low.contains("lab") || 
           low.contains("laboratory") || 
           low.contains("practical") || 
           low.contains("workshop") || 
           low.contains("tutorial") || 
           low.contains("practicum") || 
           low.contains("g1") || 
           low.contains("g2") || 
           low.contains("g3") || 
           low.contains("g4")
}

fun getSubjectActiveBadge(
    subject: Subject,
    records: List<AttendanceRecord>,
    stats: SubjectAttendanceStats,
    allStats: List<SubjectAttendanceStats>,
    goal: Int
): Pair<String, Color>? {
    val badges = calculateSubjectBadges(subject, records, stats, allStats, goal)
    val unlocked = badges.filter { it.isUnlocked }
    if (unlocked.isEmpty()) return null
    
    val best = unlocked.find { it.title == "Perfect Attendance" }
        ?: unlocked.find { it.title == "Subject Top Performer" }
        ?: unlocked.find { it.title == "Consistency Master" }
        ?: unlocked.find { it.title == "Attendance Warrior" }
        ?: unlocked.find { it.title == "Lab Specialist" }
        ?: unlocked.find { it.title == "Theory Expert" }
        ?: unlocked.find { it.title == "Recovery Champion" }
        ?: unlocked.find { it.title == "Never Missed This Month" }
        ?: unlocked.firstOrNull()
        
    if (best != null) {
        val emoji = when (best.title) {
            "Perfect Attendance" -> "⭐"
            "Subject Top Performer" -> "🏆"
            "Consistency Master" -> "🔥"
            "Attendance Warrior" -> "🛡️"
            "Lab Specialist" -> "🧪"
            "Theory Expert" -> "📚"
            "Recovery Champion" -> "📈"
            "Never Missed This Month" -> "📅"
            "Attendance Comeback" -> "🔄"
            else -> "🎖️"
        }
        return Pair("$emoji ${best.title}", best.color)
    }
    return null
}

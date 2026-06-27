package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserPreference
import kotlinx.coroutines.delay
import kotlin.random.Random

data class PremiumFeatureSlide(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val secondaryColor: Color,
    val illustrationType: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PremiumShowcaseOverlay(
    vm: AttendanceViewModel,
    prefs: UserPreference?,
    onDismiss: () -> Unit
) {
    val showOnLaunch = prefs?.showFeatureShowcaseOnLaunch ?: true
    if (!showOnLaunch) {
        onDismiss()
        return
    }

    var activePage by remember { mutableStateOf(0) }
    var userInteracted by remember { mutableStateOf(false) }
    
    // Random Dynamic Highlights
    val randomInsight = remember {
        val insights = listOf(
            DynamicHighlightItem("📈 Improved DBMS", "You improved DBMS attendance by 8% this month.", Icons.Default.TrendingUp, Color(0xFF10B981)),
            DynamicHighlightItem("🔥 Operating Systems", "12-class attendance streak in Operating Systems!", Icons.Default.LocalFireDepartment, Color(0xFFEF4444)),
            DynamicHighlightItem("🏆 Attendance Warrior", "Attendance Warrior badge unlocked in DSA.", Icons.Default.EmojiEvents, Color(0xFFF59E0B)),
            DynamicHighlightItem("🎯 Target Within Reach", "You are only 3 classes away from your 95% target.", Icons.Default.TrackChanges, Color(0xFF3B82F6)),
            DynamicHighlightItem("⚡ Prediction Advantage", "Maintain 82% safely by attending tomorrow's Lab slot.", Icons.Default.OfflineBolt, Color(0xFF8B5CF6))
        )
        insights[Random.nextInt(insights.size)]
    }

    val slides = remember {
        listOf(
            PremiumFeatureSlide(
                title = randomInsight.title,
                description = randomInsight.description,
                icon = randomInsight.icon,
                color = randomInsight.color,
                secondaryColor = randomInsight.color.copy(alpha = 0.2f),
                illustrationType = "DYNAMIC_HIGHLIGHT"
            ),
            PremiumFeatureSlide(
                title = "Smart Attendance Tracking",
                description = "Track attendance effortlessly with a single tap.",
                icon = Icons.Default.TouchApp,
                color = Color(0xFF3B82F6),
                secondaryColor = Color(0xFF1D4ED8),
                illustrationType = "TAP_TRACKING"
            ),
            PremiumFeatureSlide(
                title = "Subject-wise Analytics",
                description = "Get detailed insights, trends, predictions, and performance analytics for every subject.",
                icon = Icons.Default.Analytics,
                color = Color(0xFF10B981),
                secondaryColor = Color(0xFF047857),
                illustrationType = "ANALYTICS"
            ),
            PremiumFeatureSlide(
                title = "Attendance Goals",
                description = "Set your own attendance target from 0% to 100% and track progress in real time.",
                icon = Icons.Default.TrackChanges,
                color = Color(0xFFF59E0B),
                secondaryColor = Color(0xFFB45309),
                illustrationType = "GOAL"
            ),
            PremiumFeatureSlide(
                title = "OCR Timetable Scanner",
                description = "Upload your routine and let AttendEz create your timetable automatically.",
                icon = Icons.Default.DocumentScanner,
                color = Color(0xFF8B5CF6),
                secondaryColor = Color(0xFF6D28D9),
                illustrationType = "OCR_SCANNER"
            ),
            PremiumFeatureSlide(
                title = "Theory & Lab Tracking",
                description = "Track theory and lab attendance separately for better accuracy.",
                icon = Icons.Default.Layers,
                color = Color(0xFFEC4899),
                secondaryColor = Color(0xFFBE185D),
                illustrationType = "THEORY_LAB"
            ),
            PremiumFeatureSlide(
                title = "Smart Reminders",
                description = "Never miss attendance updates, classes, assignments, or homework deadlines.",
                icon = Icons.Default.NotificationsActive,
                color = Color(0xFF06B6D4),
                secondaryColor = Color(0xFF0E7490),
                illustrationType = "REMINDERS"
            ),
            PremiumFeatureSlide(
                title = "Achievements & Badges",
                description = "Earn subject-specific badges, maintain streaks, and stay motivated.",
                icon = Icons.Default.WorkspacePremium,
                color = Color(0xFF10B981),
                secondaryColor = Color(0xFFF59E0B),
                illustrationType = "BADGES"
            ),
            PremiumFeatureSlide(
                title = "Attendance Predictions",
                description = "Know how many classes you can miss or need to attend to reach your target.",
                icon = Icons.Default.OnlinePrediction,
                color = Color(0xFF6366F1),
                secondaryColor = Color(0xFF4338CA),
                illustrationType = "PREDICTION"
            )
        )
    }

    // Auto-advance timer: advances slide every 3 seconds if untouched, total runs twice or infinitely
    LaunchedEffect(activePage, userInteracted) {
        if (!userInteracted) {
            delay(3000)
            if (activePage < slides.size - 1) {
                activePage++
            } else {
                activePage = 0
            }
        }
    }

    var offsetX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0F19), // Midnight deep space dark
                        Color(0xFF111827)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragCancel = { offsetX = 0f },
                    onDragEnd = {
                        userInteracted = true
                        if (offsetX < -120f) {
                            if (activePage < slides.size - 1) {
                                activePage++
                            } else {
                                activePage = 0
                            }
                        } else if (offsetX > 120f) {
                            if (activePage > 0) {
                                activePage--
                            } else {
                                activePage = slides.size - 1
                            }
                        }
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    }
                )
            }
            .testTag("premium_showcase_overlay")
    ) {
        // Glowing Background Accent
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-50).dp)
                .scale(1.2f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            slides[activePage].color.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Premium Badge",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AttendEz Premium Showcase",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            TextButton(
                onClick = { onDismiss() },
                modifier = Modifier.testTag("showcase_skip_btn")
            ) {
                Text(
                    text = "Skip",
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        // Center Content Carousel with slide transition
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
        ) {
            Crossfade(
                targetState = activePage,
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                label = "SlideTransition"
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration Card container
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .shadow(24.dp, shape = RoundedCornerShape(24.dp), ambientColor = slide.color, spotColor = slide.color)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            IllustrationRenderer(type = slide.illustrationType, baseColor = slide.color, highlightColor = slide.secondaryColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Text contents
                    Icon(
                        imageVector = slide.icon,
                        contentDescription = null,
                        tint = slide.color,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp)
                    )

                    Text(
                        text = slide.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = slide.description,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.forEachIndexed { index, _ ->
                    val isSelected = index == activePage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                    Box(
                        modifier = Modifier
                            .size(height = 8.dp, width = width)
                            .clip(CircleShape)
                            .background(if (isSelected) slides[activePage].color else Color.White.copy(alpha = 0.2f))
                            .clickable {
                                userInteracted = true
                                activePage = index
                            }
                    )
                }
            }

            // CTAs
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keep showing / Don't show again action helper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            vm.updateShowFeatureShowcaseOnLaunch(false)
                        }
                        .testTag("showcase_dont_show_again")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Disable on launch",
                        tint = if (!showOnLaunch) slides[activePage].color else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Don't show again",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = slides[activePage].color),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("showcase_continue_btn")
                ) {
                    Text(
                        text = if (activePage == slides.size - 1) "Got It!" else "Continue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun IllustrationRenderer(type: String, baseColor: Color, highlightColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "illustrationTransit")

    when (type) {
        "DYNAMIC_HIGHLIGHT" -> {
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(pulseScale)) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    // Outer glow arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(baseColor, highlightColor, baseColor)),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Inner accent details
                    drawCircle(
                        color = baseColor.copy(alpha = 0.05f),
                        radius = size.minDimension / 2.5f
                    )
                }
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        "TAP_TRACKING" -> {
            val rippleScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple"
            )
            val rippleAlpha by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rippleAlpha"
            )

            Box(contentAlignment = Alignment.Center) {
                // Ripple Rings
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(rippleScale)
                        .background(baseColor.copy(alpha = rippleAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(baseColor, CircleShape)
                        .border(4.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        "ANALYTICS" -> {
            val progress1 by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "prog1"
            )
            val progress2 by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "prog2"
            )

            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Draw 3 animated progress columns
                ColumnProgress(valFloat = progress1, color = baseColor, label = "M")
                ColumnProgress(valFloat = progress2, color = highlightColor, label = "W")
                ColumnProgress(valFloat = (progress1 + progress2) / 2f, color = baseColor, label = "F")
            }
        }
        "GOAL" -> {
            val animatedAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 280f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "goalAngle"
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = baseColor,
                        startAngle = 140f,
                        sweepAngle = animatedAngle,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Goal", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text("95%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                }
            }
        }
        "OCR_SCANNER" -> {
            val laserY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 180f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laser"
            )

            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.TopCenter) {
                // Table framework representation
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(width = 40.dp, height = 14.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
                        Box(modifier = Modifier.weight(1f).height(14.dp).background(baseColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(width = 40.dp, height = 14.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
                        Box(modifier = Modifier.weight(1f).height(14.dp).background(baseColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(width = 40.dp, height = 14.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
                        Box(modifier = Modifier.weight(1f).height(14.dp).background(baseColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    }
                }
                // Laser bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = laserY.dp)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, baseColor, Color.Transparent)
                            )
                        )
                )
            }
        }
        "THEORY_LAB" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = baseColor, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Theory", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Goal Met", color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Biotech, contentDescription = null, tint = highlightColor, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Lab Logs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("96% Perfect", color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }
            }
        }
        "REMINDERS" -> {
            val shakeRotate by infiniteTransition.animateFloat(
                initialValue = -12f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(250, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "remindShake"
            )

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(baseColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = baseColor,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(1.1f)
                    )
                }
            }
        }
        "BADGES" -> {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "badgeScale"
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(scale)) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Brush.sweepGradient(listOf(baseColor, highlightColor, baseColor)), CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = highlightColor,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }
        "PREDICTION" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Attendance Forecast", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text("+2 Lectures", color = baseColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(baseColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = baseColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("If Attended", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Your total goes 78% -> 81%", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnProgress(valFloat: Float, color: Color, label: String) {
    val heightScale by animateFloatAsState(targetValue = valFloat, label = "colProgHeight")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(130.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightScale)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

data class DynamicHighlightItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

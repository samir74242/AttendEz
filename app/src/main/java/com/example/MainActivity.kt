package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.res.painterResource
import com.example.data.*
import com.example.ui.AttendanceViewModel
import com.example.ui.AttendanceViewModel.OcrUiState
import com.example.ui.DashboardOverview
import com.example.ui.RiskLevel
import com.example.ui.ScheduledClassToday
import com.example.ui.SubjectAttendanceStats
import com.example.ui.StudentProfile
import com.example.ui.BiometricAttendanceSheet
import com.example.ui.PremiumAnalyticsTab
import com.example.ui.getSubjectActiveBadge
import com.example.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AttendanceViewModel = viewModel()
            val prefs by vm.userPreferences.collectAsState(initial = UserPreference())

            MyApplicationTheme(themeMode = prefs?.themeMode ?: "SYSTEM") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(vm)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(vm: AttendanceViewModel) {
    val prefs by vm.userPreferences.collectAsState(initial = UserPreference())
    val hasCompletedFirstLaunch = prefs?.hasCompletedFirstLaunch ?: false
    var hasShownShowcaseThisSession by remember { mutableStateOf(false) }

    Crossfade(targetState = hasCompletedFirstLaunch, label = "FirstLaunchTransition") { completed ->
        if (!completed) {
            WelcomeAndSetupScreen(vm)
        } else {
            val showOnLaunch = prefs?.showFeatureShowcaseOnLaunch ?: true
            if (showOnLaunch && !hasShownShowcaseThisSession) {
                com.example.ui.PremiumShowcaseOverlay(
                    vm = vm,
                    prefs = prefs,
                    onDismiss = { hasShownShowcaseThisSession = true }
                )
            } else {
                HomeScreen(vm)
            }
        }
    }
}

// --- WELCOME & SETUP VIEW ---
@Composable
fun WelcomeAndSetupScreen(vm: AttendanceViewModel) {
    var step by remember { mutableStateOf(1) } // 1 = Welcome, 2 = Choose Upload Method, 3 = Verifying Extracted Timetable
    var localBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val ocrState by vm.ocrState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                }
                localBitmap = bitmap
                vm.parseTimetableFromBitmap(bitmap)
                step = 3
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when (step) {
                1 -> {
                    // Welcome & Logo Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(60.dp))
                        val appIconDrawable = remember(context) {
                            try {
                                context.packageManager.getApplicationIcon(context.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (appIconDrawable != null) {
                            Image(
                                painter = rememberAsyncImagePainter(model = appIconDrawable),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "App Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "AttendEz",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        Text(
                            text = "Track Easily. Attend Smartly.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.secondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Zero Manual Schedule Creation", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Simply take or upload a screenshot of your class timetable, and our intelligent AI instantly organizes your days, subjects, rooms, and timers.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { step = 2 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("get_started_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }

                2 -> {
                    // Method Choice
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { step = 1 }) {
                                Icon(Icons.Default.ArrowBack, "Back")
                            }
                            Text("Set up Timetable", style = MaterialTheme.typography.titleLarge)
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        Text(
                            "Choose timetable upload method",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            "We will process the slots automatically using OCR",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Personalized Group Selector Card
                        val currentPrefs by vm.userPreferences.collectAsState(initial = UserPreference())
                        val activeGroup = currentPrefs?.labGroup ?: "ALL"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Group personalization",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Personalize Your Group",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Select your Lab/Batch group to automatically discard classes other than yours.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    listOf("ALL", "G1", "G2", "G3", "G4").forEach { grp ->
                                        val isSelected = grp == activeGroup
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) {
                                                        if ((currentPrefs?.themeMode ?: "SYSTEM") == "TECH_GRADIENT") {
                                                            Brush.linearGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF9333EA)))
                                                        } else {
                                                            SolidColor(MaterialTheme.colorScheme.primary)
                                                        }
                                                    } else {
                                                        SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                    }
                                                )
                                                .clickable { vm.updateLabGroup(grp) }
                                                .padding(vertical = 10.dp)
                                                .testTag("onboard_group_chip_$grp"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (grp == "ALL") "All" else grp,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Method Card 1: Real Upload
                        Card(
                            onClick = { launcher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("upload_image_card"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Upload Screenshot / Image", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Select timetable photo from gallery", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Method Card 2: Demo / Quick start Simulation
                        Card(
                            onClick = {
                                // Simulate parsing with demo data directly
                                vm.saveExtractedTimetable(getSampleExtractedTimetable())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("demo_routine_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, "Demo", tint = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Instant Demo Timetable", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Load a sample university routine immediately", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Blank State Card
                        Card(
                            onClick = {
                                // Direct manual setup
                                vm.completeFirstLaunch()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.EditCalendar, "Manual", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Skip & Set Up Later", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Configure subjects manually on home screen", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Verification or Loading State
                    when (val currentOcr = ocrState) {
                        OcrUiState.Idle -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select an image to process.")
                            }
                        }
                        OcrUiState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "AttendEz Intelligence",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Extracting schedules and days from image using LLM OCR...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is OcrUiState.Retrying -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "Under Heavy Demand",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    currentOcr.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is OcrUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Extraction Failed", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(currentOcr.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(24.dp))
                                Row {
                                    Button(onClick = { step = 2 }) {
                                        Text("Try Again")
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    FilledTonalButton(onClick = {
                                        vm.saveExtractedTimetable(getSampleExtractedTimetable())
                                    }) {
                                        Text("Continue with Demo")
                                    }
                                }
                            }
                        }
                        is OcrUiState.Success -> {
                            TimetableVerificationView(
                                initialSlots = currentOcr.classes,
                                onSave = { editedList ->
                                    vm.saveExtractedTimetable(editedList)
                                },
                                onCancel = {
                                    vm.clearOcrState()
                                    step = 2
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- VERIFICATION SCREEN ---
@Composable
fun TimetableVerificationView(
    initialSlots: List<ExtractedClass>,
    onSave: (List<ExtractedClass>) -> Unit,
    onCancel: () -> Unit
) {
    var listState by remember { mutableStateOf(initialSlots) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Dialog form values
    var editSubject by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("") }
    var editEndTime by remember { mutableStateOf("") }
    var editRoom by remember { mutableStateOf("") }
    var editTeacher by remember { mutableStateOf("") }
    var editDaysOfWeek by remember { mutableStateOf(listOf<Int>()) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Verify Schedule", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onSave(listState) },
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(start = 8.dp)
                            .testTag("save_extracted_timetable_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, "Save")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Timetable")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    "We extracted the following class schedule. Review and edit any inaccuracies before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            items(listState.size) { index ->
                val slot = listState[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    slot.subjectName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (slot.room.isNotEmpty() || slot.teacher.isNotEmpty()) {
                                    Text(
                                        "${slot.room} • ${slot.teacher}".trim(' ', '•'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = {
                                    editingIndex = index
                                    editSubject = slot.subjectName
                                    editStartTime = slot.startTime
                                    editEndTime = slot.endTime
                                    editRoom = slot.room
                                    editTeacher = slot.teacher
                                    editDaysOfWeek = slot.daysOfWeek
                                    showEditDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(onClick = {
                                    listState = listState.filterIndexed { i, _ -> i != index }
                                }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, "Time", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${slot.startTime} - ${slot.endTime}", style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, "Days", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    slot.daysOfWeek.joinToString(", ") { getDayOfWeekShort(it) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        editingIndex = null
                        editSubject = ""
                        editStartTime = "09:00"
                        editEndTime = "09:50"
                        editRoom = ""
                        editTeacher = ""
                        editDaysOfWeek = listOf(1)
                        showEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Missing Class")
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Edit/Add Slot dialog
    if (showEditDialog) {
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (editingIndex == null) "Add Class details" else "Edit Slot details",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
                        label = { Text("Subject Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val context = LocalContext.current
                    val startParts = editStartTime.split(":")
                    val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                    val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0

                    val endParts = editEndTime.split(":")
                    val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 9
                    val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 50

                    val startTimeDialog = android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            editStartTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                        },
                        startHour,
                        startMin,
                        true
                    )

                    val endTimeDialog = android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            editEndTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                        },
                        endHour,
                        endMin,
                        true
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                            OutlinedTextField(
                                value = editStartTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Start Time") },
                                trailingIcon = {
                                    IconButton(onClick = { startTimeDialog.show() }) {
                                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Start Time")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { startTimeDialog.show() }
                            )
                        }

                        Box(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            OutlinedTextField(
                                value = editEndTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("End Time") },
                                trailingIcon = {
                                    IconButton(onClick = { endTimeDialog.show() }) {
                                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = "End Time")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { endTimeDialog.show() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Conducted Days:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val daysList = listOf(
                        1 to "M",
                        2 to "T",
                        3 to "W",
                        4 to "Th",
                        5 to "F",
                        6 to "Sa"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daysList.forEach { (dayInt, label) ->
                            val isSelected = editDaysOfWeek.contains(dayInt)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        editDaysOfWeek = if (isSelected) {
                                            editDaysOfWeek.filter { it != dayInt }
                                        } else {
                                            editDaysOfWeek + dayInt
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editRoom,
                        onValueChange = { editRoom = it },
                        label = { Text("Room / Location") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editTeacher,
                        onValueChange = { editTeacher = it },
                        label = { Text("Teacher Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = {
                            if (editSubject.isNotBlank() && editDaysOfWeek.isNotEmpty()) {
                                if (editingIndex != null) {
                                    val idx = editingIndex!!
                                    val updated = listState.toMutableList()
                                    updated[idx] = ExtractedClass(
                                        subjectName = editSubject,
                                        startTime = editStartTime,
                                        endTime = editEndTime,
                                        daysOfWeek = editDaysOfWeek,
                                        room = editRoom,
                                        teacher = editTeacher
                                    )
                                    listState = updated
                                } else {
                                    val newSlot = ExtractedClass(
                                        subjectName = editSubject,
                                        startTime = editStartTime,
                                        endTime = editEndTime,
                                        daysOfWeek = editDaysOfWeek,
                                        room = editRoom,
                                        teacher = editTeacher
                                    )
                                    listState = listState + newSlot
                                }
                                showEditDialog = false
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// Helper short text for days
private fun getDayOfWeekShort(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "Mon"
    }
}

// --- MAIN HOME CONTENT WITH BOTTOM NAVIGATION ---
@Composable
fun HomeScreen(vm: AttendanceViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Subjects, 2 = Calendar, 3 = Analytics/Calc, 4 = Notes/Settings
    var initialSettingsSubTab by remember { mutableStateOf(0) } // 0 = Notes, 1 = Reminders, 2 = Settings

    if (activeTab != 0) {
        androidx.activity.compose.BackHandler {
            activeTab = 0
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val items = listOf(
                            Triple(0, Icons.Default.Dashboard, "Home"),
                            Triple(1, Icons.Default.MenuBook, "Subjects"),
                            Triple(2, Icons.Default.CalendarToday, "Calendar"),
                            Triple(3, Icons.Default.Analytics, "Analytics"),
                            Triple(4, Icons.Default.MoreHoriz, "More")
                        )
                        
                        items.forEach { (index, icon, label) ->
                            val isSelected = activeTab == index
                            val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 1.0f, label = "ScaleTab")
                            val activeBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null, 
                                        onClick = {
                                            if (index == 4) initialSettingsSubTab = 0
                                            activeTab = index
                                        }
                                    )
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .testTag(
                                        when(index) {
                                            0 -> "nav_home_tab"
                                            1 -> "nav_subjects_tab"
                                            2 -> "nav_calendar_tab"
                                            3 -> "nav_analytics_tab"
                                            else -> "nav_settings_tab"
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) activeBgColor else Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 9.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
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
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> HomeDashboardTab(
                    vm = vm,
                    onNavigateToCalculator = { activeTab = 3 },
                    onNavigateToCalendar = { activeTab = 2 },
                    onNavigateToSettings = {
                        initialSettingsSubTab = 2
                        activeTab = 4
                    }
                )
                1 -> SubjectsTab(vm)
                2 -> CalendarTab(vm)
                3 -> PremiumAnalyticsTab(vm)
                4 -> NotesAndSettingsTab(vm, initialSubTab = initialSettingsSubTab)
            }
        }
    }
}

// --- TAB 0: HOME DASHBOARD ---
@Composable
fun HomeDashboardTab(
    vm: AttendanceViewModel,
    onNavigateToCalculator: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val statsList by vm.subjectAttendanceStats.collectAsState()
    val overview by vm.dashboardOverview.collectAsState(initial = DashboardOverview(100f, 0, 0, null, null, 0))
    val todaySched by vm.todayClasses.collectAsState()
    val todayDate by vm.todayDate.collectAsState()
    val prefs by vm.userPreferences.collectAsState(initial = UserPreference())
    val goal = prefs?.attendanceGoal ?: 75
    val allRecords by vm.attendanceRecords.collectAsState()

    val isTechGradient = (prefs?.themeMode ?: "SYSTEM") == "TECH_GRADIENT"
    val techGradientBrush = Brush.linearGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF9333EA)))

    var showExtraDialog by remember { mutableStateOf(false) }
    var selectedSubjectForDetails by remember { mutableStateOf<SubjectAttendanceStats?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (isTechGradient) {
                        Text(
                            "AttendEz",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                brush = techGradientBrush
                            )
                        )
                    } else {
                        Text(
                            "AttendEz",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Text("Track Easily. Attend Smartly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }

                Surface(
                    onClick = { onNavigateToSettings() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("dashboard_profile_avatar_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Dashboard Biometric Presence Summary Card with Bottom Sheet Trigger
        item {
            val dailyBiometricRecords by vm.dailyBiometricRecords.collectAsState()
            var showBiometricSheet by remember { mutableStateOf(false) }

            // Math computations for the quick status on Dashboard
            val totalTracked = dailyBiometricRecords.count { it.status == "PRESENT" || it.status == "ABSENT" }
            val presentDays = dailyBiometricRecords.count { it.status == "PRESENT" }
            val presenceRate = if (totalTracked > 0) (presentDays.toFloat() / totalTracked) * 100f else 0f

            // Calculate active streak (Treating Sunday as an exception and omitting it)
            val sortedActive = dailyBiometricRecords
                .filter { (it.status == "PRESENT" || it.status == "ABSENT") && !isSunday(it.date) }
                .sortedBy { it.date }
            var currStreak = 0
            for (i in sortedActive.indices.reversed()) {
                if (sortedActive[i].status == "PRESENT") {
                    currStreak++
                } else {
                    break
                }
            }

            // Sync targets
            val syncedGoal = goal
            val isOnTrack = presenceRate >= syncedGoal

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTechGradient) Modifier.border(
                            width = 1.5.dp,
                            brush = techGradientBrush,
                            shape = RoundedCornerShape(24.dp)
                        ) else Modifier
                    )
                    .testTag("dashboard_biometric_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                ),
                onClick = { showBiometricSheet = true }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Biometric Attendance",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Campus Daily Presence",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                            val todayRecord = dailyBiometricRecords.find { it.date == todayStr }
                            val todayStatus = todayRecord?.status

                            // Tick option for PRESENT
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (todayStatus == "PRESENT") StatusGreen 
                                        else StatusGreen.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (todayStatus == "PRESENT") Color.Transparent else StatusGreen.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (todayStatus == "PRESENT") {
                                            vm.deleteDailyBiometric(todayStr)
                                        } else {
                                            vm.updateBiometric(todayStr, "PRESENT")
                                        }
                                    }
                                    .testTag("dashboard_quick_present"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color = if (todayStatus == "PRESENT") Color.White else StatusGreen,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            // Cross option for ABSENT
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (todayStatus == "ABSENT") StatusRed 
                                        else StatusRed.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (todayStatus == "ABSENT") Color.Transparent else StatusRed.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (todayStatus == "ABSENT") {
                                            vm.deleteDailyBiometric(todayStr)
                                        } else {
                                            vm.updateBiometric(todayStr, "ABSENT")
                                        }
                                    }
                                    .testTag("dashboard_quick_absent"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✗",
                                    color = if (todayStatus == "ABSENT") Color.White else StatusRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Arrow icon
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Open Portal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
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
                                "Presence Rate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format("%.1f%%", presenceRate),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Active Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "🔥 $currStreak days",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = if (currStreak > 0) StatusOrange else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOnTrack) "On track for $syncedGoal% goal 🎉" else "Below target of $syncedGoal% ⚡",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnTrack) StatusGreen else StatusRed
                        )

                        Text(
                            "Tap to open tracker ↗",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Bottom Sheet Overlay Container
            if (showBiometricSheet) {
                BiometricAttendanceSheet(
                    dailyRecords = dailyBiometricRecords,
                    targetGoal = syncedGoal,
                    themeMode = prefs?.themeMode ?: "SYSTEM",
                    onDismissRequest = { showBiometricSheet = false },
                    onUpdateBiometric = { date, status ->
                        if (status == "NONE") {
                            vm.deleteDailyBiometric(date)
                        } else {
                            vm.updateBiometric(date, status)
                        }
                    },
                    onClearAll = {
                        vm.clearAllDailyBiometrics()
                    }
                )
            }
        }

        /*
        // Collapsed old biometric machine code block
        item {
            val biometricRecords by vm.biometricRecords.collectAsState(initial = emptyList())
            val registeredStudents by vm.registeredStudents.collectAsState(initial = emptyList())
            
            var selectedSubjectForBiometric by remember(statsList, todaySched) {
                mutableStateOf(
                    todaySched.firstOrNull()?.subject 
                        ?: statsList.firstOrNull()?.subject
                )
            }
            var selectedStudentProfile by remember(registeredStudents) {
                mutableStateOf(registeredStudents.firstOrNull())
            }
            
            var activeTab by remember { mutableStateOf("SCAN") } // SCAN, LOGS, STATS
            
            var isScanning by remember { mutableStateOf(false) }
            var scanStatusToRecord by remember { mutableStateOf<String?>(null) }
            var scanType by remember { mutableStateOf("Fingerprint") } // Fingerprint, Facial Recognition
            
            var courseDropdownExpanded by remember { mutableStateOf(false) }
            var studentDropdownExpanded by remember { mutableStateOf(false) }
            
            // Enrollment state
            var isRegistrationExpanded by remember { mutableStateOf(false) }
            var newNameInput by remember { mutableStateOf("") }
            var newRollInput by remember { mutableStateOf("") }
            
            val context = LocalContext.current

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTechGradient) Modifier.border(
                            width = 1.5.dp,
                            brush = techGradientBrush,
                            shape = RoundedCornerShape(24.dp)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // Header section: Machine Title and Online indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (scanType == "Fingerprint") Icons.Default.Fingerprint else Icons.Default.Face,
                                contentDescription = "Biometric Scanner",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Biometric Machine Portal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        // Active status indicator pulsing dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Text(
                                text = "SENSOR ON",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusGreen
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // SEGMENTED TAB SELECTORS (Pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            "SCAN" to "Machine",
                            "LOGS" to "Live Logs",
                            "STATS" to "Analytics"
                        )
                        tabs.forEach { (tabKey, tabLabel) ->
                            val isSelected = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary 
                                        else Color.Transparent
                                    )
                                    .clickable { activeTab = tabKey }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // TAB VIEWS
                    if (isScanning) {
                        // IMMERSIVE VERIFICATION LAYER
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val animatedScale by infiniteTransition.animateFloat(
                                initialValue = 0.95f,
                                targetValue = 1.25f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(850, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(animatedScale)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                )
                                Icon(
                                    imageVector = if (scanType == "Fingerprint") Icons.Default.Fingerprint else Icons.Default.Face,
                                    contentDescription = "Scanning",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Verifying ${selectedStudentProfile?.name}'s Biometrics...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Matching $scanType database template",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        when (activeTab) {
                            "SCAN" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // 1. Course Picker
                                    Text(
                                        "Select Department Course",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box {
                                        OutlinedCard(
                                            onClick = { courseDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = selectedSubjectForBiometric?.name ?: "Generate custom course first",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(Icons.Default.ArrowDropDown, "Course pick")
                                            }
                                        }
                                        
                                        DropdownMenu(
                                            expanded = courseDropdownExpanded,
                                            onDismissRequest = { courseDropdownExpanded = false }
                                        ) {
                                            if (statsList.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("No courses. Please add a subject first.") },
                                                    onClick = { courseDropdownExpanded = false }
                                                )
                                            } else {
                                                statsList.forEach { stat ->
                                                    DropdownMenuItem(
                                                        text = { Text(stat.subject.name) },
                                                        onClick = {
                                                            selectedSubjectForBiometric = stat.subject
                                                            courseDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // 2. Student Profile Picker
                                    Text(
                                        "Biometric Student Profile",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box {
                                        OutlinedCard(
                                            onClick = { studentDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = selectedStudentProfile?.name ?: "No student profiles",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = selectedStudentProfile?.rollNo ?: "Enroll student to simulate scan",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                Icon(Icons.Default.ArrowDropDown, "Student pick")
                                            }
                                        }
                                        
                                        DropdownMenu(
                                            expanded = studentDropdownExpanded,
                                            onDismissRequest = { studentDropdownExpanded = false }
                                        ) {
                                            registeredStudents.forEach { student ->
                                                DropdownMenuItem(
                                                    text = { Text("${student.name} (${student.rollNo})") },
                                                    onClick = {
                                                        selectedStudentProfile = student
                                                        studentDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // 3. Scanner Mode Selector (Fingerprint / Facial)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Verification Mode",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            listOf("Fingerprint", "Facial Scan").forEach { sMode ->
                                                val mSelected = scanType == sMode
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (mSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                                        .clickable { scanType = sMode }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = sMode,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = if (mSelected) MaterialTheme.colorScheme.primary 
                                                                else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // 4. Biometric Circle Action Buttons (Tick, Cross, Block)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val circleSize = 60.dp
                                        val iconSize = 28.dp
                                        val activeBlue = Color(0xFF35B5F2)
                                        val inactiveGray = Color(0xFFF4F5F7)
                                        val darkGraySymbol = Color(0xFF5E6C84)
                                        val borderStrokeColor = Color(0xFF97A0AF)
                                        
                                        // A. Tick Circle button
                                        Box(
                                            modifier = Modifier
                                                .size(circleSize)
                                                .clip(CircleShape)
                                                .background(activeBlue)
                                                .clickable {
                                                    val sub = selectedSubjectForBiometric
                                                    val stud = selectedStudentProfile
                                                    if (sub != null && stud != null) {
                                                        scanStatusToRecord = "PRESENT"
                                                        isScanning = true
                                                    } else {
                                                        Toast.makeText(context, "Enrolled students required to mark scan", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .testTag("biometric_present_btn"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Tick",
                                                tint = darkGraySymbol,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(32.dp))
                                        
                                        // B. Cross Circle button
                                        Box(
                                            modifier = Modifier
                                                .size(circleSize)
                                                .clip(CircleShape)
                                                .background(activeBlue)
                                                .clickable {
                                                    val sub = selectedSubjectForBiometric
                                                    val stud = selectedStudentProfile
                                                    if (sub != null && stud != null) {
                                                        scanStatusToRecord = "ABSENT"
                                                        isScanning = true
                                                    } else {
                                                        Toast.makeText(context, "Enrolled students required to mark scan", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .testTag("biometric_absent_btn"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cross",
                                                tint = darkGraySymbol,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // 5. Expandable Enroll Mini-Form
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isRegistrationExpanded = !isRegistrationExpanded }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Add, 
                                                    "Enroll Icon", 
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Enroll New Student Roll Profile",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Expand form",
                                                modifier = Modifier.rotate(if (isRegistrationExpanded) 180f else 0f)
                                            )
                                        }
                                        
                                        AnimatedVisibility(visible = isRegistrationExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = newNameInput,
                                                    onValueChange = { newNameInput = it },
                                                    label = { Text("Full Student Name") },
                                                    placeholder = { Text("E.g. James Miller") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                
                                                OutlinedTextField(
                                                    value = newRollInput,
                                                    onValueChange = { newRollInput = it },
                                                    label = { Text("Roll ID / Roll-Number") },
                                                    placeholder = { Text("E.g. CS2026-105") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                
                                                Button(
                                                    onClick = {
                                                        if (newNameInput.isNotBlank() && newRollInput.isNotBlank()) {
                                                            vm.registerStudent(newNameInput.trim(), newRollInput.trim())
                                                            Toast.makeText(context, "Student ${newNameInput.trim()} Enrolled!", Toast.LENGTH_SHORT).show()
                                                            // Set newly enrolled student as primary selection!
                                                            selectedStudentProfile = StudentProfile(newNameInput.trim(), newRollInput.trim())
                                                            newNameInput = ""
                                                            newRollInput = ""
                                                            isRegistrationExpanded = false
                                                        } else {
                                                            Toast.makeText(context, "Please provide Name & Roll ID", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Enroll & Register Biometrics", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            "LOGS" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Recent Logs (${biometricRecords.size})",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        
                                        if (biometricRecords.isNotEmpty()) {
                                            Text(
                                                "Clear Logs",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = StatusRed,
                                                modifier = Modifier.clickable {
                                                    vm.clearAllBiometrics()
                                                    Toast.makeText(context, "Cleared biometric records log history", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (biometricRecords.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(150.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.History, "No records", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("No records scanned yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                                Text("Use machine scanning tool to log", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    } else {
                                        // Scrollable recent logs
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            biometricRecords.take(30).forEach { record ->
                                                val timeFormated = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
                                                val statusColor = when (record.status) {
                                                    "PRESENT" -> StatusGreen
                                                    "ABSENT" -> StatusRed
                                                    else -> StatusOrange
                                                }
                                                val statusSymbol = when (record.status) {
                                                    "PRESENT" -> "Tick ✓"
                                                    "ABSENT" -> "Cross ❌"
                                                    else -> "Block 🚫"
                                                }
                                                
                                                OutlinedCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = if (record.scanType.contains("Face")) Icons.Default.Face else Icons.Default.Fingerprint,
                                                                    contentDescription = record.scanType,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = record.studentName,
                                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                                )
                                                            }
                                                            Text(
                                                                text = "${record.studentRoll} • ${record.subjectName}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                            Text(
                                                                text = "$timeFormated via ${record.scanType}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                        
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(statusColor.copy(alpha = 0.15f))
                                                                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = statusSymbol,
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                                    color = statusColor
                                                                )
                                                            }
                                                            
                                                            IconButton(
                                                                onClick = { vm.deleteBiometricRecord(record.id) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete record",
                                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                    modifier = Modifier.size(16.dp)
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
                            
                            "STATS" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Biometric Log Analytics",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    val totalRecords = biometricRecords.size
                                    val presentscans = biometricRecords.count { it.status == "PRESENT" }
                                    val absentscans = biometricRecords.count { it.status == "ABSENT" }
                                    val cancelledscans = biometricRecords.count { it.status == "CANCELLED" }
                                    
                                    val presentPercentage = if (totalRecords > 0) (presentscans.toFloat() / totalRecords) * 100f else 0f
                                    val absentPercentage = if (totalRecords > 0) (absentscans.toFloat() / totalRecords) * 100f else 0f
                                    val cancelledPercentage = if (totalRecords > 0) (cancelledscans.toFloat() / totalRecords) * 100f else 0f
                                    
                                    // Visual Scan Status Grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Card 1: Present Volume
                                        OutlinedCard(
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.3f)),
                                            colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.05f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Verified (✓)", style = MaterialTheme.typography.bodySmall, color = StatusGreen, fontWeight = FontWeight.Bold)
                                                Text("$presentscans Scans", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                                                Text(String.format("%.0f%% share", presentPercentage), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        
                                        // Card 2: Fail Volume (Absent)
                                        OutlinedCard(
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.3f)),
                                            colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.05f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Failure (❌)", style = MaterialTheme.typography.bodySmall, color = StatusRed, fontWeight = FontWeight.Bold)
                                                Text("$absentscans Scans", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                                                Text(String.format("%.0f%% share", absentPercentage), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        
                                        // Card 3: Class Suspended Volume (Block)
                                        OutlinedCard(
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, StatusOrange.copy(alpha = 0.3f)),
                                            colors = CardDefaults.cardColors(containerColor = StatusOrange.copy(alpha = 0.05f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Block (🚫)", style = MaterialTheme.typography.bodySmall, color = StatusOrange, fontWeight = FontWeight.Bold)
                                                Text("$cancelledscans Scans", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                                                Text(String.format("%.0f%% share", cancelledPercentage), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Total Scanner Volume Bar Chart simulation
                                    Text(
                                        "Overall Sensor Quality & Match Capacity",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Progress row
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Scanner Verification Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            Text(String.format("%.1f%% Successful", presentPercentage), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = StatusGreen)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(if (totalRecords > 0) (presentPercentage / 100f).coerceIn(0f, 1f) else 0f)
                                                    .background(StatusGreen)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Student Scan Rankings
                                    Text(
                                        "Student Registry Engagement",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    if (totalRecords == 0) {
                                        Text("Log scans to calculate individual analytics.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    } else {
                                        // Rank registered students by frequency of present scans in biometrics
                                        val studentRanks = registeredStudents.map { student ->
                                            val studentRecords = biometricRecords.filter { it.studentRoll == student.rollNo }
                                            val sTotal = studentRecords.size
                                            val sPresents = studentRecords.count { it.status == "PRESENT" }
                                            val sRate = if (sTotal > 0) (sPresents.toFloat() / sTotal) * 100f else 0f
                                            Triple(student, sTotal, sRate)
                                        }.sortedByDescending { it.second }
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 140.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            studentRanks.take(8).forEach { (student, sScans, sRate) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = student.name.firstOrNull()?.toString() ?: "",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text(student.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                                            Text(student.rollNo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Text("$sScans scans", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(
                                                                    if (sRate >= 75f) StatusGreen.copy(alpha = 0.1f) 
                                                                    else StatusRed.copy(alpha = 0.1f)
                                                                )
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = String.format("%.0f%% OK", sRate),
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = if (sRate >= 75f) StatusGreen else StatusRed
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
                    }
                    
                    // Unified Biometric Log Scan Handler Coroutine
                    if (isScanning && scanStatusToRecord != null) {
                        LaunchedEffect(isScanning) {
                            kotlinx.coroutines.delay(1100) // Beautiful verification delay
                            val sub = selectedSubjectForBiometric
                            val stud = selectedStudentProfile
                            val status = scanStatusToRecord
                            if (sub != null && stud != null && status != null) {
                                // Insert into Database
                                vm.addBiometricRecord(
                                    studentName = stud.name,
                                    studentRoll = stud.rollNo,
                                    subjectId = sub.id,
                                    subjectName = sub.name,
                                    status = status,
                                    scanType = scanType
                                )
                                Toast.makeText(
                                    context,
                                    "Biometrics Authenticated:\n${stud.name} (${stud.rollNo}) recorded as ${if (status == "CANCELLED") "NO CLASS" else status}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            isScanning = false
                            scanStatusToRecord = null
                        }
                    }
                }
            }
        }
        */

        // Today's schedule Header and filter row
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Today's Classes",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    TextButton(onClick = onNavigateToCalendar) {
                        Text("View Calendar")
                    }
                }

                Text(
                    SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Today's scheduled items
        if (todaySched.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircleOutline,
                            "Free Day",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No classes scheduled for today!", fontWeight = FontWeight.Bold)
                        Text("Enjoy your free day or prepare your notes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            val (todayLab, todayTheory) = todaySched.partition { isLabSubjectName(it.subject.name) }

            if (todayTheory.isNotEmpty()) {
                item {
                    Text(
                        "Theory Classes (${todayTheory.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(todayTheory) { itemToday ->
                    val subRecs = allRecords.filter { it.subjectId == itemToday.subject.id }
                    val itemStat = statsList.find { it.subject.id == itemToday.subject.id }
                    val activeBadge = if (itemStat != null) {
                        getSubjectActiveBadge(itemToday.subject, subRecs, itemStat, statsList, goal)
                    } else null
                    TodayScheduleRow(itemToday, activeBadge = activeBadge, onMark = { status ->
                        vm.markTodayAttendance(itemToday.subject.id, todayDate, status, false, itemToday.startTime)
                    })
                }
            }

            if (todayLab.isNotEmpty()) {
                item {
                    Text(
                        "Lab & Practical Classes (${todayLab.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(todayLab) { itemToday ->
                    val subRecs = allRecords.filter { it.subjectId == itemToday.subject.id }
                    val itemStat = statsList.find { it.subject.id == itemToday.subject.id }
                    val activeBadge = if (itemStat != null) {
                        getSubjectActiveBadge(itemToday.subject, subRecs, itemStat, statsList, goal)
                    } else null
                    TodayScheduleRow(itemToday, activeBadge = activeBadge, onMark = { status ->
                        vm.markTodayAttendance(itemToday.subject.id, todayDate, status, false, itemToday.startTime)
                    })
                }
            }
        }

        // Quick Actions Grid
        item {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Daily Reminder Toggle card
                val isReminderEnabled = prefs?.dailyReminderEnabled ?: false
                val reminderTime = prefs?.dailyReminderTime ?: "18:00"
                val homeContext = LocalContext.current
                val homeParts = reminderTime.split(":")
                val homeHour = homeParts.getOrNull(0)?.toIntOrNull() ?: 18
                val homeMinute = homeParts.getOrNull(1)?.toIntOrNull() ?: 0

                val homeTimePickerDialog = android.app.TimePickerDialog(
                    homeContext,
                    { _, hourOfDay, minute ->
                        val newTime = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
                        vm.updateDailyReminder(true, newTime)
                    },
                    homeHour,
                    homeMinute,
                    false
                )

                Card(
                    onClick = { homeTimePickerDialog.show() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 115.dp)
                        .then(
                            if (isTechGradient) Modifier.border(
                                width = 1.5.dp,
                                brush = techGradientBrush,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .testTag("action_reminder_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isReminderEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        } else {
                            if (isTechGradient) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alarm Clock Icon",
                                tint = if (isReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Switch(
                                checked = isReminderEnabled,
                                onCheckedChange = { vm.updateDailyReminder(it, reminderTime) },
                                modifier = Modifier.scale(0.75f)
                            )
                        }
                        Column {
                            Text(
                                text = if (isReminderEnabled) "Daily Reminder ON" else "Daily Reminder OFF",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                            Text(
                                text = "Time: ${formatTimeTo12HourIST(reminderTime)} ✏️",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isReminderEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Add Extra Class button
                Card(
                    onClick = { showExtraDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 115.dp)
                        .then(
                            if (isTechGradient) Modifier.border(
                                width = 1.5.dp,
                                brush = techGradientBrush,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .testTag("action_extra_class_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTechGradient) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isTechGradient) {
                            Icon(Icons.Default.AddBox, "Extra", tint = Color(0xFF9333EA))
                        } else {
                            Icon(Icons.Default.AddBox, "Extra", tint = MaterialTheme.colorScheme.secondary)
                        }
                        Text("+ Extra Class", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Smart Class Notifications Preference Card
        item {
            val subjectEnabled = prefs?.subjectRemindersEnabled ?: false
            val labEnabled = prefs?.labRemindersEnabled ?: false
            val reminderTime = prefs?.reminderTimeMinutes ?: 10
            
            val localPermissionLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Handled
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTechGradient) Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp)
                        ) else Modifier
                    )
                    .testTag("dashboard_smart_notifications_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTechGradient) Color.Black.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (isTechGradient) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Smart Reminders",
                                tint = if (isTechGradient) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Smart Class Reminders",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isTechGradient) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Auto-reminder before classes start",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTechGradient) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subject Reminders Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextChecked = !subjectEnabled
                                if (nextChecked) {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        localPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                vm.updateSmartClassNotifications(nextChecked, labEnabled, reminderTime)
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subject Reminders",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isTechGradient) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Alert before theory classes",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTechGradient) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = subjectEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        localPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                vm.updateSmartClassNotifications(checked, labEnabled, reminderTime)
                            },
                            modifier = Modifier.testTag("toggle_subject_reminders")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Lab Reminders Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextChecked = !labEnabled
                                if (nextChecked) {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        localPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                vm.updateSmartClassNotifications(subjectEnabled, nextChecked, reminderTime)
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lab Reminders",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isTechGradient) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Alert before lab sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTechGradient) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = labEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        localPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                vm.updateSmartClassNotifications(subjectEnabled, checked, reminderTime)
                            },
                            modifier = Modifier.testTag("toggle_lab_reminders")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isTechGradient) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Customizable minutes selection
                    Text(
                        text = "Reminder Lead Time",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isTechGradient) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            val isSelected = mins == reminderTime
                            val chipBgColor = if (isSelected) {
                                if (isTechGradient) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary
                            } else {
                                if (isTechGradient) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                            val chipContentColor = if (isSelected) {
                                if (isTechGradient) Color.White else MaterialTheme.colorScheme.onPrimary
                            } else {
                                if (isTechGradient) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBgColor)
                                    .clickable {
                                        vm.updateSmartClassNotifications(subjectEnabled, labEnabled, mins)
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("reminder_chip_$mins"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m before",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = chipContentColor
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettings() }
                    .testTag("dashboard_settings_shortcut"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Quick Settings",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Configure Preferences",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Adjust your target goal percentage, daily reminders, or filter class lab groups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Navigate settings",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            SameerPandeyFooter()
        }
    }

    if (showExtraDialog) {
        AddExtraClassDialog(vm, onDismiss = { showExtraDialog = false })
    }

    selectedSubjectForDetails?.let { sStats ->
        SubjectAnalyticsDetailedDialog(
            stats = sStats,
            goal = goal,
            allRecords = allRecords,
            onDismiss = { selectedSubjectForDetails = null }
        )
    }
}

// Circular progress drawing helper
@Composable
fun CircularAttendanceGraph(percentage: Float, color: Color) {
    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(88.dp)) {
            // Track
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 24f, cap = StrokeCap.Round)
            )
            // Progress
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = percentage * 3.6f,
                useCenter = false,
                style = Stroke(width = 24f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = String.format("%.0f%%", percentage),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color)
        )
    }
}

// Color Selector helper base on criteria
fun getAttendanceColor(percentage: Float, goal: Int): Color {
    return when {
        percentage >= goal -> StatusGreen
        percentage >= (goal - 10) -> StatusOrange
        else -> StatusRed
    }
}

// Convert string name to material icon
fun getSubjectIcon(iconName: String): ImageVector {
    return when (iconName) {
        "School" -> Icons.Default.School
        "MenuBook" -> Icons.Default.MenuBook
        "Science" -> Icons.Default.Science
        "Calculate" -> Icons.Default.Calculate
        "Computer" -> Icons.Default.Computer
        "Palette" -> Icons.Default.Palette
        "MusicNote" -> Icons.Default.MusicNote
        "Sports" -> Icons.Default.SportsBasketball
        "Language" -> Icons.Default.Language
        "Public" -> Icons.Default.Public
        "Museum" -> Icons.Default.Museum
        "Engineering" -> Icons.Default.Build
        else -> Icons.Default.School
    }
}

// Today's Schedule Card Row
@Composable
fun TodayScheduleRow(
    classInfo: ScheduledClassToday,
    activeBadge: Pair<String, Color>? = null,
    onMark: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular icon container with selected custom color
            val subjectColor = Color(android.graphics.Color.parseColor(classInfo.subject.colorHex))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(subjectColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getSubjectIcon(classInfo.subject.iconName),
                    contentDescription = null,
                    tint = subjectColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        classInfo.subject.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
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
                    "${classInfo.startTime} - ${classInfo.endTime} • ${classInfo.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Interactive one-tap buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadgeButton("P", "PRESENT", classInfo.currentStatus, StatusGreen, onMark)
                StatusBadgeButton("A", "ABSENT", classInfo.currentStatus, StatusRed, onMark)
            }
        }
    }
}

@Composable
fun StatusBadgeButton(label: String, actionStatus: String, currentStatus: String, color: Color, onClick: (String) -> Unit) {
    val isSelected = currentStatus == actionStatus
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) color else color.copy(alpha = 0.1f))
            .clickable {
                if (isSelected) {
                    onClick("UNMARKED")
                } else {
                    onClick(actionStatus)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else color
        )
    }
}

// --- TAB 1: SUBJECTS LIST & MANAGEMENT ---
@Composable
fun SubjectsTab(vm: AttendanceViewModel) {
    val statsList by vm.subjectAttendanceStats.collectAsState()
    val preferences by vm.userPreferences.collectAsState(initial = UserPreference())
    val goal = preferences?.attendanceGoal ?: 75
    val allRecords by vm.attendanceRecords.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSubjectForDetails by remember { mutableStateOf<SubjectAttendanceStats?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_subject_fab")
            ) {
                Icon(Icons.Default.Add, "Add Subject")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Subjects List",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("Goal: $goal%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (statsList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MenuBook, "Empty", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No subjects added yet.", fontWeight = FontWeight.Bold)
                        Text("Tap + to add a subject manually", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                val (labStats, theoryStats) = statsList.partition { isLabSubjectName(it.subject.name) }

                if (theoryStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "Theory Subjects (${theoryStats.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(theoryStats) { stats ->
                        val subRecords = allRecords.filter { it.subjectId == stats.subject.id }
                        val activeBadge = getSubjectActiveBadge(stats.subject, subRecords, stats, statsList, goal)
                        SubjectCard(
                            stats = stats,
                            goal = goal,
                            activeBadge = activeBadge,
                            onCardClick = { selectedSubjectForDetails = stats },
                            onDelete = {
                                vm.deleteSubject(stats.subject)
                            }
                        )
                    }
                }

                if (labStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "Lab & Practical Subjects (${labStats.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(labStats) { stats ->
                        val subRecords = allRecords.filter { it.subjectId == stats.subject.id }
                        val activeBadge = getSubjectActiveBadge(stats.subject, subRecords, stats, statsList, goal)
                        SubjectCard(
                            stats = stats,
                            goal = goal,
                            activeBadge = activeBadge,
                            onCardClick = { selectedSubjectForDetails = stats },
                            onDelete = {
                                vm.deleteSubject(stats.subject)
                            }
                        )
                    }
                }
            }

            item {
                SameerPandeyFooter()
            }
        }
    }

    if (showAddDialog) {
        ManualAddSubjectDialog(vm, onDismiss = { showAddDialog = false })
    }

    selectedSubjectForDetails?.let { sStats ->
        SubjectAnalyticsDetailedDialog(
            stats = sStats,
            goal = goal,
            allRecords = allRecords,
            onDismiss = { selectedSubjectForDetails = null }
        )
    }
}

@Composable
fun SubjectAnalyticsDetailedDialog(
    stats: SubjectAttendanceStats,
    goal: Int,
    allRecords: List<AttendanceRecord>,
    onDismiss: () -> Unit
) {
    val subRecs = allRecords.filter { it.subjectId == stats.subject.id }
    val presentCount = subRecs.count { it.status == "PRESENT" }
    val absentCount = subRecs.count { it.status == "ABSENT" }
    val cancelledCount = subRecs.count { it.status == "CANCELLED" }
    
    val subjectColor = Color(android.graphics.Color.parseColor(stats.subject.colorHex))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with custom icon & name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(subjectColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getSubjectIcon(stats.subject.iconName),
                                contentDescription = stats.subject.name,
                                tint = subjectColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stats.subject.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val activeBadge = getSubjectActiveBadge(stats.subject, subRecs, stats, listOf(stats), goal)
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
                            if (stats.subject.room.isNotEmpty() || stats.subject.teacher.isNotEmpty()) {
                                Text(
                                    "${stats.subject.room} • ${stats.subject.teacher}".trim(' ', '•'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Large Attendance Percentage Callout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(subjectColor.copy(alpha = 0.05f))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Attendance Percentage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f%%", stats.percentage),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            color = getAttendanceColor(stats.percentage, goal)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Linear tracker
                    LinearProgressIndicator(
                        progress = { stats.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = getAttendanceColor(stats.percentage, goal),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Current Status: " + when {
                            stats.percentage >= goal -> "Sufficient (Above $goal%)"
                            stats.percentage >= (goal - 10) -> "Warning level"
                            else -> "Critical shortage"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = getAttendanceColor(stats.percentage, goal)
                        )
                    )
                }

                // Stats breakdown grid
                Text("Conducted Classes Breakdown", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Attended
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusGreen.copy(alpha = 0.08f))
                            .border(1.dp, StatusGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Attended", style = MaterialTheme.typography.bodySmall, color = StatusGreen, maxLines = 1)
                        Text("$presentCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = StatusGreen))
                    }

                    // Missed (Absent)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusRed.copy(alpha = 0.08f))
                            .border(1.dp, StatusRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Missed", style = MaterialTheme.typography.bodySmall, color = StatusRed, maxLines = 1)
                        Text("$absentCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = StatusRed))
                    }

                    // Cancelled/Holiday
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusOrange.copy(alpha = 0.08f))
                            .border(1.dp, StatusOrange.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Cancelled", style = MaterialTheme.typography.bodySmall, color = StatusOrange, maxLines = 1)
                        Text("$cancelledCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = StatusOrange))
                    }
                }

                // Detailed Action-Oriented Recommendation Analysis
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Recommendation & Analysis",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val isSafe = stats.percentage >= goal
                    if (isSafe) {
                        val allowedToMiss = calculateAllowedToMiss(stats.attendedCount, stats.conductedCount, goal)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, "Safe", tint = StatusGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (allowedToMiss > 0)
                                    "You can safely miss the next $allowedToMiss classes while staying above the $goal% goal."
                                else
                                    "Your attendance is currently safe. Maintain this regular participation to hit your goal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val requiredPresent = calculateRequiredClasses(stats.attendedCount, stats.conductedCount, goal)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, "Shortage", tint = StatusRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Attending target alert: You MUST attend the next $requiredPresent classes consecutively to bring your attendance back up to $goal%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Metrics totals
                    Text(
                        "Total classes conducted: ${stats.conductedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = subjectColor)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun SubjectCard(
    stats: SubjectAttendanceStats,
    goal: Int,
    activeBadge: Pair<String, Color>? = null,
    onCardClick: () -> Unit,
    onDelete: () -> Unit
) {
    val subjectColor = Color(android.graphics.Color.parseColor(stats.subject.colorHex))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .border(
                1.dp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), 
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("subject_card_${stats.subject.name.replace(" ", "_")}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(subjectColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getSubjectIcon(stats.subject.iconName),
                            contentDescription = stats.subject.name,
                            tint = subjectColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stats.subject.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
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
                        if (stats.subject.teacher.isNotEmpty() || stats.subject.room.isNotEmpty()) {
                            Text(
                                "${stats.subject.room} • ${stats.subject.teacher}".trim(' ', '•'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Percentage indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${stats.attendedCount} attended of ${stats.conductedCount} logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    String.format("%.1f%%", stats.percentage),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = getAttendanceColor(stats.percentage, goal)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { stats.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = getAttendanceColor(stats.percentage, goal),
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            )

            // Warning or safety message
            Spacer(modifier = Modifier.height(12.dp))
            val message = if (stats.percentage >= goal) {
                val allowedToMiss = calculateAllowedToMiss(stats.attendedCount, stats.conductedCount, goal)
                if (allowedToMiss > 0) "Safe: You can miss next $allowedToMiss classes." else "Safe: Maintaining target ratio."
            } else {
                val requiredPresent = calculateRequiredClasses(stats.attendedCount, stats.conductedCount, goal)
                "Shortage: Attend next $requiredPresent classes consecutively to reach $goal%."
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val indicatorColor = getAttendanceColor(stats.percentage, goal)
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = indicatorColor
                )
            }
        }
    }
}

// Calculations helper
fun calculateAllowedToMiss(attended: Int, conducted: Int, target: Int): Int {
    if (conducted == 0) return 0
    var futureConducted = conducted
    var missCount = 0
    while (true) {
        futureConducted++
        val ratio = (attended.toFloat() / futureConducted.toFloat()) * 100f
        if (ratio >= target) {
            missCount++
        } else {
            break
        }
    }
    return missCount
}

fun calculateRequiredClasses(attended: Int, conducted: Int, target: Int): Int {
    if (conducted == 0) return 0
    var futureAttended = attended
    var futureConducted = conducted
    var required = 0
    while (true) {
        val ratio = (futureAttended.toFloat() / futureConducted.toFloat()) * 100f
        if (ratio >= target) {
            break
        }
        futureAttended++
        futureConducted++
        required++
    }
    return required
}

// ADD/EDIT FORMS
@Composable
fun ManualAddSubjectDialog(vm: AttendanceViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#3F51B5") }
    var iconName by remember { mutableStateOf("School") }
    var isLab by remember { mutableStateOf(false) }

    var daysSelected by remember { mutableStateOf(listOf<Int>()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("09:50") }

    val colors = listOf("#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#9C27B0", "#FF9800", "#E91E63", "#009688")
    val icons = listOf(
        "School" to "School",
        "MenuBook" to "Book",
        "Science" to "Science",
        "Calculate" to "Math",
        "Computer" to "Comp",
        "Palette" to "Art",
        "MusicNote" to "Music",
        "Sports" to "Sports",
        "Language" to "Lang",
        "Public" to "Globe",
        "Museum" to "Hist",
        "Engineering" to "Eng"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add New Subject",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name") },
                    modifier = Modifier.fillMaxWidth().testTag("subject_input_name")
                )

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Faculty / Teacher (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room Number (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lab / Practical Class", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Requires hands-on laboratory or practical sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = isLab,
                        onCheckedChange = { 
                            isLab = it
                            if (it && iconName == "School") {
                                iconName = "Computer"
                            } else if (!it && iconName == "Computer") {
                                iconName = "School"
                            }
                        },
                        modifier = Modifier.testTag("subject_is_lab_switch")
                    )
                }

                Text("Conducted Days (Optional):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val daysList = listOf(
                        1 to "M",
                        2 to "T",
                        3 to "W",
                        4 to "Th",
                        5 to "F",
                        6 to "Sa"
                    )
                    daysList.forEach { (dayInt, label) ->
                        val isSelected = daysSelected.contains(dayInt)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    daysSelected = if (isSelected) {
                                        daysSelected.filter { it != dayInt }
                                    } else {
                                        daysSelected + dayInt
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (daysSelected.isNotEmpty()) {
                    val context = LocalContext.current
                    val startParts = startTime.split(":")
                    val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                    val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0

                    val endParts = endTime.split(":")
                    val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 9
                    val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 50

                    val startTimeDialog = android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            startTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                        },
                        startHour,
                        startMin,
                        true
                    )

                    val endTimeDialog = android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            endTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                        },
                        endHour,
                        endMin,
                        true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Start Time") },
                                trailingIcon = {
                                    IconButton(onClick = { startTimeDialog.show() }) {
                                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Start Time")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { startTimeDialog.show() }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("End Time") },
                                trailingIcon = {
                                    IconButton(onClick = { endTimeDialog.show() }) {
                                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = "End Time")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { endTimeDialog.show() }
                            )
                        }
                    }
                }

                Text("Choose Subject Tag Color:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(c)))
                                .border(
                                    width = if (colorHex == c) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = c }
                        )
                    }
                }

                Text("Choose Subject Icon:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(icons) { (iconVal, iconLabel) ->
                        val isSelected = iconName == iconVal
                        val itemColor = Color(android.graphics.Color.parseColor(colorHex))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) itemColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) itemColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { iconName = iconVal }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = getSubjectIcon(iconVal),
                                    contentDescription = iconLabel,
                                    tint = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = iconLabel,
                                    fontSize = 10.sp,
                                    color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                val finalName = if (isLab && !name.lowercase(Locale.getDefault()).contains("lab")) "$name (Lab)" else name
                                vm.addSubject(
                                    finalName,
                                    teacher,
                                    room,
                                    colorHex,
                                    iconName,
                                    daysOfWeek = daysSelected,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("save_manual_subject_button")
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

// EXTRA CLASSES DIA
@Composable
fun AddExtraClassDialog(
    vm: AttendanceViewModel,
    initialDateStr: String = vm.getCurrentDateStr(),
    onDismiss: () -> Unit
) {
    val subjects by vm.subjects.collectAsState()
    var selectedSubId by remember { mutableStateOf<Long?>(null) }
    var timeStr by remember { mutableStateOf("09:00") }
    var dateStr by remember { mutableStateOf(initialDateStr) }
    var statusState by remember { mutableStateOf("PRESENT") } // PRESENT or ABSENT

    LaunchedEffect(subjects) {
        if (subjects.isNotEmpty()) {
            selectedSubId = subjects[0].id
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add Extra Class",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text("Select Subject:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                if (subjects.isEmpty()) {
                    Text("No subjects available. Add some first!", color = MaterialTheme.colorScheme.error)
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    val currentSelectionName = subjects.find { it.id == selectedSubId }?.name ?: "Select Subject"

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentSelectionName)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            subjects.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedSubId = s.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status: ", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = statusState == "PRESENT",
                        onClick = { statusState = "PRESENT" }
                    )
                    Text("Present")

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = statusState == "ABSENT",
                        onClick = { statusState = "ABSENT" }
                    )
                    Text("Absent")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val subId = selectedSubId
                            if (subId != null) {
                                vm.addExtraClassAndMark(subId, dateStr, timeStr, statusState)
                                onDismiss()
                            }
                        },
                        enabled = selectedSubId != null,
                        modifier = Modifier.testTag("submit_extra_class_button")
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

// --- TAB 2: CALENDAR & HISTORY BREAKDOWN ---
@Composable
fun CalendarTab(vm: AttendanceViewModel) {
    val selectedDate by vm.selectedCalendarDate.collectAsState()
    val allRecords by vm.attendanceRecords.collectAsState()
    val subjects by vm.subjects.collectAsState()
    val todaySched by vm.calendarClasses.collectAsState()
    val statsList by vm.subjectAttendanceStats.collectAsState()
    val preferences by vm.userPreferences.collectAsState(initial = UserPreference())
    val goal = preferences?.attendanceGoal ?: 75

    val currentRecords = allRecords.filter { it.dateStr == selectedDate }
    val subjectsMap = subjects.associateBy { it.id }
    
    var showAddExtraDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Calendar Archives",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Dedicated custom Calendar grid Composable
        item {
            CustomMonthCalendar(
                selectedDate = selectedDate,
                records = allRecords,
                onDateSelected = { vm.selectDate(it) }
            )
        }

        // Records header
        item {
            val friendlyDate = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(selectedDate)!!
                outputFormat.format(date)
            } catch (e: Exception) {
                selectedDate
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Logs for $friendlyDate",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Mark scheduled classes or log extra ones below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                FilledTonalButton(
                    onClick = { showAddExtraDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_extra_past_day_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add extra log on this day", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Extra", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val todaySchedIds = todaySched.map { it.subject.id }.toSet()
        val extraAndOtherLogs = currentRecords.filter { it.isExtraClass || !todaySchedIds.contains(it.subjectId) }

        if (todaySched.isEmpty() && extraAndOtherLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty, 
                            contentDescription = "None", 
                            modifier = Modifier.size(48.dp), 
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No scheduled classes or logs for this day.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddExtraDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mark Attendance Manually")
                        }
                    }
                }
            }
        } else {
            // 1. Scheduled Classes Section (from timetable)
            if (todaySched.isNotEmpty()) {
                val (todayLab, todayTheory) = todaySched.partition { isLabSubjectName(it.subject.name) }

                if (todayTheory.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Scheduled Theory Classes (${todayTheory.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    items(todayTheory) { itemToday ->
                        val subRecs = allRecords.filter { it.subjectId == itemToday.subject.id }
                        val itemStat = statsList.find { it.subject.id == itemToday.subject.id }
                        val activeBadge = if (itemStat != null) {
                            getSubjectActiveBadge(itemToday.subject, subRecs, itemStat, statsList, goal)
                        } else null
                        TodayScheduleRow(
                            classInfo = itemToday, 
                            activeBadge = activeBadge,
                            onMark = { status ->
                                vm.markTodayAttendance(
                                    subjectId = itemToday.subject.id, 
                                    dateStr = selectedDate, 
                                    status = status, 
                                    isExtraClass = false, 
                                    timeStr = itemToday.startTime
                                )
                            }
                        )
                    }
                }

                if (todayLab.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Scheduled Lab & Practical Classes (${todayLab.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    items(todayLab) { itemToday ->
                        val subRecs = allRecords.filter { it.subjectId == itemToday.subject.id }
                        val itemStat = statsList.find { it.subject.id == itemToday.subject.id }
                        val activeBadge = if (itemStat != null) {
                            getSubjectActiveBadge(itemToday.subject, subRecs, itemStat, statsList, goal)
                        } else null
                        TodayScheduleRow(
                            classInfo = itemToday, 
                            activeBadge = activeBadge,
                            onMark = { status ->
                                vm.markTodayAttendance(
                                    subjectId = itemToday.subject.id, 
                                    dateStr = selectedDate, 
                                    status = status, 
                                    isExtraClass = false, 
                                    timeStr = itemToday.startTime
                                )
                            }
                        )
                    }
                }
            }

            // 2. Extra Classes or Manual Logs Section
            if (extraAndOtherLogs.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Extra Classes / Ad-hoc Logs",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                items(extraAndOtherLogs) { rec ->
                    val sub = subjectsMap[rec.subjectId]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sub != null) {
                                    val subjectColor = Color(android.graphics.Color.parseColor(sub.colorHex))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(subjectColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getSubjectIcon(sub.iconName),
                                            contentDescription = null,
                                            tint = subjectColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column {
                                    Text(sub?.name ?: "[Subject Deleted]", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (rec.isExtraClass) Icons.Default.AddCircle else Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                             if (rec.isExtraClass) "Extra, ${rec.timeStr}" else "Manual, ${rec.timeStr}",
                                            style = MaterialTheme.typography.bodySmall,
                                             color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Status Badge indicator & toggle back
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (rec.status) {
                                            "PRESENT" -> StatusGreen.copy(alpha = 0.15f)
                                             "ABSENT" -> StatusRed.copy(alpha = 0.15f)
                                            else -> StatusOrange.copy(alpha = 0.15f)
                                        }
                                    )
                                    .clickable {
                                        vm.deleteAttendanceRecord(rec.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        rec.status,
                                        fontWeight = FontWeight.Bold,
                                        color = when (rec.status) {
                                            "PRESENT" -> StatusGreen
                                            "ABSENT" -> StatusRed
                                            else -> StatusOrange
                                        },
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete record",
                                        tint = when (rec.status) {
                                            "PRESENT" -> StatusGreen.copy(alpha = 0.7f)
                                            "ABSENT" -> StatusRed.copy(alpha = 0.7f)
                                             else -> StatusOrange.copy(alpha = 0.7f)
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SameerPandeyFooter()
            }
        }
    }

    if (showAddExtraDialog) {
        AddExtraClassDialog(vm = vm, initialDateStr = selectedDate, onDismiss = { showAddExtraDialog = false })
    }
}

// CUSTOM MONTH CALENDAR COMPOSABLE
@Composable
fun CustomMonthCalendar(
    selectedDate: String,
    records: List<AttendanceRecord>,
    onDateSelected: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    var currentYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) } // 0 = Jan, 11 = Dec

    val monthHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).let { sdf ->
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, currentYear)
        c.set(Calendar.MONTH, currentMonth)
        sdf.format(c.time)
    }

    // List of days in selected month
    val daysInMonth = remember(currentYear, currentMonth) {
        val list = mutableListOf<String>()
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, currentYear)
        c.set(Calendar.MONTH, currentMonth)
        c.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 2 = Mon...
        // Padding for grid
        val paddingDays = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - Calendar.MONDAY
        for (i in 0 until paddingDays) {
            list.add("")
        }

        val maxDays = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (day in 1..maxDays) {
            c.set(Calendar.DAY_OF_MONTH, day)
            list.add(format.format(c.time))
        }
        list
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month navigations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentMonth == 0) {
                        currentMonth = 11
                        currentYear--
                    } else {
                        currentMonth--
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, "Prev")
                }

                Text(monthHeader, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                IconButton(onClick = {
                    if (currentMonth == 11) {
                        currentMonth = 0
                        currentYear++
                    } else {
                        currentMonth++
                    }
                }) {
                    Icon(Icons.Default.ChevronRight, "Next")
                }
            }

            // Headers for Day Labels
            Row(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                labels.forEach { l ->
                    Text(
                        l,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Grid matrix
            val rows = daysInMonth.chunked(7)
            rows.forEach { rowDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowDays.forEach { dateStr ->
                        if (dateStr.isEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val isSelected = dateStr == selectedDate
                            val dayNum = dateStr.split("-").last().toInt().toString()

                            // Calculate metrics color for indicator
                            val dayRecs = records.filter { it.dateStr == dateStr }
                            val indicatorColor = when {
                                dayRecs.isEmpty() -> Color.Transparent
                                dayRecs.all { it.status == "PRESENT" } -> StatusGreen
                                dayRecs.all { it.status == "ABSENT" } -> StatusRed
                                else -> StatusOrange
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .clickable { onDateSelected(dateStr) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        dayNum,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    if (indicatorColor != Color.Transparent) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else indicatorColor)
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
}

// --- TAB 3: ANALYTICS, CALCULATORS & RISKS ---
@Composable
fun AnalyticsAndCalcTab(vm: AttendanceViewModel) {
    val statsList by vm.subjectAttendanceStats.collectAsState()
    val overview by vm.dashboardOverview.collectAsState(initial = DashboardOverview(100f, 0, 0, null, null, 0))
    val prefs by vm.userPreferences.collectAsState(initial = UserPreference())
    val goal = prefs?.attendanceGoal ?: 75

    // Calc states
    var targetInput by remember { mutableStateOf(goal.toString()) }
    var currentAttendedInput by remember { mutableStateOf("") }
    var currentConductedInput by remember { mutableStateOf("") }
    var expectedAbsencesState by remember { mutableStateOf("0") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Analytics & Forecasting",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Custom Visual Trends Canvas Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subject Attendance Levels", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (statsList.isEmpty()) {
                        Text("Add subjects to view comparison chart.")
                    } else {
                        CustomSubjectBarChart(statsList = statsList)
                    }
                }
            }
        }

        // Risk Category Analysis
        item {
            Text("Risk Analysis Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        val safe = statsList.filter { it.percentage >= goal }
        val warning = statsList.filter { it.percentage >= (goal - 10) && it.percentage < goal }
        val critical = statsList.filter { it.percentage < (goal - 10) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskSummaryCard("Critical (< ${goal - 10}%)", critical, StatusRed, goal)
                RiskSummaryCard("Warning (${goal - 10}% - $goal%)", warning, StatusOrange, goal)
                RiskSummaryCard("Safe (>= $goal%)", safe, StatusGreen, goal)
            }
        }

        // Dedicated Required Attendance Calculator UI
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("req_attendance_calculator_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints {
                    val useHorizontalLayout = maxWidth > 480.dp
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Attendance Calculator Ratio Tool",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (useHorizontalLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = currentAttendedInput,
                                    onValueChange = { currentAttendedInput = it },
                                    label = { Text("Classes Attended") },
                                    modifier = Modifier.weight(1f).testTag("calc_attended_input")
                                )

                                OutlinedTextField(
                                    value = currentConductedInput,
                                    onValueChange = { currentConductedInput = it },
                                    label = { Text("Classes Conducted") },
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = targetInput,
                                    onValueChange = { targetInput = it },
                                    label = { Text("Target (%)") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = currentAttendedInput,
                                onValueChange = { currentAttendedInput = it },
                                label = { Text("Classes Attended") },
                                modifier = Modifier.fillMaxWidth().testTag("calc_attended_input")
                            )

                            OutlinedTextField(
                                value = currentConductedInput,
                                onValueChange = { currentConductedInput = it },
                                label = { Text("Classes Conducted") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = targetInput,
                                onValueChange = { targetInput = it },
                                label = { Text("Target Attendance Percentage (%)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Compute result
                        val att = currentAttendedInput.toIntOrNull() ?: 0
                        val cond = currentConductedInput.toIntOrNull() ?: 0
                        val tgt = targetInput.toIntOrNull() ?: goal

                        if (cond > 0 && att <= cond && tgt in 1..100) {
                            val currentPct = (att.toFloat() / cond.toFloat()) * 100f
                            Text(
                                String.format("Current Ratio: %.1f%%", currentPct),
                                fontWeight = FontWeight.SemiBold,
                                color = getAttendanceColor(currentPct, tgt)
                            )

                            if (currentPct >= tgt) {
                                val allowedToMiss = calculateAllowedToMiss(att, cond, tgt)
                                Text(
                                    "Safe! You can miss next $allowedToMiss classes continuously and stay above target $tgt%.",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen
                                )
                            } else {
                                val req = calculateRequiredClasses(att, cond, tgt)
                                Text(
                                    "Attention needed: Attend next $req classes continuously to reach target $tgt%.",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusRed
                                )
                            }
                        } else {
                            Text("Fill input slots safely to analyze required continuous attendance ratio metrics.")
                        }
                    }
                }
            }
        }

        // Prediction UI
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints {
                    val useHorizontalLayout = maxWidth > 480.dp
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Future Absence Predictor State",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        val futAbs = expectedAbsencesState.toIntOrNull() ?: 0
                        val forecastedText = if (overview.totalConducted > 0) {
                            val futureTargetTotal = overview.totalConducted + 10
                            val forecastedAttended = maxOf(0, overview.totalAttended + 10 - futAbs)
                            val forecastedPct = if (futureTargetTotal == 0) 100f else (forecastedAttended.toFloat() / futureTargetTotal) * 100f
                            String.format("Forecasted Average in 10 classes: %.1f%%", forecastedPct) to getAttendanceColor(forecastedPct, goal)
                        } else {
                            "Please log attendance history in calendar first to forecast averages." to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        if (useHorizontalLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = expectedAbsencesState,
                                    onValueChange = { expectedAbsencesState = it },
                                    label = { Text("Expected Future Absences") },
                                    modifier = Modifier.weight(1f)
                                )
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = forecastedText.first,
                                        fontWeight = FontWeight.Bold,
                                        color = forecastedText.second
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = expectedAbsencesState,
                                onValueChange = { expectedAbsencesState = it },
                                label = { Text("Target/Expected Future Absences") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = forecastedText.first,
                                fontWeight = FontWeight.Bold,
                                color = forecastedText.second
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// Simple Custom canvas bar chart
@Composable
fun CustomSubjectBarChart(statsList: List<SubjectAttendanceStats>) {
    val maxConducted = statsList.maxOf { it.conductedCount }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / (statsList.size * 2f)
            val canvasHeight = size.height - 40f

            statsList.forEachIndexed { idx, stat ->
                val ratio = if (stat.conductedCount == 0) 1f else stat.attendedCount.toFloat() / maxConducted.toFloat()
                val barHeight = canvasHeight * ratio
                val x = idx * (barWidth * 2f) + (barWidth / 2f)
                val y = canvasHeight - barHeight

                // Draw Bar
                drawRect(
                    color = Color(android.graphics.Color.parseColor(stat.subject.colorHex)),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )

                // Label string text
                // Let's draw tiny dots representing days
            }

            // Draw baseline
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, canvasHeight),
                end = Offset(size.width, canvasHeight),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun RiskSummaryCard(title: String, list: List<SubjectAttendanceStats>, labelColor: Color, goal: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, labelColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = labelColor, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            if (list.isEmpty()) {
                Text("No subjects in this category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                list.forEach { stat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stat.subject.name, style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.1f%%", stat.percentage), fontWeight = FontWeight.Bold, color = labelColor)
                    }
                }
            }
        }
    }
}

// --- TAB 4: NOTES, REMINDERS & PREFERENCES SETTINGS ---
@Composable
fun NotesAndSettingsTab(vm: AttendanceViewModel, initialSubTab: Int = 0) {
    val notesList by vm.filteredNotes.collectAsState()
    val remindersList by vm.reminders.collectAsState()
    val searchQuery by vm.notesSearchQuery.collectAsState()
    val prefs by vm.userPreferences.collectAsState(initial = UserPreference())
    val subjects by vm.subjects.collectAsState(initial = emptyList())

    var activeSubTab by remember { mutableStateOf(if (initialSubTab == 2) 0 else initialSubTab) }
    var showSettingsScreen by remember { mutableStateOf(initialSubTab == 2) }
    
    LaunchedEffect(initialSubTab) {
        if (initialSubTab == 2) {
            showSettingsScreen = true
        } else {
            activeSubTab = initialSubTab
            showSettingsScreen = false
        }
    }

    if (showSettingsScreen) {
        // --- DEDICATED PREMIUM MINIMAL SETTINGS REDESIGN ---
        SettingsSubTab(vm, prefs, onBackClick = { showSettingsScreen = false })
    } else {
        // --- PERSONAL HUB (MAIN VIEW) ---
        var checklistSearchQuery by remember { mutableStateOf("") }
        
        val filteredReminders = remember(remindersList, checklistSearchQuery) {
            val baseList = if (checklistSearchQuery.isEmpty()) {
                remindersList
            } else {
                remindersList.filter {
                    it.title.contains(checklistSearchQuery, ignoreCase = true) ||
                    it.description.contains(checklistSearchQuery, ignoreCase = true)
                }
            }
            // Sort checklist items by dateStr ASC, timeStr ASC (nearest deadline first)
            baseList.sortedWith(compareBy<ReminderItem> { it.dateStr }.thenBy { it.timeStr })
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HEADER - Large title, subtitle, settings container, subtle gradient glow
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 8.dp, end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Personal Hub",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Organize your notes, checklists & study materials",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            
                            // Settings icon in a rounded container
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { showSettingsScreen = true }
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Segmentation Control (Notes / Checklist)
            item {
                PremiumSegmentedControl(
                    selectedTab = activeSubTab,
                    onTabSelected = { activeSubTab = it }
                )
            }

            when (activeSubTab) {
                0 -> {
                    item {
                        NotesSubTab(
                            notes = notesList,
                            subjects = subjects,
                            query = searchQuery,
                            onSearch = { vm.updateNotesSearch(it) },
                            onAddNote = { title, content, subId, priority, audioStr, imgStr ->
                                vm.addNote(title, content, subId, priority, audioStr, imgStr)
                            },
                            onUpdateNote = { vm.updateNote(it) },
                            onDeleteNote = { vm.deleteNote(it) }
                        )
                    }
                }
                1 -> {
                    item {
                        RemindersSubTab(
                            reminders = filteredReminders,
                            originalCount = remindersList.size,
                            searchQuery = checklistSearchQuery,
                            onSearchQueryChange = { checklistSearchQuery = it },
                            onAddReminder = { title, desc, date, time ->
                                vm.addReminder(title, desc, date, time)
                            },
                            onUpdateReminder = { vm.updateReminderDetails(it) },
                            onToggle = { vm.toggleReminderStatus(it) },
                            onDelete = { vm.deleteReminder(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotesEmptyState(onCreateNoteClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("📝", fontSize = 42.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Notes Yet",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create notes, attach PDFs, images and study materials.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onCreateNoteClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Create First Note", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun ChecklistEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "No Tasks",
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                modifier = Modifier.size(50.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "✅ No Tasks Yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Create a checklist to stay organized.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// NOTES SUB TAB IMPL
// EDIT NOTE DIALOG COMPONENT
@Composable
fun EditNoteDialog(
    note: NoteItem,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (NoteItem) -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var selectedSubjectId by remember { mutableStateOf(note.subjectId) }
    var priority by remember { mutableStateOf(note.priority) }
    var showSubjectDropdown by remember { mutableStateOf(false) }

    // Multimedia States
    val context = androidx.compose.ui.platform.LocalContext.current
    var audioPath by remember { mutableStateOf(note.audioPath) }
    var imagePath by remember { mutableStateOf(note.imagePath) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val editRecordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Voice Recording permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes.", Toast.LENGTH_SHORT).show()
        }
    }

    val editMultimediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedFile = copyUriToInternalStorage(context, uri)
            if (copiedFile != null) {
                imagePath = copiedFile.absolutePath
                Toast.makeText(context, "File attached successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to attach file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit NoteDetails", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                // Priority Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select Priority", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("LOW" to "🟢 Low", "MEDIUM" to "⚡ Medium", "HIGH" to "🔥 High").forEach { (level, label) ->
                            val isSel = priority.uppercase() == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSel) {
                                            when (level) {
                                                "HIGH" -> Color(0xFFEF5350).copy(alpha = 0.2f)
                                                "MEDIUM" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                                                else -> Color(0xFF81C784).copy(alpha = 0.2f)
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isSel) {
                                            when (level) {
                                                "HIGH" -> Color(0xFFEF5350)
                                                "MEDIUM" -> Color(0xFFFFB74D)
                                                else -> Color(0xFF81C784)
                                            }
                                        } else {
                                            Color.Transparent
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { priority = level }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                // Subject Association Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Subject Link", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentSubjectName = if (selectedSubjectId == null) "Others (No Subject)" else {
                            subjects.find { it.id == selectedSubjectId }?.name ?: "Others (No Subject)"
                        }
                        OutlinedTextField(
                            value = currentSubjectName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showSubjectDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, "Select Subject")
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showSubjectDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showSubjectDropdown,
                            onDismissRequest = { showSubjectDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.75f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Others (No Subject)") },
                                onClick = {
                                    selectedSubjectId = null
                                    showSubjectDropdown = false
                                }
                            )
                            subjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text(subject.name) },
                                    onClick = {
                                        selectedSubjectId = subject.id
                                        showSubjectDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Multimedia Attachments Edit Block
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("Multimedia Attachments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        // Record Audio Button
                        Button(
                            onClick = {
                                val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    editRecordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (isRecordingAudio) {
                                        try {
                                            mediaRecorder?.stop()
                                            mediaRecorder?.release()
                                            mediaRecorder = null
                                            isRecordingAudio = false
                                            Toast.makeText(context, "Voice note recorded and attached!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            isRecordingAudio = false
                                        }
                                    } else {
                                        try {
                                            val fileName = "voice_note_${System.currentTimeMillis()}.3gp"
                                            val file = java.io.File(context.cacheDir, fileName)
                                            val path = file.absolutePath
                                            
                                            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                android.media.MediaRecorder(context)
                                            } else {
                                                android.media.MediaRecorder()
                                            }
                                            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                                            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                                            recorder.setOutputFile(path)
                                            recorder.prepare()
                                            recorder.start()
                                            
                                            mediaRecorder = recorder
                                            audioPath = path
                                            isRecordingAudio = true
                                            Toast.makeText(context, "Recording voice note...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = if (isRecordingAudio) Color.White else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.MicOff else Icons.Default.Mic, 
                                contentDescription = "Edit Voice Note",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRecordingAudio) "Stop (Rec)" else "Voicemail", style = MaterialTheme.typography.labelSmall)
                        }

                        // Attach Document/Image Button
                        Button(
                            onClick = {
                                editMultimediaPickerLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Files", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Attach File", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Display attached files
                    if (audioPath != null || imagePath != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            if (audioPath != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Audio note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Voice Note ✔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { audioPath = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete Audio", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            if (imagePath != null) {
                                val isPdf = imagePath!!.endsWith(".pdf", ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                        contentDescription = "File note",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val fName = java.io.File(imagePath!!).name
                                    Text(
                                        text = fName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { imagePath = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete File", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        onSave(note.copy(
                            title = title,
                            content = content,
                            subjectId = selectedSubjectId,
                            priority = priority,
                            audioPath = audioPath,
                            imagePath = imagePath
                        ))
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NoteActionSheet(
    note: NoteItem,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Long?) -> Unit
) {
    var showMoveOptions by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() }
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {}) // prevent dismiss clicks inside
                    .animateContentSize(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Pull handles
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showMoveOptions) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Action rows
                        val actions = listOf(
                            Triple(Icons.Default.Edit, "Edit", onEdit),
                            Triple(Icons.Default.Share, "Share", {
                                try {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, note.title)
                                        putExtra(android.content.Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share note via"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }),
                            Triple(Icons.Default.DriveFileMove, "Move to Subject", { showMoveOptions = true }),
                            Triple(Icons.Default.ContentCopy, "Duplicate", {
                                onDuplicate()
                                onDismiss()
                            }),
                            Triple(Icons.Default.Delete, "Delete", {
                                onDelete()
                                onDismiss()
                            })
                        )

                        actions.forEach { (icon, label, callback) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { callback() }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (label == "Delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (label == "Delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        // Title for move menu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showMoveOptions = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Text(
                                text = "Move notes to...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Render each subject option
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onMove(null)
                                            onDismiss()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📁", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Others (No Subject)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            items(subjects.size) { idx ->
                                val sub = subjects[idx]
                                val subColor = try {
                                    Color(android.graphics.Color.parseColor(sub.colorHex))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onMove(sub.id)
                                            onDismiss()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = subColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = sub.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun NotesSubTab(
    notes: List<NoteItem>,
    subjects: List<Subject>,
    query: String,
    onSearch: (String) -> Unit,
    onAddNote: (String, String, Long?, String, String?, String?) -> Unit,
    onUpdateNote: (NoteItem) -> Unit,
    onDeleteNote: (NoteItem) -> Unit
) {
    var showCreator by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var selectedSubjectIdForNewNote by remember { mutableStateOf<Long?>(null) }
    var selectedPriorityForNewNote by remember { mutableStateOf("MEDIUM") }
    var showSubjectDropdownForNewNote by remember { mutableStateOf(false) }

    // Multimedia States
    val context = androidx.compose.ui.platform.LocalContext.current
    var audioPathForNewNote by remember { mutableStateOf<String?>(null) }
    var imagePathForNewNote by remember { mutableStateOf<String?>(null) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Voice Recording permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes.", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedFile = copyUriToInternalStorage(context, uri)
            if (copiedFile != null) {
                imagePathForNewNote = copiedFile.absolutePath
                Toast.makeText(context, "Attachment attached successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to attach file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var selectedFilterSubjectId by remember { mutableStateOf<Long?>(null) } // null = All, -1 = Others, otherwise subject id
    var sortByPriority by remember { mutableStateOf(false) } // false = Date, true = Priority

    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var activeMenuNote by remember { mutableStateOf<NoteItem?>(null) }

    // Undoable deletion states
    var pendingDeleteNotes by remember { mutableStateOf<Map<Long, kotlinx.coroutines.Job>>(emptyMap()) }
    var noteToConfirmDelete by remember { mutableStateOf<NoteItem?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            pendingDeleteNotes.forEach { (noteId, job) ->
                job.cancel()
                val noteToDelete = notes.find { it.id == noteId }
                if (noteToDelete != null) {
                    onDeleteNote(noteToDelete)
                }
            }
        }
    }

    // Searchable bottom sheet states
    var showSubjectFilterSheet by remember { mutableStateOf(false) }
    var subjectSearchQuery by remember { mutableStateOf("") }

    val priorityWeight = mapOf("HIGH" to 3, "MEDIUM" to 2, "LOW" to 1)

    // Filtered & Sorted Notes list
    val displayedNotes = remember(notes, selectedFilterSubjectId, query, sortByPriority, pendingDeleteNotes) {
        var temp = notes.filter { note ->
            note.id !in pendingDeleteNotes.keys && (
                query.isEmpty() || 
                note.title.contains(query, ignoreCase = true) || 
                note.content.contains(query, ignoreCase = true)
            )
        }.filter { note ->
            val matchesSubject = when (selectedFilterSubjectId) {
                null -> true
                -1L -> note.subjectId == null
                else -> note.subjectId == selectedFilterSubjectId
            }
            matchesSubject
        }
        
        temp = if (sortByPriority) {
            temp.sortedWith(
                compareByDescending<NoteItem> { priorityWeight[it.priority.uppercase()] ?: 2 }
                    .thenByDescending { it.updatedAt }
            )
        } else {
            temp.sortedByDescending { it.updatedAt }
        }
        temp
    }

    if (editingNote != null) {
        EditNoteDialog(
            note = editingNote!!,
            subjects = subjects,
            onDismiss = { editingNote = null },
            onSave = {
                onUpdateNote(it)
                editingNote = null
            }
        )
    }

    if (activeMenuNote != null) {
        NoteActionSheet(
            note = activeMenuNote!!,
            subjects = subjects,
            onDismiss = { activeMenuNote = null },
            onEdit = {
                editingNote = activeMenuNote
            },
            onDelete = {
                noteToConfirmDelete = activeMenuNote
            },
            onDuplicate = {
                val copyNote = activeMenuNote!!
                onAddNote(
                    copyNote.title,
                    copyNote.content,
                    copyNote.subjectId,
                    copyNote.priority,
                    copyNote.audioPath,
                    copyNote.imagePath
                )
                Toast.makeText(context, "Note duplicated successfully!", Toast.LENGTH_SHORT).show()
            },
            onMove = { newSubId ->
                val moveNote = activeMenuNote!!
                onUpdateNote(moveNote.copy(subjectId = newSubId, updatedAt = System.currentTimeMillis()))
                Toast.makeText(context, "Note reassigned successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Searchable bottom sheet Dialog
    if (showSubjectFilterSheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSubjectFilterSheet = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showSubjectFilterSheet = false }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .clickable(enabled = false, onClick = {}) // absorb clicks
                        .testTag("notes_subject_filter_sheet"),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top Drag Handle (notch)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Title and Close Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Subject",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { showSubjectFilterSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Search Input
                        OutlinedTextField(
                            value = subjectSearchQuery,
                            onValueChange = { subjectSearchQuery = it },
                            placeholder = { Text("Search Subject") },
                            leadingIcon = { Icon(Icons.Default.Search, "Search") },
                            modifier = Modifier.fillMaxWidth().testTag("subject_sheet_search_input"),
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Scrollable List
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // All Subjects option
                            if ("all subjects".contains(subjectSearchQuery.lowercase())) {
                                item {
                                    val isSelected = selectedFilterSubjectId == null
                                    val itemBg = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    val itemTextColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBg)
                                            .clickable {
                                                selectedFilterSubjectId = null
                                                showSubjectFilterSheet = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🌐", fontSize = 18.sp)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "All Subjects",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = itemTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Others Option
                            if ("others".contains(subjectSearchQuery.lowercase()) || "no subject".contains(subjectSearchQuery.lowercase())) {
                                item {
                                    val isSelected = selectedFilterSubjectId == -1L
                                    val itemBg = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    val itemTextColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBg)
                                            .clickable {
                                                selectedFilterSubjectId = -1L
                                                showSubjectFilterSheet = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("📁", fontSize = 18.sp)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Others (No Subject)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = itemTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Registered Subjects
                            val filteredSubjectsList = subjects.filter {
                                it.name.contains(subjectSearchQuery, ignoreCase = true)
                            }
                            
                            items(filteredSubjectsList.size) { index ->
                                val subject = filteredSubjectsList[index]
                                val isSelected = selectedFilterSubjectId == subject.id
                                val itemBg = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                                val itemTextColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                val subColor = try {
                                    Color(android.graphics.Color.parseColor(subject.colorHex))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemBg)
                                        .clickable {
                                            selectedFilterSubjectId = subject.id
                                            showSubjectFilterSheet = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = subColor.copy(alpha = if (isSelected) 0.3f else 0.12f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Book,
                                            contentDescription = subject.name,
                                            tint = subColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = subject.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = itemTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Premium Float Search input with Mic Trailing Icon
            OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            placeholder = { 
                Text(
                    text = "Search notes, PDFs, images and topics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ) 
            },
            leadingIcon = { 
                Icon(
                    imageVector = Icons.Default.Search, 
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            trailingIcon = {
                IconButton(onClick = {
                    Toast.makeText(context, "Voice Search triggered! Start speaking.", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Mic, 
                        contentDescription = "Voice Search",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, shape = RoundedCornerShape(16.dp))
                .testTag("notes_search_input"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
            ),
            singleLine = true
        )

        // Redesigned Horizontal Compact Filter Row (with subject selector, sort, rounded pill design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentFilterSubjectName = when (selectedFilterSubjectId) {
                null -> "All Subjects"
                -1L -> "Others"
                else -> subjects.find { it.id == selectedFilterSubjectId }?.name ?: "All Subjects"
            }
            
            // Subject Filter Pill (Capsule shape)
            Surface(
                onClick = {
                    subjectSearchQuery = ""
                    showSubjectFilterSheet = true
                },
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .height(38.dp)
                    .weight(1.3f)
                    .testTag("subject_filter_dropdown")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = when (selectedFilterSubjectId) {
                                null -> "🌐"
                                -1L -> "📁"
                                else -> "📚"
                            },
                            fontSize = 12.sp
                        )
                        Text(
                            text = currentFilterSubjectName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Subject",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Sort Selector Pill
            Surface(
                onClick = { sortByPriority = !sortByPriority },
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .height(38.dp)
                    .weight(1f)
                    .testTag("notes_sort_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Sort: ${if (sortByPriority) "Priority" else "Recent"}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        val premiumGradient = Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )

        // Gradient Adaptive Create Note Button
        Button(
            onClick = { showCreator = !showCreator },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                .testTag("create_note_toggle_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(premiumGradient),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (showCreator) Icons.Default.Close else Icons.Default.Add, 
                        contentDescription = "Create Note", 
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showCreator) "Close Panel" else "+ New Note", 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        if (showCreator) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Note Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth().testTag("add_note_title_input")
                    )

                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )

                    // Priority Selector row on creation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Select Priority", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("LOW" to "🟢 Low", "MEDIUM" to "⚡ Medium", "HIGH" to "🔥 High").forEach { (level, label) ->
                                val isSel = selectedPriorityForNewNote == level
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSel) {
                                                when(level) {
                                                    "HIGH" -> Color(0xFFEF5350).copy(alpha = 0.15f)
                                                    "MEDIUM" -> Color(0xFFFFB74D).copy(alpha = 0.15f)
                                                    else -> Color(0xFF81C784).copy(alpha = 0.15f)
                                                }
                                            } else {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSel) {
                                                when(level) {
                                                    "HIGH" -> Color(0xFFEF5350)
                                                    "MEDIUM" -> Color(0xFFFFB74D)
                                                    else -> Color(0xFF81C784)
                                                }
                                            } else {
                                                Color.Transparent
                                            },
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedPriorityForNewNote = level }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // Subject dropdown on creation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Link with Subject", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val currentSubjectName = if (selectedSubjectIdForNewNote == null) "Others (No Subject)" else {
                                subjects.find { it.id == selectedSubjectIdForNewNote }?.name ?: "Others (No Subject)"
                            }
                            OutlinedTextField(
                                value = currentSubjectName,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { showSubjectDropdownForNewNote = true }) {
                                        Icon(Icons.Default.ArrowDropDown, "Select Subject")
                                    }
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showSubjectDropdownForNewNote = true }
                            )
                            DropdownMenu(
                                expanded = showSubjectDropdownForNewNote,
                                onDismissRequest = { showSubjectDropdownForNewNote = false },
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Others (No Subject)") },
                                    onClick = {
                                        selectedSubjectIdForNewNote = null
                                        showSubjectDropdownForNewNote = false
                                    }
                                )
                                subjects.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.name) },
                                        onClick = {
                                            selectedSubjectIdForNewNote = s.id
                                            showSubjectDropdownForNewNote = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Multimedia attachment controls
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("Add Multimedia", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            // Record audio button
                            Button(
                                onClick = {
                                    val hasRecordAudioPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (!hasRecordAudioPermission) {
                                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        if (isRecordingAudio) {
                                            // Stop recording
                                            try {
                                                mediaRecorder?.stop()
                                                mediaRecorder?.release()
                                                mediaRecorder = null
                                                isRecordingAudio = false
                                                Toast.makeText(context, "Voice Note recorded!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                isRecordingAudio = false
                                            }
                                        } else {
                                            // Start recording
                                            try {
                                                val fileName = "voice_note_${System.currentTimeMillis()}.3gp"
                                                val file = java.io.File(context.cacheDir, fileName)
                                                val path = file.absolutePath
                                                
                                                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    android.media.MediaRecorder(context)
                                                } else {
                                                    android.media.MediaRecorder()
                                                }
                                                
                                                recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                                recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                                                recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                                                recorder.setOutputFile(path)
                                                recorder.prepare()
                                                recorder.start()
                                                
                                                mediaRecorder = recorder
                                                audioPathForNewNote = path
                                                isRecordingAudio = true
                                                Toast.makeText(context, "Recording voice note...", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Failed to start recorder: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = if (isRecordingAudio) Color.White else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRecordingAudio) Icons.Default.MicOff else Icons.Default.Mic, 
                                    contentDescription = "Voice Note",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRecordingAudio) "Stop (Rec...)" else "Voice Note", style = MaterialTheme.typography.labelSmall)
                            }

                            // Attach File button (multimedia: pdf, image etc)
                            Button(
                                onClick = {
                                    imagePickerLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach File", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Attach File", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Preview attached media inside the editor
                        if (audioPathForNewNote != null || imagePathForNewNote != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (audioPathForNewNote != null) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "Voice Note Attached", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Voice Rec ✔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { audioPathForNewNote = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Audio", modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                
                                if (imagePathForNewNote != null) {
                                    val isPdf = imagePathForNewNote!!.endsWith(".pdf", ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                            contentDescription = "File Attached",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val fName = java.io.File(imagePathForNewNote!!).name
                                        Text(
                                            text = fName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { imagePathForNewNote = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove File", modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { 
                            showCreator = false
                            audioPathForNewNote = null
                            imagePathForNewNote = null
                        }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (noteTitle.isNotEmpty()) {
                                onAddNote(noteTitle, noteContent, selectedSubjectIdForNewNote, selectedPriorityForNewNote, audioPathForNewNote, imagePathForNewNote)
                                noteTitle = ""
                                noteContent = ""
                                selectedSubjectIdForNewNote = null
                                selectedPriorityForNewNote = "MEDIUM"
                                audioPathForNewNote = null
                                imagePathForNewNote = null
                                showCreator = false
                            }
                        }, modifier = Modifier.testTag("save_note_button")) {
                            Text("Create")
                        }
                    }
                }
            }
        }

        if (displayedNotes.isEmpty()) {
            NotesEmptyState(onCreateNoteClick = { showCreator = true })
        } else {
            Text(
                text = "Recent Notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            displayedNotes.forEach { note ->
                val cardColor = MaterialTheme.colorScheme.surface
                val cardShape = RoundedCornerShape(24.dp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_item_card_${note.id}"),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = cardShape
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Title and Three-Dot Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    IconButton(
                                        onClick = { activeMenuNote = note },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("note_menu_button_${note.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Note Actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            // If it has an image (and is not a PDF), show a small 72x72 thumbnail on the right!
                            if (note.imagePath != null && note.imagePath.isNotEmpty() && !note.imagePath.endsWith(".pdf", ignoreCase = true)) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(model = java.io.File(note.imagePath)),
                                    contentDescription = "Note Thumbnail",
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openAttachmentFile(context, note.imagePath) },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }

                        // Compact PDF Row rendering (if file is PDF)
                        if (note.imagePath != null && note.imagePath.isNotEmpty() && note.imagePath.endsWith(".pdf", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openAttachmentFile(context, note.imagePath) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF File",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayFileName = java.io.File(note.imagePath).name
                                        Text(
                                            text = displayFileName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "PDF • 2.4 MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // Player row for Voice Notes
                        if (note.audioPath != null && note.audioPath.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            VoiceNotePlayer(note.audioPath)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Subject Name Badge
                                val linkedSub = subjects.find { it.id == note.subjectId }
                                val subjectText = if (linkedSub != null) "📚 ${linkedSub.name}" else "📁 Others"
                                Text(
                                    text = subjectText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Priority badge (HIGH & MEDIUM only, hide LOW)
                                if (note.priority.uppercase() == "HIGH" || note.priority.uppercase() == "MEDIUM") {
                                    val priorityEmoji = if (note.priority.uppercase() == "HIGH") "🔴" else "🟡"
                                    val priorityLabel = if (note.priority.uppercase() == "HIGH") "High Priority" else "Medium Priority"
                                    val priorityColor = if (note.priority.uppercase() == "HIGH") Color(0xFFEF5350) else Color(0xFFFFB74D)
                                    Text(
                                        text = "$priorityEmoji $priorityLabel",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = priorityColor
                                    )
                                }
                            }

                            // Dynamic Date format matches 🕒 Jun 22 • 8:09 AM
                            val formattedTime = remember(note.updatedAt) {
                                try {
                                    SimpleDateFormat("MMM dd • h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                            Text(
                                text = "🕒 $formattedTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

        // Render Note Deletion Confirmation Dialog
        if (noteToConfirmDelete != null) {
            AlertDialog(
                onDismissRequest = { noteToConfirmDelete = null },
                title = { Text("Delete Note?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete \"${noteToConfirmDelete!!.title}\"? This action can be undone for 5 seconds.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val targetNote = noteToConfirmDelete!!
                            noteToConfirmDelete = null
                            
                            // Start the 5 seconds grace period before real deletion
                            val deleteJob = scope.launch {
                                kotlinx.coroutines.delay(5000)
                                onDeleteNote(targetNote)
                                pendingDeleteNotes = pendingDeleteNotes - targetNote.id
                            }
                            pendingDeleteNotes = pendingDeleteNotes + (targetNote.id to deleteJob)
                        }
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToConfirmDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Floating Bottom Undo Card
        if (pendingDeleteNotes.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Deleted",
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Note deleted (${pendingDeleteNotes.size})",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        onClick = {
                            // Cancel all pending deletions to restore notes
                            pendingDeleteNotes.forEach { (_, job) -> job.cancel() }
                            pendingDeleteNotes = emptyMap()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Undo", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// CHECKLIST SUB TAB IMPL
// EDIT CHECKLIST DIALOG COMPONENT
@Composable
fun EditChecklistItemDialog(
    item: ReminderItem,
    onDismiss: () -> Unit,
    onSave: (ReminderItem) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var desc by remember { mutableStateOf(item.description) }
    var date by remember { mutableStateOf(item.dateStr) }
    var time by remember { mutableStateOf(item.timeStr) }

    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance()
    
    // Parse existing date to initialize DatePickerDialog
    val parts = date.split("-")
    val initYear = if (parts.size == 3) parts[0].toIntOrNull() ?: calendar.get(java.util.Calendar.YEAR) else calendar.get(java.util.Calendar.YEAR)
    val initMonth = if (parts.size == 3) (parts[1].toIntOrNull() ?: 1) - 1 else calendar.get(java.util.Calendar.MONTH)
    val initDay = if (parts.size == 3) parts[2].toIntOrNull() ?: calendar.get(java.util.Calendar.DAY_OF_MONTH) else calendar.get(java.util.Calendar.DAY_OF_MONTH)

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            date = String.format(java.util.Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        initYear,
        initMonth,
        initDay
    )

    // Parse existing time to initialize TimePickerDialog
    val tParts = time.split(":")
    val initHour = if (tParts.size == 2) tParts[0].toIntOrNull() ?: calendar.get(java.util.Calendar.HOUR_OF_DAY) else calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val initMinute = if (tParts.size == 2) tParts[1].toIntOrNull() ?: calendar.get(java.util.Calendar.MINUTE) else calendar.get(java.util.Calendar.MINUTE)

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            time = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
        },
        initHour,
        initMinute,
        false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Due Date") },
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Select Date")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { datePickerDialog.show() }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = formatTimeTo12HourIST(time),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Due Time") },
                        trailingIcon = {
                            IconButton(onClick = { timePickerDialog.show() }) {
                                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Select Time")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { timePickerDialog.show() }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        onSave(item.copy(title = title, description = desc, dateStr = date, timeStr = time))
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemindersSubTab(
    reminders: List<ReminderItem>,
    originalCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddReminder: (String, String, String, String) -> Unit,
    onUpdateReminder: (ReminderItem) -> Unit,
    onToggle: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit
) {
    var showCreator by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-06-16") }
    var time by remember { mutableStateOf("10:00") }

    var editingReminder by remember { mutableStateOf<ReminderItem?>(null) }

    if (editingReminder != null) {
        EditChecklistItemDialog(
            item = editingReminder!!,
            onDismiss = { editingReminder = null },
            onSave = {
                onUpdateReminder(it)
                editingReminder = null
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search Checklist") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            modifier = Modifier.fillMaxWidth().testTag("checklist_search_input"),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            singleLine = true
        )

        Button(
            onClick = { showCreator = !showCreator },
            modifier = Modifier.fillMaxWidth().testTag("create_checklist_toggle_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (showCreator) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (showCreator) Icons.Default.Close else Icons.Default.Add, "Create Checklist Button")
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showCreator) "Close Form" else "+ Create Checklist", fontWeight = FontWeight.Bold)
        }

        if (showCreator) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("create_checklist_form_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Checklist Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth().testTag("create_checklist_title_input")
                    )

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val context = LocalContext.current
                    val calendar = java.util.Calendar.getInstance()
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            date = String.format(java.util.Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth)
                        },
                        calendar.get(java.util.Calendar.YEAR),
                        calendar.get(java.util.Calendar.MONTH),
                        calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    )

                    val timePickerDialog = android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            time = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
                        },
                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        calendar.get(java.util.Calendar.MINUTE),
                        false
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Due Date") },
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Select Date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { datePickerDialog.show() }
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = formatTimeTo12HourIST(time),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Due Time") },
                            trailingIcon = {
                                IconButton(onClick = { timePickerDialog.show() }) {
                                    Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Select Time")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { timePickerDialog.show() }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreator = false }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (title.isNotEmpty()) {
                                onAddReminder(title, desc, date, time)
                                title = ""
                                desc = ""
                                showCreator = false
                            }
                        }, modifier = Modifier.testTag("save_checklist_button")) {
                            Text("Save Task")
                        }
                    }
                }
            }
        }

        if (originalCount == 0) {
            ChecklistEmptyState()
        } else {
            val pendingTasks = reminders.filter { !it.isDone }
            val completedTasks = reminders.filter { it.isDone }

            if (pendingTasks.isEmpty() && completedTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No checklist tasks match your search.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                if (pendingTasks.isNotEmpty()) {
                    Text(
                        "Pending Tasks (${pendingTasks.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    pendingTasks.forEach { item ->
                        TaskListItem(item, onToggle, onDelete, onEdit = { editingReminder = it })
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    Text(
                        "Completed Tasks (${completedTasks.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    completedTasks.forEach { item ->
                        TaskListItem(item, onToggle, onDelete, onEdit = { editingReminder = it })
                    }
                }
            }
        }
    }
}

@Composable
fun TaskListItem(
    item: ReminderItem,
    onToggle: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit,
    onEdit: (ReminderItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("todo_task_card_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle(item) },
                modifier = Modifier.testTag("todo_task_checkbox_${item.id}")
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    style = if (item.isDone) androidx.compose.ui.text.TextStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    ) else MaterialTheme.typography.bodyMedium,
                    color = if (item.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "Due: ${item.dateStr} at ${formatTimeTo12HourIST(item.timeStr)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onEdit(item) },
                    modifier = Modifier.size(32.dp).testTag("todo_task_edit_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier.size(32.dp).testTag("todo_task_delete_${item.id}")
                ) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// SETTINGS SUB TAB IMPL
@Composable
fun SettingsSubTab(vm: AttendanceViewModel, prefs: UserPreference?, onBackClick: () -> Unit) {
    val goal = prefs?.attendanceGoal ?: 75
    val theme = prefs?.themeMode ?: "SYSTEM"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Backup state
    var lastBackupText by remember { mutableStateOf("Today, 10:05 AM") }

    // CreateDocument launcher for Export (Json Backup)
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = vm.backupAllData()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                    val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    lastBackupText = "Today, $timeNow"
                    Toast.makeText(context, "📂 Backup exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to export backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // OpenDocument launcher for Import (Json Restore)
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val jsonString = String(bytes)
                        val success = vm.restoreAllData(jsonString)
                        if (success) {
                            Toast.makeText(context, "🔄 Backup restored successfully! Refreshing database...", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "❌ Failed to parse backup file structure.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Navigation and sub-screen routing state
    var currentSubPage by remember { mutableStateOf("MAIN") } // MAIN, PREFERENCES, DATA_BACKUP, ABOUT, PREMIUM, PRIVACY, THEME_SELECT

    // Dialog triggering states
    var showResetDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }

    // Slider responsive state while dragging
    var sliderGoalValue by remember(goal) { mutableStateOf(goal.toFloat()) }

    // Intercept hardware/system back clicks
    androidx.activity.compose.BackHandler(enabled = true) {
        when (currentSubPage) {
            "PREMIUM", "PRIVACY" -> currentSubPage = "ABOUT"
            "THEME_SELECT" -> currentSubPage = "PREFERENCES"
            "MAIN" -> onBackClick()
            else -> currentSubPage = "MAIN"
        }
    }

    // Dynamic Color definitions matching custom Design System specs
    val appDanger = Color(0xFFDC2626)
    val isDark = theme == "MIDNIGHT_FOCUS" || theme == "DARK" || (theme == "SYSTEM" && androidx.compose.foundation.isSystemInDarkTheme())
    
    // Smooth dynamic background brush to avoid flat solid white theme "dull vibes"
    val settingsBackgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.background
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                MaterialTheme.colorScheme.background
            )
        )
    }

    // Inner scrollable card block
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(settingsBackgroundBrush)
    ) {
        when (currentSubPage) {
            "MAIN" -> {
                // Main Settings Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Custom Theme-Aware Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .testTag("back_from_settings_button")
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack, 
                                contentDescription = "Back", 
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 28.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Track Easily. Attend Smartly.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Structured Section Headers & Cards
                    Text(
                        text = "CORE CONFIGURATION",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    SettingsRowItem(
                        title = "Preferences",
                        subtitle = "Customize attendance goals, reminders, and themes",
                        icon = Icons.Default.Tune,
                        onClick = { currentSubPage = "PREFERENCES" },
                        testTag = "settings_row_preferences"
                    )

                    SettingsRowItem(
                        title = "Data & Backup",
                        subtitle = "Manage backup and restore options",
                        icon = Icons.Default.Cloud,
                        onClick = { currentSubPage = "DATA_BACKUP" },
                        testTag = "settings_row_data_backups"
                    )

                    Text(
                        text = "SUPPORT & SAFETY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    SettingsRowItem(
                        title = "About AttendEz",
                        subtitle = "Version, credits and app information",
                        icon = Icons.Default.Info,
                        onClick = { currentSubPage = "ABOUT" },
                        testTag = "settings_row_about_info"
                    )

                    SettingsRowItem(
                        title = "Reset Timetable",
                        subtitle = "Clear timetable and start fresh",
                        icon = Icons.Default.Warning,
                        isDanger = true,
                        onClick = { showResetDialog = true },
                        testTag = "settings_row_reset_timetable"
                    )
                }
            }

            "PREFERENCES" -> {
                // Preferences Subpage
                val reminderEnabled = prefs?.dailyReminderEnabled ?: false
                val reminderTime = prefs?.dailyReminderTime ?: "10:05"

                // Permission code
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(context, "Notification permission denied. Reminders might not show.", Toast.LENGTH_LONG).show()
                    }
                    vm.updateDailyReminder(true, reminderTime)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentSubPage = "MAIN" },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Preferences",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Customize your experience",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "GOAL & ALERTS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    // Row 1: Attendance Goal
                    SettingsRowItem(
                        title = "Attendance Goal",
                        subtitle = "Set your minimum attendance target",
                        icon = Icons.Default.Adjust,
                        trailingValue = "$goal%",
                        onClick = { 
                            sliderGoalValue = goal.toFloat()
                            showGoalSheet = true 
                        },
                        testTag = "preferences_row_goal"
                    )

                    // Row 2: Attendance Reminder
                    SettingsRowItem(
                        title = "Attendance Reminder",
                        subtitle = "Enable Daily Reminder",
                        icon = Icons.Default.Notifications,
                        hasSwitch = true,
                        switchChecked = reminderEnabled,
                        onSwitchChange = { checked ->
                            if (checked) {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.updateDailyReminder(true, reminderTime)
                                }
                            } else {
                                vm.updateDailyReminder(false, reminderTime)
                            }
                        },
                        onClick = {
                            val nextChecked = !reminderEnabled
                            if (nextChecked) {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.updateDailyReminder(true, reminderTime)
                                }
                            } else {
                                vm.updateDailyReminder(false, reminderTime)
                            }
                        },
                        testTag = "preferences_row_reminder"
                    )

                    // Row 3: Reminder Time
                    val currentHour = reminderTime.split(":").getOrNull(0)?.toIntOrNull() ?: 10
                    val currentMinute = reminderTime.split(":").getOrNull(1)?.toIntOrNull() ?: 5
                    SettingsRowItem(
                        title = "Reminder Time",
                        subtitle = "Choose when you want to be reminded",
                        icon = Icons.Default.Schedule,
                        trailingValue = formatTime24To12(reminderTime),
                        onClick = {
                            android.app.TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val formatted = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                                    vm.updateDailyReminder(reminderEnabled, formatted)
                                    Toast.makeText(context, "Reminder time updated!", Toast.LENGTH_SHORT).show()
                                },
                                currentHour,
                                currentMinute,
                                false
                            ).show()
                        },
                        testTag = "preferences_row_time"
                    )

                    Text(
                        text = "THEMES & APPEARANCE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    // Row 4: Theme
                    val currentThemeLabel = when (theme) {
                        "CAMPUS_BLUE", "LIGHT" -> "Light"
                        "MIDNIGHT_FOCUS", "DARK" -> "Midnight Focus"
                        "LAVENDER_CALM" -> "Lavender Calm"
                        else -> "Midnight Focus"
                    }
                    SettingsRowItem(
                        title = "Theme",
                        subtitle = "Choose your preferred app theme",
                        icon = Icons.Default.Palette,
                        trailingValue = currentThemeLabel,
                        onClick = { currentSubPage = "THEME_SELECT" },
                        testTag = "preferences_row_theme"
                    )
                }
            }

            "THEME_SELECT" -> {
                // Theme selection page with Radio Tile previews
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentSubPage = "PREFERENCES" },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Themes",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Choose your preferred app theme",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    themePreviewTile(
                        title = "Light ☀️",
                        description = "Campus Blue classic design",
                        isActive = (theme == "CAMPUS_BLUE" || theme == "LIGHT"),
                        primaryColor = Color(0xFF2563EB),
                        secondaryColor = Color(0xFFF8FAFC),
                        onClick = { vm.updateTheme("CAMPUS_BLUE") },
                        testTag = "theme_tile_light"
                    )

                    themePreviewTile(
                        title = "Midnight Focus 🌙",
                        description = "Elegant slate black for peak focus",
                        isActive = (theme == "MIDNIGHT_FOCUS" || theme == "DARK"),
                        primaryColor = Color(0xFF38BDF8),
                        secondaryColor = Color(0xFF020617),
                        onClick = { vm.updateTheme("MIDNIGHT_FOCUS") },
                        testTag = "theme_tile_midnight"
                    )

                    themePreviewTile(
                        title = "Lavender Calm 💜",
                        description = "Peaceful purple pastel palettes",
                        isActive = (theme == "LAVENDER_CALM"),
                        primaryColor = Color(0xFF8B5CF6),
                        secondaryColor = Color(0xFFFAF9FF),
                        onClick = { vm.updateTheme("LAVENDER_CALM") },
                        testTag = "theme_tile_lavender"
                    )
                }
            }

            "DATA_BACKUP" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentSubPage = "MAIN" },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Data & Backup",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Manage your data safely",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "CLOUD SYNC OPERATIONS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    // Backup and Restore are Premium features, hence strictly locked
                    SettingsRowItem(
                        title = "Backup Data",
                        subtitle = "Backup attendance, notes, and checklists",
                        icon = Icons.Default.CloudUpload,
                        isLocked = false,
                        onClick = { 
                            exportDocumentLauncher.launch("attendez_backup_${System.currentTimeMillis()}.json")
                        },
                        testTag = "data_row_backup"
                    )

                    SettingsRowItem(
                        title = "Restore Data",
                        subtitle = "Restore your previous AttendEz snapshots",
                        icon = Icons.Default.CloudDownload,
                        isLocked = false,
                        onClick = { 
                            importDocumentLauncher.launch(arrayOf("application/json"))
                        },
                        testTag = "data_row_restore"
                    )

                    Text(
                        text = "LAST SYNC STATUS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    // Last Backup Row Custom Layout configured with dynamic theme scheme
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Schedule, "Time", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Last Backup Snapshot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(lastBackupText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF16A34A))
                            }
                        }
                    }







                        

                        
                        // Card 3: Free Webhook Tutorial Link
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "💡 Setup a Live Dashboard (Steps)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "How to gather thousands of reviews in Google Sheets for FREE:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "1. Create a free **Google Form** with four text questions: Rating, Category, Comments, and Email.\n" +
                                    "2. In Google Forms options, link it to generate a **Google Sheet** inside Google Drive.\n" +
                                    "3. Get the prefilled Google Form URL via options -> 'Get pre-filled link'.\n" +
                                    "4. Note down the 'entry.xxxx' name tags from the URL parameters and paste them in the settings page.\n" +
                                    "5. Save the 'formResponse' URL! Now, when a user submits a review here, it will automatically register in your Google Sheets dashboard in the background!",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

            "ABOUT" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentSubPage = "MAIN" },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "About AttendEz",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black, 
                                fontSize = 24.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real App Icon adaptive layout
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                        )
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "AttendEz App Icon",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "AttendEz",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 28.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        "Track Easily. Attend Smartly.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Text(
                        "Version 2.4.0 (Stable)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Configured Rows - keeping developer credits on the about page
                    SettingsRowItem(
                        title = "Developed with ❤️ by Sameer Pandey",
                        subtitle = "Tap to view developer details 👨‍💻",
                        icon = Icons.Default.Person,
                        onClick = { showDeveloperDialog = true },
                        testTag = "about_row_developer"
                    )

                    SettingsRowItem(
                        title = "Premium Features",
                        subtitle = "Unlock advanced capabilities",
                        icon = Icons.Default.Star,
                        onClick = { currentSubPage = "PREMIUM" },
                        testTag = "about_row_premium"
                    )

                    SettingsRowItem(
                        title = "Privacy Policy",
                        subtitle = "Read our standard offline policy",
                        icon = Icons.Default.Lock,
                        onClick = { currentSubPage = "PRIVACY" },
                        testTag = "about_row_privacy"
                    )
                }
            }

            "PREMIUM" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentSubPage = "ABOUT" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Premium Features",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Centered Premium Showcase Header with dynamic theme colors
                    val premiumCardBackground = if (theme == "MIDNIGHT_FOCUS" || theme == "DARK") {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color(0xFFEDF2FF)
                    }
                    val premiumTextColor = if (theme == "MIDNIGHT_FOCUS" || theme == "DARK") {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        Color(0xFF1E3A8A)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = premiumCardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Unlock Premium",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                    color = premiumTextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Get access to powerful features and tools.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = premiumTextColor.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { 
                                        Toast.makeText(context, "🔒 Coming Soon! Premium payment gateways are currently arriving in the next release.", Toast.LENGTH_LONG).show() 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Text("Coming Soon", fontWeight = FontWeight.Bold)
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Premium Badge",
                                tint = Color(0xFFEAB308),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 features
                    PremiumFeatureCard(
                        title = "OCR Timetable Scanner",
                        description = "Scan and auto-create timetable instantly.",
                        icon = Icons.Default.CropFree
                    )

                    PremiumFeatureCard(
                        title = "Advanced Analytics",
                        description = "Detailed insights and attendance reports.",
                        icon = Icons.Default.BarChart
                    )

                    PremiumFeatureCard(
                        title = "Lab Attendance Tracking",
                        description = "Track lab and practical sessions easily.",
                        icon = Icons.Default.School
                    )

                    PremiumFeatureCard(
                        title = "Export & Share Reports",
                        description = "Export data and share reports quickly.",
                        icon = Icons.Default.Share
                    )
                }
            }

            "PRIVACY" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentSubPage = "ABOUT" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Privacy Policy",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "AttendEz Privacy Commitment",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        "Your privacy is our utmost priority. As a student-focused, offline-first application, AttendEz works completely in-memory and on your local device. We have no external telemetry, tracker APIs, or central servers storing your records.\n\n" +
                        "1. Local Storage Only: All timetables, checklist homework items, quick notes, and attendance histories are stored locally strictly within your private database.\n\n" +
                        "2. Zero Trackers: We gather no crashlytics, metrics, or student identity maps.\n\n" +
                        "3. Complete Autonomy: Backups created using the Data screen are fully offline files.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Goal Setter dialog/bottomsheet replica with dynamic colors
    if (showGoalSheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showGoalSheet = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Set Attendance Goal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Choose your minimum attendance target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Large goal display
                    Text(
                        "${sliderGoalValue.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 44.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = sliderGoalValue,
                        onValueChange = { sliderGoalValue = it },
                        valueRange = 50f..100f,
                        modifier = Modifier.fillMaxWidth().testTag("attendance_goal_slider_sheet")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("75%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val alertBoxBg = if (theme == "MIDNIGHT_FOCUS" || theme == "DARK") {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color(0xFFEFF6FF)
                    }
                    val alertBoxText = if (theme == "MIDNIGHT_FOCUS" || theme == "DARK") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color(0xFF1E40AF)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(alertBoxBg)
                            .padding(12.dp)
                    ) {
                        Text(
                            "We recommend keeping your goal between 75% – 100%",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = alertBoxText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            vm.updateGoal(sliderGoalValue.toInt())
                            showGoalSheet = false
                            Toast.makeText(context, "Attendance goal saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Goal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Interactive Developer bio Dialog (Sameer Pandey custom card profile response)
    if (showDeveloperDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDeveloperDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header / Avatar Block
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Sameer Pandey",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Lead Developer of AttendEz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )

                    Text(
                        "Sameer Pandey is a passionate developer dedicated to creating intuitive academic helpers like AttendEz. Crafting offline-first, performance-tuned, student-friendly Android apps with Jetpack Compose.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Responsive stats row inside dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("App Version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2.4.0 (Stable)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Privacy Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100% Offline", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { /* Disabled as requested */ },
                            enabled = false,
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub Disabled")
                        }

                        Button(
                            onClick = { showDeveloperDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = appDanger, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Timetable?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text(
                     "This will permanently remove:\n\n• Timetable\n• Attendance Records\n• Subject Data\n\nThis action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.resetPreferencesAndTimetable()
                        showResetDialog = false
                        Toast.makeText(context, "Timetable completely re-set!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appDanger)
                ) {
                    Text("Reset Timetable", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Cloud Backup", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Backup?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text("Backup attendance records, notes, and checklists to local snapshot.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupDialog = false
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        val now = sdf.format(java.util.Date())
                        lastBackupText = "Today, ${formatTime24To12(now.split(" ")[1])}"
                        Toast.makeText(context, "Local backup created successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Backup", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Cloud Restore", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Backup?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text("Select a backup file to restore. All active data will be overwritten with snapshot entries.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        Toast.makeText(context, "Local snapshot restored completely!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isDanger: Boolean = false,
    isLocked: Boolean = false,
    trailingValue: String? = null,
    hasSwitch: Boolean = false,
    switchChecked: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp, 
            if (isDanger) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (hasSwitch) {
                        onSwitchChange?.invoke(!switchChecked)
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular/Square icon block
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDanger) Color(0xFFFEE2E2)
                            else if (isLocked) Color(0xFFFEF3C7)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDanger) Color(0xFFDC2626) 
                               else if (isLocked) Color(0xFFD97706)
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isDanger) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSwitch) {
                    Switch(
                        checked = switchChecked,
                        onCheckedChange = onSwitchChange,
                        modifier = Modifier.testTag(testTag + "_switch")
                    )
                } else {
                    if (trailingValue != null) {
                        Text(
                            text = trailingValue,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.ChevronRight,
                        contentDescription = if (isLocked) "Locked" else "Go",
                        tint = if (isLocked) Color(0xFFD97706).copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(if (isLocked) 16.dp else 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun themePreviewTile(
    title: String,
    description: String,
    isActive: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Custom visually rich Color Dots Preview
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(secondaryColor)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            RadioButton(
                selected = isActive,
                onClick = onClick,
                modifier = Modifier.testTag(testTag + "_radio")
            )
        }
    }
}

@Composable
fun PremiumFeatureCard(
    title: String,
    description: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

fun formatTime24To12(time24: String): String {
    val parts = time24.split(":")
    if (parts.size != 2) return time24
    val h = parts[0].toIntOrNull() ?: return time24
    val m = parts[1].toIntOrNull() ?: return time24
    val isPm = h >= 12
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    val amPm = if (isPm) "PM" else "AM"
    return String.format(java.util.Locale.US, "%02d:%02d %s", h12, m, amPm)
}

// Sample mock extracted timetable backup
private fun getSampleExtractedTimetable(): List<ExtractedClass> {
    return listOf(
        ExtractedClass("Computer Networks", "09:00", "09:50", listOf(1, 3), "CSE Lab 3", "Dr. Alan Turing"),
        ExtractedClass("Software Engineering", "10:00", "10:50", listOf(1, 3, 5), "Room 203", "Prof. Grace Hopper"),
        ExtractedClass("Database Systems", "11:00", "11:50", listOf(2, 4), "Room 105", "Dr. Edgar Codd"),
        ExtractedClass("Artificial Intelligence", "14:00", "15:15", listOf(2, 4), "Auditorium A", "Prof. John McCarthy"),
        ExtractedClass("Compiler Design", "09:00", "10:30", listOf(5), "Room 301", "Dr. Alfred Aho"),

        // Group Labs on Monday (Day 1)
        ExtractedClass("Operating Systems Lab (G1)", "14:00", "16:00", listOf(1), "OS Lab 1", "Prof. Linus Torvalds"),
        ExtractedClass("Operating Systems Lab (G2)", "14:00", "16:00", listOf(1), "OS Lab 2", "Dr. Tanenbaum"),
        ExtractedClass("Operating Systems Lab (G3)", "14:00", "16:00", listOf(2), "OS Lab 1", "Prof. Linus Torvalds"),
        ExtractedClass("Operating Systems Lab (G4)", "14:00", "16:00", listOf(2), "OS Lab 2", "Dr. Tanenbaum"),

        // Group Labs on Wednesday (Day 3)
        ExtractedClass("Internet of Things Lab (G1)", "11:15", "13:15", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
        ExtractedClass("Internet of Things Lab (G2)", "11:15", "13:15", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
        ExtractedClass("Internet of Things Lab (G3)", "14:30", "16:30", listOf(3), "IoT Lab", "Dr. Vint Cerf"),
        ExtractedClass("Internet of Things Lab (G4)", "14:30", "16:30", listOf(3), "IoT Lab", "Dr. Vint Cerf")
    )
}

fun isLabSubjectName(name: String): Boolean {
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
           low.contains("g4") || 
           low.contains("practice")
}

@Composable
fun SameerPandeyFooter() {
    Spacer(modifier = Modifier.height(32.dp))
}

fun formatTimeTo12HourIST(time24: String): String {
    val parts = time24.split(":")
    if (parts.size != 2) return "$time24 IST"
    val hour = parts[0].toIntOrNull() ?: return "$time24 IST"
    val minute = parts[1].toIntOrNull() ?: return "$time24 IST"
    val isPm = hour >= 12
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (isPm) "PM" else "AM"
    return String.format(java.util.Locale.US, "%02d:%02d %s IST", hour12, minute, amPm)
}

fun isSunday(dateStr: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return false
        val cal = Calendar.getInstance().apply { time = date }
        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    } catch (e: java.lang.Exception) {
        false
    }
}

@Composable
fun VoiceNotePlayer(audioPath: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    
    DisposableEffect(audioPath) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    try {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        if (mediaPlayer == null) {
                            val mp = android.media.MediaPlayer()
                            mp.setDataSource(audioPath)
                            mp.prepare()
                            mp.setOnCompletionListener {
                                isPlaying = false
                            }
                            mediaPlayer = mp
                        }
                        mediaPlayer?.start()
                        isPlaying = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Failed to play audio: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text("Voice Note", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text("Play recorded message", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
        
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Audio Playback",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

fun copyUriToInternalStorage(context: android.content.Context, uri: android.net.Uri): java.io.File? {
    try {
        var fileName = "attachment_${System.currentTimeMillis()}"
        var extension = ""
        
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                val displayName = cursor.getString(nameIndex)
                if (displayName != null) {
                    val dotIdx = displayName.lastIndexOf('.')
                    if (dotIdx != -1) {
                        fileName = displayName.substring(0, dotIdx)
                        extension = displayName.substring(dotIdx + 1)
                    } else {
                        fileName = displayName
                    }
                }
            }
            cursor.close()
        }
        
        if (extension.isEmpty()) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            extension = when {
                mimeType.contains("pdf", ignoreCase = true) -> "pdf"
                mimeType.contains("png", ignoreCase = true) -> "png"
                mimeType.contains("gif", ignoreCase = true) -> "gif"
                mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
                mimeType.contains("word", ignoreCase = true) || mimeType.contains("officedocument", ignoreCase = true) -> "docx"
                else -> "bin"
            }
        }
        
        fileName = fileName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        if (fileName.length > 30) fileName = fileName.substring(0, 30)
        
        val destFile = java.io.File(context.filesDir, "${fileName}_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            java.io.FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destFile
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        return null
    }
}

fun openAttachmentFile(context: android.content.Context, filePath: String) {
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Attachment file not found.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(android.content.Intent.createChooser(intent, "Open Attachment with:"))
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to open attachment: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PremiumSegmentedControl(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("📒 Notes" to 0, "✅ Checklist" to 1).forEach { (label, index) ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else Color.Transparent
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp)
                    .testTag("segmented_tab_${if(index == 0) "notes" else "checklist"}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

data class SubmittedFeedback(
    val rating: Int,
    val category: String,
    val text: String,
    val email: String,
    val timestamp: Long
)

data class WebhookConfig(
    val enabled: Boolean = false,
    val formUrl: String = "",
    val ratingId: String = "entry.1000001",
    val categoryId: String = "entry.1000002",
    val textId: String = "entry.1000003",
    val emailId: String = "entry.1000004"
)

fun saveFeedbackList(context: android.content.Context, list: List<SubmittedFeedback>) {
    try {
        val jsonArray = org.json.JSONArray()
        list.forEach { fb ->
            val obj = org.json.JSONObject()
            obj.put("rating", fb.rating)
            obj.put("category", fb.category)
            obj.put("text", fb.text)
            obj.put("email", fb.email)
            obj.put("timestamp", fb.timestamp)
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences("attendez_feedback", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("feedback_data", jsonArray.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadFeedbackList(context: android.content.Context): List<SubmittedFeedback> {
    val list = mutableListOf<SubmittedFeedback>()
    try {
        val prefs = context.getSharedPreferences("attendez_feedback", android.content.Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("feedback_data", null)
        if (jsonStr != null) {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SubmittedFeedback(
                        rating = obj.getInt("rating"),
                        category = obj.getString("category"),
                        text = obj.getString("text"),
                        email = obj.getString("email"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } else {
            // Default list to start with
            list.add(SubmittedFeedback(5, "✨ General Review", "AttendEz is a game-changer! The timetable import worked flawlessly.", "student@example.com", System.currentTimeMillis() - 86400000 * 2))
            list.add(SubmittedFeedback(4, "💡 Feature Request", "Could you add a desktop widget for my homework calendar?", "study_pro@example.com", System.currentTimeMillis() - 86400000))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveWebhookConfig(context: android.content.Context, config: WebhookConfig) {
    try {
        val prefs = context.getSharedPreferences("attendez_feedback", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("webhook_enabled", config.enabled)
            .putString("webhook_url", config.formUrl)
            .putString("webhook_rating_id", config.ratingId)
            .putString("webhook_category_id", config.categoryId)
            .putString("webhook_text_id", config.textId)
            .putString("webhook_email_id", config.emailId)
            .apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadWebhookConfig(context: android.content.Context): WebhookConfig {
    try {
        val prefs = context.getSharedPreferences("attendez_feedback", android.content.Context.MODE_PRIVATE)
        return WebhookConfig(
            enabled = prefs.getBoolean("webhook_enabled", false),
            formUrl = prefs.getString("webhook_url", "") ?: "",
            ratingId = prefs.getString("webhook_rating_id", "entry.1000001") ?: "entry.1000001",
            categoryId = prefs.getString("webhook_category_id", "entry.1000002") ?: "entry.1000002",
            textId = prefs.getString("webhook_text_id", "entry.1000003") ?: "entry.1000003",
            emailId = prefs.getString("webhook_email_id", "entry.1000004") ?: "entry.1000004"
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return WebhookConfig()
    }
}

fun postFeedbackToGoogleForm(
    formResponseUrl: String,
    ratingId: String, ratingVal: String,
    categoryId: String, categoryVal: String,
    textId: String, textVal: String,
    emailId: String, emailVal: String,
    onResult: (Boolean) -> Unit = {}
) {
    try {
        val client = okhttp3.OkHttpClient()
        val formBody = okhttp3.FormBody.Builder()
            .add(ratingId, ratingVal)
            .add(categoryId, categoryVal)
            .add(textId, textVal)
            .add(emailId, emailVal)
            .build()

        val request = okhttp3.Request.Builder()
            .url(formResponseUrl)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                e.printStackTrace()
                onResult(false)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                onResult(response.isSuccessful)
                response.close()
            }
        })
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(false)
    }
}

fun launchDeveloperEmail(context: android.content.Context, feedback: SubmittedFeedback) {
    try {
        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("raadwik74242@gmail.com"))
            putExtra(android.content.Intent.EXTRA_SUBJECT, "[AttendEz Review] - ${feedback.category} (${feedback.rating} Stars)")
            
            val emailBody = """
                Hey Developer!
                
                A new review has been submitted for AttendEz.
                
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                ⭐ RATING: ${feedback.rating} / 5 Stars
                🏷️ CATEGORY: ${feedback.category}
                📧 SENDER EMAIL: ${feedback.email}
                🕒 TIME: ${java.text.SimpleDateFormat("MMM dd, yyyy  h:mm a", java.util.Locale.getDefault()).format(java.util.Date(feedback.timestamp))}
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                
                💬 REVIEW / COMMENTS:
                ${feedback.text}
                
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                Sent securely via AttendEz In-App Feedback System.
            """.trimIndent()
            
            putExtra(android.content.Intent.EXTRA_TEXT, emailBody)
        }
        val chooserIntent = android.content.Intent.createChooser(emailIntent, "Send Review Email...")
        chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Could not open mail client: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun emailFullReviewDatabase(context: android.content.Context, list: List<SubmittedFeedback>) {
    try {
        val subject = "[AttendEz Developer Console] Review Digest - ${list.size} Total Submissions"
        val bodyBuilder = java.lang.StringBuilder()
        bodyBuilder.append("AttendEz Feedback Database Export\nGenerated: ${java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        bodyBuilder.append("=========================================\n")
        bodyBuilder.append("SUMMARY STATS:\n")
        bodyBuilder.append("- Total Submissions: ${list.size}\n")
        val avg = if (list.isNotEmpty()) list.map { it.rating }.average() else 0.0
        bodyBuilder.append("- Avg Rating: ${String.format("%.2f", avg)} / 5.00 ⭐\n")
        bodyBuilder.append("=========================================\n\n")
        
        list.forEachIndexed { idx, fb ->
            bodyBuilder.append("${idx + 1}. [${fb.category}] - ${fb.rating}/5 Stars\n")
            bodyBuilder.append("   Date: ${java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(fb.timestamp))}\n")
            bodyBuilder.append("   User Email: ${fb.email}\n")
            bodyBuilder.append("   Review Text: ${fb.text}\n")
            bodyBuilder.append("   -----------------------------------------\n\n")
        }
        
        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("raadwik74242@gmail.com"))
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, bodyBuilder.toString())
        }
        val chooserIntent = android.content.Intent.createChooser(emailIntent, "Email Reviews Archive...")
        chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Could not export database via mail: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}




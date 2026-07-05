package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HealthRecord
import com.example.ui.AppViewModel
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun SettingsFitnessSyncTrendsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()
    val rawRecord by viewModel.healthRecordForSelectedDate.collectAsStateWithLifecycle()
    val googleFitSyncStatus by viewModel.googleFitSyncStatus.collectAsStateWithLifecycle()
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()
    
    val record = rawRecord ?: HealthRecord(dateString = selectedDate)

    var activeSubSection by remember { mutableIntStateOf(0) } // 0 = Trends, 1 = Google Sync, 2 = Step Tracker

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Subpage Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "FITNESS SYNC & TRENDS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Activity tracking dashboards, integrations & manual logs",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        
        HorizontalDivider(color = Color(0xFF1A1A1E), thickness = 1.dp)

        // Sub-tabs row inside Settings Subpage
        TabRow(
            selectedTabIndex = activeSubSection,
            containerColor = Color.Transparent,
            contentColor = WaterBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeSubSection]),
                    color = WaterBlue
                )
            },
            divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }
        ) {
            Tab(
                selected = activeSubSection == 0,
                onClick = { activeSubSection = 0 },
                text = { Text("Trends Graph", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeSubSection == 1,
                onClick = { activeSubSection = 1 },
                text = { Text("Google Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeSubSection == 2,
                onClick = { activeSubSection = 2 },
                text = { Text("Step Tracker", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (activeSubSection) {
                0 -> {
                    // Call existing TrendsTab directly!
                    TrendsTab(allRecords = allRecords)
                }
                1 -> {
                    // Call existing GoogleSyncTab directly!
                    GoogleSyncTab(
                        statusMessage = googleFitSyncStatus,
                        onConnectFit = {
                            viewModel.connectAndSyncGoogleFit(context)
                        },
                        onClearCache = {
                            coroutineScope.launch {
                                viewModel.updateHealthMetric(
                                    steps = 0,
                                    sleepMinutes = 0,
                                    waterMl = 0,
                                    caloriesBurned = 0,
                                    activeMinutes = 0
                                )
                                Toast.makeText(context, "Local metrics reset to baseline.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                2 -> {
                    // Embed Step Tracker details view!
                    SettingsEmbeddedStepTrackerSection(
                        viewModel = viewModel,
                        record = record
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsEmbeddedStepTrackerSection(
    viewModel: AppViewModel,
    record: HealthRecord
) {
    val context = LocalContext.current
    var inputSteps by remember { mutableStateOf(record.steps.toString()) }
    var inputGoal by remember { mutableStateOf(record.stepGoal.toString()) }
    var inputCalories by remember { mutableStateOf(record.caloriesBurned.toString()) }
    var inputActiveMinutes by remember { mutableStateOf(record.activeMinutes.toString()) }

    LaunchedEffect(record.dateString, record.steps, record.stepGoal, record.caloriesBurned, record.activeMinutes) {
        inputSteps = record.steps.toString()
        inputGoal = record.stepGoal.toString()
        inputCalories = record.caloriesBurned.toString()
        inputActiveMinutes = record.activeMinutes.toString()
    }

    val computedDistance = (record.steps * 0.00075f)

    val activityRecognitionPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        android.Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        null
    }

    var isPermissionGranted by remember {
        mutableStateOf(
            if (activityRecognitionPermission != null) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, activityRecognitionPermission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
        if (granted) {
            Toast.makeText(context, "Fitness Tracking Permission Granted! Automatic tracking active.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission Denied. Manual tracking mode only.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121420)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVITY SUMMARY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${record.steps} / ${record.stepGoal} steps", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        text = "Distance: ${String.format(java.util.Locale.US, "%.2f", computedDistance)} km | Est. Burned: ${(record.steps * 0.04f).toInt()} kcal",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    val progress = if (record.stepGoal > 0) (record.steps.toFloat() / record.stepGoal.toFloat()).coerceIn(0f, 1f) else 0f
                    CircularProgressIndicator(
                        progress = progress,
                        color = WaterBlue,
                        strokeWidth = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(text = "${(progress * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Manual Input Section
        Text(
            text = "ENTER DETAILS MANUALLY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = inputSteps,
                onValueChange = { inputSteps = it },
                label = { Text("Steps Walked") },
                placeholder = { Text("e.g., 8500") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = WaterBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = inputGoal,
                onValueChange = { inputGoal = it },
                label = { Text("Step Goal Target") },
                placeholder = { Text("e.g., 10000") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = WaterBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = inputCalories,
                        onValueChange = { inputCalories = it },
                        label = { Text("Food Calories (kcal)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = WaterBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = inputActiveMinutes,
                        onValueChange = { inputActiveMinutes = it },
                        label = { Text("Active Minutes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = WaterBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    val s = inputSteps.toIntOrNull() ?: record.steps
                    val g = inputGoal.toIntOrNull() ?: record.stepGoal
                    val c = inputCalories.toIntOrNull() ?: record.caloriesBurned
                    val m = inputActiveMinutes.toIntOrNull() ?: record.activeMinutes
                    viewModel.updateHealthMetric(
                        steps = s,
                        stepGoal = g,
                        caloriesBurned = c,
                        activeMinutes = m
                    )
                    Toast.makeText(context, "Statistics saved manually!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
            ) {
                Text("Save Manual Details", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Permission card
        Text(
            text = "FITNESS TRACKING PERMISSIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = "Permission state",
                        tint = if (isPermissionGranted) SuccessGreen else Color.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPermissionGranted) "Fitness Permission Granted" else "Physical Activity Permission Required",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Granting this permission allows Life OS to automatically track physical steps in the background using your device's internal step sensors.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (activityRecognitionPermission != null) {
                            permissionLauncher.launch(activityRecognitionPermission)
                        } else {
                            Toast.makeText(context, "Physical tracking is automatically active on this OS version.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPermissionGranted) Color.White.copy(alpha = 0.1f) else WaterBlue
                    ),
                ) {
                    Text(
                        text = if (isPermissionGranted) "Permission Active" else "Request Permission",
                        color = if (isPermissionGranted) Color.LightGray else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

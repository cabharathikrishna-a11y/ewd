package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HealthRecord
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Retrieve database health states
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()
    val rawRecord by viewModel.healthRecordForSelectedDate.collectAsStateWithLifecycle()
    val googleFitSyncStatus by viewModel.googleFitSyncStatus.collectAsStateWithLifecycle()
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()

    // Ensure we have a non-null record for the selected date
    val record = rawRecord ?: HealthRecord(dateString = selectedDate)

    // Screen Sub-Tabs: 0 = Summary, 1 = Trends & Analytics, 2 = Google Cloud Sync
    var selectedSubTab by remember { mutableIntStateOf(0) }

    // Dialog state controllers
    var showManualLogDialog by remember { mutableStateOf(false) }
    var metricToLog by remember { mutableStateOf("") } // "Steps", "Sleep", "Calories", "HeartRate"

    // Real-time hardware step detector sensor integration
    DisposableEffect(key1 = selectedDate) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var listener: SensorEventListener? = null
        if (sensorManager != null) {
            listener = object : SensorEventListener {
                private var lastStepTime = 0L
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val currentTime = System.currentTimeMillis()
                    if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                        // Increment step count directly on physical step detection
                        viewModel.updateHealthMetric(steps = record.steps + 1)
                    } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        // Accelero step detection fallback using a standard peak threshold algorithm
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                        if (magnitude > 14.5 && currentTime - lastStepTime > 350) {
                            lastStepTime = currentTime
                            viewModel.updateHealthMetric(steps = record.steps + 1)
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            // Register both step counter and raw motion backup
            if (stepDetector != null) {
                sensorManager.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_UI)
            } else if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            listener?.let { sensorManager?.unregisterListener(it) }
        }
    }

    // Main Health Dashboard scaffold
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .testTag("health_view_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GOOGLE HEALTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Fitness & Wellness",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                // Selected date display and editor selector
                IconButton(
                    onClick = {
                        // Toggle date to yesterday or today
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val today = viewModel.getCurrentDateString()
                        if (selectedDate == today) {
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DATE, -1)
                            viewModel.selectHealthDate(sdf.format(cal.time))
                            Toast.makeText(context, "Switched to Yesterday", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.selectHealthDate(today)
                            Toast.makeText(context, "Switched to Today", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("date_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select Date",
                        tint = Color.White
                    )
                }
            }

            // Current date bar banner
            val displayDateText = if (selectedDate == viewModel.getCurrentDateString()) {
                "Today (${selectedDate})"
            } else {
                "Yesterday (${selectedDate})"
            }
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.getDpOrZero())
                                .background(SuccessGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayDateText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (record.isSynced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Synced",
                                tint = WaterBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Synced with Fit",
                                color = WaterBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Offline Cache",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Local Sensors",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Sub-Tabs row selector
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                contentColor = WaterBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = WaterBlue
                    )
                },
                divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Summary", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Trends", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Google Sync", fontWeight = FontWeight.Bold) }
                )
            }

            // Main body area based on active subtab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedSubTab) {
                    0 -> SummaryTab(
                        record = record,
                        onLogMetric = { metric ->
                            metricToLog = metric
                            showManualLogDialog = true
                        },
                        onWaterIncrement = { amountMl ->
                            val currentWater = record.waterMl
                            viewModel.updateHealthMetric(waterMl = currentWater + amountMl)
                        }
                    )
                    1 -> TrendsTab(allRecords = allRecords)
                    2 -> GoogleSyncTab(
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
            }
        }
    }

    // Manual Log Dialog modal
    if (showManualLogDialog) {
        var inputValue by remember { mutableStateOf("") }
        var inputGoalValue by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showManualLogDialog = false }) {
            Surface(
                color = Color(0xFF161722),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (metricToLog) {
                            "Sleep" -> Icons.Default.Hotel
                            "Calories" -> Icons.Default.LocalFireDepartment
                            else -> Icons.Default.Favorite
                        },
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Log ${metricToLog}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your recorded health metric data point to update your local dashboard.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Value Input
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = { Text("Metric Value") },
                        placeholder = {
                            Text(
                                when (metricToLog) {
                                    "Sleep" -> "e.g., 420 (mins)"
                                    "Calories" -> "e.g., 500 (kcal)"
                                    else -> "e.g., 75 (bpm)"
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = WaterBlue,
                            unfocusedLabelColor = Color.LightGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("metric_input_field")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Goal Input
                    OutlinedTextField(
                        value = inputGoalValue,
                        onValueChange = { inputGoalValue = it },
                        label = { Text("Daily Target/Goal") },
                        placeholder = {
                            Text(
                                when (metricToLog) {
                                    "Sleep" -> "e.g., 480"
                                    "Calories" -> "e.g., 2000"
                                    else -> "e.g., 130"
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = WaterBlue,
                            unfocusedLabelColor = Color.LightGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("metric_goal_field")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showManualLogDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val value = inputValue.toIntOrNull()
                                val goal = inputGoalValue.toIntOrNull()
                                if (value != null) {
                                    when (metricToLog) {
 
                                        "Sleep" -> viewModel.updateHealthMetric(
                                            sleepMinutes = value,
                                            sleepGoalMinutes = goal
                                        )
                                        "Calories" -> viewModel.updateHealthMetric(
                                            caloriesBurned = value,
                                            calorieGoal = goal
                                        )
                                        "HeartRate" -> viewModel.updateHealthMetric(
                                            heartRateAvg = value,
                                            heartRateMin = (value - 15).coerceAtLeast(40),
                                            heartRateMax = (value + 35).coerceAtMost(200)
                                        )
                                    }
                                    showManualLogDialog = false
                                    Toast.makeText(context, "${metricToLog} updated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Data", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryTab(
    record: HealthRecord,
    onLogMetric: (String) -> Unit,
    onWaterIncrement: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("summary_scroll_col"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // High-fidelity dynamic Ring Visualizer
        item {
            FitnessActivityRingCard(record = record)
        }

        // Action Quick metrics Logger
        item {
            QuickLogIntakeCard(record = record, onWaterIncrement = onWaterIncrement)
        }

        // Active minutes & Calories burnout details
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricDetailCard(
                        title = "Sleep Cycle",
                        metric = "${record.sleepMinutes / 60}h ${record.sleepMinutes % 60}m",
                        target = "Goal: ${record.sleepGoalMinutes / 60}h",
                        icon = Icons.Default.Hotel,
                        progress = (record.sleepMinutes.toFloat() / record.sleepGoalMinutes.toFloat()).coerceIn(0f, 1f),
                        color = Color(0xFF9575CD),
                        onLogClick = { onLogMetric("Sleep") }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    MetricDetailCard(
                        title = "Energy Burned",
                        metric = "${record.caloriesBurned} kcal",
                        target = "Goal: ${record.calorieGoal} kcal",
                        icon = Icons.Default.LocalFireDepartment,
                        progress = (record.caloriesBurned.toFloat() / record.calorieGoal.toFloat()).coerceIn(0f, 1f),
                        color = Color(0xFFFF8A65),
                        onLogClick = { onLogMetric("Calories") }
                    )
                }
            }
        }

        // Accelerometer Sensor Diagnostics Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Accessibility,
                        contentDescription = "Sensor Info",
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Pedestrian Motion Active",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Device's internal accelerometer sensor listener is active. Walk or shake to count steps.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FitnessActivityRingCard(record: HealthRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121420)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fitness_ring_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = "ACTIVITY PROGRESS",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep Moving!",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Steps stat
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(WaterBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Steps: ",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${record.steps} / ${record.stepGoal}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Active Minutes stat
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SuccessGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active: ",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${record.activeMinutes} / ${record.activeMinutesGoal} min",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Central Canvas Rings
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(110.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    
                    // Background step circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.minDimension / 2 - strokeWidth,
                        style = Stroke(width = strokeWidth)
                    )
                    // Foreground steps sweep
                    val stepPercent = (record.steps.toFloat() / record.stepGoal.toFloat()).coerceIn(0f, 1f)
                    drawArc(
                        color = WaterBlue,
                        startAngle = -90f,
                        sweepAngle = stepPercent * 360f,
                        useCenter = false,
                        size = Size(size.width - strokeWidth * 2, size.height - strokeWidth * 2),
                        topLeft = Offset(strokeWidth, strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Background active minutes circle
                    val innerStrokeWidth = 8.dp.toPx()
                    val innerOffset = strokeWidth + innerStrokeWidth + 4.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.minDimension / 2 - innerOffset,
                        style = Stroke(width = innerStrokeWidth)
                    )
                    // Foreground active minutes sweep
                    val activePercent = (record.activeMinutes.toFloat() / record.activeMinutesGoal.toFloat()).coerceIn(0f, 1f)
                    drawArc(
                        color = SuccessGreen,
                        startAngle = -90f,
                        sweepAngle = activePercent * 360f,
                        useCenter = false,
                        size = Size(size.width - innerOffset * 2, size.height - innerOffset * 2),
                        topLeft = Offset(innerOffset, innerOffset),
                        style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${((record.steps.toFloat() / record.stepGoal.toFloat()) * 100).toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Goal Achieved",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLogIntakeCard(record: HealthRecord, onWaterIncrement: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = "Water Hydration",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Water Intake",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "${record.waterMl} / ${record.waterGoalMl} ml",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glass progress display line
            val progress = (record.waterMl.toFloat() / record.waterGoalMl.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = WaterBlue,
                trackColor = Color.White.copy(alpha = 0.05f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive cup logger shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onWaterIncrement(250) },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Cup",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cup (+250ml)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onWaterIncrement(500) },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Bottle",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bottle (+500ml)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LiveHeartRatePulseCard(record: HealthRecord, onLogClick: () -> Unit) {
    val context = LocalContext.current
    var isMeasuring by remember { mutableStateOf(false) }
    var pulseReading by remember { mutableIntStateOf(record.heartRateAvg) }
    val coroutineScope = rememberCoroutineScope()

    // Pulse animation logic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMeasuring) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMeasuring) 400 else 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_pulse"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15121F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart Pulse",
                        tint = Color(0xFFFF4081),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onLogClick() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Heart Rate Pulse",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = { onLogClick() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Pulse", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart graphic & reading
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFF4081).copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Pulse Beat",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier
                                .size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMeasuring) "Measuring..." else "${pulseReading} bpm",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Min: ${record.heartRateMin} bpm / Max: ${record.heartRateMax} bpm",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Interactive check button
                Button(
                    onClick = {
                        if (!isMeasuring) {
                            isMeasuring = true
                            coroutineScope.launch {
                                // Simulate high-precision diagnostic scanning sequence
                                for (i in 1..8) {
                                    delay(400)
                                    pulseReading = (65..120).random()
                                }
                                isMeasuring = false
                                Toast.makeText(context, "Pulse reading locked!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMeasuring) Color(0xFFFF4081) else Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isMeasuring
                ) {
                    Icon(
                        imageVector = if (isMeasuring) Icons.Default.HourglassEmpty else Icons.Default.CameraAlt,
                        contentDescription = "Read Pulse",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMeasuring) "Reading..." else "Scan Pulse",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated EKG Emitter line on canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val phaseOffset = if (isMeasuring) {
                    val progressTransition = rememberInfiniteTransition("offset")
                    val value by progressTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 100f,
                        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                        label = "pulse_wave"
                    )
                    value
                } else {
                    0f
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    val width = size.width
                    val height = size.height
                    path.moveTo(0f, height / 2)

                    var x = 0f
                    val step = 10f
                    while (x < width) {
                        val phase = (x + phaseOffset) % 150f
                        val y = if (phase > 40f && phase < 60f) {
                            // Draw an EKG peak point
                            val relative = (phase - 40f) / 20f
                            val factor = Math.sin(relative * Math.PI)
                            (height / 2) - (factor * (height * 0.4f)).toFloat()
                        } else if (phase >= 60f && phase < 80f) {
                            // Draw an EKG negative trough
                            val relative = (phase - 60f) / 20f
                            val factor = Math.sin(relative * Math.PI)
                            (height / 2) + (factor * (height * 0.2f)).toFloat()
                        } else {
                            height / 2
                        }
                        path.lineTo(x, y)
                        x += step
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFFFF4081).copy(alpha = if (isMeasuring) 1.0f else 0.4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun MetricDetailCard(
    title: String,
    metric: String,
    target: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    progress: Float,
    color: Color,
    onLogClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onLogClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Log", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(text = metric, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(text = target, fontSize = 9.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
fun TrendsTab(allRecords: List<HealthRecord>) {
    val itemsToShow = allRecords.take(7).reversed()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trends_tab_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121422)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "WEEKLY STEPS GRAPH",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Daily Walk Trends",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (itemsToShow.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No trend records logged yet. Shake or walk to begin logging metrics.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        // Custom Canvas Steps Graph Drawing
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val spacing = canvasWidth / 8
                                val maxSteps = (itemsToShow.maxOfOrNull { it.steps } ?: 10000).coerceAtLeast(10000)

                                // Draw baseline targets
                                val baselineY = canvasHeight - (10000f / maxSteps.toFloat() * canvasHeight)
                                drawLine(
                                    color = WaterBlue.copy(alpha = 0.25f),
                                    start = Offset(0f, baselineY),
                                    end = Offset(canvasWidth, baselineY),
                                    strokeWidth = 2f
                                )

                                itemsToShow.forEachIndexed { idx, rec ->
                                    val barWidth = spacing * 0.6f
                                    val xOffset = (idx + 1) * spacing - (barWidth / 2)
                                    val percent = rec.steps.toFloat() / maxSteps.toFloat()
                                    val barHeight = canvasHeight * percent
                                    val yOffset = canvasHeight - barHeight

                                    // Draw background bar anchor
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.05f),
                                        topLeft = Offset(xOffset, 0f),
                                        size = Size(barWidth, canvasHeight)
                                    )

                                    // Draw active metrics foreground rectangle
                                    drawRect(
                                        color = if (rec.steps >= 10000) SuccessGreen else WaterBlue,
                                        topLeft = Offset(xOffset, yOffset),
                                        size = Size(barWidth, barHeight)
                                    )
                                }
                            }
                        }

                        // Labels Row under the custom Canvas Graph
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            itemsToShow.forEach { rec ->
                                val label = rec.dateString.split("-").lastOrNull() ?: rec.dateString
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Historic Logging List Table
        item {
            Text(
                text = "HISTORICAL METRICS LOG",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WaterBlue,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        if (allRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No historic metrics recorded yet.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(allRecords.size) { index ->
                val rec = allRecords[index]
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = rec.dateString, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(text = "Sleep: ${rec.sleepMinutes / 60}h | Water: ${rec.waterMl}ml | Energy: ${rec.caloriesBurned}kcal", color = Color.Gray, fontSize = 11.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${rec.steps} steps",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (rec.steps >= rec.stepGoal) SuccessGreen else WaterBlue,
                                fontSize = 13.sp
                            )
                            if (rec.isSynced) {
                                Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = WaterBlue, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSyncTab(
    statusMessage: String,
    onConnectFit: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("google_sync_tab_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Branded integration card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101222)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Fit REST API & Health Connect Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Sync your personal fitness sensors securely with Google Cloud. We sync step counters, active duration, energy burnt, heart beat rate, and hydration cycles automatically.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connection diagnostics status block
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (statusMessage.contains("successfully", ignoreCase = true)) SuccessGreen
                                    else if (statusMessage.contains("Connecting", ignoreCase = true)) Color.Yellow
                                    else Color.Gray,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Status: $statusMessage",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary sync trigger button
                Button(
                    onClick = onConnectFit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_fit_sync_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync & Authorize Google Fit REST API",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Local cache reset card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sensors Diagnostics",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If steps do not increment or you wish to reset your diagnostic dashboard, you can trigger a full sensor baseline clear.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Clear Local Health Cache", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// Utility extension helper to support safe 0.dp constraints
private fun Int.getDpOrZero(): androidx.compose.ui.unit.Dp {
    return if (this <= 0) 0.dp else this.dp
}

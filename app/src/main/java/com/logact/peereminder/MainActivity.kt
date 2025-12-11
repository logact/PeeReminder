package com.logact.peereminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.logact.peereminder.alarm.AlarmScheduler
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.ui.theme.*
import com.logact.peereminder.utils.PermissionHelper
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var prefsManager: SharedPrefsManager
    private lateinit var alarmScheduler: AlarmScheduler
    
    // Notification permission launcher (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = SharedPrefsManager.getInstance(this)
        alarmScheduler = AlarmScheduler(this)
        
        // Note: Notification channel is created in AlarmReceiver.createNotificationChannel()
        // with IMPORTANCE_MAX for full-screen intent support
        // We don't create it here to avoid conflicts
        
        // Request notification permission on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            PeeReminderTheme {
                MainScreen(
                    onStartPauseClick = { shouldStart ->
                        if (shouldStart) {
                            startReminder()
                        } else {
                            pauseReminder()
                        }
                    },
                    onSettingsClick = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestBatteryOptimization = {
                        PermissionHelper.openBatteryOptimizationSettings(this@MainActivity)
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Verify alarm is still scheduled if reminder is active
        // This handles cases where the app was killed and restarted
        if (prefsManager.isActive) {
            val nextAlarmTime = alarmScheduler.getNextAlarmTime()
            val now = System.currentTimeMillis()
            
            // If no alarm is scheduled or alarm time has passed, reschedule
            if (nextAlarmTime <= 0 || nextAlarmTime <= now) {
                Log.d("MainActivity", "Alarm missing or expired, rescheduling...")
                alarmScheduler.scheduleNextAlarm()
            }
        }
    }
    
    private fun startReminder() {
        // Check exact alarm permission (Android 12+)
        if (!PermissionHelper.hasExactAlarmPermission(this)) {
            Log.w("MainActivity", "Cannot start reminder - exact alarm permission not granted")
            // Permission dialog will be shown in UI
            return
        }
        
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            !PermissionHelper.hasNotificationPermission(this)) {
            Log.w("MainActivity", "Cannot start reminder - notification permission not granted")
            // Permission dialog will be shown in UI
            return
        }
        
        try {
            prefsManager.isActive = true
            alarmScheduler.scheduleNextAlarm()
            val nextAlarm = alarmScheduler.getNextAlarmTime()
            Log.d("MainActivity", "Reminder started. Next alarm at: ${java.util.Date(nextAlarm)}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start reminder", e)
            prefsManager.isActive = false
        }
    }
    
    private fun pauseReminder() {
        prefsManager.isActive = false
        alarmScheduler.cancelAlarm()
    }
}

@Composable
fun MainScreen(
    onStartPauseClick: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = SharedPrefsManager.getInstance(context)
    val alarmScheduler = AlarmScheduler(context)
    
    // Check if app is in debug mode
    val isTestMode = remember {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    var isActive by remember { mutableStateOf(prefsManager.isActive) }
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(prefsManager, alarmScheduler)) }
    var nextAlarmTime by remember { mutableStateOf(formatNextAlarmTime(prefsManager, alarmScheduler)) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(!PermissionHelper.isBatteryOptimizationDisabled(context)) }
    
    // Function to check and request permissions before starting reminder
    fun checkPermissionsAndStart(): Boolean {
        if (!PermissionHelper.hasExactAlarmPermission(context)) {
            showExactAlarmDialog = true
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            !PermissionHelper.hasNotificationPermission(context)) {
            showNotificationPermissionDialog = true
            return false
        }
        return true
    }
    
    // Update time remaining every second and check battery optimization
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isActive = prefsManager.isActive
            timeRemaining = calculateTimeRemaining(prefsManager, alarmScheduler)
            nextAlarmTime = formatNextAlarmTime(prefsManager, alarmScheduler)
            // Check battery optimization status periodically
            isBatteryOptimized = !PermissionHelper.isBatteryOptimizationDisabled(context)
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Test mode indicator and Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Mode Indicator
                if (isTestMode) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = BrightYellow.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "🧪 TEST MODE",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrightYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // Settings button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(56.dp) // Large touch target
                ) {
                    Text(
                        text = "⚙️",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Middle: Status and time display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Status text (ACTIVE/PAUSED)
                Text(
                    text = if (isActive) "ACTIVE" else "PAUSED",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isActive) BrightGreen else BrightRed,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Time display
                if (isActive && timeRemaining.isNotEmpty()) {
                    Text(
                        text = "Time Remaining:",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrightText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = timeRemaining,
                        style = MaterialTheme.typography.displayMedium,
                        color = BrightYellow,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else if (isActive && nextAlarmTime.isNotEmpty()) {
                    Text(
                        text = "Next Reminder At:",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrightText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = nextAlarmTime,
                        style = MaterialTheme.typography.displayMedium,
                        color = BrightYellow,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Reminder is paused",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrightText,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Battery optimization warning (if optimized)
            if (isBatteryOptimized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = BrightYellow.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Battery Optimization Enabled",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Alarms may not work reliably. Disable battery optimization for best results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText
                            )
                        }
                        TextButton(
                            onClick = { showBatteryOptimizationDialog = true }
                        ) {
                            Text(
                                text = "Fix",
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Bottom: Start/Pause button
            Button(
                onClick = { 
                    if (!isActive) {
                        // Check permissions before starting
                        if (checkPermissionsAndStart()) {
                            // Permissions OK, start reminder
                            onStartPauseClick(true)
                        }
                        // If permissions not OK, dialog will be shown
                    } else {
                        onStartPauseClick(false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // Large touch target
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) BrightRed else BrightGreen
                )
            ) {
                Text(
                    text = if (isActive) "PAUSE REMINDER" else "START REMINDER",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrightText
                )
            }
        }
    }
    
    // Exact Alarm Permission Dialog (Android 12+)
    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText
                )
            },
            text = {
                Text(
                    text = "This app needs permission to schedule exact alarms for reliable reminders. Please grant this permission in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrightText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExactAlarmDialog = false
                        PermissionHelper.openAlarmSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen
                    )
                ) {
                    Text(
                        text = "Open Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExactAlarmDialog = false }
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            containerColor = DarkGray
        )
    }
    
    // Notification Permission Dialog (Android 13+)
    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = {
                Text(
                    text = "Notification Permission",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText
                )
            },
            text = {
                Text(
                    text = "This app needs notification permission to show alarm reminders when the app is closed or the screen is locked.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrightText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationPermissionDialog = false
                        onRequestNotificationPermission()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen
                    )
                ) {
                    Text(
                        text = "Grant Permission",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationPermissionDialog = false }
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            containerColor = DarkGray
        )
    }
    
    // Battery Optimization Dialog (Android 6.0+)
    if (showBatteryOptimizationDialog) {
        var showInstructions by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            title = {
                Text(
                    text = "Battery Optimization Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Battery optimization can prevent alarms from working when the app is in the background, screen is locked, or app is closed.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                    
                    Text(
                        text = "To fix this, you need to disable battery optimization for this app.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = { showInstructions = !showInstructions },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showInstructions) "Hide Instructions" else "Show Step-by-Step Instructions",
                            color = BrightYellow
                        )
                    }
                    
                    if (showInstructions) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quick Steps:",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text("1. Tap 'Open Settings' button below", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("2. You'll see a popup - tap 'Allow'", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("3. If no popup, find 'Pee Reminder' in the list", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("4. Change to 'Don't optimize' or 'Not optimized'", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            
                            HorizontalDivider(color = DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "For Vivo/OriginOS 4 (REQUIRES MULTIPLE SETTINGS):",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text("1. Autostart: Settings → Apps → Pee Reminder → Autostart → Enable", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                            Text("2. Background power: Settings → Battery → Background power consumption → Don't restrict", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                            Text("3. Battery optimization: Settings → Battery → Battery Optimization → Don't Optimize", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                            Text("4. Special access: Settings → Apps → Special app access → Autostart → Enable", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                            Text("5. Full-screen intents: Settings → Apps → Special app access → Full screen intents → Enable", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                            
                            HorizontalDivider(color = DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "For Samsung/Xiaomi/Huawei/OnePlus:",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightText,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Settings → Apps → Pee Reminder → Battery → Unrestricted/Don't optimize", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = BrightText.copy(alpha = 0.8f))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        onRequestBatteryOptimization()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Open Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatteryOptimizationDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            containerColor = DarkGray
        )
    }
}

private fun calculateTimeRemaining(
    prefsManager: SharedPrefsManager,
    alarmScheduler: AlarmScheduler
): String {
    val nextAlarm = alarmScheduler.getNextAlarmTime()
    if (nextAlarm <= 0) return ""
    
    val now = System.currentTimeMillis()
    val remaining = nextAlarm - now
    
    if (remaining <= 0) return ""
    
    val hours = remaining / (1000 * 60 * 60)
    val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (remaining % (1000 * 60)) / 1000
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatNextAlarmTime(
    prefsManager: SharedPrefsManager,
    alarmScheduler: AlarmScheduler
): String {
    val nextAlarm = alarmScheduler.getNextAlarmTime()
    if (nextAlarm <= 0) return ""
    
    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return dateFormat.format(Date(nextAlarm))
}
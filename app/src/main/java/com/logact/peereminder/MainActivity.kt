package com.logact.peereminder

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.logact.peereminder.alarm.AlarmScheduler
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.ui.theme.*
import com.logact.peereminder.utils.PermissionHelper
import com.logact.peereminder.R
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
                    },
                    onRequestOverlayPermission = {
                        PermissionHelper.openOverlayPermissionSettings(this@MainActivity)
                    },
                    onRequestAutoStart = {
                        PermissionHelper.openAutoStartSettings(this@MainActivity)
                    }
                )
            }
        }
        
        // Schedule daily reset on app start
        try {
            alarmScheduler.scheduleDailyReset()
            Log.d("MainActivity", "Daily reset scheduled on app start")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to schedule daily reset on app start", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Verify alarm is still scheduled if reminder is active
        // This handles cases where the app was killed and restarted
        verifyAndRescheduleAlarmIfNeeded()
        
        // Check if we missed an alarm (OriginOS 4 blocking scenario)
        checkForMissedAlarm()
        
        // Ensure daily reset is scheduled (in case it was cancelled or missed)
        try {
            alarmScheduler.scheduleDailyReset()
            Log.d("MainActivity", "Daily reset verified/rescheduled on resume")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to schedule daily reset on resume", e)
        }
    }
    
    /**
     * Check if an alarm was supposed to fire but didn't (OriginOS 4 blocking)
     * This detects when the alarm time has passed but the receiver wasn't called
     */
    private fun checkForMissedAlarm() {
        if (!prefsManager.isActive) {
            return
        }
        
        val nextAlarmTime = alarmScheduler.getNextAlarmTime()
        val now = System.currentTimeMillis()
        
        // If alarm time has passed by more than 1 minute, it likely didn't fire
        if (nextAlarmTime > 0 && nextAlarmTime < now - 60000) {
            val minutesLate = (now - nextAlarmTime) / 1000 / 60
            Log.w("MainActivity", "⚠️ POTENTIAL MISSED ALARM DETECTED")
            Log.w("MainActivity", "Alarm was scheduled for: ${java.util.Date(nextAlarmTime)}")
            Log.w("MainActivity", "Current time: ${java.util.Date(now)}")
            Log.w("MainActivity", "Alarm is $minutesLate minutes late")
            Log.w("MainActivity", "This suggests OriginOS 4 blocked the broadcast")
            Log.w("MainActivity", "AlarmReceiver.onReceive() was never called")
            
            // Check if this is OriginOS 4
            val isOriginOS4 = android.os.Build.MANUFACTURER.lowercase().contains("vivo") && 
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            
            if (isOriginOS4) {
                Log.e("MainActivity", "❌ CONFIRMED: OriginOS 4 blocked the alarm broadcast")
                Log.e("MainActivity", "The alarm was scheduled correctly, but BroadcastReceiver never received it")
                Log.e("MainActivity", "User needs to configure all OriginOS 4 settings")
                
                // Reschedule the alarm for the next interval
                try {
                    alarmScheduler.scheduleNextAlarm()
                    Log.d("MainActivity", "Alarm rescheduled for next interval")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to reschedule alarm", e)
                }
            }
        }
    }
    
    /**
     * Verify alarm status and reschedule if needed
     * This is critical when the app is restarted after being killed
     */
    private fun verifyAndRescheduleAlarmIfNeeded() {
        if (!prefsManager.isActive) {
            Log.d("MainActivity", "Reminder is not active, skipping alarm verification")
            return
        }
        
        Log.d("MainActivity", "=== Verifying alarm status ===")
        
        // Use AlarmScheduler's ensureAlarmScheduled method
        val alarmScheduled = alarmScheduler.ensureAlarmScheduled()
        
        if (alarmScheduled) {
            Log.d("MainActivity", "✅ Alarm is properly scheduled")
            val nextAlarmTime = alarmScheduler.getNextAlarmTime()
            Log.d("MainActivity", "Next alarm at: ${java.util.Date(nextAlarmTime)}")
        } else {
            Log.w("MainActivity", "⚠️ Alarm verification failed or alarm was rescheduled")
        }
        
        // Also run comprehensive diagnostics (for debugging)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val verifier = com.logact.peereminder.alarm.AlarmVerifier(this)
                val status = verifier.verifyAlarmStatus()
                
                if (!status.isAlarmProperlyScheduled) {
                    Log.w("MainActivity", "Alarm diagnostic check failed:")
                    Log.w("MainActivity", verifier.getDiagnosticReport())
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error running alarm diagnostics", e)
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
            
            // Verify alarm was scheduled correctly
            val verified = alarmScheduler.verifyAlarmScheduled()
            if (verified) {
                Log.d("MainActivity", "✅ Alarm verified - scheduled correctly")
            } else {
                Log.w("MainActivity", "⚠️ Alarm verification failed - may need to reschedule")
            }
            
            // Schedule daily reset when reminder starts
            alarmScheduler.scheduleDailyReset()
            Log.d("MainActivity", "Daily reset scheduled when reminder started")
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
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
    onRequestAutoStart: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = SharedPrefsManager.getInstance(context)
    val alarmScheduler = AlarmScheduler(context)
    
    var isActive by remember { mutableStateOf(prefsManager.isActive) }
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(prefsManager, alarmScheduler)) }
    var nextAlarmTime by remember { mutableStateOf(formatNextAlarmTime(prefsManager, alarmScheduler)) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showAutoStartDialog by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(!PermissionHelper.isBatteryOptimizationDisabled(context)) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    
    // Check if device might need auto-start permission (Chinese ROMs)
    // Note: On some devices (like OriginOS), alarms may work without explicit auto-start
    val needsAutoStart = remember {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        // Vivo/OriginOS: Alarms often work without auto-start, so make it optional
        // Show for other Chinese ROMs where auto-start is more critical
        manufacturer.contains("xiaomi") || 
        manufacturer.contains("redmi") || 
        manufacturer.contains("oppo") || 
        manufacturer.contains("oneplus") || 
        manufacturer.contains("huawei") || 
        manufacturer.contains("honor")
        // Note: Vivo/OriginOS removed - alarms work without it
    }
    
    // For Vivo/OriginOS, show a less critical info card instead
    val isVivoOriginOS = remember {
        android.os.Build.MANUFACTURER.lowercase().contains("vivo")
    }
    
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
        // Optional: Check overlay permission (for full-screen when unlocked)
        // Don't block starting, but show info dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            !PermissionHelper.hasOverlayPermission(context)) {
            // Show info dialog but allow starting anyway
            showOverlayPermissionDialog = true
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
            // Check overlay permission status periodically
            hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top: Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Middle: Status and time display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Status text (ACTIVE/PAUSED)
                Text(
                    text = if (isActive) stringResource(R.string.status_active) else stringResource(R.string.status_paused),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isActive) BrightGreen else BrightRed,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Time display
                if (isActive && timeRemaining.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.time_remaining),
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
                        text = stringResource(R.string.next_reminder_at),
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
                        text = stringResource(R.string.reminder_is_paused),
                        style = MaterialTheme.typography.titleLarge,
                        color = BrightText,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
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
                                text = "⚠️ ${stringResource(R.string.battery_optimization_title)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.battery_optimization_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText
                            )
                        }
                        TextButton(
                            onClick = { showBatteryOptimizationDialog = true }
                        ) {
                            Text(
                                text = stringResource(R.string.fix),
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Overlay permission warning (if not granted) - Android 6.0+
            if (!hasOverlayPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                                text = "ℹ️ ${stringResource(R.string.display_over_other_apps)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.display_over_other_apps_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText
                            )
                        }
                        TextButton(
                            onClick = { showOverlayPermissionDialog = true }
                        ) {
                            Text(
                                text = stringResource(R.string.grant),
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Auto-start permission warning (for Chinese ROMs, except Vivo/OriginOS)
            if (needsAutoStart) {
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
                                text = "⚠️ Auto-Start Permission",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Required for alarms to work when app is killed. Enable auto-start for best reliability.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText
                            )
                        }
                        TextButton(
                            onClick = { showAutoStartDialog = true }
                        ) {
                            Text(
                                text = "Enable",
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Vivo/OriginOS 4 warning card (critical - alarms may not work when swiped away)
            if (isVivoOriginOS) {
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
                                text = "⚠️ OriginOS 4 Alarm Warning",
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Alarms may NOT trigger when app is swiped away. Configure all required settings for best reliability.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText
                            )
                        }
                        TextButton(
                            onClick = { showAutoStartDialog = true }
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
                    text = if (isActive) stringResource(R.string.pause_reminder) else stringResource(R.string.start_reminder),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = WhiteText
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
                    text = stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.exact_alarm_permission_message_detailed),
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
                        text = stringResource(R.string.open_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteText
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExactAlarmDialog = false }
                ) {
                    Text(
                        text = stringResource(R.string.later),
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
                    text = stringResource(R.string.notification_permission),
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_message),
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
                        text = stringResource(R.string.grant_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteText
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationPermissionDialog = false }
                ) {
                    Text(
                        text = stringResource(R.string.later),
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
                    text = stringResource(R.string.battery_optimization_required),
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
                        text = stringResource(R.string.battery_optimization_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                    
                    Text(
                        text = stringResource(R.string.battery_optimization_fix),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = { showInstructions = !showInstructions },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showInstructions) stringResource(R.string.hide_instructions) else stringResource(R.string.show_instructions),
                            color = BrightYellow
                        )
                    }
                    
                    if (showInstructions) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quick_steps),
                                style = MaterialTheme.typography.titleSmall,
                                color = BrightYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text("1. ${stringResource(R.string.open_settings)}", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("2. ${stringResource(R.string.later)}", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("3. ${stringResource(R.string.later)}", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            Text("4. ${stringResource(R.string.later)}", style = MaterialTheme.typography.bodySmall, color = BrightText)
                            
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
                        text = stringResource(R.string.open_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteText,
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
                        text = stringResource(R.string.later),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            containerColor = DarkGray
        )
    }
    
    // Overlay Permission Dialog (Android 6.0+)
    // This is optional but recommended for full-screen alarms when device is unlocked
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.display_over_other_apps),
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
                        text = stringResource(R.string.display_over_other_apps_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                    
                    Text(
                        text = stringResource(R.string.without_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Full-screen alarm only works when device is LOCKED",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• When unlocked, you'll see a notification (tap to open)",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = stringResource(R.string.with_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Full-screen alarm works even when device is UNLOCKED",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.to_grant_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Tap 'Open Settings' below",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "2. Find 'Display over other apps' or 'Appear on top'",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "3. Enable the toggle for Pee Reminder",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        onRequestOverlayPermission()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOverlayPermissionDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.later),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                }
            },
            containerColor = DarkGray
        )
    }
    
    // Auto-Start Permission Dialog (for Chinese ROMs)
    if (showAutoStartDialog) {
        AlertDialog(
            onDismissRequest = { showAutoStartDialog = false },
            title = {
                Text(
                    text = "Auto-Start Permission",
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
                        text = "Auto-start permission is required on your device for alarms to work when the app is killed or swiped away.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText
                    )
                    
                    Text(
                        text = "Without this permission:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Alarms may not trigger when app is swiped away",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• Alarms may be delayed or blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "With auto-start enabled:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Alarms work reliably even when app is killed",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• Alarms trigger on time, every time",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrightText.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Instructions for your device:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrightYellow,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val instructions = PermissionHelper.getAutoStartInstructions(context)
                    instructions.split("\n").forEach { line ->
                        if (line.isNotBlank()) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoStartDialog = false
                        onRequestAutoStart()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Open Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAutoStartDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.later),
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
    
    val dateFormat = SimpleDateFormat("h:mm a", Locale("zh", "CN"))
    return dateFormat.format(Date(nextAlarm))
}
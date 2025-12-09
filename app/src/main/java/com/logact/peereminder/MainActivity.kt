package com.logact.peereminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = SharedPrefsManager.getInstance(this)
        alarmScheduler = AlarmScheduler(this)
        
        setContent {
            PeeReminderTheme {
                MainScreen(
                    onStartPauseClick = { isActive ->
                        if (isActive) {
                            startReminder()
                        } else {
                            pauseReminder()
                        }
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Permission check is now handled in Compose UI
    }
    
    private fun startReminder() {
        if (!PermissionHelper.hasExactAlarmPermission(this)) {
            // Permission check is handled in Compose UI
            return
        }
        
        prefsManager.isActive = true
        alarmScheduler.scheduleNextAlarm()
    }
    
    private fun pauseReminder() {
        prefsManager.isActive = false
        alarmScheduler.cancelAlarm()
    }
}

@Composable
fun MainScreen(
    onStartPauseClick: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = SharedPrefsManager.getInstance(context)
    val alarmScheduler = AlarmScheduler(context)
    
    var isActive by remember { mutableStateOf(prefsManager.isActive) }
    var timeRemaining by remember { mutableStateOf(calculateTimeRemaining(prefsManager, alarmScheduler)) }
    var nextAlarmTime by remember { mutableStateOf(formatNextAlarmTime(prefsManager, alarmScheduler)) }
    var showPermissionDialog by remember { mutableStateOf(!PermissionHelper.hasExactAlarmPermission(context)) }
    
    // Update time remaining every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isActive = prefsManager.isActive
            timeRemaining = calculateTimeRemaining(prefsManager, alarmScheduler)
            nextAlarmTime = formatNextAlarmTime(prefsManager, alarmScheduler)
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
            // Top: Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
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
            
            // Bottom: Start/Pause button
            Button(
                onClick = { onStartPauseClick(!isActive) },
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
    
    // Permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrightText
                )
            },
            text = {
                Text(
                    text = "This app needs permission to schedule exact alarms. Please grant this permission in Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrightText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
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
                    onClick = { showPermissionDialog = false }
                ) {
                    Text(
                        text = "Cancel",
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
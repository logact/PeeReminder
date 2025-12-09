package com.logact.peereminder

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.ui.theme.*
import com.logact.peereminder.utils.PermissionHelper

class SettingsActivity : ComponentActivity() {
    private lateinit var prefsManager: SharedPrefsManager
    
    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                prefsManager.customSoundUri = uri.toString()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = SharedPrefsManager.getInstance(this)
        
        setContent {
            PeeReminderTheme {
                SettingsScreen(
                    onBackClick = { finish() },
                    onSoundPickerClick = { openSoundPicker() }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Permission check is handled in Compose UI
    }
    
    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            
            val currentUri = prefsManager.customSoundUri?.let { Uri.parse(it) }
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
        }
        soundPickerLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onSoundPickerClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = SharedPrefsManager.getInstance(context)
    
    var intervalMinutes by remember { mutableStateOf(prefsManager.intervalMinutes) }
    var quietHoursEnabled by remember { mutableStateOf(prefsManager.quietHoursEnabled) }
    var quietHoursStart by remember { mutableStateOf(prefsManager.quietHoursStart) }
    var quietHoursEnd by remember { mutableStateOf(prefsManager.quietHoursEnd) }
    var alertType by remember { mutableStateOf(prefsManager.alertType) }
    var customSoundName by remember { mutableStateOf(getSoundName(context, prefsManager)) }
    
    LaunchedEffect(Unit) {
        // Update sound name when it changes
        customSoundName = getSoundName(context, prefsManager)
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.headlineMedium,
                            color = BrightText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGray
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Interval Picker
            SettingsSection(title = "Reminder Interval") {
                val intervals = listOf(60, 90, 120, 180) // 1h, 1.5h, 2h, 3h
                val intervalLabels = listOf("1 hour", "1.5 hours", "2 hours", "3 hours")
                
                intervals.forEachIndexed { index, minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = intervalMinutes == minutes,
                            onClick = {
                                intervalMinutes = minutes
                                prefsManager.intervalMinutes = minutes
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = BrightGreen
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = intervalLabels[index],
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrightText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            HorizontalDivider(color = DarkGray, thickness = 2.dp)
            
            // Quiet Hours
            SettingsSection(title = "Quiet Hours") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Disable reminders overnight",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrightText,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = quietHoursEnabled,
                        onCheckedChange = {
                            quietHoursEnabled = it
                            prefsManager.quietHoursEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BrightGreen,
                            checkedTrackColor = BrightGreen
                        )
                    )
                }
                
                if (quietHoursEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Start time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Start:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrightText,
                            modifier = Modifier.width(100.dp)
                        )
                        TimePicker(
                            hour = quietHoursStart,
                            onHourChange = {
                                quietHoursStart = it
                                prefsManager.quietHoursStart = it
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // End time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "End:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrightText,
                            modifier = Modifier.width(100.dp)
                        )
                        TimePicker(
                            hour = quietHoursEnd,
                            onHourChange = {
                                quietHoursEnd = it
                                prefsManager.quietHoursEnd = it
                            }
                        )
                    }
                }
            }
            
            HorizontalDivider(color = DarkGray, thickness = 2.dp)
            
            // Alert Type
            SettingsSection(title = "Alert Type") {
                val alertTypes = listOf("SOUND", "VIBRATION", "BOTH")
                val alertLabels = listOf("Sound", "Vibration", "Both")
                
                alertTypes.forEachIndexed { index, type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = alertType == type,
                            onClick = {
                                alertType = type
                                prefsManager.alertType = type
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = BrightGreen
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = alertLabels[index],
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrightText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            HorizontalDivider(color = DarkGray, thickness = 2.dp)
            
            // Alarm Sound
            SettingsSection(title = "Alarm Sound") {
                Button(
                    onClick = onSoundPickerClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGray
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = customSoundName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = BrightText
                        )
                        Text(
                            text = "Tap to change",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrightText.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Permission check section
            if (!PermissionHelper.hasExactAlarmPermission(context)) {
                HorizontalDivider(color = DarkGray, thickness = 2.dp)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = BrightRed.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrightRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This app needs permission to schedule exact alarms.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrightText
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                PermissionHelper.openAlarmSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightRed
                            )
                        ) {
                            Text(
                                text = "Grant Permission",
                                style = MaterialTheme.typography.bodyLarge,
                                color = BrightText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = BrightText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

@Composable
fun TimePicker(
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    val hours = (0..23).toList()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        hours.chunked(6).forEach { hourGroup ->
            Column {
                hourGroup.forEach { h ->
                    TextButton(
                        onClick = { onHourChange(h) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (hour == h) BrightGreen else BrightText
                        ),
                        modifier = Modifier.width(48.dp)
                    ) {
                        Text(
                            text = String.format("%02d:00", h),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (hour == h) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun getSoundName(context: android.content.Context, prefsManager: SharedPrefsManager): String {
    return try {
        val uri = prefsManager.customSoundUri?.let { Uri.parse(it) }
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Default System Sound"
        } else {
            "Default System Sound"
        }
    } catch (e: Exception) {
        "Default System Sound"
    }
}


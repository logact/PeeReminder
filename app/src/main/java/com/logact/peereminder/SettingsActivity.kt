package com.logact.peereminder

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.ui.theme.*
import com.logact.peereminder.utils.PermissionHelper
import com.logact.peereminder.R

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
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_alarm_sound))
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
    
    // Check if app is in debug mode
    val isTestMode = remember {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
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
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrightText,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(
                            text = stringResource(R.string.back_arrow),
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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Test Mode Indicator
            if (isTestMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = BrightYellow.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.test_mode),
                            style = MaterialTheme.typography.titleLarge,
                            color = BrightYellow,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.test_mode_intervals),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrightText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Interval Picker
            SettingsSection(title = stringResource(R.string.interval_setting)) {
                // Test mode: use seconds for rapid testing
                val intervals: List<Int>
                val intervalLabels: List<String>
                val defaultInterval: Int
                
                if (isTestMode) {
                    // Test mode: intervals in seconds
                    intervals = listOf(10, 30, 60, 90, 120) // 10s, 30s, 60s, 90s, 120s
                    intervalLabels = listOf(
                        stringResource(R.string.interval_10_seconds),
                        stringResource(R.string.interval_30_seconds),
                        stringResource(R.string.interval_60_seconds),
                        stringResource(R.string.interval_90_seconds),
                        stringResource(R.string.interval_120_seconds)
                    )
                    defaultInterval = 60 // Default to 60 seconds in test mode
                } else {
                    // Production mode: intervals in minutes
                    intervals = listOf(60, 90, 120, 180) // 1h, 1.5h, 2h, 3h
                    intervalLabels = listOf(
                        stringResource(R.string.interval_1_hour),
                        stringResource(R.string.interval_1_5_hours),
                        stringResource(R.string.interval_2_hours),
                        stringResource(R.string.interval_3_hours)
                    )
                    defaultInterval = 120 // Default to 2 hours in production
                }
                
                // Initialize with default if current value doesn't match any option
                if (intervalMinutes !in intervals) {
                    intervalMinutes = defaultInterval
                    prefsManager.intervalMinutes = defaultInterval
                }
                
                intervals.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = intervalMinutes == value,
                            onClick = {
                                intervalMinutes = value
                                prefsManager.intervalMinutes = value
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
            SettingsSection(title = stringResource(R.string.quiet_hours)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.quiet_hours_enabled),
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
                            text = stringResource(R.string.quiet_hours_start),
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
                            text = stringResource(R.string.quiet_hours_end),
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
            SettingsSection(title = stringResource(R.string.alert_type)) {
                val alertTypes = listOf("SOUND", "VIBRATION", "BOTH")
                val alertLabels = listOf(
                    stringResource(R.string.alert_sound),
                    stringResource(R.string.alert_vibration),
                    stringResource(R.string.alert_both)
                )
                
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
            SettingsSection(title = stringResource(R.string.alarm_sound)) {
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
                            text = stringResource(R.string.tap_to_change),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrightText.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Battery Optimization Section
            HorizontalDivider(color = DarkGray, thickness = 2.dp)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (!PermissionHelper.isBatteryOptimizationDisabled(context)) 
                        BrightYellow.copy(alpha = 0.2f) else BrightGreen.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.battery_optimization_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!PermissionHelper.isBatteryOptimizationDisabled(context)) 
                                    BrightYellow else BrightGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (!PermissionHelper.isBatteryOptimizationDisabled(context)) 
                                    stringResource(R.string.battery_optimization_enabled)
                                else stringResource(R.string.battery_optimization_disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrightText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!PermissionHelper.isBatteryOptimizationDisabled(context)) {
                        Button(
                            onClick = {
                                PermissionHelper.openBatteryOptimizationSettings(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightYellow
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.disable_battery_optimization),
                                style = MaterialTheme.typography.bodyLarge,
                                color = BrightText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        var showInstructions by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showInstructions = !showInstructions },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showInstructions) stringResource(R.string.hide_instructions) else stringResource(R.string.show_instructions),
                                color = BrightText
                            )
                        }
                        if (showInstructions) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BatteryOptimizationInstructions()
                        }
                    }
                }
            }
            
            // Overlay Permission Section (for full-screen when unlocked)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                var hasOverlayPermission by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
                
                LaunchedEffect(Unit) {
                    // Check permission status periodically
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
                    }
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasOverlayPermission) 
                            BrightGreen.copy(alpha = 0.2f) 
                        else 
                            BrightYellow.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.display_over_other_apps),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (hasOverlayPermission) BrightGreen else BrightYellow,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (hasOverlayPermission) 
                                        stringResource(R.string.display_over_other_apps_enabled)
                                    else 
                                        stringResource(R.string.display_over_other_apps_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrightText
                                )
                            }
                        }
                        
                        if (!hasOverlayPermission) {
                            Button(
                                onClick = {
                                    PermissionHelper.openOverlayPermissionSettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrightGreen
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.grant_permission),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = BrightText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Text(
                                text = stringResource(R.string.display_over_other_apps_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightText.copy(alpha = 0.8f)
                            )
                        }
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
                            text = stringResource(R.string.permission_required),
                            style = MaterialTheme.typography.titleMedium,
                            color = BrightRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.exact_alarm_permission_message),
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
                                text = stringResource(R.string.grant_permission),
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

@Composable
fun BatteryOptimizationInstructions() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.step_by_step_instructions),
            style = MaterialTheme.typography.titleSmall,
            color = BrightYellow,
            fontWeight = FontWeight.Bold
        )
        
        InstructionStep(
            number = 1,
            text = "Tap the 'Disable Battery Optimization' button above"
        )
        
        InstructionStep(
            number = 2,
            text = "You'll see a popup asking to 'Allow' or 'Don't allow'"
        )
        
        InstructionStep(
            number = 3,
            text = "Tap 'Allow' to disable battery optimization for this app"
        )
        
        InstructionStep(
            number = 4,
            text = "If you don't see a popup, you'll be taken to Settings"
        )
        
        InstructionStep(
            number = 5,
            text = "In Settings, find 'Pee Reminder' in the list and select it"
        )
        
        InstructionStep(
            number = 6,
            text = "Change from 'Optimize' to 'Don't optimize' or 'Not optimized'"
        )
        
        HorizontalDivider(color = DarkGray, thickness = 1.dp)
        
        Text(
            text = stringResource(R.string.for_specific_phone_brands),
            style = MaterialTheme.typography.titleSmall,
            color = BrightText,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "• Vivo/OriginOS 4 (REQUIRES MULTIPLE SETTINGS):",
            style = MaterialTheme.typography.bodySmall,
            color = BrightYellow,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "  1. Settings → Apps → Pee Reminder → Autostart → Enable",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        Text(
            text = "  2. Settings → Battery → Background power consumption → Don't restrict",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        Text(
            text = "  3. Settings → Battery → Battery Optimization → Don't Optimize",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        Text(
            text = "  4. Settings → Apps → Special app access → Autostart → Enable",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        Text(
            text = "  5. Settings → Apps → Special app access → Full screen intents → Enable",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        Text(
            text = "  6. Settings → Notifications & Status Bar → Pee Reminder → Allow all",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        
        Text(
            text = "• Samsung: Settings → Apps → Pee Reminder → Battery → Unrestricted",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        
        Text(
            text = "• Xiaomi/MIUI: Settings → Apps → Manage apps → Pee Reminder → Battery saver → No restrictions",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        
        Text(
            text = "• Huawei/EMUI: Settings → Apps → Apps → Pee Reminder → Battery → Launch → Manage manually → All enabled",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        
        Text(
            text = "• OnePlus/OxygenOS: Settings → Apps → Pee Reminder → Battery → Don't optimize",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
        
        Text(
            text = "• Stock Android: Settings → Apps → Pee Reminder → Battery → Unrestricted",
            style = MaterialTheme.typography.bodySmall,
            color = BrightText.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrightYellow,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BrightText,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getSoundName(context: android.content.Context, prefsManager: SharedPrefsManager): String {
    return try {
        val uri = prefsManager.customSoundUri?.let { Uri.parse(it) }
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: context.getString(R.string.default_sound)
        } else {
            context.getString(R.string.default_sound)
        }
    } catch (e: Exception) {
        context.getString(R.string.default_sound)
    }
}


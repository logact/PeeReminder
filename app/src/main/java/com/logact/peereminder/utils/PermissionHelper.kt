package com.logact.peereminder.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {
    /**
     * Check if exact alarm permission is granted (Android 12+)
     * For Android < 12, always returns true as permission is not required
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     * For Android < 13, always returns true as permission is not required
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Check if the app is battery optimized (Android 6.0+)
     * Returns true if NOT optimized (i.e., exempt from battery optimization)
     * Returns false if optimized (i.e., battery optimization is enabled)
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Battery optimization not available on older versions
        }
    }
    
    /**
     * Open the system settings page for alarm permissions
     */
    fun openAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }
    }
    
    /**
     * Open the system settings page for battery optimization
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery settings
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(fallbackIntent)
            }
        }
    }
    
    /**
     * Check if full-screen intent permission is available (Android 13+)
     * Note: On Android 13, this method may not be available, so we use try-catch
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.canUseFullScreenIntent()
            } catch (e: NoSuchMethodError) {
                // Method not available on this Android 13 device
                // Assume it's available (alarm apps usually get it automatically)
                true
            } catch (e: Exception) {
                // Other error - assume available
                true
            }
        } else {
            true // Pre-Android 13, assume available
        }
    }
    
    /**
     * Check if overlay permission (SYSTEM_ALERT_WINDOW) is granted (Android 6.0+)
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Open the system settings page for overlay permission
     */
    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app info page
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
    
    /**
     * Open the system settings page for full-screen intent permission (Android 14+)
     * On Android 13, this might not be available, but we try anyway
     */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app info page
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(fallbackIntent)
            }
        } else {
            // Android 13 - try to open app info where user can check special app access
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Last resort - open general app settings
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Get the device manufacturer name
     */
    private fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    /**
     * Open auto-start settings page (manufacturer-specific)
     * Auto-start permission is required on Chinese ROMs (MIUI, OriginOS, ColorOS, etc.)
     * to allow apps to run in background and receive broadcasts when killed
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = getManufacturer()
        val packageName = context.packageName
        
        try {
            when {
                // Vivo / OriginOS / FuntouchOS
                // Note: On OriginOS, alarms may work without explicit auto-start
                // if battery optimization is disabled and other permissions are set
                manufacturer.contains("vivo") -> {
                    // On OriginOS, auto-start might not be visible or needed
                    // Instead, focus on battery optimization and background settings
                    try {
                        // Try to open app info where user can check battery and background settings
                        openAppInfoSettings(context)
                        return
                    } catch (e: Exception) {
                        // Last resort
                        val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                        context.startActivity(intent)
                    }
                }
                
                // Xiaomi / MIUI / Redmi
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    try {
                        // Try MIUI's auto-start settings
                        val intent = Intent("miui.intent.action.OP_AUTO_START").apply {
                            setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                            putExtra("extra_pkgname", packageName)
                        }
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        // Fallback: Open app info, then user can find "Autostart" option
                        openAppInfoSettings(context)
                    }
                }
                
                // OPPO / ColorOS / OnePlus
                manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> {
                    try {
                        // Try OPPO's auto-start settings
                        val intent = Intent().apply {
                            setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                        }
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        // Fallback
                        openAppInfoSettings(context)
                    }
                }
                
                // Huawei / EMUI / HarmonyOS
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    try {
                        // Try Huawei's auto-start settings
                        val intent = Intent().apply {
                            setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                        }
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        // Fallback
                        openAppInfoSettings(context)
                    }
                }
                
                // Samsung
                manufacturer.contains("samsung") -> {
                    // Samsung doesn't have separate auto-start, but battery optimization matters
                    openBatteryOptimizationSettings(context)
                    return
                }
                
                // Default: Open app info page where user can find auto-start option
                else -> {
                    openAppInfoSettings(context)
                }
            }
        } catch (e: Exception) {
            // Last resort: Open app info
            openAppInfoSettings(context)
        }
    }
    
    /**
     * Open the app info settings page
     * This is a fallback when manufacturer-specific auto-start settings can't be opened
     */
    private fun openAppInfoSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Last resort - open general app settings
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            context.startActivity(intent)
        }
    }
    
    /**
     * Get instructions for enabling auto-start based on device manufacturer
     */
    fun getAutoStartInstructions(context: Context): String {
        val manufacturer = getManufacturer()
        
        return when {
            manufacturer.contains("vivo") -> {
                "For Vivo/OriginOS 4:\n" +
                "⚠️ IMPORTANT: OriginOS 4 may block alarms when app is swiped away!\n\n" +
                "Required settings (ALL must be enabled):\n" +
                "1. Settings → Apps → Pee Reminder → Battery → Don't optimize\n" +
                "2. Settings → Battery → Background power consumption → Don't restrict\n" +
                "3. Settings → Apps → Pee Reminder → Allow background activity\n" +
                "4. Settings → Apps → Special app access → Full screen intents → Enable\n" +
                "5. Settings → Notifications → Pee Reminder → Allow all\n\n" +
                "Note: Even with all settings, OriginOS 4 may still block alarms.\n" +
                "This is a ROM limitation, not an app bug.\n\n" +
                "Test: Schedule a test alarm, swipe away app, check if alarm triggers."
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                "For Xiaomi/MIUI:\n" +
                "1. Settings → Apps → Manage apps → Pee Reminder\n" +
                "2. Tap 'Autostart' → Enable\n" +
                "3. Settings → Battery saver → Pee Reminder → No restrictions"
            }
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> {
                "For OPPO/OnePlus/ColorOS:\n" +
                "1. Settings → Apps → App management → Pee Reminder\n" +
                "2. Tap 'Autostart' → Enable\n" +
                "3. Settings → Battery → Background power consumption → Allow"
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                "For Huawei/Honor:\n" +
                "1. Settings → Apps → Apps → Pee Reminder\n" +
                "2. Tap 'Launch' → Manage manually → Enable all\n" +
                "3. Settings → Battery → App launch → Pee Reminder → Manage manually"
            }
            manufacturer.contains("samsung") -> {
                "For Samsung:\n" +
                "1. Settings → Apps → Pee Reminder → Battery → Unrestricted\n" +
                "2. Settings → Apps → Pee Reminder → Allow background activity"
            }
            else -> {
                "For your device:\n" +
                "1. Settings → Apps → Pee Reminder\n" +
                "2. Look for 'Autostart', 'Startup', or 'Background activity' option\n" +
                "3. Enable it for Pee Reminder\n" +
                "4. Also disable battery optimization"
            }
        }
    }
}


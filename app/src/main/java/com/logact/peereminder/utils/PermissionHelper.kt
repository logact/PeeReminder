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
}


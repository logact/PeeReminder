package com.logact.peereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.logact.peereminder.data.SharedPrefsManager

/**
 * Utility class to verify alarms are properly scheduled and provide diagnostics
 * for troubleshooting alarm issues, especially when the app is killed.
 */
class AlarmVerifier(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefsManager: SharedPrefsManager = SharedPrefsManager.getInstance(context)
    
    companion object {
        private const val TAG = "AlarmVerifier"
        private const val ALARM_ACTION = "com.logact.peereminder.ALARM_TRIGGERED"
        private const val REQUEST_CODE = 1001
    }
    
    /**
     * Comprehensive alarm verification and diagnostics
     * Returns a detailed report of alarm status
     */
    fun verifyAlarmStatus(): AlarmStatus {
        val status = AlarmStatus()
        
        // Check if reminder is active
        status.isReminderActive = prefsManager.isActive
        Log.d(TAG, "Reminder active: ${status.isReminderActive}")
        
        // Check saved alarm timestamp
        val savedTimestamp = prefsManager.nextAlarmTimestamp
        status.savedAlarmTimestamp = savedTimestamp
        status.hasSavedTimestamp = savedTimestamp > 0
        
        if (status.hasSavedTimestamp) {
            val now = System.currentTimeMillis()
            status.isAlarmInFuture = savedTimestamp > now
            status.timeUntilAlarm = savedTimestamp - now
            
            Log.d(TAG, "Saved alarm timestamp: ${java.util.Date(savedTimestamp)}")
            Log.d(TAG, "Current time: ${java.util.Date(now)}")
            Log.d(TAG, "Alarm is in future: ${status.isAlarmInFuture}")
            if (status.isAlarmInFuture) {
                Log.d(TAG, "Time until alarm: ${status.timeUntilAlarm / 1000 / 60} minutes")
            }
        } else {
            Log.w(TAG, "No saved alarm timestamp found")
        }
        
        // Check if PendingIntent exists (indicates alarm might be scheduled)
        status.pendingIntentExists = checkPendingIntentExists()
        Log.d(TAG, "PendingIntent exists: ${status.pendingIntentExists}")
        
        // Check exact alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            status.hasExactAlarmPermission = alarmManager.canScheduleExactAlarms()
            Log.d(TAG, "Exact alarm permission: ${status.hasExactAlarmPermission}")
        } else {
            status.hasExactAlarmPermission = true // Not required on older versions
        }
        
        // Determine overall status
        status.isAlarmProperlyScheduled = determineIfAlarmIsScheduled(status)
        
        return status
    }
    
    /**
     * Check if the PendingIntent for the alarm exists
     * This is an indirect way to verify if an alarm might be scheduled
     */
    private fun checkPendingIntentExists(): Boolean {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        return pendingIntent != null
    }
    
    /**
     * Determine if alarm is properly scheduled based on all checks
     */
    private fun determineIfAlarmIsScheduled(status: AlarmStatus): Boolean {
        if (!status.isReminderActive) {
            return false // Reminder is not active, so alarm shouldn't be scheduled
        }
        
        if (!status.hasSavedTimestamp) {
            return false // No timestamp saved
        }
        
        if (!status.isAlarmInFuture) {
            return false // Alarm time has passed
        }
        
        if (!status.hasExactAlarmPermission) {
            return false // Missing required permission
        }
        
        // PendingIntent existence is a good indicator, but not 100% reliable
        // Some devices may not show it even if alarm is scheduled
        // So we consider it a warning, not a failure
        
        return true
    }
    
    /**
     * Get a human-readable diagnostic report
     */
    fun getDiagnosticReport(): String {
        val status = verifyAlarmStatus()
        val report = StringBuilder()
        
        report.appendLine("=== Alarm Diagnostic Report ===")
        report.appendLine("Reminder Active: ${status.isReminderActive}")
        report.appendLine("Exact Alarm Permission: ${status.hasExactAlarmPermission}")
        
        if (status.hasSavedTimestamp) {
            report.appendLine("Saved Alarm Time: ${java.util.Date(status.savedAlarmTimestamp)}")
            report.appendLine("Alarm is in Future: ${status.isAlarmInFuture}")
            if (status.isAlarmInFuture) {
                val minutes = status.timeUntilAlarm / 1000 / 60
                report.appendLine("Time Until Alarm: $minutes minutes")
            }
        } else {
            report.appendLine("No saved alarm timestamp")
        }
        
        report.appendLine("PendingIntent Exists: ${status.pendingIntentExists}")
        report.appendLine("Alarm Properly Scheduled: ${status.isAlarmProperlyScheduled}")
        
        // Add recommendations
        report.appendLine("\n=== Recommendations ===")
        if (!status.isReminderActive) {
            report.appendLine("⚠️ Reminder is not active - alarms will not trigger")
        }
        if (!status.hasExactAlarmPermission) {
            report.appendLine("⚠️ Exact alarm permission not granted - alarms may not work")
            report.appendLine("   Go to: Settings → Apps → Pee Reminder → Alarms & reminders")
        }
        if (!status.hasSavedTimestamp || !status.isAlarmInFuture) {
            report.appendLine("⚠️ No valid alarm scheduled - reschedule the alarm")
        }
        if (!status.pendingIntentExists && status.isReminderActive) {
            report.appendLine("⚠️ PendingIntent not found - alarm may need to be rescheduled")
        }
        
        return report.toString()
    }
    
    /**
     * Data class to hold alarm status information
     */
    data class AlarmStatus(
        var isReminderActive: Boolean = false,
        var hasSavedTimestamp: Boolean = false,
        var savedAlarmTimestamp: Long = 0L,
        var isAlarmInFuture: Boolean = false,
        var timeUntilAlarm: Long = 0L,
        var pendingIntentExists: Boolean = false,
        var hasExactAlarmPermission: Boolean = false,
        var isAlarmProperlyScheduled: Boolean = false
    )
}


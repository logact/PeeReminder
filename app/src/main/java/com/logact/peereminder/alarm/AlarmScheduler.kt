package com.logact.peereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.logact.peereminder.data.SharedPrefsManager

class AlarmScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefsManager: SharedPrefsManager = SharedPrefsManager.getInstance(context)
    
    // Check if app is in debug mode
    private val isTestMode: Boolean
        get() = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    
    companion object {
        private const val ALARM_ACTION = "com.logact.peereminder.ALARM_TRIGGERED"
        private const val REQUEST_CODE = 1001
        private const val TAG = "AlarmScheduler"
    }
    
    /**
     * Schedule an exact alarm at the specified time
     * Uses setExactAndAllowWhileIdle() for reliability even in Doze Mode
     */
    fun scheduleAlarm(targetTimeMillis: Long) {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        // Use setExactAndAllowWhileIdle for Android 6.0+ to bypass Doze Mode
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Alarm scheduled with setExactAndAllowWhileIdle for: ${java.util.Date(targetTimeMillis)}")
            } else {
                // For older versions, use setExact
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Alarm scheduled with setExact for: ${java.util.Date(targetTimeMillis)}")
            }
            
            // Save the scheduled time
            prefsManager.nextAlarmTimestamp = targetTimeMillis
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm - permission denied", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            throw e
        }
    }
    
    /**
     * Cancel the scheduled alarm
     */
    fun cancelAlarm() {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        // Clear the saved timestamp
        prefsManager.nextAlarmTimestamp = 0L
        Log.d(TAG, "Alarm cancelled")
    }
    
    /**
     * Get the next alarm time in milliseconds
     * Returns 0 if no alarm is scheduled
     */
    fun getNextAlarmTime(): Long {
        return prefsManager.nextAlarmTimestamp
    }
    
    /**
     * Calculate and schedule the next alarm based on current interval
     */
    fun scheduleNextAlarm() {
        val intervalValue = prefsManager.intervalMinutes
        // In test mode (DEBUG), intervalValue is in seconds; otherwise in minutes
        val targetTime = if (isTestMode) {
            // Test mode: interval is in seconds
            System.currentTimeMillis() + (intervalValue * 1000L)
        } else {
            // Production mode: interval is in minutes
            System.currentTimeMillis() + (intervalValue * 60 * 1000L)
        }
        scheduleAlarm(targetTime)
    }
    
    /**
     * Check if an alarm is currently scheduled
     */
    fun isAlarmScheduled(): Boolean {
        return prefsManager.nextAlarmTimestamp > System.currentTimeMillis()
    }
}


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
     * 
     * CRITICAL: This method ensures alarms work even when the app is killed/swiped away.
     * The alarm is stored at the system level and will fire even if the app process is not running.
     */
    fun scheduleAlarm(targetTimeMillis: Long) {
        Log.d(TAG, "=== SCHEDULING ALARM ===")
        Log.d(TAG, "Target time: ${java.util.Date(targetTimeMillis)}")
        Log.d(TAG, "Current time: ${java.util.Date(System.currentTimeMillis())}")
        Log.d(TAG, "Time until alarm: ${(targetTimeMillis - System.currentTimeMillis()) / 1000 / 60} minutes")
        
        // CRITICAL: Create intent with explicit class name
        // This ensures the receiver can be found even when the app is killed
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            // Explicitly set the component to ensure delivery
            setClass(context, AlarmReceiver::class.java)
            // Add package name for extra reliability
            setPackage(context.packageName)
            // Add flags to ensure intent is delivered
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        
        // CRITICAL: Use FLAG_UPDATE_CURRENT to ensure the PendingIntent persists
        // FLAG_IMMUTABLE is required for Android 12+ but doesn't prevent alarms from working
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
        
        Log.d(TAG, "PendingIntent created: ${pendingIntent != null}")
        if (pendingIntent == null) {
            Log.e(TAG, "❌ CRITICAL: Failed to create PendingIntent!")
            throw IllegalStateException("Failed to create PendingIntent for alarm")
        }
        
        // Use setExactAndAllowWhileIdle for Android 6.0+ to bypass Doze Mode
        // This is CRITICAL for alarms to work when app is killed
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "✅ Alarm scheduled with setExactAndAllowWhileIdle")
                Log.d(TAG, "This alarm will fire even if the app is killed/swiped away")
            } else {
                // For older versions, use setExact
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "✅ Alarm scheduled with setExact")
            }
            
            // Save the scheduled time
            prefsManager.nextAlarmTimestamp = targetTimeMillis
            Log.d(TAG, "Alarm timestamp saved to SharedPreferences")
            
            // Verify the alarm was actually set
            // Note: This is a best-effort check - AlarmManager doesn't provide a direct API
            val verification = verifyAlarmScheduled()
            if (verification) {
                Log.d(TAG, "✅ Alarm verification passed - alarm is scheduled")
            } else {
                Log.w(TAG, "⚠️ Alarm verification failed - alarm may not be scheduled")
                Log.w(TAG, "This can happen on some devices - alarm may still work")
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Failed to schedule alarm - permission denied", e)
            Log.e(TAG, "User needs to grant SCHEDULE_EXACT_ALARM permission")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule alarm", e)
            throw e
        }
        
        Log.d(TAG, "=== ALARM SCHEDULING COMPLETE ===")
        
        // Log critical information for OriginOS 4 debugging
        val isOriginOS4 = Build.MANUFACTURER.lowercase().contains("vivo") && 
                         Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (isOriginOS4) {
            Log.w(TAG, "⚠️ ORIGINOS 4 DETECTED - Alarm scheduling may be blocked when app is killed")
            Log.w(TAG, "To verify if alarm works:")
            Log.w(TAG, "1. Schedule a test alarm (1-2 minutes from now)")
            Log.w(TAG, "2. Swipe away the app")
            Log.w(TAG, "3. Check Logcat for 'AlarmReceiver: === ALARM RECEIVED ==='")
            Log.w(TAG, "4. If no log appears, OriginOS 4 is blocking the broadcast")
            Log.w(TAG, "5. Configure all required settings (see app instructions)")
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
    
    /**
     * Verify that the alarm is actually set in AlarmManager
     * This checks if the PendingIntent exists in the system
     * Note: This is a best-effort check - AlarmManager doesn't provide a direct API
     * to check if an alarm is set, so we verify by checking if the PendingIntent exists
     */
    fun verifyAlarmScheduled(): Boolean {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        
        // Try to get the PendingIntent without creating it
        // If it exists, the alarm is likely scheduled
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        val exists = pendingIntent != null
        if (exists) {
            Log.d(TAG, "Alarm verification: PendingIntent exists - alarm is likely scheduled")
        } else {
            Log.w(TAG, "Alarm verification: PendingIntent does not exist - alarm may not be scheduled")
        }
        
        return exists
    }
    
    /**
     * Reschedule alarm if it's missing or expired
     * This is useful when the app is restarted after being killed
     */
    fun ensureAlarmScheduled(): Boolean {
        val nextAlarmTime = prefsManager.nextAlarmTimestamp
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "=== Ensuring alarm is scheduled ===")
        Log.d(TAG, "Next alarm time: ${if (nextAlarmTime > 0) java.util.Date(nextAlarmTime) else "NOT SET"}")
        Log.d(TAG, "Current time: ${java.util.Date(now)}")
        Log.d(TAG, "Reminder active: ${prefsManager.isActive}")
        
        // Check if alarm is missing, expired, or not verified
        val isExpired = nextAlarmTime > 0 && nextAlarmTime <= now
        val isMissing = nextAlarmTime <= 0
        val notVerified = !verifyAlarmScheduled()
        
        if (isMissing || isExpired || notVerified) {
            Log.w(TAG, "Alarm issue detected:")
            if (isMissing) Log.w(TAG, "  - Alarm timestamp is missing")
            if (isExpired) Log.w(TAG, "  - Alarm time has passed")
            if (notVerified) Log.w(TAG, "  - Alarm verification failed")
            
            if (prefsManager.isActive) {
                Log.d(TAG, "Rescheduling alarm...")
                try {
                    scheduleNextAlarm()
                    Log.d(TAG, "✅ Alarm rescheduled successfully")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to reschedule alarm", e)
                    return false
                }
            } else {
                Log.d(TAG, "Reminder is not active, not rescheduling alarm")
                return false
            }
        } else {
            Log.d(TAG, "✅ Alarm is properly scheduled for: ${java.util.Date(nextAlarmTime)}")
            return true
        }
    }
    
    /**
     * Test alarm - schedule an alarm for 10 seconds from now
     * Useful for testing if alarms work when app is killed
     */
    fun scheduleTestAlarm(): Boolean {
        Log.d(TAG, "=== SCHEDULING TEST ALARM ===")
        val testTime = System.currentTimeMillis() + (10 * 1000L) // 10 seconds from now
        try {
            scheduleAlarm(testTime)
            Log.d(TAG, "✅ Test alarm scheduled for 10 seconds from now")
            Log.d(TAG, "Swipe away the app and wait 10 seconds to test")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule test alarm", e)
            return false
        }
    }
}


package com.logact.peereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.logact.peereminder.data.SharedPrefsManager
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefsManager: SharedPrefsManager = SharedPrefsManager.getInstance(context)
    
    companion object {
        private const val ALARM_ACTION = "com.logact.peereminder.ALARM_TRIGGERED"
        private const val DAILY_RESET_ACTION = "com.logact.peereminder.DAILY_RESET"
        private const val REQUEST_CODE = 1001
        private const val DAILY_RESET_REQUEST_CODE = 1002
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
        // Interval is in minutes
        val targetTime = System.currentTimeMillis() + (intervalValue * 60 * 1000L)
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
     * 
     * IMPORTANT: This method preserves existing alarm times to prevent countdown reset.
     * It only reschedules when the alarm is truly missing or expired.
     */
    fun ensureAlarmScheduled(): Boolean {
        val nextAlarmTime = prefsManager.nextAlarmTimestamp
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "=== Ensuring alarm is scheduled ===")
        Log.d(TAG, "Next alarm time: ${if (nextAlarmTime > 0) java.util.Date(nextAlarmTime) else "NOT SET"}")
        Log.d(TAG, "Current time: ${java.util.Date(now)}")
        Log.d(TAG, "Reminder active: ${prefsManager.isActive}")
        
        // Check if alarm is missing or expired
        val isExpired = nextAlarmTime > 0 && nextAlarmTime <= now
        val isMissing = nextAlarmTime <= 0
        val isValid = nextAlarmTime > now
        
        // Verification check for logging only (not used for decision-making)
        val notVerified = !verifyAlarmScheduled()
        if (notVerified) {
            Log.w(TAG, "Alarm verification check failed (this is informational only)")
        }
        
        if (!prefsManager.isActive) {
            Log.d(TAG, "Reminder is not active, skipping alarm scheduling")
            return false
        }
        
        when {
            isMissing || isExpired -> {
                // Alarm is missing or expired - reschedule with new time
                Log.w(TAG, "Alarm issue detected:")
                if (isMissing) Log.w(TAG, "  - Alarm timestamp is missing")
                if (isExpired) Log.w(TAG, "  - Alarm time has passed")
                
                Log.d(TAG, "Rescheduling alarm with new time...")
                try {
                    scheduleNextAlarm()
                    Log.d(TAG, "✅ Alarm rescheduled successfully")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to reschedule alarm", e)
                    return false
                }
            }
            isValid -> {
                // Alarm exists and is valid - re-register it to ensure it's set in AlarmManager
                // This preserves the countdown while ensuring the alarm is properly scheduled
                Log.d(TAG, "Alarm timestamp is valid - re-registering with AlarmManager to ensure it's set")
                try {
                    scheduleAlarm(nextAlarmTime)
                    Log.d(TAG, "✅ Alarm re-registered successfully (countdown preserved)")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to re-register alarm", e)
                    return false
                }
            }
            else -> {
                // Should not reach here, but handle gracefully
                Log.w(TAG, "Unexpected alarm state")
                return false
            }
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
    
    /**
     * Schedule the daily reset alarm at quietHoursEnd time
     * This will trigger a reset every day at the specified hour
     */
    fun scheduleDailyReset() {
        Log.d(TAG, "=== SCHEDULING DAILY RESET ===")
        
        val resetHour = prefsManager.quietHoursEnd
        Log.d(TAG, "Reset hour: $resetHour (quiet hours end)")
        
        // Calculate the next reset time (today or tomorrow at resetHour)
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        // Set to reset hour, minute 0, second 0
        calendar.set(Calendar.HOUR_OF_DAY, resetHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If reset time has already passed today, schedule for tomorrow
        if (currentHour > resetHour || (currentHour == resetHour && currentMinute >= 0)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            Log.d(TAG, "Reset time has passed today, scheduling for tomorrow")
        }
        
        val resetTimeMillis = calendar.timeInMillis
        Log.d(TAG, "Daily reset scheduled for: ${java.util.Date(resetTimeMillis)}")
        
        // Create intent for DailyResetReceiver
        val intent = Intent(context, DailyResetReceiver::class.java).apply {
            action = DAILY_RESET_ACTION
            setClass(context, DailyResetReceiver::class.java)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        if (pendingIntent == null) {
            Log.e(TAG, "❌ CRITICAL: Failed to create PendingIntent for daily reset!")
            return
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    resetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "✅ Daily reset scheduled with setExactAndAllowWhileIdle")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    resetTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "✅ Daily reset scheduled with setExact")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Failed to schedule daily reset - permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule daily reset", e)
        }
        
        Log.d(TAG, "=== DAILY RESET SCHEDULING COMPLETE ===")
    }
    
    /**
     * Cancel the daily reset alarm
     */
    fun cancelDailyReset() {
        Log.d(TAG, "=== CANCELLING DAILY RESET ===")
        
        val intent = Intent(DAILY_RESET_ACTION).apply {
            setClass(context, DailyResetReceiver::class.java)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_REQUEST_CODE,
            intent,
            pendingIntentFlags
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        Log.d(TAG, "Daily reset cancelled")
    }
    
    /**
     * Perform the daily reset: cancel current alarm and schedule new one from reset time
     */
    fun performDailyReset() {
        Log.d(TAG, "=== PERFORMING DAILY RESET ===")
        
        // Cancel the current reminder alarm if it exists
        if (prefsManager.isActive && prefsManager.nextAlarmTimestamp > 0) {
            Log.d(TAG, "Cancelling current reminder alarm")
            cancelAlarm()
        }
        
        // Only schedule if reminder is active
        if (!prefsManager.isActive) {
            Log.d(TAG, "Reminder is not active, skipping alarm scheduling")
            Log.d(TAG, "=== DAILY RESET PERFORMED ===")
            return
        }
        
        // Calculate reset time (today at quietHoursEnd)
        val resetHour = prefsManager.quietHoursEnd
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, resetHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val resetTimeMillis = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        // Calculate interval in milliseconds
        val intervalValue = prefsManager.intervalMinutes
        val intervalMillis = intervalValue * 60 * 1000L // Interval is in minutes
        
        // First alarm should be at reset time + interval
        // This ensures alarms are aligned to intervals from the reset time
        // Example: Reset at 7:00 AM, interval 2 hours -> alarms at 9:00 AM, 11:00 AM, 1:00 PM, etc.
        var firstAlarmTime = resetTimeMillis + intervalMillis
        
        // If reset time is in the past (reset fired slightly after the hour), 
        // align to the next interval from reset time
        if (resetTimeMillis < now) {
            val timeSinceReset = now - resetTimeMillis
            val intervalsPassed = (timeSinceReset / intervalMillis) + 1
            firstAlarmTime = resetTimeMillis + (intervalsPassed * intervalMillis)
            
            // Ensure first alarm is in the future
            if (firstAlarmTime <= now) {
                firstAlarmTime += intervalMillis
            }
            
            Log.d(TAG, "Reset time was in the past, aligned first alarm to next interval: ${java.util.Date(firstAlarmTime)}")
        }
        
        // Schedule first alarm of the day
        scheduleAlarm(firstAlarmTime)
        Log.d(TAG, "✅ First alarm of the day scheduled at: ${java.util.Date(firstAlarmTime)}")
        Log.d(TAG, "Reset time: ${java.util.Date(resetTimeMillis)}, Interval: $intervalValue minutes")
        
        Log.d(TAG, "=== DAILY RESET PERFORMED ===")
    }
}


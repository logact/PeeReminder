package com.logact.peereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.logact.peereminder.data.SharedPrefsManager

class AlarmScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefsManager: SharedPrefsManager = SharedPrefsManager.getInstance(context)
    
    companion object {
        private const val ALARM_ACTION = "com.logact.peereminder.ALARM_TRIGGERED"
        private const val REQUEST_CODE = 1001
    }
    
    /**
     * Schedule an exact alarm at the specified time
     * Uses setExactAndAllowWhileIdle() for reliability even in Doze Mode
     */
    fun scheduleAlarm(targetTimeMillis: Long) {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use setExactAndAllowWhileIdle for Android 6.0+ to bypass Doze Mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTimeMillis,
                pendingIntent
            )
        } else {
            // For older versions, use setExact
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                targetTimeMillis,
                pendingIntent
            )
        }
        
        // Save the scheduled time
        prefsManager.nextAlarmTimestamp = targetTimeMillis
    }
    
    /**
     * Cancel the scheduled alarm
     */
    fun cancelAlarm() {
        val intent = Intent(ALARM_ACTION).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        // Clear the saved timestamp
        prefsManager.nextAlarmTimestamp = 0L
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
        val intervalMinutes = prefsManager.intervalMinutes
        val targetTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)
        scheduleAlarm(targetTime)
    }
    
    /**
     * Check if an alarm is currently scheduled
     */
    fun isAlarmScheduled(): Boolean {
        return prefsManager.nextAlarmTimestamp > System.currentTimeMillis()
    }
}


package com.logact.peereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.logact.peereminder.data.SharedPrefsManager
import java.text.SimpleDateFormat
import java.util.*

class DailyResetReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DailyResetReceiver"
        private const val WAKELOCK_TAG = "PeeReminder::DailyResetWakeLock"
        const val DAILY_RESET_ACTION = "com.logact.peereminder.DAILY_RESET"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DAILY_RESET_ACTION) {
            return
        }
        
        Log.d(TAG, "=== DAILY RESET RECEIVED ===")
        
        // Acquire wake lock to ensure device stays awake
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKELOCK_TAG
        )
        
        // Acquire wake lock (will be released after reset completes)
        wakeLock.acquire(60 * 1000L) // Hold for up to 1 minute
        Log.d(TAG, "Wake lock acquired")
        
        try {
            val prefsManager = SharedPrefsManager.getInstance(context)
            val alarmScheduler = AlarmScheduler(context)
            
            // Get today's date in "yyyy-MM-dd" format
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            
            // Check if reset already occurred today
            val lastResetDate = prefsManager.lastResetDate
            if (lastResetDate == today) {
                Log.d(TAG, "Reset already occurred today ($today), skipping")
                wakeLock.release()
                return
            }
            
            Log.d(TAG, "Performing daily reset (last reset: $lastResetDate, today: $today)")
            
            // Perform the daily reset
            alarmScheduler.performDailyReset()
            
            // Update last reset date
            prefsManager.lastResetDate = today
            Log.d(TAG, "Daily reset completed, last reset date updated to: $today")
            
            // Reschedule the next daily reset alarm
            alarmScheduler.scheduleDailyReset()
            Log.d(TAG, "Next daily reset alarm scheduled")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during daily reset", e)
        } finally {
            wakeLock.release()
            Log.d(TAG, "Wake lock released")
        }
        
        Log.d(TAG, "=== DAILY RESET COMPLETE ===")
    }
}

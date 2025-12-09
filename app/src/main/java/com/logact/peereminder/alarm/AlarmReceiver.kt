package com.logact.peereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.logact.peereminder.ReminderActivity
import com.logact.peereminder.data.SharedPrefsManager
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.logact.peereminder.ALARM_TRIGGERED") {
            return
        }
        
        val prefsManager = SharedPrefsManager.getInstance(context)
        
        // Check if reminder is still active
        if (!prefsManager.isActive) {
            return
        }
        
        // Check quiet hours
        if (isQuietHours(prefsManager)) {
            // Silently reschedule for after quiet hours
            val alarmScheduler = AlarmScheduler(context)
            alarmScheduler.scheduleNextAlarm()
            return
        }
        
        // Launch the full-screen reminder activity
        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(reminderIntent)
    }
    
    /**
     * Check if current time is within quiet hours
     */
    private fun isQuietHours(prefsManager: SharedPrefsManager): Boolean {
        if (!prefsManager.quietHoursEnabled) {
            return false
        }
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val startHour = prefsManager.quietHoursStart
        val endHour = prefsManager.quietHoursEnd
        
        // Handle quiet hours that span midnight (e.g., 10 PM to 7 AM)
        return if (startHour > endHour) {
            // Quiet hours span midnight
            currentHour >= startHour || currentHour < endHour
        } else {
            // Quiet hours within same day
            currentHour >= startHour && currentHour < endHour
        }
    }
}


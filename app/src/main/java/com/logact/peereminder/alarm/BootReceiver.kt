package com.logact.peereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.logact.peereminder.data.SharedPrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefsManager = SharedPrefsManager.getInstance(context)
            val alarmScheduler = AlarmScheduler(context)
            
            // If reminder was active before reboot, reschedule the alarm
            if (prefsManager.isActive) {
                // Check if we have a saved next alarm time
                val nextAlarmTime = prefsManager.nextAlarmTimestamp
                
                if (nextAlarmTime > System.currentTimeMillis()) {
                    // Reschedule the existing alarm
                    alarmScheduler.scheduleAlarm(nextAlarmTime)
                } else {
                    // Schedule a new alarm based on the interval
                    alarmScheduler.scheduleNextAlarm()
                }
            }
            
            // Always schedule daily reset on boot (regardless of reminder state)
            alarmScheduler.scheduleDailyReset()
        }
    }
}


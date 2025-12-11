package com.logact.peereminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.logact.peereminder.ReminderActivity
import com.logact.peereminder.data.SharedPrefsManager
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKELOCK_TAG = "PeeReminder::AlarmWakeLock"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pee_reminder_alarm_channel"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.logact.peereminder.ALARM_TRIGGERED") {
            return
        }
        
        Log.d(TAG, "Alarm received - action: ${intent.action}")
        
        // Acquire wake lock to ensure device stays awake
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKELOCK_TAG
        )
        
        // Acquire wake lock (will be released when activity starts or after timeout)
        wakeLock.acquire(10 * 60 * 1000L) // Hold for up to 10 minutes
        
        try {
            val prefsManager = SharedPrefsManager.getInstance(context)
            
            // Check if reminder is still active
            if (!prefsManager.isActive) {
                Log.d(TAG, "Reminder is not active, ignoring alarm")
                wakeLock.release()
                return
            }
            
            // Check quiet hours
            if (isQuietHours(prefsManager)) {
                Log.d(TAG, "Within quiet hours, rescheduling alarm")
                // Silently reschedule for after quiet hours
                val alarmScheduler = AlarmScheduler(context)
                alarmScheduler.scheduleNextAlarm()
                wakeLock.release()
                return
            }
            
            Log.d(TAG, "Launching ReminderActivity")
            
            // Create notification channel for Android O+ (must be done before creating notification)
            createNotificationChannel(context)
            
            // Create intent for ReminderActivity with proper flags to show on lock screen
            // These flags ensure the activity can start from background and show over lock screen
            val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                // Add extra to indicate this is from alarm
                putExtra("from_alarm", true)
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or 
                PendingIntent.FLAG_IMMUTABLE or 
                PendingIntent.FLAG_ONE_SHOT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or 
                PendingIntent.FLAG_ONE_SHOT
            }
            
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                reminderIntent,
                pendingIntentFlags
            )
            
            // Create a high-priority notification with full-screen intent
            // This is the primary mechanism to show the activity on lock screen
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Time to Go!")
                .setContentText("Pee Reminder")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(null) // Sound will be handled by ReminderActivity
                .build()
            
            // Primary method: Start activity directly
            // Alarm receivers are allowed to start activities even on Android 10+
            // The wake lock ensures the device is awake, making this work reliably
            try {
                context.startActivity(reminderIntent)
                Log.d(TAG, "ReminderActivity started directly")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ReminderActivity directly: ${e.message}", e)
                // If direct start fails, the notification will handle it
            }
            
            // Secondary method: Show notification with full-screen intent as backup
            // This ensures the activity shows even if direct start fails or device is locked
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Notification with full-screen intent posted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post notification: ${e.message}", e)
                wakeLock.release()
            }
            
            // Note: We use both methods for maximum reliability:
            // 1. Direct startActivity() - works when device is awake (most reliable for alarms)
            // 2. Full-screen intent notification - works when device is locked/as backup
            // The wake lock (ACQUIRE_CAUSES_WAKEUP) ensures the device wakes up, so direct start should work
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in AlarmReceiver", e)
            wakeLock.release()
        }
        // Wake lock will be released when ReminderActivity starts or after timeout
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pee Reminder Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for pee reminders"
                enableLights(true)
                enableVibration(true)
                setShowBadge(false)
                // Allow full-screen intents and bypass DND
                setBypassDnd(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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


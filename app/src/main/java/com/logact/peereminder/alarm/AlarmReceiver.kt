package com.logact.peereminder.alarm

import android.app.ActivityOptions
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
            
            Log.d(TAG, "Launching ReminderActivity automatically (like system alarm)")
            
            // Create notification channel for Android O+ (must be done before creating notification)
            createNotificationChannel(context)
            
            // Create intent for ReminderActivity with proper flags to show on lock screen
            // These flags ensure the activity can start from background and show over lock screen
            // FLAG_ACTIVITY_NEW_TASK is required to start from BroadcastReceiver
            // FLAG_ACTIVITY_CLEAR_TOP ensures only one instance
            // FLAG_ACTIVITY_SINGLE_TOP prevents multiple instances
            // FLAG_ACTIVITY_REORDER_TO_FRONT brings existing activity to front if it exists
            val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
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
            
            // Create PendingIntent for full-screen intent
            // For Android 14+, we need to allow background activity start
            val fullScreenPendingIntent = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ requires explicit opt-in for background activity starts
                    val activityOptions = ActivityOptions.makeBasic()
                    activityOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        reminderIntent,
                        pendingIntentFlags,
                        activityOptions.toBundle()
                    )
                } else {
                    // Android 13 and below - standard PendingIntent
                    PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        reminderIntent,
                        pendingIntentFlags
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create PendingIntent: ${e.message}", e)
                // Fallback to standard PendingIntent
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    reminderIntent,
                    pendingIntentFlags
                )
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if full-screen intent is available (Android 13+)
            // Note: canUseFullScreenIntent() may not be available on all Android 13 devices
            // So we use try-catch to handle it safely
            var fullScreenIntentAvailable = true
            var canCheckPermission = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Try to check permission (may not be available on all Android 13 devices)
                    fullScreenIntentAvailable = notificationManager.canUseFullScreenIntent()
                    canCheckPermission = true
                    if (!fullScreenIntentAvailable) {
                        Log.e(TAG, "❌ CRITICAL: Full-screen intent permission NOT granted!")
                        Log.e(TAG, "This means the activity will NOT auto-launch - user must tap notification")
                        Log.e(TAG, "User needs to enable it in: Settings → Apps → Pee Reminder → Special App Access → Full Screen Intents")
                        Log.e(TAG, "OR: Settings → Apps → Special App Access → Full Screen Intents → Enable Pee Reminder")
                    } else {
                        Log.d(TAG, "✅ Full-screen intent permission is granted")
                    }
                }
            } catch (e: NoSuchMethodError) {
                // Method not available on this device/version (common on Android 13)
                Log.w(TAG, "⚠️ canUseFullScreenIntent() not available on this Android 13 device")
                Log.w(TAG, "Cannot verify permission - assuming it's granted (alarm apps usually get it automatically)")
                Log.w(TAG, "If full-screen intent doesn't work, check: Settings → Apps → Special App Access → Full Screen Intents")
                fullScreenIntentAvailable = true // Assume available
                canCheckPermission = false
            } catch (e: Exception) {
                Log.w(TAG, "Error checking full-screen intent permission: ${e.message}")
                fullScreenIntentAvailable = true // Assume available on error
                canCheckPermission = false
            }
            
            if (!fullScreenIntentAvailable && canCheckPermission) {
                Log.e(TAG, "❌ CRITICAL: Full-screen intent permission not available!")
                Log.e(TAG, "Full-screen intent will NOT work - activity will only launch when user taps notification")
            }
            
            // Verify channel importance before creating notification
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel != null) {
                Log.d(TAG, "Channel importance: ${channel.importance} (IMPORTANCE_MAX=${NotificationManager.IMPORTANCE_MAX})")
                if (channel.importance != NotificationManager.IMPORTANCE_MAX) {
                    Log.e(TAG, "CRITICAL: Channel importance is ${channel.importance}, not IMPORTANCE_MAX! Full-screen intent will NOT work!")
                }
            } else {
                Log.e(TAG, "CRITICAL: Notification channel is null!")
            }
            
            // PRIMARY METHOD: Full-screen intent notification
            // This automatically launches the activity when notification is posted
            // Works even when app is in background, device is locked, or sleeping
            // This is the same mechanism system alarm clock uses
            // On Android 13: Full-screen intent auto-launches when device is LOCKED (if permission granted)
            // On Android 13: Shows as notification when device is UNLOCKED (Android design)
            // 
            // CRITICAL: For full-screen intent to work, we need:
            // 1. Channel importance = IMPORTANCE_MAX (already set)
            // 2. Full-screen intent permission granted (may not be checkable on Android 13)
            // 3. Notification priority = PRIORITY_MAX (already set)
            // 4. setFullScreenIntent(pendingIntent, true) - the true is critical
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("⏰ Time to Go!")
                .setContentText("Pee Reminder - Tap to open")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MAX) // MAX priority - REQUIRED for full-screen intent
                .setCategory(NotificationCompat.CATEGORY_ALARM) // ALARM category - helps with full-screen intent
                .setFullScreenIntent(fullScreenPendingIntent, true) // true = show immediately when locked
                .setContentIntent(fullScreenPendingIntent) // Launches when tapped (unlocked or if FSI fails)
                .setAutoCancel(true)
                .setOngoing(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(null) // Sound will be handled by ReminderActivity
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("⏰ Time to Go! Tap to open the reminder.")
                    .setSummaryText("Pee Reminder Alarm"))
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build()
            
            // Verify the notification has all required properties
            Log.d(TAG, "Notification properties:")
            Log.d(TAG, "  Priority: ${notification.priority} (MAX=${NotificationCompat.PRIORITY_MAX})")
            Log.d(TAG, "  Category: ${notification.category}")
            Log.d(TAG, "  Full-screen intent: ${notification.fullScreenIntent != null}")
            Log.d(TAG, "  Content intent: ${notification.contentIntent != null}")
            
            // Verify full-screen intent is set
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasFullScreenIntent = notification.fullScreenIntent != null
                Log.d(TAG, "Notification full-screen intent set: $hasFullScreenIntent")
            }
            
            // On Android 13, we need to rely on full-screen intent notification
            // Direct startActivity from BroadcastReceiver may be blocked even for alarms
            // Full-screen intent is the most reliable method
            
            // Try direct startActivity first
            // On Android 13, even if startActivity() succeeds (no exception),
            // Android may silently block it from actually showing
            var activityStarted = false
            try {
                Log.d(TAG, "Attempting direct startActivity with flags: ${reminderIntent.flags}")
                context.startActivity(reminderIntent)
                Log.d(TAG, "✅ Direct startActivity() call succeeded - no exception thrown")
                activityStarted = true
                
                // On Android 13, even if no exception, the activity might not actually show
                // We'll verify in ReminderActivity.onCreate() if it was actually launched
                Log.d(TAG, "Note: On Android 13, startActivity() may succeed but activity might not show")
                Log.d(TAG, "Check ReminderActivity logs to see if onCreate() was called immediately")
                
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Direct startActivity BLOCKED by SecurityException: ${e.message}")
                Log.e(TAG, "This means Android 13 is blocking background activity start")
            } catch (e: android.content.ActivityNotFoundException) {
                Log.e(TAG, "❌ Activity not found: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Direct startActivity failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
            
            if (activityStarted) {
                Log.d(TAG, "✅ Direct startActivity() returned successfully")
                Log.d(TAG, "⚠️ However, on Android 13, activity may still not appear if app is in background")
            } else {
                Log.w(TAG, "⚠️ Direct startActivity failed - relying on full-screen intent notification")
            }
            
            // PRIMARY METHOD: Full-screen intent notification
            // On Android 13: Auto-launches when device is LOCKED (if permission granted)
            // On Android 13: Shows as notification when device is UNLOCKED (by design)
            // 
            // IMPORTANT: On some devices (especially Vivo, Xiaomi, etc.), full-screen intents
            // may not work even with correct setup due to manufacturer-specific restrictions
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Full-screen intent notification posted")
                
                // Log detailed information for debugging
                Log.d(TAG, "=== ALARM TRIGGER SUMMARY ===")
                Log.d(TAG, "Direct startActivity attempted: $activityStarted")
                Log.d(TAG, "Full-screen intent notification posted: true")
                Log.d(TAG, "Channel importance: IMPORTANCE_MAX (5)")
                Log.d(TAG, "Full-screen intent set: true")
                Log.d(TAG, "=== EXPECTED BEHAVIOR ===")
                if (fullScreenIntentAvailable || !canCheckPermission) {
                    Log.d(TAG, "Device LOCKED: Activity should auto-launch via full-screen intent")
                    Log.d(TAG, "Device UNLOCKED: Notification appears, user must tap to launch activity")
                } else {
                    Log.e(TAG, "⚠️ Full-screen intent permission NOT granted - activity will NOT auto-launch!")
                    Log.e(TAG, "User must tap notification to launch activity (even when device is locked)")
                }
                Log.d(TAG, "=== CHECK LOGS ===")
                Log.d(TAG, "Look for 'ReminderActivity onCreate called' - if missing, activity didn't launch")
                Log.d(TAG, "If onCreate appears immediately = direct start worked")
                Log.d(TAG, "If onCreate appears after delay = launched from notification tap")
                Log.d(TAG, "=== TROUBLESHOOTING ===")
                if (!fullScreenIntentAvailable && canCheckPermission) {
                    Log.e(TAG, "❌ Full-screen intent permission is DENIED")
                    Log.e(TAG, "Solution: Enable in Settings → Apps → Special App Access → Full Screen Intents")
                } else if (!canCheckPermission) {
                    Log.w(TAG, "⚠️ Cannot verify full-screen intent permission on this device")
                    Log.w(TAG, "If full-screen intent doesn't work, check:")
                    Log.w(TAG, "1. Settings → Apps → Special App Access → Full Screen Intents")
                    Log.w(TAG, "2. Settings → Apps → Pee Reminder → Notifications → Allow all notifications")
                    Log.w(TAG, "3. Settings → Do Not Disturb → Allow Pee Reminder")
                    Log.w(TAG, "4. Some devices (Vivo/Xiaomi) may have additional restrictions")
                }
                
                if (activityStarted) {
                    Log.d(TAG, "✅ Both methods attempted: direct start + full-screen intent notification")
                } else {
                    Log.w(TAG, "⚠️ Direct start failed - relying ONLY on full-screen intent notification")
                }
                
                // Additional check: Verify notification was actually posted
                try {
                    val activeNotifications = notificationManager.activeNotifications
                    val ourNotification = activeNotifications.find { it.id == NOTIFICATION_ID }
                    if (ourNotification != null) {
                        Log.d(TAG, "✅ Notification is active in system")
                        Log.d(TAG, "Notification flags: ${ourNotification.notification.flags}")
                        Log.d(TAG, "Notification priority: ${ourNotification.notification.priority}")
                    } else {
                        Log.w(TAG, "⚠️ Notification not found in active notifications - may have been suppressed by system")
                        Log.w(TAG, "This could mean:")
                        Log.w(TAG, "  - Full-screen intent permission denied")
                        Log.w(TAG, "  - Device-specific restrictions (Vivo/Xiaomi)")
                        Log.w(TAG, "  - Do Not Disturb mode blocking it")
                        Log.w(TAG, "  - Notification settings blocking it")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check active notifications: ${e.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post full-screen intent notification: ${e.message}", e)
                if (!activityStarted) {
                    Log.e(TAG, "CRITICAL: Both methods failed!")
                    wakeLock.release()
                }
            }
            
            // Note: Full-screen intent notification is the PRIMARY method because:
            // 1. It automatically launches activity when posted (no user interaction needed)
            // 2. Works even when app is in background (exempt from restrictions)
            // 3. Works when device is locked or sleeping
            // 4. This is exactly how system alarm clock works
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in AlarmReceiver", e)
            wakeLock.release()
        }
        // Wake lock will be released when ReminderActivity starts or after timeout
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if channel already exists
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            
            // IMPORTANCE_MAX is REQUIRED for full-screen intents to work automatically
            // IMPORTANCE_HIGH will only show as a regular notification (requires user click)
            val requiredImportance = NotificationManager.IMPORTANCE_MAX
            
            // If channel exists, check its importance
            if (existingChannel != null) {
                val currentImportance = existingChannel.importance
                Log.d(TAG, "Existing channel found with importance: $currentImportance (required: $requiredImportance)")
                
                // If channel has wrong importance, delete and recreate it
                // Note: Once created, channel importance cannot be changed, must delete and recreate
                if (currentImportance != requiredImportance) {
                    Log.w(TAG, "Channel has wrong importance ($currentImportance). Deleting to recreate with IMPORTANCE_MAX")
                    try {
                        notificationManager.deleteNotificationChannel(CHANNEL_ID)
                        Log.d(TAG, "Channel deleted successfully")
                        // Wait a moment for deletion to complete
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete channel: ${e.message}", e)
                    }
                } else {
                    Log.d(TAG, "Channel already has correct importance (IMPORTANCE_MAX)")
                    return // Channel is correct, no need to recreate
                }
            } else {
                Log.d(TAG, "No existing channel found, will create new one")
            }
            
            // Create channel with IMPORTANCE_MAX (REQUIRED for automatic full-screen intent)
            // IMPORTANCE_HIGH will NOT trigger full-screen intent automatically
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pee Reminder Alarms",
                NotificationManager.IMPORTANCE_MAX // MAX is REQUIRED for automatic full-screen intent
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
            
            try {
                notificationManager.createNotificationChannel(channel)
                val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (createdChannel != null) {
                    Log.d(TAG, "Notification channel created successfully with importance: ${createdChannel.importance}")
                    if (createdChannel.importance != NotificationManager.IMPORTANCE_MAX) {
                        Log.e(TAG, "WARNING: Channel was created but importance is ${createdChannel.importance}, not IMPORTANCE_MAX!")
                    }
                } else {
                    Log.e(TAG, "ERROR: Channel creation failed - channel is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel: ${e.message}", e)
            }
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


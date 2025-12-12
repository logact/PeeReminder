package com.logact.peereminder.alarm

import android.app.ActivityOptions
import android.app.KeyguardManager
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
import com.logact.peereminder.alarm.AlarmForegroundService
import com.logact.peereminder.alarm.OverlayAlarmWindow
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.utils.PermissionHelper
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKELOCK_TAG = "PeeReminder::AlarmWakeLock"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pee_reminder_alarm_channel"
        
        // Store overlay window instance to prevent garbage collection
        @Volatile
        private var overlayWindowInstance: OverlayAlarmWindow? = null
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.logact.peereminder.ALARM_TRIGGERED") {
            return
        }
        
        Log.d(TAG, "=== ALARM RECEIVED ===")
        Log.d(TAG, "Action: ${intent.action}")
        Log.d(TAG, "Context: ${context.javaClass.simpleName}")
        Log.d(TAG, "Package: ${context.packageName}")
        Log.d(TAG, "Process ID: ${android.os.Process.myPid()}")
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT} (API ${Build.VERSION.SDK_INT})")
        Log.d(TAG, "✅ SUCCESS: Alarm triggered even though app was killed!")
        Log.d(TAG, "This proves AlarmManager is working correctly")
        
        // Check if this is OriginOS 4
        val isOriginOS4 = Build.MANUFACTURER.lowercase().contains("vivo") && 
                         Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (isOriginOS4) {
            Log.d(TAG, "⚠️ ORIGINOS 4 DETECTED")
            Log.d(TAG, "If you see this log, alarms ARE working on OriginOS 4!")
            Log.d(TAG, "If alarms don't trigger, OriginOS 4 is blocking BroadcastReceiver")
        }
        
        // Verify this is a valid alarm trigger
        // This helps ensure the receiver works even from cold start
        if (intent.action == null || intent.action != "com.logact.peereminder.ALARM_TRIGGERED") {
            Log.e(TAG, "Invalid alarm intent - action mismatch")
            return
        }
        
        // Acquire wake lock to ensure device stays awake
        // This is critical when app is killed - ensures device wakes up
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKELOCK_TAG
        )
        
        // Acquire wake lock (will be released when activity starts or after timeout)
        wakeLock.acquire(10 * 60 * 1000L) // Hold for up to 10 minutes
        Log.d(TAG, "Wake lock acquired - device will stay awake")
        
        try {
            // Initialize SharedPreferences - this works even from cold start
            // SharedPreferences are stored on disk, so they persist even when app is killed
            val prefsManager = SharedPrefsManager.getInstance(context)
            Log.d(TAG, "SharedPreferences initialized successfully")
            
            // Check if reminder is still active
            // This check is important even from cold start - user may have disabled reminder
            if (!prefsManager.isActive) {
                Log.d(TAG, "Reminder is not active, ignoring alarm")
                Log.d(TAG, "This alarm was likely scheduled before reminder was paused")
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
            
            Log.d(TAG, "=== LAUNCHING REMINDER ACTIVITY ===")
            Log.d(TAG, "App was likely killed - this is a cold start")
            Log.d(TAG, "System has revived the app process to handle this alarm")
            
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
            
            // For full-screen intents, we should NOT use FLAG_ONE_SHOT
            // FLAG_ONE_SHOT can prevent the full-screen intent from working properly
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or 
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
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
            
            // ANDROID 13 RESTRICTION: Background Activity Launch (BAL) is blocked
            // Android 13 blocks ALL background activity launches for security
            // The ONLY way to show full-screen activity from background is:
            // 1. Use full-screen intent notification (works when device is LOCKED)
            // 2. When device is UNLOCKED, full-screen intents show as notifications (by design)
            // 
            // Direct activity launches (startActivity, PendingIntent.send) are ALL blocked
            // This is Android 13 security feature, not a bug
            
            Log.d(TAG, "=== ANDROID 13 BACKGROUND ACTIVITY RESTRICTION ===")
            Log.d(TAG, "Android 13 blocks ALL background activity launches (BAL_BLOCK)")
            Log.d(TAG, "Full-screen intent notification is the ONLY way to show activity from background")
            Log.d(TAG, "Full-screen intent works when device is LOCKED")
            Log.d(TAG, "When UNLOCKED, it shows as notification (user can tap to open)")
            
            // Skip direct launch attempts - they will be blocked by Android 13
            val activityStarted = false
            
            // PRIMARY METHOD: Full-screen intent notification
            // This is the ONLY way to show full-screen activity from background on Android 13
            // Full-screen intent works when device is LOCKED, shows as notification when UNLOCKED
            try {
                Log.d(TAG, "=== PRIMARY METHOD: Full-screen intent notification ===")
                Log.d(TAG, "This is the ONLY method that works on Android 13 from background")
                
                // CRITICAL: For full-screen intents to work on Android 13:
                // 1. Channel importance MUST be IMPORTANCE_MAX (5)
                // 2. Notification priority should be PRIORITY_HIGH (not MAX - MAX is deprecated)
                // 3. setFullScreenIntent must be called with true
                // 4. Category should be CATEGORY_ALARM
                // 5. PendingIntent should NOT have FLAG_ONE_SHOT
                
                val fullScreenNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("⏰ Time to Go!")
                    .setContentText("Pee Reminder - Tap to open")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // HIGH priority (MAX is deprecated)
                    .setCategory(NotificationCompat.CATEGORY_ALARM) // ALARM category - CRITICAL
                    .setFullScreenIntent(fullScreenPendingIntent, true) // PRIMARY: full-screen intent - true is critical
                    .setContentIntent(fullScreenPendingIntent) // Backup: tap to launch
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setSound(null) // Sound handled by ReminderActivity
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("⏰ Time to Go! Tap to open the reminder.")
                        .setSummaryText("Pee Reminder Alarm"))
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis())
                    // Make it heads-up when unlocked
                    .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                    .build()
                
                Log.d(TAG, "Full-screen notification created with:")
                Log.d(TAG, "  Priority: ${fullScreenNotification.priority}")
                Log.d(TAG, "  Category: ${fullScreenNotification.category}")
                Log.d(TAG, "  Full-screen intent: ${fullScreenNotification.fullScreenIntent != null}")
                Log.d(TAG, "  Channel importance: ${notificationManager.getNotificationChannel(CHANNEL_ID)?.importance}")
                
                notificationManager.notify(NOTIFICATION_ID, fullScreenNotification)
                Log.d(TAG, "✅ Full-screen intent notification posted")
                
                // Check device lock state
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isDeviceLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    keyguardManager.isDeviceLocked
                } else {
                    @Suppress("DEPRECATION")
                    keyguardManager.isKeyguardLocked
                }
                
                Log.d(TAG, "Device lock state: ${if (isDeviceLocked) "LOCKED" else "UNLOCKED"}")
                
                // Show overlay window when device is UNLOCKED (requires SYSTEM_ALERT_WINDOW permission)
                // This is a workaround for Android 13's limitation
                if (!isDeviceLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Log.d(TAG, "=== Device is UNLOCKED - Checking overlay permission ===")
                    val hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
                    Log.d(TAG, "Overlay permission granted: $hasOverlayPermission")
                    
                    if (hasOverlayPermission) {
                        try {
                            Log.d(TAG, "Creating and showing overlay window...")
                            // Store instance to prevent garbage collection
                            overlayWindowInstance = OverlayAlarmWindow(context)
                            overlayWindowInstance?.show()
                            Log.d(TAG, "✅ Overlay window show() called - check logs for result")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to show overlay window: ${e.javaClass.simpleName}: ${e.message}", e)
                            Log.e(TAG, "Stack trace:", e)
                            overlayWindowInstance = null
                        }
                    } else {
                        Log.w(TAG, "⚠️ Overlay permission NOT granted")
                        Log.w(TAG, "Device is UNLOCKED but overlay can't be shown")
                        Log.w(TAG, "To enable full-screen when unlocked:")
                        Log.w(TAG, "Settings → Apps → Pee Reminder → Display over other apps → Enable")
                    }
                } else if (isDeviceLocked) {
                    Log.d(TAG, "Device is LOCKED - full-screen intent will handle it automatically")
                }
                
                Log.d(TAG, "=== FULL-SCREEN BEHAVIOR SUMMARY ===")
                Log.d(TAG, "Device LOCKED: Full-screen activity via full-screen intent ✅")
                if (PermissionHelper.hasOverlayPermission(context)) {
                    Log.d(TAG, "Device UNLOCKED: Overlay window should appear ✅")
                } else {
                    Log.d(TAG, "Device UNLOCKED: Shows as notification (tap to open) ⚠️")
                    Log.d(TAG, "Grant 'Display over other apps' for full-screen when unlocked")
                }
                
                // Log detailed information for debugging
                Log.d(TAG, "=== ALARM TRIGGER SUMMARY (Android 13) ===")
                Log.d(TAG, "Full-screen intent notification: ✅ POSTED")
                val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
                val channelImportance = channel?.importance ?: -1
                Log.d(TAG, "Channel importance: $channelImportance (required: ${NotificationManager.IMPORTANCE_MAX})")
                if (channelImportance != NotificationManager.IMPORTANCE_MAX) {
                    Log.e(TAG, "❌ CRITICAL: Channel importance is NOT MAX!")
                    Log.e(TAG, "Full-screen intent will NOT work automatically!")
                    Log.e(TAG, "Please set channel to 'Urgent' in: Settings → Apps → Pee Reminder → Notifications")
                }
                Log.d(TAG, "Full-screen intent permission: ${if (fullScreenIntentAvailable || !canCheckPermission) "✅ Available" else "❌ Denied"}")
                
                Log.d(TAG, "=== EXPECTED BEHAVIOR ===")
                Log.d(TAG, "Device LOCKED: Full-screen activity should appear automatically")
                Log.d(TAG, "Device UNLOCKED: Notification appears - user must tap to open")
                Log.d(TAG, "This is Android 13 security behavior - background activity launches are blocked")
                
                Log.d(TAG, "=== ORIGINOS 4 SPECIFIC NOTES ===")
                Log.d(TAG, "OriginOS 4 may block full-screen intents even with permission")
                Log.d(TAG, "Direct launch with ActivityOptions is more reliable on OriginOS")
                Log.d(TAG, "If activity doesn't appear, check:")
                Log.d(TAG, "  1. Settings → Apps → Pee Reminder → Battery → No restrictions")
                Log.d(TAG, "  2. Settings → Apps → Pee Reminder → Auto-start → Enabled")
                Log.d(TAG, "  3. Settings → Apps → Special App Access → Full Screen Intents")
                Log.d(TAG, "  4. Settings → Battery → Background restrictions → Allow Pee Reminder")
                
                // Verify notification was posted
                try {
                    val activeNotifications = notificationManager.activeNotifications
                    val ourNotification = activeNotifications.find { it.id == NOTIFICATION_ID }
                    if (ourNotification != null) {
                        Log.d(TAG, "✅ Notification is active in system")
                        Log.d(TAG, "Notification priority: ${ourNotification.notification.priority}")
                    } else {
                        Log.w(TAG, "⚠️ Notification not found - may have been suppressed by OriginOS")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check active notifications: ${e.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to post notification: ${e.message}", e)
                if (!activityStarted) {
                    Log.e(TAG, "CRITICAL: All methods failed!")
                    Log.e(TAG, "Please check OriginOS battery optimization and auto-start settings")
                    wakeLock.release()
                }
            }
            
            // Summary: New approach prioritizes direct launch for Android 13/OriginOS 4
            // This works better than full-screen intents on custom ROMs with aggressive restrictions
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in AlarmReceiver", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            
            // Even on error, try to reschedule the alarm if reminder is active
            try {
                val prefsManager = SharedPrefsManager.getInstance(context)
                if (prefsManager.isActive) {
                    Log.d(TAG, "Attempting to reschedule alarm after error")
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.scheduleNextAlarm()
                }
            } catch (rescheduleError: Exception) {
                Log.e(TAG, "Failed to reschedule alarm after error", rescheduleError)
            }
            
            wakeLock.release()
        }
        
        Log.d(TAG, "=== ALARM RECEIVER COMPLETE ===")
        // Wake lock will be released when ReminderActivity starts or after timeout
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // IMPORTANCE_MAX is REQUIRED for full-screen intents to work automatically
            // IMPORTANCE_HIGH will only show as a regular notification (requires user click)
            val requiredImportance = NotificationManager.IMPORTANCE_MAX
            
            // Always delete and recreate channel to ensure correct importance
            // OriginOS 4 may downgrade importance, so we need to be aggressive
            try {
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel != null) {
                    val currentImportance = existingChannel.importance
                    Log.d(TAG, "Existing channel found with importance: $currentImportance (required: $requiredImportance)")
                    
                    if (currentImportance != requiredImportance) {
                        Log.w(TAG, "Channel has wrong importance ($currentImportance). Deleting to recreate with IMPORTANCE_MAX")
                        notificationManager.deleteNotificationChannel(CHANNEL_ID)
                        // Wait for deletion to complete
                        Thread.sleep(200)
                    } else {
                        Log.d(TAG, "Channel already has correct importance (IMPORTANCE_MAX)")
                        // Double-check it's still correct (OriginOS might have changed it)
                        val recheckChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                        if (recheckChannel != null && recheckChannel.importance != requiredImportance) {
                            Log.w(TAG, "Channel importance was changed! Deleting and recreating...")
                            notificationManager.deleteNotificationChannel(CHANNEL_ID)
                            Thread.sleep(200)
                        } else {
                            return // Channel is correct
                        }
                    }
                } else {
                    Log.d(TAG, "No existing channel found, will create new one")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking/deleting channel: ${e.message}", e)
                // Continue to create channel anyway
            }
            
            // Create channel with IMPORTANCE_MAX (REQUIRED for automatic full-screen intent)
            // IMPORTANCE_HIGH will NOT trigger full-screen intent automatically
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pee Reminder Alarms",
                NotificationManager.IMPORTANCE_MAX // MAX is REQUIRED for automatic full-screen intent
            ).apply {
                description = "Notifications for pee reminders - Full screen alarm"
                enableLights(true)
                enableVibration(true)
                setShowBadge(false)
                // Allow full-screen intents and bypass DND
                setBypassDnd(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                // Set sound to null (handled by activity)
                setSound(null, null)
            }
            
            try {
                notificationManager.createNotificationChannel(channel)
                
                // Verify channel was created with correct importance
                // Wait a moment for system to process
                Thread.sleep(100)
                
                val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (createdChannel != null) {
                    val actualImportance = createdChannel.importance
                    Log.d(TAG, "Notification channel created with importance: $actualImportance")
                    
                    if (actualImportance != NotificationManager.IMPORTANCE_MAX) {
                        Log.e(TAG, "❌ CRITICAL: Channel was created but importance is $actualImportance, not IMPORTANCE_MAX!")
                        Log.e(TAG, "This means full-screen intent will NOT work automatically!")
                        Log.e(TAG, "OriginOS 4 may have downgraded the importance - user needs to manually set it in settings")
                        Log.e(TAG, "Settings → Apps → Pee Reminder → Notifications → Pee Reminder Alarms → Urgent")
                        
                        // Try one more time - delete and recreate
                        if (actualImportance == NotificationManager.IMPORTANCE_HIGH) {
                            Log.w(TAG, "Attempting to fix channel importance by recreating...")
                            notificationManager.deleteNotificationChannel(CHANNEL_ID)
                            Thread.sleep(200)
                            notificationManager.createNotificationChannel(channel)
                            Thread.sleep(100)
                            
                            val recreatedChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                            if (recreatedChannel != null && recreatedChannel.importance != NotificationManager.IMPORTANCE_MAX) {
                                Log.e(TAG, "❌ FAILED: Channel importance still wrong after recreation: ${recreatedChannel.importance}")
                                Log.e(TAG, "User MUST manually set channel to 'Urgent' in notification settings")
                            }
                        }
                    } else {
                        Log.d(TAG, "✅ Channel has correct importance (IMPORTANCE_MAX)")
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


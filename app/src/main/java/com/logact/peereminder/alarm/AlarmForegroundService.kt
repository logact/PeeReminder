package com.logact.peereminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.logact.peereminder.ReminderActivity

/**
 * Foreground service to launch ReminderActivity reliably on Android 13+ (especially OriginOS 4)
 * 
 * Foreground services have higher priority and can start activities more reliably
 * than BroadcastReceivers on custom ROMs with aggressive battery optimization.
 */
class AlarmForegroundService : Service() {
    companion object {
        private const val TAG = "AlarmForegroundService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "alarm_foreground_service_channel"
        
        private const val ACTION_START_ALARM = "com.logact.peereminder.START_ALARM"
        
        fun start(context: Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).apply {
                action = ACTION_START_ALARM
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        if (intent?.action == ACTION_START_ALARM) {
            // Create a minimal notification to keep service in foreground
            val notification = createForegroundNotification()
            
            // Start foreground service with proper type for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            // Launch the activity from foreground service with a slight delay
            // Sometimes helps with timing issues on aggressive ROMs like OriginOS
            Handler(Looper.getMainLooper()).postDelayed({
                launchReminderActivity()
                // Stop the service after launching activity (with delay to ensure activity starts)
                Handler(Looper.getMainLooper()).postDelayed({
                    stopSelf()
                }, 1000)
            }, 100)
        }
        
        return START_NOT_STICKY
    }
    
    private fun launchReminderActivity() {
        Log.d(TAG, "=== Launching ReminderActivity from foreground service ===")
        Log.d(TAG, "Service context: $this")
        Log.d(TAG, "ReminderActivity class: ${ReminderActivity::class.java.name}")
        Log.d(TAG, "Package name: ${packageName}")
        
        val reminderIntent = Intent(this, ReminderActivity::class.java).apply {
            // More aggressive flags for OriginOS 4
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("from_alarm", true)
            putExtra("from_foreground_service", true)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use ActivityOptions for Android 13+
                val activityOptions = android.app.ActivityOptions.makeBasic()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    activityOptions.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                
                activityOptions.setLaunchDisplayId(0)
                
                Log.d(TAG, "Starting activity with ActivityOptions from foreground service...")
                Log.d(TAG, "Intent flags: ${reminderIntent.flags}")
                
                // Try multiple times with slight delays (OriginOS 4 workaround)
                var attempts = 0
                val maxAttempts = 3
                
                fun tryLaunch() {
                    attempts++
                    try {
                        Log.d(TAG, "Attempt $attempts: Calling startActivity()...")
                        Log.d(TAG, "Intent component: ${reminderIntent.component}")
                        Log.d(TAG, "Intent flags: ${reminderIntent.flags}")
                        Log.d(TAG, "ActivityOptions bundle: ${activityOptions.toBundle()}")
                        
                        startActivity(reminderIntent, activityOptions.toBundle())
                        Log.d(TAG, "✅ Activity launch attempt $attempts: startActivity() returned (no exception)")
                        Log.d(TAG, "⚠️ IMPORTANT: If ReminderActivity.onCreate() is NOT called, OriginOS 4 is blocking it silently")
                        Log.d(TAG, "Check logs for 'REMINDER ACTIVITY CREATED' to verify activity actually started")
                        
                        // Wait and check if activity actually started
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "=== VERIFICATION: Checking if ReminderActivity was created ===")
                            Log.d(TAG, "If you see 'REMINDER ACTIVITY CREATED' in logs = SUCCESS")
                            Log.d(TAG, "If NO 'REMINDER ACTIVITY CREATED' = OriginOS 4 blocked it silently")
                            Log.d(TAG, "In that case, the notification's full-screen intent is the only option")
                        }, 1000)
                        
                    } catch (e: SecurityException) {
                        Log.e(TAG, "❌ SecurityException on attempt $attempts: ${e.message}", e)
                        Log.e(TAG, "Stack trace:", e)
                        if (attempts < maxAttempts) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                tryLaunch()
                            }, 200)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception on attempt $attempts: ${e.javaClass.simpleName}: ${e.message}", e)
                        Log.e(TAG, "Stack trace:", e)
                        if (attempts < maxAttempts) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                tryLaunch()
                            }, 200)
                        }
                    }
                }
                
                tryLaunch()
            } else {
                Log.d(TAG, "Starting activity from foreground service (standard method)...")
                startActivity(reminderIntent)
                Log.d(TAG, "✅ Activity launched from foreground service")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException launching activity: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch activity: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("from_alarm", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pee Reminder")
            .setContentText("Launching reminder...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority since it's just for service
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for alarm foreground service"
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

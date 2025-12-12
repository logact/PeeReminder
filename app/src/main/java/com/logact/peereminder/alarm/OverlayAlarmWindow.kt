package com.logact.peereminder.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.logact.peereminder.ReminderActivity

/**
 * Overlay window to show full-screen alarm when device is unlocked
 * Uses SYSTEM_ALERT_WINDOW permission to draw over other apps
 */
class OverlayAlarmWindow(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    companion object {
        private const val TAG = "OverlayAlarmWindow"
    }
    
    fun show() {
        Log.d(TAG, "=== Attempting to show overlay window ===")
        
        if (!canDrawOverlays()) {
            Log.e(TAG, "❌ SYSTEM_ALERT_WINDOW permission not granted")
            Log.e(TAG, "Cannot show overlay window without this permission")
            return
        }
        
        Log.d(TAG, "✅ Overlay permission granted")
        
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (windowManager == null) {
                Log.e(TAG, "❌ WindowManager is null")
                return
            }
            
            Log.d(TAG, "WindowManager obtained")
            
            // Create overlay view
            overlayView = createOverlayView()
            Log.d(TAG, "Overlay view created")
            
            // Set up window parameters
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                // CRITICAL: Remove FLAG_NOT_FOCUSABLE so user can interact with buttons
                // Add FLAG_WATCH_OUTSIDE_TOUCH to detect touches outside
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                gravity = Gravity.CENTER
                // Make it appear above everything
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "✅ Overlay window added to WindowManager")
            Log.d(TAG, "Window params: type=${params.type}, flags=${params.flags}, width=${params.width}, height=${params.height}")
            
            // Verify it was added
            if (overlayView?.parent != null) {
                Log.d(TAG, "✅ Overlay window is attached to parent - should be visible")
            } else {
                Log.e(TAG, "❌ Overlay window is NOT attached - may have been rejected")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: ${e.message}", e)
            Log.e(TAG, "This usually means SYSTEM_ALERT_WINDOW permission was revoked or not properly granted")
        } catch (e: android.view.WindowManager.BadTokenException) {
            Log.e(TAG, "❌ BadTokenException: ${e.message}", e)
            Log.e(TAG, "This might mean the context is invalid or the window type is not allowed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay window: ${e.javaClass.simpleName}: ${e.message}", e)
            Log.e(TAG, "Stack trace:", e)
        }
    }
    
    fun dismiss() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "Overlay window dismissed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss overlay window: ${e.message}", e)
        }
    }
    
    private fun createOverlayView(): View {
        // Create a simple full-screen view programmatically
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(48, 48, 48, 48)
        }
        
        // Large emoji/icon
        val iconText = TextView(context).apply {
            text = "🚽"
            textSize = 80f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }
        
        // Main message
        val messageText = TextView(context).apply {
            text = "Time to Go!"
            textSize = 36f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }
        
        // Dismiss button
        val dismissButton = Button(context).apply {
            text = "I HEARD IT"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            setPadding(64, 32, 64, 32)
            minHeight = 120
            minWidth = 400
            
            setOnClickListener {
                Log.d(TAG, "I HEARD IT button clicked - dismissing alarm")
                
                // Cancel the notification first to prevent it from launching ReminderActivity
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(1001) // NOTIFICATION_ID from AlarmReceiver
                Log.d(TAG, "Notification cancelled")
                
                // Dismiss the overlay window
                dismiss()
                Log.d(TAG, "Overlay dismissed")
                
                // Reschedule next alarm (same logic as ReminderActivity.dismissAlarm())
                try {
                    val prefsManager = com.logact.peereminder.data.SharedPrefsManager.getInstance(context)
                    if (prefsManager.isActive) {
                        val alarmScheduler = com.logact.peereminder.alarm.AlarmScheduler(context)
                        alarmScheduler.scheduleNextAlarm()
                        Log.d(TAG, "Next alarm scheduled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarm: ${e.message}", e)
                }
                
                // Don't launch ReminderActivity - overlay already served as the reminder
                // User has acknowledged it by clicking the button
            }
        }
        
        rootLayout.addView(iconText)
        rootLayout.addView(messageText)
        rootLayout.addView(dismissButton)
        
        return rootLayout
    }
    
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}

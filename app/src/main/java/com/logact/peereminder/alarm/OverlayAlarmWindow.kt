package com.logact.peereminder.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.logact.peereminder.ReminderActivity
import com.logact.peereminder.R

/**
 * Overlay window to show full-screen alarm when device is unlocked
 * Uses SYSTEM_ALERT_WINDOW permission to draw over other apps
 */
class OverlayAlarmWindow(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val prefsManager = com.logact.peereminder.data.SharedPrefsManager.getInstance(context)
    
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
            
            // Start alarm sound and vibration
            startAlarm()
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
            // Stop sound and vibration
            stopAlarm()
            
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "Overlay window dismissed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss overlay window: ${e.message}", e)
        }
    }
    
    private fun startAlarm() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val alertType = prefsManager.alertType
        Log.d(TAG, "=== START ALARM ===")
        Log.d(TAG, "Alert type: '$alertType'")
        
        if (audioManager != null) {
            Log.d(TAG, "Alarm stream volume: ${audioManager?.getStreamVolume(AudioManager.STREAM_ALARM)}")
            Log.d(TAG, "Alarm stream max volume: ${audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM)}")
            Log.d(TAG, "Ringer mode: ${audioManager?.ringerMode} (0=SILENT, 1=VIBRATE, 2=NORMAL)")
        }
        
        // Play sound if needed
        if (alertType == "SOUND" || alertType == "BOTH") {
            Log.d(TAG, "Should play sound - calling playSound()")
            playSound()
        } else {
            Log.d(TAG, "Sound not requested (alertType='$alertType')")
        }
        
        // Vibrate if needed
        if (alertType == "VIBRATION" || alertType == "BOTH") {
            Log.d(TAG, "Should vibrate - calling startVibration()")
            startVibration()
        } else {
            Log.d(TAG, "Vibration not requested (alertType='$alertType')")
        }
    }
    
    private fun stopAlarm() {
        // Stop sound
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        // Release audio focus
        releaseAudioFocus()
        
        // Stop vibration
        vibrator?.cancel()
        vibrator = null
    }
    
    private fun playSound() {
        Log.d(TAG, "=== PLAY SOUND ===")
        try {
            // Request audio focus for alarm playback
            Log.d(TAG, "Requesting audio focus...")
            requestAudioFocus()
            
            val customSoundUri = prefsManager.customSoundUri
            val soundUri = customSoundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
            Log.d(TAG, "Custom sound URI: $customSoundUri")
            Log.d(TAG, "Using sound URI: $soundUri")
            
            Log.d(TAG, "Creating MediaPlayer...")
            mediaPlayer = MediaPlayer.create(context, soundUri)
            
            if (mediaPlayer == null) {
                Log.e(TAG, "ERROR: MediaPlayer.create() returned null!")
                Log.e(TAG, "Trying fallback to default alarm sound...")
                
                // Fallback to default alarm sound
                try {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    Log.d(TAG, "Fallback: Using default alarm URI: $defaultUri")
                    mediaPlayer = MediaPlayer.create(context, defaultUri)
                    
                    if (mediaPlayer == null) {
                        Log.e(TAG, "ERROR: Fallback MediaPlayer.create() also returned null!")
                        return
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "ERROR in fallback: ${e2.message}", e2)
                    return
                }
            }
            
            Log.d(TAG, "MediaPlayer created successfully")
            mediaPlayer?.apply {
                // Set audio stream type to ALARM for proper volume control
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.d(TAG, "Setting AudioAttributes (API >= 21)")
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    Log.d(TAG, "Setting audio stream type (API < 21)")
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                isLooping = true
                setVolume(1.0f, 1.0f)
                
                Log.d(TAG, "Starting MediaPlayer...")
                Log.d(TAG, "MediaPlayer state before start: isPlaying=${isPlaying}")
                start()
                Log.d(TAG, "MediaPlayer started! isPlaying=${isPlaying}")
                
                if (!isPlaying) {
                    Log.e(TAG, "WARNING: MediaPlayer.start() called but isPlaying is still false!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in playSound(): ${e.message}", e)
            e.printStackTrace()
            // Fallback to default alarm sound
            try {
                Log.d(TAG, "Exception caught, trying fallback...")
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                Log.d(TAG, "Fallback: Using default alarm URI: $defaultUri")
                mediaPlayer = MediaPlayer.create(context, defaultUri)
                
                if (mediaPlayer == null) {
                    Log.e(TAG, "ERROR: Fallback MediaPlayer.create() returned null!")
                    return
                }
                
                mediaPlayer?.apply {
                    // Set audio stream type to ALARM for proper volume control
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_ALARM)
                    }
                    
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    Log.d(TAG, "Starting fallback MediaPlayer...")
                    start()
                    Log.d(TAG, "Fallback MediaPlayer started! isPlaying=${isPlaying}")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "ERROR in fallback: ${e2.message}", e2)
                e2.printStackTrace()
            }
        }
    }
    
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500), // Pattern: wait, vibrate, wait, vibrate
                    0 // Repeat from index 0
                )
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in startVibration(): ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    private fun requestAudioFocus() {
        if (audioManager == null) {
            Log.e(TAG, "ERROR: AudioManager is null!")
            return
        }
        
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Requesting audio focus (API >= 26)")
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                
                audioFocusRequest?.let { request ->
                    val result = am.requestAudioFocus(request)
                    Log.d(TAG, "Audio focus request result: $result (1=GRANTED, 0=FAILED, -1=DELAYED)")
                    if (result != 1) { // 1 = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        Log.w(TAG, "WARNING: Audio focus not granted! Result: $result")
                    }
                }
            } else {
                Log.d(TAG, "Requesting audio focus (API < 26)")
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d(TAG, "Audio focus request result: $result (1=GRANTED, 0=FAILED)")
                if (result != 1) { // 1 = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    Log.w(TAG, "WARNING: Audio focus not granted! Result: $result")
                }
            }
        }
    }
    
    private fun releaseAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    am.abandonAudioFocusRequest(request)
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
        audioFocusRequest = null
    }
    
    private fun createOverlayView(): View {
        // Create a simple full-screen view programmatically
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.WHITE)
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
            text = context.getString(R.string.time_to_go)
            textSize = 36f
            setTextColor(android.graphics.Color.parseColor("#212121")) // Dark text for light background
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }
        
        // Dismiss button
        val dismissButton = Button(context).apply {
            text = context.getString(R.string.i_heard_it)
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
                
                // Reschedule next alarm (same logic as ReminderActivity.dismissAlarm())
                try {
                    if (prefsManager.isActive) {
                        val alarmScheduler = com.logact.peereminder.alarm.AlarmScheduler(context)
                        alarmScheduler.scheduleNextAlarm()
                        Log.d(TAG, "Next alarm scheduled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarm: ${e.message}", e)
                }
                
                // Dismiss the overlay window (this will stop sound/vibration)
                dismiss()
                Log.d(TAG, "Overlay dismissed")
                
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

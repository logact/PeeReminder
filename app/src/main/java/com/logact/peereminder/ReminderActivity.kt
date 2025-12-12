package com.logact.peereminder

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.logact.peereminder.alarm.AlarmScheduler
import com.logact.peereminder.data.SharedPrefsManager
import com.logact.peereminder.R

class ReminderActivity : AppCompatActivity() {
    private lateinit var prefsManager: SharedPrefsManager
    private lateinit var alarmScheduler: AlarmScheduler
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d("ReminderActivity", "=== REMINDER ACTIVITY CREATED ===")
        android.util.Log.d("ReminderActivity", "onCreate called at: ${System.currentTimeMillis()}")
        android.util.Log.d("ReminderActivity", "Intent extras: ${intent.extras?.keySet()}")
        android.util.Log.d("ReminderActivity", "Intent flags: ${intent.flags}")
        android.util.Log.d("ReminderActivity", "From alarm: ${intent.getBooleanExtra("from_alarm", false)}")
        
        // Log how we were launched
        val action = intent.action
        android.util.Log.d("ReminderActivity", "Intent action: $action")
        android.util.Log.d("ReminderActivity", "Intent data: ${intent.data}")
        
        // Check if launched from notification (will have different intent structure)
        val launchedFromNotification = action != null || intent.data != null
        android.util.Log.d("ReminderActivity", "Likely launched from notification: $launchedFromNotification")
        
        // Configure to show over lock screen and wake device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // Window flags to wake device and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        // For Android 10+, unlock the device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        prefsManager = SharedPrefsManager.getInstance(this)
        alarmScheduler = AlarmScheduler(this)
        
        // Dismiss the notification since we're showing the activity
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1001)
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismissAlarm()
            }
        })
        
        setupUI()
        startAlarm()
    }
    
    private fun setupUI() {
        // Create UI programmatically for simplicity and reliability
        val rootLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(48, 48, 48, 48)
        }
        
        // Large emoji/icon
        val iconText = TextView(this).apply {
            text = "🚽"
            textSize = 80f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }
        
        // Main message
        val messageText = TextView(this).apply {
            text = getString(R.string.time_to_go)
            textSize = 36f // Large font
            setTextColor(android.graphics.Color.parseColor("#212121")) // Dark text for light background
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }
        
        // Dismiss button
        val dismissButton = Button(this).apply {
            text = getString(R.string.i_heard_it)
            textSize = 24f // Large font
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Green
            setPadding(64, 32, 64, 32)
            minHeight = 120 // Large touch target
            minWidth = 400
            
            setOnClickListener {
                dismissAlarm()
            }
        }
        
        rootLayout.addView(iconText)
        rootLayout.addView(messageText)
        rootLayout.addView(dismissButton)
        
        setContentView(rootLayout)
    }
    
    private fun startAlarm() {
        val alertType = prefsManager.alertType
        
        // Play sound if needed
        if (alertType == "SOUND" || alertType == "BOTH") {
            playSound()
        }
        
        // Vibrate if needed
        if (alertType == "VIBRATION" || alertType == "BOTH") {
            startVibration()
        }
    }
    
    private fun playSound() {
        try {
            val soundUri = prefsManager.customSoundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
            mediaPlayer = MediaPlayer.create(this, soundUri)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to default alarm sound
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer.create(this, defaultUri)
                mediaPlayer?.apply {
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    start()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun dismissAlarm() {
        // Stop sound
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        // Stop vibration
        vibrator?.cancel()
        vibrator = null
        
        // Reschedule next alarm
        if (prefsManager.isActive) {
            alarmScheduler.scheduleNextAlarm()
        }
        
        // Close activity
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }
}


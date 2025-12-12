package com.logact.peereminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        android.util.Log.d("ReminderActivity", "AudioManager initialized: ${audioManager != null}")
        if (audioManager != null) {
            android.util.Log.d("ReminderActivity", "Alarm stream volume: ${audioManager?.getStreamVolume(AudioManager.STREAM_ALARM)}")
            android.util.Log.d("ReminderActivity", "Alarm stream max volume: ${audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM)}")
            android.util.Log.d("ReminderActivity", "Ringer mode: ${audioManager?.ringerMode} (0=SILENT, 1=VIBRATE, 2=NORMAL)")
        }
        
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
        android.util.Log.d("ReminderActivity", "=== START ALARM ===")
        android.util.Log.d("ReminderActivity", "Alert type: '$alertType'")
        
        // Play sound if needed
        if (alertType == "SOUND" || alertType == "BOTH") {
            android.util.Log.d("ReminderActivity", "Should play sound - calling playSound()")
            playSound()
        } else {
            android.util.Log.d("ReminderActivity", "Sound not requested (alertType='$alertType')")
        }
        
        // Vibrate if needed
        if (alertType == "VIBRATION" || alertType == "BOTH") {
            android.util.Log.d("ReminderActivity", "Should vibrate - calling startVibration()")
            startVibration()
        } else {
            android.util.Log.d("ReminderActivity", "Vibration not requested (alertType='$alertType')")
        }
    }
    
    private fun playSound() {
        android.util.Log.d("ReminderActivity", "=== PLAY SOUND ===")
        try {
            // Request audio focus for alarm playback
            android.util.Log.d("ReminderActivity", "Requesting audio focus...")
            requestAudioFocus()
            
            val customSoundUri = prefsManager.customSoundUri
            val soundUri = customSoundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
            android.util.Log.d("ReminderActivity", "Custom sound URI: $customSoundUri")
            android.util.Log.d("ReminderActivity", "Using sound URI: $soundUri")
            
            android.util.Log.d("ReminderActivity", "Creating MediaPlayer...")
            mediaPlayer = MediaPlayer.create(this, soundUri)
            
            if (mediaPlayer == null) {
                android.util.Log.e("ReminderActivity", "ERROR: MediaPlayer.create() returned null!")
                android.util.Log.e("ReminderActivity", "Trying fallback to default alarm sound...")
                
                // Fallback to default alarm sound
                try {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    android.util.Log.d("ReminderActivity", "Fallback: Using default alarm URI: $defaultUri")
                    mediaPlayer = MediaPlayer.create(this, defaultUri)
                    
                    if (mediaPlayer == null) {
                        android.util.Log.e("ReminderActivity", "ERROR: Fallback MediaPlayer.create() also returned null!")
                        return
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("ReminderActivity", "ERROR in fallback: ${e2.message}", e2)
                    return
                }
            }
            
            android.util.Log.d("ReminderActivity", "MediaPlayer created successfully")
            mediaPlayer?.apply {
                // Set audio stream type to ALARM for proper volume control
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    android.util.Log.d("ReminderActivity", "Setting AudioAttributes (API >= 21)")
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    android.util.Log.d("ReminderActivity", "Setting audio stream type (API < 21)")
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                isLooping = true
                setVolume(1.0f, 1.0f)
                
                android.util.Log.d("ReminderActivity", "Starting MediaPlayer...")
                android.util.Log.d("ReminderActivity", "MediaPlayer state before start: isPlaying=${isPlaying}")
                start()
                android.util.Log.d("ReminderActivity", "MediaPlayer started! isPlaying=${isPlaying}")
                
                if (!isPlaying) {
                    android.util.Log.e("ReminderActivity", "WARNING: MediaPlayer.start() called but isPlaying is still false!")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReminderActivity", "ERROR in playSound(): ${e.message}", e)
            e.printStackTrace()
            // Fallback to default alarm sound
            try {
                android.util.Log.d("ReminderActivity", "Exception caught, trying fallback...")
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                android.util.Log.d("ReminderActivity", "Fallback: Using default alarm URI: $defaultUri")
                mediaPlayer = MediaPlayer.create(this, defaultUri)
                
                if (mediaPlayer == null) {
                    android.util.Log.e("ReminderActivity", "ERROR: Fallback MediaPlayer.create() returned null!")
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
                    android.util.Log.d("ReminderActivity", "Starting fallback MediaPlayer...")
                    start()
                    android.util.Log.d("ReminderActivity", "Fallback MediaPlayer started! isPlaying=${isPlaying}")
                }
            } catch (e2: Exception) {
                android.util.Log.e("ReminderActivity", "ERROR in fallback: ${e2.message}", e2)
                e2.printStackTrace()
            }
        }
    }
    
    private fun requestAudioFocus() {
        if (audioManager == null) {
            android.util.Log.e("ReminderActivity", "ERROR: AudioManager is null!")
            return
        }
        
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("ReminderActivity", "Requesting audio focus (API >= 26)")
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
                    android.util.Log.d("ReminderActivity", "Audio focus request result: $result (1=GRANTED, 0=FAILED, -1=DELAYED)")
                    if (result != 1) { // 1 = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        android.util.Log.w("ReminderActivity", "WARNING: Audio focus not granted! Result: $result")
                    }
                }
            } else {
                android.util.Log.d("ReminderActivity", "Requesting audio focus (API < 26)")
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                android.util.Log.d("ReminderActivity", "Audio focus request result: $result (1=GRANTED, 0=FAILED)")
                if (result != 1) { // 1 = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    android.util.Log.w("ReminderActivity", "WARNING: Audio focus not granted! Result: $result")
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
        
        // Release audio focus
        releaseAudioFocus()
        
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
        releaseAudioFocus()
        vibrator?.cancel()
    }
}


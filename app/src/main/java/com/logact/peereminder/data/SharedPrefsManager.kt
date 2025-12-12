package com.logact.peereminder.data

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "PeeReminderPrefs"
        
        // Keys
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_NEXT_ALARM_TIMESTAMP = "next_alarm_timestamp"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_ALERT_TYPE = "alert_type"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        
        // Default values
        // Note: In test mode (DEBUG), this stores seconds; in production, it stores minutes
        private const val DEFAULT_INTERVAL_MINUTES = 120 // 2 hours (production) or 120 seconds (test mode default, but will be overridden to 10 in SettingsActivity)
        private const val DEFAULT_QUIET_HOURS_START = 22 // 10 PM
        private const val DEFAULT_QUIET_HOURS_END = 7 // 7 AM
        private const val DEFAULT_ALERT_TYPE = "BOTH" // Sound + Vibration
        
        @Volatile
        private var INSTANCE: SharedPrefsManager? = null
        
        fun getInstance(context: Context): SharedPrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedPrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Interval (in minutes)
    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_MINUTES, value).apply()
    
    // Check if interval has been explicitly set (not using default)
    fun hasIntervalBeenSet(): Boolean {
        return prefs.contains(KEY_INTERVAL_MINUTES)
    }
    
    // Next alarm timestamp
    var nextAlarmTimestamp: Long
        get() = prefs.getLong(KEY_NEXT_ALARM_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_ALARM_TIMESTAMP, value).apply()
    
    // Active state
    var isActive: Boolean
        get() = prefs.getBoolean(KEY_IS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ACTIVE, value).apply()
    
    // Quiet hours start (hour of day, 0-23)
    var quietHoursStart: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_START, DEFAULT_QUIET_HOURS_START)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_START, value).apply()
    
    // Quiet hours end (hour of day, 0-23)
    var quietHoursEnd: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_END, DEFAULT_QUIET_HOURS_END)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_END, value).apply()
    
    // Quiet hours enabled
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()
    
    // Alert type: "SOUND", "VIBRATION", or "BOTH"
    var alertType: String
        get() = prefs.getString(KEY_ALERT_TYPE, DEFAULT_ALERT_TYPE) ?: DEFAULT_ALERT_TYPE
        set(value) = prefs.edit().putString(KEY_ALERT_TYPE, value).apply()
    
    // Custom sound URI (as string)
    var customSoundUri: String?
        get() = prefs.getString(KEY_CUSTOM_SOUND_URI, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_SOUND_URI, value).apply()
}


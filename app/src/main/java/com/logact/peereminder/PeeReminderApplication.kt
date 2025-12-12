package com.logact.peereminder

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

class PeeReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setLocaleToChinese(this)
    }
    
    override fun attachBaseContext(base: Context) {
        val locale = Locale("zh", "CN")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            super.attachBaseContext(base.createConfigurationContext(config))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(config, base.resources.displayMetrics)
            super.attachBaseContext(base)
        }
    }
    
    private fun setLocaleToChinese(context: Context) {
        val locale = Locale("zh", "CN")
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}

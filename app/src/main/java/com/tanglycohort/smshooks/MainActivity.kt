package com.tanglycohort.smshooks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncBackgroundMode()
    }

    private fun syncBackgroundMode() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean(BACKGROUND_MODE_KEY, false)) {
            SmsHooksForegroundService.start(this)
        } else {
            SmsHooksForegroundService.stop(this)
        }
    }

    companion object {
        const val BACKGROUND_MODE_KEY = "persistentBackgroundMode"
    }
}

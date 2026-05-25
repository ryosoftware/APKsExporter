package com.ryosoftware.apks_exporter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContent {
            AppTheme(this) {
                PreferencesScreen(onNavigateBack = { finish() })
            }
        }
    }
}

package com.ryosoftware.apks_exporter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

class AppDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: return finish()
        setContent {
            AppTheme(this) {
                AppDetailScreen(packageName = packageName, onNavigateBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}

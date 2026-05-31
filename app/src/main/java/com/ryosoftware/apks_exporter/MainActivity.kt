package com.ryosoftware.apks_exporter

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            AppTheme(this@MainActivity) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        startActivity(Intent(this@MainActivity, PreferencesActivity::class.java))
                    }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.isSelecting) finish()
                else viewModel.cancelSelection()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
        MainBackupWorker.onAppExecuted(this)
    }

    companion object {
    }
}

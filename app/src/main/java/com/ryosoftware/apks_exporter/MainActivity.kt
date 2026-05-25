package com.ryosoftware.apks_exporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

private const val ACTION_SAVE_TASKS_FINISHED = BuildConfig.APPLICATION_ID + ".SAVE_TASKS_FINISHED"

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private val iReceiver = MainActivityBroadcastReceiver()

    private inner class MainActivityBroadcastReceiver : BroadcastReceiver() {
        private var registered = false

        fun enable(context: Context) {
            if (!registered) {
                val packageFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
                context.registerReceiver(this, packageFilter, Context.RECEIVER_EXPORTED)

                val backupFilter = IntentFilter(MainService.ACTION_AUTO_BACKUP_APPS_DONE)
                context.registerReceiver(this, backupFilter, Context.RECEIVER_EXPORTED)

                val saveFilter = IntentFilter(ACTION_SAVE_TASKS_FINISHED)
                context.registerReceiver(this, saveFilter, Context.RECEIVER_EXPORTED)
                registered = true
            }
        }

        fun disable(context: Context) {
            if (registered) {
                context.unregisterReceiver(this)
                registered = false
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                intent.data?.schemeSpecificPart?.let { packageName ->
                    ApplicationPreferences.removePackagePreferences(packageName)
                }
            }
            viewModel.loadData()
        }
    }

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

        MainService.onAppExecuted(this)
    }

    override fun onResume() {
        super.onResume()
        iReceiver.enable(this)
        viewModel.loadData()
    }

    override fun onPause() {
        iReceiver.disable(this)
        super.onPause()
    }

    companion object {
    }
}

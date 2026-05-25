package com.ryosoftware.apks_exporter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ryosoftware.utilities.LogUtilities
import java.io.File

class Main : Application(), Thread.UncaughtExceptionHandler {

    override fun onCreate() {
        super.onCreate()
        iInstance = this
        LogUtilities.tag = TAG
        ApplicationPreferences.initialize()
        LogUtilities.show(this, "Application started (current app version is ${getString(R.string.app_version_name_value)} (${getString(R.string.app_version_code_value)}))")
        LogUtilities.initialize(LogUtilities.DEBUG_ALL, this)
        createNotificationsChannels()
    }

    private fun createNotificationsChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(BACKGROUND_TASKS_NOTIFICATION_CHANNEL) == null) {
            NotificationChannel(
                BACKGROUND_TASKS_NOTIFICATION_CHANNEL,
                getString(R.string.background_tasks),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                description = getString(R.string.background_tasks)
                notificationManager.createNotificationChannel(this)
            }
        }
        if (notificationManager.getNotificationChannel(AUTO_CREATED_BACKUPS_NOTIFICATION_CHANNEL) == null) {
            NotificationChannel(
                AUTO_CREATED_BACKUPS_NOTIFICATION_CHANNEL,
                getString(R.string.auto_created_backups),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                description = getString(R.string.auto_created_backups)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        LogUtilities.show(this, "Uncaught exception", throwable)
    }

    companion object {
        const val BACKGROUND_TASKS_NOTIFICATION_CHANNEL = "background-tasks"
        const val AUTO_CREATED_BACKUPS_NOTIFICATION_CHANNEL = "auto-created-backups-notification"

        private const val TAG = "ApkExporter"

        private var iInstance: Main? = null

        fun getInstance(): Main = iInstance!!

        fun getUriFromFile(context: Context, file: File): Uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files-provider",
                file
            )
    }
}

package com.ryosoftware.apks_exporter

import android.annotation.SuppressLint
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
        instance = this
        LogUtilities.tag = TAG
        LogUtilities.logMode = LogUtilities.DEBUG_ERRORS
        LogUtilities.show(this, "Application started (current app version is ${getString(R.string.app_version_name_value)} (${getString(R.string.app_version_code_value)}))")
        ApplicationPreferences.initialize()
        createNotificationsChannels()
        Thread.setDefaultUncaughtExceptionHandler(this)
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
        if (notificationManager.getNotificationChannel(AUTO_CREATED_BACKUPS_NONE_NOTIFICATION_CHANNEL) == null) {
            NotificationChannel(
                AUTO_CREATED_BACKUPS_NONE_NOTIFICATION_CHANNEL,
                getString(R.string.auto_created_backups_none),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                description = getString(R.string.auto_created_backups_none)
                notificationManager.createNotificationChannel(this)
            }
        }
        if (notificationManager.getNotificationChannel(AUTO_CREATED_BACKUPS_ERROR_NOTIFICATION_CHANNEL) == null) {
            NotificationChannel(
                AUTO_CREATED_BACKUPS_ERROR_NOTIFICATION_CHANNEL,
                getString(R.string.auto_created_backups_error),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                description = getString(R.string.auto_created_backups_error)
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

        const val AUTO_CREATED_BACKUPS_NONE_NOTIFICATION_CHANNEL = "auto-created-backups-none-notification"
        const val AUTO_CREATED_BACKUPS_ERROR_NOTIFICATION_CHANNEL = "auto-created-backups-error-notification"

        private const val TAG = "ApkExporter"

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: Context
            private set

        fun getUriFromFile(context: Context, file: File): Uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files-provider",
                file
            )
    }
}

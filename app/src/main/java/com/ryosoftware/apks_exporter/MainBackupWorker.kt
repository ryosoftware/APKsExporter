package com.ryosoftware.apks_exporter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.ryosoftware.utilities.*

class MainBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(
            ForegroundInfo(
                SERVICE_NOTIFICATION_ID,
                createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        )
        doAutoBackupApps()
        return Result.success()
    }

    private fun createForegroundNotification(): Notification {
        val notification = NotificationCompat.Builder(applicationContext, Main.BACKGROUND_TASKS_NOTIFICATION_CHANNEL)
        val body = applicationContext.getString(R.string.checking_if_recently_installed_or_updated_apps)
        notification.setContentTitle(applicationContext.getString(R.string.app_name))
        notification.setContentText(body)
        notification.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        notification.setSmallIcon(R.drawable.ic_stat_notification_main_service)
        notification.setWhen(System.currentTimeMillis())
        notification.setShowWhen(true)
        notification.setContentIntent(
            PendingIntent.getActivity(
                applicationContext,
                SERVICE_NOTIFICATION_ID,
                Intent(applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        notification.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return notification.build()
    }

    private fun getAppsThatNeedsToBeBacked(): List<PackageInfo>? {
        val allPackages = applicationContext.packageManager.getInstalledPackages(0)
        val showSystemPackages = ApplicationPreferences.get(
            ApplicationPreferences.SHOW_SYSTEM_PACKAGES_KEY,
            ApplicationPreferences.SHOW_SYSTEM_PACKAGES_DEFAULT
        )
        val showSystemPackagesOnlyIfUpdated = ApplicationPreferences.get(
            ApplicationPreferences.SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_KEY,
            ApplicationPreferences.SHOW_SYSTEM_PACKAGES_ONLY_IF_UPDATED_DEFAULT
        )
        val appsToBeBacked = mutableListOf<PackageInfo>()
        for (packageInfo in allPackages) {
            if (packageInfo.applicationInfo?.packageName == null) continue
            if (MainService.isSystemApplication(packageInfo) &&
                (!showSystemPackages ||
                        (!MainService.isSystemApplicationUpdated(packageInfo) && showSystemPackagesOnlyIfUpdated))
            ) continue
            if (!MainService.canAutomaticallyBackup(packageInfo)) continue
            if (MainService.isAppBacked(packageInfo)) continue
            appsToBeBacked.add(packageInfo)
        }
        return appsToBeBacked.ifEmpty { null }
    }

    private fun getBackedAppsNotification(
        success: Boolean,
        backedApps: List<String>?
    ): Notification? {
        val count = backedApps?.size ?: 0
        if (!success || count > 0) {
            val notification = NotificationCompat.Builder(applicationContext, Main.AUTO_CREATED_BACKUPS_NOTIFICATION_CHANNEL)
            val body = if (count > 0) {
                applicationContext.resources.getQuantityString(
                    if (success) R.plurals.auto_apps_backup_ended_without_error
                    else R.plurals.auto_apps_backup_ended_with_error,
                    count,
                    count,
                    StringUtilities.join(backedApps, applicationContext.getString(R.string.strings_middle_separator), applicationContext.getString(R.string.strings_and_separator))
                )
            } else {
                applicationContext.getString(R.string.error_performing_auto_apps_backup)
            }
            notification.setContentTitle(applicationContext.getString(R.string.app_name))
            notification.setContentText(body)
            notification.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            notification.setSmallIcon(R.drawable.ic_stat_notification_auto_created_backups)
            notification.setWhen(System.currentTimeMillis())
            notification.setShowWhen(true)
            notification.setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    BACKED_APPS_NOTIFICATION_ID,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            notification.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            return notification.build()
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun doAutoBackupApps() {
        var success = true
        var backedApps: MutableList<String>? = null
        try {
            val appsToBeBacked = getAppsThatNeedsToBeBacked()
            if (appsToBeBacked != null) {
                for (packageInfo in appsToBeBacked) {
                    if (!MainService.doBackup(applicationContext, packageInfo)) {
                        success = false
                        break
                    }
                    if (backedApps == null) backedApps = mutableListOf()
                    backedApps.add(
                        PackageManagerUtilities.getApplicationLabel(
                            applicationContext,
                            packageInfo.packageName,
                            packageInfo.packageName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(this@MainBackupWorker, e)
            success = false
        }
        val notification = getBackedAppsNotification(success, backedApps)
        if (notification != null &&
            PermissionUtilities.permissionGranted(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            NotificationManagerCompat.from(applicationContext).notify(BACKED_APPS_NOTIFICATION_ID, notification)
        }
        applicationContext.sendBroadcast(Intent(MainService.ACTION_AUTO_BACKUP_APPS_DONE))
        MainService.execute(applicationContext, INTERVAL_BEFORE_AUTO_BACKUP_APPS)
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val BACKED_APPS_NOTIFICATION_ID = SERVICE_NOTIFICATION_ID + 1
        private const val INTERVAL_BEFORE_AUTO_BACKUP_APPS = 6 * DateUtils.HOUR_IN_MILLIS
    }
}

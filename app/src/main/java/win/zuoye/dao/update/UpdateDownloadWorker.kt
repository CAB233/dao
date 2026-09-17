package win.zuoye.dao.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.CancellationException
import win.zuoye.dao.MainActivity
import win.zuoye.dao.R

interface UpdateDownloads {
    fun start(update: UpdateInfo)
}

class WorkManagerUpdateDownloads(context: Context) : UpdateDownloads {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun start(update: UpdateInfo) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_VERSION to update.versionName,
                    UpdateDownloadWorker.KEY_URL to update.downloadUrl,
                    UpdateDownloadWorker.KEY_SHA256 to update.sha256,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            UpdateDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

}

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val version = inputData.getString(KEY_VERSION) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        setForeground(downloadForegroundInfo(progress = null))
        val update = UpdateInfo(
            versionName = version,
            releaseNotes = "",
            downloadUrl = url,
            sha256 = inputData.getString(KEY_SHA256),
        )
        return try {
            val apk = AppUpdater.downloadApk(applicationContext, update) { progress ->
                setProgress(workDataOf(KEY_PROGRESS to (progress ?: -1)))
                setForeground(downloadForegroundInfo(progress))
            }
            showDownloadComplete(version, apk)
            Result.success(workDataOf(KEY_OUTPUT_PATH to apk.absolutePath))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                showDownloadFailed()
                Result.failure()
            }
        }
    }

    private fun downloadForegroundInfo(progress: Int?): ForegroundInfo {
        ensureNotificationChannels()
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgress(progress ?: 0)
            .setProgressIndeterminate(progress == null)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(applicationContext.getString(R.string.update_notification_downloading_title))
            .setContentText(
                progress?.let {
                    applicationContext.getString(R.string.update_notification_downloading_progress, it)
                } ?: applicationContext.getString(R.string.update_notification_downloading),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(progressStyle)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(
                progress?.let {
                    applicationContext.getString(R.string.update_notification_short_progress, it)
                },
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun showDownloadComplete(version: String, apk: File) {
        ensureNotificationChannels()
        val installIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_CONFIRM_UPDATE_INSTALL
            putExtra(EXTRA_APK_PATH, apk.absolutePath)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            REQUEST_INSTALL,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            NOTIFICATION_ID_COMPLETE,
            NotificationCompat.Builder(applicationContext, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(applicationContext.getString(R.string.update_notification_complete_title))
                .setContentText(applicationContext.getString(R.string.update_notification_complete, version))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    private fun showDownloadFailed() {
        ensureNotificationChannels()
        notificationManager.notify(
            NOTIFICATION_ID_RESULT,
            NotificationCompat.Builder(applicationContext, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(applicationContext.getString(R.string.update_notification_failed_title))
                .setContentText(applicationContext.getString(R.string.update_download_failed))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build(),
        )
    }

    private val notificationManager: NotificationManager
        get() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    applicationContext.getString(R.string.update_notification_channel_download),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_RESULT,
                    applicationContext.getString(R.string.update_notification_channel_result),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "app-update-download"
        const val ACTION_CONFIRM_UPDATE_INSTALL = "win.zuoye.dao.action.CONFIRM_UPDATE_INSTALL"
        const val EXTRA_APK_PATH = "apk_path"
        const val KEY_VERSION = "version"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"

        private const val CHANNEL_DOWNLOAD = "update_download"
        private const val CHANNEL_RESULT = "update_result"
        private const val NOTIFICATION_ID_PROGRESS = 10_001
        private const val NOTIFICATION_ID_COMPLETE = 10_002
        private const val NOTIFICATION_ID_RESULT = 10_003
        private const val REQUEST_INSTALL = 20_001
    }
}

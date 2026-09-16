package win.zuoye.dao.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.core.content.edit
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Running(val progress: Int?) : UpdateDownloadState
    data class Complete(val workId: String, val apk: File) : UpdateDownloadState
    data class Failed(val workId: String) : UpdateDownloadState
}

interface UpdateDownloads {
    val state: Flow<UpdateDownloadState>
    fun start(update: UpdateInfo)
    fun consume(workId: String)
}

class WorkManagerUpdateDownloads(context: Context) : UpdateDownloads {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences("update-downloads", Context.MODE_PRIVATE)

    override val state: Flow<UpdateDownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.UNIQUE_WORK_NAME)
            .map { infos ->
                val info = infos.lastOrNull()
                    ?: return@map UpdateDownloadState.Idle
                if (preferences.getString(KEY_CONSUMED_WORK_ID, null) == info.id.toString()) {
                    return@map UpdateDownloadState.Idle
                }
                when (info.state) {
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    -> UpdateDownloadState.Running(
                        info.progress.getInt(UpdateDownloadWorker.KEY_PROGRESS, -1).takeIf { it >= 0 },
                    )
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(UpdateDownloadWorker.KEY_OUTPUT_PATH)
                        if (path == null) UpdateDownloadState.Failed(info.id.toString())
                        else UpdateDownloadState.Complete(info.id.toString(), File(path))
                    }
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                    -> UpdateDownloadState.Failed(info.id.toString())
                }
            }

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

    override fun consume(workId: String) {
        preferences.edit { putString(KEY_CONSUMED_WORK_ID, workId) }
    }

    private companion object {
        const val KEY_CONSUMED_WORK_ID = "consumed_work_id"
    }
}

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val version = inputData.getString(KEY_VERSION) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val update = UpdateInfo(
            versionName = version,
            releaseNotes = "",
            downloadUrl = url,
            sha256 = inputData.getString(KEY_SHA256),
        )
        return try {
            val apk = AppUpdater.downloadApk(applicationContext, update) { progress ->
                setProgress(workDataOf(KEY_PROGRESS to (progress ?: -1)))
            }
            Result.success(workDataOf(KEY_OUTPUT_PATH to apk.absolutePath))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "app-update-download"
        const val KEY_VERSION = "version"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
    }
}

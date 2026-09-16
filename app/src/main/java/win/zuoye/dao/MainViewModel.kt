package win.zuoye.dao

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanStore
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.domain.ImportResult
import win.zuoye.dao.domain.importPlan
import win.zuoye.dao.update.AppUpdater
import win.zuoye.dao.update.UpdateChecker
import win.zuoye.dao.update.UpdateDownloadState
import win.zuoye.dao.update.UpdateDownloads
import win.zuoye.dao.update.UpdateInfo
import win.zuoye.dao.update.WorkManagerUpdateDownloads

@Immutable
data class MainUiState(
    val document: PlanDocument? = null,
    val corruptionBackup: String? = null,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val downloadingUpdate: Boolean = false,
    val downloadProgress: Int? = null,
)

sealed interface MainEvent {
    data object UpdateCheckFailed : MainEvent
    data object UpdateDownloadFailed : MainEvent
    data class InstallUpdate(val apk: File) : MainEvent
    data class ImportFinished(val result: ImportResult) : MainEvent
}

/**
 * 持有应用数据与更新流程。构造函数只依赖小接口，单测可直接注入内存 fake；
 * 单模块当前没有足够复杂的对象图，因此不用 Hilt，避免为两个依赖增加生成代码和启动成本。
 */
class MainViewModel(
    private val planStore: PlanStore,
    private val updateChecker: UpdateChecker,
    private val updateDownloads: UpdateDownloads,
    private val currentVersionName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var updateCheckStarted = false
    private val handledDownloads = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            planStore.document.collect { document ->
                _uiState.update { it.copy(document = document) }
                if (!updateCheckStarted) {
                    updateCheckStarted = true
                    if (document.checkUpdatesOnLaunch) checkForUpdate(document)
                }
            }
        }
        viewModelScope.launch {
            planStore.corruptionRecovery.collect { backup ->
                _uiState.update { it.copy(corruptionBackup = backup) }
            }
        }
        viewModelScope.launch {
            updateDownloads.state.collect(::handleDownloadState)
        }
    }

    private suspend fun checkForUpdate(document: PlanDocument) {
        try {
            val update = updateChecker.checkForUpdate(currentVersionName, document.updateChannel)
            _uiState.update { it.copy(updateInfo = update, showUpdateDialog = update != null) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            eventChannel.send(MainEvent.UpdateCheckFailed)
        }
    }

    private suspend fun handleDownloadState(state: UpdateDownloadState) {
        when (state) {
            UpdateDownloadState.Idle -> Unit
            is UpdateDownloadState.Running -> _uiState.update {
                it.copy(downloadingUpdate = true, downloadProgress = state.progress)
            }
            is UpdateDownloadState.Complete -> {
                _uiState.update {
                    it.copy(showUpdateDialog = false, downloadingUpdate = false, downloadProgress = null)
                }
                if (handledDownloads.add(state.workId)) {
                    eventChannel.send(MainEvent.InstallUpdate(state.apk))
                    updateDownloads.consume(state.workId)
                }
            }
            is UpdateDownloadState.Failed -> {
                val wasDownloading = _uiState.value.downloadingUpdate
                _uiState.update {
                    it.copy(showUpdateDialog = false, downloadingUpdate = false, downloadProgress = null)
                }
                if (wasDownloading && handledDownloads.add(state.workId)) {
                    eventChannel.send(MainEvent.UpdateDownloadFailed)
                }
                updateDownloads.consume(state.workId)
            }
        }
    }

    fun acknowledgeCorruptionRecovery() = planStore.acknowledgeCorruptionRecovery()

    fun dismissUpdate() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun startUpdateDownload() {
        val update = _uiState.value.updateInfo ?: return
        _uiState.update { it.copy(downloadingUpdate = true, downloadProgress = null) }
        updateDownloads.start(update)
    }

    fun mutate(transform: (PlanDocument) -> PlanDocument) {
        viewModelScope.launch { planStore.update(transform) }
    }

    fun saveDocument(document: PlanDocument) = mutate { document }

    fun skipOnboarding() = mutate { it.copy(onboardingDone = true) }

    fun saveNewScheme(
        cycleDays: Int,
        templates: ImmutableList<ShiftTemplate>,
        dayTemplateIds: ImmutableList<Long>,
        anchorEpochDay: Long,
        planName: String,
        groupName: String,
    ) {
        mutate { current ->
            val id = System.currentTimeMillis()
            current.copy(
                templates = templates,
                schemes = current.schemes.toPersistentList().add(
                    Scheme(
                        id = id,
                        name = planName,
                        cycleDays = cycleDays,
                        dayTemplateIds = dayTemplateIds,
                        createdAt = id,
                        groups = persistentListOf(
                            SchemeGroup(
                                id = id,
                                name = groupName,
                                anchorEpochDay = anchorEpochDay,
                            ),
                        ),
                        defaultGroupId = id,
                    ),
                ),
                activeSchemeId = id,
                onboardingDone = true,
            )
        }
    }

    fun importPlan(payload: PlanShare, completeOnboarding: Boolean = false) {
        viewModelScope.launch {
            var result: ImportResult? = null
            planStore.update { current ->
                val (merged, outcome) = current.importPlan(payload)
                result = outcome
                if (completeOnboarding && outcome.changed) merged.copy(onboardingDone = true) else merged
            }
            result?.let { eventChannel.send(MainEvent.ImportFinished(it)) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val application = context.applicationContext
            @Suppress("DEPRECATION")
            val versionName = application.packageManager
                .getPackageInfo(application.packageName, 0).versionName ?: "0"
            return viewModelFactory {
                initializer {
                    MainViewModel(
                        planStore = PlanRepository.get(application),
                        updateChecker = AppUpdater,
                        updateDownloads = WorkManagerUpdateDownloads(application),
                        currentVersionName = versionName,
                    )
                }
            }
        }
    }
}

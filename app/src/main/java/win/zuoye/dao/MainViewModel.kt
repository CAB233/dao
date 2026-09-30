package win.zuoye.dao

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
import win.zuoye.dao.update.UpdateDownloads
import win.zuoye.dao.update.UpdateInfo
import win.zuoye.dao.update.WorkManagerUpdateDownloads
import win.zuoye.dao.widget.DaoWidgetProvider

@Immutable
data class MainUiState(
    val document: PlanDocument? = null,
    val corruptionBackup: String? = null,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val showUpdateInSettings: Boolean = false,
)

sealed interface MainEvent {
    data object UpdateCheckFailed : MainEvent

    data class ImportFinished(val result: ImportResult) : MainEvent
}

/**
 * 持有应用数据与更新流程。构造函数只依赖小接口，单测可直接注入内存 fake； 单模块当前没有足够复杂的对象图，因此不用 Hilt，避免为两个依赖增加生成代码和启动成本。
 *
 * [onDocumentChanged] 在每次数据落盘后回调，用来让桌面小组件跟上（真实实现见 [companion].factory）； 默认空实现，这样单测不需要 Android
 * Context。
 */
class MainViewModel(
    private val planStore: PlanStore,
    private val updateChecker: UpdateChecker,
    private val updateDownloads: UpdateDownloads,
    private val currentVersionName: String,
    private val onDocumentChanged: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<MainEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var updateCheckStarted = false

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
        // drop(1)：冷启动时那次初值不算「变化」，否则每次打开 App 都会白刷一次小组件
        viewModelScope.launch { planStore.document.drop(1).collect { onDocumentChanged() } }
        viewModelScope.launch {
            planStore.corruptionRecovery.collect { backup ->
                _uiState.update { it.copy(corruptionBackup = backup) }
            }
        }
    }

    private suspend fun checkForUpdate(document: PlanDocument) {
        try {
            val update = updateChecker.checkForUpdate(currentVersionName, document.updateChannel)
            _uiState.update {
                it.copy(
                    updateInfo = update,
                    showUpdateDialog = update != null,
                    showUpdateInSettings = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            eventChannel.send(MainEvent.UpdateCheckFailed)
        }
    }

    fun acknowledgeCorruptionRecovery() = planStore.acknowledgeCorruptionRecovery()

    fun dismissUpdate() {
        _uiState.update { it.copy(showUpdateDialog = false, showUpdateInSettings = true) }
    }

    fun startUpdateDownload() {
        val update = _uiState.value.updateInfo ?: return
        _uiState.update { it.copy(showUpdateDialog = false, showUpdateInSettings = false) }
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
                schemes =
                    current.schemes
                        .toPersistentList()
                        .add(
                            Scheme(
                                id = id,
                                name = planName,
                                cycleDays = cycleDays,
                                dayTemplateIds = dayTemplateIds,
                                createdAt = id,
                                groups =
                                    persistentListOf(
                                        SchemeGroup(
                                            id = id,
                                            name = groupName,
                                            anchorEpochDay = anchorEpochDay,
                                        )
                                    ),
                                defaultGroupId = id,
                            )
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
                if (completeOnboarding && outcome.changed) merged.copy(onboardingDone = true)
                else merged
            }
            result?.let { eventChannel.send(MainEvent.ImportFinished(it)) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val application = context.applicationContext
            @Suppress("DEPRECATION")
            val versionName =
                application.packageManager.getPackageInfo(application.packageName, 0).versionName
                    ?: "0"
            return viewModelFactory {
                initializer {
                    MainViewModel(
                        planStore = PlanRepository.get(application),
                        updateChecker = AppUpdater,
                        updateDownloads = WorkManagerUpdateDownloads(application),
                        currentVersionName = versionName,
                        onDocumentChanged = { DaoWidgetProvider.refreshNow(application) },
                    )
                }
            }
        }
    }
}

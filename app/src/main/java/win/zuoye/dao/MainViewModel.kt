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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import win.zuoye.dao.data.IdSource
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanRepository
import win.zuoye.dao.data.PlanShare
import win.zuoye.dao.data.PlanStore
import win.zuoye.dao.data.Scheme
import win.zuoye.dao.data.SchemeGroup
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.data.usedIds
import win.zuoye.dao.domain.ImportResult
import win.zuoye.dao.domain.importPlan
import win.zuoye.dao.update.AppUpdater
import win.zuoye.dao.update.UpdateChecker
import win.zuoye.dao.update.UpdateDownloads
import win.zuoye.dao.update.UpdateInfo
import win.zuoye.dao.update.WorkManagerUpdateDownloads

@Immutable
data class MainUiState(
    val document: PlanDocument? = null,
    val corruptionBackup: String? = null,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val showUpdateInSettings: Boolean = false,
    val updateCheckInProgress: Boolean = false,
    val updateChecked: Boolean = false,
)

sealed interface MainEvent {
    data object UpdateCheckFailed : MainEvent

    data class ImportFinished(val result: ImportResult) : MainEvent
}

/** 持有应用数据与更新流程。构造函数只依赖小接口，单测可直接注入内存 fake； 单模块当前没有足够复杂的对象图，因此不用 Hilt，避免为两个依赖增加生成代码和启动成本。 */
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
    }

    private suspend fun checkForUpdate(document: PlanDocument, showDialog: Boolean = true) {
        try {
            val update = updateChecker.checkForUpdate(currentVersionName, document.updateChannel)
            _uiState.update {
                it.copy(
                    updateInfo = update,
                    showUpdateDialog = showDialog && update != null,
                    showUpdateInSettings = false,
                    updateChecked = true,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _uiState.update { it.copy(updateChecked = true) }
            eventChannel.send(MainEvent.UpdateCheckFailed)
        }
    }

    /** About 页的「手动检查更新」；检查进行中时忽略重复点击。 */
    fun checkForUpdateNow() {
        val document = _uiState.value.document ?: return
        if (_uiState.value.updateCheckInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheckInProgress = true) }
            try {
                checkForUpdate(document, showDialog = false)
            } finally {
                _uiState.update { it.copy(updateCheckInProgress = false) }
            }
        }
    }

    /** 关于页用 summary 展示更新，进入时把待弹的更新弹窗收掉。 */
    fun suppressUpdateDialog() {
        if (_uiState.value.showUpdateDialog) {
            _uiState.update { it.copy(showUpdateDialog = false) }
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
            val now = System.currentTimeMillis()
            // 新班次模板也要计入已用 id，避免方案/班组 id 与它们撞车
            val newIds = IdSource(current.usedIds() + templates.map { it.id }, now)
            val schemeId = newIds.nextId()
            val groupId = newIds.nextId()
            current.copy(
                templates = templates,
                schemes =
                    current.schemes
                        .toPersistentList()
                        .adding(
                            Scheme(
                                id = schemeId,
                                name = planName,
                                cycleDays = cycleDays,
                                dayTemplateIds = dayTemplateIds,
                                createdAt = now,
                                groups =
                                    persistentListOf(
                                        SchemeGroup(
                                            id = groupId,
                                            name = groupName,
                                            anchorEpochDay = anchorEpochDay,
                                        )
                                    ),
                                defaultGroupId = groupId,
                            )
                        ),
                activeSchemeId = schemeId,
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
                    )
                }
            }
        }
    }
}

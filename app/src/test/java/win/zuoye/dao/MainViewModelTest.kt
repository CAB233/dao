package win.zuoye.dao

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.PlanStore
import win.zuoye.dao.data.UpdateChannel
import win.zuoye.dao.update.UpdateChecker
import win.zuoye.dao.update.UpdateDownloadState
import win.zuoye.dao.update.UpdateDownloads
import win.zuoye.dao.update.UpdateInfo

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `document triggers update check outside composition`() = runTest(dispatcher.scheduler) {
        val store = FakePlanStore(PlanDocument(checkUpdatesOnLaunch = true))
        val update = UpdateInfo("2.0", "notes", "https://example.test/app.apk", null)
        val checker = FakeUpdateChecker(update)
        val viewModel = MainViewModel(store, checker, FakeDownloads(), "1.0")

        advanceUntilIdle()

        assertEquals(store.document.value, viewModel.uiState.value.document)
        assertEquals(update, viewModel.uiState.value.updateInfo)
        assertTrue(viewModel.uiState.value.showUpdateDialog)
        assertEquals(1, checker.calls)
    }

    @Test
    fun `mutations use replaceable plan store`() = runTest(dispatcher.scheduler) {
        val store = FakePlanStore(PlanDocument(onboardingDone = false))
        val viewModel = MainViewModel(store, FakeUpdateChecker(null), FakeDownloads(), "1.0")

        viewModel.skipOnboarding()
        advanceUntilIdle()

        assertTrue(store.document.value.onboardingDone)
        assertTrue(viewModel.uiState.value.document?.onboardingDone == true)
    }

    @Test
    fun `download state survives UI recreation boundary`() = runTest(dispatcher.scheduler) {
        val downloads = FakeDownloads()
        val update = UpdateInfo("2.0", "", "https://example.test/app.apk", null)
        val viewModel = MainViewModel(
            FakePlanStore(PlanDocument(checkUpdatesOnLaunch = true)),
            FakeUpdateChecker(update),
            downloads,
            "1.0",
        )
        advanceUntilIdle()

        viewModel.startUpdateDownload()
        downloads.state.value = UpdateDownloadState.Running(40)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.downloadingUpdate)
        assertEquals(40, viewModel.uiState.value.downloadProgress)

        downloads.state.value = UpdateDownloadState.Complete("work-1", File("update.apk"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.downloadingUpdate)
        assertFalse(viewModel.uiState.value.showUpdateDialog)
        assertEquals(1, downloads.starts)
    }
}

private class FakePlanStore(initial: PlanDocument) : PlanStore {
    override val document = MutableStateFlow(initial)
    override val corruptionRecovery: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun update(transform: (PlanDocument) -> PlanDocument) {
        document.value = transform(document.value)
    }

    override fun acknowledgeCorruptionRecovery() = Unit
}

private class FakeUpdateChecker(private val result: UpdateInfo?) : UpdateChecker {
    var calls = 0
    override suspend fun checkForUpdate(
        currentVersionName: String,
        channel: UpdateChannel,
    ): UpdateInfo? {
        calls++
        return result
    }
}

private class FakeDownloads : UpdateDownloads {
    override val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    var starts = 0
    override fun start(update: UpdateInfo) {
        starts++
    }

    override fun consume(workId: String) = Unit
}

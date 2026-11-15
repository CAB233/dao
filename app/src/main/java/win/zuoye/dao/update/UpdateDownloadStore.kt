package win.zuoye.dao.update

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用内可见的下载状态：没有通知权限时，靠它展示进度和安装入口。 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Running(val progress: Int?) : UpdateDownloadState

    data class Succeeded(val apkPath: String) : UpdateDownloadState

    data object Failed : UpdateDownloadState
}

/**
 * 记录下载状态与「已下载但还没安装」的更新包。
 *
 * Worker 写、界面读；成功路径额外落盘，进程被杀或下次打开应用时仍能提示安装。
 */
object UpdateDownloadStore {
    private const val PREFS = "update_download"
    private const val KEY_APK_PATH = "apk_path"

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    fun running(progress: Int?) {
        _state.value = UpdateDownloadState.Running(progress)
    }

    fun record(context: Context, apk: File) {
        prefs(context).edit().putString(KEY_APK_PATH, apk.absolutePath).apply()
        _state.value = UpdateDownloadState.Succeeded(apk.absolutePath)
    }

    fun failed() {
        _state.value = UpdateDownloadState.Failed
    }

    /** 取出未安装的更新包；文件已不存在时顺手清掉记录。 */
    fun pending(context: Context): File? {
        val path = prefs(context).getString(KEY_APK_PATH, null) ?: return null
        val apk = File(path)
        if (!apk.exists()) {
            clear(context)
            return null
        }
        return apk
    }

    /** 冷启动时恢复「已下载待安装」状态，让设置页能继续提供安装入口。 */
    fun restore(context: Context) {
        pending(context)?.let { _state.value = UpdateDownloadState.Succeeded(it.absolutePath) }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_APK_PATH).apply()
        if (_state.value is UpdateDownloadState.Succeeded) {
            _state.value = UpdateDownloadState.Idle
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

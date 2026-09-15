package win.zuoye.dao.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.ui.about.appVersionName
import win.zuoye.dao.ui.common.WEEKDAY_LABELS

/** 设置：倒班方案（二级页面入口）、关于。 */
@Composable
fun SettingsScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenAbout: () -> Unit,
    onWeekStartDayChange: (Int) -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val versionName = remember(context) { context.appVersionName() }
    val weekStartDay = doc.weekStartDay.coerceIn(0, WEEKDAY_LABELS.lastIndex)

    val activeScheme = doc.activeScheme() ?: doc.schemes.firstOrNull()

    Scaffold(
        topBar = { TopAppBar(title = "设置") },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // 设置页行数少，按规范仍可用 Column + verticalScroll（不拆行、不改 LazyColumn）
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "一周开始日",
                    summary = "选择每周的起始星期",
                    items = WEEKDAY_LABELS.map { "周$it" },
                    selectedIndex = weekStartDay,
                    onSelectedIndexChange = onWeekStartDayChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = "倒班方案",
                    summary = activeScheme?.let {
                        "${it.cycleDays} 天周期 · ${doc.templates.size} 个班次"
                    } ?: "还没有方案，点进去建一个",
                    endActions = { Chevron() },
                    onClick = onOpenPlan,
                )
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = "关于",
                    summary = "版本 $versionName",
                    endActions = { Chevron() },
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = Modifier.size(12.dp, 18.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

package win.zuoye.dao.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.R
import win.zuoye.dao.ui.about.appVersionName

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
    val weekdays = stringArrayResource(R.array.weekday_full)
    val weekStartDay = doc.weekStartDay.coerceIn(0, weekdays.lastIndex)

    val activeScheme = doc.activeScheme() ?: doc.schemes.firstOrNull()

    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.nav_settings)) },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // 设置页行数少，按规范仍可用 Column + verticalScroll（不拆行、不改 LazyColumn）
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_week_start),
                    summary = stringResource(R.string.settings_week_start_summary),
                    items = weekdays.toList(),
                    selectedIndex = weekStartDay,
                    onSelectedIndexChange = onWeekStartDayChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = stringResource(R.string.settings_plans),
                    summary = stringResource(R.string.settings_plans_summary),
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeScheme?.name ?: stringResource(R.string.status_not_selected),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                            Chevron(Modifier.padding(start = 8.dp))
                        }
                    },
                    onClick = onOpenPlan,
                )
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                BasicComponent(
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.version_text, versionName),
                    endActions = { Chevron() },
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = modifier.size(12.dp, 18.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

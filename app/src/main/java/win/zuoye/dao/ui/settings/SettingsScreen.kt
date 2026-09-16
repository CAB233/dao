package win.zuoye.dao.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
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
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import win.zuoye.dao.data.PlanDocument
import win.zuoye.dao.data.CalendarViewMode
import win.zuoye.dao.data.ThemeMode
import win.zuoye.dao.data.UpdateChannel
import win.zuoye.dao.R
import win.zuoye.dao.ui.about.appVersionName

/** 设置：倒班方案（二级页面入口）、关于。 */
@Composable
fun SettingsScreen(
    doc: PlanDocument,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenAbout: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWeekStartDayChange: (Int) -> Unit,
    onCalendarViewModeChange: (CalendarViewMode) -> Unit,
    onCheckUpdatesOnLaunchChange: (Boolean) -> Unit,
    onUpdateChannelChange: (UpdateChannel) -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val versionName = remember(context) { context.appVersionName() }
    val themeModeOptions = stringArrayResource(R.array.theme_mode_options)
    val themeModes = ThemeMode.entries
    val weekdays = stringArrayResource(R.array.weekday_full)
    val weekStartDay = doc.weekStartDay.coerceIn(0, weekdays.lastIndex)
    val calendarViewOptions = stringArrayResource(R.array.calendar_view_options)
    val calendarViewModes = CalendarViewMode.entries
    val updateChannelOptions = stringArrayResource(R.array.update_channel_options)
    val updateChannels = UpdateChannel.entries

    val activeScheme = doc.activeScheme() ?: doc.schemes.firstOrNull()

    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.nav_settings)) },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState()),
            ) {
            // 设置页行数少，按规范仍可用 Column + verticalScroll（不拆行、不改 LazyColumn）
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_theme_mode),
                    summary = stringResource(R.string.settings_theme_mode_summary),
                    items = themeModeOptions.toList(),
                    selectedIndex = themeModes.indexOf(doc.themeMode),
                    onSelectedIndexChange = { index ->
                        themeModes.getOrNull(index)?.let(onThemeModeChange)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_week_start),
                    summary = stringResource(R.string.settings_week_start_summary),
                    items = weekdays.toList(),
                    selectedIndex = weekStartDay,
                    onSelectedIndexChange = onWeekStartDayChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_calendar_view),
                    summary = stringResource(R.string.settings_calendar_view_summary),
                    items = calendarViewOptions.toList(),
                    selectedIndex = calendarViewModes.indexOf(doc.calendarViewMode),
                    onSelectedIndexChange = { index ->
                        calendarViewModes.getOrNull(index)?.let(onCalendarViewModeChange)
                    },
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
                    title = stringResource(R.string.settings_check_updates),
                    summary = stringResource(R.string.settings_check_updates_summary),
                    endActions = {
                        Switch(
                            checked = doc.checkUpdatesOnLaunch,
                            onCheckedChange = onCheckUpdatesOnLaunchChange,
                        )
                    },
                    onClick = {
                        onCheckUpdatesOnLaunchChange(!doc.checkUpdatesOnLaunch)
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_update_channel),
                    summary = stringResource(R.string.settings_update_channel_summary),
                    items = updateChannelOptions.toList(),
                    selectedIndex = updateChannels.indexOf(doc.updateChannel),
                    onSelectedIndexChange = { index ->
                        updateChannels.getOrNull(index)?.let(onUpdateChannelChange)
                    },
                    modifier = Modifier.fillMaxWidth(),
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

# 交接说明（2026-09-30）

> 给下一个会话看的状态快照。**先读这份，再读 `AGENTS.md`**（后者是长期规范，这份是本轮进度）。
> **接手会话已补做真机验证、修掉两个 bug，并把小组件从"整月网格"改成了"可上下滑动的周列表"，见「四·五」和「九」。**

## 一、当前状态一句话

仓库 `CAB233/dao`（安卓应用「倒班表」）已按需求完成 **7 项功能迭代 + 2 处接手后修复 + 1 次小组件改版**，
`assembleDebug` + **72 个单测** + `ktfmtCheck` 全绿，**但工作区变更全部未提交**。

## 二、本轮做了什么

按时间顺序，每项都对应一轮用户反馈：

| # | 提交范围建议 | 内容 |
|---|---|---|
| 1 | `feat: 桌面小组件` | 新增 `widget/` 包。「今日班次」小组件（后来被 #3 取代为月历形态）；`MainViewModel` 加 `onDocumentChanged` 回调驱动刷新；`MainActivity` 的 `ON_RESUME` 兜底 |
| 2 | `fix: 小组件不显示` | **线上 bug**：`setColorFilter` 打在 `TextView` 上，`RemoteViews.apply()` 抛 `ActionException`，宿主一直停在加载占位图。改成 `ImageView` + `src` + `fitXY`；去掉吞异常的 `runCatching`；补 Robolectric 防线 `DaoWidgetProviderTest` |
| 3 | `feat: 小组件改为整月日历` | 小组件改成 4×4 月历；抽出 `domain/TodayRoster.kt`（今日状态）、`domain/MonthGrid.kt`（月网格），首页状态卡与小组件共用 |
| 4 | `feat: 日历补满 6×7 / 过去消色 / 今日高亮` | `MonthGrid` 从"每行只放本月日期"改成**固定 6 行 × 7 列**（含上下月补位，列才对齐表头）；格子分五种状态；网格靠 `layout_weight` 撑满高度 |
| 5 | `refactor: 统一 App 与小组件配色` | 新增 `domain/DayVisualState.kt`（五种状态的**唯一判定**）+ `ShiftPalette.onColor()`（按 WCAG 对比度选白字/深字）。App 内月历从"13% 淡底 + 深色字"改成与小组件一致的**实心填充** |
| 6 | `fix: 跨零点结束时刻` | 用户两次反馈。`ShiftEndMoment` 让同一个夜班在零点前后说法不同：**零点前「到次日07:30」，零点后「到07:30」** |
| 7 | `feat: 小组件翻月` + `feat: 换班覆盖` | 小组件标题行加 `‹ ›` 和「今」；`WidgetMonthStore`（SharedPreferences，不入 plan.json）。`domain/ShiftOverride.kt` 补上 `overrides` 的写入口，首页点某天可换班 |
| 8 | `fix: 小组件底部状态行取错班组` | **接手会话发现并修复（真机复现）**：`TodayRoster.defaultShift` 原是 `groups.firstOrNull()`，而 `phase` 按**默认班组**算 —— 两者不是同一个班组时，底部会把休息班的 `endMinute = 0` 配成「上班中 · 到 00:00」（正是用户报过的那种形态）。改成取默认班组那一项，补 2 个单测 |
| 9 | `feat: 小组件改成可滑动的周列表` | **接手会话按用户要求改版**：整月网格换成 `ListView` + `DaoWeekListService`，手指上下滑动逐周滚动；窗口从锚点周起给 20 周，往回看用「‹」把窗口整段前移（`WidgetMonthStore` 改存相对今月的偏移）。详见「九」 |
| 10 | `fix: 小组件翻月箭头不可见` | 箭头 vector 的 `fillColor` 是白色又没有 `tint`，画在白卡片上等于隐形——按钮能点、能翻月，但用户看不见它（这也是"翻月到底能不能用"的由来）。补 `android:tint="@color/widget_text_secondary"` |

## 三、构建环境（**重要，不配好编不过**）

这台机器是**新配的**，JDK/SDK 都装在非默认位置：

| 项 | 值 |
|---|---|
| JDK（构建用） | `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` |
| JDK（备用） | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| Android SDK | `D:\Android\sdk`（platform **android-37.0**、build-tools 37.0.0、platform-tools） |
| Gradle | **手动装在** `%USERPROFILE%\.gradle-wrapper-dist\gradle-9.7.1\`（wrapper 用不了，见下） |
| Trust store | `%USERPROFILE%\.dsh\android-build\cacerts25` |
| AVD | `dao_test`（API 35, google_apis, x86_64），可用于自测 |

**必须用的环境变量 + 命令**（wrapper 会因 TLS 失败，所以直接用解压出来的 gradle）：

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:ANDROID_HOME="D:\Android\sdk"; $env:ANDROID_SDK_ROOT="D:\Android\sdk"
$env:GRADLE_OPTS="-Djavax.net.ssl.trustStore=$env:USERPROFILE\.dsh\android-build\cacerts25 -Djavax.net.ssl.trustStorePassword=changeit"
& "$env:USERPROFILE\.gradle-wrapper-dist\gradle-9.7.1\bin\gradle.bat" `
  :app:assembleDebug :app:testDebugUnitTest ktfmtCheck --console=plain --no-daemon
```

### 为什么要自定义 trust store

1. JDK 的 `cacerts` 里没有 `WE1`/`WE2` 中间证书，而 **Java 不会像 Schannel 那样走 AIA 补链** →
   `services.gradle.org` / `dl.google.com` 全部 PKIX 失败。已把两个中间证书导入自定义 trust store。
2. 这台机器的 `hosts` 把 `github.com` 等指向 `127.0.0.1`，由 **Steam++ (SteamTools)** 用自签根证书接管 TLS
   （Kotlin 编译器 jar 从 GitHub 下，所以必须信任它）。该根证书也已导入**仅这份** trust store。
   **没有改动系统证书库、没有改 JDK 安装目录、没有动 `local.properties`。** 详见「六、注意事项」。

### 其他环境坑

- **Gradle wrapper 用不了**：`gradlew.bat` 下载发行包时 PKIX 失败（JAVA_TOOL_OPTIONS 在 wrapper 里不生效）。
  已用 `curl --ssl-no-revoke` 手动下载 gradle-9.7.1 并校验 SHA256 后解压到上面那个路径。
- **Robolectric 必须显式 `@Config(sdk = [...])`**：它自带 android-all 最高 36，而应用 targetSdk 是 37，
  不写会直接 `targetSdkVersion=37 > maxSdkVersion=36` 配置失败。现有测试取 35（个别取 30 覆盖降级分支）。
- **`Expand-Archive` 不认 `.aar`**：要先把 aar 复制成 `.zip` 再解压（排查 miuix API 时用过）。
- **`.NET` 文件 API 的工作目录**是 pwsh 进程 cwd，不是 `Set-Location` 的结果——用绝对路径。

## 四、验证状态（**交接重点：哪些验证过、哪些没有**）

```
:app:assembleDebug + :app:testDebugUnitTest + ktfmtCheck  → BUILD SUCCESSFUL
66 tests, 0 failures, 0 errors        （12 个测试类）
git diff --check                       → 干净
APK                                    → app/build/outputs/apk/debug/Dao-0.4.0-debug.apk (17.7 MB)
```

| 功能 | 单测 | 模拟器人肉验证 |
|---|---|---|
| 小组件渲染（RemoteViews 合法性） | ✅ Robolectric 真 inflate + apply | ✅ 见过实际渲染 |
| 小组件不显示那个 bug | ✅ 用临时 repro 用例证明测试能抓到 | ✅ |
| 跨零点结束时刻（你的规则） | ✅ 8 个用例含 06:33 场景 | ⚠️ 时钟已对齐到 06:33 CST 跑过，但**模拟器画面冻住没截到图** |
| App/小组件配色统一 | ✅ 对比度断言 + 状态优先级 | ✅ 见过同屏对照 |
| 6×7 网格 / 上下月补位 | ✅ 断言格子连续、42 格 | ✅ |
| **小组件翻月** | ✅ 跨年进位、回落今月 | ❌ **没点过按钮** |
| **换班覆盖** | ✅ 7 个用例 | ❌ **没点过按钮** |

> **下个会话第一件事建议**：装上 APK，人肉点一遍「小组件 ‹ › 箭头」和「首页点某天 → 换班」。
> 这两项只有单测覆盖，UI 接线（`PendingIntent` 的 requestCode、弹层宿主）没在真机验过。

## 五、还没做的 / 已知不足

1. **小组件底部状态行只看"今天"**：翻到别的月份时，底部那行说的仍是今天的上下班状态，不是所看月份那天的。
   这是有意（状态行语义是"此刻在不在班上"），但如果希望它跟着所看月份走，需要改 `phaseTextParts` 的入参。
2. **非精确闹钟只覆盖今明两天**：`nextShiftBoundaryMinute()` 只算今天和昨天那班的边界。跨月翻看时不会有额外的刷新点
   （不影响正确性，因为翻月本身会触发重画）。
3. **`share/ShareUtils.kt` 里旧导出代码是死代码**（`prepareImageFile` / `monthText` / `shareImage`），
   已不挂 UI。可删。
4. **AGENTS.md 里"关于页暂未实现"的说法在上游就已过时**（我改过一处，但可能还有残留）。
5. `R.string.not_implemented` 当前无入口使用。

## 六、注意事项（别踩）

- **这台机器有 GitHub 中间人**（Steam++ 自签根证书）。为构建而信任它，但**下载的东西是经它中转的**。
  真要长期用，建议关掉 Steam++ 或换干净环境。**不要把这份 trust store 带进仓库。**
- **按 `AGENTS.md` 规程，未获明确授权不执行 `git add/commit/push`**。本轮所有改动都在工作区，
  用户尚未授权提交。
- 装机测试时**必须先删掉桌面上旧的小组件再重新添加**——启动器缓存着旧的 RemoteViews，覆盖安装不会重画。
- **本会话的工具后台作业系统已损坏**（`job_output` 报 "inactive context"，`pwsh-*` 句柄失效）。
  下个会话如果也这样，把命令输出**重定向到文件再读**即可绕过。

## 七、变更清单（33 项，未提交）

**新增**
- `domain/`: `TodayRoster.kt`、`MonthGrid.kt`、`DayVisualState.kt`、`ShiftOverride.kt`
- `widget/`: `DaoWidgetProvider.kt`、`WidgetMonthStore.kt`、`WidgetStrings.kt`
- `app/src/test/`: 12 个测试文件（66 用例）
- `res/layout/`: `widget_month.xml`、`widget_day_cell.xml`、`widget_week_row.xml`、`widget_weekday_header.xml`
- `res/drawable/`: `widget_background`、`widget_refresh`、`widget_arrow_left/right`、`widget_nav_button`、
  `widget_day_empty`、`widget_day_outside`、`widget_day_today_none`、
  `widget_day_selected_*`×12、`widget_day_faded_*`×12、`widget_day_today_*`×13（**按色板生成，改色板要同步**）
- `res/drawable-*/widget_preview.png`（5 档密度，由 `make_widget_preview.py` 生成）
- `res/values/widget_colors.xml` + `res/values-night/widget_colors.xml`
- `res/xml/widget_month_info.xml`

**修改**
- `README.md`、`AGENTS.md`
- `app/build.gradle.kts`（Robolectric 测试依赖 + `isIncludeAndroidResources`）
- `gradle/libs.versions.toml`（junit / robolectric / androidx-test-core）
- `app/src/main/AndroidManifest.xml`（小组件 receiver）
- `MainActivity.kt`、`MainViewModel.kt`、`HomeScreen.kt`、`Ymd.kt`、`Roster.kt`
- `ui/ShiftPalette.kt`（`onColor` / `pastColor` / `pastFadeRatio`）
- `data/Model.kt`（`weekStartDay` 默认 0→6，即周一→周日）
- `res/values/strings.xml` + `values-en/strings.xml`

## 八、建议的提交拆分

```
feat: 新增「倒班月历」桌面小组件（含翻月）
feat: 小组件改为可上下滑动的周列表
feat: 支持单日换班覆盖
fix: 跨零点夜班的结束时刻按今天/次日区分显示
fix: 小组件底部状态行取错默认班组
fix: 小组件翻月箭头不可见
refactor: 统一 App 内月历与小组件的配色规则
test: 补 Robolectric 小组件测试与纯逻辑单测
docs: 更新 README 与 AGENTS.md
chore: 补小组件预览图生成脚本
```

注意 #2、#3 的修复都在第 4 项之前，如果需要能编译的中间提交，顺序要调成：
小组件 → 修复 → 月历形态 → 配色统一 → 跨零点 → 翻月+覆盖 → 滑动改版。

## 九、小组件的三轮改版与最终形态

用户先后要过三种形态，**最后定在"6×7 整月网格 + 点标题翻月、不要箭头"**：

| 轮次 | 形态 | 结果 |
|---|---|---|
| 1 | 静态 6×7 网格 +「‹ ›」箭头翻月 | 可用。踩的坑：箭头 vector 是白色又没 tint，白底白箭头 —— 按钮能点、能翻月，但**用户看不见它**（已修） |
| 2 | 单块 `ListView`，手指上下滑动逐周滚 | 能滑。但 `setScrollPosition` **三种投递方式实测全无效**，视口只能停在第一行；试过"拆上下两块列表"绕开定位：接缝能对齐、两块都能滚，可两块的时间方向必然相反（下块往上拖 = 看未来，上块往上拖 = 看更早），手感坏，放弃 |
| 3（最终） | 静态 6×7 网格 + **点标题翻下个月** +「今」 | 用户选定"回到原来的方案、取消箭头"。第 2 轮引入的 `DaoWeekListService` / `WeekWindow` / Manifest service 全部删掉了 |

**"左右滑动切月"做不到，原因很硬**：RemoteViews 的 LayoutInflater 带白名单（只允许打了 `@RemoteView` 的控件），
**`HorizontalScrollView` 不在名单里** —— inflate 时直接抛
`Class not allowed to be inflated android.widget.HorizontalScrollView`，小组件会永远停在加载占位图。
（这个错误是 Robolectric 上一跑就现形的，真机上只会看到空白。）
`ViewPager` / `Gallery` 之类同样不可用，`ViewFlipper` 能用但**不响应手势**（只有程序切换）。
所以横向滑动在桌面小组件里没有实现路径，折中是**点标题翻月** + 「今」回今月。

**"窗口太大 / 不能缩放"**：`widget/WidgetMetrics.kt` 现在按 `getAppWidgetOptions` 给的宽高算列宽与行高
（列宽 =(宽 − padding)/7，行高 =(高 − 标题表头状态行)/6），用 `setViewLayoutWidth/Height`（API 31+）显式下发，
所以拉伸/缩小时格子跟着变、内容不会把 widget 撑爆。Launcher 侧的四边缩放手柄实测是存在的
（`com.google.android.apps.nexuslauncher:id/widget_resize_{top,left,right,bottom}_handle`），
`resizeMode="horizontal|vertical"` 一直没写错；若用户那边仍不能缩，需要问清具体现象（长按后有没有出现边框）。

**真机验证**（AVD `dao_test`，adb 驱动）：

| 动作 | 结果 |
|---|---|
| 安装后打开 | 标题 `9/2026`，6 行 × 7 列铺满，今天 9/30 描边，底部 `Not started · begins 22:30` |
| 点标题 | 9/2026 → 10/2026 → 11/2026，「今」按钮随之出现 ✓ |
| 点「今」 | 回到 9/2026，按钮自动隐藏 ✓ |
| 尺寸自适应 | 行高实测 170px = 网格高度 1021px / 6 ✓ |

**没做的**：横向滑动（见上，平台不允许）；滚动/滑动时标题不跟随（静态网格没有滚动，标题只显示当前所选月）。

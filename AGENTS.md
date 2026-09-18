# AGENTS.md

## 项目概述

Android 应用「**倒班表**」（app_name 与界面标题都用这个；namespace 仍是 `win.zuoye.dao`），
单模块 `:app`，Gradle Kotlin DSL。**功能是倒班安排表**：班次模板 + 周期方案 + 月历着色。

**产品目标**：基于 [miuix](https://github.com/compose-miuix-ui/miuix) 的倒班安排表应用，
离线可用、可把自己的排班方案导出给别人导入。

## 产品设计

### 核心概念（两层数据模型）
- **班次模板（"基本天"）**：名称 + 起止时间 + 颜色，只定义一次、全程复用。例：「早班 08:00–15:00」。结束时间早于开始 = 跨零点夜班（标注"次日"）。**不内置休息模板**；用户可自行建名为"休息"的班次。
- **倒班方案**：周期天数 N（用户文本输入 1–99，不提供预设）+ 周期第 1..N 天各挂哪个模板（点选复用）+ **锚点日期**（周期第 1 天对应真实日期）。任意日期班次 = `模板[(日期 − 锚点) mod N]`，纯本地推导。
- **换班覆盖**：个别日期手动指定班次、优先于周期推导——本期 UI 不做，数据模型预留（`overrides`）。

### 首页（日历）
- 自绘月历网格，月份左右滑动切换；顶栏标题 = `app_name`（「倒班表」）。
- 单元格按当日班次颜色着色，格内显示日期 + 班次名；今天主色描边；点击弹详情（日期、星期、班次、时间段、覆盖标记）。
- 顶栏副标题显示"今天·班次名+时间段"；右上角按钮进入**「分享配置」二级页面**（见下）。
- 滑到非当月时右下角出现蓝色圆形「今」按钮，点击回到今天所在月份。
- 底部导航栏：主页 / 设置，两个标签页由**一个 `HorizontalPager`**承载（切换是整页横向滑动）。

### 分享配置（`ui/share/SharePlanScreen.kt`，二级页面）
首页右上角进入。页内结构：选一个方案（`Card` + `BasicComponent`，选中打勾）→ 亮出该方案的二维码 → 「其它方式」三条：
- **二维码**：内容是紧凑载荷（见下），下方提示「扫码即可导入」。
- **复制到剪贴板**：复制整段分享文本，粘到聊天软件发给对方。
- **系统分享**：`ACTION_SEND` 纯文本（微信/QQ 等都能发），对方整条复制即可导入。

### 载荷与导入
- 两种表示，都在 `data/PlanShare.kt`：
  - **紧凑载荷** `DAO1:<base64url>`：短字段 JSON → raw deflate → base64url（Kotlin stdlib `Base64.UrlSafe`，不用 android.util.Base64，单测能跑）。
    二维码和聊天文本用它——只有几十~一两百字符，二维码才不会太密。实测 6 个班次 3 个方案的原样 JSON 700+ 字符，紧凑载荷约 1/4。
- `PlanShareCodec.decode(text)` 三种输入都认：紧凑载荷（二维码/剪贴板）、整段分享文本（`[DAO-PLAN]` 之后那段）、完整 JSON。
- **导入**：设置页 →「导入倒班方案」弹窗，**竖排四个按钮、点了直接导入**（没有文本框和二次确认）：
  「从剪贴板导入」/「从文件导入」（`OpenDocument` 选 .json）/「扫码导入」（自己的相机页）/「取消」（着重色）。合并规则：
  班次按「名称+时间+颜色」去重复用，方案按「名称+周期+锚点+日序」去重跳过，同名不同内容自动改名；
  本机已有启用方案时不动它，本机没有启用方案才自动启用导入的方案（`domain/PlanImport.kt`，有单测）。
- 二维码生成用 ZXing（`share/QrCode.kt`：`encodeQrMatrix` 纯 JVM 可测 + `encodeQrCode` 转 Bitmap）。扫码仍走
  `zxing-android-embedded` 的 `ScanContract`（启动 + 收结果），但**相机页是我们自己的**：
  `ui/scan/ScanCaptureActivity.kt`（`ComponentActivity` + 库的 `CaptureManager`：权限、解码、返回结果全交给它，
  生命周期调用顺序照抄库自带页，视图与 Manager 都在 `onCreate` 里建好）→
  `ScanScreen.kt`（相机铺满整屏、左上角一个 miuix 返回箭头，没有标题栏）→
  `ScanFrameView.kt`（正方形取景框：miuix 的 `ViewfinderView` 只挖**直角**洞，和圆角描边会在四个角上错开，
  所以这里不用父类的遮罩，压暗与描边共用同一条圆角 Path）。
  进入时 `ScanOptions.setOrientationLocked(true)` 且 Activity 不声明 `android:screenOrientation`，保持当前屏幕方向
  （库自带页在它自己的 Manifest 里被写死成 `sensorLandscape`）。Manifest 里声明了 CAMERA。
- 旧的「当月日历 PNG + 文本」导出代码仍保留在 `share/ShareUtils.kt`（`prepareImageFile` / `monthText` / `shareImage`），但**已不再挂到 UI 上**；要用的话自己接一个入口。
- 分享/启动分享面板必须从 **Activity context** 发起，或给 chooser 加 `FLAG_ACTIVITY_NEW_TASK`；
  否则 `startActivity` 抛 `AndroidRuntimeException` 直接闪退（`ShareUtils.startChooser` 已兜住）。

### 设置页
- 只剩三块：**倒班方案**（→ 二级页面）、**导入方案**（粘贴 / 扫码）、**关于**。原来的「班次模板」「方案列表」都并进了倒班方案页。
- 列表行整行可点，按下有 miuix 自带的弹簧高亮动效。

### 倒班方案页（`ui/scheme/PlanEditScreen.kt` + `ui/scheme/SchemeEditScreen.kt`，二级页面）
**整页是方案列表**（尺寸对齐系统闹钟列表），点某张卡片进入那个方案的编辑页（同一个 `PageCardStack`，从右侧滑入）。
- 一张 `Card` 一个方案：标题（启用中的那个在名字右边带一个「使用中」小字）+「N 天周期 · yyyy-MM-dd 起」摘要 + 右侧一个 `Switch`；
  关闭的方案标题/摘要用 `disabledOnSurface` 整体变灰。
- **使用中同一时刻只有一个**：开另一个时前一个自动关闭；**不允许全关**，点已启用那个的开关只弹 Toast 提示。
- 右下角一个加号 `FloatingActionButton`（和首页「今」同款：`shadowElevation = 0`、54dp）新建方案；建完直接进编辑页并把光标放进名字框。
  **这个加号和「班次模板」页签里的加号，滚动时都要收起**（用 `ui/common/FabVisibility.kt` 的 `rememberFabVisible`：
  往下滚藏起来、往回滚或回到顶部再露出来，外面套 `AnimatedVisibility` 做淡入缩放）。
- **长按任意卡片进入多选**：左侧出现复选框、右侧开关隐藏，顶栏变成「已选 N 个」+ 关闭按钮；右上角依次是复制图标和红色删除图标。
  复制会给方案与班组生成新 id，方案名追加「 副本」；删除前二次确认，确认弹窗里的「删除」用
  `textButtonColors(textColor = colorScheme.error)`。删掉的正好是使用中的方案时剩下的第一个自动接上。
  多选期间禁用底部导航/侧栏导航与 `HorizontalPager` 手势，系统返回键先退出多选。
- 编辑页最上面是**方案名输入框**（打开已有方案默认不高亮、只显示当前值；新建时自动聚焦），没有单独的编辑图标。
- 下面是 miuix `TabRow` 的「班次模板 / 排班设置」切换（左右各 12dp，与下面的卡片同一条边线）；**名字行与页签固定，只滚页签内容**。
- 「班次模板」直接复用引导向导的 `TemplatesStep`（`internal`；`title = null` + `onAdd = null` 时**只渲染卡片**，小标题和行内加号都不出现，
  加号由右下角 FAB 承担），增删改即时落库；模板是**全局一份**（挂在 `PlanDocument` 上），所有方案共用。
  编辑弹窗 `TemplateEditorDialog`：名称（**框右边的圆点是当前颜色，点开是颜色页**）+ **开始/结束一个切换框**（`SegmentedSwitch`，胶囊会滑动）
  + 共用一组时间滚轮；颜色页里上面是 `ColorPicker` 调色盘、下面是 12 个预设色，确定时把 alpha 收成 1
  （班次色要画在日历格浅底上，半透明会跟底色混）。
- 「排班设置」顺序固定：**开始日期**（点击框 → 弹日期弹窗）→ **周期天数** → 逐日指派
  （复用 `AssignmentRow`；未指派用 `UNASSIGNED = 0L` 占位，因为 `Scheme.dayTemplateIds` 没留 null）。
  周期天数这个表单不包 `Card`（规范如此），但纵向 `insideMargin` 给 26dp，让它的高度和上下那几张卡片（约 74dp）对齐。
- 日期弹窗**只滚「月 / 日」两列，年沿用该方案现有锚点的年份**；「月」「日」是固定表头，不跟着数字滚。
- 「删除方案」在页签内容最底部（红色标题 + 二次确认）。
- **改动即时生效**（不再走三步向导，也不再"修改=生成新方案"）。首次启动引导仍然是原来的三步向导（`ui/onboarding/OnboardingScreen.kt`）。

### 关于页（`ui/about/AboutScreen.kt`）
- 头部：应用图标 + `app_name` + 版本号（版本从 PackageManager 读，不手写）。
- 卡片两项：查看源代码 / 获取更新——**目前是占位**，点了只弹「暂未实现」。

### 设计决策（默认拍板，可推翻）
- 夜班跨零点：归属**开始**日期，日历格不跨格。
- 班次颜色是数据色（预设 12 色板），色块文字按亮度自动黑白。
- UI 中文；一周从周一开始。
- 无方案时首页可用，显示提示文案。

## 数据与持久化规范

- `data/Ymd.kt`：纯数学日期（minSdk 24 不依赖 java.time），epochDay 与 `LocalDate.toEpochDay()` 一致。
  单测对照 `java.util.Calendar` 时**必须用 UTC 时区**——本地时区的毫秒截断会差一天（在 UTC+8 上 1970-01-02 会算成 0）。
- `data/Model.kt`：`ShiftTemplate` / `Scheme` / `PlanDocument`。模型里的集合都是 `ImmutableList` / `ImmutableMap`
  （kotlinx-serialization **对这两个接口只会生成多态序列化器**：写盘直接抛 `SerializationException`，而
  `PlanRepository.readFrom` 会吞掉异常回退默认值 = 静默清空 `plan.json`），所以用 `data/ImmutableSerializers.kt`
  里的代理序列化器收发，**磁盘/分享 JSON 格式不变**；给这几个类型加字段时记得同步改代理类。
- `data/PlanRepository.kt`：DataStore + kotlinx-serialization 单文件 `plan.json`，**不用 Room**（数据量小、免 KSP 版本耦合）。
- `domain/Roster.kt`：`resolveShift` = 覆盖优先 → 周期推导；`Roster` 是月历高频查询用的零分配索引。
- `domain/PlanImport.kt`：导入合并 + 去重规则（有单测）。

## UI 规范

- **界面标题一律读 `R.string.app_name`，不要硬编码应用名**（`ShareUtils` 的渲染函数用参数传 appName）。
- **标签页状态只有一个来源**：`baseTab`（`rememberSaveable`）+ miuix-nav 返回栈（二级页面）。
  别引入第二个标签页状态（`rememberPagerState` 自带保存恢复会与冷启动重置打架，导致冷启动时 pager 半页错位）。
- **`onMutate` 的 transform 是稍后执行的**（MainActivity 里丢给 `lifecycleScope.launch { repo.update(transform) }`），
  里面**不能读可变的组合状态**——同一帧里先把状态清掉（比如退出多选）再交给它，等它跑到时值已经变了。
  要用的值先在外面 `val ids = …` 抓一份再进 lambda（多选删除踩过这个坑：清空选中集合后一个都没删掉）。
- 底栏（主页/设置）在外层 `Scaffold`，两个标签页共用一个 `HorizontalPager`：点底栏走 `animateScrollToPage`，
  `beyondViewportPageCount = 1` 让相邻页保持组合（来回切不丢日历位置）。
- 二级页面走 `PageCardStack`（`ui/common/PageCardStack.kt`，封装 miuix-nav `NavDisplay`，方案编辑页也从它进来）：底层标签页**原地不动只压暗**，卡片从右侧整页滑入（前段 32dp 圆角、贴合时归零）。
  **不要**让底层整页滑走/淡出，也不要把 `Screen.toTab()` 用成 `else -> Home`（推入二级页面时底层会滑回主页）。
  返回栈用 miuix-nav `rememberNavBackStack<Route>`（显式传可序列化的路由父类型），不要重新引入 Navigation3。
  页面滑动与系统预测性返回交给 `NavDisplay`；不要在页面里额外用普通返回处理器拦截出栈。
  多选/引导步骤等页内返回用 `NavigationBackHandler`，标签页只启用当前页的处理器。
  页面自己的 Scaffold 要设 `contentWindowInsets = WindowInsets.systemBars.only(Top + Horizontal)`，否则底部重复算导航栏内边距。
- 首页「今」悬浮按钮：滑到别的月份时出现，蓝色圆形、**不加阴影**（`shadowElevation = 0`，默认阴影会建离屏图层导致掉帧）。
- 日历性能规范：`MonthGrid` 每页数据 `remember` 预计算（`DaySlot`）；`Roster` 查询零分配；单元格用一个
  `drawWithCache` 画底色/描边/文字（配跨页共享的 `TextMeasurer` 缓存）；淡化系数直接乘进颜色，**不加 `Modifier.alpha`**。
- 引导向导页：按钮行放 `Scaffold` 的 `bottomBar`（自己 `navigationBarsPadding()`），
  Scaffold 用 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`（顶栏/底栏各自吃系统栏内边距），
  步骤内容放 `Box(Modifier.weight(1f))`——CycleAssignStep 是 LazyColumn，不加约束会吃光 Column 剩余高度，
  把"上一步/下一步"挤出屏幕；各步骤的加号用 Scaffold 的 `floatingActionButton`（会落在底栏上方）。
- **miuix 的 OverlayDialog 必须放在 Scaffold 的 content lambda 内**（弹层宿主 `LocalRootDialogStates` 由 Scaffold 提供，放在外面点击无反应）。
  踩过：方案列表里多选删除的确认弹窗写在 `Scaffold { … }` **之后**，界面上能看见、点「删除」却什么都不发生——
  写新弹窗时先确认它在 `) { padding -> … }` 这段里，而不是函数体的末尾。
- 应用图标资产：自适应图标前景在 `drawable-*dpi/ic_launcher_foreground.png`（108dp 画布、内容压在 66dp 安全区内），
  背景纯白 `@color/ic_launcher_background`，`ic_launcher_monochrome.png` 给 Android 13+ 主题图标；API 24/25 用 `mipmap-*dpi/ic_launcher*.png`。
- 冷启动用 Android 12 标准启动图（`core-splashscreen`）：`Theme.Dao.Starting`（背景 `@color/splash_background` 深浅色各自取值；
  图标 = `@mipmap/ic_launcher`，`postSplashScreenTheme = Theme.Dao`）。MainActivity 里
  `installSplashScreen().setKeepOnScreenCondition { !planReady }`——首帧数据读完才撤下，另有 2s 兜底；
  `doc == null` 时只留空白 Box，不要再造「加载中…」占位。

## miuix UI 规范

- **squircle 形状**：miuix 组件内部自带 squircle 圆角；**自绘形状（日历格、色块、徽标等）不要用 `RoundedCornerShape` 的 clip/background**，用 `top.yukonga.miuix.kmp.squircle.*` 按场景选：非点击纯色底 → `squircleBackground`（无 offscreen layer，**不要 clip**）；图片/必须裁剪 → `squircleClip`（一个 offscreen layer）；可点击 → `squircleSurface` + `.clickable{}`（涟漪裁进形状）。
- **返回按钮用 miuix 自带图标**（`MiuixIcons.Back`，`top.yukonga.miuix.kmp.icon`），不用 material 的 ArrowBack。
- **列表骨架**：Scaffold + TopAppBar(scrollBehavior) + LazyColumn。LazyColumn 必须加
  `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`；`contentPadding` 只设 top 不设 bottom；
  首个 item 是 Card/表单时先 `item { Spacer(12.dp) }`，`SmallTitle` 开头**不加**（自带 8dp 上下边距）；
  末尾统一 `item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }`。
  二级页面签名**禁止 `bottomPadding: Dp` 参数**（靠末尾 Spacer 自适应）；只有持有外层 bottomBar 的主 Tab 才收 bottomPadding 透传给 contentPadding。
- **Card 间距**：水平 12dp，每项统一 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`；不用 `Arrangement.spacedBy` 拼卡片；**TextField 表单不包 Card**，直接同样的 padding。
- **LazyColumn 里禁止 `item { Card { 多行 } }`**（整卡一次性组合，行多时滚动卡顿）：多行卡拆成每行独立 item 再拼回视觉连续的卡片——
  带圆角的首/末段用 `squircleSurface`（**必须 clip**，否则段内 clickable 的方角涟漪溢出圆角），中间段纯 `background`（无 offscreen 最省）；
  拆分本身**不加 item 动画**（不可见的纯性能优化），placement spec 不能设 null。本项目设置页行数少暂可用 Column+verticalScroll，行数增长后按此拆。
- **Dialog 规范**：按钮顺序 `not_modified | cancel | confirm`（三按钮 weight(1f) + `spacedBy(8.dp)`，confirm 用 `textButtonColorsPrimary()`）。
  **长内容 Dialog** 包 `Column(Modifier.heightIn(max = 500.dp))`，滚动区 `weight(1f, fill = false).verticalScroll(...)`，按钮作非加权子项固定底部——
  miuix `WindowDialog` 不限 content 高度，过长会把按钮顶出屏（TemplateEditorDialog 含时间滚轮，加长时按此办）。
  选项列表 Dialog `insideMargin = DpSize(0.dp, 24.dp)`（水平 0 让行全出血、行内自带 24dp 内缩；垂直 24 补 title 顶距）；
  弹 Dialog 的入口行设 `holdDownState`；单选互斥用 `WindowDropdownDialog`，别手搓 TextButton 列表 + 确认按钮。
  **弹层要常驻组合、用 `show` 驱动**：写成 `if (x) { OverlayDialog(show = true, …) }` 的话，关闭时整个 composable 被直接拿走，
  退出动画来不及播（进场有、退场没有）。内容依赖"打开的是哪一项"时，另存一份 `shownXxx` 记住最后一次打开的值供退出动画渲染
  （`DialogContentLayout` 内部是 `if (!show && !internalVisible) return`，隐藏时既不渲染也不注册 `NavigationBackHandler`，常驻没有副作用）。
- **语义色 token**：合法颜色源**仅** `MiuixTheme.colorScheme.*` 与 `ui/ShiftPalette.kt`（班次数据色）；**屏幕代码禁止散落 `Color(0xFF…)` / `Color.parseColor`**
  （`ShareUtils` 的 android.graphics 渲染例外——无 Compose 主题上下文，颜色也只能经参数传入，不许在调用处新造）。
- **Flow 收集**：屏幕一律 `collectAsStateWithLifecycle()`（androidx.lifecycle:lifecycle-runtime-compose），不用 compose runtime 的 `collectAsState`——后台时上游不再驱动重组。
- **状态类形状**：UiState data class 必须 `@Immutable`；大集合字段一律 `ImmutableList`/`ImmutableMap`（kotlinx.collections.immutable），
  且**从生产端（Repository）就是这个类型**，中途任何裸 `List` 作为 composable 参数都会让该 composable 不可 skip。
- **帧率级 State 不能在组合期读**：每帧都变的值（动画进度、滚动折叠比例）以 `State<T>` 或 `() -> T` 透传，消费方在
  **layout/draw 阶段**读——布局用自定义 `layout{}`（不要 `Modifier.padding(value)`），绘制用 `graphicsLayer{}` / `onDrawBehind{}`；
  `derivedStateOf` 后再 `by` 解包仍是组合期读，禁止。只有夹紧成布尔或离散档位后才可在组合期读。
- **持续动画在不可见时必须停**：帧循环/推进由 alpha 门控，**判定只能在 draw 里做**（draw 阶段的快照读会在 alpha 回到非零时自动重触发 draw）；
  协程侧门控用 `snapshotFlow { alpha() > 0f }.first { it }` 挂起，不能 `by` 解包。
- **周期数据的连续动画**：动画目标用单调递增的**绝对量** `animateTo(seq)`，**禁止 snapTo(0)+animateTo(1) 的相对进度**——
  新点随重组立即进入绘制而 LaunchedEffect 晚一帧，相对进度会画出整体左移再拽回的双向抖动；单格时长**略长于数据推送周期**（稳态恒落后小半格以吸收抖动）；
  滚动窗口 x 步长取 `capacity - 2`（左边缘始终有内容）；进度/上限都在 `onDrawBehind` 里读（draw 阶段读 State 只重绘不重组）。

## 关键版本

- AGP 9.4.0（内置 Kotlin，**不要** apply `org.jetbrains.kotlin.android`）；Kotlin/compose 插件 **2.4.10**；Gradle 9.6.0
- miuix **0.9.4-rc01**（`top.yukonga.miuix.kmp:miuix-ui` / `miuix-nav`，Maven Central；为使用独立导航及预测性返回升级）；Compose BOM 2026.02.01；**activity-compose ≥ 1.13.0**（miuix 0.9.3 对话框内部用 `NavigationBackHandler`/androidx.navigationevent，旧版 ComponentActivity 不提供 NavigationEventDispatcher，点开对话框即 `IllegalStateException` 闪退）
- Java toolchain / JVM target **21**（miuix-nav 0.9.4-rc01 内联 API 的字节码要求）。
- core-splashscreen 1.2.0（Android 12 标准启动图，低版本兼容）
- ZXing：`com.google.zxing:core` 3.5.3（生成二维码）+ `com.journeyapps:zxing-android-embedded` 4.3.0（相机扫码页）
- miuix-icons（`MiuixIcons.Regular.*` 那套图标）与 miuix-preference（`WindowDropdownDialog` 单选弹窗）版本号跟 miuix 一致
- kotlinx-collections-immutable 0.4.0（数据模型的不可变集合）+ lifecycle-runtime-compose 2.9.4（`collectAsStateWithLifecycle`）
- minSdk 24，compileSdk/targetSdk 37；依赖一律走 `gradle/libs.versions.toml`
- 应用版本：`app/build.gradle.kts` 的 `defaultConfig { versionCode / versionName }`。
  发新版要**同时**改 `versionCode`（+1，否则装不上更新）和 `versionName`；关于页/设置页显示的版本是从 PackageManager 读的，不用手改。

## 构建与测试命令

```bash
./gradlew :app:assembleDebug

# 发布（签名配置见下）
./gradlew :app:assembleRelease  # → app/build/outputs/apk/release/app-release.apk
```

- 只需要由人进行安装和测试。

## 工作规程（每次改动必须遵守）

1. 每次改动至少跑：`git diff --check`（空白错误）+ 与变更匹配的 Gradle 任务（改 UI/逻辑 → `:app:assembleDebug`；动了可测模块 → `:app:testDebugUnitTest`）。
2. **保留用户已有的未提交改动**；不使用 `git reset --hard` / `git checkout --` 等破坏性操作。
3. **不修改、不输出 `local.properties`**（含 sdk.dir 等内容）。
4. 完成后先报告：改了什么 + 验证结果（构建/测试输出），再谈后续。
5. 除非用户在当前请求中明确授权，**不执行 `git add` / `git commit` / `git push`**。
6. 需要提交时，commit message 用 `<scope>: <summary>`，scope 取：
   `feat`(新功能) `fix`(修补 bug) `docs`(文档) `style`(格式) `refactor`(重构) `chore`(构建/辅助工具) `revert`(回退) `perf`(性能优化) `test`(测试) `improvement`(改进) `build`(打包) `ci`(持续集成)。
   主题行 ≤72 字符、sentence case、无句尾句号；body 简洁，只讲代码里看不出的根因与取舍，**绝不逐文件复述 diff**。

## 发布签名

- 密钥 `dao-release.jks` + 口令文件 `keystore.properties` 都在**项目根目录**，且**已 gitignore**（别提交、别丢：
  丢了就无法用同一签名发更新）。`app/build.gradle.kts` 读 `keystore.properties` 生成 `signingConfigs.release`；
  缺少该文件时 release 产物不签名（输出 `app-release-unsigned.apk`，装不上）。

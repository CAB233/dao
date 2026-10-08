# 倒班表

基于 **Jetpack Compose** 与 **miuix** 设计语言的 Android 倒班排班应用。用「班次模板 + N 天周期」描述自己的轮班规律，日历自动推导并着色；支持多班组、法定节假日与农历、桌面小组件。应用完全离线运行，方案可通过二维码或文本分享给他人导入。

[![Release](https://img.shields.io/github/v/release/CAB233/dao?label=release)](https://github.com/CAB233/dao/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-blue?logo=jetpackcompose)](https://developer.android.com/compose)
[![miuix](https://img.shields.io/badge/UI-miuix-FF6900.svg)](https://github.com/compose-miuix-ui/miuix)
[![Android Min SDK](<https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-brightgreen.svg?logo=android>)](https://android.com)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)

---

> [!NOTE]
>
> 所有排班数据只保存在本机，应用不收集、不上传任何信息；只有你主动分享时，方案内容才会以二维码或文本的形式离开设备。

---

## 目录

- [倒班表](#倒班表)
  - [特性概览](#特性概览)
  - [快速上手](#快速上手)
  - [下载与安装](#下载与安装)
  - [运行要求](#运行要求)
  - [编译与开发](#编译与开发)
  - [开源引用](#开源引用)
  - [开源许可](#开源许可)

---

## 特性概览

- **班次模板，定义一次复用**：名称、起止时间与颜色自由设置，所有方案共用同一套模板。结束时间早于开始时间即视为跨零点夜班，界面自动标注「次日」；不排班的日子打开「休班」开关即可。
- **周期方案，自动推导**：周期天数 1–99 任意填写，为周期内每一天点选班次模板，再指定周期的开始日期，日历就会按周期自动推导出每一天的班次。
- **多班组对照**：一个方案可以包含多个班组，每个班组有自己的名称与基准日期，并可指定默认班组，同一天各班组的班次一目了然。
- **月历一目了然**：每天按当日班次颜色着色并显示班次名，今天带主色描边；顶栏常显今日班次与时间段，点击任意日期即可查看当天各班组的具体安排。
- **节假日与农历**：内置法定节假日（含调休上班提示）与农历日期，可以在设置中选择只显示其中一种或都显示。
- **扫码分享与导入**：把自己的方案生成二维码或一段文本发给同事，对方扫码或粘贴即可导入；已经存在的重复方案会自动跳过。
- **桌面小组件**：提供「本周排班」与「排班月历」两种组件，可自由调整大小，随尺寸显示本周、前后三周或整个月的内容。
- **应用内更新**：可在设置中选择 GitHub 或镜像渠道检查更新，安装包下载并校验后交给系统安装。

---

## 快速上手

1. 首次启动选择创建方式：**手动添加**、**从剪贴板导入** 或 **扫描二维码导入**。
2. 添加班次模板，例如「早班 08:00–15:00」，并挑一个颜色；夜班把结束时间设到次日即可。
3. 填写周期天数，为周期内每一天点选班次，最后选择周期的开始日期。
4. 回到首页，月历会自动着色；在「配置」页可以新建、启用、复制或删除方案，改动即时生效。

---

## 下载与安装

从 [Releases](https://github.com/CAB233/dao/releases/latest) 下载 `Dao-<版本>.apk` 并安装。

---

## 运行要求

| 项目     | 要求                                                         |
| :------- | :----------------------------------------------------------- |
| 系统版本 | Android 7.0（API 24）及以上                                  |
| 设备架构 | arm64-v8a                                                    |
| 网络     | 可选，仅检查更新与下载安装包时使用                           |
| 权限     | 相机（扫码导入）、通知（更新下载进度）                       |
| 界面语言 | 简体中文 / English                                           |

---

## 编译与开发

### 环境要求

- **JDK 17** 或更高版本（编译工具链使用 JDK 21）
- **Android SDK**（`compileSdk = 37`，`minSdk = 24`）
- **Android NDK 30.0.16248370** 与 **CMake 3.22.1** 以上（扫码功能包含本地代码，首次构建需联网拉取依赖）
- **Gradle**（推荐使用项目自带的 Gradle Wrapper）

### 常用命令

```bash
# 构建 Debug APK
./gradlew :app:assembleDebug

# 构建 Release APK
./gradlew :app:assembleRelease

# 检查 / 自动格式化 Kotlin 代码风格
./gradlew ktfmtCheck
./gradlew ktfmtFormat
```

---

## 开源引用

- [miuix](https://github.com/compose-miuix-ui/miuix) - 遵循 MIUI / HyperOS 设计风格的 Compose UI 库
- [ZXing-C++](https://github.com/zxing-cpp/zxing-cpp) - 扫码导入使用的二维码识别库
- [QR Code generator](https://github.com/nayuki/QR-Code-generator) - 分享二维码生成库
- [Tyme](https://github.com/6tail/tyme4kt) - 农历与法定节假日数据
- [Noto Emoji](https://github.com/googlefonts/noto-emoji) - 首页状态图标
- [AboutLibraries](https://github.com/mikepenz/AboutLibraries) - 开源许可清单
- AndroidX、Jetpack Compose、CameraX、WorkManager 等 Android 官方组件

---

## 开源许可

本项目基于 [AGPL-3.0](LICENSE) 开源。
